package com.example.genji.network.packet;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.events.DragonbladeCombat;
import com.example.genji.events.ShurikenCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Mouse 2 (secondary) held/released. Route to blade during CAST/ACTIVE; shuriken otherwise. HARD-LOCK during SHEATHE. */
public class C2SSetSecondaryHeld {
    private final boolean down;
    public C2SSetSecondaryHeld(boolean down) { this.down = down; }
    public C2SSetSecondaryHeld(FriendlyByteBuf buf) { this.down = buf.readBoolean(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeBoolean(down); }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            var data = GenjiDataProvider.getOrNull(sp);
            if (data == null) return;

            // === HARD LOCK while SHEATHING: no blade swings, no shurikens ===
            if (data.isSheathing()) {
                DragonbladeCombat.setSecondaryHeld(sp, false);
                ShurikenCombat.setSecondaryHeld(sp, false);
                return;
            }

            boolean bladePhase = data.isCastingBlade() || data.isBladeActive();
            if (bladePhase) {
                DragonbladeCombat.setSecondaryHeld(sp, down);
                ShurikenCombat.setSecondaryHeld(sp, false);
            } else {
                ShurikenCombat.setSecondaryHeld(sp, down);
                DragonbladeCombat.setSecondaryHeld(sp, false);
            }
        });
        return true;
    }
}
