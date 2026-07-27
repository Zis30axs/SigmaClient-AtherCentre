package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.capability.VehicleCapabilityProvider;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.model.provider.data.EntityModelData;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.JomlMatrix4fBridge;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Faithful port of the upstream renderer. Structural notes for 1.16.5:
 * <ul>
 *   <li>{@code LivingEntityRenderer} -&gt; {@link LivingRenderer}; the dummy {@code PlayerModel} exists
 *       only so the vanilla base has a model - it is never rendered.</li>
 *   <li>Forge {@code RenderLivingEvent} Pre/Post posts are dropped (no event bus).</li>
 *   <li>{@code LivingEntityAccessor#invokeSetLivingEntityFlag} -&gt;
 *       {@code LivingEntity#setLivingFlagForYsmRender} (decompiled-source getter).</li>
 *   <li>{@code getLastClimbablePos()} does not exist in 1.16.5; the ladder facing is read from the
 *       block at the entity's feet instead.</li>
 * </ul>
 */
public abstract class GeoReplacedEntityRenderer<TEntity extends LivingEntity, T extends LivingAnimatable<TEntity>>
        extends LivingRenderer<TEntity, PlayerModel<TEntity>> implements IGeoRenderer<T> {

    public final List<GeoLayerRenderer<T>> layerRenderers = new ObjectArrayList<>();

    public Matrix4f dispatchedMat = new Matrix4f();

    public Matrix4f renderEarlyMat = new Matrix4f();

    public IRenderTypeBuffer rtb;

    private IRenderCycle currentModelRenderCycle = EModelRenderCycle.INITIAL;

    protected GeoReplacedEntityRenderer(EntityRendererManager renderManager) {
        super(renderManager, new PlayerModel<>(0.0f, true), 0.5f);
        this.rtb = null;
    }

    public static int packOverlayCoords(LivingEntity entity, float u) {
        return OverlayTexture.getPackedUV(OverlayTexture.getU(u), OverlayTexture.getV(entity.hurtTime > 0 || entity.deathTime > 0));
    }

    @Override
    @NotNull
    public IRenderCycle getCurrentModelRenderCycle() {
        return this.currentModelRenderCycle;
    }

    @Override
    public void setCurrentModelRenderCycle(IRenderCycle cycle) {
        this.currentModelRenderCycle = cycle;
    }

    @Override
    public void renderEarly(T animatable, MatrixStack poseStack, float partialTick, IRenderTypeBuffer bufferSource,
                            IVertexBuilder buffer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        this.renderEarlyMat = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
        IGeoRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    public void renderEntity(T t, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        renderEntityWithTexture(t, null, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    public void renderEntityWithTexture(T t, @Nullable ResourceLocation resourceLocation, float entityYaw, float partialTick,
                                        MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        AnimationEvent<?> event = t.processAnimation(partialTick);
        TEntity entity = t.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (event != null && minecraft.player != null && t.getCurrentModel() != null) {
            com.elfmcys.yesstevemodel.util.log.AnimationStateDebug.dumpLocalPlayer(t);
            EntityModelData modelData = event.getModelData();
            this.dispatchedMat = JomlMatrix4fBridge.fromVanilla(poseStack.getLast().getMatrix());
            setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);
            poseStack.push();
            if (entity.getPose() == Pose.SLEEPING) {
                Direction bedOrientation = entity.getBedDirection();
                if (bedOrientation != null) {
                    float eyeHeight = entity.getEyeHeight(Pose.STANDING) - 0.1f;
                    poseStack.translate((-bedOrientation.getXOffset()) * eyeHeight, 0.0f, (-bedOrientation.getZOffset()) * eyeHeight);
                }
            }
            setupRotations(entity, poseStack, modelData.lerpedAge, modelData.lerpBodyRot, partialTick);
            Entity vehicle = entity.getRidingEntity();
            if (vehicle != null) {
                vehicle.getCapability(VehicleCapabilityProvider.VEHICLE_CAP).ifPresent(cap -> {
                    org.joml.Vector3f offset = cap.getExpressionOffset();
                    if (offset != null) {
                        // Upstream: mulPose(new Quaternionf().rotateZYX(z, 0, x).invert()).
                        // inverse(Rz(z) * Rx(x)) == Rx(-x) * Rz(-z), and MatrixStack#rotate post-multiplies.
                        poseStack.rotate(Vector3f.XP.rotation(-offset.x));
                        poseStack.rotate(Vector3f.ZP.rotation(-offset.z));
                    }
                });
            }
            preRenderCallback(entity, poseStack, partialTick);
            poseStack.translate(0.0f, 0.01f, 0.0f);
            AnimatedGeoModel animatedGeoModel = t.getCurrentModel();
            int textureIndex = resourceLocation == null ? t.getTextureIndex() : 0;
            RenderType renderType = getRenderType(resourceLocation == null ? t.getTextureLocation() : resourceLocation,
                    isVisible(entity) && !entity.isInvisibleToPlayer(minecraft.player),
                    minecraft.isEntityGlowing(entity),
                    animatedGeoModel.getGeoModel().isTranslucentTexture(textureIndex));
            boolean useExtraPlayer = t.isRenderLayersFirst();
            Color color = getRenderColor(t, partialTick, poseStack, bufferSource, null, packedLight);
            int packedOverlay = packOverlayCoords(entity, getHurtOverlayProgress(entity, partialTick));
            renderWithBone(animatedGeoModel, t, partialTick, poseStack, bufferSource, null, packedLight, packedOverlay,
                    color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
            if (useExtraPlayer && !entity.isSpectator()) {
                render(t, partialTick, poseStack, bufferSource, packedLight, event, modelData);
            }
            if (renderType != null) {
                renderWithBoneAndRenderType(animatedGeoModel, t, partialTick, renderType, poseStack, bufferSource, textureIndex,
                        null, packedLight, packedOverlay, color.getRed() / 255.0f, color.getGreen() / 255.0f,
                        color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
            }
            if (!useExtraPlayer && !entity.isSpectator()) {
                render(t, partialTick, poseStack, bufferSource, packedLight, event, modelData);
            }
            poseStack.pop();
        }
    }

    public void render(T entity, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLightIn,
                       AnimationEvent<?> event, EntityModelData data) {
        for (GeoLayerRenderer<T> layerRenderer : this.layerRenderers) {
            layerRenderer.render(poseStack, bufferSource, packedLightIn, entity, event.getLimbSwing(), event.getLimbSwingAmount(),
                    partialTick, data.lerpedAge, data.rawNetHeadYaw, data.rawHeadPitch);
        }
    }

    public float getHurtOverlayProgress(TEntity entity, float partialTick) {
        return 0.0f;
    }

    public void preRenderCallback(TEntity entity, MatrixStack poseStack, float partialTick) {
    }

    public void setupRotations(TEntity entity, MatrixStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        // Upstream suppresses the vanilla death tilt and spin-attack tilt so YSM can drive both via
        // its own animations, then restores the entity state afterwards.
        int deathTime = entity.deathTime;
        boolean spinAttack = entity.isSpinAttacking();
        if (deathTime > 0) {
            entity.deathTime = 0;
        }
        if (spinAttack) {
            entity.setLivingFlagForYsmRender(4, false);
        }
        if (entity.isOnLadder()) {
            BlockState climbedState = entity.world.getBlockState(entity.getPosition());
            if (climbedState.hasProperty(HorizontalBlock.HORIZONTAL_FACING)) {
                Direction climbedFacing = climbedState.get(HorizontalBlock.HORIZONTAL_FACING);
                rotationYaw = climbedFacing.getOpposite().getHorizontalIndex() * 90;
            }
        }
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (deathTime > 0) {
            entity.deathTime = deathTime;
        }
        if (spinAttack) {
            entity.setLivingFlagForYsmRender(4, true);
        }
    }

    /** MCP name for upstream's {@code shouldShowName}. */
    @Override
    public boolean canRenderName(TEntity entity) {
        double d = entity.isSneaking() ? 32.0d : 64.0d;
        return this.renderManager.getDistanceToCamera(entity.getPosX(), entity.getPosY(), entity.getPosZ()) < d * d
                && entity == this.renderManager.pointedEntity && entity.hasCustomName() && Minecraft.isGuiEnabled();
    }

    public void renderNameTag(TEntity entity, ITextComponent displayName, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        super.renderName(entity, displayName, poseStack, bufferSource, packedLight);
    }

    @Override
    public void render(TEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight) {
    }

    @Override
    @NotNull
    public ResourceLocation getEntityTexture(TEntity entity) {
        return new ResourceLocation("missingno");
    }

    public final boolean addLayerRenderer(GeoLayerRenderer<T> layerRenderer) {
        return this.layerRenderers.add(layerRenderer);
    }

    @Override
    public IRenderTypeBuffer getCurrentRTB() {
        return this.rtb;
    }

    @Override
    public void setCurrentRTB(IRenderTypeBuffer bufferSource) {
        this.rtb = bufferSource;
    }
}
