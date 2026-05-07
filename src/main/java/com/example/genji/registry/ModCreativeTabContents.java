package com.example.genji.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adds Genji items to vanilla creative tabs so they show up in search,
 * JEI, and creative inventories.
 */
public final class ModCreativeTabContents {
    private ModCreativeTabContents() {
    }

    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.SHURIKEN);
            event.accept(ModItems.DRAGONBLADE);
        }
    }
}
