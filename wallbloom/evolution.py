"""Heredity and populations: why the forest can surprise even its author.

Every animal is an individual with genes. Most of a population lives out of sight
in the wider forest, and is simulated every few minutes with the same rules that
govern the ones you can see: it eats what the clearing really offers, is hunted by
predators that really exist, and only the survivors breed. Nothing here says what
should happen. There are no scripted outcomes, only costs and benefits:

  coat       predators spot you by contrast with the actual ground, moss included
  glow       mates love it; so do owls
  ornament   antlers, crests, tufts: more mates, more upkeep
  size       harder to take and eats more, but needs more
  speed      escapes and catches, at a metabolic price
  wary       notices danger sooner, feeds less
  active     night to day: shift hours and you dodge different hunters and food
  diet       which food or prey it goes for
  resist     survives disease, breeds less
  tolerance  eats toxic plants, costs upkeep

Plants answer with toxins, fungi may turn on the beetles, crowds breed disease, and
a rare large mutation can turn up anything. Mates are chosen by similarity, so one
population can split in two. The journal only reports what it observes.
"""

import math
import time

from . import palette
from .palette import CAP, COAT_GREEN, FOX, FROG, HARE, LITTER, STAG, STONE, TEAL, TRUNK, VIOLET, FLECK

GENE_NAMES = ("size", "coat", "speed", "wary", "active", "diet", "resist", "tolerance", "glow", "ornament")
STEP_TICKS = 600  # one generation-scale step: five minutes at the default pace
OFFLINE_STEPS = 288  # catch up on at most a day of absence
HIBERNATORS = {"hedgehog", "bats", "snail", "beetle", "moth"}

# dark, mid, light. Neighbours are similar, so small mutations make small changes.
COATS = [
    (TRUNK[1], TRUNK[3], TRUNK[5]), (LITTER[1], LITTER[3], LITTER[4]), (FOX[0], FOX[1], FOX[2]), (FOX[1], FOX[2], FOX[3]),
    (CAP[5][0], CAP[5][1], CAP[5][2]), (HARE[0], HARE[1], HARE[2]), (HARE[1], HARE[2], HARE[3]),
    (STAG[0], STAG[1], STAG[2]), (STAG[1], STAG[2], STAG[3]), (STONE[2], STONE[3], STONE[5]),
    (VIOLET[2], VIOLET[4], FLECK[0]), (TEAL[2], TEAL[4], TEAL[6]), (COAT_GREEN[0], COAT_GREEN[1], COAT_GREEN[2]),
    (COAT_GREEN[0], COAT_GREEN[3], COAT_GREEN[4]), (FROG[1], FROG[2], FROG[3]),
]
COAT_NAMES = ["charcoal", "dark brown", "dark russet", "russet", "tan", "grey", "silver", "pale", "white", "slate",
              "indigo", "teal", "moss-green", "olive", "green"]
# "^" is the lit top edge of a body. The light tone serves, unless it's so far from the coat that it reads as a stripe.
_COAT_MAPS = [{"#": mid, "+": dark, "w": light, "o": light, "O": dark,
               "^": light if palette.luminance(light) - palette.luminance(mid) < 45 else mid} for dark, mid, light in COATS]
_COAT_LUM = [palette.luminance(mid) for _, mid, _ in COATS]


def clamp(v, low=0.0, high=1.0):
    return low if v < low else high if v > high else v


def coat_index(g):
    return int(clamp(g["coat"]) * (len(COATS) - 1) + 0.5)


def coat_colors(g):
    return _COAT_MAPS[coat_index(g)]


def coat_dark(g):
    return COATS[coat_index(g)][0]


def awake_position(daylight):
    """Where the present moment sits on the night-to-day scale of the `active` gene."""
    return min(1.0, daylight / 0.7)


def overlap(a, b):
    """How much two creatures' waking hours coincide."""
    return 1.0 - abs(a - b) * 0.85


def distance(a, b):
    return sum(abs(a[k] - b[k]) for k in ("coat", "active", "size", "diet", "ornament")) / 5.0


def founder_genes(rng, base, spread=0.05):
    g = {"size": 0.4, "coat": 0.4, "speed": 0.5, "wary": 0.5, "active": 0.5, "diet": 0.5, "resist": 0.1,
         "tolerance": 0.1, "glow": 0.03, "ornament": 0.1}
    g.update(base)
    return {k: clamp(v + rng.gauss(0, spread)) for k, v in g.items()}


def child_genes(rng, a, b):
    g = {}
    for key in GENE_NAMES:
        g[key] = clamp((a if rng.random() < 0.5 else b).get(key, 0.3) + rng.gauss(0, 0.028))
    if rng.random() < 0.015:  # rarely, something large happens
        g[rng.choice(("coat", "glow", "ornament", "size", "active", "diet"))] = rng.random()
    return g


SPECIES = {
    "rabbit": dict(start=12, cap=34, life=70, mature=6, clutch=3, metab=0.10, breed=0.45,
                   base=dict(size=0.35, coat=0.36, speed=0.6, wary=0.5, active=0.15, diet=0.4)),
    "fox": dict(start=3, cap=7, life=320, mature=40, clutch=2, metab=0.045, breed=0.07,
                base=dict(size=0.5, coat=0.21, speed=0.6, wary=0.3, active=0.2, diet=0.2)),
    "owl": dict(start=2, cap=5, life=500, mature=60, clutch=2, metab=0.03, breed=0.05,
                base=dict(size=0.5, coat=0.07, speed=0.6, active=0.05, diet=0.5)),
    "bats": dict(start=8, cap=18, life=200, mature=20, clutch=1, metab=0.05, breed=0.12,
                 base=dict(size=0.3, coat=0.0, speed=0.7, active=0.12)),
    "hedgehog": dict(start=4, cap=10, life=150, mature=20, clutch=2, metab=0.035, breed=0.12,
                     base=dict(size=0.4, coat=0.36, speed=0.25, wary=0.3, active=0.1, diet=0.3)),
    "songbird": dict(start=8, cap=22, life=110, mature=12, clutch=3, metab=0.08, breed=0.25,
                     base=dict(size=0.3, coat=0.64, speed=0.7, active=0.88, diet=0.3)),
    "squirrel": dict(start=5, cap=12, life=160, mature=18, clutch=2, metab=0.06, breed=0.15,
                     base=dict(size=0.35, coat=0.21, speed=0.75, wary=0.6, active=0.85)),
    "stag": dict(start=3, cap=5, life=800, mature=80, clutch=1, metab=0.02, breed=0.04,
                 base=dict(size=0.5, coat=0.55, speed=0.5, wary=0.6, active=0.2, ornament=0.5, glow=0.1)),
}
RESIDENT_BASE = {
    "beetle": dict(size=0.3, coat=0.68, speed=0.5, active=0.5, resist=0.15),
    "moth": dict(size=0.35, coat=0.5, speed=0.6, active=0.25),
    "frog": dict(size=0.35, coat=0.97, speed=0.6, active=0.3),
    "snail": dict(size=0.3, coat=0.1, speed=0.1, active=0.4),
}
# Who hunts whom. Each hunter picks by its own diet gene: low favours the left of the list.
PREY = {
    "fox": ("rabbit", "squirrel", "hedgehog", "frog"),
    "owl": ("beetle", "snail", "frog", "bats", "squirrel", "songbird"),
    "bats": ("moth",),
    "hedgehog": ("beetle", "snail"),
    "songbird": ("beetle", "moth"),
}
PREY_VALUE = {"rabbit": 1.4, "squirrel": 1.0, "hedgehog": 1.2, "frog": 0.8, "beetle": 0.35, "snail": 0.4, "moth": 0.3,
              "bats": 0.5, "songbird": 0.6}
TRAIT_WORDS = {"size": ("smaller", "larger"), "speed": ("slower", "faster"), "wary": ("bolder", "warier"),
               "glow": ("duller", "more luminous"), "ornament": ("plainer", "more ornamented"),
               "resist": ("more disease-prone", "more disease-resistant"),
               "tolerance": ("fussier about food", "more tolerant of toxic plants")}
PLURAL = {"rabbit": "rabbits", "fox": "foxes", "owl": "owls", "bats": "bats", "hedgehog": "hedgehogs",
          "songbird": "songbirds", "squirrel": "squirrels", "stag": "stags"}


def mean_genes(group):
    if not group:
        return {}
    return {k: sum(i["g"].get(k, 0.0) for i in group) / len(group) for k in GENE_NAMES}


class Evolution:
    def __init__(self, eco):
        self.eco = eco
        self.rng = eco.rng
        self.pools = {}
        self.plagues = {}
        self.journal = []
        self.memory = {}
        self.steps = 0
        self.tick_left = STEP_TICKS
        self.memory_env = None
        for name, sp in SPECIES.items():
            self.pools[name] = [self.new_individual(name, age=self.rng.randint(0, sp["life"] // 2))
                                for _ in range(sp["start"])]
        self.memory["ref"] = {n: mean_genes(p) for n, p in self.pools.items()}

    # ---- individuals -------------------------------------------------------

    def new_individual(self, name, g=None, age=0, spread=0.05):
        return {"g": g or founder_genes(self.rng, SPECIES[name]["base"], spread), "age": age, "e": 1.0}

    def resident_genes(self, kind):
        return founder_genes(self.rng, RESIDENT_BASE[kind])

    def child_of(self, a, b):
        return child_genes(self.rng, a, b)

    def group(self, name):
        """The list holding a species, visible or not."""
        if name in self.pools:
            return self.pools[name]
        wild = self.eco.wild
        return {"beetle": self.eco.beetles, "moth": self.eco.moths, "frog": wild.frogs, "snail": wild.snails}[name]

    def awake(self, g, daylight=None):
        d = awake_position(self.eco.daylight if daylight is None else daylight)
        return abs(g["active"] - d) < 0.5

    def pick(self, name, count=1, force=False):
        """Individuals who would be out and about right now."""
        pool = self.pools[name]
        if self.asleep(name) and not force:
            return []
        if force and not pool:
            self.wander_in(name, 2)
            pool = self.pools[name]
        ready = [i for i in pool if self.awake(i["g"])] or (pool if force else [])
        self.rng.shuffle(ready)
        return ready[:count]

    def asleep(self, name):
        """Hibernating. Sleepers don't age, starve or get hunted, and wake when it warms."""
        climate = self.eco.climate
        if name == "frog":
            return climate.frozen
        return name in HIBERNATORS and climate.dormant

    def awake_count(self, name):
        if self.asleep(name):
            return 0
        return sum(1 for i in self.pools[name] if self.awake(i["g"]))

    def winter_floor(self, name):
        """How many of a species winter can't take. Hunters and hunger alike stop here until the thaw."""
        if not self.eco.climate.dormant or name not in SPECIES:
            return 0
        return max(2, SPECIES[name]["start"] // 2)

    def kill(self, name, ind):
        pool = self.pools.get(name)
        if pool is not None and ind in pool and len(pool) > self.winter_floor(name):
            pool.remove(ind)

    def wander_in(self, name, n=1):
        """Strangers from the wider forest, carrying whatever they carry."""
        for _ in range(n):
            self.pools[name].append(self.new_individual(name, age=SPECIES[name]["mature"] + 1, spread=0.2))

    # ---- what the clearing offers ------------------------------------------

    def survey(self):
        eco = self.eco
        rng = self.rng
        sample = eco.c.comp
        lums = []
        for _ in range(48):
            x, y = eco._random_ground()
            lums.append(palette.luminance(sample[y * eco.w + x]))
        bins = [0.0] * 5  # forage by toxicity
        fern = fruit = trees = logs = flowers = 0.0
        for p in eco.plants:
            if p.kind in ("clover", "fern", "spire"):
                bins[min(4, int(p.g.get("toxin", 0.05) * 5))] += p.shown
            if p.kind == "fern":
                fern += p.shown
            elif p.kind == "shrub":
                fruit += p.shown
            elif p.kind == "sapling":
                trees += p.shown
            elif p.kind == "log":
                logs += 1
            if p.kind == "spire":
                flowers += 1
        return {"ground": sum(lums) / len(lums), "forage": bins, "fern": fern, "fruit": fruit, "trees": trees, "logs": logs,
                "flowers": flowers, "rng": rng}

    def env(self):
        return self.memory_env or self.survey()

    def detect(self, g, env):
        contrast = min(1.0, abs(_COAT_LUM[coat_index(g)] - env["ground"]) / 85.0)
        return clamp(0.2 + 0.8 * contrast + 0.7 * g["glow"]) * (0.7 + 0.6 * g["size"])

    def hunt_chance(self, hunter, prey, env):
        escape = 0.5 * prey["speed"] + 0.5 * prey["wary"]
        return (0.55 * self.detect(prey, env) * (1 - 0.75 * escape) * (0.4 + 0.8 * hunter["speed"])
                * overlap(hunter["active"], prey["active"]))

    # ---- one step ----------------------------------------------------------

    def tick(self):
        self.tick_left -= 1
        if self.tick_left <= 0:
            self.tick_left = STEP_TICKS
            self.step()

    def step(self, offline=False):
        self.steps += 1
        env = self.memory_env = self.survey()
        dormant = self.eco.climate.dormant
        if self.memory.get("was_dormant") and not dormant:
            self._spring()
        self.memory["was_dormant"] = dormant
        self._disease()
        self._hunt(env, offline)
        self._feed(env)
        if not offline:
            self._graze()
        self._age_and_breed(env)
        self._migrate()
        self._record()
        if not offline:
            self._residents()
            self._watch(env)

    def _spring(self):
        """The thaw. Whoever made it through the winter gets a real chance at the year ahead."""
        eco = self.eco
        for name, pool in self.pools.items():
            sp = SPECIES[name]
            for ind in pool:
                ind["e"] = max(ind["e"], 1.3)
                ind["age"] = min(ind["age"], int(sp["life"] * 0.4))  # survivors of a winter aren't about to drop dead
            if len(pool) < 2:
                self.wander_in(name, 2 - len(pool))
        for group in (eco.beetles, eco.moths, eco.wild.frogs):
            for member in group:
                for key in ("e", "energy"):
                    if key in member:
                        member[key] = max(member[key], 1.0)
        if len(eco.moths) < 3:
            for _ in range(4):
                eco.moths.append(eco._new_moth())
        if len(eco.beetles) < 3:
            for _ in range(4):
                eco.beetles.append(eco._new_beetle(*eco._random_ground()))
        self.say("Spring. Everything that waited is moving again.")

    def _record(self):
        """A rolling ten hours of head counts, for the graphs in the menu."""
        history = self.memory.setdefault("history", {})
        for name in tuple(SPECIES) + tuple(RESIDENT_BASE):
            row = history.setdefault(name, [])
            row.append(len(self.group(name)))
            del row[:-120]

    def catch_up(self, seconds, ticks_per_second):
        """The computer was off. The animals weren't."""
        steps = min(OFFLINE_STEPS, int(seconds * ticks_per_second / STEP_TICKS))
        if steps < 1:
            return
        before = {n: len(p) for n, p in self.pools.items()}
        for _ in range(steps):
            self.step(offline=True)
        changes = [f"{PLURAL[n]} {before[n]} to {len(p)}" for n, p in self.pools.items() if abs(len(p) - before[n]) >= 3]
        hours = seconds / 3600.0
        when = f"{hours:.0f} hours" if hours >= 2 else f"{seconds / 60:.0f} minutes"
        self.say(f"Away for {when}. Life went on" + (": " + ", ".join(changes) + "." if changes else "."))
        self._watch(self.survey())

    def _disease(self):
        rng = self.rng
        for name, pool in self.pools.items():
            sp = SPECIES[name]
            plague = self.plagues.get(name)
            if not plague:
                if self.eco.climate.dormant:
                    continue
                if sp["cap"] >= 12 and len(pool) >= sp["cap"] * 0.8 and rng.random() < 0.006 and self._again(f"plague-{name}", 150):
                    self.plagues[name] = {"v": rng.uniform(0.3, 1.0), "left": rng.randint(8, 24), "dead": 0}
                    self.say(f"Sickness is spreading among the crowded {PLURAL[name]}.")
                continue
            for ind in list(pool):
                if rng.random() < plague["v"] * 0.16 * (1 - ind["g"]["resist"]) ** 2:
                    pool.remove(ind)
                    plague["dead"] += 1
                    self.eco.wild.enrich(*self.eco._random_ground(), 0.04)
            plague["left"] -= 1
            if plague["left"] <= 0 or not pool:
                self.say(f"The sickness among the {PLURAL[name]} has passed. It took {plague['dead']}, and {len(pool)} remain.")
                del self.plagues[name]

    def _hunt(self, env, offline):
        rng = self.rng
        for name, menu in PREY.items():
            if self.asleep(name):
                continue
            for hunter in self.pools[name]:
                if hunter["e"] > 2.0:
                    continue
                g = hunter["g"]
                for _ in range(3 if name != "songbird" else (1 if g["diet"] > 0.5 else 0)):
                    # Diet gene slides along the menu, with some slop.
                    k = int(clamp(g["diet"] + rng.gauss(0, 0.22)) * (len(menu) - 0.001))
                    kind = menu[k]
                    if self.asleep(kind) or len(self.group(kind)) <= self.winter_floor(kind):
                        continue
                    if kind in self.pools:
                        group = self.pools[kind]
                    elif offline:
                        hunter["e"] += 0.05  # residents don't change while nobody is running the clearing
                        continue
                    else:
                        group = self.group(kind)
                    if not group:
                        continue
                    prey = rng.choice(group)
                    prey_g = prey.get("g")
                    if prey_g is None or rng.random() > self.hunt_chance(g, prey_g, env):
                        continue
                    if kind == "frog" and prey.get("swim"):
                        continue
                    group.remove(prey)
                    hunter["e"] += PREY_VALUE[kind]
                    self.eco.wild.enrich(*self.eco._random_ground(), 0.03)
                    self.eco.wild.note(f"{kind} taken by {name}")
                    break

    def _feed(self, env):
        # Lean times: winter takes most of the food, so numbers fall until spring.
        lean = 0.35 if self.eco.climate.dormant else 1.0
        n_rabbit = max(1, len(self.pools["rabbit"]))
        for ind in self.pools["rabbit"]:
            g = ind["g"]
            supply = sum(amount * clamp(1 - max(0.0, (k + 0.5) / 5 - g["tolerance"]) * 1.6) for k, amount in enumerate(env["forage"]))
            ind["e"] += lean * min(0.45, 0.00068 * supply / (n_rabbit + 3)) * (0.8 + 0.4 * g["size"]) * (1 - 0.25 * g["wary"]) + 0.015
        n = max(1, len(self.pools["songbird"]))
        insects = len(self.eco.beetles) + len(self.eco.moths)
        for ind in self.pools["songbird"]:
            d = ind["g"]["diet"]
            ind["e"] += lean * min(0.4, (0.0012 * (env["fruit"] + 8 * env["flowers"]) * (1 - d) + 0.02 * insects * d) / (n + 2)) + 0.02
        n = max(1, len(self.pools["squirrel"]))
        for ind in self.pools["squirrel"]:
            ind["e"] += lean * min(0.4, (0.0009 * env["trees"] + 0.0005 * env["fruit"] + 0.2 * env["logs"]) / (n + 1)) + 0.03
        n = max(1, len(self.pools["stag"]))
        for ind in self.pools["stag"]:
            ind["e"] += lean * min(0.2, 0.00002 * (env["fern"] + env["fruit"] + env["trees"]) / n) + 0.008
        for name, pool in self.pools.items():
            sp = SPECIES[name]
            if self.asleep(name):
                continue
            for ind in pool:
                g = ind["g"]
                upkeep = sp["metab"] * (0.65 + 0.7 * g["size"]) * (1 + 0.3 * g["speed"] + 0.25 * g["tolerance"] + 0.25 * g["ornament"])
                ind["e"] = min(3.0, ind["e"] - upkeep + (0.012 if name in ("fox", "owl", "bats", "hedgehog") else 0.0))

    def _graze(self):
        """Everything those unseen rabbits ate came from these plants."""
        eco, rng = self.eco, self.rng
        n = len(self.pools["rabbit"])
        bites = min(14, n // 2) // (4 if self.eco.climate.dormant else 1)
        plants = [p for p in eco.plants if p.kind in ("clover", "fern", "spire") and p.shown > 6]
        tolerance = mean_genes(self.pools["rabbit"]).get("tolerance", 0.0)
        for _ in range(bites):
            if not plants:
                return
            # A grazer tries a few plants and takes the one that tastes safest.
            p = min(rng.sample(plants, min(4, len(plants))), key=lambda q: q.g.get("toxin", 0.05) + rng.random() * 0.06)
            if rng.random() < 1 - max(0.0, p.g.get("toxin", 0.05) - tolerance) * 2.0:
                eco._unshow(p, max(3, int(p.shown * 0.4)))
                eco._restamp(p)
                if p.shown <= 3 and p.kind != "fern":
                    p.wilting = True  # grazed to the ground
        eaters = len(self.pools["songbird"]) // 5
        fruiting = [p for p in plants if p.kind == "spire"]
        for _ in range(eaters):
            if fruiting and rng.random() < 0.5:
                p = rng.choice(fruiting)
                eco._unshow(p, max(3, int(p.shown * 0.9)))
                eco._restamp(p)
        if self.pools["squirrel"] and rng.random() < 0.02 * len(self.pools["squirrel"]):
            eco.wild.bury(*eco._random_ground())

    def _age_and_breed(self, env):
        rng = self.rng
        dormant = self.eco.climate.dormant
        for name, pool in self.pools.items():
            sp = SPECIES[name]
            if self.asleep(name):
                continue
            # Winter thins a population but can't end it: the last few always find enough.
            floor = self.winter_floor(name)
            for ind in list(pool):
                ind["age"] += 1
                if ind["e"] <= 0 or ind["age"] > sp["life"] * (0.6 + 0.8 * ind["g"]["resist"] * 0.5 + rng.random() * 0.6):
                    if len(pool) <= floor:
                        ind["e"] = max(ind["e"], 0.2)
                        continue
                    pool.remove(ind)
                    self.eco.wild.enrich(*self.eco._random_ground(), 0.04)
            if dormant:
                continue  # nobody raises young in the snow
            babies = []
            adults = [i for i in pool if i["age"] >= sp["mature"] and i["e"] > 1.15]
            for ind in adults:
                if len(pool) + len(babies) >= sp["cap"]:
                    break
                g = ind["g"]
                display = 1 + 0.5 * g["ornament"] + 0.6 * g["glow"]
                if rng.random() > sp["breed"] * display * (1 - 0.5 * g["resist"]):
                    continue
                # Mates are chosen by likeness, which is how one kind becomes two.
                candidates = rng.sample(adults, min(4, len(adults)))
                candidates = [c for c in candidates if c is not ind]
                if not candidates:
                    continue
                mate = min(candidates, key=lambda c: distance(c["g"], g))
                if distance(mate["g"], g) > 0.4:
                    continue
                for _ in range(rng.randint(1, sp["clutch"])):
                    babies.append(self.new_individual(name, child_genes(rng, g, mate["g"])))
                ind["e"] -= 0.45
                mate["e"] -= 0.2
            pool.extend(babies)

    def _migrate(self):
        rng = self.rng
        for name, pool in self.pools.items():
            if not pool and rng.random() < 0.008:
                self.wander_in(name, 2)
                self.say(f"A pair of {PLURAL[name]} has wandered in from the wider forest, after none were left.")
            elif pool and rng.random() < 0.003:
                self.wander_in(name)

    def _residents(self):
        """Colonisers for the residents, when their local lines die out."""
        eco, rng = self.eco, self.rng
        if not eco.cfg.fauna or eco.climate.dormant:
            return
        if not eco.beetles and rng.random() < 0.1:
            eco.beetles.append(eco._new_beetle(*eco._random_ground()))
            if self._again("beetle-back", 150):
                self.say("A beetle has found its way back into the clearing, after none were left.")
        if not eco.moths and rng.random() < 0.03:
            eco.moths.append(eco._new_moth())
            eco.moths.append(eco._new_moth())
            if self._again("moth-back", 150):
                self.say("Moths have returned to the clearing, after none were left.")
        # Nectar: whoever is flying feeds, and the well-fed breed.
        flowers = sum(1 for p in eco.plants if p.kind in ("spire", "clover") and p.shown == len(p.pix))
        nectar = min(0.14, flowers * 0.004)
        for m in list(eco.moths):
            m["e"] = min(2.5, m["e"] + nectar)
            if m["e"] > 1.6 and len(eco.moths) < int(14 * eco.cfg.density):
                m["e"] -= 0.8
                mate = min(rng.sample(eco.moths, min(3, len(eco.moths))), key=lambda o: abs(o["g"]["active"] - m["g"]["active"]))
                eco.moths.append(eco._new_moth(m["x"], m["y"], self.child_of(m["g"], mate["g"]), 0.8))

    # ---- the journal: reports only -----------------------------------------

    def say(self, text):
        self.journal.append([int(time.time()), self.eco.tick_no, text])
        del self.journal[:-400]

    def _once(self, key):
        seen = self.memory.setdefault("seen", {})
        if key in seen:
            return False
        seen[key] = self.steps
        return True

    def _again(self, key, cooldown):
        """True if this hasn't been said within `cooldown` steps."""
        seen = self.memory.setdefault("seen", {})
        if self.steps - seen.get(key, -cooldown) < cooldown:
            return False
        seen[key] = self.steps
        return True

    def _watch(self, env):
        refs = self.memory.setdefault("ref", {})
        highs = self.memory.setdefault("high", {})
        for name, pool in self.pools.items():
            n = len(pool)
            cap = SPECIES[name]["cap"]
            word = PLURAL[name]
            high = max(highs.get(name, 0), n)
            if n == 0 and self.memory.setdefault("gone", {}).get(name) is None:
                self.memory["gone"][name] = self.steps
                self.say(f"The last of the {word} is gone.")
            elif n and self.memory.setdefault("gone", {}).get(name) is not None:
                del self.memory["gone"][name]
            if n >= cap * 0.85 and cap >= 7 and self._again(f"boom-{name}", 300):
                self.say(f"{word.capitalize()} are everywhere: {n} of them.")
            if high >= 6 and 0 < n <= high / 3 and self._again(f"crash-{name}", 60):
                self.say(f"{word.capitalize()} have crashed, from {high} to {n}.")
                high = n
            highs[name] = max(int(high * 0.99), n)
            if not pool:
                continue
            means = mean_genes(pool)
            ref = refs.setdefault(name, means)
            self._note_drift(name, word, means, ref)
            self._note_oddities(name, word, pool)
            self._note_split(name, word, pool)
        for kind, group in (("beetle", self.eco.beetles), ("moth", self.eco.moths), ("frog", self.eco.wild.frogs)):
            members = [m for m in group if m.get("g")]
            if len(members) >= 3:
                means = mean_genes(members)
                ref = refs.setdefault(kind, means)
                self._note_drift(kind, kind + "s", means, ref)
        self._note_plants_and_fungi()
        self._note_fireflies()

    def _note_drift(self, name, word, means, ref):
        old, new = int(ref["coat"] * 14 + 0.5), int(means["coat"] * 14 + 0.5)
        if abs(means["coat"] - ref["coat"]) > 0.14 and old != new:
            self.say(f"The {word} are turning {COAT_NAMES[new]}, where they used to be {COAT_NAMES[old]}.")
            ref["coat"] = means["coat"]
        if ref["active"] < 0.5 <= means["active"]:
            self.say(f"The {word} are shifting to daytime.")
            ref["active"] = means["active"]
        elif ref["active"] >= 0.5 > means["active"]:
            self.say(f"The {word} are shifting to the night.")
            ref["active"] = means["active"]
        for gene, (down, up) in TRAIT_WORDS.items():
            if abs(means[gene] - ref[gene]) > 0.2:
                self.say(f"The {word} have grown {up if means[gene] > ref[gene] else down}.")
                ref[gene] = means[gene]
        if abs(means["diet"] - ref["diet"]) > 0.25 and name in PREY:
            menu = PREY[name]
            self.say(f"The {word} are hunting differently now, mostly after {menu[int(means['diet'] * (len(menu) - 0.001))]}s.")
            ref["diet"] = means["diet"]

    def _note_oddities(self, name, word, pool):
        one = {"fox": "fox", "bats": "bat"}.get(name, word[:-1])
        for ind in pool:
            g = ind["g"]
            if g["glow"] > 0.7 and self._again(f"glow-{name}", 500):
                self.say(f"A {one} that glows has been born.")
            if g["ornament"] > 0.8 and self._again(f"orn-{name}", 500):
                extra = {"rabbit": "A rabbit with antlers", "fox": "A fox with a great plume of a tail",
                         "stag": "A stag with enormous antlers"}.get(name, f"A strikingly ornamented {one}")
                self.say(f"{extra} has been born.")

    def _note_split(self, name, word, pool):
        """Has one population become two? Look for a gap between the two most different groups."""
        if len(pool) < 6:
            return
        a = max(pool, key=lambda i: i["g"]["coat"] + i["g"]["active"])
        b = max(pool, key=lambda i: distance(i["g"], a["g"]))
        ga, gb = [], []
        for i in pool:
            (ga if distance(i["g"], a["g"]) < distance(i["g"], b["g"]) else gb).append(i)
        split = min(len(ga), len(gb)) >= 3 and distance(mean_genes(ga), mean_genes(gb)) > 0.2
        was = self.memory.setdefault("split", {}).get(name, False)
        if split and not was:
            ca, cb = mean_genes(ga), mean_genes(gb)
            self.say(f"There now seem to be two kinds of {word}: {self.describe(ca)}, and {self.describe(cb)}.")
        elif was and not split and self._again(f"unsplit-{name}", 30):
            self.say(f"The two kinds of {word} are one kind again.")
        self.memory["split"][name] = split

    def _note_plants_and_fungi(self):
        eco = self.eco
        toxic = [p.g.get("toxin", 0.0) for p in eco.plants if p.kind in ("clover", "fern", "spire")]
        if toxic:
            mean = sum(toxic) / len(toxic)
            mark = int(mean * 5)
            if mark > self.memory.get("toxin_mark", 0):
                self.memory["toxin_mark"] = mark
                self.say(f"The meadow is growing more poisonous to eat, about {int(mean * 100)}% now. Red-flecked leaves are spreading.")
        worst = max((c.g.get("parasite", 0.0) for c in eco.colonies), default=0.0)
        if worst > 0.3 and self._once("parasite"):
            self.say("One of the fungal colonies has begun to infect beetles.")

    def _note_fireflies(self):
        flies = self.eco.flies
        if len(flies) < 6:
            return
        c = sum(math.cos(f["phase"] * math.tau) for f in flies) / len(flies)
        s = sum(math.sin(f["phase"] * math.tau) for f in flies) / len(flies)
        if math.hypot(c, s) > 0.8 and self._again("sync", 300):
            self.say("The fireflies are flashing as one.")
        hues = {}
        for f in flies:
            hues[f.get("hue", 0)] = hues.get(f.get("hue", 0), 0) + 1
        top = max(hues, key=hues.get)
        if hues[top] > len(flies) * 0.6 and top != self.memory.get("fly_hue", 0):
            self.memory["fly_hue"] = top
            self.say(f"Most fireflies now flash {('green', 'teal', 'amber', 'ice blue', 'rose')[top]}.")

    # ---- describing --------------------------------------------------------

    def describe(self, m):
        parts = [COAT_NAMES[int(clamp(m["coat"]) * 14 + 0.5)]]
        if m["size"] > 0.62:
            parts.append("large")
        elif m["size"] < 0.2:
            parts.append("small")
        if m["glow"] > 0.5:
            parts.append("glowing")
        if m["ornament"] > 0.6:
            parts.append("ornamented")
        parts.append("day-active" if m["active"] > 0.5 else "nocturnal")
        return ", ".join(parts)

    def _entry(self, name, members):
        """What the menu needs to draw and describe a species: its average, and a few real individuals."""
        members = [m for m in members if m.get("g")]
        means = mean_genes(members)
        return {
            "n": len(members),
            "looks": self.describe(means) if members else "none left",
            "means": {k: round(v, 2) for k, v in means.items()},
            # Every individual by name, so the menu can show each one's own traits.
            "members": [{"g": {k: round(v, 2) for k, v in m["g"].items()},
                         "age": m.get("age"), "e": round(m.get("e", m.get("energy", 0.0)), 2)} for m in members[:40]],
            "hist": self.memory.get("history", {}).get(name, [])[-60:],
            "split": self.memory.get("split", {}).get(name, False),
            "asleep": self.asleep(name),
        }

    def census(self):
        return {name: self._entry(name, pool) for name, pool in self.pools.items()}

    def resident_census(self):
        return {name: self._entry(name, self.group(name)) for name in RESIDENT_BASE}

    # ---- persistence -------------------------------------------------------

    def to_state(self):
        return {"pools": self.pools, "plagues": self.plagues, "journal": self.journal, "memory": self.memory,
                "steps": self.steps, "left": self.tick_left}

    def load(self, st):
        if not st:
            self.say("Animals in this forest now carry genes of their own.")
            return
        self.pools.update(st["pools"])
        self.plagues = st["plagues"]
        self.journal = st["journal"]
        self.memory = st["memory"]
        self.steps = st["steps"]
        self.tick_left = st["left"]
