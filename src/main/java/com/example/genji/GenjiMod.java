package com.example.genji;

import com.example.genji.capability.GenjiDataAttach;
import com.example.genji.events.CommonEvents;
import com.example.genji.network.ModNetwork;
import com.example.genji.registry.ModCreativeTabContents;
import com.example.genji.registry.ModEntities;
import com.example.genji.registry.ModItems;
import com.example.genji.registry.ModSounds;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib.GeckoLib;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.example.genji.config.GenjiConfig;

@Mod(GenjiMod.MODID)
public class GenjiMod {
    public static final String MODID = "genji";

    public GenjiMod() {
        GeckoLib.initialize();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GenjiConfig.SPEC, "genji-common.toml");

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(GenjiConfig::onLoad);
        modBus.addListener(GenjiConfig::onReload);

        // DeferredRegisters
        ModItems.REGISTER.register(modBus);
        ModEntities.register(modBus);
        ModSounds.SOUND_EVENTS.register(modBus);
        modBus.addListener(ModCreativeTabContents::onBuildContents);

        ModNetwork.init();

        // Cap attach
        MinecraftForge.EVENT_BUS.register(new GenjiDataAttach());

        // CommonEvents uses @EventBusSubscriber with static handlers; no instance registration needed here
        // MinecraftForge.EVENT_BUS.register(new CommonEvents());
    }
}
