"""The app icon: a curled seedling pushing out of the soil at night, with a firefly close by.

The art is drawn by hand on three grids, so it stays crisp at every size. Larger
sizes are whole-number enlargements. The tile behind it matches the menu's windows.
"""

import math
import os

from . import palette
from .palette import CANOPY, FIREFLY, FLECK, LEAF, LITTER, SKY, TEAL

# icon size in screen pixels -> (art grid, enlargement)
SIZES = {16: (16, 1), 22: (22, 1), 24: (24, 1), 32: (32, 1), 48: (24, 2), 64: (32, 2), 128: (32, 4), 256: (32, 8)}
APP_ID = "bloom-wallpaper"

# S stem, s its shaded side, T the bright tip of the curl, L leaf, l leaf in shadow.
SEEDLING_LARGE = (
    "..............SSSSS.........",
    "...........SSSSSSSSSSS......",
    "..........SSs........SSS....",
    ".........SSs...........SS...",
    "........SSs...SSSSS.....SS..",
    "........SS..SSS...SS....sS..",
    ".......SSs..SS.....SS...sS..",
    ".......SS..SS...TT..S...sS..",
    ".......SS..SS..T..T.S...sS..",
    ".......SS..sS..T....S..sS...",
    ".......SS...SS.TTTTSs.sSS...",
    ".......SS....S.......sSs....",
    ".......SS....SSs...sSSs.....",
    ".......SSs....sSSSSSs.......",
    "........SS..................",
    "........SS..................",
    "..L.....SS....LL............",
    ".LLLL...SS..LLLLL...........",
    ".lLLLL..SS.LLLLLl...........",
    "..llLLLLSSLLLll.............",
    "....lllLSSLll...............",
    "........SS..................",
    "........SS..................",
)
SEEDLING_MEDIUM = (
    "..........SSSS......",
    "........SSSSSSSS....",
    ".......SS......SS...",
    "......SS..SSS...SS..",
    "......S..SS.SS..sS..",
    ".....SS..S.T..S..S..",
    ".....SS..S.TT.S..S..",
    ".....SS..sS..Ss.sS..",
    ".....SS...SSSs.sS...",
    ".....SS.......sS....",
    ".....SS...sSSSs.....",
    ".....SS.............",
    ".L...SS..LL.........",
    "LLLL.SS.LLLL........",
    ".lLLLSSLLLl.........",
    "...lLSSLl...........",
    ".....SS.............",
    ".....SS.............",
)
SEEDLING_SMALL = (
    "....SSSSS.....",
    "...SS...SS....",
    "..SS.SS..SS...",
    "..S.S..T..S...",
    "..S.S.TT.sS...",
    "..S..SS..S....",
    "..S.....S.....",
    "..S..SSS......",
    "L.S.LL........",
    "LLSLLl........",
    ".lSl..........",
    "..S...........",
)
INK = {"S": FIREFLY[2], "s": LEAF[5], "T": FIREFLY[4], "L": FIREFLY[1], "l": LEAF[4]}


def icon_grid(n):
    """A square of palette indices (0 = transparent): frame, night sky, soil, seedling, firefly."""
    grid = [[0] * n for _ in range(n)]

    def put(x, y, c):
        if 0 <= x < n and 0 <= y < n:
            grid[y][x] = c

    # The tile: chunky notched corners like the menu's windows, deep sky lightening around the firefly.
    frame = 1 if n < 24 else 2
    notch = 1 if n < 24 else 2
    glow_x, glow_y = n * 0.80, n * 0.70
    for y in range(n):
        for x in range(n):
            corner = min(x, n - 1 - x) + min(y, n - 1 - y)
            if corner < notch:
                continue
            edge = min(x, n - 1 - x, y, n - 1 - y)
            if edge == 0 or corner == notch:
                put(x, y, CANOPY[0])
            elif edge < frame or corner < notch + frame:
                put(x, y, FLECK[1])
            else:
                glow = 1 - math.hypot(x - glow_x, y - glow_y) / (n * 0.62)
                put(x, y, SKY[int(max(0.0, glow) * 3.4 + 0.1 + ((x ^ y) & 1) * 0.5)])
    inner = frame

    # Soil, heaped a little where the seedling has pushed through, under a skin of moss.
    art, left = (SEEDLING_LARGE, 2) if n >= 32 else ((SEEDLING_MEDIUM, 1 if n == 22 else 2) if n >= 22 else (SEEDLING_SMALL, 1))
    stem_at = left + (9 if n >= 32 else 6 if n >= 22 else 2)
    depth = max(2, n // 7)
    floor = n - inner
    for x in range(inner, n - inner):
        hump = max(0.0, 1 - abs(x - stem_at) / (n * 0.26))
        top = floor - depth - int(hump * n * 0.07 + 0.5)
        for y in range(top, floor):
            if y == top:
                put(x, y, TEAL[4] if (x * 7) & 3 else TEAL[6])
            else:
                put(x, y, LITTER[2] if (x + y * 3) % 5 else LITTER[3])

    # The seedling itself, its stem running down into the soil.
    bottom = floor - depth + (1 if n >= 22 else 0)
    top_row = bottom - len(art)
    for row, line in enumerate(art):
        for col, ch in enumerate(line):
            if ch != ".":
                put(left + col, top_row + row, INK[ch])

    # A firefly mid-flash, the same cross it makes on the wallpaper, with a dotted flight path.
    fx, fy = int(glow_x), int(glow_y)
    put(fx, fy, FIREFLY[4])
    if n >= 22:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(fx + dx, fy + dy, FIREFLY[3])
    if n >= 32:
        for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            put(fx + dx, fy + dy, FIREFLY[1])
        for dx, dy in ((-4, 1), (-6, 0), (-7, -2)):
            put(fx + dx, fy + dy, FIREFLY[1])
    return grid


def write_png(path, size):
    """Write one size of the icon. Needs only pycairo."""
    import cairo

    art, scale = SIZES[size]
    grid = icon_grid(art)
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surface)
    ctx.set_antialias(cairo.Antialias.NONE)
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c:
                r, g, b = palette.painted(c)
                ctx.set_source_rgb(r / 255.0, g / 255.0, b / 255.0)
                ctx.rectangle(x * scale, y * scale, scale, scale)
                ctx.fill()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    surface.write_to_png(path)


def icon_paths(data_home):
    return {size: os.path.join(data_home, "icons", "hicolor", f"{size}x{size}", "apps", f"{APP_ID}.png") for size in SIZES}


def install(data_home):
    for size, path in icon_paths(data_home).items():
        write_png(path, size)


def uninstall(data_home):
    for path in icon_paths(data_home).values():
        if os.path.exists(path):
            os.unlink(path)
