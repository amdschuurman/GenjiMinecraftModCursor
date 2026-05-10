package com.example.genji.events;

import com.example.genji.GenjiMod;
import com.example.genji.client.input.Keybinds;
import com.example.genji.client.hud.AbilityHud;
import com.example.genji.network.ModNetwork;
import com.example.genji.network.packet.C2SActivateBlade;
import com.example.genji.network.packet.C2SActivateDash;
import com.example.genji.network.packet.C2SActivateDeflect;
import com.example.genji.network.packet.C2SDoubleJump;
import com.example.genji.network.packet.C2SSetPrimaryHeld;
import com.example.genji.network.packet.C2SSetSecondaryHeld;
import com.example.genji.network.packet.C2SSetWallClimb;
import com.example.genji.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side input + HUD. Sends "held" packets for both Mouse1 and Mouse2 while
 * the player is holding ANY Genji combat item (shuriken OR dragonblade).
 */
@Mod.EventBusSubscriber(modid = GenjiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {}

    private static boolean primaryHeldSent = false;
    private static boolean secondaryHeldSent = false;
    private static boolean hadScreenLastTick = false;

    // Passive-movement input state (double-jump edge + wall-climb level).
    private static boolean jumpHeldLastTick = false;
    private static boolean wallClimbingSent = false;

    private static boolean holdingShuriken(ItemStack stack) {
        return stack != null && stack.is(ModItems.SHURIKEN.get());
    }
    private static boolean holdingDragonblade(ItemStack stack) {
        return stack != null && stack.is(ModItems.DRAGONBLADE.get());
    }
    private static boolean holdingGenji(ItemStack stack) {
        return holdingShuriken(stack) || holdingDragonblade(stack);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack main = mc.player.getMainHandItem();

        // Deflect (only while holding a Genji item)
        if (Keybinds.DEFLECT != null && Keybinds.DEFLECT.consumeClick()) {
            if (holdingGenji(main)) {
                ModNetwork.CHANNEL.sendToServer(new C2SActivateDeflect());
            }
        }

        // Dash (only while holding a Genji item)
        if (Keybinds.DASH != null && Keybinds.DASH.consumeClick()) {
            if (holdingGenji(main)) {
                ModNetwork.CHANNEL.sendToServer(new C2SActivateDash());
            }
        }

        // Blade cast/activate (only while holding SHURIKEN so we know which slot to swap back to)
        if (Keybinds.BLADE != null && Keybinds.BLADE.consumeClick()) {
            if (holdingShuriken(main)) {
                int slot = mc.player.getInventory().selected;
                ModNetwork.CHANNEL.sendToServer(new C2SActivateBlade(slot));
            }
        }
    }

    /** Handle mouse *only* in the cancelable PRE event. */
    @SubscribeEvent
    public static void onMousePre(InputEvent.MouseButton.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // If any GUI is open (ESC/inventory/etc.), do not intercept
        if (mc.screen != null) return;

        ItemStack main = mc.player.getMainHandItem();
        boolean genji = holdingGenji(main);
        if (!genji) return;

        if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (e.getAction() == GLFW.GLFW_PRESS) {
                // Check if we're holding dragonblade - if so, cancel vanilla attack and use our timing system
                boolean isDragonblade = holdingDragonblade(main);
                
                if (isDragonblade) {
                    // For dragonblade: cancel vanilla attack and let our swing timing system control when attacks happen
                    e.setCanceled(true);
                    ModNetwork.CHANNEL.sendToServer(new C2SSetPrimaryHeld(true));
                    primaryHeldSent = true;
                } else {
                    // For shurikens: prevent vanilla swing and use custom system
                    e.setCanceled(true);
                    ModNetwork.CHANNEL.sendToServer(new C2SSetPrimaryHeld(true));
                    primaryHeldSent = true;
                }
            } else if (e.getAction() == GLFW.GLFW_RELEASE) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetPrimaryHeld(false));
                primaryHeldSent = false;
            }
        } else if (e.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            if (e.getAction() == GLFW.GLFW_PRESS) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetSecondaryHeld(true));
                secondaryHeldSent = true;
            } else if (e.getAction() == GLFW.GLFW_RELEASE) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetSecondaryHeld(false));
                secondaryHeldSent = false;
            }
        }
    }

    /**
     * Send button-release packets if either:
     *   (a) a GUI just opened while a button was held — vanilla doesn't fire
     *       a RELEASE event in that case;
     *   (b) the player swapped off the Genji item while a button was held
     *       (number-key / mouse-wheel hotbar swap) — vanilla doesn't fire a
     *       RELEASE for the swap either, so the server-side held flag would
     *       persist and keep firing shurikens.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        final boolean hasScreen = mc.screen != null;
        final boolean holdingGenji = !hasScreen && holdingGenji(mc.player.getMainHandItem());
        final boolean shouldRelease = hasScreen || !holdingGenji;

        if (shouldRelease) {
            if (primaryHeldSent) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetPrimaryHeld(false));
                primaryHeldSent = false;
            }
            if (secondaryHeldSent) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetSecondaryHeld(false));
                secondaryHeldSent = false;
            }
            if (wallClimbingSent) {
                ModNetwork.CHANNEL.sendToServer(new C2SSetWallClimb(false));
                wallClimbingSent = false;
            }
        }

        if (holdingGenji) {
            handlePassiveMovementInput(mc);
        } else {
            jumpHeldLastTick = false;
        }

        hadScreenLastTick = hasScreen;
    }

    /**
     * Detect the passive-movement input edges:
     *   - Double-jump: jump key just pressed AND player is mid-air → C2SDoubleJump
     *   - Wall-climb: jump key held AND wall in front AND mid-air → C2SSetWallClimb(true/false on edge)
     *
     * Hand-swap-mid-press is implicitly handled by the {@code holdingGenji} gate above —
     * if the player swaps off, the wall-climb release packet fires from the
     * {@code shouldRelease} branch on next tick.
     */
    private static void handlePassiveMovementInput(Minecraft mc) {
        boolean jumpHeldNow = mc.options.keyJump.isDown();
        boolean midAir = !mc.player.onGround() && !mc.player.isInWater() && !mc.player.isPassenger();

        // Double-jump on the rising edge of the jump key while mid-air.
        if (jumpHeldNow && !jumpHeldLastTick && midAir) {
            ModNetwork.CHANNEL.sendToServer(new C2SDoubleJump());
        }
        jumpHeldLastTick = jumpHeldNow;

        // Wall-climb is a level signal: jump held + horizontal collision + mid-air.
        boolean wallClimbingNow = jumpHeldNow && midAir && mc.player.horizontalCollision;
        if (wallClimbingNow != wallClimbingSent) {
            ModNetwork.CHANNEL.sendToServer(new C2SSetWallClimb(wallClimbingNow));
            wallClimbingSent = wallClimbingNow;
        }
    }

    /** Hide hand while LMB is held with genji items (no vanilla swing anim), but never during GUIs. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        ItemStack main = mc.player.getMainHandItem();
        if (holdingGenji(main) && mc.mouseHandler.isLeftPressed()) {
            e.setCanceled(true);
        }
    }

    /** HUD: subtle ability boxes on the right side. */
    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post e) {
        AbilityHud.render(e.getGuiGraphics());
    }
}
