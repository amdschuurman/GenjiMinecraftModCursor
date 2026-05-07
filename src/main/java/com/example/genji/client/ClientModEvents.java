package com.example.genji.client;

import com.example.genji.GenjiMod;
import com.example.genji.client.render.DeflectTPSLayer;
import com.example.genji.client.render.ShurikenRenderer;
import com.example.genji.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = GenjiMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    private static final Logger LOGGER = LogManager.getLogger();
    
    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[ClientModEvents] Client setup starting...");
        event.enqueueWork(() -> {
            // Projectile entity renderer (your existing renderer)
            EntityRenderers.register(ModEntities.SHURIKEN.get(), ShurikenRenderer::new);
            LOGGER.info("[ClientModEvents] Registered Shuriken renderer");
            // NOTE: Item renderer is attached via ShurikenItem#initializeClient()
        });
    }
    
    @SubscribeEvent
    public static void onAddLayers(final EntityRenderersEvent.AddLayers event) {
        LOGGER.info("=== [ClientModEvents] onAddLayers EVENT FIRED ===");
        LOGGER.info("[ClientModEvents] Adding deflect layers to player renderers...");
        
        try {
            // Access the skin renderers map (where player renderers actually are)
            var entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            LOGGER.info("[ClientModEvents] Got EntityRenderDispatcher: " + entityRenderDispatcher);
            
            // The player renderer is in the skinMap, not the regular renderers map
            var skinMap = entityRenderDispatcher.getSkinMap();
            LOGGER.info("[ClientModEvents] SkinMap size: " + skinMap.size());
            LOGGER.info("[ClientModEvents] SkinMap keys: " + skinMap.keySet());
            
            // Add layer to all skin types (default, slim)
            int layersAdded = 0;
            for (var entry : skinMap.entrySet()) {
                String skinType = entry.getKey();
                var renderer = entry.getValue();
                LOGGER.info("[ClientModEvents] Processing skin type '" + skinType + "', renderer: " + renderer.getClass().getName());
                
                if (renderer instanceof PlayerRenderer pr) {
                    pr.addLayer(new DeflectTPSLayer(pr));
                    layersAdded++;
                    LOGGER.info("[ClientModEvents] Successfully added DeflectTPSLayer to '" + skinType + "' renderer");
                }
            }
            
            LOGGER.info("[ClientModEvents] Total deflect layers added: " + layersAdded);
        } catch (Exception e) {
            LOGGER.error("[ClientModEvents] Error adding deflect layer:", e);
        }
    }
}
