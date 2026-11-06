package com.reviiser.bloom.core;

import java.util.Arrays;

/** Moving things for one frame: pixel to colour, cheap to fill, clear and compare. Anything off the picture is ignored. */
public final class Overlay {
    final int[] val;
    final IntList keys = new IntList(512);
    public Overlay(int n) { val = new int[n]; Arrays.fill(val, -1); }
    public void put(int i, int v) {
        if (i < 0 || i >= val.length) return;
        if (val[i] < 0) keys.add(i);
        val[i] = v;
    }
    public void setdefault(int i, int v) { if (i >= 0 && i < val.length && val[i] < 0) put(i, v); }
    public boolean has(int i) { return i >= 0 && i < val.length && val[i] >= 0; }
    public int get(int i) { return i >= 0 && i < val.length ? val[i] : -1; }
    public void remove(int i) { if (has(i)) val[i] = -2; }
    public boolean isEmpty() { return keys.size == 0; }
    public void clear() {
        for (int k = 0; k < keys.size; k++) val[keys.a[k]] = -1;
        keys.clear();
    }
}
