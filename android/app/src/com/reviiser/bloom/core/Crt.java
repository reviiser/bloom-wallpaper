package com.reviiser.bloom.core;

import java.util.List;

/**
 * An old tube over the picture: the beam's smear, phosphor dots, dark rounded edges. The smear is what old pixel artists
 * counted on. A composite signal can't hold sharp changes from one pixel to the next along a line, so checkerboard
 * dithering melts into a solid in-between colour. The mask never changes, so it is composed once.
 */
public final class Crt {
    private Crt() {}
    public static final int BAR_HEIGHT = 56, BAR_SPEED = 3, BAR_EVERY = 4, SMEAR_MARGIN = 2;

    /** The art as the beam draws it: each pixel bleeding into its neighbours along the row. rects are in art pixels, or null for everything. */
    public static void smear(int[] pic, int[] soft, int w, int h, List<int[]> rects, double strength) {
        if (rects == null) {
            for (int y = 0; y < h; y++) row(pic, soft, w, y, 0, w, strength);
            return;
        }
        for (int[] r : rects)
            for (int y = Math.max(0, r[1]), e = Math.min(h, r[1] + r[3]); y < e; y++)
                row(pic, soft, w, y, Math.max(0, r[0] - SMEAR_MARGIN), Math.min(w, r[0] + r[2] + SMEAR_MARGIN), strength);
    }

    private static void row(int[] pic, int[] soft, int w, int y, int x0, int x1, double strength) {
        // At full smear this is the classic 1-2-1 filter, which turns a checkerboard into one flat colour.
        double blend = Math.min(1.0, strength * 1.6);
        // Phosphors are bright: this makes up for the light the mask takes away, and lets the brightest things swell.
        double boost = 1.1 * strength;
        int right = (int) (blend / 3.0 * 256), left = (int) (blend / 4.0 * 256);
        int addC = (int) (boost * (1.0 - blend / 2.0) * 256), addN = (int) (boost * blend / 4.0 * 256);
        int o = y * w;
        for (int x = x0; x < x1; x++) {
            int c = pic[o + x], l = x > 0 ? pic[o + x - 1] : c, r = x + 1 < w ? pic[o + x + 1] : c;
            int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
            int lr = l >> 16 & 255, lg = l >> 8 & 255, lb = l & 255;
            int rr = r >> 16 & 255, rg = r >> 8 & 255, rb = r & 255;
            int vr = cr + ((rr - cr) * right >> 8), vg = cg + ((rg - cg) * right >> 8), vb = cb + ((rb - cb) * right >> 8);
            vr += (lr - vr) * left >> 8;
            vg += (lg - vg) * left >> 8;
            vb += (lb - vb) * left >> 8;
            vr += (cr * addC + (lr + rr) * addN) >> 8;
            vg += (cg * addC + (lg + rg) * addN) >> 8;
            vb += (cb * addC + (lb + rb) * addN) >> 8;
            soft[o + x] = 0xff000000 | (vr > 255 ? 255 : vr) << 16 | (vg > 255 ? 255 : vg) << 8 | (vb > 255 ? 255 : vb);
        }
    }

    /** The whole fixed pattern for one screen: columns of red, green and blue dots, the fall-off of the glass, its rounded corners. */
    public static int[] mask(int width, int height, double strength, int scale) {
        int tall = 6 * scale;
        int[] tile = new int[tall * 6 * 3];
        double off = 1.0 - 0.72 * strength; // how much of the other two colours gets past a phosphor
        double gap = 1.0 - 0.58 * strength; // the dark mesh between rows of dots
        for (int y = 0; y < tall; y++) {
            // A faint trace of each art row as a beam line, under the dots.
            double beam = 1.0 - 0.22 * strength * (1.0 - Math.sin(Math.PI * ((y % scale) + 0.5) / scale));
            for (int x = 0; x < 6; x++) {
                int row = (y + (x / 3 != 0 ? 3 : 0)) % 6;
                double mesh = row == 2 || row == 5 ? gap : 1.0;
                int at = (y * 6 + x) * 3;
                tile[at] = (int) ((x % 3 == 0 ? 1.0 : off) * mesh * beam * 255);
                tile[at + 1] = (int) ((x % 3 == 1 ? 1.0 : off) * mesh * beam * 255);
                tile[at + 2] = (int) ((x % 3 == 2 ? 1.0 : off) * mesh * beam * 255);
            }
        }
        double radius = Math.min(width, height) * 0.075 * strength;
        double cx = width / 2.0, cy = height / 2.0;
        int[] out = new int[width * height];
        int[] fades = new int[width / 8 + 2];
        for (int y = 0; y < height; y++) {
            int o = y * width;
            if ((y & 7) == 0) { // the fall-off is so gradual that working it out every few pixels is enough
                double ny = (y + 4 - cy) / cy;
                for (int k = 0; k < fades.length; k++) {
                    double nx = (k * 8 + 4 - cx) / cx;
                    double t = (Math.sqrt(nx * nx + ny * ny) - 0.45) / (1.42 - 0.45);
                    fades[k] = (int) (256 * (1.0 - (t <= 0 ? 0 : t >= 1 ? 0.62 * strength : 0.62 * strength * t)));
                }
            }
            int tileRow = (y % tall) * 6 * 3;
            for (int x = 0; x < width; x++) {
                int at = tileRow + (x % 6) * 3, fade = fades[x >> 3];
                out[o + x] = 0xff000000 | (tile[at] * fade >> 8) << 16 | (tile[at + 1] * fade >> 8) << 8 | tile[at + 2] * fade >> 8;
            }
        }
        // And the tube's rounded corners, where the bezel takes over.
        int r = (int) radius;
        if (radius >= 1) {
            for (int y = 0; y < r; y++) {
                double dy = radius - y;
                int cut = r - (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
                for (int x = 0; x < cut; x++) {
                    out[y * width + x] = 0xff000000;
                    out[y * width + width - 1 - x] = 0xff000000;
                    out[(height - 1 - y) * width + x] = 0xff000000;
                    out[(height - 1 - y) * width + width - 1 - x] = 0xff000000;
                }
            }
        }
        return out;
    }
}
