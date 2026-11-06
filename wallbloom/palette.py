"""The 256-colour palette.

Everything on screen is an index into this table, in the spirit of old
colour-cycling art. Moving the whole forest from night to dusk, or changing
darkness, only rebuilds the table and re-translates the image with
bytes.translate, which is far cheaper than touching pixels from Python.
"""


import math


def _hex(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


class Ramp:
    """A run of palette entries from dark to light."""

    __slots__ = ("start", "n")

    def __init__(self, start, n):
        self.start = start
        self.n = n

    def __getitem__(self, level):
        if level < 0:
            level = 0
        elif level >= self.n:
            level = self.n - 1
        return self.start + level


_colors = [(0, 0, 0)]  # index 0 means "transparent" in every plane
_kinds = ["scene"]
_ramp_of = [None]
_ramps = []


def _ramp(colors, kind="scene"):
    ramp = Ramp(len(_colors), len(colors.split()))
    for c in colors.split():
        _colors.append(_hex(c))
        _kinds.append(kind)
        _ramp_of.append(ramp)
    _ramps.append(ramp)
    return ramp


SKY = _ramp("090a22 0d0e30 12133f 17194f 1e205f 262872 30338a 3b3f9f", "sky")
FAR = _ramp("05061a 080922 0c0e2c 101236 14173f 191c49")
CANOPY = _ramp("02030b 040612 070a1a 0a0e22 0e142c 121a36")
TRUNK = _ramp("040307 09060c 100a12 18101a 221722 2e2029 3e2c33")
VIOLET = _ramp("0a0920 100e2d 16133a 1d1848 251e57 2e2566")
TEAL = _ramp("03101a 051a24 08262e 0c3338 124243 19524f 226560")
FLECK = _ramp("2a3580 3f4fb5 5a70da 7d95f2 a4bcff", "glow")
LEAF = _ramp("03130f 061f1a 0a2c24 103b2f 184d3c 22614b 2e775c", "leaf")
OLIVE = _ramp("0a120a 121e0e 1c2c14 283c1a 364e20 466226", "leaf")
STEM = _ramp("08141a 0d2026 142e30 1c3e3c 264f48")
STONE = _ramp("08091a 0f1024 171931 21243f 2d314f 3b4062")
LITTER = _ramp("0c0810 170e12 241714 332016 442b1a")
LOG = _ramp("060409 0d080c 160e10 211514 2d1d18 3b2720")
WATER = _ramp("07182e 09203c 0b2849 0d3056 10396a 0d3056 0b2849 09203c", "water")
MOON = _ramp("3a3c9e 6a6cc8 a3a4e6 d6d7f8 f4f5ff", "moon")
STAR = _ramp("3a3f9c 7a84e0 c6d0ff", "moon")
FIREFLY = _ramp("12321c 2e7a30 66d24a a8f070 e4ffb8", "glow")
STAG = _ramp("5a7fb0 9cc0de cfe6f6 f4fbff", "glow")
EYE = _ramp("6a3a0a d89a28 ffe08a", "glow")
# Seven colours and two spare, holding the same nine palette slots the old beetle colours did.
RAINBOW = _ramp("e8483c f08a2c f6d63c 5cc84c 3caae0 4a5ce0 9a58d8 000000 000000", "rainbow")
MOTH = _ramp("6a6890 a8a6c8 d8d6ee", "glow")
SPORE = _ramp("3a4a7a 7a8ac0 c0d0f0", "glow")

MOSS = [
    _ramp("06201e 0b312b 12443a 1c5a49"),  # teal
    _ramp("061c2a 0a2c3c 113f50 1b5566"),  # blue-green
    _ramp("10200e 1a3114 28461a 3a5e22"),  # olive
    _ramp("06220f 0b3418 134a22 1f642e"),  # deep green
    _ramp("161434 201d47 2c295d 3b3876"),  # slate violet
    _ramp("241a10 362614 4a3518 62481e"),  # rust
]
MYC = [
    _ramp("10183c 1e3280 3a5cc4 6c96ec b4d4ff", "glow"),  # ice blue
    _ramp("181040 2e2080 5238bc 8a6cea c8b0ff", "glow"),  # violet
    _ramp("08242c 0e4450 1c7a84 36b8b2 90f2dc", "glow"),  # teal
    _ramp("2a1030 501e56 8a3880 c060a8 f0a8d0", "glow"),  # rose
    _ramp("2a1a0a 503010 8a5a1c d09430 f8d078", "glow"),  # amber
    _ramp("1e2434 384460 687892 a4b4cc e4eef8", "glow"),  # pale
]
CAP = [
    _ramp("1e3488 3a62d0 6c96f4 b4d0ff", "glow"),  # blue
    _ramp("0e5060 1e9098 48d4c8 a8f8e4", "glow"),  # cyan
    _ramp("3c2288 6c44c8 a078ec d4bcff", "glow"),  # violet
    _ramp("60380c a86818 e0a038 fce090", "glow"),  # amber
    _ramp("5a1418 98282a c85040 e89070", "scene"),  # red
    _ramp("4a4634 80785c b8ae8c e8e0c4", "scene"),  # pale tan
]
FLOWER = [
    _ramp("2a48a8 5a84e8 a0c4ff", "glow"),  # bluebell
    _ramp("4438b0 7a68e4 b4a8ff", "glow"),  # periwinkle
    _ramp("6a2c98 a258d0 d49cf0"),  # violet
    _ramp("8a2a60 c4548e ec98bc"),  # rose
    _ramp("8c2428 c44438 ec7c5c"),  # red
    _ramp("9a6a14 d8a430 f4d878"),  # amber
    _ramp("8a8c9c c8cad8 f4f4fc"),  # cream
    _ramp("1c7a78 3cb4a8 8ce8d4", "glow"),  # teal
]

# Animals are appended here, after everything the cached scenery refers to.
FOX = _ramp("3a1a0c 7a3414 b85c20 d8d0c0", "animal")
HARE = _ramp("2a2428 5a4c4c 8e7e76 c8bcb0", "animal")
FROG = _ramp("0a2010 1c5024 3a8434 9ccc58", "animal")
BIRD = _ramp("20244c 4c6aa8 d89a3c ece0c8", "animal")
OWL = _ramp("1a1410 46362a 8a7254", "animal")
BAT = _ramp("07061a 14102c 221c40", "animal")
SNOW = _ramp("20284a 343f66 465480")
# Green coats, kept apart from the leaf colours so autumn can't turn an animal brown.
COAT_GREEN = _ramp("0c1e14 184d3c 2e775c 283c1a 466226", "animal")

_n = len(_colors)
assert _n <= 256, f"palette overflow: {_n}"
_colors += [(0, 0, 0)] * (256 - _n)
_kinds += ["scene"] * (256 - _n)
_ramp_of += [None] * (256 - _n)


def painted(index):
    """An entry's colour as painted, before any time of day or darkness."""
    return _colors[index]


def luminance(index):
    r, g, b = _colors[index]
    return 0.3 * r + 0.59 * g + 0.11 * b


def _shift_lut(steps):
    lut = bytearray(256)
    for i in range(256):
        ramp = _ramp_of[i]
        lut[i] = ramp[i - ramp.start + steps] if ramp else i
    return bytes(lut)


LIT = _shift_lut(1)  # one step brighter inside the same ramp: shafts of light
LIT2 = _shift_lut(2)  # two steps: firefly halos
DARK = _shift_lut(-1)  # one step darker: shadows

# Natural daylight, ramp by ramp: multipliers of a colour's brightness, plus a little ambient light.
# Ramps not listed simply keep their own painted colours, brightened.
_NATURAL_DAY = {}
for _ramp_obj, _look in (
    (SKY, (1.05, 1.35, 1.6, 16, 34, 62)),
    (FAR, (1.0, 1.25, 1.2, 10, 16, 18)),
    (CANOPY, (0.8, 1.35, 0.7, 2, 6, 2)),
    (VIOLET, (1.3, 1.05, 0.6, 4, 3, 0)),
    (TEAL, (0.8, 1.45, 0.6, 2, 6, 1)),
    (TRUNK, (1.5, 1.1, 0.75, 6, 3, 1)),
    (STONE, (1.05, 1.05, 1.1, 4, 4, 5)),
    (SNOW, (1.45, 1.5, 1.6, 8, 8, 10)),
):
    for _i in range(_ramp_obj.start, _ramp_obj.start + _ramp_obj.n):
        _NATURAL_DAY[_i] = _look

THEME_GAINS = {
    "deepwoods": (1.00, 1.00, 1.00),
    "teal": (0.55, 1.08, 1.02),
    "moonlit": (0.55, 0.85, 1.40),
    "amber": (1.25, 0.95, 0.32),
    "violet": (1.15, 0.72, 1.22),
}


MONO_TINTS = {
    "deepwoods": (0.70, 0.95, 1.75),
    "teal": (0.45, 1.10, 1.30),
    "moonlit": (0.60, 0.90, 1.70),
    "amber": (1.55, 0.95, 0.30),
    "violet": (1.35, 0.65, 1.70),
}


def _mono_tint(theme):
    """A palette's signature colour, scaled so that tinting by it leaves brightness unchanged."""
    r, g, b = MONO_TINTS.get(theme, MONO_TINTS["deepwoods"])
    weight = 0.3 * r + 0.59 * g + 0.11 * b
    return r / weight, g / weight, b / weight


def compute(theme, darkness, glow, saturation, daylight, warmth, water_phase=0, autumn=0.0, winter=0.0, spring=0.0,
            ice=0.0, aurora=0.0, aurora_phase=0.0, flash=0.0, natural=0.0):
    """Return (r, g, b) for all 256 entries under the given look, season and weather."""
    gr, gg, gb = THEME_GAINS.get(theme, THEME_GAINS["deepwoods"])
    # The more natural, the less a palette's tint takes over everything.
    soften = 1.0 - 0.75 * natural
    gr, gg, gb = 1 + (gr - 1) * soften, 1 + (gg - 1) * soften, 1 + (gb - 1) * soften
    scene_gain = 1.9 ** (1.0 - 2.0 * darkness)  # darkness 0.5 = as painted
    glow_gain = (0.45 + 0.75 * glow) * (1.35 ** (1.0 - 2.0 * darkness))
    sky_mid = _colors[SKY[6]]
    out = []
    for i, (r, g, b) in enumerate(_colors):
        kind = _kinds[i]
        if kind == "water":
            ramp = WATER
            r, g, b = _colors[ramp.start + (i - ramp.start + water_phase) % ramp.n]
            if ice > 0:  # as the pond freezes it stills and pales
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                r += (lum * 1.5 + 22 - r) * ice
                g += (lum * 1.8 + 30 - g) * ice
                b += (lum * 2.2 + 44 - b) * ice
        elif kind == "leaf":
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            if autumn > 0:  # gold and russet; a little of each ramp keeps the forest from going flat
                warm = 1.0 if i % 3 else 0.7
                r += (lum * 2.5 * warm + 8 - r) * autumn
                g += (lum * 1.15 * warm - g) * autumn
                b += (lum * 0.28 - b) * autumn
            if winter > 0:  # bare and grey-brown
                r += (lum * 0.95 - r) * winter
                g += (lum * 0.88 - g) * winter
                b += (lum * 0.8 - b) * winter
            if spring > 0:  # the fresh green of new leaves
                r += (r * 0.85 - r) * spring
                g += (g * 1.18 - g) * spring
                b += (b * 0.85 - b) * spring
        elif kind == "sky" and aurora > 0:
            # Slow curtains of green and violet over the winter sky.
            sway = 0.5 + 0.5 * math.sin(aurora_phase + (i - SKY.start) * 0.9)
            r += (sway * 26 + 16) * aurora
            g += ((1 - sway) * 90 + 28) * aurora
            b += (sway * 54 + 18) * aurora
        if daylight > 0:
            if kind == "moon":
                # The moon and stars dissolve into the sky at sunrise.
                f = min(1.0, daylight * 2.5)
                r, g, b = r + (sky_mid[0] - r) * f, g + (sky_mid[1] - g) * f, b + (sky_mid[2] - b) * f
                kind = "sky"
            if kind == "sky":
                # Daylight is pale gold-green, filtered through leaves.
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                day = (lum * 1.25 + 6, lum * 1.55 + 10, lum * 0.7 + 3)
            else:
                # Under the deep canopy: greener, warmer, never bright.
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                day = (lum * 1.08 + 3, lum * 1.28 + 5, lum * 0.5 + 1)
            if natural > 0:
                # Natural light: each thing its own colour, rather than all of them one tint.
                look = _NATURAL_DAY.get(i)
                if look:
                    own = (lum * look[0] + look[3], lum * look[1] + look[4], lum * look[2] + look[5])
                else:
                    own = (r * 1.35, g * 1.35, b * 1.35)
                day = (day[0] + (own[0] - day[0]) * natural, day[1] + (own[1] - day[1]) * natural,
                       day[2] + (own[2] - day[2]) * natural)
            if kind == "glow":
                # Luminous things only really show in the dark.
                f = 1.0 - 0.45 * daylight
                r, g, b = r * f, g * f, b * f
            elif kind not in ("animal", "rainbow"):  # animals keep their own colours, or they would be camouflaged
                mix = min(1.0, daylight * 1.3)
                boost = 1.0 + 0.3 * daylight
                r = (r + (day[0] - r) * mix) * boost
                g = (g + (day[1] - g) * mix) * boost
                b = (b + (day[2] - b) * mix) * boost
        if warmth > 0:
            r *= 1.0 + 0.45 * warmth
            g *= 1.0 - 0.02 * warmth
            b *= 1.0 - 0.32 * warmth
        gain = glow_gain if _kinds[i] in ("glow", "moon") and daylight < 0.3 else scene_gain
        if kind == "rainbow":
            gain = 0.75 + 0.25 * scene_gain  # it is light, not scenery: the darkness setting barely touches it
        if kind == "water":
            gain = (scene_gain + glow_gain) / 2 * 0.8
        tint = 0.0 if kind == "rainbow" else 0.5 if kind in ("glow", "moon") else 1.0
        r *= gain * (1 + (gr - 1) * tint)
        g *= gain * (1 + (gg - 1) * tint)
        b *= gain * (1 + (gb - 1) * tint)
        lum = 0.3 * r + 0.59 * g + 0.11 * b
        if saturation >= 1.0:
            r = lum + (r - lum) * saturation
            g = lum + (g - lum) * saturation
            b = lum + (b - lum) * saturation
        else:
            # Below full colour, fade toward one colour: this palette's own, at the same brightness.
            # The brightest things pale a little, so the moon still looks like light.
            tr, tg, tb = _mono_tint(theme)
            pale = max(0.0, (lum - 150.0) / 105.0) * 0.6
            mr = lum * (tr + (1.0 - tr) * pale)
            mg = lum * (tg + (1.0 - tg) * pale)
            mb = lum * (tb + (1.0 - tb) * pale)
            r = mr + (r - mr) * saturation
            g = mg + (g - mg) * saturation
            b = mb + (b - mb) * saturation
        if flash > 0:
            r, g, b = r + (r * 2.4 + 34) * flash, g + (g * 2.4 + 38) * flash, b + (b * 2.4 + 52) * flash
        out.append((min(255, max(0, int(r))), min(255, max(0, int(g))), min(255, max(0, int(b)))))
    return out
