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
            ABYSSAL_TILE = new Ref<>(), UNDERWORLD_BRICK = new Ref<>(), CHAOS_LAMP = new Ref<>();
    public static final Ref<Block> TOWER_BRAZIER = new Ref<>();

    public static final List<Def<Block>> BLOCK_DEFS = new ArrayList<>();

    // ---- items --------------------------------------------------------------------------------
    public static final Ref<Item> CHAOS_COMPASS = new Ref<>();
    public static final Ref<Item> CHAOS_CODEX = new Ref<>();
    public static final Ref<Item> FIELD_BAG = new Ref<>();
    public static final Map<String, Ref<Item>> WEAPONS = new HashMap<>();
    /** The eight pieces of the panoply, by id. */
    public static final Map<String, Ref<Item>> PANOPLY = new java.util.LinkedHashMap<>();
    public static final Map<String, Ref<Item>> BLOCK_ITEMS = new HashMap<>();
    public static final Ref<Item> TAROT_DECK = new Ref<>();
    public static final Map<Tarot, Ref<Item>> TAROT = new HashMap<>();
    public static final Map<VoodooDollItem.Pin, Ref<Item>> DOLLS = new HashMap<>();
    public static final Map<Line, Ref<Item>> ESSENCES = new HashMap<>();
    public static final Map<Line, Ref<Item>> KEYSTONES = new HashMap<>();
    public static final Map<Line, Ref<Item>> SEEKERS = new HashMap<>();
    /** Forge materials dropped on tiers 1-3 of each tower, keyed "<line id>/<tier>". */
    public static final Map<String, Ref<Item>> MATERIALS = new HashMap<>();
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
                unbreakable(key).mapColor(MapColor.WOOD).lightLevel(s -> 6).sound(SoundType.WOOD)));

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

        // items: codex, compass, 45 god weapons (ordered by line, rank 1 -> 15), then the block items
        item("chaos_codex", CHAOS_CODEX, key -> new CodexItem(new Item.Properties().setId(key).stacksTo(1)));
        item("chaos_compass", CHAOS_COMPASS, key -> new ChaosCompassItem(new Item.Properties().setId(key).stacksTo(1)));
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

    private static void item(String id, Ref<Item> ref, Function<ResourceKey<Item>, Item> factory) {
        ITEM_DEFS.add(new Def<>(id, factory, ref));
    }

    private static BlockBehaviour.Properties unbreakable(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().setId(key).strength(-1.0F, 3600000.0F).noLootTable();
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
