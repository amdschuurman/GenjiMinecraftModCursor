package com.example.genji.capability;

import com.example.genji.config.GenjiConfig;
import net.minecraft.nbt.CompoundTag;

public class GenjiData {

    // ====== BASE SWING TIMINGS (TICKS) ======
    public static final int SWING_RL_STARTUP = 5;
    public static final int SWING_RL_RECOVER = 10;
    public static final int SWING_LR_STARTUP = 3;
    public static final int SWING_LR_RECOVER = 11;

    // ====== PERSISTENT METERS (0..100) ======
    private int ult  = 0;
    private int nano = 0;

    // ====== BLADE PHASES (TICKS) ======
    private int bladeTicks = 0;
    private int bladeCastTicks = 0;
    private int bladeSheatheTicks = 0;
    private boolean bladeEndingPlayed = false;

    // ====== SWING STATE ======
    private int bladeSwingStartupTicks = 0;
    private int bladeSwingRecoverTicks = 0;
    private boolean bladeNextRight = true;

    // ====== INVENTORY SLOTS ======
    private int bladeSlot = -1;

    // ====== DEFLECT ======
    private int deflectTicks = 0;
    private int deflectCooldown = 0;

    // ====== DASH ======
    private int dashCooldown = 0;

    // ====== NANO-BOOST RUNTIME ======
    private int nanoBoostTicks = 0;
    private boolean nanoJustActivated = false;

    // ====== DOUBLE JUMP ======
    private boolean doubleJumpUsed = false;

    private boolean dirty = true;

    // ====== CONFIG HELPERS (SECONDS -> TICKS) ======
    private static int cfgBladeActiveTicks()   { return GenjiConfig.secToTicksClamped(GenjiConfig.DRAGONBLADE_DURATION_SECONDS); }
    private static int cfgBladeCastTicks()     { return GenjiConfig.secToTicksClamped(GenjiConfig.DRAGONBLADE_CAST_SECONDS); }
    private static int cfgBladeSheatheTicks()  { return GenjiConfig.secToTicksClamped(GenjiConfig.DRAGONBLADE_SHEATHE_SECONDS); }
    private static int cfgDeflectMaxTicks()    { return GenjiConfig.secToTicksClamped(GenjiConfig.DEFLECT_MAX_DURATION_SECONDS); }
    private static int cfgDeflectCooldown()    { return GenjiConfig.secToTicksClamped(GenjiConfig.DEFLECT_COOLDOWN_SECONDS); }
    private static int cfgDashCooldown()       { return GenjiConfig.secToTicksClamped(GenjiConfig.DASH_COOLDOWN_SECONDS); }
    private static int cfgNanoDuration()       { return GenjiConfig.secToTicksClamped(GenjiConfig.NANO_DURATION_SECONDS); }

    // ====== TICK ======
    public void tick() {
        if (bladeTicks > 0) { bladeTicks--; dirty = true; }
        if (bladeCastTicks > 0) { bladeCastTicks--; dirty = true; }
        if (bladeSheatheTicks > 0) { bladeSheatheTicks--; dirty = true; }

        if (bladeSwingStartupTicks > 0) { bladeSwingStartupTicks--; dirty = true; }
        if (bladeSwingRecoverTicks > 0) { bladeSwingRecoverTicks--; dirty = true; }

        if (deflectTicks > 0) { deflectTicks--; dirty = true; }
        if (deflectCooldown > 0) { deflectCooldown--; dirty = true; }

        if (dashCooldown > 0) { dashCooldown--; dirty = true; }

        if (nanoBoostTicks > 0) {
            nanoBoostTicks--;
            dirty = true;
            if (nanoJustActivated) nanoJustActivated = false; // clear one-shot after first tick
        }
    }

    public boolean isDirty() { return dirty; }
    public void markSynced() { dirty = false; }

    // ====== METERS ======
    public int getUlt()  { return ult; }
    public int getNano() { return nano; }
    public void setUlt(int v)  { ult = clamp01(v);  dirty = true; }
    public void setNano(int v) { nano = clamp01(v); dirty = true; }

    public void addUltFromDamage(float damage) {
        double full = GenjiConfig.ULT_DAMAGE_FOR_FULL_CHARGE.get();
        if (full <= 0.0) full = 50.0;
        int add = (int)Math.ceil((damage / full) * 100.0);
        setUlt(Math.min(100, ult + add));
    }

    public void addNanoFromDamage(float damage) {
        double full = GenjiConfig.NANO_DAMAGE_FOR_FULL_CHARGE.get();
        if (full <= 0.0) full = 120.0;
        int add = (int)Math.ceil((damage / full) * 100.0);
        setNano(Math.min(100, nano + add));
    }

    // ====== NANO-BOOST RUNTIME ======
    public boolean isNanoActive() { return nanoBoostTicks > 0; }
    public int getNanoBoostTicks() { return nanoBoostTicks; }
    public boolean nanoJustActivated() { return nanoJustActivated; }

    public void beginNanoBoost() {
        nanoBoostTicks = cfgNanoDuration();
        nanoJustActivated = true;
        nano = 0; // spend meter
        dirty = true;
    }

    public double getNanoDamageMultiplier()       { return isNanoActive() ? Math.max(1.0, GenjiConfig.NANO_DAMAGE_MULTIPLIER.get()) : 1.0; }
    public double getNanoSlashSpeedMultiplier()   { return isNanoActive() ? Math.max(1.0, GenjiConfig.NANO_SLASH_SPEED_MULTIPLIER.get()) : 1.0; }

    // ====== BLADE ======
    public boolean isBladeActive()    { return bladeTicks > 0; }
    public boolean isCastingBlade()   { return bladeCastTicks > 0; }
    public boolean isSheathing()      { return bladeSheatheTicks > 0; }

    public int getBladeTicks()        { return bladeTicks; }
    public int getBladeCastTicks()    { return bladeCastTicks; }
    public int getBladeSheatheTicks() { return bladeSheatheTicks; }

    public boolean canBlade() {
        return ult >= 100 && bladeCastTicks == 0 && bladeTicks == 0 && bladeSheatheTicks == 0;
    }

    public void beginBladeCast() {
        bladeCastTicks = cfgBladeCastTicks();
        ult = 0;
        cancelDeflectStartCooldown();
        dirty = true;
    }

    public void activateBlade() {
        bladeCastTicks = 0;
        bladeTicks = cfgBladeActiveTicks();
        bladeEndingPlayed = false;
        bladeSwingStartupTicks = 0;
        bladeSwingRecoverTicks = 0;
        bladeNextRight = false; // Always start with LEFT swing
        
        // Reset dash cooldown when dragonblade activates
        resetDashCooldown();
        
        dirty = true;
    }

    public void endBladeStartSheathe() {
        bladeTicks = 0;
        bladeSheatheTicks = cfgBladeSheatheTicks();
        bladeSwingStartupTicks = 0;
        bladeSwingRecoverTicks = 0;
        dirty = true;
    }

    /** Allow callers (e.g., dash packet) to cancel the sheathe phase early. */
    public void cancelSheathe() {
        if (bladeSheatheTicks > 0) { bladeSheatheTicks = 0; dirty = true; }
    }

    public void cancelBlade() {
        if (bladeTicks > 0)       { bladeTicks = 0; dirty = true; }
        if (bladeCastTicks > 0)   { bladeCastTicks = 0; dirty = true; }
        if (bladeSheatheTicks > 0){ bladeSheatheTicks = 0; dirty = true; }
    }

    public boolean bladeEndingPlayed() { return bladeEndingPlayed; }
    public void markBladeEndingPlayed() { bladeEndingPlayed = true; dirty = true; }

    public void setBladeSlot(int slot)  { bladeSlot = slot; dirty = true; }
    public int  getBladeSlot()          { return bladeSlot; }
    public void clearBladeSlot()        { bladeSlot = -1; dirty = true; }

    // ====== SWINGS (apply Nano speed multiplier) ======
    public boolean isNextSwingRight()   { return bladeNextRight; }
    public boolean nextSwingIsRight()   { return bladeNextRight; }
    public void setNextSwingRight(boolean v){ bladeNextRight = v; dirty = true; }
    public void resetSwingToLeft()      { bladeNextRight = false; dirty = true; }

    public int  getSwingStartupTicks()  { return bladeSwingStartupTicks; }
    public int  getSwingRecoverTicks()  { return bladeSwingRecoverTicks; }

    public boolean canSwingNow() {
        return isBladeActive()
                && bladeCastTicks == 0
                && bladeSwingStartupTicks == 0
                && bladeSwingRecoverTicks == 0;
    }

    public void startSwingStartup(boolean rightToLeft) {
        double m = getNanoSlashSpeedMultiplier();
        int base = rightToLeft ? SWING_RL_STARTUP : SWING_LR_STARTUP;
        int dur  = Math.max(1, (int)Math.round(base / m));
        bladeNextRight = !rightToLeft;
        bladeSwingStartupTicks = dur;
        dirty = true;
    }

    public void startSwingRecover(boolean rightToLeft) {
        double m = getNanoSlashSpeedMultiplier();
        int base = rightToLeft ? SWING_RL_RECOVER : SWING_LR_RECOVER;
        int dur  = Math.max(1, (int)Math.round(base / m));
        bladeSwingRecoverTicks = dur;
        dirty = true;
    }

    public void startSwingRecovery(boolean rightToLeft) { startSwingRecover(rightToLeft); }

    // ====== DEFLECT ======
    public int  getDeflectTicks()    { return deflectTicks; }
    public int  getDeflectCooldown() { return deflectCooldown; }
    public boolean isDeflectActive() { return deflectTicks > 0; }

    public boolean tryDeflect() {
        if (deflectCooldown > 0 || bladeCastTicks > 0) return false;
        deflectTicks = cfgDeflectMaxTicks();
        deflectCooldown = cfgDeflectCooldown();
        dirty = true;
        return true;
    }

    /**
     * Cancel an active deflect AND ensure the deflect cooldown is set. Used
     * by all three cancel paths (manual Q, dash starting, blade-cast starting)
     * for symmetric behavior. No-op if deflect was not active so non-deflecting
     * players don't accidentally inherit a deflect cooldown.
     */
    public void cancelDeflectStartCooldown() {
        if (deflectTicks > 0) {
            deflectTicks = 0;
            if (deflectCooldown <= 0) deflectCooldown = cfgDeflectCooldown();
            dirty = true;
        }
    }

    // ====== DASH ======
    public int  getDashCooldown() { return dashCooldown; }
    public boolean tryDash() {
        if (dashCooldown > 0) return false;
        dashCooldown = cfgDashCooldown();
        dirty = true;
        return true;
    }
    public void resetDashCooldown() { this.dashCooldown = 0; dirty = true; }
    public void clearDashCooldown() { resetDashCooldown(); }

    // ====== DOUBLE JUMP ======
    public boolean isDoubleJumpUsed() { return doubleJumpUsed; }
    public void useDoubleJump() { doubleJumpUsed = true; dirty = true; }
    public void resetDoubleJump() { doubleJumpUsed = false; dirty = true; }

    // ====== PERSISTENCE ======
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("ult", ult);
        t.putInt("nano", nano);

        t.putInt("blade", bladeTicks);
        t.putInt("bladeCast", bladeCastTicks);
        t.putInt("bladeSheathe", bladeSheatheTicks);
        t.putBoolean("bladeEndingPlayed", bladeEndingPlayed);
        t.putBoolean("bladeNextRight", bladeNextRight);
        t.putInt("bladeSwingStartup", bladeSwingStartupTicks);
        t.putInt("bladeSwingRecover", bladeSwingRecoverTicks);
        t.putInt("bladeSlot", bladeSlot);

        t.putInt("deflect", deflectTicks);
        t.putInt("deflectCd", deflectCooldown);

        t.putInt("dashCd", dashCooldown);

        t.putInt("nanoBoost", nanoBoostTicks);
        t.putBoolean("nanoJust", nanoJustActivated);

        t.putBoolean("doubleJumpUsed", doubleJumpUsed);

        return t;
    }

    public void load(CompoundTag t) {
        ult = t.getInt("ult");
        nano = t.contains("nano") ? t.getInt("nano") : 0;

        bladeTicks = t.getInt("blade");
        bladeCastTicks = t.getInt("bladeCast");
        bladeSheatheTicks = t.getInt("bladeSheathe");
        bladeEndingPlayed = t.contains("bladeEndingPlayed") && t.getBoolean("bladeEndingPlayed");
        bladeNextRight = t.contains("bladeNextRight") && t.getBoolean("bladeNextRight");
        bladeSwingStartupTicks = t.getInt("bladeSwingStartup");
        bladeSwingRecoverTicks = t.getInt("bladeSwingRecover");
        bladeSlot = t.contains("bladeSlot") ? t.getInt("bladeSlot") : -1;

        deflectTicks = t.getInt("deflect");
        deflectCooldown = t.getInt("deflectCd");

        dashCooldown = t.getInt("dashCd");

        nanoBoostTicks = t.getInt("nanoBoost");
        nanoJustActivated = t.contains("nanoJust") && t.getBoolean("nanoJust");

        doubleJumpUsed = t.contains("doubleJumpUsed") && t.getBoolean("doubleJumpUsed");

        dirty = true;
    }

    private static int clamp01(int v) { return v < 0 ? 0 : Math.min(100, v); }
}
