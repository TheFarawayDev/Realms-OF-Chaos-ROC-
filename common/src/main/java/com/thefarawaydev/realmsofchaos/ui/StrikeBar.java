package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The timing bar behind every quick-time strike: a hammer runs back and forth along a row of nine slots, and a
 * strike is judged by where the hammer is at that moment - on the mark is perfect, beside it is good, anywhere
 * else is a miss. The hammer's place follows the server clock, so the same bar can be drawn, struck and tested.
 */
public final class StrikeBar {
    public enum Hit { PERFECT, GOOD, MISS }

    public static final int SLOTS = 9;
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".strike.";

    private final long start;
    private final int ticksPerSlot;
    private final int target;

    /**
     * @param start        server tick at which the hammer sets off from the left end
     * @param ticksPerSlot how long the hammer rests on each slot: lower is faster and harder
     * @param target       the slot of the mark, 1-7
     */
    public StrikeBar(long start, int ticksPerSlot, int target) {
        this.start = start;
        this.ticksPerSlot = Math.max(1, ticksPerSlot);
        this.target = Math.max(1, Math.min(SLOTS - 2, target));
    }

    public int target() {
        return target;
    }

    /** The slot the hammer is over at server tick {@code now}: it sweeps 0..8 and back again. */
    public int marker(long now) {
        int step = (int) ((Math.max(0, now - start) / ticksPerSlot) % (2 * (SLOTS - 1)));
        return step < SLOTS ? step : 2 * (SLOTS - 1) - step;
    }

    public Hit hit(long now) {
        int off = Math.abs(marker(now) - target);
        return off == 0 ? Hit.PERFECT : off == 1 ? Hit.GOOD : Hit.MISS;
    }

    /** The nine stacks that draw the bar at server tick {@code now}. */
    public ItemStack[] row(long now) {
        ItemStack[] row = new ItemStack[SLOTS];
        int marker = marker(now);
        for (int i = 0; i < SLOTS; i++) {
            int off = Math.abs(i - target);
            if (i == marker) {
                row[i] = ButtonMenu.icon(Items.MACE, ButtonMenu.text(NS + "hammer", 0xFFFFFF), true, List.of(ButtonMenu.text(NS + "hammer.lore", 0xAAAAAA)));
            } else if (off == 0) {
                row[i] = ButtonMenu.pane(DyeColor.YELLOW, ButtonMenu.text(NS + "perfect", 0xFFFF55), List.of());
            } else if (off == 1) {
                row[i] = ButtonMenu.pane(DyeColor.LIME, ButtonMenu.text(NS + "good", 0x55FF55), List.of());
            } else {
                row[i] = ButtonMenu.pane(DyeColor.GRAY, ButtonMenu.text(NS + "miss", 0x888888), List.of());
            }
        }
        return row;
    }
}
