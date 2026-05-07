package com.example.genji.network.packet;

import com.example.genji.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → owner: play a confirm-sound on the attacker's client. These are
 * personal feedback sounds — tight, snappy, nano-styled when applicable.
 *
 * Ambient/world-broadcast hit sounds (e.g. the dragonblade slash-hit) are
 * dispatched via {@code level.playSound(null, pos, ...)} on the server side,
 * not via this packet.
 */
public class S2CPlayHitSound {
    private final String soundType;

    public S2CPlayHitSound(String soundType) {
        this.soundType = soundType;
    }

    public S2CPlayHitSound(FriendlyByteBuf buf) {
        this.soundType = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(soundType);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            switch (soundType) {
                case "shuriken"      -> player.playSound(ModSounds.SHURIKEN_HIT.get(),      2.0f, 1.0f);
                case "shuriken_nano" -> player.playSound(ModSounds.SHURIKEN_HIT_NANO.get(), 2.0f, 1.0f);
                case "kill"          -> player.playSound(ModSounds.KILL_SOUND.get(),        2.0f, 1.0f);
                case "headshot"      -> player.playSound(ModSounds.HEADSHOT_HIT.get(),      2.0f, 1.0f);
            }
        });
        c.setPacketHandled(true);
        return true;
    }
}
