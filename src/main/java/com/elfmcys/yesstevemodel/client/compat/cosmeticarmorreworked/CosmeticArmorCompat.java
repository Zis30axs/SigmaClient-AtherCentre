package com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked;

import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CosmeticArmorCompat {
    public static boolean isLoaded() { return false; }
    @Nullable
    public static ItemStack getCosmeticItem(LivingEntity entity, EquipmentSlotType slot) { return null; }
}