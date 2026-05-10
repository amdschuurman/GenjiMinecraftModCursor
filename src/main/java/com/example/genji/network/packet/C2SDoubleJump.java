package com.example.genji.network.packet;

import com.example.genji.events.DoubleJumpAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: jump key was pressed mid-air with a Genji item held. */
public class C2SDoubleJump {
    public C2SDoubleJump() {}
    public C2SDoubleJump(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp != null) DoubleJumpAbility.executeDoubleJump(sp);
        });
        c.setPacketHandled(true);
        return true;
    }
}
