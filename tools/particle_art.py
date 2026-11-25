"""The mod's own particles, drawn again: soft-edged sprites painted from shapes rather than laid pixel by pixel, each
with a bright core that fades out through its colour to clear, and more frames, so they flicker, swell and die away
instead of stepping. 8x8, the size of the game's own particles."""
import glob
import math
import os
import random

from PIL import Image

MOD = "realms_of_chaos"
S = 8


def blend(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(fn):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            c = fn(x + 0.5, y + 0.5)
            if c is not None and c[3] > 8:
                img.putpixel((x, y), (c[0], c[1], c[2], int(c[3])))
    return img


def glow_shape(d, core, mid, edge, size):
    """Colour and alpha at distance d (0 at the heart) of a glowing thing of a given size: white-hot core, its colour,
    a dark rim, then clear."""
    if d > size: return None
    k = d / size
    if k < 0.3: return blend(core, mid, k / 0.3) + (255,)
    if k < 0.75: return blend(mid, edge, (k - 0.3) / 0.45) + (int(255 - (k - 0.3) * 200),)
    return edge + (int(165 * (1 - (k - 0.75) / 0.25)),)


def flame(core, mid, edge, frames=6, seed=1):
    out = []
    rnd = random.Random(seed)
    for f in range(frames):
        life = f / (frames - 1)
        sway = math.sin(f * 1.7) * 0.6
        size = 3.6 * (1 - life * 0.6)

        def fn(x, y, sway=sway, size=size):
            # a teardrop: round below, drawn up to a point that sways
            cy = 5.2 - (1 - size / 3.6) * 1.2
            dx = x - 4 - sway * (cy - y) / 4
            dy = y - cy
            if dy < 0: dx *= 1 + (-dy) * 0.35          # narrowing toward the tip
            d = math.hypot(dx, dy * (0.6 if dy < 0 else 1.0))
            return glow_shape(d, core, mid, edge, size)
        out.append(paint(fn))
    return out


def star(core, mid, edge, frames=5):
    out = []
    for f in range(frames):
        s = [0.6, 1.0, 0.85, 0.6, 0.35][f % 5] if frames == 5 else 1 - f / frames

        def fn(x, y, s=s):
            dx, dy = abs(x - 4), abs(y - 4)
            ray = min(dx, dy)                          # the four rays
            d = math.hypot(dx, dy)
            if ray < 0.6 and d < 3.9 * s: return glow_shape(d * 0.9, core, mid, edge, 3.9 * s)
            if d < 1.6 * s: return glow_shape(d, core, mid, edge, 1.6 * s)
            if abs(dx - dy) < 0.5 and d < 2.4 * s: return edge + (120,)
            return None
        out.append(paint(fn))
    return out


def spark(core, mid, edge, frames=5, seed=3):
    out = []
    for f in range(frames):
        rnd = random.Random(seed + f)
        pts = set()
        for arm in range(4):
            a = arm * math.pi / 2 + rnd.uniform(-0.5, 0.5)
            x, y = 4.0, 4.0
            for step in range(4 - f // 2):
                x += math.cos(a) + rnd.uniform(-0.5, 0.5)
                y += math.sin(a) + rnd.uniform(-0.5, 0.5)
                pts.add((int(x), int(y)))

        def fn(x, y, pts=pts):
            xi, yi = int(x), int(y)
            if (xi, yi) == (4, 4) or (xi, yi) == (3, 3): return core + (255,)
            if (xi, yi) in pts: return mid + (235,)
            if any((xi + dx, yi + dy) in pts for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): return edge + (110,)
            return None
        out.append(paint(fn))
    return out


def drop(core, mid, edge, frames=4):
    out = []
    for f in range(frames):
        size = 3.2 - f * 0.55

        def fn(x, y, size=size):
            dy = y - 4.6
            dx = (x - 4) * (1 + max(0, -dy) * 0.45)
            d = math.hypot(dx, dy * (0.7 if dy < 0 else 1))
            if d > size: return None
            c = blend(mid, edge, d / size)
            if (x - 3.2) ** 2 + (y - 4.0) ** 2 < 0.8: c = core
            return c + (int(255 - d / size * 90),)
        out.append(paint(fn))
    return out


def puff(core, mid, edge, frames=5, seed=7):
    out = []
    rnd = random.Random(seed)
    turn = rnd.uniform(0, math.pi)
    lumps = [(4.0, 4.0, 1.9)] + [(4 + math.cos(turn + k * 1.047) * 1.5, 4 + math.sin(turn + k * 1.047) * 1.5, 1.15 + 0.2 * (k % 2)) for k in range(6)]
    for f in range(frames):
        grow = 0.7 + f * 0.25
        fade = 1 - f / frames

        def fn(x, y, grow=grow, fade=fade):
            best = 0.0
            for lx, ly, lr in lumps:
                cx, cy = 4 + (lx - 4) * grow, 4 + (ly - 4) * grow
                k = 1 - math.hypot(x - cx, y - cy) / (lr * grow)
                best = max(best, k)
            if best <= 0: return None
            return blend(edge, mid if best < 0.6 else core, best) + (int(220 * fade * min(1, best * 2.2)),)
        out.append(paint(fn))
    return out


def ring(core, mid, edge, frames=4):
    out = []
    for f in range(frames):
        r = 0.8 + f * 1.0

        def fn(x, y, r=r, f=f):
            d = math.hypot(x - 4, y - 4)
            if f == 0:
                return glow_shape(min(abs(x - 4), abs(y - 4)) + d * 0.3, core, mid, edge, 3.0)
            k = abs(d - r)
            if k > 0.8: return None
            return blend(core, edge, k / 0.8 + f * 0.15) + (int((255 - f * 50) * (1 - k / 0.8)),)
        out.append(paint(fn))
    return out


def flake(core, mid, edge, frames=3):
    """A snowflake, drawn by hand at this size: six arms with barbs, then smaller as it melts."""
    maps = [["...#....", ".#.#.#..", "..###...", "###@###.", "..###...", ".#.#.#..", "...#....", "........"],
            ["........", "...#....", ".#.#.#..", "..#@#...", ".#.#.#..", "...#....", "........", "........"],
            ["........", "........", "...#....", "..#@#...", "...#....", "........", "........", "........"]]
    out = []
    for m in maps[:frames]:
        def fn(x, y, m=m):
            ch = m[int(y)][int(x)]
            if ch == "@": return core + (255,)
            if ch == "#": return mid + (235,)
            if any(0 <= int(x) + dx < 8 and 0 <= int(y) + dy < 8 and m[int(y) + dy][int(x) + dx] in "#@" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                return edge + (90,)
            return None
        out.append(paint(fn))
    return out


def mote(core, mid, edge, frames=5):
    out = []
    for f in range(frames):
        turn = f * math.pi / 2.5

        def fn(x, y, turn=turn):
            dx, dy = x - 4, y - 4
            d = math.hypot(dx, dy)
            if d < 1.2: return core + (255,)
            a = math.atan2(dy, dx) - turn
            spiral = abs(((a - d * 1.3) % (math.pi)) - math.pi / 2)
            if d < 3.6 and spiral < 0.5: return blend(mid, edge, d / 3.6) + (int(230 - d * 40),)
            return None
        out.append(paint(fn))
    return out


def ember(core, mid, edge, frames=4):
    out = []
    for f in range(frames):
        size = 2.6 - f * 0.45
        out.append(paint(lambda x, y, size=size: glow_shape(math.hypot(x - 4, y - 4), core, mid, edge, size)))
    return out


def rune(colours, frames=4):
    glyphs = [["........", ".####...", ".#..#...", ".####...", ".#.#....", ".#..#...", "........", "........"],
              ["........", "...#....", "..###...", "...#....", "..#.#...", ".#...#..", "........", "........"],
              ["........", ".#...#..", "..#.#...", "...#....", "..#.#...", ".#...#..", "........", "........"],
              ["........", ".#####..", "...#....", "...#....", "...#....", ".#####..", "........", "........"]]
    out = []
    for f in range(frames):
        g = glyphs[f % len(glyphs)]
        core, halo = colours[f % len(colours)], colours[-1]
        fade = 1 - f * 0.18

        def fn(x, y, g=g, core=core, halo=halo, fade=fade):
            xi, yi = int(x), int(y)
            if g[yi][xi] == "#": return core + (int(255 * fade),)
            if any(0 <= xi + dx < 8 and 0 <= yi + dy < 8 and g[yi + dy][xi + dx] == "#" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                return halo + (int(110 * fade),)
            return None
        out.append(paint(fn))
    return out


KINDS = {
    "storm_spark": lambda: spark((255, 255, 255), (250, 244, 160), (120, 170, 255)),
    "sea_spray": lambda: drop((255, 255, 255), (130, 220, 228), (36, 110, 150)),
    "water_drop": lambda: drop((236, 246, 255), (100, 160, 236), (36, 74, 170)),
    "soul_wisp": lambda: flame((240, 252, 255), (120, 220, 255), (50, 110, 210), seed=4),
    "flame_storm": lambda: flame((255, 252, 220), (255, 196, 70), (220, 100, 24), seed=5),
    "flame_sea": lambda: flame((236, 255, 252), (100, 236, 222), (24, 140, 160), seed=6),
    "flame_shade": lambda: flame((244, 234, 255), (176, 126, 255), (90, 44, 180), seed=7),
    "glint": lambda: star((255, 255, 255), (255, 238, 150), (230, 170, 50)),
    "rune": lambda: rune([(190, 240, 255), (170, 225, 255), (150, 205, 250), (110, 160, 230)]),
    "strike": lambda: ring((255, 255, 240), (255, 238, 170), (255, 200, 100)),
    "dust": lambda: puff((214, 206, 192), (176, 168, 154), (126, 120, 110), seed=8),
    "frost": lambda: flake((255, 255, 255), (214, 238, 255), (150, 200, 250)),
    "chaos_mote": lambda: mote((255, 230, 255), (214, 120, 255), (110, 50, 180)),
    "smoke": lambda: puff((150, 146, 142), (112, 108, 104), (70, 66, 64), seed=9),
    "gloom": lambda: puff((150, 100, 190), (88, 48, 120), (40, 20, 60), seed=10),
    "ember": lambda: ember((255, 250, 210), (255, 170, 60), (200, 70, 24)),
}


def gen(assets, wj):
    P = os.path.join(assets, "textures/particle")
    D = os.path.join(assets, "particles")
    for old in glob.glob(os.path.join(P, "*.png")):
        os.remove(old)
    for pid, make in KINDS.items():
        frames = make()
        names = []
        for n, img in enumerate(frames):
            img.save(os.path.join(P, f"{pid}_{n}.png"))
            names.append(f"{MOD}:{pid}_{n}")
        wj(os.path.join(D, f"{pid}.json"), {"textures": names})


def sheet(assets, out):
    """A sheet of every particle's frames, large, for looking at."""
    P = os.path.join(assets, "textures/particle")
    rows = list(KINDS)
    img = Image.new("RGBA", (8 * 6 * 10 + 20, len(rows) * 90 + 20), (24, 24, 30, 255))
    for r, pid in enumerate(rows):
        for n in range(6):
            p = os.path.join(P, f"{pid}_{n}.png")
            if not os.path.exists(p): continue
            spr = Image.open(p).resize((80, 80), Image.NEAREST)
            img.alpha_composite(spr, (10 + n * 80, 10 + r * 90))
    img.save(out)
