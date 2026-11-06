"""A tiny 8-bit drawing surface: indexed pixels from the wallpaper's own palette, and a 5x7 font."""

from . import palette
from .palette import CANOPY, FLECK, SKY, TEAL

_FONT = (
    "0000000000 00005F0000 0007000700 147F147F14 242A7F2A12 2313086462 3649552250 0005030000 001C224100 0041221C00"
    " 14083E0814 08083E0808 0050300000 0808080808 0060600000 2010080402 3E5149453E 00427F4000 4261514946 2141454B31"
    " 1814127F10 2745454539 3C4A494930 0171090503 3649494936 064949291E 0036360000 0056360000 0814224100 1414141414"
    " 0041221408 0201510906 324979413E 7E1111117E 7F49494936 3E41414122 7F4141221C 7F49494941 7F09090901 3E4149497A"
    " 7F0808087F 00417F4100 2040413F01 7F08142241 7F40404040 7F020C027F 7F0408107F 3E4141413E 7F09090906 3E4151215E"
    " 7F09192946 4649494931 01017F0101 3F4040403F 1F2040201F 3F4038403F 6314081463 0708700807 6151494543 007F414100"
    " 0204081020 0041417F00 0402010204 4040404040 0001020400 2054545478 7F48444438 3844444420 384444487F 3854545418"
    " 087E090102 0C5252523E 7F08040478 00447D4000 2040443D00 7F10284400 00417F4000 7C04180478 7C08040478 3844444438"
    " 7C14141408 081414187C 7C08040408 4854545420 043F444020 3C4040207C 1C2040201C 3C4030403C 4428102844 0C5050503C"
    " 4464544C44 0008364100 00007F0000 0041360800 0201020402"
)
GLYPHS = {}
for _i, _word in enumerate(_FONT.split()):
    _columns = bytes.fromhex(_word)
    GLYPHS[chr(32 + _i)] = [(x, y) for x in range(5) for y in range(7) if _columns[x] >> y & 1]
GLYPH_W = 6
GLYPH_H = 9


def text_width(s, scale=1):
    return len(s) * GLYPH_W * scale


def wrap(text, chars):
    lines = []
    line = ""
    for word in text.split():
        if line and len(line) + 1 + len(word) > chars:
            lines.append(line)
            line = word
        else:
            line = f"{line} {word}" if line else word
    if line:
        lines.append(line)
    return lines


class Screen:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.px = bytearray(width * height)
        self.buf = bytearray(width * height * 4)
        self.lut_r = self.lut_g = self.lut_b = bytes(256)
        self.clip = (0, 0, width, height)

    def set_palette(self, colors):
        self.lut_r = bytes(c[0] for c in colors)
        self.lut_g = bytes(c[1] for c in colors)
        self.lut_b = bytes(c[2] for c in colors)

    def flush(self):
        self.buf[0::4] = self.px.translate(self.lut_b)
        self.buf[1::4] = self.px.translate(self.lut_g)
        self.buf[2::4] = self.px.translate(self.lut_r)
        self.buf[3::4] = b"\xff" * (self.w * self.h)

    def set_clip(self, x=0, y=0, w=None, h=None):
        self.clip = (max(0, x), max(0, y), min(self.w, x + (self.w if w is None else w)), min(self.h, y + (self.h if h is None else h)))

    def pixel(self, x, y, c):
        x0, y0, x1, y1 = self.clip
        if x0 <= x < x1 and y0 <= y < y1:
            self.px[y * self.w + x] = c

    def rect(self, x, y, w, h, c):
        x0, y0, x1, y1 = self.clip
        a, b = max(x, x0), min(x + w, x1)
        if a >= b:
            return
        fill = bytes((c,)) * (b - a)
        for row in range(max(y, y0), min(y + h, y1)):
            o = row * self.w
            self.px[o + a:o + b] = fill

    def dither(self, x, y, w, h, c1, c2):
        """A checkerboard: the 8-bit way to mix two colours."""
        x0, y0, x1, y1 = self.clip
        a, b = max(x, x0), min(x + w, x1)
        if a >= b:
            return
        run = b - a
        even = (bytes((c1, c2)) * (run // 2 + 1))[:run]
        odd = (bytes((c2, c1)) * (run // 2 + 1))[:run]
        for row in range(max(y, y0), min(y + h, y1)):
            o = row * self.w
            self.px[o + a:o + b] = even if (row + a) & 1 == 0 else odd

    def text(self, x, y, s, c, shadow=None, scale=1):
        for ch in s:
            glyph = GLYPHS.get(ch, GLYPHS["?"])
            for gx, gy in glyph:
                for sy in range(scale):
                    for sx in range(scale):
                        if shadow is not None:
                            self.pixel(x + gx * scale + sx + 1, y + gy * scale + sy + 1, shadow)
            for gx, gy in glyph:
                for sy in range(scale):
                    for sx in range(scale):
                        self.pixel(x + gx * scale + sx, y + gy * scale + sy, c)
            x += GLYPH_W * scale

    def rows(self, rows, x, y, colors, flip=False, scale=1):
        """Draw an ASCII sprite, as used by the animals."""
        for ry, row in enumerate(rows):
            n = len(row)
            for rx, ch in enumerate(row):
                if ch == ".":
                    continue
                c = colors.get(ch)
                if c is None:
                    continue
                px = x + (n - 1 - rx if flip else rx) * scale
                py = y + ry * scale
                if scale == 1:
                    self.pixel(px, py, c)
                else:
                    self.rect(px, py, scale, scale, c)

    def panel(self, x, y, w, h, fill=None, border=None):
        """A chunky window frame with notched corners, like a menu from an old cartridge."""
        fill = SKY[1] if fill is None else fill
        border = FLECK[1] if border is None else border
        self.rect(x + 2, y, w - 4, h, CANOPY[0])
        self.rect(x, y + 2, w, h - 4, CANOPY[0])
        self.rect(x + 1, y + 1, w - 2, h - 2, CANOPY[0])
        self.rect(x + 2, y + 1, w - 4, 1, border)
        self.rect(x + 2, y + h - 2, w - 4, 1, border)
        self.rect(x + 1, y + 2, 1, h - 4, border)
        self.rect(x + w - 2, y + 2, 1, h - 4, border)
        self.rect(x + 3, y + 3, w - 6, h - 6, fill)

    def meter(self, x, y, blocks, value, on=None, off=None, height=5):
        """A segmented bar. value is 0 to 1."""
        on = TEAL[5] if on is None else on
        off = SKY[0] if off is None else off
        lit = int(value * blocks + 0.5)
        for k in range(blocks):
            self.rect(x + k * 4, y, 3, height, on if k < lit else off)


def ui_palette(theme, saturation, natural=0.0):
    """The wallpaper's colours at night, kept bright enough to read."""
    return palette.compute(theme, 0.42, 0.85, max(0.7, min(1.2, saturation)), 0.0, 0.0, natural=natural)
