package com.elfmcys.yesstevemodel.geckolib3.extended;

import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.entity.LivingEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

public interface LivingEntityRendererAccessor {
    default void setupRotations(LivingEntity entity, MatrixStack matrixStack, float ageInTicks, float rotationYaw, float partialTicks) {}
}