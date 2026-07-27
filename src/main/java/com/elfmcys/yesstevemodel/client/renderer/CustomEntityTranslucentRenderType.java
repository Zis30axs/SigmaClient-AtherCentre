package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity-translucent render type with upload sorting disabled - YSM sorts its own quads, and letting
 * the buffer re-sort them scrambles multi-layer models.
 *
 * <p>Port notes: 1.16.5 field names are {@code useDelegate}/{@code needsSorting} where 1.20 has
 * {@code affectsCrumbling}/sort-on-upload, and {@code Util.memoize} does not exist here, so the cache
 * is a plain {@link ConcurrentHashMap}. 1.16.5 also has no {@code isOutline()}; only
 * {@link #getOutline()} needs to delegate.
 */
public class CustomEntityTranslucentRenderType extends RenderType {

    private static final ConcurrentHashMap<ResourceLocation, CustomEntityTranslucentRenderType> CACHE = new ConcurrentHashMap<>();

    private final Optional<RenderType> delegateOutline;

    private CustomEntityTranslucentRenderType(RenderType renderType) {
        super("entity_translucent_ysm", renderType.getVertexFormat(), renderType.getDrawMode(), renderType.getBufferSize(),
                renderType.isUseDelegate(), false, renderType::setupRenderState, renderType::clearRenderState);
        this.delegateOutline = renderType.getOutline();
    }

    @Override
    @NotNull
    public Optional<RenderType> getOutline() {
        return this.delegateOutline;
    }

    public static CustomEntityTranslucentRenderType get(ResourceLocation resourceLocation) {
        return CACHE.computeIfAbsent(resourceLocation,
                rl -> new CustomEntityTranslucentRenderType(RenderType.getEntityTranslucent(rl)));
    }
}
