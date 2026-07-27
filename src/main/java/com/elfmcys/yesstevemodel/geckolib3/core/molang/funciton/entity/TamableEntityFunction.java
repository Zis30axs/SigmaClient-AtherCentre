package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.entity.passive.TameableEntity;

public abstract class TamableEntityFunction extends ContextFunction<TameableEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof TameableEntity;
    }
}