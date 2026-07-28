package de.florianmichael.viamcp.fixes;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;

/**
 * 1.21.5+ movement helpers for the upgrade path (1.16.5 client -&gt; modern servers).
 * <p>
 * Authority is vanilla 1.21.x + ViaFabricPlus inverted gates:
 * ViaFabricPlus rewinds a modern client toward older servers (high-to-low); this client
 * enables the modern physics those mixins would otherwise strip once the target is new
 * enough (low-to-high). Prefer inverting ViaFP lower-cutoff tags over inventing
 * 1.21.11-only special cases.
 * <p>
 * References (ViaFabricPlus 26.2 / yarn 1.21.11):
 * <ul>
 *   <li>LivingEntity.aiStep velocity threshold (player combined 9.0E-6, else per-axis 0.003)</li>
 *   <li>LocalPlayer.modifyInput / modifyInputSpeedForSquareMovement / distanceToUnitSquare</li>
 *   <li>LocalPlayer.isHorizontalCollisionMinor</li>
 *   <li>Entity.move minorHorizontalCollision assignment from collided movement</li>
 *   <li>Entity.collide modern step: fallingOntoGround + collectCandidateStepUpHeights(getCoords)</li>
 *   <li>MixinEntity.use1_20_6StepCollisionCalculation olderThanOrEqualTo(v1_20_5) inverted</li>
 * </ul>
 */
public final class PacketFixFor1_21_5Plus {
    private static final String ENABLED_PROPERTY = "sigma.viamcp.packetFix1_21_5";

    /** Pre-1.9 LivingEntity residual clamp. */
    private static final double LEGACY_1_8_THRESHOLD = 0.005D;
    /** Post-1.9 / non-player axis clamp used by modern LivingEntity.aiStep. */
    private static final double MODERN_AXIS_THRESHOLD = 0.003D;
    /**
     * Modern LivingEntity.aiStep player branch:
     * {@code horizontalDistanceSqr() < 9.0E-6} zeros both X and Z together.
     */
    private static final double COMBINED_HORIZONTAL_THRESHOLD_SQR = 9.0E-6D;
    /**
     * Modern LocalPlayer.isHorizontalCollisionMinor length epsilon
     * ({@code 1.0E-5F} promoted to double = 9.999999747378752E-6).
     */
    private static final double MINOR_HORIZONTAL_LENGTH_SQR = 9.999999747378752E-6D;
    /** Modern LocalPlayer.isHorizontalCollisionMinor angle cap (~8 degrees). */
    private static final double MINOR_HORIZONTAL_ANGLE_RAD = 0.13962633907794952D;
    private static final float INPUT_SCALE_1_21_5 = 0.98F;

    private PacketFixFor1_21_5Plus() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    public static boolean isAtLeast1_21_5() {
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null && version.newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    public static boolean isAtLeast1_18() {
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null && version.newerThan(ProtocolVersion.v1_17_1);
    }

    /**
     * ViaFabricPlus rewinds modern candidate-height step with
     * {@code olderThanOrEqualTo(v1_20_5)}. Inverted for the upgrade path:
     * modern step only when the target is strictly newer than 1.20.5.
     */
    public static boolean shouldUseModernStepCollision() {
        if (!isEnabled()) {
            return false;
        }
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null && version.newerThan(ProtocolVersion.v1_20_5);
    }

    /**
     * Modern LivingEntity only uses the combined horizontal clamp for
     * {@code EntityType.PLAYER}. ViaFabricPlus forces the per-axis path on &lt;=1.21.4
     * by making the player-type check fail; inverted here for the upgrade path.
     */
    public static boolean shouldUseCombinedHorizontalMovementThreshold(boolean isPlayer) {
        return isEnabled() && isAtLeast1_21_5() && isPlayer;
    }

    public static boolean shouldApplySquareMovementCompensation(Entity entity) {
        return isEnabled()
                && isAtLeast1_21_5()
                && entity instanceof ClientPlayerEntity;
    }

    /**
     * LivingEntity / LocalPlayer travel-input stage.
     * <p>
     * ViaFP on a modern client:
     * <ul>
     *   <li>&lt;=1.21.4: LivingEntity.aiStep multiplies xxa/zza by 0.98 for everyone;
     *       LocalPlayer.modifyInput/*0.98+square is stripped</li>
     *   <li>&gt;1.21.4: LocalPlayer.modifyInput does 0.98 + square; non-players keep base 0.98</li>
     * </ul>
     * Upgrade path: always apply base 0.98 (1.14+ LivingEntity expectation), then square
     * only for the local player on 1.21.5+.
     */
    public static boolean shouldApplyTravelInputFactors() {
        return isEnabled();
    }

    public static double axisMovementThreshold() {
        ProtocolVersion version = JelloPortal.getVersion();
        if (version != null && version.olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return LEGACY_1_8_THRESHOLD;
        }
        return MODERN_AXIS_THRESHOLD;
    }

    public static Vector3d applyMovementThreshold(Vector3d motion, boolean isPlayer) {
        if (motion == null) {
            return Vector3d.ZERO;
        }
        return applyMovementThreshold(motion.x, motion.y, motion.z, isPlayer);
    }

    /**
     * Mirrors modern LivingEntity.aiStep velocity scrub:
     * <pre>
     * if (type == PLAYER) {
     *   if (horizontalDistanceSqr &lt; 9.0E-6) x = z = 0;
     * } else {
     *   per-axis |v| &lt; 0.003
     * }
     * y always per-axis |v| &lt; 0.003 (0.005 on &lt;=1.8)
     * </pre>
     * No vehicle exception: vanilla applies the player branch regardless of riding.
     */
    public static Vector3d applyMovementThreshold(double x, double y, double z, boolean isPlayer) {
        double threshold = axisMovementThreshold();

        if (shouldUseCombinedHorizontalMovementThreshold(isPlayer)) {
            if (x * x + z * z < COMBINED_HORIZONTAL_THRESHOLD_SQR) {
                x = 0.0D;
                z = 0.0D;
            }
        } else {
            if (Math.abs(x) < threshold) {
                x = 0.0D;
            }
            if (Math.abs(z) < threshold) {
                z = 0.0D;
            }
        }

        if (Math.abs(y) < threshold) {
            y = 0.0D;
        }
        return new Vector3d(x, y, z);
    }

    /**
     * ViaFP {@code horizontalExactCollisionEqualness}: exact equality on &lt;=1.12.2,
     * {@link MathHelper#epsilonEquals} on 1.13+.
     */
    public static boolean axisCollided(double input, double collided) {
        ProtocolVersion version = JelloPortal.getVersion();
        if (version != null && version.olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return input != collided;
        }
        return !MathHelper.epsilonEquals(input, collided);
    }

    /**
     * Modern LocalPlayer.modifyInputSpeedForSquareMovement on already-scaled xxa/zza.
     * Returns {strafe, forward}.
     */
    public static float[] modifyInputSpeedForSquareMovement(float strafe, float forward) {
        float length = MathHelper.sqrt(strafe * strafe + forward * forward);
        if (length < 1.0E-5F) {
            return new float[]{0.0F, 0.0F};
        }

        float normStrafe = strafe / length;
        float normForward = forward / length;
        float dist = distanceToUnitSquare(normStrafe, normForward);
        return new float[]{normStrafe * dist * length, normForward * dist * length};
    }

    /**
     * Modern LocalPlayer.distanceToUnitSquare on a unit-ish Vec2 (x=strafe, y=forward).
     */
    public static float distanceToUnitSquare(float normStrafe, float normForward) {
        float absStrafe = Math.abs(normStrafe);
        float absForward = Math.abs(normForward);
        float ratio = absForward > absStrafe
                ? (absForward == 0.0F ? 0.0F : absStrafe / absForward)
                : (absStrafe == 0.0F ? 0.0F : absForward / absStrafe);
        return MathHelper.sqrt(1.0F + ratio * ratio);
    }

    /**
     * LivingEntity travel-input stage for the local player on 1.21.5+:
     * base LivingEntity.applyInput multiplies by 0.98 for non-players; LocalPlayer
     * folds 0.98 into modifyInput and then runs square compensation. Item/sneak are
     * already applied earlier on this 1.16.5 client (movementInput), matching the
     * effective non-item non-sneak path used while wall sprint-jumping.
     */
    public static void applyTravelInputFactors(LivingEntity entity) {
        if (!shouldApplyTravelInputFactors() || entity == null) {
            return;
        }

        entity.moveStrafing *= INPUT_SCALE_1_21_5;
        entity.moveForward *= INPUT_SCALE_1_21_5;

        if (!shouldApplySquareMovementCompensation(entity)) {
            return;
        }

        float[] squared = modifyInputSpeedForSquareMovement(entity.moveStrafing, entity.moveForward);
        entity.moveStrafing = squared[0];
        entity.moveForward = squared[1];
    }

    /**
     * Modern LocalPlayer.isHorizontalCollisionMinor(Vec3).
     * Uses post-modifyInput xxa/zza (here: moveStrafing/moveForward) and the
     * collided movement result from Entity.move — not raw KeyboardInput.
     */
    public static boolean isHorizontalCollisionMinor(float yawDegrees,
                                                     float xxa,
                                                     float zza,
                                                     double movementX,
                                                     double movementZ) {
        float yawRadians = yawDegrees * ((float) Math.PI / 180.0F);
        double sin = MathHelper.sin(yawRadians);
        double cos = MathHelper.cos(yawRadians);
        // LocalPlayer: inputX = xxa * cos - zza * sin; inputZ = zza * cos + xxa * sin
        double inputX = (double) xxa * cos - (double) zza * sin;
        double inputZ = (double) zza * cos + (double) xxa * sin;
        double inputLengthSqr = inputX * inputX + inputZ * inputZ;
        double movementLengthSqr = movementX * movementX + movementZ * movementZ;

        if (inputLengthSqr < MINOR_HORIZONTAL_LENGTH_SQR || movementLengthSqr < MINOR_HORIZONTAL_LENGTH_SQR) {
            return false;
        }

        double dot = inputX * movementX + inputZ * movementZ;
        double angle = Math.acos(dot / Math.sqrt(inputLengthSqr * movementLengthSqr));
        return angle < MINOR_HORIZONTAL_ANGLE_RAD;
    }

    /**
     * Entity.move post-HC bookkeeping. Vanilla sets
     * {@code minorHorizontalCollision} from the collided movement vector here so
     * sprint-stop on the next tick matches the server.
     */
    public static void updateMinorHorizontalCollision(Entity entity, Vector3d collidedMovement) {
        if (entity == null) {
            return;
        }

        // Modern Entity base returns false; only LocalPlayer overrides the angle test.
        // Kill-switch / pre-1.18 / no HC => hard collision path (minor=false).
        if (!isEnabled() || !entity.collidedHorizontally || collidedMovement == null || !isAtLeast1_18()) {
            entity.minorHorizontalCollision = false;
            return;
        }

        if (entity instanceof ClientPlayerEntity) {
            LivingEntity living = (LivingEntity) entity;
            // Collided movement from Entity.move + post-travel moveStrafing/moveForward (xxa/zza).
            entity.minorHorizontalCollision = isHorizontalCollisionMinor(
                    living.rotationYaw,
                    living.moveStrafing,
                    living.moveForward,
                    collidedMovement.x,
                    collidedMovement.z);
            return;
        }

        entity.minorHorizontalCollision = false;
    }

    /**
     * Modern Entity.collide step-box predicate.
     * {@code steppedBox} is pre-offset by the already-resolved Y only when the entity is
     * settling onto ground (vertical collision while moving down). Being {@code onGround}
     * at the start of move is not enough: the jump tick is onGround with y&gt;0 and must keep
     * the step box at the feet, matching vanilla 1.20.5+ / 1.21.x.
     */
    public static boolean isFallingOntoGround(double movementY, double collidedY) {
        return movementY != collidedY && movementY < 0.0D;
    }

    /**
     * Modern Entity.collectCandidateStepUpHeights.
     * <p>
     * Uses {@link VoxelShape#getCoords(Direction.Axis)} (sorted Y planes), skips
     * {@code delta == collidedY}, and <b>breaks</b> a shape once {@code delta > stepHeight}
     * — not the earlier toBoundingBoxList min/max approximation.
     */
    public static float[] collectCandidateStepUpHeights(AxisAlignedBB box,
                                                        List<VoxelShape> shapes,
                                                        float stepHeight,
                                                        float collidedY) {
        TreeSet<Float> candidates = new TreeSet<>();
        if (box == null || shapes == null) {
            return new float[0];
        }

        for (VoxelShape shape : shapes) {
            if (shape == null || shape.isEmpty()) {
                continue;
            }

            DoubleList coords = shape.getCoords(Direction.Axis.Y);
            int size = coords.size();
            for (int i = 0; i < size; i++) {
                float delta = (float) (coords.getDouble(i) - box.minY);
                if (delta < 0.0F) {
                    continue;
                }
                if (delta == collidedY) {
                    continue;
                }
                // Coords are ascending; once past stepHeight the rest of this shape is above.
                if (delta > stepHeight) {
                    break;
                }
                candidates.add(delta);
            }
        }

        float[] result = new float[candidates.size()];
        int i = 0;
        for (float candidate : candidates) {
            result[i++] = candidate;
        }
        return result;
    }

    /**
     * ViaFP shouldStopRunSprinting bands inverted for the upgrade path.
     * <ul>
     *   <li>&lt;=1.21.4: legacy set (handled by ClientPlayerEntity pre-1.21.5 branches)</li>
     *   <li>1.21.5-1.21.7: expanded set with HC&amp;&amp;!minor + water shallow check</li>
     *   <li>&gt;1.21.7 (1.21.9+): modern
     *       {@code !isSprintingPossible(flying) || !forward || (HC &amp;&amp; !minor)}</li>
     * </ul>
     */
    public static boolean isInSprintStopBand_1_21_5_to_1_21_7() {
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null
                && version.newerThan(ProtocolVersion.v1_21_4)
                && version.olderThanOrEqualTo(ProtocolVersion.v1_21_7);
    }

    /** ViaFP leaves modern shouldStopRunSprinting intact above 1.21.7. */
    public static boolean isInModernSprintStopBand() {
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null && version.newerThan(ProtocolVersion.v1_21_7);
    }

    public static boolean isPlayerEntity(Entity entity) {
        return entity instanceof PlayerEntity;
    }
}
