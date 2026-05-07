package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.capability.GenjiData;
import com.example.genji.capability.GenjiDataProvider;
import com.example.genji.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Defends against the dragonblade-as-pickup-item exploit.
 *
 * The dragonblade item is a transient state-bound item that only legitimately
 * exists in the player's inventory while their ult is in cast / active / sheathe
 * phase. It must never:
 *   - exist as an ItemEntity in the world
 *   - be picked up by any player
 *   - persist across non-blade-state boundaries
 *
 * Defense layers (first -> last; each is intentionally redundant):
 *   1. LivingDeathEvent — cancel blade state on the dying player so drops and
 *      respawn see a consistent capability.
 *   2. LivingDropsEvent — strip dragonblade from drop list before vanilla drops.
 *   3. EntityJoinLevelEvent — any ItemEntity holding a dragonblade gets
 *      discarded as it tries to join the world (catches dispenser / drag-drop
 *      / any mechanism (1) and (2) missed).
 *   4. EntityItemPickupEvent — cancel pickup of any dragonblade ItemEntity
 *      (should never trigger if (3) holds; defense in depth).
 *   5. PlayerLoggedInEvent — cleanse any stray dragonblade items in inventory
 *      that don't match the active blade slot (revert to shuriken).
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID)
public final class DragonbladeIntegrityHandler {
    private DragonbladeIntegrityHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;
        // Death always ends the ult regardless of phase. Capability is then
        // copied to the respawned player via PlayerEvent.Clone with a clean
        // blade state.
        data.cancelBlade();
        data.clearBladeSlot();
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.getDrops().removeIf(itemEntity ->
                itemEntity.getItem().is(ModItems.DRAGONBLADE.get()));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity ie)) return;
        if (ie.getItem().is(ModItems.DRAGONBLADE.get())) {
            event.setCanceled(true);
            ie.discard();
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getItem().getItem().is(ModItems.DRAGONBLADE.get())) {
            event.getItem().discard();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        cleanseInventory(sp);
    }

    /**
     * Replace any dragonblade item in the player's inventory with a shuriken,
     * except the one in the active blade slot if the player is currently in a
     * blade state (cast / active / sheathe).
     */
    private static void cleanseInventory(ServerPlayer sp) {
        GenjiData data = GenjiDataProvider.getOrNull(sp);
        if (data == null) return;
        boolean inBladeState = data.isBladeActive() || data.isCastingBlade() || data.isSheathing();
        int activeBladeSlot = inBladeState ? data.getBladeSlot() : -1;

        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == activeBladeSlot) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.DRAGONBLADE.get())) {
                inv.setItem(i, new ItemStack(ModItems.SHURIKEN.get()));
            }
        }
    }
}
