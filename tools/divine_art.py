"""Divine resources: what is won from the world itself, the god-metals forged from it, the cores, the relics made
from them, four sets of armour, and the Cornucopia. Pictures at 16x16, lit from the top left; models, recipes, tags,
loot and world generation.

The long roads:
  storm glass   - lightning strikes sand -> fulgurite -> blast furnace -> storm glass
  celestial bronze - copper, gold and storm glass
  abyssal pearl - a giant clam set deep in an ocean grows one, slowly
  orichalcum    - gold, prismarine crystals and an abyssal pearl
  stygian iron  - ore in the soul sand valleys of the nether -> blast furnace
  cores         - four of a god-metal, four of its resource, an adamantine ingot at the heart
  relics        - a core, the metal, the tower's essence from its summit, and something of the god's own
  Cornucopia    - all three cores, a nether star, two blocks of adamantine, a goat horn and gold
"""
import math
import os
import random

from PIL import Image

import panoply_art

MOD = "realms_of_chaos"
GOLD, GOLD_HI, GOLD_LO = (232, 186, 66), (255, 232, 140), (150, 104, 30)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def paint(fn, size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            c = fn(x, y)
            if c is not None:
                img.putpixel((x, y), tuple(c[:3]) + ((c[3],) if len(c) == 4 else (255,)))
    return img


def noise(seed):
    rnd = random.Random(seed)
    return [[0.92 + rnd.random() * 0.16 for _ in range(16)] for _ in range(16)]


def sprite(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), tuple(pal[ch]) + (255,))
    return img


def outlined(cells, outline):
    """Cells {(x, y): colour}, with an outline wherever a cell meets empty space."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in cells.items():
        img.putpixel((x, y), tuple(c) + (255,))
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in cells and img.getpixel((nx, ny))[3] == 0:
                img.putpixel((nx, ny), tuple(outline) + (255,))
    return img


# an ingot, a gem, a lump and an orb, as shapes: O outline, W white, H light, M mid, D dark, A accent
INGOT = ["................", "................", "................", "................", "......OOOO......", "....OOWHHHOO....",
         "..OOHHHHMMMMOO..", ".OHHHMMMMMMADDO.", ".OMMMMMMMMDDDDO.", ".OMMMMDDDDDDDDO.", "..OODDDDDDDDOO..", "....OODDDDOO....",
         "......OOOO......", "................", "................", "................"]
LUMP = ["................", "................", "................", ".....OOOO.......", "...OOHWHMOO.....", "..OHHMMAMMDO....",
        "..OHMMMMMDDDO...", ".OHMMAMMMDDDO...", ".OMMMMMMDDADDO..", ".OMMDDDMDDDDDO..", "..ODDDADDDDDO...", "...OODDDDDOO....",
        ".....OOOOO......", "................", "................", "................"]
ORB = ["................", "................", ".....OOOOOO.....", "....OHHWHMMO....", "...OHWWHMMMDO...", "..OHWHMMMMMMDO..",
       "..OHHMMMMMMMDO..", "..OHMMMMMMMDDO..", "..OMMMMMMMDDDO..", "..OMMMMMMDDDDO..", "...OMMMMDDDDO...", "....ODDDDDDO....",
       ".....OOOOOO.....", "................", "................", "................"]
SHARD = ["................", "................", ".........OO.....", "........OWHO....", ".......OWHMO....", "......OWHMMDO...",
         ".....OHHMMDO....", "....OHHMMDDO....", "....OHMMDDO.....", "...OHMMDDO......", "...OMMDDO.......", "..OMMDO.........",
         "..ODDO..........", "..OOO...........", "................", "................"]


def core(base, accent, glow):
    """A core: a ring of the god-metal round a glowing heart, four studs about it."""
    cells = {}
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= 6.4:
                if r <= 2.4:
                    cells[(x, y)] = (255, 255, 255) if r < 1.0 else glow
                elif r <= 3.4:
                    cells[(x, y)] = shade(glow, 0.7)
                else:
                    lit = (x + y) < 15
                    cells[(x, y)] = shade(base, 1.25 if lit and r > 5.4 else 1.0 if lit else 0.7)
    for x, y in ((7, 1), (8, 1), (14, 7), (14, 8), (7, 14), (8, 14), (1, 7), (1, 8)):
        cells[(x, y)] = accent
    return outlined(cells, shade(base, 0.3))


METALS = {
    # id: (outline, white, light, mid, dark, accent)
    "celestial_bronze": ((70, 40, 12), (255, 246, 200), (244, 200, 110), (214, 156, 66), (150, 96, 34), (120, 200, 255)),
    "orichalcum": ((70, 28, 16), (255, 226, 200), (240, 160, 110), (214, 112, 68), (150, 70, 40), (90, 220, 210)),
    "stygian_iron": ((14, 12, 20), (170, 230, 226), (92, 96, 112), (60, 62, 76), (38, 38, 50), (96, 220, 214)),
}

# armour: set -> (base, trim)
ARMOR = {
    "adamantine": ((160, 206, 220), (150, 112, 222)),
    "celestial_bronze": ((214, 160, 72), (250, 240, 170)),
    "orichalcum": ((214, 116, 70), (90, 214, 204)),
    "stygian_iron": ((56, 56, 70), (96, 220, 214)),
}
PIECES = [("helmet", "helmet"), ("chestplate", "chest"), ("leggings", "legs"), ("boots", "boots")]


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/block")
    TI = os.path.join(assets, "textures/item")
    M = os.path.join(assets, "models/block")
    MI = os.path.join(assets, "models/item")
    t = lambda n: f"{MOD}:block/{n}"
    g = noise(5150)

    def item_model(name, handheld=False):
        wj(os.path.join(MI, f"{name}.json"), {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})

    def cube(name):
        wj(os.path.join(M, f"{name}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": t(name)}})
        wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})

    # ---- fulgurite: sand fused by lightning into branching tubes of dark glass
    SAND = ((206, 186, 136), (220, 202, 152), (190, 168, 118), (236, 222, 176))
    rnd = random.Random(88)
    tubes = set()
    for _ in range(5):
        x, y = rnd.randrange(16), 0
        while y < 16:
            tubes.add((x % 16, y))
            y += 1
            x += rnd.choice((-1, 0, 0, 1))

    def fulgurite(x, y):
        if (x, y) in tubes:
            return (60, 74, 70) if (x + y) % 3 else (120, 150, 140)
        if any((x + dx, y) in tubes for dx in (-1, 1)):
            return (150, 136, 100)
        return shade(SAND[(x * 7 + y * 3) % 4], g[y][x])
    paint(fulgurite).save(os.path.join(T, "fulgurite.png"))
    cube("fulgurite")

    # ---- stygian iron ore: soul soil with lumps of black iron that hold a soul's light
    SOIL = ((74, 56, 44), (88, 68, 54), (62, 46, 36), (100, 80, 64))
    lumps = [(4, 4), (11, 5), (6, 11), (12, 12)]

    def stygian_ore(x, y):
        for cx, cy in lumps:
            d = abs(x - cx) + abs(y - cy)
            if d == 0: return (96, 220, 214)
            if d == 1: return (92, 96, 112) if (x < cx or y < cy) else (46, 46, 60)
            if d == 2 and (x - cx) * (y - cy) == 0: return (26, 24, 34)
        return shade(SOIL[(x * 5 + y * 3) % 4], g[(y * 3) % 16][(x * 5) % 16])
    paint(stygian_ore).save(os.path.join(T, "stygian_iron_ore.png"))
    cube("stygian_iron_ore")

    # ---- the giant clam: a ridged shell, nacre inside, a pearl when it has grown one
    SHELL = ((60, 90, 110), (92, 132, 152), (130, 172, 188), (176, 210, 220))

    def shell(x, y):
        ridge = x % 3
        c = SHELL[1] if ridge == 1 else SHELL[2] if ridge == 0 else SHELL[0]
        if y == 0: c = SHELL[3]
        return shade(c, g[y][x])

    def nacre(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        return shade((236, 196, 210), 0.9 + 0.1 * math.sin(r * 1.3)) if r < 7 else (210, 170, 190)

    def pearl(x, y):
        r = math.hypot(x - 6, y - 6)
        return (255, 255, 255) if r < 2 else (230, 236, 246) if r < 5 else (196, 204, 222)
    paint(shell).save(os.path.join(T, "giant_clam_shell.png"))
    paint(nacre).save(os.path.join(T, "giant_clam_inside.png"))
    paint(pearl).save(os.path.join(T, "giant_clam_pearl.png"))

    def box(a, b, tex, uv_side, uv_top, uv_bottom=None, top_tex=None, bottom_tex=None, rot=None):
        faces = {f: {"texture": f"#{tex}", "uv": list(uv_side)} for f in ("north", "south", "east", "west")}
        faces["up"] = {"texture": f"#{top_tex or tex}", "uv": list(uv_top)}
        faces["down"] = {"texture": f"#{bottom_tex or tex}", "uv": list(uv_bottom or uv_top)}
        el = {"from": list(a), "to": list(b), "faces": faces}
        if rot: el["rotation"] = rot
        return el
    tx = {"particle": t("giant_clam_shell"), "shell": t("giant_clam_shell"), "inside": t("giant_clam_inside"), "pearl": t("giant_clam_pearl")}
    lower = box((1, 0, 1), (15, 3, 15), "shell", (1, 13, 15, 16), (1, 1, 15, 15), top_tex="inside")
    closed = [lower, box((1, 3, 1), (15, 6, 15), "shell", (1, 0, 15, 3), (1, 1, 15, 15), bottom_tex="inside")]
    hinge = {"origin": [8, 3, 15], "axis": "x", "angle": 45.0}
    opened = [lower, box((1, 3, 1), (15, 6, 15), "shell", (1, 0, 15, 3), (1, 1, 15, 15), bottom_tex="inside", rot=hinge),
              box((6, 3, 6), (10, 7, 10), "pearl", (2, 2, 6, 6), (2, 2, 6, 6))]
    wj(os.path.join(M, "giant_clam.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": closed})
    wj(os.path.join(M, "giant_clam_pearl.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": opened})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in rot.items():
        for p in ("false", "true"):
            for w in ("false", "true"):
                variants[f"facing={f},pearl={p},waterlogged={w}"] = {"model": f"{MOD}:block/giant_clam{'_pearl' if p == 'true' else ''}", "y": r}
    wj(os.path.join(assets, "blockstates/giant_clam.json"), {"variants": variants})
    wj(os.path.join(assets, "items/giant_clam.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/giant_clam_pearl"}})

    # ---- the Cornucopia: a golden horn banded in ivory on a porphyry plinth, spilling fruit, a core glowing in its mouth
    def horn(x, y):
        band = y % 5
        if band == 0: return (240, 230, 200)
        if band == 1: return (200, 186, 150)
        return GOLD_HI if x < 4 else GOLD if x < 11 else GOLD_LO
    paint(horn).save(os.path.join(T, "cornucopia_horn.png"))

    def plinth(x, y):
        if y in (0, 1): return GOLD if y == 0 else GOLD_LO
        if y == 15: return (58, 18, 34)
        return shade((120, 42, 66), g[y][x])
    paint(plinth).save(os.path.join(T, "cornucopia_plinth.png"))

    def fruit(x, y):
        # apples, grapes and ears of grain, packed together
        q = (x // 4 + y // 4) % 3
        if q == 0: return (210, 50, 50) if (x + y) % 3 else (250, 120, 110)
        if q == 1: return (120, 60, 160) if (x + y) % 2 else (170, 110, 210)
        return (230, 190, 80) if y % 2 else (200, 150, 50)
    paint(fruit).save(os.path.join(T, "cornucopia_fruit.png"))
    glows = {"none": ((40, 24, 14), (70, 44, 24)), "storm": ((255, 236, 140), (255, 255, 230)), "tide": ((90, 220, 230), (220, 255, 255)),
             "shade": ((150, 100, 240), (230, 210, 255))}
    for core_id, (c1, c2) in glows.items():
        def mouth(x, y, c1=c1, c2=c2):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 6.5: return GOLD_LO
            return c2 if r < 2.2 else c1 if r < 4.5 else shade(c1, 0.7)
        paint(mouth).save(os.path.join(T, f"cornucopia_mouth_{core_id}.png"))

    def cornucopia(core_id):
        tex = {"particle": t("cornucopia_horn"), "horn": t("cornucopia_horn"), "plinth": t("cornucopia_plinth"), "fruit": t("cornucopia_fruit"),
               "mouth": t(f"cornucopia_mouth_{core_id}"), "top": t("polished_porphyry")}
        els = [box((2, 0, 2), (14, 3, 14), "plinth", (2, 13, 14, 16), (2, 2, 14, 14), top_tex="top"),
               box((1, 3, 1), (15, 4, 15), "plinth", (1, 0, 15, 1), (1, 1, 15, 15), top_tex="top")]
        # the horn, narrowing from its mouth at the front to a tip curling up behind
        segs = [((4, 4, 1), (12, 12, 5)), ((5, 5, 5), (11, 11, 8)), ((6, 6, 8), (10, 10, 11)), ((7, 7, 11), (9, 9, 13)),
                ((7, 9, 12), (9, 12, 14)), ((7, 12, 11), (9, 14, 13))]
        for n, (a, b) in enumerate(segs):
            e = box(a, b, "horn", (0, n * 2, b[0] - a[0], n * 2 + b[1] - a[1]), (0, 0, b[0] - a[0], b[2] - a[2]))
            if n == 0: e["faces"]["north"] = {"texture": "#mouth", "uv": [4, 4, 12, 12]}
            els.append(e)
        els += [box((3, 4, 0), (6, 7, 3), "fruit", (0, 0, 3, 3), (0, 0, 3, 3)), box((10, 4, 0), (13, 6, 3), "fruit", (4, 4, 7, 6), (4, 4, 7, 7)),
                box((6, 4, 0), (10, 5, 2), "fruit", (8, 8, 12, 9), (8, 8, 12, 10))]
        return {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    cv = {}
    for core_id in glows:
        wj(os.path.join(M, f"cornucopia_{core_id}.json"), cornucopia(core_id))
        for f, r in rot.items():
            cv[f"core={core_id},facing={f}"] = {"model": f"{MOD}:block/cornucopia_{core_id}", "y": r}
    wj(os.path.join(M, "cornucopia.json"), {"parent": f"{MOD}:block/cornucopia_storm"})
    wj(os.path.join(assets, "blockstates/cornucopia.json"), {"variants": cv})
    wj(os.path.join(assets, "items/cornucopia.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/cornucopia_storm"}})

    # ---- items: the resources, the god-metals, the cores
    def pal(o, w, h, m, d, a):
        return {"O": o, "W": w, "H": h, "M": m, "D": d, "A": a}
    items = {
        "storm_glass": sprite(SHARD, pal((40, 50, 60), (250, 255, 255), (200, 236, 250), (130, 196, 224), (70, 120, 160), (255, 236, 120))),
        "abyssal_pearl": sprite(ORB, pal((30, 40, 70), (255, 255, 255), (226, 232, 248), (176, 190, 226), (110, 124, 176), (200, 160, 240))),
        "raw_stygian_iron": sprite(LUMP, pal((14, 12, 20), (170, 230, 226), (92, 96, 112), (60, 62, 76), (38, 38, 50), (96, 220, 214))),
    }
    for metal, colours in METALS.items():
        items[f"{metal}_ingot"] = sprite(INGOT, pal(*colours))
    items["thunder_core"] = core((214, 156, 66), (255, 246, 200), (255, 236, 120))
    items["tide_core"] = core((214, 112, 68), (90, 220, 210), (110, 230, 240))
    items["shade_core"] = core((60, 62, 76), (96, 220, 214), (170, 120, 250))
    for name, img in items.items():
        img.save(os.path.join(TI, f"{name}.png"))
        item_model(name)

    # ---- armour: an icon for each piece, and the set as it is worn
    for set_id, (base, trim) in ARMOR.items():
        main = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        legs = None
        for piece, kind in PIECES:
            name = f"{set_id}_{piece}"
            panoply_art.icon(kind, base, trim, name).save(os.path.join(TI, f"{name}.png"))
            item_model(name)
            m, l = panoply_art.worn(name, kind, base, trim)
            main.alpha_composite(m)
            if kind == "legs": legs = l
        for layer, img in (("humanoid", main), ("humanoid_leggings", legs)):
            folder = os.path.join(assets, f"textures/entity/equipment/{layer}")
            os.makedirs(folder, exist_ok=True)
            img.save(os.path.join(folder, f"{set_id}.png"))
        wj(os.path.join(assets, f"equipment/{set_id}.json"),
           {"layers": {"humanoid": [{"texture": f"{MOD}:{set_id}"}], "humanoid_leggings": [{"texture": f"{MOD}:{set_id}"}]}})

    # ---- recipes: the long roads
    R = os.path.join(data, "recipe")

    def shaped(rid, key, pattern, result, count=1, cat="misc"):
        wj(os.path.join(R, f"{rid}.json"), {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
                                            "result": {"id": result if ":" in result else f"{MOD}:{result}", "count": count}})

    def blast(rid, src, result, time=300, xp=1.0):
        wj(os.path.join(R, f"{rid}.json"), {"type": "minecraft:blasting", "category": "misc", "cookingtime": time, "experience": xp,
                                            "ingredient": src, "result": {"id": f"{MOD}:{result}"}})
    blast("storm_glass_from_blasting_fulgurite", f"{MOD}:fulgurite", "storm_glass")
    blast("stygian_iron_ingot_from_blasting_raw_stygian_iron", f"{MOD}:raw_stygian_iron", "stygian_iron_ingot", 400, 1.5)
    blast("stygian_iron_ingot_from_blasting_stygian_iron_ore", f"{MOD}:stygian_iron_ore", "stygian_iron_ingot", 400, 1.5)
    shaped("celestial_bronze_ingot", {"C": "minecraft:copper_ingot", "G": "minecraft:gold_ingot", "S": f"{MOD}:storm_glass"}, ["CGC", "GSG", "CGC"], "celestial_bronze_ingot", 2)
    shaped("orichalcum_ingot", {"G": "minecraft:gold_ingot", "P": "minecraft:prismarine_crystals", "A": f"{MOD}:abyssal_pearl"}, ["GPG", "PAP", "GPG"], "orichalcum_ingot", 2)
    A = f"{MOD}:adamantine_ingot"
    shaped("thunder_core", {"B": f"{MOD}:celestial_bronze_ingot", "S": f"{MOD}:storm_glass", "A": A}, ["BSB", "SAS", "BSB"], "thunder_core")
    shaped("tide_core", {"O": f"{MOD}:orichalcum_ingot", "P": f"{MOD}:abyssal_pearl", "A": A}, ["OPO", "PAP", "OPO"], "tide_core")
    shaped("shade_core", {"I": f"{MOD}:stygian_iron_ingot", "E": "minecraft:echo_shard", "A": A}, ["IEI", "EAE", "IEI"], "shade_core")
    shaped("zeus_weapon_from_thunder_core", {"L": "minecraft:lightning_rod", "C": f"{MOD}:thunder_core", "B": f"{MOD}:celestial_bronze_ingot",
                                             "E": f"{MOD}:essence_zeus"}, ["LCL", "BEB", " B "], "zeus_weapon", 1, "equipment")
    shaped("poseidon_weapon_from_tide_core", {"P": f"{MOD}:abyssal_pearl", "C": f"{MOD}:tide_core", "O": f"{MOD}:orichalcum_ingot", "T": "minecraft:trident",
                                              "E": f"{MOD}:essence_poseidon"}, ["PCP", "OTO", " E "], "poseidon_weapon", 1, "equipment")
    shaped("hades_weapon_from_shade_core", {"K": "minecraft:wither_skeleton_skull", "C": f"{MOD}:shade_core", "I": f"{MOD}:stygian_iron_ingot",
                                            "E": f"{MOD}:essence_hades"}, ["KCK", "IEI", " I "], "hades_weapon", 1, "equipment")
    shaped("giant_clam", {"N": "minecraft:nautilus_shell", "C": "minecraft:prismarine_crystals", "S": "minecraft:sea_pickle"}, ["NCN", "CSC", "NCN"], "giant_clam")
    shaped("cornucopia", {"T": f"{MOD}:thunder_core", "N": "minecraft:nether_star", "S": f"{MOD}:shade_core", "A": f"{MOD}:adamantine_block",
                          "H": "minecraft:goat_horn", "G": "minecraft:gold_block", "D": f"{MOD}:tide_core"}, ["TNS", "AHA", "GDG"], "cornucopia")
    ingots = {"adamantine": A, "celestial_bronze": f"{MOD}:celestial_bronze_ingot", "orichalcum": f"{MOD}:orichalcum_ingot",
              "stygian_iron": f"{MOD}:stygian_iron_ingot"}
    patterns = {"helmet": ["###", "# #"], "chestplate": ["# #", "###", "###"], "leggings": ["###", "# #", "# #"], "boots": ["# #", "# #"]}
    for set_id, ingot in ingots.items():
        for piece, pattern in patterns.items():
            shaped(f"{set_id}_{piece}", {"#": ingot}, pattern, f"{set_id}_{piece}", 1, "equipment")
        wj(os.path.join(data, f"tags/item/repairs_{set_id}.json"), {"values": [ingot]})

    # ---- loot
    wj(os.path.join(data, "loot_table/blocks/stygian_iron_ore.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{MOD}:stygian_iron_ore"},
            {"type": "minecraft:item", "name": f"{MOD}:raw_stygian_iron",
             "modifier": [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                          {"type": "minecraft:explosion_decay"}]}]}]}], "random_sequence": f"{MOD}:blocks/stygian_iron_ore"})
    pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:cornucopia"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]
    for core_id, item in (("storm", "thunder_core"), ("tide", "tide_core"), ("shade", "shade_core")):
        pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{item}"}],
                      "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:cornucopia", "properties": {"core": core_id}}]})
    wj(os.path.join(data, "loot_table/blocks/cornucopia.json"), {"type": "minecraft:block", "pools": pools})

    # ---- tags
    MC = os.path.join(os.path.dirname(data), "minecraft")
    wj(os.path.join(MC, "tags/block/needs_diamond_tool.json"), {"replace": False, "values": [f"{MOD}:deepslate_adamantine_ore", f"{MOD}:adamantine_block",
                                                                                           f"{MOD}:stygian_iron_ore", f"{MOD}:cornucopia"]})
    for piece, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        wj(os.path.join(MC, f"tags/item/{tag}.json"), {"replace": False, "values": [f"{MOD}:{s}_{piece}" for s in ARMOR]})

    # ---- stygian iron ore in the soul sand valleys
    wj(os.path.join(data, "worldgen/feature/stygian_iron_ore.json"), {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.2, "size": 5,
        "targets": [{"state": f"{MOD}:stygian_iron_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:soul_soil"}},
                    {"state": f"{MOD}:stygian_iron_ore", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:soul_sand"}}]})
    wj(os.path.join(data, "worldgen/placed_feature/stygian_iron_ore.json"), {"feature": f"{MOD}:stygian_iron_ore", "placement": [
        {"type": "minecraft:count", "count": 6}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 10}, "max_inclusive": {"absolute": 90}}},
        {"type": "minecraft:biome"}]})
    wj(os.path.join(data, "forge/biome_modifier/stygian_iron_ore.json"), {"type": "forge:add_features", "biomes": "minecraft:soul_sand_valley",
                                                                         "features": f"{MOD}:stygian_iron_ore", "step": "underground_ores"})


def lang():
    out = {
        "block.realms_of_chaos.fulgurite": "Fulgurite",
        "block.realms_of_chaos.giant_clam": "Giant Clam",
        "block.realms_of_chaos.stygian_iron_ore": "Stygian Iron Ore",
        "block.realms_of_chaos.cornucopia": "Cornucopia",
        "item.realms_of_chaos.storm_glass": "Storm Glass",
        "item.realms_of_chaos.abyssal_pearl": "Abyssal Pearl",
        "item.realms_of_chaos.raw_stygian_iron": "Raw Stygian Iron",
        "item.realms_of_chaos.celestial_bronze_ingot": "Celestial Bronze Ingot",
        "item.realms_of_chaos.orichalcum_ingot": "Orichalcum Ingot",
        "item.realms_of_chaos.stygian_iron_ingot": "Stygian Iron Ingot",
        "item.realms_of_chaos.thunder_core": "Thunder Core",
        "item.realms_of_chaos.tide_core": "Tide Core",
        "item.realms_of_chaos.shade_core": "Shade Core",
        "item.realms_of_chaos.thunder_core.lore": "The heart of Keraunos; set in a Cornucopia, it brings forth storm glass",
        "item.realms_of_chaos.tide_core.lore": "The heart of the Trident of the Deep; set in a Cornucopia, it brings forth abyssal pearls",
        "item.realms_of_chaos.shade_core.lore": "The heart of the Bident of the Dead; set in a Cornucopia, it brings forth stygian iron",
        "item.realms_of_chaos.armor_set.adamantine": "Worn whole: resistance",
        "item.realms_of_chaos.armor_set.celestial_bronze": "Worn whole: speed and haste",
        "item.realms_of_chaos.armor_set.orichalcum": "Worn whole: breathe and swim like the sea's own",
        "item.realms_of_chaos.armor_set.stygian_iron": "Worn whole: fire and withering cannot touch you",
    }
    names = {"adamantine": "Adamantine", "celestial_bronze": "Celestial Bronze", "orichalcum": "Orichalcum", "stygian_iron": "Stygian Iron"}
    for set_id, title in names.items():
        for piece, _ in PIECES:
            out[f"item.realms_of_chaos.{set_id}_{piece}"] = f"{title} {piece.capitalize()}"
    return out
