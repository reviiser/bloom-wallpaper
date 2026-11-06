"""The year: seasons from your real calendar, weather, snow, ice and tracks in the snow.

Winter here is a slowing-down, never a killing. Plants go dormant under the snow,
small things hibernate, and whoever stays awake goes hungry enough that numbers
fall, but a species can't be wiped out by the cold.
"""

import datetime
import math
import random
from collections import deque

from .palette import FLOWER, LITTER, MOON, RAINBOW, SNOW, STAG, STAR
from .scene import Noise

SEASONS = ("spring", "summer", "autumn", "winter")
LITTER_HUE = (LITTER[2], LITTER[3], LITTER[4])
LEAF_RUSSET = (FLOWER[4][0], FLOWER[4][0], FLOWER[4][1])
LEAF_OCHRE = (LITTER[4], FLOWER[5][0], FLOWER[5][0])
LEAF_BRIGHT = (FLOWER[4][0], FLOWER[4][1], FLOWER[5][1])  # the odd fresh one
# Fallen leaves, smallest first: a few pixels, in several attitudes.
LEAF_SHAPES = (
    ((0, 0), (1, 0)),
    ((0, 0), (0, 1)),
    ((0, 0), (1, 0), (1, 1)),
    ((1, 0), (0, 1), (1, 1)),
    ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2)),
    ((0, 0), (1, 0), (1, 1), (2, 1)),
)
PINNED_TEMPERATURE = {"spring": 11.0, "summer": 24.0, "autumn": 9.0, "winter": -4.0}
_PEAK_DAY = {"winter": 5, "spring": 95, "summer": 186, "autumn": 278}
TRACK_STEPS = {  # shape of a print, and how far apart prints fall
    "rabbit": (((0, 0), (1, 0), (3, -1), (3, 1)), 7),
    "fox": (((0, 0), (1, 0)), 4),
    "hedgehog": (((0, 0),), 3),
    "squirrel": (((0, 0), (0, 1), (2, 0), (2, 1)), 8),
    "songbird": (((0, 0), (1, -1), (1, 1)), 5),
    "stag": (((0, 0), (1, 0), (0, 1), (1, 1)), 6),
    "ermine": (((0, 0), (2, 0)), 6),
    "mossback": (((0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (1, 1), (2, 1), (3, 1), (4, 1), (1, 2), (2, 2), (3, 2), (4, 2)), 13),
}


def season_weights(day_of_year, southern=False):
    """How much of each season today is: a smooth blend, peaking through each one's middle."""
    if southern:
        day_of_year = (day_of_year + 182) % 365
    weights = {}
    for name, peak in _PEAK_DAY.items():
        phase = math.cos((day_of_year - peak) / 365.0 * math.tau)
        weights[name] = max(0.0, phase) ** 2
    return weights


def easter(year):
    """Easter Sunday, by the anonymous Gregorian computus."""
    a, b, c = year % 19, year // 100, year % 100
    d, e = b // 4, b % 4
    g = (8 * b + 13) // 25
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    m = (32 + 2 * e + 2 * i - h - k) % 7
    n = (a + 11 * h + 22 * m) // 451
    month = (h + m - 7 * n + 114) // 31
    day = (h + m - 7 * n + 114) % 31 + 1
    return datetime.date(year, month, day)


def holidays(when):
    """Names of what today is. Used by the easter eggs."""
    date = when.date() if hasattr(when, "date") else when
    found = set()
    md = (date.month, date.day)
    if (10, 24) <= md <= (10, 31):
        found.add("halloween")
    if md in ((10, 30), (10, 31)):
        found.add("hallows_eve")
    if (12, 20) <= md <= (12, 26):
        found.add("yuletide")
    if md == (12, 24):
        found.add("christmas_eve")
    if md == (12, 31) or md == (1, 1):
        found.add("new_year")
    if md == (2, 14):
        found.add("valentine")
    if md == (3, 17):
        found.add("st_patrick")
    if md == (4, 1):
        found.add("april_fools")
    if (8, 11) <= md <= (8, 13):
        found.add("perseids")
    if md == (6, 21):
        found.add("midsummer")
    if md == (12, 21):
        found.add("midwinter")
    if date.weekday() == 4 and date.day == 13:
        found.add("friday_13th")
    if abs((date - easter(date.year)).days) <= 1:
        found.add("easter")
    return found


class Climate:
    def __init__(self, eco):
        self.eco = eco
        self.rng = eco.rng
        self.fixed_date = None  # tests can set the calendar
        self.snow = 0.0
        self.ice = 0.0
        self.snowing = 0
        self.storm = 0
        self.fog = 0
        self.aurora = 0
        self.rainbow = 0
        self._bow = None
        self._bow_cells = None
        self._bow_age = 0
        self.flash = 0
        self.flakes = []
        self.order = None
        self.shown = 0
        self.caps = []
        self.caps_shown = 0
        self.prints = {}
        self.melting_prints = False
        self.carpet = deque()
        self._drift = None
        self.temp = 10.0
        self.weights = dict.fromkeys(SEASONS, 0.25)
        self.season = "autumn"
        self.today_tags = set()
        self.refresh_calendar()

    # ---- the calendar ------------------------------------------------------

    def now(self):
        return self.fixed_date or datetime.datetime.now()

    def refresh_calendar(self):
        when = self.now()
        doy = when.timetuple().tm_yday
        pinned = self.eco.cfg.season
        southern = self.eco.cfg.hemisphere == "south"
        if pinned in SEASONS:
            self.weights = {s: 1.0 if s == pinned else 0.0 for s in SEASONS}
            base = PINNED_TEMPERATURE[pinned]
        else:
            self.weights = season_weights(doy, southern)
            d = (doy + 182) % 365 if southern else doy
            base = 11.0 - 16.0 * math.cos((d - 20) / 365.0 * math.tau)
        self.season = max(self.weights, key=self.weights.get)
        # Each day runs a little warm or cold, consistently through that day.
        day_noise = random.Random(when.toordinal() * 31 + self.eco.s.seed).uniform(-3.5, 3.5)
        self.temp = base + day_noise
        self.today_tags = holidays(when)

    # ---- who is awake ------------------------------------------------------

    @property
    def dormant(self):
        """Deep enough into winter that small things sleep and plants wait."""
        return self.temp < 2.0 or self.snow > 0.35

    @property
    def frozen(self):
        return self.ice > 0.6

    def look(self):
        """What the palette needs to know."""
        return {"autumn": self.weights["autumn"], "winter": self.weights["winter"], "spring": self.weights["spring"],
                "ice": min(1.0, self.ice * 1.4)}

    # ---- the slow tick -----------------------------------------------------

    def start_precipitation(self):
        """Rain, or snow if it's cold enough; in the warm months sometimes a thunderstorm."""
        eco, rng = self.eco, self.rng
        if self.temp < 1.5:
            self.snowing = rng.randint(700, 2400)
            eco.stats["snowfalls"] = eco.stats.get("snowfalls", 0) + 1
            return
        eco.raining = rng.randint(400, 900)
        eco.stats["rains"] += 1
        if self.temp > 12 and rng.random() < 0.25:
            self.start_storm()

    def start_storm(self):
        self.eco.raining = max(self.eco.raining, self.rng.randint(500, 1000))
        self.storm = self.eco.raining
        self.eco.wild.note("thunderstorm")

    def tick(self):
        eco, rng = self.eco, self.rng
        if eco.tick_no % 1200 == 0:
            self.refresh_calendar()
        temp = self.temp
        if self.snowing:
            self.snowing -= 1
            self.snow = min(1.0, self.snow + 0.00028)
        elif temp > 1.5 and self.snow > 0:
            self.snow = max(0.0, self.snow - 0.00002 * temp * (1 + eco.daylight))
        if temp < -0.5:
            self.ice = min(1.0, self.ice + 0.00025)
        elif temp > 2 and self.ice > 0:
            self.ice = max(0.0, self.ice - 0.00012 * min(temp, 12) / 4)
        if self.snow > 0 or self.shown or self.prints:
            self._settle()
        if self.storm:
            self.storm -= 1
            if rng.random() < 0.012:
                self.flash = rng.randint(2, 3)
                self._maybe_strike()
        elif eco.raining == 1 and eco.daylight > 0.2 and rng.random() < 0.5:
            self.rainbow = rng.randint(500, 1100)
            eco.wild.odd.sighted("rainbow")
        if self.rainbow:
            self.rainbow -= 1
            if not self.rainbow:
                self._bow = None
                self._bow_age = 0
        if self.fog:
            self.fog -= 1
        elif eco.cfg.weather and rng.random() < 0.00012 * (2.5 if self.season in ("autumn", "spring") else 0.6):
            self.fog = rng.randint(1500, 5000)
            eco.wild.note("fog")
        if self.aurora:
            self.aurora -= 1
        elif (eco.cfg.weather and self.weights["winter"] > 0.3 and eco.daylight < 0.05 and not self.snowing
              and rng.random() < (0.0005 if "midwinter" in self.today_tags else 0.00006)):
            self.aurora = rng.randint(1500, 4000)
            eco.wild.odd.sighted("aurora")
        if eco.tick_no % 600 == 300 and self.snow > 0.25:
            self.hidden_tracks()
        if eco.tick_no % 60 == 0:
            self._tend_carpet()

    def _maybe_strike(self):
        """Now and then the lightning finds a tree, and there's a log where it stood."""
        eco = self.eco
        trees = [p for p in eco.plants if p.kind == "sapling" and p.shown > 30]
        if trees and self.rng.random() < 0.02:
            tree = self.rng.choice(trees)
            tree.wilting = True
            tree.shed = 0
            tree.age = tree.life
            eco.wild.evo.say("Lightning struck a young tree in the storm.")
            eco.wild.note("tree struck by lightning")

    # ---- snow on the ground ------------------------------------------------

    def _ensure_order(self):
        """Every piece of ground in the order snow will cover it: patchy, drifting, and repeatable."""
        if self.order is not None:
            return
        scene = self.eco.s
        noise = Noise(random.Random(scene.seed ^ 0xF1A4E))
        w = scene.w
        ground = scene.ground
        keyed = []
        hash_rng = random.Random(scene.seed ^ 0x51F7)
        rnd = hash_rng.random
        for y in range(scene.gy, scene.h):
            row = y * w
            for x in range(w):
                i = row + x
                if ground[i]:
                    keyed.append((noise(x / 30.0, y / 12.0) * 0.6 + rnd() * 0.4, i))
        keyed.sort()
        self.order = [i for _, i in keyed]
        self.tone = hash_rng.randbytes(scene.w * scene.h)
        fg = self.eco.c.fg
        caps = []
        for y in range(1, scene.h):
            for x in range(w):
                i = y * w + x
                # Only on what's solid enough to hold it: thick boughs, rocks, bushes. Not vines.
                if fg[i] and not fg[i - w] and i + 2 * w < len(fg) and fg[i + w] and fg[i + 2 * w]:
                    caps.append(i)
                    if hash_rng.random() < 0.4:
                        caps.append(i + w)
        hash_rng.shuffle(caps)
        self.caps = caps

    def _flake_tone(self, i):
        """Mostly one soft shade, freckled with the lighter one."""
        return SNOW[2] if self.tone[i] < 50 else SNOW[1]

    def _settle(self):
        """Bring the visible snow in line with how much has fallen. Budgeted, so it stays cheap."""
        self._ensure_order()
        c = self.eco.c
        snow_plane, cap = c.snow, c.cap
        order = self.order
        refresh = c.refresh
        target = int(self.snow * len(order))
        budget = 160
        while self.shown < target and budget:
            i = order[self.shown]
            self.shown += 1
            budget -= 1
            if i not in self.prints:
                snow_plane[i] = self._flake_tone(i)
                refresh(i)
        while self.shown > target and budget:
            self.shown -= 1
            budget -= 1
            i = order[self.shown]
            if i in self.prints and self.snow > 0.02:
                continue  # trodden snow is packed hard, and goes last
            snow_plane[i] = 0
            refresh(i)
        # As it thaws, the old tracks stand out pale against bare ground.
        if self.snow < 0.3 and self.prints and not self.melting_prints:
            self.melting_prints = True
            for i in self.prints:
                snow_plane[i] = SNOW[2]
                refresh(i)
            if len(self.prints) > 30:
                self.eco.wild.evo.say("The thaw has uncovered a day's worth of tracks.")
        elif self.snow >= 0.3:
            self.melting_prints = False
        if self.snow <= 0.02 and self.prints:
            for _ in range(min(20, len(self.prints))):
                i = self.prints.popitem()[0]
                snow_plane[i] = 0
                refresh(i)
        # Caps on boughs, rocks and bushes, once it's lying properly.
        want = int(max(0.0, self.snow - 0.25) / 0.75 * len(self.caps))
        for _ in range(12):
            if self.caps_shown < want:
                i = self.caps[self.caps_shown]
                cap[i] = SNOW[2]
                refresh(i)
                self.caps_shown += 1
            elif self.caps_shown > want:
                self.caps_shown -= 1
                i = self.caps[self.caps_shown]
                cap[i] = 0
                refresh(i)
            else:
                break

    def footprint(self, kind, x, y, stride_key):
        """Called as something walks. Leaves prints only where there's snow to take them."""
        shape = TRACK_STEPS.get(kind)
        if self.snow < 0.2 or not shape or stride_key % shape[1]:
            return
        self.press(kind, x, y)

    def press(self, kind, x, y):
        c = self.eco.c
        w = c.w
        for dx, dy in TRACK_STEPS[kind][0]:
            px, py = x + dx, y + dy
            if 0 <= px < w and 0 <= py < c.h:
                i = py * w + px
                if c.snow[i]:
                    c.snow[i] = SNOW[0]
                    self.prints[i] = 1
                    c.refresh(i)
        while len(self.prints) > 2500:
            i = next(iter(self.prints))
            del self.prints[i]
            if c.snow[i] == SNOW[0]:
                c.snow[i] = self._flake_tone(i)
                c.refresh(i)

    def track(self, kind, x0=None, y0=None, x1=None):
        """A whole line of prints, left by something that passed while nobody was looking."""
        eco, rng = self.eco, self.rng
        if self.snow < 0.2 or kind not in TRACK_STEPS:
            return
        stride = TRACK_STEPS[kind][1]
        x = rng.randrange(eco.w) if x0 is None else x0
        y = eco.gy + int(eco.span * rng.uniform(0.1, 0.9)) if y0 is None else y0
        heading = rng.choice((-1, 1))
        drift = rng.uniform(-0.2, 0.2)
        length = rng.randint(40, 170) if x1 is None else abs(x1 - x)
        for k in range(0, length, stride):
            px, py = x + heading * k, int(y + drift * k + rng.uniform(-1, 1))
            if 0 <= px < eco.w and eco.gy + 1 < py < eco.h - 3:
                self.press(kind, px, py)

    def hidden_tracks(self):
        """Most of the forest passes unseen. In snow, you can tell who it was."""
        rng = self.rng
        pools = self.eco.wild.evo.pools
        for kind in ("rabbit", "fox", "squirrel", "songbird", "stag"):
            if pools.get(kind) and rng.random() < 0.25:
                self.track(kind)
        if rng.random() < 0.004:  # and sometimes you can't tell at all
            self.track("mossback")
            self.eco.wild.odd.sighted("big_tracks")

    # ---- a carpet of fallen leaves -----------------------------------------

    def _leaf_drift(self):
        """Where leaves collect: against trunks, rocks and the shore, and in scattered wind-made patches."""
        if self._drift is not None:
            return self._drift
        eco = self.eco
        scene = eco.s
        noise = Noise(random.Random(scene.seed ^ 0x1EAF))
        w = scene.w
        cols = w // 6 + 1
        rows = eco.span // 4 + 1
        field = []
        for row in range(rows):
            y = scene.gy + row * 4
            for col in range(cols):
                x = col * 6
                value = max(0.0, noise(x / 38.0, y / 13.0) * 1.7 - 0.72) + 0.04
                # Anything solid within a short reach makes a drift against it.
                for dx, dy in ((-9, 0), (9, 0), (-5, -3), (5, -3), (0, -5), (-14, 0), (14, 0)):
                    px, py = x + dx, y + dy
                    if 0 <= px < w and scene.gy <= py < scene.h:
                        i = py * w + px
                        if scene.fg[i] or scene.water[i]:
                            value += 0.28
                field.append(value)
        self._drift = (field, cols)
        return self._drift

    def lay_leaf(self, x, y):
        """A leaf settles: blown a little toward where others have gathered, and lying where it lands."""
        eco, rng = self.eco, self.rng
        c = eco.c
        field, cols = self._leaf_drift()
        best = None
        best_value = -1.0
        for _ in range(7):
            px = x + rng.randint(-34, 34)
            py = y + rng.randint(-12, 12)
            if not (0 <= px < c.w and eco.gy + 1 <= py < c.h):
                continue
            k = ((py - eco.gy) // 4) * cols + px // 6
            value = (field[k] if 0 <= k < len(field) else 0.0) * (0.75 + rng.random() * 0.25)
            if value > best_value:
                best, best_value = (px, py), value
        if best is None:
            return
        px, py = best
        scene = eco.s
        if best_value < 0.12 and rng.random() < 0.85:
            return  # open ground stays mostly clear: wind doesn't leave leaves there
        self._lay_bed(px, py, best_value)
        i = py * c.w + px
        if scene.water[i] and rng.random() > 0.2:
            return  # most that reach the pond sink or drift to the shore
        if not scene.ground[i] and not scene.water[i]:
            return
        # Nearer leaves are bigger, as anything is.
        depth = (py - eco.gy) / eco.span
        shapes = LEAF_SHAPES[:2] if depth < 0.25 else LEAF_SHAPES[:4] if depth < 0.6 else LEAF_SHAPES
        shape = rng.choice(shapes)
        ramp = rng.choice(self.leaf_hues())
        tone = rng.choice((0, 0, 0, 1, 1, 2))
        cells = []
        for k, (dx, dy) in enumerate(shape):
            qx, qy = px + dx, py + dy
            if 0 <= qx < c.w and 0 <= qy < c.h and (scene.ground[qy * c.w + qx] or scene.water[qy * c.w + qx]):
                q = qy * c.w + qx
                # One edge of each leaf sits darker, so it reads as a leaf and not as a dot.
                c.leaf[q] = ramp[max(0, tone - 1)] if k == len(shape) - 1 and len(shape) > 2 else ramp[tone]
                c.refresh(q)
                cells.append(q)
        if cells:
            self.carpet.append(cells)
        while len(self.carpet) > 3000:
            self._lift_leaf()

    def _lay_bed(self, x, y, thickness):
        """Under the fresh leaves, the dark mat of those already fallen."""
        eco, rng = self.eco, self.rng
        c = eco.c
        scene = eco.s
        radius = 2 + int(min(1.0, thickness) * 5)
        cells = []
        for _ in range(radius * 3):
            qx, qy = x + rng.randint(-radius, radius), y + rng.randint(-radius // 2, radius // 2)
            if 0 <= qx < c.w and 0 <= qy < c.h:
                q = qy * c.w + qx
                if scene.ground[q] and not c.leaf[q]:
                    c.leaf[q] = LITTER[rng.randint(0, 2)]
                    c.refresh(q)
                    cells.append(q)
        if cells:
            self.carpet.append(cells)

    def leaf_hues(self):
        """Muted, as leaves on the ground are: russet, ochre and brown, with a few still bright."""
        return (LEAF_RUSSET, LEAF_RUSSET, LEAF_RUSSET, LEAF_OCHRE, LEAF_OCHRE, LITTER_HUE, LITTER_HUE, LEAF_BRIGHT)

    def _lift_leaf(self):
        c = self.eco.c
        cells = self.carpet.popleft()
        for q in cells:
            c.leaf[q] = 0
            c.refresh(q)
        y, x = divmod(cells[0], c.w)
        self.eco.wild.enrich(x, y, 0.002)

    def seed_carpet(self):
        """Autumn doesn't start over when the wallpaper does: the ground is already covered to suit the date."""
        eco = self.eco
        amount = int(1600 * self.weights["autumn"]) if self.weights["autumn"] > 0.3 else 0
        for _ in range(amount):
            self.lay_leaf(self.rng.randrange(eco.w), self.rng.randrange(eco.gy, eco.h))

    def _tend_carpet(self):
        """Fallen leaves rot down over the winter, feeding the soil they lie on."""
        if self.carpet and self.weights["autumn"] < 0.5:
            for _ in range(min(16, len(self.carpet))):
                self._lift_leaf()

    # ---- the fast frame ----------------------------------------------------

    def frame(self, overlay):
        eco, rng = self.eco, self.rng
        c = eco.c
        w, h = c.w, c.h
        fg = c.fg
        # Snowflakes.
        wanted = 70 if self.snowing else 0
        flakes = self.flakes
        while len(flakes) < wanted:
            flakes.append([rng.randrange(w) * 1.0, rng.randrange(h) * 1.0, rng.uniform(0.35, 0.9), rng.uniform(0, 6.28)])
        for f in list(flakes):
            f[1] += f[2]
            f[3] += 0.09
            x, y = int(f[0] + math.sin(f[3]) * 2.2), int(f[1])
            if y >= h - 2:
                if wanted:
                    f[0], f[1] = rng.randrange(w) * 1.0, 0.0
                else:
                    flakes.remove(f)
                continue
            if 0 <= x < w:
                i = y * w + x
                if not fg[i]:
                    overlay[i] = STAG[1] if f[2] > 0.6 else SNOW[2]
        # A frost-glint on lying snow.
        if self.snow > 0.5 and eco.daylight < 0.25 and not self.snowing and self.shown and rng.random() < 0.2:
            i = self.order[rng.randrange(self.shown)]
            if not fg[i] and not c.life[i]:
                overlay[i] = STAG[3]
        if self.rainbow:
            self._rainbow_frame(overlay)

    def _build_bow(self):
        """Every pixel of the arch once, with its colour band. It only has to be worked out one time."""
        eco = self.eco
        scene = eco.s
        w = eco.w
        band_width = max(3, int(3 * eco.scale_s))
        cx = w / 2.0
        cy = eco.gy + eco.span * 0.15
        outer = cy - eco.h * 0.045
        inner = outer - band_width * 7
        grain = random.Random(scene.seed ^ 0xB0).randbytes(w * eco.gy)
        cells = []
        for y in range(eco.gy):
            dy = cy - y
            if dy > outer:
                continue
            reach = math.sqrt(outer * outer - dy * dy)
            hole = math.sqrt(inner * inner - dy * dy) if dy < inner else 0.0
            for side in (-1, 1):
                for x in range(int(hole), int(reach) + 1):
                    px = int(cx + side * x)
                    if 0 <= px < w:
                        band = int((outer - math.hypot(px - cx, dy)) / band_width)
                        if 0 <= band < 7:
                            i = y * w + px
                            cells.append((i, band, min(1.0, (eco.gy - y) / (eco.gy * 0.3) + 0.25), grain[i] / 255.0))
        self._bow_cells = cells

    def _rainbow_frame(self, overlay):
        """A whole arch, solid against open sky. Seen through rain-washed air, it lies over distant trees too, but thinner."""
        eco = self.eco
        if self._bow_cells is None:
            self._build_bow()
        if eco.frame_no % 16 == 0 or self._bow is None:
            c = eco.c
            self._bow_age += 16
            level = min(1.0, self.rainbow / 150.0, self._bow_age / (max(1, eco.fps) * 10.0))
            sky_start, sky_end = eco.s_sky
            faint = range(STAR.start, STAR.start + STAR.n)
            moon = range(MOON.start, MOON.start + MOON.n)
            fg, life, comp, cloud = c.fg, c.life, c.comp, c.cloud
            bow = {}
            for i, band, foot, grain in self._bow_cells:
                if fg[i] or life[i] or comp[i] in moon:
                    continue
                v = comp[i]
                strength = level * foot
                if cloud[i]:
                    strength *= 0.7
                elif not (sky_start <= v < sky_end or v in faint):
                    strength *= 0.5  # leaves and far trees show through it
                if grain < strength:
                    bow[i] = RAINBOW[band]
            self._bow = bow
        overlay.update(self._bow)

    # ---- persistence -------------------------------------------------------

    def to_state(self):
        return {"snow": round(self.snow, 4), "ice": round(self.ice, 4), "snowing": self.snowing, "storm": self.storm,
                "fog": self.fog, "aurora": self.aurora, "prints": list(self.prints)[-1500:]}

    def load(self, st):
        if not st:
            return
        self.snow, self.ice = st["snow"], st["ice"]
        self.snowing, self.storm, self.fog, self.aurora = st["snowing"], st["storm"], st["fog"], st["aurora"]
        if self.snow > 0:
            self._ensure_order()
            c = self.eco.c
            self.shown = int(self.snow * len(self.order))
            for i in self.order[:self.shown]:
                c.snow[i] = self._flake_tone(i)
                c.refresh(i)
            for i in st.get("prints", ()):
                if c.snow[i]:
                    self.prints[i] = 1
                    c.snow[i] = SNOW[0]
                    c.refresh(i)
            self.caps_shown = 0
