package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Genji's wall-climb passive: while pressing jump against a wall mid-air, climb
 * upward at a steady rate. Capped by a tick-budget that refills on ground touch.
 *
 * Server-authoritative — the client sends a {@code C2SSetWallClimb(true|false)}
 * packet when its local conditions change, but the server validates the
 * conditions itself each tick (horizontal collision, not on ground, budget left)
 * before applying velocity. Block-step sounds for the touched wall play
 * periodically so the climb feels surface-aware.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class WallClimbAbility {
    private WallClimbAbility() {}

    /** Per-tick upward velocity while climbing. Slow + deliberate, OW-feel. */
    private static final double CLIMB_VELOCITY = 0.22;

    /** Step-sound cadence while climbing — every N ticks. */
    private static final int STEP_SOUND_PERIOD_TICKS = 5;

    /** How far in front of the eye to sample the wall for sound + facing. */
    private static final double WALL_PROBE_DISTANCE = 0.6;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;

        // Refill the budget on ground / water touch — independent of whether
        // the player is currently climbing or not.
        if (sp.onGround() || sp.isInWater()) {
            if (data.getWallClimbBudgetTicks() < GenjiData.WALL_CLIMB_MAX_BUDGET_TICKS) {
                data.refillWallClimbBudget();
            }
            // Climbing is mutually exclusive with being on the ground.
            if (data.isWallClimbing()) data.setWallClimbing(false);
            return;
        }

        // Hard-stop in states where wall-climb makes no sense — riding a horse,
        // sleeping in a bed, creative-flying, spectator. Without these checks
        // a forged C2SSetWallClimb(true) could climb the player while seated.
        if (sp.isPassenger() || sp.isSleeping() || sp.getAbilities().flying) {
            if (data.isWallClimbing()) data.setWallClimbing(false);
            return;
        }

        if (!data.isWallClimbing()) return;

        // Server-side validation of the climb preconditions. Without this a
        // forged packet could climb the player up through open air.
        if (!sp.horizontalCollision) {
            data.setWallClimbing(false);
            return;
        }
        if (data.getWallClimbBudgetTicks() <= 0) {
            // Out of budget — let the player fall, they have to ground-touch
            // to refill. We keep the climbing flag set; server will simply
            // skip applying velocity until budget refills or they release.
            return;
        }

        // Apply the climb velocity (preserve horizontal-into-wall component
        // so the player stays adhered to the surface).
        Vec3 v = sp.getDeltaMovement();
        sp.setDeltaMovement(v.x, CLIMB_VELOCITY, v.z);
        sp.hasImpulse = true;
        sp.fallDistance = 0;

        data.consumeWallClimbBudget(1);

        // Periodic step sound — pick the block we're actually touching so the
        // sound matches the surface (mining-like cue per the user's ask).
        if ((sp.tickCount % STEP_SOUND_PERIOD_TICKS) == 0) {
            BlockState wallState = probeWallBlock(sp);
            if (wallState != null) {
                SoundType st = wallState.getSoundType();
                sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        st.getStepSound(), SoundSource.PLAYERS, 0.4f, 0.85f);
            }
        }
    }

    /**
     * Sample the block directly in front of the player at eye level. Returns
     * null if there's no block there (player isn't actually on a wall).
     */
    private static BlockState probeWallBlock(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 forward = sp.getLookAngle().normalize();
        // Project to the horizontal plane — wall-climb shouldn't sample
        // blocks based on where the player is looking up/down.
        Vec3 horizontalForward = new Vec3(forward.x, 0, forward.z).normalize();
        Vec3 sample = eye.add(horizontalForward.scale(WALL_PROBE_DISTANCE));
        BlockPos pos = BlockPos.containing(sample);
        BlockState state = sp.level().getBlockState(pos);
        return state.isAir() ? null : state;
    }
}
