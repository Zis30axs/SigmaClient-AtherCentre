package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.CustomEntityTranslucentRenderType;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Faithful port of the upstream interface. The two-phase contract matters:
 * {@link #renderWithBone} only sets up the pose (this is where the model's height/width scale is
 * applied), and {@link #renderWithBoneAndRenderType} is the pass that actually submits the mesh.
 */
public interface IGeoRenderer<T extends AnimatableEntity<?>> {

    IRenderTypeBuffer getCurrentRTB();

    default void setCurrentRTB(IRenderTypeBuffer bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, MatrixStack poseStack,
                                @Nullable IRenderTypeBuffer bufferSource, @Nullable IVertexBuilder vertexConsumer,
                                int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType,
                                             MatrixStack poseStack, @Nullable IRenderTypeBuffer bufferSource, int textureIndex,
                                             @Nullable IVertexBuilder vertexConsumer, int packedLight, int packedOverlay,
                                             float red, float green, float blue, float alpha) {
        if (vertexConsumer == null) {
            if (bufferSource == null) {
                return;
            }
            vertexConsumer = bufferSource.getBuffer(renderType);
        }
        animatable.resetAnimationState();
        NativeModelRenderer.renderMesh(vertexConsumer, poseStack.getLast(), model.getGeoModel(), model.getMatrixData(),
                model.getAbsPivotData(), textureIndex, 0, packedLight, packedOverlay, red, green, blue, alpha);
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    default void renderEarly(T animatable, MatrixStack poseStack, float partialTick,
                             @Nullable IRenderTypeBuffer bufferSource, @Nullable IVertexBuilder buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
            // Upstream feeds getHeightScale() into x/z and getWidthScale() into y; kept verbatim
            // (the two property names are swapped upstream, do not "fix" it - it changes model size).
            float width = animatable.getHeightScale();
            float height = animatable.getWidthScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, MatrixStack poseStack, float partialTick, @Nullable IRenderTypeBuffer bufferSource,
                            @Nullable IVertexBuilder buffer, int packedLight, int packedOverlayIn, float red, float green,
                            float blue, float alpha) {
    }

    @Nullable
    default RenderType getRenderType(ResourceLocation resourceLocation, boolean bodyVisible, boolean glowing, boolean translucent) {
        if (bodyVisible) {
            if (translucent) {
                return CustomEntityTranslucentRenderType.get(resourceLocation);
            }
            return RenderType.getEntityCutoutNoCull(resourceLocation);
        }
        if (glowing) {
            return RenderType.getOutline(resourceLocation);
        }
        return null;
    }

    default Color getRenderColor(T animatable, float partialTick, MatrixStack poseStack, @Nullable IRenderTypeBuffer bufferSource,
                                 @Nullable IVertexBuilder buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}
