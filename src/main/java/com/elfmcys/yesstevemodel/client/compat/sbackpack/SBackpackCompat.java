package com.elfmcys.yesstevemodel.client.compat.sbackpack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class SBackpackCompat {
    public static boolean hasBackpack(Object entity) { return false; }
    public static ItemStack getBackpackItem(PlayerEntity player) { return null; }
    public static void setupRenderLayers() { }
}
