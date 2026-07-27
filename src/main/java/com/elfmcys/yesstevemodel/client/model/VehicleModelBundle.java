package com.elfmcys.yesstevemodel.client.model;

import com.elfmcys.yesstevemodel.geckolib3.core.controller.controllers.VehicleAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.entity.GeckoVehicleEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.renderer.texture.Texture;

import java.util.function.Consumer;

public class VehicleModelBundle {
    private final GeoModel model;

    private final Object2ReferenceMap<String, Animation> animations;

    private final Object2ReferenceMap<String, AnimationController> animationControllers;

    private final Texture texture;

    private final Consumer<GeckoVehicleEntity> controllerInitializer;

    public VehicleModelBundle(GeoModel model, Object2ReferenceMap<String, Animation> animations, Object2ReferenceMap<String, AnimationController> animationControllers, Texture Texture, ModelResourceBundle modelResourceBundle) {
        this.model = model;
        this.animations = animations;
        this.animationControllers = animationControllers;
        this.texture = Texture;
        this.controllerInitializer = VehicleAnimationController.buildControllers(this, modelResourceBundle);
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

    public Consumer<GeckoVehicleEntity> getAnimatableConsumer() {
        return this.controllerInitializer;
    }
}