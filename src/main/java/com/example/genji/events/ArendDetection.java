package com.example.genji.events;

import com.example.genji.registry.ModItems;
import com.example.genji.util.AdvancementHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Special Easter egg: When player "JustArend" equips shurikens,
 * all other players on the server get a warning achievement,
 * and JustArend gets his own special achievement.
 */
@Mod.EventBusSubscriber(modid = "genji", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArendDetection {
    
    // Track which players have already received the warning to avoid spam
    private static final Set<UUID> PLAYERS_WHO_SAW_AREND = new HashSet<>();
    
    // Track if Arend got his own achievement
    private static boolean AREND_GOT_HIS_ACHIEVEMENT = false;

    /**
     * Reset on server stop so integrated-server reloads (open a new world after
     * an old one) don't carry the Easter-egg flag forward. Per-player logout
     * cleanup is intentionally NOT done — once a player has seen Arend they
     * stay tagged until server stop, so the achievement isn't re-granted on
     * every relog.
     */
    public static void onServerStopped() {
        PLAYERS_WHO_SAW_AREND.clear();
        AREND_GOT_HIS_ACHIEVEMENT = false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        
        // Check if this is JustArend holding a shuriken
        String playerName = sp.getGameProfile().getName();
        if ("JustArend".equals(playerName)) {
            ItemStack mainHand = sp.getMainHandItem();
            
            // Check if JustArend is holding a shuriken or dragonblade
            if (mainHand.is(ModItems.SHURIKEN.get()) || mainHand.is(ModItems.DRAGONBLADE.get())) {
                
                // Grant Arend his own achievement (only once)
                if (!AREND_GOT_HIS_ACHIEVEMENT) {
                    AdvancementHelper.grantAdvancement(sp, ResourceLocation.fromNamespaceAndPath("genji", "arend_god"));
                    AREND_GOT_HIS_ACHIEVEMENT = true;
                }
                
                // Grant warning achievement to all other players on the server
                for (ServerPlayer otherPlayer : sp.serverLevel().players()) {
                    // Don't grant to JustArend himself
                    if (!otherPlayer.getGameProfile().getName().equals("JustArend")) {
                        UUID playerId = otherPlayer.getUUID();
                        
                        // Only grant once per player
                        if (!PLAYERS_WHO_SAW_AREND.contains(playerId)) {
                            AdvancementHelper.grantAdvancement(otherPlayer, 
                                ResourceLocation.fromNamespaceAndPath("genji", "arend_warning"));
                            PLAYERS_WHO_SAW_AREND.add(playerId);
                        }
                    }
                }
            }
        }
    }
}

