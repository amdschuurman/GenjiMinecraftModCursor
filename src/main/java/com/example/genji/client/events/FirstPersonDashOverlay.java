package com.example.genji.client.events;

import com.example.genji.GenjiMod;
import com.example.genji.client.anim.FPDashAnim;
import com.example.genji.client.model.WakizashiFPSModel;
import com.example.genji.client.render.HandOnlyRendererForBlade;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Shows the 0.25s wakizashi "dash" FP animation on top of whatever is equipped.
 * We render BOTH the hand (same as blade) and the wakizashi model that the "dash" clip animates.
 * This only runs while FPDashAnim.isActive() (started by your dash packet / sync).
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonDashOverlay {

    private FirstPersonDashOverlay() {}

    // Your validated FP placement
    private static final double SHIFT_X = 0.12D;
    private static final double SHIFT_Y = -0.90D;
    private static final double SHIFT_Z = -1.40D;
    private static final float  SCALE   = 1.00f;

    private static ItemStack DASH_STACK; // persistent stack so GeckoLib controllers persist

    // Renderers: hand + wakizashi model (dash clip manipulates both Holding + wakizashi bones)
    private static final GeoItemRenderer<DragonbladeItem> HAND_RENDERER =
            new HandOnlyRendererForBlade();
    private static final GeoItemRenderer<DragonbladeItem> WAKIZASHI_RENDERER =
            new GeoItemRenderer<>(new WakizashiFPSModel()) {};

    private static boolean loggedOnce = false;
    private static long lastDashStartTick = Long.MIN_VALUE;

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        
        boolean isActive = FPDashAnim.isActive();
        boolean justStarted = FPDashAnim.justStarted();
        
        if (isActive && !loggedOnce) {
            loggedOnce = true;
        }
        if (!isActive) {
            loggedOnce = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;

        // Lazy init AFTER registries are ready; use the blade item so its controllers pick the "dash" clip
        if (DASH_STACK == null) {
            if (!ModItems.DRAGONBLADE.isPresent()) return;
            DASH_STACK = new ItemStack(ModItems.DRAGONBLADE.get());
        }
        
        // If this is a new dash (justStarted), recreate the stack to force GeckoLib reset
        if (justStarted) {
            long currentTick = mc.level != null ? mc.level.getGameTime() : 0;
            if (currentTick != lastDashStartTick) {
                DASH_STACK = new ItemStack(ModItems.DRAGONBLADE.get());
                lastDashStartTick = currentTick;
            }
        }

        final boolean rightHanded = ((AbstractClientPlayer) mc.player).getMainArm() == HumanoidArm.RIGHT;
        final ItemDisplayContext ctx = rightHanded
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        final PoseStack pose = event.getPoseStack();
        final MultiBufferSource buf = event.getMultiBufferSource();
        final int light = event.getPackedLight();
        final int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();
        pose.translate(SHIFT_X, SHIFT_Y, SHIFT_Z);
        if (SCALE != 1.0f) pose.scale(SCALE, SCALE, SCALE);

        // IMPORTANT: render order = hand first, then the wakizashi model
        HAND_RENDERER.renderByItem(DASH_STACK, ctx, pose, buf, light, overlay);
        WAKIZASHI_RENDERER.renderByItem(DASH_STACK, ctx, pose, buf, light, overlay);

        pose.popPose();

        // Hide vanilla hand/item while the dash clip plays
        event.setCanceled(true);
    }
}
