package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.entity.projectile.ProjectileEntity;

public abstract class AbstractProjectileFunction extends ContextFunction<ProjectileEntity> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof ProjectileEntity;
    }
}