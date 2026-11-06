"""Animals and happenings that come and go, each one changing the forest a little.

Residents (frogs, snails) live here and are saved with the world. Visitors are real
members of the populations in evolution.py: who turns up depends on who is alive and
awake, and whatever happens to them on screen happens to their line.

  rabbit     nibbles plants, carries seed, fertilises; bolts from the fox
  fox        stalks rabbits, or sniffs about and drinks at the pond
  owl        glides down from the bough to take a beetle, snail or frog
  bats       hawk moths over the clearing
  hedgehog   snuffles after beetles and snails
  songbird   eats fruit and flowers, and sows the seeds further along
  squirrel   buries nuts; the forgotten ones become trees
  wisp       a light over the pond that draws moths and rouses the mycelium
  star       a shooting star, seen only through gaps in the leaves
  wind       a gust that carries leaves, spores and seeds across the clearing
  ring       a fairy ring: one fungal colony fruiting in a circle
"""

import math
from collections import deque

from . import palette
from .palette import FLOWER, FROG, LITTER, MYC, SPORE, STAG, STAR
from .evolution import PREY, Evolution, coat_colors, coat_dark
from .sprites import animal_rows


def sign(v):
    return (v > 0) - (v < 0)


def stamp(eco, overlay, rows, x, feet, flip, colors, reflect=None):
    """Draw a sprite with its last row on `feet`. Returns the pixels it actually covered."""
    c = eco.c
    w, h = eco.w, eco.h
    fg, life, life_z = c.fg, c.life, c.life_z
    water = eco.s.water
    z = (feet - eco.gy) * 255 // eco.span if feet >= eco.gy else 0
    top = feet - len(rows) + 1
    placed = []
    if feet >= eco.gy + 1 and reflect is not None:
        # A soft shadow on the ground under anything standing on it. The flyers and the shapes on the ridge don't cast one.
        n = len(rows[-1])
        dark = palette.DARK
        frame = c.frame
        for row_offset, inset in ((1, 0), (2, 2)):
            y = feet + row_offset
            if 0 <= y < h:
                for dx in range(inset, n - inset):
                    px = x + dx
                    if 0 <= px < w:
                        i = y * w + px
                        if not fg[i] and not life[i] and not water[i] and (inset == 0 or (px + y) & 1):
                            overlay[i] = dark[frame[i]]
    for ry, row in enumerate(rows):
        y = top + ry
        if not 0 <= y < h:
            continue
        n = len(row)
        for rx, ch in enumerate(row):
            if ch == ".":
                continue
            px = x + (n - 1 - rx if flip else rx)
            if not 0 <= px < w:
                continue
            i = y * w + px
            if fg[i] or (life[i] and life_z[i] > z):
                continue
            overlay[i] = colors[ch]
            placed.append(i)
            if reflect is not None:
                my = 2 * feet + 1 - y
                if my < h and water[my * w + px] and my & 1:
                    j = my * w + px
                    if j not in overlay:
                        overlay[j] = reflect
    return placed


def halo(eco, overlay, placed, lut):
    """A soft light on whatever a pale or luminous animal is passing."""
    w, h = eco.w, eco.h
    frame = eco.c.frame
    fg = eco.c.fg
    for i in placed:
        for off in (-1, 1, -w, w):
            k = i + off
            if 0 <= k < w * h and k not in overlay and not fg[k]:
                overlay[k] = lut[frame[k]]


class Creature:
    name = "creature"
    ind = None

    def __init__(self, wild, ind=None):
        self.wild = wild
        self.eco = wild.eco
        self.rng = wild.eco.rng
        self.move = 0.0
        self.age = 0
        self.ind = ind
        self.g = ind["g"] if ind else None

    def body(self, overlay, pose, x, feet, flip, reflect=True):
        # (`reflect` also means "stands on the ground", so it casts a shadow too.)
        placed = stamp(self.eco, overlay, animal_rows(self.name, pose, self.g), x, feet, flip, coat_colors(self.g),
                       coat_dark(self.g) if reflect else None)
        if self.g["glow"] > 0.6:
            halo(self.eco, overlay, placed, palette.LIT2)

    def seen(self):
        self.wild.odd.sighted(self.name)

    def fed(self, amount):
        self.ind["e"] = min(3.0, self.ind["e"] + amount)

    def sick(self):
        return self.name in self.wild.evo.plagues

    def steps(self, px_per_second):
        """Whole pixels to move this frame, independent of the frame rate."""
        self.move += px_per_second / max(1, self.eco.fps)
        n = int(self.move)
        self.move -= n
        return n

    def seconds(self, s):
        return int(s * max(1, self.eco.fps))

    def frame(self, overlay):
        """Advance and draw. Return False once gone."""
        return False


class Walker(Creature):
    half = 6

    def __init__(self, wild, ind=None, y=None):
        super().__init__(wild, ind)
        eco = self.eco
        self.dir = self.rng.choice((-1, 1))
        self.x = -18 if self.dir > 0 else eco.w + 18
        self.y = y if y is not None else eco.gy + int(eco.span * self.rng.uniform(0.15, 0.8))

    def go(self, speed, tx, ty):
        """Walk toward a point, around the water. Returns True on arrival."""
        for _ in range(self.steps(speed)):
            dx, dy = tx - self.x, ty - self.y
            if abs(dx) <= 2 and abs(dy) <= 1:
                return True
            if abs(dx) > 2:
                self.dir = sign(dx)
                self.x += self.dir
            ny = self.y + sign(dy)
            self.y = self.wild.steer(self.x + self.half, self.y, ny, self.dir)
            self.strides = getattr(self, "strides", 0) + 1
            self.eco.climate.footprint(self.name, self.x + self.half, self.y + 1, self.strides)
        return abs(tx - self.x) <= 2 and abs(ty - self.y) <= 1

    def go_off(self, speed):
        """Head for the nearest edge. Returns True once out of sight."""
        target = -24 if self.x < self.eco.w / 2 else self.eco.w + 24
        self.go(speed, target, self.y)
        return self.x <= -20 or self.x >= self.eco.w + 20


class Rabbit(Walker):
    name = "rabbit"
    half = 3

    def __init__(self, wild, ind, y=None, delay=0):
        super().__init__(wild, ind, y)
        self.pause = delay
        self.hopping = 0
        self.target = None
        self.nibble = 0
        self.meals = self.rng.randint(2, 5)
        self.seed = None
        self.flee = False
        self.patience = self.seconds(90)
        self.wary = 22 + self.g["wary"] * 70  # how far off it notices a fox
        self.gone = False

    def frame(self, overlay):
        if self.gone:
            return False
        eco, rng = self.eco, self.rng
        self.patience -= 1
        if self.patience <= 0:
            self.meals = 0
        fox = self.wild.find("fox")
        if fox and not self.flee and abs(fox.x - self.x) < self.wary and abs(fox.y - self.y) < self.wary / 2:
            self.flee = True
        hop = False
        if self.flee or self.meals <= 0:
            if self.pause > 0:
                self.pause -= 1
            else:
                hop = True
                if self.go_off(24 + 30 * self.g["speed"] if self.flee else 12):
                    return False
                if not self.flee and rng.random() < 0.02:
                    self.pause = self.seconds(rng.uniform(1, 3))
        elif self.nibble > 0:
            self.nibble -= 1
            if self.nibble == 0:
                self._eat()
        elif self.pause > 0:
            self.pause -= 1
        else:
            if self.target is None or self.target not in eco.plants:
                self._choose()
            if self.target is None:
                self.meals = 0
            else:
                hop = True
                self.hopping += 1
                arrived = self.go((14 + 14 * self.g["speed"]) * (0.5 if self.sick() else 1), self.target.x + (-5 if self.x < self.target.x else 5), self.target.y)
                if arrived:
                    self.nibble = self.seconds(rng.uniform(4, 12))
                elif self.hopping > self.seconds(0.6):
                    self.hopping = 0
                    self.pause = self.seconds(rng.uniform(0.4, 2.5))
        lift = 1 if hop and eco.frame_no & 1 else 0
        if self.nibble and eco.frame_no % 6 < 2:
            lift = -1  # the head dips to eat
        self.body(overlay, "hop" if hop else "sit", self.x, self.y - lift, self.dir < 0)
        return True

    def _choose(self):
        eco = self.eco
        options = [p for p in eco.plants if p.kind in ("clover", "spire", "fern") and p.shown > 6]
        if options:
            near = sorted(options, key=lambda p: abs(p.x - self.x) + abs(p.y - self.y) * 2)[:6]
            self.target = self.rng.choice(near)
        else:
            self.target = None

    def _eat(self):
        eco, rng = self.eco, self.rng
        p = self.target
        self.meals -= 1
        self.target = None
        if p in eco.plants:
            bite = p.g.get("toxin", 0.0) - self.g["tolerance"]
            if bite > 0.25:  # one mouthful of that was enough
                self.fed(-0.3 * bite)
                self.meals = 0
                self.wild.note("rabbit put off by a toxic plant")
                return
            self.fed(0.25)
            eco._unshow(p, max(3, int(p.shown * 0.6)))
            eco._restamp(p)
            if self.seed:
                kind, g, gen = self.seed
                eco._sprout(kind, self.x + rng.randint(-6, 6), self.y + rng.randint(-2, 2), eco._mutate_plant(g), gen + 1)
            self.seed = (p.kind, p.g, p.gen)
        self.wild.enrich(self.x, self.y, 0.05)  # what goes in comes out
        self.pause = self.seconds(rng.uniform(0.5, 3))


class Fox(Walker):
    name = "fox"
    half = 7

    def __init__(self, wild, ind, y=None):
        super().__init__(wild, ind, y)
        self.state = "enter"
        self.timer = self.seconds(self.rng.uniform(10, 20))
        self.spot = None
        self.prey = None
        self.step = 0

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        moving = True
        drink = False
        rabbits = [r for r in self.wild.visitors if isinstance(r, Rabbit) and not r.gone]
        if self.state != "leave" and self.state != "hunt" and rabbits:
            self.prey = min(rabbits, key=lambda r: abs(r.x - self.x) + abs(r.y - self.y))
            self.state = "hunt"
            self.timer = self.seconds(50)
        elif self.state == "wander" and self.g["diet"] > 0.6 and self.wild.frogs:
            # This line of foxes has taken to frogs.
            frog = rng.choice(self.wild.frogs)
            if not frog["swim"]:
                self.spot = (int(frog["x"]), int(frog["y"]))
                self.frog = frog
                self.state = "stalk_frog"
                self.timer = self.seconds(40)
        if self.state == "stalk_frog":
            self.timer -= 1
            frog = self.frog
            if frog not in self.wild.frogs or frog["swim"] or self.timer <= 0:
                self.state = "leave"
            elif self.go(10 + 22 * self.g["speed"], int(frog["x"]) - self.half, int(frog["y"])):
                self.wild.take(("frog", int(frog["x"]), int(frog["y"]), frog))
                self.fed(0.8)
                self.state = "leave"
        elif self.state == "hunt":
            r = self.prey
            self.timer -= 1
            if r is None or r.gone or r not in self.wild.visitors or self.timer <= 0:
                self.state = "leave"
            else:
                d = abs(r.x - self.x) + abs(r.y - self.y)
                self.go((24 + 24 * self.g["speed"]) if d < 55 else 8, r.x, r.y)  # a slow stalk, then the rush
                if d < 5:
                    r.gone = True
                    self.wild.evo.kill("rabbit", r.ind)
                    self.fed(1.4)
                    self.wild.enrich(r.x, r.y, 0.12)
                    self.wild.note("rabbit caught by fox")
                    self.state = "leave"
        elif self.state == "enter":
            bx, by = self.wild._bank(-math.pi / 2 + rng.uniform(-0.7, 0.7))
            self.spot = (int(bx), int(by) - 2)
            self.state = "to_pond" if rng.random() < 0.6 else "wander"
        elif self.state == "to_pond":
            self.timer -= 1
            if self.go(11, self.spot[0] - self.half, self.spot[1]):
                self.state = "drink"
                self.timer = self.seconds(rng.uniform(6, 14))
            elif self.timer <= 0:
                self.state = "leave"
        elif self.state == "drink":
            moving = False
            drink = True
            self.timer -= 1
            if self.timer % self.seconds(2.2) == 0:
                self.wild.ripple(self.x + (self.half * 2 if self.dir > 0 else 0), self.y + 4)
            if self.timer <= 0:
                self.state = "leave"
        elif self.state == "wander":
            self.timer -= 1
            if self.spot is None or self.go(9, *self.spot):
                moving = False
                if rng.random() < 0.04:
                    x, y = eco._random_ground()
                    self.spot = (x, y)
            if self.timer <= 0:
                self.state = "leave"
        elif self.go_off(13):
            return False
        if moving:
            self.step += 1
        self.body(overlay, "drink" if drink else f"walk{(self.step // 2) % 2}", self.x, self.y, self.dir < 0)
        return True


class Hedgehog(Walker):
    name = "hedgehog"
    half = 3

    def __init__(self, wild, ind, y=None):
        super().__init__(wild, ind, y)
        self.life = self.seconds(self.rng.uniform(180, 320))
        self.spot = self.eco._random_ground()
        self.rest = 0
        self.step = 0

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        self.life -= 1
        if self.life <= 0:
            if self.go_off(5):
                return False
            self.step += 1
        elif self.rest > 0:
            self.rest -= 1
        else:
            prey = self.wild.nearest_prey(self.x, self.y, 55, frogs=False)
            if prey:
                self.spot = (prey[1], prey[2])
            if self.go(4, *self.spot):
                if prey and abs(prey[1] - self.x) < 5:
                    self.wild.take(prey)
                    self.fed(0.25)
                    self.rest = self.seconds(rng.uniform(3, 7))
                else:
                    self.spot = eco._random_ground()
                    self.rest = self.seconds(rng.uniform(1, 5))
            self.step += 1
        self.body(overlay, f"walk{(self.step // 3) % 2}", self.x, self.y, self.dir < 0)
        return True


class Squirrel(Walker):
    name = "squirrel"
    half = 3

    def __init__(self, wild, ind, y=None):
        super().__init__(wild, ind, y)
        self.pause = 0
        self.dig = 0
        self.burst = 0
        self.errands = self.rng.randint(1, 3)
        self.spot = self._pick_spot()
        self.patience = self.seconds(30)
        self.leaving_set = False
        self.up_a_tree = False

    def _pick_spot(self):
        eco, rng = self.eco, self.rng
        groves = [p for p in eco.plants if p.kind in ("sapling", "log")]
        if groves and rng.random() < 0.6:
            p = rng.choice(groves)
            x, y = p.x + rng.randint(-30, 30), p.y + rng.randint(-8, 8)
            if 0 <= x < eco.w and eco.gy + 3 <= y < eco.h and eco.s.ground[y * eco.w + x]:
                return x, y
        return eco._random_ground()

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        pose = "run"
        if self.errands <= 0:
            if not self.leaving_set:
                self.leaving_set = True
                self.up_a_tree = rng.random() < 0.5
            if self.pause > 0:
                self.pause -= 1
            elif self.up_a_tree:
                from .climbers import ClimbingSquirrel

                self.wild.pending.append(ClimbingSquirrel(self.wild, self.ind))
                return False
            elif self.go_off(30):
                return False
            elif rng.random() < 0.03:
                self.pause = self.seconds(rng.uniform(0.4, 1.5))
        elif self.dig > 0:
            self.dig -= 1
            pose = "dig"
            if eco.frame_no & 1:
                i = (self.y) * eco.w + self.x + (7 if self.dir > 0 else -2) + rng.randint(-1, 1)
                if 0 <= i < eco.w * eco.h and not eco.c.fg[i]:
                    overlay[i] = LITTER[3]
            if self.dig == 0:
                self.wild.bury(self.x, self.y)
                self.errands -= 1
                self.spot = self._pick_spot()
        elif self.pause > 0:
            self.pause -= 1
        else:
            self.patience -= 1
            if self.patience <= 0:  # can't get there; try somewhere else, or give up
                self.errands -= 1
                self.spot = self._pick_spot()
                self.patience = self.seconds(30)
            if self.go(34, *self.spot):
                self.dig = self.seconds(rng.uniform(3, 6))
                self.patience = self.seconds(30)
            self.burst += 1
            if self.burst > self.seconds(rng.uniform(0.5, 1.4)):
                self.burst = 0
                self.pause = self.seconds(rng.uniform(0.3, 2))
        self.body(overlay, pose, self.x, self.y, self.dir < 0)
        return True


class Flyer(Creature):
    """Moves in a straight, bobbing line between points."""

    def fly_to(self, tx, ty, speed):
        dx, dy = tx - self.x, ty - self.y
        d = math.hypot(dx, dy)
        step = speed / max(1, self.eco.fps)
        if d <= step:
            self.x, self.y = tx, ty
            return True
        self.dir = sign(dx) or self.dir
        self.x += dx / d * step
        self.y += dy / d * step
        return False


class Songbird(Flyer):
    name = "songbird"

    def __init__(self, wild, ind):
        super().__init__(wild, ind)
        eco = self.eco
        self.dir = self.rng.choice((-1, 1))
        self.x = -6.0 if self.dir > 0 else eco.w + 6.0
        self.y = self.rng.uniform(eco.h * 0.1, eco.gy)
        self.visits = self.rng.randint(2, 4)
        self.plant = None
        self.perch = None
        self.sit = 0
        self.seed = None
        self._next()

    def _next(self):
        eco = self.eco
        options = [p for p in eco.plants if p.kind in ("shrub", "sapling", "spire", "fern") and p.shown > 8]
        if self.visits <= 0 or not options:
            self.plant = None
            self.perch = (-8.0 if self.x < eco.w / 2 else eco.w + 8.0, self.rng.uniform(0, eco.gy))
            return
        favoured = [p for p in options if p.kind == "shrub"]
        self.plant = self.rng.choice(favoured if favoured and self.rng.random() < 0.6 else options)
        p = self.plant
        top = min(p.pix[:p.shown], key=lambda d: d[1])
        self.perch = (float(p.x + top[0]), float(p.y + top[1] - 1))

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        flying = self.sit <= 0
        if self.sit > 0:
            self.sit -= 1
            if self.plant not in eco.plants:
                self.sit = 0
            if self.sit == 0:
                self._peck()
                self.visits -= 1
                self._next()
        elif self.fly_to(*self.perch, 42):
            if self.plant is None:
                return False
            self.sit = self.seconds(rng.uniform(6, 22))
            if self.seed:
                kind, g, gen = self.seed
                eco._sprout(kind, int(self.x) + rng.randint(-14, 14), self.plant.y + rng.randint(-5, 5),
                            eco._mutate_plant(g), gen + 1)
                self.seed = None
        if flying:
            pose = f"fly{eco.frame_no & 1}"
            y = int(self.y + math.sin(eco.frame_no * 0.9) * 1.5)
        else:
            pose = "sit"
            y = int(self.y) + (1 if eco.frame_no % 11 < 2 else 0)  # pecking
        self.body(overlay, pose, int(self.x) - 2, y, self.dir < 0, reflect=False)
        return True

    def _peck(self):
        """Eat the fruit or flower (always the last pixels a plant grew) and keep the seed."""
        eco = self.eco
        p = self.plant
        if p and p in eco.plants:
            eco._unshow(p, max(4, int(p.shown * 0.9)))
            eco._restamp(p)
            self.seed = (p.kind, p.g, p.gen)
            self.fed(0.2 * (1 - self.g["diet"]))
        prey = self.wild.nearest_prey(self.x, self.y, 28, frogs=False, snails=False)
        if prey and self.rng.random() < self.g["diet"]:
            self.wild.take(prey)
            self.fed(0.25)


class OwlHunt(Flyer):
    name = "owl"

    def __init__(self, wild, ind):
        super().__init__(wild, ind)
        eco = self.eco
        self.home = eco.s.owl
        self.x, self.y = float(self.home[0]), float(self.home[1])
        self.dir = 1
        self.prey = wild.most_visible_prey()
        self.state = "down"
        self.wild.owl_away = True
        self.wait = 0

    def frame(self, overlay):
        eco = self.eco
        if self.state == "down":
            if self.prey is None or not self.wild.prey_alive(self.prey):
                self.state = "up"
            elif self.fly_to(self.prey[1], self.prey[2] - 3, 46):
                self.wild.take(self.prey)
                self.fed(0.3)
                self.state = "up"
                self.wait = self.seconds(0.6)
        elif self.wait > 0:
            self.wait -= 1
        elif self.fly_to(self.home[0], self.home[1], 34):
            self.wild.owl_away = False
            eco.owl_on = True
            return False
        self.body(overlay, f"fly{(eco.frame_no // 2) & 1}", int(self.x) - 4, int(self.y), self.dir < 0, reflect=False)
        return True


class Bats(Creature):
    name = "bats"

    def __init__(self, wild, inds):
        super().__init__(wild, inds[0])
        eco = self.eco
        rng = self.rng
        self.life = self.seconds(rng.uniform(60, 170))
        self.flock = [{"x": rng.uniform(0, eco.w), "y": rng.uniform(eco.h * 0.08, eco.gy), "vx": rng.uniform(-40, 40),
                       "vy": rng.uniform(-14, 14), "ind": ind} for ind in inds]

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        self.life -= 1
        fps = max(1, eco.fps)
        alive = False
        for b in self.flock:
            if rng.random() < 0.18:  # bats turn on a whim
                b["vx"] += rng.uniform(-34, 34)
                b["vy"] += rng.uniform(-22, 22)
            for m in eco.moths:
                dx, dy = m["x"] - b["x"], m["y"] - b["y"]
                if abs(dx) < 34 and abs(dy) < 22:
                    b["vx"] += sign(dx) * 7
                    b["vy"] += sign(dy) * 5
                    if abs(dx) < 3 and abs(dy) < 3:
                        eco.moths.remove(m)
                        b["ind"]["e"] = min(3.0, b["ind"]["e"] + 0.3)
                        self.wild.note("moth taken by bats")
                    break
            speed = math.hypot(b["vx"], b["vy"])
            if speed > 52:
                b["vx"] *= 52 / speed
                b["vy"] *= 52 / speed
            b["x"] += b["vx"] / fps
            b["y"] += b["vy"] / fps
            low = eco.gy + eco.span * 0.3
            if b["y"] < eco.h * 0.05:
                b["vy"] = abs(b["vy"])
            elif b["y"] > low:
                b["vy"] = -abs(b["vy"])
            if self.life > 0:
                if b["x"] < 0:
                    b["vx"] = abs(b["vx"])
                elif b["x"] > eco.w:
                    b["vx"] = -abs(b["vx"])
            if -8 <= b["x"] <= eco.w + 8:
                alive = True
                self.g = b["ind"]["g"]
                self.body(overlay, f"fly{(eco.frame_no + int(b['y'])) & 1}", int(b["x"]) - 2, int(b["y"]), False, reflect=False)
        return self.life > 0 or alive


class Wisp(Creature):
    name = "wisp"

    def __init__(self, wild):
        super().__init__(wild)
        self.life = self.seconds(self.rng.uniform(70, 170))
        self.t = self.rng.uniform(0, 6.28)
        self.fade = 0

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        self.life -= 1
        self.t += 0.05 / max(1, eco.fps) * 8
        cx, cy, rx, ry = eco.s.pond
        x = int(cx + math.cos(self.t * 0.31) * rx * 1.1 + math.sin(self.t * 0.73) * 7)
        y = int(cy + math.sin(self.t * 0.47) * ry * 1.4 - 5 + math.sin(self.t * 1.3) * 2)
        w, h = eco.w, eco.h
        i = y * w + x
        if self.life > 0 and 3 < x < w - 3 and 3 < y < h - 3:
            ramp = MYC[0]
            flicker = rng.random() < 0.12
            overlay[i] = ramp[4] if not flicker else ramp[3]
            for off in (-1, 1, -w, w):
                overlay[i + off] = ramp[3]
            frame = eco.c.frame
            lit2 = palette.LIT2
            for dy in range(-3, 4):
                for dx in range(-4, 5):
                    k = i + dy * w + dx
                    if k not in overlay and dx * dx + dy * dy * 2 < 16 and not eco.c.fg[k]:
                        overlay[k] = lit2[frame[k]]
        # Moths come to it, fireflies answer it, and the threads beneath it wake up.
        for m in eco.moths:
            if abs(m["x"] - x) < 70 and abs(m["y"] - y) < 40:
                m["x"] += sign(x - m["x"]) * 0.35
                m["y"] += sign(y - m["y"]) * 0.2
        if eco.frame_no % self.seconds(2) == 0:
            for f in eco.flies:
                if abs(f["x"] - x) < 40 and abs(f["y"] - y) < 25:
                    f["phase"] = 0.0
            for dy in range(-12, 13, 3):
                for dx in range(-24, 25, 3):
                    node = eco.myc_node.get((y + dy) * w + x + dx)
                    col = eco.colony_by_id.get(node[0]) if node else None
                    if col:
                        col.energy += 0.05
        return self.life > 0


class ShootingStar(Creature):
    name = "star"

    def __init__(self, wild):
        super().__init__(wild)
        eco = self.eco
        rng = self.rng
        mx = eco.s.moon[0]
        self.x = mx + rng.uniform(-110, 110) * eco.scale_s
        self.y = rng.uniform(eco.h * 0.05, eco.h * 0.2)
        side = rng.choice((-1, 1))
        self.vx = side * rng.uniform(90, 150)
        self.vy = rng.uniform(40, 80)
        self.life = self.seconds(rng.uniform(0.8, 1.5))
        self.sky = set(range(palette.SKY.start, palette.SKY.start + palette.SKY.n)) | set(
            range(STAR.start, STAR.start + STAR.n))

    def frame(self, overlay):
        eco = self.eco
        self.life -= 1
        fps = max(1, eco.fps)
        self.x += self.vx / fps
        self.y += self.vy / fps
        n = int(math.hypot(self.vx, self.vy) / fps) + 4
        ux, uy = self.vx / fps / n, self.vy / fps / n
        w, h = eco.w, eco.gy
        for k in range(n):
            x, y = int(self.x - ux * k), int(self.y - uy * k)
            if 0 <= x < w and 0 <= y < h:
                i = y * w + x
                if eco.c.comp[i] in self.sky:  # seen only through gaps in the leaves
                    overlay[i] = STAG[3] if k < 2 else STAG[1] if k < n // 2 else STAG[0]
        return self.life > 0


class Wind(Creature):
    name = "wind"

    def __init__(self, wild):
        super().__init__(wild)
        rng = self.rng
        self.life = self.seconds(rng.uniform(14, 26))
        self.dir = rng.choice((-1, 1))
        self.bits = []
        self.total = self.life
        self.sown = False

    def _bit(self):
        eco, rng = self.eco, self.rng
        return {"x": -2.0 if self.dir > 0 else eco.w + 2.0, "y": rng.uniform(eco.gy - eco.span * 0.45, eco.h - 14),
                "v": rng.uniform(24, 46), "ph": rng.uniform(0, 6.28),
                "c": rng.choice((LITTER[3], LITTER[4], LITTER[2], SPORE[1], FLOWER[3][1]))}

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        self.life -= 1
        fps = max(1, eco.fps)
        strength = min(1.0, min(self.life, self.total - self.life) / max(1, self.seconds(3)))
        if self.life > 0 and len(self.bits) < 26 * strength and rng.random() < 0.5:
            self.bits.append(self._bit())
        w, h = eco.w, eco.h
        fg = eco.c.fg
        for b in list(self.bits):
            b["x"] += self.dir * b["v"] / fps
            b["ph"] += 0.5
            y = int(b["y"] + math.sin(b["ph"]) * 2.2)
            x = int(b["x"])
            if not 0 <= x < w:
                self.bits.remove(b)
                continue
            i = y * w + x
            if 0 <= i < w * h and not fg[i]:
                overlay[i] = b["c"]
        # Everything light gets carried along.
        for m in eco.moths:
            m["x"] = (m["x"] + self.dir * 0.5 * strength) % eco.w
        for s in eco.spores:
            s["vx"] += self.dir * 0.01
        if not self.sown and self.life < self.total // 2:
            self.sown = True
            self._sow()
        return self.life > 0 or bool(self.bits)

    def _sow(self):
        eco, rng = self.eco, self.rng
        mature = [p for p in eco.plants if p.kind in ("clover", "fern", "spire", "shrub") and p.shown == len(p.pix)]
        for _ in range(3):
            if mature:
                p = rng.choice(mature)
                eco._sprout(p.kind, p.x + self.dir * rng.randint(60, 170), p.y + rng.randint(-14, 14),
                            eco._mutate_plant(p.g, p.pollen), p.gen + 1)
        for _ in range(6):
            x, y = eco._random_ground()
            eco._add_litter(x, y)


VISITORS = {"rabbit": Rabbit, "fox": Fox, "hedgehog": Hedgehog, "squirrel": Squirrel, "songbird": Songbird,
            "owl": OwlHunt, "bats": Bats, "wisp": Wisp, "star": ShootingStar, "wind": Wind}
# Happenings that aren't animals: name -> (weight, lowest daylight, highest daylight).
# Animals aren't listed: who appears, and when, follows from who is alive and when their genes keep them awake.
HAPPENINGS = {
    "wisp": (2, 0.0, 0.1),
    "star": (4, 0.0, 0.15),
    "wind": (2, 0.0, 1.0),
    "ring": (1, 0.0, 1.0),
}
ANIMALS = ("rabbit", "fox", "hedgehog", "owl", "bats", "songbird", "squirrel")
ALL_NAMES = sorted(HAPPENINGS) + sorted(ANIMALS) + ["stag", "snow", "rain", "storm", "fog", "aurora", "rainbow", "watcher", "mossback",
                                                    "mothman", "pond_thing", "under_ice", "black_dog", "kodama", "saucer", "werewolf",
                                                    "ghost_stag", "witch", "sleigh", "black_cat", "geese", "dragonfly", "ducks", "ermine",
                                                    "climbing_squirrel", "pine_marten", "woodpecker", "treecreeper"]


class Wildlife:
    def __init__(self, eco):
        self.eco = eco
        self.rng = eco.rng
        self.visitors = []
        self.pending = []  # those arriving as a result of something a visitor just did
        self.frogs = []
        self.snails = []
        self.ripples = []
        self.caches = []  # buried nuts: [x, y, due tick, sprouts?]
        self.owl_away = False
        self.next_event = self.rng.randint(120, 500)
        self.evo = Evolution(eco)
        from .cryptids import Oddities

        self.odd = Oddities(self)

    # ---- things creatures do to the world ----------------------------------

    def enrich(self, x, y, amount):
        eco = self.eco
        if 0 <= x < eco.w and eco.gy <= y < eco.h:
            cell = eco._cell(x, y)
            eco.nut[cell] = min(1.0, eco.nut[cell] + amount)

    def note(self, what):
        seen = self.eco.stats.setdefault("sightings", {})
        seen[what] = seen.get(what, 0) + 1

    def find(self, name):
        for v in self.visitors:
            if v.name == name:
                return v
        return None

    def ripple(self, x, y):
        if len(self.ripples) < 8 and 0 <= y < self.eco.h and 0 <= x < self.eco.w:
            self.ripples.append([x, y, 0.0])

    def in_pond(self, x, y):
        """Is this at the water, allowing a little margin? Reads the real shore, bays and all."""
        eco = self.eco
        water = eco.s.water
        w, h = eco.w, eco.h
        x, y = int(x), int(y)
        for dx, dy in ((0, 0), (-5, 0), (5, 0), (0, 2), (0, -2)):
            px, py = x + dx, y + dy
            if 0 <= px < w and 0 <= py < h and water[py * w + px]:
                return True
        return False

    def steer(self, x, y, wanted_y, heading):
        """The next row for someone walking at (x, y): their wish, unless water lies ahead."""
        eco = self.eco
        ahead = x + heading * 26
        if self.in_pond(ahead, wanted_y) or self.in_pond(x, wanted_y):
            cy = eco.s.pond[1]
            wanted_y = y + (-1 if y <= cy else 1)
        return max(eco.gy + 2, min(eco.h - 2, wanted_y))

    def nearest_prey(self, x, y, reach, frogs=True, snails=True):
        """The closest small creature, as ('beetle'|'snail'|'frog', x, y, object)."""
        best = None
        best_d = reach
        frogs = frogs and not self.evo.asleep("frog")
        snails = snails and not self.evo.asleep("snail")
        for b in () if self.evo.asleep("beetle") else self.eco.beetles:
            d = abs(b["x"] - x) + abs(b["y"] - y)
            if d < best_d:
                best, best_d = ("beetle", b["x"], b["y"], b), d
        if snails:
            for s in self.snails:
                d = abs(s["x"] - x) + abs(s["y"] - y)
                if d < best_d:
                    best, best_d = ("snail", int(s["x"]), int(s["y"]), s), d
        if frogs:
            for f in self.frogs:
                d = abs(f["x"] - x) + abs(f["y"] - y)
                if d < best_d and not f["swim"]:
                    best, best_d = ("frog", int(f["x"]), int(f["y"]), f), d
        return best

    def most_visible_prey(self):
        """Owls take what shows up best against the ground, so camouflage genuinely pays."""
        best = None
        best_score = 0.0
        env = self.evo.env()
        for kind in PREY["owl"][:3]:
            if self.evo.asleep(kind):
                continue
            for member in self.evo.group(kind):
                if member.get("swim") or "g" not in member:
                    continue
                score = self.evo.detect(member["g"], env) * self.rng.random()
                if score > best_score:
                    best, best_score = (kind, int(member["x"]), int(member["y"]), member), score
        return best

    def prey_alive(self, prey):
        kind, _, _, obj = prey
        return obj in {"beetle": self.eco.beetles, "snail": self.snails, "frog": self.frogs}[kind]

    def take(self, prey):
        """A predator feeds. The prey's substance returns to the soil around it."""
        if not self.prey_alive(prey):
            return
        kind, x, y, obj = prey
        {"beetle": self.eco.beetles, "snail": self.snails, "frog": self.frogs}[kind].remove(obj)
        self.enrich(x, y, 0.04)
        self.note(f"{kind} eaten")

    def bury(self, x, y):
        """A squirrel's nut. Squirrels forget about a third of them, which is how oaks travel."""
        rng = self.rng
        eco = self.eco
        for dy in range(-1, 2):
            for dx in range(-2, 3):
                eco._moss_set(x // 2 + dx, (y - eco.gy) // 2 + dy, 0)
        self.caches.append([x, y, eco.tick_no + rng.randint(1500, 7000), rng.random() < 0.35])
        self.enrich(x, y, 0.03)

    def fairy_ring(self):
        """A well-fed colony fruits all at once, in a circle around where it began."""
        eco, rng = self.eco, self.rng
        colonies = [c for c in eco.colonies if len(c.nodes) - c.head > 150]
        if not colonies:
            return False
        col = max(colonies, key=lambda c: c.energy + rng.random())
        for _ in range(12):
            p = col.nodes[rng.randrange(col.head, len(col.nodes))]
            cx, cy = p % eco.w, p // eco.w
            r = rng.uniform(16, 30) * (0.6 + (cy - eco.gy) / eco.span * 0.8)
            if eco.gy + r * 0.5 + 4 < cy < eco.h - r * 0.5 - 4 and r < cx < eco.w - r and not self.in_pond(cx, cy):
                break
        else:
            return False
        g = col.g
        n = rng.randint(10, 15)
        offset = rng.uniform(0, 6.28)
        for k in range(n):
            a = offset + k / n * math.tau + rng.uniform(-0.1, 0.1)
            x, y = int(cx + math.cos(a) * r), int(cy + math.sin(a) * r * 0.45)
            genes = {"hue": g["cap"], "shape": g["shape"], "cluster": 0.0, "size": min(g["size"], 0.6),
                     "slender": g["slender"], "tone": 0, "bushy": 0, "bloom": 0, "wet": 0.5, "dark": 0.5}
            plant = eco._sprout("mushroom", x, y, genes, col.gen, force=True, ignore_limit=True)
            if plant:
                plant.colony = col.cid
        col.energy *= 0.6
        return True

    # ---- scheduling --------------------------------------------------------

    def summon(self, name, force=True):
        """Bring something on. Forced, whoever is around comes whatever the hour; if none are, strangers do."""
        if name == "stag":
            self.eco.summon_stag(force)
            return True
        if name == "ring":
            ok = self.fairy_ring()
            if ok:
                self.note("ring")
            return ok
        from .cryptids import ODD_VISITORS

        if name in ODD_VISITORS:
            return self.odd.summon(name, force)
        climate = self.eco.climate
        if name == "snow":
            climate.snowing = max(climate.snowing, 1500)
            return True
        if name == "rain":
            self.eco.raining = max(self.eco.raining, 600)
            return True
        if name == "storm":
            climate.start_storm()
            return True
        if name == "fog":
            climate.fog = 3000
            return True
        if name == "aurora":
            climate.aurora = 2500
            self.odd.sighted("aurora")
            return True
        if name == "rainbow":
            climate.rainbow = 900
            self.odd.sighted("rainbow")
            return True
        if name == "frog":
            self._add_frog()
            return True
        if name == "snail":
            self._add_snail()
            return True
        if name == "rabbit":
            # The bigger the warren, the bigger the party.
            count = self.rng.randint(1, 2 + len(self.evo.pools["rabbit"]) // 6)
            inds = self.evo.pick("rabbit", min(count, 6), force)
            if not inds:
                return False
            y = self.eco.gy + int(self.eco.span * self.rng.uniform(0.2, 0.75))
            for k, ind in enumerate(inds):
                rabbit = Rabbit(self, ind, y + k * 4, delay=k * self.eco.fps * 3)
                self.visitors.append(rabbit)
                self.odd.on_rabbit_seen(rabbit)
            self.note("rabbit")
            return True
        cls = VISITORS.get(name)
        if not cls or self.find(name):
            return False
        if name in HAPPENINGS:
            self.visitors.append(cls(self))
            self.note(name)
            return True
        if name == "owl" and self.most_visible_prey() is None:
            return False
        inds = self.evo.pick(name, 5 if name == "bats" else 1, force)
        if not inds:
            return False
        self.visitors.append(cls(self, inds if name == "bats" else inds[0]))
        self.note(name)
        return True

    def _pick_event(self):
        """Common animals are seen often and rare ones rarely, whichever those turn out to be."""
        daylight = self.eco.daylight
        options = []
        for name, (weight, low, high) in HAPPENINGS.items():
            if low <= daylight <= high and not self.find(name):
                options += [name] * weight
        for name in ANIMALS:
            awake = self.evo.awake_count(name)
            if awake and not self.find(name):
                options += [name] * (1 + int(awake ** 0.8))
        return self.rng.choice(options) if options else None

    def tick(self):
        eco, rng = self.eco, self.rng
        if not eco.cfg.fauna:
            return
        self.next_event -= 1
        if self.next_event <= 0:
            self.next_event = rng.randint(300, 1100)
            if len(self.visitors) < 3:
                name = self._pick_event()
                if name:
                    self.summon(name, force=False)
        for cache in list(self.caches):
            if eco.tick_no >= cache[2]:
                self.caches.remove(cache)
                if cache[3]:
                    trees = [p for p in eco.plants if p.kind == "sapling"]
                    genes = eco._mutate_plant(trees[0].g) if trees else eco._random_genes("sapling")
                    if eco._sprout("sapling", cache[0], cache[1], genes, trees[0].gen + 1 if trees else 0, force=True):
                        self.note("forgotten nut sprouted")
        # Rabbits out in the open don't go unnoticed for long.
        if rng.random() < 0.002 and not self.find("fox") and self.find("rabbit"):
            self.summon("fox", force=False)
        self._frog_tick()
        self._snail_tick()
        self.odd.tick()
        self.evo.tick()

    # ---- frogs -------------------------------------------------------------

    def _bank(self, a):
        """The shore in a direction from the pond's middle, found by walking out until the water ends."""
        eco = self.eco
        cx, cy, rx, ry = eco.s.pond
        water = eco.s.water
        w, h = eco.w, eco.h
        last = (cx, cy)
        for k in range(1, 60):
            t = k / 30.0
            x, y = int(cx + math.cos(a) * rx * t), int(cy + math.sin(a) * ry * t)
            if not (0 <= x < w and 0 <= y < h):
                break
            if water[y * w + x]:
                last = (x, y)
        return last[0] + math.cos(a) * 3, last[1] + math.sin(a) * 2

    def _add_frog(self, g=None):
        a = self.rng.uniform(0, 6.28)
        x, y = self._bank(a)
        self.frogs.append({"x": x, "y": y, "a": a, "e": 1.0, "wait": self.rng.randint(10, 200), "hop": None,
                           "swim": 0, "tongue": None, "g": g or self.evo.resident_genes("frog")})

    def _frog_tick(self):
        eco, rng = self.eco, self.rng
        if self.evo.asleep("frog"):
            return  # down in the mud under the ice
        for f in list(self.frogs):
            f["e"] -= 0.00012
            if f["e"] <= 0:
                self.frogs.remove(f)
                self.enrich(int(f["x"]), int(f["y"]), 0.05)
            elif eco.raining and f["e"] > 1.3 and len(self.frogs) < 8 and rng.random() < 0.002:
                f["e"] -= 0.7
                self._add_frog(self.evo.child_of(f["g"], rng.choice(self.frogs)["g"]))
                self.note("frog born")
            elif f["e"] > 1.5 and len(self.frogs) < 8 and rng.random() < 0.0004:  # they don't only breed in the rain
                f["e"] -= 0.7
                self._add_frog(self.evo.child_of(f["g"], rng.choice(self.frogs)["g"]))
        if len(self.frogs) < 2 and rng.random() < 0.0015:
            self._add_frog()

    def _frog_frame(self, overlay):
        eco, rng = self.eco, self.rng
        if self.evo.asleep("frog"):
            return
        evo = self.evo
        w = eco.w
        for f in self.frogs:
            if f["swim"] > 0:
                f["swim"] -= 1
                # Just the eyes, drifting across the surface.
                f["sx"] += rng.uniform(-0.18, 0.3) * f["sd"]
                x, y = int(f["sx"]), int(f["sy"])
                i = y * w + x
                if eco.s.water[i]:
                    overlay[i] = FROG[3]
                    overlay[i + 2] = FROG[3]
                if f["swim"] == 0:
                    self.ripple(x, y)
                    f["a"] = rng.uniform(0, 6.28)
                    f["x"], f["y"] = self._bank(f["a"])
                continue
            hop = f["hop"]
            lift = 0
            if hop:
                hop[4] += 1
                t = hop[4] / hop[5]
                f["x"] = hop[0] + (hop[2] - hop[0]) * t
                f["y"] = hop[1] + (hop[3] - hop[1]) * t
                lift = int(math.sin(t * math.pi) * 4)
                if t >= 1:
                    f["hop"] = None
                    if hop[6]:
                        self.ripple(int(f["x"]), int(f["y"]))
                        f["swim"] = eco.fps * rng.randint(8, 30)
                        f["sx"], f["sy"], f["sd"] = f["x"], f["y"], rng.choice((-1, 1))
                        continue
            else:
                f["wait"] -= 1
                if f["wait"] <= 0:
                    active = evo.awake(f["g"]) or eco.raining
                    f["wait"] = eco.fps * rng.randint(4, 40) * (1 if active else 4)
                    self._frog_jump(f)
                elif eco.frame_no % 8 == 0 and (evo.awake(f["g"]) or eco.raining):
                    self._frog_hunt(f)
            x, y = int(f["x"]), int(f["y"])
            tongue = f["tongue"]
            if tongue:
                tongue[2] -= 1
                tx, ty = tongue[0], tongue[1]
                n = max(abs(tx - x), abs(ty - (y - 2)), 1)
                for k in range(1, n + 1):
                    j = int(y - 2 + (ty - y + 2) * k / n) * w + int(x + (tx - x) * k / n)
                    if 0 <= j < w * eco.h:
                        overlay[j] = FLOWER[3][1]
                if tongue[2] <= 0:
                    f["tongue"] = None
            placed = stamp(eco, overlay, animal_rows("frog", "hop" if lift else "sit", f["g"]), x - 2, y - lift, False,
                           coat_colors(f["g"]), coat_dark(f["g"]))
            if f["g"]["glow"] > 0.6:
                halo(eco, overlay, placed, palette.LIT2)

    def _frog_jump(self, f):
        rng = self.rng
        cx, cy, rx, ry = self.eco.s.pond
        into = rng.random() < 0.3
        if into:
            a = rng.uniform(0, 6.28)
            tx, ty = cx + math.cos(a) * rx * 0.55, cy + math.sin(a) * ry * 0.55
        else:
            f["a"] += rng.uniform(-0.5, 0.5)
            tx, ty = self._bank(f["a"])
        f["hop"] = [f["x"], f["y"], tx, ty, 0, max(3, self.eco.fps // 2 if not into else self.eco.fps), into]

    def _frog_hunt(self, f):
        """A flick of the tongue at anything small and close enough."""
        eco = self.eco
        reach = 8 + 8 * f["g"]["speed"]
        for group, kind in ((eco.moths, "moth"), (eco.flies, "firefly")):
            for m in group:
                if abs(m["x"] - f["x"]) < reach and abs(m["y"] - f["y"]) < reach * 0.6:
                    if kind == "firefly" and self.rng.random() > m["bright"]:
                        continue  # the dimmer ones go unnoticed
                    f["tongue"] = [int(m["x"]), int(m["y"]), 2]
                    f["e"] = min(2.0, f["e"] + 0.5)
                    self.note(f"{kind} eaten by frog")
                    if kind == "moth":
                        group.remove(m)
                    else:  # the survivors' young take its place
                        eco._fly_offspring(m)
                    return

    # ---- snails ------------------------------------------------------------

    def _add_snail(self):
        x, y = self.eco._random_ground()
        if len(self.snails) >= 2:
            a, b = self.rng.sample(self.snails, 2)
            g = self.evo.child_of(a["g"], b["g"])
        else:
            g = self.evo.resident_genes("snail")
        self.snails.append({"x": float(x), "y": float(y), "d": self.rng.uniform(0, 6.28), "left": self.rng.randint(1500, 4500),
                            "trail": deque(maxlen=40), "target": None, "chew": 0, "g": g})
        self.note("snail")

    def _snail_tick(self):
        eco, rng = self.eco, self.rng
        if self.evo.asleep("snail"):
            return
        if len(self.snails) < 5 and rng.random() < (0.004 if eco.raining else 0.0002):
            self._add_snail()
        for s in list(self.snails):
            s["left"] -= 1
            if s["left"] <= 0:
                self.snails.remove(s)
                self.enrich(int(s["x"]), int(s["y"]), 0.02)
                continue
            if s["chew"] > 0:
                s["chew"] -= 1
                if s["chew"] == 0 and s["target"] in eco.plants:
                    p = s["target"]
                    eco._unshow(p, max(0, p.shown - 6))
                    eco._restamp(p)
                    if p.shown <= 4:
                        p.wilting = True
                        s["target"] = None
                    else:
                        s["chew"] = 24
                continue
            if rng.random() > 0.5:
                continue
            t = s["target"]
            if t not in eco.plants:
                t = s["target"] = None
                if rng.random() < 0.03:
                    shrooms = [p for p in eco.plants if p.kind == "mushroom" and abs(p.x - s["x"]) < 60 and abs(p.y - s["y"]) < 26]
                    if shrooms:
                        t = s["target"] = rng.choice(shrooms)
            if t:
                s["d"] = math.atan2((t.y - s["y"]) * 2, t.x - s["x"])
                if abs(t.x - s["x"]) < 3 and abs(t.y - s["y"]) < 2:
                    s["chew"] = 24
                    continue
            else:
                s["d"] += rng.gauss(0, 0.25)
            nx = s["x"] + math.cos(s["d"]) * 0.7
            ny = s["y"] + math.sin(s["d"]) * 0.35
            if 0 <= int(nx) < eco.w and eco.gy + 2 <= int(ny) < eco.h - 1 and eco.s.ground[int(ny) * eco.w + int(nx)]:
                if int(nx) != int(s["x"]) or int(ny) != int(s["y"]):
                    s["trail"].append(int(s["y"]) * eco.w + int(s["x"]))
                s["x"], s["y"] = nx, ny
            else:
                s["d"] += math.pi

    def _snail_frame(self, overlay):
        eco = self.eco
        if self.evo.asleep("snail"):
            return
        fg, life = eco.c.fg, eco.c.life
        for s in self.snails:
            for i in s["trail"]:  # the silver thread it leaves behind
                if not fg[i] and not life[i]:
                    overlay[i] = SPORE[0]
            stamp(eco, overlay, animal_rows("snail", "crawl", s["g"]), int(s["x"]) - 2, int(s["y"]), math.cos(s["d"]) < 0,
                  coat_colors(s["g"]))

    # ---- drawing -----------------------------------------------------------

    def _ripple_frame(self, overlay):
        eco = self.eco
        if eco.climate.frozen:
            self.ripples.clear()
            return
        if eco.raining and eco.frame_no % 5 == 0:
            cx, cy, rx, ry = eco.s.pond
            a, d = self.rng.uniform(0, 6.28), self.rng.random() ** 0.5
            self.ripple(int(cx + math.cos(a) * rx * d), int(cy + math.sin(a) * ry * d))
        water = eco.s.water
        frame = eco.c.frame
        lit = palette.LIT2
        w, h = eco.w, eco.h
        for r in list(self.ripples):
            r[2] += 3.0 / max(1, eco.fps)
            rad = r[2]
            if rad > 7:
                self.ripples.remove(r)
                continue
            for k in range(int(6 + rad * 3)):
                a = k * math.tau / int(6 + rad * 3)
                x, y = int(r[0] + math.cos(a) * rad), int(r[1] + math.sin(a) * rad * 0.42)
                if 0 <= x < w and 0 <= y < h:
                    i = y * w + x
                    if water[i]:
                        overlay[i] = lit[frame[i]]

    def frame(self, overlay):
        self._ripple_frame(overlay)
        self._snail_frame(overlay)
        self._frog_frame(overlay)
        if self.visitors:
            self.visitors = [v for v in self.visitors if v.frame(overlay)]
        if self.pending:
            self.visitors.extend(self.pending)
            self.pending = []

    # ---- persistence -------------------------------------------------------

    def to_state(self):
        return {
            "frogs": [[round(f["x"], 1), round(f["y"], 1), round(f["a"], 2), round(f["e"], 3), f["g"]] for f in self.frogs],
            "snails": [[round(s["x"], 1), round(s["y"], 1), s["left"], s["g"]] for s in self.snails],
            "moths": [[round(m["x"], 1), round(m["y"], 1), m["g"], round(m["e"], 3)] for m in self.eco.moths],
            "caches": self.caches,
            "next": self.next_event,
            "evo": self.evo.to_state(),
        }

    def load(self, st):
        if not st:  # a world from before there were frogs: some arrive now
            self.evo.load(None)
            for _ in range(3):
                self._add_frog()
            return
        self.next_event = st["next"]
        self.caches = st["caches"]
        self.evo.load(st.get("evo"))
        for x, y, a, e, *rest in st["frogs"]:
            self.frogs.append({"x": x, "y": y, "a": a, "e": e, "wait": self.rng.randint(10, 200), "hop": None,
                               "swim": 0, "tongue": None, "g": rest[0] if rest else self.evo.resident_genes("frog")})
        for x, y, left, *rest in st["snails"]:
            self.snails.append({"x": x, "y": y, "d": self.rng.uniform(0, 6.28), "left": left, "trail": deque(maxlen=40),
                                "target": None, "chew": 0, "g": rest[0] if rest else self.evo.resident_genes("snail")})
        for x, y, g, e in st.get("moths", ()):
            self.eco.moths.append(self.eco._new_moth(x, y, g, e))
