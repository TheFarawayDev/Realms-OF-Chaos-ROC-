package com.thefarawaydev.realmsofchaos.world;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Travelling by Waygate Obelisk. Nothing about it is instant: the traveller chooses where to go, then has to stand
 * still by the obelisk for five seconds while it gathers - move, or be hit, and it lets go. It costs an ender
 * pearl, taken only when the journey is made. An obelisk can send its traveller to any other obelisk they have
 * touched, or - for someone a Ruined Gate already knows - to the Hub of Realms.
 */
public final class Waygates {
    private static final String HUD = "hud." + RealmsOfChaos.MOD_ID + ".waygate.";
    /** How far a traveller may drift from where they began before the obelisk lets go. */
    public static final double DRIFT = 0.8;
    public static final String HUB = "hub";

    private static final class Channel {
        ServerPlayer player;
        BlockPos obelisk;
        String destination;          // HUB, or the code of another obelisk
        String dimension;
        Vec3 from;
        float health;
        int ticks, needed;
    }

    private static final Map<UUID, Channel> ACTIVE = new HashMap<>();

    private Waygates() {}

    public static Item cost() {
        return Items.ENDER_PEARL;
    }

    public static int price(MinecraftServer server) {
        return HubTiers.waygatesFree(server) ? 0 : 1;
    }

    /** The other obelisks this player can travel to from here. */
    public static List<Network.Node> destinations(ServerPlayer player, BlockPos from) {
        String here = Network.dimension(player.level());
        List<Network.Node> out = new ArrayList<>();
        for (Network.Node node : Network.discovered(player)) {
            if (node.pos().equals(from) && node.dimension().equals(here)) continue;
            out.add(node);
        }
        return out;
    }

    public static Network.Node destination(ServerPlayer player, BlockPos from, String code) {
        for (Network.Node node : destinations(player, from)) if (node.code().equals(code)) return node;
        return null;
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) n += inventory.getItem(i).getCount();
        }
        return n;
    }

    private static void take(ServerPlayer player, Item item, int amount) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            int n = Math.min(amount, stack.getCount());
            stack.shrink(n);
            amount -= n;
        }
    }

    public static boolean canPay(ServerPlayer player) {
        return player.isCreative() || count(player, cost()) >= price(player.level().getServer());
    }

    public static boolean channelling(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * Begins the channel. @param destination {@link #HUB} or the code of an obelisk the player has touched
     * @return false, with the reason on the action bar, if the journey cannot be begun.
     */
    public static boolean begin(ServerPlayer player, BlockPos obelisk, String destination) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !(player.level() instanceof ServerLevel level)) return false;
        if (!level.getBlockState(obelisk).is(Network.Kind.WAYGATE.block()) || player.distanceToSqr(Vec3.atCenterOf(obelisk)) > 36.0) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "far"), 0xFF5555));
            return false;
        }
        if (HUB.equals(destination)) {
            if (!Access.check(player)) return false;        // says why itself
        } else if (destination(player, obelisk, destination) == null) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "unknown"), 0xFF5555));
            return false;
        }
        if (!canPay(player)) {
            Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "cost", price(server), new ItemStack(cost()).getHoverName()), 0xFF5555));
            return false;
        }
        Channel channel = new Channel();
        channel.player = player;
        channel.obelisk = obelisk.immutable();
        channel.destination = destination;
        channel.dimension = Network.dimension(level);
        channel.from = player.position();
        channel.health = player.getHealth();
        channel.needed = (int) Math.max(20, Math.round(HubTiers.channelTicks(server) * Skills.channelScale(player)));
        ACTIVE.put(player.getUUID(), channel);
        level.playSound(null, obelisk, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    private static void cancel(ServerPlayer player, String why) {
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + why), 0xFF5555));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Channel>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Channel> entry = it.next();
            Channel channel = entry.getValue();
            ServerPlayer player = channel.player;
            if (player.isRemoved() || player.hasDisconnected() || !player.isAlive() || !(player.level() instanceof ServerLevel level)
                    || !Network.dimension(level).equals(channel.dimension)) {
                it.remove();
                continue;
            }
            if (player.position().distanceTo(channel.from) > DRIFT) {
                cancel(player, "moved");
                it.remove();
                continue;
            }
            if (player.getHealth() < channel.health - 0.01F) {
                cancel(player, "hurt");
                it.remove();
                continue;
            }
            channel.health = Math.max(channel.health, player.getHealth());
            channel.ticks++;
            // the obelisk gathers: a spiral closing on the traveller, and a bar that fills
            double a = channel.ticks * 0.45, r = 1.6 * (1.0 - channel.ticks / (double) channel.needed) + 0.3;
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), player.getX() + Math.cos(a) * r, player.getY() + 0.2 + channel.ticks / (double) channel.needed * 1.8,
                    player.getZ() + Math.sin(a) * r, 2, 0.02, 0.02, 0.02, 0.0);
            level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.GLINT.type(), channel.obelisk.getX() + 0.5, channel.obelisk.getY() + 1.2, channel.obelisk.getZ() + 0.5, 1, 0.1, 0.3, 0.1, 0.01);
            if (channel.ticks % 5 == 0) {
                int lit = (int) Math.round(channel.ticks / (double) channel.needed * 10);
                Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "channel", "▮".repeat(lit) + "▯".repeat(10 - lit)), 0xA55AD6));
            }
            if (channel.ticks % 30 == 0) com.thefarawaydev.realmsofchaos.fx.RocSounds.by(level, player, "waygate_charge", 0.8F, 0.8F + 0.5F * channel.ticks / (float) channel.needed);
            if (channel.ticks >= channel.needed) {
                it.remove();
                arrive(player, level, channel);
            }
        }
    }

    private static void arrive(ServerPlayer player, ServerLevel level, Channel channel) {
        MinecraftServer server = level.getServer();
        if (!canPay(player)) {
            cancel(player, "unpaid");
            return;
        }
        Network.Node to = null;
        if (!HUB.equals(channel.destination)) {
            to = destination(player, channel.obelisk, channel.destination);
            if (to == null || to.level(server) == null) {
                cancel(player, "unknown");
                return;
            }
        }
        if (!player.isCreative()) take(player, cost(), price(server));
        level.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.8, 0.4, 0.1);
        com.thefarawaydev.realmsofchaos.fx.RocSounds.by(level, player, "waygate_travel", 1.0F, 1.0F);
        if (to == null) {
            Teleports.toHub(player);
            return;
        }
        ServerLevel dest = to.level(server);
        BlockPos stand = standBy(dest, to.pos());
        Teleports.moveTo(player, dest, new Vec3(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5), player.getYRot());
        dest.sendParticles(com.thefarawaydev.realmsofchaos.fx.RocParticles.CHAOS_MOTE.type(), stand.getX() + 0.5, stand.getY() + 1, stand.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.1);
        com.thefarawaydev.realmsofchaos.fx.RocSounds.at(dest, stand, "waygate_travel", 1.0F, 1.1F);
        Hud.actionBar(player, Hud.colored(Component.translatable(HUD + "arrived", Scanner.coords(to.pos())), 0x55FF55));
    }

    /** A free block beside an obelisk to arrive on; on top of it if it is walled in. */
    public static BlockPos standBy(ServerLevel level, BlockPos obelisk) {
        int[][] around = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] o : around) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos pos = obelisk.offset(o[0], dy, o[1]);
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                        && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return pos;
            }
        }
        return obelisk.above();
    }

    /** Test seam: brings a running channel to its last tick. */
    public static void hurry(ServerPlayer player) {
        Channel channel = ACTIVE.get(player.getUUID());
        if (channel != null) channel.ticks = channel.needed - 1;
    }

    public static void reset() {
        ACTIVE.clear();
    }
}
