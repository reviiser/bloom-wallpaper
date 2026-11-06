package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Procedural pixel plants and fungi. Each builder returns an ordered list of {dx, dy, colour} relative to the
 * root, stem first. A growing organism simply reveals more of its list, so plants visibly build themselves one
 * pixel at a time. Everything is derived from the genome and a seed, so a saved world can regrow identical sprites.
 */
public final class Sprites {
    private Sprites() {}
    private static final double TAU = 2 * Math.PI;

    private static int round(double v) { return (int) Math.rint(v); }
    private static int b(boolean v) { return v ? 1 : 0; }
    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }

    private static final class Px extends ArrayList<int[]> {
        void put(int x, int y, int c) { add(new int[]{x, y, c}); }
    }

    private static List<int[]> dedupe(List<int[]> pixels) {
        HashSet<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>(pixels.size());
        for (int[] p : pixels) if (seen.add(key(p[0], p[1]))) out.add(p);
        return out;
    }

    /** Arching fronds with leaflets, like the reference's feathery spines. */
    static List<int[]> fern(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        int fronds = 3 + (int) (g.d("bushy") * 4);
        double height = (9 + g.d("size") * 22) * scale;
        Ramp leaf = g.d("tone") < 0.6 ? LEAF : OLIVE;
        for (int f = 0; f < fronds; f++) {
            double mid = f - (fronds - 1) / 2.0;
            double ang = -Math.PI / 2 + mid * (0.44 + rng.uniform(-0.05, 0.05));
            double droop = 0.05 + Math.abs(ang + Math.PI / 2) * 0.09;
            double x = 0, y = 0;
            int n = (int) (height * (1 - Math.abs(mid) * 0.08));
            for (int j = 0, end = Math.max(3, n); j < end; j++) {
                x += Math.cos(ang);
                y += Math.sin(ang);
                ang += Math.cos(ang) >= 0 ? droop : -droop;
                double t = j / (double) Math.max(1, n);
                int shade = 2 + (int) ((1 - Math.abs(t - 0.45)) * 3.4) - (f % 2 != 0 ? 1 : 0);
                px.put(round(x), round(y), leaf.at(shade));
                if (j > 1 && j % 2 == 0) {
                    int reach = (int) ((1 - t) * (2 + g.d("bushy") * 3)) + 1;
                    for (int side = -1; side <= 1; side += 2)
                        for (int k = 1; k <= reach; k++)
                            px.put(round(x + side * k), round(y - k * 0.4 + 0.5), leaf.at(shade - 1 + b(k == reach)));
                }
            }
        }
        return dedupe(px);
    }

    /** A herringbone stem topped with flat glowing dishes (the blue cups of the reference). */
    static List<int[]> spire(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        int height = (int) ((8 + g.d("size") * 16) * scale) + 2;
        double width = 1 + g.d("bushy") * 3.5 * scale;
        for (int j = 0; j < height; j++) {
            px.put(0, -j, STEM.at(1 + j * 3 / height));
            if (j % 2 == 0 && j < height - 2) {
                int reach = (int) (width * (1 - j / (double) height) + 1);
                for (int side = -1; side <= 1; side += 2)
                    for (int k = 1; k <= reach; k++) px.put(side * k, -j - k / 2, STEM.at(2 - b(k == reach)));
            }
        }
        Ramp flower = FLOWER[g.i("hue")];
        int heads = 1 + (int) (g.d("bushy") * 2.2);
        for (int h = 0; h < heads; h++) {
            int hx = 0;
            if (h != 0) { int sign = rng.choice(-1, 1); hx = sign * rng.randint(2, 4); }
            int hy = -height - (h == 0 ? 0 : -rng.randint(1, 4));
            int w = 2 + (int) (g.d("bloom") * 2 * scale);
            for (int k = -w; k <= w; k++) px.put(hx + k, hy, flower.at(1));
            for (int k = -w + 1; k < w; k++) px.put(hx + k, hy + 1, Math.abs(k) > 1 ? flower.at(0) : flower.at(1));
            px.put(hx, hy - 1, flower.at(2));
            if (hx != 0)
                for (int j = 1, e = Math.abs(hy) - height + 3; j < e; j++) px.put(hx - (hx > 0 ? 1 : -1) * (j / 2), hy + 1 + j, STEM.at(1));
        }
        return dedupe(px);
    }

    /** Low nitrogen-fixing groundcover, sprinkled with tiny blooms. */
    static List<int[]> clover(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        double spread = 3 + g.d("size") * 8 * scale;
        Ramp leaf = g.d("tone") < 0.5 ? LEAF : OLIVE;
        int tufts = 4 + (int) (g.d("bushy") * 7);
        int[][] offsets = {{0, 0}, {-1, -1}, {1, -1}, {0, -2}};
        List<int[]> spots = new ArrayList<>();
        for (int t = 0; t < tufts; t++) {
            int tx = round(rng.uniform(-spread, spread));
            int ty = round(rng.uniform(-1.2, 1.2) - Math.abs(tx) * 0.02);
            spots.add(new int[]{tx, ty});
            for (int[] o : offsets) px.put(tx + o[0], ty + o[1], leaf.at(2 + b(o[1] == -2) + (t & 1)));
        }
        Ramp flower = FLOWER[g.i("hue")];
        for (int s = 0; s < spots.size(); s += 2) px.put(spots.get(s)[0], spots.get(s)[1] - 3, flower.at(2));
        return dedupe(px);
    }

    /** A rounded bush of star-shaped leaves bearing dark red fruit. */
    static List<int[]> shrub(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        double r = (5 + g.d("size") * 9) * scale;
        Ramp leaf = g.d("tone") < 0.55 ? LEAF : OLIVE;
        for (int j = 0, e = (int) (r * 0.6); j < e; j++) px.put(0, -j, TRUNK.at(3));
        for (int bb = 0; bb < 5; bb++) {
            double ang = -Math.PI / 2 + (bb - 2) * 0.5;
            for (int k = 0, e = (int) (r * 0.7); k < e; k++)
                px.put(round(Math.cos(ang) * k), round(-r * 0.3 + Math.sin(ang) * k * 0.9), TRUNK.at(3));
        }
        int stars = (int) (10 + g.d("bushy") * 16);
        for (int s = 0; s < stars; s++) {
            double a = rng.uniform(0, TAU);
            double d = Math.sqrt(rng.random()) * r;
            double sx = Math.cos(a) * d, sy = -r * 0.8 + Math.sin(a) * d * 0.72;
            int shade = 1 + (int) ((-sy / (r * 1.5)) * 3);
            int arm = 2 + (int) (scale * 1.5);
            for (int an = 0; an < 6; an++) {
                double th = an * Math.PI / 3 + 0.2;
                for (int k = 0; k <= arm; k++)
                    px.put(round(sx + Math.cos(th) * k), round(sy + Math.sin(th) * k * 0.8), leaf.at(shade + b(k == 0)));
            }
        }
        Ramp fruit = FLOWER[g.i("hue")];
        int[][] blossom = {{0, 0, 2}, {1, 0, 1}, {-1, 0, 1}, {0, 1, 0}, {0, -1, 1}, {1, 1, 0}, {-1, 1, 0}};
        for (int f = 0, e = 2 + (int) (g.d("bloom") * 7); f < e; f++) {
            double a = rng.uniform(0, TAU);
            double d = Math.sqrt(rng.random()) * r * 0.9;
            int fx = round(Math.cos(a) * d), fy = round(-r * 0.8 + Math.sin(a) * d * 0.72);
            if (g.d("bloom") > 0.6) { // the showier blossoms open out, like the red roses in the reference
                for (int[] o : blossom) px.put(fx + o[0], fy + o[1], fruit.at(o[2]));
            } else {
                px.put(fx, fy, fruit.at(1));
                px.put(fx + 1, fy, fruit.at(0));
            }
        }
        return dedupe(px);
    }

    private static void branch(PyRandom rng, Px px, List<double[]> tips, double scale, double x, double y, double ang, double length, int depth) {
        for (int j = 0, e = (int) length; j < e; j++) {
            x += Math.cos(ang);
            y += Math.sin(ang);
            ang += rng.uniform(-0.1, 0.1);
            int shade = Math.cos(ang) > 0.1 ? 4 : 2;
            px.put(round(x), round(y), TRUNK.at(shade));
            if (depth >= 2 && scale > 0.5) px.put(round(x) + 1, round(y), TRUNK.at(2));
        }
        tips.add(new double[]{x, y});
        if (depth > 0)
            for (int side = -1; side <= 1; side += 2)
                branch(rng, px, tips, scale, x, y, ang + side * rng.uniform(0.35, 0.8), length * 0.62, depth - 1);
    }

    /** A young tree. Slow, thirsty, and the backbone of a mature guild. */
    static List<int[]> sapling(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        double height = (24 + g.d("size") * 40) * scale;
        Ramp leaf = g.d("tone") < 0.7 ? LEAF : OLIVE;
        List<double[]> tips = new ArrayList<>();
        branch(rng, px, tips, scale, 0, 0, -Math.PI / 2, height * 0.55, 2 + b(g.d("bushy") > 0.5));
        Px canopy = new Px();
        for (double[] tip : tips) {
            double tx = tip[0], ty = tip[1];
            double r = 3 + g.d("bushy") * 5 * scale;
            for (int k = 0, e = (int) (r * r * 1.0); k < e; k++) {
                double a = rng.uniform(0, TAU);
                double d = Math.sqrt(rng.random()) * r;
                double sx = tx + Math.cos(a) * d, sy = ty + Math.sin(a) * d * 0.7;
                int shade = 1 + (int) (Math.max(0, (ty - sy) / r + 1) * 1.9);
                canopy.put(round(sx), round(sy), leaf.at(shade));
            }
        }
        rng.shuffle(canopy);
        px.addAll(canopy);
        return dedupe(px);
    }

    /** A little flush of fruiting bodies. Shape and colour come from the colony's genes. */
    static List<int[]> mushroom(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        Ramp cap = CAP[g.i("hue")];
        int n = 1 + (int) (g.d("cluster") * 4.5);
        for (int m = 0; m < n; m++) {
            int mx = m == 0 ? 0 : rng.randint(-5, 5);
            int my = m == 0 ? 0 : rng.randint(-1, 1);
            double s = g.d("size") * rng.uniform(0.6, 1.0) * scale;
            int tall = (int) (1 + s * 5 * (0.6 + g.d("slender")));
            for (int j = 0; j < tall; j++) px.put(mx, my - j, j < tall - 1 ? cap.at(0) : cap.at(1));
            int rad = 1 + (int) (s * 3.5 * (1.3 - g.d("slender") * 0.6));
            int top = my - tall;
            double shape = g.d("shape");
            if (shape < 0.34) { // shallow dish
                for (int k = -rad; k <= rad; k++) px.put(mx + k, top, cap.at(1 + b(Math.abs(k) < rad - 1)));
                for (int k = -rad + 1; k < rad; k++) px.put(mx + k, top - 1, Math.abs(k) < rad / 2.0 ? cap.at(3) : cap.at(2));
            } else if (shape < 0.67) { // dome
                for (int row = 0; row <= rad; row++) {
                    int half = (int) (Math.sqrt(Math.max(0, rad * rad - row * row)) + 0.4);
                    for (int k = -half; k <= half; k++)
                        px.put(mx + k, top - row, cap.at(1 + b(row > 0) + b(row > rad / 2.0 && Math.abs(k) < half)));
                }
            } else { // bell
                for (int row = 0; row < rad + 2; row++) {
                    int half = Math.max(0, rad - row / 2 - b(row == rad + 1));
                    for (int k = -half; k <= half; k++) px.put(mx + k, top - row, cap.at(Math.min(3, 1 + row * 2 / (rad + 1))));
                }
            }
        }
        return dedupe(px);
    }

    /** What a dead tree leaves behind: a nurse log, feeding everything for a long time. */
    static List<int[]> fallenLog(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        int length = (int) ((22 + g.d("size") * 26) * scale);
        double r = 1.5 + g.d("size") * 2.5 * scale;
        double tilt = rng.uniform(-0.05, 0.05);
        int ir = (int) r;
        for (int j = Clock.div(-length, 2); j < length / 2; j++) {
            double cy = j * tilt;
            for (int row = -ir; row <= ir; row++) {
                int shade = 2 - Clock.div(row * 2, Math.max(1, ir));
                if (((j * 5 + row * 3) & 7) == 0) shade -= 1;
                px.put(j, round(cy) + row - ir, LOG.at(shade));
            }
        }
        rng.shuffle(px);
        return dedupe(px);
    }

    /** Spring's first flower, up through the last of the snow. */
    static List<int[]> crocus(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        Ramp petals = g.d("bloom") < 0.6 ? FLOWER[2] : FLOWER[5];
        for (int k = 0, e = 2 + (int) (g.d("bushy") * 3); k < e; k++) {
            int x = rng.randint(-4, 4);
            int h = 2 + (int) (g.d("size") * 3 * scale);
            for (int j = 0; j < h; j++) px.put(x, -j, LEAF.at(4));
            px.put(x - 1, 1 - h / 2, LEAF.at(5));
            px.put(x - 1, -h, petals.at(1)); px.put(x, -h, petals.at(2)); px.put(x + 1, -h, petals.at(1));
            px.put(x - 1, -h - 1, petals.at(0)); px.put(x, -h - 1, petals.at(1)); px.put(x + 1, -h - 1, petals.at(0));
            px.put(x, -h + 1, petals.at(2));
        }
        return dedupe(px);
    }

    /** Midsummer: a tall spire of hanging bells, darkening toward the tip. */
    static List<int[]> foxglove(G g, long seed, double scale) {
        Px px = new Px();
        int h = (int) ((14 + g.d("size") * 14) * scale) + 3;
        Ramp hue = g.d("bloom") < 0.6 ? FLOWER[3] : FLOWER[2];
        for (int j = 0; j < h; j++) px.put(0, -j, STEM.at(2));
        for (int side = -1; side <= 1; side += 2)
            for (int k = 1; k < 4; k++) px.put(side * k, Clock.div(-k, 2), LEAF.at(4));
        for (int j = h / 3; j < h; j += 2) {
            double t = (j - h / 3) / (double) Math.max(1, h - h / 3);
            int width = t < 0.55 ? 2 : 1;
            int shade = t < 0.4 ? 2 : t < 0.75 ? 1 : 0;
            for (int side = -1; side <= 1; side += 2)
                for (int k = 1; k <= width; k++) px.put(side * k, -j, hue.at(shade));
        }
        return dedupe(px);
    }

    /** Autumn's last gold: arching plumes of tiny yellow flowers. */
    static List<int[]> goldenrod(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        int stems = 2 + (int) (g.d("bushy") * 3);
        int h = (int) ((12 + g.d("size") * 10) * scale) + 3;
        for (int s = 0; s < stems; s++) {
            double lean = (s - (stems - 1) / 2.0) * 0.3;
            double x = 0, y = 0;
            for (int j = 0; j < h; j++) {
                x += lean + j * lean * 0.02;
                y -= 1;
                px.put(round(x), round(y), STEM.at(2));
                if (j > h * 0.5) {
                    for (int side = -1; side <= 1; side += 2) {
                        if (rng.random() < 0.75) {
                            int spread = 1 + (int) ((h - j) * 0.25);
                            int fx = round(x) + side * rng.randint(1, spread);
                            int fy = round(y) + rng.randint(-1, 0);
                            px.put(fx, fy, FLOWER[5].at(rng.randint(1, 2)));
                        }
                    }
                }
            }
        }
        return dedupe(px);
    }

    /** Winter's small defiance: a nodding white bell above the snow. */
    static List<int[]> snowdrop(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        for (int k = 0, e = 2 + (int) (g.d("bushy") * 3); k < e; k++) {
            int x = rng.randint(-5, 5);
            int h = 3 + (int) (g.d("size") * 3 * scale);
            for (int j = 0; j < h; j++) px.put(x, -j, LEAF.at(3));
            px.put(x + 1, -h, LEAF.at(3)); px.put(x + 2, -h + 1, FLOWER[6].at(2)); px.put(x + 1, -h + 1, FLOWER[6].at(1));
            px.put(x + 3, -h + 1, FLOWER[6].at(1)); px.put(x + 2, -h + 2, FLOWER[6].at(2));
        }
        return dedupe(px);
    }

    /** Autumn, on a sprawling vine. In the last week of October, someone carves them. */
    static List<int[]> pumpkin(G g, long seed, double scale) {
        PyRandom rng = new PyRandom(seed);
        Px px = new Px();
        for (int side = -1; side <= 1; side += 2) {
            int x = 0;
            for (int j = 0, e = 4 + (int) (g.d("bushy") * 7); j < e; j++) {
                x += side;
                px.put(x, rng.randint(0, 1), LEAF.at(3));
                if (j % 3 == 0) px.put(x, -1, LEAF.at(5));
            }
        }
        int r = 3 + (int) (g.d("size") * 2 * scale);
        int rows = Math.max(3, r);
        for (int y = -rows; y <= 0; y++) {
            int half = (int) (Math.sqrt(Math.max(0, 1 - Math.pow((y + rows / 2.0) / (rows / 2.0 + 0.5), 2))) * r + 0.5);
            for (int x = -half; x <= half; x++) {
                boolean rib = (x + half) % 3 == 0, lit = x < 0 && y < -1;
                px.put(x, y, !rib ? FLOWER[5].at(lit ? 2 : 1) : FLOWER[4].at(1));
            }
        }
        px.put(0, -rows - 1, LEAF.at(3));
        px.put(1, -rows - 1, LEAF.at(3));
        return dedupe(px);
    }

    /** Give a flat sprite some form: edges facing the light go a shade brighter, edges away from it a shade darker. */
    public static List<int[]> bevel(List<int[]> pix, int light) {
        HashSet<Long> filled = new HashSet<>();
        for (int[] p : pix) filled.add(key(p[0], p[1]));
        if (filled.size() < 6) return pix; // too small to carry shading without losing its shape
        List<int[]> out = new ArrayList<>(pix.size());
        for (int[] p : pix) {
            int dx = p[0], dy = p[1], c = p[2];
            boolean up = !filled.contains(key(dx, dy - 1)), lit = !filled.contains(key(dx + light, dy));
            boolean down = !filled.contains(key(dx, dy + 1)), away = !filled.contains(key(dx - light, dy));
            if ((up || lit) && !(down && away)) c = Palette.LIT[c];
            else if ((down || away) && !(up || lit)) c = Palette.DARK[c];
            out.add(new int[]{dx, dy, c});
        }
        return out;
    }

    /** Poison shows: well-defended plants carry red flecks, a warning to anything learning to eat them. */
    private static List<int[]> flecked(List<int[]> px, G g) {
        double toxin = g.d("toxin", 0.0);
        if (toxin > 0.5) {
            int step = toxin > 0.75 ? 3 : 5;
            for (int k = 0; k < px.size(); k += step) { int[] p = px.get(k); px.set(k, new int[]{p[0], p[1], FLOWER[4].at(0)}); }
        }
        return px;
    }

    public static List<int[]> build(String kind, G g, long seed, double scale) {
        switch (kind) {
            case "clover": return flecked(clover(g, seed, scale), g);
            case "fern": return flecked(fern(g, seed, scale), g);
            case "spire": return flecked(spire(g, seed, scale), g);
            case "shrub": return shrub(g, seed, scale);
            case "sapling": return sapling(g, seed, scale);
            case "mushroom": return mushroom(g, seed, scale);
            case "log": return fallenLog(g, seed, scale);
            case "crocus": return crocus(g, seed, scale);
            case "foxglove": return foxglove(g, seed, scale);
            case "goldenrod": return goldenrod(g, seed, scale);
            case "snowdrop": return snowdrop(g, seed, scale);
            case "pumpkin": return pumpkin(g, seed, scale);
        }
        throw new IllegalArgumentException(kind);
    }

    static String[] join(String[] a, String[] b) {
        String[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    public static String ljust(String s, int width) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < width) b.append('.');
        return b.toString();
    }

    static final String[] STAG_BODY = {
        "..............#...#...",
        "..............#..#..#.",
        "...............#.#.#..",
        "...........#...###....",
        "............#..##.....",
        ".............####.....",
        "..............####O##.",
        "..............######..",
        "..............###.....",
        ".............####.....",
        "..##########.####.....",
        ".###############......",
        "#################.....",
        "#++##############.....",
        ".++++##########++.....",
        "..+++++++++++++++.....",
    };
    // The same stag with its head lowered, to graze or drink.
    static final String[] STAG_LOWERED = new String[STAG_BODY.length];
    static {
        for (int row = 0; row < STAG_BODY.length; row++) {
            String line = STAG_BODY[row];
            String kept = line.substring(0, 14);
            kept += row >= 9 ? STAG_BODY[row - 9].substring(14) : ljust("", line.length() - 14);
            STAG_LOWERED[row] = kept;
        }
    }
    static final String[][] STAG_LEGS = {
        {"...++.#.......+#......", "...+..#........#......", "...+...#.......#......", "...+...#.......#......", "...+...#......#......."},
        {"..+#..+........#+.....", "..#...+.........#.....", ".#....+.........#.....", ".#.....+.......#......", ".#.....+.......#......"},
    };
    static final String[] RABBIT_SIT = {"...#.#.", "...#.#.", "...###.", ".#####.", "w####+.", ".#+.#+."};
    static final String[] RABBIT_HOP = {"....#.#", "....#.#", "...####", "######.", "w####..", ".+...+."};
    static final String[] FOX_TOP = {"..........#.#.", "..........###.", ".........####o", "w.........#w..", "#+.#######w...", ".##########...", "...########..."};
    static final String[][] FOX_LEGS = {{"...+#....#+...", "...+.#...#.+..", "...o.o...o.o.."}, {"..#+.....+#...", "..#..+..+.#...", ".o...o..o..o.."}};
    static final String[] FOX_DRINK = {"..............", "..............", "..............", "w.............", "#+.#######....", ".##########...", "...########.#.", "...+#....#+###", "...+.#...#.####", "...o.o...o.o.ow"};
    static final String[] HEDGEHOG_TOP = {"..+#+#+..", ".+#+#+#w.", "+#+#+#+#w"};
    static final String[][] HEDGEHOG_LEGS = {{"..w...w.."}, {"...w.w..."}};
    static final String[] SQUIRREL_RUN = {".##.....", "###..#.#", "##...###", ".#.####w", "..#####.", "...w..w."};
    static final String[] SQUIRREL_DIG = {"##......", "###.....", ".##.....", "..#####.", "...#####", "...w.w.w"};
    static final String[] FROG_SIT = {".w.w.", "+###+", "#+++#"};
    static final String[] FROG_HOP = {".w.w.", ".###.", "#...#"};
    static final String[] SNAIL_BODY = {"..++.", ".+#++", "wwwww"};
    static final String[] BIRD_SIT = {"...##o", "+.###.", ".+###.", "..w.w."};
    static final String[][] BIRD_FLY = {{"..##..", "..##..", "+#####", ".###o."}, {"......", "......", "+#####", ".####o", "..##.."}};
    static final String[][] OWL_FLY = {{"##.....##", "+##.w.##+", ".+#####+.", "...###..."}, {".........", "...www...", ".+#####+.", "##+###+##"}};
    static final String[][] BAT_FLAP = {{"#...#", ".###.", "..#.."}, {".....", "#.#.#", ".###."}};
    static final String[] BEETLE_BODY = {"...w.w...", "..#####..", ".##+#+##.", ".#######.", "..+.+.+.."};
    static final String[] MOTH_BODY = {"##.....##", "w##...##w", "#w#.+.#w#", ".###+###.", "..##+##..", "...#.#..."};

    static final Map<String, Map<String, String[]>> ANIMAL_POSES = new HashMap<>();
    private static void poses(String kind, Object... kv) {
        Map<String, String[]> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (String[]) kv[i + 1]);
        ANIMAL_POSES.put(kind, m);
    }
    static {
        poses("beetle", "walk0", BEETLE_BODY);
        poses("moth", "fly0", MOTH_BODY);
        poses("rabbit", "sit", RABBIT_SIT, "hop", RABBIT_HOP);
        poses("fox", "walk0", join(FOX_TOP, FOX_LEGS[0]), "walk1", join(FOX_TOP, FOX_LEGS[1]), "drink", FOX_DRINK);
        poses("hedgehog", "walk0", join(HEDGEHOG_TOP, HEDGEHOG_LEGS[0]), "walk1", join(HEDGEHOG_TOP, HEDGEHOG_LEGS[1]));
        poses("squirrel", "run", SQUIRREL_RUN, "dig", SQUIRREL_DIG);
        poses("frog", "sit", FROG_SIT, "hop", FROG_HOP);
        poses("snail", "crawl", SNAIL_BODY);
        poses("songbird", "sit", BIRD_SIT, "fly0", BIRD_FLY[0], "fly1", BIRD_FLY[1]);
        poses("owl", "fly0", OWL_FLY[0], "fly1", OWL_FLY[1]);
        poses("bats", "fly0", BAT_FLAP[0], "fly1", BAT_FLAP[1]);
        poses("stag", "walk0", join(STAG_BODY, STAG_LEGS[0]), "walk1", join(STAG_BODY, STAG_LEGS[1]), "lowered", join(STAG_LOWERED, STAG_LEGS[0]));
    }
    static final Map<String, Integer> BODY_COLUMN = new HashMap<>();
    static final Map<String, String[]> ORNAMENTS = new HashMap<>();
    static {
        Object[] cols = {"beetle", 4, "moth", 4, "rabbit", 2, "fox", 6, "hedgehog", 4, "squirrel", 5, "frog", 2, "snail", 2, "songbird", 3, "owl", 4, "bats", 2, "stag", 6};
        for (int i = 0; i < cols.length; i += 2) BODY_COLUMN.put((String) cols[i], (Integer) cols[i + 1]);
        // Extra rows worn on top by highly ornamented individuals: antlers, plumes, crests, tufts.
        ORNAMENTS.put("rabbit", new String[]{".w.w.w.", "..w.w.."});
        ORNAMENTS.put("fox", new String[]{"w.............", "#............."});
        ORNAMENTS.put("hedgehog", new String[]{"..+.+.+.."});
        ORNAMENTS.put("squirrel", new String[]{".###...."});
        ORNAMENTS.put("songbird", new String[]{"....o."});
        ORNAMENTS.put("owl", new String[]{"..w...w.."});
        ORNAMENTS.put("stag", new String[]{"............#...#.#...", ".............#.#..#.#."});
    }
    private static final Map<String, String[]> animalCache = new HashMap<>();

    /** Lengthen (or, with n = -1, shorten) a body by repeating or dropping a column. */
    public static String[] stretch(String[] rows, int col, int n) {
        if (n == 0) return rows;
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) {
            String r = rows[i];
            if (n < 0) out[i] = r.substring(0, col) + r.substring(col + 1);
            else {
                StringBuilder b = new StringBuilder(r.substring(0, col));
                for (int k = 0; k < n; k++) b.append(r.charAt(col));
                out[i] = b.append(r.substring(col)).toString();
            }
        }
        return out;
    }

    /** Form for an animal: the top of the body catches the light, and the underside falls into shadow. */
    public static String[] roundBody(String[] rows) {
        int h = rows.length;
        String[] out = new String[h];
        for (int y = 0; y < h; y++) {
            char[] line = rows[y].toCharArray();
            for (int x = 0; x < line.length; x++) {
                if (rows[y].charAt(x) != '#') continue;
                char above = y > 0 && x < rows[y - 1].length() ? rows[y - 1].charAt(x) : '.';
                char below = y + 1 < h && x < rows[y + 1].length() ? rows[y + 1].charAt(x) : '.';
                if (above == '.' && below != '.') line[x] = '^';
                else if (below == '.' && above != '.') line[x] = '+';
            }
            out[y] = new String(line);
        }
        return out;
    }

    /** A body built from its genes: size stretches it, ornament crowns it. Cached by what shows. */
    public static String[] animalRows(String kind, String pose, G g) {
        int size = Math.min(3, (int) (g.d("size") * 4));
        boolean fancy = g.d("ornament") > 0.7, plain = kind.equals("stag") && g.d("ornament") < 0.3;
        String key = kind + "/" + pose + "/" + size + "/" + fancy + "/" + plain;
        String[] rows = animalCache.get(key);
        if (rows == null) {
            rows = ANIMAL_POSES.get(kind).get(pose);
            if (plain) rows = Arrays.copyOfRange(rows, 2, rows.length); // a young-looking stag with only spikes
            String[] crown = ORNAMENTS.get(kind);
            if (fancy && crown != null) rows = join(crown, rows);
            int width = 0;
            for (String r : rows) width = Math.max(width, r.length());
            String[] even = new String[rows.length];
            for (int i = 0; i < rows.length; i++) even[i] = ljust(rows[i], width);
            rows = stretch(even, BODY_COLUMN.get(kind), size - 1);
            if (!kind.equals("bats")) rows = roundBody(rows); // a bat is only ever a silhouette
            animalCache.put(key, rows);
        }
        return rows;
    }
}
