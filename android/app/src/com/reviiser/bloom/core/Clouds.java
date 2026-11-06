package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.Arrays;

/**
 * Slow clouds, seen through the gaps in the leaves. They drift on their own plane, just above the sky: behind every
 * tree, but in front of the moon and the stars. A cloud is drawn as a shift along the sky's own colour ramp, so it is
 * lit by whatever lies behind it. Each step only touches rows whose pattern has actually changed.
 */
public final class Clouds {
    static final double SPEED = 0.12; // art pixels per tick: at the default pace, about half an hour to cross the screen
    static final int STEP_TICKS = 6, MOON_LEVEL = 6;
    static final double COVER_RATE = 0.002;

    final Eco eco;
    final PyRandom rng;
    double offset;
    public double cover = -1; // -1 until first worked out
    private byte[][] strip;
    private int[] quantiles;
    private int[][] rows; // for each row, pairs of x and sky level
    private boolean[][] seenRow, seenAbove;
    private boolean dayShown, dayKnown;
    private double drawnCover = -1;
    private int dayOrdinal = -1;
    private double dayValue;

    Clouds(Eco eco) {
        this.eco = eco;
        rng = new PyRandom(eco.s.seed ^ 0xC10D);
        offset = rng.uniform(0, 1000);
    }

    /** The cloud field, in 2-pixel blocks so that its edges stay chunky, and the sky pixels it can show on. */
    private void build() {
        Scene scene = eco.s;
        int w = scene.w, gy = scene.gy;
        Scene.Noise noise = new Scene.Noise(new PyRandom(scene.seed ^ 0xC10D5));
        int period = w * 2, blocksX = period / 2, blocksY = gy / 2 + 1;
        strip = new byte[blocksY][blocksX * 2];
        IntList sample = new IntList(blocksX * blocksY);
        for (int by = 0; by < blocksY; by++) {
            double y = by * 2.0;
            // High clouds sit in long bands, so the field is much wider than tall.
            for (int bx = 0; bx < blocksX; bx++) {
                double x = bx * 2.0;
                // Crossfading into itself is what lets the strip wrap with no seam.
                double blend = x / period;
                double a = noise.fbm(x / 44.0, y / 17.0) * 0.62 + noise.at(x / 11.0, y / 6.0) * 0.26 + noise.at(x / 4.5 + 31, y / 3.5) * 0.12;
                double b = noise.fbm((x - period) / 44.0, y / 17.0) * 0.62 + noise.at((x - period) / 11.0, y / 6.0) * 0.26 + noise.at((x - period) / 4.5 + 31, y / 3.5) * 0.12;
                int v = Math.min(255, (int) ((a * (1 - blend) + b * blend) * 255));
                strip[by][bx * 2] = (byte) v;
                strip[by][bx * 2 + 1] = (byte) v;
                if ((bx * 2) % 4 == 0) sample.add(v);
            }
        }
        int[] ordered = sample.toArray();
        Arrays.sort(ordered);
        quantiles = new int[101];
        for (int p = 0; p <= 100; p++) quantiles[p] = ordered[Math.min(ordered.length - 1, ordered.length * p / 100)];
        int mx = scene.moonX, my = scene.moonY, mr = scene.moonR;
        rows = new int[gy][];
        for (int y = 0; y < gy; y++) {
            IntList cells = new IntList();
            for (int x = 0; x < w; x++) {
                int v = scene.base[y * w + x];
                boolean sky = v >= SKY.start && v < SKY.start + SKY.n;
                boolean faint = v >= STAR.start && v < STAR.start + STAR.n;
                boolean lit = v >= MOON.start && v < MOON.start + MOON.n;
                int level = -1;
                if ((x - mx) * (x - mx) + (y - my) * (y - my) <= (mr + 1) * (mr + 1)) { // the whole disc, lit part or dark
                    if (sky || lit) level = MOON_LEVEL;
                } else if (sky) level = v - SKY.start;
                else if (faint) level = 2;
                else if (lit) level = MOON_LEVEL;
                if (level >= 0) {
                    cells.add(x);
                    cells.add(level);
                }
            }
            rows[y] = cells.toArray();
        }
        seenRow = new boolean[gy][];
        seenAbove = new boolean[gy][];
    }

    /** How much of the sky should be cloud right now, 0 to 1. */
    double targetCover() {
        Climate climate = eco.climate;
        if (!eco.cfg.weather) return 0.0;
        if (climate.storm != 0 || climate.snowing != 0 || eco.raining != 0) return 0.85;
        if (climate.aurora != 0) return 0.05;
        if (climate.fog != 0) return 0.6;
        // Otherwise each day has its own sky, fair more often than not.
        int today = Clock.ordinal(climate.now());
        if (today != dayOrdinal) {
            dayOrdinal = today;
            dayValue = new PyRandom(today * 17L + eco.s.seed).random();
        }
        return 0.08 + 0.5 * dayValue * dayValue;
    }

    void tick() {
        double target = targetCover();
        if (cover < 0) cover = target;
        else if (cover < target) cover = Math.min(target, cover + COVER_RATE / STEP_TICKS);
        else cover = Math.max(target, cover - COVER_RATE / STEP_TICKS);
        offset += SPEED;
        if (eco.tickNo % STEP_TICKS == 0) step();
    }

    public void step() {
        if (cover < 0) cover = targetCover();
        if (cover <= 0.001 && drawnCover <= 0.001) return;
        if (strip == null) build();
        Canvas canvas = eco.c;
        int w = eco.s.w, period = w * 2;
        boolean day = eco.daylight > 0.3;
        int percent = (int) (cover * 100);
        int limit = quantiles[100 - percent];
        if (!dayKnown || day != dayShown) {
            dayKnown = true;
            dayShown = day;
            Arrays.fill(seenRow, null);
            Arrays.fill(seenAbove, null);
        }
        int o = (int) offset % period;
        int[] plane = canvas.cloud;
        int topShade = day ? 3 : 1, bodyShade = day ? 2 : -2; // by day, bright; by night, dark with a moonlit edge
        boolean[] above = null;
        for (int y = 0; y < rows.length; y++) {
            byte[] band = strip[y / 2];
            boolean[] row = new boolean[w];
            if (percent != 0) {
                for (int x = 0; x < w; x++) {
                    int v = band[(o + x) % period] & 255;
                    row[x] = v > limit || percent >= 100;
                }
            }
            int[] cells = rows[y];
            if (cells.length > 0 && !(seenRow[y] != null && Arrays.equals(seenRow[y], row) && Arrays.equals(seenAbove[y], above))) {
                seenRow[y] = row;
                seenAbove[y] = above;
                int rowStart = y * w;
                for (int k = 0; k < cells.length; k += 2) {
                    int x = cells[k], level = cells[k + 1];
                    int i = rowStart + x, value;
                    if (!row[x]) value = day && level == MOON_LEVEL ? SKY.at(level) : 0; // by day the moon has dissolved into the sky: this hides the last trace of its disc
                    else value = SKY.at(level + (above != null && !above[x] ? topShade : bodyShade));
                    if (plane[i] != value) {
                        plane[i] = value;
                        canvas.refresh(i);
                    }
                }
            }
            above = row;
        }
        drawnCover = cover;
    }
}
