"""The furniture, drawn again and added to: richer wood (grain that wanders, knots, worn edges), tufted velvet, a
chair with turned legs and a carved, gilded crest rail - and eight new pieces, each one block: a stool, an armchair,
a nightstand, a writing desk, a standing lamp, a candelabrum, a wall shelf of jars and scrolls, a rug with a Greek
key border, and a painted amphora."""
import math
import os
import random

from PIL import Image

MOD = "realms_of_chaos"
H = 16
GOLD, GOLD_HI, GOLD_LO = (232, 186, 66), (255, 232, 140), (150, 104, 30)
PIECES = ["stool", "armchair", "nightstand", "writing_desk", "standing_lamp", "candelabrum", "wall_shelf", "rug", "amphora"]
NAMES = {"stool": "Stool", "armchair": "Armchair", "nightstand": "Nightstand", "writing_desk": "Writing Desk", "standing_lamp": "Standing Lamp",
         "candelabrum": "Candelabrum", "wall_shelf": "Wall Shelf", "rug": "Meander Rug", "amphora": "Painted Amphora"}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(fn):
    img = Image.new("RGBA", (H, H))
    for y in range(H):
        for x in range(H):
            c = fn(x, y)
            img.putpixel((x, y), c if len(c) == 4 else tuple(c) + (255,))
    return img


def wood(base, seed, along_x=False, knots=1):
    """Planks of wood: grain lines that wander, darker between boards, a knot or two, edges worn lighter."""
    rnd = random.Random(seed)
    phase = [rnd.uniform(0, 6) for _ in range(4)]
    ks = [(rnd.randrange(3, 13), rnd.randrange(3, 13)) for _ in range(knots)]

    def fn(x, y):
        u, v = (y, x) if along_x else (x, y)
        board = u // 4
        line = math.sin(u * 1.9 + math.sin(v * 0.45 + phase[board % 4]) * 1.6 + phase[(board + 1) % 4])
        f = 1.0 + 0.07 * line
        if u % 4 == 3: f *= 0.72                                       # the joint between boards
        if u % 4 == 0: f *= 1.08
        for kx, ky in ks:
            d = math.hypot(x - kx, (y - ky) * 1.6)
            if d < 1.2: f *= 0.62
            elif d < 2.3: f *= 0.86 + 0.06 * math.sin(d * 4)
        if (x + y * 7 + seed) % 23 == 0: f *= 1.12
        return shade(base, f)
    return paint(fn)


def end_grain(base, seed):
    def fn(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        f = 0.86 if int(d * 1.1) % 2 == 0 else 1.02
        if x in (0, 15) or y in (0, 15): f *= 0.8
        return shade(base, f)
    return paint(fn)


def velvet(base, gold=True):
    """Tufted velvet: a diamond of buttons pulling the cloth into soft hollows, gold piping round the edge."""
    def fn(x, y):
        if gold and (x in (0, 15) or y in (0, 15)): return GOLD if (x + y) % 2 else GOLD_LO
        u, v = (x + y) % 8, (x - y) % 8
        d = min(u, 8 - u) + min(v, 8 - v)
        f = 0.78 + 0.07 * d
        if (x + y) % 8 == 0 and (x - y) % 8 == 0: return shade(base, 0.45)       # a button
        return shade(base, min(1.2, f))
    return paint(fn)


def carved_rail():
    """The chair's crest rail: dark wood with a carved Greek key, its raised lines gilded."""
    key = ["####.", "...#.", "##.#.", "#..#.", "#####"]
    def fn(x, y):
        if y < 3 or y > 12: return shade((70, 46, 28), 0.9 if y in (0, 15) else 1.05)
        if y in (3, 12): return GOLD_LO
        ch = key[(y - 5) % 5][x % 5] if 5 <= y <= 9 else "."
        return GOLD if ch == "#" else shade((58, 38, 24), 0.95)
    return paint(fn)


def rug():
    """A rug: a deep red field, a rosette in the middle, and round its edge a Greek key on cream."""
    key = ["#####", "#...#", "#.#.#", "#.###", "#...."]
    def fn(x, y):
        edge = min(x, y, 15 - x, 15 - y)
        if edge == 0: return (60, 30, 24)
        if edge <= 3:
            u, v = (x if y < 4 or y > 11 else y), (min(y, 15 - y) if (y < 4 or y > 11) else min(x, 15 - x))
            ch = key[(v - 1) % 5][u % 5] if 1 <= v <= 3 else "."
            return (40, 36, 60) if ch == "#" else (226, 210, 170)
        d = math.hypot(x - 7.5, y - 7.5)
        a = math.atan2(y - 7.5, x - 7.5)
        if d < 3.2 and math.cos(a * 4) > 0.2: return GOLD if d < 2 else (230, 140, 60)
        if d < 1: return GOLD_HI
        return shade((150, 34, 32), 1.0 + 0.06 * math.sin(x * 2.1 + y))
    return paint(fn)


def amphora_body():
    """Terracotta painted in the black-figure way: a band of running figures between key bands."""
    figures = ["................", "..#.....#....#..", ".###...###..###.", "..#.....#....#..", ".#.#...#.#..#.#.", "#...#.#...##...#"]
    def fn(x, y):
        clay = shade((196, 104, 50), 1 + 0.05 * math.sin(x * 1.3 + y * 0.4))
        if y in (3, 4) or y in (11, 12): return (28, 20, 18) if (x + y) % 3 else clay
        if 5 <= y <= 10:
            return (24, 18, 16) if figures[y - 5][x] == "#" else clay
        if y < 3: return shade(clay, 0.9 + 0.03 * y)
        return shade(clay, 1.05 - 0.04 * (y - 13))
    return paint(fn)


def bronze():
    def fn(x, y):
        f = 1.0 + 0.12 * math.sin(x * 0.9 + y * 0.3) + (0.15 if (x * 3 + y) % 11 == 0 else 0)
        c = shade((176, 118, 56), f)
        if (x * 7 + y * 5) % 19 == 0: c = mix(c, (80, 170, 140), 0.5)       # a touch of verdigris
        return c
    return paint(fn)


def flame():
    def fn(x, y):
        d = math.hypot((x - 7.5) * 1.3, y - 10)
        if d < 3: return (255, 250, 220)
        if d < 6: return (255, 200, 90)
        return (240, 120, 40)
    return paint(fn)


def wax():
    return paint(lambda x, y: shade((236, 226, 200), 1.0 - (0.08 if x in (0, 15) else 0) - 0.01 * y))


def scrolls():
    def fn(x, y):
        if y % 5 == 0: return (120, 90, 60)
        return shade((224, 206, 160), 0.92 + 0.08 * math.sin(x * 0.8))
    return paint(fn)


def clay_jar():
    def fn(x, y):
        c = shade((170, 96, 56), 1 + 0.06 * math.sin(x))
        if y in (4, 5): c = (40, 30, 26)
        if y == 10 and x % 3 == 0: c = (230, 200, 150)
        return c
    return paint(fn)


def leather():
    def fn(x, y):
        if x in (0, 15) or y in (0, 15): return GOLD_LO
        if x in (1, 14) or y in (1, 14): return GOLD if (x + y) % 2 else GOLD_LO
        return shade((46, 70, 52), 1 + 0.05 * math.sin(x * 2.3 + y * 1.7))
    return paint(fn)


def paper():
    return paint(lambda x, y: (40, 34, 30) if (y % 3 == 1 and 2 < x < 13 and (x + y) % 5) else (232, 222, 196))


def book():
    return paint(lambda x, y: GOLD if y in (3, 12) else (110, 30, 34) if x < 14 else (230, 220, 190))


def _uv(face, a, b):
    """A face's texture coordinates from where it lies - as the game would work them out - but kept inside the
    texture, so a part that rises above the block (a chair's back) does not reach into its neighbour's picture."""
    x1, y1, z1 = a
    x2, y2, z2 = b
    if face in ("north", "south"): u0, u1, v0, v1 = x1, x2, 16 - y2, 16 - y1
    elif face in ("east", "west"): u0, u1, v0, v1 = z1, z2, 16 - y2, 16 - y1
    else: u0, u1, v0, v1 = x1, x2, z1, z2

    def fit(lo, hi):
        if hi - lo >= 16: return 0, 16
        if lo < 0: return 0, hi - lo
        if hi > 16: return 16 - (hi - lo), 16
        return lo, hi
    u0, u1 = fit(u0, u1)
    v0, v1 = fit(v0, v1)
    return [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def el(a, b, faces, rot=None, emit=None):
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b],
         "faces": {d: {"texture": f"#{t}", "uv": _uv(d, a, b)} for d, t in faces.items()}}
    if rot: e["rotation"] = rot
    if emit: e["light_emission"] = emit
    return e


def al(t, **over):
    f = {d: t for d in ("north", "south", "east", "west", "up", "down")}
    f.update(over)
    return f


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    oak, dark = (112, 76, 46), (66, 44, 28)
    # the woods and cloths, drawn again (the barracks' pieces use the same names)
    wood(oak, 3, knots=1).save(os.path.join(T, "furniture_oak.png"))
    end_grain(oak, 4).save(os.path.join(T, "furniture_oak_end.png"))
    wood(dark, 5, along_x=True, knots=0).save(os.path.join(T, "furniture_dark.png"))
    velvet((150, 40, 36)).save(os.path.join(T, "chair_cushion.png"))
    velvet((46, 70, 140)).save(os.path.join(T, "armchair_cushion.png"))
    carved_rail().save(os.path.join(T, "furniture_rail.png"))
    rug().save(os.path.join(T, "meander_rug.png"))
    amphora_body().save(os.path.join(T, "amphora_body.png"))
    bronze().save(os.path.join(T, "furniture_bronze.png"))
    flame().save(os.path.join(T, "furniture_flame.png"))
    wax().save(os.path.join(T, "furniture_wax.png"))
    scrolls().save(os.path.join(T, "furniture_scrolls.png"))
    clay_jar().save(os.path.join(T, "furniture_jar.png"))
    leather().save(os.path.join(T, "furniture_leather.png"))
    paper().save(os.path.join(T, "furniture_paper.png"))
    book().save(os.path.join(T, "furniture_book.png"))
    tx = {"particle": t("furniture_oak"), "oak": t("furniture_oak"), "end": t("furniture_oak_end"), "dark": t("furniture_dark"),
          "cushion": t("chair_cushion"), "blue": t("armchair_cushion"), "rail": t("furniture_rail"), "rug": t("meander_rug"),
          "clay": t("amphora_body"), "bronze": t("furniture_bronze"), "flame": t("furniture_flame"), "wax": t("furniture_wax"),
          "scrolls": t("furniture_scrolls"), "jar": t("furniture_jar"), "leather": t("furniture_leather"), "paper": t("furniture_paper"),
          "book": t("furniture_book"), "front": t("dresser_front"), "front_r": t("dresser_front_r")}

    def leg(x, z, top, w=2):
        # a turned leg: thicker at the knee, a collar, slim below
        return [el((x, 0, z), (x + w, top * 0.55, z + w), al("dark")),
                el((x - 0.3, top * 0.55, z - 0.3), (x + w + 0.3, top * 0.62, z + w + 0.3), al("oak")),
                el((x - 0.15, top * 0.62, z - 0.15), (x + w + 0.15, top, z + w + 0.15), al("dark"))]

    models = {}
    # ---- the chair, carved: turned legs, a velvet seat, two posts and a gilded crest rail with a key on it
    chair = leg(2, 2, 8) + leg(12, 2, 8) + leg(2, 12, 8) + leg(12, 12, 8)
    chair += [el((1.5, 8, 1.5), (14.5, 9.5, 14.5), al("oak", up="end")), el((2.5, 9.5, 2.5), (13.5, 11, 12), al("cushion")),
              el((2, 9.5, 12.5), (4, 21, 14.5), al("dark")), el((12, 9.5, 12.5), (14, 21, 14.5), al("dark")),
              el((2.2, 21, 12.7), (3.8, 22, 14.3), al("oak")), el((12.2, 21, 12.7), (13.8, 22, 14.3), al("oak")),
              el((4, 12.5, 13), (12, 13.5, 14), al("oak")), el((7.5, 13.5, 13), (8.5, 17, 14), al("oak")),
              el((3, 17, 12.75), (13, 21, 14.25), al("dark", north="rail", south="rail"))]
    models["chair"] = chair
    # ---- the stool: three legs splayed out under a round seat with a cushion
    stool = []
    for (x, z) in ((3.5, 3.5), (11, 3.5), (3.5, 11), (11, 11)):
        stool.append(el((x, 0, z), (x + 1.5, 9, z + 1.5), al("dark")))
    stool += [el((4.5, 3.5, 4), (11.5, 4.5, 4.75), al("oak")), el((4.5, 3.5, 11.25), (11.5, 4.5, 12), al("oak")),
              el((4, 3.5, 4.5), (4.75, 4.5, 11.5), al("oak")), el((11.25, 3.5, 4.5), (12, 4.5, 11.5), al("oak")),
              el((3, 9, 4), (13, 10.5, 12), al("oak", up="end")), el((4, 9, 3), (12, 10.5, 13), al("oak", up="end")),
              el((4.5, 10.5, 4.5), (11.5, 11.5, 11.5), al("cushion"))]
    models["stool"] = stool
    # ---- the armchair: upholstered in blue, arms with rolled caps, a high back
    arm = leg(1, 1, 7) + leg(13, 1, 7) + leg(1, 13, 7) + leg(13, 13, 7)
    arm += [el((1, 5.5, 1), (15, 7.5, 15), al("oak")), el((2, 7.5, 1.5), (14, 10, 13), al("blue")),
            el((1, 7.5, 13), (15, 21, 15), al("oak", north="dark")), el((2.5, 10, 12), (13.5, 20, 13), al("blue")),
            el((1, 7.5, 1), (2.5, 13, 13), al("oak")), el((13.5, 7.5, 1), (15, 13, 13), al("oak")),
            el((0.5, 13, 0.5), (3, 14.5, 13.5), al("dark")), el((13, 13, 0.5), (15.5, 14.5, 13.5), al("dark")),
            el((1, 20.5, 12.5), (15, 22, 15.5), al("dark", north="rail", south="rail"))]
    models["armchair"] = arm
    # ---- the nightstand: a drawer and a cupboard, a book left on it
    models["nightstand"] = [el((2.5, 0, 2.5), (4, 1.5, 4), al("dark")), el((12, 0, 2.5), (13.5, 1.5, 4), al("dark")),
                            el((2.5, 0, 12), (4, 1.5, 13.5), al("dark")), el((12, 0, 12), (13.5, 1.5, 13.5), al("dark")),
                            el((2, 1.5, 2), (14, 12, 14), al("oak", north="front")),
                            el((1.5, 12, 1.5), (14.5, 13, 14.5), al("dark")),
                            el((4, 13, 5), (10, 14.2, 10), al("book")), el((4.5, 14.2, 5.5), (9, 15, 9.5), al("book"), rot={"origin": [7, 14.5, 7.5], "axis": "y", "angle": 22.5})]
    # ---- the writing desk: a leather-topped board, drawers along its back, papers, an inkwell and a candle
    models["writing_desk"] = (leg(1, 1, 12) + leg(13, 1, 12) +
                              [el((1, 0, 9), (15, 12, 15), al("oak", north="front")),
                               el((0, 12, 0), (16, 13.5, 16), al("dark", up="leather")),
                               el((3, 13.5, 3.5), (9, 13.7, 8.5), al("paper")),
                               el((10, 13.5, 9.5), (11.5, 15, 11), al("jar")),
                               el((12, 13.5, 11), (13, 17.5, 12), al("wax")),
                               el((11.5, 13.5, 10.5), (13.5, 14, 12.5), al("bronze")),
                               el((12.2, 17.5, 11.2), (12.8, 19, 11.8), al("flame"), emit=15)])
    # ---- the standing lamp: a bronze tripod, a tall stem, a bowl of oil and its flame
    lamp = []
    for ang in (0.0, 120.0, -120.0):
        lamp.append(el((7.25, 0, 3), (8.75, 1.5, 8), al("bronze"), rot={"origin": [8, 0, 8], "axis": "y", "angle": ang}))
    lamp += [el((6.5, 1, 6.5), (9.5, 2.5, 9.5), al("bronze")), el((7.4, 2.5, 7.4), (8.6, 13, 8.6), al("bronze")),
             el((6.8, 8, 6.8), (9.2, 9, 9.2), al("bronze")),
             el((5, 13, 5), (11, 14.5, 11), al("bronze")), el((4.5, 14.5, 4.5), (11.5, 15.5, 11.5), al("bronze", up="dark")),
             el((6.8, 15.5, 6.8), (9.2, 18, 9.2), al("flame"), emit=15)]
    models["standing_lamp"] = lamp
    # ---- the candelabrum: a branched bronze stand with three candles
    can = [el((5, 0, 6), (11, 1, 10), al("bronze")), el((7.4, 1, 7.4), (8.6, 11, 8.6), al("bronze")),
           el((3.5, 10, 7.4), (12.5, 11, 8.6), al("bronze"))]
    for x, top in ((4, 15), (8, 16.5), (12, 15)):
        can += [el((x - 1, 11, 7), (x + 1, 11.8, 9), al("bronze")), el((x - 0.5, 11.8, 7.5), (x + 0.5, top, 8.5), al("wax")),
                el((x - 0.35, top, 7.65), (x + 0.35, top + 1.4, 8.35), al("flame"), emit=15)]
    models["candelabrum"] = can
    # ---- the wall shelf: a board on two brackets against the wall, jars, scrolls and a stood-up book on it
    models["wall_shelf"] = [el((0, 5, 10), (16, 6.5, 16), al("oak", up="oak")),
                            el((1, 2, 14), (2.5, 5, 16), al("dark")), el((13.5, 2, 14), (15, 5, 16), al("dark")),
                            el((1.5, 6.5, 11), (4.5, 10.5, 14), al("jar")), el((2, 10.5, 11.5), (4, 11.5, 13.5), al("dark")),
                            el((5.5, 6.5, 11.5), (11, 7.8, 13), al("scrolls")), el((6.5, 7.8, 11.8), (10.5, 9, 12.8), al("scrolls")),
                            el((12, 6.5, 11), (14.5, 13, 14.8), al("book"))]
    # ---- the rug: low on the floor, its border of a Greek key
    models["rug"] = [el((0, 0, 0), (16, 0.75, 16), al("rug", north="dark", south="dark", east="dark", west="dark"))]
    # ---- the amphora: a painted jar on a foot, with two handles
    models["amphora"] = [el((6, 0, 6), (10, 1, 10), al("clay")), el((5, 1, 5), (11, 3, 11), al("clay")),
                         el((4, 3, 4), (12, 10, 12), al("clay")), el((5, 10, 5), (11, 12, 11), al("clay")),
                         el((6.5, 12, 6.5), (9.5, 14, 9.5), al("clay")), el((6, 14, 6), (10, 15, 10), al("clay")),
                         el((2.8, 8, 7.4), (4, 13, 8.6), al("clay")), el((12, 8, 7.4), (13.2, 13, 8.6), al("clay")),
                         el((2.8, 13, 7.4), (6.5, 14, 8.6), al("clay")), el((9.5, 13, 7.4), (13.2, 14, 8.6), al("clay"))]
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for name, els in models.items():
        wj(os.path.join(M, f"{name}.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": els})
        wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": {f"facing={f}": {"model": f"{MOD}:block/{name}", "y": r} for f, r in rot.items()}})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})
    for name in PIECES:
        wj(os.path.join(data, f"loot_table/blocks/{name}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # recipes: wood and wool and the like, made at a crafting table
    R = os.path.join(data, "recipe")
    recipes = {
        "stool": (["PWP", "S S"], {"P": "#minecraft:planks", "W": "#minecraft:wool", "S": "minecraft:stick"}, 2),
        "armchair": (["P  ", "WWW", "PSP"], {"P": "#minecraft:planks", "W": "minecraft:blue_wool", "S": "minecraft:stick"}, 1),
        "nightstand": (["SSS", "PCP", "P P"], {"S": "#minecraft:wooden_slabs", "P": "#minecraft:planks", "C": "minecraft:chest"}, 1),
        "writing_desk": (["SBS", "P P"], {"S": "#minecraft:wooden_slabs", "B": "minecraft:book", "P": "#minecraft:planks"}, 1),
        "standing_lamp": (["T", "C", "C"], {"T": "minecraft:torch", "C": "minecraft:copper_ingot"}, 1),
        "candelabrum": (["CCC", " I ", " I "], {"C": "minecraft:candle", "I": "minecraft:copper_ingot"}, 1),
        "wall_shelf": (["SSS", "D D"], {"S": "#minecraft:wooden_slabs", "D": "minecraft:stick"}, 2),
        "rug": (["RYR"], {"R": "minecraft:red_carpet", "Y": "minecraft:yellow_carpet"}, 3),
        "amphora": ([" B ", "B B", " B "], {"B": "minecraft:brick"}, 1),
    }
    for name, (pattern, key, count) in recipes.items():
        wj(os.path.join(R, f"{name}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "pattern": pattern, "key": key,
                                             "result": {"id": f"{MOD}:{name}", "count": count}})


def lang():
    return {f"block.{MOD}.{k}": v for k, v in NAMES.items()}
