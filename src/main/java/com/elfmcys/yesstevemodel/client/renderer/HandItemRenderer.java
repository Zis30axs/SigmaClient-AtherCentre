package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.geckolib3.geo.NativeModelRenderer;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.entity.PlayerGeoEntity;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.LayerTypeConstants;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.HandSide;

public class HandItemRenderer {

    private PlayerGeoEntity geoModel = null;

    /**
     * Renders the custom first-person arm. Returns {@code true} only when the arm
     * mesh was actually submitted to {@link NativeModelRenderer#renderMesh}; a
     * {@code false} return tells the caller to continue with its fallback chain.
     */
    public boolean renderHandItem(ClientPlayerEntity ClientPlayerEntity, ModelAssembly modelAssembly, PlayerCapability capability, HandSide arm, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight, float partialTick) {
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != ClientPlayerEntity) {
            this.geoModel = new PlayerGeoEntity(ClientPlayerEntity, capability);
        }
        this.geoModel.tickModel();
        if (this.geoModel.processAnimation(partialTick) == null || (model = this.geoModel.getCurrentModel()) == null) {
            return false;
        }
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(ClientPlayerEntity, capability, capability.getModelId());
        // Forge event bus removed for standalone client
        ResourceLocation resourceLocation = event.getTextureLocation() == null ? capability.getTextureLocation() : event.getTextureLocation();
        int textureIndex = event.getTextureLocation() == null ? capability.getTextureIndex() : 0;
        IVertexBuilder buffer = bufferSource.getBuffer(CustomEntityTranslucentRenderType.get(resourceLocation));
        int renderPartMask = arm == HandSide.LEFT ? LayerTypeConstants.TYPE_LEFT : LayerTypeConstants.TYPE_RIGHT;
        poseStack.push();
        if (arm == HandSide.LEFT) {
            poseStack.translate(0.25d, 1.8d, 0.0d);
        } else {
            poseStack.translate(-0.25d, 1.8d, 0.0d);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        NativeModelRenderer.renderMesh(buffer, poseStack.getLast(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.pop();
        return true;
    }
}
