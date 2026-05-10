package com.example.genji.client.events;

import com.example.genji.GenjiMod;
import com.example.genji.client.fx.DragonbladeFxState;
import com.example.genji.client.model.DragonbladeFPSModel;
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
 * First-person Dragonblade overlay:
 * - Uses ONE persistent ItemStack (lazy-initialized) so GeckoLib animations persist across frames.
 * - Cancels vanilla hand while active to prevent the default item flash.
 * - Placement: X=+0.12, Y=-0.90, Z=-1.40, scale=1.0.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonDragonbladeOverlay {

    // Hand-grip pose — see FPOverlayConstants
    private static final double SHIFT_X = com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_X;
    private static final double SHIFT_Y = com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_Y;
    private static final double SHIFT_Z = com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_Z;
    private static final float  SCALE   = com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SCALE;

    // Lazily created on first render (AFTER registries are ready).
    // Never call ModItems.DRAGONBLADE.get() in static init!
    private static ItemStack FP_BLADE_STACK;

    private static final GeoItemRenderer<DragonbladeItem> HAND_RENDERER =
            new HandOnlyRendererForBlade();
    private static final GeoItemRenderer<DragonbladeItem> BLADE_RENDERER =
            new software.bernie.geckolib.renderer.GeoItemRenderer<>(new DragonbladeFPSModel()) {};

    private FirstPersonDragonbladeOverlay() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;

        // Show while casting/active/sheathe or during short grace frames
        if (!DragonbladeFxState.shouldRender()) return;

        // Lazy-init the persistent stack now that registries are present
        if (FP_BLADE_STACK == null) {
            if (!ModItems.DRAGONBLADE.isPresent()) return; // extra safety
            FP_BLADE_STACK = new ItemStack(ModItems.DRAGONBLADE.get());
        }

        final boolean rightHanded = ((AbstractClientPlayer) mc.player).getMainArm() == HumanoidArm.RIGHT;
        final ItemDisplayContext ctx = rightHanded
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource buffer = event.getMultiBufferSource();
        final int light = event.getPackedLight();
        final int overlay = OverlayTexture.NO_OVERLAY;

        poseStack.pushPose();
        poseStack.translate(SHIFT_X, SHIFT_Y, SHIFT_Z);
        if (SCALE != 1.0f) poseStack.scale(SCALE, SCALE, SCALE);

        // IMPORTANT: render *the same* stack every frame so GeckoLib controllers persist (unsheath/idle/swing/sheathe)
        HAND_RENDERER.renderByItem(FP_BLADE_STACK, ctx, poseStack, buffer, light, overlay);
        BLADE_RENDERER.renderByItem(FP_BLADE_STACK, ctx, poseStack, buffer, light, overlay);

        poseStack.popPose();

        // Block vanilla main-hand render while blade overlay is shown
        event.setCanceled(true);
    }
}
