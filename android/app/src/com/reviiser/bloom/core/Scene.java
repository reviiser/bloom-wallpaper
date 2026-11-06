package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paints the unchanging forest: sky, far trees, meadow, pond, framing trunks. Static planes plus the geometry the ecosystem needs. */
public final class Scene {
    static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    /** Value noise with smooth interpolation. */
    public static final class Noise {
        final double[] t = new double[4096];
        public Noise(PyRandom rng) { for (int i = 0; i < 4096; i++) t[i] = rng.random(); }
        public double at(double x, double y) {
            double xf = Math.floor(x), yf = Math.floor(y);
            double fx = x - xf, fy = y - yf;
            fx = fx * fx * (3 - 2 * fx);
            fy = fy * fy * (3 - 2 * fy);
            int h = (int) (((long) xf * 73 + (long) yf * 179) & 4095);
            int h2 = (h + 179) & 4095;
            double a = t[h], b = t[(h + 73) & 4095], c = t[h2], d = t[(h2 + 73) & 4095];
            double top = a + (b - a) * fx;
            return top + (c + (d - c) * fx - top) * fy;
        }
        public double fbm(double x, double y) {
            return 0.6 * at(x, y) + 0.3 * at(x * 2.1 + 9, y * 2.1 + 3) + 0.1 * at(x * 4.3 + 5, y * 4.3 + 11);
        }
    }

    public final int w, h, gy;
    public final long seed;
    public final int[] base, fg, ground, water;
    public int moonX, moonY, moonR;
    public int pondCx, pondCy, pondRx, pondRy;
    public int pondX0, pondY0, pondX1, pondY1;
    public int side, owlX, owlY, shaftX, waterPixels;
    public final List<int[]> stars = new ArrayList<>();
    public final List<int[]> lichen = new ArrayList<>(); // {x0, y0, x1, y1} patches of bark and stone
    public Map<String, double[][]> paths;

    public interface Writer { void write(int i, int v); }

    public Scene(int w, int h, long seed) {
        this.w = w; this.h = h; this.seed = seed;
        int n = w * h;
        base = new int[n]; fg = new int[n]; ground = new int[n]; water = new int[n];
        gy = (int) (h * 0.50);
    }

    /** 0 = new, 0.5 = full, from the real date. */
    public static double moonPhase(long millis) {
        double days = (millis / 1000.0 - 947182440.0) / 86400.0 / 29.53059;
        return days - Math.floor(days);
    }

    /** Draw the moon as it actually is tonight. */
    public void stampMoon(double phase, Writer write) {
        int mx = moonX, my = moonY, r = moonR;
        // Clamp near-new moons to a sliver: an empty sky is less of a landmark.
        int litSide = phase < 0.5 ? 1 : -1;
        double terminator = Math.cos(phase * 2 * Math.PI);
        if (Math.abs(terminator) > 0.93) terminator = Math.copySign(0.93, terminator);
        for (int y = -r; y <= r; y++) {
            int half = (int) (Math.sqrt(Math.max(0, r * r - y * y)) + 0.5);
            for (int x = -half; x <= half; x++) {
                int i = (my + y) * w + mx + x;
                double nx = x / (double) (half != 0 ? half : 1);
                boolean lit = phase == 0.5 || nx * litSide > terminator;
                if (!lit) { write.write(i, SKY.at(5)); continue; }
                int mottle = ((x * 7 + y * 13) ^ (x * y)) & 15;
                double edge = (x * x + y * y) / (double) (r * r);
                int level = edge < 0.55 ? 4 : 3;
                if (mottle < 2) level -= 1;
                write.write(i, MOON.at(level));
            }
        }
    }

    public static Scene build(int w, int h, long seed, long nowMillis) {
        Scene s = paintOnly(w, h, seed);
        final int[] base = s.base;
        s.stampMoon(moonPhase(nowMillis), new Writer() {
            @Override public void write(int i, int v) { base[i] = v; }
        });
        return s;
    }

    /** Everything but tonight's moon. */
    public static Scene paintOnly(int w, int h, long seed) {
        Scene s = new Scene(w, h, seed);
        PyRandom rng = new PyRandom(seed);
        s.layout(rng);
        s.paint(rng);
        return s;
    }

    /** Decide where the landmarks sit. */
    private void layout(PyRandom rng) {
        moonR = Math.max(7, (int) (h * 0.036));
        moonX = (int) (w * rng.uniform(0.40, 0.62));
        moonY = (int) (h * rng.uniform(0.19, 0.27));
        side = rng.choice(-1, 1);
        int cx = (int) (w * (0.5 + side * rng.uniform(0.10, 0.20)));
        int cy = (int) (gy + (h - gy) * rng.uniform(0.34, 0.46));
        int rx = (int) (w * rng.uniform(0.09, 0.13));
        int ry = (int) (h * rng.uniform(0.040, 0.055));
        pondCx = cx; pondCy = cy; pondRx = rx; pondRy = ry;
        // Roomy enough for the widest bay, and its reeds.
        pondX0 = Math.max(0, (int) (cx - rx * 1.75));
        pondY0 = Math.max(0, (int) (cy - ry * 1.9));
        pondX1 = Math.min(w, (int) (cx + rx * 1.75));
        pondY1 = Math.min(h, (int) (cy + ry * 1.9));
        PyRandom starRng = new PyRandom(rng.random());
        for (int k = 0, n = (int) (w * h / 6000.0); k < n; k++) {
            int sx = starRng.randrange(w);
            stars.add(new int[]{sx, starRng.randrange((int) (h * 0.40))});
        }
        owlX = side < 0 ? (int) (w * 0.78) : (int) (w * 0.70);
        owlY = (int) (h * 0.10);
        shaftX = moonX;
        paths = treePaths();
    }

    private void paint(PyRandom rng) {
        Noise noise = new Noise(rng);
        paintSky(noise);
        paintFarTrees(rng, noise);
        paintLace(noise);
        paintGround(rng, noise);
        paintPond(noise);
        paintStars();
        paintBoulders(rng, noise);
        paintTrunks(rng, noise);
        paintCastShadows();
        paintBushes(rng, noise);
        paintUndergrowth(rng, noise);
    }

    private void paintSky(Noise noise) {
        double reach = w * 0.46;
        for (int y = 0; y < gy + 2; y++) {
            int[] by = BAYER[y & 3];
            for (int x = 0; x < w; x++) {
                double d = Math.hypot(x - moonX, (y - moonY) * 1.2);
                double level = 0.6 + 5.6 * Math.pow(Math.max(0.0, 1 - d / reach), 2.2) + noise.at(x / 40.0, y / 22.0) * 1.1;
                level += by[x & 3] / 16.0;
                base[y * w + x] = SKY.at(level);
            }
        }
    }

    private void paintFarTrees(PyRandom rng, Noise noise) {
        int[] colorsByLayer = {FAR.at(4), FAR.at(2), FAR.at(0)};
        for (int layer = 0; layer < 3; layer++) {
            int color = colorsByLayer[layer];
            for (int k = 0, n = (int) (w / (double) (58 - layer * 8)); k < n; k++) {
                int cx = rng.randrange(w);
                if (Math.abs(cx - moonX) < w * 0.045) continue; // never straight across the moon
                int half = rng.randint(2, 4);
                half += layer * rng.randint(1, 4);
                double lean = rng.uniform(-0.06, 0.06);
                for (int y = 0; y <= gy; y++) {
                    double wob = noise.at(cx * 0.1 + y / 30.0, layer) * 2 - 1;
                    double spread = half * (1 + Math.pow(y / (double) gy, 3) * 0.6);
                    int a = (int) (cx + lean * (gy - y) + wob - spread);
                    int b = (int) (cx + lean * (gy - y) + wob + spread);
                    for (int x = Math.max(0, a); x < Math.min(w, b + 1); x++) base[y * w + x] = color;
                }
            }
        }
        // Dark treeline against the meadow.
        for (int y = gy - (int) (h * 0.09); y < gy + 2; y++) {
            double t = (y - (gy - h * 0.09)) / (h * 0.09);
            for (int x = 0; x < w; x++) {
                if (t + (noise.at(x / 7.0, y / 4.0) - 0.5) * 0.9 > 0.42)
                    base[y * w + x] = noise.at(x / 3.0, y / 3.0 + 9) > 0.6 ? CANOPY.at(2) : CANOPY.at(1);
            }
        }
    }

    /** Leaf canopy with sky showing through, thickest away from the moon. */
    private void paintLace(Noise noise) {
        double reach = w * 0.30;
        for (int y = 0; y < (int) (gy * 0.92); y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.hypot(x - moonX, (y - moonY) * 1.1);
                double cover = Math.min(1.3, d / reach) * 0.9 + (noise.fbm(x / 26.0, y / 18.0) - 0.5) * 1.1;
                // Close the canopy overhead, so the opening reads as a gap in the leaves.
                cover += 1.1 * Math.pow(Math.max(0.0, 1 - y / (gy * 0.3)), 1.5);
                if (cover < 0.34) continue;
                double leaf = noise.at(x / 2.6 + 40, y / 2.2 + 70);
                if (leaf < 0.14 + cover * 0.62) {
                    double light = noise.at(x / 11.0 + 80, y / 9.0);
                    base[y * w + x] = light > 0.68 ? CANOPY.at(3) : light > 0.42 ? CANOPY.at(2) : CANOPY.at(1);
                }
            }
        }
    }

    private void paintStars() {
        for (int[] star : stars) {
            int i = star[1] * w + star[0];
            if (base[i] >= SKY.start && base[i] < SKY.start + 8) base[i] = STAR.at(0); // faint filler: the bright ones are the real stars
        }
    }

    private void paintGround(PyRandom rng, Noise noise) {
        double cx = w * 0.5;
        int span = h - gy;
        for (int y = gy; y < h; y++) {
            double t = (y - gy) / (double) span;
            int[] by = BAYER[y & 3];
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                ground[i] = 1;
                double patch = noise.fbm(x / 58.0, y / 11.0);
                double detail = noise.at(x / 8.0 + 50, y / 3.0);
                // A soft pool of light in the middle of the clearing, falling off to the edges.
                double radial = 1 - Math.min(1.0, Math.hypot((x - cx) / (w * 0.56), (t - 0.34) / 0.72));
                double light = radial * 3.1 + (detail - 0.5) * 1.5 - 0.1;
                if (t < 0.05) light *= t / 0.05;
                light += by[x & 3] / 16.0;
                base[i] = patch > 0.50 ? TEAL.at(light) : VIOLET.at(light * 1.1);
            }
        }
        // Dark rosettes pressed into the meadow.
        for (int n = 0, c = (int) (w * span / 900.0); n < c; n++) {
            int rx = rng.randrange(w), ry = rng.randrange(gy + 6, h);
            int s = 3 + (ry - gy) * 8 / span;
            for (int arm = -2; arm < 3; arm++) {
                for (int k = 1; k <= s; k++) {
                    int px = rx + (int) (arm * k * 0.75);
                    int py = ry - (int) (k * (0.8 - Math.abs(arm) * 0.28));
                    if (px >= 0 && px < w && gy < py && py < h) {
                        int i = py * w + px;
                        base[i] = base[i] >= VIOLET.start && base[i] < VIOLET.start + 6 ? VIOLET.at(0) : TEAL.at(0);
                    }
                }
            }
        }
        // Blue flecks: tiny flowers catching the moon, densest at the far edge of the clearing.
        for (int n = 0, c = (int) (w * span / 55.0); n < c; n++) {
            int fy = gy + (int) (span * Math.pow(rng.random(), 1.9) * 0.85);
            int fx = rng.randrange(w);
            if (noise.at(fx / 34.0, fy / 7.0 + 20) < 0.47) continue;
            for (int k = 0, m = rng.randint(1, 4); k < m; k++) {
                int px = fx + k;
                if (px < w) base[fy * w + px] = rng.random() < 0.72 ? FLECK.at(0) : FLECK.at(1);
            }
        }
    }

    /** How far the shore lies in a direction, as a multiple of the pond's nominal size: bays, points and a lopsided body. */
    public static double shoreRadius(double angle, Noise shape) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double wobble = (shape.at(c * 1.1 + 5, s * 1.1 + 5) - 0.5) * 0.85;
        wobble += (shape.at(c * 3.1 + 19, s * 3.1 + 19) - 0.5) * 0.42;
        wobble += (shape.at(c * 7.0 + 41, s * 7.0 + 41) - 0.5) * 0.14;
        return Math.max(0.78, 1.04 + wobble);
    }

    private void paintPond(Noise noise) {
        int cx = pondCx, cy = pondCy, rx = pondRx, ry = pondRy, mx = moonX;
        // Its own random numbers, so that the rest of the landscape stays exactly as it was.
        PyRandom detail = new PyRandom(seed ^ 0x5A0D);
        Noise shape = new Noise(detail);
        List<int[]> bank = new ArrayList<>();
        for (int y = pondY0; y < pondY1; y++) {
            for (int x = pondX0; x < pondX1; x++) {
                int i = y * w + x;
                double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry;
                double d = Math.hypot(dx, dy) / shoreRadius(Math.atan2(dy, dx), shape);
                if (d >= 1.0) {
                    // Mud and damp ground, wider in some places than others.
                    if (d < 1.05 + 0.13 * noise.at(x / 6.0, y / 3.0 + 50)) {
                        base[i] = ((x + y) & 1) != 0 ? TEAL.at(0) : VIOLET.at(0);
                        ground[i] = 0;
                        bank.add(new int[]{x, y});
                    }
                    continue;
                }
                ground[i] = 0;
                water[i] = 1;
                // Long horizontal bands that the palette rotation turns into slow shimmer.
                int band = (int) (noise.at(x / 22.0, y / 1.3 + 60) * 11 + y * 0.6);
                base[i] = WATER.at(band % 8);
                // Shallows at the edge go dark with the shore's reflection.
                if (d > 0.88 && ((x + y) & 1) != 0) base[i] = WATER.at(0);
                // The moon's broken reflection.
                if (Math.abs(x - cx - (mx - cx) * 0.25) < rx * 0.16 * (0.5 + noise.at(y * 3.1, 77)) && noise.at(x / 3.0, y * 1.7) > 0.45)
                    base[i] = MOON.at(1 + (y & 1));
            }
        }
        for (int v : water) waterPixels += v;
        if (bank.isEmpty()) return;
        // Stones half sunk at the water's edge.
        for (int n = 0, c = Math.max(4, bank.size() / 45); n < c; n++) {
            int[] at = detail.choice(bank);
            int size = detail.randint(1, 3);
            for (int sy = -1; sy < 1; sy++) {
                for (int sx = -size; sx <= size; sx++) {
                    int px = at[0] + sx, py = at[1] + sy;
                    if (px >= 0 && px < w && py >= 0 && py < h && Math.abs(sx) + Math.abs(sy) <= size)
                        base[py * w + px] = STONE.at(sy < 0 && sx <= 0 ? 3 : sy < 0 ? 2 : 1);
                }
            }
        }
        // Clumps of reeds, standing in front of the pond.
        for (int n = 0, c = Math.max(3, bank.size() / 70); n < c; n++) {
            int[] at = detail.choice(bank);
            int x = at[0], y = at[1];
            for (int m = 0, reeds = detail.randint(3, 7); m < reeds; m++) {
                int rx0 = x + detail.randint(-3, 3);
                int tall = detail.randint(5, 13);
                double lean = detail.uniform(-0.18, 0.18);
                for (int j = 0; j < tall; j++) {
                    int px = (int) (rx0 + lean * j), py = y - j;
                    if (px >= 0 && px < w && py >= 0 && py < h) fg[py * w + px] = LEAF.at(j < tall / 2 ? 2 : 3);
                }
                if (detail.random() < 0.3) { // a bulrush head
                    int px = (int) (rx0 + lean * tall), py = y - tall;
                    for (int k = 0; k < 3; k++)
                        if (px >= 0 && px < w && py - k >= 0 && py - k < h) fg[(py - k) * w + px] = TRUNK.at(4);
                }
            }
        }
    }

    private void paintBoulders(PyRandom rng, Noise noise) {
        int span = h - gy;
        for (int b = 0, count = rng.randint(2, 3); b < count; b++) {
            double t = 0;
            int bx = 0, by = 0;
            for (int k = 0; k < 30; k++) {
                t = rng.uniform(0.25, 0.8);
                bx = (int) (w * rng.uniform(0.2, 0.8));
                by = gy + (int) (t * span);
                if (water[by * w + bx] == 0) break;
            }
            int brx = (int) ((7 + 14 * t) * rng.uniform(0.8, 1.2));
            int bry = (int) (brx * 0.62);
            for (int y = by - bry; y < by + 2; y++) {
                for (int x = bx - brx; x <= bx + brx; x++) {
                    if (!(x >= 0 && x < w && y >= 0 && y < h)) continue;
                    double d = Math.hypot((x - bx) / (double) brx, (y - by) / (double) bry);
                    d += (noise.at(x / 5.0, y / 5.0 + 90) - 0.5) * 0.3;
                    if (d >= 1) continue;
                    double facing = -(y - by) / (double) bry * 0.7 + (moonX - bx) / (double) w * 0.5 * (x - bx) / brx;
                    double level = 1.2 + facing * 2.5 + BAYER[y & 3][x & 3] / 16.0;
                    int i = y * w + x;
                    fg[i] = STONE.at(level);
                    ground[i] = 0;
                }
            }
            // Contact shadow.
            for (int x = bx - brx; x <= bx + brx; x++)
                if (x >= 0 && x < w && by + 2 < h) base[(by + 2) * w + x] = CANOPY.at(1);
            lichen.add(new int[]{bx - brx / 2, by - bry + 1, bx + brx / 2, by - 1});
        }
    }

    /** Stamp a tapered, rim-lit limb along (x, y, radius) waypoints. */
    private void limb(double[][] pts, Ramp shade, double rimX, double rimY, Noise noise) {
        for (int p = 0; p + 1 < pts.length; p++) {
            double[] a = pts[p], b = pts[p + 1];
            double length = Math.max(1.0, Math.hypot(b[0] - a[0], b[1] - a[1]));
            int steps = (int) (length / Math.max(1.0, Math.min(a[2], b[2]) / 3.0)) + 1;
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                double cx = a[0] + (b[0] - a[0]) * t, cy = a[1] + (b[1] - a[1]) * t;
                double r = a[2] + (b[2] - a[2]) * t;
                r += (noise.at(cx / 13, cy / 13) - 0.5) * r * 0.25;
                int ir = (int) r + 1;
                for (int dy = -ir; dy <= ir; dy++) {
                    int py = (int) cy + dy;
                    if (py < 0 || py >= h) continue;
                    for (int dx = -ir; dx <= ir; dx++) {
                        int px = (int) cx + dx;
                        if (px < 0 || px >= w || dx * dx + dy * dy > r * r) continue;
                        double across = (dx * rimX + dy * rimY) / Math.max(1.0, r);
                        double bark = noise.at(px / 3.2 + 7, py / 11.0 + 3);
                        double level = 0.9 + bark * 1.8;
                        if (across > 0.6) level += (across - 0.6) * 10;
                        else if (across < -0.25) level = bark * 0.9;
                        fg[py * w + px] = shade.at(level);
                    }
                }
            }
        }
    }

    private static double[][] curve(double[] p0, double[] p1, double[] p2, int n) {
        double[][] pts = new double[n + 1][];
        for (int k = 0; k <= n; k++) {
            double t = k / (double) n, u = 1 - t;
            pts[k] = new double[]{u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
                    u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
                    u * u * p0[2] + 2 * u * t * p1[2] + t * t * p2[2]};
        }
        return pts;
    }

    private static double[] v(double x, double y, double r) { return new double[]{x, y, r}; }

    private double[][] mainCurve(double xBase, double lean, double wide) {
        return curve(v(xBase, h + 16, wide * 1.15), v(xBase + (lean - xBase) * 0.2, h * 0.52, wide * 0.72), v(lean, -14, wide * 0.5), 24);
    }
    private double[][] secondCurve(double x2, boolean left, double s) {
        return curve(v(x2 - (left ? -1 : 1) * 40 * s, h + 6, 11 * s), v(x2, h * 0.55, 8 * s), v(x2 + (left ? 1 : -1) * 40 * s, -8, 5 * s), 18);
    }
    private double[][] farCurve(double xf, boolean left, double s) {
        return curve(v(xf, h + 8, 10 * s), v(xf + (left ? -8 : 8) * s, h * 0.5, 7 * s), v(xf - (left ? -14 : 14) * s, 0, 5 * s), 14);
    }
    private double[][] boughCurve(boolean left, double s) {
        double far = left ? w + 10 : -10;
        return curve(v(far, h * 0.13, 15 * s), v(w * (left ? 0.80 : 0.20), h * 0.16, 10 * s), v(w * (left ? 0.56 : 0.44), -10, 4 * s), 20);
    }

    /** The curves the trunks and the great bough are painted along, for anything that climbs them. */
    private Map<String, double[][]> treePaths() {
        double s = h / 270.0;
        boolean left = side > 0;
        double wide = 26 * s;
        double xBase = left ? w * 0.055 : w * 0.945, lean = left ? w * 0.13 : w * 0.87;
        Map<String, double[][]> m = new HashMap<>();
        m.put("main", mainCurve(xBase, lean, wide));
        m.put("second", secondCurve(w * (left ? 0.165 : 0.835), left, s));
        m.put("far", farCurve(w * (left ? 0.965 : 0.035), left, s));
        m.put("bough", boughCurve(left, s));
        return m;
    }

    private void paintTrunks(PyRandom rng, Noise noise) {
        double s = h / 270.0;
        boolean left = side > 0; // big trunk opposite the pond
        double wide = 26 * s;
        double xBase = left ? w * 0.055 : w * 0.945, lean = left ? w * 0.13 : w * 0.87;
        // The moon sits inward of a left-hand trunk.
        double rimX = left ? 1 : -1, rimY = -0.2;
        limb(mainCurve(xBase, lean, wide), TRUNK, rimX, rimY, noise);
        // Root flare.
        for (int sign = -1; sign <= 1; sign += 2)
            limb(curve(v(xBase + sign * wide * 0.7, h * 0.84, wide * 0.42), v(xBase + sign * wide * 1.3, h * 0.95, wide * 0.25),
                    v(xBase + sign * wide * 2.3, h + 6, wide * 0.1), 8), TRUNK, rimX, rimY, noise);
        // A second, slimmer trunk leaning away from the first.
        limb(secondCurve(w * (left ? 0.165 : 0.835), left, s), TRUNK, rimX, rimY, noise);
        // Bough across the top from the far side.
        limb(boughCurve(left, s), TRUNK, 0, 1, noise);
        limb(curve(v(w * (left ? 0.84 : 0.16), h * 0.15, 6 * s), v(w * (left ? 0.72 : 0.28), h * 0.08, 3.5 * s),
                v(w * (left ? 0.64 : 0.36), h * 0.14, 1 * s), 14), TRUNK, 0, 1, noise);
        // Far-side distant trunk, narrow, so the frame isn't lopsided.
        limb(farCurve(w * (left ? 0.965 : 0.035), left, s), TRUNK, left ? -1 : 1, -0.2, noise);
        // Vines hanging in slack curves.
        for (int k = 0; k < 4; k++) {
            double x0 = left ? w * rng.uniform(0.55, 0.95) : w * rng.uniform(0.05, 0.45);
            double drop = h * rng.uniform(0.10, 0.28);
            double x1 = x0 + rng.uniform(-30, 30) * s;
            for (int j = 0, n = (int) (drop * 1.5); j < n; j++) {
                double t = j / (drop * 1.5);
                double x = x0 + (x1 - x0) * t + Math.sin(t * Math.PI) * 8 * s;
                double y = t * drop + h * 0.04;
                if ((int) x >= 0 && (int) x < w) fg[(int) y * w + (int) x] = TRUNK.at(1);
            }
        }
        // Lichen patches on the bark, where Conway's rules run for anyone who looks closely.
        int barkX = (int) (xBase + (lean - xBase) * 0.28);
        int size = (int) (15 * s);
        lichen.add(new int[]{Math.max(1, barkX - size), (int) (h * 0.56), Math.min(w - 1, barkX + size), (int) (h * 0.56) + size + 4});
        lichen.add(new int[]{Math.max(1, barkX - size / 2), (int) (h * 0.30), Math.min(w - 1, barkX + size), (int) (h * 0.30) + size});
    }

    /** Trunks and rocks each lay a dithered shadow on the meadow, reaching away from the moon. */
    private void paintCastShadows() {
        int span = h - gy;
        for (int x = 0; x < w; x++) {
            // Find where something solid meets the ground in this column.
            for (int y = h - 2; y > gy; y--) {
                if (fg[y * w + x] != 0 && fg[(y + 1) * w + x] == 0) {
                    int reach = (int) (6 + 22 * (y - gy) / (double) span);
                    int away = x > moonX ? 1 : -1;
                    for (int k = 1; k < reach; k++) {
                        int px = x + away * k, py = y + 1 + k / 5;
                        if (px >= 0 && px < w && py < h) {
                            int i = py * w + px;
                            if (ground[i] != 0 && fg[i] == 0 && (((px ^ py) & 1) != 0 || k < reach / 2)) base[i] = DARK[base[i]];
                        }
                    }
                    break;
                }
            }
        }
    }

    /** Star-leaved shrubs banked at the edges of the clearing. */
    private void paintBushes(PyRandom rng, Noise noise) {
        double[][] banks = {{-0.02, 0.17}, {1.0, 0.16}};
        for (double[] bank : banks) {
            double sx = bank[0], spread = bank[1];
            for (int n = 0, c = (int) (w / 7.0); n < c; n++) {
                double u = rng.random();
                double bx = w * (sx + (sx < 0.5 ? u * spread : -u * spread));
                double by = gy + (h - gy) * (0.02 + rng.random() * 0.46) * (1 - u * 0.3);
                int r = rng.randint(3, 6);
                double shade = 0.35 + 2.3 * (1 - u) * noise.at(bx / 20, by / 20);
                for (int arm = 0; arm < 6; arm++) {
                    double ang = arm * Math.PI / 3 + 0.26;
                    for (int k = 0; k <= r; k++) {
                        int px = (int) (bx + Math.cos(ang) * k), py = (int) (by + Math.sin(ang) * k * 0.85);
                        if (px >= 0 && px < w && py >= 0 && py < h && fg[py * w + px] == 0)
                            fg[py * w + px] = LEAF.at(shade + (k == r ? 1 : 0));
                    }
                }
                for (int dy = -r + 1; dy < r; dy++) {
                    for (int dx = -r + 1; dx < r; dx++) {
                        int px = (int) bx + dx, py = (int) by + dy;
                        // The shrub's shadowy inside, not a leaf: only the stars should turn in autumn.
                        if (px >= 0 && px < w && py >= 0 && py < h && fg[py * w + px] == 0 && dx * dx + dy * dy < r * r)
                            fg[py * w + px] = CANOPY.at(((px ^ py) & 1) != 0 ? 1 : 0);
                    }
                }
            }
        }
    }

    private void put(double x, double y, int v) {
        int ix = (int) x, iy = (int) y;
        if (ix >= 0 && ix < w && iy >= 0 && iy < h) fg[iy * w + ix] = v;
    }

    /** Thorned twigs, thickest at the corners. */
    private void twig(PyRandom rng, double x, double y, double ang, double length, int depth) {
        for (int j = 0, n = (int) length; j < n; j++) {
            x += Math.cos(ang);
            y += Math.sin(ang);
            ang += rng.uniform(-0.18, 0.18);
            put(x, y, CANOPY.at(0));
            if (depth > 0 && rng.random() < 0.13) {
                int sign = rng.choice(-1, 1);
                twig(rng, x, y, ang + sign * rng.uniform(0.5, 1.1), length * 0.5, depth - 1);
            } else if (rng.random() < 0.18) put(x + rng.choice(-1, 1), y, CANOPY.at(0));
        }
    }

    /** Near-black grass, thorns and sprigs along the bottom edge. */
    private void paintUndergrowth(PyRandom rng, Noise noise) {
        double s = h / 270.0;
        // Rising shadow along the very bottom.
        for (int x = 0; x < w; x++) {
            double top = h - 6 * s - noise.at(x / 25.0, 3.3) * 16 * s;
            for (int y = (int) top; y < h; y++) put(x, y, y > top + 2 ? CANOPY.at(0) : CANOPY.at(1));
        }
        // Grass blades.
        for (int n = 0, c = (int) (w * 0.75); n < c; n++) {
            int x = rng.randrange(w);
            double bh = rng.uniform(7, 30) * s * (Math.abs(x - w / 2.0) > w * 0.3 ? 1.3 : 0.75);
            double bend = rng.uniform(-0.5, 0.5);
            double y0 = h - rng.uniform(0, 8) * s;
            int v = rng.random() < 0.82 ? CANOPY.at(rng.randint(0, 1)) : LEAF.at(1);
            for (int j = 0, m = (int) bh; j < m; j++) {
                double t = j / bh;
                put(x + bend * j * t * 1.2, y0 - j, v);
            }
        }
        for (int corner = 0, c = (int) (9.0 * w / 480); corner < c; corner++) {
            double edge = Math.pow(rng.random(), 2) * 0.3;
            double x = w * (rng.random() < 0.5 ? edge : 1 - edge);
            double ang = -Math.PI / 2 + rng.uniform(-0.6, 0.6);
            twig(rng, x, h, ang, rng.uniform(28, 60) * s, 3);
        }
        // A broad-leaved sprig silhouetted in one corner.
        double sx = w * (side > 0 ? 0.94 : 0.06);
        double y = h * 0.98;
        for (int k = 0; k < 9; k++) {
            y -= 8 * s;
            double x = sx + Math.sin(k * 0.7) * 6 * s;
            for (int lr = -1; lr <= 1; lr += 2) {
                double cx = x + lr * 6 * s, cy = y - 1 * s, rx = 4.5 * s, ry = 2.6 * s;
                for (int dy = (int) (-ry) - 1; dy < (int) ry + 2; dy++)
                    for (int dx = (int) (-rx) - 1; dx < (int) rx + 2; dx++)
                        if (Math.pow(dx / rx, 2) + Math.pow(dy / ry, 2) < 1) put(cx + dx, cy + dy - lr * dx * 0.35, CANOPY.at(0));
            }
            for (int j = 0, m = (int) (8 * s); j < m; j++) put(x, y + j, CANOPY.at(0));
        }
    }
}
