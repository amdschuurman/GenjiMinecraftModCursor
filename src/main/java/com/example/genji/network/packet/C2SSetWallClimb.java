package com.example.genji.network.packet;

import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player is now / no longer holding the wall-climb input
 * combination (jump pressed, against a wall, mid-air, holding a Genji item).
 * The server flips the per-player flag in {@link GenjiData}; the actual
 * velocity application happens in {@link com.example.genji.events.WallClimbAbility}'s
 * tick handler with full server-side validation.
 */
public class C2SSetWallClimb {
    private final boolean climbing;

    public C2SSetWallClimb(boolean climbing) { this.climbing = climbing; }
    public C2SSetWallClimb(FriendlyByteBuf buf) { this.climbing = buf.readBoolean(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(climbing); }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null) return;
            GenjiData data = GenjiDataProvider.getOrNull(sp);
            if (data == null) return;
            data.setWallClimbing(climbing);
        });
        c.setPacketHandled(true);
        return true;
    }
}
