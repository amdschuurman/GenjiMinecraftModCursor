package com.example.genji.network.packet;

import com.example.genji.client.anim.FPDeflectAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client trigger for the "deflect_hit{1..3}" FP animation variant. */
public class S2CDeflectHit {
    private final int variant; // 1..3

    public S2CDeflectHit(int variant) { this.variant = variant; }
    public S2CDeflectHit(FriendlyByteBuf buf) { this.variant = buf.readVarInt(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeVarInt(variant); }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            FPDeflectAnim.hit(variant);
        });
        c.setPacketHandled(true);
        return true;
    }
}
