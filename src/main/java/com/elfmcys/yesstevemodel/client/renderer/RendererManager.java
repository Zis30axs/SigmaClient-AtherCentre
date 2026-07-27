package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.compat.sbackpack.SBackpackCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.resources.IResourceManager;

public class RendererManager {

    private static CustomPlayerRenderer playerRenderer;

    private static ProjectileRenderer projectileRenderer;

    private static HandItemRenderer handRenderer;

    private static VehicleRenderer vehicleRenderer;

    private static void initRenderers(IResourceManager resourceManager) {
        EntityRendererManager renderManager = Minecraft.getInstance().getRenderManager();
        playerRenderer = new CustomPlayerRenderer(renderManager);
        projectileRenderer = new ProjectileRenderer(renderManager);
        handRenderer = new HandItemRenderer();
        vehicleRenderer = new VehicleRenderer(renderManager);
        SBackpackCompat.setupRenderLayers();
    }

    public static void onResourceManagerReload(IResourceManager resourceManager) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        initRenderers(resourceManager);
    }

    public static CustomPlayerRenderer getPlayerRenderer() {
        if (playerRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return playerRenderer;
    }

    public static ProjectileRenderer getProjectileRenderer() {
        if (projectileRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return projectileRenderer;
    }

    public static HandItemRenderer getHandRenderer() {
        if (handRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return handRenderer;
    }

    public static VehicleRenderer getVehicleRenderer() {
        if (vehicleRenderer == null) {
            initRenderers(Minecraft.getInstance().getResourceManager());
        }
        return vehicleRenderer;
    }
}