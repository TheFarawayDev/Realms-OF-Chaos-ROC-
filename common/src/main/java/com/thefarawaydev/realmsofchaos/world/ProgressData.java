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

/** Per-world persistent state: defeated gods per player, built realm pieces, and where to send players who leave. */
public class ProgressData extends SavedData {
    /** Where a player stood before entering the realms, so the exit gate can put them back. */
    public record ReturnPoint(String dimension, double x, double y, double z, float yaw) {
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z),
                Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(ReturnPoint::yaw)
        ).apply(i, ReturnPoint::new));
    }

    public static final Codec<ProgressData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("defeated", Map.of()).forGetter(ProgressData::exportDefeated),
            Codec.STRING.listOf().optionalFieldOf("built", List.of()).forGetter(d -> new ArrayList<>(d.built)),
            Codec.STRING.listOf().optionalFieldOf("welcomed", List.of()).forGetter(d -> new ArrayList<>(d.welcomed)),
            Codec.unboundedMap(Codec.STRING, ReturnPoint.CODEC).optionalFieldOf("returns", Map.of()).forGetter(d -> new HashMap<>(d.returns)),
            Codec.STRING.listOf().optionalFieldOf("shifted", List.of()).forGetter(d -> new ArrayList<>(d.shifted)),
            Codec.STRING.listOf().optionalFieldOf("hermits", List.of()).forGetter(d -> new ArrayList<>(d.hermits))
    ).apply(i, ProgressData::new));

    public static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(
            RealmsOfChaos.id("progress"), ProgressData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, Set<String>> defeated = new HashMap<>();
    private final Set<String> built = new HashSet<>();
    private final Set<String> welcomed = new HashSet<>();
    private final Map<String, ReturnPoint> returns = new HashMap<>();
    private final Set<String> shifted = new HashSet<>();
    /** Players who own an island in the Hermitage; a player's position in this list is their island's number. */
    private final List<String> hermits = new ArrayList<>();

    public ProgressData() {}

    private ProgressData(Map<String, List<String>> defeated, List<String> built, List<String> welcomed, Map<String, ReturnPoint> returns,
                         List<String> shifted, List<String> hermits) {
        this(defeated, built, welcomed, returns);
        this.shifted.addAll(shifted);
        this.hermits.addAll(hermits);
    }

    /** The number of this player's Hermitage island, assigning the next free one the first time. */
    public int hermitIndex(UUID player) {
        int index = hermits.indexOf(player.toString());
        if (index < 0) {
            hermits.add(player.toString());
            index = hermits.size() - 1;
            setDirty();
        }
        return index;
    }

    /** Floors whose terrain is currently away from their own design (see FloorShifts). */
    public boolean isShifted(String key) {
        return shifted.contains(key);
    }

    public void markShifted(String key) {
        if (shifted.add(key)) setDirty();
    }

    public void clearShifted(String key) {
        if (shifted.remove(key)) setDirty();
    }

    private ProgressData(Map<String, List<String>> defeated, List<String> built, List<String> welcomed, Map<String, ReturnPoint> returns) {
        defeated.forEach((k, v) -> this.defeated.put(k, new HashSet<>(v)));
        this.built.addAll(built);
        this.welcomed.addAll(welcomed);
        this.returns.putAll(returns);
    }

    public ReturnPoint returnPoint(UUID player) {
        return returns.get(player.toString());
    }

    public void setReturnPoint(UUID player, ReturnPoint point) {
        returns.put(player.toString(), point);
        setDirty();
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

    /** The first build key starting with {@code prefix}, or null: for keys that carry a value after their name. */
    public String findBuilt(String prefix) {
        for (String key : built) if (key.startsWith(prefix)) return key;
        return null;
    }

    public void unmarkBuilt(String key) {
        if (built.remove(key)) setDirty();
    }

    /** Forgets every build key that starts with {@code prefix}. */
    public void unmarkPrefix(String prefix) {
        if (built.removeIf(key -> key.startsWith(prefix))) setDirty();
    }

    public void markBuilt(String key) {
        if (built.add(key)) setDirty();
    }

    private static String passedKey(UUID player, Line line, int passage) {
        return "passed/" + player + "/" + line.id() + "/" + passage;
    }

    /** True once the player has walked out of this passage's gate (passage n lies between floors n and n + 1). */
    public boolean hasPassed(UUID player, Line line, int passage) {
        return built.contains(passedKey(player, line, passage));
    }

    /** @return true the first time. */
    public boolean markPassed(UUID player, Line line, int passage) {
        boolean first = built.add(passedKey(player, line, passage));
        if (first) setDirty();
        return first;
    }

    /** @return true the first time this player is seen in this world. */
    public boolean markWelcomed(UUID player) {
        boolean first = welcomed.add(player.toString());
        if (first) setDirty();
        return first;
    }

    public void reset(UUID player) {
        if (defeated.remove(player.toString()) != null) setDirty();
        if (built.removeIf(key -> key.startsWith("passed/" + player + "/"))) setDirty();
    }
}
