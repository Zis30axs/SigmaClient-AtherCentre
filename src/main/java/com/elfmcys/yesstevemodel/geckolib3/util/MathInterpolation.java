package com.elfmcys.yesstevemodel.geckolib3.util;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.util.MathUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.vector.Vector3d;

public class MathInterpolation {
    public static double getYawInterpolation(IContext<Entity> context) {
        Entity entity = context.entity();
        float frameTime = context.animationEvent().getPartialTick();
        Vector3d positionDelta = context.geoInstance().getPositionTracker().getPositionDelta();
        double d = positionDelta.x;
        double d2 = positionDelta.z;
        if (Math.sqrt((d * d) + (d2 * d2)) < 1.0E-4d) {
            return 0.0d;
        }
        return MathHelper.cos(MathUtil.degreesToRadians(MathHelper.wrapDegrees(MathUtil.radiansToDegrees((float) MathHelper.atan2(d2, d)) - (90.0f - MathHelper.wrapDegrees(-MathHelper.lerp(frameTime, entity.prevRotationYaw, entity.rotationYaw))))));
    }

    public static double getPitchInterpolation(IContext<Entity> context) {
        Entity entityMo327xaffeef43 = context.entity();
        float frameTime = context.animationEvent().getPartialTick();
        Vector3d positionDelta = context.geoInstance().getPositionTracker().getPositionDelta();
        double d = positionDelta.x;
        double d2 = positionDelta.z;
        if (Math.sqrt((d * d) + (d2 * d2)) < 1.0E-4d) {
            return 0.0d;
        }
        return MathHelper.sin(MathUtil.degreesToRadians(MathHelper.wrapDegrees(MathUtil.radiansToDegrees((float) MathHelper.atan2(d2, d)) - (90.0f - MathHelper.wrapDegrees(-MathHelper.lerp(frameTime, entityMo327xaffeef43.prevRotationYaw, entityMo327xaffeef43.rotationYaw))))));
    }
}