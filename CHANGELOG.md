# Changelog

## 5.0.0

Major polish + correctness pass. Network protocol bumped to `3` (some packets removed, some added — old clients/servers can't connect).

### New abilities

- **Wall-climb** — hold jump while pressed against a wall mid-air to climb up. ~3 s climb budget; refills on ground touch. Plays the wall block's step sound (mining-style cue) every ~0.25 s while climbing.
- **Double-jump** — one extra mid-air jump per airborne stretch with a soft phantom-flap puff. Refreshes on ground or water touch.

### Combat additions

- **Shuriken headshot 1.5×** — Y-coordinate detection; hits in the upper ~28 % of an entity's bbox count as headshots. Distinct hit-confirm sound + CRIT particle burst at the hit point.
- **Dash i-frames** — Genji is invulnerable to all damage during the brief dash motion (OW canon Swift Strike behavior).
- **Dragonblade slash trail** — `SWEEP_ATTACK` particle arc on every swing.
- **Dragonblade slash-hit sound** — the registered-but-never-played `DRAGONBLADE_HIT` sound now plays at each victim's position on impact (server-broadcast so everyone nearby hears it).
- **Ult-ready audio cue** — personal chime exactly on the tick the ult meter crosses 99 → 100.

### Combat fixes

- **Drop-during-dragonblade exploit closed** — 5-layer defense (LivingDeath / LivingDrops / EntityJoinLevel / EntityItemPickup / PlayerLoggedIn) so the dragonblade item cannot leave the active player's inventory or be picked up by anyone.
- **Forged-packet defense on `C2SActivateBlade`** — server now validates that the target slot actually holds a shuriken before overwriting it.
- **Stuck-primaryHeld-on-hand-swap closed** — when the player swaps off a Genji item with the mouse button still held, both client and server now stop firing shurikens immediately (no RELEASE event from vanilla in that case).
- **Dash double-fire from kill-reset closed** — `startDash` now early-returns if a dash is already in progress, so the kill-resets-cooldown can't re-anchor the dash mid-flight.
- **Zombie-blade-on-`/clear` closed** — if the player is in any blade phase but their main hand is no longer a dragonblade, force-cancel rather than running the FSM with no weapon.
- **Deflect-cancel symmetry** — manual Q, dash, and blade-cast now all cancel deflect identically (zero ticks + ensure cooldown). Blade-cast was previously a free deflect cancel.
- **Damage at impact frame** — dragonblade damage now applies at the end of swing-startup (3 – 5 t windup) instead of on swing-press, matching pre-S9 OW slash feel.
- **Dragonblade ending cue at 5 s** (intentional, user-locked).
- **Nano-HUD flicker fixed** — every per-hurt sync packet was silently zeroing `nanoBoostTicks` client-side, briefly clearing the nano flag on every hit during nano. Now sends the full state.

### Balance / OW canon

- **Nano damage unified to 1.5×** across shuriken / dash / deflect / dragonblade. Was 2.0× for the first three and a hardcoded 1.5× for dragonblade. Config migration moves saved 2.0× values forward to 1.5×.
- **Auto-enchant double-buff removed** — nano-active dragonblade was getting Sharpness III + Looting III on top of the 1.5× multiplier, stacking to ~1.7× effective damage. Single-path multiplier now.
- **Dragonblade combat config wired** — `Reach`, `Height`, `ComboWindowTicks` are now actually read from `genji-common.toml` (were hardcoded as `5.0`, `1.0`, `10`). Defaults match the previous hardcoded values; old saved configs migrate forward.
- **Pre-S9 swing timings preserved** (user-locked at `SWING_RL_*` / `SWING_LR_*`).

### Performance

- **Effect re-application throttled 4×** — nano + speed effects now re-applied once every 4 ticks (with 6-tick effect duration for 2-tick overlap, no gap). Previously fired 3 – 4 `addEffect` calls per server tick per nano-active player.
- **Per-tick sync gated on dirty flag** — idle players (no abilities active, no timers ticking) no longer receive a redundant `S2CSyncGenjiData` packet every tick.
- **Capability null-safety** — every server-side `GenjiData` access migrated from the throwing `.get` to the safe `.getOrNull` + null-check pattern. The death/respawn/login race window can no longer throw `IllegalStateException` and abort tick loops.
- **Memory-leak prevention** — central `StateCleanup` handler clears 7 static UUID-keyed maps on player logout + server stop. Previously these grew on every player iteration forever.

### Architecture

- **`CommonEvents.onPlayerTick`** (~120 LOC, 6 jobs) split into 6 focused private helpers.
- **`CommonEvents.onEntityHurt`** (~115 LOC, 9 jobs) split into 6 focused private helpers.
- **`DragonbladeCombat.perPlayerTick`** (~100 LOC, 5 jobs) split into 7 focused private helpers; `damageInFrontWithLOS` got a `forwardCone` + `lineOfSight` extraction.
- **`ModNetwork.sendToPlayer` / `syncTo` / `syncIfDirty`** helpers replace 11 sites of `CHANNEL.sendTo(p, sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT)` boilerplate.
- **`S2CSyncGenjiData` simplified** — dropped the legacy 7-arg and 8-arg constructors; only the full 9-arg ctor remains.
- **`S2CPlayHitSound` simplified** — dropped the unused `BlockPos` field + 2-arg constructor; ambient hit sounds now go through server-side `level.playSound` instead of mixing into this owner-confirm packet.
- **`FPOverlayConstants`** centralizes the wakizashi + hand-grip positioning shared by the 4 first-person overlays.

### Cleanup

- **9 dead files removed** — `DragonbladeOnlyRenderer`, `FPSDragonbladeCompositeRenderer`, `FPSShurikenCompositeRenderer`, `HandOnlyRenderer`, `DragonbladeOverrideTPSRenderer`, `DragonbladeTPSModelForShuriken`, `PlayerAnimationHelper`, `BetterCombatIntegrationHandler`, plus the previous double-jump stub system. ~600 lines of unreachable code.
- **56 debug `System.out.println` calls** removed from render hot paths and per-tick handlers.
- **Dead config keys removed** — `DRAGONBLADE_WIDTH`, `NANO_SHURIKEN_FIRERATE_MULTIPLIER`, `NANO_PITCH_MULTIPLIER`, plus the four `*_TICKS` deprecated aliases.
- **Dead methods / fields removed** — `isNextSwingRight`, `startSwingRecover`, `resetDashCooldown`, `deflectSlot`, `dashResetDoneForThisBlade`, `getNanoFirerateMultiplier`, `getNanoPitchMultiplier`, `isBetterCombatActive`, `Keybinds.ULTIMATE`, `FPDashAnim.start()` no-arg overload, `FPDashAnim.forceStop`.
- **15 missing `subtitles.genji.*` lang keys** added.
- **Mod metadata placeholders fixed** in `gradle.properties` — `Example Mod` / `YourNameHere, OtherNameHere` / `com.example.examplemod` were leaking into the Forge mod-list UI.
- **CRLF/LF text-policy** added to `.gitattributes` so cross-platform line-ending churn can't recur.

### Sound mixing

Volumes normalized across the mod. Loud signature events (ult cast, dragonblade unsheathe) at `1.3`, ambient action audio (shuriken throw, dash whoosh) at `1.0 – 1.4`, owner-confirm hit cues at `1.0 – 1.5`. Previously some abilities were `3.5` louder than others.

### Known intentional divergences from OW canon

These are gated behind explicit user choice and are NOT bugs:

- Dash range 15 blocks (vs. OW ~10 m)
- Nano duration 9 s (vs. OW 8 s)
- Dragonblade ending cue at 5 s pre-end (vs. OW ~1 s)
- Pre-S9 dragonblade swing timings (`SWING_RL_*` / `SWING_LR_*`)
- Self-cast nanoboost (vs. OW: cast by Ana on a teammate)
- Non-canon nano buffs: Absorption V, Instant Heal II on cast, Fire Resistance I, Speed II for blade-only
- Wall-climb has a 3 s budget (OW is more generous)

### Compatibility

- **Forge 1.20.1**, mod loader 47+
- **GeckoLib 4.7.x mandatory** — install separately
- **Better Combat** — soft optional, used for the katana stance pose only

---

## 4.0.0 and earlier

See `git log` for the per-commit history before this changelog was kept.
