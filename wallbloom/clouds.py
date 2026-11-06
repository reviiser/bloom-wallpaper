"""Slow clouds, seen through the gaps in the leaves.

They drift on their own plane, just above the sky: behind every tree, but in front of
the moon and the stars. A cloud is drawn as a shift along the sky's own colour ramp, so
it is lit by whatever lies behind it, and comes out brightest close to the moon.

The cloud field is one long strip of noise that wraps around, built once. Each step
only touches rows whose pattern has actually changed, so this stays very cheap.
"""

import random

from .palette import MOON, SKY, STAR
from .scene import Noise

SPEED = 0.12  # art pixels per tick: at the default pace, about half an hour to cross the screen
STEP_TICKS = 6
COVER_RATE = 0.002
MOON_LEVEL = 6


class Clouds:
    def __init__(self, eco):
        self.eco = eco
        self.rng = random.Random(eco.s.seed ^ 0xC10D)
        self.offset = self.rng.uniform(0, 1000)
        self.cover = None
        self.strip = None
        self.quantiles = None
        self.rows = None
        self.seen = {}
        self.day = None
        self.drawn_cover = -1

    # ---- building ----------------------------------------------------------

    def _build(self):
        """The cloud field, in 2-pixel blocks so that its edges stay chunky, and the sky pixels it can show on."""
        scene = self.eco.s
        w, gy = scene.w, scene.gy
        noise = Noise(random.Random(scene.seed ^ 0xC10D5))
        period = w * 2
        blocks_x, blocks_y = period // 2, gy // 2 + 1
        field = []
        for by in range(blocks_y):
            y = by * 2.0
            row = bytearray(blocks_x)
            # High clouds sit in long bands, so the field is much wider than tall.
            for bx in range(blocks_x):
                x = bx * 2.0
                # Crossfading into itself is what lets the strip wrap with no seam.
                blend = x / period
                a = noise.fbm(x / 44.0, y / 17.0) * 0.62 + noise(x / 11.0, y / 6.0) * 0.26 + noise(x / 4.5 + 31, y / 3.5) * 0.12
                b = (noise.fbm((x - period) / 44.0, y / 17.0) * 0.62 + noise((x - period) / 11.0, y / 6.0) * 0.26
                     + noise((x - period) / 4.5 + 31, y / 3.5) * 0.12)
                row[bx] = min(255, int((a * (1 - blend) + b * blend) * 255))
            field.append(bytes(v for v in row for _ in range(2)))
        self.strip = field
        ordered = sorted(v for row in field for v in row[::4])
        self.quantiles = [ordered[min(len(ordered) - 1, int(len(ordered) * p / 100))] for p in range(101)]
        base = scene.base
        sky = range(SKY.start, SKY.start + SKY.n)
        faint = range(STAR.start, STAR.start + STAR.n)
        lit = range(MOON.start, MOON.start + MOON.n)
        rows = []
        mx, my, mr = scene.moon
        for y in range(gy):
            cells = []
            for x in range(w):
                v = base[y * w + x]
                if (x - mx) ** 2 + (y - my) ** 2 <= (mr + 1) ** 2:  # the whole disc, lit part or dark
                    if v in sky or v in lit:
                        cells.append((x, MOON_LEVEL))
                elif v in sky:
                    cells.append((x, v - SKY.start))
                elif v in faint:
                    cells.append((x, 2))
                elif v in lit:
                    cells.append((x, MOON_LEVEL))
            rows.append(cells)
        self.rows = rows

    # ---- weather -----------------------------------------------------------

    def target_cover(self):
        """How much of the sky should be cloud right now, 0 to 1."""
        eco = self.eco
        climate = eco.climate
        if not eco.cfg.weather:
            return 0.0
        if climate.storm or climate.snowing or eco.raining:
            return 0.85
        if climate.aurora:
            return 0.05
        if climate.fog:
            return 0.6
        # Otherwise each day has its own sky, fair more often than not.
        day = random.Random(climate.now().toordinal() * 17 + eco.s.seed).random()
        return 0.08 + 0.5 * day * day

    # ---- moving ------------------------------------------------------------

    def tick(self):
        eco = self.eco
        target = self.target_cover()
        if self.cover is None:
            self.cover = target
        elif self.cover < target:
            self.cover = min(target, self.cover + COVER_RATE / STEP_TICKS)
        else:
            self.cover = max(target, self.cover - COVER_RATE / STEP_TICKS)
        self.offset += SPEED
        if eco.tick_no % STEP_TICKS == 0:
            self.step()

    def step(self):
        eco = self.eco
        if self.cover is None:
            self.cover = self.target_cover()
        if self.cover <= 0.001 and self.drawn_cover <= 0.001:
            return
        if self.strip is None:
            self._build()
        canvas = eco.c
        scene = eco.s
        w = scene.w
        period = w * 2
        day = eco.daylight > 0.3
        percent = int(self.cover * 100)
        limit = self.quantiles[100 - percent]
        solid = bytes(1 if v > limit or percent >= 100 else 0 for v in range(256)) if percent else bytes(256)
        if day != self.day:
            self.day = day
            self.seen.clear()
        o = int(self.offset) % period
        plane = canvas.cloud
        refresh = canvas.refresh
        seen = self.seen
        top_shade, body_shade = (3, 2) if day else (1, -2)  # by day, bright; by night, dark with a moonlit edge
        above = None
        for y, cells in enumerate(self.rows):
            band = self.strip[y // 2]
            row = (band[o:] + band[:o])[:w].translate(solid)
            if cells and (seen.get(y) != (row, above)):
                seen[y] = (row, above)
                row_start = y * w
                for x, level in cells:
                    i = row_start + x
                    if not row[x]:
                        # By day the moon has dissolved into the sky: this hides the last trace of its disc.
                        value = SKY[level] if day and level == MOON_LEVEL else 0
                    else:
                        shade = top_shade if above is not None and not above[x] else body_shade
                        value = SKY[level + shade]
                    if plane[i] != value:
                        plane[i] = value
                        refresh(i)
            above = row
        self.drawn_cover = self.cover
