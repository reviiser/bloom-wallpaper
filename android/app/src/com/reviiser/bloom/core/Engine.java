package com.reviiser.bloom.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * The heartbeat, with no knowledge of any window. Whoever owns the screen calls frame() on its own thread, then shows
 * picture() scaled up, with menuPixels() over it when the menu is open. Sticks around between screensaver runs.
 */
public final class Engine {
    public static final double SIM_HZ = 2.0; // ecosystem ticks per second at speed 1
    public static final long SAVE_MILLIS = 5 * 60 * 1000;

    public final File dir;
    public final Menu.Host host;
    public Config cfg;
    public World world;
    public volatile Menu menu;
    private int viewW, viewH;
    private double simDebt;
    private long lastFrame, nextLight, nextWater, nextSlow, nextSave, stoppedAt, rebuildAt, configAt;
    private int[] soft, softFor;
    private boolean softClean, pictureChanged = true;
    private int barY;
    private final ArrayDeque<Integer> keys = new ArrayDeque<>();
    public volatile String status = "";
    public boolean onBattery;

    public Engine(File dir, Menu.Host host, int viewWidth, int viewHeight) {
        this.dir = dir;
        this.host = host;
        viewW = viewWidth;
        viewH = viewHeight;
        cfg = loadConfig(dir, viewHeight);
    }

    private static File configFile(File dir) { return new File(dir, "config.json"); }

    @SuppressWarnings("unchecked")
    static Config loadConfig(File dir, int viewHeight) {
        File f = configFile(dir);
        if (f.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                byte[] raw = new byte[(int) f.length()];
                int got = 0, n;
                while (got < raw.length && (n = in.read(raw, got, raw.length - got)) > 0) got += n;
                return Config.fromMap((Map<String, Object>) Json.parse(new String(raw, 0, got, "UTF-8")));
            } catch (IOException | RuntimeException e) {
                System.err.println("bloom: ignoring unreadable settings: " + e);
            }
        }
        // A new TV: pixels chunky enough to look like the forest should, whatever the screen's size.
        Config c = new Config();
        c.pixelSize = Math.max(2, Math.min(8, Math.round(viewHeight / 270f)));
        return c;
    }

    private void saveConfig() {
        try {
            dir.mkdirs();
            File tmp = new File(dir, "config.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(Json.write(cfg.toMap()).getBytes(Charset.forName("UTF-8")));
            }
            tmp.renameTo(configFile(dir));
        } catch (IOException e) {
            System.err.println("bloom: could not save settings: " + e);
        }
    }

    private int canvasW() { return (int) Math.ceil(viewW / (double) cfg.pixelSize); }
    private int canvasH() { return (int) Math.ceil(viewH / (double) cfg.pixelSize); }

    /** Done off the screen's thread: a brand new forest takes a little while to grow. */
    public void start() {
        status = "A forest is growing";
        buildWorld();
        status = "";
        long now = Clock.millis();
        lastFrame = now;
        nextSave = now + 30000; // a first save soon, so a new world is safe
        saveConfig();
    }

    private void buildWorld() {
        world = new World(cfg, canvasW(), canvasH(), dir, true);
        soft = null;
        softClean = false;
        if (menu != null) menu.attach(world);
    }

    /** Back from being put away: time has passed, for the animals if not for the plants. */
    public void resume() {
        long now = Clock.millis();
        if (stoppedAt != 0 && world != null) world.eco.wild.evo.catchUp((now - stoppedAt) / 1000.0, SIM_HZ * cfg.speed);
        stoppedAt = 0;
        lastFrame = now;
        nextLight = nextWater = nextSlow = 0;
        pictureChanged = true;
    }

    public void stop() {
        stoppedAt = Clock.millis();
        save();
    }

    public void save() {
        if (world == null) return;
        try {
            world.save();
        } catch (IOException e) {
            System.err.println("bloom: could not save world: " + e);
        }
    }

    public int fps() { return onBattery && cfg.batterySaver ? Math.max(2, cfg.fps / 2) : cfg.fps; }

    public boolean menuOpen() { return menu != null; }
    public void openMenu() { if (menu == null && world != null) menu = new Menu(host, world, cfg); }

    /** Called from any thread. */
    public void key(int key) {
        synchronized (keys) {
            keys.add(key);
        }
    }

    private void takeKeys() {
        long now = Clock.millis();
        while (true) {
            Integer key;
            synchronized (keys) {
                key = keys.poll();
            }
            if (key == null) return;
            if (menu == null) openMenu(); // any button in the forest brings up the menu
            else {
                menu.key(key);
                if (menu.closed()) {
                    if (menu.takeChanged() || configAt != 0) settle(now);
                    configAt = 0;
                    menu = null;
                }
            }
        }
    }

    private void settle(long now) {
        applyConfig(Config.fromMap(menu.values), now);
    }

    private void applyConfig(Config next, long now) {
        boolean rebuild = next.pixelSize != cfg.pixelSize || (next.seed != 0 && next.seed != world.seed);
        if (next.crt != cfg.crt) softClean = false;
        cfg = next;
        saveConfig();
        if (rebuild) rebuildAt = now + 800; // a slider held down shouldn't grow a forest at every stop along the way
        else world.setConfig(cfg);
    }

    private void reconcile(long now) {
        if (menu != null && menu.takeChanged()) configAt = now + 150;
        if (configAt != 0 && now >= configAt && menu != null) {
            configAt = 0;
            applyConfig(Config.fromMap(menu.values), now);
        }
        if (rebuildAt != 0 && now >= rebuildAt) {
            rebuildAt = 0;
            status = "A forest is growing";
            save();
            buildWorld();
            status = "";
            lastFrame = Clock.millis();
            pictureChanged = true;
        }
    }

    /** One animation frame. */
    public void frame() {
        long now = Clock.millis();
        takeKeys();
        reconcile(now);
        double dt = (now - lastFrame) / 1000.0;
        lastFrame = now;
        Eco eco = world.eco;
        eco.fps = fps();
        simDebt += Math.min(dt, 2.0) * SIM_HZ * cfg.speed;
        int ticks = (int) simDebt;
        simDebt -= ticks;
        for (int k = 0; k < Math.min(ticks, 40); k++) eco.tick();
        eco.frame(false);
        if (now >= nextLight) {
            nextLight = now + 12000;
            world.updateLight(false);
        }
        if (now >= nextWater) {
            nextWater = now + 1100;
            world.cycleWater();
        }
        world.frameEffects(eco.frameNo);
        if (now >= nextSlow) {
            nextSlow = now + 60000;
            world.minuteTick();
        }
        if (now >= nextSave) {
            nextSave = now + SAVE_MILLIS;
            save();
        }
        List<int[]> rects = world.canvas.takeDirty();
        if (rects == null || !rects.isEmpty()) {
            pictureChanged = true;
            if (cfg.crt > 0.001) {
                if (soft == null || soft.length != world.canvas.n) {
                    soft = new int[world.canvas.n];
                    softClean = false;
                }
                Crt.smear(world.canvas.buf, soft, world.canvas.w, world.canvas.h, softClean ? rects : null, cfg.crt);
                softClean = true;
            }
        }
        if (cfg.crt > 0.001 && eco.frameNo % Crt.BAR_EVERY == 0) {
            barY = (barY + Crt.BAR_SPEED) % (viewH + Crt.BAR_HEIGHT);
            pictureChanged = true;
        }
        if (menu != null) {
            menu.draw();
            pictureChanged = true;
        }
    }

    /** True once if anything on screen has changed since last asked. */
    public boolean takePictureChanged() {
        boolean c = pictureChanged;
        pictureChanged = false;
        return c;
    }

    public int[] picture() { return cfg.crt > 0.001 && soft != null ? soft : world.canvas.buf; }
    public int pictureW() { return world.canvas.w; }
    public int pictureH() { return world.canvas.h; }
    public int barY() { return barY; }
    public int[] menuPixels() { return menu != null ? menu.pixels() : null; }
}
