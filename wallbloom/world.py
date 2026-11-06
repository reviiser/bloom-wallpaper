"""Ties scene, canvas and ecosystem together, independent of any window."""

import datetime
import gzip
import json
import math
import os
import random
import shutil
import time

from . import palette, scene
from .canvas import Canvas
from .config import STATE_DIR, STATE_PATH, archive_path
from .ecosystem import Eco


def sky_state(mode, when=None):
    """(daylight, warmth) from the clock or a pinned time of day."""
    if mode == "night":
        return 0.0, 0.0
    if mode == "dusk":
        return 0.22, 0.75
    if mode == "day":
        return 0.7, 0.05
    now = when or datetime.datetime.now()
    h = now.hour + now.minute / 60.0
    if h < 5.5 or h >= 20.5:
        return 0.0, 0.0
    if h < 7.5:  # dawn
        t = (h - 5.5) / 2.0
        return 0.7 * t * t, math.sin(t * math.pi) * 0.8
    if h < 18.0:
        return 0.7, 0.05
    t = (20.5 - h) / 2.5  # dusk
    return 0.7 * t * t, math.sin(t * math.pi) * 0.9


class World:
    def __init__(self, config, width, height, seed=None, load=True):
        self.cfg = config
        self.w = width
        self.h = height
        state = self._read_state() if load else None
        source = STATE_PATH
        carried_seed = None
        if state and config.seed and state.get("seed") != config.seed:
            # Switching forests: put this one aside, and pick up the requested one if it exists.
            try:
                shutil.copyfile(STATE_PATH, archive_path(state.get("seed")))
            except OSError as e:
                print(f"bloom: could not keep the previous forest: {e}")
            source = archive_path(config.seed)
            state = self._read_state(source)
        if state and (state.get("w") != width or state.get("h") != height):
            # A different pixel size is a different canvas. Keep this forest for when you change back.
            keep = archive_path(f"{state.get('seed')}-{state.get('w')}x{state.get('h')}")
            try:
                shutil.copyfile(source, keep)
            except OSError as e:
                print(f"bloom: could not keep the previous forest: {e}")
            carried_seed = config.seed or state.get("seed")  # same landscape, whatever the pixel size
            state = self._read_state(archive_path(f"{carried_seed}-{width}x{height}"))
        self.seed = seed or (state["seed"] if state else (config.seed or carried_seed or random.randrange(1, 1 << 30)))
        self.scene = scene.build(width, height, self.seed)
        self.canvas = Canvas(width, height)
        self.canvas.base[:] = self.scene.base
        self.canvas.fg[:] = self.scene.fg
        self.canvas.flatten()
        self.eco = Eco(self.canvas, self.scene, config, self.seed)
        self.eco.fps = config.fps
        self.eco.climate.refresh_calendar()
        self._apply_look(force=True)
        restored = False
        if state:
            try:
                self.eco.from_state(state["eco"])
                restored = True
            except (KeyError, ValueError, TypeError, IndexError) as e:
                print(f"bloom: could not restore saved world, starting fresh: {e}")
                self.eco = Eco(self.canvas, self.scene, config, self.seed)
                self.canvas.life[:] = bytes(len(self.canvas.life))
                self.canvas.life_z[:] = bytes(len(self.canvas.life))
                self.canvas.moss[:] = bytes(len(self.canvas.moss))
                self.canvas.myc[:] = bytes(len(self.canvas.myc))
                self.canvas.flatten()
        if not restored:
            self.eco.genesis()
        elif state.get("saved_at"):
            self.eco.wild.evo.catch_up(time.time() - state["saved_at"], 2.0 * config.speed)
        self.eco.sky.update()
        self.eco.clouds.step()
        self.update_light(force=True)
        self.phase_day = datetime.date.today()

    # ---- look --------------------------------------------------------------

    def _colors(self, flash=0.0, aurora=0.0, aurora_phase=0.0):
        look = self.eco.climate.look() if hasattr(self, "eco") else {}
        return palette.compute(self.cfg.theme, self.cfg.darkness, self.cfg.glow, self.cfg.saturation,
                               self.daylight, self.warmth, getattr(self, "water_phase", 0), flash=flash, aurora=aurora,
                               aurora_phase=aurora_phase, natural=self.cfg.natural, **look)

    def _apply_look(self, force=False):
        daylight, warmth = sky_state(self.cfg.time_of_day)
        look = self.eco.climate.look() if hasattr(self, "eco") else {}
        key = (self.cfg.theme, self.cfg.darkness, self.cfg.glow, self.cfg.saturation, self.cfg.natural, round(daylight, 2), round(warmth, 2),
               tuple(round(v, 2) for v in look.values()))
        if not force and key == getattr(self, "_look_key", None):
            return False
        self._look_key = key
        self.daylight = daylight
        self.warmth = warmth
        self.eco_daylight(daylight)
        self.canvas.set_palette(self._colors())
        return True

    def eco_daylight(self, daylight):
        if hasattr(self, "eco"):
            self.eco.daylight = daylight

    def set_config(self, config):
        self.cfg = config
        self.eco.cfg = config
        self.eco.fps = config.fps
        self.eco.climate.refresh_calendar()
        self.eco._sync_fauna()
        self._apply_look(force=True)
        self.eco.sky.update()
        self.eco.clouds.step()
        self.update_light(force=True)

    def minute_tick(self):
        """Cheap slow housekeeping: time of day, tonight's moon, mist."""
        self._apply_look()
        self.eco.sky.update()
        today = datetime.date.today()
        if today != self.phase_day:
            self.phase_day = today
            self._restamp_moon()
        eco = self.eco
        # Mist gathers around dawn and after rain.
        target = 0.0
        if self.cfg.weather and (eco.raining or eco.climate.fog or 0.02 < self.daylight < 0.35):
            target = 1.3 if eco.climate.fog else 0.8
        eco.mist += (target - eco.mist) * 0.15

    def _restamp_moon(self):
        canvas = self.canvas

        def write(i, v):
            canvas.base[i] = v
            canvas.refresh(i)

        scene.stamp_moon(self.scene, scene.moon_phase(), write)

    def _push(self, colors, ramp):
        """Change a run of palette entries only, leaving the rest alone."""
        canvas = self.canvas
        for i in range(ramp.start, ramp.start + ramp.n):
            r, g, b = colors[i]
            canvas.pal4[i] = bytes((b, g, r, 255))
        lr, lg, lb = bytearray(canvas.lut_r), bytearray(canvas.lut_g), bytearray(canvas.lut_b)
        for i in range(ramp.start, ramp.start + ramp.n):
            lr[i], lg[i], lb[i] = colors[i]
        canvas.lut_r, canvas.lut_g, canvas.lut_b = bytes(lr), bytes(lg), bytes(lb)

    def cycle_water(self):
        """Rotate the pond's colours: all its shimmer, for almost no work. Frozen water holds still."""
        if self.eco.climate.frozen:
            return
        self.water_phase = (getattr(self, "water_phase", 0) + 1) % palette.WATER.n
        self._push(self._colors(), palette.WATER)
        self.canvas.repaint_rect(*self.scene.pond_rect)

    def frame_effects(self, frame_no):
        """Weather that lives in the palette: lightning for a frame or two, the aurora's slow curtains."""
        climate = self.eco.climate
        if climate.flash:
            climate.flash -= 1
            strength = 0.5 if climate.flash else 0.22
            self.canvas.set_palette(self._colors(flash=strength))
            self._flashing = True
        elif getattr(self, "_flashing", False):
            self._flashing = False
            self.canvas.set_palette(self._colors())
        if climate.aurora and frame_no % 16 == 0 and self.daylight < 0.1:
            level = min(1.0, min(climate.aurora, 600) / 600.0) * 0.8
            self._push(self._colors(aurora=level, aurora_phase=frame_no * 0.012), palette.SKY)
            self.canvas.repaint_rect(0, 0, self.w, self.scene.gy + 2)
            self._aurora_on = True
        elif getattr(self, "_aurora_on", False) and not climate.aurora:
            self._aurora_on = False
            self.canvas.set_palette(self._colors())

    def update_light(self, force=False):
        spans = self.eco.light_spans()
        if force or spans != self.canvas.spans:
            self.canvas.apply_light(spans)
            return True
        return False

    # ---- persistence -------------------------------------------------------

    def _read_state(self, path=STATE_PATH):
        try:
            with gzip.open(path, "rt") as f:
                return json.load(f)
        except (OSError, ValueError, EOFError):
            return None

    def save(self):
        data = {"w": self.w, "h": self.h, "seed": self.seed, "eco": self.eco.to_state(),
                "census": self.eco.census(), "pixel_size": self.cfg.pixel_size, "saved_at": time.time()}
        os.makedirs(STATE_DIR, exist_ok=True)
        tmp = STATE_PATH + ".tmp"
        with gzip.open(tmp, "wt", compresslevel=5) as f:
            json.dump(data, f, separators=(",", ":"))
        os.replace(tmp, STATE_PATH)
