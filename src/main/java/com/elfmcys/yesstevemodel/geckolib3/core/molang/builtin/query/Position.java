package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.query;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;

public class Position extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int value = arguments.getAsInt(context, 0);
        float partialTicks = context.entity().animationEvent().getPartialTick();
        Entity entity = context.entity().entity();
        switch (value) {
            case 0:
                return MathHelper.lerp(partialTicks, entity.prevPosX, entity.getPosX());
            case 1:
                return MathHelper.lerp(partialTicks, entity.prevPosY, entity.getPosY());
            case 2:
                return MathHelper.lerp(partialTicks, entity.prevPosZ, entity.getPosZ());
            default:
                return null;
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}