package com.example.genji.events;

import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.config.GenjiConfig;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CDragonbladeFPAnim;
import com.example.genji.registry.ModItems;
import com.example.genji.registry.ModSounds;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side dragonblade combat: per-tick swing FSM (startup → land → recovery
 * with a LEFT→RIGHT combo window) and the LOS-based damage routine that runs at
 * the impact frame.
 *
 * Public surface: {@link #perPlayerTick}, {@link #setPrimaryHeld},
 * {@link #setSecondaryHeld}, {@link #onSwingLand}, {@link #isInternalDragonbladeDamage}.
 * Lifecycle hooks: {@link #onPlayerLoggedOut}, {@link #onServerStopped} (called from
 * {@link StateCleanup}).
 */
public final class DragonbladeCombat {
    private DragonbladeCombat() {}

    private static final Set<UUID> HELD_PRIMARY        = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> HELD_SECONDARY      = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> STARTUP_IN_PROGRESS = ConcurrentHashMap.newKeySet();

    /** Game-tick when the player's last completed swing was on. Used to gate the
     *  10-tick LEFT→RIGHT combo window. Only present for players who just finished
     *  a LEFT swing — RIGHT-swing completion clears the entry. */
    private static final Map<UUID, Long> LAST_SWING_COMPLETION_TIME = new ConcurrentHashMap<>();

    /** Marks damage that originates from our LOS routine, so {@link CommonEvents#onEntityHurt}
     *  knows to allow it through (instead of suppressing as vanilla/BC duplicate damage). */
    private static final ThreadLocal<Boolean> INTERNAL_DRAGONBLADE_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static boolean isInternalDragonbladeDamage() { return Boolean.TRUE.equals(INTERNAL_DRAGONBLADE_DAMAGE.get()); }

    // ===== Public per-tick driver =====

    public static void perPlayerTick(ServerPlayer sp) {
        final GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;

        if (cancelOrphanBladeIfNoWeapon(sp, data)) return;
        if (!data.isBladeActive()) {
            clearPerPlayerState(sp.getUUID());
            return;
        }
        if (handleStartupLand(sp, data)) return;

        final UUID id = sp.getUUID();
        final long nowTicks = sp.level().getGameTime();

        handleRecoveryCompletion(data, id, nowTicks);
        expireComboWindowIfStale(data, id, nowTicks);

        boolean held = HELD_PRIMARY.contains(id) || HELD_SECONDARY.contains(id);
        if (held && data.canSwingNow()) {
            tryStartSwing(sp, data, id, nowTicks, LAST_SWING_COMPLETION_TIME.get(id));
        }
    }

    // ===== State machine sub-steps =====

    /**
     * If the player is in any blade phase (cast/active/sheathe) but their main
     * hand is no longer a dragonblade (admin /clear, mod conflict, capability
     * desync, or the player dragged the dragonblade item to offhand/main inv),
     * force-cancel rather than running a zombie phase with no weapon — AND
     * sweep any orphaned dragonblade items out of the inventory so they
     * can't be re-equipped later. Returns true if the blade was cancelled.
     */
    private static boolean cancelOrphanBladeIfNoWeapon(ServerPlayer sp, GenjiData data) {
        boolean inAnyBladePhase = data.isBladeActive() || data.isCastingBlade() || data.isSheathing();
        if (!inAnyBladePhase) return false;
        if (sp.getMainHandItem().getItem() instanceof DragonbladeItem) return false;

        data.cancelBlade();
        data.clearBladeSlot();
        clearPerPlayerState(sp.getUUID());

        // Sweep all 41 inventory slots — main hotbar, main inv, armor, offhand —
        // converting any dragonblade items to shurikens. The blade was just
        // orphaned; leaving the item to linger would make it a permanent
        // unusable bauble (and exploit bait — see DragonbladeIntegrityHandler).
        Inventory inv = sp.getInventory();
        boolean anyReplaced = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.DRAGONBLADE.get())) {
                inv.setItem(i, new ItemStack(ModItems.SHURIKEN.get()));
                anyReplaced = true;
            }
        }
        if (anyReplaced) sp.inventoryMenu.broadcastChanges();

        return true;
    }

    /**
     * If a swing's startup ticks just hit zero, transition to recovery (and apply
     * damage via {@link #onSwingLand}). Returns true if the caller should exit
     * because the tick has been consumed by a swing-land transition.
     */
    private static boolean handleStartupLand(ServerPlayer sp, GenjiData data) {
        if (!STARTUP_IN_PROGRESS.contains(sp.getUUID())) return false;
        if (data.getSwingStartupTicks() != 0) return false;

        onSwingLand(sp);
        STARTUP_IN_PROGRESS.remove(sp.getUUID());
        return true;
    }

    /**
     * On the tick that a swing's recovery ticks would hit zero (swingRecover==1
     * means "this tick is the last recovery tick"), open the LEFT→RIGHT combo
     * window if the just-completed swing was LEFT, otherwise reset to LEFT.
     *
     * The +1 on the stored tick is intentional — combo window opens NEXT tick,
     * not this tick, so it can't be triggered by a same-tick re-press.
     */
    private static void handleRecoveryCompletion(GenjiData data, UUID id, long nowTicks) {
        if (data.getSwingRecoverTicks() != 1) return;

        if (data.nextSwingIsRight()) {
            // Last swing was LEFT (so next is RIGHT). Open the combo window.
            LAST_SWING_COMPLETION_TIME.put(id, nowTicks + 1);
        } else {
            // Last swing was RIGHT — combo chain done, reset to LEFT.
            LAST_SWING_COMPLETION_TIME.remove(id);
            data.resetSwingToLeft();
        }
    }

    /**
     * If the combo window has expired since the last LEFT swing completed, reset
     * back to LEFT and drop the completion-time entry.
     */
    private static void expireComboWindowIfStale(GenjiData data, UUID id, long nowTicks) {
        Long lastCompletionTime = LAST_SWING_COMPLETION_TIME.get(id);
        if (lastCompletionTime == null) return;
        if (!data.canSwingNow()) return;

        long timeSinceCompletion = nowTicks - lastCompletionTime;
        if (timeSinceCompletion >= GenjiConfig.DRAGONBLADE_COMBO_WINDOW_TICKS.get()) {
            data.resetSwingToLeft();
            LAST_SWING_COMPLETION_TIME.remove(id);
        }
    }

    /**
     * Begin a swing if the player is holding a mouse button and {@link GenjiData#canSwingNow}.
     * The {@code STARTUP_IN_PROGRESS.add} call has the side-effect-on-success
     * semantic from {@link Set#add} — only the first add this tick proceeds.
     */
    private static void tryStartSwing(ServerPlayer sp, GenjiData data, UUID id, long nowTicks, Long lastCompletionTime) {
        if (!STARTUP_IN_PROGRESS.add(id)) return;

        boolean isLeftSwing = computeIsLeftSwing(data, lastCompletionTime, nowTicks);

        // Configure the swing FSM.
        data.startSwingStartup(!isLeftSwing); // rightToLeft = !isLeftSwing
        // After this swing, the *next* one's direction is the opposite.
        data.setNextSwingRight(isLeftSwing);

        emitSwingAnimAndAudio(sp, isLeftSwing);
    }

    /**
     * Determine which direction the next swing should be. Pure function over
     * the inputs — no side effects.
     */
    private static boolean computeIsLeftSwing(GenjiData data, Long lastCompletionTime, long nowTicks) {
        if (lastCompletionTime == null) return true; // first swing of the blade — always LEFT

        long timeSinceCompletion = nowTicks - lastCompletionTime;
        boolean inComboWindow = timeSinceCompletion < GenjiConfig.DRAGONBLADE_COMBO_WINDOW_TICKS.get();
        // RIGHT only if we're inside the window AND the FSM says next-is-RIGHT.
        // Otherwise (timed out or out of phase) — fall back to LEFT.
        return !(inComboWindow && data.nextSwingIsRight());
    }

    /**
     * Send the FP animation packet + trigger the TPS arm swing + play the slice
     * "whoosh" cue at swing-start. Damage is applied later in {@link #onSwingLand}
     * at the impact frame — the audio cue at swing-start matches OW's startup feel.
     */
    private static void emitSwingAnimAndAudio(ServerPlayer sp, boolean isLeftSwing) {
        S2CDragonbladeFPAnim.Dir dir = isLeftSwing ? S2CDragonbladeFPAnim.Dir.LEFT : S2CDragonbladeFPAnim.Dir.RIGHT;
        ModNetwork.sendToPlayer(sp, new S2CDragonbladeFPAnim(dir));
        sp.swing(sp.getUsedItemHand());

        float pitch = 0.9f + sp.getRandom().nextFloat() * 0.2f;
        sp.serverLevel().playSound(null, sp, ModSounds.DRAGONBLADE_SLICE.get(), SoundSource.PLAYERS, 1.0f, pitch);

        // Slash trail: a SWEEP_ATTACK arc ~1.5 blocks in front of the eye, biased
        // slightly upward so the visual reads as a horizontal slash, not a footprint.
        Vec3 eye = sp.getEyePosition();
        Vec3 forward = sp.getLookAngle();
        Vec3 trail = eye.add(forward.scale(1.5));
        sp.serverLevel().sendParticles(
                net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                trail.x, trail.y, trail.z,
                1, 0.0, 0.0, 0.0, 0.0
        );
    }

    private static void clearPerPlayerState(UUID id) {
        STARTUP_IN_PROGRESS.remove(id);
        LAST_SWING_COMPLETION_TIME.remove(id);
    }

    // ===== Lifecycle hooks =====

    /** Drop per-player runtime state on logout. Called from {@link StateCleanup}. */
    public static void onPlayerLoggedOut(UUID id) {
        HELD_PRIMARY.remove(id);
        HELD_SECONDARY.remove(id);
        STARTUP_IN_PROGRESS.remove(id);
        LAST_SWING_COMPLETION_TIME.remove(id);
    }

    /** Reset all global state on server stop. Called from {@link StateCleanup}. */
    public static void onServerStopped() {
        HELD_PRIMARY.clear();
        HELD_SECONDARY.clear();
        STARTUP_IN_PROGRESS.clear();
        LAST_SWING_COMPLETION_TIME.clear();
    }

    // ===== Input handlers (called from C2SSetPrimaryHeld / C2SSetSecondaryHeld) =====

    public static void setPrimaryHeld(ServerPlayer sp, boolean down) {
        if (down) HELD_PRIMARY.add(sp.getUUID());
        else      HELD_PRIMARY.remove(sp.getUUID());
    }

    public static void setSecondaryHeld(ServerPlayer sp, boolean down) {
        if (down) HELD_SECONDARY.add(sp.getUUID());
        else      HELD_SECONDARY.remove(sp.getUUID());
    }

    // ===== Swing-land + damage =====

    /**
     * Called when a swing's startup ticks complete (impact frame). Starts the
     * recovery counter and applies LOS-based damage. Vanilla/BC swing damage
     * for the dragonblade is suppressed in {@link CommonEvents#onEntityHurt};
     * this is the only path that actually deals dragonblade damage.
     */
    public static void onSwingLand(ServerPlayer sp) {
        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null || !data.isBladeActive()) return;

        boolean lastWasRightToLeft = !data.nextSwingIsRight();
        data.startSwingRecovery(lastWasRightToLeft);

        damageInFrontWithLOS(sp);
    }

    private static void damageInFrontWithLOS(ServerPlayer sp) {
        ItemStack mainHand = sp.getMainHandItem();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof DragonbladeItem)) return;

        final ServerLevel level = sp.serverLevel();
        final Vec3 eye = sp.getEyePosition();
        final Vec3 lookVec = sp.getLookAngle();

        // Range and Y-inflate read from config (DragonbladeCombat group).
        final double range = GenjiConfig.DRAGONBLADE_REACH.get();
        final double heightInflate = GenjiConfig.DRAGONBLADE_HEIGHT.get();

        final AABB searchBox = sp.getBoundingBox().inflate(range, heightInflate, range);
        var entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                target -> target != sp && target.isAlive() && !target.isDeadOrDying() && sp.canAttack(target));

        // Nano damage multiplier flows through GenjiData.getNanoDamageMultiplier
        // which returns 1.0 when nano is inactive — so this is the unified path
        // for shuriken/dash/deflect/dragonblade nano scaling.
        GenjiData data = GenjiDataProvider.getOrNull(sp);
        float damageMultiplier = (data != null) ? (float) data.getNanoDamageMultiplier() : 1.0f;
        float baseDamage = GenjiConfig.DAMAGE_PER_DRAGONBLADE_SWING.get().floatValue();
        float finalDamage = baseDamage * damageMultiplier;

        INTERNAL_DRAGONBLADE_DAMAGE.set(true);
        try {
            for (LivingEntity target : entities) {
                if (!isInForwardCone(sp, target, eye, lookVec, range)) continue;
                if (!hasLineOfSight(level, sp, eye, target.getEyePosition())) continue;

                target.hurt(level.damageSources().playerAttack(sp), finalDamage);
                // Server-broadcast the slash-hit ambient sound at the victim's
                // position so everyone nearby hears it.
                level.playSound(null, target.blockPosition(),
                        ModSounds.DRAGONBLADE_HIT.get(),
                        SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        } finally {
            INTERNAL_DRAGONBLADE_DAMAGE.set(false);
        }
    }

    /** True if {@code target} is within {@code range} blocks AND inside the
     *  ~45° forward cone (dot ≥ 0.7) from {@code eye}. */
    private static boolean isInForwardCone(ServerPlayer sp, LivingEntity target, Vec3 eye, Vec3 lookVec, double range) {
        Vec3 toTarget = target.getEyePosition().subtract(eye);
        double distance = toTarget.length();
        if (distance > range) return false;

        Vec3 toTargetNormalized = toTarget.normalize();
        double dot = lookVec.dot(toTargetNormalized);
        return dot >= 0.7;
    }

    /** True if there's no block between {@code from} and {@code to}. */
    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer sp, Vec3 from, Vec3 to) {
        var clipResult = level.clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        return clipResult.getType() == HitResult.Type.MISS;
    }
}
