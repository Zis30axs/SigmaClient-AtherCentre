package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.VehicleCapabilityProvider;
import com.elfmcys.yesstevemodel.client.entity.GeckoVehicleEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoEntityRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class VehicleRenderer extends GeoEntityRenderer<Entity, GeckoVehicleEntity> {
    public VehicleRenderer(EntityRendererManager renderManager) {
        super(renderManager);
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        if (Minecraft.getInstance().player == null || entity.isInvisibleToPlayer(Minecraft.getInstance().player)) {
            return;
        }
        entity.getCapability(VehicleCapabilityProvider.VEHICLE_CAP).ifPresent(cap -> {
            cap.tickModel();
            renderEntity(cap, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        });
    }

    @NotNull
    public ResourceLocation getTextureLocation(Entity entity) {
        return entity.getCapability(VehicleCapabilityProvider.VEHICLE_CAP).map(cap -> cap.getTextureLocation()).orElse(new ResourceLocation("missingno"));
    }

    @Override
    @NotNull
    public ResourceLocation getEntityTexture(Entity entity) {
        return getTextureLocation(entity);
    }
}