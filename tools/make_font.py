#!/usr/bin/env python3
"""Builds site/ChaosBlocks.ttf: the square block lettering the website uses for its titles.

An original typeface made for this project: capitals five blocks tall, drawn on a grid, every corner square.
Needs fontTools (pip install fonttools). Run it again after changing a letter below.
"""
import os
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

CELL = 125                 # one block; eight blocks to the em, so 16px text has 2px blocks
ROWS = 5                   # capitals are five blocks tall
ASCENT, DESCENT = 6 * CELL, 2 * CELL
SIDE = CELL // 2           # half a block of air on each side of a letter

GLYPHS = {
    "A": "####,#..#,####,#..#,#..#",
    "B": "###.,#.#.,####,#..#,####",
    "C": "####,#...,#...,#...,####",
    "D": "###.,#..#,#..#,#..#,###.",
    "E": "####,#...,###.,#...,####",
    "F": "####,#...,###.,#...,#...",
    "G": "####,#...,#.##,#..#,####",
    "H": "#..#,#..#,####,#..#,#..#",
    "I": "###,.#.,.#.,.#.,###",
    "J": "..##,...#,...#,#..#,####",
    "K": "#..#,#.#.,##..,#.#.,#..#",
    "L": "#...,#...,#...,#...,####",
    "M": "#...#,##.##,#.#.#,#...#,#...#",
    "N": "#..#,##.#,#.##,#..#,#..#",
    "O": "####,#..#,#..#,#..#,####",
    "P": "####,#..#,####,#...,#...",
    "Q": "####,#..#,#..#,#.#.,##.#",
    "R": "####,#..#,####,#.#.,#..#",
    "S": "####,#...,####,...#,####",
    "T": "#####,..#..,..#..,..#..,..#..",
    "U": "#..#,#..#,#..#,#..#,####",
    "V": "#...#,#...#,#...#,.#.#.,..#..",
    "W": "#...#,#...#,#.#.#,##.##,#...#",
    "X": "#...#,.#.#.,..#..,.#.#.,#...#",
    "Y": "#...#,.#.#.,..#..,..#..,..#..",
    "Z": "####,...#,.##.,#...,####",
    "0": ".##.,#..#,#..#,#..#,.##.",
    "1": ".#.,##.,.#.,.#.,###",
    "2": "####,...#,####,#...,####",
    "3": "####,...#,.###,...#,####",
    "4": "#..#,#..#,####,...#,...#",
    "5": "####,#...,###.,...#,###.",
    "6": "####,#...,####,#..#,####",
    "7": "####,...#,..#.,.#..,.#..",
    "8": "####,#..#,####,#..#,####",
    "9": "####,#..#,####,...#,####",
    ".": "#,#,#,#,#".replace("#", ".", 4),
    ",": "..,..,..,.#,#.",
    "!": "#,#,#,.,#",
    "?": "####,...#,.##.,....,.#..",
    "-": "...,...,###,...,...",
    "+": "...,.#.,###,.#.,...",
    ":": ".,#,.,#,.",
    "'": "#,#,.,.,.",
    "/": "..#,..#,.#.,#..,#..",
    "&": ".##..,#..#.,.##.#,#..#.,.##.#",
    "(": ".#,#.,#.,#.,.#",
    ")": "#.,.#,.#,.#,#.",
    "%": "#..#,...#,.##.,#...,#..#",
    "#": ".#.#.,#####,.#.#.,#####,.#.#.",
    "·": ".,.,#,.,.",
}
NAMES = {".": "period", ",": "comma", "!": "exclam", "?": "question", "-": "hyphen", "+": "plus", ":": "colon",
         "'": "quotesingle", "/": "slash", "&": "ampersand", "(": "parenleft", ")": "parenright", "%": "percent",
         "#": "numbersign", "·": "periodcentered",
         "0": "zero", "1": "one", "2": "two", "3": "three", "4": "four", "5": "five", "6": "six", "7": "seven",
         "8": "eight", "9": "nine"}


def outline(rows):
    """The edge of a letter's blocks as closed loops, so touching blocks join without a seam."""
    filled = {(x, ROWS - 1 - y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "#"}
    edges = {}                                  # start corner -> end corners, the ink kept on the right
    for (x, y) in filled:
        sides = [((x, y + 1), (x + 1, y + 1), (x, y + 1)), ((x + 1, y + 1), (x + 1, y), (x + 1, y)),
                 ((x + 1, y), (x, y), (x, y - 1)), ((x, y), (x, y + 1), (x - 1, y))]
        for a, b, beyond in sides:
            if beyond not in filled:
                edges.setdefault(a, []).append(b)
    loops = []
    while edges:
        start = next(iter(edges))
        loop, at = [start], start
        while True:
            ends = edges[at]
            nxt = ends.pop(0)
            if not ends:
                del edges[at]
            if nxt == start:
                break
            loop.append(nxt)
            at = nxt
        # drop the corners that lie on a straight run
        keep = [p for i, p in enumerate(loop)
                if (p[0] - loop[i - 1][0]) * (loop[(i + 1) % len(loop)][1] - p[1])
                != (p[1] - loop[i - 1][1]) * (loop[(i + 1) % len(loop)][0] - p[0])]
        loops.append(keep)
    return loops


def glyph(rows):
    pen = TTGlyphPen(None)
    for loop in outline(rows):
        pen.moveTo((SIDE + loop[0][0] * CELL, loop[0][1] * CELL))
        for (x, y) in loop[1:]:
            pen.lineTo((SIDE + x * CELL, y * CELL))
        pen.closePath()
    return pen.glyph()


def main():
    order, cmap, glyphs, metrics = [".notdef", "space"], {32: "space", 0xA0: "space"}, {}, {}
    empty = TTGlyphPen(None)
    glyphs[".notdef"] = glyph("####,#..#,#..#,#..#,####".split(","))
    metrics[".notdef"] = (5 * CELL, SIDE)
    glyphs["space"] = empty.glyph()
    metrics["space"] = (3 * CELL, 0)
    for ch, art in GLYPHS.items():
        rows = art.split(",")
        assert len(rows) == ROWS and len({len(r) for r in rows}) == 1, ch
        name = NAMES.get(ch, ch)
        order.append(name)
        cmap[ord(ch)] = name
        if ch.isalpha():
            cmap[ord(ch.lower())] = name        # capitals only: small letters use the same shapes
        glyphs[name] = glyph(rows)
        metrics[name] = ((len(rows[0]) + 1) * CELL, SIDE)

    fb = FontBuilder(8 * CELL, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=ASCENT, descent=-DESCENT)
    fb.setupNameTable({"familyName": "Chaos Blocks", "styleName": "Regular",
                       "copyright": "Chaos Blocks (c) TheFarawayDev. Made for Realms of Chaos; MIT licensed.",
                       "licenseDescription": "MIT License"})
    fb.setupOS2(sTypoAscender=ASCENT, sTypoDescender=-DESCENT, sTypoLineGap=0, usWinAscent=ASCENT, usWinDescent=DESCENT,
                sCapHeight=ROWS * CELL, sxHeight=ROWS * CELL, achVendID="ROC ")
    fb.setupPost()
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "site", "ChaosBlocks.ttf")
    fb.save(out)
    print("wrote", os.path.normpath(out), os.path.getsize(out), "bytes,", len(order), "glyphs")


if __name__ == "__main__":
    main()
