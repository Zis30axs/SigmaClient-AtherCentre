package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.config.UIConstants;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

final class ModelSelectionPolicy {
    private ModelSelectionPolicy() {
    }

    static boolean shouldUseVanillaRenderer(String selectedModel, AbstractClientPlayerEntity player) {
        return selectedModel == null
                || selectedModel.isEmpty()
                || UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel)
                || player.isSpectator();
    }
}
