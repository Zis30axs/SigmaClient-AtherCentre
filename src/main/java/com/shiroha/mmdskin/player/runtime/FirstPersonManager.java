package com.shiroha.mmdskin.player.runtime;

import com.shiroha.mmdskin.bridge.runtime.NativeModelPort;
import com.shiroha.mmdskin.config.RuntimeConfigPortHolder;
import com.shiroha.mmdskin.player.port.VrRuntimePort;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** 鏂囦欢鑱岃矗锛氱淮鎶ゆ湰鍦扮涓€浜虹О涓?VR 瑙嗚鐩稿叧鐨勬ā鍨嬬姸鎬併€?*/
public final class FirstPersonManager {
    private static final Logger logger = LogManager.getLogger();
    private static final float MODEL_SCALE = 0.09f;

    private static final NativeModelPort NOOP_MODEL_PORT = new NativeModelPort() {
        @Override public boolean setLayerBoneMask(long h, int l, String b) { return false; }
        @Override public boolean setLayerBoneExclude(long h, int l, String b) { return false; }
        @Override public long getModelMemoryUsage(long h) { return 0L; }
        @Override public void setFirstPersonMode(long h, boolean e) {}
        @Override public void getEyeBonePosition(long h, float[] o) {}
        @Override public void applyVrTrackingInput(long h, float[] d) {}
        @Override public void setVrEnabled(long h, boolean e) {}
        @Override public void setVrIkParams(long h, float s) {}
        @Override public int getMaterialCount(long h) { return 0; }
        @Override public void setMaterialVisible(long h, int i, boolean v) {}
        @Override public void setAllMaterialsVisible(long h, boolean v) {}
        @Override public void deleteModel(long h) {}
    };

    private static volatile NativeModelPort modelPort = NOOP_MODEL_PORT;
    private static volatile VrRuntimePort vrRuntimePort = VrRuntimePort.noop();

    private static float cachedModelScale = 1.0f;
    private static long trackedModelHandle = 0;
    private static boolean activeDesktopFirstPerson = false;
    private static boolean activeVrEyeCamera = false;
    private static final float[] eyeBonePos = new float[3];
    private static boolean eyeBoneValid = false;

    private static Vector3d vrModelRootOffset = Vector3d.ZERO;
    private static boolean vrModelRootOffsetValid = false;
    private static Vector3d lastCameraPos = Vector3d.ZERO;

    private FirstPersonManager() {}

    public static void configureRuntimeCollaborators(NativeModelPort port) {
        modelPort = port != null ? port : NOOP_MODEL_PORT;
    }

    public static void configureVrRuntime(VrRuntimePort vrRuntimePort) {
        FirstPersonManager.vrRuntimePort = vrRuntimePort != null ? vrRuntimePort : VrRuntimePort.noop();
    }

    public static VrRuntimePort vrRuntime() {
        return vrRuntimePort;
    }

    public static void setLastCameraPos(Vector3d pos) {
        lastCameraPos = pos;
    }

    public static Vector3d getLastCameraPos() {
        return lastCameraPos;
    }

    public static boolean shouldRenderFirstPerson() {
        if (isLocalVrMmdModelActive()) return false;
        if (!RuntimeConfigPortHolder.get().isFirstPersonModelEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameSettings.getPointOfView() != PointOfView.FIRST_PERSON) return false;
        return mc.player != null && MmdSkinRendererPlayerHelper.isUsingMmdModel(mc.player);
    }

    public static void preRender(long modelHandle, float modelScale, boolean isLocalPlayer) {
        if (!isLocalPlayer) return;

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        activeVrEyeCamera = vrEyeCamera;

        // 鍒囨崲妯″瀷鏃跺厛鍏抽棴鏃фā鍨嬬殑绗竴浜虹О妯″紡
        if (modelHandle != trackedModelHandle) {
            if (trackedModelHandle != 0 && activeDesktopFirstPerson) {
                disableTrackedModel();
            }
            trackedModelHandle = modelHandle;
            activeDesktopFirstPerson = false;
        }

        // 浠呮闈㈢涓€浜虹О闇€瑕侀┍鍔?native SetFirstPersonMode
        if (desktopFirstPerson != activeDesktopFirstPerson) {
            try {
                modelPort.setFirstPersonMode(modelHandle, desktopFirstPerson);
            } catch (Exception e) {
                logger.warn("SetFirstPersonMode failed for model {}", modelHandle, e);
            }
            activeDesktopFirstPerson = desktopFirstPerson;
        }

        if (desktopFirstPerson || vrModelActive) {
            cachedModelScale = modelScale;
        }
    }

    public static void postRender(long modelHandle, PlayerEntity player, float tickDelta) {
        if (activeDesktopFirstPerson && modelHandle != 0) {
            try {
                modelPort.getEyeBonePosition(modelHandle, eyeBonePos);
                eyeBoneValid = (eyeBonePos[0] != 0.0f || eyeBonePos[1] != 0.0f || eyeBonePos[2] != 0.0f);
            } catch (Exception e) {
                logger.warn("GetEyeBonePosition failed for model {}", modelHandle, e);
                clearEyeBoneState();
            }
        } else {
            clearEyeBoneState();
        }
        updateVrModelRootOffset(player, tickDelta);
    }

    public static boolean isActive() {
        ensureActiveState();
        return activeDesktopFirstPerson;
    }

    public static boolean isEyeCameraActive() {
        ensureActiveState();
        return activeDesktopFirstPerson || activeVrEyeCamera;
    }

    public static boolean isVrEyeCameraActive() {
        ensureActiveState();
        return activeVrEyeCamera;
    }

    public static boolean isEyeBoneValid() {
        return eyeBoneValid;
    }

    public static Vector3d getLocalVrModelRootOffset(PlayerEntity player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.player == null || !minecraft.player.getUniqueID().equals(player.getUniqueID())) {
            return Vector3d.ZERO;
        }
        return vrModelRootOffsetValid ? vrModelRootOffset : Vector3d.ZERO;
    }

    public static void getEyeWorldOffset(float[] out) {
        float scale = MODEL_SCALE * cachedModelScale;
        out[0] = eyeBonePos[0] * scale;
        out[1] = eyeBonePos[1] * scale;
        out[2] = eyeBonePos[2] * scale;
    }

    public static Vector3d getRotatedEyePosition(Entity entity, float partialTick) {
        float[] eyeOffset = new float[3];
        getEyeWorldOffset(eyeOffset);
        Vector3d renderOrigin = entity instanceof PlayerEntity player
                ? fallbackRenderOrigin(player, partialTick)
                : new Vector3d(
                        MathHelper.lerp(partialTick, entity.prevPosX, entity.getPosX()),
                        MathHelper.lerp(partialTick, entity.prevPosY, entity.getPosY()),
                        MathHelper.lerp(partialTick, entity.prevPosZ, entity.getPosZ())
                );
        if (entity instanceof PlayerEntity player) {
            renderOrigin = renderOrigin.add(getLocalVrModelRootOffset(player));
        }
        double px = renderOrigin.x;
        double py = renderOrigin.y;
        double pz = renderOrigin.z;

        float bodyYaw = entity instanceof PlayerEntity player
                ? fallbackBodyYawDegrees(player, partialTick)
                : entity instanceof LivingEntity livingEntity
                ? MathHelper.interpolateAngle(partialTick, livingEntity.prevRenderYawOffset, livingEntity.renderYawOffset)
                : MathHelper.interpolateAngle(partialTick, entity.prevRotationYaw, entity.rotationYaw);
        float yawRad = (float) Math.toRadians(bodyYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double worldOffX = eyeOffset[0] * cosYaw - eyeOffset[2] * sinYaw;
        double worldOffZ = eyeOffset[0] * sinYaw + eyeOffset[2] * cosYaw;
        return new Vector3d(px + worldOffX, py + eyeOffset[1], pz + worldOffZ);
    }

    public static Vector3d getVanillaEyePosition(LivingEntity entity, float partialTick) {
        double px = MathHelper.lerp(partialTick, entity.prevPosX, entity.getPosX());
        double py = MathHelper.lerp(partialTick, entity.prevPosY, entity.getPosY()) + entity.getEyeHeight();
        double pz = MathHelper.lerp(partialTick, entity.prevPosZ, entity.getPosZ());
        return new Vector3d(px, py, pz);
    }

    public static boolean shouldUseVanillaReachValidation(LivingEntity entity) {
        if (!isActive() || !isEyeBoneValid()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUniqueID().equals(entity.getUniqueID())) return false;
        return mc.gameSettings.getPointOfView() == PointOfView.FIRST_PERSON;
    }

    public static Vector3d getVrCameraPosition(Entity entity, float partialTick) {
        if (!(entity instanceof PlayerEntity player)) {
            return getRotatedEyePosition(entity, partialTick);
        }
        Vector3d headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) {
            return getRotatedEyePosition(entity, partialTick);
        }
        return headRenderPos;
    }

    public static void reset() {
        disableTrackedModel();
        activeDesktopFirstPerson = false;
        activeVrEyeCamera = false;
        trackedModelHandle = 0;
        cachedModelScale = 1.0f;
        clearEyeBoneState();
        clearVrModelRootOffset();
        lastCameraPos = Vector3d.ZERO;
    }

    private static void ensureActiveState() {
        if (!activeDesktopFirstPerson && !activeVrEyeCamera) return;

        boolean desktopFirstPerson = shouldRenderFirstPerson();
        boolean vrModelActive = isLocalVrMmdModelActive();
        boolean vrEyeCamera = vrModelActive && isVrFirstPersonRequested() && vrRuntimePort.isLocalPlayerEyePass();
        if (desktopFirstPerson || vrEyeCamera) return;

        if (vrModelActive) {
            activeDesktopFirstPerson = false;
            activeVrEyeCamera = false;
            lastCameraPos = Vector3d.ZERO;
            return;
        }

        reset();
    }

    private static boolean isLocalVrMmdModelActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && vrRuntimePort.isLocalPlayerInVr()
                && MmdSkinRendererPlayerHelper.isUsingMmdModel(minecraft.player);
    }

    private static boolean isVrFirstPersonRequested() {
        return Minecraft.getInstance().gameSettings.getPointOfView() == PointOfView.FIRST_PERSON;
    }

    private static Vector3d fallbackRenderOrigin(PlayerEntity player, float tickDelta) {
        Vector3d renderOrigin = vrRuntimePort.getRenderOrigin(player, tickDelta);
        if (renderOrigin != null) return renderOrigin;
        return new Vector3d(
                MathHelper.lerp(tickDelta, player.prevPosX, player.getPosX()),
                MathHelper.lerp(tickDelta, player.prevPosY, player.getPosY()),
                MathHelper.lerp(tickDelta, player.prevPosZ, player.getPosZ())
        );
    }

    private static float fallbackBodyYawDegrees(PlayerEntity player, float tickDelta) {
        float bodyYaw = vrRuntimePort.getBodyYawDegrees(player, tickDelta);
        if (Float.isFinite(bodyYaw)) return bodyYaw;
        return MathHelper.interpolateAngle(tickDelta, player.prevRenderYawOffset, player.renderYawOffset);
    }

    private static void updateVrModelRootOffset(PlayerEntity player, float tickDelta) {
        if (player == null || !eyeBoneValid || !isLocalVrMmdModelActive()) return;
        Vector3d headRenderPos = vrRuntimePort.getWorldRenderHeadPosition(player);
        if (headRenderPos == null) return;
        Vector3d avatarEyePos = getRotatedEyePosition(player, tickDelta);
        double correctedY = MathHelper.clamp(vrModelRootOffset.y + (headRenderPos.y - avatarEyePos.y), -2.5d, 2.5d);
        vrModelRootOffset = new Vector3d(0.0d, correctedY, 0.0d);
        vrModelRootOffsetValid = true;
    }

    private static void disableTrackedModel() {
        if (!activeDesktopFirstPerson || trackedModelHandle == 0) return;
        try {
            modelPort.setFirstPersonMode(trackedModelHandle, false);
        } catch (Exception e) {
            logger.warn("Failed to disable first-person mode for model {}", trackedModelHandle, e);
        }
    }

    private static void clearEyeBoneState() {
        eyeBonePos[0] = 0.0f;
        eyeBonePos[1] = 0.0f;
        eyeBonePos[2] = 0.0f;
        eyeBoneValid = false;
    }

    private static void clearVrModelRootOffset() {
        vrModelRootOffset = Vector3d.ZERO;
        vrModelRootOffsetValid = false;
    }
}
