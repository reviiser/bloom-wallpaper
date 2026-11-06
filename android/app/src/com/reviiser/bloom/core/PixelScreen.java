package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A tiny 8-bit drawing surface: indexed pixels from the forest's own palette, and a 5x7 font. */
final class PixelScreen {
    private static final String FONT =
        "0000000000 00005F0000 0007000700 147F147F14 242A7F2A12 2313086462 3649552250 0005030000 001C224100 0041221C00"
        + " 14083E0814 08083E0808 0050300000 0808080808 0060600000 2010080402 3E5149453E 00427F4000 4261514946 2141454B31"
        + " 1814127F10 2745454539 3C4A494930 0171090503 3649494936 064949291E 0036360000 0056360000 0814224100 1414141414"
        + " 0041221408 0201510906 324979413E 7E1111117E 7F49494936 3E41414122 7F4141221C 7F49494941 7F09090901 3E4149497A"
        + " 7F0808087F 00417F4100 2040413F01 7F08142241 7F40404040 7F020C027F 7F0408107F 3E4141413E 7F09090906 3E4151215E"
        + " 7F09192946 4649494931 01017F0101 3F4040403F 1F2040201F 3F4038403F 6314081463 0708700807 6151494543 007F414100"
        + " 0204081020 0041417F00 0402010204 4040404040 0001020400 2054545478 7F48444438 3844444420 384444487F 3854545418"
        + " 087E090102 0C5252523E 7F08040478 00447D4000 2040443D00 7F10284400 00417F4000 7C04180478 7C08040478 3844444438"
        + " 7C14141408 081414187C 7C08040408 4854545420 043F444020 3C4040207C 1C2040201C 3C4030403C 4428102844 0C5050503C"
        + " 4464544C44 0008364100 00007F0000 0041360800 0201020402";
    private static final int[][] GLYPHS = new int[96][5];
    static {
        String[] words = FONT.split(" ");
        for (int i = 0; i < words.length; i++)
            for (int x = 0; x < 5; x++) GLYPHS[i][x] = Integer.parseInt(words[i].substring(x * 2, x * 2 + 2), 16);
    }
    static final int GLYPH_W = 6, GLYPH_H = 9;

    static int textWidth(String s) { return s.length() * GLYPH_W; }
    static int textWidth(String s, int scale) { return s.length() * GLYPH_W * scale; }

    static List<String> wrap(String text, int chars) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.trim().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > chars) {
                lines.add(line);
                line = word;
            } else line = line.isEmpty() ? word : line + " " + word;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    final int w, h;
    final int[] px, out;
    final int[] pal = new int[256];
    int clipX0, clipY0, clipX1, clipY1;

    PixelScreen(int width, int height) {
        w = width;
        h = height;
        px = new int[w * h];
        out = new int[w * h];
        setClip();
    }

    void flush() { for (int i = 0; i < px.length; i++) out[i] = pal[px[i]]; }

    void setClip() { clipX0 = 0; clipY0 = 0; clipX1 = w; clipY1 = h; }
    void setClip(int x, int y, int cw, int ch) {
        clipX0 = Math.max(0, x);
        clipY0 = Math.max(0, y);
        clipX1 = Math.min(w, x + cw);
        clipY1 = Math.min(h, y + ch);
    }

    void pixel(int x, int y, int c) { if (clipX0 <= x && x < clipX1 && clipY0 <= y && y < clipY1) px[y * w + x] = c; }

    void rect(int x, int y, int rw, int rh, int c) {
        int a = Math.max(x, clipX0), b = Math.min(x + rw, clipX1);
        if (a >= b) return;
        for (int row = Math.max(y, clipY0), e = Math.min(y + rh, clipY1); row < e; row++) Arrays.fill(px, row * w + a, row * w + b, c);
    }

    /** A checkerboard: the 8-bit way to mix two colours. */
    void dither(int x, int y, int rw, int rh, int c1, int c2) {
        int a = Math.max(x, clipX0), b = Math.min(x + rw, clipX1);
        if (a >= b) return;
        for (int row = Math.max(y, clipY0), e = Math.min(y + rh, clipY1); row < e; row++)
            for (int k = a; k < b; k++) px[row * w + k] = (((row + a) & 1) == 0) == (((k - a) & 1) == 0) ? c1 : c2;
    }

    void text(int x, int y, String s, int c) { text(x, y, s, c, -1, 1); }
    void text(int x, int y, String s, int c, int shadow, int scale) {
        for (int n = 0; n < s.length(); n++) {
            char ch = s.charAt(n);
            int[] glyph = GLYPHS[ch >= 32 && ch < 128 ? ch - 32 : '?' - 32];
            if (shadow >= 0) glyph(glyph, x + 1, y + 1, shadow, scale);
            glyph(glyph, x, y, c, scale);
            x += GLYPH_W * scale;
        }
    }

    private void glyph(int[] columns, int x, int y, int c, int scale) {
        for (int gx = 0; gx < 5; gx++)
            for (int gy = 0; gy < 7; gy++)
                if ((columns[gx] >> gy & 1) != 0)
                    for (int sy = 0; sy < scale; sy++)
                        for (int sx = 0; sx < scale; sx++) pixel(x + gx * scale + sx, y + gy * scale + sy, c);
    }

    /** Draw an ASCII sprite, as used by the animals. */
    void rows(String[] rows, int x, int y, int[] colors, boolean flip, int scale) {
        for (int ry = 0; ry < rows.length; ry++) {
            String row = rows[ry];
            int n = row.length();
            for (int rx = 0; rx < n; rx++) {
                char ch = row.charAt(rx);
                if (ch == '.' || ch >= 128) continue;
                int c = colors[ch];
                if (c == 0) continue;
                int qx = x + (flip ? n - 1 - rx : rx) * scale, qy = y + ry * scale;
                if (scale == 1) pixel(qx, qy, c);
                else rect(qx, qy, scale, scale, c);
            }
        }
    }

    /** A chunky window frame with notched corners, like a menu from an old cartridge. */
    void panel(int x, int y, int pw, int ph) {
        int fill = SKY.at(1), border = FLECK.at(1);
        rect(x + 2, y, pw - 4, ph, CANOPY.at(0));
        rect(x, y + 2, pw, ph - 4, CANOPY.at(0));
        rect(x + 1, y + 1, pw - 2, ph - 2, CANOPY.at(0));
        rect(x + 2, y + 1, pw - 4, 1, border);
        rect(x + 2, y + ph - 2, pw - 4, 1, border);
        rect(x + 1, y + 2, 1, ph - 4, border);
        rect(x + pw - 2, y + 2, 1, ph - 4, border);
        rect(x + 3, y + 3, pw - 6, ph - 6, fill);
    }

    /** A segmented bar. value is 0 to 1. */
    void meter(int x, int y, int blocks, double value, int on, int off) {
        int lit = (int) (value * blocks + 0.5);
        for (int k = 0; k < blocks; k++) rect(x + k * 4, y, 3, 5, k < lit ? on : off);
    }
    void meter(int x, int y, int blocks, double value, int on) { meter(x, y, blocks, value, on, SKY.at(0)); }
}
