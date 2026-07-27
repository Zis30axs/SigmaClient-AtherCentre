package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.entity.projectile.ProjectileItemEntity;

public class ThrowableProjectileEntityVariable extends LambdaVariable<ProjectileItemEntity> {
    public ThrowableProjectileEntityVariable(IValueEvaluator<?, IContext<ProjectileItemEntity>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ProjectileItemEntity;
    }
}