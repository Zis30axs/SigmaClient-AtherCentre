package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

public class EquippedEnchantmentLevel extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlotType slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null) {
            return null;
        }
        ItemStack stack = CosmeticArmorHelper.getArmorItem(context.entity().entity(), slotType);
        if (stack.isEmpty()) {
            return 0;
        }
        int enchantmentLevel = 0;
        for (int i = 1; i < arguments.size(); i++) {
            ResourceLocation id = arguments.getResourceLocation(context, 1);
            if (id != null) {
                Enchantment enchantment = Registry.ENCHANTMENT.getOptional(id).orElse(null);
                if (enchantment != null) {
                    enchantmentLevel += EnchantmentHelper.getEnchantmentLevel(enchantment, stack);
                }
            }
        }
        return enchantmentLevel;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
