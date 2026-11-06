package com.reviiser.bloom.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Just enough JSON for saved worlds: maps, lists, doubles, strings, booleans. Numbers always come back as Double. */
public final class Json {
    private Json() {}

    public static String write(Object o) {
        StringBuilder b = new StringBuilder(1 << 16);
        write(o, b);
        return b.toString();
    }

    private static void write(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                string(String.valueOf(e.getKey()), b);
                b.append(':');
                write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object v : (List<?>) o) {
                if (!first) b.append(',');
                first = false;
                write(v, b);
            }
            b.append(']');
        } else if (o instanceof double[]) {
            b.append('[');
            double[] a = (double[]) o;
            for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); number(a[i], b); }
            b.append(']');
        } else if (o instanceof int[]) {
            b.append('[');
            int[] a = (int[]) o;
            for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); }
            b.append(']');
        } else if (o instanceof Number) number(((Number) o).doubleValue(), b);
        else if (o instanceof Boolean) b.append(o.toString());
        else string(o.toString(), b);
    }

    private static void number(double v, StringBuilder b) {
        if (Double.isNaN(v) || Double.isInfinite(v)) b.append('0');
        else if (v == Math.rint(v) && Math.abs(v) < 1e15) b.append((long) v);
        else b.append(v);
    }

    private static void string(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32) b.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        return p.value();
    }

    private static final class Parser {
        final String s;
        int i;
        Parser(String s) { this.s = s; }
        void ws() { while (i < s.length() && s.charAt(i) <= ' ') i++; }
        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = str();
                    ws();
                    i++; // the colon
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(start, i));
        }
        String str() {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else b.append(c);
            }
        }
    }

    public static double num(Object o) { return ((Number) o).doubleValue(); }
    public static int integer(Object o) { return ((Number) o).intValue(); }
    public static double round(double v, int places) { double f = Math.pow(10, places); return Math.rint(v * f) / f; }

    public static double[] doubles(Object list) {
        List<?> l = (List<?>) list;
        double[] out = new double[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = num(l.get(i));
        return out;
    }

    public static double[] rounded(double[] a, int places) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = round(a[i], places);
        return out;
    }

    // ---- compact bytes, for the big grids ------------------------------------

    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    public static String packBytes(int[] values) {
        byte[] raw = new byte[values.length];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) values[i];
        Deflater d = new Deflater(6);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
        byte[] chunk = new byte[8192];
        while (!d.finished()) out.write(chunk, 0, d.deflate(chunk));
        d.end();
        byte[] z = out.toByteArray();
        StringBuilder b = new StringBuilder(z.length * 4 / 3 + 8);
        for (int i = 0; i < z.length; i += 3) {
            int n = (z[i] & 255) << 16 | (i + 1 < z.length ? (z[i + 1] & 255) << 8 : 0) | (i + 2 < z.length ? z[i + 2] & 255 : 0);
            b.append(B64.charAt(n >> 18 & 63)).append(B64.charAt(n >> 12 & 63));
            b.append(i + 1 < z.length ? B64.charAt(n >> 6 & 63) : '=').append(i + 2 < z.length ? B64.charAt(n & 63) : '=');
        }
        return b.toString();
    }

    public static int[] unpackBytes(String text, int size) throws java.util.zip.DataFormatException {
        ByteArrayOutputStream z = new ByteArrayOutputStream(text.length());
        int bits = 0, have = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=') break;
            bits = bits << 6 | B64.indexOf(c);
            have += 6;
            if (have >= 8) { have -= 8; z.write(bits >> have & 255); }
        }
        Inflater inf = new Inflater();
        inf.setInput(z.toByteArray());
        byte[] raw = new byte[size];
        int got = 0;
        while (got < size && !inf.finished()) {
            int k = inf.inflate(raw, got, size - got);
            if (k == 0 && inf.needsInput()) break;
            got += k;
        }
        inf.end();
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = raw[i] & 255;
        return out;
    }
}
