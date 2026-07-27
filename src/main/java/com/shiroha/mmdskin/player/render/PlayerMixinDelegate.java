package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.model.runtime.ModelSubjectKind;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.scene.client.SceneModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.math.MathHelper;

/**
 * 鐜╁娓叉煋 Mixin 濮旀墭銆? */
public final class PlayerMixinDelegate {

    private PlayerMixinDelegate() {}

    public static PlayerRenderAction handleRender(
            AbstractClientPlayerEntity player, float entityYaw, float tickDelta,
            MatrixStack matrixStack, IRenderTypeBuffer vertexConsumers, int packedLight,
            boolean isYsmActive) {
        PlayerRenderSelection selection = PlayerRenderSelectionResolver.resolve(player, isYsmActive);
        if (selection.hasTerminalAction()) {
            return selection.terminalAction();
        }

        ModelRequestKey requestKey = new ModelRequestKey(
                ModelSubjectKind.PLAYER,
                selection.playerCacheKey(),
                selection.selectedModel());
        ManagedModel modelData = ClientRenderRuntime.get().modelRepository().acquire(requestKey);

        if (modelData == null) {
            if (ClientRenderRuntime.get().modelRepository().isPending(requestKey)) {
                return PlayerRenderAction.CANCEL;
            }
            return PlayerRenderAction.SUPER_RENDER;
        }

        return PlayerModelRenderCoordinator.render(
                selection,
                player,
                entityYaw,
                tickDelta,
                matrixStack,
                vertexConsumers,
                packedLight,
                modelData);
    }

    public static void renderSceneModel(AbstractClientPlayerEntity player, float tickDelta,
                                         MatrixStack matrixStack, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUniqueID().equals(player.getUniqueID())) return;

        SceneModelManager sceneMgr = SceneModelManager.getInstance();
        if (!sceneMgr.isActive() && !sceneMgr.isLoading()) return;

        double renderX = MathHelper.lerp(tickDelta, player.prevPosX, player.getPosX());
        double renderY = MathHelper.lerp(tickDelta, player.prevPosY, player.getPosY());
        double renderZ = MathHelper.lerp(tickDelta, player.prevPosZ, player.getPosZ());
        sceneMgr.renderScene(matrixStack, tickDelta, packedLight, renderX, renderY, renderZ);
    }
}
