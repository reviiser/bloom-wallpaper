import com.reviiser.bloom.core.*;
import java.io.*;
import java.util.*;

/** Runs the engine with no window: soaks it for errors, and writes pictures of the forest and every menu page to look at. */
public class Shots {
    static void ppm(String path, int[] px, int w, int h) throws IOException {
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(path))) {
            o.write(("P6\n" + w + " " + h + "\n255\n").getBytes());
            for (int v : px) { o.write(v >> 16 & 255); o.write(v >> 8 & 255); o.write(v & 255); }
        }
    }
    public static void main(String[] a) throws Exception {
        File out = new File(a[0]), dir = new File(a[1]);
        long ticks = Long.parseLong(a[2]);
        out.mkdirs(); dir.mkdirs();
        Menu.Host host = new Menu.Host() {
            public boolean isScreensaver() { return true; }
            public void openScreensaverSettings() { System.out.println("open screensaver settings"); }
            public String screensaverHint() { return "Bloom is your screensaver."; }
        };
        long t0 = System.nanoTime();
        Engine e = new Engine(dir, host, 1920, 1080);
        e.start();
        System.out.println("started in " + (System.nanoTime() - t0) / 1000000 + " ms, seed " + e.world.seed);
        long fixed = System.currentTimeMillis();
        Clock.fixedMillis = fixed;
        t0 = System.nanoTime();
        int errors = 0;
        for (long k = 0; k < ticks; k++) {
            Clock.fixedMillis += 125;
            try { e.frame(); } catch (RuntimeException ex) { if (errors++ < 5) ex.printStackTrace(); }
        }
        System.out.println(ticks + " frames in " + (System.nanoTime() - t0) / 1000000 + " ms, errors " + errors);
        ppm(new File(out, "forest.ppm").getPath(), e.picture(), e.pictureW(), e.pictureH());
        e.openMenu();
        String[] names = {"settings", "animals", "plants", "forestpage", "oddities", "journal"};
        for (int t = 0; t < 6; t++) {
            Clock.fixedMillis += 125; e.frame();
            ppm(new File(out, "menu_" + names[t] + ".ppm").getPath(), e.menuPixels(), Menu.W, Menu.H);
            e.key(Menu.KEY_NEXT_TAB);
        }
        // Walk every key over every page, to shake out any error in the menus.
        Random r = new Random(1);
        int[] keys = {Menu.KEY_UP, Menu.KEY_DOWN, Menu.KEY_LEFT, Menu.KEY_RIGHT, Menu.KEY_SELECT, Menu.KEY_PREV_TAB, Menu.KEY_NEXT_TAB, Menu.KEY_DIGIT + 3};
        for (int k = 0; k < 4000; k++) {
            e.key(keys[r.nextInt(keys.length)]);
            if (k % 400 == 399) e.openMenu();
            Clock.fixedMillis += 125;
            try { e.frame(); } catch (RuntimeException ex) { if (errors++ < 8) ex.printStackTrace(); }
        }
        System.out.println("after key walk, errors " + errors + " seed " + e.world.seed + " size " + e.pictureW());
        e.save();
        System.out.println("saved " + new File(dir, "world.json.gz").length() + " bytes");
        t0 = System.nanoTime();
        Engine again = new Engine(dir, host, 1920, 1080);
        again.start();
        System.out.println("reloaded in " + (System.nanoTime() - t0) / 1000000 + " ms; plants " + e.world.eco.plants.size() + " -> " + again.world.eco.plants.size());
        for (int k = 0; k < 400; k++) { Clock.fixedMillis += 125; again.frame(); }
        System.out.println("ran on after reload");
    }
}
