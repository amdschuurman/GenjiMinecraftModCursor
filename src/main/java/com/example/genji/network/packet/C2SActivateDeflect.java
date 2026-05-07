package com.example.genji.network.packet;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.network.ModNetwork;
import com.example.genji.registry.ModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SActivateDeflect {
    public C2SActivateDeflect() { }
    public C2SActivateDeflect(FriendlyByteBuf buf) { }
    public void toBytes(FriendlyByteBuf buf) { }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            var data = GenjiDataProvider.getOrNull(sp);
            if (data == null) return;

            if (data.isDeflectActive()) {
                // Manual cancel: stop deflect & start cooldown — NO end sound here
                data.cancelDeflectStartCooldown();
                ModNetwork.syncTo(sp, data);
            } else {
                // Try start deflect
                if (data.tryDeflect()) {
                    sp.level().playSound(null, sp, ModSounds.DEFLECT_START.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
                    ModNetwork.syncTo(sp, data);
                }
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
