"""The menu: settings, a bestiary, a herbarium, a field map and the forest's journal.

Everything is drawn by hand on a 320x240 indexed screen, in the wallpaper's own
palette and over your actual forest. Animals and plants are drawn from their real
genes, using the same sprite code the wallpaper uses.
"""

import base64
import datetime
import math
import random
import time
import zlib

import gi

gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
import cairo  # noqa: E402
from gi.repository import Gdk, GLib, Gtk  # noqa: E402

from . import config as config_mod  # noqa: E402
from . import icon, report, scene as scene_mod  # noqa: E402
from .cryptids import CATALOGUE, CATALOGUE_SPRITES, EGG  # noqa: E402
from .ecosystem import COMPANIONS, FLY_RAMPS, LIFESPAN, SEASONAL_PLANTS  # noqa: E402
from .evolution import COAT_NAMES, COATS, PREY, awake_position, coat_colors  # noqa: E402
from .palette import (RAINBOW, CANOPY, CAP, EYE, FIREFLY, FLECK, FLOWER, HARE, LEAF, LITTER, MOON, MOSS, MYC, OLIVE, SKY, STAG,  # noqa: E402
                      TEAL, TRUNK, VIOLET, WATER)
from .pixelui import GLYPH_H, Screen, text_width, ui_palette, wrap  # noqa: E402
from .sprites import BUILDERS, animal_rows  # noqa: E402
from .world import sky_state  # noqa: E402

# The window asks the desktop for its icon by this name, matching the menu entry.
GLib.set_prgname("bloom-wallpaper-settings")
Gdk.set_program_class("bloom-wallpaper-settings")

W, H = 320, 240
BODY_Y = 42
BODY_H = 186
TABS = ("SETTINGS", "ANIMALS", "PLANTS", "FOREST", "ODDITIES", "JOURNAL")
INK = MOON[4]
DIM = FLECK[2]
FAINT = FLECK[1]
GOOD = FIREFLY[3]
WARM = EYE[1]
BAD = FLOWER[4][1]
AWAKE_MARK = FLOWER[1][2]  # periwinkle: stands clear of the yellow day strip in every palette

# key, label, low, high, step, hint
SLIDERS = (
    ("darkness", "DARKNESS", 0.0, 1.0, 0.05, "How deep the night is. Glowing things fade less."),
    ("glow", "GLOW", 0.0, 1.0, 0.05, "Fireflies, mushrooms, mycelium sparks, the moon."),
    ("saturation", "COLOUR", 0.0, 1.5, 0.05, "From one palette colour up to vivid."),
    ("natural", "NATURAL", 0.0, 1.0, 0.05, "Each thing its own colour, not one tint over all."),
    ("flare", "LENS FLARE", 0.0, 1.0, 0.05, "Bloom and flare from the sun and moon."),
    ("crt", "CRT FILTER", 0.0, 1.0, 0.05, "Beam smear, phosphor dots and old glass. 0 is off."),
    ("speed", "PACE OF LIFE", 0.25, 4.0, 0.25, "How fast time passes in the forest."),
    ("density", "ABUNDANCE", 0.3, 1.5, 0.05, "How much life the clearing can carry."),
    ("fireflies", "FIREFLIES", 0, 80, 2, "How many fireflies at night."),
    ("fps", "FRAME RATE", 4, 15, 1, "Animation frames a second. Lower saves battery."),
    ("pixel_size", "PIXEL SIZE", 2, 8, 1, "Chunkier is cheaper. Each size grows its own forest."),
)
COMBOS = (
    ("theme", "PALETTE", config_mod.THEMES, "The colours of your forest, and of this menu."),
    ("time_of_day", "TIME OF DAY", config_mod.TIMES, "Auto follows your clock. Or stay in one hour."),
    ("season", "SEASON", config_mod.SEASON_MODES, "Auto follows your calendar. Or stay in one season."),
)
SWITCHES = (
    ("fauna", "ANIMALS", "All animals, and visitors like the wisp."),
    ("light_shafts", "LIGHT SHAFTS", "Slow beams of moonlight or sun."),
    ("weather", "WEATHER", "Clouds, rain, snowfall, storms and mist."),
    ("oddities", "ODDITIES", "Cryptids and date-based surprises."),
    ("constellations", "CONSTELLATIONS", "Real star positions, through the gaps in the leaves."),
    ("battery_saver", "BATTERY SAVER", "Half the frame rate on battery."),
)
ROWS = ([("slider",) + s for s in SLIDERS] + [("combo",) + c for c in COMBOS] + [("switch",) + s for s in SWITCHES]
        + [("seed", "seed", "FOREST SEED", "Type a number and press ENTER. Your old forest is kept."),
           ("auto", "auto", "START AT LOGIN", "Wake the forest whenever you log in.")])

ANIMAL_ORDER = ("rabbit", "fox", "owl", "bats", "hedgehog", "songbird", "squirrel", "stag", "frog", "beetle", "moth", "snail")
ANIMAL_POSE = {"rabbit": ("sit", "hop"), "fox": ("walk0", "walk1"), "owl": ("fly0", "fly1"), "bats": ("fly0", "fly1"),
               "hedgehog": ("walk0", "walk1"), "songbird": ("sit", "fly0"), "squirrel": ("run", "dig"),
               "stag": ("walk0", "walk1"), "frog": ("sit", "hop"), "beetle": ("walk0", "walk0"),
               "moth": ("fly0", "fly0"), "snail": ("crawl", "crawl")}
ANIMAL_TITLE = {"bats": "BAT"}
ANIMAL_NOTE = {
    "rabbit": "Grazes the meadow, carries seed, bolts from foxes.",
    "fox": "Stalks slowly, then rushes. Follows the rabbits.",
    "owl": "Watches from the bough. Takes what shows up best.",
    "bats": "Hawks moths on the wing, turning on a whim.",
    "hedgehog": "Snuffles after beetles and snails.",
    "songbird": "Eats fruit and flowers, sows the seeds elsewhere.",
    "squirrel": "Buries nuts. The forgotten ones become trees.",
    "stag": "Rare. Walks round the water and spreads seed.",
    "frog": "Lives on the bank. Catches moths by tongue.",
    "beetle": "Grazes moss or fungus. Prey to many, host to some.",
    "moth": "Pollinator. Day-fliers are butterflies to you.",
    "snail": "Comes out after rain. Eats mushrooms.",
}
ANIMAL_EATS = {"rabbit": "clover, ferns, spires", "squirrel": "nuts, fruit", "stag": "ferns, shrubs, trees",
               "frog": "moths, fireflies", "beetle": "moss, fungus", "moth": "nectar", "snail": "mushrooms, moss"}
EATEN_BY = {"frog": ["fox", "owl"], "moth": ["bat", "songbird", "frog"]}
for _hunter, _menu in PREY.items():
    for _kind in _menu:
        _word = "bat" if _hunter == "bats" else _hunter
        if _word not in EATEN_BY.setdefault(_kind, []):
            EATEN_BY[_kind].append(_word)
TRAITS = (("size", "SIZE"), ("speed", "SPEED"), ("wary", "WARY"), ("glow", "GLOW"), ("ornament", "ORNATE"), ("resist", "RESIST"))

PLANT_ORDER = ("clover", "spire", "fern", "shrub", "sapling", "mushroom", "log", "crocus", "foxglove", "goldenrod", "pumpkin", "snowdrop")
PLANT_NOTE = {
    "clover": "Fixes nitrogen. First to arrive on poor soil.",
    "spire": "Glowing flat blooms. Feeds moths and birds.",
    "fern": "Shades the ground so moss can move in.",
    "shrub": "Star-leaved, fruiting. Songbirds carry its seed.",
    "sapling": "Slow and hungry. Grows best with a fungal partner.",
    "mushroom": "The fruit of a hidden colony. Its shape is that colony's genes.",
    "log": "What a dead tree leaves. Feeds and shades its neighbours for hours.",
    "crocus": "Spring's first flower, up through the last snow.",
    "foxglove": "Midsummer's tall spire of hanging bells.",
    "goldenrod": "Autumn's last gold, loved by late moths.",
    "pumpkin": "Autumn, on a sprawling vine. Watch them in late October.",
    "snowdrop": "Winter's small defiance. Blooms in the snow.",
}
PLANT_TITLE = {"sapling": "YOUNG TREE", "log": "NURSE LOG", "spire": "CUP SPIRE"}
PLANT_TRAITS = (("size", "SIZE"), ("bushy", "BUSHY"), ("bloom", "BLOOM"), ("wet", "WET"), ("dark", "SHADE"), ("toxin", "TOXIN"))
ICON_WORDS = (("rabbit", "rabbit"), ("fox", "fox"), ("owl", "owl"), ("bat", "bats"), ("hedgehog", "hedgehog"), ("songbird", "songbird"),
              ("squirrel", "squirrel"), ("stag", "stag"), ("frog", "frog"), ("beetle", "beetle"), ("moth", "moth"), ("snail", "snail"))
SEASON_COLOR = {"spring": FLOWER[3][2], "summer": FIREFLY[3], "autumn": FLOWER[5][2], "winter": STAG[2]}
SILHOUETTE = {"#": CANOPY[1], "+": CANOPY[1], "w": CANOPY[2], "o": CANOPY[2], "O": CANOPY[1], "^": CANOPY[2]}


def default_genes():
    return {"size": 0.4, "coat": 0.4, "speed": 0.5, "wary": 0.5, "active": 0.5, "diet": 0.5, "resist": 0.1, "tolerance": 0.1,
            "glow": 0.0, "ornament": 0.1}


def creature_colors(name, g):
    if name == "moth" and g.get("active", 0) > 0.55:
        wing = FLOWER[int(g["coat"] * 7.99)]
        return {"#": wing[1], "+": HARE[0], "w": wing[2], "^": wing[2]}
    return coat_colors(g)


class Menu(Gtk.Window):
    def __init__(self, autostart_get, autostart_set):
        super().__init__(title="Bloom")
        self.set_icon_name(icon.APP_ID)
        self.banner_icon = icon.icon_grid(24)
        self.autostart_get, self.autostart_set = autostart_get, autostart_set
        self.cfg = config_mod.load()
        self.values = self.cfg.as_dict()
        self.pending = 0
        self.tab = 0
        self.row = 0
        self.animal = 0
        self.odd_index = 0
        self.chosen = {}
        self.chosen_at = {}
        self.plant = 0
        self.scroll = 0
        self.journal_rows = []
        self.max_scroll = 0
        self.data = None
        self.running = True
        self.seed_text = None
        self.frame = 0
        self.hits = []
        self.mouse = (-1, -1)
        self.drag = None
        self.scale = 3
        self.ox = self.oy = 0
        self.sprite_cache = {}
        self._strip_key = None
        self._strip = []
        self.map_cache = None
        self.loaded_scene = None
        self.bg = bytes(W * H)
        self.scene = None
        self.motes = [[random.uniform(0, W), random.uniform(2, 26), random.random(), random.uniform(0.02, 0.05)] for _ in range(9)]

        self.screen = Screen(W, H)
        self.screen.set_palette(ui_palette(self.values["theme"], self.values["saturation"], self.values["natural"]))
        self.surface = cairo.ImageSurface.create_for_data(self.screen.buf, cairo.FORMAT_RGB24, W, H, W * 4)

        display = Gdk.Display.get_default()
        monitor = display.get_primary_monitor() or display.get_monitor(0)
        height = monitor.get_geometry().height if monitor else 1080
        fit = max(1, (height - 90) // H)
        self.set_default_size(W * min(3, fit), H * min(3, fit))
        self.area = Gtk.DrawingArea()
        self.area.set_can_focus(True)
        self.area.add_events(Gdk.EventMask.BUTTON_PRESS_MASK | Gdk.EventMask.BUTTON_RELEASE_MASK | Gdk.EventMask.POINTER_MOTION_MASK
                             | Gdk.EventMask.SCROLL_MASK | Gdk.EventMask.SMOOTH_SCROLL_MASK | Gdk.EventMask.KEY_PRESS_MASK)
        self.add(self.area)
        self.area.connect("draw", self.on_draw)
        self.area.connect("button-press-event", self.on_press)
        self.area.connect("button-release-event", self.on_release)
        self.area.connect("motion-notify-event", self.on_motion)
        self.area.connect("scroll-event", self.on_scroll)
        self.connect("key-press-event", self.on_key)
        self.connect("destroy", Gtk.main_quit)
        self.show_all()
        self.area.grab_focus()

        self._load_scene()
        self._refresh()
        GLib.timeout_add(170, self.on_timer)
        GLib.timeout_add_seconds(30, self.on_refresh_timer)

    # ---- data --------------------------------------------------------------

    def on_refresh_timer(self):
        if self.tab > 0 and self.is_active():
            self._refresh()
        return True

    def _refresh(self):
        """Ask the wallpaper for a fresh save, and read it once it has had a moment to write."""
        GLib.timeout_add(400 if report.request_save() else 0, self._load)

    def _load(self):
        self.data = report.read_world()
        self.running = bool(report.running_pid())
        self.map_cache = None
        self._load_scene()
        self.journal_rows = self._layout_journal()
        self.sprite_cache.clear()
        return False

    def _load_scene(self):
        data = self.data or {}
        key = (data.get("w", 480), data.get("h", 270), data.get("seed") or config_mod.current_seed(self.cfg) or 1)
        if key == self.loaded_scene:
            return
        self.loaded_scene = key
        sc = scene_mod.build(*key)
        self.scene = sc
        sw, sh = sc.w, sc.h
        ox = max(0, min(sw - W, sc.moon[0] - W // 2))
        bg = bytearray(W * H)
        for y in range(H):
            sy = min(sh - 1, y * sh // H)
            o = sy * sw + ox
            row = bytes(f or b for f, b in zip(sc.fg[o:o + W], sc.base[o:o + W]))
            bg[y * W:y * W + len(row)] = row
        self.bg = bytes(bg)

    def census(self):
        return (self.data or {}).get("census", {})

    # ---- frame -------------------------------------------------------------

    def on_timer(self):
        self.frame += 1
        self.redraw()
        return True

    def redraw(self):
        self.hits = []
        s = self.screen
        s.set_clip()
        s.px[:] = self.bg
        self._banner()
        self._tabs()
        (self._draw_settings, self._draw_animals, self._draw_plants, self._draw_forest, self._draw_oddities, self._draw_journal)[self.tab]()
        s.set_clip()
        s.flush()
        self.area.queue_draw()

    def hit(self, x, y, w, h, fn):
        self.hits.append((x, y, w, h, fn))

    def over(self, x, y, w, h):
        mx, my = self.mouse
        return x <= mx < x + w and y <= my < y + h

    def _banner(self):
        s = self.screen
        for mote in self.motes:  # a few fireflies of its own, up in the leaves
            mote[0] = (mote[0] + mote[3] * 8) % W
            mote[2] = (mote[2] + mote[3]) % 1.0
            if mote[2] < 0.35:
                s.pixel(int(mote[0]), int(mote[1]), FIREFLY[4 if mote[2] < 0.15 else 3])
        s.text(8, 6, "BLOOM", FIREFLY[3], shadow=CANOPY[0], scale=3)
        for row, cells in enumerate(self.banner_icon):
            for col, c in enumerate(cells):
                if c:
                    s.pixel(100 + col, 4 + row, c)
        census = self.census()
        if census:
            right = f"FOREST {(self.data or {}).get('seed', '?')}"
            s.text(W - text_width(right) - 6, 6, right, DIM, shadow=CANOPY[0])
            age = f"{census.get('tick', 0) / 7200:.1f} HOURS OLD"
            s.text(W - text_width(age) - 6, 16, age, INK, shadow=CANOPY[0])
            season = census.get("season")
            if season:
                s.text(128, 16, season.upper(), SEASON_COLOR.get(season, DIM), shadow=CANOPY[0])
        if not self.running:
            s.text(128, 6, "ASLEEP", WARM, shadow=CANOPY[0])

    def _tabs(self):
        s = self.screen
        x = 2
        for k, name in enumerate(TABS):
            w = 50
            current = k == self.tab
            s.rect(x, 31, w, 12, CANOPY[0])
            s.rect(x + 1, 32, w - 2, 11, SKY[1] if current else (SKY[2] if self.over(x, 31, w, 12) else CANOPY[2]))
            s.rect(x + 1, 31, w - 2, 1, FLECK[1] if current else FLECK[0])
            s.text(x + (w - text_width(name)) // 2 + 1, 34, name, GOOD if current else DIM)
            self.hit(x, 31, w, 12, lambda *_, k=k: self.set_tab(k))
            x += w + 3

    def set_tab(self, k):
        if k != self.tab:
            self.tab = k
            self.scroll = 0
            if k > 0:
                self._refresh()

    def _footer(self, text):
        s = self.screen
        s.rect(0, H - 11, W, 11, CANOPY[0])
        s.text(6, H - 9, text[:52], DIM)

    # ---- settings ----------------------------------------------------------

    def _draw_settings(self):
        s = self.screen
        s.panel(4, BODY_Y, W - 8, BODY_H)
        y = BODY_Y + 5
        step = 8
        hint = ""
        for k, row in enumerate(ROWS):
            selected = k == self.row
            if selected:
                s.rect(8, y - 1, W - 16, step, SKY[3])
                if self.frame % 6 < 4:
                    s.text(9, y, ">", GOOD)
            self.hit(8, y - 1, W - 16, step, lambda mx, my, k=k: self.choose_row(k, mx, my))
            kind = row[0]
            s.text(18, y, row[2], INK if selected else DIM)
            if kind == "slider":
                _, key, _, low, high, _, hint_text = row
                value = self.values[key]
                s.meter(116, y + 1, 30, (value - low) / (high - low), FIREFLY[3] if selected else TEAL[5], SKY[0])
                shown = str(value) if isinstance(value, int) else f"{value:.2f}"
                s.text(W - 12 - text_width(shown), y, shown, INK)
            elif kind == "combo":
                _, key, _, _, hint_text = row
                text = str(self.values[key]).upper()
                s.text(116, y, "<", GOOD if selected else FAINT)
                s.text(126, y, text, INK)
                s.text(126 + text_width(text) + 4, y, ">", GOOD if selected else FAINT)
            elif kind == "switch":
                _, key, _, hint_text = row
                self._switch(116, y, self.values[key], selected)
            elif kind == "seed":
                hint_text = row[3]
                editing = self.seed_text is not None
                shown = self.seed_text if editing else str(config_mod.current_seed(self.cfg) or "")
                s.rect(115, y - 1, 66, 8, CANOPY[0])
                s.text(117, y, shown + ("_" if selected and editing and self.frame % 4 < 2 else ""), GOOD if editing else INK)
                self._button(186, y - 1, "GROW", lambda *_: self.seed_apply(), selected and bool(self.seed_text))
                self._button(218, y - 1, "RANDOM", lambda *_: self.seed_random(), selected)
            else:
                hint_text = row[3]
                self._switch(116, y, self.autostart_get(), selected)
            if selected:
                hint = hint_text
            y += step
        self._footer(hint)

    def _switch(self, x, y, on, selected):
        s = self.screen
        s.rect(x, y - 1, 28, 8, CANOPY[0])
        if on:
            s.rect(x + 15, y, 12, 7, GOOD if selected else TEAL[5])
            s.text(x + 2, y, "ON", GOOD if selected else INK)
        else:
            s.rect(x + 1, y, 10, 7, FAINT)
            s.text(x + 13, y, "OFF"[:2], FAINT)

    def _button(self, x, y, label, fn, lit=False):
        s = self.screen
        w = text_width(label) + 5
        hover = self.over(x, y, w, 8)
        s.rect(x, y, w, 8, FLECK[1] if lit or hover else CANOPY[1])
        s.text(x + 3, y + 1, label, INK if lit or hover else DIM)
        self.hit(x, y, w, 8, fn)

    def choose_row(self, k, mx, my):
        self.row = k
        kind = ROWS[k][0]
        if kind == "slider":
            if 116 <= mx <= 240:
                self.drag = k
                self.slide_to(mx)
        elif kind == "combo":
            if mx >= 116:
                self.adjust(-1 if mx < 124 else 1)
        elif kind in ("switch", "auto"):
            if 116 <= mx < 144:
                self.adjust(1)
        elif kind == "seed" and 115 <= mx < 181 and self.seed_text is None:
            self.seed_text = ""

    def slide_to(self, mx):
        _, key, _, low, high, step, _ = ROWS[self.drag]
        frac = max(0.0, min(1.0, (mx - 116) / 119.0))
        self.set_value(key, low + round(frac * (high - low) / step) * step)

    def set_value(self, key, value):
        default = config_mod.DEFAULTS[key][0]
        if isinstance(default, int) and not isinstance(default, bool):
            value = int(round(value))
        elif isinstance(value, float):
            value = round(value, 2)
        self.values[key] = value
        if key in ("theme", "saturation", "natural"):
            self.screen.set_palette(ui_palette(self.values["theme"], self.values["saturation"], self.values["natural"]))
        self._changed()

    def adjust(self, direction):
        row = ROWS[self.row]
        kind = row[0]
        if kind == "slider":
            _, key, _, low, high, step, _ = row
            self.set_value(key, max(low, min(high, self.values[key] + direction * step)))
        elif kind == "combo":
            _, key, _, options, _ = row
            self.set_value(key, options[(options.index(self.values[key]) + direction) % len(options)])
        elif kind == "switch":
            self.set_value(row[1], not self.values[row[1]])
        elif kind == "auto":
            self.autostart_set(not self.autostart_get())

    def seed_apply(self):
        if self.seed_text and int(self.seed_text) > 0:
            self.set_value("seed", int(self.seed_text))
            self.seed_text = None
            GLib.timeout_add(2500, self._after_seed)

    def seed_random(self):
        self.seed_text = str(random.randrange(1, 1 << 30))
        self.seed_apply()

    def _after_seed(self):
        self._refresh()
        return False

    def _changed(self):
        # Sliders fire continuously; write at most a few times a second.
        if not self.pending:
            self.pending = GLib.timeout_add(150, self._write)

    def _write(self):
        self.pending = 0
        self.cfg = config_mod.Config(self.values)
        config_mod.save(self.cfg)
        return False

    # ---- shared picture tools ----------------------------------------------

    def _fit_rows(self, rows, x, y, w, h, colors, big=3):
        """Centre a sprite in a box, as large as fits, or thinned out if it won't fit at all."""
        rw, rh = max(len(r) for r in rows), len(rows)
        if rw > w or rh > h:
            step = max(math.ceil(rw / w), math.ceil(rh / h))
            rows = [r[::step] for r in rows[::step]]
            rw, rh = max(len(r) for r in rows), len(rows)
            scale = 1
        else:
            scale = max(1, min(big, w // rw, h // rh))
        self.screen.rows(rows, x + (w - rw * scale) // 2, y + h - rh * scale, colors, False, scale)

    def _creature(self, name, g, x, y, w, h, big=3, moving=False):
        pose = ANIMAL_POSE[name][1 if moving and self.frame % 8 < 4 else 0]
        self._fit_rows(animal_rows(name, pose, g), x, y, w, h, creature_colors(name, g), big)

    def _tile(self, x, y, w, h, selected, alive=True):
        s = self.screen
        s.rect(x, y, w, h, FIREFLY[3] if selected else CANOPY[0])
        s.dither(x + 1, y + 1, w - 2, h - 2, SKY[5] if alive else CANOPY[1], SKY[4] if alive else CANOPY[0])
        s.rect(x + 1, y + h - 5, w - 2, 4, TEAL[3] if alive else CANOPY[1])

    def _bars(self, x, y, items):
        s = self.screen
        for label, value in items:
            s.text(x, y, label, DIM)
            s.meter(x + 40, y + 1, 34, value, BAD if label == "TOXIN" and value > 0.5 else TEAL[5])
            y += GLYPH_H
        return y

    # ---- animals -----------------------------------------------------------

    def _species(self):
        census = self.census()
        table = dict(census.get("animals", {}))
        table.update(census.get("residents", {}))
        return table

    def _draw_animals(self):
        s = self.screen
        s.panel(4, BODY_Y, 118, BODY_H)
        table = self._species()
        tw, th = 36, 42
        for k, name in enumerate(ANIMAL_ORDER):
            x = 8 + (k % 3) * (tw + 1)
            y = BODY_Y + 4 + (k // 3) * (th + 2)
            row = table.get(name, {})
            n = row.get("n", 0)
            self._tile(x, y, tw, th, k == self.animal or self.over(x, y, tw, th), n > 0)
            one = self._individual(name, row)[0]
            if one:
                self._creature(name, one["g"], x + 2, y + 1, tw - 4, th - 7, big=2)
            else:
                self._fit_rows(animal_rows(name, ANIMAL_POSE[name][0], default_genes()), x + 2, y + 1, tw - 4, th - 7, SILHOUETTE, 2)
            count = str(n)
            s.text(x + tw - text_width(count) - 1, y + 1, count, GOOD if n else BAD)
            self.hit(x, y, tw, th, lambda *_, k=k: setattr(self, "animal", k))
        name = ANIMAL_ORDER[self.animal]
        self._animal_page(name, table.get(name, {}))
        self._footer(ANIMAL_NOTE[name])

    def _individual(self, name, row):
        """The individual being looked at, kept across refreshes while it's still alive."""
        members = row.get("members") or []
        if not members:
            return None, 0
        chosen = self.chosen.get(name)
        if chosen is not None:
            for k, m in enumerate(members):
                if m["g"] == chosen:
                    return m, k
        # That one has died, or nobody was chosen yet: move to whoever stands nearest in the list.
        k = min(self.chosen_at.get(name, 0), len(members) - 1)
        self.chosen[name] = members[k]["g"]
        return members[k], k

    def _choose(self, name, row, k):
        members = row.get("members") or []
        if members:
            k %= len(members)
            self.chosen[name] = members[k]["g"]
            self.chosen_at[name] = k

    def _animal_page(self, name, row):
        s = self.screen
        x0 = 124
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H)
        x = x0 + 6
        y = BODY_Y + 5
        n = row.get("n", 0)
        s.text(x, y, ANIMAL_TITLE.get(name, name.upper()), GOOD if n else FAINT, scale=2)
        tag = f"{n} ALIVE" if n else "NONE LEFT"
        s.text(W - 10 - text_width(tag), y + 4, tag, INK if n else BAD)
        y += 18
        one, index = self._individual(name, row)
        g = one["g"] if one else {}
        # Portrait of this individual, on its own patch of forest.
        pw, ph = 70, 48
        s.rect(x, y, pw, ph, CANOPY[0])
        s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY[5], SKY[4])
        s.dither(x + 1, y + ph - 9, pw - 2, 8, TEAL[3], TEAL[4])
        if g:
            self._creature(name, g, x + 2, y + 2, pw - 4, ph - 6, big=3, moving=True)
        else:
            self._fit_rows(animal_rows(name, ANIMAL_POSE[name][0], default_genes()), x + 2, y + 2, pw - 4, ph - 6, SILHOUETTE)
        # Beside it: coat and waking hours, then the whole species' fortunes as a vine.
        tx = x + pw + 5
        right = W - 10
        if g:
            coat = int(g["coat"] * 14 + 0.5)
            s.text(tx, y, COAT_NAMES[coat].upper()[:11], INK)
            for k, c in enumerate(COATS[coat]):
                s.rect(right - 15 + k * 5, y + 1, 5, 5, c)
            self._hours_gauge(tx, y + 8, g, row.get("asleep", False))
        hist = row.get("hist", [])
        s.text(tx, y + 18, "POPULATION", FAINT)
        if hist:
            peak = f"{max(hist)}"
            s.text(right - text_width(peak), y + 18, peak, FAINT)
        self._vine(tx, y + 27, right - tx, 22, hist)
        y += ph + 3
        # Everyone alive: click one to see that one's own traits.
        members = row.get("members") or []
        if members:
            label = f"INDIVIDUAL {index + 1} OF {n}"
            s.text(x, y, label, DIM)
            if one.get("age") is not None:
                age = f"AGE {one['age'] / 12:.1f}H"
                s.text(right - text_width(age), y, age, FAINT)
            elif row.get("split"):
                s.text(right - text_width("TWO KINDS!"), y, "TWO KINDS!", WARM)
            y += 9
            page_size = 8
            page = index // page_size
            cx = x + 11
            shown = members[page * page_size:(page + 1) * page_size]
            for k, m in enumerate(shown):
                at = page * page_size + k
                hover = self.over(cx, y, 17, 16)
                s.rect(cx, y, 17, 16, GOOD if at == index else (FLECK[2] if hover else CANOPY[0]))
                s.rect(cx + 1, y + 1, 15, 14, SKY[4])
                self._creature(name, m["g"], cx + 1, y + 1, 15, 14, big=1)
                self.hit(cx, y, 17, 16, lambda *_, at=at, name=name, row=row: self._choose(name, row, at))
                cx += 18
            if len(members) > page_size:
                self._arrow(x, y, "<", lambda *_, name=name, row=row: self._choose(name, row, (page - 1) * page_size))
                self._arrow(x + 11 + 18 * page_size, y, ">", lambda *_, name=name, row=row: self._choose(name, row, (page + 1) * page_size))
            y += 18
            y = self._bars(x, y, [(label, g.get(key, 0.0)) for key, label in TRAITS])
        else:
            for line in wrap(ANIMAL_NOTE[name], 30)[:4]:
                s.text(x, y, line, INK)
                y += GLYPH_H
        bottom = BODY_Y + BODY_H - 22
        eats = ANIMAL_EATS.get(name)
        if name in PREY:
            menu = PREY[name]
            eats = (menu[int(g.get("diet", 0) * (len(menu) - 0.001))] + "s mostly") if g else menu[0] + "s"
        hunters = EATEN_BY.get(name)
        fears = ("FEARS " + ", ".join(h.upper() for h in hunters[:3])) if hunters else "FEARS NOTHING"
        if eats:
            s.text(x, bottom, ("EATS " + eats.upper())[:30], DIM)
        s.text(x, bottom + 9, fears[:30], DIM)

    @staticmethod
    def _clock():
        return datetime.datetime.now()

    def _day_strip(self, width):
        """Daylight across 24 hours, midnight at both ends and noon in the middle, by the wallpaper's own clock."""
        mode = self.values["time_of_day"]
        key = (mode, width)
        if self._strip_key != key:
            self._strip_key = key
            strip = []
            for k in range(width):
                minutes = int(k / width * 1440)
                strip.append(sky_state(mode, datetime.datetime(2000, 1, 1, minutes // 60, minutes % 60)))
            self._strip = strip
        return self._strip

    def _hours_gauge(self, x, y, g, hibernating):
        """A real day: the strip is the sky over 24 hours, the green line its waking hours, the pointer is now."""
        s = self.screen
        width = 66
        strip = self._day_strip(width)
        clock = self._clock()
        pinned = self.values["time_of_day"] != "auto"
        awake = not hibernating and abs(g["active"] - awake_position(sky_state(self.values["time_of_day"], clock)[0])) < 0.5
        s.text(x, y, "AWAKE" if awake else "ASLEEP", AWAKE_MARK if awake else DIM)
        bx = x + 38
        s.rect(bx - 1, y + 1, width + 2, 5, CANOPY[0])
        for k, (daylight, warmth) in enumerate(strip):
            if daylight < 0.03:
                color = SKY[3] if warmth < 0.2 else SKY[5]
            elif daylight < 0.12 or warmth > 0.45:
                color = EYE[0] if daylight < 0.3 else EYE[1]
            elif daylight < 0.4:
                color = EYE[1]
            else:
                color = EYE[2]
            s.rect(bx + k, y + 3, 1, 3, color)
            if not hibernating and abs(g["active"] - awake_position(daylight)) < 0.5:
                s.rect(bx + k, y, 1, 2, AWAKE_MARK)
        if not pinned:  # a pinned sky has no "now" to point at
            px = bx + int((clock.hour + clock.minute / 60.0) / 24.0 * width)
            s.pixel(px, y + 6, INK)
            s.rect(px - 1, y + 7, 3, 1, INK)

    def _arrow(self, x, y, label, fn):
        s = self.screen
        hover = self.over(x, y, 9, 16)
        s.rect(x, y, 9, 16, FLECK[1] if hover else CANOPY[1])
        s.text(x + 2, y + 4, label, INK)
        self.hit(x, y, 9, 16, fn)

    def _vine(self, x, y, w, h, hist):
        """Population as a climbing vine: it grows rightward from its root as the hours pass."""
        s = self.screen
        s.set_clip(x, y, w, h)
        s.dither(x, y + h - 2, w, 2, LITTER[1], LITTER[0])
        ground = y + h - 3
        if len(hist) < 2:  # just planted
            s.rect(x + 2, ground - 2, 1, 3, LEAF[5])
            s.pixel(x + 1, ground - 2, LEAF[6])
            s.pixel(x + 3, ground - 3, LEAF[6])
            s.text(x + 8, y + h - 11, "SPROUTING", FAINT)
            s.set_clip()
            return
        top = max(max(hist), 1)
        room = h - 7
        spacing = (w - 5) / 59.0
        prev_x = x + 1
        prev_y = ground - hist[0] * room // top
        s.rect(prev_x, prev_y, 1, ground - prev_y + 1, TRUNK[4])  # the stem rising from its root
        col = 0
        for k in range(1, len(hist)):
            nx = x + 1 + int(k * spacing)
            ny = ground - hist[k] * room // top
            # Fresh green while thriving, olive as numbers fall, dead brown at nothing.
            color = LITTER[3] if hist[k] == 0 else (OLIVE[4] if hist[k] < hist[k - 1] else LEAF[5])
            for px in range(prev_x + 1, nx + 1):
                t = (px - prev_x) / max(1, nx - prev_x)
                py = int(prev_y + (ny - prev_y) * t)
                last = int(prev_y + (ny - prev_y) * (px - 1 - prev_x) / max(1, nx - prev_x))
                s.rect(px, min(py, last), 1, abs(py - last) + 2, color)  # two pixels thick, so it reads as a stem
                col += 1
                if hist[k] == 0:
                    continue
                if col % 6 == 0:  # leaves, one side then the other
                    if col % 12 == 0:
                        s.pixel(px + 1, py - 1, LEAF[6])
                        s.pixel(px + 2, py - 1, LEAF[6])
                    else:
                        s.pixel(px + 1, py + 1, LEAF[6])
                        s.pixel(px + 2, py + 2, LEAF[6])
                elif col % 17 == 0:  # now and then a curling tendril
                    s.pixel(px, py - 1, LEAF[4])
                    s.pixel(px + 1, py - 2, LEAF[4])
                    s.pixel(px + 2, py - 2, LEAF[4])
                    s.pixel(px + 2, py - 1, LEAF[4])
            prev_x, prev_y = nx, ny
        # At the growing tip, today's count in flower. A dead tip stays brown.
        if hist[-1]:
            s.rect(prev_x, prev_y - 1, 3, 3, FLOWER[3][1])
            s.pixel(prev_x + 1, prev_y, EYE[2] if self.frame % 8 < 5 else EYE[1])
        else:
            s.rect(prev_x, prev_y - 1, 2, 2, LITTER[2])
        s.set_clip()

    # ---- plants ------------------------------------------------------------

    def _draw_plant(self, kind, sample, x, y, w, h):
        key = (kind, sample["seed"])
        pix = self.sprite_cache.get(key)
        if pix is None:
            pix = self.sprite_cache[key] = BUILDERS[kind](sample["g"], sample["seed"], 1.15)
        if not pix:
            return
        xs = [p[0] for p in pix]
        ys = [p[1] for p in pix]
        x0, y0 = min(xs), min(ys)
        pw, ph = max(xs) - x0 + 1, max(ys) - y0 + 1
        shrink = max(1, math.ceil(pw / w), math.ceil(ph / h))
        grow = max(1, min(2, w // pw, h // ph))
        s = self.screen
        ox = x + (w - pw * grow // shrink) // 2
        oy = y + h - ph * grow // shrink
        for dx, dy, c in pix:
            px, py = ox + (dx - x0) * grow // shrink, oy + (dy - y0) * grow // shrink
            if grow == 1:
                s.pixel(px, py, c)
            else:
                s.rect(px, py, grow, grow, c)

    def _draw_plants(self):
        s = self.screen
        flora = self.census().get("flora", {})
        s.panel(4, BODY_Y, 118, BODY_H)
        tw, th = 36, 42
        for k, kind in enumerate(PLANT_ORDER):
            x = 8 + (k % 3) * (tw + 1)
            y = BODY_Y + 4 + (k // 3) * (th + 2)
            row = flora.get(kind, {})
            n = row.get("n", 0)
            self._tile(x, y, tw, th, k == self.plant or self.over(x, y, tw, th), n > 0)
            if row.get("samples"):
                self._draw_plant(kind, row["samples"][0], x + 2, y + 1, tw - 4, th - 5)
            label = str(n)
            s.text(x + tw - text_width(label) - 1, y + 1, label, GOOD if n else FAINT)
            self.hit(x, y, tw, th, lambda *_, k=k: setattr(self, "plant", k))
        kind = PLANT_ORDER[self.plant]
        self._plant_page(kind, flora.get(kind, {}))
        self._footer("ARROWS BROWSE   TAB NEXT PAGE   ESC CLOSE")

    def _plant_page(self, kind, row):
        s = self.screen
        x0 = 124
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H)
        x = x0 + 6
        y = BODY_Y + 5
        n = row.get("n", 0)
        s.text(x, y, PLANT_TITLE.get(kind, kind.upper()), GOOD if n else FAINT, scale=2)
        y += 18
        pw, ph = 70, 56
        s.rect(x, y, pw, ph, CANOPY[0])
        s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY[5], SKY[4])
        s.rect(x + 1, y + ph - 6, pw - 2, 5, TEAL[3])
        samples = row.get("samples") or []
        if samples:
            # Leaf through the real specimens.
            self._draw_plant(kind, samples[(self.frame // 24) % len(samples)], x + 2, y + 2, pw - 4, ph - 5)
        else:
            s.text(x + 8, y + 22, "NONE LEFT", FAINT)
        tx = x + pw + 5
        s.text(tx, y, f"{n} GROWING", INK)
        s.text(tx, y + 9, f"{row.get('mature', 0)} MATURE", DIM)
        if row.get("wilting"):
            s.text(tx, y + 18, f"{row['wilting']} WILTING", WARM)
        s.text(tx, y + 27, f"GENERATION {row.get('gen', 0)}", DIM)
        hours = LIFESPAN.get(kind, 0) / 7200.0
        if kind in SEASONAL_PLANTS:
            s.text(tx, y + 36, f"IN {SEASONAL_PLANTS[kind].upper()}", SEASON_COLOR[SEASONAL_PLANTS[kind]])
        else:
            s.text(tx, y + 36, f"LIVES ~{hours:.1f}H" if hours >= 1 else f"LIVES ~{hours * 60:.0f}M", DIM)
        if kind in COMPANIONS and n:
            s.text(tx, y + 45, f"{row.get('partnered', 0)}/{n} HAVE FUNGI", TEAL[6] if row.get("partnered") else FAINT)
        y += ph + 4
        means = row.get("means") or {}
        hues = row.get("hues") or []
        if means and kind not in ("mushroom", "log"):
            y = self._bars(x, y, [(label, means.get(key, 0.0)) for key, label in PLANT_TRAITS])
            if kind in ("spire", "clover", "shrub") and hues:
                s.text(x, y, "FLOWERS", DIM)
                for k, hue in enumerate(hues[:8]):
                    s.rect(x + 46 + k * 7, y, 6, 7, FLOWER[hue % 8][1])
                y += GLYPH_H
            for line in wrap(PLANT_NOTE[kind], 30)[:2]:
                s.text(x, y + 1, line, INK)
                y += GLYPH_H
        else:
            if kind == "mushroom" and hues:
                s.text(x, y, "CAPS", DIM)
                for k, hue in enumerate(hues[:6]):
                    s.rect(x + 34 + k * 7, y, 6, 7, CAP[hue % 6][2])
                y += GLYPH_H + 2
            for line in wrap(PLANT_NOTE[kind], 30)[:4]:
                s.text(x, y, line, INK)
                y += GLYPH_H
        good = [k.upper() for k, v in sorted(COMPANIONS.get(kind, {}).items(), key=lambda kv: -kv[1]) if v > 0][:3]
        if good:
            s.text(x, BODY_Y + BODY_H - 13, ("LIKES " + ", ".join(good))[:31], DIM)

    # ---- forest ------------------------------------------------------------

    def _field_map(self):
        """The real ground, one pixel for every three: moss, threads and plants as they stand now."""
        if self.map_cache is not None:
            return self.map_cache
        data = self.data
        sc = self.scene
        if not data or not sc or (sc.w, sc.h) != (data.get("w"), data.get("h")):
            return None
        mw, mh = sc.w // 3, (sc.h - sc.gy) // 3
        out = bytearray(mw * mh)
        for y in range(mh):
            for x in range(mw):
                i = (sc.gy + y * 3 + 1) * sc.w + x * 3 + 1
                out[y * mw + x] = WATER[3] if sc.water[i] else sc.base[i]
        eco = data["eco"]
        try:
            cells = zlib.decompress(base64.b64decode(eco["cells"]))
            strains = eco["strains"]
            mc = sc.w // 2
            for c, sid in enumerate(cells):
                if sid and strains[sid]:
                    cy, cx = divmod(c, mc)
                    k = (cy * 2 // 3) * mw + cx * 2 // 3
                    if 0 <= k < len(out):
                        out[k] = MOSS[strains[sid][2]][2]
            hues = {col[0]: col[1]["hue"] for col in eco["colonies"]}
            for p, cid, _ in eco["threads"]:
                y, x = divmod(p, sc.w)
                k = ((y - sc.gy) // 3) * mw + x // 3
                if 0 <= k < len(out):
                    out[k] = MYC[hues.get(cid, 0)][1]
            for row in eco["plants"]:
                k = ((row[2] - sc.gy) // 3) * mw + row[1] // 3
                if 0 <= k < len(out):
                    out[k] = {"mushroom": FLOWER[5][2], "sapling": STAG[3], "log": LITTER[4], "shrub": FLOWER[4][1]}.get(row[0], LEAF[6])
        except (KeyError, ValueError, zlib.error, TypeError):
            pass
        self.map_cache = (bytes(out), mw, mh)
        return self.map_cache

    def _draw_forest(self):
        s = self.screen
        census = self.census()
        s.panel(4, BODY_Y, W - 8, BODY_H)
        x, y = 10, BODY_Y + 5
        s.text(x, y, "FIELD MAP", GOOD)
        mw, mh = 160, 45
        s.rect(x, y + 9, mw + 2, mh + 2, CANOPY[0])
        field = self._field_map()
        if field:
            pix, fw, fh = field
            for row in range(min(mh, fh)):
                o = (y + 10 + row) * W + x + 1
                s.px[o:o + min(mw, fw)] = pix[row * fw:row * fw + min(mw, fw)]
        key_y = y + 58
        for label, c, kx in (("MOSS", MOSS[0][2], 0), ("THREADS", MYC[0][1], 34), ("PLANTS", LEAF[6], 86), ("TREES", STAG[3], 128)):
            s.rect(x + kx, key_y + 1, 4, 5, c)
            s.text(x + kx + 6, key_y, label[:5] if label == "TREES" else label, FAINT)
        # Soil.
        sx = x + mw + 8
        soil = census.get("soil", {})
        s.text(sx, y, "SOIL", GOOD)
        yy = y + 10
        for label, key, on in (("FOOD", "nutrients", LITTER[4]), ("WATER", "moisture", WATER[4]), ("SHADE", "shade", VIOLET[5]), ("MOSS", "moss", MOSS[0][3])):
            s.text(sx, yy, label, DIM)
            s.meter(sx + 36, yy + 1, 16, min(1.0, soil.get(key, 0.0)), on)
            yy += GLYPH_H
        if census.get("snow", 0) > 0.02 or census.get("ice", 0) > 0.02:
            yy -= GLYPH_H  # moss is under the snow anyway, so the snow takes its row
            s.rect(sx, yy, W - 12 - sx, GLYPH_H - 1, SKY[1])
            s.text(sx, yy, "SNOW", DIM)
            s.meter(sx + 36, yy + 1, 8, min(1.0, census.get("snow", 0)), STAG[2])
            s.text(sx + 70, yy, "ICE", DIM)
            s.meter(sx + 90, yy + 1, 6, min(1.0, census.get("ice", 0)), WATER[5])
            yy += GLYPH_H
        weather = census.get("weather", "clear")
        word = {"rain": "RAINING", "storm": "THUNDERSTORM", "snow": "SNOWING", "fog": "FOG", "aurora": "AURORA!"}.get(weather, "CLEAR")
        temp = census.get("temp")
        s.text(sx, yy + 1, word, TEAL[6] if weather != "clear" else DIM)
        if temp is not None:
            label = f"{temp:.0f}C"
            s.text(W - 12 - text_width(label), yy + 1, label, DIM)
        yy += 11
        flies = census.get("fireflies", {})
        s.text(sx, yy, "FLIES", DIM)
        fx = sx + 36
        for k, count in enumerate(flies.get("hues", [])):
            if count:
                s.rect(fx, yy + 1, max(1, count // 2), 5, FLY_RAMPS[k][3])
                fx += max(1, count // 2) + 1
        # Moss rules: the evolving game of life.
        y2 = y + 70
        s.text(x, y2, "RULES OF THE MOSS", GOOD)
        rules = census.get("top_moss_rules", [])
        tones = census.get("rule_tones", [])
        top = max((n for _, n in rules), default=1)
        yy = y2 + 10
        for k, (rule, n) in enumerate(rules[:5]):
            tone = tones[k] if k < len(tones) else 0
            s.rect(x, yy + 1, 5, 5, MOSS[tone][3])
            s.text(x + 7, yy, rule[:16], INK if k == 0 else DIM)
            s.meter(x + 106, yy + 1, 12, n / top, MOSS[tone][3])
            yy += GLYPH_H
        # Fungal colonies.
        cx = x + 162
        s.text(cx, y2, "FUNGAL COLONIES", GOOD)
        fungi = sorted(census.get("fungi", []), key=lambda c: -c["threads"])
        top = max((c["threads"] for c in fungi), default=1) or 1
        yy = y2 + 10
        for col in fungi[:5]:
            s.rect(cx, yy + 1, 5, 5, MYC[col["hue"]][3])
            s.meter(cx + 8, yy + 1, 14, col["threads"] / top, MYC[col["hue"]][2])
            if col["parasite"] > 0.3:
                s.text(cx + 68, yy, "PARASITE", BAD)
            yy += GLYPH_H
        if not fungi:
            s.text(cx, yy, "None yet", FAINT)
        # Tally.
        y3 = y2 + 58
        stats = census.get("stats", {})
        s.text(x, y3, "SO FAR", GOOD)
        tally = (("BORN", stats.get("births", 0), FIREFLY[3]), ("DIED", stats.get("deaths", 0), FLOWER[4][2]),
                 ("FRUITED", stats.get("fruitings", 0), FLOWER[5][2]), ("POLLEN", stats.get("pollinations", 0), FLOWER[3][2]),
                 ("RAINS", stats.get("rains", 0), MYC[0][3]), ("STAG", stats.get("stag_visits", 0), STAG[3]))
        for k, (label, value, c) in enumerate(tally):
            tx = x + (k % 3) * 50
            ty = y3 + 9 + (k // 3) * 19
            s.text(tx, ty, label, DIM)
            s.text(tx, ty + 9, str(value), c)
        seen = sorted(stats.get("sightings", {}).items(), key=lambda kv: -kv[1])
        s.text(cx, y3, "MOST SEEN", GOOD)
        yy = y3 + 9
        for name, count in seen[:4]:
            words = name.replace(" taken by ", ", ").replace(" eaten by ", ", ").replace(" caught by ", ", ")
            s.text(cx, yy, f"{count:>4} {words}"[:23], DIM)
            yy += GLYPH_H
        near = (census.get("sky") or {}).get("near")
        self._footer(f"THE MOON IS NEAR {near.upper()} TONIGHT" if near else "YOUR GROUND AS IT IS NOW   TAB NEXT PAGE")

    # ---- oddities ----------------------------------------------------------

    def _draw_oddities(self):
        s = self.screen
        seen = self.census().get("oddities", {})
        names = list(CATALOGUE)
        found = sum(1 for n in names if n in seen)
        s.panel(4, BODY_Y, 118, BODY_H)
        tw, th = 22, 29
        for k, name in enumerate(names):
            x = 8 + (k % 5) * (tw - 0)
            y = BODY_Y + 4 + (k // 5) * (th + 1)
            known = name in seen
            hover = self.over(x, y, tw - 1, th)
            s.rect(x, y, tw - 1, th, GOOD if k == self.odd_index or hover else CANOPY[0])
            if known:
                s.dither(x + 1, y + 1, tw - 3, th - 2, SKY[5], SKY[4])
                self._odd_icon(name, x + 1, y + 1, tw - 3, th - 2)
            else:
                s.dither(x + 1, y + 1, tw - 3, th - 2, CANOPY[1], CANOPY[0])
                s.text(x + 7, y + 10, "?", FAINT)
            self.hit(x, y, tw - 1, th, lambda *_, k=k: setattr(self, "odd_index", k))
        name = names[self.odd_index]
        title, riddle, note = CATALOGUE[name]
        x0 = 124
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H)
        x, y = x0 + 6, BODY_Y + 5
        entry = seen.get(name)
        s.text(x, y, title if entry else "? ? ?", GOOD if entry else FAINT, scale=1 if len(title) > 15 else 2)
        y += 18
        pw, ph = W - x0 - 16, 62
        s.rect(x, y, pw, ph, CANOPY[0])
        if entry:
            s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY[5], SKY[4])
            s.dither(x + 1, y + ph - 6, pw - 2, 5, TEAL[2], TEAL[3])
            self._odd_icon(name, x + 8, y + 2, pw - 16, ph - 7, big=3)
        else:
            s.dither(x + 1, y + 1, pw - 2, ph - 2, CANOPY[1], CANOPY[0])
            s.text(x + pw // 2 - 8, y + ph // 2 - 8, "?", FAINT, scale=3)
        y += ph + 6
        if entry:
            for line in wrap(note, 30)[:4]:
                s.text(x, y, line, INK)
                y += GLYPH_H
            y += 4
            s.text(x, y, f"SEEN {entry['n']} TIME" + ("S" if entry["n"] != 1 else ""), DIM)
            s.text(x, y + 9, "FIRST " + time.strftime("%d %b %Y", time.localtime(entry["first"])).upper(), DIM)
        else:
            s.text(x, y, "NOT YET SEEN", BAD)
            y += GLYPH_H + 4
            for line in wrap(riddle, 30)[:3]:
                s.text(x, y, line, WARM)
                y += GLYPH_H
        self._footer(f"FOUND {found} OF {len(names)}   ARROWS BROWSE   TAB NEXT PAGE")

    def _odd_icon(self, name, x, y, w, h, big=2):
        s = self.screen
        ink = {"#": CANOPY[0], "=": CANOPY[2], "o": EYE[1], "R": FLOWER[4][2], "E": FLOWER[4][1], "O": CANOPY[0]}
        if name in CATALOGUE_SPRITES:
            ink.update({"w": HARE[3], "+": CANOPY[2]})
            if name == "woodpecker":
                ink["R"] = FLOWER[4][2]
                ink["o"] = HARE[2]
            elif name in ("treecreeper", "pine_marten"):
                ink.update({"#": LITTER[3], "+": LITTER[2]})
            if name in ("kodama", "ermine"):
                ink["#"] = STAG[2]
            elif name == "dragonfly":
                ink["#"] = MYC[2][2]
            elif name == "ducks":
                ink["#"] = LITTER[3]
            self._fit_rows(CATALOGUE_SPRITES[name], x, y, w, h, ink, big)
        elif name == "jackalope":
            g = dict(default_genes(), ornament=0.95, coat=0.36, size=0.4)
            self._fit_rows(animal_rows("rabbit", "sit", g), x, y, w, h, creature_colors("rabbit", g), big)
        elif name == "ghost_stag":
            g = dict(default_genes(), coat=0.57, size=0.5, ornament=0.5)
            self._fit_rows(animal_rows("stag", "walk0", g), x, y, w, h, {"#": SKY[7], "+": SKY[6], "w": SKY[7], "o": SKY[7], "O": SKY[6], "^": SKY[7]}, big)
        elif name == "black_dog":
            cx, cy = x + w // 2, y + h // 2
            s.rect(cx - 4, cy, 2, 2, FLOWER[4][1])
            s.rect(cx + 2, cy, 2, 2, FLOWER[4][1])
        elif name == "big_tracks":
            for k in range(3):
                px, py = x + w // 4 + k * w // 4, y + h - 6 - k * (h // 4)
                s.rect(px, py, 5, 3, CANOPY[0])
                s.rect(px + 1, py - 1, 3, 1, CANOPY[0])
        elif name == "under_ice":
            s.rect(x + 1, y + 1, w - 2, h - 2, WATER[4])
            s.rect(x + w // 5, y + h // 2 - 1, w * 3 // 5, 3, WATER[0])
        elif name == "aurora":
            for k, c in enumerate((MYC[2][2], MYC[2][3], MYC[1][2])):
                s.rect(x + 1, y + 2 + k * h // 4, w - 2, 2, c)
        elif name == "rainbow":
            for k in range(7):
                s.rect(x + 1, y + 2 + k * 2, w - 2, 2, RAINBOW[k])
        elif name == "fireworks":
            cx, cy = x + w // 2, y + h // 2
            for k in range(8):
                a = k * math.tau / 8
                s.pixel(int(cx + math.cos(a) * 5), int(cy + math.sin(a) * 5), FLOWER[5][2])
                s.pixel(int(cx + math.cos(a) * 2), int(cy + math.sin(a) * 2), FLOWER[4][2])
        elif name == "heart":
            rows = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
            self._fit_rows(rows, x, y, w, h, {"#": FLOWER[3][2]}, big)
        elif name == "eggs":
            self._fit_rows(EGG, x, y, w, h, {"#": FLOWER[0][1], "w": FLOWER[5][2]}, big)
        elif name == "lucky_clover":
            rows = [".##.##.", "#######", ".#####.", "#######", ".##.##.", "...#..."]
            self._fit_rows(rows, x, y, w, h, {"#": FIREFLY[2]}, big)
        elif name == "square_moon":
            rows = ["######"] * 6
            self._fit_rows(rows, x, y, w, h, {"#": MOON[4]}, big)

    # ---- journal -----------------------------------------------------------

    def _layout_journal(self):
        """Day ribbons and entries with their pictures, laid out once per refresh."""
        evo = ((self.data or {}).get("eco", {}).get("wild", {}) or {}).get("evo") or {}
        rows = []
        day = None
        for when, _, text in reversed(evo.get("journal", [])):
            moment = time.localtime(when)
            heading = time.strftime("%A %d %B", moment).upper()
            if heading != day:
                day = heading
                rows.append(("day", heading))
            rows.append(("entry", report.clock_time(moment), wrap(text, 37), self._journal_icon(text), self._journal_tone(text)))
        return rows

    @staticmethod
    def _journal_icon(text):
        low = text.lower()
        for word, name in ICON_WORDS:
            if word in low:
                return name
        return None

    @staticmethod
    def _journal_tone(text):
        low = text.lower()
        if any(w in low for w in ("sick", "last of", "crashed", "infect", "body of")):
            return BAD
        if any(w in low for w in ("wandered", "returned", "found its way", "everywhere")):
            return GOOD
        if any(w in low for w in ("glows", "antler", "two kinds", "as one", "ornament", "poison", "plume")):
            return WARM
        return INK

    def _row_height(self, row):
        return 13 if row[0] == "day" else max(16, len(row[2]) * GLYPH_H + 2)

    def _draw_journal(self):
        s = self.screen
        s.panel(4, BODY_Y, W - 8, BODY_H)
        rows = self.journal_rows
        top = BODY_Y + 4
        view = BODY_H - 8
        total = sum(self._row_height(r) for r in rows)
        self.max_scroll = max(0, total - view)
        self.scroll = max(0, min(self.max_scroll, self.scroll))
        s.set_clip(8, top, W - 22, view)
        y = top - self.scroll
        table = self._species()
        if not rows:
            s.text(14, top + 20, "Nothing written yet.", DIM)
            s.text(14, top + 30, "It takes a little while for", FAINT)
            s.text(14, top + 40, "a forest to have a history.", FAINT)
        for row in rows:
            h = self._row_height(row)
            if y + h >= top and y < top + view:
                if row[0] == "day":
                    s.rect(8, y + 1, W - 22, 10, TEAL[1])
                    s.text(11, y + 2, row[1], GOOD)
                else:
                    _, when, lines, icon, tone = row
                    s.rect(8, y + 1, 18, 14, SKY[4])
                    if icon:
                        g = (table.get(icon) or {}).get("means") or default_genes()
                        self._creature(icon, g, 9, y + 2, 16, 12, big=1)
                    else:
                        s.rect(14, y + 5, 5, 5, tone)
                    s.text(29, y + 1, when, DIM)
                    for k, line in enumerate(lines):
                        s.text(29 + (52 if k == 0 else 0), y + 1 + k * GLYPH_H, line, tone)
            y += h
        s.set_clip()
        if self.max_scroll:
            bar_h = max(10, view * view // total)
            s.rect(W - 13, top, 4, view, CANOPY[0])
            s.rect(W - 13, top + (view - bar_h) * self.scroll // self.max_scroll, 4, bar_h, FLECK[1])
        self._footer("UP/DOWN OR WHEEL TO READ   TAB NEXT PAGE   ESC CLOSE")

    # ---- input -------------------------------------------------------------

    def _to_screen(self, event):
        return int((event.x - self.ox) / self.scale), int((event.y - self.oy) / self.scale)

    def on_press(self, area, event):
        self.area.grab_focus()
        self.mouse = self._to_screen(event)
        mx, my = self.mouse
        for x, y, w, h, fn in reversed(self.hits):
            if x <= mx < x + w and y <= my < y + h:
                fn(mx, my)
                break
        return True

    def on_release(self, area, event):
        self.drag = None
        return True

    def on_motion(self, area, event):
        self.mouse = self._to_screen(event)
        if self.drag is not None and event.state & Gdk.ModifierType.BUTTON1_MASK:
            self.slide_to(self.mouse[0])
        return True

    def on_scroll(self, area, event):
        if self.tab == 5:
            if event.direction == Gdk.ScrollDirection.UP:
                self.scroll -= 18
            elif event.direction == Gdk.ScrollDirection.DOWN:
                self.scroll += 18
            elif event.direction == Gdk.ScrollDirection.SMOOTH:
                self.scroll += int(event.delta_y * 18)
        return True

    def on_key(self, window, event):
        key = Gdk.keyval_name(event.keyval)
        if key == "Escape":
            if self.seed_text is not None:
                self.seed_text = None
            else:
                self.destroy()
        elif key in ("Tab", "ISO_Left_Tab"):
            self.set_tab((self.tab + (-1 if key == "ISO_Left_Tab" else 1)) % len(TABS))
        else:
            (self._key_settings, self._key_animals, self._key_plants, lambda k: None, self._key_oddities, self._key_journal)[self.tab](key)
        return True

    def _key_settings(self, key):
        row = ROWS[self.row]
        if row[0] == "seed" and (key.isdigit() or (key.startswith("KP_") and key[3:].isdigit())):
            self.seed_text = ((self.seed_text or "") + key[-1])[:10]
        elif key == "BackSpace" and self.seed_text is not None:
            self.seed_text = self.seed_text[:-1]
        elif key in ("Up", "Down"):
            self.seed_text = None
            self.row = (self.row + (1 if key == "Down" else -1)) % len(ROWS)
        elif key in ("Left", "Right"):
            self.adjust(1 if key == "Right" else -1)
        elif key in ("Return", "KP_Enter", "space"):
            if row[0] == "seed":
                self.seed_apply()
            elif row[0] in ("switch", "auto", "combo"):
                self.adjust(1)

    @staticmethod
    def _grid_move(value, key, columns, count):
        step = {"Left": -1, "Right": 1, "Up": -columns, "Down": columns}.get(key, 0)
        return (value + step) % count

    def _key_animals(self, key):
        if key in ("bracketleft", "bracketright", "comma", "period"):
            # Step through the individuals of this species.
            name = ANIMAL_ORDER[self.animal]
            row = self._species().get(name, {})
            index = self._individual(name, row)[1]
            self._choose(name, row, index + (1 if key in ("bracketright", "period") else -1))
        else:
            self.animal = self._grid_move(self.animal, key, 3, len(ANIMAL_ORDER))

    def _key_plants(self, key):
        self.plant = self._grid_move(self.plant, key, 3, len(PLANT_ORDER))

    def _key_oddities(self, key):
        self.odd_index = self._grid_move(self.odd_index, key, 5, len(CATALOGUE))

    def _key_journal(self, key):
        self.scroll += {"Up": -18, "Down": 18, "Page_Up": -150, "Page_Down": 150, "Home": -100000, "End": 100000}.get(key, 0)

    # ---- drawing to the window ---------------------------------------------

    def on_draw(self, area, ctx):
        aw, ah = area.get_allocated_width(), area.get_allocated_height()
        self.scale = max(1, min(aw // W, ah // H))
        self.ox = (aw - W * self.scale) // 2
        self.oy = (ah - H * self.scale) // 2
        ctx.set_source_rgb(0.01, 0.01, 0.04)
        ctx.paint()
        ctx.translate(self.ox, self.oy)
        ctx.scale(self.scale, self.scale)
        self.surface.mark_dirty()
        ctx.set_source_surface(self.surface, 0, 0)
        ctx.get_source().set_filter(cairo.Filter.NEAREST)
        ctx.paint()
        return False


def run(autostart_get, autostart_set):
    Gtk.Window.set_default_icon_name(icon.APP_ID)
    Menu(autostart_get, autostart_set)
    Gtk.main()
