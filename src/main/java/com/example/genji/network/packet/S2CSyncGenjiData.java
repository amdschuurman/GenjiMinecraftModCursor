package com.example.genji.network.packet;

import com.example.genji.client.ClientGenjiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: full state sync for HUD + FP overlays.
 * New layout includes 'nano' but keeps a 7-int legacy ctor for older call sites.
 */
public class S2CSyncGenjiData {
    private final int ult;
    private final int nano; // NEW

    private final int bladeTicks;
    private final int deflectTicks;
    private final int dashCooldown;
    private final int deflectCooldown;
    private final int bladeCastTicks;
    private final int bladeSheatheTicks;
    private final int nanoBoostTicks; // NEW: Nano active timer

    public S2CSyncGenjiData(int ult, int nano, int bladeTicks, int deflectTicks, int dashCooldown, int deflectCooldown, int bladeCastTicks, int bladeSheatheTicks, int nanoBoostTicks) {
        this.ult = ult;
        this.nano = nano;
        this.bladeTicks = bladeTicks;
        this.deflectTicks = deflectTicks;
        this.dashCooldown = dashCooldown;
        this.deflectCooldown = deflectCooldown;
        this.bladeCastTicks = bladeCastTicks;
        this.bladeSheatheTicks = bladeSheatheTicks;
        this.nanoBoostTicks = nanoBoostTicks;
    }

    public S2CSyncGenjiData(FriendlyByteBuf buf) {
        this.ult = buf.readVarInt();
        this.nano = buf.readVarInt();
        this.bladeTicks = buf.readVarInt();
        this.deflectTicks = buf.readVarInt();
        this.dashCooldown = buf.readVarInt();
        this.deflectCooldown = buf.readVarInt();
        this.bladeCastTicks = buf.readVarInt();
        this.bladeSheatheTicks = buf.readVarInt();
        this.nanoBoostTicks = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(ult);
        buf.writeVarInt(nano);
        buf.writeVarInt(bladeTicks);
        buf.writeVarInt(deflectTicks);
        buf.writeVarInt(dashCooldown);
        buf.writeVarInt(deflectCooldown);
        buf.writeVarInt(bladeCastTicks);
        buf.writeVarInt(bladeSheatheTicks);
        buf.writeVarInt(nanoBoostTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Set nano explicitly, then use legacy update() for the rest
            ClientGenjiData.nano = Math.max(0, Math.min(100, nano));
            ClientGenjiData.nanoBoostTicks = Math.max(0, nanoBoostTicks);
            ClientGenjiData.update(ult, bladeTicks, deflectTicks, dashCooldown, deflectCooldown, bladeCastTicks, bladeSheatheTicks);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
