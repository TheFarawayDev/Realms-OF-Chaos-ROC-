package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.Sentinel;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.item.Panoply;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Words;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * The lesser gods: twelve who have no floor of a tower to their name and hold the arenas of the passages instead.
 * Four to a tower, met in turn as the passages are climbed - Strife, Retribution, Fortune and the North Wind in
 * the tower of Zeus; four of the old sea in Poseidon's; the Oath, Forgetting, the Death-Mist and Old Age in Hades'.
 *
 * <p>A lesser god is an arena's champion crowned: named, half as big again in health, a little larger. The first
 * time each falls to a player it lets fall the piece of the panoply it keeps; after that, now and then.
 */
public final class Lesser {
    public enum God {
        ERIS(Line.ZEUS, Panoply.Piece.GIRDLE_OF_HIPPOLYTA), NEMESIS(Line.ZEUS, Panoply.Piece.AEGIS_CUIRASS),
        TYCHE(Line.ZEUS, Panoply.Piece.TALARIA), BOREAS(Line.ZEUS, Panoply.Piece.HELM_OF_ATHENA),
        GLAUCUS(Line.POSEIDON, Panoply.Piece.STYX_TREADS), LEUCOTHEA(Line.POSEIDON, Panoply.Piece.TALARIA),
        PHORCYS(Line.POSEIDON, Panoply.Piece.NEMEAN_PELT), CETO(Line.POSEIDON, Panoply.Piece.GREAVES_OF_ACHILLES),
        STYX(Line.HADES, Panoply.Piece.STYX_TREADS), LETHE(Line.HADES, Panoply.Piece.HELM_OF_HADES),
        ACHLYS(Line.HADES, Panoply.Piece.GIRDLE_OF_HIPPOLYTA), GERAS(Line.HADES, Panoply.Piece.NEMEAN_PELT);

        public final Line line;
        /** The piece of the panoply it keeps. */
        public final Panoply.Piece keeps;

        God(Line line, Panoply.Piece keeps) {
            this.line = line;
            this.keeps = keeps;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component displayName() {
            return Component.translatable("lesser." + RealmsOfChaos.MOD_ID + "." + id());
        }
    }

    public static final String TAG = "roc_lesser/";
    /** After the first time, a lesser god lets its piece fall this often: one time in so many. */
    public static final int GIFT_ODDS = 8;

    private Lesser() {}

    /** Which lesser god holds the arenas of a passage: the four of its tower, in turn. */
    public static God of(Line line, int passage) {
        int turn = Math.floorMod(passage - 1, 4), seen = 0;
        for (God god : God.values()) {
            if (god.line != line) continue;
            if (seen++ == turn) return god;
        }
        return God.ERIS;
    }

    /** Which lesser god a sentinel is, or null if it is only a sentinel. */
    public static God of(Sentinel mob) {
        for (String tag : mob.entityTags()) {
            if (!tag.startsWith(TAG)) continue;
            for (God god : God.values()) if (god.id().equals(tag.substring(TAG.length()))) return god;
        }
        return null;
    }

    /** Crowns an arena's champion: it is the lesser god of this passage. */
    public static God crown(Sentinel mob, Passages.Plan plan) {
        God god = of(plan.line, plan.index);
        mob.addTag(TAG + god.id());
        mob.setCustomName(Hud.colored(god.displayName(), plan.line.color()));
        mob.setCustomNameVisible(true);
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * 1.5);
            mob.setHealth(mob.getMaxHealth());
        }
        var scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(scale.getBaseValue() * 1.15);
        return god;
    }

    private static String key(ServerPlayer player, God god) {
        return "lesser/" + player.getUUID() + "/" + god.id();
    }

    public static boolean felled(ServerPlayer player, God god) {
        return ProgressData.get(player.level().getServer()).isBuilt(key(player, god));
    }

    /** A sentinel has fallen to a player: if it was a lesser god, it is recorded, and may let its piece fall. */
    public static ItemStack fallen(Sentinel mob, ServerPlayer killer) {
        God god = of(mob);
        if (god == null || !(mob.level() instanceof ServerLevel level)) return ItemStack.EMPTY;
        ProgressData data = ProgressData.get(level.getServer());
        boolean first = !data.isBuilt(key(killer, god));
        data.markBuilt(key(killer, god));
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".lesser.";
        if (!first && level.getRandom().nextInt(GIFT_ODDS) != 0) {
            Hud.actionBar(killer, Hud.colored(Component.translatable(hud + "fallen", god.displayName()), god.line.color()));
            return ItemStack.EMPTY;
        }
        ItemStack gift = god.keeps.stack();
        mob.spawnAtLocation(level, gift.copy());
        mob.spawnAtLocation(level, new ItemStack(ModContent.materialItem(god.line, Math.max(1, Math.min(3, god.keeps.tier))), 3));
        Cards.notice(killer, Words.of(hud + "fallen", god.displayName()), Words.of(hud + "gift", god.displayName(), gift.getHoverName()));
        return gift;
    }
}
