package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.config.GenjiConfig;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.content.ShurikenEntity;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CSyncGenjiData;
import com.example.genji.network.packet.S2CPlayHitSound;
import com.example.genji.registry.ModItems;
import com.example.genji.registry.ModSounds;
import com.example.genji.util.AdvancementHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GenjiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;

        p.getCapability(GenjiDataProvider.CAPABILITY).ifPresent(data -> {
            int prevCast    = data.getBladeCastTicks();
            int prevBlade   = data.getBladeTicks();
            int prevSheathe = data.getBladeSheatheTicks();
            boolean prevNano = data.isNanoActive();

            // advance all timers once per tick
            data.tick();
            
            // === Nano-Boost: apply buffs while active (server-side)
            if (!p.level().isClientSide && data.isNanoActive() && p instanceof ServerPlayer sp) {
                int dur = 4; // reapply briefly each tick so effects persist
                if (GenjiConfig.NANO_RESISTANCE_AMPLIFIER.get() >= 0)
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, GenjiConfig.NANO_RESISTANCE_AMPLIFIER.get(),  false, false, true));
                if (GenjiConfig.NANO_FIRE_RES_AMPLIFIER.get() >= 0)
                    sp.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,   dur, GenjiConfig.NANO_FIRE_RES_AMPLIFIER.get(),    false, false, true));
                if (GenjiConfig.NANO_ABSORPTION_AMPLIFIER.get() >= 0)
                    sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,        dur, GenjiConfig.NANO_ABSORPTION_AMPLIFIER.get(),  false, false, true));

                // one-shot: instant health on activation tick
                if (data.nanoJustActivated()) {
                    int amp = Math.max(0, GenjiConfig.NANO_INSTANT_HEALTH_AMPLIFIER.get());
                    sp.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, amp, false, false, true));
                }
            }

            // === Speed Effects: Dragonblade + Nanoboost combined logic (server-side)
            if (!p.level().isClientSide && p instanceof ServerPlayer sp) {
                int dur = 4; // reapply briefly each tick so effects persist
                boolean nanoActive = data.isNanoActive();
                boolean bladeActive = data.isBladeActive();
                
                if (nanoActive && bladeActive) {
                    // Both active: Speed 4 (amplifier 3)
                    sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 3, false, false, true));
                } else if (nanoActive) {
                    // Only nanoboost: Speed 2 (amplifier 1)
                    sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false, true));
                } else if (bladeActive) {
                    // Only dragonblade: Speed 2 (amplifier 1)
                    sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 1, false, false, true));
                }
            }

            // === Blade CAST -> ACTIVE
            if (prevCast > 0 && data.getBladeCastTicks() == 0 && data.getBladeTicks() == 0) {
                data.activateBlade();

                if (p instanceof ServerPlayer spCombo && data.isNanoActive()) {
                    AdvancementHelper.grantAdvancement(
                            spCombo,
                            ResourceLocation.fromNamespaceAndPath("genji", "nano_blade_combo")
                    );
                }

                // Edge case: ending cue at start if total active <= cue lead
                final int endingCueLeadTicks = 1 * 20; // 1s pre-end (matches OW)
                int totalActive = GenjiConfig.secToTicksClamped(GenjiConfig.DRAGONBLADE_DURATION_SECONDS);
                if (totalActive <= endingCueLeadTicks && !data.bladeEndingPlayed() && p instanceof ServerPlayer spStart) {
                    spStart.level().playSound(
                            null, spStart.blockPosition(),
                            ModSounds.DRAGONBLADE_ENDING.get(),
                            SoundSource.PLAYERS, 1.0f, 1.0f
                    );
                    data.markBladeEndingPlayed();
                }
            }

            // === Play ending SFX exactly 1s (20 ticks) before sheathing begins
            final int endingCueLeadTicks = 1 * 20;
            if (!data.bladeEndingPlayed() && data.getBladeTicks() == endingCueLeadTicks && endingCueLeadTicks > 0) {
                if (p instanceof ServerPlayer sp) {
                    sp.level().playSound(
                            null, sp.blockPosition(),
                            ModSounds.DRAGONBLADE_ENDING.get(),
                            SoundSource.PLAYERS, 1.0f, 1.0f
                    );
                }
                data.markBladeEndingPlayed();
            }

            // === ACTIVE -> SHEATHE
            if (prevBlade > 0 && data.getBladeTicks() == 0 && !data.isSheathing()) {
                data.endBladeStartSheathe();
            }

            // === SHEATHE -> DONE: restore slot and swap dragonblade back to shurikens
            if (prevSheathe > 0 && data.getBladeSheatheTicks() == 0 && p instanceof ServerPlayer spSheathe) {
                // First, swap dragonblade item back to shurikens
                int target = data.getBladeSlot();
                if (target >= 0 && target < 9) {
                    // Swap the dragonblade item back to shurikens in the remembered slot
                    spSheathe.getInventory().setItem(target, new ItemStack(ModItems.SHURIKEN.get()));
                    spSheathe.getInventory().selected = target;
                } else {
                    // Fallback: find dragonblade in hotbar and replace it with shurikens
                    for (int i = 0; i < 9; i++) {
                        ItemStack it = spSheathe.getInventory().getItem(i);
                        if (!it.isEmpty() && it.is(ModItems.DRAGONBLADE.get())) {
                            spSheathe.getInventory().setItem(i, new ItemStack(ModItems.SHURIKEN.get()));
                            spSheathe.getInventory().selected = i;
                            break;
                        }
                    }
                }
                spSheathe.inventoryMenu.broadcastChanges();
                data.clearBladeSlot();
            }

            // === Drive Dragonblade combat loop + sync to client (dirty-gated)
            if (p instanceof ServerPlayer sp) {
                DragonbladeCombat.perPlayerTick(sp);
                ModNetwork.syncIfDirty(sp, data);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityHurt(LivingHurtEvent e) {
        // Swift Strike i-frames: Genji is invulnerable during dash motion.
        // OW canon — applies to all damage sources including environmental.
        if (e.getEntity() instanceof ServerPlayer victim && DashAbility.isDashing(victim)) {
            e.setCanceled(true);
            return;
        }

        // Attribute damage to a player (direct or projectile owner)
        ServerPlayer attacker = null;
        Entity srcEntity = e.getSource().getEntity();
        if (srcEntity instanceof ServerPlayer sp) {
            attacker = sp;
        } else {
            Entity direct = e.getSource().getDirectEntity();
            if (direct instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer owner) {
                attacker = owner;
            }
        }
        if (attacker == null) {
            return;
        }

        // Disable vanilla hitreg for shurikens only (dragonblade handled in custom system below)
        boolean shurikenHit = e.getSource().getDirectEntity() instanceof ShurikenEntity;
        
        if (shurikenHit) {
            // Bypass invulnerability frames for shurikens only
            e.getEntity().invulnerableTime = 0;
        }

        // Suppress vanilla/Better Combat dragonblade melee damage; our custom LOS damage will apply instead.
        if (attacker != null) {
            var item = attacker.getMainHandItem();
            boolean isDragonblade = !item.isEmpty() && item.getItem() instanceof com.example.genji.content.DragonbladeItem;
            if (isDragonblade) {
                var data = com.example.genji.capability.GenjiDataProvider.getOrNull(attacker);
                if (data != null && data.isBladeActive()) {
                    // Allow our own custom hits and dash hits through, cancel others
                    if (!com.example.genji.events.DragonbladeCombat.isInternalDragonbladeDamage()
                            && !com.example.genji.events.DashAbility.isInternalDashDamage()) {
                        e.setCanceled(true);
                        return;
                    }
                }
            }
        }

        final float raw = e.getAmount();
        if (raw <= 0f) return;

        // Cap counted damage by victim's remaining HP (prevents overkill over-credit)
        LivingEntity victim = e.getEntity();
        float targetHPBefore = victim.getHealth();
        final float effectiveDamage = Math.max(0f, Math.min(raw, targetHPBefore));

        final ServerPlayer sp = attacker;
        sp.getCapability(GenjiDataProvider.CAPABILITY).ifPresent(data -> {
            // Nano damage multiplier: for Shuriken hits, Dash damage, and Deflected projectiles (dragonblade uses vanilla sword mechanics with enchantments)
            boolean isDashing = DashAbility.isDashing(sp);
            
            // Check if this is a deflected projectile (any projectile owned by player, excluding shurikens which are already handled)
            Entity direct = e.getSource().getDirectEntity();
            boolean isDeflectedProjectile = !shurikenHit && direct instanceof Projectile proj && proj.getOwner() == sp;
            
            if ((shurikenHit || isDashing || isDeflectedProjectile) && data.isNanoActive()) {
                float mult = (float) data.getNanoDamageMultiplier();
                if (mult > 1.0f) {
                    // Use current event amount; don't touch outer locals inside lambda
                    e.setAmount(e.getAmount() * mult);
                }
            }

        // Check if player is holding shuriken or dragonblade items
        boolean holdingShuriken = sp.getMainHandItem().is(ModItems.SHURIKEN.get()) || 
                                sp.getOffhandItem().is(ModItems.SHURIKEN.get());
        boolean holdingDragonblade = sp.getMainHandItem().is(ModItems.DRAGONBLADE.get()) || 
                                     sp.getOffhandItem().is(ModItems.DRAGONBLADE.get());

            // Charge meters (overkill clamped)
            // Charge nano from: shuriken hits, dash damage, and dragonblade attacks
            if (shurikenHit || holdingShuriken || holdingDragonblade) {
                data.addNanoFromDamage(effectiveDamage);
            }
            if (!data.isBladeActive()) {
                data.addUltFromDamage(effectiveDamage);
            }

            // Send hit sound packet to client
            
            if (shurikenHit) {
                // Owner-confirm hit sound: headshot wins, then nano-empowered, then plain.
                String soundType;
                if (ShurikenEntity.wasShurikenHeadshot()) {
                    soundType = "headshot";
                } else if (data.isNanoActive()) {
                    soundType = "shuriken_nano";
                } else {
                    soundType = "shuriken";
                }
                ModNetwork.CHANNEL.sendTo(new S2CPlayHitSound(soundType), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
            } else if (holdingShuriken) {
                // Dash damage hit sound (when holding shurikens)
                if (data.isNanoActive()) {
                    ModNetwork.CHANNEL.sendTo(new S2CPlayHitSound("shuriken_nano"), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
                } else {
                    ModNetwork.CHANNEL.sendTo(new S2CPlayHitSound("shuriken"), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
                }
            }

            // Kill sound is now handled in LivingDeathEvent in DashResets.java

            ModNetwork.syncTo(sp, data);
        });
    }
}
