package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import net.minecraft.entity.player.PlayerEntity;

public class FirstPersonModHide implements IValueEvaluator<Boolean, IContext<PlayerEntity>> {
    @Override
    public Boolean eval(IContext<PlayerEntity> ctx) {
        // FirstPerson mod is absent in this runtime; never hide.
        return false;
    }
}
