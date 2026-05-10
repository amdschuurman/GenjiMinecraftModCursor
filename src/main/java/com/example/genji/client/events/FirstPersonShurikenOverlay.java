package com.example.genji.client.events;

import com.example.genji.GenjiMod;
import com.example.genji.client.anim.FPDashAnim;
import com.example.genji.client.anim.FPDeflectAnim;
import com.example.genji.client.fx.DragonbladeFxState;
import com.example.genji.client.model.HandFPSModel;
import com.example.genji.client.model.ShurikensFPSModel;
import com.example.genji.content.ShurikenItem;
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
 * First-person Shuriken overlay:
 * - Uses ONE persistent ItemStack (lazy-initialized) so GeckoLib animations persist across frames.
 * - Cancels vanilla hand while active to prevent the default item flash.
 * - Renders hand + shurikens models with proper positioning.
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonShurikenOverlay {

    // Hand-grip pose — see FPOverlayConstants
    private static final float SHIFT_X = (float) com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_X;
    private static final float SHIFT_Y = (float) com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_Y;
    private static final float SHIFT_Z = (float) com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SHIFT_Z;
    private static final float SCALE   = com.example.genji.client.render.FPOverlayConstants.HAND_GRIP_SCALE;

    // Lazy-initialized persistent stack for GeckoLib animation continuity
    private static ItemStack FP_SHURIKEN_STACK = null;

    // Renderers (lazy-initialized)
    private static GeoItemRenderer<ShurikenItem> HAND_RENDERER = null;
    private static GeoItemRenderer<ShurikenItem> SHURIKEN_RENDERER = null;

    private FirstPersonShurikenOverlay() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;

        // Only render when player is holding shurikens
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof ShurikenItem)) return;

        // Cancel shuriken rendering when deflect, dash, or dragonblade animations are active
        if (FPDeflectAnim.isVisible() || FPDashAnim.isActive() || DragonbladeFxState.shouldRender()) {
            return;
        }

        // Lazy-init the persistent stack now that registries are present
        if (FP_SHURIKEN_STACK == null) {
            if (!ModItems.SHURIKEN.isPresent()) return; // extra safety
            FP_SHURIKEN_STACK = new ItemStack(ModItems.SHURIKEN.get());
        }

        // Lazy-init renderers
        if (HAND_RENDERER == null) {
            HAND_RENDERER = new GeoItemRenderer<>(new HandFPSModel()) {
                @Override
                public net.minecraft.resources.ResourceLocation getTextureLocation(ShurikenItem animatable) {
                    AbstractClientPlayer p = Minecraft.getInstance().player;
                    return (p != null)
                            ? p.getSkinTextureLocation() // base skin in 1.20.1
                            : net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
                }
            };
        }

        if (SHURIKEN_RENDERER == null) {
            SHURIKEN_RENDERER = new GeoItemRenderer<>(new ShurikensFPSModel()) {};
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

        // IMPORTANT: render *the same* stack every frame so GeckoLib controllers persist
        HAND_RENDERER.renderByItem(FP_SHURIKEN_STACK, ctx, poseStack, buffer, light, overlay);
        SHURIKEN_RENDERER.renderByItem(FP_SHURIKEN_STACK, ctx, poseStack, buffer, light, overlay);

        poseStack.popPose();

        // Block vanilla main-hand render while shuriken overlay is shown
        event.setCanceled(true);
    }
}
