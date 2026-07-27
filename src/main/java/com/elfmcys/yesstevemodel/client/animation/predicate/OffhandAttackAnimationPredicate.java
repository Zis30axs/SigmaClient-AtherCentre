package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionPassenger;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.List;

public class OffhandAttackAnimationPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }

        List<Entity> passengers = entity.getPassengers();
        Entity firstPassenger = passengers.isEmpty() ? null : passengers.get(0);
        if (firstPassenger == null || !firstPassenger.isAlive()) {
            return PlayState.STOP;
        }

        ConditionPassenger condition = animatable.getModelConfig().getPassenger();
        if (condition != null) {
            String animationName = condition.doTest(entity);
            if (animationName != null && !animationName.isBlank()) {
                return IAnimationPredicate.playAnimationWithLoop(
                        event,
                        animationName,
                        ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        return PlayState.STOP;
    }
}
