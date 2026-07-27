package com.elfmcys.yesstevemodel.client.animation.predicate;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionHold;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingEntityFrameState;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class MainHandHoldPredicate<T extends LivingAnimatable<?>> implements IAnimationPredicate<T> {
    @Override
    public PlayState predicate(AnimationEvent<T> event, ExpressionEvaluator<?> evaluator) {
        T animatable = event.getAnimatable();
        LivingEntity entity = animatable.getEntity();
        if (entity == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (!checkSwingAndUse(entity, Hand.MAIN_HAND)) {
            return PlayState.PAUSE;
        }

        int formatVersion = animatable.getModelAssembly().getModelData().getFormatVersion();
        ItemStack mainHandItem = entity.getHeldItem(Hand.MAIN_HAND);
        if (mainHandItem.getItem() == Items.CROSSBOW && CrossbowItem.isCharged(mainHandItem)) {
            return IAnimationPredicate.playAnimationWithValid(
                    event,
                    "hold_mainhand:charged_crossbow",
                    ILoopType.EDefaultLoopTypes.LOOP,
                    formatVersion);
        }
        if (entity instanceof PlayerEntity && ((PlayerEntity) entity).fishingBobber != null) {
            return IAnimationPredicate.playAnimationWithValid(
                    event,
                    "hold_mainhand:fishing",
                    ILoopType.EDefaultLoopTypes.LOOP,
                    formatVersion);
        }

        LivingEntityFrameState<?> frameState = animatable.getPositionTracker();
        if (!isSameItem(mainHandItem, frameState, Hand.MAIN_HAND)) {
            frameState.setHandItemsForAnimation(mainHandItem, Hand.MAIN_HAND);
            event.getController().stopTransition();
        }

        ConditionHold condition = animatable.getModelConfig().getHoldMainhand();
        if (condition != null) {
            String animationName = condition.doTest(entity, Hand.MAIN_HAND);
            if (animationName != null && !animationName.isBlank()) {
                return IAnimationPredicate.playAnimationWithValid(
                        event,
                        animationName,
                        ILoopType.EDefaultLoopTypes.LOOP,
                        formatVersion);
            }
        }
        return PlayState.STOP;
    }

    private static boolean isSameItem(ItemStack stack, LivingEntityFrameState<?> frameState, Hand hand) {
        ItemStack previousItem = frameState.getHandItemsForAnimation(hand);
        if (previousItem.isDamaged()) {
            return ItemStack.areItemsEqualIgnoreDurability(stack, previousItem);
        }
        return ItemStack.areItemsEqual(stack, previousItem);
    }

    private static boolean checkSwingAndUse(LivingEntity entity, Hand hand) {
        if (entity.isSwingInProgress && entity.swingingHand == hand) {
            return false;
        }
        return !entity.isHandActive() || entity.getActiveHand() != hand;
    }
}
