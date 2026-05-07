package com.example.genji.client.anim;

import net.minecraft.client.Minecraft;

/** Client-only timeline for the dash first-person animation with variable duration. */
public final class FPDashAnim {
    private FPDashAnim() {}

    private static long startTick = Long.MIN_VALUE;
    private static long endTick = Long.MIN_VALUE;
    private static int durationTicks = 5;
    private static boolean wasJustStarted = false;
    private static long lastTickChecked = Long.MIN_VALUE;

    /** Called when the dash begins with a specific duration (from S2CStartDash packet). */
    public static void start(int duration) {
        long currentTick = gameTicks();
        
        
        // Set new state
        durationTicks = Math.max(2, duration);
        startTick = currentTick;
        endTick = currentTick + durationTicks;
        wasJustStarted = true;
        lastTickChecked = currentTick;
        
    }

    /** Called when the dash begins with default duration (legacy support). */
    public static void start() {
        start(5);
    }

    /** True while the dash clip should be playing. */
    public static boolean isActive() {
        if (startTick == Long.MIN_VALUE) return false;
        
        long currentTick = gameTicks();
        
        // Update state once per tick only (prevents multiple-call issues)
        if (currentTick != lastTickChecked) {
            lastTickChecked = currentTick;
            
            // Keep "just started" flag for 2 ticks to ensure animation controllers see it
            if (wasJustStarted && currentTick > startTick + 1) {
                wasJustStarted = false;
            }
            
            // Check if animation should end
            if (currentTick >= endTick) {
                clear();
                return false;
            }
        }
        
        // Animation is active if we're between start and end
        return currentTick >= startTick && currentTick < endTick;
    }

    /** True only on the very first tick after .start() (used to force-reset controllers). */
    public static boolean justStarted() {
        return wasJustStarted && startTick != Long.MIN_VALUE;
    }

    private static void clear() {
        startTick = Long.MIN_VALUE;
        endTick = Long.MIN_VALUE;
        wasJustStarted = false;
    }

    private static long gameTicks() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0L;
        }
        return mc.level.getGameTime();
    }
    
    /** Force clear the animation state (for debugging). */
    public static void forceStop() {
        clear();
        durationTicks = 5;
    }
}
