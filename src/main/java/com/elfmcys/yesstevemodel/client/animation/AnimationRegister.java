package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.BiPredicate;

public class AnimationRegister {
    private static final float MIN_SPEED = 0.05f;
    private static boolean registered;

    public static synchronized void registerAnimationState() {
        if (registered) {
            return;
        }
        registered = true;
        register("death", ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.HIGHEST, (player, event) -> !player.isAlive());
        register("riptide", Priority.HIGHEST, (player, event) -> player.isSpinAttacking());
        register("sleep", Priority.HIGHEST, (player, event) -> player.getPose() == Pose.SLEEPING);
        register("swim", Priority.HIGHEST, (player, event) -> player.isSwimming());
        register("climb", Priority.HIGHEST, (player, event) -> player.getPose() == Pose.SWIMMING && Math.abs(event.getLimbSwingAmount()) > MIN_SPEED);
        register("climbing", Priority.HIGHEST, (player, event) -> player.getPose() == Pose.SWIMMING);
        register("ladder_up", Priority.HIGHEST, (player, event) -> player.isOnLadder() && getVerticalSpeed(player) > 0.0f);
        register("ladder_stillness", Priority.HIGHEST, (player, event) -> player.isOnLadder() && getVerticalSpeed(player) == 0.0f);
        register("ladder_down", Priority.HIGHEST, (player, event) -> player.isOnLadder() && getVerticalSpeed(player) < 0.0f);
        register("fly", Priority.HIGH, (player, event) -> {
            AnimatableEntity<PlayerEntity> animatable = event.getAnimatable();
            if (animatable instanceof PlayerCapability cap) {
                if (!cap.isLocalPlayerModel()) {
                    return cap.getPositionTracker().isFlying();
                }
            }
            return player.abilities.isFlying;
        });
        register("elytra_fly", Priority.HIGH, (player, event) -> player.getPose() == Pose.FALL_FLYING && player.isElytraFlying());
        register("swim_stand", Priority.NORMAL, (player, event) -> player.isInWater() && !player.onGround);
        register("attacked", ILoopType.EDefaultLoopTypes.PLAY_ONCE, 2, (player, event) -> player.hurtTime > 0);
        register("jump", Priority.NORMAL, (player, event) -> !player.onGround && !player.isInWater());
        register("sneak", Priority.NORMAL, (player, event) -> player.onGround && player.getPose() == Pose.CROUCHING && Math.abs(event.getLimbSwingAmount()) > MIN_SPEED);
        register("sneaking", Priority.NORMAL, (player, event) -> player.onGround && player.getPose() == Pose.CROUCHING);
        register("run", Priority.LOW, (player, event) -> player.onGround && player.isSprinting());
        register("walk", Priority.LOW, (player, event) -> player.onGround && event.getLimbSwingAmount() > MIN_SPEED);
        register("idle", Priority.LOWEST, (player, event) -> true);
    }

    private static void register(String animationName, ILoopType loopType, int priority, BiPredicate<PlayerEntity, AnimationEvent<CustomPlayerEntity>> predicate) {
        AnimationManager.register(new AnimationState<>(animationName, loopType, priority, predicate));
    }

    private static void register(String animationName, int priority, BiPredicate<PlayerEntity, AnimationEvent<CustomPlayerEntity>> predicate) {
        register(animationName, ILoopType.EDefaultLoopTypes.LOOP, priority, predicate);
    }

    private static float getVerticalSpeed(PlayerEntity player) {
        return 20.0f * ((float) (player.getPosY() - player.prevPosY));
    }
}
