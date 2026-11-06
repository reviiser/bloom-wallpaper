package com.reviiser.bloom.core;

/** A growable list of ints, for the many lists of pixels. */
public final class IntList {
    public int[] a;
    public int size;
    public IntList() { a = new int[16]; }
    public IntList(int capacity) { a = new int[Math.max(4, capacity)]; }
    public void add(int v) {
        if (size == a.length) a = java.util.Arrays.copyOf(a, size * 2);
        a[size++] = v;
    }
    public int get(int i) { return a[i]; }
    public int last() { return a[size - 1]; }
    public int pop() { return a[--size]; }
    public void clear() { size = 0; }
    public boolean isEmpty() { return size == 0; }
    public void dropFront(int n) {
        System.arraycopy(a, n, a, 0, size - n);
        size -= n;
    }
    public int[] toArray() { return java.util.Arrays.copyOf(a, size); }
    public boolean sameAs(IntList o) {
        if (o == null || o.size != size) return false;
        for (int i = 0; i < size; i++) if (a[i] != o.a[i]) return false;
        return true;
    }
}
