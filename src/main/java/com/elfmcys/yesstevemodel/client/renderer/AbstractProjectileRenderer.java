package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.IGeoRenderer;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public abstract class AbstractProjectileRenderer<TEntity extends ProjectileEntity, T extends AnimatableEntity<TEntity>> extends EntityRenderer<TEntity> implements IGeoRenderer<T> {

    public Matrix4f modelViewMatrix;
    public Matrix4f projectionMatrix;
    private IRenderCycle renderState;
    public IRenderTypeBuffer bufferSource;

    public AbstractProjectileRenderer(EntityRendererManager renderManager) {
        super(renderManager);
        this.modelViewMatrix = new Matrix4f();
        this.projectionMatrix = new Matrix4f();
        this.renderState = EModelRenderCycle.INITIAL;
        this.bufferSource = null;
    }

    public void render(T animatable, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        AnimationEvent<?> event = animatable.processAnimation(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null) {
            ProjectileEntity projectile = animatable.getEntity();
            boolean visible = !projectile.isInvisibleToPlayer(minecraft.player);
            boolean glowing = minecraft.isEntityGlowing(projectile);
            RenderType renderType = getRenderType(animatable.getTextureLocation(), visible, glowing, animatable.getCurrentModel() != null && animatable.getCurrentModel().getGeoModel().isTranslucentTexture(0));
            if (renderType != null && (visible || glowing)) {
                Color color = getRenderColor(animatable, partialTick, poseStack, bufferSource, null, packedLight);
                AnimatedGeoModel model = animatable.getCurrentModel();
                this.modelViewMatrix = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
                setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
                poseStack.push();
                poseStack.rotate(Vector3f.YP.rotationDegrees(MathHelper.lerp(partialTick, projectile.prevRotationYaw, projectile.rotationYaw) - 90.0f));
                poseStack.rotate(Vector3f.ZP.rotationDegrees(MathHelper.lerp(partialTick, projectile.prevRotationPitch, projectile.rotationPitch)));
                renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, 0, null, packedLight, getPackedOverlay(projectile, 0.0f), color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
                poseStack.pop();
            }
        }
        super.render(animatable.getEntity(), entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void renderEarly(T animatable, MatrixStack poseStack, float partialTick, IRenderTypeBuffer bufferSource, IVertexBuilder buffer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        this.projectionMatrix = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
        IGeoRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public static int getPackedOverlay(Entity entity, float u) {
        return OverlayTexture.getPackedUV(OverlayTexture.getU(u), OverlayTexture.getV(false));
    }

    @NotNull
    public IRenderCycle getCurrentModelRenderCycle() {
        return this.renderState;
    }

    public void setCurrentModelRenderCycle(IRenderCycle cycle) {
        this.renderState = cycle;
    }

    public void setCurrentRTB(IRenderTypeBuffer bufferSource) {
        this.bufferSource = bufferSource;
    }

    @Override
    public IRenderTypeBuffer getCurrentRTB() {
        return this.bufferSource;
    }

    @Override
    @NotNull
    public ResourceLocation getEntityTexture(TEntity entity) {
        return new ResourceLocation("missingno");
    }

}