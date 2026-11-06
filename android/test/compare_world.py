#!/usr/bin/python3
"""Grows the same forest in Python and Java and shows whether their states agree, tick by tick."""
import os, subprocess, sys, tempfile
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ROOT)
os.environ["XDG_CACHE_HOME"] = tempfile.mkdtemp(); os.environ["XDG_STATE_HOME"] = tempfile.mkdtemp(); os.environ["XDG_CONFIG_HOME"] = tempfile.mkdtemp()
from wallbloom import config as C, world as W
classes, java, seed, ticks, tod, season = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5], sys.argv[6]
def state(e):
    h = 17
    for p in e.plants:
        h = (h * 31 + p.x * 7 + p.y * 11 + p.seed + p.shown) & 0xFFFFFFFFFFFFFFFF
    comp = 0
    for v in e.c.comp:
        comp = (comp * 31 + v) & 0xFFFFFFFF
    return f"plants={len(e.plants)} hash={h & 0xffffff} moss={e.moss_count} myc={len(e.myc_node)} col={len(e.colonies)} beetles={len(e.beetles)} moths={len(e.moths)} comp={comp & 0xffffff}"
cfg = C.Config({"time_of_day": tod, "season": season, "seed": seed})
world = W.World(cfg, 480, 270, seed=seed, load=False)
e = world.eco
out = ["genesis " + state(e)]
for t in range(ticks):
    e.tick()
    if t % 500 == 499:
        out.append(f"tick{t+1} " + state(e))
d = tempfile.mkdtemp()
j = subprocess.run([java, "-cp", classes, "CompareSeeded", d, tod, season, str(seed), str(ticks)], capture_output=True, text=True)
if j.returncode: print(j.stderr[:3000])
jl = j.stdout.strip().split("\n")
for a, b in zip(out, jl):
    print(("same " if a == b else "DIFF ") + a + ("" if a == b else "\n     " + b))
