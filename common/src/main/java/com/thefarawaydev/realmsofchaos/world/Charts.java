package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The Passage Chart: a map with fog of war. It is an ordinary map item - it can be held, framed, looked at in the
 * off hand - but the mod draws it. A level starts as blank parchment; each room is inked in when its owner first
 * walks into it, with its doorways, what kind of room it is, and whether its seal still holds. The owner's own
 * position is a white mark that moves with them. Every player has one chart of their own, redrawn whenever they
 * come to a new level; with the Cartographer skill it also sketches the rooms next to those already seen.
 */
public final class Charts {
    private static final String TAG = "roc_chart";
    /** Pixels to a room, and where the drawing starts so that the largest level sits in the middle of the sheet. */
    public static final int CELL = 18, SIZE = 128;

    private Charts() {}

    private static byte ink(MapColor color, MapColor.Brightness brightness) {
        return color.getPackedId(brightness);
    }

    /** This player's chart map, made the first time it is asked for. */
    public static MapId id(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ProgressData data = ProgressData.get(server);
        String key = "chart/" + player.getUUID();
        int stored = data.counter(key);
        ServerLevel overworld = server.overworld();
        if (stored > 0 && overworld.getMapData(new MapId(stored - 1)) != null) return new MapId(stored - 1);
        MapId id = overworld.getFreeMapId();
        overworld.setMapData(id, MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, overworld.dimension()).locked());
        data.setCounter(key, id.id() + 1);
        return id;
    }

    public static MapItemSavedData data(ServerPlayer player) {
        return player.level().getServer().overworld().getMapData(id(player));
    }

    public static boolean isChart(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.FILLED_MAP) && data != null && data.copyTag().getBooleanOr(TAG, false);
    }

    public static ItemStack stack(ServerPlayer player) {
        ItemStack chart = new ItemStack(Items.FILLED_MAP);
        chart.set(DataComponents.MAP_ID, id(player));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        chart.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        chart.set(DataComponents.CUSTOM_NAME, Component.translatable("item." + RealmsOfChaos.MOD_ID + ".passage_chart").withStyle(Style.EMPTY.withItalic(false).withColor(0xF5B82E)));
        return chart;
    }

    public static boolean holds(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (isChart(player.getInventory().getItem(i))) return true;
        return false;
    }

    /** Hands a player their chart if they are not carrying it. @return true if one was given. */
    public static boolean give(ServerPlayer player) {
        if (holds(player)) return false;
        ItemStack chart = stack(player);
        if (!player.getInventory().add(chart) && !chart.isEmpty()) player.drop(chart, false, net.minecraft.util.Prediction.SERVER_ONLY);
        return true;
    }

    private static MapColor floor(Passages.Kind kind) {
        return switch (kind) {
            case START -> MapColor.COLOR_LIGHT_GREEN;
            case EXIT -> MapColor.EMERALD;
            case SEAL -> MapColor.COLOR_RED;
            case TREASURE, VAULT, CHIMES, BEAM, PLATES, STAIRWELL -> MapColor.GOLD;
            case SHRINE -> MapColor.COLOR_LIGHT_BLUE;
            case TRAP, CRUSHER, DARTS, SPIKES, FLAMES, BLADES, ARC, GAS -> MapColor.COLOR_ORANGE;
            case ARENA, PROVING, DUEL, OVERLOOK -> MapColor.COLOR_PURPLE;
            case BARRACKS -> MapColor.COLOR_RED;
            case GARDEN -> MapColor.PLANT;
            case POOL -> MapColor.WATER;
            case LIBRARY, ARMORY -> MapColor.WOOD;
            case OBSERVATORY, SANCTUM -> MapColor.COLOR_BLUE;
            case BATHS -> MapColor.WATER;
            default -> MapColor.STONE;
        };
    }

    private static void put(MapItemSavedData data, int x, int z, byte color) {
        if (x >= 0 && z >= 0 && x < SIZE && z < SIZE && data.colors[x + z * SIZE] != color) data.setColor(x, z, color);
    }

    /**
     * Redraws a player's chart for the level they are on. @return how many rooms are inked in (seen, or - with the
     * Cartographer skill - next to one that is).
     */
    public static int draw(ServerPlayer player, ServerLevel level, Passages.Plan plan) {
        MapItemSavedData data = data(player);
        if (data == null) return 0;
        Set<Passages.Cell> seen = Passages.visited(player, plan);
        boolean shown = revealed(player, plan);
        int sketch = Skills.rank(player, Skills.Skill.CARTOGRAPHER);
        int origin = (SIZE - plan.size * CELL) / 2, inked = 0;
        byte fog = ink(MapColor.NONE, MapColor.Brightness.NORMAL), wall = ink(MapColor.COLOR_BLACK, MapColor.Brightness.NORMAL);
        byte[] sheet = new byte[SIZE * SIZE];
        java.util.Arrays.fill(sheet, fog);
        // the room the owner stands in is outlined in white; the rest in black
        Passages.Cell standing = plan.cellAt(player.blockPosition());
        byte around = ink(MapColor.SNOW, MapColor.Brightness.HIGH);
        for (Passages.Cell c : plan.cells()) {
            boolean known = seen.contains(c);
            boolean near = false;
            if (!known && sketch > 0) {
                for (Passages.Cell n : plan.neighbours(c)) near |= plan.connected(c, n) && seen.contains(n);
            }
            if (!known && !near && !shown) continue;
            inked++;
            Passages.Kind kind = plan.kind(c);
            boolean sealBroken = kind == Passages.Kind.SEAL && !level.getBlockState(plan.sealPos(c)).is(com.thefarawaydev.realmsofchaos.ModContent.PASSAGE_SEAL.get());
            // a room only guessed at is drawn grey; with two ranks of Cartographer its kind shows through
            MapColor colour = known || shown || sketch >= 2 ? (sealBroken ? MapColor.COLOR_GREEN : floor(kind)) : MapColor.COLOR_LIGHT_GRAY;
            byte fill = ink(colour, known ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW);
            int x0 = origin + c.x() * CELL, z0 = origin + c.z() * CELL;
            // (a wide room is drawn wider)
            int inset = plan.wide(c) ? 1 : plan.small(c) ? 3 : 2;
            for (int dx = inset; dx < CELL - inset; dx++) {
                for (int dz = inset; dz < CELL - inset; dz++) {
                    boolean edge = dx == inset || dz == inset || dx == CELL - inset - 1 || dz == CELL - inset - 1;
                    sheet[(x0 + dx) + (z0 + dz) * SIZE] = edge ? (c.equals(standing) ? around : wall) : fill;
                }
            }
            // what the room is, as a small sign in its middle - once it has been seen, or a shrine has shown it
            if (known || shown) {
                for (int[] dot : sign(kind, sealBroken)) sheet[(x0 + CELL / 2 + dot[0]) + (z0 + CELL / 2 + dot[1]) * SIZE] = wall;
            }
            if (!known) continue;
            // doorways, as far as the wall between two rooms
            for (Passages.Cell n : plan.neighbours(c)) {
                if (!plan.connected(c, n)) continue;
                int sx = n.x() - c.x(), sz = n.z() - c.z();
                for (int k = -1; k <= 1; k++) {
                    for (int d = CELL / 2 - 2; d < CELL / 2 + 2; d++) {
                        int px = x0 + CELL / 2 + sx * d + (sx == 0 ? k : 0), pz = z0 + CELL / 2 + sz * d + (sz == 0 ? k : 0);
                        if (px >= 0 && pz >= 0 && px < SIZE && pz < SIZE) sheet[px + pz * SIZE] = fill;
                    }
                }
            }
        }
        // along the top, a mark for every level of the passage, this one bright; along the bottom, one for every seal
        byte dim = ink(MapColor.COLOR_GRAY, MapColor.Brightness.NORMAL), bright = ink(MapColor.SNOW, MapColor.Brightness.HIGH);
        int left = (SIZE - plan.stages * 5 + 2) / 2;
        for (int s = 0; s < plan.stages; s++) {
            for (int dx = 0; dx < 3; dx++) for (int dz = 2; dz < 5; dz++) sheet[(left + s * 5 + dx) + dz * SIZE] = s == plan.stage ? bright : dim;
        }
        left = (SIZE - plan.seals.size() * 6 + 2) / 2;
        for (int i = 0; i < plan.seals.size(); i++) {
            boolean broken = !level.getBlockState(plan.sealPos(plan.seals.get(i))).is(com.thefarawaydev.realmsofchaos.ModContent.PASSAGE_SEAL.get());
            byte mark = ink(broken ? MapColor.COLOR_GREEN : MapColor.COLOR_RED, MapColor.Brightness.HIGH);
            for (int dx = 0; dx < 4; dx++) for (int dz = SIZE - 6; dz < SIZE - 2; dz++) sheet[(left + i * 6 + dx) + dz * SIZE] = mark;
        }
        // the owner: a white mark where they stand
        Passages.Cell here = plan.cellAt(player.blockPosition());
        if (here != null) {
            var m = plan.centre(here);
            int px = origin + here.x() * CELL + CELL / 2 + (int) Math.round((player.getX() - (m.getX() + 0.5)) * (CELL - 6) / 9.0);
            int pz = origin + here.z() * CELL + CELL / 2 + (int) Math.round((player.getZ() - (m.getZ() + 0.5)) * (CELL - 6) / 9.0);
            byte mark = ink(MapColor.SNOW, MapColor.Brightness.HIGH);
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = px + d[0], z = pz + d[1];
                if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) sheet[x + z * SIZE] = mark;
            }
            // and a yellow point ahead of it, the way they are facing
            double yaw = Math.toRadians(player.getYRot());
            byte nose = ink(MapColor.COLOR_YELLOW, MapColor.Brightness.HIGH);
            for (int step = 2; step <= 3; step++) {
                int x = px + (int) Math.round(-Math.sin(yaw) * step), z = pz + (int) Math.round(Math.cos(yaw) * step);
                if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) sheet[x + z * SIZE] = nose;
            }
        }
        // companions: a blue mark for anyone else on the level, in a room that is on the chart
        byte friend = ink(MapColor.COLOR_LIGHT_BLUE, MapColor.Brightness.HIGH);
        for (ServerPlayer other : level.getPlayers(o -> o != player && o.isAlive() && !o.isSpectator() && plan.box().contains(o.position()))) {
            Passages.Cell there = plan.cellAt(other.blockPosition());
            if (there == null || !(seen.contains(there) || shown)) continue;
            var m = plan.centre(there);
            int px = origin + there.x() * CELL + CELL / 2 + (int) Math.round((other.getX() - (m.getX() + 0.5)) * (CELL - 6) / 9.0);
            int pz = origin + there.z() * CELL + CELL / 2 + (int) Math.round((other.getZ() - (m.getZ() + 0.5)) * (CELL - 6) / 9.0);
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                int x = px + d[0], z = pz + d[1];
                if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) sheet[x + z * SIZE] = friend;
            }
        }
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) put(data, x, z, sheet[x + z * SIZE]);
        return inked;
    }

    private static final int[][] CROSS = {{-2, -2}, {-1, -1}, {0, 0}, {1, 1}, {2, 2}, {2, -2}, {1, -1}, {-1, 1}, {-2, 2}};
    private static final int[][] BOX = {{-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {2, 2}, {-2, -1}, {-2, 0}, {-2, 1}, {2, -1}, {2, 0}, {2, 1}};
    private static final int[][] DIAMOND = {{0, -2}, {-1, -1}, {1, -1}, {-2, 0}, {2, 0}, {-1, 1}, {1, 1}, {0, 2}};
    private static final int[][] PLUS = {{0, -2}, {0, -1}, {0, 0}, {0, 1}, {0, 2}, {-2, 0}, {-1, 0}, {1, 0}, {2, 0}};
    private static final int[][] ARCH = {{-2, 2}, {-2, 1}, {-2, 0}, {-1, -1}, {0, -2}, {1, -1}, {2, 0}, {2, 1}, {2, 2}};
    private static final int[][] BANG = {{0, -2}, {0, -1}, {0, 0}, {0, 2}};
    private static final int[][] NOTHING = {};

    /** The sign drawn in the middle of a room: a cross for a seal (a box once it is broken), a diamond for a cache, and so on. */
    public static int[][] sign(Passages.Kind kind, boolean sealBroken) {
        return switch (kind) {
            case SEAL -> sealBroken ? BOX : CROSS;
            case TREASURE, ARENA, STAIRWELL, VAULT, CHIMES, BEAM, PLATES -> DIAMOND;
            case SHRINE -> PLUS;
            case EXIT -> ARCH;
            case TRAP, CRUSHER, DARTS, SPIKES, FLAMES, BLADES, ARC, GAS, GAUNTLET -> BANG;
            default -> NOTHING;
        };
    }

    private static String shownKey(ServerPlayer player, Passages.Plan plan) {
        return "chartall/" + player.getUUID() + "/" + plan.line.id() + "/" + plan.index + "/" + plan.stage;
    }

    /** Has a shrine shown this player the whole of this level? */
    public static boolean revealed(ServerPlayer player, Passages.Plan plan) {
        return ProgressData.get(player.level().getServer()).isBuilt(shownKey(player, plan));
    }

    /**
     * A shrine shows whoever it blesses the whole of its level: every room is inked in with its sign, though the
     * doorways between rooms not yet walked stay unknown. @return true if this was news
     */
    public static boolean reveal(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos shrine) {
        Passages.Plan plan = Passages.planAt(level, shrine);
        if (plan == null || revealed(player, plan)) return false;
        ProgressData.get(level.getServer()).markBuilt(shownKey(player, plan));
        if (holds(player)) draw(player, level, plan);
        return true;
    }

    /** The colour of one point of a player's chart, for looking at what was drawn. */
    public static byte at(ServerPlayer player, int x, int z) {
        return data(player).colors[x + z * SIZE];
    }

    public static byte fog() {
        return ink(MapColor.NONE, MapColor.Brightness.NORMAL);
    }

    /** The point of the sheet at the middle of a room. */
    public static int[] pixel(Passages.Plan plan, Passages.Cell cell) {
        int origin = (SIZE - plan.size * CELL) / 2;
        return new int[]{origin + cell.x() * CELL + CELL / 2, origin + cell.z() * CELL + CELL / 2 - 4};
    }
}
