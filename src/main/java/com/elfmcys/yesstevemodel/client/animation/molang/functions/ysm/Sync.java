package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.AbstractClientPlayerFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

/**
 * Port note: upstream relays the collected arguments to the server (C2SSyncAnimationExpressionPacket)
 * when the animatable is a live {@code PlayerCapability}. Server sync is cut in this client-only port,
 * so only the local branch survives: the preview/model-switch entity executes the expression directly.
 */
public class Sync extends AbstractClientPlayerFunction {

    private static final int MAX_ARGS = 16;

    @Override
    public Object eval(ExecutionContext<IContext<AbstractClientPlayerEntity>> context, ArgumentCollection arguments) {
        if (!context.entity().isClientSide()) {
            return null;
        }
        AnimatableEntity<?> animatableEntity = context.entity().geoInstance();
        if (animatableEntity instanceof CustomPlayerEntity) {
            ((CustomPlayerEntity) animatableEntity).executeAnimationExpression(collectArgs(context, arguments));
        }
        return null;
    }

    private static FloatArrayList collectArgs(ExecutionContext<IContext<AbstractClientPlayerEntity>> context, ArgumentCollection arguments) {
        FloatArrayList floatArrayList = new FloatArrayList(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            floatArrayList.add(arguments.getAsFloat(context, i));
        }
        return floatArrayList;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size <= MAX_ARGS;
    }
}
