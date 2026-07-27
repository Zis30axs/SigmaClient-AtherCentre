package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.entity.MobEntity;

public abstract class MobEntityFunction extends ContextFunction<MobEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof MobEntity;
    }
}