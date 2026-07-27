package com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl;

import com.elfmcys.yesstevemodel.client.animation.condition.InnerClassify;
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
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class HandRenderFunction extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String TYPE_PREFIX = ":";

    private static final String EMPTY_ITEM = "empty";

    private static final int RESULT_FALSE = 0;

    private static final int RESULT_TRUE = 1;

    private final HandItemPredicate handItemPredicate;

    private interface HandItemPredicate {
        boolean test(LivingEntity livingEntity, Hand hand);
    }

    private HandRenderFunction(HandItemPredicate predicate) {
        this.handItemPredicate = predicate;
    }

    public static HandRenderFunction createAlways() {
        return new HandRenderFunction((entity, hand) -> true);
    }

    public static HandRenderFunction createWhenSwinging() {
        return new HandRenderFunction((entity, hand) -> entity.isSwingInProgress && !entity.isSleeping());
    }

    public static HandRenderFunction createWhenUsing() {
        return new HandRenderFunction((entity, hand) -> entity.isHandActive() && !entity.isSleeping());
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlotType slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null || slotType.getSlotType() == EquipmentSlotType.Group.ARMOR) {
            return RESULT_FALSE;
        }
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return RESULT_FALSE;
        }
        ItemStack itemBySlot = entity.getItemStackFromSlot(slotType);
        if (!this.handItemPredicate.test(entity, slotType == EquipmentSlotType.OFFHAND ? Hand.OFF_HAND : Hand.MAIN_HAND)) {
            return RESULT_FALSE;
        }
        if (itemBySlot.isEmpty() && id.equals(EMPTY_ITEM)) {
            return RESULT_TRUE;
        }
        String strSubstring = id.substring(1);
        if (id.startsWith(PREFIX_ITEM_ID)) {
            ResourceLocation key = Registry.ITEM.getKey(itemBySlot.getItem());
            if (key == null) {
                return RESULT_FALSE;
            }
            return strSubstring.equals(key.toString()) ? RESULT_TRUE : RESULT_FALSE;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            ITag<Item> tag = ItemTags.getCollection().get(new ResourceLocation(strSubstring));
            return tag != null && itemBySlot.getItem().isIn(tag) ? RESULT_TRUE : RESULT_FALSE;
        }
        if (id.startsWith(TYPE_PREFIX)) {
            String itemType = InnerClassify.getItemType(itemBySlot);
            if ((!StringUtils.isNotBlank(itemType) || !itemType.equals(strSubstring)) && !itemBySlot.getUseAction().name().toLowerCase(Locale.ENGLISH).equals(strSubstring)) {
                return RESULT_FALSE;
            }
            return RESULT_TRUE;
        }
        return RESULT_FALSE;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}
