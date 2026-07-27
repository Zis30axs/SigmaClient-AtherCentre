package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.entity.projectile.AbstractArrowEntity;

public class AbstractArrowEntityVariable extends LambdaVariable<AbstractArrowEntity> {
    public AbstractArrowEntityVariable(IValueEvaluator<?, IContext<AbstractArrowEntity>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractArrowEntity;
    }
}