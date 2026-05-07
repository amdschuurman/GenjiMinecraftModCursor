package com.example.genji.events;

import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class DeflectEndCue {
    private static final Map<UUID, Integer> PREV = new HashMap<>();

    /** Drop per-player previous-tick state on logout. Called from StateCleanup. */
    public static void onPlayerLoggedOut(UUID id) {
        PREV.remove(id);
    }

    /** Reset all state on server stop. */
    public static void onServerStopped() {
        PREV.clear();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return; // capability can be missing during death/respawn

        int now = data.getDeflectTicks();
        int prev = PREV.getOrDefault(sp.getUUID(), 0);
        PREV.put(sp.getUUID(), now);

        if (prev > 20 && now == 20) {
            sp.level().playSound(null, sp, ModSounds.DEFLECT_END.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }
}
