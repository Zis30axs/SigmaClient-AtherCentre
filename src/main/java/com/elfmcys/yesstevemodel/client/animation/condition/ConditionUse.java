package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.util.Hand;

public class ConditionUse extends HandItemCondition {
    public ConditionUse(Hand hand) {
        super(hand,
                hand == Hand.MAIN_HAND ? "use_mainhand$" : "use_offhand$",
                hand == Hand.MAIN_HAND ? "use_mainhand#" : "use_offhand#",
                hand == Hand.MAIN_HAND ? "use_mainhand:" : "use_offhand:");
    }
}
