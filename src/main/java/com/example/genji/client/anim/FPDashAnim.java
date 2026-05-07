package com.example.genji.client.anim;

import net.minecraft.client.Minecraft;

/** Client-only timeline for the dash first-person animation with variable duration. */
public final class FPDashAnim {
    private FPDashAnim() {}

    /** Floor for dash anim duration — prevents 0/1-tick "blink" anims from server-side scaling. */
    private static final int MIN_DURATION_TICKS = 2;

    private static long startTick = Long.MIN_VALUE;
    private static long endTick = Long.MIN_VALUE;
    private static int durationTicks = MIN_DURATION_TICKS;
    private static boolean wasJustStarted = false;
    private static long lastTickChecked = Long.MIN_VALUE;

    /** Called when the dash begins with a specific duration (from S2CStartDash packet). */
    public static void start(int duration) {
        long currentTick = gameTicks();
        durationTicks = Math.max(MIN_DURATION_TICKS, duration);
        startTick = currentTick;
        endTick = currentTick + durationTicks;
        wasJustStarted = true;
        lastTickChecked = currentTick;
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
        if (mc.level == null) return 0L;
        return mc.level.getGameTime();
    }
}
