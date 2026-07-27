package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

public class ClientPlayerEntityVariable extends LambdaVariable<AbstractClientPlayerEntity> {
    public ClientPlayerEntityVariable(IValueEvaluator<?, IContext<AbstractClientPlayerEntity>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractClientPlayerEntity;
    }
}