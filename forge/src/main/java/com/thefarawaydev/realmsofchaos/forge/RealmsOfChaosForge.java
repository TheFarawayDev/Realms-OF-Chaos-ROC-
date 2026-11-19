package com.thefarawaydev.realmsofchaos.forge;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.command.RocCommands;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
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
        RegistryObject<CreativeModeTab> tab = TABS.register("main", () -> CreativeModeTab.builder()
                .title(ModContent.tabTitle())
                .icon(ModContent::tabIcon)
                .displayItems((params, output) -> ModContent.tabItems().forEach(output::accept))
                .build());
        ModContent.TAB.bind(tab);

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);

        EntityAttributeCreationEvent.BUS.addListener(event -> event.put(ModContent.GOD_BOSS.get(), GodBoss.createAttributes().build()));
        RegisterCommandsEvent.BUS.addListener(event -> RocCommands.register(event.getDispatcher()));

        LOGGER.info("{} by {} loaded: 45 gods, 3 realms, 1 hub.", RealmsOfChaos.MOD_NAME, RealmsOfChaos.AUTHOR);
    }
}
