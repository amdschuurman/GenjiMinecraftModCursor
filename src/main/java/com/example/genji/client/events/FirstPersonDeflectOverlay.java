package com.example.genji.client.events;

import com.example.genji.GenjiMod;
import com.example.genji.client.anim.FPDeflectAnim;
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

/** Renders hand + wakizashi during the deflect animation window. */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FirstPersonDeflectOverlay {

    private FirstPersonDeflectOverlay() {}

    private static final double SHIFT_X = 0.12D;
    private static final double SHIFT_Y = -0.90D;
    private static final double SHIFT_Z = -1.40D;
    private static final float  SCALE   = 1.00f;

    private static ItemStack DEFLECT_STACK;

    private static final GeoItemRenderer<DragonbladeItem> HAND_RENDERER =
            new HandOnlyRendererForBlade();
    private static final GeoItemRenderer<DragonbladeItem> WAKIZASHI_RENDERER =
            new GeoItemRenderer<>(new WakizashiFPSModel()) {};

    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!FPDeflectAnim.isVisible()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;

        if (DEFLECT_STACK == null) {
            if (!ModItems.DRAGONBLADE.isPresent()) return;
            DEFLECT_STACK = new ItemStack(ModItems.DRAGONBLADE.get());
        }

        final boolean right = ((AbstractClientPlayer) mc.player).getMainArm() == HumanoidArm.RIGHT;
        final ItemDisplayContext ctx = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        final PoseStack pose = event.getPoseStack();
        final MultiBufferSource buf = event.getMultiBufferSource();
        final int light = event.getPackedLight();
        final int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();
        pose.translate(SHIFT_X, SHIFT_Y, SHIFT_Z);
        if (SCALE != 1.0f) pose.scale(SCALE, SCALE, SCALE);

        HAND_RENDERER.renderByItem(DEFLECT_STACK, ctx, pose, buf, light, overlay);
        WAKIZASHI_RENDERER.renderByItem(DEFLECT_STACK, ctx, pose, buf, light, overlay);

        pose.popPose();
        event.setCanceled(true);
    }
}
