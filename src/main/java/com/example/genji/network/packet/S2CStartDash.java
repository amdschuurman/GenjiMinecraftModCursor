package com.example.genji.network.packet;

import com.example.genji.client.DashInterpolation;
import com.example.genji.client.anim.FPDashAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent to the client when a dash starts, providing start/end positions for smooth interpolation. */
public class S2CStartDash {
    private final Vec3 start;
    private final Vec3 end;
    private final int durationTicks;

    public S2CStartDash(Vec3 start, Vec3 end, int durationTicks) {
        this.start = start;
        this.end = end;
        this.durationTicks = durationTicks;
    }

    public S2CStartDash(FriendlyByteBuf buf) {
        this.start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.durationTicks = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(end.x);
        buf.writeDouble(end.y);
        buf.writeDouble(end.z);
        buf.writeVarInt(durationTicks);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            
            
            // Start both interpolation and animation with the same duration
            DashInterpolation.start(start, end, durationTicks);
            FPDashAnim.start(durationTicks);
            
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}

