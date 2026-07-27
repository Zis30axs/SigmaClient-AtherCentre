package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.block;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.block.Block;

public class BlockBehaviorVariable extends LambdaVariable<Block> {
    public BlockBehaviorVariable(IValueEvaluator<?, IContext<Block>> valueEvaluator) {
        super(valueEvaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof Block;
    }
}