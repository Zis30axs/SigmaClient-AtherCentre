package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.query;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.util.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.entity.Entity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

public class RelativeBlockHasAllTags extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        BlockState block = MolangUtils.getRelativeBlockState(context, arguments);
        if (block == null) return null;
        for (int i = 3; i < arguments.size(); i++) {
            ResourceLocation tagId = arguments.getResourceLocation(context, i);
            if (tagId == null) return null;
            ITag<Block> tag = BlockTags.getCollection().get(tagId);
            if (tag == null || !tag.contains(block.getBlock())) return false;
        }
        return true;
    }
    @Override
    public boolean validateArgumentSize(int size) { return size >= 4; }
}