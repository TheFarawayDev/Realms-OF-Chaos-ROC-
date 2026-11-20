package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Hermit's private dimension. Every player owns one small walled island in it, far from everyone else's;
 * nobody can walk from one to another. It has a crafting table, a chest and a gate home.
 */
public final class Hermitage {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, RealmsOfChaos.id("hermitage"));
    public static final int SPACING = 512;
    public static final int Y = 64;
    private static final int RADIUS = 7;

    private Hermitage() {}

    public static boolean isIn(Level level) {
        return level.dimension().equals(KEY);
    }

    /** The centre of a player's island; islands are laid out along x. */
    public static BlockPos cell(int index) {
        return new BlockPos(index * SPACING, Y, 0);
    }

    private static void set(ServerLevel level, BlockPos origin, int x, int y, int z, Block block) {
        level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), 2);
    }

    /** Builds one island: a mossy garden under a dome of glass, furnished, with the way out on the south side. */
    public static void buildCell(ServerLevel level, int index) {
        BlockPos o = cell(index);
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > RADIUS + 1.2) continue;
                set(level, o, x, -2, z, Blocks.STONE_BRICKS);
                set(level, o, x, -1, z, Blocks.STONE_BRICKS);
                if (d >= RADIUS) {
                    // the wall: stone, then glass, then an unseen ceiling so nothing gets in or out
                    for (int y = 0; y <= 5; y++) set(level, o, x, y, z, y <= 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.GLASS);
                    set(level, o, x, 6, z, Blocks.BARRIER);
                } else {
                    set(level, o, x, 0, z, d < 1.5 ? Blocks.CHISELED_STONE_BRICKS : ((x + z) & 1) == 0 ? Blocks.MOSS_BLOCK : Blocks.SMOOTH_STONE);
                    for (int y = 1; y <= 5; y++) set(level, o, x, y, z, Blocks.AIR);
                    set(level, o, x, 6, z, Blocks.BARRIER);
                }
            }
        }
        // furnishings round the edge, well away from the arrival spot at the centre
        set(level, o, -5, 1, 0, Blocks.CRAFTING_TABLE);
        set(level, o, 5, 1, 0, Blocks.CHEST);
        set(level, o, 0, 1, -5, Blocks.BOOKSHELF);
        set(level, o, 1, 1, -5, Blocks.BOOKSHELF);
        set(level, o, -1, 1, -5, Blocks.BOOKSHELF);
        set(level, o, 0, 2, -5, Blocks.LANTERN);
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            set(level, o, c[0], 1, c[1], Blocks.MOSSY_STONE_BRICKS);
            set(level, o, c[0], 2, c[1], Blocks.VERDANT_FROGLIGHT);
            set(level, o, c[0], 3, c[1], Blocks.FLOWERING_AZALEA);
        }
        // the way home
        set(level, o, 0, 1, 5, ModContent.GATE_LEAVE.get());
        set(level, o, 0, 2, 5, ModContent.GATE_LEAVE.get());
    }

    /** The Hermit card: into your own island, or - played while there - home again. */
    public static void toggle(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        if (isIn(player.level())) {
            Teleports.leave(player);
            return;
        }
        ServerLevel level = server.getLevel(KEY);
        if (level == null) {
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".unavailable"), 0xFF5555));
            return;
        }
        ProgressData data = ProgressData.get(server);
        int index = data.hermitIndex(player.getUUID());
        String key = "hermitage/cell" + index + "@1";
        if (!data.isBuilt(key)) {
            buildCell(level, index);
            data.markBuilt(key);
        }
        Teleports.rememberReturn(player, server);
        BlockPos o = cell(index);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(level, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
        Hud.title(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".hermit.enter"), 0xB9C6FF),
                Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".hermit.sub"), 0xCCCCCC));
    }
}
