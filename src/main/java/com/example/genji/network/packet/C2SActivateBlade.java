package com.example.genji.network.packet;

import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.content.DragonbladeItem;
import com.example.genji.events.ShurikenCombat;
import com.example.genji.network.ModNetwork;
import com.example.genji.registry.ModItems;
import com.example.genji.registry.ModSounds;
import com.example.genji.util.AdvancementHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> Server: request to activate Dragonblade. Carries selected hotbar slot (0..8 or -1). */
public class C2SActivateBlade {
    private final int selectedSlot; // -1 if unknown

    public C2SActivateBlade(int selectedSlot) { this.selectedSlot = selectedSlot; }
    public C2SActivateBlade(FriendlyByteBuf buf) { this.selectedSlot = buf.readVarInt(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeVarInt(selectedSlot); }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        var c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null) return;

            sp.getCapability(GenjiDataProvider.CAPABILITY).ifPresent(data -> {
                if (!data.canBlade()) return;

                // Remember client-selected slot if valid; else current
                int sel = (selectedSlot >= 0 && selectedSlot < 9) ? selectedSlot : sp.getInventory().selected;

                // Server-side validation: the slot we're about to overwrite
                // must currently hold a shuriken. Without this check a forged
                // C2SActivateBlade could silently destroy any other item.
                ItemStack currentInSlot = sp.getInventory().getItem(sel);
                if (!currentInSlot.is(ModItems.SHURIKEN.get())) return;

                data.setBladeSlot(sel);

                // Swap shuriken item to dragonblade item in the selected slot
                ItemStack dragonbladeStack = new ItemStack(ModItems.DRAGONBLADE.get());
                
                // Apply nanoboost enchantments if active
                boolean nanoWasActive = data.isNanoActive();
                if (nanoWasActive) {
                    DragonbladeItem.applyNanoboostEnchantments(dragonbladeStack, true);
                }
                
                sp.getInventory().setItem(sel, dragonbladeStack);
                sp.inventoryMenu.broadcastChanges();

                // Start cast (this also cancels deflect pose without cooldown)
                data.beginBladeCast();
                
                // Grant first dragonblade advancement
                AdvancementHelper.grantAdvancement(sp, ResourceLocation.fromNamespaceAndPath("genji", "first_dragonblade"));
                
                // Grant combo achievement if nano was active when blade was cast
                if (nanoWasActive) {
                    AdvancementHelper.grantAdvancement(sp, ResourceLocation.fromNamespaceAndPath("genji", "nano_blade_combo"));
                }

                // Play the ult VO / start sound
                sp.level().playSound(null, sp, ModSounds.DRAGONBLADE_START.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

                // Stop shuriken channels immediately
                ShurikenCombat.setPrimaryHeld(sp, false);
                ShurikenCombat.setSecondaryHeld(sp, false);

                // Immediate sync so client shows unsheath overlay this tick
                ModNetwork.syncTo(sp, data);
            });
        });
        c.setPacketHandled(true);
        return true;
    }
}
