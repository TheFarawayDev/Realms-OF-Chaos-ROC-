package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The field network: the blocks players set down in the world to find their way. A Relay Tower Core steadies every
 * compass scan near it. A Signal Jammer spoils the scans of everyone but its owner and their party. A Waygate
 * Obelisk is a place to travel from and to. Where each one stands is kept with the world, so the network survives
 * restarts; a block that is gone is forgotten the next time anything looks for it.
 */
public final class Network {
    public enum Kind {
        RELAY(96), JAMMER(64), WAYGATE(0);

        /** How far its effect reaches, in blocks along the ground. */
        public final int range;

        Kind(int range) {
            this.range = range;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Block block() {
            return switch (this) {
                case RELAY -> ModContent.RELAY_CORE.get();
                case JAMMER -> ModContent.SIGNAL_JAMMER.get();
                case WAYGATE -> ModContent.WAYGATE_OBELISK.get();
            };
        }
    }

    /** One block of the network. */
    public record Node(Kind kind, String dimension, BlockPos pos, UUID owner) {
        String key() {
            return prefix(kind) + dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ() + "|" + owner;
        }

        /** A short name that stays the same for as long as the block stands: used in commands. */
        public String code() {
            return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        }

        public ServerLevel level(MinecraftServer server) {
            Identifier id = Identifier.tryParse(dimension);
            return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        }
    }

    private Network() {}

    private static String prefix(Kind kind) {
        return "net|" + kind.id() + "|";
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    private static Node parse(Kind kind, String key) {
        String[] f = key.split("\\|");
        if (f.length != 7) return null;
        try {
            return new Node(kind, f[2], new BlockPos(Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5])), UUID.fromString(f[6]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Every block of a kind, in every dimension. */
    public static List<Node> nodes(MinecraftServer server, Kind kind) {
        List<Node> out = new ArrayList<>();
        for (String key : ProgressData.get(server).keys(prefix(kind))) {
            Node node = parse(kind, key);
            if (node != null) out.add(node);
        }
        return out;
    }

    public static Node at(ServerLevel level, BlockPos pos, Kind kind) {
        String dimension = dimension(level);
        for (Node node : nodes(level.getServer(), kind)) {
            if (node.pos().equals(pos) && node.dimension().equals(dimension)) return node;
        }
        return null;
    }

    /** Records a block that has just been set down. Whatever was recorded on that spot before is replaced. */
    public static Node register(ServerLevel level, BlockPos pos, Kind kind, UUID owner) {
        unregister(level, pos, kind);
        Node node = new Node(kind, dimension(level), pos.immutable(), owner);
        ProgressData.get(level.getServer()).markBuilt(node.key());
        return node;
    }

    public static boolean unregister(ServerLevel level, BlockPos pos, Kind kind) {
        Node node = at(level, pos, kind);
        if (node == null) return false;
        ProgressData data = ProgressData.get(level.getServer());
        data.unmarkBuilt(node.key());
        if (kind == Kind.WAYGATE) {
            // nobody can travel to an obelisk that is gone
            for (String seen : data.keys("seen|")) {
                if (seen.endsWith("|" + node.dimension() + "|" + node.code())) data.unmarkBuilt(seen);
            }
        }
        return true;
    }

    /** True if the block is still there - or cannot be checked right now because its chunk is not loaded. */
    private static boolean stands(ServerLevel level, Node node) {
        return !level.isLoaded(node.pos()) || level.getBlockState(node.pos()).is(node.kind().block());
    }

    /**
     * The blocks of a kind within {@code range} blocks (along the ground) of a place in this dimension. One that
     * turns out to be gone is forgotten on the way.
     */
    public static List<Node> near(ServerLevel level, Kind kind, double x, double z, double range) {
        String dimension = dimension(level);
        List<Node> out = new ArrayList<>();
        for (Node node : nodes(level.getServer(), kind)) {
            if (!node.dimension().equals(dimension)) continue;
            double dx = node.pos().getX() + 0.5 - x, dz = node.pos().getZ() + 0.5 - z;
            if (dx * dx + dz * dz > range * range) continue;
            if (!stands(level, node)) {
                unregister(level, node.pos(), kind);
                continue;
            }
            out.add(node);
        }
        return out;
    }

    // ---- waygates a player has found -------------------------------------------------------------------------

    private static String seenKey(UUID player, Node node) {
        return "seen|" + player + "|" + node.dimension() + "|" + node.code();
    }

    /** Touching an obelisk is what makes it a place you can travel to. @return true the first time. */
    public static boolean discover(ServerPlayer player, Node node) {
        ProgressData data = ProgressData.get(player.level().getServer());
        if (data.isBuilt(seenKey(player.getUUID(), node))) return false;
        data.markBuilt(seenKey(player.getUUID(), node));
        return true;
    }

    public static boolean discovered(ServerPlayer player, Node node) {
        return ProgressData.get(player.level().getServer()).isBuilt(seenKey(player.getUUID(), node));
    }

    /** Every obelisk this player has touched that still stands, nearest first within their own dimension. */
    public static List<Node> discovered(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        List<Node> out = new ArrayList<>();
        String here = dimension(player.level());
        for (Node node : nodes(server, Kind.WAYGATE)) {
            if (!discovered(player, node)) continue;
            ServerLevel level = node.level(server);
            if (level == null) continue;
            if (!stands(level, node)) {
                unregister(level, node.pos(), Kind.WAYGATE);
                continue;
            }
            out.add(node);
        }
        out.sort((a, b) -> {
            boolean ah = a.dimension().equals(here), bh = b.dimension().equals(here);
            if (ah != bh) return ah ? -1 : 1;
            return Double.compare(a.pos().distSqr(player.blockPosition()), b.pos().distSqr(player.blockPosition()));
        });
        return out;
    }
}
