"""What the forest has to say for itself, as text, for the command line and the settings window alike."""

import gzip
import json
import os
import signal
import time

from . import config as config_mod

PID_FILE = os.path.join(os.environ.get("XDG_RUNTIME_DIR", "/tmp"), "bloom-wallpaper.pid")


def running_pid():
    try:
        with open(PID_FILE) as f:
            pid = int(f.read().strip())
        with open(f"/proc/{pid}/cmdline", "rb") as f:
            if b"bloom" in f.read():
                return pid
    except (OSError, ValueError):
        pass
    return None


def request_save():
    """Ask the running wallpaper to write its world now, so what we read next is current."""
    pid = running_pid()
    if pid:
        try:
            os.kill(pid, signal.SIGUSR2)
            return True
        except OSError:
            pass
    return False


def refresh_and_wait(timeout=1.5):
    """request_save, then wait for the new file to land."""
    try:
        before = os.stat(config_mod.STATE_PATH).st_mtime_ns
    except OSError:
        before = 0
    if not request_save():
        return
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        time.sleep(0.05)
        try:
            if os.stat(config_mod.STATE_PATH).st_mtime_ns != before:
                return
        except OSError:
            pass


def read_world():
    try:
        with gzip.open(config_mod.STATE_PATH, "rt") as f:
            return json.load(f)
    except (OSError, ValueError, EOFError):
        return None


def clock_time(moment):
    """12-hour time, padded so that a column of them lines up: " 3:05 PM"."""
    return time.strftime("%I:%M %p", moment).lstrip("0").rjust(8)


def journal_text(count=40, newest_first=False):
    """The forest's own record of what has happened."""
    data = read_world()
    entries = ((data or {}).get("eco", {}).get("wild", {}).get("evo", {}) or {}).get("journal", [])
    if not entries:
        return "Nothing written yet. It takes a little while for a forest to have a history."
    entries = entries[-count:]
    if newest_first:
        entries = entries[::-1]
    lines = []
    day = None
    for when, tick, text in entries:
        moment = time.localtime(when)
        heading = time.strftime("%A %d %B", moment)
        if heading != day:
            if day is not None:
                lines.append("")
            day = heading
            lines.append(heading)
        lines.append(f"  {clock_time(moment)}  {text}")
    return "\n".join(lines)


def status_text():
    pid = running_pid()
    lines = [f"Running as pid {pid}." if pid else "Not running."]
    data = read_world()
    if not data:
        lines.append("No saved world yet.")
        return "\n".join(lines)
    census = data.get("census", {})
    stats = census.get("stats", {})
    tick = census.get("tick", 0)
    lines.append("")
    lines.append(f"World seed {data.get('seed')}, age {tick} ticks (about {tick / 7200:.1f} hours of forest time)")
    if census.get("season"):
        lines.append(f"Season:   {census['season']}, {census.get('temp', 0):.0f}C, {census.get('weather', 'clear')}, "
                     f"snow {census.get('snow', 0):.0%}, ice {census.get('ice', 0):.0%}")
    sky = census.get("sky") or {}
    if sky.get("near"):
        shown = ", ".join(sky.get("visible", [])) or "little, through the leaves"
        lines.append(f"Sky:      moon near {sky['near']}; showing {shown} (for {sky.get('place') or 'your longitude'})")
    lines.append("Plants:   " + ", ".join(f"{n} {k}" for k, n in sorted(census.get("plants", {}).items())))
    lines.append(f"Mycelium: {census.get('colonies', 0)} colonies, {census.get('hyphae', 0)} thread pixels, "
                 f"fungal generation {stats.get('max_fungal_gen', 0)}")
    lines.append(f"Moss:     {census.get('moss_cells', 0)} cells; strongest rules: " +
                 ", ".join(f"{r} ({n})" for r, n in census.get("top_moss_rules", [])))
    lines.append(f"So far:   {stats.get('births', 0)} births, {stats.get('deaths', 0)} deaths, "
                 f"{stats.get('fruitings', 0)} fruitings, {stats.get('pollinations', 0)} pollinations, "
                 f"{stats.get('rule_mutations', 0)} rule mutations, plant generation {stats.get('max_plant_gen', 0)}, "
                 f"{stats.get('rains', 0)} rains, {stats.get('stag_visits', 0)} visits from the stag")
    animals = census.get("animals", {})
    if animals:
        lines.append("")
        lines.append("Animals:")
        for name, row in animals.items():
            lines.append(f"  {name:9} {row['n']:3}  {row['looks']}")
        for name in ("frogs", "beetles", "moths", "snails"):
            lines.append(f"  {name:9} {census.get(name, 0):3}")
    seen = stats.get("sightings", {})
    if seen:
        lines.append("")
        lines.append("Sightings: " + ", ".join(f"{n} {k}" for k, n in sorted(seen.items(), key=lambda kv: -kv[1])))
    return "\n".join(lines)
