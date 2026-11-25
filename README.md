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

* **The shrine, the clam and the ores, modelled.** The physical blocks are built in layers with real depth, and their
  lit parts glow:
  * the **Orb of Plenty** (once the Cornucopia's horn) is a glassy orb glowing with the light of its core - gold for
    a thunder core, sea-green for a tide core, violet for a shade core, dark smoked glass with none - cupped by three
    gold claws on a tripod, inside a tilted ring of gold (`art/renders/orb-of-plenty.png`);
  * the **Plinth of Plenty** has a footing, a gold moulding, a body of inset panels with an inlaid line round each, a
    cornice with a running meander and a polished top; the inlay glows when the shrine is whole;
  * each **Column of Plenty** has a rounded fluted shaft with four inlaid strips that glow, a base of mouldings and an
    Ionic capital with volutes at its corners (`art/renders/shrine-of-plenty-night.png`);
  * the **Giant Clam** has ribbed, scalloped halves with a wavy lip, a bright blue-green mantle between them, and a
    pearl that shimmers when it is open (`art/renders/clams-and-ore.png`);
  * **Deepslate Adamantine Ore** and **Stygian Iron Ore** are a base of stone with an overlay of crystals or lumps
    that glint faintly in the dark (`art/renders/clams-and-ore-night.png`).
* **The long roads: resources to grind, and the relics made from them.** Three new resources are won from the
  world itself, one for each god's domain, and each is the start of a long chain of crafting:
  * **Storm glass (the sky).** Where lightning strikes sand, the sand fuses into **Fulgurite** - so a lightning rod set
    on sand in a thunderstorm, or a trident of channelling, is how it is farmed. A blast furnace turns fulgurite into
    **Storm Glass**; four copper, four gold and a storm glass make two **Celestial Bronze Ingots**.
  * **Abyssal pearls (the sea).** A **Giant Clam** (four nautilus shells, four prismarine crystals and a sea pickle)
    set under water, deep in an ocean (no higher than y 50), slowly grows an **Abyssal Pearl** - open it to take the
    pearl and it begins another. Four gold, four prismarine crystals and a pearl make two **Orichalcum Ingots**.
  * **Stygian iron (the underworld).** **Stygian Iron Ore** lies in the soul soil and soul sand of the nether's soul
    sand valleys. It wants a diamond pickaxe; its **Raw Stygian Iron** is only rendered into a **Stygian Iron Ingot**
    in a blast furnace.
  * **Cores.** Four of a god-metal, four of its resource and an adamantine ingot at the heart make a **Thunder Core**
    (celestial bronze and storm glass), a **Tide Core** (orichalcum and abyssal pearls) or a **Shade Core** (stygian
    iron and echo shards).
  * **The relics, crafted.** A core, its metal, the tower's essence (won from its summit) and something of the god's
    own make the king's relic at a crafting table: **Keraunos, the Thunderbolt** (two lightning rods), the **Trident
    of the Deep** (a trident and abyssal pearls) and the **Bident of the Dead** (two wither skeleton skulls). The
    longest road to Zeus's thunderbolt: lightning on sand, fulgurite, storm glass, copper and gold into celestial
    bronze; deep ore into adamantine scrap, scrap and netherite into adamantine; both into a thunder core; and the
    Spark of Olympus from the top of the sky tower.
* **Four sets of armour.** Helmet, chestplate, leggings and boots of **Adamantine**, **Celestial Bronze**,
  **Orichalcum** and **Stygian Iron**, each better than netherite and each with a gift when worn whole:
  adamantine (the most protection - 24 points, toughness 4) gives resistance; celestial bronze gives speed and
  haste; orichalcum lets you breathe and swim like the sea's own; stygian iron keeps off fire and withering. They
  cannot burn and are mended with their ingot. (`art/renders/armor-sets.png`)
* **The Shrine of Plenty: a resource generator of three parts.** The Orb of Plenty no longer works alone: it is the heart
  of a shrine built of three blocks, each hard to make -
  * the **Orb of Plenty**, a glass orb on a gold tripod that glows with whatever core is set in it (a
    nether star, a heart of the sea, two blocks of adamantine, two blocks of gold and an ingot of each god-metal);
  * the **Plinth of Plenty** it stands on, an altar of porphyry with a gold meander and an offering dish (chiseled and
    polished porphyry, gold and an adamantine ingot);
  * four **Columns of Plenty**, fluted porphyry with a gold base and capital, two high at the corners of the three by
    three round the plinth (each from a porphyry pillar, gold and two god-metal ingots).

  Whole, the shrine lights: the inlays of the columns and the plinth glow and threads of light run from the columns
  into the orb. Set a core in it and it brings forth one or two of that core's resource every five minutes -
  storm glass for a thunder core, abyssal pearls for a tide core, raw stygian iron for a shade core, and now and then
  raw adamantine - **twice as fast where its core is at home**: in a thunderstorm under open sky, by the sea, or in the
  nether. What it makes goes into a chest or hopper beside or under the plinth, or spills out on top. Use the orb to
  see a panel over it: what the shrine still lacks, or how long until its next yield. Sneak with an empty hand to take
  the core back. (`art/renders/shrine-of-plenty.png`, `shrine-of-plenty-incomplete.png`, `shrine-of-plenty-above.png`)
* **The new resources, drawn properly.** Storm glass, abyssal pearls, raw stygian iron and adamantine, the four
  god-metal ingots, the three cores, the sixteen pieces of armour (their icons drawn on the panoply's own shapes, in its manner, so they sit beside the Helm of Athena and the rest - `art/renders/armor-icons.png`), fulgurite, stygian ore and the giant clam are
  redrawn with ramps of colour that cool in the shadows and warm in the light: crystals cut in facets, pearls and gems
  shaded as spheres, ores as clusters of nuggets, and the armour worn in two layers, as the game
  layers leather armour: a base of shaped, shaded plates - a Corinthian helm with eye sockets, a nose guard and a crest,
  a breastplate with its chest and muscles, a belt set with studs and a hem of leather strips, shoulder guards of two
  overlapping lames and bracers, plated thighs to a knee guard and a skirt of strips at the hip, greaves with a ridge
  down the shin and an ankle strap - and over it an overlay with each set's own mark: violet stones on adamantine, a
  thunderbolt on celestial bronze, a breaking wave on orichalcum, and the light of souls in the eyes, breast and knees
  of stygian iron.
  (`art/renders/divine-resources-art.png`, `art/renders/armor-sets.png`)
* **Adamantine, and the first tool set.** A new ore, **Deepslate Adamantine Ore**, lies in small veins in the deepest
  stone of the overworld (below y -40) - about twenty times rarer than diamond. It is as hard as ancient debris and
  wants a diamond pickaxe or better. It gives **Raw Adamantine**, which only a blast furnace will render into
  **Adamantine Scrap**; four scrap and a netherite ingot make an **Adamantine Ingot**, and nine ingots a **Block of
  Adamantine** (a beacon base; the ingot pays a beacon too). From the ingots come the mod's first tools - an
  **Adamantine Sword, Pickaxe, Axe, Shovel and Hoe** - better than netherite in every way: 2731 uses to netherite's
  2031, mining speed 11 to 9, a point more damage, and better enchantability (18 to 15). They mine all netherite
  can, cannot burn, and are mended at an anvil with an ingot. (`art/renders/adamantine-tools.png`)
* **Porphyry.** A new building stone, the purple of emperors with pale crystals in it: **Porphyry**, **Polished
  Porphyry**, **Porphyry Bricks**, **Porphyry Tiles**, **Chiseled Porphyry** (a gilt rosette) and a fluted **Porphyry
  Pillar**, with stairs and slabs of the polished stone and the bricks, and a **Porphyry Door** and **Porphyry
  Trapdoor**. Made from granite, cobbled deepslate and an amethyst shard; everything else is cut from it at a
  stonecutter or crafted. (`art/renders/porphyry-adamantine.png`)
* **The doors, redrawn the game's way.** Every door of the mod - bronze, Sky, Sea, Shade, garrison, verdigris, ivory
  and porphyry - is drawn like the game's own: stiles and rails lit from the top left, a window of four panes behind
  a cross of mullions above (a carved emblem in the stone doors), two raised panels below, iron hinges and a handle.
  Trapdoors match, with holes to see through. They open and shut at once, as the game's doors do.
  (`art/renders/doors-redrawn.png`)
* **Sliding doors that join up.** Each panel of a sliding door knows which of its edges join the rest of its door,
  so the frame runs only round the outside of the whole door, an inlaid line inside it and a pull sunk into its
  leading edge, whatever the door's size. Set one where it joins no other door and it goes down two high, taking a
  second panel from the stack. (`art/renders/sliding-door-joined.png`)
* **The swinging doors are gone.** The panel-built swinging doors, and the great swinging doors on the treasure rooms,
  have been taken out; treasure rooms are open again.
* **Sliding doors.** Four of them - bronze, and one in each tower's stone: Sky, Sea and Shade. Each block is a
  panel; set panels side by side and on top of each other and they make one door, of any size up to 64
  panels. Use it, or give any panel a redstone signal, and the whole door glides sideways a block at a time until it
  has moved its own width (four blocks at most) or meets something in the way; use it again, or take the signal
  away, and it glides back. It slides to the right of whoever set it - set it while sneaking to have it slide left.
  Crafted from the tower's stone or bronze with a nugget or shard in the middle of the top row, four at a time.
* **Puzzle rooms behind sliding doors.** The vault, chime room, beam room, trembling floor, shutter hall, dial hall
  and sliding stones are now shut behind a sliding door of their tower's stone, three wide and three high, that
  slides into a pocket in the corridor wall when used. (`art/renders/sliding-door.png`, `art/videos/sliding-door.mp4`)
* **Chests built in layers.** The treasure cache is now built up the way the game's own blocks are: a box of boards on
  a metal plinth, iron guards up its four corners, a lid that overhangs the box by a pixel all round, two straps over
  the lid, and a hasp hanging from it with a gem set in it - each part standing proud of the one under it, so the
  chest has depth from every side. The boards are redrawn as staggered planks with knots and grain; inside lies a
  heap of coins with a gem or two in it; and the padlock on a locked cache hangs from the hasp.
  (`art/renders/chests-layered.png`)
* **The mod's own particles.** Sixteen particles drawn for the mod at 8x8, the size of the game's own, each a few
  frames that play out over its life: storm sparks for Zeus, sea spray for Poseidon, soul wisps for Hades, a flame
  in each tower's colour for its braziers, a twinkle for treasure and solved puzzles, glyphs of the old script for
  rune stones and seals, a flash where a blow lands, stone dust, frost, motes of chaos, water drops, smoke, a
  violet gloom and falling embers. Every fire, spark, puff and glow the mod made with the game's particles now uses
  these; what is left of the game's own is its explosions, hearts, block crumbs and a few such cues.
  (`art/renders/particles.png`, `art/renders/brazier-particles.png`)
* **Two more puzzle rooms.**
  * **Dial Hall** - four stone dials on plinths before the north wall, each showing one of four sigils, and four
    frescoes in the wall above them showing the sigils wanted. Turning a dial turns the one beside it too. A
    fresco lights when the dial under it matches; light all four and the cache unlocks. (`art/renders/dial-hall.png`)
  * **Sliding Stones** - two heavy stones near the middle of the room and a socket in two opposite corners, with
    pillars in the way. Use a stone and it slides a block away from you, smoothly; push each onto a socket and the
    cache unlocks. Sneak and use a stone to put it back where it started. (`art/renders/sliding-stones.png`)
* **Rising water and sprinklers.** A flooding rune chamber has four bronze sprinklers in its ceiling. When it
  springs they pour, and each layer of water creeps in ring by ring from the walls over three seconds rather than
  appearing at once. (`art/videos/flood-sprinklers.mp4`)
* **Bigger levels.** Every passage level is at least five rooms square (it was four on the lowest floors), six on
  the grander ones, and more of its rooms are tall - a god's tower, not a burrow.
* **Chests redrawn, and locked.** The treasure caches are redrawn in the game's own flat manner, a face to each side:
  boards, a lid with a lip, a gold latch, and inside a bed of coins. About half the caches in a passage are
  **locked** and wear a padlock.
* **Lockpicking.** Craft a **Lockpick** from two iron nuggets over a stick (they also turn up in loot and in the
  barracks footlockers). Use one on a locked cache and a lock appears over it: a mark sweeps along a bar, and you
  click while it is in the gold. Each pin narrows the gold; a tower's higher floors have more pins. Miss and the
  pins drop, and the pick may snap. (`art/renders/lockpick-panel.png`, `art/videos/lockpicking.mp4`)
* **Messages over the block.** What a block says - a rune stone pressed out of turn, a locked cache, a guarded
  treasure, a seal, a shrine, a war drum, a training dummy's count - now shows in a framed panel floating over that
  block for a few seconds, rather than in the bar above the hotbar.
* **Every item redrawn.** All 45 relic weapons, the codex, the compass, the seekers, the keystones, the essences,
  the field bag, the Deck of Fate and its 22 cards, the voodoo dolls, the eight pieces of the panoply, the forge
  materials and the bolts the staffs fire. Each is designed as smooth solids and cut into crisp 16x16 pixels - the
  size Minecraft's own items are, so they sit beside them - shaded from the top left in five tones with cool
  shadows and warm lights, outlined, and the weapons keep their glint sweeping along the metal. Every part is at
  least a pixel wide, so a spear's shaft or a bow's string never breaks up. (`art/renders/items-16-*.png`; the
  generator is `tools/item_art.py`)
* **The hub, bigger and better spaced.** The plaza's radius goes from 26 to 40 blocks: the gates stand at 28, the
  ways to them are seven wide, the canal, the gardens and the echoes' plinths move out with them, two pairs of
  beacons line each way, flowering beds with benches ring the outer plaza, and the empty quarter has a still pool
  with an obelisk in it. The rotunda is larger and the Oracle and the Heart now stand inside it, under the dome.
  Each tower's god stands just behind its own gate, facing the plaza and rising over the gate, on a pedestal built
  as a monument's is: two steps, a die faced with the god's own connecting panels between corner posts with the
  god's mark lit in its front, a cornice of hanging stairs, a paved top with a lamp at each corner; the model towers stand far off behind. Whatever an earlier hub left in the sky is cleared away when it is
  rebuilt. (`art/renders/hub-larger.png`)
* **Forges join up.** Forges of Chaos set side by side become one furnace: each block knows which of its
  neighbours are forges and drops its frame along those edges, so a bank of them shows one long grated mouth of
  fire. Any block of the bank opens the forge. (`art/renders/hub-forge-bank.png`)
* **A bounty board that varies.** Each of the board's blocks shows one of six faces - a wanted poster, a map with a
  cross on it, a sealed scroll, notes, a torn notice, bare boards - on planks that run on from block to block.
  (`art/renders/hub-bounty-board.png`)
* **Pedestals and puzzle pieces redone.** The altars are tiered pedestals - plinth, fluted shaft, gilded band,
  carved capital and the glowing top - in each tower's stone, and the Ruined Gate's pedestal is the same in old,
  mossy stone. The beam room's mirror is now a real pane of glass in a gold frame on a stand, set at the angle it
  reflects at; the emitter and the receiver are lenses on pedestals; and the beam is drawn as a straight, solid line
  of light with a halo, flashing at each mirror, instead of a scatter of sparks. (`art/renders/blocks-new.png`,
  `beam-mirrors-above.png`)
* **Eleven building blocks**, drawn at 16x16 like the game's own, used across the hub and craftable: Olympian
  Marble, Olympian Marble Pillar (fluted), Gilded Frieze (a gold Greek key over a row of beads), Abyssal Tile (carved
  waves), Underworld Brick (embers in its mortar), the Chaos Lamp, Olympian Tiles (flags with gold stars where they
  meet) and the Chaos Mosaic - and three with **connecting textures**: the Olympian, Abyssal and Underworld Panels.
  Set side by side, panels drop their frames where they meet, so a wall or a floor of them reads as one great
  panel with a single frame round its outside. They face the statues' pedestals, the entablatures of the Sea and
  Shade gates and the rim of the hub's pool; the rotunda is floored in mosaic and tiles, the ways in Olympian
  Tiles, Abyssal Tile and Underworld Brick. (`art/renders/blocks-16.png`, `connected-panel-wall.png`)
* **Each tower's own stone, connecting.** Nine more blocks, three to a tower: a **column** (Marble, Coral, Bone)
  that shows its moulded base and capital only at the ends of a run, a fluted shaft between; a **floor panel**
  (Sunstone, Pearl, Ember) that frames a whole run of itself as one; and a **brick** (Storm, Tide, Ash). Every
  tower floor now has its columns of its tower's column, a dado of its wall panels round the wall, its brick
  between the windows, and its walkway paved in its floor panels with one frame round the whole ring; every
  passage room of the tower has its columns in the tower's column and, in the tall rooms, its brick above the
  band. All craftable and dug with a pickaxe. (`art/renders/tower-stone*.png`)
* **Stairs and slabs of the mod's stone.** Olympian Marble, Cloudstone, Trench Stone, Tartarus Stone and the Storm,
  Tide, Ash and Underworld Bricks each have stairs and a slab now - craftable, a double slab dropping two. Every
  builder in the mod uses them: the cornices, column bases, arches and steps of the upper tower floors and the
  passages built of these stones are cut from their own stairs and slabs, not from vanilla ones near in colour.
  (`art/renders/stairs-slabs-doors.png`)
* **Doors and trapdoors.** Four of each, every one a proper panelled leaf at 16x16: stiles and rails lit on the left
  and shadowed on the right, a sunk panel with its bevel, a raised emblem in the upper panel and a lozenge in the
  lower, studs, hinges and a ring, and an item drawn for its own size. **Bronze** (a sun), the **Door of the Sky**
  (white marble and gold, a thunderbolt), the **Door of the Sea** (dark sea-wood, a trident) and the **Door of the
  Shades** (black wood with violet light, a skull), each with its trapdoor. All open by hand and are craftable.
  (`art/renders/door-textures.png`, `door-items.png`, `doors-placed.png`)
* **Training dummies, polished.** Set a dummy on another and they become one taller dummy - a post on its foot,
  then a body, then a head in a crested bronze helm - one, two or three blocks high. Strike one and it says what the
  blow was worth (the hand's strength and how drawn back the swing was, half again for a riposte, more for momentum)
  and how many blows you have landed in a row. Right-click it for the parry and dodge lesson. Craftable as before,
  and broken by hand.
* **Sparring Dummy.** A dummy on a sledge (a dummy over a minecart to craft) that slides to and fro along the floor
  while anyone is near - a block at a time, gliding the way a piston moves a block - and turns back where its way is
  blocked or its floor changes: a strip of one floor is its track. (`art/renders/sparring-dummy.png`)
* **Barracks furniture, all craftable.** A **Chair** (sit on it: right-click; you face the way it does), a **Bench**
  (sit anywhere along it), a **Table** (tables side by side join into one long table, legs only at its corners), a
  **Dresser** two blocks wide whose drawers slide open, and a **Bunk Bed** two long and two high with a ladder up
  its foot, put down and taken up as one piece.
* **Barracks rebuilt with it.** Two quarters of bunk beds with footlockers and a dresser; a mess with a table,
  chairs drawn up on both sides, a candle and a bench along the wall; and a drill yard of dummies one, two and three
  high, racks of arms and a sparring dummy on its boards. Barracks are entered through Garrison Doors.
  (`art/renders/barracks-mess.png`, `barracks-bunks.png`, `barracks-drill.png`)
* **Three more doors** with their trapdoors: the **Garrison Door** (dark oak, iron bands, a shield), the **Verdigris
  Door** (green copper, a laurel) and the **Ivory Door** (bone white and gold, a star). (`art/renders/doors-new.png`)
* **Five kinds of glass, all connecting** - a run of any of them is one window, with one frame round its outside
  and nothing between its blocks - and all craftable. All 16x16.
  * **Reinforced Glass** - clear, framed in two pixels of bronze with rivets at the corners, as hard as stone.
  * **Frosted Glass** - milky white with crystals of frost in it: light comes through, nothing else.
  * **One-Way Glass** - every face is drawn: the front and the four sides are solid, untinted silvered glass;
    the back has a wider pewter border round a faint pane of glass you can see through, so you can always tell
    which side is which. A face is left off only where it meets another block or another pane of it, and every
    face's frame joins across a run, so a whole window or floor has one border round it. Set it down and its solid
    face is turned towards you: a floor laid from above looks solid from above and shows the sky from below.
    (`art/renders/one-way-glass-all-sides.png`, `one-way-floor-below.png`, `one-way-window-curve.png`,
    `art/videos/one-way-floor.mp4`)
  * **Smart Glass** - a smoked, shaded pane until it has a redstone signal; then clear.
  * **Veil Glass** - a solid-looking pane of pale glass, untinted, that turns clear - on a redstone signal, or when
    anything shot or thrown strikes it, which turns it whichever way it is not. (`art/videos/veil-glass-arrows.mp4`)
  * Smart and veil glass do not change all at once: the change runs through the window block by block from where it
    started, two ticks a block. (`art/renders/glass-veil-smart.png`, `art/videos/smart-glass-ripple.mp4` - an arrow in
    a target block beside it)
* **Treasure caches redone.** A chest in the game's own manner - a chunky box, a lid and a gold latch, its faces
  flat boards with a metal rim - in its tower's wood and metal: cedar and gold for Zeus, sea-wood and silver for
  Poseidon, black wood and violet for Hades. Every cache is turned to face the middle of its room, and when someone
  takes their share its lid swings up over a bed of coins and falls shut a couple of seconds later.
  (`art/renders/caches-and-braziers.png`)
* **Braziers three ways, redrawn.** The tower brazier comes as a low **bowl**, a **standing** brazier on a post, and
  a **wall** brazier on a riveted plate. Its art is drawn one texture pixel to one model pixel, the way the game's
  own blocks are: each tower's metal in five tones (bronze, verdigris, black iron) with a lit lip, a band of rivets
  and a shadowed foot; coals that glow hottest in the middle; and a four-frame flame of three tongues in four tones
  of the tower's fire, which glows on its own in the dark. Set one on a floor and it stands; set it against a wall and
  it hangs there. (`art/renders/caches-and-braziers.png`)
* **Braziers in the towers.** Every tower floor has braziers in brackets round its wall between the windows, and a
  standing brazier either side of each gate - all outside the fight ring. (`art/renders/tower-braziers.png`,
  `tower-gate-braziers.png`)
* **Three more passage rooms**, each lit by braziers:
  * **Smithy** - a brick hearth with its fires burning in two bowls and its flue up through the ceiling; an anvil,
    grindstone, smithing table and a cauldron to quench in; ore and coal heaped in a corner; racks of finished arms.
    Entered through bronze doors. (`art/renders/smithy.png`)
  * **Crypt** - four lidded stone coffins with candles burning on them, a standing brazier in each corner, cobwebs
    overhead, an aisle of the tower's inlay. (`art/renders/crypt.png`)
  * **War Room** - two long tables with chairs drawn up on both sides and candles on them, a lectern for the orders,
    the tower's standards on plinths in the corners. Entered through garrison doors. (`art/renders/war-room.png`)
* **The two-storey stairwell, rebuilt.** A straight flight of steps climbs each side wall to an upper floor that runs
  along both side walls and across the north end, with one railing round its inner edge; two pillars carry the north
  end, and the hall below is left open, a runner of inlay up its middle. Upstairs the cache stands against the north
  wall between two standing braziers, two more on the wall above it.
  (`art/renders/stairwell-two-storey.png`, `stairwell-stairs.png`, `stairwell-loft.png`)
* **Veil glass in the puzzles.** Smart and veil glass hold back light while shaded and let it through once clear,
  so a lamp behind them lights a room only when they open.
  * **Shutter Hall** - a new puzzle room: four lanterns in its quarters, each a lamp in a case of veil glass. Shoot
    or throw at a lantern and it turns - and the two beside it in the ring turn with it. Open all four and the cache
    unlocks. Two barrels of snowballs stand by the walls for whoever came without a bow. (`art/renders/shutter-hall.png`)
  * **Every puzzle room** - vault, chime room, beam room, trembling floor and shutter hall - has its wall lamps set
    back behind veil glass: dim while the puzzle stands, and opening in a ripple, light pouring in, once it is solved.
* **The glass in use.**
  * **Tower windows** are glazed in their tower's glass: Zeus's in Reinforced, Poseidon's in Frosted, Hades's in
    One-Way - from inside, the realm beyond; from outside, a ring of silvered panes that shows nothing of who is
    within. Every block of a window faces the same way, so each window joins up as one.
  * **Quiet halls**: the four holes over the room are glazed with one-way glass facing down - from the floor they are
    silver panels in the ceiling. When the ambush springs, they give way and the guard comes through.
    (`art/renders/ambush-skylights.png`)
* **Twelve new sentinels with attacks of their own** - four to a tower, seventy-five kinds in all. Every one of
  these attacks is telegraphed and can be parried or dodged. (`art/renders/new-sentinels.png`)
  * **Duelists** (Storm Duelist, Coral Duelist, Grave Fencer): a guard glints on the blade when it is up, and the
    first blow struck at one from the front is turned with a ring of steel - then it answers a moment later. Its
    guard is down for five seconds after; strike then, or from behind, or parry its answer and punish the stagger.
  * **Slingers** (Gale Slinger, Spine Thrower, Pyre Archer): a fan of five bolts at once. Step out of the fan, or
    between its bolts.
  * **Reavers** (Cyclone Reaver, Maelstrom Reaver, Scythe Reaper): spin up for a second, a ring on the floor showing
    the reach, then cut everything inside it and throw it outward.
  * **Slammers** (Skyfall Brute, Depth Crusher, Tomb Breaker): from four to twelve blocks off, it marks the ground
    under you with a ring, crouches, leaps, and comes down on the mark in a shockwave that throws you into the air.
  They join the levels as their threat allows (slingers from the second floor up, slammers from the sixth). The
  bestiary page now holds twenty-five to a tower.
* **Three more combat rooms.**
  * **Duelling circle** - a ring of the tower's mosaic, posts with the tower's fire, benches for those who watch.
    Its champion waits alone: a duelist of the tower, crowned as one of the passage's lesser gods. The cache on its
    corner plinth stays shut while the champion stands. (`art/renders/duelling-circle.png`)
  * **Overlook** - a tall hall with a railed gallery round all four walls, ladders up in two corners and four great
    pillars on the floor for cover. Its bowmen and slingers stand up on the gallery, a guard below; the cache is up
    there too. (`art/renders/overlook-floor.png`, `overlook-gallery.png`)
  * **Quiet hall** - it looks like a forgotten room: rubble, cobwebs, a cache left on a lit plinth in the middle,
    four dark holes in the ceiling. Walk up to the cache, or reach for it, and it springs: iron bars drop across
    every doorway and the room's guard drops in from the holes. The bars lift when they are beaten - or as soon as
    nobody is left inside, so no one is ever shut out of their own passage. (`art/renders/ambush-barred.png`,
    `ambush-sprung.png`)
* **The dodge.** Hold a direction and tap sneak, and you throw yourself that way - a quick dash, and for eight
  ticks no blow of the realms' creatures can find you. A blow slipped like that leaves its striker open: your next
  blow within three seconds is a riposte, as after a parry, and it counts towards momentum. A dodge needs a little
  over a second's breath before the next, and only works from the ground. Stand still and the same tap parries,
  as before, so there is still no key of its own.
* **Proving grounds.** One great room on most passage levels - the widest and tallest kind - floored in Arena Sand
  with a chalk ring round its middle, eight Beast Gates in its walls (each with a dark tunnel behind it where the
  rock allows), a War Standard on a plinth in every corner and a ledge round the walls overhead. Beat either War
  Drum and the trial begins: the gates grind open and three waves come out of them, one after the other - the
  tower's soldiers, then skirmishers and a brute, then a lesser god of the passage leading the last. Titles call
  each wave. The cache in the middle stays shut until the last one is down, and pays the best of any room: a
  second share of the tier's material and a golden apple or a totem. When the passage seals itself again, so
  does the proving. (`art/renders/proving-ground.png`, `proving-ground-wave.png`)
* **Barracks.** A wide room whose soldiers are already awake: two quarters of bunk beds against the walls with a
  footlocker at each foot (a few arrows, a loaf), racks of arms on the walls, and two drill quarters of Training
  Dummies, more racks, a barrel and a standard. (`art/renders/barracks-bunks.png`, `barracks-drill.png`)
* **Five new blocks for them, all 16x16 and craftable.** **Arena Sand** (three looks: raked, scuffed with
  footprints, stony with an old stain), the **Beast Gate** (a black portcullis; now and then a pair of eyes behind
  it), the **War Drum** (red hide laced between bronze hoops - beat it and it booms), the **War Standard** (a
  crimson cloth with a crested gold helm, on a spear) and the **Training Dummy** (a straw sack on a post with a
  target on its chest; strike it and it thumps, right-click it and it teaches the parry and the dodge). More of
  the passage rooms are of the wider size than before.
* **Entrances.** Storerooms and armouries are entered through a pair of bronze doors; libraries, sanctums,
  observatories, shrines and baths through the tower's own - thrown open against the corridor walls under the arch,
  the way between them left clear. Storerooms have a bronze cellar hatch in the floor, and a corridor pit's lids
  are the tower's own trapdoors. (`art/renders/entrance-doors.png`)
* **A new trap: the pit.** Some corridors - more the deeper the level, never beside the room you arrive in, never
  where there are jets - have their middle floored with nine of the tower's trapdoors over iron spikes. Step on them and they
  creak; half a second later they drop. Whoever goes down is on the spikes until the lids swing shut again three
  seconds later and heave them back up. Jump, or run. (`art/renders/pit-trap.png`)
* **Rooms furnished as a whole.** An ordinary room's four quarters - the floor between the lanes that run door to
  door - each hold a set piece built of several blocks, the opposite quarter the same, so a room reads as laid out
  rather than strewn with single blocks: a kerbed planter of the tower's hedge, a column of the tower on a stepped
  plinth with a relief beside it, a table with candles and benches along it, a fallen column with its drums and
  chips, a lit basin of water, a dais with the tower's brazier on it, a store of barrels, jars and sacks, or a
  reading nook of scroll shelves and a lectern. Sunken rooms keep them on the corners of the ledge round the pit,
  colonnades between their columns; the lanes are never touched. Floors have a border band round each room and
  worn patches of the other stone instead of a plain chequer. (`art/renders/rooms-furnished.png`)
* **Floor blocks polished, with breaks.** Every tile floor - Olympian, Cloud, Nacre, Throne, Abyssal and Bath Tile -
  is bevelled now, each tile lit on its top and left and shadowed on its bottom and right, and comes in five or six
  looks the game mixes block by block: whole, cracked, a corner chipped off to the bed, a tile settled lower,
  cracked and chipped together, and (for the older floors) moss in the joints. The connecting floors - the Sunstone,
  Pearl and Ember Panels and the three Mosaics - crack and settle too, without breaking their frames; the Cloud,
  Trench and Tartarus Stone and the three towers' bricks crack. (`art/renders/floor-variants.png`)
* **A real weapon rack.** The Weapon Rack is a rack on the wall now, not a painted cube: a board of dark oak, two
  rails, and on them a spear, a sword, a shield or two crossed axes, modelled in the round - which, the game
  chooses block by block. It faces the way it is placed, and racks side by side or one over another join: end
  posts and the cap stand only where the run ends. (`art/renders/weapon-rack.png`)
* **The upper floors in their own stone.** Six more blocks for the towers' higher stretches: Cloudstone and Cloud
  Tile (the Sky's), Trench Stone - dark, with glowing specks - and Nacre Tile (the Sea's), Tartarus Stone and the
  gold-lined Throne Tile (the Shades'). The second stretch of every tower now uses the Olympian, Abyssal and
  Underworld stone, the third and the summit these. (`art/renders/upper-floors.png`)
* **Portal frames and arrival sigils.** Every gate - in the hub, on every tower floor, at the end of every passage
  level - stands between the Portal Frames of its tower (the Sky, the Sea, the Shades, or the Hub's for the way
  out): columns with a rune glowing in each, a base and a cap only at the ends. And wherever somebody arrives -
  the hub's spawn, the hub's arrival points, every tower floor's arrival pad, every passage level's first room -
  they stand on an Arrival Sigil, a ring of the three gods' colours in gold. (`art/renders/portals-and-sigils.png`,
  `portal-frames.png`)
* Every room updated in these builds, as it stands now: `art/renders/rooms-updated.png`. Two developer commands
  help look round: `/roc dev floor <tower> <floor>` and `/roc dev room <tower> <kind>`.
* **Blocks that vary.** The blocks laid in great runs now come in several looks the game picks between, block by
  block, so a wall or a floor of them never repeats: the Storm, Tide, Ash and Underworld Bricks (plain, cracked, a
  brick gold-veined, barnacled or ember-lit, a worn patch), the Abyssal Tile, Olympian Tiles, Bath Tile and Olympian
  Marble (a stain, a chip, the pattern begun elsewhere), the Star Vault (its stars somewhere else each time, now and
  then a comet, turned every way), the three hedges (leaves turned, flowers scattered differently) and the Bronze
  Grate (bright, or going green). Every variant still runs on into its neighbours. (`art/renders/block-variants.png`)
* **Racks and shelves that connect.** The Weapon Rack and the Scroll Shelf are connecting blocks now: a run of racks
  is one rack, and a wall of scroll shelves one case of pigeonholes. Libraries have two walls of books and two of
  scrolls.
* **Four new rooms, and twelve more blocks to furnish them.** Passages now have **observatories** (a vault of
  stars overhead, the floor's quarters in the tower's mosaic, celestial globes on bronze stands and lecterns of
  star charts), **armouries** (racks of arms along the walls, bronze grates underfoot, an anvil, a grindstone and a
  smithing table), **baths** (four tiled basins of lit water, tiled ways between them) and **sanctums** (the whole
  floor one framed mosaic of the tower, the walls lined in its panels, a brazier of its fire in every corner, a chaos
  lamp hung over the middle). Gardens grow each tower's own clipped hedges in their corners, and libraries keep
  scrolls among their books. The new blocks: the Mosaics of the Sky, the Sea and the Shades (connecting floors), the
  Star Vault, the Celestial Globe, the Bronze Grate, the Weapon Rack, the Scroll Shelf, Bath Tile, and the Laurel
  Hedge, Kelp Weave and Asphodel Bed. Every one is craftable and drops itself. (`art/renders/new-rooms.png`,
  `room-observatory.png`, `blocks-rooms.png`)
* **Reliefs.** The Relief of the Sky, of the Sea and of the Shades - a thunderbolt under clouds, a trident over the
  waves, a skull over a bident and flames - carved into the walls of every passage room either side of the middle
  of each wall, and under every other window of the tower floors. (`art/renders/passage-reliefs.png`,
  `tower-relief.png`)
* **More traps.** Three new hazards and nine new trap halls, fifty-five in all: **Crush** (a stone drops from the
  ceiling: a heavy blow and a swimming head) in Crushing Rows, Falling Sky and the Millstone; **Snare** (roots close
  on the ankles and hold you all but still) in Root Tangle, Briar Rings and Stranglevine; and **Drain** (the floor
  feeds on you: hunger and weakness) in the Leech Floor, the Hunger Tide and the Sapping Weave. Each was checked to
  be crossable by somebody who watches it, and neither harmless nor a wall.
* **Trap fittings of their own.** Spike halls are floored in Spike Plates, light and dark, each with four sockets,
  and what comes up through them are Trap Spikes - a cluster of iron spikes, rusted at the root - instead of
  dripstone. Flame halls and jet corridors have Flame Vents (a grate over glowing coals); gas halls have Gas Vents.
* **Puzzle blocks redone.** Glyph tiles, trembling plates, the dart tile, the beam receiver and the lore tablet are
  dressed stone now: bevelled edges, a sunken panel with a chamfer, the sign cut in as a groove with a lit lip - and,
  when lit, a glowing inlay with a halo and gold studs. The chime crystal is a faceted crystal with a vein of light,
  and the ancient urn a black-figure amphora with a running warrior on it.
* **The middle of the hub is built as a place.**
  * A **rotunda** stands over the spawn: eight marble columns with bases and capitals, a gilded entablature, and a
    dome of verdigris copper with a gold lantern at its crown and chandeliers hung under it. The Oracle's Flame
    and the Heart stand in its openings.
  * The **Bounty Board** is a real notice board: nine boards of notices, three by three, in a dark oak frame under
    a gabled roof, lanterns under the eaves, a barrel, a lectern and a shelf of scrolls beside it. Every one of the
    nine is the board itself, so it opens from wherever you click it. (`art/renders/hub-bounty-board.png`)
  * Each **Forge of Chaos** is a smithy: a bank of ten forge mouths two high on the corner facing the plaza, every
    one of them a Forge of Chaos, under a hood; a chimney rising twenty blocks with smoke at its top; a hipped
    roof round it on posts; an anvil, a grindstone and a smithing table; and a quenching trough fed by the canal.
    (`art/renders/hub-forge.png`, `hub-centre*.png`)
* **The hub looks out on the gods and their towers.** Beyond each tower's gate, outside the plaza's wall, a
  colossus of the tower's god stands on a pedestal on its own floating island, facing the plaza and rising over the
  gate: **Zeus** in marble with a golden thunderbolt raised, **Poseidon** in prismarine with a golden trident,
  **Hades** in blackstone, helmed, eyes burning, with his bident. Each is shaped from rounded solids - a robe in
  folds, a sash, arms, a bearded head under a wreath, crown or helm - so it reads as carved, not stacked. Far off
  behind each one, to the side, a **model of the tower** rises out of the sky on an island of its own: fifteen floors
  banded in the stone of each of its stretches, lit windows, buttresses, cornices where its stone changes,
  pinnacles and a beacon on its summit, clouds drifting about it. They are scenery only - the real towers are
  still reached through the gates. The plaza itself now sits on an island of rock, its rim on corbels, lanterns
  hung beneath. (`art/renders/hub-vista-*.png`)
* **Built like builders build.** Every structure in the mod was rebuilt with a mason's kit of stairs, slabs and
  walls, so nothing is a box of whole blocks any more:
  * **Tower floors**: windows set deep in the wall under round heads, engaged columns with moulded bases and
    capitals, a stepped cornice where the wall meets the ceiling, a ribbed ceiling with a chain chandelier in the
    middle and four lesser ones, and the gates as proper gateways - pilasters, a round head, an entablature and a
    pediment. Walls are laid in mixed stone (a few mossy, cracked or worked blocks among the rest), never stripes.
    The fight ring is untouched: the same flat, walkable ground, nothing within six blocks above it.
    (`art/renders/build-tower-*.png`)
  * **Citadels**: round corner towers with banded walls, arrow slits, corbelled battlements and an open lookout
    under a conical roof in a contrasting stone (slate for the Sky, dark prismarine for the Tides, red nether brick
    for the Shades); a curtain wall on a battered plinth, buttressed, slit, with its parapet carried out on corbels;
    a gatehouse that is one block over the gate, with a round head and the portcullis drawn up; a keep with a
    plinth, a string course, hooded windows, a porch with a pediment and a patterned roof; statues on moulded
    plinths and lamp posts along the way in. (`art/renders/build-citadel-*.png`)
  * **The Ruined Gate** is now a ruined temple: a stepped round platform, a gateway of two columns under an
    entablature and a pediment half fallen, a ring of columns with bases - a few standing under their capitals, most
    broken off, some lying where they fell - braziers on stepped feet, and grass and moss between the stones. A
    hillside behind it is cut back in grassed terraces instead of standing over it as a wall, and on falling ground
    it stands on an earth bank rather than a sheer pillar of stone. (`art/renders/build-ruined-gate*.png`)
  * **The hub**: the four gate monuments are gateways with pediments, the beacons stand on moulded bases under
    capitals, the parapet is a joined balustrade with a plinth on its inner face, the garden beds have kerbs, and
    the cherry trees fork and grow a ragged crown instead of being cubes of leaves. (`art/renders/build-hub.png`)
  * **Passages**: walls in mixed stone, round-headed arches at the ends of every corridor, and a cornice round the
    ceiling of every tall room.
  * In an existing world each place is rebuilt in its new design the next time it is reached: tower floors, the
    hub, passage levels, Ruined Gates and citadels. A citadel or gate rebuilt this way has its chests filled again.
* **Gates stay shut.** A level's Gate of Ascent is dark glass in its arch until every seal on the level is broken;
  the last seal opens it, and a level sealed again has its gate shut again.
* **Corridors.** Rooms stand further apart: seven blocks of lit corridor between two of them, with an arch at each
  end, instead of a doorway through a wall. Some corridors - more of them the deeper the level, and never beside
  the room you arrive in - have **jets** across the middle that smoke, then burn, then rest.
* **Trap halls that do not say what they are.** A trap hall's floor is the floor of any other room:
  no vents, no coloured tiles. The only signs are a few stained tiles, the bones of whoever came before, an urn or
  two in the corners for whoever dares - and, for a moment before a tile goes off, a wisp over it. Each hall is
  something that hurts, a way it moves across the floor, and a size of room:
  * **Eleven hazards**: spikes, fire, soulfire, frost (slows and freezes), venom, lightning, rot (wither),
    geysers (thrown at the ceiling and dropped), gales (shoved across the floor), lifting (levitated in a tall
    room, then let go) and blight (blinded).
  * **Twenty-two patterns**: rows, columns, a line that comes back, two lines that close on the middle, rings
    that spread or close, a floor taken from the walls inwards, checkers, stripes, a weave, quarters, diagonals,
    a creeping lattice, a cross-then-saltire, one or two turning arms, a scatter that is different every round,
    hail, a row and column that cross somewhere new each time, the straight way from door to door being the
    trap, **one narrow path** that shows itself only for a moment, and halls that **strike where you stood** -
    never stand still.
  * **Three sizes**: small (seven across - close work), ordinary, and wide (eleven across - a long way).
  * **Harder the deeper.** The warning shortens with the level, and the nastier halls only turn up on levels
    dangerous enough for them. No hall is on a level twice.
  * **Always fair.** The three tiles just inside every doorway are safe, to watch from. Every hall is proved
    crossable - by a walker slower than a walk, at every depth - before the mod is built (`Gauntlet.crossable`).
  * **Learnt by being bitten.** The first time a kind of hall catches you a card names it - *Ember Tide*, *The
    Jaw*, *Rime Wheel*, *Hollow Steps* - and says how it is beaten.
* **Citadels hold the keystones.** The three shrines are now castles of their tower's stone, 43 blocks across: a
  curtain wall with a wall-walk, a round tower at each corner, a gatehouse with its portcullis drawn up, a
  courtyard of fallen statues, and a three-storey keep with the keystone on its roof. The Citadel of the Sky is
  white and gold; the Tides' stands behind a moat; the Shades' is black stone ringed with soul fire. When you come
  within sight of the walls the garrison stands to: **half-kin** - children of the tower's king - at the gate, in
  the courtyard, on the towers and in the keep, and on the roof beside the keystone a **fallen Titan**, who gives up
  a piece of the panoply the first time it falls to you. (`art/renders/citadel-*.png`)
* **Titan fights.** Each Titan is a creature of its own, half as big again as a man, bound in chains, armed, with
  three skins - itself, lit with its power, cracked open in its fury - and a boss bar that names each move as it
  winds up, with a line above the hotbar on how to get out of the way. Two moves from the start, a third at
  three-fifths health, its greatest at three-tenths; each phase begins with a roar that throws everyone back while
  it is untouchable for two seconds, and a shockwave across the roof. Knocked off its roof, it is set back on it.
  * **Hyperion, the Fallen Light** - *Solar Lance* (a line of light: step off it); *Blinding Dawn* (blinds only
    those looking at him); *Falling Stars* (burning rings, then meteors); *Corona* (rings of fire to jump).
  * **Oceanus, the Fallen Sea** - *Undertow* (drags everyone in for a slam); *Waterspouts* (under whoever stands
    still); *Tidal Wall* (find the gap); *Call of the Deep* (half-kin rise to fight beside him).
  * **Cronus, the Fallen King** - *Scythe Sweep* (a cleave in front: get behind him); *Devour Time* (everyone slows,
    he quickens); *Rewind* (his target is thrown back to where they stood three seconds ago); *Age of Iron* (the
    roof splits in a star, and he fights faster and harder). (`art/renders/titans.png`)
* **Far apart.** The citadels lie on a grid a thousand blocks to a square, the three kinds taking turns so no two
  of a kind are neighbours, and any two at least five hundred blocks apart; the Ruined Gates have their own grid,
  never near a citadel. Each keystone is a journey. The Ruined Gate itself is grander: a ring of twelve columns -
  some standing lit, most broken, some fallen - four braziers and a processional way to the arch.
* **The compass speaks on a tab.** The Compass of Chaos and the seekers no longer write above the hotbar. A tab
  slides out from the right edge of the screen when there is something to say - when you use one, on a clean
  lock, a static surge, every few rolls, and at the end of a scan - and slides away after a few seconds. It has a
  dial whose needle keeps to its bearing as you turn, the thing pointed at, how far, the signal in five bars, the
  time left and how far along the scan is. Between readings it folds back in to a small compass the size of a
  status effect's icon, its needle still pointing and a ring round it filling as the scan goes on; it stays while
  a scan runs, while a compass is in hand and for a while after, and the last reading pops out again when a
  compass is picked up. (`art/renders/compass-fold.png`)
* **Three kinds of rune chamber: work fast, and remember.** A rune chamber shuts you in and gets worse by the
  moment until its runes are pressed in the right order. A **flooding** chamber shows the order over and over
  while the water rises. A **furnace** shows it **once** - and again only after a mistake - while the heat
  builds and burns. A chamber of **gloom** shows it over and over but wants it pressed **backwards**, while the
  dark closes in. Which one a chamber is, it only says when it springs.
* **The panoply: eight pieces of armour from the old stories**, each with one power, two for each part of the
  body - so whoever owns them all still has to choose. *Helm of Hades* (unseen while you sneak), *Helm of
  Athena* (night is as day, and what means you harm is lit), *Aegis Cuirass* (wards every blow; a second wind
  when badly hurt, once a minute), *Nemean Pelt* (hard to wound, hard to move), *Girdle of Hippolyta*
  (strength), *Greaves of Achilles* (swift-footed), *Talaria* (leap high, land soft), *Treads of the Styx* (fire
  and deep water cannot harm you). Each has its own look when worn. (`art/renders/ingame-panoply-worn.png`)
* **Twelve lesser gods hold the arenas.** Four to a tower, met in turn as its passages are climbed - Eris,
  Nemesis, Tyche and Boreas; Glaucus, Leucothea, Phorcys and Ceto; Styx, Lethe, Achlys and Geras. An arena's
  champion is one of them: named, larger, half as much health again. The first time each falls to you it lets
  fall the piece of the panoply it keeps; after that, one time in eight.
* **Commissions: the slow way to a relic.** From the Codex, the forge can be asked to make any piece of the
  panoply or any god's relic - for a great deal of a tower's forge material (twenty to thirty-four for a relic,
  ten from each tower for armour) and a long wait: half an hour of play for the least relic, nearly two hours for
  a king's. One thing at a time; it works only while you are in the world, and keeps the thing until it is
  fetched. Beating the god is always quicker - and the forge makes a god's relic only for someone who has stood
  on that god's floor.
* **Onslaughts: attacks on the whole floor.** Every so often in a trial - sooner, and from a wider choice, the
  further it has gone - the god turns on the floor itself. **Shockwaves** run out from the middle in three rings
  and have to be jumped. **Fissures** split the floor in a five-pointed star, and then along the five lines
  between. A **cross** from wall to wall, then a saltire. **Rings** that go off in turn. A **wall** that sweeps
  the arena with one gap in it. Each is lit along the ground before it lands - the floor gives it away - follows
  the shape of the terrain, throws pieces of the paving clear, and always leaves somewhere to stand; a line above
  the hotbar says what to do. Between them a ripple of dust runs out over the ground every few seconds.
* **Halls between the rooms.** Two more ways a corridor can run: a **gallery**, seven across and four high with a
  pillar in each corner, and a **causeway**, a walk three wide with water lying to both sides. Only a quarter of
  corridors now run straight. Their vents let out fire, frost, sparks or venom, different from corridor to
  corridor. More rooms are tall and more than half are a step wider.
* **Forty-six trap halls**, eight of them new: rings that go off in turn, a comb of teeth from either wall, a
  line that swings from side to side like a pendulum, and two lines that cross the room together.
* **Corridors that wind.** Fewer than half the corridors between rooms run straight. The rest go round a baffle
  that hides what is ahead - to one side, or either - or pass a nook with an urn at the back of it. The maze
  itself turns three times as often as it carries straight on, and has fewer short cuts.
* **Laid out like a stronghold.** No doorway joins two rooms of the same kind - corridors included - and a kind
  already used on a level is less likely to be used again, so a level is a run of different rooms rather than
  the same hall three times. Two new kinds: **cells**, four barred cells in the corners with a way into each
  and whatever was left behind in them, and **storerooms**, stacked with crates and bales, higher against the
  walls, with something to find among them.
* **Afflicted levels.** The first three levels of a passage are clean. Past them most levels carry an
  affliction, laid on everyone there for as long as they stay: **Gloom** (the dark closes in, on and off),
  **Weight** (slowness), **Frailty** (weakness), **Hunger**, or **Fatigue**. A card says which on the way in,
  and the level's plaque keeps saying it. **A shrine holds it off** for a minute and a half.
* **Two more trap halls.** An **arc hall**, where a line of lightning turns about a pillar in the middle, and a
  **gas hall**, where each half of the room fills in turn and the other is the place to stand.
* **Puzzle blocks cut from one stone.** Glyph tiles, memory plates, beam emitters and receivers, chime crystals,
  dart tiles and lore tablets are redrawn in the rune stones' style: grey grained stone, a scored frame, one carved
  sign that is dull until lit. Mirrors are a pane of polished silver in a stone frame, the glint leaning the way
  the mirror is turned.
* **A chart that remembers.** Rooms walked are kept with the world instead of being forgotten when the server
  stops. The room you stand in is outlined in white, a yellow point shows which way you face, and companions on
  the level show as blue marks.

* **Parry, riposte, momentum.** Fighting the realms' creatures is more than trading blows, and none of it needs a
  key of its own.
  * **Parry.** Tap sneak - or raise a shield - just as a blow would land and it is turned aside: no harm, the
    attacker reels (slowed, weakened, and open), and the next blow within three seconds is a **riposte**, half as
    hard again. A bolt parried goes back to whoever loosed it. Even a god's blow can be met. The moment is seven
    ticks; a tap that parries nothing has to be waited out.
  * **Dodge.** Hold a direction and tap sneak instead, and you dash that way; for a moment no blow can find you,
    and one that misses leaves its striker open to a riposte.
  * **Momentum.** Every blow landed without being hit builds it, to five; each point adds 4% to every blow. Being
    struck loses it all.
  * **Exposure and the plunge.** A blow on a reeling enemy counts half as much again; a blow struck while falling
    shakes everything round the one it lands on.
  * **A combat meter** above the hotbar shows momentum, whether a parry is to hand, and a riposte waiting. It can be
    turned off in Settings.
* **Sentinels strike heavy blows.** Those that fight hand to hand - on any level past the first, and every elite -
  draw back for most of a second before a blow nearly twice as hard. It can be seen coming, stepped away from, or
  parried, and a heavy blow parried leaves the sentinel reeling twice as long.
* **A seal's boon.** Whoever breaks a seal is offered three boons and takes one - Vigour, Edge, Swiftness, Ward,
  Keen Eye (longer to parry in), Thirst (a heart back per sentinel felled) or Fortune (more from every cache). The
  same boon can be taken up to four times. Boons last until the passage is behind you.
* **A frame for every phase.** A god's bar is plain metal in its first phase, studded with crystal while its aegis
  is up - with a pylon on the plaque for each still standing - and toothed, cracked and glowing in its wrath, with
  the rings of floor still left. The aegis and the wrath are each announced on a card of their own.
* **A better chart.** Rooms that matter carry a sign (a cross for a seal, a box once it is broken, a diamond for a
  cache, an arch for the gate); wide rooms are drawn wide; a mark along the top for every level of the passage and
  along the bottom for every seal. **A shrine shows the whole of its level** on the chart of whoever it blesses.
* **The gate faces the way in.** The Gate of Ascent stands across the doorway a climber comes through, in an arch
  of its own, instead of always facing north and south.
* **A bestiary** (`/roc bestiary`, or from the Codex): every kind of sentinel you have felled, a tower to a page,
  with what it does and how many have fallen. Tall rooms are hung with lamps on chains, each tower's own kind.

* **Its own things on the screen.** Nothing the mod shows takes over the view.
  * **Tips are cards.** A tip is a small card at the top of the screen with up to three lines on it, shown for a few
    seconds; cards wait their turn instead of replacing one another. Any title too long to fit a small window is
    written on a card too. Short ones (*Victory!*) stay titles.
  * **Boss bars are drawn.** A god's bar is its tower's own frame and emblems, a fill coloured by phase, a mark
    where its aegis rises (65%) and where its wrath begins (30%), a gem lit for each phase reached, and its name in
    block lettering.
  * **The cast bar is opt-in.** A god's move is no longer named for everyone above the hotbar. Turn the cast bar on
    (Codex > Settings, or `/roc casts`) and each move is named on a bar that fills as the god winds up.
  * **Settings and insight menus**, each with its own panel: `/roc settings`, `/roc insight`, or from the Codex.
  * **Drawn by the game itself.** The HUD is a real overlay drawn by the mod on your own game (`RocHud`), from
    the textures in `textures/gui/hud/`: the god's panel, the plaque of the floor or passage you are on, the
    cards, the cast bar and the combat meter. It is no longer letters of a font sent down the chat line, so there
    is nothing for an out-of-date resource cache to turn into boxes.
  * **One bar for a god.** A trial shows one panel: the god's. The floor's plaque steps aside while a trial is on,
    and the game's own boss bar is not used for a god at all unless the drawn interface is switched off.
  * **A plaque for every floor and passage level.** Which tower, how far up, a meter of the climb - or, in a
    passage, the level's name, a notch for every seal and how many are broken. A level with an affliction says so
    on a strip under the plaque, greyed while a shrine holds it off.
  * **Less said twice.** The same card is not put up again within half a minute, nor queued twice; the same line
    above the hotbar is not rewritten within a second; a room says what it is the first time you walk in, not
    every time; a god tells you what it remembers of you only when that has changed; and a god changing its
    stance mid-trial is a card, not a line of chat for everyone.
  * **Pages of its own for everything written.** The Codex, the Climber's Handbook, the Oracle's Chronicle and
    its chapters, the trial altar, the party and mastery screens are no longer the game's stock dialog: each is a
    page on a tablet of dark stone (`PageScreen`), with a header in the page's own colour, text set out in
    paragraphs that scroll, a picture of the item a line speaks of, and buttons drawn to match, in columns, with a
    note when pointed at. A page is a menu type of the mod's own with no slots to click; a button pressed travels
    as the menu's button number, so only the game's own packets are used. `art/renders/ingame-page-*.png`.
  * **Room to read.** A god's panel has a name tab tall and wide enough for everything on it to sit with air
    round it: the pylons still standing (or rings of floor still left) at its left, the god's name in the middle
    of the room between, a gem for each phase at its right. Under it, a health bar clear of its housing on every
    side, with marks where the aegis rises and the wrath begins. The floor plaque is the same family a size smaller, with whatever
    does not fit on it on a strip beneath. (`art/renders/ingame-hud-*.png`)
  * **Pages get out of the way.** A page with one or two things to do - begin a trial, call a rematch, take a
    chapter as a book - keeps those buttons at its foot, always in view, and closes by itself once one is
    pressed. Only a button that leads to another page keeps a page open.
  * **A look for every screen.** Each written screen is drawn on its own material, with its own emblem and
    buttons to match: the Codex on amethyst, the handbook in stitched leather with brass corners and a ribbon, the
    Oracle's Chronicle on a night sky, the altar on carved stone with a meander, the Bounty Board on planks and
    cork with its heading on a pinned note, the party on a green banner, mastery in bronze, a waygate in teal, the
    hub's heart in marble and gold. (`art/renders/ingame-pages-themes.png`, `ingame-page-board.png`)
  * **Every build says which it is.** A released jar carries its build number in its name and its version
    (`realms-of-chaos-forge-1.2.0-alpha.57+mc26.3.jar`), the first time you join a world on a new build a line of
    chat names it, and `/roc version` shows it at any time. If a game and the world it joins run different builds,
    the HUD says so. Remove older jars from the mods folder when you install a new one.
  * **Seen in the real game.** The menus, cards, plaques and a god's panel have been looked at in a running
    Fabric client and a running Forge client (`art/renders/ingame-*.png`).
  * **For whoever runs the world** (operators): `/roc dev tower <zeus|poseidon|hades>` goes straight to your
    floor of a tower, `/roc dev card` shows two cards, `/roc dev art` shows the older font-drawn bars.
  * **A switch.** Codex > Settings > *Drawn interface*, or `/roc art`, turns the overlay off: boss bars, the cast
    bar and cards are then plain text on the game's own screens. It is on unless you turn it off. **Client and server must run the same
    build.**
* **Gods that remember.** Every trial is watched, player by player: how much harm was done from afar or hand to
  hand, blows taken on a shield, wounds mended mid-fight, time spent out of reach. A habit shown in trial after
  trial is learnt, through three levels, and the next god already has an answer: shots count for less and it
  closes in on a **marksman**; its guard lashes back at a **duellist**; its blows knock a **shield-bearer**'s
  shield aside; a marked strike falls where a **mender** stops to heal; a **runner** is hauled back. A card says
  what a god knows of you as the trial starts and what was learnt as it ends. Drop a habit and it is forgotten, a
  trial at a time. This is on top of the stance a god takes within a single trial.
* **Rooms that are not all alike.** Passage rooms are low (5), tall (7) or great (8 high); about a third of the
  ordinary ones are a step wider (11 across); and a room may have its corners cut, a ring of columns to the
  ceiling, or a floor sunk a step with stairs out of it. Arenas are always the biggest room there is. Levels are
  built as solid slabs twelve deep, so a tall room is still inside its own level.
  * **Stairwells.** Nearly every level has one: a great hall with a flight of steps at either side up to a loft
    across its north end, and a cache on the loft.
  * **Three more trap halls**, none of which can be switched off: a **spike hall** (two colours of tile, each
    colour's spikes up in turn), a **flame hall** (vents erupt a row at a time, the fire walking down the hall) and
    a **blade hall** (a blade sweeping from wall to wall and back). Each warns before it strikes, and they hurt
    more the deeper the level.

* **Trials in three phases.** A god fights as itself down to 65% of its health, and no single blow carries it past
  that mark or the next.
  * **Aegis (65%).** It raises shield pylons round the floor - two on Easy, three on Normal, four on Hard, one more
    for a group of three - and nothing can hurt it while one stands. Right-click a pylon to strike it; three blows
    break it. The boss bar says *Aegis*, light runs from each pylon to the god, and an aegis nobody breaks fades
    after 75 seconds. When it goes, the floor starts to shift.
  * **Wrath (30%).** A last stand (two seconds untouchable, two sentinels, its signature move and its line's
    ability back to back), and then the floor is lost ring by ring from the wall inward: the paving turns to
    charged stone under Zeus, black ice under Poseidon, magma under Hades, and standing on it hurts. Strikes fall
    from above - lightning, the sea bursting up, burning stone - each marked by a ring on the floor for a second
    and a half before it lands. The middle nine blocks are never lost.
  * **The floor is given back.** Every block a fight changes is put back the moment the god falls, and a floor
    left scorched by a server stop is rebuilt the next time anyone sets foot on it.
* **Gods that adapt.**
  * **To your gear.** When a trial is called the god measures everyone on the floor: the hardest-hitting weapon in
    each pack (a relic counts for its level, stars and temper), the enchantments on it, and armour with its
    enchantments. The strongest challenger sets the bar. Gear past what the floor expects gives the god up to 2.2
    times its health and up to 1.6 times its damage; poor gear never makes it weaker. The Trial Altar's screen
    tells you what it will make of you before you begin.
  * **To how you fight.** Between phases it looks at how the last one went. Mostly shot at: it turns shots aside
    (they do 40%) and throws itself at whoever stands off. Mostly struck hand to hand: its guard lashes back and
    the floor erupts more often. A phase that was over quickly makes the next one heavier - another pylon, more
    strikes, a faster collapse - and a long one lighter.
  * **To the difficulty.** Easy, Normal and Hard set its health (85%, 100%, 125%), how long it waits between
    moves, and how many pylons it raises.
  * **In its looks.** Every god's skin has been redrawn with more detail - the mark of its tower on its back, gold
    on the arms of the stronger gods, a cape on the three greatest of each tower - and each god now has three
    skins: itself, lit with runes behind its aegis, and cracked open with fire inside in its wrath.
* **The mod's own menu artwork.** The Codex's tower plans, the forge, the passage map, the seal, the deck, the
  party, the chronicle and the Field Tower Bag no longer look like chests. Each has its own panel - a dark field
  with a header in the menu's colour and its emblem, and a parchment half for your inventory - unused slots are
  blank tiles of the panel and meters are gems instead of glass panes. Each is a **real screen of its own**, like
  an inventory: a menu type registered with the game for every panel, and a screen on the client (`PanelScreen`)
  that draws that panel's background from `textures/gui/screen_<name>.png`, with the slots where the game puts
  them. Settings, insight, boons and the bestiary have panels of their own too.
* **Passages of one to ten levels.** How many levels lie between two boss floors is decided by the world's seed:
  one to four low in a tower, two to seven in the middle, four to ten near the top (about 190 levels across the
  three towers in a typical world). Every level has a name - **7-B2** is passage 7, wing B, level 2 - shown on its
  bar and title. Wings are three levels long and each is built from a different set of its tower's stone. Levels
  after a passage's first may be a row and a column larger.
* **No waiting at the gate.** The next level is built in the background, about four milliseconds a tick, while
  you explore the one you are on, and a passage's first level is built while the trial below it is fought. Arrive
  early and only what is left is finished on the spot.
* **The Passage Chart: a map with fog of war.** Everyone is handed a chart on entering a passage. It is an
  ordinary map item, drawn by the mod: blank parchment to begin with, each room inked in when you first walk into
  it - its kind by colour, its doorways, a broken seal turning green - with a white mark where you stand. `/roc
  chart` gives a new one.
* **Four puzzle rooms**, each keeping a cache locked until it is solved (for everyone at once; they lock and
  scramble again when a passage re-seals):
  * **Vault** - nine glyph tiles; pressing one turns it and its neighbours; light all nine.
  * **Chime room** - ring four crystals before the first falls silent (eleven seconds low in a tower, seven high).
  * **Beam room** - a wide, tall room laid out differently every time: the emitter on any wall, and three to
    five mirrors (more the deeper the level) that each have to stand the right way for the beam to reach the
    receiver. Two or three more mirrors are decoys the right path never touches. Every turn of a mirror is
    counted: one past the limit and the room throws every mirror about again and stings whoever was fumbling.
  * **Trembling floor** - a ring of plates of which one path holds. It glimmers every six seconds and has to be
    walked in order from its first plate; a wrong plate throws you off.
* **Two more trap halls.** A **crusher hall** marks a row and a second later brings the ceiling down on it, row
  after row; the row that just fell is the safe one. A **dart hall** has marked tiles that loose arrows from the
  walls at whoever treads on them.
* **Things to find.** **Urns** can be smashed: most hold a little loot, some nothing, a few a sentinel. **Lore
  tablets** tell a line of the tower's story (24 in all) and give a little experience the first time. Each tower
  has its own **brazier**.
* **Mastery.** A point for every god woken and every passage walked, spent in the Codex (or `/roc skills`) on
  twelve skills of three ranks each: *Combat* - Might, Swiftness, Focus, Reach; *Survival* - Vigor, Guard,
  Steadiness, Second Wind; *Exploration* - Cartographer (the chart sketches rooms next door), Signal, Scavenger,
  Pathfinder. The tree can be taken apart and re-spent at any time.
* **Sixty-three sentinels** (seventy-five since). Each tower now has twenty-one kinds instead of six. Every kind has a role (soldier,
  caster, skirmisher, brute, mystic, Keeper) and most have one **trait** that no other kind in their tower has. The
  deeper a level, the more kinds it fields.
  * *They cover for each other:* a **shield-bearer** turns blows from its front and shelters whoever stands beside
    it; a **warden** takes half of every blow that lands on an ally; a **standard-bearer** strengthens everyone near
    (and they falter when it falls); a **dancer** trades places with a wounded ally and mends it; a **singer** stands
    one fallen ally back up; a **berserker** grows stronger each time an ally falls.
  * *They make trouble:* **bombers** run at you, hiss and burst; **binders** root you (Zeus), haul you in
    (Poseidon) or blind you (Hades); **sowers** leave patches of ground that hurt; **snipers** aim along a line of
    light before one heavy shot; **thieves** knock your weapon out of your hand and into your pack (nothing is ever
    lost); **silencers** stop your relic's move for six seconds; **mirages** split into three, two of them harmless
    copies; **chargers** run you through; **heralds** sound an alarm that wakes the rooms next door.
* **Layered skins.** Gods and sentinels are drawn with the player model, so a skin's outer layer stands proud of
  the body: pauldrons, breastplates, greaves, cloaks, tabards, sashes, belts, furs, chains, bandoliers, masks,
  plumes. All 63 sentinels and all 45 gods wear some.
* **Bolts are models.** Staff bolts, the gods' shots, thrown relics and thrown cards are no longer a flat picture
  facing the camera: they are drawn as a 3D model lying along the line of flight, nose first, turning as they go.
* **Craters.** A bolt from a relic of level 3 or more breaks the ground where it lands - on anything, not only on
  an enemy. The crater is one block wide at level 3, two at 6, three at 9, and one more with three stars; the blast
  hurts what is near. **Out in the world a crater stays** (where you could have dug it yourself: not in adventure
  mode or spawn protection). Inside the realms the towers mend themselves: there a crater is one block deep and
  closes after ten seconds, lifting out anyone standing in it. Chests and unbreakable blocks are never taken.
* **Rune chambers.** The trap rooms of the passages are now traps with a puzzle in them. A rune stone stands on a
  pillar in each corner; step onto the marked square in the middle and the doorways close and water rises a layer
  at a time. The runes light up in an order, again and again - press them (right-click) in that order and the room
  drains, opens, and pays each player in it forge material, once. A wrong rune starts the order again and the
  water leaps up a layer. It is a trap, not a grave: once full it hurts its catch for a quarter of a minute and
  then lets go by itself, unpaid. It resets when nobody is left inside, never springs for someone who has solved
  it, and a room left flooded by a server stop is put right when someone next walks in.
* **A sidebar.** `/roc sidebar` (or the switch in the Codex) turns on a panel at the side of the screen: the tower
  and floor, a trial's phase and health, pylons standing, the ring of floor that is left, the hub's tier, the
  compass's signal, today's bounties and your party. Each player has their own; it never touches the world's
  scoreboard.
* **The compass scans. Nothing warps.** The Compass of Chaos no longer carries anyone to the hub, and neither do
  the Codex or `/roc hub` from the world outside.
  * Right-click begins a **scan** of 60 to 90 seconds, read above the hotbar while the compass is in hand: an
    arrow (up is straight ahead), a distance, five bars of signal and the time left. Every two or three seconds the
    needle rolls and picks a target - the nearer and weightier, the likelier.
  * Most rolls are rough, off by as much as the signal is poor. Now and then one is a **Clean Lock** (exact, with
    a trail of light) or a **Static Surge** (nonsense). The longer a scan runs the truer it reads, and its best
    reading is written to chat when it ends.
  * Sneak and right-click turns it to what it looks for: the way to the hub, the keystone shrines, or relays and
    waygates.
  * **Signal:** a bare compass reads 0.35. Each **Relay Tower Core** within 96 blocks adds 0.15 (up to three) and
    each hub tier 0.05; rain costs 0.1, thunder 0.2, being deep underground 0.15, and another player's **Signal
    Jammer** within 64 blocks costs 0.5 (not its owner's or their party's).
* **A field network.** New players get a **Field Tower Bag** holding two Relay Tower Cores and a **Waygate
  Obelisk**. All three blocks can be crafted, set down, broken and carried, and where they stand is saved with the
  world.
  * The bag keeps its contents in itself, so they survive wherever the bag goes. Sneak and right-click a block to
    set down the first network block inside without opening it. A bag cannot go inside a bag.
  * A waygate sends you to any other obelisk you have touched, or - once a Ruined Gate knows you - to the Hub of
    Realms. Choose on its screen, then stand still for **five seconds**: move or be hit and it lets go. A journey
    costs one ender pearl, taken on arrival.
* **The hub grows.** The **Heart of the Hub** opens Hub Command. Everyone on the world shares one hub and anyone
  can hand over what the next of its **five tiers** asks for.
  * II: steadier compasses, a hub that mends whoever stands in it, and the **Bounty Board**. III: six-row Field
    Bags, and the hub lifts every harm. IV: three-second waygates and a needle that rolls every two seconds. V: free
    waygates.
  * The Bounty Board posts three bounties a day, the same for everyone - sentinels felled, seals broken, trials
    won, pylons shattered, caches opened. Each player earns them for themselves and is paid in forge materials.
* **Custom screens, not just chests.** The Codex, the Climber's Handbook, the Oracle's Chronicle, the Trial Altar
  and the party are real screens - a title, text, pictures of items and buttons - drawn as pages of the mod's
  own on both Fabric and Forge (the game's stock dialog is only used with the drawn interface switched off). Chapters of the story are read on screen, page by page. The
  screens with moving parts (the forge's strike bar, the passage map, a tower's floor plan) are still chest menus.
* **A tutorial.** A new player is shown the **Climber's Handbook** a few seconds after they first join: nineteen
  topics, from getting in to reading a fight, always there in the Codex. After that, a **tip** appears the first
  time each thing happens - the first Ruined Gate, the first altar, the first seal, the first eruption - once each.
* **Climb together.** Up to four players can form a **party** (Codex -> Party, or `/roc party invite <player>`,
  `/roc party accept`, `/roc party leave`).
  * A party enters a tower on the floor of its **highest member**, and anyone in it may begin that floor's trial.
  * Progress is always each player's own. Everyone on the floor when a god falls is credited, but nobody goes up
    past a floor until they have beaten its god themselves - with help or without - and walked its passage. A
    friend who got ahead while you were away can come back down and help you through yours.
  * Floors you were carried past are still yours to do: alone, you enter on your own floor, and can also return to
    any floor you earned with the party.
  * **Everyone gets their own loot.** Each victor's drops can only be picked up by them: the floor's forge material
    every time, and the god's relic (and its cards) the first time *they* beat it. Passage treasure is a **cache**
    that gives every player their own share, once.
* **The tower adapts.** A trial faced by a group is harder: +60% health and +15% damage for each challenger after
  the first (up to four), and each sealed chamber wakes an extra sentinel per extra player (up to two). A god that
  keeps beating you relents: 8% less health for each defeat, up to three, wiped by a victory. A passage that others
  broke open **seals itself again** for a player who has not walked it yet.
* **The walls hold.** Inside the hub, the towers and the passages, survival players are in adventure mode - they
  can fight, open and use everything, but not break or place blocks - and get survival back the moment they leave.
  Creative players are not restricted.
* **Sentinels: the passages' own enemies.** Eighteen kinds, six to a tower, each with its own look:

  | | Soldier | Caster | Skirmisher | Brute | Mystic | Keeper |
  | --- | --- | --- | --- | --- | --- | --- |
  | Zeus | Storm Hoplite | Sky Archer | Harpy | Thunder Brute | Storm Augur | Keeper of the Sky |
  | Poseidon | Tide Hoplite | Siren | Telchine | Reef Brute | Tide Priest | Keeper of the Tides |
  | Hades | Shade | Lampad | Fury | Tartarus Brute | Necromancer | Keeper of the Shades |

  Soldiers close in (the Shade steps behind you). Casters stand off and shoot. Skirmishers are quick and leap at
  you (a Fury's blow sets you alight). Brutes hit slowly and throw you back. **Mystics** help the rest: the Storm
  Augur quickens and emboldens them, the Tide Priest heals them, the Necromancer raises Shades. A **Keeper** holds
  every seal and uses a small version of one of its tower's moves. **Elites** glow, take half as much again to
  bring down and always drop forge material.
  How strong a sentinel is follows its place: every passage further up the tower, and every level further into a
  passage, makes them tougher and brings more kinds - skirmishers from the second passage, brutes from the middle
  of the tower, mystics near the top.
* **Fights that move.** Every heavy move now **breaks the ground**: chunks of whatever the floor is made of are
  thrown into the air (they are for show, and vanish when they land). Once a god is enraged **the floor erupts**
  under its challengers every few seconds - dust and a rumble for a second, then anyone still standing there is
  thrown into the air. At a fifth of its health a god makes a **last stand**: for two seconds nothing can touch it,
  two of its tower's sentinels answer its call, and then its signature move and its line's ability follow one
  after the other.
* **A story.** Chaos, the oldest thing there is, has woken and is unmaking the world one story at a time. The
  gods of the sky, the sea and the dead shut themselves in three towers to hold it off - and have forgotten why.
  You do not slay them: every trial wakes a god, and a relic is its thanks. The story is told by the **Oracle** in
  nineteen chapters, each a real book: her letter (given on first join), books found in the ruins, a chapter for
  each stretch of each tower (after 5, 10 and 15 of its gods are awake - the last in the king's own words) and an
  epilogue. **The Oracle's Chronicle** (her flame in the hub, or the Codex) lists them, hands out the ones you
  have earned, and always says what to do next.
* **The way in has to be found.** The gates were broken on purpose. Until a gate knows you, the realms are closed
  and the **Compass of Chaos** scans for the nearest **Ruined Gate** in the Overworld.
  * A Ruined Gate is a ruined temple round a dead arch, with a campfire still smoking. Its chest holds the story so
    far and three **seekers** - compasses that each point to the nearest shrine of one keystone.
  * The three **keystones** lie in three kinds of shrine elsewhere in the world: the **Shrine of the Sky** (a
    white temple), the **Shrine of the Tides** (a ring of prismarine round a pool) and the **Shrine of the Shades**
    (standing stones round a crypt you walk down into).
  * Right-click the gate's **pedestal** with each keystone to set it in the arch. Three keystones wake the gate
    (for everyone); stepping through it **attunes** you: from then on every gate lets you through and a Waygate
    Obelisk will send you to the hub.
  * Ruins are placed from the world seed - there is always a Ruined Gate within about 800 blocks and each kind of
    shrine within about 700 - and are raised on the surface when a player first comes near. Players who had
    already woken a god before this update, and players in creative mode, are let in without a gate.
* **A livelier hub.** The Oracle's Flame burns at its heart. Beside each tower gate stand two **echoes** of its
  gods - the first you will meet and the king at the top - which hold their relics and turn their heads to watch
  you. A canal rings the plaza, bridged by the four paths; cherry trees stand in four gardens; pilgrims wander,
  cats sit about and allays drift overhead; fireflies rise over the gardens.
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

* **Passages between the floors.** The Gate of Ascent no longer takes you straight up: the first time, it leads
  into a **passage** (14 per tower, 42 in all). A passage is not one level but several, stacked one above the
  other - **two levels** in the lower third of a tower, **three** above - and each level is a maze of rooms and
  corridors with a gate at its far end that stays shut until every **seal** on that level is broken (two seals
  low in the tower, three in the middle, four near the summit). A level's gate leads up to the next level; the
  last one leads to the next floor. Each level is harder than the one below, and each passage harder than the last.
  * **Procedural.** Each passage is laid out from the world seed - which rooms connect, where the way in, the gate
    and the seals are, what every room is - so every world's towers are different, and every level of every
    passage is different. Mazes grow with the tower: 4 x 4 rooms on the lower floors, 5 x 5 above, and 6 x 6 for
    the last level of the highest passages. A tower has 879 rooms in all.
  * **Rooms.** Every proper room has a shallow dome, a band of trim and sconces in its walls. There are sealed
    chambers, **arenas** (on the levels above the first: an elite and its pack guard a richer cache), **gardens**,
    **libraries** (with a working enchanting table), **pools**, treasure rooms (a cache with a share for every player: this tier's forge material,
    experience bottles, food and metal; in Hades' tower sometimes a tarot card), one **shrine** (right-click: full health, harmful effects
    cleared and three minutes of the tower's favour, once every ten minutes per player), trapped rooms (scorching
    tiles and webs), plain rooms and corridors.
  * **Seals and guardians.** Walking into a sealed chamber wakes its sentinels: a Keeper, one more for each tier of
    the tower and one more again for each level into the passage (more for a group), with an elite among them on a
    passage's last level. A breaking seal goes with a shockwave that throws up the floor and staggers anyone close. While any stand the seal is warded. Then right-click the seal and **strike it apart** - a
    quick-time bar: click when the hammer is on the mark. A perfect strike cracks it twice, a good one once, a miss
    stings.
  * **Finding your way.** The Codex opens the **passage map** while you are in one: one slot per room, filled in as
    you explore, with unexplored rooms hinted where a door leads on. The Compass of Chaos points to the nearest
    unbroken seal, then to the gate (sneak + right-click still travels to the hub). A bar at the top counts seals.
  * A passage you have walked is remembered: next time the Gate of Ascent takes you straight up. Players inside
    together share its seals; a player who arrives later, not having walked it, finds it sealed again.
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
* **Signature moves.** Every god has a move of its own, named on screen as it winds up (*"Zeus readies Wrath of
  Olympus!"*), and alternates it with its line's ability. There are fourteen kinds of move - storm, charge, volley,
  vortex, shockwave, blink, mend, slumber, reap, hex, frost, gale, ward and eruption - and each also carries the
  god's own hit power and curse. Eruptions and hexes land a moment late, so they can be stepped out of.
* **Livelier gods.** Melee gods lunge at a target that keeps its distance; ranged gods hop away when crowded (and
  back towards the middle if cornered). At half health a god is **enraged**: it glows, roars everyone back a step,
  moves faster and casts sooner.
* **Rematches.** A god you have beaten can be called back from its altar for another fight. A rematch drops the
  floor's forge material again - but no second relic, cards or deck.
* **The relic casts its god's move.** Right-click with a melee relic (sneak + right-click with a ranged one) casts
  the same signature move, on a 12-second recharge. Forging matters to it: **three stars empower** the move (+50%
  damage) and **five stars awaken** the relic so the move echoes a second time. Every fourth hit in a row with any
  relic is a **flourish** (+50%). Moves never hurt other players or your own pets.
* **Weapon levelling and forging.** A relic gains experience from every kill made with it (level 1-10, +8% damage a
  level). At a **Forge of Chaos** (two stand in the hub; also `/roc forge` and the Codex) you forge up to five stars
  onto it (+10% damage and faster recharge each; star N needs weapon level 2N). The forge screen shows the star
  track, what the next star changes in numbers, the signature move and its perks, where each material drops, and
  an **Infuse** button that pours your own experience levels into the relic (30 weapon XP a level).
* **Forging is struck, not clicked.** Pressing *Forge* pays the price and starts a quick-time round of three
  strikes: a hammer runs back and forth along the bottom row, and each click is judged by where it is - on the
  mark is perfect, beside it good, elsewhere a miss. The star is forged whatever happens; the strikes decide the
  finish. **Masterwork** (no misses, two perfect) *tempers* the star for +2% damage and gives 60 weapon XP; fine
  work (at most one miss) gives 30; rough work gives nothing extra. The later stars are struck faster. A star that
  was not tempered can be struck again with the **Temper** button (2 of the tower's first material; only a
  masterwork takes). Closing the screen mid-round ends it as it stands - nothing paid is lost. Stars are paid for with what the
  gods of the weapon's own tower drop - every tier of a tower has its own material, so the higher stars send you
  higher up:

  | Tier (floors) | Zeus | Poseidon | Hades | Drop per god |
  | --- | --- | --- | --- | --- |
  | 1 (1-5) | Laurel Leaf | Sea Glass | Asphodel Petal | 2 |
  | 2 (6-10) | Marble Shard | Reef Scale | Tartarus Cinder | 2 |
  | 3 (11-14) | Cloud Silk | Trench Ink | Obol of the Dead | 3 |
  | 4 (summit) | Spark of Olympus | Pearl of the Deep | Ember of the Underworld | 2 |

  Star 1 costs 3 of tier 1; star 2: 3 of tier 1 + 2 of tier 2; star 3: 4 of tier 2 + 1 of tier 3; star 4: 4 of
  tier 3; star 5: 3 of tier 3 + 1 of the summit essence. One climb of a tower drops enough to fully forge one
  weapon, and a fully forged relic shimmers.
* **Tarot.** The gods of Hades' tower drop the 22 Major Arcana (three copies of each of their cards). A card is
  played with right-click and spent. Hades drops Death and the **Deck of Fate**: a stack that never runs out -
  right-click draws a random card, sneak + right-click lets you choose one (the deck then rests longer).

  | Card | What it does |
  | --- | --- |
  | 0 The Fool | Iron armour and an iron sword, and a journey to the nearest village or mineshaft |
  | I The Magician | Strength II-III, and 20 logs |
  | II The High Priestess | A bubble for 10 seconds: no harm gets in and monsters are pushed out |
  | III The Empress | Grows the plants around you; Luck |
  | IV The Emperor | No knockback and +8 armour for 45 seconds |
  | V The Hierophant | Hero of the Village |
  | VI The Lovers | Regeneration for you, and buffs for every player near you |
  | VII The Chariot | Speed III, and you step up full blocks |
  | VIII Strength | Strength and Resistance |
  | IX The Hermit | Takes you to your own private island in a pocket dimension; play it again (or use the gate) to return |
  | X Wheel of Fortune | Luck at its maximum |
  | XI Justice | Everything around you glows through walls |
  | XII The Hanged Man | Monsters stop targeting you for 30 seconds (gods are not fooled) |
  | XIII Death | Hands you a Totem of Undying |
  | XIV Temperance | Full health, hunger and saturation |
  | XV The Devil | Burns your inventory; for 30 seconds everything you pick up becomes the item in your off hand |
  | XVI The Tower | Carries you to another god tower: the one whose relics you hold fewest of |
  | XVII The Star | Experience and a little Luck |
  | XVIII The Moon | Summons five illusions that cannot hurt you; slay them all for a reward |
  | XIX The Sun | Burns nearby foes; Regeneration |
  | XX Judgement | Full health and a moment of Resistance |
  | XXI The World | Speed, Strength, Regeneration and Resistance |

  The Tower reads item tags: every relic weapon is tagged with the tower it comes from
  (`realms_of_chaos:relics/zeus`, `.../poseidon`, `.../hades`; `realms_of_chaos:relics` holds them all). The card
  counts the relics you carry by tag, never picks the tower you are standing in, and of the rest sends you to the
  one you hold fewest relics of (ties fall at random). A new mythology only needs a tag of its own to join in.

  The Devil spares worn armour, the Deck of Fate and the chosen item, refuses to be played with an empty off hand,
  and is never dealt by a blind draw from the Deck - you have to choose it.
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

Signature moves, floor by floor:

| Floor | Zeus line | Poseidon line | Hades line |
|---|---|---|---|
| 15 | Zeus: *Wrath of Olympus* | Poseidon: *Earthshaker* | Hades: *Gates of Tartarus* |
| 14 | Athena: *Aegis* | Amphitrite: *Queen's Tide* | Persephone: *Winter's Harvest* |
| 13 | Apollo: *Sunburst Volley* | Triton: *Call of the Deep* | Zagreus: *Stygian Dash* |
| 12 | Dionysus: *Bacchic Revel* | Chrysaor: *Golden Rush* | Thanatos: *Final Harvest* |
| 11 | Artemis: *Hunter's Moon* | Cymopoleia: *Stormwave* | Hypnos: *Lullaby* |
| 10 | Ares: *Warpath* | Rhode: *Rain of Roses* | Melinoe: *Ghostlight* |
| 9 | Hermes: *Windstep* | Pegasus: *Skyward Gale* | Macaria: *Blessed End* |
| 8 | Asclepius: *Panacea* | Benthesikyme: *Undertow* | Hecate: *Crossroads Hex* |
| 7 | Hebe: *Cup of Youth* | Halia: *Seafoam Chill* | Morpheus: *Waking Dream* |
| 6 | Phobos: *Terror Strike* | Nereus: *Old Man of the Sea* | Nyx: *Starfall* |
| 5 | Deimos: *Dread Harvest* | Proteus: *Shapeshift* | The Erinyes: *Vengeance* |
| 4 | Hygeia: *Cleansing Ward* | Palaemon: *Anchor Drop* | The Moirai: *Severed Thread* |
| 3 | Pan: *Panic* | Thetis: *Silver Rain* | The Keres: *Carrion Feast* |
| 2 | Nike: *Victory Rush* | Galene: *Stillwater* | The Oneiroi: *Nightmare* |
| 1 | Iris: *Prism Gust* | Galatea: *Pearl Frost* | Charon: *Ferryman's Toll* |

* **In-game UI instead of chat** - nothing the mod tells you goes to chat:
  * **Codex of Chaos** (item or `/roc`), a double-chest screen: the three towers side by side with climb meters,
    and a page per tower that draws it floor by floor with tier labels. Click any floor you have reached to travel
    there; travel to the hub or leave the realms from the main page.
  * **Trial Altar menu**: right-click a floor's altar to see its god and begin the trial, or ascend once it is won;
    a strip along the bottom shows the floors around you.
  * **Tower bar**: a bar at the top of the screen shows the tower, floor and tier you are on.
  * **Titles and action bar** for arrivals, trials, victories and short notices.
  * **Advancements tab** with one advancement per god.
* **First-join welcome** - a thank-you title, plus the Codex, the Compass of Chaos and a Field Tower Bag.

## How to play

1. Join a world. You are given the Codex, the Compass of Chaos, a Field Tower Bag and the Oracle's letter. Read
   the letter, then right-click the **Compass of Chaos** and follow its scan to the nearest **Ruined Gate**. Take
   the three seekers from its chest, follow each to its shrine for a keystone, set the keystones at the pedestal
   and step through the gate. (To come back later, walk through the gate again or set down a Waygate Obelisk near
   home and channel at it.)
2. In the hub, walk into a tower gate. You arrive on the floor you are working on.
3. Right-click the floor's **Trial Altar** and press **Begin the trial**. (Trials need a difficulty above Peaceful.)
4. Defeat the god - break its shield pylons when it raises its aegis at 65%, and stay off the lost edge of the
   floor and out of the marked rings in its wrath at 30%. It drops its **relic weapon** (scaled by rank, with a hit power of its own and its god's
   signature move) and its tier's forge material, and the **Gate of Ascent** at the far side of the floor opens for you.
5. Step through the gate into the **passage**: explore it, break its seals, and take the gate at its far end up to
   the next floor.
6. Climb. The three towers are independent, and progress is saved per player.
7. To go home, take the south gate in the hub, the Codex's *Leave the Realms* button, or `/roc leave`.

Each line fights differently: **Zeus** calls lightning, **Poseidon** drags you in and crashes tidal waves over you,
**Hades** drains your life and (for the top gods) raises wither skeletons. Bosses raise their arms and gather
particles for about a second before a big attack.

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
* `tools/make_font.py` - builds `site/ChaosBlocks.ttf`, the square block lettering of the site's titles (an original
  typeface; needs `pip install fonttools`)
* `art/atlases/` - every texture the mod ships, gathered into atlases (items, animated item frames, blocks, skins,
  portraits, weapon designs), each also at 4x, with an `index.json` naming every tile
* `art/weapon-designs/` - a sheet of every weapon silhouette, including designs for mythologies that are not in the
  mod yet. Japanese, planned next: a curved katana, wakizashi, tanto, nodachi, naginata, yari, kusarigama, sai,
  kanabo, nunchaku, yumi, kunai, shuriken and tessen. Others: battleaxe, Mjolnir, khopesh, claymore, halberd, mace,
  crossbow. Their
  textures already ship under `textures/item/design/`.

### Self-test

The mod contains a developer self-test that only runs when the JVM has `-Droc.selftest=true`. It exercises the mod
on a real server: data loading, the hub, building and walking every floor of every tower, a full climb of all three
towers through the altar menu (with rematches) and all 42 passages, the Ruined Gate and its keystones, the story's
chapters, the hub's residents, all seventy-five sentinels and their traits, craters, the custom screens and their buttons, the tutorial, impacts, the
floor's eruptions and the gods' last stands, the three phases of a trial (health gates, pylons, stances, the collapsing floor and its restoration, strikes), gear and difficulty scaling,
the compass scanner, relays and jammers, waygates, the Field Tower Bag, hub tiers and bounties, the rune chambers, the sidebar, level counts and names, building ahead, the four puzzle rooms (beam rooms laid out at random, with decoys and a limit of turns), no two rooms of a kind joined, cells and storerooms, afflictions, cards and lines not said twice, the written screens as pages (packing, pictures, buttons pressed), onslaughts, the three rune chambers, the panoply, commissions, the lesser gods, the thirty-eight trap halls (each proved crossable, each hazard, a chasing hall, a small hall), winding corridors, crusher and dart halls, urns and tablets, the Passage Chart, the mastery tree, the menu artwork, cards and tips, the drawn boss bar and the opt-in cast bar, settings, the gods' memory of each player, room shapes and stairwells, the spike, flame and blade halls, parries, ripostes, momentum and plunges, heavy blows, boons, the gate's facing, the shrine's chart, the bestiary, the frames of each phase, shut gates, corridors and their jets, the arc and gas halls, the chart's memory, plain-word fallbacks, every menu's words against the language file, parties, group scaling and per-player loot, the wall protection (built, checked room by room for reachability,
seals struck, guardians, shrine, map), every god's signature move, the relic moves, the forging quick-time rounds,
tier materials, each tarot card, the Codex, leaving the realms, the welcome and the anti-stuck safeguards.

```bash
cd forge && ./gradlew runGameTestServer && cat run/roc-selftest.txt
```

License: MIT. Greek mythology belongs to everyone.
