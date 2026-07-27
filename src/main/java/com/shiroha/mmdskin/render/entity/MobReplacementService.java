package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.asset.catalog.ModelCatalogEntry;
import com.shiroha.mmdskin.config.ConfigManager;
import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;

/**
 * 解析原版生物到 MMD 模型的本地替换映射。
 */
public final class MobReplacementService {

    private MobReplacementService() {
    }

    public static String getReplacementModelName(LivingEntity entity) {
        if (entity == null || !MobReplacementTargets.isSupported(entity.getType()) || isPlayerLike(entity) || isMaidEntity(entity)) {
            return null;
        }

        ResourceLocation entityTypeId = Registry.ENTITY_TYPE.getKey(entity.getType());
        if (entityTypeId == null) {
            return null;
        }

        String modelName = ConfigManager.getMobModelReplacement(entityTypeId.toString());
        if (modelName == null || modelName.isBlank() || UIConstants.DEFAULT_MODEL_NAME.equals(modelName)) {
            return null;
        }

        return ModelCatalogEntry.findByFolderName(modelName) != null ? modelName : null;
    }

    public static boolean isMaidEntity(LivingEntity entity) {
        String className = entity.getClass().getName();
        return className.contains("EntityMaid") || className.contains("touhoulittlemaid");
    }

    private static boolean isPlayerLike(LivingEntity entity) {
        return entity.getType().toString().equals(EntityType.PLAYER.toString());
    }
}
