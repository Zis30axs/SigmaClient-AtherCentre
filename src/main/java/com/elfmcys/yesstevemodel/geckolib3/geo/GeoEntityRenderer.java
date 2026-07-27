package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.JomlMatrix4fBridge;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public abstract class GeoEntityRenderer<TEntity extends Entity, T extends AnimatableEntity<TEntity>> extends EntityRenderer<TEntity> implements IGeoRenderer<T> {

    /** Kept from the previous port: layer support the upstream base does not have but this port uses. */
    protected final List<GeoLayerRenderer<T>> layerRenderers = new ArrayList<>();

    public Matrix4f worldMatrix;

    public Matrix4f modelMatrix;

    private IRenderCycle renderState;

    public IRenderTypeBuffer bufferSource;

    protected GeoEntityRenderer(EntityRendererManager renderManager) {
        super(renderManager);
        this.worldMatrix = new Matrix4f();
        this.modelMatrix = new Matrix4f();
        this.renderState = EModelRenderCycle.INITIAL;
        this.bufferSource = null;
    }

    public void addLayerRenderer(GeoLayerRenderer<T> layer) {
        this.layerRenderers.add(layer);
    }

    public void renderEntity(T animatable, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        AnimationEvent<?> event = animatable.processAnimation(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null) {
            TEntity entity = animatable.getEntity();
            boolean visible = !entity.isInvisibleToPlayer(minecraft.player);
            boolean glowing = minecraft.isEntityGlowing(entity);
            AnimatedGeoModel model = animatable.getCurrentModel();
            if (model == null) {
                super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                return;
            }
            RenderType renderType = getRenderType(animatable.getTextureLocation(), visible, glowing,
                    model.getGeoModel().isTranslucentTexture(0));
            if (renderType != null && (visible || glowing)) {
                Color color = getRenderColor(animatable, partialTick, poseStack, bufferSource, null, packedLight);
                this.worldMatrix = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
                setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
                setCurrentRTB(bufferSource);
                poseStack.push();
                poseStack.rotate(Vector3f.YP.rotationDegrees(180.0f - entityYaw));
                renderWithBone(model, animatable, partialTick, poseStack, bufferSource, null, packedLight,
                        packOverlayCoords(entity, 0.0f), color.getRed() / 255.0f, color.getGreen() / 255.0f,
                        color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
                renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, 0, null,
                        packedLight, packOverlayCoords(entity, 0.0f), color.getRed() / 255.0f, color.getGreen() / 255.0f,
                        color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
                for (GeoLayerRenderer<T> layer : this.layerRenderers) {
                    layer.render(poseStack, bufferSource, packedLight, animatable, event.getLimbSwing(), event.getLimbSwingAmount(),
                            partialTick, event.getModelData().lerpedAge, event.getModelData().rawNetHeadYaw,
                            event.getModelData().rawHeadPitch);
                }
                poseStack.pop();
            }
        }
        super.render(animatable.getEntity(), entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void renderEarly(T animatable, MatrixStack poseStack, float partialTick, IRenderTypeBuffer bufferSource,
                            IVertexBuilder buffer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        this.modelMatrix = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
        IGeoRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public static int packOverlayCoords(Entity entity, float u) {
        return OverlayTexture.getPackedUV(OverlayTexture.getU(u), OverlayTexture.getV(false));
    }

    @Override
    public void render(TEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight) {
    }

    @Override
    @NotNull
    public ResourceLocation getEntityTexture(TEntity entity) {
        return new ResourceLocation("missingno");
    }

    @Override
    @NotNull
    public IRenderCycle getCurrentModelRenderCycle() {
        return this.renderState;
    }

    @Override
    public void setCurrentModelRenderCycle(IRenderCycle cycle) {
        this.renderState = cycle;
    }

    @Override
    public void setCurrentRTB(IRenderTypeBuffer bufferSource) {
        this.bufferSource = bufferSource;
    }

    @Override
    public IRenderTypeBuffer getCurrentRTB() {
        return this.bufferSource;
    }
}
