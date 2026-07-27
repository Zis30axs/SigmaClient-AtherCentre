package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import net.minecraft.entity.EntityType;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.Set;

public class FileTypeUtil {
    /** Upstream {@code ARCHIVE_EXTENSIONS}: extensions stripped by {@link #getNameWithoutArchiveExtension}. */
    private static final Set<String> ARCHIVE_EXTENSIONS = new HashSet<>(java.util.Arrays.asList(".zip", ".7z", ".ysm"));

    public static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    /**
     * Upstream {@code splitFileNameAndParentDir}: {@code "a/b/c.ysm" -> ("c.ysm", "a/b/")};
     * a path with no slash yields {@code (path, "")}. Local pair type is commons-lang3 (the
     * convention in this codebase) instead of fastutil's.
     */
    public static Pair<String, String> splitFileNameAndParentDir(String filePath) {
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return Pair.of(filePath, StringPool.EMPTY);
        }
        return Pair.of(filePath.substring(lastSlashIndex + 1), filePath.substring(0, lastSlashIndex + 1));
    }

    /** Upstream {@code getNameWithoutArchiveExtension}: strips .zip/.7z/.ysm from the final segment. */
    public static String getNameWithoutArchiveExtension(String filePath) {
        String fileName;
        int lastSlashIndex = filePath.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            fileName = filePath;
        } else {
            fileName = filePath.substring(lastSlashIndex + 1);
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 1 || !ARCHIVE_EXTENSIONS.contains(fileName.substring(dotIndex).toLowerCase())) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    /** Upstream {@code getFinalPathSegment}: {@code "a/b/" -> "b"}; empty-safe. */
    public static String getFinalPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return StringPool.EMPTY;
        }
        String trimmedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlashIndex = trimmedPath.lastIndexOf('/');
        return lastSlashIndex >= 0 ? trimmedPath.substring(lastSlashIndex + 1) : trimmedPath;
    }

    /**
     * Upstream {@code getPackIconLocation}: synthetic location a pack icon texture is registered
     * under. No local loader parses {@code ysm_pack.json} icons yet, so nothing registers here and
     * callers fall back to the default icon.
     */
    public static ResourceLocation getPackIconLocation(String str) {
        return new ResourceLocation(YesSteveModel.MOD_ID, "model_pack_icon/" + str.hashCode());
    }

    /**
     * Short numeric id derived from the first 8 hex digits of a model's sha256.
     *
     * <p>Deviation from upstream: upstream calls {@code substring(0, 8)} unguarded, which throws
     * for models with no hash. Folder-loaded models here legitimately have an empty hash, so a
     * short or malformed input yields 0 instead of an exception.
     */
    public static int parseHexId(String str) {
        if (str == null || str.length() < 8) {
            return 0;
        }
        try {
            return Integer.parseUnsignedInt(str.substring(0, 8), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Resolve the "match" field, e.g. ["minecraft:arrow", "#minecraft:arrows"].
     * Entries prefixed with '#' are entity-type tags.
     */
    public static Set<ResourceLocation> resolveEntityTypes(String[] strArr) {
        HashSet<ResourceLocation> hashSet = new HashSet<>();
        for (String str : strArr) {
            if (str.startsWith("#")) {
                ResourceLocation resourceLocation = ResourceLocation.tryCreate(str.substring(1));
                if (resourceLocation != null) {
                    ITag<EntityType<?>> tag = EntityTypeTags.getCollection().get(resourceLocation);
                    if (tag != null) {
                        for (EntityType<?> entityType : tag.getAllElements()) {
                            hashSet.add(Registry.ENTITY_TYPE.getKey(entityType));
                        }
                    }
                }
            } else {
                ResourceLocation resourceLocation = ResourceLocation.tryCreate(str);
                if (resourceLocation != null) {
                    hashSet.add(resourceLocation);
                }
            }
        }
        return hashSet;
    }
}
