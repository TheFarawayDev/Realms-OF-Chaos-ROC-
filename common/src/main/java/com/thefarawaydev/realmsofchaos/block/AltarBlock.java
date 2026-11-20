package com.thefarawaydev.realmsofchaos.block;

import com.thefarawaydev.realmsofchaos.ModContent;
import com.thefarawaydev.realmsofchaos.RealmsOfChaos;
import com.thefarawaydev.realmsofchaos.entity.GodBoss;
import com.thefarawaydev.realmsofchaos.god.God;
import com.thefarawaydev.realmsofchaos.god.Gods;
import com.thefarawaydev.realmsofchaos.god.Line;
import com.thefarawaydev.realmsofchaos.ui.AltarMenu;
import com.thefarawaydev.realmsofchaos.ui.Hud;
import com.thefarawaydev.realmsofchaos.world.ProgressData;
import com.thefarawaydev.realmsofchaos.world.Realm;
import com.thefarawaydev.realmsofchaos.world.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The trial altar of a god line. Inside a tower each floor's altar belongs to that floor's god; right-clicking
 * opens the Trial Altar menu, from which the trial is started. Players climb from the starter god (rank 15,
 * floor 1) to the final god (rank 1, floor 15), and a trial only opens once the previous god has fallen.
 */
public class AltarBlock extends Block {
    public enum State { READY, UNDERWAY, DEFEATED, LOCKED, COMPLETE, PEACEFUL }

    private final Line line;

    public AltarBlock(Line line, Properties properties) {
        super(properties);
        this.line = line;
    }

    public Line line() {
        return line;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            AltarMenu.open(sp, line, pos);
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean inTower(ServerLevel level, Line line) {
        Realm realm = Realm.of(level.dimension());
        return realm != null && realm.line() == line;
    }

    /** The god this altar offers to this player: the floor's god inside a tower, otherwise their next trial. */
    public static God godFor(ServerLevel level, ServerPlayer player, BlockPos altar, Line line) {
        if (inTower(level, line)) return Tower.god(line, Tower.floorAt(altar.getY()));
        int next = ProgressData.get(level.getServer()).nextRank(player.getUUID(), line);
        return next == 0 ? null : Gods.get(line, next);
    }

    /** The space a fight at this altar occupies: one tower floor, or a generous box around a free-standing altar. */
    private static AABB fightArea(ServerLevel level, BlockPos altar, Line line) {
        if (inTower(level, line)) {
            int y0 = Tower.floorY(Tower.floorAt(altar.getY()));
            return new AABB(-Tower.WALL_OUTER, y0 - Tower.DEPTH, -Tower.WALL_OUTER, Tower.WALL_OUTER, y0 + Tower.ROOM_HEIGHT + 1, Tower.WALL_OUTER);
        }
        return new AABB(altar).inflate(64);
    }

    public static State state(ServerLevel level, ServerPlayer player, BlockPos altar, Line line) {
        ProgressData data = ProgressData.get(level.getServer());
        God god = godFor(level, player, altar, line);
        if (god == null) return State.COMPLETE;
        if (data.isDefeated(player.getUUID(), god)) return State.DEFEATED;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return State.PEACEFUL;
        if (!level.getEntitiesOfClass(GodBoss.class, fightArea(level, altar, line), GodBoss::isAlive).isEmpty()) return State.UNDERWAY;
        if (god.rank() != data.nextRank(player.getUUID(), line)) return State.LOCKED;
        return State.READY;
    }

    /** Starts the trial if the altar is {@link State#READY}. @return the summoned boss, or null. */
    public static GodBoss begin(ServerLevel level, ServerPlayer player, BlockPos altar, Line line) {
        if (state(level, player, altar, line) != State.READY) return null;
        God god = godFor(level, player, altar, line);
        GodBoss boss = ModContent.GOD_BOSS.get().create(level, EntitySpawnReason.TRIGGERED);
        if (boss == null) return null;
        // In a tower the god appears on the floor's centre; an altar placed elsewhere summons a few blocks away.
        Vec3 spawn = inTower(level, line) ? Tower.centre(level, Tower.floorAt(altar.getY())) : findSpawn(level, boss, altar);
        boss.setGod(god);
        boss.setPos(spawn.x, spawn.y, spawn.z);
        boss.setHome(spawn);
        level.addFreshEntity(boss);
        boss.setTarget(player);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setPos(spawn.x, spawn.y, spawn.z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.POOF, spawn.x, spawn.y + 1, spawn.z, 60, 0.6, 1.0, 0.6, 0.08);
        level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.3F);
        String hud = "hud." + RealmsOfChaos.MOD_ID + ".";
        for (ServerPlayer witness : level.getEntitiesOfClass(ServerPlayer.class, fightArea(level, altar, line), Player::isAlive)) {
            announce(witness, god, hud);
        }
        if (!fightArea(level, altar, line).contains(player.position())) announce(player, god, hud);
        return boss;
    }

    private static void announce(ServerPlayer player, God god, String hud) {
        Hud.title(player, Hud.colored(Component.translatable(hud + "trial", god.displayName()), god.line().color()),
                Hud.colored(Component.translatable(hud + "trial.sub", god.displayTitle(), god.rank()), 0xCCCCCC));
    }

    /** The first free spot around the altar where the boss fits, so it never appears inside a block. */
    private static Vec3 findSpawn(ServerLevel level, GodBoss boss, BlockPos altar) {
        int[][] offsets = {{0, -4}, {4, 0}, {-4, 0}, {0, 4}, {3, -3}, {-3, -3}, {3, 3}, {-3, 3}, {0, -2}, {2, 0}, {-2, 0}, {0, 2}};
        for (int dy = 0; dy <= 3; dy++) {
            for (int[] o : offsets) {
                Vec3 p = new Vec3(altar.getX() + 0.5 + o[0], altar.getY() + dy, altar.getZ() + 0.5 + o[1]);
                boss.setPos(p.x, p.y, p.z);
                if (level.noCollision(boss)) return p;
            }
        }
        return new Vec3(altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5);
    }

    /** Client-side ambience: runes rise from the altar. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6, pos.getY() + 1.6,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6, 0, -0.6, 0);
        }
    }
}
