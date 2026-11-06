"""Things that may or may not be there.

Nothing here is on a schedule anyone can rely on. Each oddity has its own conditions:
rare states of the ecosystem, the phase of the real moon, the hour on your clock, the
date. Most are glimpses, and several are only ever a shape against the sky. Everything
that has ever been seen is remembered, and shows up on the menu's Oddities page.
"""

import math
import random
import time

from . import palette, scene as scene_mod
from .palette import CANOPY, EYE, FIREFLY, FLOWER, FROG, HARE, LEAF, LITTER, MOON, MYC, SNOW, STAG, TRUNK
from .wildlife import Creature, Flyer, Walker, stamp

# ---- sprites ---------------------------------------------------------------

WATCHER = [
    "...##...",
    "...##...",
    "..####..",
    ".#.##.#.",
    ".#.##.#.",
    ".#.##.#.",
    "#..##..#",
    "#..##..#",
    "#..##..#",
    "#..##..#",
    "...##...",
    "...##...",
    "...##...",
    "...##...",
    "..#..#..",
    "..#..#..",
    "..#..#..",
    "..#..#..",
    "..#..#..",
    "..#..#..",
    "..#..#..",
    ".##..##.",
]
MOSSBACK = (
    [
        "....####.....",
        "...######....",
        "...######....",
        "..########...",
        ".##########..",
        ".##########..",
        "##.#######.#.",
        "##.#######.##",
        "##.#######.##",
        "#..#######..#",
        "...#######...",
        "...###.###...",
        "...###.###...",
        "..###...###..",
        "..###...###..",
        ".####...###..",
    ],
    [
        "....####.....",
        "...######....",
        "...######....",
        "..########...",
        ".##########..",
        ".##########..",
        ".##.#######.#",
        "##..#######.#",
        "##..#######.#",
        "#...#######..",
        "...#######...",
        "...###.###...",
        "...###.###...",
        "...###..###..",
        "..###...###..",
        "..###...####.",
    ],
)
MOTHMAN = (
    [
        "#.............#",
        "##...........##",
        "###..##.##..###",
        "####.#R#R#.####",
        "#####.###.#####",
        ".#############.",
        "..####.#.####..",
        "...##..#..##...",
        "......#.#......",
    ],
    [
        "...............",
        "......##.##....",
        "......#R#R#....",
        "##.....###...##",
        "###############",
        ".#############.",
        "..####.#.####..",
        "......#.#......",
        "......#.#......",
    ],
)
POND_NECK = [
    "..###.",
    ".####E",
    "#####.",
    "###...",
    "##....",
    "##....",
    "###...",
    ".##...",
    ".##...",
    ".###..",
]
POND_HUMP = [
    "..####..",
    ".######.",
    "########",
]
WOLF = [
    "..........#.",
    ".........##.",
    "........###.",
    ".......####.",
    ".......##...",
    "......###...",
    ".#...####...",
    ".##.#####...",
    "..#######...",
    "..#######...",
    "..##...##...",
    "..#.....#...",
    ".##.....##..",
]
KODAMA = (
    [
        ".###.",
        "#O#O#",
        "#####",
        ".#.#.",
    ],
    [
        ".###..",
        "O#O##.",
        "#####.",
        ".#.#..",
    ],
)
SAUCER = [
    "....###....",
    "...#####...",
    ".#########.",
    "#o#o#o#o#o#",
    ".#########.",
]
WITCH = [
    "......#.......",
    ".....###......",
    "....#####.....",
    "...#######....",
    "..##.###.##...",
    "......###.....",
    ".....####.....",
    "#...#####.....",
    "##########=====",
    "#.....##.......",
]
SLEIGH = [
    ".......#.........#.........#....",
    "##.#..###.......###.......###...",
    "#####.####.##..#####.##..#####..",
    "#####..#.#...#..#.#....#..#.#...",
    "##.....#.#......#.#.......#.#...",
]
CAT = (
    [
        "#.#.....",
        "###....#",
        "E#E...#.",
        ".#####..",
        ".#####..",
        ".#...#..",
    ],
    [
        "#.#.....",
        "###.....",
        "E#E....#",
        ".#####.#",
        ".#####..",
        "..#.#...",
    ],
)
GOOSE = ["..#", ".##", "###", ".#."]
DRAGONFLY = (["#.#.#", ".###.", "..#..", "..#.."], [".....", "#####", "..#..", "..#.."])
DUCK = [
    "...##.",
    "...#EO",
    "#.###.",
    "#####.",
    ".###..",
]
ERMINE = (
    ["......##", "#....###", ".######.", "..#..#.."],
    ["......##", "#....###", ".######.", ".#....#."],
)
EGG = ["..#..", ".#w#.", "#w#w#", "#####", ".###."]

SHADOW = {"#": CANOPY[0], "=": TRUNK[1], "o": EYE[1], "R": FLOWER[4][2], "E": FLOWER[4][1]}
SKY_START = palette.SKY.start
SKY_END = palette.SKY.start + palette.SKY.n


def full_moon_nearness():
    """0 at new moon, 1 at full."""
    return 1.0 - abs(scene_mod.moon_phase() - 0.5) * 2.0


class Oddity(Creature):
    pass


class Watcher(Oddity):
    """Stands among the far trees. Never moves while you look. Only ever a shape."""

    name = "watcher"

    def __init__(self, wild, storm_only=False):
        super().__init__(wild)
        eco = self.eco
        self.storm_only = storm_only
        self.life = self.seconds(self.rng.uniform(120, 300))
        self.x = int(eco.s.moon[0] + self.rng.uniform(-90, 90) * eco.scale_s)
        self.y = eco.gy + 1
        self.shift = self.seconds(self.rng.uniform(25, 60))

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        self.shift -= 1
        if self.shift <= 0:  # the moment the shape changes place, it is a little closer to the middle
            self.shift = self.seconds(self.rng.uniform(25, 60))
            self.x += int((eco.w // 2 - self.x) * 0.2) + self.rng.randint(-14, 14)
        if self.storm_only and not eco.climate.flash:
            return self.life > 0
        placed = stamp(eco, overlay, WATCHER, self.x, self.y, False, SHADOW)
        if placed:
            self.seen()
        return self.life > 0


class Mossback(Walker):
    """Something very large, walking along the far treeline at a patient pace."""

    name = "mossback"
    half = 6

    def __init__(self, wild):
        super().__init__(wild, None, wild.eco.gy + 2)
        self.step = 0

    def frame(self, overlay):
        eco = self.eco
        if eco.frame_no % 3 == 0:
            self.x += self.dir
            self.step += 1
            eco.climate.footprint("mossback", self.x + 6, self.y + 1, self.step)
        if self.x < -20 or self.x > eco.w + 20:
            return False
        rows = MOSSBACK[(self.step // 6) % 2]
        colors = {"#": CANOPY[0] if eco.daylight < 0.4 else TRUNK[0]}
        placed = stamp(eco, overlay, rows, self.x, self.y, self.dir < 0, colors)
        if placed:
            self.wild.odd.sighted(self.name)
            # A few pixels of old moss along the shoulder, for anyone looking closely.
            for i in placed[:14:3]:
                overlay[i] = LEAF[1]
        return True


class Mothman(Flyer):
    """Once across the moon, wings wide, on a night the moths have been thick."""

    name = "mothman"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        mx, my, _ = eco.s.moon
        self.dir = self.rng.choice((-1, 1))
        self.x = float(mx - self.dir * 130 * eco.scale_s)
        self.y = float(my + self.rng.randint(-4, 8))
        self.end = float(mx + self.dir * 130 * eco.scale_s)

    def frame(self, overlay):
        eco = self.eco
        done = self.fly_to(self.end, self.y - 0.3, 24)
        placed = stamp(eco, overlay, MOTHMAN[(eco.frame_no // 3) & 1], int(self.x) - 7, int(self.y), self.dir < 0, SHADOW)
        if placed:
            self.seen()
        return not done


class PondThing(Oddity):
    """Rises from the middle of the pond, looks about, and goes down again. Usually with a frog fewer."""

    name = "pond_thing"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        cx, cy, rx, ry = eco.s.pond
        self.x = cx + self.rng.randint(-rx // 3, rx // 3)
        self.y = cy + self.rng.randint(-ry // 4, ry // 4)
        self.t = 0
        self.span = self.seconds(self.rng.uniform(30, 60))
        self.fed = False

    def frame(self, overlay):
        eco = self.eco
        self.t += 1
        rise = min(1.0, self.t / self.seconds(6), (self.span - self.t) / self.seconds(6))
        if rise <= 0:
            return False
        if self.t % self.seconds(3) == 1:
            self.wild.ripple(self.x, self.y)
            self.wild.ripple(self.x + 16, self.y)
        rows = POND_NECK[:int(len(POND_NECK) * rise) + 1]
        colors = {"#": CANOPY[1], "E": EYE[1]}
        stamp(eco, overlay, rows, self.x, self.y, False, colors)
        humps = POND_HUMP[:max(1, int(3 * rise))]
        stamp(eco, overlay, humps, self.x + 12, self.y + 1, False, {"#": CANOPY[1]})
        stamp(eco, overlay, humps, self.x + 24, self.y + 1, False, {"#": CANOPY[1]})
        self.seen()
        if self.t == self.span // 2 and not self.fed and self.wild.frogs and self.rng.random() < 0.6:
            self.fed = True
            frog = self.rng.choice(self.wild.frogs)
            self.wild.take(("frog", int(frog["x"]), int(frog["y"]), frog))
            self.wild.ripple(int(frog["x"]), int(frog["y"]))
        return True


class UnderIce(Oddity):
    """In a hard winter, a long dark shape moving beneath the ice."""

    name = "under_ice"

    def __init__(self, wild):
        super().__init__(wild)
        cx, cy, rx, ry = self.eco.s.pond
        self.a = self.rng.uniform(0, 6.28)
        self.cx, self.cy, self.rx, self.ry = cx, cy, rx * 0.55, ry * 0.45
        self.life = self.seconds(self.rng.uniform(25, 50))

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        self.a += 0.25 / max(1, eco.fps)
        w = eco.w
        water = eco.s.water
        for k in range(16):
            a = self.a - k * 0.06
            x = int(self.cx + math.cos(a) * self.rx)
            y = int(self.cy + math.sin(a) * self.ry)
            thick = 2 if 3 < k < 12 else 1
            for dy in range(-thick + 1, thick):
                for dx in range(0, 2):
                    i = (y + dy) * w + x + dx
                    if 0 <= i < len(water) and water[i] and (x + y + dx + dy + eco.frame_no // 6) % 3:
                        overlay[i] = CANOPY[3]
        self.seen()
        return self.life > 0


class BlackDog(Oddity):
    """Just two red eyes at the treeline, keeping pace with nothing."""

    name = "black_dog"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        self.x = float(self.rng.choice((eco.w * 0.2, eco.w * 0.7)))
        self.y = eco.gy - 5
        self.dir = self.rng.choice((-1, 1))
        self.life = self.seconds(self.rng.uniform(25, 60))
        self.blink = 0

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        self.x += self.dir * 0.3 * 8 / max(1, eco.fps)
        self.blink = (self.blink + 1) % self.seconds(4)
        if self.blink > self.seconds(0.2):
            i = self.y * eco.w + int(self.x)
            if 0 <= i < eco.w * eco.h - 3 and not eco.c.fg[i]:
                overlay[i] = FLOWER[4][1]
                overlay[i + 3] = FLOWER[4][1]
                self.seen()
        return self.life > 0


class Kodama(Oddity):
    """Small pale tree spirits, gathered where the woods have grown old enough to want them."""

    name = "kodama"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        trees = [p for p in eco.plants if p.kind in ("sapling", "log")]
        self.spots = []
        for _ in range(self.rng.randint(3, 7)):
            if trees:
                t = self.rng.choice(trees)
                self.spots.append([t.x + self.rng.randint(-12, 12), t.y + self.rng.randint(0, 5), self.rng.randint(0, 40)])
        self.life = self.seconds(self.rng.uniform(90, 200))

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        colors = {"#": STAG[2], "O": CANOPY[0]}
        for s in self.spots:
            s[2] += 1
            tilt = (s[2] // 11) % 5 == 0  # heads turn, each in its own time
            if stamp(eco, overlay, KODAMA[1 if tilt else 0], s[0], s[1], False, colors):
                self.seen()
        return self.life > 0 and bool(self.spots)


class Saucer(Flyer):
    """Yes, really. Very seldom. It takes one small thing and leaves a ring."""

    name = "saucer"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        self.dir = self.rng.choice((-1, 1))
        self.x = -12.0 if self.dir > 0 else eco.w + 12.0
        self.y = eco.h * 0.14
        self.target_x = float(self.rng.randint(eco.w // 4, eco.w * 3 // 4))
        self.state = "in"
        self.beam = 0
        self.prey = wild.nearest_prey(self.target_x, eco.gy + eco.span // 2, 9999, frogs=False)
        if self.prey:
            self.target_x = float(self.prey[1])
            self.ground = int(self.prey[2])
        else:
            self.ground = eco.gy + eco.span // 2

    def frame(self, overlay):
        eco = self.eco
        if self.state == "in":
            if self.fly_to(self.target_x, self.y, 60):
                self.state = "beam"
                self.beam = self.seconds(5)
        elif self.state == "beam":
            self.beam -= 1
            top = int(self.y) + 1
            lit = palette.LIT2
            w = eco.w
            # A pale column, widening as it reaches the ground.
            for y in range(top, self.ground + 1):
                half = 1 + (y - top) // 12
                if (y + eco.frame_no) % 3:
                    for dx in range(-half, half + 1):
                        i = y * w + int(self.x) + dx
                        if 0 <= i < w * eco.h and not eco.c.fg[i]:
                            overlay[i] = lit[eco.c.frame[i]]
            if self.beam == self.seconds(1):
                if self.prey and self.wild.prey_alive(self.prey):
                    self.wild.take(self.prey)
                self._ring()
            if self.beam <= 0:
                self.state = "out"
        elif self.fly_to(self.x + self.dir * 200, -14, 120):
            return False
        lights = {"#": HARE[1], "o": FIREFLY[3] if eco.frame_no & 1 else FLOWER[4][2]}
        stamp(eco, overlay, SAUCER, int(self.x) - 5, int(self.y), False, lights)
        self.seen()
        return True

    def _ring(self):
        """A flattened circle, in the moss or the snow, that lasts until they grow back over it."""
        eco = self.eco
        c = eco.c
        r = 11.0
        for k in range(70):
            a = k / 70 * math.tau
            x, y = int(self.x + math.cos(a) * r), int(self.ground + math.sin(a) * r * 0.45)
            if 0 <= x < eco.w and eco.gy <= y < eco.h:
                eco._moss_set(x // 2, (y - eco.gy) // 2, 0)
                i = y * c.w + x
                if c.snow[i]:
                    c.snow[i] = SNOW[0]
                    eco.climate.prints[i] = 1
                    c.refresh(i)
        eco.wild.evo.say("There is a perfect ring in the ground this morning.")


class Werewolf(Oddity):
    """On the ridge at the full moon, muzzle raised. Everything small goes quiet and runs."""

    name = "werewolf"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        self.x = int(eco.s.moon[0] + self.rng.choice((-1, 1)) * 34 * eco.scale_s)
        self.y = eco.gy - 2
        self.life = self.seconds(self.rng.uniform(20, 40))
        for v in wild.visitors:  # the rabbits know
            if v.name == "rabbit":
                v.flee = True

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        if stamp(eco, overlay, WOLF, self.x, self.y, False, {"#": CANOPY[0]}):
            self.seen()
        return self.life > 0


class GhostStag(Oddity):
    """After the last stag is gone. It walks the same way they walked, and drinks from nothing."""

    name = "ghost_stag"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        self.dir = self.rng.choice((-1, 1))
        self.x = -26.0 if self.dir > 0 else eco.w + 26.0
        self.y = eco.gy + int(eco.span * self.rng.uniform(0.25, 0.5))
        self.step = 0
        self.g = {"size": 0.5, "ornament": 0.5, "coat": 0.57, "glow": 0.0, "active": 0.2}

    def frame(self, overlay):
        from .sprites import animal_rows

        eco = self.eco
        if eco.frame_no % 5 == 0:
            self.x += self.dir
            self.step += 1
            self.y = self.wild.steer(int(self.x) + 11, self.y, self.y, self.dir)
        if self.x < -30 or self.x > eco.w + 30:
            return False
        rows = animal_rows("stag", f"walk{(self.step // 4) % 2}", self.g)
        # Not drawn so much as lit: the world shows straight through it.
        frame = eco.c.frame
        lit = palette.LIT2
        placed = stamp(eco, {}, rows, int(self.x), self.y, self.dir < 0, {"#": 1, "+": 1, "O": 1, "w": 1, "^": 1})
        for i in placed:
            overlay[i] = lit[frame[i]]
        if placed:
            self.seen()
        return True


class WitchAcrossTheMoon(Flyer):
    name = "witch"
    sprite = WITCH
    speed = 34

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        mx, my, _ = eco.s.moon
        self.dir = self.rng.choice((-1, 1))
        self.x = float(mx - self.dir * 170 * eco.scale_s)
        self.y = float(my + 6)
        self.end = float(mx + self.dir * 170 * eco.scale_s)

    def frame(self, overlay):
        eco = self.eco
        done = self.fly_to(self.end, self.y - 0.5, self.speed)
        placed = stamp(eco, overlay, self.sprite, int(self.x) - len(self.sprite[0]) // 2,
                       int(self.y + math.sin(eco.frame_no * 0.2) * 1.5), self.dir < 0, SHADOW)
        if placed:
            self.wild.odd.sighted(self.name)
        return not done


class SleighAcrossTheMoon(WitchAcrossTheMoon):
    name = "sleigh"
    sprite = SLEIGH
    speed = 44


class BlackCat(Walker):
    """Friday the 13th. It crosses your path, and looks at you."""

    name = "black_cat"
    half = 3

    def __init__(self, wild):
        super().__init__(wild, None)
        self.step = 0

    def frame(self, overlay):
        eco = self.eco
        if self.go_off(9):
            return False
        self.step += 1
        placed = stamp(eco, overlay, CAT[(self.step // 3) % 2], self.x, self.y, self.dir > 0, {"#": CANOPY[0], "E": FIREFLY[3]})
        if placed:
            self.wild.odd.sighted(self.name)
        return True


# ---- seasonal animals ------------------------------------------------------


class Geese(Flyer):
    """A long V high overhead, north in spring and south in autumn."""

    name = "geese"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        self.dir = self.rng.choice((-1, 1))
        self.x = -60.0 if self.dir > 0 else eco.w + 60.0
        self.y = eco.h * self.rng.uniform(0.07, 0.22)
        self.count = self.rng.randint(5, 11)

    def frame(self, overlay):
        eco = self.eco
        self.x += self.dir * 22 / max(1, eco.fps)
        if self.x < -90 or self.x > eco.w + 90:
            return False
        for k in range(self.count):
            side = 1 if k % 2 else -1
            rank = (k + 1) // 2
            px = int(self.x - self.dir * rank * 7)
            py = int(self.y + side * rank * 3 + math.sin(eco.frame_no * 0.3 + k) * 0.7)
            stamp(eco, overlay, GOOSE, px, py, self.dir > 0, {"#": CANOPY[0]})
        self.wild.odd.sighted(self.name)
        return True


class Dragonflies(Oddity):
    """Summer, over the water. They hover, dart, and take moths on the wing."""

    name = "dragonfly"

    def __init__(self, wild):
        super().__init__(wild)
        rng = self.rng
        self.life = self.seconds(rng.uniform(70, 170))
        cx, cy, rx, ry = self.eco.s.pond
        self.flies = [{"x": cx + rng.uniform(-rx, rx), "y": cy - rng.uniform(2, 12), "wait": rng.randint(0, 30), "tx": 0.0, "ty": 0.0,
                       "hue": rng.choice((MYC[2], MYC[0], FLOWER[4]))} for _ in range(rng.randint(2, 4))]

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        self.life -= 1
        cx, cy, rx, ry = eco.s.pond
        for d in self.flies:
            d["wait"] -= 1
            if d["wait"] <= 0:
                d["wait"] = rng.randint(6, 40)
                d["tx"], d["ty"] = cx + rng.uniform(-rx * 1.1, rx * 1.1), cy - rng.uniform(0, 14)
            d["x"] += (d["tx"] - d["x"]) * 0.3
            d["y"] += (d["ty"] - d["y"]) * 0.3
            for m in eco.moths:
                if abs(m["x"] - d["x"]) < 3 and abs(m["y"] - d["y"]) < 3:
                    eco.moths.remove(m)
                    self.wild.note("moth taken by dragonfly")
                    break
            hue = d["hue"]
            stamp(eco, overlay, DRAGONFLY[eco.frame_no & 1], int(d["x"]) - 2, int(d["y"]), False, {"#": hue[1]})
        self.wild.odd.sighted(self.name)
        return self.life > 0


class Ducks(Oddity):
    """A pair on the pond, spring to autumn; in spring, a line of ducklings behind."""

    name = "ducks"

    def __init__(self, wild):
        super().__init__(wild)
        rng = self.rng
        cx, cy, rx, ry = self.eco.s.pond
        self.a = rng.uniform(0, 6.28)
        self.cx, self.cy, self.rx, self.ry = cx, cy, rx * 0.6, ry * 0.5
        self.life = self.seconds(rng.uniform(100, 240))
        self.ducklings = rng.randint(3, 6) if self.eco.climate.season == "spring" else 0

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        if eco.climate.frozen:
            return False
        self.a += 0.12 / max(1, eco.fps)
        w = eco.w
        for k in range(2):
            a = self.a - k * 0.35
            x, y = int(self.cx + math.cos(a) * self.rx), int(self.cy + math.sin(a) * self.ry)
            colors = {"#": FROG[1] if k == 0 else LITTER[3], "E": CANOPY[0], "O": EYE[1]}  # the drake, and the duck
            flip = math.sin(a) > 0
            stamp(eco, overlay, DUCK, x - 3, y, flip, colors)
            if eco.frame_no % 20 == k:
                self.wild.ripple(x, y + 2)
        for k in range(self.ducklings):
            a = self.a - 0.7 - k * 0.2
            x, y = int(self.cx + math.cos(a) * self.rx), int(self.cy + math.sin(a) * self.ry)
            i = y * w + x
            if eco.s.water[i]:
                overlay[i] = EYE[1]
                overlay[i + 1] = EYE[2]
        self.wild.odd.sighted(self.name)
        return self.life > 0


class Ermine(Walker):
    """White as the snow it runs on, in sudden bursts. Black tip to the tail is all that shows."""

    name = "ermine"
    half = 4

    def __init__(self, wild):
        super().__init__(wild, None)
        self.pause = 0
        self.step = 0

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        if self.pause > 0:
            self.pause -= 1
        else:
            if self.go_off(26):
                return False
            self.step += 1
            eco.climate.footprint("ermine", self.x + 3, self.y, self.step)
            if rng.random() < 0.05:
                self.pause = self.seconds(rng.uniform(0.5, 2.5))
        placed = stamp(eco, overlay, ERMINE[(self.step // 2) % 2], self.x, self.y, self.dir < 0, {"#": STAG[2]})
        if placed:
            overlay[placed[0]] = CANOPY[0]
            self.wild.odd.sighted(self.name)
        return True


from .climbers import MARTEN, WOODPECKER, CREEPER, ClimbingSquirrel, PineMarten, Treecreeper, Woodpecker  # noqa: E402

ODD_VISITORS = {"climbing_squirrel": ClimbingSquirrel, "pine_marten": PineMarten, "woodpecker": Woodpecker, "treecreeper": Treecreeper,
                "watcher": Watcher, "mossback": Mossback, "mothman": Mothman, "pond_thing": PondThing, "under_ice": UnderIce,
                "black_dog": BlackDog, "kodama": Kodama, "saucer": Saucer, "werewolf": Werewolf, "ghost_stag": GhostStag,
                "witch": WitchAcrossTheMoon, "sleigh": SleighAcrossTheMoon, "black_cat": BlackCat,
                "geese": Geese, "dragonfly": Dragonflies, "ducks": Ducks, "ermine": Ermine}
# Everything in the collection: name -> (title, riddle while unseen, note once seen)
CATALOGUE = {
    "watcher": ("THE WATCHER", "It never moves while you look.", "Tall and thin among the far trees. Lightning shows it best."),
    "mossback": ("OLD MOSSBACK", "The largest thing on the ridge.", "Walks the far treeline. Leaves huge tracks in snow."),
    "big_tracks": ("BIG TRACKS", "Look down, after snowfall.", "Enormous prints in the snow, going nowhere."),
    "mothman": ("THE GREAT MOTH", "When wings are many, watch the moon.", "Crossed the moon, wings wide, on a night of many moths."),
    "pond_thing": ("POND THING", "When the frogs grow bold.", "Rises from the middle of the water. Frogs disappear."),
    "under_ice": ("BELOW THE ICE", "In the deepest cold, look down.", "A long dark shape moving beneath the frozen pond."),
    "black_dog": ("BLACK DOG", "Two points of red, in the small hours.", "Just the eyes, keeping pace along the treeline."),
    "kodama": ("KODAMA", "They come when the woods grow old.", "Pale spirits, gathering where trees have stood long."),
    "saucer": ("VISITOR", "Keep watching the skies.", "Took something small, and left a ring in the ground."),
    "werewolf": ("THE HOWLER", "Under a full moon only.", "A shape on the ridge. Rabbits bolt when it comes."),
    "ghost_stag": ("GHOST STAG", "Lose something you can't get back.", "Appears when no stag is left. The world shows through it."),
    "jackalope": ("JACKALOPE", "It has to evolve. Be patient.", "A rabbit born with antlers. Nobody planned this."),
    "witch": ("THE WITCH", "One night in autumn.", "Crossed the moon on the last nights of October."),
    "sleigh": ("THE SLEIGH", "One night in December.", "Very late on the night before Christmas."),
    "black_cat": ("BLACK CAT", "Beware a certain Friday.", "Friday the 13th. It looked straight at you."),
    "geese": ("GEESE", "Look up, at the turn of the year.", "North in spring and south in autumn."),
    "dragonfly": ("DRAGONFLY", "Summer, over the water.", "Hovers, darts, and takes moths on the wing."),
    "ducks": ("DUCKS", "They need open water.", "Paddle the pond spring to autumn, with young in spring."),
    "ermine": ("ERMINE", "White on white, in deep snow.", "Runs in bursts across the snow. Black-tipped tail."),
    "woodpecker": ("WOODPECKER", "Listen to the bark, by day.", "Works up a trunk in hops, drilling for beetles."),
    "treecreeper": ("TREECREEPER", "A brown mote, circling a trunk.", "Spirals up one tree, then flutters to the foot of the next."),
    "pine_marten": ("PINE MARTEN", "Squirrels aren't safe up there.", "Long, dark and fast. Hunts squirrels up the trunks."),
    "aurora": ("AURORA", "The coldest clear nights.", "Green curtains over the winter forest."),
}
CATALOGUE_SPRITES = {"watcher": WATCHER, "mossback": MOSSBACK[0], "mothman": MOTHMAN[0], "pond_thing": POND_NECK, "kodama": KODAMA[0], "saucer": SAUCER,
                     "werewolf": WOLF, "witch": WITCH, "sleigh": SLEIGH, "black_cat": CAT[0], "geese": GOOSE, "dragonfly": DRAGONFLY[0], "ducks": DUCK,
                     "ermine": ERMINE[0], "woodpecker": WOODPECKER, "treecreeper": CREEPER, "pine_marten": MARTEN}


class Oddities:
    """Decides what appears, remembers what has appeared, and dresses the forest for the date."""

    def __init__(self, wild):
        self.wild = wild
        self.eco = wild.eco
        self.rng = wild.eco.rng
        self.watcher_storm = 0
        self.eggs = []
        self.egg_key = None
        self.pond_was = False
        self.firework = []

    # ---- the collection ----------------------------------------------------

    def sighted(self, name):
        table = self.eco.stats.setdefault("oddities", {})
        entry = table.get(name)
        now = int(time.time())
        if entry is None or now - entry["last"] > 600:  # one glimpse is one sighting, however long it lasts
            first = entry is None
            if first:
                table[name] = entry = {"n": 0, "first": now, "last": now}
            entry["n"] += 1
            entry["last"] = now
            if first and name in CATALOGUE:
                self.wild.evo.say(f"Something new in the forest: {CATALOGUE[name][0].title()}.")
        else:
            entry["last"] = now

    # ---- what appears ------------------------------------------------------

    def summon(self, name, force=True):
        cls = ODD_VISITORS.get(name)
        if not cls or self.wild.find(name):
            return False
        self.wild.visitors.append(cls(self.wild))
        return True

    def check(self):
        """Every two minutes or so, whether anything has a reason to show itself."""
        eco, rng = self.eco, self.rng
        climate = eco.climate
        when = climate.now()
        tags = climate.today_tags
        night = eco.daylight < 0.1
        dusk = eco.daylight < 0.4
        wild = self.wild
        evo = wild.evo
        frogs = len(wild.frogs)
        moon = full_moon_nearness()

        def chance(p):
            return rng.random() < p

        # The seasonal residents and visitors.
        if climate.season in ("spring", "autumn") and chance(0.012):
            self.summon("geese")
        if climate.season == "summer" and eco.daylight > 0.2 and chance(0.02):
            self.summon("dragonfly")
        if climate.season != "winter" and not climate.frozen and eco.daylight > 0.1 and chance(0.012):
            self.summon("ducks")
        if climate.snow > 0.4 and chance(0.02):
            self.summon("ermine")
        # Life on the trunks.
        if eco.daylight > 0.2 and chance(0.02):
            self.summon("woodpecker")
        if eco.daylight > 0.1 and chance(0.02):
            self.summon("treecreeper")
        if evo.awake_count("squirrel") and chance(0.02):
            self.summon("climbing_squirrel")
        if dusk and chance(0.006):
            self.summon("pine_marten")
        elif self.wild.find("climbing_squirrel") and dusk and chance(0.2):
            self.summon("pine_marten")  # a squirrel on the trunk is what brings one
        if not eco.cfg.oddities:
            return
        # Rare states of the world.
        if night and eco.moths and len(eco.moths) >= int(10 * eco.cfg.density) and chance(0.03):
            self.summon("mothman")
        if night and not climate.frozen and frogs >= 6 and chance(0.02):
            self.summon("pond_thing")
        trees = sum(1 for p in eco.plants if p.kind in ("sapling", "log"))
        if dusk and trees >= 4 and chance(0.012):
            self.summon("kodama")
        if not evo.pools["stag"] and chance(0.04):
            self.summon("ghost_stag")
        if climate.frozen and night and chance(0.012):
            self.summon("under_ice")
        # Long odds at any time.
        if night and chance(0.0015):
            self.summon("pond_thing") if not climate.frozen else None
        if dusk and chance(0.0015):
            self.summon("mossback")
        if night and chance(0.0007):
            self.summon("saucer")
        # The moon.
        if night and moon > 0.92 and chance(0.012):
            self.summon("werewolf")
        # The hour on your clock.
        hour, minute = when.hour, when.minute
        if hour == 3 and minute in (33, 34) and chance(0.5):
            self.summon("watcher")
        elif night and chance(0.0012):
            self.summon("watcher")
        if 2 <= hour < 4 and chance(0.01 + (0.08 if "friday_13th" in tags else 0)):
            self.summon("black_dog")
        # Dates.
        if "hallows_eve" in tags and night and chance(0.06):
            self.summon("witch")
        if "christmas_eve" in tags and night and hour >= 22 and chance(0.2):
            self.summon("sleigh")
        if "friday_13th" in tags and chance(0.02):
            self.summon("black_cat")
        if "perseids" in tags and night and chance(0.8):
            wild.summon("star")
        if "midsummer" in tags and dusk and chance(0.05):
            wild.summon("ring")

    def tick(self):
        eco = self.eco
        if eco.tick_no % 240 == 0 and eco.cfg.fauna:
            self.check()
        # A storm may bring the Watcher, visible only in the lightning.
        climate = eco.climate
        if climate.storm == 1:
            self.watcher_storm = 0
        if eco.cfg.oddities and climate.storm > 100 and not self.watcher_storm and eco.tick_no % 200 == 0:
            self.watcher_storm = 1
            if self.rng.random() < 0.35 and not self.wild.find("watcher"):
                self.wild.visitors.append(Watcher(self.wild, storm_only=True))

    def on_rabbit_seen(self, rabbit):
        if rabbit.g["ornament"] > 0.7:
            self.sighted("jackalope")

    # ---- the forest dressed for the date -----------------------------------

    def frame(self, overlay):
        eco = self.eco
        if not eco.cfg.oddities:
            return
        tags = eco.climate.today_tags
        if not tags:
            return
        night = eco.daylight < 0.25
        if "halloween" in tags and night:
            self._jack_o_lanterns(overlay)
        if "yuletide" in tags and night:
            self._fairy_lights(overlay)
        if "new_year" in tags:
            self._fireworks(overlay)
        if "valentine" in tags and night:
            self._heart(overlay)
        if "easter" in tags:
            self._eggs(overlay)
        if "st_patrick" in tags:
            self._lucky_clover(overlay)
        if "april_fools" in tags:
            self._square_moon(overlay)

    def _jack_o_lanterns(self, overlay):
        eco = self.eco
        w = eco.w
        for p in eco.plants:
            if p.kind == "pumpkin" and p.shown > len(p.pix) * 0.7:
                flicker = (eco.frame_no + p.pid) % 11 < 2
                c = EYE[1] if flicker else EYE[2]
                y = p.y - 3
                for dx, dy in ((-2, 0), (2, 0), (-2, 2), (-1, 2), (0, 2), (1, 2), (2, 2)):
                    i = (y + dy) * w + p.x + dx
                    if 0 <= i < w * eco.h and not eco.c.fg[i]:
                        overlay[i] = c

    def _fairy_lights(self, overlay):
        """On the tallest young tree. Who put them there is another question."""
        eco = self.eco
        trees = [p for p in eco.plants if p.kind == "sapling" and p.shown > 20]
        if not trees:
            return
        tree = max(trees, key=lambda p: p.shown)
        hues = (FLOWER[4][2], FLOWER[5][2], FLOWER[0][2], FLOWER[7][2], FLOWER[3][2])
        w = eco.w
        for k in range(0, tree.shown, max(1, tree.shown // 18)):
            dx, dy, _ = tree.pix[k]
            i = (tree.y + dy) * w + tree.x + dx
            if 0 <= i < w * eco.h and not eco.c.fg[i] and (k + eco.frame_no // 6) % 3:
                overlay[i] = hues[k % len(hues)]

    def _fireworks(self, overlay):
        """Far off and quiet, over the trees, around midnight."""
        eco, rng = self.eco, self.rng
        when = eco.climate.now()
        midnight = (when.month, when.day, when.hour) in ((12, 31, 23), (1, 1, 0))
        if midnight and rng.random() < 0.03 and len(self.firework) < 3:
            self.firework.append([rng.randrange(eco.w // 5, eco.w * 4 // 5), rng.randrange(eco.h // 12, eco.gy // 2), 0,
                                  rng.choice((FLOWER[4], FLOWER[5], FLOWER[0], FLOWER[7], FLOWER[3]))])
            self.sighted("fireworks")
        w = eco.w
        comp = eco.c.comp
        for fw in list(self.firework):
            fw[2] += 1
            r = fw[2] * 1.3
            if r > 13:
                self.firework.remove(fw)
                continue
            shade = fw[3][2 if r < 6 else 1 if r < 10 else 0]
            for k in range(16):
                a = k * math.tau / 16
                x, y = int(fw[0] + math.cos(a) * r), int(fw[1] + math.sin(a) * r * 0.85)
                if 0 <= x < w and 0 <= y < eco.gy:
                    i = y * w + x
                    if SKY_START <= comp[i] < SKY_END:  # behind the leaves
                        overlay[i] = shade

    def _heart(self, overlay):
        """Valentine's night: for a while, the fireflies over the clearing keep a certain shape."""
        eco = self.eco
        cycle = eco.frame_no % (eco.fps * 120)
        if cycle > eco.fps * 25:
            return
        self.sighted("heart")
        cx, cy = eco.w // 2, eco.gy + eco.span // 3
        w = eco.w
        for k in range(48):
            t = k / 48 * math.tau
            x = int(cx + 16 * math.sin(t) ** 3)
            y = int(cy - (13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)))
            if (k + eco.frame_no // 4) % 5 < 3:
                i = y * w + x
                if 0 <= i < w * eco.h and not eco.c.fg[i]:
                    overlay[i] = FLOWER[3][2]

    def _eggs(self, overlay):
        """Hidden in the meadow for the Easter weekend. There are always seven."""
        eco = self.eco
        year = eco.climate.now().year
        if self.egg_key != year:
            self.egg_key = year
            rng = random.Random(year ^ eco.s.seed)
            self.eggs = []
            hues = (FLOWER[0], FLOWER[3], FLOWER[5], FLOWER[7], FLOWER[2], FLOWER[4], FLOWER[1])
            for k in range(7):
                for _ in range(20):
                    x, y = rng.randrange(eco.w), rng.randrange(eco.gy + 6, eco.h - 4)
                    if eco.s.ground[y * eco.w + x]:
                        self.eggs.append((x, y, hues[k]))
                        break
        for x, y, hue in self.eggs:
            stamp(eco, overlay, EGG, x, y, False, {"#": hue[1], "w": hue[2]})
        self.sighted("eggs")

    def _lucky_clover(self, overlay):
        """St Patrick's Day. Every clover has a glint of gold today, for those who look."""
        eco = self.eco
        w = eco.w
        for p in eco.plants:
            if p.kind == "clover" and (p.pid * 7 + eco.frame_no // 8) % 19 == 0:
                i = (p.y - 2) * w + p.x
                if 0 <= i < w * eco.h and not eco.c.fg[i]:
                    overlay[i] = EYE[2]
        self.sighted("lucky_clover")

    def _square_moon(self, overlay):
        """Happy April 1st. Nothing to see here."""
        eco = self.eco
        mx, my, r = eco.s.moon
        w = eco.w
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                i = (my + y) * w + mx + x
                if 0 <= i < w * eco.h and not eco.c.fg[i]:
                    overlay[i] = MOON[4] if (x * 3 + y * 5) & 7 else MOON[3]
        self.sighted("square_moon")


CATALOGUE.update({
    "fireworks": ("FIREWORKS", "As one year turns to the next.", "Far off and silent, over the trees at midnight."),
    "heart": ("A HEART", "When love is in the air.", "February 14th. The fireflies keep a certain shape."),
    "eggs": ("SEVEN EGGS", "A spring weekend that moves.", "Hidden in the meadow for Easter."),
    "lucky_clover": ("LUCKY CLOVER", "Green, in the middle of March.", "St Patrick's Day: a glint of gold in the clover."),
    "square_moon": ("THE MOON?", "Don't be fooled.", "April 1st. Nothing to see here."),
    "rainbow": ("RAINBOW", "Sun, straight after rain.", "A whole arch over the clearing, when the sun follows rain."),
})
