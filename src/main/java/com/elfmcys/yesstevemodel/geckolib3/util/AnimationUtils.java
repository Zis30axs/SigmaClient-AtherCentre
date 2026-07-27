package com.elfmcys.yesstevemodel.geckolib3.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.entity.Entity;

@SuppressWarnings({"unchecked"})
public class AnimationUtils {
    public static float convertTicksToSeconds(float ticks) {
        return ticks / 20;
    }

    public static float convertSecondsToTicks(float seconds) {
        return seconds * 20;
    }

    public static <T extends Entity> EntityRenderer<T> getRenderer(T entity) {
        EntityRendererManager renderManager = Minecraft.getInstance().getRenderManager();
        return (EntityRenderer<T>) renderManager.getRenderer(entity);
    }
}