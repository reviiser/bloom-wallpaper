package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Animals and happenings that come and go, each one changing the forest a little. Residents (frogs, snails) live
 * here and are saved with the world. Visitors are real members of the populations in Evolution: who turns up
 * depends on who is alive and awake, and whatever happens to them on screen happens to their line.
 */
public final class Wildlife {
    static int sign(double v) { return v > 0 ? 1 : v < 0 ? -1 : 0; }

    /** Draw a sprite with its last row on `feet`. Returns the pixels it actually covered. reflect is -1 for none. */
    static IntList stamp(Eco eco, Overlay overlay, String[] rows, int x, int feet, boolean flip, int[] colors, int reflect) {
        Canvas c = eco.c;
        int w = eco.w, h = eco.h;
        int[] fg = c.fg, life = c.life, lifeZ = c.lifeZ, water = eco.s.water;
        int z = feet >= eco.gy ? (feet - eco.gy) * 255 / eco.span : 0;
        int top = feet - rows.length + 1;
        IntList placed = new IntList(64);
        if (feet >= eco.gy + 1 && reflect >= 0) {
            // A soft shadow on the ground under anything standing on it. The flyers and the shapes on the ridge don't cast one.
            int n = rows[rows.length - 1].length();
            for (int row = 1; row <= 2; row++) {
                int inset = row == 1 ? 0 : 2;
                int y = feet + row;
                if (0 <= y && y < h) {
                    for (int dx = inset; dx < n - inset; dx++) {
                        int px = x + dx;
                        if (0 <= px && px < w) {
                            int i = y * w + px;
                            if (fg[i] == 0 && life[i] == 0 && water[i] == 0 && (inset == 0 || ((px + y) & 1) != 0)) overlay.put(i, DARK[c.frame[i]]);
                        }
                    }
                }
            }
        }
        for (int ry = 0; ry < rows.length; ry++) {
            int y = top + ry;
            if (y < 0 || y >= h) continue;
            String row = rows[ry];
            int n = row.length();
            for (int rx = 0; rx < n; rx++) {
                char ch = row.charAt(rx);
                if (ch == '.') continue;
                int px = x + (flip ? n - 1 - rx : rx);
                if (px < 0 || px >= w) continue;
                int i = y * w + px;
                if (fg[i] != 0 || (life[i] != 0 && lifeZ[i] > z)) continue;
                overlay.put(i, colors[ch]);
                placed.add(i);
                if (reflect >= 0) {
                    int my = 2 * feet + 1 - y;
                    if (my < h && water[my * w + px] != 0 && (my & 1) != 0) overlay.setdefault(my * w + px, reflect);
                }
            }
        }
        return placed;
    }

    /** A soft light on whatever a pale or luminous animal is passing. */
    static void halo(Eco eco, Overlay overlay, IntList placed, int[] lut) {
        int w = eco.w, n = eco.w * eco.h;
        int[] frame = eco.c.frame, fg = eco.c.fg;
        int[] offs = {-1, 1, -w, w};
        for (int p = 0; p < placed.size; p++) {
            int i = placed.a[p];
            for (int off : offs) {
                int k = i + off;
                if (0 <= k && k < n && !overlay.has(k) && fg[k] == 0) overlay.put(k, lut[frame[k]]);
            }
        }
    }

    public abstract static class Creature {
        public final String name;
        final Wildlife wild;
        final Eco eco;
        final PyRandom rng;
        double move;
        int age;
        Animal ind;
        G g;

        Creature(String name, Wildlife wild, Animal ind) {
            this.name = name;
            this.wild = wild;
            this.eco = wild.eco;
            this.rng = wild.eco.rng;
            this.ind = ind;
            this.g = ind != null ? ind.g : null;
        }

        // (`reflect` also means "stands on the ground", so it casts a shadow too.)
        void body(Overlay overlay, String pose, int x, int feet, boolean flip, boolean reflect) {
            IntList placed = stamp(eco, overlay, Sprites.animalRows(name, pose, g), x, feet, flip, Evolution.coatColors(g), reflect ? Evolution.coatDark(g) : -1);
            if (g.d("glow") > 0.6) halo(eco, overlay, placed, LIT2);
        }
        void seen() { wild.odd.sighted(name); }
        void fed(double amount) { ind.e = Math.min(3.0, ind.e + amount); }
        boolean sick() { return wild.evo.plagues.containsKey(name); }
        /** Whole pixels to move this frame, independent of the frame rate. */
        int steps(double pxPerSecond) {
            move += pxPerSecond / Math.max(1, eco.fps);
            int n = (int) move;
            move -= n;
            return n;
        }
        int seconds(double s) { return (int) (s * Math.max(1, eco.fps)); }
        /** Advance and draw. Return false once gone. */
        abstract boolean frame(Overlay overlay);
    }

    public abstract static class Walker extends Creature {
        int half = 6, x, y, dir, strides;

        Walker(String name, int half, Wildlife wild, Animal ind, Integer atY) {
            super(name, wild, ind);
            this.half = half;
            dir = rng.choice(-1, 1);
            x = dir > 0 ? -18 : eco.w + 18;
            y = atY != null ? atY : eco.gy + (int) (eco.span * rng.uniform(0.15, 0.8));
        }

        /** Walk toward a point, around the water. Returns true on arrival. */
        boolean go(double speed, int tx, int ty) {
            for (int k = 0, n = steps(speed); k < n; k++) {
                int dx = tx - x, dy = ty - y;
                if (Math.abs(dx) <= 2 && Math.abs(dy) <= 1) return true;
                if (Math.abs(dx) > 2) {
                    dir = sign(dx);
                    x += dir;
                }
                y = wild.steer(x + half, y, y + sign(dy), dir);
                strides++;
                eco.climate.footprint(name, x + half, y + 1, strides);
            }
            return Math.abs(tx - x) <= 2 && Math.abs(ty - y) <= 1;
        }

        /** Head for the nearest edge. Returns true once out of sight. */
        boolean goOff(double speed) {
            go(speed, x < eco.w / 2.0 ? -24 : eco.w + 24, y);
            return x <= -20 || x >= eco.w + 20;
        }
    }

    public static final class Rabbit extends Walker {
        int pause, hopping, nibble, meals, patience, seedGen;
        Eco.Plant target;
        String seedKind;
        G seedG;
        boolean flee, gone;
        double wary;

        Rabbit(Wildlife wild, Animal ind, Integer y, int delay) {
            super("rabbit", 3, wild, ind, y);
            pause = delay;
            meals = rng.randint(2, 5);
            patience = seconds(90);
            wary = 22 + g.d("wary") * 70; // how far off it notices a fox
        }

        @Override boolean frame(Overlay overlay) {
            if (gone) return false;
            patience--;
            if (patience <= 0) meals = 0;
            Creature found = wild.find("fox");
            if (found instanceof Walker && !flee) {
                Walker fox = (Walker) found;
                if (Math.abs(fox.x - x) < wary && Math.abs(fox.y - y) < wary / 2) flee = true;
            }
            boolean hop = false;
            if (flee || meals <= 0) {
                if (pause > 0) pause--;
                else {
                    hop = true;
                    if (goOff(flee ? 24 + 30 * g.d("speed") : 12)) return false;
                    if (!flee && rng.random() < 0.02) pause = seconds(rng.uniform(1, 3));
                }
            } else if (nibble > 0) {
                nibble--;
                if (nibble == 0) eat();
            } else if (pause > 0) pause--;
            else {
                if (target == null || !eco.plants.contains(target)) choose();
                if (target == null) meals = 0;
                else {
                    hop = true;
                    hopping++;
                    boolean arrived = go((14 + 14 * g.d("speed")) * (sick() ? 0.5 : 1), target.x + (x < target.x ? -5 : 5), target.y);
                    if (arrived) nibble = seconds(rng.uniform(4, 12));
                    else if (hopping > seconds(0.6)) {
                        hopping = 0;
                        pause = seconds(rng.uniform(0.4, 2.5));
                    }
                }
            }
            int lift = hop && (eco.frameNo & 1) != 0 ? 1 : 0;
            if (nibble != 0 && eco.frameNo % 6 < 2) lift = -1; // the head dips to eat
            body(overlay, hop ? "hop" : "sit", x, y - lift, dir < 0, true);
            return true;
        }

        private void choose() {
            List<Eco.Plant> options = new ArrayList<>();
            for (Eco.Plant p : eco.plants)
                if ((p.kind.equals("clover") || p.kind.equals("spire") || p.kind.equals("fern")) && p.shown > 6) options.add(p);
            if (options.isEmpty()) { target = null; return; }
            Collections.sort(options, new Comparator<Eco.Plant>() {
                @Override public int compare(Eco.Plant a, Eco.Plant b) {
                    return Math.abs(a.x - x) + Math.abs(a.y - y) * 2 - Math.abs(b.x - x) - Math.abs(b.y - y) * 2;
                }
            });
            target = rng.choice(options.subList(0, Math.min(6, options.size())));
        }

        private void eat() {
            Eco.Plant p = target;
            meals--;
            target = null;
            if (eco.plants.contains(p)) {
                double bite = p.g.d("toxin", 0.0) - g.d("tolerance");
                if (bite > 0.25) { // one mouthful of that was enough
                    fed(-0.3 * bite);
                    meals = 0;
                    wild.note("rabbit put off by a toxic plant");
                    return;
                }
                fed(0.25);
                eco.unshow(p, Math.max(3, (int) (p.shown * 0.6)));
                eco.restamp(p);
                if (seedKind != null) {
                    int sx = x + rng.randint(-6, 6), sy = y + rng.randint(-2, 2);
                    eco.sprout(seedKind, sx, sy, eco.mutatePlant(seedG, null), seedGen + 1, false, false);
                }
                seedKind = p.kind;
                seedG = p.g;
                seedGen = p.gen;
            }
            wild.enrich(x, y, 0.05); // what goes in comes out
            pause = seconds(rng.uniform(0.5, 3));
        }
    }

    public static final class Fox extends Walker {
        String state = "enter";
        int timer, step;
        int[] spot;
        Rabbit prey;
        Frog frog;

        Fox(Wildlife wild, Animal ind) {
            super("fox", 7, wild, ind, null);
            timer = seconds(rng.uniform(10, 20));
        }

        @Override boolean frame(Overlay overlay) {
            boolean moving = true, drink = false;
            Rabbit nearest = null;
            int nearestD = 0;
            for (Creature v : wild.visitors) {
                if (v instanceof Rabbit && !((Rabbit) v).gone) {
                    Rabbit r = (Rabbit) v;
                    int d = Math.abs(r.x - x) + Math.abs(r.y - y);
                    if (nearest == null || d < nearestD) { nearest = r; nearestD = d; }
                }
            }
            if (!state.equals("leave") && !state.equals("hunt") && nearest != null) {
                prey = nearest;
                state = "hunt";
                timer = seconds(50);
            } else if (state.equals("wander") && g.d("diet") > 0.6 && !wild.frogs.isEmpty()) {
                // This line of foxes has taken to frogs.
                Frog f = rng.choice(wild.frogs);
                if (f.swimLeft == 0) {
                    spot = new int[]{(int) f.x, (int) f.y};
                    frog = f;
                    state = "stalk_frog";
                    timer = seconds(40);
                }
            }
            if (state.equals("stalk_frog")) {
                timer--;
                if (!wild.frogs.contains(frog) || frog.swimLeft != 0 || timer <= 0) state = "leave";
                else if (go(10 + 22 * g.d("speed"), (int) frog.x - half, (int) frog.y)) {
                    wild.take(new Prey("frog", (int) frog.x, (int) frog.y, frog));
                    fed(0.8);
                    state = "leave";
                }
            } else if (state.equals("hunt")) {
                Rabbit r = prey;
                timer--;
                if (r == null || r.gone || !wild.visitors.contains(r) || timer <= 0) state = "leave";
                else {
                    int d = Math.abs(r.x - x) + Math.abs(r.y - y);
                    go(d < 55 ? 24 + 24 * g.d("speed") : 8, r.x, r.y); // a slow stalk, then the rush
                    if (d < 5) {
                        r.gone = true;
                        wild.evo.kill("rabbit", r.ind);
                        fed(1.4);
                        wild.enrich(r.x, r.y, 0.12);
                        wild.note("rabbit caught by fox");
                        state = "leave";
                    }
                }
            } else if (state.equals("enter")) {
                double[] bank = wild.bank(-Math.PI / 2 + rng.uniform(-0.7, 0.7));
                spot = new int[]{(int) bank[0], (int) bank[1] - 2};
                state = rng.random() < 0.6 ? "to_pond" : "wander";
            } else if (state.equals("to_pond")) {
                timer--;
                if (go(11, spot[0] - half, spot[1])) {
                    state = "drink";
                    timer = seconds(rng.uniform(6, 14));
                } else if (timer <= 0) state = "leave";
            } else if (state.equals("drink")) {
                moving = false;
                drink = true;
                timer--;
                if (Clock.mod(timer, Math.max(1, seconds(2.2))) == 0) wild.ripple(x + (dir > 0 ? half * 2 : 0), y + 4);
                if (timer <= 0) state = "leave";
            } else if (state.equals("wander")) {
                timer--;
                if (spot == null || go(9, spot[0], spot[1])) {
                    moving = false;
                    if (rng.random() < 0.04) spot = eco.randomGround();
                }
                if (timer <= 0) state = "leave";
            } else if (goOff(13)) return false;
            if (moving) step++;
            body(overlay, drink ? "drink" : "walk" + (step / 2) % 2, x, y, dir < 0, true);
            return true;
        }
    }

    static final class Hedgehog extends Walker {
        int life, rest, step;
        int[] spot;

        Hedgehog(Wildlife wild, Animal ind) {
            super("hedgehog", 3, wild, ind, null);
            life = seconds(rng.uniform(180, 320));
            spot = eco.randomGround();
        }

        @Override boolean frame(Overlay overlay) {
            life--;
            if (life <= 0) {
                if (goOff(5)) return false;
                step++;
            } else if (rest > 0) rest--;
            else {
                Prey prey = wild.nearestPrey(x, y, 55, false, true);
                if (prey != null) spot = new int[]{prey.x, prey.y};
                if (go(4, spot[0], spot[1])) {
                    if (prey != null && Math.abs(prey.x - x) < 5) {
                        wild.take(prey);
                        fed(0.25);
                        rest = seconds(rng.uniform(3, 7));
                    } else {
                        spot = eco.randomGround();
                        rest = seconds(rng.uniform(1, 5));
                    }
                }
                step++;
            }
            body(overlay, "walk" + (step / 3) % 2, x, y, dir < 0, true);
            return true;
        }
    }

    static final class Squirrel extends Walker {
        int pause, dig, burst, errands, patience;
        int[] spot;
        boolean leavingSet, upATree;

        Squirrel(Wildlife wild, Animal ind) {
            super("squirrel", 3, wild, ind, null);
            errands = rng.randint(1, 3);
            spot = pickSpot();
            patience = seconds(30);
        }

        private int[] pickSpot() {
            List<Eco.Plant> groves = new ArrayList<>();
            for (Eco.Plant p : eco.plants) if (p.kind.equals("sapling") || p.kind.equals("log")) groves.add(p);
            if (!groves.isEmpty() && rng.random() < 0.6) {
                Eco.Plant p = rng.choice(groves);
                int sx = p.x + rng.randint(-30, 30), sy = p.y + rng.randint(-8, 8);
                if (0 <= sx && sx < eco.w && eco.gy + 3 <= sy && sy < eco.h && eco.s.ground[sy * eco.w + sx] != 0) return new int[]{sx, sy};
            }
            return eco.randomGround();
        }

        @Override boolean frame(Overlay overlay) {
            String pose = "run";
            if (errands <= 0) {
                if (!leavingSet) {
                    leavingSet = true;
                    upATree = rng.random() < 0.5;
                }
                if (pause > 0) pause--;
                else if (upATree) {
                    wild.pending.add(new Climbers.ClimbingSquirrel(wild, ind));
                    return false;
                } else if (goOff(30)) return false;
                else if (rng.random() < 0.03) pause = seconds(rng.uniform(0.4, 1.5));
            } else if (dig > 0) {
                dig--;
                pose = "dig";
                if ((eco.frameNo & 1) != 0) {
                    int i = y * eco.w + x + (dir > 0 ? 7 : -2) + rng.randint(-1, 1);
                    if (0 <= i && i < eco.w * eco.h && eco.c.fg[i] == 0) overlay.put(i, LITTER.at(3));
                }
                if (dig == 0) {
                    wild.bury(x, y);
                    errands--;
                    spot = pickSpot();
                }
            } else if (pause > 0) pause--;
            else {
                patience--;
                if (patience <= 0) { // can't get there; try somewhere else, or give up
                    errands--;
                    spot = pickSpot();
                    patience = seconds(30);
                }
                if (go(34, spot[0], spot[1])) {
                    dig = seconds(rng.uniform(3, 6));
                    patience = seconds(30);
                }
                burst++;
                if (burst > seconds(rng.uniform(0.5, 1.4))) {
                    burst = 0;
                    pause = seconds(rng.uniform(0.3, 2));
                }
            }
            body(overlay, pose, x, y, dir < 0, true);
            return true;
        }
    }

    /** Moves in a straight, bobbing line between points. */
    public abstract static class Flyer extends Creature {
        double x, y;
        int dir;
        Flyer(String name, Wildlife wild, Animal ind) { super(name, wild, ind); }
        boolean flyTo(double tx, double ty, double speed) {
            double dx = tx - x, dy = ty - y, d = Math.hypot(dx, dy);
            double step = speed / Math.max(1, eco.fps);
            if (d <= step) {
                x = tx;
                y = ty;
                return true;
            }
            if (sign(dx) != 0) dir = sign(dx);
            x += dx / d * step;
            y += dy / d * step;
            return false;
        }
    }

    static final class Songbird extends Flyer {
        int visits, sit, seedGen;
        Eco.Plant plant;
        double perchX, perchY;
        String seedKind;
        G seedG;

        Songbird(Wildlife wild, Animal ind) {
            super("songbird", wild, ind);
            dir = rng.choice(-1, 1);
            x = dir > 0 ? -6.0 : eco.w + 6.0;
            y = rng.uniform(eco.h * 0.1, eco.gy);
            visits = rng.randint(2, 4);
            next();
        }

        private void next() {
            List<Eco.Plant> options = new ArrayList<>(), favoured = new ArrayList<>();
            for (Eco.Plant p : eco.plants) {
                if ((p.kind.equals("shrub") || p.kind.equals("sapling") || p.kind.equals("spire") || p.kind.equals("fern")) && p.shown > 8) {
                    options.add(p);
                    if (p.kind.equals("shrub")) favoured.add(p);
                }
            }
            if (visits <= 0 || options.isEmpty()) {
                plant = null;
                perchX = x < eco.w / 2.0 ? -8.0 : eco.w + 8.0;
                perchY = rng.uniform(0, eco.gy);
                return;
            }
            plant = rng.choice(!favoured.isEmpty() && rng.random() < 0.6 ? favoured : options);
            int[] top = null;
            for (int k = 0; k < plant.shown; k++) {
                int[] d = plant.pix.get(k);
                if (top == null || d[1] < top[1]) top = d;
            }
            perchX = plant.x + top[0];
            perchY = plant.y + top[1] - 1;
        }

        @Override boolean frame(Overlay overlay) {
            boolean flying = sit <= 0;
            if (sit > 0) {
                sit--;
                if (!eco.plants.contains(plant)) sit = 0;
                if (sit == 0) {
                    peck();
                    visits--;
                    next();
                }
            } else if (flyTo(perchX, perchY, 42)) {
                if (plant == null) return false;
                sit = seconds(rng.uniform(6, 22));
                if (seedKind != null) {
                    int sx = (int) x + rng.randint(-14, 14), sy = plant.y + rng.randint(-5, 5);
                    eco.sprout(seedKind, sx, sy, eco.mutatePlant(seedG, null), seedGen + 1, false, false);
                    seedKind = null;
                }
            }
            String pose;
            int fy;
            if (flying) {
                pose = "fly" + (eco.frameNo & 1);
                fy = (int) (y + Math.sin(eco.frameNo * 0.9) * 1.5);
            } else {
                pose = "sit";
                fy = (int) y + (eco.frameNo % 11 < 2 ? 1 : 0); // pecking
            }
            body(overlay, pose, (int) x - 2, fy, dir < 0, false);
            return true;
        }

        /** Eat the fruit or flower (always the last pixels a plant grew) and keep the seed. */
        private void peck() {
            Eco.Plant p = plant;
            if (p != null && eco.plants.contains(p)) {
                eco.unshow(p, Math.max(4, (int) (p.shown * 0.9)));
                eco.restamp(p);
                seedKind = p.kind;
                seedG = p.g;
                seedGen = p.gen;
                fed(0.2 * (1 - g.d("diet")));
            }
            Prey prey = wild.nearestPrey(x, y, 28, false, false);
            if (prey != null && rng.random() < g.d("diet")) {
                wild.take(prey);
                fed(0.25);
            }
        }
    }

    static final class OwlHunt extends Flyer {
        final int homeX, homeY;
        Prey prey;
        boolean down = true;
        int wait;

        OwlHunt(Wildlife wild, Animal ind) {
            super("owl", wild, ind);
            homeX = eco.s.owlX;
            homeY = eco.s.owlY;
            x = homeX;
            y = homeY;
            dir = 1;
            prey = wild.mostVisiblePrey();
            wild.owlAway = true;
        }

        @Override boolean frame(Overlay overlay) {
            if (down) {
                if (prey == null || !wild.preyAlive(prey)) down = false;
                else if (flyTo(prey.x, prey.y - 3, 46)) {
                    wild.take(prey);
                    fed(0.3);
                    down = false;
                    wait = seconds(0.6);
                }
            } else if (wait > 0) wait--;
            else if (flyTo(homeX, homeY, 34)) {
                wild.owlAway = false;
                eco.owlOn = true;
                return false;
            }
            body(overlay, "fly" + ((eco.frameNo / 2) & 1), (int) x - 4, (int) y, dir < 0, false);
            return true;
        }
    }

    static final class Bats extends Creature {
        static final class Bat { double x, y, vx, vy; Animal ind; }
        int life;
        final List<Bat> flock = new ArrayList<>();

        Bats(Wildlife wild, List<Animal> inds) {
            super("bats", wild, inds.get(0));
            life = seconds(rng.uniform(60, 170));
            for (Animal one : inds) {
                Bat b = new Bat();
                b.x = rng.uniform(0, eco.w);
                b.y = rng.uniform(eco.h * 0.08, eco.gy);
                b.vx = rng.uniform(-40, 40);
                b.vy = rng.uniform(-14, 14);
                b.ind = one;
                flock.add(b);
            }
        }

        @Override boolean frame(Overlay overlay) {
            life--;
            int fps = Math.max(1, eco.fps);
            boolean alive = false;
            for (Bat b : flock) {
                if (rng.random() < 0.18) { // bats turn on a whim
                    b.vx += rng.uniform(-34, 34);
                    b.vy += rng.uniform(-22, 22);
                }
                for (int k = 0; k < eco.moths.size(); k++) {
                    Eco.Moth m = eco.moths.get(k);
                    double dx = m.x - b.x, dy = m.y - b.y;
                    if (Math.abs(dx) < 34 && Math.abs(dy) < 22) {
                        b.vx += sign(dx) * 7;
                        b.vy += sign(dy) * 5;
                        if (Math.abs(dx) < 3 && Math.abs(dy) < 3) {
                            eco.moths.remove(k);
                            b.ind.e = Math.min(3.0, b.ind.e + 0.3);
                            wild.note("moth taken by bats");
                        }
                        break;
                    }
                }
                double speed = Math.hypot(b.vx, b.vy);
                if (speed > 52) {
                    b.vx *= 52 / speed;
                    b.vy *= 52 / speed;
                }
                b.x += b.vx / fps;
                b.y += b.vy / fps;
                double low = eco.gy + eco.span * 0.3;
                if (b.y < eco.h * 0.05) b.vy = Math.abs(b.vy);
                else if (b.y > low) b.vy = -Math.abs(b.vy);
                if (life > 0) {
                    if (b.x < 0) b.vx = Math.abs(b.vx);
                    else if (b.x > eco.w) b.vx = -Math.abs(b.vx);
                }
                if (-8 <= b.x && b.x <= eco.w + 8) {
                    alive = true;
                    g = b.ind.g;
                    body(overlay, "fly" + ((eco.frameNo + (int) b.y) & 1), (int) b.x - 2, (int) b.y, false, false);
                }
            }
            return life > 0 || alive;
        }
    }

    static final class Wisp extends Creature {
        int life;
        double t;

        Wisp(Wildlife wild) {
            super("wisp", wild, null);
            life = seconds(rng.uniform(70, 170));
            t = rng.uniform(0, 6.28);
        }

        @Override boolean frame(Overlay overlay) {
            life--;
            t += 0.05 / Math.max(1, eco.fps) * 8;
            Scene s = eco.s;
            int x = (int) (s.pondCx + Math.cos(t * 0.31) * s.pondRx * 1.1 + Math.sin(t * 0.73) * 7);
            int y = (int) (s.pondCy + Math.sin(t * 0.47) * s.pondRy * 1.4 - 5 + Math.sin(t * 1.3) * 2);
            int w = eco.w, h = eco.h;
            int i = y * w + x;
            if (life > 0 && 3 < x && x < w - 3 && 3 < y && y < h - 3) {
                Ramp ramp = MYC[0];
                boolean flicker = rng.random() < 0.12;
                overlay.put(i, flicker ? ramp.at(3) : ramp.at(4));
                overlay.put(i - 1, ramp.at(3));
                overlay.put(i + 1, ramp.at(3));
                overlay.put(i - w, ramp.at(3));
                overlay.put(i + w, ramp.at(3));
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        int k = i + dy * w + dx;
                        if (k >= 0 && k < w * h && !overlay.has(k) && dx * dx + dy * dy * 2 < 16 && eco.c.fg[k] == 0) overlay.put(k, LIT2[eco.c.frame[k]]);
                    }
                }
            }
            // Moths come to it, fireflies answer it, and the threads beneath it wake up.
            for (Eco.Moth m : eco.moths) {
                if (Math.abs(m.x - x) < 70 && Math.abs(m.y - y) < 40) {
                    m.x += sign(x - m.x) * 0.35;
                    m.y += sign(y - m.y) * 0.2;
                }
            }
            if (eco.frameNo % Math.max(1, seconds(2)) == 0) {
                for (Eco.Fly f : eco.flies) if (Math.abs(f.x - x) < 40 && Math.abs(f.y - y) < 25) f.phase = 0.0;
                for (int dy = -12; dy <= 12; dy += 3) {
                    for (int dx = -24; dx <= 24; dx += 3) {
                        int owner = eco.node((y + dy) * w + x + dx);
                        Eco.Colony col = owner != 0 ? eco.colonyById.get(owner) : null;
                        if (col != null) col.energy += 0.05;
                    }
                }
            }
            return life > 0;
        }
    }

    static final class ShootingStar extends Creature {
        double x, y, vx, vy;
        int life;

        ShootingStar(Wildlife wild) {
            super("star", wild, null);
            x = eco.s.moonX + rng.uniform(-110, 110) * eco.scaleS;
            y = rng.uniform(eco.h * 0.05, eco.h * 0.2);
            int side = rng.choice(-1, 1);
            vx = side * rng.uniform(90, 150);
            vy = rng.uniform(40, 80);
            life = seconds(rng.uniform(0.8, 1.5));
        }

        @Override boolean frame(Overlay overlay) {
            life--;
            int fps = Math.max(1, eco.fps);
            x += vx / fps;
            y += vy / fps;
            int n = (int) (Math.hypot(vx, vy) / fps) + 4;
            double ux = vx / fps / n, uy = vy / fps / n;
            int w = eco.w, h = eco.gy;
            for (int k = 0; k < n; k++) {
                int px = (int) (x - ux * k), py = (int) (y - uy * k);
                if (0 <= px && px < w && 0 <= py && py < h) {
                    int i = py * w + px, v = eco.c.comp[i];
                    // Seen only through gaps in the leaves.
                    if ((v >= SKY.start && v < SKY.start + SKY.n) || (v >= STAR.start && v < STAR.start + STAR.n))
                        overlay.put(i, k < 2 ? STAG.at(3) : k < n / 2 ? STAG.at(1) : STAG.at(0));
                }
            }
            return life > 0;
        }
    }

    static final class Wind extends Creature {
        static final class Bit { double x, y, v, ph; int c; }
        int life, dir;
        final int total;
        final List<Bit> bits = new ArrayList<>();
        boolean sown;

        Wind(Wildlife wild) {
            super("wind", wild, null);
            life = seconds(rng.uniform(14, 26));
            dir = rng.choice(-1, 1);
            total = life;
        }

        private Bit bit() {
            Bit b = new Bit();
            b.x = dir > 0 ? -2.0 : eco.w + 2.0;
            b.y = rng.uniform(eco.gy - eco.span * 0.45, eco.h - 14);
            b.v = rng.uniform(24, 46);
            b.ph = rng.uniform(0, 6.28);
            b.c = rng.choice(LITTER.at(3), LITTER.at(4), LITTER.at(2), SPORE.at(1), FLOWER[3].at(1));
            return b;
        }

        @Override boolean frame(Overlay overlay) {
            life--;
            int fps = Math.max(1, eco.fps);
            double strength = Math.min(1.0, Math.min(life, total - life) / (double) Math.max(1, seconds(3)));
            if (life > 0 && bits.size() < 26 * strength && rng.random() < 0.5) bits.add(bit());
            int w = eco.w, h = eco.h;
            for (Bit b : new ArrayList<>(bits)) {
                b.x += dir * b.v / fps;
                b.ph += 0.5;
                int y = (int) (b.y + Math.sin(b.ph) * 2.2), x = (int) b.x;
                if (x < 0 || x >= w) {
                    bits.remove(b);
                    continue;
                }
                int i = y * w + x;
                if (0 <= i && i < w * h && eco.c.fg[i] == 0) overlay.put(i, b.c);
            }
            // Everything light gets carried along.
            for (Eco.Moth m : eco.moths) {
                double v = m.x + dir * 0.5 * strength;
                m.x = v - eco.w * Math.floor(v / eco.w);
            }
            for (Eco.Spore s : eco.spores) s.vx += dir * 0.01;
            if (!sown && life < total / 2) {
                sown = true;
                sow();
            }
            return life > 0 || !bits.isEmpty();
        }

        private void sow() {
            List<Eco.Plant> mature = new ArrayList<>();
            for (Eco.Plant p : eco.plants)
                if ((p.kind.equals("clover") || p.kind.equals("fern") || p.kind.equals("spire") || p.kind.equals("shrub")) && p.shown == p.pix.size()) mature.add(p);
            for (int k = 0; k < 3; k++) {
                if (!mature.isEmpty()) {
                    Eco.Plant p = rng.choice(mature);
                    int x = p.x + dir * rng.randint(60, 170), y = p.y + rng.randint(-14, 14);
                    eco.sprout(p.kind, x, y, eco.mutatePlant(p.g, p.pollen), p.gen + 1, false, false);
                }
            }
            for (int k = 0; k < 6; k++) {
                int[] at = eco.randomGround();
                eco.addLitter(at[0], at[1]);
            }
        }
    }

    // Happenings that aren't animals: name, weight, lowest daylight, highest daylight.
    // Animals aren't listed: who appears, and when, follows from who is alive and when their genes keep them awake.
    static final Object[][] HAPPENINGS = {{"wisp", 2, 0.0, 0.1}, {"star", 4, 0.0, 0.15}, {"wind", 2, 0.0, 1.0}, {"ring", 1, 0.0, 1.0}};
    static final String[] ANIMALS = {"rabbit", "fox", "hedgehog", "owl", "bats", "songbird", "squirrel"};

    public static final class Frog extends Animal {
        double x, y, a, sx, sy;
        int wait, swimLeft, sd;
        double[] hop; // from x, from y, to x, to y, step, steps, into the water
        int[] tongue;
    }

    public static final class Snail extends Animal {
        double x, y, d;
        int left, chew;
        final IntList trail = new IntList();
        Eco.Plant target;
    }

    static final class Prey {
        final String kind;
        final int x, y;
        final Animal obj;
        Prey(String kind, int x, int y, Animal obj) { this.kind = kind; this.x = x; this.y = y; this.obj = obj; }
    }

    static final class Cache { int x, y, due; boolean sprouts; }

    final Eco eco;
    final PyRandom rng;
    public List<Creature> visitors = new ArrayList<>();
    final List<Creature> pending = new ArrayList<>(); // those arriving as a result of something a visitor just did
    public final List<Frog> frogs = new ArrayList<>();
    public final List<Snail> snails = new ArrayList<>();
    final List<double[]> ripples = new ArrayList<>();
    final List<Cache> caches = new ArrayList<>(); // buried nuts
    boolean owlAway;
    int nextEvent;
    public final Evolution evo;
    public final Oddities odd;

    Wildlife(Eco eco) {
        this.eco = eco;
        this.rng = eco.rng;
        nextEvent = rng.randint(120, 500);
        evo = new Evolution(eco);
        odd = new Oddities(this);
    }

    // ---- things creatures do to the world ----------------------------------

    void enrich(int x, int y, double amount) {
        if (0 <= x && x < eco.w && eco.gy <= y && y < eco.h) {
            int soil = eco.cell(x, y);
            eco.nut[soil] = Math.min(1.0, eco.nut[soil] + amount);
        }
    }

    @SuppressWarnings("unchecked")
    void note(String what) {
        Map<String, Object> seen = (Map<String, Object>) eco.stats.get("sightings");
        Object was = seen.get(what);
        seen.put(what, (was == null ? 0.0 : Json.num(was)) + 1);
    }

    Creature find(String name) {
        for (Creature v : visitors) if (v.name.equals(name)) return v;
        return null;
    }

    void ripple(int x, int y) {
        if (ripples.size() < 8 && 0 <= y && y < eco.h && 0 <= x && x < eco.w) ripples.add(new double[]{x, y, 0.0});
    }

    /** Is this at the water, allowing a little margin? Reads the real shore, bays and all. */
    boolean inPond(int x, int y) {
        int[] water = eco.s.water;
        int w = eco.w, h = eco.h;
        int[][] offs = {{0, 0}, {-5, 0}, {5, 0}, {0, 2}, {0, -2}};
        for (int[] o : offs) {
            int px = x + o[0], py = y + o[1];
            if (0 <= px && px < w && 0 <= py && py < h && water[py * w + px] != 0) return true;
        }
        return false;
    }

    /** The next row for someone walking at (x, y): their wish, unless water lies ahead. */
    int steer(int x, int y, int wantedY, int heading) {
        int ahead = x + heading * 26;
        if (inPond(ahead, wantedY) || inPond(x, wantedY)) wantedY = y + (y <= eco.s.pondCy ? -1 : 1);
        return Math.max(eco.gy + 2, Math.min(eco.h - 2, wantedY));
    }

    /** The closest small creature. */
    Prey nearestPrey(double x, double y, double reach, boolean withFrogs, boolean withSnails) {
        Prey best = null;
        double bestD = reach;
        withFrogs = withFrogs && !evo.asleep("frog");
        withSnails = withSnails && !evo.asleep("snail");
        if (!evo.asleep("beetle")) {
            for (Eco.Beetle b : eco.beetles) {
                double d = Math.abs(b.x - x) + Math.abs(b.y - y);
                if (d < bestD) { best = new Prey("beetle", b.x, b.y, b); bestD = d; }
            }
        }
        if (withSnails) {
            for (Snail s : snails) {
                double d = Math.abs(s.x - x) + Math.abs(s.y - y);
                if (d < bestD) { best = new Prey("snail", (int) s.x, (int) s.y, s); bestD = d; }
            }
        }
        if (withFrogs) {
            for (Frog f : frogs) {
                double d = Math.abs(f.x - x) + Math.abs(f.y - y);
                if (d < bestD && f.swimLeft == 0) { best = new Prey("frog", (int) f.x, (int) f.y, f); bestD = d; }
            }
        }
        return best;
    }

    /** Owls take what shows up best against the ground, so camouflage genuinely pays. */
    Prey mostVisiblePrey() {
        Prey best = null;
        double bestScore = 0.0;
        Evolution.Env env = evo.env();
        String[] kinds = {"beetle", "snail", "frog"};
        for (String kind : kinds) {
            if (evo.asleep(kind)) continue;
            for (Animal member : evo.group(kind)) {
                if (member.swim || member.g == null) continue;
                double score = evo.detect(member.g, env) * rng.random();
                if (score > bestScore) {
                    int mx, my;
                    if (member instanceof Eco.Beetle) { mx = ((Eco.Beetle) member).x; my = ((Eco.Beetle) member).y; }
                    else if (member instanceof Snail) { mx = (int) ((Snail) member).x; my = (int) ((Snail) member).y; }
                    else { mx = (int) ((Frog) member).x; my = (int) ((Frog) member).y; }
                    best = new Prey(kind, mx, my, member);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    boolean preyAlive(Prey prey) {
        switch (prey.kind) {
            case "beetle": return eco.beetles.contains(prey.obj);
            case "snail": return snails.contains(prey.obj);
            default: return frogs.contains(prey.obj);
        }
    }

    /** A predator feeds. The prey's substance returns to the soil around it. */
    void take(Prey prey) {
        if (!preyAlive(prey)) return;
        switch (prey.kind) {
            case "beetle": eco.beetles.remove(prey.obj); break;
            case "snail": snails.remove(prey.obj); break;
            default: frogs.remove(prey.obj);
        }
        enrich(prey.x, prey.y, 0.04);
        note(prey.kind + " eaten");
    }

    /** A squirrel's nut. Squirrels forget about a third of them, which is how oaks travel. */
    void bury(int x, int y) {
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -2; dx <= 2; dx++)
                eco.mossSet(Clock.div(x, 2) + dx, Clock.div(y - eco.gy, 2) + dy, 0);
        Cache cache = new Cache();
        cache.x = x;
        cache.y = y;
        cache.due = eco.tickNo + rng.randint(1500, 7000);
        cache.sprouts = rng.random() < 0.35;
        caches.add(cache);
        enrich(x, y, 0.03);
    }

    /** A well-fed colony fruits all at once, in a circle around where it began. */
    boolean fairyRing() {
        Eco.Colony col = null;
        double best = 0;
        for (Eco.Colony k : eco.colonies) {
            if (k.nodes.size - k.head > 150) {
                double v = k.energy + rng.random();
                if (col == null || v > best) { col = k; best = v; }
            }
        }
        if (col == null) return false;
        int cx = 0, cy = 0;
        double r = 0;
        boolean found = false;
        for (int k = 0; k < 12 && !found; k++) {
            int p = col.nodes.a[rng.randrange(col.head, col.nodes.size)];
            cx = p % eco.w;
            cy = p / eco.w;
            r = rng.uniform(16, 30) * (0.6 + (cy - eco.gy) / (double) eco.span * 0.8);
            found = eco.gy + r * 0.5 + 4 < cy && cy < eco.h - r * 0.5 - 4 && r < cx && cx < eco.w - r && !inPond(cx, cy);
        }
        if (!found) return false;
        G g = col.g;
        int n = rng.randint(10, 15);
        double offset = rng.uniform(0, 6.28);
        for (int k = 0; k < n; k++) {
            double a = offset + k / (double) n * 2 * Math.PI + rng.uniform(-0.1, 0.1);
            int x = (int) (cx + Math.cos(a) * r), y = (int) (cy + Math.sin(a) * r * 0.45);
            G genes = new G().with("hue", g.d("cap")).with("shape", g.d("shape")).with("cluster", 0).with("size", Math.min(g.d("size"), 0.6))
                    .with("slender", g.d("slender")).with("tone", 0).with("bushy", 0).with("bloom", 0).with("wet", 0.5).with("dark", 0.5);
            Eco.Plant plant = eco.sprout("mushroom", x, y, genes, col.gen, true, true);
            if (plant != null) plant.colony = col.cid;
        }
        col.energy *= 0.6;
        return true;
    }

    // ---- scheduling --------------------------------------------------------

    /** Bring something on. Forced, whoever is around comes whatever the hour; if none are, strangers do. */
    public boolean summon(String name, boolean force) {
        if (name.equals("stag")) {
            eco.summonStag(force);
            return true;
        }
        if (name.equals("ring")) {
            boolean ok = fairyRing();
            if (ok) note("ring");
            return ok;
        }
        if (Oddities.isOddVisitor(name)) return odd.summon(name, force);
        Climate climate = eco.climate;
        switch (name) {
            case "snow": climate.snowing = Math.max(climate.snowing, 1500); return true;
            case "rain": eco.raining = Math.max(eco.raining, 600); return true;
            case "storm": climate.startStorm(); return true;
            case "fog": climate.fog = 3000; return true;
            case "aurora": climate.aurora = 2500; odd.sighted("aurora"); return true;
            case "rainbow": climate.rainbow = 900; odd.sighted("rainbow"); return true;
            case "frog": addFrog(); return true;
            case "snail": addSnail(); return true;
            case "rabbit": {
                // The bigger the warren, the bigger the party.
                int count = rng.randint(1, 2 + evo.pools.get("rabbit").size() / 6);
                List<Animal> inds = evo.pick("rabbit", Math.min(count, 6), force);
                if (inds.isEmpty()) return false;
                int y = eco.gy + (int) (eco.span * rng.uniform(0.2, 0.75));
                for (int k = 0; k < inds.size(); k++) {
                    Rabbit rabbit = new Rabbit(this, inds.get(k), y + k * 4, k * eco.fps * 3);
                    visitors.add(rabbit);
                    odd.onRabbitSeen(rabbit);
                }
                note("rabbit");
                return true;
            }
        }
        if (find(name) != null) return false;
        Creature happening = null;
        switch (name) {
            case "wisp": happening = new Wisp(this); break;
            case "star": happening = new ShootingStar(this); break;
            case "wind": happening = new Wind(this); break;
        }
        if (happening != null) {
            visitors.add(happening);
            note(name);
            return true;
        }
        if (name.equals("owl") && mostVisiblePrey() == null) return false;
        if (!Arrays.asList(ANIMALS).contains(name)) return false;
        List<Animal> inds = evo.pick(name, name.equals("bats") ? 5 : 1, force);
        if (inds.isEmpty()) return false;
        Creature v;
        switch (name) {
            case "fox": v = new Fox(this, inds.get(0)); break;
            case "hedgehog": v = new Hedgehog(this, inds.get(0)); break;
            case "squirrel": v = new Squirrel(this, inds.get(0)); break;
            case "songbird": v = new Songbird(this, inds.get(0)); break;
            case "owl": v = new OwlHunt(this, inds.get(0)); break;
            default: v = new Bats(this, inds);
        }
        visitors.add(v);
        note(name);
        return true;
    }

    /** Common animals are seen often and rare ones rarely, whichever those turn out to be. */
    private String pickEvent() {
        double daylight = eco.daylight;
        List<String> options = new ArrayList<>();
        for (Object[] h : HAPPENINGS) {
            String name = (String) h[0];
            if ((Double) h[2] <= daylight && daylight <= (Double) h[3] && find(name) == null)
                for (int k = 0; k < (Integer) h[1]; k++) options.add(name);
        }
        for (String name : ANIMALS) {
            int awake = evo.awakeCount(name);
            if (awake != 0 && find(name) == null)
                for (int k = 0, n = 1 + (int) Math.pow(awake, 0.8); k < n; k++) options.add(name);
        }
        return options.isEmpty() ? null : rng.choice(options);
    }

    void tick() {
        if (!eco.cfg.fauna) return;
        nextEvent--;
        if (nextEvent <= 0) {
            nextEvent = rng.randint(300, 1100);
            if (visitors.size() < 3) {
                String name = pickEvent();
                if (name != null) summon(name, false);
            }
        }
        for (Cache cache : new ArrayList<>(caches)) {
            if (eco.tickNo >= cache.due) {
                caches.remove(cache);
                if (cache.sprouts) {
                    Eco.Plant tree = null;
                    for (Eco.Plant p : eco.plants) if (p.kind.equals("sapling")) { tree = p; break; }
                    G genes = tree != null ? eco.mutatePlant(tree.g, null) : eco.randomGenes("sapling");
                    if (eco.sprout("sapling", cache.x, cache.y, genes, tree != null ? tree.gen + 1 : 0, true, false) != null) note("forgotten nut sprouted");
                }
            }
        }
        // Rabbits out in the open don't go unnoticed for long.
        if (rng.random() < 0.002 && find("fox") == null && find("rabbit") != null) summon("fox", false);
        frogTick();
        snailTick();
        odd.tick();
        evo.tick();
    }

    // ---- frogs -------------------------------------------------------------

    /** The shore in a direction from the pond's middle, found by walking out until the water ends. */
    double[] bank(double a) {
        Scene s = eco.s;
        int w = eco.w, h = eco.h;
        int lastX = s.pondCx, lastY = s.pondCy;
        for (int k = 1; k < 60; k++) {
            double t = k / 30.0;
            int x = (int) (s.pondCx + Math.cos(a) * s.pondRx * t), y = (int) (s.pondCy + Math.sin(a) * s.pondRy * t);
            if (!(0 <= x && x < w && 0 <= y && y < h)) break;
            if (s.water[y * w + x] != 0) { lastX = x; lastY = y; }
        }
        return new double[]{lastX + Math.cos(a) * 3, lastY + Math.sin(a) * 2};
    }

    void addFrog() { addFrog(null); }
    void addFrog(G g) {
        Frog f = new Frog();
        f.a = rng.uniform(0, 6.28);
        double[] at = bank(f.a);
        f.x = at[0];
        f.y = at[1];
        f.e = 1.0;
        f.wait = rng.randint(10, 200);
        f.g = g != null ? g : evo.residentGenes("frog");
        frogs.add(f);
    }

    private void frogTick() {
        if (evo.asleep("frog")) return; // down in the mud under the ice
        for (Frog f : new ArrayList<>(frogs)) {
            f.e -= 0.00012;
            if (f.e <= 0) {
                frogs.remove(f);
                enrich((int) f.x, (int) f.y, 0.05);
            } else if (eco.raining != 0 && f.e > 1.3 && frogs.size() < 8 && rng.random() < 0.002) {
                f.e -= 0.7;
                addFrog(evo.childOf(f.g, rng.choice(frogs).g));
                note("frog born");
            } else if (f.e > 1.5 && frogs.size() < 8 && rng.random() < 0.0004) { // they don't only breed in the rain
                f.e -= 0.7;
                addFrog(evo.childOf(f.g, rng.choice(frogs).g));
            }
        }
        if (frogs.size() < 2 && rng.random() < 0.0015) addFrog();
    }

    private void frogFrame(Overlay overlay) {
        if (evo.asleep("frog")) return;
        int w = eco.w;
        for (Frog f : frogs) {
            if (f.swimLeft > 0) {
                f.swimLeft--;
                f.swim = f.swimLeft > 0;
                // Just the eyes, drifting across the surface.
                f.sx += rng.uniform(-0.18, 0.3) * f.sd;
                int x = (int) f.sx, y = (int) f.sy;
                int i = y * w + x;
                if (i >= 0 && i < eco.s.water.length && eco.s.water[i] != 0) {
                    overlay.put(i, FROG.at(3));
                    overlay.put(i + 2, FROG.at(3));
                }
                if (f.swimLeft == 0) {
                    ripple(x, y);
                    f.a = rng.uniform(0, 6.28);
                    double[] at = bank(f.a);
                    f.x = at[0];
                    f.y = at[1];
                }
                continue;
            }
            double[] hop = f.hop;
            int lift = 0;
            if (hop != null) {
                hop[4] += 1;
                double t = hop[4] / hop[5];
                f.x = hop[0] + (hop[2] - hop[0]) * t;
                f.y = hop[1] + (hop[3] - hop[1]) * t;
                lift = (int) (Math.sin(t * Math.PI) * 4);
                if (t >= 1) {
                    f.hop = null;
                    if (hop[6] != 0) {
                        ripple((int) f.x, (int) f.y);
                        f.swimLeft = eco.fps * rng.randint(8, 30);
                        f.swim = f.swimLeft > 0;
                        f.sx = f.x;
                        f.sy = f.y;
                        f.sd = rng.choice(-1, 1);
                        continue;
                    }
                }
            } else {
                f.wait--;
                if (f.wait <= 0) {
                    boolean active = evo.awake(f.g) || eco.raining != 0;
                    f.wait = eco.fps * rng.randint(4, 40) * (active ? 1 : 4);
                    frogJump(f);
                } else if (eco.frameNo % 8 == 0 && (evo.awake(f.g) || eco.raining != 0)) frogHunt(f);
            }
            int x = (int) f.x, y = (int) f.y;
            int[] tongue = f.tongue;
            if (tongue != null) {
                tongue[2]--;
                int tx = tongue[0], ty = tongue[1];
                int n = Math.max(Math.max(Math.abs(tx - x), Math.abs(ty - (y - 2))), 1);
                for (int k = 1; k <= n; k++)
                    overlay.put((int) (y - 2 + (ty - y + 2) * k / (double) n) * w + (int) (x + (tx - x) * k / (double) n), FLOWER[3].at(1));
                if (tongue[2] <= 0) f.tongue = null;
            }
            IntList placed = stamp(eco, overlay, Sprites.animalRows("frog", lift != 0 ? "hop" : "sit", f.g), x - 2, y - lift, false,
                    Evolution.coatColors(f.g), Evolution.coatDark(f.g));
            if (f.g.d("glow") > 0.6) halo(eco, overlay, placed, LIT2);
        }
    }

    private void frogJump(Frog f) {
        boolean into = rng.random() < 0.3;
        double tx, ty;
        if (into) {
            double a = rng.uniform(0, 6.28);
            tx = eco.s.pondCx + Math.cos(a) * eco.s.pondRx * 0.55;
            ty = eco.s.pondCy + Math.sin(a) * eco.s.pondRy * 0.55;
        } else {
            f.a += rng.uniform(-0.5, 0.5);
            double[] at = bank(f.a);
            tx = at[0];
            ty = at[1];
        }
        f.hop = new double[]{f.x, f.y, tx, ty, 0, Math.max(3, into ? eco.fps : eco.fps / 2), into ? 1 : 0};
    }

    /** A flick of the tongue at anything small and close enough. */
    private void frogHunt(Frog f) {
        double reach = 8 + 8 * f.g.d("speed");
        for (int k = 0; k < eco.moths.size(); k++) {
            Eco.Moth m = eco.moths.get(k);
            if (Math.abs(m.x - f.x) < reach && Math.abs(m.y - f.y) < reach * 0.6) {
                f.tongue = new int[]{(int) m.x, (int) m.y, 2};
                f.e = Math.min(2.0, f.e + 0.5);
                note("moth eaten by frog");
                eco.moths.remove(k);
                return;
            }
        }
        for (Eco.Fly m : eco.flies) {
            if (Math.abs(m.x - f.x) < reach && Math.abs(m.y - f.y) < reach * 0.6) {
                if (rng.random() > m.bright) continue; // the dimmer ones go unnoticed
                f.tongue = new int[]{(int) m.x, (int) m.y, 2};
                f.e = Math.min(2.0, f.e + 0.5);
                note("firefly eaten by frog");
                eco.flyOffspring(m); // the survivors' young take its place
                return;
            }
        }
    }

    // ---- snails ------------------------------------------------------------

    void addSnail() {
        int[] at = eco.randomGround();
        G g;
        if (snails.size() >= 2) {
            int[] two = rng.sampleIndices(snails.size(), 2);
            g = evo.childOf(snails.get(two[0]).g, snails.get(two[1]).g);
        } else g = evo.residentGenes("snail");
        Snail s = new Snail();
        s.x = at[0];
        s.y = at[1];
        s.d = rng.uniform(0, 6.28);
        s.left = rng.randint(1500, 4500);
        s.g = g;
        snails.add(s);
        note("snail");
    }

    private void snailTick() {
        if (evo.asleep("snail")) return;
        if (snails.size() < 5 && rng.random() < (eco.raining != 0 ? 0.004 : 0.0002)) addSnail();
        for (Snail s : new ArrayList<>(snails)) {
            s.left--;
            if (s.left <= 0) {
                snails.remove(s);
                enrich((int) s.x, (int) s.y, 0.02);
                continue;
            }
            if (s.chew > 0) {
                s.chew--;
                if (s.chew == 0 && eco.plants.contains(s.target)) {
                    Eco.Plant p = s.target;
                    eco.unshow(p, Math.max(0, p.shown - 6));
                    eco.restamp(p);
                    if (p.shown <= 4) {
                        p.wilting = true;
                        s.target = null;
                    } else s.chew = 24;
                }
                continue;
            }
            if (rng.random() > 0.5) continue;
            Eco.Plant t = s.target;
            if (!eco.plants.contains(t)) {
                t = s.target = null;
                if (rng.random() < 0.03) {
                    List<Eco.Plant> shrooms = new ArrayList<>();
                    for (Eco.Plant p : eco.plants) if (p.kind.equals("mushroom") && Math.abs(p.x - s.x) < 60 && Math.abs(p.y - s.y) < 26) shrooms.add(p);
                    if (!shrooms.isEmpty()) t = s.target = rng.choice(shrooms);
                }
            }
            if (t != null) {
                s.d = Math.atan2((t.y - s.y) * 2, t.x - s.x);
                if (Math.abs(t.x - s.x) < 3 && Math.abs(t.y - s.y) < 2) {
                    s.chew = 24;
                    continue;
                }
            } else s.d += rng.gauss(0, 0.25);
            double nx = s.x + Math.cos(s.d) * 0.7, ny = s.y + Math.sin(s.d) * 0.35;
            if (0 <= (int) nx && (int) nx < eco.w && eco.gy + 2 <= (int) ny && (int) ny < eco.h - 1 && eco.s.ground[(int) ny * eco.w + (int) nx] != 0) {
                if ((int) nx != (int) s.x || (int) ny != (int) s.y) {
                    s.trail.add((int) s.y * eco.w + (int) s.x);
                    if (s.trail.size > 40) s.trail.dropFront(1);
                }
                s.x = nx;
                s.y = ny;
            } else s.d += Math.PI;
        }
    }

    private void snailFrame(Overlay overlay) {
        if (evo.asleep("snail")) return;
        int[] fg = eco.c.fg, life = eco.c.life;
        for (Snail s : snails) {
            for (int k = 0; k < s.trail.size; k++) { // the silver thread it leaves behind
                int i = s.trail.a[k];
                if (fg[i] == 0 && life[i] == 0) overlay.put(i, SPORE.at(0));
            }
            stamp(eco, overlay, Sprites.animalRows("snail", "crawl", s.g), (int) s.x - 2, (int) s.y, Math.cos(s.d) < 0, Evolution.coatColors(s.g), -1);
        }
    }

    // ---- drawing -----------------------------------------------------------

    private void rippleFrame(Overlay overlay) {
        if (eco.climate.frozen()) {
            ripples.clear();
            return;
        }
        if (eco.raining != 0 && eco.frameNo % 5 == 0) {
            double a = rng.uniform(0, 6.28), d = Math.pow(rng.random(), 0.5);
            ripple((int) (eco.s.pondCx + Math.cos(a) * eco.s.pondRx * d), (int) (eco.s.pondCy + Math.sin(a) * eco.s.pondRy * d));
        }
        int w = eco.w, h = eco.h;
        for (double[] r : new ArrayList<>(ripples)) {
            r[2] += 3.0 / Math.max(1, eco.fps);
            double rad = r[2];
            if (rad > 7) {
                ripples.remove(r);
                continue;
            }
            int n = (int) (6 + rad * 3);
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n;
                int x = (int) (r[0] + Math.cos(a) * rad), y = (int) (r[1] + Math.sin(a) * rad * 0.42);
                if (0 <= x && x < w && 0 <= y && y < h) {
                    int i = y * w + x;
                    if (eco.s.water[i] != 0) overlay.put(i, LIT2[eco.c.frame[i]]);
                }
            }
        }
    }

    void frame(Overlay overlay) {
        rippleFrame(overlay);
        snailFrame(overlay);
        frogFrame(overlay);
        if (!visitors.isEmpty()) {
            List<Creature> kept = new ArrayList<>(visitors.size());
            for (Creature v : visitors) if (v.frame(overlay)) kept.add(v);
            visitors = kept;
        }
        if (!pending.isEmpty()) {
            visitors.addAll(pending);
            pending.clear();
        }
    }

    // ---- persistence -------------------------------------------------------

    Map<String, Object> toState() {
        Map<String, Object> st = new LinkedHashMap<>();
        List<Object> savedFrogs = new ArrayList<>(), savedSnails = new ArrayList<>(), savedMoths = new ArrayList<>(), savedCaches = new ArrayList<>();
        for (Frog f : frogs) savedFrogs.add(new ArrayList<Object>(Arrays.asList(Json.round(f.x, 1), Json.round(f.y, 1), Json.round(f.a, 2), Json.round(f.e, 3), f.g)));
        for (Snail s : snails) savedSnails.add(new ArrayList<Object>(Arrays.asList(Json.round(s.x, 1), Json.round(s.y, 1), (double) s.left, s.g)));
        for (Eco.Moth m : eco.moths) savedMoths.add(new ArrayList<Object>(Arrays.asList(Json.round(m.x, 1), Json.round(m.y, 1), m.g, Json.round(m.e, 3))));
        for (Cache c : caches) savedCaches.add(new ArrayList<Object>(Arrays.asList((double) c.x, (double) c.y, (double) c.due, c.sprouts)));
        st.put("frogs", savedFrogs);
        st.put("snails", savedSnails);
        st.put("moths", savedMoths);
        st.put("caches", savedCaches);
        st.put("next", (double) nextEvent);
        st.put("evo", evo.toState());
        return st;
    }

    @SuppressWarnings("unchecked")
    void load(Map<String, Object> st) {
        if (st == null) { // a world from before there were frogs: some arrive now
            evo.load(null);
            for (int k = 0; k < 3; k++) addFrog();
            return;
        }
        nextEvent = Json.integer(st.get("next"));
        caches.clear();
        for (Object o : (List<Object>) st.get("caches")) {
            List<Object> row = (List<Object>) o;
            Cache c = new Cache();
            c.x = Json.integer(row.get(0));
            c.y = Json.integer(row.get(1));
            c.due = Json.integer(row.get(2));
            c.sprouts = (Boolean) row.get(3);
            caches.add(c);
        }
        evo.load((Map<String, Object>) st.get("evo"));
        for (Object o : (List<Object>) st.get("frogs")) {
            List<Object> row = (List<Object>) o;
            Frog f = new Frog();
            f.x = Json.num(row.get(0));
            f.y = Json.num(row.get(1));
            f.a = Json.num(row.get(2));
            f.e = Json.num(row.get(3));
            f.wait = rng.randint(10, 200);
            f.g = row.size() > 4 ? new G((Map<String, ?>) row.get(4)) : evo.residentGenes("frog");
            frogs.add(f);
        }
        for (Object o : (List<Object>) st.get("snails")) {
            List<Object> row = (List<Object>) o;
            Snail s = new Snail();
            s.x = Json.num(row.get(0));
            s.y = Json.num(row.get(1));
            s.d = rng.uniform(0, 6.28);
            s.left = Json.integer(row.get(2));
            s.g = row.size() > 3 ? new G((Map<String, ?>) row.get(3)) : evo.residentGenes("snail");
            snails.add(s);
        }
        Object saved = st.get("moths");
        if (saved != null) {
            for (Object o : (List<Object>) saved) {
                List<Object> row = (List<Object>) o;
                eco.moths.add(eco.newMoth(Json.num(row.get(0)), Json.num(row.get(1)), new G((Map<String, ?>) row.get(2)), Json.num(row.get(3))));
            }
        }
    }
}
