package com.example.genji.network.packet;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.events.DashAbility;
import com.example.genji.network.ModNetwork;
import com.example.genji.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Starts dash on server. We only play the FP "dash" animation and do sheathe-cancel→shuriken
 * if a REAL dash started (dashCooldown 0 -> >0). Spamming the key no longer plays the anim.
 */
public class C2SActivateDash {
    public C2SActivateDash() {}
    public C2SActivateDash(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            var data = GenjiDataProvider.getOrNull(sp);
            if (data == null) return;

            // Remember state before trying to dash
            boolean wasSheathing = data.isSheathing();
            int cdBefore = data.getDashCooldown();

            // Try to start the dash (handles velocity, invuln windows, etc.)
            DashAbility.startDash(sp, data.isBladeActive());

            // Determine if a REAL dash started this tick
            int cdAfter = data.getDashCooldown();
            boolean dashStartedNow = (cdBefore == 0 && cdAfter > 0);

            if (!dashStartedNow) {
                // No dash -> do NOT play FP anim or swap items
                return;
            }

            // If dash started while sheathing, cancel sheathe and swap to SHURIKEN immediately
            if (wasSheathing) {
                data.cancelSheathe();

                // Prefer the remembered blade slot if present
                int slot = data.getBladeSlot();
                if (slot >= 0 && slot < 9) {
                    sp.getInventory().setItem(slot, new ItemStack(ModItems.SHURIKEN.get()));
                } else {
                    // Fallback: find a blade in hotbar and replace it
                    for (int i = 0; i < 9; i++) {
                        if (sp.getInventory().getItem(i).is(ModItems.DRAGONBLADE.get())) {
                            sp.getInventory().setItem(i, new ItemStack(ModItems.SHURIKEN.get()));
                            slot = i;
                            break;
                        }
                    }
                    // Still nothing? Overwrite selected slot
                    if (slot < 0 || slot >= 9) {
                        int sel = sp.getInventory().selected;
                        sp.getInventory().setItem(sel, new ItemStack(ModItems.SHURIKEN.get()));
                        data.setBladeSlot(sel);
                    }
                }
                sp.inventoryMenu.broadcastChanges();
                data.setBladeSlot(-1); // so end-of-blade logic doesn't fight us later
            }

            // NOTE: Dash animation is triggered by S2CStartDash inside DashAbility.startDash.
            // Sync HUD/FX right away (dashCooldown, etc.)
            ModNetwork.syncTo(sp, data);
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
