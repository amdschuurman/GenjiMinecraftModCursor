package com.example.genji.client.anim;

import net.minecraft.client.Minecraft;

/**
 * Client-only timing/state for deflect animations:
 * start -> idle (loop) -> (hit1..3 one-shots) -> end one-shot.
 */
public final class FPDeflectAnim {
    private FPDeflectAnim() {}

    // 20 TPS
    private static final int START_TICKS = 5;   // ~0.25s
    private static final int HIT_TICKS   = 10;  // 0.5s
    private static final int END_TICKS   = 6;   // ~0.3s

    private static long startTick = Long.MIN_VALUE;
    private static long hitUntil  = Long.MIN_VALUE;
    private static long endUntil  = Long.MIN_VALUE;
    private static boolean active = false;
    private static int hitVariant = 1; // 1..3

    public static void start() {
        active = true;
        startTick = ticks();
        endUntil = Long.MIN_VALUE; // cancel end visual if we restart
    }

    public static void end() {
        active = false;
        startTick = Long.MIN_VALUE;
        endUntil = ticks() + END_TICKS;
    }

    public static void hit(int variant) {
        if (variant < 1 || variant > 3) variant = 1;
        hitVariant = variant;
        hitUntil = ticks() + HIT_TICKS;
    }

    public static Mode current() {
        long now = ticks();
        if (active) {
            if (startTick != Long.MIN_VALUE && now - startTick < START_TICKS) return Mode.START;
            if (hitUntil  != Long.MIN_VALUE && now < hitUntil) {
                return switch (hitVariant) { case 2 -> Mode.HIT2; case 3 -> Mode.HIT3; default -> Mode.HIT1; };
            }
            return Mode.IDLE;
        } else {
            if (endUntil != Long.MIN_VALUE && now < endUntil) return Mode.END;
        }
        return Mode.NONE;
    }

    // Edge helpers for force-reset in controllers
    public static boolean justStarted() { return active && startTick != Long.MIN_VALUE && ticks() == startTick; }
    public static boolean justHit()     { long now = ticks(); return hitUntil != Long.MIN_VALUE && now == (hitUntil - HIT_TICKS); }
    public static boolean justEnded()   { long now = ticks(); return !active && endUntil != Long.MIN_VALUE && now == (endUntil - END_TICKS); }

    public static boolean isVisible() { return current() != Mode.NONE; }

    public enum Mode { NONE, START, IDLE, HIT1, HIT2, HIT3, END }

    private static long ticks() {
        var lvl = Minecraft.getInstance().level;
        return (lvl == null) ? 0L : lvl.getGameTime();
    }
}
