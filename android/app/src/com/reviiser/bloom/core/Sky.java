package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * The real sky behind the leaves. Stars come from a small catalogue of real positions, and the moon from a standard
 * low-precision lunar theory. The view is centred on the painted moon with your zenith straight up, so every star sits
 * where it truly is relative to the moon, for your place, date and time of night. Your place is worked out from the
 * timezone setting, or set in the menu. Nothing here uses the network or any location service.
 */
public final class Sky {
    static final double PIXELS_PER_DEGREE = 3.0; // wide enough that whole constellations fit in the gap in the leaves

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double mod360(double v) { return v - 360.0 * Math.floor(v / 360.0); }

    static double julianDay(long millis) { return 2440587.5 + millis / 86400000.0; }

    /** The moon's right ascension (degrees), declination and parallax, in the same J2000 frame as the catalogue. */
    static double[] moonEquatorial(double jd) {
        double t = (jd - 2451545.0) / 36525.0;
        double lon = 218.32 + 481267.881 * t + 6.29 * sin(135.0 + 477198.87 * t) - 1.27 * sin(259.3 - 413335.36 * t)
                + 0.66 * sin(235.7 + 890534.22 * t) + 0.21 * sin(269.9 + 954397.74 * t) - 0.19 * sin(357.5 + 35999.05 * t) - 0.11 * sin(186.5 + 966404.03 * t);
        double lat = 5.13 * sin(93.3 + 483202.02 * t) + 0.28 * sin(228.2 + 960400.89 * t) - 0.28 * sin(318.3 + 6003.15 * t) - 0.17 * sin(217.6 - 407332.21 * t);
        double parallax = 0.9508 + 0.0518 * cos(135.0 + 477198.87 * t) + 0.0095 * cos(259.3 - 413335.36 * t)
                + 0.0078 * cos(235.7 + 890534.22 * t) + 0.0028 * cos(269.9 + 954397.74 * t);
        lon = mod360(lon) - 1.397 * t; // undo precession since 2000, so the moon and the stars agree
        double obliquity = 23.439;
        double x = cos(lat) * cos(lon);
        double y = cos(obliquity) * cos(lat) * sin(lon) - sin(obliquity) * sin(lat);
        double z = sin(obliquity) * cos(lat) * sin(lon) + cos(obliquity) * sin(lat);
        return new double[]{mod360(Math.toDegrees(Math.atan2(y, x))), Math.toDegrees(Math.asin(z)), parallax};
    }

    static double localSiderealDegrees(double jd, double longitude) { return mod360(280.46061837 + 360.98564736629 * (jd - 2451545.0) + longitude); }

    /** A unit vector (east, north, up) for a position on the sky. */
    static double[] toHorizon(double ra, double dec, double lst, double latitude) {
        double hour = lst - ra;
        return new double[]{-cos(dec) * sin(hour), sin(dec) * cos(latitude) - cos(dec) * cos(hour) * sin(latitude), sin(dec) * sin(latitude) + cos(dec) * cos(hour) * cos(latitude)};
    }

    /** Where the moon appears from where you stand, which is up to a degree from where it is from Earth's centre. */
    static double[] moonVector(double jd, double lst, double latitude) {
        double[] eq = moonEquatorial(jd);
        double[] v = toHorizon(eq[0], eq[1], lst, latitude);
        double r = 1.0 / sin(eq[2]);
        double e = v[0] * r, n = v[1] * r, u = v[2] * r - 1.0;
        double m = Math.sqrt(e * e + n * n + u * u);
        return new double[]{e / m, n / m, u / m};
    }

    /** Your timezone's main city, from a table carried in the app: only as exact as that, and entirely offline. */
    static Object[] locationFromTimezone() {
        TimeZone zone = TimeZone.getDefault();
        double[] at = Zones.lookup(zone.getID());
        if (at != null) return new Object[]{at[0], at[1], zone.getID()};
        return new Object[]{40.0, zone.getOffset(Clock.millis()) / 3600000.0 * 15.0, null};
    }

    /** 'auto', or 'latitude, longitude' in degrees (north and east positive). */
    static Object[] parseLocation(String text) {
        if (text != null && !text.trim().equalsIgnoreCase("auto")) {
            try {
                String[] parts = text.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim()), lon = Double.parseDouble(parts[1].trim());
                    if (-90 <= lat && lat <= 90 && -180 <= lon && lon <= 180) return new Object[]{lat, lon, "set by you"};
                }
            } catch (NumberFormatException e) {
                // falls through to the timezone
            }
        }
        return locationFromTimezone();
    }

    final Eco eco;
    private final HashMap<Integer, Integer> saved = new HashMap<>(); // pixels we have changed, and what they were
    public final List<int[]> stars = new ArrayList<>();
    public String near;
    public List<String> visible = new ArrayList<>();
    private Object[] place;
    private String placeText = "\0";

    Sky(Eco eco) { this.eco = eco; }

    private Object[] place() {
        String text = eco.cfg.location;
        if (!text.equals(placeText)) {
            placeText = text;
            place = parseLocation(text);
        }
        return place;
    }

    /** Screen positions {x, y, depth} of catalogue stars for a moment, null for ones too far from the moon. */
    double[][] project(long millis) {
        Object[] here = place();
        double latitude = (Double) here[0], longitude = (Double) here[1];
        double jd = julianDay(millis);
        double lst = localSiderealDegrees(jd, longitude);
        double[] c = moonVector(jd, lst, latitude);
        // Facing the moon, with the zenith straight up.
        double rx = c[1], ry = -c[0], rz = 0.0;
        double m = Math.hypot(rx, ry);
        if (m == 0) m = 1.0;
        rx /= m;
        ry /= m;
        double ux = ry * c[2] - rz * c[1], uy = rz * c[0] - rx * c[2], uz = rx * c[1] - ry * c[0];
        double scale = Math.toDegrees(1.0) * PIXELS_PER_DEGREE * eco.s.h / 270.0;
        double[][] positions = new double[SkyData.COUNT][];
        for (int k = 0; k < SkyData.COUNT; k++) {
            double[] s = toHorizon(SkyData.RA[k] * 15.0, SkyData.DEC[k], lst, latitude);
            double depth = s[0] * c[0] + s[1] * c[1] + s[2] * c[2];
            if (depth < 0.35) continue;
            double x = (s[0] * rx + s[1] * ry + s[2] * rz) / depth;
            double y = (s[0] * ux + s[1] * uy + s[2] * uz) / depth;
            positions[k] = new double[]{eco.s.moonX + x * scale, eco.s.moonY - y * scale, depth};
        }
        return positions;
    }

    private boolean clear(int i) {
        Integer was = saved.get(i);
        int v = was != null ? was : eco.c.base[i];
        return (v >= SKY.start && v < SKY.start + SKY.n) || (v >= STAR.start && v < STAR.start + STAR.n);
    }

    /** Repaint the stars for now. Only pixels that actually changed get touched. */
    public void update() {
        Canvas canvas = eco.c;
        Map<Integer, Integer> wanted = new LinkedHashMap<>();
        visible = new ArrayList<>();
        stars.clear();
        int nearIndex = -1;
        double nearDepth = -2.0;
        if (eco.cfg.constellations && eco.daylight < 0.3) {
            double[][] positions = project(eco.climate.now().getTimeInMillis());
            int w = canvas.w, gy = eco.gy, mx = eco.s.moonX, my = eco.s.moonY, mr = eco.s.moonR;
            Map<String, Integer> showing = new LinkedHashMap<>();
            for (int k = 0; k < positions.length; k++) {
                double[] pos = positions[k];
                if (pos == null) continue;
                if (pos[2] > nearDepth) {
                    nearIndex = k;
                    nearDepth = pos[2];
                }
                int ix = (int) pos[0], iy = (int) pos[1];
                if (0 <= ix && ix < w && 0 <= iy && iy < gy && (ix - mx) * (ix - mx) + (iy - my) * (iy - my) > (mr + 1) * (mr + 1)) {
                    int i = iy * w + ix;
                    if (clear(i)) {
                        wanted.put(i, STAR.at(2));
                        stars.add(new int[]{ix, iy});
                        String home = SkyData.HOME[k];
                        Integer seen = showing.get(home);
                        showing.put(home, seen == null ? 1 : seen + 1);
                        boolean bright = SkyData.BRIGHT[k];
                        // A little body of light around each, larger for the brightest: it should read as a star.
                        for (int off : new int[]{-1, 1, -w, w}) {
                            int q = i + off;
                            if (0 <= q && q < w * gy && clear(q)) wanted.put(q, bright ? STAR.at(1) : STAR.at(0));
                        }
                        if (bright) {
                            for (int off : new int[]{-2, 2, -2 * w, 2 * w}) {
                                int q = i + off;
                                if (0 <= q && q < w * gy && clear(q)) wanted.put(q, STAR.at(0));
                            }
                        }
                    }
                }
            }
            for (Map.Entry<String, Integer> e : showing.entrySet()) if (e.getValue() >= 3) visible.add(e.getKey());
        }
        near = nearIndex >= 0 ? SkyData.HOME[nearIndex] : null;
        // Put back what's no longer wanted, then paint what's new.
        for (Integer i : new ArrayList<>(saved.keySet())) {
            if (!wanted.containsKey(i)) {
                canvas.base[i] = saved.remove(i);
                canvas.refresh(i);
            }
        }
        for (Map.Entry<Integer, Integer> e : wanted.entrySet()) {
            int i = e.getKey(), v = e.getValue();
            if (!saved.containsKey(i)) saved.put(i, canvas.base[i]);
            if (canvas.base[i] != v) {
                canvas.base[i] = v;
                canvas.refresh(i);
            }
        }
    }

    public String placeName() { return place() != null ? (String) place[2] : null; }

    /** Latitude and longitude in use, for the menu. */
    public double[] placeDegrees() {
        Object[] here = place();
        return new double[]{(Double) here[0], (Double) here[1]};
    }
}
