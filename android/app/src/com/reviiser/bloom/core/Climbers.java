package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Life on the trees: hanging moss on the boughs, and the animals that use the trunks. Trunks and boughs are foreground
 * scenery, so anything on them is drawn over the foreground, following the same curves the trees were painted along.
 */
public final class Climbers {
    private Climbers() {}

    /** Turn a right-facing side view so that it climbs, head upward. */
    static String[] rotateUp(String[] rows) {
        int width = 0;
        for (String r : rows) width = Math.max(width, r.length());
        String[] out = new String[width];
        for (int x = width - 1, n = 0; x >= 0; x--, n++) {
            StringBuilder b = new StringBuilder();
            for (String r : rows) b.append(x < r.length() ? r.charAt(x) : '.');
            out[n] = b.toString();
        }
        return out;
    }

    static final String[] WOODPECKER = {"..R.", ".RRo", ".##w", "###w", "#+#w", "#+#.", "#.#.", "#..."};
    static final String[] WOODPECKER_TAP = {"...R", "..RR", ".##o", "###w", "#+#w", "#+#.", "#.#.", "#..."};
    static final String[] CREEPER = {".#.", "##w", "#+w", "#+.", "#..", "#.."};
    static final String[] MARTEN = {"...........#.#.", "...........###o", "#.........####.", "##...#######w..", ".###########...", "...##.....##...", "...#.......#..."};
    static final String[] BIRD_FLIGHT = {"#..#", "####", ".##."};

    /** A path of (x, y, radius) points up a trunk, one per pixel of travel, trimmed to what's on screen. */
    static List<double[]> routeOn(Eco eco, String name, boolean fromGround) {
        double[][] points = eco.s.paths.get(name);
        List<double[]> route = new ArrayList<>();
        for (int p = 0; p + 1 < points.length; p++) {
            double[] a = points[p], b = points[p + 1];
            int steps = Math.max(1, (int) Math.hypot(b[0] - a[0], b[1] - a[1]));
            for (int k = 0; k < steps; k++) {
                double t = k / (double) steps;
                route.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
            }
        }
        if (!fromGround) return route;
        double foot = eco.gy + eco.span * 0.72;
        List<double[]> out = new ArrayList<>();
        for (double[] p : route) if (1 < p[1] && p[1] < foot && 0 <= p[0] && p[0] < eco.w) out.add(p);
        return out;
    }

    /** Up the far trunk until it meets the great bough, then out along the bough toward its tip. */
    static List<double[]> boughRoute(Eco eco) {
        List<double[]> far = routeOn(eco, "far", true);
        List<double[]> bough = new ArrayList<>();
        for (double[] p : routeOn(eco, "bough", false)) if (0 <= p[0] && p[0] < eco.w && p[1] > 2) bough.add(p);
        if (far.isEmpty() || bough.isEmpty()) return far;
        int join = 0, start = 0;
        double best = Double.MAX_VALUE;
        for (int k = 0; k < far.size(); k++) {
            double v = Math.abs(far.get(k)[1] - bough.get(0)[1]) + Math.abs(far.get(k)[0] - bough.get(0)[0]);
            if (v < best) { best = v; join = k; }
        }
        best = Double.MAX_VALUE;
        for (int k = 0; k < bough.size(); k++) {
            double v = Math.abs(bough.get(k)[0] - far.get(join)[0]);
            if (v < best) { best = v; start = k; }
        }
        List<double[]> out = new ArrayList<>(far.subList(0, join));
        out.addAll(bough.subList(start, bough.size()));
        return out;
    }

    private static List<List<double[]>> longEnough(List<double[]> a, List<double[]> b, List<double[]> c) {
        List<List<double[]>> out = new ArrayList<>();
        if (a.size() > 20) out.add(a);
        if (b.size() > 20) out.add(b);
        if (c.size() > 20) out.add(c);
        return out;
    }

    /** Over the foreground, unlike nearly everything else, because the tree is what it's standing on. */
    static void drawClinging(Eco eco, Overlay overlay, String[] rows, int x, int y, int[] colors, boolean flip) {
        int w = eco.w, h = eco.h;
        for (int ry = 0; ry < rows.length; ry++) {
            String row = rows[ry];
            int n = row.length();
            for (int rx = 0; rx < n; rx++) {
                char ch = row.charAt(rx);
                if (ch == '.') continue;
                int c = colors[ch];
                int px = x + (flip ? n - 1 - rx : rx), py = y + ry;
                if (c != 0 && 0 <= px && px < w && 0 <= py && py < h) overlay.put(py * w + px, c);
            }
        }
    }

    /** Goes up a trunk in bursts. If the route turns along a bough, it runs along it. */
    abstract static class Climber extends Wildlife.Creature {
        double speed = 26, burst = 1.3, restLo = 0.4, restHi = 2.0, spiral = 0.0;
        List<double[]> route;
        double s;
        int pace, halt;
        boolean down;

        Climber(String name, Wildlife wild, Animal ind) { super(name, wild, ind); }

        void begin(List<double[]> r) {
            route = r;
            s = 0.0;
            pace = seconds(rng.uniform(0.5, burst));
            halt = 0;
            down = false;
        }

        int where() { return Math.max(0, Math.min(route.size() - 1, (int) s)); }

        /** Move along the route. Returns false once it has gone off the end or back to the bottom. */
        boolean travel() {
            if (halt > 0) {
                halt--;
                return true;
            }
            s += (down ? -1 : 1) * speed / Math.max(1, eco.fps);
            pace--;
            if (pace <= 0) {
                pace = seconds(rng.uniform(0.4, burst));
                halt = seconds(rng.uniform(restLo, restHi));
            }
            return 0 <= s && s < route.size();
        }

        /** Draw it. A spiralling climber passes round the back of the trunk, out of sight. */
        void place(Overlay overlay, String[] climbRows, String[] sideRows, int[] colors) {
            int k = where();
            double[] p = route.get(k);
            double x = p[0], y = p[1], radius = p[2];
            double[] a = route.get(Math.max(0, k - 3)), b = route.get(Math.min(route.size() - 1, k + 3));
            double dx = b[0] - a[0], dy = b[1] - a[1];
            if (Math.abs(dx) > Math.abs(dy)) { // going along, not up
                drawClinging(eco, overlay, sideRows, (int) x - sideRows[0].length() / 2, (int) (y - radius) - sideRows.length + 1, colors, (down ? -dx : dx) < 0);
                return;
            }
            double px = x;
            if (spiral != 0) {
                double angle = s * spiral;
                if (Math.cos(angle) < -0.15) return;
                px = x + Math.sin(angle) * radius * 0.75;
            }
            String[] rows = climbRows;
            if (down) {
                rows = climbRows.clone();
                for (int i = 0; i < rows.length; i++) rows[i] = climbRows[climbRows.length - 1 - i];
            }
            drawClinging(eco, overlay, rows, (int) px - rows[0].length() / 2, (int) y, colors, false);
        }
    }

    /** Up the trunk in the quick stop-start way they have, along the bough if that's the tree, and away. */
    static final class ClimbingSquirrel extends Climber {
        boolean caught;

        ClimbingSquirrel(Wildlife wild, Animal who) {
            super("climbing_squirrel", wild, who);
            speed = 34;
            if (ind == null) ind = wild.evo.pick("squirrel", 1, true).get(0);
            g = ind.g;
            List<List<double[]>> options = longEnough(routeOn(eco, "main", true), routeOn(eco, "second", true), boughRoute(eco));
            if (options.isEmpty()) options.add(new ArrayList<>(Arrays.asList(new double[]{0, 0, 1})));
            begin(rng.choice(options));
        }

        @Override boolean frame(Overlay overlay) {
            if (caught) return false;
            Wildlife.Creature marten = wild.find("pine_marten");
            if (marten instanceof PineMarten && ((PineMarten) marten).prey == this) { // it has seen what's coming
                halt = 0;
                speed = 39 + 14 * g.d("speed");
            }
            if (!travel()) return false;
            String[] side = Sprites.animalRows("squirrel", "run", g);
            place(overlay, rotateUp(side), side, Evolution.coatColors(g));
            return true;
        }
    }

    /** Long, dark and very fast on a trunk. It hunts squirrels up where they feel safe. */
    static final class PineMarten extends Climber {
        static final int[] COLORS = Oddities.colors('#', LITTER.at(3), '+', LITTER.at(2), 'w', HARE.at(3), 'o', CANOPY.at(0));
        ClimbingSquirrel prey;
        final String[] rows;

        PineMarten(Wildlife wild) {
            super("pine_marten", wild, null);
            speed = 64;
            burst = 2.2;
            restLo = 0.1;
            restHi = 0.4;
            Wildlife.Creature found = wild.find("climbing_squirrel");
            prey = found instanceof ClimbingSquirrel ? (ClimbingSquirrel) found : null;
            if (prey != null) {
                begin(prey.route); // from the foot of the same tree, with some catching up to do
                halt = seconds(0.4);
            } else begin(rng.choice(longEnough(routeOn(eco, "main", true), routeOn(eco, "second", true), boughRoute(eco))));
            rows = rotateUp(MARTEN);
        }

        @Override boolean frame(Overlay overlay) {
            if (prey != null && !prey.caught && wild.visitors.contains(prey)) {
                if (s > 6 && s >= prey.s - 2) {
                    prey.caught = true;
                    wild.evo.kill("squirrel", prey.ind);
                    int[] at = eco.randomGround();
                    wild.enrich(at[0], at[1], 0.05);
                    wild.note("squirrel caught by pine marten");
                    down = true;
                    prey = null;
                }
            }
            if (!travel()) return false;
            place(overlay, rows, MARTEN, COLORS);
            wild.odd.sighted(name);
            return true;
        }
    }

    /** Lands on a trunk, works up it in hops, and drills. Beetles under the bark do not like this. */
    static final class Woodpecker extends Wildlife.Creature {
        static final int[] COLORS = Oddities.colors('#', CANOPY.at(0), '+', CANOPY.at(2), 'w', HARE.at(3), 'R', FLOWER[4].at(1), 'o', HARE.at(2));
        final List<double[]> route;
        int k, taps, wait, hops;
        double x, y;
        String state = "in";

        Woodpecker(Wildlife wild) {
            super("woodpecker", wild, null);
            route = rng.choice(longEnough(routeOn(eco, "main", true), routeOn(eco, "second", true), routeOn(eco, "far", true)));
            k = (int) (route.size() * rng.uniform(0.25, 0.55));
            x = rng.random() < 0.5 ? -6.0 : eco.w + 6.0;
            y = eco.h * rng.uniform(0.1, 0.3);
            hops = rng.randint(2, 5);
        }

        @Override boolean frame(Overlay overlay) {
            double px = route.get(k)[0], py = route.get(k)[1];
            if (!state.equals("work")) {
                boolean in = state.equals("in");
                double tx = in ? px : x < eco.w / 2.0 ? -10 : eco.w + 10, ty = in ? py : eco.h * 0.1;
                double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy), step = 60.0 / Math.max(1, eco.fps);
                if (d <= step) {
                    if (!in) return false;
                    state = "work";
                    wait = seconds(rng.uniform(0.5, 1.5));
                } else {
                    x += dx / d * step;
                    y += dy / d * step;
                }
                drawClinging(eco, overlay, BIRD_FLIGHT, (int) x, (int) y, COLORS, false);
                wild.odd.sighted(name);
                return true;
            }
            String[] rows = WOODPECKER;
            if (taps > 0) {
                rows = (eco.frameNo & 1) != 0 ? WOODPECKER_TAP : WOODPECKER;
                taps--;
                if (taps == 0) {
                    Wildlife.Prey prey = wild.nearestPrey(px, eco.gy + 40, 9999, false, false);
                    if (prey != null && rng.random() < 0.25) wild.take(prey);
                    wait = seconds(rng.uniform(0.5, 2.5));
                }
            } else if (wait > 0) wait--;
            else {
                hops--;
                if (hops < 0) {
                    state = "out";
                    x = px;
                    y = py;
                } else {
                    k = Math.min(route.size() - 1, k + rng.randint(4, 12));
                    taps = seconds(rng.uniform(0.8, 2.0));
                }
            }
            drawClinging(eco, overlay, rows, (int) route.get(k)[0], (int) route.get(k)[1], COLORS, false);
            return true;
        }
    }

    /** A tiny brown bird that spirals up one trunk, flutters down to the foot of the next, and starts again. */
    static final class Treecreeper extends Climber {
        static final int[] COLORS = Oddities.colors('#', LITTER.at(3), '+', LITTER.at(2), 'w', HARE.at(3));
        int left;

        Treecreeper(Wildlife wild) {
            super("treecreeper", wild, null);
            speed = 11;
            burst = 1.8;
            restLo = 0.2;
            restHi = 0.9;
            spiral = 0.16;
            left = rng.randint(1, 3);
            nextTree();
        }

        private void nextTree() { begin(rng.choice(longEnough(routeOn(eco, "main", true), routeOn(eco, "second", true), routeOn(eco, "far", true)))); }

        @Override boolean frame(Overlay overlay) {
            if (!travel() || s > route.size() * 0.75) {
                left--;
                if (left <= 0) return false;
                nextTree();
            }
            place(overlay, CREEPER, CREEPER, COLORS);
            wild.odd.sighted(name);
            return true;
        }
    }

    /** Grey-green strands that slowly lengthen from the undersides of the great boughs, faster in wet weather. */
    public static final class HangingMoss {
        static final class Strand { int x, y, shown; long seed; List<int[]> pix; }

        final Eco eco;
        final PyRandom rng;
        List<int[]> anchors;
        final List<Strand> strands = new ArrayList<>();
        int swing;

        HangingMoss(Eco eco) {
            this.eco = eco;
            rng = new PyRandom(eco.s.seed ^ 0x3055);
        }

        /** The undersides of limbs: solid above, open air below, and running roughly level. */
        private void findAnchors() {
            int[] fg = eco.c.fg;
            int w = eco.w, top = (int) (eco.gy * 0.85);
            List<int[]> spots = new ArrayList<>();
            for (int x = 2; x < w - 2; x++) {
                for (int y = top; y > 4; y--) {
                    int i = y * w + x;
                    if (fg[i] != 0 && fg[i + w] == 0 && fg[i - w] != 0 && fg[i - 2 * w] != 0 && eco.c.cap[i + w] == 0) {
                        // Level ground to the sides, or this is the flank of a trunk.
                        int level = 0;
                        for (int dx = -3; dx <= 3; dx += 6) {
                            for (int dy = -2; dy <= 2; dy++) {
                                int k = (y + dy) * w + x + dx;
                                if (fg[k] != 0 && fg[k + w] == 0) {
                                    level++;
                                    break;
                                }
                            }
                        }
                        if (level == 2) spots.add(new int[]{x, y + 1});
                        break;
                    }
                }
            }
            rng.shuffle(spots);
            anchors = spots;
        }

        /** One strand, as pixels from the bough downward: a drifting main thread with short wisps. */
        private List<int[]> pixels(long seed) {
            PyRandom r = new PyRandom(seed);
            int length = (int) (r.uniform(6, 26) * eco.scaleS);
            List<int[]> out = new ArrayList<>();
            double drift = 0.0;
            for (int j = 0; j < length; j++) {
                drift += r.choice(-0.25, 0.0, 0.0, 0.25);
                double t = j / (double) Math.max(1, length);
                int shade = t < 0.3 ? STEM.at(3) : t < 0.75 ? STEM.at(2) : STEM.at(1); // never a leaf colour: moss doesn't turn in autumn
                out.add(new int[]{(int) drift, j, shade});
                // Thick where it hangs from the limb, thinning into wisps.
                if (r.random() < 0.8 * (1 - t)) out.add(new int[]{(int) drift + r.choice(-1, 1), j, t < 0.5 ? STEM.at(2) : STEM.at(1)});
                if (t < 0.35 && r.random() < 0.4) out.add(new int[]{(int) drift + r.choice(-2, 2), j, STEM.at(1)});
            }
            return out;
        }

        void tick() {
            if (eco.tickNo % 30 != 0) return;
            if (anchors == null) findAnchors();
            if (anchors.isEmpty()) return;
            boolean dormant = eco.climate.dormant();
            int limit = Math.min((int) (anchors.size() * 0.5), (int) (70 * eco.cfg.density * eco.w / 480));
            if (strands.size() < limit && rng.random() < (dormant ? 0.1 : 0.3)) clump(rng.choice(anchors), false);
            boolean wet = eco.raining != 0 || eco.climate.fog != 0;
            for (Strand strand : strands)
                if (strand.shown < strand.pix.size() && rng.random() < (wet ? 0.5 : 0.12) * (dormant ? 0.2 : 1.0)) show(strand, strand.shown + 1);
        }

        /** Moss hangs in tufts: a few strands together, one of them the longest. */
        private void clump(int[] anchor, boolean shown) {
            List<int[]> near = new ArrayList<>();
            for (int[] a : anchors) if (Math.abs(a[0] - anchor[0]) <= 5) near.add(a);
            int count = Math.min(near.size(), rng.randint(2, 5));
            for (int idx : rng.sampleIndices(near.size(), count)) {
                Strand strand = add(near.get(idx));
                if (shown) show(strand, (int) (strand.pix.size() * rng.uniform(0.4, 1.0)));
            }
        }

        private Strand add(int[] anchor) {
            Strand st = new Strand();
            st.x = anchor[0];
            st.y = anchor[1];
            st.seed = rng.randrange(1 << 30);
            st.pix = pixels(st.seed);
            strands.add(st);
            return st;
        }

        private void show(Strand strand, int upto) {
            Canvas c = eco.c;
            for (int k = strand.shown; k < upto && k < strand.pix.size(); k++) {
                int[] d = strand.pix.get(k);
                int px = strand.x + d[0], py = strand.y + d[1];
                if (0 <= px && px < eco.w && 0 <= py && py < eco.h) {
                    int i = py * eco.w + px;
                    c.cap[i] = d[2];
                    c.refresh(i);
                }
            }
            strand.shown = upto;
        }

        /** A gust sets the strands swaying: only the lower half of each moves. */
        void frame() {
            Wildlife.Creature found = eco.wild.find("wind");
            int target = found instanceof Wildlife.Wind ? (eco.frameNo % 16 < 8 ? 1 : 0) * ((Wildlife.Wind) found).dir : 0;
            if (target == swing || eco.frameNo % 8 != 0) return;
            Canvas c = eco.c;
            int w = eco.w, h = eco.h;
            for (Strand strand : strands) {
                int half = strand.pix.size() / 2, end = Math.min(strand.shown, strand.pix.size());
                for (int k = half; k < end; k++) {
                    int[] d = strand.pix.get(k);
                    int old = strand.x + d[0] + swing, py = strand.y + d[1];
                    if (0 <= old && old < w && 0 <= py && py < h) {
                        int i = py * w + old;
                        c.cap[i] = 0;
                        c.refresh(i);
                    }
                }
                for (int k = half; k < end; k++) {
                    int[] d = strand.pix.get(k);
                    int px = strand.x + d[0] + target, py = strand.y + d[1];
                    if (0 <= px && px < w && 0 <= py && py < h) {
                        int i = py * w + px;
                        c.cap[i] = d[2];
                        c.refresh(i);
                    }
                }
            }
            swing = target;
        }

        // ---- persistence -------------------------------------------------------

        List<Object> toState() {
            List<Object> rows = new ArrayList<>();
            for (Strand s : strands) rows.add(new double[]{s.x, s.y, s.seed, s.shown});
            return rows;
        }

        void load(Object saved) {
            List<?> rows = (List<?>) saved;
            if (rows != null && !rows.isEmpty()) {
                for (Object o : rows) {
                    double[] row = Json.doubles(o);
                    Strand st = new Strand();
                    st.x = (int) row[0];
                    st.y = (int) row[1];
                    st.seed = (long) row[2];
                    st.pix = pixels(st.seed);
                    strands.add(st);
                    show(st, Math.min((int) row[3], st.pix.size()));
                }
                return;
            }
            prewarm();
        }

        /** A forest that has been here a while already has some moss on its boughs. */
        void prewarm() {
            findAnchors();
            if (anchors.isEmpty()) return;
            for (int k = 0, e = Math.min(9, anchors.size() / 12); k < e; k++) clump(rng.choice(anchors), true);
        }
    }
}
