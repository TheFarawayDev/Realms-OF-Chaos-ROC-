package com.thefarawaydev.realmsofchaos.forge;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.command.RocCommands;
import com.thefarawaydev.realmsofchaos.dev.SelfTest;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.world.RealmSafety;
import com.thefarawaydev.realmsofchaos.world.Welcome;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/** Forge entrypoint for Realms of Chaos by TheFarawayDev. */
@Mod(RealmsOfChaos.MOD_ID)
public final class RealmsOfChaosForge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RealmsOfChaos.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RealmsOfChaos.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RealmsOfChaos.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RealmsOfChaos.MOD_ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RealmsOfChaos.MOD_ID);

    public RealmsOfChaosForge(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();

        for (ModContent.Def<Block> def : ModContent.BLOCK_DEFS) {
            RegistryObject<Block> ro = BLOCKS.register(def.id(), () -> def.factory().apply(BLOCKS.key(def.id())));
            def.ref().bind(ro);
        }
        for (ModContent.Def<Item> def : ModContent.ITEM_DEFS) {
            RegistryObject<Item> ro = ITEMS.register(def.id(), () -> def.factory().apply(ITEMS.key(def.id())));
            def.ref().bind(ro);
        }
        RegistryObject<EntityType<?>> boss = ENTITIES.register("god_boss", () -> ModContent.createGodBossType(ENTITIES.key("god_boss")));
        ModContent.GOD_BOSS.bind(() -> {
            @SuppressWarnings("unchecked")
            EntityType<GodBoss> type = (EntityType<GodBoss>) boss.get();
            return type;
        });
        RegistryObject<EntityType<?>> sentinel = ENTITIES.register("sentinel", () -> ModContent.createSentinelType(ENTITIES.key("sentinel")));
        ModContent.SENTINEL.bind(() -> {
            @SuppressWarnings("unchecked")
            EntityType<com.thefarawaydev.realmsofchaos.entity.Sentinel> type = (EntityType<com.thefarawaydev.realmsofchaos.entity.Sentinel>) sentinel.get();
            return type;
        });
        RegistryObject<EntityType<?>> projectile = ENTITIES.register("relic_projectile", () -> ModContent.createProjectileType(ENTITIES.key("relic_projectile")));
        ModContent.RELIC_PROJECTILE.bind(() -> {
            @SuppressWarnings("unchecked")
            EntityType<com.thefarawaydev.realmsofchaos.entity.RelicProjectile> type = (EntityType<com.thefarawaydev.realmsofchaos.entity.RelicProjectile>) projectile.get();
            return type;
        });
        RegistryObject<CreativeModeTab> tab = TABS.register("main", () -> CreativeModeTab.builder()
                .title(ModContent.tabTitle())
                .icon(ModContent::tabIcon)
                .displayItems((params, output) -> ModContent.tabItems().forEach(output::accept))
                .build());
        ModContent.TAB.bind(tab);

        for (com.thefarawaydev.realmsofchaos.ui.Skin.Panel panel : com.thefarawaydev.realmsofchaos.ui.Skin.Panel.values()) {
            RegistryObject<net.minecraft.world.inventory.MenuType<?>> menu = MENUS.register(panel.id(), () -> ModContent.createMenuType(panel));
            ModContent.MENUS.get(panel.id()).bind(() -> {
                @SuppressWarnings("unchecked")
                net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.ChestMenu> type = (net.minecraft.world.inventory.MenuType<net.minecraft.world.inventory.ChestMenu>) menu.get();
                return type;
            });
        }
        MENUS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);

        EntityAttributeCreationEvent.BUS.addListener(event -> {
            event.put(ModContent.GOD_BOSS.get(), GodBoss.createAttributes().build());
            event.put(ModContent.SENTINEL.get(), com.thefarawaydev.realmsofchaos.entity.Sentinel.createAttributes().build());
        });
        RegisterCommandsEvent.BUS.addListener(event -> RocCommands.register(event.getDispatcher()));

        // first-join thank-you, and the once-a-second void / stuck rescue inside the realms
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) Welcome.onJoin(player);
        });
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> RealmSafety.tick(event.server()));
        // kills made with a relic weapon in hand give that weapon experience
        net.minecraftforge.event.entity.living.LivingDeathEvent.BUS.addListener(event -> {
            com.thefarawaydev.realmsofchaos.item.GodWeaponItem.onKill(event.getEntity(), event.getSource());
            return false;
        });
        if (SelfTest.enabled()) {
            ServerStartedEvent.BUS.addListener(event -> SelfTest.run(event.getServer()));
        }

        RealmsOfChaos.version(context.getContainer().getModInfo().getVersion().toString());
        LOGGER.info("{} {} by {} loaded: 45 gods, 3 realms, 1 hub.", RealmsOfChaos.MOD_NAME, RealmsOfChaos.version(), RealmsOfChaos.AUTHOR);
    }
}
