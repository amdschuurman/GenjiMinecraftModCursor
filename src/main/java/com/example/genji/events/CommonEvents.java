package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.config.GenjiConfig;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.content.ShurikenEntity;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CPlayHitSound;
import com.example.genji.registry.ModItems;
import com.example.genji.registry.ModSounds;
import com.example.genji.util.AdvancementHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side per-tick + on-hurt event dispatch for the Genji ability state machine.
 *
 * Single subscriber per event by design — Forge does not guarantee ordering between
 * subscribers and several edge transitions in here depend on {@code data.tick()}
 * running before the prev-state-vs-current-state edge checks. The work is split
 * into focused private helpers; the public subscribers are thin orchestrators.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    /** 5s pre-end timing for the dragonblade ending cue. User-tuned, intentional. */
    private static final int ENDING_CUE_LEAD_TICKS = 5 * 20;

    /** Throttle period mask for periodic effect re-application. (tickCount &amp; mask) == 0 fires every {@code mask + 1} ticks. */
    private static final int NANO_REAPPLY_PERIOD_MASK = 3;

    /** Effect duration when re-applied — must exceed {@code NANO_REAPPLY_PERIOD_MASK + 1} by ≥ 1 to avoid gaps. */
    private static final int NANO_EFFECT_DURATION_TICKS = 6;

    // ===== onPlayerTick =====

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;

        p.getCapability(GenjiDataProvider.CAPABILITY).ifPresent(data -> {
            // Snapshot prev-state BEFORE tick() so the transition helpers can detect edges.
            final int prevCast    = data.getBladeCastTicks();
            final int prevBlade   = data.getBladeTicks();
            final int prevSheathe = data.getBladeSheatheTicks();

            data.tick();

            if (p instanceof ServerPlayer sp) {
                applyNanoAndSpeedEffects(sp, data);
            }
            handleCastToActiveTransition(p, data, prevCast);
            playEndingCueIfDue(p, data);
            handleActiveToSheatheTransition(data, prevBlade);
            handleSheatheToDoneTransition(p, data, prevSheathe);

            if (p instanceof ServerPlayer sp) {
                DragonbladeCombat.perPlayerTick(sp);
                ModNetwork.syncIfDirty(sp, data);
            }
        });
    }

    /**
     * Apply nano-boost defensive buffs (Resistance / FireRes / Absorption) and the combined
     * nano+blade movement speed effect. Periodic effects are throttled to once every 4 ticks
     * with effect duration 6 — 2 ticks of overlap means there's no gap between re-applies
     * and we save ~75% of the per-tick {@code addEffect} overhead the prior code paid.
     * The instant-heal one-shot stays ungated so it never gets missed by the throttle phase.
     */
    private static void applyNanoAndSpeedEffects(ServerPlayer sp, GenjiData data) {
        if (sp.level().isClientSide) return;

        final boolean nanoActive  = data.isNanoActive();
        final boolean bladeActive = data.isBladeActive();

        if (nanoActive && data.nanoJustActivated()) {
            int amp = Math.max(0, GenjiConfig.NANO_INSTANT_HEALTH_AMPLIFIER.get());
            sp.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, amp, false, false, true));
        }

        if ((sp.tickCount & NANO_REAPPLY_PERIOD_MASK) != 0) return;

        final int dur = NANO_EFFECT_DURATION_TICKS;

        if (nanoActive) {
            int resAmp = GenjiConfig.NANO_RESISTANCE_AMPLIFIER.get();
            if (resAmp >= 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, resAmp, false, false, true));
            }
            int fireAmp = GenjiConfig.NANO_FIRE_RES_AMPLIFIER.get();
            if (fireAmp >= 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, dur, fireAmp, false, false, true));
            }
            int absorbAmp = GenjiConfig.NANO_ABSORPTION_AMPLIFIER.get();
            if (absorbAmp >= 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, absorbAmp, false, false, true));
            }
        }

        if (nanoActive || bladeActive) {
            // Speed IV when both active, Speed II when only one.
            int speedAmp = (nanoActive && bladeActive) ? 3 : 1;
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, speedAmp, false, false, true));
        }
    }

    /**
     * Cast → Active edge. Activates the blade, grants the nano-blade combo achievement
     * if applicable, and fires the ending cue early in the rare case where the configured
     * total active duration is shorter than the cue lead.
     */
    private static void handleCastToActiveTransition(Player p, GenjiData data, int prevCast) {
        boolean justActivated = prevCast > 0 && data.getBladeCastTicks() == 0 && data.getBladeTicks() == 0;
        if (!justActivated) return;

        data.activateBlade();

        if (!(p instanceof ServerPlayer sp)) return;

        if (data.isNanoActive()) {
            AdvancementHelper.grantAdvancement(
                    sp,
                    ResourceLocation.fromNamespaceAndPath("genji", "nano_blade_combo")
            );
        }

        int totalActiveTicks = GenjiConfig.secToTicksClamped(GenjiConfig.DRAGONBLADE_DURATION_SECONDS);
        if (totalActiveTicks <= ENDING_CUE_LEAD_TICKS && !data.bladeEndingPlayed()) {
            playEndingCueSound(sp);
            data.markBladeEndingPlayed();
        }
    }

    /** Standalone path: play the ending cue exactly {@link #ENDING_CUE_LEAD_TICKS} before sheathe. */
    private static void playEndingCueIfDue(Player p, GenjiData data) {
        if (data.bladeEndingPlayed()) return;
        if (data.getBladeTicks() != ENDING_CUE_LEAD_TICKS) return;
        if (!(p instanceof ServerPlayer sp)) return;

        playEndingCueSound(sp);
        data.markBladeEndingPlayed();
    }

    private static void playEndingCueSound(ServerPlayer sp) {
        sp.level().playSound(
                null, sp.blockPosition(),
                ModSounds.DRAGONBLADE_ENDING.get(),
                SoundSource.PLAYERS, 1.0f, 1.0f
        );
    }

    /** Active → Sheathe edge: bladeTicks just hit zero. Start the sheathe phase. */
    private static void handleActiveToSheatheTransition(GenjiData data, int prevBlade) {
        if (prevBlade > 0 && data.getBladeTicks() == 0 && !data.isSheathing()) {
            data.endBladeStartSheathe();
        }
    }

    /**
     * Sheathe → Done edge: swap the dragonblade item back to a shuriken in the
     * remembered slot (or fall back to a hotbar scan if the remembered slot is
     * invalid), then clear the slot record.
     */
    private static void handleSheatheToDoneTransition(Player p, GenjiData data, int prevSheathe) {
        boolean justFinished = prevSheathe > 0 && data.getBladeSheatheTicks() == 0;
        if (!justFinished) return;
        if (!(p instanceof ServerPlayer sp)) return;

        final int rememberedSlot = data.getBladeSlot();
        if (rememberedSlot >= 0 && rememberedSlot < 9) {
            sp.getInventory().setItem(rememberedSlot, new ItemStack(ModItems.SHURIKEN.get()));
            sp.getInventory().selected = rememberedSlot;
        } else {
            // Fallback: find any dragonblade in hotbar and swap to shuriken.
            for (int i = 0; i < 9; i++) {
                ItemStack stack = sp.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(ModItems.DRAGONBLADE.get())) {
                    sp.getInventory().setItem(i, new ItemStack(ModItems.SHURIKEN.get()));
                    sp.getInventory().selected = i;
                    break;
                }
            }
        }
        sp.inventoryMenu.broadcastChanges();
        data.clearBladeSlot();
    }

    // ===== onEntityHurt =====

    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent e) {
        // Swift Strike i-frames first — cancels any damage to a dashing player
        // before any attacker-side processing.
        if (e.getEntity() instanceof ServerPlayer victim && DashAbility.isDashing(victim)) {
            e.setCanceled(true);
            return;
        }

        final ServerPlayer attacker = resolveAttacker(e);
        if (attacker == null) return;

        final boolean shurikenHit = e.getSource().getDirectEntity() instanceof ShurikenEntity;
        if (shurikenHit) {
            // Shurikens bypass i-frames so they always deal damage even if the victim
            // was hit by another source the same tick.
            e.getEntity().invulnerableTime = 0;
        }

        final GenjiData data = GenjiDataProvider.getOrNull(attacker);
        if (data == null) return;

        if (shouldSuppressDragonbladeDamage(attacker, data)) {
            e.setCanceled(true);
            return;
        }

        if (e.getAmount() <= 0f) return;
        // Cap counted damage by victim's remaining HP — overkill must not over-credit
        // ult/nano. Computed BEFORE any nano multiplier mutates the event amount.
        final float effectiveDamage = Math.min(e.getAmount(), e.getEntity().getHealth());

        applyNanoDamageMultiplier(e, attacker, data, shurikenHit);
        accrueChargeMeters(attacker, data, shurikenHit, effectiveDamage);
        dispatchHitSound(attacker, data, shurikenHit);
        ModNetwork.syncTo(attacker, data);
    }

    /**
     * Resolve the attacking player, preferring a direct ServerPlayer attacker, falling
     * back to the projectile-owner if the damage came from a thrown/shot projectile.
     * Returns null if no player can be attributed.
     */
    private static ServerPlayer resolveAttacker(LivingHurtEvent e) {
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer sp) return sp;

        Entity direct = e.getSource().getDirectEntity();
        if (direct instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    /**
     * True if this is a dragonblade-flavored damage event that should be suppressed.
     * Vanilla and Better Combat both fire melee swing damage when the player swings
     * a sword — but the dragonblade timing/range logic lives entirely in
     * {@link DragonbladeCombat}, so vanilla/BC swings would be duplicate damage.
     * Our internal LOS routine and dash damage are exempted via ThreadLocal sentinels.
     */
    private static boolean shouldSuppressDragonbladeDamage(ServerPlayer attacker, GenjiData data) {
        ItemStack item = attacker.getMainHandItem();
        if (item.isEmpty() || !(item.getItem() instanceof DragonbladeItem)) return false;
        if (!data.isBladeActive()) return false;
        return !DragonbladeCombat.isInternalDragonbladeDamage()
                && !DashAbility.isInternalDashDamage();
    }

    /**
     * Multiply event damage by the nano damage multiplier for shuriken, dash, and
     * deflected-projectile hits when nano is active. Dragonblade damage already gets
     * the same multiplier inside {@link DragonbladeCombat#damageInFrontWithLOS} since
     * its hits are tagged INTERNAL_DRAGONBLADE_DAMAGE and skip this path entirely.
     */
    private static void applyNanoDamageMultiplier(LivingHurtEvent e, ServerPlayer sp, GenjiData data, boolean shurikenHit) {
        if (!data.isNanoActive()) return;

        boolean isDashing = DashAbility.isDashing(sp);
        // Deflected-projectile detection: any projectile (other than a shuriken — those
        // are already handled above) whose owner is the attacker. Reference equality
        // is fine because Forge keeps owner refs stable across the projectile's life.
        Entity direct = e.getSource().getDirectEntity();
        boolean isDeflectedProjectile = !shurikenHit
                && direct instanceof Projectile proj
                && proj.getOwner() == sp;

        if (!(shurikenHit || isDashing || isDeflectedProjectile)) return;

        float mult = (float) data.getNanoDamageMultiplier();
        if (mult > 1.0f) {
            e.setAmount(e.getAmount() * mult);
        }
    }

    /**
     * Charge nano + ult meters from this hit. Nano accrues from any hit while the
     * attacker is holding a shuriken or dragonblade (or fired a shuriken). Ult does
     * NOT accrue while blade is active — once you've ulted you can't keep building it.
     */
    private static void accrueChargeMeters(ServerPlayer sp, GenjiData data, boolean shurikenHit, float effectiveDamage) {
        boolean holdingShuriken = sp.getMainHandItem().is(ModItems.SHURIKEN.get())
                || sp.getOffhandItem().is(ModItems.SHURIKEN.get());
        boolean holdingDragonblade = sp.getMainHandItem().is(ModItems.DRAGONBLADE.get())
                || sp.getOffhandItem().is(ModItems.DRAGONBLADE.get());

        if (shurikenHit || holdingShuriken || holdingDragonblade) {
            data.addNanoFromDamage(effectiveDamage);
        }
        if (!data.isBladeActive()) {
            data.addUltFromDamage(effectiveDamage);
        }
    }

    /**
     * Send the owner-confirm hit-sound packet for shuriken hits and dash-while-holding-
     * shuriken hits. Headshot wins, then nano-empowered, then plain. Dash damage uses
     * the same shuriken sound by convention.
     */
    private static void dispatchHitSound(ServerPlayer sp, GenjiData data, boolean shurikenHit) {
        if (shurikenHit) {
            String soundType;
            if (ShurikenEntity.wasShurikenHeadshot()) {
                soundType = "headshot";
            } else if (data.isNanoActive()) {
                soundType = "shuriken_nano";
            } else {
                soundType = "shuriken";
            }
            sendHitSound(sp, soundType);
            return;
        }

        boolean holdingShuriken = sp.getMainHandItem().is(ModItems.SHURIKEN.get())
                || sp.getOffhandItem().is(ModItems.SHURIKEN.get());
        if (holdingShuriken) {
            sendHitSound(sp, data.isNanoActive() ? "shuriken_nano" : "shuriken");
        }
    }

    private static void sendHitSound(ServerPlayer sp, String soundType) {
        ModNetwork.sendToPlayer(sp, new S2CPlayHitSound(soundType));
    }
}
