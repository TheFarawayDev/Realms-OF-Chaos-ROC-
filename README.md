# Realms of Chaos

**By TheFarawayDev** - a Minecraft mod (Fabric **and** Forge) for **Minecraft 26.3**.

> A mashup of mythologies forged into weapons, with several starting points, bosses and paths to travel.

This first release implements the **Greek mythology** path only (the part of the design that is fully specified).
The other four starting points (Norse, Egyptian, Celtic, Japanese) and the optional side-realms (Hellish, Heavenly,
Phobias, Community weapons, Voodoo & Tarot) are not in this release.

## What's in 1.0.0

* **The Hub of Realms** - a void dimension with three gates: **Zeus** (north), **Poseidon** (east), **Hades** (west).
* **Three zones**, one under each gate: *Olympian Heights*, *The Abyssal Palace*, *The Underworld*.
* **45 gods** - each is a boss *and* a relic weapon, in three ranked lines (rank 1 = strongest, rank 15 = weakest):

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

## How to play

1. Get the **Compass of Chaos** (creative tab *Realms of Chaos*) and right-click it, or run `/roc hub`.
2. Walk into one of the three gates (or right-click it) to enter a zone.
3. Use the zone's **Trial Altar**. Each line is climbed from the **starter** (Iris / Galatea / Charon, rank 15) up to
   the **final god** (Zeus / Poseidon / Hades, rank 1). The next trial only unlocks once you have defeated the previous
   god of that line. The three lines are independent: pick any path.
4. Every god you defeat drops its **relic weapon**. Each weapon is a sword-class weapon scaled by rank, with a
   signature hit power and a right-click **Divine Surge** (12 s cooldown).
5. `/roc progress` shows how far up each line you have climbed. Progress is saved per player in the world.

Each line fights differently: **Zeus** calls lightning, **Poseidon** drags you in and crashes tidal waves over you,
**Hades** drains your life and (for the top gods) raises wither skeletons. Bosses enrage at half health.

## Install

| Loader | Needs |
|---|---|
| Fabric | Minecraft 26.3, Java 25, Fabric Loader >= 0.20.1, Fabric API 0.166.2+26.3 (or newer for 26.3) |
| Forge | Minecraft 26.3, Java 25, Forge 66.1.9 (or newer for 26.3) |

Drop the matching `realms-of-chaos-<loader>-1.0.0+mc26.3.jar` into your `mods` folder.

## Building from source

```bash
JAVA_HOME=/path/to/jdk-25 ./build.sh
```

* `common/` - loader-independent code and assets, shared by both builds
* `fabric/`, `forge/` - thin loader entrypoints and Gradle builds
* `tools/generate.py` - the god roster (single source of truth) plus the generator for textures, models and lang
* `tools/validate/Validate.java` - offline sanity checks of the content (registries, dimension JSON, assets)

License: MIT. Greek mythology belongs to everyone.
