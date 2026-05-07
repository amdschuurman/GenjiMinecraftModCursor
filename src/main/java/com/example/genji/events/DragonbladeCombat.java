package com.example.genji.events;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.config.GenjiConfig;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.registry.ModSounds;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CDragonbladeFPAnim;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DragonbladeCombat {
    private DragonbladeCombat() {}

    private static final Set<UUID> HELD_PRIMARY   = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> HELD_SECONDARY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STARTUP_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    // Marks damage that originates from our custom dragonblade LOS routine so we can
    // suppress vanilla/Better Combat duplicate damage in event hooks.
    private static final ThreadLocal<Boolean> INTERNAL_DRAGONBLADE_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static boolean isInternalDragonbladeDamage() { return Boolean.TRUE.equals(INTERNAL_DRAGONBLADE_DAMAGE.get()); }

    // Track when last swing fully completed (after recovery) for combo window
    private static final Map<UUID, Long> LAST_SWING_COMPLETION_TIME = new ConcurrentHashMap<>();

    public static void perPlayerTick(ServerPlayer sp) {
        var data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;
        if (!data.isBladeActive()) {
            STARTUP_IN_PROGRESS.remove(sp.getUUID());
            LAST_SWING_COMPLETION_TIME.remove(sp.getUUID());
            return;
        }

        // Startup -> land -> recovery
        if (STARTUP_IN_PROGRESS.contains(sp.getUUID()) && data.getSwingStartupTicks() == 0) {
            onSwingLand(sp);
            STARTUP_IN_PROGRESS.remove(sp.getUUID());
            return;
        }

        final UUID id = sp.getUUID();
        final long nowTicks = sp.level().getGameTime();

        // When recovery completes, handle combo window logic
        if (data.getSwingRecoverTicks() == 1) {
            // Recovery is about to complete this tick
            // Only set combo window if the last swing was LEFT (nextSwingIsRight = true means last was LEFT)
            if (data.nextSwingIsRight()) {
                // Last swing was LEFT, open combo window for RIGHT swing
                LAST_SWING_COMPLETION_TIME.put(id, nowTicks + 1);
            } else {
                // Last swing was RIGHT, no combo window, reset to LEFT
                LAST_SWING_COMPLETION_TIME.remove(id);
                data.resetSwingToLeft();
            }
        }

        // Check if we're outside the combo window - if so, reset to LEFT swing
        Long lastCompletionTime = LAST_SWING_COMPLETION_TIME.get(id);
        if (lastCompletionTime != null && data.canSwingNow()) {
            long timeSinceCompletion = nowTicks - lastCompletionTime;
            if (timeSinceCompletion >= GenjiConfig.DRAGONBLADE_COMBO_WINDOW_TICKS.get()) {
                // Combo window expired, reset to LEFT
                data.resetSwingToLeft();
                LAST_SWING_COMPLETION_TIME.remove(id);
            }
        }

        // Use our custom timing system to trigger attacks
        boolean held = HELD_PRIMARY.contains(id) || HELD_SECONDARY.contains(id);
        boolean canSwing = data.canSwingNow();
        
        if (held && canSwing && STARTUP_IN_PROGRESS.add(id)) {
            // Determine swing direction based on combo window
            boolean isLeftSwing;
            
            if (lastCompletionTime == null) {
                // First swing or blade just started - always LEFT
                isLeftSwing = true;
            } else {
                long timeSinceCompletion = nowTicks - lastCompletionTime;
                if (timeSinceCompletion < GenjiConfig.DRAGONBLADE_COMBO_WINDOW_TICKS.get() && data.nextSwingIsRight()) {
                    // Within combo window and ready for RIGHT swing
                    isLeftSwing = false;
                } else {
                    // Outside combo window or not ready - reset to LEFT
                    isLeftSwing = true;
                }
            }
            
            // Set up the swing
            data.startSwingStartup(!isLeftSwing);  // rightToLeft = !isLeftSwing
            
            // Set next swing direction: if this was LEFT, next can be RIGHT (within window)
            data.setNextSwingRight(isLeftSwing);

            // Trigger first-person animation
            ModNetwork.CHANNEL.sendTo(
                    new S2CDragonbladeFPAnim(isLeftSwing ? S2CDragonbladeFPAnim.Dir.LEFT : S2CDragonbladeFPAnim.Dir.RIGHT),
                    sp.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
            );

            // Trigger third-person player swing animation
            sp.swing(sp.getUsedItemHand());

            // Play swing audio at swing-start (the slice "whoosh" cue).
            // Damage is applied later in onSwingLand at the impact frame —
            // this matches pre-S9 OW Genji's startup → contact feel.
            float pitch = 0.9f + sp.getRandom().nextFloat() * 0.2f;
            sp.serverLevel().playSound(null, sp, ModSounds.DRAGONBLADE_SLICE.get(), SoundSource.PLAYERS, 1.0f, pitch);
        }
    }

    public static void setPrimaryHeld(ServerPlayer sp, boolean down) {
        if (down) {
            HELD_PRIMARY.add(sp.getUUID());
        } else {
            HELD_PRIMARY.remove(sp.getUUID());
        }
    }
    
    public static void setSecondaryHeld(ServerPlayer sp, boolean down) {
        if (down) {
            HELD_SECONDARY.add(sp.getUUID());
        } else {
            HELD_SECONDARY.remove(sp.getUUID());
        }
    }

    public static void onSwingLand(ServerPlayer sp) {
        var data  = GenjiDataProvider.getOrNull(sp);
        if (data == null || !data.isBladeActive()) return;

        boolean lastWasRightToLeft = !data.nextSwingIsRight();
        data.startSwingRecovery(lastWasRightToLeft);

        // Apply damage at the impact frame (after startup, before recovery)
        // so the swing has a small wind-up before contact — matches pre-S9
        // OW Genji's slash feel. Vanilla/Better Combat damage is suppressed
        // in CommonEvents.onEntityHurt; this LOS routine is the only path.
        triggerAttack(sp);
    }

    private static void triggerAttack(ServerPlayer sp) {
        // Get the dragonblade item
        ItemStack mainHand = sp.getMainHandItem();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof DragonbladeItem)) {
            return;
        }
        
        // Perform trace-based damage in front of player
        damageInFrontWithLOS(sp);
    }
    
    private static void damageInFrontWithLOS(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        Vec3 playerPos = sp.getEyePosition();
        Vec3 lookVec = sp.getLookAngle();
        
        // Range and Y-inflate read from config (DragonbladeCombat group)
        double range = GenjiConfig.DRAGONBLADE_REACH.get();
        double heightInflate = GenjiConfig.DRAGONBLADE_HEIGHT.get();

        // Get all entities in a cone in front of the player
        AABB searchBox = sp.getBoundingBox().inflate(range, heightInflate, range);
        var entities = level.getEntitiesOfClass(LivingEntity.class, searchBox, 
            entity -> entity != sp && entity.isAlive() && !entity.isDeadOrDying() && sp.canAttack(entity));
        
        // Check nanoboost status for damage multiplier
        var data = GenjiDataProvider.getOrNull(sp);
        boolean nanoboostActive = data != null && data.isNanoActive();
        float damageMultiplier = nanoboostActive ? 1.5f : 1.0f; // +50% damage with nanoboost
        float baseDamage = GenjiConfig.DAMAGE_PER_DRAGONBLADE_SWING.get().floatValue(); // Configurable base damage
        float finalDamage = baseDamage * damageMultiplier;
        
        INTERNAL_DRAGONBLADE_DAMAGE.set(true);
        try {
        for (LivingEntity entity : entities) {
            Vec3 entityPos = entity.getEyePosition();
            Vec3 toEntity = entityPos.subtract(playerPos);
            double distance = toEntity.length();
            
            // Check if entity is within range
            if (distance > range) continue;
            
            // Check if entity is in front of player (dot product check)
            Vec3 toEntityNormalized = toEntity.normalize();
            double dot = lookVec.dot(toEntityNormalized);
            if (dot < 0.7) continue; // Only consider entities roughly in front
            
            // Line of sight check
            if (level.clip(new net.minecraft.world.level.ClipContext(
                playerPos, entityPos, 
                net.minecraft.world.level.ClipContext.Block.COLLIDER, 
                net.minecraft.world.level.ClipContext.Fluid.NONE, sp)).getType() != 
                net.minecraft.world.phys.HitResult.Type.MISS) {
                continue; // Blocked by something
            }
            
            // Apply damage
            entity.hurt(level.damageSources().playerAttack(sp), finalDamage);
        }
        } finally {
            INTERNAL_DRAGONBLADE_DAMAGE.set(false);
        }
    }

}
