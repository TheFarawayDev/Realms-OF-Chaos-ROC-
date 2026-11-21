package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.world.Parties;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Tower;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

/**
 * The party screen, opened from the Codex. The top row is your party; below it, everyone else on the server, one
 * click each to invite. An invitation waiting for you shows at the bottom with buttons to accept or decline.
 */
public class PartyMenu extends ButtonMenu {
    public static final int SLOT_INFO = slot(0, 4), MEMBER_ROW = 1, OTHERS_ROW = 3,
            SLOT_ACCEPT = slot(5, 3), SLOT_DECLINE = slot(5, 5), SLOT_LEAVE = slot(5, 8), SLOT_BACK = slot(5, 0);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".party.";

    /** Who is shown in each slot of the "everyone else" rows, so a click knows whom to invite. */
    private final List<UUID> others = new ArrayList<>();

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PartyMenu(id, inventory, (ServerPlayer) p),
                Skin.title(Skin.Panel.PARTY, Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".party"))));
    }

    private PartyMenu(int id, Inventory inventory, ServerPlayer viewer) {
        super(id, inventory, viewer, 6);
        render();
    }

    public static int memberSlot(int i) {
        return slot(MEMBER_ROW, 2 + i);
    }

    public static int otherSlot(int i) {
        return slot(OTHERS_ROW + i / 7, 1 + i % 7);
    }

    @Override
    protected void press(int slot) {
        MinecraftServer server = viewer.level().getServer();
        if (slot == SLOT_BACK) {
            clickSound();
            CodexMenu.open(viewer);
            return;
        }
        if (slot == SLOT_ACCEPT && Parties.invitedBy(viewer.getUUID()) != null) {
            clickSound();
            Parties.accept(viewer);
        } else if (slot == SLOT_DECLINE && Parties.invitedBy(viewer.getUUID()) != null) {
            clickSound();
            Parties.decline(viewer);
        } else if (slot == SLOT_LEAVE && Parties.inParty(viewer.getUUID())) {
            clickSound();
            Parties.leave(viewer);
        } else {
            for (int i = 0; i < others.size(); i++) {
                if (otherSlot(i) != slot) continue;
                ServerPlayer other = server == null ? null : server.getPlayerList().getPlayer(others.get(i));
                if (other != null) {
                    clickSound();
                    Parties.invite(viewer, other);
                }
            }
        }
        render();
    }

    private List<Component> progress(UUID player) {
        List<Component> lore = new ArrayList<>();
        ProgressData data = ProgressData.get(viewer.level().getServer());
        for (Line line : Line.values()) {
            lore.add(text(NS + "progress", line.color(), Tower.towerName(line), Tower.currentFloor(data, player, line)));
        }
        return lore;
    }

    @Override
    protected void render() {
        frame(DyeColor.CYAN, DyeColor.BLACK);
        MinecraftServer server = viewer.level().getServer();
        Set<UUID> members = Parties.members(viewer.getUUID());
        boolean party = Parties.inParty(viewer.getUUID());
        view.setItem(SLOT_INFO, icon(ModContent.CHAOS_COMPASS.get(), text("menu." + RealmsOfChaos.MOD_ID + ".party", 0x55FFFF), party, List.of(
                party ? text(NS + "size", 0x55FF55, members.size()) : text(NS + "solo", 0xAAAAAA),
                text(NS + "rule1", 0xAAAAAA), text(NS + "rule2", 0xAAAAAA), text(NS + "rule3", 0xAAAAAA), text(NS + "rule4", 0xAAAAAA))));
        int i = 0;
        for (UUID id : members) {
            ServerPlayer member = server == null ? null : server.getPlayerList().getPlayer(id);
            Component name = member != null ? member.getDisplayName() : Component.literal(id.toString().substring(0, 8));
            List<Component> lore = new ArrayList<>();
            lore.add(text(NS + (id.equals(viewer.getUUID()) ? "you" : "member"), 0x55FF55));
            lore.addAll(progress(id));
            view.setItem(memberSlot(i++), icon(Items.PLAYER_HEAD, name.copy().withStyle(plain(0x55FFFF)), true, lore));
        }
        others.clear();
        if (server != null) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (members.contains(other.getUUID()) || others.size() >= 14) continue;
                List<Component> lore = new ArrayList<>();
                lore.add(text(NS + "invite", 0x55FF55));
                lore.addAll(progress(other.getUUID()));
                view.setItem(otherSlot(others.size()), icon(Items.PLAYER_HEAD, other.getDisplayName().copy().withStyle(plain(0xFFFFFF)), false, lore));
                others.add(other.getUUID());
            }
        }
        UUID inviter = Parties.invitedBy(viewer.getUUID());
        if (inviter != null) {
            ServerPlayer from = server == null ? null : server.getPlayerList().getPlayer(inviter);
            Component who = from != null ? from.getDisplayName() : Component.literal("?");
            view.setItem(SLOT_ACCEPT, icon(Items.DYE.pick(DyeColor.LIME), text(NS + "accept", 0x55FF55), true, List.of(text(NS + "pending", 0xFFFFFF, who))));
            view.setItem(SLOT_DECLINE, icon(Items.DYE.pick(DyeColor.RED), text(NS + "decline", 0xFF5555), false, List.of(text(NS + "pending", 0xFFFFFF, who))));
        }
        if (party) view.setItem(SLOT_LEAVE, icon(Items.OAK_DOOR, text(NS + "leave", 0xFF5555), false, List.of()));
        view.setItem(SLOT_BACK, icon(ModContent.CHAOS_CODEX.get(), text("menu." + RealmsOfChaos.MOD_ID + ".passage.codex", 0xFFFFFF), false, List.of()));
        broadcastChanges();
    }
}
