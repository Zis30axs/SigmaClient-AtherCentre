package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionArmor;
import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;

public class ArmorPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    private final EquipmentSlotType slot;

    public ArmorPredicate(EquipmentSlotType slot) {
        this.slot = slot;
    }

    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (CosmeticArmorHelper.getArmorItem(entity, this.slot).isEmpty()) {
            return PlayState.STOP;
        }

        ConditionArmor condition = animatable.getModelConfig().getArmor();
        if (condition != null) {
            String animationName = condition.doTest(entity, this.slot);
            if (animationName != null && !animationName.isBlank()) {
                return IAnimationPredicate.playAnimationWithLoop(
                        event,
                        animationName,
                        ILoopType.EDefaultLoopTypes.LOOP);
            }
        }

        String defaultAnimation = this.slot.getName() + ":default";
        if (animatable.getAnimation(defaultAnimation) != null) {
            return IAnimationPredicate.playAnimationWithLoop(
                    event,
                    defaultAnimation,
                    ILoopType.EDefaultLoopTypes.LOOP);
        }
        return PlayState.STOP;
    }
}
