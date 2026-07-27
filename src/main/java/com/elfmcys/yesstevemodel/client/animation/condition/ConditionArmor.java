package com.elfmcys.yesstevemodel.client.animation.condition;

import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.util.EquipmentUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConditionArmor {
    private static final String EMPTY = "";

    private final Map<EquipmentSlotType, Set<ResourceLocation>> idTests = new EnumMap<>(EquipmentSlotType.class);
    private final Map<EquipmentSlotType, Set<ResourceLocation>> tagTests = new EnumMap<>(EquipmentSlotType.class);

    public void addTest(String name) {
        addTest(name, '$', this.idTests);
        addTest(name, '#', this.tagTests);
    }

    private static void addTest(String name, char separator, Map<EquipmentSlotType, Set<ResourceLocation>> tests) {
        if (name == null) {
            return;
        }

        int separatorIndex = name.indexOf(separator);
        if (separatorIndex <= 0 || separatorIndex == name.length() - 1) {
            return;
        }

        EquipmentSlotType slot = getType(name.substring(0, separatorIndex));
        ResourceLocation id = ResourceLocation.tryCreate(name.substring(separatorIndex + 1));
        if (slot != null && id != null) {
            tests.computeIfAbsent(slot, ignored -> new HashSet<>()).add(id);
        }
    }

    public String doTest(LivingEntity entity, EquipmentSlotType slot) {
        ItemStack stack = CosmeticArmorHelper.getArmorItem(entity, slot);
        if (stack.isEmpty()) {
            return EMPTY;
        }

        String result = doIdTest(stack, slot);
        return result.isEmpty() ? doTagTest(stack, slot) : result;
    }

    private String doIdTest(ItemStack stack, EquipmentSlotType slot) {
        Set<ResourceLocation> ids = this.idTests.get(slot);
        if (ids == null || ids.isEmpty()) {
            return EMPTY;
        }

        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        return id != null && ids.contains(id) ? slot.getName() + "$" + id : EMPTY;
    }

    private String doTagTest(ItemStack stack, EquipmentSlotType slot) {
        Set<ResourceLocation> tagIds = this.tagTests.get(slot);
        if (tagIds == null || tagIds.isEmpty()) {
            return EMPTY;
        }

        for (ResourceLocation tagId : tagIds) {
            ITag<Item> tag = TagCollectionManager.getManager().getItemTags().get(tagId);
            if (tag != null && tag.contains(stack.getItem())) {
                return slot.getName() + "#" + tagId;
            }
        }
        return EMPTY;
    }

    public boolean hasFilter(EquipmentSlotType slot) {
        return this.idTests.containsKey(slot) || this.tagTests.containsKey(slot);
    }

    @Nullable
    public static EquipmentSlotType getType(String type) {
        return EquipmentUtil.getEquipmentSlotByName(type).orElse(null);
    }
}
