package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import java.util.HashSet;
import java.util.Set;

public class ConditionVehicle {
    private static final String EMPTY = "";
    private static final String ID_PREFIX = "vehicle$";
    private static final String TAG_PREFIX = "vehicle#";

    private final Set<ResourceLocation> idTests = new HashSet<>();
    private final Set<ResourceLocation> tagTests = new HashSet<>();

    public void addTest(String name) {
        addTest(name, ID_PREFIX, this.idTests);
        addTest(name, TAG_PREFIX, this.tagTests);
    }

    public String doTest(LivingEntity entity) {
        Entity vehicle = entity.getRidingEntity();
        return vehicle == null || !vehicle.isAlive() ? EMPTY : doTest(vehicle);
    }

    private String doTest(Entity entity) {
        ResourceLocation id = Registry.ENTITY_TYPE.getKey(entity.getType());
        if (id != null && this.idTests.contains(id)) {
            return ID_PREFIX + id;
        }

        for (ResourceLocation tagId : this.tagTests) {
            ITag<EntityType<?>> tag = TagCollectionManager.getManager().getEntityTypeTags().get(tagId);
            if (tag != null && tag.contains(entity.getType())) {
                return TAG_PREFIX + tagId;
            }
        }
        return EMPTY;
    }

    private static void addTest(String name, String prefix, Set<ResourceLocation> tests) {
        if (name == null || !name.startsWith(prefix)) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryCreate(name.substring(prefix.length()));
        if (id != null) {
            tests.add(id);
        }
    }
}
