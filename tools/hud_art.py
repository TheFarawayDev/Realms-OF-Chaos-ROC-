"""The mod's on-screen artwork: boss bars, the cast bar and the cards that tips and notices are written on.

A server cannot draw on a player's screen - but it can name a boss bar, a name can be written in a font, and a
font's letters can be pictures. So a boss bar's name here is a row of such pictures: a frame laid exactly over the
game's own bar, a fill of the right length, the god's name in block lettering. Spaces of exact width (some of
them negative) move the pen between the pieces, and the whole row is always the same width, so the game centres
it where the pictures expect to be.

generate.py calls gen_hud(); the measured widths are written to HudGlyphs.java so the code composing a bar can
never disagree with the pictures.
"""
import json
import os
from PIL import Image

from make_font import GLYPHS

INK, TROUGH, PLATE = (5, 4, 11), (13, 11, 26), (24, 20, 48)
BASE, WELL = (38, 32, 80), (28, 23, 60)
TOWERS = {
    "zeus": ((245, 184, 46), ["...##", "..##.", ".###.", "..##.", ".##..", "##...", "#...."]),
    "poseidon": ((43, 183, 204), ["#.#.#", "#.#.#", "#####", ".###.", "..#..", "..#..", "..#.."]),
    "hades": ((165, 90, 214), ["#...#", "#...#", "#####", "#.#.#", "..#..", "..#..", "..#.."]),
}
CARDS = {"tip": ((85, 230, 245), "TIP"), "insight": ((255, 170, 60), "INSIGHT"), "notice": ((236, 208, 150), "NOTICE"),
         "aegis": ((126, 235, 255), "AEGIS"), "wrath": ((255, 90, 60), "WRATH"), "boon": ((120, 230, 140), "BOON")}
AEGIS, WRATH = (126, 235, 255), (255, 90, 60)
# small pictures that sit on a bar's plaque or above the hotbar: each an 8x8 cell, seven rows used
ICONS = {
    "pip_off": ((70, 62, 120), [".......", "..###..", ".#...#.", ".#...#.", ".#...#.", "..###..", "......."]),
    "pip_on": ((245, 184, 46), [".......", "..###..", ".#####.", ".#####.", ".#####.", "..###..", "......."]),
    "parry_ready": ((126, 235, 255), ["...#...", "..###..", ".##.##.", "##...##", ".##.##.", "..###..", "...#..."]),
    "parry_spent": ((70, 62, 120), ["...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#..."]),
    "riposte": ((255, 90, 60), ["......#", ".....##", "#...##.", ".#.##..", "..##...", ".#.#...", "#...#.."]),
    "pylon": ((126, 235, 255), ["...#...", "..###..", "..###..", "..###..", "..###..", ".#####.", "......."]),
    "pylon_gone": ((60, 70, 100), [".......", ".......", "...#...", "..#.#..", "..###..", ".#####.", "......."]),
    "ring": ((255, 90, 60), [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", ".#####."]),
    "ring_gone": ((90, 50, 60), [".#.#.#.", "#.....#", ".......", "#.....#", ".......", "#.....#", ".#.#.#."]),
}
FRAME_W, FRAME_H, BAR_X, BAR_W = 200, 22, 9, 182
CARD_W, CARD_H = 220, 46
# glyph of the default font -> advance, measured from the game's own ascii.png
ASCII_ADVANCE = {2: "!',.:;i|", 3: "`l", 4: " \"()*I[]t{}", 5: "<>fk", 7: "@~"}


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lettering(px, x, y, text, colour):
    """Writes block capitals straight into a picture; returns the width used."""
    at = x
    for ch in text.upper():
        if ch == " ":
            at += 3
            continue
        rows = GLYPHS[ch].split(",")
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "#":
                    px[at + rx, y + ry] = colour + (255,)
        at += len(rows[0]) + 1
    return at - x - 1


def frame(colour, emblem, plain=False, phase=1):
    """The picture under a bar: a plaque for the name, a trough for the fill, an emblem at each end.

    Each phase of a trial has its own: plain metal first; studded with the aegis's crystal while the shield is up;
    toothed, cracked and glowing once the god's wrath has begun."""
    img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    hi, lo = mix(colour, (255, 255, 255), 0.45), shade(colour, 0.55)
    # the plaque: a dark tablet with cut corners and a rule of the tower's colour along its top
    for y in range(1, 10):          # (row 0 stays empty: a bar at the very top of the screen would lose it)
        inset = max(0, 3 - (y - 1))
        for x in range(34 + inset, 166 - inset):
            edge = x in (34 + inset, 165 - inset) or y == 1
            px[x, y] = (INK if edge else mix(PLATE, shade(colour, 0.3), 0.25)) + (255 if edge else 232,)
    for x in range(38, 162):
        px[x, 2] = (hi if x % 4 else colour) + (255,)
    # the trough: ink outside, a line of metal, then the dark the fill is laid on
    for y in range(10, 21):
        for x in range(6, 194):
            ring = min(x - 6, 193 - x, y - 10, 20 - y)
            c = INK if ring == 0 else (hi if y == 11 or x == 7 else lo) if ring == 1 else TROUGH
            if ring == 1 and (x in (7, 192)) and y in (11, 19):
                c = INK
            px[x, y] = c + (255,)
    if not plain:
        # an emblem block at each end, bevelled like a keystone
        for x0 in (0, FRAME_W - 11):
            for y in range(8, 22):
                for x in range(x0, x0 + 11):
                    lx, ly = x - x0, y - 8
                    edge = lx in (0, 10) or ly in (0, 13)
                    corner = (lx in (0, 10)) and (ly in (0, 13))
                    if corner:
                        continue
                    c = INK if edge else hi if lx == 1 or ly == 1 else lo if lx == 9 or ly == 12 else colour
                    px[x, y] = c + (255,)
            for ry, row in enumerate(emblem):
                for rx, bit in enumerate(row):
                    if bit == "#":
                        px[x0 + 3 + rx, 11 + ry] = INK + (255,)
    else:
        # the cast bar has arrowheads instead: it is a smaller thing
        for x0, step in ((0, 1), (FRAME_W - 1, -1)):
            for i in range(5):
                for y in range(15 - i, 16 + i):
                    px[x0 + step * (5 - i) + (0 if step > 0 else 0), y] = (hi if y <= 15 else lo) + (255,)
        px[FRAME_W - 1, 15] = hi + (255,)
        px[0, 15] = hi + (255,)
    if phase == 2:
        # crystal studs along the trough, facets on the emblem blocks, a cold rule under the plaque's own
        for x in range(12, 190, 6):
            for y in (10, 20):
                px[x, y] = AEGIS + (255,)
                px[x + 1, y] = mix(AEGIS, (255, 255, 255), 0.6) + (255,)
        for x in range(38, 162):
            px[x, 3] = (shade(AEGIS, 0.55) if x % 3 else AEGIS) + (255,)
        for x0 in (0, FRAME_W - 11):
            for i in range(1, 10):
                px[x0 + i, 8 + i] = mix(AEGIS, (255, 255, 255), 0.5) + (150,)
            for y in range(9, 21):
                px[x0 + (0 if x0 else 10), y] = AEGIS + (255,)
    elif phase == 3:
        # teeth above and below the trough, a crack down the plaque, the emblem blocks glowing at their edges
        for x in range(9, 192, 4):
            px[x, 9] = WRATH + (255,)
            px[x + 1, 9] = shade(WRATH, 0.6) + (255,)
            px[x, 21] = WRATH + (255,)
            px[x + 1, 21] = shade(WRATH, 0.6) + (255,)
            px[x, 10] = shade(WRATH, 0.75) + (255,)
        for crack in (52, 128):
            x = crack
            for y in range(2, 10):
                px[x, y] = INK + (255,)
                px[x + 1, y] = shade(WRATH, 0.5) + (255,)
                x += (1 if y % 3 else -2)
        for x0 in (0, FRAME_W - 11):
            for y in range(9, 21):
                for x in (x0 + 1, x0 + 9):
                    px[x, y] = mix(WRATH, (255, 220, 120), 0.4 if y % 2 else 0.0) + (255,)
    return img


def overlay(phase):
    """Laid over the fill: a shine, a notch every tenth, marks where the aegis rises and the wrath begins, and the phase."""
    img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    for x in range(BAR_X, BAR_X + BAR_W):
        px[x, 13] = (255, 255, 255, 46)
        px[x, 17] = (0, 0, 0, 70)
    for i in range(1, 10):
        x = BAR_X + round(BAR_W * i / 10)
        for y in range(13, 18):
            px[x, y] = (0, 0, 0, 90)
    for at, colour in ((0.65, (126, 235, 255)), (0.30, (255, 90, 60))):
        x = BAR_X + round(BAR_W * at)
        for y in range(11, 20):
            px[x, y] = colour + (255,)
            px[x + 1, y] = INK + (200,)
        for dx in (-1, 0, 1):
            px[x + dx, 10] = colour + (255,)
        px[x, 9] = colour + (255,)
    # three gems on the plaque: one lit for every phase reached
    for i in range(3):
        lit = i < phase
        colour = ((240, 240, 250), (126, 235, 255), (255, 90, 60))[i]
        c = colour if lit else shade(BASE, 0.9)
        x0 = 146 + i * 5
        for y in range(5, 8):
            for x in range(x0, x0 + 3):
                px[x, y] = (c if (x, y) != (x0, 5) else mix(c, (255, 255, 255), 0.6 if lit else 0.1)) + (255,)
        px[x0 + 1, 4] = INK + (255,)
        px[x0 + 1, 8] = INK + (255,)
    px[FRAME_W - 1, 21] = (0, 0, 0, 1)      # (so the picture is measured at its full width)
    return img


def card(colour, label):
    """A card for a few lines of text: a tab with its kind, a dark field, a stripe of the kind's colour."""
    img = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 0))
    px = img.load()
    hi = mix(colour, (255, 255, 255), 0.4)
    for y in range(CARD_H):
        for x in range(CARD_W):
            corner = (x in (0, CARD_W - 1)) and (y in (0, CARD_H - 1))
            if corner:
                continue
            edge = x in (0, CARD_W - 1) or y in (0, CARD_H - 1)
            inner = x in (1, CARD_W - 2) or y in (1, CARD_H - 2)
            if edge:
                c = INK
            elif y < 11:
                c = mix(shade(colour, 0.42), shade(colour, 0.26), y / 11) if not inner else shade(colour, 0.6)
            elif y == 11:
                c = colour if x % 2 == 0 else shade(colour, 0.7)
            else:
                c = shade(BASE, 1.25) if inner and (x == 1 or y == 1) else shade(BASE, 0.6) if inner else mix(WELL, BASE, 0.35)
            px[x, y] = c + (255,)
    for y in range(13, CARD_H - 2):                 # the stripe down the left side
        px[3, y] = colour + (255,)
        px[4, y] = shade(colour, 0.6) + (255,)
    lettering(px, 8, 3, label, hi)
    for x in (CARD_W - 5, CARD_W - 9, CARD_W - 13):  # three studs, top right
        px[x, 5] = hi + (255,)
        px[x, 6] = shade(colour, 0.6) + (255,)
    return img


def measured(img):
    """What the game takes a picture's advance to be: its last column that is not empty, and one more."""
    box = img.getchannel("A").getbbox()
    return (box[2] if box else 0) + 1


def gen_hud(assets, mod, java_dir, wj, w):
    out = os.path.join(assets, "textures/gui/hud")
    os.makedirs(out, exist_ok=True)
    providers, art = [], {}
    # spaces: a power of two in either direction, so any gap is a handful of them
    advances = {}
    for k in range(9):
        advances[chr(0xF810 + k)] = 1 << k
        advances[chr(0xF820 + k)] = -(1 << k)
    advances[chr(0xE220)] = 3                        # the space between words of block lettering
    providers.append({"type": "space", "advances": advances})

    def picture(name, img, glyph, ascent):
        img.save(os.path.join(out, name + ".png"))
        providers.append({"type": "bitmap", "file": f"{mod}:gui/hud/{name}.png", "ascent": ascent, "height": img.size[1], "chars": [glyph]})
        art[name] = (glyph, measured(img))

    for i, (name, (colour, emblem)) in enumerate(TOWERS.items()):
        picture("frame_" + name, frame(colour, emblem), chr(0xE100 + i), 11)
        picture("frame_" + name + "_aegis", frame(colour, emblem, phase=2), chr(0xE130 + i * 2), 11)
        picture("frame_" + name + "_wrath", frame(colour, emblem, phase=3), chr(0xE131 + i * 2), 11)
    for phase in (1, 2, 3):
        picture(f"over_{phase}", overlay(phase), chr(0xE103 + phase), 11)
    picture("frame_cast", frame((236, 208, 150), None, plain=True), chr(0xE108), 11)
    for i, (name, (colour, label)) in enumerate(CARDS.items()):
        picture("card_" + name, card(colour, label), chr(0xE120 + i), 9)
    # icons: one sheet, a row of eight-pixel cells, on the same line as the block lettering
    icon_sheet = Image.new("RGBA", (8 * len(ICONS), 8), (0, 0, 0, 0))
    ip = icon_sheet.load()
    icon_row, icon_adv = "", []
    for i, (name, (colour, rows)) in enumerate(ICONS.items()):
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "#":
                    ip[i * 8 + rx, ry] = (mix(colour, (255, 255, 255), 0.35) if ry < 2 else colour if ry < 5 else shade(colour, 0.7)) + (255,)
        icon_row += chr(0xE140 + i)
        icon_adv.append(max(rx for row in rows for rx, bit in enumerate(row) if bit == "#") + 2)
    icon_sheet.save(os.path.join(out, "icons.png"))
    providers.append({"type": "bitmap", "file": f"{mod}:gui/hud/icons.png", "ascent": 7, "height": 8, "chars": [icon_row]})
    # the fill, in pieces 1, 2, 4 ... 128 wide: white, shaded top to bottom, coloured by the text's own colour
    for k in range(8):
        piece = Image.new("RGBA", (1 << k, 5))
        for y, f in enumerate((0.8, 1.0, 0.92, 0.74, 0.58)):
            for x in range(1 << k):
                piece.putpixel((x, y), shade((255, 255, 255), f) + (255,))
        piece.save(os.path.join(out, f"fill_{1 << k}.png"))
        providers.append({"type": "bitmap", "file": f"{mod}:gui/hud/fill_{1 << k}.png", "ascent": -2, "height": 5, "chars": [chr(0xE110 + k)]})
    # block lettering for names: the sixty-four characters from space to underscore, eight pixels to a cell
    sheet = Image.new("RGBA", (128, 32), (0, 0, 0, 0))
    sp = sheet.load()
    rows, letter = [], {}
    for r in range(4):
        row = ""
        for c in range(16):
            ch = chr(0x20 + r * 16 + c)
            if ch in GLYPHS:
                width = lettering(sp, c * 8, r * 8, ch, (255, 255, 255))
                letter[ch] = width + 1
                row += chr(0xE200 + ord(ch))
            else:
                row += "\u0000"
        rows.append(row)
    letter[" "] = 3
    sheet.save(os.path.join(out, "letters.png"))
    providers.append({"type": "bitmap", "file": f"{mod}:gui/hud/letters.png", "ascent": 7, "height": 8, "chars": rows})
    wj(os.path.join(assets, "font/hud.json"), {"providers": providers})

    # the game's own letters again, lower on the line: five fonts, so a card can hold one, two or three lines
    ascii_rows = ["\u0000" * 16, "\u0000" * 16] + ["".join("\u0000" if code in (0x20, 0x7F) else chr(code) for code in range(r * 16, r * 16 + 16)) for r in range(2, 8)] + ["\u0000" * 16] * 8
    for i in range(5):
        wj(os.path.join(assets, f"font/tip{i}.json"), {"providers": [
            {"type": "space", "advances": {" ": 4}},
            {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": -5 - 5 * i, "chars": ascii_rows}]})

    ascii_adv = [6] * 95
    for adv, chars in ASCII_ADVANCE.items():
        for ch in chars:
            ascii_adv[ord(ch) - 0x20] = adv
    letter_adv = [letter.get(chr(0x20 + i), 0) for i in range(64)]

    def jchar(ch):
        return "'\\u%04X'" % ord(ch)
    names = list(TOWERS)
    src = f"""package com.thefarawaydev.realmsofchaos.ui;

/** GENERATED by tools/hud_art.py - do not edit. The letters of the on-screen artwork, and how wide each one is. */
public final class HudGlyphs {{
    private HudGlyphs() {{}}

    public static final int FRAME_WIDTH = {FRAME_W}, BAR_X = {BAR_X}, BAR_WIDTH = {BAR_W}, CARD_WIDTH = {CARD_W};
    /** Frames by tower (Zeus, Poseidon, Hades), and what the game takes their advance to be. */
    public static final char[] FRAME = {{{", ".join(jchar(art["frame_" + n][0]) for n in names)}}};
    public static final int[] FRAME_ADVANCE = {{{", ".join(str(art["frame_" + n][1]) for n in names)}}};
    /** The frames of the later phases: [tower][0] while its aegis is up, [tower][1] in its wrath. */
    public static final char[][] PHASE_FRAME = {{{", ".join("{" + jchar(art["frame_" + n + "_aegis"][0]) + ", " + jchar(art["frame_" + n + "_wrath"][0]) + "}" for n in names)}}};
    public static final int[][] PHASE_FRAME_ADVANCE = {{{", ".join("{" + str(art["frame_" + n + "_aegis"][1]) + ", " + str(art["frame_" + n + "_wrath"][1]) + "}" for n in names)}}};
    /** Icons: {", ".join(ICONS)}. */
    public static final char ICON = '\\uE140';
    public static final int {", ".join("ICON_" + n.upper() + " = " + str(i) for i, n in enumerate(ICONS))};
    public static final int[] ICON_ADVANCE = {{{", ".join(map(str, icon_adv))}}};
    /** Overlays by phase, 1 to 3. */
    public static final char[] OVERLAY = {{{", ".join(jchar(art[f"over_{p}"][0]) for p in (1, 2, 3))}}};
    public static final int[] OVERLAY_ADVANCE = {{{", ".join(str(art[f"over_{p}"][1]) for p in (1, 2, 3))}}};
    public static final char CAST_FRAME = {jchar(art["frame_cast"][0])};
    public static final int CAST_FRAME_ADVANCE = {art["frame_cast"][1]};
    /** Cards: {", ".join(CARDS)}. */
    public static final char[] CARD = {{{", ".join(jchar(art["card_" + n][0]) for n in CARDS)}}};
    public static final int[] CARD_ADVANCE = {{{", ".join(str(art["card_" + n][1]) for n in CARDS)}}};
    /** A piece of fill 2^k wide is FILL + k; a space of 2^k is SPACE + k, and of -2^k is BACK + k. */
    public static final char FILL = '\\uE110', SPACE = '\\uF810', BACK = '\\uF820';
    /** Block lettering: the letter for an ASCII character from space to underscore is LETTER + its code. */
    public static final char LETTER = '\\uE200';
    public static final int[] LETTER_ADVANCE = {{{", ".join(map(str, letter_adv))}}};
    /** Advances of the game's own font, space to tilde. */
    public static final int[] ASCII_ADVANCE = {{{", ".join(map(str, ascii_adv))}}};
}}
"""
    w(os.path.join(java_dir, "ui", "HudGlyphs.java"), src)
    return art
