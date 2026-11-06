package com.reviiser.bloom.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Ties scene, canvas and ecosystem together, independent of any window. */
public final class World {
    /** {daylight, warmth} from the clock or a pinned time of day. */
    public static double[] skyState(String mode) {
        if (mode.equals("night")) return new double[]{0.0, 0.0};
        if (mode.equals("dusk")) return new double[]{0.22, 0.75};
        if (mode.equals("day")) return new double[]{0.7, 0.05};
        Calendar now = Clock.now();
        double h = Clock.hour(now) + Clock.minute(now) / 60.0;
        if (h < 5.5 || h >= 20.5) return new double[]{0.0, 0.0};
        if (h < 7.5) { // dawn
            double t = (h - 5.5) / 2.0;
            return new double[]{0.7 * t * t, Math.sin(t * Math.PI) * 0.8};
        }
        if (h < 18.0) return new double[]{0.7, 0.05};
        double t = (20.5 - h) / 2.5; // dusk
        return new double[]{0.7 * t * t, Math.sin(t * Math.PI) * 0.9};
    }

    public Config cfg;
    public final int w, h;
    public final File dir;
    public long seed;
    public final Scene scene;
    public final Canvas canvas;
    public Eco eco;
    public double daylight, warmth;
    int waterPhase;
    private String lookKey;
    private boolean flashing, auroraOn;
    private int phaseDay;

    public static File statePath(File dir) { return new File(dir, "world.json.gz"); }
    public static File archivePath(File dir, String name) { return new File(dir, "world-" + name + ".json.gz"); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readState(File path) {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(path)))) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(1 << 20);
            byte[] chunk = new byte[1 << 16];
            int n;
            while ((n = in.read(chunk)) > 0) raw.write(chunk, 0, n);
            return (Map<String, Object>) Json.parse(raw.toString("UTF-8"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); java.io.OutputStream out = new FileOutputStream(to)) {
            byte[] chunk = new byte[1 << 16];
            int n;
            while ((n = in.read(chunk)) > 0) out.write(chunk, 0, n);
        }
    }

    private static long seedOf(Map<String, Object> state) { return (long) Json.num(state.get("seed")); }
    private static int size(Map<String, Object> state, String k) { return Json.integer(state.get(k)); }

    public World(Config config, int width, int height, File dir, boolean load) {
        cfg = config;
        w = width;
        h = height;
        this.dir = dir;
        Map<String, Object> state = load ? readState(statePath(dir)) : null;
        File source = statePath(dir);
        long carriedSeed = 0;
        if (state != null && config.seed != 0 && seedOf(state) != config.seed) {
            // Switching forests: put this one aside, and pick up the requested one if it exists.
            keep(source, archivePath(dir, Long.toString(seedOf(state))));
            source = archivePath(dir, Long.toString(config.seed));
            state = readState(source);
        }
        if (state != null && (size(state, "w") != width || size(state, "h") != height)) {
            // A different pixel size is a different canvas. Keep this forest for when you change back.
            keep(source, archivePath(dir, seedOf(state) + "-" + size(state, "w") + "x" + size(state, "h")));
            carriedSeed = config.seed != 0 ? config.seed : seedOf(state); // same landscape, whatever the pixel size
            state = readState(archivePath(dir, carriedSeed + "-" + width + "x" + height));
        }
        if (state != null) seed = seedOf(state);
        else if (config.seed != 0) seed = config.seed;
        else if (carriedSeed != 0) seed = carriedSeed;
        else seed = 1 + (long) (Math.random() * ((1 << 30) - 1));
        scene = Scene.build(width, height, seed, Clock.millis());
        canvas = new Canvas(width, height);
        System.arraycopy(scene.base, 0, canvas.base, 0, canvas.n);
        System.arraycopy(scene.fg, 0, canvas.fg, 0, canvas.n);
        canvas.flatten();
        eco = new Eco(canvas, scene, config, seed);
        eco.fps = config.fps;
        eco.climate.refreshCalendar();
        applyLook(true);
        boolean restored = false;
        if (state != null) {
            try {
                eco.fromState((Map<String, Object>) state.get("eco"));
                restored = true;
            } catch (Exception e) {
                System.err.println("bloom: could not restore saved world, starting fresh: " + e);
                eco = new Eco(canvas, scene, config, seed);
                java.util.Arrays.fill(canvas.life, 0);
                java.util.Arrays.fill(canvas.lifeZ, 0);
                java.util.Arrays.fill(canvas.moss, 0);
                java.util.Arrays.fill(canvas.myc, 0);
                canvas.flatten();
            }
        }
        if (!restored) eco.genesis();
        else if (state.get("saved_at") != null) eco.wild.evo.catchUp(Clock.seconds() - Json.num(state.get("saved_at")), 2.0 * config.speed);
        eco.sky.update();
        eco.clouds.step();
        updateLight(true);
        phaseDay = Clock.ordinal(Clock.now());
    }

    private static void keep(File from, File to) {
        try {
            copy(from, to);
        } catch (IOException e) {
            System.err.println("bloom: could not keep the previous forest: " + e);
        }
    }

    // ---- look --------------------------------------------------------------

    private int[] colors(double flash, double aurora, double auroraPhase) {
        return Palette.compute(cfg.theme, cfg.darkness, cfg.glow, cfg.saturation, daylight, warmth, waterPhase, eco != null ? eco.climate.look() : null, aurora, auroraPhase, flash, cfg.natural);
    }

    public boolean applyLook(boolean force) {
        double[] sky = skyState(cfg.timeOfDay);
        Palette.Look look = eco.climate.look();
        String key = cfg.theme + "|" + cfg.darkness + "|" + cfg.glow + "|" + cfg.saturation + "|" + cfg.natural + "|" + Json.round(sky[0], 2) + "|" + Json.round(sky[1], 2)
                + "|" + Json.round(look.autumn, 2) + "|" + Json.round(look.winter, 2) + "|" + Json.round(look.spring, 2) + "|" + Json.round(look.ice, 2);
        if (!force && key.equals(lookKey)) return false;
        lookKey = key;
        daylight = sky[0];
        warmth = sky[1];
        eco.daylight = daylight;
        canvas.setPalette(colors(0, 0, 0));
        return true;
    }

    public void setConfig(Config config) {
        cfg = config;
        eco.cfg = config;
        eco.fps = config.fps;
        eco.climate.refreshCalendar();
        eco.syncFauna();
        applyLook(true);
        eco.sky.update();
        eco.clouds.step();
        updateLight(true);
    }

    /** Cheap slow housekeeping: time of day, tonight's moon, mist. */
    public void minuteTick() {
        applyLook(false);
        eco.sky.update();
        int today = Clock.ordinal(Clock.now());
        if (today != phaseDay) {
            phaseDay = today;
            scene.stampMoon(Scene.moonPhase(Clock.millis()), new Scene.Writer() {
                @Override public void write(int i, int v) {
                    canvas.base[i] = v;
                    canvas.refresh(i);
                }
            });
        }
        // Mist gathers around dawn and after rain.
        double target = 0.0;
        if (cfg.weather && (eco.raining != 0 || eco.climate.fog != 0 || (0.02 < daylight && daylight < 0.35))) target = eco.climate.fog != 0 ? 1.3 : 0.8;
        eco.mist += (target - eco.mist) * 0.15;
    }

    /** Change a run of palette entries only, leaving the rest alone. */
    private void push(int[] colors, Palette.Ramp ramp) {
        for (int i = ramp.start; i < ramp.start + ramp.n; i++) canvas.pal[i] = colors[i];
    }

    /** Rotate the pond's colours: all its shimmer, for almost no work. Frozen water holds still. */
    public void cycleWater() {
        if (eco.climate.frozen()) return;
        waterPhase = (waterPhase + 1) % Palette.WATER.n;
        push(colors(0, 0, 0), Palette.WATER);
        canvas.repaintRect(scene.pondX0, scene.pondY0, scene.pondX1, scene.pondY1);
    }

    /** Weather that lives in the palette: lightning for a frame or two, the aurora's slow curtains. */
    public void frameEffects(int frameNo) {
        Climate climate = eco.climate;
        if (climate.flash != 0) {
            climate.flash--;
            canvas.setPalette(colors(climate.flash != 0 ? 0.5 : 0.22, 0, 0));
            flashing = true;
        } else if (flashing) {
            flashing = false;
            canvas.setPalette(colors(0, 0, 0));
        }
        if (climate.aurora != 0 && frameNo % 16 == 0 && daylight < 0.1) {
            double level = Math.min(1.0, Math.min(climate.aurora, 600) / 600.0) * 0.8;
            push(colors(0, level, frameNo * 0.012), Palette.SKY);
            canvas.repaintRect(0, 0, w, scene.gy + 2);
            auroraOn = true;
        } else if (auroraOn && climate.aurora == 0) {
            auroraOn = false;
            canvas.setPalette(colors(0, 0, 0));
        }
    }

    public boolean updateLight(boolean force) {
        IntList spans = eco.lightSpans();
        if (force || !spans.sameAs(canvas.spans)) {
            canvas.applyLight(spans);
            return true;
        }
        return false;
    }

    // ---- persistence -------------------------------------------------------

    public void save() throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("w", (double) w);
        data.put("h", (double) h);
        data.put("seed", (double) seed);
        data.put("eco", eco.toState());
        data.put("pixel_size", (double) cfg.pixelSize);
        data.put("saved_at", Clock.seconds());
        String text = Json.write(data);
        dir.mkdirs();
        File tmp = new File(dir, "world.json.gz.tmp");
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))), "UTF-8")) {
            out.write(text);
        }
        if (!tmp.renameTo(statePath(dir))) throw new IOException("could not replace the saved world");
    }
}
