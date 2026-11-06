package com.reviiser.bloom.core;

import java.util.HashSet;
import java.util.List;

/** Python's random.Random, number for number, so a seed grows the same forest here as on the desktop. */
public final class PyRandom {
    private final int[] mt = new int[624];
    private int index = 625;
    private double gaussNext;
    private boolean hasGauss;

    public PyRandom(long seed) { seed(seed); }
    public PyRandom(double seed) { seedUnsigned(hashDouble(seed)); }

    public void seed(long seed) {
        seedUnsigned(seed == Long.MIN_VALUE ? seed : Math.abs(seed));
    }

    private void seedUnsigned(long n) {
        int[] key = (n >>> 32) != 0 ? new int[]{(int) n, (int) (n >>> 32)} : new int[]{(int) n};
        initGenrand(19650218);
        int i = 1, j = 0;
        for (int k = Math.max(624, key.length); k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++; j++;
            if (i >= 624) { mt[0] = mt[623]; i = 1; }
            if (j >= key.length) j = 0;
        }
        for (int k = 623; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
            i++;
            if (i >= 624) { mt[0] = mt[623]; i = 1; }
        }
        mt[0] = 0x80000000;
        index = 624;
        hasGauss = false;
    }

    private void initGenrand(int s) {
        mt[0] = s;
        for (int i = 1; i < 624; i++) mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
        index = 624;
    }

    /** Python's hash() of a float, which is what seeding from a float actually uses. */
    static long hashDouble(double v) {
        final long mod = (1L << 61) - 1;
        if (v == 0) return 0;
        boolean neg = v < 0;
        double m = Math.abs(v);
        int e = Math.getExponent(m) + 1;
        m = m / Math.pow(2, e);
        long x = 0;
        while (m != 0) {
            x = ((x << 28) & mod) | (x >>> 33);
            m *= 268435456.0;
            e -= 28;
            long y = (long) m;
            m -= y;
            x += y;
            if (x >= mod) x -= mod;
        }
        e = e >= 0 ? e % 61 : 60 - (-1 - e) % 61;
        x = ((x << e) & mod) | (x >>> (61 - e));
        if (neg) x = -x;
        if (x == -1) x = -2;
        return x;
    }

    private int next32() {
        if (index >= 624) {
            for (int k = 0; k < 624; k++) {
                int y = (mt[k] & 0x80000000) | (mt[(k + 1) % 624] & 0x7fffffff);
                mt[k] = mt[(k + 397) % 624] ^ (y >>> 1) ^ ((y & 1) != 0 ? 0x9908b0df : 0);
            }
            index = 0;
        }
        int y = mt[index++];
        y ^= y >>> 11;
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        return y ^ (y >>> 18);
    }

    /** For tests: a fingerprint of the generator's position. */
    public long stateHash() {
        long h = 0;
        for (int v : mt) h = h * 31 + (v & 0xffffffffL);
        return (h + index) & 0xffffffffffffL;
    }

    public double random() {
        int a = next32() >>> 5, b = next32() >>> 6;
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
    }

    private int below(int n) {
        int k = 32 - Integer.numberOfLeadingZeros(n);
        int r = next32() >>> (32 - k);
        while (r >= n) r = next32() >>> (32 - k);
        return r;
    }

    public int randrange(int stop) { return below(stop); }
    public int randrange(int start, int stop) { return start + below(stop - start); }
    public int randint(int a, int b) { return a + below(b - a + 1); }
    public double uniform(double a, double b) { return a + (b - a) * random(); }
    public int choice(int... options) { return options[below(options.length)]; }
    public double choice(double... options) { return options[below(options.length)]; }
    public <T> T choice(T[] options) { return options[below(options.length)]; }
    public <T> T choice(List<T> options) { return options.get(below(options.size())); }
    public String choiceOf(String... options) { return options[below(options.length)]; }

    public double gauss(double mu, double sigma) {
        double z;
        if (hasGauss) { z = gaussNext; hasGauss = false; }
        else {
            double x2pi = random() * 2 * Math.PI, g2rad = Math.sqrt(-2.0 * Math.log(1.0 - random()));
            z = Math.cos(x2pi) * g2rad;
            gaussNext = Math.sin(x2pi) * g2rad;
            hasGauss = true;
        }
        return mu + z * sigma;
    }

    public <T> void shuffle(List<T> x) {
        for (int i = x.size() - 1; i > 0; i--) {
            int j = below(i + 1);
            T t = x.get(i); x.set(i, x.get(j)); x.set(j, t);
        }
    }

    public void shuffle(int[] x) {
        for (int i = x.length - 1; i > 0; i--) {
            int j = below(i + 1);
            int t = x[i]; x[i] = x[j]; x[j] = t;
        }
    }

    /** Which positions of a list of size n a sample of k would pick. */
    public int[] sampleIndices(int n, int k) {
        int[] result = new int[k];
        int setsize = 21;
        if (k > 5) setsize += (int) Math.pow(4, Math.ceil(Math.log(k * 3) / Math.log(4)));
        if (n <= setsize) {
            int[] pool = new int[n];
            for (int i = 0; i < n; i++) pool[i] = i;
            for (int i = 0; i < k; i++) {
                int j = below(n - i);
                result[i] = pool[j];
                pool[j] = pool[n - i - 1];
            }
        } else {
            HashSet<Integer> selected = new HashSet<>();
            for (int i = 0; i < k; i++) {
                int j = below(n);
                while (selected.contains(j)) j = below(n);
                selected.add(j);
                result[i] = j;
            }
        }
        return result;
    }

    public byte[] randbytes(int n) {
        byte[] out = new byte[n];
        for (int i = 0; i < n; i += 4) {
            int left = n - i;
            int r = next32();
            if (left < 4) r >>>= 32 - left * 8;
            for (int b = 0; b < 4 && b < left; b++) out[i + b] = (byte) (r >>> (8 * b));
        }
        return out;
    }
}
