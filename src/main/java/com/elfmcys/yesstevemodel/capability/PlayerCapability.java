package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.entity.GeoEntity;
import com.elfmcys.yesstevemodel.client.entity.PlayerEntityFrameState;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.molang.runtime.Struct;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

public final class PlayerCapability extends CustomPlayerEntity {

    public PlayerCapability(PlayerEntity player) {
        super(player, player instanceof ClientPlayerEntity, true);
    }

    @Override
    public PlayerEntityFrameState createPositionTracker(PlayerEntity player) {
        return new PlayerEntityFrameState(player, player instanceof ClientPlayerEntity);
    }

    @Override
    public PlayerEntityFrameState getPositionTracker() {
        return (PlayerEntityFrameState) super.getPositionTracker();
    }

    public boolean isClientPlayerEntityModel() {
        return isLocalPlayerModel();
    }

    @Nullable
    public Struct getServerVarContainer() {
        return null;
    }

    @Override
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean z) {
        return new TexturedModelWrapper(modelAssembly, z, true, true, 600);
    }
}
