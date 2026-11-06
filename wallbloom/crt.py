"""An old tube over the picture: the beam's smear, phosphor dots, dark rounded edges and a slow hum bar.

The smear is what old pixel artists counted on. A composite signal can't hold sharp changes
from one pixel to the next along a line, so checkerboard dithering melts into a solid
in-between colour. So the art is first blurred along each row, at art resolution, then
enlarged smoothly and seen through a shadow mask of staggered red, green and blue dots.

The mask never changes, so it is composed once and multiplied over whatever is being redrawn.
"""

import math

import cairo

BAR_HEIGHT = 56
BAR_SPEED = 3  # screen pixels per step: a slow roll, the way mains hum drifts on a real set
BAR_EVERY = 4  # frames between steps. Too faint for the eye to mind, and it saves most of the cost.
SMEAR_MARGIN = 2  # art pixels: how far one changed pixel's smear reaches, so how much around it to redraw


class SoftPicture:
    """The art as the beam draws it: each pixel bleeding into its neighbours along the row."""

    def __init__(self, picture, width, height):
        self.picture = picture
        self.width = width
        self.height = height
        self.surface = cairo.ImageSurface(cairo.FORMAT_RGB24, width, height)
        self.ctx = cairo.Context(self.surface)

    def update(self, rects, strength):
        """rects are in art pixels, or None for everything."""
        self.picture.mark_dirty()
        ctx = self.ctx
        # At full smear this is the classic 1-2-1 filter, which turns a checkerboard into one flat colour.
        blend = min(1.0, strength * 1.6)
        ctx.save()
        if rects is not None:
            for x, y, w, h in rects:
                ctx.rectangle(x, y, w, h)
            ctx.clip()
        ctx.set_operator(cairo.Operator.SOURCE)
        ctx.set_source_surface(self.picture, 0, 0)
        ctx.paint()
        ctx.set_operator(cairo.Operator.OVER)
        ctx.set_source_surface(self.picture, -1, 0)
        ctx.paint_with_alpha(blend / 3.0)
        ctx.set_source_surface(self.picture, 1, 0)
        ctx.paint_with_alpha(blend / 4.0)
        # Phosphors are bright: this makes up for the light the mask takes away, and lets the brightest
        # things swell. Doing it here, on the small image, saves a second smooth enlargement on screen.
        boost = 1.1 * strength
        ctx.set_operator(cairo.Operator.ADD)
        ctx.set_source_surface(self.picture, 0, 0)
        ctx.paint_with_alpha(boost * (1.0 - blend / 2.0))
        ctx.set_source_surface(self.picture, -1, 0)
        ctx.paint_with_alpha(boost * blend / 4.0)
        ctx.set_source_surface(self.picture, 1, 0)
        ctx.paint_with_alpha(boost * blend / 4.0)
        ctx.restore()
        self.surface.flush()


def _dots(strength, scale):
    """One repeat of the shadow mask: columns of red, green and blue dots, every other triad set half a step lower."""
    tall = 6 * scale  # a whole number of both dot rows and art rows
    surface = cairo.ImageSurface(cairo.FORMAT_RGB24, 6, tall)
    stride = surface.get_stride()
    data = surface.get_data()
    off = 1.0 - 0.72 * strength  # how much of the other two colours gets past a phosphor
    gap = 1.0 - 0.58 * strength  # the dark mesh between rows of dots
    for y in range(tall):
        # A faint trace of each art row as a beam line, under the dots.
        beam = 1.0 - 0.22 * strength * (1.0 - math.sin(math.pi * ((y % scale) + 0.5) / scale))
        for x in range(6):
            triad = x // 3
            row = (y + (3 if triad else 0)) % 6
            mesh = gap if row in (2, 5) else 1.0
            gains = [off, off, off]
            gains[x % 3] = 1.0
            offset = y * stride + x * 4
            data[offset] = int(gains[2] * mesh * beam * 255)
            data[offset + 1] = int(gains[1] * mesh * beam * 255)
            data[offset + 2] = int(gains[0] * mesh * beam * 255)
            data[offset + 3] = 255
    surface.mark_dirty()
    return surface


def build_mask(width, height, strength, scale, offset_y):
    """The whole fixed pattern for one screen."""
    mask = cairo.ImageSurface(cairo.FORMAT_RGB24, width, height)
    ctx = cairo.Context(mask)
    tile = cairo.SurfacePattern(_dots(strength, scale))
    tile.set_extend(cairo.Extend.REPEAT)
    tile.set_filter(cairo.Filter.NEAREST)
    matrix = cairo.Matrix()
    matrix.translate(0, offset_y)
    tile.set_matrix(matrix)
    ctx.set_source(tile)
    ctx.paint()
    # The picture falls off toward the edges of the glass.
    ctx.save()
    ctx.translate(width / 2.0, height / 2.0)
    ctx.scale(width / 2.0, height / 2.0)
    falloff = cairo.RadialGradient(0, 0, 0.45, 0, 0, 1.42)
    falloff.add_color_stop_rgba(0.0, 0, 0, 0, 0.0)
    falloff.add_color_stop_rgba(1.0, 0, 0, 0, 0.62 * strength)
    ctx.set_source(falloff)
    ctx.paint()
    ctx.restore()
    # And the tube's rounded corners, where the bezel takes over.
    radius = min(width, height) * 0.075 * strength
    if radius >= 1:
        ctx.set_source_rgb(0, 0, 0)
        ctx.set_fill_rule(cairo.FillRule.EVEN_ODD)
        ctx.rectangle(0, 0, width, height)
        ctx.new_sub_path()
        ctx.arc(width - radius, radius, radius, -math.pi / 2, 0)
        ctx.arc(width - radius, height - radius, radius, 0, math.pi / 2)
        ctx.arc(radius, height - radius, radius, math.pi / 2, math.pi)
        ctx.arc(radius, radius, radius, math.pi, math.pi * 1.5)
        ctx.close_path()
        ctx.fill()
    mask.flush()
    return mask


class CrtLayer:
    """Applied by each wallpaper surface in place of drawing the sharp picture."""

    def __init__(self):
        self.key = None
        self.mask = None
        self.bar_y = 0

    def _ready(self, width, height, strength, scale, offset_y):
        key = (width, height, round(strength, 3), scale, offset_y)
        if key != self.key:
            self.key = key
            self.mask = build_mask(width, height, strength, scale, offset_y)

    def paint(self, ctx, soft, strength, width, height, scale, ox, oy):
        self._ready(width, height, strength, scale, -oy % scale)
        # The smeared picture, enlarged smoothly, since a beam has no hard pixel edges.
        ctx.save()
        ctx.translate(-ox, -oy)
        ctx.scale(scale, scale)
        ctx.set_operator(cairo.Operator.SOURCE)
        ctx.set_source_surface(soft, 0, 0)
        source = ctx.get_source()
        source.set_filter(cairo.Filter.BILINEAR)
        source.set_extend(cairo.Extend.PAD)
        ctx.paint()
        ctx.restore()
        # Phosphor dots, and the edges of the glass.
        ctx.save()
        ctx.set_operator(cairo.Operator.MULTIPLY)
        ctx.set_source_surface(self.mask, 0, 0)
        ctx.paint()
        ctx.restore()
        # The hum bar: a band only a little brighter than the rest, rolling down the screen.
        ctx.save()
        ctx.set_operator(cairo.Operator.ADD)
        bar = cairo.LinearGradient(0, self.bar_y, 0, self.bar_y + BAR_HEIGHT)
        level = 0.035 * strength
        bar.add_color_stop_rgba(0.0, 1, 1, 1, 0.0)
        bar.add_color_stop_rgba(0.5, 1, 1, 1, level)
        bar.add_color_stop_rgba(1.0, 1, 1, 1, 0.0)
        ctx.rectangle(0, self.bar_y, width, BAR_HEIGHT)
        ctx.set_source(bar)
        ctx.fill()
        ctx.restore()

    def roll(self, height):
        """Move the bar on. Returns the stretch of the screen that needs redrawing."""
        top = self.bar_y
        self.bar_y = (self.bar_y + BAR_SPEED) % (height + BAR_HEIGHT)
        if self.bar_y < top:  # it has wrapped back to the top
            return 0, height
        return top, BAR_HEIGHT + BAR_SPEED
