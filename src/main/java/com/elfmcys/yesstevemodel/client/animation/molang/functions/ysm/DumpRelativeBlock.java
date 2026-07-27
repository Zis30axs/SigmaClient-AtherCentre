package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

public class DumpRelativeBlock extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        BlockState blockState;
        ResourceLocation key;
        if (!context.entity().isDebugMode() || (blockState = MolangUtils.getRelativeBlockState(context, arguments)) == null || (key = Registry.BLOCK.getKey(blockState.getBlock())) == null) {
            return null;
        }
        context.entity().logWarningComponent(new StringTextComponent("Display ").append(copyOnClickText(blockState.getBlock().getTranslatedName().getStringTruncated(99))));
        context.entity().logWarningComponent(new StringTextComponent("Name ").append(copyOnClickText(key.toString())));
        BlockTags.getCollection().getOwningTags(blockState.getBlock()).forEach(tagId -> {
            context.entity().logWarningComponent(new StringTextComponent("Tag ").append(copyOnClickText(tagId.toString())));
        });
        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }

    private static ITextComponent copyOnClickText(String text) {
        return new StringTextComponent(text).setStyle(Style.EMPTY
                .setInsertion(text)
                .setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Click to copy"))));
    }
}
