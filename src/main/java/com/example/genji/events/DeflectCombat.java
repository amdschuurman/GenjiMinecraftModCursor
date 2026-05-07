package com.example.genji.events;

import com.example.genji.config.GenjiConfig;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CDeflectHit;
import com.example.genji.registry.ModSounds;
import com.example.genji.util.AdvancementHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deflect:
 *  - Cone + LOS
 *  - Re-emit FROM camera center TOWARD crosshair
 *  - Min speeds uit config voor "stuck" projectiles
 *  - Ping SFX (volume uit config)
 *  - 1 hit-anim packet per tick als er iets gereflect is
 *  - SAFE: geen crash als capability (nog) niet aanwezig is
 */
public final class DeflectCombat {
    private DeflectCombat() {}

    private static final Map<Integer, Long> RECENT = new HashMap<>();

    /** Reset re-reflect-cooldown map on server stop. RECENT is keyed by entity id, not player UUID, so no per-player cleanup is needed. */
    public static void onServerStopped() {
        RECENT.clear();
    }

    public static void perPlayerTick(ServerPlayer sp) {
        // Verdere safety (kan voorkomen op death/logout-frames)
        if (sp == null || sp.isRemoved() || !sp.isAlive()) return;

        // SAFETY: capability kan incidenteel ontbreken (spawn/death/login). Nooit hard throwen.
        var data = safeGetData(sp);
        if (data == null) return;

        if (data.getDeflectTicks() <= 0) return;

        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if ((now & 0xF) == 0) RECENT.entrySet().removeIf(e -> e.getValue() <= now);

        final double reach   = GenjiConfig.DEFLECT_REACH.get();
        final double width   = GenjiConfig.DEFLECT_WIDTH.get();
        final double height  = GenjiConfig.DEFLECT_HEIGHT.get();
        final double dotMin  = GenjiConfig.DEFLECT_CONE_DOT_MIN.get();
        final int    cooldown= GenjiConfig.DEFLECT_REREFLECT_COOLDOWN_TICKS.get();

        Vec3 eye  = sp.getEyePosition();
        Vec3 look = sp.getLookAngle().normalize();
        Vec3 tip  = eye.add(look.scale(reach));
        AABB box  = new AABB(eye, tip).inflate(width * 0.5, height * 0.5, width * 0.5);

        List<Entity> list = level.getEntities(sp, box, e -> e instanceof Projectile);
        boolean anyReflected = false;

        for (Entity e : list) {
            if (!(e instanceof Projectile proj)) continue;

            int id = proj.getId();
            if (RECENT.getOrDefault(id, 0L) > now) continue;

            Vec3 to = e.position().subtract(sp.position()).normalize();
            if (to.dot(look) < dotMin) continue;

            HitResult hit = level.clip(new ClipContext(eye, e.position(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
            if (hit.getType() == HitResult.Type.BLOCK) {
                double blockDist  = hit.getLocation().distanceTo(eye);
                double targetDist = e.position().distanceTo(eye);
                if (blockDist <= targetDist - 0.1) continue;
            }

            // Snelheden
            double current = proj.getDeltaMovement().length();
            
            // Skip stationary projectiles (stuck in ground/walls or otherwise not moving)
            // This catches arrows/shurikens stuck in blocks, as well as any other stationary projectiles
            if (current < 0.01) {
                continue; // Projectile is essentially stationary, don't deflect
            }
            
            double min = minSpeed(proj);
            float  speed = (float) Math.max(current, min);

            // Re-emit van camera center naar crosshair
            Vec3 origin = eye.add(look.scale(0.2));
            proj.setPos(origin.x, origin.y, origin.z);
            proj.shoot(look.x, look.y, look.z, speed, 0.0F);

            if (proj instanceof AbstractHurtingProjectile ahp) {
                ahp.setDeltaMovement(look.scale(speed));
            }
            if (proj instanceof AbstractArrow arr) {
                arr.setDeltaMovement(arr.getDeltaMovement().add(0.0, 0.02, 0.0));
            }

            proj.setOwner(sp);
            proj.hasImpulse = true;
            proj.hurtMarked = true;

            // Ping
            float vol   = GenjiConfig.DEFLECT_PING_VOLUME.get().floatValue();
            float pitch = 0.95f + sp.getRandom().nextFloat() * 0.1f;
            level.playSound(null, origin.x, origin.y, origin.z,
                    ModSounds.DEFLECT_PING.get(), SoundSource.PLAYERS, vol, pitch);

            RECENT.put(id, now + cooldown);
            anyReflected = true;
        }

        if (anyReflected) {
            // Grant deflect achievement on first successful deflect
            AdvancementHelper.grantAdvancement(sp, ResourceLocation.fromNamespaceAndPath("genji", "first_deflect"));
            
            int variant = 1 + sp.getRandom().nextInt(3);
            ModNetwork.CHANNEL.sendTo(new S2CDeflectHit(variant),
                    sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    /** Read GenjiData without throwing; returns null if not (yet) attached. */
    private static com.example.genji.capability.GenjiData safeGetData(ServerPlayer sp) {
        return com.example.genji.capability.GenjiDataProvider.getOrNull(sp);
    }

    private static double minSpeed(Projectile p) {
        if (p instanceof AbstractArrow)             return GenjiConfig.DEFLECT_MIN_SPEED_ARROW.get();
        if (p instanceof AbstractHurtingProjectile) return GenjiConfig.DEFLECT_MIN_SPEED_HURTING.get();
        return GenjiConfig.DEFLECT_MIN_SPEED_DEFAULT.get();
    }
}
