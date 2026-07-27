package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.player.model.PlayerModelResolver;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

final class PlayerRenderSelectionResolver {

    private PlayerRenderSelectionResolver() {
    }

    static PlayerRenderSelection resolve(AbstractClientPlayerEntity player, boolean isYsmActive) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean isLocalPlayer = minecraft.player != null && minecraft.player.getUniqueID().equals(player.getUniqueID());
        String playerName = player.getName().getString();
        String selectedModel = PlayerModelSyncService.getPlayerModel(player.getUniqueID(), playerName, isLocalPlayer);
        PlayerRenderRequest request = new PlayerRenderRequest(
                player,
                selectedModel,
                PlayerModelResolver.getCacheKey(player),
                isLocalPlayer,
                isYsmActive);

        PlayerRenderAction terminalAction = PlayerVanillaRenderPolicy.resolveTerminalAction(request);
        if (terminalAction != null) {
            return PlayerRenderSelection.terminal(terminalAction);
        }

        if (!PlayerPerformanceGate.allowsMmd(request.player())) {
            return PlayerRenderSelection.terminal(PlayerRenderAction.FALLTHROUGH);
        }

        return PlayerRenderSelection.render(request.selectedModel(), request.playerCacheKey(), request.localPlayer());
    }
}
