package com.thefarawaydev.realmsofchaos.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        Shown shown;
        int count;
        /** Whose screen this is. */
        ServerPlayer player;
        /** The tick the card showing is taken away at; or, with none showing, the tick the next may be put up. */
        long until;
        final Deque<Shown> waiting = new ArrayDeque<>();
        /** What has been put up lately, and when: the same card is not put up twice in half a minute. */
        final Map<String, Long> recent = new HashMap<>();
    }

    /** The same card is not shown to a player again within this many ticks. */
    public static final int REPEAT_TICKS = 20 * 30;

    private static String words(Shown card) {
        return card.kind() + "/" + String.join("/", card.lines());
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
        show(player, kind, heading, text, false);
    }

    /** @param urgent shown at once, in place of whatever card is up: a phase beginning does not wait behind a tip */
    public static void show(ServerPlayer player, HudArt.Card kind, String heading, String text, boolean urgent) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        Shown card = new Shown(kind, HudArt.lines(heading, text), heading != null && !heading.isBlank());
        Slot slot = SLOTS.computeIfAbsent(player.getUUID(), id -> new Slot());
        slot.player = player;
        if (urgent) {
            take(slot);
            put(server, player, slot, card);
            return;
        }
        // said already, and not long ago - or about to be said: once is enough
        Long last = slot.recent.get(words(card));
        if (last != null && server.getTickCount() >= last && server.getTickCount() - last < REPEAT_TICKS) return;
        for (Shown held : slot.waiting) if (words(held).equals(words(card))) return;
        if (slot.shown != null || server.getTickCount() < slot.until) {
            while (slot.waiting.size() >= MAX_WAITING) slot.waiting.pollFirst();
            slot.waiting.addLast(card);
            return;
        }
        put(server, player, slot, card);
    }

    private static void put(MinecraftServer server, ServerPlayer player, Slot slot, Shown card) {
        slot.shown = card;
        slot.recent.values().removeIf(at -> server.getTickCount() - at >= REPEAT_TICKS);
        slot.recent.put(words(card), (long) server.getTickCount());
        slot.until = server.getTickCount() + STAY;
        slot.count++;
        if (Options.on(player, Options.Option.ART)) {
            // the player's own HUD draws it: which card, whether its first line is a heading, and its lines
            HudFeed.set(player, HudFeed.CARD, card.kind().ordinal() | (card.headed() ? 16 : 0) | (slot.count << 8),
                    net.minecraft.network.chat.Component.literal(String.join("\n", card.lines())));
        } else {
            Hud.actionBar(player, plain(card));
        }
    }

    /** A card in plain words, for a game that cannot draw it: its heading (or its kind), then what it says. */
    public static net.minecraft.network.chat.Component plain(Shown card) {
        List<String> lines = card.lines();
        String head = card.headed() ? lines.get(0) : switch (card.kind()) {
            case TIP -> "Tip";
            case INSIGHT -> "Insight";
            case AEGIS -> "Aegis";
            case WRATH -> "Wrath";
            case BOON -> "Boon";
            default -> "";
        };
        String rest = String.join(" ", card.headed() ? lines.subList(1, lines.size()) : lines);
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.literal(head).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(card.kind().color));
        return rest.isEmpty() ? out : out.append(net.minecraft.network.chat.Component.literal((head.isEmpty() ? "" : ": ") + rest).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFFF)));
    }

    private static void take(Slot slot) {
        if (slot.shown != null && slot.player != null) HudFeed.clear(slot.player, HudFeed.CARD);
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
            if (next == null) {
                // (kept a while longer with nothing on it: it still knows what was said lately)
                slot.recent.values().removeIf(at -> now - at >= REPEAT_TICKS);
                if (slot.recent.isEmpty() || player == null) it.remove();
            } else put(server, player, slot, next);
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
