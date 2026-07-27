package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.animation.Priority;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.Armor;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.HandRenderFunction;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.IndicateReload;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.Reset;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.Ride;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.SetAnimation;
import com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl.SetTransitionSpeed;
import com.elfmcys.yesstevemodel.client.compat.CompatMolangStubs;
import com.elfmcys.yesstevemodel.client.compat.parcool.ParcoolCompat;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.controllers.PlayerAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.AnimationState;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.util.data.LazySupplier;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.function.Predicate;

/**
 * Port note (1.20.1 -> 1.16.5): upstream also registers ten third-party compat bindings here
 * (carryon/tacz/swem/parcool/slashblade/sbackpack/create/bettercombat/immersivemelodies/spellbooks);
 * those are cut per the client-only plan. Vanilla animation states and ctrl functions are verbatim.
 */
public class CtrlBinding extends ContextBinding {

    public static final LazySupplier<CtrlBinding> INSTANCE = new LazySupplier<>(CtrlBinding::new);

    private static ReferenceArrayList<AnimationStatePredicate>[] data;

    private CtrlBinding() {
        registerLivingEntityState("death", Priority.HIGHEST, LivingEntity::getShouldBeDead);
        registerLivingEntityState("riptide", Priority.HIGHEST, LivingEntity::isSpinAttacking);
        registerLivingEntityState("sleep", Priority.HIGHEST, entity -> entity.getPose() == Pose.SLEEPING);
        registerLivingEntityState("swim", Priority.HIGHEST, Entity::isSwimming);
        registerLivingEntityState("climb", Priority.HIGHEST, entity -> entity.getPose() == Pose.SWIMMING && isWalking(entity));
        registerLivingEntityState("climbing", Priority.HIGHEST, entity -> entity.getPose() == Pose.SWIMMING);
        registerLivingEntityState("ladder_up", Priority.HIGHEST, entity -> entity.isOnLadder() && getVerticalVelocity(entity) > 0.0f);
        registerLivingEntityState("ladder_stillness", Priority.HIGHEST, entity -> entity.isOnLadder() && getVerticalVelocity(entity) == 0.0f);
        registerLivingEntityState("ladder_down", Priority.HIGHEST, entity -> entity.isOnLadder() && getVerticalVelocity(entity) < 0.0f);
        registerState("fly", Priority.HIGH, CtrlBinding::isFlying);
        registerLivingEntityState("elytra_fly", Priority.HIGH, entity -> entity.getPose() == Pose.FALL_FLYING && entity.isElytraFlying());
        registerLivingEntityState("swim_stand", Priority.NORMAL, entity -> entity.isInWater() && !entity.isOnGround());
        registerLivingEntityState("attacked", Priority.NORMAL, entity -> entity.hurtTime > 0);
        registerLivingEntityState("jump", Priority.NORMAL, entity -> !entity.isOnGround() && !entity.isInWater());
        registerLivingEntityState("sneak", Priority.NORMAL, entity -> entity.isOnGround() && entity.getPose() == Pose.CROUCHING && isWalking(entity));
        registerLivingEntityState("sneaking", Priority.NORMAL, entity -> entity.isOnGround() && entity.getPose() == Pose.CROUCHING);
        registerLivingEntityState("run", Priority.LOWEST, entity -> entity.isOnGround() && entity.isSprinting());
        registerLivingEntityState("walk", Priority.LOWEST, entity -> entity.isOnGround() && isWalking(entity));
        registerLivingEntityState("idle", Priority.LOWEST, entity -> true);

        var("playing_extra_animation", CtrlBinding::isPlayingExtraAnimation);
        function("hold", HandRenderFunction.createAlways());
        function("swing", HandRenderFunction.createWhenSwinging());
        function("use", HandRenderFunction.createWhenUsing());
        function("armor", Armor.create());
        function("ride", Ride.create());
        // Upstream registers ten third-party compat bindings here. The integrations themselves are
        // cut, but their molang SYMBOLS must still exist with upstream's "mod absent" values -
        // models reference them unconditionally and a null lookup poisons the whole expression
        // (see CompatMolangStubs).
        CompatMolangStubs.registerCtrl(this);
        constValue("state_continue", 2);
        constValue("state_stop", 3);
        constValue("state_pause", 4);
        constValue("state_bypass", 5);
        constValue("loop", 10);
        constValue("play_once", 11);
        constValue("hold_on_last_frame", 12);
        function("set_animation", new SetAnimation());
        function("set_beginning_transition_length", new SetTransitionSpeed());
        function("reset", new Reset());
        function("indicate_reload", new IndicateReload());
    }

    private static boolean isPlayingExtraAnimation(IContext<Object> context) {
        AnimatableEntity<?> animatableEntity = context.geoInstance();
        if (!(animatableEntity instanceof CustomPlayerEntity)) {
            return false;
        }
        CustomPlayerEntity customPlayerEntity = (CustomPlayerEntity) animatableEntity;
        return customPlayerEntity.isModelSwitching()
                && customPlayerEntity.getAnimationState(PlayerAnimationController.CAP_CONTROLLER_KEY) != AnimationState.IDLE;
    }

    @SuppressWarnings("unchecked")
    private void registerState(String name, int priority, Predicate<IContext<LivingEntity>> predicate) {
        if (data == null) {
            data = new ReferenceArrayList[Priority.LOWEST + 1];
            for (int i = 0; i < data.length; i++) {
                data[i] = new ReferenceArrayList<>(6);
            }
        }
        data[priority].add(new AnimationStatePredicate(name, priority, predicate));
        livingEntityVar(name, ctx -> evaluateState(name, ctx));
    }

    private void registerLivingEntityState(String name, int priority, EntityCondition predicate) {
        registerState(name, priority, predicate);
    }

    private static boolean evaluateState(String name, IContext<LivingEntity> context) {
        LivingEntity livingEntity = context.entity();
        EntityFrameStateTracker<?> positionTracker = context.geoInstance().getPositionTracker();
        if (positionTracker.getCachedModelId() != null) {
            return name.equals(positionTracker.getCachedModelId());
        }
        if (context.geoInstance() instanceof IPreviewAnimatable) {
            positionTracker.setCachedModelId(StringPool.EMPTY);
            return false;
        }
        if ((livingEntity instanceof PlayerEntity) && ParcoolCompat.isPlayerParcooling((PlayerEntity) livingEntity)) {
            positionTracker.setCachedModelId(StringPool.EMPTY);
            return false;
        }
        Entity vehicle = livingEntity.getRidingEntity();
        if (vehicle != null && vehicle.isAlive()) {
            positionTracker.setCachedModelId(StringPool.EMPTY);
            return false;
        }
        for (int i = 0; i <= 4; i++) {
            for (AnimationStatePredicate animationStatePredicate : data[i]) {
                if (animationStatePredicate.predicate.test(context)) {
                    positionTracker.setCachedModelId(animationStatePredicate.name);
                    return animationStatePredicate.name.equals(name);
                }
            }
        }
        positionTracker.setCachedModelId(StringPool.EMPTY);
        return false;
    }

    private static boolean isWalking(LivingEntity livingEntity) {
        // 1.20.1 walkAnimation.speed(partialTick) -> lerp over the 1.16.5 limb-swing amount pair.
        float partialTick = Minecraft.getInstance().getRenderPartialTicks();
        return Math.abs(MathHelper.lerp(partialTick, livingEntity.prevLimbSwingAmount, livingEntity.limbSwingAmount)) > 0.05f;
    }

    private static float getVerticalVelocity(LivingEntity livingEntity) {
        return 20.0f * ((float) (livingEntity.getPosY() - livingEntity.prevPosY));
    }

    private static boolean isFlying(IContext<LivingEntity> context) {
        AnimatableEntity<?> animatableEntity = context.geoInstance();
        if (animatableEntity instanceof PlayerCapability) {
            PlayerCapability cap = (PlayerCapability) animatableEntity;
            if (!cap.isLocalPlayerModel()) {
                return cap.getPositionTracker().isFlying();
            }
        }
        Entity entity = context.entity();
        if (entity instanceof PlayerEntity) {
            return ((PlayerEntity) entity).abilities.isFlying;
        }
        return false;
    }

    private static final class AnimationStatePredicate {
        private final String name;
        private final int priority;
        private final Predicate<IContext<LivingEntity>> predicate;

        private AnimationStatePredicate(String name, int priority, Predicate<IContext<LivingEntity>> predicate) {
            this.name = name;
            this.priority = priority;
            this.predicate = predicate;
        }
    }

    private interface EntityCondition extends Predicate<IContext<LivingEntity>> {
        boolean check(LivingEntity entity);

        @Override
        default boolean test(IContext<LivingEntity> context) {
            return check(context.entity());
        }
    }
}
