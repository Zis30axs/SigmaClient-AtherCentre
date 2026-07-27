package com.shiroha.mmdskin.render.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.LightType;

/** 鍏夌収璁＄畻宸ュ叿绫汇€?*/
public final class LightingHelper {

    private static final int LIGHT_SCALE = 16;

    private LightingHelper() {}

    public record LightData(int blockLight, int skyLight, float skyDarken, float intensity) {}

    private static final LightData DEFAULT_LIGHT = new LightData(0, 15, 0, 1.0f);

    public static LightData sampleLight(Entity entity, Minecraft mc) {
        if (mc.world == null) return DEFAULT_LIGHT;
        mc.world.calculateInitialSkylight();
        int eyeHeight = (int) (entity.getPosYEye() - entity.getPosition().getY());
        int blockLight = entity.world.getLightFor(LightType.BLOCK, entity.getPosition().up(eyeHeight));
        int skyLight = entity.world.getLightFor(LightType.SKY, entity.getPosition().up(eyeHeight));
        float skyDarken = mc.world.getSkylightSubtracted();

        float blockLightFactor = blockLight / 15.0f;
        float skyLightFactor = (skyLight / 15.0f) * ((15.0f - skyDarken) / 15.0f);
        float lightIntensity = Math.max(blockLightFactor, skyLightFactor);

        lightIntensity = 0.1f + lightIntensity * 0.9f;

        return new LightData(blockLight, skyLight, skyDarken, lightIntensity);
    }

    public static int computeBlockBrightness(int blockLight) {
        return LIGHT_SCALE * blockLight;
    }

    public static int computeSkyBrightness(int skyLight, float skyDarken, boolean irisActive) {
        if (irisActive) {
            return LIGHT_SCALE * skyLight;
        }
        return Math.round((15.0f - skyDarken) * (skyLight / 15.0f) * LIGHT_SCALE);
    }
}
