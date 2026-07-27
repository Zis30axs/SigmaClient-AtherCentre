package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.compat.sbackpack.SBackpackCompat;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.IGeoRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Vector3f;

public class SophisticatedBackpackLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    public SophisticatedBackpackLayer(IGeoRenderer<CustomPlayerEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        PlayerEntity player;
        ItemStack stack;
        AnimatedGeoModel model = entityLivingBaseIn.getCurrentModel();
        if (model != null && !model.backpackBones().isEmpty() && (stack = SBackpackCompat.getBackpackItem((player = entityLivingBaseIn.getEntity()))) != null) {
            poseStack.push();
            renderBackpack(poseStack, model);
            poseStack.rotate(Vector3f.XP.rotationDegrees(180.0f));
            poseStack.rotate(Vector3f.YP.rotationDegrees(180.0f));
            poseStack.translate(0.0d, -0.1d, 0.0d);
            // PORT-REVIEW: SophisticatedBackpacks mod not available in standalone client
            poseStack.pop();
        }
    }

    public void renderBackpack(MatrixStack poseStack, AnimatedGeoModel model) {
        RenderUtils.prepMatrixForLocator(poseStack, model.backpackBones());
    }
}