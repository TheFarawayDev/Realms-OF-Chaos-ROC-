"""The divine resources drawn properly: hue-shifted ramps, shaded spheres and crystals, hand-laid templates, armour
painted plate by plate. Called after divine_art, it replaces the first, plainer pictures."""
import math
import os
import random

from PIL import Image

from pixel_kit import ramp, sphere, crystal, outline, template, tones_pal

MOD = "realms_of_chaos"

INGOT = ["................", "................", "................", "................",
         "....OOOOOOOOO...", "...OWWLLLLLLLO..", "..OLLLLLLLLLLMO.", ".OMMMMMMMMMMMMDO",
         ".OLMMMMMMMMMMDSO", ".OMMMMMAMMMMMDSO", ".ODDDDDDDDDDDSSO", "..OOOOOOOOOOOOO.",
         "................", "................", "................", "................"]
HELMET = ["................", "......OTTO......", ".....OTTTTO.....", "....OOTTTTOO....",
          "..OOLLWLLLMMOO..", ".OLLWLLLLLMMMDO.", ".OLLLMMMMMMMMDO.", ".OLMOOOMMOOOMDSO",
          ".OLMO..MM..OMDSO", ".OLMMO.MM.OMMDSO", ".OLMMO.MM.OMDSO.", ".OMMDO.OO.ODDSO.",
          "..OOO......OOO..", "................", "................", "................"]
CHEST = ["................", "..OOOO....OOOO..", ".OTLLMO..OMMDTO.", ".OLWLMMOOMMMDSO.",
         ".OLLMMMMMMMMDSO.", ".OOOLMLMMLMDOOO.", "...OLMMMMMMDO...", "...OLMLMMLMDO...",
         "...OLMMMMMMDO...", "...OLMLMMLMDO...", "...OTTTTTTTTO...", "...OMMDDDDDSO...",
         "....OOOOOOOO....", "................", "................", "................"]
LEGS = ["................", "................", "...OOOOOOOOOOO..", "...OTTTTTTTTTO..",
        "...OLWMMMMMMDO..", "...OLMMMMMMMDO..", "...OLMMOOOMMDO..", "...OLMDO.OLMDO..",
        "...OTTTO.OTTTO..", "...OLMDO.OLMDO..", "...OLMDO.OLMDO..", "...OLMSO.OLDSO..",
        "...OOOOO.OOOOO..", "................", "................", "................"]
BOOTS = ["................", "................", "................", "................",
         "..OOOO....OOOO..", "..OLMO....OLMO..", "..OTTO....OTTO..", "..OLMO....OLMO..",
         "..OLDO....OLDO..", ".OOLDO...OOLDO..", ".OLWMDO..OLWMDO.", ".OLMMDSO.OLMMDSO",
         ".OOOOOOO.OOOOOOO", "................", "................", "................"]
ICONS = {"helmet": HELMET, "chestplate": CHEST, "leggings": LEGS, "boots": BOOTS}

# armour sets: base metal, trim
SETS = {"adamantine": ((150, 200, 216), (156, 116, 226)), "celestial_bronze": ((212, 150, 62), (252, 236, 160)),
        "orichalcum": ((208, 104, 62), (86, 206, 196)), "stygian_iron": ((64, 64, 80), (86, 214, 206))}


def cluster(nuggets, base, accent, seed):
    """A raw lump: nuggets shaded as spheres, specks of the accent in them, outlined."""
    t = ramp(base)
    cells, depth = {}, {}
    for cx, cy, r in nuggets:
        sphere(cells, cx, cy, r, t[1:], depth)
    rnd = random.Random(seed)
    for (x, y) in sorted(cells):
        if rnd.random() < 0.09 and cells[(x, y)] in (t[2], t[3]):
            cells[(x, y)] = accent
    return outline(cells, t[0])


def ingot(base, accent):
    t = ramp(base)
    return template(INGOT, tones_pal(t, {"A": accent}))


def core(metal, glow, warm_shadow=False):
    """A core: a ring of the god-metal, lit from the top left, round a glowing gem, held by four prongs."""
    mt, gt = ramp(metal), ramp(glow, cool=0.03 if warm_shadow else 0.62)
    cells = {}
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            if 3.6 <= r <= 6.6:
                rr = (r - 3.6) / 3.0 * 2 - 1                # -1 inner edge .. 1 outer edge
                ox, oy = dx / r, dy / r
                nx, ny = ox * rr * 0.85, oy * rr * 0.85
                nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
                lam = max(0.0, nx * -0.55 + ny * -0.62 + nz * 0.56)
                cells[(x, y)] = mt[1:][min(4, int(lam * 5 * 0.999 + 0.3))]
    sphere(cells, 8, 8, 3.4, gt[1:])
    cells[(6, 6)] = (255, 255, 255)
    for ang in (45, 135, 225, 315):                    # the prongs
        a = math.radians(ang)
        x, y = int(8 + math.cos(a) * 3.6), int(8 + math.sin(a) * 3.6)
        cells[(x, y)] = mt[5] if ang in (135, 225) else mt[4]
    return outline(cells, mt[0])


def shard_cluster(base, streak):
    t = ramp(base)
    cells, depth = {}, {}
    crystal(cells, 5.0, 14.5, -42, 7.0, 1.7, t[1:], depth, z=1)
    crystal(cells, 10.5, 14.5, 32, 8.5, 2.0, t[1:], depth, z=2)
    crystal(cells, 7.6, 15.0, -6, 12.5, 2.5, t[1:], depth, z=3)
    for x, y in ((8, 11), (8, 10), (7, 9), (8, 8), (8, 7), (7, 6)):
        if (x, y) in cells and depth.get((x, y)) == 3: cells[(x, y)] = streak
    return outline(cells, t[0])


def pearl():
    t = ramp((206, 204, 232))
    cells = sphere({}, 8, 8.2, 5.5, t[1:])
    for (x, y), c in list(cells.items()):
        a = math.atan2(y - 8.2, x - 8)
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8.2)
        if r > 3.8:                                   # an orient at the rim, pink to green
            tint = (40, -10, 20) if math.sin(a * 2) > 0 else (-10, 25, 10)
            cells[(x, y)] = tuple(max(0, min(255, c[i] + tint[i])) for i in range(3))
    cells[(6, 6)] = (255, 255, 255)
    cells[(5, 6)] = (250, 250, 255)
    return outline(cells, t[0])


def scrap(base, vein):
    """Scrap: a crumpled plate, dark, with bright seams through it."""
    t = ramp(base)
    cells, depth = {}, {}
    for cx, cy, r in ((7, 9, 4.6), (10.5, 7, 3.6), (5, 6, 2.8)):
        sphere(cells, cx, cy, r, t[1:], depth, spec=False)
    for x, y in ((3, 9), (4, 9), (5, 8), (6, 8), (7, 9), (8, 10), (9, 10), (10, 9), (11, 8), (12, 7), (9, 5), (9, 6)):
        if (x, y) in cells: cells[(x, y)] = vein
    return outline(cells, t[0])


def set_icon(piece, base, trim):
    """An armour piece drawn the way the panoply is: smooth solids on a 64 canvas, cut to 16 pixels by the item painter,
    on the panoply's own shapes - the Corinthian helm, the cuirass, belted greaves, the treads - in a set's metal and trim."""
    import item_art as A
    ic = A.Icon()
    M, Tm = A.metal(base), A.metal(trim)
    dark = A.matte(tuple(max(0, int(c * 0.18)) for c in base))
    if piece == "helmet":
        ic.add(A.polygon([(14, 30), (18, 14), (32, 8), (46, 14), (50, 30), (48, 52), (40, 56), (36, 40), (28, 40), (24, 56), (16, 52)]), M, 2)
        ic.add(A.polygon([(28, 24), (36, 24), (36, 40), (28, 40)]), dark, 3)                  # the T cut for the eyes and mouth
        ic.add(A.polygon([(22, 22), (42, 22), (42, 26), (22, 26)]), dark, 3)
        ic.add(A.inter(A.ring(34, 26, 22, 4.0), A.polygon([(0, 0), (64, 0), (64, 14), (0, 14)])), A.cloth(trim), 1)    # the crest
        ic.add(A.capsule(17, 30, 47, 30, 1.2), Tm, 3)                                           # a band at the brow
    elif piece == "chestplate":
        ic.add(A.polygon([(14, 10), (26, 6), (38, 6), (50, 10), (54, 30), (46, 56), (18, 56), (10, 30)]), M, 1)
        ic.add(A.capsule(22, 13, 42, 13, 1.6), Tm, 2)                                           # the collar
        ic.add(A.ellipse(13, 15, 7, 5, -0.5), Tm, 2)                                             # shoulder guards
        ic.add(A.ellipse(51, 15, 7, 5, 0.5), Tm, 2)
        ic.add(A.capsule(20, 26, 30, 28, 1.0), A.shade_mat(M, 0.7), 2)                         # the curve of the chest
        ic.add(A.capsule(34, 28, 44, 26, 1.0), A.shade_mat(M, 0.7), 2)
        ic.add(A.capsule(32, 30, 32, 44, 0.9), A.shade_mat(M, 0.75), 2)
        ic.add(A.capsule(16, 48, 48, 48, 2.0), Tm, 2)                                           # the belt
        ic.add(A.circle(32, 48, 2.6), A.gem(trim), 3)
    elif piece == "leggings":
        ic.add(A.polygon([(10, 8), (54, 8), (54, 18), (10, 18)]), Tm, 2)                         # the belt
        for x in (20, 44):
            ic.add(A.polygon([(x - 10, 14), (x + 10, 14), (x + 8, 40), (x + 9, 56), (x - 9, 56), (x - 8, 40)]), M, 1)
            ic.add(A.ellipse(x, 36, 5, 6), Tm, 2)                                                 # the knee
        ic.add(A.circle(32, 13, 2.6), A.gem(trim), 3)
    else:
        for x, dx in ((18, -1), (42, 1)):
            ic.add(A.polygon([(x - 6, 18), (x + 6, 18), (x + 6, 44), (x + 14, 50), (x + 14, 56), (x - 8, 56), (x - 8, 44)]), M, 2)
            ic.add(A.capsule(x - 6, 24, x + 6, 24, 1.4), Tm, 3)                                   # the cuff
            ic.add(A.capsule(x - 7, 53, x + 13, 53, 1.0), Tm, 3)                                  # the sole
    return ic.render()


def ichor_vial():
    """Divine Ichor: a stoppered glass vial of glowing gold, a bead of light in it."""
    glass = ramp((200, 220, 236))
    gold = ramp((255, 206, 90), cool=0.04)
    cells = {}
    for y in range(4, 15):
        for x in range(4, 12):
            dx = x + 0.5 - 8
            if y >= 7:
                if abs(dx) > 3.6 - (0.5 if y == 14 else 0): continue
                if y >= 9 and abs(dx) < 3.0 and y < 14:
                    cells[(x, y)] = gold[5] if (x == 6 and y in (10, 11)) else gold[4] if dx < 0 else gold[3]
                else:
                    cells[(x, y)] = glass[4] if dx < 0 else glass[2]
            elif y >= 5:
                if abs(dx) > 1.6: continue
                cells[(x, y)] = glass[4] if dx < 0 else glass[3]
    for x in range(6, 10):
        cells[(x, 3)] = (150, 96, 54)
        cells[(x, 4)] = (120, 74, 40)
    cells[(5, 9)] = (255, 255, 255)
    return outline(cells, (40, 34, 52))


def icon(piece, base, trim):
    t, tr = ramp(base), ramp(trim)
    return template(ICONS[piece], tones_pal(t, {"T": tr[4]}))


# ---- armour as it is worn
def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def plate(px, x0, y0, w, h, t, rows=None):
    """One face of a box as a plate, shaded as the icons are: no noise, lit along its top and left with a soft light
    in the upper corner, shadowed along its right, its bottom edge darkest."""
    rows = list(rows) if rows is not None else list(range(h))
    top, bottom = min(rows), max(rows)
    span = max(1, (w - 1) + (bottom - top))
    for y in rows:
        for x in range(w):
            k = (x + (y - top)) / span                      # 0 at the top left .. 1 at the bottom right
            if y == bottom and bottom - top > 0: c = t[1]
            elif y == top or x == 0: c = t[4]
            elif x == w - 1: c = t[2]
            elif k < 0.22: c = t[4]
            elif k > 0.78: c = t[2]
            else: c = t[3]
            px[x0 + x, y0 + y] = c + (255,)


def band(px, x0, y0, w, y, tr, lit=True):
    """A row of the trim across a face: its light tone, or its mid tone below a lit one."""
    for x in range(w):
        px[x0 + x, y0 + y] = (tr[4] if lit else tr[2]) + (255,)


def worn(set_id, base, trim):
    """The set as it is worn, in the icons' colours and manner: a crested Corinthian helm, a breastplate with a collar,
    a belt and shoulder guards, leggings belted at the hip with a lit knee, and greaves cuffed below the knee."""
    t, tr = ramp(base), ramp(trim)
    main, legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    mp, lp = main.load(), legs.load()
    head, body, arm, leg = faces(0, 0, 8, 8, 8), faces(16, 16, 8, 12, 4), faces(40, 16, 4, 12, 4), faces(0, 16, 4, 12, 4)
    clear = (0, 0, 0, 0)

    # the helm: shaded plates all round, a band of trim at the brow, a crest from brow to nape, a T cut for eyes and mouth
    for name, (x0, y0, w, h) in head.items():
        plate(mp, x0, y0, w, h, t)
        if name in ("front", "right", "left", "back"): band(mp, x0, y0, w, 1, tr)
    fx, fy = head["front"][0], head["front"][1]
    for x, y in ((1, 3), (2, 3), (5, 3), (6, 3), (1, 4), (2, 4), (5, 4), (6, 4), (3, 6), (4, 6), (3, 7), (4, 7)):
        mp[fx + x, fy + y] = clear
    for x, y in ((0, 3), (0, 4), (7, 3), (7, 4), (2, 5), (5, 5)):
        mp[fx + x, fy + y] = t[1] + (255,)                 # the rims of the cuts, in shadow
    for y in (3, 4, 5):
        mp[fx + 3, fy + y], mp[fx + 4, fy + y] = t[4] + (255,), t[3] + (255,)   # the nose guard
    tx, ty = head["top"][0], head["top"][1]
    for y in range(8):
        mp[tx + 3, ty + y], mp[tx + 4, ty + y] = tr[4] + (255,), tr[2] + (255,)
    for face, rows in (("back", range(0, 4)), ("front", range(0, 1))):
        x0, y0 = head[face][0], head[face][1]
        for y in rows: mp[x0 + 3, y0 + y], mp[x0 + 4, y0 + y] = tr[4] + (255,), tr[2] + (255,)

    # the breastplate: a collar, the curve under the chest, a belt, and a fringe of strips at its hem
    for name, (x0, y0, w, h) in body.items():
        plate(mp, x0, y0, w, h, t)
        if name in ("front", "back", "right", "left"):
            band(mp, x0, y0, w, 0, tr)
            band(mp, x0, y0, w, 9, tr)
            band(mp, x0, y0, w, 10, tr, lit=False)
            for x in range(w): mp[x0 + x, y0 + 11] = (tr[3] if x % 2 == 0 else t[1]) + (255,)
    cx, cy = body["front"][0], body["front"][1]
    for x in (1, 2, 5, 6): mp[cx + x, cy + 4] = t[2] + (255,)
    for x in (1, 2, 5, 6): mp[cx + x, cy + 3] = t[4] + (255,)
    for y in range(5, 9): mp[cx + 3, cy + y], mp[cx + 4, cy + y] = t[2] + (255,), t[4] + (255,)
    # shoulder guards: the top of the arm and its four sides down to a trimmed edge
    for name, (x0, y0, w, h) in arm.items():
        if name == "bottom": continue
        if name == "top":
            plate(mp, x0, y0, w, h, t)
            continue
        plate(mp, x0, y0, w, h, t, rows=range(0, 5))
        band(mp, x0, y0, w, 4, tr)

    # greaves: the leg below the knee, a cuff of trim at the top, shaded down to the foot
    for name, (x0, y0, w, h) in leg.items():
        if name == "top": continue
        if name == "bottom":
            plate(mp, x0, y0, w, h, t)
            continue
        plate(mp, x0, y0, w, h, t, rows=range(7, 12))
        band(mp, x0, y0, w, 7, tr)

    # leggings: belted at the hip with a fringe below, guarding the thigh to a lit knee
    for name, (x0, y0, w, h) in leg.items():
        if name in ("top", "bottom"): continue
        plate(lp, x0, y0, w, h, t, rows=range(0, 8))
        band(lp, x0, y0, w, 0, tr)
        if name == "front":
            for y in (4, 5): lp[x0 + 1, y0 + y], lp[x0 + 2, y0 + y] = tr[4] + (255,), tr[3] + (255,)
    for name, (x0, y0, w, h) in body.items():
        if name in ("top", "bottom"): continue
        plate(lp, x0, y0, w, h, t, rows=range(7, 12))
        band(lp, x0, y0, w, 7, tr)
        for x in range(w):
            for y in (9, 10): lp[x0 + x, y0 + y] = (t[3] if x % 2 == 0 else t[2]) + (255,)
    return main, legs

GOLD = ramp((226, 176, 60))
PORPH = ramp((118, 40, 64))
IVORY = ramp((228, 216, 186))


def tex(fn):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c is not None: img.putpixel((x, y), tuple(c[:3]) + ((c[3],) if len(c) == 4 else (255,)))
    return img


def noise16(seed):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(16)] for _ in range(16)]


def fulgurite_tex():
    s, gl = ramp((212, 190, 138)), ramp((92, 116, 104))
    n = noise16(31)
    rnd = random.Random(77)
    tube = {}
    for start in (2, 7, 12):
        x = start
        for y in range(16):
            tube[(x % 16, y)] = 0
            tube[((x + 1) % 16, y)] = 1
            if rnd.random() < 0.35: x += rnd.choice((-1, 1))
            if rnd.random() < 0.15:                      # a branch
                tube[((x + 2) % 16, y)] = 1

    def px(x, y):
        k = tube.get((x, y))
        if k == 0: return gl[4] if (y + x) % 5 == 0 else gl[2]
        if k == 1: return gl[1]
        if any((x + d) % 16 in (p[0] for p in tube if p[1] == y) for d in (-1, 1)): return s[4]   # fused, pale sand at the glass
        v = n[y][x]
        return s[2] if v < 0.18 else s[4] if v > 0.86 else s[3]
    return tex(px)


def stygian_ore_tex():
    soil = ramp((88, 66, 52))
    n = noise16(53)
    cells, depth = {}, {}
    iron = ramp((72, 72, 90))
    for cx, cy, r in ((4.5, 4.5, 1.9), (11.5, 5.0, 1.6), (6.0, 11.5, 1.8), (12.0, 12.0, 1.5), (9, 8.5, 1.1)):
        sphere(cells, cx, cy, r, iron[1:], depth)
    glints = {(4, 4), (11, 4), (5, 11), (12, 11)}

    def px(x, y):
        if (x, y) in glints: return (110, 236, 226)
        if (x, y) in cells: return cells[(x, y)]
        if any((x + dx, y + dy) in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): return iron[0]
        v = n[y][x]
        return soil[2] if v < 0.25 else soil[4] if v > 0.9 else soil[3]
    return tex(px)


def clam_textures():
    sh = ramp((92, 138, 160))

    def shell(x, y):
        if y == 0: return sh[5]
        if y == 15: return sh[1]
        ridge = x % 4
        c = sh[4] if ridge == 0 else sh[3] if ridge in (1, 2) else sh[2]
        if y % 5 == 4: c = sh[2] if ridge else sh[3]       # growth lines
        return c

    def nacre(x, y):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        base = (238, 206, 220) if math.sin(r * 1.1 + x * 0.3) > 0 else (214, 222, 236)
        return tuple(int(c * (1.0 - r * 0.02)) for c in base)
    pt = ramp((214, 214, 238))

    def pearl(x, y):
        dx, dy = (x + 0.5 - 8) / 7.5, (y + 0.5 - 8) / 7.5
        d2 = dx * dx + dy * dy
        if d2 > 1: return pt[2]
        nz = math.sqrt(1 - d2)
        lam = max(0, -0.55 * dx - 0.62 * dy + 0.56 * nz)
        return (255, 255, 255) if lam > 0.95 else pt[1:][min(4, int(lam * 5 + 0.25))]
    return tex(shell), tex(nacre), tex(pearl)


def plinth_textures(lit):
    glow = (255, 236, 160) if lit else GOLD[4]
    inlay = (255, 246, 210) if lit else GOLD[3]

    def side(x, y):
        if y == 0: return GOLD[5]
        if y == 1: return GOLD[3]
        if y in (2, 3):                                   # a meander of gold on dark porphyry
            k = (x + (0 if y == 2 else 2)) % 4
            return glow if k in (0, 1) else PORPH[1]
        if y == 4: return GOLD[2]
        if y in (12, 13): return GOLD[4] if y == 12 else GOLD[2]
        if y >= 14: return PORPH[2] if y == 14 else PORPH[1]
        # the panel: a sunk frame with an inlaid line inside it
        if x in (0, 15): return PORPH[2]
        if x == 1 or y == 5: return PORPH[4]
        if x == 14 or y == 11: return PORPH[1]
        if (x in (3, 12) and 7 <= y <= 9) or (y in (7, 9) and 3 <= x <= 12): return inlay if (x in (3, 12) or y in (7, 9)) and not (3 < x < 12 and y == 8) else PORPH[3]
        return PORPH[3]

    def top(x, y):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        if x in (0, 15) or y in (0, 15): return GOLD[2]
        if x in (1, 14) or y in (1, 14): return GOLD[4]
        if 5.0 <= r < 6.0: return glow
        if 6.0 <= r < 6.8: return GOLD[2]
        if r < 2.2: return (255, 250, 220) if lit else PORPH[1]
        if r < 3.0: return GOLD[3]
        return PORPH[4] if (x + y) % 7 == 0 else PORPH[3]
    return tex(side), tex(top)


def column_textures(lit):
    inlay = (255, 240, 170) if lit else GOLD[3]

    def shaft(x, y):
        f = x % 4                                          # flutes: a lit ridge, the face, a shadowed groove
        if x in (7, 8): return inlay if f in (3, 0) else PORPH[4]
        return PORPH[5] if f == 0 else PORPH[4] if f == 1 else PORPH[3] if f == 2 else PORPH[1]

    def trim(x, y):
        if y == 0: return GOLD[5]
        if y in (1, 2): return GOLD[4] if y == 1 else GOLD[3]
        if y == 3: return GOLD[1]
        if y in (4, 5): return inlay if (x // 2) % 2 == 0 else GOLD[2]       # a band of beads
        if y == 6: return GOLD[1]
        return PORPH[4] if y < 10 else PORPH[3] if y < 15 else PORPH[1]
    return tex(shaft), tex(trim)


def cornucopia_textures():
    def horn(x, y):
        band = x % 6                                       # ivory bound with gold, banded along its length
        if band in (0, 1):
            return GOLD[5] if band == 0 else GOLD[3]
        c = IVORY[4] if y < 5 else IVORY[3] if y < 11 else IVORY[2]
        return IVORY[5] if band == 2 and y < 5 else c

    def rim(x, y):
        return GOLD[5] if y in (0, 1) else GOLD[4] if y < 6 else GOLD[3] if y < 11 else GOLD[2]

    def stand(x, y):
        return GOLD[4] if y < 2 else GOLD[3] if y < 12 else GOLD[1]

    def apple(x, y):
        r = math.hypot(x + 0.5 - 7, y + 0.5 - 7)
        return (255, 170, 150) if r < 2 else (220, 50, 46) if r < 6 else (150, 26, 30)

    def grapes(x, y):
        on = ((x // 3) + (y // 3)) % 2 == 0
        k = (x % 3, y % 3)
        return (196, 150, 240) if k == (0, 0) else (126, 62, 176) if on else (90, 40, 140)

    def wheat(x, y):
        return GOLD[5] if (x + y) % 3 == 0 else GOLD[4] if y % 2 else GOLD[3]
    mouths = {}
    for core, glow in (("none", None), ("storm", (255, 232, 120)), ("tide", (90, 222, 232)), ("shade", (168, 110, 250))):
        g = ramp(glow) if glow else None

        def mouth(x, y, g=g):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if r >= 7.6: return (0, 0, 0, 0)
            if r >= 7.0: return GOLD[2]
            if r >= 6.0: return GOLD[5] if (x + y) < 16 else GOLD[2]
            if g is None: return (40, 22, 14) if r > 3 else (24, 12, 8)
            return (255, 255, 255) if r < 1.4 else g[5] if r < 2.6 else g[4] if r < 3.8 else g[2] if r < 5 else g[1]
        mouths[core] = tex(mouth)
    return tex(horn), tex(rim), tex(stand), tex(apple), tex(grapes), tex(wheat), mouths


def box(a, b, faces, rot=None):
    el = {"from": list(a), "to": list(b), "faces": faces}
    if rot: el["rotation"] = rot
    return el


def f6(tex_side, uv_side, tex_top=None, uv_top=None, tex_bottom=None, uv_bottom=None, overrides=None):
    out = {d: {"texture": f"#{tex_side}", "uv": list(uv_side)} for d in ("north", "south", "east", "west")}
    out["up"] = {"texture": f"#{tex_top or tex_side}", "uv": list(uv_top or uv_side)}
    out["down"] = {"texture": f"#{tex_bottom or tex_top or tex_side}", "uv": list(uv_bottom or uv_top or uv_side)}
    if overrides: out.update(overrides)
    return out


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/block")
    TI = os.path.join(assets, "textures/item")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    # ---- items
    items = {
        "storm_glass": shard_cluster((118, 198, 232), (255, 238, 120)),
        "abyssal_pearl": pearl(),
        "raw_stygian_iron": cluster(((6, 9.5, 3.6), (10.5, 7, 3.2), (9.5, 11.5, 2.6), (5, 5.5, 2.4)), (70, 70, 88), (100, 230, 220), 3),
        "raw_adamantine": cluster(((6, 9.5, 3.6), (10.5, 7, 3.2), (9.5, 11.5, 2.6), (5, 5.5, 2.4)), (150, 196, 214), (170, 120, 240), 5),
        "adamantine_scrap": scrap((62, 70, 88), (150, 226, 240)),
        "celestial_bronze_ingot": ingot((212, 150, 62), (130, 206, 255)),
        "orichalcum_ingot": ingot((208, 104, 62), (86, 214, 204)),
        "stygian_iron_ingot": ingot((68, 68, 84), (96, 226, 216)),
        "adamantine_ingot": ingot((150, 200, 216), (168, 120, 236)),
        "thunder_core": core((212, 150, 62), (255, 226, 110), warm_shadow=True),
        "divine_ichor": ichor_vial(),
        "tide_core": core((208, 104, 62), (80, 216, 230)),
        "shade_core": core((68, 68, 84), (164, 110, 250)),
    }
    for name, img in items.items():
        img.save(os.path.join(TI, f"{name}.png"))
    wj(os.path.join(assets, "models/item/divine_ichor.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/divine_ichor"}})
    wj(os.path.join(assets, "items/divine_ichor.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/divine_ichor"}})
    for set_id, (base, trim) in SETS.items():
        for piece in ICONS:
            set_icon(piece, base, trim).save(os.path.join(TI, f"{set_id}_{piece}.png"))
        main, legs = worn(set_id, base, trim)
        main.save(os.path.join(assets, "textures/entity/equipment/humanoid", f"{set_id}.png"))
        legs.save(os.path.join(assets, "textures/entity/equipment/humanoid_leggings", f"{set_id}.png"))

    # ---- blocks
    fulgurite_tex().save(os.path.join(T, "fulgurite.png"))
    stygian_ore_tex().save(os.path.join(T, "stygian_iron_ore.png"))
    shell, nacre, pearl_t = clam_textures()
    shell.save(os.path.join(T, "giant_clam_shell.png"))
    nacre.save(os.path.join(T, "giant_clam_inside.png"))
    pearl_t.save(os.path.join(T, "giant_clam_pearl.png"))

    # the plinth
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        side, top = plinth_textures(lit)
        side.save(os.path.join(T, f"plinth_of_plenty_side{sfx}.png"))
        top.save(os.path.join(T, f"plinth_of_plenty_top{sfx}.png"))
        wj(os.path.join(M, f"plinth_of_plenty{sfx}.json"), {"parent": "minecraft:block/cube_bottom_top",
            "textures": {"side": t(f"plinth_of_plenty_side{sfx}"), "top": t(f"plinth_of_plenty_top{sfx}"), "bottom": t("polished_porphyry")}})
    wj(os.path.join(assets, "blockstates/plinth_of_plenty.json"), {"variants": {"lit=false": {"model": f"{MOD}:block/plinth_of_plenty"},
                                                                             "lit=true": {"model": f"{MOD}:block/plinth_of_plenty_lit"}}})
    wj(os.path.join(assets, "items/plinth_of_plenty.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/plinth_of_plenty_lit"}})

    # the columns: a fluted shaft, a base where it starts and a capital where it ends
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        shaft, trim = column_textures(lit)
        shaft.save(os.path.join(T, f"column_of_plenty_shaft{sfx}.png"))
        trim.save(os.path.join(T, f"column_of_plenty_trim{sfx}.png"))
        tx = {"particle": t(f"column_of_plenty_shaft{sfx}"), "shaft": t(f"column_of_plenty_shaft{sfx}"), "trim": t(f"column_of_plenty_trim{sfx}"),
              "end": t("polished_porphyry")}
        wj(os.path.join(M, f"column_of_plenty_shaft{sfx}.json"), {"parent": "minecraft:block/block", "textures": tx,
            "elements": [box((3, 0, 3), (13, 16, 13), f6("shaft", (3, 0, 13, 16), "end", (3, 3, 13, 13)))]})
        wj(os.path.join(M, f"column_of_plenty_base{sfx}.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": [
            box((1, 0, 1), (15, 3, 15), f6("trim", (1, 7, 15, 10), "end", (1, 1, 15, 15))),
            box((2, 3, 2), (14, 5, 14), f6("trim", (2, 1, 14, 3), "trim", (2, 4, 14, 6)))]})
        wj(os.path.join(M, f"column_of_plenty_capital{sfx}.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": [
            box((2, 11, 2), (14, 13, 14), f6("trim", (2, 4, 14, 6), "trim", (2, 4, 14, 6))),
            box((1, 13, 1), (15, 16, 15), f6("trim", (1, 0, 15, 3), "end", (1, 1, 15, 15)))]})
    parts = []
    for lit in ("false", "true"):
        sfx = "_lit" if lit == "true" else ""
        parts.append({"when": {"lit": lit}, "apply": {"model": f"{MOD}:block/column_of_plenty_shaft{sfx}"}})
        parts.append({"when": {"lit": lit, "base": "true"}, "apply": {"model": f"{MOD}:block/column_of_plenty_base{sfx}"}})
        parts.append({"when": {"lit": lit, "capital": "true"}, "apply": {"model": f"{MOD}:block/column_of_plenty_capital{sfx}"}})
    wj(os.path.join(assets, "blockstates/column_of_plenty.json"), {"multipart": parts})
    whole = []
    for part in ("shaft", "base", "capital"):
        import json
        with open(os.path.join(M, f"column_of_plenty_{part}_lit.json")) as fh: whole += json.load(fh)["elements"]
    wj(os.path.join(M, "column_of_plenty.json"), {"parent": "minecraft:block/block", "textures": {
        "particle": t("column_of_plenty_shaft_lit"), "shaft": t("column_of_plenty_shaft_lit"), "trim": t("column_of_plenty_trim_lit"), "end": t("polished_porphyry")},
        "elements": whole})
    wj(os.path.join(assets, "items/column_of_plenty.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/column_of_plenty"}})

    # the Cornucopia: a horn of ivory bound in gold on a gold stand, its mouth to the front, fruit spilling from it
    import glob
    for old in glob.glob(os.path.join(T, "cornucopia_*.png")) + glob.glob(os.path.join(M, "cornucopia*.json")):
        os.remove(old)
    horn, rim, stand, apple, grapes, wheat, mouths = cornucopia_textures()
    for name, img in (("horn", horn), ("rim", rim), ("stand", stand), ("apple", apple), ("grapes", grapes), ("wheat", wheat)):
        img.save(os.path.join(T, f"cornucopia_{name}.png"))
    for core_id, img in mouths.items():
        img.save(os.path.join(T, f"cornucopia_mouth_{core_id}.png"))

    def cornucopia(core_id):
        tx = {"particle": t("cornucopia_horn"), "horn": t("cornucopia_horn"), "rim": t("cornucopia_rim"), "stand": t("cornucopia_stand"),
              "apple": t("cornucopia_apple"), "grapes": t("cornucopia_grapes"), "wheat": t("cornucopia_wheat"), "mouth": t(f"cornucopia_mouth_{core_id}")}
        els = [box((4, 0, 5), (12, 1, 12), f6("stand", (4, 0, 12, 1), "stand", (4, 5, 12, 12))),
               box((7, 1, 7), (9, 4, 10), f6("stand", (7, 2, 9, 5), "stand", (7, 7, 9, 10)))]
        # the horn, turned a little so its length shows: a round mouth of gold to the front, then ivory bound in gold,
        # narrowing as it runs back and curling up into its tip
        turn = {"origin": [8, 8, 8], "axis": "y", "angle": 22.5}
        els.append(box((4, 3, 1), (12, 11, 3), f6("rim", (4, 3, 12, 5), "rim", (4, 1, 12, 3), overrides={
            "north": {"texture": "#mouth", "uv": [0, 0, 16, 16]}, "south": {"texture": "#mouth", "uv": [0, 0, 16, 16]}}), turn))
        segs = [((5, 4, 3), (11, 10, 6)), ((6, 4, 6), (10, 9, 9)), ((6.5, 4, 9), (9.5, 8, 12)), ((7, 5, 12), (9, 8, 14)),
                ((7, 7, 13.5), (9, 10, 15.5)), ((7, 10, 12.5), (9, 12, 14.5)), ((7.5, 12, 11.5), (8.5, 13, 13))]
        for n, (a, b) in enumerate(segs):
            w, h, d = b[0] - a[0], b[1] - a[1], b[2] - a[2]
            els.append(box(a, b, f6("horn", (n * 2, 0, n * 2 + d, h), "horn", (n * 2, 0, n * 2 + w, d), overrides={
                "north": {"texture": "#horn", "uv": [n * 2, 0, n * 2 + w, h]}, "south": {"texture": "#horn", "uv": [n * 2, 0, n * 2 + w, h]}}), turn))
        # fruit spilling out of the mouth onto the stand and the plinth below it
        els += [box((2, 0, 0), (6, 4, 3), f6("apple", (2, 4, 6, 8), "apple", (3, 3, 7, 7))),
                box((10, 0, 0), (14, 3, 3), f6("grapes", (0, 0, 4, 3), "grapes", (0, 0, 4, 3))),
                box((6, 0, 0), (10, 2, 2), f6("wheat", (0, 0, 4, 2), "wheat", (0, 0, 4, 2))),
                box((5, 2, 0), (8, 4, 1), f6("grapes", (4, 4, 7, 6), "grapes", (4, 4, 7, 5)))]
        return {"parent": "minecraft:block/block", "textures": tx, "elements": els}
    cv = {}
    for core_id in mouths:
        wj(os.path.join(M, f"cornucopia_{core_id}.json"), cornucopia(core_id))
        for f, r in rot.items():
            cv[f"core={core_id},facing={f}"] = {"model": f"{MOD}:block/cornucopia_{core_id}", "y": r}
    wj(os.path.join(M, "cornucopia.json"), {"parent": f"{MOD}:block/cornucopia_storm"})
    wj(os.path.join(assets, "blockstates/cornucopia.json"), {"variants": cv})
    wj(os.path.join(assets, "items/cornucopia.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/cornucopia_storm"}})

    # ---- recipes for the shrine: the horn, the plinth and the columns
    R = os.path.join(data, "recipe")
    wj(os.path.join(data, "tags/item/god_metals.json"), {"values": [f"{MOD}:celestial_bronze_ingot", f"{MOD}:orichalcum_ingot", f"{MOD}:stygian_iron_ingot"]})
    wj(os.path.join(R, "cornucopia.json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BNO", "AHA", "GSG"],
        "key": {"B": f"{MOD}:celestial_bronze_ingot", "N": "minecraft:nether_star", "O": f"{MOD}:orichalcum_ingot", "A": f"{MOD}:adamantine_block",
                "H": "minecraft:heart_of_the_sea", "G": "minecraft:gold_block", "S": f"{MOD}:stygian_iron_ingot"}, "result": {"id": f"{MOD}:cornucopia"}})
    wj(os.path.join(data, "tags/item/essences.json"), {"values": [f"{MOD}:essence_zeus", f"{MOD}:essence_poseidon", f"{MOD}:essence_hades"]})
    wj(os.path.join(R, "divine_ichor.json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GMG", "MEM", "GMG"],
        "key": {"G": "minecraft:gold_ingot", "M": "minecraft:glistering_melon_slice", "E": f"#{MOD}:essences"}, "result": {"id": f"{MOD}:divine_ichor", "count": 4}})
    # what the orb will not make: what cannot be had in survival, the gods' own gifts, and its own makings
    never = ["minecraft:bedrock", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
             "minecraft:command_block_minecart", "minecraft:barrier", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw",
             "minecraft:light", "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault",
             "minecraft:end_portal_frame", "minecraft:reinforced_deepslate", "minecraft:petrified_oak_slab", "minecraft:test_block",
             "minecraft:test_instance_block", f"#{MOD}:relics", f"#{MOD}:essences", f"{MOD}:divine_ichor", f"{MOD}:thunder_core", f"{MOD}:tide_core",
             f"{MOD}:shade_core", f"{MOD}:cornucopia", f"{MOD}:plinth_of_plenty", f"{MOD}:column_of_plenty", f"{MOD}:chaos_codex", f"{MOD}:tarot_deck",
             f"{MOD}:helm_of_hades", f"{MOD}:helm_of_athena", f"{MOD}:aegis_cuirass", f"{MOD}:nemean_pelt", f"{MOD}:girdle_of_hippolyta",
             f"{MOD}:greaves_of_achilles", f"{MOD}:talaria", f"{MOD}:styx_treads"]
    wj(os.path.join(data, "tags/item/unreplicable.json"), {"values": [{"id": v, "required": False} if v.startswith("minecraft:test") else v for v in never]})
    wj(os.path.join(R, "plinth_of_plenty.json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GCG", "PAP", "PPP"],
        "key": {"G": "minecraft:gold_ingot", "C": f"{MOD}:chiseled_porphyry", "P": f"{MOD}:polished_porphyry", "A": f"{MOD}:adamantine_ingot"},
        "result": {"id": f"{MOD}:plinth_of_plenty"}})
    wj(os.path.join(R, "column_of_plenty.json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["GMG", "GPG", "GMG"],
        "key": {"G": "minecraft:gold_ingot", "M": f"#{MOD}:god_metals", "P": f"{MOD}:porphyry_pillar"}, "result": {"id": f"{MOD}:column_of_plenty"}})


def lang():
    return {
        "block.realms_of_chaos.plinth_of_plenty": "Plinth of Plenty",
        "block.realms_of_chaos.column_of_plenty": "Column of Plenty",
        "block.realms_of_chaos.cornucopia.incomplete": "The shrine is not whole",
        "block.realms_of_chaos.cornucopia.needs_plinth": "Set the orb on a Plinth of Plenty",
        "block.realms_of_chaos.cornucopia.needs_columns": "Columns of Plenty: %s of %s, two high at the corners",
        "block.realms_of_chaos.cornucopia.no_core": "The shrine stands whole. Set a core in the orb; show it anything, and pour in ichor, to have it made",
        "block.realms_of_chaos.cornucopia.working": "%s in %s",
        "block.realms_of_chaos.cornucopia.making": "Making %s: %s ichor each, %s held",
        "block.realms_of_chaos.cornucopia.next": "The next in %s",
        "block.realms_of_chaos.cornucopia.needs_ichor": "Waiting for ichor: it needs %s",
        "block.realms_of_chaos.cornucopia.refused": "The orb will not make %s",
        "item.realms_of_chaos.divine_ichor": "Divine Ichor",
        "item.realms_of_chaos.divine_ichor.lore": "The golden blood of the gods. Poured into the Orb of Plenty, it pays for what the orb makes",
        "block.realms_of_chaos.cornucopia.at_home.storm": "The storm feeds it: twice as fast",
        "block.realms_of_chaos.cornucopia.at_home.tide": "The sea feeds it: twice as fast",
        "block.realms_of_chaos.cornucopia.at_home.shade": "The underworld feeds it: twice as fast",
    }
