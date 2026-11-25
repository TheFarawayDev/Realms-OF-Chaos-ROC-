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
        gen_connected_furniture(assets)
        gen_variety(assets)
        gen_weapon_rack(assets)
        gen_upper(assets, data)
        gen_floor_polish(assets)
        gen_cuts(assets, data)
        gen_doors2(assets, data)
        gen_combat(assets, data)
        gen_furnish2(assets, data)
        gen_cache_brazier(assets)
        gen_particles(assets)
        gen_sprinkler(assets, data)
        gen_puzzle2(assets, data)
        gen_sliding_doors(assets, data)
        for floor in ("sunstone_panel", "pearl_panel", "ember_panel", "sky_mosaic", "sea_mosaic", "shade_mosaic"):
            connected_breaks(assets, floor)


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


# ---- variety: blocks that come in several looks, chosen by the game block by block --------------------------------------

def _load(T, name):
    return Image.open(os.path.join(T, name + ".png")).convert("RGBA")


def _roll(img, dx, dy):
    """The same seamless pattern, started somewhere else: a different block that still runs on into its neighbours."""
    out = Image.new("RGBA", img.size)
    w, h = img.size
    for y in range(h):
        for x in range(w):
            out.putpixel(((x + dx) % w, (y + dy) % h), img.getpixel((x, y)))
    return out


def _crack(img, seed, color, length=6):
    rnd = random.Random(seed)
    x, y = rnd.randrange(3, 13), rnd.randrange(3, 13)
    for _ in range(length):
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), color + (255,))
        x += rnd.choice((-1, 0, 1))
        y += 1 if rnd.random() < 0.6 else 0
    return img


def _speckle(img, seed, colors, n, keep=None):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if keep and not keep(img.getpixel((x, y))):
            continue
        img.putpixel((x, y), rnd.choice(colors) + (255,))
    return img


def _patch(img, seed, color, strength, radius=3.5):
    rnd = random.Random(seed)
    cx, cy = rnd.uniform(3, 12), rnd.uniform(3, 12)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < radius and (x * 7 + y * 3 + seed) % 3:
                p = img.getpixel((x, y))
                img.putpixel((x, y), mix(p[:3], color, strength * (1 - d / radius)) + (255,))
    return img


def _brightness(c):
    return sum(c[:3]) / 3


def variants(assets, name, imgs, weights, parent="minecraft:block/cube_all", turn=False):
    """Writes a block's looks as variants the game picks among at random for each block placed."""
    T = os.path.join(assets, "textures/block")
    entries = []
    for i, (img, wgt) in enumerate(zip(imgs, weights)):
        tex = name if i == 0 else f"{name}_v{i}"
        img.save(os.path.join(T, tex + ".png"))
        model = name if i == 0 else f"{name}_v{i}"
        wj(os.path.join(assets, f"models/block/{model}.json"), {"parent": parent, "textures": {"all": f"{MOD}:block/{tex}"}})
        entries.append({"model": f"{MOD}:block/{model}", "weight": wgt})
        if turn:
            for rot in (90, 180, 270):
                entries.append({"model": f"{MOD}:block/{model}", "weight": wgt, "y": rot, "x": 0})
    wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": {"": entries}})


def gen_variety(assets):
    T = os.path.join(assets, "textures/block")
    dark = lambda c: _brightness(c) < 255
    # bricks: plain, one cracked, one with a brick of its own (gold-veined, barnacled, ember-lit), one worn
    for name, special, crackc in (("storm_brick", [GOLD, GOLD_HI], (120, 116, 110)), ("tide_brick", [(236, 226, 200), (200, 170, 150)], (40, 90, 90)),
                                  ("ash_brick", [(255, 120, 50), (200, 60, 40)], (110, 100, 88)), ("underworld_brick", [(255, 150, 60), (255, 200, 120)], (20, 16, 24))):
        base = _load(T, name)
        v1 = _crack(base.copy(), len(name), crackc)
        v2 = _speckle(base.copy(), len(name) * 3, special, 7)
        v3 = _patch(base.copy(), len(name) * 5, (60, 60, 60) if name != "tide_brick" else (90, 150, 110), 0.25)
        variants(assets, name, [base, v1, v2, v3], [6, 2, 2, 2])
    # tiles and stone: the pattern begun elsewhere, now and then a chip or a stain
    for name, stain in (("abyssal_tile", (150, 220, 180)), ("olympian_tiles", (200, 190, 170)), ("bath_tile", (180, 200, 214)), ("olympian_marble", (190, 184, 178))):
        base = _load(T, name)
        imgs = [base, _patch(base.copy(), 3, stain, 0.35), _crack(base.copy(), 9, shade(stain, 0.7), 4)]
        if name == "olympian_marble":
            imgs.append(_roll(base, 5, 9))
        variants(assets, name, imgs, [5, 2, 2, 3][:len(imgs)])
    # the star vault: its stars somewhere else each time, and now and then a comet
    base = _load(T, "star_vault")
    comet = _roll(base, 7, 3)
    for k in range(5):
        comet.putpixel((3 + k, 4 + k // 2), mix((255, 255, 255), (120, 140, 220), k / 5) + (255,))
    variants(assets, "star_vault", [base, _roll(base, 5, 11), _roll(base, 10, 6), comet], [4, 4, 4, 1], turn=True)
    # the hedges: leaves turned every way, flowers and berries scattered differently
    for name, extra in (("laurel_hedge", [(240, 210, 90), (255, 236, 150)]), ("kelp_weave", [(240, 160, 150), (250, 220, 120)]),
                        ("asphodel_bed", [(230, 224, 240), (250, 250, 255)])):
        base = _load(T, name)
        variants(assets, name, [base, _speckle(_roll(base, 6, 3), 41, extra, 6), _roll(base, 11, 9)], [3, 2, 3], turn=True)
    # the bronze grate: bright, or going green with verdigris
    base = _load(T, "bronze_grate")
    green = lambda img, s: _speckle(img, s, [(90, 160, 130), (70, 130, 110)], 18, keep=lambda p: _brightness(p) > 60)
    variants(assets, "bronze_grate", [base, green(base.copy(), 5), green(green(base.copy(), 6), 7)], [4, 2, 1])


# ---- weapon racks and scroll shelves that run on from block to block --------------------------------------------------

def gen_connected_furniture(assets):
    T = os.path.join(assets, "textures/block")
    g = grain(97, 0.04)
    rnd = random.Random(5)

    # the rack: a run of arms hung side by side - spears, swords, shields, axes, in no fixed order
    def rack_field(x, y):
        c = shade((84, 56, 34), g[y][x] * (0.9 if x % 5 == 4 else 1.0))
        if y in (3, 12): c = (60, 40, 26)
        slot = x // 4
        kind = (slot * 7 + 3) % 4
        lx = x % 4
        if kind == 0 and lx == 1 and 1 <= y <= 14: c = (214, 218, 226) if y <= 3 else (150, 110, 70)          # a spear
        if kind == 1 and lx in (1, 2) and 3 <= y <= 11: c = (206, 212, 224) if lx == 1 else (160, 166, 180)    # a sword
        if kind == 1 and y == 11 and lx in (0, 1, 2, 3): c = GOLD
        if kind == 1 and lx in (1, 2) and y >= 12: c = (100, 60, 40)
        if kind == 2 and math.hypot(lx - 1.5, y - 7.5) < 2.2: c = (176, 50, 40) if math.hypot(lx - 1.5, y - 7.5) > 1.2 else GOLD   # a shield
        if kind == 3 and lx == 2 and 2 <= y <= 14: c = (150, 110, 70)                                          # an axe
        if kind == 3 and lx in (0, 1) and 3 <= y <= 6: c = (190, 196, 206)
        return c
    # (the weapon rack is a model of its own now: see gen_weapon_rack)

    # the scroll shelf: pigeonholes of scrolls, two rows to a block, the case framing a whole wall of them
    def shelf_field(x, y):
        cy = y % 8
        if cy in (0, 7): return (96, 64, 38) if cy == 0 else (70, 46, 28)
        if x % 4 == 0: return (80, 54, 32)
        if 2 <= cy <= 5:
            c = (236, 224, 190) if (x // 4 + y // 8) % 3 else (214, 200, 160)
            if cy in (3, 4) and x % 2 == 0: c = (170, 150, 120)
            if cy == 2 and (x * 3 + y) % 7 == 0: c = (190, 50, 40)
            return c
        return (40, 28, 18)
    connected(assets, "scroll_shelf", shelf_field, (96, 64, 38), (130, 92, 58), (56, 36, 22), (150, 118, 70))


# ---- the weapon rack: a real rack on the wall, with real arms on it ---------------------------------------------------

def gen_weapon_rack(assets):
    import glob
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    for old in glob.glob(os.path.join(T, "weapon_rack_*.png")) + glob.glob(os.path.join(M, "weapon_rack_*.json")):
        os.remove(old)
    g = grain(101, 0.05)
    tex = {
        "rack_wood": lambda x, y: shade((96, 64, 38), g[y][x] * (0.86 if y % 4 == 3 else 1.0)),
        "rack_board": lambda x, y: shade((62, 42, 26), g[y][x] * (0.8 if x % 4 == 3 else 1.0)),
        "rack_iron": lambda x, y: mix((150, 156, 170), (236, 240, 248), (15 - y + x) / 30),
        "rack_gold": lambda x, y: GOLD_HI if (x + y) % 4 == 0 else GOLD,
        "rack_leather": lambda x, y: (96, 56, 40) if (x + y) % 3 else (70, 40, 30),
        "rack_shield": lambda x, y: (GOLD if min(x, y, 15 - x, 15 - y) < 2 else (176, 48, 40) if (x // 4 + y // 4) % 2 else (150, 36, 32)),
    }
    for name, fn in tex.items():
        paint_hi(fn).save(os.path.join(T, name + ".png"))
    t = lambda n: f"{MOD}:block/{n}"
    textures = {"particle": t("rack_wood"), "wood": t("rack_wood"), "board": t("rack_board"), "iron": t("rack_iron"), "gold": t("rack_gold"),
                "leather": t("rack_leather"), "shield": t("rack_shield")}

    def box(a, b, texture, rot=None, cull_back=False):
        faces = {d: {"texture": f"#{texture}"} for d in ("north", "south", "east", "west", "up", "down")}
        if cull_back:
            faces["south"]["cullface"] = "south"
        el = {"from": list(a), "to": list(b), "faces": faces}
        if rot:
            el["rotation"] = rot
        return el

    # (drawn facing north: the board against the south side of the block, the arms hung in front of it)
    arms = {
        "spear": [box((7.5, 0.5, 9.5), (8.5, 13, 10.5), "wood"), box((7, 13, 9.5), (9, 14.5, 10.5), "iron"), box((7.5, 14.5, 9.5), (8.5, 16, 10.5), "iron"),
                  box((6.5, 12.5, 9.25), (9.5, 13, 10.75), "gold")],
        "sword": [box((7, 5, 9.5), (9, 15.5, 10.5), "iron"), box((5, 4, 9), (11, 5, 11), "gold"), box((7.5, 1.5, 9.5), (8.5, 4, 10.5), "leather"),
                  box((7, 0.5, 9.5), (9, 1.5, 10.5), "gold")],
        "shield": [box((3, 2.5, 9.5), (13, 13.5, 10.75), "shield"), box((7, 6.5, 9), (9, 9.5, 9.5), "gold")],
        "axes": [],
    }
    for sign in (1, -1):
        r = {"origin": [8, 8, 10], "axis": "z", "angle": 45.0 * sign}
        arms["axes"] += [box((7.5, 0.5, 9.5 - (0.5 if sign < 0 else 0)), (8.5, 15, 10.5 - (0.5 if sign < 0 else 0)), "wood", r),
                         box((8.5, 11, 9.25 - (0.5 if sign < 0 else 0)), (11.5, 15, 10.75 - (0.5 if sign < 0 else 0)), "iron", r)]
    dirs = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for mask in range(16):
        left, right, up, down = mask & 1, mask & 2, mask & 4, mask & 8
        frame = [box((0, 0, 13), (16, 16, 16), "board", cull_back=True),
                 box((0, 3, 11), (16, 4, 13), "wood"), box((0, 11, 11), (16, 12, 13), "wood")]
        # (seen from in front, its left is the east side of a north-facing model)
        if not left: frame.append(box((14, 0, 11), (16, 16, 16), "wood"))
        if not right: frame.append(box((0, 0, 11), (2, 16, 16), "wood"))
        if not up: frame.append(box((0, 14.5, 10.5), (16, 16, 16), "wood"))
        if not down: frame.append(box((0, 0, 11), (16, 1, 16), "wood"))
        for arm, parts in arms.items():
            wj(os.path.join(M, f"weapon_rack_{mask}_{arm}.json"), {"parent": "minecraft:block/block", "textures": textures, "elements": frame + parts})
        for facing, rot in dirs.items():
            key = f"down={'true' if down else 'false'},facing={facing},left={'true' if left else 'false'},right={'true' if right else 'false'},up={'true' if up else 'false'}"
            variants[key] = [{"model": f"{MOD}:block/weapon_rack_{mask}_{arm}", "y": rot, "weight": w}
                             for arm, w in (("spear", 3), ("sword", 3), ("shield", 2), ("axes", 2))]
    wj(os.path.join(assets, "blockstates/weapon_rack.json"), {"variants": variants})
    wj(os.path.join(M, "weapon_rack.json"), {"parent": f"{MOD}:block/weapon_rack_0_sword"})
    wj(os.path.join(assets, "items/weapon_rack.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/weapon_rack"}})


# ---- the upper floors' stone, portal frames, arrival sigils ---------------------------------------------------------------

UPPER_BLOCKS = ["cloudstone", "cloud_tile", "trench_stone", "nacre_tile", "tartarus_stone", "throne_tile",
                "portal_frame_sky", "portal_frame_sea", "portal_frame_shade", "portal_frame_hub", "arrival_sigil"]


def gen_upper(assets, data):
    T = os.path.join(assets, "textures/block")
    g = grain(111, 0.05)
    rnd = random.Random(3)
    spots = [(rnd.randrange(16), rnd.randrange(16)) for _ in range(12)]

    def cloudstone(x, y):
        v = 0.5 + 0.5 * math.sin(x * 0.7 + math.sin(y * 0.5) * 2) * math.cos(y * 0.6)
        c = mix((214, 224, 240), (250, 252, 255), v)
        if y % 8 == 7: c = (176, 190, 214)                                        # coursing
        if (x + (4 if (y // 8) % 2 else 0)) % 16 == 0: c = (176, 190, 214)
        return shade(c, g[y][x])

    def cloud_tile(x, y):
        tx, ty = x % 8, y % 8
        if tx == 0 or ty == 0: return (190, 200, 220)
        c = cloudstone(x, y)
        if (tx, ty) in ((4, 4), (3, 4), (4, 3)): c = GOLD
        if tx == 7 or ty == 7: c = shade(c, 0.9)
        return c

    def trench(x, y):
        c = shade((26, 40, 60), g[y][x] * (1.15 if (x * 3 + y) % 7 == 0 else 1.0))
        if (x, y) in spots[:7]: c = (90, 240, 220)                                # glowing specks
        if y % 8 == 7 or (x + (4 if (y // 8) % 2 else 0)) % 16 == 0: c = (14, 22, 34)
        return c

    def nacre(x, y):
        t = 0.5 + 0.5 * math.sin(x * 0.9 + y * 0.5 + math.sin(x * 0.4) * 2)
        c = mix((210, 226, 236), (236, 214, 232), t)
        if (x % 8 == 0) or (y % 8 == 0): c = (150, 180, 196)
        return shade(c, g[y][x])

    def tartarus(x, y):
        c = shade((40, 24, 26), g[y][x])
        row = y // 4
        bx, by = (x + (4 if row % 2 else 0)) % 8, y % 4
        if by == 3 or bx == 7: c = (200, 60, 30) if (x + y * 3) % 5 == 0 else (90, 26, 20)
        elif by == 0 or bx == 0: c = (70, 44, 46)
        return c

    def throne(x, y):
        tx, ty = x % 8, y % 8
        c = shade((24, 20, 30), g[y][x])
        if tx == 0 or ty == 0: c = GOLD_LO
        if tx == ty or tx == 8 - ty: c = (60, 48, 70)
        if (tx, ty) == (4, 4): c = GOLD_HI
        return c
    for name, fn in (("cloudstone", cloudstone), ("cloud_tile", cloud_tile), ("trench_stone", trench), ("nacre_tile", nacre),
                     ("tartarus_stone", tartarus), ("throne_tile", throne)):
        paint_hi(fn).save(os.path.join(T, name + ".png"))
        cube(assets, name)

    # portal frames: columns of the gods' stone with a rune glowing in each, base and cap only where a run ends
    for name, base, light, dark, accent in (("portal_frame_sky", (232, 228, 220), (252, 250, 244), (160, 152, 142), (255, 214, 90)),
                                            ("portal_frame_sea", (70, 150, 146), (150, 214, 206), (30, 80, 84), (120, 240, 240)),
                                            ("portal_frame_shade", (52, 44, 60), (96, 84, 110), (20, 16, 26), (196, 120, 255)),
                                            ("portal_frame_hub", (150, 140, 160), (200, 192, 210), (80, 72, 92), (200, 150, 255))):
        connected_column(assets, name, base, light, dark, accent)
        # a rune let into the middle of every side, glowing
        rune = ["..##..", ".#..#.", "#.##.#", "#.##.#", ".#..#.", "..##.."]
        for kind in ("single", "bottom", "middle", "top"):
            img = Image.open(os.path.join(T, f"{name}_{kind}.png")).convert("RGBA")
            for ry, row in enumerate(rune):
                for rx, ch in enumerate(row):
                    if ch == "#":
                        img.putpixel((5 + rx, 5 + ry), accent + (255,))
                    elif 0 < rx < 5 and 0 < ry < 5:
                        img.putpixel((5 + rx, 5 + ry), shade(dark, 0.8) + (255,))
            img.save(os.path.join(T, f"{name}_{kind}.png"))

    # the arrival sigil: a ring of gold round the three gods' colours, where travellers come to stand
    cols = [(255, 214, 100), (100, 228, 236), (200, 130, 255)]

    def sigil(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        a = (math.atan2(y - 7.5, x - 7.5) + math.pi) / math.tau
        c = shade((60, 56, 72), g[y][x])
        if 6.2 < r < 7.4: c = GOLD
        elif 4.6 < r <= 6.2: c = cols[int(a * 3) % 3]
        elif 3.6 < r <= 4.6: c = GOLD_LO
        elif r <= 2.2: c = (255, 250, 236)
        elif r <= 3.6 and int(a * 8) % 2: c = (150, 120, 220)
        return c
    paint_hi(sigil).save(os.path.join(T, "arrival_sigil.png"))
    paint_hi(lambda x, y: shade((60, 56, 72), g[y][x] * (1.2 if x in (0, 15) or y in (0, 15) else 1.0))).save(os.path.join(T, "arrival_sigil_side.png"))
    cube(assets, "arrival_sigil", side="arrival_sigil_side", top="arrival_sigil")

    recipes = {
        "cloudstone": ({"C": "minecraft:calcite", "W": "minecraft:white_wool"}, ["CC", "CW"], 3),
        "cloud_tile": ({"C": f"{MOD}:cloudstone", "G": "minecraft:gold_nugget"}, ["CC", "CG"], 3),
        "trench_stone": ({"D": "minecraft:deepslate", "G": "minecraft:glow_ink_sac"}, ["DD", "DG"], 3),
        "nacre_tile": ({"P": "minecraft:prismarine", "N": "minecraft:nautilus_shell"}, ["PPP", "PNP", "PPP"], 8),
        "tartarus_stone": ({"B": "minecraft:blackstone", "M": "minecraft:magma_block"}, ["BB", "BM"], 3),
        "throne_tile": ({"O": "minecraft:obsidian", "G": "minecraft:gold_ingot"}, ["OO", "OG"], 3),
        "portal_frame_sky": ({"M": f"{MOD}:olympian_marble", "G": "minecraft:glowstone_dust"}, ["M", "G", "M"], 2),
        "portal_frame_sea": ({"T": f"{MOD}:abyssal_tile", "S": "minecraft:sea_lantern"}, ["T", "S", "T"], 2),
        "portal_frame_shade": ({"B": f"{MOD}:underworld_brick", "C": "minecraft:crying_obsidian"}, ["B", "C", "B"], 2),
        "portal_frame_hub": ({"S": "minecraft:stone_bricks", "A": "minecraft:amethyst_shard"}, ["S", "A", "S"], 2),
        "arrival_sigil": ({"M": f"{MOD}:chaos_mosaic", "G": "minecraft:gold_ingot", "E": "minecraft:ender_pearl"}, ["MGM", "GEG", "MGM"], 2),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})
    for b in UPPER_BLOCKS:
        wj(os.path.join(data, f"loot_table/blocks/{b}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                                                                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


# ---- floors: bevelled tiles, and the breaks a floor gets with use ---------------------------------------------------------

def _px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c[:3]) + (255,))


def bevel(img, cell, grout_dark=0.55):
    """Each tile of a floor lit on its top and left edges and shadowed on its bottom and right, so it reads as a
    laid stone rather than a painted square."""
    out = img.copy()
    for y in range(16):
        for x in range(16):
            tx, ty = x % cell, y % cell
            c = img.getpixel((x, y))[:3]
            if tx == 0 or ty == 0:
                continue                                   # (the grout)
            if tx == 1 or ty == 1: c = shade(c, 1.1)
            elif tx == cell - 1 or ty == cell - 1: c = shade(c, 0.84)
            out.putpixel((x, y), c + (255,))
    return out


def crack(img, seed, lo=2, hi=13):
    """A jagged crack across the stone: a dark line with its lower lip catching the light."""
    rnd = random.Random(seed)
    out = img.copy()
    x, y = rnd.randrange(lo, hi), lo
    for _ in range(hi - lo + rnd.randrange(3)):
        c = out.getpixel((x, y))[:3]
        _px(out, x, y, shade(c, 0.45))
        _px(out, x + 1, y, shade(c, 1.12))
        if rnd.random() < 0.25:                            # a hair-crack off to the side
            _px(out, x - 1, y + 1, shade(c, 0.65))
        x = max(lo, min(hi, x + rnd.choice((-1, 0, 0, 1))))
        y += 1
        if y > hi: break
    return out


def chip(img, seed, cell, under=(40, 34, 40)):
    """A corner broken off one tile: the bed beneath shows, its broken edge lit."""
    rnd = random.Random(seed)
    out = img.copy()
    tx0, ty0 = rnd.randrange(16 // cell) * cell, rnd.randrange(16 // cell) * cell
    corner = rnd.randrange(4)
    size = max(2, cell // 2)
    for k in range(size):
        for j in range(size - k):
            x = tx0 + 1 + (k if corner in (0, 2) else cell - 2 - k)
            y = ty0 + 1 + (j if corner in (0, 1) else cell - 2 - j)
            _px(out, x, y, shade(under, 0.9 + 0.1 * ((x + y) % 2)))
            if j == size - k - 1:
                _px(out, x, y, shade(out.getpixel((min(15, x + 1), y))[:3], 1.15))
    return out


def sunk(img, seed, cell):
    """One tile settled a little lower than the rest: darker, its top edge in shadow."""
    rnd = random.Random(seed)
    out = img.copy()
    tx0, ty0 = rnd.randrange(16 // cell) * cell, rnd.randrange(16 // cell) * cell
    for y in range(ty0 + 1, ty0 + cell):
        for x in range(tx0 + 1, tx0 + cell):
            c = out.getpixel((x, y))[:3]
            out.putpixel((x, y), shade(c, 0.62 if (y == ty0 + 1 or x == tx0 + 1) else 0.86) + (255,))
    return out


def moss(img, seed, color=(86, 120, 60), n=14):
    rnd = random.Random(seed)
    out = img.copy()
    cx, cy = rnd.randrange(16), rnd.randrange(16)
    for _ in range(n):
        x, y = cx + rnd.randrange(-3, 4), cy + rnd.randrange(-3, 4)
        if 0 <= x < 16 and 0 <= y < 16:
            out.putpixel((x, y), mix(out.getpixel((x, y))[:3], color, 0.7) + (255,))
    return out


FLOORS = {
    # block: (tile size, the bed under a chipped tile, whether moss grows on it)
    "olympian_tiles": (8, (150, 140, 130), True), "cloud_tile": (8, (170, 184, 208), False), "nacre_tile": (8, (120, 150, 166), False),
    "throne_tile": (8, (60, 40, 20), False), "abyssal_tile": (8, (14, 40, 46), True), "bath_tile": (4, (150, 166, 176), True),
}
STONES = ["cloudstone", "trench_stone", "tartarus_stone", "storm_brick", "tide_brick", "ash_brick"]


def gen_floor_polish(assets):
    T = os.path.join(assets, "textures/block")
    for name, (cell, under, mossy) in FLOORS.items():
        base = bevel(_load(T, name), cell)
        imgs = [base, crack(base, 1), chip(base, 2, cell, under), sunk(base, 3, cell), crack(chip(base, 4, cell, under), 5)]
        weights = [8, 3, 2, 2, 1]
        if mossy:
            imgs.append(moss(base, 6))
            weights.append(2)
        variants(assets, name, imgs, weights)
    for name in STONES:
        base = _load(T, name)
        variants(assets, name, [base, crack(base, 11), crack(crack(base, 12), 13), _patch(base.copy(), 14, (40, 40, 40), 0.2)], [6, 2, 1, 2])


def connected_breaks(assets, name):
    """A connecting floor's every look given a cracked and a worn twin, chosen block by block like any variant."""
    import json as _json
    T = os.path.join(assets, "textures/block")
    for mask in range(16):
        base = _load(T, f"{name}_{mask}")
        crack(base, mask * 7 + 1, 4, 11).save(os.path.join(T, f"{name}_{mask}_v1.png"))
        sunk(base, mask * 7 + 2, 8).save(os.path.join(T, f"{name}_{mask}_v2.png"))
    path = os.path.join(assets, f"blockstates/{name}.json")
    with open(path) as f:
        state = _json.load(f)
    for key, entry in state["variants"].items():
        model_id = entry["model"] if isinstance(entry, dict) else entry[0]["model"]
        model_name = model_id.split("/")[-1]
        with open(os.path.join(assets, f"models/block/{model_name}.json")) as f:
            model = _json.load(f)
        out = [{"model": model_id, "weight": 6}]
        for v in (1, 2):
            twin = dict(model)
            twin["textures"] = {k: (t + f"_v{v}" if k != "particle" and t.split("/")[-1].startswith(name + "_") else t) for k, t in model["textures"].items()}
            wj(os.path.join(assets, f"models/block/{model_name}_v{v}.json"), twin)
            out.append({"model": f"{MOD}:block/{model_name}_v{v}", "weight": 2})
        state["variants"][key] = out
    wj(path, state)


# ---- stairs and slabs of the mod's stone; the bronze door and trapdoor ---------------------------------------------------

CUT_STONES = ["olympian_marble", "cloudstone", "trench_stone", "tartarus_stone", "storm_brick", "tide_brick", "ash_brick", "underworld_brick"]
DOORS = ["bronze_door", "bronze_trapdoor"]


def _template(name):
    import json as _json
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "templates", name + ".json")) as f:
        return _json.load(f)


def _retarget(obj, old, new):
    import json as _json
    return _json.loads(_json.dumps(obj).replace(old, new))


def gen_cuts(assets, data):
    for b in CUT_STONES:
        tex = {"bottom": f"{MOD}:block/{b}", "side": f"{MOD}:block/{b}", "top": f"{MOD}:block/{b}"}
        for suffix, parent in (("_stairs", "stairs"), ("_stairs_inner", "inner_stairs"), ("_stairs_outer", "outer_stairs"),
                               ("_slab", "slab"), ("_slab_top", "slab_top")):
            wj(os.path.join(assets, f"models/block/{b}{suffix}.json"), {"parent": f"minecraft:block/{parent}", "textures": tex})
        stairs = _retarget(_template("stairs"), "minecraft:block/stone_brick_stairs", f"{MOD}:block/{b}_stairs")
        wj(os.path.join(assets, f"blockstates/{b}_stairs.json"), stairs)
        slab = _retarget(_template("slab"), "minecraft:block/stone_brick_slab", f"{MOD}:block/{b}_slab")
        slab = _retarget(slab, "minecraft:block/stone_bricks", f"{MOD}:block/{b}")
        wj(os.path.join(assets, f"blockstates/{b}_slab.json"), slab)
        for kind in ("stairs", "slab"):
            wj(os.path.join(assets, f"items/{b}_{kind}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{b}_{kind}"}})
        wj(os.path.join(data, f"recipe/{b}_stairs.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": {"#": f"{MOD}:{b}"},
                                                            "pattern": ["#  ", "## ", "###"], "result": {"id": f"{MOD}:{b}_stairs", "count": 4}})
        wj(os.path.join(data, f"recipe/{b}_slab.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": {"#": f"{MOD}:{b}"},
                                                          "pattern": ["###"], "result": {"id": f"{MOD}:{b}_slab", "count": 6}})
        wj(os.path.join(data, f"loot_table/blocks/{b}_stairs.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}_stairs"}],
                                                                                                         "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        wj(os.path.join(data, f"loot_table/blocks/{b}_slab.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
            "type": "minecraft:item", "name": f"{MOD}:{b}_slab",
            "functions": [{"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:{b}_slab",
                                                                                           "properties": {"type": "double"}}]},
                          {"function": "minecraft:explosion_decay"}]}]}]})
    wj(os.path.join(RES_DATA_MC(data), "tags/block/stairs.json"), {"replace": False, "values": [f"{MOD}:{b}_stairs" for b in CUT_STONES]})
    wj(os.path.join(RES_DATA_MC(data), "tags/block/slabs.json"), {"replace": False, "values": [f"{MOD}:{b}_slab" for b in CUT_STONES]})
    wj(os.path.join(RES_DATA_MC(data), "tags/item/stairs.json"), {"replace": False, "values": [f"{MOD}:{b}_stairs" for b in CUT_STONES]})
    wj(os.path.join(RES_DATA_MC(data), "tags/item/slabs.json"), {"replace": False, "values": [f"{MOD}:{b}_slab" for b in CUT_STONES]})


def RES_DATA_MC(data):
    return os.path.join(os.path.dirname(data), "minecraft")


def gen_doors(assets, data):
    T = os.path.join(assets, "textures/block")
    TI = os.path.join(assets, "textures/item")
    g = grain(121, 0.05)
    BRONZE, DARK, LIGHT = (176, 110, 50), (96, 56, 24), (226, 160, 86)

    def door_half(top):
        def fn(x, y):
            c = shade(BRONZE, g[y][x])
            if x in (0, 15) or (y == 0 and top) or (y == 15 and not top): return DARK
            if x in (1, 14): return LIGHT if x == 1 else shade(DARK, 1.2)
            # two sunk panels, each with a boss: a sun on the upper, a meander band across the lower
            py0, py1 = (3, 13) if top else (2, 12)
            if 3 <= x <= 12 and py0 <= y <= py1:
                c = shade(BRONZE, 0.82 * g[y][x])
                if x == 3 or y == py0: c = shade(DARK, 1.0)
                if x == 12 or y == py1: c = LIGHT
                r = math.hypot(x - 7.5, y - (py0 + py1) / 2)
                if top and r < 2.4: c = GOLD_HI if r < 1.2 else GOLD
                if top and 3.2 < r < 4.0 and (x + y) % 2 == 0: c = GOLD
                if not top and y in (6, 7) and (x + (y - 6) * 2) % 4 < 2: c = GOLD
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): c = GOLD_HI                # studs
            if not top and x == 12 and y == 1: c = GOLD_HI                                # the handle
            if top and x == 12 and y == 14: c = GOLD_HI
            return c
        return fn
    paint_hi(door_half(True)).save(os.path.join(T, "bronze_door_top.png"))
    paint_hi(door_half(False)).save(os.path.join(T, "bronze_door_bottom.png"))
    # the trapdoor: a studded bronze hatch, a ring to lift it by
    def hatch(x, y):
        if x in (0, 15) or y in (0, 15): return DARK
        if x in (1, 14) or y in (1, 14): return LIGHT if x == 1 or y == 1 else shade(DARK, 1.2)
        c = shade(BRONZE, g[y][x] * (0.9 if (x // 4 + y // 4) % 2 else 1.0))
        if x % 4 == 2 and y % 4 == 2: c = GOLD_HI
        if 6 <= x <= 9 and y in (6, 9) or 6 <= y <= 9 and x in (6, 9): c = (60, 40, 20)
        return c
    paint_hi(hatch).save(os.path.join(T, "bronze_trapdoor.png"))
    # the door as an item
    item = Image.new("RGBA", (16, 16))
    top, bottom = _load(T, "bronze_door_top"), _load(T, "bronze_door_bottom")
    for y in range(16):
        for x in range(4, 12):
            src = top if y < 8 else bottom
            item.putpixel((x, y), src.getpixel((2 + (x - 4) * 12 // 8, (y % 8) * 2)))
    for y in range(16):
        item.putpixel((4, y), DARK + (255,)); item.putpixel((11, y), DARK + (255,))
    item.save(os.path.join(TI, "bronze_door.png"))
    door = _retarget(_template("door"), "minecraft:block/iron_door", f"{MOD}:block/bronze_door")
    wj(os.path.join(assets, "blockstates/bronze_door.json"), door)
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right", "top_right_open"):
        wj(os.path.join(assets, f"models/block/bronze_door_{part}.json"),
           {"parent": f"minecraft:block/door_{part}", "textures": {"bottom": f"{MOD}:block/bronze_door_bottom", "top": f"{MOD}:block/bronze_door_top"}})
    wj(os.path.join(assets, "models/block/bronze_door.json"), {"parent": f"{MOD}:block/bronze_door_bottom_left"})
    wj(os.path.join(assets, "models/item/bronze_door.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/bronze_door"}})
    wj(os.path.join(assets, "items/bronze_door.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/bronze_door"}})
    trap = _retarget(_template("trapdoor"), "minecraft:block/iron_trapdoor", f"{MOD}:block/bronze_trapdoor")
    wj(os.path.join(assets, "blockstates/bronze_trapdoor.json"), trap)
    for part, parent in (("bottom", "template_trapdoor_bottom"), ("top", "template_trapdoor_top"), ("open", "template_trapdoor_open")):
        wj(os.path.join(assets, f"models/block/bronze_trapdoor_{part}.json"), {"parent": f"minecraft:block/{parent}", "textures": {"texture": f"{MOD}:block/bronze_trapdoor"}})
    wj(os.path.join(assets, "models/block/bronze_trapdoor.json"), {"parent": f"{MOD}:block/bronze_trapdoor_bottom"})
    wj(os.path.join(assets, "items/bronze_trapdoor.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/bronze_trapdoor_bottom"}})
    wj(os.path.join(data, "recipe/bronze_door.json"), {"type": "minecraft:crafting_shaped", "category": "redstone", "key": {"C": "minecraft:copper_ingot", "G": "minecraft:gold_nugget"},
                                                       "pattern": ["CC", "CG", "CC"], "result": {"id": f"{MOD}:bronze_door", "count": 3}})
    wj(os.path.join(data, "recipe/bronze_trapdoor.json"), {"type": "minecraft:crafting_shaped", "category": "redstone", "key": {"C": "minecraft:copper_ingot", "G": "minecraft:gold_nugget"},
                                                           "pattern": ["CC", "CG"], "result": {"id": f"{MOD}:bronze_trapdoor", "count": 2}})
    wj(os.path.join(data, "loot_table/blocks/bronze_door.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:bronze_door"}],
        "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:bronze_door", "properties": {"half": "lower"}}, {"condition": "minecraft:survives_explosion"}]}]})
    wj(os.path.join(data, "loot_table/blocks/bronze_trapdoor.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:bronze_trapdoor"}],
                                                                                                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    wj(os.path.join(RES_DATA_MC(data), "tags/block/doors.json"), {"replace": False, "values": [f"{MOD}:bronze_door"]})
    wj(os.path.join(RES_DATA_MC(data), "tags/block/trapdoors.json"), {"replace": False, "values": [f"{MOD}:bronze_trapdoor"]})


# ---- doors: a proper panelled leaf, one for each tower and the bronze one --------------------------------------------------

EMBLEMS = {
    "shield": [".######.", "#++##++#", "#++##++#", "########", "#++##++#", ".#+##+#.", "..#++#..", "...##..."],
    "laurel": ["#......#", "##....##", ".#....#.", "##....##", ".##..##.", "..####..", "...##...", "..#..#.."],
    "star": ["...##...", "...##...", "########", ".######.", "..####..", ".##..##.", "##....##", "........"],
    "sun": ["..#..#..", "...##...", "#.####.#", ".######.", ".######.", "#.####.#", "...##...", "..#..#.."],
    "bolt": ["....##..", "...##...", "..##....", ".######.", "....##..", "...##...", "..##....", ".##....."],
    "trident": ["#..##..#", "#..##..#", "#..##..#", "########", "...##...", "...##...", "...##...", "...##..."],
    "skull": [".######.", "########", "##.##.##", "##.##.##", "########", ".##..##.", "..####..", "..#..#.."],
}

DOOR_SPECS = {
    # name: (field, frame, band, stud, emblem colour, emblem, style)
    "bronze": ((176, 110, 50), (96, 56, 24), (226, 160, 86), GOLD_HI, GOLD, "sun", "metal"),
    "sky": ((236, 232, 222), (168, 160, 148), GOLD, GOLD_HI, (240, 196, 60), "bolt", "stone"),
    "sea": ((54, 104, 108), (24, 54, 60), (130, 200, 190), (236, 236, 226), (150, 230, 220), "trident", "planks"),
    "shade": ((52, 40, 52), (22, 16, 24), (110, 96, 110), (190, 120, 240), (200, 140, 255), "skull", "planks"),
    "garrison": ((92, 62, 38), (40, 28, 20), (118, 120, 128), (178, 182, 190), (200, 60, 50), "shield", "planks"),
    "verdigris": ((88, 160, 132), (46, 98, 80), (176, 116, 62), GOLD_HI, (226, 190, 110), "laurel", "metal"),
    "ivory": ((236, 228, 206), (186, 170, 140), GOLD, GOLD_HI, (190, 150, 60), "star", "stone"),
}


def door_textures(field, frame, band, stud, glow, emblem, style, seed):
    g = grain(seed, 0.05)

    def surface(x, y):
        if style == "planks":
            c = shade(field, g[y][x] * (0.8 if x % 4 == 3 else 1.06 if x % 4 == 0 else 1.0))
        elif style == "stone":
            c = shade(field, g[y][x] * (0.97 + 0.03 * ((x + y) % 3)))
        else:
            c = shade(field, g[y][x] * (1.08 if (x + y) % 7 == 0 else 1.0))
        return c

    def leaf(top):
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = surface(x, y)
                # the stiles and rails: a frame round the leaf, lit on its left, shadowed on its right
                if x <= 1 or x >= 14:
                    c = shade(frame, 1.25 if x == 1 else 0.75 if x == 14 else 1.0)
                if (top and y <= 1) or (not top and y >= 14):
                    c = shade(frame, 1.25 if (top and y == 1) else 0.75 if (not top and y == 14) else 1.0)
                # a rail across the middle of the door: the bottom of the upper leaf, the top of the lower one
                if (top and y >= 14) or (not top and y <= 1):
                    c = band if (top and y == 14) or (not top and y == 1) else shade(frame, 1.0)
                # the sunk panel: shadowed on its top and left edge, lit on its bottom and right
                px0, px1 = 3, 12
                py0, py1 = (3, 12) if top else (3, 12)
                if px0 <= x <= px1 and py0 <= y <= py1:
                    if x == px0 or y == py0: c = shade(frame, 0.9)
                    elif x == px1 or y == py1: c = shade(field, 1.25)
                    else: c = shade(surface(x, y), 0.9)
                # the emblem raised in the upper panel; a lozenge and bands in the lower
                if top:
                    ex, ey = x - 4, y - 4
                    if 0 <= ex < 8 and 0 <= ey < 8 and EMBLEMS[emblem][ey][ex] == "#":
                        c = glow if (ex + ey) % 4 else shade(glow, 1.15)
                else:
                    d = abs(x - 7.5) + abs(y - 7.5)
                    if 3.0 <= d < 4.0: c = glow
                    if d < 1.5: c = shade(glow, 1.1)
                # studs at the corners of the panel, hinges on the left stile, a ring on the right
                if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): c = stud
                if x <= 1 and y in ((4, 5) if top else (10, 11)): c = shade(band, 0.9)
                if top and x == 12 and y == 14: c = stud
                if not top and x in (11, 12, 13) and y == 2: c = stud
                img.putpixel((x, y), tuple(c[:3]) + (255,))
        return img

    hatch = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = surface(x, y)
            e = min(x, y, 15 - x, 15 - y)
            if e <= 1: c = shade(frame, 1.25 if (x == 1 or y == 1) and e == 1 else 0.75 if e == 1 else 1.0)
            elif e == 2: c = band if (x + y) % 2 else shade(band, 0.85)
            ex, ey = x - 4, y - 4
            if 0 <= ex < 8 and 0 <= ey < 8 and EMBLEMS[emblem][ey][ex] == "#": c = glow
            if (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): c = stud
            hatch.putpixel((x, y), tuple(c[:3]) + (255,))
    return leaf(True), leaf(False), hatch


def gen_doors2(assets, data):
    T = os.path.join(assets, "textures/block")
    TI = os.path.join(assets, "textures/item")
    for n, (key, spec) in enumerate(DOOR_SPECS.items()):
        door, trapdoor = f"{key}_door", f"{key}_trapdoor"
        top, bottom, hatch = door_textures(*spec, seed=131 + n)
        top.save(os.path.join(T, f"{door}_top.png"))
        bottom.save(os.path.join(T, f"{door}_bottom.png"))
        hatch.save(os.path.join(T, f"{trapdoor}.png"))
        # the door as an item: drawn for its own size, a narrow leaf standing up with its two panels and emblem
        field, frame, band, stud, glow = spec[0], spec[1], spec[2], spec[3], spec[4]
        item = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(3, 13):
                if x in (3, 12) or y in (0, 15): c = shade(frame, 0.7)
                elif x == 4 or y == 1: c = shade(frame, 1.3)
                elif x == 11 or y == 14: c = frame
                elif y in (7, 8): c = band if y == 7 else shade(band, 0.75)
                else:
                    c = shade(field, 1.0)
                    upper = 2 <= y <= 6
                    if (upper or 9 <= y <= 13) and (x in (5, 10) or y in (2, 9) or y in (6, 13)):
                        c = shade(frame, 0.95) if x == 5 or y in (2, 9) else shade(field, 1.25)
                    if upper and 6 <= x <= 9 and 3 <= y <= 5 and (x, y) not in ((6, 3), (9, 3), (6, 5), (9, 5)): c = glow
                    if not upper and (x, y) in ((7, 11), (8, 11), (7, 10), (8, 12)): c = glow
                if (x, y) == (10, 8): c = stud
                item.putpixel((x, y), tuple(c[:3]) + (255,))
        item.save(os.path.join(TI, f"{door}.png"))
        wj(os.path.join(assets, f"blockstates/{door}.json"), _retarget(_template("door"), "minecraft:block/iron_door", f"{MOD}:block/{door}"))
        for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open", "top_left", "top_left_open", "top_right", "top_right_open"):
            wj(os.path.join(assets, f"models/block/{door}_{part}.json"),
               {"parent": f"minecraft:block/door_{part}", "textures": {"bottom": f"{MOD}:block/{door}_bottom", "top": f"{MOD}:block/{door}_top"}})
        wj(os.path.join(assets, f"models/block/{door}.json"), {"parent": f"{MOD}:block/{door}_bottom_left"})
        wj(os.path.join(assets, f"models/item/{door}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{door}"}})
        wj(os.path.join(assets, f"items/{door}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{door}"}})
        wj(os.path.join(assets, f"blockstates/{trapdoor}.json"), _retarget(_template("trapdoor"), "minecraft:block/iron_trapdoor", f"{MOD}:block/{trapdoor}"))
        for part, parent in (("bottom", "template_trapdoor_bottom"), ("top", "template_trapdoor_top"), ("open", "template_trapdoor_open")):
            wj(os.path.join(assets, f"models/block/{trapdoor}_{part}.json"), {"parent": f"minecraft:block/{parent}", "textures": {"texture": f"{MOD}:block/{trapdoor}"}})
        wj(os.path.join(assets, f"models/block/{trapdoor}.json"), {"parent": f"{MOD}:block/{trapdoor}_bottom"})
        wj(os.path.join(assets, f"items/{trapdoor}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{trapdoor}_bottom"}})
        mat = {"bronze": ("minecraft:copper_ingot", "minecraft:gold_nugget"), "sky": (f"{MOD}:olympian_marble", "minecraft:gold_nugget"),
               "sea": ("minecraft:dark_oak_planks", "minecraft:prismarine_shard"), "shade": ("minecraft:crimson_planks", "minecraft:amethyst_shard"),
               "garrison": ("minecraft:dark_oak_planks", "minecraft:iron_nugget"), "verdigris": ("minecraft:oxidized_copper", "minecraft:gold_nugget"),
               "ivory": ("minecraft:bone_block", "minecraft:gold_nugget")}[key]
        wj(os.path.join(data, f"recipe/{door}.json"), {"type": "minecraft:crafting_shaped", "category": "redstone", "key": {"C": mat[0], "G": mat[1]},
                                                       "pattern": ["CC", "CG", "CC"], "result": {"id": f"{MOD}:{door}", "count": 3}})
        wj(os.path.join(data, f"recipe/{trapdoor}.json"), {"type": "minecraft:crafting_shaped", "category": "redstone", "key": {"C": mat[0], "G": mat[1]},
                                                           "pattern": ["CC", "CG"], "result": {"id": f"{MOD}:{trapdoor}", "count": 2}})
        wj(os.path.join(data, f"loot_table/blocks/{door}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{door}"}],
            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{MOD}:{door}", "properties": {"half": "lower"}}, {"condition": "minecraft:survives_explosion"}]}]})
        wj(os.path.join(data, f"loot_table/blocks/{trapdoor}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{trapdoor}"}],
                                                                                                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    keys = list(DOOR_SPECS)
    wj(os.path.join(RES_DATA_MC(data), "tags/block/doors.json"), {"replace": False, "values": [f"{MOD}:{k}_door" for k in keys]})
    wj(os.path.join(RES_DATA_MC(data), "tags/block/trapdoors.json"), {"replace": False, "values": [f"{MOD}:{k}_trapdoor" for k in keys]})
    wj(os.path.join(RES_DATA_MC(data), "tags/item/doors.json"), {"replace": False, "values": [f"{MOD}:{k}_door" for k in keys]})
    wj(os.path.join(RES_DATA_MC(data), "tags/item/trapdoors.json"), {"replace": False, "values": [f"{MOD}:{k}_trapdoor" for k in keys]})


# ---- the proving grounds and barracks: arena sand, beast gates, war drums, standards, training dummies ------------------

COMBAT_BLOCKS = ["arena_sand", "beast_gate", "war_drum", "war_standard", "training_dummy"]


def _el(a, b, faces, rot=None):
    """One box of a model, each face its own texture (by key); faces missing from the map are left off."""
    el = {"from": list(a), "to": list(b), "faces": {d: {"texture": f"#{t}"} for d, t in faces.items()}}
    if rot:
        el["rotation"] = rot
    return el


def _all(t, **over):
    f = {d: t for d in ("north", "south", "east", "west", "up", "down")}
    f.update(over)
    return f


def _facing_block(assets, name, model):
    """A block that faces the way it was set: one model, turned for each facing."""
    wj(os.path.join(assets, f"models/block/{name}.json"), model)
    wj(os.path.join(assets, f"blockstates/{name}.json"),
       {"variants": {f"facing={f}": {"model": f"{MOD}:block/{name}", "y": r} for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})


def gen_combat(assets, data):
    T = os.path.join(assets, "textures/block")
    t = lambda n: f"{MOD}:block/{n}"
    g = grain(141, 0.06)
    rnd = random.Random(141)

    # arena sand: raked in long furrows; one look scuffed with footprints, one with stones turned up and an old stain
    sand = (220, 198, 146)
    grit = random.Random(142)
    speck = [[grit.random() for _ in range(16)] for _ in range(16)]

    def raked(x, y):
        # grains of three tones, as sand is; the rake's furrows only a faint swell across it, never a seam
        v = speck[y][x]
        c = shade(sand, 0.9 if v < 0.18 else 1.07 if v > 0.86 else 1.0)
        if v < 0.04: c = (172, 150, 112)
        swell = math.sin((y + 1.6 * math.sin(x * 0.55)) * math.tau / 5.0)
        if swell > 0.8: c = shade(c, 0.94)
        return c
    base = paint_hi(raked)
    steps = base.copy()
    for fx, fy in ((3, 3), (9, 9)):
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (0, 2), (1, 2), (2, 1), (0, -1), (1, -1)):
            if 0 <= fx + dx < 16 and 0 <= fy + dy < 16:
                steps.putpixel((fx + dx, fy + dy), tuple(shade(sand, 0.72)) + (255,))
    stones = base.copy()
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        stones.putpixel((x, y), tuple(shade((150, 140, 128), 1.0 + rnd.random() * 0.2)) + (255,))
        if x + 1 < 16: stones.putpixel((x + 1, y), tuple(shade((120, 112, 104), 1.0)) + (255,))
    for x in range(10, 14):
        for y in range(2, 5):
            if (x - 12) ** 2 + (y - 3) ** 2 <= 3:
                stones.putpixel((x, y), tuple(mix(shade(sand, 0.9), (120, 50, 40), 0.35)) + (255,))
    variants(assets, "arena_sand", [base, steps, stones], [6, 2, 2], turn=True)
    wj(os.path.join(assets, "items/arena_sand.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/arena_sand"}})

    # the beast gate: a portcullis of black iron over darkness, rivets where its bars cross; behind one, eyes
    def gate(x, y):
        bar_x, bar_y = x in (1, 5, 10, 14), y in (2, 7, 13)
        if x in (0, 15) or y in (0, 15): return (52, 48, 50)
        if bar_x and bar_y: return (150, 146, 150)
        if bar_x: return (92, 88, 94) if y % 3 else (72, 68, 74)
        if bar_y: return (84, 80, 86) if x % 3 else (66, 62, 68)
        return shade((22, 16, 16), 0.8 + 0.4 * g[y][x] - 0.4)
    plain = paint_hi(gate)
    eyes = plain.copy()
    for ex, ey, col in ((7, 10, (255, 120, 40)), (8, 10, (255, 200, 90)), (11, 10, (255, 200, 90)), (12, 10, (255, 120, 40))):
        eyes.putpixel((ex, ey), col + (255,))
    clawed = plain.copy()
    for y in range(3, 12):
        for x0 in (6, 8):
            x = x0 + (y // 3) % 2
            if x not in (1, 5, 10, 14) and y % 2: clawed.putpixel((x, y), (120, 112, 104, 255))
    variants(assets, "beast_gate", [plain, eyes, clawed], [6, 1, 2])
    wj(os.path.join(assets, "items/beast_gate.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/beast_gate"}})

    # the war drum: red hide laced in a zigzag of cord between bronze hoops; a pale skin on top, a mark burned in it
    hide = (150, 42, 34)

    def drum_side(x, y):
        c = shade(hide, g[y][x])
        zig = 5 + abs((x % 8) - 4) * 2
        if abs(y - zig) < 1 or abs(y - (zig - 1)) < 1 and x % 2 == 0: c = (226, 206, 160)
        if y <= 4: c = shade((176, 110, 50), g[y][x] * (1.15 if y == 4 else 1.0))
        return c

    def drum_top(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        if r > 5.6: return shade((176, 110, 50), 1.1 if (x + y) % 3 == 0 else 0.95)
        c = shade((228, 206, 168), g[y][x])
        if 1.6 < r < 2.6: c = (130, 40, 30)
        if r < 0.9: c = (130, 40, 30)
        return c
    paint_hi(drum_side).save(os.path.join(T, "war_drum_side.png"))
    paint_hi(drum_top).save(os.path.join(T, "war_drum_top.png"))
    tx = {"particle": t("war_drum_side"), "hide": t("war_drum_side"), "skin": t("war_drum_top"), "bronze": t("bronze")}
    drum = [_el((3, 1, 3), (13, 12, 13), _all("hide", up="skin", down="bronze")),
            _el((2.5, 0, 2.5), (13.5, 2, 13.5), _all("bronze")),
            _el((2.5, 11, 2.5), (13.5, 13, 13.5), _all("bronze", up="skin")),
            # four feet
            _el((2, 0, 2), (4, 1, 4), _all("bronze")), _el((12, 0, 2), (14, 1, 4), _all("bronze")),
            _el((2, 0, 12), (4, 1, 14), _all("bronze")), _el((12, 0, 12), (14, 1, 14), _all("bronze"))]
    wj(os.path.join(assets, "models/block/war_drum.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": drum})
    wj(os.path.join(assets, "blockstates/war_drum.json"), {"variants": {"": {"model": f"{MOD}:block/war_drum"}}})
    wj(os.path.join(assets, "items/war_drum.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/war_drum"}})

    # the war standard: a crimson cloth with a gold border and a gold helm on it, hung from a gilded bar on a spear
    def cloth(x, y):
        c = shade((158, 30, 36), 0.92 + 0.16 * g[y][x])
        if x in (0, 15) or y == 0: c = GOLD
        if y == 15: c = GOLD if x % 2 == 0 else (110, 20, 26)
        # a crested helm in profile, facing left: plume, dome, the eye slot and cheek guard, the neck guard behind
        rows = ["................", "................", ".....#####......", "....#######.....", "......####......", ".....######.....",
                "....########....", "....#..#####....", "....##.#####....", "....##..####....", ".....#...###....", "................"]
        helm = {(i, j) for j, row in enumerate(rows) for i, ch in enumerate(row) if ch == "#"}
        if (x, y) in helm: c = (200, 40, 36) if y <= 3 else GOLD_HI if y <= 5 else GOLD
        if y == 12 and 3 <= x <= 12: c = GOLD
        return c
    paint_hi(cloth).save(os.path.join(T, "war_standard.png"))
    paint_hi(lambda x, y: shade((96, 64, 38), g[y][x] * (0.86 if y % 4 == 3 else 1.0))).save(os.path.join(T, "standard_pole.png"))
    paint_hi(lambda x, y: GOLD_HI if (x + y) % 4 == 0 else GOLD).save(os.path.join(T, "standard_gold.png"))
    paint_hi(lambda x, y: mix((150, 156, 170), (236, 240, 248), (15 - y + x) / 30)).save(os.path.join(T, "standard_iron.png"))
    tx = {"particle": t("war_standard"), "cloth": t("war_standard"), "pole": t("standard_pole"), "gold": t("standard_gold"), "iron": t("standard_iron")}
    standard = [_el((7.5, 0, 7.5), (8.5, 15, 8.5), _all("pole")),
                _el((6, 0, 6), (10, 1, 10), _all("gold")),
                _el((7.25, 15, 7.25), (8.75, 16, 8.75), _all("iron")),
                _el((2.5, 13.5, 6.5), (13.5, 14.5, 7.5), _all("gold")),
                _el((3, 2, 6.75), (13, 13.5, 7.25), _all("cloth"))]
    _facing_block(assets, "war_standard", {"parent": "minecraft:block/block", "textures": tx, "elements": standard})

    # the training dummy: a straw sack on a post with a target on its chest, a crossbar for arms, a burlap head
    straw = (206, 176, 86)

    def straw_fn(x, y):
        c = shade(straw, g[y][x])
        if (x * 3 + y) % 7 == 0: c = shade(straw, 1.18)
        if (x + y * 2) % 9 == 0: c = shade(straw, 0.8)
        return c

    def target(x, y):
        c = straw_fn(x, y)
        r = math.hypot(x - 7.5, y - 7.5)
        if r < 6.0: c = (196, 44, 40) if int(r / 1.6) % 2 == 0 else (236, 226, 204)
        return c

    def burlap(x, y):
        return shade((168, 136, 96), (1.08 if (x + y) % 2 else 0.92) * g[y][x])

    def face(x, y):
        c = burlap(x, y)
        if (x, y) in ((4, 5), (5, 6), (6, 5), (4, 7), (6, 7), (9, 5), (10, 6), (11, 5), (9, 7), (11, 7)): c = (50, 34, 26)
        if y == 10 and 5 <= x <= 10: c = (90, 60, 40) if x % 2 else (50, 34, 26)
        return c
    paint_hi(straw_fn).save(os.path.join(T, "dummy_straw.png"))
    paint_hi(target).save(os.path.join(T, "dummy_target.png"))
    paint_hi(burlap).save(os.path.join(T, "dummy_burlap.png"))
    paint_hi(face).save(os.path.join(T, "dummy_face.png"))
    paint_hi(lambda x, y: (96, 56, 40) if (x + y) % 3 else (70, 40, 30)).save(os.path.join(T, "dummy_rope.png"))
    tx = {"particle": t("dummy_straw"), "straw": t("dummy_straw"), "target": t("dummy_target"), "burlap": t("dummy_burlap"), "face": t("dummy_face"),
          "rope": t("dummy_rope"), "pole": t("standard_pole")}
    dummy = [_el((5, 0, 5), (11, 1, 11), _all("pole")),
             _el((7, 1, 7), (9, 5, 9), _all("pole")),
             _el((4, 5, 5.5), (12, 12, 10.5), _all("straw", north="target")),
             _el((3.9, 7, 5.4), (12.1, 7.75, 10.6), _all("rope")),
             _el((0.5, 9.5, 7.25), (15.5, 10.5, 8.75), _all("pole")),
             _el((5.5, 12, 6), (10.5, 16, 10), _all("burlap", north="face"))]
    _facing_block(assets, "training_dummy", {"parent": "minecraft:block/block", "textures": tx, "elements": dummy})

    recipes = {
        "arena_sand": ({"S": "minecraft:sand", "G": "minecraft:gravel"}, ["SG", "GS"], 4),
        "beast_gate": ({"I": "minecraft:iron_ingot", "B": "minecraft:iron_bars"}, ["IBI", "BBB", "IBI"], 4),
        "war_drum": ({"L": "minecraft:leather", "N": "minecraft:note_block", "C": "minecraft:copper_ingot"}, [" L ", "CNC"], 1),
        "war_standard": ({"G": "minecraft:gold_ingot", "W": "minecraft:red_wool", "S": "minecraft:stick"}, ["GWG", " S ", " S "], 1),
        "training_dummy": ({"H": "minecraft:hay_block", "S": "minecraft:stick", "W": "minecraft:white_wool"}, [" W ", "SHS", " S "], 1),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})


# ---- dummies of every height, sparring dummies, the barracks' furniture, and the mod's glass -------------------------

FURNISH2 = ["moving_dummy", "chair", "table", "bench", "dresser", "bunk_bed"]
GLASS = ["reinforced_glass", "frosted_glass", "smart_glass", "veil_glass", "one_way_glass"]


def _mp_variants(assets, name, base_states, entries):
    wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": entries})


def gen_furnish2(assets, data):
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    g = grain(151, 0.05)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def save(name, fn):
        paint_hi(fn).save(os.path.join(T, name + ".png"))

    # ---- woods and cloths
    oak = (112, 76, 46)
    save("furniture_oak", lambda x, y: shade(oak, g[y][x] * (0.84 if y % 4 == 3 else 1.06 if y % 4 == 0 else 1.0)))
    save("furniture_oak_end", lambda x, y: shade(oak, g[y][x] * (0.8 if int(math.hypot(x - 7.5, y - 7.5)) % 3 == 0 else 1.0)))
    save("furniture_dark", lambda x, y: shade((66, 44, 28), g[y][x] * (0.84 if x % 4 == 3 else 1.0)))
    save("chair_cushion", lambda x, y: shade((150, 40, 36), (1.1 if (x + y) % 4 == 0 else 1.0) * (0.85 if x in (0, 15) or y in (0, 15) else 1.0)))
    save("bunk_mattress", lambda x, y: shade((226, 218, 196), 0.92 if x % 5 == 0 or y % 5 == 0 else 1.0))
    save("bunk_blanket", lambda x, y: (60, 70, 120) if y % 6 in (2, 3) else shade((150, 36, 34), g[y][x]))
    save("bunk_pillow", lambda x, y: shade((240, 236, 226), 0.9 if x in (0, 15) or y in (0, 15) else 1.0))
    save("dummy_bronze", lambda x, y: shade((176, 110, 50), g[y][x] * (1.25 if (x + y) % 6 == 0 else 1.0)))
    save("dummy_crest", lambda x, y: shade((190, 30, 30), 1.15 if y % 3 == 0 else 1.0))
    save("dummy_sledge", lambda x, y: shade((84, 58, 36), g[y][x] * (0.8 if x % 5 == 4 else 1.0)))

    def drawer_front(right):
        def fn(x, y):
            c = shade(oak, g[y][x])
            # (the outer frame on the half's own outside edge; the two halves meet in a plain joint)
            outer = x == (15 if right else 0)
            if outer or y in (0, 15): return shade((66, 44, 28), 1.0)
            if x == (0 if right else 15): return shade(oak, 0.8)
            # three drawers, each with a shadowed lower edge and a brass pull in the middle
            if y in (5, 10): return shade((66, 44, 28), 0.9)
            if y in (4, 9, 14): return shade(oak, 0.8)
            if y in (1, 6, 11): return shade(oak, 1.18)
            row = y // 5
            px = 3 if right else 12
            if x in (px - 1, px, px + 1) and y in (2 + 5 * row, 3 + 5 * row): return GOLD_HI if x == px else GOLD
            return c
        return fn
    save("dresser_front", drawer_front(False))
    save("dresser_front_r", drawer_front(True))
    save("dresser_inside", lambda x, y: (40, 26, 18) if (x in (0, 15) or y in (0, 15)) else (220, 210, 190) if (x // 3 + y // 3) % 2 else (180, 60, 50))

    # ---- training dummies: one block, or a post, a body and a head up to three high
    tx = {"particle": t("dummy_straw"), "straw": t("dummy_straw"), "target": t("dummy_target"), "burlap": t("dummy_burlap"), "face": t("dummy_face"),
          "rope": t("dummy_rope"), "pole": t("standard_pole"), "bronze": t("dummy_bronze"), "crest": t("dummy_crest"), "sledge": t("dummy_sledge")}
    foot_base = [_el((4, 0, 4), (12, 1, 12), _all("pole")), _el((1, 0, 7), (15, 1.5, 9), _all("pole")), _el((7, 0, 1), (9, 1.5, 15), _all("pole"))]
    helm = lambda y0, x0=4.5, x1=11.5, z0=5, z1=11: [_el((x0 - 0.5, y0, z0 - 0.5), (x1 + 0.5, y0 + 3, z1 + 0.5), _all("bronze")),
                                                       _el((7.5, y0 + 3, z0), (8.5, y0 + 5, z1 + 1), _all("crest"))]
    parts = {
        "single": foot_base + [_el((7, 1, 7), (9, 5, 9), _all("pole")),
                               _el((4, 5, 5.5), (12, 12, 10.5), _all("straw", north="target")),
                               _el((3.9, 7, 5.4), (12.1, 7.75, 10.6), _all("rope")),
                               _el((0.5, 9.5, 7.25), (15.5, 10.5, 8.75), _all("pole")),
                               _el((5.5, 12, 6), (10.5, 16, 10), _all("burlap", north="face"))],
        "foot": foot_base + [_el((7, 1, 7), (9, 16, 9), _all("pole")),
                             _el((6.5, 6, 6.5), (9.5, 7, 9.5), _all("rope"))],
        "upper": [_el((7, 0, 7), (9, 3, 9), _all("pole")),
                  _el((3.5, 3, 5), (12.5, 12, 11), _all("straw", north="target")),
                  _el((3.4, 5, 4.9), (12.6, 5.75, 11.1), _all("rope")),
                  _el((0, 9.5, 7.25), (16, 11, 8.75), _all("pole")),
                  _el((5, 12, 6), (11, 16, 10), _all("burlap", north="face"))],
        "torso": [_el((7, 0, 7), (9, 2, 9), _all("pole")),
                  _el((2.5, 2, 4), (13.5, 16, 12), _all("straw", north="target")),
                  _el((2.4, 6, 3.9), (13.6, 6.75, 12.1), _all("rope")),
                  _el((2.4, 12, 3.9), (13.6, 12.75, 12.1), _all("rope")),
                  _el((-2, 12.5, 7), (18, 14, 9), _all("pole")),
                  # a practice shield strapped to its left arm
                  _el((14.5, 6, 3.5), (15.5, 14, 12.5), _all("pole", west="sledge", east="sledge"))],
        "head": [_el((7, 0, 7), (9, 2, 9), _all("pole")),
                 _el((4.5, 2, 5), (11.5, 10, 11), _all("burlap", north="face"))] + helm(8),
    }
    for part, els in parts.items():
        wj(os.path.join(M, f"training_dummy_{part}.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": els})
    wj(os.path.join(M, "training_dummy.json"), {"parent": f"{MOD}:block/training_dummy_single"})
    wj(os.path.join(assets, "blockstates/training_dummy.json"),
       {"variants": {f"facing={f},part={p}": {"model": f"{MOD}:block/training_dummy_{p}", "y": r} for f, r in rot.items() for p in parts}})
    wj(os.path.join(assets, "items/training_dummy.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/training_dummy_single"}})

    # the sparring dummy rides a sledge with runners
    sledge = [_el((1, 0, 2), (15, 1, 4), _all("pole")), _el((1, 0, 12), (15, 1, 14), _all("pole")), _el((2, 1, 2), (14, 2.5, 14), _all("sledge")),
              _el((7, 2.5, 7), (9, 5, 9), _all("pole")),
              _el((4, 5, 5.5), (12, 12, 10.5), _all("straw", north="target")),
              _el((3.9, 8, 5.4), (12.1, 8.75, 10.6), _all("crest")),
              _el((0.5, 10, 7.25), (15.5, 11, 8.75), _all("pole")),
              _el((5.5, 12, 6), (10.5, 15.5, 10), _all("burlap", north="face"))]
    wj(os.path.join(M, "moving_dummy.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": sledge})
    wj(os.path.join(assets, "blockstates/moving_dummy.json"), {"variants": {f"facing={f}": {"model": f"{MOD}:block/moving_dummy", "y": (r + 90) % 360} for f, r in rot.items()}})
    wj(os.path.join(assets, "items/moving_dummy.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/moving_dummy"}})

    # ---- the chair: four legs, a cushioned seat, a back of two posts and three slats
    ftx = {"particle": t("furniture_oak"), "oak": t("furniture_oak"), "end": t("furniture_oak_end"), "dark": t("furniture_dark"), "cushion": t("chair_cushion"),
           "front": t("dresser_front"), "front_r": t("dresser_front_r"), "inside": t("dresser_inside"), "mattress": t("bunk_mattress"),
           "blanket": t("bunk_blanket"), "pillow": t("bunk_pillow")}
    chair = [_el((2, 0, 2), (4, 8, 4), _all("dark")), _el((12, 0, 2), (14, 8, 4), _all("dark")), _el((2, 0, 12), (4, 8, 12 + 2), _all("dark")),
             _el((12, 0, 12), (14, 8, 14), _all("dark")),
             _el((1.5, 8, 1.5), (14.5, 9.5, 14.5), _all("oak", up="end")), _el((2.5, 9.5, 2.5), (13.5, 10.5, 12), _all("cushion")),
             _el((2, 9.5, 12.5), (4, 22, 14.5), _all("dark")), _el((12, 9.5, 12.5), (14, 22, 14.5), _all("dark")),
             _el((4, 12, 13), (12, 13.5, 14), _all("oak")), _el((4, 16, 13), (12, 17.5, 14), _all("oak")), _el((3, 20, 12.75), (13, 22, 14.25), _all("oak"))]
    wj(os.path.join(M, "chair.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": chair})
    wj(os.path.join(assets, "blockstates/chair.json"), {"variants": {f"facing={f}": {"model": f"{MOD}:block/chair", "y": r} for f, r in rot.items()}})
    wj(os.path.join(assets, "items/chair.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/chair"}})

    # ---- tables and benches: a top always, legs only at the corners of the whole, an apron where nothing joins
    def joining(name, top, leg_h, apron):
        wj(os.path.join(M, f"{name}_top.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": [_el((0, top[0], 0), (16, top[1], 16), _all("oak", up="oak"))]})
        corners = {("north", "west"): (1, 1), ("north", "east"): (12, 1), ("south", "west"): (1, 12), ("south", "east"): (12, 12)}
        multipart = [{"apply": {"model": f"{MOD}:block/{name}_top"}}]
        for (a, b), (x0, z0) in corners.items():
            leg = f"{name}_leg_{a}_{b}"
            wj(os.path.join(M, f"{leg}.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": [_el((x0, 0, z0), (x0 + 3, leg_h, z0 + 3), _all("dark"))]})
            multipart.append({"when": {a: "false", b: "false"}, "apply": {"model": f"{MOD}:block/{leg}"}})
        for side, box in (("north", ((0, apron[0], 0.5), (16, apron[1], 1.5))), ("south", ((0, apron[0], 14.5), (16, apron[1], 15.5))),
                          ("west", ((0.5, apron[0], 0), (1.5, apron[1], 16))), ("east", ((14.5, apron[0], 0), (15.5, apron[1], 16)))):
            ap = f"{name}_apron_{side}"
            wj(os.path.join(M, f"{ap}.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": [_el(box[0], box[1], _all("dark"))]})
            multipart.append({"when": {side: "false"}, "apply": {"model": f"{MOD}:block/{ap}"}})
        wj(os.path.join(assets, f"blockstates/{name}.json"), {"multipart": multipart})
        item = [_el((0, top[0], 0), (16, top[1], 16), _all("oak"))] + [_el((x0, 0, z0), (x0 + 3, leg_h, z0 + 3), _all("dark")) for x0, z0 in corners.values()]
        wj(os.path.join(M, f"{name}.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": item})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})
    joining("table", (13, 16), 13, (11, 13))
    joining("bench", (6, 8), 6, (4.5, 6))

    # ---- the dresser: two halves, three drawers in each, the middle one pulled out when open
    def dresser(right, open_):
        front = "front_r" if right else "front"
        els = [_el((0, 0, 1), (16, 14, 16), _all("oak", north=front)), _el((-0.0, 14, 0.5), (16, 15.5, 16), _all("dark")),
               _el((1 if not right else 0, 0, 0.5), (16 if not right else 15, 1, 1), _all("dark"))]
        if open_:
            els += [_el((1.5, 5.5, -4), (14.5, 9.5, 1), _all("oak", north=front, up="inside")), _el((2.5, 9.5, -3), (13.5, 10, 0), _all("inside"))]
        return els
    for right in (False, True):
        for open_ in (False, True):
            n = "dresser_" + ("right" if right else "left") + ("_open" if open_ else "")
            wj(os.path.join(M, f"{n}.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": dresser(right, open_)})
    wj(os.path.join(assets, "blockstates/dresser.json"),
       {"variants": {f"facing={f},open={'true' if o else 'false'},right={'true' if r_ else 'false'}":
                     {"model": f"{MOD}:block/dresser_{'right' if r_ else 'left'}{'_open' if o else ''}", "y": r}
                     for f, r in rot.items() for o in (False, True) for r_ in (False, True)}})
    wj(os.path.join(M, "dresser.json"), {"parent": f"{MOD}:block/dresser_left"})
    wj(os.path.join(assets, "items/dresser.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/dresser_left"}})

    # ---- the bunk bed (drawn with its head to the north)
    def bunk(head, upper):
        posts_z = (0, 2) if head else (14, 16)
        els = []
        for x0 in (0, 14):
            els.append(_el((x0, 0, posts_z[0]), (x0 + 2, 16, posts_z[1]), _all("dark", up="end")))
        if not upper:
            els += [_el((1, 2, 0), (15, 3, 16), _all("oak")), _el((1.5, 3, 0), (14.5, 6, 16), _all("mattress"))]
            els += [_el((1.5, 6, 0 if not head else 4), (14.5, 6.5, 16 if not head else 16), _all("blanket"))] if not head else [_el((1.5, 6, 5), (14.5, 6.5, 16), _all("blanket"))]
            els += [_el((0, 1, posts_z[0]), (16, 3, posts_z[1]), _all("dark"))]
            if head: els += [_el((3, 6, 0.5), (13, 8, 4.5), _all("pillow")), _el((2, 3, 0), (14, 12, 1.5), _all("oak"))]
        else:
            els += [_el((1, 0, 0), (15, 1.5, 16), _all("oak")), _el((1.5, 1.5, 0), (14.5, 4, 16), _all("mattress"))]
            els += [_el((1.5, 4, 5 if head else 0), (14.5, 4.5, 16), _all("blanket"))]
            els += [_el((0, 7, 0), (1, 8.5, 16), _all("dark")), _el((0, 13, posts_z[0]), (16, 15, posts_z[1]), _all("dark"))]
            if head: els += [_el((3, 4, 0.5), (13, 6, 4.5), _all("pillow"))]
        if not head:
            # the ladder up the foot, on its east side
            els += [_el((15, 0, 15.5), (16, 16, 16), _all("oak"))] if False else []
            for y in (2, 6, 10, 14):
                els.append(_el((2, y, 15.25), (14, y + 1, 16), _all("oak")))
        return els
    for head in (False, True):
        for upper in (False, True):
            n = f"bunk_bed_{'upper' if upper else 'lower'}_{'head' if head else 'foot'}"
            wj(os.path.join(M, f"{n}.json"), {"parent": "minecraft:block/block", "textures": ftx, "elements": bunk(head, upper)})
    wj(os.path.join(assets, "blockstates/bunk_bed.json"),
       {"variants": {f"facing={f},head={'true' if h else 'false'},upper={'true' if u else 'false'}":
                     {"model": f"{MOD}:block/bunk_bed_{'upper' if u else 'lower'}_{'head' if h else 'foot'}", "y": r}
                     for f, r in rot.items() for h in (False, True) for u in (False, True)}})
    wj(os.path.join(M, "bunk_bed.json"), {"parent": f"{MOD}:block/bunk_bed_lower_foot"})
    wj(os.path.join(assets, "items/bunk_bed.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/bunk_bed_lower_head"}})

    # ---- glass that connects: a frame round the outside of a whole window, none between its blocks
    def glass_face(inner, frame, width, mask):
        up, down, left, right = mask & 1, mask & 2, mask & 4, mask & 8

        def fn(x, y):
            d = []
            if not up: d.append(("t", y))
            if not down: d.append(("b", 15 - y))
            if not left: d.append(("l", x))
            if not right: d.append(("r", 15 - x))
            if d:
                side, near = min(d, key=lambda k: k[1])
                if near < width: return frame(x, y, near, side)
            return inner(x, y)
        return fn

    def glass_set(name, inner, frame, width):
        for mask in range(16):
            paint_hi(glass_face(inner, frame, width, mask)).save(os.path.join(T, f"{name}_{mask}.png"))

    dirs = ["down", "east", "north", "south", "up", "west"]

    def masks(j):
        """The frame mask of each face of a block joined as {j} says."""
        out = {}
        for face, (u, dn, l, r) in SIDE_EDGES_C.items():
            out[face] = (1 if j[u] else 0) | (2 if j[dn] else 0) | (4 if j[l] else 0) | (8 if j[r] else 0)
        out["up"] = (1 if j["north"] else 0) | (2 if j["south"] else 0) | (4 if j["west"] else 0) | (8 if j["east"] else 0)
        out["down"] = (1 if j["south"] else 0) | (2 if j["north"] else 0) | (4 if j["west"] else 0) | (8 if j["east"] else 0)
        return out

    def glass_models(tex_prefix, model_prefix):
        out = {}
        for bits in range(64):
            j = {d: bool(bits >> i & 1) for i, d in enumerate(dirs)}
            m = masks(j)
            tex = {"particle": {"sprite": t(f"{tex_prefix}_0"), "force_translucent": True}}
            faces = {}
            for face in ("north", "south", "east", "west", "up", "down"):
                tex[face] = {"sprite": t(f"{tex_prefix}_{m[face]}"), "force_translucent": True}
                faces[face] = {"uv": [0, 0, 16, 16], "texture": f"#{face}", "cullface": face}
            model = model_prefix + "_" + "".join(d[0] if j[d] else "_" for d in dirs)
            wj(os.path.join(M, f"{model}.json"), {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex,
                                                  "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
            out[",".join(f"{d}={'true' if j[d] else 'false'}" for d in dirs)] = {"model": f"{MOD}:block/{model}"}
        return out

    def glass_item(name, model):
        wj(os.path.join(M, f"{name}.json"), {"parent": f"{MOD}:block/{model}_" + "______"})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})

    def switching(name, looks):
        """A glass of two looks (clear or not), each look connecting; its powered state changes nothing to see."""
        states = {}
        for clr, prefix in looks:
            for k, v in glass_models(prefix, prefix).items():
                for pw in ("false", "true"):
                    states[f"clear={'true' if clr else 'false'},powered={pw}," + k] = v
        wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": states})

    CLEAR = (0, 0, 0, 0)

    def sheen(x, y, strength=1.0):
        """Two soft diagonal streaks of light, running on from block to block."""
        k = (x + y) % 16
        if k in (3, 4): return int(70 * strength)
        if k == 5: return int(35 * strength)
        if k in (10,): return int(40 * strength)
        return 0

    def clear_glass(x, y, strength=1.0):
        a = sheen(x, y, strength)
        return (255, 255, 255, a) if a else CLEAR

    def bevel(dark, mid, light, width):
        """A frame lit on its top and left, shadowed on its bottom and right."""
        def f(x, y, near, side):
            if near == 0: return tuple(dark) + (255,)
            lit = side in ("t", "l")
            return tuple(light if lit else mid) + (255,)
        return f

    # reinforced: clear, framed in two pixels of bronze, a rivet where frame meets frame
    def bronze_frame(x, y, near, side):
        if near == 0: return (96, 56, 24, 255)
        if (x in (1, 14)) and (y in (1, 14)): return GOLD_HI + (255,)
        return ((226, 160, 86) if side in ("t", "l") else (176, 110, 50)) + (255,)
    glass_set("reinforced_glass", lambda x, y: clear_glass(x, y, 1.2), bronze_frame, 2)
    wj(os.path.join(assets, "blockstates/reinforced_glass.json"), {"variants": glass_models("reinforced_glass", "reinforced_glass")})
    glass_item("reinforced_glass", "reinforced_glass")

    # frosted: milky white, crystals of frost brighter in it, a pale frame
    frost = random.Random(161)
    flakes = {(frost.randrange(16), frost.randrange(16)) for _ in range(18)}
    fn_ = fbm(171)

    def frosted(x, y):
        v = fn_(x, y)
        a = int(196 + 40 * v)
        if (x, y) in flakes: return (255, 255, 255, 245)
        return (232 + int(10 * v), 240 + int(8 * v), 246, min(240, a + sheen(x, y, 0.5)))
    glass_set("frosted_glass", frosted, bevel((176, 186, 196), (214, 222, 230), (246, 250, 252), 1), 1)
    wj(os.path.join(assets, "blockstates/frosted_glass.json"), {"variants": glass_models("frosted_glass", "frosted_glass")})
    glass_item("frosted_glass", "frosted_glass")

    # smart glass: graphite frame; shaded, a smoked pane with fine lines across it; clear, nothing but the light on it
    graphite = bevel((40, 44, 54), (78, 84, 98), (120, 128, 144), 1)

    def shaded(x, y):
        c = mix((44, 52, 68), (70, 80, 100), 0.5 + 0.5 * math.sin((x + y) * math.pi / 8))
        if y % 2 == 0: c = shade(c, 0.92)
        a = sheen(x, y, 0.6)
        if a: c = mix(c, (180, 190, 210), a / 255)
        return tuple(c) + (240,)
    glass_set("smart_glass", shaded, graphite, 1)
    glass_set("smart_glass_clear", lambda x, y: (200, 230, 255, 40) if y % 4 == 0 and not sheen(x, y) else clear_glass(x, y), graphite, 1)
    switching("smart_glass", ((False, "smart_glass"), (True, "smart_glass_clear")))
    glass_item("smart_glass", "smart_glass")

    # veil glass: untinted - a pane of pale glass that cannot be seen through, its surface catching the light; clear,
    # the same pane with nothing behind it. Pewter frame.
    pewter = bevel((96, 100, 106), (150, 154, 160), (206, 210, 214), 1)
    vn = fbm(181)

    def veiled(x, y):
        v = vn(x, y)
        c = (226 + int(14 * v), 232 + int(12 * v), 236 + int(10 * v))
        a = sheen(x, y, 1.0)
        if a: c = mix(c, (255, 255, 255), a / 140)
        if (x * 5 + y * 3) % 17 == 0: c = shade(c, 0.96)
        return tuple(c) + (255,)
    glass_set("veil_glass", veiled, pewter, 1)
    glass_set("veil_glass_clear", lambda x, y: clear_glass(x, y), pewter, 1)
    switching("veil_glass", ((False, "veil_glass"), (True, "veil_glass_clear")))
    glass_item("veil_glass", "veil_glass")

    # one-way glass: from the front a solid pane of silvered glass, untinted; from behind, nothing. Where a run of it
    # steps, the sides of the steps are drawn plain, so the front reads whole.
    sn = fbm(191)

    def silvered(x, y):
        # (noise and streaks that tile, so a run of it reads as one sheet)
        v = sn(x, y)
        c = (200 + int(22 * v), 206 + int(22 * v), 214 + int(20 * v))
        a = sheen(x, y, 1.0)
        if a: c = mix(c, (255, 255, 255), a / 110)
        return tuple(c) + (255,)
    glass_set("one_way_glass", silvered, pewter, 1)
    # from behind: the same frame round the window, nothing inside it; along its edges, the frame's own side
    # from behind: a wider frame, so the back can be told, round a faint pane of glass that can be seen through
    def back_pane(x, y):
        a = sheen(x, y, 1.0)
        return (236, 242, 248, 56 + a) if a else (214, 224, 232, 44)

    def back_frame(x, y, near, side):
        if near == 0: return (96, 100, 106, 255)
        if near == 2: return (176, 180, 186, 255)
        return ((206, 210, 214) if side in ("t", "l") else (150, 154, 160)) + (255,)
    glass_set("one_way_glass_back", back_pane, back_frame, 3)
    face_box = {"north": ((0, 0, 0), (16, 16, 0)), "south": ((0, 0, 16), (16, 16, 16)), "west": ((0, 0, 0), (0, 16, 16)),
                "east": ((16, 0, 0), (16, 16, 16)), "up": ((0, 16, 0), (16, 16, 16)), "down": ((0, 0, 0), (16, 0, 16))}
    opposite = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
    import glob as _glob
    for old in _glob.glob(os.path.join(M, "one_way_glass_*.json")) + _glob.glob(os.path.join(T, "one_way_glass_edge.png")):
        os.remove(old)
    one_way = {}
    for facing in face_box:
        back = opposite[facing]
        for bits in range(64):
            j = {d: bool(bits >> i & 1) for i, d in enumerate(dirs)}
            m = masks(j)
            # every face drawn - the front and the sides silvered, the back framed and faint - each joined to the run
            # it belongs to; a face against another block, or another pane of it, is not drawn
            tex = {"particle": t(f"one_way_glass_{m[facing]}")}
            elements = []
            for face in face_box:
                if face == back:
                    tex[face] = {"sprite": t(f"one_way_glass_back_{m[face]}"), "force_translucent": True}
                else:
                    tex[face] = t(f"one_way_glass_{m[face]}")
                elements.append({"from": list(face_box[face][0]), "to": list(face_box[face][1]),
                                  "faces": {face: {"uv": [0, 0, 16, 16], "texture": f"#{face}", "cullface": face}}})
            model = f"one_way_glass_{facing}_" + "".join(d[0] if j[d] else "_" for d in dirs)
            wj(os.path.join(M, f"{model}.json"), {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": elements})
            one_way[f"facing={facing}," + ",".join(f"{d}={'true' if j[d] else 'false'}" for d in dirs)] = {"model": f"{MOD}:block/{model}"}
    wj(os.path.join(assets, "blockstates/one_way_glass.json"), {"variants": one_way})
    wj(os.path.join(M, "one_way_glass.json"), {"parent": f"{MOD}:block/one_way_glass_north_______"})
    wj(os.path.join(assets, "items/one_way_glass.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/one_way_glass"}})

    # ---- recipes and loot
    recipes = {
        "moving_dummy": ({"D": f"{MOD}:training_dummy", "M": "minecraft:minecart"}, ["D", "M"], 1),
        "chair": ({"P": "#minecraft:planks", "S": "minecraft:stick", "W": "#minecraft:wool"}, ["S  ", "SWS", "P P"], 2),
        "table": ({"P": "#minecraft:planks", "S": "minecraft:stick"}, ["PPP", "S S", "S S"], 2),
        "bench": ({"P": "#minecraft:planks", "S": "minecraft:stick"}, ["PPP", "S S"], 3),
        "dresser": ({"P": "#minecraft:planks", "C": "minecraft:chest", "G": "minecraft:gold_nugget"}, ["PGP", "PCP", "PGP"], 1),
        "bunk_bed": ({"B": "#minecraft:beds", "S": "minecraft:stick"}, ["SBS", "SBS"], 1),
        "reinforced_glass": ({"G": "minecraft:glass", "C": "minecraft:copper_ingot"}, ["CGC", "GCG", "CGC"], 4),
        "frosted_glass": ({"G": "minecraft:glass", "W": "minecraft:white_dye"}, ["GGG", "GWG", "GGG"], 8),
        "smart_glass": ({"G": "minecraft:glass", "Q": "minecraft:quartz", "R": "minecraft:redstone"}, ["GQG", "QRQ", "GQG"], 4),
        "veil_glass": ({"G": "minecraft:glass", "Q": "minecraft:quartz", "A": "minecraft:amethyst_shard"}, ["GQG", "QAQ", "GQG"], 4),
        "one_way_glass": ({"G": "minecraft:glass", "N": "minecraft:iron_nugget"}, ["GGG", "NNN"], 3),
    }
    for rid, (key_, pattern_, count) in recipes.items():
        wj(os.path.join(data, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": "building", "key": key_, "pattern": pattern_,
                                                      "result": {"id": f"{MOD}:{rid}", "count": count}})

    def loot(b, props=None):
        cond = [{"condition": "minecraft:survives_explosion"}]
        if props: cond.insert(0, {"condition": "minecraft:block_state_property", "block": f"{MOD}:{b}", "properties": props})
        wj(os.path.join(data, f"loot_table/blocks/{b}.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                                                                                                  "conditions": cond}]})
    for b in ("training_dummy", "moving_dummy", "chair", "table", "bench"):
        loot(b)
    loot("dresser", {"right": "false"})
    loot("bunk_bed", {"head": "false", "upper": "false"})


# ---- treasure caches and the towers' braziers ------------------------------------------------------------------------

LINES3 = ["zeus", "poseidon", "hades"]


def gen_cache_brazier(assets):
    """Caches and braziers in the game's own manner: chunky boxes, flat textures of a few tones."""
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def save(name, fn):
        paint_hi(fn).save(os.path.join(T, name + ".png"))

    # ---- the caches: a chest built up in layers, the game's way - a box of boards set on a metal plinth, iron guards on
    # its corners, a lid that overhangs it by a pixel, two straps over the lid, a hasp hanging from it, and on a locked
    # one a padlock. Each part is its own box standing proud of the one under it, so the chest has depth from every
    # side; and each texture is drawn one texel to one model pixel: a tower's wood in five tones, its metal in four.
    woods = {"zeus": ((62, 38, 20), (120, 80, 42), (160, 112, 64), (192, 146, 90), (222, 184, 126)),
             "poseidon": ((16, 42, 48), (32, 78, 86), (48, 108, 114), (72, 138, 142), (112, 178, 176)),
             "hades": ((20, 12, 24), (42, 28, 50), (62, 44, 72), (86, 64, 98), (118, 94, 132))}
    metals = {"zeus": ((92, 58, 16), (170, 122, 36), (228, 182, 70), (255, 232, 146)),
              "poseidon": ((62, 70, 80), (132, 144, 156), (192, 204, 214), (244, 248, 252)),
              "hades": ((50, 24, 74), (104, 62, 154), (156, 106, 212), (216, 182, 252))}
    gems = {"zeus": ((40, 90, 200), (120, 180, 255)), "poseidon": ((20, 140, 120), (120, 240, 210)), "hades": ((150, 20, 40), (240, 90, 100))}
    for line in LINES3:
        o, d, b, l, h = woods[line]
        mo, md, mb, mh = metals[line]

        # boards: four-pixel planks laid across, a lit edge at the top of each and a dark seam under it, butt joints
        # staggered from plank to plank, a little grain and the odd knot
        def wood(x, y, o=o, d=d, b=b, l=l, h=h):
            plank, r = y // 4, y % 4
            if r == 3: return o if x % 5 == 0 else d
            if x == (plank * 7 + 3) % 16: return d                     # a butt joint
            if x == (plank * 7 + 4) % 16 and r == 0: return h
            if r == 0: return l
            if (x * 3 + plank * 5) % 11 == 0 and r == 1: return d      # a knot
            if (x + plank * 3) % 6 == 0: return l if r == 1 else b
            return b
        save(f"cache_wood_{line}", wood)

        # the lid's top: the same boards running front to back, and a lit rim
        def lid_top(x, y, o=o, d=d, b=b, l=l, h=h):
            if y in (1, 14) or x in (1, 14): return o
            if y == 2: return h
            return wood(y, x)
        save(f"cache_top_{line}", lid_top)

        # metal for the upright guards and straps: a lit stripe, a shadowed one, rivets every few pixels
        def strap(x, y, mo=mo, md=md, mb=mb, mh=mh):
            lit = x % 2 == 0
            if y % 5 == 2: return mh if lit else mo
            if y % 5 == 3: return mb if lit else mo
            return mb if lit else md
        save(f"cache_strap_{line}", strap)

        # metal for the plinth and other bands lying flat: lit along the top, shadowed below, rivets along it
        def band(x, y, mo=mo, md=md, mb=mb, mh=mh):
            r = y % 2
            if x % 4 == 1: return mh if r == 0 else mo
            return mb if r == 0 else md
        save(f"cache_band_{line}", band)

        # the hasp: a plate four wide and five tall with a gem set in it and a slot for the lock's shackle
        gd, gl = gems[line]

        def hasp(x, y, mo=mo, md=md, mb=mb, mh=mh, gd=gd, gl=gl):
            if x > 3 or y > 4: return mb
            if y == 0: return mh if 0 < x < 3 else mb
            if x == 0: return mb
            if x == 3 or y == 4: return mo
            if y == 1 or y == 2: return gl if (x, y) == (1, 1) else gd
            return (16, 12, 10)                                          # the slot
        save(f"cache_latch_{line}", hasp)

    # the padlock: a steel body with a keyhole, lit on its left; its shackle an arch above it
    steel = ((36, 36, 42), (104, 108, 120), (164, 168, 182), (228, 232, 240))

    def padlock(x, y):
        if y <= 2:                                       # the shackle (x 0..3, y 0..2)
            if x > 3: return None
            if y == 0: return steel[2] if x in (1, 2) else None
            return steel[3] if x == 0 else steel[1] if x == 3 else None
        if x > 3 or y > 7: return None                   # the body (x 0..3, y 3..7)
        if y == 3: return steel[3]
        if y == 7: return steel[0]
        if x == 0: return steel[3] if y == 4 else steel[2]
        if x == 3: return steel[1]
        if (x, y) in ((1, 5), (2, 5)): return (14, 12, 16)
        if (x, y) == (1, 6): return (14, 12, 16)
        return steel[2]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = padlock(x, y)
            if c: img.putpixel((x, y), tuple(c) + (255,))
    img.save(os.path.join(T, "cache_padlock.png"))

    # what lies inside: a heap of coins on dark velvet, highest in the middle, a gem or two in it, in a rim of wood
    rnd_c = random.Random(229)
    heap = {}
    for _ in range(120):
        cx, cy = rnd_c.gauss(7.5, 2.6), rnd_c.gauss(7.5, 2.6)
        heap[(int(cx), int(cy))] = rnd_c.random()

    def inside(x, y):
        if x in (2, 13) or y in (2, 13): return (52, 34, 22)
        if (x, y) in ((6, 6), (9, 8)): return (120, 200, 255) if (x, y) == (6, 6) else (240, 90, 100)
        v = heap.get((x, y))
        if v is not None:
            centre = math.hypot(x - 7.5, y - 7.5)
            if v > 0.75 or centre < 2: return GOLD_HI
            return GOLD if v > 0.3 else GOLD_LO
        return (104, 26, 34) if (x + y) % 2 else (86, 20, 28)
    save("cache_inside", inside)
    for line in LINES3:
        for old in (f"cache_body_{line}", f"cache_lid_{line}"):
            if os.path.exists(os.path.join(T, old + ".png")): os.remove(os.path.join(T, old + ".png"))
    if os.path.exists(os.path.join(T, "cache_lock.png")): os.remove(os.path.join(T, "cache_lock.png"))

    def part(a, b, tex, uvs, rot=None):
        """A box: uvs gives the uv of each face, by name, or of every side face at once under "side"."""
        faces = {}
        for f in ("north", "south", "east", "west", "up", "down"):
            k = f if f in uvs else ("side" if f in ("north", "south", "east", "west") else "flat")
            if k not in uvs: continue
            uv = uvs[k]
            faces[f] = {"texture": f"#{uv[1] if isinstance(uv, tuple) else tex}", "uv": list(uv[0] if isinstance(uv, tuple) else uv)}
        el = {"from": list(a), "to": list(b), "faces": faces}
        if rot: el["rotation"] = rot
        return el

    def chest(line, open_, locked):
        tx = {"particle": t(f"cache_wood_{line}"), "wood": t(f"cache_wood_{line}"), "top": t(f"cache_top_{line}"),
              "strap": t(f"cache_strap_{line}"), "band": t(f"cache_band_{line}"), "hasp": t(f"cache_latch_{line}"),
              "in": t("cache_inside"), "lock": t("cache_padlock")}
        hinge = {"origin": [8, 10, 15], "axis": "x", "angle": 45.0} if open_ else None
        els = [
            # the plinth, a metal band all round the foot
            part((1, 0, 1), (15, 2, 15), "band", {"side": [1, 0, 15, 2], "down": [1, 1, 15, 15]}),
            # the box of boards, a pixel in from the plinth and the guards; its top is the inside, seen with the lid up
            part((2, 2, 2), (14, 10, 14), "wood", {"side": [2, 4, 14, 12], "up": ([2, 2, 14, 14], "in")}),
            # the lid, overhanging the box by a pixel all round, boards on its sides and its top
            part((1, 10, 1), (15, 14, 15), "wood", {"side": [1, 0, 15, 4], "up": ([1, 1, 15, 15], "top"), "down": [1, 1, 15, 15]}, hinge),
            # two straps over the lid, proud of it on the front, the top and the back
            part((3, 10, 0), (5, 15, 16), "strap", {"side": [0, 0, 2, 5], "east": [0, 0, 16, 5], "west": [0, 0, 16, 5], "up": [0, 0, 2, 16]}, hinge),
            part((11, 10, 0), (13, 15, 16), "strap", {"side": [0, 0, 2, 5], "east": [0, 0, 16, 5], "west": [0, 0, 16, 5], "up": [0, 0, 2, 16]}, hinge),
            # the hasp, hanging from the lid's front over the box
            part((6, 7, 0), (10, 12, 1), "hasp", {"north": [0, 0, 4, 5], "south": [0, 0, 4, 5], "east": [3, 0, 4, 5], "west": [0, 0, 1, 5],
                                                  "up": [0, 0, 4, 1], "down": [0, 4, 4, 5]}, hinge),
        ]
        # iron guards up the four corners, between the plinth and the lid
        for gx in (1, 13):
            for gz in (1, 13):
                els.append(part((gx, 2, gz), (gx + 2, 10, gz + 2), "strap", {"side": [0, 1, 2, 9]}))
        if locked:
            # the padlock hangs from the hasp: its body below, its shackle through the slot
            els.append(part((6, 3, -1), (10, 8, 0), "lock", {"north": [0, 3, 4, 8], "south": [0, 3, 4, 8], "east": [3, 3, 4, 8], "west": [0, 3, 1, 8],
                                                             "up": [0, 3, 4, 4], "down": [0, 7, 4, 8]}))
            els.append({"from": [6, 8, -0.5], "to": [10, 11, -0.5], "faces": {
                "north": {"texture": "#lock", "uv": [0, 0, 4, 3]}, "south": {"texture": "#lock", "uv": [0, 0, 4, 3]}}})
        return {"parent": "minecraft:block/block", "textures": tx, "elements": els}
    variants = {}
    for i, line in enumerate(LINES3):
        for open_ in (False, True):
            for locked in (False, True):
                name = f"passage_cache_{line}{'_open' if open_ else ''}{'_locked' if locked else ''}"
                wj(os.path.join(M, f"{name}.json"), chest(line, open_, locked))
                for f, r in rot.items():
                    variants[f"facing={f},locked={'true' if locked else 'false'},open={'true' if open_ else 'false'},tower={i}"] = {"model": f"{MOD}:block/{name}", "y": r}
    wj(os.path.join(assets, "blockstates/passage_cache.json"), {"variants": variants})
    wj(os.path.join(M, "passage_cache.json"), {"parent": f"{MOD}:block/passage_cache_zeus"})
    wj(os.path.join(assets, "items/passage_cache.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/passage_cache_zeus"}})

    # ---- the lockpick, as an item: a hooked steel pick with a wrapped grip, drawn at 16 and kept simple
    pick = ["................", "................", "................", "............##..", "...........#ab#.", "..........#ab#..",
            ".........#ab#...", "........#ab#....", ".......#ab#.....", "......#ab#......", ".....#cd#.......", "....#cdc#.......",
            "...#cdc#........", "..#cdc#.........", "..#dd#..........", "...##..........."]
    pc = {"#": (34, 30, 36), "a": (222, 226, 236), "b": (140, 146, 160), "c": (150, 92, 52), "d": (104, 60, 34)}
    item = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pick):
        for x, ch in enumerate(row):
            if ch in pc: item.putpixel((x, y), pc[ch] + (255,))
    # the hook at its tip
    for (x, y) in ((13, 2), (14, 2), (14, 3)): item.putpixel((x, y), (34, 30, 36, 255))
    item.putpixel((13, 3), (222, 226, 236, 255))
    item.save(os.path.join(assets, "textures/item/lockpick.png"))

    # ---- braziers: drawn one texture pixel to one model pixel. Each tower's metal in five tones - outline, shadow,
    # base, light, and an accent for its rivets - a lip that catches the light round the bowl, coals glowing in it,
    # and a flame of four frames in four tones of the tower's fire.
    pal = {"zeus": ((64, 36, 18), (122, 72, 34), (176, 112, 52), (224, 164, 90), (255, 232, 140)),
           "poseidon": ((24, 58, 52), (48, 100, 86), (84, 152, 128), (138, 204, 176), (226, 232, 236)),
           "hades": ((20, 16, 28), (40, 34, 52), (68, 60, 82), (108, 98, 128), (176, 120, 240))}
    fires = {"zeus": ((255, 252, 214), (255, 230, 120), (250, 168, 52), (196, 88, 26)),
             "poseidon": ((236, 255, 250), (150, 246, 232), (60, 200, 196), (24, 120, 136)),
             "hades": ((246, 236, 255), (204, 170, 255), (140, 92, 230), (78, 40, 150))}
    for line in LINES3:
        out_, dark, base, light, acc = pal[line]
        core, hot, mid, deep = fires[line]

        # the bowl's sides, read from the top: the lip lit, its shadow line, rivets along the band, the base in shadow
        def bowl(x, y, out_=out_, dark=dark, base=base, light=light, acc=acc):
            if y == 0: return light
            if y == 1: return out_
            if y == 2: return acc if x % 4 == 1 else base
            if y == 3: return base
            return dark
        save(f"brazier_bowl_{line}", bowl)

        ember_rnd = random.Random(223)
        ember = [[ember_rnd.random() for _ in range(16)] for _ in range(16)]

        # its top: the lip round the edge (2..13), coals within, hottest at the middle
        def coals(x, y, out_=out_, light=light, base=base, hot=hot, mid=mid, deep=deep, core=core, ember=ember):
            if x in (2, 13) or y in (2, 13): return light if x == 2 or y == 2 else base
            if x in (3, 12) or y in (3, 12): return out_
            r = math.hypot(x - 7.5, y - 7.5)
            k = ember[y][x]
            if r < 2.2 and k < 0.6: return core
            if r < 3.6 and k < 0.6: return hot
            if k < 0.35: return mid
            if k < 0.7: return deep
            return (52, 34, 30)
        save(f"brazier_coals_{line}", coals)

        # posts, feet and arms: lit down the left, shadowed down the right, a band at the top
        def metal(x, y, out_=out_, dark=dark, base=base, light=light):
            if y == 0: return light
            if x % 16 == 0: return light
            if x % 16 >= 2 and (x % 3 == 2): return dark
            return base
        save(f"brazier_metal_{line}", metal)

        # the wall plate: framed, a rivet at each corner
        def plate(x, y, out_=out_, dark=dark, base=base, light=light, acc=acc):
            if x in (0, 5) or y in (0, 9): return out_
            if (x, y) in ((1, 1), (4, 1), (1, 8), (4, 8)): return acc
            if x == 1 or y == 1: return light
            if x == 4 or y == 8: return dark
            return base
        save(f"brazier_plate_{line}", plate)

        # the flame: three tongues, rising and falling frame to frame
        frames = []
        tongues = [[(5.0, 10, 3.2), (8.5, 15, 4.0), (11.5, 9, 2.8)], [(5.5, 12, 3.0), (8.0, 13, 3.8), (11.0, 11, 3.0)],
                   [(4.5, 9, 3.0), (8.5, 14, 4.2), (11.5, 12, 2.8)], [(5.0, 11, 3.2), (9.0, 15, 3.6), (11.0, 10, 3.0)]]
        for f in range(4):
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for y in range(16):
                yy = 15 - y
                for x in range(16):
                    heat = 0.0
                    for cx, H, W in tongues[f]:
                        if yy >= H: continue
                        half = W * (1 - yy / H) ** 0.75 + 0.4
                        dx = abs(x + 0.5 - cx)
                        if dx <= half: heat = max(heat, 1.0 - dx / half * 0.55 - yy / H * 0.5)
                    if heat <= 0: continue
                    c = core if heat > 0.78 else hot if heat > 0.58 else mid if heat > 0.36 else deep
                    img.putpixel((x, y), c + (255,))
            frames.append(img)
        strip = Image.new("RGBA", (16, 64))
        for k, fr in enumerate(frames): strip.paste(fr, (0, k * 16))
        strip.save(os.path.join(T, f"brazier_flame_{line}.png"))
        wj(os.path.join(T, f"brazier_flame_{line}.png.mcmeta"), {"animation": {"frametime": 4}})

    def box(a, b, side, side_uv, top=None, top_uv=None, bottom=None, bottom_uv=None):
        faces = {d: {"texture": f"#{side}", "uv": list(side_uv)} for d in ("north", "south", "east", "west")}
        faces["up"] = {"texture": f"#{top or side}", "uv": list(top_uv or side_uv)}
        faces["down"] = {"texture": f"#{bottom or side}", "uv": list(bottom_uv or side_uv)}
        return {"from": list(a), "to": list(b), "faces": faces}

    def flames(x0, x1, y0, y1):
        out = []
        for ang in (45.0, -45.0):
            el = _el((x0, y0, 8), (x1, y1, 8), {"north": "flame", "south": "flame"}, {"origin": [8, 8, 8], "axis": "y", "angle": ang})
            for face in el["faces"].values(): face["uv"] = [0, 0, 16, 16]
            el["shade"] = False
            el["light_emission"] = 15                          # (fire is its own light)
            out.append(el)
        return out

    for line in LINES3:
        tx = {"particle": t(f"brazier_bowl_{line}"), "metal": t(f"brazier_metal_{line}"), "bowl": t(f"brazier_bowl_{line}"),
              "coals": t(f"brazier_coals_{line}"), "plate": t(f"brazier_plate_{line}"), "flame": t(f"brazier_flame_{line}")}
        bowl_top = [2, 2, 14, 14]
        floor = [box((4, 0, 4), (12, 2, 12), "metal", [0, 0, 8, 2], "metal", [0, 2, 8, 10]),
                 box((2, 2, 2), (14, 7, 14), "bowl", [2, 0, 14, 5], "coals", bowl_top, "metal", [0, 2, 12, 14])] + flames(2, 14, 6, 18)
        standing = [box((3, 0, 3), (13, 1, 13), "metal", [0, 0, 10, 1], "metal", [0, 2, 10, 12]),
                    box((5, 1, 5), (11, 2, 11), "metal", [0, 0, 6, 1], "metal", [0, 2, 6, 8]),
                    box((6.5, 2, 6.5), (9.5, 10, 9.5), "metal", [0, 1, 3, 9]),
                    box((5.5, 7, 5.5), (10.5, 8, 10.5), "metal", [0, 0, 5, 1], "metal", [0, 2, 5, 7]),
                    box((2, 10, 2), (14, 15, 14), "bowl", [2, 0, 14, 5], "coals", bowl_top, "metal", [0, 2, 12, 14])] + flames(2, 14, 14, 26)
        wall = [box((5, 2, 14), (11, 12, 16), "plate", [0, 0, 6, 10], "metal", [0, 0, 6, 2], "metal", [0, 0, 6, 2]),
                box((7, 6, 6), (9, 8, 14), "metal", [0, 0, 8, 2], "metal", [0, 0, 2, 8]),
                box((7, 3, 12), (9, 6, 14), "metal", [0, 0, 2, 3]),
                box((2, 8, 2), (14, 12, 14), "bowl", [2, 0, 14, 4], "coals", bowl_top, "metal", [0, 2, 12, 14])] + flames(2, 14, 11, 21)
        for name, els in (("floor", floor), ("standing", standing), ("wall", wall)):
            wj(os.path.join(M, f"tower_brazier_{name}_{line}.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": els})
    variants = {}
    for i, line in enumerate(LINES3):
        for mount in ("floor", "standing", "wall"):
            for f, r in rot.items():
                v = {"model": f"{MOD}:block/tower_brazier_{mount}_{line}"}
                if mount == "wall": v["y"] = r
                variants[f"facing={f},mount={mount},tower={i}"] = v
    wj(os.path.join(assets, "blockstates/tower_brazier.json"), {"variants": variants})
    wj(os.path.join(M, "tower_brazier.json"), {"parent": f"{MOD}:block/tower_brazier_standing_zeus"})
    wj(os.path.join(assets, "items/tower_brazier.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/tower_brazier_standing_zeus"}})


# ---- the mod's own particles: 8x8 sprites like the game's own, a few frames each, read left to right as they age --

def gen_particles(assets):
    P = os.path.join(assets, "textures/particle")
    D = os.path.join(assets, "particles")
    os.makedirs(P, exist_ok=True)
    os.makedirs(D, exist_ok=True)
    import glob as _glob
    for old in _glob.glob(os.path.join(P, "*.png")):
        os.remove(old)

    def sprite(rows, colours):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in colours: img.putpixel((x, y), colours[ch] + (255,))
        return img

    def write(pid, frames):
        names = []
        for n, f in enumerate(frames):
            f.save(os.path.join(P, f"{pid}_{n}.png"))
            names.append(f"{MOD}:{pid}_{n}")
        wj(os.path.join(D, f"{pid}.json"), {"textures": names})

    # a star that shrinks: four frames from big to a point
    star = [["...#....", "...#....", "..#@#...", "##@O@##.", "..#@#...", "...#....", "...#....", "........"],
            ["........", "...#....", "..#@#...", ".#@O@#..", "..#@#...", "...#....", "........", "........"],
            ["........", "........", "...#....", "..#O#...", "...#....", "........", "........", "........"],
            ["........", "........", "........", "...O....", "........", "........", "........", "........"]]
    # a jagged spark
    spark = [["#.....#.", ".#...#..", "..#O#...", "...O@...", "..#O#...", ".#...#..", "#.....#.", "........"],
             ["........", ".#...#..", "..#O#...", "...@....", "..#O#...", ".#...#..", "........", "........"],
             ["........", "........", "..#.#...", "...O....", "..#.#...", "........", "........", "........"]]
    # a drop
    drop = [["........", "...#....", "..#O#...", ".#O@O#..", ".#OOO#..", "..###...", "........", "........"],
            ["........", "........", "...#....", "..#O#...", "..#@#...", "...#....", "........", "........"],
            ["........", "........", "........", "...#....", "..#O#...", "...#....", "........", "........"]]
    # a wisp / flame tongue, from full to a flicker
    tongue = [["...#....", "..#O#...", "..#O#...", ".#O@O#..", ".#O@O#..", ".#O@O#..", "..#O#...", "........"],
              ["........", "...#....", "..#O#...", "..#@#...", ".#O@O#..", "..#O#...", "........", "........"],
              ["........", "........", "...#....", "..#O#...", "..#@#...", "...#....", "........", "........"],
              ["........", "........", "........", "...#....", "...O....", "........", "........", "........"]]
    # a puff, opening out and thinning
    puff = [["........", "........", "...##...", "..#OO#..", "..#OO#..", "...##...", "........", "........"],
            ["........", "..###...", ".#OO#O..", ".#O..O#.", ".#O..O#.", "..#OO#..", "...##...", "........"],
            [".#..##..", "#.....#.", "........", "#......#", "#......#", "........", ".#....#.", "..##.#.."]]
    # a flake
    flake = [["...#....", ".#.#.#..", "..###...", "###@###.", "..###...", ".#.#.#..", "...#....", "........"],
             ["........", "...#....", "..#.#...", ".#.@.#..", "..#.#...", "...#....", "........", "........"]]
    # runes: three glyphs, each fading as it rises
    glyphs = [[".####...", ".#..#...", ".####...", ".#.#....", ".#..#...", "........", "........", "........"],
              ["..#.....", ".###....", "..#.....", ".#.#....", "#...#...", "........", "........", "........"],
              ["#...#...", ".#.#....", "..#.....", ".#.#....", "#...#...", "........", "........", "........"]]
    # a strike: a cross of light, fanning out
    strike = [["........", "........", "...@....", "..@O@...", "...@....", "........", "........", "........"],
              ["#.....#.", ".#...#..", "...@....", "..@O@...", "...@....", ".#...#..", "#.....#.", "........"],
              ["#.....#.", "........", "........", "...#....", "........", "........", "#.....#.", "........"]]

    def tone(c, k): return shade(c, k)
    tower_fire = {"flame_storm": ((255, 250, 210), (255, 206, 80), (220, 120, 30)),
                  "flame_sea": ((230, 255, 250), (110, 236, 222), (30, 150, 160)),
                  "flame_shade": ((240, 230, 255), (176, 130, 255), (96, 50, 180))}
    for pid, (hi, mid, lo) in tower_fire.items():
        write(pid, [sprite(r, {"@": hi, "O": mid, "#": lo}) for r in tongue])
    write("soul_wisp", [sprite(r, {"@": (236, 250, 255), "O": (120, 220, 255), "#": (60, 120, 200)}) for r in tongue])
    write("storm_spark", [sprite(r, {"@": (255, 255, 255), "O": (255, 240, 140), "#": (160, 200, 255)}) for r in spark])
    write("sea_spray", [sprite(r, {"@": (240, 255, 255), "O": (120, 210, 220), "#": (40, 120, 150)}) for r in drop])
    write("water_drop", [sprite(r, {"@": (220, 240, 255), "O": (90, 150, 230), "#": (40, 80, 170)}) for r in drop])
    write("glint", [sprite(r, {"@": (255, 244, 170), "O": (255, 255, 255), "#": (230, 180, 60)}) for r in star])
    write("rune", [sprite(r, {"#": c}) for r, c in zip(glyphs + glyphs[:1], [(170, 230, 255), (150, 200, 250), (120, 160, 230), (90, 120, 200)])])
    write("strike", [sprite(r, {"@": (255, 255, 230), "O": (255, 255, 255), "#": (255, 220, 120)}) for r in strike])
    write("dust", [sprite(r, {"O": (176, 168, 156), "#": (126, 120, 112)}) for r in puff])
    write("frost", [sprite(r, {"@": (255, 255, 255), "#": (190, 226, 255)}) for r in flake])
    write("chaos_mote", [sprite(r, {"@": (255, 210, 255), "O": (210, 110, 255), "#": (110, 50, 170)}) for r in star[:3]])
    write("smoke", [sprite(r, {"O": (120, 116, 112), "#": (78, 74, 72)}) for r in puff])
    write("gloom", [sprite(r, {"O": (96, 52, 132), "#": (50, 24, 72)}) for r in puff])
    # an ember: a hot point with a cooling rim, going out
    ember = [["........", "...#....", "..#O#...", ".#O@O#..", "..#O#...", "...#....", "........", "........"],
             ["........", "........", "...#....", "..#@#...", "...#....", "........", "........", "........"],
             ["........", "........", "........", "...O....", "........", "........", "........", "........"]]
    write("ember", [sprite(r, {"@": (255, 244, 180), "O": (255, 170, 60), "#": (200, 80, 30)}) for r in ember])


def gen_sprinkler(assets, data):
    """A bronze rose on a short pipe, drawn one texture pixel to one model pixel."""
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    o, d, b, l, h = (64, 36, 18), (122, 72, 34), (176, 112, 52), (224, 164, 90), (255, 220, 150)

    def pipe(x, y):
        return l if x % 16 == 0 else d if x % 16 >= 1 and x % 3 == 2 else b

    def rose(x, y):
        # the face of the rose: a ring of holes round a boss
        r = math.hypot(x - 7.5, y - 7.5)
        if r > 7.6: return o
        if r > 6.6: return l
        if r < 1.6: return h
        if r < 2.4: return o
        if (round(r) in (4, 6)) and ((x + y) % 2 == 0): return (30, 22, 18)
        return b

    def rim(x, y):
        return l if y == 0 else o if y == 3 else b
    paint_hi(pipe).save(os.path.join(T, "sprinkler_pipe.png"))
    paint_hi(rose).save(os.path.join(T, "sprinkler_rose.png"))
    paint_hi(rim).save(os.path.join(T, "sprinkler_rim.png"))
    tx = {"particle": t("sprinkler_rose"), "pipe": t("sprinkler_pipe"), "rose": t("sprinkler_rose"), "rim": t("sprinkler_rim")}

    def box(a, bb, side, side_uv, up, up_uv, down, down_uv):
        faces = {k: {"texture": f"#{side}", "uv": list(side_uv)} for k in ("north", "south", "east", "west")}
        faces["up"] = {"texture": f"#{up}", "uv": list(up_uv)}
        faces["down"] = {"texture": f"#{down}", "uv": list(down_uv)}
        return {"from": list(a), "to": list(bb), "faces": faces}
    els = [box((5, 15, 5), (11, 16, 11), "rim", [0, 0, 6, 1], "rim", [0, 0, 6, 6], "rim", [0, 0, 6, 6]),
           box((7, 12, 7), (9, 15, 9), "pipe", [0, 0, 2, 3], "pipe", [0, 0, 2, 2], "pipe", [0, 0, 2, 2]),
           box((4, 9, 4), (12, 12, 12), "rim", [0, 0, 8, 3], "rim", [0, 0, 8, 8], "rose", [0, 0, 16, 16])]
    wj(os.path.join(M, "sprinkler.json"), {"parent": "minecraft:block/block", "textures": tx, "elements": els})
    wj(os.path.join(assets, "blockstates/sprinkler.json"), {"variants": {"": {"model": f"{MOD}:block/sprinkler"}}})
    wj(os.path.join(assets, "items/sprinkler.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/sprinkler"}})
    wj(os.path.join(data, "recipe/sprinkler.json"), {"type": "minecraft:crafting_shaped", "category": "building",
                                                     "key": {"C": "minecraft:copper_ingot", "I": "minecraft:iron_bars"}, "pattern": [" C ", "CIC"],
                                                     "result": {"id": f"{MOD}:sprinkler", "count": 2}})


def gen_sliding_doors(assets, data):
    """Sliding doors: a panel a block square, coffered, that stacks with others into a door of any size."""
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    mats = {"bronze": ("minecraft:copper_ingot", "minecraft:gold_nugget"), "sky": (f"{MOD}:olympian_marble", "minecraft:gold_nugget"),
            "sea": ("minecraft:prismarine_bricks", "minecraft:prismarine_shard"), "shade": ("minecraft:polished_blackstone", "minecraft:amethyst_shard")}
    for n, key in enumerate(("bronze", "sky", "sea", "shade")):
        field, frame, band, stud, glow, emblem, style = DOOR_SPECS[key]
        g = grain(311 + n, 0.04)
        name = f"{key}_sliding_door"

        # the face: a frame lit on its top and left and shadowed on its bottom and right, so that panels set side by
        # side meet in a groove; a band inside it; a sunk panel; a rosette in the middle; studs at the corners
        def face(x, y, field=field, frame=frame, band=band, stud=stud, glow=glow, g=g):
            e = min(x, y, 15 - x, 15 - y)
            if e == 0: return shade(frame, 1.3) if (x == 0 or y == 0) and x != 15 and y != 15 else shade(frame, 0.7)
            if e == 1: return band if (x + y) % 2 else shade(band, 0.86)
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)): return stud
            if e == 2: return shade(frame, 1.0)
            if e == 3: return shade(frame, 0.82) if (x == 3 or y == 3) else shade(field, 1.22)
            dd = abs(x - 7.5) + abs(y - 7.5)
            if dd < 1.5: return shade(glow, 1.12)
            if 3.0 <= dd < 4.0: return glow
            return shade(field, g[y][x] * 0.92)
        paint_hi(face).save(os.path.join(T, f"{name}.png"))

        # the edge: the frame's metal, lit down its middle
        def edge(x, y, frame=frame, band=band):
            return shade(band, 1.1) if x in (7, 8) and y % 4 else shade(frame, 1.15) if x in (6, 9) else shade(frame, 0.9)
        paint_hi(edge).save(os.path.join(T, f"{name}_edge.png"))
        el = {"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
            "north": {"texture": "#face", "uv": [0, 0, 16, 16]}, "south": {"texture": "#face", "uv": [0, 0, 16, 16]},
            "east": {"texture": "#edge", "uv": [6, 0, 10, 16]}, "west": {"texture": "#edge", "uv": [6, 0, 10, 16]},
            "up": {"texture": "#edge", "uv": [0, 6, 16, 10], "rotation": 90}, "down": {"texture": "#edge", "uv": [0, 6, 16, 10], "rotation": 90}}}
        wj(os.path.join(M, f"{name}.json"), {"parent": "minecraft:block/block",
                                             "textures": {"particle": t(name), "face": t(name), "edge": t(f"{name}_edge")}, "elements": [el]})
        variants = {}
        for f, r in {"north": 0, "east": 90, "south": 180, "west": 270}.items():
            for shift in range(5):
                for powered in ("false", "true"):
                    variants[f"facing={f},powered={powered},shift={shift}"] = {"model": f"{MOD}:block/{name}", "y": r}
        wj(os.path.join(assets, f"blockstates/{name}.json"), {"variants": variants})
        wj(os.path.join(assets, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{name}"}})
        m = mats[key]
        wj(os.path.join(data, f"recipe/{name}.json"), {"type": "minecraft:crafting_shaped", "category": "redstone", "key": {"C": m[0], "G": m[1]},
                                                       "pattern": ["CGC", "CCC"], "result": {"id": f"{MOD}:{name}", "count": 4}})


def gen_puzzle2(assets, data):
    """The dial hall and the sliding stones: dials, frescoes, carved stones and sockets."""
    T = os.path.join(assets, "textures/block")
    M = os.path.join(assets, "models/block")
    t = lambda n: f"{MOD}:block/{n}"
    sigils = ["sun", "trident", "skull", "star"]
    stone = ((52, 50, 56), (96, 94, 102), (128, 126, 134), (160, 158, 166), (196, 194, 202))
    bronze = ((80, 46, 20), (150, 94, 40), (196, 132, 60), (240, 190, 110))
    g = grain(233, 0.04)

    def block_stone(x, y):
        o, d, b, l, h = stone
        if x in (0, 15) or y in (0, 15): return d
        if x == 1 or y == 1: return l
        if x == 14 or y == 14: return d
        return shade(b, g[y][x])

    def glyph(name, x, y):
        ex, ey = x - 4, y - 4
        return 0 <= ex < 8 and 0 <= ey < 8 and EMBLEMS[name][ey][ex] in "#+"

    # ---- dials: a stone face, a bronze ring, the sigil inlaid in gold; the sides plain dressed stone
    paint_hi(block_stone).save(os.path.join(T, "dial_stone_side.png"))
    for n, name in enumerate(sigils):
        def face(x, y, name=name):
            r = math.hypot(x - 7.5, y - 7.5)
            if 6.0 < r < 7.4: return bronze[3] if (x + y) < 15 else bronze[1]
            if 5.2 < r <= 6.0: return bronze[0]
            if glyph(name, x, y): return GOLD_HI if y < 8 else GOLD
            if r <= 5.2: return stone[1]
            return block_stone(x, y)
        paint_hi(face).save(os.path.join(T, f"dial_stone_{n}.png"))
        wj(os.path.join(M, f"dial_stone_{n}.json"), {"parent": "minecraft:block/orientable", "textures": {"front": t(f"dial_stone_{n}"), "side": t("dial_stone_side"), "top": t("dial_stone_side")}})
    wj(os.path.join(assets, "blockstates/dial_stone.json"), {"variants": {f"facing={f},sigil={n}": {"model": f"{MOD}:block/dial_stone_{n}", "y": r}
                                                                           for f, r in {"north": 0, "east": 90, "south": 180, "west": 270}.items() for n in range(4)}})
    wj(os.path.join(M, "dial_stone.json"), {"parent": f"{MOD}:block/dial_stone_0"})
    wj(os.path.join(assets, "items/dial_stone.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/dial_stone_0"}})

    # ---- frescoes: a plaster panel with a painted border and its sigil in ochre; lit, the paint glows gold
    for n, name in enumerate(sigils):
        for lit in (False, True):
            def fresco(x, y, name=name, lit=lit):
                plaster = (226, 214, 188) if not lit else (250, 236, 200)
                if x in (0, 15) or y in (0, 15): return (120, 64, 40)
                if x in (1, 14) or y in (1, 14): return (176, 96, 56) if not lit else GOLD
                if glyph(name, x, y): return (150, 70, 40) if not lit else GOLD_HI
                if (x * 3 + y * 7) % 23 == 0: return shade(plaster, 0.9)
                return plaster
            nm = f"fresco_tile_{n}{'_lit' if lit else ''}"
            paint_hi(fresco).save(os.path.join(T, nm + ".png"))
            cube(assets, nm)
    wj(os.path.join(assets, "blockstates/fresco_tile.json"), {"variants": {f"lit={'true' if l else 'false'},sigil={n}": {"model": f"{MOD}:block/fresco_tile_{n}{'_lit' if l else ''}"}
                                                                            for n in range(4) for l in (False, True)}})
    wj(os.path.join(M, "fresco_tile.json"), {"parent": f"{MOD}:block/fresco_tile_0"})
    wj(os.path.join(assets, "items/fresco_tile.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/fresco_tile_0"}})
    for n in range(4):
        for lit in (False, True):
            p = os.path.join(assets, f"items/fresco_tile_{n}{'_lit' if lit else ''}.json")
            if os.path.exists(p): os.remove(p)
            p = os.path.join(assets, f"blockstates/fresco_tile_{n}{'_lit' if lit else ''}.json")
            if os.path.exists(p): os.remove(p)

    # ---- the sliding stone: a heavy block, arrows cut in its sides, a spiral on top
    def slide_side(x, y):
        c = block_stone(x, y)
        arrow = {(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9), (7, 10), (8, 10), (7, 11), (8, 11)}
        if (x, y) in arrow: return stone[0]
        if (x - 1, y) in arrow or (x, y - 1) in arrow: return stone[4] if (x, y) not in arrow else c
        return c

    def slide_top(x, y):
        c = block_stone(x, y)
        r, a = math.hypot(x - 7.5, y - 7.5), math.atan2(y - 7.5, x - 7.5)
        if 1.0 < r < 6.0 and abs(((r - a * 1.1) % 2.6) - 1.3) < 0.45: return stone[0]
        return c
    paint_hi(slide_side).save(os.path.join(T, "slide_stone_side.png"))
    paint_hi(slide_top).save(os.path.join(T, "slide_stone_top.png"))
    cube(assets, "slide_stone", side="slide_stone_side", top="slide_stone_top")

    # ---- the socket: a floor tile with a sunk square and a ring of runes round it; lit, the runes glow
    for lit in (False, True):
        def sock(x, y, lit=lit):
            e = min(x, y, 15 - x, 15 - y)
            if e == 0: return stone[1]
            if e == 1: return stone[3]
            if e == 2: return (90, 180, 230) if lit and (x + y) % 2 else (180, 240, 255) if lit else stone[0] if (x + y) % 3 == 0 else stone[2]
            if e == 3: return stone[0]
            return stone[1] if not lit else (60, 110, 150)
        nm = f"stone_socket{'_lit' if lit else ''}"
        paint_hi(sock).save(os.path.join(T, nm + ".png"))
        wj(os.path.join(M, f"{nm}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": t(nm)}})
    wj(os.path.join(assets, "blockstates/stone_socket.json"), {"variants": {"lit=false": {"model": f"{MOD}:block/stone_socket"}, "lit=true": {"model": f"{MOD}:block/stone_socket_lit"}}})
    wj(os.path.join(assets, "items/stone_socket.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:block/stone_socket"}})
