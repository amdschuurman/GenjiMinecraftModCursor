package com.example.genji.network;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiData;
import com.example.genji.network.packet.C2SActivateBlade;
import com.example.genji.network.packet.C2SActivateDash;
import com.example.genji.network.packet.C2SActivateDeflect;
import com.example.genji.network.packet.C2SActivateNanoBoost;
import com.example.genji.network.packet.C2SSetPrimaryHeld;
import com.example.genji.network.packet.C2SSetSecondaryHeld;
import com.example.genji.network.packet.S2CSyncGenjiData;
import com.example.genji.network.packet.S2CShurikenFPAnim;
import com.example.genji.network.packet.S2CPlayHitSound;
import com.example.genji.network.packet.S2CPlayerPunchAnim;
import com.example.genji.network.packet.S2CStartDash;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    public static final String PROTO = "2";
    public static SimpleChannel CHANNEL;
    private static int id = 0;

    public static void init() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GenjiMod.MODID, "main"))
                .networkProtocolVersion(() -> PROTO)
                .clientAcceptedVersions(PROTO::equals)
                .serverAcceptedVersions(PROTO::equals)
                .simpleChannel();

        // ---------------- C2S ----------------
        CHANNEL.messageBuilder(C2SActivateDeflect.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SActivateDeflect::new).encoder(C2SActivateDeflect::toBytes)
                .consumerMainThread(C2SActivateDeflect::handle).add();

        CHANNEL.messageBuilder(C2SActivateDash.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SActivateDash::new).encoder(C2SActivateDash::toBytes)
                .consumerMainThread(C2SActivateDash::handle).add();

        CHANNEL.messageBuilder(C2SActivateBlade.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SActivateBlade::new).encoder(C2SActivateBlade::toBytes)
                .consumerMainThread(C2SActivateBlade::handle).add();

        CHANNEL.messageBuilder(C2SActivateNanoBoost.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SActivateNanoBoost::new).encoder(C2SActivateNanoBoost::toBytes)
                .consumerMainThread(C2SActivateNanoBoost::handle).add();

        CHANNEL.messageBuilder(C2SSetPrimaryHeld.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSetPrimaryHeld::new).encoder(C2SSetPrimaryHeld::toBytes)
                .consumerMainThread(C2SSetPrimaryHeld::handle).add();

        CHANNEL.messageBuilder(C2SSetSecondaryHeld.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSetSecondaryHeld::new).encoder(C2SSetSecondaryHeld::toBytes)
                .consumerMainThread(C2SSetSecondaryHeld::handle).add();

        // ---------------- S2C ----------------
        CHANNEL.messageBuilder(S2CSyncGenjiData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CSyncGenjiData::new).encoder(S2CSyncGenjiData::toBytes)
                .consumerMainThread(S2CSyncGenjiData::handle).add();

        // NEW: first-person shuriken hand animation trigger (client-only)
        CHANNEL.messageBuilder(S2CShurikenFPAnim.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CShurikenFPAnim::new).encoder(S2CShurikenFPAnim::toBytes)
                .consumerMainThread(S2CShurikenFPAnim::handle).add();

        CHANNEL.messageBuilder(com.example.genji.network.packet.S2CDragonbladeFPAnim.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.example.genji.network.packet.S2CDragonbladeFPAnim::new)
                .encoder(com.example.genji.network.packet.S2CDragonbladeFPAnim::toBytes)
                .consumerMainThread(com.example.genji.network.packet.S2CDragonbladeFPAnim::handle).add();

        CHANNEL.messageBuilder(com.example.genji.network.packet.S2CDeflectHit.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(com.example.genji.network.packet.S2CDeflectHit::new)
                .encoder(com.example.genji.network.packet.S2CDeflectHit::toBytes)
                .consumerMainThread(com.example.genji.network.packet.S2CDeflectHit::handle)
                .add();

        CHANNEL.messageBuilder(S2CPlayHitSound.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CPlayHitSound::new)
                .encoder(S2CPlayHitSound::toBytes)
                .consumerMainThread(S2CPlayHitSound::handle)
                .add();

        // NEW: third-person player air-punch animations for shurikens
        CHANNEL.messageBuilder(S2CPlayerPunchAnim.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CPlayerPunchAnim::new)
                .encoder(S2CPlayerPunchAnim::toBytes)
                .consumerMainThread(S2CPlayerPunchAnim::handle)
                .add();

        // Smooth dash interpolation
        CHANNEL.messageBuilder(S2CStartDash.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CStartDash::new)
                .encoder(S2CStartDash::toBytes)
                .consumerMainThread(S2CStartDash::handle)
                .add();

    }

    /**
     * Send any packet to a single player. Replaces the verbose
     * {@code CHANNEL.sendTo(packet, sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT)}
     * idiom — same semantics, one line.
     */
    public static <MSG> void sendToPlayer(ServerPlayer sp, MSG packet) {
        CHANNEL.sendTo(packet, sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    /**
     * Send a full GenjiData state sync to the client and clear the dirty flag.
     * Use from event-response paths that always have a state change.
     */
    public static void syncTo(ServerPlayer sp, GenjiData data) {
        sendToPlayer(sp, new S2CSyncGenjiData(
                data.getUlt(), data.getNano(),
                data.getBladeTicks(), data.getDeflectTicks(),
                data.getDashCooldown(), data.getDeflectCooldown(),
                data.getBladeCastTicks(), data.getBladeSheatheTicks(),
                data.getNanoBoostTicks()
        ));
        data.markSynced();
    }

    /**
     * Send a sync only if state actually changed since the last send.
     * Use from per-tick paths so idle players don't receive redundant
     * packets every tick.
     */
    public static void syncIfDirty(ServerPlayer sp, GenjiData data) {
        if (data.isDirty()) syncTo(sp, data);
    }
}
