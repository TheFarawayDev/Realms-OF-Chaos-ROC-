package com.thefarawaydev.realmsofchaos.client;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the mod's tooltips on the screen: a line of lore or of a menu's description longer than there is room for
 * is broken into several, at word boundaries and keeping its colours, so no tooltip runs off the edge of a small
 * window. Only the mod's own items and the icons of its menus (anything with lore or a name given it) are touched.
 */
public final class RocTooltips {
    private RocTooltips() {}

    /** The widest a line may be: never more than 220, and never more than half the screen less a margin. */
    public static int widest() {
        Minecraft mc = Minecraft.getInstance();
        int screen = mc.getWindow() == null ? 640 : mc.getWindow().getGuiScaledWidth();
        return Math.max(120, Math.min(220, screen / 2 - 16));
    }

    public static void wrap(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null || lines.isEmpty()) return;
        boolean ours = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(RealmsOfChaos.MOD_ID)
                || stack.has(DataComponents.LORE) || stack.has(DataComponents.CUSTOM_NAME);
        if (!ours) return;
        int max = widest();
        boolean changed = false;
        List<Component> out = new ArrayList<>();
        for (Component line : lines) {
            if (mc.font.width(line) <= max) {
                out.add(line);
                continue;
            }
            changed = true;
            for (FormattedText part : mc.font.getSplitter().splitLines(line, max, Style.EMPTY)) {
                MutableComponent piece = Component.empty();
                part.visit((style, text) -> {
                    piece.append(Component.literal(text).setStyle(style));
                    return Optional.empty();
                }, Style.EMPTY);
                out.add(piece);
            }
        }
        if (!changed) return;
        lines.clear();
        lines.addAll(out);
    }
}
