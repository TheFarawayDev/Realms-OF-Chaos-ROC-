package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Parties: up to four players climbing together. A party enters a tower on the floor of its highest member, so
 * friends can fight side by side wherever the furthest of them has got to. Progress stays each player's own:
 * nobody goes up past a floor until they have beaten its god themselves - with the party's help or without.
 *
 * Parties last as long as the server runs; nothing about them is saved.
 */
public final class Parties {
    public static final int MAX = 4;
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".party.";
    /** Party id (its founder) by member. A player in no party is not in the map. */
    private static final Map<UUID, UUID> PARTY_OF = new HashMap<>();
    private static final Map<UUID, Set<UUID>> MEMBERS = new HashMap<>();
    /** Open invitations: who was invited, by whom. */
    private static final Map<UUID, UUID> INVITES = new HashMap<>();

    /** Test seam: players the self-test made, which are not in the server's player list. Empty in normal play. */
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();

    private Parties() {}

    private static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer known = TEST_PLAYERS.get(id);
        if (known != null) return known;
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** Test seam: lets the self-test's players be found as if they were online. */
    public static void testPlayer(ServerPlayer player) {
        TEST_PLAYERS.put(player.getUUID(), player);
    }

    public static boolean inParty(UUID player) {
        return PARTY_OF.containsKey(player);
    }

    /** Everyone in a player's party, the player included; just the player if they are alone. */
    public static Set<UUID> members(UUID player) {
        UUID party = PARTY_OF.get(player);
        Set<UUID> out = new LinkedHashSet<>();
        if (party == null) out.add(player);
        else out.addAll(MEMBERS.get(party));
        return out;
    }

    /** The members who are on the server right now, the player first. */
    public static List<ServerPlayer> online(ServerPlayer player) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(player);
        MinecraftServer server = player.level().getServer();
        for (UUID id : members(player.getUUID())) {
            if (id.equals(player.getUUID())) continue;
            ServerPlayer other = find(server, id);
            if (other != null) out.add(other);
        }
        return out;
    }

    public static UUID invitedBy(UUID player) {
        return INVITES.get(player);
    }

    private static void say(ServerPlayer player, String key, int color, Object... args) {
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + key, args), color));
    }

    /** @return true if the invitation was sent. */
    public static boolean invite(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) {
            say(from, "self", 0xFF5555);
            return false;
        }
        if (members(from.getUUID()).contains(to.getUUID())) {
            say(from, "already", 0xAAAAAA, to.getDisplayName());
            return false;
        }
        if (members(from.getUUID()).size() >= MAX) {
            say(from, "full", 0xFF5555);
            return false;
        }
        INVITES.put(to.getUUID(), from.getUUID());
        say(from, "sent", 0x55FF55, to.getDisplayName());
        Hud.title(to, Hud.colored(Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".party"), 0x55FFFF),
                Hud.colored(Component.translatable(HUD + "invited", from.getDisplayName()), 0xFFFFFF), 10, 100, 20);
        to.level().playSound(null, to.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    /** Joins the party of whoever invited this player (leaving any other first). @return true if they joined. */
    public static boolean accept(ServerPlayer player) {
        UUID inviter = INVITES.remove(player.getUUID());
        if (inviter == null) {
            say(player, "none", 0xAAAAAA);
            return false;
        }
        UUID party = PARTY_OF.getOrDefault(inviter, inviter);
        Set<UUID> members = MEMBERS.computeIfAbsent(party, id -> new LinkedHashSet<>(List.of(id)));
        if (members.size() >= MAX) {
            say(player, "full", 0xFF5555);
            return false;
        }
        leave(player, false);
        PARTY_OF.put(party, party);
        members.add(player.getUUID());
        PARTY_OF.put(player.getUUID(), party);
        MinecraftServer server = player.level().getServer();
        for (UUID id : members) {
            ServerPlayer member = find(server, id);
            if (member != null) say(member, "joined", 0x55FF55, player.getDisplayName());
        }
        say(player, "joined", 0x55FF55, player.getDisplayName());
        return true;
    }

    public static void decline(ServerPlayer player) {
        INVITES.remove(player.getUUID());
    }

    public static boolean leave(ServerPlayer player) {
        return leave(player, true);
    }

    private static boolean leave(ServerPlayer player, boolean announce) {
        UUID party = PARTY_OF.remove(player.getUUID());
        if (party == null) {
            if (announce) say(player, "alone", 0xAAAAAA);
            return false;
        }
        Set<UUID> members = MEMBERS.get(party);
        members.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        for (UUID id : members) {
            ServerPlayer member = find(server, id);
            if (member != null) say(member, "left", 0xAAAAAA, player.getDisplayName());
        }
        if (announce) say(player, "left", 0xAAAAAA, player.getDisplayName());
        if (members.size() <= 1) {
            // a party of one is no party
            for (UUID id : members) PARTY_OF.remove(id);
            MEMBERS.remove(party);
        } else if (party.equals(player.getUUID())) {
            // the founder left: the party goes on under its next member
            UUID next = members.iterator().next();
            MEMBERS.remove(party);
            MEMBERS.put(next, members);
            for (UUID id : members) PARTY_OF.put(id, next);
        }
        return true;
    }

    /**
     * The floor of a tower a player travels to with their party: the highest floor any member who is online is
     * working on. Alone, that is simply the player's own floor.
     */
    public static int floor(ServerPlayer player, Line line) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return 1;
        ProgressData data = ProgressData.get(server);
        int best = Tower.currentFloor(data, player.getUUID(), line);
        for (ServerPlayer member : online(player)) best = Math.max(best, Tower.currentFloor(data, member.getUUID(), line));
        return best;
    }

    /** Test seam: forget every party and invitation. */
    public static void reset() {
        PARTY_OF.clear();
        MEMBERS.clear();
        INVITES.clear();
        TEST_PLAYERS.clear();
    }
}
