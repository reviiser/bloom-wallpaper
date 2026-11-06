"""Layered indexed canvas with cheap partial updates.

Planes, top to bottom: cap (snow on things), fg, life, snow, leaf, myc, moss, cloud, base.
A separate shade plane darkens whatever ground lies under a shadow. A value of 0 is transparent.
comp   = flattened planes
frame  = comp with light shafts applied
buf    = frame through the palette, as the BGRx image handed to cairo
Moving things (fireflies, moths) never touch the planes. They are overlaid
straight onto buf and diffed frame to frame, so a still pixel costs nothing.
"""

from . import palette

TILE_SHIFT = 3  # dirty tracking granularity: 8x8 art pixels


class Canvas:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        n = width * height
        self.base = bytearray(n)
        self.fg = bytearray(n)
        self.life = bytearray(n)
        self.life_z = bytearray(n)
        self.myc = bytearray(n)
        self.moss = bytearray(n)
        self.snow = bytearray(n)
        self.cap = bytearray(n)
        self.cloud = bytearray(n)
        self.leaf = bytearray(n)  # fallen leaves, lying over the moss and under any snow
        self.shade = bytearray(n)  # how many things are casting a shadow here
        self.comp = bytearray(n)
        self.frame = bytearray(n)
        self.mask = bytearray(n)
        self.buf = bytearray(n * 4)
        self.pal4 = [bytes((0, 0, 0, 255))] * 256
        self.lut_b = self.lut_g = self.lut_r = bytes(256)
        self.spans = []
        self.overlay = {}
        self.dirty = set()
        self.dirty_all = True
        self.tiles_w = (width >> TILE_SHIFT) + 1

    def set_palette(self, colors):
        self.pal4 = [bytes((b, g, r, 255)) for r, g, b in colors]
        self.lut_r = bytes(c[0] for c in colors)
        self.lut_g = bytes(c[1] for c in colors)
        self.lut_b = bytes(c[2] for c in colors)
        self.repaint()

    def repaint(self):
        """Re-translate the entire frame through the palette."""
        self.buf[0::4] = self.frame.translate(self.lut_b)
        self.buf[1::4] = self.frame.translate(self.lut_g)
        self.buf[2::4] = self.frame.translate(self.lut_r)
        self.buf[3::4] = b"\xff" * (self.w * self.h)
        overlay, self.overlay = self.overlay, {}
        for i, v in overlay.items():
            self.buf[i * 4:i * 4 + 4] = self.pal4[v]
        self.overlay = overlay
        self.dirty_all = True

    def repaint_rect(self, x0, y0, x1, y1):
        """Re-translate a rectangle, used for colour-cycling the pond."""
        w = self.w
        n = x1 - x0
        frame = self.frame
        buf = self.buf
        lb, lg, lr = self.lut_b, self.lut_g, self.lut_r
        for y in range(y0, y1):
            a = y * w + x0
            row = frame[a:a + n]
            o = a * 4
            e = o + n * 4
            buf[o:e:4] = row.translate(lb)
            buf[o + 1:e:4] = row.translate(lg)
            buf[o + 2:e:4] = row.translate(lr)
        # Whatever is standing in front of this rectangle stays there.
        pal4 = self.pal4
        for i, v in self.overlay.items():
            if y0 <= i // w < y1 and x0 <= i % w < x1:
                buf[i * 4:i * 4 + 4] = pal4[v]
        shift = TILE_SHIFT
        tw = self.tiles_w
        add = self.dirty.add
        for ty in range(y0 >> shift, ((y1 - 1) >> shift) + 1):
            for tx in range(x0 >> shift, ((x1 - 1) >> shift) + 1):
                add(ty * tw + tx)

    def flatten(self):
        """Rebuild comp from the planes. Only needed when loading a world."""
        comp = self.comp
        fg, life, myc, moss, base, snow, cap, cloud = self.fg, self.life, self.myc, self.moss, self.base, self.snow, self.cap, self.cloud
        shade = self.shade
        leaf = self.leaf
        dark = palette.DARK
        for i in range(len(comp)):
            v = cap[i] or fg[i] or life[i]
            if not v:
                v = snow[i] or leaf[i] or myc[i] or moss[i] or cloud[i] or base[i]
                if shade[i]:
                    v = dark[v]
            comp[i] = v
        self.apply_light(self.spans)

    def apply_light(self, spans):
        """spans: list of (y, x0, x1) that get the LIT treatment. Repaints everything."""
        self.spans = spans
        frame = self.frame
        comp = self.comp
        frame[:] = comp
        mask = self.mask
        mask[:] = bytes(len(mask))
        w = self.w
        lit = palette.LIT
        for y, x0, x1 in spans:
            a = y * w + x0
            b = y * w + x1
            frame[a:b] = comp[a:b].translate(lit)
            mask[a:b] = b"\x01" * (b - a)
        self.repaint()

    def refresh(self, i):
        """A plane changed at pixel i: push it through to buf."""
        v = self.cap[i] or self.fg[i] or self.life[i]
        if not v:  # the ground, where shadows fall
            v = self.snow[i] or self.leaf[i] or self.myc[i] or self.moss[i] or self.cloud[i] or self.base[i]
            if self.shade[i]:
                v = palette.DARK[v]
        if v == self.comp[i]:
            return
        self.comp[i] = v
        if self.mask[i]:
            v = palette.LIT[v]
        self.frame[i] = v
        if i not in self.overlay:
            self.buf[i * 4:i * 4 + 4] = self.pal4[v]
        w = self.w
        self.dirty.add(((i // w) >> TILE_SHIFT) * self.tiles_w + ((i % w) >> TILE_SHIFT))

    def commit_overlay(self, new):
        """Diff the moving-things overlay against the last frame."""
        old = self.overlay
        if not new and not old:
            return
        buf = self.buf
        pal4 = self.pal4
        frame = self.frame
        dirty = self.dirty
        w = self.w
        tw = self.tiles_w
        for i in old:
            if i not in new:
                o = i * 4
                buf[o:o + 4] = pal4[frame[i]]
                dirty.add(((i // w) >> TILE_SHIFT) * tw + ((i % w) >> TILE_SHIFT))
        for i, v in new.items():
            if old.get(i) != v:
                o = i * 4
                buf[o:o + 4] = pal4[v]
                dirty.add(((i // w) >> TILE_SHIFT) * tw + ((i % w) >> TILE_SHIFT))
        self.overlay = new

    def take_dirty(self):
        """Return dirty rectangles in art pixels, or None when everything changed."""
        if self.dirty_all:
            self.dirty_all = False
            self.dirty.clear()
            return None
        if not self.dirty:
            return []
        tw = self.tiles_w
        size = 1 << TILE_SHIFT
        rects = []
        run_start = prev = None
        for t in sorted(self.dirty):
            if prev is not None and t == prev + 1 and t % tw != 0:
                prev = t
                continue
            if prev is not None:
                rects.append(self._tile_rect(run_start, prev, tw, size))
            run_start = prev = t
        rects.append(self._tile_rect(run_start, prev, tw, size))
        self.dirty.clear()
        return rects

    @staticmethod
    def _tile_rect(first, last, tw, size):
        ty = first // tw
        return ((first % tw) * size, ty * size, (last - first + 1) * size, size)
