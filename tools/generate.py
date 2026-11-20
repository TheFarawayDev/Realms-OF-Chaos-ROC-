#!/usr/bin/env python3
"""
Realms of Chaos - content generator (TheFarawayDev)

Single source of truth for the Greek roster. Emits:
  * common/src/main/java/.../god/GodRoster.java   (generated Java roster)
  * assets: lang, item defs, item models, blockstates, block models, animated textures, boss skins
  * data: dimensions, dimension types, advancements
  * site: roster.json + icon atlases for the download page
Run:  python3 tools/generate.py
"""
import hashlib, json, math, os, random
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MOD = "realms_of_chaos"
JAVA_PKG = "com.thefarawaydev.realmsofchaos"
RES = os.path.join(ROOT, "common/src/main/resources")
ASSETS = os.path.join(RES, f"assets/{MOD}")
DATA = os.path.join(RES, f"data/{MOD}")
SITE = os.path.join(ROOT, "site")
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
    "ZEUS":     dict(label="Zeus", zone="Olympian Heights", metals=[(236, 196, 84), (206, 212, 224), (196, 142, 84)]),
    "POSEIDON": dict(label="Poseidon", zone="The Abyssal Palace", metals=[(86, 196, 206), (200, 214, 226), (232, 150, 120)]),
    "HADES":    dict(label="Hades", zone="The Underworld", metals=[(110, 100, 128), (170, 110, 214), (214, 204, 178)]),
}

# Tower tiers: floors 1-5, 6-10, 11-14 and the summit (15)
TIERS = {
    "ZEUS": ("Tower of Zeus", ["Foothills of Olympus", "The Marble Halls", "The Cloud Sanctum", "Summit of Olympus"]),
    "POSEIDON": ("Tower of Poseidon", ["The Sunlit Shallows", "The Prismarine Halls", "The Trench", "Throne of the Deep"]),
    "HADES": ("Tower of Hades", ["Fields of Asphodel", "Tartarus", "Halls of the Dead", "Throne of the Underworld"]),
}

# ---- colours used by the skins -------------------------------------------------------------
LIGHT, TAN, DARK = (236, 198, 162), (206, 158, 118), (152, 106, 76)
PALE, SEA, GHOST, WHITE_SKIN = (216, 208, 224), (170, 214, 212), (190, 200, 216), (244, 244, 250)
GOLD, SILVER, BRONZE, IRON = (236, 196, 84), (206, 212, 224), (184, 138, 82), (92, 94, 106)
WHITE, CREAM, BONE = (240, 240, 245), (238, 228, 204), (212, 204, 176)

# id: (outfit, cloth, trim, hair, skin, headgear, headgear colour, beard, long hair)
# outfits: toga dress robe armor wild ; headgear: none crown laurel helmet hood wings horns circlet flowers rainbow
LOOKS = {
    # --- Zeus line
    "zeus":      ("toga", WHITE, GOLD, (236, 236, 242), LIGHT, "crown", GOLD, True, False),
    "athena":    ("armor", BRONZE, GOLD, (110, 78, 50), LIGHT, "helmet", (70, 110, 200), False, True),
    "apollo":    ("toga", CREAM, GOLD, (242, 212, 110), LIGHT, "laurel", GOLD, False, False),
    "dionysus":  ("toga", (124, 62, 152), (90, 170, 80), (70, 46, 34), TAN, "laurel", (90, 170, 80), True, False),
    "artemis":   ("toga", (92, 142, 112), SILVER, (150, 84, 52), LIGHT, "circlet", SILVER, False, True),
    "ares":      ("armor", IRON, (196, 44, 44), (30, 26, 26), TAN, "helmet", (196, 44, 44), False, False),
    "hermes":    ("toga", (150, 192, 232), GOLD, (120, 84, 54), LIGHT, "wings", WHITE, False, False),
    "asclepius": ("toga", WHITE, (70, 160, 92), (170, 170, 176), LIGHT, "none", GOLD, True, False),
    "hebe":      ("dress", (242, 172, 192), GOLD, (242, 212, 110), LIGHT, "flowers", (250, 130, 170), False, True),
    "phobos":    ("armor", (52, 46, 62), (196, 44, 44), (24, 22, 26), PALE, "helmet", (110, 50, 150), False, False),
    "deimos":    ("robe", (62, 62, 78), (170, 30, 50), (230, 230, 236), PALE, "hood", (62, 62, 78), False, False),
    "hygeia":    ("dress", WHITE, (70, 160, 92), (120, 84, 54), LIGHT, "circlet", GOLD, False, True),
    "pan":       ("wild", (112, 78, 48), (90, 150, 70), (96, 64, 40), TAN, "horns", BONE, True, False),
    "nike":      ("armor", GOLD, WHITE, (242, 212, 110), LIGHT, "laurel", GOLD, False, True),
    "iris":      ("dress", WHITE, (250, 130, 170), (246, 206, 96), LIGHT, "rainbow", WHITE, False, True),
    # --- Poseidon line
    "poseidon":     ("toga", (42, 112, 172), GOLD, (204, 236, 230), TAN, "crown", (240, 170, 110), True, False),
    "amphitrite":   ("dress", (62, 172, 172), (240, 240, 230), (30, 92, 104), SEA, "crown", (240, 240, 230), False, True),
    "triton":       ("armor", (52, 142, 142), GOLD, (40, 120, 130), SEA, "none", GOLD, False, False),
    "chrysaor":     ("armor", GOLD, (62, 172, 172), (242, 212, 110), TAN, "helmet", (62, 172, 172), False, False),
    "cymopoleia":   ("dress", (72, 92, 132), WHITE, (232, 236, 242), SEA, "circlet", SILVER, False, True),
    "rhode":        ("dress", (222, 122, 152), GOLD, (190, 64, 50), LIGHT, "flowers", (240, 90, 120), False, True),
    "pegasus":      ("toga", WHITE, (150, 200, 255), WHITE, WHITE_SKIN, "wings", (220, 232, 255), False, True),
    "benthesikyme": ("dress", (32, 52, 112), (62, 172, 172), (22, 24, 40), SEA, "circlet", (62, 172, 172), False, True),
    "halia":        ("dress", (152, 222, 202), WHITE, (242, 222, 150), SEA, "none", GOLD, False, True),
    "nereus":       ("toga", (112, 142, 162), WHITE, (232, 236, 240), TAN, "none", GOLD, True, False),
    "proteus":      ("robe", (52, 122, 112), GOLD, (150, 160, 160), SEA, "none", GOLD, True, False),
    "palaemon":     ("toga", (82, 182, 202), WHITE, (120, 84, 54), TAN, "circlet", GOLD, False, False),
    "thetis":       ("dress", (202, 206, 222), (62, 172, 172), (170, 200, 226), SEA, "circlet", SILVER, False, True),
    "galene":       ("dress", (172, 226, 236), WHITE, (150, 210, 236), SEA, "none", GOLD, False, True),
    "galatea":      ("dress", (240, 236, 226), (250, 170, 190), (122, 202, 212), SEA, "flowers", (250, 240, 235), False, True),
    # --- Hades line
    "hades":      ("robe", (42, 32, 62), (152, 72, 202), (22, 20, 28), PALE, "helmet", (152, 72, 202), True, False),
    "persephone": ("dress", (132, 32, 62), (90, 160, 80), (150, 70, 44), LIGHT, "flowers", (220, 50, 80), False, True),
    "zagreus":    ("armor", (122, 32, 32), GOLD, (22, 20, 24), PALE, "laurel", (220, 80, 50), False, False),
    "thanatos":   ("robe", (26, 26, 32), SILVER, (236, 236, 240), GHOST, "hood", (26, 26, 32), False, False),
    "hypnos":     ("robe", (42, 46, 102), (236, 220, 160), (236, 236, 240), PALE, "wings", WHITE, False, False),
    "melinoe":    ("dress", (62, 52, 82), (170, 230, 180), (236, 236, 240), GHOST, "circlet", (170, 230, 180), False, True),
    "macaria":    ("dress", (152, 142, 176), WHITE, (24, 22, 30), PALE, "none", GOLD, False, True),
    "hecate":     ("robe", (72, 32, 102), (240, 150, 50), (22, 20, 28), PALE, "crown", IRON, False, True),
    "morpheus":   ("robe", (62, 52, 132), (230, 230, 255), (40, 44, 96), PALE, "wings", (120, 110, 190), False, False),
    "nyx":        ("dress", (18, 18, 44), (200, 200, 255), (12, 12, 24), PALE, "circlet", (200, 200, 255), False, True),
    "erinyes":    ("robe", (102, 22, 28), (20, 18, 20), (62, 122, 62), GHOST, "horns", (40, 30, 34), False, True),
    "moirai":     ("robe", (122, 122, 128), WHITE, (190, 190, 196), PALE, "hood", (122, 122, 128), False, False),
    "keres":      ("robe", (62, 16, 22), BONE, (20, 18, 22), GHOST, "wings", (50, 40, 46), False, True),
    "oneiroi":    ("robe", (92, 62, 142), (202, 182, 255), (190, 160, 230), GHOST, "none", GOLD, False, False),
    "charon":     ("robe", (72, 62, 52), BONE, (150, 150, 150), GHOST, "hood", (58, 50, 42), True, False),
}

POWER_COLOR = {"LIGHTNING": (255, 246, 150), "FIRE": (255, 140, 40), "FREEZE": (160, 232, 255),
               "DRAIN": (226, 60, 96), "SWEEP": (80, 190, 255), "KNOCKBACK": (236, 236, 240), "NONE": (190, 150, 255)}

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

def clamp(v):
    return max(0, min(255, int(v)))

def shade(c, f):
    return tuple(clamp(v * f) for v in c[:3])

def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))

def all_gods():
    idx = 0
    for line, gods in LINES.items():
        for i, g in enumerate(gods):
            yield idx, line, i + 1, g
            idx += 1

# ----------------------------------------------------------------------------
# Java roster
# ----------------------------------------------------------------------------
def gen_java():
    rows = []
    for idx, line, rank, g in all_gods():
        gid, name, title, wname, shape, power, teff, seff = g
        rows.append(
            f"        new God({idx}, Line.{line}, {rank}, {jstr(gid)}, {jstr(name)}, {jstr(title)}, "
            f"{jstr(wname)}, Power.{power}, {jstr(teff)}, {jstr(seff)})"
        )
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

def read_version():
    """VERSION holds e.g. 1.2.0-alpha / 1.2.0-beta / 1.2.0 ; the suffix is the release channel."""
    raw = open(os.path.join(ROOT, "VERSION")).read().strip()
    number, _, channel = raw.partition("-")
    return raw, number, (channel.split(".")[0].capitalize() if channel else "Release")

def gen_build_info():
    raw, number, channel = read_version()
    w(os.path.join(ROOT, "common/src/main/java", *JAVA_PKG.split("."), "BuildInfo.java"), f"""package {JAVA_PKG};

/** GENERATED by tools/generate.py from the VERSION file - do not edit by hand. */
public final class BuildInfo {{
    private BuildInfo() {{}}

    public static final String VERSION = {jstr(raw)};
    public static final String NUMBER = {jstr(number)};
    /** "Alpha", "Beta" or "Release". */
    public static final String CHANNEL = {jstr(channel)};
    public static final String DISPLAY = {jstr(number + ("" if channel == "Release" else " " + channel))};
}}
""")

# ----------------------------------------------------------------------------
# Lang
# ----------------------------------------------------------------------------
def gen_lang():
    lang = {
        "itemGroup.realms_of_chaos.main": "Realms of Chaos",
        "item.realms_of_chaos.chaos_compass": "Compass of Chaos",
        "item.realms_of_chaos.chaos_compass.tooltip": "Right-click to travel to the Hub of Realms.",
        "item.realms_of_chaos.chaos_codex": "Codex of Chaos",
        "item.realms_of_chaos.chaos_codex.tooltip": "Right-click to open the Codex: your progress, the gods and fast travel.",
        "block.realms_of_chaos.gate_hub": "Gate to the Hub",
        "block.realms_of_chaos.gate_zeus": "Gate of Zeus",
        "block.realms_of_chaos.gate_poseidon": "Gate of Poseidon",
        "block.realms_of_chaos.gate_hades": "Gate of Hades",
        "block.realms_of_chaos.altar_zeus": "Trial Altar of Zeus",
        "block.realms_of_chaos.altar_poseidon": "Trial Altar of Poseidon",
        "block.realms_of_chaos.altar_hades": "Trial Altar of Hades",
        "entity.realms_of_chaos.god_boss": "God of the Realms",
        "block.realms_of_chaos.gate_ascend": "Gate of Ascent",
        "block.realms_of_chaos.gate_leave": "Gate to the Overworld",
        "hud.realms_of_chaos.hub": "Hub of Realms",
        "hud.realms_of_chaos.hub.sub": "Zeus north - Poseidon east - Hades west - Exit south",
        "hud.realms_of_chaos.floor": "%s - Floor %s of 15",
        "hud.realms_of_chaos.bar": "%s - Floor %s of 15 - %s",
        "hud.realms_of_chaos.left": "You leave the Realms of Chaos",
        "hud.realms_of_chaos.left.sub": "The gates remain open to you",
        "hud.realms_of_chaos.rescued": "The realm pulls you back",
        "hud.realms_of_chaos.not_inside": "You are not inside the Realms of Chaos",
        "hud.realms_of_chaos.unavailable": "That realm is not available in this world",
        "hud.realms_of_chaos.trial": "%s",
        "hud.realms_of_chaos.trial.sub": "%s - Rank %s of 15",
        "hud.realms_of_chaos.victory": "Victory!",
        "hud.realms_of_chaos.victory.sub": "%s has fallen",
        "hud.realms_of_chaos.ascend": "The Gate of Ascent opens: floor %s awaits",
        "hud.realms_of_chaos.ascend.locked": "Defeat %s to open the way up",
        "hud.realms_of_chaos.line_complete": "Tower conquered",
        "hud.realms_of_chaos.line_complete.sub": "The whole line of %s has fallen to you",
        "welcome.realms_of_chaos.title": "Realms of Chaos",
        "welcome.realms_of_chaos.subtitle": "Thank you for using our mod!",
        "welcome.realms_of_chaos.hint": "Open the Codex of Chaos in your inventory to begin",
        "menu.realms_of_chaos.codex": "Codex of Chaos",
        "menu.realms_of_chaos.line": "Line of %s",
        "menu.realms_of_chaos.line.progress": "%s / 15 gods defeated",
        "menu.realms_of_chaos.line.next": "Next trial: %s",
        "menu.realms_of_chaos.line.floor": "You are on floor %s of 15",
        "menu.realms_of_chaos.line.complete": "Tower conquered!",
        "menu.realms_of_chaos.click_view": "Click to view the tower",
        "menu.realms_of_chaos.hub": "Travel to the Hub of Realms",
        "menu.realms_of_chaos.leave": "Leave the Realms",
        "menu.realms_of_chaos.leave.lore": "Return to where you came from",
        "menu.realms_of_chaos.click": "Click to travel",
        "menu.realms_of_chaos.travel": "Enter the %s",
        "menu.realms_of_chaos.travel.lore": "Takes you to your current floor",
        "menu.realms_of_chaos.back": "Back",
        "menu.realms_of_chaos.tier.floors": "Floors %s-%s",
        "menu.realms_of_chaos.tier.progress": "%s / %s gods defeated",
        "menu.realms_of_chaos.tier.summit": "Leads to the %s",
        "menu.realms_of_chaos.strip.floor": "Floor %s - %s",
        "menu.realms_of_chaos.strip.here": "You are here",
        "hud.realms_of_chaos.shift": "The floor shifts beneath you!",
        "menu.realms_of_chaos.about": "Realms of Chaos",
        "menu.realms_of_chaos.about.lore1": "Thank you for using our mod!",
        "menu.realms_of_chaos.about.lore2": "By TheFarawayDev",
        "menu.realms_of_chaos.about.version": "Version %s",
        "menu.realms_of_chaos.about.total": "%s / 45 gods defeated",
        "menu.realms_of_chaos.god.defeated": "Defeated",
        "menu.realms_of_chaos.god.next": "Your next trial",
        "menu.realms_of_chaos.god.locked": "Locked - defeat %s first",
        "menu.realms_of_chaos.god.rank": "Rank %s of 15",
        "menu.realms_of_chaos.god.floor": "Floor %s - %s",
        "menu.realms_of_chaos.god.relic": "Relic: %s",
        "menu.realms_of_chaos.god.travel": "Click to travel to this floor",
        "menu.realms_of_chaos.altar": "Trial Altar",
        "menu.realms_of_chaos.altar.begin": "Begin the trial",
        "menu.realms_of_chaos.altar.begin.lore": "%s will rise at the centre of the floor",
        "menu.realms_of_chaos.altar.ascend": "Ascend to floor %s",
        "menu.realms_of_chaos.altar.ascend.lore": "Or walk through the Gate of Ascent",
        "menu.realms_of_chaos.altar.underway": "The trial is underway",
        "menu.realms_of_chaos.altar.underway.lore": "Defeat the god that stands against you",
        "menu.realms_of_chaos.altar.peaceful": "The gods do not answer",
        "menu.realms_of_chaos.altar.peaceful.lore": "Raise the difficulty above Peaceful",
        "menu.realms_of_chaos.altar.locked": "Not yet",
        "menu.realms_of_chaos.altar.complete": "Tower conquered",
        "menu.realms_of_chaos.altar.complete.lore": "Every god of this line has fallen to you",
        "menu.realms_of_chaos.altar.close": "Step back",
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
        "advancement.realms_of_chaos.root.title": "Realms of Chaos",
        "advancement.realms_of_chaos.root.desc": "Thank you for using our mod! Open the Codex of Chaos to begin. - TheFarawayDev",
    }
    for line, gods in LINES.items():
        info = LINE_INFO[line]
        lang[f"line.realms_of_chaos.{line.lower()}"] = info["label"]
        lang[f"zone.realms_of_chaos.{line.lower()}"] = info["zone"]
        tower, tiers = TIERS[line]
        lang[f"tower.realms_of_chaos.{line.lower()}"] = tower
        for t, tname in enumerate(tiers, start=1):
            lang[f"tier.realms_of_chaos.{line.lower()}.{t}"] = tname
        for rank, (gid, name, title, wname, *_rest) in enumerate(gods, start=1):
            lang[f"god.realms_of_chaos.{gid}"] = name
            lang[f"god.realms_of_chaos.{gid}.title"] = title
            lang[f"item.realms_of_chaos.{gid}_weapon"] = wname
            lang[f"advancement.realms_of_chaos.{gid}.desc"] = (
                f"Defeat {name}, {title}. Rank {rank} of the line of {info['label']}.")
    wj(os.path.join(ASSETS, "lang/en_us.json"), lang)

# ----------------------------------------------------------------------------
# Item definitions / models
# ----------------------------------------------------------------------------
BLOCKS = ["gate_hub", "gate_zeus", "gate_poseidon", "gate_hades", "gate_ascend", "gate_leave",
          "altar_zeus", "altar_poseidon", "altar_hades"]

def item_def(item_id, model_path):
    wj(os.path.join(ASSETS, f"items/{item_id}.json"),
       {"model": {"type": "minecraft:model", "model": f"{MOD}:{model_path}"}})

def gen_models():
    for _, _, _, g in all_gods():
        iid = f"{g[0]}_weapon"
        item_def(iid, f"item/{iid}")
        wj(os.path.join(ASSETS, f"models/item/{iid}.json"),
           {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{MOD}:item/{iid}"}})
    for flat in ("chaos_compass", "chaos_codex"):
        item_def(flat, f"item/{flat}")
        wj(os.path.join(ASSETS, f"models/item/{flat}.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{flat}"}})
    for b in BLOCKS:
        wj(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
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
# Weapon textures: hand-drawn 16x16 templates, auto-outlined, shaded, with an animated glint
#   h highlight  b blade  s blade shadow  g guard/accent  w wood  W dark wood  p gem (power colour)
# ----------------------------------------------------------------------------
SHAPES = {
    "sword": """
................
.............hh.
............hbb.
...........hbbs.
..........hbbs..
.........hbbs...
........hbbs....
.......hbbs.....
..g...hbbs......
..gg.hbbs.......
...ggbbs........
....ggg.........
...wWggg........
..wW...gg.......
.pW.............
................""",
    "dagger": """
................
................
................
...........hh...
..........hbb...
.........hbbs...
........hbbs....
.......hbbs.....
...g..hbbs......
...gg.bbs.......
....ggbs........
.....ggg........
....wWgg........
...wW...........
..pW............
................""",
    "spear": """
................
.............hh.
............hbbh
...........hbbbs
...........bbbs.
..........gbss..
.........gW.....
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................""",
    "trident": """
................
..........h...h.
.........hb..hb.
........gg..hb..
.........gghb...
..........ggb.h.
.........wWgghb.
........wW..gg..
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................""",
    "staff": """
................
...........ppp..
..........phppp.
..........ppppp.
..........gpppg.
.........wWggg..
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.wW.............
................
................""",
    "scythe": """
................
.....hhhhhh.....
...hhbbbbbbbh...
..hbbsss..sbbw..
..bs........wW..
..s........wW...
..........wW....
.........wW.....
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..pW............
................""",
    "hammer": """
................
..........hh....
.........hbbb...
........hbbbbb..
.......hbbbbbbs.
........bbbbbs..
.......wWbbbs...
......wW.bs.....
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................
................
................""",
    "bolt": """
................
...........hh...
..........hbb...
.........hbb....
........hbbb....
.......hbbbbbb..
......hbbbbbs...
.........bbs....
........bbs.....
.......bbs......
......bbs.......
.....bbs........
....bs..........
...bs...........
..s.............
................""",
}

def parse_shape(name):
    rows = [r for r in SHAPES[name].strip("\n").split("\n")]
    rows = [(r + "." * 16)[:16] for r in rows]
    rows += ["." * 16] * (16 - len(rows))
    return rows[:16]

def weapon_frames(gid, shape, line, power):
    rnd = random.Random(hsh("w" + gid))
    metals = LINE_INFO[line]["metals"]
    metal = metals[hsh(gid) % len(metals)]
    if gid in ("zeus",):
        metal = (255, 236, 120)
    trim = LOOKS[gid][2]
    gem = POWER_COLOR[power]
    wood = mix((116, 78, 46), LOOKS[gid][1], 0.15)
    pal = {"h": mix(metal, (255, 255, 255), 0.55), "b": metal, "s": shade(metal, 0.66), "g": trim,
           "w": wood, "W": shade(wood, 0.66), "p": gem}
    rows = parse_shape(shape)
    base = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                c = pal[ch]
                if ch == "g" and (x + y) % 2:
                    c = shade(c, 0.82)
                base[(x, y)] = c
    # auto outline: every empty pixel that touches a filled one becomes a dark rim
    outline = {}
    for (x, y), c in base.items():
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy)
            if p not in base and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                outline[p] = shade(c, 0.28)
    blade = sorted([p for p, _ in base.items() if rows[p[1]][p[0]] in "hbs"], key=lambda p: p[0] - p[1])
    gems = [p for p in base if rows[p[1]][p[0]] == "p"]
    frames = []
    n_sweep = 6
    for f in range(1 + n_sweep):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for p, c in outline.items():
            px[p] = c + (255,)
        for p, c in base.items():
            px[p] = c + (255,)
        if f > 0 and blade:
            lo, hi = blade[0][0] - blade[0][1], blade[-1][0] - blade[-1][1]
            pos = lo + (hi - lo) * (f - 1) / (n_sweep - 1)
            for p in blade:
                d = abs((p[0] - p[1]) - pos)
                if d <= 1.5:
                    px[p] = mix(base[p], (255, 255, 255), 0.85 if d < 0.8 else 0.45) + (255,)
            for p in gems:
                px[p] = mix(gem, (255, 255, 255), 0.5) + (255,)
        frames.append(img)
    return frames

def save_strip(frames, path, mcmeta):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    wdt, hgt = frames[0].size
    strip = Image.new("RGBA", (wdt, hgt * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * hgt))
    strip.save(path)
    wj(path + ".mcmeta", mcmeta)

def gen_weapons():
    first = {}
    for _, line, rank, g in all_gods():
        gid, _, _, _, shape, power, *_ = g
        frames = weapon_frames(gid, shape, line, power)
        # frame 0 rests for a while, then the glint sweeps along the blade
        meta = {"animation": {"frametime": 2, "frames": [{"index": 0, "time": 50 + (hsh(gid) % 30)}] + list(range(1, len(frames)))}}
        save_strip(frames, os.path.join(ASSETS, f"textures/item/{gid}_weapon.png"), meta)
        first[gid] = frames[0]
    return first

def gen_flat_items():
    # Compass of Chaos: a brass compass whose needle spins
    frames = []
    for f in range(8):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 7.4:
                    px[x, y] = (24, 18, 44, 255)
                if 5.6 <= d < 7.4:
                    px[x, y] = shade((226, 176, 72), 1.1 if (x + y) < 14 else 0.75) + (255,)
                if 4.6 <= d < 5.6:
                    px[x, y] = (52, 40, 84, 255)
        a = f / 8 * math.tau
        for r in range(1, 5):
            px[int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))] = (240, 80, 90, 255)
            px[int(round(7.5 - math.cos(a) * r)), int(round(7.5 - math.sin(a) * r))] = (236, 236, 246, 255)
        px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = (250, 220, 120, 255)
        frames.append(img)
    save_strip(frames, os.path.join(ASSETS, "textures/item/chaos_compass.png"), {"animation": {"frametime": 3}})
    # Codex of Chaos: a bound book with a three-coloured sigil that pulses
    book = """
................
...oooooooooo...
..oPPPPPPPPPPo..
..oPggggggggPo..
..oPgPPPPPPgPo..
..oPgP1PP2PgPo..
..oPgPPPPPPgPo..
..oPgPP33PPgPo..
..oPgPPPPPPgPo..
..oPggggggggPo..
..oPPPPPPPPPPo..
..oppppppppppo..
..owwwwwwwwwwo..
...oooooooooo...
................
................"""
    rows = [(r + "." * 16)[:16] for r in book.strip("\n").split("\n")]
    frames = []
    for f in range(6):
        glow = 0.5 + 0.5 * math.sin(f / 6 * math.tau)
        pal = {"o": (20, 12, 36), "P": (98, 52, 150), "p": (70, 36, 110), "g": (236, 196, 84), "w": (236, 228, 204),
               "1": mix((236, 196, 84), (255, 255, 255), glow * 0.7), "2": mix((62, 190, 210), (255, 255, 255), glow * 0.7),
               "3": mix((190, 90, 240), (255, 255, 255), glow * 0.7)}
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in pal:
                    c = pal[ch]
                    if ch == "P" and x < 5:
                        c = shade(c, 1.2)
                    px[x, y] = c + (255,)
        frames.append(img)
    save_strip(frames, os.path.join(ASSETS, "textures/item/chaos_codex.png"), {"animation": {"frametime": 4}})

# ----------------------------------------------------------------------------
# Block textures (animated)
# ----------------------------------------------------------------------------
def gen_block_textures():
    T = os.path.join(ASSETS, "textures/block")
    gates = {"gate_hub": ((60, 30, 120), (170, 120, 255), (240, 220, 255)),
             "gate_zeus": ((120, 80, 10), (250, 205, 70), (255, 250, 200)),
             "gate_poseidon": ((10, 60, 110), (50, 190, 225), (210, 250, 255)),
             "gate_hades": ((40, 10, 70), (150, 50, 215), (240, 170, 255)),
             "gate_ascend": ((150, 150, 170), (236, 236, 246), (255, 246, 190)),
             "gate_leave": ((14, 70, 30), (70, 190, 100), (210, 255, 200))}
    n = 16
    for name, (dark, mid, light) in gates.items():
        rnd = random.Random(hsh(name))
        sparks = [(rnd.randrange(16), rnd.randrange(16), rnd.randrange(n)) for _ in range(10)]
        frames = []
        for f in range(n):
            ph = f / n * math.tau
            img = Image.new("RGBA", (16, 16))
            px = img.load()
            for y in range(16):
                for x in range(16):
                    # tileable flowing waves (period 16 in x and y) so the 3x4 gate reads as one surface
                    v = (math.sin((x + y) / 16 * math.tau * 2 - ph * 2) + math.sin((x - y) / 16 * math.tau + ph)
                         + math.sin(y / 16 * math.tau * 3 + ph * 3) * 0.6) / 2.6
                    v = (v + 1) / 2
                    v = round(v * 5) / 5            # posterise for a pixel-art look
                    c = mix(dark, mid, min(1, v * 1.6)) if v < 0.62 else mix(mid, light, (v - 0.62) / 0.38)
                    px[x, y] = c + (255,)
            for (sx, sy, sf) in sparks:
                if (f - sf) % n < 3:
                    px[sx, (sy - (f - sf) % n) % 16] = light + (255,)
            frames.append(img)
        save_strip(frames, os.path.join(T, f"{name}.png"), {"animation": {"frametime": 2, "interpolate": True}})

    stone = {"altar_zeus": ((232, 232, 240), (250, 205, 70)), "altar_poseidon": ((74, 150, 150), (120, 240, 250)),
             "altar_hades": ((44, 38, 58), (190, 80, 250))}
    base = Image.new("RGBA", (16, 16))
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            base.putpixel((x, y), shade((64, 62, 72), 1 + rnd.uniform(-0.1, 0.1)) + (255,))
    os.makedirs(T, exist_ok=True)
    base.save(os.path.join(T, "altar_base.png"))
    key = ["................", "gggggggggggggggg", "................", ".ggg.ggg.ggg.ggg", ".g.g.g.g.g.g.g.g",
           ".g...g...g...g..", ".ggggggggggggggg", "................"]
    rune = ["..rrrr..", ".r....r.", "r..rr..r", "r.r..r.r", "r.r..r.r", "r..rr..r", ".r....r.", "..rrrr.."]
    for name, (body, glow) in stone.items():
        rnd = random.Random(hsh(name))
        noise = [[1 + rnd.uniform(-0.06, 0.06) for _ in range(16)] for _ in range(16)]
        side_frames, top_frames = [], []
        for f in range(8):
            k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
            g = mix(shade(glow, 0.55), mix(glow, (255, 255, 255), 0.35), k)
            side = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    c = shade(body, noise[y][x] * (1.08 if y < 2 else 0.86 if y > 13 else 1.0))
                    if x in (0, 15):
                        c = shade(c, 0.8)
                    if y < 8 and key[y][x] == "g":
                        c = shade(body, 0.62)
                    ry, rx = y - 8, x - 4
                    if 0 <= ry < 8 and 0 <= rx < 8 and rune[ry][rx] == "r":
                        c = g
                    side.putpixel((x, y), c + (255,))
            side_frames.append(side)
            top = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    d = max(abs(x - 7.5), abs(y - 7.5))
                    r = math.hypot(x - 7.5, y - 7.5)
                    c = shade(body, noise[y][x])
                    if d > 6.6:
                        c = shade(body, 0.7)
                    if 4.2 <= r < 5.4:
                        c = g
                    if r < 2.6:
                        c = mix(g, (255, 255, 255), 0.35 * k)
                    if (abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6) and 2.6 <= r < 4.2:
                        c = shade(g, 0.8)
                    top.putpixel((x, y), c + (255,))
            top_frames.append(top)
        save_strip(side_frames, os.path.join(T, f"{name}_side.png"), {"animation": {"frametime": 4, "interpolate": True}})
        save_strip(top_frames, os.path.join(T, f"{name}_top.png"), {"animation": {"frametime": 4, "interpolate": True}})

# ----------------------------------------------------------------------------
# Boss skins: 64x64 humanoid layout, per-god outfit, hair, face and headgear (hat layer)
# ----------------------------------------------------------------------------
def faces(u, v, wd, ht, dp):
    return {"top": (u + dp, v, wd, dp), "bottom": (u + dp + wd, v, wd, dp), "right": (u, v + dp, dp, ht),
            "front": (u + dp, v + dp, wd, ht), "left": (u + dp + wd, v + dp, dp, ht), "back": (u + dp + wd + dp, v + dp, wd, ht)}

PARTS = {"head": faces(0, 0, 8, 8, 8), "hat": faces(32, 0, 8, 8, 8), "body": faces(16, 16, 8, 12, 4),
         "rarm": faces(40, 16, 4, 12, 4), "larm": faces(32, 48, 4, 12, 4),
         "rleg": faces(0, 16, 4, 12, 4), "lleg": faces(16, 48, 4, 12, 4)}
SIDES = ("front", "back", "left", "right")

class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)

    def put(self, part, face, x, y, c):
        u, v, wd, ht = PARTS[part][face]
        if 0 <= x < wd and 0 <= y < ht:
            self.px[u + x, v + y] = tuple(c[:3]) + (255,)

    def fill(self, part, face, c, y0=0, y1=None, x0=0, x1=None, lit=True):
        u, v, wd, ht = PARTS[part][face]
        y1 = ht if y1 is None else y1
        x1 = wd if x1 is None else x1
        for y in range(max(0, y0), min(ht, y1)):
            for x in range(max(0, x0), min(wd, x1)):
                f = 1 + self.rnd.uniform(-0.035, 0.035)
                if lit:
                    f *= 1.0 - 0.10 * (y / max(1, ht - 1))            # soft top-down light
                    if face in ("left", "right", "back"):
                        f *= 0.9
                    if x == 0 or x == wd - 1:
                        f *= 0.93
                self.px[u + x, v + y] = shade(c, f) + (255,)

    def band(self, part, c, y0, y1, faces_=SIDES):
        for face in faces_:
            self.fill(part, face, c, y0, y1)

def make_skin(gid, line, rank):
    outfit, cloth, trim, hair, skin, gear, gear_col, beard, long_hair = LOOKS[gid]
    s = Skin(hsh("skin" + gid))
    underworld = line == "HADES"
    limbs = ("rarm", "larm", "rleg", "lleg")

    # --- bare skin everywhere first
    for part in ("head", "body") + limbs:
        for face in PARTS[part]:
            s.fill(part, face, skin)

    # --- outfit
    dark, light = shade(cloth, 0.72), mix(cloth, (255, 255, 255), 0.25)
    if outfit in ("toga", "dress"):
        for face in SIDES:
            s.fill("body", face, cloth)
        s.fill("body", "top", cloth); s.fill("body", "bottom", dark)
        # one bare shoulder with a trimmed diagonal drape
        for y in range(5):
            for x in range(8):
                if x + y < 3:
                    s.put("body", "front", x, y, shade(skin, 0.97))
                elif x + y == 3:
                    s.put("body", "front", x, y, trim)
        for x in range(8):                                            # folds
            for y in range(5, 12):
                if x in (2, 5):
                    s.put("body", "front", x, y, dark)
        s.band("body", trim, 7, 8)                                    # belt
        s.put("body", "front", 3, 7, mix(trim, (255, 255, 255), 0.5)); s.put("body", "front", 4, 7, shade(trim, 0.7))
        hem = 10 if outfit == "dress" else 5
        for leg in ("rleg", "lleg"):
            s.band(leg, cloth, 0, hem)
            s.band(leg, trim, hem, hem + 1)
            for face in SIDES:
                s.put(leg, face, 1, 2, dark)
            if outfit == "toga":                                      # sandals
                s.band(leg, (120, 84, 52), 11, 12)
                s.band(leg, (120, 84, 52), 8, 9)
            s.fill(leg, "top", cloth)
        for arm in ("rarm", "larm"):
            s.band(arm, trim, 8, 9)                                   # bracer
            if outfit == "dress":
                s.band(arm, cloth, 0, 2)
                s.fill(arm, "top", cloth)
        s.band("larm", cloth, 0, 3); s.fill("larm", "top", cloth)     # draped shoulder
    elif outfit == "robe":
        for part in ("body",) + limbs:
            for face in PARTS[part]:
                s.fill(part, face, cloth)
        for y in range(12):                                           # trimmed opening down the front
            s.put("body", "front", 3, y, trim); s.put("body", "front", 4, y, shade(trim, 0.8))
        s.band("body", shade(trim, 0.85), 6, 7)
        for arm in ("rarm", "larm"):
            s.band(arm, trim, 8, 9)
            s.band(arm, skin, 9, 12)
            s.fill(arm, "bottom", skin)
        for leg in ("rleg", "lleg"):
            s.band(leg, trim, 10, 11)
            s.band(leg, dark, 11, 12)
            for face in SIDES:
                s.put(leg, face, 2, 4, dark); s.put(leg, face, 1, 7, dark)
    elif outfit == "armor":
        metal, hi, lo = cloth, mix(cloth, (255, 255, 255), 0.4), shade(cloth, 0.62)
        for face in SIDES:
            s.fill("body", face, metal)
        s.fill("body", "top", metal)
        for x in range(8):                                            # sculpted breastplate
            s.put("body", "front", x, 0, hi)
        for y in range(1, 6):
            s.put("body", "front", 3, y, lo); s.put("body", "front", 4, y, lo)
        for x in (1, 2, 5, 6):
            s.put("body", "front", x, 2, hi); s.put("body", "front", x, 4, lo)
        s.put("body", "front", 3, 3, trim); s.put("body", "front", 4, 3, trim)   # emblem
        s.band("body", trim, 7, 8)
        for face in SIDES:                                            # leather strips below the belt
            for x in range(8 if face in ("front", "back") else 4):
                for y in range(8, 12):
                    s.put("body", face, x, y, (112, 70, 44) if x % 2 else (84, 52, 34))
        for arm in ("rarm", "larm"):
            s.band(arm, metal, 0, 3); s.fill(arm, "top", hi)          # pauldron
            s.band(arm, trim, 3, 4)
            s.band(arm, metal, 8, 10)                                 # vambrace
        for leg in ("rleg", "lleg"):
            for face in SIDES:
                for x in range(4):
                    for y in range(0, 3):
                        s.put(leg, face, x, y, (112, 70, 44) if x % 2 else (84, 52, 34))
            s.band(leg, metal, 6, 12)                                 # greaves
            s.band(leg, hi, 6, 7)
            s.band(leg, lo, 11, 12)
    elif outfit == "wild":                                            # Pan: bare chest, shaggy goat legs
        fur, fur2 = cloth, shade(cloth, 0.75)
        for leg in ("rleg", "lleg"):
            for face in PARTS[leg]:
                s.fill(leg, face, fur)
            for face in SIDES:
                for y in range(12):
                    for x in range(4):
                        if (x * 3 + y * 5) % 4 == 0:
                            s.put(leg, face, x, y, fur2)
            s.band(leg, (40, 30, 24), 11, 12)                         # hooves
        s.band("body", fur, 9, 12)
        s.band("body", trim, 8, 9)                                    # vine belt
        for x in range(8):
            s.put("body", "front", x, max(0, x - 2) if x < 6 else 4, trim) if x % 2 == 0 else None
        for arm in ("rarm", "larm"):
            s.band(arm, trim, 7, 8)

    # --- rank: stronger gods carry a gold collar / emblem
    if rank <= 8 and outfit != "armor":
        s.put("body", "front", 3, 4 if outfit != "robe" else 2, mix(trim, (255, 255, 255), 0.4))
        s.put("body", "front", 4, 4 if outfit != "robe" else 2, mix(trim, (255, 255, 255), 0.4))
    if rank <= 3:
        for face in SIDES:
            for x in range(8 if face in ("front", "back") else 4):
                if x % 2 == 0:
                    s.put("body", face, x, 0, GOLD)

    # --- hair
    s.fill("head", "top", hair)
    side_len, back_len = (7, 8) if long_hair else (3, 4)
    s.fill("head", "front", hair, 0, 2)
    s.fill("head", "left", hair, 0, side_len); s.fill("head", "right", hair, 0, side_len)
    s.fill("head", "back", hair, 0, back_len)
    if long_hair:
        s.put("head", "front", 0, 2, hair); s.put("head", "front", 7, 2, hair)
        s.put("head", "front", 0, 3, hair); s.put("head", "front", 7, 3, hair)
        s.fill("body", "back", shade(hair, 0.92), 0, 4, 1, 7)
    hl = mix(hair, (255, 255, 255), 0.25)
    for x in (1, 4, 6):
        s.put("head", "front", x, 0, hl); s.put("head", "top", x, 5, hl)

    # --- face
    eye_white = (250, 250, 250)
    iris = shade(cloth, 0.55) if not underworld else mix(trim, (255, 255, 255), 0.35)
    if underworld:
        eye_white = iris
    brow = shade(hair, 0.6) if sum(hair) < 600 else shade(skin, 0.7)
    for ex in (1, 5):
        s.put("head", "front", ex, 3, brow); s.put("head", "front", ex + 1, 3, brow)
    s.put("head", "front", 1, 4, eye_white); s.put("head", "front", 2, 4, iris)
    s.put("head", "front", 5, 4, iris); s.put("head", "front", 6, 4, eye_white)
    s.put("head", "front", 3, 5, shade(skin, 0.86)); s.put("head", "front", 4, 5, shade(skin, 0.86))
    s.put("head", "front", 3, 6, shade(skin, 0.68)); s.put("head", "front", 4, 6, shade(skin, 0.68))
    if beard:
        bc = shade(hair, 0.95)
        s.fill("head", "front", bc, 6, 8)
        s.put("head", "front", 0, 5, bc); s.put("head", "front", 7, 5, bc)
        s.put("head", "front", 3, 6, shade(skin, 0.62)); s.put("head", "front", 4, 6, shade(skin, 0.62))
        s.fill("head", "left", bc, 5, 8, 4, 8); s.fill("head", "right", bc, 5, 8, 0, 4)
        s.fill("head", "bottom", bc)
        s.fill("body", "front", bc, 0, 2, 2, 6)
    else:
        s.put("head", "front", 2, 5, mix(skin, (240, 130, 130), 0.25)) if not underworld else None

    # --- headgear on the outer hat layer
    g, gh, gl = gear_col, mix(gear_col, (255, 255, 255), 0.45), shade(gear_col, 0.65)
    def ring(y, col_fn):
        for face in SIDES:
            for x in range(8):
                c = col_fn(face, x)
                if c is not None:
                    s.put("hat", face, x, y, c)
    if gear == "crown":
        ring(1, lambda f, x: g)
        ring(0, lambda f, x: gh if x % 2 == 0 else None)
        s.put("hat", "front", 3, 1, POWER_COLOR["DRAIN"]); s.put("hat", "front", 4, 1, POWER_COLOR["SWEEP"])
    elif gear == "laurel":
        ring(1, lambda f, x: None if (f == "front" and x in (3, 4)) else (g if x % 2 == 0 else gl))
        ring(0, lambda f, x: gh if (f != "front" and x % 3 == 0) else None)
    elif gear == "circlet":
        ring(1, lambda f, x: g)
        s.put("hat", "front", 3, 1, gh); s.put("hat", "front", 4, 1, gh); s.put("hat", "front", 3, 0, gh)
    elif gear == "flowers":
        cols = [g, (120, 190, 110), mix(g, (255, 255, 255), 0.5), (120, 190, 110)]
        ring(1, lambda f, x: cols[x % 4])
        ring(0, lambda f, x: cols[(x + 2) % 4] if x % 4 == 0 else None)
    elif gear == "rainbow":
        bow = [(240, 70, 70), (245, 150, 50), (245, 225, 70), (90, 200, 90), (70, 160, 240), (130, 90, 220), (200, 100, 220), (240, 70, 70)]
        ring(1, lambda f, x: bow[x])
        ring(0, lambda f, x: bow[(x + 3) % 8] if f == "front" and x in (2, 3, 4, 5) else None)
    elif gear == "helmet":
        metal = (150, 150, 162) if sum(cloth) < 330 else mix(cloth, (255, 255, 255), 0.15)
        if gid == "hades":
            metal = (46, 40, 60)
        s.fill("hat", "top", metal)
        for face in ("left", "right", "back"):
            s.fill("hat", face, metal, 0, 6)
        s.fill("hat", "front", metal, 0, 2)
        for y in range(2, 7):                                         # cheek guards + nose guard
            s.put("hat", "front", 0, y, shade(metal, 0.85)); s.put("hat", "front", 7, y, shade(metal, 0.85))
        s.put("hat", "front", 3, 2, metal); s.put("hat", "front", 4, 2, metal); s.put("hat", "front", 3, 3, shade(metal, 0.8))
        for i in range(8):                                            # crest
            s.put("hat", "top", 3, i, g); s.put("hat", "top", 4, i, gh)
        for y in range(0, 5):
            s.put("hat", "back", 3, y, g); s.put("hat", "back", 4, y, gl)
        s.put("hat", "front", 3, 0, g); s.put("hat", "front", 4, 0, gh)
    elif gear == "hood":
        s.fill("hat", "top", g)
        for face in ("left", "right", "back"):
            s.fill("hat", face, g)
        s.fill("hat", "front", shade(g, 0.9), 0, 2)
        for y in range(2, 8):
            s.put("hat", "front", 0, y, shade(g, 0.8)); s.put("hat", "front", 7, y, shade(g, 0.8))
        s.put("hat", "front", 1, 2, shade(g, 0.7)); s.put("hat", "front", 6, 2, shade(g, 0.7))
        s.fill("head", "front", shade(skin, 0.8), 2, 4, 1, 7)         # face in shadow
        s.put("head", "front", 1, 4, eye_white); s.put("head", "front", 2, 4, iris)
        s.put("head", "front", 5, 4, iris); s.put("head", "front", 6, 4, eye_white)
    elif gear == "wings":
        for face, xs in (("left", (1, 2, 3, 4)), ("right", (3, 4, 5, 6))):
            for i, x in enumerate(xs):
                s.put("hat", face, x, 2, g); s.put("hat", face, x, 3, gh if i % 2 else g)
            s.put("hat", face, xs[0], 1, gh); s.put("hat", face, xs[1], 1, g); s.put("hat", face, xs[-1], 4, gl)
        ring(1, lambda f, x: GOLD if f == "front" else None)
    elif gear == "horns":
        for x in (1, 6):
            s.put("hat", "front", x, 0, g); s.put("hat", "front", x, 1, gl)
            s.put("hat", "top", x, 6, g); s.put("hat", "top", x, 5, gh); s.put("hat", "top", x, 7, gl)
        s.put("hat", "left", 1, 0, g); s.put("hat", "right", 6, 0, g)
    return s.img

def skin_preview(img, scale=6):
    """Front view of the assembled character, for eyeballing designs and for the website."""
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    def blit(part, face, dx, dy):
        u, v, wd, ht = PARTS[part][face]
        tile = img.crop((u, v, u + wd, v + ht))
        out.alpha_composite(tile, (dx, dy))
    blit("head", "front", 4, 0); blit("hat", "front", 4, 0)
    blit("body", "front", 4, 8); blit("rarm", "front", 0, 8); blit("larm", "front", 12, 8)
    blit("rleg", "front", 4, 20); blit("lleg", "front", 8, 20)
    return out.resize((16 * scale, 32 * scale), Image.NEAREST)

def gen_skins():
    previews = {}
    for _, line, rank, g in all_gods():
        img = make_skin(g[0], line, rank)
        path = os.path.join(ASSETS, f"textures/entity/god/{g[0]}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        previews[g[0]] = skin_preview(img, 1)
    return previews

# ----------------------------------------------------------------------------
# Datapack: void dimensions + dimension types + advancements
# ----------------------------------------------------------------------------
REALMS = {
    # id: (skybox, sky color, fog color, sky light color, sky light factor, ambient light)
    "hub":        ("overworld", "#78a7ff", "#c0d8ff", "#ffffff", 1.0, 0.10),
    "olympus":    ("overworld", "#a8d4ff", "#eaf5ff", "#fff6d8", 1.0, 0.15),
    "abyss":      ("none",      "#06223d", "#0b3d5c", "#4aa8c8", 0.45, 0.20),
    "underworld": ("none",      "#1a0810", "#2a0f18", "#c04030", 0.05, 0.12),
}

def gen_data():
    for rid, (sky, skyc, fog, slc, slf, amb) in REALMS.items():
        wj(os.path.join(DATA, f"dimension_type/{rid}.json"), {
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
            "height": 384,
            "infiniburn": "#minecraft:infiniburn_end",
            "logical_height": 384,
            "min_y": 0,
            "monster_spawn_block_light_limit": 0,
            "monster_spawn_light_level": 0,
            "skybox": sky,
        })
        wj(os.path.join(DATA, f"dimension/{rid}.json"), {
            "type": f"{MOD}:{rid}",
            "generator": {"type": "minecraft:flat",
                          "settings": {"biome": "minecraft:the_void", "features": False, "lakes": False,
                                       "layers": [], "structure_overrides": []}},
        })

def gen_advancements():
    """One tab: root (granted on first join) -> three chains, starter (rank 15) up to the final god (rank 1)."""
    adv = os.path.join(DATA, "advancement")
    def node(path, parent, icon, title, desc, frame, toast=True, background=None):
        display = {"icon": {"id": icon}, "title": title, "description": desc, "frame": frame,
                   "show_toast": toast, "announce_to_chat": frame == "challenge", "hidden": False}
        if background:
            display["background"] = background
        obj = {"display": display, "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]]}
        if parent:
            obj["parent"] = parent
        wj(os.path.join(adv, path + ".json"), obj)
    node("root", None, f"{MOD}:chaos_codex", {"translate": "advancement.realms_of_chaos.root.title"},
         {"translate": "advancement.realms_of_chaos.root.desc"}, "task",
         background="minecraft:gui/advancements/backgrounds/end")
    for line, gods in LINES.items():
        parent = f"{MOD}:root"
        for rank in range(15, 0, -1):
            gid = gods[rank - 1][0]
            frame = "challenge" if rank == 1 else "goal" if rank <= 5 else "task"
            path = f"{line.lower()}/{gid}"
            node(path, parent, f"{MOD}:{gid}_weapon", {"translate": f"god.realms_of_chaos.{gid}"},
                 {"translate": f"advancement.realms_of_chaos.{gid}.desc"}, frame)
            parent = f"{MOD}:{path}"

def gen_pack():
    wj(os.path.join(RES, "pack.mcmeta"), {
        "pack": {"description": "Realms of Chaos resources by TheFarawayDev", "min_format": 97, "max_format": 121}
    })

def gen_icon(previews):
    """Mod icon: the three final gods standing in front of their gate colours."""
    img = Image.new("RGBA", (128, 128), (22, 18, 43, 255))
    px = img.load()
    cols = [(245, 184, 46), (43, 183, 204), (165, 90, 214)]
    for i, c in enumerate(cols):
        for y in range(128):
            for x in range(i * 43, min(128, (i + 1) * 43)):
                k = 0.28 + 0.34 * (y / 127)
                px[x, y] = shade(c, k) + (255,)
    for y in range(128):
        for x in range(128):
            if x < 4 or y < 4 or x > 123 or y > 123:
                px[x, y] = (245, 184, 46, 255)
    for i, gid in enumerate(("zeus", "poseidon", "hades")):
        fig = previews[gid].resize((48, 96), Image.NEAREST)
        img.alpha_composite(fig, (i * 43 - 3 + (1 if i else 3), 24))
    path = os.path.join(ASSETS, "icon.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)

# ----------------------------------------------------------------------------
# Website data: roster + icon atlases (weapons 16px, portraits 16x32) in roster order
# ----------------------------------------------------------------------------
def gen_site(weapon_first, previews):
    if not os.path.isdir(SITE):
        return
    order = [g for _, _, _, g in all_gods()]
    atlas = Image.new("RGBA", (16 * len(order), 16), (0, 0, 0, 0))
    people = Image.new("RGBA", (16 * len(order), 32), (0, 0, 0, 0))
    for i, g in enumerate(order):
        atlas.paste(weapon_first[g[0]], (i * 16, 0))
        people.paste(previews[g[0]], (i * 16, 0))
    atlas.save(os.path.join(SITE, "weapons.png"))
    people.save(os.path.join(SITE, "gods.png"))
    roster = []
    for idx, line, rank, g in all_gods():
        floor = 16 - rank
        tier = 0 if floor <= 5 else 1 if floor <= 10 else 2 if floor <= 14 else 3
        roster.append({"i": idx, "line": line.lower(), "rank": rank, "floor": floor, "tier": TIERS[line][1][tier],
                       "id": g[0], "name": g[1], "title": g[2], "weapon": g[3], "power": g[5].lower()})
    wj(os.path.join(SITE, "roster.json"), roster)
    raw, number, channel = read_version()
    wj(os.path.join(SITE, "version.json"), {"version": raw, "number": number, "channel": channel})

# ----------------------------------------------------------------------------
# Promotional art for the unlisted /promotional/ page: drawn from the same sprites the mod ships
# ----------------------------------------------------------------------------
PROMO_BG, PROMO_GRID, PROMO_PANEL, PROMO_SHADOW = (22, 18, 43), (32, 27, 61), (38, 32, 80), (17, 13, 38)
PROMO_COLS = {"ZEUS": ((245, 184, 46), (169, 113, 10)), "POSEIDON": ((43, 183, 204), (20, 112, 127)),
              "HADES": ((165, 90, 214), (101, 45, 143))}

def gen_promo(weapon_first, previews):
    from PIL import ImageDraw, ImageFont
    out = os.path.join(SITE, "promotional")
    font_path = os.path.join(SITE, "Monocraft.ttf")
    if not os.path.isdir(SITE) or not os.path.exists(font_path):
        return
    os.makedirs(out, exist_ok=True)
    raw, number, channel = read_version()
    short = "v" + ".".join(number.split(".")[:2])
    label = short + ("" if channel == "Release" else " " + channel.upper())
    font = lambda size: ImageFont.truetype(font_path, size)

    def canvas(wd, ht):
        img = Image.new("RGBA", (wd, ht), PROMO_BG + (255,))
        d = ImageDraw.Draw(img)
        for x in range(0, wd, 64):
            d.rectangle([x, 0, x + 1, ht], fill=PROMO_GRID)
        for y in range(0, ht, 64):
            d.rectangle([0, y, wd, y + 1], fill=PROMO_GRID)
        for x in range(0, wd, 192):
            for y in range(0, ht, 192):
                d.rectangle([x - 4, y - 4, x + 5, y + 5], fill=(42, 36, 80))
        return img, d

    def chunk(d, box, col, sh, radius=18, depth=10):
        x0, y0, x1, y1 = box
        d.rounded_rectangle([x0, y0 + depth, x1, y1 + depth], radius, fill=sh)
        d.rounded_rectangle(box, radius, fill=col)

    def title(d, x, y, size, anchor="la"):
        f = font(size)
        gap = int(size * 1.02)
        for i, (text, col, sh) in enumerate((("REALMS", (255, 255, 255), (169, 113, 10)), ("OF CHAOS", (245, 184, 46), (169, 113, 10)))):
            yy = y + i * gap
            k = max(3, size // 14)
            d.text((x, yy + k * 2), text, font=f, fill=(0, 0, 0), anchor=anchor)
            d.text((x + k, yy + k), text, font=f, fill=sh, anchor=anchor)
            d.text((x, yy), text, font=f, fill=col, anchor=anchor)
        return y + 2 * gap

    def badge(d, x, y, size, anchor_right=False):
        f = font(size)
        wd = int(d.textlength(label, font=f)) + size
        if anchor_right:
            x -= wd
        chunk(d, [x, y, x + wd, y + int(size * 1.7)], PROMO_COLS["HADES"][0], PROMO_COLS["HADES"][1], radius=size // 2, depth=max(4, size // 5))
        d.text((x + size // 2, y + int(size * 0.32)), label, font=f, fill=(255, 255, 255))
        return wd

    def sprite(img, gid, x, y, k):
        fig = previews[gid].resize((16 * k, 32 * k), Image.NEAREST)
        img.alpha_composite(fig, (x, y))

    def bar(d, x, y, wd, ht):
        third = wd // 3
        for i, line in enumerate(("ZEUS", "POSEIDON", "HADES")):
            d.rectangle([x + i * third, y, x + (i + 1) * third, y + ht], fill=PROMO_COLS[line][0])

    def champions(img, d, x, y, k, gap):
        for i, (line, gid) in enumerate((("ZEUS", "zeus"), ("POSEIDON", "poseidon"), ("HADES", "hades"))):
            col, sh = PROMO_COLS[line]
            bx = x + i * (16 * k + gap + 40)
            chunk(d, [bx, y, bx + 16 * k + 40, y + 32 * k + 40], col, sh)
            sprite(img, gid, bx + 20, y + 20, k)

    def weapons_row(img, x, y, k, ids):
        for i, gid in enumerate(ids):
            icon = weapon_first[gid].resize((16 * k, 16 * k), Image.NEAREST)
            img.alpha_composite(icon, (x + i * (16 * k + k * 2), y))

    tagline = "Three towers. Forty-five gods. Floors that move."
    sub = "Fabric + Forge  -  Minecraft 26.3  -  by TheFarawayDev"
    order = [g[0] for _, _, _, g in all_gods()]

    # 1. social card 1200x630
    img, d = canvas(1200, 630)
    y = title(d, 64, 70, 104)
    bar(d, 64, y + 18, 560, 14)
    badge(d, 64, y + 62, 34)
    d.text((64, 520), tagline, font=font(24), fill=(238, 234, 255))
    d.text((64, 562), sub, font=font(17), fill=(170, 163, 214))
    champions(img, d, 690, 110, 8, 0)
    img.convert("RGB").save(os.path.join(out, "banner-1200x630.png"))

    # 2. square 1080x1080
    img, d = canvas(1080, 1080)
    y = title(d, 540, 90, 118, anchor="ma")
    bar(d, 220, y + 16, 640, 16)
    champions(img, d, 126, y + 80, 13, 20)
    f = font(40)
    wd = int(d.textlength(label, font=f)) + 40
    badge(d, 540 - wd // 2, 890, 40)
    d.text((540, 1000), tagline, font=font(26), fill=(238, 234, 255), anchor="ma")
    img.convert("RGB").save(os.path.join(out, "square-1080.png"))

    # 3. wide key art 1920x1080
    img, d = canvas(1920, 1080)
    y = title(d, 110, 130, 170)
    bar(d, 110, y + 30, 900, 22)
    badge(d, 110, y + 96, 54)
    d.text((110, 800), tagline, font=font(40), fill=(238, 234, 255))
    d.text((110, 866), sub, font=font(26), fill=(170, 163, 214))
    champions(img, d, 1130, 150, 13, 0)
    weapons_row(img, 110, 950, 5, order[:15] + [order[15], order[30]])
    img.convert("RGB").save(os.path.join(out, "keyart-1920x1080.png"))

    # 4. one "climb" card per tower: the fifteen gods standing on a staircase, floor 1 at the bottom left
    for line, gods in LINES.items():
        col, sh = PROMO_COLS[line]
        tower, tiers = TIERS[line]
        img, d = canvas(1600, 900)
        d.text((70, 60), tower.upper(), font=font(84), fill=(0, 0, 0))
        d.text((64, 54), tower.upper(), font=font(84), fill=col)
        d.text((66, 164), "15 floors  -  15 gods  -  one climb", font=font(28), fill=(238, 234, 255))
        badge(d, 1536, 64, 34, anchor_right=True)
        k, step_w, step_h = 5, 96, 26
        base_y = 820
        for floor in range(1, 16):
            g = gods[15 - floor]
            x = 64 + (floor - 1) * step_w
            top = base_y - floor * step_h
            d.rectangle([x, top + 12, x + step_w - 6, base_y + 12], fill=sh)
            d.rectangle([x, top, x + step_w - 6, base_y], fill=col if floor % 5 else (255, 255, 255))
            d.rectangle([x, top + 14, x + step_w - 6, base_y], fill=PROMO_PANEL)
            sprite(img, g[0], x + 5, top - 32 * k, k)
            d.text((x + (step_w - 6) // 2, top + 22, ), str(floor), font=font(18), fill=(170, 163, 214), anchor="ma")
        for t, (name, f0) in enumerate(zip(tiers, (1, 6, 11, 15))):
            x = 64 + (f0 - 1) * step_w
            if t == 3:      # the summit label hangs from the right edge of the last step
                d.text((x + step_w - 6, base_y + 30), name, font=font(17), fill=(255, 255, 255), anchor="ra")
            else:
                d.text((x, base_y + 30), name, font=font(17), fill=col)
        img.convert("RGB").save(os.path.join(out, f"tower-{line.lower()}-1600x900.png"))

    wj(os.path.join(out, "promo.json"), {"label": label, "version": raw, "channel": channel, "tagline": tagline})

# ----------------------------------------------------------------------------
if __name__ == "__main__":
    gen_java()
    gen_build_info()
    gen_lang()
    gen_models()
    first = gen_weapons()
    gen_flat_items()
    gen_block_textures()
    previews = gen_skins()
    gen_pack()
    gen_data()
    gen_advancements()
    gen_icon(previews)
    gen_site(first, previews)
    gen_promo(first, {k: v for k, v in previews.items()})
    print("generated", sum(len(v) for v in LINES.values()), "gods")
