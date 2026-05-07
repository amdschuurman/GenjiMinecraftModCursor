package com.example.genji.network.packet;

import com.example.genji.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CPlayHitSound {
    private final String soundType;
    private final BlockPos targetPos;

    public S2CPlayHitSound(String soundType) {
        this.soundType = soundType;
        this.targetPos = null; // For non-positional sounds
    }

    public S2CPlayHitSound(String soundType, BlockPos targetPos) {
        this.soundType = soundType;
        this.targetPos = targetPos;
    }

    public S2CPlayHitSound(FriendlyByteBuf buf) {
        this.soundType = buf.readUtf();
        this.targetPos = buf.readBoolean() ? buf.readBlockPos() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(soundType);
        buf.writeBoolean(targetPos != null);
        if (targetPos != null) {
            buf.writeBlockPos(targetPos);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            // Play sound on client side
            var player = Minecraft.getInstance().player;
            if (player != null) {
                switch (soundType) {
                    case "shuriken" -> {
                        player.playSound(ModSounds.SHURIKEN_HIT.get(), 2.0f, 1.0f); // Increased volume to 2.0f
                    }
                    case "shuriken_nano" -> {
                        player.playSound(ModSounds.SHURIKEN_HIT_NANO.get(), 2.0f, 1.0f); // Increased volume to 2.0f
                    }
                    case "dragonblade" -> {
                        BlockPos soundPos = targetPos != null ? targetPos : player.blockPosition();
                        player.level().playSound(null, soundPos, ModSounds.DRAGONBLADE_HIT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 50.0f, 1.0f); // Play at target position
                    }
                    case "kill" -> {
                        player.playSound(ModSounds.KILL_SOUND.get(), 2.0f, 1.0f); // Increased volume to 2.0f
                    }
                    case "headshot" -> {
                        player.playSound(ModSounds.HEADSHOT_HIT.get(), 2.0f, 1.0f); // Increased volume to 2.0f
                    }
                }
            } else {
            }
        });
        c.setPacketHandled(true);
        return true;
    }
}
