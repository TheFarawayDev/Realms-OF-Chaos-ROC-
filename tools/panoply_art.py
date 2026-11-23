"""The panoply's pictures: an icon for each piece of armour, and what it looks like worn.

Worn armour is a texture wrapped round the boxes of a player model - head, body, arms, legs - so a piece is painted
onto the faces of its own box and nothing else. Each piece has an equipment asset of its own, since each is its own
material: a helm of dark iron with a slit to see through, a bronze helm with a nose-guard, a gilded cuirass with a
face on it, a lion's pelt, a red girdle, steel greaves, winged sandals, black treads.
"""
import os
import random
from PIL import Image

# id -> (kind, base, trim, name, what it does)
PIECES = {
    "helm_of_hades": ("helmet", (58, 54, 82), (165, 90, 214), "Helm of Hades", "Unseen while you sneak"),
    "helm_of_athena": ("helmet", (196, 146, 74), (240, 208, 110), "Helm of Athena", "Night is as day to you, and what means you harm is lit"),
    "aegis_cuirass": ("chest", (226, 188, 84), (126, 235, 255), "Aegis Cuirass", "Wards every blow a little. Hurt badly, it gives you a second wind"),
    "nemean_pelt": ("chest", (198, 152, 86), (120, 78, 40), "Nemean Pelt", "The hide no blade went through: hard to wound, hard to move"),
    "girdle_of_hippolyta": ("legs", (168, 52, 52), (240, 208, 110), "Girdle of Hippolyta", "Strength in every blow"),
    "greaves_of_achilles": ("legs", (132, 152, 184), (240, 208, 110), "Greaves of Achilles", "Swift-footed"),
    "talaria": ("boots", (156, 112, 70), (245, 245, 250), "Talaria", "The winged sandals: leap high, land soft"),
    "styx_treads": ("boots", (44, 44, 58), (60, 200, 190), "Treads of the Styx", "Fire and deep water cannot harm you"),
}

ICONS = {
    "helmet": ["................", "................", "....oooooooo....", "...obbbbbbbbo...", "..obhhbbbbbbbo..", "..obhbbbbbbbso..", "..obbttttttbso..",
               "..obbo....obso..", "..obbo....obso..", "..obso....osso..", "..oso......oso..", "..ooo......ooo..", "................", "................",
               "................", "................"],
    "chest": ["................", "..ooo......ooo..", ".obbbo....obbbo.", ".obhbboooobbsbo.", ".obhbbbbbbbbsbo.", ".ooobbttttbbooo.", "...obbbttbbbo...",
              "...obhbttbbso...", "...obhbbbbbso...", "...obhbbbbbso...", "...obbbbbbsso...", "...obbbbbbsso...", "....oooooooo....", "................",
              "................", "................"],
    "legs": ["................", "................", "...oooooooooo...", "...otttttttto...", "...obbhbbbbso...", "...obhbbbbbso...", "...obhbooobso...",
             "...obbbo.obso...", "...obbbo.obso...", "...obbbo.obso...", "...obsso.osso...", "...obsso.osso...", "...ooooo.oooo...", "................",
             "................", "................"],
    "boots": ["................", "................", "................", "................", "..oooo....oooo..", "..obbo....obbo..", "..obho....obho..",
              "..obbo....obbo..", "..otto....otto..", ".oobbo...oobbo..", ".obbbso..obbbso.", ".obhbso..obhbso.", ".oooooo..oooooo.", "................",
              "................", "................"],
}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def icon(kind, base, trim, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pal = {"o": shade(base, 0.32), "b": base, "h": shade(base, 1.3), "s": shade(base, 0.72), "t": trim}
    for y, row in enumerate(ICONS[kind]):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y + 1), pal[ch] + (255,))
    if name == "talaria":
        # a wing at each ankle
        for x, y in ((0, 6), (1, 6), (0, 7), (1, 7), (1, 8), (15, 6), (14, 6), (15, 7), (14, 7), (14, 8), (8, 7), (9, 7), (8, 8)):
            if img.getpixel((x, y))[3] == 0:
                img.putpixel((x, y), trim + (255,))
    if name == "helm_of_athena":
        # a crest along the crown
        for x in range(5, 11):
            img.putpixel((x, 2), (200, 50, 50, 255))
            img.putpixel((x, 1), (150, 34, 34, 255))
    if name == "aegis_cuirass":
        for x, y in ((6, 9), (9, 9), (7, 11), (8, 11)):
            img.putpixel((x, y), shade(base, 0.4) + (255,))
    return img


def faces(u, v, w, h, d):
    """The six faces of a model box whose texture starts at (u, v): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def paint(px, box, base, trim, rnd, rows=None, band=None):
    """Paints the faces of a box: the base colour with a grain, darker at the edges of each face, a band of trim on
    the row asked for. {@code rows}: only these rows of the side faces (boots cover the foot of a leg, not all of it)."""
    for name, (x0, y0, w, h) in box.items():
        for y in range(h):
            if rows is not None and name not in ("top", "bottom") and y not in rows:
                continue
            if rows is not None and name == "top":
                continue
            for x in range(w):
                edge = x in (0, w - 1) or y in (0, h - 1)
                c = shade(base, (0.78 if edge else 1.0) * rnd.choice((0.94, 1.0, 1.0, 1.06)))
                if band is not None and name not in ("top", "bottom") and y == band:
                    c = trim
                px[x0 + x, y0 + y] = c + (255,)


def worn(name, kind, base, trim):
    """The two layers a piece is worn as: the main one, and the one leggings use."""
    rnd = random.Random(name)
    main, legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    mp, lp = main.load(), legs.load()
    head, body, arm, leg = faces(0, 0, 8, 8, 8), faces(16, 16, 8, 12, 4), faces(40, 16, 4, 12, 4), faces(0, 16, 4, 12, 4)
    if kind == "helmet":
        paint(mp, head, base, trim, rnd, band=1)
        fx, fy = head["front"][0], head["front"][1]
        if name == "helm_of_hades":
            for x in range(1, 7):                       # a slit to see through, and nothing else
                mp[fx + x, fy + 4] = (0, 0, 0, 0)
        else:
            for x, y in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4), (3, 6), (4, 6), (3, 7), (4, 7), (2, 7), (5, 7)):
                mp[fx + x, fy + y] = (0, 0, 0, 0)       # eyes either side of a nose-guard, and an open mouth
            tx, ty = head["top"][0], head["top"][1]
            for y in range(8):                          # the crest
                mp[tx + 3, ty + y] = (200, 50, 50, 255)
                mp[tx + 4, ty + y] = (150, 34, 34, 255)
    elif kind == "chest":
        paint(mp, body, base, trim, rnd, band=10)
        paint(mp, arm, base, trim, rnd, rows=range(0, 5 if name == "nemean_pelt" else 9), band=0 if name != "nemean_pelt" else None)
        fx, fy = body["front"][0], body["front"][1]
        if name == "aegis_cuirass":
            for x, y in ((2, 3), (5, 3), (2, 4), (5, 4), (3, 6), (4, 6), (2, 7), (5, 7), (3, 2), (4, 2)):
                mp[fx + x, fy + y] = shade(base, 0.42) + (255,)       # a face to turn an enemy to stone
            for x in range(1, 7):
                mp[fx + x, fy + 1] = trim + (255,)
        else:
            for x, y in ((1, 2), (6, 2), (2, 5), (5, 5), (3, 8), (4, 9), (1, 9), (6, 7)):
                mp[fx + x, fy + y] = trim + (255,)                    # the tufts of a lion's mane
    elif kind == "legs":
        paint(lp, leg, base, trim, rnd, band=8 if name == "greaves_of_achilles" else None)
        lower = {k: v for k, v in body.items() if k not in ("top", "bottom")}
        paint(lp, lower, base, trim, rnd, rows=range(7, 12), band=8)
    else:
        paint(mp, leg, base, trim, rnd, rows=range(7, 12), band=7)
        if name == "talaria":
            for face in ("right", "left"):
                x0, y0, w, h = leg[face]
                for x, y in ((1, 5), (2, 5), (2, 6), (3, 6), (1, 6)):
                    mp[x0 + x, y0 + y] = trim + (255,)                # a wing at the ankle
    return main, legs


def gen_panoply(assets, mod, wj):
    for name, (kind, base, trim, _, _) in PIECES.items():
        os.makedirs(os.path.join(assets, "textures/item"), exist_ok=True)
        icon(kind, base, trim, name).save(os.path.join(assets, f"textures/item/{name}.png"))
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{mod}:item/{name}"}})
        wj(os.path.join(assets, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{mod}:item/{name}"}})
        main, legs = worn(name, kind, base, trim)
        for layer, img in (("humanoid", main), ("humanoid_leggings", legs)):
            folder = os.path.join(assets, f"textures/entity/equipment/{layer}")
            os.makedirs(folder, exist_ok=True)
            img.save(os.path.join(folder, f"{name}.png"))
        wj(os.path.join(assets, f"equipment/{name}.json"),
           {"layers": {"humanoid": [{"texture": f"{mod}:{name}"}], "humanoid_leggings": [{"texture": f"{mod}:{name}"}]}})


def lang():
    out = {}
    for name, (_, _, _, title, lore) in PIECES.items():
        out[f"item.realms_of_chaos.{name}"] = title
        out[f"item.realms_of_chaos.{name}.lore"] = lore
    return out
