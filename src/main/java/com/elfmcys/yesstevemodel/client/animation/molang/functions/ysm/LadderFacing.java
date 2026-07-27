package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.entity.LivingEntity;

public class LadderFacing implements IValueEvaluator<Integer, IContext<LivingEntity>> {
    @Override
    public Integer eval(IContext<LivingEntity> ctx) {
        // 1.16.5 has no lastClimbablePos; sample the block at the entity while on a ladder.
        LivingEntity entity = ctx.entity();
        if (entity.isOnLadder()) {
            BlockState state = entity.world.getBlockState(entity.getPosition());
            if (state.hasProperty(HorizontalBlock.HORIZONTAL_FACING)) {
                return state.get(HorizontalBlock.HORIZONTAL_FACING).getHorizontalIndex();
            }
        }
        return 0;
    }
}
