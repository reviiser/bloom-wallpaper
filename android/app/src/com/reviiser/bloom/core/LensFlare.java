package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.Calendar;

/**
 * The light itself: a sun that crosses the sky by your clock, and bloom and lens flare from it and the moon. With a full
 * palette there is no true blending, so "brighter" means a step up each colour's own ramp, dithered for softness.
 * It is worked out once every second or two and only reused between, so it stays cheap.
 */
public final class LensFlare {
    static final int RECOMPUTE_FRAMES = 12;
    // Distance along the axis (0 is the light, 1 the screen's centre), radius, and how solid.
    static final double[][] GHOSTS = {{0.48, 3, 0.75}, {0.82, 7, 0.34}, {1.24, 2, 0.9}, {1.55, 11, 0.24}, {1.95, 5, 0.42}, {2.35, 15, 0.14}};

    final Eco eco;
    private int[] noise;
    private final Overlay cached;
    public double[] sun;
    public double visible;
    private int tint0 = STAR.at(0), tint1 = STAR.at(1);

    LensFlare(Eco eco) {
        this.eco = eco;
        cached = new Overlay(eco.w * eco.h);
    }

    /** Across the sky from morning to evening, arcing over the top. Null when it's down. */
    public double[] sunPosition() {
        if (eco.daylight < 0.03) return null;
        String mode = eco.cfg.timeOfDay;
        double t;
        if (mode.equals("day")) t = 0.5;
        else if (mode.equals("dusk")) t = 0.93;
        else {
            Calendar now = eco.climate.now();
            t = (Clock.hour(now) + Clock.minute(now) / 60.0 - 6.0) / 14.0;
            t = Math.max(0.0, Math.min(1.0, t));
        }
        if (eco.cfg.hemisphere.equals("south")) t = 1.0 - t;
        return new double[]{(int) (eco.w * (0.04 + 0.92 * t)), (int) (eco.gy * (0.92 - 0.72 * Math.sin(Math.PI * t)))};
    }

    /** The disc, only where sky shows through. Returns how much of it could be seen, 0 to 1. */
    private double sunPixels(int x, int y) {
        Canvas c = eco.c;
        int w = eco.w, radius = Math.max(5, (int) (8 * eco.scaleS));
        double seen = 0;
        int total = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int d = dx * dx + dy * dy;
                if (d > radius * radius) continue;
                total++;
                int px = x + dx, py = y + dy;
                if (!(0 <= px && px < w && 0 <= py && py < eco.gy)) continue;
                int i = py * w + px;
                int v = c.comp[i];
                if (c.fg[i] != 0 || c.life[i] != 0 || v < SKY.start || v >= SKY.start + SKY.n) continue;
                if (c.cloud[i] != 0) {
                    seen += 0.15; // a cloud lets a little through
                    continue;
                }
                seen += 1;
                cached.put(i, d < radius * radius * 0.4 ? STAG.at(3) : EYE.at(2));
            }
        }
        return seen / Math.max(1, total);
    }

    private double moonVisible() {
        Canvas c = eco.c;
        int w = eco.w, mx = eco.s.moonX, my = eco.s.moonY, mr = eco.s.moonR;
        int seen = 0, total = 0;
        for (int dy = -mr; dy <= mr; dy += 2) {
            for (int dx = -mr; dx <= mr; dx += 2) {
                if (dx * dx + dy * dy <= mr * mr) {
                    int i = (my + dy) * w + mx + dx;
                    // Only the lit part counts: a crescent isn't "blocked" by its own dark side.
                    if (0 <= i && i < w * eco.h && c.base[i] >= MOON.start && c.base[i] < MOON.start + MOON.n) {
                        total++;
                        if (c.comp[i] >= MOON.start && c.comp[i] < MOON.start + MOON.n) seen++;
                    }
                }
            }
        }
        return seen / (double) Math.max(1, total);
    }

    private void build() {
        double strength = eco.cfg.flare;
        cached.clear();
        sun = null;
        if (strength <= 0.01 || eco.climate.snowing != 0 || eco.raining != 0) {
            // The sun is still up there, with or without the lens effects.
            double[] at = sunPosition();
            if (at != null) sunPixels((int) at[0], (int) at[1]);
            visible = 0.0;
            return;
        }
        if (noise == null) {
            byte[] raw = new PyRandom(eco.s.seed ^ 0xF1A2E).randbytes(eco.w * eco.h);
            noise = new int[raw.length];
            for (int i = 0; i < raw.length; i++) noise[i] = raw[i] & 255;
        }
        double[] at = sunPosition();
        double seen, power;
        int sx, sy, reach;
        if (at != null) {
            sun = at;
            sx = (int) at[0];
            sy = (int) at[1];
            seen = sunPixels(sx, sy);
            power = Math.min(1.0, seen * 1.6) * (0.55 + 0.45 * Math.min(1.0, eco.daylight / 0.5));
            reach = 46;
        } else {
            seen = moonVisible();
            sx = eco.s.moonX;
            sy = eco.s.moonY;
            power = seen * 0.9;
            reach = 34;
        }
        visible = seen;
        power *= strength;
        // The light's own colour: warm gold from the sun, cool from the moon.
        tint0 = at != null ? EYE.at(0) : STAR.at(0);
        tint1 = at != null ? EYE.at(1) : STAR.at(1);
        if (power > 0.02) {
            bloom(sx, sy, power, reach);
            ghosts(sx, sy, power);
        }
    }

    /** Brightest close in, thinning out with distance, and a little wider than tall, as glare is. */
    private void bloom(int sx, int sy, double power, int reachBase) {
        int[] frame = eco.c.frame;
        int w = eco.w, h = eco.h;
        int reach = (int) (reachBase * eco.scaleS * (0.6 + 0.6 * power));
        for (int dy = -reach; dy <= reach; dy++) {
            int py = sy + dy;
            if (py < 0 || py >= h) continue;
            for (int dx = Clock.div(-reach * 3, 2), e = reach * 3 / 2; dx <= e; dx++) {
                int px = sx + dx;
                if (px < 0 || px >= w) continue;
                double d = Math.hypot(dx / 1.5, dy) / reach;
                if (d >= 1) continue;
                int i = py * w + px;
                if (cached.has(i)) continue;
                double falloff = (1 - d) * (1 - d) * power;
                double roll = noise[i] / 255.0;
                if (roll < falloff * falloff * 0.5) cached.put(i, d < 0.3 ? tint1 : tint0);
                else if (roll < falloff * 1.3) cached.put(i, LIT2[frame[i]]);
                else if (roll < falloff * 3.0) cached.put(i, LIT[frame[i]]);
            }
        }
        // A thin streak through the light, the mark of a lens.
        for (int dx = -(int) (reach * 2.2), e = (int) (reach * 2.2); dx <= e; dx++) {
            int px = sx + dx;
            if (0 <= px && px < w && 0 <= sy && sy < h) {
                int i = sy * w + px;
                double falloff = (1 - Math.abs(dx) / (reach * 2.2)) * power;
                if (!cached.has(i) && noise[i] / 255.0 < falloff) cached.put(i, Math.abs(dx) < reach ? tint0 : LIT2[frame[i]]);
            }
        }
    }

    private void ghosts(int sx, int sy, double power) {
        int[] frame = eco.c.frame;
        int w = eco.w, h = eco.h;
        double cx = w / 2.0, cy = h / 2.0;
        for (double[] ghost : GHOSTS) {
            double gx = sx + (cx - sx) * ghost[0], gy = sy + (cy - sy) * ghost[0];
            double r = Math.max(1.5, ghost[1] * eco.scaleS);
            double amount = ghost[2] * power * 2.4;
            int ir = (int) r + 1;
            for (int dy = -ir; dy <= ir; dy++) {
                int py = (int) gy + dy;
                if (py < 0 || py >= h) continue;
                for (int dx = -ir; dx <= ir; dx++) {
                    int px = (int) gx + dx;
                    if (px < 0 || px >= w) continue;
                    double d = Math.hypot(dx, dy) / r;
                    if (d > 1) continue;
                    int i = py * w + px;
                    if (cached.has(i)) continue;
                    double roll = noise[i] / 255.0;
                    // Each disc is faint inside, with a slightly firmer rim.
                    if (d > 0.78) {
                        if (roll < amount * 0.55) cached.put(i, tint0);
                        else if (roll < amount * 2.0) cached.put(i, LIT2[frame[i]]);
                    } else if (roll < amount) cached.put(i, roll < amount * 0.25 ? LIT2[frame[i]] : LIT[frame[i]]);
                }
            }
        }
    }

    /** Lays the light under whatever is already in this frame's overlay. */
    void frame(Overlay overlay) {
        if (eco.frameNo % RECOMPUTE_FRAMES == 0) build();
        for (int k = 0; k < cached.keys.size; k++) {
            int i = cached.keys.a[k];
            overlay.setdefault(i, cached.val[i]);
        }
    }
}
