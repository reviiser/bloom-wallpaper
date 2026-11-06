"""The living part.

Four loosely coupled systems share one soil:

  moss      Life-like cellular automaton updated a few cells at a time. Every
            strain carries its own birth/survival rule, inherited and mutated,
            so the rules themselves evolve.
  mycelium  Hyphal colonies forage through the soil for dead matter, feed plants
            they touch, fight or fuse with neighbours, and fruit after rain.
  plants    Guilds in the permaculture sense: clover fixes nitrogen, ferns and
            shrubs shade the ground, trees need fungal partners and leave nurse
            logs. Seeds inherit mutated genes; moths carry pollen between plants.
  fauna     Fireflies whose flashes slowly synchronise, moths, beetles, an owl,
            and now and then a white stag that spreads seeds as it passes.

Nutrients move around in a loop: nothing is created except a little leaf fall.
All per-tick work is sampled, so cost stays flat however crowded it gets.
"""

import base64
import math
import random
import zlib

from . import palette
from .climbers import HangingMoss
from .clouds import Clouds
from .evolution import COATS, coat_colors
from .flare import LensFlare
from .seasons import Climate
from .sky import Sky
from .palette import EYE, FIREFLY, FLOWER, LITTER, MOSS, MYC, SKY, SPORE, STAR
from .sprites import BUILDERS, animal_rows, bevel
from .wildlife import Wildlife, halo, stamp

FLY_RAMPS = (FIREFLY, MYC[2], MYC[4], MYC[0], MYC[3])
CELL_W = 6
CELL_H = 4
MOSS_CELL = 2

NBR8 = ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1))
DIRS = ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1))


def rule_mask(digits):
    mask = 0
    for d in digits:
        mask |= 1 << int(d)
    return mask


def rule_text(b, s):
    return "B" + "".join(str(i) for i in range(9) if b >> i & 1) + "/S" + "".join(str(i) for i in range(9) if s >> i & 1)


STARTING_RULES = [("34", "1234567"), ("34", "234567"), ("345", "2345678"), ("35", "123456"), ("346", "1234567")]
R_PENTOMINO = ((1, 0), (2, 0), (0, 1), (1, 1), (1, 2))
ACORN = ((1, 0), (3, 1), (0, 2), (1, 2), (4, 2), (5, 2), (6, 2))

# Who likes growing beside whom, permaculture-guild style.
COMPANIONS = {
    "clover": {"fern": 0.1, "spire": 0.15, "shrub": 0.1, "sapling": 0.15, "clover": -0.15},
    "fern": {"sapling": 0.25, "log": 0.3, "clover": 0.15, "fern": -0.1},
    "spire": {"clover": 0.25, "shrub": 0.1, "spire": 0.05, "sapling": -0.1},
    "shrub": {"clover": 0.2, "sapling": 0.1, "fern": 0.05, "shrub": -0.2},
    "sapling": {"clover": 0.25, "shrub": 0.1, "fern": 0.05, "sapling": -0.5, "log": 0.2},
}
LIMITS = {"clover": 34, "fern": 26, "spire": 30, "shrub": 10, "sapling": 5, "mushroom": 22, "log": 3,
          "crocus": 14, "foxglove": 9, "goldenrod": 12, "snowdrop": 12, "pumpkin": 6}
# Season-bound plants: they come up when theirs arrives and die back when it leaves.
SEASONAL_PLANTS = {"crocus": "spring", "foxglove": "summer", "goldenrod": "autumn", "pumpkin": "autumn", "snowdrop": "winter"}
# Age in ticks at which each kind begins to wither, before genetic variation.
LIFESPAN = {"clover": 3600, "fern": 9000, "spire": 3200, "shrub": 16000, "sapling": 60000,
            "mushroom": 900, "log": 30000, "crocus": 90000, "foxglove": 90000, "goldenrod": 90000, "snowdrop": 90000, "pumpkin": 90000}
HUNGER = {"clover": 0.12, "fern": 0.35, "spire": 0.4, "shrub": 0.5, "sapling": 0.6, "mushroom": 0.0, "log": 0.0,
          "crocus": 0.0, "foxglove": 0.1, "goldenrod": 0.05, "snowdrop": 0.0, "pumpkin": 0.1}


def clamp(v, low=0.0, high=1.0):
    return low if v < low else high if v > high else v


class Plant:
    __slots__ = ("pid", "kind", "x", "y", "z", "g", "seed", "pix", "shown", "age", "life", "wilting", "colony",
                 "pollen", "linked", "shed", "gen", "shadow")


class Colony:
    __slots__ = ("cid", "g", "energy", "tips", "nodes", "head", "lv1", "lv0", "gen", "alive", "born")


class Eco:
    def __init__(self, canvas, scene, config, seed):
        self.c = canvas
        self.s = scene
        self.cfg = config
        self.rng = random.Random(seed ^ 0x51ED)
        w, h, gy = scene.w, scene.h, scene.gy
        self.w, self.h, self.gy = w, h, gy
        self.scale_s = h / 270.0
        self.span = h - gy
        self.tick_no = 0
        self.frame_no = 0
        self.next_pid = 1
        self.next_cid = 1
        self.stats = {"births": 0, "deaths": 0, "fruitings": 0, "max_plant_gen": 0, "max_fungal_gen": 0,
                      "stag_visits": 0, "rains": 0, "pollinations": 0, "rule_mutations": 0, "colonies_founded": 0,
                      "sightings": {}}

        # Soil, coarse grid.
        self.cols = w // CELL_W + 1
        self.rows = self.span // CELL_H + 1
        n = self.cols * self.rows
        self.nut = [self.rng.uniform(0.25, 0.5) for _ in range(n)]
        self.wet = [0.3] * n
        self.shade = [0.0] * n
        self.wet_base = [0.3] * n
        self._init_moisture()

        # Moss, on its own 2x2 grid.
        self.mc = w // MOSS_CELL
        self.mr = self.span // MOSS_CELL
        self.cells = bytearray(self.mc * self.mr)
        self.strains = [None]
        self.moss_active = []
        self.moss_flag = bytearray(self.mc * self.mr)
        self.moss_count = 0
        self.moss_hist = {}

        self.plants = []
        self.colonies = []
        self.myc_node = {}  # pixel -> (colony id, parent pixel)
        self.colony_by_id = {}
        self.pulses = []
        self.spores = []
        self.falling = []

        self.lichen = self._init_lichen()

        self.flies = []
        self.moths = []
        self.beetles = []
        self.stag = None
        self.next_stag = self.rng.randint(6000, 14000)
        self.owl_blink = 0
        self.owl_on = self.rng.random() < 0.6
        self.twinkle = {}

        self.raining = 0
        self.next_rain = self.rng.randint(2500, 9000)
        self.drops = []
        self.mist = 0.0

        self.water_phase = 0
        self.daylight = 0.0
        self.sim_accum = 0.0
        self.covered = False

        self.shafts = [
            {"x": scene.shaft_x + self.rng.uniform(-30, 30) * self.scale_s, "w": self.rng.uniform(12, 26) * self.scale_s,
             "slope": self.rng.uniform(0.32, 0.6) * self.rng.choice((-1, 1)),
             "phase": self.rng.uniform(0, 6.28), "rate": self.rng.uniform(0.0004, 0.0009)}
            for _ in range(3)
        ]
        for shaft in self.shafts[1:]:
            shaft["slope"] = self.shafts[0]["slope"]
            shaft["x"] += self.rng.uniform(-110, 110) * self.scale_s
        self.last_spans = None
        self.s_sky = (SKY.start, SKY.start + SKY.n)
        self.climate = Climate(self)
        self.sky = Sky(self)
        self.clouds = Clouds(self)
        self.flare = LensFlare(self)
        self.wild = Wildlife(self)
        self.hanging = HangingMoss(self)

    # ---- setup -------------------------------------------------------------

    def _init_moisture(self):
        cx, cy, rx, ry = self.s.pond
        for row in range(self.rows):
            for col in range(self.cols):
                x = col * CELL_W
                y = self.gy + row * CELL_H
                d = math.hypot((x - cx) / (rx * 2.6), (y - cy) / (ry * 3.2))
                v = clamp(0.75 - d * 0.42 + self.rng.uniform(-0.05, 0.05), 0.12, 0.85)
                self.wet_base[row * self.cols + col] = v
                self.wet[row * self.cols + col] = v

    def _init_lichen(self):
        patches = []
        for x0, y0, x1, y1 in self.s.lichen:
            if x1 - x0 < 5 or y1 - y0 < 4:
                continue
            w = x1 - x0
            h = y1 - y0
            patches.append({"x": x0, "y": y0, "w": w, "h": h, "cells": bytearray(w * h), "under": {}, "idle": 0,
                            "tone": self.rng.choice((2, 2, 5))})
        return patches

    def genesis(self):
        """A new world: scatter pioneers, then let it run a little before anyone looks."""
        rng = self.rng
        for rule in STARTING_RULES:
            self._new_strain(rule_mask(rule[0]), rule_mask(rule[1]), rng.randrange(len(MOSS)))
        for _ in range(16):
            self._moss_seed(rng.randrange(self.mc), rng.randrange(self.mr), rng.randrange(1, len(self.strains)), 7)
        for _ in range(4):
            self._found_colony(*self._random_ground(), self._random_fungal_genes(), 0)
        for kind, count in (("clover", 20), ("fern", 11), ("spire", 22), ("shrub", 3), ("sapling", 1)):
            for _ in range(count):
                x, y = self._random_ground()
                p = self._sprout(kind, x, y, self._random_genes(kind), 0, force=True)
                if p:
                    p.age = rng.randint(0, LIFESPAN[kind] // 2)
                    self._reveal(p, int(len(p.pix) * rng.uniform(0.5, 1.0)))
        for _ in range(int(self.cfg.fireflies)):
            self.flies.append(self._new_fly())
        self._sync_fauna()
        for _ in range(700):
            self._moss_tick(160)
            self.tick()
        self.wild.visitors = []  # nobody has been seen arriving yet
        self.hanging.prewarm()
        self.climate.seed_carpet()
        for _ in range(3):
            self.wild._add_frog()

    def _sync_fauna(self):
        rng = self.rng
        wanted = int(self.cfg.fireflies)
        while len(self.flies) < wanted:
            self.flies.append(self._new_fly())
        del self.flies[wanted:]
        if self.cfg.fauna:
            if not self.moths:
                for k in range(6):
                    # Half start out night-flying, half day-flying. Their descendants may settle it.
                    genes = self.wild.evo.resident_genes("moth")
                    if k % 2:
                        genes["active"], genes["coat"] = rng.uniform(0.7, 0.95), rng.random()
                    self.moths.append(self._new_moth(g=genes))
            if not self.beetles:
                for _ in range(6):
                    self.beetles.append(self._new_beetle(*self._random_ground()))
        else:
            self.moths = []
            self.beetles = []
            self.stag = None

    def _random_ground(self):
        rng = self.rng
        g = self.s.ground
        for _ in range(40):
            x = rng.randrange(self.w)
            y = rng.randrange(self.gy + 3, self.h - 2)
            if g[y * self.w + x]:
                return x, y
        return self.w // 2, self.gy + self.span // 2

    def _cell(self, x, y):
        return ((y - self.gy) // CELL_H) * self.cols + x // CELL_W

    # ---- moss --------------------------------------------------------------

    def _new_strain(self, b, s, tone):
        for sid in range(1, len(self.strains)):
            if self.strains[sid] is None or self.moss_hist.get(sid, 0) <= 0 and sid > 5:
                self.strains[sid] = (b, s, tone)
                self.moss_hist[sid] = 0
                return sid
        if len(self.strains) >= 255:
            return None
        self.strains.append((b, s, tone))
        self.moss_hist[len(self.strains) - 1] = 0
        return len(self.strains) - 1

    def _moss_seed(self, cx, cy, sid, n):
        rng = self.rng
        shape = R_PENTOMINO if n < 7 else ACORN
        for dx, dy in shape:
            self._moss_set(cx + dx, cy + dy, sid)
        for _ in range(n):
            self._moss_set(cx + rng.randint(-3, 7), cy + rng.randint(-2, 4), sid)

    def _moss_set(self, cx, cy, sid):
        if not (1 <= cx < self.mc - 1 and 1 <= cy < self.mr - 1):
            return
        c = cy * self.mc + cx
        old = self.cells[c]
        if old == sid:
            return
        if sid and not self.s.ground[(self.gy + cy * MOSS_CELL) * self.w + cx * MOSS_CELL]:
            return
        self.cells[c] = sid
        if old:
            self.moss_hist[old] = self.moss_hist.get(old, 1) - 1
            self.moss_count -= 1
        if sid:
            self.moss_hist[sid] = self.moss_hist.get(sid, 0) + 1
            self.moss_count += 1
        self._moss_draw(cx, cy)
        active, flag = self.moss_active, self.moss_flag
        mc = self.mc
        if not flag[c]:
            flag[c] = 1
            active.append(c)
        for dx, dy in NBR8:
            k = c + dy * mc + dx
            if not flag[k]:
                flag[k] = 1
                active.append(k)

    def _moss_draw(self, cx, cy):
        c = cy * self.mc + cx
        sid = self.cells[c]
        px = self.gy + cy * MOSS_CELL
        i = px * self.w + cx * MOSS_CELL
        moss = self.c.moss
        refresh = self.c.refresh
        if sid:
            tone = self.strains[sid][2]
            ramp = MOSS[tone]
            h = (cx * 7 + cy * 13) & 7
            level = 0 + (h > 3) + (h == 7)
            depth = (cy * MOSS_CELL) / self.span
            if depth > 0.3:
                level += 1
            a, b, d = ramp[level], ramp[level - 1], ramp[level + 1]
            moss[i] = a
            moss[i + 1] = b if h & 1 else a
            moss[i + self.w] = d if h & 2 else a
            moss[i + self.w + 1] = 0 if h & 4 == 0 and h & 1 else b
        else:
            moss[i] = moss[i + 1] = moss[i + self.w] = moss[i + self.w + 1] = 0
        refresh(i)
        refresh(i + 1)
        refresh(i + self.w)
        refresh(i + self.w + 1)

    def _moss_tick(self, budget):
        active = self.moss_active
        if not active:
            return
        rng = self.rng
        cells = self.cells
        mc = self.mc
        flag = self.moss_flag
        cap = int(self.mc * self.mr * 0.17 * self.cfg.density)
        for _ in range(min(budget, len(active))):
            k = rng.randrange(len(active))
            c = active[k]
            active[k] = active[-1]
            active.pop()
            flag[c] = 0
            cy, cx = divmod(c, mc)
            if not (1 <= cx < mc - 1 and 1 <= cy < self.mr - 1):
                continue
            n = 0
            cand = 0
            for off in (-mc - 1, -mc, -mc + 1, -1, 1, mc - 1, mc, mc + 1):
                v = cells[c + off]
                if v:
                    n += 1
                    if not cand or rng.random() < 0.35:
                        cand = v
            cur = cells[c]
            soil = self._cell(cx * MOSS_CELL, self.gy + cy * MOSS_CELL)
            if cur:
                _, surv, _ = self.strains[cur]
                dry = self.wet[soil] < 0.16 and rng.random() < 0.3
                crowded = self.moss_count > cap and rng.random() < 0.25
                if not surv >> n & 1 or dry or crowded:
                    self._moss_set(cx, cy, 0)
                    self.nut[soil] = min(1.0, self.nut[soil] + 0.004)
            elif n and cand:
                birth, _, tone = self.strains[cand]
                if birth >> n & 1:
                    wet = self.wet[soil]
                    shade = self.shade[soil]
                    # Moss loves damp shade; it spreads only reluctantly anywhere else.
                    if rng.random() < clamp(wet * 1.6 + shade * 0.6 - 0.1) and self.moss_count < cap:
                        sid = cand
                        if rng.random() < 0.004:
                            sid = self._mutate_strain(cand) or cand
                        self._moss_set(cx, cy, sid)
                        self.nut[soil] = max(0.0, self.nut[soil] - 0.003)
                    elif not flag[c]:
                        # Conditions said no, for now. Stay on the frontier and try again later.
                        flag[c] = 1
                        active.append(c)
        # Spot-check settled moss too, so drought and crowding reach the middle of a patch.
        for _ in range(4):
            c = rng.randrange(len(cells))
            if cells[c] and not flag[c]:
                flag[c] = 1
                active.append(c)

    def _mutate_strain(self, sid):
        rng = self.rng
        b, s, tone = self.strains[sid]
        if rng.random() < 0.35:
            # Birth digits stay at 3 and up. Lower ones explode across the whole meadow.
            b ^= 1 << rng.randint(4, 8)
        else:
            s ^= 1 << rng.randint(1, 8)
        if rng.random() < 0.3:
            tone = rng.randrange(len(MOSS))
        self.stats["rule_mutations"] += 1
        return self._new_strain(b, s, tone)

    # ---- lichen: strict Conway, on bark and stone --------------------------

    def _lichen_step(self, patch):
        w, h = patch["w"], patch["h"]
        cells = patch["cells"]
        nxt = bytearray(w * h)
        alive = 0
        for y in range(1, h - 1):
            row = y * w
            for x in range(1, w - 1):
                i = row + x
                n = (cells[i - w - 1] + cells[i - w] + cells[i - w + 1] + cells[i - 1] + cells[i + 1]
                     + cells[i + w - 1] + cells[i + w] + cells[i + w + 1])
                if n == 3 or (n == 2 and cells[i]):
                    nxt[i] = 1
                    alive += 1
        same = nxt == cells
        fg = self.c.fg
        base_w = self.w
        ramp = MYC[patch["tone"]]
        under = patch["under"]
        for y in range(h):
            for x in range(w):
                i = y * w + x
                if nxt[i] != cells[i]:
                    p = (patch["y"] + y) * base_w + patch["x"] + x
                    if nxt[i]:
                        if fg[p]:  # lichen only ever grows on something solid
                            under[p] = fg[p]
                            fg[p] = ramp[1]
                            self.c.refresh(p)
                    elif p in under:
                        fg[p] = under.pop(p)
                        self.c.refresh(p)
        patch["cells"] = nxt
        patch["idle"] = patch["idle"] + 1 if same or alive < 3 else 0
        if patch["idle"] > 12:
            patch["idle"] = 0
            self._lichen_seed(patch)

    def _lichen_seed(self, patch):
        shape = self.rng.choice((R_PENTOMINO, ACORN))
        w, h = patch["w"], patch["h"]
        ox = self.rng.randint(1, max(1, w - 8))
        oy = self.rng.randint(1, max(1, h - 4))
        for dx, dy in shape:
            x, y = ox + dx, oy + dy
            if 0 < x < w - 1 and 0 < y < h - 1:
                patch["cells"][y * w + x] = 1

    # ---- mycelium ----------------------------------------------------------

    def _random_fungal_genes(self):
        r = self.rng
        return {"hue": r.randrange(len(MYC)), "branch": r.uniform(0.012, 0.05), "wander": r.uniform(0.08, 0.3),
                "thrift": r.uniform(0.3, 0.8), "fruit": r.uniform(2.0, 6.0), "shape": r.random(),
                "cluster": r.random(), "size": r.uniform(0.35, 1.0), "slender": r.random(), "cap": r.randrange(6),
                "parasite": r.uniform(0.0, 0.1)}

    def _mutate_fungal(self, g, other=None):
        r = self.rng
        n = dict(g)
        if other:
            for key in n:
                if r.random() < 0.5:
                    n[key] = other.get(key, n[key])
        n["parasite"] = clamp(n.get("parasite", 0.03) + r.gauss(0, 0.035))
        n["branch"] = clamp(n["branch"] + r.gauss(0, 0.005), 0.006, 0.09)
        n["wander"] = clamp(n["wander"] + r.gauss(0, 0.04), 0.04, 0.6)
        n["thrift"] = clamp(n["thrift"] + r.gauss(0, 0.05), 0.1, 1.0)
        n["fruit"] = clamp(n["fruit"] + r.gauss(0, 0.3), 1.2, 9.0)
        for key in ("shape", "cluster", "slender"):
            n[key] = clamp(n[key] + r.gauss(0, 0.05))
        n["size"] = clamp(n["size"] + r.gauss(0, 0.05), 0.25, 1.0)
        if r.random() < 0.06:
            n["hue"] = r.randrange(len(MYC))
        if r.random() < 0.06:
            n["cap"] = r.randrange(6)
        return n

    def _found_colony(self, x, y, g, gen):
        col = Colony()
        col.cid = self.next_cid
        self.next_cid += 1
        col.g = g
        col.energy = 1.2
        col.tips = [[x, y, self.rng.uniform(0, math.tau)] for _ in range(3)]
        col.nodes = []
        col.head = col.lv1 = col.lv0 = 0
        col.gen = gen
        col.alive = True
        col.born = self.tick_no
        self.colonies.append(col)
        self.colony_by_id[col.cid] = col
        self.stats["colonies_founded"] += 1
        self.stats["max_fungal_gen"] = max(self.stats["max_fungal_gen"], gen)
        return col

    def _myc_tick(self):
        rng = self.rng
        limit = int(self.w * self.span * 0.05 * self.cfg.density)
        total = len(self.myc_node)
        w = self.w
        ground = self.s.ground
        nodes_here = self.myc_node
        for col in list(self.colonies):
            g = col.g
            ramp = MYC[g["hue"]]
            col.energy -= 0.0015 + (len(col.nodes) - col.head) * 0.0000012
            for tip in list(col.tips):
                if rng.random() > 0.45:
                    continue
                x, y, a = tip
                a += rng.gauss(0, g["wander"])
                if rng.random() < 0.05:
                    # Forage toward whichever side smells richer.
                    best, here = a, -1.0
                    for trial in (a - 0.6, a, a + 0.6):
                        tx, ty = int(x + math.cos(trial) * 9), int(y + math.sin(trial) * 5)
                        if 0 <= tx < w and self.gy <= ty < self.h:
                            v = self.nut[self._cell(tx, ty)]
                            if v > here:
                                best, here = trial, v
                    a = best
                # Half the vertical speed: threads lie flat in the ground plane.
                fx, fy = x + math.cos(a), y + math.sin(a) * 0.5
                nx, ny = int(fx), int(fy)
                px, py = int(x), int(y)
                if nx == px and ny == py:
                    tip[0], tip[1], tip[2] = fx, fy, a
                    continue
                if not (0 <= nx < w and self.gy + 2 <= ny < self.h) or not ground[ny * w + nx]:
                    tip[2] = a + math.pi + rng.uniform(-0.6, 0.6)
                    if rng.random() < 0.5 and len(col.tips) > 1:
                        col.tips.remove(tip)
                    continue
                p = ny * w + nx
                node = self.myc_node.get(p)
                if node:
                    if node[0] == col.cid:
                        tip[2] = a + rng.choice((-1, 1)) * rng.uniform(0.7, 1.5)
                        if rng.random() < 0.25 and len(col.tips) > 1:
                            col.tips.remove(tip)
                        continue
                    self._meet(col, tip, p, node)
                    continue
                crowd = 0
                for off in (-w, w, -1, 1, -w - 1, -w + 1, w - 1, w + 1):
                    if p + off in nodes_here:
                        crowd += 1
                if crowd > 2:  # keep threads apart, so they read as lace and not as a smear
                    tip[2] = a + rng.choice((-1, 1)) * rng.uniform(0.8, 2.2)
                    if rng.random() < 0.3 and len(col.tips) > 1:
                        col.tips.remove(tip)
                    continue
                if total >= limit or col.energy < 0.02:
                    if rng.random() < 0.08 and len(col.tips) > 1:
                        col.tips.remove(tip)
                    continue
                parent = py * w + px
                self.myc_node[p] = (col.cid, parent)
                col.nodes.append(p)
                total += 1
                self.c.myc[p] = ramp[1]
                self.c.refresh(p)
                tip[0], tip[1], tip[2] = fx, fy, a
                cell = self._cell(nx, ny)
                # A parasite's body is built for beetles, and is poorer at living on leaf litter.
                take = min(self.nut[cell], (0.006 + 0.012 * g["thrift"]) * (1 - 0.5 * g.get("parasite", 0.0)))
                self.nut[cell] -= take
                col.energy += take * 2.0
                if rng.random() < g["branch"] and len(col.tips) < 9:
                    col.tips.append([fx, fy, a + rng.choice((-1, 1)) * rng.uniform(0.4, 1.1)])
            if not col.tips and col.energy > 0.25 and col.head < len(col.nodes):
                # Sprout a fresh tip from anywhere along the living network.
                p = col.nodes[rng.randrange(col.head, len(col.nodes))]
                col.tips.append([p % w, p // w, rng.uniform(0, math.tau)])
                col.energy -= 0.08
            self._age_threads(col, ramp)
            if col.energy < 0.05 and col.head < len(col.nodes):
                self._retract(col, 2)
            elif total > limit and rng.random() < 0.3:
                self._retract(col, 1)
            elif col.energy > g["fruit"] * 0.1 and len(col.nodes) - col.head > 900:
                self._retract(col, 1)  # old hyphae are shed as a mature colony moves on
            if col.head >= len(col.nodes) and not col.tips and col.head:
                col.alive = False
                self.colonies.remove(col)
                del self.colony_by_id[col.cid]
            elif col.energy < -0.3:
                self._retract(col, 6)
                col.tips = []
            elif col.energy > g["fruit"]:
                self._try_fruit(col)
            if col.head > 2000:
                col.nodes = col.nodes[col.head:]
                col.lv1 = max(0, col.lv1 - col.head)
                col.lv0 = max(0, col.lv0 - col.head)
                col.head = 0

    def _meet(self, col, tip, p, node):
        """Two colonies touch. Close kin fuse; strangers wall each other off."""
        rng = self.rng
        other = self.colony_by_id.get(node[0])
        if not other:
            return False
        if other.g["hue"] == col.g["hue"] and abs(other.g["thrift"] - col.g["thrift"]) < 0.2:
            share = (col.energy + other.energy) / 2
            col.energy = other.energy = share
            tip[0], tip[1] = p % self.w, p // self.w
            if len(other.tips) < 9:
                other.tips.append(list(tip))
            if rng.random() < 0.3:
                col.tips.remove(tip)
            return True
        if col.energy > other.energy * 2 and rng.random() < 0.06:
            # A stronger colony overruns the wall, thread by thread.
            self._kill_thread(p)
            other.energy -= 0.01
            return True
        tip[2] += rng.choice((-1, 1)) * rng.uniform(0.9, 2.4)
        if rng.random() < 0.2 and len(col.tips) > 1:
            col.tips.remove(tip)
        return True

    def _kill_thread(self, p):
        if self.myc_node.pop(p, None) is not None:
            self.c.myc[p] = 0
            self.c.refresh(p)

    def _age_threads(self, col, ramp):
        """Fresh threads are bright; they settle into faint lines as they age."""
        nodes = col.nodes
        n = len(nodes)
        if col.lv1 < col.head:
            col.lv1 = col.head
        if col.lv0 < col.head:
            col.lv0 = col.head
        myc = self.c.myc
        if n - col.lv1 > 12:
            for _ in range(3):
                if col.lv1 < n:
                    p = nodes[col.lv1]
                    col.lv1 += 1
                    if p in self.myc_node:
                        myc[p] = ramp[0]
                        self.c.refresh(p)

    def _retract(self, col, n):
        nodes = col.nodes
        for _ in range(n):
            if col.head >= len(nodes):
                return
            p = nodes[col.head]
            col.head += 1
            if p in self.myc_node and self.myc_node[p][0] == col.cid:
                self._kill_thread(p)
                y = p // self.w
                if y >= self.gy:
                    cell = self._cell(p % self.w, y)
                    self.nut[cell] = min(1.0, self.nut[cell] + 0.002)

    def _try_fruit(self, col):
        rng = self.rng
        if self.climate.dormant or len(col.nodes) - col.head < 30 or sum(1 for pl in self.plants if pl.kind == "mushroom") >= LIMITS["mushroom"]:
            return
        p = col.nodes[rng.randrange(col.head, len(col.nodes))]
        x, y = p % self.w, p // self.w
        if self.wet[self._cell(x, y)] < 0.35 and not self.raining:
            return
        g = col.g
        genes = {"hue": g["cap"], "shape": g["shape"], "cluster": g["cluster"], "size": g["size"],
                 "slender": g["slender"], "tone": 0, "bushy": 0, "bloom": 0, "wet": 0.5, "dark": 0.5}
        plant = self._sprout("mushroom", x, y, genes, col.gen, force=True)
        if plant:
            plant.colony = col.cid
            col.energy -= g["fruit"] * 0.45
            self.stats["fruitings"] += 1

    def _pulse_tick(self):
        """Nutrients in transit: faint sparks running along threads toward the colony's heart."""
        rng = self.rng
        if len(self.pulses) < 7 and self.colonies and rng.random() < 0.18:
            col = rng.choice(self.colonies)
            if len(col.nodes) - col.head > 30:
                p = col.nodes[rng.randrange(max(col.head, len(col.nodes) - 400), len(col.nodes))]
                self.pulses.append([p, MYC[col.g["hue"]], rng.randint(25, 90)])

    # ---- plants ------------------------------------------------------------

    def _random_genes(self, kind):
        r = self.rng
        hue = r.choice({"spire": (0, 0, 0, 1, 7), "clover": (6, 6, 3, 1), "shrub": (4, 4, 4, 3)}.get(kind, (0,)))
        return {"size": r.random(), "bushy": r.random(), "tone": r.random(), "hue": hue, "bloom": r.random(),
                "wet": r.uniform(0.3, 0.75), "dark": r.uniform(0.2, 0.8), "toxin": r.uniform(0.0, 0.15)}

    def _mutate_plant(self, g, pollen=None):
        r = self.rng
        n = dict(g)
        if pollen:
            for key in n:
                if r.random() < 0.5:
                    n[key] = pollen.get(key, n[key])
        n.setdefault("toxin", r.uniform(0.0, 0.1))
        for key in ("size", "bushy", "tone", "bloom", "wet", "dark", "toxin"):
            n[key] = clamp(n[key] + r.gauss(0, 0.05))
        if r.random() < 0.05:
            n["hue"] = r.randrange(len(palette.FLOWER))
        return n

    def _depth_scale(self, y):
        return (0.62 + 0.95 * (y - self.gy) / self.span) * self.scale_s

    def _sprout(self, kind, x, y, g, gen, force=False, ignore_limit=False):
        if not ignore_limit and sum(1 for p in self.plants if p.kind == kind) >= int(LIMITS[kind] * self.cfg.density + 0.5):
            return None
        if not (0 <= x < self.w and self.gy + 3 <= y < self.h) or not self.s.ground[y * self.w + x]:
            return None
        if not force and self._suitability(kind, x, y, g) < self.rng.random():
            return None
        p = Plant()
        p.pid = self.next_pid
        self.next_pid += 1
        p.kind, p.x, p.y, p.g, p.gen = kind, x, y, g, gen
        p.z = min(255, (y - self.gy) * 255 // self.span)
        p.seed = self.rng.randrange(1 << 30)
        p.pix = self._sprite(kind, g, p.seed, x, y)
        p.shadow = None
        p.shown = 0
        p.age = 0
        p.life = LIFESPAN[kind] * self.rng.uniform(0.7, 1.3) * (0.8 + g.get("size", 0.5) * 0.4)
        p.wilting = False
        p.colony = 0
        p.pollen = None
        p.linked = False
        p.shed = 0
        self.plants.append(p)
        self.stats["births"] += 1
        self.stats["max_plant_gen"] = max(self.stats["max_plant_gen"], gen)
        return p

    def _sprite(self, kind, g, seed, x, y):
        """A plant's pixels, shaded for where it stands relative to the moon."""
        light = -1 if self.s.moon[0] < x else 1
        return bevel(BUILDERS[kind](g, seed, self._depth_scale(y)), light)

    def _cast_shadow(self, plant):
        """Once a plant has some size, it lays a soft shadow on the ground, away from the moon."""
        c = self.c
        xs = [d[0] for d in plant.pix]
        width = max(xs) - min(xs) + 1
        ys = [d[1] for d in plant.pix]
        height = max(ys) - min(ys) + 1
        rx = max(2, width // 2)
        ry = max(1, min(4, height // 6 + 1))
        away = 1 if self.s.moon[0] < plant.x else -1
        cx = plant.x + (min(xs) + max(xs)) // 2 + away * min(rx, 2 + height // 5)
        cy = plant.y + 1
        cells = []
        for dy in range(-ry, ry + 1):
            for dx in range(-rx, rx + 1):
                if (dx / (rx + 0.5)) ** 2 + (dy / (ry + 0.5)) ** 2 >= 1:
                    continue
                if abs(dx) > rx * 0.7 and (cx + dx + cy + dy) & 1:  # dithered away at the edge
                    continue
                x, y = cx + dx, cy + dy
                if 0 <= x < self.w and self.gy <= y < self.h:
                    cells.append(y * self.w + x)
        for i in cells:
            if c.shade[i] < 250:
                c.shade[i] += 1
            if c.shade[i] == 1:
                c.comp[i] = 0  # force a redraw
                c.refresh(i)
        plant.shadow = cells

    def _lift_shadow(self, plant):
        c = self.c
        for i in plant.shadow or ():
            if c.shade[i]:
                c.shade[i] -= 1
                if not c.shade[i]:
                    c.comp[i] = 0
                    c.refresh(i)
        plant.shadow = None

    def _suitability(self, kind, x, y, g):
        cell = self._cell(x, y)
        wet = self.wet[cell]
        shade = self.shade[cell]
        fit = 1.0 - abs(wet - g["wet"]) * 1.1 - abs(shade - g["dark"]) * 0.5
        # Succession: hungry species wait until pioneers have built the soil.
        fit -= max(0.0, HUNGER[kind] - self.nut[cell]) * 1.6
        table = COMPANIONS.get(kind, {})
        for other in self.plants:
            if abs(other.x - x) < 26 and abs(other.y - y) < 14:
                fit += table.get(other.kind, 0.0) * 0.5
                if abs(other.x - x) < 5 and abs(other.y - y) < 3:
                    fit -= 0.2
        return clamp(fit)

    def _reveal(self, plant, upto):
        """Show the plant's pixels up to index `upto`, respecting who stands in front."""
        w = self.w
        h = self.h
        life, life_z = self.c.life, self.c.life_z
        refresh = self.c.refresh
        pix = plant.pix
        z = plant.z
        upto = min(upto, len(pix))
        for k in range(plant.shown, upto):
            dx, dy, color = pix[k]
            x, y = plant.x + dx, plant.y + dy
            if 0 <= x < w and 0 <= y < h:
                i = y * w + x
                if z >= life_z[i] or not life[i]:
                    life[i] = color
                    life_z[i] = z
                    refresh(i)
        plant.shown = max(plant.shown, upto)
        if plant.shadow is None and plant.shown > len(pix) * 0.35 and plant.shown >= 6:
            self._cast_shadow(plant)

    def _unshow(self, plant, down_to):
        w, h = self.w, self.h
        life, life_z = self.c.life, self.c.life_z
        refresh = self.c.refresh
        pix = plant.pix
        z = plant.z
        for k in range(plant.shown - 1, down_to - 1, -1):
            dx, dy, _ = pix[k]
            x, y = plant.x + dx, plant.y + dy
            if 0 <= x < w and 0 <= y < h:
                i = y * w + x
                if life_z[i] == z and life[i]:
                    life[i] = 0
                    life_z[i] = 0
                    refresh(i)
        plant.shown = min(plant.shown, down_to)

    def _restamp(self, plant):
        """After a removal, redraw whatever was hiding behind it."""
        xs = [d[0] for d in plant.pix] or [0]
        ys = [d[1] for d in plant.pix] or [0]
        x0, x1 = plant.x + min(xs), plant.x + max(xs)
        y0, y1 = plant.y + min(ys), plant.y + max(ys)
        others = [p for p in self.plants if p is not plant and p.shown and p.x > x0 - 60 and p.x < x1 + 60]
        others.sort(key=lambda p: p.z)
        w, h = self.w, self.h
        life, life_z = self.c.life, self.c.life_z
        refresh = self.c.refresh
        for p in others:
            for k in range(p.shown):
                dx, dy, color = p.pix[k]
                x, y = p.x + dx, p.y + dy
                if x0 <= x <= x1 and y0 <= y <= y1 and 0 <= x < w and 0 <= y < h:
                    i = y * w + x
                    if p.z >= life_z[i] or not life[i]:
                        life[i] = color
                        life_z[i] = p.z
                        refresh(i)

    def _plant_tick(self, budget):
        rng = self.rng
        plants = self.plants
        if not plants:
            return
        for _ in range(min(budget, len(plants))):
            p = plants[rng.randrange(len(plants))]
            self._plant_update(p, len(plants) / max(1, budget))

    def _plant_update(self, p, dt):
        rng = self.rng
        if self.climate.dormant and not p.wilting and p.kind != "snowdrop":
            return  # waiting for spring; nothing ages under the snow
        p.age += dt
        cell = self._cell(p.x, p.y)
        wet = self.wet[cell]
        nut = self.nut[cell]
        kind = p.kind
        if kind in ("mushroom", "log"):
            return self._saprophyte_update(p, dt, cell)
        if p.wilting:
            return self._wilt(p, cell)
        if not p.linked and rng.random() < 0.05:
            self._check_mycorrhiza(p)
        if p.shown < len(p.pix):
            # Growth is paid for in soil nutrients, more cheaply with a fungal partner.
            rate = clamp(nut * 1.6) * clamp(wet * 1.7) * (1.4 if p.linked else 0.85)
            rate *= 1 - 0.3 * p.g.get("toxin", 0.0)  # poison is expensive to make
            if kind == "sapling" and not p.linked:
                rate *= 0.35
            if rng.random() < rate * 0.9:
                step = 1 + len(p.pix) // 70
                self._reveal(p, p.shown + step)
                self.nut[cell] = max(0.0, nut - 0.0007 * step)
        else:
            self._mature(p, dt, cell)
        # The plant shapes the soil around it.
        if kind == "clover":
            self.nut[cell] = min(1.0, nut + 0.0016 * dt)  # nitrogen fixing
        reach = {"sapling": 3, "shrub": 2, "fern": 1}.get(kind, 0)
        if reach:
            target = min(1.0, 0.25 + 0.2 * reach) * p.shown / max(1, len(p.pix))
            row, col = divmod(cell, self.cols)
            for r in range(max(0, row - reach), min(self.rows, row + reach + 1)):
                for c in range(max(0, col - reach), min(self.cols, col + reach + 1)):
                    k = r * self.cols + c
                    if self.shade[k] < target:
                        self.shade[k] += 0.02
        # Stress and old age.
        fit = 1.0 - abs(wet - p.g["wet"]) - abs(self.shade[cell] - p.g["dark"]) * 0.5
        if p.age > p.life or (fit < 0.3 and rng.random() < 0.003 * dt):
            p.wilting = True
            p.shed = 0

    def _mature(self, p, dt, cell):
        rng = self.rng
        kind = p.kind
        # Leaf fall: the slow, steady gift to the soil.
        if kind != "clover" and rng.random() < 0.02:
            lx = p.x + rng.randint(-5, 5)
            ly = min(self.h - 1, p.y + rng.randint(-1, 2))
            if 0 <= lx < self.w:
                self._add_litter(lx, ly)
        if kind in SEASONAL_PLANTS:
            return
        seed_chance = {"clover": 0.012, "fern": 0.003, "spire": 0.01, "shrub": 0.0016, "sapling": 0.00025}[kind]
        if rng.random() < seed_chance * dt * clamp(self.nut[cell] * 2):
            spread = {"clover": 16, "fern": 34, "spire": 44, "shrub": 40, "sapling": 80}[kind]
            x = p.x + int(rng.gauss(0, spread))
            y = p.y + int(rng.gauss(0, spread * 0.3))
            child = self._sprout(kind, x, y, self._mutate_plant(p.g, p.pollen), p.gen + 1)
            if child:
                p.pollen = None
                self.nut[cell] = max(0.0, self.nut[cell] - 0.01)

    def _check_mycorrhiza(self, p):
        """If a hypha runs near the roots they trade: sugar for minerals."""
        w = self.w
        for dy in (-1, 0, 1):
            for dx in range(-3, 4):
                node = self.myc_node.get((p.y + dy) * w + p.x + dx)
                if node:
                    col = self.colony_by_id.get(node[0])
                    if col:
                        p.linked = True
                        col.energy += 0.25
                        return

    def _add_litter(self, x, y):
        i = y * self.w + x
        if not self.s.ground[i]:
            return
        cell = self._cell(x, y)
        self.nut[cell] = min(1.0, self.nut[cell] + 0.012)
        if not self.c.life[i] and not self.c.moss[i] and self.rng.random() < 0.25:
            self.c.moss[i] = LITTER[self.rng.randint(0, 1)]
            self.c.refresh(i)

    def _wilt(self, p, cell):
        rng = self.rng
        if p.shown <= 0:
            return self._remove(p, cell)
        n = max(1, len(p.pix) // 10)
        # Withered parts brown first, then crumble from the tips down.
        if p.shed < p.shown and rng.random() < 0.8:
            w, h = self.w, self.h
            for k in range(p.shed, min(p.shown, p.shed + n)):
                dx, dy, _ = p.pix[k]
                x, y = p.x + dx, p.y + dy
                if 0 <= x < w and 0 <= y < h:
                    i = y * w + x
                    if self.c.life_z[i] == p.z and self.c.life[i]:
                        self.c.life[i] = LITTER[(x + y) % 2]
                        self.c.refresh(i)
            p.shed += n
        elif rng.random() < 0.7:
            self._unshow(p, max(0, p.shown - n))
            self.nut[cell] = min(1.0, self.nut[cell] + 0.002 * n)
            if rng.random() < 0.4:
                self._add_litter(clamp(p.x + rng.randint(-4, 4), 0, self.w - 1), p.y)

    def _remove(self, p, cell):
        kind = p.kind
        self.plants.remove(p)
        self._lift_shadow(p)
        self.stats["deaths"] += 1
        self.nut[cell] = min(1.0, self.nut[cell] + 0.06)
        self._restamp(p)
        if kind == "sapling":
            self._sprout("log", p.x, p.y, dict(p.g), p.gen, force=True)

    def _saprophyte_update(self, p, dt, cell):
        rng = self.rng
        if p.wilting:
            return self._wilt(p, cell)
        if p.shown < len(p.pix):
            speed = 0.6 if p.kind == "mushroom" else 0.9
            if rng.random() < speed:
                self._reveal(p, p.shown + 1 + len(p.pix) // (24 if p.kind == "mushroom" else 40))
        elif p.kind == "mushroom":
            if rng.random() < 0.012 * dt and len(self.spores) < 8:
                g = self.colony_by_id.get(p.colony)
                self.spores.append({"x": p.x * 1.0, "y": p.y - 4.0, "vx": rng.uniform(-0.25, 0.25), "alt": rng.uniform(6, 30),
                                    "g": g.g if g else None, "gen": p.gen + 1})
        elif p.kind == "log":
            # A nurse log slowly gives itself back, enriching and shading its neighbours.
            row, col = divmod(cell, self.cols)
            for r in range(max(0, row - 1), min(self.rows, row + 2)):
                for c in range(max(0, col - 2), min(self.cols, col + 3)):
                    k = r * self.cols + c
                    self.nut[k] = min(1.0, self.nut[k] + 0.0006 * dt)
                    self.wet_base[k] = min(0.9, self.wet_base[k] + 0.00002 * dt)
        if p.age > p.life:
            p.wilting = True
            p.shed = 0

    # ---- soil and weather --------------------------------------------------

    def _soil_tick(self):
        rng = self.rng
        n = len(self.nut)
        nut, wet, shade, wet_base = self.nut, self.wet, self.shade, self.wet_base
        cols = self.cols
        rain = 0.012 if self.raining else 0.0
        for _ in range(26):
            a = rng.randrange(n)
            b = a + rng.choice((-1, 1, -cols, cols))
            if 0 <= b < n:
                d = (nut[a] - nut[b]) * 0.15
                nut[a] -= d
                nut[b] += d
                d = (wet[a] - wet[b]) * 0.2
                wet[a] -= d
                wet[b] += d
            # Water relaxes toward its resting level; shade forgets what no longer casts it.
            wet[a] += (wet_base[a] - wet[a]) * 0.02 + rain * 4
            if wet[a] > 1:
                wet[a] = 1.0
            shade[a] *= 0.995
            # A whisper of leaf fall from the canopy overhead.
            if nut[a] < 0.25:
                nut[a] += 0.002
        if self.raining:
            self.raining -= 1
            if not self.raining:
                self.next_rain = self.rng.randint(4000, 14000)
        elif self.cfg.weather:
            self.next_rain -= 1
            if self.next_rain <= 0:
                self.start_rain()

    def start_rain(self):
        self.next_rain = self.rng.randint(4000, 14000)
        self.climate.start_precipitation()

    # ---- the slow tick -----------------------------------------------------

    def tick(self):
        self.tick_no += 1
        n = self.tick_no
        climate = self.climate
        climate.tick()
        self.clouds.tick()
        self.hanging.tick()
        self._soil_tick()
        # Under snow the moss and the threads all but stop, which costs almost nothing.
        if not climate.dormant:
            self._moss_tick(34)
            self._myc_tick()
        elif n % 8 == 0:
            self._myc_tick()
        self._plant_tick(7)
        if n % 300 == 0:
            self._seasonal_plants()
        self._pulse_tick()
        if n % 4 == 0:
            for patch in self.lichen:
                self._lichen_step(patch)
        if n % 1500 == 0:
            self._revive()
        self._critter_tick()
        self.wild.tick()

    def _seasonal_plants(self):
        """Each kind comes up in its season, descended from last year's, and dies back after."""
        rng = self.rng
        climate = self.climate
        bank = self.wild.evo.memory.setdefault("seed_bank", {})
        for p in self.plants:
            if p.kind in SEASONAL_PLANTS and not p.wilting and climate.weights[SEASONAL_PLANTS[p.kind]] < 0.2:
                p.wilting = True
                p.shed = 0
        for kind, season in SEASONAL_PLANTS.items():
            weight = climate.weights[season]
            if weight < 0.35 or rng.random() > 0.35 * weight:
                continue
            if climate.dormant and kind != "snowdrop" and not (kind == "crocus" and climate.snow < 0.6):
                continue
            x, y = self._random_ground()
            parent = bank.get(kind)
            genes = self._mutate_plant(parent) if parent else self._random_genes(kind)
            for _ in range(rng.randint(1, 3)):  # they come up in little drifts
                p = self._sprout(kind, x + rng.randint(-12, 12), y + rng.randint(-4, 4), genes, 0, force=True)
                if p:
                    bank[kind] = genes

    def _settle_on_landscape(self):
        """The ground may have changed since this forest was saved. Nothing should be left growing in the pond."""
        ground = self.s.ground
        w = self.w
        for c in range(len(self.cells)):
            if self.cells[c]:
                cy, cx = divmod(c, self.mc)
                y = self.gy + cy * MOSS_CELL
                x = cx * MOSS_CELL
                if not (ground[y * w + x] and ground[y * w + x + 1] and ground[(y + 1) * w + x] and ground[(y + 1) * w + x + 1]):
                    self._moss_set(cx, cy, 0)
        for p in list(self.plants):
            if not ground[p.y * w + p.x]:
                self._unshow(p, 0)
                self._remove(p, self._cell(p.x, p.y))
        for i in [i for i in self.myc_node if not ground[i]]:
            self._kill_thread(i)
        self.c.flatten()

    def _revive(self):
        """Spores and seeds blow in from the wider forest, so nothing is ever truly lost."""
        rng = self.rng
        if len(self.colonies) < 3:
            self._found_colony(*self._random_ground(), self._random_fungal_genes(), 0)
        if self.moss_count < 900:
            live = [i for i in range(1, len(self.strains)) if self.strains[i]]
            if live:
                for _ in range(3):
                    self._moss_seed(rng.randrange(self.mc), rng.randrange(self.mr), rng.choice(live), 7)
        if self.moss_hist and not self.moss_active and self.moss_count:
            for _ in range(40):
                c = rng.randrange(len(self.cells))
                if not self.moss_flag[c]:
                    self.moss_flag[c] = 1
                    self.moss_active.append(c)
        for kind in ("clover", "spire"):
            if sum(1 for p in self.plants if p.kind == kind) < 4:
                x, y = self._random_ground()
                self._sprout(kind, x, y, self._random_genes(kind), 0, force=True)
        if not any(p.kind == "sapling" for p in self.plants) and rng.random() < 0.3:
            x, y = self._random_ground()
            self._sprout("sapling", x, y, self._random_genes("sapling"), 0)
        if not any(p.kind == "fern" for p in self.plants):
            x, y = self._random_ground()
            self._sprout("fern", x, y, self._random_genes("fern"), 0, force=True)

    # ---- fauna -------------------------------------------------------------

    def _new_fly(self):
        rng = self.rng
        return {"x": rng.randrange(self.w) * 1.0,
                "y": self.gy + rng.random() ** 1.3 * self.span * 0.85 - rng.uniform(0, self.span * 0.12),
                "phase": rng.random(), "freq": rng.uniform(0.09, 0.14), "vx": 0.0, "vy": 0.0,
                "hue": 0, "bright": rng.uniform(0.35, 0.65)}

    def _fly_offspring(self, f):
        """Replace a firefly with the young of two others. Brighter flashers get more mates."""
        rng = self.rng
        others = [o for o in self.flies if o is not f]
        if len(others) < 2:
            f.update(self._new_fly())
            return
        a = max(rng.sample(others, min(2, len(others))), key=lambda o: o["bright"])
        b = rng.choice(others)
        fresh = self._new_fly()
        fresh["freq"] = clamp((a["freq"] + b["freq"]) / 2 + rng.gauss(0, 0.004), 0.05, 0.25)
        fresh["bright"] = clamp((a["bright"] + b["bright"]) / 2 + rng.gauss(0, 0.04), 0.05, 1.0)
        fresh["hue"] = rng.choice((a["hue"], b["hue"])) if rng.random() > 0.02 else rng.randrange(len(FLY_RAMPS))
        f.update(fresh)

    def _new_beetle(self, x, y, g=None, energy=1.0):
        return {"x": x, "y": y, "d": self.rng.randrange(8), "g": g or self.wild.evo.resident_genes("beetle"),
                "energy": energy, "wait": 0, "inf": 0, "inf_t": 0}

    def _new_moth(self, x=None, y=None, g=None, energy=1.0):
        rng = self.rng
        return {"x": rng.randrange(self.w) * 1.0 if x is None else x,
                "y": self.gy + rng.random() * self.span * 0.8 if y is None else y,
                "target": None, "rest": rng.randint(1, 200), "pollen": None, "e": energy,
                "g": g or self.wild.evo.resident_genes("moth")}

    def _critter_tick(self):
        rng = self.rng
        if not self.cfg.fauna:
            return
        beetles = self.beetles
        if self.climate.dormant:  # under bark and leaf litter until it warms
            self._critter_winter()
            return
        for b in list(beetles):
            g = b["g"]
            b["energy"] -= 0.0007 * (0.7 + 0.6 * g["size"])
            if b["inf"]:
                b["inf_t"] -= 1
                if b["inf_t"] <= 0:
                    self._beetle_burst(b)
                    continue
            b["wait"] -= 1
            if b["wait"] > 0:
                continue
            b["wait"] = int(1 + (1 - g["speed"]) * 7)
            if rng.random() < (0.7 if b["inf"] else 0.25):  # the infected wander, as if not themselves
                b["d"] = (b["d"] + rng.choice((-1, 1))) & 7
            dx, dy = DIRS[b["d"]]
            nx, ny = b["x"] + dx, b["y"] + (dy if rng.random() < 0.5 else 0)
            if not (0 < nx < self.w - 1 and self.gy + 2 < ny < self.h - 1) or not self.s.ground[ny * self.w + nx]:
                b["d"] = (b["d"] + 4) & 7
                continue
            b["x"], b["y"] = nx, ny
            # Grazes moss and picks at litter.
            cx, cy = nx // MOSS_CELL, (ny - self.gy) // MOSS_CELL
            if g["diet"] < 0.55 and 1 <= cx < self.mc - 1 and 1 <= cy < self.mr - 1 and self.cells[cy * self.mc + cx] and rng.random() < 0.08:
                self._moss_set(cx, cy, 0)
                b["energy"] += 0.25
                cell = self._cell(nx, ny)
                self.nut[cell] = min(1.0, self.nut[cell] + 0.003)
            node = self.myc_node.get(ny * self.w + nx)
            if node:
                col = self.colony_by_id.get(node[0])
                if col:
                    if g["diet"] >= 0.45 and rng.random() < 0.25:  # some lines have learned to graze on fungus
                        self._kill_thread(ny * self.w + nx)
                        b["energy"] += 0.15
                    if not b["inf"] and rng.random() < col.g.get("parasite", 0.0) * (1 - g["resist"]) * 0.06:
                        b["inf"], b["inf_t"] = col.cid, rng.randint(240, 700)
                        self.wild.note("beetle infected by fungus")
            if b["energy"] > 2.2 and len(beetles) < int(16 * self.cfg.density) and not b["inf"]:
                b["energy"] -= 1.0
                beetles.append(self._new_beetle(nx, ny, self.wild.evo.child_of(g, rng.choice(beetles)["g"])))
            if b["energy"] <= 0:
                beetles.remove(b)
                cell = self._cell(nx, ny)
                self.nut[cell] = min(1.0, self.nut[cell] + 0.03)
        for m in list(self.moths):
            m["e"] -= 0.00012
            if m["e"] <= 0:
                self.moths.remove(m)
        if self.flies and rng.random() < 0.002:  # the brightest burn out soonest; the young of others replace them
            self._fly_offspring(max(rng.sample(self.flies, min(3, len(self.flies))), key=lambda f: f["bright"]))
        if not self.stag:
            self.next_stag -= 1
            if self.next_stag <= 0:
                self.summon_stag()

    def _critter_winter(self):
        if not self.stag:
            self.next_stag -= 1
            if self.next_stag <= 0:
                self.summon_stag()

    def _beetle_burst(self, b):
        """The fungus has finished with its host, and fruits from where it fell."""
        rng = self.rng
        self.beetles.remove(b)
        col = self.colony_by_id.get(b["inf"])
        x, y = b["x"], b["y"]
        self.wild.enrich(x, y, 0.05)
        if not col:
            return
        g = col.g
        genes = {"hue": g["cap"], "shape": g["shape"], "cluster": 0.0, "size": g["size"], "slender": g["slender"],
                 "tone": 0, "bushy": 0, "bloom": 0, "wet": 0.5, "dark": 0.5}
        plant = self._sprout("mushroom", x, y, genes, col.gen, force=True, ignore_limit=True)
        if plant:
            plant.colony = col.cid
        col.energy += 1.5
        for _ in range(3):
            self.spores.append({"x": x * 1.0, "y": y - 3.0, "vx": rng.uniform(-0.25, 0.25), "alt": rng.uniform(6, 26),
                                "g": g, "gen": col.gen + 1})
        self.wild.note("beetle consumed by fungus")
        if self.wild.evo._once("first-burst"):
            self.wild.evo.say("A mushroom has grown from the body of a beetle.")

    def summon_stag(self, force=True):
        if not self.cfg.fauna or self.stag:
            return
        rng = self.rng
        inds = self.wild.evo.pick("stag", 1, force)
        if not inds:
            self.next_stag = rng.randint(2000, 8000)  # none about, for now
            return
        heading = rng.choice((-1, 1))
        y = self.gy + int(self.span * rng.uniform(0.25, 0.55))
        self.stag = {"x": (-26 if heading > 0 else self.w + 26) * 1.0, "y": y, "dir": heading, "step": 0, "graze": 0, "seeds": None,
                     "drunk": False, "drinking": False, "ind": inds[0]}
        self.stats["stag_visits"] += 1
        self.next_stag = rng.randint(7000, 20000)

    def _stag_frame(self, overlay):
        stag = self.stag
        rng = self.rng
        w, h = self.w, self.h
        if self.frame_no % 4 == 0:
            if stag["graze"] > 0:
                stag["graze"] -= 1
            else:
                stag["x"] += stag["dir"]
                stag["step"] += 1
                # It walks round the water, never across it.
                stag["y"] = self.wild.steer(int(stag["x"]) + 11, stag["y"], stag["y"], stag["dir"])
                self.climate.footprint("stag", int(stag["x"]) + 8, stag["y"] + 1, stag["step"])
                cx, cy, rx, ry = self.s.pond
                muzzle = int(stag["x"]) + (21 if stag["dir"] > 0 else 0)
                if 0 <= muzzle < w and stag["y"] + 5 < h and self.s.water[(stag["y"] + 5) * w + muzzle] and not stag["drunk"]:
                    stag["drunk"] = True  # at the bank: stop and drink, above its own reflection
                    stag["graze"] = rng.randint(30, 70)
                    stag["drinking"] = True
                elif rng.random() < 0.006:
                    stag["graze"] = rng.randint(12, 70)
                    stag["drinking"] = False
                    self._stag_browse(stag)
            if stag["x"] < -30 or stag["x"] > w + 30:
                self.stag = None
                return
            if stag.get("drinking") and stag["graze"] and stag["graze"] % 14 == 0:
                self.wild.ripple(int(stag["x"]) + (21 if stag["dir"] > 0 else 0), stag["y"] + 5)
        g = stag["ind"]["g"]
        rows = animal_rows("stag", "lowered" if stag["graze"] else f"walk{(stag['step'] // 4) % 2}", g)
        placed = stamp(self, overlay, rows, int(stag["x"]), stag["y"], stag["dir"] < 0, coat_colors(g), coat_colors(g)["+"])
        # The pale ones shine a little on whatever they pass.
        if g["coat"] > 0.42 and g["coat"] < 0.62 or g["glow"] > 0.5:
            halo(self, overlay, placed, palette.LIT)

    def _stag_browse(self, stag):
        """The stag nips a plant, picks up its seeds, and drops others along its way."""
        rng = self.rng
        near = [p for p in self.plants if abs(p.x - stag["x"] - 11) < 22 and abs(p.y - stag["y"]) < 14
                and p.kind in ("fern", "spire", "shrub", "clover") and p.shown > 8]
        if near:
            p = rng.choice(near)
            if stag["seeds"]:
                kind, g, gen = stag["seeds"]
                self._sprout(kind, int(stag["x"]) + rng.randint(-6, 6), stag["y"] + rng.randint(-3, 3),
                             self._mutate_plant(g), gen + 1, force=True)
            stag["seeds"] = (p.kind, p.g, p.gen)
            self._unshow(p, max(4, p.shown // 2))
            self._restamp(p)
        cell = self._cell(max(0, min(self.w - 1, int(stag["x"]))), stag["y"])
        self.nut[cell] = min(1.0, self.nut[cell] + 0.08)

    def _fly_frame(self, overlay, dim):
        rng = self.rng
        flies = self.flies
        w, h = self.w, self.h
        frame = self.c.frame
        lit2 = palette.LIT2
        gy = self.gy
        dt = 1.0 / max(1, self.fps)
        flashers = []
        for f in flies:
            f["phase"] += f["freq"] * dt
            if f["phase"] >= 1.0:
                f["phase"] -= 1.0
                flashers.append(f)
        # Pulse coupling: seeing a neighbour flash nudges you toward flashing. Left alone,
        # scattered blinking drifts over hours into waves that roll through the clearing.
        for src in flashers:
            for f in flies:
                if f is not src and abs(f["x"] - src["x"]) < 80 and abs(f["y"] - src["y"]) < 45:
                    if f["phase"] > 0.04:
                        f["phase"] = min(0.999, f["phase"] * 1.01 + 0.006)
                        f["freq"] += (src["freq"] - f["freq"]) * 0.005
        if dim:
            return
        weights = self.climate.weights
        flying = int(len(flies) * min(1.0, weights["summer"] + 0.55 * (weights["spring"] + weights["autumn"]) + 0.05))
        if self.climate.dormant:
            flying = min(flying, 2)  # almost none, and you'd wonder about those
        for f in flies[:flying]:
            if rng.random() < 0.05:
                f["vx"] = rng.uniform(-0.28, 0.28)
                f["vy"] = rng.uniform(-0.14, 0.14)
            f["x"] += f["vx"]
            f["y"] += f["vy"]
            if f["x"] < 0:
                f["x"] += w
            elif f["x"] >= w:
                f["x"] -= w
            if f["y"] < gy - self.span * 0.22:
                f["vy"] = 0.1
            elif f["y"] > h - 8:
                f["vy"] = -0.1
            x, y = int(f["x"]), int(f["y"])
            i = y * w + x
            if not 0 <= i < w * h:
                continue
            ph = f["phase"]
            ramp = FLY_RAMPS[f["hue"]]
            lit_for = 0.06 + 0.16 * f["bright"]
            if ph < lit_for:
                level = 4 if ph < lit_for * 0.3 else 3 if ph < lit_for * 0.6 else 2
                near = y > gy + self.span * 0.22
                overlay[i] = ramp[level]
                if near or level >= 3:
                    for off, lv in ((-1, level - 2), (1, level - 2), (-w, level - 2), (w, level - 2)):
                        k = i + off
                        if 0 <= k < w * h:
                            overlay[k] = ramp[lv]
                    if near:
                        for off in (-w - 1, -w + 1, w - 1, w + 1, -2, 2, -2 * w, 2 * w):
                            k = i + off
                            if 0 <= k < w * h and k not in overlay:
                                overlay[k] = lit2[frame[k]]
            elif ph < 0.34:
                overlay[i] = ramp[1]
            elif ph > 0.7:
                overlay[i] = ramp[0]

    def _moth_frame(self, overlay):
        rng = self.rng
        w, h = self.w, self.h
        fg = self.c.fg
        evo = self.wild.evo
        if evo.asleep("moth"):
            return
        for m in self.moths:
            if not evo.awake(m["g"]):
                continue  # asleep under a leaf until its own hours come round
            if m["rest"] > 0:
                m["rest"] -= 1
                if m["rest"] == 0:
                    bloomers = [p for p in self.plants if p.kind in ("spire", "clover", "shrub", "crocus", "foxglove", "goldenrod") and p.shown == len(p.pix)]
                    m["target"] = rng.choice(bloomers) if bloomers else None
                    if not m["target"]:
                        m["rest"] = rng.randint(40, 200)
            else:
                t = m["target"]
                if t is None or t not in self.plants:
                    m["rest"] = 60
                    m["target"] = None
                else:
                    tx, ty = t.x + t.pix[-1][0], t.y + t.pix[-1][1]
                    dx, dy = tx - m["x"], ty - m["y"]
                    d = math.hypot(dx, dy)
                    if d < 2:
                        self._pollinate(m, t)
                        m["rest"] = rng.randint(60, 480)
                    else:
                        m["x"] += dx / d * 0.7 + rng.uniform(-0.5, 0.5)
                        m["y"] += dy / d * 0.5 + rng.uniform(-0.5, 0.5)
            x, y = int(m["x"]), int(m["y"])
            if 1 <= x < w - 1 and 0 <= y < h:
                i = y * w + x
                if fg[i]:
                    continue
                g = m["g"]
                if g["active"] > 0.55:  # a day-flier: what we would call a butterfly, bright-winged
                    wing = FLOWER[int(g["coat"] * 7.99)]
                    dark, pale = wing[1], wing[2]
                    overlay[i] = palette.HARE[0]
                else:
                    wing = COATS[int(g["coat"] * (len(COATS) - 1) + 0.5)]
                    dark, pale = wing[1], wing[2]
                    overlay[i] = wing[2]
                if m["rest"] == 0:
                    if self.frame_no & 1:
                        overlay[i - 1] = overlay[i + 1] = dark
                    elif i > w:
                        overlay[i - w - 1] = overlay[i - w + 1] = pale

    def _pollinate(self, moth, plant):
        if moth["pollen"] and moth["pollen"][0] == plant.kind:
            plant.pollen = moth["pollen"][1]
            self.stats["pollinations"] += 1
        moth["pollen"] = (plant.kind, plant.g)
        moth["e"] = min(2.5, moth["e"] + 0.3)
        if moth["e"] > 1.6 and len(self.moths) < int(14 * self.cfg.density):
            moth["e"] -= 0.8
            mate = min(self.rng.sample(self.moths, min(3, len(self.moths))),
                       key=lambda o: abs(o["g"]["active"] - moth["g"]["active"]))
            self.moths.append(self._new_moth(moth["x"], moth["y"], self.wild.evo.child_of(moth["g"], mate["g"]), 0.8))

    def _beetle_frame(self, overlay):
        w, h = self.w, self.h
        fg = self.c.fg
        life = self.c.life
        if self.climate.dormant:
            return
        for b in self.beetles:
            i = b["y"] * w + b["x"]
            if fg[i] or life[i]:
                continue
            g = b["g"]
            shell = coat_colors(g)
            below = i + w
            if below + 1 < w * h and not fg[below] and not life[below]:
                overlay[below] = palette.DARK[self.c.frame[below]]
                overlay[below + 1] = palette.DARK[self.c.frame[below + 1]]
            overlay[i] = shell["#"]
            if i + 1 < w * h:
                overlay[i + 1] = shell["w"]
            if g["size"] > 0.55 and i + 2 < w * h:  # a big one
                overlay[i + 2] = shell["#"]
            if g["ornament"] > 0.7 and i >= w:  # horned
                overlay[i - w + 1] = shell["w"]
            if b["inf"] and i >= w and self.frame_no % 8 < 5:  # a pale stalk has started to grow from it
                overlay[i - w] = SPORE[2]

    def _spore_frame(self, overlay):
        rng = self.rng
        w, h = self.w, self.h
        fg = self.c.fg
        for s in list(self.spores):
            s["x"] += s["vx"] + rng.uniform(-0.12, 0.12)
            s["alt"] -= 0.06
            s["y"] += 0.02 + rng.uniform(-0.04, 0.04)
            x, y = int(s["x"]), int(s["y"] - s["alt"])
            if s["alt"] <= 0 or not 0 <= x < w:
                self.spores.remove(s)
                self._land_spore(s)
                continue
            if 0 <= y < h:
                i = y * w + x
                if not fg[i]:
                    overlay[i] = SPORE[1]

    def _land_spore(self, s):
        x, y = int(s["x"]), int(s["y"])
        if not (0 <= x < self.w and self.gy + 3 <= y < self.h) or not self.s.ground[y * self.w + x] or not s["g"]:
            return
        cell = self._cell(x, y)
        if self.nut[cell] < 0.25:
            return
        if (y * self.w + x) in self.myc_node:
            return
        # Sometimes crosses with whichever colony lives nearby, as compatible hyphae do.
        mate = None
        if self.colonies and self.rng.random() < 0.5:
            mate = self.rng.choice(self.colonies).g
        if len(self.colonies) < int(9 * self.cfg.density + 1):
            self._found_colony(x, y, self._mutate_fungal(s["g"], mate), s["gen"])

    def _pulse_frame(self, overlay):
        myc = self.c.myc
        comp = self.c.comp
        for pulse in list(self.pulses):
            p, ramp, left = pulse
            node = self.myc_node.get(p)
            if not node or left <= 0:
                self.pulses.remove(pulse)
                continue
            if comp[p] == myc[p]:
                overlay[p] = ramp[3]
            parent = node[1]
            if parent in self.myc_node:
                if comp[parent] == myc[parent]:
                    overlay.setdefault(parent, ramp[2])
                if self.frame_no % 2 == 0:
                    pulse[0] = parent
                    pulse[2] = left - 1
            else:
                self.pulses.remove(pulse)

    def _twinkle_frame(self, overlay):
        rng = self.rng
        stars = self.sky.stars if self.sky.stars and rng.random() < 0.4 else self.s.stars
        if stars and rng.random() < 0.06:
            x, y = rng.choice(stars)
            i = y * self.w + x
            if not self.c.fg[i] and self.c.comp[i] in range(STAR.start, STAR.start + STAR.n):
                self.twinkle[i] = rng.randint(4, 14)
        for i in list(self.twinkle):
            self.twinkle[i] -= 1
            if self.twinkle[i] <= 0:
                del self.twinkle[i]
            else:
                overlay[i] = STAR[min(2, self.c.comp[i] - STAR.start + 1)]

    def _owl_frame(self, overlay):
        if not self.owl_on or self.wild.owl_away or not self.wild.evo.awake_count("owl"):
            return
        ox, oy = self.s.owl
        i = oy * self.w + ox
        self.owl_blink -= 1
        if self.owl_blink < 0:
            self.owl_blink = self.rng.randint(self.fps * 6, self.fps * 50)
        if self.owl_blink < 2:
            return
        overlay[i] = EYE[1]
        overlay[i + 3] = EYE[1]

    def _rain_frame(self, overlay):
        rng = self.rng
        w, h = self.w, self.h
        lit = palette.LIT2
        frame = self.c.frame
        fg = self.c.fg
        wanted = 50 if self.raining else 0
        drops = self.drops
        while len(drops) < wanted:
            drops.append([rng.randrange(w), rng.randrange(h // 2), rng.randint(3, 5)])
        for d in list(drops):
            d[1] += d[2]
            if d[1] >= h - 2:
                if wanted:
                    d[0], d[1] = rng.randrange(w), 0
                else:
                    drops.remove(d)
                continue
            for k in range(d[2]):
                y = d[1] - k
                if 0 <= y < h:
                    i = y * w + d[0]
                    if i not in overlay and not fg[i]:
                        overlay[i] = lit[frame[i]]

    def _falling_frame(self, overlay):
        rng = self.rng
        autumn = self.climate.weights["autumn"]
        if self.climate.weights["winter"] > 0.6:
            autumn = 0.0
        if len(self.falling) < 3 + int(16 * autumn) and rng.random() < 0.008 + 0.14 * autumn and self.fps:
            self.falling.append([rng.randrange(self.w) * 1.0, 0.0, rng.uniform(0, 6.28), rng.randint(self.gy, self.h - 10),
                                 rng.choice(self.climate.leaf_hues()) if autumn > 0.3 else (LITTER[3],) * 3])
        w, h = self.w, self.h
        for leaf in list(self.falling):
            leaf[1] += 0.3
            leaf[2] += 0.1
            x = int(leaf[0] + math.sin(leaf[2]) * 3)
            y = int(leaf[1])
            if y >= leaf[3]:
                self.falling.remove(leaf)
                if 0 <= x < w and y < h:
                    if autumn > 0.3:
                        self.climate.lay_leaf(x, y)
                    else:
                        self._add_litter(x, y)
                continue
            if 0 <= x < w - 1 and 0 <= y < h and not self.c.fg[y * w + x]:
                # Turning as it falls: flat, then edge-on.
                overlay[y * w + x] = leaf[4][1]
                if math.sin(leaf[2] * 2) > -0.2:
                    overlay[y * w + x + 1] = leaf[4][0]

    # ---- the fast frame ----------------------------------------------------

    fps = 8

    def frame(self, dim=False):
        """One animation frame. `dim` means no-one can see: keep the clock but skip drawing."""
        self.frame_no += 1
        overlay = {}
        night = self.daylight < 0.45
        if night and self.flies:
            self._fly_frame(overlay, dim)
        if dim:
            return
        # Lens light first, so that everything moving is drawn on top of it.
        overlay = {**self.flare.frame(), **overlay}
        if self.cfg.fauna:
            self._moth_frame(overlay)
            self._beetle_frame(overlay)
            self.wild.frame(overlay)
            if self.stag:
                self._stag_frame(overlay)
            self._owl_frame(overlay)
        self._spore_frame(overlay)
        self._pulse_frame(overlay)
        if self.daylight < 0.25:
            self._twinkle_frame(overlay)
        if self.raining or self.drops:
            self._rain_frame(overlay)
        self.climate.frame(overlay)
        self.hanging.frame()
        self._falling_frame(overlay)
        self.wild.odd.frame(overlay)
        self.c.commit_overlay(overlay)

    # ---- light -------------------------------------------------------------

    def light_spans(self):
        """Rows of the frame that the slow shafts of light and mist touch."""
        spans = []
        w, h = self.w, self.h
        now = self.frame_no / max(1, self.fps)
        if self.cfg.light_shafts:
            # By day the shafts come from the sun, and swing across with it.
            sun = self.flare.sun_position()
            follow = sun[0] - self.s.moon[0] if sun else 0
            for shaft in self.shafts:
                drift = math.sin(now * shaft["rate"] * 2 * math.pi + shaft["phase"]) * 26 * self.scale_s
                half = shaft["w"] * (0.75 + 0.25 * math.sin(now * shaft["rate"] * 5 + shaft["phase"]))
                slope = shaft["slope"]
                for y in range(int(h * 0.04), int(h * 0.92)):
                    # Step in 2-pixel stairs so the edge reads as pixel art, not a smooth band.
                    cx = shaft["x"] + drift + slope * (y & ~1) + follow
                    a = int(cx - half)
                    b = int(cx + half)
                    if a < w and b > 0:
                        spans.append((y, max(0, a), min(w, b)))
        if self.cfg.weather and self.mist > 0.02:
            base = self.gy
            for y in range(base - int(14 * self.scale_s), base + int(26 * self.scale_s), 2):
                for k in range(5):
                    cx = (k * 113 + now * 0.5 + 40 * math.sin(y * 0.3 + k)) % w
                    half = 22 * self.mist * (1 + math.sin(y * 0.7 + k))
                    a, b = int(cx - half), int(cx + half)
                    if b > 0 and a < w:
                        spans.append((y, max(0, a), min(w, b)))
        # Overlapping spans would light a pixel twice in apply_light only from the base,
        # since each is translated from comp; so overlaps are harmless.
        return spans

    # ---- persistence -------------------------------------------------------

    def to_state(self):
        return {
            "tick": self.tick_no,
            "pid": self.next_pid,
            "cid": self.next_cid,
            "stats": self.stats,
            "nut": [round(v, 3) for v in self.nut],
            "wet": [round(v, 3) for v in self.wet],
            "shade": [round(v, 3) for v in self.shade],
            "wet_base": [round(v, 3) for v in self.wet_base],
            "strains": self.strains,
            "cells": base64.b64encode(zlib.compress(bytes(self.cells), 6)).decode(),
            "plants": [[p.kind, p.x, p.y, p.g, p.seed, p.shown, round(p.age), round(p.life), p.wilting, p.colony,
                        p.pollen, p.linked, p.shed, p.gen, p.pid] for p in self.plants],
            "colonies": [[c.cid, c.g, round(c.energy, 3), c.tips, c.nodes[c.head:], c.gen, c.born] for c in self.colonies],
            "threads": [[p, n[0], n[1]] for p, n in self.myc_node.items()],
            "flies": [[round(f["x"], 1), round(f["y"], 1), round(f["phase"], 3), round(f["freq"], 4), f["hue"], round(f["bright"], 3)]
                      for f in self.flies],
            "beetles": [[b["x"], b["y"], round(b["energy"], 3), b["g"]] for b in self.beetles],
            "next_rain": self.next_rain,
            "next_stag": self.next_stag,
            "raining": self.raining,
            "wild": self.wild.to_state(),
            "climate": self.climate.to_state(),
            "hanging": self.hanging.to_state(),
        }

    def from_state(self, st):
        self.tick_no = st["tick"]
        self.next_pid = st["pid"]
        self.next_cid = st["cid"]
        self.stats.update(st["stats"])
        self.nut, self.wet, self.shade, self.wet_base = st["nut"], st["wet"], st["shade"], st["wet_base"]
        self.strains = [tuple(s) if s else None for s in st["strains"]]
        cells = zlib.decompress(base64.b64decode(st["cells"]))
        self.moss_hist = {}
        self.moss_count = 0
        self.moss_active = []
        self.moss_flag = bytearray(len(self.cells))
        for c, sid in enumerate(cells):
            if sid:
                cy, cx = divmod(c, self.mc)
                self._moss_set(cx, cy, sid)
        for row in st["plants"]:
            p = Plant()
            (p.kind, p.x, p.y, p.g, p.seed, shown, p.age, p.life, p.wilting, p.colony, p.pollen, p.linked,
             p.shed, p.gen, p.pid) = row
            p.z = min(255, (p.y - self.gy) * 255 // self.span)
            p.pix = self._sprite(p.kind, p.g, p.seed, p.x, p.y)
            p.shadow = None
            p.shown = 0
            self.plants.append(p)
        for p in sorted(self.plants, key=lambda q: q.z):
            shown = next(r[5] for r in st["plants"] if r[-1] == p.pid)
            self._reveal(p, shown)
        for cid, g, energy, tips, nodes, gen, born in st["colonies"]:
            col = Colony()
            col.cid, col.g, col.energy, col.tips, col.nodes, col.gen, col.born = cid, g, energy, tips, nodes, gen, born
            col.head = col.lv1 = col.lv0 = 0
            col.alive = True
            self.colonies.append(col)
            self.colony_by_id[cid] = col
        for p, cid, parent in st["threads"]:
            col = self.colony_by_id.get(cid)
            if col:
                self.myc_node[p] = (cid, parent)
                self.c.myc[p] = MYC[col.g["hue"]][0]
                self.c.refresh(p)
        self.flies = [{"x": row[0], "y": row[1], "phase": row[2], "freq": row[3], "vx": 0.0, "vy": 0.0,
                       "hue": row[4] if len(row) > 4 else 0, "bright": row[5] if len(row) > 5 else 0.5} for row in st["flies"]]
        self.beetles = []
        for row in st["beetles"]:
            if len(row) == 4:
                self.beetles.append(self._new_beetle(row[0], row[1], row[3], row[2]))
            else:  # saved before beetles had genes
                self.beetles.append(self._new_beetle(row[0], row[1], None, row[5]))
        self._settle_on_landscape()
        self.next_rain = st["next_rain"]
        self.next_stag = st["next_stag"]
        self.raining = st["raining"]
        self.climate.load(st.get("climate"))
        self.hanging.load(st.get("hanging"))
        self.climate.seed_carpet()
        self.wild.load(st.get("wild"))
        self._sync_fauna()

    def _flora_census(self):
        flora = {}
        for kind in LIMITS:
            members = [p for p in self.plants if p.kind == kind]
            means = {}
            if members:
                for key in ("size", "bushy", "bloom", "wet", "dark", "toxin"):
                    means[key] = round(sum(p.g.get(key, 0.0) for p in members) / len(members), 2)
            spacing = max(1, len(members) // 3)
            flora[kind] = {
                "n": len(members),
                "mature": sum(1 for p in members if p.shown == len(p.pix) and not p.wilting),
                "wilting": sum(1 for p in members if p.wilting),
                "partnered": sum(1 for p in members if p.linked),
                "gen": max((p.gen for p in members), default=0),
                "means": means,
                "hues": sorted({p.g.get("hue", 0) for p in members}),
                "samples": [{"g": p.g, "seed": p.seed} for p in members[::spacing][:3]],
                "limit": int(LIMITS[kind] * self.cfg.density + 0.5),
            }
        return flora

    def census(self):
        kinds = {}
        for p in self.plants:
            kinds[p.kind] = kinds.get(p.kind, 0) + 1
        rules = sorted(((n, sid) for sid, n in self.moss_hist.items() if n > 0 and self.strains[sid]), reverse=True)[:5]
        return {
            "tick": self.tick_no,
            "plants": kinds,
            "colonies": len(self.colonies),
            "hyphae": len(self.myc_node),
            "moss_cells": self.moss_count,
            "top_moss_rules": [[rule_text(self.strains[sid][0], self.strains[sid][1]), n] for n, sid in rules],
            "beetles": len(self.beetles),
            "moths": len(self.moths),
            "frogs": len(self.wild.frogs),
            "snails": len(self.wild.snails),
            "animals": self.wild.evo.census(),
            "residents": self.wild.evo.resident_census(),
            "flora": self._flora_census(),
            "fungi": [{"hue": c.g["hue"], "threads": len(c.nodes) - c.head, "parasite": round(c.g.get("parasite", 0.0), 2),
                       "gen": c.gen, "cap": c.g["cap"]} for c in self.colonies],
            "fireflies": {"hues": [sum(1 for f in self.flies if f["hue"] == h) for h in range(len(FLY_RAMPS))],
                          "bright": round(sum(f["bright"] for f in self.flies) / max(1, len(self.flies)), 2),
                          "sync": round(math.hypot(sum(math.cos(f["phase"] * math.tau) for f in self.flies),
                                                   sum(math.sin(f["phase"] * math.tau) for f in self.flies))
                                        / max(1, len(self.flies)), 2)},
            "soil": {"nutrients": round(sum(self.nut) / len(self.nut), 2), "moisture": round(sum(self.wet) / len(self.wet), 2),
                     "shade": round(sum(self.shade) / len(self.shade), 2),
                     "moss": round(self.moss_count / max(1, int(self.mc * self.mr * 0.17 * self.cfg.density)), 2)},
            "weather": ("storm" if self.climate.storm else "snow" if self.climate.snowing else "rain" if self.raining
                        else "aurora" if self.climate.aurora else "fog" if self.climate.fog else "clear"),
            "season": self.climate.season,
            "sky": self.sky.summary(),
            "cloud_cover": round(self.clouds.cover or 0.0, 2),
            "temp": round(self.climate.temp, 1),
            "snow": round(self.climate.snow, 2),
            "ice": round(self.climate.ice, 2),
            "oddities": self.stats.get("oddities", {}),
            "rule_tones": [self.strains[sid][2] for n, sid in rules],
            "stats": self.stats,
        }
