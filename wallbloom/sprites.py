"""Procedural pixel plants and fungi.

Each builder returns an ordered list of (dx, dy, colour) relative to the root,
stem first. A growing organism simply reveals more of its list, so plants
visibly build themselves one pixel at a time. Everything is derived from the
genome and a seed, so a saved world can regrow identical sprites.
"""

import math
import random

from . import palette
from .palette import CAP, FLOWER, LEAF, LOG, OLIVE, STEM, TRUNK


def _dedupe(pixels):
    seen = set()
    out = []
    for dx, dy, c in pixels:
        key = (dx, dy)
        if key not in seen:
            seen.add(key)
            out.append((dx, dy, c))
    return out


def fern(g, seed, scale):
    """Arching fronds with leaflets, like the reference's feathery spines."""
    rng = random.Random(seed)
    px = []
    fronds = 3 + int(g["bushy"] * 4)
    height = (9 + g["size"] * 22) * scale
    leaf = LEAF if g["tone"] < 0.6 else OLIVE
    order = list(range(fronds))
    for f in order:
        ang = -math.pi / 2 + (f - (fronds - 1) / 2) * (0.44 + rng.uniform(-0.05, 0.05))
        droop = 0.05 + abs(ang + math.pi / 2) * 0.09
        x = y = 0.0
        n = int(height * (1 - abs(f - (fronds - 1) / 2) * 0.08))
        for j in range(max(3, n)):
            x += math.cos(ang)
            y += math.sin(ang)
            ang += droop if math.cos(ang) >= 0 else -droop
            t = j / max(1, n)
            shade = 2 + int((1 - abs(t - 0.45)) * 3.4) - (1 if f % 2 else 0)
            px.append((round(x), round(y), leaf[shade]))
            if j > 1 and j % 2 == 0:
                reach = int((1 - t) * (2 + g["bushy"] * 3)) + 1
                for side in (-1, 1):
                    for k in range(1, reach + 1):
                        px.append((round(x + side * k), round(y - k * 0.4 + 0.5), leaf[shade - 1 + (k == reach)]))
    return _dedupe(px)


def spire(g, seed, scale):
    """A herringbone stem topped with flat glowing dishes (the blue cups of the reference)."""
    rng = random.Random(seed)
    px = []
    height = int((8 + g["size"] * 16) * scale) + 2
    width = 1 + g["bushy"] * 3.5 * scale
    for j in range(height):
        px.append((0, -j, STEM[1 + j * 3 // height]))
        if j % 2 == 0 and j < height - 2:
            reach = int(width * (1 - j / height) + 1)
            for side in (-1, 1):
                for k in range(1, reach + 1):
                    px.append((side * k, -j - k // 2, STEM[2 - (k == reach)]))
    flower = FLOWER[g["hue"]]
    heads = 1 + int(g["bushy"] * 2.2)
    for h in range(heads):
        hx = 0 if h == 0 else rng.choice((-1, 1)) * rng.randint(2, 4)
        hy = -height - (0 if h == 0 else -rng.randint(1, 4))
        w = 2 + int(g["bloom"] * 2 * scale)
        for k in range(-w, w + 1):
            px.append((hx + k, hy, flower[1]))
        for k in range(-w + 1, w):
            px.append((hx + k, hy + 1, flower[0] if abs(k) > 1 else flower[1]))
        px.append((hx, hy - 1, flower[2]))
        if hx:
            for j in range(1, abs(hy) - height + 3):
                px.append((hx - (1 if hx > 0 else -1) * (j // 2), hy + 1 + j, STEM[1]))
    return _dedupe(px)


def clover(g, seed, scale):
    """Low nitrogen-fixing groundcover, sprinkled with tiny blooms."""
    rng = random.Random(seed)
    px = []
    spread = 3 + g["size"] * 8 * scale
    leaf = LEAF if g["tone"] < 0.5 else OLIVE
    tufts = 4 + int(g["bushy"] * 7)
    spots = []
    for t in range(tufts):
        tx = round(rng.uniform(-spread, spread))
        ty = round(rng.uniform(-1.2, 1.2) - abs(tx) * 0.02)
        spots.append((tx, ty))
        for dx, dy in ((0, 0), (-1, -1), (1, -1), (0, -2)):
            px.append((tx + dx, ty + dy, leaf[2 + (dy == -2) + (t & 1)]))
    flower = FLOWER[g["hue"]]
    for tx, ty in spots[::2]:
        px.append((tx, ty - 3, flower[2]))
    return _dedupe(px)


def shrub(g, seed, scale):
    """A rounded bush of star-shaped leaves bearing dark red fruit."""
    rng = random.Random(seed)
    px = []
    r = (5 + g["size"] * 9) * scale
    leaf = LEAF if g["tone"] < 0.55 else OLIVE
    for j in range(int(r * 0.6)):
        px.append((0, -j, TRUNK[3]))
    for b in range(5):
        ang = -math.pi / 2 + (b - 2) * 0.5
        for k in range(int(r * 0.7)):
            px.append((round(math.cos(ang) * k), round(-r * 0.3 + math.sin(ang) * k * 0.9), TRUNK[3]))
    stars = int(10 + g["bushy"] * 16)
    for s in range(stars):
        a = rng.uniform(0, math.tau)
        d = math.sqrt(rng.random()) * r
        sx = math.cos(a) * d
        sy = -r * 0.8 + math.sin(a) * d * 0.72
        shade = 1 + int((-sy / (r * 1.5)) * 3)
        arm = 2 + int(scale * 1.5)
        for ang in range(6):
            th = ang * math.pi / 3 + 0.2
            for k in range(arm + 1):
                px.append((round(sx + math.cos(th) * k), round(sy + math.sin(th) * k * 0.8), leaf[shade + (k == 0)]))
    fruit = FLOWER[g["hue"]]
    for f in range(2 + int(g["bloom"] * 7)):
        a = rng.uniform(0, math.tau)
        d = math.sqrt(rng.random()) * r * 0.9
        fx, fy = round(math.cos(a) * d), round(-r * 0.8 + math.sin(a) * d * 0.72)
        if g["bloom"] > 0.6:  # the showier blossoms open out, like the red roses in the reference
            for dx, dy, lv in ((0, 0, 2), (1, 0, 1), (-1, 0, 1), (0, 1, 0), (0, -1, 1), (1, 1, 0), (-1, 1, 0)):
                px.append((fx + dx, fy + dy, fruit[lv]))
        else:
            px.append((fx, fy, fruit[1]))
            px.append((fx + 1, fy, fruit[0]))
    return _dedupe(px)


def sapling(g, seed, scale):
    """A young tree. Slow, thirsty, and the backbone of a mature guild."""
    rng = random.Random(seed)
    px = []
    height = (24 + g["size"] * 40) * scale
    leaf = LEAF if g["tone"] < 0.7 else OLIVE

    tips = []

    def branch(x, y, ang, length, depth):
        for j in range(int(length)):
            x += math.cos(ang)
            y += math.sin(ang)
            ang += rng.uniform(-0.1, 0.1)
            shade = 4 if math.cos(ang) > 0.1 else 2
            px.append((round(x), round(y), TRUNK[shade]))
            if depth >= 2 and scale > 0.5:
                px.append((round(x) + 1, round(y), TRUNK[2]))
        tips.append((x, y))
        if depth > 0:
            for side in (-1, 1):
                branch(x, y, ang + side * rng.uniform(0.35, 0.8), length * 0.62, depth - 1)

    branch(0, 0, -math.pi / 2, height * 0.55, 2 + (g["bushy"] > 0.5))
    canopy = []
    for tx, ty in tips:
        r = 3 + g["bushy"] * 5 * scale
        for _ in range(int(r * r * 1.0)):
            a = rng.uniform(0, math.tau)
            d = math.sqrt(rng.random()) * r
            sx, sy = tx + math.cos(a) * d, ty + math.sin(a) * d * 0.7
            shade = 1 + int(max(0, (ty - sy) / r + 1) * 1.9)
            canopy.append((round(sx), round(sy), leaf[shade]))
    rng.shuffle(canopy)
    return _dedupe(px + canopy)


def mushroom(g, seed, scale):
    """A little flush of fruiting bodies. Shape and colour come from the colony's genes."""
    rng = random.Random(seed)
    px = []
    cap = CAP[g["hue"]]
    n = 1 + int(g["cluster"] * 4.5)
    for m in range(n):
        mx = 0 if m == 0 else rng.randint(-5, 5)
        my = 0 if m == 0 else rng.randint(-1, 1)
        s = g["size"] * rng.uniform(0.6, 1.0) * scale
        tall = int(1 + s * 5 * (0.6 + g["slender"]))
        for j in range(tall):
            px.append((mx, my - j, cap[0] if j < tall - 1 else cap[1]))
        rad = 1 + int(s * 3.5 * (1.3 - g["slender"] * 0.6))
        top = my - tall
        if g["shape"] < 0.34:  # shallow dish
            for k in range(-rad, rad + 1):
                px.append((mx + k, top, cap[1 + (abs(k) < rad - 1)]))
            for k in range(-rad + 1, rad):
                px.append((mx + k, top - 1, cap[3] if abs(k) < rad / 2 else cap[2]))
        elif g["shape"] < 0.67:  # dome
            for row in range(rad + 1):
                half = int(math.sqrt(max(0, rad * rad - row * row)) + 0.4)
                for k in range(-half, half + 1):
                    px.append((mx + k, top - row, cap[1 + (row > 0) + (row > rad / 2 and abs(k) < half)]))
        else:  # bell
            for row in range(rad + 2):
                half = max(0, rad - row // 2 - (row == rad + 1))
                for k in range(-half, half + 1):
                    px.append((mx + k, top - row, cap[min(3, 1 + row * 2 // (rad + 1))]))
    return _dedupe(px)


def fallen_log(g, seed, scale):
    """What a dead tree leaves behind: a nurse log, feeding everything for a long time."""
    rng = random.Random(seed)
    px = []
    length = int((22 + g["size"] * 26) * scale)
    r = 1.5 + g["size"] * 2.5 * scale
    tilt = rng.uniform(-0.05, 0.05)
    for j in range(-length // 2, length // 2):
        cy = j * tilt
        for row in range(-int(r), int(r) + 1):
            shade = 2 - row * 2 // max(1, int(r))
            if (j * 5 + row * 3) & 7 == 0:
                shade -= 1
            px.append((j, round(cy) + row - int(r), LOG[shade]))
    rng.shuffle(px)
    return _dedupe(px)


def crocus(g, seed, scale):
    """Spring's first flower, up through the last of the snow."""
    rng = random.Random(seed)
    px = []
    petals = FLOWER[2] if g["bloom"] < 0.6 else FLOWER[5]
    for k in range(2 + int(g["bushy"] * 3)):
        x = rng.randint(-4, 4)
        h = 2 + int(g["size"] * 3 * scale)
        for j in range(h):
            px.append((x, -j, LEAF[4]))
        px.append((x - 1, 1 - h // 2, LEAF[5]))
        px += [(x - 1, -h, petals[1]), (x, -h, petals[2]), (x + 1, -h, petals[1]), (x - 1, -h - 1, petals[0]),
               (x, -h - 1, petals[1]), (x + 1, -h - 1, petals[0]), (x, -h + 1, petals[2])]
    return _dedupe(px)


def foxglove(g, seed, scale):
    """Midsummer: a tall spire of hanging bells, darkening toward the tip."""
    px = []
    h = int((14 + g["size"] * 14) * scale) + 3
    hue = FLOWER[3] if g["bloom"] < 0.6 else FLOWER[2]
    for j in range(h):
        px.append((0, -j, STEM[2]))
    for side in (-1, 1):
        for k in range(1, 4):
            px.append((side * k, -k // 2, LEAF[4]))
    for j in range(h // 3, h, 2):
        t = (j - h // 3) / max(1, h - h // 3)
        width = 2 if t < 0.55 else 1
        shade = 2 if t < 0.4 else 1 if t < 0.75 else 0
        for side in (-1, 1):
            for k in range(1, width + 1):
                px.append((side * k, -j, hue[shade]))
    return _dedupe(px)


def goldenrod(g, seed, scale):
    """Autumn's last gold: arching plumes of tiny yellow flowers."""
    rng = random.Random(seed)
    px = []
    stems = 2 + int(g["bushy"] * 3)
    h = int((12 + g["size"] * 10) * scale) + 3
    for s in range(stems):
        lean = (s - (stems - 1) / 2) * 0.3
        x, y = 0.0, 0.0
        for j in range(h):
            x += lean + j * lean * 0.02
            y -= 1
            px.append((round(x), round(y), STEM[2]))
            if j > h * 0.5:
                for side in (-1, 1):
                    if rng.random() < 0.75:
                        spread = 1 + int((h - j) * 0.25)
                        px.append((round(x) + side * rng.randint(1, spread), round(y) + rng.randint(-1, 0), FLOWER[5][rng.randint(1, 2)]))
    return _dedupe(px)


def snowdrop(g, seed, scale):
    """Winter's small defiance: a nodding white bell above the snow."""
    rng = random.Random(seed)
    px = []
    for k in range(2 + int(g["bushy"] * 3)):
        x = rng.randint(-5, 5)
        h = 3 + int(g["size"] * 3 * scale)
        for j in range(h):
            px.append((x, -j, LEAF[3]))
        px += [(x + 1, -h, LEAF[3]), (x + 2, -h + 1, FLOWER[6][2]), (x + 1, -h + 1, FLOWER[6][1]), (x + 3, -h + 1, FLOWER[6][1]),
               (x + 2, -h + 2, FLOWER[6][2])]
    return _dedupe(px)


def pumpkin(g, seed, scale):
    """Autumn, on a sprawling vine. In the last week of October, someone carves them."""
    rng = random.Random(seed)
    px = []
    for side in (-1, 1):
        x = 0
        for j in range(4 + int(g["bushy"] * 7)):
            x += side
            px.append((x, rng.randint(0, 1), LEAF[3]))
            if j % 3 == 0:
                px.append((x, -1, LEAF[5]))
    r = 3 + int(g["size"] * 2 * scale)
    rows = max(3, r)
    for y in range(-rows, 1):
        half = int(math.sqrt(max(0, 1 - ((y + rows / 2) / (rows / 2 + 0.5)) ** 2)) * r + 0.5)
        for x in range(-half, half + 1):
            rib = (x + half) % 3 == 0
            lit = x < 0 and y < -1
            px.append((x, y, FLOWER[5][2 if lit else 1] if not rib else FLOWER[4][1]))
    px += [(0, -rows - 1, LEAF[3]), (1, -rows - 1, LEAF[3])]
    return _dedupe(px)


def bevel(pix, light=-1):
    """Give a flat sprite some form: edges facing the light go a shade brighter, edges away from it a shade darker.

    light is -1 when the light is to the left of the sprite and 1 when it's to the right.
    """
    filled = {(dx, dy) for dx, dy, _ in pix}
    if len(filled) < 6:
        return pix  # too small to carry shading without losing its shape
    out = []
    lit, dark = palette.LIT, palette.DARK
    for dx, dy, c in pix:
        open_up = (dx, dy - 1) not in filled
        open_lit = (dx + light, dy) not in filled
        open_down = (dx, dy + 1) not in filled
        open_away = (dx - light, dy) not in filled
        if (open_up or open_lit) and not (open_down and open_away):
            c = lit[c]
        elif (open_down or open_away) and not (open_up or open_lit):
            c = dark[c]
        out.append((dx, dy, c))
    return out


def _flecked(builder):
    """Poison shows: well-defended plants carry red flecks, a warning to anything learning to eat them."""

    def build(g, seed, scale):
        px = builder(g, seed, scale)
        if g.get("toxin", 0.0) > 0.5:
            step = 3 if g["toxin"] > 0.75 else 5
            return [(dx, dy, FLOWER[4][0] if k % step == 0 else c) for k, (dx, dy, c) in enumerate(px)]
        return px

    return build


BUILDERS = {
    "clover": _flecked(clover),
    "fern": _flecked(fern),
    "spire": _flecked(spire),
    "shrub": shrub,
    "sapling": sapling,
    "mushroom": mushroom,
    "log": fallen_log,
    "crocus": crocus,
    "foxglove": foxglove,
    "goldenrod": goldenrod,
    "snowdrop": snowdrop,
    "pumpkin": pumpkin,
}

STAG_BODY = [
    "..............#...#...",
    "..............#..#..#.",
    "...............#.#.#..",
    "...........#...###....",
    "............#..##.....",
    ".............####.....",
    "..............####O##.",
    "..............######..",
    "..............###.....",
    ".............####.....",
    "..##########.####.....",
    ".###############......",
    "#################.....",
    "#++##############.....",
    ".++++##########++.....",
    "..+++++++++++++++.....",
]
# The same stag with its head lowered, to graze or drink.
STAG_LOWERED = []
for _row, _line in enumerate(STAG_BODY):
    _kept = _line[:14] + "." * (len(_line) - 14)
    _from = STAG_BODY[_row - 9] if _row >= 9 else None
    if _from:
        _kept = _kept[:14] + _from[14:]
    STAG_LOWERED.append(_kept)
STAG_LEGS = (
    [
        "...++.#.......+#......",
        "...+..#........#......",
        "...+...#.......#......",
        "...+...#.......#......",
        "...+...#......#.......",
    ],
    [
        "..+#..+........#+.....",
        "..#...+.........#.....",
        ".#....+.........#.....",
        ".#.....+.......#......",
        ".#.....+.......#......",
    ],
)

RABBIT_SIT = [
    "...#.#.",
    "...#.#.",
    "...###.",
    ".#####.",
    "w####+.",
    ".#+.#+.",
]
RABBIT_HOP = [
    "....#.#",
    "....#.#",
    "...####",
    "######.",
    "w####..",
    ".+...+.",
]
FOX_TOP = [
    "..........#.#.",
    "..........###.",
    ".........####o",
    "w.........#w..",
    "#+.#######w...",
    ".##########...",
    "...########...",
]
FOX_LEGS = (
    [
        "...+#....#+...",
        "...+.#...#.+..",
        "...o.o...o.o..",
    ],
    [
        "..#+.....+#...",
        "..#..+..+.#...",
        ".o...o..o..o..",
    ],
)
FOX_DRINK = [
    "..............",
    "..............",
    "..............",
    "w.............",
    "#+.#######....",
    ".##########...",
    "...########.#.",
    "...+#....#+###",
    "...+.#...#.####",
    "...o.o...o.o.ow",
]
FOX_DRINK = [row.ljust(15, ".") for row in FOX_DRINK]
FOX_TOP = [row.ljust(14, ".") for row in FOX_TOP]
HEDGEHOG_TOP = [
    "..+#+#+..",
    ".+#+#+#w.",
    "+#+#+#+#w",
]
HEDGEHOG_LEGS = (
    ["..w...w.."],
    ["...w.w..."],
)
SQUIRREL_RUN = [
    ".##.....",
    "###..#.#",
    "##...###",
    ".#.####w",
    "..#####.",
    "...w..w.",
]
SQUIRREL_DIG = [
    "##......",
    "###.....",
    ".##.....",
    "..#####.",
    "...#####",
    "...w.w.w",
]
FROG_SIT = [
    ".w.w.",
    "+###+",
    "#+++#",
]
FROG_HOP = [
    ".w.w.",
    ".###.",
    "#...#",
]
SNAIL_BODY = [
    "..++.",
    ".+#++",
    "wwwww",
]
BIRD_SIT = [
    "...##o",
    "+.###.",
    ".+###.",
    "..w.w.",
]
BIRD_FLY = (
    [
        "..##..",
        "..##..",
        "+#####",
        ".###o.",
    ],
    [
        "......",
        "......",
        "+#####",
        ".####o",
        "..##..",
    ],
)
OWL_FLY = (
    [
        "##.....##",
        "+##.w.##+",
        ".+#####+.",
        "...###...",
    ],
    [
        ".........",
        "...www...",
        ".+#####+.",
        "##+###+##",
    ],
)
BAT_FLAP = (
    [
        "#...#",
        ".###.",
        "..#..",
    ],
    [
        ".....",
        "#.#.#",
        ".###.",
    ],
)


def _stretch(rows, col, n):
    """Lengthen (or, with n = -1, shorten) a body by repeating or dropping a column."""
    if n == 0:
        return rows
    if n < 0:
        return [r[:col] + r[col + 1:] for r in rows]
    return [r[:col] + r[col] * n + r[col:] for r in rows]


BEETLE_BODY = [
    "...w.w...",
    "..#####..",
    ".##+#+##.",
    ".#######.",
    "..+.+.+..",
]
MOTH_BODY = [
    "##.....##",
    "w##...##w",
    "#w#.+.#w#",
    ".###+###.",
    "..##+##..",
    "...#.#...",
]
ANIMAL_POSES = {
    "beetle": {"walk0": BEETLE_BODY},
    "moth": {"fly0": MOTH_BODY},
    "rabbit": {"sit": RABBIT_SIT, "hop": RABBIT_HOP},
    "fox": {"walk0": FOX_TOP + FOX_LEGS[0], "walk1": FOX_TOP + FOX_LEGS[1], "drink": FOX_DRINK},
    "hedgehog": {"walk0": HEDGEHOG_TOP + HEDGEHOG_LEGS[0], "walk1": HEDGEHOG_TOP + HEDGEHOG_LEGS[1]},
    "squirrel": {"run": SQUIRREL_RUN, "dig": SQUIRREL_DIG},
    "frog": {"sit": FROG_SIT, "hop": FROG_HOP},
    "snail": {"crawl": SNAIL_BODY},
    "songbird": {"sit": BIRD_SIT, "fly0": BIRD_FLY[0], "fly1": BIRD_FLY[1]},
    "owl": {"fly0": OWL_FLY[0], "fly1": OWL_FLY[1]},
    "bats": {"fly0": BAT_FLAP[0], "fly1": BAT_FLAP[1]},
    "stag": {"walk0": STAG_BODY + STAG_LEGS[0], "walk1": STAG_BODY + STAG_LEGS[1], "lowered": STAG_LOWERED + STAG_LEGS[0]},
}
BODY_COLUMN = {"beetle": 4, "moth": 4, "rabbit": 2, "fox": 6, "hedgehog": 4, "squirrel": 5, "frog": 2, "snail": 2, "songbird": 3, "owl": 4, "bats": 2, "stag": 6}
# Extra rows worn on top by highly ornamented individuals: antlers, plumes, crests, tufts.
ORNAMENTS = {
    "rabbit": [".w.w.w.", "..w.w.."],
    "fox": ["w.............", "#............."],
    "hedgehog": ["..+.+.+.."],
    "squirrel": [".###...."],
    "songbird": ["....o."],
    "owl": ["..w...w.."],
    "stag": ["............#...#.#...", ".............#.#..#.#."],
}
_animal_cache = {}


def _round_body(rows):
    """Form for an animal: the top of the body catches the light, and the underside falls into shadow."""
    grid = [list(r) for r in rows]
    h = len(grid)
    out = [list(r) for r in rows]
    for y in range(h):
        for x, ch in enumerate(grid[y]):
            if ch != "#":
                continue
            above = grid[y - 1][x] if y > 0 else "."
            below = grid[y + 1][x] if y + 1 < h else "."
            if above == "." and below != ".":
                out[y][x] = "^"
            elif below == "." and above != ".":
                out[y][x] = "+"
    return ["".join(r) for r in out]


def animal_rows(kind, pose, g):
    """A body built from its genes: size stretches it, ornament crowns it. Cached by what shows."""
    size = min(3, int(g["size"] * 4))
    fancy = g["ornament"] > 0.7
    plain = kind == "stag" and g["ornament"] < 0.3
    key = (kind, pose, size, fancy, plain)
    rows = _animal_cache.get(key)
    if rows is None:
        rows = list(ANIMAL_POSES[kind][pose])
        width = len(rows[0])
        if plain:  # a young-looking stag with only spikes
            rows = rows[2:]
        if fancy and kind in ORNAMENTS:
            width = max(len(r) for r in rows)
            rows = [r.ljust(width, ".") for r in ORNAMENTS[kind]] + rows
        width = max(len(r) for r in rows)
        rows = [r.ljust(width, ".") for r in rows]
        rows = _stretch(rows, BODY_COLUMN[kind], (-1, 0, 1, 2)[size])
        if kind not in ("bats",):  # a bat is only ever a silhouette
            rows = _round_body(rows)
        _animal_cache[key] = rows
    return rows
