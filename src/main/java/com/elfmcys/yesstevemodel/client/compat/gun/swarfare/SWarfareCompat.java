package com.elfmcys.yesstevemodel.client.compat.gun.swarfare;

import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SWarfareCompat {
    public static boolean isHoldingGun(Object entity) { return false; }
    public static boolean isPlayerAiming(PlayerEntity player) { return false; }
    public static boolean isGunItem(ItemStack stack) { return false; }
    public static void applyGunTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, MatrixStack poseStack, int packedLight, float partialTick) { }

    @Nullable
    public static PlayState handleTaczAnim(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event, String str, ILoopType loopType) { return null; }

    @Nullable
    public static PlayState handleGunHoldAnim(ItemStack stack, AnimationEvent<? extends LivingAnimatable<? extends LivingEntity>> event) { return null; }
}
