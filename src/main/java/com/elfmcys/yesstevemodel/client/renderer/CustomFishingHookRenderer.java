package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ProjectileCapabilityProvider;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;

public class CustomFishingHookRenderer {

    public static boolean tryRenderCustomHook(FishingBobberEntity hook, float entityYaw, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight) {
        return hook.getCapability(ProjectileCapabilityProvider.PROJECTILE_CAP).map(cap -> {
            if (cap.isModelReady()) {
                hook.rotationPitch = 0.0f;
                hook.prevRotationPitch = 0.0f;
                RendererManager.getProjectileRenderer().render(hook, entityYaw, partialTick, poseStack, bufferSource, packedLight);
                PlayerEntity playerOwner = hook.func_234606_i_();
                if (playerOwner != null) {
                    poseStack.push();
                    renderFishingLine(hook, partialTick, poseStack, bufferSource, playerOwner);
                    poseStack.pop();
                }
                return false;
            }
            return true;
        }).orElse(true);
    }

    private static void renderFishingLine(FishingBobberEntity hook, float partialTick, MatrixStack poseStack, IRenderTypeBuffer bufferSource, PlayerEntity player) {
        int hand = player.getPrimaryHand() == HandSide.RIGHT ? 1 : -1;
        ItemStack itemstack = player.getHeldItemMainhand();
        if (itemstack.getItem() != Items.FISHING_ROD) {
            hand = -hand;
        }
        float swingProgress = player.getSwingProgress(partialTick);
        float swingProgressSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float yawOffset = MathHelper.lerp(partialTick, player.prevRenderYawOffset, player.renderYawOffset) * ((float) Math.PI / 180F);
        double dSin = (double) MathHelper.sin(yawOffset);
        double dCos = (double) MathHelper.cos(yawOffset);
        double handOffset = (double) hand * 0.35D;
        double anglerX;
        double anglerY;
        double anglerZ;
        float anglerEye;
        EntityRendererManager renderManager = Minecraft.getInstance().getRenderManager();
        if ((renderManager.options == null || renderManager.options.getPointOfView().func_243192_a()) && player == Minecraft.getInstance().player) {
            double fovScale = renderManager.options.fov;
            fovScale = fovScale / 100.0D;
            Vector3d vector3d = new Vector3d((double) hand * -0.36D * fovScale, -0.045D * fovScale, 0.4D);
            vector3d = vector3d.rotatePitch(-MathHelper.lerp(partialTick, player.prevRotationPitch, player.rotationPitch) * ((float) Math.PI / 180F));
            vector3d = vector3d.rotateYaw(-MathHelper.lerp(partialTick, player.prevRotationYaw, player.rotationYaw) * ((float) Math.PI / 180F));
            vector3d = vector3d.rotateYaw(swingProgressSqrt * 0.5F);
            vector3d = vector3d.rotatePitch(-swingProgressSqrt * 0.7F);
            anglerX = MathHelper.lerp((double) partialTick, player.prevPosX, player.getPosX()) + vector3d.x;
            anglerY = MathHelper.lerp((double) partialTick, player.prevPosY, player.getPosY()) + vector3d.y;
            anglerZ = MathHelper.lerp((double) partialTick, player.prevPosZ, player.getPosZ()) + vector3d.z;
            anglerEye = player.getEyeHeight();
        } else {
            anglerX = MathHelper.lerp((double) partialTick, player.prevPosX, player.getPosX()) - dCos * handOffset - dSin * 0.8D;
            anglerY = player.prevPosY + (double) player.getEyeHeight() + (player.getPosY() - player.prevPosY) * (double) partialTick - 0.45D;
            anglerZ = MathHelper.lerp((double) partialTick, player.prevPosZ, player.getPosZ()) - dSin * handOffset + dCos * 0.8D;
            anglerEye = player.isCrouching() ? -0.1875F : 0.0F;
        }
        double hookX = MathHelper.lerp((double) partialTick, hook.prevPosX, hook.getPosX());
        double hookY = MathHelper.lerp((double) partialTick, hook.prevPosY, hook.getPosY()) + 0.25D;
        double hookZ = MathHelper.lerp((double) partialTick, hook.prevPosZ, hook.getPosZ());
        float startX = (float) (anglerX - hookX);
        float startY = (float) (anglerY - hookY) + anglerEye;
        float startZ = (float) (anglerZ - hookZ);
        IVertexBuilder buffer = bufferSource.getBuffer(RenderType.getLines());
        Matrix4f matrix = poseStack.getLast().getMatrix();
        for (int k = 0; k < 16; ++k) {
            stringVertex(startX, startY, startZ, buffer, matrix, fraction(k, 16));
            stringVertex(startX, startY, startZ, buffer, matrix, fraction(k + 1, 16));
        }
    }

    private static float fraction(int numerator, int denominator) {
        return (float) numerator / (float) denominator;
    }

    private static void stringVertex(float x, float y, float z, IVertexBuilder buffer, Matrix4f matrix, float fraction) {
        buffer.pos(matrix, x * fraction, y * (fraction * fraction + fraction) * 0.5F + 0.25F, z * fraction).color(0, 0, 0, 255).endVertex();
    }
}
