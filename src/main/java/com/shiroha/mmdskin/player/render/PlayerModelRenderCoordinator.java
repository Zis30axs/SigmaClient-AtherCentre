/* 鏂囦欢鑱岃矗锛氬崗璋冪帺瀹舵ā鍨嬪湪鏅€氳瑙掋€佺涓€浜虹О涓?VR 鍦烘櫙涓殑娓叉煋鍒囨崲銆?*/
package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.config.ModelConfigManager;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.animation.AnimationStateManager;
import com.shiroha.mmdskin.player.animation.PendingAnimSignalCache;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.shiroha.mmdskin.render.backend.BaseModelInstance;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;

/** 鏂囦欢鑱岃矗锛氬崗璋冪帺瀹舵ā鍨嬪湪鏅€氳瑙掋€佺涓€浜虹О涓?VR 鍦烘櫙涓殑娓叉煋鍒囨崲銆?*/
final class PlayerModelRenderCoordinator {

    private PlayerModelRenderCoordinator() {
    }

    static PlayerRenderAction render(PlayerRenderSelection selection,
                                                    AbstractClientPlayerEntity player,
                                                   float entityYaw,
                                                   float tickDelta,
                                                   MatrixStack matrixStack,
                                                   IRenderTypeBuffer vertexConsumers,
                                                   int packedLight,
                                                   ManagedModel modelData) {
        ModelInstance model = modelData.modelInstance();
        VrRuntimePort vrRuntime = FirstPersonManager.vrRuntime();

        float[] size = PlayerRenderHelper.getModelSize(modelData);
        boolean isVr = selection.isLocalPlayer() && vrRuntime.isLocalPlayerInVr();
        syncVrState(modelData, player, tickDelta, isVr, vrRuntime);

        ModelConfigData modelConfig = ModelConfigManager.getConfig(selection.selectedModel());
        float combinedScale = size[0] * modelConfig.modelScale;
        long modelHandle = model.getModelHandle();
        if (selection.isLocalPlayer()) {
            FirstPersonManager.preRender(modelHandle, combinedScale, true);
        }
        boolean isFirstPerson = !isVr && selection.isLocalPlayer() && FirstPersonManager.isActive();

        if (!isVr) {
            AnimationStateManager.updateAnimationState(player, modelData);
        }
        consumePendingSignals(player, modelData, selection.isLocalPlayer());

        MutableRenderPose params = PlayerRenderHelper.calculateMutableRenderPose(player, modelData, tickDelta);
        boolean needsPostRenderSync = selection.isLocalPlayer();

        matrixStack.push();
        try {
            if (InventoryRenderHelper.isInventoryScreen()) {
                InventoryRenderHelper.renderInInventory(player, model, entityYaw, tickDelta, matrixStack, packedLight, size);
            } else {
                matrixStack.scale(size[0], size[0], size[0]);
                
                RenderScene context = isFirstPerson ? RenderScene.FIRST_PERSON : RenderScene.WORLD;
                model.render(player, params.bodyYaw, params.bodyPitch, params.translation, tickDelta, matrixStack, packedLight, context);
            }

            if (needsPostRenderSync) {
                FirstPersonManager.postRender(modelHandle, player, tickDelta);
                needsPostRenderSync = false;
            }

            ItemRenderHelper.renderItems(
                    player,
                    modelData,
                    matrixStack,
                    vertexConsumers,
                    packedLight,
                    modelConfig.heldItemScale,
                    tickDelta,
                    size[0]);
            return PlayerRenderAction.CANCEL;
        } finally {
            try {
                if (needsPostRenderSync) {
                    FirstPersonManager.postRender(modelHandle, player, tickDelta);
                }
            } finally {
                matrixStack.pop();
            }
        }
    }

    private static void syncVrState(ManagedModel modelData,
                                    AbstractClientPlayerEntity player,
                                    float tickDelta,
                                    boolean isVr,
                                    VrRuntimePort vrRuntime) {
        ModelInstance model = modelData.modelInstance();
        if (!(model instanceof BaseModelInstance abstractModel)) {
            return;
        }

        if (isVr) {
            vrRuntime.applyMmdRenderState(true);
            if (!abstractModel.isVrActive()) {
                MmdSkinRendererPlayerHelper.suppressDefaultAnimationState(modelData);
                vrRuntime.setModelVrEnabled(model.getModelHandle(), true);
                abstractModel.setVrActive(true);
            }
            vrRuntime.updateModelVr(model.getModelHandle(), player, tickDelta, RuntimeConfigPortHolder.get().getVrArmIkStrength());
            return;
        }

        vrRuntime.applyMmdRenderState(false);
        if (abstractModel.isVrActive()) {
            vrRuntime.setModelVrEnabled(model.getModelHandle(), false);
            abstractModel.setVrActive(false);
            MmdSkinRendererPlayerHelper.resetModelAnimationState(player, modelData);
        }
    }

    private static void consumePendingSignals(AbstractClientPlayerEntity player,
                                              ManagedModel modelData,
                                              boolean isLocalPlayer) {
        if (isLocalPlayer) {
            return;
        }

        PendingAnimSignalCache.SignalType signal = PendingAnimSignalCache.consume(player.getUniqueID());
        if (signal == PendingAnimSignalCache.SignalType.RESET) {
            MmdSkinRendererPlayerHelper.ResetPhysics(player);
        }
    }
}
