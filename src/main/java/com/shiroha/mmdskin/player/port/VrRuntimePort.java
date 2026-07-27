package com.shiroha.mmdskin.player.port;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;

/** 文件职责：向普通业务类暴露最小化的 VR 运行时能力边界。 */
public interface VrRuntimePort {
    VrRuntimePort NOOP = new VrRuntimePort() {
        @Override
        public boolean isLocalPlayerInVr() {
            return false;
        }

        @Override
        public boolean isLocalPlayerEyePass() {
            return false;
        }

        @Override
        public float getBodyYawRad(PlayerEntity player, float tickDelta) {
            return Float.NaN;
        }

        @Override
        public float getBodyYawDegrees(PlayerEntity player, float tickDelta) {
            return Float.NaN;
        }

        @Override
        public Vector3d getRenderOrigin(PlayerEntity player, float tickDelta) {
            return null;
        }

        @Override
        public Vector3d getWorldRenderHeadPosition(PlayerEntity player) {
            return null;
        }

        @Override
        public void applyMmdRenderState(boolean active) {
        }

        @Override
        public void setModelVrEnabled(long modelHandle, boolean enabled) {
        }

        @Override
        public void updateModelVr(long modelHandle, PlayerEntity player, float tickDelta, float armIkStrength) {
        }
    };

    static VrRuntimePort noop() {
        return NOOP;
    }

    boolean isLocalPlayerInVr();

    boolean isLocalPlayerEyePass();

    float getBodyYawRad(PlayerEntity player, float tickDelta);

    float getBodyYawDegrees(PlayerEntity player, float tickDelta);

    Vector3d getRenderOrigin(PlayerEntity player, float tickDelta);

    Vector3d getWorldRenderHeadPosition(PlayerEntity player);

    void applyMmdRenderState(boolean active);

    void setModelVrEnabled(long modelHandle, boolean enabled);

    void updateModelVr(long modelHandle, PlayerEntity player, float tickDelta, float armIkStrength);
}
