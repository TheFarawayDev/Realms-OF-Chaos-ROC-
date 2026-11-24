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
    return paint(fn, hash(kind) & 0xFFFF)


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
