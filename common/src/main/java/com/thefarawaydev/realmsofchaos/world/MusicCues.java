package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.ui.HudFeed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Which of the mod's tracks belongs where a player is: a god's trial above all, then a passage, then the realm - the
 * hub, the Hermitage, each tower - and in the Overworld the Ruined Gate and the citadels. The server works it out
 * every second and tells the player's game through the HUD feed; the game plays it (see the client's RocMusic).
 * Anywhere else the game's own music plays.
 */
public final class MusicCues {
    /** How near the Ruined Gate, and how far out from a citadel's walls, its music is heard. */
    public static final int GATE_REACH = 48, CITADEL_REACH = 40;

    private MusicCues() {}

    /** The track for where a player stands, or null for the game's own music. */
    public static String cue(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        if (!level.getEntitiesOfClass(GodBoss.class, player.getBoundingBox().inflate(40), GodBoss::inTrial).isEmpty()) return "trial";
        if (Hermitage.isIn(level)) return "hermitage";
        Realm realm = Realm.of(level.dimension());
        if (realm != null) {
            if (Passages.locate(player) != null) return "passage";
            if (realm.line() == null) return "hub";
            return switch (realm.line()) {
                case ZEUS -> "olympus";
                case POSEIDON -> "abyss";
                case HADES -> "underworld";
            };
        }
        if (level.dimension() != Level.OVERWORLD) return null;
        long seed = level.getSeed();
        Ruins.Site gate = Ruins.site(seed, Ruins.Kind.GATE);
        if (near(player, gate, GATE_REACH)) return "gate";
        for (Ruins.Kind kind : new Ruins.Kind[]{Ruins.Kind.SKY, Ruins.Kind.SEA, Ruins.Kind.SHADE}) {
            if (near(player, Ruins.site(seed, kind), Citadels.HALF + CITADEL_REACH)) return "citadel";
        }
        return null;
    }

    private static boolean near(ServerPlayer player, Ruins.Site site, int reach) {
        double dx = site.x() + 0.5 - player.getX(), dz = site.z() + 0.5 - player.getZ();
        return dx * dx + dz * dz <= (double) reach * reach;
    }

    /** Tells a player's game which track to play: its place in {@link ModContent#TRACKS} plus one, or nothing. */
    public static void tell(ServerPlayer player) {
        String cue = cue(player);
        HudFeed.set(player, HudFeed.MUSIC, cue == null ? 0 : ModContent.TRACKS.indexOf(cue) + 1, null);
    }
}
