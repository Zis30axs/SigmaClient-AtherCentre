package com.elfmcys.yesstevemodel.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import java.util.concurrent.TimeUnit;

/**
 * Port of upstream {@code util/AnimatableCacheUtil} (1.20.1).
 *
 * <p>Holds the throwaway entities the animation-test preview renders as vehicles
 * ({@code ModelPreviewRenderer.renderVehicleForAnimation} fetches a horse/pig/boat keyed by
 * {@code EntityType.getKey}). Upstream caches them for five minutes of idle; the Forge
 * {@code @OnlyIn(Dist.CLIENT)} marker has no counterpart here and is simply dropped — this class is
 * only ever touched from the client render path.
 */
public final class AnimatableCacheUtil {

    public static final Cache<ResourceLocation, Entity> ENTITIES_CACHE =
            CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    private AnimatableCacheUtil() {
    }
}
