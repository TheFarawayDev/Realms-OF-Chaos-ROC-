package com.thefarawaydev.realmsofchaos.ui;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.world.Passages;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Breaking a passage seal is a quick-time strike: a hammer runs along the middle row, and every click while it is
 * on or beside the mark cracks the seal. A miss stings. Enough cracks and the seal gives way.
 */
public class SealMenu extends ButtonMenu {
    public static final int SLOT_INFO = slot(0, 4), BAR_ROW = 1, SLOT_STRIKE = slot(2, 4);
    private static final String NS = "menu." + RealmsOfChaos.MOD_ID + ".seal.";

    private final BlockPos seal;
    private final int needed;
    private final int speed;
    private int cracks;
    private StrikeBar bar;
    private int drawnMarker = -1;

    public static void open(ServerPlayer player, BlockPos seal, int tier) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SealMenu(id, inventory, (ServerPlayer) p, seal, tier),
                Component.translatable("menu." + RealmsOfChaos.MOD_ID + ".seal")));
    }

    private SealMenu(int id, Inventory inventory, ServerPlayer viewer, BlockPos seal, int tier) {
        super(id, inventory, viewer, 3);
        this.seal = seal;
        this.needed = Math.max(2, Math.min(4, tier + 1));
        this.speed = tier <= 1 ? 3 : 2;
        newBar();
        render();
    }

    private long now() {
        return viewer.level().getServer().getTickCount();
    }

    private void newBar() {
        bar = new StrikeBar(now(), speed, 2 + viewer.getRandom().nextInt(5));
        drawnMarker = -1;
    }

    public StrikeBar strikeBar() {
        return bar;
    }

    public int cracks() {
        return cracks;
    }

    public int needed() {
        return needed;
    }

    @Override
    protected void press(int slot) {
        if (slot != SLOT_STRIKE && slot / 9 != BAR_ROW) return;
        if (!(viewer.level() instanceof ServerLevel level)) return;
        if (!level.getBlockState(seal).is(ModContent.PASSAGE_SEAL.get())) {
            viewer.closeContainer();                // somebody else broke it first
            return;
        }
        StrikeBar.Hit hit = bar.hit(now());
        double x = seal.getX() + 0.5, y = seal.getY() + 0.5, z = seal.getZ() + 0.5;
        if (hit == StrikeBar.Hit.MISS) {
            // the seal bites back
            viewer.hurtServer(level, level.damageSources().magic(), 2.0F);
            level.playSound(null, seal, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7F, 0.6F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 12, 0.3, 0.3, 0.3, 0.2);
            Hud.actionBar(viewer, Hud.colored(Component.translatable(NS + "miss"), 0xFF5555));
        } else {
            cracks += hit == StrikeBar.Hit.PERFECT ? 2 : 1;
            level.playSound(null, seal, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, hit == StrikeBar.Hit.PERFECT ? 1.6F : 1.2F);
            level.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.3, 0.3, 0.3, 0.3);
            Hud.actionBar(viewer, Hud.colored(Component.translatable(NS + (hit == StrikeBar.Hit.PERFECT ? "perfect" : "good")),
                    hit == StrikeBar.Hit.PERFECT ? 0xFFFF55 : 0x55FF55));
        }
        if (cracks >= needed) {
            viewer.closeContainer();
            Passages.breakSeal(level, seal, viewer);
            return;
        }
        newBar();
        render();
    }

    private void drawBar() {
        long now = now();
        ItemStack[] row = bar.row(now);
        for (int i = 0; i < StrikeBar.SLOTS; i++) view.setItem(slot(BAR_ROW, i), row[i]);
        drawnMarker = bar.marker(now);
    }

    /** Called every server tick while the screen is open: the hammer moves on. */
    @Override
    public void broadcastChanges() {
        if (bar != null && bar.marker(now()) != drawnMarker) drawBar();
        super.broadcastChanges();
    }

    @Override
    protected void render() {
        fill(DyeColor.BLACK);
        view.setItem(SLOT_INFO, icon(ModContent.PASSAGE_SEAL.get(), text(NS + "title", 0x55FFFF), true, List.of(
                text(NS + "lore", 0xAAAAAA), text(NS + "lore2", 0xAAAAAA), text(NS + "cracks", 0xFFFF55, Math.min(cracks, needed), needed))));
        view.setItem(SLOT_STRIKE, icon(Items.MACE, text(NS + "strike", 0x55FF55), true, List.of(text(NS + "cracks", 0xFFFF55, Math.min(cracks, needed), needed))));
        drawBar();
        broadcastChanges();
    }
}
