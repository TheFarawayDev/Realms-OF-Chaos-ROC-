package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.network.chat.Component;

/**
 * What sort of weapon a god's relic is. The kind decides its melee stats, whether it attacks at range, and how the
 * god that wields it fights: gods with a ranged kind keep their distance and shoot.
 */
public enum WeaponKind {
    //            melee  speed  reach  ranged  shot  cooldown
    BLADE  (1.00, -2.4, 0.0, false, 0.0, 0),
    DAGGER (0.70, -1.4, 0.0, false, 0.0, 0),
    POLEARM(0.95, -2.7, 1.5, false, 0.0, 0),
    HAMMER (1.35, -3.2, 0.0, false, 0.0, 0),
    SCYTHE (0.90, -2.6, 0.5, false, 0.0, 0),
    STAFF  (0.55, -2.4, 0.0, true, 0.90, 20),
    BOW    (0.40, -2.4, 0.0, true, 1.00, 16),
    THROWN (0.60, -2.4, 0.0, true, 1.10, 24),
    RELIC  (0.50, -2.4, 0.0, true, 0.80, 50);

    private final double meleeScale;
    private final double attackSpeed;
    private final double reach;
    private final boolean ranged;
    private final double rangedScale;
    private final int useCooldown;

    WeaponKind(double meleeScale, double attackSpeed, double reach, boolean ranged, double rangedScale, int useCooldown) {
        this.meleeScale = meleeScale;
        this.attackSpeed = attackSpeed;
        this.reach = reach;
        this.ranged = ranged;
        this.rangedScale = rangedScale;
        this.useCooldown = useCooldown;
    }

    /** Fraction of the god's base damage dealt by a melee hit. */
    public double meleeScale() { return meleeScale; }
    public double attackSpeed() { return attackSpeed; }
    /** Extra blocks of melee reach. */
    public double reach() { return reach; }
    /** True for kinds whose main attack is at range (right-click for players, the default attack for gods). */
    public boolean ranged() { return ranged; }
    public double rangedScale() { return rangedScale; }
    /** Ticks between ranged uses. */
    public int useCooldown() { return useCooldown; }

    public Component displayName() {
        return Component.translatable("kind." + RealmsOfChaos.MOD_ID + "." + name().toLowerCase());
    }

    public Component useHint() {
        return Component.translatable("kind." + RealmsOfChaos.MOD_ID + "." + name().toLowerCase() + ".use");
    }
}
