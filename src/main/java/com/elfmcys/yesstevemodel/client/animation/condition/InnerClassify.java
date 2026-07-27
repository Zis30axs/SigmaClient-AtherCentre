package com.elfmcys.yesstevemodel.client.animation.condition;

import com.elfmcys.yesstevemodel.util.ItemTagsConstants;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;

public final class InnerClassify {
    private static final String EMPTY = "";

    private InnerClassify() {
    }

    public static String doClassifyTest(String prefix, LivingEntity entity, Hand hand) {
        String itemType = getItemType(entity.getHeldItem(hand));
        return itemType.isEmpty() ? EMPTY : prefix + itemType;
    }

    public static String getItemType(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SwordItem || isInTag(item, ItemTagsConstants.SWORDS)) {
            return "sword";
        }
        if (item instanceof AxeItem || isInTag(item, ItemTagsConstants.AXES)) {
            return "axe";
        }
        if (item instanceof PickaxeItem || isInTag(item, ItemTagsConstants.PICKAXES)) {
            return "pickaxe";
        }
        if (item instanceof ShovelItem || isInTag(item, ItemTagsConstants.SHOVELS)) {
            return "shovel";
        }
        if (item instanceof HoeItem || isInTag(item, ItemTagsConstants.HOES)) {
            return "hoe";
        }
        if (item instanceof ShieldItem || isInTag(item, ItemTagsConstants.SHIELDS)) {
            return "shield";
        }
        if (item instanceof CrossbowItem || isInTag(item, ItemTagsConstants.CROSSBOWS)) {
            return "crossbow";
        }
        if (item instanceof BowItem || isInTag(item, ItemTagsConstants.BOWS)) {
            return "bow";
        }
        if (item instanceof FishingRodItem || isInTag(item, ItemTagsConstants.FISHING_RODS)) {
            return "fishing_rod";
        }
        if (item instanceof TridentItem || isInTag(item, ItemTagsConstants.TRIDENTS)) {
            return "spear";
        }
        if (item instanceof ThrowablePotionItem || isInTag(item, ItemTagsConstants.THROWABLE_POTION)) {
            return "throwable_potion";
        }
        return EMPTY;
    }

    private static boolean isInTag(Item item, ResourceLocation tagId) {
        ITag<Item> tag = TagCollectionManager.getManager().getItemTags().get(tagId);
        return tag != null && tag.contains(item);
    }
}
