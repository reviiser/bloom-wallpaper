package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Heredity and populations: why the forest can surprise even its author.
 * Every animal is an individual with genes. Most of a population lives out of sight in the wider forest, and is
 * simulated every few minutes with the same rules that govern the ones you can see. Nothing here says what should
 * happen. There are no scripted outcomes, only costs and benefits. The journal only reports what it observes.
 */
public final class Evolution {
    public static final String[] GENE_NAMES = {"size", "coat", "speed", "wary", "active", "diet", "resist", "tolerance", "glow", "ornament"};
    static final int STEP_TICKS = 600; // one generation-scale step: five minutes at the default pace
    static final int OFFLINE_STEPS = 288; // catch up on at most a day of absence
    static final List<String> HIBERNATORS = Arrays.asList("hedgehog", "bats", "snail", "beetle", "moth");

    // dark, mid, light. Neighbours are similar, so small mutations make small changes.
    public static final int[][] COATS = {
        {TRUNK.at(1), TRUNK.at(3), TRUNK.at(5)}, {LITTER.at(1), LITTER.at(3), LITTER.at(4)}, {FOX.at(0), FOX.at(1), FOX.at(2)}, {FOX.at(1), FOX.at(2), FOX.at(3)},
        {CAP[5].at(0), CAP[5].at(1), CAP[5].at(2)}, {HARE.at(0), HARE.at(1), HARE.at(2)}, {HARE.at(1), HARE.at(2), HARE.at(3)},
        {STAG.at(0), STAG.at(1), STAG.at(2)}, {STAG.at(1), STAG.at(2), STAG.at(3)}, {STONE.at(2), STONE.at(3), STONE.at(5)},
        {VIOLET.at(2), VIOLET.at(4), FLECK.at(0)}, {TEAL.at(2), TEAL.at(4), TEAL.at(6)}, {COAT_GREEN.at(0), COAT_GREEN.at(1), COAT_GREEN.at(2)},
        {COAT_GREEN.at(0), COAT_GREEN.at(3), COAT_GREEN.at(4)}, {FROG.at(1), FROG.at(2), FROG.at(3)},
    };
    public static final String[] COAT_NAMES = {"charcoal", "dark brown", "dark russet", "russet", "tan", "grey", "silver", "pale", "white", "slate",
            "indigo", "teal", "moss-green", "olive", "green"};
    // "^" is the lit top edge of a body. The light tone serves, unless it's so far from the coat that it reads as a stripe.
    private static final int[][] COAT_MAPS = new int[COATS.length][128];
    private static final double[] COAT_LUM = new double[COATS.length];
    static {
        for (int i = 0; i < COATS.length; i++) {
            int dark = COATS[i][0], mid = COATS[i][1], light = COATS[i][2];
            int[] m = COAT_MAPS[i];
            m['#'] = mid; m['+'] = dark; m['w'] = light; m['o'] = light; m['O'] = dark;
            m['^'] = luminance(light) - luminance(mid) < 45 ? light : mid;
            COAT_LUM[i] = luminance(mid);
        }
    }

    public static double clamp(double v) { return clamp(v, 0.0, 1.0); }
    public static double clamp(double v, double low, double high) { return v < low ? low : v > high ? high : v; }
    public static int coatIndex(G g) { return (int) (clamp(g.d("coat")) * (COATS.length - 1) + 0.5); }
    public static int[] coatColors(G g) { return COAT_MAPS[coatIndex(g)]; }
    public static int coatDark(G g) { return COATS[coatIndex(g)][0]; }

    /** Where the present moment sits on the night-to-day scale of the `active` gene. */
    public static double awakePosition(double daylight) { return Math.min(1.0, daylight / 0.7); }
    /** How much two creatures' waking hours coincide. */
    static double overlap(double a, double b) { return 1.0 - Math.abs(a - b) * 0.85; }

    private static final String[] DIST_KEYS = {"coat", "active", "size", "diet", "ornament"};
    static double distance(G a, G b) {
        double s = 0;
        for (String k : DIST_KEYS) s += Math.abs(a.d(k) - b.d(k));
        return s / 5.0;
    }

    static G founderGenes(PyRandom rng, G base, double spread) {
        G g = new G().with("size", 0.4).with("coat", 0.4).with("speed", 0.5).with("wary", 0.5).with("active", 0.5).with("diet", 0.5)
                .with("resist", 0.1).with("tolerance", 0.1).with("glow", 0.03).with("ornament", 0.1);
        g.putAll(base);
        G out = new G();
        for (Map.Entry<String, Double> e : g.entrySet()) out.put(e.getKey(), clamp(e.getValue() + rng.gauss(0, spread)));
        return out;
    }

    static G childGenes(PyRandom rng, G a, G b) {
        G g = new G();
        for (String key : GENE_NAMES) {
            double v = (rng.random() < 0.5 ? a : b).d(key, 0.3);
            g.put(key, clamp(v + rng.gauss(0, 0.028)));
        }
        if (rng.random() < 0.015) { // rarely, something large happens
            String key = rng.choiceOf("coat", "glow", "ornament", "size", "active", "diet");
            g.put(key, rng.random());
        }
        return g;
    }

    public static final class Species {
        public final int start, cap, life, mature, clutch;
        public final double metab, breed;
        public final G base;
        Species(int start, int cap, int life, int mature, int clutch, double metab, double breed, G base) {
            this.start = start; this.cap = cap; this.life = life; this.mature = mature; this.clutch = clutch;
            this.metab = metab; this.breed = breed; this.base = base;
        }
    }
    private static G gs(Object... kv) {
        G g = new G();
        for (int i = 0; i < kv.length; i += 2) g.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return g;
    }
    public static final Map<String, Species> SPECIES = new LinkedHashMap<>();
    public static final Map<String, G> RESIDENT_BASE = new LinkedHashMap<>();
    // Who hunts whom. Each hunter picks by its own diet gene: low favours the left of the list.
    static final Map<String, String[]> PREY = new LinkedHashMap<>();
    static final Map<String, Double> PREY_VALUE = new HashMap<>();
    static final Map<String, String[]> TRAIT_WORDS = new LinkedHashMap<>();
    public static final Map<String, String> PLURAL = new HashMap<>();
    static {
        SPECIES.put("rabbit", new Species(12, 34, 70, 6, 3, 0.10, 0.45, gs("size", 0.35, "coat", 0.36, "speed", 0.6, "wary", 0.5, "active", 0.15, "diet", 0.4)));
        SPECIES.put("fox", new Species(3, 7, 320, 40, 2, 0.045, 0.07, gs("size", 0.5, "coat", 0.21, "speed", 0.6, "wary", 0.3, "active", 0.2, "diet", 0.2)));
        SPECIES.put("owl", new Species(2, 5, 500, 60, 2, 0.03, 0.05, gs("size", 0.5, "coat", 0.07, "speed", 0.6, "active", 0.05, "diet", 0.5)));
        SPECIES.put("bats", new Species(8, 18, 200, 20, 1, 0.05, 0.12, gs("size", 0.3, "coat", 0.0, "speed", 0.7, "active", 0.12)));
        SPECIES.put("hedgehog", new Species(4, 10, 150, 20, 2, 0.035, 0.12, gs("size", 0.4, "coat", 0.36, "speed", 0.25, "wary", 0.3, "active", 0.1, "diet", 0.3)));
        SPECIES.put("songbird", new Species(8, 22, 110, 12, 3, 0.08, 0.25, gs("size", 0.3, "coat", 0.64, "speed", 0.7, "active", 0.88, "diet", 0.3)));
        SPECIES.put("squirrel", new Species(5, 12, 160, 18, 2, 0.06, 0.15, gs("size", 0.35, "coat", 0.21, "speed", 0.75, "wary", 0.6, "active", 0.85)));
        SPECIES.put("stag", new Species(3, 5, 800, 80, 1, 0.02, 0.04, gs("size", 0.5, "coat", 0.55, "speed", 0.5, "wary", 0.6, "active", 0.2, "ornament", 0.5, "glow", 0.1)));
        RESIDENT_BASE.put("beetle", gs("size", 0.3, "coat", 0.68, "speed", 0.5, "active", 0.5, "resist", 0.15));
        RESIDENT_BASE.put("moth", gs("size", 0.35, "coat", 0.5, "speed", 0.6, "active", 0.25));
        RESIDENT_BASE.put("frog", gs("size", 0.35, "coat", 0.97, "speed", 0.6, "active", 0.3));
        RESIDENT_BASE.put("snail", gs("size", 0.3, "coat", 0.1, "speed", 0.1, "active", 0.4));
        PREY.put("fox", new String[]{"rabbit", "squirrel", "hedgehog", "frog"});
        PREY.put("owl", new String[]{"beetle", "snail", "frog", "bats", "squirrel", "songbird"});
        PREY.put("bats", new String[]{"moth"});
        PREY.put("hedgehog", new String[]{"beetle", "snail"});
        PREY.put("songbird", new String[]{"beetle", "moth"});
        Object[] values = {"rabbit", 1.4, "squirrel", 1.0, "hedgehog", 1.2, "frog", 0.8, "beetle", 0.35, "snail", 0.4, "moth", 0.3, "bats", 0.5, "songbird", 0.6};
        for (int i = 0; i < values.length; i += 2) PREY_VALUE.put((String) values[i], (Double) values[i + 1]);
        TRAIT_WORDS.put("size", new String[]{"smaller", "larger"});
        TRAIT_WORDS.put("speed", new String[]{"slower", "faster"});
        TRAIT_WORDS.put("wary", new String[]{"bolder", "warier"});
        TRAIT_WORDS.put("glow", new String[]{"duller", "more luminous"});
        TRAIT_WORDS.put("ornament", new String[]{"plainer", "more ornamented"});
        TRAIT_WORDS.put("resist", new String[]{"more disease-prone", "more disease-resistant"});
        TRAIT_WORDS.put("tolerance", new String[]{"fussier about food", "more tolerant of toxic plants"});
        String[] plural = {"rabbit", "rabbits", "fox", "foxes", "owl", "owls", "bats", "bats", "hedgehog", "hedgehogs", "songbird", "songbirds", "squirrel", "squirrels", "stag", "stags"};
        for (int i = 0; i < plural.length; i += 2) PLURAL.put(plural[i], plural[i + 1]);
    }

    public static G meanGenes(List<? extends Animal> group) {
        G out = new G();
        if (group.isEmpty()) return out;
        for (String k : GENE_NAMES) {
            double s = 0;
            for (Animal a : group) s += a.g.d(k, 0.0);
            out.put(k, s / group.size());
        }
        return out;
    }

    public static final class Plague { double v, left, dead; }
    static final class Env { double ground, fern, fruit, trees, logs, flowers; final double[] forage = new double[5]; }

    public final Eco eco;
    final PyRandom rng;
    public final Map<String, List<Animal>> pools = new LinkedHashMap<>();
    final Map<String, Plague> plagues = new LinkedHashMap<>();
    public List<Object> journal = new ArrayList<>();
    public Map<String, Object> memory = new LinkedHashMap<>();
    int steps, tickLeft = STEP_TICKS;
    Env memoryEnv;

    public Evolution(Eco eco) {
        this.eco = eco;
        this.rng = eco.rng;
        for (Map.Entry<String, Species> e : SPECIES.entrySet()) {
            List<Animal> pool = new ArrayList<>();
            for (int k = 0; k < e.getValue().start; k++) {
                int age = rng.randint(0, e.getValue().life / 2);
                pool.add(newIndividual(e.getKey(), null, age, 0.05));
            }
            pools.put(e.getKey(), pool);
        }
        Map<String, Object> ref = sub("ref");
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) ref.put(e.getKey(), meanGenes(e.getValue()));
    }

    // ---- small helpers for the memory, which is saved as it stands ----------

    @SuppressWarnings("unchecked")
    public Map<String, Object> sub(String key) {
        Object m = memory.get(key);
        if (m == null) { m = new LinkedHashMap<String, Object>(); memory.put(key, m); }
        return (Map<String, Object>) m;
    }
    private double num(Map<String, Object> m, String k, double fallback) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }
    @SuppressWarnings("unchecked")
    private G ref(Map<String, Object> refs, String name, G means) {
        Object r = refs.get(name);
        if (r instanceof G) return (G) r;
        G g = r == null ? means : new G((Map<String, ?>) r);
        refs.put(name, g);
        return g;
    }

    // ---- individuals -------------------------------------------------------

    public Animal newIndividual(String name, G g, int age, double spread) {
        Animal a = new Animal();
        a.g = g != null ? g : founderGenes(rng, SPECIES.get(name).base, spread);
        a.age = age;
        a.e = 1.0;
        return a;
    }

    public G residentGenes(String kind) { return founderGenes(rng, RESIDENT_BASE.get(kind), 0.05); }
    public G childOf(G a, G b) { return childGenes(rng, a, b); }

    /** The list holding a species, visible or not. */
    @SuppressWarnings("unchecked")
    public List<Animal> group(String name) {
        List<Animal> pool = pools.get(name);
        if (pool != null) return pool;
        switch (name) {
            case "beetle": return (List<Animal>) (List<?>) eco.beetles;
            case "moth": return (List<Animal>) (List<?>) eco.moths;
            case "frog": return (List<Animal>) (List<?>) eco.wild.frogs;
            default: return (List<Animal>) (List<?>) eco.wild.snails;
        }
    }

    public boolean awake(G g) { return Math.abs(g.d("active") - awakePosition(eco.daylight)) < 0.5; }
    public boolean awake(G g, double daylight) { return Math.abs(g.d("active") - awakePosition(daylight)) < 0.5; }

    /** Individuals who would be out and about right now. */
    public List<Animal> pick(String name, int count, boolean force) {
        List<Animal> pool = pools.get(name);
        if (asleep(name) && !force) return new ArrayList<>();
        if (force && pool.isEmpty()) wanderIn(name, 2);
        List<Animal> ready = new ArrayList<>();
        for (Animal i : pool) if (awake(i.g)) ready.add(i);
        if (ready.isEmpty() && force) ready.addAll(pool);
        rng.shuffle(ready);
        return ready.size() > count ? new ArrayList<>(ready.subList(0, count)) : ready;
    }

    /** Hibernating. Sleepers don't age, starve or get hunted, and wake when it warms. */
    public boolean asleep(String name) {
        Climate climate = eco.climate;
        if (name.equals("frog")) return climate.frozen();
        return HIBERNATORS.contains(name) && climate.dormant();
    }

    public int awakeCount(String name) {
        if (asleep(name)) return 0;
        int n = 0;
        for (Animal i : pools.get(name)) if (awake(i.g)) n++;
        return n;
    }

    /** How many of a species winter can't take. Hunters and hunger alike stop here until the thaw. */
    int winterFloor(String name) {
        Species sp = SPECIES.get(name);
        if (!eco.climate.dormant() || sp == null) return 0;
        return Math.max(2, sp.start / 2);
    }

    public void kill(String name, Animal ind) {
        List<Animal> pool = pools.get(name);
        if (pool != null && pool.contains(ind) && pool.size() > winterFloor(name)) pool.remove(ind);
    }

    /** Strangers from the wider forest, carrying whatever they carry. */
    public void wanderIn(String name, int n) {
        for (int k = 0; k < n; k++) pools.get(name).add(newIndividual(name, null, SPECIES.get(name).mature + 1, 0.2));
    }

    // ---- what the clearing offers ------------------------------------------

    Env survey() {
        Env env = new Env();
        double lums = 0;
        for (int k = 0; k < 48; k++) {
            int[] at = eco.randomGround();
            lums += luminance(eco.c.comp[at[1] * eco.w + at[0]]);
        }
        env.ground = lums / 48;
        for (Eco.Plant p : eco.plants) {
            String kind = p.kind;
            if (kind.equals("clover") || kind.equals("fern") || kind.equals("spire"))
                env.forage[Math.min(4, (int) (p.g.d("toxin", 0.05) * 5))] += p.shown;
            if (kind.equals("fern")) env.fern += p.shown;
            else if (kind.equals("shrub")) env.fruit += p.shown;
            else if (kind.equals("sapling")) env.trees += p.shown;
            else if (kind.equals("log")) env.logs += 1;
            if (kind.equals("spire")) env.flowers += 1;
        }
        return env;
    }

    Env env() { return memoryEnv != null ? memoryEnv : survey(); }

    double detect(G g, Env env) {
        double contrast = Math.min(1.0, Math.abs(COAT_LUM[coatIndex(g)] - env.ground) / 85.0);
        return clamp(0.2 + 0.8 * contrast + 0.7 * g.d("glow")) * (0.7 + 0.6 * g.d("size"));
    }

    double huntChance(G hunter, G prey, Env env) {
        double escape = 0.5 * prey.d("speed") + 0.5 * prey.d("wary");
        return 0.55 * detect(prey, env) * (1 - 0.75 * escape) * (0.4 + 0.8 * hunter.d("speed")) * overlap(hunter.d("active"), prey.d("active"));
    }
    public double huntChance(G hunter, G prey) { return huntChance(hunter, prey, env()); }

    // ---- one step ----------------------------------------------------------

    public void tick() {
        tickLeft--;
        if (tickLeft <= 0) {
            tickLeft = STEP_TICKS;
            step(false);
        }
    }

    public void step(boolean offline) {
        steps++;
        Env env = memoryEnv = survey();
        boolean dormant = eco.climate.dormant();
        if (Boolean.TRUE.equals(memory.get("was_dormant")) && !dormant) spring();
        memory.put("was_dormant", dormant);
        disease();
        hunt(env, offline);
        feed(env);
        if (!offline) graze();
        ageAndBreed(env);
        migrate();
        record();
        if (!offline) {
            residents();
            watch(env);
        }
    }

    /** The thaw. Whoever made it through the winter gets a real chance at the year ahead. */
    private void spring() {
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            Species sp = SPECIES.get(e.getKey());
            for (Animal ind : e.getValue()) {
                ind.e = Math.max(ind.e, 1.3);
                ind.age = Math.min(ind.age, (int) (sp.life * 0.4)); // survivors of a winter aren't about to drop dead
            }
            if (e.getValue().size() < 2) wanderIn(e.getKey(), 2 - e.getValue().size());
        }
        for (Animal m : eco.beetles) m.e = Math.max(m.e, 1.0);
        for (Animal m : eco.moths) m.e = Math.max(m.e, 1.0);
        for (Animal m : eco.wild.frogs) m.e = Math.max(m.e, 1.0);
        if (eco.moths.size() < 3) for (int k = 0; k < 4; k++) eco.moths.add(eco.newMoth());
        if (eco.beetles.size() < 3) for (int k = 0; k < 4; k++) { int[] at = eco.randomGround(); eco.beetles.add(eco.newBeetle(at[0], at[1], null, 1.0)); }
        say("Spring. Everything that waited is moving again.");
    }

    /** A rolling ten hours of head counts, for the graphs in the menu. */
    @SuppressWarnings("unchecked")
    private void record() {
        Map<String, Object> history = sub("history");
        List<String> names = new ArrayList<>(SPECIES.keySet());
        names.addAll(RESIDENT_BASE.keySet());
        for (String name : names) {
            List<Object> row = (List<Object>) history.get(name);
            if (row == null) { row = new ArrayList<>(); history.put(name, row); }
            row.add((double) group(name).size());
            while (row.size() > 120) row.remove(0);
        }
    }

    /** The screen was off. The animals weren't. */
    public void catchUp(double seconds, double ticksPerSecond) {
        int count = Math.min(OFFLINE_STEPS, (int) (seconds * ticksPerSecond / STEP_TICKS));
        if (count < 1) return;
        Map<String, Integer> before = new HashMap<>();
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) before.put(e.getKey(), e.getValue().size());
        for (int k = 0; k < count; k++) step(true);
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            int was = before.get(e.getKey());
            if (Math.abs(e.getValue().size() - was) >= 3) changes.add(PLURAL.get(e.getKey()) + " " + was + " to " + e.getValue().size());
        }
        double hours = seconds / 3600.0;
        String when = hours >= 2 ? String.format(Locale.US, "%.0f hours", hours) : String.format(Locale.US, "%.0f minutes", seconds / 60);
        say("Away for " + when + ". Life went on" + (changes.isEmpty() ? "." : ": " + join(changes) + "."));
        watch(survey());
    }

    private static String join(List<String> parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) { if (b.length() > 0) b.append(", "); b.append(p); }
        return b.toString();
    }

    private void disease() {
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            String name = e.getKey();
            List<Animal> pool = e.getValue();
            Species sp = SPECIES.get(name);
            Plague plague = plagues.get(name);
            if (plague == null) {
                if (eco.climate.dormant()) continue;
                if (sp.cap >= 12 && pool.size() >= sp.cap * 0.8 && rng.random() < 0.006 && again("plague-" + name, 150)) {
                    Plague p = new Plague();
                    p.v = rng.uniform(0.3, 1.0);
                    p.left = rng.randint(8, 24);
                    plagues.put(name, p);
                    say("Sickness is spreading among the crowded " + PLURAL.get(name) + ".");
                }
                continue;
            }
            for (Animal ind : new ArrayList<>(pool)) {
                if (rng.random() < plague.v * 0.16 * Math.pow(1 - ind.g.d("resist"), 2)) {
                    pool.remove(ind);
                    plague.dead += 1;
                    int[] at = eco.randomGround();
                    eco.wild.enrich(at[0], at[1], 0.04);
                }
            }
            plague.left -= 1;
            if (plague.left <= 0 || pool.isEmpty()) {
                say("The sickness among the " + PLURAL.get(name) + " has passed. It took " + (int) plague.dead + ", and " + pool.size() + " remain.");
                plagues.remove(name);
            }
        }
    }

    private void hunt(Env env, boolean offline) {
        for (Map.Entry<String, String[]> e : PREY.entrySet()) {
            String name = e.getKey();
            String[] menu = e.getValue();
            if (asleep(name)) continue;
            for (Animal hunter : pools.get(name)) {
                if (hunter.e > 2.0) continue;
                G g = hunter.g;
                int tries = !name.equals("songbird") ? 3 : g.d("diet") > 0.5 ? 1 : 0;
                for (int t = 0; t < tries; t++) {
                    // Diet gene slides along the menu, with some slop.
                    int k = (int) (clamp(g.d("diet") + rng.gauss(0, 0.22)) * (menu.length - 0.001));
                    String kind = menu[k];
                    if (asleep(kind) || group(kind).size() <= winterFloor(kind)) continue;
                    List<Animal> list;
                    if (pools.containsKey(kind)) list = pools.get(kind);
                    else if (offline) {
                        hunter.e += 0.05; // residents don't change while nobody is running the clearing
                        continue;
                    } else list = group(kind);
                    if (list.isEmpty()) continue;
                    Animal prey = rng.choice(list);
                    if (prey.g == null || rng.random() > huntChance(g, prey.g, env)) continue;
                    if (kind.equals("frog") && prey.swim) continue;
                    list.remove(prey);
                    hunter.e += PREY_VALUE.get(kind);
                    int[] at = eco.randomGround();
                    eco.wild.enrich(at[0], at[1], 0.03);
                    eco.wild.note(kind + " taken by " + name);
                    break;
                }
            }
        }
    }

    private void feed(Env env) {
        // Lean times: winter takes most of the food, so numbers fall until spring.
        double lean = eco.climate.dormant() ? 0.35 : 1.0;
        int nRabbit = Math.max(1, pools.get("rabbit").size());
        for (Animal ind : pools.get("rabbit")) {
            G g = ind.g;
            double supply = 0;
            for (int k = 0; k < 5; k++) supply += env.forage[k] * clamp(1 - Math.max(0.0, (k + 0.5) / 5 - g.d("tolerance")) * 1.6);
            ind.e += lean * Math.min(0.45, 0.00068 * supply / (nRabbit + 3)) * (0.8 + 0.4 * g.d("size")) * (1 - 0.25 * g.d("wary")) + 0.015;
        }
        int n = Math.max(1, pools.get("songbird").size());
        int insects = eco.beetles.size() + eco.moths.size();
        for (Animal ind : pools.get("songbird")) {
            double d = ind.g.d("diet");
            ind.e += lean * Math.min(0.4, (0.0012 * (env.fruit + 8 * env.flowers) * (1 - d) + 0.02 * insects * d) / (n + 2)) + 0.02;
        }
        n = Math.max(1, pools.get("squirrel").size());
        for (Animal ind : pools.get("squirrel"))
            ind.e += lean * Math.min(0.4, (0.0009 * env.trees + 0.0005 * env.fruit + 0.2 * env.logs) / (n + 1)) + 0.03;
        n = Math.max(1, pools.get("stag").size());
        for (Animal ind : pools.get("stag"))
            ind.e += lean * Math.min(0.2, 0.00002 * (env.fern + env.fruit + env.trees) / n) + 0.008;
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            String name = e.getKey();
            Species sp = SPECIES.get(name);
            if (asleep(name)) continue;
            boolean hunter = name.equals("fox") || name.equals("owl") || name.equals("bats") || name.equals("hedgehog");
            for (Animal ind : e.getValue()) {
                G g = ind.g;
                double upkeep = sp.metab * (0.65 + 0.7 * g.d("size")) * (1 + 0.3 * g.d("speed") + 0.25 * g.d("tolerance") + 0.25 * g.d("ornament"));
                ind.e = Math.min(3.0, ind.e - upkeep + (hunter ? 0.012 : 0.0));
            }
        }
    }

    /** Everything those unseen rabbits ate came from these plants. */
    private void graze() {
        int n = pools.get("rabbit").size();
        int bites = Math.min(14, n / 2) / (eco.climate.dormant() ? 4 : 1);
        List<Eco.Plant> plants = new ArrayList<>();
        for (Eco.Plant p : eco.plants)
            if ((p.kind.equals("clover") || p.kind.equals("fern") || p.kind.equals("spire")) && p.shown > 6) plants.add(p);
        double tolerance = meanGenes(pools.get("rabbit")).d("tolerance", 0.0);
        for (int b = 0; b < bites; b++) {
            if (plants.isEmpty()) return;
            // A grazer tries a few plants and takes the one that tastes safest.
            int[] picks = rng.sampleIndices(plants.size(), Math.min(4, plants.size()));
            Eco.Plant p = null;
            double best = 0;
            for (int idx : picks) {
                Eco.Plant q = plants.get(idx);
                double taste = q.g.d("toxin", 0.05) + rng.random() * 0.06;
                if (p == null || taste < best) { p = q; best = taste; }
            }
            if (rng.random() < 1 - Math.max(0.0, p.g.d("toxin", 0.05) - tolerance) * 2.0) {
                eco.unshow(p, Math.max(3, (int) (p.shown * 0.4)));
                eco.restamp(p);
                if (p.shown <= 3 && !p.kind.equals("fern")) p.wilting = true; // grazed to the ground
            }
        }
        int eaters = pools.get("songbird").size() / 5;
        List<Eco.Plant> fruiting = new ArrayList<>();
        for (Eco.Plant p : plants) if (p.kind.equals("spire")) fruiting.add(p);
        for (int k = 0; k < eaters; k++) {
            if (!fruiting.isEmpty() && rng.random() < 0.5) {
                Eco.Plant p = rng.choice(fruiting);
                eco.unshow(p, Math.max(3, (int) (p.shown * 0.9)));
                eco.restamp(p);
            }
        }
        if (!pools.get("squirrel").isEmpty() && rng.random() < 0.02 * pools.get("squirrel").size()) {
            int[] at = eco.randomGround();
            eco.wild.bury(at[0], at[1]);
        }
    }

    private void ageAndBreed(Env env) {
        boolean dormant = eco.climate.dormant();
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            String name = e.getKey();
            List<Animal> pool = e.getValue();
            Species sp = SPECIES.get(name);
            if (asleep(name)) continue;
            // Winter thins a population but can't end it: the last few always find enough.
            int floor = winterFloor(name);
            for (Animal ind : new ArrayList<>(pool)) {
                ind.age += 1;
                if (ind.e <= 0 || ind.age > sp.life * (0.6 + 0.8 * ind.g.d("resist") * 0.5 + rng.random() * 0.6)) {
                    if (pool.size() <= floor) {
                        ind.e = Math.max(ind.e, 0.2);
                        continue;
                    }
                    pool.remove(ind);
                    int[] at = eco.randomGround();
                    eco.wild.enrich(at[0], at[1], 0.04);
                }
            }
            if (dormant) continue; // nobody raises young in the snow
            List<Animal> babies = new ArrayList<>();
            List<Animal> adults = new ArrayList<>();
            for (Animal i : pool) if (i.age >= sp.mature && i.e > 1.15) adults.add(i);
            for (Animal ind : adults) {
                if (pool.size() + babies.size() >= sp.cap) break;
                G g = ind.g;
                double display = 1 + 0.5 * g.d("ornament") + 0.6 * g.d("glow");
                if (rng.random() > sp.breed * display * (1 - 0.5 * g.d("resist"))) continue;
                // Mates are chosen by likeness, which is how one kind becomes two.
                Animal mate = null;
                double best = 0;
                for (int idx : rng.sampleIndices(adults.size(), Math.min(4, adults.size()))) {
                    Animal c = adults.get(idx);
                    if (c == ind) continue;
                    double d = distance(c.g, g);
                    if (mate == null || d < best) { mate = c; best = d; }
                }
                if (mate == null || best > 0.4) continue;
                for (int k = 0, clutch = rng.randint(1, sp.clutch); k < clutch; k++)
                    babies.add(newIndividual(name, childGenes(rng, g, mate.g), 0, 0.05));
                ind.e -= 0.45;
                mate.e -= 0.2;
            }
            pool.addAll(babies);
        }
    }

    private void migrate() {
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            String name = e.getKey();
            if (e.getValue().isEmpty() && rng.random() < 0.008) {
                wanderIn(name, 2);
                say("A pair of " + PLURAL.get(name) + " has wandered in from the wider forest, after none were left.");
            } else if (!e.getValue().isEmpty() && rng.random() < 0.003) wanderIn(name, 1);
        }
    }

    /** Colonisers for the residents, when their local lines die out. */
    private void residents() {
        if (!eco.cfg.fauna || eco.climate.dormant()) return;
        if (eco.beetles.isEmpty() && rng.random() < 0.1) {
            int[] at = eco.randomGround();
            eco.beetles.add(eco.newBeetle(at[0], at[1], null, 1.0));
            if (again("beetle-back", 150)) say("A beetle has found its way back into the clearing, after none were left.");
        }
        if (eco.moths.isEmpty() && rng.random() < 0.03) {
            eco.moths.add(eco.newMoth());
            eco.moths.add(eco.newMoth());
            if (again("moth-back", 150)) say("Moths have returned to the clearing, after none were left.");
        }
        // Nectar: whoever is flying feeds, and the well-fed breed.
        int flowers = 0;
        for (Eco.Plant p : eco.plants) if ((p.kind.equals("spire") || p.kind.equals("clover")) && p.shown == p.pix.size()) flowers++;
        double nectar = Math.min(0.14, flowers * 0.004);
        for (Eco.Moth m : new ArrayList<>(eco.moths)) {
            m.e = Math.min(2.5, m.e + nectar);
            if (m.e > 1.6 && eco.moths.size() < (int) (14 * eco.cfg.density)) {
                m.e -= 0.8;
                eco.moths.add(eco.newMoth(m.x, m.y, childOf(m.g, eco.likeliestMate(m).g), 0.8));
            }
        }
    }

    // ---- the journal: reports only -----------------------------------------

    public void say(String text) {
        List<Object> line = new ArrayList<>();
        line.add((double) (long) Clock.seconds());
        line.add((double) eco.tickNo);
        line.add(text);
        journal.add(line);
        while (journal.size() > 400) journal.remove(0);
    }

    public boolean once(String key) {
        Map<String, Object> seen = sub("seen");
        if (seen.containsKey(key)) return false;
        seen.put(key, (double) steps);
        return true;
    }

    /** True if this hasn't been said within `cooldown` steps. */
    public boolean again(String key, int cooldown) {
        Map<String, Object> seen = sub("seen");
        if (steps - num(seen, key, -cooldown) < cooldown) return false;
        seen.put(key, (double) steps);
        return true;
    }

    private static String capitalize(String w) { return w.substring(0, 1).toUpperCase(Locale.US) + w.substring(1); }

    private void watch(Env env) {
        Map<String, Object> refs = sub("ref"), highs = sub("high"), gone = sub("gone");
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            String name = e.getKey();
            List<Animal> pool = e.getValue();
            int n = pool.size();
            int cap = SPECIES.get(name).cap;
            String word = PLURAL.get(name);
            int high = Math.max((int) num(highs, name, 0), n);
            if (n == 0 && gone.get(name) == null) {
                gone.put(name, (double) steps);
                say("The last of the " + word + " is gone.");
            } else if (n != 0 && gone.get(name) != null) gone.remove(name);
            if (n >= cap * 0.85 && cap >= 7 && again("boom-" + name, 300)) say(capitalize(word) + " are everywhere: " + n + " of them.");
            if (high >= 6 && 0 < n && n <= high / 3.0 && again("crash-" + name, 60)) {
                say(capitalize(word) + " have crashed, from " + high + " to " + n + ".");
                high = n;
            }
            highs.put(name, (double) Math.max((int) (high * 0.99), n));
            if (pool.isEmpty()) continue;
            G means = meanGenes(pool);
            noteDrift(name, word, means, ref(refs, name, means));
            noteOddities(name, word, pool);
            noteSplit(name, word, pool);
        }
        String[] kinds = {"beetle", "moth", "frog"};
        for (String kind : kinds) {
            List<Animal> members = new ArrayList<>();
            for (Animal m : group(kind)) if (m.g != null) members.add(m);
            if (members.size() >= 3) {
                G means = meanGenes(members);
                noteDrift(kind, kind + "s", means, ref(refs, kind, means));
            }
        }
        notePlantsAndFungi();
        noteFireflies();
    }

    private void noteDrift(String name, String word, G means, G ref) {
        int old = (int) (ref.d("coat") * 14 + 0.5), now = (int) (means.d("coat") * 14 + 0.5);
        if (Math.abs(means.d("coat") - ref.d("coat")) > 0.14 && old != now) {
            say("The " + word + " are turning " + COAT_NAMES[now] + ", where they used to be " + COAT_NAMES[old] + ".");
            ref.put("coat", means.d("coat"));
        }
        if (ref.d("active") < 0.5 && 0.5 <= means.d("active")) {
            say("The " + word + " are shifting to daytime.");
            ref.put("active", means.d("active"));
        } else if (ref.d("active") >= 0.5 && 0.5 > means.d("active")) {
            say("The " + word + " are shifting to the night.");
            ref.put("active", means.d("active"));
        }
        for (Map.Entry<String, String[]> t : TRAIT_WORDS.entrySet()) {
            String gene = t.getKey();
            if (Math.abs(means.d(gene) - ref.d(gene)) > 0.2) {
                say("The " + word + " have grown " + (means.d(gene) > ref.d(gene) ? t.getValue()[1] : t.getValue()[0]) + ".");
                ref.put(gene, means.d(gene));
            }
        }
        String[] menu = PREY.get(name);
        if (Math.abs(means.d("diet") - ref.d("diet")) > 0.25 && menu != null) {
            say("The " + word + " are hunting differently now, mostly after " + menu[(int) (means.d("diet") * (menu.length - 0.001))] + "s.");
            ref.put("diet", means.d("diet"));
        }
    }

    private void noteOddities(String name, String word, List<Animal> pool) {
        String one = name.equals("fox") ? "fox" : name.equals("bats") ? "bat" : word.substring(0, word.length() - 1);
        for (Animal ind : pool) {
            G g = ind.g;
            if (g.d("glow") > 0.7 && again("glow-" + name, 500)) say("A " + one + " that glows has been born.");
            if (g.d("ornament") > 0.8 && again("orn-" + name, 500)) {
                String extra = name.equals("rabbit") ? "A rabbit with antlers" : name.equals("fox") ? "A fox with a great plume of a tail"
                        : name.equals("stag") ? "A stag with enormous antlers" : "A strikingly ornamented " + one;
                say(extra + " has been born.");
            }
        }
    }

    /** Has one population become two? Look for a gap between the two most different groups. */
    private void noteSplit(String name, String word, List<Animal> pool) {
        if (pool.size() < 6) return;
        Animal a = null, b = null;
        double best = 0;
        for (Animal i : pool) {
            double v = i.g.d("coat") + i.g.d("active");
            if (a == null || v > best) { a = i; best = v; }
        }
        for (Animal i : pool) {
            double v = distance(i.g, a.g);
            if (b == null || v > best) { b = i; best = v; }
        }
        List<Animal> ga = new ArrayList<>(), gb = new ArrayList<>();
        for (Animal i : pool) (distance(i.g, a.g) < distance(i.g, b.g) ? ga : gb).add(i);
        boolean split = Math.min(ga.size(), gb.size()) >= 3 && distance(meanGenes(ga), meanGenes(gb)) > 0.2;
        Map<String, Object> splits = sub("split");
        boolean was = Boolean.TRUE.equals(splits.get(name));
        if (split && !was) say("There now seem to be two kinds of " + word + ": " + describe(meanGenes(ga)) + ", and " + describe(meanGenes(gb)) + ".");
        else if (was && !split && again("unsplit-" + name, 30)) say("The two kinds of " + word + " are one kind again.");
        splits.put(name, split);
    }

    private void notePlantsAndFungi() {
        double total = 0;
        int count = 0;
        for (Eco.Plant p : eco.plants) {
            if (p.kind.equals("clover") || p.kind.equals("fern") || p.kind.equals("spire")) { total += p.g.d("toxin", 0.0); count++; }
        }
        if (count > 0) {
            double mean = total / count;
            int mark = (int) (mean * 5);
            if (mark > num(memory, "toxin_mark", 0)) {
                memory.put("toxin_mark", (double) mark);
                say("The meadow is growing more poisonous to eat, about " + (int) (mean * 100) + "% now. Red-flecked leaves are spreading.");
            }
        }
        double worst = 0;
        for (Eco.Colony c : eco.colonies) worst = Math.max(worst, c.g.d("parasite", 0.0));
        if (worst > 0.3 && once("parasite")) say("One of the fungal colonies has begun to infect beetles.");
    }

    private void noteFireflies() {
        List<Eco.Fly> flies = eco.flies;
        if (flies.size() < 6) return;
        double c = 0, s = 0;
        int[] hues = new int[Eco.FLY_RAMPS.length];
        for (Eco.Fly f : flies) {
            c += Math.cos(f.phase * 2 * Math.PI);
            s += Math.sin(f.phase * 2 * Math.PI);
            hues[f.hue]++;
        }
        if (Math.hypot(c / flies.size(), s / flies.size()) > 0.8 && again("sync", 300)) say("The fireflies are flashing as one.");
        int top = 0;
        for (int h = 1; h < hues.length; h++) if (hues[h] > hues[top]) top = h;
        if (hues[top] > flies.size() * 0.6 && top != (int) num(memory, "fly_hue", 0)) {
            memory.put("fly_hue", (double) top);
            String[] names = {"green", "teal", "amber", "ice blue", "rose"};
            say("Most fireflies now flash " + names[top] + ".");
        }
    }

    // ---- describing --------------------------------------------------------

    public String describe(G m) {
        List<String> parts = new ArrayList<>();
        parts.add(COAT_NAMES[(int) (clamp(m.d("coat")) * 14 + 0.5)]);
        if (m.d("size") > 0.62) parts.add("large");
        else if (m.d("size") < 0.2) parts.add("small");
        if (m.d("glow") > 0.5) parts.add("glowing");
        if (m.d("ornament") > 0.6) parts.add("ornamented");
        parts.add(m.d("active") > 0.5 ? "day-active" : "nocturnal");
        return join(parts);
    }

    /** What the menu needs to draw and describe a species: its average, and a few real individuals. */
    public static final class Entry {
        public int n;
        public String looks;
        public G means = new G();
        public List<Animal> members = new ArrayList<>();
        public List<Object> hist = new ArrayList<>();
        public boolean split, asleep;
    }

    @SuppressWarnings("unchecked")
    private Entry entry(String name, List<Animal> group) {
        Entry en = new Entry();
        for (Animal m : group) if (m.g != null) en.members.add(m);
        en.n = en.members.size();
        en.means = meanGenes(en.members);
        en.looks = en.n > 0 ? describe(en.means) : "none left";
        // Every individual by name, so the menu can show each one's own traits.
        if (en.members.size() > 40) en.members = new ArrayList<>(en.members.subList(0, 40));
        Object hist = sub("history").get(name);
        if (hist != null) {
            List<Object> row = (List<Object>) hist;
            en.hist = new ArrayList<>(row.subList(Math.max(0, row.size() - 60), row.size()));
        }
        en.split = Boolean.TRUE.equals(sub("split").get(name));
        en.asleep = asleep(name);
        return en;
    }

    public Map<String, Entry> census() {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) out.put(e.getKey(), entry(e.getKey(), e.getValue()));
        return out;
    }

    public Map<String, Entry> residentCensus() {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (String name : RESIDENT_BASE.keySet()) out.put(name, entry(name, group(name)));
        return out;
    }

    // ---- persistence -------------------------------------------------------

    public Map<String, Object> toState() {
        Map<String, Object> st = new LinkedHashMap<>();
        Map<String, Object> savedPools = new LinkedHashMap<>();
        for (Map.Entry<String, List<Animal>> e : pools.entrySet()) {
            List<Object> rows = new ArrayList<>();
            for (Animal a : e.getValue()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("g", a.g);
                row.put("age", (double) a.age);
                row.put("e", a.e);
                rows.add(row);
            }
            savedPools.put(e.getKey(), rows);
        }
        st.put("pools", savedPools);
        Map<String, Object> savedPlagues = new LinkedHashMap<>();
        for (Map.Entry<String, Plague> e : plagues.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("v", e.getValue().v);
            row.put("left", e.getValue().left);
            row.put("dead", e.getValue().dead);
            savedPlagues.put(e.getKey(), row);
        }
        st.put("plagues", savedPlagues);
        st.put("journal", journal);
        st.put("memory", memory);
        st.put("steps", (double) steps);
        st.put("left", (double) tickLeft);
        return st;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> st) {
        if (st == null) {
            say("Animals in this forest now carry genes of their own.");
            return;
        }
        for (Map.Entry<String, Object> e : ((Map<String, Object>) st.get("pools")).entrySet()) {
            List<Animal> pool = new ArrayList<>();
            for (Object o : (List<Object>) e.getValue()) {
                Map<String, Object> row = (Map<String, Object>) o;
                Animal a = new Animal();
                a.g = new G((Map<String, ?>) row.get("g"));
                a.age = ((Number) row.get("age")).intValue();
                a.e = ((Number) row.get("e")).doubleValue();
                pool.add(a);
            }
            pools.put(e.getKey(), pool);
        }
        plagues.clear();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) st.get("plagues")).entrySet()) {
            Map<String, Object> row = (Map<String, Object>) e.getValue();
            Plague p = new Plague();
            p.v = ((Number) row.get("v")).doubleValue();
            p.left = ((Number) row.get("left")).doubleValue();
            p.dead = ((Number) row.get("dead")).doubleValue();
            plagues.put(e.getKey(), p);
        }
        journal = (List<Object>) st.get("journal");
        memory = (Map<String, Object>) st.get("memory");
        steps = ((Number) st.get("steps")).intValue();
        tickLeft = ((Number) st.get("left")).intValue();
    }
}
