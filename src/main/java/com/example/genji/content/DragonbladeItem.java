package com.example.genji.content;

import com.example.genji.client.anim.DragonbladeFPAnim;
import com.example.genji.client.anim.FPDashAnim;
import com.example.genji.client.anim.FPDeflectAnim;
import com.example.genji.client.fx.DragonbladeFxState;
import com.example.genji.client.render.PerspectiveAwareDragonbladeRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import com.example.genji.config.GenjiConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;

public class DragonbladeItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Dash
    private static final RawAnimation CLIP_DASH      = RawAnimation.begin().thenPlay("dash");
    // Deflect
    private static final RawAnimation CLIP_DEFLECT_START = RawAnimation.begin().thenPlay("deflect_start");
    private static final RawAnimation CLIP_DEFLECT_IDLE  = RawAnimation.begin().thenLoop("deflect_idle");
    private static final RawAnimation CLIP_DEFLECT_HIT1  = RawAnimation.begin().thenPlay("deflect_hit1");
    private static final RawAnimation CLIP_DEFLECT_HIT2  = RawAnimation.begin().thenPlay("deflect_hit2");
    private static final RawAnimation CLIP_DEFLECT_HIT3  = RawAnimation.begin().thenPlay("deflect_hit3");
    private static final RawAnimation CLIP_DEFLECT_END   = RawAnimation.begin().thenPlay("deflect_end");
    // Blade
    private static final RawAnimation CLIP_UNSHEATHE = RawAnimation.begin().thenPlay("dragonblade_unsheathe");
    private static final RawAnimation CLIP_IDLE      = RawAnimation.begin().thenLoop("dragonblade_idle");
    private static final RawAnimation CLIP_SHEATHE   = RawAnimation.begin().thenPlay("dragonblade_sheathe");
    private static final RawAnimation CLIP_SWING_L   = RawAnimation.begin().thenPlay("dragonblade_swing_left");
    private static final RawAnimation CLIP_SWING_R   = RawAnimation.begin().thenPlay("dragonblade_swing_right");

    private static final int TRANSITION_TICKS = 0;

    private enum Phase {
        NONE,
        DASH,
        DEFLECT_START, DEFLECT_HIT1, DEFLECT_HIT2, DEFLECT_HIT3, DEFLECT_IDLE, DEFLECT_END,
        UNSHEATHE, IDLE, SWING_L, SWING_R, SHEATHE
    }
    // Non-static per-instance phase tracking
    private Phase handPhase  = Phase.NONE;
    private Phase bladePhase = Phase.NONE;

    public DragonbladeItem(Tier tier, int dmg, float speed, Properties props) {
        super(tier, dmg, speed, props);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.SHARPNESS || 
               enchantment == Enchantments.MOB_LOOTING || 
               super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        // No default enchantments - we use custom damage system
        return stack;
    }
    
    /**
     * Check if Better Combat mod is active
     */
    private static boolean isBetterCombatActive() {
        return net.minecraftforge.fml.ModList.get().isLoaded("bettercombat");
    }
    
    @Override
    public float getDamage() {
        // Dragonblade base damage sourced from config (default 11.0 HP)
        return GenjiConfig.DAMAGE_PER_DRAGONBLADE_SWING.get().floatValue();
    }
    

    /**
     * Apply nanoboost enchantments to the dragonblade item.
     * Adds Sharpness III and Looting III when nanoboost is active.
     */
    public static void applyNanoboostEnchantments(ItemStack stack, boolean nanoboostActive) {
        if (nanoboostActive) {
            // Add Sharpness III for +50% damage boost (1.5x damage)
            stack.enchant(Enchantments.SHARPNESS, 3);
            // Add Looting III for better drops
            stack.enchant(Enchantments.MOB_LOOTING, 3);
        } else {
            // Remove nanoboost enchantments by creating a new stack without them
            ItemStack newStack = new ItemStack(stack.getItem());
            if (stack.hasTag() && stack.getTag() != null) {
                newStack.setTag(stack.getTag().copy());
            }
            // Copy all enchantments except the ones we want to remove
            var enchantments = stack.getEnchantmentTags();
            for (int i = 0; i < enchantments.size(); i++) {
                var enchantmentTag = enchantments.getCompound(i);
                var enchantment = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(
                    net.minecraft.resources.ResourceLocation.parse(enchantmentTag.getString("id"))
                );
                if (enchantment != Enchantments.SHARPNESS && enchantment != Enchantments.MOB_LOOTING) {
                    newStack.enchant(enchantment, enchantmentTag.getInt("lvl"));
                }
            }
            // Copy the new stack back
            stack.setTag(newStack.getTag());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar ctrls) {
        ctrls.add(new AnimationController<>(this, "hand_ctrl", TRANSITION_TICKS, state -> {
            DragonbladeItem item = state.getAnimatable();
            Phase want = currentPhase();
            
            // Force reset on edges for stable restart
            if (want == Phase.DASH && FPDashAnim.justStarted()) {
                state.getController().forceAnimationReset();
                item.handPhase = Phase.NONE; // Force phase reset to ensure setClip is called
            } else if (isDeflectPhase(want) && (FPDeflectAnim.justStarted() || FPDeflectAnim.justHit() || FPDeflectAnim.justEnded())) {
                state.getController().forceAnimationReset();
            } else if ((want == Phase.SWING_L || want == Phase.SWING_R) && DragonbladeFPAnim.justStarted()) {
                // Force reset when a new swing starts, even if it's the same direction
                state.getController().forceAnimationReset();
                item.handPhase = Phase.NONE; // Force phase reset to ensure setClip is called
            }
            
            if (want != item.handPhase) { 
                setClip(state, want); 
                item.handPhase = want; 
            }
            return want == Phase.NONE ? PlayState.STOP : PlayState.CONTINUE;
        }));
        ctrls.add(new AnimationController<>(this, "blade_ctrl", TRANSITION_TICKS, state -> {
            DragonbladeItem item = state.getAnimatable();
            Phase want = currentPhase();
            
            if (want == Phase.DASH && FPDashAnim.justStarted()) {
                state.getController().forceAnimationReset();
                item.bladePhase = Phase.NONE; // Force phase reset to ensure setClip is called
            } else if (isDeflectPhase(want) && (FPDeflectAnim.justStarted() || FPDeflectAnim.justHit() || FPDeflectAnim.justEnded())) {
                state.getController().forceAnimationReset();
            } else if ((want == Phase.SWING_L || want == Phase.SWING_R) && DragonbladeFPAnim.justStarted()) {
                // Force reset when a new swing starts, even if it's the same direction
                state.getController().forceAnimationReset();
                item.bladePhase = Phase.NONE; // Force phase reset to ensure setClip is called
            }
            
            if (want != item.bladePhase) { 
                setClip(state, want); 
                item.bladePhase = want; 
            }
            return want == Phase.NONE ? PlayState.STOP : PlayState.CONTINUE;
        }));
    }

    private static boolean isDeflectPhase(Phase p) {
        return p == Phase.DEFLECT_START || p == Phase.DEFLECT_IDLE || p == Phase.DEFLECT_END
                || p == Phase.DEFLECT_HIT1 || p == Phase.DEFLECT_HIT2 || p == Phase.DEFLECT_HIT3;
    }

    private static void setClip(AnimationState<DragonbladeItem> state, Phase want) {
        switch (want) {
            case DASH               -> state.setAndContinue(CLIP_DASH);
            case DEFLECT_START      -> state.setAndContinue(CLIP_DEFLECT_START);
            case DEFLECT_IDLE       -> state.setAndContinue(CLIP_DEFLECT_IDLE);
            case DEFLECT_HIT1       -> state.setAndContinue(CLIP_DEFLECT_HIT1);
            case DEFLECT_HIT2       -> state.setAndContinue(CLIP_DEFLECT_HIT2);
            case DEFLECT_HIT3       -> state.setAndContinue(CLIP_DEFLECT_HIT3);
            case DEFLECT_END        -> state.setAndContinue(CLIP_DEFLECT_END);
            case UNSHEATHE          -> state.setAndContinue(CLIP_UNSHEATHE);
            case IDLE               -> state.setAndContinue(CLIP_IDLE);
            case SWING_L            -> state.setAndContinue(CLIP_SWING_L);
            case SWING_R            -> state.setAndContinue(CLIP_SWING_R);
            case SHEATHE            -> state.setAndContinue(CLIP_SHEATHE);
            case NONE               -> { /* no-op */ }
        }
    }

    private static Phase currentPhase() {
        if (FPDashAnim.isActive()) return Phase.DASH;

        switch (FPDeflectAnim.current()) {
            case START -> { return Phase.DEFLECT_START; }
            case HIT1  -> { return Phase.DEFLECT_HIT1; }
            case HIT2  -> { return Phase.DEFLECT_HIT2; }
            case HIT3  -> { return Phase.DEFLECT_HIT3; }
            case IDLE  -> { return Phase.DEFLECT_IDLE; }
            case END   -> { return Phase.DEFLECT_END; }
            default    -> { /* fallthrough */ }
        }

        if (DragonbladeFxState.isUnsheathing()) return Phase.UNSHEATHE;
        if (DragonbladeFxState.isSheathing())   return Phase.SHEATHE;

                if (DragonbladeFxState.isActive() || DragonbladeFxState.isGrace()) {
                    if (DragonbladeFPAnim.isLeft())  return Phase.SWING_L;
                    if (DragonbladeFPAnim.isRight()) return Phase.SWING_R;
                    return Phase.IDLE;
                }
        return Phase.NONE;
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initializeClient(@Nonnull java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private final PerspectiveAwareDragonbladeRenderer renderer = new PerspectiveAwareDragonbladeRenderer();
            @OnlyIn(Dist.CLIENT)
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { 
                return renderer; 
            }
        });
    }

    // ===== FAILSAFES: Prevent moving/dropping/duplicating dragonblade =====
    
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        // Prevent dropping the dragonblade item
        return false;
    }

    @Override
    public net.minecraft.world.InteractionResult onItemUseFirst(ItemStack stack, net.minecraft.world.item.context.UseOnContext context) {
        // Prevent using the dragonblade item on blocks
        return net.minecraft.world.InteractionResult.FAIL;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(@Nonnull net.minecraft.world.item.context.UseOnContext context) {
        // Prevent using the dragonblade item on blocks
        return net.minecraft.world.InteractionResult.FAIL;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull net.minecraft.world.InteractionHand hand) {
        // Prevent using the dragonblade item
        return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
