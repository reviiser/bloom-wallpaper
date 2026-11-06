#!/usr/bin/python3
"""Draws the app's icon and its TV banner, from the same hand-placed grid the desktop icon uses."""
import os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from PIL import Image
from wallbloom import icon, palette, pixelui
out = os.path.join(ROOT, "android", "app", "res", "drawable-nodpi")
def render(n, s):
    g = icon.icon_grid(n)
    im = Image.new("RGBA", (n * s, n * s), (0, 0, 0, 0))
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c:
                im.paste(palette.painted(c) + (255,), (x * s, y * s, (x + 1) * s, (y + 1) * s))
    return im
render(32, 6).save(os.path.join(out, "icon.png"))
banner = Image.new("RGB", (320, 180), palette.painted(palette.SKY[0]))
for y in range(180):
    for x in range(320):
        banner.putpixel((x, y), palette.painted(palette.SKY[min(3, int((y / 180.0) * 3 + ((x ^ y) & 1) * 0.5))]))
ic = render(32, 3)
banner.paste(ic, (112, 8), ic)
def text(s, y, scale, color):
    x = (320 - len(s) * 6 * scale) // 2
    for ch in s:
        for gx, gy in pixelui.GLYPHS.get(ch, []):
            banner.paste(color, (x + gx * scale, y + gy * scale, x + (gx + 1) * scale, y + (gy + 1) * scale))
        x += 6 * scale
text("BLOOM", 110, 5, palette.painted(palette.FIREFLY[3]))
text("BY REVIISER", 160, 2, palette.painted(palette.FLECK[3]))
banner.save(os.path.join(out, "banner.png"))
