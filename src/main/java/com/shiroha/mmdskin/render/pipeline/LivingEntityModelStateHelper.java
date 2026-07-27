package com.shiroha.mmdskin.render.pipeline;

import com.shiroha.mmdskin.bridge.runtime.NativeScenePort;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.scene.RenderScene;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;

/** 文件职责：同步生物实体到模型运行时的姿态状态。 */
public final class LivingEntityModelStateHelper {
    private static final NativeScenePort NOOP_SCENE_PORT = new NativeScenePort() {
        @Override
        public void setHeadAngle(long modelHandle, float x, float y, float z, boolean worldSpace) {
        }

        @Override
        public void setModelPositionAndYaw(long modelHandle, float x, float y, float z, float yawRadians) {
        }

        @Override
        public void setAutoBlinkEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void setEyeTrackingEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void setEyeMaxAngle(long modelHandle, float maxAngle) {
        }

        @Override
        public void setEyeAngle(long modelHandle, float eyeX, float eyeY) {
        }
    };

    private static final float MODEL_SCALE = 0.09f;
    private static volatile NativeScenePort scenePort = NOOP_SCENE_PORT;

    private LivingEntityModelStateHelper() {
    }

    public static void configureRuntimeCollaborators(NativeScenePort scenePort) {
        LivingEntityModelStateHelper.scenePort = scenePort != null ? scenePort : NOOP_SCENE_PORT;
    }

    public static void syncModelState(long modelHandle,
                                      LivingEntity entity,
                                      float entityYaw,
                                      float tickDelta,
                                      RenderScene context,
                                      String modelName,
                                      boolean stagePlaying,
        boolean vrActive) {
        if (stagePlaying) {
            scenePort.setHeadAngle(modelHandle, 0.0f, 0.0f, 0.0f, context.isWorldScene());
        } else if (!vrActive) {
            HeadAngleHelper.updateHeadAngle(scenePort, modelHandle, entity, entityYaw, tickDelta, context);
            EyeTrackingHelper.updateEyeTracking(scenePort, modelHandle, entity, entityYaw, tickDelta, modelName);
        }

        Vector3d renderOrigin = entity instanceof PlayerEntity player
                ? resolveRenderOrigin(player, tickDelta)
                : new Vector3d(
                        MathHelper.lerp(tickDelta, entity.prevPosX, entity.getPosX()),
                        MathHelper.lerp(tickDelta, entity.prevPosY, entity.getPosY()),
                        MathHelper.lerp(tickDelta, entity.prevPosZ, entity.getPosZ())
                );
        if (entity instanceof PlayerEntity player) {
            renderOrigin = renderOrigin.add(FirstPersonManager.getLocalVrModelRootOffset(player));
        }

        float posX = (float) (renderOrigin.x * MODEL_SCALE);
        float posY = (float) (renderOrigin.y * MODEL_SCALE);
        float posZ = (float) (renderOrigin.z * MODEL_SCALE);
        float bodyYaw = entity instanceof PlayerEntity player
                ? resolveBodyYaw(player, tickDelta)
                : MathHelper.interpolateAngle(tickDelta, entity.prevRenderYawOffset, entity.renderYawOffset) * ((float) Math.PI / 180F);
        scenePort.setModelPositionAndYaw(modelHandle, posX, posY, posZ, bodyYaw);
    }

    private static Vector3d resolveRenderOrigin(PlayerEntity player, float tickDelta) {
        Vector3d renderOrigin = FirstPersonManager.vrRuntime().getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) {
            return renderOrigin;
        }
        return new Vector3d(
                MathHelper.lerp(tickDelta, player.prevPosX, player.getPosX()),
                MathHelper.lerp(tickDelta, player.prevPosY, player.getPosY()),
                MathHelper.lerp(tickDelta, player.prevPosZ, player.getPosZ())
        );
    }

    private static float resolveBodyYaw(PlayerEntity player, float tickDelta) {
        float vrBodyYaw = FirstPersonManager.vrRuntime().getBodyYawRad(player, tickDelta);
        if (Float.isFinite(vrBodyYaw)) {
            return vrBodyYaw;
        }
        return MathHelper.interpolateAngle(tickDelta, player.prevRenderYawOffset, player.renderYawOffset) * ((float) Math.PI / 180F);
    }
}
