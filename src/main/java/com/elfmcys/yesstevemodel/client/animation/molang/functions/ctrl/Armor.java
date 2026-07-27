package com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.StringUtils;

public class Armor extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String EMPTY_ITEM = "empty";

    public static Armor create() {
        return new Armor();
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlotType slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null || slotType.getSlotType() != EquipmentSlotType.Group.ARMOR) {
            return null;
        }
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        ItemStack itemBySlot = entity.getItemStackFromSlot(slotType);
        if (itemBySlot.isEmpty() && id.equals(EMPTY_ITEM)) {
            return 1;
        }
        String strSubstring = id.substring(1);
        if (id.startsWith(PREFIX_ITEM_ID)) {
            ResourceLocation key = Registry.ITEM.getKey(itemBySlot.getItem());
            if (key == null) {
                return 0;
            }
            return strSubstring.equals(key.toString()) ? 1 : 0;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            ITag<Item> tag = ItemTags.getCollection().get(new ResourceLocation(strSubstring));
            return tag != null && itemBySlot.getItem().isIn(tag) ? 1 : 0;
        }
        return 0;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}
