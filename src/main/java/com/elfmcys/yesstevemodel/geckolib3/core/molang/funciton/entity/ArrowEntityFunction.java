package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.entity.projectile.ArrowEntity;

public abstract class ArrowEntityFunction extends ContextFunction<ArrowEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ArrowEntity;
    }
}