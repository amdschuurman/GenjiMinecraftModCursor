package com.example.genji.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client to trigger a third-person air-punch animation for a shuriken throw.
 * The {@link Type} enum is preserved on the wire even though both variants currently
 * play the same swing — keeps room to differentiate burst vs single later without a
 * protocol bump.
 */
public class S2CPlayerPunchAnim {
    public enum Type { SINGLE_PUNCH, BURST_PUNCH }
    private final Type type;

    public S2CPlayerPunchAnim(Type type) { this.type = type; }
    public S2CPlayerPunchAnim(FriendlyByteBuf buf) { this.type = buf.readEnum(Type.class); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeEnum(type); }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.options.getCameraType().isFirstPerson()) return;
            mc.player.swing(mc.player.getUsedItemHand());
        });
        c.setPacketHandled(true);
        return true;
    }
}
