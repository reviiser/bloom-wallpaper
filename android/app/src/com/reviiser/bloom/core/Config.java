package com.reviiser.bloom.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Settings. On the TV they live in a small file beside the saved forest. */
public final class Config implements Cloneable {
    public static final String[] THEMES = {"deepwoods", "teal", "moonlit", "amber", "violet"};
    public static final String[] TIMES = {"auto", "night", "dusk", "day"};
    public static final String[] SEASON_MODES = {"auto", "spring", "summer", "autumn", "winter"};
    public static final String[] HEMISPHERES = {"north", "south"};

    public double darkness = 0.55, glow = 0.7, saturation = 0.9, natural = 0.8, flare = 0.6, crt = 0.0;
    public String theme = "deepwoods", timeOfDay = "auto", season = "auto", hemisphere = "north", location = "auto";
    public boolean oddities = true, constellations = true, fauna = true, lightShafts = true, weather = true, batterySaver = true;
    public int pixelSize = 4, fps = 8, fireflies = 28;
    public long seed = 0;
    public double speed = 1.0, density = 1.0;

    private static double clamp(double v, double low, double high) { return Math.max(low, Math.min(high, v)); }
    private static boolean among(String v, String[] options) { for (String o : options) if (o.equals(v)) return true; return false; }

    public void sanitise() {
        darkness = clamp(darkness, 0, 1); glow = clamp(glow, 0, 1); saturation = clamp(saturation, 0, 1.5);
        natural = clamp(natural, 0, 1); flare = clamp(flare, 0, 1); crt = clamp(crt, 0, 1);
        pixelSize = (int) clamp(pixelSize, 2, 10); fps = (int) clamp(fps, 1, 30);
        speed = clamp(speed, 0.05, 20); density = clamp(density, 0.1, 2); fireflies = (int) clamp(fireflies, 0, 120);
        if (!among(theme, THEMES)) theme = "deepwoods";
        if (!among(timeOfDay, TIMES)) timeOfDay = "auto";
        if (!among(season, SEASON_MODES)) season = "auto";
        if (!among(hemisphere, HEMISPHERES)) hemisphere = "north";
    }

    public Config copy() {
        try { return (Config) clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("darkness", darkness); m.put("glow", glow); m.put("saturation", saturation); m.put("natural", natural);
        m.put("flare", flare); m.put("crt", crt); m.put("theme", theme); m.put("time_of_day", timeOfDay); m.put("season", season);
        m.put("hemisphere", hemisphere); m.put("oddities", oddities); m.put("constellations", constellations); m.put("location", location);
        m.put("pixel_size", (double) pixelSize); m.put("fps", (double) fps); m.put("speed", speed); m.put("density", density);
        m.put("fireflies", (double) fireflies); m.put("fauna", fauna); m.put("light_shafts", lightShafts); m.put("weather", weather);
        m.put("battery_saver", batterySaver); m.put("seed", (double) seed);
        return m;
    }

    private static double d(Map<String, Object> m, String k, double v) { Object o = m.get(k); return o instanceof Number ? ((Number) o).doubleValue() : v; }
    private static String s(Map<String, Object> m, String k, String v) { Object o = m.get(k); return o instanceof String ? (String) o : v; }
    private static boolean b(Map<String, Object> m, String k, boolean v) { Object o = m.get(k); return o instanceof Boolean ? (Boolean) o : v; }

    public static Config fromMap(Map<String, Object> m) {
        Config c = new Config();
        c.darkness = d(m, "darkness", c.darkness); c.glow = d(m, "glow", c.glow); c.saturation = d(m, "saturation", c.saturation);
        c.natural = d(m, "natural", c.natural); c.flare = d(m, "flare", c.flare); c.crt = d(m, "crt", c.crt);
        c.theme = s(m, "theme", c.theme); c.timeOfDay = s(m, "time_of_day", c.timeOfDay); c.season = s(m, "season", c.season);
        c.hemisphere = s(m, "hemisphere", c.hemisphere); c.oddities = b(m, "oddities", c.oddities);
        c.constellations = b(m, "constellations", c.constellations); c.location = s(m, "location", c.location);
        c.pixelSize = (int) d(m, "pixel_size", c.pixelSize); c.fps = (int) d(m, "fps", c.fps); c.speed = d(m, "speed", c.speed);
        c.density = d(m, "density", c.density); c.fireflies = (int) d(m, "fireflies", c.fireflies); c.fauna = b(m, "fauna", c.fauna);
        c.lightShafts = b(m, "light_shafts", c.lightShafts); c.weather = b(m, "weather", c.weather);
        c.batterySaver = b(m, "battery_saver", c.batterySaver); c.seed = (long) d(m, "seed", c.seed);
        c.sanitise();
        return c;
    }
}
