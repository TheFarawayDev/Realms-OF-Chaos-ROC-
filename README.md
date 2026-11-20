# Realms of Chaos

**By TheFarawayDev** - a Minecraft mod (Fabric **and** Forge) for **Minecraft 26.3**.

> A mashup of mythologies forged into weapons, with several starting points, bosses and paths to travel.

This release implements the **Greek mythology** path (the part of the design that is fully specified).
The other four starting points (Norse, Egyptian, Celtic, Japanese) and the optional side-realms (Hellish, Heavenly,
Phobias, Community weapons, Voodoo & Tarot) are not in yet.

## Release channel

The `VERSION` file at the repo root sets the version and channel for both loaders, the in-game Codex, the GitHub
release and the download page: `1.2.0-alpha` and `1.2.0-beta` are published as pre-releases, `1.2.0` as a full release.
The current build is an **Alpha**.

## What's in 1.2.0

* **The Hub of Realms** - a plaza with four gates: **Zeus** (north), **Poseidon** (east), **Hades** (west), and the
  **exit** back to where you came from (south).
* **Three towers of 15 floors**, one per god line. Every floor is its own enclosed arena holding one god; floor 1 has
  the starter (rank 15) and floor 15, the open summit, has the final god (rank 1). Beat a floor's god and its
  **Gate of Ascent** opens.
* **Every floor is different.** Ten terrain layouts (flat, mounds, pillared hall, pit, dais, waves, moat, terraces,
  split level, ziggurat, summit) are combined with four material tiers per tower:

| Floors | Tower of Zeus | Tower of Poseidon | Tower of Hades |
|---|---|---|---|
| 1-5 | Foothills of Olympus | The Sunlit Shallows | Fields of Asphodel |
| 6-10 | The Marble Halls | The Prismarine Halls | Tartarus |
| 11-14 | The Cloud Sanctum | The Trench | Halls of the Dead |
| 15 | Summit of Olympus | Throne of the Deep | Throne of the Underworld |

* **Living floors.** Floors change during a trial: when the god drops below two thirds health the terrain shifts,
  one block at a time, into a second shape (a pit rises into a dais, a hall folds into terraces), and shifts back
  below one third; the upper floors also keep shifting on a timer. Anything standing on rising ground is carried up,
  no step ever exceeds one block, and the floor settles back into its own design when the fight ends.
* **Furnished rooms.** Every floor has engaged columns between its windows, a ribbed ceiling with hanging lights, a
  processional path from the gates, and props that match its tier (azaleas and lanterns in the foothills, braziers
  of soul fire in Tartarus, sculk and soul lanterns in the Trench...).
* **Nine kinds of weapon** instead of 45 swords: blades, daggers (fast), polearms (long reach), heavy weapons (slow,
  crushing), scythes (every hit sweeps), and four ranged kinds - staffs (cast bolts), bows (loose arrows), thrown
  weapons (thunderbolts, javelins, shards) and relics such as a chalice, a conch or pan pipes (pulse their power
  around you). Right-click attacks with ranged kinds; sneak + right-click is their Divine Surge.
* **Ranged gods.** A god fights the way its weapon does: gods with a ranged kind keep their distance and shoot.
  Hades also throws tarot cards.
* **Weapon levelling and forging.** A relic gains experience from every kill made with it (level 1-10, +8% damage a
  level). Gods drop essences of their tower; at a **Forge of Chaos** (two stand in the hub; also `/roc forge` and the
  Codex) those forge up to five stars onto a relic (+10% damage and faster recharge each; star N costs 2N essences
  and needs weapon level 2N). One tower yields enough essences to fully forge one weapon.
* **Tarot.** The gods of Hades' tower drop the 22 Major Arcana (three copies of each of their cards). A card is
  played with right-click and spent. Hades drops Death and the **Deck of Fate**: a stack that never runs out -
  right-click draws a random card, sneak + right-click lets you choose one (the deck then rests longer).
* **Voodoo dolls** (crafted: string over wheat-pin-wheat over wheat; the pin is an iron nugget, cobweb, rotten flesh
  or blaze powder). Right-click a creature to bind the doll, then right-click to stab: the creature is hurt from up
  to 64 blocks away. Pain wounds, Binding slows and weakens, Rot withers, Embers burns. Gods take half and ignore
  the curses.
* **45 gods** - each is a boss with its own look *and* an animated relic weapon:

| Floor | Rank | Zeus line | Poseidon line | Hades line |
|---|---|---|---|---|
| 15 | 1 | Zeus | Poseidon | Hades |
| 14 | 2 | Athena | Amphitrite | Persephone |
| 13 | 3 | Apollo | Triton | Zagreus |
| 12 | 4 | Dionysus | Chrysaor | Thanatos |
| 11 | 5 | Artemis | Cymopoleia | Hypnos |
| 10 | 6 | Ares | Rhode | Melinoe |
| 9 | 7 | Hermes | Pegasus | Macaria |
| 8 | 8 | Asclepius | Benthesikyme | Hecate |
| 7 | 9 | Hebe | Halia | Morpheus |
| 6 | 10 | Phobos | Nereus | Nyx |
| 5 | 11 | Deimos | Proteus | Erinyes |
| 4 | 12 | Hygeia | Palaemon | Moirai |
| 3 | 13 | Pan | Thetis | Keres |
| 2 | 14 | Nike | Galene | Oneiroi |
| 1 (start) | 15 | **Iris** | **Galatea** | **Charon** |

* **In-game UI instead of chat** - nothing the mod tells you goes to chat:
  * **Codex of Chaos** (item or `/roc`), a double-chest screen: the three towers side by side with climb meters,
    and a page per tower that draws it floor by floor with tier labels. Click any floor you have reached to travel
    there; travel to the hub or leave the realms from the main page.
  * **Trial Altar menu**: right-click a floor's altar to see its god and begin the trial, or ascend once it is won;
    a strip along the bottom shows the floors around you.
  * **Tower bar**: a bar at the top of the screen shows the tower, floor and tier you are on.
  * **Titles and action bar** for arrivals, trials, victories and short notices.
  * **Advancements tab** with one advancement per god.
* **First-join welcome** - a thank-you title, plus the Codex and the Compass of Chaos.

## How to play

1. Join a world. Open the **Codex of Chaos** (or run `/roc`), or use the **Compass of Chaos** / `/roc hub`.
2. In the hub, walk into a tower gate. You arrive on the floor you are working on.
3. Right-click the floor's **Trial Altar** and press **Begin the trial**. (Trials need a difficulty above Peaceful.)
4. Defeat the god: it drops its **relic weapon** (sword-class, scaled by rank, with a signature hit power and a
   right-click **Divine Surge**), and the **Gate of Ascent** at the far side of the floor opens for you.
5. Climb. The three towers are independent, and progress is saved per player.
6. To go home, take the south gate in the hub, the Codex's *Leave the Realms* button, or `/roc leave`.

Each line fights differently: **Zeus** calls lightning, **Poseidon** drags you in and crashes tidal waves over you,
**Hades** drains your life and (for the top gods) raises wither skeletons. Bosses raise their arms and gather
particles for about a second before a big attack, and enrage at half health.

**No getting stuck:** no step on any floor is taller than one block and nothing stands inside the fight ring, floors
are fully enclosed, bosses that end up off their floor, inside a block or unable to reach you are put back, and a
player who is stuck in a block or falls out is returned to the floor's arrival pad.

## Install

| Loader | Needs |
|---|---|
| Fabric | Minecraft 26.3, Java 25, Fabric Loader >= 0.20.1, Fabric API 0.166.2+26.3 (or newer for 26.3) |
| Forge | Minecraft 26.3, Java 25, Forge 66.1.9 (or newer for 26.3) |

Drop the matching `realms-of-chaos-<loader>-<version>+mc26.3.jar` into your `mods` folder.

Updating from 1.0 / 1.1: the hub is rebuilt and the old arenas are replaced by the towers automatically the next
time someone enters them. Progress is kept: you arrive on the floor matching the gods you have already defeated.

## Building from source

```bash
JAVA_HOME=/path/to/jdk-25 ./build.sh
```

* `common/` - loader-independent code and assets, shared by both builds
* `fabric/`, `forge/` - thin loader entrypoints and Gradle builds
* `tools/generate.py` - the god roster (single source of truth) plus the generator for textures, skins, models,
  lang, advancements and the website's roster
* `site/` - the download page (GitHub Pages)

### Self-test

The mod contains a developer self-test that only runs when the JVM has `-Droc.selftest=true`. It exercises the mod
on a real server: data loading, the hub, building and walking every floor of every tower, a full climb of all three
towers through the altar menu, every weapon power, the Codex, leaving the realms, the welcome and the anti-stuck
safeguards.

```bash
cd forge && ./gradlew runGameTestServer && cat run/roc-selftest.txt
```

License: MIT. Greek mythology belongs to everyone.
