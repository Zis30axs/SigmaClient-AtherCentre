package com.shiroha.mmdskin.render.entity;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityClassification;

import java.util.Comparator;
import java.util.List;

/**
 * 可用于 MMD 替换的原版生物目标列表。
 */
public final class MobReplacementTargets {

    private static final List<Target> TARGETS = Registry.ENTITY_TYPE.stream()
        .filter(MobReplacementTargets::isSupported)
        .map(entityType -> new Target(
            Registry.ENTITY_TYPE.getKey(entityType),
            entityType,
            entityType.getName()))
        .sorted(Comparator.comparing(target -> target.entityTypeId().toString()))
        .toList();

    private MobReplacementTargets() {
    }

    public static List<Target> all() {
        return TARGETS;
    }

    public static boolean isSupported(EntityType<?> entityType) {
        if (entityType == EntityType.PLAYER) {
            return false;
        }

        ResourceLocation entityTypeId = Registry.ENTITY_TYPE.getKey(entityType);
        return entityTypeId != null
            && "minecraft".equals(entityTypeId.getNamespace())
            && entityType.getClassification() != EntityClassification.MISC;
    }

    public record Target(ResourceLocation entityTypeId, EntityType<?> entityType, ITextComponent displayName) {
    }
}
