"""The light itself: a sun that crosses the sky by your clock, and bloom and lens flare from it and the moon.

Bloom is a soft veil of brightness around the light. Flare is what a lens adds: a row
of faint discs on a line from the light through the middle of the screen, so they swing
across as the sun moves. All of it is seen through the camera, so it lies over the trees.
It fades as leaves and clouds get in the way, which makes it flicker as the light passes
behind a gap. With a full palette there is no true blending, so "brighter" means a step up
each colour's own ramp, dithered for softness.

It is worked out once every second or two and only reused between, so it stays cheap.
"""

import math
import random

from . import palette
from .palette import EYE, MOON, SKY, STAG, STAR

RECOMPUTE_FRAMES = 12
# Distance along the axis (0 is the light, 1 the screen's centre), radius, and how solid.
GHOSTS = ((0.48, 3, 0.75), (0.82, 7, 0.34), (1.24, 2, 0.9), (1.55, 11, 0.24), (1.95, 5, 0.42), (2.35, 15, 0.14))


class LensFlare:
    def __init__(self, eco):
        self.eco = eco
        self.noise = None
        self.cached = {}
        self.sun = None
        self.sun_x = None
        self.visible = 0.0
        self.tint = (STAR[0], STAR[1])

    # ---- where the light is ------------------------------------------------

    def sun_position(self):
        """Across the sky from morning to evening, arcing over the top. None when it's down."""
        eco = self.eco
        if eco.daylight < 0.03:
            return None
        mode = eco.cfg.time_of_day
        if mode == "day":
            t = 0.5
        elif mode == "dusk":
            t = 0.93
        else:
            now = eco.climate.now()
            t = (now.hour + now.minute / 60.0 - 6.0) / 14.0
            t = max(0.0, min(1.0, t))
        if eco.cfg.hemisphere == "south":
            t = 1.0 - t
        x = eco.w * (0.04 + 0.92 * t)
        y = eco.gy * (0.92 - 0.72 * math.sin(math.pi * t))
        return int(x), int(y)

    def _sun_pixels(self, overlay, x, y):
        """The disc, only where sky shows through. Returns how much of it could be seen, 0 to 1."""
        eco = self.eco
        c = eco.c
        w = eco.w
        radius = max(5, int(8 * eco.scale_s))
        sky = range(SKY.start, SKY.start + SKY.n)
        seen = total = 0
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                d = dx * dx + dy * dy
                if d > radius * radius:
                    continue
                total += 1
                px, py = x + dx, y + dy
                if not (0 <= px < w and 0 <= py < eco.gy):
                    continue
                i = py * w + px
                if c.fg[i] or c.life[i] or c.comp[i] not in sky:
                    continue
                if c.cloud[i]:
                    seen += 0.15  # a cloud lets a little through
                    continue
                seen += 1
                overlay[i] = STAG[3] if d < radius * radius * 0.4 else EYE[2]
        return seen / max(1, total)

    def _moon_visible(self):
        eco = self.eco
        c = eco.c
        w = eco.w
        mx, my, mr = eco.s.moon
        lit = range(MOON.start, MOON.start + MOON.n)
        seen = total = 0
        for dy in range(-mr, mr + 1, 2):
            for dx in range(-mr, mr + 1, 2):
                if dx * dx + dy * dy <= mr * mr:
                    i = (my + dy) * w + mx + dx
                    # Only the lit part counts: a crescent isn't "blocked" by its own dark side.
                    if 0 <= i < w * eco.h and c.base[i] in lit:
                        total += 1
                        if c.comp[i] in lit:
                            seen += 1
        return seen / max(1, total)

    # ---- the veil of light -------------------------------------------------

    def _build(self):
        eco = self.eco
        strength = eco.cfg.flare
        overlay = {}
        self.sun = None
        self.sun_x = None
        if strength <= 0.01 or eco.climate.snowing or eco.raining:
            # The sun is still up there, with or without the lens effects.
            sun = self.sun_position()
            if sun:
                self.sun_x = sun[0]
                self._sun_pixels(overlay, *sun)
            self.visible = 0.0
            return overlay
        if self.noise is None:
            self.noise = random.Random(eco.s.seed ^ 0xF1A2E).randbytes(eco.w * eco.h)
        sun = self.sun_position()
        if sun:
            self.sun = sun
            self.sun_x = sun[0]
            seen = self._sun_pixels(overlay, *sun)
            source = sun
            power = min(1.0, seen * 1.6) * (0.55 + 0.45 * min(1.0, eco.daylight / 0.5))
            reach = 46
        else:
            seen = self._moon_visible()
            source = eco.s.moon[:2]
            power = seen * 0.9
            reach = 34
        self.visible = seen
        power *= strength
        # The light's own colour: warm gold from the sun, cool from the moon.
        self.tint = (EYE[0], EYE[1]) if sun else (STAR[0], STAR[1])
        if power > 0.02:
            self._bloom(overlay, source, power, reach)
            self._ghosts(overlay, source, power)
        return overlay

    def _bloom(self, overlay, source, power, reach):
        """Brightest close in, thinning out with distance, and a little wider than tall, as glare is."""
        eco = self.eco
        frame = eco.c.frame
        w, h = eco.w, eco.h
        noise = self.noise
        lit, lit2 = palette.LIT, palette.LIT2
        reach = int(reach * eco.scale_s * (0.6 + 0.6 * power))
        sx, sy = source
        for dy in range(-reach, reach + 1):
            py = sy + dy
            if not 0 <= py < h:
                continue
            for dx in range(-reach * 3 // 2, reach * 3 // 2 + 1):
                px = sx + dx
                if not 0 <= px < w:
                    continue
                d = math.hypot(dx / 1.5, dy) / reach
                if d >= 1:
                    continue
                i = py * w + px
                if i in overlay:
                    continue
                falloff = (1 - d) ** 2 * power
                roll = noise[i] / 255.0
                if roll < falloff * falloff * 0.5:
                    overlay[i] = self.tint[1] if d < 0.3 else self.tint[0]
                elif roll < falloff * 1.3:
                    overlay[i] = lit2[frame[i]]
                elif roll < falloff * 3.0:
                    overlay[i] = lit[frame[i]]
        # A thin streak through the light, the mark of a lens.
        for dx in range(-int(reach * 2.2), int(reach * 2.2) + 1):
            px = sx + dx
            if 0 <= px < w and 0 <= sy < h:
                i = sy * w + px
                falloff = (1 - abs(dx) / (reach * 2.2)) * power
                if i not in overlay and noise[i] / 255.0 < falloff:
                    overlay[i] = self.tint[0] if abs(dx) < reach else lit2[frame[i]]

    def _ghosts(self, overlay, source, power):
        eco = self.eco
        frame = eco.c.frame
        w, h = eco.w, eco.h
        noise = self.noise
        lit, lit2 = palette.LIT, palette.LIT2
        sx, sy = source
        cx, cy = w / 2.0, h / 2.0
        scale = eco.scale_s
        for along, radius, solidity in GHOSTS:
            gx = sx + (cx - sx) * along
            gy = sy + (cy - sy) * along
            r = max(1.5, radius * scale)
            amount = solidity * power * 2.4
            ir = int(r) + 1
            for dy in range(-ir, ir + 1):
                py = int(gy) + dy
                if not 0 <= py < h:
                    continue
                for dx in range(-ir, ir + 1):
                    px = int(gx) + dx
                    if not 0 <= px < w:
                        continue
                    d = math.hypot(dx, dy) / r
                    if d > 1:
                        continue
                    i = py * w + px
                    if i in overlay:
                        continue
                    roll = noise[i] / 255.0
                    # Each disc is faint inside, with a slightly firmer rim.
                    if d > 0.78:
                        if roll < amount * 0.55:
                            overlay[i] = self.tint[0]
                        elif roll < amount * 2.0:
                            overlay[i] = lit2[frame[i]]
                    elif roll < amount:
                        overlay[i] = lit2[frame[i]] if roll < amount * 0.25 else lit[frame[i]]

    # ---- each frame --------------------------------------------------------

    def frame(self):
        eco = self.eco
        if eco.frame_no % RECOMPUTE_FRAMES == 0:
            self.cached = self._build()
        return self.cached
