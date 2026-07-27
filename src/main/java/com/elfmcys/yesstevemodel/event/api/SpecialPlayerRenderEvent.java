package com.elfmcys.yesstevemodel.event.api;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class SpecialPlayerRenderEvent {
    private final PlayerEntity player;
    private final CustomPlayerEntity customPlayer;
    private final String modelId;
    @Nullable
    private ResourceLocation textureLocation;

    public SpecialPlayerRenderEvent(PlayerEntity player, CustomPlayerEntity customPlayer, String modelId) {
        this.player = player;
        this.customPlayer = customPlayer;
        this.modelId = modelId;
        // Upstream leaves this null unless another mod overrides it; null means "use the
        // animatable's own texture AND its texture index" in GeoReplacedEntityRenderer.
        this.textureLocation = null;
    }

    public PlayerEntity getPlayer() { return this.player; }
    public CustomPlayerEntity getCustomPlayer() { return this.customPlayer; }
    public String getModelId() { return this.modelId; }
    @Nullable
    public ResourceLocation getTextureLocation() { return this.textureLocation; }
    public void setTextureLocation(@Nullable ResourceLocation loc) { this.textureLocation = loc; }
}
