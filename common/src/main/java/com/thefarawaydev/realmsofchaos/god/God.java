package com.thefarawaydev.realmsofchaos.god;

import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/** One god / boss / weapon in the roster. Rank 1 is the strongest of its line, rank 15 the weakest. */
public final class God {
    private final int index;
    private final Line line;
    private final int rank;
    private final String id;
    private final String name;
    private final String title;
    private final String weaponName;
    private final WeaponKind kind;
    private final Power power;
    private final EffectSpec targetEffect;
    private final EffectSpec selfEffect;

    God(int index, Line line, int rank, String id, String name, String title, String weaponName,
        WeaponKind kind, Power power, String targetEffect, String selfEffect) {
        this.kind = kind;
        this.index = index;
        this.line = line;
        this.rank = rank;
        this.id = id;
        this.name = name;
        this.title = title;
        this.weaponName = weaponName;
        this.power = power;
        this.targetEffect = EffectSpec.parse(targetEffect);
        this.selfEffect = EffectSpec.parse(selfEffect);
    }

    public int index() { return index; }
    public Line line() { return line; }
    public int rank() { return rank; }
    public String id() { return id; }
    public String englishName() { return name; }
    public String englishTitle() { return title; }
    public String englishWeaponName() { return weaponName; }
    public Power power() { return power; }
    public WeaponKind kind() { return kind; }
    /** The damage every kind of this god's weapon is scaled from: 5 for a starter up to 13.4 for a final god. */
    public double baseDamage() { return 3.0 + weaponDamageBonus(); }
    public boolean isFinalBoss() { return rank == 1; }
    public boolean isStarter() { return rank == Line.RANKS; }

    public Identifier weaponId() { return RealmsOfChaos.id(id + "_weapon"); }

    public Component displayName() {
        return Component.translatable("god." + RealmsOfChaos.MOD_ID + "." + id);
    }

    public Component displayTitle() {
        return Component.translatable("god." + RealmsOfChaos.MOD_ID + "." + id + ".title");
    }

    public Component bossName() {
        return Component.translatable("boss." + RealmsOfChaos.MOD_ID + ".title", displayName(), displayTitle());
    }

    EffectSpec targetEffect() { return targetEffect; }

    public void applyTargetEffect(LivingEntity target) {
        if (targetEffect != null) target.addEffect(targetEffect.instance(1.0));
    }

    public void applySelfEffect(LivingEntity self) {
        if (selfEffect != null) self.addEffect(selfEffect.instance(1.0));
    }

    /** Boss max health: 60 for the starters up to 620 for the final gods. */
    public float bossHealth() { return 60.0F + (Line.RANKS - rank) * 40.0F; }
    public double bossArmor() { return 2.0 + (Line.RANKS - rank) * 1.1; }
    public double weaponDamageBonus() { return 2.0 + (Line.RANKS - rank) * 0.6; }
}
