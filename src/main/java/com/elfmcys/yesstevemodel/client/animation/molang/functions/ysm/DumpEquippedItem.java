package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

public class DumpEquippedItem extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlotType slot;
        ResourceLocation key;
        if (!context.entity().isDebugMode() || (slot = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0))) == null) {
            return null;
        }
        ItemStack stack = CosmeticArmorHelper.getArmorItem(context.entity().entity(), slot);
        if (stack.isEmpty() || (key = Registry.ITEM.getKey(stack.getItem())) == null) {
            return null;
        }
        context.entity().logWarningComponent(new StringTextComponent("Display ").append(copyOnClickText(stack.getDisplayName().getStringTruncated(99))));
        context.entity().logWarningComponent(new StringTextComponent("Name ").append(copyOnClickText(key.toString())));
        ItemTags.getCollection().getOwningTags(stack.getItem()).forEach(tagId -> {
            context.entity().logWarningComponent(new StringTextComponent("Tag ").append(copyOnClickText(tagId.toString())));
        });
        for (INBT inbt : stack.getEnchantmentTagList()) {
            if (inbt instanceof CompoundNBT) {
                CompoundNBT compoundTag = (CompoundNBT) inbt;
                ResourceLocation enchantmentId = ResourceLocation.tryCreate(compoundTag.getString("id"));
                if (enchantmentId != null) {
                    Enchantment enchantment = Registry.ENCHANTMENT.getOptional(enchantmentId).orElse(null);
                    if (enchantment != null) {
                        context.entity().logWarningComponent(new StringTextComponent("Enchantment: display ")
                                .append(copyOnClickText(enchantment.getDisplayName(compoundTag.getInt("lvl")).getStringTruncated(99)))
                                .append(new StringTextComponent("  name "))
                                .append(copyOnClickText(enchantmentId.toString())));
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

    private static ITextComponent copyOnClickText(String text) {
        return new StringTextComponent(text).setStyle(Style.EMPTY
                .setInsertion(text)
                .setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Click to copy"))));
    }
}
