"""The two screens that hold real items - the Orb of Plenty's and the Keystone Pedestal's - their backgrounds, the
faint pictures shown in their empty slots, their words; and the pedestal's new model, three cradles on its top that
each hold a lit keystone once it is set."""
import math
import os
import random

from PIL import Image

MOD = "realms_of_chaos"
W, H = 176, 186
SPLIT = 92                          # where the block's half of a screen ends and the player's begins
BASE, DARK, WELL, PARCH, PARCH_D = (38, 32, 80), (22, 18, 43), (28, 23, 60), (214, 196, 160), (150, 128, 96)
GOLD, GOLD_D = (236, 196, 84), (150, 110, 40)
LINES = {"zeus": (242, 201, 76), "poseidon": (47, 181, 200), "hades": (176, 110, 230)}
CRADLE = {"poseidon": -1, "zeus": 0, "hades": 1}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Canvas:
    def __init__(self, accent, seed):
        self.img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        self.px = self.img.load()
        rnd = random.Random(seed)
        for y in range(H):
            for x in range(W):
                corner = (x < 2 or x > W - 3) and (y < 2 or y > H - 3)
                if corner and (x in (0, W - 1) or y in (0, H - 1)):
                    continue
                edge = x < 2 or x > W - 3 or y < 2 or y > H - 3
                if y < SPLIT:
                    c = DARK if edge else shade(BASE, 1 + rnd.uniform(-0.03, 0.03))
                    if not edge and y < 16:
                        c = mix(shade(accent, 0.34), shade(accent, 0.2), y / 16)
                    if not edge and y == 16:
                        c = GOLD if x % 2 == 0 else shade(GOLD, 0.75)
                else:
                    c = shade(PARCH_D, 0.8) if edge else shade(PARCH, 1 + rnd.uniform(-0.025, 0.025))
                    if not edge and y == SPLIT:
                        c = PARCH_D
                self.px[x, y] = c + (255,)
        for cx in (4, W - 5):
            self.px[cx, 4] = GOLD + (255,)
        # the player's inventory and hotbar
        for r in range(3):
            for c in range(9):
                self.well(8 + c * 18, 104 + r * 18, PARCH_D, mix(PARCH, (255, 255, 255), 0.5), shade(PARCH, 0.82))
        for c in range(9):
            self.well(8 + c * 18, 162, PARCH_D, mix(PARCH, (255, 255, 255), 0.5), shade(PARCH, 0.82))

    def put(self, x, y, c, a=255):
        if 0 <= x < W and 0 <= y < H:
            if a >= 255:
                self.px[x, y] = tuple(c[:3]) + (255,)
            else:
                old = self.px[x, y]
                self.px[x, y] = mix(old, c, a / 255) + (255,)

    def well(self, x0, y0, rim, lip, fill=WELL):
        for dy in range(-1, 17):
            for dx in range(-1, 17):
                if dx in (-1, 16) or dy in (-1, 16):
                    self.put(x0 + dx, y0 + dy, rim if dx == -1 or dy == -1 else lip)
                else:
                    self.put(x0 + dx, y0 + dy, fill)

    def frame(self, x0, y0, x1, y1, c, hi=None):
        for x in range(x0, x1 + 1):
            self.put(x, y0, hi or c)
            self.put(x, y1, c)
        for y in range(y0, y1 + 1):
            self.put(x0, y, hi or c)
            self.put(x1, y, c)

    def line(self, x0, y0, x1, y1, c, a=255):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.put(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c, a)


def orb_screen(path):
    cv = Canvas((214, 120, 255), "orb")
    cx, cy = 88, 45
    # a disc of darker stone round the pattern, ringed in gold, with the ring of lights' sockets in it
    for y in range(cy - 25, cy + 26):
        for x in range(cx - 25, cx + 26):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 24:
                cv.put(x, y, shade(BASE, 0.72 + 0.12 * (1 - d / 24)))
            if 23 <= d <= 24.2:
                cv.put(x, y, GOLD if (x + y) % 3 else GOLD_D)
            if 21.4 <= d <= 22.2:
                cv.put(x, y, shade(GOLD_D, 0.8), 140)
    # gold channels from the core and the ichor into the disc, and from it out to what it makes
    for x in range(38, 67):
        cv.put(x, 30, GOLD_D); cv.put(x, 29, shade(GOLD, 0.9), 120)
    cv.line(38, 56, 66, 52, GOLD_D)
    for x in range(113, 129):
        cv.put(x, 45, GOLD_D); cv.put(x, 44, shade(GOLD, 0.9), 120)
    # the core's and the ichor's wells, each in a small frame; the pattern's in the middle of the disc
    for (x, y, col) in ((20, 22, (232, 214, 120)), (20, 48, (240, 120, 90))):
        cv.frame(x - 3, y - 3, x + 18, y + 18, shade(col, 0.5), shade(col, 0.8))
        cv.well(x, y, DARK, shade(BASE, 1.35))
    cv.well(80, 37, DARK, shade(GOLD, 0.7), shade(WELL, 0.85))
    # what it has made: four wells in a gold-edged tray
    cv.frame(128, 25, 167, 64, GOLD_D, GOLD)
    for i in range(4):
        cv.well(131 + (i % 2) * 18, 28 + (i // 2) * 18, DARK, shade(BASE, 1.35))
    # a band under them for the orb's words
    for x in range(8, 168):
        cv.put(x, 71, shade(GOLD_D, 0.8), 90)
    cv.img.save(path)


def pedestal_screen(path):
    cv = Canvas((165, 90, 214), "pedestal")
    stone, light, dark = (128, 126, 120), (164, 162, 154), (72, 70, 68)
    rnd = random.Random(5)
    x0, x1, top, sill = 28, 148, 18, 66
    # the arch: two piers and a lintel of worn ashlar, moss in the joints
    def ashlar(x, y, base):
        row = (y - top) // 5
        joint = (y - top) % 5 == 4 or ((x + (3 if row % 2 else 0)) % 10 == 0)
        c = shade(dark, 1.0) if joint else shade(base, 1 + rnd.uniform(-0.06, 0.06))
        if joint and rnd.random() < 0.08:
            c = (86, 120, 60)
        return c
    for y in range(top, sill + 2):
        for x in range(x0, x1 + 1):
            pier = x < 46 or x > 130
            lintel = y < 30
            if not (pier or lintel):
                continue
            c = ashlar(x, y, stone)
            if x in (x0, 46 - 1) and pier and not lintel or y == top:
                c = light
            if x in (x1, 131) and pier and not lintel:
                c = shade(dark, 0.9)
            cv.put(x, y, c)
    # capitals on the piers and a sill under the opening
    for x in range(x0 - 2, 48):
        cv.put(x, 30, light); cv.put(x, 31, shade(stone, 0.8))
    for x in range(129, x1 + 3):
        cv.put(x, 30, light); cv.put(x, 31, shade(stone, 0.8))
    for x in range(x0 - 3, x1 + 4):
        cv.put(x, sill, light); cv.put(x, sill + 1, stone); cv.put(x, sill + 2, dark)
    # the opening: the dark beyond the dead gate
    for y in range(30, 64):
        for x in range(46, 130):
            t = (y - 30) / 34
            cv.put(x, y, mix((16, 12, 30), (30, 22, 52), t))
    # the three sockets carved in the lintel, and a cradle under each in the opening
    for line, k in CRADLE.items():
        sx = 80 + k * 30
        cv.frame(sx + 3, 19, sx + 12, 28, dark, shade(stone, 1.25))
        for y in range(20, 28):
            for x in range(sx + 4, sx + 12):
                cv.put(x, y, (32, 26, 46))
        col = LINES[line]
        cv.frame(sx - 3, 39, sx + 18, 60, shade(col, 0.45), shade(col, 0.75))
        cv.well(sx, 42, DARK, shade(col, 0.55), shade(WELL, 0.9))
        # a small mark of its tower under the cradle
        for dx in range(-2, 3):
            cv.put(sx + 8 + dx, 62, shade(col, 0.8))
    cv.img.save(path)


def ghost(src, out, frame=0, tint=(214, 206, 240)):
    """An item's picture as a faint silhouette, for an empty slot that wants it."""
    img = Image.open(src).convert("RGBA")
    img = img.crop((0, frame * 16, 16, frame * 16 + 16)) if img.size[1] > 16 else img
    if img.size != (16, 16):
        img = img.resize((16, 16), Image.LANCZOS)
    a = img.split()[3]
    res = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if a.getpixel((x, y)) < 60:
                continue
            edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or a.getpixel((x + dx, y + dy)) < 60
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            res.putpixel((x, y), tint + ((110 if edge else 44),))
    res.save(out)


def pattern_ghost(out):
    """The pattern slot's picture: a dotted square with an eye of light, to say an item is shown here, not given."""
    res = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(1, 15):
        if i % 3 != 0:
            for p in ((i, 1), (i, 14), (1, i), (14, i)):
                res.putpixel(p, (214, 206, 240, 100))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, (y - 7.5) * 1.8)
            if 3.0 <= d <= 4.2:
                res.putpixel((x, y), (214, 206, 240, 110))
            elif math.hypot(x - 7.5, y - 7.5) <= 1.3:
                res.putpixel((x, y), (240, 230, 255, 150))
    res.save(out)


# ---- the pedestal's model ---------------------------------------------------------------------------------

def gem_texture(col, out):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5) / 16, (y + 0.5) / 16
            f = 1.25 - 0.55 * v + 0.2 * (0.5 - abs(u - 0.5))
            if (x + y) % 5 == 0:
                f += 0.15
            c = shade(col, f)
            if x < 6 and y < 6 and x + y < 7:
                c = mix(c, (255, 255, 255), 0.55)
            img.putpixel((x, y), c + (255,))
    img.save(out)


def box(frm, to, tex, rotation=None, emit=0, uv_all=None):
    faces = {}
    x1, y1, z1 = frm
    x2, y2, z2 = to
    for face in ("north", "south", "east", "west", "up", "down"):
        f = {"texture": tex}
        if uv_all:
            f["uv"] = uv_all
        faces[face] = f
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        el["rotation"] = rotation
    if emit:
        el["light_emission"] = emit
    return el


def pedestal_models(assets, wj):
    T = os.path.join(assets, "textures/block")
    p = lambda k: f"{MOD}:block/gate_pedestal_{k}"
    for line, col in LINES.items():
        gem_texture(col, os.path.join(T, f"gate_pedestal_gem_{line}.png"))
    # the pedestal itself: the old build, with a broader top that carries three cradles
    base = {"parent": "minecraft:block/block", "textures": {"particle": p("plinth"), "plinth": p("plinth"), "plain": p("plain"), "shaft": p("shaft"),
                                                            "band": p("band"), "capital": p("capital"), "top": p("plain"), "under": f"{MOD}:block/altar_base"},
            "elements": []}
    E = base["elements"]
    def part(frm, to, side, top, bottom):
        el = box(frm, to, f"#{side}")
        el["faces"]["up"]["texture"] = f"#{top}"
        el["faces"]["down"]["texture"] = f"#{bottom}"
        E.append(el)
    part((1, 0, 1), (15, 2, 15), "plinth", "plain", "under")
    part((3, 2, 3), (13, 3, 13), "plinth", "plain", "plain")
    part((5, 3, 5), (11, 10, 11), "shaft", "plain", "plain")
    part((4, 10, 4), (12, 11, 12), "band", "band", "band")
    part((2, 11, 2), (14, 13, 14), "capital", "plain", "plain")
    part((1, 13, 2), (15, 14, 14), "capital", "top", "plain")
    for k in CRADLE.values():
        c = 8 + k * 4.5
        # a cradle: a gold ring on a little foot, four claws rising
        E.append(box((c - 1.75, 14, 6.25), (c + 1.75, 14.5, 9.75), "#band"))
        for dx, dz in ((-1.5, -1.5), (1.0, -1.5), (-1.5, 1.0), (1.0, 1.0)):
            E.append(box((c + dx, 14.5, 8 + dz), (c + dx + 0.5, 15.75, 8 + dz + 0.5), "#band"))
    wj(os.path.join(assets, "models/block/gate_pedestal.json"), base)
    parts = [{"apply": {"model": f"{MOD}:block/gate_pedestal"}}]
    for line, k in CRADLE.items():
        c = 8 + k * 4.5
        gem = box((c - 1, 14.6, 7), (c + 1, 16.6, 9), "#gem", rotation={"angle": 45, "axis": "y", "origin": [c, 15.6, 8]}, emit=15)
        wj(os.path.join(assets, f"models/block/gate_pedestal_gem_{line}.json"),
           {"textures": {"gem": f"{MOD}:block/gate_pedestal_gem_{line}", "particle": f"{MOD}:block/gate_pedestal_gem_{line}"}, "elements": [gem]})
        parts.append({"when": {line: "true"}, "apply": {"model": f"{MOD}:block/gate_pedestal_gem_{line}"}})
    # awake: the band round the shaft lights with the gate's colour
    glow = box((3.9, 9.9, 3.9), (12.1, 11.1, 12.1), "#glow", emit=15)
    del glow["faces"]["up"], glow["faces"]["down"]
    wj(os.path.join(assets, "models/block/gate_pedestal_awake.json"),
       {"textures": {"glow": f"{MOD}:block/gate_pedestal_glow", "particle": f"{MOD}:block/gate_pedestal_glow"}, "elements": [glow]})
    g = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            g.putpixel((x, y), mix((120, 60, 200), (230, 170, 255), 0.5 + 0.5 * math.sin(x * 0.8)) + (255,))
    g.save(os.path.join(T, "gate_pedestal_glow.png"))
    parts.append({"when": {"awake": "true"}, "apply": {"model": f"{MOD}:block/gate_pedestal_awake"}})
    wj(os.path.join(assets, "blockstates/gate_pedestal.json"), {"multipart": parts})


LANG = {
    "orb.incomplete": "The shrine is not whole",
    "orb.needs_plinth": "Set the orb on a Plinth of Plenty",
    "orb.needs_columns": "Columns of Plenty: %s of 4",
    "orb.no_core": "Set a core in the orb",
    "orb.no_core.hint": "Thunder, tide or shade",
    "orb.bringing": "Bringing forth %s",
    "orb.making": "Making %s",
    "orb.cost": "%s ichor each.",
    "orb.next": "Next in %s",
    "orb.next.home": "Next in %s (at home)",
    "orb.needs_ichor": "Waiting for ichor: %s each",
    "orb.refused": "The orb will not make that",
    "orb.slot.core": "Core",
    "orb.slot.core.hint": "A thunder, tide or shade core. It brings forth its own resource",
    "orb.slot.ichor": "Divine Ichor",
    "orb.slot.ichor.hint": "Pays for each copy of the pattern",
    "orb.slot.pattern": "Pattern",
    "orb.slot.pattern.hint": "Click with any item to have the orb make it. The item stays yours",
    "orb.slot.pattern.set": "The pattern",
    "orb.slot.pattern.set.hint": "Click with another item to change it, or with an empty hand to clear it",
    "orb.slot.made": "Made",
    "orb.slot.made.hint": "What the orb makes waits here if no chest stands by its plinth. Hoppers under the plinth take it",
    "pedestal.no_gate": "No gate stands behind this pedestal",
    "pedestal.awake": "The gate is awake",
    "pedestal.awake.hint": "Step through the arch",
    "pedestal.set": "Keystones set: %s of 3",
    "pedestal.missing": "Wanted: %s",
    "pedestal.tower.zeus": "Sky",
    "pedestal.tower.poseidon": "Tides",
    "pedestal.tower.hades": "Shades",
    "pedestal.cradle.empty": "Lay this keystone here to set it in the arch",
    "pedestal.cradle.set": "Set in the arch",
}


def lang():
    return {f"screen.{MOD}.{k}": v for k, v in LANG.items()}


def gen(assets, wj):
    G = os.path.join(assets, "textures/gui")
    S = os.path.join(G, "sprites/container/slot")
    os.makedirs(S, exist_ok=True)
    orb_screen(os.path.join(G, "screen_orb.png"))
    pedestal_screen(os.path.join(G, "screen_pedestal.png"))
    I = os.path.join(assets, "textures/item")
    ghost(os.path.join(I, "thunder_core.png"), os.path.join(S, "orb_core.png"))
    ghost(os.path.join(I, "divine_ichor.png"), os.path.join(S, "orb_ichor.png"))
    pattern_ghost(os.path.join(S, "orb_pattern.png"))
    for line in LINES:
        ghost(os.path.join(I, f"keystone_{line}.png"), os.path.join(S, f"keystone_{line}.png"))
    pedestal_models(assets, wj)
