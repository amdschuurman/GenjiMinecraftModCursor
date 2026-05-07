package com.example.genji.client.anim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.fml.ModList;

/**
 * Utility class for handling third-person player animations with mod compatibility.
 * Detects Better Combat and Simply Swords mods and uses appropriate katana animations.
 */
public class PlayerAnimationHelper {
    
    private static final String BETTER_COMBAT_MOD_ID = "bettercombat";
    private static final String SIMPLY_SWORDS_MOD_ID = "simplyswords";
    
    /**
     * Triggers appropriate third-person animation for dragonblade swings.
     * Uses Better Combat's katana animations if Better Combat is present.
     */
    public static void triggerDragonbladeSwing(boolean rightToLeft) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Only trigger in third-person view
        if (mc.options.getCameraType().isFirstPerson()) return;
        
        if (hasBetterCombat()) {
            // Better Combat handles katana animations automatically through weapon attributes
            // We just need to trigger a swing to activate the katana animations
            mc.player.swing(mc.player.getUsedItemHand());
        } else if (hasSimplySwords()) {
            // Simply Swords might need manual animation triggering
            triggerKatanaSwing(mc.player, rightToLeft);
        } else {
            // Use generic sword swing for vanilla
            mc.player.swing(mc.player.getUsedItemHand());
        }
    }
    
    /**
     * Triggers appropriate third-person animation for shuriken throws.
     * Uses air-punch animations.
     */
    public static void triggerShurikenThrow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Only trigger in third-person view
        if (mc.options.getCameraType().isFirstPerson()) return;
        
        // Use air-punch animation for shurikens
        mc.player.swing(mc.player.getUsedItemHand());
    }
    
    /**
     * Triggers katana-style swing animation.
     * This method can be extended to use specific katana animations from mods.
     */
    private static void triggerKatanaSwing(LocalPlayer player, boolean rightToLeft) {
        // For now, use the standard swing but with potential for mod-specific animations
        // In the future, this could be extended to use specific katana animations
        
        // Try to use a more katana-like swing by triggering multiple swings
        // to simulate the fluid katana motion
        player.swing(player.getUsedItemHand());
        
        // Schedule additional swings for more katana-like effect
        if (hasBetterCombat()) {
            // Better Combat might have specific katana animations
            scheduleKatanaFollowUp(player);
        } else if (hasSimplySwords()) {
            // Simply Swords might have different katana animations
            scheduleKatanaFollowUp(player);
        }
    }
    
    /**
     * Schedules follow-up swings to create a more katana-like animation.
     */
    private static void scheduleKatanaFollowUp(LocalPlayer player) {
        // Schedule additional swings with small delays to create fluid motion
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            // Use a simple approach - trigger additional swings
            // This creates a more fluid, katana-like motion
            for (int i = 1; i <= 2; i++) {
                int delay = i * 3; // 3 ticks between follow-up swings
                mc.level.getServer().tell(
                    new net.minecraft.server.TickTask(
                        (int)(mc.level.getGameTime() + delay),
                        () -> player.swing(player.getUsedItemHand())
                    )
                );
            }
        }
    }
    
    /**
     * Checks if Better Combat mod is loaded.
     */
    public static boolean hasBetterCombat() {
        boolean detected = ModList.get().isLoaded(BETTER_COMBAT_MOD_ID);
        // Debug: Print to console to verify detection
        if (detected) {
        } else {
        }
        return detected;
    }
    
    /**
     * Checks if Simply Swords mod is loaded.
     */
    public static boolean hasSimplySwords() {
        return ModList.get().isLoaded(SIMPLY_SWORDS_MOD_ID);
    }
    
    /**
     * Gets the appropriate UseAnim for dragonblade based on loaded mods.
     */
    public static net.minecraft.world.item.UseAnim getDragonbladeUseAnim() {
        if (hasBetterCombat()) {
            // Better Combat expects weapons to be held like swords
            // Use BLOCK animation for katana-style weapons
            return net.minecraft.world.item.UseAnim.BLOCK;
        } else if (hasSimplySwords()) {
            // Simply Swords might have specific katana animations
            return net.minecraft.world.item.UseAnim.BLOCK;
        } else {
            // Use generic sword animation for vanilla
            return net.minecraft.world.item.UseAnim.BLOCK;
        }
    }
}
