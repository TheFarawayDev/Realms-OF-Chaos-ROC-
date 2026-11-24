"""
Item art at 64x64.

Every icon is drawn as a handful of solids - capsules, polygons, ellipses, rings - each of one material, and then
cut into pixels: no anti-aliasing, so every edge is a clean pixel edge. Each solid is shaded from its own shape: the
light comes from the top left, so the edges that face it are lit and the far ones fall into shadow, a bevel runs
round every solid, metals carry a bright line just inside their lit edge, and every colour is drawn from a ramp of
five tones whose shadows lean cool and whose lights lean warm. A dark line is drawn round the whole icon and between
parts where one lies over another.

Run from generate.py after the older 16x16 art: it writes over the item textures.
"""
import json
import math
import os

from PIL import Image

N = 64          # the size the designs are drawn at
OUT = 16        # the size they are cut into: Minecraft's own, so items sit beside vanilla ones
LIGHT = (-0.62, -0.78)          # where the light comes from (x right, y down)


# ---- colour ------------------------------------------------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(clamp(v * f) for v in c[:3])


def ramp(c, glow=False):
    """Six tones of a colour: outline, deep shadow, shadow, base, light, highlight. Shadows lean blue-violet and
    lights lean warm, as a painter would have them."""
    if glow:
        return [shade(c, 0.55), mix(c, (255, 255, 255), 0.05), mix(c, (255, 255, 255), 0.18), mix(c, (255, 255, 255), 0.35),
                mix(c, (255, 255, 240), 0.6), (255, 255, 250)]
    cool, warm = (40, 34, 86), (255, 248, 214)
    return [mix(shade(c, 0.28), (18, 12, 30), 0.35), mix(shade(c, 0.55), cool, 0.22), mix(shade(c, 0.78), cool, 0.1), c,
            mix(c, warm, 0.32), mix(c, warm, 0.68)]


# ---- solids ------------------------------------------------------------------------------------------------------
# Each solid is a signed distance: negative inside, in pixels.

def circle(cx, cy, r):
    return lambda x, y: math.hypot(x - cx, y - cy) - r


def ellipse(cx, cy, rx, ry, angle=0.0):
    ca, sa = math.cos(angle), math.sin(angle)
    k = min(rx, ry)

    def f(x, y):
        dx, dy = x - cx, y - cy
        u, v = dx * ca + dy * sa, -dx * sa + dy * ca
        return (math.hypot(u / rx, v / ry) - 1) * k
    return f


def capsule(ax, ay, bx, by, r, r2=None):
    r2 = r if r2 is None else r2

    def f(x, y):
        vx, vy = bx - ax, by - ay
        L = vx * vx + vy * vy
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / L))
        return math.hypot(x - ax - vx * t, y - ay - vy * t) - (r + (r2 - r) * t)
    return f


def polygon(pts):
    def f(x, y):
        d = float("inf")
        inside = False
        n = len(pts)
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            vx, vy = bx - ax, by - ay
            L = vx * vx + vy * vy
            t = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * vx + (y - ay) * vy) / L))
            d = min(d, math.hypot(x - ax - vx * t, y - ay - vy * t))
            if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                inside = not inside
        return -d if inside else d
    return f


def ring(cx, cy, r, w):
    return lambda x, y: abs(math.hypot(x - cx, y - cy) - r) - w


def union(*fs):
    return lambda x, y: min(f(x, y) for f in fs)


def cut(a, b):
    return lambda x, y: max(a(x, y), -b(x, y))


def inter(a, b):
    return lambda x, y: max(a(x, y), b(x, y))


# ---- materials ---------------------------------------------------------------------------------------------------

class Mat:
    def __init__(self, color, kind="matte", glow=False):
        self.color, self.kind, self.glow = color, kind, glow
        self.tones = ramp(color, glow)


def metal(c): return Mat(c, "metal")
def gold(c=(236, 190, 70)): return Mat(c, "metal")
def wood(c=(120, 80, 48)): return Mat(c, "wood")
def gem(c): return Mat(c, "gem")
def glow(c): return Mat(c, "glow", True)
def cloth(c): return Mat(c, "cloth")
def matte(c): return Mat(c, "matte")


# ---- the painter -------------------------------------------------------------------------------------------------

class Icon:
    def __init__(self):
        self.parts = []          # (sdf, material, z, axis) - axis: a direction for wood grain and metal streaks

    def add(self, sdf, mat, z=0, axis=(0.707, -0.707)):
        self.parts.append((sdf, mat, z, axis))
        return self

    def render(self, glint=None, outline=True):
        """Cuts the solids into OUT x OUT pixels. Each pixel takes the topmost solid that covers at least a third of
        it (sampled four by four), so a shaft narrower than a pixel still gets its line of pixels. glint: a position
        along the icon's diagonal (0 at bottom left, 1 at top right) where a bright band crosses the metal."""
        K = N // OUT
        owner = [[None] * OUT for _ in range(OUT)]
        order = sorted(range(len(self.parts)), key=lambda i: self.parts[i][2])
        subs = [(i + 0.5) * K / 4 for i in range(4)]
        for y in range(OUT):
            for x in range(OUT):
                for i in reversed(order):
                    f = self.parts[i][0]
                    hits = sum(1 for sy in subs for sx in subs if f(x * K + sx, y * K + sy) < 0)
                    if hits >= 5:
                        owner[y][x] = i
                        break
        img = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0))
        pix = img.load()
        for y in range(OUT):
            for x in range(OUT):
                i = owner[y][x]
                if i is None:
                    continue
                sdf, mat, z, axis = self.parts[i]
                px, py = (x + 0.5) * K, (y + 0.5) * K
                h = 0.6 * K
                gx = sdf(px + h, py) - sdf(px - h, py)
                gy = sdf(px, py + h) - sdf(px, py - h)
                gl = math.hypot(gx, gy) or 1.0
                nx, ny = gx / gl, gy / gl                       # outward normal
                rim = -(nx * LIGHT[0] + ny * LIGHT[1])          # 1 where the edge faces the light
                d = max(0.05, -sdf(px, py) / K)                # depth in pixels
                if mat.kind == "gem":
                    s = 0.5 + 0.45 * rim if d < 1.2 else 0.45 + 0.5 * max(0.0, 1 - math.hypot(x - OUT * 0.4, y - OUT * 0.35) / (OUT * 0.5))
                elif d < 0.6:
                    s = 0.52 + 0.46 * rim
                elif d < 1.4:
                    s = 0.55 + 0.3 * rim
                else:
                    s = 0.56 + 0.12 * (1 - (x + y) / (2 * OUT))
                if mat.kind == "metal" and 0.6 <= d < 1.5 and rim > 0.25:
                    s = 0.98
                if mat.kind == "wood":
                    along = px * axis[0] + py * axis[1]
                    across = px * -axis[1] + py * axis[0]
                    s += 0.09 * math.sin(along * 0.55 + math.sin(across * 1.3) * 1.4)
                if mat.kind == "cloth" and ((x * 7 + y * 13) % 11 == 0):
                    s -= 0.12                                    # the odd darker thread in the weave
                if glint is not None and mat.kind == "metal":
                    g = (x + (OUT - y)) / (2 * OUT)
                    if abs(g - glint) < 0.05:
                        s = 1.1
                    elif abs(g - glint) < 0.09:
                        s = max(s, 0.86)
                if mat.glow:
                    s = 0.6 + 0.4 * min(1.0, d / 1.5)
                t = 1 if s < 0.26 else 2 if s < 0.44 else 3 if s < 0.66 else 4 if s < 0.86 else 5
                # where this part lies over another, a crisp line divides them
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    qx, qy = x + dx, y + dy
                    if 0 <= qx < OUT and 0 <= qy < OUT:
                        j = owner[qy][qx]
                        if j is not None and j != i and self.parts[j][2] < z and not mat.glow:
                            t = min(t, 2)
                pix[x, y] = mat.tones[t] + (255,)
        if outline:
            out = []
            for y in range(OUT):
                for x in range(OUT):
                    if owner[y][x] is not None:
                        continue
                    best = None
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        qx, qy = x + dx, y + dy
                        if 0 <= qx < OUT and 0 <= qy < OUT and owner[qy][qx] is not None:
                            c = self.parts[owner[qy][qx]][1].tones[0]
                            if best is None or sum(c) < sum(best):
                                best = c
                    if best is not None:
                        out.append((x, y, best))
            for x, y, c in out:
                pix[x, y] = c + (255,)
        return img


def sparkle(img, x, y, c=(255, 255, 250), size=2):
    """A star of light at a point given in design units."""
    p = img.load()
    x, y, size = int(x * OUT / N), int(y * OUT / N), max(0, size // 2)
    for k in range(-size, size + 1):
        for (qx, qy) in ((x + k, y), (x, y + k)):
            if 0 <= qx < OUT and 0 <= qy < OUT and p[qx, qy][3]:
                p[qx, qy] = (mix(p[qx, qy][:3], c, 0.9 if k == 0 else 0.55)) + (255,)


# ---- an axis for things held diagonally: bottom left (the butt) to top right (the tip) -------------------------

class Axis:
    def __init__(self, x0=8.0, y0=56.0, x1=56.0, y1=8.0):
        self.x0, self.y0 = x0, y0
        L = math.hypot(x1 - x0, y1 - y0)
        self.dx, self.dy = (x1 - x0) / L, (y1 - y0) / L
        self.px, self.py = -self.dy, self.dx           # across: to the right of the direction of travel
        self.L = L

    def at(self, u, v=0.0):
        return (self.x0 + self.dx * u + self.px * v, self.y0 + self.dy * u + self.py * v)

    def cap(self, u0, u1, r, r2=None, v=0.0):
        a, b = self.at(u0, v), self.at(u1, v)
        return capsule(a[0], a[1], b[0], b[1], r, r2)

    def poly(self, pts):
        return polygon([self.at(u, v) for u, v in pts])

    def dir(self):
        return (self.dx, self.dy)


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def save_frames(frames, path, mcmeta):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    strip = Image.new("RGBA", (OUT, OUT * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * OUT))
    strip.save(path)
    with open(path + ".mcmeta", "w") as fh:
        json.dump(mcmeta, fh, indent=2)
        fh.write("\n")
    return frames[0]


# ---- weapons -----------------------------------------------------------------------------------------------------

def blade(ic, ax, metal_m, trim_m, gem_m, grip_m, u_guard, blade_len, width, guard_w, grip=None, tip=0.0, curve=0.0, fuller=True,
          pommel=True, z=0):
    """A bladed weapon along the axis: pommel, grip, guard, and a blade that narrows to its point. curve bends the
    blade's spine (a cutlass, a sickle); tip > 0 makes the point broader (a leaf blade)."""
    g0 = 3.0
    grip = grip if grip is not None else u_guard - g0
    if pommel:
        ic.add(circle(*ax.at(g0, 0), 3.2), gem_m if gem_m else trim_m, z + 3)
        ic.add(circle(*ax.at(g0, 0), 4.2), trim_m, z + 2)
    ic.add(ax.cap(g0 + 2, u_guard, 2.3), grip_m, z + 1, ax.dir())
    for k in range(int(g0 + 4), int(u_guard - 1), 3):            # the grip's binding
        ic.add(ax.cap(k, k + 0.8, 2.7), shade_mat(grip_m, 0.8), z + 2)
    ic.add(ax.cap(u_guard, u_guard, 2.6, v=0) if guard_w <= 0 else
           capsule(*ax.at(u_guard, -guard_w), *ax.at(u_guard, guard_w), 2.4), trim_m, z + 4)
    if gem_m:
        ic.add(circle(*ax.at(u_guard, 0), 2.2), gem_m, z + 5)
    pts = []
    steps = 10
    for i in range(steps + 1):
        t = i / steps
        u = u_guard + 1.5 + blade_len * t
        bend = curve * math.sin(t * math.pi * 0.9) * 6
        half = width * (1 - t ** (1.6 if tip == 0 else 3.0)) + tip * math.sin(t * math.pi) * 2.5
        pts.append((u, -half + bend))
    for i in range(steps, -1, -1):
        t = i / steps
        u = u_guard + 1.5 + blade_len * t
        bend = curve * math.sin(t * math.pi * 0.9) * 6
        half = width * (1 - t ** (1.6 if tip == 0 else 3.0)) + tip * math.sin(t * math.pi) * 2.5
        pts.append((u, half + bend))
    ic.add(ax.poly(pts), metal_m, z + 3, ax.dir())
    if fuller and width >= 3:
        ic.add(ax.cap(u_guard + 3, u_guard + blade_len * 0.62, 0.7), shade_mat(metal_m, 0.72), z + 4)


def shade_mat(m, f):
    return Mat(shade(m.color, f), m.kind, m.glow)


def weapon_icon(shape, metal_c, trim_c, gem_c, wood_c):
    ic = Icon()
    ax = Axis()
    M, T, G, W = metal(metal_c), gold(trim_c), gem(gem_c), wood(wood_c)
    leather = cloth(mix(wood_c, (60, 36, 30), 0.5))
    if shape in ("sword", "gladius", "greatsword", "rapier", "dagger", "cutlass"):
        p = {"sword": (17, 38, 4.2, 7), "gladius": (15, 32, 5.0, 6), "greatsword": (14, 44, 5.4, 9),
             "rapier": (16, 42, 2.2, 6), "dagger": (19, 24, 3.6, 5), "cutlass": (16, 38, 4.4, 5)}[shape]
        blade(ic, ax, M, T, G, leather, p[0], p[1], p[2], p[3], tip=1.0 if shape == "gladius" else 0.0,
              curve=-0.9 if shape == "cutlass" else 0.0, fuller=shape != "rapier")
        if shape == "rapier":
            ic.add(ring(*ax.at(p[0] + 1, 2.5), 4.5, 0.9), T, 6)               # a swept hilt
        if shape == "cutlass":
            ic.add(ring(*ax.at(p[0] - 4, 3.5), 5.0, 0.9), T, 5)
    elif shape in ("spear", "trident"):
        ic.add(ax.cap(2, 46, 1.8), W, 1, ax.dir())
        for k in (14, 17, 30):
            ic.add(ax.cap(k, k + 1.2, 2.4), T, 2)
        ic.add(circle(*ax.at(2, 0), 2.6), T, 2)
        if shape == "spear":
            ic.add(ax.poly([(44, -5.5), (52, -4), (62, 0), (52, 4), (44, 5.5), (46, 0)]), M, 3, ax.dir())
            ic.add(ax.cap(44, 58, 0.6), shade_mat(M, 0.72), 4)
            ic.add(circle(*ax.at(44, 0), 2.4), G, 5)
        else:
            ic.add(capsule(*ax.at(44, -8), *ax.at(44, 8), 1.8), M, 3)
            for v in (-8, 0, 8):
                L = 60 if v == 0 else 56
                ic.add(ax.cap(44, L - 4, 1.6, v=v), M, 3, ax.dir())
                ic.add(ax.poly([(L - 6, v - 3.2), (L + 2, v), (L - 6, v + 3.2)]), M, 4, ax.dir())
            ic.add(circle(*ax.at(44, 0), 2.6), G, 5)
    elif shape in ("staff", "torch", "oar"):
        ic.add(ax.cap(2, 48 if shape != "oar" else 36, 1.9), W, 1, ax.dir())
        ic.add(circle(*ax.at(2, 0), 2.6), T, 2)
        if shape == "staff":
            ic.add(ring(*ax.at(54, 0), 7.0, 1.6), T, 3)
            ic.add(capsule(*ax.at(46, -4), *ax.at(46, 4), 1.6), T, 3)
            ic.add(circle(*ax.at(54, 0), 4.6), G, 4)
            for k in (14, 24, 34):
                ic.add(ax.cap(k, k + 1.2, 2.5), T, 2)
        elif shape == "torch":
            ic.add(ax.poly([(40, -5), (48, -6), (48, 6), (40, 5)]), T, 2, ax.dir())
            ic.add(ellipse(*ax.at(54, 0), 6.5, 9.5, math.atan2(ax.dy, ax.dx) + math.pi / 2), glow(mix(gem_c, (255, 150, 40), 0.5)), 3)
            ic.add(ellipse(*ax.at(55, 0), 3.5, 5.5, math.atan2(ax.dy, ax.dx) + math.pi / 2), glow((255, 244, 200)), 4)
        else:
            ic.add(ax.poly([(34, -2.5), (44, -8), (60, -7), (62, 0), (60, 7), (44, 8), (34, 2.5)]), W, 2, ax.dir())
            ic.add(ax.cap(38, 58, 0.8), shade_mat(W, 0.7), 3)
            ic.add(ax.cap(30, 32, 2.8), T, 3)
    elif shape in ("scythe", "sickle"):
        handle = 50 if shape == "scythe" else 22
        ic.add(ax.cap(2, handle, 2.0), W if shape == "scythe" else leather, 1, ax.dir())
        ic.add(circle(*ax.at(2, 0), 2.8), T, 2)
        top = ax.at(handle, 0)
        R = 22 if shape == "scythe" else 15
        cx, cy = top[0] - 4, top[1] + R * 0.55
        crescent = cut(circle(cx, cy - 2, R), circle(cx + 6, cy + 6, R))
        crescent = inter(crescent, polygon([(cx - R - 2, cy - R - 4), (cx + R + 4, cy - R - 4), (cx + R + 4, cy + 4), (cx - R - 2, cy + 4)]))
        ic.add(crescent, M, 3)
        ic.add(circle(*top, 3.0), T, 4)
        ic.add(circle(*top, 1.6), G, 5)
    elif shape == "bow":
        c = ax.at(30, 0)
        bowarc = inter(ring(c[0] + 12, c[1] + 12, 30, 2.2), polygon([(0, 0), (64, 0), (64, 6), (6, 64), (0, 64)]))
        ic.add(bowarc, W, 2)
        a, b = (6, 22), (42, 58)
        ic.add(capsule(a[0], a[1], b[0], b[1], 0.55), matte((236, 230, 214)), 1)
        ic.add(circle(c[0] - 9, c[1] - 9, 3.2), T, 3)
        ic.add(circle(c[0] - 9, c[1] - 9, 1.8), G, 4)
        for e in ((7, 21), (43, 57)):
            ic.add(circle(e[0] - 1, e[1] - 1, 2.4), T, 3)
        # an arrow nocked
        ic.add(capsule(18, 46, 50, 14, 1.0), W, 4)
        ic.add(polygon([(50, 10), (56, 8), (54, 14), (52, 16), (48, 14)]), M, 5)
        ic.add(polygon([(16, 44), (12, 46), (14, 50), (18, 50), (20, 48)]), cloth(gem_c), 5)
    elif shape == "hammer":
        ic.add(ax.cap(2, 42, 2.2), W, 1, ax.dir())
        for k in (6, 10):
            ic.add(ax.cap(k, k + 1, 2.8), leather, 2)
        ic.add(ax.poly([(38, -13), (52, -13), (52, 13), (38, 13)]), M, 3, ax.dir())
        ic.add(ax.poly([(36, -14), (39, -14), (39, 14), (36, 14)]), T, 4)
        ic.add(ax.poly([(51, -14), (54, -14), (54, 14), (51, 14)]), T, 4)
        ic.add(circle(*ax.at(45, 0), 3.2), G, 5)
    elif shape == "bolt":
        pts = [(8, 60), (22, 38), (16, 37), (36, 14), (30, 13), (56, 2), (36, 26), (42, 27), (24, 48), (30, 49)]
        ic.add(polygon(pts), metal((250, 204, 60)), 2)
        ic.add(polygon([(14, 54), (24, 40), (20, 40), (34, 20), (28, 20), (46, 8), (34, 24), (38, 25), (24, 44), (28, 45)]), glow((255, 255, 220)), 3)
    elif shape == "whip":
        ic.add(ax.cap(2, 18, 2.6), leather, 3, ax.dir())
        ic.add(circle(*ax.at(18, 0), 3.2), T, 4)
        ic.add(circle(*ax.at(2, 0), 3.0), T, 4)
        prev = ax.at(19, 0)
        for i in range(1, 25):
            t = i / 24
            p = (22 + t * 34 + math.sin(t * 5.5) * 5, 40 - t * 30 + math.sin(t * 6.5 + 1) * 5)
            ic.add(capsule(prev[0], prev[1], p[0], p[1], 2.0 - t * 0.9), leather, 2)
            prev = p
        ic.add(circle(prev[0], prev[1], 2.6), glow(gem_c), 3)
    elif shape == "shears":
        for side in (-1, 1):
            ic.add(ax.poly([(26, side * 1), (60, side * 5), (58, side * 7.5), (26, side * 4)]), M, 3, ax.dir())
            ic.add(ring(*ax.at(12, side * 7), 5.2, 1.6), T, 2)
            ic.add(ax.cap(14, 26, 1.6, v=side * 4), T, 2)
        ic.add(circle(*ax.at(26, 0), 2.6), G, 5)
    elif shape == "talon":
        # three hooked claws springing from a gauntlet
        ic.add(ellipse(20, 44, 13, 9, -0.75), leather, 2)
        ic.add(capsule(8, 58, 18, 46, 4.0), T, 3)
        for k in range(3):
            bx, by = 18 + k * 7, 34 + k * 3
            claw = cut(circle(bx + 12, by - 2, 13), circle(bx + 16, by + 3, 12.5))
            claw = inter(claw, polygon([(bx - 2, by + 6), (bx + 8, by - 18), (bx + 30, by - 18), (bx + 30, by + 6)]))
            ic.add(claw, M, 4 + k)
        ic.add(circle(20, 44, 2.6), G, 8)
    elif shape == "anchor":
        ic.add(capsule(32, 12, 32, 50, 2.6), M, 3)
        ic.add(capsule(22, 20, 42, 20, 2.0), M, 3)
        ic.add(ring(32, 8, 4.2, 1.6), T, 4)
        arc = inter(ring(32, 34, 18, 2.6), polygon([(0, 40), (64, 40), (64, 64), (0, 64)]))
        ic.add(arc, M, 3)
        ic.add(polygon([(10, 36), (16, 44), (12, 46)]), M, 4)
        ic.add(polygon([(54, 36), (48, 44), (52, 46)]), M, 4)
        ic.add(circle(32, 20, 2.4), G, 5)
    elif shape == "chalice":
        ic.add(polygon([(14, 12), (50, 12), (46, 28), (38, 34), (26, 34), (18, 28)]), T, 2)
        ic.add(ellipse(32, 13, 17, 3.4), glow(gem_c), 3)
        ic.add(capsule(32, 34, 32, 46, 2.6), T, 2)
        ic.add(ellipse(32, 52, 13, 4.2), T, 2)
        ic.add(circle(32, 24, 3.0), G, 4)
        for x in (22, 42):
            ic.add(circle(x, 21, 1.6), gem(mix(gem_c, (255, 255, 255), 0.2)), 4)
    elif shape == "conch":
        shell = union(ellipse(30, 34, 20, 13, -0.6), polygon([(40, 18), (56, 8), (52, 26)]))
        ic.add(shell, matte((238, 206, 184)), 2)
        for k in range(4):
            ic.add(ring(26 - k * 2, 38 + k, 4 + k * 3.2, 0.7), matte((206, 142, 132)), 3)
        ic.add(ellipse(22, 40, 6, 4, -0.6), matte((246, 170, 160)), 4)
        ic.add(circle(52, 12, 2.0), T, 4)
    elif shape == "feather":
        ic.add(capsule(10, 56, 46, 14, 1.2), matte((236, 232, 220)), 3)
        vane = polygon([(16, 46), (22, 30), (34, 16), (48, 8), (54, 12), (48, 24), (38, 36), (24, 48)])
        ic.add(vane, cloth(mix(metal_c, (255, 255, 255), 0.45)), 2)
        for k in range(6):
            a = (20 + k * 5, 44 - k * 5)
            ic.add(capsule(a[0], a[1], a[0] - 6, a[1] - 9, 0.5), cloth(shade(mix(metal_c, (255, 255, 255), 0.45), 0.8)), 3)
        ic.add(circle(48, 12, 2.2), G, 4)
    elif shape == "flute":
        ic.add(ax.cap(6, 58, 3.2), W, 2, ax.dir())
        for k in (8, 56):
            ic.add(ax.cap(k - 1, k + 1, 3.8), T, 3)
        for k in range(20, 50, 7):
            ic.add(circle(*ax.at(k, -0.6), 1.2), matte((40, 26, 18)), 4)
        ic.add(circle(*ax.at(14, 0), 1.6), G, 4)
    elif shape == "orb":
        ic.add(circle(32, 30, 19), gem(gem_c), 2)
        ic.add(ring(32, 30, 19.5, 1.4), T, 3)
        ic.add(ellipse(32, 54, 14, 5), T, 2)
        ic.add(polygon([(24, 47), (40, 47), (36, 52), (28, 52)]), T, 3)
    elif shape == "lantern":
        ic.add(polygon([(18, 22), (46, 22), (42, 54), (22, 54)]), glow(mix(gem_c, (255, 255, 255), 0.25)), 2)
        for x in (18, 32, 46):
            ic.add(capsule(x, 22, x + (24 - x) * 0.18 + (x - 18) * 0.18 * 0 if x != 32 else 32, 54, 1.2), T, 3)
        ic.add(capsule(18, 22, 46, 22, 2.4), T, 3)
        ic.add(capsule(22, 54, 42, 54, 2.4), T, 3)
        ic.add(ring(32, 12, 6, 1.4), T, 3)
        ic.add(polygon([(24, 22), (40, 22), (36, 16), (28, 16)]), T, 3)
    elif shape == "star":
        pts = []
        for k in range(10):
            a = -math.pi / 2 + k * math.pi / 5
            r = 26 if k % 2 == 0 else 11
            pts.append((32 + math.cos(a) * r, 33 + math.sin(a) * r))
        ic.add(polygon(pts), glow(mix(gem_c, (255, 255, 255), 0.15)), 2)
        ic.add(circle(32, 33, 6), glow((255, 255, 240)), 3)
    else:
        blade(ic, ax, M, T, G, leather, 17, 38, 4.2, 7)
    return ic


def weapon_frames(shape, metal_c, trim_c, gem_c, wood_c):
    ic = weapon_icon(shape, metal_c, trim_c, gem_c, wood_c)
    frames = [ic.render()]
    for k in range(6):
        frames.append(ic.render(glint=0.15 + k * 0.14))
    return frames


# ---- everything else ---------------------------------------------------------------------------------------------

GOLD_C = (236, 190, 70)
LINE_COL = {"zeus": (250, 210, 80), "poseidon": (70, 200, 220), "hades": (170, 90, 230)}


def pixel_symbol(ic, rows, x0, y0, cell, mat, z=6, accent=None):
    """An 8x8 symbol, each of its pixels a square of cell x cell."""
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch in "#+":
                m = accent if (ch == "+" and accent) else mat
                ic.add(polygon([(x0 + rx * cell, y0 + ry * cell), (x0 + (rx + 1) * cell, y0 + ry * cell),
                                (x0 + (rx + 1) * cell, y0 + (ry + 1) * cell), (x0 + rx * cell, y0 + (ry + 1) * cell)]), m, z)


def book(k):
    ic = Icon()
    ic.add(polygon([(12, 10), (50, 6), (54, 52), (16, 58)]), matte((236, 226, 200)), 1)            # the pages
    ic.add(polygon([(10, 8), (48, 4), (50, 50), (12, 56)]), cloth((92, 44, 150)), 2)                # the cover
    ic.add(polygon([(10, 8), (16, 7.5), (18, 55.3), (12, 56)]), cloth((60, 28, 104)), 3)            # the spine
    for (cx, cy) in ((14, 11), (46, 7), (48, 47), (15, 52)):
        ic.add(circle(cx, cy, 3.4), gold(), 4)
    for y in (14, 48):
        ic.add(capsule(13, y + 0.5, 16, y, 1.6), gold(), 4)
    # the sigil of chaos on its cover: a ring in the three gods' colours, a star of light in it
    for i, col in enumerate(LINE_COL.values()):
        a0 = i * math.tau / 3 + k * 0.4
        arc = inter(ring(31, 29, 11, 2.2), polygon([(31, 29), (31 + 30 * math.cos(a0), 29 + 30 * math.sin(a0)),
                                                     (31 + 30 * math.cos(a0 + math.tau / 3), 29 + 30 * math.sin(a0 + math.tau / 3))]))
        ic.add(arc, glow(col), 5)
    ic.add(circle(31, 29, 4 + 0.8 * math.sin(k * math.pi / 3)), glow((255, 250, 230)), 6)
    return ic.render()


def compass(k, face=(28, 24, 46), needle=(240, 70, 60), rim=GOLD_C, mark=None):
    ic = Icon()
    ic.add(circle(32, 33, 25), gold(rim), 1)
    ic.add(circle(32, 33, 20.5), matte(face), 2)
    ic.add(ring(32, 33, 15.5, 0.6), matte(shade(face, 1.8)), 3)
    for a in range(0, 360, 45):
        r = math.radians(a)
        ic.add(capsule(32 + math.cos(r) * 17, 33 + math.sin(r) * 17, 32 + math.cos(r) * 19.5, 33 + math.sin(r) * 19.5, 0.8 if a % 90 else 1.2),
               gold(rim), 3)
    ic.add(ring(32, 8, 4.4, 1.6), gold(rim), 1)
    ang = -math.pi / 4 + math.sin(k / 8 * math.tau) * 0.35
    tip = (32 + math.cos(ang) * 16, 33 + math.sin(ang) * 16)
    tail = (32 - math.cos(ang) * 12, 33 - math.sin(ang) * 12)
    px, py = -math.sin(ang) * 3.2, math.cos(ang) * 3.2
    ic.add(polygon([tip, (32 + px, 33 + py), (32 - px, 33 - py)]), glow(needle), 5)
    ic.add(polygon([tail, (32 + px, 33 + py), (32 - px, 33 - py)]), metal((206, 212, 224)), 4)
    ic.add(circle(32, 33, 2.6), gold(rim), 6)
    if mark:
        pixel_symbol(ic, mark, 24, 25, 2, glow(shade(needle, 0.7)), z=3)
    return ic.render()


def field_bag():
    ic = Icon()
    ic.add(inter(ring(32, 26, 18, 2.2), polygon([(0, 0), (64, 0), (64, 26), (0, 26)])), cloth((110, 74, 46)), 1)     # the strap
    ic.add(polygon([(12, 24), (52, 24), (54, 54), (50, 58), (14, 58), (10, 54)]), cloth((140, 92, 54)), 2)
    ic.add(polygon([(10, 22), (54, 22), (52, 38), (32, 42), (12, 38)]), cloth((116, 74, 42)), 3)                       # the flap
    ic.add(polygon([(28, 34), (36, 34), (36, 44), (28, 44)]), gold(), 4)
    ic.add(circle(32, 39, 2.4), glow((90, 240, 230)), 5)
    for x in (16, 48):
        ic.add(capsule(x, 44, x, 56, 0.9), cloth((96, 60, 34)), 4)                                                    # stitching
    return ic.render()


def essence(line, k):
    ic = Icon()
    col = LINE_COL[line]
    if line == "zeus":
        ic.add(polygon([(32, 6), (44, 26), (36, 28), (46, 52), (24, 30), (32, 28), (22, 12)]), gem(col), 2)
    elif line == "poseidon":
        ic.add(ellipse(32, 40, 20, 11), matte((236, 214, 196)), 1)
        ic.add(circle(32, 32, 13), gem(col), 2)
    else:
        ic.add(polygon([(32, 4), (42, 22), (46, 38), (38, 54), (26, 54), (18, 38), (24, 22)]), gem(col), 2)
        ic.add(polygon([(32, 20), (38, 34), (34, 48), (28, 48), (26, 34)]), glow((255, 180, 120)), 3)
    img = ic.render()
    for j in range(3):
        a = k / 6 * math.tau + j * math.tau / 3
        sparkle(img, int(32 + math.cos(a) * 9), int(30 + math.sin(a) * 12), size=1 + (j + k) % 2)
    return img


SIGILS = {
    "zeus": ["...##...", "..##....", ".##.....", "######..", "..####..", "...##...", "..##....", ".##....."],
    "poseidon": ["#..##..#", "#..##..#", "#..##..#", "########", "...##...", "...##...", "...##...", "...##..."],
    "hades": [".#....#.", ".#....#.", ".#....#.", ".######.", "...##...", "...##...", "...##...", "...##..."],
}


def keystone(line, k):
    ic = Icon()
    col = LINE_COL[line]
    stone = {"zeus": (226, 222, 212), "poseidon": (88, 164, 156), "hades": (62, 56, 74)}[line]
    ic.add(polygon([(32, 4), (56, 18), (56, 46), (32, 60), (8, 46), (8, 18)]), matte(stone), 1)
    ic.add(polygon([(32, 10), (50, 21), (50, 43), (32, 54), (14, 43), (14, 21)]), matte(shade(stone, 0.85)), 2)
    pixel_symbol(ic, SIGILS[line], 16, 16, 4, glow(mix(col, (255, 255, 255), 0.15 + 0.2 * math.sin(k / 6 * math.tau))), z=4)
    for (x, y) in ((32, 6), (54, 19), (54, 45), (32, 58), (10, 45), (10, 19)):
        ic.add(circle(x, y, 2.0), gold(), 3)
    return ic.render()


def doll(pin):
    ic = Icon()
    burlap = cloth((186, 150, 100))
    ic.add(circle(32, 16, 11), burlap, 2)
    ic.add(polygon([(22, 24), (42, 24), (44, 44), (20, 44)]), burlap, 1)
    ic.add(capsule(22, 28, 10, 38, 4.2), burlap, 1)
    ic.add(capsule(42, 28, 54, 38, 4.2), burlap, 1)
    ic.add(capsule(26, 44, 22, 58, 4.4), burlap, 1)
    ic.add(capsule(38, 44, 42, 58, 4.4), burlap, 1)
    ic.add(capsule(20, 34, 44, 34, 1.0), cloth((110, 70, 40)), 3)                     # a twine belt
    for x in (28, 36):
        ic.add(circle(x, 15, 2.6), matte((40, 30, 30)), 3)                              # button eyes
        ic.add(capsule(x - 2, 13, x + 2, 17, 0.5), matte((200, 190, 170)), 4)
    ic.add(capsule(26, 21, 38, 21, 0.6), matte((70, 40, 30)), 3)                       # a stitched mouth
    for x in range(27, 38, 3):
        ic.add(capsule(x, 19.6, x, 22.4, 0.5), matte((70, 40, 30)), 4)
    ic.add(capsule(30, 32, 50, 12, 1.0), metal((206, 212, 224)), 5)                    # the pin
    ic.add(circle(50, 12, 3.6), gem(pin), 6)
    return ic.render()


def card(symbol, colour, k, back=False):
    ic = Icon()
    ic.add(polygon([(8, 0), (56, 0), (56, 64), (8, 64)]), gold(), 1)
    ic.add(polygon([(12, 4), (52, 4), (52, 60), (12, 60)]), matte(mix(colour, (30, 24, 44), 0.55) if back else (240, 230, 206)), 2)
    if back:
        for i in range(4):
            ic.add(ring(32, 32, 4 + i * 4, 0.8), gold(), 3)
        ic.add(circle(32, 32, 3), glow((200, 170, 255)), 4)
    else:
        ic.add(polygon([(12, 4), (52, 4), (52, 8), (12, 8)]), matte(colour), 3)
        ic.add(polygon([(12, 56), (52, 56), (52, 60), (12, 60)]), matte(colour), 3)
        pixel_symbol(ic, symbol, 16, 20, 4, matte((54, 40, 46)), z=4, accent=glow(colour))
    img = ic.render()
    if k:
        p = img.load()
        for y in range(OUT):
            for x in range(OUT):
                if p[x, y][3] and abs((x + y) - (k * 5 - 3)) < 1:
                    p[x, y] = mix(p[x, y][:3], (255, 255, 255), 0.45) + (255,)
    return img


def deck(k):
    ic = Icon()
    for i, (dx, dy) in enumerate(((-6, 4), (-3, 2), (0, 0))):
        ic.add(polygon([(16 + dx, 6 + dy), (50 + dx, 6 + dy), (50 + dx, 58 + dy), (16 + dx, 58 + dy)]), gold(), 1 + i * 2)
        ic.add(polygon([(19 + dx, 9 + dy), (47 + dx, 9 + dy), (47 + dx, 55 + dy), (19 + dx, 55 + dy)]), matte((60, 40, 96)), 2 + i * 2)
    for i in range(3):
        ic.add(ring(33, 32, 4 + i * 4, 0.8), gold(), 7)
    ic.add(circle(33, 32, 3 + 0.6 * math.sin(k / 6 * math.tau)), glow((210, 170, 255)), 8)
    return ic.render()


def armour(piece):
    ic = Icon()
    G = gold()
    if piece == "aegis_cuirass":
        ic.add(polygon([(14, 10), (26, 6), (38, 6), (50, 10), (54, 30), (46, 56), (18, 56), (10, 30)]), gold((226, 180, 74)), 1)
        ic.add(capsule(22, 14, 42, 14, 1.4), gold((200, 150, 50)), 2)
        ic.add(capsule(32, 18, 32, 50, 1.0), gold((200, 150, 50)), 2)
        ic.add(circle(32, 30, 8), matte((70, 120, 90)), 3)                       # the gorgon's head
        for a in range(0, 360, 40):
            r = math.radians(a)
            ic.add(capsule(32 + math.cos(r) * 7, 30 + math.sin(r) * 7, 32 + math.cos(r) * 12, 30 + math.sin(r) * 12, 1.2), matte((60, 104, 70)), 2)
        ic.add(circle(29, 29, 1.2), glow((255, 220, 120)), 4)
        ic.add(circle(35, 29, 1.2), glow((255, 220, 120)), 4)
    elif piece in ("helm_of_athena", "helm_of_hades"):
        dark = piece == "helm_of_hades"
        M = metal((70, 62, 88)) if dark else gold((226, 180, 74))
        ic.add(polygon([(14, 30), (18, 14), (32, 8), (46, 14), (50, 30), (48, 52), (40, 56), (36, 40), (28, 40), (24, 56), (16, 52)]), M, 2)
        ic.add(polygon([(28, 24), (36, 24), (36, 40), (28, 40)]), matte((20, 16, 26)), 3)          # the eye slit
        ic.add(polygon([(22, 22), (42, 22), (42, 26), (22, 26)]), matte((20, 16, 26)), 3)
        crest = (130, 70, 220) if dark else (210, 50, 50)
        ic.add(inter(ring(34, 26, 22, 4.0), polygon([(0, 0), (64, 0), (64, 14), (0, 14)])), cloth(crest), 1)
        if dark:
            ic.add(circle(32, 30, 1.6), glow((200, 140, 255)), 4)
    elif piece == "girdle_of_hippolyta":
        ic.add(inter(ring(32, 10, 30, 6), polygon([(0, 24), (64, 24), (64, 52), (0, 52)])), cloth((150, 60, 50)), 1)
        ic.add(polygon([(24, 28), (40, 28), (40, 44), (24, 44)]), G, 2)
        ic.add(polygon([(28, 32), (36, 32), (36, 40), (28, 40)]), gem((240, 80, 90)), 3)
        for x in (12, 52):
            ic.add(circle(x, 36, 2.4), G, 2)
    elif piece == "greaves_of_achilles":
        for x in (20, 44):
            ic.add(polygon([(x - 9, 8), (x + 9, 8), (x + 7, 40), (x + 9, 56), (x - 9, 56), (x - 7, 40)]), gold((214, 170, 80)), 1)
            ic.add(ellipse(x, 22, 5, 7), gold((236, 196, 100)), 2)
            ic.add(capsule(x - 7, 44, x + 7, 44, 1.2), metal((160, 130, 70)), 2)
    elif piece == "nemean_pelt":
        ic.add(polygon([(10, 20), (20, 10), (44, 10), (54, 20), (56, 50), (44, 58), (20, 58), (8, 50)]), cloth((196, 150, 80)), 1)
        ic.add(ellipse(32, 18, 13, 10), cloth((176, 120, 60)), 2)                 # the lion's head over it
        for x in (25, 39):
            ic.add(circle(x, 17, 1.8), glow((255, 220, 110)), 3)
        ic.add(polygon([(28, 22), (36, 22), (32, 27)]), matte((80, 50, 40)), 3)
        for x in (14, 50):
            ic.add(capsule(x, 50, x, 58, 3.0), cloth((176, 120, 60)), 2)
    elif piece in ("styx_treads", "talaria"):
        wing = piece == "talaria"
        for x, dx in ((18, -1), (42, 1)):
            body = cloth((150, 110, 70)) if wing else cloth((44, 40, 60))
            ic.add(polygon([(x - 6, 20), (x + 6, 20), (x + 6, 44), (x + 14 * (1 if dx > 0 else 1), 50), (x + 14, 56), (x - 8, 56), (x - 8, 44)]), body, 2)
            ic.add(capsule(x - 7, 52, x + 13, 52, 1.0), G if wing else glow((110, 200, 190)), 3)
            if wing:
                ic.add(polygon([(x - 6, 30), (x - 18, 18), (x - 16, 28), (x - 20, 32), (x - 8, 36)]), cloth((250, 250, 244)), 1)
            else:
                ic.add(capsule(x, 26, x, 40, 0.8), glow((110, 200, 190)), 3)
    return ic.render()


MATERIAL_ART = {}


def material(shape, col):
    ic = Icon()
    C = gem(col) if shape in ("shard", "drop") else matte(col)
    if shape == "leaf":
        ic.add(polygon([(14, 52), (18, 34), (28, 20), (44, 10), (52, 12), (48, 26), (38, 40), (24, 50)]), matte(col), 1)
        ic.add(capsule(14, 54, 46, 14, 0.9), matte(shade(col, 0.7)), 2)
        for k in range(4):
            a = (20 + k * 7, 46 - k * 8)
            ic.add(capsule(a[0], a[1], a[0] + 6, a[1] + 2, 0.6), matte(shade(col, 0.75)), 2)
    elif shape == "shard":
        ic.add(polygon([(30, 4), (44, 20), (40, 50), (28, 60), (20, 40), (22, 18)]), C, 1)
        ic.add(polygon([(30, 8), (36, 22), (32, 48), (26, 40), (26, 18)]), gem(mix(col, (255, 255, 255), 0.35)), 2)
    elif shape == "tuft":
        for (cx, cy, r) in ((22, 36, 11), (36, 30, 14), (46, 38, 9), (30, 42, 10)):
            ic.add(circle(cx, cy, r), cloth(col), 1)
    elif shape == "scale":
        ic.add(polygon([(32, 6), (52, 18), (50, 40), (32, 58), (14, 40), (12, 18)]), matte(col), 1)
        for k in range(3):
            ic.add(inter(ring(32, 20 + k * 11, 14, 1.0), polygon([(0, 20 + k * 11), (64, 20 + k * 11), (64, 64), (0, 64)])), matte(shade(col, 0.75)), 2)
    elif shape == "drop":
        ic.add(union(circle(32, 40, 16), polygon([(32, 4), (46, 34), (18, 34)])), C, 1)
    elif shape == "petal":
        for a in range(0, 360, 72):
            r = math.radians(a - 90)
            ic.add(ellipse(32 + math.cos(r) * 13, 32 + math.sin(r) * 13, 11, 7, r), matte(col), 1)
        ic.add(circle(32, 32, 6), matte((240, 214, 110)), 2)
    elif shape == "cinder":
        ic.add(polygon([(16, 22), (30, 10), (48, 16), (54, 36), (40, 54), (20, 50), (12, 36)]), matte((60, 40, 36)), 1)
        for (cx, cy) in ((28, 26), (40, 34), (26, 42)):
            ic.add(capsule(cx - 5, cy - 2, cx + 5, cy + 2, 1.6), glow(col), 2)
    elif shape == "coin":
        ic.add(circle(32, 32, 22), gold(col), 1)
        ic.add(ring(32, 32, 17, 1.0), gold(shade(col, 0.8)), 2)
        ic.add(ellipse(32, 30, 6, 9), gold(shade(col, 0.85)), 2)           # a face in profile
    return ic.render()


def run(assets):
    """Writes every item's art over the old, using the mod's own data from generate.py."""
    import generate as G
    T = os.path.join(assets, "textures/item")

    def static(name, img):
        path = os.path.join(T, name + ".png")
        save_png(img, path)
        if os.path.exists(path + ".mcmeta"):
            os.remove(path + ".mcmeta")

    firsts = {}
    for _, line, rank, g in G.all_gods():
        gid, shape, power = g[0], g[4], g[5]
        metals = G.LINE_INFO[line]["metals"]
        metal_c = (255, 236, 120) if gid == "zeus" else metals[G.hsh(gid) % len(metals)]
        trim = G.LOOKS[gid][2]
        wood_c = mix((116, 78, 46), G.LOOKS[gid][1], 0.15)
        frames = weapon_frames(shape, metal_c, trim, G.POWER_COLOR[power], wood_c)
        meta = {"animation": {"frametime": 2, "frames": [{"index": 0, "time": 50 + (G.hsh(gid) % 30)}] + list(range(1, len(frames)))}}
        firsts[gid] = save_frames(frames, os.path.join(T, f"{gid}_weapon.png"), meta)
    pulse = lambda n: {"animation": {"frametime": 3, "interpolate": True}}
    save_frames([book(k) for k in range(6)], os.path.join(T, "chaos_codex.png"), pulse(6))
    save_frames([compass(k) for k in range(8)], os.path.join(T, "chaos_compass.png"), pulse(8))
    static("field_bag", field_bag())
    for line in ("zeus", "poseidon", "hades"):
        save_frames([essence(line, k) for k in range(6)], os.path.join(T, f"essence_{line}.png"), pulse(6))
        save_frames([keystone(line, k) for k in range(6)], os.path.join(T, f"keystone_{line}.png"), pulse(6))
        save_frames([compass(k, face=shade(LINE_COL[line], 0.25), needle=LINE_COL[line]) for k in range(8)],
                    os.path.join(T, f"seeker_{line}.png"), pulse(8))
    for did, _, _, pin in G.DOLLS:
        static(f"voodoo_doll_{did}", doll(pin))
    save_frames([deck(k) for k in range(6)], os.path.join(T, "tarot_deck.png"), pulse(6))
    for cid, _, _, _, symbol, colour in G.TAROT:
        frames = [card(symbol, colour, 0)] + [card(symbol, colour, k) for k in range(1, 6)]
        save_frames(frames, os.path.join(T, f"tarot_{cid}.png"), {"animation": {"frametime": 2, "frames": [{"index": 0, "time": 60}, 1, 2, 3, 4, 5]}})
    for piece in ("aegis_cuirass", "helm_of_athena", "helm_of_hades", "girdle_of_hippolyta", "greaves_of_achilles", "nemean_pelt", "styx_treads", "talaria"):
        static(piece, armour(piece))
    for line, mats in G.MATERIALS.items():
        for tier, (_, shape, col) in enumerate(mats, start=1):
            static(f"material_{line.lower()}_{tier}", material(shape, col))
    # the bolts that staffs and gods fire: a side view, bright along its axis and brightest at the point, sparks
    # running down it - drawn at 64 so they match everything else
    for orb in G.ORBS:
        col = G.POWER_COLOR[orb.upper()]
        frames = []
        for f in range(4):
            pulse = 0.5 + 0.5 * math.sin(f / 4 * math.tau)
            img = Image.new("RGBA", (OUT, OUT))
            px = img.load()
            for y in range(OUT):
                for x in range(OUT):
                    off = abs(y - (OUT - 1) / 2) / ((OUT - 1) / 2)
                    along = x / (OUT - 1)
                    core = max(0.0, 1.0 - off * 2.2)
                    v = min(1.0, core * (0.55 + 0.45 * along) + 0.25 * pulse * along)
                    v = round(v * 5) / 5                                  # in bands, not a smear
                    c = mix(shade(col, 0.55 + 0.35 * round(along * 4) / 4), (255, 255, 255), v)
                    if (x + f) % 5 == 0 and off > 0.25:
                        c = mix(c, (255, 255, 255), 0.35)
                    px[x, y] = c + (255,)
            frames.append(img)
        save_frames(frames, os.path.join(T, f"orb_{orb}.png"), {"animation": {"frametime": 2}})
    return firsts
