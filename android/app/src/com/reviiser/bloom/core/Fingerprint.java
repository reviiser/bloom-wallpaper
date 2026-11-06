package com.reviiser.bloom.core;

/** A compact description of a forest's state, for tests. */
public final class Fingerprint {
    private Fingerprint() {}
    public static String of(World world, int ticks) {
        Eco e = world.eco;
        StringBuilder b = new StringBuilder();
        b.append("genesis ").append(state(e)).append('\n');
        for (int t = 0; t < ticks; t++) {
            e.tick();
            if (t % 500 == 499) b.append("tick").append(t + 1).append(' ').append(state(e)).append('\n');
        }
        return b.toString();
    }
    static String state(Eco e) {
        StringBuilder b = new StringBuilder();
        long h = 17;
        for (Eco.Plant p : e.plants) h = h * 31 + p.x * 7 + p.y * 11 + p.seed + p.shown;
        int comp = 0;
        for (int v : e.c.comp) comp = comp * 31 + v;
        b.append("plants=").append(e.plants.size()).append(" hash=").append(h & 0xffffff).append(" moss=").append(e.mossCount).append(" myc=").append(e.mycCount)
         .append(" col=").append(e.colonies.size()).append(" beetles=").append(e.beetles.size()).append(" moths=").append(e.moths.size()).append(" comp=").append(comp & 0xffffff);
        return b.toString();
    }
}
