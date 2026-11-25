"""The Orb of Plenty's three greater ranks - the Greater, the Exalted and the Divine Orb of Plenty. Each is the orb
before it remade: in a new metal, with another ring, then a crown, then a halo of gems; each works faster than the
last, and the last two pay less ichor. Each can be made two ways - from what the gods of the towers drop, or from what
the Overworld (and the Nether) gives - and each is harder to make than the one before."""
import copy
import json
import os

from PIL import Image

import divine_models
from pixel_kit import ramp

MOD = "realms_of_chaos"
IDS = {1: "cornucopia", 2: "greater_orb_of_plenty", 3: "exalted_orb_of_plenty", 4: "divine_orb_of_plenty"}
NAMES = {2: "Greater Orb of Plenty", 3: "Exalted Orb of Plenty", 4: "Divine Orb of Plenty"}
# each rank's metal: celestial bronze, orichalcum, adamantine
METALS = {2: (206, 150, 86), 3: (222, 112, 70), 4: (176, 214, 236)}
CORES = ("none", "storm", "tide", "shade")
GEMS = {"none": (230, 220, 255), "storm": (255, 230, 120), "tide": (120, 236, 240), "shade": (190, 130, 255)}


def metal_tex(base, seed):
    """The orb's gold, recast in another metal: the same grain, in that metal's ramp."""
    r = ramp(base)
    n = divine_models.noise(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            img.putpixel((x, y), (r[5] if v > 0.93 else r[4] if v > 0.6 else r[3] if v > 0.12 else r[2]) + (255,))
    return img


def gem_tex(col):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            f = 1.3 - 0.6 * (y / 15)
            c = tuple(max(0, min(255, int(v * f))) for v in col)
            if x + y < 6:
                c = tuple(int(v + (255 - v) * 0.6) for v in c)
            img.putpixel((x, y), c + (255,))
    return img


def rank_model(rank, core):
    m = copy.deepcopy(divine_models.orb_model(core))
    m["textures"]["gold"] = f"{MOD}:block/orb_metal_{rank}"
    m["textures"]["particle"] = f"{MOD}:block/orb_metal_{rank}"
    el = divine_models.el
    els = m["elements"]
    c = [8, 9, 8]
    # a third ring, level, round the orb's middle
    for a, b in (((2.2, 8.7, 2.2), (13.8, 9.3, 2.8)), ((2.2, 8.7, 13.2), (13.8, 9.3, 13.8)), ((2.2, 8.7, 2.8), (2.8, 9.3, 13.2)), ((13.2, 8.7, 2.8), (13.8, 9.3, 13.2))):
        els.append(el(a, b, "gold", rot={"origin": c, "axis": "y", "angle": 45.0}))
    if rank >= 3:
        # a crown: four points rising from a band over the orb's top
        els.append(el((6, 12.6, 6), (10, 13.2, 10), "gold"))
        for ang in (0.0, 90.0, 180.0, -90.0):
            r = {"origin": [8, 13, 8], "axis": "y", "angle": ang if ang != 180.0 else 0.0}
            if ang == 180.0:
                els.append(el((7.5, 13.2, 9.3), (8.5, 15.2, 10.0), "gold"))
            else:
                els.append(el((7.5, 13.2, 6.0), (8.5, 15.2, 6.7), "gold", rot=r))
        m["textures"]["gem"] = f"{MOD}:block/orb_gem_{core}"
        els.append(el((7.4, 13.2, 7.4), (8.6, 14.4, 8.6), "gem", emit=15 if core != "none" else 8,
                      rot={"origin": [8, 13.8, 8], "axis": "y", "angle": 45.0}))
    if rank >= 4:
        # a halo of four gems about the orb, and a broader foot
        for gx, gz in ((8, 1.6), (8, 14.4), (1.6, 8), (14.4, 8)):
            els.append(el((gx - 0.7, 10.3, gz - 0.7), (gx + 0.7, 11.7, gz + 0.7), "gem", emit=15 if core != "none" else 8,
                          rot={"origin": [gx, 11, gz], "axis": "y", "angle": 45.0}))
        els.append(el((3, 0, 3), (13, 0.6, 13), "gold"))
    return m


def shaped(R, name, pattern, key, result):
    with open(os.path.join(R, name + ".json"), "w") as f:
        json.dump({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key, "result": {"id": result}}, f, indent=2)


def recipes(data):
    R = os.path.join(data, "recipe")
    m = lambda line, tier: f"{MOD}:material_{line}_{tier}"
    e = lambda line: f"{MOD}:essence_{line}"
    orb = lambda rank: f"{MOD}:{IDS[rank]}"
    # Rank II. From the towers: what the gods of floors 1-5 of all three towers drop. From the world: the long roads'
    # raw finds - storm glass, abyssal pearls, raw stygian iron - and blocks of diamond.
    shaped(R, "greater_orb_of_plenty_from_towers", ["ZPH", "GOG", "ZPH"],
           {"Z": m("zeus", 1), "P": m("poseidon", 1), "H": m("hades", 1), "G": "minecraft:gold_block", "O": orb(1)}, orb(2))
    shaped(R, "greater_orb_of_plenty_from_the_world", ["SPS", "DOD", "IFI"],
           {"S": f"{MOD}:storm_glass", "P": f"{MOD}:abyssal_pearl", "D": "minecraft:diamond_block", "I": f"{MOD}:raw_stygian_iron",
            "F": f"{MOD}:fulgurite", "O": orb(1)}, orb(2))
    # Rank III. From the towers: floors 6-10, and adamantine. From the world: the three god-metals worked, two
    # nether stars and adamantine.
    shaped(R, "exalted_orb_of_plenty_from_towers", ["ZPH", "AOA", "ZPH"],
           {"Z": m("zeus", 2), "P": m("poseidon", 2), "H": m("hades", 2), "A": f"{MOD}:adamantine_block", "O": orb(2)}, orb(3))
    shaped(R, "exalted_orb_of_plenty_from_the_world", ["BNR", "AOA", "BSR"],
           {"B": f"{MOD}:celestial_bronze_ingot", "R": f"{MOD}:orichalcum_ingot", "S": f"{MOD}:stygian_iron_ingot", "N": "minecraft:nether_star",
            "A": f"{MOD}:adamantine_block", "O": orb(2)}, orb(3))
    # Rank IV. From the towers: floors 11-14 and each summit's essence, with two nether stars. From the world: four
    # nether stars and four blocks of adamantine.
    shaped(R, "divine_orb_of_plenty_from_towers", ["ZPH", "1O2", "N3N"],
           {"Z": m("zeus", 3), "P": m("poseidon", 3), "H": m("hades", 3), "1": e("zeus"), "2": e("poseidon"), "3": e("hades"),
            "N": "minecraft:nether_star", "O": orb(3)}, orb(4))
    shaped(R, "divine_orb_of_plenty_from_the_world", ["NAN", "AOA", "NAN"],
           {"N": "minecraft:nether_star", "A": f"{MOD}:adamantine_block", "O": orb(3)}, orb(4))


def add_values(path, values):
    with open(path) as f:
        tag = json.load(f)
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    with open(path, "w") as f:
        json.dump(tag, f, indent=2)


def lang():
    out = {f"block.{MOD}.{IDS[r]}": n for r, n in NAMES.items()}
    out.update({
        f"block.{MOD}.orb_rank.rank": "Rank %s of 4",
        f"block.{MOD}.orb_rank.speed": "Works %sx as fast as the first",
        f"block.{MOD}.orb_rank.ichor": "Pays %s%% of the ichor",
        f"block.{MOD}.orb_rank.yield": "Brings forth %s more of its core's resource",
        f"screen.{MOD}.orb.rank": "Rank %s",
        f"screen.{MOD}.orb.rank.tip": "%sx as fast, %s%% of the ichor",
    })
    return out


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    for rank, col in METALS.items():
        metal_tex(col, 11 + rank).save(os.path.join(T, f"orb_metal_{rank}.png"))
    for core, col in GEMS.items():
        gem_tex(col).save(os.path.join(T, f"orb_gem_{core}.png"))
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for rank in (2, 3, 4):
        oid = IDS[rank]
        variants = {}
        for core in CORES:
            wj(os.path.join(M, f"{oid}_{core}.json"), rank_model(rank, core))
            for f, r in rot.items():
                variants[f"core={core},facing={f}"] = {"model": f"{MOD}:block/{oid}_{core}", "y": r}
        wj(os.path.join(M, f"{oid}.json"), {"parent": f"{MOD}:block/{oid}_storm"})
        wj(os.path.join(assets, f"blockstates/{oid}.json"), {"variants": variants})
        wj(os.path.join(assets, f"items/{oid}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{oid}_storm"}})
    # an orb drops itself; what it held spills out of it when it breaks (its core among it)
    for rank, oid in IDS.items():
        wj(os.path.join(data, f"loot_table/blocks/{oid}.json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{oid}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    recipes(data)
    MC = os.path.join(os.path.dirname(data), "minecraft")
    more = [f"{MOD}:{IDS[r]}" for r in (2, 3, 4)]
    add_values(os.path.join(MC, "tags/block/needs_diamond_tool.json"), more)
    add_values(os.path.join(MC, "tags/block/mineable/pickaxe.json"), more)
    add_values(os.path.join(data, "tags/item/unreplicable.json"), more)
