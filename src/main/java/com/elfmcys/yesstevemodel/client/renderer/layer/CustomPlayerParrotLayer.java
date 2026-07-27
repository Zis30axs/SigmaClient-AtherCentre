package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.IGeoRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.model.ParrotModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

public class CustomPlayerParrotLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private static final String TAG_ID = "id";
    private static final String TAG_VARIANT = "Variant";
    private static final ResourceLocation[] PARROT_TEXTURES = new ResourceLocation[]{
        new ResourceLocation("textures/entity/parrot/parrot_red_blue.png"),
        new ResourceLocation("textures/entity/parrot/parrot_blue.png"),
        new ResourceLocation("textures/entity/parrot/parrot_green.png"),
        new ResourceLocation("textures/entity/parrot/parrot_yellow_blue.png"),
        new ResourceLocation("textures/entity/parrot/parrot_grey.png")
    };

    private final ParrotModel parrotModel;

    public CustomPlayerParrotLayer(IGeoRenderer<CustomPlayerEntity> renderer) {
        super(renderer);
        this.parrotModel = new ParrotModel();
    }

    @Override
    public void render(MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        PlayerEntity player = entityLivingBaseIn.getEntity();
        AnimatedGeoModel model = entityLivingBaseIn.getCurrentModel();
        if (model == null) return;
        if (!model.leftShoulderBones().isEmpty()) {
            renderParrot(poseStack, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, true);
        }
        if (!model.rightShoulderBones().isEmpty()) {
            renderParrot(poseStack, bufferSource, model, packedLightIn, player, limbSwing, limbSwingAmount, netHeadYaw, headPitch, false);
        }
    }

    private void renderParrot(MatrixStack poseStack, IRenderTypeBuffer bufferSource, AnimatedGeoModel model, int packedLightIn, PlayerEntity player, float limbSwing, float limbSwingAmount, float netHeadYaw, float headPitch, boolean isLeftShoulder) {
        CompoundNBT shoulderEntity = isLeftShoulder ? player.getLeftShoulderEntity() : player.getRightShoulderEntity();
        EntityType.byKey(shoulderEntity.getString(TAG_ID)).filter(entityType -> entityType == EntityType.PARROT).ifPresent(entityType -> {
            poseStack.push();
            applyParrotTransform(poseStack, model, isLeftShoulder);
            poseStack.translate(0.0d, 1.5d, 0.0d);
            poseStack.rotate(Vector3f.ZP.rotationDegrees(180.0f));
            int variant = shoulderEntity.getInt(TAG_VARIANT);
            ResourceLocation texture = PARROT_TEXTURES[variant % PARROT_TEXTURES.length];
            IVertexBuilder parrotBuffer = bufferSource.getBuffer(this.parrotModel.getRenderType(texture));
            this.parrotModel.renderOnShoulder(poseStack, parrotBuffer, packedLightIn, OverlayTexture.NO_OVERLAY, limbSwing, limbSwingAmount, netHeadYaw, headPitch, player.ticksExisted);
            poseStack.pop();
        });
    }

    public void applyParrotTransform(MatrixStack poseStack, AnimatedGeoModel model, boolean isLeftShoulder) {
        if (isLeftShoulder) {
            RenderUtils.prepMatrixForLocator(poseStack, model.leftShoulderBones());
        } else {
            RenderUtils.prepMatrixForLocator(poseStack, model.rightShoulderBones());
        }
    }
}
