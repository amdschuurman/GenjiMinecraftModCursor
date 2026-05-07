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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Resets dash cooldown on kills and assists (assist window ~3s).
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class DashResets {
    private DashResets() {}

    // victim -> (attacker -> lastHitGameTime)
    private static final Map<UUID, Map<UUID, Long>> RECENT_HITS = new HashMap<>();
    // 3 seconds at 20 tps
    private static final long ASSIST_WINDOW_TICKS = 60L;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        var level = sp.serverLevel();
        long now = level.getGameTime();

        UUID victim = e.getEntity().getUUID();
        UUID attacker = sp.getUUID();

        RECENT_HITS.computeIfAbsent(victim, k -> new HashMap<>()).put(attacker, now);

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
            ModNetwork.CHANNEL.sendTo(new S2CPlayHitSound("kill"), killer.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
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
                            ModNetwork.CHANNEL.sendTo(new S2CPlayHitSound("kill"), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
                        }
                    }
                }
            }
        }
    }
}
