package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;

/**
 * The music of the realms: in the hub, and in each tower, the mod's own track plays in place of the game's - and
 * after it ends, a quiet, then again. Outside the realms the game's own music comes back.
 */
public final class RocMusic {
    /** The quiet between one playing and the next, in ticks. */
    public static final int GAP = 20 * 40;
    private static final Map<String, Music> MUSIC = new HashMap<>();
    private static String playing;
    private static int quiet;

    private RocMusic() {}

    /** Which track belongs where: a realm's own, or none. */
    static String trackFor(String dimension) {
        return switch (dimension) {
            case "hub", "hermitage" -> "music.hub";
            case "olympus" -> "music.olympus";
            case "abyss" -> "music.abyss";
            case "underworld" -> "music.underworld";
            default -> null;
        };
    }

    private static Music music(String track) {
        return MUSIC.computeIfAbsent(track, t -> new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModContent.sound(t)), GAP, GAP * 2, true));
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        var id = mc.level.dimension().identifier();
        String track = id.getNamespace().equals(RealmsOfChaos.MOD_ID) ? trackFor(id.getPath()) : null;
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
            // arriving in a realm: its music begins at once
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
        // between playings the game's own music is kept away; then the realm's begins again
        if (manager.getCurrentMusicTranslationKey() != null) manager.stopPlaying();
        if (++quiet >= GAP) {
            manager.startPlaying(m);
            quiet = 0;
        }
    }
}
