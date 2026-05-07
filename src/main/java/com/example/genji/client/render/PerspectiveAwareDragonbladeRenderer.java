package com.example.genji.client.render;

import com.example.genji.client.model.DragonbladeFPSModel;
import com.example.genji.client.model.DragonbladeTPSModel;
import com.example.genji.content.DragonbladeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import javax.annotation.Nonnull;

/**
 * Perspective-aware dragonblade renderer that uses different models for first-person and third-person.
 * First person: hand.fps.model.geo.json + dragonblade.fps.model.geo.json
 * Third person: dragonblade.tps.model.geo.json
 * GUI: dragonblade_icon.png (handled by item model)
 */
public class PerspectiveAwareDragonbladeRenderer extends BlockEntityWithoutLevelRenderer {

    private final GeoItemRenderer<DragonbladeItem> handRenderer = new HandOnlyRendererForBlade();
    private final GeoItemRenderer<DragonbladeItem> fpsBladeRenderer = new GeoItemRenderer<>(new DragonbladeFPSModel()) {};
    private final DragonbladeTPSRenderer tpsBladeRenderer = new DragonbladeTPSRenderer();

    public PerspectiveAwareDragonbladeRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
        );
    }

    @Override
    public void renderByItem(@Nonnull ItemStack stack,
                             @Nonnull ItemDisplayContext displayContext,
                             @Nonnull PoseStack poseStack,
                             @Nonnull MultiBufferSource buffer,
                             int packedLight,
                             int packedOverlay) {

        if (!(stack.getItem() instanceof DragonbladeItem)) return;


        // For ground, fixed, and other contexts, let Minecraft handle the default rendering
        if (displayContext == ItemDisplayContext.GROUND ||
            displayContext == ItemDisplayContext.FIXED ||
            displayContext == ItemDisplayContext.HEAD ||
            displayContext == ItemDisplayContext.NONE) {
            // Let Minecraft handle the default rendering for these contexts
            return;
        }

        // For first-person rendering, let the FirstPersonDragonbladeOverlay handle it
        if (isFirstPersonContext(displayContext)) {
            // First person is handled by FirstPersonDragonbladeOverlay
            return;
        }

        // For GUI and third-person contexts, render the GeckoLib TPS model (static)
        if (displayContext == ItemDisplayContext.GUI || isThirdPersonContext(displayContext)) {
            tpsBladeRenderer.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay);
            return;
        }

        // For any other context, let Minecraft handle it
    }

    private boolean isFirstPersonContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
               displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private boolean isThirdPersonContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND ||
               displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}