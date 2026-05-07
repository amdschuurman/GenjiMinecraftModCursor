package com.example.genji.events;

import com.example.genji.GenjiMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Centralized cleanup of static per-player state on player logout and server stop.
 *
 * Without this, the per-player runtime maps in ShurikenCombat, DragonbladeCombat,
 * DashAbility, DashResets, DeflectCombat, DeflectEndCue, and ArendDetection accumulate
 * UUIDs across the entire server uptime — and on integrated-server (singleplayer)
 * reloads the JVM persists across world loads, so static state would carry across
 * worlds too.
 *
 * Runs on the FORGE bus (player logout) and the MOD bus default scope is fine because
 * ServerStoppedEvent fires on the FORGE bus too in 1.20.1.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class StateCleanup {
    private StateCleanup() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();

        ShurikenCombat.onPlayerLoggedOut(id);
        DragonbladeCombat.onPlayerLoggedOut(id);
        DashAbility.onPlayerLoggedOut(id);
        DashResets.onPlayerLoggedOut(id);
        DeflectEndCue.onPlayerLoggedOut(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ShurikenCombat.onServerStopped();
        DragonbladeCombat.onServerStopped();
        DashAbility.onServerStopped();
        DashResets.onServerStopped();
        DeflectCombat.onServerStopped();
        DeflectEndCue.onServerStopped();
        ArendDetection.onServerStopped();
    }
}
