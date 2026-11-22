package com.thefarawaydev.realmsofchaos.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Cards: a tip, something a god has learnt about you, a notice too long for a title. Each is a small panel at the
 * top of the screen with a few lines on it, shown for some seconds and then taken away - never the huge letters of
 * a title across the whole view. A card is a boss bar underneath, drawn over by the mod's own artwork (see
 * {@link HudArt}). One shows at a time; others wait their turn.
 */
public final class Cards {
    /** How long a card stays, and how long the screen is left clear before the next. */
    public static final int STAY = 150, GAP = 10;
    /** At most this many wait; more than that and the oldest are let go. */
    public static final int MAX_WAITING = 4;

    /** What a card says. */
    public record Shown(HudArt.Card kind, List<String> lines, boolean headed) {}

    private static final class Slot {
        ServerBossEvent bar;
        Shown shown;
        /** Whose screen this is. */
        ServerPlayer player;
        /** The tick the card showing is taken away at; or, with none showing, the tick the next may be put up. */
        long until;
        final Deque<Shown> waiting = new ArrayDeque<>();
    }

    private static final Map<UUID, Slot> SLOTS = new HashMap<>();

    private Cards() {}

    public static void tip(ServerPlayer player, String text) {
        show(player, HudArt.Card.TIP, null, text);
    }

    public static void insight(ServerPlayer player, String heading, String text) {
        show(player, HudArt.Card.INSIGHT, heading, text);
    }

    public static void notice(ServerPlayer player, String heading, String text) {
        show(player, HudArt.Card.NOTICE, heading, text);
    }

    public static void show(ServerPlayer player, HudArt.Card kind, String heading, String text) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Shown card = new Shown(kind, HudArt.lines(heading, text), heading != null && !heading.isBlank());
        Slot slot = SLOTS.computeIfAbsent(player.getUUID(), id -> new Slot());
        slot.player = player;
        if (slot.shown != null || server.getTickCount() < slot.until) {
            while (slot.waiting.size() >= MAX_WAITING) slot.waiting.pollFirst();
            slot.waiting.addLast(card);
            return;
        }
        put(server, player, slot, card);
    }

    private static void put(MinecraftServer server, ServerPlayer player, Slot slot, Shown card) {
        slot.shown = card;
        slot.until = server.getTickCount() + STAY;
        slot.bar = new ServerBossEvent(UUID.randomUUID(), HudArt.card(card.kind(), card.lines(), card.headed()),
                BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        slot.bar.setProgress(0.0F);
        if (player.connection != null) slot.bar.addPlayer(player);
    }

    private static void take(Slot slot) {
        if (slot.bar != null) {
            slot.bar.removeAllPlayers();
            slot.bar.setVisible(false);
        }
        slot.bar = null;
        slot.shown = null;
    }

    /** Every tick: cards that have had their time are taken away, and the next in line is put up after a moment. */
    public static void tick(MinecraftServer server) {
        if (SLOTS.isEmpty()) return;
        long now = server.getTickCount();
        for (var it = SLOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Slot> e = it.next();
            Slot slot = e.getValue();
            if (now < slot.until) continue;
            ServerPlayer online = server.getPlayerList().getPlayer(e.getKey());
            ServerPlayer player = online != null ? online : slot.player != null && !slot.player.hasDisconnected() ? slot.player : null;
            if (slot.shown != null) {
                take(slot);
                slot.until = now + GAP;
                if (!slot.waiting.isEmpty() && player != null) continue;
            }
            Shown next = player == null ? null : slot.waiting.pollFirst();
            if (next == null) it.remove();
            else put(server, player, slot, next);
        }
    }

    /** The card on a player's screen now, or null. */
    public static Shown showing(ServerPlayer player) {
        Slot slot = SLOTS.get(player.getUUID());
        return slot == null ? null : slot.shown;
    }

    /** How many cards wait behind the one showing. */
    public static int waiting(ServerPlayer player) {
        Slot slot = SLOTS.get(player.getUUID());
        return slot == null ? 0 : slot.waiting.size();
    }

    /** Takes a player's card away and forgets any that wait: on leaving, and between tests. */
    public static void clear(ServerPlayer player) {
        Slot slot = SLOTS.remove(player.getUUID());
        if (slot != null) take(slot);
    }
}
