package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The "Realms of Chaos" advancement tab: one root plus one advancement per god, granted from code. */
public final class RocAdvancements {
    public static final Identifier ROOT = RealmsOfChaos.id("root");

    private RocAdvancements() {}

    public static Identifier idOf(God god) {
        return RealmsOfChaos.id(god.line().id() + "/" + god.id());
    }

    public static boolean award(ServerPlayer player, Identifier id) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        AdvancementHolder holder = server.getAdvancements().get(id);
        return holder != null && player.getAdvancements().award(holder, "done");
    }

    public static void awardGod(ServerPlayer player, God god) {
        award(player, idOf(god));
    }

    /** Brings the tab in line with saved progress (covers worlds played before the tab existed). */
    public static void sync(ServerPlayer player, ProgressData data) {
        award(player, ROOT);
        for (God god : Gods.ALL) {
            if (data.isDefeated(player.getUUID(), god)) awardGod(player, god);
        }
    }
}
