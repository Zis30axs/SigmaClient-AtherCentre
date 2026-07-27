package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.AnimationControllerContext;
import com.elfmcys.yesstevemodel.audio.PlaybackFlags;
import com.elfmcys.yesstevemodel.client.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.client.entity.PlayerEntityFrameState;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.query.*;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.util.MolangUtils;
import com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Optional;

public class QueryBinding extends ContextBinding {

    public static final QueryBinding INSTANCE = new QueryBinding();

    private QueryBinding() {
        function("debug_output", new DebugOut());
        function("biome_has_all_tags", new BiomeHasAllTags());
        function("biome_has_any_tag", new BiomeHasAnyTag());
        function("relative_block_has_all_tags", new RelativeBlockHasAllTags());
        function("relative_block_has_any_tag", new RelativeBlockHasAnyTag());
        function("is_item_name_any", new IsItemNameAny());
        function("equipped_item_all_tags", new EquippedItemAllTags());
        function("equipped_item_any_tag", new EquipmentItemAnyTag());
        function("position", new Position());
        function("position_delta", new PositionDelta());
        function("rotation_to_camera", new RotationToCamera());

        function("max_durability", new MaxDurability());
        function("remaining_durability", new RemainingDurability());

        var("actor_count", ctx -> com.google.common.collect.Iterables.size(ctx.level().getAllEntities()));

        var("anim_time", ctx -> animationControllerContext(ctx).map(AnimationControllerContext::animTime).orElse(0.0f));
        var("all_animations_finished", ctx -> getPlaybackFlags(ctx).map(PlaybackFlags::isPaused).orElse(false));
        var("any_animation_finished", ctx -> getPlaybackFlags(ctx).map(PlaybackFlags::isStopped).orElse(false));
        var("life_time", ctx -> ctx.geoInstance().getSeekTime() / 20.0d);
        var("head_x_rotation", ctx -> ctx.data().netHeadYaw);
        var("head_y_rotation", ctx -> ctx.data().headPitch);
        var("moon_phase", ctx -> ctx.level().getMoonPhase());
        var("time_of_day", ctx -> MolangUtils.normalizeTime(ctx.level().getDayTime()));
        var("time_stamp", ctx -> ctx.level().getDayTime());
        var("delta_time", ctx -> ctx.geoInstance().getPositionTracker().getTimeDelta() / 20.0f);

        entityVar("yaw_speed", QueryBinding::getYawSpeed);
        entityVar("cardinal_facing_2d", ctx -> ctx.entity().getHorizontalFacing().getIndex());
        entityVar("distance_from_camera", ctx -> ctx.mc().gameRenderer.getActiveRenderInfo().getProjectedView().distanceTo(ctx.entity().getPositionVec()));
        entityVar("eye_target_x_rotation", ctx -> MathHelper.lerp(ctx.animationEvent().getPartialTick(), ctx.entity().prevRotationPitch, ctx.entity().rotationPitch));
        entityVar("eye_target_y_rotation", ctx -> MathHelper.lerp(ctx.animationEvent().getPartialTick(), ctx.entity().prevRotationYaw, ctx.entity().rotationYaw));
        entityVar("ground_speed", ctx -> getGroundSpeed(ctx.entity()));
        entityVar("modified_distance_moved", ctx -> ctx.entity().distanceWalkedModified);
        entityVar("vertical_speed", QueryBinding::getVerticalSpeed);
        entityVar("walk_distance", ctx -> ctx.entity().distanceWalkedOnStepModified);
        entityVar("has_rider", ctx -> ctx.entity().isBeingRidden());
        entityVar("is_first_person", ctx -> CameraUtil.getCameraType(ctx) == PointOfView.FIRST_PERSON.ordinal());
        entityVar("is_in_water", ctx -> ctx.entity().isInWater());
        entityVar("is_in_water_or_rain", ctx -> ctx.entity().isInWaterRainOrBubbleColumn());
        entityVar("is_on_fire", ctx -> ctx.entity().isBurning());
        entityVar("is_on_ground", ctx -> ctx.entity().onGround);
        entityVar("is_riding", ctx -> ctx.entity().isPassenger());
        entityVar("is_sneaking", ctx -> ctx.entity().onGround && ctx.entity().getPose() == Pose.CROUCHING);
        entityVar("is_spectator", ctx -> ctx.entity().isSpectator());
        entityVar("is_sprinting", ctx -> ctx.entity().isSprinting());
        entityVar("is_swimming", ctx -> ctx.entity().isSwimming());

        livingEntityVar("body_x_rotation", ctx -> MathHelper.lerp(ctx.animationEvent().getPartialTick(), ctx.entity().prevRotationPitch, ctx.entity().rotationPitch));
        livingEntityVar("body_y_rotation", ctx -> MathHelper.wrapDegrees(MathHelper.lerp(ctx.animationEvent().getPartialTick(), ctx.entity().prevRenderYawOffset, ctx.entity().renderYawOffset)));
        livingEntityVar("health", QueryBinding::getHealth);
        livingEntityVar("max_health", QueryBinding::getMaxHealth);
        livingEntityVar("hurt_time", ctx -> ctx.entity().hurtTime);
        livingEntityVar("is_eating", ctx -> ctx.entity().getActiveItemStack().getUseAction() == UseAction.EAT);
        livingEntityVar("is_playing_dead", ctx -> ctx.entity().getHealth() <= 0);
        livingEntityVar("is_sleeping", ctx -> ctx.entity().isSleeping());
        livingEntityVar("is_using_item", ctx -> ctx.entity().isHandActive());
        livingEntityVar("item_in_use_duration", ctx -> ctx.entity().getItemInUseCount() / 20.0d);
        livingEntityVar("item_max_use_duration", ctx -> getItemMaxUseDuration(ctx.entity()) / 20.0d);
        livingEntityVar("item_remaining_use_duration", ctx -> ctx.entity().getItemInUseMaxCount() / 20.0d);
        livingEntityVar("equipment_count", ctx -> getEquipmentCount(ctx.entity()));

        playerEntityVar("cape_flap_amount", QueryBinding::getCapeFlapAmount);
        playerEntityVar("player_level", QueryBinding::getPlayerLevel);
        playerEntityVar("is_jumping", ctx -> !isFlying(ctx) && !ctx.entity().isPassenger() && !ctx.entity().onGround && !ctx.entity().isInWater());

        clientPlayerEntityVar("has_cape", ctx -> hasCape(ctx.entity()));
    }

    private static Optional<AnimationControllerContext> animationControllerContext(IContext<?> context) {
        return Optional.ofNullable(context.animationControllerContext());
    }

    private static Optional<PlaybackFlags> getPlaybackFlags(IContext<?> context) {
        return Optional.ofNullable(context.getPlaybackFlags());
    }

    private static boolean isFlying(IContext<PlayerEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isClientPlayerEntityModel()) {
                return playerCapability.getPositionTracker().isFlying();
            }
        }
        return context.entity().abilities.isFlying;
    }

    private static int getPlayerLevel(IContext<PlayerEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isClientPlayerEntityModel()) {
                return playerCapability.getPositionTracker().getExperienceLevel();
            }
        }
        return context.entity().experienceLevel;
    }

    private static Object getHealth(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isClientPlayerEntityModel()) {
                return playerCapability.getPositionTracker().getHealth();
            }
        }
        return context.entity().getHealth();
    }

    private static Object getMaxHealth(IContext<LivingEntity> context) {
        AnimatableEntity<?> abstractC0235x5da32a01Mo322x83eb685f = context.geoInstance();
        if (abstractC0235x5da32a01Mo322x83eb685f instanceof PlayerCapability playerCapability) {
            if (!playerCapability.isClientPlayerEntityModel()) {
                return playerCapability.getPositionTracker().getMaxHealth();
            }
        }
        return context.entity().getMaxHealth();
    }

    private static boolean hasCape(AbstractClientPlayerEntity abstractClientPlayer) {
        return abstractClientPlayer.hasPlayerInfo() && !abstractClientPlayer.isInvisible() && abstractClientPlayer.isWearing(PlayerModelPart.CAPE) && abstractClientPlayer.getLocationCape() != null;
    }

    private static int getEquipmentCount(LivingEntity entity) {
        int i = 0;
        for (EquipmentSlotType equipmentSlot : EquipmentSlotType.values()) {
            if (equipmentSlot.getSlotType() == EquipmentSlotType.Group.ARMOR && !CosmeticArmorHelper.getArmorItem(entity, equipmentSlot).isEmpty()) {
                i++;
            }
        }
        return i;
    }

    private static int getItemMaxUseDuration(LivingEntity entity) {
        ItemStack useItem = entity.getActiveItemStack();
        if (useItem.isEmpty()) {
            return 0;
        }
        return useItem.getUseDuration();
    }

    private static float getYawSpeed(IContext<Entity> context) {
        if (context.entity() instanceof ClientPlayerEntity) {
            return PlayerEntityFrameState.getHeadYawDelta();
        }
        return 20.0f * (context.entity().rotationYaw - context.entity().prevRotationYaw);
    }

    private static float getGroundSpeed(Entity entity) {
        Vector3d deltaMovement = entity.getMotion();
        return 20.0f * MathHelper.sqrt((float) ((deltaMovement.x * deltaMovement.x) + (deltaMovement.z * deltaMovement.z)));
    }

    private static float getVerticalSpeed(IContext<Entity> context) {
        EntityFrameStateTracker<?> positionTracker = context.geoInstance().getPositionTracker();
        return (20.0f * ((float) positionTracker.getPositionDelta().y)) / positionTracker.getTimeDelta();
    }

    private static float getCapeFlapAmount(IContext<PlayerEntity> context) {
        float gameTime = context.animationEvent().getPartialTick();
        PlayerEntity PlayerEntity = context.entity();
        float fLerp = (float) (MathHelper.lerp(gameTime, PlayerEntity.prevChasingPosX, PlayerEntity.chasingPosX) - MathHelper.lerp(gameTime, PlayerEntity.prevPosX, PlayerEntity.getPosX()));
        float fLerp2 = (float) (MathHelper.lerp(gameTime, PlayerEntity.prevChasingPosY, PlayerEntity.chasingPosY) - MathHelper.lerp(gameTime, PlayerEntity.prevPosY, PlayerEntity.getPosY()));
        float fLerp3 = (float) (MathHelper.lerp(gameTime, PlayerEntity.prevChasingPosZ, PlayerEntity.chasingPosZ) - MathHelper.lerp(gameTime, PlayerEntity.prevPosZ, PlayerEntity.getPosZ()));
        float f = PlayerEntity.prevRenderYawOffset + (PlayerEntity.renderYawOffset - PlayerEntity.prevRenderYawOffset);
        float fSin = MathHelper.sin(f * 0.017453292f);
        float f2 = -MathHelper.cos(f * 0.017453292f);
        float fClamp = MathHelper.clamp(fLerp2 * 10.0f, -6.0f, 32.0f);
        float fClamp2 = MathHelper.clamp(((fLerp * fSin) + (fLerp3 * f2)) * 100.0f, 0.0f, 150.0f);
        if (fClamp2 < 0.0f) {
            fClamp2 = 0.0f;
        }
        float fSin2 = fClamp + (MathHelper.sin(MathHelper.lerp(gameTime, PlayerEntity.prevDistanceWalkedModified, PlayerEntity.distanceWalkedModified) * 6.0f) * 32.0f * MathHelper.lerp(gameTime, PlayerEntity.prevCameraYaw, PlayerEntity.cameraYaw));
        if (PlayerEntity.isCrouching()) {
            fSin2 += 25.0f;
        }
        return MathHelper.clamp(((6.0f + (fClamp2 / 2.0f)) + fSin2) / 108.0f, 0.0f, 1.0f);
    }
}