package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.entity.projectile.AbstractArrowEntity;

public abstract class AbstractArrowEntityFunction extends ContextFunction<AbstractArrowEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof AbstractArrowEntity;
    }
}