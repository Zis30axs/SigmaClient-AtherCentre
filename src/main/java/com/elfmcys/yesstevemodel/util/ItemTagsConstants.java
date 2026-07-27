package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.util.ResourceLocation;

public final class ItemTagsConstants {
    public static final ResourceLocation AXES = createTag("axes");
    public static final ResourceLocation HOES = createTag("hoes");
    public static final ResourceLocation PICKAXES = createTag("pickaxes");
    public static final ResourceLocation SHOVELS = createTag("shovels");
    public static final ResourceLocation SWORDS = createTag("swords");
    public static final ResourceLocation THROWABLE_POTION = createTag("throwable_potion");
    public static final ResourceLocation BOWS = createTag("bows");
    public static final ResourceLocation CROSSBOWS = createTag("crossbows");
    public static final ResourceLocation FISHING_RODS = createTag("fishing_rods");
    public static final ResourceLocation SHIELDS = createTag("shields");
    public static final ResourceLocation TRIDENTS = createTag("tridents");

    private ItemTagsConstants() {
    }

    private static ResourceLocation createTag(String name) {
        return new ResourceLocation(YesSteveModel.MOD_ID, name);
    }
}
