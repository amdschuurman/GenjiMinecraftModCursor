package com.example.genji.content;

import com.example.genji.registry.ModEntities;
import com.example.genji.registry.ModItems;
import com.example.genji.config.GenjiConfig;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import javax.annotation.Nonnull;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ShurikenEntity extends ThrowableItemProjectile implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Animation names from your shuriken_projectile.animation.json
    private static final RawAnimation SPIN  = RawAnimation.begin().thenLoop("animation.shuriken_projectile.spin");
    private static final RawAnimation STUCK = RawAnimation.begin().thenLoop("animation.shuriken_projectile.stuck");

    // Headshot threshold — hits within the upper ~28% of an entity's bbox
    // count as headshots. For an MC player (1.8 bbox), Y >= 1.296 covers the
    // full head model and a bit of the upper neck for forgiving feel.
    private static final double HEADSHOT_TOP_FRACTION = 0.72;
    private static final float  HEADSHOT_DAMAGE_MULTIPLIER = 1.5f;

    // Set to true while a headshot shuriken-hurt() call is on the stack so
    // CommonEvents.onEntityHurt can dispatch the headshot hit sound packet.
    private static final ThreadLocal<Boolean> SHURIKEN_HEADSHOT = ThreadLocal.withInitial(() -> false);
    public static boolean wasShurikenHeadshot() { return Boolean.TRUE.equals(SHURIKEN_HEADSHOT.get()); }


    // Synchronized entity data
    private static final EntityDataAccessor<Boolean> DATA_STUCK = SynchedEntityData.defineId(ShurikenEntity.class, EntityDataSerializers.BOOLEAN);

    // Timing
    private int flightTicks = 0;
    private int stuckTicks = 0;
    private int waterTicks = 0;
    private static final int MAX_FLIGHT_TICKS = 400; // 20 seconds at 20 TPS
    private static final int MAX_STUCK_TICKS = 100;  // 5 seconds at 20 TPS
    private static final int MAX_WATER_TICKS = 60;   // 3 seconds in water

    public ShurikenEntity(EntityType<? extends ShurikenEntity> type, Level level) { 
        super(type, level); 
    }
    
    public ShurikenEntity(Level level, LivingEntity thrower) { 
        super(ModEntities.SHURIKEN.get(), thrower, level); 
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STUCK, false);
    }

    @Override 
    protected Item getDefaultItem() { 
        return ModItems.SHURIKEN.get(); 
    }

    // Synchronized getter/setter for stuck state
    public boolean isStuck() {
        return this.entityData.get(DATA_STUCK);
    }

    public void setStuck(boolean stuck) {
        this.entityData.set(DATA_STUCK, stuck); // Sync to client
    }


    // --- GeckoLib ---
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ctrl", 0, state -> {
            // Use synchronized data for client-side animation decisions
            if (isStuck()) {
                return state.setAndContinue(STUCK);
            } else {
                return state.setAndContinue(SPIN);
            }
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    /** No gravity for shurikens */
    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();

        // Use synchronized data for client-side logic too
        if (isStuck()) {
            // Stay pinned where we hit, keeping our final rotation
            setDeltaMovement(Vec3.ZERO);
            
            stuckTicks++;
            if (!level().isClientSide && stuckTicks >= MAX_STUCK_TICKS) {
                discard();
            }
            return;
        }

        // Spawn green particle trail while in flight. Composter particles
        // dissipate fast so a per-tick spawn keeps the trail visually solid.
        if (level().isClientSide) {
            Vec3 pos = position();
            level().addParticle(net.minecraft.core.particles.ParticleTypes.COMPOSTER,
                    pos.x, pos.y, pos.z,
                    0, 0, 0);
        }

        // Increment flight time
        flightTicks++;
        
        // Despawn after 20 seconds in flight
        if (!level().isClientSide && flightTicks >= MAX_FLIGHT_TICKS) {
            discard();
            return;
        }

        // Water cleanup timer
        if (this.isInWaterOrBubble()) {
            waterTicks++;
            if (!level().isClientSide && waterTicks >= MAX_WATER_TICKS) {
                discard();
                return;
            }
        } else {
            waterTicks = 0; // Reset if not in water
        }
    }

    @Override
    protected void onHitEntity(@Nonnull EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!level().isClientSide) {
            // Don't collide with other shurikens
            if (hit.getEntity() instanceof ShurikenEntity) {
                return;
            }

            var target = hit.getEntity();
            var owner = getOwner();
            DamageSource src = owner instanceof LivingEntity le ? damageSources().thrown(this, le) : damageSources().generic();

            // Headshot detection (OW canon: 1.5x on head). We compare the hit
            // location's Y against the top fraction of the target's bbox so it
            // works for any entity size, not just players.
            double hitY = hit.getLocation().y;
            double headLine = target.getY() + target.getBbHeight() * HEADSHOT_TOP_FRACTION;
            boolean isHeadshot = hitY >= headLine;

            float baseDamage = GenjiConfig.SHURIKEN_DAMAGE_PER_STAR.get().floatValue();
            float damage = isHeadshot ? baseDamage * HEADSHOT_DAMAGE_MULTIPLIER : baseDamage;

            // Flag this hurt() call as a shuriken headshot so CommonEvents can
            // dispatch the dedicated headshot hit-sound packet to the owner.
            SHURIKEN_HEADSHOT.set(isHeadshot);
            try {
                target.hurt(src, damage);
            } finally {
                SHURIKEN_HEADSHOT.set(false);
            }

            // Generic ambient hit sound (level-broadcast — owner-confirm sound
            // is dispatched from CommonEvents.onEntityHurt).
            playSound(net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 0.5f, 1.2f);

            discard();
        }
    }

    @Override
    protected void onHitBlock(@Nonnull BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!level().isClientSide) {
            // Stick EXACTLY at the hit point
            Vec3 hitLocation = hit.getLocation();
            setPos(hitLocation.x, hitLocation.y, hitLocation.z);
            
            // Stop all movement immediately
            setDeltaMovement(Vec3.ZERO);
            
            // Mark as stuck
            setStuck(true);
            
            // Play block-specific break sound
            var blockState = level().getBlockState(hit.getBlockPos());
            var block = blockState.getBlock();
            var soundType = block.defaultBlockState().getSoundType();
            playSound(soundType.getBreakSound(), 0.5f, 1.2f);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
