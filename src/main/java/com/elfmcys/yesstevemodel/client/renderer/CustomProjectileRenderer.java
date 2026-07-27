package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ProjectileCapabilityProvider;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.entity.projectile.ProjectileEntity;

public class CustomProjectileRenderer {
    public static boolean renderProjectile(ProjectileEntity ProjectileEntity, float entityYaw, float f2, MatrixStack poseStack, IRenderTypeBuffer multiBufferSource, int i) {
        return ProjectileEntity.getCapability(ProjectileCapabilityProvider.PROJECTILE_CAP).map(cap -> {
            if (cap.isModelInitialized() && cap.isModelReady()) {
                RendererManager.getProjectileRenderer().render(cap, entityYaw, f2, poseStack, multiBufferSource, i);
                return false;
            }
            return true;
        }).orElse(true);
    }
}