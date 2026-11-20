package com.thefarawaydev.realmsofchaos;

import com.thefarawaydev.realmsofchaos.block.AltarBlock;
import com.thefarawaydev.realmsofchaos.block.GateBlock;
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

    public static final List<Def<Block>> BLOCK_DEFS = new ArrayList<>();

    // ---- items --------------------------------------------------------------------------------
    public static final Ref<Item> CHAOS_COMPASS = new Ref<>();
    public static final Ref<Item> CHAOS_CODEX = new Ref<>();
    public static final Map<String, Ref<Item>> WEAPONS = new HashMap<>();
    public static final Map<String, Ref<Item>> BLOCK_ITEMS = new HashMap<>();

    public static final List<Def<Item>> ITEM_DEFS = new ArrayList<>();

    // ---- entity / tab -------------------------------------------------------------------------
    public static final Ref<EntityType<GodBoss>> GOD_BOSS = new Ref<>();
    public static final Ref<CreativeModeTab> TAB = new Ref<>();

    static {
        // blocks
        block("gate_hub", GATE_HUB, key -> gate(GateBlock.Kind.HUB, key));
        block("gate_zeus", GATE_ZEUS, key -> gate(GateBlock.Kind.ZEUS, key));
        block("gate_poseidon", GATE_POSEIDON, key -> gate(GateBlock.Kind.POSEIDON, key));
        block("gate_hades", GATE_HADES, key -> gate(GateBlock.Kind.HADES, key));
        block("gate_ascend", GATE_ASCEND, key -> gate(GateBlock.Kind.ASCEND, key));
        block("gate_leave", GATE_LEAVE, key -> gate(GateBlock.Kind.LEAVE, key));
        block("altar_zeus", ALTAR_ZEUS, key -> altar(Line.ZEUS, key));
        block("altar_poseidon", ALTAR_POSEIDON, key -> altar(Line.POSEIDON, key));
        block("altar_hades", ALTAR_HADES, key -> altar(Line.HADES, key));

        // items: codex, compass, 45 god weapons (ordered by line, rank 1 -> 15), then the block items
        item("chaos_codex", CHAOS_CODEX, key -> new CodexItem(new Item.Properties().setId(key).stacksTo(1)));
        item("chaos_compass", CHAOS_COMPASS, key -> new ChaosCompassItem(new Item.Properties().setId(key).stacksTo(1)));
        for (Line line : Line.values()) {
            for (God god : Gods.ofLine(line)) {
                Ref<Item> ref = new Ref<>();
                WEAPONS.put(god.id(), ref);
                item(god.id() + "_weapon", ref, key -> new GodWeaponItem(god, GodWeaponItem.properties(god, key)));
            }
        }
        for (Def<Block> b : BLOCK_DEFS) {
            Ref<Item> ref = new Ref<>();
            BLOCK_ITEMS.put(b.id(), ref);
            item(b.id(), ref, key -> new BlockItem(b.ref().get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));
        }
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

    private static Block gate(GateBlock.Kind kind, ResourceKey<Block> key) {
        return new GateBlock(kind, unbreakable(key).mapColor(MapColor.COLOR_PURPLE).noCollision().lightLevel(s -> 15)
                .sound(SoundType.AMETHYST));
    }

    private static Block altar(Line line, ResourceKey<Block> key) {
        return new AltarBlock(line, unbreakable(key).mapColor(MapColor.STONE).lightLevel(s -> 10).sound(SoundType.STONE));
    }

    // ---- entity -------------------------------------------------------------------------------

    public static EntityType<GodBoss> createGodBossType(ResourceKey<EntityType<?>> key) {
        return EntityType.Builder.<GodBoss>of(GodBoss::new, MobCategory.MONSTER)
                .sized(0.7F, 2.1F)
                .clientTrackingRange(12)
                .fireImmune()
                .build(key);
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
        for (Line line : Line.values()) {
            for (God god : Gods.ofLine(line)) {
                out.add(WEAPONS.get(god.id()).get());
            }
        }
        for (Def<Block> b : BLOCK_DEFS) {
            out.add(BLOCK_ITEMS.get(b.id()).get());
        }
        return out;
    }
}
