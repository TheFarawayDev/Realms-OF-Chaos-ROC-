package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.HudFeed;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;

/**
 * The mod's music. The server says which track belongs where the player is - a trial, a passage, the hub, the
 * Hermitage, a tower, the Ruined Gate, a citadel (see MusicCues) - and that track plays in place of the game's own,
 * coming back after a quiet when it ends. Where the server names none, the game's own music plays. (With no word
 * from the server at all, the realm's dimension decides, as before.)
 */
public final class RocMusic {
    /** The quiet between one playing and the next, in ticks. */
    public static final int GAP = 20 * 40;
    private static final Map<String, Music> MUSIC = new HashMap<>();
    private static String playing;
    private static int quiet;

    private RocMusic() {}

    /** Which track belongs to a dimension of the mod's own, when the server has not said. */
    static String trackFor(String dimension) {
        return switch (dimension) {
            case "hub" -> "hub";
            case "hermitage" -> "hermitage";
            case "olympus" -> "olympus";
            case "abyss" -> "abyss";
            case "underworld" -> "underworld";
            default -> null;
        };
    }

    private static Music music(String track) {
        return MUSIC.computeIfAbsent(track, t -> new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModContent.sound("music." + t)), GAP, GAP * 2, true));
    }

    /** The track the server has named, "" for the game's own music, or null if it has said nothing. */
    private static String told(Minecraft mc) {
        var board = mc.level.getScoreboard();
        Objective feed = board.getObjective(HudFeed.OBJECTIVE);
        if (feed == null) return null;
        var info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(HudFeed.MUSIC), feed);
        if (info == null) return null;
        int v = info.value();
        return v <= 0 || v > ModContent.TRACKS.size() ? "" : ModContent.TRACKS.get(v - 1);
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        String track = told(mc);
        if (track == null) {
            var id = mc.level.dimension().identifier();
            track = id.getNamespace().equals(RealmsOfChaos.MOD_ID) ? trackFor(id.getPath()) : null;
        } else if (track.isEmpty()) {
            track = null;
        }
        var manager = mc.getMusicManager();
        if (track == null) {
            if (playing != null) {
                manager.stopPlaying(music(playing));
                playing = null;
            }
            return;
        }
        Music m = music(track);
        if (!track.equals(playing)) {
            // somewhere new: its music begins at once
            manager.stopPlaying();
            manager.startPlaying(m);
            playing = track;
            quiet = 0;
            return;
        }
        if (manager.isPlayingMusic(m)) {
            quiet = 0;
            return;
        }
        // between playings the game's own music is kept away; then the track begins again
        if (manager.getCurrentMusicTranslationKey() != null) manager.stopPlaying();
        if (++quiet >= GAP) {
            manager.startPlaying(m);
            quiet = 0;
        }
    }
}
