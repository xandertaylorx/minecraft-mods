#!/usr/bin/env python3
"""
Generates the dragon's model and its five skins from one description.

  python3 tools/gen_dragon.py

Writes:
  src/client/java/com/petdragons/client/model/DragonLayer.java   the model's geometry (parts and cubes)
  src/main/resources/assets/pet_dragons/textures/entity/dragon/<colour>.png

The model is described once below (parts, their pivots and cubes). Cubes are packed onto the texture sheet
automatically and painted by kind (scales, belly, horn, membrane, eye, spike), so there is no hand UV mapping.
"""
import math
import os
import random
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_OUT = os.path.join(ROOT, "src/client/java/com/petdragons/client/model/DragonLayer.java")
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/pet_dragons/textures/entity/dragon")

# ---------------------------------------------------------------------------------------------- the model
# Units are model pixels (16 to a block). y is down, the floor is y=24, and the dragon faces -z.

PARTS = []  # (name, parent, offset, rotation, [cubes])


def cube(kind, x, y, z, w, h, d):
    return {"kind": kind, "x": x, "y": y, "z": z, "w": w, "h": h, "d": d}


def part(name, parent, offset, cubes, rot=(0, 0, 0)):
    PARTS.append((name, parent, offset, rot, cubes))


# body, neck, head
part("body", "root", (0, 13, 0), [cube("scale", -5, -5, -8, 10, 10, 16)])
for i, z in enumerate((-6, -2, 2, 6)):
    part(f"spike_body{i}", "body", (0, -5, z), [cube("spike", -0.5, -2.5, -1, 1, 3, 2)])
part("neck", "root", (0, 10, -7), [cube("scale", -2.5, -10, -3, 5, 10, 6)], rot=(0.55, 0, 0))
for i, y in enumerate((-2, -5, -8)):
    part(f"spike_neck{i}", "neck", (0, y, 3), [cube("spike", -0.5, -1, 0, 1, 2, 2)])
part("head", "neck", (0, -10, 0), [cube("scale", -4, -3, -7, 8, 6, 7), cube("scale", -2.5, -1, -12, 5, 3, 5)])
part("jaw", "head", (0, 2.2, -2), [cube("scale", -2.5, 0, -10, 5, 1, 10)])
part("eye_l", "head", (4, -1.5, -4.5), [cube("eye", 0, -1, -1, 1, 2, 2)])
part("eye_r", "head", (-4, -1.5, -4.5), [cube("eye", -1, -1, -1, 1, 2, 2)])
part("horn_l", "head", (2.5, -3, -1), [cube("horn", -0.75, -0.75, 0, 2, 2, 7)], rot=(0.5, -0.12, 0))
part("horn_r", "head", (-2.5, -3, -1), [cube("horn", -1.25, -0.75, 0, 2, 2, 7)], rot=(0.5, 0.12, 0))
part("horn2_l", "head", (3.8, 0.5, -1), [cube("horn", -0.5, -0.5, 0, 1, 1, 4)], rot=(0.1, -0.5, 0))
part("horn2_r", "head", (-3.8, 0.5, -1), [cube("horn", -0.5, -0.5, 0, 1, 1, 4)], rot=(0.1, 0.5, 0))
# tail
part("tail0", "root", (0, 13, 8), [cube("scale", -3, -3, 0, 6, 6, 8)])
part("tail1", "tail0", (0, 0, 8), [cube("scale", -2.5, -2.5, 0, 5, 5, 8)])
part("tail2", "tail1", (0, 0, 8), [cube("scale", -2, -2, 0, 4, 4, 8)])
part("tail3", "tail2", (0, 0, 8), [cube("scale", -1.5, -1.5, 0, 3, 3, 7), cube("spike", -0.5, -0.5, 7, 1, 1, 3)])
for n, p in ((0, "tail0"), (1, "tail1"), (2, "tail2")):
    part(f"spike_tail{n}", p, (0, -3 + n * 0.5, 4), [cube("spike", -0.5, -2, -1, 1, 2, 2)])
# legs: upper and lower, a foot that reaches forward
for tag, x, y, z, big in (("fl", 5, 17, -5, False), ("fr", -5, 17, -5, False), ("bl", 5, 16, 6, True), ("br", -5, 16, 6, True)):
    if big:
        part(f"leg_{tag}", "root", (x, y, z), [cube("scale", -2, 0, -2, 4, 5, 4)])
        part(f"low_{tag}", f"leg_{tag}", (0, 5, 0), [cube("scale", -1.5, 0, -1.5, 3, 3, 3), cube("claw", -1.5, 2, -4.5, 3, 1, 3)])
    else:
        part(f"leg_{tag}", "root", (x, y, z), [cube("scale", -1.5, 0, -1.5, 3, 4, 3)])
        part(f"low_{tag}", f"leg_{tag}", (0, 4, 0), [cube("scale", -1.5, 0, -1.5, 3, 3, 3), cube("claw", -1.5, 2, -4.5, 3, 1, 3)])
# wings: an arm, a bent tip, and a membrane on each
for tag, sx in (("l", 1), ("r", -1)):
    x0 = 0 if sx > 0 else -14
    part(f"wing_{tag}", "root", (4 * sx, 9, -4), [cube("bone", x0, -1, -1.5, 14, 2, 3)])
    part(f"wmem_{tag}", f"wing_{tag}", (0, 0.5, 1.5), [cube("membrane", x0, 0, 0, 14, 1, 14)])
    tx0 = 0 if sx > 0 else -13
    part(f"tip_{tag}", f"wing_{tag}", (14 * sx, 0, 0), [cube("bone", tx0, -0.8, -1, 13, 2, 2)])
    part(f"tmem_{tag}", f"tip_{tag}", (0, 0.5, 1), [cube("membrane", tx0, 0, 0, 13, 1, 12)])

# ---------------------------------------------------------------------------------------------- packing


def footprint(c):
    w, h, d = math.ceil(c["w"]), math.ceil(c["h"]), math.ceil(c["d"])
    return 2 * (d + w), d + h


def pack(width, height):
    cubes = [c for (_, _, _, _, cs) in PARTS for c in cs]
    order = sorted(cubes, key=lambda c: -footprint(c)[1])
    x = y = row = 0
    for c in order:
        fw, fh = footprint(c)
        if x + fw > width:
            x, y, row = 0, y + row, 0
        c["u"], c["v"] = x, y
        x += fw
        row = max(row, fh)
        if y + fh > height:
            raise SystemExit("texture sheet too small")
    return cubes


# ---------------------------------------------------------------------------------------------- colours

PALETTES = {
    "ember": dict(main=(158, 34, 28), dark=(96, 18, 18), belly=(236, 176, 92), mem=(206, 66, 30), horn=(238, 228, 200), eye=(255, 214, 40), spike=(70, 14, 14)),
    "frost": dict(main=(96, 164, 222), dark=(44, 94, 164), belly=(228, 242, 252), mem=(150, 212, 242), horn=(244, 252, 255), eye=(255, 255, 255), spike=(200, 232, 250)),
    "verdant": dict(main=(64, 152, 62), dark=(30, 92, 42), belly=(196, 224, 122), mem=(124, 192, 72), horn=(210, 186, 126), eye=(255, 230, 50), spike=(110, 80, 40)),
    "void": dict(main=(86, 44, 134), dark=(36, 16, 72), belly=(154, 112, 204), mem=(112, 52, 172), horn=(214, 204, 255), eye=(255, 90, 255), spike=(24, 8, 48)),
    "sun": dict(main=(232, 182, 42), dark=(172, 112, 22), belly=(255, 238, 164), mem=(255, 212, 92), horn=(255, 250, 224), eye=(255, 92, 42), spike=(150, 90, 16)),
}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def scales(pal, base, x, y, rng):
    # brick-like scale rows with a little noise
    row = y // 3
    cell = (x + (row % 2) * 2) % 4
    f = 1.0
    if cell == 0:
        f = 0.80
    elif cell == 1:
        f = 1.06
    if y % 3 == 2:
        f *= 0.9
    f *= 1 + (rng.random() - 0.5) * 0.08
    return shade(base, f)


def faces(c):
    """Rectangles of a cube's faces on the sheet, in Minecraft's unfolded layout."""
    u, v = c["u"], c["v"]
    w, h, d = math.ceil(c["w"]), math.ceil(c["h"]), math.ceil(c["d"])
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(pal, cubes, size, seed):
    rng = random.Random(seed)
    img = [[(0, 0, 0, 0)] * size[0] for _ in range(size[1])]
    for c in cubes:
        kind = c["kind"]
        for name, (fx, fy, fw, fh) in faces(c).items():
            for yy in range(fh):
                for xx in range(fw):
                    X, Y = fx + xx, fy + yy
                    if kind == "scale":
                        base = pal["belly"] if name == "bottom" else pal["main"]
                        if name == "top":
                            base = shade(base, 0.9)
                        col = scales(pal, base, xx, yy, rng)
                        if name == "bottom":
                            col = shade(pal["belly"], 0.95 + 0.08 * ((xx + yy) % 2))
                    elif kind == "claw":
                        col = shade(pal["horn"], 0.85 + 0.15 * rng.random())
                    elif kind == "horn":
                        col = shade(pal["horn"], 1.0 - 0.35 * (yy / max(1, fh)) + 0.05 * rng.random())
                    elif kind == "spike":
                        col = shade(pal["spike"], 0.85 + 0.3 * rng.random())
                    elif kind == "bone":
                        col = shade(pal["dark"], 0.9 + 0.2 * rng.random())
                    elif kind == "eye":
                        col = pal["eye"]
                        if xx == fw // 2 and yy == fh // 2:
                            col = (20, 10, 10)
                    else:  # membrane: veins radiating from the arm, thicker toward the edge
                        base = pal["mem"]
                        vein = (xx * 2 + yy) % 7 == 0 or (xx - yy) % 9 == 0
                        col = shade(base, 0.72 if vein else 1.0 - 0.1 * (yy / max(1, fh)))
                        if name in ("top", "bottom"):
                            col = shade(col, 1.0 if name == "top" else 0.9)
                    img[Y][X] = col + (255,)
    return img


# ---------------------------------------------------------------------------------------------- png


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


# ---------------------------------------------------------------------------------------------- java


def f(v):
    s = repr(float(v))
    if s.endswith(".0"):
        s = s[:-2]
    return s + "F"


def java(size):
    out = []
    out.append("package com.petdragons.client.model;\n")
    out.append("import net.minecraft.client.model.geom.PartPose;")
    out.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    out.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    out.append("import net.minecraft.client.model.geom.builders.PartDefinition;\n")
    out.append("/** The dragon's geometry. Generated by tools/gen_dragon.py: edit the script, not this file. */")
    out.append("public final class DragonLayer {")
    out.append("\tprivate DragonLayer() {\n\t}\n")
    out.append("\tpublic static LayerDefinition create() {")
    out.append("\t\tMeshDefinition mesh = new MeshDefinition();")
    out.append("\t\tPartDefinition root = mesh.getRoot();")
    for name, parent, off, rot, cubes in PARTS:
        pose = f"PartPose.offsetAndRotation({f(off[0])}, {f(off[1])}, {f(off[2])}, {f(rot[0])}, {f(rot[1])}, {f(rot[2])})" if any(rot) else f"PartPose.offset({f(off[0])}, {f(off[1])}, {f(off[2])})"
        b = "CubeListBuilder.create()" + "".join(
            f".texOffs({c['u']}, {c['v']}).addBox({f(c['x'])}, {f(c['y'])}, {f(c['z'])}, {f(c['w'])}, {f(c['h'])}, {f(c['d'])})" for c in cubes)
        out.append(f"\t\tPartDefinition {name} = {parent}.addOrReplaceChild(\"{name}\", {b}, {pose});")
    out.append(f"\t\treturn LayerDefinition.create(mesh, {size[0]}, {size[1]});")
    out.append("\t}")
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    size = (128, 128)
    cubes = pack(*size)
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w") as fh:
        fh.write(java(size))
    for i, (name, pal) in enumerate(PALETTES.items()):
        write_png(os.path.join(TEX_DIR, name + ".png"), paint(pal, cubes, size, 100 + i))
    used = max(c["v"] + footprint(c)[1] for c in cubes)
    print(f"{len(PARTS)} parts, {len(cubes)} cubes, sheet {size[0]}x{size[1]} (rows used: {used})")


if __name__ == "__main__":
    main()
