package com.thefarawaydev.realmsofchaos.item;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.ui.Tutorial;
import com.thefarawaydev.realmsofchaos.world.Passages;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Scanner;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Compass of Chaos. It carries nobody anywhere: it scans. Right-click begins a scan of a minute or more, read
 * above the hotbar while the compass is in hand; sneak and right-click turns it to what it should look for - the
 * way to the hub, the keystone shrines, or the field network. Inside a passage it is the guide it always was and
 * points at the nearest seal. How a scan works is in {@link Scanner}.
 */
public class ChaosCompassItem extends Item {
    public ChaosCompassItem(Properties properties) {
        super(properties);
    }

    /** The compass a player is holding, or the first in their pack. */
    public static ItemStack find(ServerPlayer player) {
        if (player.getMainHandItem().is(ModContent.CHAOS_COMPASS.get())) return player.getMainHandItem();
        if (player.getOffhandItem().is(ModContent.CHAOS_COMPASS.get())) return player.getOffhandItem();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModContent.CHAOS_COMPASS.get())) return player.getInventory().getItem(i);
        }
        return ItemStack.EMPTY;
    }

    /** Begins a scan with whatever the player's compass is set to. @return true if one began. */
    public static boolean scan(ServerPlayer player) {
        ItemStack compass = find(player);
        if (compass.isEmpty()) {
            Hud.actionBar(player, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".scan.nocompass"), 0xAAAAAA));
            return false;
        }
        if (Scanner.scanning(player)) return false;
        boolean began = Scanner.start(player, Scanner.mode(compass));
        if (began) Tutorial.tip(player, Tutorial.Tip.SCAN);
        return began;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack compass = player.getItemInHand(hand);
        Passages.Spot spot = Passages.locate(sp);
        if (spot != null && sp.level() instanceof ServerLevel passage) {
            // inside a passage the compass is a guide: it points at the nearest seal, then at the gate
            Passages.point(sp, passage, spot);
            player.getCooldowns().addCooldown(compass, 20);
            return InteractionResult.SUCCESS;
        }
        if (Realm.of(sp.level().dimension()) != null) {
            // in the hub and the towers there is nothing to scan for: the gates are where they are
            Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".scan.nowhere"), 0xAAAAAA));
            player.getCooldowns().addCooldown(compass, 20);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // a new setting starts a new scan
            Scanner.Mode mode = Scanner.cycle(compass);
            Scanner.stop(sp);
            level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (com.thefarawaydev.realmsofchaos.ui.CompassTab.drawn(sp)) {
                com.thefarawaydev.realmsofchaos.ui.CompassTab.notice(sp, compass.getHoverName().copy().withStyle(style -> style.withColor(mode.color)),
                        Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".scan.mode", mode.displayName()));
            } else {
                Hud.actionBar(sp, Hud.colored(Component.translatable("hud." + RealmsOfChaos.MOD_ID + ".scan.mode", mode.displayName()), mode.color));
            }
            player.getCooldowns().addCooldown(compass, 10);
            return InteractionResult.SUCCESS;
        }
        if (Scanner.scanning(sp)) {
            // a second click asks the needle for a roll right away (then it needs two seconds to settle)
            Scanner.pulse(sp, (ServerLevel) sp.level(), Scanner.mode(compass));
            player.getCooldowns().addCooldown(compass, 40);
            return InteractionResult.SUCCESS;
        }
        if (Scanner.start(sp, Scanner.mode(compass))) Tutorial.tip(sp, Tutorial.Tip.SCAN);
        player.getCooldowns().addCooldown(compass, 20);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass.tooltip"));
        Scanner.Mode mode = Scanner.mode(stack);
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass.mode", mode.displayName()).withStyle(Style.EMPTY.withColor(mode.color)));
        builder.accept(Component.translatable("item." + RealmsOfChaos.MOD_ID + ".chaos_compass.sneak").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
    }
}
