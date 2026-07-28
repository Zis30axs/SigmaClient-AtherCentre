package de.florianmichael.viamcp.fixes;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.ViaMCP;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.SimpleReloadableResourceManager;
import net.minecraft.resources.VanillaPack;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.ITagCollectionSupplier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollectionReader;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ViaFabricPlus-style registry/tag validation bypass for ViaMCP.
 *
 * <p>On modern targets (especially 1.20.2+ configuration-phase servers such as
 * 1.20.3/4), ViaBackwards can deliver an empty or incomplete UPDATE_TAGS payload
 * to the 1.16.5 client. Vanilla then disconnects with
 * {@code multiplayer.disconnect.missing_tags}
 * ("Incomplete set of tags received from server").
 *
 * <p>ViaFabricPlus solves the modern-client side by skipping hard registry/tag
 * validation errors and returning empty holder sets. Here we:
 * <ol>
 *   <li>detect non-native Via targets</li>
 *   <li>fill missing required tags from local vanilla datapack data</li>
 *   <li>keep any tags the server/Via did send</li>
 *   <li>allow the join to continue instead of instant-kick</li>
 * </ol>
 */
public final class IncompleteTagsFix {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-Tags");
    private static final Object LOCK = new Object();
    private static volatile ITagCollectionSupplier localVanillaTags;
    private static volatile boolean localLoadAttempted;

    private IncompleteTagsFix() {
    }

    /**
     * True when JelloPortal/Via is translating away from the native 1.16.5 protocol.
     * Matches the ViaFabricPlus idea of relaxing registry validation on translated links.
     */
    public static boolean shouldRelaxValidation() {
        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        if (loadingBase == null) {
            return false;
        }
        ProtocolVersion target = loadingBase.getTargetVersion();
        if (target == null) {
            return false;
        }
        return target.getVersion() != ViaMCP.NATIVE_VERSION;
    }

    /**
     * Repair an incomplete server tag set for Via connections.
     * Server-provided entries win; missing required tags are filled from local vanilla data
     * (or empty tags if local data cannot be loaded).
     */
    public static ITagCollectionSupplier repair(ITagCollectionSupplier serverTags,
                                                Multimap<ResourceLocation, ResourceLocation> missing) {
        if (serverTags == null) {
            serverTags = ITagCollectionSupplier.TAG_COLLECTION_SUPPLIER;
        }
        if (missing == null || missing.isEmpty()) {
            return serverTags;
        }

        ITagCollectionSupplier local = getLocalVanillaTags();
        ITagCollection<Block> blocks = merge(serverTags.getBlockTags(), local != null ? local.getBlockTags() : null, BlockTags.getAllTags());
        ITagCollection<Item> items = merge(serverTags.getItemTags(), local != null ? local.getItemTags() : null, ItemTags.getAllTags());
        ITagCollection<Fluid> fluids = merge(serverTags.getFluidTags(), local != null ? local.getFluidTags() : null, FluidTags.getAllTags());
        ITagCollection<EntityType<?>> entities = merge(serverTags.getEntityTypeTags(), local != null ? local.getEntityTypeTags() : null, EntityTypeTags.getAllTags());

        LOGGER.warn("Repaired incomplete Via tags (target={}): filled {} missing registries/entries from local vanilla data",
                safeTargetName(), missing.size());
        return ITagCollectionSupplier.getTagCollectionSupplier(blocks, items, fluids, entities);
    }

    private static String safeTargetName() {
        try {
            ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
            if (loadingBase != null && loadingBase.getTargetVersion() != null) {
                return loadingBase.getTargetVersion().getName();
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static <T> ITagCollection<T> merge(ITagCollection<T> server,
                                               ITagCollection<T> local,
                                               java.util.List<? extends ITag.INamedTag<T>> required) {
        Map<ResourceLocation, ITag<T>> map = new HashMap<>();
        if (local != null) {
            map.putAll(local.getIDTagMap());
        }
        if (server != null) {
            // Server/Via values override local fallbacks when present.
            map.putAll(server.getIDTagMap());
        }
        if (required != null) {
            for (ITag.INamedTag<T> named : required) {
                map.putIfAbsent(named.getName(), Tag.getEmptyTag());
            }
        }
        return ITagCollection.getTagCollectionFromMap(map);
    }

    private static ITagCollectionSupplier getLocalVanillaTags() {
        ITagCollectionSupplier cached = localVanillaTags;
        if (cached != null || localLoadAttempted) {
            return cached;
        }
        synchronized (LOCK) {
            if (localVanillaTags != null || localLoadAttempted) {
                return localVanillaTags;
            }
            localLoadAttempted = true;
            try {
                localVanillaTags = loadLocalVanillaTags();
                if (localVanillaTags != null) {
                    LOGGER.info("Loaded local vanilla tags as Via incomplete-tag fallback");
                }
            } catch (Throwable t) {
                LOGGER.warn("Failed to load local vanilla tags for Via fallback", t);
                localVanillaTags = null;
            }
            return localVanillaTags;
        }
    }

    private static ITagCollectionSupplier loadLocalVanillaTags() {
        SimpleReloadableResourceManager resourceManager = new SimpleReloadableResourceManager(ResourcePackType.SERVER_DATA);
        resourceManager.addResourcePack(new VanillaPack("minecraft"));

        TagCollectionReader<Block> blocks = new TagCollectionReader<>(Registry.BLOCK::getOptional, "tags/blocks", "block");
        TagCollectionReader<Item> items = new TagCollectionReader<>(Registry.ITEM::getOptional, "tags/items", "item");
        TagCollectionReader<Fluid> fluids = new TagCollectionReader<>(Registry.FLUID::getOptional, "tags/fluids", "fluid");
        TagCollectionReader<EntityType<?>> entities = new TagCollectionReader<>(Registry.ENTITY_TYPE::getOptional, "tags/entity_types", "entity_type");

        // Run async suppliers inline so we can build synchronously on first use.
        Map<ResourceLocation, ITag.Builder> blockBuilders = joinInline(blocks.readTagsFromManager(resourceManager, Runnable::run));
        Map<ResourceLocation, ITag.Builder> itemBuilders = joinInline(items.readTagsFromManager(resourceManager, Runnable::run));
        Map<ResourceLocation, ITag.Builder> fluidBuilders = joinInline(fluids.readTagsFromManager(resourceManager, Runnable::run));
        Map<ResourceLocation, ITag.Builder> entityBuilders = joinInline(entities.readTagsFromManager(resourceManager, Runnable::run));

        // Defensive copies: buildTagCollectionFromMap mutates/removes from the builder map.
        ITagCollection<Block> blockTags = blocks.buildTagCollectionFromMap(copyBuilders(blockBuilders));
        ITagCollection<Item> itemTags = items.buildTagCollectionFromMap(copyBuilders(itemBuilders));
        ITagCollection<Fluid> fluidTags = fluids.buildTagCollectionFromMap(copyBuilders(fluidBuilders));
        ITagCollection<EntityType<?>> entityTags = entities.buildTagCollectionFromMap(copyBuilders(entityBuilders));

        return ITagCollectionSupplier.getTagCollectionSupplier(blockTags, itemTags, fluidTags, entityTags);
    }

    private static Map<ResourceLocation, ITag.Builder> copyBuilders(Map<ResourceLocation, ITag.Builder> source) {
        return Maps.newHashMap(source);
    }

    private static <T> T joinInline(CompletableFuture<T> future) {
        // With Runnable::run as executor, supplyAsync usually completes inline;
        // join() still covers any residual async edge cases.
        return future.join();
    }
}
