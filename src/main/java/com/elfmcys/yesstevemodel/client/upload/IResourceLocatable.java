package com.elfmcys.yesstevemodel.client.upload;

import net.minecraft.util.ResourceLocation;

import java.util.Optional;

public interface IResourceLocatable {
    ResourceLocation getLocation();

    default Optional<ResourceLocation> getResourceLocation() {
        ResourceLocation loc = getLocation();
        return loc != null ? Optional.of(loc) : Optional.empty();
    }
}