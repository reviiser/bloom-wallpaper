package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

/** The app's mark: a curled seedling pushing out of the soil at night, with a firefly close by. Drawn by hand on a grid. */
public final class Icon {
    private Icon() {}
    private static final String[] LARGE = {
        "..............SSSSS.........", "...........SSSSSSSSSSS......", "..........SSs........SSS....", ".........SSs...........SS...", "........SSs...SSSSS.....SS..",
        "........SS..SSS...SS....sS..", ".......SSs..SS.....SS...sS..", ".......SS..SS...TT..S...sS..", ".......SS..SS..T..T.S...sS..", ".......SS..sS..T....S..sS...",
        ".......SS...SS.TTTTSs.sSS...", ".......SS....S.......sSs....", ".......SS....SSs...sSSs.....", ".......SSs....sSSSSSs.......", "........SS..................",
        "........SS..................", "..L.....SS....LL............", ".LLLL...SS..LLLLL...........", ".lLLLL..SS.LLLLLl...........", "..llLLLLSSLLLll.............",
        "....lllLSSLll...............", "........SS..................", "........SS..................",
    };
    private static final String[] MEDIUM = {
        "..........SSSS......", "........SSSSSSSS....", ".......SS......SS...", "......SS..SSS...SS..", "......S..SS.SS..sS..", ".....SS..S.T..S..S..", ".....SS..S.TT.S..S..",
        ".....SS..sS..Ss.sS..", ".....SS...SSSs.sS...", ".....SS.......sS....", ".....SS...sSSSs.....", ".....SS.............", ".L...SS..LL.........", "LLLL.SS.LLLL........",
        ".lLLLSSLLLl.........", "...lLSSLl...........", ".....SS.............", ".....SS.............",
    };

    /** A square of palette indices (0 = transparent): frame, night sky, soil, seedling, firefly. */
    public static int[][] grid(int n) {
        int[][] g = new int[n][n];
        int frame = 2, notch = 2;
        double glowX = n * 0.80, glowY = n * 0.70;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int corner = Math.min(x, n - 1 - x) + Math.min(y, n - 1 - y);
                if (corner < notch) continue;
                int edge = Math.min(Math.min(x, n - 1 - x), Math.min(y, n - 1 - y));
                if (edge == 0 || corner == notch) g[y][x] = CANOPY.at(0);
                else if (edge < frame || corner < notch + frame) g[y][x] = FLECK.at(1);
                else {
                    double glow = 1 - Math.hypot(x - glowX, y - glowY) / (n * 0.62);
                    g[y][x] = SKY.at((int) (Math.max(0.0, glow) * 3.4 + 0.1 + ((x ^ y) & 1) * 0.5));
                }
            }
        }
        boolean large = n >= 32;
        String[] art = large ? LARGE : MEDIUM;
        int left = n == 22 ? 1 : 2, stemAt = left + (large ? 9 : 6), depth = Math.max(2, n / 7), floor = n - frame;
        // Soil, heaped a little where the seedling has pushed through, under a skin of moss.
        for (int x = frame; x < n - frame; x++) {
            double hump = Math.max(0.0, 1 - Math.abs(x - stemAt) / (n * 0.26));
            int top = floor - depth - (int) (hump * n * 0.07 + 0.5);
            for (int y = top; y < floor; y++) {
                if (y == top) g[y][x] = ((x * 7) & 3) != 0 ? TEAL.at(4) : TEAL.at(6);
                else g[y][x] = (x + y * 3) % 5 != 0 ? LITTER.at(2) : LITTER.at(3);
            }
        }
        // The seedling itself, its stem running down into the soil.
        int topRow = floor - depth + 1 - art.length;
        for (int row = 0; row < art.length; row++) {
            for (int col = 0; col < art[row].length(); col++) {
                char ch = art[row].charAt(col);
                if (ch == '.') continue;
                int c = ch == 'S' ? FIREFLY.at(2) : ch == 's' ? LEAF.at(5) : ch == 'T' ? FIREFLY.at(4) : ch == 'L' ? FIREFLY.at(1) : LEAF.at(4);
                put(g, n, left + col, topRow + row, c);
            }
        }
        // A firefly mid-flash, the same cross it makes in the forest, with a dotted flight path.
        int fx = (int) glowX, fy = (int) glowY;
        put(g, n, fx, fy, FIREFLY.at(4));
        put(g, n, fx + 1, fy, FIREFLY.at(3));
        put(g, n, fx - 1, fy, FIREFLY.at(3));
        put(g, n, fx, fy + 1, FIREFLY.at(3));
        put(g, n, fx, fy - 1, FIREFLY.at(3));
        if (large) {
            int[][] flight = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {-4, 1}, {-6, 0}, {-7, -2}};
            for (int[] d : flight) put(g, n, fx + d[0], fy + d[1], FIREFLY.at(1));
        }
        return g;
    }

    private static void put(int[][] g, int n, int x, int y, int c) { if (0 <= x && x < n && 0 <= y && y < n) g[y][x] = c; }
}
