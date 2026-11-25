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
import hud_art
import panoply_art
import divine_art
import divine_pro
import divine_models
import armor_art
import particle_art
import music
import station_ui
import orb_ranks
import discs
import furniture_art
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
# Roster: (id, display name, title, weapon name, shape, power, target effect, self effect, weapon kind)
# kinds: BLADE DAGGER POLEARM HAMMER SCYTHE STAFF BOW THROWN RELIC (the last four fight at range)
# effects are "effect_id:seconds:amplifier" or "" ; powers: NONE LIGHTNING FIRE KNOCKBACK DRAIN FREEZE SWEEP
# Ranks run 1 (strongest, the final boss of the line) .. 15 (weakest, the starter boss)
# ----------------------------------------------------------------------------
LINES = {
    "ZEUS": [
        ("zeus", "Zeus", "King of the Gods", "Keraunos, the Thunderbolt", "bolt", "LIGHTNING", "glowing:6:0", "resistance:6:0", "THROWN"),
        ("athena", "Athena", "Goddess of Wisdom and War", "Spear of the Grey-Eyed", "spear", "KNOCKBACK", "weakness:6:1", "resistance:6:1", "POLEARM"),
        ("apollo", "Apollo", "God of the Sun and Prophecy", "Bow of the Sun", "bow", "FIRE", "glowing:8:0", "speed:6:0", "BOW"),
        ("dionysus", "Dionysus", "God of Wine and Madness", "Thyrsus of Madness", "staff", "NONE", "nausea:8:0", "regeneration:5:0", "STAFF"),
        ("artemis", "Artemis", "Goddess of the Hunt and Moon", "Moonlit Bow", "bow", "NONE", "slowness:5:1", "speed:6:1", "BOW"),
        ("ares", "Ares", "God of War", "Warblade of Ares", "greatsword", "KNOCKBACK", "", "strength:6:1", "BLADE"),
        ("hermes", "Hermes", "Messenger of the Gods", "Winged Caduceus", "staff", "NONE", "slowness:3:0", "speed:8:2", "STAFF"),
        ("asclepius", "Asclepius", "God of Medicine", "Rod of Asclepius", "staff", "DRAIN", "", "regeneration:6:1", "STAFF"),
        ("hebe", "Hebe", "Goddess of Youth", "Chalice of Ambrosia", "chalice", "NONE", "weakness:5:0", "regeneration:6:0", "RELIC"),
        ("phobos", "Phobos", "God of Fear", "Terror Fang", "dagger", "NONE", "darkness:6:0", "speed:5:0", "DAGGER"),
        ("deimos", "Deimos", "God of Dread", "Dread Reaper", "scythe", "NONE", "blindness:3:0", "speed:5:0", "SCYTHE"),
        ("hygeia", "Hygeia", "Goddess of Health", "Bowl of Hygeia", "chalice", "DRAIN", "", "absorption:8:0", "RELIC"),
        ("pan", "Pan", "God of Wild Nature", "Pan's Syrinx", "flute", "NONE", "poison:4:0", "speed:6:0", "RELIC"),
        ("nike", "Nike", "Goddess of Victory", "Victory Gladius", "gladius", "NONE", "", "strength:6:0", "BLADE"),
        ("iris", "Iris", "Divine Messenger of the Rainbow", "Rainbow Rapier", "rapier", "NONE", "glowing:10:0", "speed:6:0", "BLADE"),
    ],
    "POSEIDON": [
        ("poseidon", "Poseidon", "Lord of the Seas", "Trident of the Deep", "trident", "SWEEP", "slowness:5:1", "resistance:6:0", "POLEARM"),
        ("amphitrite", "Amphitrite", "Queen of the Sea", "Queen's Coral Scepter", "staff", "FREEZE", "", "regeneration:6:0", "STAFF"),
        ("triton", "Triton", "Herald of the Sea", "Triton's Conch", "conch", "KNOCKBACK", "", "water_breathing:20:0", "RELIC"),
        ("chrysaor", "Chrysaor", "Golden Blade of the Sea", "Golden Sword of Chrysaor", "sword", "SWEEP", "", "strength:6:0", "BLADE"),
        ("cymopoleia", "Cymopoleia", "Goddess of Storm Waves", "Stormwave Maul", "hammer", "KNOCKBACK", "slowness:4:1", "resistance:6:0", "HAMMER"),
        ("rhode", "Rhode", "Nymph of Rhodes", "Roserain Bow", "bow", "NONE", "nausea:6:0", "speed:6:0", "BOW"),
        ("pegasus", "Pegasus", "The Winged Horse", "Wingbeat Plume", "feather", "NONE", "levitation:2:0", "jump_boost:8:2", "RELIC"),
        ("benthesikyme", "Benthesikyme", "Goddess of the Deep Waves", "Deepcurrent Dirk", "dagger", "DRAIN", "", "water_breathing:20:0", "DAGGER"),
        ("halia", "Halia", "Sea Nymph of the Salt", "Seafoam Cutlass", "cutlass", "FREEZE", "", "regeneration:5:0", "BLADE"),
        ("nereus", "Nereus", "Old Man of the Sea", "Elder's Tide Staff", "staff", "NONE", "weakness:6:0", "resistance:6:0", "STAFF"),
        ("proteus", "Proteus", "The Shapeshifter", "Shapeshifter's Kris", "dagger", "NONE", "nausea:6:0", "speed:6:0", "DAGGER"),
        ("palaemon", "Palaemon", "God of Harbours", "Harbour Anchor", "anchor", "NONE", "slowness:4:0", "jump_boost:6:1", "HAMMER"),
        ("thetis", "Thetis", "Silver-Footed Sea Nymph", "Silverfoot Javelin", "spear", "FREEZE", "", "absorption:8:0", "THROWN"),
        ("galene", "Galene", "Goddess of Calm Seas", "Calmwater Pearl", "orb", "NONE", "slowness:3:0", "regeneration:5:0", "RELIC"),
        ("galatea", "Galatea", "Sea Nymph of the Pearl Shore", "Pearl Sea Dagger", "dagger", "FREEZE", "", "speed:5:0", "DAGGER"),
    ],
    "HADES": [
        ("hades", "Hades", "Lord of the Underworld", "Bident of the Dead", "trident", "DRAIN", "wither:5:1", "resistance:6:0", "POLEARM"),
        ("persephone", "Persephone", "Queen of the Underworld", "Pomegranate Scythe", "scythe", "NONE", "poison:5:0", "regeneration:6:0", "SCYTHE"),
        ("zagreus", "Zagreus", "The Hunter of the Dead", "Stygian Blade", "sword", "FIRE", "", "strength:6:0", "BLADE"),
        ("thanatos", "Thanatos", "Death Incarnate", "Reaper's Sickle", "sickle", "DRAIN", "wither:4:2", "", "SCYTHE"),
        ("hypnos", "Hypnos", "God of Sleep", "Slumber Staff", "staff", "NONE", "slowness:5:2", "", "STAFF"),
        ("melinoe", "Melinoe", "Goddess of Ghosts", "Witchlight Lantern", "lantern", "NONE", "darkness:6:0", "speed:6:0", "RELIC"),
        ("macaria", "Macaria", "Goddess of Blessed Death", "Mercy's Edge", "dagger", "DRAIN", "", "regeneration:5:0", "DAGGER"),
        ("hecate", "Hecate", "Goddess of Witchcraft", "Torch of the Crossroads", "torch", "FIRE", "blindness:3:0", "speed:6:0", "STAFF"),
        ("morpheus", "Morpheus", "God of Dreams", "Dreamshaper Wand", "staff", "NONE", "nausea:6:0", "speed:6:0", "STAFF"),
        ("nyx", "Nyx", "Primordial Goddess of Night", "Midnight Shards", "star", "NONE", "darkness:10:0", "resistance:6:0", "THROWN"),
        ("erinyes", "The Erinyes", "The Furies of Vengeance", "Furies' Lash", "whip", "FIRE", "weakness:6:0", "", "POLEARM"),
        ("moirai", "The Moirai", "The Three Fates", "Shears of Fate", "shears", "NONE", "mining_fatigue:8:1", "", "DAGGER"),
        ("keres", "The Keres", "Spirits of Violent Death", "Carrion Talon", "talon", "DRAIN", "poison:4:0", "", "SCYTHE"),
        ("oneiroi", "The Oneiroi", "Spirits of Dreams", "Nightmare Reed", "staff", "NONE", "nausea:7:0", "speed:5:0", "STAFF"),
        ("charon", "Charon", "Ferryman of the Dead", "Ferryman's Oar", "oar", "KNOCKBACK", "slowness:4:0", "resistance:6:0", "HAMMER"),
    ],
}

# Every god's signature move: its own name, and which of the fourteen kinds of move it is.
# The god casts it in a fight; the god's relic weapon casts the same move for the player.
SIGNATURES = {
    "zeus": ("Wrath of Olympus", "STORM"), "athena": ("Aegis", "WARD"), "apollo": ("Sunburst Volley", "VOLLEY"),
    "dionysus": ("Bacchic Revel", "SLUMBER"), "artemis": ("Hunter's Moon", "VOLLEY"), "ares": ("Warpath", "CHARGE"),
    "hermes": ("Windstep", "BLINK"), "asclepius": ("Panacea", "MEND"), "hebe": ("Cup of Youth", "MEND"),
    "phobos": ("Terror Strike", "BLINK"), "deimos": ("Dread Harvest", "REAP"), "hygeia": ("Cleansing Ward", "WARD"),
    "pan": ("Panic", "NOVA"), "nike": ("Victory Rush", "CHARGE"), "iris": ("Prism Gust", "GALE"),
    "poseidon": ("Earthshaker", "ERUPTION"), "amphitrite": ("Queen's Tide", "FROST"), "triton": ("Call of the Deep", "VORTEX"),
    "chrysaor": ("Golden Rush", "CHARGE"), "cymopoleia": ("Stormwave", "NOVA"), "rhode": ("Rain of Roses", "VOLLEY"),
    "pegasus": ("Skyward Gale", "GALE"), "benthesikyme": ("Undertow", "VORTEX"), "halia": ("Seafoam Chill", "FROST"),
    "nereus": ("Old Man of the Sea", "WARD"), "proteus": ("Shapeshift", "BLINK"), "palaemon": ("Anchor Drop", "NOVA"),
    "thetis": ("Silver Rain", "VOLLEY"), "galene": ("Stillwater", "MEND"), "galatea": ("Pearl Frost", "FROST"),
    "hades": ("Gates of Tartarus", "ERUPTION"), "persephone": ("Winter's Harvest", "REAP"), "zagreus": ("Stygian Dash", "CHARGE"),
    "thanatos": ("Final Harvest", "REAP"), "hypnos": ("Lullaby", "SLUMBER"), "melinoe": ("Ghostlight", "CURSE"),
    "macaria": ("Blessed End", "BLINK"), "hecate": ("Crossroads Hex", "CURSE"), "morpheus": ("Waking Dream", "SLUMBER"),
    "nyx": ("Starfall", "VOLLEY"), "erinyes": ("Vengeance", "CURSE"), "moirai": ("Severed Thread", "CURSE"),
    "keres": ("Carrion Feast", "REAP"), "oneiroi": ("Nightmare", "SLUMBER"), "charon": ("Ferryman's Toll", "NOVA"),
}
MOVE_KINDS = {
    "STORM": "Lightning strikes every foe around you",
    "CHARGE": "Dash forward, cutting through everything in the way",
    "VOLLEY": "Looses a fan of five shots",
    "VORTEX": "Drags foes in, then bursts",
    "NOVA": "A shockwave that hurls every foe away",
    "BLINK": "Step behind your foe and strike hard",
    "MEND": "Heals you, cures what ails you, and weakens foes",
    "SLUMBER": "Dazes foes: they slow, weaken and lose their way",
    "REAP": "A wide arc that heals you for every foe it cuts",
    "CURSE": "A hex that withers foes, then bursts",
    "FROST": "A ring of frost that freezes and slows",
    "GALE": "A gust that throws foes into the air",
    "WARD": "A shield for you, and foes are pushed back",
    "ERUPTION": "The ground erupts under every foe a moment later",
}

# The story, chapter by chapter: (id, title, one-line blurb for menus, pages). Every chapter is a book that can be read
# in game; the Oracle in the hub and the Codex list the ones a player has earned. A page fits a written book page.
STORY = [
    ("prologue", "The Oracle's Letter", "A letter that found you. It asks for help.", [
        "If you are reading this, the last of my ravens found you.\n\nI am the Oracle. Once of Delphi. Now of nowhere you could walk to.",
        "Chaos has woken: the first thing that ever was, older than any god. It does not hate the world. It is simply taking it back, one story at a time.",
        "The gods of the sky, the sea and the dead have shut themselves in three towers to hold it off.\n\nThey are losing.",
        "The gates to their realm were broken on purpose, so no mortal would wander in.\n\nI am asking you to wander in.",
        "Take the compass. It remembers where a gate still stands.\n\nThe gate will want three keystones. You will find seekers there that know the way to each.\n\nHurry.",
    ]),
    ("sundering", "The Sundering", "How the gods hid, and what it cost them.", [
        "When Chaos rose, Zeus did the one thing a king hates.\n\nHe hid.",
        "The three brothers raised towers in a realm between realms and locked every god of their houses inside: the least at the foot, themselves at the crown.\n\nA wall of gods between Chaos and the world.",
        "Then they broke the gates and gave the keystones away: one to the sky, one to the tides, one to the dark. Each to keep what it was owed.",
        "But a wall that stands in Chaos does not stay a wall.\n\nThe gods you will meet no longer know what they are guarding. They only know they must not be passed.",
        "Set the three keystones in the arch above the pedestal. The gate will wake.\n\nWhat is on the other side has been waiting a long time for someone stubborn.",
    ]),
    ("sky", "Keystone of the Sky", "Where Zeus set his keystone, and what waits in his tower.", [
        "Zeus set his keystone where eagles could watch it.\n\nYou are holding it now, which says something about the eagles.",
        "His tower is the brightest and the proudest. Messengers at its foot. Fear and dread on the stair. War and wisdom near the crown.",
        "They will greet you as a thief. Do not argue.\n\nA god beaten in fair trial remembers itself, for a while. That is the whole of the plan.",
    ]),
    ("sea", "Keystone of the Tides", "Where Poseidon left his keystone, and what waits in his tower.", [
        "Poseidon gave his keystone to the water, and the water kept it the way water keeps everything: carelessly, and for ever.",
        "His tower goes down, whatever your feet tell you. Sunlit shallows, then drowned halls, then the trench, where no light was ever asked for.",
        "The sea does not hate you.\n\nIt simply does not stop. Learn its rhythm and strike between the waves.",
    ]),
    ("shade", "Keystone of the Shades", "Where Hades buried his keystone, and what waits in his tower.", [
        "Hades buried his keystone, as he buries everything, and marked the place so that it could be found by anyone willing to go down.",
        "His tower is the quietest. The ferryman at its door will take your measure. The dreams above him will lie to you kindly.",
        "At the top sits a king who never wanted a throne, holding the one deck of cards that Chaos is afraid of.\n\nAsk him for it. Loudly.",
    ]),
    ("hub", "The Hub of Realms", "The Oracle speaks: where you are, and what the trials are for.", [
        "You stand where three realms touch.\n\nNorth, Olympus. East, the Abyss. West, the Underworld. South, the way home, which I advise you to remember.",
        "The flame you are speaking to is me. I burn here so the towers have something to point at.\n\nThe figures by the gates are echoes. They still turn their heads. Be polite.",
        "Understand what a trial is. You do not slay a god. You wake one.\n\nEach relic is the thanks of a god who has remembered its own name.",
        "Between the floors lie passages: what Chaos made of the stairs. Break its seals and the way opens.\n\nClimb. I will write down what you find.",
    ]),
    ("zeus_foot", "Olympus: The Foothills", "The first gods of the sky tower.", [
        "Iris ran messages between gods who stopped listening. She still runs. Nobody told her the letters are blank.",
        "The foothills are green because Chaos has not finished with them. Enjoy the moss. Higher up it is marble, and marble does not forgive.",
    ]),
    ("zeus_mid", "Olympus: The Marble Halls", "Five gods of the sky are awake.", [
        "Five awake. Hebe poured you a cup, I hope. She has been pouring for nobody for a very long time.",
        "Fear and Dread guard the same stair as Medicine. That was never the order of things. Chaos shuffles gods the way a bored hand shuffles cards.",
        "Ares is above you. He was not locked in. He volunteered. Keep that in mind when he smiles.",
    ]),
    ("zeus_high", "Olympus: The Cloud Sanctum", "Ten gods of the sky are awake.", [
        "Ten awake, and the clouds are beginning to move again. From down here it looks like weather. It is the sky remembering how.",
        "Artemis will not miss. Apollo will not stop talking. Athena has already worked out how this ends and is checking her sums by fighting you.",
        "Then the summit. He will not be glad to see you. He will be relieved, and hate that.",
    ]),
    ("zeus_fall", "Zeus Speaks", "The king of the gods, awake.", [
        "\"I built a wall out of my own children and called it wisdom.\"",
        "\"Every one of them you woke, I felt. Like stones coming out of a dam. I thought the flood would be Chaos.\n\nIt was only the truth, which is worse and more useful.\"",
        "\"Take the thunderbolt. I threw it at everything except the one thing that mattered.\n\nYou will aim better. You have had practice. On us.\"",
    ]),
    ("poseidon_foot", "The Abyss: Sunlit Shallows", "The first gods of the sea tower.", [
        "Galatea was a statue once, then loved into life. Chaos is trying to remind her of the statue.\n\nRemind her of the other part.",
        "The shallows are kind. That is how the sea begins every conversation.",
    ]),
    ("poseidon_mid", "The Abyss: Drowned Halls", "Five gods of the sea are awake.", [
        "Five awake, and the tide in the hub has started keeping time.\n\nProteus will be whoever you least want to fight. Do not take it personally. He does it to everyone.",
        "Old Nereus knows every answer and gives none of them willingly. He is the only god in that tower who is hiding on purpose.",
        "Below the halls the light gives up. Bring your own.",
    ]),
    ("poseidon_high", "The Abyss: The Trench", "Ten gods of the sea are awake.", [
        "Ten awake. The canals here run clear now. You did that.",
        "Chrysaor was born with a golden sword in his hand and has never once put it down. Triton's horn can be heard through stone. The Queen is patient. She married the sea.",
        "And then him. Earthshaker. Mind your footing.",
    ]),
    ("poseidon_fall", "Poseidon Speaks", "The lord of the seas, awake.", [
        "\"My brother built a wall. I built a depth. I thought nothing could follow us down there.\"",
        "\"Something did. It was patient, the way I am patient. I mistook it for myself. For an age I sat in the dark, proud of how still the water was.\"",
        "\"Take the trident. It was made to stir things.\n\nGo and stir my brother in the west. He has been still the longest.\"",
    ]),
    ("hades_foot", "Underworld: Fields of Asphodel", "The first gods of the tower of the dead.", [
        "Charon wants a coin. He has always wanted a coin. Pay him the other way.",
        "The fields are grey because the dead forget colour first. The dreams that drift here are the Oneiroi. They mean no harm. They do it anyway.",
    ]),
    ("hades_mid", "Underworld: Tartarus", "Five gods of the dead are awake.", [
        "Five awake. The Fates measured your thread while you fought them. I looked over their shoulders. It is longer than they expected.",
        "The Furies punish oath-breakers. Chaos convinced them everything is a broken oath. In a way it is right, which is why they are so hard to wake.",
        "Night herself is above you. Nyx is older than the towers, older than me. Chaos is her mother. She has not decided whose side she is on.",
    ]),
    ("hades_high", "Underworld: Halls of the Dead", "Ten gods of the dead are awake.", [
        "Ten awake. Sleep and Death are brothers, and you have met one. The other is coming, and for once he is the one who is afraid.",
        "Zagreus fights like someone who has died often and resents each time. Persephone keeps the seasons of a place that has none.",
        "And the king. He kept the dead in order when order was unfashionable. He will make you earn every step.",
    ]),
    ("hades_fall", "Hades Speaks", "The king of the dead, awake.", [
        "\"They gave me the dark because nobody else wanted it. I kept it well. Chaos came, and I thought: here at last is something darker. I can rest.\"",
        "\"That was the lie it told me. Not that I was weak. That I was finished.\"",
        "\"Take the deck. Every card in it is a story Chaos could not eat. I have been dealing them into the dark for an age, hoping somebody would pick one up.\n\nSomebody did.\"",
    ]),
    ("epilogue", "What Comes After", "Three kings awake. The Oracle looks further.", [
        "Three kings awake. Forty-five gods who know their names.\n\nChaos is not beaten. It cannot be. It is what everything is made of.",
        "But it has been told no, by a mortal, forty-five times. It is not used to that. It has drawn back from these towers like a tide.",
        "It has not gone far. I can feel other gates, in other places it is busy unmaking.\n\nA bridge of light that is not Iris's. A river lined with reeds.",
        "And a mountain where a blade is folded a thousand times before anyone dares to call it finished.\n\nRest. Sharpen what they gave you. Then come back to the flame.",
    ]),
]

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
    # --- the fallen Titans, who hold the citadels
    "hyperion":  ("armor", (232, 186, 70), (255, 244, 200), (255, 226, 130), (246, 214, 160), "crown", (255, 236, 150), True, True),
    "oceanus":   ("robe", (34, 98, 120), (120, 222, 210), (200, 226, 228), SEA, "crown", (120, 222, 210), True, True),
    "cronus":    ("robe", (38, 34, 46), (168, 132, 64), (196, 196, 204), PALE, "hood", (38, 34, 46), True, False),
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

# The Climber's Handbook: the tutorial, topic by topic. (id, title, lines). Shown as a custom screen; opened once for a
# new player and always from the Codex.
HANDBOOK = [
    ("start", "Getting started", [
        "Welcome to Realms of Chaos, and thank you for using our mod.",
        "You have been given three things: the Codex of Chaos (this menu), the Compass of Chaos, and a letter from the Oracle. Read the letter - it is the start of the story.",
        "The realms of the gods are closed until you find a way in. Right-click the Compass of Chaos: it scans for the nearest Ruined Gate. You also carry a Field Tower Bag with two Relay Tower Cores and a Waygate Obelisk in it.",
        "This handbook is always here: open the Codex and choose Handbook.",
    ]),
    ("way_in", "Finding the way in", [
        "A Ruined Gate is a broken stone plaza with a dead arch and a campfire still smoking. You will see the smoke first.",
        "Its chest holds three seekers. Each is a compass for one keystone: right-click it and it tells you how far and which way its citadel is. The citadels lie far apart - a long journey each.",
        "Each keystone is kept in a citadel - white and gold for the Sky, prismarine behind a moat for the Tides, black stone ringed with soul fire for the Shades. Half-kin hold the walls and towers; on the roof of the keep, beside the keystone, waits a fallen Titan. Climb the keep's stairs to reach it. Go armed.",
        "Bring the three keystones back and right-click the pedestal with each. The arch fills. Step through, and the gate knows you. Nothing carries you to the hub in an instant: you walk through a gate, or you channel at a Waygate Obelisk.",
    ]),
    ("compass", "The compass scans", [
        "The Compass of Chaos does not carry you anywhere. Right-click and it scans for a minute to a minute and a half. Keep it in hand and read the line above your hotbar: an arrow (up is straight ahead), a distance, the signal, the time left.",
        "Every two or three seconds the needle rolls. Most rolls are rough - off by more the poorer the signal. A Clean Lock (green, with a trail of light) is exact. A Static Surge (red) is nonsense. The longer a scan runs, the truer it gets, and its best reading is written to chat at the end.",
        "Sneak and right-click turns the compass to what it looks for: the way to the hub, the keystone shrines, or relays and waygates.",
        "Signal: Relay Towers within 96 blocks steady it (up to three). A higher hub tier steadies it. Rain, thunder and being deep underground spoil it, and so does another player's Signal Jammer.",
    ]),
    ("network", "Relays, jammers, waygates", [
        "Your Field Tower Bag starts with two Relay Tower Cores and a Waygate Obelisk. Right-click opens the bag; sneak and right-click a block sets down the first of them without opening it. All three can be crafted, broken and carried.",
        "A Relay Tower Core steadies every compass within 96 blocks. A Signal Jammer spoils the scans of everyone within 64 blocks except its owner and their party.",
        "A Waygate Obelisk is a place to travel from and to. Right-click it and choose: the Hub of Realms (once a Ruined Gate knows you) or any other obelisk you have touched. Then stand still for five seconds - move or be hit and it lets go. Each journey costs one ender pearl.",
    ]),
    ("traps", "Rune chambers", [
        "Some rooms of a passage are rune chambers: a rune stone on a pillar in each corner and a marked square in the middle. Step onto the square and the trap springs - the doorways close and water rises a layer at a time.",
        "The runes light up in an order, again and again. Right-click them in that order and the room drains, opens, and gives each of you forge material (once). A wrong rune starts the order again and the water leaps up a layer.",
        "It is a trap, not a grave: once full it hurts whoever is inside for a quarter of a minute, then lets go by itself, with no reward. It never springs for someone who has already solved it.",
    ]),
    ("rooms", "Levels, puzzles and traps", [
        "A passage has anywhere from one to ten levels - more the higher in a tower - and each is named: 7-B2 is passage 7, wing B, level 2. Wings are three levels long and each is built of different stone.",
        "You are handed a Passage Chart: a map that starts blank and inks in each room as you walk into it. The next level is built while you explore the one you are on.",
        "Puzzle rooms keep a cache locked. A vault: press the glyph tiles until all nine are lit (each turns its neighbours too). A chime room: ring all four crystals before the first falls silent. A beam room: set the mirrors so the light reaches the receiver - some are decoys, and turns are counted. A trembling floor: only one path of plates holds - it glimmers every few seconds, and has to be walked from its first plate.",
        "A rune chamber shuts you in: press its runes in order to get out. A flood shows the order over and over; a furnace shows it once; the gloom wants it backwards. Trap halls are only to be got through. A crusher hall marks a row, and a breath later brings the ceiling down on it. A dart hall has marked tiles that loose arrows from the walls. Most trap halls do not show what they are: look for stained tiles and old bones, watch for a wisp over a tile before it goes off, and wait just inside the doorway, where it is always safe.",
        "Urns can be smashed for a little loot - or for the sentinel hiding inside. Lore tablets tell a line of the tower's story.",
    ]),
    ("mastery", "Mastery", [
        "You earn a point for every god you wake and every passage you walk for the first time. Spend them in the Codex under Mastery, or with /roc skills.",
        "Combat: Might, Swiftness, Focus (relic moves return sooner), Reach. Survival: Vigor, Guard, Steadiness, Second Wind. Exploration: Cartographer, Signal, Scavenger, Pathfinder.",
        "Every skill has three ranks. Nothing is ever lost: the tree can be taken apart and the points spent again.",
    ]),
    ("hub_tiers", "The hub grows", [
        "The Heart of the Hub stands south of the middle of the plaza. Right-click it for Hub Command: the hub's tier, what each tier does, and what the next one asks for. Everyone on the world shares one hub and anyone can add to the pile.",
        "Tier II: steadier compasses, a hub that mends you, and the Bounty Board. Tier III: six-row Field Bags, and the hub lifts every harm. Tier IV: three-second waygates, and the needle rolls every two seconds. Tier V: free waygates.",
        "The Bounty Board posts three bounties a day, the same for everyone. Each player earns them for themselves and claims forge materials at the board.",
    ]),
    ("phases", "A trial's three phases", [
        "A god fights as itself down to 65% of its health. No single blow carries it past that mark, or the next.",
        "At 65% it raises its aegis: nothing can hurt it while its shield pylons stand. Find them round the floor and right-click each until it breaks. Left alone, an aegis fades after 75 seconds.",
        "At 30% comes its wrath: a last stand, the floor lost ring by ring from the wall inward, and strikes from above. Each strike is marked by a ring on the floor for a second and a half first.",
        "A god watches how it is fought. Shoot it and it turns shots aside and closes in; stay hand to hand and its guard lashes back. It also measures your best weapon, its enchantments and your armour when the trial begins, and rises to match. The world's difficulty sets its health and pace.",
    ]),
    ("hub", "The Hub of Realms", [
        "Three gates lead to three towers: Zeus to the north, Poseidon to the east, Hades to the west. The south gate takes you home.",
        "The Oracle's Flame burns just north of where you arrive. Right-click it for the story so far and for what to do next.",
        "Two Forges of Chaos stand south of the middle. The figures beside the gates are echoes of the gods. They only watch.",
        "Nothing in the realms can be broken or built on. You get your tools back the moment you leave.",
    ]),
    ("trials", "Towers and trials", [
        "Each tower has fifteen floors and one god to a floor. Floor 1 is the gentlest; the king waits on floor 15.",
        "Right-click the Trial Altar on a floor to see who waits there, what their move is and what they drop, and to begin the trial.",
        "Beat the god and the Gate of Ascent opens at the far side of the floor. It leads into the passage to the next floor.",
        "A god you have beaten can be called back from its altar for a rematch. Rematches drop forge materials.",
    ]),
    ("fights", "Reading a fight", [
        "Tap sneak, or raise a shield, just as a blow would land and it is parried: no harm done, the attacker reels, and your next blow within three seconds is a riposte, half as hard again. A parried bolt goes back to whoever loosed it. Sentinels draw back before a heavy blow - that is the one to parry.",
        "Hold a direction and tap sneak and you dodge instead: a quick dash that way, and for a moment no blow can find you. A blow slipped like that leaves its striker open to a riposte, just as a parry does. A dodge needs a breath before the next.",
        "Blows landed without being hit build momentum, up to five, and each point adds a little to every blow. A blow on a reeling enemy counts half as much again, and one struck while falling shakes everything round it.",
        "Every god has a signature move with a name, and raises its arms before it: that is your warning. Turn on the cast bar in the Codex's settings and each move is named and counted down as it comes.",
        "The gods remember how you fight. Shoot from afar, hide behind a shield, stop to heal or keep out of reach in trial after trial, and the next god has an answer ready. The Codex shows what they know of you; fight differently and they forget.",
        "Rings on the ground mean it is about to erupt there. Step out. A hex bursts two seconds after it lands unless you get well away.",
        "At half health a god is enraged: it glows, moves faster, and the floor starts to throw itself up under your feet. Keep moving when you see dust.",
        "At a fifth of its health it makes a last stand: for two seconds nothing can hurt it, sentinels answer its call, and then it uses everything it has.",
        "The floor itself changes shape as the fight goes on. If a god beats you, it comes back a little weaker next time.",
    ]),
    ("passages", "Passages", [
        "Between every two floors lies a passage: one to ten levels, each a maze of rooms. No two worlds have the same ones, and no two rooms are quite alike: low or tall, wide, ringed with columns, sunk a step into the floor.",
        "Some halls never rest. Spikes come up by the colour of the tile, fire walks a flame hall row by row, and a blade sweeps a blade hall from wall to wall. Each shows what it is about to do: watch, then go. A stairwell has a loft, and a cache on it.",
        "The gate at the end of a level is sealed. Find every seal on that level and break it. The bar at the top counts them.",
        "A sealed chamber wakes its sentinels when you walk in. When they have fallen, right-click the seal and strike it: click when the hammer is on the yellow mark.",
        "The Codex opens a map of the level you are on. The Compass of Chaos points to the nearest seal, then to the gate.",
        "Whoever breaks a seal is offered three boons and takes one; they last until the passage is behind you. A shrine shows the whole of its level on your chart.",
        "Treasure caches give every player their own share. Shrines heal you. Arenas hold an elite, and a better cache. Each level up is harder than the last.",
    ]),
    ("sentinels", "Sentinels", [
        "Soldiers close in. Casters keep their distance and shoot. Skirmishers are quick and leap at you. Brutes are slow and send you flying.",
        "Mystics hang back and help the rest: the Storm Augur quickens them, the Tide Priest heals them, the Necromancer raises more. Deal with the mystic first.",
        "A Keeper guards every seal and now and then makes a small version of one of its tower's moves. Elites glow, take half as much again to bring down, and always drop forge material.",
        "Each tower has twenty-one kinds, and most have one trick of their own. They cover for each other: a shield-bearer turns blows from its front and shelters those beside it, a warden takes half of what lands on its allies, a standard-bearer strengthens them, a dancer trades places with a wounded one, a singer stands a fallen one back up.",
        "Others are there to make trouble: bombers that run at you and burst, binders that root or drag you, sowers whose bolts leave ground that hurts, snipers that aim along a line of light, thieves that knock your weapon into your pack, silencers that stop your relic's move, mirages that split into three, and heralds that wake the rooms next door. Kill the helper first.",
    ]),
    ("relics", "Relics and their moves", [
        "Every god drops its relic the first time you beat it. Each relic has a kind - blade, dagger, polearm, heavy, scythe, staff, bow, thrown or relic - and a power on every hit.",
        "A relic casts its god's signature move. Right-click with a melee relic; sneak and right-click with a ranged one. It recharges in twelve seconds.",
        "Every fourth hit in a row is a flourish for half as much damage again. Relics gain experience from kills and level up to 10.",
    ]),
    ("forge", "The Forge of Chaos", [
        "Hold a relic and right-click a Forge of Chaos. Stars cost forge materials from the relic's own tower, and each needs the relic to be a high enough level.",
        "Forging is struck, not clicked: three strikes, timed on the bar. No misses and two perfect strikes is a masterwork, which tempers the star for extra damage.",
        "Three stars empower the relic's move. Five make it echo. You can pour your own experience levels into a relic with Infuse.",
    ]),
    ("party", "Playing together", [
        "Form a party of up to four from the Codex. A party enters a tower on the floor of its highest member.",
        "Your progress is your own: you go up past a floor only when you have beaten its god yourself - with help or without - and walked its passage.",
        "Everyone who fights gets their own loot. Trials are harder with more of you.",
    ]),
    ("cards", "Tarot and dolls", [
        "The gods of the tower of Hades drop tarot cards. Right-click to play one; each does something different. Hades himself drops the Deck of Fate, which never runs out.",
        "Voodoo dolls are stitched from wheat and string round a pin. Bind one to a creature and stabbing the doll hurts the creature from afar.",
    ]),
    ("commands", "Commands", [
        "/roc - this menu.  /roc hub - back to the hub from inside the realms.  /roc leave - go home.  /roc forge - the forge screen.  /roc scan - begin a compass scan.  /roc sidebar - the side panel on or off.  /roc skills - the mastery tree.  /roc chart - a new Passage Chart.",
        "/roc party invite <player>, /roc party accept, /roc party leave.",
    ]),
]

# One-time tips: shown on screen the first time each thing happens to a player.
TIPS = [
    ("ruin", "A Ruined Gate. Look in its chest: the seekers lead to the keystones it needs"),
    ("hub", "The Oracle's Flame is just north. Right-click it. Each gate leads to a tower"),
    ("floor", "Right-click the Trial Altar to see who waits here and to begin"),
    ("trial", "A god raises its arms before a heavy move. Settings in the Codex can name each move as it comes"),
    ("victory", "Pick up your relic. The Gate of Ascent at the far side is open"),
    ("passage", "Break every seal on this level. The Codex is a map; the compass points the way"),
    ("seal", "Clear the sentinels, then right-click the seal and strike on the yellow mark"),
    ("forge", "Stars cost materials from the relic's own tower. Strike on the yellow mark"),
    ("upheaval", "Dust under your feet means the floor is about to erupt. Move"),
    ("aegis", "Shield pylons stand round the floor. Right-click each until it breaks"),
    ("wrath", "Rings on the floor mark where a strike lands. The edge of the floor is lost: stay in"),
    ("scan", "Keep the compass in hand and read above your hotbar. Sneak-click changes its target"),
    ("waygate", "Choose where to go, then stand still while the obelisk gathers"),
    ("insight", "The gods remember how you fight. The Codex shows what they know. Fight differently and they forget"),
    ("parry", "A heavy blow is coming. Tap sneak as it lands to parry, or hold a direction and tap sneak to dodge"),
    ("boon", "A broken seal offers three boons. Take one: it lasts until you leave this passage"),
]

SENTINELS = [
    ("storm_hoplite", "Storm Hoplite", "ZEUS", ("armor", BRONZE, GOLD, (60, 44, 30), TAN, "helmet", GOLD, False, False)),
    ("sky_archer", "Sky Archer", "ZEUS", ("toga", (150, 192, 232), SILVER, (190, 150, 90), LIGHT, "circlet", SILVER, False, True)),
    ("thunder_brute", "Thunder Brute", "ZEUS", ("armor", IRON, (250, 205, 70), (40, 34, 30), TAN, "horns", (250, 205, 70), True, False)),
    ("keeper_sky", "Keeper of the Sky", "ZEUS", ("robe", WHITE, GOLD, (236, 236, 242), LIGHT, "crown", GOLD, True, False)),
    ("tide_hoplite", "Tide Hoplite", "POSEIDON", ("armor", (60, 130, 140), (120, 240, 250), (24, 70, 84), (150, 200, 200), "helmet", (120, 240, 250), False, False)),
    ("siren", "Siren", "POSEIDON", ("dress", (70, 170, 190), (220, 250, 255), (30, 90, 110), (170, 215, 210), "circlet", (220, 250, 255), False, True)),
    ("reef_brute", "Reef Brute", "POSEIDON", ("wild", (40, 90, 100), (230, 120, 110), (30, 70, 70), (120, 170, 160), "horns", (230, 120, 110), True, False)),
    ("keeper_tides", "Keeper of the Tides", "POSEIDON", ("robe", (30, 80, 110), (120, 240, 250), (200, 235, 240), (150, 200, 200), "crown", (120, 240, 250), True, False)),
    ("shade", "Shade", "HADES", ("robe", (40, 36, 54), (120, 80, 170), (20, 18, 28), (150, 150, 170), "hood", (40, 36, 54), False, False)),
    ("lampad", "Lampad", "HADES", ("dress", (60, 30, 80), (255, 140, 40), (30, 20, 40), (190, 170, 200), "circlet", (255, 140, 40), False, True)),
    ("tartarus_brute", "Tartarus Brute", "HADES", ("armor", (50, 40, 50), (200, 60, 40), (20, 16, 20), (120, 110, 120), "horns", (200, 60, 40), True, False)),
    ("keeper_shades", "Keeper of the Shades", "HADES", ("robe", (24, 20, 32), (190, 80, 250), (210, 210, 225), (200, 200, 215), "crown", (190, 80, 250), True, False)),
    ("harpy", "Harpy", "ZEUS", ("wild", (120, 96, 70), (236, 220, 170), (90, 60, 40), (226, 196, 160), "wings", (236, 220, 170), False, True)),
    ("storm_augur", "Storm Augur", "ZEUS", ("robe", (90, 110, 170), (250, 205, 70), (230, 230, 240), LIGHT, "hood", (90, 110, 170), True, False)),
    ("telchine", "Telchine", "POSEIDON", ("wild", (30, 60, 80), (220, 170, 70), (20, 40, 50), (110, 150, 150), "circlet", (220, 170, 70), True, False)),
    ("tide_priest", "Tide Priest", "POSEIDON", ("robe", (220, 240, 245), (60, 190, 215), (180, 220, 225), (170, 215, 210), "circlet", (60, 190, 215), False, True)),
    ("fury", "Fury", "HADES", ("dress", (110, 20, 30), (20, 16, 20), (30, 10, 14), (170, 150, 160), "wings", (40, 30, 40), False, True)),
    ("necromancer", "Necromancer", "HADES", ("robe", (30, 44, 36), (110, 220, 130), (20, 30, 24), (160, 175, 165), "hood", (30, 44, 36), True, False)),
]
SEA, DEEP, PEARL = (150, 200, 200), (110, 150, 150), (170, 215, 210)
ASH, GREY, BONEWHITE = (150, 150, 170), (120, 110, 120), (200, 200, 215)
SENTINELS += [
    # --- Zeus: fifteen more, each with a trait of its own
    ("aegis_bearer", "Aegis Bearer", "ZEUS", ("armor", GOLD, WHITE, (50, 36, 26), TAN, "helmet", (200, 60, 60), False, False)),
    ("eagle_standard", "Eagle Standard", "ZEUS", ("toga", WHITE, (200, 40, 40), (90, 60, 40), LIGHT, "laurel", GOLD, False, False)),
    ("spark_runner", "Spark Runner", "ZEUS", ("wild", (70, 70, 90), (255, 240, 120), (250, 240, 160), PALE, "none", GOLD, False, False)),
    ("chain_thrower", "Chain Thrower", "ZEUS", ("armor", IRON, (150, 150, 160), (30, 30, 30), TAN, "hood", (70, 70, 80), True, False)),
    ("thunder_herald", "Thunder Herald", "ZEUS", ("robe", (60, 70, 130), GOLD, (220, 220, 230), LIGHT, "wings", GOLD, True, False)),
    ("gust_thief", "Gust Thief", "ZEUS", ("toga", (170, 200, 190), (90, 110, 100), (120, 90, 60), LIGHT, "hood", (90, 110, 100), False, False)),
    ("storm_sower", "Storm Sower", "ZEUS", ("robe", (80, 90, 110), (255, 240, 120), (40, 40, 50), TAN, "circlet", (255, 240, 120), False, True)),
    ("mirage", "Mirage", "ZEUS", ("dress", (200, 220, 250), (250, 250, 255), (240, 240, 250), PALE, "circlet", WHITE, False, True)),
    ("oathkeeper", "Oathkeeper", "ZEUS", ("armor", (210, 210, 220), (90, 130, 220), (160, 120, 70), LIGHT, "helmet", (90, 130, 220), True, False)),
    ("maenad", "Maenad", "ZEUS", ("wild", (124, 62, 152), (90, 170, 80), (60, 30, 20), TAN, "flowers", (180, 60, 120), False, True)),
    ("paean_singer", "Paean Singer", "ZEUS", ("toga", CREAM, (242, 212, 110), (242, 212, 110), LIGHT, "laurel", GOLD, False, True)),
    ("bolt_hurler", "Bolt Hurler", "ZEUS", ("armor", BRONZE, (255, 240, 120), (50, 40, 30), TAN, "helmet", (255, 240, 120), False, False)),
    ("bull_charger", "Bull Charger", "ZEUS", ("wild", (110, 70, 40), (220, 200, 160), (40, 26, 18), TAN, "horns", BONE, True, False)),
    ("oracle_breaker", "Oracle Breaker", "ZEUS", ("robe", (50, 40, 70), (200, 120, 255), (20, 20, 30), PALE, "hood", (50, 40, 70), False, False)),
    ("wind_dancer", "Wind Dancer", "ZEUS", ("dress", (190, 230, 220), (120, 200, 190), (250, 250, 250), LIGHT, "wings", (220, 250, 245), False, True)),
    # --- Poseidon
    ("shell_guard", "Shell Guard", "POSEIDON", ("armor", (200, 150, 130), (250, 230, 210), (30, 70, 80), SEA, "helmet", (230, 120, 110), False, False)),
    ("tide_herald", "Tide Herald", "POSEIDON", ("robe", (40, 110, 150), (250, 230, 150), (200, 235, 240), PEARL, "circlet", (250, 230, 150), True, False)),
    ("pufferling", "Pufferling", "POSEIDON", ("wild", (220, 190, 90), (120, 90, 40), (160, 130, 60), (200, 210, 160), "horns", (250, 240, 200), False, False)),
    ("net_caster", "Net Caster", "POSEIDON", ("toga", (90, 150, 140), (200, 190, 150), (30, 60, 60), SEA, "none", GOLD, True, False)),
    ("conch_blower", "Conch Blower", "POSEIDON", ("toga", (230, 200, 190), (230, 120, 110), (40, 90, 100), PEARL, "flowers", (230, 120, 110), False, True)),
    ("wrecker", "Wrecker", "POSEIDON", ("wild", (60, 50, 40), (160, 120, 70), (20, 20, 20), DEEP, "hood", (40, 36, 30), True, False)),
    ("brine_layer", "Brine Layer", "POSEIDON", ("robe", (70, 120, 90), (170, 220, 120), (40, 80, 50), DEEP, "hood", (70, 120, 90), False, False)),
    ("mist_dancer", "Mist Dancer", "POSEIDON", ("dress", (210, 235, 240), (255, 255, 255), (230, 245, 250), PEARL, "circlet", (255, 255, 255), False, True)),
    ("tide_warden", "Tide Warden", "POSEIDON", ("armor", (40, 80, 120), (120, 240, 250), (20, 50, 70), SEA, "helmet", (120, 240, 250), True, False)),
    ("sea_raider", "Sea Raider", "POSEIDON", ("wild", (120, 50, 40), (220, 200, 170), (150, 60, 40), TAN, "horns", (220, 200, 170), True, True)),
    ("pearl_mender", "Pearl Mender", "POSEIDON", ("dress", (245, 240, 250), (250, 180, 200), (250, 200, 210), PEARL, "circlet", (250, 180, 200), False, True)),
    ("harpooner", "Harpooner", "POSEIDON", ("armor", (70, 100, 100), (230, 230, 210), (30, 40, 40), DEEP, "hood", (50, 80, 80), True, False)),
    ("wave_rider", "Wave Rider", "POSEIDON", ("armor", (60, 140, 170), (255, 255, 255), (240, 250, 255), SEA, "wings", (255, 255, 255), False, False)),
    ("lull_singer", "Lull Singer", "POSEIDON", ("dress", (120, 100, 170), (220, 200, 250), (60, 40, 100), PEARL, "flowers", (220, 200, 250), False, True)),
    ("current_slider", "Current Slider", "POSEIDON", ("toga", (40, 160, 170), (20, 90, 100), (10, 60, 70), SEA, "none", GOLD, False, False)),
    # --- Hades
    ("gate_warden", "Gate Warden", "HADES", ("armor", (40, 40, 48), (190, 80, 250), (20, 16, 20), GREY, "helmet", (190, 80, 250), False, False)),
    ("styx_herald", "Styx Herald", "HADES", ("robe", (20, 40, 50), (110, 220, 200), (200, 220, 220), BONEWHITE, "hood", (20, 40, 50), True, False)),
    ("ember_wretch", "Ember Wretch", "HADES", ("wild", (60, 24, 16), (255, 140, 40), (255, 180, 60), (140, 90, 80), "none", GOLD, False, False)),
    ("shackler", "Shackler", "HADES", ("armor", (60, 56, 64), (150, 150, 160), (20, 20, 24), GREY, "hood", (30, 28, 34), True, False)),
    ("death_knell", "Death Knell", "HADES", ("robe", (36, 30, 24), (200, 170, 90), (220, 220, 220), BONEWHITE, "hood", (36, 30, 24), True, False)),
    ("grave_robber", "Grave Robber", "HADES", ("toga", (70, 60, 50), (120, 100, 70), (40, 30, 24), ASH, "hood", (50, 44, 36), False, False)),
    ("miasma_sower", "Miasma Sower", "HADES", ("robe", (40, 60, 40), (150, 220, 90), (30, 40, 30), (150, 170, 150), "hood", (40, 60, 40), False, False)),
    ("phantasm", "Phantasm", "HADES", ("dress", (90, 80, 120), (200, 190, 240), (230, 230, 250), BONEWHITE, "circlet", (200, 190, 240), False, True)),
    ("soul_warden", "Soul Warden", "HADES", ("armor", (30, 50, 70), (120, 220, 255), (200, 230, 250), ASH, "helmet", (120, 220, 255), True, False)),
    ("revenant", "Revenant", "HADES", ("armor", (70, 30, 30), (20, 16, 20), (20, 10, 10), GREY, "none", GOLD, True, False)),
    ("psychopomp", "Psychopomp", "HADES", ("robe", (240, 236, 220), (120, 80, 170), (30, 24, 40), BONEWHITE, "wings", (60, 50, 80), False, True)),
    ("bone_lancer", "Bone Lancer", "HADES", ("armor", BONE, (60, 50, 70), (30, 26, 34), ASH, "horns", BONE, False, False)),
    ("chariot_shade", "Chariot Shade", "HADES", ("armor", (24, 20, 32), (255, 140, 40), (10, 8, 12), ASH, "helmet", (255, 140, 40), False, False)),
    ("lethe_drinker", "Lethe Drinker", "HADES", ("dress", (60, 90, 110), (170, 210, 230), (150, 180, 200), BONEWHITE, "flowers", (170, 210, 230), False, True)),
    ("shadow_stepper", "Shadow Stepper", "HADES", ("toga", (24, 22, 30), (90, 60, 130), (12, 10, 16), ASH, "hood", (24, 22, 30), False, False)),
    # --- four more to a tower, each with an attack of its own: a duelist, a slinger, a reaver, a slammer
    ("storm_duelist", "Storm Duelist", "ZEUS", ("armor", (230, 230, 236), (90, 160, 250), (40, 30, 24), TAN, "helmet", (200, 40, 40), False, False)),
    ("gale_slinger", "Gale Slinger", "ZEUS", ("toga", (200, 225, 235), (110, 170, 200), (120, 90, 60), LIGHT, "circlet", (110, 170, 200), False, True)),
    ("cyclone_reaver", "Cyclone Reaver", "ZEUS", ("wild", (120, 130, 150), (255, 240, 120), (30, 30, 40), TAN, "horns", (230, 230, 240), True, False)),
    ("skyfall_brute", "Skyfall Brute", "ZEUS", ("armor", (150, 120, 70), (250, 205, 70), (50, 36, 26), TAN, "helmet", (250, 205, 70), True, False)),
    ("coral_duelist", "Coral Duelist", "POSEIDON", ("armor", (230, 110, 100), (255, 210, 190), (30, 60, 70), SEA, "helmet", (250, 160, 150), False, False)),
    ("spine_thrower", "Spine Thrower", "POSEIDON", ("wild", (70, 50, 90), (200, 120, 230), (30, 20, 40), DEEP, "horns", (200, 120, 230), False, False)),
    ("maelstrom_reaver", "Maelstrom Reaver", "POSEIDON", ("wild", (30, 70, 110), (120, 240, 250), (10, 30, 50), SEA, "hood", (30, 70, 110), True, False)),
    ("depth_crusher", "Depth Crusher", "POSEIDON", ("armor", (40, 60, 70), (200, 170, 90), (20, 30, 34), DEEP, "helmet", (200, 170, 90), True, False)),
    ("grave_fencer", "Grave Fencer", "HADES", ("toga", (30, 26, 36), (220, 220, 230), (14, 12, 18), BONEWHITE, "helmet", (120, 80, 170), False, False)),
    ("pyre_archer", "Pyre Archer", "HADES", ("robe", (70, 24, 16), (255, 160, 50), (40, 14, 10), ASH, "hood", (70, 24, 16), False, True)),
    ("scythe_reaper", "Scythe Reaper", "HADES", ("robe", (20, 18, 24), (150, 150, 170), (230, 230, 230), BONEWHITE, "hood", (20, 18, 24), True, False)),
    ("tomb_breaker", "Tomb Breaker", "HADES", ("armor", (80, 70, 70), (190, 80, 250), (30, 24, 30), GREY, "horns", BONE, True, False)),
]
for _sid, _name, _line, _look in SENTINELS:
    LOOKS[_sid] = _look

# What each sentinel wears on the skin's outer layer (drawn standing proud of the body): see apply_layers
SENTINEL_LAYERS = {
    "storm_duelist": ["pauldrons", "cuirass", "bracers", "cape", "plume"], "gale_slinger": ["bandolier", "scarf", "wraps"],
    "cyclone_reaver": ["furs", "chains", "bracers", "belt"], "skyfall_brute": ["pauldrons", "cuirass", "belt", "greaves"],
    "coral_duelist": ["pauldrons", "cuirass", "bracers", "sash", "plume"], "spine_thrower": ["bandolier", "kelp", "wraps"],
    "maelstrom_reaver": ["kelp", "chains", "belt", "bracers"], "depth_crusher": ["pauldrons", "cuirass", "chains", "greaves"],
    "grave_fencer": ["mask", "cape", "bracers", "plume"], "pyre_archer": ["bandolier", "sash", "bracers"],
    "scythe_reaper": ["cape", "bones", "chains"], "tomb_breaker": ["bones", "pauldrons", "belt", "greaves"],
    "storm_hoplite": ["pauldrons", "cuirass", "greaves"], "sky_archer": ["bandolier", "bracers"], "thunder_brute": ["furs", "pauldrons", "belt"],
    "keeper_sky": ["cape", "tabard"], "tide_hoplite": ["pauldrons", "cuirass", "greaves"], "siren": ["skirt", "scarf"], "reef_brute": ["kelp", "belt"],
    "keeper_tides": ["cape", "tabard"], "shade": ["wraps"], "lampad": ["skirt", "sash"], "tartarus_brute": ["bones", "pauldrons", "belt"],
    "keeper_shades": ["cape", "tabard", "chains"], "harpy": ["furs"], "storm_augur": ["sash", "scarf"], "telchine": ["bandolier", "belt"],
    "tide_priest": ["sash", "scarf"], "fury": ["skirt", "chains"], "necromancer": ["bones", "belt"],
    "aegis_bearer": ["pauldrons", "cuirass", "greaves", "plume"], "eagle_standard": ["cape", "sash"], "spark_runner": ["wraps", "chains"],
    "chain_thrower": ["chains", "bracers", "bandolier"], "thunder_herald": ["tabard", "scarf"], "gust_thief": ["mask", "bandolier", "wraps"],
    "storm_sower": ["sash", "belt", "bracers"], "mirage": ["scarf", "skirt", "cape"], "oathkeeper": ["pauldrons", "cuirass", "tabard", "greaves"],
    "maenad": ["furs", "kelp", "skirt"], "paean_singer": ["sash", "scarf"], "bolt_hurler": ["pauldrons", "bandolier", "bracers"],
    "bull_charger": ["furs", "pauldrons", "belt", "greaves"], "oracle_breaker": ["mask", "tabard", "chains"], "wind_dancer": ["scarf", "skirt", "wraps"],
    "shell_guard": ["pauldrons", "cuirass", "greaves"], "tide_herald": ["cape", "tabard", "scarf"], "pufferling": ["furs", "chains"],
    "net_caster": ["bandolier", "chains", "belt"], "conch_blower": ["sash", "scarf"], "wrecker": ["mask", "bandolier", "wraps", "belt"],
    "brine_layer": ["kelp", "belt"], "mist_dancer": ["scarf", "skirt", "cape"], "tide_warden": ["pauldrons", "cuirass", "tabard", "greaves"],
    "sea_raider": ["furs", "bandolier", "bracers", "belt"], "pearl_mender": ["sash", "skirt"], "harpooner": ["bandolier", "bracers", "greaves"],
    "wave_rider": ["pauldrons", "cape", "greaves"], "lull_singer": ["scarf", "skirt", "chains"], "current_slider": ["wraps", "sash", "mask"],
    "gate_warden": ["pauldrons", "cuirass", "greaves", "chains"], "styx_herald": ["cape", "tabard", "scarf"], "ember_wretch": ["wraps", "chains", "bones"],
    "shackler": ["chains", "bandolier", "bracers", "mask"], "death_knell": ["tabard", "belt", "scarf"], "grave_robber": ["mask", "bandolier", "wraps", "belt"],
    "miasma_sower": ["mask", "kelp", "belt"], "phantasm": ["cape", "skirt", "scarf"], "soul_warden": ["pauldrons", "cuirass", "tabard", "greaves"],
    "revenant": ["bones", "pauldrons", "wraps", "belt"], "psychopomp": ["cape", "sash", "scarf"], "bone_lancer": ["bones", "pauldrons", "bandolier", "greaves"],
    "chariot_shade": ["pauldrons", "cape", "cuirass", "greaves"], "lethe_drinker": ["scarf", "skirt", "sash"], "shadow_stepper": ["mask", "wraps", "sash"],
}
# What a god wears on the outer layer, by its outfit
OUTFIT_LAYERS = {"armor": ["pauldrons", "cuirass", "greaves"], "toga": ["sash", "belt"], "dress": ["skirt", "scarf"], "robe": ["tabard", "scarf"], "wild": ["furs", "belt"]}

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
    assert set(SIGNATURES) == {g[0] for _, _, _, g in all_gods()}, "every god needs a signature move"
    assert all(k in MOVE_KINDS for _, k in SIGNATURES.values())
    rows = []
    for idx, line, rank, g in all_gods():
        gid, name, title, wname, shape, power, teff, seff, kind = g
        rows.append(
            f"        new God({idx}, Line.{line}, {rank}, {jstr(gid)}, {jstr(name)}, {jstr(title)}, "
            f"{jstr(wname)}, WeaponKind.{kind}, Power.{power}, {jstr(teff)}, {jstr(seff)}, Signature.{SIGNATURES[gid][1]})"
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

def gen_atlases():
    """
    Every texture the mod ships, gathered into atlases under art/atlases/: one sheet per kind, each at its own pixel
    size and again four times larger, with index.json saying which tile is which. Animated textures show their
    first frame; items-animated.png keeps every frame of the animated items in a row each.
    """
    out = os.path.join(ROOT, "art/atlases")
    os.makedirs(out, exist_ok=True)
    index = {}

    def sheet(name, folder, tile_w, tile_h, columns, recursive=False):
        base = os.path.join(ASSETS, "textures", folder)
        files = []
        for root, _dirs, names in os.walk(base):
            for n in sorted(names):
                if n.endswith(".png"):
                    files.append(os.path.join(root, n))
            if not recursive:
                break
        files.sort(key=lambda f: os.path.relpath(f, base))
        rows = (len(files) + columns - 1) // columns
        img = Image.new("RGBA", (columns * tile_w, max(1, rows) * tile_h), (0, 0, 0, 0))
        cells = {}
        for i, f in enumerate(files):
            tile = Image.open(f).convert("RGBA").crop((0, 0, tile_w, tile_h))
            img.paste(tile, ((i % columns) * tile_w, (i // columns) * tile_h))
            cells[os.path.relpath(f, base)[:-4].replace(os.sep, "/")] = [i % columns, i // columns]
        img.save(os.path.join(out, f"{name}.png"))
        img.resize((img.width * 4, img.height * 4), Image.NEAREST).save(os.path.join(out, f"{name}@4x.png"))
        index[name] = {"tile": [tile_w, tile_h], "columns": columns, "count": len(files), "source": f"assets/{MOD}/textures/{folder}", "tiles": cells}
        return files

    item_files = sheet("items", "item", 16, 16, 16)
    sheet("weapon-designs", "item/design", 16, 16, 8)
    sheet("blocks", "block", 16, 16, 8)
    sheet("skins", "entity", 64, 64, 9, recursive=True)

    # every frame of every animated item, a row per item
    animated = []
    for f in item_files:
        im = Image.open(f).convert("RGBA")
        if im.height > 16:
            animated.append((os.path.basename(f)[:-4], im))
    if animated:
        widest = max(im.height // 16 for _n, im in animated)
        strip = Image.new("RGBA", (widest * 16, len(animated) * 16), (0, 0, 0, 0))
        rows = {}
        for r, (n, im) in enumerate(animated):
            for fr in range(im.height // 16):
                strip.paste(im.crop((0, fr * 16, 16, fr * 16 + 16)), (fr * 16, r * 16))
            rows[n] = {"row": r, "frames": im.height // 16}
        strip.save(os.path.join(out, "items-animated.png"))
        strip.resize((strip.width * 4, strip.height * 4), Image.NEAREST).save(os.path.join(out, "items-animated@4x.png"))
        index["items-animated"] = {"tile": [16, 16], "rows": rows}

    # the gods and sentinels as they look in game: front views cut from the skins
    people = []
    for _, _line, _rank, g in all_gods():
        people.append((g[0], os.path.join(ASSETS, f"textures/entity/god/{g[0]}.png")))
    for sid, _name, _line, _look in SENTINELS:
        people.append((sid, os.path.join(ASSETS, f"textures/entity/sentinel/{sid}.png")))
    cols = 15
    portraits = Image.new("RGBA", (cols * 16, ((len(people) + cols - 1) // cols) * 32), (0, 0, 0, 0))
    cells = {}
    for i, (n, f) in enumerate(people):
        portraits.paste(skin_preview(Image.open(f).convert("RGBA"), 1), ((i % cols) * 16, (i // cols) * 32))
        cells[n] = [i % cols, i // cols]
    portraits.save(os.path.join(out, "portraits.png"))
    portraits.resize((portraits.width * 4, portraits.height * 4), Image.NEAREST).save(os.path.join(out, "portraits@4x.png"))
    index["portraits"] = {"tile": [16, 32], "columns": cols, "count": len(people), "tiles": cells}

    # every god in each phase of its trial: a row of three for each
    gods = [g[0] for _, _line, _rank, g in all_gods()]
    cols = 9
    phases = Image.new("RGBA", (cols * 48, ((len(gods) + cols - 1) // cols) * 32), (0, 0, 0, 0))
    cells = {}
    for i, n in enumerate(gods):
        for ph, suffix in enumerate(("", "_p2", "_p3")):
            f = os.path.join(ASSETS, f"textures/entity/god/{n}{suffix}.png")
            phases.paste(skin_preview(Image.open(f).convert("RGBA"), 1), ((i % cols) * 48 + ph * 16, (i // cols) * 32))
        cells[n] = [i % cols, i // cols]
    phases.save(os.path.join(out, "phases.png"))
    phases.resize((phases.width * 4, phases.height * 4), Image.NEAREST).save(os.path.join(out, "phases@4x.png"))
    index["phases"] = {"tile": [48, 32], "columns": cols, "count": len(gods), "tiles": cells}

    wj(os.path.join(out, "index.json"), index)
    lines = ["# Texture atlases", "", "Generated by `tools/generate.py` from the textures the mod ships. Each sheet comes at its own pixel size and",
             "again four times larger (`@4x`). `index.json` maps every texture name to its column and row.", "",
             "| Atlas | Tiles | Tile size | What |", "| --- | --- | --- | --- |"]
    what = {"items": "every item texture (first frame of animated ones)", "weapon-designs": "weapon designs drawn ahead for future mythologies",
            "blocks": "every block texture (first frame)", "skins": "the raw 64x64 skins of the gods (one for each phase of a trial) and the passage sentinels",
            "portraits": "the gods and sentinels as they look from the front",
            "phases": "every god in the three phases of its trial, side by side: itself, its aegis, its wrath"}
    for name in ("items", "weapon-designs", "blocks", "skins", "portraits", "phases"):
        e = index[name]
        lines.append(f"| `{name}.png` | {e['count']} | {e['tile'][0]} x {e['tile'][1]} | {what[name]} |")
    lines.append(f"| `items-animated.png` | {len(animated)} rows | 16 x 16 | every frame of every animated item, one item per row |")
    w(os.path.join(out, "README.md"), "\n".join(lines) + "\n")

def gen_handbook_java():
    rows = [f"        new Topic({jstr(hid)}, {len(lines)})" for hid, _title, lines in HANDBOOK]
    src = f"""package {JAVA_PKG}.ui;

import java.util.List;

/** GENERATED by tools/generate.py - do not edit by hand. The topics of the Climber's Handbook: id, number of lines. */
public final class HandbookData {{
    private HandbookData() {{}}

    public record Topic(String id, int lines) {{}}

    public static final List<Topic> ALL = List.of(
{(",\n").join(rows)}
    );
}}
"""
    w(os.path.join(ROOT, "common/src/main/java", *JAVA_PKG.split("."), "ui", "HandbookData.java"), src)

def gen_story_java():
    rows = [f"        new Entry({jstr(sid)}, {jstr(title)}, {len(pages)})" for sid, title, _blurb, pages in STORY]
    src = f"""package {JAVA_PKG}.story;

import java.util.List;

/** GENERATED by tools/generate.py - do not edit by hand. The chapters of the story: id, book title, pages. */
final class StoryData {{
    private StoryData() {{}}

    record Entry(String id, String title, int pages) {{}}

    static final List<Entry> ALL = List.of(
{(",\n").join(rows)}
    );
}}
"""
    w(os.path.join(ROOT, "common/src/main/java", *JAVA_PKG.split("."), "story", "StoryData.java"), src)

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
        "itemGroup.realms_of_chaos.arms": "Chaos: Relics & Arms",
        "itemGroup.realms_of_chaos.materials": "Chaos: Materials",
        "itemGroup.realms_of_chaos.building": "Chaos: Building Blocks",
        "itemGroup.realms_of_chaos.furniture": "Chaos: Furniture",
        "itemGroup.realms_of_chaos.curios": "Chaos: Curios & Music",
        "itemGroup.realms_of_chaos.realm": "Chaos: Realm Builder",
        "item.realms_of_chaos.chaos_compass": "Compass of Chaos",
        "item.realms_of_chaos.chaos_compass.tooltip": "Right-click: scan. Read it above your hotbar for a minute or more. In a passage it points to the nearest seal",
        "item.realms_of_chaos.chaos_compass.mode": "Looking for: %s",
        "item.realms_of_chaos.chaos_compass.sneak": "Sneak and right-click to change what it looks for",
        "item.realms_of_chaos.field_bag": "Field Tower Bag",
        "item.realms_of_chaos.lockpick": "Lockpick",
        "hud.realms_of_chaos.lockpick.locked": "Locked. A lockpick would open it",
        "hud.realms_of_chaos.lockpick.title": "Pick the lock: click in the gold",
        "hud.realms_of_chaos.lockpick.set": "A pin is set",
        "hud.realms_of_chaos.lockpick.miss": "The pins drop",
        "hud.realms_of_chaos.lockpick.snapped": "The pick snaps",
        "hud.realms_of_chaos.lockpick.open": "The lock gives",
        "item.realms_of_chaos.field_bag.tooltip": "Right-click to open. What is in it stays in it",
        "item.realms_of_chaos.field_bag.deploy": "Sneak and right-click a block: sets down the first relay, jammer or waygate inside",
        "item.realms_of_chaos.field_bag.used": "%s stacks inside",
        "menu.realms_of_chaos.bag": "Field Tower Bag (%s slots)",
        "block.realms_of_chaos.shield_pylon": "Shield Pylon",
        "block.realms_of_chaos.relay_core": "Relay Tower Core",
        "block.realms_of_chaos.signal_jammer": "Signal Jammer",
        "block.realms_of_chaos.waygate_obelisk": "Waygate Obelisk",
        "block.realms_of_chaos.hub_heart": "Heart of the Hub",
        "block.realms_of_chaos.bounty_board": "Bounty Board",
        "block.realms_of_chaos.rune_stone": "Rune Stone",
        "block.realms_of_chaos.glyph_tile": "Glyph Tile",
        "block.realms_of_chaos.chime_crystal": "Chime Crystal",
        "block.realms_of_chaos.memory_plate": "Trembling Plate",
        "block.realms_of_chaos.olympian_marble": "Olympian Marble",
        "block.realms_of_chaos.olympian_marble_pillar": "Olympian Marble Pillar",
        "block.realms_of_chaos.gilded_frieze": "Gilded Frieze",
        "block.realms_of_chaos.abyssal_tile": "Abyssal Tile",
        "block.realms_of_chaos.underworld_brick": "Underworld Brick",
        "block.realms_of_chaos.chaos_lamp": "Chaos Lamp",
        "block.realms_of_chaos.olympian_tiles": "Olympian Tiles",
        "block.realms_of_chaos.chaos_mosaic": "Chaos Mosaic",
        "block.realms_of_chaos.olympian_panel": "Olympian Panel",
        "block.realms_of_chaos.abyssal_panel": "Abyssal Panel",
        "block.realms_of_chaos.underworld_panel": "Underworld Panel",
        "block.realms_of_chaos.spike_plate": "Spike Plate",
        "block.realms_of_chaos.spike_plate_dark": "Dark Spike Plate",
        "block.realms_of_chaos.trap_spikes": "Trap Spikes",
        "block.realms_of_chaos.flame_vent": "Flame Vent",
        "block.realms_of_chaos.gas_vent": "Gas Vent",
        "block.realms_of_chaos.sky_relief": "Relief of the Sky",
        "block.realms_of_chaos.sea_relief": "Relief of the Sea",
        "block.realms_of_chaos.shade_relief": "Relief of the Shades",
        "block.realms_of_chaos.marble_column": "Marble Column",
        "block.realms_of_chaos.sunstone_panel": "Sunstone Panel",
        "block.realms_of_chaos.storm_brick": "Storm Brick",
        "block.realms_of_chaos.coral_column": "Coral Column",
        "block.realms_of_chaos.pearl_panel": "Pearl Panel",
        "block.realms_of_chaos.tide_brick": "Tide Brick",
        "block.realms_of_chaos.bone_column": "Bone Column",
        "block.realms_of_chaos.ember_panel": "Ember Panel",
        "block.realms_of_chaos.ash_brick": "Ash Brick",
        "block.realms_of_chaos.sky_mosaic": "Mosaic of the Sky",
        "block.realms_of_chaos.sea_mosaic": "Mosaic of the Sea",
        "block.realms_of_chaos.shade_mosaic": "Mosaic of the Shades",
        "block.realms_of_chaos.star_vault": "Star Vault",
        "block.realms_of_chaos.celestial_globe": "Celestial Globe",
        "block.realms_of_chaos.bronze_grate": "Bronze Grate",
        "block.realms_of_chaos.weapon_rack": "Weapon Rack",
        "block.realms_of_chaos.scroll_shelf": "Scroll Shelf",
        "block.realms_of_chaos.bath_tile": "Bath Tile",
        "block.realms_of_chaos.laurel_hedge": "Laurel Hedge",
        "block.realms_of_chaos.kelp_weave": "Kelp Weave",
        "block.realms_of_chaos.asphodel_bed": "Asphodel Bed",
        "block.realms_of_chaos.cloudstone": "Cloudstone",
        "block.realms_of_chaos.cloud_tile": "Cloud Tile",
        "block.realms_of_chaos.trench_stone": "Trench Stone",
        "block.realms_of_chaos.nacre_tile": "Nacre Tile",
        "block.realms_of_chaos.tartarus_stone": "Tartarus Stone",
        "block.realms_of_chaos.throne_tile": "Throne Tile",
        "block.realms_of_chaos.portal_frame_sky": "Portal Frame of the Sky",
        "block.realms_of_chaos.portal_frame_sea": "Portal Frame of the Sea",
        "block.realms_of_chaos.portal_frame_shade": "Portal Frame of the Shades",
        "block.realms_of_chaos.portal_frame_hub": "Portal Frame of the Hub",
        "block.realms_of_chaos.arrival_sigil": "Arrival Sigil",
        "block.realms_of_chaos.olympian_marble_stairs": "Olympian Marble Stairs",
        "block.realms_of_chaos.olympian_marble_slab": "Olympian Marble Slab",
        "block.realms_of_chaos.cloudstone_stairs": "Cloudstone Stairs",
        "block.realms_of_chaos.cloudstone_slab": "Cloudstone Slab",
        "block.realms_of_chaos.trench_stone_stairs": "Trench Stone Stairs",
        "block.realms_of_chaos.trench_stone_slab": "Trench Stone Slab",
        "block.realms_of_chaos.tartarus_stone_stairs": "Tartarus Stone Stairs",
        "block.realms_of_chaos.tartarus_stone_slab": "Tartarus Stone Slab",
        "block.realms_of_chaos.storm_brick_stairs": "Storm Brick Stairs",
        "block.realms_of_chaos.storm_brick_slab": "Storm Brick Slab",
        "block.realms_of_chaos.tide_brick_stairs": "Tide Brick Stairs",
        "block.realms_of_chaos.tide_brick_slab": "Tide Brick Slab",
        "block.realms_of_chaos.ash_brick_stairs": "Ash Brick Stairs",
        "block.realms_of_chaos.ash_brick_slab": "Ash Brick Slab",
        "block.realms_of_chaos.underworld_brick_stairs": "Underworld Brick Stairs",
        "block.realms_of_chaos.underworld_brick_slab": "Underworld Brick Slab",
        "block.realms_of_chaos.bronze_door": "Bronze Door",
        "block.realms_of_chaos.bronze_trapdoor": "Bronze Trapdoor",
        "block.realms_of_chaos.sky_door": "Door of the Sky",
        "block.realms_of_chaos.sky_trapdoor": "Trapdoor of the Sky",
        "block.realms_of_chaos.sea_door": "Door of the Sea",
        "block.realms_of_chaos.sea_trapdoor": "Trapdoor of the Sea",
        "block.realms_of_chaos.shade_door": "Door of the Shades",
        "block.realms_of_chaos.shade_trapdoor": "Trapdoor of the Shades",
        "hud.realms_of_chaos.passage.room.observatory": "An observatory - a vault of stars",
        "hud.realms_of_chaos.passage.room.armory": "An armoury",
        "hud.realms_of_chaos.passage.room.baths": "Baths",
        "hud.realms_of_chaos.passage.room.sanctum": "A sanctum - quiet, and lit by the tower's fire",
        "hud.realms_of_chaos.passage.room.proving": "A proving ground. Beat a war drum to call three waves - the cache is for whoever stands at the end",
        "hud.realms_of_chaos.passage.room.proving.done": "A proving ground, its trial won",
        "hud.realms_of_chaos.passage.room.barracks": "A barracks - and its soldiers are awake",
        "hud.realms_of_chaos.passage.room.smithy": "A smithy - the tower's fires still burning in its hearth",
        "hud.realms_of_chaos.passage.room.crypt": "A crypt. Its dead are lying still. For now",
        "hud.realms_of_chaos.passage.room.war_room": "A war room - the tower's orders were drawn up here",
        "menu.realms_of_chaos.passage.kind.smithy": "Smithy",
        "menu.realms_of_chaos.passage.kind.crypt": "Crypt",
        "menu.realms_of_chaos.passage.kind.war_room": "War Room",
        "hud.realms_of_chaos.passage.room.dials": "A dial hall. Set each dial to the fresco above it - but each dial turns the next",
        "hud.realms_of_chaos.passage.room.slides": "A hall of sliding stones. Push each onto its socket (sneak to set one back)",
        "menu.realms_of_chaos.passage.kind.dials": "Dial Hall",
        "menu.realms_of_chaos.passage.kind.slides": "Sliding Stones",
        "hud.realms_of_chaos.room.dials.matched": "%s of 4 frescoes answered",
        "hud.realms_of_chaos.room.slides.stuck": "It will not go that way",
        "hud.realms_of_chaos.room.slides.reset": "The stone is set back",
        "block.realms_of_chaos.dial_stone": "Dial Stone",
        "block.realms_of_chaos.fresco_tile": "Fresco",
        "block.realms_of_chaos.slide_stone": "Sliding Stone",
        "block.realms_of_chaos.stone_socket": "Stone Socket",
        "hud.realms_of_chaos.passage.room.shutters": "A shutter hall. Shoot or throw at a lantern's veil: it turns, and the two beside it too. Open all four",
        "menu.realms_of_chaos.passage.kind.shutters": "Shutter Hall",
        "hud.realms_of_chaos.passage.room.duel": "A duelling circle. Its champion has been waiting",
        "hud.realms_of_chaos.passage.room.ambush": "A quiet hall. Somebody left a cache in the middle of it",
        "hud.realms_of_chaos.passage.room.overlook": "An overlook - keep to the pillars, and climb for the cache",
        "hud.realms_of_chaos.passage.ambush": "Ambush!",
        "hud.realms_of_chaos.passage.ambush.sub": "The doors are barred",
        "hud.realms_of_chaos.passage.ambush.over": "The ambush is beaten: the bars lift",
        "menu.realms_of_chaos.passage.kind.duel": "Duelling Circle",
        "menu.realms_of_chaos.passage.kind.ambush": "Quiet Hall",
        "menu.realms_of_chaos.passage.kind.overlook": "Overlook",
        "hud.realms_of_chaos.passage.proving.wave": "Wave %s of %s",
        "hud.realms_of_chaos.passage.proving.wave.1": "The tower's soldiers",
        "hud.realms_of_chaos.passage.proving.wave.2": "Skirmishers, and a brute",
        "hud.realms_of_chaos.passage.proving.wave.3": "Its champion leads the last",
        "hud.realms_of_chaos.passage.proving.won": "Proving won",
        "hud.realms_of_chaos.passage.proving.won.sub": "The cache is yours to open",
        "hud.realms_of_chaos.passage.proving.locked": "Locked. Beat a war drum and win the proving first",
        "hud.realms_of_chaos.passage.proving.underway": "The proving is under way: wave %s of %s",
        "hud.realms_of_chaos.passage.proving.done": "This proving is won",
        "hud.realms_of_chaos.passage.proving.peaceful": "No one answers the drum on Peaceful. The cache is open",
        "hud.realms_of_chaos.dummy.parry": "Parry",
        "hud.realms_of_chaos.dummy.parry.sub": "Stand still and tap sneak, or raise a shield, just as a blow lands. The attacker reels, and your next blow within three seconds is a riposte",
        "hud.realms_of_chaos.dummy.dodge": "Dodge",
        "hud.realms_of_chaos.dummy.dodge.sub": "Hold a direction and tap sneak to throw yourself that way. For a moment nothing can touch you, and a blow slipped leaves its striker open to a riposte",
        "menu.realms_of_chaos.passage.kind.proving": "Proving Ground",
        "menu.realms_of_chaos.passage.kind.barracks": "Barracks",
        "block.realms_of_chaos.arena_sand": "Arena Sand",
        "block.realms_of_chaos.beast_gate": "Beast Gate",
        "block.realms_of_chaos.war_drum": "War Drum",
        "block.realms_of_chaos.war_standard": "War Standard",
        "block.realms_of_chaos.training_dummy": "Training Dummy",
        "block.realms_of_chaos.sprinkler": "Sprinkler",
        "block.realms_of_chaos.porphyry": "Porphyry",
        "block.realms_of_chaos.polished_porphyry": "Polished Porphyry",
        "block.realms_of_chaos.porphyry_bricks": "Porphyry Bricks",
        "block.realms_of_chaos.porphyry_tiles": "Porphyry Tiles",
        "block.realms_of_chaos.chiseled_porphyry": "Chiseled Porphyry",
        "block.realms_of_chaos.porphyry_pillar": "Porphyry Pillar",
        "block.realms_of_chaos.polished_porphyry_stairs": "Polished Porphyry Stairs",
        "block.realms_of_chaos.polished_porphyry_slab": "Polished Porphyry Slab",
        "block.realms_of_chaos.porphyry_bricks_stairs": "Porphyry Brick Stairs",
        "block.realms_of_chaos.porphyry_bricks_slab": "Porphyry Brick Slab",
        "block.realms_of_chaos.porphyry_door": "Porphyry Door",
        "block.realms_of_chaos.porphyry_trapdoor": "Porphyry Trapdoor",
        "block.realms_of_chaos.deepslate_adamantine_ore": "Deepslate Adamantine Ore",
        "block.realms_of_chaos.adamantine_block": "Block of Adamantine",
        "item.realms_of_chaos.raw_adamantine": "Raw Adamantine",
        "item.realms_of_chaos.adamantine_scrap": "Adamantine Scrap",
        "item.realms_of_chaos.adamantine_ingot": "Adamantine Ingot",
        "item.realms_of_chaos.adamantine_sword": "Adamantine Sword",
        "item.realms_of_chaos.adamantine_pickaxe": "Adamantine Pickaxe",
        "item.realms_of_chaos.adamantine_axe": "Adamantine Axe",
        "item.realms_of_chaos.adamantine_shovel": "Adamantine Shovel",
        "item.realms_of_chaos.adamantine_hoe": "Adamantine Hoe",
        "block.realms_of_chaos.bronze_sliding_door": "Bronze Sliding Door",
        "block.realms_of_chaos.sky_sliding_door": "Sky Sliding Door",
        "block.realms_of_chaos.sea_sliding_door": "Sea Sliding Door",
        "block.realms_of_chaos.shade_sliding_door": "Shade Sliding Door",
        "block.realms_of_chaos.moving_dummy": "Sparring Dummy",
        "block.realms_of_chaos.chair": "Chair",
        "block.realms_of_chaos.table": "Table",
        "block.realms_of_chaos.bench": "Bench",
        "block.realms_of_chaos.dresser": "Dresser",
        "block.realms_of_chaos.bunk_bed": "Bunk Bed",
        "block.realms_of_chaos.garrison_door": "Garrison Door",
        "block.realms_of_chaos.garrison_trapdoor": "Garrison Trapdoor",
        "block.realms_of_chaos.verdigris_door": "Verdigris Door",
        "block.realms_of_chaos.verdigris_trapdoor": "Verdigris Trapdoor",
        "block.realms_of_chaos.ivory_door": "Ivory Door",
        "block.realms_of_chaos.ivory_trapdoor": "Ivory Trapdoor",
        "block.realms_of_chaos.reinforced_glass": "Reinforced Glass",
        "block.realms_of_chaos.frosted_glass": "Frosted Glass",
        "block.realms_of_chaos.smart_glass": "Smart Glass",
        "block.realms_of_chaos.veil_glass": "Veil Glass",
        "block.realms_of_chaos.one_way_glass": "One-Way Glass",
        "hud.realms_of_chaos.bunk": "A soldier's bunk - nobody sleeps in the passages",
        "hud.realms_of_chaos.dummy.hit": "Hit %s  -  %s in a row",
        "hud.realms_of_chaos.dummy.riposte": "  -  riposte!",
        "hud.realms_of_chaos.dummy.momentum": "  -  momentum %s",
        "menu.realms_of_chaos.passage.kind.observatory": "Observatory",
        "menu.realms_of_chaos.passage.kind.armory": "Armoury",
        "menu.realms_of_chaos.passage.kind.baths": "Baths",
        "menu.realms_of_chaos.passage.kind.sanctum": "Sanctum",
        "block.realms_of_chaos.beam_mirror": "Beam Mirror",
        "block.realms_of_chaos.beam_emitter": "Beam Emitter",
        "block.realms_of_chaos.beam_receiver": "Beam Receiver",
        "block.realms_of_chaos.dart_tile": "Dart Tile",
        "block.realms_of_chaos.ancient_urn": "Ancient Urn",
        "block.realms_of_chaos.lore_tablet": "Lore Tablet",
        "block.realms_of_chaos.tower_brazier": "Tower Brazier",
        "item.realms_of_chaos.passage_chart": "Passage Chart",
        "hud.realms_of_chaos.room.solved": "Solved",
        "hud.realms_of_chaos.room.solved.sub": "The cache unlocks",
        "hud.realms_of_chaos.room.already": "This room is already solved",
        "hud.realms_of_chaos.room.beam.scrambled": "Too many turns: the mirrors are thrown about again",
        "hud.realms_of_chaos.room.beam.last": "One more wrong turn and the mirrors are thrown about",
        "chat.realms_of_chaos.version": "Realms of Chaos %s",
        "hud.realms_of_chaos.build.differs": "Realms of Chaos: your game has %s, this world runs %s. Install the same build.",
        "hud.realms_of_chaos.onslaught.shockwave": "Shockwaves across the floor - jump them",
        "hud.realms_of_chaos.onslaught.fissures": "The floor is splitting - step off the lines",
        "hud.realms_of_chaos.onslaught.cross": "A cross, then a saltire - stand between the arms",
        "hud.realms_of_chaos.onslaught.rings": "Ring by ring - move to a ring that has gone off",
        "hud.realms_of_chaos.onslaught.sweep": "A wall across the floor - find the gap in it",
        "hud.realms_of_chaos.passage.afflicted": "This level is afflicted: %s",
        "hud.realms_of_chaos.passage.ill": "Afflicted: %s",
        "hud.realms_of_chaos.passage.ill.held": "%s - held off by the shrine",
        "hud.realms_of_chaos.passage.room.cells": "Cells. Whatever was kept here is long gone - mostly",
        "hud.realms_of_chaos.passage.room.store": "A storeroom. Look among the stacks",
        "affliction.realms_of_chaos.none": "Nothing",
        "affliction.realms_of_chaos.none.lore": "Nothing weighs on this level",
        "affliction.realms_of_chaos.gloom": "Gloom",
        "affliction.realms_of_chaos.gloom.lore": "The dark closes in, on and off. A shrine holds it back",
        "affliction.realms_of_chaos.weight": "Weight",
        "affliction.realms_of_chaos.weight.lore": "Every step is slower. A shrine lifts it for a while",
        "affliction.realms_of_chaos.frailty": "Frailty",
        "affliction.realms_of_chaos.frailty.lore": "Your blows land softer. A shrine mends it for a while",
        "affliction.realms_of_chaos.hunger": "Hunger",
        "affliction.realms_of_chaos.hunger.lore": "Your strength drains faster. A shrine stills it for a while",
        "affliction.realms_of_chaos.fatigue": "Fatigue",
        "affliction.realms_of_chaos.fatigue.lore": "Your arm is slow to swing. A shrine eases it for a while",
        "menu.realms_of_chaos.passage.kind.gauntlet": "Trap hall",
        "trap.realms_of_chaos.ember_tide": "Ember Tide",
        "trap.realms_of_chaos.ember_shuttle": "Ember Shuttle",
        "trap.realms_of_chaos.cinder_rain": "Cinder Rain",
        "trap.realms_of_chaos.pyre_ring": "Pyre Ring",
        "trap.realms_of_chaos.furnace_floor": "Furnace Floor",
        "trap.realms_of_chaos.hunting_flame": "Hunting Flame",
        "trap.realms_of_chaos.ember_path": "Ember Path",
        "trap.realms_of_chaos.fang_rows": "Fang Rows",
        "trap.realms_of_chaos.fang_weave": "Fang Weave",
        "trap.realms_of_chaos.the_jaw": "The Jaw",
        "trap.realms_of_chaos.needle_lattice": "Needle Lattice",
        "trap.realms_of_chaos.thorn_bloom": "Thorn Bloom",
        "trap.realms_of_chaos.narrow_way": "The Narrow Way",
        "trap.realms_of_chaos.saltire": "Saltire",
        "trap.realms_of_chaos.frost_creep": "Frost Creep",
        "trap.realms_of_chaos.rime_wheel": "Rime Wheel",
        "trap.realms_of_chaos.hoar_quarters": "Hoar Quarters",
        "trap.realms_of_chaos.glacier_march": "Glacier March",
        "trap.realms_of_chaos.storm_cross": "Storm Cross",
        "trap.realms_of_chaos.spark_scatter": "Spark Scatter",
        "trap.realms_of_chaos.thunder_wheel": "Thunder Wheel",
        "trap.realms_of_chaos.live_wire": "Live Wire",
        "trap.realms_of_chaos.static_field": "Static Field",
        "trap.realms_of_chaos.stalking_storm": "Stalking Storm",
        "trap.realms_of_chaos.miasma_drift": "Miasma Drift",
        "trap.realms_of_chaos.spore_bloom": "Spore Bloom",
        "trap.realms_of_chaos.bile_pools": "Bile Pools",
        "trap.realms_of_chaos.creeping_rot": "Creeping Rot",
        "trap.realms_of_chaos.grave_breath": "Grave Breath",
        "trap.realms_of_chaos.hollow_steps": "Hollow Steps",
        "trap.realms_of_chaos.geyser_field": "Geyser Field",
        "trap.realms_of_chaos.blowholes": "Blowholes",
        "trap.realms_of_chaos.gale_hall": "Gale Hall",
        "trap.realms_of_chaos.sky_snare": "Sky Snare",
        "trap.realms_of_chaos.false_floor": "False Floor",
        "trap.realms_of_chaos.blind_march": "Blind March",
        "trap.realms_of_chaos.dark_weave": "Dark Weave",
        "trap.realms_of_chaos.black_tide": "Black Tide",
        "trap.realms_of_chaos.pattern.rows": "It crosses the room a row at a time. Step over the row just as it dies",
        "trap.realms_of_chaos.pattern.columns": "It crosses the room from side to side. Step through just as a line dies",
        "trap.realms_of_chaos.pattern.bounce": "It crosses the room and comes back. Slip through as a row dies - it will be back",
        "trap.realms_of_chaos.pattern.pincer": "Two lines close on the middle. Get behind one before they meet",
        "trap.realms_of_chaos.pattern.ripple": "A ring spreads from the middle. Step inward through it as it passes",
        "trap.realms_of_chaos.pattern.implode": "A ring closes on the middle. Step outward through it as it passes",
        "trap.realms_of_chaos.pattern.flood": "The floor is taken from the walls inwards. Wait in the middle, then run as it lets go",
        "trap.realms_of_chaos.pattern.checker": "Every other tile, then the rest. Step onto a tile the moment it has gone off",
        "trap.realms_of_chaos.pattern.stripes": "Every other row, then the rest. Move a row at a time, onto the one that just went off",
        "trap.realms_of_chaos.pattern.weave": "Rows, then columns, then the other rows and columns. Stand where two spent lines cross",
        "trap.realms_of_chaos.pattern.quarters": "A quarter of the room at a time, round and round. Keep a quarter behind it",
        "trap.realms_of_chaos.pattern.diagonal": "It crosses corner to corner. Step sideways through the line as it passes",
        "trap.realms_of_chaos.pattern.serpent": "A lattice of single tiles creeps across the floor. Stand between them and move with it",
        "trap.realms_of_chaos.pattern.crossfire": "The cross through the middle, then the diagonals, then all the rest. Change ground each time",
        "trap.realms_of_chaos.pattern.spiral": "An arm turns about the middle. Walk round behind it",
        "trap.realms_of_chaos.pattern.twin_spiral": "Two arms turn about the middle. Keep between them",
        "trap.realms_of_chaos.pattern.scatter": "Every tile goes off once a round, in no order. Watch the wisps and stand on a spent tile",
        "trap.realms_of_chaos.pattern.hail": "A third of the floor at a time, never the same third. Watch for the wisps and keep off them",
        "trap.realms_of_chaos.pattern.crosshair": "A row and a column cross somewhere new each time. Stand off both lines",
        "trap.realms_of_chaos.pattern.lanes": "The straight way from door to door is the trap. Go round by the corners, then back",
        "trap.realms_of_chaos.pattern.path": "Only one narrow path is safe. It shows itself for a moment: remember it",
        "trap.realms_of_chaos.pattern.chase": "It strikes where you stood. Never stand still",
        "trap.realms_of_chaos.tolling_rings": "Tolling Rings",
        "trap.realms_of_chaos.frost_rings": "Frost Rings",
        "trap.realms_of_chaos.the_comb": "The Comb",
        "trap.realms_of_chaos.venom_comb": "Venom Comb",
        "trap.realms_of_chaos.pendulum_fire": "Pendulum Fire",
        "trap.realms_of_chaos.gale_pendulum": "Gale Pendulum",
        "trap.realms_of_chaos.twin_tides": "Twin Tides",
        "trap.realms_of_chaos.marching_thorns": "Marching Thorns",
        "trap.realms_of_chaos.crushing_rows": "Crushing Rows",
        "trap.realms_of_chaos.falling_sky": "Falling Sky",
        "trap.realms_of_chaos.millstone": "The Millstone",
        "trap.realms_of_chaos.root_tangle": "Root Tangle",
        "trap.realms_of_chaos.briar_rings": "Briar Rings",
        "trap.realms_of_chaos.stranglevine": "Stranglevine",
        "trap.realms_of_chaos.leech_floor": "Leech Floor",
        "trap.realms_of_chaos.hunger_tide": "Hunger Tide",
        "trap.realms_of_chaos.sapping_weave": "Sapping Weave",
        "trap.realms_of_chaos.pattern.rings": "Every other ring about the middle, then the rings between. Step inward a ring at a time, onto the one just spent",
        "trap.realms_of_chaos.pattern.comb": "Teeth from one wall, then from the other. Change columns as the halves swap",
        "trap.realms_of_chaos.pattern.pendulum": "A line swings from side to side and back. Cross behind it, and mind its return",
        "trap.realms_of_chaos.pattern.twin_rows": "Two lines cross the room half a room apart. Walk between them, at their pace",
        "menu.realms_of_chaos.passage.kind.cells": "Cells",
        "menu.realms_of_chaos.passage.kind.store": "Storeroom",
        "hud.realms_of_chaos.room.chimes.start": "Ring all four within %s seconds",
        "hud.realms_of_chaos.room.chimes.slow": "Too slow - the crystals fall silent",
        "hud.realms_of_chaos.room.plates.wrong": "The floor gives. Watch the path, and begin at its first plate",
        "hud.realms_of_chaos.room.urn.ambush": "Something was hiding in the urn!",
        "hud.realms_of_chaos.cache.locked": "Locked. Solve this room's puzzle first",
        "hud.realms_of_chaos.passage.chart": "You have been handed a Passage Chart: it draws itself as you explore",
        "hud.realms_of_chaos.passage.room.vault": "A vault. Press the glyph tiles until all nine are lit",
        "hud.realms_of_chaos.passage.room.chimes": "A chime room. Ring all four crystals before the first falls silent",
        "hud.realms_of_chaos.passage.room.beam": "A beam room. Turn mirrors until the light reaches the receiver. Some lead nowhere - and turns are counted",
        "hud.realms_of_chaos.passage.room.plates": "A trembling floor. Only one path holds - watch it show itself",
        "hud.realms_of_chaos.passage.room.crusher": "A crusher hall. Get off the marked row",
        "hud.realms_of_chaos.passage.room.darts": "A dart hall. Keep off the marked tiles",
        "hud.realms_of_chaos.passage.room.spikes": "A spike hall. Two colours of tile: each comes up in turn",
        "hud.realms_of_chaos.passage.room.flames": "A flame hall. The fire walks row by row: cross behind it",
        "hud.realms_of_chaos.passage.room.blades": "A blade hall. It sweeps wall to wall: go when it has passed",
        "hud.realms_of_chaos.passage.room.stairwell": "A stairwell. Something is kept on the loft",
        "hud.realms_of_chaos.passage.room.arc": "An arc hall. The arc turns about the pillar: keep ahead of it",
        "hud.realms_of_chaos.passage.room.gas": "A gas hall. One half fills at a time: stand in the other",
        "menu.realms_of_chaos.passage.kind.vault": "Vault",
        "menu.realms_of_chaos.passage.kind.chimes": "Chime room",
        "menu.realms_of_chaos.passage.kind.beam": "Beam room",
        "menu.realms_of_chaos.passage.kind.plates": "Trembling floor",
        "menu.realms_of_chaos.passage.kind.crusher": "Crusher hall",
        "menu.realms_of_chaos.passage.kind.darts": "Dart hall",
        "menu.realms_of_chaos.passage.kind.spikes": "Spike hall",
        "menu.realms_of_chaos.passage.kind.flames": "Flame hall",
        "menu.realms_of_chaos.passage.kind.blades": "Blade hall",
        "menu.realms_of_chaos.passage.kind.stairwell": "Stairwell",
        "menu.realms_of_chaos.passage.kind.arc": "Arc hall",
        "menu.realms_of_chaos.passage.kind.gas": "Gas hall",
        "screen.realms_of_chaos.home.skills": "Mastery (%s to spend)",
        "screen.realms_of_chaos.home.skills.tip": "Spend the points you have earned on combat, survival and exploration",
        "screen.realms_of_chaos.skills": "Mastery",
        "screen.realms_of_chaos.skills.points": "Points to spend: %s (earned %s - one for every god woken and every passage walked)",
        "screen.realms_of_chaos.skills.how": "Every skill has three ranks. Points are never lost: the tree can be taken apart and spent again",
        "screen.realms_of_chaos.skills.branch.combat": "- Combat -",
        "screen.realms_of_chaos.skills.branch.survival": "- Survival -",
        "screen.realms_of_chaos.skills.branch.exploration": "- Exploration -",
        "screen.realms_of_chaos.skills.line": "%s  %s / %s  -  %s",
        "screen.realms_of_chaos.skills.raise": "%s to rank %s",
        "screen.realms_of_chaos.skills.reset": "Take the tree apart",
        "screen.realms_of_chaos.skills.reset.tip": "Every point comes back",
        "skill.realms_of_chaos.might": "Might", "skill.realms_of_chaos.might.desc": "6% more damage with every blow, each rank",
        "skill.realms_of_chaos.swift": "Swiftness", "skill.realms_of_chaos.swift.desc": "4% faster on your feet, each rank",
        "skill.realms_of_chaos.focus": "Focus", "skill.realms_of_chaos.focus.desc": "A relic's move comes back 8% sooner, each rank",
        "skill.realms_of_chaos.reach": "Reach", "skill.realms_of_chaos.reach.desc": "A quarter of a block more reach in a fight, each rank",
        "skill.realms_of_chaos.vigor": "Vigor", "skill.realms_of_chaos.vigor.desc": "One more heart, each rank",
        "skill.realms_of_chaos.guard": "Guard", "skill.realms_of_chaos.guard.desc": "A point and a half of armour, each rank",
        "skill.realms_of_chaos.steady": "Steadiness", "skill.realms_of_chaos.steady.desc": "Thrown back 15% less, each rank",
        "skill.realms_of_chaos.second_wind": "Second Wind", "skill.realms_of_chaos.second_wind.desc": "Below half health you start to mend - more often each rank",
        "skill.realms_of_chaos.cartographer": "Cartographer", "skill.realms_of_chaos.cartographer.desc": "Your chart sketches the rooms next to those you have seen; from rank 2 it knows what they are",
        "skill.realms_of_chaos.signal": "Signal", "skill.realms_of_chaos.signal.desc": "A steadier compass: +0.05 signal, each rank",
        "skill.realms_of_chaos.scavenger": "Scavenger", "skill.realms_of_chaos.scavenger.desc": "Urns give up forge material more often, each rank",
        "skill.realms_of_chaos.pathfinder": "Pathfinder", "skill.realms_of_chaos.pathfinder.desc": "Waygates channel 15% faster, each rank",
        "lore.realms_of_chaos.zeus.1": "Olympus was not always a tower. It was a mountain, and the gods stacked it floor on floor when Chaos came for the sky.",
        "lore.realms_of_chaos.zeus.2": "The Storm Hoplites do not remember being men. They remember an oath, and nothing before it.",
        "lore.realms_of_chaos.zeus.3": "Hebe poured the last cup of ambrosia the night the gates were broken. Nobody has grown older here since.",
        "lore.realms_of_chaos.zeus.4": "Athena drew the passages herself. She is said to have laughed only once while she did it.",
        "lore.realms_of_chaos.zeus.5": "A Keeper is not set to guard a seal. A Keeper is what is left of whoever broke the one before it.",
        "lore.realms_of_chaos.zeus.6": "The aegis was a shield once, and one god's. Now every god raises one, and none of them recalls being taught.",
        "lore.realms_of_chaos.zeus.7": "Hermes still runs messages between floors. There has been nobody to read them for a very long time.",
        "lore.realms_of_chaos.zeus.8": "When the floor shifts under you, that is Olympus turning in its sleep.",
        "lore.realms_of_chaos.poseidon.1": "The Abyss was the bed of the first sea. Poseidon drowned it a second time to keep Chaos out.",
        "lore.realms_of_chaos.poseidon.2": "Sirens sing the same song they sang to sailors. Down here it is a lullaby for something much larger.",
        "lore.realms_of_chaos.poseidon.3": "Every pearl in these halls was a promise made to the sea. The sea keeps promises. It keeps everything.",
        "lore.realms_of_chaos.poseidon.4": "The Telchines forged the trident, and were cast down for boasting of it. They forge still, and say nothing.",
        "lore.realms_of_chaos.poseidon.5": "Tide Priests mend what is broken because the tide does: it takes, and then it brings back.",
        "lore.realms_of_chaos.poseidon.6": "No light reaches the Trench. What glows there has learnt to do without.",
        "lore.realms_of_chaos.poseidon.7": "Amphitrite counts the waves. If she ever finishes, they say, the sea will stop.",
        "lore.realms_of_chaos.poseidon.8": "The water in the rune chambers is older than the towers. It remembers being rain.",
        "lore.realms_of_chaos.hades.1": "Nobody is kept in the Underworld. They simply cannot think of anywhere else to go.",
        "lore.realms_of_chaos.hades.2": "Charon was paid once for every soul. He has spent none of it, and will not say what he is saving for.",
        "lore.realms_of_chaos.hades.3": "The Lethe runs through the lowest passages. Those who drink of it fight on without knowing why.",
        "lore.realms_of_chaos.hades.4": "Shades do not hate the living. They are trying to remember what warmth was.",
        "lore.realms_of_chaos.hades.5": "Persephone planted the gardens in these halls. They flower in the dark, out of spite.",
        "lore.realms_of_chaos.hades.6": "Thanatos has never hurried, and has never been late.",
        "lore.realms_of_chaos.hades.7": "The Furies were sent after oath-breakers. Since the gates fell, everyone here is one.",
        "lore.realms_of_chaos.hades.8": "A soul warden carries its charges' wounds because it could not carry them in life.",
        "hud.realms_of_chaos.sentinel.disarmed": "Your weapon is knocked from your hand - it is in your pack",
        "hud.realms_of_chaos.sentinel.horn": "The %s sounds the alarm - more are coming!",
        "hud.realms_of_chaos.sentinel.silenced": "Silenced: your relic's move will not answer for %s s",
        "sidebar.realms_of_chaos.title": "Realms of Chaos",
        "sidebar.realms_of_chaos.on": "Sidebar on",
        "sidebar.realms_of_chaos.off": "Sidebar off",
        "sidebar.realms_of_chaos.passage": "Passage to floor %s",
        "sidebar.realms_of_chaos.passage.level": "Level %s of %s",
        "sidebar.realms_of_chaos.passage.seals": "Seals broken: %s / %s",
        "sidebar.realms_of_chaos.outside": "The world outside",
        "sidebar.realms_of_chaos.hub": "Hub of Realms",
        "sidebar.realms_of_chaos.floor": "Floor %s of %s",
        "sidebar.realms_of_chaos.trial.phase.first": "First phase: %s%%",
        "sidebar.realms_of_chaos.trial.phase.aegis": "Aegis: %s%%",
        "sidebar.realms_of_chaos.trial.phase.rage": "Enraged: %s%%",
        "sidebar.realms_of_chaos.trial.phase.wrath": "Wrath: %s%%",
        "sidebar.realms_of_chaos.trial.pylons": "Pylons standing: %s",
        "sidebar.realms_of_chaos.trial.ring": "Safe ring: %s blocks",
        "sidebar.realms_of_chaos.trial.stance.deflect": "It turns shots aside",
        "sidebar.realms_of_chaos.trial.stance.bristle": "Its guard lashes back",
        "sidebar.realms_of_chaos.hubtier": "Hub tier %s: %s",
        "sidebar.realms_of_chaos.signal": "Signal %s",
        "sidebar.realms_of_chaos.signal.scanning": "Scanning %s",
        "sidebar.realms_of_chaos.party": "Party of %s",
        "sidebar.realms_of_chaos.bounty": "%s: %s / %s",
        "sidebar.realms_of_chaos.bounty.done": "Done - %s: %s / %s",
        "sidebar.realms_of_chaos.bounty.claimed": "Claimed - %s",
        "screen.realms_of_chaos.home.sidebar.on": "Sidebar: on",
        "screen.realms_of_chaos.home.sidebar.off": "Sidebar: off",
        "screen.realms_of_chaos.home.sidebar.tip": "A panel at the side of the screen: where you are, the trial's phase, the hub, your signal, today's bounties",
        "hud.realms_of_chaos.trap.sprung.embers": "The room is a furnace!",
        "hud.realms_of_chaos.trap.sprung.sub.embers": "The %s runes light ONCE. Remember the order, then press it",
        "hud.realms_of_chaos.trap.status.embers": "Heat %s / %s  |  runes %s / %s",
        "hud.realms_of_chaos.trap.wrong.embers": "Wrong rune: the heat builds. The order is shown once more",
        "hud.realms_of_chaos.trap.full.embers": "The furnace is at its height - press the runes or hold on!",
        "hud.realms_of_chaos.trap.failed.embers": "The furnace lets you go. It will light again if you come back",
        "hud.realms_of_chaos.trap.sprung.gloom": "The dark closes in!",
        "hud.realms_of_chaos.trap.sprung.sub.gloom": "Watch the %s runes light, then press them BACKWARDS",
        "hud.realms_of_chaos.trap.status.gloom": "Gloom %s / %s  |  runes %s / %s",
        "hud.realms_of_chaos.trap.wrong.gloom": "Wrong rune: the dark thickens. Last rune first",
        "hud.realms_of_chaos.trap.full.gloom": "The dark is complete - press the runes or hold on!",
        "hud.realms_of_chaos.trap.failed.gloom": "The dark lets you go. It will close in again if you come back",
        "screen.realms_of_chaos.home.commission": "Commissions",
        "screen.realms_of_chaos.home.commission.tip": "Have the forge make a relic or a piece of armour. It takes a long time",
        "screen.realms_of_chaos.commission": "Commissions",
        "screen.realms_of_chaos.commission.intro": "The forge will make what the gods would otherwise have to be beaten for. It asks for a great deal of a tower's forge material, and for time: it works only while you are in the world.",
        "screen.realms_of_chaos.commission.idle": "Nothing is being made for you. One thing at a time.",
        "screen.realms_of_chaos.commission.working": "Being made: %s - about %s minutes of play left",
        "screen.realms_of_chaos.commission.ready": "Finished: %s",
        "screen.realms_of_chaos.commission.claim": "Take it",
        "screen.realms_of_chaos.commission.armour": "Armour of the old stories",
        "screen.realms_of_chaos.commission.relics": "Relics: %s",
        "screen.realms_of_chaos.commission.list": "Point at a thing to see what it costs. Choosing one hands the materials over and sets the forge to work.",
        "screen.realms_of_chaos.commission.cost": "Costs %s. Takes %s minutes of play",
        "screen.realms_of_chaos.commission.locked": "%s (not yet)",
        "screen.realms_of_chaos.commission.locked.tip": "The forge makes a god's relic only for someone who has stood on that god's floor",
        "hud.realms_of_chaos.commission.busy": "The forge is already making something for you",
        "hud.realms_of_chaos.commission.short": "Not enough: %s x %s needed",
        "hud.realms_of_chaos.commission.locked": "Reach that god's floor first",
        "hud.realms_of_chaos.commission.placed": "The forge sets to work on %s",
        "hud.realms_of_chaos.commission.done": "Your commission is finished",
        "hud.realms_of_chaos.commission.done.sub": "%s waits at the forge. Open the Codex and choose Commissions",
        "hud.realms_of_chaos.commission.taken": "You take %s from the forge",
        "lesser.realms_of_chaos.eris": "Eris, Strife",
        "lesser.realms_of_chaos.nemesis": "Nemesis, Retribution",
        "lesser.realms_of_chaos.tyche": "Tyche, Fortune",
        "lesser.realms_of_chaos.boreas": "Boreas, the North Wind",
        "lesser.realms_of_chaos.glaucus": "Glaucus of the Deep",
        "lesser.realms_of_chaos.leucothea": "Leucothea, the White Goddess",
        "lesser.realms_of_chaos.phorcys": "Phorcys, the Old One",
        "lesser.realms_of_chaos.ceto": "Ceto, Mother of Monsters",
        "lesser.realms_of_chaos.styx": "Styx, the Oath",
        "lesser.realms_of_chaos.lethe": "Lethe, Forgetting",
        "lesser.realms_of_chaos.achlys": "Achlys, the Death-Mist",
        "lesser.realms_of_chaos.geras": "Geras, Old Age",
        "hud.realms_of_chaos.lesser.holds": "%s holds this arena",
        "hud.realms_of_chaos.lesser.fallen": "%s falls",
        "hud.realms_of_chaos.lesser.gift": "%s lets fall %s",
        "titan.realms_of_chaos.hyperion": "Hyperion, the Fallen Light",
        "titan.realms_of_chaos.oceanus": "Oceanus, the Fallen Sea",
        "titan.realms_of_chaos.cronus": "Cronus, the Fallen King",
        "citadel.realms_of_chaos.halfkin": "Half-kin of %s",
        "citadel.realms_of_chaos.woken": "%s: the garrison stands to",
        "citadel.realms_of_chaos.woken.sub": "Half-kin hold the walls. On the roof of the keep, beside the keystone, %s waits",
        "citadel.realms_of_chaos.titan.fallen": "%s falls again",
        "hud.realms_of_chaos.tab.distance.rough": "about %s blocks",
        "hud.realms_of_chaos.tab.distance.exact": "%s blocks",
        "hud.realms_of_chaos.tab.lock": "Clean lock",
        "hud.realms_of_chaos.tab.surge": "Static surge",
        "hud.realms_of_chaos.tab.done": "Scan complete",
        "hud.realms_of_chaos.tab.here": "You are here",
        "hud.realms_of_chaos.tab.listening": "Listening for %s",
        "hud.realms_of_chaos.tab.signal": "Signal",
        "entity.realms_of_chaos.titan": "Fallen Titan",
        "titan.realms_of_chaos.move.solar_lance": "Solar Lance",
        "titan.realms_of_chaos.move.blinding_dawn": "Blinding Dawn",
        "titan.realms_of_chaos.move.falling_stars": "Falling Stars",
        "titan.realms_of_chaos.move.corona": "Corona",
        "titan.realms_of_chaos.move.undertow": "Undertow",
        "titan.realms_of_chaos.move.waterspouts": "Waterspouts",
        "titan.realms_of_chaos.move.tidal_wall": "Tidal Wall",
        "titan.realms_of_chaos.move.call_of_the_deep": "Call of the Deep",
        "titan.realms_of_chaos.move.scythe_sweep": "Scythe Sweep",
        "titan.realms_of_chaos.move.devour_time": "Devour Time",
        "titan.realms_of_chaos.move.rewind": "Rewind",
        "titan.realms_of_chaos.move.age_of_iron": "Age of Iron",
        "titan.realms_of_chaos.cue.solar_lance": "Solar Lance - step off the line of light",
        "titan.realms_of_chaos.cue.blinding_dawn": "Blinding Dawn - look away from Hyperion!",
        "titan.realms_of_chaos.cue.falling_stars": "Falling Stars - leave the burning rings",
        "titan.realms_of_chaos.cue.corona": "Corona - jump the rings of fire",
        "titan.realms_of_chaos.cue.undertow": "Undertow - you will be dragged in: be ready to run",
        "titan.realms_of_chaos.cue.waterspouts": "Waterspouts - move off the bubbles",
        "titan.realms_of_chaos.cue.tidal_wall": "Tidal Wall - find the gap",
        "titan.realms_of_chaos.cue.call_of_the_deep": "Call of the Deep - half-kin rise from the sea",
        "titan.realms_of_chaos.cue.scythe_sweep": "Scythe Sweep - get behind him",
        "titan.realms_of_chaos.cue.devour_time": "Devour Time - time slows for you, not for him",
        "titan.realms_of_chaos.cue.rewind": "Rewind - you will be thrown back to where you stood",
        "titan.realms_of_chaos.cue.age_of_iron": "Age of Iron - the roof splits: step off the lines",
        "titan.realms_of_chaos.phase2": "Its power wakes - it has a new move",
        "titan.realms_of_chaos.phase3": "Its fury - faster, stronger, and its greatest move",
        "hud.realms_of_chaos.trap.sprung": "The room floods!",
        "hud.realms_of_chaos.trap.sprung.sub": "Watch the %s runes light, then press them in that order",
        "hud.realms_of_chaos.trap.status": "Water %s / %s  |  runes %s / %s",
        "hud.realms_of_chaos.trap.wrong": "Wrong rune: the water leaps up. Watch the order again",
        "hud.realms_of_chaos.trap.full": "The room is full - press the runes or hold on!",
        "hud.realms_of_chaos.trap.failed": "The room lets go of you. It will flood again if you come back",
        "hud.realms_of_chaos.trap.solved": "The room drains",
        "hud.realms_of_chaos.trap.solved.sub": "Its doors stand open",
        "hud.realms_of_chaos.trap.prize": "The chamber gives up %s x %s",
        "hud.realms_of_chaos.trap.dormant": "The runes sleep until someone stands in the middle of the room",
        # ---- a trial's three phases
        "boss.realms_of_chaos.phase.aegis": "%s - Aegis",
        "boss.realms_of_chaos.phase.rage": "%s - Enraged",
        "boss.realms_of_chaos.phase.wrath": "%s - Wrath",
        "hud.realms_of_chaos.aegis": "%s raises its aegis",
        "hud.realms_of_chaos.aegis.sub": "Break the %s shield pylons: right-click them",
        "hud.realms_of_chaos.aegis.left": "%s pylons still shield %s",
        "hud.realms_of_chaos.aegis.shielded": "Shielded! %s pylons still stand: right-click them",
        "hud.realms_of_chaos.aegis.broken": "The aegis breaks",
        "hud.realms_of_chaos.aegis.faded": "The aegis fades",
        "hud.realms_of_chaos.aegis.broken.sub": "%s can be hurt again",
        "hud.realms_of_chaos.pylon.cracks": "The pylon cracks: %s more",
        "hud.realms_of_chaos.collapse": "The floor is lost here - move in!",
        "hud.realms_of_chaos.strike.zeus": "Lightning gathers - leave the rings!",
        "hud.realms_of_chaos.strike.poseidon": "The deep surges - leave the rings!",
        "hud.realms_of_chaos.strike.hades": "Burning stone falls - leave the rings!",
        "hud.realms_of_chaos.adapt.deflect": "%s has read your shots: it turns them aside and closes in. Fight it hand to hand",
        "hud.realms_of_chaos.adapt.bristle": "%s braces against your blade: its guard lashes back. Strike and step away, or fight from range",
        "hud.realms_of_chaos.adapt.gear": "%s has measured your arms. The trial rises to match: %s",
        "hud.realms_of_chaos.might.even": "Even",
        "hud.realms_of_chaos.might.armed": "Well armed",
        "hud.realms_of_chaos.might.mighty": "Mighty",
        "hud.realms_of_chaos.might.legendary": "Legendary",
        "screen.realms_of_chaos.altar.strength": "Your arms: %s. This god will have %s times its health",
        "screen.realms_of_chaos.altar.phases": "Three phases: itself, then at 65% its aegis (break the pylons), then at 30% its wrath",
        # ---- the compass scans
        "hud.realms_of_chaos.scan.mode": "The compass now looks for %s",
        "hud.realms_of_chaos.scan.mode.hub": "the way to the hub",
        "hud.realms_of_chaos.scan.mode.keystones": "keystone citadels",
        "hud.realms_of_chaos.scan.mode.network": "relays and waygates",
        "hud.realms_of_chaos.scan.nowhere": "Nothing to scan for here: the compass reads the world outside",
        "hud.realms_of_chaos.scan.nocompass": "You have no Compass of Chaos",
        "hud.realms_of_chaos.scan.settling": "The needle is settling: %s s",
        "hud.realms_of_chaos.scan.nothing": "Nothing to find: %s",
        "hud.realms_of_chaos.scan.start": "Scanning for %s: %s s",
        "hud.realms_of_chaos.scan.target.relay": "Relay Tower",
        "hud.realms_of_chaos.scan.target.waygate": "Waygate (%s, %s)",
        "hud.realms_of_chaos.scan.line": "%s  %s  ~%s blocks  |  signal %s  |  %s s",
        "hud.realms_of_chaos.scan.line.lock": "CLEAN LOCK  %s  %s  %s blocks  |  signal %s  |  %s s",
        "hud.realms_of_chaos.scan.line.surge": "STATIC SURGE - the needle spins  |  signal %4$s  |  %5$s s",
        "hud.realms_of_chaos.scan.line.waiting": "Scanning for %s  |  signal %s  |  %s s",
        "hud.realms_of_chaos.scan.done": "Scan over. Best reading (rough): %s, about %s blocks to the %s. Clean locks: %s",
        "hud.realms_of_chaos.scan.done.lock": "Scan over. Clean lock: %s, %s blocks to the %s. Clean locks: %s",
        "hud.realms_of_chaos.scan.done.none": "Scan over: nothing found for %s",
        "hud.realms_of_chaos.recall.outside": "The hub cannot be reached from here. Walk through a Ruined Gate or channel at a Waygate Obelisk",
        # ---- the field network
        "hud.realms_of_chaos.bag.nothing": "Nothing in the bag to set down",
        "hud.realms_of_chaos.bag.given": "You have been handed a Field Tower Bag. The compass scans now; a Waygate Obelisk takes you to the hub",
        "hud.realms_of_chaos.field.placed.relay": "Relay Tower raised: it steadies every compass within %s blocks",
        "hud.realms_of_chaos.field.placed.jammer": "Signal Jammer set: it spoils other players' scans within %s blocks",
        "hud.realms_of_chaos.field.placed.waygate": "Waygate Obelisk raised. Right-click it to travel",
        "hud.realms_of_chaos.field.status.relay": "A Relay Tower: it steadies every compass within %s blocks",
        "hud.realms_of_chaos.field.status.relay.mine": "Your Relay Tower: it steadies every compass within %s blocks",
        "hud.realms_of_chaos.field.status.jammer": "A Signal Jammer: it spoils your scans within %s blocks, unless you are in its owner's party",
        "hud.realms_of_chaos.field.status.jammer.mine": "Your Signal Jammer: it spoils other players' scans within %s blocks",
        "hud.realms_of_chaos.waygate.found": "Waygate found: you can travel here from any other",
        "hud.realms_of_chaos.waygate.far": "Stand by the obelisk",
        "hud.realms_of_chaos.waygate.unknown": "That waygate is gone, or you have never touched it",
        "hud.realms_of_chaos.waygate.cost": "The journey costs %s x %s",
        "hud.realms_of_chaos.waygate.moved": "You moved: the waygate lets go",
        "hud.realms_of_chaos.waygate.hurt": "You were hit: the waygate lets go",
        "hud.realms_of_chaos.waygate.unpaid": "You can no longer pay for the journey",
        "hud.realms_of_chaos.waygate.channel": "Channelling %s",
        "hud.realms_of_chaos.waygate.arrived": "Arrived at the waygate at %s",
        "screen.realms_of_chaos.waygate": "Waygate Obelisk",
        "screen.realms_of_chaos.waygate.how": "Choose where to go, then stand still for %s seconds. Moving or being hit breaks the channel",
        "screen.realms_of_chaos.waygate.cost": "Each journey costs %s x %s, taken when you arrive",
        "screen.realms_of_chaos.waygate.free": "The hub has made the waygates free",
        "screen.realms_of_chaos.waygate.hub": "To the Hub of Realms",
        "screen.realms_of_chaos.waygate.unattuned": "No Ruined Gate knows you yet: the hub is closed to you",
        "screen.realms_of_chaos.waygate.to": "To the waygate at %s, %s, %s",
        "screen.realms_of_chaos.waygate.alone": "Touch another obelisk and it will be listed here",
        # ---- the hub's tiers and the bounty board
        "screen.realms_of_chaos.hub": "Hub Command",
        "screen.realms_of_chaos.hub.tier": "The Hub of Realms stands at tier %s of %s: %s",
        "screen.realms_of_chaos.hub.perk.have": "%s: %s",
        "screen.realms_of_chaos.hub.perk.next": "%s (not yet): %s",
        "screen.realms_of_chaos.hub.asks": "%s asks for:",
        "screen.realms_of_chaos.hub.cost": "%s: %s / %s",
        "screen.realms_of_chaos.hub.deposit": "Hand over what I carry",
        "screen.realms_of_chaos.hub.deposit.tip": "Takes whatever the next tier still needs from your pack. Anyone can add to the pile",
        "screen.realms_of_chaos.hub.complete": "The hub stands at its highest tier",
        "screen.realms_of_chaos.hub.bounties": "Bounty Board",
        "hubtier.realms_of_chaos.1": "Waystation",
        "hubtier.realms_of_chaos.2": "Outpost",
        "hubtier.realms_of_chaos.3": "Sanctuary",
        "hubtier.realms_of_chaos.4": "Citadel",
        "hubtier.realms_of_chaos.5": "Throne of Realms",
        "hubtier.realms_of_chaos.1.perk": "A place to stand between the towers",
        "hubtier.realms_of_chaos.2.perk": "Compasses read steadier. The hub mends whoever stands in it. The Bounty Board opens",
        "hubtier.realms_of_chaos.3.perk": "Field Tower Bags hold six rows. The hub lifts every harm you carry in",
        "hubtier.realms_of_chaos.4.perk": "Waygates channel in three seconds. The compass needle rolls every two",
        "hubtier.realms_of_chaos.5.perk": "Waygates ask for nothing. The steadiest compass there is",
        "hubtier.realms_of_chaos.cost.iron": "Iron ingots",
        "hubtier.realms_of_chaos.cost.gold": "Gold ingots",
        "hubtier.realms_of_chaos.cost.amethyst": "Amethyst shards",
        "hubtier.realms_of_chaos.cost.diamond": "Diamonds",
        "hubtier.realms_of_chaos.cost.pearl": "Ender pearls",
        "hubtier.realms_of_chaos.cost.netherite": "Netherite ingots",
        "hubtier.realms_of_chaos.cost.material_1": "Forge materials from floors 1-5 (any tower)",
        "hubtier.realms_of_chaos.cost.material_2": "Forge materials from floors 6-10 (any tower)",
        "hubtier.realms_of_chaos.cost.material_3": "Forge materials from floors 11-14 (any tower)",
        "hubtier.realms_of_chaos.cost.material_4": "Summit essences (any tower)",
        "hud.realms_of_chaos.hubtier": "The hub rises: %s",
        "screen.realms_of_chaos.bounty": "Bounty Board",
        "screen.realms_of_chaos.bounty.closed": "The board opens when the hub reaches its second tier: %s",
        "screen.realms_of_chaos.bounty.intro": "Three bounties a day, the same for everyone. Each of you earns them for yourself",
        "screen.realms_of_chaos.bounty.line": "%s: %s / %s. Pays %s x %s",
        "screen.realms_of_chaos.bounty.line.done": "Done - %s: %s / %s. Claim %s x %s",
        "screen.realms_of_chaos.bounty.line.claimed": "Claimed - %s",
        "screen.realms_of_chaos.bounty.claim": "Claim: %s",
        "bounty.realms_of_chaos.sentinels": "Fell %s sentinels",
        "bounty.realms_of_chaos.seals": "Break %s passage seals",
        "bounty.realms_of_chaos.gods": "Win %s trials",
        "bounty.realms_of_chaos.pylons": "Shatter %s shield pylons",
        "bounty.realms_of_chaos.caches": "Open %s treasure caches",
        "hud.realms_of_chaos.bounty.done": "Bounty done: %s. Claim it at the Bounty Board",
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
        "block.realms_of_chaos.chaos_forge": "Forge of Chaos",
        "block.realms_of_chaos.passage_cache": "Treasure Cache",
        "hud.realms_of_chaos.tip": "Tip",
        "hud.realms_of_chaos.upheaval": "The floor erupts!",
        "hud.realms_of_chaos.laststand": "%s's last stand",
        "hud.realms_of_chaos.laststand.sub": "Its wrath begins. The floor will be lost from the wall inward",
        "hud.realms_of_chaos.passage.level": "Level %s of %s - break the %s seals",
        "hud.realms_of_chaos.passage.level.open": "Level %s of %s - the gate stands open",
        "hud.realms_of_chaos.passage.open.deeper": "The gate leads up to level %s of %s",
        "hud.realms_of_chaos.passage.room.arena": "An arena - an elite and its pack guard the cache",
        "hud.realms_of_chaos.passage.room.garden": "A garden",
        "hud.realms_of_chaos.passage.room.library": "A library - the table still works",
        "hud.realms_of_chaos.passage.room.pool": "Pools",
        "hud.realms_of_chaos.passage.room.exit.deeper": "The gate up to the next level stands open",
        "menu.realms_of_chaos.passage.kind.garden": "Garden",
        "menu.realms_of_chaos.passage.kind.library": "Library",
        "menu.realms_of_chaos.passage.kind.pool": "Pools",
        "menu.realms_of_chaos.passage.kind.arena": "Arena",
        "menu.realms_of_chaos.passage.info.level": "Level %s of %s",
        "screen.realms_of_chaos.home": "Codex of Chaos",
        "screen.realms_of_chaos.home.thanks": "Thank you for using our mod. - TheFarawayDev",
        "screen.realms_of_chaos.home.awake": "Gods awake: %s of 45",
        "screen.realms_of_chaos.home.tower": "%s: floor %s",
        "screen.realms_of_chaos.home.next": "What now?",
        "screen.realms_of_chaos.home.towers": "Towers and travel",
        "screen.realms_of_chaos.home.towers.tip": "Every floor of every tower, and travel to the ones you have reached",
        "screen.realms_of_chaos.home.chronicle": "The Oracle's Chronicle",
        "screen.realms_of_chaos.home.chronicle.tip": "The story so far",
        "screen.realms_of_chaos.home.handbook": "Handbook",
        "screen.realms_of_chaos.home.handbook.tip": "How everything works",
        "screen.realms_of_chaos.home.party": "Party",
        "screen.realms_of_chaos.home.party.tip": "Climb together",
        "screen.realms_of_chaos.home.forge": "Forge of Chaos",
        "screen.realms_of_chaos.home.forge.tip": "For the relic in your hand",
        "screen.realms_of_chaos.home.hub": "Go to the hub",
        "screen.realms_of_chaos.home.leave": "Leave the realms",
        "screen.realms_of_chaos.home.map": "Passage map",
        "screen.realms_of_chaos.close": "Close",
        "screen.realms_of_chaos.back": "Back",
        "screen.realms_of_chaos.handbook": "Climber's Handbook",
        "screen.realms_of_chaos.handbook.intro": "Choose a topic.",
        "screen.realms_of_chaos.chronicle": "The Oracle's Chronicle",
        "screen.realms_of_chaos.chronicle.count": "%s of %s chapters written",
        "screen.realms_of_chaos.chronicle.locked": "Not yet written: %s",
        "screen.realms_of_chaos.chronicle.none": "Nothing more to read yet",
        "screen.realms_of_chaos.chapter.take": "Take as a book",
        "screen.realms_of_chaos.altar": "Trial Altar",
        "screen.realms_of_chaos.altar.floor": "%s - floor %s of 15 - %s",
        "screen.realms_of_chaos.altar.rank": "%s. Rank %s of 15",
        "screen.realms_of_chaos.altar.move": "Signature move: %s. %s",
        "screen.realms_of_chaos.altar.drops": "Drops: %s x %s, and %s the first time you beat it",
        "screen.realms_of_chaos.altar.scaling": "The trial is harder for every extra challenger, and rises to match your weapons, enchantments and armour",
        "screen.realms_of_chaos.altar.state.ready": "The altar is ready",
        "screen.realms_of_chaos.altar.state.defeated": "You have beaten this god",
        "screen.realms_of_chaos.altar.state.underway": "A trial is underway on this floor",
        "screen.realms_of_chaos.altar.state.locked": "This floor is not yours to challenge yet",
        "screen.realms_of_chaos.altar.state.peaceful": "The gods do not answer on Peaceful",
        "screen.realms_of_chaos.altar.state.complete": "Every god of this line has fallen to you",
        "screen.realms_of_chaos.altar.begin": "Begin the trial",
        "screen.realms_of_chaos.altar.ascend": "Go up",
        "screen.realms_of_chaos.altar.rematch": "Rematch",
        "screen.realms_of_chaos.party": "Party",
        "screen.realms_of_chaos.party.member": "%s - %s",
        "screen.realms_of_chaos.party.floors": "floors %s / %s / %s",
        "screen.realms_of_chaos.party.invite": "Invite %s",
        "screen.realms_of_chaos.party.refresh": "Refresh",
        "hud.realms_of_chaos.cache.taken": "You have already taken your share of this cache",
        "hud.realms_of_chaos.cache.loot": "Your share of the treasure: %s stacks",
        "hud.realms_of_chaos.cache.empty": "This cache holds nothing here",
        "hud.realms_of_chaos.passage.rearmed": "The passage has sealed itself again for you",
        "hud.realms_of_chaos.adapt.party": "The trial rises to meet %s challengers",
        "hud.realms_of_chaos.adapt.mercy": "The tower relents: %s is weakened",
        "hud.realms_of_chaos.party.invited": "%s invites you to their party. Open the Codex and choose Party, or run /roc party accept",
        "hud.realms_of_chaos.party.sent": "Invitation sent to %s",
        "hud.realms_of_chaos.party.joined": "%s joined the party",
        "hud.realms_of_chaos.party.left": "%s left the party",
        "hud.realms_of_chaos.party.full": "The party is full (4)",
        "hud.realms_of_chaos.party.none": "Nobody has invited you",
        "hud.realms_of_chaos.party.self": "You cannot invite yourself",
        "hud.realms_of_chaos.party.already": "%s is already in your party",
        "hud.realms_of_chaos.party.alone": "You are not in a party",
        "hud.realms_of_chaos.party.floor": "Travelling with your party: floor %s",
        "menu.realms_of_chaos.party": "Party",
        "menu.realms_of_chaos.party.open": "Party",
        "menu.realms_of_chaos.party.open.lore": "Climb together: invite friends and travel as one",
        "menu.realms_of_chaos.party.size": "%s of 4 in your party",
        "menu.realms_of_chaos.party.solo": "You are climbing alone",
        "menu.realms_of_chaos.party.member": "In your party",
        "menu.realms_of_chaos.party.you": "You",
        "menu.realms_of_chaos.party.progress": "%s: floor %s",
        "menu.realms_of_chaos.party.invite": "Click to invite",
        "menu.realms_of_chaos.party.pending": "%s has invited you",
        "menu.realms_of_chaos.party.accept": "Accept the invitation",
        "menu.realms_of_chaos.party.decline": "Decline",
        "menu.realms_of_chaos.party.leave": "Leave the party",
        "menu.realms_of_chaos.party.rule1": "A party enters a tower on its highest member's floor",
        "menu.realms_of_chaos.party.rule2": "Everyone keeps their own progress: you climb past a floor",
        "menu.realms_of_chaos.party.rule3": "only once you have beaten its god - with or without help",
        "menu.realms_of_chaos.party.rule4": "Everyone who fights gets their own loot",
        "block.realms_of_chaos.gate_pedestal": "Keystone Pedestal",
        "block.realms_of_chaos.oracle": "The Oracle's Flame",
        "ruin.realms_of_chaos.gate": "Ruined Gate",
        "item.realms_of_chaos.keystone.tooltip": "Set it in the arch of a Ruined Gate: right-click the pedestal",
        "item.realms_of_chaos.seeker.tooltip": "Right-click: points to the nearest %s",
        "hud.realms_of_chaos.point": "%s: %s blocks to the %s",
        "hud.realms_of_chaos.point.here": "%s is right here",
        "hud.realms_of_chaos.point.overworld": "It only finds its way in the Overworld",
        "hud.realms_of_chaos.access.closed": "The realms are closed to you. Follow the Compass of Chaos to a Ruined Gate",
        "hud.realms_of_chaos.access.attuned": "Attuned",
        "hud.realms_of_chaos.access.attuned.sub": "The gate knows you now. A Waygate Obelisk will carry you back here",
        "hud.realms_of_chaos.pedestal.set": "The %s is set: %s of 3",
        "hud.realms_of_chaos.pedestal.already": "That keystone is already set",
        "hud.realms_of_chaos.pedestal.need": "The gate sleeps. Still missing: %s",
        "hud.realms_of_chaos.pedestal.awake": "The gate is awake. Step through",
        "hud.realms_of_chaos.pedestal.nogate": "There is no gate here for the pedestal to wake",
        "hud.realms_of_chaos.gate.wakes": "The gate wakes",
        "hud.realms_of_chaos.gate.wakes.sub": "Step through to the Hub of Realms",
        "hud.realms_of_chaos.story.new": "A new chapter is written: %s",
        "hud.realms_of_chaos.story.given": "The Oracle hands you: %s",
        "menu.realms_of_chaos.story": "The Oracle's Chronicle",
        "menu.realms_of_chaos.story.locked": "Not yet written",
        "menu.realms_of_chaos.story.read": "Click to take this chapter as a book",
        "menu.realms_of_chaos.story.unlock.attuned": "Pass through a Ruined Gate",
        "menu.realms_of_chaos.story.unlock.count": "Wake %s gods of the %s",
        "menu.realms_of_chaos.story.unlock.all": "Wake all three kings",
        "menu.realms_of_chaos.story.guide": "What now?",
        "menu.realms_of_chaos.story.guide.gate": "Follow the Compass of Chaos to a Ruined Gate in the Overworld",
        "menu.realms_of_chaos.story.guide.keystones": "Its seekers lead to three keystones. Set them in the arch",
        "menu.realms_of_chaos.story.guide.next": "%s: %s waits on floor %s",
        "menu.realms_of_chaos.story.guide.done": "%s: every god is awake",
        "menu.realms_of_chaos.story.guide.finished": "All three towers are yours. Read the last chapter",
        "menu.realms_of_chaos.story.open": "The Oracle's Chronicle",
        "menu.realms_of_chaos.story.open.lore": "The story so far, and what to do next",
        "menu.realms_of_chaos.story.count": "%s of %s chapters written",
        "entity.realms_of_chaos.pilgrim": "Pilgrim",
        "entity.realms_of_chaos.echo": "Echo of %s",
        "block.realms_of_chaos.passage_seal": "Passage Seal",
        "block.realms_of_chaos.passage_shrine": "Passage Shrine",
        "hud.realms_of_chaos.passage.title": "Passage %s - to floor %s",
        "hud.realms_of_chaos.passage.sub": "Break the %s seals to open the gate",
        "hud.realms_of_chaos.passage.sub.open": "The gate at the far end stands open",
        "hud.realms_of_chaos.passage.bar": "%s - Passage %s - Level %s of %s - Seals %s / %s",
        "hud.realms_of_chaos.passage.seal.broken": "Seal broken: %s left",
        "hud.realms_of_chaos.passage.open": "The way is open",
        "hud.realms_of_chaos.passage.open.sub": "The gate to floor %s awaits",
        "hud.realms_of_chaos.passage.sealed": "%s seals still hold this gate shut",
        "hud.realms_of_chaos.passage.guarded": "Its guardians still stand: %s left",
        "hud.realms_of_chaos.passage.room.seal": "A sealed chamber - its guardians wake",
        "hud.realms_of_chaos.passage.room.seal.done": "This chamber's seal is broken",
        "hud.realms_of_chaos.passage.room.treasure": "A treasure room",
        "hud.realms_of_chaos.passage.room.shrine": "A shrine - right-click it for a blessing",
        "hud.realms_of_chaos.passage.room.trap": "A rune chamber. Step into the middle and it floods",
        "hud.realms_of_chaos.passage.room.exit.sealed": "The gate - still sealed",
        "hud.realms_of_chaos.passage.room.exit.open": "The gate stands open",
        "hud.realms_of_chaos.passage.compass.seal": "Nearest seal: %s blocks to the %s",
        "hud.realms_of_chaos.passage.compass.gate": "The gate: %s blocks to the %s",
        "hud.realms_of_chaos.passage.dir.north": "north", "hud.realms_of_chaos.passage.dir.north_east": "north-east",
        "hud.realms_of_chaos.passage.dir.east": "east", "hud.realms_of_chaos.passage.dir.south_east": "south-east",
        "hud.realms_of_chaos.passage.dir.south": "south", "hud.realms_of_chaos.passage.dir.south_west": "south-west",
        "hud.realms_of_chaos.passage.dir.west": "west", "hud.realms_of_chaos.passage.dir.north_west": "north-west",
        "hud.realms_of_chaos.shrine.resting": "The shrine is resting: about %s min",
        "hud.realms_of_chaos.shrine.blessed.zeus": "Olympus favours you: healed, swift and strong",
        "hud.realms_of_chaos.shrine.blessed.poseidon": "The deep favours you: healed, hardened and mending",
        "hud.realms_of_chaos.shrine.blessed.hades": "The underworld favours you: healed, fireproof and shielded",
        "hud.realms_of_chaos.finish.masterwork": "Masterwork! Tempered: +2% damage, +60 weapon XP",
        "hud.realms_of_chaos.finish.masterwork.sub": "Masterwork: this star is tempered",
        "hud.realms_of_chaos.finish.fine": "Fine work: +30 weapon XP",
        "hud.realms_of_chaos.finish.fine.sub": "Fine work",
        "hud.realms_of_chaos.finish.rough": "Rough work: forged, but the finish is plain",
        "hud.realms_of_chaos.finish.rough.sub": "Rough work",
        "hud.realms_of_chaos.finish.tempered": "Tempered: +%s%% damage in all",
        "hud.realms_of_chaos.finish.temper.failed": "The finish did not take. Only a masterwork tempers",
        "menu.realms_of_chaos.passage": "Passage to floor %s",
        "menu.realms_of_chaos.passage.unexplored": "Unexplored",
        "menu.realms_of_chaos.passage.unexplored.lore": "A door leads here",
        "menu.realms_of_chaos.passage.here": "You are here",
        "menu.realms_of_chaos.passage.door": "Door to the %s",
        "menu.realms_of_chaos.passage.kind.start": "Where you came in",
        "menu.realms_of_chaos.passage.kind.exit": "The gate",
        "menu.realms_of_chaos.passage.kind.seal": "Sealed chamber",
        "menu.realms_of_chaos.passage.kind.seal.broken": "Chamber: seal broken",
        "menu.realms_of_chaos.passage.kind.treasure": "Treasure room",
        "menu.realms_of_chaos.passage.kind.shrine": "Shrine",
        "menu.realms_of_chaos.passage.kind.trap": "Trapped room",
        "menu.realms_of_chaos.passage.kind.hall": "Corridors",
        "menu.realms_of_chaos.passage.kind.room": "Room",
        "menu.realms_of_chaos.passage.info": "Passage to floor %s",
        "menu.realms_of_chaos.passage.info.seals": "Seals broken: %s / %s",
        "menu.realms_of_chaos.passage.info.rooms": "Rooms explored: %s / %s",
        "menu.realms_of_chaos.passage.info.compass": "The Compass of Chaos points to the nearest seal",
        "menu.realms_of_chaos.passage.codex": "Codex of Chaos",
        "menu.realms_of_chaos.passage.codex.lore": "Open the full Codex",
        "menu.realms_of_chaos.seal": "Break the Seal",
        "menu.realms_of_chaos.seal.title": "Passage Seal",
        "menu.realms_of_chaos.seal.lore": "A hammer runs along the bar below",
        "menu.realms_of_chaos.seal.lore2": "Click when it is on the mark. A miss stings",
        "menu.realms_of_chaos.seal.cracks": "Cracks: %s / %s",
        "menu.realms_of_chaos.seal.strike": "Strike!",
        "menu.realms_of_chaos.seal.perfect": "A perfect strike: two cracks",
        "menu.realms_of_chaos.seal.good": "A good strike: one crack",
        "menu.realms_of_chaos.seal.miss": "Missed - the seal bites back",
        "menu.realms_of_chaos.strike.hammer": "The hammer",
        "menu.realms_of_chaos.strike.hammer.lore": "Strike when it is on the yellow mark",
        "menu.realms_of_chaos.strike.perfect": "The mark: a perfect strike",
        "menu.realms_of_chaos.strike.good": "Beside the mark: a good strike",
        "menu.realms_of_chaos.strike.miss": "Off the mark: a miss",
        "menu.realms_of_chaos.strike.perfect.hit": "Perfect strike!",
        "menu.realms_of_chaos.strike.good.hit": "Good strike",
        "menu.realms_of_chaos.strike.miss.hit": "Missed",
        "menu.realms_of_chaos.forge.strike": "Strike!",
        "menu.realms_of_chaos.forge.strike.lore": "Click when the hammer below is on the yellow mark",
        "menu.realms_of_chaos.forge.strike.left": "%s strikes to go",
        "menu.realms_of_chaos.forge.strike.hint": "Forging is struck: three strikes, timed on the bar",
        "menu.realms_of_chaos.forge.temper": "Temper",
        "menu.realms_of_chaos.forge.temper.have": "Tempered stars: %s of %s (+%s%% damage)",
        "menu.realms_of_chaos.forge.temper.full": "Every star is tempered",
        "menu.realms_of_chaos.forge.temper.lore": "Strike a star again: a masterwork tempers it (+2% damage)",
        "tooltip.realms_of_chaos.temper": "Tempered x%s (+%s%% damage)",
        "block.realms_of_chaos.gate_ascend": "Gate of Ascent",
        "kind.realms_of_chaos.blade": "Blade", "kind.realms_of_chaos.dagger": "Dagger",
        "kind.realms_of_chaos.polearm": "Polearm", "kind.realms_of_chaos.hammer": "Heavy weapon",
        "kind.realms_of_chaos.scythe": "Scythe", "kind.realms_of_chaos.staff": "Staff",
        "kind.realms_of_chaos.bow": "Bow", "kind.realms_of_chaos.thrown": "Thrown weapon",
        "kind.realms_of_chaos.relic": "Relic",
        "kind.realms_of_chaos.blade.use": "A balanced blade",
        "kind.realms_of_chaos.dagger.use": "Fast strikes",
        "kind.realms_of_chaos.polearm.use": "Long reach",
        "kind.realms_of_chaos.hammer.use": "Slow, crushing blows",
        "kind.realms_of_chaos.scythe.use": "Every hit sweeps",
        "kind.realms_of_chaos.staff.use": "Right-click: cast a bolt",
        "kind.realms_of_chaos.bow.use": "Right-click: loose an arrow",
        "kind.realms_of_chaos.thrown.use": "Right-click: throw",
        "kind.realms_of_chaos.relic.use": "Right-click: invoke its power around you",
        "tooltip.realms_of_chaos.level": "Level %s of 10  %s  %s / %s XP",
        "tooltip.realms_of_chaos.level.max": "Level 10 (max)",
        "tooltip.realms_of_chaos.stars": "Forged %s",
        "tooltip.realms_of_chaos.bonus": "+%s%% damage from level and forging",
        "tooltip.realms_of_chaos.hit": "On hit: %s",
        "tooltip.realms_of_chaos.flourish": "Every fourth hit in a row is a flourish (+50%)",
        "tooltip.realms_of_chaos.move": "Right-click: %s",
        "tooltip.realms_of_chaos.move.sneak": "Sneak + right-click: %s",
        "tooltip.realms_of_chaos.perk.empowered": "Empowered: the move hits 50% harder",
        "tooltip.realms_of_chaos.perk.awakened": "Awakened: the move echoes a second time",
        "tooltip.realms_of_chaos.perk.locked": "(forge %s stars)",
        "hud.realms_of_chaos.move.cast": "%s",
        "hud.realms_of_chaos.move.echo": "%s echoes",
        "hud.realms_of_chaos.move.wait": "%s is ready in %s s",
        "hud.realms_of_chaos.move.notarget": "%s needs a foe in sight",
        "hud.realms_of_chaos.move.windup": "%s readies %s!",
        "hud.realms_of_chaos.flourish": "Flourish!",
        "hud.realms_of_chaos.enraged": "%s is enraged",
        "hud.realms_of_chaos.rematch": "Rematch",
        "hud.realms_of_chaos.rematch.sub": "%s rises again",
        "hud.realms_of_chaos.infused": "%s drinks your experience: +%s XP",
        "hud.realms_of_chaos.infuse.none": "You have no experience levels to give",
        "hud.realms_of_chaos.forged.empowered": "Empowered: %s hits 50%% harder",
        "hud.realms_of_chaos.forged.awakened": "Awakened: %s now echoes",
        "hud.realms_of_chaos.forged.star": "+10%% damage, faster recharge",
        "menu.realms_of_chaos.forge.stars": "%s",
        "menu.realms_of_chaos.forge.star.next": "Next to forge",
        "menu.realms_of_chaos.forge.move": "Signature move: %s",
        "menu.realms_of_chaos.forge.preview": "What the next star gives",
        "menu.realms_of_chaos.forge.preview.damage": "Damage bonus: +%s%% -> +%s%%",
        "menu.realms_of_chaos.forge.preview.recharge": "Recharge: %s s -> %s s",
        "menu.realms_of_chaos.forge.preview.max": "Nothing left to forge: this relic is complete",
        "menu.realms_of_chaos.forge.guide": "Forge materials",
        "menu.realms_of_chaos.forge.guide.line": "%s x %s - %s (floors %s)",
        "menu.realms_of_chaos.forge.guide.rematch": "Rematch a fallen god at its altar for more",
        "menu.realms_of_chaos.forge.guide.cost": "%s x %s",
        "menu.realms_of_chaos.forge.move.damage": "%s damage - recharges in %s s",
        "menu.realms_of_chaos.forge.infuse": "Infuse experience",
        "menu.realms_of_chaos.forge.infuse.lore": "Click: give 1 of your levels (+%s weapon XP)",
        "menu.realms_of_chaos.forge.infuse.lore2": "Shift-click: give 5 levels",
        "menu.realms_of_chaos.forge.infuse.have": "You have %s levels",
        "menu.realms_of_chaos.forge.infuse.max": "This relic has reached level 10",
        "menu.realms_of_chaos.forge.close": "Step back",
        "menu.realms_of_chaos.forge.blocked.level": "The relic must reach level %s first",
        "menu.realms_of_chaos.forge.blocked.cost": "You do not have the materials yet",
        "menu.realms_of_chaos.god.move": "Signature move: %s",
        "menu.realms_of_chaos.god.drops": "Drops",
        "menu.realms_of_chaos.god.drops.relic": "%s (first victory)",
        "menu.realms_of_chaos.god.drops.material": "%s x %s",
        "menu.realms_of_chaos.altar.rematch": "Rematch",
        "menu.realms_of_chaos.altar.rematch.lore": "Fight %s again for forge materials",
        "menu.realms_of_chaos.altar.rematch.busy": "A trial is already underway on this floor",
        "hud.realms_of_chaos.levelup": "%s reached level %s!",
        "tarot.realms_of_chaos.single": "Right-click to play. The card is spent",
        "tarot.realms_of_chaos.devil.warn": "Clears your inventory! Hold the chosen item in your off hand",
        "hud.realms_of_chaos.devil.need": "The Devil wants an offering: hold the item you choose in your off hand",
        "hud.realms_of_chaos.devil.start": "Everything you pick up becomes %s",
        "hud.realms_of_chaos.devil.end": "The Devil's bargain ends",
        "hud.realms_of_chaos.fool.none": "The Fool wanders, but finds nowhere to go",
        "hud.realms_of_chaos.fool.found": "The Fool has brought you somewhere",
        "hud.realms_of_chaos.moon.start": "The Moon conjures %s illusions: slay them all",
        "hud.realms_of_chaos.moon.left": "%s illusions remain",
        "hud.realms_of_chaos.moon.done": "The illusions are gone. The Moon rewards you",
        "hud.realms_of_chaos.moon.failed": "The illusions fade away",
        "hud.realms_of_chaos.hermit.enter": "Your Hermitage",
        "hud.realms_of_chaos.hermit.sub": "Nobody else can come here. Play the card or step through the gate to return",
        "entity.realms_of_chaos.illusion": "Illusion",
        "menu.realms_of_chaos.forge.from": "Dropped by the gods of %s",
        "menu.realms_of_chaos.forge.awakened": "Awakened: fully forged",
        "item.realms_of_chaos.tarot_deck": "Deck of Fate",
        "item.realms_of_chaos.tarot_deck.lore1": "Right-click: draw a card at random",
        "item.realms_of_chaos.tarot_deck.lore2": "Sneak + right-click: choose a card",
        "item.realms_of_chaos.tarot_deck.lore3": "A stack that never runs out",
        "menu.realms_of_chaos.deck": "Deck of Fate",
        "menu.realms_of_chaos.deck.rest": "The deck rests %s seconds after this card",
        "hud.realms_of_chaos.tarot": "%s",
        "doll.realms_of_chaos.unbound": "Right-click a creature to bind the doll to it",
        "doll.realms_of_chaos.bound": "Bound to %s",
        "doll.realms_of_chaos.use": "Right-click to stab. Sneak + right-click to unbind",
        "hud.realms_of_chaos.doll.bound": "The doll is bound to %s",
        "hud.realms_of_chaos.doll.unbound": "The doll is unbound",
        "hud.realms_of_chaos.doll.lost": "The bond is broken: nothing answers the doll",
        "hud.realms_of_chaos.doll.far": "%s is too far away to feel it",
        "menu.realms_of_chaos.forge": "Forge of Chaos",
        "menu.realms_of_chaos.forge.none": "Hold a god's relic weapon",
        "menu.realms_of_chaos.forge.none.lore": "Put the weapon in your main hand, then open the forge",
        "menu.realms_of_chaos.forge.level": "Level %s of 10",
        "menu.realms_of_chaos.forge.xp": "%s / %s XP - earned by slaying with this weapon",
        "menu.realms_of_chaos.forge.star": "Star %s",
        "menu.realms_of_chaos.forge.star.owned": "Forged",
        "menu.realms_of_chaos.forge.star.empty": "Not forged yet",
        "menu.realms_of_chaos.forge.upgrade": "Forge star %s",
        "menu.realms_of_chaos.forge.cost": "Costs %s x %s (you have %s)",
        "menu.realms_of_chaos.forge.needlevel": "Needs weapon level %s",
        "menu.realms_of_chaos.forge.effect": "Each star: +10% damage, faster recharge",
        "menu.realms_of_chaos.forge.max": "Fully forged",
        "menu.realms_of_chaos.forge.open": "Forge of Chaos",
        "menu.realms_of_chaos.forge.open.lore": "Level up and forge your relic weapons",
        "hud.realms_of_chaos.forged": "%s is now forged to %s stars!",
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
        "welcome.realms_of_chaos.hint": "Read the Oracle's letter, then follow the Compass of Chaos to a Ruined Gate",
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
        "menu.realms_of_chaos.options.combat": "Combat meter",
        "menu.realms_of_chaos.options.combat.lore": "Momentum, parry and riposte, above the hotbar",
        "menu.realms_of_chaos.boon": "A Seal's Boon",
        "menu.realms_of_chaos.boon.title": "The seal gives way",
        "menu.realms_of_chaos.boon.lore": "Take one. It is yours until you leave this passage,",
        "menu.realms_of_chaos.boon.lore2": "and the same boon can be taken again",
        "menu.realms_of_chaos.boon.have": "Carried: %s of %s",
        "menu.realms_of_chaos.boon.take": "Click to take",
        "boon.realms_of_chaos.vigour": "Vigour",
        "boon.realms_of_chaos.vigour.lore": "Two more hearts",
        "boon.realms_of_chaos.edge": "Edge",
        "boon.realms_of_chaos.edge.lore": "Every blow a tenth harder",
        "boon.realms_of_chaos.swift": "Swiftness",
        "boon.realms_of_chaos.swift.lore": "Quicker on your feet",
        "boon.realms_of_chaos.ward": "Ward",
        "boon.realms_of_chaos.ward.lore": "Two points of armour",
        "boon.realms_of_chaos.keen": "Keen Eye",
        "boon.realms_of_chaos.keen.lore": "A longer moment to parry in",
        "boon.realms_of_chaos.thirst": "Thirst",
        "boon.realms_of_chaos.thirst.lore": "A heart back for every sentinel you fell",
        "boon.realms_of_chaos.fortune": "Fortune",
        "boon.realms_of_chaos.fortune.lore": "More forge material from every cache",
        "hud.realms_of_chaos.boons.gone": "The passage is behind you: its boons are laid down",
        "menu.realms_of_chaos.bestiary": "Bestiary",
        "menu.realms_of_chaos.bestiary.title": "The sentinels of the towers",
        "menu.realms_of_chaos.bestiary.known": "%s of %s kinds met and felled",
        "menu.realms_of_chaos.bestiary.lore": "A kind is written down when one falls to you",
        "menu.realms_of_chaos.bestiary.unknown": "Not yet met",
        "menu.realms_of_chaos.bestiary.unknown.lore": "Fell one to learn what it is",
        "menu.realms_of_chaos.bestiary.slain": "Felled: %s",
        "menu.realms_of_chaos.bestiary.open": "Bestiary",
        "menu.realms_of_chaos.bestiary.role.soldier": "Soldier: closes in and fights hand to hand",
        "menu.realms_of_chaos.bestiary.role.caster": "Caster: keeps its distance and shoots",
        "menu.realms_of_chaos.bestiary.role.skirmisher": "Skirmisher: quick, and leaps at you",
        "menu.realms_of_chaos.bestiary.role.brute": "Brute: slow, and sends you flying",
        "menu.realms_of_chaos.bestiary.role.mystic": "Mystic: hangs back and helps the rest",
        "menu.realms_of_chaos.bestiary.role.keeper": "Keeper: the guardian of a seal",
        "menu.realms_of_chaos.bestiary.trait.shield": "Turns blows from the front and shelters its neighbours",
        "menu.realms_of_chaos.bestiary.trait.bind": "Roots you, hauls you in or blinds you",
        "menu.realms_of_chaos.bestiary.trait.bomb": "Bursts when it falls",
        "menu.realms_of_chaos.bestiary.trait.frenzy": "Grows stronger as its allies fall",
        "menu.realms_of_chaos.bestiary.trait.disarm": "Knocks your weapon out of your hand",
        "menu.realms_of_chaos.bestiary.trait.trap": "Its bolts leave harmful ground",
        "menu.realms_of_chaos.bestiary.trait.snipe": "Lines up one heavy shot, where you can see it coming",
        "menu.realms_of_chaos.bestiary.trait.swap": "Changes places with a wounded ally",
        "menu.realms_of_chaos.bestiary.trait.charge": "Charges across the room",
        "menu.realms_of_chaos.bestiary.trait.mirage": "Splits into copies that do no harm",
        "menu.realms_of_chaos.bestiary.trait.link": "Takes half of an ally's wounds on itself",
        "menu.realms_of_chaos.bestiary.trait.silence": "Stops your relic's move",
        "menu.realms_of_chaos.bestiary.trait.banner": "Its standard quickens the others; its fall dismays them",
        "menu.realms_of_chaos.bestiary.trait.horn": "Its alarm wakes the rooms next door",
        "menu.realms_of_chaos.bestiary.trait.revive": "Raises one fallen ally",
        "menu.realms_of_chaos.bestiary.trait.duelist": "Turns your first blow from the front, and answers it",
        "menu.realms_of_chaos.bestiary.trait.volley": "Looses five bolts at once in a fan",
        "menu.realms_of_chaos.bestiary.trait.whirl": "Spins up, then cuts everything round it",
        "menu.realms_of_chaos.bestiary.trait.slam": "Leaps onto a marked spot and lands in a shockwave",
        "hud.realms_of_chaos.sentinel.guarded": "%s turns your blow - its answer is coming",
        "menu.realms_of_chaos.bestiary.known.codex": "%s of %s kinds",
        "screen.realms_of_chaos.home.bestiary": "Bestiary",
        "screen.realms_of_chaos.home.bestiary.tip": "Every kind of sentinel you have felled, and what it does",
        "art.realms_of_chaos.v3": "%1$s",
        "menu.realms_of_chaos.options.art": "Drawn interface",
        "menu.realms_of_chaos.options.art.lore": "Panels, boss bars and cards drawn in the mod's own style.",
        "menu.realms_of_chaos.options.art.lore2": "Turn it off if they show as rows of boxes: everything is then in plain words",
        "hud.realms_of_chaos.momentum": "Momentum %s",
        "hud.realms_of_chaos.riposte": "Riposte!",
        "menu.realms_of_chaos.options": "Settings",
        "menu.realms_of_chaos.options.casts": "Cast bar",
        "menu.realms_of_chaos.options.casts.lore": "A god's move is named and counted down as it winds up",
        "menu.realms_of_chaos.options.tips": "Tips",
        "menu.realms_of_chaos.options.tips.lore": "A card the first time each new thing happens",
        "menu.realms_of_chaos.options.insight": "Insight",
        "menu.realms_of_chaos.options.insight.lore": "A card when a god remembers how you fight",
        "menu.realms_of_chaos.options.sidebar": "Side panel",
        "menu.realms_of_chaos.options.sidebar.lore": "Your climb at a glance, down the side of the screen",
        "menu.realms_of_chaos.options.on": "On",
        "menu.realms_of_chaos.options.off": "Off",
        "menu.realms_of_chaos.options.click": "Click to switch",
        "menu.realms_of_chaos.options.again": "Show tips again",
        "menu.realms_of_chaos.options.again.lore": "Forget which tips you have had, so each is shown once more",
        "menu.realms_of_chaos.options.again.done": "Every tip will be shown once more",
        "menu.realms_of_chaos.options.open": "Settings",
        "menu.realms_of_chaos.options.open.lore": "Choose what is shown on your screen",
        "menu.realms_of_chaos.insight": "What the Gods Know",
        "menu.realms_of_chaos.insight.open": "What the gods know",
        "menu.realms_of_chaos.insight.open.lore": "How you fight, as the gods remember it",
        "menu.realms_of_chaos.insight.about": "The gods remember",
        "menu.realms_of_chaos.insight.about.1": "Every trial is watched. A habit seen again and again is remembered,",
        "menu.realms_of_chaos.insight.about.2": "and the next god you meet has an answer ready.",
        "menu.realms_of_chaos.insight.about.3": "Fight differently and it is forgotten, one trial at a time",
        "menu.realms_of_chaos.insight.unknown": "Not known to the gods",
        "menu.realms_of_chaos.insight.known": "Known to the gods: %s",
        "menu.realms_of_chaos.insight.level": "Level %s",
        "insight.realms_of_chaos.archer": "Marksman",
        "insight.realms_of_chaos.archer.cause": "Most of your harm done from afar",
        "insight.realms_of_chaos.archer.answer": "Your shots count for less, and the god comes for you",
        "insight.realms_of_chaos.duellist": "Duellist",
        "insight.realms_of_chaos.duellist.cause": "Nearly all of your harm done hand to hand",
        "insight.realms_of_chaos.duellist.answer": "Its guard lashes back at your blade",
        "insight.realms_of_chaos.guard": "Shield-bearer",
        "insight.realms_of_chaos.guard.cause": "Blows taken on a raised shield",
        "insight.realms_of_chaos.guard.answer": "Its blows knock your shield aside",
        "insight.realms_of_chaos.mender": "Mender",
        "insight.realms_of_chaos.mender.cause": "Wounds mended in the middle of a trial",
        "insight.realms_of_chaos.mender.answer": "A strike falls where you stop to heal",
        "insight.realms_of_chaos.runner": "Runner",
        "insight.realms_of_chaos.runner.cause": "Most of a trial spent out of its reach",
        "insight.realms_of_chaos.runner.answer": "It hauls you back when you keep away",
        "insight.realms_of_chaos.remembers": "%s knows you",
        "insight.realms_of_chaos.changed": "The gods have learnt",
        "insight.realms_of_chaos.learnt": "%s %s",
        "insight.realms_of_chaos.forgotten": "%s is forgotten",
        "cast.realms_of_chaos.zeus": "Thunderclap",
        "cast.realms_of_chaos.poseidon": "Riptide",
        "cast.realms_of_chaos.hades": "Shadow Grasp",
        "screen.realms_of_chaos.home.settings": "Settings",
        "screen.realms_of_chaos.home.settings.tip": "The cast bar, tips, what the gods learn, the side panel",
        "screen.realms_of_chaos.home.insight": "What the gods know",
        "screen.realms_of_chaos.home.insight.tip": "How you fight, as the gods remember it - and what they do about it",
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
    lang["entity.realms_of_chaos.sentinel"] = "Sentinel"
    lang["entity.realms_of_chaos.sentinel.elite"] = "Elite %s"
    for hid, htitle, hlines in HANDBOOK:
        lang[f"handbook.realms_of_chaos.{hid}.title"] = htitle
        for n, hline in enumerate(hlines, start=1):
            lang[f"handbook.realms_of_chaos.{hid}.line{n}"] = hline
    for tid, ttext in TIPS:
        lang[f"tip.realms_of_chaos.{tid}"] = ttext
    for sid, sname, _line, _look in SENTINELS:
        lang[f"entity.realms_of_chaos.sentinel.{sid}"] = sname
    for sid, title, blurb, pages in STORY:
        assert len(title) <= 32, title
        lang[f"story.realms_of_chaos.{sid}.title"] = title
        lang[f"story.realms_of_chaos.{sid}.blurb"] = blurb
        for n, page in enumerate(pages, start=1):
            assert len(page) <= 255, (sid, n, len(page))
            lang[f"story.realms_of_chaos.{sid}.page{n}"] = page
    for line, (kname, sname, shrine) in KEYSTONES.items():
        lang[f"item.realms_of_chaos.keystone_{line.lower()}"] = kname
        lang[f"item.realms_of_chaos.seeker_{line.lower()}"] = sname
        lang[f"ruin.realms_of_chaos.{line.lower()}"] = shrine
    for gid, (mname, mkind) in SIGNATURES.items():
        lang[f"move.realms_of_chaos.{gid}"] = mname
    for mkind, mdesc in MOVE_KINDS.items():
        lang[f"move.realms_of_chaos.kind.{mkind.lower()}"] = mdesc
    for cid, numeral, cname, cdesc, _sym, _col in TAROT:
        lang[f"item.realms_of_chaos.tarot_{cid}"] = f"{cname} ({numeral})"
        lang[f"item.realms_of_chaos.tarot_{cid}.desc"] = cdesc
    for did, dname, ddesc, _col in DOLLS:
        lang[f"item.realms_of_chaos.voodoo_doll_{did}"] = dname
        lang[f"item.realms_of_chaos.voodoo_doll_{did}.desc"] = ddesc
    for line, (ename, _col) in ESSENCES.items():
        lang[f"item.realms_of_chaos.essence_{line.lower()}"] = ename
    for orb in ORBS:
        lang[f"item.realms_of_chaos.orb_{orb}"] = "Divine Bolt"
    for line, mats in MATERIALS.items():
        for tier, (mname, _shape, _col) in enumerate(mats, start=1):
            lang[f"item.realms_of_chaos.material_{line.lower()}_{tier}"] = mname
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
    lang.update(panoply_art.lang())
    lang.update(divine_art.lang())
    lang.update(divine_pro.lang())
    lang.update({f"subtitles.{MOD}.{k}": v for k, v in music.SUBTITLES.items()})
    lang.update(station_ui.lang())
    lang.update(orb_ranks.lang())
    lang.update(discs.lang())
    lang.update(furniture_art.lang())
    wj(os.path.join(ASSETS, "lang/en_us.json"), lang)

# ----------------------------------------------------------------------------
# Item definitions / models
# ----------------------------------------------------------------------------
# The three keystones a Ruined Gate needs: (keystone name, the seeker compass that finds it, the shrine it lies in)
KEYSTONES = {
    "ZEUS": ("Keystone of the Sky", "Seeker of the Sky", "Citadel of the Sky"),
    "POSEIDON": ("Keystone of the Tides", "Seeker of the Tides", "Citadel of the Tides"),
    "HADES": ("Keystone of the Shades", "Seeker of the Shades", "Citadel of the Shades"),
}

BLOCKS = ["gate_hub", "gate_zeus", "gate_poseidon", "gate_hades", "gate_ascend", "gate_leave",
          "altar_zeus", "altar_poseidon", "altar_hades", "chaos_forge", "passage_seal", "passage_shrine", "gate_pedestal", "oracle", "passage_cache",
          "shield_pylon", "relay_core", "signal_jammer", "waygate_obelisk", "hub_heart", "bounty_board", "rune_stone",
          "glyph_tile", "chime_crystal", "memory_plate", "beam_mirror", "beam_emitter", "beam_receiver", "dart_tile", "ancient_urn", "lore_tablet", "tower_brazier"]

# The passages' room blocks: id -> (boxes or None for a plain cube, whether it looks different when lit)
ROOM_BLOCKS = {
    "glyph_tile": (None, True), "memory_plate": (None, True), "dart_tile": (None, False), "beam_emitter": (None, False), "beam_receiver": (None, True),
    "chime_crystal": ([(5, 0, 5, 11, 2, 11), (6, 2, 6, 10, 11, 10), (7, 11, 7, 9, 15, 9)], True),
    "beam_mirror": ([(2, 0, 2, 14, 2, 14), (3, 2, 3, 13, 16, 13)], True),
    "ancient_urn": ([(5, 0, 5, 11, 1, 11), (3, 1, 3, 13, 10, 13), (5, 10, 5, 11, 12, 11), (4, 12, 4, 12, 14, 12)], False),
    "lore_tablet": ([(2, 0, 4, 14, 2, 12), (3, 2, 6, 13, 16, 10)], False),
}

# Blocks shaped from stacked boxes with their own top and side textures: id -> boxes
FIELD_MODELS = {
    "shield_pylon": [(3, 0, 3, 13, 2, 13), (5, 2, 5, 11, 13, 11), (4, 13, 4, 12, 16, 12)],
    "relay_core": [(2, 0, 2, 14, 4, 14), (5, 4, 5, 11, 12, 11), (7, 12, 7, 9, 16, 9)],
    "signal_jammer": [(1, 0, 1, 15, 6, 15), (4, 6, 4, 12, 10, 12), (6, 10, 6, 10, 12, 10)],
    "waygate_obelisk": [(1, 0, 1, 15, 3, 15), (4, 3, 4, 12, 13, 12), (5, 13, 5, 11, 16, 11)],
    "hub_heart": [(0, 0, 0, 16, 4, 16), (3, 4, 3, 13, 7, 13), (5, 7, 5, 11, 12, 11), (3, 12, 3, 13, 16, 13)],
    "bounty_board": [(0, 0, 0, 16, 16, 16)],
}
# Field blocks a player can break: they drop themselves
FIELD_DROPS = ["relay_core", "signal_jammer", "waygate_obelisk"]
# the realms' own building stone: dug by pickaxe, drops itself
DECOR = ["olympian_marble", "olympian_marble_pillar", "gilded_frieze", "abyssal_tile", "underworld_brick", "chaos_lamp",
         "olympian_tiles", "chaos_mosaic", "olympian_panel", "abyssal_panel", "underworld_panel", "sky_relief", "sea_relief", "shade_relief",
         "marble_column", "sunstone_panel", "storm_brick", "coral_column", "pearl_panel", "tide_brick", "bone_column", "ember_panel", "ash_brick",
         "sky_mosaic", "sea_mosaic", "shade_mosaic", "star_vault", "celestial_globe", "bronze_grate", "weapon_rack", "scroll_shelf", "bath_tile",
         "laurel_hedge", "kelp_weave", "asphodel_bed",
         "cloudstone", "cloud_tile", "trench_stone", "nacre_tile", "tartarus_stone", "throne_tile",
         "portal_frame_sky", "portal_frame_sea", "portal_frame_shade", "portal_frame_hub", "arrival_sigil",
         "bronze_trapdoor", "sky_trapdoor", "shade_trapdoor",
         "arena_sand", "beast_gate", "war_drum", "war_standard", "sprinkler", "porphyry", "polished_porphyry", "porphyry_bricks", "porphyry_tiles", "chiseled_porphyry",
         "porphyry_pillar", "adamantine_block", "fulgurite", "giant_clam", "plinth_of_plenty", "column_of_plenty", "bronze_sliding_door", "sky_sliding_door", "sea_sliding_door", "shade_sliding_door",
         "reinforced_glass", "frosted_glass", "smart_glass", "veil_glass", "one_way_glass"] + [f"{b}_{k}" for b in ("olympian_marble", "cloudstone", "trench_stone", "tartarus_stone", "storm_brick", "tide_brick", "ash_brick", "underworld_brick", "polished_porphyry", "porphyry_bricks") for k in ("stairs", "slab")]
PICKAXE_ONLY = ["bronze_door", "sky_door", "shade_door", "verdigris_door", "verdigris_trapdoor", "porphyry_door", "porphyry_trapdoor", "deepslate_adamantine_ore", "stygian_iron_ore", "cornucopia",
                "standing_lamp", "candelabrum", "amphora"]
AXE_ONLY = ["sea_door", "sea_trapdoor", "garrison_door", "garrison_trapdoor", "ivory_door", "ivory_trapdoor",
            "training_dummy", "moving_dummy", "chair", "table", "bench", "dresser", "bunk_bed",
            "stool", "armchair", "nightstand", "writing_desk", "wall_shelf", "rug"]
FIELD_DROPS = FIELD_DROPS + DECOR

# The 22 Major Arcana: (id, numeral, name, what the card does, 8x8 symbol (# ink, + colour), colour)
TAROT = [
    ("fool", "0", "The Fool", "Dons you in iron and carries you to a village or a dungeon", ["..#..#..", ".#+##+#.", "#++##++#", "########", ".######.", "..#++#..", "..####..", "........"], (120, 200, 120)),
    ("magician", "I", "The Magician", "Great strength, and twenty logs from nowhere", [".##..##.", "#..##..#", ".##..##.", "...++...", "...++...", "...##...", "...##...", "...##..."], (200, 80, 80)),
    ("high_priestess", "II", "The High Priestess", "A bubble no harm can enter: time to think", ["#......#", "#..##..#", "#.#+...#", "#.#+...#", "#.#+...#", "#..##..#", "#......#", "########"], (90, 110, 200)),
    ("empress", "III", "The Empress", "Everything green around you grows; fortune smiles", [".##..##.", "#++##++#", "#++++++#", "#++++++#", ".#++++#.", "..#++#..", "...##...", "........"], (220, 110, 150)),
    ("emperor", "IV", "The Emperor", "Nothing knocks you back; your armour hardens", ["#..##..#", "#..##..#", "##.##.##", "########", "#+#++#+#", "########", "........", "........"], (200, 60, 50)),
    ("hierophant", "V", "The Hierophant", "Villagers hail you as their hero", ["...##...", ".######.", "...##...", "..####..", "...##...", "...++...", "..++++..", "...++..."], (150, 120, 200)),
    ("lovers", "VI", "The Lovers", "Mends you and strengthens everyone near you", [".##..##.", "#++##++#", "#++##++#", ".##..##.", "...##...", "..#++#..", "..#++#..", "...##..."], (230, 120, 170)),
    ("chariot", "VII", "The Chariot", "Great speed, and you stride over full blocks", ["..####..", ".#.##.#.", "#..##..#", "###++###", "###++###", "#..##..#", ".#.##.#.", "..####.."], (90, 170, 220)),
    ("strength", "VIII", "Strength", "Strength and resistance", ["#.####.#", "########", "#+#..#+#", "########", "##.++.##", ".######.", "..#..#..", "........"], (220, 150, 60)),
    ("hermit", "IX", "The Hermit", "Withdraw to a private realm of your own (play again to return)", ["...##...", "..#..#..", ".######.", ".#.++.#.", ".#++++#.", ".#.++.#.", ".######.", "..####.."], (120, 130, 150)),
    ("wheel_of_fortune", "X", "Wheel of Fortune", "Luck beyond measure", ["..####..", ".#+##+#.", "#+#..#+#", "##.++.##", "##.++.##", "#+#..#+#", ".#+##+#.", "..####.."], (220, 190, 70)),
    ("justice", "XI", "Justice", "Every creature near you is revealed", ["...##...", "########", "#..##..#", "#..##..#", "+++##+++", ".+.##.+.", "...##...", "..####.."], (200, 200, 210)),
    ("hanged_man", "XII", "The Hanged Man", "For a time, no monster will turn on you", ["########", "...##...", "...##...", "..####..", ".#.##.#.", "...##...", "..#++#..", "...++..."], (90, 160, 150)),
    ("death", "XIII", "Death", "Hands you a Totem of Undying", [".######.", "########", "#++##++#", "#++##++#", "###..###", ".######.", ".#.##.#.", ".#.##.#."], (60, 50, 70)),
    ("temperance", "XIV", "Temperance", "Full health, full hunger", ["###..###", "#+#..#+#", ".#+..+#.", "..#++#..", "...++...", "..#++#..", ".#+..+#.", "###..###"], (110, 190, 210)),
    ("devil", "XV", "The Devil", "Burns your inventory; whatever you pick up becomes the item in your off hand", ["#......#", "##....##", ".######.", "#+#..#+#", "########", ".#.##.#.", "..####..", "...##..."], (150, 30, 40)),
    ("tower", "XVI", "The Tower", "Carries you to another god tower: the one whose relics you hold fewest of", ["..+..##.", ".+..####", "++..#..#", ".+..####", "..+.#..#", "....####", "....#..#", "..######"], (130, 120, 110)),
    ("star", "XVII", "The Star", "Experience, and a little luck", ["...#....", "...#....", ".#.#.#..", "..+++...", "###+###.", "..+++...", ".#.#.#..", "...#...."], (120, 170, 240)),
    ("moon", "XVIII", "The Moon", "Summons illusions: slay them all for a reward", ["..####..", ".###....", "###.....", "###.....", "###.....", ".###....", "..####..", ".+.+.+.."], (150, 150, 220)),
    ("sun", "XIX", "The Sun", "Set your foes ablaze", ["#..##..#", ".#.##.#.", "..####..", "###++###", "###++###", "..####..", ".#.##.#.", "#..##..#"], (245, 190, 60)),
    ("judgement", "XX", "Judgement", "Rise again at full health", [".......#", ".....###", "+..#####", "++######", "++######", "+..#####", ".....###", ".......#"], (230, 220, 190)),
    ("world", "XXI", "The World", "Receive every blessing at once", ["..####..", ".#++++#.", "#++##++#", "#+####+#", "#+####+#", "#++##++#", ".#++++#.", "..####.."], (90, 180, 120)),
]
# Voodoo dolls: (id, name, what stabbing it does, pin colour)
DOLLS = [
    ("pain", "Voodoo Doll of Pain", "Stab it to wound the creature it is bound to", (200, 200, 210)),
    ("binding", "Voodoo Doll of Binding", "Stab it to slow and weaken the creature it is bound to", (120, 200, 230)),
    ("rot", "Voodoo Doll of Rot", "Stab it to wither the creature it is bound to", (120, 170, 80)),
    ("embers", "Voodoo Doll of Embers", "Stab it to set the creature it is bound to ablaze", (245, 140, 50)),
]
DOLL_PINS = {"pain": "minecraft:iron_nugget", "binding": "minecraft:cobweb", "rot": "minecraft:rotten_flesh", "embers": "minecraft:blaze_powder"}
ESSENCES = {"ZEUS": ("Spark of Olympus", (250, 215, 90)), "POSEIDON": ("Pearl of the Deep", (80, 200, 220)), "HADES": ("Ember of the Underworld", (180, 80, 230))}
# Forge materials dropped by the gods of each tier of a tower (tier 4, the summit, drops the essence above)
MATERIALS = {
    "ZEUS": [("Laurel Leaf", "leaf", (96, 170, 84)), ("Marble Shard", "shard", (226, 226, 236)), ("Cloud Silk", "tuft", (236, 244, 255))],
    "POSEIDON": [("Sea Glass", "shard", (120, 214, 196)), ("Reef Scale", "scale", (236, 130, 120)), ("Trench Ink", "drop", (40, 50, 110))],
    "HADES": [("Asphodel Petal", "petal", (214, 206, 232)), ("Tartarus Cinder", "cinder", (236, 110, 40)), ("Obol of the Dead", "coin", (190, 170, 110))],
}
MATERIAL_SHAPES = {
    "leaf": ["................", "................", ".........hhh....", ".......hhbbbh...", "......hbbsbbb...", ".....hbbsbbbs...", ".....hbsbbbs....",
             "....hbsbbbs.....", "....bsbbbs......", "....sbbss.......", "...gss..........", "..g.............", ".g.............."],
    "shard": ["................", "................", ".......hh.......", "......hbbh......", "......hbbbs.....", ".....hbbbbs.....", ".....hbbbbbs....",
              "....hbbbbbbs....", "....hbbbbbs.....", ".....bbbbss.....", "......bsss......", ".......ss......."],
    "tuft": ["................", "................", "................", "......hhh.......", "....hhhbbhh.....", "...hhbbbbbbhh...", "..hbbbbbbbbbbs..",
             "..hbbbbbbbbbss..", "...sbbbsbbbss...", "....sss.sss.....", "................"],
    "scale": ["................", "................", "................", ".....hhhhhh.....", "....hbbbbbbs....", "...hbbsbbsbbs...", "...hbsbbsbbss...",
              "...hbbbbbbbbs...", "....sbbsbbss....", ".....sbbbss.....", "......ssss......", "................"],
    "drop": ["................", "................", ".......h........", "......hb........", "......hbs.......", ".....hbbbs......", ".....hbbbs......",
             "....hbbbbbs.....", "....hbbbbbs.....", "....bbbbbss.....", ".....bbsss......", "......sss......."],
    "petal": ["................", "................", "......hh.hh.....", ".....hbbhbbh....", "....hbbbbbbbs...", "....hbbgpgbbs...", ".....bbgpgbs....",
              "....hbbbgbbbs...", "....hbbsbsbbs...", ".....ss.g.ss....", "........g.......", ".......gg......."],
    "cinder": ["................", "................", "................", "......ss.s......", ".....sbbsbs.....", "....sbhhbbss....", "....sbhhhbbs....",
               "...sbbhhbbbs....", "...sbbbbbbss....", "....ssbbsss.....", "......sss.......", "................"],
    "coin": ["................", "................", "................", ".....hhhhhh.....", "....hbbbbbbs....", "...hbbsssbbbs...", "...hbsbbbsbbs...",
             "...hbsbbbsbbs...", "...hbbsssbbss...", "....sbbbbbss....", ".....ssssss.....", "................"],
}

# Projectile sprites for staffs and gods, one per power
ORBS = ["none", "lightning", "fire", "knockback", "drain", "freeze", "sweep"]

def item_def(item_id, model_path):
    wj(os.path.join(ASSETS, f"items/{item_id}.json"),
       {"model": {"type": "minecraft:model", "model": f"{MOD}:{model_path}"}})

def station_model(top, bottom, side, boxes):
    """A block model built from stacked boxes (x1, y1, z1, x2, y2, z2): a plinth, a waist and a table, not a cube.
    Side faces take the matching strip of the side texture, so the art lines up from box to box."""
    elements = []
    for x1, y1, z1, x2, y2, z2 in boxes:
        faces = {
            "north": {"uv": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "texture": "#side"},
            "south": {"uv": [x1, 16 - y2, x2, 16 - y1], "texture": "#side"},
            "west": {"uv": [z1, 16 - y2, z2, 16 - y1], "texture": "#side"},
            "east": {"uv": [16 - z2, 16 - y2, 16 - z1, 16 - y1], "texture": "#side"},
            "up": {"uv": [x1, z1, x2, z2], "texture": "#top"},
            "down": {"uv": [x1, 16 - z2, x2, 16 - z1], "texture": "#bottom"},
        }
        elements.append({"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces})
    return {"parent": "minecraft:block/block", "textures": {"particle": side, "top": top, "bottom": bottom, "side": side},
            "elements": elements}

def gen_models():
    for _, _, _, g in all_gods():
        iid = f"{g[0]}_weapon"
        item_def(iid, f"item/{iid}")
        parent = "minecraft:item/generated" if g[4] in GENERATED_SHAPES else "minecraft:item/handheld"
        wj(os.path.join(ASSETS, f"models/item/{iid}.json"), {"parent": parent, "textures": {"layer0": f"{MOD}:item/{iid}"}})
    extra = ["chaos_compass", "chaos_codex", "field_bag", "lockpick", "tarot_deck"] + [f"tarot_{c[0]}" for c in TAROT] + [f"voodoo_doll_{d[0]}" for d in DOLLS] \
        + [f"essence_{l.lower()}" for l in LINES] \
        + [f"material_{l.lower()}_{t}" for l in LINES for t in (1, 2, 3)] \
        + [f"keystone_{l.lower()}" for l in LINES] + [f"seeker_{l.lower()}" for l in LINES]
    for flat in extra:
        item_def(flat, f"item/{flat}")
        wj(os.path.join(ASSETS, f"models/item/{flat}.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{flat}"}})
    # staff bolts and the gods' shots: a 3D bolt, drawn lying along its flight
    for orb in ORBS:
        item_def(f"orb_{orb}", f"item/orb_{orb}")
        wj(os.path.join(ASSETS, f"models/item/orb_{orb}.json"), bolt_model(f"{MOD}:item/orb_{orb}"))
    for b in BLOCKS:
        wj(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": {"": {"model": f"{MOD}:block/{b}"}}})
        if b == "chaos_forge":
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/chaos_forge_top", f"{MOD}:block/altar_base", f"{MOD}:block/chaos_forge_side",
                             [(1, 0, 1, 15, 4, 15), (3, 4, 3, 13, 9, 13), (0, 9, 0, 16, 16, 16)]))
        elif b == "passage_seal":
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/passage_seal_top", f"{MOD}:block/altar_base", f"{MOD}:block/passage_seal",
                             [(2, 0, 2, 14, 2, 14), (4, 2, 4, 12, 13, 12), (6, 13, 6, 10, 16, 10)]))
        elif b == "gate_pedestal":
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/gate_pedestal_top", f"{MOD}:block/altar_base", f"{MOD}:block/gate_pedestal_side",
                             [(1, 0, 1, 15, 3, 15), (4, 3, 4, 12, 11, 12), (2, 11, 2, 14, 14, 14)]))
        elif b == "oracle":
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/oracle_top", f"{MOD}:block/altar_base", f"{MOD}:block/oracle_side",
                             [(0, 0, 0, 16, 4, 16), (3, 4, 3, 13, 8, 13), (1, 8, 1, 15, 12, 15), (4, 12, 4, 12, 16, 12)]))
        elif b in ROOM_BLOCKS:
            boxes, lit = ROOM_BLOCKS[b]
            variants = {}
            for on in (False, True):
                name = b + ("_lit" if on and lit else "")
                if boxes is None:
                    model = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{name}"}}
                else:
                    model = station_model(f"{MOD}:block/{name}_top", f"{MOD}:block/altar_base", f"{MOD}:block/{name}_side", boxes)
                wj(os.path.join(ASSETS, f"models/block/{name}.json"), model)
                variants[f"lit={'true' if on else 'false'}"] = {"model": f"{MOD}:block/{name}"}
            wj(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": variants})
            item_def(b, f"block/{b}")
            continue
        elif b == "tower_brazier":
            variants = {}
            for n, line in enumerate(LINES):
                name = f"tower_brazier_{line.lower()}"
                wj(os.path.join(ASSETS, f"models/block/{name}.json"),
                   station_model(f"{MOD}:block/{name}_top", f"{MOD}:block/altar_base", f"{MOD}:block/tower_brazier_side",
                                 [(5, 0, 5, 11, 2, 11), (7, 2, 7, 9, 7, 9), (3, 7, 3, 13, 12, 13)]))
                variants[f"tower={n}"] = {"model": f"{MOD}:block/{name}"}
            wj(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": variants})
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/tower_brazier_zeus_top", f"{MOD}:block/altar_base", f"{MOD}:block/tower_brazier_side",
                             [(5, 0, 5, 11, 2, 11), (7, 2, 7, 9, 7, 9), (3, 7, 3, 13, 12, 13)]))
            item_def(b, f"block/{b}")
            continue
        elif b == "rune_stone":
            # four runes, each lit or dark: eight looks from one block
            variants = {}
            for n in range(4):
                for lit in (False, True):
                    name = f"rune_stone_{n}" + ("_lit" if lit else "")
                    variants[f"lit={'true' if lit else 'false'},rune={n}"] = {"model": f"{MOD}:block/{name}"}
                    wj(os.path.join(ASSETS, f"models/block/{name}.json"),
                       {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/rune_stone_top", "side": f"{MOD}:block/{name}"}})
            wj(os.path.join(ASSETS, f"blockstates/{b}.json"), {"variants": variants})
            # the block's own model: the first rune, dark - what it looks like in the hand
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               {"parent": "minecraft:block/cube_column", "textures": {"end": f"{MOD}:block/rune_stone_top", "side": f"{MOD}:block/rune_stone_0"}})
            item_def(b, f"block/{b}")
            continue
        elif b in FIELD_MODELS:
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/{b}_top", f"{MOD}:block/altar_base", f"{MOD}:block/{b}_side", FIELD_MODELS[b]))
        elif b.startswith("altar"):
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               station_model(f"{MOD}:block/{b}_top", f"{MOD}:block/altar_base", f"{MOD}:block/{b}_side",
                             [(0, 0, 0, 16, 3, 16), (2, 3, 2, 14, 5, 14), (4, 5, 4, 12, 12, 12), (1, 12, 1, 15, 16, 15)]))
        else:
            wj(os.path.join(ASSETS, f"models/block/{b}.json"),
               {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{b}"}})
        item_def(b, f"block/{b}")

# ----------------------------------------------------------------------------
# Weapon textures: hand-drawn 16x16 templates, auto-outlined, shaded, with an animated glint
#   h highlight  b blade  s blade shadow  g guard/accent  w wood  W dark wood  p gem (power colour)
# ----------------------------------------------------------------------------
GENERATED_SHAPES = {"chalice", "conch", "flute", "orb", "lantern", "star", "talon", "crossbow", "tessen", "kunai", "mjolnir", "shuriken", "kusarigama", "nunchaku", "yumi"}      # held flat, not like a tool

# Designs drawn ahead of the mythologies that will use them (Japanese, Norse, Egyptian, Celtic...)
# Japanese first: the next mythology planned
JAPANESE_SHAPES = ["katana", "wakizashi", "tanto", "nodachi", "naginata", "yari", "kusarigama", "sai", "kanabo", "nunchaku",
                   "yumi", "kunai", "shuriken", "tessen"]
FUTURE_SHAPES = JAPANESE_SHAPES + ["battleaxe", "mjolnir", "khopesh", "claymore", "halberd", "mace", "crossbow"]

SHAPES = {
    "scythe": """
................
....hhhhhhh.....
..hhbbbbbbbbg...
.hbbbbsssbbbgw..
.hbbss....sgwW..
.bbs......gwW...
.bs.......wW....
.s.......wW.....
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..pW............
................""",
    "sickle": """
................
................
......hhhhh.....
....hhbbbbbb....
...hbbsssbbbb...
...bbs....sbb...
..hbs......bb...
..bs.......bs...
..s.......gg....
.........gW.....
........wW......
.......wW.......
......wW........
.....pW.........
................
................""",
    "talon": """
................
..h...h...h.....
..hb..hb..hb....
...bb..bb..bb...
...sbb.sbb.sbb..
....sbbbsbbbsb..
.....ggggggggg..
......gwwwwwg...
.......wWWw.....
.......wWWw.....
......ggggg.....
.......ppp......
................
................
................
................""",
    "rapier": """
................
..............h.
.............hb.
............hb..
...........hb...
..........hb....
.........hb.....
........hb......
.......hb.......
...gg.hb........
...ggggb........
....gpgg........
...wWgg.........
..wW............
.pW.............
................""",
    "cutlass": """
................
...........hhh..
..........hbbbh.
.........hbbbs..
........hbbbs...
.......hbbbs....
......hbbbs.....
.....hbbbs......
....gbbss.......
...ggbs.........
..g.gg..........
..gwWg..........
..wW............
.pW.............
................
................""",
    "greatsword": """
................
............hhh.
...........hbbb.
..........hbbbs.
.........hbbbs..
........hbbbs...
.......hbbbs....
..g...hbbbs.....
..gg.hbbbs......
...ggbbbs.......
....gggs........
...gggggg.......
..wWg..gg.......
.wW.............
pW..............
................""",
    "gladius": """
................
................
...........hh...
..........hbbs..
.........hbbbs..
........hbbbbs..
.......hbbbbs...
......hbbbbs....
...g..hbbbs.....
...gg.bbbs......
....gggs........
.....ggg........
....wWgg........
...wW...........
..pW............
................""",
    "katana": """
................
............hhh.
..........hhbbs.
.........hbbss..
........hbbs....
.......hbbs.....
......hbbs......
.....hbbs.......
.....hbs........
....hbbs........
....hbs.........
...gggg.........
...wWg..........
..wWw...........
..WwW...........
.pW.............""",
    "wakizashi": """
................
................
................
...........hhh..
.........hhbbs..
........hbbss...
.......hbbs.....
......hbbs......
......hbs.......
.....hbbs.......
....gggg........
....wWg.........
...wWw..........
...WwW..........
..pW............
................""",
    "tanto": """
................
................
................
................
..........hh....
.........hbs....
........hbbs....
.......hbbs.....
.......hbs......
......hbs.......
.....ggg........
.....wW.........
....wW..........
....Ww..........
...pW...........
................""",
    "nodachi": """
.............hhh
...........hhbbs
..........hbbbs.
.........hbbbs..
........hbbbs...
.......hbbbs....
......hbbbs.....
.....hbbbs......
.....hbbs.......
....hbbbs.......
....hbbs........
...hbbs.........
..gggg..........
..wWg...........
.wWw............
pWW.............""",
    "yari": """
..............h.
.............hb.
............hbs.
..........ghbs..
.........gghs...
..........wgg...
.........wW.g...
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.wW.............
pW..............""",
    "kusarigama": """
................
.....hhhhh......
....hbbbbbh.....
...hbs...sbh....
...bs.....sb....
..........wW....
.........wW.....
........wW......
.......wW.......
......wW........
.....gW.........
....g...........
...g..g.........
..g..g.g........
.ppg...g........
.pp.gggg........""",
    "sai": """
................
.............h..
............hb..
...........hbs..
....h.....hbs...
....bh...hbs....
.....bh.hbs.....
......bhbs......
......gggh......
.....gggg.bh....
....wW....sb....
...wW......s....
..wW............
.wW.............
pW..............
................""",
    "kanabo": """
................
..........hhh...
.........hbgbs..
........hbbbbgs.
.......hgbbbbs..
......hbbbgbs...
.....hbgbbbs....
......hbbbs.....
......gggs......
.....wW.........
....wW..........
....Ww..........
...wW...........
..gg............
.pg.............
................""",
    "shuriken": """
................
.......h........
.......hb.......
.......hb.......
......hbbs......
..hh.hbbbs......
.hbbbbbgbbbbbh..
..sbbbgpgbbbs...
...ssbbgbbbbbs..
......hbbs.ss...
......hbs.......
.......bs.......
.......bs.......
.......s........
................
................""",
    "yumi": """
................
.........WWWw...
.......WWw..h...
......Ww....h...
.....Ww.....h...
....Ww......h...
....Ww......h...
...ggg......h...
...ggg.....h....
....Ww....h.....
....Ww...h......
.....Ww.h.......
.....WWh........
......Ww........
................
................""",
    "nunchaku": """
................
...........wW...
..........wWw...
.........wWw....
........wWw.....
.......gWw......
......g.g.......
.....g..........
....g...........
...g.g..........
....gWw.........
.....wWw........
......wWw.......
.......wWw......
........wW......
................""",
    "battleaxe": """
................
......hhhh......
....hhbbbbb.wW..
...hbbbbbbbwW...
...hbbbbbbwW....
...bbbbbbwW.....
....sbbbwW......
.....sswW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................
................""",
    "halberd": """
................
.............hh.
............hbb.
......hh...hbs..
.....hbbh.wW....
....hbbbbwW.....
....bbbbwW......
.....sswW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................
................""",
    "mace": """
................
..........h.h...
.........hbbbh..
........hbbbbb..
.........bbbbbs.
........hbbbbs..
.......wWbsbs...
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................
................
................""",
    "naginata": """
................
.............hh.
............hbs.
...........hbs..
..........hbs...
.........hbs....
........ggs.....
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.wW.............
pW..............
................""",
    "kunai": """
................
................
................
..........hh....
.........hbbs...
........hbbs....
.......hbbs.....
......hbs.......
.....wW.........
....wW..........
...gg...........
..g..g..........
..g..g..........
...gg...........
................
................""",
    "khopesh": """
................
.........hhhh...
.......hhbbbbh..
......hbbss.bb..
.....hbbs...bs..
.....hbs....s...
.....hbs........
....hbs.........
....gbs.........
...ggg..........
...wWg..........
..wW............
.pW.............
................
................
................""",
    "mjolnir": """
................
.....hhhhhhhh...
....hbbbbbbbbs..
....hbbggbbbbs..
....hbbggbbbbs..
....hbbbbbbbbs..
.....ssssssss...
........wW......
........wW......
........wW......
........wW......
.......gggg.....
........pp......
................
................
................""",
    "claymore": """
................
.............hh.
............hbs.
...........hbs..
..........hbs...
.........hbs....
........hbs.....
.......hbs......
..g...hbs.......
...g.hbs........
....gbs.........
...g.gg.........
..g.wWg.........
...wW..g........
..pW............
................""",
    "crossbow": """
................
..gg........gg..
...ggwwwwwwgg...
....hWWWWWWh....
.....h.ww.h.....
......hwwh......
.......ww.......
.......ww.......
.......ww.......
.......wW.......
......gwWg......
.......wW.......
.......pp.......
................
................
................""",
    "tessen": """
................
...hhhhhhhhh....
..hbbsbbsbbsbh..
..bbsbbsbbsbbs..
...bsbbsbbsbs...
...sbbsbbsbbs...
....sbsbbsbs....
.....ssbbss.....
......sggs......
.......gg.......
.......wW.......
.......pp.......
................
................
................
................""",
    "bow": """
................
.......ggwwwg...
.....wwW.....h..
....wW......h...
...wW......h....
...wW.....h.....
..wW.....h......
..pW....h.......
..wW...h........
..gW..h.........
..gw.h..........
...wh...........
...g............
................
................
................""",
    "chalice": """
................
................
...hbbbbbbbbs...
...hppppppbbs...
...hbbbbbbbbs...
....hbbbbbbs....
.....hbbbbs.....
......hbbs......
.......bs.......
.......bs.......
.......bs.......
......hbbs......
....ggggggggg...
....gggggggg....
................
................""",
    "conch": """
................
................
.......hhh......
.....hhbbbbb....
....hbbsbbbbb...
...hbbsbbsbbbs..
...hbsbbsbbsbs..
..hbbsbbsbbsbs..
..hbbbsbbsbbs...
...gbbbsbbss....
....ggbbbss.....
......ggpp......
........p.......
................
................
................""",
    "flute": """
................
.wW.............
.wWwW...........
.wWwWwW.........
.wWwWwWwW.......
.wWwWwWwWwW.....
.ggggggggggggp..
.wWwWwWwWwWwW...
.wWwWwWwWwWwW...
.wWwWwWwWwW.....
.wWwWwWwW.......
.wWwWwW.........
.wWwW...........
.wW.............
................
................""",
    "feather": """
................
............hh..
..........hhbb..
.........hbbbs..
........hbbbs...
.......hbbbs....
......hbbbs.....
.....hbbss......
....hbbs........
....gbs.........
...gws..........
...w............
..w.............
.p..............
................
................""",
    "anchor": """
................
.......pp.......
......p..p......
.......pp.......
.......bb.......
....ggggggggg...
.......bb.......
.......bb.......
.......bb.......
..h....bb....s..
..hb...bb...bs..
...hb..bb..bs...
....hbbbbbbs....
.....hbbbbs.....
................
................""",
    "orb": """
................
................
................
.....hhhhh......
....hhhbbbb.....
...hhhbbbbbb....
...hhbbbbbbs....
...hbbbbbbbs....
...bbbbbbbss....
...bbbbbbsss....
....bbbssss.....
.....sssss......
....ggggggg.....
................
................
................""",
    "lantern": """
................
.......gg.......
......g..g......
.......gg.......
.....ggggggg....
.....gphhpg.....
.....ghpphg.....
.....gpphpg.....
.....ghpphg.....
.....gphhpg.....
.....ggggggg....
......ggggg.....
................
................
................
................""",
    "torch": """
................
...........pp...
..........phhp..
..........phhp..
.........gppg...
.........ggg....
........wWg.....
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.wW.............
................
................""",
    "whip": """
................
.........bbbb...
.......bbs..bb..
......bs.....bb.
......b.......b.
......bs.....bs.
.......bb...bs..
....g...bbbbs...
....gg..........
.....gg.........
....wWgg........
...wW...........
..wW............
.pW.............
................
................""",
    "shears": """
................
.............h..
...........hhb..
.........hhbb...
........hbbs.h..
.......hbs.hhb..
......gpghbbs...
.....gg.gbs.....
....g..g.g......
...g...g..g.....
...g..g...g.....
....gg.ggg......
................
................
................
................""",
    "oar": """
................
............hb..
...........hbbs.
..........hbbbs.
..........bbbs..
.........wbss...
........wW......
.......wW.......
......wW........
.....wW.........
....wW..........
...wW...........
..wW............
.pW.............
................
................""",
    "star": """
................
.......h........
.......hb.......
......hbb.......
..hh..hbbs......
...bbbbbbbbbbs..
....hbbppbbs....
.....hbppbs.....
....hbbbbbbs....
...hbbs.sbbs....
...bs.....ss....
..s.........s...
................
................
................
................""",
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
    metals = LINE_INFO[line]["metals"]
    metal = metals[hsh(gid) % len(metals)]
    if gid in ("zeus",):
        metal = (255, 236, 120)
    return paint_weapon(shape, metal, LOOKS[gid][2], POWER_COLOR[power], mix((116, 78, 46), LOOKS[gid][1], 0.15))

def paint_weapon(shape, metal, trim, gem, wood):
    """Frames of one weapon: frame 0 at rest, then a glint sweeping along the blade."""
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

def gen_concepts():
    """
    Weapon designs for the mythologies still to come. Each is written to the mod's assets (unused until an item
    points at it) and to art/weapon-designs/, with one sheet showing every design in three metals.
    """
    out = os.path.join(ROOT, "art/weapon-designs")
    os.makedirs(out, exist_ok=True)
    styles = [((206, 212, 224), (196, 60, 60), (236, 196, 84)),      # steel, red wrap, gold gem
              ((236, 196, 84), (60, 110, 200), (80, 200, 220)),      # gold, blue trim, aqua gem
              ((92, 86, 110), (150, 72, 200), (226, 60, 96))]        # dark iron, purple trim, blood gem
    wood = (104, 70, 44)
    names = FUTURE_SHAPES + [n for n in SHAPES if n not in FUTURE_SHAPES]
    k = 6
    sheet = Image.new("RGBA", (len(styles) * (16 * k + 8) + 8, len(names) * (16 * k + 8) + 8), (22, 18, 43, 255))
    for row, name in enumerate(names):
        for col, (metal, trim, gem) in enumerate(styles):
            frame = paint_weapon(name, metal, trim, gem, wood)[0]
            sheet.alpha_composite(frame.resize((16 * k, 16 * k), Image.NEAREST), (8 + col * (16 * k + 8), 8 + row * (16 * k + 8)))
            if col == 0 and name in FUTURE_SHAPES:
                frame.save(os.path.join(out, f"{name}.png"))
                dst = os.path.join(ASSETS, f"textures/item/design/{name}.png")
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                frame.save(dst)
    sheet.save(os.path.join(out, "sheet.png"))
    w(os.path.join(out, "README.md"), "# Weapon designs\n\nGenerated by `tools/generate.py`. `sheet.png` shows every weapon silhouette in three metals; "
      "the first rows are designs for mythologies that are not in the mod yet.\n\nJapanese (planned next):\n\n"
      + "".join(f"* `{n}`\n" for n in JAPANESE_SHAPES) + "\nOther mythologies:\n\n" + "".join(f"* `{n}`\n" for n in FUTURE_SHAPES if n not in JAPANESE_SHAPES)
      + "\nThe single PNGs are also shipped in the mod under `textures/item/design/`, ready for an item to point at.\n")

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

def draw_template(rows, pal, outline=True):
    """Paints a 16x16 ASCII template with a palette and (optionally) a dark one-pixel rim."""
    rows = [(r + "." * 16)[:16] for r in rows]
    rows += ["." * 16] * (16 - len(rows))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    filled = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                filled[(x, y)] = pal[ch]
    if outline:
        for (x, y), c in filled.items():
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    px[q] = shade(c, 0.28) + (255,)
    for q, c in filled.items():
        px[q] = tuple(c) + (255,)
    return img

def tarot_card(col, sym, glow):
    """One 16x16 frame: a full-height parchment card, gilt edge, coloured inner frame, and the 8x8 symbol."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    parchment, gilt, dark = (243, 233, 208), (218, 172, 68), (44, 28, 16)
    for y in range(16):
        for x in range(2, 14):
            if (x in (2, 13)) and (y in (0, 15)):
                continue                                             # clipped corners
            edge = x in (2, 13) or y in (0, 15)
            inner = x in (3, 12) or y in (1, 14)
            c = dark if edge else gilt if inner else parchment
            if not edge and not inner and (y in (2, 13)):
                c = mix(parchment, col, 0.75)                        # coloured bands above and below the symbol
            if not edge and not inner and y not in (2, 13) and (x + y) % 5 == 0:
                c = shade(parchment, 0.95)                           # a little paper grain
            px[x, y] = c + (255,)
    ink = (52, 36, 30)
    accent = mix(shade(col, 0.85), mix(col, (255, 255, 255), 0.25), glow)
    for sy, row in enumerate(sym):
        for sx, ch in enumerate(row):
            if ch == "#":
                px[4 + sx, 4 + sy] = ink + (255,)
            elif ch == "+":
                px[4 + sx, 4 + sy] = accent + (255,)
    return img

def gen_tarot():
    for cid, numeral, cname, cdesc, sym, col in TAROT:
        frames = [tarot_card(col, sym, 0.5 + 0.5 * math.sin(f / 6 * math.tau)) for f in range(6)]
        save_strip(frames, os.path.join(ASSETS, f"textures/item/tarot_{cid}.png"), {"animation": {"frametime": 5, "interpolate": True}})
    # the Deck of Fate: a stack of face-down cards, the top one shimmering
    frames = []
    back, gilt, dark = (92, 48, 142), (218, 172, 68), (36, 20, 52)
    for f in range(6):
        k = 0.5 + 0.5 * math.sin(f / 6 * math.tau)
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for layer in range(3, -1, -1):                               # four cards, each offset up and left
            ox, oy = 4 - layer, 1 + layer
            for y in range(12):
                for x in range(10):
                    edge = x in (0, 9) or y in (0, 11)
                    c = dark if edge else (shade(back, 0.6 + 0.1 * (3 - layer)) if layer else back)
                    if layer == 0 and not edge:
                        if x in (1, 8) or y in (1, 10):
                            c = gilt
                        d = abs(x - 4.5) + abs(y - 5.5)
                        if d < 2.6:
                            c = mix(gilt, (255, 255, 255), 0.6 * k)
                        elif d < 3.6:
                            c = shade(back, 1.35)
                    px[ox + x, oy + y] = c + (255,)
        frames.append(img)
    save_strip(frames, os.path.join(ASSETS, "textures/item/tarot_deck.png"), {"animation": {"frametime": 4, "interpolate": True}})

DOLL_SHAPE = [
    "................",
    "......bbbb......",
    ".....bbbbbb...h.",
    ".....bebbeb..h..",
    ".....bbbbbb.h...",
    "......bttb.h....",
    "...bbbbbbbph....",
    "...b.bbbppb.b...",
    ".....bbbpbb.....",
    ".....bbtbbb.....",
    ".....bbbbbb.....",
    ".....bb..bb.....",
    ".....bb..bb.....",
    "....bbb..bbb....",
]

def gen_dolls():
    for did, dname, ddesc, pin in DOLLS:
        burlap = (176, 142, 96)
        pal = {"b": burlap, "e": (30, 22, 18), "t": shade(burlap, 0.6), "p": pin, "h": mix(pin, (255, 255, 255), 0.5)}
        img = draw_template(DOLL_SHAPE, pal)
        px = img.load()
        rnd = random.Random(hsh("doll" + did))
        for y in range(16):                              # a little weave in the cloth
            for x in range(16):
                if px[x, y][:3] == burlap and rnd.random() < 0.3:
                    px[x, y] = shade(burlap, 0.86) + (255,)
        path = os.path.join(ASSETS, f"textures/item/voodoo_doll_{did}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)

GEM_SHAPE = ["................", "................", "......hhhh......", ".....hhbbbb.....", "....hhbbbbbb....", "...hhbbbbbbss...",
             "...hbbbbbbbss...", "...bbbbbbbsss...", "....bbbbbsss....", ".....bbbsss.....", "......bsss......", ".......ss.......", "................"]

KEYSTONE_SHAPE = ["................", "......oooo......", "....oossssoo....", "...osssssssso...", "..osshhhhhhsso..", "..oshbbbbbbhso..",
                  ".osshbbrrbbhsso.", ".osshbrrrrbhsso.", ".osshbrrrrbhsso.", ".osshbbrrbbhsso.", "..oshbbbbbbhso..", "..osshhhhhhsso..",
                  "...osssssssso...", "....oossssoo....", "......oooo......", "................"]

def gen_keystones_and_seekers():
    cols = {"ZEUS": (245, 196, 70), "POSEIDON": (60, 190, 215), "HADES": (170, 80, 230)}
    for line, col in cols.items():
        frames = []
        for f in range(6):
            k = 0.5 + 0.5 * math.sin(f / 6 * math.tau)
            pal = {"o": (40, 40, 48), "s": (118, 122, 128), "h": (170, 174, 180), "b": shade(col, 0.55), "r": mix(col, (255, 255, 255), 0.2 + 0.6 * k)}
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            for y, row in enumerate(KEYSTONE_SHAPE):
                for x, ch in enumerate(row):
                    if ch in pal:
                        img.putpixel((x, y), pal[ch] + (255,))
            frames.append(img)
        save_strip(frames, os.path.join(ASSETS, f"textures/item/keystone_{line.lower()}.png"), {"animation": {"frametime": 4, "interpolate": True}})
        frames = []
        for f in range(8):
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            px = img.load()
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - 7.5, y - 7.5)
                    if d < 7.4:
                        px[x, y] = shade(col, 0.22) + (255,)
                    if 5.6 <= d < 7.4:
                        px[x, y] = shade((150, 154, 164), 1.15 if (x + y) < 14 else 0.75) + (255,)
                    if 4.6 <= d < 5.6:
                        px[x, y] = shade(col, 0.5) + (255,)
            a = math.sin(f / 8 * math.tau) * 0.5 - math.pi / 4            # the needle sways about one heading
            for r in range(1, 5):
                px[int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))] = mix(col, (255, 255, 255), 0.35) + (255,)
                px[int(round(7.5 - math.cos(a) * r)), int(round(7.5 - math.sin(a) * r))] = (90, 92, 104, 255)
            px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = col + (255,)
            frames.append(img)
        save_strip(frames, os.path.join(ASSETS, f"textures/item/seeker_{line.lower()}.png"), {"animation": {"frametime": 3}})

def gen_essences_and_orbs():
    for line, (ename, col) in ESSENCES.items():
        frames = []
        for f in range(6):
            k = 0.5 + 0.5 * math.sin(f / 6 * math.tau)
            pal = {"b": col, "h": mix(col, (255, 255, 255), 0.45 + 0.4 * k), "s": shade(col, 0.6)}
            frames.append(draw_template(GEM_SHAPE, pal))
        save_strip(frames, os.path.join(ASSETS, f"textures/item/essence_{line.lower()}.png"), {"animation": {"frametime": 4, "interpolate": True}})
    for orb in ORBS:
        # the bolt's skin: a side view, bright along its length and brightest at the head, pulsing
        col = POWER_COLOR[orb.upper()]
        frames = []
        for f in range(4):
            pulse = 0.5 + 0.5 * math.sin(f / 4 * math.tau)
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            px = img.load()
            for y in range(16):
                for x in range(16):
                    off = abs(y - 7.5) / 7.5                       # 0 on the bolt's axis, 1 at the edge of the picture
                    along = x / 15.0                              # 0 at the tail, 1 at the point
                    core = max(0.0, 1.0 - off * 2.2)
                    c = mix(shade(col, 0.55 + 0.35 * along), (255, 255, 255), min(1.0, core * (0.55 + 0.45 * along) + 0.25 * pulse * along))
                    if (x + f) % 5 == 0 and off > 0.25:
                        c = mix(c, (255, 255, 255), 0.35)          # sparks running down it
                    px[x, y] = c + (255,)
            frames.append(img)
        save_strip(frames, os.path.join(ASSETS, f"textures/item/orb_{orb}.png"), {"animation": {"frametime": 2}})

BOLT_BOXES = [(3, 7, 7, 12, 9, 9), (11, 6, 6, 14, 10, 10), (14, 7, 7, 16, 9, 9), (8, 6.5, 6.5, 9, 9.5, 9.5),
              (0, 4.5, 7.5, 4, 11.5, 8.5), (0, 7.5, 4.5, 4, 8.5, 11.5)]

def bolt_model(texture):
    """A bolt as a solid thing: a shaft, a head and a point along x, a collar, and two crossed fins at the tail.
    Every face takes the strip of the side-view texture it would show, so the light runs the right way along it."""
    elements = []
    for x1, y1, z1, x2, y2, z2 in BOLT_BOXES:
        faces_ = {
            "north": {"uv": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "texture": "#bolt"},
            "south": {"uv": [x1, 16 - y2, x2, 16 - y1], "texture": "#bolt"},
            "up": {"uv": [x1, z1, x2, z2], "texture": "#bolt"},
            "down": {"uv": [x1, 16 - z2, x2, 16 - z1], "texture": "#bolt"},
            "west": {"uv": [max(0, x1 - 1), 16 - y2, x1 + 1, 16 - y1], "texture": "#bolt"},
            "east": {"uv": [x2 - 1, 16 - y2, min(16, x2 + 1), 16 - y1], "texture": "#bolt"},
        }
        elements.append({"from": [x1, y1, z1], "to": [x2, y2, z2], "shade": False, "faces": faces_})
    return {"textures": {"bolt": texture, "particle": texture}, "elements": elements}

def gen_materials():
    for line, mats in MATERIALS.items():
        for tier, (mname, mshape, col) in enumerate(mats, start=1):
            pal = {"b": col, "h": mix(col, (255, 255, 255), 0.5), "s": shade(col, 0.62), "g": (110, 150, 84), "p": (240, 214, 110)}
            img = draw_template(MATERIAL_SHAPES[mshape], pal)
            path = os.path.join(ASSETS, f"textures/item/material_{line.lower()}_{tier}.png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            img.save(path)

def gen_forge_textures():
    T = os.path.join(ASSETS, "textures/block")
    rnd = random.Random(99)
    noise = [[1 + rnd.uniform(-0.07, 0.07) for _ in range(16)] for _ in range(16)]
    body, hot = (58, 52, 76), (255, 150, 60)
    cols = [(245, 184, 46), (43, 183, 204), (165, 90, 214)]
    side_frames, top_frames = [], []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        side = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = shade(body, noise[y][x] * (1.15 if y < 2 else 0.8 if y > 13 else 1.0))
                if x in (0, 15):
                    c = shade(c, 0.75)
                if y in (3, 12) and 1 <= x <= 14:
                    c = shade(body, 0.55)                    # iron bands
                if 5 <= y <= 10 and 3 <= x <= 12:              # the furnace mouth, glowing
                    d = abs(x - 7.5) / 5 + abs(y - 7.5) / 3
                    c = mix((30, 14, 10), mix(hot, (255, 240, 180), k * 0.6), max(0, 1 - d * 0.9))
                side.putpixel((x, y), c + (255,))
        side_frames.append(side)
        top = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                d = max(abs(x - 7.5), abs(y - 7.5))
                c = shade((92, 92, 104), noise[y][x])
                if d > 6.6:
                    c = shade(body, 0.8)
                r = math.hypot(x - 7.5, y - 7.5)
                if 3.4 <= r < 4.6:                           # a ring in the three god colours
                    a = (math.atan2(y - 7.5, x - 7.5) + math.pi) / math.tau
                    c = mix(shade(cols[int(a * 3) % 3], 0.6), cols[int(a * 3) % 3], k)
                if r < 1.6:
                    c = mix(hot, (255, 255, 255), 0.5 * k)
                top.putpixel((x, y), c + (255,))
        top_frames.append(top)
    save_strip(side_frames, os.path.join(T, "chaos_forge_side.png"), {"animation": {"frametime": 3, "interpolate": True}})
    save_strip(top_frames, os.path.join(T, "chaos_forge_top.png"), {"animation": {"frametime": 3, "interpolate": True}})

def gen_recipes():
    """Voodoo dolls are stitched from wheat and string around a 'pin' that decides what the doll does."""
    for did, *_ in DOLLS:
        wj(os.path.join(DATA, f"recipe/voodoo_doll_{did}.json"), {
            "type": "minecraft:crafting_shaped",
            "category": "equipment",
            "key": {"S": "minecraft:string", "W": "minecraft:wheat", "X": DOLL_PINS[did]},
            "pattern": [" S ", "WXW", " W "],
            "result": {"id": f"{MOD}:voodoo_doll_{did}"},
        })

def gen_field_data():
    """The field network is made by hand: recipes for its three blocks and the bag, loot tables so the blocks drop
    themselves, and the pickaxe tag so they come up quickly."""
    recipes = {
        "relay_core": ({"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot", "R": "minecraft:redstone_block", "I": "minecraft:iron_ingot"},
                       [" A ", "CRC", "III"], "redstone"),
        "signal_jammer": ({"R": "minecraft:redstone", "E": "minecraft:ender_eye", "I": "minecraft:iron_ingot", "O": "minecraft:obsidian"},
                          [" R ", "IEI", "OOO"], "redstone"),
        "waygate_obelisk": ({"E": "minecraft:ender_eye", "A": "minecraft:amethyst_shard", "P": "minecraft:ender_pearl", "O": "minecraft:obsidian"},
                            [" E ", "APA", "OOO"], "misc"),
        "field_bag": ({"S": "minecraft:string", "L": "minecraft:leather", "C": "minecraft:chest"}, ["SLS", "LCL", "LLL"], "equipment"),
        "lockpick": ({"N": "minecraft:iron_nugget", "S": "minecraft:stick"}, ["N", "N", "S"], "equipment"),
    }
    for rid, (key, pattern, category) in recipes.items():
        wj(os.path.join(DATA, f"recipe/{rid}.json"), {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                                       "result": {"id": f"{MOD}:{rid}"}})
    for b in [d for d in FIELD_DROPS if not d.endswith("_slab")]:      # (a slab writes its own: two from a double)
        wj(os.path.join(DATA, f"loot_table/blocks/{b}.json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })
    wj(os.path.join(RES, "data/minecraft/tags/block/mineable/pickaxe.json"), {"replace": False, "values": [f"{MOD}:{b}" for b in FIELD_DROPS + PICKAXE_ONLY]})
    wj(os.path.join(RES, "data/minecraft/tags/block/mineable/axe.json"), {"replace": False, "values": [f"{MOD}:{b}" for b in AXE_ONLY]})

def gen_tags():
    """Item tags: realms_of_chaos:relics/<tower> holds the relic weapons won in that tower, and realms_of_chaos:relics
    holds them all. The Tower card reads these to send a player to the tower whose relics they hold fewest of."""
    for line, gods in LINES.items():
        wj(os.path.join(DATA, f"tags/item/relics/{line.lower()}.json"), {"values": [f"{MOD}:{g[0]}_weapon" for g in gods]})
    wj(os.path.join(DATA, "tags/item/relics.json"), {"values": [f"#{MOD}:relics/{line.lower()}" for line in LINES]})

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

    # passage seal: dark stone bound by a pulsing rune ring; passage shrine: pale stone with a glowing heart
    rnd = random.Random(4242)
    grain = [[1 + rnd.uniform(-0.08, 0.08) for _ in range(16)] for _ in range(16)]
    seal_frames, shrine_frames = [], []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        seal = Image.new("RGBA", (16, 16))
        shrine = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                edge = x in (0, 15) or y in (0, 15)
                c = shade((44, 40, 66), grain[y][x] * (0.7 if edge else 1.0))
                if 4.6 <= r < 6.0:
                    c = mix((70, 170, 200), (200, 250, 255), k)                       # the ring
                elif r < 4.6 and (abs(x - 7.5) < 1 or abs(y - 7.5) < 1 or abs(abs(x - 7.5) - abs(y - 7.5)) < 0.8):
                    c = mix((240, 190, 70), (255, 245, 190), 1 - k)                   # the rune inside it
                seal.putpixel((x, y), c + (255,))
                d = abs(x - 7.5) + abs(y - 7.5)
                c2 = shade((222, 222, 232), grain[y][x] * (0.8 if edge else 1.0))
                if d < 3.0:
                    c2 = mix((255, 215, 110), (255, 255, 235), k)                     # the heart of the shrine
                elif d < 5.0:
                    c2 = mix((90, 200, 190), (170, 240, 230), 1 - k)
                elif 6.0 <= d < 7.0:
                    c2 = shade((180, 180, 196), grain[y][x])
                shrine.putpixel((x, y), c2 + (255,))
        seal_frames.append(seal)
        shrine_frames.append(shrine)
    os.makedirs(T, exist_ok=True)
    save_strip(seal_frames, os.path.join(T, "passage_seal.png"), {"animation": {"frametime": 3, "interpolate": True}})
    # the top of the seal's crystal: a bright eye that opens and closes
    top_frames = []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        top = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                c = shade((44, 40, 66), grain[y][x])
                if r < 2.0 + 2.0 * k:
                    c = mix((240, 190, 70), (255, 255, 235), k)
                elif r < 5.5:
                    c = mix((40, 110, 140), (120, 220, 240), max(0.0, 1 - (r - 2) / 3.5) * (0.5 + 0.5 * k))
                elif r < 6.5:
                    c = (200, 250, 255)
                top.putpixel((x, y), c + (255,))
        top_frames.append(top)
    save_strip(top_frames, os.path.join(T, "passage_seal_top.png"), {"animation": {"frametime": 3, "interpolate": True}})
    save_strip(shrine_frames, os.path.join(T, "passage_shrine.png"), {"animation": {"frametime": 4, "interpolate": True}})

    # keystone pedestal: weathered stone with three coloured sockets; the Oracle's flame: a brazier of violet fire
    line_cols = [(245, 196, 70), (60, 190, 215), (170, 80, 230)]
    ped_top, ped_side, ora_top, ora_side = [], [], [], []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        pt, ps, ot, os_ = (Image.new("RGBA", (16, 16)) for _ in range(4))
        for y in range(16):
            for x in range(16):
                g = grain[y][x]
                r = math.hypot(x - 7.5, y - 7.5)
                # pedestal top: mossy stone, a triangle of three sockets round a central rune
                c = shade((112, 118, 112), g)
                if (x * 7 + y * 13) % 11 == 0:
                    c = shade((86, 120, 78), g)
                for i, lc in enumerate(line_cols):
                    a = -math.pi / 2 + i * math.tau / 3
                    if math.hypot(x - (7.5 + math.cos(a) * 4.2), y - (7.5 + math.sin(a) * 4.2)) < 1.6:
                        c = mix(shade(lc, 0.55), lc, k)
                if r < 1.5:
                    c = mix((200, 200, 215), (255, 255, 255), k)
                pt.putpixel((x, y), c + (255,))
                # pedestal side: stone courses with a carved band
                c = shade((104, 110, 106), g * (0.8 if y % 4 == 3 else 1.0))
                if (x * 5 + y * 3) % 13 == 0:
                    c = shade((80, 112, 74), g)
                if 6 <= y <= 9 and (x + y) % 4 < 2:
                    c = mix(shade(line_cols[(x // 5) % 3], 0.5), line_cols[(x // 5) % 3], k * 0.8)
                ps.putpixel((x, y), c + (255,))
                # oracle top: a bowl of fire
                c = shade((58, 50, 78), g)
                if r < 6.5:
                    heat = max(0.0, 1 - r / 6.5)
                    flick = 0.5 + 0.5 * math.sin(f / 8 * math.tau + x * 1.3 + y * 0.9)
                    c = mix((70, 30, 120), mix((190, 110, 255), (255, 240, 255), heat), min(1, heat * (0.8 + 0.6 * flick)))
                elif r < 7.5:
                    c = shade((214, 170, 74), g)
                ot.putpixel((x, y), c + (255,))
                # oracle side: dark bronze with rising violet glyphs
                c = shade((66, 56, 86), g * (0.75 if x in (0, 15) else 1.0))
                if y in (2, 13):
                    c = shade((214, 170, 74), g)
                if 4 <= y <= 11 and x % 4 == 1 and (y + f) % 4 < 2:
                    c = mix((120, 60, 200), (230, 190, 255), k)
                os_.putpixel((x, y), c + (255,))
        ped_top.append(pt); ped_side.append(ps); ora_top.append(ot); ora_side.append(os_)
    save_strip(ped_top, os.path.join(T, "gate_pedestal_top.png"), {"animation": {"frametime": 4, "interpolate": True}})
    save_strip(ped_side, os.path.join(T, "gate_pedestal_side.png"), {"animation": {"frametime": 4, "interpolate": True}})
    save_strip(ora_top, os.path.join(T, "oracle_top.png"), {"animation": {"frametime": 2, "interpolate": True}})
    save_strip(ora_side, os.path.join(T, "oracle_side.png"), {"animation": {"frametime": 3, "interpolate": True}})

    cache_frames = []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f / 8 * math.tau)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = shade((92, 60, 38), grain[y][x] * (0.8 if x in (0, 15) or y in (0, 15) else 1.0))
                if y in (4, 5) or x in (3, 12):
                    c = shade((214, 170, 74), 1.1 if (x + y) % 3 else 0.85)                  # gold bands
                if 6 <= x <= 9 and 6 <= y <= 10:
                    c = mix((150, 110, 40), (255, 240, 170), k) if (x in (7, 8) and y in (7, 8, 9)) else (60, 44, 30)   # the lock
                img.putpixel((x, y), c + (255,))
        cache_frames.append(img)
    save_strip(cache_frames, os.path.join(T, "passage_cache.png"), {"animation": {"frametime": 4, "interpolate": True}})

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

def gen_field_textures():
    """Side and top textures of the shield pylon, the three field-network blocks, the Heart of the Hub and the
    Bounty Board, and the Field Tower Bag's icon. The glowing ones pulse."""
    T = os.path.join(ASSETS, "textures/block")
    os.makedirs(T, exist_ok=True)

    def stone_pair(name, body, glow, sigil, frames=8, band_rows=(3, 12)):
        """A carved block: noisy stone, darker edges, glowing bands and a sigil (8x8, '#') on every side."""
        rnd = random.Random(hsh("field" + name))
        noise = [[1 + rnd.uniform(-0.07, 0.07) for _ in range(16)] for _ in range(16)]
        sides, tops = [], []
        for f in range(frames):
            k = 0.5 + 0.5 * math.sin(f / frames * math.tau)
            g = mix(shade(glow, 0.6), mix(glow, (255, 255, 255), 0.4), k)
            side = Image.new("RGBA", (16, 16))
            top = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    c = shade(body, noise[y][x] * (1.1 if y < 2 else 0.84 if y > 13 else 1.0))
                    if x in (0, 15):
                        c = shade(c, 0.78)
                    if y in band_rows:
                        c = shade(g, 0.85 if x % 2 else 1.0)
                    sy, sx = y - 4, x - 4
                    if 0 <= sy < 8 and 0 <= sx < 8 and sigil[sy][sx] == "#":
                        c = g
                    side.putpixel((x, y), c + (255,))
                    r = math.hypot(x - 7.5, y - 7.5)
                    t = shade(body, noise[x][y])
                    if max(abs(x - 7.5), abs(y - 7.5)) > 6.6:
                        t = shade(body, 0.7)
                    if 3.4 <= r < 4.6:
                        t = g
                    if r < 1.8:
                        t = mix(g, (255, 255, 255), 0.4 * k)
                    top.putpixel((x, y), t + (255,))
            sides.append(side)
            tops.append(top)
        meta = {"animation": {"frametime": 4, "interpolate": True}}
        save_strip(sides, os.path.join(T, f"{name}_side.png"), meta)
        save_strip(tops, os.path.join(T, f"{name}_top.png"), meta)

    shield = ["..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#..#.#", ".#.##.#.", "..#..#..", "...##..."]
    waves = ["...##...", "..#..#..", ".#....#.", "#..##..#", "..#..#..", ".#....#.", "...##...", "........"]
    cross = ["#......#", ".#....#.", "..#..#..", "...##...", "...##...", "..#..#..", ".#....#.", "#......#"]
    arch = ["..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", "#......#", "#......#"]
    heart = ["........", ".##..##.", "########", "########", ".######.", "..####..", "...##...", "........"]
    stone_pair("shield_pylon", (70, 96, 140), (130, 220, 255), shield)
    stone_pair("relay_core", (58, 78, 92), (90, 240, 230), waves)
    stone_pair("signal_jammer", (64, 44, 50), (255, 90, 80), cross)
    stone_pair("waygate_obelisk", (40, 30, 62), (190, 110, 255), arch)
    stone_pair("hub_heart", (74, 52, 110), (245, 184, 46), heart, band_rows=(2, 13))

    # ---- the passages' room blocks
    def tile(name, base, draw, noise=0.07):
        rnd_ = random.Random(hsh("room" + name))
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                c = shade(base, (1 + rnd_.uniform(-noise, noise)) * (0.78 if x in (0, 15) or y in (0, 15) else 1.0))
                d = draw(x, y)
                img.putpixel((x, y), (d if d is not None else c) + (255,))
        img.save(os.path.join(T, name + ".png"))

    spiral = ["................", "..############..", "..#..........#..", "..#.########.#..", "..#.#......#.#..", "..#.#.####.#.#..", "..#.#.#..#.#.#..", "..#.#.#.##.#.#..",
              "..#.#.#....#.#..", "..#.#.######.#..", "..#.#........#..", "..#.##########..", "..#.............", "..#############.", "................", "................"]
    for lit in (False, True):
        g = (120, 240, 250) if lit else (60, 84, 96)
        tile("glyph_tile" + ("_lit" if lit else ""), (70, 72, 84), lambda x, y: g if spiral[y][x] == "#" else ((84, 110, 120) if lit and 2 <= x <= 13 and 2 <= y <= 13 else None))
        gold = (250, 215, 90) if lit else (118, 104, 70)
        tile("memory_plate" + ("_lit" if lit else ""), (96, 96, 104), lambda x, y: gold if (x in (3, 12) and 3 <= y <= 12) or (y in (3, 12) and 3 <= x <= 12) else
             (mix((96, 96, 104), gold, 0.45) if lit and 4 <= x <= 11 and 4 <= y <= 11 else (shade((96, 96, 104), 0.86) if 4 <= x <= 11 and 4 <= y <= 11 else None)))
        ring = (255, 250, 200) if lit else (40, 40, 48)
        tile("beam_receiver" + ("_lit" if lit else ""), (60, 62, 76), lambda x, y: ring if math.hypot(x - 7.5, y - 7.5) < 3.2 else ((200, 170, 80) if math.hypot(x - 7.5, y - 7.5) < 4.6 else None))
        crystal = (214, 170, 255) if lit else (120, 90, 170)
        tile("chime_crystal" + ("_lit_side" if lit else "_side"), crystal, lambda x, y: mix(crystal, (255, 255, 255), 0.55) if (x + y) % 5 == 0 else (shade(crystal, 0.75) if (x - y) % 4 == 0 else None), 0.04)
        tile("chime_crystal" + ("_lit_top" if lit else "_top"), mix(crystal, (255, 255, 255), 0.3), lambda x, y: (255, 255, 255) if 6 <= x <= 9 and 6 <= y <= 9 else None, 0.04)
        # a mirror seen from above: a bright line corner to corner, one way or the other
        tile("beam_mirror" + ("_lit_top" if lit else "_top"), (50, 52, 64), lambda x, y: (230, 240, 250) if abs((x if not lit else 15 - x) - y) <= 1 else None)
        tile("beam_mirror" + ("_lit_side" if lit else "_side"), (70, 74, 90), lambda x, y: (200, 215, 230) if 3 <= x <= 12 and 3 <= y <= 13 and (x + y) % 6 != 0 else None)
    tile("beam_emitter", (60, 62, 76), lambda x, y: (255, 240, 150) if math.hypot(x - 7.5, y - 7.5) < 2.4 else ((230, 180, 70) if math.hypot(x - 7.5, y - 7.5) < 4.2 else None))
    tile("dart_tile", (104, 100, 96), lambda x, y: (24, 22, 22) if (x in (4, 5, 10, 11) and y in (4, 5, 10, 11)) else ((150, 60, 50) if x in (1, 14) and y in (1, 14) else None))
    def urn_side(x, y):
        terra = (196, 112, 64)
        c = shade(terra, 1.12 if x < 5 else 0.82 if x > 11 else 1.0)            # lit on its left, round
        if y in (1, 2) or y in (12, 13): c = (34, 22, 18)                          # black bands
        if y == 2 and x % 4 in (0, 1): c = (210, 130, 80)                           # a meander in the band
        if 4 <= y <= 10:
            # a black-figure warrior: shield, spear, running legs
            fig = ["................", "......##........", ".....####...#...", "....######.#....", "....#####.#.....", ".....###.#......",
                   "....#...#.......", "...#.....#......", "................"]
            row = fig[y - 3] if 0 <= y - 3 < len(fig) else ""
            if x < len(row) and row[x] == "#": c = (34, 22, 18)
        if y in (0, 15): c = (90, 54, 38)
        return c
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), urn_side(x, y) + (255,))
    img.save(os.path.join(T, "ancient_urn_side.png"))
    tile("ancient_urn_top", (150, 94, 62), lambda x, y: (40, 24, 18) if math.hypot(x - 7.5, y - 7.5) < 3.0 else None)
    tile("lore_tablet_side", (128, 126, 118), lambda x, y: (70, 68, 64) if y in (4, 6, 8, 10, 12) and 4 <= x <= 11 and (x * 3 + y) % 5 != 0 else ((236, 196, 84) if y == 2 and 5 <= x <= 10 else None))
    tile("lore_tablet_top", (120, 118, 110), lambda x, y: None)

    # ---- every puzzle block is cut from the same stone as the rune stones: grey, grained, a frame scored a little
    # way in from its edge, and one sign carved in its middle that is dull until it is lit
    STONE = (104, 104, 116)
    stone_rnd = random.Random(hsh("room_stone"))
    stone_grain = [[1 + stone_rnd.uniform(-0.08, 0.08) for _ in range(16)] for _ in range(16)]

    def carved(name, sign, glow, lit, base=STONE, frame=True):
        """A face of dressed stone: bevelled at its edges, a sunken panel in its middle with a chamfer round it, and
        an 8x8 sign cut into the panel - a groove with a lit lip below it when dark, a glowing inlay with a halo
        round it and gold studs at the panel's corners when lit."""
        img = Image.new("RGBA", (16, 16))
        on = lambda rx, ry: sign is not None and 0 <= ry < 8 and 0 <= rx < 8 and sign[ry][rx] == "#"
        for y in range(16):
            for x in range(16):
                g = stone_grain[y][x]
                c = shade(base, g)
                if x == 0 or y == 0: c = shade(base, g * 1.14)                  # the lit edges
                if x == 15 or y == 15: c = shade(base, g * 0.66)                # and the shadowed ones
                if frame and 2 <= x <= 13 and 2 <= y <= 13:
                    if x == 2 or y == 2: c = shade(base, 0.7)                   # the panel is sunk: shadow on its top and left
                    elif x == 13 or y == 13: c = shade(base, 1.1)               # light on its bottom and right
                    else: c = shade(base, g * 0.9)
                rx, ry = x - 4, y - 4
                if on(rx, ry):
                    c = mix(glow, (255, 255, 255), 0.3) if lit else shade(base, 0.48)
                elif sign is not None and not lit and on(rx - 1, ry - 1):
                    c = shade(base, 1.18)                                       # the lip the light catches
                elif lit and 3 <= x <= 12 and 3 <= y <= 12:
                    near = any(on(rx + dx, ry + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                    c = mix(c, glow, 0.5 if near else 0.16)
                if frame and (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                    c = mix(glow, (255, 255, 255), 0.4) if lit else (150, 118, 60)  # studs, bronze until lit
                img.putpixel((x, y), c + (255,))
        img.save(os.path.join(T, name + ".png"))

    eye = ["..####..", ".#....#.", "#..##..#", "#.####.#", "#.####.#", "#..##..#", ".#....#.", "..####.."]
    step = ["...##...", "..#..#..", ".#....#.", "#..##..#", "#..##..#", ".#....#.", "..#..#..", "...##..."]
    ring = ["..####..", ".######.", "##....##", "##....##", "##....##", "##....##", ".######.", "..####.."]
    sun = ["#..##..#", ".#.##.#.", "..####..", "########", "########", "..####..", ".#.##.#.", "#..##..#"]
    barbs = [".#....#.", "###..###", ".#....#.", "........", "........", ".#....#.", "###..###", ".#....#."]
    bell = ["...##...", "..####..", "..#..#..", ".##..##.", ".#....#.", "##....##", "########", "...##..."]
    script = ["######..", "........", "#######.", "........", "#####...", "........", "#######.", "........"]
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        carved("glyph_tile" + suffix, eye, (90, 220, 240), lit)
        carved("memory_plate" + suffix, step, (250, 215, 90), lit)
        carved("beam_receiver" + suffix, ring, (255, 240, 170), lit)
        crystal = (196, 120, 255) if lit else (120, 84, 150)
        for face in ("side", "top"):
            img = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    # long facets running up the crystal, each lit on one side; a bright vein down the middle
                    f = (x + (y // 6)) % 4
                    c = shade(crystal, {0: 0.62, 1: 1.0, 2: 1.18, 3: 0.82}[f])
                    if face == "side" and x in (7, 8) and (y + (3 if lit else 0)) % 5 < 3: c = mix(c, (255, 255, 255), 0.55 if lit else 0.2)
                    if face == "top" and math.hypot(x - 7.5, y - 7.5) < 2.5: c = mix(crystal, (255, 255, 255), 0.7 if lit else 0.25)
                    if (x + y * 3) % 17 == 0: c = mix(c, (255, 255, 255), 0.6)          # a glint
                    img.putpixel((x, y), c + (255,))
            img.save(os.path.join(T, "chime_crystal" + suffix + "_" + face + ".png"))
        # a mirror: a pane of polished silver set in a stone frame, the glint across it leaning the way it is turned
        for face in ("side", "top"):
            img = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    c = shade(STONE, stone_grain[y][x] * (0.76 if x in (0, 15) or y in (0, 15) else 1.0))
                    if 2 <= x <= 13 and 2 <= y <= 13:
                        edge = x in (2, 13) or y in (2, 13)
                        d = (x + y) if lit else (x - y + 15)                     # which way the glint leans
                        pane = mix((92, 112, 140), (214, 232, 248), min(1.0, max(0.0, (d - 6) / 18)))
                        if abs(d - 15) <= 1:
                            pane = (250, 253, 255)                              # the glint itself
                        elif abs(d - 15) == 2 or abs(d - 9) == 0:
                            pane = mix(pane, (255, 255, 255), 0.45)
                        c = shade((46, 50, 64), 0.9) if edge else pane
                        if not edge and (x in (3, 12) or y in (3, 12)):
                            c = shade(c, 0.86)                                  # the pane sits a little way into its frame
                    if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                        c = (236, 196, 84)                                      # gold studs in the frame's corners
                    img.putpixel((x, y), c + (255,))
            img.save(os.path.join(T, "beam_mirror" + suffix + "_" + face + ".png"))
    carved("beam_emitter", sun, (255, 230, 130), True)
    carved("dart_tile", barbs, (230, 80, 70), False)
    carved("lore_tablet_side", script, (236, 196, 84), False)
    carved("lore_tablet_top", None, (236, 196, 84), False)
    tile("tower_brazier_side", (84, 70, 56), lambda x, y: (150, 120, 70) if y in (4, 5) else ((54, 44, 36) if y > 12 else None))
    for line, fire in (("zeus", (255, 240, 140)), ("poseidon", (90, 230, 240)), ("hades", (170, 110, 255))):
        tile(f"tower_brazier_{line}_top", (60, 50, 42), lambda x, y: mix(fire, (255, 255, 255), max(0.0, 0.7 - math.hypot(x - 7.5, y - 7.5) / 6)) if math.hypot(x - 7.5, y - 7.5) < 5.2 else None)

    # rune stones: four runes carved in grey stone, dark or alight
    runes = [["#......#", "##....##", "#.#..#.#", "#..##..#", "#..##..#", "#.#..#.#", "##....##", "#......#"],
             ["...##...", "..####..", ".#.##.#.", "#..##..#", "...##...", "...##...", "...##...", "...##..."],
             ["########", "#......#", "#.####.#", "#.#..#.#", "#.#..#.#", "#.####.#", "#......#", "########"],
             ["#..#..#.", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "#..#..#.", "........"]]
    rune_glow = [(250, 215, 90), (90, 220, 240), (200, 120, 255), (120, 240, 140)]
    rnd = random.Random(hsh("rune_stone"))
    grain = [[1 + rnd.uniform(-0.08, 0.08) for _ in range(16)] for _ in range(16)]
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            top.putpixel((x, y), shade((112, 112, 124), grain[y][x] * (0.78 if x in (0, 15) or y in (0, 15) else 1.0)) + (255,))
    top.save(os.path.join(T, "rune_stone_top.png"))
    for n, rune in enumerate(runes):
        for lit in (False, True):
            img = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    c = shade((104, 104, 116), grain[x][y] * (0.76 if x in (0, 15) or y in (0, 15) else 1.0))
                    if x in (2, 13) and 2 <= y <= 13 or y in (2, 13) and 2 <= x <= 13:
                        c = shade(c, 0.82)
                    ry, rx = y - 4, x - 4
                    if 0 <= ry < 8 and 0 <= rx < 8 and rune[ry][rx] == "#":
                        c = mix(rune_glow[n], (255, 255, 255), 0.25) if lit else shade(mix(rune_glow[n], (60, 60, 70), 0.72), 0.9)
                    elif lit and 3 <= x <= 12 and 3 <= y <= 12:
                        c = mix(c, rune_glow[n], 0.22)
                    img.putpixel((x, y), c + (255,))
            img.save(os.path.join(T, f"rune_stone_{n}" + ("_lit" if lit else "") + ".png"))

    # the Bounty Board: a plank board with three pinned notes
    rnd = random.Random(hsh("bounty_board"))
    side = Image.new("RGBA", (16, 16))
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            wood = shade((122, 84, 50), (0.9 if y % 4 == 3 else 1.0) * (1 + rnd.uniform(-0.06, 0.06)))
            if x in (0, 15) or y in (0, 15):
                wood = shade((84, 56, 34), 1 + rnd.uniform(-0.05, 0.05))
            side.putpixel((x, y), wood + (255,))
            top.putpixel((x, y), shade((96, 66, 40), (0.9 if x % 4 == 3 else 1.0) * (1 + rnd.uniform(-0.06, 0.06))) + (255,))
    for (nx, ny, col) in ((2, 2, (238, 228, 204)), (9, 3, (226, 214, 180)), (5, 9, (240, 232, 210))):
        for y in range(5):
            for x in range(5):
                c = shade(col, 0.9 if y == 4 or x == 4 else 1.0)
                if y in (1, 3) and 1 <= x <= 3:
                    c = (96, 84, 70)
                side.putpixel((nx + x, ny + y), c + (255,))
        side.putpixel((nx + 2, ny), (200, 50, 50, 255))
    side.save(os.path.join(T, "bounty_board_side.png"))
    top.save(os.path.join(T, "bounty_board_top.png"))

    # the Field Tower Bag
    bag = """
................
.....oooooo.....
....oSSSSSSo....
...oSooooooSo...
...oLLLLLLLLo...
..oLLhLLLLLLLo..
..oLhLLLLLLLLo..
..oLLLLggLLLLo..
..oDDDDggDDDDo..
..oLLLLLLLLLLo..
..oLLtLLLLtLLo..
..oLLtttttLLLo..
..oLLLLLLLLLLo..
...oDDDDDDDDo...
....oooooooo....
................"""
    pal = {"o": (40, 26, 18), "S": (170, 150, 120), "L": (140, 92, 54), "h": (176, 124, 78), "D": (104, 66, 38), "g": (236, 196, 84),
           "t": (90, 240, 230)}
    rows = [(r + "." * 16)[:16] for r in bag.strip("\n").split("\n")]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    img.save(os.path.join(ASSETS, "textures/item/field_bag.png"))

# ----------------------------------------------------------------------------
# The mod's own menu artwork. A chest screen cannot be given a new background by a server - but its title can be
# written in a font, and a font's letters can be pictures. So each menu has one "letter" that is its whole panel,
# drawn from the top-left corner of the screen; the title text follows it. Every slot is left as a hole in the
# picture, so whatever the game draws there - an icon, or the mod's own blank tile - always shows.
# ----------------------------------------------------------------------------
UI_BASE, UI_DARK, UI_WELL, UI_PARCH, UI_PARCH_D = (38, 32, 80), (22, 18, 43), (28, 23, 60), (214, 196, 160), (150, 128, 96)
# name: (rows of the menu, accent colour, the 9x9 emblem in its header, glyph)
UI_PANELS = {
    "codex": (6, (165, 90, 214), ["..#####..", ".#.....#.", ".#.###.#.", ".#.#.#.#.", ".#.###.#.", ".#.....#.", ".#.###.#.", ".#.....#.", "..#####.."], "\uE000"),
    "forge": (6, (255, 140, 40), [".........", ".#######.", ".#######.", "..#####..", "...###...", "...###...", "..#####..", ".#######.", "........."], "\uE001"),
    "passage": (6, (60, 190, 215), ["....#....", "...###...", "..#.#.#..", ".#..#..#.", "#########", ".#..#..#.", "..#.#.#..", "...###...", "....#...."], "\uE002"),
    "bag6": (6, (176, 124, 78), ["..#####..", ".#.....#.", ".#######.", "#########", "#...#...#", "#..###..#", "#...#...#", "#########", ".#######."], "\uE003"),
    "altar": (3, (245, 184, 46), ["....#....", "...###...", "..##.##..", "..#...#..", "..##.##..", "...###...", ".#######.", ".#######.", "#########"], "\uE004"),
    "seal": (3, (120, 240, 250), ["....#....", "...#.#...", "..#...#..", ".#..#..#.", "#..###..#", ".#..#..#.", "..#...#..", "...#.#...", "....#...."], "\uE005"),
    "deck": (6, (190, 80, 250), [".#######.", ".#.....#.", ".#..#..#.", ".#.###.#.", ".#..#..#.", ".#.....#.", ".#..#..#.", ".#.....#.", ".#######."], "\uE006"),
    "party": (6, (90, 220, 120), [".........", ".##...##.", ".##...##.", ".........", "####.####", "####.####", "####.####", "####.####", "........."], "\uE007"),
    "story": (6, (236, 208, 150), ["#########", "#.......#", "#.#####.#", "#.......#", "#.#####.#", "#.......#", "#.###...#", "#.......#", "#########"], "\uE008"),
    "bag3": (3, (176, 124, 78), ["..#####..", ".#.....#.", ".#######.", "#########", "#...#...#", "#..###..#", "#...#...#", "#########", ".#######."], "\uE009"),
    "settings": (3, (150, 160, 190), [".........", "..##.....", "#########", "..##.....", ".........", ".....##..", "#########", ".....##..", "........."], "\uE00A"),
    "insight": (6, (255, 170, 60), [".........", "..#####..", ".#.....#.", "#..###..#", "#..###..#", "#..###..#", ".#.....#.", "..#####..", "........."], "\uE00B"),
    "boon": (3, (120, 230, 140), ["....#....", "...###...", "..#####..", ".#######.", "#########", "...###...", "...###...", "...###...", "........."], "\uE00C"),
    "bestiary": (6, (220, 80, 80), [".#.....#.", ".##...##.", ".#######.", ".#.###.#.", ".#######.", "..#####..", "..#.#.#..", ".........", "........."], "\uE00D"),
}
UI_DYES = {"white": (236, 236, 240), "orange": (240, 140, 50), "magenta": (200, 90, 200), "light_blue": (110, 190, 240), "yellow": (250, 215, 80), "lime": (130, 220, 80),
           "pink": (245, 160, 190), "gray": (90, 90, 100), "light_gray": (160, 160, 168), "cyan": (60, 180, 190), "purple": (150, 80, 210), "blue": (70, 90, 210),
           "brown": (130, 90, 60), "green": (80, 150, 70), "red": (210, 60, 60), "black": (30, 28, 36)}

def gen_ui():
    G = os.path.join(ASSETS, "textures/gui")
    I = os.path.join(ASSETS, "textures/item/ui")
    os.makedirs(G, exist_ok=True)
    os.makedirs(I, exist_ok=True)
    providers = [{"type": "space", "advances": {"\uF800": -8, "\uF801": -169}}]
    for name, (rows, accent, emblem, glyph) in UI_PANELS.items():
        height = 114 + rows * 18
        split = 18 + rows * 18 + 3                         # where the menu's own half ends and the player's begins
        rnd = random.Random(hsh("ui" + name))
        img = Image.new("RGBA", (176, height), (0, 0, 0, 0))
        px = img.load()
        for y in range(height):
            for x in range(176):
                corner = (x < 2 or x > 173) and (y < 2 or y > height - 3)
                if corner and (x in (0, 175) or y in (0, height - 1)):
                    continue                                # rounded corners
                edge = x < 2 or x > 173 or y < 2 or y > height - 3
                if y < split:
                    c = UI_DARK if edge else UI_BASE
                    if not edge and y < 16:                # the header band, in the menu's own colour
                        c = mix(shade(accent, 0.34), shade(accent, 0.2), y / 16)
                    if not edge and y == 16:
                        c = GOLD if x % 2 == 0 else shade(GOLD, 0.75)
                else:
                    c = shade(UI_PARCH_D, 0.8) if edge else shade(UI_PARCH, 1 + rnd.uniform(-0.025, 0.025))
                    if not edge and y == split:
                        c = UI_PARCH_D
                px[x, y] = c + (255,)
        for sy, row in enumerate(emblem):                  # the emblem, top right of the header
            for sx, ch in enumerate(row):
                if ch == "#":
                    px[160 + sx, 4 + sy] = mix(accent, (255, 255, 255), 0.35) + (255,)
        for cx in (4, 171):                                # gold studs in the header's corners
            px[cx, 4] = GOLD + (255,)
        def well(x0, y0, rim, lip):
            for dy in range(-1, 17):
                for dx in range(-1, 17):
                    if dx in (-1, 16) or dy in (-1, 16):
                        px[x0 + dx, y0 + dy] = (rim if dx == -1 or dy == -1 else lip) + (255,)
                    else:
                        px[x0 + dx, y0 + dy] = (0, 0, 0, 0)        # the slot itself: a hole
        for r in range(rows):
            for c in range(9):
                well(8 + c * 18, 18 + r * 18, UI_DARK, shade(UI_BASE, 1.35))
        shift = (rows - 4) * 18
        for r in range(3):
            for c in range(9):
                well(8 + c * 18, 103 + r * 18 + shift, UI_PARCH_D, mix(UI_PARCH, (255, 255, 255), 0.5))
        for c in range(9):
            well(8 + c * 18, 161 + shift, UI_PARCH_D, mix(UI_PARCH, (255, 255, 255), 0.5))
        img.save(os.path.join(G, f"panel_{name}.png"))
        # the same panel as a screen's background: there the slots are part of the picture
        screen = img.copy()
        sp = screen.load()
        for y in range(height):
            for x in range(176):
                if sp[x, y][3] == 0 and 2 <= x <= 173 and 2 <= y <= height - 3:
                    sp[x, y] = (UI_WELL if y < split else shade(UI_PARCH, 0.82)) + (255,)
        screen.save(os.path.join(G, f"screen_{name}.png"))
        providers.append({"type": "bitmap", "file": f"{MOD}:gui/panel_{name}.png", "ascent": 13, "height": height, "chars": [glyph]})
    wj(os.path.join(ASSETS, "font/ui.json"), {"providers": providers})

    # icons: the blank tile that fills an unused slot, and a gem for every colour a meter or a label can have
    def icon(name, img):
        img.save(os.path.join(I, name + ".png"))
        wj(os.path.join(ASSETS, f"items/ui/{name}.json"), {"model": {"type": "minecraft:model", "model": f"{MOD}:item/ui/{name}"}})
        wj(os.path.join(ASSETS, f"models/item/ui/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/ui/{name}"}})
    tile = Image.new("RGBA", (16, 16), UI_WELL + (255,))
    for i in range(16):
        tile.putpixel((i, 0), shade(UI_WELL, 0.8) + (255,)); tile.putpixel((0, i), shade(UI_WELL, 0.8) + (255,))
        tile.putpixel((i, 15), shade(UI_WELL, 1.25) + (255,)); tile.putpixel((15, i), shade(UI_WELL, 1.25) + (255,))
    icon("tile", tile)
    for dye, col in UI_DYES.items():
        gem = Image.new("RGBA", (16, 16), UI_WELL + (255,))
        for y in range(2, 14):
            for x in range(2, 14):
                rounded = (x in (2, 13)) and (y in (2, 13))
                if rounded:
                    continue
                c = shade(col, 1.0 - 0.03 * (y - 2))
                if x == 2 or y == 2:
                    c = mix(col, (255, 255, 255), 0.45)
                elif x == 13 or y == 13:
                    c = shade(col, 0.6)
                elif (x, y) in ((4, 4), (5, 4), (4, 5)):
                    c = mix(col, (255, 255, 255), 0.7)
                gem.putpixel((x, y), c + (255,))
        icon(f"gem_{dye}", gem)

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
# the skin's outer layer: drawn a little larger than the part under it, so what is painted here stands proud
PARTS.update({"jacket": faces(16, 32, 8, 12, 4), "rsleeve": faces(40, 32, 4, 12, 4), "lsleeve": faces(48, 48, 4, 12, 4),
              "rpants": faces(0, 32, 4, 12, 4), "lpants": faces(0, 48, 4, 12, 4)})

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

# What a god's aegis lights it with, what its wrath burns with, and the colour of the cape its three greatest wear
PHASE_GLOW = {"ZEUS": (255, 238, 140), "POSEIDON": (110, 240, 255), "HADES": (196, 120, 255)}
WRATH_CORE = {"ZEUS": (255, 255, 224), "POSEIDON": (214, 255, 255), "HADES": (255, 122, 70)}
CAPE = {"ZEUS": (116, 58, 150), "POSEIDON": (22, 84, 112), "HADES": (88, 22, 34)}
BACK_MOTIF = {
    "ZEUS": ["...##...", "..##....", ".####...", "...##...", "..##....", ".#......"],             # a thunderbolt
    "POSEIDON": [".#.##.#.", ".#.##.#.", ".######.", "...##...", "...##...", "...##..."],        # a trident
    "HADES": ["..####..", ".#.##.#.", ".######.", "..#..#..", "..####..", "........"],            # a skull
}

class SkinEdit:
    """Reads and writes a finished skin part by part, for the passes that run over what make_skin drew."""
    def __init__(self, img, seed):
        self.img = img
        self.px = img.load()
        self.rnd = random.Random(seed)

    def size(self, part, face):
        return PARTS[part][face][2], PARTS[part][face][3]

    def get(self, part, face, x, y):
        u, v, wd, ht = PARTS[part][face]
        return self.px[u + x, v + y] if 0 <= x < wd and 0 <= y < ht else None

    def put(self, part, face, x, y, c):
        u, v, wd, ht = PARTS[part][face]
        if 0 <= x < wd and 0 <= y < ht:
            self.px[u + x, v + y] = tuple(int(k) for k in c[:3]) + (255,)

    def scale(self, part, face, x, y, f):
        c = self.get(part, face, x, y)
        if c is not None and c[3] > 0:
            self.put(part, face, x, y, shade(c[:3], f))

    def tint(self, part, face, x, y, col, t):
        c = self.get(part, face, x, y)
        if c is not None and c[3] > 0:
            self.put(part, face, x, y, mix(c[:3], col, t))

def polish_skin(img, gid, line, rank):
    """Detail every god's skin gets: shadow under the chin, shaded hands and feet, strands in the hair, the mark of
    its tower on its back, gold on the arms of the stronger gods and a cape on the three greatest."""
    outfit, cloth, trim, hair, skin, gear, gear_col, beard, long_hair = LOOKS[gid]
    e = SkinEdit(img, hsh("polish" + gid))
    # shadow the head casts on the shoulders, and the body on the tops of the legs
    for face in SIDES:
        wd, _ = e.size("body", face)
        for x in range(wd):
            if not (rank <= 3 and x % 2 == 0):                       # (leave the gold collar bright)
                e.scale("body", face, x, 0, 0.86)
        for leg in ("rleg", "lleg"):
            for x in range(4):
                e.scale(leg, face, x, 0, 0.88)
    # hands and feet a touch darker than the limb, the inside of each arm darker still
    for face in SIDES:
        for arm in ("rarm", "larm"):
            for x in range(4):
                e.scale(arm, face, x, 11, 0.86)
                e.scale(arm, face, x, 10, 0.94)
        for leg in ("rleg", "lleg"):
            for x in range(4):
                e.scale(leg, face, x, 11, 0.9)
    for y in range(12):
        e.scale("rarm", "left", 0, y, 0.88); e.scale("larm", "right", 3, y, 0.88)
    # strands in the hair
    for y in range(8):
        for x in range(8):
            if (x * 5 + y * 3) % 7 == 0:
                e.scale("head", "top", x, y, 0.86)
            elif (x * 3 + y * 5) % 11 == 0:
                e.scale("head", "top", x, y, 1.12)
    for x in range(8):
        for y in range(8 if long_hair else 4):
            if x % 3 == 1:
                e.scale("head", "back", x, y, 0.88)
    # shadow under the brow and along the jaw
    e.scale("head", "front", 0, 4, 0.9); e.scale("head", "front", 7, 4, 0.9)
    e.scale("head", "front", 0, 7, 0.88); e.scale("head", "front", 7, 7, 0.88)
    # the mark of its tower, carried on its back
    motif_col = mix(trim, (255, 255, 255), 0.2) if sum(trim) > sum(cloth) else shade(trim, 0.8)
    if rank <= 3:
        # the three greatest of each tower wear a cape over it all, hemmed in gold
        top_row = 4 if long_hair else 1
        for y in range(top_row, 12):
            for x in range(8):
                c = shade(CAPE[line], 1.0 - 0.03 * y + (0.06 if x in (2, 5) else 0.0))
                if x in (0, 7) or y == 11:
                    c = GOLD if (x + y) % 2 == 0 else shade(GOLD, 0.8)
                e.put("body", "back", x, y, c)
        motif_col = GOLD
    for my, row in enumerate(BACK_MOTIF[line]):
        for mx, ch in enumerate(row):
            if ch == "#":
                e.put("body", "back", mx, my + (5 if long_hair else 3), motif_col)
    # gold on the arms of the stronger gods
    if rank <= 5 and outfit != "armor":
        for arm in ("rarm", "larm"):
            for face in SIDES:
                for x in range(4):
                    e.put(arm, face, x, 4, GOLD if x % 2 == 0 else shade(GOLD, 0.78))
    return img

def phase_skin(base, gid, line, rank, phase):
    """
    The same god in a later phase of its trial. Phase 2, its aegis: lines of its tower's light run down its arms and
    legs, a sigil burns on its chest, its eyes are lit and a ring of light stands over its head. Phase 3, its
    wrath: its cloth darkens, cracks open across it with fire inside, its eyes blaze and flames ring its head.
    """
    img = base.copy()
    e = SkinEdit(img, hsh(f"phase{phase}" + gid))
    glow = PHASE_GLOW[line]
    bright = mix(glow, (255, 255, 255), 0.45)
    body_parts = ("body", "rarm", "larm", "rleg", "lleg")
    if phase == 2:
        for part in body_parts:
            for face in PARTS[part]:
                wd, ht = e.size(part, face)
                for y in range(ht):
                    for x in range(wd):
                        e.tint(part, face, x, y, glow, 0.07)
        # runes: a broken line of light down each limb
        for limb in ("rarm", "larm", "rleg", "lleg"):
            for face in ("front", "back"):
                for y in range(1, 11):
                    if y % 3 != 0:
                        e.put(limb, face, 1 if limb[0] == "r" else 2, y, glow if y % 3 == 1 else bright)
        # the sigil on its chest: a diamond with a bright heart
        for (x, y) in ((3, 1), (4, 1), (2, 2), (5, 2), (2, 3), (5, 3), (3, 4), (4, 4)):
            e.put("body", "front", x, y, glow)
        e.put("body", "front", 3, 2, bright); e.put("body", "front", 4, 2, bright)
        e.put("body", "front", 3, 3, bright); e.put("body", "front", 4, 3, bright)
        for y in range(5, 11):
            e.put("body", "front", 3 if y % 2 else 4, y, glow)
        # eyes lit
        for x in (1, 2, 5, 6):
            e.put("head", "front", x, 4, bright if x in (2, 5) else glow)
        # a ring of light over the head, wherever its headgear leaves room
        for face in SIDES:
            for x in range(8):
                c = e.get("hat", face, x, 0)
                if c is not None and c[3] == 0 and x % 2 == 0:
                    e.put("hat", face, x, 0, bright)
        return img
    core = WRATH_CORE[line]
    ember = mix(glow, core, 0.5) if line != "HADES" else (255, 84, 40)
    for part in body_parts + ("head",):
        for face in PARTS[part]:
            wd, ht = e.size(part, face)
            for y in range(ht):
                for x in range(wd):
                    c = e.get(part, face, x, y)
                    if c is None or c[3] == 0:
                        continue
                    grey = sum(c[:3]) / 3
                    ash = mix(c[:3], (grey, grey, grey), 0.35)
                    e.put(part, face, x, y, shade(ash, 0.72 if part != "head" else 0.86))

    def crack(part, face, x, y0, y1):
        wd, _ = e.size(part, face)
        for y in range(y0, y1):
            x = max(0, min(wd - 1, x + e.rnd.choice((-1, 0, 0, 1))))
            for nx in (x - 1, x + 1):
                e.scale(part, face, nx, y, 0.74)
            e.put(part, face, x, y, core if y % 2 == 0 else ember)

    crack("body", "front", 2, 0, 12); crack("body", "front", 5, 2, 10)
    crack("body", "back", 4, 1, 12)
    for limb in ("rarm", "larm", "rleg", "lleg"):
        crack(limb, "front", 1 + e.rnd.randrange(2), 1, 12)
        crack(limb, "back", 1 + e.rnd.randrange(2), 3, 10)
    for face in ("left", "right"):
        crack("body", face, 2, 2, 11)
    # a crack down one cheek, and eyes that blaze
    for y in range(2, 7):
        e.put("head", "front", 7 if y < 4 else 6, y, ember if y % 2 else core)
    for x in (1, 2, 5, 6):
        e.put("head", "front", x, 4, core)
    e.put("head", "front", 1, 5, ember); e.put("head", "front", 6, 5, ember)
    for x in (1, 2, 5, 6):
        e.scale("head", "front", x, 3, 0.55)                          # brows drawn down, dark
    # flames round the head, wherever its headgear leaves room
    for face in SIDES:
        for x in range(8):
            for y in (0, 1):
                c = e.get("hat", face, x, y)
                if c is not None and c[3] == 0 and (x + y) % 2 == 0 and (y == 0 or e.rnd.random() < 0.5):
                    e.put("hat", face, x, y, core if y == 0 else ember)
    for x in range(8):
        for y in range(8):
            c = e.get("hat", "top", x, y)
            if c is not None and c[3] == 0 and (x in (0, 7) or y in (0, 7)) and (x + y) % 2 == 0:
                e.put("hat", "top", x, y, ember)
    return img

def apply_layers(img, look, layers, seed):
    """Paints a skin's outer layer: armour plates, cloaks, sashes, straps - whatever the list names. Each piece takes
    its colours from the look underneath, so the same piece is a different thing on every wearer."""
    outfit, cloth, trim, hair, skin, gear, gear_col, beard, long_hair = look
    e = SkinEdit(img, hsh("layers" + seed))
    metal = gear_col if outfit == "armor" else mix(trim, (150, 150, 160), 0.35)
    hi, lo = mix(metal, (255, 255, 255), 0.4), shade(metal, 0.62)
    leather, leather2 = (104, 68, 42), (72, 46, 30)
    for piece in layers:
        if piece == "pauldrons":
            for sleeve in ("rsleeve", "lsleeve"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(4):
                            e.put(sleeve, face, x, y, hi if y == 0 else lo if y == 3 else metal)
                for x in range(4):
                    for y in range(4):
                        e.put(sleeve, "top", x, y, hi if (x + y) % 3 == 0 else metal)
        elif piece == "cuirass":
            for face in ("front", "back"):
                for x in range(8):
                    for y in range(7):
                        c = hi if y == 0 else lo if x in (3, 4) and face == "front" else metal
                        if face == "front" and y in (2, 4) and x in (1, 2, 5, 6):
                            c = hi if y == 2 else lo
                        e.put("jacket", face, x, y, c)
            for face in ("left", "right"):
                for x in range(4):
                    for y in range(1, 6):
                        e.put("jacket", face, x, y, shade(metal, 0.85))
            e.put("jacket", "front", 3, 3, trim); e.put("jacket", "front", 4, 3, trim)
        elif piece == "greaves":
            for leg in ("rpants", "lpants"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(6, 12):
                            e.put(leg, face, x, y, hi if y == 6 else lo if y == 11 else metal)
                e.put(leg, "front", 1, 8, hi); e.put(leg, "front", 2, 8, hi)
        elif piece == "bracers":
            for sleeve in ("rsleeve", "lsleeve"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(7, 10):
                            e.put(sleeve, face, x, y, leather if y != 8 else mix(trim, leather, 0.4))
        elif piece == "cape":
            col = mix(trim, cloth, 0.35) if sum(trim) < 560 else shade(mix(cloth, trim, 0.5), 0.7)
            for x in range(8):
                for y in range(12):
                    c = shade(col, 1.0 - 0.03 * y + (0.08 if x in (2, 5) else 0.0))
                    if x in (0, 7) or y == 11:
                        c = trim
                    e.put("jacket", "back", x, y, c)
            for face in ("left", "right"):
                for y in range(9):
                    e.put("jacket", face, 3 if face == "left" else 0, y, shade(col, 0.85))
            for x in range(8):
                e.put("jacket", "top", x, 3, col)
        elif piece == "tabard":
            for face in ("front", "back"):
                for x in range(2, 6):
                    for y in range(12):
                        e.put("jacket", face, x, y, trim if x in (2, 5) or y == 11 else shade(cloth, 0.8) if sum(cloth) > 200 else mix(cloth, trim, 0.3))
            for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (3, 2), (4, 5)):
                e.put("jacket", "front", x, y, mix(trim, (255, 255, 255), 0.4))
        elif piece == "sash":
            for face in ("front", "back"):
                for x in range(8):
                    y = x if face == "front" else 7 - x
                    e.put("jacket", face, x, y, trim); e.put("jacket", face, x, y + 1, shade(trim, 0.78))
        elif piece == "bandolier":
            for face in ("front", "back"):
                for x in range(8):
                    y = 7 - x if face == "front" else x
                    e.put("jacket", face, x, y, leather); e.put("jacket", face, x, y + 1, leather2)
            e.put("jacket", "front", 2, 5, hi); e.put("jacket", "front", 5, 2, hi)
        elif piece == "belt":
            for face in SIDES:
                wd, _ = e.size("jacket", face)
                for x in range(wd):
                    e.put("jacket", face, x, 8, leather); e.put("jacket", face, x, 9, leather2)
            e.put("jacket", "front", 3, 8, hi); e.put("jacket", "front", 4, 8, hi); e.put("jacket", "front", 3, 9, lo); e.put("jacket", "front", 4, 9, lo)
        elif piece == "skirt":
            col = shade(cloth, 0.86)
            for leg in ("rpants", "lpants"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(6):
                            e.put(leg, face, x, y, trim if y == 5 else shade(col, 1.0 - 0.04 * y + (0.07 if x % 2 else 0.0)))
        elif piece == "scarf":
            for face in SIDES:
                wd, _ = e.size("jacket", face)
                for x in range(wd):
                    e.put("jacket", face, x, 0, trim); e.put("jacket", face, x, 1, shade(trim, 0.8))
            for y in range(2, 6):
                e.put("jacket", "front", 5, y, trim if y % 2 else shade(trim, 0.8))
        elif piece == "furs":
            fur, fur2 = mix(cloth, (120, 90, 60), 0.5), shade(mix(cloth, (120, 90, 60), 0.5), 0.72)
            for face in SIDES:
                wd, _ = e.size("jacket", face)
                for x in range(wd):
                    for y in range(4):
                        if y < 3 or (x * 3 + y) % 2 == 0:
                            e.put("jacket", face, x, y, fur2 if (x * 5 + y * 3) % 4 == 0 else fur)
            for sleeve in ("rsleeve", "lsleeve"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(2):
                            e.put(sleeve, face, x, y, fur2 if (x + y) % 2 else fur)
        elif piece == "chains":
            link, link2 = mix(trim, (190, 190, 200), 0.5), shade(mix(trim, (190, 190, 200), 0.5), 0.6)
            for face in ("front", "back"):
                for x in range(8):
                    e.put("jacket", face, x, 5 if x % 2 else 6, link if x % 2 else link2)
            for sleeve in ("rsleeve", "lsleeve"):
                for face in SIDES:
                    for x in range(4):
                        if x % 2 == 0:
                            e.put(sleeve, face, x, 10, link)
        elif piece == "kelp":
            leaf, leaf2 = (60, 140, 80), (40, 100, 60)
            for face in SIDES:
                wd, _ = e.size("jacket", face)
                for x in range(0, wd, 2):
                    for y in range(3 + e.rnd.randrange(3), 12):
                        e.put("jacket", face, x, y, leaf if y % 2 else leaf2)
            for leg in ("rpants", "lpants"):
                for y in range(4):
                    e.put(leg, "front", 1, y, leaf2); e.put(leg, "back", 2, y, leaf)
        elif piece == "bones":
            bone, bone2 = (226, 220, 200), (170, 164, 146)
            for y in (1, 3, 5):
                for x in range(1, 7):
                    e.put("jacket", "front", x, y, bone2 if x in (3, 4) else bone)
            for y in range(0, 7):
                e.put("jacket", "front", 3, y, bone); e.put("jacket", "back", 4, y, bone); e.put("jacket", "back", 3, y, bone2)
        elif piece == "wraps":
            band = mix(cloth, (230, 224, 200), 0.6)
            for part in ("rsleeve", "lsleeve", "rpants", "lpants"):
                for face in SIDES:
                    for x in range(4):
                        for y in range(5, 12, 2):
                            e.put(part, face, x, y, band if (x + y) % 3 else shade(band, 0.8))
        elif piece == "mask":
            col = shade(gear_col if gear == "hood" else cloth, 0.7)
            for x in range(8):
                for y in range(5, 8):
                    e.put("hat", "front", x, y, col if y != 5 else shade(col, 1.2))
            for face in ("left", "right"):
                for x in range(8):
                    for y in range(5, 8):
                        e.put("hat", face, x, y, shade(col, 0.9))
        elif piece == "plume":
            col = gear_col
            for i in range(8):
                e.put("hat", "top", 3, i, col); e.put("hat", "top", 4, i, mix(col, (255, 255, 255), 0.3))
            for y in range(0, 6):
                e.put("hat", "back", 3, y, col); e.put("hat", "back", 4, y, shade(col, 0.75))
            e.put("hat", "front", 3, 0, col); e.put("hat", "front", 4, 0, mix(col, (255, 255, 255), 0.3))
    return img

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
    blit("jacket", "front", 4, 8); blit("rsleeve", "front", 0, 8); blit("lsleeve", "front", 12, 8)
    blit("rpants", "front", 4, 20); blit("lpants", "front", 8, 20)
    return out.resize((16 * scale, 32 * scale), Image.NEAREST)

def gen_sentinel_skins():
    for sid, _name, line, look in SENTINELS:
        img = apply_layers(make_skin(sid, line, 8), look, SENTINEL_LAYERS.get(sid, []), sid)
        path = os.path.join(ASSETS, f"textures/entity/sentinel/{sid}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)

TITANS = {"hyperion": "ZEUS", "oceanus": "POSEIDON", "cronus": "HADES"}


def chain_titan(img, gid):
    """A Titan is a god cast down: a chain across its chest from shoulder to hip, and iron at both wrists."""
    px = img.load()
    iron, dark = (120, 122, 132, 255), (62, 62, 72, 255)
    for i in range(12):
        x, y = 20 + (i * 8) // 12, 20 + i                      # (the body's front face is 8 wide from x 20, 12 high from y 20)
        px[x, y] = iron if i % 2 == 0 else dark
        if x + 1 < 28:
            px[x + 1, y] = dark if i % 2 == 0 else iron
        for bx, by in ((x + 16, y + 16),):                    # (and over the jacket layer, so the cloak does not hide it)
            if px[bx, by][3] > 0:
                px[bx, by] = iron if i % 2 == 0 else dark
    for x0, y0 in ((44, 29), (36, 61), (40, 29), (32, 61), (48, 29), (52, 29), (40, 61), (44, 61)):
        for dx in range(4):
            px[x0 + dx, y0] = iron
            px[x0 + dx, y0 + 1] = dark
    return img


def gen_titan_skins():
    for gid, line in TITANS.items():
        img = polish_skin(make_skin(gid, line, 1), gid, line, 1)
        img = apply_layers(img, LOOKS[gid], list(OUTFIT_LAYERS[LOOKS[gid][0]]) + ["cape", "bracers"], gid)
        img = chain_titan(img, gid)
        folder = os.path.join(ASSETS, "textures/entity/titan")
        os.makedirs(folder, exist_ok=True)
        img.save(os.path.join(folder, f"{gid}.png"))
        for phase in (2, 3):
            phase_skin(img, gid, line, 1, phase).save(os.path.join(folder, f"{gid}_p{phase}.png"))


def gen_skins():
    gen_titan_skins()
    previews = {}
    for _, line, rank, g in all_gods():
        img = polish_skin(make_skin(g[0], line, rank), g[0], line, rank)
        # the outer layer: what the god's outfit calls for, and a cloak for the three greatest of each tower
        god_layers = list(OUTFIT_LAYERS[LOOKS[g[0]][0]]) + (["cape"] if rank <= 3 else []) + (["bracers"] if rank <= 8 else [])
        img = apply_layers(img, LOOKS[g[0]], god_layers, g[0])
        path = os.path.join(ASSETS, f"textures/entity/god/{g[0]}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        # the same god with its aegis up, and in its wrath
        for phase in (2, 3):
            phase_skin(img, g[0], line, rank, phase).save(os.path.join(ASSETS, f"textures/entity/god/{g[0]}_p{phase}.png"))
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
    "hermitage":  ("none",      "#0b1026", "#141a3a", "#b9c6ff", 0.60, 0.25),
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
# Website data: the god roster and portraits (16x32) in roster order. The main page shows gods and towers only;
# the weapon atlas is for the unlisted promotional page.
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
    promo = os.path.join(SITE, "promotional")
    if os.path.isdir(promo):
        atlas.save(os.path.join(promo, "weapons.png"))
    people.save(os.path.join(SITE, "gods.png"))
    roster = []
    for idx, line, rank, g in all_gods():
        floor = 16 - rank
        tier = 0 if floor <= 5 else 1 if floor <= 10 else 2 if floor <= 14 else 3
        roster.append({"i": idx, "line": line.lower(), "rank": rank, "floor": floor, "tier": TIERS[line][1][tier],
                       "id": g[0], "name": g[1], "title": g[2], "move": SIGNATURES[g[0]][0]})
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
    gen_field_textures()
    gen_ui()
    panoply_art.gen_panoply(ASSETS, MOD, wj)
    hud_art.gen_hud(ASSETS, MOD, os.path.join(ROOT, "common/src/main/java", *JAVA_PKG.split(".")), wj, w)
    gen_tarot()
    gen_dolls()
    gen_essences_and_orbs()
    gen_sentinel_skins()
    gen_keystones_and_seekers()
    gen_materials()
    gen_concepts()
    gen_forge_textures()
    # every item's art, redrawn at 64x64 over the older 16x16 art above
    import item_art
    item_art.run(ASSETS)
    import block_art
    block_art.run(ASSETS, DATA)
    divine_art.gen(ASSETS, DATA, wj)
    divine_pro.gen(ASSETS, DATA, wj)
    divine_models.gen(ASSETS, DATA, wj)
    armor_art.gen(ASSETS, wj)
    particle_art.gen(ASSETS, wj)
    music.gen(ASSETS, wj)
    discs.gen(ASSETS, DATA, wj)
    station_ui.gen(ASSETS, wj)
    furniture_art.gen(ASSETS, DATA, wj)
    gen_recipes()
    gen_field_data()
    gen_tags()
    gen_story_java()
    gen_handbook_java()
    previews = gen_skins()
    gen_pack()
    gen_data()
    gen_advancements()
    orb_ranks.gen(ASSETS, DATA, wj)
    gen_icon(previews)
    gen_site(first, previews)
    gen_promo(first, {k: v for k, v in previews.items()})
    gen_atlases()
    print("generated", sum(len(v) for v in LINES.values()), "gods")
