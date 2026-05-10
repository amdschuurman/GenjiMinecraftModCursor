package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Genji's double-jump passive: one extra mid-air jump per airborne stretch.
 * Resets when the player touches ground (or water).
 *
 * Server-authoritative — the client sends a {@code C2SDoubleJump} packet on the
 * jump-key edge while mid-air; this class validates and applies the velocity.
 * The "puff" cue is broadcast via {@code level.playSound} so nearby players hear it.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class DoubleJumpAbility {
    private DoubleJumpAbility() {}

    /** Slightly under vanilla's 0.42 — feels controllable, doesn't punt the player into the ceiling. */
    private static final double JUMP_VELOCITY = 0.4;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.onGround() && !sp.isInWater()) return;

        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;
        if (data.isDoubleJumpUsed()) data.resetDoubleJump();
    }

    /** Called by the C2SDoubleJump packet handler. Validates state, applies the jump. */
    public static void executeDoubleJump(ServerPlayer sp) {
        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;
        // Must be airborne and not riding something to use it.
        if (sp.onGround() || sp.isInWater() || sp.isPassenger()) return;
        if (data.isDoubleJumpUsed()) return;

        Vec3 v = sp.getDeltaMovement();
        sp.setDeltaMovement(v.x, JUMP_VELOCITY, v.z);
        sp.hasImpulse = true;
        sp.fallDistance = 0;
        data.useDoubleJump();

        // Soft "puff" — phantom flap at low volume + faster pitch. Broadcast so
        // nearby players hear the air-flap.
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4f, 1.4f);
    }
}
