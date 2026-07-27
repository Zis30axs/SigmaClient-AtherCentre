package com.elfmcys.yesstevemodel.client.compat.parcool;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

public class ParcoolCompat {
    public static boolean isPlayerParcooling(PlayerEntity player) {
        return false;
    }

    @Nullable
    public static String getActionName(PlayerEntity player) {
        return null;
    }
}
