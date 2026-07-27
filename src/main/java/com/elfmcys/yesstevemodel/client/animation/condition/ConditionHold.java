package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class ConditionHold extends HandItemCondition {
    private static final String EMPTY_MAINHAND = "hold_mainhand:empty";
    private static final String EMPTY_OFFHAND = "hold_offhand:empty";

    public ConditionHold(Hand hand) {
        super(hand,
                hand == Hand.MAIN_HAND ? "hold_mainhand$" : "hold_offhand$",
                hand == Hand.MAIN_HAND ? "hold_mainhand#" : "hold_offhand#",
                hand == Hand.MAIN_HAND ? "hold_mainhand:" : "hold_offhand:");
    }

    @Override
    public String doTest(LivingEntity entity, Hand hand) {
        if (entity.getHeldItem(hand).isEmpty()) {
            return hand == Hand.MAIN_HAND ? EMPTY_MAINHAND : EMPTY_OFFHAND;
        }
        return super.doTest(entity, hand);
    }
}
