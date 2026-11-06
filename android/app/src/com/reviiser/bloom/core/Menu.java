package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;
import static com.reviiser.bloom.core.PixelScreen.GLYPH_H;
import static com.reviiser.bloom.core.PixelScreen.textWidth;
import static com.reviiser.bloom.core.PixelScreen.wrap;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * The menu: settings, a bestiary, a herbarium, a field map and the forest's journal, on a 320x240 indexed screen, in the
 * forest's own palette. Animals and plants are drawn from their real genes, using the same sprite code as the forest.
 * Everything is reached with a remote's arrows, Select and Back: up from the top of any page lands on the tabs.
 */
public final class Menu {
    public static final int W = 320, H = 240;
    public static final int KEY_UP = 1, KEY_DOWN = 2, KEY_LEFT = 3, KEY_RIGHT = 4, KEY_SELECT = 5, KEY_BACK = 6, KEY_PREV_TAB = 7, KEY_NEXT_TAB = 8;
    public static final int KEY_DIGIT = 100; // plus the digit, for remotes that have number keys

    /** What the menu needs from the device it runs on. */
    public interface Host {
        boolean isScreensaver();
        void openScreensaverSettings();
        String screensaverHint();
    }

    static final int BODY_Y = 42, BODY_H = 186;
    static final String[] TABS = {"SETTINGS", "ANIMALS", "PLANTS", "FOREST", "ODDITIES", "JOURNAL"};
    static final int INK = MOON.at(4), DIM = FLECK.at(2), FAINT = FLECK.at(1), GOOD = FIREFLY.at(3), WARM = EYE.at(1), BAD = FLOWER[4].at(1);
    static final int AWAKE_MARK = FLOWER[1].at(2); // periwinkle: stands clear of the yellow day strip in every palette

    private static final class Row {
        String kind, key, label, hint;
        double low, high, step;
        String[] options;
        static Row slider(String key, String label, double low, double high, double step, String hint) {
            Row r = new Row();
            r.kind = "slider"; r.key = key; r.label = label; r.low = low; r.high = high; r.step = step; r.hint = hint;
            return r;
        }
        static Row combo(String key, String label, String[] options, String hint) {
            Row r = new Row();
            r.kind = "combo"; r.key = key; r.label = label; r.options = options; r.hint = hint;
            return r;
        }
        static Row other(String kind, String key, String label, String hint) {
            Row r = new Row();
            r.kind = kind; r.key = key; r.label = label; r.hint = hint;
            return r;
        }
    }

    static final List<String> WHOLE_NUMBERS = Arrays.asList("fireflies", "fps", "pixel_size");
    private static final List<Row> ROWS = new ArrayList<>(Arrays.asList(
        Row.slider("darkness", "DARKNESS", 0.0, 1.0, 0.05, "How deep the night is. Glowing things fade less."),
        Row.slider("glow", "GLOW", 0.0, 1.0, 0.05, "Fireflies, mushrooms, mycelium sparks, the moon."),
        Row.slider("saturation", "COLOUR", 0.0, 1.5, 0.05, "From one palette colour up to vivid."),
        Row.slider("natural", "NATURAL", 0.0, 1.0, 0.05, "Each thing its own colour, not one tint over all."),
        Row.slider("flare", "LENS FLARE", 0.0, 1.0, 0.05, "Bloom and flare from the sun and moon."),
        Row.slider("crt", "CRT FILTER", 0.0, 1.0, 0.05, "Beam smear, phosphor dots and old glass. 0 is off."),
        Row.slider("speed", "PACE OF LIFE", 0.25, 4.0, 0.25, "How fast time passes in the forest."),
        Row.slider("density", "ABUNDANCE", 0.3, 1.5, 0.05, "How much life the clearing can carry."),
        Row.slider("fireflies", "FIREFLIES", 0, 80, 2, "How many fireflies at night."),
        Row.slider("fps", "FRAME RATE", 4, 15, 1, "Animation frames a second. Lower is gentler."),
        Row.slider("pixel_size", "PIXEL SIZE", 2, 8, 1, "Chunkier is cheaper. Each size grows its own forest."),
        Row.combo("theme", "PALETTE", Config.THEMES, "The colours of your forest, and of this menu."),
        Row.combo("time_of_day", "TIME OF DAY", Config.TIMES, "Auto follows your clock. Or stay in one hour."),
        Row.combo("season", "SEASON", Config.SEASON_MODES, "Auto follows your calendar. Or stay in one season."),
        Row.combo("hemisphere", "HEMISPHERE", Config.HEMISPHERES, "Which half of the world your seasons belong to."),
        Row.other("switch", "fauna", "ANIMALS", "All animals, and visitors like the wisp."),
        Row.other("switch", "light_shafts", "LIGHT SHAFTS", "Slow beams of moonlight or sun."),
        Row.other("switch", "weather", "WEATHER", "Clouds, rain, snowfall, storms and mist."),
        Row.other("switch", "oddities", "ODDITIES", "Cryptids and date-based surprises."),
        Row.other("switch", "constellations", "CONSTELLATIONS", "Real star positions, through the gaps in the leaves."),
        Row.other("switch", "battery_saver", "BATTERY SAVER", "Half the frame rate on battery."),
        Row.other("location", "location", "YOUR PLACE", "Where the stars are seen from. SELECT to set it."),
        Row.other("seed", "seed", "FOREST SEED", "SELECT, then UP/DOWN to dial a number. Forests are kept."),
        Row.other("auto", "auto", "SCREENSAVER", "")));

    static final String[] ANIMAL_ORDER = {"rabbit", "fox", "owl", "bats", "hedgehog", "songbird", "squirrel", "stag", "frog", "beetle", "moth", "snail"};
    static final Map<String, String[]> ANIMAL_POSE = new HashMap<>();
    static final Map<String, String> ANIMAL_NOTE = new HashMap<>(), ANIMAL_EATS = new HashMap<>(), PLANT_NOTE = new HashMap<>(), PLANT_TITLE = new HashMap<>();
    static final Map<String, List<String>> EATEN_BY = new HashMap<>();
    private static void put(Map<String, String> m, String... kv) { for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]); }
    static {
        String[][] poses = {{"rabbit", "sit", "hop"}, {"fox", "walk0", "walk1"}, {"owl", "fly0", "fly1"}, {"bats", "fly0", "fly1"}, {"hedgehog", "walk0", "walk1"}, {"songbird", "sit", "fly0"},
            {"squirrel", "run", "dig"}, {"stag", "walk0", "walk1"}, {"frog", "sit", "hop"}, {"beetle", "walk0", "walk0"}, {"moth", "fly0", "fly0"}, {"snail", "crawl", "crawl"}};
        for (String[] p : poses) ANIMAL_POSE.put(p[0], new String[]{p[1], p[2]});
        put(ANIMAL_NOTE, "rabbit", "Grazes the meadow, carries seed, bolts from foxes.", "fox", "Stalks slowly, then rushes. Follows the rabbits.", "owl", "Watches from the bough. Takes what shows up best.",
            "bats", "Hawks moths on the wing, turning on a whim.", "hedgehog", "Snuffles after beetles and snails.", "songbird", "Eats fruit and flowers, sows the seeds elsewhere.",
            "squirrel", "Buries nuts. The forgotten ones become trees.", "stag", "Rare. Walks round the water and spreads seed.", "frog", "Lives on the bank. Catches moths by tongue.",
            "beetle", "Grazes moss or fungus. Prey to many, host to some.", "moth", "Pollinator. Day-fliers are butterflies to you.", "snail", "Comes out after rain. Eats mushrooms.");
        put(ANIMAL_EATS, "rabbit", "clover, ferns, spires", "squirrel", "nuts, fruit", "stag", "ferns, shrubs, trees", "frog", "moths, fireflies", "beetle", "moss, fungus", "moth", "nectar", "snail", "mushrooms, moss");
        EATEN_BY.put("frog", new ArrayList<>(Arrays.asList("fox", "owl")));
        EATEN_BY.put("moth", new ArrayList<>(Arrays.asList("bat", "songbird", "frog")));
        for (Map.Entry<String, String[]> e : Evolution.PREY.entrySet()) {
            String word = e.getKey().equals("bats") ? "bat" : e.getKey();
            for (String kind : e.getValue()) {
                List<String> list = EATEN_BY.get(kind);
                if (list == null) EATEN_BY.put(kind, list = new ArrayList<>());
                if (!list.contains(word)) list.add(word);
            }
        }
        put(PLANT_NOTE, "clover", "Fixes nitrogen. First to arrive on poor soil.", "spire", "Glowing flat blooms. Feeds moths and birds.", "fern", "Shades the ground so moss can move in.",
            "shrub", "Star-leaved, fruiting. Songbirds carry its seed.", "sapling", "Slow and hungry. Grows best with a fungal partner.", "mushroom", "The fruit of a hidden colony. Its shape is that colony's genes.",
            "log", "What a dead tree leaves. Feeds and shades its neighbours for hours.", "crocus", "Spring's first flower, up through the last snow.", "foxglove", "Midsummer's tall spire of hanging bells.",
            "goldenrod", "Autumn's last gold, loved by late moths.", "pumpkin", "Autumn, on a sprawling vine. Watch them in late October.", "snowdrop", "Winter's small defiance. Blooms in the snow.");
        put(PLANT_TITLE, "sapling", "YOUNG TREE", "log", "NURSE LOG", "spire", "CUP SPIRE");
    }
    static final String[][] TRAITS = {{"size", "SIZE"}, {"speed", "SPEED"}, {"wary", "WARY"}, {"glow", "GLOW"}, {"ornament", "ORNATE"}, {"resist", "RESIST"}};
    static final String[] PLANT_ORDER = {"clover", "spire", "fern", "shrub", "sapling", "mushroom", "log", "crocus", "foxglove", "goldenrod", "pumpkin", "snowdrop"};
    static final String[][] PLANT_TRAITS = {{"size", "SIZE"}, {"bushy", "BUSHY"}, {"bloom", "BLOOM"}, {"wet", "WET"}, {"dark", "SHADE"}, {"toxin", "TOXIN"}};
    static final String[][] ICON_WORDS = {{"rabbit", "rabbit"}, {"fox", "fox"}, {"owl", "owl"}, {"bat", "bats"}, {"hedgehog", "hedgehog"}, {"songbird", "songbird"}, {"squirrel", "squirrel"},
        {"stag", "stag"}, {"frog", "frog"}, {"beetle", "beetle"}, {"moth", "moth"}, {"snail", "snail"}};
    static final int[] SILHOUETTE = Oddities.colors('#', CANOPY.at(1), '+', CANOPY.at(1), 'w', CANOPY.at(2), 'o', CANOPY.at(2), 'O', CANOPY.at(1), '^', CANOPY.at(2));

    static int seasonColor(String season) {
        switch (season) {
            case "spring": return FLOWER[3].at(2);
            case "summer": return FIREFLY.at(3);
            case "autumn": return FLOWER[5].at(2);
            default: return STAG.at(2);
        }
    }

    static G defaultGenes() {
        return new G().with("size", 0.4).with("coat", 0.4).with("speed", 0.5).with("wary", 0.5).with("active", 0.5).with("diet", 0.5).with("resist", 0.1).with("tolerance", 0.1).with("glow", 0.0).with("ornament", 0.1);
    }

    static int[] creatureColors(String name, G g) {
        if (name.equals("moth") && g.d("active", 0) > 0.55) {
            Ramp wing = FLOWER[(int) (g.d("coat") * 7.99)];
            return Oddities.colors('#', wing.at(1), '+', HARE.at(0), 'w', wing.at(2), '^', wing.at(2));
        }
        return Evolution.coatColors(g);
    }

    private static final class FloraRow {
        int n, mature, wilting, partnered, gen;
        G means = new G();
        TreeSet<Integer> hues = new TreeSet<>();
        List<Eco.Plant> samples = new ArrayList<>();
    }

    private static final class JournalRow {
        boolean day;
        String heading, when, icon;
        List<String> lines;
        int tone;
        int height() { return day ? 13 : Math.max(16, lines.size() * GLYPH_H + 2); }
    }

    private final Host host;
    private World world;
    private Config cfg;
    public final Map<String, Object> values;
    private boolean changed;
    private final PixelScreen s = new PixelScreen(W, H);
    private final int[][] bannerIcon = Icon.grid(24);
    private int tab, row, rowTop, animal, oddIndex, plant, scroll, maxScroll, frame;
    private boolean tabFocus, individualFocus;
    private final Map<String, Animal> chosen = new HashMap<>();
    private final Map<String, Integer> chosenAt = new HashMap<>();
    private String seedText, locationText;
    private int seedCursor, locationCursor, seedChoice;
    private final Map<String, List<int[]>> spriteCache = new HashMap<>();
    private double[] stripDaylight, stripWarmth;
    private String stripKey;
    private int[] bg = new int[W * H];
    private final double[][] motes = new double[9][];
    private final Random random = new Random();
    private List<JournalRow> journalRows = new ArrayList<>();
    private Map<String, Evolution.Entry> species = new LinkedHashMap<>();
    private final Map<String, FloraRow> flora = new HashMap<>();
    private int[] fieldMap;
    private int fieldW, fieldH;
    private long nextRefresh;
    private boolean closed;

    public Menu(Host host, World world, Config cfg) {
        this.host = host;
        this.cfg = cfg;
        this.values = cfg.toMap();
        for (int k = 0; k < motes.length; k++) motes[k] = new double[]{random.nextDouble() * W, 2 + random.nextDouble() * 24, random.nextDouble(), 0.02 + random.nextDouble() * 0.03};
        applyUiPalette();
        attach(world);
    }

    /** The world can be replaced while the menu is open, when the seed or the pixel size changes. */
    public void attach(World next) {
        world = next;
        Scene sc = world.scene;
        int ox = Math.max(0, Math.min(sc.w - W, sc.moonX - W / 2));
        for (int y = 0; y < H; y++) {
            int sy = Math.min(sc.h - 1, y * sc.h / H);
            int o = sy * sc.w + ox;
            for (int x = 0; x < W; x++) {
                int v = 0;
                if (x + ox < sc.w) v = sc.fg[o + x] != 0 ? sc.fg[o + x] : sc.base[o + x];
                bg[y * W + x] = v;
            }
        }
        refresh();
    }

    private void applyUiPalette() {
        double saturation = Math.max(0.7, Math.min(1.2, num("saturation")));
        System.arraycopy(Palette.compute((String) values.get("theme"), 0.42, 0.85, saturation, 0.0, 0.0, 0, null, 0, 0, 0, num("natural")), 0, s.pal, 0, 256);
    }

    private double num(String key) { return Json.num(values.get(key)); }

    public int[] pixels() { return s.out; }
    public boolean closed() { return closed; }

    /** True once if a setting has changed since last asked, for whoever is applying them. */
    public boolean takeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    // ---- what is true of the forest right now --------------------------------

    private void refresh() {
        spriteCache.clear();
        Evolution evo = world.eco.wild.evo;
        species = new LinkedHashMap<>(evo.census());
        species.putAll(evo.residentCensus());
        flora.clear();
        Eco eco = world.eco;
        for (String kind : Eco.LIMITS.keySet()) {
            List<Eco.Plant> members = new ArrayList<>();
            for (Eco.Plant p : eco.plants) if (p.kind.equals(kind)) members.add(p);
            FloraRow r = new FloraRow();
            r.n = members.size();
            for (Eco.Plant p : members) {
                if (p.shown == p.pix.size() && !p.wilting) r.mature++;
                if (p.wilting) r.wilting++;
                if (p.linked) r.partnered++;
                r.gen = Math.max(r.gen, p.gen);
                r.hues.add(p.g.i("hue", 0));
            }
            if (!members.isEmpty()) {
                for (String key : new String[]{"size", "bushy", "bloom", "wet", "dark", "toxin"}) {
                    double sum = 0;
                    for (Eco.Plant p : members) sum += p.g.d(key, 0.0);
                    r.means.put(key, sum / members.size());
                }
                int spacing = Math.max(1, members.size() / 3);
                for (int k = 0; k < members.size() && r.samples.size() < 3; k += spacing) r.samples.add(members.get(k));
            }
            flora.put(kind, r);
        }
        buildFieldMap();
        journalRows = layoutJournal();
        nextRefresh = Clock.millis() + 30000;
    }

    // ---- frame -------------------------------------------------------------

    public void draw() {
        frame++;
        if (Clock.millis() >= nextRefresh && tab > 0) refresh();
        s.setClip();
        System.arraycopy(bg, 0, s.px, 0, bg.length);
        banner();
        tabs();
        switch (tab) {
            case 0: drawSettings(); break;
            case 1: drawAnimals(); break;
            case 2: drawPlants(); break;
            case 3: drawForest(); break;
            case 4: drawOddities(); break;
            default: drawJournal();
        }
        s.setClip();
        s.flush();
    }

    private void banner() {
        for (double[] m : motes) { // a few fireflies of its own, up in the leaves
            m[0] = (m[0] + m[3] * 8) % W;
            m[2] = (m[2] + m[3]) % 1.0;
            if (m[2] < 0.35) s.pixel((int) m[0], (int) m[1], FIREFLY.at(m[2] < 0.15 ? 4 : 3));
        }
        s.text(8, 6, "BLOOM", FIREFLY.at(3), CANOPY.at(0), 3);
        for (int r = 0; r < bannerIcon.length; r++)
            for (int c = 0; c < bannerIcon[r].length; c++) if (bannerIcon[r][c] != 0) s.pixel(100 + c, 4 + r, bannerIcon[r][c]);
        s.text(128, 6, "BY REVIISER", DIM, CANOPY.at(0), 1);
        String right = "FOREST " + world.seed;
        s.text(W - textWidth(right) - 6, 6, right, DIM, CANOPY.at(0), 1);
        String age = String.format(Locale.US, "%.1f HOURS OLD", world.eco.tickNo / 7200.0);
        s.text(W - textWidth(age) - 6, 16, age, INK, CANOPY.at(0), 1);
        String season = world.eco.climate.season;
        s.text(128, 16, season.toUpperCase(Locale.US), seasonColor(season), CANOPY.at(0), 1);
    }

    private void tabs() {
        int x = 2;
        for (int k = 0; k < TABS.length; k++) {
            int w = 50;
            boolean current = k == tab;
            s.rect(x, 31, w, 12, tabFocus && current && frame % 6 < 4 ? GOOD : CANOPY.at(0));
            s.rect(x + 1, 32, w - 2, 11, current ? SKY.at(1) : CANOPY.at(2));
            s.rect(x + 1, 31, w - 2, 1, current ? FLECK.at(1) : FLECK.at(0));
            s.text(x + (w - textWidth(TABS[k])) / 2 + 1, 34, TABS[k], current ? GOOD : DIM);
            x += w + 3;
        }
    }

    private void footer(String text) {
        s.rect(0, H - 11, W, 11, CANOPY.at(0));
        s.text(6, H - 9, text.length() > 52 ? text.substring(0, 52) : text, DIM);
    }

    // ---- settings ----------------------------------------------------------

    private void drawSettings() {
        s.panel(4, BODY_Y, W - 8, BODY_H);
        int step = 8, visible = (BODY_H - 10) / step;
        if (row < rowTop) rowTop = row;
        if (row >= rowTop + visible) rowTop = row - visible + 1;
        rowTop = Math.max(0, Math.min(ROWS.size() - visible, rowTop));
        int y = BODY_Y + 5;
        String hint = "";
        for (int k = rowTop; k < Math.min(ROWS.size(), rowTop + visible); k++) {
            Row r = ROWS.get(k);
            boolean selected = k == row && !tabFocus;
            if (selected) {
                s.rect(8, y - 1, W - 16, step, SKY.at(3));
                if (frame % 6 < 4) s.text(9, y, ">", GOOD);
            }
            s.text(18, y, r.label, selected ? INK : DIM);
            if (r.kind.equals("slider")) {
                double value = num(r.key);
                s.meter(116, y + 1, 30, (value - r.low) / (r.high - r.low), selected ? FIREFLY.at(3) : TEAL.at(5), SKY.at(0));
                String shown = WHOLE_NUMBERS.contains(r.key) ? Integer.toString((int) value) : String.format(Locale.US, "%.2f", value);
                s.text(W - 12 - textWidth(shown), y, shown, INK);
            } else if (r.kind.equals("combo")) {
                String text = String.valueOf(values.get(r.key)).toUpperCase(Locale.US);
                s.text(116, y, "<", selected ? GOOD : FAINT);
                s.text(126, y, text, INK);
                s.text(126 + textWidth(text) + 4, y, ">", selected ? GOOD : FAINT);
            } else if (r.kind.equals("switch")) {
                switchAt(116, y, Boolean.TRUE.equals(values.get(r.key)), selected);
            } else if (r.kind.equals("seed")) {
                drawSeedRow(y, selected);
            } else if (r.kind.equals("location")) {
                drawLocationRow(y, selected);
            } else {
                switchAt(116, y, host.isScreensaver(), selected);
                if (selected) s.text(148, y, "SELECT TO CHANGE", DIM);
            }
            if (selected) hint = rowHint(r);
            y += step;
        }
        if (rowTop > 0) s.text(W - 14, BODY_Y + 4, "^", DIM);
        footer(tabFocus ? "LEFT/RIGHT PAGES   DOWN TO ENTER   BACK TO THE FOREST" : hint);
    }

    private String rowHint(Row r) {
        if (r.kind.equals("seed")) {
            if (seedText != null) return "UP/DOWN DIGIT  LEFT/RIGHT MOVE  SELECT GROW  BACK NO";
            return seedChoice == 0 ? r.hint : "SELECT for a new forest, chosen at random.";
        }
        if (r.kind.equals("location")) {
            if (locationText != null) return "UP/DOWN DIGIT  LEFT/RIGHT MOVE  SELECT OK  BACK NO";
            return "AUTO".equals(locationShown()) ? r.hint : "LEFT/RIGHT back to auto. North and east are plus.";
        }
        if (r.kind.equals("auto")) return host.screensaverHint();
        return r.hint;
    }

    private void drawSeedRow(int y, boolean selected) {
        boolean editing = seedText != null;
        String shown = editing ? seedText : Long.toString(world.seed);
        s.rect(115, y - 1, 66, 8, CANOPY.at(0));
        s.text(117, y, shown, editing ? GOOD : INK);
        if (editing && frame % 4 < 3) s.rect(117 + seedCursor * 6, y + 6, 5, 1, GOOD);
        button(186, y - 1, "GROW", selected && (editing || seedChoice == 0));
        button(218, y - 1, "RANDOM", selected && !editing && seedChoice == 1);
    }

    private String locationShown() {
        String v = (String) values.get("location");
        return v == null || v.trim().equalsIgnoreCase("auto") ? "AUTO" : v;
    }

    private void drawLocationRow(int y, boolean selected) {
        boolean editing = locationText != null;
        String shown = editing ? locationText : locationShown();
        s.text(116, y, shown, editing ? GOOD : INK);
        if (editing && frame % 4 < 3) s.rect(116 + locationCursor * 6, y + 7, 5, 1, GOOD);
        if (!editing && "AUTO".equals(shown)) {
            String place = world.eco.sky.placeName();
            if (place != null) {
                String tail = place.substring(place.lastIndexOf('/') + 1).replace('_', ' ').toUpperCase(Locale.US);
                s.text(146, y, tail.length() > 26 ? tail.substring(0, 26) : tail, DIM);
            }
        }
    }

    private void switchAt(int x, int y, boolean on, boolean selected) {
        s.rect(x, y - 1, 28, 8, CANOPY.at(0));
        if (on) {
            s.rect(x + 15, y, 12, 7, selected ? GOOD : TEAL.at(5));
            s.text(x + 2, y, "ON", selected ? GOOD : INK);
        } else {
            s.rect(x + 1, y, 10, 7, FAINT);
            s.text(x + 13, y, "OF", FAINT);
        }
    }

    private void button(int x, int y, String label, boolean lit) {
        s.rect(x, y, textWidth(label) + 5, 8, lit ? FLECK.at(1) : CANOPY.at(1));
        s.text(x + 3, y + 1, label, lit ? INK : DIM);
    }

    private void setValue(String key, Object value) {
        if (value instanceof Double) {
            double v = (Double) value;
            value = WHOLE_NUMBERS.contains(key) || key.equals("seed") ? (double) Math.rint(v) : Json.round(v, 2);
        }
        values.put(key, value);
        if (key.equals("theme") || key.equals("saturation") || key.equals("natural")) applyUiPalette();
        changed = true;
    }

    private void adjust(int direction) {
        Row r = ROWS.get(row);
        if (r.kind.equals("slider")) setValue(r.key, Math.max(r.low, Math.min(r.high, num(r.key) + direction * r.step)));
        else if (r.kind.equals("combo")) {
            int at = Arrays.asList(r.options).indexOf(values.get(r.key));
            setValue(r.key, r.options[Clock.mod(at + direction, r.options.length)]);
        } else if (r.kind.equals("switch")) setValue(r.key, !Boolean.TRUE.equals(values.get(r.key)));
        else if (r.kind.equals("seed")) seedChoice = 1 - seedChoice;
        else if (r.kind.equals("location")) setValue("location", "auto");
    }

    private void seedRandom() {
        setValue("seed", (double) (1 + random.nextInt((1 << 30) - 1)));
        seedText = null;
    }

    /** Remotes rarely have number keys, so a number is dialled in digit by digit. */
    private boolean editSeed(int key) {
        if (key == KEY_UP || key == KEY_DOWN) {
            char[] t = seedText.toCharArray();
            t[seedCursor] = (char) ('0' + Clock.mod(t[seedCursor] - '0' + (key == KEY_UP ? 1 : -1), 10));
            seedText = new String(t);
        } else if (key == KEY_LEFT) {
            seedCursor = Math.max(0, seedCursor - 1);
        } else if (key == KEY_RIGHT) {
            if (seedCursor + 1 >= seedText.length() && seedText.length() < 10) seedText += "0";
            seedCursor = Math.min(seedText.length() - 1, seedCursor + 1);
        } else if (key >= KEY_DIGIT) {
            char[] t = seedText.toCharArray();
            t[seedCursor] = (char) ('0' + key - KEY_DIGIT);
            seedText = new String(t);
            if (seedCursor + 1 < 10) {
                if (seedCursor + 1 >= seedText.length()) seedText += "0";
                seedCursor++;
            }
        } else if (key == KEY_SELECT) {
            long value = Long.parseLong(seedText);
            if (value > 0) setValue("seed", (double) value);
            seedText = null;
        } else if (key == KEY_BACK) {
            seedText = null;
        } else return false;
        return true;
    }

    private static boolean editable(String text, int k) { char c = text.charAt(k); return c != '.' && c != ','; }

    private boolean editLocation(int key) {
        String t = locationText;
        if (key == KEY_UP || key == KEY_DOWN) {
            char[] a = t.toCharArray();
            char c = a[locationCursor];
            if (c == '+' || c == '-') a[locationCursor] = c == '+' ? '-' : '+';
            else a[locationCursor] = (char) ('0' + Clock.mod(c - '0' + (key == KEY_UP ? 1 : -1), 10));
            locationText = new String(a);
        } else if (key == KEY_LEFT || key == KEY_RIGHT) {
            int d = key == KEY_LEFT ? -1 : 1, k = locationCursor + d;
            while (k >= 0 && k < t.length() && !editable(t, k)) k += d;
            if (k >= 0 && k < t.length()) locationCursor = k;
        } else if (key == KEY_SELECT) {
            int comma = t.indexOf(',');
            double lat = Double.parseDouble(t.substring(0, comma)), lon = Double.parseDouble(t.substring(comma + 1));
            lat = Math.max(-90, Math.min(90, lat));
            lon = Math.max(-180, Math.min(180, lon));
            setValue("location", String.format(Locale.US, "%.1f, %.1f", lat, lon));
            locationText = null;
        } else if (key == KEY_BACK) {
            locationText = null;
        } else return false;
        return true;
    }

    private void startLocationEdit() {
        double[] here = world.eco.sky.placeDegrees();
        locationText = String.format(Locale.US, "%+05.1f,%+06.1f", here[0], here[1]);
        locationCursor = 0;
    }

    private void keySettings(int key) {
        Row r = ROWS.get(row);
        if (seedText != null && editSeed(key)) return;
        if (locationText != null && editLocation(key)) return;
        if (key == KEY_UP) {
            if (row == 0) tabFocus = true;
            else row--;
        } else if (key == KEY_DOWN) row = (row + 1) % ROWS.size();
        else if (key == KEY_LEFT || key == KEY_RIGHT) adjust(key == KEY_RIGHT ? 1 : -1);
        else if (key == KEY_SELECT) {
            if (r.kind.equals("seed")) {
                if (seedChoice == 1) seedRandom();
                else {
                    seedText = Long.toString(world.seed);
                    seedCursor = 0;
                }
            } else if (r.kind.equals("location")) startLocationEdit();
            else if (r.kind.equals("auto")) host.openScreensaverSettings();
            else if (r.kind.equals("switch") || r.kind.equals("combo")) adjust(1);
        } else if (key >= KEY_DIGIT && r.kind.equals("seed")) {
            seedText = Character.toString((char) ('0' + key - KEY_DIGIT));
            seedCursor = 0;
        }
    }

    // ---- shared picture tools ----------------------------------------------

    /** Centre a sprite in a box, as large as fits, or thinned out if it won't fit at all. */
    private void fitRows(String[] rows, int x, int y, int w, int h, int[] colors, int big) {
        int rw = 0, rh = rows.length, scale;
        for (String r : rows) rw = Math.max(rw, r.length());
        if (rw > w || rh > h) {
            int step = Math.max((int) Math.ceil(rw / (double) w), (int) Math.ceil(rh / (double) h));
            List<String> thin = new ArrayList<>();
            for (int k = 0; k < rows.length; k += step) {
                StringBuilder b = new StringBuilder();
                for (int c = 0; c < rows[k].length(); c += step) b.append(rows[k].charAt(c));
                thin.add(b.toString());
            }
            rows = thin.toArray(new String[0]);
            rw = 0;
            for (String r : rows) rw = Math.max(rw, r.length());
            rh = rows.length;
            scale = 1;
        } else scale = Math.max(1, Math.min(big, Math.min(w / rw, h / rh)));
        s.rows(rows, x + Clock.div(w - rw * scale, 2), y + h - rh * scale, colors, false, scale);
    }

    private void creature(String name, G g, int x, int y, int w, int h, int big, boolean moving) {
        String pose = ANIMAL_POSE.get(name)[moving && frame % 8 < 4 ? 1 : 0];
        fitRows(Sprites.animalRows(name, pose, g), x, y, w, h, creatureColors(name, g), big);
    }

    private void tile(int x, int y, int w, int h, boolean selected, boolean alive) {
        s.rect(x, y, w, h, selected ? FIREFLY.at(3) : CANOPY.at(0));
        s.dither(x + 1, y + 1, w - 2, h - 2, alive ? SKY.at(5) : CANOPY.at(1), alive ? SKY.at(4) : CANOPY.at(0));
        s.rect(x + 1, y + h - 5, w - 2, 4, alive ? TEAL.at(3) : CANOPY.at(1));
    }

    private int bars(int x, int y, String[][] items, G from) {
        for (String[] item : items) {
            double value = from.d(item[0], 0.0);
            s.text(x, y, item[1], DIM);
            s.meter(x + 40, y + 1, 34, value, item[1].equals("TOXIN") && value > 0.5 ? BAD : TEAL.at(5));
            y += GLYPH_H;
        }
        return y;
    }

    // ---- animals -----------------------------------------------------------

    private void drawAnimals() {
        s.panel(4, BODY_Y, 118, BODY_H);
        int tw = 36, th = 42;
        for (int k = 0; k < ANIMAL_ORDER.length; k++) {
            int x = 8 + (k % 3) * (tw + 1), y = BODY_Y + 4 + (k / 3) * (th + 2);
            String name = ANIMAL_ORDER[k];
            Evolution.Entry row = species.get(name);
            int n = row == null ? 0 : row.n;
            tile(x, y, tw, th, k == animal && !tabFocus && (!individualFocus || frame % 6 < 3), n > 0);
            Animal one = individual(name, row);
            if (one != null) creature(name, one.g, x + 2, y + 1, tw - 4, th - 7, 2, false);
            else fitRows(Sprites.animalRows(name, ANIMAL_POSE.get(name)[0], defaultGenes()), x + 2, y + 1, tw - 4, th - 7, SILHOUETTE, 2);
            String count = Integer.toString(n);
            s.text(x + tw - textWidth(count) - 1, y + 1, count, n != 0 ? GOOD : BAD);
        }
        String name = ANIMAL_ORDER[animal];
        animalPage(name, species.get(name));
        if (tabFocus) footer("LEFT/RIGHT PAGES   DOWN TO ENTER   BACK TO THE FOREST");
        else if (individualFocus) footer("LEFT/RIGHT EACH ONE   SELECT OR BACK TO FINISH");
        else footer(frame % 48 < 32 ? ANIMAL_NOTE.get(name) : "SELECT TO MEET EACH ONE   UP TOP FOR PAGES");
    }

    /** The individual being looked at, kept across refreshes while it's still alive. */
    private Animal individual(String name, Evolution.Entry row) {
        if (row == null || row.members.isEmpty()) return null;
        Animal one = chosen.get(name);
        if (one != null && row.members.contains(one)) return one;
        // That one has died, or nobody was chosen yet: move to whoever stands nearest in the list.
        Integer at = chosenAt.get(name);
        int k = Math.min(at == null ? 0 : at, row.members.size() - 1);
        chosen.put(name, row.members.get(k));
        return row.members.get(k);
    }

    private void choose(String name, Evolution.Entry row, int k) {
        if (row != null && !row.members.isEmpty()) {
            k = Clock.mod(k, row.members.size());
            chosen.put(name, row.members.get(k));
            chosenAt.put(name, k);
        }
    }

    private void animalPage(String name, Evolution.Entry row) {
        int x0 = 124;
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H);
        int x = x0 + 6, y = BODY_Y + 5;
        int n = row == null ? 0 : row.n;
        s.text(x, y, name.equals("bats") ? "BAT" : name.toUpperCase(Locale.US), n != 0 ? GOOD : FAINT, -1, 2);
        String tag = n != 0 ? n + " ALIVE" : "NONE LEFT";
        s.text(W - 10 - textWidth(tag), y + 4, tag, n != 0 ? INK : BAD);
        y += 18;
        Animal one = individual(name, row);
        int index = one != null ? row.members.indexOf(one) : 0;
        G g = one != null ? one.g : null;
        // Portrait of this individual, on its own patch of forest.
        int pw = 70, ph = 48;
        s.rect(x, y, pw, ph, CANOPY.at(0));
        s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY.at(5), SKY.at(4));
        s.dither(x + 1, y + ph - 9, pw - 2, 8, TEAL.at(3), TEAL.at(4));
        if (g != null) creature(name, g, x + 2, y + 2, pw - 4, ph - 6, 3, true);
        else fitRows(Sprites.animalRows(name, ANIMAL_POSE.get(name)[0], defaultGenes()), x + 2, y + 2, pw - 4, ph - 6, SILHOUETTE, 3);
        // Beside it: coat and waking hours, then the whole species' fortunes as a vine.
        int tx = x + pw + 5, right = W - 10;
        if (g != null) {
            int coat = (int) (g.d("coat") * 14 + 0.5);
            String cname = Evolution.COAT_NAMES[coat].toUpperCase(Locale.US);
            s.text(tx, y, cname.length() > 11 ? cname.substring(0, 11) : cname, INK);
            for (int k = 0; k < 3; k++) s.rect(right - 15 + k * 5, y + 1, 5, 5, Evolution.COATS[coat][k]);
            hoursGauge(tx, y + 8, g, row.asleep);
        }
        List<Object> hist = row == null ? new ArrayList<Object>() : row.hist;
        s.text(tx, y + 18, "POPULATION", FAINT);
        if (!hist.isEmpty()) {
            int peak = 0;
            for (Object v : hist) peak = Math.max(peak, Json.integer(v));
            s.text(right - textWidth(Integer.toString(peak)), y + 18, Integer.toString(peak), FAINT);
        }
        vine(tx, y + 27, right - tx, 22, hist);
        y += ph + 3;
        // Everyone alive: step along to see each one's own traits.
        if (row != null && !row.members.isEmpty()) {
            s.text(x, y, "INDIVIDUAL " + (index + 1) + " OF " + n, DIM);
            if (Evolution.SPECIES.containsKey(name)) {
                String age = String.format(Locale.US, "AGE %.1fH", one.age / 12.0);
                s.text(right - textWidth(age), y, age, FAINT);
            } else if (row.split) s.text(right - textWidth("TWO KINDS!"), y, "TWO KINDS!", WARM);
            y += 9;
            int pageSize = 8, page = index / pageSize, cx = x + 11;
            for (int k = page * pageSize; k < Math.min(row.members.size(), (page + 1) * pageSize); k++) {
                s.rect(cx, y, 17, 16, k == index ? GOOD : CANOPY.at(0));
                s.rect(cx + 1, y + 1, 15, 14, SKY.at(4));
                creature(name, row.members.get(k).g, cx + 1, y + 1, 15, 14, 1, false);
                cx += 18;
            }
            if (row.members.size() > pageSize) {
                arrow(x, y, "<");
                arrow(x + 11 + 18 * pageSize, y, ">");
            }
            y += 18;
            y = bars(x, y, TRAITS, g);
        } else {
            for (String line : limit(wrap(ANIMAL_NOTE.get(name), 30), 4)) {
                s.text(x, y, line, INK);
                y += GLYPH_H;
            }
        }
        int bottom = BODY_Y + BODY_H - 22;
        String eats = ANIMAL_EATS.get(name);
        String[] menu = Evolution.PREY.get(name);
        if (menu != null) eats = g != null ? menu[(int) (g.d("diet", 0) * (menu.length - 0.001))] + "s mostly" : menu[0] + "s";
        List<String> hunters = EATEN_BY.get(name);
        StringBuilder fears = new StringBuilder();
        if (hunters != null && !hunters.isEmpty()) {
            fears.append("FEARS ");
            for (int k = 0; k < Math.min(3, hunters.size()); k++) fears.append(k > 0 ? ", " : "").append(hunters.get(k).toUpperCase(Locale.US));
        } else fears.append("FEARS NOTHING");
        if (eats != null) s.text(x, bottom, cut(("EATS " + eats).toUpperCase(Locale.US), 30), DIM);
        s.text(x, bottom + 9, cut(fears.toString(), 30), DIM);
    }

    private static String cut(String t, int n) { return t.length() > n ? t.substring(0, n) : t; }
    private static List<String> limit(List<String> l, int n) { return l.size() > n ? l.subList(0, n) : l; }

    private void arrow(int x, int y, String label) {
        s.rect(x, y, 9, 16, CANOPY.at(1));
        s.text(x + 2, y + 4, label, INK);
    }

    /** Daylight across 24 hours, midnight at both ends and noon in the middle, by the forest's own clock. */
    private void dayStrip(int width) {
        String mode = (String) values.get("time_of_day");
        String key = mode + width;
        if (key.equals(stripKey)) return;
        stripKey = key;
        stripDaylight = new double[width];
        stripWarmth = new double[width];
        long saved = Clock.fixedMillis;
        Calendar c = Clock.date(2000, 1, 1);
        long midnight = c.getTimeInMillis();
        for (int k = 0; k < width; k++) {
            Clock.fixedMillis = midnight + (int) (k / (double) width * 1440) * 60000L;
            double[] sky = World.skyState(mode);
            stripDaylight[k] = sky[0];
            stripWarmth[k] = sky[1];
        }
        Clock.fixedMillis = saved;
    }

    /** A real day: the strip is the sky over 24 hours, the line above it the waking hours, the pointer is now. */
    private void hoursGauge(int x, int y, G g, boolean hibernating) {
        int width = 66;
        dayStrip(width);
        Calendar clock = Clock.now();
        String mode = (String) values.get("time_of_day");
        boolean pinned = !mode.equals("auto");
        boolean awake = !hibernating && Math.abs(g.d("active") - Evolution.awakePosition(World.skyState(mode)[0])) < 0.5;
        s.text(x, y, awake ? "AWAKE" : "ASLEEP", awake ? AWAKE_MARK : DIM);
        int bx = x + 38;
        s.rect(bx - 1, y + 1, width + 2, 5, CANOPY.at(0));
        for (int k = 0; k < width; k++) {
            double daylight = stripDaylight[k], warmth = stripWarmth[k];
            int color;
            if (daylight < 0.03) color = warmth < 0.2 ? SKY.at(3) : SKY.at(5);
            else if (daylight < 0.12 || warmth > 0.45) color = daylight < 0.3 ? EYE.at(0) : EYE.at(1);
            else if (daylight < 0.4) color = EYE.at(1);
            else color = EYE.at(2);
            s.rect(bx + k, y + 3, 1, 3, color);
            if (!hibernating && Math.abs(g.d("active") - Evolution.awakePosition(daylight)) < 0.5) s.rect(bx + k, y, 1, 2, AWAKE_MARK);
        }
        if (!pinned) { // a pinned sky has no "now" to point at
            int px = bx + (int) ((Clock.hour(clock) + Clock.minute(clock) / 60.0) / 24.0 * width);
            s.pixel(px, y + 6, INK);
            s.rect(px - 1, y + 7, 3, 1, INK);
        }
    }

    /** Population as a climbing vine: it grows rightward from its root as the hours pass. */
    private void vine(int x, int y, int w, int h, List<Object> hist) {
        s.setClip(x, y, w, h);
        s.dither(x, y + h - 2, w, 2, LITTER.at(1), LITTER.at(0));
        int ground = y + h - 3;
        if (hist.size() < 2) { // just planted
            s.rect(x + 2, ground - 2, 1, 3, LEAF.at(5));
            s.pixel(x + 1, ground - 2, LEAF.at(6));
            s.pixel(x + 3, ground - 3, LEAF.at(6));
            s.text(x + 8, y + h - 11, "SPROUTING", FAINT);
            s.setClip();
            return;
        }
        int top = 1;
        for (Object v : hist) top = Math.max(top, Json.integer(v));
        int room = h - 7;
        double spacing = (w - 5) / 59.0;
        int prevX = x + 1, prevY = ground - Json.integer(hist.get(0)) * room / top;
        s.rect(prevX, prevY, 1, ground - prevY + 1, TRUNK.at(4)); // the stem rising from its root
        int col = 0;
        for (int k = 1; k < hist.size(); k++) {
            int value = Json.integer(hist.get(k));
            int nx = x + 1 + (int) (k * spacing), ny = ground - value * room / top;
            // Fresh green while thriving, olive as numbers fall, dead brown at nothing.
            int color = value == 0 ? LITTER.at(3) : value < Json.integer(hist.get(k - 1)) ? OLIVE.at(4) : LEAF.at(5);
            for (int px = prevX + 1; px <= nx; px++) {
                double t = (px - prevX) / (double) Math.max(1, nx - prevX);
                int py = (int) (prevY + (ny - prevY) * t);
                int last = (int) (prevY + (ny - prevY) * (px - 1 - prevX) / (double) Math.max(1, nx - prevX));
                s.rect(px, Math.min(py, last), 1, Math.abs(py - last) + 2, color); // two pixels thick, so it reads as a stem
                col++;
                if (value == 0) continue;
                if (col % 6 == 0) { // leaves, one side then the other
                    if (col % 12 == 0) {
                        s.pixel(px + 1, py - 1, LEAF.at(6));
                        s.pixel(px + 2, py - 1, LEAF.at(6));
                    } else {
                        s.pixel(px + 1, py + 1, LEAF.at(6));
                        s.pixel(px + 2, py + 2, LEAF.at(6));
                    }
                } else if (col % 17 == 0) { // now and then a curling tendril
                    s.pixel(px, py - 1, LEAF.at(4));
                    s.pixel(px + 1, py - 2, LEAF.at(4));
                    s.pixel(px + 2, py - 2, LEAF.at(4));
                    s.pixel(px + 2, py - 1, LEAF.at(4));
                }
            }
            prevX = nx;
            prevY = ny;
        }
        // At the growing tip, today's count in flower. A dead tip stays brown.
        if (Json.integer(hist.get(hist.size() - 1)) != 0) {
            s.rect(prevX, prevY - 1, 3, 3, FLOWER[3].at(1));
            s.pixel(prevX + 1, prevY, frame % 8 < 5 ? EYE.at(2) : EYE.at(1));
        } else s.rect(prevX, prevY - 1, 2, 2, LITTER.at(2));
        s.setClip();
    }

    // ---- plants ------------------------------------------------------------

    private void drawPlant(String kind, Eco.Plant sample, int x, int y, int w, int h) {
        String key = kind + sample.seed;
        List<int[]> pix = spriteCache.get(key);
        if (pix == null) spriteCache.put(key, pix = Sprites.build(kind, sample.g, sample.seed, 1.15));
        if (pix.isEmpty()) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] p : pix) {
            x0 = Math.min(x0, p[0]); x1 = Math.max(x1, p[0]);
            y0 = Math.min(y0, p[1]); y1 = Math.max(y1, p[1]);
        }
        int pw = x1 - x0 + 1, ph = y1 - y0 + 1;
        int shrink = Math.max(1, Math.max((int) Math.ceil(pw / (double) w), (int) Math.ceil(ph / (double) h)));
        int grow = Math.max(1, Math.min(2, Math.min(w / pw, h / ph)));
        int ox = x + Clock.div(w - pw * grow / shrink, 2), oy = y + h - ph * grow / shrink;
        for (int[] p : pix) {
            int px = ox + (p[0] - x0) * grow / shrink, py = oy + (p[1] - y0) * grow / shrink;
            if (grow == 1) s.pixel(px, py, p[2]);
            else s.rect(px, py, grow, grow, p[2]);
        }
    }

    private void drawPlants() {
        s.panel(4, BODY_Y, 118, BODY_H);
        int tw = 36, th = 42;
        for (int k = 0; k < PLANT_ORDER.length; k++) {
            int x = 8 + (k % 3) * (tw + 1), y = BODY_Y + 4 + (k / 3) * (th + 2);
            String kind = PLANT_ORDER[k];
            FloraRow row = flora.get(kind);
            int n = row == null ? 0 : row.n;
            tile(x, y, tw, th, k == plant && !tabFocus, n > 0);
            if (row != null && !row.samples.isEmpty()) drawPlant(kind, row.samples.get(0), x + 2, y + 1, tw - 4, th - 5);
            s.text(x + tw - textWidth(Integer.toString(n)) - 1, y + 1, Integer.toString(n), n != 0 ? GOOD : FAINT);
        }
        String kind = PLANT_ORDER[plant];
        plantPage(kind, flora.get(kind));
        footer(tabFocus ? "LEFT/RIGHT PAGES   DOWN TO ENTER   BACK TO THE FOREST" : "ARROWS BROWSE   UP TOP FOR PAGES   BACK TO THE FOREST");
    }

    private void plantPage(String kind, FloraRow row) {
        if (row == null) row = new FloraRow();
        int x0 = 124;
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H);
        int x = x0 + 6, y = BODY_Y + 5, n = row.n;
        String title = PLANT_TITLE.get(kind);
        s.text(x, y, title != null ? title : kind.toUpperCase(Locale.US), n != 0 ? GOOD : FAINT, -1, 2);
        y += 18;
        int pw = 70, ph = 56;
        s.rect(x, y, pw, ph, CANOPY.at(0));
        s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY.at(5), SKY.at(4));
        s.rect(x + 1, y + ph - 6, pw - 2, 5, TEAL.at(3));
        // Leaf through the real specimens.
        if (!row.samples.isEmpty()) drawPlant(kind, row.samples.get((frame / 24) % row.samples.size()), x + 2, y + 2, pw - 4, ph - 5);
        else s.text(x + 8, y + 22, "NONE LEFT", FAINT);
        int tx = x + pw + 5;
        s.text(tx, y, n + " GROWING", INK);
        s.text(tx, y + 9, row.mature + " MATURE", DIM);
        if (row.wilting != 0) s.text(tx, y + 18, row.wilting + " WILTING", WARM);
        s.text(tx, y + 27, "GENERATION " + row.gen, DIM);
        double hours = Eco.LIFESPAN.get(kind) / 7200.0;
        String seasonal = Eco.SEASONAL_PLANTS.get(kind);
        if (seasonal != null) s.text(tx, y + 36, "IN " + seasonal.toUpperCase(Locale.US), seasonColor(seasonal));
        else s.text(tx, y + 36, hours >= 1 ? String.format(Locale.US, "LIVES ~%.1fH", hours) : String.format(Locale.US, "LIVES ~%.0fM", hours * 60), DIM);
        Map<String, Double> companions = Eco.COMPANIONS.get(kind);
        if (companions != null && n != 0) s.text(tx, y + 45, row.partnered + "/" + n + " HAVE FUNGI", row.partnered != 0 ? TEAL.at(6) : FAINT);
        y += ph + 4;
        if (!row.means.isEmpty() && !kind.equals("mushroom") && !kind.equals("log")) {
            y = bars(x, y, PLANT_TRAITS, row.means);
            if ((kind.equals("spire") || kind.equals("clover") || kind.equals("shrub")) && !row.hues.isEmpty()) {
                s.text(x, y, "FLOWERS", DIM);
                int k = 0;
                for (int hue : row.hues) {
                    if (k >= 8) break;
                    s.rect(x + 46 + k * 7, y, 6, 7, FLOWER[hue % 8].at(1));
                    k++;
                }
                y += GLYPH_H;
            }
            for (String line : limit(wrap(PLANT_NOTE.get(kind), 30), 2)) {
                s.text(x, y + 1, line, INK);
                y += GLYPH_H;
            }
        } else {
            if (kind.equals("mushroom") && !row.hues.isEmpty()) {
                s.text(x, y, "CAPS", DIM);
                int k = 0;
                for (int hue : row.hues) {
                    if (k >= 6) break;
                    s.rect(x + 34 + k * 7, y, 6, 7, CAP[hue % 6].at(2));
                    k++;
                }
                y += GLYPH_H + 2;
            }
            for (String line : limit(wrap(PLANT_NOTE.get(kind), 30), 4)) {
                s.text(x, y, line, INK);
                y += GLYPH_H;
            }
        }
        if (companions != null) {
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(companions.entrySet());
            Collections.sort(sorted, new Comparator<Map.Entry<String, Double>>() {
                @Override public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                    int c = Double.compare(b.getValue(), a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                }
            });
            StringBuilder good = new StringBuilder();
            int count = 0;
            for (Map.Entry<String, Double> e : sorted) {
                if (e.getValue() > 0 && count < 3) {
                    good.append(count > 0 ? ", " : "").append(e.getKey().toUpperCase(Locale.US));
                    count++;
                }
            }
            if (count > 0) s.text(x, BODY_Y + BODY_H - 13, cut("LIKES " + good, 31), DIM);
        }
    }

    // ---- forest ------------------------------------------------------------

    /** The real ground, one pixel for every three: moss, threads and plants as they stand now. */
    private void buildFieldMap() {
        Scene sc = world.scene;
        Eco eco = world.eco;
        int mw = sc.w / 3, mh = (sc.h - sc.gy) / 3;
        int[] out = new int[mw * mh];
        for (int y = 0; y < mh; y++) {
            for (int x = 0; x < mw; x++) {
                int i = (sc.gy + y * 3 + 1) * sc.w + x * 3 + 1;
                out[y * mw + x] = sc.water[i] != 0 ? WATER.at(3) : sc.base[i];
            }
        }
        for (int c = 0; c < eco.cells.length; c++) {
            int sid = eco.cells[c];
            if (sid != 0 && eco.strains.get(sid) != null) {
                int cy = c / eco.mc, cx = c % eco.mc;
                int k = (cy * 2 / 3) * mw + cx * 2 / 3;
                if (0 <= k && k < out.length) out[k] = MOSS[eco.strains.get(sid)[2]].at(2);
            }
        }
        for (int p = 0; p < eco.mycCid.length; p++) {
            int cid = eco.mycCid[p];
            if (cid != 0) {
                Eco.Colony col = eco.colonyById.get(cid);
                int k = ((p / sc.w - sc.gy) / 3) * mw + (p % sc.w) / 3;
                if (0 <= k && k < out.length) out[k] = MYC[col != null ? col.g.i("hue") : 0].at(1);
            }
        }
        for (Eco.Plant p : eco.plants) {
            int k = ((p.y - sc.gy) / 3) * mw + p.x / 3;
            if (0 <= k && k < out.length) {
                int v = LEAF.at(6);
                if (p.kind.equals("mushroom")) v = FLOWER[5].at(2);
                else if (p.kind.equals("sapling")) v = STAG.at(3);
                else if (p.kind.equals("log")) v = LITTER.at(4);
                else if (p.kind.equals("shrub")) v = FLOWER[4].at(1);
                out[k] = v;
            }
        }
        fieldMap = out;
        fieldW = mw;
        fieldH = mh;
    }

    private void drawForest() {
        Eco eco = world.eco;
        Climate climate = eco.climate;
        s.panel(4, BODY_Y, W - 8, BODY_H);
        int x = 10, y = BODY_Y + 5;
        s.text(x, y, "FIELD MAP", GOOD);
        int mw = 160, mh = 45;
        s.rect(x, y + 9, mw + 2, mh + 2, CANOPY.at(0));
        if (fieldMap != null) {
            for (int row = 0; row < Math.min(mh, fieldH); row++)
                System.arraycopy(fieldMap, row * fieldW, s.px, (y + 10 + row) * W + x + 1, Math.min(mw, fieldW));
        }
        int keyY = y + 58;
        Object[][] key = {{"MOSS", MOSS[0].at(2), 0}, {"THREADS", MYC[0].at(1), 34}, {"PLANTS", LEAF.at(6), 86}, {"TREES", STAG.at(3), 128}};
        for (Object[] k : key) {
            s.rect(x + (Integer) k[2], keyY + 1, 4, 5, (Integer) k[1]);
            s.text(x + (Integer) k[2] + 6, keyY, (String) k[0], FAINT);
        }
        // Soil.
        int sx = x + mw + 8;
        s.text(sx, y, "SOIL", GOOD);
        int yy = y + 10;
        double nutrients = 0, moisture = 0, shade = 0;
        for (int k = 0; k < eco.nut.length; k++) {
            nutrients += eco.nut[k];
            moisture += eco.wet[k];
            shade += eco.shade[k];
        }
        int cells = eco.nut.length;
        double mossLevel = eco.mossCount / (double) Math.max(1, (int) (eco.mc * eco.mr * 0.17 * eco.cfg.density));
        Object[][] soil = {{"FOOD", nutrients / cells, LITTER.at(4)}, {"WATER", moisture / cells, WATER.at(4)}, {"SHADE", shade / cells, VIOLET.at(5)}, {"MOSS", mossLevel, MOSS[0].at(3)}};
        for (Object[] row : soil) {
            s.text(sx, yy, (String) row[0], DIM);
            s.meter(sx + 36, yy + 1, 16, Math.min(1.0, (Double) row[1]), (Integer) row[2]);
            yy += GLYPH_H;
        }
        if (climate.snow > 0.02 || climate.ice > 0.02) {
            yy -= GLYPH_H; // moss is under the snow anyway, so the snow takes its row
            s.rect(sx, yy, W - 12 - sx, GLYPH_H - 1, SKY.at(1));
            s.text(sx, yy, "SNOW", DIM);
            s.meter(sx + 36, yy + 1, 8, Math.min(1.0, climate.snow), STAG.at(2));
            s.text(sx + 70, yy, "ICE", DIM);
            s.meter(sx + 90, yy + 1, 6, Math.min(1.0, climate.ice), WATER.at(5));
            yy += GLYPH_H;
        }
        String word = climate.storm != 0 ? "THUNDERSTORM" : climate.snowing != 0 ? "SNOWING" : eco.raining != 0 ? "RAINING" : climate.aurora != 0 ? "AURORA!" : climate.fog != 0 ? "FOG" : "CLEAR";
        s.text(sx, yy + 1, word, word.equals("CLEAR") ? DIM : TEAL.at(6));
        String temp = String.format(Locale.US, "%.0fC", climate.temp);
        s.text(W - 12 - textWidth(temp), yy + 1, temp, DIM);
        yy += 11;
        s.text(sx, yy, "FLIES", DIM);
        int fx = sx + 36;
        int[] hues = new int[Eco.FLY_RAMPS.length];
        for (Eco.Fly f : eco.flies) hues[f.hue]++;
        for (int k = 0; k < hues.length; k++) {
            if (hues[k] != 0) {
                s.rect(fx, yy + 1, Math.max(1, hues[k] / 2), 5, Eco.FLY_RAMPS[k].at(3));
                fx += Math.max(1, hues[k] / 2) + 1;
            }
        }
        // Moss rules: the evolving game of life.
        int y2 = y + 70;
        s.text(x, y2, "RULES OF THE MOSS", GOOD);
        List<int[]> rules = new ArrayList<>();
        for (int sid = 1; sid < eco.strains.size(); sid++) if (eco.mossHist[sid] > 0 && eco.strains.get(sid) != null) rules.add(new int[]{eco.mossHist[sid], sid});
        Collections.sort(rules, new Comparator<int[]>() {
            @Override public int compare(int[] a, int[] b) { return a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]; }
        });
        int top = rules.isEmpty() ? 1 : rules.get(0)[0];
        yy = y2 + 10;
        for (int k = 0; k < Math.min(5, rules.size()); k++) {
            int[] strain = eco.strains.get(rules.get(k)[1]);
            Ramp ramp = MOSS[strain[2]];
            s.rect(x, yy + 1, 5, 5, ramp.at(3));
            s.text(x + 7, yy, cut(Eco.ruleText(strain[0], strain[1]), 16), k == 0 ? INK : DIM);
            s.meter(x + 106, yy + 1, 12, rules.get(k)[0] / (double) top, ramp.at(3));
            yy += GLYPH_H;
        }
        // Fungal colonies.
        int cx = x + 162;
        s.text(cx, y2, "FUNGAL COLONIES", GOOD);
        List<Eco.Colony> fungi = new ArrayList<>(eco.colonies);
        Collections.sort(fungi, new Comparator<Eco.Colony>() {
            @Override public int compare(Eco.Colony a, Eco.Colony b) { return (b.nodes.size - b.head) - (a.nodes.size - a.head); }
        });
        top = fungi.isEmpty() ? 1 : Math.max(1, fungi.get(0).nodes.size - fungi.get(0).head);
        yy = y2 + 10;
        for (int k = 0; k < Math.min(5, fungi.size()); k++) {
            Eco.Colony col = fungi.get(k);
            Ramp ramp = MYC[col.g.i("hue")];
            s.rect(cx, yy + 1, 5, 5, ramp.at(3));
            s.meter(cx + 8, yy + 1, 14, (col.nodes.size - col.head) / (double) top, ramp.at(2));
            if (col.g.d("parasite", 0.0) > 0.3) s.text(cx + 68, yy, "PARASITE", BAD);
            yy += GLYPH_H;
        }
        if (fungi.isEmpty()) s.text(cx, yy, "None yet", FAINT);
        // Tally.
        int y3 = y2 + 58;
        s.text(x, y3, "SO FAR", GOOD);
        Object[][] tally = {{"BORN", "births", FIREFLY.at(3)}, {"DIED", "deaths", FLOWER[4].at(2)}, {"FRUITED", "fruitings", FLOWER[5].at(2)},
            {"POLLEN", "pollinations", FLOWER[3].at(2)}, {"RAINS", "rains", MYC[0].at(3)}, {"STAG", "stag_visits", STAG.at(3)}};
        for (int k = 0; k < tally.length; k++) {
            int tx = x + (k % 3) * 50, ty = y3 + 9 + (k / 3) * 19;
            Object v = eco.stats.get(tally[k][1]);
            s.text(tx, ty, (String) tally[k][0], DIM);
            s.text(tx, ty + 9, Integer.toString(v == null ? 0 : Json.integer(v)), (Integer) tally[k][2]);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> sightings = (Map<String, Object>) eco.stats.get("sightings");
        List<Map.Entry<String, Object>> seen = new ArrayList<>(sightings.entrySet());
        Collections.sort(seen, new Comparator<Map.Entry<String, Object>>() {
            @Override public int compare(Map.Entry<String, Object> a, Map.Entry<String, Object> b) { return Double.compare(Json.num(b.getValue()), Json.num(a.getValue())); }
        });
        s.text(cx, y3, "MOST SEEN", GOOD);
        yy = y3 + 9;
        for (int k = 0; k < Math.min(4, seen.size()); k++) {
            String words = seen.get(k).getKey().replace(" taken by ", ", ").replace(" eaten by ", ", ").replace(" caught by ", ", ");
            s.text(cx, yy, cut(String.format(Locale.US, "%4d %s", Json.integer(seen.get(k).getValue()), words), 23), DIM);
            yy += GLYPH_H;
        }
        String near = eco.sky.near;
        footer(tabFocus ? "LEFT/RIGHT PAGES   BACK TO THE FOREST" : near != null ? "THE MOON IS NEAR " + near.toUpperCase(Locale.US) + " TONIGHT" : "YOUR GROUND AS IT IS NOW");
    }

    // ---- oddities ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void drawOddities() {
        Object table = world.eco.stats.get("oddities");
        Map<String, Object> seen = table == null ? new HashMap<String, Object>() : (Map<String, Object>) table;
        List<String> names = new ArrayList<>(Oddities.CATALOGUE.keySet());
        int found = 0;
        for (String n : names) if (seen.containsKey(n)) found++;
        s.panel(4, BODY_Y, 118, BODY_H);
        int tw = 22, th = 29;
        for (int k = 0; k < names.size(); k++) {
            int x = 8 + (k % 5) * tw, y = BODY_Y + 4 + (k / 5) * (th + 1);
            String name = names.get(k);
            s.rect(x, y, tw - 1, th, k == oddIndex && !tabFocus ? GOOD : CANOPY.at(0));
            if (seen.containsKey(name)) {
                s.dither(x + 1, y + 1, tw - 3, th - 2, SKY.at(5), SKY.at(4));
                oddIcon(name, x + 1, y + 1, tw - 3, th - 2, 2);
            } else {
                s.dither(x + 1, y + 1, tw - 3, th - 2, CANOPY.at(1), CANOPY.at(0));
                s.text(x + 7, y + 10, "?", FAINT);
            }
        }
        String name = names.get(oddIndex);
        String[] info = Oddities.CATALOGUE.get(name);
        int x0 = 124;
        s.panel(x0, BODY_Y, W - x0 - 4, BODY_H);
        int x = x0 + 6, y = BODY_Y + 5;
        Map<String, Object> entry = (Map<String, Object>) seen.get(name);
        s.text(x, y, entry != null ? info[0] : "? ? ?", entry != null ? GOOD : FAINT, -1, info[0].length() > 15 ? 1 : 2);
        y += 18;
        int pw = W - x0 - 16, ph = 62;
        s.rect(x, y, pw, ph, CANOPY.at(0));
        if (entry != null) {
            s.dither(x + 1, y + 1, pw - 2, ph - 2, SKY.at(5), SKY.at(4));
            s.dither(x + 1, y + ph - 6, pw - 2, 5, TEAL.at(2), TEAL.at(3));
            oddIcon(name, x + 8, y + 2, pw - 16, ph - 7, 3);
        } else {
            s.dither(x + 1, y + 1, pw - 2, ph - 2, CANOPY.at(1), CANOPY.at(0));
            s.text(x + pw / 2 - 8, y + ph / 2 - 8, "?", FAINT, -1, 3);
        }
        y += ph + 6;
        if (entry != null) {
            for (String line : limit(wrap(info[2], 30), 4)) {
                s.text(x, y, line, INK);
                y += GLYPH_H;
            }
            y += 4;
            int n = Json.integer(entry.get("n"));
            s.text(x, y, "SEEN " + n + " TIME" + (n != 1 ? "S" : ""), DIM);
            s.text(x, y + 9, "FIRST " + new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date((long) (Json.num(entry.get("first")) * 1000))).toUpperCase(Locale.US), DIM);
        } else {
            s.text(x, y, "NOT YET SEEN", BAD);
            y += GLYPH_H + 4;
            for (String line : limit(wrap(info[1], 30), 3)) {
                s.text(x, y, line, WARM);
                y += GLYPH_H;
            }
        }
        footer(tabFocus ? "LEFT/RIGHT PAGES   DOWN TO ENTER   BACK TO THE FOREST" : "FOUND " + found + " OF " + names.size() + "   ARROWS BROWSE   UP TOP FOR PAGES");
    }

    private void oddIcon(String name, int x, int y, int w, int h, int big) {
        int[] ink = Oddities.colors('#', CANOPY.at(0), '=', CANOPY.at(2), 'o', EYE.at(1), 'R', FLOWER[4].at(2), 'E', FLOWER[4].at(1), 'O', CANOPY.at(0));
        String[] sprite = Oddities.CATALOGUE_SPRITES.get(name);
        if (sprite != null) {
            ink['w'] = HARE.at(3);
            ink['+'] = CANOPY.at(2);
            if (name.equals("woodpecker")) {
                ink['R'] = FLOWER[4].at(2);
                ink['o'] = HARE.at(2);
            } else if (name.equals("treecreeper") || name.equals("pine_marten")) {
                ink['#'] = LITTER.at(3);
                ink['+'] = LITTER.at(2);
            }
            if (name.equals("kodama") || name.equals("ermine")) ink['#'] = STAG.at(2);
            else if (name.equals("dragonfly")) ink['#'] = MYC[2].at(2);
            else if (name.equals("ducks")) ink['#'] = LITTER.at(3);
            fitRows(sprite, x, y, w, h, ink, big);
            return;
        }
        switch (name) {
            case "jackalope": {
                G g = defaultGenes().with("ornament", 0.95).with("coat", 0.36).with("size", 0.4);
                fitRows(Sprites.animalRows("rabbit", "sit", g), x, y, w, h, creatureColors("rabbit", g), big);
                break;
            }
            case "ghost_stag": {
                G g = defaultGenes().with("coat", 0.57).with("size", 0.5).with("ornament", 0.5);
                fitRows(Sprites.animalRows("stag", "walk0", g), x, y, w, h, Oddities.colors('#', SKY.at(7), '+', SKY.at(6), 'w', SKY.at(7), 'o', SKY.at(7), 'O', SKY.at(6), '^', SKY.at(7)), big);
                break;
            }
            case "black_dog":
                s.rect(x + w / 2 - 4, y + h / 2, 2, 2, FLOWER[4].at(1));
                s.rect(x + w / 2 + 2, y + h / 2, 2, 2, FLOWER[4].at(1));
                break;
            case "big_tracks":
                for (int k = 0; k < 3; k++) {
                    int px = x + w / 4 + k * w / 4, py = y + h - 6 - k * (h / 4);
                    s.rect(px, py, 5, 3, CANOPY.at(0));
                    s.rect(px + 1, py - 1, 3, 1, CANOPY.at(0));
                }
                break;
            case "under_ice":
                s.rect(x + 1, y + 1, w - 2, h - 2, WATER.at(4));
                s.rect(x + w / 5, y + h / 2 - 1, w * 3 / 5, 3, WATER.at(0));
                break;
            case "aurora": {
                int[] bands = {MYC[2].at(2), MYC[2].at(3), MYC[1].at(2)};
                for (int k = 0; k < 3; k++) s.rect(x + 1, y + 2 + k * h / 4, w - 2, 2, bands[k]);
                break;
            }
            case "rainbow":
                for (int k = 0; k < 7; k++) s.rect(x + 1, y + 2 + k * 2, w - 2, 2, RAINBOW.at(k));
                break;
            case "fireworks": {
                int cx = x + w / 2, cy = y + h / 2;
                for (int k = 0; k < 8; k++) {
                    double a = k * 2 * Math.PI / 8;
                    s.pixel((int) (cx + Math.cos(a) * 5), (int) (cy + Math.sin(a) * 5), FLOWER[5].at(2));
                    s.pixel((int) (cx + Math.cos(a) * 2), (int) (cy + Math.sin(a) * 2), FLOWER[4].at(2));
                }
                break;
            }
            case "heart":
                fitRows(new String[]{".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."}, x, y, w, h, Oddities.colors('#', FLOWER[3].at(2)), big);
                break;
            case "eggs":
                fitRows(Oddities.EGG, x, y, w, h, Oddities.colors('#', FLOWER[0].at(1), 'w', FLOWER[5].at(2)), big);
                break;
            case "lucky_clover":
                fitRows(new String[]{".##.##.", "#######", ".#####.", "#######", ".##.##.", "...#..."}, x, y, w, h, Oddities.colors('#', FIREFLY.at(2)), big);
                break;
            case "square_moon":
                fitRows(new String[]{"######", "######", "######", "######", "######", "######"}, x, y, w, h, Oddities.colors('#', MOON.at(4)), big);
                break;
            default:
        }
    }

    // ---- journal -----------------------------------------------------------

    /** Day ribbons and entries with their pictures, laid out once per refresh. */
    private List<JournalRow> layoutJournal() {
        List<JournalRow> rows = new ArrayList<>();
        String day = null;
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE dd MMMM", Locale.US), timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
        List<Object> journal = world.eco.wild.evo.journal;
        for (int k = journal.size() - 1; k >= 0; k--) {
            List<?> line = (List<?>) journal.get(k);
            Date moment = new Date((long) (Json.num(line.get(0)) * 1000));
            String text = (String) line.get(2);
            String heading = dayFormat.format(moment).toUpperCase(Locale.US);
            if (!heading.equals(day)) {
                day = heading;
                JournalRow r = new JournalRow();
                r.day = true;
                r.heading = heading;
                rows.add(r);
            }
            JournalRow r = new JournalRow();
            r.when = String.format(Locale.US, "%8s", timeFormat.format(moment));
            r.lines = wrap(text, 37);
            r.icon = journalIcon(text);
            r.tone = journalTone(text);
            rows.add(r);
        }
        return rows;
    }

    private static String journalIcon(String text) {
        String low = text.toLowerCase(Locale.US);
        for (String[] w : ICON_WORDS) if (low.contains(w[0])) return w[1];
        return null;
    }

    private static boolean any(String low, String... words) {
        for (String w : words) if (low.contains(w)) return true;
        return false;
    }

    private static int journalTone(String text) {
        String low = text.toLowerCase(Locale.US);
        if (any(low, "sick", "last of", "crashed", "infect", "body of")) return BAD;
        if (any(low, "wandered", "returned", "found its way", "everywhere")) return GOOD;
        if (any(low, "glows", "antler", "two kinds", "as one", "ornament", "poison", "plume")) return WARM;
        return INK;
    }

    private void drawJournal() {
        s.panel(4, BODY_Y, W - 8, BODY_H);
        int top = BODY_Y + 4, view = BODY_H - 8, total = 0;
        for (JournalRow r : journalRows) total += r.height();
        maxScroll = Math.max(0, total - view);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        s.setClip(8, top, W - 22, view);
        int y = top - scroll;
        if (journalRows.isEmpty()) {
            s.text(14, top + 20, "Nothing written yet.", DIM);
            s.text(14, top + 30, "It takes a little while for", FAINT);
            s.text(14, top + 40, "a forest to have a history.", FAINT);
        }
        for (JournalRow r : journalRows) {
            int h = r.height();
            if (y + h >= top && y < top + view) {
                if (r.day) {
                    s.rect(8, y + 1, W - 22, 10, TEAL.at(1));
                    s.text(11, y + 2, r.heading, GOOD);
                } else {
                    s.rect(8, y + 1, 18, 14, SKY.at(4));
                    if (r.icon != null) {
                        Evolution.Entry e = species.get(r.icon);
                        creature(r.icon, e != null && !e.means.isEmpty() ? e.means : defaultGenes(), 9, y + 2, 16, 12, 1, false);
                    } else s.rect(14, y + 5, 5, 5, r.tone);
                    s.text(29, y + 1, r.when, DIM);
                    for (int k = 0; k < r.lines.size(); k++) s.text(29 + (k == 0 ? 52 : 0), y + 1 + k * GLYPH_H, r.lines.get(k), r.tone);
                }
            }
            y += h;
        }
        s.setClip();
        if (maxScroll != 0) {
            int barH = Math.max(10, view * view / total);
            s.rect(W - 13, top, 4, view, CANOPY.at(0));
            s.rect(W - 13, top + (view - barH) * scroll / maxScroll, 4, barH, FLECK.at(1));
        }
        footer(tabFocus ? "LEFT/RIGHT PAGES   DOWN TO READ   BACK TO THE FOREST" : "UP/DOWN TO READ   BACK TO THE FOREST");
    }

    // ---- the remote -------------------------------------------------------

    private void setTab(int k) {
        k = Clock.mod(k, TABS.length);
        if (k != tab) {
            tab = k;
            scroll = 0;
            individualFocus = false;
            refresh();
        }
    }

    private int gridMove(int value, int key, int columns, int count) {
        if (key == KEY_UP && value < columns) {
            tabFocus = true;
            return value;
        }
        int step = key == KEY_LEFT ? -1 : key == KEY_RIGHT ? 1 : key == KEY_UP ? -columns : key == KEY_DOWN ? columns : 0;
        return Clock.mod(value + step, count);
    }

    /** One press of the remote. */
    public void key(int key) {
        if (key == KEY_PREV_TAB || key == KEY_NEXT_TAB) {
            seedText = null;
            locationText = null;
            setTab(tab + (key == KEY_NEXT_TAB ? 1 : -1));
            return;
        }
        if (tabFocus) {
            if (key == KEY_LEFT || key == KEY_RIGHT) setTab(tab + (key == KEY_RIGHT ? 1 : -1));
            else if (key == KEY_DOWN || key == KEY_SELECT) tabFocus = false;
            else if (key == KEY_BACK) closed = true;
            return;
        }
        if (key == KEY_BACK && seedText == null && locationText == null && !individualFocus) {
            closed = true;
            return;
        }
        switch (tab) {
            case 0: keySettings(key); break;
            case 1: keyAnimals(key); break;
            case 2: plant = key == KEY_SELECT ? plant : gridMove(plant, key, 3, PLANT_ORDER.length); break;
            case 3: if (key == KEY_UP) tabFocus = true; break;
            case 4: oddIndex = key == KEY_SELECT ? oddIndex : gridMove(oddIndex, key, 5, Oddities.CATALOGUE.size()); break;
            default:
                if (key == KEY_UP) {
                    if (scroll <= 0) tabFocus = true;
                    else scroll -= 18;
                } else if (key == KEY_DOWN) scroll += 18;
                else if (key == KEY_LEFT) scroll -= 150;
                else if (key == KEY_RIGHT) scroll += 150;
        }
    }

    private void keyAnimals(int key) {
        String name = ANIMAL_ORDER[animal];
        Evolution.Entry row = species.get(name);
        if (individualFocus) {
            if (key == KEY_SELECT || key == KEY_BACK || key == KEY_UP) individualFocus = false;
            else if (key == KEY_LEFT || key == KEY_RIGHT) {
                Animal one = individual(name, row);
                if (one != null) choose(name, row, row.members.indexOf(one) + (key == KEY_RIGHT ? 1 : -1));
            }
            return;
        }
        if (key == KEY_SELECT) {
            if (row != null && !row.members.isEmpty()) individualFocus = true;
            return;
        }
        animal = gridMove(animal, key, 3, ANIMAL_ORDER.length);
    }
}
