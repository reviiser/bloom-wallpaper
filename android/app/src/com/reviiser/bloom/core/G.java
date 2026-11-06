package com.reviiser.bloom.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** A set of genes: names to numbers, in the order they were given, as the Python dicts keep them. */
public final class G extends LinkedHashMap<String, Double> {
    public G() {}
    public G(Map<String, ?> other) {
        for (Map.Entry<String, ?> e : other.entrySet()) if (e.getValue() instanceof Number) put(e.getKey(), ((Number) e.getValue()).doubleValue());
    }
    public double d(String k) { Double v = get(k); if (v == null) throw new IllegalArgumentException("no gene " + k); return v; }
    public double d(String k, double fallback) { Double v = get(k); return v == null ? fallback : v; }
    public int i(String k) { return (int) d(k); }
    public int i(String k, int fallback) { return (int) d(k, fallback); }
    public G with(String k, double v) { put(k, v); return this; }
    public void setdefault(String k, double v) { if (!containsKey(k)) put(k, v); }
}
