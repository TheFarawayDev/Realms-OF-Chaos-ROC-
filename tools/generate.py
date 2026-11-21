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
        "The realms of the gods are closed until you find a way in. Right-click the Compass of Chaos: it points to the nearest Ruined Gate.",
        "This handbook is always here: open the Codex and choose Handbook.",
    ]),
    ("way_in", "Finding the way in", [
        "A Ruined Gate is a broken stone plaza with a dead arch and a campfire still smoking. You will see the smoke first.",
        "Its chest holds three seekers. Each is a compass for one keystone: right-click it and it tells you how far and which way the shrine is.",
        "The Shrine of the Sky is a white temple. The Shrine of the Tides is a ring of prismarine round a pool. The Shrine of the Shades is a crypt under standing stones.",
        "Bring the three keystones back and right-click the pedestal with each. The arch fills. Step through, and the gate knows you: from then on the compass carries you to the hub from anywhere.",
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
        "Every god has a signature move with a name. When a god raises its arms, the name appears above your hotbar: that is your warning.",
        "Rings on the ground mean it is about to erupt there. Step out. A hex bursts two seconds after it lands unless you get well away.",
        "At half health a god is enraged: it glows, moves faster, and the floor starts to throw itself up under your feet. Keep moving when you see dust.",
        "At a fifth of its health it makes a last stand: for two seconds nothing can hurt it, sentinels answer its call, and then it uses everything it has.",
        "The floor itself changes shape as the fight goes on. If a god beats you, it comes back a little weaker next time.",
    ]),
    ("passages", "Passages", [
        "Between every two floors lies a passage: two or three levels, each a maze of rooms. No two worlds have the same ones.",
        "The gate at the end of a level is sealed. Find every seal on that level and break it. The bar at the top counts them.",
        "A sealed chamber wakes its sentinels when you walk in. When they have fallen, right-click the seal and strike it: click when the hammer is on the yellow mark.",
        "The Codex opens a map of the level you are on. The Compass of Chaos points to the nearest seal, then to the gate.",
        "Treasure caches give every player their own share. Shrines heal you. Arenas hold an elite, and a better cache. Each level up is harder than the last.",
    ]),
    ("sentinels", "Sentinels", [
        "Soldiers close in. Casters keep their distance and shoot. Skirmishers are quick and leap at you. Brutes are slow and send you flying.",
        "Mystics hang back and help the rest: the Storm Augur quickens them, the Tide Priest heals them, the Necromancer raises more. Deal with the mystic first.",
        "A Keeper guards every seal and now and then makes a small version of one of its tower's moves. Elites glow, take half as much again to bring down, and always drop forge material.",
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
        "/roc - this menu.  /roc hub - go to the hub.  /roc leave - go home.  /roc forge - the forge screen.",
        "/roc party invite <player>, /roc party accept, /roc party leave.",
    ]),
]

# One-time tips: shown on screen the first time each thing happens to a player.
TIPS = [
    ("ruin", "A Ruined Gate. Look in its chest: the seekers lead to the keystones it needs"),
    ("hub", "The Oracle's Flame is just north. Right-click it. Each gate leads to a tower"),
    ("floor", "Right-click the Trial Altar to see who waits here and to begin"),
    ("trial", "When the god raises its arms, its move is named above your hotbar"),
    ("victory", "Pick up your relic. The Gate of Ascent at the far side is open"),
    ("passage", "Break every seal on this level. The Codex is a map; the compass points the way"),
    ("seal", "Clear the sentinels, then right-click the seal and strike on the yellow mark"),
    ("forge", "Stars cost materials from the relic's own tower. Strike on the yellow mark"),
    ("upheaval", "Dust under your feet means the floor is about to erupt. Move"),
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
for _sid, _name, _line, _look in SENTINELS:
    LOOKS[_sid] = _look

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

    wj(os.path.join(out, "index.json"), index)
    lines = ["# Texture atlases", "", "Generated by `tools/generate.py` from the textures the mod ships. Each sheet comes at its own pixel size and",
             "again four times larger (`@4x`). `index.json` maps every texture name to its column and row.", "",
             "| Atlas | Tiles | Tile size | What |", "| --- | --- | --- | --- |"]
    what = {"items": "every item texture (first frame of animated ones)", "weapon-designs": "weapon designs drawn ahead for future mythologies",
            "blocks": "every block texture (first frame)", "skins": "the raw 64x64 skins of the gods and the passage sentinels",
            "portraits": "the gods and sentinels as they look from the front"}
    for name in ("items", "weapon-designs", "blocks", "skins", "portraits"):
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
        "item.realms_of_chaos.chaos_compass": "Compass of Chaos",
        "item.realms_of_chaos.chaos_compass.tooltip": "Right-click: points to the nearest Ruined Gate. Once a gate knows you, it carries you to the Hub of Realms. In a passage it points to the nearest seal (sneak to travel)",
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
        "hud.realms_of_chaos.laststand.sub": "Nothing can touch it for a moment. Brace",
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
        "screen.realms_of_chaos.altar.scaling": "The trial is harder for every extra challenger on the floor",
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
        "hud.realms_of_chaos.access.attuned.sub": "The gate knows you now. The compass will carry you to the hub",
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
        "hud.realms_of_chaos.passage.title": "Passage to floor %s",
        "hud.realms_of_chaos.passage.sub": "Break the %s seals to open the gate",
        "hud.realms_of_chaos.passage.sub.open": "The gate at the far end stands open",
        "hud.realms_of_chaos.passage.bar": "%s - Passage to floor %s - Level %s of %s - Seals %s / %s",
        "hud.realms_of_chaos.passage.seal.broken": "Seal broken: %s left",
        "hud.realms_of_chaos.passage.open": "The way is open",
        "hud.realms_of_chaos.passage.open.sub": "The gate to floor %s awaits",
        "hud.realms_of_chaos.passage.sealed": "%s seals still hold this gate shut",
        "hud.realms_of_chaos.passage.guarded": "Its guardians still stand: %s left",
        "hud.realms_of_chaos.passage.room.seal": "A sealed chamber - its guardians wake",
        "hud.realms_of_chaos.passage.room.seal.done": "This chamber's seal is broken",
        "hud.realms_of_chaos.passage.room.treasure": "A treasure room",
        "hud.realms_of_chaos.passage.room.shrine": "A shrine - right-click it for a blessing",
        "hud.realms_of_chaos.passage.room.trap": "Careful: scorching tiles and webs",
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
    wj(os.path.join(ASSETS, "lang/en_us.json"), lang)

# ----------------------------------------------------------------------------
# Item definitions / models
# ----------------------------------------------------------------------------
# The three keystones a Ruined Gate needs: (keystone name, the seeker compass that finds it, the shrine it lies in)
KEYSTONES = {
    "ZEUS": ("Keystone of the Sky", "Seeker of the Sky", "Shrine of the Sky"),
    "POSEIDON": ("Keystone of the Tides", "Seeker of the Tides", "Shrine of the Tides"),
    "HADES": ("Keystone of the Shades", "Seeker of the Shades", "Shrine of the Shades"),
}

BLOCKS = ["gate_hub", "gate_zeus", "gate_poseidon", "gate_hades", "gate_ascend", "gate_leave",
          "altar_zeus", "altar_poseidon", "altar_hades", "chaos_forge", "passage_seal", "passage_shrine", "gate_pedestal", "oracle", "passage_cache"]

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
    extra = ["chaos_compass", "chaos_codex", "tarot_deck"] + [f"tarot_{c[0]}" for c in TAROT] + [f"voodoo_doll_{d[0]}" for d in DOLLS] \
        + [f"essence_{l.lower()}" for l in LINES] + [f"orb_{o}" for o in ORBS] \
        + [f"material_{l.lower()}_{t}" for l in LINES for t in (1, 2, 3)] \
        + [f"keystone_{l.lower()}" for l in LINES] + [f"seeker_{l.lower()}" for l in LINES]
    for flat in extra:
        item_def(flat, f"item/{flat}")
        wj(os.path.join(ASSETS, f"models/item/{flat}.json"),
           {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{flat}"}})
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
        col = POWER_COLOR[orb.upper()]
        frames = []
        for f in range(4):
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            px = img.load()
            rad = 4.2 + 0.9 * math.sin(f / 4 * math.tau)
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - 7.5, y - 7.5)
                    if d < rad - 2.2:
                        px[x, y] = (255, 255, 255, 255)
                    elif d < rad:
                        px[x, y] = mix(col, (255, 255, 255), 0.3) + (255,)
                    elif d < rad + 1.3:
                        px[x, y] = shade(col, 0.75) + (255,)
            for i in range(4):                              # sparks circling the core
                a = f / 4 * math.tau + i * math.tau / 4
                sx, sy = int(round(7.5 + math.cos(a) * 6.5)), int(round(7.5 + math.sin(a) * 6.5))
                if 0 <= sx < 16 and 0 <= sy < 16:
                    px[sx, sy] = col + (255,)
            frames.append(img)
        save_strip(frames, os.path.join(ASSETS, f"textures/item/orb_{orb}.png"), {"animation": {"frametime": 2}})

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

def gen_sentinel_skins():
    for sid, _name, line, _look in SENTINELS:
        img = make_skin(sid, line, 8)
        path = os.path.join(ASSETS, f"textures/entity/sentinel/{sid}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)

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
    gen_tarot()
    gen_dolls()
    gen_essences_and_orbs()
    gen_sentinel_skins()
    gen_keystones_and_seekers()
    gen_materials()
    gen_concepts()
    gen_forge_textures()
    gen_recipes()
    gen_tags()
    gen_story_java()
    gen_handbook_java()
    previews = gen_skins()
    gen_pack()
    gen_data()
    gen_advancements()
    gen_icon(previews)
    gen_site(first, previews)
    gen_promo(first, {k: v for k, v in previews.items()})
    gen_atlases()
    print("generated", sum(len(v) for v in LINES.values()), "gods")
