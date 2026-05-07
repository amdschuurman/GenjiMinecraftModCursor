package com.example.genji.client.model;

import com.example.genji.GenjiMod;
import com.example.genji.content.DragonbladeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class WakizashiFPSModel extends GeoModel<DragonbladeItem> {
    @Override public ResourceLocation getModelResource(DragonbladeItem a) {
        return ResourceLocation.fromNamespaceAndPath(GenjiMod.MODID, "geo/wakizashi.fps.model.geo.json");
    }
    @Override public ResourceLocation getTextureResource(DragonbladeItem a) {
        return ResourceLocation.fromNamespaceAndPath(GenjiMod.MODID, "textures/item/dragonblade.png");
    }
    @Override public ResourceLocation getAnimationResource(DragonbladeItem a) {
        return ResourceLocation.fromNamespaceAndPath(GenjiMod.MODID, "animations/shurikens.fps.animations.json");
    }
}
