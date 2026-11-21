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
            case TREASURE, VAULT, CHIMES, BEAM, PLATES -> MapColor.GOLD;
            case SHRINE -> MapColor.COLOR_LIGHT_BLUE;
            case TRAP, CRUSHER, DARTS -> MapColor.COLOR_ORANGE;
            case ARENA -> MapColor.COLOR_PURPLE;
            case GARDEN -> MapColor.PLANT;
            case POOL -> MapColor.WATER;
            case LIBRARY -> MapColor.WOOD;
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
        int sketch = Skills.rank(player, Skills.Skill.CARTOGRAPHER);
        int origin = (SIZE - plan.size * CELL) / 2, inked = 0;
        byte fog = ink(MapColor.NONE, MapColor.Brightness.NORMAL), wall = ink(MapColor.COLOR_BLACK, MapColor.Brightness.NORMAL);
        byte[] sheet = new byte[SIZE * SIZE];
        java.util.Arrays.fill(sheet, fog);
        for (Passages.Cell c : plan.cells()) {
            boolean known = seen.contains(c);
            boolean near = false;
            if (!known && sketch > 0) {
                for (Passages.Cell n : plan.neighbours(c)) near |= plan.connected(c, n) && seen.contains(n);
            }
            if (!known && !near) continue;
            inked++;
            Passages.Kind kind = plan.kind(c);
            boolean sealBroken = kind == Passages.Kind.SEAL && !level.getBlockState(plan.sealPos(c)).is(com.thefarawaydev.realmsofchaos.ModContent.PASSAGE_SEAL.get());
            // a room only guessed at is drawn grey; with two ranks of Cartographer its kind shows through
            MapColor colour = known || sketch >= 2 ? (sealBroken ? MapColor.COLOR_GREEN : floor(kind)) : MapColor.COLOR_LIGHT_GRAY;
            byte fill = ink(colour, known ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW);
            int x0 = origin + c.x() * CELL, z0 = origin + c.z() * CELL;
            for (int dx = 2; dx < CELL - 2; dx++) {
                for (int dz = 2; dz < CELL - 2; dz++) {
                    boolean edge = dx == 2 || dz == 2 || dx == CELL - 3 || dz == CELL - 3;
                    sheet[(x0 + dx) + (z0 + dz) * SIZE] = edge ? wall : fill;
                }
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
        }
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) put(data, x, z, sheet[x + z * SIZE]);
        return inked;
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
