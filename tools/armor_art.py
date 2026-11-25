"""The four armour sets as they are worn, drawn plate by plate and in two layers, the way the game layers leather
armour: a base of shaped, shaded metal, and over it an overlay carrying each set's own motif.

Every face of the armour model is laid out from a small map: O outline, S shadow, D dark, M mid, L light, W
highlight; T, U, V the trim (mid, light, dark); R a rivet; '.' left open. Plates overlap: a lit edge over a shadow
line where one lame lies on the next, so the armour reads as layered metal rather than paint.
"""
import os

from PIL import Image

from pixel_kit import ramp

MOD = "realms_of_chaos"

# set -> (metal, trim, motif colour)
SETS = {"adamantine": ((150, 200, 216), (156, 116, 226), (214, 176, 255)),
        "celestial_bronze": ((212, 150, 62), (252, 236, 160), (255, 250, 210)),
        "orichalcum": ((208, 104, 62), (86, 206, 196), (150, 246, 236)),
        "stygian_iron": ((64, 64, 80), (86, 214, 206), (140, 255, 244))}

# ---- the helm: a Corinthian helm, a crest from brow to nape
HEAD = {
    "front": ["UUUUUUUU", "LWLLLLMD", "LOOLMOOD", "L..LM..D", "LMOLMOMD", "LMM..MMD", "DML..MDS", "SDDSSDDS"],
    "right": ["UUUUUUUU", "LWLLLLMD", "LLMMMMMD", "LMMRMMMD", "LMMMMMMD", "LMMMMMMD", "DMMMMMMS", "SDDDDDDS"],
    "left":  ["UUUUUUUU", "LWLLLLMD", "LLMMMMMD", "LMMMRMMD", "LMMMMMMD", "LMMMMMMD", "DMMMMMMS", "SDDDDDDS"],
    "back":  ["UUUUUUUU", "LLLTULMD", "LMMTUMMD", "LMMTUMMD", "LMMMMMMD", "SDDDDDDS", "LMMMMMMD", "SDDDDDDS"],
    "top":   ["DLLTULLD", "LWLTUWMD", "LLMTUMMD", "LMMTUMMD", "LMMTUMMD", "LMMTUMMD", "DMMTUMDS", "SDDTVDDS"],
    "bottom": ["SSSSSSSS"] * 8,
}
# ---- the breastplate: a collar, the chest and its muscles, a belt set with stones, leather strips at the hem
BODY = {
    "front": ["UTTUUTTU", "LWLLLLMD", "LWLMMLMD", "LLMDDMMD", "LMDMMDMD", "LMMLLMMD", "LMDMMDMD", "LMMLLMMD",
              "DMDMMDMS", "UUUUUUUU", "VTRTTRTV", "T.TVT.TV"],
    "back":  ["UTTUUTTU", "LLLLLLMD", "LMMMMMMD", "LMMDLMMD", "LMMDLMMD", "LMMDLMMD", "LMMDLMMD", "LMMDLMMD",
              "DMMDLMMS", "UUUUUUUU", "VTTTTTTV", "T.TVT.TV"],
    "right": ["UUUU", "LWMD", "LMMD", "LMMD", "LMMD", "LMMD", "LMMD", "LMMD", "DMMS", "UUUU", "VTTV", "T.TV"],
    "left":  ["UUUU", "LWMD", "LMMD", "LMMD", "LMMD", "LMMD", "LMMD", "LMMD", "DMMS", "UUUU", "VTTV", "T.TV"],
    "top":   ["UUUUUUUU", "LWLLLLMD", "LMMMMMMD", "SDDDDDDS"],
    "bottom": ["SSSSSSSS"] * 4,
}
# ---- shoulder guards of two lames, then the bare arm, then a bracer
ARM_SIDE = ["UUUU", "LWLD", "LMMD", "SDDS", "LLMD", "SDDS", "....", "....", "....", "UUUU", "LMMD", "SDDS"]
ARM = {"front": ARM_SIDE, "back": ARM_SIDE, "right": ARM_SIDE, "left": ARM_SIDE,
       "top": ["UUUU", "LWLD", "LMMD", "SDDS"], "bottom": ["....", "....", "....", "...."]}
# ---- greaves: a cuff, a ridge down the shin, a strap at the ankle, the sole
BOOT_FRONT = [".", ".", ".", ".", ".", ".", "UUUU", "LWLD", "LWMD", "TUTV", "LMMD", "SSSS"]
BOOT_SIDE = [".", ".", ".", ".", ".", ".", "UUUU", "LLMD", "LMMD", "TUTV", "LMMD", "SSSS"]
BOOTS = {"front": BOOT_FRONT, "back": BOOT_SIDE, "right": BOOT_SIDE, "left": BOOT_SIDE,
         "bottom": ["SSSS", "SDDS", "SDDS", "SSSS"]}
# ---- leggings: a plated thigh to a knee guard; at the hip a belt and a skirt of strips
LEG_FRONT = ["UUUU", "LWLD", "LMMD", "LMMD", "SDDS", "LWUD", "LUUD", "SDDS", ".", ".", ".", "."]
LEG_SIDE = ["UUUU", "LWLD", "LMMD", "LMMD", "SDDS", "LMMD", "LMMD", "SDDS", ".", ".", ".", "."]
LEGS = {"front": LEG_FRONT, "back": LEG_SIDE, "right": LEG_SIDE, "left": LEG_SIDE}
WAIST = [".", ".", ".", ".", ".", ".", ".", "UUUUUUUU", "LMDLMDLM", "LMDLMDLM", "LMDLMDLM", "SDSSDSSD"]
WAIST_SIDE = [".", ".", ".", ".", ".", ".", ".", "UUUU", "LMDL", "LMDL", "LMDL", "SDSS"]

# ---- each set's motif, on the overlay: (face, x, y) cells
MOTIFS = {
    # violet stones: one at the brow, one at the heart, one on each shoulder
    "adamantine": [("head.front", 3, 0), ("head.front", 4, 0), ("body.front", 3, 3), ("body.front", 4, 3), ("body.front", 3, 4), ("body.front", 4, 4),
                   ("arm.front", 1, 1), ("arm.front", 2, 1)],
    # a thunderbolt down the breast, a spark at the brow
    "celestial_bronze": [("body.front", 4, 2), ("body.front", 3, 3), ("body.front", 4, 3), ("body.front", 3, 4), ("body.front", 4, 5),
                         ("body.front", 3, 6), ("body.front", 2, 7), ("head.front", 3, 1), ("head.front", 4, 1)],
    # a breaking wave across the breast and the brow
    "orichalcum": [("body.front", 1, 5), ("body.front", 2, 4), ("body.front", 3, 5), ("body.front", 4, 4), ("body.front", 5, 5), ("body.front", 6, 4),
                   ("head.front", 1, 1), ("head.front", 2, 1), ("head.front", 5, 1), ("head.front", 6, 1)],
    # the light of souls: the eyes, a line down the breast, the knees
    "stygian_iron": [("head.front", 1, 3), ("head.front", 6, 3), ("body.front", 3, 2), ("body.front", 4, 2), ("body.front", 3, 4), ("body.front", 4, 4),
                     ("body.front", 3, 6), ("body.front", 4, 6), ("leg.front", 1, 6), ("leg.front", 2, 6)],
}


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def palette(metal, trim):
    """The metal's ramp, its deepest shadow softened halfway toward the next tone so the plates do not turn to stripes."""
    t, tr = ramp(metal), ramp(trim)
    soft = tuple((a + b) // 2 for a, b in zip(t[1], t[2]))
    deep = tuple((a + b) // 2 for a, b in zip(t[0], t[1]))
    return {"O": deep, "S": soft, "D": t[2], "M": t[3], "L": t[4], "W": t[5], "T": tr[3], "U": tr[4], "V": tr[2], "R": tr[5]}


def lay(img, box, rows, pal, mirror=False):
    x0, y0, w, h = box
    px = img.load()
    for y in range(h):
        row = rows[y] if y < len(rows) else "."
        for x in range(w):
            ch = row[(w - 1 - x) if mirror else x] if len(row) > 1 else row
            if ch in pal:
                px[x0 + x, y0 + y] = tuple(pal[ch]) + (255,)


def worn(set_id):
    metal, trim, motif = SETS[set_id]
    pal = palette(metal, trim)
    base, legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    over, over_legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0)), Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    head, body, arm, leg = faces(0, 0, 8, 8, 8), faces(16, 16, 8, 12, 4), faces(40, 16, 4, 12, 4), faces(0, 16, 4, 12, 4)
    for name, box in head.items(): lay(base, box, HEAD[name], pal)
    for name, box in body.items(): lay(base, box, BODY[name], pal)
    for name, box in arm.items(): lay(base, box, ARM[name], pal)
    for name, box in leg.items():
        if name in BOOTS: lay(base, box, BOOTS[name], pal)
    for name, box in leg.items():
        if name in LEGS: lay(legs, box, LEGS[name], pal)
    for name, box in body.items():
        if name in ("front", "back"): lay(legs, box, WAIST, pal)
        elif name in ("right", "left"): lay(legs, box, WAIST_SIDE, pal)
    # the motif, on its own layer: a bright core and a softer rim of the motif's colour
    parts = {"head": head, "body": body, "arm": arm, "leg": leg}
    hi = tuple(motif)
    for spec, x, y in MOTIFS[set_id]:
        part, face = spec.split(".")
        x0, y0, w, h = parts[part][face]
        target = over_legs if part == "leg" else over
        target.putpixel((x0 + x, y0 + y), hi + (255,))
    return base, over, legs, over_legs


def gen(assets, wj):
    for set_id in SETS:
        base, over, legs, over_legs = worn(set_id)
        for layer, b, o in (("humanoid", base, over), ("humanoid_leggings", legs, over_legs)):
            folder = os.path.join(assets, f"textures/entity/equipment/{layer}")
            os.makedirs(folder, exist_ok=True)
            b.save(os.path.join(folder, f"{set_id}.png"))
            o.save(os.path.join(folder, f"{set_id}_overlay.png"))
        wj(os.path.join(assets, f"equipment/{set_id}.json"), {"layers": {
            "humanoid": [{"texture": f"{MOD}:{set_id}"}, {"texture": f"{MOD}:{set_id}_overlay"}],
            "humanoid_leggings": [{"texture": f"{MOD}:{set_id}"}, {"texture": f"{MOD}:{set_id}_overlay"}]}})
