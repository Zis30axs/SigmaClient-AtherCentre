package com.shiroha.mmdskin.player.render;

import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

record PlayerRenderRequest(
        AbstractClientPlayerEntity player,
        String selectedModel,
        String playerCacheKey,
        boolean localPlayer,
        boolean ysmActive
) {
}
