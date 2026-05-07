package com.example.genji.client.render;

import com.example.genji.client.fx.DragonbladeFxState;
import com.example.genji.client.model.HandFPSModel;
import com.example.genji.client.model.ShurikensFPSModel;
import com.example.genji.client.render.ShurikensTPSRenderer;
import com.example.genji.content.ShurikenItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Perspective-aware composite renderer for shurikens.
 * - First person: renders hand + shurikens models (same as before)
 * - Third person: renders only shurikens model without positioning manipulation
 */
public class PerspectiveAwareShurikenRenderer extends BlockEntityWithoutLevelRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerspectiveAwareShurikenRenderer.class);

    // First-person renderers (hand + shurikens)
    private final GeoItemRenderer<ShurikenItem> handRenderer =
            new GeoItemRenderer<>(new HandFPSModel()) {
                @Override
                public ResourceLocation getTextureLocation(ShurikenItem animatable) {
                    AbstractClientPlayer p = Minecraft.getInstance().player;
                    return (p != null)
                            ? p.getSkinTextureLocation() // base skin in 1.20.1
                            : ResourceLocation.withDefaultNamespace("textures/entity/steve.png");
                }
            };

    private final GeoItemRenderer<ShurikenItem> shurikenFPRenderer =
            new GeoItemRenderer<>(new ShurikensFPSModel()) {};

    // Third-person renderer (shurikens only, with animations)
    private final ShurikensTPSRenderer shurikenTPSRenderer = new ShurikensTPSRenderer();


    public PerspectiveAwareShurikenRenderer() {
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

        if (!(stack.getItem() instanceof ShurikenItem)) return;


        // For ground, fixed, and other contexts, let Minecraft handle the default rendering
        if (displayContext == ItemDisplayContext.GROUND ||
            displayContext == ItemDisplayContext.FIXED ||
            displayContext == ItemDisplayContext.HEAD ||
            displayContext == ItemDisplayContext.NONE) {
            // Let Minecraft handle the default rendering for these contexts
            return;
        }

        // For first-person rendering, let the FirstPersonShurikenOverlay handle it
        if (isFirstPersonContext(displayContext)) {
            // First person is handled by FirstPersonShurikenOverlay
            return;
        }

        // For GUI and third-person contexts, render the GeckoLib TPS model (static)
        if (displayContext == ItemDisplayContext.GUI || isThirdPersonContext(displayContext)) {

            // Render GeckoLib TPS model
            try {
                shurikenTPSRenderer.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay);
            } catch (Exception e) {
                LOGGER.error("Failed to render shuriken TPS model", e);
            }
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
