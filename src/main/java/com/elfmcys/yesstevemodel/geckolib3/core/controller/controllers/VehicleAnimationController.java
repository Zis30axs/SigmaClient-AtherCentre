package com.elfmcys.yesstevemodel.geckolib3.core.controller.controllers;

import com.elfmcys.yesstevemodel.client.entity.GeckoVehicleEntity;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.model.VehicleModelBundle;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.IAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.BoneTransformProvider;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.AnimationContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.snapshot.BoneTopLevelSnapshot;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import java.util.List;
import java.util.function.Consumer;

public class VehicleAnimationController<T extends AnimatableEntity<?>> implements IAnimationController<T> {
    public static final String ORIGIN_CONTROLLER_KEY = "controller.vehicle.origin";
    private final T animatable;
    public VehicleAnimationController(T animatable) { this.animatable = animatable; }
    @Override public String getName() { return ORIGIN_CONTROLLER_KEY; }
    @Override public String getCurrentAnimation() { return ""; }
    @Override public void init(List<BoneTopLevelSnapshot> list, Object2ReferenceMap<String, List<IValue>> map) {}
    @Override public void process(AnimationEvent<T> event, ExpressionEvaluator<AnimationContext<?>> evaluator, boolean z) {}
    @Override public void forEachTransform(Consumer<BoneTransformProvider> consumer) {}
    @Override public void reset() {}

    public static Consumer<GeckoVehicleEntity> buildControllers(VehicleModelBundle bundle, ModelResourceBundle resourceBundle) {
        return entity -> {};
    }
}