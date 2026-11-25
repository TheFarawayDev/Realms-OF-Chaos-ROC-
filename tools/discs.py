"""A music disc for each of the mod's tracks: its picture (black grooves, a label in the colour of the place its track
belongs to, a mark of that place), its model, its jukebox song and its name."""
import math
import os

from PIL import Image

import music

MOD = "realms_of_chaos"
# label colour, and a mark of the place drawn on the label (3x3, '#' lit)
DISCS = {
    "hub": ((214, 178, 92), "The Hub", [".#.", "###", ".#."]),
    "hermitage": ((120, 196, 120), "The Hermitage", ["##.", "#..", "..."]),
    "olympus": ((242, 201, 76), "Olympus", [".#.", "#.#", "..."]),
    "olympus_peak": ((255, 236, 150), "The Storm on the Peak", ["#..", ".#.", "..#"]),
    "abyss": ((47, 181, 200), "The Abyss", ["#.#", ".#.", "#.#"]),
    "abyss_trench": ((30, 90, 140), "The Trench", ["...", "###", "..."]),
    "underworld": ((142, 68, 173), "The Underworld", ["#.#", "###", ".#."]),
    "tartarus": ((120, 30, 40), "Tartarus", ["###", ".#.", ".#."]),
    "passage": ((200, 120, 60), "The Passages", ["###", "#.#", "###"]),
    "trial": ((214, 60, 52), "A God's Trial", ["#.#", ".#.", "#.#"]),
    "summit": ((255, 200, 60), "The King on the Summit", ["#.#", "###", "###"]),
    "titan": ((170, 90, 50), "The Fallen Titan", [".#.", "###", "#.#"]),
    "citadel": ((150, 150, 166), "The Citadels", ["#.#", "###", "###"]),
    "gate": ((165, 90, 214), "The Ruined Gate", [".#.", "#.#", "#.#"]),
}


def disc_image(colour, mark):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.6:
                continue
            if d > 6.9:
                c = (20, 20, 24)                                  # the rim
            elif d > 2.9:
                groove = int(d * 2) % 2
                c = (40, 40, 46) if groove else (28, 28, 34)
                # light caught across the grooves
                ang = math.atan2(y - 7.5, x - 7.5)
                if abs(math.sin(ang - 0.8)) < 0.22 and d > 3.5:
                    c = (86, 86, 98)
            else:
                c = colour if d > 0.8 else (16, 16, 20)
                if d > 2.2:
                    c = tuple(int(v * 0.7) for v in colour)
            img.putpixel((x, y), c + (255,))
    # the mark on the label
    for my, row in enumerate(mark):
        for mx, ch in enumerate(row):
            if ch == "#":
                x, y = 6 + mx, 6 + my
                if (x, y) != (7, 7) or True:
                    img.putpixel((x, y), tuple(min(255, int(v * 1.35 + 30)) for v in colour) + (255,))
    img.putpixel((7, 7), (12, 12, 14, 255))
    img.putpixel((8, 8), (12, 12, 14, 255))
    return img


def gen(assets, data, wj):
    T = os.path.join(assets, "textures/item")
    lengths = music.lengths(assets)
    for i, (track, (colour, title, mark)) in enumerate(DISCS.items()):
        iid = f"music_disc_{track}"
        disc_image(colour, mark).save(os.path.join(T, f"{iid}.png"))
        wj(os.path.join(assets, f"models/item/{iid}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{iid}"}})
        wj(os.path.join(assets, f"items/{iid}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{iid}"}})
        wj(os.path.join(data, f"jukebox_song/{track}.json"), {"sound_event": f"{MOD}:music.{track}",
                                                              "description": {"translate": f"jukebox_song.{MOD}.{track}"},
                                                              "length_in_seconds": round(lengths.get(track, 120.0), 1), "comparator_output": i + 1})


def lang():
    out = {}
    for track, (colour, title, mark) in DISCS.items():
        out[f"item.{MOD}.music_disc_{track}"] = "Music Disc"
        out[f"jukebox_song.{MOD}.{track}"] = f"TheFarawayDev - {title}"
    return out
