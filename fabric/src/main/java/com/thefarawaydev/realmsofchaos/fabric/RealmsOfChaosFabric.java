package com.thefarawaydev.realmsofchaos.fabric;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.command.RocCommands;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric entrypoint for Realms of Chaos by TheFarawayDev. */
public class RealmsOfChaosFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(RealmsOfChaos.MOD_NAME);

    @Override
    public void onInitialize() {
        for (ModContent.Def<net.minecraft.world.level.block.Block> def : ModContent.BLOCK_DEFS) {
            Identifier id = RealmsOfChaos.id(def.id());
            var block = def.factory().apply(ResourceKey.create(Registries.BLOCK, id));
            Registry.register(BuiltInRegistries.BLOCK, id, block);
            def.ref().bind(() -> block);
        }
        for (ModContent.Def<net.minecraft.world.item.Item> def : ModContent.ITEM_DEFS) {
            Identifier id = RealmsOfChaos.id(def.id());
            var item = def.factory().apply(ResourceKey.create(Registries.ITEM, id));
            Registry.register(BuiltInRegistries.ITEM, id, item);
            def.ref().bind(() -> item);
        }

        EntityType<GodBoss> bossType = ModContent.createGodBossType(ModContent.godBossKey());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, RealmsOfChaos.id("god_boss"), bossType);
        ModContent.GOD_BOSS.bind(() -> bossType);
        FabricDefaultAttributeRegistry.register(bossType, GodBoss.createAttributes());

        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(ModContent.tabTitle())
                .icon(ModContent::tabIcon)
                .displayItems((params, output) -> ModContent.tabItems().forEach(output::accept))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, RealmsOfChaos.id("main"), tab);
        ModContent.TAB.bind(() -> tab);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RocCommands.register(dispatcher));

        LOGGER.info("{} by {} loaded: 45 gods, 3 realms, 1 hub.", RealmsOfChaos.MOD_NAME, RealmsOfChaos.AUTHOR);
    }
}
