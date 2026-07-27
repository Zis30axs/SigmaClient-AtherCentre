package com.shiroha.mmdskin.model.runtime;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** 鏂囦欢鑱岃矗锛氫负妯″瀷浠撳偍鎻愪緵寮虹被鍨嬭姹傞敭锛屾浛浠ｆ棫鐨勫瓧绗︿覆鎷兼帴缂撳瓨閿€?*/
public record ModelRequestKey(ModelSubjectKind subjectKind, String subjectId, String modelName) {

    public ModelRequestKey {
        subjectKind = Objects.requireNonNull(subjectKind, "subjectKind");
        subjectId = normalize(subjectId);
        modelName = normalize(modelName);
    }

    public static ModelRequestKey player(PlayerEntity player, String modelName) {
        return new ModelRequestKey(ModelSubjectKind.PLAYER, playerSubjectId(player), modelName);
    }

    public static ModelRequestKey mob(Entity entity, String modelName) {
        return new ModelRequestKey(ModelSubjectKind.MOB, entitySubjectId(entity), modelName);
    }

    public static ModelRequestKey maid(UUID maidId, String modelName) {
        return new ModelRequestKey(ModelSubjectKind.MAID, maidId != null ? maidId.toString() : "unknown", modelName);
    }

    public static ModelRequestKey scene(String sceneId, String modelName) {
        return new ModelRequestKey(ModelSubjectKind.SCENE, sceneId, modelName);
    }

    public String cacheKey() {
        return subjectKind.name() + ":" + subjectId + ":" + modelName;
    }

    private static String playerSubjectId(PlayerEntity player) {
        return player != null ? entitySubjectId(player) : "unknown";
    }

    private static String entitySubjectId(Entity entity) {
        if (entity == null) {
            return "unknown";
        }
        String uuid = entity.getUniqueID().toString();
        if (uuid != null && !uuid.isBlank()) {
            return uuid;
        }
        return entity.getName().getString();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }
}
