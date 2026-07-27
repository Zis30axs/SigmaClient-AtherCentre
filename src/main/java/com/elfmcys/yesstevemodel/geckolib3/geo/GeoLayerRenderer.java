package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

public abstract class GeoLayerRenderer<T extends AnimatableEntity<?>> {
    protected final IGeoRenderer<T> renderer;
    public GeoLayerRenderer(IGeoRenderer<T> renderer) { this.renderer = renderer; }
    public abstract void render(MatrixStack matrixStack, IRenderTypeBuffer bufferSource, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch);
    protected RenderType getRenderType(ResourceLocation texture) { return RenderType.getEntityTranslucent(texture); }
}