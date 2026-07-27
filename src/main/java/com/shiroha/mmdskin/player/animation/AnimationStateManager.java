package com.shiroha.mmdskin.player.animation;

import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.sync.PlayerActionSyncService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AnimationStateManager {

    private static final float TRANSITION_TIME = 0.25f;
    static final String DRINK_ANIMATION = "Drink";

    public static void updateAnimationState(AbstractClientPlayerEntity player, ManagedModel model) {
        if (model.entityState().playCustomAnim) {
            if (!model.entityState().playStageAnim) {
                boolean local = isLocalPlayer(player);
                if (local && shouldStopCustomAnimation(player)) {
                    stopCustomAnim(model);
                    PlayerActionSyncService.getInstance().syncAnimStop();
                }
            }
        }

        if (!model.entityState().playCustomAnim) {
            updateLayer0Animation(player, model);
            updateLayer1Animation(player, model);
            updateLayer2Animation(player, model);
        }
    }

    private static void updateLayer0Animation(AbstractClientPlayerEntity player, ManagedModel model) {
        EntityAnimState.State target = resolveLayer0State(player);
        changeAnimationOnce(model, target, 0);
    }

    private static EntityAnimState.State resolveLayer0State(AbstractClientPlayerEntity player) {
        if (player.getHealth() == 0.0f) return EntityAnimState.State.Die;
        if (player.isElytraFlying()) return EntityAnimState.State.ElytraFly;
        if (player.isSleeping()) return EntityAnimState.State.Sleep;
        if (player.isPassenger()) return resolveRidingState(player);
        if (player.isSwimming()) return EntityAnimState.State.Swim;
        if (player.isOnLadder()) return resolveClimbingState(player);
        if (player.isSprinting() && !player.isSneaking()) return EntityAnimState.State.Sprint;
        if (player.isCrouching()) return resolveCrawlState(player);
        if (hasMovement(player)) return EntityAnimState.State.Walk;
        return EntityAnimState.State.Idle;
    }

    private static EntityAnimState.State resolveRidingState(AbstractClientPlayerEntity player) {
        var vehicle = player.getRidingEntity();
        if (vehicle != null && isHorselike(vehicle.getType()) && hasMovement(player)) {
            return EntityAnimState.State.OnHorse;
        }
        return EntityAnimState.State.Ride;
    }

    private static EntityAnimState.State resolveClimbingState(AbstractClientPlayerEntity player) {
        double vy = player.getPosY() - player.prevPosY;
        if (vy > 0) return EntityAnimState.State.OnClimbableUp;
        if (vy < 0) return EntityAnimState.State.OnClimbableDown;
        return EntityAnimState.State.OnClimbable;
    }

    private static EntityAnimState.State resolveCrawlState(AbstractClientPlayerEntity player) {
        return hasMovement(player) ? EntityAnimState.State.Crawl : EntityAnimState.State.LieDown;
    }

    private static void updateLayer1Animation(AbstractClientPlayerEntity player, ManagedModel model) {
        if ((!player.isHandActive() && !player.isSwingInProgress && player.hurtTime <= 0) || player.isSleeping()) {
            if (model.entityState().stateLayers[1] != EntityAnimState.State.Idle) {
                model.entityState().stateLayers[1] = EntityAnimState.State.Idle;
                model.entityState().layerAnimationKeys[1] = null;
                model.modelInstance().setLayerLoop(1, true);
                model.modelInstance().transitionAnim(0, 1, TRANSITION_TIME);
            }
        } else if (player.hurtTime <= 0) {
            updateHandAnimation(player, model);
        }
    }

    private static void updateHandAnimation(AbstractClientPlayerEntity player, ManagedModel model) {
        if (player.getActiveHand() == Hand.MAIN_HAND && player.isHandActive()) {
            updateUsingItemAnimation(model, player.getHeldItem(Hand.MAIN_HAND),
                    EntityAnimState.State.ItemRight, "Right", 1);
        } else if (player.swingingHand == Hand.MAIN_HAND && player.isSwingInProgress) {
            String itemId = getItemId(player.getHeldItem(Hand.MAIN_HAND));
            applyCustomItemAnimation(model, EntityAnimState.State.SwingRight, itemId,
                    "Right", UseAction.NONE, "swinging", 1);
        } else if (player.getActiveHand() == Hand.OFF_HAND && player.isHandActive()) {
            updateUsingItemAnimation(model, player.getHeldItem(Hand.OFF_HAND),
                    EntityAnimState.State.ItemLeft, "Left", 1);
        } else if (player.swingingHand == Hand.OFF_HAND && player.isSwingInProgress) {
            String itemId = getItemId(player.getHeldItem(Hand.OFF_HAND));
            applyCustomItemAnimation(model, EntityAnimState.State.SwingLeft, itemId,
                    "Left", UseAction.NONE, "swinging", 1);
        }
    }

    private static void updateUsingItemAnimation(ManagedModel model, ItemStack itemStack,
                                                 EntityAnimState.State targetState, String activeHand, int layer) {
        String triggerAnimation = resolveUseTriggerAnimationName(itemStack.getUseAction());
        if (triggerAnimation != null) {
            long triggerAnim = model.animationLibrary().animation(triggerAnimation);
            if (triggerAnim != 0) {
                applyLayerAnimation(model, targetState, triggerAnimation, triggerAnim, layer, false);
                return;
            }
        }

        applyCustomItemAnimation(model, targetState, getItemId(itemStack), activeHand,
                itemStack.getUseAction(), "using", layer);
    }

    private static void updateLayer2Animation(AbstractClientPlayerEntity player, ManagedModel model) {
        if (player.isSneaking() && !player.isCrouching()) {
            changeAnimationOnce(model, EntityAnimState.State.Sneak, 2);
            return;
        }

        if (model.entityState().stateLayers[2] != EntityAnimState.State.Idle) {
            model.entityState().stateLayers[2] = EntityAnimState.State.Idle;
            model.entityState().layerAnimationKeys[2] = null;
            model.modelInstance().transitionAnim(0, 2, TRANSITION_TIME);
        }
    }

    private static void stopCustomAnim(ManagedModel model) {
        model.entityState().playCustomAnim = false;
        model.entityState().playStageAnim = false;
        model.modelInstance().changeAnim(model.animationLibrary().animation("idle"), 0);
        model.modelInstance().setLayerLoop(1, true);
        model.modelInstance().changeAnim(0, 1);
        model.modelInstance().changeAnim(0, 2);
        model.modelInstance().resetPhysics();
        model.entityState().invalidateStateLayers();
    }

    private static boolean shouldStopCustomAnimation(AbstractClientPlayerEntity player) {
        return player.getHealth() == 0.0f || player.isElytraFlying()
                || player.isSleeping() || player.isSwimming()
                || player.isOnLadder() || player.isSprinting()
                || player.isCrouching() || player.isPassenger()
                || hasMovement(player);
    }

    private static boolean hasMovement(AbstractClientPlayerEntity player) {
        return player.getPosX() - player.prevPosX != 0.0f || player.getPosZ() - player.prevPosZ != 0.0f;
    }

    private static boolean isLocalPlayer(AbstractClientPlayerEntity player) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUniqueID().equals(player.getUniqueID());
    }

    private static boolean isHorselike(EntityType<?> type) {
        return type == EntityType.HORSE || type == EntityType.DONKEY
                || type == EntityType.MULE || type == EntityType.SKELETON_HORSE
                || type == EntityType.ZOMBIE_HORSE;
    }

    private static void changeAnimationOnce(ManagedModel model, EntityAnimState.State targetState, int layer) {
        String animationKey = targetState.propertyName;
        if (model.entityState().stateLayers[layer] != targetState
                || !Objects.equals(model.entityState().layerAnimationKeys[layer], animationKey)) {
            model.entityState().stateLayers[layer] = targetState;
            model.entityState().layerAnimationKeys[layer] = animationKey;
            model.modelInstance().transitionAnim(model.animationLibrary().animation(animationKey), layer, TRANSITION_TIME);
        }
    }

    private static void applyCustomItemAnimation(ManagedModel model, EntityAnimState.State targetState,
                                                 String itemName, String activeHand, UseAction useAnim,
                                                 String handState, int layer) {
        boolean shouldLoop = !"using".equals(handState);
        for (String animationKey : resolveItemAnimationKeys(itemName, activeHand, useAnim, handState)) {
            long anim = model.animationLibrary().animation(animationKey);
            if (anim != 0) {
                applyLayerAnimation(model, targetState, animationKey, anim, layer, shouldLoop);
                return;
            }
        }

        if (targetState == EntityAnimState.State.ItemRight || targetState == EntityAnimState.State.SwingRight) {
            changeAnimationOnce(model, EntityAnimState.State.SwingRight, layer);
            model.modelInstance().setLayerLoop(layer, shouldLoop);
        } else if (targetState == EntityAnimState.State.ItemLeft || targetState == EntityAnimState.State.SwingLeft) {
            changeAnimationOnce(model, EntityAnimState.State.SwingLeft, layer);
            model.modelInstance().setLayerLoop(layer, shouldLoop);
        }
    }

    private static void applyLayerAnimation(ManagedModel model, EntityAnimState.State targetState, String animationKey,
                                            long animHandle, int layer, boolean shouldLoop) {
        if (animHandle == 0) {
            return;
        }
        if (model.entityState().stateLayers[layer] != targetState
                || !Objects.equals(model.entityState().layerAnimationKeys[layer], animationKey)) {
            model.entityState().stateLayers[layer] = targetState;
            model.entityState().layerAnimationKeys[layer] = animationKey;
            model.modelInstance().setLayerLoop(layer, shouldLoop);
            model.modelInstance().transitionAnim(animHandle, layer, TRANSITION_TIME);
        }
    }

    static String resolveUseTriggerAnimationName(UseAction useAnim) {
        if (useAnim == null) {
            return null;
        }
        return switch (useAnim) {
            case EAT, DRINK -> DRINK_ANIMATION;
            default -> null;
        };
    }

    static List<String> resolveItemAnimationKeys(String itemName, String activeHand, UseAction useAnim,
                                                 String handState) {
        List<String> animationKeys = new ArrayList<>();
        animationKeys.add(buildItemAnimationKey(itemName, activeHand, handState));

        if (useAnim == UseAction.BOW) {
            String alternateHand = "Right".equals(activeHand) ? "Left" : "Right";
            animationKeys.add(buildItemAnimationKey(itemName, alternateHand, handState));
        }

        return animationKeys;
    }

    private static String buildItemAnimationKey(String itemName, String activeHand, String handState) {
        return String.format("itemActive_%s_%s_%s", itemName, activeHand, handState);
    }

    private static String getItemId(ItemStack itemStack) {
        String descriptionId = itemStack.getItem().getTranslationKey();
        int dotIndex = descriptionId.indexOf('.');
        return dotIndex >= 0 ? descriptionId.substring(dotIndex + 1) : descriptionId;
    }
}
