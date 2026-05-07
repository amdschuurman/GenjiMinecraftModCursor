package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.S2CPlayHitSound;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resets dash cooldown on kills and assists (assist window ~3s).
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class DashResets {
    private DashResets() {}

    // victim -> (attacker -> lastHitGameTime). ConcurrentHashMap because Forge can
    // dispatch LivingHurtEvent / LivingDeathEvent from worker threads in some
    // versions; matches the concurrent-safe collections in DragonbladeCombat.
    private static final Map<UUID, Map<UUID, Long>> RECENT_HITS = new ConcurrentHashMap<>();
    // 3 seconds at 20 tps
    private static final long ASSIST_WINDOW_TICKS = 60L;

    /**
     * Drop assist-tracking state for a leaving player. They're cleared as a
     * victim (their inner attacker map is removed) AND as an attacker (their
     * UUID is removed from every other victim's inner map) — without the
     * latter, persistent entities that were hit but never died would leak the
     * disconnecting attacker's UUID.
     */
    public static void onPlayerLoggedOut(UUID id) {
        RECENT_HITS.remove(id);
        RECENT_HITS.values().forEach(inner -> inner.remove(id));
    }

    /** Reset all global state on server stop. */
    public static void onServerStopped() {
        RECENT_HITS.clear();
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        var level = sp.serverLevel();
        long now = level.getGameTime();

        UUID victim = e.getEntity().getUUID();
        UUID attacker = sp.getUUID();

        RECENT_HITS.computeIfAbsent(victim, k -> new ConcurrentHashMap<>()).put(attacker, now);

        // Clean old entries for this victim
        Map<UUID, Long> map = RECENT_HITS.get(victim);
        Iterator<Map.Entry<UUID, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            if (now - en.getValue() > ASSIST_WINDOW_TICKS) it.remove();
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        UUID victim = e.getEntity().getUUID();
        var level = e.getEntity().level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) {
            RECENT_HITS.remove(victim);
            return;
        }
        long now = sl.getGameTime();

        // Killer gets reset and plays kill sound
        if (e.getSource().getEntity() instanceof ServerPlayer killer) {
            var killerData = GenjiDataProvider.getOrNull(killer);
            if (killerData != null) killerData.clearDashCooldown();
            ModNetwork.sendToPlayer(killer, new S2CPlayHitSound("kill"));
        }

        // Assists: any attacker who hit victim within window
        Map<UUID, Long> map = RECENT_HITS.remove(victim);
        if (map != null) {
            for (var en : map.entrySet()) {
                UUID attackerId = en.getKey();
                long when = en.getValue();
                if (now - when <= ASSIST_WINDOW_TICKS) {
                    ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(attackerId);
                    if (sp != null) {
                        var spData = GenjiDataProvider.getOrNull(sp);
                        if (spData != null) spData.clearDashCooldown();
                        // Play kill sound for assist (if not already played for direct kill)
                        if (!(e.getSource().getEntity() instanceof ServerPlayer killer && killer.getUUID().equals(attackerId))) {
                            ModNetwork.sendToPlayer(sp, new S2CPlayHitSound("kill"));
                        }
                    }
                }
            }
        }
    }
}
