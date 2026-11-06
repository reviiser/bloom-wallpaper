"""The running wallpaper: one layer-shell surface per monitor, sharing a single world."""

import math
import os
import signal
import time

os.environ.setdefault("GDK_BACKEND", "wayland")

import gi  # noqa: E402

gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
gi.require_version("GtkLayerShell", "0.1")
import cairo  # noqa: E402
from gi.repository import Gdk, Gio, GLib, Gtk, GtkLayerShell  # noqa: E402

from . import config as config_mod  # noqa: E402
from .crt import BAR_EVERY, SMEAR_MARGIN, CrtLayer, SoftPicture  # noqa: E402
from .world import World  # noqa: E402

SIM_HZ = 2.0  # ecosystem ticks per second at speed 1
SAVE_SECONDS = 300
COVERED_AFTER = 3.0  # seconds without a draw before we assume nobody can see us
PID_FILE = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "bloom-wallpaper.pid")
SUMMON_FILE = PID_FILE + ".summon"


def on_battery():
    base = "/sys/class/power_supply"
    try:
        found = False
        for name in os.listdir(base):
            with open(os.path.join(base, name, "type")) as f:
                if f.read().strip() != "Mains":
                    continue
            with open(os.path.join(base, name, "online")) as f:
                if f.read().strip() == "1":
                    return False
            found = True
        return found
    except OSError:
        return False


def swaybg_pids():
    """Wallpaper daemons that could end up stacked on top of us."""
    pids = set()
    try:
        for entry in os.listdir("/proc"):
            if entry.isdigit():
                try:
                    with open(f"/proc/{entry}/comm") as f:
                        if f.read().strip() == "swaybg":
                            pids.add(entry)
                except OSError:
                    pass
    except OSError:
        pass
    return pids


class Surface:
    """A window on one monitor, showing the shared canvas scaled up with crisp pixels."""

    def __init__(self, app, monitor):
        self.app = app
        self.monitor = monitor
        self.last_draw = time.monotonic()
        self.pending_since = None
        self.crt = CrtLayer()
        geo = monitor.get_geometry()
        self.width, self.height = geo.width, geo.height
        px = app.cfg.pixel_size
        # Scale up by the pixel size, crop centrally if this monitor differs from the canvas.
        self.scale = max(px, math.ceil(max(geo.width / app.world.w, geo.height / app.world.h)))
        self.ox = (app.world.w * self.scale - geo.width) // 2
        self.oy = (app.world.h * self.scale - geo.height) // 2

        win = Gtk.Window()
        self.win = win
        GtkLayerShell.init_for_window(win)
        GtkLayerShell.set_monitor(win, monitor)
        GtkLayerShell.set_layer(win, GtkLayerShell.Layer.BACKGROUND)
        GtkLayerShell.set_namespace(win, "bloom-wallpaper")
        for edge in (GtkLayerShell.Edge.TOP, GtkLayerShell.Edge.BOTTOM, GtkLayerShell.Edge.LEFT, GtkLayerShell.Edge.RIGHT):
            GtkLayerShell.set_anchor(win, edge, True)
        GtkLayerShell.set_exclusive_zone(win, -1)
        GtkLayerShell.set_keyboard_mode(win, GtkLayerShell.KeyboardMode.NONE)
        win.set_app_paintable(True)
        win.connect("draw", self.on_draw)
        win.connect("realize", self.on_realize)
        win.show_all()

    def on_realize(self, win):
        gdk_win = win.get_window()
        # Opaque, and clicks fall straight through to the desktop.
        gdk_win.set_opaque_region(cairo.Region(cairo.RectangleInt(0, 0, self.width, self.height)))
        win.input_shape_combine_region(cairo.Region())

    def on_draw(self, win, ctx):
        self.last_draw = time.monotonic()
        self.pending_since = None
        strength = self.app.cfg.crt
        if strength > 0.001:
            self.crt.paint(ctx, self.app.soft.surface, strength, self.width, self.height, self.scale, self.ox, self.oy)
            return False
        ctx.set_operator(cairo.Operator.SOURCE)
        ctx.translate(-self.ox, -self.oy)
        ctx.scale(self.scale, self.scale)
        self.app.surface.mark_dirty()
        ctx.set_source_surface(self.app.surface, 0, 0)
        ctx.get_source().set_filter(cairo.Filter.NEAREST)
        ctx.paint()
        return False

    def queue(self, rects):
        if self.pending_since is None:
            self.pending_since = time.monotonic()
        if rects is None:
            self.win.queue_draw()
            return
        s, ox, oy = self.scale, self.ox, self.oy
        for x, y, w, h in rects:
            self.win.queue_draw_area(x * s - ox, y * s - oy, w * s, h * s)

    def roll_crt(self):
        """The one thing a CRT does even when the picture is still."""
        top, height = self.crt.roll(self.height)
        self.win.queue_draw_area(0, top, self.width, height)

    def covered(self, now):
        return self.pending_since is not None and now - self.pending_since > COVERED_AFTER

    def destroy(self):
        self.win.destroy()


class BloomApp:
    def __init__(self):
        config_mod.ensure_exists()
        self.cfg = config_mod.load()
        self.surfaces = []
        self.timer = 0
        self.sim_debt = 0.0
        self.frame_count = 0
        self.last_frame = time.monotonic()
        self.was_covered = False
        self.battery = on_battery()
        self.next_save = time.monotonic() + 30  # a first save soon, so a new world is safe
        self.next_slow = time.monotonic() + 60
        self.next_light = 0.0
        self.next_water = 0.0
        self.swaybg = None

        display = Gdk.Display.get_default()
        if display is None or not GtkLayerShell.is_supported():
            raise SystemExit("bloom: this compositor does not offer wlr-layer-shell, which Bloom needs.")
        self.display = display
        self._build_world()
        self._build_surfaces()
        self._start_timer()

        display.connect("monitor-added", lambda *_: self._rebuild_soon())
        display.connect("monitor-removed", lambda *_: self._rebuild_soon())
        self.rebuild_source = 0

        self.monitor_file = Gio.File.new_for_path(config_mod.CONFIG_PATH).monitor_file(Gio.FileMonitorFlags.WATCH_MOVES, None)
        self.monitor_file.connect("changed", self.on_config_changed)
        self.reload_source = 0

        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGTERM, self.quit)
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGINT, self.quit)
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGUSR1, self.on_summon)
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGUSR2, self.on_save_request)
        GLib.timeout_add_seconds(5, self.on_stack_check)
        self.swaybg = swaybg_pids()

    # ---- setup -------------------------------------------------------------

    def _canvas_size(self):
        """A canvas big enough for the largest monitor at the chosen pixel size."""
        px = self.cfg.pixel_size
        w = h = 0
        for k in range(self.display.get_n_monitors()):
            geo = self.display.get_monitor(k).get_geometry()
            w = max(w, geo.width)
            h = max(h, geo.height)
        w = w or 1920
        h = h or 1080
        return math.ceil(w / px), math.ceil(h / px)

    def _build_world(self):
        w, h = self._canvas_size()
        self.world = World(self.cfg, w, h)
        canvas = self.world.canvas
        stride = cairo.ImageSurface.format_stride_for_width(cairo.FORMAT_RGB24, w)
        assert stride == w * 4
        self.surface = cairo.ImageSurface.create_for_data(canvas.buf, cairo.FORMAT_RGB24, w, h, stride)
        self.soft = SoftPicture(self.surface, w, h)
        self.soft.update(None, self.cfg.crt)

    def _build_surfaces(self):
        for s in self.surfaces:
            s.destroy()
        self.surfaces = []
        for k in range(self.display.get_n_monitors()):
            self.surfaces.append(Surface(self, self.display.get_monitor(k)))

    def _rebuild_soon(self):
        if not getattr(self, "rebuild_source", 0):
            self.rebuild_source = GLib.timeout_add(800, self._rebuild_now)

    def _rebuild_now(self):
        self.rebuild_source = 0
        want = self._canvas_size()
        if want != (self.world.w, self.world.h):
            self.world.save()
            self._build_world()
        self._build_surfaces()
        return False

    def _effective_fps(self):
        fps = self.cfg.fps
        if self.battery and self.cfg.battery_saver:
            fps = max(2, fps // 2)
        return fps

    def _start_timer(self):
        if self.timer:
            GLib.source_remove(self.timer)
        fps = self._effective_fps()
        self.world.eco.fps = fps
        self.timer = GLib.timeout_add(int(1000 / fps), self.on_frame)

    # ---- the heartbeat -----------------------------------------------------

    def on_frame(self):
        now = time.monotonic()
        dt = now - self.last_frame
        self.last_frame = now
        world = self.world
        eco = world.eco

        covered = bool(self.surfaces) and all(s.covered(now) for s in self.surfaces)
        if covered != self.was_covered:
            self.was_covered = covered
            if os.environ.get("BLOOM_DEBUG"):
                print(f"bloom: {'covered, resting' if covered else 'visible again'}", flush=True)
            for s in self.surfaces:
                # Ask for one frame so we notice the moment anything becomes visible again.
                s.queue(None)

        self.sim_debt += min(dt, 2.0) * SIM_HZ * self.cfg.speed
        ticks = int(self.sim_debt)
        self.sim_debt -= ticks
        for _ in range(min(ticks, 40)):
            eco.tick()
        eco.frame(dim=covered)

        if not covered:
            if now >= self.next_light:
                self.next_light = now + 12.0
                world.update_light()
            if now >= self.next_water:
                self.next_water = now + 1.1
                world.cycle_water()
            world.frame_effects(eco.frame_no)
        if now >= self.next_slow:
            self.next_slow = now + 60
            world.minute_tick()
            battery = on_battery()
            if battery != self.battery:
                self.battery = battery
                self._start_timer()
                return False
        if now >= self.next_save:
            self.next_save = now + SAVE_SECONDS
            self._save()

        rects = world.canvas.take_dirty()
        if rects is None or rects:
            if self.cfg.crt > 0.001:
                # A changed pixel's smear reaches its neighbours, so a little more needs redrawing.
                if rects is not None:
                    m = SMEAR_MARGIN
                    rects = [(x - m, y, w + 2 * m, h) for x, y, w, h in rects]
                self.soft.update(rects, self.cfg.crt)
            for s in self.surfaces:
                s.queue(rects)
        # The hum bar is the one effect that redraws constantly, so it rests on battery and when nobody can see.
        if (self.cfg.crt > 0.001 and eco.frame_no % BAR_EVERY == 0 and not covered
                and not (self.battery and self.cfg.battery_saver)):
            for s in self.surfaces:
                s.roll_crt()
        return True

    def _save(self):
        try:
            self.world.save()
        except OSError as e:
            print(f"bloom: could not save world: {e}")

    # ---- events ------------------------------------------------------------

    def on_config_changed(self, monitor, file, other, event):
        if event in (Gio.FileMonitorEvent.CHANGES_DONE_HINT, Gio.FileMonitorEvent.CREATED, Gio.FileMonitorEvent.MOVED_IN,
                     Gio.FileMonitorEvent.RENAMED, Gio.FileMonitorEvent.CHANGED) and not self.reload_source:
            self.reload_source = GLib.timeout_add(250, self._reload_config)

    def _reload_config(self):
        self.reload_source = 0
        new = config_mod.load()
        old = self.cfg
        self.cfg = new
        if new.pixel_size != old.pixel_size or (new.seed and new.seed != self.world.seed):
            self.world.save()
            self._build_world()
            self._build_surfaces()
            self._save()
        else:
            self.world.set_config(new)
        self.soft.update(None, self.cfg.crt)
        for s in self.surfaces:
            s.queue(None)
        self._start_timer()
        return False

    def on_save_request(self):
        self._save()
        return True

    def on_summon(self):
        name = "stag"
        try:
            with open(SUMMON_FILE) as f:
                name = f.read().strip() or name
            os.unlink(SUMMON_FILE)
        except OSError:
            pass
        self.world.eco.wild.summon(name)
        return True

    def on_stack_check(self):
        """A wallpaper daemon started after us lands on top. If one appears, step back in front."""
        pids = swaybg_pids()
        if pids - (self.swaybg or set()):
            self._build_surfaces()
        self.swaybg = pids
        return True

    def quit(self, *_):
        self._save()
        Gtk.main_quit()
        return False


def run():
    # Until the world is built, a stray request must not be fatal. Nobody can
    # find us to send one before the pid file exists, so this comes first.
    signal.signal(signal.SIGUSR1, signal.SIG_IGN)
    signal.signal(signal.SIGUSR2, signal.SIG_IGN)
    with open(PID_FILE, "w") as f:
        f.write(str(os.getpid()))
    os.nice(10)
    try:
        BloomApp()
        Gtk.main()
    finally:
        try:
            os.unlink(PID_FILE)
        except OSError:
            pass
