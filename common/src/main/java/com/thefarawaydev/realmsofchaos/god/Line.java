package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

/** The three Greek god lines. Each runs from rank 15 (starter boss) up to rank 1 (final boss). */
public enum Line {
    ZEUS("zeus", BossEvent.BossBarColor.YELLOW, 0xF2C94C),
    POSEIDON("poseidon", BossEvent.BossBarColor.BLUE, 0x2FB5C8),
    HADES("hades", BossEvent.BossBarColor.PURPLE, 0x8E44AD);

    public static final int RANKS = 15;

    private final String id;
    private final BossEvent.BossBarColor barColor;
    private final int color;

    Line(String id, BossEvent.BossBarColor barColor, int color) {
        this.id = id;
        this.barColor = barColor;
        this.color = color;
    }

    public String id() { return id; }
    public BossEvent.BossBarColor barColor() { return barColor; }
    public int color() { return color; }

    public Component displayName() {
        return Component.translatable("line." + RealmsOfChaos.MOD_ID + "." + id);
    }

    public Component zoneName() {
        return Component.translatable("zone." + RealmsOfChaos.MOD_ID + "." + id);
    }

    public static Line byId(String id) {
        for (Line l : values()) {
            if (l.id.equals(id)) return l;
        }
        return null;
    }
}
