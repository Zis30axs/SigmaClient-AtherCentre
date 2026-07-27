package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ProjectileCapabilityProvider;
import com.elfmcys.yesstevemodel.client.entity.GeckoProjectileEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.jetbrains.annotations.NotNull;

public class ProjectileRenderer extends AbstractProjectileRenderer<ProjectileEntity, GeckoProjectileEntity> {
    public ProjectileRenderer(EntityRendererManager renderManager) {
        super(renderManager);
    }

    @Override
    public void render(ProjectileEntity projectile, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        if (Minecraft.getInstance().player == null || projectile.isInvisibleToPlayer(Minecraft.getInstance().player)) {
            return;
        }
        projectile.getCapability(ProjectileCapabilityProvider.PROJECTILE_CAP).ifPresent(cap -> {
            cap.tickModel();
            render(cap, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        });
    }

    @NotNull
    public ResourceLocation getTextureLocation(ProjectileEntity projectile) {
        return projectile.getCapability(ProjectileCapabilityProvider.PROJECTILE_CAP).map(cap -> cap.getTextureLocation()).orElse(new ResourceLocation("missingno"));
    }

    @Override
    @NotNull
    public ResourceLocation getEntityTexture(ProjectileEntity entity) {
        return getTextureLocation(entity);
    }
}