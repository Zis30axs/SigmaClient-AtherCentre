package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionVehicle;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.entity.passive.horse.AbstractHorseEntity;
import org.jetbrains.annotations.Nullable;

public class LivingMovementAnimationPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        PlayState ridingState = renderRidingAnimation(event);
        return ridingState == null ? PlayState.STOP : ridingState;
    }

    @Nullable
    public PlayState renderRidingAnimation(AnimationEvent<T> event) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return null;
        }

        Entity vehicle = entity.getRidingEntity();
        if (vehicle == null || !vehicle.isAlive()) {
            return null;
        }

        ConditionVehicle condition = animatable.getModelConfig().getVehicle();
        if (condition != null) {
            String animationName = condition.doTest(entity);
            if (animationName != null && !animationName.isBlank()) {
                return IAnimationPredicate.playAnimationWithLoop(
                        event,
                        animationName,
                        ILoopType.EDefaultLoopTypes.LOOP);
            }
        }

        if (vehicle instanceof PigEntity) {
            return playLoop(event, "ride_pig");
        }
        if (vehicle instanceof AbstractHorseEntity || vehicle instanceof StriderEntity) {
            return playLoop(event, "ride");
        }
        if (vehicle instanceof BoatEntity) {
            return playLoop(event, "boat");
        }
        return playLoop(event, "sit");
    }

    private PlayState playLoop(AnimationEvent<T> event, String animationName) {
        return IAnimationPredicate.playAnimationWithLoop(
                event,
                animationName,
                ILoopType.EDefaultLoopTypes.LOOP);
    }
}
