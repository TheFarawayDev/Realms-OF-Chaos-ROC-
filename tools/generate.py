#!/usr/bin/env python3
"""
Realms of Chaos - content generator (TheFarawayDev)

Single source of truth for the Greek roster. Emits:
  * common/src/main/java/.../god/GodRoster.java   (generated Java roster)
  * assets: lang, item defs, item models, blockstates, block models, textures
Run:  python3 tools/generate.py
"""
import hashlib, json, os, random
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MOD = "realms_of_chaos"
JAVA_PKG = "com.thefarawaydev.realmsofchaos"
RES = os.path.join(ROOT, "common/src/main/resources")
ASSETS = os.path.join(RES, f"assets/{MOD}")
JAVA_DIR = os.path.join(ROOT, "common/src/main/java", *JAVA_PKG.split("."), "god")

# ----------------------------------------------------------------------------
# Roster: (id, display name, title, weapon name, shape, power, target effect, self effect)
# effects are "effect_id:seconds:amplifier" or "" ; powers: NONE LIGHTNING FIRE KNOCKBACK DRAIN FREEZE SWEEP
# Ranks run 1 (strongest, the final boss of the line) .. 15 (weakest, the starter boss)
# ----------------------------------------------------------------------------
LINES = {
    "ZEUS": [
        ("zeus", "Zeus", "King of the Gods", "Keraunos, the Thunderbolt", "bolt", "LIGHTNING", "glowing:6:0", "resistance:6:0"),
        ("athena", "Athena", "Goddess of Wisdom and War", "Spear of the Grey-Eyed", "spear", "KNOCKBACK", "weakness:6:1", "resistance:6:1"),
        ("apollo", "Apollo", "God of the Sun and Prophecy", "Helios Lance", "spear", "FIRE", "glowing:8:0", "speed:6:0"),
        ("dionysus", "Dionysus", "God of Wine and Madness", "Thyrsus of Madness", "staff", "NONE", "nausea:8:0", "regeneration:5:0"),
        ("artemis", "Artemis", "Goddess of the Hunt and Moon", "Crescent Fang", "dagger", "NONE", "slowness:5:1", "speed:6:1"),
        ("ares", "Ares", "God of War", "Warblade of Ares", "sword", "KNOCKBACK", "", "strength:6:1"),
        ("hermes", "Hermes", "Messenger of the Gods", "Winged Caduceus", "dagger", "NONE", "slowness:3:0", "speed:8:2"),
        ("asclepius", "Asclepius", "God of Medicine", "Rod of Asclepius", "staff", "DRAIN", "", "regeneration:6:1"),
        ("hebe", "Hebe", "Goddess of Youth", "Ambrosia Sabre", "sword", "NONE", "weakness:5:0", "regeneration:6:0"),
        ("phobos", "Phobos", "God of Fear", "Terror Fang", "dagger", "NONE", "darkness:6:0", "speed:5:0"),
        ("deimos", "Deimos", "God of Dread", "Dread Reaper", "scythe", "NONE", "blindness:3:0", "speed:5:0"),
        ("hygeia", "Hygeia", "Goddess of Health", "Serpent Scalpel", "dagger", "DRAIN", "", "absorption:8:0"),
        ("pan", "Pan", "God of Wild Nature", "Wildwood Cudgel", "staff", "NONE", "poison:4:0", "speed:6:0"),
        ("nike", "Nike", "Goddess of Victory", "Victory Gladius", "sword", "NONE", "", "strength:6:0"),
        ("iris", "Iris", "Divine Messenger of the Rainbow", "Rainbow Rapier", "sword", "NONE", "glowing:10:0", "speed:6:0"),
    ],
    "POSEIDON": [
        ("poseidon", "Poseidon", "Lord of the Seas", "Trident of the Deep", "trident", "SWEEP", "slowness:5:1", "resistance:6:0"),
        ("amphitrite", "Amphitrite", "Queen of the Sea", "Queen's Coral Scepter", "staff", "FREEZE", "", "regeneration:6:0"),
        ("triton", "Triton", "Herald of the Sea", "Conch Spear", "spear", "KNOCKBACK", "", "water_breathing:20:0"),
        ("chrysaor", "Chrysaor", "Golden Blade of the Sea", "Golden Sword of Chrysaor", "sword", "SWEEP", "", "strength:6:0"),
        ("cymopoleia", "Cymopoleia", "Goddess of Storm Waves", "Stormwave Maul", "hammer", "KNOCKBACK", "slowness:4:1", "resistance:6:0"),
        ("rhode", "Rhode", "Nymph of Rhodes", "Roserain Blade", "sword", "NONE", "nausea:6:0", "speed:6:0"),
        ("pegasus", "Pegasus", "The Winged Horse", "Wingbeat Saber", "sword", "NONE", "levitation:2:0", "jump_boost:8:2"),
        ("benthesikyme", "Benthesikyme", "Goddess of the Deep Waves", "Deepcurrent Dirk", "dagger", "DRAIN", "", "water_breathing:20:0"),
        ("halia", "Halia", "Sea Nymph of the Salt", "Seafoam Cutlass", "sword", "FREEZE", "", "regeneration:5:0"),
        ("nereus", "Nereus", "Old Man of the Sea", "Elder's Tideblade", "sword", "NONE", "weakness:6:0", "resistance:6:0"),
        ("proteus", "Proteus", "The Shapeshifter", "Shapeshifter's Kris", "dagger", "NONE", "nausea:6:0", "speed:6:0"),
        ("palaemon", "Palaemon", "God of Harbours", "Anchor Pike", "spear", "NONE", "slowness:4:0", "jump_boost:6:1"),
        ("thetis", "Thetis", "Silver-Footed Sea Nymph", "Silverfoot Rapier", "sword", "FREEZE", "", "absorption:8:0"),
        ("galene", "Galene", "Goddess of Calm Seas", "Calmwater Foil", "sword", "NONE", "slowness:3:0", "regeneration:5:0"),
        ("galatea", "Galatea", "Sea Nymph of the Pearl Shore", "Pearl Sea Dagger", "dagger", "FREEZE", "", "speed:5:0"),
    ],
    "HADES": [
        ("hades", "Hades", "Lord of the Underworld", "Bident of the Dead", "trident", "DRAIN", "wither:5:1", "resistance:6:0"),
        ("persephone", "Persephone", "Queen of the Underworld", "Pomegranate Scythe", "scythe", "NONE", "poison:5:0", "regeneration:6:0"),
        ("zagreus", "Zagreus", "The Hunter of the Dead", "Stygian Blade", "sword", "FIRE", "", "strength:6:0"),
        ("thanatos", "Thanatos", "Death Incarnate", "Reaper's Sickle", "scythe", "DRAIN", "wither:4:2", ""),
        ("hypnos", "Hypnos", "God of Sleep", "Slumber Staff", "staff", "NONE", "slowness:5:2", ""),
        ("melinoe", "Melinoe", "Goddess of Ghosts", "Witchlight Knife", "dagger", "NONE", "darkness:6:0", "speed:6:0"),
        ("macaria", "Macaria", "Goddess of Blessed Death", "Mercy's Edge", "dagger", "DRAIN", "", "regeneration:5:0"),
        ("hecate", "Hecate", "Goddess of Witchcraft", "Hecate's Torchblade", "sword", "FIRE", "blindness:3:0", "speed:6:0"),
        ("morpheus", "Morpheus", "God of Dreams", "Dreamshaper Blade", "sword", "NONE", "nausea:6:0", "speed:6:0"),
        ("nyx", "Nyx", "Primordial Goddess of Night", "Midnight Shard", "dagger", "NONE", "darkness:10:0", "resistance:6:0"),
        ("erinyes", "The Erinyes", "The Furies of Vengeance", "Furies' Lash Blade", "sword", "FIRE", "weakness:6:0", ""),
        ("moirai", "The Moirai", "The Three Fates", "Shears of Fate", "dagger", "NONE", "mining_fatigue:8:1", ""),
        ("keres", "The Keres", "Spirits of Violent Death", "Carrion Talon", "scythe", "DRAIN", "poison:4:0", ""),
        ("oneiroi", "The Oneiroi", "Spirits of Dreams", "Nightmare Reed", "staff", "NONE", "nausea:7:0", "speed:5:0"),
        ("charon", "Charon", "Ferryman of the Dead", "Ferryman's Oar", "staff", "KNOCKBACK", "slowness:4:0", "resistance:6:0"),
    ],
}

LINE_INFO = {
    "ZEUS":     dict(label="Zeus", zone="Olympian Heights", starter="Iris", final="Zeus",
                     palette=((250, 215, 80), (245, 245, 255), (110, 170, 255)), color=0xF2C94C, bar="YELLOW"),
    "POSEIDON": dict(label="Poseidon", zone="The Abyssal Palace", starter="Galatea", final="Poseidon",
                     palette=((60, 190, 200), (30, 90, 170), (210, 245, 250)), color=0x2FB5C8, bar="BLUE"),
    "HADES":    dict(label="Hades", zone="The Underworld", starter="Charon", final="Hades",
                     palette=((150, 70, 200), (45, 35, 70), (235, 120, 60)), color=0x8E44AD, bar="PURPLE"),
}

def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)

def wj(path, obj):
    w(path, json.dumps(obj, indent=2, ensure_ascii=False) + "\n")

def jstr(s):
    return json.dumps(s, ensure_ascii=False)

def hsh(s):
    return int(hashlib.md5(s.encode()).hexdigest(), 16)

# ----------------------------------------------------------------------------
# Java roster
# ----------------------------------------------------------------------------
def gen_java():
    rows = []
    idx = 0
    for line, gods in LINES.items():
        for i, g in enumerate(gods):
            gid, name, title, wname, shape, power, teff, seff = g
            rows.append(
                f"        new God({idx}, Line.{line}, {i + 1}, {jstr(gid)}, {jstr(name)}, {jstr(title)}, "
                f"{jstr(wname)}, Power.{power}, {jstr(teff)}, {jstr(seff)})"
            )
            idx += 1
    src = f"""package {JAVA_PKG}.god;

import java.util.List;

/** GENERATED by tools/generate.py - do not edit by hand. */
final class GodRoster {{
    private GodRoster() {{}}

    static final List<God> ALL = List.of(
{(",\n").join(rows)}
    );
}}
"""
    w(os.path.join(JAVA_DIR, "GodRoster.java"), src)

# ----------------------------------------------------------------------------
# Lang
# ----------------------------------------------------------------------------
def gen_lang():
    lang = {
        "itemGroup.realms_of_chaos.main": "Realms of Chaos",
        "item.realms_of_chaos.chaos_compass": "Compass of Chaos",
        "item.realms_of_chaos.chaos_compass.tooltip": "Right-click to travel to the Hub of Realms. By TheFarawayDev.",
        "block.realms_of_chaos.gate_hub": "Gate to the Hub",
        "block.realms_of_chaos.gate_zeus": "Gate of Zeus",
        "block.realms_of_chaos.gate_poseidon": "Gate of Poseidon",
        "block.realms_of_chaos.gate_hades": "Gate of Hades",
        "block.realms_of_chaos.altar_zeus": "Trial Altar of Zeus",
        "block.realms_of_chaos.altar_poseidon": "Trial Altar of Poseidon",
        "block.realms_of_chaos.altar_hades": "Trial Altar of Hades",
        "entity.realms_of_chaos.god_boss": "God of the Realms",
        "message.realms_of_chaos.hub_welcome": "Welcome to the Hub of Realms. Three gates await: Zeus (north), Poseidon (east) and Hades (west).",
        "message.realms_of_chaos.enter_zone": "You enter %s.",
        "message.realms_of_chaos.altar.summon": "%s rises to test you! (Rank %s of 15)",
        "message.realms_of_chaos.altar.active": "A god already stands against you. Defeat them first.",
        "message.realms_of_chaos.altar.done": "You have conquered the whole line of %s. Their relics are yours.",
        "message.realms_of_chaos.altar.next": "Your next trial: %s (Rank %s of 15).",
        "message.realms_of_chaos.victory": "%s has been vanquished! Their relic is yours.",
        "message.realms_of_chaos.unlocked": "Next trial unlocked at the altar: %s",
        "message.realms_of_chaos.line_complete": "You have conquered the line of %s!",
        "message.realms_of_chaos.progress.header": "Realms of Chaos progress",
        "message.realms_of_chaos.progress.line": "%s: %s / 15 gods defeated (next: %s)",
        "message.realms_of_chaos.progress.complete": "complete",
        "tooltip.realms_of_chaos.rank": "Rank %s of 15",
        "tooltip.realms_of_chaos.line": "Line of %s",
        "tooltip.realms_of_chaos.ability": "Right-click: Divine Surge (%s)",
        "power.realms_of_chaos.none": "empowering strike",
        "power.realms_of_chaos.lightning": "calls lightning",
        "power.realms_of_chaos.fire": "sets foes ablaze",
        "power.realms_of_chaos.knockback": "hurls foes back",
        "power.realms_of_chaos.drain": "drains life",
        "power.realms_of_chaos.freeze": "freezes foes",
        "power.realms_of_chaos.sweep": "sweeping tidal strike",
        "boss.realms_of_chaos.title": "%s, %s",
        "mod.realms_of_chaos.credit": "Realms of Chaos by TheFarawayDev",
    }
    for line, gods in LINES.items():
        info = LINE_INFO[line]
        lang[f"line.realms_of_chaos.{line.lower()}"] = info["label"]
        lang[f"zone.realms_of_chaos.{line.lower()}"] = info["zone"]
        for gid, name, title, wname, *_ in gods:
            lang[f"god.realms_of_chaos.{gid}"] = name
            lang[f"god.realms_of_chaos.{gid}.title"] = title
            lang[f"item.realms_of_chaos.{gid}_weapon"] = wname
    wj(os.path.join(ASSETS, "lang/en_us.json"), lang)

# ----------------------------------------------------------------------------
# Item definitions / models
# ----------------------------------------------------------------------------
def item_def(item_id, model_path):
    wj(os.path.join(ASSETS, f"items/{item_id}.json"),
       {"model": {"type": "minecraft:model", "model": f"{MOD}:{model_path}"}})

def gen_models():
    for line, gods in LINES.items():
        for gid, *_ in gods:
            iid = f"{gid}_weapon"
            item_def(iid, f"item/{iid}")
            wj(os.path.join(ASSETS, f"models/item/{iid}.json"),
               {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{MOD}:item/{iid}"}})
    # compass
    item_def("chaos_compass", "item/chaos_compass")
    wj(os.path.join(ASSETS, "models/item/chaos_compass.json"),
       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/chaos_compass"}})
    blocks = ["gate_hub", "gate_zeus", "gate_poseidon", "gate_hades",
              "altar_zeus", "altar_poseidon", "altar_hades"]
    for b in blocks:
        wj(os.path.join(ASSETS, f"blockstates/{b}.json"),
           {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        if b.startswith("altar"):
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               {"parent": "minecraft:block/cube_bottom_top",
                "textures": {"top": f"{MOD}:block/{b}_top", "bottom": f"{MOD}:block/altar_base",
                             "side": f"{MOD}:block/{b}_side"}})
        else:
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}})
        item_def(b, f"block/{b}")

# ----------------------------------------------------------------------------
# Textures
# ----------------------------------------------------------------------------
def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

def draw_weapon(gid, shape, line, power):
    pal = LINE_INFO[line]["palette"]
    rnd = random.Random(hsh(gid))
    metal = mix(pal[0], pal[2], rnd.uniform(0.0, 0.7))
    accent = mix(pal[1], pal[0], rnd.uniform(0.2, 0.8))
    hilt = (92, 62, 36)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c + (255,)

    def diag(x0, y0, n, c, c2=None):
        for i in range(n):
            put(x0 + i, y0 - i, c)
            if c2 is not None:
                put(x0 + i + 1, y0 - i, c2)

    if shape in ("sword",):
        diag(4, 11, 9, metal, shade(metal, 0.75))
        put(13, 2, shade(metal, 1.25)); put(14, 1, shade(metal, 1.25))
        diag(2, 13, 3, hilt)
        for i in range(-1, 2):
            put(5 + i, 10 + (1 - abs(i)) * 0 - i, accent)  # crossguard
    elif shape == "dagger":
        diag(6, 9, 6, metal, shade(metal, 0.75))
        put(12, 3, shade(metal, 1.3))
        diag(3, 12, 3, hilt)
        put(5, 10, accent); put(6, 11, accent)
    elif shape == "spear":
        diag(2, 13, 10, hilt)
        diag(10, 5, 3, metal, shade(metal, 0.8))
        put(13, 2, shade(metal, 1.3)); put(14, 1, shade(metal, 1.3))
        put(10, 4, accent); put(11, 5, accent)
    elif shape == "trident":
        diag(2, 13, 9, hilt)
        for (x, y) in [(10, 5), (11, 4), (12, 3), (11, 2), (13, 4), (13, 5), (12, 6), (14, 3), (14, 1), (12, 1), (9, 3), (10, 2)]:
            put(x, y, metal)
        put(11, 3, accent); put(12, 4, accent)
    elif shape == "staff":
        diag(2, 14, 10, hilt, shade(hilt, 0.8))
        for (x, y) in [(11, 4), (12, 3), (12, 4), (13, 3), (11, 3), (12, 2)]:
            put(x, y, accent)
        put(12, 3, shade(metal, 1.3)); put(13, 2, metal)
    elif shape == "scythe":
        diag(3, 14, 10, hilt)
        for (x, y) in [(10, 3), (11, 2), (12, 2), (13, 3), (14, 4), (9, 3), (14, 5)]:
            put(x, y, metal)
        put(13, 4, shade(metal, 0.7)); put(12, 3, shade(metal, 1.25))
    elif shape == "hammer":
        diag(2, 14, 9, hilt)
        for x in range(9, 14):
            for y in range(2, 6):
                put(x, y, metal if (x + y) % 2 else shade(metal, 0.85))
        put(9, 2, accent); put(13, 5, accent)
    elif shape == "bolt":
        pts = [(9, 1), (8, 2), (8, 3), (9, 3), (7, 5), (8, 5), (6, 7), (7, 7), (5, 9), (6, 9), (5, 10), (4, 12), (5, 12), (3, 14)]
        for (x, y) in pts:
            put(x, y, metal); put(x + 1, y, shade(metal, 0.8))
        for (x, y) in [(8, 3), (7, 5), (6, 7), (5, 9)]:
            put(x, y, (255, 255, 255))
    else:
        diag(4, 11, 8, metal)
    # a glint of the god's power colour
    glow = {"LIGHTNING": (255, 255, 160), "FIRE": (255, 150, 40), "FREEZE": (170, 235, 255),
            "DRAIN": (210, 60, 90), "SWEEP": (90, 190, 255), "KNOCKBACK": (230, 230, 230)}.get(power)
    if glow:
        put(14, 1, glow); put(13, 1, shade(glow, 0.8))
    path = os.path.join(ASSETS, f"textures/item/{gid}_weapon.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def draw_compass():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 7.2:
                px[x, y] = (80, 60, 110, 255) if d > 6 else (30, 20, 50, 255)
            if 6.0 <= d < 7.2:
                px[x, y] = (230, 190, 70, 255)
    for i in range(3, 13):
        px[8, i] = (255, 255, 255, 255) if i < 8 else (230, 90, 90, 255)
        px[7, i] = (255, 255, 255, 255) if i < 8 else (230, 90, 90, 255)
    for i in range(4, 12):
        px[i, 8] = (150, 200, 255, 255)
    img.save(os.path.join(ASSETS, "textures/item/chaos_compass.png"))

def noise_tex(path, base, var, seed, size=16, pattern=None):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            f = 1 + rnd.uniform(-var, var)
            c = shade(base, f)
            if pattern:
                c = pattern(x, y, c)
            px[x, y] = c + (255,)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def gen_block_textures():
    T = os.path.join(ASSETS, "textures/block")
    gate_cols = {"gate_hub": (170, 120, 255), "gate_zeus": (255, 220, 90),
                 "gate_poseidon": (60, 200, 230), "gate_hades": (160, 60, 220)}
    for name, col in gate_cols.items():
        def pat(x, y, c, col=col):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            band = (int(d * 1.6) + (x ^ y) % 2) % 3
            if x in (0, 15) or y in (0, 15):
                return shade(col, 0.45)
            return mix(c, (255, 255, 255), 0.35) if band == 0 else shade(c, 0.85 if band == 1 else 1.1)
        noise_tex(os.path.join(T, f"{name}.png"), col, 0.07, hsh(name), pattern=pat)
    altar = {"altar_zeus": ((235, 235, 245), (250, 215, 80)),
             "altar_poseidon": ((70, 150, 150), (60, 200, 230)),
             "altar_hades": ((40, 36, 52), (160, 60, 220))}
    noise_tex(os.path.join(T, "altar_base.png"), (60, 60, 66), 0.1, 7)
    for name, (body, glow) in altar.items():
        def side(x, y, c, glow=glow):
            if y in (0, 15):
                return glow
            if x in (0, 15):
                return shade(c, 0.8)
            if 5 <= x <= 10 and 4 <= y <= 11 and (x + y) % 2 == 0:
                return glow
            return c
        noise_tex(os.path.join(T, f"{name}_side.png"), body, 0.06, hsh(name + "s"), pattern=side)
        def top(x, y, c, glow=glow):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if 5 <= d < 6.5:
                return glow
            if d < 2.5:
                return mix(glow, (255, 255, 255), 0.4)
            return c
        noise_tex(os.path.join(T, f"{name}_top.png"), body, 0.05, hsh(name + "t"), pattern=top)

def gen_skin(gid, line, rank):
    pal = LINE_INFO[line]["palette"]
    rnd = random.Random(hsh("skin" + gid))
    skin_tones = [(240, 205, 170), (222, 180, 140), (190, 140, 105), (225, 225, 240), (160, 190, 200), (200, 175, 215)]
    skin = skin_tones[hsh(gid) % len(skin_tones)]
    if line == "HADES":
        skin = mix(skin, (120, 110, 150), 0.45)
    if line == "POSEIDON":
        skin = mix(skin, (130, 200, 210), 0.35)
    robe = mix(pal[0], pal[1], rnd.uniform(0.0, 0.8))
    trim = mix(pal[2], pal[0], rnd.uniform(0.0, 0.6))
    hair = shade(mix(pal[1], pal[2], rnd.uniform(0.0, 1.0)), rnd.uniform(0.5, 1.1))
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def rect(x0, y0, x1, y1, c, jitter=0.04):
        for y in range(y0, y1):
            for x in range(x0, x1):
                f = 1 + rnd.uniform(-jitter, jitter)
                px[x, y] = shade(c, f) + (255,)

    # head (skin) 0..32 x 0..16
    rect(0, 0, 32, 16, skin)
    rect(8, 0, 24, 8, hair)                        # top/bottom -> hair
    rect(8, 8, 16, 11, hair)                       # fringe on front face
    rect(0, 8, 32, 10, hair)                       # hair band around sides
    rect(24, 8, 32, 16, hair)                      # back of head
    # eyes (front face is x 8..16, y 8..16)
    eye = (255, 255, 255) if line != "HADES" else (255, 120, 60)
    pupil = shade(pal[1], 0.5) if line != "HADES" else (80, 0, 0)
    px[10, 12] = eye + (255,); px[13, 12] = eye + (255,)
    px[9, 12] = pupil + (255,); px[14, 12] = pupil + (255,)
    for x in range(10, 14):
        px[x, 14] = shade(skin, 0.75) + (255,)
    # body, arms, legs (robes)
    rect(16, 16, 40, 32, robe)
    rect(20, 20, 28, 32, robe)
    rect(20, 20, 28, 22, trim)                     # collar
    rect(20, 28, 28, 30, trim)                     # belt
    rect(40, 16, 56, 32, robe)                     # right arm
    rect(44, 20, 48, 24, skin)                     # sleeve ends
    rect(40, 28, 56, 32, skin)                     # hands
    rect(32, 48, 48, 64, robe)                     # left arm
    rect(32, 60, 48, 64, skin)
    rect(0, 16, 16, 32, shade(robe, 0.8))          # right leg
    rect(0, 28, 16, 32, trim)                      # sandal straps / boots
    rect(16, 48, 32, 64, shade(robe, 0.8))         # left leg
    rect(16, 60, 32, 64, trim)
    # chest emblem: more elaborate for stronger gods
    emb = trim
    px[23, 24] = emb + (255,); px[24, 24] = emb + (255,)
    px[23, 25] = emb + (255,); px[24, 25] = emb + (255,)
    if rank <= 8:
        for x in range(21, 27):
            px[x, 26] = emb + (255,)
    if rank <= 3:
        # crown / divine halo on head top and fringe row
        for x in range(8, 16):
            px[x, 8] = trim + (255,)
        for x in range(8, 24):
            px[x, 0] = trim + (255,)
    path = os.path.join(ASSETS, f"textures/entity/god/{gid}.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

def gen_pack():
    wj(os.path.join(ROOT, "common/src/main/resources/pack.mcmeta"), {
        "pack": {
            "description": "Realms of Chaos resources by TheFarawayDev",
            "min_format": 97,
            "max_format": 121,
        }
    })


# ----------------------------------------------------------------------------
# Datapack: four void dimensions + their dimension types
# ----------------------------------------------------------------------------
REALMS = {
    # id: (skybox, sky color, fog color, sky light color, sky light factor, ambient light)
    "hub":        ("overworld", "#78a7ff", "#c0d8ff", "#ffffff", 1.0, 0.10),
    "olympus":    ("overworld", "#a8d4ff", "#eaf5ff", "#fff6d8", 1.0, 0.15),
    "abyss":      ("none",      "#06223d", "#0b3d5c", "#4aa8c8", 0.45, 0.20),
    "underworld": ("none",      "#1a0810", "#2a0f18", "#c04030", 0.05, 0.12),
}

def gen_data():
    base = os.path.join(RES, f"data/{MOD}")
    for rid, (sky, skyc, fog, slc, slf, amb) in REALMS.items():
        wj(os.path.join(base, f"dimension_type/{rid}.json"), {
            "ambient_light": amb,
            "attributes": {
                "minecraft:audio/background_music": {
                    "default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.game"}
                },
                "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
                "minecraft:gameplay/respawn_anchor_works": False,
                "minecraft:gameplay/straw_bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
                "minecraft:visual/fog_color": fog,
                "minecraft:visual/sky_color": skyc,
                "minecraft:visual/sky_light_color": slc,
                "minecraft:visual/sky_light_factor": slf,
            },
            "coordinate_scale": 1.0,
            "default_clock": "minecraft:the_end",
            "has_ceiling": False,
            "has_ender_dragon_fight": False,
            "has_fixed_time": True,
            "has_skylight": True,
            "height": 256,
            "infiniburn": "#minecraft:infiniburn_end",
            "logical_height": 256,
            "min_y": 0,
            "monster_spawn_block_light_limit": 0,
            "monster_spawn_light_level": 0,
            "skybox": sky,
        })
        wj(os.path.join(base, f"dimension/{rid}.json"), {
            "type": f"{MOD}:{rid}",
            "generator": {
                "type": "minecraft:flat",
                "settings": {
                    "biome": "minecraft:the_void",
                    "features": False,
                    "lakes": False,
                    "layers": [],
                    "structure_overrides": [],
                },
            },
        })

def gen_icon():
    img = Image.new("RGBA", (128, 128), (14, 10, 28, 255))
    px = img.load()
    rnd = random.Random(42)
    cols = [(250, 215, 80), (60, 200, 230), (160, 60, 220)]
    for y in range(128):
        for x in range(128):
            d = ((x - 64) ** 2 + (y - 64) ** 2) ** 0.5
            if d < 58:
                t = (x + y) / 256
                c = cols[0] if t < 0.33 else cols[1] if t < 0.66 else cols[2]
                f = 0.35 + 0.25 * (1 - d / 58) + rnd.uniform(-0.04, 0.04)
                px[x, y] = tuple(int(v * f) for v in c) + (255,)
            if 54 <= d < 58:
                px[x, y] = (235, 200, 90, 255)
    # three gate arches
    for i, c in enumerate(cols):
        ox = 24 + i * 32
        for y in range(44, 96):
            for x in range(ox, ox + 24):
                inner = ox + 5 <= x < ox + 19 and y > 54
                arch = (x - (ox + 12)) ** 2 + (y - 60) ** 2 < 49 and ox + 5 <= x < ox + 19
                if not inner and not arch:
                    px[x, y] = tuple(min(255, v + 25) for v in c) + (255,)
                else:
                    px[x, y] = tuple(int(v * 0.55) for v in c) + (255,)
    path = os.path.join(ASSETS, "icon.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

# ----------------------------------------------------------------------------
if __name__ == "__main__":
    gen_java()
    gen_lang()
    gen_models()
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    for line, gods in LINES.items():
        for rank, g in enumerate(gods, start=1):
            gid, name, title, wname, shape, power, *_ = g
            draw_weapon(gid, shape, line, power)
            gen_skin(gid, line, rank)
    draw_compass()
    gen_block_textures()
    gen_pack()
    gen_data()
    gen_icon()
    print("generated", sum(len(v) for v in LINES.values()), "gods")
