package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.compat.slashblade.SlashBladeRenderer;
import com.elfmcys.yesstevemodel.client.compat.slashblade.SlashBladeCompat;
import com.elfmcys.yesstevemodel.client.compat.gun.swarfare.SWarfareCompat;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.IGeoRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.client.compat.gun.tacz.TacCompat;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.elfmcys.yesstevemodel.util.accessors.BufferSourceAccessor;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public class CustomPlayerHeldItemLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private final ItemRenderer itemRenderer;

    public CustomPlayerHeldItemLayer(IGeoRenderer<CustomPlayerEntity> renderer) {
        super(renderer);
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = entityLivingBaseIn.getEntity();
        AnimatedGeoModel animatedGeoModel = entityLivingBaseIn.getCurrentModel();
        if (animatedGeoModel == null) {
            return;
        }
        ItemStack offhandItem = entity.getHeldItemOffhand();
        ItemStack mainHandItem = entity.getHeldItemMainhand();
        if (!offhandItem.isEmpty() || !mainHandItem.isEmpty()) {
            poseStack.push();
            boolean useExtraPlayer = entityLivingBaseIn.isRenderLayersFirst();
            if (!animatedGeoModel.rightHandBones().isEmpty()) {
                if (SlashBladeCompat.isSlashBladeItem(mainHandItem)) {
                    SlashBladeRenderer.renderOnEntity(entity, animatedGeoModel, poseStack, bufferSource, packedLightIn, mainHandItem, partialTick);
                } else {
                    TacCompat.handleGunSound(entity, mainHandItem);
                    renderItem(animatedGeoModel, entity, mainHandItem, ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, HandSide.RIGHT, poseStack, bufferSource, packedLightIn);
                    if (useExtraPlayer && !mainHandItem.isEmpty() && (bufferSource instanceof BufferSourceAccessor)) {
                        ((BufferSourceAccessor) bufferSource).initialize();
                    }
                    TacCompat.handleItemSound(mainHandItem);
                }
            }
            if (!animatedGeoModel.leftHandBones().isEmpty()) {
                if (SlashBladeCompat.isSlashBladeItem(offhandItem)) {
                    SlashBladeRenderer.renderRightWaist(animatedGeoModel, poseStack, bufferSource, packedLightIn, offhandItem);
                } else {
                    if (!SWarfareCompat.isGunItem(offhandItem)) {
                        renderItem(animatedGeoModel, entity, offhandItem, ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, HandSide.LEFT, poseStack, bufferSource, packedLightIn);
                    }
                    if (useExtraPlayer && !offhandItem.isEmpty() && (bufferSource instanceof BufferSourceAccessor)) {
                        ((BufferSourceAccessor) bufferSource).initialize();
                    }
                }
            }
            poseStack.pop();
            TacCompat.applyItemTransform(offhandItem, animatedGeoModel, entity, poseStack, packedLightIn, partialTick);
            SWarfareCompat.applyGunTransform(offhandItem, animatedGeoModel, entity, poseStack, packedLightIn, partialTick);
        }
    }

    public void renderItem(AnimatedGeoModel model, LivingEntity livingEntity, ItemStack itemStack, ItemCameraTransforms.TransformType transformType, HandSide handSide, MatrixStack poseStack, IRenderTypeBuffer multiBufferSource, int i) {
        if (!itemStack.isEmpty()) {
            boolean z = handSide == HandSide.LEFT;
            poseStack.push();
            if (!applyItemBoneTransform(handSide, poseStack, model)) {
                poseStack.translate(0.0d, -0.0625d, -0.1d);
                poseStack.rotate(Vector3f.XP.rotationDegrees(-90.0f));
                if (SWarfareCompat.isGunItem(itemStack)) {
                    poseStack.translate(0.1d, 0.0d, 0.0d);
                    poseStack.scale(1.25f, 1.25f, 1.25f);
                }
                this.itemRenderer.renderItem(livingEntity, itemStack, transformType, z, poseStack, multiBufferSource, livingEntity.world, i, OverlayTexture.NO_OVERLAY);
            }
            poseStack.pop();
            (z ? model.rightHandChain() : model.leftHandChains()).forEach(list -> {
                poseStack.push();
                if (!RenderUtils.prepMatrixForLocator(poseStack, list)) {
                    poseStack.translate(0.0d, -0.0625d, -0.1d);
                    poseStack.rotate(Vector3f.XP.rotationDegrees(-90.0f));
                    if (SWarfareCompat.isGunItem(itemStack)) {
                        poseStack.scale(1.25f, 1.25f, 1.25f);
                    }
                    this.itemRenderer.renderItem(livingEntity, itemStack, transformType, z, poseStack, multiBufferSource, livingEntity.world, i, OverlayTexture.NO_OVERLAY);
                }
                poseStack.pop();
            });
        }
    }

    public boolean applyItemBoneTransform(HandSide handSide, MatrixStack poseStack, AnimatedGeoModel model) {
        if (handSide == HandSide.LEFT) {
            return RenderUtils.prepMatrixForLocator(poseStack, model.leftHandBones());
        }
        return RenderUtils.prepMatrixForLocator(poseStack, model.rightHandBones());
    }
}
