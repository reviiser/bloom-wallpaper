"""Life on the trees: hanging moss on the boughs, and the animals that use the trunks.

Trunks and boughs are foreground scenery, so anything on them is drawn over the
foreground, following the same curves the trees were painted along.
"""

import math
import random

from .palette import CANOPY, FLOWER, HARE, LITTER, STEM
from .evolution import coat_colors
from .sprites import animal_rows
from .wildlife import Creature


def rotate_up(rows):
    """Turn a right-facing side view so that it climbs, head upward."""
    width = max(len(r) for r in rows)
    rows = [r.ljust(width, ".") for r in rows]
    return ["".join(row[x] for row in rows) for x in range(width - 1, -1, -1)]


WOODPECKER = [
    "..R.",
    ".RRo",
    ".##w",
    "###w",
    "#+#w",
    "#+#.",
    "#.#.",
    "#...",
]
WOODPECKER_TAP = [
    "...R",
    "..RR",
    ".##o",
    "###w",
    "#+#w",
    "#+#.",
    "#.#.",
    "#...",
]
CREEPER = [
    ".#.",
    "##w",
    "#+w",
    "#+.",
    "#..",
    "#..",
]
MARTEN = [
    "...........#.#.",
    "...........###o",
    "#.........####.",
    "##...#######w..",
    ".###########...",
    "...##.....##...",
    "...#.......#...",
]
BIRD_FLIGHT = [
    "#..#",
    "####",
    ".##.",
]


def route_on(eco, name, from_ground=True):
    """A path of (x, y, radius) points up a trunk, one per pixel of travel, trimmed to what's on screen."""
    points = eco.s.paths[name]
    route = []
    for a, b in zip(points, points[1:]):
        steps = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1])))
        for k in range(steps):
            t = k / steps
            route.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t))
    foot = eco.gy + eco.span * 0.72
    return [p for p in route if 1 < p[1] < foot and 0 <= p[0] < eco.w] if from_ground else route


def bough_route(eco):
    """Up the far trunk until it meets the great bough, then out along the bough toward its tip."""
    far = route_on(eco, "far")
    bough = [p for p in route_on(eco, "bough", from_ground=False) if 0 <= p[0] < eco.w and p[1] > 2]
    if not far or not bough:
        return far
    join = min(range(len(far)), key=lambda k: abs(far[k][1] - bough[0][1]) + abs(far[k][0] - bough[0][0]))
    start = min(range(len(bough)), key=lambda k: abs(bough[k][0] - far[join][0]))
    return far[:join] + bough[start:]


def draw_clinging(eco, overlay, rows, x, y, colors, flip=False):
    """Over the foreground, unlike nearly everything else, because the tree is what it's standing on."""
    w, h = eco.w, eco.h
    for ry, row in enumerate(rows):
        n = len(row)
        for rx, ch in enumerate(row):
            if ch == ".":
                continue
            c = colors.get(ch)
            px, py = x + (n - 1 - rx if flip else rx), y + ry
            if c is not None and 0 <= px < w and 0 <= py < h:
                overlay[py * w + px] = c


class Climber(Creature):
    """Goes up a trunk in bursts. If the route turns along a bough, it runs along it."""

    speed = 26
    burst = 1.3
    rest = (0.4, 2.0)
    spiral = 0.0
    horizontal_after = None

    def begin(self, route):
        self.route = route
        self.s = 0.0
        self.pace = self.seconds(self.rng.uniform(0.5, self.burst))
        self.halt = 0
        self.down = False

    def where(self):
        k = max(0, min(len(self.route) - 1, int(self.s)))
        return self.route[k], k

    def travel(self):
        """Move along the route. Returns False once it has gone off the end or back to the bottom."""
        if self.halt > 0:
            self.halt -= 1
            return True
        self.s += (-1 if self.down else 1) * self.speed / max(1, self.eco.fps)
        self.pace -= 1
        if self.pace <= 0:
            self.pace = self.seconds(self.rng.uniform(0.4, self.burst))
            self.halt = self.seconds(self.rng.uniform(*self.rest))
        return 0 <= self.s < len(self.route)

    def heading(self, k):
        """Is it going up, or along? Compares its position a little way ahead."""
        route = self.route
        a, b = route[max(0, k - 3)], route[min(len(route) - 1, k + 3)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        return abs(dx) > abs(dy), (dx if not self.down else -dx)

    def place(self, overlay, climb_rows, side_rows, colors):
        """Draw it. A spiralling climber passes round the back of the trunk, out of sight."""
        (x, y, radius), k = self.where()
        along, dx = self.heading(k)
        if along:
            draw_clinging(self.eco, overlay, side_rows, int(x) - len(side_rows[0]) // 2, int(y - radius) - len(side_rows) + 1, colors, dx < 0)
            return
        px = x
        if self.spiral:
            angle = self.s * self.spiral
            if math.cos(angle) < -0.15:
                return
            px = x + math.sin(angle) * radius * 0.75
        rows = climb_rows if not self.down else climb_rows[::-1]
        draw_clinging(self.eco, overlay, rows, int(px) - len(rows[0]) // 2, int(y), colors)


class ClimbingSquirrel(Climber):
    """Up the trunk in the quick stop-start way they have, along the bough if that's the tree, and away."""

    name = "climbing_squirrel"
    speed = 34

    def __init__(self, wild, ind=None):
        super().__init__(wild, ind)
        if ind is None:
            pool = wild.evo.pick("squirrel", 1, True)
            self.ind = pool[0]
        self.g = self.ind["g"]
        options = [route_on(self.eco, "main"), route_on(self.eco, "second"), bough_route(self.eco)]
        self.begin(self.rng.choice([r for r in options if len(r) > 20] or [[(0, 0, 1)]]))
        self.caught = False

    def frame(self, overlay):
        if self.caught:
            return False
        marten = self.wild.find("pine_marten")
        if marten and marten.prey is self:  # it has seen what's coming
            self.halt = 0
            self.speed = 39 + 14 * self.g["speed"]
        if not self.travel():
            return False
        side = animal_rows("squirrel", "run", self.g)
        self.place(overlay, rotate_up(side), side, coat_colors(self.g))
        return True


class PineMarten(Climber):
    """Long, dark and very fast on a trunk. It hunts squirrels up where they feel safe."""

    name = "pine_marten"
    speed = 64
    burst = 2.2
    rest = (0.1, 0.4)
    colors = {"#": LITTER[3], "+": LITTER[2], "w": HARE[3], "o": CANOPY[0]}

    def __init__(self, wild):
        super().__init__(wild)
        self.prey = wild.find("climbing_squirrel")
        if self.prey:
            self.begin(self.prey.route)  # from the foot of the same tree, with some catching up to do
            self.halt = self.seconds(0.4)
        else:
            self.begin(self.rng.choice([r for r in (route_on(self.eco, "main"), route_on(self.eco, "second"), bough_route(self.eco)) if len(r) > 20]))
        self.rows = rotate_up(MARTEN)

    def frame(self, overlay):
        prey = self.prey
        if prey is not None and not prey.caught and prey in self.wild.visitors:
            if self.s > 6 and self.s >= prey.s - 2:
                prey.caught = True
                self.wild.evo.kill("squirrel", prey.ind)
                self.wild.enrich(*self.eco._random_ground(), 0.05)
                self.wild.note("squirrel caught by pine marten")
                self.down = True
                self.prey = None
        if not self.travel():
            return False
        self.place(overlay, self.rows, MARTEN, self.colors)
        self.wild.odd.sighted(self.name)
        return True


class Woodpecker(Creature):
    """Lands on a trunk, works up it in hops, and drills. Beetles under the bark do not like this."""

    name = "woodpecker"
    colors = {"#": CANOPY[0], "+": CANOPY[2], "w": HARE[3], "R": FLOWER[4][1], "o": HARE[2]}

    def __init__(self, wild):
        super().__init__(wild)
        rng = self.rng
        eco = self.eco
        self.route = rng.choice([r for r in (route_on(eco, "main"), route_on(eco, "second"), route_on(eco, "far")) if len(r) > 20])
        self.k = int(len(self.route) * rng.uniform(0.25, 0.55))
        self.x = -6.0 if rng.random() < 0.5 else eco.w + 6.0
        self.y = eco.h * rng.uniform(0.1, 0.3)
        self.state = "in"
        self.taps = 0
        self.wait = 0
        self.hops = rng.randint(2, 5)

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        x, y, _ = self.route[self.k]
        if self.state in ("in", "out"):
            tx, ty = (x, y) if self.state == "in" else (-10 if self.x < eco.w / 2 else eco.w + 10, eco.h * 0.1)
            dx, dy = tx - self.x, ty - self.y
            d = math.hypot(dx, dy)
            step = 60 / max(1, eco.fps)
            if d <= step:
                if self.state == "out":
                    return False
                self.state = "work"
                self.wait = self.seconds(rng.uniform(0.5, 1.5))
            else:
                self.x += dx / d * step
                self.y += dy / d * step
            draw_clinging(eco, overlay, BIRD_FLIGHT, int(self.x), int(self.y), self.colors)
            self.wild.odd.sighted(self.name)
            return True
        rows = WOODPECKER
        if self.taps > 0:
            rows = WOODPECKER_TAP if eco.frame_no & 1 else WOODPECKER
            self.taps -= 1
            if self.taps == 0:
                prey = self.wild.nearest_prey(x, eco.gy + 40, 9999, frogs=False, snails=False)
                if prey and rng.random() < 0.25:
                    self.wild.take(prey)
                self.wait = self.seconds(rng.uniform(0.5, 2.5))
        elif self.wait > 0:
            self.wait -= 1
        else:
            self.hops -= 1
            if self.hops < 0:
                self.state = "out"
                self.x, self.y = x, y
            else:
                self.k = min(len(self.route) - 1, self.k + rng.randint(4, 12))
                self.taps = self.seconds(rng.uniform(0.8, 2.0))
        x, y, _ = self.route[self.k]
        draw_clinging(eco, overlay, rows, int(x), int(y), self.colors)
        return True


class Treecreeper(Climber):
    """A tiny brown bird that spirals up one trunk, flutters down to the foot of the next, and starts again."""

    name = "treecreeper"
    speed = 11
    burst = 1.8
    rest = (0.2, 0.9)
    spiral = 0.16
    colors = {"#": LITTER[3], "+": LITTER[2], "w": HARE[3]}

    def __init__(self, wild):
        super().__init__(wild)
        self.left = self.rng.randint(1, 3)
        self._next_tree()

    def _next_tree(self):
        eco = self.eco
        routes = [r for r in (route_on(eco, "main"), route_on(eco, "second"), route_on(eco, "far")) if len(r) > 20]
        self.begin(self.rng.choice(routes))

    def frame(self, overlay):
        if not self.travel() or self.s > len(self.route) * 0.75:
            self.left -= 1
            if self.left <= 0:
                return False
            self._next_tree()
        self.place(overlay, CREEPER, CREEPER, self.colors)
        self.wild.odd.sighted(self.name)
        return True


class HangingMoss:
    """Grey-green strands that slowly lengthen from the undersides of the great boughs, faster in wet weather."""

    def __init__(self, eco):
        self.eco = eco
        self.rng = random.Random(eco.s.seed ^ 0x3055)
        self.anchors = None
        self.strands = []
        self.swing = 0

    # ---- where it can grow -------------------------------------------------

    def _find_anchors(self):
        """The undersides of limbs: solid above, open air below, and running roughly level."""
        eco = self.eco
        fg = eco.c.fg
        w = eco.w
        top = int(eco.gy * 0.85)
        spots = []
        for x in range(2, w - 2):
            for y in range(top, 4, -1):
                i = y * w + x
                if fg[i] and not fg[i + w] and fg[i - w] and fg[i - 2 * w] and not eco.c.cap[i + w]:
                    # Level ground to the sides, or this is the flank of a trunk.
                    level = 0
                    for dx in (-3, 3):
                        for dy in (-2, -1, 0, 1, 2):
                            k = (y + dy) * w + x + dx
                            if fg[k] and not fg[k + w]:
                                level += 1
                                break
                    if level == 2:
                        spots.append((x, y + 1))
                    break
        self.rng.shuffle(spots)
        self.anchors = spots

    def _pixels(self, seed, x, y):
        """One strand, as pixels from the bough downward: a drifting main thread with short wisps."""
        rng = random.Random(seed)
        scale = self.eco.scale_s
        length = int(rng.uniform(6, 26) * scale)
        out = []
        drift = 0.0
        for j in range(length):
            drift += rng.choice((-0.25, 0.0, 0.0, 0.25))
            t = j / max(1, length)
            shade = STEM[3] if t < 0.3 else STEM[2] if t < 0.75 else STEM[1]  # never a leaf colour: moss doesn't turn in autumn
            out.append((int(drift), j, shade))
            # Thick where it hangs from the limb, thinning into wisps.
            if rng.random() < 0.8 * (1 - t):
                out.append((int(drift) + rng.choice((-1, 1)), j, STEM[2] if t < 0.5 else STEM[1]))
            if t < 0.35 and rng.random() < 0.4:
                out.append((int(drift) + rng.choice((-2, 2)), j, STEM[1]))
        return out

    # ---- growth ------------------------------------------------------------

    def tick(self):
        eco, rng = self.eco, self.rng
        if eco.tick_no % 30:
            return
        if self.anchors is None:
            self._find_anchors()
        if not self.anchors:
            return
        limit = min(int(len(self.anchors) * 0.5), int(70 * eco.cfg.density * eco.w / 480))
        if len(self.strands) < limit and rng.random() < (0.1 if eco.climate.dormant else 0.3):
            self._clump(rng.choice(self.anchors))
        wet = eco.raining or eco.climate.fog
        for strand in self.strands:
            if strand["shown"] < len(strand["pix"]) and rng.random() < (0.5 if wet else 0.12) * (0.2 if eco.climate.dormant else 1.0):
                self._show(strand, strand["shown"] + 1)

    def _clump(self, anchor, shown=False):
        """Moss hangs in tufts: a few strands together, one of them the longest."""
        near = [a for a in self.anchors if abs(a[0] - anchor[0]) <= 5]
        for spot in self.rng.sample(near, min(len(near), self.rng.randint(2, 5))):
            strand = self._add(spot)
            if shown:
                self._show(strand, int(len(strand["pix"]) * self.rng.uniform(0.4, 1.0)))

    def _add(self, anchor):
        seed = self.rng.randrange(1 << 30)
        strand = {"x": anchor[0], "y": anchor[1], "seed": seed, "pix": self._pixels(seed, *anchor), "shown": 0}
        self.strands.append(strand)
        return strand

    def _show(self, strand, upto):
        c = self.eco.c
        w, h = self.eco.w, self.eco.h
        for dx, dy, color in strand["pix"][strand["shown"]:upto]:
            px, py = strand["x"] + dx, strand["y"] + dy
            if 0 <= px < w and 0 <= py < h:
                i = py * w + px
                c.cap[i] = color
                c.refresh(i)
        strand["shown"] = upto

    def frame(self):
        """A gust sets the strands swaying: only the lower half of each moves."""
        eco = self.eco
        wind = eco.wild.find("wind")
        target = (1 if eco.frame_no % 16 < 8 else 0) * (wind.dir if wind else 0) if wind else 0
        if target == self.swing or eco.frame_no % 8:
            return
        c = eco.c
        w, h = eco.w, eco.h
        for strand in self.strands:
            half = len(strand["pix"]) // 2
            for dx, dy, color in strand["pix"][half:strand["shown"]]:
                px, py = strand["x"] + dx, strand["y"] + dy
                old, new = px + self.swing, px + target
                if 0 <= old < w and 0 <= py < h:
                    i = py * w + old
                    c.cap[i] = 0
                    c.refresh(i)
            for dx, dy, color in strand["pix"][half:strand["shown"]]:
                px, py = strand["x"] + dx + target, strand["y"] + dy
                if 0 <= px < w and 0 <= py < h:
                    i = py * w + px
                    c.cap[i] = color
                    c.refresh(i)
        self.swing = target

    # ---- persistence -------------------------------------------------------

    def to_state(self):
        return [[s["x"], s["y"], s["seed"], s["shown"]] for s in self.strands]

    def load(self, rows):
        if rows:
            for x, y, seed, shown in rows:
                strand = {"x": x, "y": y, "seed": seed, "pix": self._pixels(seed, x, y), "shown": 0}
                self.strands.append(strand)
                self._show(strand, min(shown, len(strand["pix"])))
            return
        self.prewarm()

    def prewarm(self):
        """A forest that has been here a while already has some moss on its boughs."""
        self._find_anchors()
        if not self.anchors:
            return
        for _ in range(min(9, len(self.anchors) // 12)):
            self._clump(self.rng.choice(self.anchors), shown=True)

