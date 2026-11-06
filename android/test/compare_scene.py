#!/usr/bin/python3
"""Paints the same seeds in Python and Java and counts the pixels that differ."""
import os, subprocess, sys, random
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ROOT)
from wallbloom import palette, scene as S
classes, java = sys.argv[1], sys.argv[2]
for seed in (1, 42, 987654321, 12345):
    sc = S.Scene(480, 270, seed); rng = random.Random(seed); S._layout(sc, rng); S._paint(sc, rng)
    out = os.path.join(classes, "dump.bin")
    subprocess.run([java, "-cp", classes, "SceneDump", str(seed), out], check=True)
    raw = open(out, "rb").read(); n = 480 * 270
    names = ("base", "fg", "ground", "water"); total = 0
    res = []
    for k, plane in enumerate((sc.base, sc.fg, sc.ground, sc.water)):
        d = sum(1 for a, b in zip(plane, raw[k * n:(k + 1) * n]) if a != b); total += d; res.append(f"{names[k]}:{d}")
    print(seed, "differences", total, *res, "moon", sc.moon, "pond", sc.pond, "lichen", len(sc.lichen))
