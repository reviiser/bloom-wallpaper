package com.reviiser.bloom.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The 256-colour palette. Everything on screen is an index into this table, in the spirit of old
 * colour-cycling art, so moving the whole forest from night to dusk only rebuilds the table.
 */
public final class Palette {
    private Palette() {}

    public static final class Ramp {
        public final int start, n;
        Ramp(int start, int n) { this.start = start; this.n = n; }
        public int at(int level) {
            if (level < 0) level = 0; else if (level >= n) level = n - 1;
            return start + level;
        }
        public int at(double level) { return at((int) level); }
    }

    static final int K_SCENE = 0, K_SKY = 1, K_GLOW = 2, K_LEAF = 3, K_WATER = 4, K_MOON = 5, K_RAINBOW = 6, K_ANIMAL = 7;
    private static final int[] colors = new int[256];
    private static final int[] kinds = new int[256];
    private static final Ramp[] rampOf = new Ramp[256];
    private static int count = 1; // index 0 means "transparent" in every plane

    private static Ramp ramp(String list) { return ramp(list, K_SCENE); }
    private static Ramp ramp(String list, int kind) {
        String[] parts = list.split(" ");
        Ramp r = new Ramp(count, parts.length);
        for (String c : parts) {
            colors[count] = Integer.parseInt(c, 16);
            kinds[count] = kind;
            rampOf[count] = r;
            count++;
        }
        return r;
    }

    public static final Ramp SKY = ramp("090a22 0d0e30 12133f 17194f 1e205f 262872 30338a 3b3f9f", K_SKY);
    public static final Ramp FAR = ramp("05061a 080922 0c0e2c 101236 14173f 191c49");
    public static final Ramp CANOPY = ramp("02030b 040612 070a1a 0a0e22 0e142c 121a36");
    public static final Ramp TRUNK = ramp("040307 09060c 100a12 18101a 221722 2e2029 3e2c33");
    public static final Ramp VIOLET = ramp("0a0920 100e2d 16133a 1d1848 251e57 2e2566");
    public static final Ramp TEAL = ramp("03101a 051a24 08262e 0c3338 124243 19524f 226560");
    public static final Ramp FLECK = ramp("2a3580 3f4fb5 5a70da 7d95f2 a4bcff", K_GLOW);
    public static final Ramp LEAF = ramp("03130f 061f1a 0a2c24 103b2f 184d3c 22614b 2e775c", K_LEAF);
    public static final Ramp OLIVE = ramp("0a120a 121e0e 1c2c14 283c1a 364e20 466226", K_LEAF);
    public static final Ramp STEM = ramp("08141a 0d2026 142e30 1c3e3c 264f48");
    public static final Ramp STONE = ramp("08091a 0f1024 171931 21243f 2d314f 3b4062");
    public static final Ramp LITTER = ramp("0c0810 170e12 241714 332016 442b1a");
    public static final Ramp LOG = ramp("060409 0d080c 160e10 211514 2d1d18 3b2720");
    public static final Ramp WATER = ramp("07182e 09203c 0b2849 0d3056 10396a 0d3056 0b2849 09203c", K_WATER);
    public static final Ramp MOON = ramp("3a3c9e 6a6cc8 a3a4e6 d6d7f8 f4f5ff", K_MOON);
    public static final Ramp STAR = ramp("3a3f9c 7a84e0 c6d0ff", K_MOON);
    public static final Ramp FIREFLY = ramp("12321c 2e7a30 66d24a a8f070 e4ffb8", K_GLOW);
    public static final Ramp STAG = ramp("5a7fb0 9cc0de cfe6f6 f4fbff", K_GLOW);
    public static final Ramp EYE = ramp("6a3a0a d89a28 ffe08a", K_GLOW);
    // Seven colours and two spare, holding the same nine palette slots the old beetle colours did.
    public static final Ramp RAINBOW = ramp("e8483c f08a2c f6d63c 5cc84c 3caae0 4a5ce0 9a58d8 000000 000000", K_RAINBOW);
    public static final Ramp MOTH = ramp("6a6890 a8a6c8 d8d6ee", K_GLOW);
    public static final Ramp SPORE = ramp("3a4a7a 7a8ac0 c0d0f0", K_GLOW);

    public static final Ramp[] MOSS = {
        ramp("06201e 0b312b 12443a 1c5a49"), // teal
        ramp("061c2a 0a2c3c 113f50 1b5566"), // blue-green
        ramp("10200e 1a3114 28461a 3a5e22"), // olive
        ramp("06220f 0b3418 134a22 1f642e"), // deep green
        ramp("161434 201d47 2c295d 3b3876"), // slate violet
        ramp("241a10 362614 4a3518 62481e"), // rust
    };
    public static final Ramp[] MYC = {
        ramp("10183c 1e3280 3a5cc4 6c96ec b4d4ff", K_GLOW), // ice blue
        ramp("181040 2e2080 5238bc 8a6cea c8b0ff", K_GLOW), // violet
        ramp("08242c 0e4450 1c7a84 36b8b2 90f2dc", K_GLOW), // teal
        ramp("2a1030 501e56 8a3880 c060a8 f0a8d0", K_GLOW), // rose
        ramp("2a1a0a 503010 8a5a1c d09430 f8d078", K_GLOW), // amber
        ramp("1e2434 384460 687892 a4b4cc e4eef8", K_GLOW), // pale
    };
    public static final Ramp[] CAP = {
        ramp("1e3488 3a62d0 6c96f4 b4d0ff", K_GLOW), // blue
        ramp("0e5060 1e9098 48d4c8 a8f8e4", K_GLOW), // cyan
        ramp("3c2288 6c44c8 a078ec d4bcff", K_GLOW), // violet
        ramp("60380c a86818 e0a038 fce090", K_GLOW), // amber
        ramp("5a1418 98282a c85040 e89070"), // red
        ramp("4a4634 80785c b8ae8c e8e0c4"), // pale tan
    };
    public static final Ramp[] FLOWER = {
        ramp("2a48a8 5a84e8 a0c4ff", K_GLOW), // bluebell
        ramp("4438b0 7a68e4 b4a8ff", K_GLOW), // periwinkle
        ramp("6a2c98 a258d0 d49cf0"), // violet
        ramp("8a2a60 c4548e ec98bc"), // rose
        ramp("8c2428 c44438 ec7c5c"), // red
        ramp("9a6a14 d8a430 f4d878"), // amber
        ramp("8a8c9c c8cad8 f4f4fc"), // cream
        ramp("1c7a78 3cb4a8 8ce8d4", K_GLOW), // teal
    };

    // Animals are appended here, after everything the cached scenery refers to.
    public static final Ramp FOX = ramp("3a1a0c 7a3414 b85c20 d8d0c0", K_ANIMAL);
    public static final Ramp HARE = ramp("2a2428 5a4c4c 8e7e76 c8bcb0", K_ANIMAL);
    public static final Ramp FROG = ramp("0a2010 1c5024 3a8434 9ccc58", K_ANIMAL);
    public static final Ramp BIRD = ramp("20244c 4c6aa8 d89a3c ece0c8", K_ANIMAL);
    public static final Ramp OWL = ramp("1a1410 46362a 8a7254", K_ANIMAL);
    public static final Ramp BAT = ramp("07061a 14102c 221c40", K_ANIMAL);
    public static final Ramp SNOW = ramp("20284a 343f66 465480");
    // Green coats, kept apart from the leaf colours so autumn can't turn an animal brown.
    public static final Ramp COAT_GREEN = ramp("0c1e14 184d3c 2e775c 283c1a 466226", K_ANIMAL);

    /** An entry's colour as painted, before any time of day or darkness. */
    public static int painted(int index) { return colors[index]; }

    public static double luminance(int index) {
        int c = colors[index];
        return 0.3 * (c >> 16 & 255) + 0.59 * (c >> 8 & 255) + 0.11 * (c & 255);
    }

    private static int[] shiftLut(int steps) {
        int[] lut = new int[256];
        for (int i = 0; i < 256; i++) {
            Ramp r = rampOf[i];
            lut[i] = r != null ? r.at(i - r.start + steps) : i;
        }
        return lut;
    }

    public static final int[] LIT = shiftLut(1);   // one step brighter inside the same ramp: shafts of light
    public static final int[] LIT2 = shiftLut(2);  // two steps: firefly halos
    public static final int[] DARK = shiftLut(-1); // one step darker: shadows

    // Natural daylight, ramp by ramp: multipliers of a colour's brightness, plus a little ambient light.
    // Ramps not listed simply keep their own painted colours, brightened.
    private static final double[][] NATURAL_DAY = new double[256][];
    static {
        Object[][] looks = {
            {SKY, new double[]{1.05, 1.35, 1.6, 16, 34, 62}},
            {FAR, new double[]{1.0, 1.25, 1.2, 10, 16, 18}},
            {CANOPY, new double[]{0.8, 1.35, 0.7, 2, 6, 2}},
            {VIOLET, new double[]{1.3, 1.05, 0.6, 4, 3, 0}},
            {TEAL, new double[]{0.8, 1.45, 0.6, 2, 6, 1}},
            {TRUNK, new double[]{1.5, 1.1, 0.75, 6, 3, 1}},
            {STONE, new double[]{1.05, 1.05, 1.1, 4, 4, 5}},
            {SNOW, new double[]{1.45, 1.5, 1.6, 8, 8, 10}},
        };
        for (Object[] look : looks) {
            Ramp r = (Ramp) look[0];
            for (int i = r.start; i < r.start + r.n; i++) NATURAL_DAY[i] = (double[]) look[1];
        }
    }

    private static final HashMap<String, double[]> THEME_GAINS = new HashMap<>();
    private static final HashMap<String, double[]> MONO_TINTS = new HashMap<>();
    static {
        THEME_GAINS.put("deepwoods", new double[]{1.00, 1.00, 1.00});
        THEME_GAINS.put("teal", new double[]{0.55, 1.08, 1.02});
        THEME_GAINS.put("moonlit", new double[]{0.55, 0.85, 1.40});
        THEME_GAINS.put("amber", new double[]{1.25, 0.95, 0.32});
        THEME_GAINS.put("violet", new double[]{1.15, 0.72, 1.22});
        MONO_TINTS.put("deepwoods", new double[]{0.70, 0.95, 1.75});
        MONO_TINTS.put("teal", new double[]{0.45, 1.10, 1.30});
        MONO_TINTS.put("moonlit", new double[]{0.60, 0.90, 1.70});
        MONO_TINTS.put("amber", new double[]{1.55, 0.95, 0.30});
        MONO_TINTS.put("violet", new double[]{1.35, 0.65, 1.70});
    }

    /** The season and weather side of the look. */
    public static final class Look {
        public double autumn, winter, spring, ice;
    }

    /** Returns 0xffRRGGBB for all 256 entries under the given look, season and weather. */
    public static int[] compute(String theme, double darkness, double glow, double saturation, double daylight, double warmth,
                                int waterPhase, Look look, double aurora, double auroraPhase, double flash, double natural) {
        double autumn = look == null ? 0 : look.autumn, winter = look == null ? 0 : look.winter;
        double spring = look == null ? 0 : look.spring, ice = look == null ? 0 : look.ice;
        double[] gains = THEME_GAINS.get(theme);
        if (gains == null) gains = THEME_GAINS.get("deepwoods");
        // The more natural, the less a palette's tint takes over everything.
        double soften = 1.0 - 0.75 * natural;
        double gr = 1 + (gains[0] - 1) * soften, gg = 1 + (gains[1] - 1) * soften, gb = 1 + (gains[2] - 1) * soften;
        double sceneGain = Math.pow(1.9, 1.0 - 2.0 * darkness); // darkness 0.5 = as painted
        double glowGain = (0.45 + 0.75 * glow) * Math.pow(1.35, 1.0 - 2.0 * darkness);
        int skyMid = colors[SKY.at(6)];
        double smr = skyMid >> 16 & 255, smg = skyMid >> 8 & 255, smb = skyMid & 255;
        // A palette's signature colour, scaled so that tinting by it leaves brightness unchanged.
        double[] tint0 = MONO_TINTS.get(theme);
        if (tint0 == null) tint0 = MONO_TINTS.get("deepwoods");
        double weight = 0.3 * tint0[0] + 0.59 * tint0[1] + 0.11 * tint0[2];
        double tr = tint0[0] / weight, tg = tint0[1] / weight, tb = tint0[2] / weight;
        int[] out = new int[256];
        for (int i = 0; i < 256; i++) {
            int c = colors[i];
            double r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            int kind = kinds[i];
            double lum;
            if (kind == K_WATER) {
                int w = colors[WATER.start + (i - WATER.start + waterPhase) % WATER.n];
                r = w >> 16 & 255; g = w >> 8 & 255; b = w & 255;
                if (ice > 0) { // as the pond freezes it stills and pales
                    lum = 0.3 * r + 0.59 * g + 0.11 * b;
                    r += (lum * 1.5 + 22 - r) * ice;
                    g += (lum * 1.8 + 30 - g) * ice;
                    b += (lum * 2.2 + 44 - b) * ice;
                }
            } else if (kind == K_LEAF) {
                lum = 0.3 * r + 0.59 * g + 0.11 * b;
                if (autumn > 0) { // gold and russet; a little of each ramp keeps the forest from going flat
                    double warm = i % 3 != 0 ? 1.0 : 0.7;
                    r += (lum * 2.5 * warm + 8 - r) * autumn;
                    g += (lum * 1.15 * warm - g) * autumn;
                    b += (lum * 0.28 - b) * autumn;
                }
                if (winter > 0) { // bare and grey-brown
                    r += (lum * 0.95 - r) * winter;
                    g += (lum * 0.88 - g) * winter;
                    b += (lum * 0.8 - b) * winter;
                }
                if (spring > 0) { // the fresh green of new leaves
                    r += (r * 0.85 - r) * spring;
                    g += (g * 1.18 - g) * spring;
                    b += (b * 0.85 - b) * spring;
                }
            } else if (kind == K_SKY && aurora > 0) {
                // Slow curtains of green and violet over the winter sky.
                double sway = 0.5 + 0.5 * Math.sin(auroraPhase + (i - SKY.start) * 0.9);
                r += (sway * 26 + 16) * aurora;
                g += ((1 - sway) * 90 + 28) * aurora;
                b += (sway * 54 + 18) * aurora;
            }
            if (daylight > 0) {
                if (kind == K_MOON) {
                    // The moon and stars dissolve into the sky at sunrise.
                    double f = Math.min(1.0, daylight * 2.5);
                    r += (smr - r) * f; g += (smg - g) * f; b += (smb - b) * f;
                    kind = K_SKY;
                }
                lum = 0.3 * r + 0.59 * g + 0.11 * b;
                double dr, dg, db;
                if (kind == K_SKY) { // daylight is pale gold-green, filtered through leaves
                    dr = lum * 1.25 + 6; dg = lum * 1.55 + 10; db = lum * 0.7 + 3;
                } else { // under the deep canopy: greener, warmer, never bright
                    dr = lum * 1.08 + 3; dg = lum * 1.28 + 5; db = lum * 0.5 + 1;
                }
                if (natural > 0) {
                    // Natural light: each thing its own colour, rather than all of them one tint.
                    double[] nat = NATURAL_DAY[i];
                    double or, og, ob;
                    if (nat != null) {
                        or = lum * nat[0] + nat[3]; og = lum * nat[1] + nat[4]; ob = lum * nat[2] + nat[5];
                    } else {
                        or = r * 1.35; og = g * 1.35; ob = b * 1.35;
                    }
                    dr += (or - dr) * natural; dg += (og - dg) * natural; db += (ob - db) * natural;
                }
                if (kind == K_GLOW) { // luminous things only really show in the dark
                    double f = 1.0 - 0.45 * daylight;
                    r *= f; g *= f; b *= f;
                } else if (kind != K_ANIMAL && kind != K_RAINBOW) { // animals keep their own colours, or they would be camouflaged
                    double mix = Math.min(1.0, daylight * 1.3), boost = 1.0 + 0.3 * daylight;
                    r = (r + (dr - r) * mix) * boost;
                    g = (g + (dg - g) * mix) * boost;
                    b = (b + (db - b) * mix) * boost;
                }
            }
            if (warmth > 0) {
                r *= 1.0 + 0.45 * warmth;
                g *= 1.0 - 0.02 * warmth;
                b *= 1.0 - 0.32 * warmth;
            }
            double gain = (kinds[i] == K_GLOW || kinds[i] == K_MOON) && daylight < 0.3 ? glowGain : sceneGain;
            if (kind == K_RAINBOW) gain = 0.75 + 0.25 * sceneGain; // it is light, not scenery: the darkness setting barely touches it
            if (kind == K_WATER) gain = (sceneGain + glowGain) / 2 * 0.8;
            double tint = kind == K_RAINBOW ? 0.0 : kind == K_GLOW || kind == K_MOON ? 0.5 : 1.0;
            r *= gain * (1 + (gr - 1) * tint);
            g *= gain * (1 + (gg - 1) * tint);
            b *= gain * (1 + (gb - 1) * tint);
            lum = 0.3 * r + 0.59 * g + 0.11 * b;
            if (saturation >= 1.0) {
                r = lum + (r - lum) * saturation;
                g = lum + (g - lum) * saturation;
                b = lum + (b - lum) * saturation;
            } else {
                // Below full colour, fade toward one colour: this palette's own, at the same brightness.
                // The brightest things pale a little, so the moon still looks like light.
                double pale = Math.max(0.0, (lum - 150.0) / 105.0) * 0.6;
                double mr = lum * (tr + (1.0 - tr) * pale), mg = lum * (tg + (1.0 - tg) * pale), mb = lum * (tb + (1.0 - tb) * pale);
                r = mr + (r - mr) * saturation;
                g = mg + (g - mg) * saturation;
                b = mb + (b - mb) * saturation;
            }
            if (flash > 0) {
                r += (r * 2.4 + 34) * flash;
                g += (g * 2.4 + 38) * flash;
                b += (b * 2.4 + 52) * flash;
            }
            out[i] = 0xff000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
        }
        return out;
    }

    private static int clamp(double v) { int n = (int) v; return n < 0 ? 0 : n > 255 ? 255 : n; }
}
