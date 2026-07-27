package com.elfmcys.yesstevemodel.client.compat.gun.tacz;

import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TacCompat {
    public static boolean isHoldingGun(Object entity) { return false; }
    public static void handleGunSound(LivingEntity entity, ItemStack stack) { }
    public static void handleItemSound(ItemStack stack) { }
    public static void applyItemTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, MatrixStack poseStack, int packedLight, float partialTick) { }

    @Nullable
    public static PlayState handleTaczAnimState(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<?>> event, String animation, ILoopType loopType) { return null; }

    @Nullable
    public static PlayState handleGunHoldAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) { return null; }
}
