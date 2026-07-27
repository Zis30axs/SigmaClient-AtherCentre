package com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.blocks;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import net.minecraft.block.Block;

public abstract class AbstractBlockFunction extends ContextFunction<Block> {
    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Block;
    }
}