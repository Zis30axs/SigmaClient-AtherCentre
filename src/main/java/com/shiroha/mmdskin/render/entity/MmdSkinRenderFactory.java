package com.shiroha.mmdskin.render.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.Entity;

/**
 * 1.16.5 直移植说明：无 Fabric EntityRendererProvider 接口，
 * 改为平面工厂，由客户端装配处直接调用 create(EntityRendererManager)。
 */
public class MmdSkinRenderFactory<T extends Entity> {
    String entityName;

    public MmdSkinRenderFactory(String entityName) {
        this.entityName = entityName;
    }

    public EntityRenderer<T> create(EntityRendererManager manager) {
        return new MmdSkinRenderer<>(manager, entityName);
    }
}
