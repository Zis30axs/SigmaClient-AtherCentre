package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.player.runtime.EntityAnimState;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * 通用实体动画状态解析器。
 */
public final class EntityAnimationResolver {

    private EntityAnimationResolver() {
    }

    public static void resolve(Entity entity, ManagedModel model,
                                float entityYaw, float tickDelta, MutableRenderPose params) {

        if (entity instanceof LivingEntity living) {
            params.bodyYaw = MathHelper.interpolateAngle(tickDelta, living.prevRenderYawOffset, living.renderYawOffset);
        } else {
            params.bodyYaw = entityYaw;
        }
        params.bodyPitch = 0.0f;
        params.translation.zero();

        if (entity instanceof LivingEntity living) {
            if (living.getHealth() <= 0.0f) {
                changeAnimOnce(model, EntityAnimState.State.Die, 0);
                return;
            }
            if (living.isSleeping()) {
                params.bodyYaw = living.getBedDirection().getHorizontalAngle() + 180.0f;
                params.bodyPitch = model.renderProperties().sleepingPitch();
                params.translation.set(model.renderProperties().sleepingTranslation());
                changeAnimOnce(model, EntityAnimState.State.Sleep, 0);
                return;
            }
        }

        boolean hasMovement = entity.getPosX() - entity.prevPosX != 0.0f
                           || entity.getPosZ() - entity.prevPosZ != 0.0f;

        if (entity.isBeingRidden() && hasMovement) {
            changeAnimOnce(model, EntityAnimState.State.Driven, 0);
        } else if (entity.isBeingRidden()) {
            changeAnimOnce(model, EntityAnimState.State.Ridden, 0);
        } else if (entity.isSwimming()) {
            changeAnimOnce(model, EntityAnimState.State.Swim, 0);
        } else if (hasMovement && entity.getRidingEntity() == null) {
            changeAnimOnce(model, EntityAnimState.State.Walk, 0);
        } else {
            changeAnimOnce(model, EntityAnimState.State.Idle, 0);
        }
    }

    private static void changeAnimOnce(ManagedModel model,
                                        EntityAnimState.State targetState, int layer) {
        if (model.entityState().stateLayers[layer] != targetState) {
            model.entityState().stateLayers[layer] = targetState;
            String property = EntityAnimState.getPropertyName(targetState);
            model.modelInstance().changeAnim(model.animationLibrary().animation(property), layer);
        }
    }
}
