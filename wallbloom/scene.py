"""Paints the unchanging forest: sky, far trees, meadow, pond, framing trunks.

This is the most expensive thing Bloom ever does, so the result is cached.
"""

import datetime
import math
import os
import random
import zlib

from .config import CACHE_DIR
from .palette import CANOPY, DARK as LIT_DOWN, FAR, FLECK, LEAF, MOON, SKY, STAR, STONE, TEAL, TRUNK, VIOLET, WATER

SCENE_VERSION = 9
BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


class Noise:
    """Value noise with smooth interpolation."""

    def __init__(self, rng):
        self.t = [rng.random() for _ in range(4096)]

    def __call__(self, x, y):
        t = self.t
        xi = math.floor(x)
        yi = math.floor(y)
        fx = x - xi
        fy = y - yi
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        h = (xi * 73 + yi * 179) & 4095
        h2 = (h + 179) & 4095
        a = t[h]
        b = t[(h + 73) & 4095]
        c = t[h2]
        d = t[(h2 + 73) & 4095]
        top = a + (b - a) * fx
        return top + (c + (d - c) * fx - top) * fy

    def fbm(self, x, y):
        return 0.6 * self(x, y) + 0.3 * self(x * 2.1 + 9, y * 2.1 + 3) + 0.1 * self(x * 4.3 + 5, y * 4.3 + 11)


class Scene:
    """Static forest planes plus the geometry the ecosystem needs."""

    def __init__(self, w, h, seed):
        self.w = w
        self.h = h
        self.seed = seed
        self.base = bytearray(w * h)
        self.fg = bytearray(w * h)
        self.ground = bytearray(w * h)  # 1 where life can root
        self.water = bytearray(w * h)
        self.gy = int(h * 0.50)
        self.moon = (0, 0, 0)
        self.pond = (0, 0, 0, 0)  # cx, cy, rx, ry
        self.pond_rect = (0, 0, 1, 1)
        self.stars = []
        self.lichen = []  # (x0, y0, x1, y1) patches of bark and stone
        self.owl = (0, 0)
        self.shaft_x = 0


def moon_phase(when=None):
    """0 = new, 0.5 = full, from the real date."""
    when = when or datetime.datetime.now(datetime.timezone.utc)
    known_new = datetime.datetime(2000, 1, 6, 18, 14, tzinfo=datetime.timezone.utc)
    return ((when - known_new).total_seconds() / 86400.0 / 29.53059) % 1.0


def stamp_moon(scene, phase, write):
    """Draw the moon as it actually is tonight. write(i, value) sets base pixels."""
    mx, my, r = scene.moon
    w = scene.w
    # Clamp near-new moons to a sliver: an empty sky is less of a landmark.
    lit_side = 1 if phase < 0.5 else -1
    terminator = math.cos(phase * 2 * math.pi)
    if abs(terminator) > 0.93:
        terminator = math.copysign(0.93, terminator)
    for y in range(-r, r + 1):
        half = int(math.sqrt(max(0, r * r - y * y)) + 0.5)
        for x in range(-half, half + 1):
            px, py = mx + x, my + y
            i = py * w + px
            nx = x / (half or 1)
            lit = (nx * lit_side) > terminator if phase != 0.5 else True
            if not lit:
                write(i, SKY[5])
                continue
            mottle = ((x * 7 + y * 13) ^ (x * y)) & 15
            edge = (x * x + y * y) / float(r * r)
            level = 4 if edge < 0.55 else 3
            if mottle < 2:
                level -= 1
            write(i, MOON[level])


def build(w, h, seed):
    path = os.path.join(CACHE_DIR, f"scene-v{SCENE_VERSION}-{seed}-{w}x{h}.z")
    scene = Scene(w, h, seed)
    rng = random.Random(seed)
    _layout(scene, rng)
    try:
        with open(path, "rb") as f:
            raw = zlib.decompress(f.read())
        n = w * h
        scene.base[:] = raw[:n]
        scene.fg[:] = raw[n:2 * n]
        scene.ground[:] = raw[2 * n:3 * n]
        scene.water[:] = raw[3 * n:4 * n]
    except (OSError, zlib.error, ValueError):
        _paint(scene, rng)
        try:
            os.makedirs(CACHE_DIR, exist_ok=True)
            tmp = path + ".tmp"
            with open(tmp, "wb") as f:
                f.write(zlib.compress(bytes(scene.base + scene.fg + scene.ground + scene.water), 6))
            os.replace(tmp, path)
        except OSError:
            pass
    stamp_moon(scene, moon_phase(), scene.base.__setitem__)
    return scene


def _layout(scene, rng):
    """Decide where the landmarks sit. Runs even when pixels come from cache."""
    w, h = scene.w, scene.h
    moon_r = max(7, int(h * 0.036))
    scene.moon = (int(w * rng.uniform(0.40, 0.62)), int(h * rng.uniform(0.19, 0.27)), moon_r)
    side = rng.choice((-1, 1))
    cx = int(w * (0.5 + side * rng.uniform(0.10, 0.20)))
    cy = int(scene.gy + (h - scene.gy) * rng.uniform(0.34, 0.46))
    rx = int(w * rng.uniform(0.09, 0.13))
    ry = int(h * rng.uniform(0.040, 0.055))
    scene.pond = (cx, cy, rx, ry)
    # Roomy enough for the widest bay, and its reeds.
    scene.pond_rect = (max(0, int(cx - rx * 1.75)), max(0, int(cy - ry * 1.9)), min(w, int(cx + rx * 1.75)), min(h, int(cy + ry * 1.9)))
    scene.side = side
    scene.star_rng = random.Random(rng.random())
    scene.stars = []
    for _ in range(int(w * h / 6000)):
        scene.stars.append((scene.star_rng.randrange(w), scene.star_rng.randrange(int(h * 0.40))))
    scene.owl = (int(w * 0.78) if side < 0 else int(w * 0.70), int(h * 0.10))
    scene.shaft_x = scene.moon[0]
    scene.paths = _tree_paths(scene)


def _paint(scene, rng):
    noise = Noise(rng)
    _paint_sky(scene, rng, noise)
    _paint_far_trees(scene, rng, noise)
    _paint_lace(scene, rng, noise)
    _paint_ground(scene, rng, noise)
    _paint_pond(scene, rng, noise)
    _paint_stars(scene, rng)
    _paint_boulders(scene, rng, noise)
    _paint_trunks(scene, rng, noise)
    _paint_cast_shadows(scene)
    _paint_bushes(scene, rng, noise)
    _paint_undergrowth(scene, rng, noise)


def _paint_sky(scene, rng, noise):
    w, gy = scene.w, scene.gy
    mx, my, mr = scene.moon
    base = scene.base
    reach = w * 0.46
    for y in range(gy + 2):
        by = BAYER[y & 3]
        for x in range(w):
            d = math.hypot(x - mx, (y - my) * 1.2)
            level = 0.6 + 5.6 * max(0.0, 1 - d / reach) ** 2.2 + noise(x / 40, y / 22) * 1.1
            level += by[x & 3] / 16.0
            base[y * w + x] = SKY[int(level)]


def _paint_far_trees(scene, rng, noise):
    w, h, gy = scene.w, scene.h, scene.gy
    base = scene.base
    for layer in range(3):
        color = (FAR[4], FAR[2], FAR[0])[layer]
        for _ in range(int(w / (58 - layer * 8))):
            cx = rng.randrange(w)
            if abs(cx - scene.moon[0]) < w * 0.045:  # never straight across the moon
                continue
            half = rng.randint(2, 4) + layer * rng.randint(1, 4)
            lean = rng.uniform(-0.06, 0.06)
            for y in range(gy + 1):
                wob = noise(cx * 0.1 + y / 30, layer) * 2 - 1
                spread = half * (1 + (y / gy) ** 3 * 0.6)
                a = int(cx + lean * (gy - y) + wob - spread)
                b = int(cx + lean * (gy - y) + wob + spread)
                for x in range(max(0, a), min(w, b + 1)):
                    base[y * w + x] = color
    # Dark treeline against the meadow.
    for y in range(gy - int(h * 0.09), gy + 2):
        t = (y - (gy - h * 0.09)) / (h * 0.09)
        for x in range(w):
            if t + (noise(x / 7, y / 4) - 0.5) * 0.9 > 0.42:
                base[y * w + x] = CANOPY[2] if noise(x / 3, y / 3 + 9) > 0.6 else CANOPY[1]


def _paint_lace(scene, rng, noise):
    """Leaf canopy with sky showing through, thickest away from the moon."""
    w, gy = scene.w, scene.gy
    mx, my, _ = scene.moon
    base = scene.base
    reach = w * 0.30
    for y in range(int(gy * 0.92)):
        for x in range(w):
            d = math.hypot(x - mx, (y - my) * 1.1)
            cover = min(1.3, d / reach) * 0.9 + (noise.fbm(x / 26, y / 18) - 0.5) * 1.1
            # Close the canopy overhead, so the opening reads as a gap in the leaves.
            cover += 1.1 * max(0.0, 1 - y / (gy * 0.3)) ** 1.5
            if cover < 0.34:
                continue
            leaf = noise(x / 2.6 + 40, y / 2.2 + 70)
            if leaf < 0.14 + cover * 0.62:
                light = noise(x / 11 + 80, y / 9)
                base[y * w + x] = CANOPY[3] if light > 0.68 else (CANOPY[2] if light > 0.42 else CANOPY[1])


def _paint_stars(scene, rng):
    w = scene.w
    base = scene.base
    bright = set(SKY[i] for i in range(8))
    kept = []
    for x, y in scene.stars:
        i = y * w + x
        if base[i] in bright:
            base[i] = STAR[0]  # faint filler: the bright ones are the real stars, placed by sky.py
            kept.append((x, y))


def _paint_ground(scene, rng, noise):
    w, h, gy = scene.w, scene.h, scene.gy
    base, ground = scene.base, scene.ground
    cx = w * 0.5
    span = h - gy
    for y in range(gy, h):
        t = (y - gy) / span
        by = BAYER[y & 3]
        for x in range(w):
            i = y * w + x
            ground[i] = 1
            patch = noise.fbm(x / 58, y / 11)
            detail = noise(x / 8 + 50, y / 3)
            # A soft pool of light in the middle of the clearing, falling off to the edges.
            radial = 1 - min(1.0, math.hypot((x - cx) / (w * 0.56), (t - 0.34) / 0.72))
            light = radial * 3.1 + (detail - 0.5) * 1.5 - 0.1
            if t < 0.05:
                light *= t / 0.05
            light += by[x & 3] / 16.0
            if patch > 0.50:
                base[i] = TEAL[int(light)]
            else:
                base[i] = VIOLET[int(light * 1.1)]
    # Dark rosettes pressed into the meadow.
    for _ in range(int(w * span / 900)):
        rx, ry = rng.randrange(w), rng.randrange(gy + 6, h)
        s = 3 + (ry - gy) * 8 // span
        for arm in range(-2, 3):
            for k in range(1, s + 1):
                px = rx + int(arm * k * 0.75)
                py = ry - int(k * (0.8 - abs(arm) * 0.28))
                if 0 <= px < w and gy < py < h:
                    i = py * w + px
                    base[i] = VIOLET[0] if base[i] in range(VIOLET.start, VIOLET.start + 6) else TEAL[0]
    # Blue flecks: tiny flowers catching the moon, densest at the far edge of the clearing.
    for _ in range(int(w * span / 55)):
        fy = gy + int(span * rng.random() ** 1.9 * 0.85)
        fx = rng.randrange(w)
        if noise(fx / 34, fy / 7 + 20) < 0.47:
            continue
        for k in range(rng.randint(1, 4)):
            px = fx + k
            if px < w:
                base[fy * w + px] = FLECK[0] if rng.random() < 0.72 else FLECK[1]


def _shore_radius(scene, angle, shape):
    """How far the shore lies in a direction, as a multiple of the pond's nominal size: bays, points and a lopsided body."""
    wobble = (shape(math.cos(angle) * 1.1 + 5, math.sin(angle) * 1.1 + 5) - 0.5) * 0.85
    wobble += (shape(math.cos(angle) * 3.1 + 19, math.sin(angle) * 3.1 + 19) - 0.5) * 0.42
    wobble += (shape(math.cos(angle) * 7.0 + 41, math.sin(angle) * 7.0 + 41) - 0.5) * 0.14
    return max(0.78, 1.04 + wobble)


def _paint_pond(scene, rng, noise):
    w, h = scene.w, scene.h
    cx, cy, rx, ry = scene.pond
    base, ground, water, fg = scene.base, scene.ground, scene.water, scene.fg
    mx = scene.moon[0]
    # Its own random numbers, so that the rest of the landscape stays exactly as it was.
    detail = random.Random(scene.seed ^ 0x5A0D)
    shape = Noise(detail)
    x0, y0, x1, y1 = scene.pond_rect
    bank = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            i = y * w + x
            dx, dy = (x - cx) / rx, (y - cy) / ry
            d = math.hypot(dx, dy) / _shore_radius(scene, math.atan2(dy, dx), shape)
            if d >= 1.0:
                # Mud and damp ground, wider in some places than others.
                if d < 1.05 + 0.13 * noise(x / 6, y / 3 + 50):
                    base[i] = TEAL[0] if (x + y) & 1 else VIOLET[0]
                    ground[i] = 0
                    bank.append((x, y))
                continue
            ground[i] = 0
            water[i] = 1
            # Long horizontal bands that the palette rotation turns into slow shimmer.
            band = int(noise(x / 22, y / 1.3 + 60) * 11 + y * 0.6)
            base[i] = WATER[band % 8]
            # Shallows at the edge go dark with the shore's reflection.
            if d > 0.88 and (x + y) & 1:
                base[i] = WATER[0]
            # The moon's broken reflection.
            if abs(x - cx - (mx - cx) * 0.25) < rx * 0.16 * (0.5 + noise(y * 3.1, 77)) and noise(x / 3, y * 1.7) > 0.45:
                base[i] = MOON[1 + (y & 1)]
    scene.water_pixels = sum(water)
    if not bank:
        return
    # Stones half sunk at the water's edge.
    for _ in range(max(4, len(bank) // 45)):
        x, y = detail.choice(bank)
        size = detail.randint(1, 3)
        for sy in range(-1, 1):
            for sx in range(-size, size + 1):
                px, py = x + sx, y + sy
                if 0 <= px < w and 0 <= py < h and abs(sx) + abs(sy) <= size:
                    i = py * w + px
                    base[i] = STONE[3 if sy < 0 and sx <= 0 else 2 if sy < 0 else 1]
    # Clumps of reeds, standing in front of the pond.
    for _ in range(max(3, len(bank) // 70)):
        x, y = detail.choice(bank)
        for _ in range(detail.randint(3, 7)):
            rx0 = x + detail.randint(-3, 3)
            tall = detail.randint(5, 13)
            lean = detail.uniform(-0.18, 0.18)
            for j in range(tall):
                px, py = int(rx0 + lean * j), y - j
                if 0 <= px < w and 0 <= py < h:
                    fg[py * w + px] = LEAF[2 if j < tall // 2 else 3]
            if detail.random() < 0.3:  # a bulrush head
                px, py = int(rx0 + lean * tall), y - tall
                for k in range(3):
                    if 0 <= px < w and 0 <= py - k < h:
                        fg[(py - k) * w + px] = TRUNK[4]


def _paint_boulders(scene, rng, noise):
    w, h, gy = scene.w, scene.h, scene.gy
    fg, ground, water = scene.fg, scene.ground, scene.water
    span = h - gy
    for b in range(rng.randint(2, 3)):
        for _ in range(30):
            t = rng.uniform(0.25, 0.8)
            bx = int(w * rng.uniform(0.2, 0.8))
            by = gy + int(t * span)
            if not water[by * w + bx]:
                break
        brx = int((7 + 14 * t) * rng.uniform(0.8, 1.2))
        bry = int(brx * 0.62)
        for y in range(by - bry, by + 2):
            for x in range(bx - brx, bx + brx + 1):
                if not (0 <= x < w and 0 <= y < h):
                    continue
                d = math.hypot((x - bx) / brx, (y - by) / bry)
                d += (noise(x / 5, y / 5 + 90) - 0.5) * 0.3
                if d >= 1:
                    continue
                facing = -(y - by) / bry * 0.7 + (scene.moon[0] - bx) / w * 0.5 * (x - bx) / brx
                level = 1.2 + facing * 2.5 + BAYER[y & 3][x & 3] / 16.0
                i = y * w + x
                fg[i] = STONE[int(level)]
                ground[i] = 0
        # Contact shadow.
        for x in range(bx - brx, bx + brx + 1):
            if 0 <= x < w and by + 2 < h:
                scene.base[(by + 2) * w + x] = CANOPY[1]
        scene.lichen.append((bx - brx // 2, by - bry + 1, bx + brx // 2, by - 1))


def _limb(fg, w, h, pts, shade, rimside, noise):
    """Stamp a tapered, rim-lit limb along (x, y, radius) waypoints."""
    for a, b in zip(pts, pts[1:]):
        length = max(1.0, math.hypot(b[0] - a[0], b[1] - a[1]))
        steps = int(length / max(1.0, min(a[2], b[2]) / 3.0)) + 1
        for s in range(steps + 1):
            t = s / steps
            cx = a[0] + (b[0] - a[0]) * t
            cy = a[1] + (b[1] - a[1]) * t
            r = a[2] + (b[2] - a[2]) * t
            r += (noise(cx / 13, cy / 13) - 0.5) * r * 0.25
            ir = int(r) + 1
            for dy in range(-ir, ir + 1):
                py = int(cy) + dy
                if not 0 <= py < h:
                    continue
                for dx in range(-ir, ir + 1):
                    px = int(cx) + dx
                    if not 0 <= px < w or dx * dx + dy * dy > r * r:
                        continue
                    across = (dx * rimside[0] + dy * rimside[1]) / max(1.0, r)
                    bark = noise(px / 3.2 + 7, py / 11 + 3)
                    level = 0.9 + bark * 1.8
                    if across > 0.6:
                        level += (across - 0.6) * 10
                    elif across < -0.25:
                        level = bark * 0.9
                    fg[py * w + px] = shade[int(level)]


def _curve(p0, p1, p2, n):
    pts = []
    for k in range(n + 1):
        t = k / n
        u = 1 - t
        pts.append((u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
                    u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
                    u * u * p0[2] + 2 * u * t * p1[2] + t * t * p2[2]))
    return pts


def _tree_paths(scene):
    """The curves the trunks and the great bough are painted along, for anything that climbs them."""
    w, h = scene.w, scene.h
    s = h / 270.0
    left = scene.side > 0
    wide = 26 * s
    x_base = (w * 0.055) if left else (w * 0.945)
    lean = (w * 0.13) if left else (w * 0.87)
    x2 = w * (0.165 if left else 0.835)
    xf = w * (0.965 if left else 0.035)
    far = w + 10 if left else -10
    return {
        "main": _curve((x_base, h + 16, wide * 1.15), (x_base + (lean - x_base) * 0.2, h * 0.52, wide * 0.72), (lean, -14, wide * 0.5), 24),
        "second": _curve((x2 - (-1 if left else 1) * 40 * s, h + 6, 11 * s), (x2, h * 0.55, 8 * s), (x2 + (1 if left else -1) * 40 * s, -8, 5 * s), 18),
        "far": _curve((xf, h + 8, 10 * s), (xf + (-8 if left else 8) * s, h * 0.5, 7 * s), (xf - (-14 if left else 14) * s, 0, 5 * s), 14),
        "bough": _curve((far, h * 0.13, 15 * s), (w * (0.80 if left else 0.20), h * 0.16, 10 * s), (w * (0.56 if left else 0.44), -10, 4 * s), 20),
    }


def _paint_trunks(scene, rng, noise):
    w, h = scene.w, scene.h
    fg = scene.fg
    rim_left = (1, -0.2)  # the moon sits inward of a left-hand trunk
    rim_right = (-1, -0.2)
    s = h / 270.0
    left = scene.side > 0  # big trunk opposite the pond
    wide = 26 * s
    x_base = (w * 0.055) if left else (w * 0.945)
    lean = (w * 0.13) if left else (w * 0.87)
    rim = rim_left if left else rim_right
    _limb(fg, w, h, _curve((x_base, h + 16, wide * 1.15), (x_base + (lean - x_base) * 0.2, h * 0.52, wide * 0.72), (lean, -14, wide * 0.5), 24),
          TRUNK, rim, noise)
    # Root flare.
    for sign in (-1, 1):
        _limb(fg, w, h, _curve((x_base + sign * wide * 0.7, h * 0.84, wide * 0.42),
                               (x_base + sign * wide * 1.3, h * 0.95, wide * 0.25),
                               (x_base + sign * wide * 2.3, h + 6, wide * 0.1), 8), TRUNK, rim, noise)
    # A second, slimmer trunk leaning away from the first.
    x2 = w * (0.165 if left else 0.835)
    _limb(fg, w, h, _curve((x2 - (-1 if left else 1) * 40 * s, h + 6, 11 * s), (x2, h * 0.55, 8 * s),
                           (x2 + (1 if left else -1) * 40 * s, -8, 5 * s), 18), TRUNK, rim, noise)
    # Bough across the top from the far side.
    far = w + 10 if left else -10
    _limb(fg, w, h, _curve((far, h * 0.13, 15 * s), (w * (0.80 if left else 0.20), h * 0.16, 10 * s),
                           (w * (0.56 if left else 0.44), -10, 4 * s), 20), TRUNK, (0, 1), noise)
    _limb(fg, w, h, _curve((w * (0.84 if left else 0.16), h * 0.15, 6 * s), (w * (0.72 if left else 0.28), h * 0.08, 3.5 * s),
                           (w * (0.64 if left else 0.36), h * 0.14, 1 * s), 14), TRUNK, (0, 1), noise)
    # Far-side distant trunk, narrow, so the frame isn't lopsided.
    xf = w * (0.965 if left else 0.035)
    _limb(fg, w, h, _curve((xf, h + 8, 10 * s), (xf + (-8 if left else 8) * s, h * 0.5, 7 * s), (xf - (-14 if left else 14) * s, 0, 5 * s), 14),
          TRUNK, rim_right if left else rim_left, noise)
    # Vines hanging in slack curves.
    for k in range(4):
        x0 = w * rng.uniform(0.55, 0.95) if left else w * rng.uniform(0.05, 0.45)
        drop = h * rng.uniform(0.10, 0.28)
        x1 = x0 + rng.uniform(-30, 30) * s
        for j in range(int(drop * 1.5)):
            t = j / (drop * 1.5)
            x = x0 + (x1 - x0) * t + math.sin(t * math.pi) * 8 * s
            y = t * drop + h * 0.04
            if 0 <= int(x) < w:
                fg[int(y) * w + int(x)] = TRUNK[1]
    # Lichen patches on the bark, where Conway's rules run for anyone who looks closely.
    bark_x = int(x_base + (lean - x_base) * 0.28)
    size = int(15 * s)
    scene.lichen.append((max(1, bark_x - size), int(h * 0.56), min(w - 1, bark_x + size), int(h * 0.56) + size + 4))
    scene.lichen.append((max(1, bark_x - size // 2), int(h * 0.30), min(w - 1, bark_x + size), int(h * 0.30) + size))


def _paint_cast_shadows(scene):
    """Trunks and rocks each lay a dithered shadow on the meadow, reaching away from the moon."""
    w, h, gy = scene.w, scene.h, scene.gy
    fg, base, ground = scene.fg, scene.base, scene.ground
    dark = LIT_DOWN
    mx = scene.moon[0]
    span = h - gy
    for x in range(0, w):
        # Find where something solid meets the ground in this column.
        for y in range(h - 2, gy, -1):
            if fg[y * w + x] and not fg[(y + 1) * w + x]:
                reach = int(6 + 22 * (y - gy) / span)
                away = 1 if x > mx else -1
                for k in range(1, reach):
                    px = x + away * k
                    py = y + 1 + k // 5
                    if 0 <= px < w and py < h:
                        i = py * w + px
                        if ground[i] and not fg[i] and ((px ^ py) & 1 or k < reach // 2):
                            base[i] = dark[base[i]]
                break


def _paint_bushes(scene, rng, noise):
    """Star-leaved shrubs banked at the edges of the clearing."""
    w, h, gy = scene.w, scene.h, scene.gy
    fg = scene.fg
    for sx, spread in ((-0.02, 0.17), (1.0, 0.16)):
        for _ in range(int(w / 7)):
            u = rng.random()
            bx = w * (sx + (u * spread if sx < 0.5 else -u * spread))
            by = gy + (h - gy) * (0.02 + rng.random() * 0.46) * (1 - u * 0.3)
            r = rng.randint(3, 6)
            shade = 0.35 + 2.3 * (1 - u) * noise(bx / 20, by / 20)
            for arm in range(6):
                ang = arm * math.pi / 3 + 0.26
                for k in range(r + 1):
                    px = int(bx + math.cos(ang) * k)
                    py = int(by + math.sin(ang) * k * 0.85)
                    if 0 <= px < w and 0 <= py < h and not fg[py * w + px]:
                        fg[py * w + px] = LEAF[int(shade + (1 if k == r else 0))]
            for dy in range(-r + 1, r):
                for dx in range(-r + 1, r):
                    px, py = int(bx) + dx, int(by) + dy
                    if 0 <= px < w and 0 <= py < h and not fg[py * w + px] and dx * dx + dy * dy < r * r:
                        # The shrub's shadowy inside, not a leaf: only the stars should turn in autumn.
                        fg[py * w + px] = CANOPY[1 if (px ^ py) & 1 else 0]


def _paint_undergrowth(scene, rng, noise):
    """Near-black grass, thorns and sprigs along the bottom edge."""
    w, h = scene.w, scene.h
    fg = scene.fg
    s = h / 270.0

    def put(x, y, v):
        ix, iy = int(x), int(y)
        if 0 <= ix < w and 0 <= iy < h:
            fg[iy * w + ix] = v

    # Rising shadow along the very bottom.
    for x in range(w):
        top = h - 6 * s - noise(x / 25, 3.3) * 16 * s
        for y in range(int(top), h):
            put(x, y, CANOPY[0] if y > top + 2 else CANOPY[1])
    # Grass blades.
    for _ in range(int(w * 0.75)):
        x = rng.randrange(w)
        bh = rng.uniform(7, 30) * s * (1.3 if abs(x - w / 2) > w * 0.3 else 0.75)
        bend = rng.uniform(-0.5, 0.5)
        y0 = h - rng.uniform(0, 8) * s
        v = CANOPY[rng.randint(0, 1)] if rng.random() < 0.82 else LEAF[1]
        for j in range(int(bh)):
            t = j / bh
            put(x + bend * j * t * 1.2, y0 - j, v)

    # Thorned twigs, thickest at the corners.
    def twig(x, y, ang, length, depth):
        for j in range(int(length)):
            x += math.cos(ang)
            y += math.sin(ang)
            ang += rng.uniform(-0.18, 0.18)
            put(x, y, CANOPY[0])
            if depth > 0 and rng.random() < 0.13:
                twig(x, y, ang + rng.choice((-1, 1)) * rng.uniform(0.5, 1.1), length * 0.5, depth - 1)
            elif rng.random() < 0.18:
                put(x + rng.choice((-1, 1)), y, CANOPY[0])

    for corner in range(int(9 * w / 480)):
        edge = rng.random() ** 2 * 0.3
        x = w * (edge if rng.random() < 0.5 else 1 - edge)
        twig(x, h, -math.pi / 2 + rng.uniform(-0.6, 0.6), rng.uniform(28, 60) * s, 3)

    # A broad-leaved sprig silhouetted in one corner.
    sx = w * (0.94 if scene.side > 0 else 0.06)
    y = h * 0.98
    for k in range(9):
        y -= 8 * s
        x = sx + math.sin(k * 0.7) * 6 * s
        for lr in (-1, 1):
            cx = x + lr * 6 * s
            cy = y - 1 * s
            rx, ry = 4.5 * s, 2.6 * s
            for dy in range(int(-ry) - 1, int(ry) + 2):
                for dx in range(int(-rx) - 1, int(rx) + 2):
                    if (dx / rx) ** 2 + (dy / ry) ** 2 < 1:
                        put(cx + dx, cy + dy - lr * dx * 0.35, CANOPY[0])
        for j in range(int(8 * s)):
            put(x, y + j, CANOPY[0])
