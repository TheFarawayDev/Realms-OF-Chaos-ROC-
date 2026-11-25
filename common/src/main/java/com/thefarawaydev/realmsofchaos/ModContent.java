package com.thefarawaydev.realmsofchaos;

import com.thefarawaydev.realmsofchaos.block.AltarBlock;
import com.thefarawaydev.realmsofchaos.block.ForgeBlock;
import com.thefarawaydev.realmsofchaos.block.GateBlock;
import com.thefarawaydev.realmsofchaos.entity.RelicProjectile;
import com.thefarawaydev.realmsofchaos.god.Power;
import com.thefarawaydev.realmsofchaos.item.Tarot;
import com.thefarawaydev.realmsofchaos.item.TarotCardItem;
import com.thefarawaydev.realmsofchaos.item.TarotDeckItem;
import com.thefarawaydev.realmsofchaos.item.VoodooDollItem;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.item.ChaosCompassItem;
import com.thefarawaydev.realmsofchaos.item.CodexItem;
import com.thefarawaydev.realmsofchaos.item.GodWeaponItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Loader independent definitions of everything Realms of Chaos registers. Each loader (Fabric / Forge) walks the
 * {@code *_DEFS} lists, registers them, and binds the matching {@link Ref} so common code can look them up.
 */
public final class ModContent {
    private ModContent() {}

    /** A late-bound reference to a registered object. */
    public static final class Ref<T> implements Supplier<T> {
        private Supplier<T> supplier;

        public void bind(Supplier<T> supplier) { this.supplier = supplier; }

        @Override
        public T get() {
            if (supplier == null) throw new IllegalStateException("Not registered yet");
            return supplier.get();
        }
    }

    public record Def<T>(String id, Function<ResourceKey<T>, T> factory, Ref<T> ref) {}

    // ---- blocks -------------------------------------------------------------------------------
    public static final Ref<Block> GATE_HUB = new Ref<>();
    public static final Ref<Block> GATE_ZEUS = new Ref<>();
    public static final Ref<Block> GATE_POSEIDON = new Ref<>();
    public static final Ref<Block> GATE_HADES = new Ref<>();
    public static final Ref<Block> GATE_ASCEND = new Ref<>();
    public static final Ref<Block> GATE_LEAVE = new Ref<>();
    public static final Ref<Block> ALTAR_ZEUS = new Ref<>();
    public static final Ref<Block> ALTAR_POSEIDON = new Ref<>();
    public static final Ref<Block> ALTAR_HADES = new Ref<>();
    public static final Ref<Block> CHAOS_FORGE = new Ref<>();
    public static final Ref<Block> PASSAGE_SEAL = new Ref<>();
    public static final Ref<Block> PASSAGE_SHRINE = new Ref<>();
    public static final Ref<Block> GATE_PEDESTAL = new Ref<>();
    public static final Ref<Block> ORACLE = new Ref<>();
    public static final Ref<Block> PASSAGE_CACHE = new Ref<>();
    public static final Ref<Block> SHIELD_PYLON = new Ref<>();
    public static final Ref<Block> RELAY_CORE = new Ref<>();
    public static final Ref<Block> SIGNAL_JAMMER = new Ref<>();
    public static final Ref<Block> WAYGATE_OBELISK = new Ref<>();
    public static final Ref<Block> HUB_HEART = new Ref<>();
    public static final Ref<Block> BOUNTY_BOARD = new Ref<>();
    public static final Ref<Block> RUNE_STONE = new Ref<>();
    public static final Ref<Block> GLYPH_TILE = new Ref<>();
    public static final Ref<Block> CHIME_CRYSTAL = new Ref<>();
    public static final Ref<Block> MEMORY_PLATE = new Ref<>();
    public static final Ref<Block> BEAM_MIRROR = new Ref<>();
    public static final Ref<Block> BEAM_EMITTER = new Ref<>();
    public static final Ref<Block> BEAM_RECEIVER = new Ref<>();
    public static final Ref<Block> DART_TILE = new Ref<>();
    public static final Ref<Block> ANCIENT_URN = new Ref<>();
    public static final Ref<Block> LORE_TABLET = new Ref<>();
    public static final Ref<Block> OLYMPIAN_MARBLE = new Ref<>(), OLYMPIAN_MARBLE_PILLAR = new Ref<>(), GILDED_FRIEZE = new Ref<>(),
            ABYSSAL_TILE = new Ref<>(), UNDERWORLD_BRICK = new Ref<>(), CHAOS_LAMP = new Ref<>(),
            OLYMPIAN_TILES = new Ref<>(), CHAOS_MOSAIC = new Ref<>(), OLYMPIAN_PANEL = new Ref<>(), ABYSSAL_PANEL = new Ref<>(), UNDERWORLD_PANEL = new Ref<>();
    public static final Ref<Block> SPIKE_PLATE = new Ref<>(), SPIKE_PLATE_DARK = new Ref<>(), TRAP_SPIKES = new Ref<>(), FLAME_VENT = new Ref<>(), GAS_VENT = new Ref<>(),
            SKY_RELIEF = new Ref<>(), SEA_RELIEF = new Ref<>(), SHADE_RELIEF = new Ref<>();
    /** Each tower's own stone: a connecting column, a connecting floor panel, a brick. */
    public static final Ref<Block> MARBLE_COLUMN = new Ref<>(), SUNSTONE_PANEL = new Ref<>(), STORM_BRICK = new Ref<>(),
            CORAL_COLUMN = new Ref<>(), PEARL_PANEL = new Ref<>(), TIDE_BRICK = new Ref<>(),
            BONE_COLUMN = new Ref<>(), EMBER_PANEL = new Ref<>(), ASH_BRICK = new Ref<>();
    /** The furnishings of the new rooms. */
    public static final Ref<Block> SKY_MOSAIC = new Ref<>(), SEA_MOSAIC = new Ref<>(), SHADE_MOSAIC = new Ref<>(), STAR_VAULT = new Ref<>(),
            CELESTIAL_GLOBE = new Ref<>(), BRONZE_GRATE = new Ref<>(), WEAPON_RACK = new Ref<>(), SCROLL_SHELF = new Ref<>(), BATH_TILE = new Ref<>(),
            LAUREL_HEDGE = new Ref<>(), KELP_WEAVE = new Ref<>(), ASPHODEL_BED = new Ref<>();
    /** The upper floors' stone, the portal frames, and the sigil travellers arrive on. */
    public static final Ref<Block> CLOUDSTONE = new Ref<>(), CLOUD_TILE = new Ref<>(), TRENCH_STONE = new Ref<>(), NACRE_TILE = new Ref<>(),
            TARTARUS_STONE = new Ref<>(), THRONE_TILE = new Ref<>(), PORTAL_FRAME_SKY = new Ref<>(), PORTAL_FRAME_SEA = new Ref<>(),
            PORTAL_FRAME_SHADE = new Ref<>(), PORTAL_FRAME_HUB = new Ref<>(), ARRIVAL_SIGIL = new Ref<>();
    public static final Ref<Block> TOWER_BRAZIER = new Ref<>(), SPRINKLER = new Ref<>();
    /** Sliding doors: bronze, and one in each tower's stone. */
    public static final Ref<Block> BRONZE_SLIDING_DOOR = new Ref<>(), SKY_SLIDING_DOOR = new Ref<>(), SEA_SLIDING_DOOR = new Ref<>(), SHADE_SLIDING_DOOR = new Ref<>();
    /** The dial hall's and the sliding stones' blocks. */
    public static final Ref<Block> DIAL_STONE = new Ref<>(), FRESCO_TILE = new Ref<>(), SLIDE_STONE = new Ref<>(), STONE_SOCKET = new Ref<>();
    /** Sparring dummies, the barracks' furniture, three more doors, and the mod's glass. */
    public static final Ref<Block> MOVING_DUMMY = new Ref<>(), CHAIR = new Ref<>(), TABLE = new Ref<>(), BENCH = new Ref<>(), DRESSER = new Ref<>(),
            BUNK_BED = new Ref<>(), GARRISON_DOOR = new Ref<>(), GARRISON_TRAPDOOR = new Ref<>(), VERDIGRIS_DOOR = new Ref<>(), VERDIGRIS_TRAPDOOR = new Ref<>(),
            IVORY_DOOR = new Ref<>(), IVORY_TRAPDOOR = new Ref<>(), REINFORCED_GLASS = new Ref<>(), FROSTED_GLASS = new Ref<>(), SMART_GLASS = new Ref<>(),
            VEIL_GLASS = new Ref<>(), ONE_WAY_GLASS = new Ref<>();
    /** The fittings of the proving grounds and barracks. */
    public static final Ref<Block> ARENA_SAND = new Ref<>(), WAR_DRUM = new Ref<>(), WAR_STANDARD = new Ref<>(), BEAST_GATE = new Ref<>(),
            TRAINING_DUMMY = new Ref<>();

    public static final List<Def<Block>> BLOCK_DEFS = new ArrayList<>();

    // ---- items --------------------------------------------------------------------------------
    public static final Ref<Item> CHAOS_COMPASS = new Ref<>();
    public static final Ref<Item> CHAOS_CODEX = new Ref<>();
    public static final Ref<Item> FIELD_BAG = new Ref<>(), LOCKPICK = new Ref<>();
    /** Adamantine: a rare ore from the deepest stone, worked into a metal past netherite, and the tools made of it. */
    public static final Ref<Item> RAW_ADAMANTINE = new Ref<>(), ADAMANTINE_SCRAP = new Ref<>(), ADAMANTINE_INGOT = new Ref<>(),
            ADAMANTINE_SWORD = new Ref<>(), ADAMANTINE_PICKAXE = new Ref<>(), ADAMANTINE_AXE = new Ref<>(), ADAMANTINE_SHOVEL = new Ref<>(), ADAMANTINE_HOE = new Ref<>();
    public static final net.minecraft.tags.TagKey<Item> ADAMANTINE_TOOL_MATERIALS = net.minecraft.tags.TagKey.create(Registries.ITEM, RealmsOfChaos.id("adamantine_tool_materials"));
    /** Past netherite in every way: it lasts longer, digs faster, strikes harder and takes enchantment better. */
    public static final net.minecraft.world.item.ToolMaterial ADAMANTINE = new net.minecraft.world.item.ToolMaterial(
            net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2731, 11.0F, 5.0F, 18, ADAMANTINE_TOOL_MATERIALS);
    /** Porphyry, the purple stone of emperors, and what is cut from it; adamantine ore and its block. */
    public static final Ref<Block> PORPHYRY = new Ref<>(), POLISHED_PORPHYRY = new Ref<>(), PORPHYRY_BRICKS = new Ref<>(), CHISELED_PORPHYRY = new Ref<>(),
            PORPHYRY_TILES = new Ref<>(), PORPHYRY_PILLAR = new Ref<>(), PORPHYRY_DOOR = new Ref<>(), PORPHYRY_TRAPDOOR = new Ref<>(),
            DEEPSLATE_ADAMANTINE_ORE = new Ref<>(), ADAMANTINE_BLOCK = new Ref<>();
    /** What is won from the world itself: fulgurite where lightning strikes sand, clams that grow pearls, iron from the soul valleys; and the Cornucopia. */
    public static final Ref<Block> FULGURITE = new Ref<>(), GIANT_CLAM = new Ref<>(), STYGIAN_IRON_ORE = new Ref<>(), CORNUCOPIA = new Ref<>();
    /** The resources, the god-metals forged from them, and the cores that the relics and the Cornucopia are made with. */
    public static final Ref<Item> STORM_GLASS = new Ref<>(), ABYSSAL_PEARL = new Ref<>(), RAW_STYGIAN_IRON = new Ref<>(),
            CELESTIAL_BRONZE_INGOT = new Ref<>(), ORICHALCUM_INGOT = new Ref<>(), STYGIAN_IRON_INGOT = new Ref<>(),
            THUNDER_CORE = new Ref<>(), TIDE_CORE = new Ref<>(), SHADE_CORE = new Ref<>();
    /** The four armour sets, by set and then by piece. */
    public static final Map<com.thefarawaydev.realmsofchaos.item.ArmorSets.Set, Map<net.minecraft.world.item.equipment.ArmorType, Ref<Item>>> ARMOR = new java.util.EnumMap<>(com.thefarawaydev.realmsofchaos.item.ArmorSets.Set.class);
    public static final Map<String, Ref<Item>> WEAPONS = new HashMap<>();
    /** The eight pieces of the panoply, by id. */
    public static final Map<String, Ref<Item>> PANOPLY = new java.util.LinkedHashMap<>();
    public static final Map<String, Ref<Item>> BLOCK_ITEMS = new HashMap<>();
    /** The stairs and slabs cut from the mod's stone, by the stone's id. */
    public static final Map<String, Ref<Block>> STAIRS = new java.util.LinkedHashMap<>(), SLABS = new java.util.LinkedHashMap<>();
    public static final Ref<Block> BRONZE_DOOR = new Ref<>(), BRONZE_TRAPDOOR = new Ref<>(), SKY_DOOR = new Ref<>(), SKY_TRAPDOOR = new Ref<>(),
            SEA_DOOR = new Ref<>(), SEA_TRAPDOOR = new Ref<>(), SHADE_DOOR = new Ref<>(), SHADE_TRAPDOOR = new Ref<>();
    public static final Ref<Item> TAROT_DECK = new Ref<>();
    public static final Map<Tarot, Ref<Item>> TAROT = new HashMap<>();
    public static final Map<VoodooDollItem.Pin, Ref<Item>> DOLLS = new HashMap<>();
    public static final Map<Line, Ref<Item>> ESSENCES = new HashMap<>();
    public static final Map<Line, Ref<Item>> KEYSTONES = new HashMap<>();
    public static final Map<Line, Ref<Item>> SEEKERS = new HashMap<>();
    /** Forge materials dropped on tiers 1-3 of each tower, keyed "<line id>/<tier>". */
    public static final Map<String, Ref<Item>> MATERIALS = new HashMap<>();
    /** The mod's own particle types, by id (see {@link com.thefarawaydev.realmsofchaos.fx.RocParticles}). */
    public static final Map<String, Ref<net.minecraft.core.particles.SimpleParticleType>> PARTICLES = new java.util.LinkedHashMap<>();
    static {
        for (com.thefarawaydev.realmsofchaos.fx.RocParticles p : com.thefarawaydev.realmsofchaos.fx.RocParticles.values()) PARTICLES.put(p.id(), new Ref<>());
    }
    /** Not obtainable: the sprites staff bolts and god projectiles are drawn with, one per power. */
    public static final Map<Power, Ref<Item>> ORBS = new HashMap<>();

    public static final List<Def<Item>> ITEM_DEFS = new ArrayList<>();

    // ---- entity / tab -------------------------------------------------------------------------
    /**
     * A menu type for each of the mod's menus, by id ("panel_codex", ...). They are chest menus underneath - the
     * server owns every slot - but registered as types of their own, so the game opens the mod's own screen for
     * each instead of a chest's.
     */
    public static final Map<String, Ref<net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.ChestMenu>>> MENUS = new java.util.LinkedHashMap<>();

    static {
        for (com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel : com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values()) MENUS.put(panel.id(), new Ref<>());
    }

    /** Makes the menu type for one panel. On the player's side it makes an empty chest of the right size for the server to fill. */
    public static net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.ChestMenu> createMenuType(com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel) {
        return new net.minecraft.world.inventory.MenuType<>((id, inventory) -> new net.minecraft.world.inventory.ChestMenu(MENUS.get(panel.id()).get(), id, inventory,
                new net.minecraft.world.SimpleContainer(panel.rows * 9), panel.rows), net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    }

    /** The menu type of a page: the Codex, the handbook and the other written screens. */
    public static final Ref<net.minecraft.world.inventory.MenuType<com.thefarawaydev.realmsofchaos.ui.PageMenu>> PAGE = new Ref<>();

    public static net.minecraft.world.inventory.MenuType<com.thefarawaydev.realmsofchaos.ui.PageMenu> createPageType() {
        return new net.minecraft.world.inventory.MenuType<>(com.thefarawaydev.realmsofchaos.ui.PageMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
    }

    public static final Ref<EntityType<GodBoss>> GOD_BOSS = new Ref<>();
    public static final Ref<EntityType<com.thefarawaydev.realmsofchaos.entity.Titan>> TITAN = new Ref<>();

    public static EntityType<com.thefarawaydev.realmsofchaos.entity.Titan> createTitanType(ResourceKey<EntityType<?>> key) {
        return EntityType.Builder.<com.thefarawaydev.realmsofchaos.entity.Titan>of(com.thefarawaydev.realmsofchaos.entity.Titan::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F).fireImmune().clientTrackingRange(12).build(key);
    }

    public static ResourceKey<EntityType<?>> titanKey() {
        return ResourceKey.create(Registries.ENTITY_TYPE, RealmsOfChaos.id("titan"));
    }
    public static final Ref<EntityType<RelicProjectile>> RELIC_PROJECTILE = new Ref<>();
    public static final Ref<EntityType<com.thefarawaydev.realmsofchaos.entity.Sentinel>> SENTINEL = new Ref<>();
    public static final Ref<CreativeModeTab> TAB = new Ref<>();

    static {
        // blocks
        for (com.thefarawaydev.realmsofchaos.item.Panoply.Piece piece : com.thefarawaydev.realmsofchaos.item.Panoply.Piece.values()) {
            Ref<Item> ref = new Ref<>();
            PANOPLY.put(piece.id(), ref);
            item(piece.id(), ref, key -> com.thefarawaydev.realmsofchaos.item.Panoply.create(piece, key));
        }
        block("gate_hub", GATE_HUB, key -> gate(GateBlock.Kind.HUB, key));
        block("gate_zeus", GATE_ZEUS, key -> gate(GateBlock.Kind.ZEUS, key));
        block("gate_poseidon", GATE_POSEIDON, key -> gate(GateBlock.Kind.POSEIDON, key));
        block("gate_hades", GATE_HADES, key -> gate(GateBlock.Kind.HADES, key));
        block("gate_ascend", GATE_ASCEND, key -> gate(GateBlock.Kind.ASCEND, key));
        block("gate_leave", GATE_LEAVE, key -> gate(GateBlock.Kind.LEAVE, key));
        block("altar_zeus", ALTAR_ZEUS, key -> altar(Line.ZEUS, key));
        block("altar_poseidon", ALTAR_POSEIDON, key -> altar(Line.POSEIDON, key));
        block("altar_hades", ALTAR_HADES, key -> altar(Line.HADES, key));
        block("chaos_forge", CHAOS_FORGE, key -> new ForgeBlock(unbreakable(key).mapColor(MapColor.COLOR_ORANGE).lightLevel(s -> 14).noOcclusion().sound(SoundType.ANVIL)));

        block("passage_seal", PASSAGE_SEAL, key -> new com.thefarawaydev.realmsofchaos.block.SealBlock(
                unbreakable(key).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 12).noOcclusion().sound(SoundType.AMETHYST)));
        block("passage_shrine", PASSAGE_SHRINE, key -> new com.thefarawaydev.realmsofchaos.block.ShrineBlock(
                unbreakable(key).mapColor(MapColor.QUARTZ).lightLevel(s -> 14).sound(SoundType.AMETHYST)));

        block("gate_pedestal", GATE_PEDESTAL, key -> new com.thefarawaydev.realmsofchaos.block.GatePedestalBlock(
                unbreakable(key).mapColor(MapColor.STONE).lightLevel(s -> 8).noOcclusion().sound(SoundType.STONE)));
        block("oracle", ORACLE, key -> new com.thefarawaydev.realmsofchaos.block.OracleBlock(
                unbreakable(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 15).noOcclusion().sound(SoundType.AMETHYST)));

        block("passage_cache", PASSAGE_CACHE, key -> new com.thefarawaydev.realmsofchaos.block.CacheBlock(
                unbreakable(key).mapColor(MapColor.WOOD).lightLevel(s -> 6).sound(SoundType.WOOD).noOcclusion()));

        // a god's shield pylons, and the blocks of the field network - the only blocks here a player makes and breaks
        block("shield_pylon", SHIELD_PYLON, key -> new com.thefarawaydev.realmsofchaos.block.ShieldPylonBlock(
                unbreakable(key).mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 13).noOcclusion().sound(SoundType.AMETHYST)));
        block("relay_core", RELAY_CORE, key -> new com.thefarawaydev.realmsofchaos.block.FieldBlock(com.thefarawaydev.realmsofchaos.world.Network.Kind.RELAY,
                field(key).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 10)));
        block("signal_jammer", SIGNAL_JAMMER, key -> new com.thefarawaydev.realmsofchaos.block.FieldBlock(com.thefarawaydev.realmsofchaos.world.Network.Kind.JAMMER,
                field(key).mapColor(MapColor.COLOR_RED).lightLevel(s -> 6)));
        block("waygate_obelisk", WAYGATE_OBELISK, key -> new com.thefarawaydev.realmsofchaos.block.FieldBlock(com.thefarawaydev.realmsofchaos.world.Network.Kind.WAYGATE,
                field(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 12)));
        block("hub_heart", HUB_HEART, key -> new com.thefarawaydev.realmsofchaos.block.HubStationBlock(com.thefarawaydev.realmsofchaos.block.HubStationBlock.Kind.HEART,
                unbreakable(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 15).noOcclusion().sound(SoundType.AMETHYST)));
        block("bounty_board", BOUNTY_BOARD, key -> new com.thefarawaydev.realmsofchaos.block.HubStationBlock(com.thefarawaydev.realmsofchaos.block.HubStationBlock.Kind.BOUNTIES,
                unbreakable(key).mapColor(MapColor.WOOD).lightLevel(s -> 8).sound(SoundType.WOOD)));

        block("rune_stone", RUNE_STONE, key -> new com.thefarawaydev.realmsofchaos.block.RuneStoneBlock(
                unbreakable(key).mapColor(MapColor.STONE).sound(SoundType.STONE)
                        .lightLevel(s -> s.getValue(com.thefarawaydev.realmsofchaos.block.RuneStoneBlock.LIT) ? 15 : 5)));

        // what the passages' rooms are furnished with
        room("glyph_tile", GLYPH_TILE, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.GLYPH, 12, false);
        room("chime_crystal", CHIME_CRYSTAL, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.CHIME, 14, true);
        room("memory_plate", MEMORY_PLATE, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.PLATE, 10, false);
        room("beam_mirror", BEAM_MIRROR, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.MIRROR, 0, true);
        room("beam_emitter", BEAM_EMITTER, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.EMITTER, 0, true);
        room("beam_receiver", BEAM_RECEIVER, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.RECEIVER, 15, true);
        room("dart_tile", DART_TILE, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.DART, 0, false);
        room("ancient_urn", ANCIENT_URN, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.URN, 0, true);
        room("lore_tablet", LORE_TABLET, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind.TABLET, 0, true);
        block("tower_brazier", TOWER_BRAZIER, key -> new com.thefarawaydev.realmsofchaos.block.BrazierBlock(
                unbreakable(key).mapColor(MapColor.METAL).lightLevel(s -> 14).noOcclusion().sound(SoundType.METAL)));

        // the realms' own building stone, which the hub and its smithies are built of; dug with a pickaxe
        block("olympian_marble", OLYMPIAN_MARBLE, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        block("olympian_marble_pillar", OLYMPIAN_MARBLE_PILLAR, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        block("gilded_frieze", GILDED_FRIEZE, key -> new Block(decor(key).mapColor(MapColor.GOLD)));
        block("abyssal_tile", ABYSSAL_TILE, key -> new Block(decor(key).mapColor(MapColor.WARPED_WART_BLOCK)));
        block("underworld_brick", UNDERWORLD_BRICK, key -> new Block(decor(key).mapColor(MapColor.NETHER).lightLevel(s -> 3)));
        block("chaos_lamp", CHAOS_LAMP, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 15).sound(SoundType.GLASS).strength(0.6F)));
        block("olympian_tiles", OLYMPIAN_TILES, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        block("chaos_mosaic", CHAOS_MOSAIC, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        // panels with connecting textures: a run of them shows one frame round its outside
        block("olympian_panel", OLYMPIAN_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.QUARTZ)));
        block("abyssal_panel", ABYSSAL_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.WARPED_WART_BLOCK)));
        block("underworld_panel", UNDERWORLD_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_BLACK)));
        // the trap halls' own fittings
        block("spike_plate", SPIKE_PLATE, key -> new Block(unbreakable(key).mapColor(MapColor.STONE).sound(SoundType.STONE)));
        block("spike_plate_dark", SPIKE_PLATE_DARK, key -> new Block(unbreakable(key).mapColor(MapColor.DEEPSLATE).sound(SoundType.STONE)));
        block("trap_spikes", TRAP_SPIKES, key -> new com.thefarawaydev.realmsofchaos.block.SpikesBlock(unbreakable(key).mapColor(MapColor.METAL).noCollision().noOcclusion().sound(SoundType.METAL)));
        block("flame_vent", FLAME_VENT, key -> new Block(unbreakable(key).mapColor(MapColor.COLOR_BLACK).lightLevel(s -> 5).sound(SoundType.BASALT)));
        block("gas_vent", GAS_VENT, key -> new Block(unbreakable(key).mapColor(MapColor.COLOR_GREEN).lightLevel(s -> 3).sound(SoundType.TUFF)));
        // carved reliefs of each tower's story, set into the walls of its rooms and floors
        block("sky_relief", SKY_RELIEF, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        block("sea_relief", SEA_RELIEF, key -> new Block(decor(key).mapColor(MapColor.WARPED_WART_BLOCK)));
        block("shade_relief", SHADE_RELIEF, key -> new Block(decor(key).mapColor(MapColor.COLOR_BLACK)));
        // each tower's own stone; columns show a base and a capital only at the ends of a run, panels one frame round a run
        block("marble_column", MARBLE_COLUMN, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.QUARTZ)));
        block("sunstone_panel", SUNSTONE_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.SAND).lightLevel(s -> 4)));
        block("storm_brick", STORM_BRICK, key -> new Block(decor(key).mapColor(MapColor.QUARTZ)));
        block("coral_column", CORAL_COLUMN, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.WARPED_WART_BLOCK)));
        block("pearl_panel", PEARL_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.SNOW)));
        block("tide_brick", TIDE_BRICK, key -> new Block(decor(key).mapColor(MapColor.WARPED_WART_BLOCK)));
        block("bone_column", BONE_COLUMN, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.SAND)));
        block("ember_panel", EMBER_PANEL, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_BLACK).lightLevel(s -> 5)));
        block("ash_brick", ASH_BRICK, key -> new Block(decor(key).mapColor(MapColor.SAND)));
        // the furnishings of the observatories, armouries, baths, sanctums, gardens and libraries
        block("sky_mosaic", SKY_MOSAIC, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_LIGHT_BLUE)));
        block("sea_mosaic", SEA_MOSAIC, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_CYAN)));
        block("shade_mosaic", SHADE_MOSAIC, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_PURPLE)));
        block("star_vault", STAR_VAULT, key -> new Block(decor(key).mapColor(MapColor.COLOR_BLUE).lightLevel(s -> 6)));
        block("celestial_globe", CELESTIAL_GLOBE, key -> new Block(decor(key).mapColor(MapColor.COLOR_BLUE).noOcclusion().lightLevel(s -> 9).sound(SoundType.METAL)));
        block("bronze_grate", BRONZE_GRATE, key -> new Block(decor(key).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.METAL)));
        block("weapon_rack", WEAPON_RACK, key -> new com.thefarawaydev.realmsofchaos.block.WeaponRackBlock(decor(key).mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.0F).noOcclusion()));
        block("scroll_shelf", SCROLL_SHELF, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.5F)));
        block("bath_tile", BATH_TILE, key -> new Block(decor(key).mapColor(MapColor.SNOW)));
        block("laurel_hedge", LAUREL_HEDGE, key -> new Block(decor(key).mapColor(MapColor.PLANT).sound(SoundType.AZALEA_LEAVES).strength(0.6F)));
        block("kelp_weave", KELP_WEAVE, key -> new Block(decor(key).mapColor(MapColor.PLANT).sound(SoundType.WET_GRASS).strength(0.6F)));
        block("cloudstone", CLOUDSTONE, key -> new Block(decor(key).mapColor(MapColor.SNOW)));
        block("cloud_tile", CLOUD_TILE, key -> new Block(decor(key).mapColor(MapColor.SNOW)));
        block("trench_stone", TRENCH_STONE, key -> new Block(decor(key).mapColor(MapColor.COLOR_BLUE).lightLevel(s -> 2)));
        block("nacre_tile", NACRE_TILE, key -> new Block(decor(key).mapColor(MapColor.COLOR_PINK)));
        block("tartarus_stone", TARTARUS_STONE, key -> new Block(decor(key).mapColor(MapColor.NETHER)));
        block("throne_tile", THRONE_TILE, key -> new Block(decor(key).mapColor(MapColor.COLOR_BLACK)));
        block("portal_frame_sky", PORTAL_FRAME_SKY, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.QUARTZ).lightLevel(s -> 7)));
        block("portal_frame_sea", PORTAL_FRAME_SEA, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.WARPED_WART_BLOCK).lightLevel(s -> 7)));
        block("portal_frame_shade", PORTAL_FRAME_SHADE, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 7)));
        block("portal_frame_hub", PORTAL_FRAME_HUB, key -> new com.thefarawaydev.realmsofchaos.block.ConnectedBlock(decor(key).mapColor(MapColor.COLOR_GRAY).lightLevel(s -> 7)));
        block("dial_stone", DIAL_STONE, key -> new com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.Dial(unbreakable(key).mapColor(MapColor.STONE).sound(SoundType.STONE)));
        block("fresco_tile", FRESCO_TILE, key -> new com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.Fresco(unbreakable(key).mapColor(MapColor.SAND)
                .lightLevel(s -> s.getValue(com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.LIT) ? 9 : 0)));
        block("slide_stone", SLIDE_STONE, key -> new com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.SlideStone(unbreakable(key).mapColor(MapColor.STONE).sound(SoundType.STONE)));
        block("stone_socket", STONE_SOCKET, key -> new com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.Socket(unbreakable(key).mapColor(MapColor.STONE)
                .lightLevel(s -> s.getValue(com.thefarawaydev.realmsofchaos.block.PuzzleBlocks.LIT) ? 11 : 2)));
        block("bronze_sliding_door", BRONZE_SLIDING_DOOR, key -> new com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock(decor(key).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER).noOcclusion()));
        block("sky_sliding_door", SKY_SLIDING_DOOR, key -> new com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock(decor(key).mapColor(MapColor.QUARTZ).noOcclusion()));
        block("sea_sliding_door", SEA_SLIDING_DOOR, key -> new com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock(decor(key).mapColor(MapColor.WARPED_WART_BLOCK).noOcclusion()));
        block("shade_sliding_door", SHADE_SLIDING_DOOR, key -> new com.thefarawaydev.realmsofchaos.block.SlidingDoorBlock(decor(key).mapColor(MapColor.COLOR_BLACK).noOcclusion()));
        block("sprinkler", SPRINKLER, key -> new com.thefarawaydev.realmsofchaos.block.SprinklerBlock(decor(key).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER).noOcclusion()));
        block("arena_sand", ARENA_SAND, key -> new Block(decor(key).mapColor(MapColor.SAND).sound(SoundType.SAND).strength(1.0F)));
        block("war_drum", WAR_DRUM, key -> new com.thefarawaydev.realmsofchaos.block.WarDrumBlock(decor(key).mapColor(MapColor.COLOR_RED).sound(SoundType.WOOD).strength(1.5F).noOcclusion()));
        block("war_standard", WAR_STANDARD, key -> new com.thefarawaydev.realmsofchaos.block.WarStandardBlock(decor(key).mapColor(MapColor.COLOR_RED).sound(SoundType.WOOD).strength(1.0F).noOcclusion()));
        block("beast_gate", BEAST_GATE, key -> new Block(decor(key).mapColor(MapColor.METAL).sound(SoundType.METAL).strength(5.0F)));
        block("training_dummy", TRAINING_DUMMY, key -> new com.thefarawaydev.realmsofchaos.block.TrainingDummyBlock(furniture(key).strength(1.0F)));
        block("moving_dummy", MOVING_DUMMY, key -> new com.thefarawaydev.realmsofchaos.block.MovingDummyBlock(furniture(key).strength(1.0F)));
        block("chair", CHAIR, key -> new com.thefarawaydev.realmsofchaos.block.Furniture.Chair(furniture(key)));
        block("table", TABLE, key -> new com.thefarawaydev.realmsofchaos.block.Furniture.Table(furniture(key)));
        block("bench", BENCH, key -> new com.thefarawaydev.realmsofchaos.block.Furniture.Bench(furniture(key)));
        block("dresser", DRESSER, key -> new com.thefarawaydev.realmsofchaos.block.Furniture.Dresser(furniture(key)));
        block("bunk_bed", BUNK_BED, key -> new com.thefarawaydev.realmsofchaos.block.Furniture.BunkBed(furniture(key)));
        door("garrison", GARRISON_DOOR, GARRISON_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK, MapColor.COLOR_BROWN);
        door("verdigris", VERDIGRIS_DOOR, VERDIGRIS_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.COPPER, MapColor.WARPED_STEM);
        door("ivory", IVORY_DOOR, IVORY_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.BIRCH, MapColor.SNOW);
        block("reinforced_glass", REINFORCED_GLASS, key -> new com.thefarawaydev.realmsofchaos.block.Glass.Pane(glass(key).strength(5.0F, 9.0F).requiresCorrectToolForDrops()));
        block("frosted_glass", FROSTED_GLASS, key -> new com.thefarawaydev.realmsofchaos.block.Glass.Pane(glass(key)));
        block("smart_glass", SMART_GLASS, key -> new com.thefarawaydev.realmsofchaos.block.Glass.Smart(glass(key)));
        block("veil_glass", VEIL_GLASS, key -> new com.thefarawaydev.realmsofchaos.block.Glass.Veil(glass(key)));
        block("one_way_glass", ONE_WAY_GLASS, key -> new com.thefarawaydev.realmsofchaos.block.Glass.OneWay(glass(key)));
        block("arrival_sigil", ARRIVAL_SIGIL, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 10)));
        block("bronze_door", BRONZE_DOOR, key -> new net.minecraft.world.level.block.DoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.COPPER,
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_ORANGE).strength(3.0F).requiresCorrectToolForDrops().noOcclusion()));
        block("bronze_trapdoor", BRONZE_TRAPDOOR, key -> new net.minecraft.world.level.block.TrapDoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.COPPER,
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_ORANGE).strength(3.0F).requiresCorrectToolForDrops().noOcclusion()));
        // each tower's own door and trapdoor
        // porphyry
        block("porphyry", PORPHYRY, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        block("polished_porphyry", POLISHED_PORPHYRY, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        block("porphyry_bricks", PORPHYRY_BRICKS, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        block("chiseled_porphyry", CHISELED_PORPHYRY, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        block("porphyry_tiles", PORPHYRY_TILES, key -> new Block(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        block("porphyry_pillar", PORPHYRY_PILLAR, key -> new net.minecraft.world.level.block.RotatedPillarBlock(decor(key).mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F)));
        door("porphyry", PORPHYRY_DOOR, PORPHYRY_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.STONE, MapColor.COLOR_PURPLE);
        // adamantine: deep down, hard to break, and only a diamond pickaxe or better will win it
        block("deepslate_adamantine_ore", DEEPSLATE_ADAMANTINE_ORE, key -> new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(4, 9),
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(30.0F, 1200.0F).sound(SoundType.DEEPSLATE)));
        block("adamantine_block", ADAMANTINE_BLOCK, key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.DIAMOND)
                .requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)));
        // what is won from the world, and the horn of plenty
        block("fulgurite", FULGURITE, key -> new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.SAND).requiresCorrectToolForDrops()
                .strength(1.8F, 6.0F).sound(SoundType.GLASS)));
        block("giant_clam", GIANT_CLAM, key -> new com.thefarawaydev.realmsofchaos.block.GiantClamBlock(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0F).sound(SoundType.CORAL_BLOCK).noOcclusion().randomTicks()));
        block("stygian_iron_ore", STYGIAN_IRON_ORE, key -> new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(3, 7),
                BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BROWN).requiresCorrectToolForDrops().strength(20.0F, 1200.0F).sound(SoundType.NETHER_ORE)));
        block("cornucopia", CORNUCOPIA, key -> new com.thefarawaydev.realmsofchaos.block.CornucopiaBlock(BlockBehaviour.Properties.of().setId(key)
                .mapColor(MapColor.GOLD).requiresCorrectToolForDrops().strength(50.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()
                .lightLevel(st -> st.getValue(com.thefarawaydev.realmsofchaos.block.CornucopiaBlock.CORE) == com.thefarawaydev.realmsofchaos.block.CornucopiaBlock.Core.NONE ? 0 : 9)));
        door("sky", SKY_DOOR, SKY_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.COPPER, MapColor.QUARTZ);
        door("sea", SEA_DOOR, SEA_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK, MapColor.WARPED_WART_BLOCK);
        door("shade", SHADE_DOOR, SHADE_TRAPDOOR, net.minecraft.world.level.block.state.properties.BlockSetType.CRIMSON, MapColor.COLOR_BLACK);
        block("asphodel_bed", ASPHODEL_BED, key -> new Block(decor(key).mapColor(MapColor.COLOR_LIGHT_GRAY).sound(SoundType.AZALEA_LEAVES).strength(0.6F)));

        // stairs and slabs of the mod's stone (after the stone itself: a stair is cut from its block's state)
        Map<String, Ref<Block>> stones = new java.util.LinkedHashMap<>();
        stones.put("olympian_marble", OLYMPIAN_MARBLE);
        stones.put("cloudstone", CLOUDSTONE);
        stones.put("trench_stone", TRENCH_STONE);
        stones.put("tartarus_stone", TARTARUS_STONE);
        stones.put("storm_brick", STORM_BRICK);
        stones.put("tide_brick", TIDE_BRICK);
        stones.put("ash_brick", ASH_BRICK);
        stones.put("underworld_brick", UNDERWORLD_BRICK);
        stones.put("polished_porphyry", POLISHED_PORPHYRY);
        stones.put("porphyry_bricks", PORPHYRY_BRICKS);
        for (Map.Entry<String, Ref<Block>> stone : stones.entrySet()) {
            Ref<Block> stairs = new Ref<>(), slab = new Ref<>();
            STAIRS.put(stone.getKey(), stairs);
            SLABS.put(stone.getKey(), slab);
            Ref<Block> base = stone.getValue();
            block(stone.getKey() + "_stairs", stairs, key -> new net.minecraft.world.level.block.StairBlock(base.get().defaultBlockState(), decor(key)));
            block(stone.getKey() + "_slab", slab, key -> new net.minecraft.world.level.block.SlabBlock(decor(key)));
        }

        // items: codex, compass, 45 god weapons (ordered by line, rank 1 -> 15), then the block items
        item("chaos_codex", CHAOS_CODEX, key -> new CodexItem(new Item.Properties().setId(key).stacksTo(1)));
        item("chaos_compass", CHAOS_COMPASS, key -> new ChaosCompassItem(new Item.Properties().setId(key).stacksTo(1)));
        item("lockpick", LOCKPICK, key -> new Item(new Item.Properties().setId(key).stacksTo(16)));
        item("raw_adamantine", RAW_ADAMANTINE, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        item("adamantine_scrap", ADAMANTINE_SCRAP, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
        item("adamantine_ingot", ADAMANTINE_INGOT, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
        item("storm_glass", STORM_GLASS, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        item("abyssal_pearl", ABYSSAL_PEARL, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        item("raw_stygian_iron", RAW_STYGIAN_IRON, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        item("celestial_bronze_ingot", CELESTIAL_BRONZE_INGOT, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE)));
        item("orichalcum_ingot", ORICHALCUM_INGOT, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE)));
        item("stygian_iron_ingot", STYGIAN_IRON_INGOT, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
        item("thunder_core", THUNDER_CORE, key -> new Item(core(key, "thunder_core")));
        item("tide_core", TIDE_CORE, key -> new Item(core(key, "tide_core")));
        item("shade_core", SHADE_CORE, key -> new Item(core(key, "shade_core")));
        for (var set : com.thefarawaydev.realmsofchaos.item.ArmorSets.Set.values()) {
            Map<net.minecraft.world.item.equipment.ArmorType, Ref<Item>> pieces = new java.util.EnumMap<>(net.minecraft.world.item.equipment.ArmorType.class);
            ARMOR.put(set, pieces);
            for (var type : com.thefarawaydev.realmsofchaos.item.ArmorSets.PIECES) {
                Ref<Item> ref = new Ref<>();
                pieces.put(type, ref);
                item(set.id() + "_" + com.thefarawaydev.realmsofchaos.item.ArmorSets.pieceName(type), ref, key -> com.thefarawaydev.realmsofchaos.item.ArmorSets.create(set, type, key));
            }
        }
        item("adamantine_sword", ADAMANTINE_SWORD, key -> new Item(tool(key).sword(ADAMANTINE, 3.0F, -2.4F)));
        item("adamantine_pickaxe", ADAMANTINE_PICKAXE, key -> new Item(tool(key).pickaxe(ADAMANTINE, 1.0F, -2.8F)));
        item("adamantine_axe", ADAMANTINE_AXE, key -> new Item(tool(key).axe(ADAMANTINE, 5.0F, -3.0F)));
        item("adamantine_shovel", ADAMANTINE_SHOVEL, key -> new Item(tool(key).shovel(ADAMANTINE, 1.5F, -3.0F)));
        item("adamantine_hoe", ADAMANTINE_HOE, key -> new Item(tool(key).hoe(ADAMANTINE, -5.0F, 0.0F)));
        item("field_bag", FIELD_BAG, key -> new com.thefarawaydev.realmsofchaos.item.FieldBagItem(new Item.Properties().setId(key).stacksTo(1)));
        for (Line line : Line.values()) {
            for (God god : Gods.ofLine(line)) {
                Ref<Item> ref = new Ref<>();
                WEAPONS.put(god.id(), ref);
                item(god.id() + "_weapon", ref, key -> new GodWeaponItem(god, GodWeaponItem.properties(god, key)));
            }
        }
        // the way in: three keystones and the seekers that find them
        for (Line line : Line.values()) {
            Ref<Item> keystone = new Ref<>(), seeker = new Ref<>();
            KEYSTONES.put(line, keystone);
            SEEKERS.put(line, seeker);
            item("keystone_" + line.id(), keystone, key -> new com.thefarawaydev.realmsofchaos.item.KeystoneItem(line,
                    new Item.Properties().setId(key).stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
            item("seeker_" + line.id(), seeker, key -> new com.thefarawaydev.realmsofchaos.item.SeekerItem(line,
                    new Item.Properties().setId(key).stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        }
        // tarot: the Deck of Fate and the 22 single cards
        item("tarot_deck", TAROT_DECK, key -> new TarotDeckItem(new Item.Properties().setId(key).stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()));
        for (Tarot card : Tarot.values()) {
            Ref<Item> ref = new Ref<>();
            TAROT.put(card, ref);
            item(card.itemId(), ref, key -> new TarotCardItem(card, new Item.Properties().setId(key).stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        }
        // voodoo dolls, forging essences and the projectile sprites
        for (VoodooDollItem.Pin pin : VoodooDollItem.Pin.values()) {
            Ref<Item> ref = new Ref<>();
            DOLLS.put(pin, ref);
            item("voodoo_doll_" + pin.name().toLowerCase(), ref, key -> new VoodooDollItem(pin, new Item.Properties().setId(key).stacksTo(1)));
        }
        for (Line line : Line.values()) {
            Ref<Item> ref = new Ref<>();
            ESSENCES.put(line, ref);
            item("essence_" + line.id(), ref, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.RARE).fireResistant()));
        }
        for (Line line : Line.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                Ref<Item> ref = new Ref<>();
                MATERIALS.put(line.id() + "/" + tier, ref);
                item("material_" + line.id() + "_" + tier, ref, key -> new Item(new Item.Properties().setId(key).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
            }
        }
        for (Power power : Power.values()) {
            Ref<Item> ref = new Ref<>();
            ORBS.put(power, ref);
            item("orb_" + power.name().toLowerCase(), ref, key -> new Item(new Item.Properties().setId(key)));
        }
        for (Def<Block> b : BLOCK_DEFS) {
            Ref<Item> ref = new Ref<>();
            BLOCK_ITEMS.put(b.id(), ref);
            item(b.id(), ref, key -> new BlockItem(b.ref().get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));
        }
    }

    /** One of the passages' room blocks. @param glow how bright it is when lit (0: never) @param open true if it is not a full cube */
    private static void room(String id, Ref<Block> ref, com.thefarawaydev.realmsofchaos.block.RoomBlock.Kind kind, int glow, boolean open) {
        block(id, ref, key -> {
            BlockBehaviour.Properties properties = unbreakable(key).mapColor(MapColor.STONE).sound(SoundType.STONE)
                    .lightLevel(s -> glow > 0 && s.getValue(com.thefarawaydev.realmsofchaos.block.RoomBlock.LIT) ? glow : glow > 0 ? 3 : 0);
            return new com.thefarawaydev.realmsofchaos.block.RoomBlock(kind, open ? properties.noOcclusion() : properties);
        });
    }

    private static void block(String id, Ref<Block> ref, Function<ResourceKey<Block>, Block> factory) {
        BLOCK_DEFS.add(new Def<>(id, factory, ref));
    }

    /** A core: one of the hearts the relics and the Cornucopia are built round. */
    private static Item.Properties core(ResourceKey<Item> key, String id) {
        return new Item.Properties().setId(key).stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC).fireResistant()
                .component(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                        net.minecraft.network.chat.Component.translatable("item." + RealmsOfChaos.MOD_ID + "." + id + ".lore").withStyle(net.minecraft.ChatFormatting.GRAY))));
    }

    /** What every adamantine tool shares: one to a stack, proof against fire and lava, and rare. */
    private static Item.Properties tool(ResourceKey<Item> key) {
        return new Item.Properties().setId(key).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC);
    }

    private static void item(String id, Ref<Item> ref, Function<ResourceKey<Item>, Item> factory) {
        ITEM_DEFS.add(new Def<>(id, factory, ref));
    }

    private static BlockBehaviour.Properties unbreakable(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).strength(-1.0F, 3600000.0F).noLootTable();
    }

    private static void door(String id, Ref<Block> door, Ref<Block> trapdoor, net.minecraft.world.level.block.state.properties.BlockSetType set, MapColor color) {
        block(id + "_door", door, key -> new net.minecraft.world.level.block.DoorBlock(set,
                BlockBehaviour.Properties.of().setId(key).mapColor(color).strength(3.0F).noOcclusion()));
        block(id + "_trapdoor", trapdoor, key -> new net.minecraft.world.level.block.TrapDoorBlock(set,
                BlockBehaviour.Properties.of().setId(key).mapColor(color).strength(3.0F).noOcclusion()));
    }

    /** Furniture: wood, broken by hand or quicker with an axe, see-through where it is not solid. */
    private static BlockBehaviour.Properties furniture(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion();
    }

    /** Glass: broken quickly, lets light through, does not hide what is beside it. */
    private static BlockBehaviour.Properties glass(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).strength(0.6F).sound(SoundType.GLASS).noOcclusion()
                .isValidSpawn((state, getter, pos, type) -> false).isRedstoneConductor((state, getter, pos) -> false)
                .isSuffocating((state, getter, pos) -> false);
    }

    /** Building stone: as hard as stone, dug with a pickaxe. */
    private static BlockBehaviour.Properties decor(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    /** A block of the field network: it can be broken by hand, drops itself, and shrugs off explosions. */
    private static BlockBehaviour.Properties field(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).strength(2.5F, 1200.0F).noOcclusion().sound(SoundType.AMETHYST)
                .pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE);
    }

    private static Block gate(GateBlock.Kind kind, ResourceKey<Block> key) {
        return new GateBlock(kind, unbreakable(key).mapColor(MapColor.COLOR_PURPLE).noCollision().lightLevel(s -> 15)
                .sound(SoundType.AMETHYST));
    }

    private static Block altar(Line line, ResourceKey<Block> key) {
        return new AltarBlock(line, unbreakable(key).mapColor(MapColor.STONE).lightLevel(s -> 10).noOcclusion().sound(SoundType.STONE));
    }

    // ---- entity -------------------------------------------------------------------------------

    public static EntityType<GodBoss> createGodBossType(ResourceKey<EntityType<?>> key) {
        return EntityType.Builder.<GodBoss>of(GodBoss::new, MobCategory.MONSTER)
                .sized(0.7F, 2.1F)
                .clientTrackingRange(12)
                .fireImmune()
                .build(key);
    }

    public static EntityType<RelicProjectile> createProjectileType(ResourceKey<EntityType<?>> key) {
        return EntityType.Builder.<RelicProjectile>of(RelicProjectile::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .clientTrackingRange(6)
                .updateInterval(10)
                .build(key);
    }

    public static ResourceKey<EntityType<?>> projectileKey() {
        return ResourceKey.create(Registries.ENTITY_TYPE, RealmsOfChaos.id("relic_projectile"));
    }

    public static Item orbItem(Power power) {
        return ORBS.get(power).get();
    }

    public static Item tarotItem(Tarot card) {
        return TAROT.get(card).get();
    }

    public static Item keystoneItem(Line line) {
        return KEYSTONES.get(line).get();
    }

    public static Item seekerItem(Line line) {
        return SEEKERS.get(line).get();
    }

    public static Item essenceItem(Line line) {
        return ESSENCES.get(line).get();
    }

    /** What the gods of a tower's tier drop for the forge: tiers 1-3 their own material, tier 4 (the summit) the essence. */
    public static Item materialItem(Line line, int tier) {
        return tier >= 4 ? essenceItem(line) : MATERIALS.get(line.id() + "/" + tier).get();
    }

    public static EntityType<com.thefarawaydev.realmsofchaos.entity.Sentinel> createSentinelType(ResourceKey<EntityType<?>> key) {
        return EntityType.Builder.<com.thefarawaydev.realmsofchaos.entity.Sentinel>of(com.thefarawaydev.realmsofchaos.entity.Sentinel::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(10)
                .build(key);
    }

    public static ResourceKey<EntityType<?>> sentinelKey() {
        return ResourceKey.create(Registries.ENTITY_TYPE, RealmsOfChaos.id("sentinel"));
    }

    public static ResourceKey<EntityType<?>> godBossKey() {
        return ResourceKey.create(Registries.ENTITY_TYPE, RealmsOfChaos.id("god_boss"));
    }

    // ---- helpers ------------------------------------------------------------------------------

    public static Item weaponItem(God god) {
        Ref<Item> ref = WEAPONS.get(god.id());
        try {
            return ref == null ? null : ref.get();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public static Component tabTitle() {
        return Component.translatable("itemGroup." + RealmsOfChaos.MOD_ID + ".main");
    }

    public static ItemStack tabIcon() {
        return new ItemStack(WEAPONS.get("zeus").get());
    }

    /** Everything that belongs in the Realms of Chaos creative tab, in display order. */
    public static List<Item> tabItems() {
        List<Item> out = new ArrayList<>();
        out.add(CHAOS_CODEX.get());
        out.add(CHAOS_COMPASS.get());
        out.add(FIELD_BAG.get());
        out.add(LOCKPICK.get());
        for (Ref<Item> r : List.of(RAW_ADAMANTINE, ADAMANTINE_SCRAP, ADAMANTINE_INGOT, ADAMANTINE_SWORD, ADAMANTINE_PICKAXE, ADAMANTINE_AXE, ADAMANTINE_SHOVEL, ADAMANTINE_HOE))
            out.add(r.get());
        for (Ref<Item> r : List.of(STORM_GLASS, ABYSSAL_PEARL, RAW_STYGIAN_IRON, CELESTIAL_BRONZE_INGOT, ORICHALCUM_INGOT, STYGIAN_IRON_INGOT,
                THUNDER_CORE, TIDE_CORE, SHADE_CORE)) out.add(r.get());
        for (var pieces : ARMOR.values()) for (Ref<Item> r : pieces.values()) out.add(r.get());
        for (Line line : Line.values()) {
            for (God god : Gods.ofLine(line)) {
                out.add(WEAPONS.get(god.id()).get());
            }
        }
        for (Line line : Line.values()) {
            for (int tier = 1; tier <= 4; tier++) out.add(materialItem(line, tier));
        }
        for (com.thefarawaydev.realmsofchaos.item.Panoply.Piece piece : com.thefarawaydev.realmsofchaos.item.Panoply.Piece.values()) out.add(piece.item());
        for (Line line : Line.values()) out.add(seekerItem(line));
        for (Line line : Line.values()) out.add(keystoneItem(line));
        out.add(TAROT_DECK.get());
        for (Tarot card : Tarot.values()) out.add(TAROT.get(card).get());
        for (VoodooDollItem.Pin pin : VoodooDollItem.Pin.values()) out.add(DOLLS.get(pin).get());
        for (Def<Block> b : BLOCK_DEFS) {
            out.add(BLOCK_ITEMS.get(b.id()).get());
        }
        return out;
    }
}
