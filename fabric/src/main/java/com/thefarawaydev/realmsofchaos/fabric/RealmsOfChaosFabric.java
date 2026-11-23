package com.thefarawaydev.realmsofchaos.fabric;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.command.RocCommands;
import com.thefarawaydev.realmsofchaos.dev.SelfTest;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.world.RealmSafety;
import com.thefarawaydev.realmsofchaos.world.Welcome;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

        for (com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel : com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values()) {
            var menuType = ModContent.createMenuType(panel);
            Registry.register(BuiltInRegistries.MENU, RealmsOfChaos.id(panel.id()), menuType);
            ModContent.MENUS.get(panel.id()).bind(() -> menuType);
        }

        var pageType = ModContent.createPageType();
        Registry.register(BuiltInRegistries.MENU, RealmsOfChaos.id("page"), pageType);
        ModContent.PAGE.bind(() -> pageType);

        EntityType<GodBoss> bossType = ModContent.createGodBossType(ModContent.godBossKey());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, RealmsOfChaos.id("god_boss"), bossType);
        ModContent.GOD_BOSS.bind(() -> bossType);
        FabricDefaultAttributeRegistry.register(bossType, GodBoss.createAttributes());

        var sentinelType = ModContent.createSentinelType(ModContent.sentinelKey());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, RealmsOfChaos.id("sentinel"), sentinelType);
        ModContent.SENTINEL.bind(() -> sentinelType);
        FabricDefaultAttributeRegistry.register(sentinelType, com.thefarawaydev.realmsofchaos.entity.Sentinel.createAttributes());

        var projectileType = ModContent.createProjectileType(ModContent.projectileKey());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, RealmsOfChaos.id("relic_projectile"), projectileType);
        ModContent.RELIC_PROJECTILE.bind(() -> projectileType);

        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(ModContent.tabTitle())
                .icon(ModContent::tabIcon)
                .displayItems((params, output) -> ModContent.tabItems().forEach(output::accept))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, RealmsOfChaos.id("main"), tab);
        ModContent.TAB.bind(() -> tab);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RocCommands.register(dispatcher));

        // first-join thank-you, and the once-a-second void / stuck rescue inside the realms
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Welcome.onJoin(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(RealmSafety::tick);
        // kills made with a relic weapon in hand give that weapon experience
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                com.thefarawaydev.realmsofchaos.item.GodWeaponItem::onKill);
        if (SelfTest.enabled()) {
            ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
        }

        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(RealmsOfChaos.MOD_ID)
                .ifPresent(mod -> RealmsOfChaos.version(mod.getMetadata().getVersion().getFriendlyString()));
        LOGGER.info("{} {} by {} loaded: 45 gods, 3 realms, 1 hub.", RealmsOfChaos.MOD_NAME, RealmsOfChaos.version(), RealmsOfChaos.AUTHOR);
    }
}
