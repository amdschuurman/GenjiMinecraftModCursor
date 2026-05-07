package com.example.genji.events;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.config.GenjiConfig;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CStartDash;
import com.example.genji.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Smooth-looking dash via multiple server teleports (no easing, sudden stop at end).
 * Damages entities along the path and spawns a subtle trail each tick.
 */
public final class DashAbility {
    private DashAbility() {}

    // Feel
    private static final double RANGE = 15.0;         // blocks
    private static final int    DURATION_TICKS = 8;  // more steps = smoother (no easing)
    private static final double HIT_RADIUS = 1.5;    // dash hit cylinder half-width
    private static float getDamage() { return GenjiConfig.DASH_DAMAGE.get().floatValue(); } // Dash damage (configurable)

    private static final Map<UUID, DashState> ACTIVE = new HashMap<>();

    // Marks damage that originates from our dash so other hooks can avoid canceling it
    private static final ThreadLocal<Boolean> INTERNAL_DASH_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static boolean isInternalDashDamage() { return Boolean.TRUE.equals(INTERNAL_DASH_DAMAGE.get()); }

    private record DashState(Vec3 start, Vec3 end, int total, int tick, Set<UUID> hit) {
        DashState advance() { return new DashState(start, end, total, tick + 1, hit); }
    }

    /** Check if a player is currently dashing. */
    public static boolean isDashing(ServerPlayer sp) {
        return ACTIVE.containsKey(sp.getUUID());
    }

    /** Called from packet to start a dash if cooldown allows. */
    public static void startDash(ServerPlayer sp, boolean bladeActive) {
        var data = GenjiDataProvider.get(sp);
        boolean cancelDeflect = data.isDeflectActive();
        if (!data.tryDash()) return; // cooldown gate

        if (cancelDeflect) {
            data.cancelDeflectStartCooldown(); // emulate manual deflect cancel
        }

        // Compute endpoint along crosshair up to RANGE, stop just before collision
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        Vec3 wanted = eye.add(look.scale(RANGE));
        ServerLevel level = sp.serverLevel();

        HitResult hr = level.clip(new ClipContext(
                eye, wanted,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                sp
        ));

        Vec3 end;
        if (hr.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) hr;
            end = bhr.getLocation().subtract(look.scale(0.35)); // avoid clipping into the block
        } else {
            end = wanted;
        }

        // Keep Y sane
        end = new Vec3(end.x, Math.max(level.getMinBuildHeight() + 1, end.y), end.z);

        // Calculate actual distance and scale duration accordingly
        Vec3 startPos = sp.position();
        double actualDistance = startPos.distanceTo(end);
        double distanceRatio = actualDistance / RANGE;
        int scaledDuration = Math.max(2, (int)(DURATION_TICKS * distanceRatio)); // Min 2 ticks

        DashState state = new DashState(startPos, end, scaledDuration, 0, new HashSet<>());
        ACTIVE.put(sp.getUUID(), state);

        // Send dash info to client for smooth interpolation
        ModNetwork.CHANNEL.sendTo(
            new S2CStartDash(startPos, end, scaledDuration),
            sp.connection.connection,
            net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
        );

        // Audio & prep
        level.playSound(null, sp, ModSounds.DASH.get(), SoundSource.PLAYERS, 2.0f, 1.0f);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;
        sp.fallDistance = 0;

        // Small starting burst of particles
        spawnStartBurst(level, sp.position(), sp.getLookAngle());
    }

    /** Drive active dashes. Call once per server tick (ServerTicks hooks this). */
    public static void serverTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        List<UUID> done = new ArrayList<>();

        for (var entry : ACTIVE.entrySet()) {
            UUID id = entry.getKey();
            DashState st = entry.getValue();

            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null || !sp.isAlive() || st.tick >= st.total) {
                done.add(id);
                continue;
            }

            // Linear interpolation (no easing) → sudden stop at end
            double t0 = (double) st.tick / st.total;
            double t1 = (double) (st.tick + 1) / st.total;

            Vec3 prev = lerp(st.start, st.end, t0);
            Vec3 next = lerp(st.start, st.end, t1);

            // Teleport a small step forward
            sp.connection.teleport(next.x, next.y, next.z, sp.getYRot(), sp.getXRot());
            sp.setDeltaMovement(Vec3.ZERO);
            sp.hurtMarked = true;
            sp.fallDistance = 0;

            // Damage entities swept by this segment (once per target)
            damageAlongSegment(sp, prev, next, st.hit);

            // Trail along this step to sell continuity
            spawnStepTrail(sp.serverLevel(), prev, next);

            ACTIVE.put(id, st.advance());
            if (st.tick + 1 >= st.total) {
                done.add(id); // hard stop at final tick
            }
        }

        done.forEach(ACTIVE::remove);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private static void damageAlongSegment(ServerPlayer sp, Vec3 from, Vec3 to, Set<UUID> alreadyHit) {
        ServerLevel level = sp.serverLevel();
        AABB box = new AABB(from, to).inflate(HIT_RADIUS, HIT_RADIUS * 0.6, HIT_RADIUS);
        List<Entity> targets = level.getEntities(sp, box, e ->
                e.isAlive() && e.isAttackable() && e != sp && !alreadyHit.contains(e.getUUID()));
        if (targets.isEmpty()) return;

        DamageSource src = level.damageSources().playerAttack(sp);
        INTERNAL_DASH_DAMAGE.set(true);
        try {
        for (Entity e : targets) {
            if (!e.isAlive()) continue;
            
            // Deal damage
            boolean wasAlive = e.isAlive();
            e.hurt(src, getDamage());
            alreadyHit.add(e.getUUID());
            
            // Check if damage was actually dealt and send hit sound
            if (wasAlive && e instanceof LivingEntity) {
                
                // Get player data to check nano status
                sp.getCapability(com.example.genji.capability.GenjiDataProvider.CAPABILITY).ifPresent(data -> {
                    boolean isNanoActive = data.isNanoActive();
                    
                    // Always play shuriken hit sounds for dash (normal or nano based on status)
                    String soundType = isNanoActive ? "shuriken_nano" : "shuriken";
                    com.example.genji.network.ModNetwork.CHANNEL.sendTo(
                        new com.example.genji.network.packet.S2CPlayHitSound(soundType), 
                        sp.connection.connection, 
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
                    );
                });
            }
        }
        } finally {
            INTERNAL_DASH_DAMAGE.set(false);
        }
    }

    private static void spawnStartBurst(ServerLevel level, Vec3 pos, Vec3 dir) {
        // Bigger burst at start with fast-dissipating green particles
        for (int i = 0; i < 15; i++) {
            double s = 0.15 + i * 0.02;
            // Composter particles (green) - dissipate very quickly
            level.sendParticles(ParticleTypes.COMPOSTER,
                    pos.x, pos.y + 0.5, pos.z,
                    2, dir.x * s, dir.y * s, dir.z * s, 0.1);
        }
    }

    private static void spawnStepTrail(ServerLevel level, Vec3 from, Vec3 to) {
        // Green composter particles with fast dissipation
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        int steps = 6;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) (steps + 1);
            double px = from.x + dx * t;
            double py = from.y + dy * t + 0.5;
            double pz = from.z + dz * t;
            
            // Composter particles (green) - dissipate quickly, bigger spread
            level.sendParticles(ParticleTypes.COMPOSTER,
                    px, py, pz,
                    3, 0.15, 0.15, 0.15, 0.05);
        }
    }
}
