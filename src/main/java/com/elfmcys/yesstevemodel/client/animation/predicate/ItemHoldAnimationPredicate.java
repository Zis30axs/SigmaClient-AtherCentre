package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionManager;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionSwing;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class ItemHoldAnimationPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }

        int formatVersion = animatable.getModelAssembly().getModelData().getFormatVersion();
        if (entity.isSwingInProgress && !entity.isSleeping()) {
            if (entity.swingProgressInt == 0 && animatable.getPositionTracker().markProcessed(1)) {
                event.getController().stopTransition();
            }

            Hand swingingHand = entity.swingingHand;
            ConditionManager conditionManager = animatable.getModelConfig();
            ConditionSwing condition = swingingHand == Hand.MAIN_HAND
                    ? conditionManager.getSwingMainhand()
                    : conditionManager.getSwingOffhand();
            if (condition != null) {
                String animationName = condition.doTest(entity, swingingHand);
                if (animationName != null && !animationName.isBlank()) {
                    return IAnimationPredicate.playAnimationWithValid(
                            event,
                            animationName,
                            ILoopType.EDefaultLoopTypes.PLAY_ONCE,
                            formatVersion);
                }
            }

            return IAnimationPredicate.playAnimationWithValid(
                    event,
                    swingingHand == Hand.MAIN_HAND ? "swing_hand" : "swing_offhand",
                    ILoopType.EDefaultLoopTypes.PLAY_ONCE,
                    formatVersion);
        }
        return PlayState.CONTINUE;
    }
}
