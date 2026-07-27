package com.elfmcys.yesstevemodel.geckolib3.core.controller.controllers;

import com.elfmcys.yesstevemodel.client.animation.AnimationManager;
import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.StopAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionArmor;
import com.elfmcys.yesstevemodel.client.animation.predicate.ArmorPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.InteractionHandAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.ItemHoldAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.LivingMovementAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.MainHandHoldPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.NamedAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.OffHandHoldPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.OffhandAttackAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.PlayerBaseAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.PlayerCustomAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.PlayerIdleAnimationPredicate;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.model.AnimationDataProvider;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.model.PlayerModelBundle;
import com.elfmcys.yesstevemodel.client.model.processor.ArmorSlotProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ControllerSlotBinder;
import com.elfmcys.yesstevemodel.client.model.processor.ModelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.NamedModelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ParallelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ProcessorPipeline;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.CompositeAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.IAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.PredicateBasedController;
import com.elfmcys.yesstevemodel.util.function.TriFunction;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.inventory.EquipmentSlotType;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class PlayerAnimationController {
    private static final ProcessorPipeline<CustomPlayerEntity, PlayerModelBundle> REGISTRY =
            new ProcessorPipeline<>();
    private static final String PLAYER_PREFIX = "player";

    public static final String CAP_CONTROLLER_KEY = PLAYER_PREFIX + ".cap";

    private PlayerAnimationController() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerControllers() {
        registerParallelController(
                "pre_parallel",
                (controllerKey, entity, linkedAnimationName) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        linkedAnimationName != null
                                ? new NamedAnimationPredicate(linkedAnimationName)
                                : StopAnimationPredicate.INSTANCE));
        registerController(
                "vehicle",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.1f,
                        new LivingMovementAnimationPredicate()));
        registerSlotController(
                "pre_main",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerController(
                "main",
                (key, entity) -> new CompositeAnimationController<>(
                        entity,
                        key,
                        0.1f,
                        new AnimationManager()));
        registerSlotController(
                "post_main",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerSlotController(
                "pre_hold",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerController(
                "hold_offhand",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.1f,
                        new OffHandHoldPredicate()));
        registerController(
                "hold_mainhand",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.1f,
                        new MainHandHoldPredicate()));
        registerSlotController(
                "post_hold",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerSlotController(
                "pre_swing",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerController(
                "swing",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.0f,
                        new ItemHoldAnimationPredicate()));
        registerSlotController(
                "post_swing",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerSlotController(
                "pre_use",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerController(
                "use",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.1f,
                        new InteractionHandAnimationPredicate()));
        registerSlotController(
                "post_use",
                (controllerKey, entity) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        new StopAnimationPredicate()));
        registerController(
                "passenger",
                (controllerKey, entity) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.1f,
                        new OffhandAttackAnimationPredicate()));
        registerController(
                "cap",
                (key, entity) -> new PredicateBasedController<>(
                        entity,
                        key,
                        0.0f,
                        new PlayerBaseAnimationPredicate()));
        registerController(
                "gui_hover",
                true,
                (key, entity) -> new PredicateBasedController<>(
                        entity,
                        key,
                        0.0f,
                        new PlayerCustomAnimationPredicate()));
        registerController(
                "gui_focus",
                true,
                (key, entity) -> new PredicateBasedController<>(
                        entity,
                        key,
                        0.0f,
                        new PlayerIdleAnimationPredicate()));
        registerParallelController(
                "parallel",
                (controllerKey, entity, linkedAnimationName) -> new CompositeAnimationController(
                        entity,
                        controllerKey,
                        0.0f,
                        linkedAnimationName != null
                                ? new NamedAnimationPredicate(linkedAnimationName)
                                : StopAnimationPredicate.INSTANCE,
                        true));
        registerArmorController(
                "armor",
                (controllerKey, entity, equipmentSlot) -> new CompositeAnimationController<>(
                        entity,
                        controllerKey,
                        0.0f,
                        new ArmorPredicate(equipmentSlot)));
    }

    public static Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle bundle, ModelResourceBundle resourceBundle) {
        if (REGISTRY.isEmpty()) {
            registerControllers();
        }
        return REGISTRY.buildAll(bundle, resourceBundle);
    }

    public static void registerController(
            String controllerName,
            BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        registerController(controllerName, false, controllerFactory);
    }

    private static void registerController(
            String controllerName,
            boolean guiOnly,
            BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        String controllerKey = PLAYER_PREFIX + "." + controllerName;
        ModelProcessor<CustomPlayerEntity, PlayerModelBundle> processor =
                (modelData, resourceBundle) ->
                        (entity, consumer) -> consumer.accept(controllerFactory.apply(controllerKey, entity));
        if (guiOnly) {
            processor = processor.withFilter(entity -> entity instanceof IPreviewAnimatable);
        }
        REGISTRY.register(processor);
    }

    private static void registerSlotController(
            String slotName,
            BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ControllerSlotBinder<>(
                PLAYER_PREFIX,
                slotName,
                PlayerAnimationDataProvider.INSTANCE,
                controllerFactory));
    }

    private static void registerNamedController(
            String slotName,
            String[] requiredAnimations,
            boolean checkAnimationEntries,
            BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new NamedModelProcessor<>(
                PLAYER_PREFIX,
                slotName,
                requiredAnimations,
                checkAnimationEntries,
                PlayerAnimationDataProvider.INSTANCE,
                controllerFactory));
    }

    private static void registerParallelController(
            String slotName,
            TriFunction<String, CustomPlayerEntity, String, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ParallelProcessor<>(
                PLAYER_PREFIX,
                slotName,
                true,
                PlayerAnimationDataProvider.INSTANCE,
                controllerFactory));
    }

    private static void registerArmorController(
            String category,
            TriFunction<String, CustomPlayerEntity, EquipmentSlotType, IAnimationController<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ArmorSlotProcessor<>(
                PLAYER_PREFIX,
                category,
                PlayerAnimationDataProvider.INSTANCE,
                controllerFactory));
    }

    private static final class PlayerAnimationDataProvider implements AnimationDataProvider<PlayerModelBundle> {
        private static final PlayerAnimationDataProvider INSTANCE = new PlayerAnimationDataProvider();

        @Override
        public Object2ReferenceMap<String, AnimationController> getAnimationEntries(
                PlayerModelBundle modelBundle,
                ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationEntries();
        }

        @Override
        public Object2ReferenceMap<String, Animation> getAnimations(
                PlayerModelBundle modelBundle,
                ModelResourceBundle resourceBundle) {
            return modelBundle.getMainAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(
                PlayerModelBundle modelBundle,
                ModelResourceBundle resourceBundle) {
            return modelBundle.getConditionManager().getArmor();
        }
    }
}
