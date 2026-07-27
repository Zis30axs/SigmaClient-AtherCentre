package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;

/** 文件职责：作为平台玩家渲染 mixin 的统一入口。 */
public final class PlayerRenderEntrypoint {
    private PlayerRenderEntrypoint() {
    }

    public static PlayerRenderAction handleRender(
            AbstractClientPlayerEntity player,
            float entityYaw,
            float tickDelta,
            MatrixStack poseStack,
            IRenderTypeBuffer buffers,
            int packedLight,
            boolean isYsmActive) {
        if (!com.shiroha.mmdskin.MmdSkinClient.isInitialized()) {
            return PlayerRenderAction.FALLTHROUGH;
        }
        return PlayerMixinDelegate.handleRender(player, entityYaw, tickDelta, poseStack, buffers, packedLight, isYsmActive);
    }

    public static void renderSceneOverlay(
            AbstractClientPlayerEntity player,
            float tickDelta,
            MatrixStack poseStack,
            int packedLight) {
        if (!com.shiroha.mmdskin.MmdSkinClient.isInitialized()) {
            return;
        }
        PlayerMixinDelegate.renderSceneModel(player, tickDelta, poseStack, packedLight);
    }
}
