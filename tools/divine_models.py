"""The physical things built properly: layered models with depth, parts that glow, and textures to suit.

  The Cornucopia  - a horn that truly curves: tapering segments set along a curve, gold bands standing proud of it,
                    a flared rim, a gem glowing in its mouth, and fruit spilling out - grapes, an apple, wheat.
  The Plinth      - a stepped base, a body of inset panels, a cornice with a meander and a slab on top; its gold
                    inlay glows when the shrine is whole.
  The Columns     - a rounded fluted shaft with inlaid strips that glow, a base of mouldings, an Ionic capital.
  The Giant Clam  - a scalloped shell of ridges with a wavy lip, a bright mantle between its halves, a pearl.
  The ores        - a base and an overlay of crystals that glint in the dark.

Model UVs are left to the game where a face shows a plain material (it takes them from the element's place), and set
where a face shows a picture.
"""
import math
import os
import random

from PIL import Image

from pixel_kit import ramp

MOD = "realms_of_chaos"
GOLD = ramp((226, 176, 60))
PORPH = ramp((118, 40, 64))
IVORY = ramp((230, 218, 188))


def tex(fn):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = fn(x, y)
            if c is not None:
                img.putpixel((x, y), tuple(c[:3]) + ((c[3],) if len(c) == 4 else (255,)))
    return img


def noise(seed):
    rnd = random.Random(seed)
    return [[rnd.random() for _ in range(16)] for _ in range(16)]


def el(frm, to, tex_ref, faces=None, uv=None, rot=None, emit=None, shade=True):
    """An element. tex_ref: one texture for every face, or {face: texture}. uv: one uv for every face, or {face: uv}.
    Faces not named in a dict are left off."""
    names = ("north", "south", "east", "west", "up", "down")
    fs = {}
    for f in names:
        t = tex_ref.get(f) if isinstance(tex_ref, dict) else tex_ref
        if t is None:
            continue
        face = {"texture": f"#{t}"}
        u = uv.get(f) if isinstance(uv, dict) else uv
        if u is not None:
            face["uv"] = list(u)
        fs[f] = face
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fs}
    if rot is not None:
        e["rotation"] = rot
    if emit:
        e["light_emission"] = emit
    if not shade:
        e["shade"] = False
    return e


# ---------------------------------------------------------------------------------------------------- textures
def gold_tex():
    n = noise(11)
    return tex(lambda x, y: GOLD[5] if n[y][x] > 0.93 else GOLD[4] if n[y][x] > 0.6 else GOLD[3] if n[y][x] > 0.12 else GOLD[2])


def ivory_tex():
    # horn: fine growth rings across it, a sheen
    return tex(lambda x, y: IVORY[5] if y % 4 == 0 and x % 3 else IVORY[4] if y % 4 == 1 else IVORY[3] if y % 4 != 3 else IVORY[2])


def porphyry_tex(seed=4, dark=False):
    n = noise(seed)
    r = random.Random(seed)
    specks = {(r.randrange(16), r.randrange(16)) for _ in range(5)}
    base = 2 if dark else 3

    def px(x, y):
        if (x, y) in specks: return PORPH[base + 2] if base + 2 < 6 else PORPH[5]
        v = n[y][x]
        return PORPH[base - 1] if v < 0.15 else PORPH[base + 1] if v > 0.88 else PORPH[base]
    return tex(px)


def panel_tex():
    # an inset panel: a raised border lit at the top left, a sunk field in shadow at its top left
    def px(x, y):
        e = min(x, y, 15 - x, 15 - y)
        if e == 0: return PORPH[1]
        if e == 1: return PORPH[4] if (x == 1 or y == 1) else PORPH[2]
        if e == 2: return PORPH[2] if (x == 2 or y == 2) else PORPH[4]
        return PORPH[3] if (x * 3 + y * 5) % 11 else PORPH[4]
    return tex(px)


def meander_tex():
    # a running meander (Greek key) in gold on dark porphyry, two rows to a repeat of eight
    key = ["########", "#......#", "#.####.#", "#.#..#.#", "#.#.##.#", "#.#....#", "#.######", "#......."]

    def px(x, y):
        c = key[y % 8][x % 8]
        return GOLD[4] if c == "#" else PORPH[1]
    return tex(px)


def flutes_tex():
    def px(x, y):
        f = x % 4
        return PORPH[5] if f == 0 else PORPH[4] if f == 1 else PORPH[3] if f == 2 else PORPH[1]
    return tex(px)


def inlay_tex(lit):
    hi = (255, 250, 220) if lit else GOLD[4]
    lo = (255, 226, 140) if lit else GOLD[2]
    return tex(lambda x, y: hi if x % 4 < 2 else lo)


def mouth_tex():
    def px(x, y):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        return (16, 8, 6) if r < 3 else (36, 20, 12) if r < 5.5 else (60, 34, 20)
    return tex(px)


def gem_tex(c):
    t = ramp(c)

    def px(x, y):
        d = abs(x - 7.5) + abs(y - 7.5)
        if d < 2: return (255, 255, 255)
        return t[5] if x + y < 14 else t[4] if d < 6 else t[3]
    return tex(px)


def fruit_tex():
    """A sheet of fruit: grape (top left), apple (top right), leaf (bottom left), wheat (bottom right)."""
    def px(x, y):
        q = (x >= 8) + 2 * (y >= 8)
        lx, ly = x % 8, y % 8
        if q == 0:      # grape
            return (206, 160, 246) if (lx + ly) < 4 else (138, 70, 190) if (lx + ly) < 10 else (92, 40, 140)
        if q == 1:      # apple
            return (255, 176, 150) if (lx + ly) < 4 else (222, 48, 44) if (lx + ly) < 10 else (150, 24, 30)
        if q == 2:      # leaf
            return (150, 210, 90) if lx < 3 else (90, 160, 60) if lx < 6 else (54, 110, 40)
        return GOLD[5] if ly % 2 == 0 and lx % 3 else GOLD[3] if lx % 3 else GOLD[2]
    return tex(px)


def shell_tex():
    sh = ramp((88, 132, 156))

    def px(x, y):
        rib = x % 4
        c = sh[4] if rib == 0 else sh[3] if rib in (1, 2) else sh[2]
        if y % 5 == 4: c = sh[2] if rib else sh[3]
        return c
    return tex(px)


def mantle_tex():
    # the bright mantle of a giant clam: blues and greens in a wavy mottle, flecked with light
    n = noise(71)

    def px(x, y):
        w = math.sin(x * 0.9 + y * 0.45) + n[y][x] * 0.8
        if n[y][x] > 0.94: return (220, 255, 250)
        return (40, 190, 200) if w > 0.9 else (50, 120, 210) if w > 0.1 else (30, 150, 120) if w > -0.6 else (24, 80, 130)
    return tex(px)


def nacre_tex():
    def px(x, y):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        return (246, 220, 232) if math.sin(r * 1.2 + x * 0.25) > 0 else (220, 230, 244)
    return tex(px)


def pearl_tex():
    t = ramp((220, 218, 240))
    return tex(lambda x, y: (255, 255, 255) if x + y < 6 else t[4] if x + y < 16 else t[3] if x + y < 24 else t[2])


def deepslate_tex(seed):
    # deepslate: tight, near-horizontal layers in cool greys, short cracks between them
    D = ((40, 40, 46), (54, 54, 62), (68, 68, 78), (84, 84, 94), (100, 100, 110))
    n = noise(seed)

    def px(x, y):
        layer = (y + (x // 6)) % 4
        c = D[1] if layer == 0 else D[2] if layer in (1, 2) else D[3]
        if n[y][x] > 0.9: c = D[4]
        if n[y][x] < 0.08: c = D[0]
        if layer == 0 and n[y][x] < 0.35: c = D[0]
        return c
    return tex(px)


def soul_soil_tex(seed):
    S = ((52, 38, 30), (70, 52, 40), (88, 66, 52), (104, 82, 66))
    n = noise(seed)

    def px(x, y):
        v = n[y][x] + 0.25 * math.sin(x * 0.7 + y * 1.3)
        return S[0] if v < 0.15 else S[1] if v < 0.45 else S[2] if v < 0.95 else S[3]
    return tex(px)


def crystal_overlay(base, glint, spots, seed):
    """Crystals for an ore's overlay: clusters of little pointed crystals, lit on the top left, a glint in each."""
    t = ramp(base)
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for cx, cy in spots:
        shape = [(0, 0), (1, 0), (0, 1), (1, 1), (0, -1), (-1, 0), (2, 1), (1, 2)]
        for i, (dx, dy) in enumerate(shape):
            if i > 3 and rnd.random() < 0.4: continue
            x, y = cx + dx, cy + dy
            if not (0 <= x < 16 and 0 <= y < 16): continue
            c = t[5] if (dx, dy) in ((0, 0), (0, -1)) else t[4] if dx <= 0 else t[3] if dy <= 0 else t[2]
            img.putpixel((x, y), c + (255,))
        img.putpixel((cx, cy), tuple(glint) + (255,))
    # an outline of shadow round each cluster
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3]: continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))):
                out.putpixel((x, y), t[0] + (255,))
    return out


# ---------------------------------------------------------------------------------------------------- models
def orb_tex(core):
    """The orb's glass: a swirl of its core's light, brightest at the heart; without a core, dark smoked glass."""
    if core == "none":
        return tex(lambda x, y: (58, 46, 70) if (x * 3 + y * 5) % 7 else (84, 70, 100))
    c = {"storm": (255, 222, 110), "tide": (80, 216, 230), "shade": (164, 110, 250)}[core]
    t = ramp(c)

    def px(x, y):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        swirl = math.sin(a * 2 + r * 0.9)
        if r < 2.2: return (255, 255, 250)
        if swirl > 0.55: return t[5]
        if swirl > -0.1: return t[4]
        return t[3] if r < 6 else t[2]
    return tex(px)


def orb_model(core):
    """The Orb of Plenty: a glassy orb that glows with its core, held by a gold tripod inside a tilted gold ring."""
    tx = {"particle": f"{MOD}:block/shrine_gold", "gold": f"{MOD}:block/shrine_gold", "orb": f"{MOD}:block/orb_of_plenty_{core}",
          "dark": f"{MOD}:block/shrine_porphyry_dark"}
    glow = 15 if core != "none" else None
    els = [el((4, 0, 4), (12, 1, 12), "gold"),                                   # the foot
           el((6.5, 1, 6.5), (9.5, 2, 9.5), "dark"),
           el((7.3, 2, 7.3), (8.7, 4.5, 8.7), "gold")]                           # the stem
    for ang in (0.0, 120.0, 240.0):                                              # three claws cupping the orb
        r = {"origin": [8, 4, 8], "axis": "y", "angle": ang if ang <= 180 else ang - 360}
        els.append(el((7.4, 3.6, 4.6), (8.6, 7.2, 5.4), "gold", rot=r))
        els.append(el((7.4, 3.6, 5.4), (8.6, 4.4, 7.4), "gold", rot=r))
    # the orb: a rounded body of three crossed blocks, turned again to round it further
    c = (8, 9, 8)
    body = [((4, 6.5, 5.5), (12, 11.5, 10.5)), ((5.5, 5, 5.5), (10.5, 13, 10.5)), ((5.5, 6.5, 4), (10.5, 11.5, 12)), ((5, 6, 5), (11, 12, 11))]
    for a, b in body:
        els.append(el(a, b, "orb", uv=(2, 2, 14, 14), emit=glow))
    for a, b in body[:3]:
        els.append(el(a, b, "orb", uv=(2, 2, 14, 14), emit=glow, rot={"origin": list(c), "axis": "y", "angle": 45.0}))
    # a tilted ring of gold round it
    ring = {"origin": list(c), "x": 22.5, "y": 30.0, "z": 0.0}
    for a, b in (((2.5, 8.6, 2.5), (13.5, 9.4, 3.3)), ((2.5, 8.6, 12.7), (13.5, 9.4, 13.5)), ((2.5, 8.6, 3.3), (3.3, 9.4, 12.7)), ((12.7, 8.6, 3.3), (13.5, 9.4, 12.7))):
        els.append(el(a, b, "gold", rot=ring))
    return {"parent": "minecraft:block/block", "textures": tx, "elements": els,
            "display": {"gui": {"rotation": [25, 225, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]}}}


def plinth_model(lit):
    tx = {"particle": f"{MOD}:block/shrine_panel", "panel": f"{MOD}:block/shrine_panel", "porph": f"{MOD}:block/shrine_porphyry",
          "dark": f"{MOD}:block/shrine_porphyry_dark", "gold": f"{MOD}:block/shrine_gold", "meander": f"{MOD}:block/shrine_meander",
          "inlay": f"{MOD}:block/shrine_inlay{'_lit' if lit else ''}", "top": f"{MOD}:block/polished_porphyry"}
    glow = 12 if lit else None
    els = [
        el((0, 0, 0), (16, 1.5, 16), "dark"),                                             # the footing
        el((0.5, 1.5, 0.5), (15.5, 2.5, 15.5), "gold"),                                   # a gold moulding on it
        el((1.5, 2.5, 1.5), (14.5, 11.5, 14.5), {f: "panel" for f in ("north", "south", "east", "west")} | {"down": "dark"},
           uv={f: (0, 2, 16, 14) for f in ("north", "south", "east", "west")}),        # the body of inset panels
        el((1, 11.5, 1), (15, 12.5, 15), "gold"),
        el((0.5, 12.5, 0.5), (15.5, 14.5, 15.5), {f: "meander" for f in ("north", "south", "east", "west")} | {"down": "dark"},
           uv={f: (0, 0, 16, 4) for f in ("north", "south", "east", "west")}),          # the cornice with its meander
        el((0, 14.5, 0), (16, 16, 16), {f: "porph" for f in ("north", "south", "east", "west", "down")} | {"up": "top"}),
    ]
    # an inlaid line round each panel, standing a hair proud, glowing when the shrine is whole
    for (a, b) in (((3.5, 4.5, 1.3), (12.5, 5, 1.5)), ((3.5, 9, 1.3), (12.5, 9.5, 1.5)), ((3.5, 4.5, 1.3), (4, 9.5, 1.5)), ((12, 4.5, 1.3), (12.5, 9.5, 1.5))):
        for turn in (0, 90, 180, 270):
            rot = None if turn == 0 else {"origin": [8, 8, 8], "axis": "y", "angle": float(turn if turn <= 90 else turn - 360) if turn != 180 else 0.0}
            if turn == 180:
                aa, bb = (a[0], a[1], 16 - b[2]), (b[0], b[1], 16 - a[2])
                els.append(el(aa, bb, "inlay", emit=glow))
            elif turn == 90:
                aa, bb = (16 - b[2], a[1], a[0]), (16 - a[2], b[1], b[0])
                els.append(el(aa, bb, "inlay", emit=glow))
            elif turn == 270:
                aa, bb = (a[2], a[1], a[0]), (b[2], b[1], b[0])
                els.append(el(aa, bb, "inlay", emit=glow))
            else:
                els.append(el(a, b, "inlay", emit=glow))
    return {"parent": "minecraft:block/block", "textures": tx, "elements": els}


def column_models(lit):
    sfx = "_lit" if lit else ""
    tx = {"particle": f"{MOD}:block/shrine_flutes", "flutes": f"{MOD}:block/shrine_flutes", "porph": f"{MOD}:block/shrine_porphyry",
          "dark": f"{MOD}:block/shrine_porphyry_dark", "gold": f"{MOD}:block/shrine_gold", "inlay": f"{MOD}:block/shrine_inlay{sfx}",
          "top": f"{MOD}:block/polished_porphyry"}
    glow = 10 if lit else None
    side = {f: "flutes" for f in ("north", "south", "east", "west")}
    shaft = [el((4, 0, 4), (12, 16, 12), side | {"up": "top", "down": "top"}),
             el((3, 0, 5), (13, 16, 11), side), el((5, 0, 3), (11, 16, 13), side)]   # a rounded section: a square and a cross
    for a, b in (((7.5, 0, 2.7), (8.5, 16, 3.0)), ((7.5, 0, 13.0), (8.5, 16, 13.3)), ((2.7, 0, 7.5), (3.0, 16, 8.5)), ((13.0, 0, 7.5), (13.3, 16, 8.5))):
        shaft.append(el(a, b, "inlay", emit=glow))
    base = [el((1.5, 0, 1.5), (14.5, 2, 14.5), {f: "dark" for f in ("north", "south", "east", "west", "down")} | {"up": "top"}), el((2.2, 2, 2.2), (13.8, 3, 13.8), "gold"), el((2.8, 3, 2.8), (13.2, 4, 13.2), "porph")]
    capital = [el((3, 11, 3), (13, 12, 13), "gold"),                       # the necking
               el((2.2, 12, 2.2), (13.8, 13.5, 13.8), "porph"),           # the echinus
               el((1.2, 13.5, 1.2), (14.8, 16, 14.8), {f: "dark" for f in ("north", "south", "east", "west", "down")} | {"up": "top"}),   # the abacus
               el((1, 15.4, 1), (15, 15.9, 15), "gold")]
    for x in (1.0, 13.0):                                                  # the volutes at its corners
        for z in (1.0, 13.0):
            capital.append(el((x, 12.2, z), (x + 2, 14.2, z + 2), "gold"))
    return ({"parent": "minecraft:block/block", "textures": tx, "elements": shaft},
            {"parent": "minecraft:block/block", "textures": tx, "elements": base},
            {"parent": "minecraft:block/block", "textures": tx, "elements": capital})


def clam_model(open_):
    tx = {"particle": f"{MOD}:block/clam_shell", "shell": f"{MOD}:block/clam_shell", "mantle": f"{MOD}:block/clam_mantle",
          "nacre": f"{MOD}:block/clam_nacre", "pearl": f"{MOD}:block/clam_pearl"}
    els = [el((1, 0, 1.5), (15, 2.6, 14.5), {f: "shell" for f in ("north", "south", "east", "west", "down")} | {"up": "nacre"})]
    for x in (1.5, 4.5, 7.5, 10.5):                                     # ribs along the lower shell
        els.append(el((x + 0.4, 0, 1), (x + 2.0, 2.2, 15), "shell"))
    for i, x in enumerate(range(1, 15, 2)):                             # its wavy lip
        els.append(el((x, 2.6, 1.5), (x + 2, 3.1 + (0.5 if i % 2 else 0), 2.3), "shell"))
    els.append(el((1.6, 2.6, 2.3), (14.4, 3.2, 14.0), "mantle"))         # the mantle, bright between the halves
    hinge = {"origin": [8, 3.2, 14.5], "axis": "x", "angle": 50.0} if open_ else None
    top = [el((1, 3.2, 1.5), (15, 5.6, 14.5), {f: "shell" for f in ("north", "south", "east", "west", "up")} | {"down": "nacre"}, rot=hinge)]
    for x in (1.5, 4.5, 7.5, 10.5):
        top.append(el((x + 0.4, 5.6, 1.5), (x + 2.0, 6.4, 14.5), "shell", rot=hinge))
    for i, x in enumerate(range(1, 15, 2)):
        top.append(el((x, 2.7 - (0.5 if i % 2 else 0), 1.5), (x + 2, 3.2, 2.3), "shell", rot=hinge))
    els += top
    if open_:
        for a, b in (((6.5, 2.6, 6.5), (9.5, 5.6, 9.5)), ((6, 3.1, 7), (10, 5.1, 9)), ((7, 3.1, 6), (9, 5.1, 10))):
            els.append(el(a, b, "pearl", uv=(2, 2, 10, 10), emit=4))
    return {"parent": "minecraft:block/block", "textures": tx, "elements": els}


def ore_model(base_tex, overlay_tex, emit):
    return {"parent": "minecraft:block/block", "textures": {"particle": base_tex, "base": base_tex, "overlay": overlay_tex},
            "elements": [el((0, 0, 0), (16, 16, 16), "base", uv=(0, 0, 16, 16)),
                         el((0, 0, 0), (16, 16, 16), "overlay", uv=(0, 0, 16, 16), emit=emit)]}


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    textures = {
        "shrine_gold": gold_tex(), "shrine_porphyry": porphyry_tex(4), "shrine_porphyry_dark": porphyry_tex(9, dark=True),
        "shrine_panel": panel_tex(), "shrine_meander": meander_tex(), "shrine_flutes": flutes_tex(),
        "shrine_inlay": inlay_tex(False), "shrine_inlay_lit": inlay_tex(True), "clam_shell": shell_tex(), "clam_mantle": mantle_tex(), "clam_nacre": nacre_tex(), "clam_pearl": pearl_tex(),
        "ore_deepslate": deepslate_tex(17), "ore_soul_soil": soul_soil_tex(23),
        "deepslate_adamantine_ore_crystals": crystal_overlay((150, 206, 222), (236, 220, 255), [(3, 3), (11, 4), (5, 11), (12, 12), (8, 7)], 5),
        "stygian_iron_ore_lumps": crystal_overlay((74, 76, 96), (110, 240, 230), [(4, 4), (11, 5), (6, 11), (12, 12)], 6),
    }
    for name, img in textures.items():
        img.save(os.path.join(T, f"{name}.png"))
    # (the old flat pictures these replace)
    import glob
    for old in ("shrine_ivory", "cornucopia_mouth", "cornucopia_fruit", "cornucopia_gem_storm", "cornucopia_gem_tide", "cornucopia_gem_shade", "cornucopia_horn", "cornucopia_rim", "cornucopia_stand", "cornucopia_apple", "cornucopia_grapes", "cornucopia_wheat",
                "cornucopia_mouth_none", "cornucopia_mouth_storm", "cornucopia_mouth_tide", "cornucopia_mouth_shade",
                "plinth_of_plenty_side", "plinth_of_plenty_side_lit", "plinth_of_plenty_top", "plinth_of_plenty_top_lit",
                "column_of_plenty_shaft", "column_of_plenty_shaft_lit", "column_of_plenty_trim", "column_of_plenty_trim_lit",
                "giant_clam_shell", "giant_clam_inside", "giant_clam_pearl"):
        p = os.path.join(T, old + ".png")
        if os.path.exists(p): os.remove(p)
    for old in glob.glob(os.path.join(M, "column_of_plenty_*.json")) + glob.glob(os.path.join(M, "plinth_of_plenty*.json")) + glob.glob(os.path.join(M, "cornucopia_*.json")):
        os.remove(old)

    # the cornucopia
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    cv = {}
    for core in ("none", "storm", "tide", "shade"):
        orb_tex(core).save(os.path.join(T, f"orb_of_plenty_{core}.png"))
        wj(os.path.join(M, f"cornucopia_{core}.json"), orb_model(core))
        for f, r in rot.items():
            cv[f"core={core},facing={f}"] = {"model": f"{MOD}:block/cornucopia_{core}", "y": r}
    wj(os.path.join(M, "cornucopia.json"), {"parent": f"{MOD}:block/cornucopia_storm"})
    wj(os.path.join(assets, "blockstates/cornucopia.json"), {"variants": cv})

    # the plinth
    for lit in (False, True):
        wj(os.path.join(M, f"plinth_of_plenty{'_lit' if lit else ''}.json"), plinth_model(lit))
    wj(os.path.join(assets, "blockstates/plinth_of_plenty.json"), {"variants": {"lit=false": {"model": f"{MOD}:block/plinth_of_plenty"},
                                                                             "lit=true": {"model": f"{MOD}:block/plinth_of_plenty_lit"}}})

    # the columns
    for lit in (False, True):
        sfx = "_lit" if lit else ""
        shaft, base, capital = column_models(lit)
        wj(os.path.join(M, f"column_of_plenty_shaft{sfx}.json"), shaft)
        wj(os.path.join(M, f"column_of_plenty_base{sfx}.json"), base)
        wj(os.path.join(M, f"column_of_plenty_capital{sfx}.json"), capital)
    shaft, base, capital = column_models(True)
    wj(os.path.join(M, "column_of_plenty.json"), {"parent": "minecraft:block/block", "textures": shaft["textures"],
                                                   "elements": shaft["elements"] + base["elements"] + capital["elements"]})
    parts = []
    for lit in ("false", "true"):
        sfx = "_lit" if lit == "true" else ""
        parts.append({"when": {"lit": lit}, "apply": {"model": f"{MOD}:block/column_of_plenty_shaft{sfx}"}})
        parts.append({"when": {"lit": lit, "base": "true"}, "apply": {"model": f"{MOD}:block/column_of_plenty_base{sfx}"}})
        parts.append({"when": {"lit": lit, "capital": "true"}, "apply": {"model": f"{MOD}:block/column_of_plenty_capital{sfx}"}})
    wj(os.path.join(assets, "blockstates/column_of_plenty.json"), {"multipart": parts})

    # the clam
    wj(os.path.join(M, "giant_clam.json"), clam_model(False))
    wj(os.path.join(M, "giant_clam_pearl.json"), clam_model(True))

    # the ores: a base and an overlay of crystals that glint in the dark
    wj(os.path.join(M, "deepslate_adamantine_ore.json"), ore_model(t("ore_deepslate"), t("deepslate_adamantine_ore_crystals"), 6))
    wj(os.path.join(M, "stygian_iron_ore.json"), ore_model(t("ore_soul_soil"), t("stygian_iron_ore_lumps"), 4))
    for old in ("deepslate_adamantine_ore", "stygian_iron_ore"):
        p = os.path.join(T, old + ".png")
        if os.path.exists(p): os.remove(p)
