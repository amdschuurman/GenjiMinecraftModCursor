package com.example.genji.events;

import com.example.genji.GenjiMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side player tick hook for Deflect. */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeflectServerTicker {
    private DeflectServerTicker() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;

        // Death/logout-frame safety: don't tick if the player is gone or dead.
        if (sp.isRemoved() || !sp.isAlive()) return;

        DeflectCombat.perPlayerTick(sp);
    }
}
