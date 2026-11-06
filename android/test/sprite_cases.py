#!/usr/bin/python3
"""Writes sprites drawn by the Python builders, for SpriteCheck to compare against."""
import json, os, random, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, ROOT)
from wallbloom import sprites
r = random.Random(7); out = []
for kind in sprites.BUILDERS:
    for k in range(60):
        g = {"size": r.random(), "bushy": r.random(), "tone": r.random(), "hue": r.randrange(6), "bloom": r.random(), "wet": r.random(), "dark": r.random(), "toxin": r.random(), "shape": r.random(), "cluster": r.random(), "slender": r.random()}
        seed = r.randrange(1 << 30); scale = 0.62 + r.random() * 0.95; light = r.choice((-1, 1))
        pix = sprites.bevel(sprites.BUILDERS[kind](g, seed, scale), light)
        out.append({"kind": kind, "g": g, "seed": seed, "scale": scale, "light": light, "pix": pix})
json.dump(out, open(sys.argv[1], "w"))
