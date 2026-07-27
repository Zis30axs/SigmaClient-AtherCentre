package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import net.minecraft.entity.player.PlayerEntity;

public class TextureName implements IValueEvaluator<String, IContext<PlayerEntity>> {
    @Override
    public String eval(IContext<PlayerEntity> context) {
        AnimatableEntity<?> animatableEntity = context.geoInstance();
        if (animatableEntity instanceof CustomPlayerEntity) {
            return ((CustomPlayerEntity) animatableEntity).getCurrentTextureName();
        }
        return null;
    }
}
