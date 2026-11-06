package com.reviiser.bloom.core;

import static com.reviiser.bloom.core.Palette.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Things that may or may not be there. Nothing here is on a schedule anyone can rely on. Each oddity has its own
 * conditions: rare states of the ecosystem, the phase of the real moon, the hour on your clock, the date. Most are
 * glimpses, and several are only ever a shape against the sky. Everything that has ever been seen is remembered,
 * and shows up on the menu's Oddities page.
 */
public final class Oddities {
    /** A colour table for a sprite, from pairs of character and palette entry. */
    static int[] colors(Object... kv) {
        int[] m = new int[128];
        for (int i = 0; i < kv.length; i += 2) m[(Character) kv[i]] = (Integer) kv[i + 1];
        return m;
    }

    static final String[] WATCHER = {"...##...", "...##...", "..####..", ".#.##.#.", ".#.##.#.", ".#.##.#.", "#..##..#", "#..##..#", "#..##..#", "#..##..#", "...##...", "...##...", "...##...", "...##...", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "..#..#..", "..#..#..", ".##..##."};
    static final String[][] MOSSBACK = {
        {"....####.....", "...######....", "...######....", "..########...", ".##########..", ".##########..", "##.#######.#.", "##.#######.##", "##.#######.##", "#..#######..#", "...#######...", "...###.###...", "...###.###...", "..###...###..", "..###...###..", ".####...###.."},
        {"....####.....", "...######....", "...######....", "..########...", ".##########..", ".##########..", ".##.#######.#", "##..#######.#", "##..#######.#", "#...#######..", "...#######...", "...###.###...", "...###.###...", "...###..###..", "..###...###..", "..###...####."},
    };
    static final String[][] MOTHMAN = {
        {"#.............#", "##...........##", "###..##.##..###", "####.#R#R#.####", "#####.###.#####", ".#############.", "..####.#.####..", "...##..#..##...", "......#.#......"},
        {"...............", "......##.##....", "......#R#R#....", "##.....###...##", "###############", ".#############.", "..####.#.####..", "......#.#......", "......#.#......"},
    };
    static final String[] POND_NECK = {"..###.", ".####E", "#####.", "###...", "##....", "##....", "###...", ".##...", ".##...", ".###.."};
    static final String[] POND_HUMP = {"..####..", ".######.", "########"};
    static final String[] WOLF = {"..........#.", ".........##.", "........###.", ".......####.", ".......##...", "......###...", ".#...####...", ".##.#####...", "..#######...", "..#######...", "..##...##...", "..#.....#...", ".##.....##.."};
    static final String[][] KODAMA = {{".###.", "#O#O#", "#####", ".#.#."}, {".###..", "O#O##.", "#####.", ".#.#.."}};
    static final String[] SAUCER = {"....###....", "...#####...", ".#########.", "#o#o#o#o#o#", ".#########."};
    static final String[] WITCH = {"......#.......", ".....###......", "....#####.....", "...#######....", "..##.###.##...", "......###.....", ".....####.....", "#...#####.....", "##########=====", "#.....##......."};
    static final String[] SLEIGH = {".......#.........#.........#....", "##.#..###.......###.......###...", "#####.####.##..#####.##..#####..", "#####..#.#...#..#.#....#..#.#...", "##.....#.#......#.#.......#.#..."};
    static final String[][] CAT = {{"#.#.....", "###....#", "E#E...#.", ".#####..", ".#####..", ".#...#.."}, {"#.#.....", "###.....", "E#E....#", ".#####.#", ".#####..", "..#.#..."}};
    static final String[] GOOSE = {"..#", ".##", "###", ".#."};
    static final String[][] DRAGONFLY = {{"#.#.#", ".###.", "..#..", "..#.."}, {".....", "#####", "..#..", "..#.."}};
    static final String[] DUCK = {"...##.", "...#EO", "#.###.", "#####.", ".###.."};
    static final String[][] ERMINE = {{"......##", "#....###", ".######.", "..#..#.."}, {"......##", "#....###", ".######.", ".#....#."}};
    static final String[] EGG = {"..#..", ".#w#.", "#w#w#", "#####", ".###."};

    static final int[] SHADOW = colors('#', CANOPY.at(0), '=', TRUNK.at(1), 'o', EYE.at(1), 'R', FLOWER[4].at(2), 'E', FLOWER[4].at(1));
    static final int SKY_START = SKY.start, SKY_END = SKY.start + SKY.n;

    /** 0 at new moon, 1 at full. */
    static double fullMoonNearness() { return 1.0 - Math.abs(Scene.moonPhase(Clock.millis()) - 0.5) * 2.0; }

    private static IntList shape(Eco eco, Overlay overlay, String[] rows, int x, int y, boolean flip, int[] colors) {
        return Wildlife.stamp(eco, overlay, rows, x, y, flip, colors, -1);
    }

    /** Stands among the far trees. Never moves while you look. Only ever a shape. */
    static final class Watcher extends Wildlife.Creature {
        final boolean stormOnly;
        int life, x, y, shift;
        Watcher(Wildlife wild, boolean stormOnly) {
            super("watcher", wild, null);
            this.stormOnly = stormOnly;
            life = seconds(rng.uniform(120, 300));
            x = (int) (eco.s.moonX + rng.uniform(-90, 90) * eco.scaleS);
            y = eco.gy + 1;
            shift = seconds(rng.uniform(25, 60));
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            shift--;
            if (shift <= 0) { // the moment the shape changes place, it is a little closer to the middle
                shift = seconds(rng.uniform(25, 60));
                x += (int) ((eco.w / 2 - x) * 0.2) + rng.randint(-14, 14);
            }
            if (stormOnly && eco.climate.flash == 0) return life > 0;
            if (!shape(eco, overlay, WATCHER, x, y, false, SHADOW).isEmpty()) seen();
            return life > 0;
        }
    }

    /** Something very large, walking along the far treeline at a patient pace. */
    static final class Mossback extends Wildlife.Walker {
        int step;
        Mossback(Wildlife wild) { super("mossback", 6, wild, null, wild.eco.gy + 2); }
        @Override boolean frame(Overlay overlay) {
            if (eco.frameNo % 3 == 0) {
                x += dir;
                step++;
                eco.climate.footprint("mossback", x + 6, y + 1, step);
            }
            if (x < -20 || x > eco.w + 20) return false;
            IntList placed = shape(eco, overlay, MOSSBACK[(step / 6) % 2], x, y, dir < 0, colors('#', eco.daylight < 0.4 ? CANOPY.at(0) : TRUNK.at(0)));
            if (!placed.isEmpty()) {
                wild.odd.sighted(name);
                // A few pixels of old moss along the shoulder, for anyone looking closely.
                for (int k = 0; k < 14 && k < placed.size; k += 3) overlay.put(placed.a[k], LEAF.at(1));
            }
            return true;
        }
    }

    /** Once across the moon, wings wide, on a night the moths have been thick. */
    static final class Mothman extends Wildlife.Flyer {
        final double end;
        Mothman(Wildlife wild) {
            super("mothman", wild, null);
            dir = rng.choice(-1, 1);
            x = eco.s.moonX - dir * 130 * eco.scaleS;
            y = eco.s.moonY + rng.randint(-4, 8);
            end = eco.s.moonX + dir * 130 * eco.scaleS;
        }
        @Override boolean frame(Overlay overlay) {
            boolean done = flyTo(end, y - 0.3, 24);
            if (!shape(eco, overlay, MOTHMAN[(eco.frameNo / 3) & 1], (int) x - 7, (int) y, dir < 0, SHADOW).isEmpty()) seen();
            return !done;
        }
    }

    /** Rises from the middle of the pond, looks about, and goes down again. Usually with a frog fewer. */
    static final class PondThing extends Wildlife.Creature {
        final int x, y, span;
        int t;
        boolean fedOn;
        PondThing(Wildlife wild) {
            super("pond_thing", wild, null);
            Scene s = eco.s;
            int px = s.pondCx + rng.randint(Clock.div(-s.pondRx, 3), s.pondRx / 3);
            x = px;
            y = s.pondCy + rng.randint(Clock.div(-s.pondRy, 4), s.pondRy / 4);
            span = seconds(rng.uniform(30, 60));
        }
        @Override boolean frame(Overlay overlay) {
            t++;
            double six = Math.max(1, seconds(6));
            double rise = Math.min(Math.min(1.0, t / six), (span - t) / six);
            if (rise <= 0) return false;
            if (t % Math.max(1, seconds(3)) == 1) {
                wild.ripple(x, y);
                wild.ripple(x + 16, y);
            }
            shape(eco, overlay, Arrays.copyOf(POND_NECK, Math.min(POND_NECK.length, (int) (POND_NECK.length * rise) + 1)), x, y, false, colors('#', CANOPY.at(1), 'E', EYE.at(1)));
            String[] humps = Arrays.copyOf(POND_HUMP, Math.max(1, (int) (3 * rise)));
            int[] dark = colors('#', CANOPY.at(1));
            shape(eco, overlay, humps, x + 12, y + 1, false, dark);
            shape(eco, overlay, humps, x + 24, y + 1, false, dark);
            seen();
            if (t == span / 2 && !fedOn && !wild.frogs.isEmpty() && rng.random() < 0.6) {
                fedOn = true;
                Wildlife.Frog frog = rng.choice(wild.frogs);
                wild.take(new Wildlife.Prey("frog", (int) frog.x, (int) frog.y, frog));
                wild.ripple((int) frog.x, (int) frog.y);
            }
            return true;
        }
    }

    /** In a hard winter, a long dark shape moving beneath the ice. */
    static final class UnderIce extends Wildlife.Creature {
        double a;
        final double cx, cy, rx, ry;
        int life;
        UnderIce(Wildlife wild) {
            super("under_ice", wild, null);
            a = rng.uniform(0, 6.28);
            cx = eco.s.pondCx;
            cy = eco.s.pondCy;
            rx = eco.s.pondRx * 0.55;
            ry = eco.s.pondRy * 0.45;
            life = seconds(rng.uniform(25, 50));
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            a += 0.25 / Math.max(1, eco.fps);
            int w = eco.w;
            int[] water = eco.s.water;
            for (int k = 0; k < 16; k++) {
                double an = a - k * 0.06;
                int x = (int) (cx + Math.cos(an) * rx), y = (int) (cy + Math.sin(an) * ry);
                int thick = 3 < k && k < 12 ? 2 : 1;
                for (int dy = -thick + 1; dy < thick; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int i = (y + dy) * w + x + dx;
                        if (0 <= i && i < water.length && water[i] != 0 && Clock.mod(x + y + dx + dy + eco.frameNo / 6, 3) != 0) overlay.put(i, CANOPY.at(3));
                    }
                }
            }
            seen();
            return life > 0;
        }
    }

    /** Just two red eyes at the treeline, keeping pace with nothing. */
    static final class BlackDog extends Wildlife.Creature {
        double x;
        final int y, dir;
        int life, blink;
        BlackDog(Wildlife wild) {
            super("black_dog", wild, null);
            x = rng.choice(eco.w * 0.2, eco.w * 0.7);
            y = eco.gy - 5;
            dir = rng.choice(-1, 1);
            life = seconds(rng.uniform(25, 60));
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            x += dir * 0.3 * 8 / Math.max(1, eco.fps);
            blink = (blink + 1) % Math.max(1, seconds(4));
            if (blink > seconds(0.2)) {
                int i = y * eco.w + (int) x;
                if (0 <= i && i < eco.w * eco.h - 3 && eco.c.fg[i] == 0) {
                    overlay.put(i, FLOWER[4].at(1));
                    overlay.put(i + 3, FLOWER[4].at(1));
                    seen();
                }
            }
            return life > 0;
        }
    }

    /** Small pale tree spirits, gathered where the woods have grown old enough to want them. */
    static final class Kodama extends Wildlife.Creature {
        final List<int[]> spots = new ArrayList<>();
        int life;
        static final int[] COLORS = colors('#', STAG.at(2), 'O', CANOPY.at(0));
        Kodama(Wildlife wild) {
            super("kodama", wild, null);
            List<Eco.Plant> trees = new ArrayList<>();
            for (Eco.Plant p : eco.plants) if (p.kind.equals("sapling") || p.kind.equals("log")) trees.add(p);
            for (int k = 0, n = rng.randint(3, 7); k < n; k++) {
                if (!trees.isEmpty()) {
                    Eco.Plant t = rng.choice(trees);
                    int sx = t.x + rng.randint(-12, 12), sy = t.y + rng.randint(0, 5);
                    spots.add(new int[]{sx, sy, rng.randint(0, 40)});
                }
            }
            life = seconds(rng.uniform(90, 200));
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            for (int[] s : spots) {
                s[2]++;
                boolean tilt = (s[2] / 11) % 5 == 0; // heads turn, each in its own time
                if (!shape(eco, overlay, KODAMA[tilt ? 1 : 0], s[0], s[1], false, COLORS).isEmpty()) seen();
            }
            return life > 0 && !spots.isEmpty();
        }
    }

    /** Yes, really. Very seldom. It takes one small thing and leaves a ring. */
    static final class Saucer extends Wildlife.Flyer {
        double targetX;
        String state = "in";
        int beam, ground;
        final Wildlife.Prey prey;
        Saucer(Wildlife wild) {
            super("saucer", wild, null);
            dir = rng.choice(-1, 1);
            x = dir > 0 ? -12.0 : eco.w + 12.0;
            y = eco.h * 0.14;
            targetX = rng.randint(eco.w / 4, eco.w * 3 / 4);
            prey = wild.nearestPrey(targetX, eco.gy + eco.span / 2, 9999, false, true);
            if (prey != null) {
                targetX = prey.x;
                ground = prey.y;
            } else ground = eco.gy + eco.span / 2;
        }
        @Override boolean frame(Overlay overlay) {
            if (state.equals("in")) {
                if (flyTo(targetX, y, 60)) {
                    state = "beam";
                    beam = seconds(5);
                }
            } else if (state.equals("beam")) {
                beam--;
                int top = (int) y + 1, w = eco.w;
                // A pale column, widening as it reaches the ground.
                for (int py = top; py <= ground; py++) {
                    int half = 1 + (py - top) / 12;
                    if ((py + eco.frameNo) % 3 != 0) {
                        for (int dx = -half; dx <= half; dx++) {
                            int i = py * w + (int) x + dx;
                            if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0) overlay.put(i, LIT2[eco.c.frame[i]]);
                        }
                    }
                }
                if (beam == seconds(1)) {
                    if (prey != null && wild.preyAlive(prey)) wild.take(prey);
                    ring();
                }
                if (beam <= 0) state = "out";
            } else if (flyTo(x + dir * 200, -14, 120)) return false;
            shape(eco, overlay, SAUCER, (int) x - 5, (int) y, false, colors('#', HARE.at(1), 'o', (eco.frameNo & 1) != 0 ? FIREFLY.at(3) : FLOWER[4].at(2)));
            seen();
            return true;
        }
        /** A flattened circle, in the moss or the snow, that lasts until they grow back over it. */
        private void ring() {
            Canvas c = eco.c;
            double r = 11.0;
            for (int k = 0; k < 70; k++) {
                double a = k / 70.0 * 2 * Math.PI;
                int px = (int) (x + Math.cos(a) * r), py = (int) (ground + Math.sin(a) * r * 0.45);
                if (0 <= px && px < eco.w && eco.gy <= py && py < eco.h) {
                    eco.mossSet(px / 2, (py - eco.gy) / 2, 0);
                    int i = py * c.w + px;
                    if (c.snow[i] != 0) {
                        c.snow[i] = SNOW.at(0);
                        eco.climate.prints.add(i);
                        c.refresh(i);
                    }
                }
            }
            eco.wild.evo.say("There is a perfect ring in the ground this morning.");
        }
    }

    /** On the ridge at the full moon, muzzle raised. Everything small goes quiet and runs. */
    static final class Werewolf extends Wildlife.Creature {
        final int x, y;
        int life;
        Werewolf(Wildlife wild) {
            super("werewolf", wild, null);
            x = (int) (eco.s.moonX + rng.choice(-1, 1) * 34 * eco.scaleS);
            y = eco.gy - 2;
            life = seconds(rng.uniform(20, 40));
            for (Wildlife.Creature v : wild.visitors) if (v instanceof Wildlife.Rabbit) ((Wildlife.Rabbit) v).flee = true; // the rabbits know
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            if (!shape(eco, overlay, WOLF, x, y, false, colors('#', CANOPY.at(0))).isEmpty()) seen();
            return life > 0;
        }
    }

    /** After the last stag is gone. It walks the same way they walked, and drinks from nothing. */
    static final class GhostStag extends Wildlife.Creature {
        double x;
        int y, dir, step;
        static final int[] COLORS = colors('#', 1, '+', 1, 'O', 1, 'w', 1, '^', 1);
        GhostStag(Wildlife wild) {
            super("ghost_stag", wild, null);
            dir = rng.choice(-1, 1);
            x = dir > 0 ? -26.0 : eco.w + 26.0;
            y = eco.gy + (int) (eco.span * rng.uniform(0.25, 0.5));
            g = new G().with("size", 0.5).with("ornament", 0.5).with("coat", 0.57).with("glow", 0.0).with("active", 0.2);
        }
        @Override boolean frame(Overlay overlay) {
            if (eco.frameNo % 5 == 0) {
                x += dir;
                step++;
                y = wild.steer((int) x + 11, y, y, dir);
            }
            if (x < -30 || x > eco.w + 30) return false;
            String[] rows = Sprites.animalRows("stag", "walk" + (step / 4) % 2, g);
            // Not drawn so much as lit: the world shows straight through it.
            Overlay scratch = wild.odd.scratch();
            IntList placed = shape(eco, scratch, rows, (int) x, y, dir < 0, COLORS);
            scratch.clear();
            for (int k = 0; k < placed.size; k++) overlay.put(placed.a[k], LIT2[eco.c.frame[placed.a[k]]]);
            if (!placed.isEmpty()) seen();
            return true;
        }
    }

    static class WitchAcrossTheMoon extends Wildlife.Flyer {
        final String[] sprite;
        final double speed, end;
        WitchAcrossTheMoon(String name, String[] sprite, double speed, Wildlife wild) {
            super(name, wild, null);
            this.sprite = sprite;
            this.speed = speed;
            dir = rng.choice(-1, 1);
            x = eco.s.moonX - dir * 170 * eco.scaleS;
            y = eco.s.moonY + 6;
            end = eco.s.moonX + dir * 170 * eco.scaleS;
        }
        @Override boolean frame(Overlay overlay) {
            boolean done = flyTo(end, y - 0.5, speed);
            if (!shape(eco, overlay, sprite, (int) x - sprite[0].length() / 2, (int) (y + Math.sin(eco.frameNo * 0.2) * 1.5), dir < 0, SHADOW).isEmpty()) wild.odd.sighted(name);
            return !done;
        }
    }

    /** Friday the 13th. It crosses your path, and looks at you. */
    static final class BlackCat extends Wildlife.Walker {
        int step;
        static final int[] COLORS = colors('#', CANOPY.at(0), 'E', FIREFLY.at(3));
        BlackCat(Wildlife wild) { super("black_cat", 3, wild, null, null); }
        @Override boolean frame(Overlay overlay) {
            if (goOff(9)) return false;
            step++;
            if (!shape(eco, overlay, CAT[(step / 3) % 2], x, y, dir > 0, COLORS).isEmpty()) wild.odd.sighted(name);
            return true;
        }
    }

    // ---- seasonal animals ------------------------------------------------------

    /** A long V high overhead, north in spring and south in autumn. */
    static final class Geese extends Wildlife.Flyer {
        final int count;
        static final int[] COLORS = colors('#', CANOPY.at(0));
        Geese(Wildlife wild) {
            super("geese", wild, null);
            dir = rng.choice(-1, 1);
            x = dir > 0 ? -60.0 : eco.w + 60.0;
            y = eco.h * rng.uniform(0.07, 0.22);
            count = rng.randint(5, 11);
        }
        @Override boolean frame(Overlay overlay) {
            x += dir * 22.0 / Math.max(1, eco.fps);
            if (x < -90 || x > eco.w + 90) return false;
            for (int k = 0; k < count; k++) {
                int side = k % 2 != 0 ? 1 : -1, rank = (k + 1) / 2;
                int px = (int) (x - dir * rank * 7), py = (int) (y + side * rank * 3 + Math.sin(eco.frameNo * 0.3 + k) * 0.7);
                shape(eco, overlay, GOOSE, px, py, dir > 0, COLORS);
            }
            wild.odd.sighted(name);
            return true;
        }
    }

    /** Summer, over the water. They hover, dart, and take moths on the wing. */
    static final class Dragonflies extends Wildlife.Creature {
        static final class Fly { double x, y, tx, ty; int wait; Ramp hue; }
        int life;
        final List<Fly> flies = new ArrayList<>();
        Dragonflies(Wildlife wild) {
            super("dragonfly", wild, null);
            life = seconds(rng.uniform(70, 170));
            Scene s = eco.s;
            for (int k = 0, n = rng.randint(2, 4); k < n; k++) {
                Fly d = new Fly();
                d.x = s.pondCx + rng.uniform(-s.pondRx, s.pondRx);
                d.y = s.pondCy - rng.uniform(2, 12);
                d.wait = rng.randint(0, 30);
                d.hue = rng.choice(new Ramp[]{MYC[2], MYC[0], FLOWER[4]});
                flies.add(d);
            }
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            Scene s = eco.s;
            for (Fly d : flies) {
                d.wait--;
                if (d.wait <= 0) {
                    d.wait = rng.randint(6, 40);
                    d.tx = s.pondCx + rng.uniform(-s.pondRx * 1.1, s.pondRx * 1.1);
                    d.ty = s.pondCy - rng.uniform(0, 14);
                }
                d.x += (d.tx - d.x) * 0.3;
                d.y += (d.ty - d.y) * 0.3;
                for (int k = 0; k < eco.moths.size(); k++) {
                    Eco.Moth m = eco.moths.get(k);
                    if (Math.abs(m.x - d.x) < 3 && Math.abs(m.y - d.y) < 3) {
                        eco.moths.remove(k);
                        wild.note("moth taken by dragonfly");
                        break;
                    }
                }
                shape(eco, overlay, DRAGONFLY[eco.frameNo & 1], (int) d.x - 2, (int) d.y, false, colors('#', d.hue.at(1)));
            }
            wild.odd.sighted(name);
            return life > 0;
        }
    }

    /** A pair on the pond, spring to autumn; in spring, a line of ducklings behind. */
    static final class Ducks extends Wildlife.Creature {
        double a;
        final double cx, cy, rx, ry;
        int life;
        final int ducklings;
        Ducks(Wildlife wild) {
            super("ducks", wild, null);
            a = rng.uniform(0, 6.28);
            cx = eco.s.pondCx;
            cy = eco.s.pondCy;
            rx = eco.s.pondRx * 0.6;
            ry = eco.s.pondRy * 0.5;
            life = seconds(rng.uniform(100, 240));
            ducklings = eco.climate.season.equals("spring") ? rng.randint(3, 6) : 0;
        }
        @Override boolean frame(Overlay overlay) {
            life--;
            if (eco.climate.frozen()) return false;
            a += 0.12 / Math.max(1, eco.fps);
            int w = eco.w;
            for (int k = 0; k < 2; k++) {
                double an = a - k * 0.35;
                int x = (int) (cx + Math.cos(an) * rx), y = (int) (cy + Math.sin(an) * ry);
                int[] coat = colors('#', k == 0 ? FROG.at(1) : LITTER.at(3), 'E', CANOPY.at(0), 'O', EYE.at(1)); // the drake, and the duck
                shape(eco, overlay, DUCK, x - 3, y, Math.sin(an) > 0, coat);
                if (eco.frameNo % 20 == k) wild.ripple(x, y + 2);
            }
            for (int k = 0; k < ducklings; k++) {
                double an = a - 0.7 - k * 0.2;
                int x = (int) (cx + Math.cos(an) * rx), y = (int) (cy + Math.sin(an) * ry);
                int i = y * w + x;
                if (i >= 0 && i < eco.s.water.length && eco.s.water[i] != 0) {
                    overlay.put(i, EYE.at(1));
                    overlay.put(i + 1, EYE.at(2));
                }
            }
            wild.odd.sighted(name);
            return life > 0;
        }
    }

    /** White as the snow it runs on, in sudden bursts. Black tip to the tail is all that shows. */
    static final class Ermine extends Wildlife.Walker {
        int pause, step;
        static final int[] COLORS = colors('#', STAG.at(2));
        Ermine(Wildlife wild) { super("ermine", 4, wild, null, null); }
        @Override boolean frame(Overlay overlay) {
            if (pause > 0) pause--;
            else {
                if (goOff(26)) return false;
                step++;
                eco.climate.footprint("ermine", x + 3, y, step);
                if (rng.random() < 0.05) pause = seconds(rng.uniform(0.5, 2.5));
            }
            IntList placed = shape(eco, overlay, ERMINE[(step / 2) % 2], x, y, dir < 0, COLORS);
            if (!placed.isEmpty()) {
                overlay.put(placed.a[0], CANOPY.at(0));
                wild.odd.sighted(name);
            }
            return true;
        }
    }

    static final List<String> ODD_VISITORS = Arrays.asList("climbing_squirrel", "pine_marten", "woodpecker", "treecreeper", "watcher", "mossback", "mothman", "pond_thing", "under_ice",
            "black_dog", "kodama", "saucer", "werewolf", "ghost_stag", "witch", "sleigh", "black_cat", "geese", "dragonfly", "ducks", "ermine");
    static boolean isOddVisitor(String name) { return ODD_VISITORS.contains(name); }

    // Everything in the collection: name -> title, riddle while unseen, note once seen.
    public static final Map<String, String[]> CATALOGUE = new LinkedHashMap<>();
    private static void cat(String name, String title, String riddle, String note) { CATALOGUE.put(name, new String[]{title, riddle, note}); }
    static {
        cat("watcher", "THE WATCHER", "It never moves while you look.", "Tall and thin among the far trees. Lightning shows it best.");
        cat("mossback", "OLD MOSSBACK", "The largest thing on the ridge.", "Walks the far treeline. Leaves huge tracks in snow.");
        cat("big_tracks", "BIG TRACKS", "Look down, after snowfall.", "Enormous prints in the snow, going nowhere.");
        cat("mothman", "THE GREAT MOTH", "When wings are many, watch the moon.", "Crossed the moon, wings wide, on a night of many moths.");
        cat("pond_thing", "POND THING", "When the frogs grow bold.", "Rises from the middle of the water. Frogs disappear.");
        cat("under_ice", "BELOW THE ICE", "In the deepest cold, look down.", "A long dark shape moving beneath the frozen pond.");
        cat("black_dog", "BLACK DOG", "Two points of red, in the small hours.", "Just the eyes, keeping pace along the treeline.");
        cat("kodama", "KODAMA", "They come when the woods grow old.", "Pale spirits, gathering where trees have stood long.");
        cat("saucer", "VISITOR", "Keep watching the skies.", "Took something small, and left a ring in the ground.");
        cat("werewolf", "THE HOWLER", "Under a full moon only.", "A shape on the ridge. Rabbits bolt when it comes.");
        cat("ghost_stag", "GHOST STAG", "Lose something you can't get back.", "Appears when no stag is left. The world shows through it.");
        cat("jackalope", "JACKALOPE", "It has to evolve. Be patient.", "A rabbit born with antlers. Nobody planned this.");
        cat("witch", "THE WITCH", "One night in autumn.", "Crossed the moon on the last nights of October.");
        cat("sleigh", "THE SLEIGH", "One night in December.", "Very late on the night before Christmas.");
        cat("black_cat", "BLACK CAT", "Beware a certain Friday.", "Friday the 13th. It looked straight at you.");
        cat("geese", "GEESE", "Look up, at the turn of the year.", "North in spring and south in autumn.");
        cat("dragonfly", "DRAGONFLY", "Summer, over the water.", "Hovers, darts, and takes moths on the wing.");
        cat("ducks", "DUCKS", "They need open water.", "Paddle the pond spring to autumn, with young in spring.");
        cat("ermine", "ERMINE", "White on white, in deep snow.", "Runs in bursts across the snow. Black-tipped tail.");
        cat("woodpecker", "WOODPECKER", "Listen to the bark, by day.", "Works up a trunk in hops, drilling for beetles.");
        cat("treecreeper", "TREECREEPER", "A brown mote, circling a trunk.", "Spirals up one tree, then flutters to the foot of the next.");
        cat("pine_marten", "PINE MARTEN", "Squirrels aren't safe up there.", "Long, dark and fast. Hunts squirrels up the trunks.");
        cat("aurora", "AURORA", "The coldest clear nights.", "Green curtains over the winter forest.");
        cat("fireworks", "FIREWORKS", "As one year turns to the next.", "Far off and silent, over the trees at midnight.");
        cat("heart", "A HEART", "When love is in the air.", "February 14th. The fireflies keep a certain shape.");
        cat("eggs", "SEVEN EGGS", "A spring weekend that moves.", "Hidden in the meadow for Easter.");
        cat("lucky_clover", "LUCKY CLOVER", "Green, in the middle of March.", "St Patrick's Day: a glint of gold in the clover.");
        cat("square_moon", "THE MOON?", "Don't be fooled.", "April 1st. Nothing to see here.");
        cat("rainbow", "RAINBOW", "Sun, straight after rain.", "A whole arch over the clearing, when the sun follows rain.");
    }
    public static final Map<String, String[]> CATALOGUE_SPRITES = new LinkedHashMap<>();
    static {
        Object[] pairs = {"watcher", WATCHER, "mossback", MOSSBACK[0], "mothman", MOTHMAN[0], "pond_thing", POND_NECK, "kodama", KODAMA[0], "saucer", SAUCER, "werewolf", WOLF, "witch", WITCH,
                "sleigh", SLEIGH, "black_cat", CAT[0], "geese", GOOSE, "dragonfly", DRAGONFLY[0], "ducks", DUCK, "ermine", ERMINE[0], "woodpecker", Climbers.WOODPECKER, "treecreeper", Climbers.CREEPER, "pine_marten", Climbers.MARTEN};
        for (int i = 0; i < pairs.length; i += 2) CATALOGUE_SPRITES.put((String) pairs[i], (String[]) pairs[i + 1]);
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        boolean start = true;
        for (char ch : s.toCharArray()) {
            b.append(start ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            start = !Character.isLetter(ch);
        }
        return b.toString();
    }

    final Wildlife wild;
    final Eco eco;
    final PyRandom rng;
    int watcherStorm;
    final List<Object[]> eggs = new ArrayList<>();
    int eggKey = -1;
    final List<Object[]> firework = new ArrayList<>();
    private Overlay scratchOverlay;

    Oddities(Wildlife wild) {
        this.wild = wild;
        this.eco = wild.eco;
        this.rng = wild.eco.rng;
    }

    Overlay scratch() {
        if (scratchOverlay == null) scratchOverlay = new Overlay(eco.w * eco.h);
        return scratchOverlay;
    }

    // ---- the collection ----------------------------------------------------

    @SuppressWarnings("unchecked")
    void sighted(String name) {
        Map<String, Object> table = (Map<String, Object>) eco.stats.get("oddities");
        if (table == null) {
            table = new LinkedHashMap<>();
            eco.stats.put("oddities", table);
        }
        Map<String, Object> entry = (Map<String, Object>) table.get(name);
        double now = (long) Clock.seconds();
        if (entry == null || now - Json.num(entry.get("last")) > 600) { // one glimpse is one sighting, however long it lasts
            boolean first = entry == null;
            if (first) {
                entry = new LinkedHashMap<>();
                entry.put("n", 0.0);
                entry.put("first", now);
                entry.put("last", now);
                table.put(name, entry);
            }
            entry.put("n", Json.num(entry.get("n")) + 1);
            entry.put("last", now);
            if (first && CATALOGUE.containsKey(name)) wild.evo.say("Something new in the forest: " + titleCase(CATALOGUE.get(name)[0]) + ".");
        } else entry.put("last", now);
    }

    // ---- what appears ------------------------------------------------------

    boolean summon(String name, boolean force) {
        if (!ODD_VISITORS.contains(name) || wild.find(name) != null) return false;
        Wildlife.Creature v;
        switch (name) {
            case "climbing_squirrel": v = new Climbers.ClimbingSquirrel(wild, null); break;
            case "pine_marten": v = new Climbers.PineMarten(wild); break;
            case "woodpecker": v = new Climbers.Woodpecker(wild); break;
            case "treecreeper": v = new Climbers.Treecreeper(wild); break;
            case "watcher": v = new Watcher(wild, false); break;
            case "mossback": v = new Mossback(wild); break;
            case "mothman": v = new Mothman(wild); break;
            case "pond_thing": v = new PondThing(wild); break;
            case "under_ice": v = new UnderIce(wild); break;
            case "black_dog": v = new BlackDog(wild); break;
            case "kodama": v = new Kodama(wild); break;
            case "saucer": v = new Saucer(wild); break;
            case "werewolf": v = new Werewolf(wild); break;
            case "ghost_stag": v = new GhostStag(wild); break;
            case "witch": v = new WitchAcrossTheMoon("witch", WITCH, 34, wild); break;
            case "sleigh": v = new WitchAcrossTheMoon("sleigh", SLEIGH, 44, wild); break;
            case "black_cat": v = new BlackCat(wild); break;
            case "geese": v = new Geese(wild); break;
            case "dragonfly": v = new Dragonflies(wild); break;
            case "ducks": v = new Ducks(wild); break;
            default: v = new Ermine(wild);
        }
        wild.visitors.add(v);
        return true;
    }

    private boolean chance(double p) { return rng.random() < p; }

    /** Every two minutes or so, whether anything has a reason to show itself. */
    private void check() {
        Climate climate = eco.climate;
        Calendar when = climate.now();
        Set<String> tags = climate.todayTags;
        boolean night = eco.daylight < 0.1, dusk = eco.daylight < 0.4;
        Evolution evo = wild.evo;
        int frogs = wild.frogs.size();
        double moon = fullMoonNearness();
        // The seasonal residents and visitors.
        if ((climate.season.equals("spring") || climate.season.equals("autumn")) && chance(0.012)) summon("geese", true);
        if (climate.season.equals("summer") && eco.daylight > 0.2 && chance(0.02)) summon("dragonfly", true);
        if (!climate.season.equals("winter") && !climate.frozen() && eco.daylight > 0.1 && chance(0.012)) summon("ducks", true);
        if (climate.snow > 0.4 && chance(0.02)) summon("ermine", true);
        // Life on the trunks.
        if (eco.daylight > 0.2 && chance(0.02)) summon("woodpecker", true);
        if (eco.daylight > 0.1 && chance(0.02)) summon("treecreeper", true);
        if (evo.awakeCount("squirrel") != 0 && chance(0.02)) summon("climbing_squirrel", true);
        if (dusk && chance(0.006)) summon("pine_marten", true);
        else if (wild.find("climbing_squirrel") != null && dusk && chance(0.2)) summon("pine_marten", true); // a squirrel on the trunk is what brings one
        if (!eco.cfg.oddities) return;
        // Rare states of the world.
        if (night && !eco.moths.isEmpty() && eco.moths.size() >= (int) (10 * eco.cfg.density) && chance(0.03)) summon("mothman", true);
        if (night && !climate.frozen() && frogs >= 6 && chance(0.02)) summon("pond_thing", true);
        int trees = eco.countKind("sapling") + eco.countKind("log");
        if (dusk && trees >= 4 && chance(0.012)) summon("kodama", true);
        if (evo.pools.get("stag").isEmpty() && chance(0.04)) summon("ghost_stag", true);
        if (climate.frozen() && night && chance(0.012)) summon("under_ice", true);
        // Long odds at any time.
        if (night && chance(0.0015) && !climate.frozen()) summon("pond_thing", true);
        if (dusk && chance(0.0015)) summon("mossback", true);
        if (night && chance(0.0007)) summon("saucer", true);
        // The moon.
        if (night && moon > 0.92 && chance(0.012)) summon("werewolf", true);
        // The hour on your clock.
        int hour = Clock.hour(when), minute = Clock.minute(when);
        if (hour == 3 && (minute == 33 || minute == 34) && chance(0.5)) summon("watcher", true);
        else if (night && chance(0.0012)) summon("watcher", true);
        if (2 <= hour && hour < 4 && chance(0.01 + (tags.contains("friday_13th") ? 0.08 : 0))) summon("black_dog", true);
        // Dates.
        if (tags.contains("hallows_eve") && night && chance(0.06)) summon("witch", true);
        if (tags.contains("christmas_eve") && night && hour >= 22 && chance(0.2)) summon("sleigh", true);
        if (tags.contains("friday_13th") && chance(0.02)) summon("black_cat", true);
        if (tags.contains("perseids") && night && chance(0.8)) wild.summon("star", true);
        if (tags.contains("midsummer") && dusk && chance(0.05)) wild.summon("ring", true);
    }

    void tick() {
        if (eco.tickNo % 240 == 0 && eco.cfg.fauna) check();
        // A storm may bring the Watcher, visible only in the lightning.
        Climate climate = eco.climate;
        if (climate.storm == 1) watcherStorm = 0;
        if (eco.cfg.oddities && climate.storm > 100 && watcherStorm == 0 && eco.tickNo % 200 == 0) {
            watcherStorm = 1;
            if (rng.random() < 0.35 && wild.find("watcher") == null) wild.visitors.add(new Watcher(wild, true));
        }
    }

    void onRabbitSeen(Wildlife.Rabbit rabbit) { if (rabbit.g.d("ornament") > 0.7) sighted("jackalope"); }

    // ---- the forest dressed for the date -----------------------------------

    void frame(Overlay overlay) {
        if (!eco.cfg.oddities) return;
        Set<String> tags = eco.climate.todayTags;
        if (tags.isEmpty()) return;
        boolean night = eco.daylight < 0.25;
        if (tags.contains("halloween") && night) jackOLanterns(overlay);
        if (tags.contains("yuletide") && night) fairyLights(overlay);
        if (tags.contains("new_year")) fireworks(overlay);
        if (tags.contains("valentine") && night) heart(overlay);
        if (tags.contains("easter")) eggs(overlay);
        if (tags.contains("st_patrick")) luckyClover(overlay);
        if (tags.contains("april_fools")) squareMoon(overlay);
    }

    private void jackOLanterns(Overlay overlay) {
        int w = eco.w;
        int[][] face = {{-2, 0}, {2, 0}, {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {2, 2}};
        for (Eco.Plant p : eco.plants) {
            if (p.kind.equals("pumpkin") && p.shown > p.pix.size() * 0.7) {
                int c = (eco.frameNo + p.pid) % 11 < 2 ? EYE.at(1) : EYE.at(2);
                int y = p.y - 3;
                for (int[] d : face) {
                    int i = (y + d[1]) * w + p.x + d[0];
                    if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0) overlay.put(i, c);
                }
            }
        }
    }

    /** On the tallest young tree. Who put them there is another question. */
    private void fairyLights(Overlay overlay) {
        Eco.Plant tree = null;
        for (Eco.Plant p : eco.plants) if (p.kind.equals("sapling") && p.shown > 20 && (tree == null || p.shown > tree.shown)) tree = p;
        if (tree == null) return;
        int[] hues = {FLOWER[4].at(2), FLOWER[5].at(2), FLOWER[0].at(2), FLOWER[7].at(2), FLOWER[3].at(2)};
        int w = eco.w;
        for (int k = 0; k < tree.shown; k += Math.max(1, tree.shown / 18)) {
            int[] d = tree.pix.get(k);
            int i = (tree.y + d[1]) * w + tree.x + d[0];
            if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0 && (k + eco.frameNo / 6) % 3 != 0) overlay.put(i, hues[k % hues.length]);
        }
    }

    /** Far off and quiet, over the trees, around midnight. */
    private void fireworks(Overlay overlay) {
        Calendar when = eco.climate.now();
        int month = Clock.month(when), day = Clock.day(when), hour = Clock.hour(when);
        boolean midnight = (month == 12 && day == 31 && hour == 23) || (month == 1 && day == 1 && hour == 0);
        if (midnight && rng.random() < 0.03 && firework.size() < 3) {
            int x = rng.randrange(eco.w / 5, eco.w * 4 / 5), y = rng.randrange(eco.h / 12, eco.gy / 2);
            firework.add(new Object[]{x, y, new int[]{0}, rng.choice(new Ramp[]{FLOWER[4], FLOWER[5], FLOWER[0], FLOWER[7], FLOWER[3]})});
            sighted("fireworks");
        }
        int w = eco.w;
        int[] comp = eco.c.comp;
        for (Object[] fw : new ArrayList<>(firework)) {
            int[] age = (int[]) fw[2];
            age[0]++;
            double r = age[0] * 1.3;
            if (r > 13) {
                firework.remove(fw);
                continue;
            }
            int shade = ((Ramp) fw[3]).at(r < 6 ? 2 : r < 10 ? 1 : 0);
            for (int k = 0; k < 16; k++) {
                double a = k * 2 * Math.PI / 16;
                int x = (int) ((Integer) fw[0] + Math.cos(a) * r), y = (int) ((Integer) fw[1] + Math.sin(a) * r * 0.85);
                if (0 <= x && x < w && 0 <= y && y < eco.gy) {
                    int i = y * w + x;
                    if (SKY_START <= comp[i] && comp[i] < SKY_END) overlay.put(i, shade); // behind the leaves
                }
            }
        }
    }

    /** Valentine's night: for a while, the fireflies over the clearing keep a certain shape. */
    private void heart(Overlay overlay) {
        int cycle = eco.frameNo % Math.max(1, eco.fps * 120);
        if (cycle > eco.fps * 25) return;
        sighted("heart");
        int cx = eco.w / 2, cy = eco.gy + eco.span / 3, w = eco.w;
        for (int k = 0; k < 48; k++) {
            double t = k / 48.0 * 2 * Math.PI;
            int x = (int) (cx + 16 * Math.pow(Math.sin(t), 3));
            int y = (int) (cy - (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)));
            if ((k + eco.frameNo / 4) % 5 < 3) {
                int i = y * w + x;
                if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0) overlay.put(i, FLOWER[3].at(2));
            }
        }
    }

    /** Hidden in the meadow for the Easter weekend. There are always seven. */
    private void eggs(Overlay overlay) {
        int year = Clock.year(eco.climate.now());
        if (eggKey != year) {
            eggKey = year;
            PyRandom r = new PyRandom(year ^ eco.s.seed);
            eggs.clear();
            Ramp[] hues = {FLOWER[0], FLOWER[3], FLOWER[5], FLOWER[7], FLOWER[2], FLOWER[4], FLOWER[1]};
            for (int k = 0; k < 7; k++) {
                for (int t = 0; t < 20; t++) {
                    int x = r.randrange(eco.w), y = r.randrange(eco.gy + 6, eco.h - 4);
                    if (eco.s.ground[y * eco.w + x] != 0) {
                        eggs.add(new Object[]{x, y, hues[k]});
                        break;
                    }
                }
            }
        }
        for (Object[] egg : eggs) {
            Ramp hue = (Ramp) egg[2];
            shape(eco, overlay, EGG, (Integer) egg[0], (Integer) egg[1], false, colors('#', hue.at(1), 'w', hue.at(2)));
        }
        sighted("eggs");
    }

    /** St Patrick's Day. Every clover has a glint of gold today, for those who look. */
    private void luckyClover(Overlay overlay) {
        int w = eco.w;
        for (Eco.Plant p : eco.plants) {
            if (p.kind.equals("clover") && (p.pid * 7 + eco.frameNo / 8) % 19 == 0) {
                int i = (p.y - 2) * w + p.x;
                if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0) overlay.put(i, EYE.at(2));
            }
        }
        sighted("lucky_clover");
    }

    /** Happy April 1st. Nothing to see here. */
    private void squareMoon(Overlay overlay) {
        int mx = eco.s.moonX, my = eco.s.moonY, r = eco.s.moonR, w = eco.w;
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                int i = (my + y) * w + mx + x;
                if (0 <= i && i < w * eco.h && eco.c.fg[i] == 0) overlay.put(i, ((x * 3 + y * 5) & 7) != 0 ? MOON.at(4) : MOON.at(3));
            }
        }
        sighted("square_moon");
    }
}
