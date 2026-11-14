#!/usr/bin/env python3
"""Draws every texture in the mod (no image libraries needed): python3 tools/textures.py

fx/puff.png      a soft, ragged ball of cloud (smoke, fire and storm cloud are all drawn with it, tinted)
fx/streak.png    streaks of wind that tile both ways (the tornado's sleeves)
block/rift_portal.png (+ .mcmeta)  the portal sheet, a 32-frame violet swirl
item/*.png       the five items, 16x16 pixel art
icon.png         a black hole, 128x128
"""
import math
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'mass_destruction')


def png(path, w, h, pixels):
    """pixels: list of rows of (r, g, b, a) 0..255."""
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in pixels)
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(data)


def clamp(x, a=0.0, b=1.0):
    return a if x < a else b if x > b else x


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


class Noise:
    """Tileable value noise on a grid of `period` cells."""
    def __init__(self, seed, period):
        r = random.Random(seed)
        self.p = period
        self.v = [[r.random() for _ in range(period)] for _ in range(period)]

    def at(self, x, y):
        p = self.p
        ix, iy = math.floor(x), math.floor(y)
        fx, fy = x - ix, y - iy
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        v = self.v
        a, b = v[iy % p][ix % p], v[iy % p][(ix + 1) % p]
        c, d = v[(iy + 1) % p][ix % p], v[(iy + 1) % p][(ix + 1) % p]
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def fbm(noises, x, y):
    s, amp, tot = 0.0, 1.0, 0.0
    for i, n in enumerate(noises):
        f = 2 ** i
        s += n.at(x * f, y * f) * amp
        tot += amp
        amp *= 0.5
    return s / tot


def puff():
    n = [Noise(10 + i, 8 * 2 ** i) for i in range(4)]
    W = 64
    rows = []
    for y in range(W):
        row = []
        for x in range(W):
            u, v = (x + 0.5) / W * 2 - 1, (y + 0.5) / W * 2 - 1
            r = math.hypot(u, v)
            f = fbm(n, x / W * 8, y / W * 8)
            edge = 0.62 + 0.38 * f
            a = smooth(edge, edge - 0.45, r)
            # lit a little from above, darker in the folds
            shade = 0.72 + 0.28 * f - 0.12 * v
            c = int(clamp(shade) * 255)
            row.append((c, c, c, int(clamp(a) * 255)))
        rows.append(row)
    png(os.path.join(ROOT, 'textures/fx/puff.png'), W, W, rows)


def streak():
    W = 64
    n = [Noise(30 + i, 4 * 2 ** i) for i in range(4)]
    m = [Noise(50 + i, 32 * 2 ** i) for i in range(2)]
    rows = []
    for y in range(W):
        row = []
        for x in range(W):
            # stretched along u (round the funnel), slanted a little
            fu, fv = x / W, y / W
            s = fbm(n, fu * 4, (fv + fu * 0.25) * 4 * 1)
            t = fbm(m, fu * 32, (fv + fu * 0.25) * 32)
            band = fbm(n, 0.0 + fu * 0.0 + 3.3, (fv + fu * 0.25) * 16)
            a = clamp(0.25 + 0.9 * (s - 0.35) + 0.6 * (band - 0.5) + 0.2 * (t - 0.5))
            c = int(clamp(0.7 + 0.3 * t) * 255)
            row.append((c, c, c, int(a * 255)))
        rows.append(row)
    png(os.path.join(ROOT, 'textures/fx/streak.png'), W, W, rows)


def portal():
    W, F = 16, 32
    n = [Noise(70 + i, 4 * 2 ** i) for i in range(3)]
    rows = []
    for f in range(F):
        ph = f / F
        for y in range(W):
            row = []
            for x in range(W):
                u, v = (x + 0.5) / W - 0.5, (y + 0.5) / W - 0.5
                r, a = math.hypot(u, v), math.atan2(v, u)
                # a swirl turning inward, and a slow drift so it is never still
                sw = fbm(n, (a / (2 * math.pi) + r * 1.6 - ph) * 4 % 4, (r * 3 + ph * 2) % 4)
                k = clamp(0.25 + 1.1 * (sw - 0.35))
                deep = (0.18, 0.04, 0.38)
                mid = (0.55, 0.2, 0.95)
                hot = (0.75, 0.9, 1.0)
                col = [deep[i] + (mid[i] - deep[i]) * smooth(0.25, 0.6, k) for i in range(3)]
                col = [col[i] + (hot[i] - col[i]) * smooth(0.75, 0.95, k) for i in range(3)]
                alpha = 0.62 + 0.3 * k
                row.append(tuple(int(clamp(c) * 255) for c in col) + (int(alpha * 255),))
            rows.append(row)
    png(os.path.join(ROOT, 'textures/block/rift_portal.png'), W, W * F, rows)
    open(os.path.join(ROOT, 'textures/block/rift_portal.png.mcmeta'), 'w').write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')


def sprite(name, art, palette):
    rows = []
    for line in art:
        line = line.ljust(16, '.')
        rows.append([palette.get(ch, (0, 0, 0, 0)) for ch in line[:16]])
    while len(rows) < 16:
        rows.append([(0, 0, 0, 0)] * 16)
    png(os.path.join(ROOT, 'textures/item', name + '.png'), 16, 16, rows)


def items():
    O = (24, 18, 34, 255)  # outline
    # Annihilator: a hand-held orbital uplink: dark body, cyan barrel and a glowing dish
    sprite('annihilator', [
        '...........OOO..',
        '..........OwcWO.',
        '.........OcWwcO.',
        '........OcwcWO..',
        '.......OcwcO....',
        '......OgGgO.....',
        '.....OgGgO......',
        '....OdgGdO......',
        '...OdddgO.......',
        '..OdDdddO.......',
        '.OdDdOOO........',
        '.OdDO...........',
        'OdDO............',
        'OddO............',
        '.OO.............',
    ], {'O': O, 'd': (58, 64, 92, 255), 'D': (92, 104, 140, 255), 'g': (40, 120, 200, 255), 'G': (90, 200, 255, 255),
        'c': (120, 230, 255, 255), 'w': (225, 250, 255, 255), 'W': (255, 255, 255, 255)})
    # Nova Rod: a blackened rod with a burning orb
    sprite('nova_rod', [
        '...........OOO..',
        '..........OyYyO.',
        '.........OyWYrO.',
        '.........OYYyrO.',
        '..........OrrO..',
        '.........OoO....',
        '........ObO.....',
        '.......ObO......',
        '......ObO.......',
        '.....ObO........',
        '....ObO.........',
        '...ObO..........',
        '..OkO...........',
        '.OkO............',
        '.OO.............',
    ], {'O': O, 'b': (60, 40, 36, 255), 'k': (34, 24, 22, 255), 'o': (200, 90, 30, 255), 'r': (210, 60, 20, 255),
        'y': (255, 160, 40, 255), 'Y': (255, 220, 90, 255), 'W': (255, 250, 220, 255)})
    # Singularity Core: a black sphere with a bright disc across it
    sprite('singularity_core', [
        '................',
        '.....OOOOOO.....',
        '....OkkkkkkO....',
        '...OkkkkkkkkO...',
        '..vpkkkkkkkkpv..',
        '.vpyWykkkkyWypv.',
        'vpyWWWyyyyWWWypv',
        '.vpyWykkkkyWypv.',
        '..vpkkkkkkkkpv..',
        '...OkkkkkkkkO...',
        '....OkkkkkkO....',
        '.....OOOOOO.....',
    ], {'O': (60, 30, 90, 255), 'k': (8, 4, 14, 255), 'v': (90, 40, 170, 255), 'p': (200, 90, 220, 255),
        'y': (255, 190, 90, 255), 'W': (255, 248, 230, 255)})
    # Storm Caller: a staff with a whirl of wind at its head
    sprite('storm_caller', [
        '.........ssssss.',
        '........sWWWWs..',
        '.........swwWs..',
        '..........swWs..',
        '...........sws..',
        '..........OlO...',
        '.........OlO....',
        '........OlO.....',
        '.......OlO......',
        '......OlO.......',
        '.....OlO........',
        '....OlO.........',
        '...OlO..........',
        '..OLO...........',
        '..OO............',
    ], {'O': O, 'l': (110, 90, 70, 255), 'L': (80, 64, 50, 255), 's': (120, 140, 160, 255), 'w': (190, 210, 225, 255),
        'W': (240, 248, 255, 255)})
    # Rift Key: a gold key with an amethyst bow
    sprite('rift_key', [
        '................',
        '..........OOO...',
        '.........OpPpO..',
        '........OpWpPO..',
        '........OPpPpO..',
        '.........OgpO...',
        '........OgO.....',
        '.......OgO......',
        '......OgO.......',
        '.....OgO........',
        '....OgGO........',
        '...OgOGgO.......',
        '..OgO.OO........',
        '..OGgO..........',
        '...OO...........',
    ], {'O': O, 'g': (220, 170, 50, 255), 'G': (255, 220, 110, 255), 'p': (150, 80, 220, 255), 'P': (200, 140, 255, 255),
        'W': (245, 235, 255, 255)})


def icon():
    W = 128
    n = [Noise(90 + i, 8 * 2 ** i) for i in range(4)]
    rows = []
    for y in range(W):
        row = []
        for x in range(W):
            u, v = (x + 0.5) / W * 2 - 1, (y + 0.5) / W * 2 - 1
            Rh = 0.24
            # the disc, seen nearly edge-on, and its image bent round the hole
            dy = v / 0.28
            rd = math.hypot(u, dy)
            col = [0.03, 0.01, 0.07]
            def tone(t):
                c = [0.16, 0.05, 0.62]
                for tgt, e0, e1 in (((0.7, 0.25, 1.0), 0.2, 0.26), ((1.0, 0.4, 0.6), 0.3, 0.36), ((1.0, 0.62, 0.2), 0.36, 0.43), ((1.0, 0.96, 0.88), 0.62, 0.72)):
                    k = smooth(e0, e1, t)
                    c = [c[i] + (tgt[i] - c[i]) * k for i in range(3)]
                return c
            glow = 0.0
            r = math.hypot(u, v)
            # stars behind
            if random.Random(x * 131 + y * 7).random() > 0.985:
                col = [0.8, 0.8, 1.0]
            # bent ring
            if Rh * 1.05 < r < Rh * 1.9:
                uu = (r - Rh * 1.05) / (Rh * 0.85)
                a = math.atan2(v, u)
                t = (1 - uu) * (0.3 + 1.3 * fbm(n, (a / math.pi + 1) * 4, uu * 3)) * (0.4 + 0.6 * abs(math.sin(a)))
                c = tone(t)
                al = smooth(0.08, 0.17, t)
                col = [col[i] * (1 - al) + c[i] * al for i in range(3)]
            # front of the disc, in front of the hole
            in_front = v > 0 or r > Rh
            if Rh * 1.35 < rd < Rh * 4.4 and (in_front):
                uu = (rd - Rh * 1.35) / (Rh * 3.05)
                a = math.atan2(dy, u)
                t = (1 - uu) * (0.28 + 1.45 * fbm(n, (a / math.pi + 1) * 6, uu * 6))
                c = tone(t)
                al = smooth(0.1, 0.17, t) * smooth(0, 0.05, uu)
                col = [col[i] * (1 - al) + c[i] * al for i in range(3)]
            elif r < Rh:
                col = [0.0, 0.0, 0.0]
            if Rh * 0.98 < r < Rh * 1.08:
                col = [min(1, col[i] + (1.0, 0.9, 0.8)[i] * 0.8) for i in range(3)]
            # rounded square
            ax, ay = abs(u), abs(v)
            corner = math.hypot(max(ax - 0.8, 0), max(ay - 0.8, 0))
            alpha = 255 if corner < 0.2 else 0
            row.append(tuple(int(clamp(c) * 255) for c in col) + (alpha,))
        rows.append(row)
    png(os.path.join(ROOT, 'icon.png'), W, W, rows)


if __name__ == '__main__':
    puff()
    streak()
    portal()
    items()
    icon()
    print('textures written to', os.path.normpath(ROOT))
