package com.elfmcys.yesstevemodel.client.compat.slashblade;

import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SlashBladeCompat {
    public static boolean isSlashBlade(Object stack) { return false; }
    public static boolean isSlashBladeItem(ItemStack stack) { return false; }

    @Nullable
    public static String getComboAnimName(AnimationEvent<? extends LivingAnimatable<?>> event) { return null; }

    @Nullable
    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String str, ILoopType loopType) { return null; }
}
