"""User configuration: a small TOML file, hot-reloaded by the running wallpaper."""

import gzip
import json
import os
import tomllib

CONFIG_DIR = os.path.join(os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")), "bloom-wallpaper")
STATE_DIR = os.path.join(os.environ.get("XDG_STATE_HOME", os.path.expanduser("~/.local/state")), "bloom-wallpaper")
CACHE_DIR = os.path.join(os.environ.get("XDG_CACHE_HOME", os.path.expanduser("~/.cache")), "bloom-wallpaper")
CONFIG_PATH = os.path.join(CONFIG_DIR, "config.toml")
STATE_PATH = os.path.join(STATE_DIR, "world.json.gz")

THEMES = ("deepwoods", "teal", "moonlit", "amber", "violet")
TIMES = ("auto", "night", "dusk", "day")
SEASON_MODES = ("auto", "spring", "summer", "autumn", "winter")
HEMISPHERES = ("north", "south")

# name -> (default, comment)
DEFAULTS = {
    "darkness": (0.55, "0 = brightest, 1 = nearly black. Glowing things fade less than the scenery."),
    "glow": (0.7, "Strength of luminous life: fireflies, mushrooms, mycelium pulses, the moon. 0-1."),
    "saturation": (0.9, "Colour intensity. 0 = one colour, this palette's own; 1 = as painted; up to 1.5."),
    "natural": (0.8, "How natural the colours are, mainly by day. 0 = one strong tint over everything, 1 = each thing its own colour."),
    "flare": (0.6, "Bloom and lens flare from the sun and moon. 0 = off, 1 = strong."),
    "crt": (0.0, "An old tube over the picture: beam smear, phosphor dots, dark rounded edges. 0 = off, 1 = heavy."),
    "theme": ("deepwoods", "deepwoods | teal | moonlit | amber | violet"),
    "time_of_day": ("auto", "auto follows your clock. Or pin it: night | dusk | day"),
    "season": ("auto", "auto follows your calendar. Or stay in one: spring | summer | autumn | winter"),
    "hemisphere": ("north", "Which half of the world your calendar's seasons belong to: north | south"),
    "oddities": (True, "Cryptids and date-based surprises. Seasonal plants and animals stay either way."),
    "constellations": (True, "Real star positions, through the gaps in the leaves."),
    "location": ("auto", "Where you are, for the sky: auto uses your timezone's main city, or give \"latitude, longitude\" (north and east positive)."),
    "pixel_size": (4, "Screen pixels per art pixel. Bigger is chunkier and cheaper. Changing it starts a new world."),
    "fps": (8, "Animation frames per second. 4-15. Lower saves battery."),
    "speed": (1.0, "Ecosystem pace. 0.25 = glacial, 1 = default, 4 = brisk."),
    "density": (1.0, "How much life the forest can carry. 0.3-1.5."),
    "fireflies": (28, "Firefly count at night. 0 turns them off."),
    "fauna": (True, "All animals, plus visitors like the wisp and fairy rings."),
    "light_shafts": (True, "Slowly drifting shafts of moon or sunlight."),
    "weather": (True, "Clouds, rain, snowfall, storms and mist. Mushrooms flush after rain."),
    "battery_saver": (True, "Halve the frame rate when running on battery."),
    "seed": (0, "Which forest to grow. Each number is its own landscape, kept when you switch away. 0 = pick at random."),
}


def _clamp(value, low, high):
    return max(low, min(high, value))


class Config:
    def __init__(self, values=None):
        for key, (default, _) in DEFAULTS.items():
            setattr(self, key, default)
        for key, value in (values or {}).items():
            if key in DEFAULTS:
                default = DEFAULTS[key][0]
                if isinstance(default, bool):
                    value = bool(value)
                elif isinstance(default, (int, float)) and not isinstance(value, (int, float)):
                    continue
                elif isinstance(default, str) and not isinstance(value, str):
                    continue
                setattr(self, key, value)
        self.darkness = _clamp(float(self.darkness), 0.0, 1.0)
        self.glow = _clamp(float(self.glow), 0.0, 1.0)
        self.saturation = _clamp(float(self.saturation), 0.0, 1.5)
        self.natural = _clamp(float(self.natural), 0.0, 1.0)
        self.flare = _clamp(float(self.flare), 0.0, 1.0)
        self.crt = _clamp(float(self.crt), 0.0, 1.0)
        self.pixel_size = int(_clamp(self.pixel_size, 2, 10))
        self.fps = int(_clamp(self.fps, 1, 30))
        self.speed = _clamp(float(self.speed), 0.05, 20.0)
        self.density = _clamp(float(self.density), 0.1, 2.0)
        self.fireflies = int(_clamp(self.fireflies, 0, 120))
        self.seed = int(self.seed)
        if self.theme not in THEMES:
            self.theme = "deepwoods"
        if self.time_of_day not in TIMES:
            self.time_of_day = "auto"
        if self.season not in SEASON_MODES:
            self.season = "auto"
        if self.hemisphere not in HEMISPHERES:
            self.hemisphere = "north"

    def as_dict(self):
        return {key: getattr(self, key) for key in DEFAULTS}


def load():
    try:
        with open(CONFIG_PATH, "rb") as f:
            return Config(tomllib.load(f))
    except FileNotFoundError:
        return Config()
    except (OSError, tomllib.TOMLDecodeError) as e:
        print(f"bloom: ignoring unreadable config: {e}")
        return Config()


def _toml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return f'"{value}"'
    if isinstance(value, float):
        return f"{value:.3f}".rstrip("0").rstrip(".") if value % 1 else f"{value:.1f}"
    return str(value)


def save(config):
    os.makedirs(CONFIG_DIR, exist_ok=True)
    lines = ["# Bloom wallpaper settings. Changes apply immediately.", ""]
    for key, (_, comment) in DEFAULTS.items():
        lines.append(f"# {comment}")
        lines.append(f"{key} = {_toml_value(getattr(config, key))}")
        lines.append("")
    tmp = CONFIG_PATH + ".tmp"
    with open(tmp, "w") as f:
        f.write("\n".join(lines))
    os.replace(tmp, CONFIG_PATH)


def archive_path(seed):
    return os.path.join(STATE_DIR, f"world-{seed}.json.gz")


def current_seed(config):
    """The seed of the forest in use, even when the config leaves the choice to chance."""
    if config.seed:
        return config.seed
    try:
        with gzip.open(STATE_PATH, "rt") as f:
            return json.load(f).get("seed", 0)
    except (OSError, ValueError, EOFError):
        return 0


def ensure_exists():
    if not os.path.exists(CONFIG_PATH):
        save(Config())
