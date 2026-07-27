package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.config.ModelConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;

/** 文件职责：计算并同步模型眼球追踪姿态。 */
public final class EyeTrackingHelper {

    private static final float MAX_EYE_ANGLE = 0.1745f;

    private static final float MIN_HORIZONTAL_DIST = 0.01f;

    private EyeTrackingHelper() {
    }

    public static void updateEyeTracking(NativeScenePort scenePort,
                                         long modelHandle,
                                         LivingEntity entity,
                                         float entityYaw,
                                         float tickDelta,
                                         String modelName) {
        ModelConfigData modelConfig = ModelConfigManager.getConfig(modelName);
        if (!modelConfig.eyeTrackingEnabled) {
            scenePort.setEyeTrackingEnabled(modelHandle, false);
            return;
        }

        updateEyeTrackingInternal(scenePort, modelHandle, entity, entityYaw, tickDelta, modelConfig.eyeMaxAngle);
    }

    public static void updateEyeTracking(NativeScenePort scenePort,
                                         long modelHandle,
                                         LivingEntity entity,
                                         float entityYaw,
                                         float tickDelta) {
        updateEyeTrackingInternal(scenePort, modelHandle, entity, entityYaw, tickDelta, MAX_EYE_ANGLE);
    }

    private static void updateEyeTrackingInternal(NativeScenePort scenePort,
                                                  long modelHandle,
                                                  LivingEntity entity,
                                                  float entityYaw,
                                                  float tickDelta,
                                                  float maxAngle) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameRenderer == null) {
            return;
        }

        float camX = (float) mc.gameRenderer.getActiveRenderInfo().getProjectedView().x;
        float camY = (float) mc.gameRenderer.getActiveRenderInfo().getProjectedView().y;
        float camZ = (float) mc.gameRenderer.getActiveRenderInfo().getProjectedView().z;

        float eyeX = (float) MathHelper.lerp(tickDelta, entity.prevPosX, entity.getPosX());
        float eyeY = (float) (MathHelper.lerp(tickDelta, entity.prevPosY, entity.getPosY()) + entity.getEyeHeight());
        float eyeZ = (float) MathHelper.lerp(tickDelta, entity.prevPosZ, entity.getPosZ());

        float dx = camX - eyeX;
        float dy = camY - eyeY;
        float dz = camZ - eyeZ;

        float yawRad = entityYaw * ((float) Math.PI / 180F);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);

        float localX = dx * cosYaw + dz * sinYaw;
        float localY = dy;
        float localZ = -dx * sinYaw + dz * cosYaw;
        float horizontalDist = MathHelper.sqrt(localX * localX + localZ * localZ);

        float eyeAngleX = 0.0f;
        float eyeAngleY = 0.0f;
        if (horizontalDist > MIN_HORIZONTAL_DIST) {
            eyeAngleX = (float) Math.atan2(-localY, horizontalDist);
            eyeAngleY = (float) Math.atan2(localX, localZ);
        }

        eyeAngleX = MathHelper.clamp(eyeAngleX, -maxAngle, maxAngle);
        eyeAngleY = MathHelper.clamp(eyeAngleY, -maxAngle, maxAngle);

        scenePort.setEyeTrackingEnabled(modelHandle, true);
        scenePort.setEyeMaxAngle(modelHandle, maxAngle);
        scenePort.setEyeAngle(modelHandle, eyeAngleX, eyeAngleY);
    }

    public static void disableEyeTracking(NativeScenePort scenePort, long modelHandle) {
        scenePort.setEyeTrackingEnabled(modelHandle, false);
    }
}
