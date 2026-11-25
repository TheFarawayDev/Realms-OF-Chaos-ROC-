# Draws art/renders/texture-sheet.png: the glass (single and as a 3x3 window), every door and trapdoor, and every block texture.
import os, re
from PIL import Image, ImageDraw, ImageFont
ROOT = "/home/user/Realms-OF-Chaos-ROC-/common/src/main/resources/assets/realms_of_chaos/textures"
TB, TI = ROOT + "/block", ROOT + "/item"
F = lambda s, b=False: ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans" + ("-Bold" if b else "") + ".ttf", s)
W, COLS, CELL_W, CELL_H, TILE = 1600, 16, 100, 96, 64
BG, INK, DIM = (30, 31, 36), (235, 235, 240), (160, 162, 170)

def checker(w, h, s=8):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(0, h, s):
        for x in range(0, w, s):
            d.rectangle([x, y, x + s - 1, y + s - 1], fill=(120, 124, 132) if (x // s + y // s) % 2 else (150, 154, 162))
    return im

def load(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    if h > w: im = im.crop((0, 0, w, w))          # (an animated strip: its first frame)
    return im

def tile(im, size=TILE):
    t = checker(size, size)
    t.alpha_composite(im.resize((size, size), Image.NEAREST))
    return t

def label(d, x, y, text, width, font):
    while font.getlength(text) > width - 4 and len(text) > 4: text = text[:-2] + "…" if not text.endswith("…") else text[:-3] + "…"
    d.text((x + (width - font.getlength(text)) / 2, y), text, font=font, fill=DIM)

def nice(n):
    return n.replace("_", " ")

sections = []

# ---- glass
glass = ["reinforced_glass", "frosted_glass", "smart_glass", "smart_glass_clear", "veil_glass", "veil_glass_clear", "one_way_glass", "one_way_glass_back"]
titles = {"smart_glass": "smart (shaded)", "smart_glass_clear": "smart (clear)", "veil_glass": "veil (solid)", "veil_glass_clear": "veil (clear)",
          "one_way_glass": "one-way front", "one_way_glass_back": "one-way back"}
# a 3x3 window: the mask of each place (1 up, 2 down, 4 left, 8 right joined)
win = [[10, 14, 6], [11, 15, 7], [9, 13, 5]]
glass_cells = []
for g in glass:
    im = Image.new("RGBA", (144, 144))
    for r in range(3):
        for c in range(3):
            im.alpha_composite(load(f"{TB}/{g}_{win[r][c]}.png").resize((48, 48), Image.NEAREST), (c * 48, r * 48))
    glass_cells.append((titles.get(g, g.replace("_glass", "")), tile(load(f"{TB}/{g}_0.png"), 72), im))

# ---- doors
doors = ["bronze", "sky", "sea", "shade", "garrison", "verdigris", "ivory"]

# ---- blocks: everything else, one tile per texture, connecting variants shown once (as _0)
names = sorted(f[:-4] for f in os.listdir(TB) if f.endswith(".png"))
skip = re.compile(r"_([1-9]|1[0-5])$")
blocks = []
for n in names:
    if "glass" in n or "door" in n: continue
    if skip.search(n) and re.sub(skip, "_0", n) in names: continue
    blocks.append(n)

# layout heights
H_TITLE, PAD = 56, 24
glass_h = 40 + 2 * 200
door_h = 40 + 190
rows = (len(blocks) + COLS - 1) // COLS
block_h = 40 + rows * CELL_H
H = H_TITLE + glass_h + PAD + door_h + PAD + block_h + PAD
sheet = Image.new("RGBA", (W, H), BG)
d = ImageDraw.Draw(sheet)
d.text((24, 14), "Realms of Chaos - glass, doors and block textures (16x16, shown at 4x)", font=F(24, True), fill=INK)
y = H_TITLE

# glass section
d.text((24, y), "Glass  -  each as one block, and as a 3x3 window (the frame joins round the whole run)", font=F(18, True), fill=INK)
y += 40
for i, (name, single, window) in enumerate(glass_cells):
    col, row = i % 4, i // 4
    x0, y0 = 24 + col * 392, y + row * 200
    sheet.alpha_composite(single, (x0, y0 + 36))
    bg = checker(144, 144)
    bg.alpha_composite(window)
    sheet.alpha_composite(bg, (x0 + 88, y0))
    d.text((x0, y0 + 150), name, font=F(14, True), fill=INK)
y += 2 * 200 + PAD

# doors section
d.text((24, y), "Doors and trapdoors  -  upper leaf, lower leaf, item, trapdoor", font=F(18, True), fill=INK)
y += 40
for i, k in enumerate(doors):
    x0 = 24 + i * 222
    top, bot = load(f"{TB}/{k}_door_top.png").resize((64, 64), Image.NEAREST), load(f"{TB}/{k}_door_bottom.png").resize((64, 64), Image.NEAREST)
    leaf = checker(64, 128)
    leaf.alpha_composite(top, (0, 0))
    leaf.alpha_composite(bot, (0, 64))
    sheet.alpha_composite(leaf, (x0, y))
    if os.path.exists(f"{TI}/{k}_door.png"): sheet.alpha_composite(tile(load(f"{TI}/{k}_door.png"), 64), (x0 + 72, y))
    sheet.alpha_composite(tile(load(f"{TB}/{k}_trapdoor.png"), 64), (x0 + 72, y + 64))
    d.text((x0, y + 136), k.capitalize(), font=F(14, True), fill=INK)
y += 190 + PAD

# blocks section
d.text((24, y), f"Block textures  -  {len(blocks)} (connecting blocks shown once, unjoined)", font=F(18, True), fill=INK)
y += 40
small = F(10)
for i, n in enumerate(blocks):
    col, row = i % COLS, i // COLS
    x0, y0 = 24 + col * (W - 48) // COLS, y + row * CELL_H
    sheet.alpha_composite(tile(load(f"{TB}/{n}.png")), (x0 + (CELL_W - 24 - TILE) // 2, y0))
    label(d, x0 - 6, y0 + TILE + 4, nice(re.sub(r"_0$", "", n)), CELL_W - 12 + 12, small)

out = "/home/user/Realms-OF-Chaos-ROC-/art/renders/texture-sheet.png"
sheet.convert("RGB").save(out, optimize=True)
print(out, sheet.size, len(blocks))
