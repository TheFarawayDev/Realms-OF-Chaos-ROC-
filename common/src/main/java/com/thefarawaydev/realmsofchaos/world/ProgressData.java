package com.thefarawaydev.realmsofchaos.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Per-world persistent state: which gods each player has defeated and which realms have been built. */
public class ProgressData extends SavedData {
    public static final Codec<ProgressData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("defeated", Map.of()).forGetter(ProgressData::exportDefeated),
            Codec.STRING.listOf().optionalFieldOf("built", List.of()).forGetter(d -> new ArrayList<>(d.built)),
            Codec.STRING.listOf().optionalFieldOf("welcomed", List.of()).forGetter(d -> new ArrayList<>(d.welcomed))
    ).apply(i, ProgressData::new));

    public static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(
            RealmsOfChaos.id("progress"), ProgressData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, Set<String>> defeated = new HashMap<>();
    private final Set<String> built = new HashSet<>();
    private final Set<String> welcomed = new HashSet<>();

    public ProgressData() {}

    private ProgressData(Map<String, List<String>> defeated, List<String> built, List<String> welcomed) {
        defeated.forEach((k, v) -> this.defeated.put(k, new HashSet<>(v)));
        this.built.addAll(built);
        this.welcomed.addAll(welcomed);
    }

    public static ProgressData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<String, List<String>> exportDefeated() {
        Map<String, List<String>> out = new HashMap<>();
        defeated.forEach((k, v) -> out.put(k, new ArrayList<>(v)));
        return out;
    }

    public boolean isDefeated(UUID player, God god) {
        return defeated.getOrDefault(player.toString(), Set.of()).contains(god.id());
    }

    public boolean markDefeated(UUID player, God god) {
        boolean added = defeated.computeIfAbsent(player.toString(), k -> new HashSet<>()).add(god.id());
        if (added) setDirty();
        return added;
    }

    public int count(UUID player, Line line) {
        int n = 0;
        for (God g : Gods.ofLine(line)) {
            if (isDefeated(player, g)) n++;
        }
        return n;
    }

    /** The rank the player must fight next in this line (15 first, 1 last), or 0 when the line is complete. */
    public int nextRank(UUID player, Line line) {
        for (int r = Line.RANKS; r >= 1; r--) {
            if (!isDefeated(player, Gods.get(line, r))) return r;
        }
        return 0;
    }

    public int total(UUID player) {
        return defeated.getOrDefault(player.toString(), Set.of()).size();
    }

    /** Build keys carry the layout version, so realms are rebuilt when the mod ships a new design. */
    public boolean isBuilt(String key) {
        return built.contains(key);
    }

    public void markBuilt(String key) {
        if (built.add(key)) setDirty();
    }

    /** @return true the first time this player is seen in this world. */
    public boolean markWelcomed(UUID player) {
        boolean first = welcomed.add(player.toString());
        if (first) setDirty();
        return first;
    }

    public void reset(UUID player) {
        if (defeated.remove(player.toString()) != null) setDirty();
    }
}
