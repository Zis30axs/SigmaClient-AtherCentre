package com.elfmcys.yesstevemodel.client.animation.condition;

import com.elfmcys.yesstevemodel.util.EquipmentUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

abstract class HandItemCondition {
    private static final String EMPTY = "";

    private final Hand hand;
    private final String idPrefix;
    private final String tagPrefix;
    private final String extraPrefix;
    private final Set<ResourceLocation> idTests = new HashSet<>();
    private final Set<ResourceLocation> tagTests = new HashSet<>();
    private final Set<UseAction> useActionTests = new HashSet<>();
    private final Set<String> innerTests = new HashSet<>();

    protected HandItemCondition(Hand hand, String idPrefix, String tagPrefix, String extraPrefix) {
        this.hand = hand;
        this.idPrefix = idPrefix;
        this.tagPrefix = tagPrefix;
        this.extraPrefix = extraPrefix;
    }

    public void addTest(String name) {
        if (name == null) {
            return;
        }

        if (name.startsWith(this.idPrefix)) {
            addResourceLocation(name.substring(this.idPrefix.length()), this.idTests);
            return;
        }
        if (name.startsWith(this.tagPrefix)) {
            addResourceLocation(name.substring(this.tagPrefix.length()), this.tagTests);
            return;
        }
        if (!name.startsWith(this.extraPrefix)) {
            return;
        }

        String value = name.substring(this.extraPrefix.length());
        if (value.isEmpty() || UseAction.NONE.name().equalsIgnoreCase(value)) {
            return;
        }
        EquipmentUtil.getUseActionByName(value).ifPresent(this.useActionTests::add);
        this.innerTests.add(this.extraPrefix + value.toLowerCase(Locale.US));
    }

    public String doTest(LivingEntity entity, Hand hand) {
        ItemStack stack = entity.getHeldItem(hand);
        if (stack.isEmpty()) {
            return EMPTY;
        }

        String result = doIdTest(stack);
        if (result.isEmpty()) {
            result = doTagTest(stack);
        }
        return result.isEmpty() ? doExtraTest(stack) : result;
    }

    private void addResourceLocation(String value, Set<ResourceLocation> tests) {
        ResourceLocation id = ResourceLocation.tryCreate(value);
        if (id != null) {
            tests.add(id);
        }
    }

    private String doIdTest(ItemStack stack) {
        if (this.idTests.isEmpty()) {
            return EMPTY;
        }

        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        return id != null && this.idTests.contains(id) ? this.idPrefix + id : EMPTY;
    }

    private String doTagTest(ItemStack stack) {
        for (ResourceLocation tagId : this.tagTests) {
            ITag<Item> tag = TagCollectionManager.getManager().getItemTags().get(tagId);
            if (tag != null && tag.contains(stack.getItem())) {
                return this.tagPrefix + tagId;
            }
        }
        return EMPTY;
    }

    private String doExtraTest(ItemStack stack) {
        if (this.useActionTests.isEmpty() && this.innerTests.isEmpty()) {
            return EMPTY;
        }

        String innerName = this.extraPrefix + InnerClassify.getItemType(stack);
        if (this.innerTests.contains(innerName)) {
            return innerName;
        }

        UseAction useAction = stack.getUseAction();
        return this.useActionTests.contains(useAction)
                ? this.extraPrefix + useAction.name().toLowerCase(Locale.US)
                : EMPTY;
    }

    protected Hand getHand() {
        return this.hand;
    }
}
