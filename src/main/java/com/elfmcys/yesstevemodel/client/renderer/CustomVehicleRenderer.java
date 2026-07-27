package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.VehicleCapabilityProvider;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.util.math.vector.Vector3d;

public class CustomVehicleRenderer {
    public static boolean renderVehicle(Entity entity, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        return entity.getCapability(VehicleCapabilityProvider.VEHICLE_CAP).map(cap -> {
            if (cap.isModelInitialized() && cap.isModelReady()) {
                RendererManager.getVehicleRenderer().renderEntity(cap, getBodyRotation(entity, entityYaw, partialTick), partialTick, poseStack, bufferSource, packedLight);
                return false;
            }
            return true;
        }).orElse(true);
    }

    public static float getBodyRotation(Entity entity, float entityYaw, float partialTick) {
        float bodyRotation = entityYaw;
        if (entity instanceof LivingEntity) {
            bodyRotation = getLivingBodyRotation((LivingEntity) entity, partialTick);
        } else if (entity instanceof AbstractMinecartEntity) {
            bodyRotation = getMinecartBodyRotation((AbstractMinecartEntity) entity, partialTick, bodyRotation);
        }
        return bodyRotation;
    }

    private static float getLivingBodyRotation(LivingEntity entity, float partialTick) {
        float bodyYaw = MathHelper.interpolateAngle(partialTick, entity.prevRenderYawOffset, entity.renderYawOffset);
        float headYaw = MathHelper.interpolateAngle(partialTick, entity.prevRotationYawHead, entity.rotationYawHead);

        if (entity.isPassenger() && entity.getRidingEntity() != null && entity.getRidingEntity() instanceof LivingEntity) {
            LivingEntity livingVehicle = (LivingEntity) entity.getRidingEntity();
            float vehicleBodyYaw = MathHelper.interpolateAngle(partialTick, livingVehicle.prevRenderYawOffset, livingVehicle.renderYawOffset);
            float yawDiff = MathHelper.clamp(MathHelper.wrapDegrees(headYaw - vehicleBodyYaw), -85.0f, 85.0f);
            bodyYaw = headYaw - yawDiff;

            if (yawDiff * yawDiff > 2500.0f) {
                bodyYaw += yawDiff * 0.2f;
            }
        }
        return bodyYaw;
    }

    private static float getMinecartBodyRotation(AbstractMinecartEntity minecart, float partialTick, float defaultYaw) {
        double interpX = MathHelper.lerp(partialTick, minecart.prevPosX, minecart.getPosX());
        double interpY = MathHelper.lerp(partialTick, minecart.prevPosY, minecart.getPosY());
        double interpZ = MathHelper.lerp(partialTick, minecart.prevPosZ, minecart.getPosZ());

        float calculatedYaw = defaultYaw;

        double deltaX = interpX - minecart.prevPosX;
        double deltaZ = interpZ - minecart.prevPosZ;
        if (deltaX * deltaX + deltaZ * deltaZ > 0.001) {
            calculatedYaw = (float) (MathHelper.atan2(deltaZ, deltaX) * 180.0 / Math.PI);
        }

        return calculatedYaw;
    }
}