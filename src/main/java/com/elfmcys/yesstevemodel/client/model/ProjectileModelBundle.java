package com.elfmcys.yesstevemodel.client.model;

import com.elfmcys.yesstevemodel.client.entity.GeckoProjectileEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.controllers.ProjectileAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.renderer.texture.Texture;

import java.util.function.Consumer;

public class ProjectileModelBundle {

    private final GeoModel model;

    private final Object2ReferenceMap<String, Animation> animations;

    private final Object2ReferenceMap<String, AnimationController> animationControllers;

    private final Texture texture;

    private final Consumer<GeckoProjectileEntity> controllerInitializer;

    public ProjectileModelBundle(GeoModel model, Object2ReferenceMap<String, Animation> animations, Object2ReferenceMap<String, AnimationController> animationControllers, Texture texture, ModelResourceBundle resourceBundle) {
        this.model = model;
        this.animations = animations;
        this.animationControllers = animationControllers;
        this.texture = texture;
        this.controllerInitializer = ProjectileAnimationController.buildControllers(this, resourceBundle);
    }

    public GeoModel getModel() {
        return this.model;
    }

    public Object2ReferenceMap<String, Animation> getAnimations() {
        return this.animations;
    }

    public Object2ReferenceMap<String, AnimationController> getAnimationControllers() {
        return this.animationControllers;
    }

    public Texture getTexture() {
        return this.texture;
    }

    public Consumer<GeckoProjectileEntity> getControllerInitializer() {
        return this.controllerInitializer;
    }
}