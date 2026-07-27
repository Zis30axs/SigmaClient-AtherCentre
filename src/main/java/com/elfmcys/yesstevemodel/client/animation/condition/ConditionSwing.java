package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.util.Hand;

public class ConditionSwing extends HandItemCondition {
    public ConditionSwing(Hand hand) {
        super(hand,
                hand == Hand.MAIN_HAND ? "swing$" : "swing_offhand$",
                hand == Hand.MAIN_HAND ? "swing#" : "swing_offhand#",
                hand == Hand.MAIN_HAND ? "swing:" : "swing_offhand:");
    }
}
