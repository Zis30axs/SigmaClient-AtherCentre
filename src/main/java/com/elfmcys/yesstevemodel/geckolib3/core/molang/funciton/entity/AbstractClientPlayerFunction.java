package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

public abstract class AbstractClientPlayerFunction extends ContextFunction<AbstractClientPlayerEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractClientPlayerEntity;
    }
}