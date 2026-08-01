package com.mentalfrostbyte.jello.util.game.network;

import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Hand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Default-off diagnostics for the 1.21+ USE_ITEM rotation consistency problem
 * (Grim BadPacketsJ). Enable with {@code -Dsigma.via.useItemRotationDebug=true}.
 *
 * <p>No packet, entity, world or ByteBuf reference is stored. All hooks only
 * format primitive values at call time and the print rate is capped so the
 * disabled cost is a single volatile boolean branch.
 */
public final class UseItemRotationDebug {
    private static final Logger LOGGER = LogManager.getLogger("UseItemRotation");
    private static final String PROPERTY = "sigma.via.useItemRotationDebug";
    private static final long MIN_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private static volatile boolean checked;
    private static volatile boolean enabled;
    private static volatile long lastPrintNanos;
    private static volatile long packetOrdinal;

    private UseItemRotationDebug() {
    }

    public static boolean isEnabled() {
        if (!checked) {
            enabled = Boolean.getBoolean(PROPERTY);
            checked = true;
        }
        return enabled;
    }

    private static boolean rateLimited() {
        long now = System.nanoTime();
        if (now - lastPrintNanos < MIN_INTERVAL_NANOS) {
            return false;
        }
        lastPrintNanos = now;
        return true;
    }

    private static void log(String message) {
        if (isEnabled() && rateLimited()) {
            LOGGER.info("[UseItemRotation] {}", message);
        }
    }

    private static String targetName() {
        ViaLoadingBase base = ViaLoadingBase.getInstance();
        ProtocolVersion version = base == null ? null : base.getTargetVersion();
        return version == null ? "unknown" : version.getName();
    }

    /**
     * Phase A: original 1.16.4 USE_ITEM is about to be handed to the network
     * stack. Runs on the Render thread.
     */
    public static void logUseItemCreate(ClientPlayerEntity player, Hand hand) {
        if (!isEnabled() || player == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("phase=create");
        sb.append(" thread=").append(Thread.currentThread().getName());
        sb.append(" target=").append(targetName());
        sb.append(" hand=").append(hand);
        sb.append(" visualYaw=").append(player.rotationYaw);
        sb.append(" visualPitch=").append(player.rotationPitch);
        sb.append(" visualYawBits=").append(Integer.toHexString(Float.floatToRawIntBits(player.rotationYaw)));
        sb.append(" visualPitchBits=").append(Integer.toHexString(Float.floatToRawIntBits(player.rotationPitch)));
        sb.append(" lastReportedYaw=").append(player.lastReportedYaw);
        sb.append(" lastReportedPitch=").append(player.lastReportedPitch);
        sb.append(" lastReportedYawBits=").append(Integer.toHexString(Float.floatToRawIntBits(player.lastReportedYaw)));
        sb.append(" lastReportedPitchBits=").append(Integer.toHexString(Float.floatToRawIntBits(player.lastReportedPitch)));
        sb.append(" serverRotationYaw=").append(player.getServerRotationYaw());
        sb.append(" serverRotationPitch=").append(player.getServerRotationPitch());
        sb.append(" coreYaw=").append(RotationCore.currentYaw);
        sb.append(" corePitch=").append(RotationCore.currentPitch);
        sb.append(" usingItem=").append(player.isHandActive());
        sb.append(" activeHand=").append(player.getActiveHand());
        sb.append(" onGround=").append(player.onGround);
        log(sb.toString());
    }

    /** Pre-use rotation flush emitted by {@link ClientPlayerEntity#sendPreUseItemRotation()}. */
    public static void logPreUseFlush(ClientPlayerEntity player, float yaw, float pitch) {
        if (!isEnabled() || player == null) {
            return;
        }
        log("phase=pre-use-flush"
                + " thread=" + Thread.currentThread().getName()
                + " target=" + targetName()
                + " yaw=" + yaw
                + " pitch=" + pitch
                + " yawBits=" + Integer.toHexString(Float.floatToRawIntBits(yaw))
                + " pitchBits=" + Integer.toHexString(Float.floatToRawIntBits(pitch))
                + " lastReportedYaw=" + player.lastReportedYaw
                + " lastReportedPitch=" + player.lastReportedPitch
                + " onGround=" + player.onGround);
    }

    /** Phase C: the movement packet chosen by the local player's sendMovementPackets. */
    public static void logMovementPacket(ClientPlayerEntity player, String type, float yaw, float pitch, boolean carriesRotation) {
        if (!isEnabled() || player == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("phase=movement");
        sb.append(" thread=").append(Thread.currentThread().getName());
        sb.append(" target=").append(targetName());
        sb.append(" type=").append(type);
        sb.append(" carriesRotation=").append(carriesRotation);
        if (carriesRotation) {
            sb.append(" yaw=").append(yaw);
            sb.append(" pitch=").append(pitch);
            sb.append(" yawBits=").append(Integer.toHexString(Float.floatToRawIntBits(yaw)));
            sb.append(" pitchBits=").append(Integer.toHexString(Float.floatToRawIntBits(pitch)));
        }
        sb.append(" lastReportedYaw=").append(player.lastReportedYaw);
        sb.append(" lastReportedPitch=").append(player.lastReportedPitch);
        sb.append(" visualYaw=").append(player.rotationYaw);
        sb.append(" visualPitch=").append(player.rotationPitch);
        sb.append(" onGround=").append(player.onGround);
        sb.append(" positionUpdateTicks=").append(player.getPositionUpdateTicks());
        log(sb.toString());
    }

    /**
     * Phase D: final packet after the Via pipeline, parsed from the wire bytes.
     * Runs on the Netty event loop; only primitives are formatted.
     */
    public static void logPostViaUseItem(int hand, int sequence, float yaw, float pitch) {
        if (!isEnabled()) {
            return;
        }
        log("phase=post-via-use-item"
                + " ordinal=" + packetOrdinal()
                + " thread=" + Thread.currentThread().getName()
                + " target=" + targetName()
                + " hand=" + hand
                + " sequence=" + sequence
                + " yaw=" + yaw
                + " pitch=" + pitch
                + " yawBits=" + Integer.toHexString(Float.floatToRawIntBits(yaw))
                + " pitchBits=" + Integer.toHexString(Float.floatToRawIntBits(pitch)));
    }

    public static void logPostViaMovement(String type, float yaw, float pitch, boolean carriesRotation) {
        if (!isEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(192);
        sb.append("phase=post-via-movement");
        sb.append(" ordinal=").append(packetOrdinal());
        sb.append(" thread=").append(Thread.currentThread().getName());
        sb.append(" target=").append(targetName());
        sb.append(" type=").append(type);
        sb.append(" carriesRotation=").append(carriesRotation);
        if (carriesRotation) {
            sb.append(" yaw=").append(yaw);
            sb.append(" pitch=").append(pitch);
            sb.append(" yawBits=").append(Integer.toHexString(Float.floatToRawIntBits(yaw)));
            sb.append(" pitchBits=").append(Integer.toHexString(Float.floatToRawIntBits(pitch)));
        }
        log(sb.toString());
    }

    /** Local consistency check for the final USE_ITEM vs the last movement rotation seen post-Via. */
    public static void logUseItemConsistency(int sequence, float useItemYaw, float useItemPitch,
                                             boolean lastMovementHasRotation,
                                             float lastMovementYaw, float lastMovementPitch,
                                             String reason) {
        if (!isEnabled()) {
            return;
        }
        boolean match = lastMovementHasRotation
                && Float.floatToRawIntBits(useItemYaw) == Float.floatToRawIntBits(lastMovementYaw)
                && Float.floatToRawIntBits(useItemPitch) == Float.floatToRawIntBits(lastMovementPitch);
        log("phase=consistency"
                + " sequence=" + sequence
                + " useItemYawBits=" + Integer.toHexString(Float.floatToRawIntBits(useItemYaw))
                + " useItemPitchBits=" + Integer.toHexString(Float.floatToRawIntBits(useItemPitch))
                + " movementHasRotation=" + lastMovementHasRotation
                + " movementYawBits=" + (lastMovementHasRotation ? Integer.toHexString(Float.floatToRawIntBits(lastMovementYaw)) : "-")
                + " movementPitchBits=" + (lastMovementHasRotation ? Integer.toHexString(Float.floatToRawIntBits(lastMovementPitch)) : "-")
                + " result=" + (match ? "MATCH" : "MISMATCH")
                + " reason=" + reason);
    }

    private static long packetOrdinal() {
        return ++packetOrdinal;
    }

    /** Helper so the network debug handler can obtain the local player without keeping a reference. */
    public static ClientPlayerEntity localPlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.player;
    }

    /** Debug-only snapshot of the last movement packet seen post-Via (primitive fields only). */
    public static final class PostViaMovementState {
        private boolean hasRotation;
        private float yaw;
        private float pitch;

        public void set(float yaw, float pitch) {
            this.hasRotation = true;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public void clear() {
            this.hasRotation = false;
        }

        public boolean hasRotation() {
            return this.hasRotation;
        }

        public float yaw() {
            return this.yaw;
        }

        public float pitch() {
            return this.pitch;
        }
    }
}
