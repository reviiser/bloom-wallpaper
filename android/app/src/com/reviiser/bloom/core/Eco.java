package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Evolution.clamp;
import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The living part. Four loosely coupled systems share one soil: moss (a Life-like automaton whose rules evolve),
 * mycelium, plants as permaculture guilds, and fauna. Nutrients move around in a loop: nothing is created except a
 * little leaf fall. All per-tick work is sampled, so cost stays flat however crowded it gets.
 */
public final class Eco {
    static final Ramp[] FLY_RAMPS = {FIREFLY, MYC[2], MYC[4], MYC[0], MYC[3]};
    static final int CELL_W = 6, CELL_H = 4, MOSS_CELL = 2;
    static final int[][] DIRS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    static int ruleMask(String digits) {
        int mask = 0;
        for (char d : digits.toCharArray()) mask |= 1 << (d - '0');
        return mask;
    }

    static String ruleText(int b, int s) {
        StringBuilder t = new StringBuilder("B");
        for (int i = 0; i < 9; i++) if ((b >> i & 1) != 0) t.append(i);
        t.append("/S");
        for (int i = 0; i < 9; i++) if ((s >> i & 1) != 0) t.append(i);
        return t.toString();
    }

    static final String[][] STARTING_RULES = {{"34", "1234567"}, {"34", "234567"}, {"345", "2345678"}, {"35", "123456"}, {"346", "1234567"}};
    static final int[][] R_PENTOMINO = {{1, 0}, {2, 0}, {0, 1}, {1, 1}, {1, 2}};
    static final int[][] ACORN = {{1, 0}, {3, 1}, {0, 2}, {1, 2}, {4, 2}, {5, 2}, {6, 2}};

    // Who likes growing beside whom, permaculture-guild style.
    static final Map<String, Map<String, Double>> COMPANIONS = new HashMap<>();
    static final Map<String, Integer> LIMITS = new LinkedHashMap<>();
    // Season-bound plants: they come up when theirs arrives and die back when it leaves.
    static final Map<String, String> SEASONAL_PLANTS = new LinkedHashMap<>();
    // Age in ticks at which each kind begins to wither, before genetic variation.
    static final Map<String, Integer> LIFESPAN = new HashMap<>();
    static final Map<String, Double> HUNGER = new HashMap<>();
    private static void companions(String kind, Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Double) kv[i + 1]);
        COMPANIONS.put(kind, m);
    }
    static {
        companions("clover", "fern", 0.1, "spire", 0.15, "shrub", 0.1, "sapling", 0.15, "clover", -0.15);
        companions("fern", "sapling", 0.25, "log", 0.3, "clover", 0.15, "fern", -0.1);
        companions("spire", "clover", 0.25, "shrub", 0.1, "spire", 0.05, "sapling", -0.1);
        companions("shrub", "clover", 0.2, "sapling", 0.1, "fern", 0.05, "shrub", -0.2);
        companions("sapling", "clover", 0.25, "shrub", 0.1, "fern", 0.05, "sapling", -0.5, "log", 0.2);
        Object[] table = {
            "clover", 34, 3600, 0.12, "fern", 26, 9000, 0.35, "spire", 30, 3200, 0.4, "shrub", 10, 16000, 0.5, "sapling", 5, 60000, 0.6,
            "mushroom", 22, 900, 0.0, "log", 3, 30000, 0.0, "crocus", 14, 90000, 0.0, "foxglove", 9, 90000, 0.1, "goldenrod", 12, 90000, 0.05,
            "snowdrop", 12, 90000, 0.0, "pumpkin", 6, 90000, 0.1};
        for (int i = 0; i < table.length; i += 4) {
            LIMITS.put((String) table[i], (Integer) table[i + 1]);
            LIFESPAN.put((String) table[i], (Integer) table[i + 2]);
            HUNGER.put((String) table[i], (Double) table[i + 3]);
        }
        SEASONAL_PLANTS.put("crocus", "spring");
        SEASONAL_PLANTS.put("foxglove", "summer");
        SEASONAL_PLANTS.put("goldenrod", "autumn");
        SEASONAL_PLANTS.put("pumpkin", "autumn");
        SEASONAL_PLANTS.put("snowdrop", "winter");
    }

    public static final class Plant {
        public int pid, x, y, z, shown, colony, shed, gen;
        public long seed;
        public String kind;
        public G g, pollen;
        public List<int[]> pix;
        public double age, life;
        public boolean wilting, linked;
        IntList shadow;
    }

    public static final class Colony {
        public int cid, head, lv1, lv0, gen;
        public long born;
        public G g;
        public double energy;
        public boolean alive;
        final List<double[]> tips = new ArrayList<>();
        IntList nodes = new IntList();
    }

    public static final class Fly {
        double x, y, phase, freq, vx, vy, bright;
        int hue;
        void copy(Fly o) { x = o.x; y = o.y; phase = o.phase; freq = o.freq; vx = o.vx; vy = o.vy; bright = o.bright; hue = o.hue; }
    }

    public static final class Beetle extends Animal {
        int x, y, d, wait, inf, infT;
    }

    public static final class Moth extends Animal {
        double x, y;
        Plant target;
        int rest;
        String pollenKind;
        G pollenG;
    }

    static final class Stag {
        double x;
        int y, dir, step, graze, seedGen;
        String seedKind;
        G seedG;
        boolean drunk, drinking;
        Animal ind;
    }

    static final class Spore { double x, y, vx, alt; G g; int gen; }
    static final class Pulse { int p, left; Ramp ramp; }
    static final class Fallen { double x, y, phase; int land; int[] hues; }
    static final class Shaft { double x, w, slope, phase, rate; }
    static final class Lichen {
        int x, y, w, h, idle, tone;
        int[] cells;
        final HashMap<Integer, Integer> under = new HashMap<>();
    }

    public final Canvas c;
    public final Scene s;
    public Config cfg;
    public final PyRandom rng;
    public final int w, h, gy, span;
    public final double scaleS;
    public int tickNo, frameNo, fps = 8;
    int nextPid = 1, nextCid = 1;
    public final Map<String, Object> stats = new LinkedHashMap<>();

    // Soil, coarse grid.
    final int cols, rows;
    double[] nut, wet, shade, wetBase;

    // Moss, on its own 2x2 grid.
    final int mc, mr;
    final int[] cells, mossFlag;
    final List<int[]> strains = new ArrayList<>();
    IntList mossActive = new IntList();
    int mossCount;
    final int[] mossHist = new int[256];

    public final List<Plant> plants = new ArrayList<>();
    public final List<Colony> colonies = new ArrayList<>();
    final int[] mycCid, mycParent;
    int mycCount;
    final Map<Integer, Colony> colonyById = new HashMap<>();
    final List<Pulse> pulses = new ArrayList<>();
    final List<Spore> spores = new ArrayList<>();
    final List<Fallen> falling = new ArrayList<>();
    final List<Lichen> lichen = new ArrayList<>();

    public final List<Fly> flies = new ArrayList<>();
    public final List<Moth> moths = new ArrayList<>();
    public final List<Beetle> beetles = new ArrayList<>();
    Stag stag;
    int nextStag, owlBlink;
    boolean owlOn;
    final HashMap<Integer, Integer> twinkle = new HashMap<>();

    public int raining;
    int nextRain;
    final List<int[]> drops = new ArrayList<>();
    public double mist, daylight;
    final List<Shaft> shafts = new ArrayList<>();

    public final Climate climate;
    public final Sky sky;
    public final Clouds clouds;
    public final LensFlare flare;
    public final Wildlife wild;
    public final Climbers.HangingMoss hanging;

    public Eco(Canvas canvas, Scene scene, Config config, long seed) {
        c = canvas;
        s = scene;
        cfg = config;
        rng = new PyRandom(seed ^ 0x51ED);
        w = scene.w; h = scene.h; gy = scene.gy;
        scaleS = h / 270.0;
        span = h - gy;
        for (String k : new String[]{"births", "deaths", "fruitings", "max_plant_gen", "max_fungal_gen", "stag_visits", "rains", "pollinations", "rule_mutations", "colonies_founded"})
            stats.put(k, 0.0);
        stats.put("sightings", new LinkedHashMap<String, Object>());

        cols = w / CELL_W + 1;
        rows = span / CELL_H + 1;
        int n = cols * rows;
        nut = new double[n];
        for (int i = 0; i < n; i++) nut[i] = rng.uniform(0.25, 0.5);
        wet = new double[n];
        shade = new double[n];
        wetBase = new double[n];
        Arrays.fill(wet, 0.3);
        Arrays.fill(wetBase, 0.3);
        initMoisture();

        mc = w / MOSS_CELL;
        mr = span / MOSS_CELL;
        cells = new int[mc * mr];
        mossFlag = new int[mc * mr];
        strains.add(null);
        mycCid = new int[w * h];
        mycParent = new int[w * h];
        initLichen();

        nextStag = rng.randint(6000, 14000);
        owlOn = rng.random() < 0.6;
        nextRain = rng.randint(2500, 9000);
        for (int k = 0; k < 3; k++) {
            Shaft sh = new Shaft();
            sh.x = scene.shaftX + rng.uniform(-30, 30) * scaleS;
            sh.w = rng.uniform(12, 26) * scaleS;
            double slope = rng.uniform(0.32, 0.6);
            sh.slope = slope * rng.choice(-1, 1);
            sh.phase = rng.uniform(0, 6.28);
            sh.rate = rng.uniform(0.0004, 0.0009);
            shafts.add(sh);
        }
        for (int k = 1; k < 3; k++) {
            shafts.get(k).slope = shafts.get(0).slope;
            shafts.get(k).x += rng.uniform(-110, 110) * scaleS;
        }
        climate = new Climate(this);
        sky = new Sky(this);
        clouds = new Clouds(this);
        flare = new LensFlare(this);
        wild = new Wildlife(this);
        hanging = new Climbers.HangingMoss(this);
    }

    // ---- small helpers -----------------------------------------------------

    int countKind(String kind) {
        int n = 0;
        for (Plant p : plants) if (p.kind.equals(kind)) n++;
        return n;
    }

    void statInc(String k) { Object v = stats.get(k); stats.put(k, (v == null ? 0.0 : Json.num(v)) + 1); }
    void statMax(String k, double v) { stats.put(k, Math.max(Json.num(stats.get(k)), v)); }

    /** The colony holding a pixel's thread, or 0. */
    int node(int p) { return p >= 0 && p < mycCid.length ? mycCid[p] : 0; }

    // ---- setup -------------------------------------------------------------

    private void initMoisture() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = col * CELL_W, y = gy + row * CELL_H;
                double d = Math.hypot((x - s.pondCx) / (s.pondRx * 2.6), (y - s.pondCy) / (s.pondRy * 3.2));
                double v = clamp(0.75 - d * 0.42 + rng.uniform(-0.05, 0.05), 0.12, 0.85);
                wetBase[row * cols + col] = v;
                wet[row * cols + col] = v;
            }
        }
    }

    private void initLichen() {
        for (int[] l : s.lichen) {
            if (l[2] - l[0] < 5 || l[3] - l[1] < 4) continue;
            Lichen p = new Lichen();
            p.x = l[0]; p.y = l[1]; p.w = l[2] - l[0]; p.h = l[3] - l[1];
            p.cells = new int[p.w * p.h];
            p.tone = rng.choice(2, 2, 5);
            lichen.add(p);
        }
    }

    /** A new world: scatter pioneers, then let it run a little before anyone looks. */
    public void genesis() {
        for (String[] rule : STARTING_RULES) newStrain(ruleMask(rule[0]), ruleMask(rule[1]), rng.randrange(MOSS.length));
        for (int k = 0; k < 16; k++) {
            int cx = rng.randrange(mc), cy = rng.randrange(mr);
            mossSeed(cx, cy, rng.randrange(1, strains.size()), 7);
        }
        for (int k = 0; k < 4; k++) {
            int[] at = randomGround();
            foundColony(at[0], at[1], randomFungalGenes(), 0);
        }
        Object[][] start = {{"clover", 20}, {"fern", 11}, {"spire", 22}, {"shrub", 3}, {"sapling", 1}};
        for (Object[] st : start) {
            String kind = (String) st[0];
            for (int k = 0; k < (Integer) st[1]; k++) {
                int[] at = randomGround();
                Plant p = sprout(kind, at[0], at[1], randomGenes(kind), 0, true, false);
                if (p != null) {
                    p.age = rng.randint(0, LIFESPAN.get(kind) / 2);
                    reveal(p, (int) (p.pix.size() * rng.uniform(0.5, 1.0)));
                }
            }
        }
        for (int k = 0; k < cfg.fireflies; k++) flies.add(newFly());
        syncFauna();
        for (int k = 0; k < 700; k++) {
            mossTick(160);
            tick();
        }
        wild.visitors.clear(); // nobody has been seen arriving yet
        hanging.prewarm();
        climate.seedCarpet();
        for (int k = 0; k < 3; k++) wild.addFrog();
    }

    public void syncFauna() {
        int wanted = cfg.fireflies;
        while (flies.size() < wanted) flies.add(newFly());
        while (flies.size() > wanted) flies.remove(flies.size() - 1);
        if (cfg.fauna) {
            if (moths.isEmpty()) {
                for (int k = 0; k < 6; k++) {
                    // Half start out night-flying, half day-flying. Their descendants may settle it.
                    G genes = wild.evo.residentGenes("moth");
                    if (k % 2 != 0) {
                        genes.put("active", rng.uniform(0.7, 0.95));
                        genes.put("coat", rng.random());
                    }
                    moths.add(newMoth(rng.randrange(w), gy + rng.random() * span * 0.8, genes, 1.0));
                }
            }
            if (beetles.isEmpty()) {
                for (int k = 0; k < 6; k++) {
                    int[] at = randomGround();
                    beetles.add(newBeetle(at[0], at[1], null, 1.0));
                }
            }
        } else {
            moths.clear();
            beetles.clear();
            stag = null;
        }
    }

    public int[] randomGround() {
        int[] g = s.ground;
        for (int k = 0; k < 40; k++) {
            int x = rng.randrange(w);
            int y = rng.randrange(gy + 3, h - 2);
            if (g[y * w + x] != 0) return new int[]{x, y};
        }
        return new int[]{w / 2, gy + span / 2};
    }

    int cell(int x, int y) { return Clock.div(y - gy, CELL_H) * cols + x / CELL_W; }

    // ---- moss --------------------------------------------------------------

    int newStrain(int b, int sv, int tone) {
        for (int sid = 1; sid < strains.size(); sid++) {
            if (strains.get(sid) == null || (mossHist[sid] <= 0 && sid > 5)) {
                strains.set(sid, new int[]{b, sv, tone});
                mossHist[sid] = 0;
                return sid;
            }
        }
        if (strains.size() >= 255) return 0;
        strains.add(new int[]{b, sv, tone});
        mossHist[strains.size() - 1] = 0;
        return strains.size() - 1;
    }

    void mossSeed(int cx, int cy, int sid, int n) {
        int[][] shape = n < 7 ? R_PENTOMINO : ACORN;
        for (int[] d : shape) mossSet(cx + d[0], cy + d[1], sid);
        for (int k = 0; k < n; k++) {
            int mx = cx + rng.randint(-3, 7);
            mossSet(mx, cy + rng.randint(-2, 4), sid);
        }
    }

    void mossSet(int cx, int cy, int sid) {
        if (!(1 <= cx && cx < mc - 1 && 1 <= cy && cy < mr - 1)) return;
        int k = cy * mc + cx;
        int old = cells[k];
        if (old == sid) return;
        if (sid != 0 && s.ground[(gy + cy * MOSS_CELL) * w + cx * MOSS_CELL] == 0) return;
        cells[k] = sid;
        if (old != 0) {
            mossHist[old]--;
            mossCount--;
        }
        if (sid != 0) {
            mossHist[sid]++;
            mossCount++;
        }
        mossDraw(cx, cy);
        activate(k);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) activate(k + dy * mc + dx);
    }

    private void activate(int k) {
        if (mossFlag[k] == 0) {
            mossFlag[k] = 1;
            mossActive.add(k);
        }
    }

    private void mossDraw(int cx, int cy) {
        int sid = cells[cy * mc + cx];
        int i = (gy + cy * MOSS_CELL) * w + cx * MOSS_CELL;
        int[] moss = c.moss;
        if (sid != 0) {
            Ramp ramp = MOSS[strains.get(sid)[2]];
            int hh = (cx * 7 + cy * 13) & 7;
            int level = (hh > 3 ? 1 : 0) + (hh == 7 ? 1 : 0);
            if ((cy * MOSS_CELL) / (double) span > 0.3) level += 1;
            int a = ramp.at(level), b = ramp.at(level - 1), d = ramp.at(level + 1);
            moss[i] = a;
            moss[i + 1] = (hh & 1) != 0 ? b : a;
            moss[i + w] = (hh & 2) != 0 ? d : a;
            moss[i + w + 1] = (hh & 4) == 0 && (hh & 1) != 0 ? 0 : b;
        } else {
            moss[i] = moss[i + 1] = moss[i + w] = moss[i + w + 1] = 0;
        }
        c.refresh(i);
        c.refresh(i + 1);
        c.refresh(i + w);
        c.refresh(i + w + 1);
    }

    void mossTick(int budget) {
        IntList active = mossActive;
        if (active.isEmpty()) return;
        int cap = (int) (mc * mr * 0.17 * cfg.density);
        int[] offs = {-mc - 1, -mc, -mc + 1, -1, 1, mc - 1, mc, mc + 1};
        for (int t = 0, e = Math.min(budget, active.size); t < e; t++) {
            int k = rng.randrange(active.size);
            int cc = active.a[k];
            active.a[k] = active.last();
            active.pop();
            mossFlag[cc] = 0;
            int cy = cc / mc, cx = cc % mc;
            if (!(1 <= cx && cx < mc - 1 && 1 <= cy && cy < mr - 1)) continue;
            int n = 0, cand = 0;
            for (int off : offs) {
                int v = cells[cc + off];
                if (v != 0) {
                    n++;
                    if (cand == 0 || rng.random() < 0.35) cand = v;
                }
            }
            int cur = cells[cc];
            int soil = cell(cx * MOSS_CELL, gy + cy * MOSS_CELL);
            if (cur != 0) {
                int surv = strains.get(cur)[1];
                boolean dry = wet[soil] < 0.16 && rng.random() < 0.3;
                boolean crowded = mossCount > cap && rng.random() < 0.25;
                if ((surv >> n & 1) == 0 || dry || crowded) {
                    mossSet(cx, cy, 0);
                    nut[soil] = Math.min(1.0, nut[soil] + 0.004);
                }
            } else if (n != 0 && cand != 0) {
                if ((strains.get(cand)[0] >> n & 1) != 0) {
                    // Moss loves damp shade; it spreads only reluctantly anywhere else.
                    if (rng.random() < clamp(wet[soil] * 1.6 + shade[soil] * 0.6 - 0.1) && mossCount < cap) {
                        int sid = cand;
                        if (rng.random() < 0.004) {
                            int m = mutateStrain(cand);
                            if (m != 0) sid = m;
                        }
                        mossSet(cx, cy, sid);
                        nut[soil] = Math.max(0.0, nut[soil] - 0.003);
                    } else activate(cc); // conditions said no, for now: stay on the frontier and try again later
                }
            }
        }
        // Spot-check settled moss too, so drought and crowding reach the middle of a patch.
        for (int t = 0; t < 4; t++) {
            int cc = rng.randrange(cells.length);
            if (cells[cc] != 0) activate(cc);
        }
    }

    private int mutateStrain(int sid) {
        int[] st = strains.get(sid);
        int b = st[0], sv = st[1], tone = st[2];
        if (rng.random() < 0.35) b ^= 1 << rng.randint(4, 8); // birth digits stay at 3 and up: lower ones explode across the meadow
        else sv ^= 1 << rng.randint(1, 8);
        if (rng.random() < 0.3) tone = rng.randrange(MOSS.length);
        statInc("rule_mutations");
        return newStrain(b, sv, tone);
    }

    // ---- lichen: strict Conway, on bark and stone --------------------------

    private void lichenStep(Lichen patch) {
        int pw = patch.w, ph = patch.h;
        int[] cur = patch.cells, nxt = new int[pw * ph];
        int alive = 0;
        for (int y = 1; y < ph - 1; y++) {
            int row = y * pw;
            for (int x = 1; x < pw - 1; x++) {
                int i = row + x;
                int n = cur[i - pw - 1] + cur[i - pw] + cur[i - pw + 1] + cur[i - 1] + cur[i + 1] + cur[i + pw - 1] + cur[i + pw] + cur[i + pw + 1];
                if (n == 3 || (n == 2 && cur[i] != 0)) {
                    nxt[i] = 1;
                    alive++;
                }
            }
        }
        boolean same = Arrays.equals(nxt, cur);
        int[] fg = c.fg;
        Ramp ramp = MYC[patch.tone];
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                int i = y * pw + x;
                if (nxt[i] != cur[i]) {
                    int p = (patch.y + y) * w + patch.x + x;
                    if (p < 0 || p >= fg.length) continue;
                    if (nxt[i] != 0) {
                        if (fg[p] != 0) { // lichen only ever grows on something solid
                            patch.under.put(p, fg[p]);
                            fg[p] = ramp.at(1);
                            c.refresh(p);
                        }
                    } else if (patch.under.containsKey(p)) {
                        fg[p] = patch.under.remove(p);
                        c.refresh(p);
                    }
                }
            }
        }
        patch.cells = nxt;
        patch.idle = same || alive < 3 ? patch.idle + 1 : 0;
        if (patch.idle > 12) {
            patch.idle = 0;
            lichenSeed(patch);
        }
    }

    private void lichenSeed(Lichen patch) {
        int[][] shape = rng.choice(new int[][][]{R_PENTOMINO, ACORN});
        int ox = rng.randint(1, Math.max(1, patch.w - 8));
        int oy = rng.randint(1, Math.max(1, patch.h - 4));
        for (int[] d : shape) {
            int x = ox + d[0], y = oy + d[1];
            if (0 < x && x < patch.w - 1 && 0 < y && y < patch.h - 1) patch.cells[y * patch.w + x] = 1;
        }
    }

    // ---- mycelium ----------------------------------------------------------

    G randomFungalGenes() {
        G g = new G();
        g.put("hue", (double) rng.randrange(MYC.length));
        g.put("branch", rng.uniform(0.012, 0.05));
        g.put("wander", rng.uniform(0.08, 0.3));
        g.put("thrift", rng.uniform(0.3, 0.8));
        g.put("fruit", rng.uniform(2.0, 6.0));
        g.put("shape", rng.random());
        g.put("cluster", rng.random());
        g.put("size", rng.uniform(0.35, 1.0));
        g.put("slender", rng.random());
        g.put("cap", (double) rng.randrange(6));
        g.put("parasite", rng.uniform(0.0, 0.1));
        return g;
    }

    private G mutateFungal(G g, G other) {
        G n = new G(g);
        if (other != null) {
            for (String key : new ArrayList<>(n.keySet()))
                if (rng.random() < 0.5) n.put(key, other.d(key, n.d(key)));
        }
        n.put("parasite", clamp(n.d("parasite", 0.03) + rng.gauss(0, 0.035)));
        n.put("branch", clamp(n.d("branch") + rng.gauss(0, 0.005), 0.006, 0.09));
        n.put("wander", clamp(n.d("wander") + rng.gauss(0, 0.04), 0.04, 0.6));
        n.put("thrift", clamp(n.d("thrift") + rng.gauss(0, 0.05), 0.1, 1.0));
        n.put("fruit", clamp(n.d("fruit") + rng.gauss(0, 0.3), 1.2, 9.0));
        for (String key : new String[]{"shape", "cluster", "slender"}) n.put(key, clamp(n.d(key) + rng.gauss(0, 0.05)));
        n.put("size", clamp(n.d("size") + rng.gauss(0, 0.05), 0.25, 1.0));
        if (rng.random() < 0.06) n.put("hue", (double) rng.randrange(MYC.length));
        if (rng.random() < 0.06) n.put("cap", (double) rng.randrange(6));
        return n;
    }

    Colony foundColony(int x, int y, G g, int gen) {
        Colony col = new Colony();
        col.cid = nextCid++;
        col.g = g;
        col.energy = 1.2;
        for (int k = 0; k < 3; k++) col.tips.add(new double[]{x, y, rng.uniform(0, 2 * Math.PI)});
        col.gen = gen;
        col.alive = true;
        col.born = tickNo;
        colonies.add(col);
        colonyById.put(col.cid, col);
        statInc("colonies_founded");
        statMax("max_fungal_gen", gen);
        return col;
    }

    private void mycTick() {
        int limit = (int) (w * span * 0.05 * cfg.density);
        int total = mycCount;
        int[] ground = s.ground;
        int[] crowdOffs = {-w, w, -1, 1, -w - 1, -w + 1, w - 1, w + 1};
        for (Colony col : new ArrayList<>(colonies)) {
            G g = col.g;
            Ramp ramp = MYC[g.i("hue")];
            col.energy -= 0.0015 + (col.nodes.size - col.head) * 0.0000012;
            for (double[] tip : new ArrayList<>(col.tips)) {
                if (rng.random() > 0.45) continue;
                double x = tip[0], y = tip[1], a = tip[2];
                a += rng.gauss(0, g.d("wander"));
                if (rng.random() < 0.05) {
                    // Forage toward whichever side smells richer.
                    double best = a, here = -1.0;
                    for (double trial : new double[]{a - 0.6, a, a + 0.6}) {
                        int tx = (int) (x + Math.cos(trial) * 9), ty = (int) (y + Math.sin(trial) * 5);
                        if (0 <= tx && tx < w && gy <= ty && ty < h) {
                            double v = nut[cell(tx, ty)];
                            if (v > here) { best = trial; here = v; }
                        }
                    }
                    a = best;
                }
                // Half the vertical speed: threads lie flat in the ground plane.
                double fx = x + Math.cos(a), fy = y + Math.sin(a) * 0.5;
                int nx = (int) fx, ny = (int) fy, px = (int) x, py = (int) y;
                if (nx == px && ny == py) {
                    tip[0] = fx; tip[1] = fy; tip[2] = a;
                    continue;
                }
                if (!(0 <= nx && nx < w && gy + 2 <= ny && ny < h) || ground[ny * w + nx] == 0) {
                    tip[2] = a + Math.PI + rng.uniform(-0.6, 0.6);
                    if (rng.random() < 0.5 && col.tips.size() > 1) col.tips.remove(tip);
                    continue;
                }
                int p = ny * w + nx;
                int owner = mycCid[p];
                if (owner != 0) {
                    if (owner == col.cid) {
                        int sign = rng.choice(-1, 1);
                        tip[2] = a + sign * rng.uniform(0.7, 1.5);
                        if (rng.random() < 0.25 && col.tips.size() > 1) col.tips.remove(tip);
                        continue;
                    }
                    meet(col, tip, p, owner);
                    continue;
                }
                int crowd = 0;
                for (int off : crowdOffs) if (node(p + off) != 0) crowd++;
                if (crowd > 2) { // keep threads apart, so they read as lace and not as a smear
                    int sign = rng.choice(-1, 1);
                    tip[2] = a + sign * rng.uniform(0.8, 2.2);
                    if (rng.random() < 0.3 && col.tips.size() > 1) col.tips.remove(tip);
                    continue;
                }
                if (total >= limit || col.energy < 0.02) {
                    if (rng.random() < 0.08 && col.tips.size() > 1) col.tips.remove(tip);
                    continue;
                }
                mycCid[p] = col.cid;
                mycParent[p] = py * w + px;
                mycCount++;
                col.nodes.add(p);
                total++;
                c.myc[p] = ramp.at(1);
                c.refresh(p);
                tip[0] = fx; tip[1] = fy; tip[2] = a;
                int soil = cell(nx, ny);
                // A parasite's body is built for beetles, and is poorer at living on leaf litter.
                double take = Math.min(nut[soil], (0.006 + 0.012 * g.d("thrift")) * (1 - 0.5 * g.d("parasite", 0.0)));
                nut[soil] -= take;
                col.energy += take * 2.0;
                if (rng.random() < g.d("branch") && col.tips.size() < 9) {
                    int sign = rng.choice(-1, 1);
                    col.tips.add(new double[]{fx, fy, a + sign * rng.uniform(0.4, 1.1)});
                }
            }
            if (col.tips.isEmpty() && col.energy > 0.25 && col.head < col.nodes.size) {
                // Sprout a fresh tip from anywhere along the living network.
                int p = col.nodes.a[rng.randrange(col.head, col.nodes.size)];
                col.tips.add(new double[]{p % w, p / w, rng.uniform(0, 2 * Math.PI)});
                col.energy -= 0.08;
            }
            ageThreads(col, ramp);
            if (col.energy < 0.05 && col.head < col.nodes.size) retract(col, 2);
            else if (total > limit && rng.random() < 0.3) retract(col, 1);
            else if (col.energy > g.d("fruit") * 0.1 && col.nodes.size - col.head > 900) retract(col, 1); // old hyphae are shed as a mature colony moves on
            if (col.head >= col.nodes.size && col.tips.isEmpty() && col.head != 0) {
                col.alive = false;
                colonies.remove(col);
                colonyById.remove(col.cid);
            } else if (col.energy < -0.3) {
                retract(col, 6);
                col.tips.clear();
            } else if (col.energy > g.d("fruit")) tryFruit(col);
            if (col.head > 2000) {
                col.nodes.dropFront(col.head);
                col.lv1 = Math.max(0, col.lv1 - col.head);
                col.lv0 = Math.max(0, col.lv0 - col.head);
                col.head = 0;
            }
        }
    }

    /** Two colonies touch. Close kin fuse; strangers wall each other off. */
    private void meet(Colony col, double[] tip, int p, int owner) {
        Colony other = colonyById.get(owner);
        if (other == null) return;
        if (other.g.i("hue") == col.g.i("hue") && Math.abs(other.g.d("thrift") - col.g.d("thrift")) < 0.2) {
            double share = (col.energy + other.energy) / 2;
            col.energy = other.energy = share;
            tip[0] = p % w;
            tip[1] = p / w;
            if (other.tips.size() < 9) other.tips.add(tip.clone());
            if (rng.random() < 0.3) col.tips.remove(tip);
            return;
        }
        if (col.energy > other.energy * 2 && rng.random() < 0.06) {
            // A stronger colony overruns the wall, thread by thread.
            killThread(p);
            other.energy -= 0.01;
            return;
        }
        int sign = rng.choice(-1, 1);
        tip[2] += sign * rng.uniform(0.9, 2.4);
        if (rng.random() < 0.2 && col.tips.size() > 1) col.tips.remove(tip);
    }

    void killThread(int p) {
        if (node(p) != 0) {
            mycCid[p] = 0;
            mycCount--;
            c.myc[p] = 0;
            c.refresh(p);
        }
    }

    /** Fresh threads are bright; they settle into faint lines as they age. */
    private void ageThreads(Colony col, Ramp ramp) {
        int n = col.nodes.size;
        if (col.lv1 < col.head) col.lv1 = col.head;
        if (col.lv0 < col.head) col.lv0 = col.head;
        if (n - col.lv1 > 12) {
            for (int k = 0; k < 3; k++) {
                if (col.lv1 < n) {
                    int p = col.nodes.a[col.lv1++];
                    if (mycCid[p] != 0) {
                        c.myc[p] = ramp.at(0);
                        c.refresh(p);
                    }
                }
            }
        }
    }

    private void retract(Colony col, int n) {
        for (int k = 0; k < n; k++) {
            if (col.head >= col.nodes.size) return;
            int p = col.nodes.a[col.head++];
            if (mycCid[p] == col.cid) {
                killThread(p);
                int y = p / w;
                if (y >= gy) {
                    int soil = cell(p % w, y);
                    nut[soil] = Math.min(1.0, nut[soil] + 0.002);
                }
            }
        }
    }

    private void tryFruit(Colony col) {
        if (climate.dormant() || col.nodes.size - col.head < 30 || countKind("mushroom") >= LIMITS.get("mushroom")) return;
        int p = col.nodes.a[rng.randrange(col.head, col.nodes.size)];
        int x = p % w, y = p / w;
        if (wet[cell(x, y)] < 0.35 && raining == 0) return;
        G g = col.g;
        G genes = new G().with("hue", g.d("cap")).with("shape", g.d("shape")).with("cluster", g.d("cluster")).with("size", g.d("size"))
                .with("slender", g.d("slender")).with("tone", 0).with("bushy", 0).with("bloom", 0).with("wet", 0.5).with("dark", 0.5);
        Plant plant = sprout("mushroom", x, y, genes, col.gen, true, false);
        if (plant != null) {
            plant.colony = col.cid;
            col.energy -= g.d("fruit") * 0.45;
            statInc("fruitings");
        }
    }

    /** Nutrients in transit: faint sparks running along threads toward the colony's heart. */
    private void pulseTick() {
        if (pulses.size() < 7 && !colonies.isEmpty() && rng.random() < 0.18) {
            Colony col = rng.choice(colonies);
            if (col.nodes.size - col.head > 30) {
                Pulse pulse = new Pulse();
                pulse.p = col.nodes.a[rng.randrange(Math.max(col.head, col.nodes.size - 400), col.nodes.size)];
                pulse.ramp = MYC[col.g.i("hue")];
                pulse.left = rng.randint(25, 90);
                pulses.add(pulse);
            }
        }
    }

    // ---- plants ------------------------------------------------------------

    G randomGenes(String kind) {
        int hue;
        switch (kind) {
            case "spire": hue = rng.choice(0, 0, 0, 1, 7); break;
            case "clover": hue = rng.choice(6, 6, 3, 1); break;
            case "shrub": hue = rng.choice(4, 4, 4, 3); break;
            default: hue = rng.choice(0);
        }
        G g = new G();
        g.put("size", rng.random());
        g.put("bushy", rng.random());
        g.put("tone", rng.random());
        g.put("hue", (double) hue);
        g.put("bloom", rng.random());
        g.put("wet", rng.uniform(0.3, 0.75));
        g.put("dark", rng.uniform(0.2, 0.8));
        g.put("toxin", rng.uniform(0.0, 0.15));
        return g;
    }

    G mutatePlant(G g, G pollen) {
        G n = new G(g);
        if (pollen != null) {
            for (String key : new ArrayList<>(n.keySet()))
                if (rng.random() < 0.5) n.put(key, pollen.d(key, n.d(key)));
        }
        double startingToxin = rng.uniform(0.0, 0.1); // drawn whether or not it's needed, as the Python does
        if (!n.containsKey("toxin")) n.put("toxin", startingToxin);
        for (String key : new String[]{"size", "bushy", "tone", "bloom", "wet", "dark", "toxin"}) n.put(key, clamp(n.d(key) + rng.gauss(0, 0.05)));
        if (rng.random() < 0.05) n.put("hue", (double) rng.randrange(FLOWER.length));
        return n;
    }

    double depthScale(int y) { return (0.62 + 0.95 * (y - gy) / span) * scaleS; }

    Plant sprout(String kind, int x, int y, G g, int gen, boolean force, boolean ignoreLimit) {
        if (!ignoreLimit && countKind(kind) >= (int) (LIMITS.get(kind) * cfg.density + 0.5)) return null;
        if (!(0 <= x && x < w && gy + 3 <= y && y < h) || s.ground[y * w + x] == 0) return null;
        if (!force && suitability(kind, x, y, g) < rng.random()) return null;
        Plant p = new Plant();
        p.pid = nextPid++;
        p.kind = kind; p.x = x; p.y = y; p.g = g; p.gen = gen;
        p.z = Math.min(255, (y - gy) * 255 / span);
        p.seed = rng.randrange(1 << 30);
        p.pix = sprite(kind, g, p.seed, x, y);
        double life = rng.uniform(0.7, 1.3);
        p.life = LIFESPAN.get(kind) * life * (0.8 + g.d("size", 0.5) * 0.4);
        plants.add(p);
        statInc("births");
        statMax("max_plant_gen", gen);
        return p;
    }

    /** A plant's pixels, shaded for where it stands relative to the moon. */
    List<int[]> sprite(String kind, G g, long seed, int x, int y) {
        return Sprites.bevel(Sprites.build(kind, g, seed, depthScale(y)), s.moonX < x ? -1 : 1);
    }

    /** Once a plant has some size, it lays a soft shadow on the ground, away from the moon. */
    private void castShadow(Plant plant) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] d : plant.pix) {
            minX = Math.min(minX, d[0]); maxX = Math.max(maxX, d[0]);
            minY = Math.min(minY, d[1]); maxY = Math.max(maxY, d[1]);
        }
        int width = maxX - minX + 1, height = maxY - minY + 1;
        int rx = Math.max(2, width / 2), ry = Math.max(1, Math.min(4, height / 6 + 1));
        int away = s.moonX < plant.x ? 1 : -1;
        int cx = plant.x + Clock.div(minX + maxX, 2) + away * Math.min(rx, 2 + height / 5);
        int cy = plant.y + 1;
        IntList list = new IntList();
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dx = -rx; dx <= rx; dx++) {
                if (Math.pow(dx / (rx + 0.5), 2) + Math.pow(dy / (ry + 0.5), 2) >= 1) continue;
                if (Math.abs(dx) > rx * 0.7 && ((cx + dx + cy + dy) & 1) != 0) continue; // dithered away at the edge
                int x = cx + dx, y = cy + dy;
                if (0 <= x && x < w && gy <= y && y < h) list.add(y * w + x);
            }
        }
        for (int k = 0; k < list.size; k++) {
            int i = list.a[k];
            if (c.shade[i] < 250) c.shade[i]++;
            if (c.shade[i] == 1) {
                c.comp[i] = 0; // force a redraw
                c.refresh(i);
            }
        }
        plant.shadow = list;
    }

    private void liftShadow(Plant plant) {
        if (plant.shadow != null) {
            for (int k = 0; k < plant.shadow.size; k++) {
                int i = plant.shadow.a[k];
                if (c.shade[i] != 0) {
                    c.shade[i]--;
                    if (c.shade[i] == 0) {
                        c.comp[i] = 0;
                        c.refresh(i);
                    }
                }
            }
        }
        plant.shadow = null;
    }

    private double suitability(String kind, int x, int y, G g) {
        int soil = cell(x, y);
        double fit = 1.0 - Math.abs(wet[soil] - g.d("wet")) * 1.1 - Math.abs(shade[soil] - g.d("dark")) * 0.5;
        // Succession: hungry species wait until pioneers have built the soil.
        fit -= Math.max(0.0, HUNGER.get(kind) - nut[soil]) * 1.6;
        Map<String, Double> table = COMPANIONS.get(kind);
        for (Plant other : plants) {
            if (Math.abs(other.x - x) < 26 && Math.abs(other.y - y) < 14) {
                Double v = table == null ? null : table.get(other.kind);
                fit += (v == null ? 0.0 : v) * 0.5;
                if (Math.abs(other.x - x) < 5 && Math.abs(other.y - y) < 3) fit -= 0.2;
            }
        }
        return clamp(fit);
    }

    /** Show the plant's pixels up to index `upto`, respecting who stands in front. */
    void reveal(Plant plant, int upto) {
        int[] life = c.life, lifeZ = c.lifeZ;
        List<int[]> pix = plant.pix;
        int z = plant.z;
        upto = Math.min(upto, pix.size());
        for (int k = plant.shown; k < upto; k++) {
            int[] d = pix.get(k);
            int x = plant.x + d[0], y = plant.y + d[1];
            if (0 <= x && x < w && 0 <= y && y < h) {
                int i = y * w + x;
                if (z >= lifeZ[i] || life[i] == 0) {
                    life[i] = d[2];
                    lifeZ[i] = z;
                    c.refresh(i);
                }
            }
        }
        plant.shown = Math.max(plant.shown, upto);
        if (plant.shadow == null && plant.shown > pix.size() * 0.35 && plant.shown >= 6) castShadow(plant);
    }

    void unshow(Plant plant, int downTo) {
        int[] life = c.life, lifeZ = c.lifeZ;
        int z = plant.z;
        for (int k = plant.shown - 1; k >= downTo; k--) {
            int[] d = plant.pix.get(k);
            int x = plant.x + d[0], y = plant.y + d[1];
            if (0 <= x && x < w && 0 <= y && y < h) {
                int i = y * w + x;
                if (lifeZ[i] == z && life[i] != 0) {
                    life[i] = 0;
                    lifeZ[i] = 0;
                    c.refresh(i);
                }
            }
        }
        plant.shown = Math.min(plant.shown, downTo);
    }

    private static final Comparator<Plant> BY_Z = new Comparator<Plant>() {
        @Override public int compare(Plant a, Plant b) { return a.z - b.z; }
    };

    /** After a removal, redraw whatever was hiding behind it. */
    void restamp(Plant plant) {
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        boolean first = true;
        for (int[] d : plant.pix) {
            if (first) { minX = maxX = d[0]; minY = maxY = d[1]; first = false; }
            minX = Math.min(minX, d[0]); maxX = Math.max(maxX, d[0]);
            minY = Math.min(minY, d[1]); maxY = Math.max(maxY, d[1]);
        }
        int x0 = plant.x + minX, x1 = plant.x + maxX, y0 = plant.y + minY, y1 = plant.y + maxY;
        List<Plant> others = new ArrayList<>();
        for (Plant p : plants) if (p != plant && p.shown != 0 && p.x > x0 - 60 && p.x < x1 + 60) others.add(p);
        Collections.sort(others, BY_Z);
        int[] life = c.life, lifeZ = c.lifeZ;
        for (Plant p : others) {
            for (int k = 0; k < p.shown; k++) {
                int[] d = p.pix.get(k);
                int x = p.x + d[0], y = p.y + d[1];
                if (x0 <= x && x <= x1 && y0 <= y && y <= y1 && 0 <= x && x < w && 0 <= y && y < h) {
                    int i = y * w + x;
                    if (p.z >= lifeZ[i] || life[i] == 0) {
                        life[i] = d[2];
                        lifeZ[i] = p.z;
                        c.refresh(i);
                    }
                }
            }
        }
    }

    private void plantTick(int budget) {
        if (plants.isEmpty()) return;
        for (int k = 0, e = Math.min(budget, plants.size()); k < e; k++) {
            if (plants.isEmpty()) return;
            Plant p = plants.get(rng.randrange(plants.size()));
            plantUpdate(p, plants.size() / (double) Math.max(1, budget));
        }
    }

    private void plantUpdate(Plant p, double dt) {
        if (climate.dormant() && !p.wilting && !p.kind.equals("snowdrop")) return; // waiting for spring; nothing ages under the snow
        p.age += dt;
        int soil = cell(p.x, p.y);
        double soilWet = wet[soil], soilNut = nut[soil];
        String kind = p.kind;
        if (kind.equals("mushroom") || kind.equals("log")) { saprophyteUpdate(p, dt, soil); return; }
        if (p.wilting) { wilt(p, soil); return; }
        if (!p.linked && rng.random() < 0.05) checkMycorrhiza(p);
        if (p.shown < p.pix.size()) {
            // Growth is paid for in soil nutrients, more cheaply with a fungal partner.
            double rate = clamp(soilNut * 1.6) * clamp(soilWet * 1.7) * (p.linked ? 1.4 : 0.85);
            rate *= 1 - 0.3 * p.g.d("toxin", 0.0); // poison is expensive to make
            if (kind.equals("sapling") && !p.linked) rate *= 0.35;
            if (rng.random() < rate * 0.9) {
                int step = 1 + p.pix.size() / 70;
                reveal(p, p.shown + step);
                nut[soil] = Math.max(0.0, soilNut - 0.0007 * step);
            }
        } else mature(p, dt, soil);
        // The plant shapes the soil around it.
        if (kind.equals("clover")) nut[soil] = Math.min(1.0, soilNut + 0.0016 * dt); // nitrogen fixing
        int reach = kind.equals("sapling") ? 3 : kind.equals("shrub") ? 2 : kind.equals("fern") ? 1 : 0;
        if (reach != 0) {
            double target = Math.min(1.0, 0.25 + 0.2 * reach) * p.shown / Math.max(1, p.pix.size());
            int row = soil / cols, col = soil % cols;
            for (int r = Math.max(0, row - reach); r < Math.min(rows, row + reach + 1); r++) {
                for (int cc = Math.max(0, col - reach); cc < Math.min(cols, col + reach + 1); cc++) {
                    int k = r * cols + cc;
                    if (shade[k] < target) shade[k] += 0.02;
                }
            }
        }
        // Stress and old age.
        double fit = 1.0 - Math.abs(soilWet - p.g.d("wet")) - Math.abs(shade[soil] - p.g.d("dark")) * 0.5;
        if (p.age > p.life || (fit < 0.3 && rng.random() < 0.003 * dt)) {
            p.wilting = true;
            p.shed = 0;
        }
    }

    private void mature(Plant p, double dt, int soil) {
        String kind = p.kind;
        // Leaf fall: the slow, steady gift to the soil.
        if (!kind.equals("clover") && rng.random() < 0.02) {
            int lx = p.x + rng.randint(-5, 5);
            int ly = Math.min(h - 1, p.y + rng.randint(-1, 2));
            if (0 <= lx && lx < w) addLitter(lx, ly);
        }
        if (SEASONAL_PLANTS.containsKey(kind)) return;
        double seedChance; int spread;
        switch (kind) {
            case "clover": seedChance = 0.012; spread = 16; break;
            case "fern": seedChance = 0.003; spread = 34; break;
            case "spire": seedChance = 0.01; spread = 44; break;
            case "shrub": seedChance = 0.0016; spread = 40; break;
            default: seedChance = 0.00025; spread = 80;
        }
        if (rng.random() < seedChance * dt * clamp(nut[soil] * 2)) {
            int x = p.x + (int) rng.gauss(0, spread);
            int y = p.y + (int) rng.gauss(0, spread * 0.3);
            Plant child = sprout(kind, x, y, mutatePlant(p.g, p.pollen), p.gen + 1, false, false);
            if (child != null) {
                p.pollen = null;
                nut[soil] = Math.max(0.0, nut[soil] - 0.01);
            }
        }
    }

    /** If a hypha runs near the roots they trade: sugar for minerals. */
    private void checkMycorrhiza(Plant p) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int owner = node((p.y + dy) * w + p.x + dx);
                if (owner != 0) {
                    Colony col = colonyById.get(owner);
                    if (col != null) {
                        p.linked = true;
                        col.energy += 0.25;
                        return;
                    }
                }
            }
        }
    }

    void addLitter(int x, int y) {
        int i = y * w + x;
        if (s.ground[i] == 0) return;
        int soil = cell(x, y);
        nut[soil] = Math.min(1.0, nut[soil] + 0.012);
        if (c.life[i] == 0 && c.moss[i] == 0 && rng.random() < 0.25) {
            c.moss[i] = LITTER.at(rng.randint(0, 1));
            c.refresh(i);
        }
    }

    private void wilt(Plant p, int soil) {
        if (p.shown <= 0) { remove(p, soil); return; }
        int n = Math.max(1, p.pix.size() / 10);
        // Withered parts brown first, then crumble from the tips down.
        if (p.shed < p.shown && rng.random() < 0.8) {
            for (int k = p.shed, e = Math.min(p.shown, p.shed + n); k < e; k++) {
                int[] d = p.pix.get(k);
                int x = p.x + d[0], y = p.y + d[1];
                if (0 <= x && x < w && 0 <= y && y < h) {
                    int i = y * w + x;
                    if (c.lifeZ[i] == p.z && c.life[i] != 0) {
                        c.life[i] = LITTER.at(Clock.mod(x + y, 2));
                        c.refresh(i);
                    }
                }
            }
            p.shed += n;
        } else if (rng.random() < 0.7) {
            unshow(p, Math.max(0, p.shown - n));
            nut[soil] = Math.min(1.0, nut[soil] + 0.002 * n);
            if (rng.random() < 0.4) {
                int x = Math.max(0, Math.min(w - 1, p.x + rng.randint(-4, 4)));
                addLitter(x, p.y);
            }
        }
    }

    private void remove(Plant p, int soil) {
        plants.remove(p);
        liftShadow(p);
        statInc("deaths");
        nut[soil] = Math.min(1.0, nut[soil] + 0.06);
        restamp(p);
        if (p.kind.equals("sapling")) sprout("log", p.x, p.y, new G(p.g), p.gen, true, false);
    }

    private void saprophyteUpdate(Plant p, double dt, int soil) {
        if (p.wilting) { wilt(p, soil); return; }
        boolean mushroom = p.kind.equals("mushroom");
        if (p.shown < p.pix.size()) {
            if (rng.random() < (mushroom ? 0.6 : 0.9)) reveal(p, p.shown + 1 + p.pix.size() / (mushroom ? 24 : 40));
        } else if (mushroom) {
            if (rng.random() < 0.012 * dt && spores.size() < 8) {
                Colony g = colonyById.get(p.colony);
                Spore sp = new Spore();
                sp.x = p.x;
                sp.y = p.y - 4.0;
                sp.vx = rng.uniform(-0.25, 0.25);
                sp.alt = rng.uniform(6, 30);
                sp.g = g != null ? g.g : null;
                sp.gen = p.gen + 1;
                spores.add(sp);
            }
        } else if (p.kind.equals("log")) {
            // A nurse log slowly gives itself back, enriching and shading its neighbours.
            int row = soil / cols, col = soil % cols;
            for (int r = Math.max(0, row - 1); r < Math.min(rows, row + 2); r++) {
                for (int cc = Math.max(0, col - 2); cc < Math.min(cols, col + 3); cc++) {
                    int k = r * cols + cc;
                    nut[k] = Math.min(1.0, nut[k] + 0.0006 * dt);
                    wetBase[k] = Math.min(0.9, wetBase[k] + 0.00002 * dt);
                }
            }
        }
        if (p.age > p.life) {
            p.wilting = true;
            p.shed = 0;
        }
    }

    // ---- soil and weather --------------------------------------------------

    private void soilTick() {
        int n = nut.length;
        double rain = raining != 0 ? 0.012 : 0.0;
        for (int k = 0; k < 26; k++) {
            int a = rng.randrange(n);
            int b = a + rng.choice(-1, 1, -cols, cols);
            if (0 <= b && b < n) {
                double d = (nut[a] - nut[b]) * 0.15;
                nut[a] -= d;
                nut[b] += d;
                d = (wet[a] - wet[b]) * 0.2;
                wet[a] -= d;
                wet[b] += d;
            }
            // Water relaxes toward its resting level; shade forgets what no longer casts it.
            wet[a] += (wetBase[a] - wet[a]) * 0.02 + rain * 4;
            if (wet[a] > 1) wet[a] = 1.0;
            shade[a] *= 0.995;
            // A whisper of leaf fall from the canopy overhead.
            if (nut[a] < 0.25) nut[a] += 0.002;
        }
        if (raining != 0) {
            raining--;
            if (raining == 0) nextRain = rng.randint(4000, 14000);
        } else if (cfg.weather) {
            nextRain--;
            if (nextRain <= 0) startRain();
        }
    }

    void startRain() {
        nextRain = rng.randint(4000, 14000);
        climate.startPrecipitation();
    }

    // ---- the slow tick -----------------------------------------------------

    public void tick() {
        tickNo++;
        int n = tickNo;
        climate.tick();
        clouds.tick();
        hanging.tick();
        soilTick();
        // Under snow the moss and the threads all but stop, which costs almost nothing.
        if (!climate.dormant()) {
            mossTick(34);
            mycTick();
        } else if (n % 8 == 0) mycTick();
        plantTick(7);
        if (n % 300 == 0) seasonalPlants();
        pulseTick();
        if (n % 4 == 0) for (Lichen patch : lichen) lichenStep(patch);
        if (n % 1500 == 0) revive();
        critterTick();
        wild.tick();
    }

    /** Each kind comes up in its season, descended from last year's, and dies back after. */
    @SuppressWarnings("unchecked")
    private void seasonalPlants() {
        Map<String, Object> bank = wild.evo.sub("seed_bank");
        for (Plant p : plants) {
            String season = SEASONAL_PLANTS.get(p.kind);
            if (season != null && !p.wilting && climate.w(season) < 0.2) {
                p.wilting = true;
                p.shed = 0;
            }
        }
        for (Map.Entry<String, String> e : SEASONAL_PLANTS.entrySet()) {
            String kind = e.getKey();
            double weight = climate.w(e.getValue());
            if (weight < 0.35 || rng.random() > 0.35 * weight) continue;
            if (climate.dormant() && !kind.equals("snowdrop") && !(kind.equals("crocus") && climate.snow < 0.6)) continue;
            int[] at = randomGround();
            Object parent = bank.get(kind);
            G genes = parent != null ? mutatePlant(parent instanceof G ? (G) parent : new G((Map<String, ?>) parent), null) : randomGenes(kind);
            for (int k = 0, drift = rng.randint(1, 3); k < drift; k++) { // they come up in little drifts
                int x = at[0] + rng.randint(-12, 12);
                Plant p = sprout(kind, x, at[1] + rng.randint(-4, 4), genes, 0, true, false);
                if (p != null) bank.put(kind, genes);
            }
        }
    }

    /** The ground may have changed since this forest was saved. Nothing should be left growing in the pond. */
    private void settleOnLandscape() {
        int[] ground = s.ground;
        for (int k = 0; k < cells.length; k++) {
            if (cells[k] != 0) {
                int cy = k / mc, cx = k % mc;
                int y = gy + cy * MOSS_CELL, x = cx * MOSS_CELL;
                if (!(ground[y * w + x] != 0 && ground[y * w + x + 1] != 0 && ground[(y + 1) * w + x] != 0 && ground[(y + 1) * w + x + 1] != 0)) mossSet(cx, cy, 0);
            }
        }
        for (Plant p : new ArrayList<>(plants)) {
            if (ground[p.y * w + p.x] == 0) {
                unshow(p, 0);
                remove(p, cell(p.x, p.y));
            }
        }
        for (int i = 0; i < mycCid.length; i++) if (mycCid[i] != 0 && ground[i] == 0) killThread(i);
        c.flatten();
    }

    /** Spores and seeds blow in from the wider forest, so nothing is ever truly lost. */
    private void revive() {
        if (colonies.size() < 3) {
            int[] at = randomGround();
            foundColony(at[0], at[1], randomFungalGenes(), 0);
        }
        if (mossCount < 900) {
            IntList live = new IntList();
            for (int i = 1; i < strains.size(); i++) if (strains.get(i) != null) live.add(i);
            if (!live.isEmpty()) {
                for (int k = 0; k < 3; k++) {
                    int cx = rng.randrange(mc), cy = rng.randrange(mr);
                    mossSeed(cx, cy, rng.choice(live.toArray()), 7);
                }
            }
        }
        if (mossActive.isEmpty() && mossCount != 0) {
            for (int k = 0; k < 40; k++) activate(rng.randrange(cells.length));
        }
        for (String kind : new String[]{"clover", "spire"}) {
            if (countKind(kind) < 4) {
                int[] at = randomGround();
                sprout(kind, at[0], at[1], randomGenes(kind), 0, true, false);
            }
        }
        if (countKind("sapling") == 0 && rng.random() < 0.3) {
            int[] at = randomGround();
            sprout("sapling", at[0], at[1], randomGenes("sapling"), 0, false, false);
        }
        if (countKind("fern") == 0) {
            int[] at = randomGround();
            sprout("fern", at[0], at[1], randomGenes("fern"), 0, true, false);
        }
    }

    // ---- fauna -------------------------------------------------------------

    Fly newFly() {
        Fly f = new Fly();
        f.x = rng.randrange(w);
        double y = gy + Math.pow(rng.random(), 1.3) * span * 0.85;
        f.y = y - rng.uniform(0, span * 0.12);
        f.phase = rng.random();
        f.freq = rng.uniform(0.09, 0.14);
        f.bright = rng.uniform(0.35, 0.65);
        return f;
    }

    private Fly brightest(List<Fly> group, int[] indices) {
        Fly best = null;
        for (int i : indices) {
            Fly o = group.get(i);
            if (best == null || o.bright > best.bright) best = o;
        }
        return best;
    }

    /** Replace a firefly with the young of two others. Brighter flashers get more mates. */
    void flyOffspring(Fly f) {
        List<Fly> others = new ArrayList<>(flies);
        others.remove(f);
        if (others.size() < 2) {
            f.copy(newFly());
            return;
        }
        Fly a = brightest(others, rng.sampleIndices(others.size(), Math.min(2, others.size())));
        Fly b = rng.choice(others);
        Fly fresh = newFly();
        double fq = (a.freq + b.freq) / 2 + rng.gauss(0, 0.004);
        fresh.freq = clamp(fq, 0.05, 0.25);
        double br = (a.bright + b.bright) / 2 + rng.gauss(0, 0.04);
        fresh.bright = clamp(br, 0.05, 1.0);
        fresh.hue = rng.random() > 0.02 ? rng.choice(a.hue, b.hue) : rng.randrange(FLY_RAMPS.length);
        f.copy(fresh);
    }

    Beetle newBeetle(int x, int y, G g, double energy) {
        Beetle b = new Beetle();
        b.x = x; b.y = y;
        b.d = rng.randrange(8);
        b.g = g != null ? g : wild.evo.residentGenes("beetle");
        b.e = energy;
        return b;
    }

    Moth newMoth() {
        Moth m = new Moth();
        m.x = rng.randrange(w);
        m.y = gy + rng.random() * span * 0.8;
        m.rest = rng.randint(1, 200);
        m.e = 1.0;
        m.g = wild.evo.residentGenes("moth");
        return m;
    }

    Moth newMoth(double x, double y, G g, double energy) {
        Moth m = new Moth();
        m.x = x; m.y = y;
        m.rest = rng.randint(1, 200);
        m.e = energy;
        m.g = g;
        return m;
    }

    /** Of a few moths, whichever keeps the most similar hours. */
    Moth likeliestMate(Moth moth) {
        Moth best = null;
        double gap = 0;
        for (int i : rng.sampleIndices(moths.size(), Math.min(3, moths.size()))) {
            Moth o = moths.get(i);
            double d = Math.abs(o.g.d("active") - moth.g.d("active"));
            if (best == null || d < gap) { best = o; gap = d; }
        }
        return best;
    }

    private void critterTick() {
        if (!cfg.fauna) return;
        if (climate.dormant()) { // under bark and leaf litter until it warms
            critterWinter();
            return;
        }
        for (Beetle b : new ArrayList<>(beetles)) {
            G g = b.g;
            b.e -= 0.0007 * (0.7 + 0.6 * g.d("size"));
            if (b.inf != 0) {
                b.infT--;
                if (b.infT <= 0) {
                    beetleBurst(b);
                    continue;
                }
            }
            b.wait--;
            if (b.wait > 0) continue;
            b.wait = (int) (1 + (1 - g.d("speed")) * 7);
            if (rng.random() < (b.inf != 0 ? 0.7 : 0.25)) b.d = (b.d + rng.choice(-1, 1)) & 7; // the infected wander, as if not themselves
            int[] dir = DIRS[b.d];
            int nx = b.x + dir[0];
            int ny = b.y + (rng.random() < 0.5 ? dir[1] : 0);
            if (!(0 < nx && nx < w - 1 && gy + 2 < ny && ny < h - 1) || s.ground[ny * w + nx] == 0) {
                b.d = (b.d + 4) & 7;
                continue;
            }
            b.x = nx;
            b.y = ny;
            // Grazes moss and picks at litter.
            int cx = nx / MOSS_CELL, cy = (ny - gy) / MOSS_CELL;
            if (g.d("diet") < 0.55 && 1 <= cx && cx < mc - 1 && 1 <= cy && cy < mr - 1 && cells[cy * mc + cx] != 0 && rng.random() < 0.08) {
                mossSet(cx, cy, 0);
                b.e += 0.25;
                int soil = cell(nx, ny);
                nut[soil] = Math.min(1.0, nut[soil] + 0.003);
            }
            int owner = node(ny * w + nx);
            if (owner != 0) {
                Colony col = colonyById.get(owner);
                if (col != null) {
                    if (g.d("diet") >= 0.45 && rng.random() < 0.25) { // some lines have learned to graze on fungus
                        killThread(ny * w + nx);
                        b.e += 0.15;
                    }
                    if (b.inf == 0 && rng.random() < col.g.d("parasite", 0.0) * (1 - g.d("resist")) * 0.06) {
                        b.inf = col.cid;
                        b.infT = rng.randint(240, 700);
                        wild.note("beetle infected by fungus");
                    }
                }
            }
            if (b.e > 2.2 && beetles.size() < (int) (16 * cfg.density) && b.inf == 0) {
                b.e -= 1.0;
                beetles.add(newBeetle(nx, ny, wild.evo.childOf(g, rng.choice(beetles).g), 1.0));
            }
            if (b.e <= 0) {
                beetles.remove(b);
                int soil = cell(nx, ny);
                nut[soil] = Math.min(1.0, nut[soil] + 0.03);
            }
        }
        for (Moth m : new ArrayList<>(moths)) {
            m.e -= 0.00012;
            if (m.e <= 0) moths.remove(m);
        }
        if (!flies.isEmpty() && rng.random() < 0.002) // the brightest burn out soonest; the young of others replace them
            flyOffspring(brightest(flies, rng.sampleIndices(flies.size(), Math.min(3, flies.size()))));
        if (stag == null) {
            nextStag--;
            if (nextStag <= 0) summonStag(true);
        }
    }

    private void critterWinter() {
        if (stag == null) {
            nextStag--;
            if (nextStag <= 0) summonStag(true);
        }
    }

    /** The fungus has finished with its host, and fruits from where it fell. */
    private void beetleBurst(Beetle b) {
        beetles.remove(b);
        Colony col = colonyById.get(b.inf);
        int x = b.x, y = b.y;
        wild.enrich(x, y, 0.05);
        if (col == null) return;
        G g = col.g;
        G genes = new G().with("hue", g.d("cap")).with("shape", g.d("shape")).with("cluster", 0).with("size", g.d("size")).with("slender", g.d("slender"))
                .with("tone", 0).with("bushy", 0).with("bloom", 0).with("wet", 0.5).with("dark", 0.5);
        Plant plant = sprout("mushroom", x, y, genes, col.gen, true, true);
        if (plant != null) plant.colony = col.cid;
        col.energy += 1.5;
        for (int k = 0; k < 3; k++) {
            Spore sp = new Spore();
            sp.x = x;
            sp.y = y - 3.0;
            sp.vx = rng.uniform(-0.25, 0.25);
            sp.alt = rng.uniform(6, 26);
            sp.g = g;
            sp.gen = col.gen + 1;
            spores.add(sp);
        }
        wild.note("beetle consumed by fungus");
        if (wild.evo.once("first-burst")) wild.evo.say("A mushroom has grown from the body of a beetle.");
    }

    public void summonStag(boolean force) {
        if (!cfg.fauna || stag != null) return;
        List<Animal> inds = wild.evo.pick("stag", 1, force);
        if (inds.isEmpty()) {
            nextStag = rng.randint(2000, 8000); // none about, for now
            return;
        }
        int heading = rng.choice(-1, 1);
        Stag st = new Stag();
        st.y = gy + (int) (span * rng.uniform(0.25, 0.55));
        st.x = heading > 0 ? -26 : w + 26;
        st.dir = heading;
        st.ind = inds.get(0);
        stag = st;
        statInc("stag_visits");
        nextStag = rng.randint(7000, 20000);
    }

    private void stagFrame(Overlay overlay) {
        Stag st = stag;
        if (frameNo % 4 == 0) {
            if (st.graze > 0) st.graze--;
            else {
                st.x += st.dir;
                st.step++;
                // It walks round the water, never across it.
                st.y = wild.steer((int) st.x + 11, st.y, st.y, st.dir);
                climate.footprint("stag", (int) st.x + 8, st.y + 1, st.step);
                int muzzle = (int) st.x + (st.dir > 0 ? 21 : 0);
                if (0 <= muzzle && muzzle < w && st.y + 5 < h && s.water[(st.y + 5) * w + muzzle] != 0 && !st.drunk) {
                    st.drunk = true; // at the bank: stop and drink, above its own reflection
                    st.graze = rng.randint(30, 70);
                    st.drinking = true;
                } else if (rng.random() < 0.006) {
                    st.graze = rng.randint(12, 70);
                    st.drinking = false;
                    stagBrowse(st);
                }
            }
            if (st.x < -30 || st.x > w + 30) {
                stag = null;
                return;
            }
            if (st.drinking && st.graze != 0 && st.graze % 14 == 0) wild.ripple((int) st.x + (st.dir > 0 ? 21 : 0), st.y + 5);
        }
        G g = st.ind.g;
        String[] rows = Sprites.animalRows("stag", st.graze != 0 ? "lowered" : "walk" + (st.step / 4) % 2, g);
        int[] coat = Evolution.coatColors(g);
        IntList placed = Wildlife.stamp(this, overlay, rows, (int) st.x, st.y, st.dir < 0, coat, coat['+']);
        // The pale ones shine a little on whatever they pass.
        if ((g.d("coat") > 0.42 && g.d("coat") < 0.62) || g.d("glow") > 0.5) Wildlife.halo(this, overlay, placed, LIT);
    }

    /** The stag nips a plant, picks up its seeds, and drops others along its way. */
    private void stagBrowse(Stag st) {
        List<Plant> near = new ArrayList<>();
        for (Plant p : plants) {
            if (Math.abs(p.x - st.x - 11) < 22 && Math.abs(p.y - st.y) < 14 && p.shown > 8
                    && (p.kind.equals("fern") || p.kind.equals("spire") || p.kind.equals("shrub") || p.kind.equals("clover"))) near.add(p);
        }
        if (!near.isEmpty()) {
            Plant p = rng.choice(near);
            if (st.seedKind != null) {
                int x = (int) st.x + rng.randint(-6, 6);
                int y = st.y + rng.randint(-3, 3);
                sprout(st.seedKind, x, y, mutatePlant(st.seedG, null), st.seedGen + 1, true, false);
            }
            st.seedKind = p.kind;
            st.seedG = p.g;
            st.seedGen = p.gen;
            unshow(p, Math.max(4, p.shown / 2));
            restamp(p);
        }
        int soil = cell(Math.max(0, Math.min(w - 1, (int) st.x)), st.y);
        nut[soil] = Math.min(1.0, nut[soil] + 0.08);
    }

    private void flyFrame(Overlay overlay, boolean dim) {
        double dt = 1.0 / Math.max(1, fps);
        List<Fly> flashers = new ArrayList<>();
        for (Fly f : flies) {
            f.phase += f.freq * dt;
            if (f.phase >= 1.0) {
                f.phase -= 1.0;
                flashers.add(f);
            }
        }
        // Pulse coupling: seeing a neighbour flash nudges you toward flashing. Left alone,
        // scattered blinking drifts over hours into waves that roll through the clearing.
        for (Fly src : flashers) {
            for (Fly f : flies) {
                if (f != src && Math.abs(f.x - src.x) < 80 && Math.abs(f.y - src.y) < 45 && f.phase > 0.04) {
                    f.phase = Math.min(0.999, f.phase * 1.01 + 0.006);
                    f.freq += (src.freq - f.freq) * 0.005;
                }
            }
        }
        if (dim) return;
        int flying = (int) (flies.size() * Math.min(1.0, climate.w("summer") + 0.55 * (climate.w("spring") + climate.w("autumn")) + 0.05));
        if (climate.dormant()) flying = Math.min(flying, 2); // almost none, and you'd wonder about those
        int[] frame = c.frame;
        int total = w * h;
        for (int n = 0; n < flying; n++) {
            Fly f = flies.get(n);
            if (rng.random() < 0.05) {
                f.vx = rng.uniform(-0.28, 0.28);
                f.vy = rng.uniform(-0.14, 0.14);
            }
            f.x += f.vx;
            f.y += f.vy;
            if (f.x < 0) f.x += w;
            else if (f.x >= w) f.x -= w;
            if (f.y < gy - span * 0.22) f.vy = 0.1;
            else if (f.y > h - 8) f.vy = -0.1;
            int x = (int) f.x, y = (int) f.y;
            int i = y * w + x;
            if (i < 0 || i >= total) continue;
            double ph = f.phase;
            Ramp ramp = FLY_RAMPS[f.hue];
            double litFor = 0.06 + 0.16 * f.bright;
            if (ph < litFor) {
                int level = ph < litFor * 0.3 ? 4 : ph < litFor * 0.6 ? 3 : 2;
                boolean near = y > gy + span * 0.22;
                overlay.put(i, ramp.at(level));
                if (near || level >= 3) {
                    int v = ramp.at(level - 2);
                    overlay.put(i - 1, v);
                    overlay.put(i + 1, v);
                    overlay.put(i - w, v);
                    overlay.put(i + w, v);
                    if (near) {
                        for (int off : new int[]{-w - 1, -w + 1, w - 1, w + 1, -2, 2, -2 * w, 2 * w}) {
                            int k = i + off;
                            if (0 <= k && k < total && !overlay.has(k)) overlay.put(k, LIT2[frame[k]]);
                        }
                    }
                }
            } else if (ph < 0.34) overlay.put(i, ramp.at(1));
            else if (ph > 0.7) overlay.put(i, ramp.at(0));
        }
    }

    private void mothFrame(Overlay overlay) {
        Evolution evo = wild.evo;
        if (evo.asleep("moth")) return;
        // A pollination can add a moth to the list, and the newcomer flies this same frame.
        for (int n = 0; n < moths.size(); n++) {
            Moth m = moths.get(n);
            if (!evo.awake(m.g)) continue; // asleep under a leaf until its own hours come round
            if (m.rest > 0) {
                m.rest--;
                if (m.rest == 0) {
                    List<Plant> bloomers = new ArrayList<>();
                    for (Plant p : plants) {
                        String k = p.kind;
                        if ((k.equals("spire") || k.equals("clover") || k.equals("shrub") || k.equals("crocus") || k.equals("foxglove") || k.equals("goldenrod")) && p.shown == p.pix.size()) bloomers.add(p);
                    }
                    m.target = bloomers.isEmpty() ? null : rng.choice(bloomers);
                    if (m.target == null) m.rest = rng.randint(40, 200);
                }
            } else {
                Plant t = m.target;
                if (t == null || !plants.contains(t)) {
                    m.rest = 60;
                    m.target = null;
                } else {
                    int[] tip = t.pix.get(t.pix.size() - 1);
                    double dx = t.x + tip[0] - m.x, dy = t.y + tip[1] - m.y;
                    double d = Math.hypot(dx, dy);
                    if (d < 2) {
                        pollinate(m, t);
                        m.rest = rng.randint(60, 480);
                    } else {
                        double mx = dx / d * 0.7 + rng.uniform(-0.5, 0.5);
                        m.x += mx;
                        double my = dy / d * 0.5 + rng.uniform(-0.5, 0.5);
                        m.y += my;
                    }
                }
            }
            int x = (int) m.x, y = (int) m.y;
            if (1 <= x && x < w - 1 && 0 <= y && y < h) {
                int i = y * w + x;
                if (c.fg[i] != 0) continue;
                G g = m.g;
                int dark, pale;
                if (g.d("active") > 0.55) { // a day-flier: what we would call a butterfly, bright-winged
                    Ramp wing = FLOWER[(int) (g.d("coat") * 7.99)];
                    dark = wing.at(1);
                    pale = wing.at(2);
                    overlay.put(i, HARE.at(0));
                } else {
                    int[] wing = Evolution.COATS[(int) (g.d("coat") * (Evolution.COATS.length - 1) + 0.5)];
                    dark = wing[1];
                    pale = wing[2];
                    overlay.put(i, wing[2]);
                }
                if (m.rest == 0) {
                    if ((frameNo & 1) != 0) {
                        overlay.put(i - 1, dark);
                        overlay.put(i + 1, dark);
                    } else if (i > w) {
                        overlay.put(i - w - 1, pale);
                        overlay.put(i - w + 1, pale);
                    }
                }
            }
        }
    }

    private void pollinate(Moth moth, Plant plant) {
        if (moth.pollenKind != null && moth.pollenKind.equals(plant.kind)) {
            plant.pollen = moth.pollenG;
            statInc("pollinations");
        }
        moth.pollenKind = plant.kind;
        moth.pollenG = plant.g;
        moth.e = Math.min(2.5, moth.e + 0.3);
        if (moth.e > 1.6 && moths.size() < (int) (14 * cfg.density)) {
            moth.e -= 0.8;
            Moth mate = likeliestMate(moth);
            moths.add(newMoth(moth.x, moth.y, wild.evo.childOf(moth.g, mate.g), 0.8));
        }
    }

    private void beetleFrame(Overlay overlay) {
        int[] fg = c.fg, life = c.life;
        if (climate.dormant()) return;
        int total = w * h;
        for (Beetle b : beetles) {
            int i = b.y * w + b.x;
            if (fg[i] != 0 || life[i] != 0) continue;
            G g = b.g;
            int[] shell = Evolution.coatColors(g);
            int below = i + w;
            if (below + 1 < total && fg[below] == 0 && life[below] == 0) {
                overlay.put(below, DARK[c.frame[below]]);
                overlay.put(below + 1, DARK[c.frame[below + 1]]);
            }
            overlay.put(i, shell['#']);
            if (i + 1 < total) overlay.put(i + 1, shell['w']);
            if (g.d("size") > 0.55 && i + 2 < total) overlay.put(i + 2, shell['#']); // a big one
            if (g.d("ornament") > 0.7 && i >= w) overlay.put(i - w + 1, shell['w']); // horned
            if (b.inf != 0 && i >= w && frameNo % 8 < 5) overlay.put(i - w, SPORE.at(2)); // a pale stalk has started to grow from it
        }
    }

    private void sporeFrame(Overlay overlay) {
        for (Spore sp : new ArrayList<>(spores)) {
            sp.x += sp.vx + rng.uniform(-0.12, 0.12);
            sp.alt -= 0.06;
            sp.y += 0.02 + rng.uniform(-0.04, 0.04);
            int x = (int) sp.x, y = (int) (sp.y - sp.alt);
            if (sp.alt <= 0 || x < 0 || x >= w) {
                spores.remove(sp);
                landSpore(sp);
                continue;
            }
            if (0 <= y && y < h) {
                int i = y * w + x;
                if (c.fg[i] == 0) overlay.put(i, SPORE.at(1));
            }
        }
    }

    private void landSpore(Spore sp) {
        int x = (int) sp.x, y = (int) sp.y;
        if (!(0 <= x && x < w && gy + 3 <= y && y < h) || s.ground[y * w + x] == 0 || sp.g == null) return;
        if (nut[cell(x, y)] < 0.25) return;
        if (mycCid[y * w + x] != 0) return;
        // Sometimes crosses with whichever colony lives nearby, as compatible hyphae do.
        G mate = null;
        if (!colonies.isEmpty() && rng.random() < 0.5) mate = rng.choice(colonies).g;
        if (colonies.size() < (int) (9 * cfg.density + 1)) foundColony(x, y, mutateFungal(sp.g, mate), sp.gen);
    }

    private void pulseFrame(Overlay overlay) {
        int[] myc = c.myc, comp = c.comp;
        for (Pulse pulse : new ArrayList<>(pulses)) {
            int p = pulse.p, left = pulse.left;
            if (mycCid[p] == 0 || left <= 0) {
                pulses.remove(pulse);
                continue;
            }
            if (comp[p] == myc[p]) overlay.put(p, pulse.ramp.at(3));
            int parent = mycParent[p];
            if (node(parent) != 0) {
                if (comp[parent] == myc[parent]) overlay.setdefault(parent, pulse.ramp.at(2));
                if (frameNo % 2 == 0) {
                    pulse.p = parent;
                    pulse.left = left - 1;
                }
            } else pulses.remove(pulse);
        }
    }

    private void twinkleFrame(Overlay overlay) {
        List<int[]> stars = !sky.stars.isEmpty() && rng.random() < 0.4 ? sky.stars : s.stars;
        if (!stars.isEmpty() && rng.random() < 0.06) {
            int[] at = rng.choice(stars);
            int i = at[1] * w + at[0];
            if (c.fg[i] == 0 && c.comp[i] >= STAR.start && c.comp[i] < STAR.start + STAR.n) twinkle.put(i, rng.randint(4, 14));
        }
        for (Integer i : new ArrayList<>(twinkle.keySet())) {
            int left = twinkle.get(i) - 1;
            if (left <= 0) twinkle.remove(i);
            else {
                twinkle.put(i, left);
                overlay.put(i, STAR.at(Math.min(2, c.comp[i] - STAR.start + 1)));
            }
        }
    }

    private void owlFrame(Overlay overlay) {
        if (!owlOn || wild.owlAway || wild.evo.awakeCount("owl") == 0) return;
        int i = s.owlY * w + s.owlX;
        owlBlink--;
        if (owlBlink < 0) owlBlink = rng.randint(fps * 6, fps * 50);
        if (owlBlink < 2) return;
        overlay.put(i, EYE.at(1));
        overlay.put(i + 3, EYE.at(1));
    }

    private void rainFrame(Overlay overlay) {
        int wanted = raining != 0 ? 50 : 0;
        while (drops.size() < wanted) {
            int x = rng.randrange(w);
            int y = rng.randrange(h / 2);
            drops.add(new int[]{x, y, rng.randint(3, 5)});
        }
        for (int[] d : new ArrayList<>(drops)) {
            d[1] += d[2];
            if (d[1] >= h - 2) {
                if (wanted != 0) {
                    d[0] = rng.randrange(w);
                    d[1] = 0;
                } else drops.remove(d);
                continue;
            }
            for (int k = 0; k < d[2]; k++) {
                int y = d[1] - k;
                if (0 <= y && y < h) {
                    int i = y * w + d[0];
                    if (!overlay.has(i) && c.fg[i] == 0) overlay.put(i, LIT2[c.frame[i]]);
                }
            }
        }
    }

    private void fallingFrame(Overlay overlay) {
        double autumn = climate.w("autumn");
        if (climate.w("winter") > 0.6) autumn = 0.0;
        if (falling.size() < 3 + (int) (16 * autumn) && rng.random() < 0.008 + 0.14 * autumn && fps != 0) {
            Fallen leaf = new Fallen();
            leaf.x = rng.randrange(w);
            leaf.phase = rng.uniform(0, 6.28);
            leaf.land = rng.randint(gy, h - 10);
            leaf.hues = autumn > 0.3 ? rng.choice(climate.leafHues()) : new int[]{LITTER.at(3), LITTER.at(3), LITTER.at(3)};
            falling.add(leaf);
        }
        for (Fallen leaf : new ArrayList<>(falling)) {
            leaf.y += 0.3;
            leaf.phase += 0.1;
            int x = (int) (leaf.x + Math.sin(leaf.phase) * 3), y = (int) leaf.y;
            if (y >= leaf.land) {
                falling.remove(leaf);
                if (0 <= x && x < w && y < h) {
                    if (autumn > 0.3) climate.layLeaf(x, y);
                    else addLitter(x, y);
                }
                continue;
            }
            if (0 <= x && x < w - 1 && 0 <= y && y < h && c.fg[y * w + x] == 0) {
                // Turning as it falls: flat, then edge-on.
                overlay.put(y * w + x, leaf.hues[1]);
                if (Math.sin(leaf.phase * 2) > -0.2) overlay.put(y * w + x + 1, leaf.hues[0]);
            }
        }
    }

    // ---- the fast frame ----------------------------------------------------

    /** One animation frame. `dim` means no-one can see: keep the clock but skip drawing. */
    public void frame(boolean dim) {
        frameNo++;
        Overlay overlay = c.spare;
        overlay.clear();
        boolean night = daylight < 0.45;
        if (night && !flies.isEmpty()) flyFrame(overlay, dim);
        if (dim) return;
        // Lens light first, so that everything moving is drawn on top of it.
        flare.frame(overlay);
        if (cfg.fauna) {
            mothFrame(overlay);
            beetleFrame(overlay);
            wild.frame(overlay);
            if (stag != null) stagFrame(overlay);
            owlFrame(overlay);
        }
        sporeFrame(overlay);
        pulseFrame(overlay);
        if (daylight < 0.25) twinkleFrame(overlay);
        if (raining != 0 || !drops.isEmpty()) rainFrame(overlay);
        climate.frame(overlay);
        hanging.frame();
        fallingFrame(overlay);
        wild.odd.frame(overlay);
        c.spare = c.commitOverlay(overlay);
    }

    // ---- light -------------------------------------------------------------

    /** Rows of the frame that the slow shafts of light and mist touch: triples of (y, x0, x1). */
    public IntList lightSpans() {
        IntList spans = new IntList(1024);
        double now = frameNo / (double) Math.max(1, fps);
        if (cfg.lightShafts) {
            // By day the shafts come from the sun, and swing across with it.
            double[] sun = flare.sunPosition();
            double follow = sun != null ? sun[0] - s.moonX : 0;
            for (Shaft shaft : shafts) {
                double drift = Math.sin(now * shaft.rate * 2 * Math.PI + shaft.phase) * 26 * scaleS;
                double half = shaft.w * (0.75 + 0.25 * Math.sin(now * shaft.rate * 5 + shaft.phase));
                for (int y = (int) (h * 0.04), e = (int) (h * 0.92); y < e; y++) {
                    // Step in 2-pixel stairs so the edge reads as pixel art, not a smooth band.
                    double cx = shaft.x + drift + shaft.slope * (y & ~1) + follow;
                    int a = (int) (cx - half), b = (int) (cx + half);
                    if (a < w && b > 0) {
                        spans.add(y);
                        spans.add(Math.max(0, a));
                        spans.add(Math.min(w, b));
                    }
                }
            }
        }
        if (cfg.weather && mist > 0.02) {
            for (int y = gy - (int) (14 * scaleS), e = gy + (int) (26 * scaleS); y < e; y += 2) {
                for (int k = 0; k < 5; k++) {
                    double v = k * 113 + now * 0.5 + 40 * Math.sin(y * 0.3 + k);
                    double cx = v - w * Math.floor(v / w);
                    double half = 22 * mist * (1 + Math.sin(y * 0.7 + k));
                    int a = (int) (cx - half), b = (int) (cx + half);
                    if (b > 0 && a < w) {
                        spans.add(y);
                        spans.add(Math.max(0, a));
                        spans.add(Math.min(w, b));
                    }
                }
            }
        }
        // Overlapping spans would light a pixel twice in apply_light only from the base,
        // since each is translated from comp; so overlaps are harmless.
        return spans;
    }

    // ---- persistence -------------------------------------------------------

    public Map<String, Object> toState() {
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("tick", (double) tickNo);
        st.put("pid", (double) nextPid);
        st.put("cid", (double) nextCid);
        st.put("stats", stats);
        st.put("nut", Json.rounded(nut, 3));
        st.put("wet", Json.rounded(wet, 3));
        st.put("shade", Json.rounded(shade, 3));
        st.put("wet_base", Json.rounded(wetBase, 3));
        List<Object> savedStrains = new ArrayList<>();
        for (int[] strain : strains) savedStrains.add(strain);
        st.put("strains", savedStrains);
        st.put("cells", Json.packBytes(cells));
        List<Object> savedPlants = new ArrayList<>();
        for (Plant p : plants) {
            savedPlants.add(new ArrayList<Object>(Arrays.asList(p.kind, (double) p.x, (double) p.y, p.g, (double) p.seed, (double) p.shown, (double) Math.rint(p.age),
                    (double) Math.rint(p.life), p.wilting, (double) p.colony, p.pollen, p.linked, (double) p.shed, (double) p.gen, (double) p.pid)));
        }
        st.put("plants", savedPlants);
        List<Object> savedColonies = new ArrayList<>();
        for (Colony col : colonies) {
            List<Object> tips = new ArrayList<>();
            for (double[] tip : col.tips) tips.add(tip);
            int[] nodes = Arrays.copyOfRange(col.nodes.a, col.head, col.nodes.size);
            savedColonies.add(new ArrayList<Object>(Arrays.asList((double) col.cid, col.g, Json.round(col.energy, 3), tips, nodes, (double) col.gen, (double) col.born)));
        }
        st.put("colonies", savedColonies);
        List<Object> threads = new ArrayList<>();
        for (int p = 0; p < mycCid.length; p++) if (mycCid[p] != 0) threads.add(new int[]{p, mycCid[p], mycParent[p]});
        st.put("threads", threads);
        List<Object> savedFlies = new ArrayList<>();
        for (Fly f : flies) savedFlies.add(new double[]{Json.round(f.x, 1), Json.round(f.y, 1), Json.round(f.phase, 3), Json.round(f.freq, 4), f.hue, Json.round(f.bright, 3)});
        st.put("flies", savedFlies);
        List<Object> savedBeetles = new ArrayList<>();
        for (Beetle b : beetles) savedBeetles.add(new ArrayList<Object>(Arrays.asList((double) b.x, (double) b.y, Json.round(b.e, 3), b.g)));
        st.put("beetles", savedBeetles);
        st.put("next_rain", (double) nextRain);
        st.put("next_stag", (double) nextStag);
        st.put("raining", (double) raining);
        st.put("wild", wild.toState());
        st.put("climate", climate.toState());
        st.put("hanging", hanging.toState());
        return st;
    }

    @SuppressWarnings("unchecked")
    public void fromState(Map<String, Object> st) throws Exception {
        tickNo = Json.integer(st.get("tick"));
        nextPid = Json.integer(st.get("pid"));
        nextCid = Json.integer(st.get("cid"));
        stats.putAll((Map<String, Object>) st.get("stats"));
        nut = Json.doubles(st.get("nut"));
        wet = Json.doubles(st.get("wet"));
        shade = Json.doubles(st.get("shade"));
        wetBase = Json.doubles(st.get("wet_base"));
        strains.clear();
        for (Object o : (List<Object>) st.get("strains")) {
            if (o == null) strains.add(null);
            else {
                double[] v = Json.doubles(o);
                strains.add(new int[]{(int) v[0], (int) v[1], (int) v[2]});
            }
        }
        int[] saved = Json.unpackBytes((String) st.get("cells"), cells.length);
        Arrays.fill(mossHist, 0);
        mossCount = 0;
        mossActive = new IntList();
        Arrays.fill(mossFlag, 0);
        for (int k = 0; k < saved.length; k++) if (saved[k] != 0) mossSet(k % mc, k / mc, saved[k]);
        Map<Integer, Integer> shownByPid = new HashMap<>();
        for (Object o : (List<Object>) st.get("plants")) {
            List<Object> row = (List<Object>) o;
            Plant p = new Plant();
            p.kind = (String) row.get(0);
            p.x = Json.integer(row.get(1));
            p.y = Json.integer(row.get(2));
            p.g = new G((Map<String, ?>) row.get(3));
            p.seed = (long) Json.num(row.get(4));
            p.age = Json.num(row.get(6));
            p.life = Json.num(row.get(7));
            p.wilting = (Boolean) row.get(8);
            p.colony = Json.integer(row.get(9));
            p.pollen = row.get(10) == null ? null : new G((Map<String, ?>) row.get(10));
            p.linked = (Boolean) row.get(11);
            p.shed = Json.integer(row.get(12));
            p.gen = Json.integer(row.get(13));
            p.pid = Json.integer(row.get(14));
            shownByPid.put(p.pid, Json.integer(row.get(5)));
            p.z = Math.min(255, (p.y - gy) * 255 / span);
            p.pix = sprite(p.kind, p.g, p.seed, p.x, p.y);
            plants.add(p);
        }
        List<Plant> byZ = new ArrayList<>(plants);
        Collections.sort(byZ, BY_Z);
        for (Plant p : byZ) reveal(p, shownByPid.get(p.pid));
        for (Object o : (List<Object>) st.get("colonies")) {
            List<Object> row = (List<Object>) o;
            Colony col = new Colony();
            col.cid = Json.integer(row.get(0));
            col.g = new G((Map<String, ?>) row.get(1));
            col.energy = Json.num(row.get(2));
            for (Object tip : (List<Object>) row.get(3)) col.tips.add(Json.doubles(tip));
            for (double node : Json.doubles(row.get(4))) col.nodes.add((int) node);
            col.gen = Json.integer(row.get(5));
            col.born = (long) Json.num(row.get(6));
            col.alive = true;
            colonies.add(col);
            colonyById.put(col.cid, col);
        }
        for (Object o : (List<Object>) st.get("threads")) {
            double[] row = Json.doubles(o);
            int p = (int) row[0], cid = (int) row[1];
            Colony col = colonyById.get(cid);
            if (col != null) {
                if (mycCid[p] == 0) mycCount++;
                mycCid[p] = cid;
                mycParent[p] = (int) row[2];
                c.myc[p] = MYC[col.g.i("hue")].at(0);
                c.refresh(p);
            }
        }
        flies.clear();
        for (Object o : (List<Object>) st.get("flies")) {
            double[] row = Json.doubles(o);
            Fly f = new Fly();
            f.x = row[0]; f.y = row[1]; f.phase = row[2]; f.freq = row[3];
            f.hue = row.length > 4 ? (int) row[4] : 0;
            f.bright = row.length > 5 ? row[5] : 0.5;
            flies.add(f);
        }
        beetles.clear();
        for (Object o : (List<Object>) st.get("beetles")) {
            List<Object> row = (List<Object>) o;
            beetles.add(newBeetle(Json.integer(row.get(0)), Json.integer(row.get(1)), new G((Map<String, ?>) row.get(3)), Json.num(row.get(2))));
        }
        settleOnLandscape();
        nextRain = Json.integer(st.get("next_rain"));
        nextStag = Json.integer(st.get("next_stag"));
        raining = Json.integer(st.get("raining"));
        climate.load((Map<String, Object>) st.get("climate"));
        hanging.load(st.get("hanging"));
        climate.seedCarpet();
        wild.load((Map<String, Object>) st.get("wild"));
        syncFauna();
    }
}
