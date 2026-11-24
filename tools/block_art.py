"""Block art that needs more than one texture per block: forges that join into one furnace, a bounty board whose
boards each carry different notices. Run from generate.py; writes textures, models and blockstates."""
import json
import math
import os
import random

from PIL import Image

MOD = "realms_of_chaos"


def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def save_strip(frames, path, mcmeta):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    strip.save(path)
    if mcmeta:
        wj(path + ".mcmeta", mcmeta)


# ---- the forge ---------------------------------------------------------------------------------------------------
# A face's mask says which of its edges join another forge: 1 up, 2 down, 4 left, 8 right (as seen from outside).

FRAMES = 8


def forge_side(mask, f, noise):
    up, down, left, right = mask & 1, mask & 2, mask & 4, mask & 8
    body, rim, dark = (62, 56, 80), (104, 96, 124), (26, 22, 34)
    k = 0.5 + 0.5 * math.sin(f / FRAMES * math.tau)
    img = Image.new("RGBA", (16, 16))
    top = 0 if up else 4
    bot = 15 if down else 12
    lo = 0 if left else 3
    hi = 15 if right else 12
    for y in range(16):
        for x in range(16):
            c = shade(body, noise[y][x])
            # the frame, on every edge that does not join another forge: a dark outer line and a lit inner one
            edge = None
            if not up and y <= 1: edge = y
            if not down and y >= 14: edge = 15 - y
            if not left and x <= 1: edge = x if edge is None else min(edge, x)
            if not right and x >= 14: edge = 15 - x if edge is None else min(edge, 15 - x)
            if edge is not None:
                c = shade(body, 0.55) if edge == 0 else shade(rim, noise[y][x])
            # iron bands above and below the mouth
            if (not up and y == 3) or (not down and y == 13):
                if lo <= x <= hi or (left and x < 3) or (right and x > 12):
                    c = shade((40, 36, 52), 1.0)
            # rivets on the bands
            if ((not up and y == 3) or (not down and y == 13)) and x % 4 == 2:
                c = (150, 140, 170)
            # the mouth: fire behind a grate
            if lo <= x <= hi and top <= y <= bot:
                dists = []
                if not left: dists.append(x - lo)
                if not right: dists.append(hi - x)
                if not up: dists.append(y - top)
                if not down: dists.append(bot - y)
                d = min(dists) if dists else 6
                heat = min(1.0, (d + 0.6) / 3.2)
                # flames lick upward; coals glow along the bottom of the mouth
                flame = 0.5 + 0.5 * math.sin(x * math.tau / 16 * 3 + f / FRAMES * math.tau * 2 + y * 0.6)
                coals = 1.0 if (not down and bot - y <= 1) else 0.0
                t = heat * (0.55 + 0.25 * flame + 0.2 * k) + coals * 0.3
                c = mix((34, 14, 10), (255, 150, 52), t)
                if t > 0.85:
                    c = mix(c, (255, 236, 170), (t - 0.85) * 4)
                if coals and (x + y + f) % 3 == 0:
                    c = mix(c, (255, 250, 210), 0.5 * k)
                # the grate: upright bars, every fourth column, catching the light on one side
                if x % 4 == 1:
                    c = mix(dark, c, 0.12)
                elif x % 4 == 2:
                    c = mix((70, 60, 70), c, 0.45)
            img.putpixel((x, y), c + (255,))
    return img


def forge_top(mask, f, noise):
    # for the top, 1 north, 2 south, 4 west, 8 east
    n, s, w, e = mask & 1, mask & 2, mask & 4, mask & 8
    body = (70, 66, 86)
    cols = [(245, 184, 46), (43, 183, 204), (165, 90, 214)]
    k = 0.5 + 0.5 * math.sin(f / FRAMES * math.tau)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(body, noise[x][y])
            edge = None
            if not n and y <= 1: edge = y
            if not s and y >= 14: edge = 15 - y
            if not w and x <= 1: edge = x if edge is None else min(edge, x)
            if not e and x >= 14: edge = 15 - x if edge is None else min(edge, 15 - x)
            if edge is not None:
                c = shade(body, 0.55 if edge == 0 else 1.25)
            # vents across the plate, glowing
            if y % 4 == 1 and 4 <= (x + 2) % 16 <= 13:
                c = mix((40, 20, 16), (255, 140, 50), 0.4 + 0.4 * k)
            img.putpixel((x, y), c + (255,))
    if mask == 0:
        # a forge on its own keeps the ring of the three gods on its top
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                if 3.4 <= r < 4.6:
                    a = (math.atan2(y - 7.5, x - 7.5) + math.pi) / math.tau
                    col = cols[int(a * 3) % 3]
                    img.putpixel((x, y), mix(shade(col, 0.6), col, k) + (255,))
                elif r < 1.6:
                    img.putpixel((x, y), mix((255, 150, 60), (255, 255, 255), 0.5 * k) + (255,))
    return img


# which edge of each face looks at which neighbour: (up, down, left, right) for a side face, seen from outside
SIDE_EDGES = {
    "north": ("up", "down", "east", "west"),
    "south": ("up", "down", "west", "east"),
    "east": ("up", "down", "south", "north"),
    "west": ("up", "down", "north", "south"),
}
TOP_EDGES = ("north", "south", "west", "east")


def gen_forge(assets):
    T = os.path.join(assets, "textures/block")
    rnd = random.Random(99)
    noise = [[1 + rnd.uniform(-0.07, 0.07) for _ in range(16)] for _ in range(16)]
    meta = {"animation": {"frametime": 3, "interpolate": True}}
    for mask in range(16):
        save_strip([forge_side(mask, f, noise) for f in range(FRAMES)], os.path.join(T, f"chaos_forge_side_{mask}.png"), meta)
        save_strip([forge_top(mask, f, noise) for f in range(FRAMES)], os.path.join(T, f"chaos_forge_top_{mask}.png"), meta)
    # the old single textures, kept for whatever still names them
    save_strip([forge_side(0, f, noise) for f in range(FRAMES)], os.path.join(T, "chaos_forge_side.png"), meta)
    save_strip([forge_top(0, f, noise) for f in range(FRAMES)], os.path.join(T, "chaos_forge_top.png"), meta)
    dirs = ["down", "east", "north", "south", "up", "west"]
    variants = {}
    for bits in range(64):
        joined = {d: bool(bits >> i & 1) for i, d in enumerate(dirs)}
        textures = {"particle": f"{MOD}:block/chaos_forge_side_0", "bottom": f"{MOD}:block/altar_base"}
        faces = {}
        for face, (u, dn, l, r) in SIDE_EDGES.items():
            m = (1 if joined[u] else 0) | (2 if joined[dn] else 0) | (4 if joined[l] else 0) | (8 if joined[r] else 0)
            textures[face] = f"{MOD}:block/chaos_forge_side_{m}"
            faces[face] = {"uv": [0, 0, 16, 16], "texture": f"#{face}", "cullface": face}
        tm = sum(1 << i for i, d in enumerate(TOP_EDGES) if joined[d])
        textures["up"] = f"{MOD}:block/chaos_forge_top_{tm}"
        faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#up", "cullface": "up"}
        faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}
        name = "chaos_forge_" + "".join(d[0] if joined[d] else "_" for d in dirs)
        wj(os.path.join(assets, f"models/block/{name}.json"),
           {"parent": "minecraft:block/block", "textures": textures, "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
        key = ",".join(f"{d}={'true' if joined[d] else 'false'}" for d in dirs)
        variants[key] = {"model": f"{MOD}:block/{name}"}
    wj(os.path.join(assets, "blockstates/chaos_forge.json"), {"variants": variants})
    # in the hand: a forge on its own
    wj(os.path.join(assets, "models/block/chaos_forge.json"), {"parent": f"{MOD}:block/chaos_forge_" + "_" * len(dirs)})


# ---- the bounty board ------------------------------------------------------------------------------------------

def board_wood(seed):
    """Boards of dark oak laid across, four pixels a board, their seams lined up from block to block."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        plank = y // 4
        tone = 1.0 + (0.05 if plank % 2 else -0.03)
        for x in range(16):
            grain = 0.06 * math.sin((x + plank * 5) * 0.9 + math.sin(y * 1.7) * 0.8)
            c = shade((112, 76, 46), tone + grain + rnd.uniform(-0.035, 0.035))
            if y % 4 == 3:
                c = shade((70, 46, 28), 1.0)
            img.putpixel((x, y), c + (255,))
    # a nail or two where the boards meet the beam behind
    for x, y in ((1, 1), (14, 9)):
        img.putpixel((x, y), (60, 60, 66, 255))
    return img


PAPER, PAPER2, INK, RED, WAX = (236, 226, 200), (222, 206, 170), (84, 70, 58), (196, 52, 46), (170, 36, 40)


def note(img, x0, y0, w, h, paper=PAPER, lines=True, pin=RED, torn=False):
    for y in range(h):
        for x in range(w):
            if torn and y == h - 1 and (x % 3 == 1):
                continue
            c = paper
            if x == w - 1 or y == h - 1:
                c = shade(paper, 0.86)                    # the sheet's shadowed edge
            if lines and 1 <= x <= w - 3 and y >= 2 and y % 2 == 0 and y < h - 1:
                c = INK if x < w - 2 - (y * 7 % 3) else c
            img.putpixel((x0 + x, y0 + y), c + (255,))
    # its shadow on the wood
    for y in range(1, h + 1):
        px = (x0 + w, y0 + y)
        if px[0] < 16 and px[1] < 16:
            img.putpixel(px, shade(img.getpixel(px), 0.7) + (255,))
    if pin:
        img.putpixel((x0 + w // 2, y0), pin + (255,))


def gen_board(assets):
    T = os.path.join(assets, "textures/block")
    looks = []
    # 0: a wanted poster - a face drawn in ink over a line of text, a red stamp in its corner
    img = board_wood(1)
    note(img, 3, 2, 10, 12, lines=False)
    for y in range(4, 9):
        for x in range(6, 10):
            if (x - 7.5) ** 2 / 4 + (y - 6) ** 2 / 6 <= 1:
                img.putpixel((x, y), INK + (255,))
    for x in range(5, 11):
        img.putpixel((x, 10), INK + (255,))
    for x in range(5, 9):
        img.putpixel((x, 12), shade(INK, 1.3) + (255,))
    img.putpixel((11, 12), RED + (255,)); img.putpixel((10, 12), RED + (255,)); img.putpixel((11, 11), RED + (255,))
    looks.append(img)
    # 1: a map, with a red cross on it, beside a small note
    img = board_wood(2)
    for y in range(2, 12):
        for x in range(1, 10):
            c = mix((214, 196, 150), (182, 196, 140), 0.5 + 0.5 * math.sin(x * 0.9 + y * 0.6))
            if (x * 3 + y * 5) % 11 == 0:
                c = (120, 150, 190)                          # a river
            if x in (1, 9) or y in (2, 11):
                c = shade((214, 196, 150), 0.8)
            img.putpixel((x, y), c + (255,))
    img.putpixel((6, 6), RED + (255,)); img.putpixel((7, 7), RED + (255,)); img.putpixel((6, 7), RED + (255,)); img.putpixel((7, 6), RED + (255,))
    img.putpixel((5, 2), RED + (255,))
    note(img, 11, 5, 4, 6, paper=PAPER2)
    looks.append(img)
    # 2: a scroll held open, sealed in red wax
    img = board_wood(3)
    for y in range(3, 13):
        for x in range(3, 13):
            c = PAPER if 4 <= y <= 11 else shade(PAPER2, 0.92)
            if y in (3, 12):
                c = shade(PAPER2, 0.8 if x % 2 else 0.9)          # the rolled ends
            elif 5 <= y <= 9 and y % 2 == 1 and 4 <= x <= 11:
                c = INK if (x + y) % 5 else PAPER
            img.putpixel((x, y), c + (255,))
    for (x, y) in ((7, 10), (8, 10), (7, 11), (8, 11)):
        img.putpixel((x, y), WAX + (255,))
    img.putpixel((8, 12), WAX + (255,))
    looks.append(img)
    # 3: three small notes, pinned at angles
    img = board_wood(4)
    note(img, 1, 1, 6, 6)
    note(img, 9, 2, 6, 5, paper=PAPER2)
    note(img, 4, 9, 7, 6)
    looks.append(img)
    # 4: a torn notice under a god's sign, struck through
    img = board_wood(5)
    note(img, 2, 5, 9, 9, torn=True)
    for (x, y) in ((12, 2), (13, 3), (12, 4), (13, 5), (12, 6)):
        img.putpixel((x, y), (240, 200, 70, 255))                  # a bolt chalked on the wood
    for x in range(3, 10):
        img.putpixel((x, 9), RED + (255,))
    looks.append(img)
    # 5: bare wood but for a note on a string and the holes of old pins
    img = board_wood(6)
    note(img, 8, 6, 6, 7)
    for (x, y) in ((3, 3), (5, 11), (2, 8), (12, 3)):
        img.putpixel((x, y), (54, 36, 24, 255))
    for t in range(6):
        img.putpixel((3 + t, 3 + t // 2), (200, 190, 170, 255))
    looks.append(img)
    top = Image.new("RGBA", (16, 16))
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            top.putpixel((x, y), shade((96, 66, 40), (0.88 if x % 4 == 3 else 1.0) * (1 + rnd.uniform(-0.05, 0.05))) + (255,))
    top.save(os.path.join(T, "bounty_board_top.png"))
    weights = [3, 3, 3, 4, 2, 3]
    variants = []
    for n, img in enumerate(looks):
        img.save(os.path.join(T, f"bounty_board_side_{n}.png"))
        wj(os.path.join(assets, f"models/block/bounty_board_{n}.json"),
           {"parent": "minecraft:block/cube", "textures": {"particle": f"{MOD}:block/bounty_board_side_{n}",
                                                            "north": f"{MOD}:block/bounty_board_side_{n}", "south": f"{MOD}:block/bounty_board_side_{n}",
                                                            "east": f"{MOD}:block/bounty_board_side_{(n + 2) % 6}",
                                                            "west": f"{MOD}:block/bounty_board_side_{(n + 4) % 6}",
                                                            "up": f"{MOD}:block/bounty_board_top", "down": f"{MOD}:block/bounty_board_top"}})
        variants.append({"model": f"{MOD}:block/bounty_board_{n}", "weight": weights[n]})
    looks[3].save(os.path.join(T, "bounty_board_side.png"))
    wj(os.path.join(assets, "blockstates/bounty_board.json"), {"variants": {"": variants}})
    wj(os.path.join(assets, "models/block/bounty_board.json"), {"parent": f"{MOD}:block/bounty_board_3"})



# ---- blocks built of parts, each with its own stone ---------------------------------------------------------------

def parts_model(parts, particle):
    """A model of stacked boxes, each box with its own textures: (box, side, top, bottom[, rotation])."""
    textures, elements = {"particle": particle}, []
    for n, part in enumerate(parts):
        (x1, y1, z1, x2, y2, z2), side, top, bottom = part[:4]
        for k, t in (("s", side), ("t", top), ("b", bottom)):
            textures[f"{k}{n}"] = t
        faces = {
            "north": {"uv": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "texture": f"#s{n}"},
            "south": {"uv": [x1, 16 - y2, x2, 16 - y1], "texture": f"#s{n}"},
            "west": {"uv": [z1, 16 - y2, z2, 16 - y1], "texture": f"#s{n}"},
            "east": {"uv": [16 - z2, 16 - y2, 16 - z1, 16 - y1], "texture": f"#s{n}"},
            "up": {"uv": [x1, z1, x2, z2], "texture": f"#t{n}"},
            "down": {"uv": [x1, 16 - z2, x2, 16 - z1], "texture": f"#b{n}"},
        }
        el = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}
        if len(part) > 4 and part[4]:
            el["rotation"] = part[4]
        elements.append(el)
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def grain(seed, amount=0.06):
    rnd = random.Random(seed)
    return [[1 + rnd.uniform(-amount, amount) for _ in range(16)] for _ in range(16)]


def paint(fn, seed=1, amount=0.05):
    g = grain(seed, amount)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = fn(x, y, g[y][x])
            img.putpixel((x, y), (c if len(c) == 4 else c + (255,)))
    return img


GOLD, GOLD_HI, GOLD_LO = (232, 186, 66), (255, 232, 140), (150, 104, 30)

# the three towers' stones for their altars: base, light, dark, accent
ALTAR_STONE = {
    "zeus": ((228, 226, 218), (250, 249, 244), (170, 166, 160), GOLD),
    "poseidon": ((86, 160, 150), (140, 210, 196), (40, 96, 92), (236, 200, 96)),
    "hades": ((56, 50, 66), (96, 86, 112), (26, 22, 32), (196, 150, 230)),
}


def moulded(base, light, dark, accent, kind):
    """The faces of a pedestal's parts. kind: plinth, shaft (fluted), band (gilded), capital (carved)."""
    def fn(x, y, g):
        c = shade(base, g)
        if kind == "plinth":
            if y in (0, 8): c = shade(light, g)
            if y in (7, 15): c = shade(dark, g)
            if y == 11 and x % 4 == 1: c = accent
        elif kind == "shaft":
            f = x % 4
            c = shade(dark if f == 0 else light if f == 1 else base, g)
            if y in (0, 15): c = shade(base, g * 0.9)
        elif kind == "band":
            c = GOLD_HI if y % 16 < 8 and (x + y) % 5 == 0 else GOLD if (x // 2 + y // 2) % 2 == 0 else GOLD_LO
            c = mix(c, accent, 0.25)
        elif kind == "capital":
            if y in (0, 15): c = shade(light, g)
            if 4 <= y <= 11:
                d = (x - 4) % 8
                if d in (0, 7): c = shade(dark, g)
                if (d - 3.5) ** 2 + (y - 7.5) ** 2 <= 4: c = shade(accent, 0.9 if (d + y) % 3 else 1.1)
            if y == 13: c = shade(dark, g)
        elif kind == "plain":
            pass
        return c
    return paint(fn, sum(ord(ch) * (i + 1) for i, ch in enumerate(kind)))


def gen_pedestals(assets):
    T = os.path.join(assets, "textures/block")
    for line, (base, light, dark, accent) in ALTAR_STONE.items():
        for kind in ("plinth", "shaft", "band", "capital", "plain"):
            moulded(base, light, dark, accent, kind).save(os.path.join(T, f"altar_{line}_{kind}.png"))
        p = lambda k: f"{MOD}:block/altar_{line}_{k}"
        top = f"{MOD}:block/altar_{line}_top"
        parts = [
            ((0, 0, 0, 16, 2, 16), p("plinth"), p("plain"), f"{MOD}:block/altar_base"),
            ((1, 2, 1, 15, 4, 15), p("plinth"), p("plain"), p("plain")),
            ((4, 4, 4, 12, 11, 12), p("shaft"), p("plain"), p("plain")),
            ((3, 11, 3, 13, 12, 13), p("band"), p("band"), p("band")),
            ((1, 12, 1, 15, 14, 15), p("capital"), p("plain"), p("plain")),
            ((2, 14, 2, 14, 16, 14), p("capital"), top, p("plain")),
        ]
        wj(os.path.join(assets, f"models/block/altar_{line}.json"), parts_model(parts, p("plinth")))
    # the Ruined Gate's pedestal: the same build in old, mossy stone with gold in its joints
    base, light, dark = (128, 128, 124), (164, 164, 158), (80, 80, 78)
    for kind in ("plinth", "shaft", "band", "capital", "plain"):
        img = moulded(base, light, dark, GOLD, kind)
        rnd = random.Random(kind)
        for _ in range(10 if kind in ("plinth", "plain") else 5):
            x, y = rnd.randrange(16), rnd.randrange(16)
            img.putpixel((x, y), (86, 120, 60, 255))                  # moss in the joints
        img.save(os.path.join(T, f"gate_pedestal_{kind}.png"))
    p = lambda k: f"{MOD}:block/gate_pedestal_{k}"
    parts = [
        ((1, 0, 1, 15, 2, 15), p("plinth"), p("plain"), f"{MOD}:block/altar_base"),
        ((3, 2, 3, 13, 3, 13), p("plinth"), p("plain"), p("plain")),
        ((5, 3, 5, 11, 10, 11), p("shaft"), p("plain"), p("plain")),
        ((4, 10, 4, 12, 11, 12), p("band"), p("band"), p("band")),
        ((2, 11, 2, 14, 13, 14), p("capital"), p("plain"), p("plain")),
        ((3, 13, 3, 13, 14, 13), p("capital"), f"{MOD}:block/gate_pedestal_top", p("plain")),
    ]
    wj(os.path.join(assets, "models/block/gate_pedestal.json"), parts_model(parts, p("plinth")))


# ---- the beam room: a mirror of glass on a stand, a lens that throws the beam, a lens that catches it --------------

STONE = (104, 104, 116)


def gen_beam(assets):
    T = os.path.join(assets, "textures/block")
    stone = lambda x, y, g: shade(STONE, g * (0.8 if x in (0, 15) or y in (0, 15) else 1.0))
    paint(stone, 3).save(os.path.join(T, "beam_stone.png"))
    paint(lambda x, y, g: shade(GOLD if (x + y) % 4 else GOLD_HI, g)).save(os.path.join(T, "beam_gold.png"))
    paint(lambda x, y, g: shade((70, 74, 92), g * (1.2 if x % 4 == 1 else 1.0))).save(os.path.join(T, "beam_iron.png"))

    def pane(lit):
        def fn(x, y, g):
            if x in (0, 15) or y in (0, 15):
                return GOLD_LO if (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)) else GOLD
            if x in (1, 14) or y in (1, 14):
                return GOLD_HI if (x + y) % 3 == 0 else GOLD
            d = x + (15 - y) if not lit else (15 - x) + (15 - y)
            c = mix((70, 96, 130), (200, 226, 246), min(1.0, max(0.0, (y - 1) / 15 * 0.6 + 0.3)))
            if abs(d - 15) <= 1: c = (250, 253, 255)
            elif abs(d - 15) == 2 or abs(d - 21) == 0: c = mix(c, (255, 255, 255), 0.5)
            return c + (255,)
        return paint(fn, 11)
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        pane(lit).save(os.path.join(T, f"beam_mirror_pane{suffix}.png"))
        rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45.0 if lit else -45.0, "rescale": True}
        s = lambda k: f"{MOD}:block/beam_{k}"
        parts = [
            ((2, 0, 2, 14, 2, 14), s("stone"), s("stone"), s("stone")),
            ((6, 2, 6, 10, 3, 10), s("gold"), s("gold"), s("gold")),
            ((7, 3, 7, 9, 4, 9), s("iron"), s("iron"), s("iron")),
            ((1, 4, 7.5, 15, 15, 8.5), f"{MOD}:block/beam_mirror_pane{suffix}", s("gold"), s("gold"), rot),
        ]
        model = parts_model(parts, f"{MOD}:block/beam_mirror_pane{suffix}")
        # (the pane's thin edges are gold; its faces are the glass)
        el = model["elements"][-1]
        for face in ("east", "west"):
            el["faces"][face]["texture"] = "#t3"
        wj(os.path.join(assets, f"models/block/beam_mirror{suffix}.json"), model)
    # lenses: the emitter's a sun in a gold ring, always alight; the receiver's a ring that kindles when struck
    for name, lit_states in (("beam_emitter", [True]), ("beam_receiver", [False, True])):
        for lit in lit_states:
            core = (255, 236, 140) if name == "beam_emitter" else ((255, 246, 190) if lit else (96, 90, 110))
            glow = (255, 190, 70) if name == "beam_emitter" else ((255, 214, 110) if lit else (70, 66, 84))

            def lens(x, y, g, core=core, glow=glow):
                r = math.hypot(x - 7.5, y - 7.5)
                if r > 7.2: return shade(GOLD_LO, g)
                if r > 6.0: return shade(GOLD if (x + y) % 3 else GOLD_HI, g)
                return mix(glow, core, max(0.0, 1 - r / 5.5)) + (255,)
            tag = name + ("_lit" if lit and name == "beam_receiver" else "")
            paint(lens, 5).save(os.path.join(T, f"{tag}_lens.png"))
            s = lambda k: f"{MOD}:block/beam_{k}"
            L = f"{MOD}:block/{tag}_lens"
            parts = [
                ((1, 0, 1, 15, 3, 15), s("stone"), s("stone"), s("stone")),
                ((4, 3, 4, 12, 7, 12), s("iron"), s("iron"), s("iron")),
                ((2, 7, 2, 14, 9, 14), s("gold"), s("gold"), s("gold")),
                ((3, 9, 3, 13, 15, 13), L, L, L),
            ]
            wj(os.path.join(assets, f"models/block/{tag}.json"), parts_model(parts, L))
    # the receiver's state file names lit and unlit already; the emitter only ever looks one way
    wj(os.path.join(assets, "blockstates/beam_emitter.json"),
       {"variants": {"lit=false": {"model": f"{MOD}:block/beam_emitter"}, "lit=true": {"model": f"{MOD}:block/beam_emitter"}}})


# ---- building stone of the realms -----------------------------------------------------------------------------------

DECOR = ["olympian_marble", "olympian_marble_pillar", "gilded_frieze", "abyssal_tile", "underworld_brick", "chaos_lamp"]


def gen_decor(assets, data):
    T = os.path.join(assets, "textures/block")
    MARBLE = (236, 233, 226)

    def marble(x, y, g):
        v = math.sin((x + y * 0.6) * 0.55 + math.sin(y * 0.8 + x * 0.3) * 1.6)
        c = shade(MARBLE, g * (1.0 - 0.1 * max(0.0, v - 0.6) * 4))
        if abs(v - 0.95) < 0.05: c = mix(c, GOLD, 0.55)
        return c
    paint(marble, 21, 0.03).save(os.path.join(T, "olympian_marble.png"))
    paint(lambda x, y, g: shade(MARBLE, g * ({0: 0.8, 1: 1.06, 2: 1.0, 3: 0.94}[x % 4]))).save(os.path.join(T, "olympian_marble_pillar.png"))

    def pillar_top(x, y, g):
        r = math.hypot(x - 7.5, y - 7.5)
        c = shade(MARBLE, g)
        if 5.6 < r < 6.6: c = shade(MARBLE, 0.82)
        if r < 2.0: c = GOLD
        return c
    paint(pillar_top, 22, 0.03).save(os.path.join(T, "olympian_marble_pillar_top.png"))
    key = ["################", "#..............#", "#.############.#", "#.#..........#.#", "#.#.########.#.#", "#.#.#......#.#.#",
           "#.#.#.####.#.#.#", "#...#.#..#...#.#"]

    def frieze(x, y, g):
        if y in (0, 15): return shade(GOLD_LO, g)
        if y in (1, 14): return shade(MARBLE, g * 0.86)
        if y in (3, 12): return shade(GOLD, g)
        if 4 <= y <= 11:
            # a Greek key, four pixels to a turn, running on from block to block
            px, py = x % 8, y - 4
            pattern = ["#######.", "......#.", ".####.#.", ".#..#.#.", ".#.##.#.", ".#....#.", ".######.", "........"]
            return shade(GOLD if pattern[py][px] == "#" else (54, 46, 40), g)
        return shade(MARBLE, g)
    paint(frieze, 23, 0.03).save(os.path.join(T, "gilded_frieze.png"))

    def abyssal(x, y, g):
        tx, ty = x % 8, y % 8
        c = shade((34, 86, 92), g)
        if tx == 0 or ty == 0: c = shade((18, 44, 50), g)
        elif tx == 1 or ty == 1: c = shade((70, 140, 140), g)
        w = math.sin((tx - 1) * 0.9) * 1.2 + 4
        if abs(ty - w) < 0.6: c = shade((120, 210, 200), g)
        return c
    paint(abyssal, 24).save(os.path.join(T, "abyssal_tile.png"))

    def underworld(x, y, g):
        row = y // 4
        off = 4 if row % 2 else 0
        bx = (x + off) % 8
        c = shade((52, 44, 54), g)
        if y % 4 == 3 or bx == 7:
            c = (255, 120, 40) if (x * 7 + y * 3) % 9 == 0 else (60, 22, 16)    # embers in the mortar
        elif y % 4 == 0 or bx == 0:
            c = shade((74, 64, 78), g)
        return c
    paint(underworld, 25).save(os.path.join(T, "underworld_brick.png"))

    def lamp(x, y, g):
        if x in (0, 15) or y in (0, 15): return GOLD_LO
        if x in (1, 14) or y in (1, 14): return GOLD
        r = math.hypot(x - 7.5, y - 7.5)
        c = mix((190, 140, 255), (255, 250, 236), max(0.0, 1 - r / 6))
        for (cx, cy, col) in ((5, 5, (255, 220, 110)), (10, 6, (120, 230, 240)), (7, 10, (210, 150, 255))):
            if abs(x - cx) + abs(y - cy) <= 1: c = mix(c, col, 0.7)
        return c
    paint(lamp, 26).save(os.path.join(T, "chaos_lamp.png"))
    for b in DECOR:
        if b == "olympian_marble_pillar":
            model = {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/{b}_top", "side": f"{MOD}:block/{b}"}}
        else:
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}}
        wj(os.path.join(assets, f"models/block/{b}.json"), model)
        wj(os.path.join(assets, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        wj(os.path.join(assets, f"items/{b}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{b}"}})
    # recipes, so they can be built with outside the realms too
    recipes = {
        "olympian_marble": ({"C": "minecraft:calcite", "Q": "minecraft:quartz_block"}, ["CQ", "QC"], 4),
        "olympian_marble_pillar": ({"M": f"{MOD}:olympian_marble"}, ["M", "M"], 2),
        "gilded_frieze": ({"M": f"{MOD}:olympian_marble", "G": "minecraft:gold_nugget"}, ["GGG", "MMM"], 3),
        "abyssal_tile": ({"P": "minecraft:prismarine_bricks", "D": "minecraft:dark_prismarine"}, ["PD", "DP"], 4),
        "underworld_brick": ({"N": "minecraft:nether_bricks", "M": "minecraft:magma_block"}, ["NN", "NM"], 4),
        "chaos_lamp": ({"G": "minecraft:glowstone", "A": "minecraft:amethyst_shard", "O": "minecraft:gold_ingot"}, [" O ", "AGA", " O "], 2),
    }
    for rid, (key_, pattern, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})


def run(assets, data=None):
    gen_forge(assets)
    gen_board(assets)
    gen_pedestals(assets)
    gen_beam(assets)
    if data:
        gen_decor(assets, data)
        gen_hi_decor(assets, data)
        gen_traps(assets, data)
        gen_tower_stone(assets, data)
        gen_room_furnishings(assets, data)


# ---- high-resolution building stone: 64x64, with connecting panels ---------------------------------------------------

H = 16


def vnoise(seed, cells=8):
    """Smooth value noise that tiles across a 64-pixel block: cells x cells random values, wrapped, blended."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def f(x, y):
        gx, gy = x / H * cells, y / H * cells
        x0, y0 = int(math.floor(gx)), int(math.floor(gy))
        tx, ty = gx - x0, gy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0 % cells][x0 % cells]
        b = grid[y0 % cells][(x0 + 1) % cells]
        c = grid[(y0 + 1) % cells][x0 % cells]
        d = grid[(y0 + 1) % cells][(x0 + 1) % cells]
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return f


def fbm(seed):
    n = [vnoise(seed + i, 4 * 2 ** i) for i in range(3)]
    return lambda x, y: (n[0](x, y) * 0.55 + n[1](x, y) * 0.3 + n[2](x, y) * 0.15)


def paint_hi(fn):
    img = Image.new("RGBA", (H, H))
    for y in range(H):
        for x in range(H):
            c = fn(x, y)
            img.putpixel((x, y), (c if len(c) == 4 else tuple(c) + (255,)))
    return img


def quant(c, step=6):
    """Rounds a colour onto a coarse ramp, so the texture reads as painted pixels, not a photograph."""
    return tuple(int(round(v / step) * step) for v in c[:3])


# the surfaces

def marble_fn(seed, base=(236, 232, 224), vein=(186, 180, 176), gold_vein=True):
    n = fbm(seed)
    def f(x, y):
        v = n(x, y)
        flow = math.sin((x * 0.11 + y * 0.07) * math.tau / 4 + v * 7.5)
        c = shade(base, 0.95 + 0.08 * v)
        if abs(flow) < 0.07: c = mix(c, vein, 0.75)
        elif abs(flow) < 0.16: c = mix(c, vein, 0.3)
        if gold_vein and abs(flow - 0.72) < 0.04 and v > 0.5: c = mix(c, GOLD, 0.8)
        return quant(c, 4)
    return f


def prismarine_fn(seed):
    n = fbm(seed)
    def f(x, y):
        v = n(x, y)
        c = mix((44, 112, 116), (96, 176, 168), v)
        w = math.sin(x * math.tau / 32 + math.sin(y * math.tau / 64) * 2) * 3 + (y % 16) - 8
        if abs(w) < 0.8: c = mix(c, (170, 236, 222), 0.6)
        return quant(c, 6)
    return f


def blackstone_fn(seed):
    n = fbm(seed)
    def f(x, y):
        v = n(x, y)
        c = mix((30, 26, 36), (70, 62, 80), v)
        if v > 0.72 and (x * 7 + y * 3) % 13 == 0: c = mix(c, (255, 120, 50), 0.7)       # embers caught in the stone
        return quant(c, 5)
    return f


def framed(inner, mask, frame, frame_hi, frame_lo, band, width=3):
    """A face of a connecting panel: the inner surface, and a frame on every edge that does not meet another
    panel (mask bits: 1 up, 2 down, 4 left, 8 right). The frame is bevelled - light on its top and left, dark on its
    bottom and right - with a band of metal let into it."""
    up, down, left, right = mask & 1, mask & 2, mask & 4, mask & 8

    def f(x, y):
        dists = []
        if not up: dists.append(("t", y))
        if not down: dists.append(("b", H - 1 - y))
        if not left: dists.append(("l", x))
        if not right: dists.append(("r", H - 1 - x))
        near = min(dists, key=lambda t: t[1]) if dists else None
        if near is not None and near[1] < width:
            side, d = near
            if d == 0: return shade(frame_lo, 0.6)
            if d == 2: return band
            lit = side in ("t", "l")
            return frame_hi if lit else frame_lo
        # where two connected edges meet but the panel beside them is not there, nothing marks it: a plain field
        return inner(x, y)
    return f


SIDE_EDGES_C = {"north": ("up", "down", "east", "west"), "south": ("up", "down", "west", "east"),
                "east": ("up", "down", "south", "north"), "west": ("up", "down", "north", "south")}


def connected(assets, name, inner, frame, frame_hi, frame_lo, band):
    T = os.path.join(assets, "textures/block")
    for mask in range(16):
        paint_hi(framed(inner, mask, frame, frame_hi, frame_lo, band)).save(os.path.join(T, f"{name}_{mask}.png"))
    dirs = ["down", "east", "north", "south", "up", "west"]
    variants = {}
    for bits in range(64):
        j = {d: bool(bits >> i & 1) for i, d in enumerate(dirs)}
        tex, faces = {"particle": f"{MOD}:block/{name}_0"}, {}
        for face, (u, dn, l, r) in SIDE_EDGES_C.items():
            m = (1 if j[u] else 0) | (2 if j[dn] else 0) | (4 if j[l] else 0) | (8 if j[r] else 0)
            tex[face] = f"{MOD}:block/{name}_{m}"
        # the top's picture has north at its top; the bottom's has south at its top
        tex["up"] = f"{MOD}:block/{name}_{(1 if j['north'] else 0) | (2 if j['south'] else 0) | (4 if j['west'] else 0) | (8 if j['east'] else 0)}"
        tex["down"] = f"{MOD}:block/{name}_{(1 if j['south'] else 0) | (2 if j['north'] else 0) | (4 if j['west'] else 0) | (8 if j['east'] else 0)}"
        for face in ("north", "south", "east", "west", "up", "down"):
            faces[face] = {"uv": [0, 0, 16, 16], "texture": f"#{face}", "cullface": face}
        model = name + "_" + "".join(d[0] if j[d] else "_" for d in dirs)
        wj(os.path.join(assets, f"models/block/{model}.json"),
           {"parent": "minecraft:block/block", "textures": tex, "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
        variants[",".join(f"{d}={'true' if j[d] else 'false'}" for d in dirs)] = {"model": f"{MOD}:block/{model}"}
    wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": variants})
    wj(os.path.join(assets, f"models/block/{name}.json"), {"parent": f"{MOD}:block/{name}_" + "_" * len(dirs)})
    wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})


HI_DECOR = ["olympian_marble", "olympian_marble_pillar", "gilded_frieze", "abyssal_tile", "underworld_brick", "chaos_lamp",
            "olympian_tiles", "chaos_mosaic", "olympian_panel", "abyssal_panel", "underworld_panel"]
CONNECTED = ["olympian_panel", "abyssal_panel", "underworld_panel"]


def gen_hi_decor(assets, data):
    """The realms' building stone, drawn at 16x16 like the game's own blocks."""
    T = os.path.join(assets, "textures/block")
    rnd = random.Random(31)

    def save(name, fn):
        paint_hi(fn).save(os.path.join(T, name + ".png"))

    # marble: warm white, a few grey veins running diagonally, a fleck of gold in one of them
    MARBLE = (232, 228, 220)
    veins = {(x, y) for x in range(16) for y in range(16) if (x + y * 2 + int(2 * math.sin(y * 0.9))) % 11 == 0}
    grain = [[rnd.uniform(-0.035, 0.035) for _ in range(16)] for _ in range(16)]

    def marble(x, y):
        c = shade(MARBLE, 1 + grain[y][x])
        if (x, y) in veins: c = (190, 184, 178) if (x * 3 + y) % 7 else GOLD
        return c
    save("olympian_marble", marble)

    def flute(x, y):
        k = x % 4
        return shade(marble(x, y), {0: 0.8, 1: 1.08, 2: 1.0, 3: 0.92}[k])
    save("olympian_marble_pillar", flute)

    def pillar_top(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        c = marble(x, y)
        if 5.6 < r < 6.6: c = shade(c, 0.8)
        if r < 2.2: c = GOLD_HI if r < 1.2 else GOLD
        return c
    save("olympian_marble_pillar_top", pillar_top)

    # the frieze: a gold Greek key, one turn to eight pixels, between a marble fillet and a row of gold beads
    key = ["######..", "#....#..", "#.##.#..", "#.#..#..", "#.####..", "#.......", "########", "........"]

    def frieze(x, y):
        if y == 0: return shade(MARBLE, 0.95)
        if y == 1: return (150, 140, 128)
        if y == 2 or y == 13: return GOLD_HI if x % 2 == 0 else GOLD_LO
        if y >= 14: return shade(MARBLE, 0.95 if y == 14 else 0.8)
        on = key[(y - 3) * 8 // 10][x % 8] == "#"
        return (GOLD if (x + y) % 3 else GOLD_HI) if on else (54, 40, 34)
    save("gilded_frieze", frieze)

    # abyssal tile: four tiles of sea-stone, each with a carved wave, in dark grout
    def abyssal(x, y):
        tx, ty = x % 8, y % 8
        if tx == 0 or ty == 0: return (16, 44, 50)
        c = shade((70, 150, 146), 1 + grain[y][x] * 1.5)
        if tx == 1 or ty == 1: c = (120, 196, 186)
        if tx == 7 or ty == 7: c = (44, 104, 104)
        if ty == 4 + (1 if tx in (2, 3) else -1 if tx in (5, 6) else 0): c = (190, 244, 230)
        return c
    save("abyssal_tile", abyssal)

    # underworld brick: blackstone bricks, embers glowing in the mortar
    def brick(x, y):
        row = y // 4
        bx, by = (x + (4 if row % 2 else 0)) % 8, y % 4
        if by == 3 or bx == 7:
            return (255, 130, 50) if (x * 5 + y * 3) % 7 == 0 else (110, 36, 20) if (x + y) % 3 == 0 else (52, 18, 14)
        c = shade((58, 50, 66), 1 + grain[y][x] * 2)
        if by == 0 or bx == 0: c = (86, 76, 98)
        return c
    save("underworld_brick", brick)

    # the chaos lamp: glowing glass in a gold frame, the three gods' colours swirling in it
    cols = [(255, 214, 100), (100, 228, 236), (200, 130, 255)]

    def lamp(x, y):
        e = min(x, y, 15 - x, 15 - y)
        if e == 0: return GOLD_LO
        if e == 1: return GOLD_HI if x + y < 15 else GOLD
        r = math.hypot(x - 7.5, y - 7.5)
        a = math.atan2(y - 7.5, x - 7.5) + r * 0.5
        k = (a / math.tau * 3) % 3
        c = cols[int(k) % 3]
        return mix(c, (255, 250, 236), max(0.0, 1 - r / 4.0))
    save("chaos_lamp", lamp)

    # Olympian tiles: four flags of marble with a gold star where their corners meet
    def tiles(x, y):
        tx, ty = x % 8, y % 8
        c = marble(x, y) if (x, y) not in veins else shade(MARBLE, 0.97)
        if tx == 0 or ty == 0: c = (164, 154, 144)
        elif tx == 1 or ty == 1: c = shade(MARBLE, 1.05)
        elif tx == 7 or ty == 7: c = shade(MARBLE, 0.86)
        if (x, y) in ((0, 0), (8, 8), (0, 8), (8, 0)): c = GOLD_HI
        if (x in (15, 1, 7, 9) and y in (0, 8)) or (y in (15, 1, 7, 9) and x in (0, 8)): c = GOLD
        return c
    save("olympian_tiles", tiles)

    # chaos mosaic: tesserae two pixels square, a ring of the three gods' colours on marble
    def mosaic(x, y):
        if x % 2 == 1 and y % 2 == 1: return (120, 112, 104)
        cx, cy = (x // 2) * 2 + 1, (y // 2) * 2 + 1
        r = math.hypot(cx - 8, cy - 8)
        a = (math.atan2(cy - 8, cx - 8) + math.pi) / math.tau
        jitter = 0.92 + random.Random(cx * 31 + cy).random() * 0.14
        if 4.5 <= r < 7: c = cols[int(a * 3) % 3]
        elif r < 2.5: c = (255, 236, 170)
        else: c = (232, 226, 214)
        return shade(c, jitter)
    save("chaos_mosaic", mosaic)

    plain = ["olympian_marble", "gilded_frieze", "abyssal_tile", "underworld_brick", "chaos_lamp", "olympian_tiles", "chaos_mosaic"]
    for b in plain + ["olympian_marble_pillar"]:
        if b == "olympian_marble_pillar":
            model = {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/{b}_top", "side": f"{MOD}:block/{b}"}}
        else:
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}}
        wj(os.path.join(assets, f"models/block/{b}.json"), model)
        wj(os.path.join(assets, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        wj(os.path.join(assets, f"items/{b}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{b}"}})

    # the connecting panels: one per tower, a field of its stone in a frame that runs round a whole run of them
    sea = lambda x, y: (190, 244, 230) if (y % 8) == 4 + (1 if x % 8 in (1, 2) else -1 if x % 8 in (5, 6) else 0) else shade((70, 150, 146), 1 + grain[y][x] * 1.5)
    shade_field = lambda x, y: (150, 90, 210) if (x * 7 + y * 5) % 23 == 0 else shade((50, 44, 60), 1 + grain[y][x] * 2)
    connected(assets, "olympian_panel", marble, (230, 220, 200), (252, 248, 238), (150, 140, 128), GOLD)
    connected(assets, "abyssal_panel", sea, (60, 120, 124), (150, 214, 206), (24, 64, 70), (220, 226, 236))
    connected(assets, "underworld_panel", shade_field, (58, 50, 66), (104, 92, 118), (20, 16, 26), (190, 120, 240))

    recipes = {
        "olympian_tiles": ({"M": f"{MOD}:olympian_marble"}, ["MM", "MM"], 4),
        "chaos_mosaic": ({"M": f"{MOD}:olympian_marble", "A": "minecraft:amethyst_shard", "P": "minecraft:prismarine_shard", "G": "minecraft:gold_nugget"},
                         ["MAM", "PGP", "MAM"], 8),
        "olympian_panel": ({"M": f"{MOD}:olympian_marble", "G": "minecraft:gold_nugget"}, ["GMG", "MMM", "GMG"], 6),
        "abyssal_panel": ({"T": f"{MOD}:abyssal_tile", "I": "minecraft:iron_nugget"}, ["ITI", "TTT", "ITI"], 6),
        "underworld_panel": ({"B": f"{MOD}:underworld_brick", "A": "minecraft:amethyst_shard"}, ["ABA", "BBB", "ABA"], 6),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})
    # (all of them dug with a pickaxe, dropping themselves)
    for b in HI_DECOR:
        wj(os.path.join(data, f"loot_table/blocks/{b}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                                                                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ---- the trap halls' fittings and the towers' reliefs ----------------------------------------------------------------

TRAP_BLOCKS = ["spike_plate", "spike_plate_dark", "trap_spikes", "flame_vent", "gas_vent"]
RELIEFS = ["sky_relief", "sea_relief", "shade_relief"]


def cube(assets, name, all_tex=None, side=None, top=None):
    if side:
        model = {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/{top}", "side": f"{MOD}:block/{side}"}}
    else:
        model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{all_tex or name}"}}
    wj(os.path.join(assets, f"models/block/{name}.json"), model)
    wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
    wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})


def gen_traps(assets, data):
    T = os.path.join(assets, "textures/block")
    g = grain(81, 0.05)

    def plate(base):
        def fn(x, y):
            c = shade(base, g[y][x])
            if x in (0, 15) or y in (0, 15): c = shade(base, 0.72)
            elif x in (1,) or y in (1,): c = shade(base, 1.12)
            # four holes the spikes come up through, each a dark socket with a lit lower lip
            for hx, hy in ((4, 4), (11, 4), (4, 11), (11, 11)):
                d = max(abs(x - hx), abs(y - hy))
                if d == 0: c = (18, 16, 20)
                elif d == 1: c = (36, 32, 38) if (x - hx) + (y - hy) < 1 else shade(base, 1.2)
            if (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)): c = (150, 118, 70)            # a bronze rivet
            return c
        return fn
    paint_hi(plate((138, 140, 134))).save(os.path.join(T, "spike_plate.png"))
    paint_hi(plate((86, 84, 96))).save(os.path.join(T, "spike_plate_dark.png"))
    for b in ("spike_plate", "spike_plate_dark"):
        cube(assets, b)

    # the spikes: iron, rusted at their roots, bright at their points
    def iron(x, y):
        t = y / 15
        c = mix((236, 238, 244), (92, 70, 56), t)
        if x % 4 == 0: c = shade(c, 1.15)
        if x % 4 == 3: c = shade(c, 0.7)
        return c
    paint_hi(iron).save(os.path.join(T, "trap_spikes.png"))
    elements = []
    for sx, sz in ((2, 2), (9, 2), (2, 9), (9, 9), (5.5, 5.5)):
        tall = 12 if (sx, sz) == (5.5, 5.5) else 10
        for k, (w, h0, h1) in enumerate(((5, 0, tall * 0.45), (3, tall * 0.45, tall * 0.8), (1, tall * 0.8, tall))):
            o = (5 - w) / 2
            f = [sx + o, h0, sz + o, sx + o + w, h1, sz + o + w]
            faces = {d: {"uv": [f[0] if d in ("north", "south") else f[2], 16 - f[4], (f[3] if d in ("north", "south") else f[5]), 16 - f[1]], "texture": "#s"}
                     for d in ("north", "south", "east", "west")}
            faces["up"] = {"uv": [6, 0, 8, 2], "texture": "#s"}
            faces["down"] = {"uv": [6, 14, 8, 16], "texture": "#s"}
            elements.append({"from": [f[0], f[1], f[2]], "to": [f[3], f[4], f[5]], "faces": faces})
    wj(os.path.join(assets, "models/block/trap_spikes.json"), {"parent": "minecraft:block/block", "textures": {"particle": f"{MOD}:block/trap_spikes", "s": f"{MOD}:block/trap_spikes"},
                                                              "elements": elements})
    wj(os.path.join(assets, "blockstates/trap_spikes.json"), {"variants": {"": {"model": f"{MOD}:block/trap_spikes"}}})
    wj(os.path.join(assets, "items/trap_spikes.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/trap_spikes"}})

    # the flame vent: an iron grate over coals, glowing between its bars
    def vent_top(x, y):
        if x in (0, 15) or y in (0, 15): return (40, 36, 44)
        if x in (1, 14) or y in (1, 14): return (86, 80, 92)
        if x % 3 == 2:
            return (70, 66, 76) if y % 4 else (110, 104, 118)
        heat = 0.5 + 0.5 * math.sin(x * 1.3 + y * 0.9)
        return mix((90, 20, 10), (255, 160, 60), heat)
    paint_hi(vent_top).save(os.path.join(T, "flame_vent_top.png"))

    def vent_side(x, y):
        c = shade((62, 58, 70), g[y][x])
        if y in (0, 1): c = (86, 80, 92)
        if y == 2: c = (40, 36, 44)
        if y > 12: c = shade(c, 0.8)
        return c
    paint_hi(vent_side).save(os.path.join(T, "flame_vent_side.png"))
    cube(assets, "flame_vent", side="flame_vent_side", top="flame_vent_top")

    # the gas vent: a ring of green-stained stone round a dark mouth
    def gas_top(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        c = shade((112, 116, 104), g[y][x])
        if x in (0, 15) or y in (0, 15): c = (80, 84, 76)
        if r < 2.6: c = (16, 22, 14)
        elif r < 3.6: c = (60, 92, 44)
        elif r < 5.2: c = mix((140, 190, 90), c, (r - 3.6) / 1.6)
        if 5.6 < r < 6.4 and int(math.degrees(math.atan2(y - 7.5, x - 7.5))) % 45 < 15: c = (150, 118, 70)
        return c
    paint_hi(gas_top).save(os.path.join(T, "gas_vent_top.png"))
    paint_hi(lambda x, y: shade((112, 116, 104), g[y][x] * (0.75 if y > 12 else 1.0))).save(os.path.join(T, "gas_vent_side.png"))
    cube(assets, "gas_vent", side="gas_vent_side", top="gas_vent_top")

    # reliefs: a scene carved into a frame of the tower's stone, the carving lit on its upper left edges
    scenes = {
        "sky_relief": ((226, 222, 212), (250, 210, 80),
                       ["................", "..##.....###....", ".####...#####...", "######.#######..", "........#.......", ".......##.......",
                        "......##........", ".....#######....", ".......##.......", "......##........", ".....##.........", "....#...........",
                        "................"]),
        "sea_relief": ((80, 150, 144), (210, 236, 236),
                       ["................", "..#....#....#...", "..#....#....#...", "..##...#...##...", "...#########....", ".......#........",
                        ".......#........", ".......#........", "..##...#...##...", ".#..#.###.#..#..", "#....#...#....#.", "................",
                        "................"]),
        "shade_relief": ((56, 50, 66), (196, 140, 240),
                         ["................", ".....######.....", "....########....", "...##..##..##...", "...##..##..##...", "...##########...",
                          "....###..###....", ".....######.....", "......#..#......", "..#..........#..", ".###...##...###.", "#####.####.#####",
                          "................"]),
    }
    for name, (base, accent, art) in scenes.items():
        def fn(x, y, base=base, accent=accent, art=art):
            c = shade(base, g[y][x])
            if x == 0 or y == 0: return shade(base, 1.18)
            if x == 15 or y == 15: return shade(base, 0.6)
            if x == 1 or y == 1: return shade(base, 0.78)                          # the field is sunk inside its frame
            ry = y - 2
            on = 0 <= ry < len(art) and art[ry][x] == "#"
            above = 0 <= ry - 1 < len(art) and x > 0 and art[ry - 1][x - 1] == "#"
            if on:
                c = shade(base, 1.22) if not above else shade(base, 1.04)
                if (x + y) % 5 == 0: c = mix(c, accent, 0.6)
            elif above:
                c = shade(base, 0.62)                                                 # the carving's shadow
            return c
        paint_hi(fn).save(os.path.join(T, name + ".png"))
        cube(assets, name)
        wj(os.path.join(data, f"loot_table/blocks/{name}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                                                                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ---- each tower's own connecting stone: a column, a floor panel, a brick ---------------------------------------------

TOWER_STONE = {
    # tower: (column, panel, brick, base, light, dark, accent)
    "zeus": ("marble_column", "sunstone_panel", "storm_brick", (232, 228, 220), (252, 250, 244), (160, 152, 142), GOLD),
    "poseidon": ("coral_column", "pearl_panel", "tide_brick", (92, 168, 160), (170, 226, 214), (36, 92, 92), (236, 176, 160)),
    "hades": ("bone_column", "ember_panel", "ash_brick", (206, 198, 178), (236, 230, 214), (120, 110, 96), (190, 120, 240)),
}
TOWER_BLOCKS = [b for t in TOWER_STONE.values() for b in t[:3]]


def column_side(kind, base, light, dark, accent, g):
    """A column's side: fluted shaft; a moulded base where the run starts, a capital where it ends.
    kind: single, bottom, middle, top."""
    def fn(x, y):
        k = x % 4
        c = shade(base, g[y][x] * {0: 0.78, 1: 1.1, 2: 1.0, 3: 0.9}[k])
        has_cap = kind in ("single", "top")
        has_base = kind in ("single", "bottom")
        if has_cap and y <= 3:
            c = [shade(dark, 0.8), light, accent, shade(base, 0.85)][y]
            if y == 2 and x % 3 == 1: c = mix(accent, (255, 255, 255), 0.4)       # beads in the necking
        if has_base and y >= 12:
            c = [shade(base, 0.85), light, base, shade(dark, 0.8)][y - 12]
        return c
    return fn


def connected_column(assets, name, base, light, dark, accent):
    T = os.path.join(assets, "textures/block")
    g = grain(len(name) * 7, 0.04)
    for kind in ("single", "bottom", "middle", "top"):
        paint_hi(column_side(kind, base, light, dark, accent, g)).save(os.path.join(T, f"{name}_{kind}.png"))

    def end(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        c = shade(base, g[y][x])
        if 5.6 < r < 6.6: c = shade(dark, 1.0)
        if r < 2.0: c = accent
        return c
    paint_hi(end).save(os.path.join(T, f"{name}_end.png"))
    dirs = ["down", "east", "north", "south", "up", "west"]
    variants = {}
    for bits in range(64):
        j = {d: bool(bits >> i & 1) for i, d in enumerate(dirs)}
        kind = "middle" if j["up"] and j["down"] else "bottom" if j["up"] else "top" if j["down"] else "single"
        model = f"{name}_{kind}"
        variants[",".join(f"{d}={'true' if j[d] else 'false'}" for d in dirs)] = {"model": f"{MOD}:block/{model}"}
    for kind in ("single", "bottom", "middle", "top"):
        wj(os.path.join(assets, f"models/block/{name}_{kind}.json"),
           {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/{name}_end", "side": f"{MOD}:block/{name}_{kind}"}})
    wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": variants})
    wj(os.path.join(assets, f"models/block/{name}.json"), {"parent": f"{MOD}:block/{name}_single"})
    wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})


def gen_tower_stone(assets, data):
    T = os.path.join(assets, "textures/block")
    for tower, (col, panel, brick, base, light, dark, accent) in TOWER_STONE.items():
        connected_column(assets, col, base, light, dark, accent)
        g = grain(len(tower) * 13, 0.045)
        # the floor panel's field: one look per tower, tiling seamlessly from block to block
        if tower == "zeus":
            # sunstone: warm cream, a fine lattice of gold rays
            field = lambda x, y, g=g: GOLD if (x + y) % 8 == 0 or (x - y) % 8 == 0 and (x + y) % 2 == 0 else shade((240, 226, 190), g[y][x])
            frame = ((226, 212, 180), (250, 244, 226), (150, 130, 90), GOLD)
        elif tower == "poseidon":
            # pearl: soft bands of mother-of-pearl
            def field(x, y, g=g):
                t = 0.5 + 0.5 * math.sin((x * 0.6 + y * 0.9) + math.sin(y * 0.7) * 1.4)
                return shade(mix((220, 236, 240), (240, 210, 226), t) if t > 0.5 else mix((176, 226, 222), (220, 236, 240), t * 2), g[y][x])
            frame = ((190, 200, 210), (236, 240, 248), (110, 120, 134), (150, 220, 230))
        else:
            # ember: black glass with glowing cracks running through it
            def field(x, y, g=g):
                crack = (x * 3 + y * 5 + (x * y) % 7) % 13 == 0 or (x + 2 * y) % 11 == 0 and x % 3 == 0
                return (255, 130, 50) if crack and (x + y) % 2 else (150, 50, 30) if crack else shade((34, 28, 40), g[y][x])
            frame = ((70, 56, 50), (130, 104, 70), (24, 18, 22), (220, 160, 70))
        connected(assets, panel, field, *frame)
        # the brick: laid in courses, each tower's own
        def brick_fn(x, y, base=base, light=light, dark=dark, accent=accent, g=g, tower=tower):
            row = y // 4
            bx, by = (x + (4 if row % 2 else 0)) % 8, y % 4
            if by == 3 or bx == 7:
                if tower == "zeus" and (x * 3 + y) % 9 == 0: return GOLD                        # gold in the joints
                if tower == "hades" and (x * 5 + y) % 7 == 0: return (200, 60, 40)
                return shade(dark, 0.8)
            c = shade(base, g[y][x] * (0.92 + 0.08 * ((x // 8 + row) % 2)))
            if by == 0 or bx == 0: c = light
            return c
        paint_hi(brick_fn).save(os.path.join(T, brick + ".png"))
        cube(assets, brick)
    recipes = {
        "marble_column": ({"M": f"{MOD}:olympian_marble_pillar"}, ["M", "M"], 2),
        "sunstone_panel": ({"M": f"{MOD}:olympian_marble", "G": "minecraft:glowstone_dust"}, ["MGM", "GMG", "MGM"], 5),
        "storm_brick": ({"S": "minecraft:stone_bricks", "G": "minecraft:gold_nugget"}, ["SS", "SG"], 3),
        "coral_column": ({"P": "minecraft:prismarine_bricks", "C": "minecraft:brain_coral_block"}, ["P", "C", "P"], 3),
        "pearl_panel": ({"T": f"{MOD}:abyssal_tile", "N": "minecraft:nautilus_shell"}, ["TTT", "TNT", "TTT"], 8),
        "tide_brick": ({"S": "minecraft:sandstone", "P": "minecraft:prismarine_shard"}, ["SP", "PS"], 4),
        "bone_column": ({"B": "minecraft:bone_block"}, ["B", "B"], 2),
        "ember_panel": ({"O": "minecraft:obsidian", "M": "minecraft:magma_block"}, ["OMO", "MOM", "OMO"], 5),
        "ash_brick": ({"B": "minecraft:polished_blackstone_bricks", "A": "minecraft:bone_meal"}, ["BB", "BA"], 3),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})
    for b in TOWER_BLOCKS:
        wj(os.path.join(data, f"loot_table/blocks/{b}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                                                                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ---- the furnishings of the new rooms ----------------------------------------------------------------------------------

ROOM_BLOCKS_2 = ["sky_mosaic", "sea_mosaic", "shade_mosaic", "star_vault", "celestial_globe", "bronze_grate", "weapon_rack", "scroll_shelf",
                 "bath_tile", "laurel_hedge", "kelp_weave", "asphodel_bed"]


def gen_room_furnishings(assets, data):
    T = os.path.join(assets, "textures/block")
    g = grain(91, 0.05)
    tess = lambda x, y: x % 2 == 1 and y % 2 == 1

    # the towers' mosaics: tesserae two pixels square in a pattern that runs on from block to block, framed as a run
    def sky(x, y):
        if tess(x, y): return (150, 150, 160)
        cx, cy = x // 2, y // 2
        c = (238, 236, 228) if (cx + cy) % 2 else (120, 170, 230)
        if (cx % 4, cy % 4) == (1, 1): c = GOLD
        return shade(c, g[y][x])

    def sea(x, y):
        if tess(x, y): return (40, 80, 90)
        cx, cy = x // 2, y // 2
        wave = (cy + (1 if cx % 4 in (1, 2) else 0)) % 4
        c = [(40, 110, 140), (70, 160, 180), (140, 214, 220), (236, 244, 240)][wave]
        return shade(c, g[y][x])

    def shade_m(x, y):
        if tess(x, y): return (20, 16, 24)
        cx, cy = x // 2, y // 2
        c = (46, 40, 58)
        if (cx % 4, cy % 4) in ((1, 1), (2, 1), (1, 2)): c = (214, 206, 232)            # asphodel petals
        if (cx % 4, cy % 4) == (2, 2): c = (220, 190, 90)
        return shade(c, g[y][x])
    connected(assets, "sky_mosaic", sky, (226, 220, 206), (250, 248, 240), (150, 140, 128), GOLD)
    connected(assets, "sea_mosaic", sea, (60, 120, 124), (150, 214, 206), (24, 64, 70), (220, 226, 236))
    connected(assets, "shade_mosaic", shade_m, (58, 50, 66), (104, 92, 118), (20, 16, 26), (190, 120, 240))

    # the star vault: night blue, gold stars, a faint band of the galaxy
    rnd = random.Random(7)
    stars = {(rnd.randrange(16), rnd.randrange(16)) for _ in range(9)}

    def vault(x, y):
        c = mix((18, 20, 54), (40, 36, 90), 0.5 + 0.5 * math.sin((x + y) * 0.4))
        if abs((x - y) % 16 - 8) < 2: c = mix(c, (120, 110, 180), 0.25)
        if (x, y) in stars: c = (255, 236, 150) if (x + y) % 3 else (255, 255, 255)
        return c
    paint_hi(vault).save(os.path.join(T, "star_vault.png"))
    cube(assets, "star_vault")

    # the celestial globe: a blue sphere banded in gold on a bronze foot
    def globe(x, y):
        c = mix((40, 70, 150), (110, 160, 230), 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.3))
        if y % 5 == 2: c = GOLD
        if x == 7 or x == 8: c = GOLD_HI if y % 3 else GOLD
        if (x * 7 + y * 3) % 19 == 0: c = (255, 250, 220)
        return c
    paint_hi(globe).save(os.path.join(T, "celestial_globe.png"))
    paint_hi(lambda x, y: shade((176, 110, 50), g[y][x] * (1.15 if (x + y) % 5 == 0 else 1.0))).save(os.path.join(T, "bronze.png"))
    s = lambda k: f"{MOD}:block/{k}"
    parts = [((4, 0, 4, 12, 2, 12), s("bronze"), s("bronze"), s("bronze")), ((7, 2, 7, 9, 4, 9), s("bronze"), s("bronze"), s("bronze")),
             ((3, 4, 3, 13, 14, 13), s("celestial_globe"), s("celestial_globe"), s("celestial_globe")),
             ((2, 8, 7.5, 14, 9, 8.5), s("bronze"), s("bronze"), s("bronze")), ((7.5, 3, 2, 8.5, 15, 14), s("bronze"), s("bronze"), s("bronze"))]
    wj(os.path.join(assets, "models/block/celestial_globe.json"), parts_model(parts, s("celestial_globe")))
    wj(os.path.join(assets, "blockstates/celestial_globe.json"), {"variants": {"": {"model": f"{MOD}:block/celestial_globe"}}})
    wj(os.path.join(assets, "items/celestial_globe.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/celestial_globe"}})

    # the bronze grate: a lattice of bars over darkness
    def grate(x, y):
        if x in (0, 15) or y in (0, 15): return (120, 74, 34)
        if x % 4 == 1 or y % 4 == 1: return (196, 130, 62) if (x + y) % 2 else (176, 110, 50)
        return (22, 18, 16)
    paint_hi(grate).save(os.path.join(T, "bronze_grate.png"))
    cube(assets, "bronze_grate")

    # the weapon rack: a spear, a sword and a shield hung on a rack of dark oak
    def rack(x, y):
        c = shade((84, 56, 34), g[y][x] * (0.9 if x % 5 == 4 else 1.0))
        if y in (2, 13): c = (60, 40, 26)
        if x == 3 and 1 <= y <= 14: c = (150, 110, 70)                                     # spear shaft
        if x == 3 and y <= 2: c = (214, 218, 226)
        if 6 <= x <= 7 and 3 <= y <= 11: c = (206, 212, 224) if x == 6 else (160, 166, 180)  # sword
        if 5 <= x <= 8 and y == 11: c = GOLD
        if 6 <= x <= 7 and y >= 12: c = (100, 60, 40)
        r = math.hypot(x - 12, y - 7.5)
        if r < 3.2: c = (176, 50, 40) if r > 2.0 else GOLD                                  # shield
        return c
    paint_hi(rack).save(os.path.join(T, "weapon_rack.png"))
    paint_hi(lambda x, y: shade((84, 56, 34), g[y][x] * (0.85 if x % 4 == 3 else 1.0))).save(os.path.join(T, "weapon_rack_top.png"))
    cube(assets, "weapon_rack", side="weapon_rack", top="weapon_rack_top")

    # the scroll shelf: pigeonholes full of rolled scrolls, their ends tied in red
    def shelf(x, y):
        if y % 8 in (0, 7) or x in (0, 15): return (96, 64, 38) if y % 8 == 0 else (70, 46, 28)
        cell = (x - 1) // 2
        cy = y % 8
        if 2 <= cy <= 5:
            c = (236, 224, 190) if (cell + y // 8) % 3 else (214, 200, 160)
            if cy in (3, 4) and x % 2 == 0: c = (170, 150, 120)                             # the scroll's rolled end
            if cy == 2 and (cell * 3 + y) % 4 == 0: c = (190, 50, 40)                       # a red tie
            return c
        return (40, 28, 18)
    paint_hi(shelf).save(os.path.join(T, "scroll_shelf.png"))
    cube(assets, "scroll_shelf", side="scroll_shelf", top="weapon_rack_top")

    # bath tile: white tiles with a band of blue meander
    def bath(x, y):
        tx, ty = x % 4, y % 4
        if tx == 0 or ty == 0: return (170, 186, 196)
        c = shade((238, 242, 244), g[y][x])
        if 6 <= y <= 9 and (x + (y - 6) * 2) % 8 < 3: c = (70, 130, 190)
        return c
    paint_hi(bath).save(os.path.join(T, "bath_tile.png"))
    cube(assets, "bath_tile")

    # the towers' garden plants, clipped into blocks
    def hedge(leaf, flower, fseed):
        r2 = random.Random(fseed)
        flowers = {(r2.randrange(16), r2.randrange(16)) for _ in range(10)}

        def fn(x, y):
            v = 0.5 + 0.5 * math.sin(x * 1.7 + y * 2.3) * math.cos(x * 0.9 - y * 1.1)
            c = shade(leaf, 0.7 + 0.5 * v)
            if (x, y) in flowers and flower: c = flower
            return c
        return fn
    paint_hi(hedge((70, 120, 50), (240, 210, 90), 1)).save(os.path.join(T, "laurel_hedge.png"))
    paint_hi(hedge((40, 110, 80), (240, 160, 150), 2)).save(os.path.join(T, "kelp_weave.png"))
    paint_hi(hedge((140, 146, 132), (230, 224, 240), 3)).save(os.path.join(T, "asphodel_bed.png"))
    for b in ("laurel_hedge", "kelp_weave", "asphodel_bed"):
        cube(assets, b)

    recipes = {
        "sky_mosaic": ({"M": f"{MOD}:olympian_tiles", "L": "minecraft:lapis_lazuli"}, ["MLM", "LML", "MLM"], 8),
        "sea_mosaic": ({"T": f"{MOD}:abyssal_tile", "P": "minecraft:prismarine_crystals"}, ["TPT", "PTP", "TPT"], 8),
        "shade_mosaic": ({"B": f"{MOD}:underworld_brick", "D": "minecraft:white_dye"}, ["BDB", "DBD", "BDB"], 8),
        "star_vault": ({"B": "minecraft:blue_concrete", "G": "minecraft:glowstone_dust"}, ["BGB", "GBG", "BGB"], 8),
        "celestial_globe": ({"G": "minecraft:gold_ingot", "L": "minecraft:lapis_block", "C": "minecraft:copper_ingot"}, [" G ", "GLG", " C "], 1),
        "bronze_grate": ({"C": "minecraft:copper_ingot", "I": "minecraft:iron_bars"}, ["CIC", "ICI", "CIC"], 4),
        "weapon_rack": ({"P": "minecraft:dark_oak_planks", "S": "minecraft:iron_sword", "R": "minecraft:stick"}, ["RRR", "PSP", "RRR"], 2),
        "scroll_shelf": ({"P": "minecraft:dark_oak_planks", "B": "minecraft:paper"}, ["PPP", "BBB", "PPP"], 1),
        "bath_tile": ({"C": "minecraft:white_terracotta", "L": "minecraft:lapis_lazuli"}, ["CC", "CL"], 4),
        "laurel_hedge": ({"L": "minecraft:oak_leaves", "D": "minecraft:yellow_dye"}, ["LL", "LD"], 3),
        "kelp_weave": ({"K": "minecraft:dried_kelp", "S": "minecraft:seagrass"}, ["KK", "KS"], 3),
        "asphodel_bed": ({"L": "minecraft:azalea_leaves", "D": "minecraft:white_dye"}, ["LL", "LD"], 3),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})
    for b in ROOM_BLOCKS_2:
        wj(os.path.join(data, f"loot_table/blocks/{b}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                                                                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
