package com.example.genji.client.render;

/**
 * Shared positioning constants for the first-person ability overlays.
 * Two distinct pose sets — the wakizashi pose used during dash + deflect,
 * and the hand-grip pose used for dragonblade + shuriken. Centralized here
 * so the four overlays don't drift independently when one is tuned.
 */
public final class FPOverlayConstants {
    private FPOverlayConstants() {}

    // ===== Wakizashi pose (hand + short-sword) — used by dash + deflect overlays =====
    public static final double WAKIZASHI_SHIFT_X = 0.12;
    public static final double WAKIZASHI_SHIFT_Y = -0.90;
    public static final double WAKIZASHI_SHIFT_Z = -1.40;
    public static final float  WAKIZASHI_SCALE   = 1.00f;

    // ===== Hand-grip pose — used by dragonblade + shuriken FP overlays =====
    public static final double HAND_GRIP_SHIFT_X = 0.17;
    public static final double HAND_GRIP_SHIFT_Y = -1.00;
    public static final double HAND_GRIP_SHIFT_Z = -1.25;
    public static final float  HAND_GRIP_SCALE   = 1.00f;
}
