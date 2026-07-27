package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.query;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.Minecraft;

public class RotationToCamera extends ContextFunction<Object> {
    @Override
    public Object eval(ExecutionContext<IContext<Object>> context, ArgumentCollection arguments) {
        int args = arguments.getAsInt(context, 0);
        if (args < 0 || args > 1) return null;
        ActiveRenderInfo mainCamera = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();
        if (args == 0) return -mainCamera.getPitch();
        return 180.0f + mainCamera.getYaw();
    }

    @Override
    public boolean validateArgumentSize(int size) { return size == 1; }
}