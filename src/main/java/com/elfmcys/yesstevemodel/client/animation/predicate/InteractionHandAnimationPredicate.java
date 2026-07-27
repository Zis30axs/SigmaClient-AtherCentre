package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionManager;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionUse;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class InteractionHandAnimationPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }

        int formatVersion = animatable.getModelAssembly().getModelData().getFormatVersion();
        if (!entity.isHandActive() || entity.isSleeping()) {
            return PlayState.STOP;
        }
        if (entity.getItemInUseMaxCount() == 1 && animatable.getPositionTracker().markProcessed(2)) {
            event.getController().stopTransition();
        }

        Hand activeHand = entity.getActiveHand();
        ConditionManager conditionManager = animatable.getModelConfig();
        ConditionUse condition = activeHand == Hand.MAIN_HAND
                ? conditionManager.getUseMainhand()
                : conditionManager.getUseOffhand();
        if (condition != null) {
            String animationName = condition.doTest(entity, activeHand);
            if (animationName != null && !animationName.isBlank()) {
                return IAnimationPredicate.playAnimationWithValid(
                        event,
                        animationName,
                        ILoopType.EDefaultLoopTypes.LOOP,
                        formatVersion);
            }
        }

        return IAnimationPredicate.playAnimationWithValid(
                event,
                activeHand == Hand.MAIN_HAND ? "use_mainhand" : "use_offhand",
                ILoopType.EDefaultLoopTypes.LOOP,
                formatVersion);
    }
}
