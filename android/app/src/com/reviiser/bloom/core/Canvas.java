package com.reviiser.bloom.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Layered indexed canvas with cheap partial updates.
 * Planes, top to bottom: cap (snow on things), fg, life, snow, leaf, myc, moss, cloud, base.
 * comp = flattened planes, frame = comp with light shafts applied, buf = frame through the palette.
 * Moving things never touch the planes: they are overlaid straight onto buf and diffed frame to frame.
 */
public final class Canvas {
    public static final int TILE_SHIFT = 3; // dirty tracking granularity: 8x8 art pixels
    public final int w, h, n;
    public final int[] base, fg, life, lifeZ, myc, moss, snow, cap, cloud, leaf, shade, comp, frame, mask, buf;
    public final int[] pal = new int[256];
    public IntList spans = new IntList();
    public Overlay overlay, spare;
    final boolean[] dirty;
    int dirtyCount;
    boolean dirtyAll = true;
    public final int tilesW, tilesH;

    public Canvas(int width, int height) {
        w = width; h = height; n = w * h;
        base = new int[n]; fg = new int[n]; life = new int[n]; lifeZ = new int[n]; myc = new int[n]; moss = new int[n];
        snow = new int[n]; cap = new int[n]; cloud = new int[n]; leaf = new int[n]; shade = new int[n]; comp = new int[n];
        frame = new int[n]; mask = new int[n]; buf = new int[n];
        overlay = new Overlay(n);
        spare = new Overlay(n);
        tilesW = (w >> TILE_SHIFT) + 1;
        tilesH = (h >> TILE_SHIFT) + 1;
        dirty = new boolean[tilesW * tilesH];
    }

    public void setPalette(int[] colors) {
        System.arraycopy(colors, 0, pal, 0, 256);
        repaint();
    }

    /** Re-translate the entire frame through the palette. */
    public void repaint() {
        for (int i = 0; i < n; i++) buf[i] = pal[frame[i]];
        for (int k = 0; k < overlay.keys.size; k++) {
            int i = overlay.keys.a[k], v = overlay.val[i];
            if (v >= 0) buf[i] = pal[v];
        }
        dirtyAll = true;
    }

    /** Re-translate a rectangle, used for colour-cycling the pond. */
    public void repaintRect(int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int a = y * w + x0, e = y * w + x1; a < e; a++) buf[a] = pal[frame[a]];
        }
        // Whatever is standing in front of this rectangle stays there.
        for (int k = 0; k < overlay.keys.size; k++) {
            int i = overlay.keys.a[k], v = overlay.val[i], yy = i / w, xx = i % w;
            if (v >= 0 && y0 <= yy && yy < y1 && x0 <= xx && xx < x1) buf[i] = pal[v];
        }
        for (int ty = y0 >> TILE_SHIFT; ty <= (y1 - 1) >> TILE_SHIFT; ty++)
            for (int tx = x0 >> TILE_SHIFT; tx <= (x1 - 1) >> TILE_SHIFT; tx++) mark(ty * tilesW + tx);
    }

    private void mark(int t) { if (!dirty[t]) { dirty[t] = true; dirtyCount++; } }

    private int top(int i) {
        int v = cap[i];
        if (v == 0) v = fg[i];
        if (v == 0) v = life[i];
        if (v == 0) { // the ground, where shadows fall
            v = snow[i];
            if (v == 0) v = leaf[i];
            if (v == 0) v = myc[i];
            if (v == 0) v = moss[i];
            if (v == 0) v = cloud[i];
            if (v == 0) v = base[i];
            if (shade[i] != 0) v = Palette.DARK[v];
        }
        return v;
    }

    /** Rebuild comp from the planes. Only needed when loading a world. */
    public void flatten() {
        for (int i = 0; i < n; i++) comp[i] = top(i);
        applyLight(spans);
    }

    /** spans: triples of (y, x0, x1) that get the LIT treatment. Repaints everything. */
    public void applyLight(IntList newSpans) {
        spans = newSpans;
        System.arraycopy(comp, 0, frame, 0, n);
        java.util.Arrays.fill(mask, 0);
        int[] lit = Palette.LIT;
        for (int s = 0; s + 2 < spans.size; s += 3) {
            int y = spans.a[s], a = y * w + spans.a[s + 1], b = y * w + spans.a[s + 2];
            for (int i = a; i < b; i++) { frame[i] = lit[comp[i]]; mask[i] = 1; }
        }
        repaint();
    }

    /** A plane changed at pixel i: push it through to buf. */
    public void refresh(int i) {
        if (i < 0 || i >= n) return;
        int v = top(i);
        if (v == comp[i]) return;
        comp[i] = v;
        if (mask[i] != 0) v = Palette.LIT[v];
        frame[i] = v;
        if (overlay.get(i) < 0) buf[i] = pal[v];
        mark(((i / w) >> TILE_SHIFT) * tilesW + ((i % w) >> TILE_SHIFT));
    }

    /** Diff the moving-things overlay against the last frame. The old one comes back empty, as the next frame's. */
    public Overlay commitOverlay(Overlay next) {
        Overlay old = overlay;
        for (int k = 0; k < old.keys.size; k++) {
            int i = old.keys.a[k];
            if (old.val[i] >= 0 && next.val[i] < 0) {
                buf[i] = pal[frame[i]];
                mark(((i / w) >> TILE_SHIFT) * tilesW + ((i % w) >> TILE_SHIFT));
            }
        }
        for (int k = 0; k < next.keys.size; k++) {
            int i = next.keys.a[k], v = next.val[i];
            if (v >= 0 && old.val[i] != v) {
                buf[i] = pal[v];
                mark(((i / w) >> TILE_SHIFT) * tilesW + ((i % w) >> TILE_SHIFT));
            } else if (v < 0 && old.val[i] >= 0) {
                buf[i] = pal[frame[i]];
                mark(((i / w) >> TILE_SHIFT) * tilesW + ((i % w) >> TILE_SHIFT));
            }
        }
        overlay = next;
        old.clear();
        return old;
    }

    /** Dirty rectangles {x, y, w, h} in art pixels, or null when everything changed. */
    public List<int[]> takeDirty() {
        if (dirtyAll) {
            dirtyAll = false;
            clearDirty();
            return null;
        }
        List<int[]> rects = new ArrayList<>();
        if (dirtyCount == 0) return rects;
        int size = 1 << TILE_SHIFT;
        for (int ty = 0; ty < tilesH; ty++) {
            int start = -1;
            for (int tx = 0; tx <= tilesW; tx++) {
                boolean on = tx < tilesW && dirty[ty * tilesW + tx];
                if (on && start < 0) start = tx;
                else if (!on && start >= 0) {
                    rects.add(new int[]{start * size, ty * size, (tx - start) * size, size});
                    start = -1;
                }
            }
        }
        clearDirty();
        return rects;
    }

    private void clearDirty() {
        java.util.Arrays.fill(dirty, false);
        dirtyCount = 0;
    }
}
