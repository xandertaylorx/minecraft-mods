#!/usr/bin/env python3
"""
Generates the egg assets: textures for each colour and crack stage, block models, blockstates, item definitions,
loot tables and crafting recipes.

  python3 tools/gen_assets.py
"""
import json
import os
import random
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/pet_dragons")
DATA = os.path.join(ROOT, "src/main/resources/data/pet_dragons")

COLORS = {
    # shell, spots, crack glow, centre ingredient of the recipe
    "ember": dict(shell=(150, 36, 30), spot=(236, 150, 60), glow=(255, 190, 60), centre="minecraft:magma_block"),
    "frost": dict(shell=(110, 170, 226), spot=(232, 246, 255), glow=(200, 240, 255), centre="minecraft:blue_ice"),
    "verdant": dict(shell=(66, 150, 64), spot=(206, 232, 120), glow=(220, 255, 120), centre="minecraft:moss_block"),
    "void": dict(shell=(84, 44, 132), spot=(190, 130, 255), glow=(255, 120, 255), centre="minecraft:crying_obsidian"),
    "sun": dict(shell=(232, 184, 46), spot=(255, 244, 170), glow=(255, 250, 200), centre="minecraft:glowstone"),
}


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in img)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# cracks, drawn as a jagged line from the top down; each stage adds more
CRACKS = [
    [],
    [(8, 2), (7, 3), (7, 4), (6, 5), (6, 6), (5, 7)],
    [(8, 2), (7, 3), (7, 4), (6, 5), (6, 6), (5, 7), (9, 3), (10, 4), (10, 5), (11, 6), (4, 8), (4, 9), (3, 10)],
    [(8, 2), (7, 3), (7, 4), (6, 5), (6, 6), (5, 7), (9, 3), (10, 4), (10, 5), (11, 6), (4, 8), (4, 9), (3, 10), (6, 8), (7, 9),
     (7, 10), (8, 11), (9, 10), (10, 9), (11, 10), (12, 11), (5, 11), (4, 12), (9, 12), (10, 13)],
]


def egg_texture(name, pal, stage):
    rng = random.Random(hash(name) & 0xFFFF)
    img = [[(0, 0, 0, 255)] * 16 for _ in range(16)]
    spots = {(rng.randrange(16), rng.randrange(16)) for _ in range(22)}
    for y in range(16):
        for x in range(16):
            col = shade(pal["shell"], 0.9 + 0.2 * ((x + y) % 3) / 2 + (rng.random() - 0.5) * 0.08)
            if (x, y) in spots or ((x + 1, y) in spots and (x + y) % 2):
                col = shade(pal["spot"], 0.9 + rng.random() * 0.2)
            img[y][x] = col + (255,)
    for (x, y) in CRACKS[stage]:
        img[y][x] = pal["glow"] + (255,)
        if stage >= 2:
            for dx, dy in ((1, 0), (-1, 0)):
                px = x + dx
                if 0 <= px < 16 and img[y][px][:3] != pal["glow"]:
                    img[y][px] = shade(pal["shell"], 0.45) + (255,)
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def block_model(tex):
    # an egg built from four stacked boxes
    def el(a, b):
        return {"from": a, "to": b, "faces": {d: {"texture": "#egg"} for d in ("north", "south", "east", "west", "up", "down")}}

    return {
        "textures": {"egg": tex, "particle": tex},
        "elements": [el([4, 0, 4], [12, 3, 12]), el([3, 3, 3], [13, 10, 13]), el([5, 10, 5], [11, 13, 11]), el([6.5, 13, 6.5], [9.5, 15, 9.5])],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"scale": [0.8, 0.8, 0.8]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def main():
    for name, pal in COLORS.items():
        egg = f"{name}_dragon_egg"
        variants = {}
        for stage in range(4):
            tex = f"{egg}_{stage}"
            write_png(os.path.join(ASSETS, "textures/block", tex + ".png"), egg_texture(tex, pal, stage))
            write_json(os.path.join(ASSETS, "models/block", tex + ".json"), block_model(f"pet_dragons:block/{tex}"))
            variants[f"hatch={stage}"] = {"model": f"pet_dragons:block/{tex}"}
        write_json(os.path.join(ASSETS, "blockstates", egg + ".json"), {"variants": variants})
        write_json(os.path.join(ASSETS, "items", egg + ".json"), {"model": {"type": "minecraft:model", "model": f"pet_dragons:block/{egg}_0"}})
        write_json(os.path.join(DATA, "loot_table/blocks", egg + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"pet_dragons:{egg}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
        write_json(os.path.join(DATA, "recipe", egg + ".json"), {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "key": {"D": "minecraft:diamond", "E": "minecraft:ender_pearl", "X": pal["centre"]},
            "pattern": ["DED", "EXE", "DED"],
            "result": {"id": f"pet_dragons:{egg}"},
        })
    print("wrote egg assets for", ", ".join(COLORS))


if __name__ == "__main__":
    main()
