# Realms of Chaos

**By TheFarawayDev** - a Minecraft mod (Fabric **and** Forge) for **Minecraft 26.3**.

> A mashup of mythologies forged into weapons, with several starting points, bosses and paths to travel.

This release implements the **Greek mythology** path (the part of the design that is fully specified).
The other four starting points (Norse, Egyptian, Celtic, Japanese) and the optional side-realms (Hellish, Heavenly,
Phobias, Community weapons, Voodoo & Tarot) are not in yet.

## What's in 1.1.0

* **The Hub of Realms** - a plaza with three gates: **Zeus** (north), **Poseidon** (east), **Hades** (west).
* **Three arenas**, one behind each gate: *Olympian Heights* (marble temple), *The Abyssal Palace* (prismarine ringed
  by water), *The Underworld* (basalt spikes and a river of lava).
* **45 gods** - each is a boss with its own look *and* an animated relic weapon, in three ranked lines
  (rank 1 = strongest, rank 15 = weakest and the starter):

| Rank | Zeus line | Poseidon line | Hades line |
|---|---|---|---|
| 1 | Zeus | Poseidon | Hades |
| 2 | Athena | Amphitrite | Persephone |
| 3 | Apollo | Triton | Zagreus |
| 4 | Dionysus | Chrysaor | Thanatos |
| 5 | Artemis | Cymopoleia | Hypnos |
| 6 | Ares | Rhode | Melinoe |
| 7 | Hermes | Pegasus | Macaria |
| 8 | Asclepius | Benthesikyme | Hecate |
| 9 | Hebe | Halia | Morpheus |
| 10 | Phobos | Nereus | Nyx |
| 11 | Deimos | Proteus | Erinyes |
| 12 | Hygeia | Palaemon | Moirai |
| 13 | Pan | Thetis | Keres |
| 14 | Nike | Galene | Oneiroi |
| 15 (starter) | **Iris** | **Galatea** | **Charon** |

* **Codex of Chaos** - an in-game menu (item or `/roc menu`): every line and god, what you have defeated, what is
  next, and one-click travel to the hub and the arenas.
* **Advancements tab** - "Realms of Chaos", one advancement per god, a challenge for each final boss.
* **First-join welcome** - the first time a player enters a world they are thanked, and given the Codex and the
  Compass of Chaos.

## How to play

1. Join a world. Open the **Codex of Chaos** (or run `/roc menu`), or use the **Compass of Chaos** / `/roc hub`.
2. Walk into one of the three gates to enter an arena.
3. Use the arena's **Trial Altar**. Each line is climbed from the **starter** (Iris / Galatea / Charon, rank 15) up to
   the **final god** (Zeus / Poseidon / Hades, rank 1). The next trial unlocks once you have defeated the previous
   god of that line. The three lines are independent: pick any path. (Trials need a difficulty above Peaceful.)
4. Every god you defeat drops its **relic weapon**: sword-class, scaled by rank, with a signature hit power and a
   right-click **Divine Surge** (12 s cooldown).

Each line fights differently: **Zeus** calls lightning, **Poseidon** drags you in and crashes tidal waves over you,
**Hades** drains your life and (for the top gods) raises wither skeletons. Bosses raise their arms and gather
particles for about a second before a big attack, and enrage at half health.

**No getting stuck:** the fight ring of every arena is flat and empty, an invisible wall stops anyone being thrown
out, bosses that end up out of bounds, inside a block or unable to reach you are put back, and a player who falls
into the void or is stuck in a block is returned to the arrival pad.

## Install

| Loader | Needs |
|---|---|
| Fabric | Minecraft 26.3, Java 25, Fabric Loader >= 0.20.1, Fabric API 0.166.2+26.3 (or newer for 26.3) |
| Forge | Minecraft 26.3, Java 25, Forge 66.1.9 (or newer for 26.3) |

Drop the matching `realms-of-chaos-<loader>-1.1.0+mc26.3.jar` into your `mods` folder.

Updating from 1.0.0: the hub and arenas are rebuilt automatically the next time someone enters them. Progress is kept.

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
on a real server: data loading, building the hub and arenas, the altar, a boss fight, every weapon power, the Codex
menu, the welcome and the anti-stuck safeguards.

```bash
cd forge && ./gradlew runGameTestServer && cat run/roc-selftest.txt
```

License: MIT. Greek mythology belongs to everyone.
