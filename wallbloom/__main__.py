"""Command line: bloom [start|stop|restart|status|settings|install|uninstall|journal [N]|summon [NAME]|reset|snapshot FILE]"""

import os
import signal
import subprocess
import sys
import time

from . import config as config_mod
from . import icon, report
from .report import PID_FILE, running_pid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAUNCHER = os.path.join(ROOT, "bloom")
CONFIG_HOME = os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config"))
DATA_HOME = os.environ.get("XDG_DATA_HOME", os.path.expanduser("~/.local/share"))
AUTOSTART = os.path.join(CONFIG_HOME, "autostart", "bloom-wallpaper.desktop")
MENU_ENTRY = os.path.join(DATA_HOME, "applications", "bloom-wallpaper-settings.desktop")


def start():
    if running_pid():
        print("Bloom is already running.")
        return
    subprocess.Popen([LAUNCHER, "run"], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                     stderr=subprocess.DEVNULL, start_new_session=True)
    print("Bloom started.")


def stop():
    pid = running_pid()
    if not pid:
        print("Bloom is not running.")
        return
    os.kill(pid, signal.SIGTERM)
    for _ in range(50):
        if not running_pid():
            break
        time.sleep(0.1)
    print("Bloom stopped. The world is saved.")


def autostart_enabled():
    return os.path.exists(AUTOSTART)


def desktop_command(*args):
    """A command line as desktop entries require it: double quotes only, since the path may hold spaces."""
    parts = []
    for arg in args:
        if any(c in arg for c in " \t\"'\\><~|&;$*?#()`"):
            escaped = arg.replace("\\", "\\\\").replace('"', '\\"').replace("`", "\\`").replace("$", "\\$")
            arg = f'"{escaped}"'.replace("\\", "\\\\")
        parts.append(arg)
    return " ".join(parts)


def set_autostart(enabled):
    if enabled:
        os.makedirs(os.path.dirname(AUTOSTART), exist_ok=True)
        with open(AUTOSTART, "w") as f:
            f.write("[Desktop Entry]\nType=Application\nName=Bloom wallpaper\n"
                    "Comment=A slow, living pixel-art forest\n"
                    f"Icon={icon.APP_ID}\n"
                    f"Exec={desktop_command(LAUNCHER, 'run')}\n"
                    "X-GNOME-Autostart-Delay=4\nNoDisplay=true\nTerminal=false\n")
    elif os.path.exists(AUTOSTART):
        os.unlink(AUTOSTART)


def set_menu_entry(enabled):
    if enabled:
        icon.install(DATA_HOME)
        os.makedirs(os.path.dirname(MENU_ENTRY), exist_ok=True)
        with open(MENU_ENTRY, "w") as f:
            f.write("[Desktop Entry]\nType=Application\nName=Bloom Wallpaper Settings\n"
                    "Comment=Darkness, palette and pace of your living forest\n"
                    f"Exec={desktop_command(LAUNCHER, 'settings')}\n"
                    f"Icon={icon.APP_ID}\nStartupWMClass=bloom-wallpaper-settings\n"
                    "Categories=Settings;DesktopSettings;\nTerminal=false\n")
    else:
        icon.uninstall(DATA_HOME)
        if os.path.exists(MENU_ENTRY):
            os.unlink(MENU_ENTRY)
    # Let the desktop notice straight away, where it has the tools for that.
    for command in (["gtk-update-icon-cache", "-f", "-t", "-q", os.path.join(DATA_HOME, "icons", "hicolor")],
                    ["update-desktop-database", "-q", os.path.join(DATA_HOME, "applications")]):
        try:
            subprocess.run(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        except OSError:
            pass


def snapshot(path):
    """Render the saved world (or a new one, if none) to a PNG, without any window."""
    import cairo

    from .world import World

    cfg = config_mod.load()
    world = World(cfg, 1920 // cfg.pixel_size, 1080 // cfg.pixel_size)
    for _ in range(40):
        world.eco.frame()
    w, h = world.w, world.h
    surface = cairo.ImageSurface.create_for_data(world.canvas.buf, cairo.FORMAT_RGB24, w, h, w * 4)
    out = cairo.ImageSurface(cairo.FORMAT_RGB24, w * cfg.pixel_size, h * cfg.pixel_size)
    ctx = cairo.Context(out)
    ctx.scale(cfg.pixel_size, cfg.pixel_size)
    ctx.set_source_surface(surface)
    ctx.get_source().set_filter(cairo.Filter.NEAREST)
    ctx.paint()
    out.write_to_png(path)
    print(f"Wrote {path}")


def main():
    args = sys.argv[1:] or ["start"]
    cmd = args[0]
    if cmd == "run":
        if running_pid():
            print("Bloom is already running.")
            return
        from .app import run
        run()
    elif cmd == "start":
        start()
    elif cmd == "stop":
        stop()
    elif cmd == "restart":
        stop()
        start()
    elif cmd == "status":
        report.refresh_and_wait()
        print(report.status_text())
    elif cmd == "journal":
        report.refresh_and_wait()
        print(report.journal_text(int(args[1]) if len(args) > 1 and args[1].isdigit() else 40))
    elif cmd == "settings":
        config_mod.ensure_exists()
        from .settings import run
        run(autostart_enabled, set_autostart)
    elif cmd == "install":
        set_autostart(True)
        set_menu_entry(True)
        config_mod.ensure_exists()
        start()
        print("Bloom will start at login. Find 'Bloom Wallpaper Settings' in your menu.")
    elif cmd == "uninstall":
        stop()
        set_autostart(False)
        set_menu_entry(False)
        print("Removed the autostart and menu entries. Your settings and world remain in\n"
              f"  {config_mod.CONFIG_DIR}\n  {config_mod.STATE_DIR}")
    elif cmd == "summon":
        from .wildlife import ALL_NAMES
        names = ALL_NAMES + ["frog", "snail"]
        name = args[1] if len(args) > 1 else "stag"
        pid = running_pid()
        if name not in names:
            print("Can summon: " + ", ".join(sorted(names)))
            sys.exit(2)
        if pid:
            with open(PID_FILE + ".summon", "w") as f:
                f.write(name)
            os.kill(pid, signal.SIGUSR1)
            print("Something stirs at the edge of the clearing.")
        else:
            print("Bloom is not running.")
    elif cmd == "reset":
        was_running = bool(running_pid())
        if was_running:
            stop()
        paths = [config_mod.STATE_PATH]
        if config_mod.load().seed:
            paths.append(config_mod.archive_path(config_mod.load().seed))
        for path in paths:
            if os.path.exists(path):
                os.unlink(path)
        print("The forest will begin again from bare ground.")
        if was_running:
            start()
    elif cmd == "snapshot" and len(args) > 1:
        snapshot(args[1])
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main()
