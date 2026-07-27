package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.animation.AnimationTracker;
import com.elfmcys.yesstevemodel.client.compat.firstperson.FirstPersonCompat;
import com.elfmcys.yesstevemodel.client.compat.oculus.OculusCompat;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.util.AnimatableCacheUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;

import java.util.concurrent.ExecutionException;

/**
 * Port of upstream {@code client/renderer/ModelPreviewRenderer} (1.20.1).
 *
 * <p>Three independent preview paths, all bracketed by the mode flags other renderers query:
 * <ul>
 *   <li>{@link #renderEntityPreview} — the animation-test screen's model, with pose-specific
 *       adjustments (sleep/swim/sneak/sit/ride/boat) and optional ground/bed/vehicle props;</li>
 *   <li>{@link #renderLivingEntityPreview} — the model-picker/texture-picker screens' rotating
 *       preview, with optional equipment hiding;</li>
 *   <li>{@link #renderPlayerOverlay} — the paper-doll overlay.</li>
 * </ul>
 *
 * <p>Translation notes (1.20.1 -> 1.16.5):
 * <ul>
 *   <li>{@code RenderSystem.getModelViewStack()} + {@code applyModelViewMatrix()} (1.17+) -> the
 *       legacy matrix pair {@code RenderSystem.pushMatrix/popMatrix/translatef/scalef}, copied from
 *       {@code InventoryScreen.drawEntityOnScreen}, which is this version's own inventory preview.</li>
 *   <li>{@code Lighting.setupForEntityInInventory/setupFor3DItems} ->
 *       {@code RenderHelper.setupGui3DDiffuseLighting} (1.16.5 has one GUI lighting setup).</li>
 *   <li>{@code com.mojang.math.Axis.*P.rotationDegrees} -> {@code Vector3f.*P.rotationDegrees};
 *       {@code Quaternionf#mul} -> {@code Quaternion#multiply}.</li>
 *   <li>{@code EntityRenderDispatcher} -> {@code EntityRendererManager} ({@code getRenderManager},
 *       {@code setCameraOrientation}, {@code renderEntityStatic}); {@code MultiBufferSource} ->
 *       {@code IRenderTypeBuffer.Impl} ({@code getRenderTypeBuffers().getBufferSource()},
 *       {@code finish()}).</li>
 *   <li>Entity rotations: {@code yBodyRot/yBodyRotO} -> {@code renderYawOffset/prevRenderYawOffset},
 *       {@code getYRot/yRotO} -> {@code rotationYaw/prevRotationYaw}, {@code getXRot/xRotO} ->
 *       {@code rotationPitch/prevRotationPitch}, {@code yHeadRot/yHeadRotO} ->
 *       {@code rotationYawHead/prevRotationYawHead}; {@code entity.level()} -> {@code entity.world}.</li>
 *   <li>{@code renderPlayerOverlay} loses its {@code GuiGraphics} parameter: the pose stack is passed
 *       in and the buffer source is taken from {@code Minecraft} directly (upstream read both off the
 *       {@code GuiGraphics}).</li>
 * </ul>
 *
 * <p>One deliberate fix over upstream: {@link #renderLivingEntityPreview}'s equipment hiding saved
 * each slot <em>after</em> clearing it, so the restore pass wrote empties back — opening the picker
 * with {@code hideEquipment} would delete the player's gear. The save now happens before the clear.
 *
 * <p>Still stubbed: {@link #renderVehicleModel}. Its only caller is upstream's
 * {@code EntityRenderDispatcherMixin} (the YSM-vehicle takeover of vanilla vehicle rendering), which
 * is an unported seam, and it additionally needs the vehicle capability's resolved passenger bone
 * chains ({@code AnimatedGeoModel#passengerGroupChains}), which the local built {@code GeoModel} does
 * not carry. Both arrive with the vehicle seam, not here.
 */
public final class ModelPreviewRenderer {

    private static boolean isPreviewMode = false;

    private static boolean isExtraPlayerMode = false;

    private static boolean isFirstPersonMode = false;

    public static void setPreviewMode(boolean previewMode) {
        isPreviewMode = previewMode;
    }

    public static boolean isPreview() {
        return isPreviewMode;
    }

    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        isExtraPlayerMode = extraPlayerMode;
    }

    public static boolean isExtraPlayer() {
        return isExtraPlayerMode;
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        isFirstPersonMode = firstPersonMode;
    }

    public static boolean isFirstPerson() {
        return isFirstPersonMode || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertThread(RenderSystem::isOnRenderThread);
        return isFirstPersonMode && !FirstPersonCompat.isFirstPersonActive();
    }

    public static void renderVehicleModel(Entity entity, MatrixStack poseStack, float partialTick) {
        // PORT-REVIEW: needs the EntityRenderDispatcherMixin seam plus the vehicle capability's
        // resolved passenger bone chains (AnimatedGeoModel#passengerGroupChains); neither is ported.
    }

    /** The animation-test screen's model. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick,
                                           AnimatableEntity animatableEntity, GeoReplacedEntityRenderer renderer,
                                           boolean renderGround) {
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 1250.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);

        MatrixStack poseStack = new MatrixStack();
        poseStack.translate(0.0D, 0.0D, 1000.0D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, 0.8D, 0.0D);

        Quaternion rotationZ = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion rotationX = Vector3f.XP.rotationDegrees(-10.0F + pitch);
        rotationZ.multiply(rotationX);
        poseStack.rotate(rotationZ);

        float oldBodyRot = livingEntity.renderYawOffset;
        float oldBodyRotO = livingEntity.prevRenderYawOffset;
        float oldYRot = livingEntity.rotationYaw;
        float oldYRotO = livingEntity.prevRotationYaw;
        float oldXRot = livingEntity.rotationPitch;
        float oldXRotO = livingEntity.prevRotationPitch;
        float oldHeadRotO = livingEntity.prevRotationYawHead;
        float oldHeadRot = livingEntity.rotationYawHead;
        Pose oldPose = livingEntity.getPose();
        livingEntity.renderYawOffset = -yaw;
        livingEntity.prevRenderYawOffset = -yaw;
        livingEntity.rotationYaw = 180.0F;
        livingEntity.prevRotationYaw = 180.0F;
        livingEntity.rotationPitch = 0.0F;
        livingEntity.prevRotationPitch = 0.0F;
        livingEntity.rotationYawHead = -yaw;
        livingEntity.prevRotationYawHead = -yaw;

        RenderHelper.setupGui3DDiffuseLighting();
        EntityRendererManager entityRendererManager = Minecraft.getInstance().getRenderManager();
        rotationX.conjugate();
        entityRendererManager.setCameraOrientation(rotationX);
        entityRendererManager.setRenderShadow(false);
        IRenderTypeBuffer.Impl bufferSource = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();

        RenderSystem.runAsFancy(() -> {
            AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();
            if (animationTracker.isCurrentAnimation("sleep")) {
                poseStack.rotate(Vector3f.YP.rotationDegrees(yaw - 90.0F));
                poseStack.translate(0.5D, 0.5625D, 0.0D);
                livingEntity.setPose(Pose.SLEEPING);
            }
            if (animationTracker.isCurrentAnimation("swim") || animationTracker.isCurrentAnimation("swim_stand")) {
                livingEntity.setPose(Pose.SWIMMING);
            }
            if (animationTracker.isCurrentAnimation("sneak") || animationTracker.isCurrentAnimation("sneaking")) {
                livingEntity.setPose(Pose.CROUCHING);
            }
            if (animationTracker.isCurrentAnimation("sit")) {
                poseStack.translate(0.0D, -0.5D, 0.0D);
            }
            if (animationTracker.isCurrentAnimation("ride")) {
                poseStack.translate(0.0D, 0.85D, 0.0D);
            }
            if (animationTracker.isCurrentAnimation("ride_pig")) {
                poseStack.translate(0.0D, 0.3125D, 0.0D);
            }
            if (animationTracker.isCurrentAnimation("boat")) {
                poseStack.translate(0.0D, -0.45D, 0.0D);
            }
            try {
                renderVehicleForAnimation(yaw, animatableEntity, partialTick, poseStack, entityRendererManager,
                        bufferSource);
                if (animationTracker.isCurrentAnimation("sleep")) {
                    renderBedPreview(scale, pitch, yaw, bufferSource);
                }
                if (renderGround) {
                    renderGroundPreview(scale, pitch, yaw, bufferSource);
                }
                bufferSource.finish();
                renderer.renderEntity((LivingAnimatable) animatableEntity, 0.0F, partialTick, poseStack,
                        bufferSource, 15728880);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        bufferSource.finish();
        entityRendererManager.setRenderShadow(true);
        livingEntity.renderYawOffset = oldBodyRot;
        livingEntity.prevRenderYawOffset = oldBodyRotO;
        livingEntity.rotationYaw = oldYRot;
        livingEntity.prevRotationYaw = oldYRotO;
        livingEntity.rotationPitch = oldXRot;
        livingEntity.prevRotationPitch = oldXRotO;
        livingEntity.prevRotationYawHead = oldHeadRotO;
        livingEntity.rotationYawHead = oldHeadRot;
        livingEntity.setPose(oldPose);

        RenderSystem.popMatrix();
        RenderHelper.setupGui3DDiffuseLighting();
        setPreviewMode(false);
    }

    private static void renderBedPreview(float scale, float pitch, float yaw, IRenderTypeBuffer.Impl bufferSource) {
        MatrixStack poseStack = new MatrixStack();
        poseStack.translate(0.0D, 0.0D, 1000.0D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, 0.8D, 0.0D);
        Quaternion rotationZ = Vector3f.ZP.rotationDegrees(180.0F);
        rotationZ.multiply(Vector3f.XP.rotationDegrees(-10.0F + pitch));
        poseStack.rotate(rotationZ);
        poseStack.rotate(Vector3f.YP.rotationDegrees(yaw + 180.0F));
        poseStack.translate(-0.5D, 0.0D, 0.5D);
        Minecraft.getInstance().getBlockRendererDispatcher().renderBlock(Blocks.RED_BED.getDefaultState(), poseStack,
                bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderGroundPreview(float scale, float pitch, float yaw, IRenderTypeBuffer.Impl bufferSource) {
        MatrixStack poseStack = new MatrixStack();
        poseStack.translate(0.0D, 0.0D, 1000.0D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, 0.8D, 0.0D);
        Quaternion rotationZ = Vector3f.ZP.rotationDegrees(180.0F);
        rotationZ.multiply(Vector3f.XP.rotationDegrees(-10.0F + pitch));
        poseStack.rotate(rotationZ);
        poseStack.rotate(Vector3f.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5D, -1.0D, -2.5D);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0F, 0.0F, 1.0F);
                Minecraft.getInstance().getBlockRendererDispatcher().renderBlock(Blocks.GRASS_BLOCK.getDefaultState(),
                        poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
            }
            poseStack.translate(1.0F, 0.0F, -3.0F);
        }

        poseStack.translate(-1.0F, 1.0F, 1.0F);
        Minecraft.getInstance().getBlockRendererDispatcher().renderBlock(Blocks.GRASS.getDefaultState(), poseStack,
                bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
        poseStack.translate(0.0F, 0.0F, 1.0F);
        Minecraft.getInstance().getBlockRendererDispatcher().renderBlock(Blocks.RED_TULIP.getDefaultState(), poseStack,
                bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderVehicleForAnimation(float yaw, AnimatableEntity<?> animatableEntity, float partialTick,
                                                  MatrixStack poseStack, EntityRendererManager entityRendererManager,
                                                  IRenderTypeBuffer.Impl bufferSource) throws ExecutionException {
        Entity entity = animatableEntity.getEntity();
        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();

        if (animationTracker.isCurrentAnimation("ride")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRendererManager, bufferSource,
                    AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.HORSE),
                            () -> EntityType.HORSE.create(entity.world)), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRendererManager, bufferSource,
                    AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.PIG),
                            () -> EntityType.PIG.create(entity.world)), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRendererManager, bufferSource,
                    AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.BOAT),
                            () -> EntityType.BOAT.create(entity.world)), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, MatrixStack poseStack,
                                            EntityRendererManager entityRendererManager,
                                            IRenderTypeBuffer.Impl bufferSource, Entity vehicleEntity,
                                            float partialTick) {
        poseStack.push();
        poseStack.rotate(Vector3f.YP.rotationDegrees(yaw));
        entityRendererManager.renderEntityStatic(vehicleEntity, 0.0D,
                -vehicleEntity.getMountedYOffset() - riderEntity.getYOffset(), 0.0D, 0.0F, partialTick, poseStack,
                bufferSource, 15728880);
        poseStack.pop();
    }

    /** The model-picker / texture-picker screens' preview. */
    public static <T extends LivingEntity, TAnimatable extends LivingAnimatable<T>> void renderLivingEntityPreview(
            float x, float y, float scale, float partialTick, TAnimatable animatable,
            GeoReplacedEntityRenderer<T, TAnimatable> renderer, boolean disablePreviewRotation,
            boolean hideEquipment) {
        ItemStack[] savedEquipment;
        setPreviewMode(true);
        LivingEntity livingEntity = animatable.getEntity();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);

        MatrixStack poseStack = new MatrixStack();
        poseStack.translate(0.0D, disablePreviewRotation ? 5.5D : 0.0D, 1000.0D);
        poseStack.scale(scale, scale, scale);
        Quaternion rotationZ = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion rotationX = Vector3f.XP.rotationDegrees(disablePreviewRotation ? 0.0F : -10.0F);
        rotationZ.multiply(rotationX);
        poseStack.rotate(rotationZ);

        float oldBodyRot = livingEntity.renderYawOffset;
        float oldBodyRotO = livingEntity.prevRenderYawOffset;
        float oldYRot = livingEntity.rotationYaw;
        float oldYRotO = livingEntity.prevRotationYaw;
        float oldXRot = livingEntity.rotationPitch;
        float oldXRotO = livingEntity.prevRotationPitch;
        float oldHeadRotO = livingEntity.prevRotationYawHead;
        float oldHeadRot = livingEntity.rotationYawHead;
        if (hideEquipment && livingEntity instanceof PlayerEntity) {
            PlayerEntity player = (PlayerEntity) livingEntity;
            savedEquipment = new ItemStack[EquipmentSlotType.values().length];
            int slotIndex = 0;
            for (EquipmentSlotType equipmentSlot : EquipmentSlotType.values()) {
                // Save BEFORE clearing: upstream saved after, which restored empties and deleted the
                // player's gear every time the picker was opened with hideEquipment.
                savedEquipment[slotIndex] = player.getItemStackFromSlot(equipmentSlot);
                if (equipmentSlot == EquipmentSlotType.MAINHAND) {
                    player.inventory.mainInventory.set(player.inventory.currentItem, ItemStack.EMPTY);
                } else if (equipmentSlot == EquipmentSlotType.OFFHAND) {
                    player.inventory.offHandInventory.set(0, ItemStack.EMPTY);
                } else {
                    NonNullList<ItemStack> armorList = player.inventory.armorInventory;
                    if (armorList.size() > equipmentSlot.getIndex()) {
                        armorList.set(equipmentSlot.getIndex(), ItemStack.EMPTY);
                    }
                }
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        float previewYaw = disablePreviewRotation ? 180.0F : 200.0F;
        livingEntity.renderYawOffset = previewYaw;
        livingEntity.prevRenderYawOffset = previewYaw;
        livingEntity.rotationYaw = previewYaw;
        livingEntity.prevRotationYaw = previewYaw;
        livingEntity.rotationPitch = 0.0F;
        livingEntity.prevRotationPitch = 0.0F;
        livingEntity.rotationYawHead = livingEntity.rotationYaw;
        livingEntity.prevRotationYawHead = livingEntity.rotationYaw;

        Entity vehicle = livingEntity.getRidingEntity();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.rotationYaw;
            poseStack.rotate(Vector3f.YP.rotationDegrees(vehicleYaw - previewYaw));
            livingEntity.rotationYawHead = vehicleYaw;
            livingEntity.prevRotationYawHead = vehicleYaw;
        }

        RenderHelper.setupGui3DDiffuseLighting();
        EntityRendererManager entityRendererManager = Minecraft.getInstance().getRenderManager();
        rotationX.conjugate();
        entityRendererManager.setCameraOrientation(rotationX);
        entityRendererManager.setRenderShadow(false);
        IRenderTypeBuffer.Impl bufferSource = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();

        RenderSystem.runAsFancy(() -> renderer.renderEntity(animatable, 0.0F, partialTick, poseStack, bufferSource,
                15728880));

        bufferSource.finish();
        entityRendererManager.setRenderShadow(true);
        livingEntity.renderYawOffset = oldBodyRot;
        livingEntity.prevRenderYawOffset = oldBodyRotO;
        livingEntity.rotationYaw = oldYRot;
        livingEntity.prevRotationYaw = oldYRotO;
        livingEntity.rotationPitch = oldXRot;
        livingEntity.prevRotationPitch = oldXRotO;
        livingEntity.prevRotationYawHead = oldHeadRotO;
        livingEntity.rotationYawHead = oldHeadRot;
        if (savedEquipment != null) {
            PlayerEntity player = (PlayerEntity) livingEntity;
            int slotIndex = 0;
            for (EquipmentSlotType equipmentSlot : EquipmentSlotType.values()) {
                ItemStack itemStack = savedEquipment[slotIndex];
                if (equipmentSlot == EquipmentSlotType.MAINHAND) {
                    player.inventory.mainInventory.set(player.inventory.currentItem, itemStack);
                } else if (equipmentSlot == EquipmentSlotType.OFFHAND) {
                    player.inventory.offHandInventory.set(0, itemStack);
                } else {
                    NonNullList<ItemStack> armorList = player.inventory.armorInventory;
                    if (armorList.size() > equipmentSlot.getIndex()) {
                        armorList.set(equipmentSlot.getIndex(), itemStack);
                    }
                }
                slotIndex++;
            }
        }

        RenderSystem.popMatrix();
        RenderHelper.setupGui3DDiffuseLighting();
        setPreviewMode(false);
    }

    /** Paper-doll overlay. Upstream's {@code GuiGraphics} parameter splits into the pose stack here. */
    public static void renderPlayerOverlay(MatrixStack matrixStack, ClientPlayerEntity localPlayer, double x, double y,
                                           float scale, float yawOffset, int zDepth, float partialTick) {
        setExtraPlayerMode(true);
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) (x + (scale * 0.5D)), (float) (y + (scale * 2.0F)), 0.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);

        matrixStack.push();
        matrixStack.translate(0.0F, 0.0F, -zDepth);
        matrixStack.scale(scale, scale, scale);

        Quaternion rotationZ = Vector3f.ZP.rotationDegrees(180.1F);
        Quaternion rotationY = Vector3f.YP.rotationDegrees(
                (MathHelper.lerp(partialTick, localPlayer.prevRenderYawOffset, localPlayer.renderYawOffset)
                        + yawOffset) - 180.0F);
        rotationZ.multiply(rotationY);
        matrixStack.rotate(rotationZ);

        RenderHelper.setupGui3DDiffuseLighting();
        EntityRendererManager entityRendererManager = Minecraft.getInstance().getRenderManager();
        rotationY.conjugate();
        entityRendererManager.setCameraOrientation(rotationY);
        entityRendererManager.setRenderShadow(false);
        IRenderTypeBuffer.Impl bufferSource = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();

        RenderSystem.runAsFancy(() -> entityRendererManager.renderEntityStatic(localPlayer, 0.0D, 0.0D, 0.0D, 0.0F,
                partialTick, matrixStack, bufferSource, 15728880));

        bufferSource.finish();
        entityRendererManager.setRenderShadow(true);
        matrixStack.pop();
        RenderSystem.popMatrix();
        RenderHelper.setupGui3DDiffuseLighting();
        setExtraPlayerMode(false);
    }
}
