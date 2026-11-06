package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The year: seasons from your real calendar, weather, snow, ice and tracks in the snow. Winter here is a slowing-down,
 * never a killing. Plants go dormant under the snow, small things hibernate, and whoever stays awake goes hungry
 * enough that numbers fall, but a species can't be wiped out by the cold.
 */
public final class Climate {
    public static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};
    static final int[] LITTER_HUE = {LITTER.at(2), LITTER.at(3), LITTER.at(4)};
    static final int[] LEAF_RUSSET = {FLOWER[4].at(0), FLOWER[4].at(0), FLOWER[4].at(1)};
    static final int[] LEAF_OCHRE = {LITTER.at(4), FLOWER[5].at(0), FLOWER[5].at(0)};
    static final int[] LEAF_BRIGHT = {FLOWER[4].at(0), FLOWER[4].at(1), FLOWER[5].at(1)}; // the odd fresh one
    // Fallen leaves, smallest first: a few pixels, in several attitudes.
    static final int[][][] LEAF_SHAPES = {
        {{0, 0}, {1, 0}},
        {{0, 0}, {0, 1}},
        {{0, 0}, {1, 0}, {1, 1}},
        {{1, 0}, {0, 1}, {1, 1}},
        {{1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}},
        {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
    };
    static final Map<String, Double> PINNED_TEMPERATURE = new HashMap<>();
    static final Map<String, Integer> PEAK_DAY = new LinkedHashMap<>();
    static final Map<String, int[][]> TRACK_SHAPE = new HashMap<>();
    static final Map<String, Integer> TRACK_STRIDE = new HashMap<>(); // how far apart prints fall
    private static void track(String kind, int stride, int[][] shape) { TRACK_SHAPE.put(kind, shape); TRACK_STRIDE.put(kind, stride); }
    static {
        PINNED_TEMPERATURE.put("spring", 11.0);
        PINNED_TEMPERATURE.put("summer", 24.0);
        PINNED_TEMPERATURE.put("autumn", 9.0);
        PINNED_TEMPERATURE.put("winter", -4.0);
        PEAK_DAY.put("winter", 5);
        PEAK_DAY.put("spring", 95);
        PEAK_DAY.put("summer", 186);
        PEAK_DAY.put("autumn", 278);
        track("rabbit", 7, new int[][]{{0, 0}, {1, 0}, {3, -1}, {3, 1}});
        track("fox", 4, new int[][]{{0, 0}, {1, 0}});
        track("hedgehog", 3, new int[][]{{0, 0}});
        track("squirrel", 8, new int[][]{{0, 0}, {0, 1}, {2, 0}, {2, 1}});
        track("songbird", 5, new int[][]{{0, 0}, {1, -1}, {1, 1}});
        track("stag", 6, new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}});
        track("ermine", 6, new int[][]{{0, 0}, {2, 0}});
        track("mossback", 13, new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 2}});
    }

    /** How much of each season today is: a smooth blend, peaking through each one's middle. */
    static Map<String, Double> seasonWeights(int dayOfYear, boolean southern) {
        if (southern) dayOfYear = (dayOfYear + 182) % 365;
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : PEAK_DAY.entrySet()) {
            double phase = Math.cos((dayOfYear - e.getValue()) / 365.0 * 2 * Math.PI);
            weights.put(e.getKey(), Math.pow(Math.max(0.0, phase), 2));
        }
        return weights;
    }

    /** Easter Sunday as {month, day}, by the anonymous Gregorian computus. */
    static int[] easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, g = (8 * b + 13) / 25;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, m = (32 + 2 * e + 2 * i - h - k) % 7, n = (a + 11 * h + 22 * m) / 451;
        return new int[]{(h + m - 7 * n + 114) / 31, (h + m - 7 * n + 114) % 31 + 1};
    }

    /** Names of what today is. Used by the easter eggs. */
    static Set<String> holidays(Calendar when) {
        Set<String> found = new HashSet<>();
        int month = Clock.month(when), day = Clock.day(when), md = month * 100 + day;
        if (1024 <= md && md <= 1031) found.add("halloween");
        if (md == 1030 || md == 1031) found.add("hallows_eve");
        if (1220 <= md && md <= 1226) found.add("yuletide");
        if (md == 1224) found.add("christmas_eve");
        if (md == 1231 || md == 101) found.add("new_year");
        if (md == 214) found.add("valentine");
        if (md == 317) found.add("st_patrick");
        if (md == 401) found.add("april_fools");
        if (811 <= md && md <= 813) found.add("perseids");
        if (md == 621) found.add("midsummer");
        if (md == 1221) found.add("midwinter");
        if (Clock.weekday(when) == 4 && day == 13) found.add("friday_13th");
        int year = Clock.year(when);
        int[] e = easter(year);
        if (Math.abs(Clock.ordinal(year, month, day) - Clock.ordinal(year, e[0], e[1])) <= 1) found.add("easter");
        return found;
    }

    final Eco eco;
    final PyRandom rng;
    public double snow, ice;
    public int snowing, storm, fog, aurora, rainbow, flash;
    private IntList bow;
    private int[] bowCells; // pixel, band, foot x 1000, grain x 1000
    private int bowAge;
    final List<double[]> flakes = new ArrayList<>();
    private int[] order, tone, caps;
    int shown, capsShown;
    final LinkedHashSet<Integer> prints = new LinkedHashSet<>();
    boolean meltingPrints;
    final ArrayDeque<int[]> carpet = new ArrayDeque<>();
    private double[] driftField;
    private int driftCols;
    public double temp = 10.0;
    final Map<String, Double> weights = new LinkedHashMap<>();
    public String season = "autumn";
    Set<String> todayTags = new HashSet<>();

    Climate(Eco eco) {
        this.eco = eco;
        this.rng = eco.rng;
        for (String s : SEASONS) weights.put(s, 0.25);
        refreshCalendar();
    }

    public double w(String seasonName) { return weights.get(seasonName); }

    // ---- the calendar ------------------------------------------------------

    public Calendar now() { return Clock.now(); }

    public void refreshCalendar() {
        Calendar when = now();
        int doy = Clock.dayOfYear(when);
        String pinned = eco.cfg.season;
        boolean southern = eco.cfg.hemisphere.equals("south");
        double base;
        if (PINNED_TEMPERATURE.containsKey(pinned)) {
            for (String s : SEASONS) weights.put(s, s.equals(pinned) ? 1.0 : 0.0);
            base = PINNED_TEMPERATURE.get(pinned);
        } else {
            weights.clear();
            Map<String, Double> blend = seasonWeights(doy, southern);
            for (String s : SEASONS) weights.put(s, blend.get(s));
            int d = southern ? (doy + 182) % 365 : doy;
            base = 11.0 - 16.0 * Math.cos((d - 20) / 365.0 * 2 * Math.PI);
        }
        String best = null;
        for (String s : new String[]{"winter", "spring", "summer", "autumn"}) if (best == null || weights.get(s) > weights.get(best)) best = s;
        season = best;
        // Each day runs a little warm or cold, consistently through that day.
        temp = base + new PyRandom(Clock.ordinal(when) * 31L + eco.s.seed).uniform(-3.5, 3.5);
        todayTags = holidays(when);
    }

    // ---- who is awake ------------------------------------------------------

    /** Deep enough into winter that small things sleep and plants wait. */
    public boolean dormant() { return temp < 2.0 || snow > 0.35; }
    public boolean frozen() { return ice > 0.6; }

    /** What the palette needs to know. */
    public Palette.Look look() {
        Palette.Look l = new Palette.Look();
        l.autumn = w("autumn");
        l.winter = w("winter");
        l.spring = w("spring");
        l.ice = Math.min(1.0, ice * 1.4);
        return l;
    }

    // ---- the slow tick -----------------------------------------------------

    /** Rain, or snow if it's cold enough; in the warm months sometimes a thunderstorm. */
    void startPrecipitation() {
        if (temp < 1.5) {
            snowing = rng.randint(700, 2400);
            eco.statInc("snowfalls");
            return;
        }
        eco.raining = rng.randint(400, 900);
        eco.statInc("rains");
        if (temp > 12 && rng.random() < 0.25) startStorm();
    }

    void startStorm() {
        eco.raining = Math.max(eco.raining, rng.randint(500, 1000));
        storm = eco.raining;
        eco.wild.note("thunderstorm");
    }

    void tick() {
        if (eco.tickNo % 1200 == 0) refreshCalendar();
        double t = temp;
        if (snowing != 0) {
            snowing--;
            snow = Math.min(1.0, snow + 0.00028);
        } else if (t > 1.5 && snow > 0) snow = Math.max(0.0, snow - 0.00002 * t * (1 + eco.daylight));
        if (t < -0.5) ice = Math.min(1.0, ice + 0.00025);
        else if (t > 2 && ice > 0) ice = Math.max(0.0, ice - 0.00012 * Math.min(t, 12) / 4);
        if (snow > 0 || shown != 0 || !prints.isEmpty()) settle();
        if (storm != 0) {
            storm--;
            if (rng.random() < 0.012) {
                flash = rng.randint(2, 3);
                maybeStrike();
            }
        } else if (eco.raining == 1 && eco.daylight > 0.2 && rng.random() < 0.5) {
            rainbow = rng.randint(500, 1100);
            eco.wild.odd.sighted("rainbow");
        }
        if (rainbow != 0) {
            rainbow--;
            if (rainbow == 0) {
                bow = null;
                bowAge = 0;
            }
        }
        if (fog != 0) fog--;
        else if (eco.cfg.weather && rng.random() < 0.00012 * (season.equals("autumn") || season.equals("spring") ? 2.5 : 0.6)) {
            fog = rng.randint(1500, 5000);
            eco.wild.note("fog");
        }
        if (aurora != 0) aurora--;
        else if (eco.cfg.weather && w("winter") > 0.3 && eco.daylight < 0.05 && snowing == 0
                && rng.random() < (todayTags.contains("midwinter") ? 0.0005 : 0.00006)) {
            aurora = rng.randint(1500, 4000);
            eco.wild.odd.sighted("aurora");
        }
        if (eco.tickNo % 600 == 300 && snow > 0.25) hiddenTracks();
        if (eco.tickNo % 60 == 0) tendCarpet();
    }

    /** Now and then the lightning finds a tree, and there's a log where it stood. */
    private void maybeStrike() {
        List<Eco.Plant> trees = new ArrayList<>();
        for (Eco.Plant p : eco.plants) if (p.kind.equals("sapling") && p.shown > 30) trees.add(p);
        if (!trees.isEmpty() && rng.random() < 0.02) {
            Eco.Plant tree = rng.choice(trees);
            tree.wilting = true;
            tree.shed = 0;
            tree.age = tree.life;
            eco.wild.evo.say("Lightning struck a young tree in the storm.");
            eco.wild.note("tree struck by lightning");
        }
    }

    // ---- snow on the ground ------------------------------------------------

    /** Every piece of ground in the order snow will cover it: patchy, drifting, and repeatable. */
    private void ensureOrder() {
        if (order != null) return;
        Scene scene = eco.s;
        Scene.Noise noise = new Scene.Noise(new PyRandom(scene.seed ^ 0xF1A4E));
        int w = scene.w;
        int[] ground = scene.ground;
        PyRandom hash = new PyRandom(scene.seed ^ 0x51F7);
        long[] keyed = new long[w * (scene.h - scene.gy)];
        int count = 0;
        for (int y = scene.gy; y < scene.h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                if (ground[i] != 0) {
                    double v = noise.at(x / 30.0, y / 12.0) * 0.6 + hash.random() * 0.4;
                    keyed[count++] = (long) (v * (1L << 40)) << 22 | i;
                }
            }
        }
        Arrays.sort(keyed, 0, count);
        order = new int[count];
        for (int k = 0; k < count; k++) order[k] = (int) (keyed[k] & ((1L << 22) - 1));
        byte[] raw = hash.randbytes(scene.w * scene.h);
        tone = new int[raw.length];
        for (int k = 0; k < raw.length; k++) tone[k] = raw[k] & 255;
        int[] fg = eco.c.fg;
        IntList list = new IntList(4096);
        for (int y = 1; y < scene.h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                // Only on what's solid enough to hold it: thick boughs, rocks, bushes. Not vines.
                if (fg[i] != 0 && fg[i - w] == 0 && i + 2 * w < fg.length && fg[i + w] != 0 && fg[i + 2 * w] != 0) {
                    list.add(i);
                    if (hash.random() < 0.4) list.add(i + w);
                }
            }
        }
        caps = list.toArray();
        hash.shuffle(caps);
    }

    /** Mostly one soft shade, freckled with the lighter one. */
    private int flakeTone(int i) { return tone[i] < 50 ? SNOW.at(2) : SNOW.at(1); }

    /** Bring the visible snow in line with how much has fallen. Budgeted, so it stays cheap. */
    private void settle() {
        ensureOrder();
        Canvas c = eco.c;
        int[] plane = c.snow, cap = c.cap;
        int target = (int) (snow * order.length);
        int budget = 160;
        while (shown < target && budget != 0) {
            int i = order[shown++];
            budget--;
            if (!prints.contains(i)) {
                plane[i] = flakeTone(i);
                c.refresh(i);
            }
        }
        while (shown > target && budget != 0) {
            shown--;
            budget--;
            int i = order[shown];
            if (prints.contains(i) && snow > 0.02) continue; // trodden snow is packed hard, and goes last
            plane[i] = 0;
            c.refresh(i);
        }
        // As it thaws, the old tracks stand out pale against bare ground.
        if (snow < 0.3 && !prints.isEmpty() && !meltingPrints) {
            meltingPrints = true;
            for (int i : prints) {
                plane[i] = SNOW.at(2);
                c.refresh(i);
            }
            if (prints.size() > 30) eco.wild.evo.say("The thaw has uncovered a day's worth of tracks.");
        } else if (snow >= 0.3) meltingPrints = false;
        if (snow <= 0.02 && !prints.isEmpty()) {
            Iterator<Integer> it = prints.iterator();
            for (int k = 0; k < 20 && it.hasNext(); k++) {
                int i = it.next();
                it.remove();
                plane[i] = 0;
                c.refresh(i);
            }
        }
        // Caps on boughs, rocks and bushes, once it's lying properly.
        int want = (int) (Math.max(0.0, snow - 0.25) / 0.75 * caps.length);
        for (int k = 0; k < 12; k++) {
            if (capsShown < want) {
                int i = caps[capsShown++];
                cap[i] = SNOW.at(2);
                c.refresh(i);
            } else if (capsShown > want) {
                int i = caps[--capsShown];
                cap[i] = 0;
                c.refresh(i);
            } else break;
        }
    }

    /** Called as something walks. Leaves prints only where there's snow to take them. */
    void footprint(String kind, int x, int y, int strideKey) {
        Integer stride = TRACK_STRIDE.get(kind);
        if (snow < 0.2 || stride == null || strideKey % stride != 0) return;
        press(kind, x, y);
    }

    void press(String kind, int x, int y) {
        Canvas c = eco.c;
        for (int[] d : TRACK_SHAPE.get(kind)) {
            int px = x + d[0], py = y + d[1];
            if (0 <= px && px < c.w && 0 <= py && py < c.h) {
                int i = py * c.w + px;
                if (c.snow[i] != 0) {
                    c.snow[i] = SNOW.at(0);
                    prints.add(i);
                    c.refresh(i);
                }
            }
        }
        while (prints.size() > 2500) {
            Iterator<Integer> it = prints.iterator();
            int i = it.next();
            it.remove();
            if (c.snow[i] == SNOW.at(0)) {
                c.snow[i] = flakeTone(i);
                c.refresh(i);
            }
        }
    }

    /** A whole line of prints, left by something that passed while nobody was looking. */
    void track(String kind) {
        if (snow < 0.2 || !TRACK_SHAPE.containsKey(kind)) return;
        int stride = TRACK_STRIDE.get(kind);
        int x = rng.randrange(eco.w);
        int y = eco.gy + (int) (eco.span * rng.uniform(0.1, 0.9));
        int heading = rng.choice(-1, 1);
        double drift = rng.uniform(-0.2, 0.2);
        int length = rng.randint(40, 170);
        for (int k = 0; k < length; k += stride) {
            int px = x + heading * k, py = (int) (y + drift * k + rng.uniform(-1, 1));
            if (0 <= px && px < eco.w && eco.gy + 1 < py && py < eco.h - 3) press(kind, px, py);
        }
    }

    /** Most of the forest passes unseen. In snow, you can tell who it was. */
    private void hiddenTracks() {
        for (String kind : new String[]{"rabbit", "fox", "squirrel", "songbird", "stag"}) {
            List<Animal> pool = eco.wild.evo.pools.get(kind);
            if (pool != null && !pool.isEmpty() && rng.random() < 0.25) track(kind);
        }
        if (rng.random() < 0.004) { // and sometimes you can't tell at all
            track("mossback");
            eco.wild.odd.sighted("big_tracks");
        }
    }

    // ---- a carpet of fallen leaves -----------------------------------------

    /** Where leaves collect: against trunks, rocks and the shore, and in scattered wind-made patches. */
    private void leafDrift() {
        if (driftField != null) return;
        Scene scene = eco.s;
        Scene.Noise noise = new Scene.Noise(new PyRandom(scene.seed ^ 0x1EAF));
        int w = scene.w, cols = w / 6 + 1, rows = eco.span / 4 + 1;
        double[] field = new double[rows * cols];
        int[][] reach = {{-9, 0}, {9, 0}, {-5, -3}, {5, -3}, {0, -5}, {-14, 0}, {14, 0}};
        for (int row = 0; row < rows; row++) {
            int y = scene.gy + row * 4;
            for (int col = 0; col < cols; col++) {
                int x = col * 6;
                double value = Math.max(0.0, noise.at(x / 38.0, y / 13.0) * 1.7 - 0.72) + 0.04;
                // Anything solid within a short reach makes a drift against it.
                for (int[] d : reach) {
                    int px = x + d[0], py = y + d[1];
                    if (0 <= px && px < w && scene.gy <= py && py < scene.h) {
                        int i = py * w + px;
                        if (scene.fg[i] != 0 || scene.water[i] != 0) value += 0.28;
                    }
                }
                field[row * cols + col] = value;
            }
        }
        driftField = field;
        driftCols = cols;
    }

    /** A leaf settles: blown a little toward where others have gathered, and lying where it lands. */
    void layLeaf(int x, int y) {
        Canvas c = eco.c;
        leafDrift();
        int px = 0, py = 0;
        boolean found = false;
        double bestValue = -1.0;
        for (int t = 0; t < 7; t++) {
            int tx = x + rng.randint(-34, 34), ty = y + rng.randint(-12, 12);
            if (!(0 <= tx && tx < c.w && eco.gy + 1 <= ty && ty < c.h)) continue;
            int k = ((ty - eco.gy) / 4) * driftCols + tx / 6;
            double value = (0 <= k && k < driftField.length ? driftField[k] : 0.0) * (0.75 + rng.random() * 0.25);
            if (value > bestValue) {
                px = tx;
                py = ty;
                found = true;
                bestValue = value;
            }
        }
        if (!found) return;
        Scene scene = eco.s;
        if (bestValue < 0.12 && rng.random() < 0.85) return; // open ground stays mostly clear: wind doesn't leave leaves there
        layBed(px, py, bestValue);
        int i = py * c.w + px;
        if (scene.water[i] != 0 && rng.random() > 0.2) return; // most that reach the pond sink or drift to the shore
        if (scene.ground[i] == 0 && scene.water[i] == 0) return;
        // Nearer leaves are bigger, as anything is.
        double depth = (py - eco.gy) / (double) eco.span;
        int[][] shape = LEAF_SHAPES[rng.randrange(depth < 0.25 ? 2 : depth < 0.6 ? 4 : LEAF_SHAPES.length)];
        int[] ramp = rng.choice(leafHues());
        int shade = rng.choice(0, 0, 0, 1, 1, 2);
        IntList cells = new IntList(shape.length);
        for (int k = 0; k < shape.length; k++) {
            int qx = px + shape[k][0], qy = py + shape[k][1];
            if (0 <= qx && qx < c.w && 0 <= qy && qy < c.h && (scene.ground[qy * c.w + qx] != 0 || scene.water[qy * c.w + qx] != 0)) {
                int q = qy * c.w + qx;
                // One edge of each leaf sits darker, so it reads as a leaf and not as a dot.
                c.leaf[q] = k == shape.length - 1 && shape.length > 2 ? ramp[Math.max(0, shade - 1)] : ramp[shade];
                c.refresh(q);
                cells.add(q);
            }
        }
        if (!cells.isEmpty()) carpet.add(cells.toArray());
        while (carpet.size() > 3000) liftLeaf();
    }

    /** Under the fresh leaves, the dark mat of those already fallen. */
    private void layBed(int x, int y, double thickness) {
        Canvas c = eco.c;
        int radius = 2 + (int) (Math.min(1.0, thickness) * 5);
        IntList cells = new IntList();
        for (int k = 0; k < radius * 3; k++) {
            int qx = x + rng.randint(-radius, radius);
            int qy = y + rng.randint(Clock.div(-radius, 2), radius / 2);
            if (0 <= qx && qx < c.w && 0 <= qy && qy < c.h) {
                int q = qy * c.w + qx;
                if (eco.s.ground[q] != 0 && c.leaf[q] == 0) {
                    c.leaf[q] = LITTER.at(rng.randint(0, 2));
                    c.refresh(q);
                    cells.add(q);
                }
            }
        }
        if (!cells.isEmpty()) carpet.add(cells.toArray());
    }

    /** Muted, as leaves on the ground are: russet, ochre and brown, with a few still bright. */
    int[][] leafHues() { return new int[][]{LEAF_RUSSET, LEAF_RUSSET, LEAF_RUSSET, LEAF_OCHRE, LEAF_OCHRE, LITTER_HUE, LITTER_HUE, LEAF_BRIGHT}; }

    private void liftLeaf() {
        Canvas c = eco.c;
        int[] cells = carpet.poll();
        for (int q : cells) {
            c.leaf[q] = 0;
            c.refresh(q);
        }
        eco.wild.enrich(cells[0] % c.w, cells[0] / c.w, 0.002);
    }

    /** Autumn doesn't start over when the screensaver does: the ground is already covered to suit the date. */
    void seedCarpet() {
        int amount = w("autumn") > 0.3 ? (int) (1600 * w("autumn")) : 0;
        for (int k = 0; k < amount; k++) {
            int x = rng.randrange(eco.w);
            layLeaf(x, rng.randrange(eco.gy, eco.h));
        }
    }

    /** Fallen leaves rot down over the winter, feeding the soil they lie on. */
    private void tendCarpet() {
        if (!carpet.isEmpty() && w("autumn") < 0.5)
            for (int k = 0, e = Math.min(16, carpet.size()); k < e; k++) liftLeaf();
    }

    // ---- the fast frame ----------------------------------------------------

    void frame(Overlay overlay) {
        Canvas c = eco.c;
        int w = c.w, h = c.h;
        int[] fg = c.fg;
        // Snowflakes.
        int wanted = snowing != 0 ? 70 : 0;
        while (flakes.size() < wanted) {
            double fx = rng.randrange(w), fy = rng.randrange(h), speed = rng.uniform(0.35, 0.9);
            flakes.add(new double[]{fx, fy, speed, rng.uniform(0, 6.28)});
        }
        for (double[] f : new ArrayList<>(flakes)) {
            f[1] += f[2];
            f[3] += 0.09;
            int x = (int) (f[0] + Math.sin(f[3]) * 2.2), y = (int) f[1];
            if (y >= h - 2) {
                if (wanted != 0) {
                    f[0] = rng.randrange(w);
                    f[1] = 0.0;
                } else flakes.remove(f);
                continue;
            }
            if (0 <= x && x < w) {
                int i = y * w + x;
                if (fg[i] == 0) overlay.put(i, f[2] > 0.6 ? STAG.at(1) : SNOW.at(2));
            }
        }
        // A frost-glint on lying snow.
        if (snow > 0.5 && eco.daylight < 0.25 && snowing == 0 && shown != 0 && rng.random() < 0.2) {
            int i = order[rng.randrange(shown)];
            if (fg[i] == 0 && c.life[i] == 0) overlay.put(i, STAG.at(3));
        }
        if (rainbow != 0) rainbowFrame(overlay);
    }

    /** Every pixel of the arch once, with its colour band. It only has to be worked out one time. */
    private void buildBow() {
        Scene scene = eco.s;
        int w = eco.w;
        int bandWidth = Math.max(3, (int) (3 * eco.scaleS));
        double cx = w / 2.0, cy = eco.gy + eco.span * 0.15, outer = cy - eco.h * 0.045, inner = outer - bandWidth * 7;
        byte[] grain = new PyRandom(scene.seed ^ 0xB0).randbytes(w * eco.gy);
        IntList cells = new IntList(8192);
        for (int y = 0; y < eco.gy; y++) {
            double dy = cy - y;
            if (dy > outer) continue;
            double reach = Math.sqrt(outer * outer - dy * dy);
            double hole = dy < inner ? Math.sqrt(inner * inner - dy * dy) : 0.0;
            for (int side = -1; side <= 1; side += 2) {
                for (int x = (int) hole, e = (int) reach; x <= e; x++) {
                    int px = (int) (cx + side * x);
                    if (0 <= px && px < w) {
                        int band = (int) ((outer - Math.hypot(px - cx, dy)) / bandWidth);
                        if ((outer - Math.hypot(px - cx, dy)) >= 0 && band < 7) {
                            int i = y * w + px;
                            cells.add(i);
                            cells.add(band);
                            cells.add((int) (1000 * Math.min(1.0, (eco.gy - y) / (eco.gy * 0.3) + 0.25)));
                            cells.add((int) (1000 * ((grain[i] & 255) / 255.0)));
                        }
                    }
                }
            }
        }
        bowCells = cells.toArray();
    }

    /** A whole arch, solid against open sky. Seen through rain-washed air, it lies over distant trees too, but thinner. */
    private void rainbowFrame(Overlay overlay) {
        if (bowCells == null) buildBow();
        if (eco.frameNo % 16 == 0 || bow == null) {
            Canvas c = eco.c;
            bowAge += 16;
            double level = Math.min(Math.min(1.0, rainbow / 150.0), bowAge / (Math.max(1, eco.fps) * 10.0));
            IntList next = new IntList(4096);
            for (int k = 0; k < bowCells.length; k += 4) {
                int i = bowCells[k], v = c.comp[i];
                if (c.fg[i] != 0 || c.life[i] != 0 || (v >= MOON.start && v < MOON.start + MOON.n)) continue;
                double strength = level * bowCells[k + 2] / 1000.0;
                if (c.cloud[i] != 0) strength *= 0.7;
                else if (!((v >= SKY.start && v < SKY.start + SKY.n) || (v >= STAR.start && v < STAR.start + STAR.n))) strength *= 0.5; // leaves and far trees show through it
                if (bowCells[k + 3] / 1000.0 < strength) {
                    next.add(i);
                    next.add(RAINBOW.at(bowCells[k + 1]));
                }
            }
            bow = next;
        }
        for (int k = 0; k < bow.size; k += 2) overlay.put(bow.a[k], bow.a[k + 1]);
    }

    // ---- persistence -------------------------------------------------------

    Map<String, Object> toState() {
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("snow", Json.round(snow, 4));
        st.put("ice", Json.round(ice, 4));
        st.put("snowing", (double) snowing);
        st.put("storm", (double) storm);
        st.put("fog", (double) fog);
        st.put("aurora", (double) aurora);
        IntList saved = new IntList(prints.size());
        for (int i : prints) saved.add(i);
        int[] all = saved.toArray();
        st.put("prints", Arrays.copyOfRange(all, Math.max(0, all.length - 1500), all.length));
        return st;
    }

    void load(Map<String, Object> st) {
        if (st == null) return;
        snow = Json.num(st.get("snow"));
        ice = Json.num(st.get("ice"));
        snowing = Json.integer(st.get("snowing"));
        storm = Json.integer(st.get("storm"));
        fog = Json.integer(st.get("fog"));
        aurora = Json.integer(st.get("aurora"));
        if (snow > 0) {
            ensureOrder();
            Canvas c = eco.c;
            shown = (int) (snow * order.length);
            for (int k = 0; k < shown; k++) {
                int i = order[k];
                c.snow[i] = flakeTone(i);
                c.refresh(i);
            }
            Object saved = st.get("prints");
            if (saved != null) {
                for (double d : Json.doubles(saved)) {
                    int i = (int) d;
                    if (c.snow[i] != 0) {
                        prints.add(i);
                        c.snow[i] = SNOW.at(0);
                        c.refresh(i);
                    }
                }
            }
            capsShown = 0;
        }
    }
}
