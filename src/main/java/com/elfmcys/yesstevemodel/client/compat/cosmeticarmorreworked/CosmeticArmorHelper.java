package com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked;

import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class CosmeticArmorHelper {
    public static ItemStack getArmorItem(LivingEntity entity, EquipmentSlotType slot) {
        return entity.getItemStackFromSlot(slot);
    }

    public static ItemStack getElytraItem(LivingEntity entity) {
        return entity.getItemStackFromSlot(EquipmentSlotType.CHEST);
    }
}
