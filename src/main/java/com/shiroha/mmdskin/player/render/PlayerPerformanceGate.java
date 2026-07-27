package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.render.policy.RenderPriorityService;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

final class PlayerPerformanceGate {
    private PlayerPerformanceGate() {
    }

    static boolean allowsMmd(AbstractClientPlayerEntity player) {
        return RenderPriorityService.get().shouldUsePlayerModel(player);
    }
}
