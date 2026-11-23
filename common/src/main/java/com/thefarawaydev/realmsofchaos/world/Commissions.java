package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.item.Panoply;
import com.thefarawaydev.realmsofchaos.ui.Cards;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Commissions: the slow way to a relic. Everything a god gives up - and every piece of the panoply - can be made
 * by the forge instead, for a great deal of a tower's forge material and a long wait: half an hour of play for the
 * least relic, nearly two hours for a king's. The forge works on one thing at a time for each player, and only
 * while that player is in the world; the thing waits there until it is fetched.
 *
 * <p>It is the long road, not a short cut: beating the god is always quicker. And the forge makes a god's relic
 * only for someone who has stood on that god's floor.
 */
public final class Commissions {
    /** One thing the forge can be asked for. {@code god}: whose relic it is, or null for a piece of armour. */
    public record Order(String id, String shelf, Component name, Supplier<ItemStack> maker, Map<Item, Integer> cost, int minutes, God god) {
        public ItemStack make() {
            return maker.get();
        }
    }

    public static final String ARMOUR = "armour";
    private static List<Order> orders;
    private static int ticker;

    private Commissions() {}

    /** Everything that can be commissioned: the eight pieces of the panoply, then every god's relic. */
    public static List<Order> orders() {
        if (orders != null) return orders;
        List<Order> out = new ArrayList<>();
        for (Panoply.Piece piece : Panoply.Piece.values()) {
            // (armour is of no one tower: it takes a share of each)
            Map<Item, Integer> cost = new LinkedHashMap<>();
            for (Line line : Line.values()) cost.put(ModContent.materialItem(line, piece.tier), 10);
            out.add(new Order(piece.id(), ARMOUR, piece.stack().getHoverName(), piece::stack, cost, 60 + 15 * piece.tier, null));
        }
        for (Line line : Line.values()) {
            for (God god : Gods.ofLine(line)) {
                int tier = Tower.tier(Tower.floorOf(god)), climbed = Line.RANKS - god.rank();
                Map<Item, Integer> cost = new LinkedHashMap<>();
                cost.put(ModContent.materialItem(line, tier), 20 + climbed);
                out.add(new Order(god.id(), line.id(), new ItemStack(ModContent.weaponItem(god)).getHoverName(), () -> new ItemStack(ModContent.weaponItem(god)), cost, 30 + 6 * climbed, god));
            }
        }
        orders = out;
        return out;
    }

    public static List<Order> shelf(String shelf) {
        List<Order> out = new ArrayList<>();
        for (Order order : orders()) if (order.shelf().equals(shelf)) out.add(order);
        return out;
    }

    public static Order byId(String id) {
        for (Order order : orders()) if (order.id().equals(id)) return order;
        return null;
    }

    private static String key(ServerPlayer player, String what) {
        return "commission/" + player.getUUID() + "/" + what;
    }

    /** What the forge is making for this player, or has finished and is keeping for them; null if nothing. */
    public static Order current(ServerPlayer player) {
        int index = ProgressData.get(player.level().getServer()).counter(key(player, "order"));
        return index <= 0 || index > orders().size() ? null : orders().get(index - 1);
    }

    public static int ticksLeft(ServerPlayer player) {
        return current(player) == null ? 0 : ProgressData.get(player.level().getServer()).counter(key(player, "left"));
    }

    public static boolean ready(ServerPlayer player) {
        return current(player) != null && ticksLeft(player) <= 0;
    }

    public static int minutesLeft(ServerPlayer player) {
        return (ticksLeft(player) + 1199) / 1200;
    }

    /** The forge makes a god's relic only for someone who has stood on that god's floor. Armour it makes for anyone. */
    public static boolean unlocked(ServerPlayer player, Order order) {
        if (order.god() == null) return true;
        ProgressData data = ProgressData.get(player.level().getServer());
        return data.isDefeated(player.getUUID(), order.god()) || Tower.currentFloor(data, player.getUUID(), order.god().line()) >= Tower.floorOf(order.god());
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    /** Hands the materials over and sets the forge to work. @return true if it did */
    public static boolean place(ServerPlayer player, String id) {
        Order order = byId(id);
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".commission.";
        if (order == null) return false;
        if (current(player) != null) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "busy"), 0xFF5555));
            return false;
        }
        if (!unlocked(player, order)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(hud + "locked"), 0xFF5555));
            return false;
        }
        for (Map.Entry<Item, Integer> part : order.cost().entrySet()) {
            if (count(player, part.getKey()) < part.getValue()) {
                Hud.actionBar(player, Hud.colored(Component.translatable(hud + "short", part.getValue(), new ItemStack(part.getKey()).getHoverName()), 0xFF5555));
                return false;
            }
        }
        for (Map.Entry<Item, Integer> part : order.cost().entrySet()) {
            int owed = part.getValue();
            for (int i = 0; i < player.getInventory().getContainerSize() && owed > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.is(part.getKey())) continue;
                int taken = Math.min(owed, stack.getCount());
                stack.shrink(taken);
                owed -= taken;
            }
        }
        ProgressData data = ProgressData.get(player.level().getServer());
        data.setCounter(key(player, "order"), orders().indexOf(order) + 1);
        data.setCounter(key(player, "left"), order.minutes() * 1200);
        Hud.actionBar(player, Hud.colored(Component.translatable(hud + "placed", order.name()), 0xFF7A3C));
        return true;
    }

    /** Takes a finished commission from the forge. @return what was taken, or an empty stack */
    public static ItemStack claim(ServerPlayer player) {
        Order order = current(player);
        if (order == null || !ready(player)) return ItemStack.EMPTY;
        ProgressData data = ProgressData.get(player.level().getServer());
        data.setCounter(key(player, "order"), 0);
        data.setCounter(key(player, "left"), 0);
        ItemStack made = order.make();
        ItemStack given = made.copy();
        if (!player.getInventory().add(given) && !given.isEmpty()) player.drop(given, false, net.minecraft.util.Prediction.SERVER_ONLY);
        Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".commission.taken", order.name()), 0x55FF55));
        return made;
    }

    /** The forge works for this many ticks on a player's commission: called once a second, and by tests. */
    public static void work(ServerPlayer player, int ticks) {
        Order order = current(player);
        if (order == null) return;
        ProgressData data = ProgressData.get(player.level().getServer());
        int left = data.counter(key(player, "left"));
        if (left <= 0) return;
        left = Math.max(0, left - ticks);
        data.setCounter(key(player, "left"), left);
        if (left == 0) {
            String hud = "hud." + RealmsOfChaos.MOD_ID + ".commission.";
            Cards.notice(player, Words.of(hud + "done"), Words.of(hud + "done.sub", order.name()));
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.2F);
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (++ticker % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) work(player, 20);
    }
}
