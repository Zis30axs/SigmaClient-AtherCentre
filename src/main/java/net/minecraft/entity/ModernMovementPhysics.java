package net.minecraft.entity;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.util.game.network.ViaNetworkDiagnostics;
import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.Direction;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;

import java.util.List;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Version gates, shared movement math and the serverbound movement-flag
 * rewrite for the 1.21.x compatibility fixes.
 *
 * <p>This class previously existed alongside two helper classes under
 * {@code de.florianmichael.viamcp.fixes} ({@code PacketFixFor1_21Plus} and
 * {@code PacketFixFor1_21_5Plus}); the three were consolidated here so the fix
 * logic lives in the vanilla entity package next to the movement code it
 * patches ({@link Entity}, {@link LivingEntity}, {@link ClientPlayerEntity}).
 * The modern {@code ServerboundPlayerInputPacket} handling that used to live
 * in {@code PacketFixFor1_21Plus} is owned by
 * {@code net.minecraft.network.play.client.CPlayerInputPacket}.
 *
 * <p>Behavioral reference for the bubble-column gate: Minecraft 1.21.11 named
 * sources (E:\.sigma\namedSrc):
 * <ul>
 *   <li>{@code Entity.move} no longer applies inside-block effects while
 *       moving; it only records {@code QueuedCollisionCheck} segments.</li>
 *   <li>{@code LivingEntity.tickMovement} calls {@code tickBlockCollision()}
 *       immediately after {@code travel()}; that stage walks the recorded
 *       movement path and invokes {@code Block.onEntityCollision}, which for
 *       bubble columns calls {@code onBubbleColumnSurfaceCollision} /
 *       {@code onBubbleColumnCollision} and writes the final velocity.</li>
 *   <li>The bubble formulas are unchanged versus 1.16.4; only the application
 *       stage moved.</li>
 * </ul>
 *
 * <p>The movement physics references (ViaFabricPlus 26.2 / yarn 1.21.11):
 * <ul>
 *   <li>LivingEntity.aiStep velocity threshold (player combined 9.0E-6, else per-axis 0.003)</li>
 *   <li>LocalPlayer.modifyInput / modifyInputSpeedForSquareMovement / distanceToUnitSquare</li>
 *   <li>Entity.collide modern step: fallingOntoGround + collectCandidateStepUpHeights(getCoords)</li>
 *   <li>MixinEntity.use1_20_6StepCollisionCalculation olderThanOrEqualTo(v1_20_5) inverted</li>
 *   <li>MixinKeyboardInput: Vec2.normalized() only when newerThan 1.21.4 (invert keep on 1.21.5+)</li>
 * </ul>
 */
public final class ModernMovementPhysics {

    private static final String ENABLED_PROPERTY = "sigma.viamcp.packetFix1_21";
    private static final String GRIM_VANILLA_COMPAT_PROPERTY = "sigma.viamcp.grimVanillaCompat";
    private static final String ENABLED_1_21_5_PROPERTY = "sigma.viamcp.packetFix1_21_5";
    /*
     * ViaVersion exposes protocol 768 as v1_21_2 for the 1.21.2-1.21.3 range.
     * This is the lowest protocol node that can represent a 1.21.3+ target.
     */
    private static final ProtocolVersion FIRST_1_21_3_PROTOCOL = ProtocolVersion.v1_21_2;
    public static final String HANDLER_NAME = "sigma-1_21-movement-flag-fix";
    private static final int FLAG_ON_GROUND = 1;
    private static final int FLAG_HORIZONTAL_COLLISION = 2;
    /** Pre-1.9 LivingEntity residual clamp. */
    private static final double LEGACY_1_8_THRESHOLD = 0.005D;
    /** Post-1.9 / non-player axis clamp used by modern LivingEntity.aiStep. */
    private static final double MODERN_AXIS_THRESHOLD = 0.003D;
    /**
     * Modern LivingEntity.aiStep player branch:
     * {@code horizontalDistanceSqr() < 9.0E-6} zeros both X and Z together.
     */
    private static final double COMBINED_HORIZONTAL_THRESHOLD_SQR = 9.0E-6D;
    private static final float INPUT_SCALE_1_21_5 = 0.98F;
    private static final int DOUBLE_BYTES = 8;
    private static final int FLOAT_BYTES = 4;

    private ModernMovementPhysics() {
    }

    // ===== version gates =====

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    private static boolean is1_21_5FixEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_1_21_5_PROPERTY, "true"));
    }

    private static ProtocolVersion targetVersion() {
        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        return loadingBase == null ? ProtocolVersion.v1_16_4 : loadingBase.getTargetVersion();
    }

    public static boolean isTargetAtLeast1_21_3Protocol() {
        return isAtLeast1_21_3Protocol(targetVersion());
    }

    public static boolean isAtLeast1_21_3Protocol(ProtocolVersion targetVersion) {
        return targetVersion != null && targetVersion.newerThanOrEqualTo(FIRST_1_21_3_PROTOCOL);
    }

    public static boolean isAtLeast1_21_5() {
        ProtocolVersion version = JelloPortal.getVersion();
        return version != null && version.newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    /**
     * ViaFabricPlus rewinds modern candidate-height step with
     * {@code olderThanOrEqualTo(v1_20_5)}. Inverted for the upgrade path:
     * modern step only when the target is strictly newer than 1.20.5.
     */
    public static boolean shouldUseModernStepCollision() {
        if (!is1_21_5FixEnabled()) {
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
        return is1_21_5FixEnabled() && isAtLeast1_21_5() && isPlayer;
    }

    public static boolean shouldApplySquareMovementCompensation(Entity entity) {
        return is1_21_5FixEnabled()
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
        return is1_21_5FixEnabled();
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
     * Modern LocalPlayer.modifyInputSpeedForSquareMovement on already-scaled xxa/zza.
     * Returns {strafe, forward}.
     */
    public static float[] modifyInputSpeedForSquareMovement(float strafe, float forward) {
        // Modern LocalPlayer.modifyInputSpeedForSquareMovement:
        //   length = |input|; unit = input/length; dist = distanceToUnitSquare(unit);
        //   return unit * min(length * dist, 1.0F)
        // The min cap matters: without it, raw (1,1) sneak paths overshoot by ~sqrt(2).
        float length = MathHelper.sqrt(strafe * strafe + forward * forward);
        if (length <= 0.0F) {
            return new float[]{strafe, forward};
        }

        float normStrafe = strafe / length;
        float normForward = forward / length;
        float dist = distanceToUnitSquare(normStrafe, normForward);
        float cappedLength = Math.min(length * dist, 1.0F);
        return new float[]{normStrafe * cappedLength, normForward * cappedLength};
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
     * modern LocalPlayer.modifyInput does 0.98 -&gt; item -&gt; sneak -&gt; square(min).
     * On this 1.16.5 client, KeyboardInput-equivalent diagonal normalize + sneak/item
     * already ran on movementInput (see MovementInputFromOptions + livingTick); here
     * we apply the remaining 0.98 and square compensation in modern order.
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

    /**
     * 1.21.10 added the shallow-water term of {@code LocalPlayer.isSprintingPossible}:
     * {@code allowedInShallowWater || !isInShallowWater()}, where
     * {@code Entity.isInShallowWater() == isInWater() && !isUnderWater()}.
     * <p>
     * This is a new <b>restriction</b>, not an exemption: on 1.21.10+ a run sprint is impossible
     * while waist-deep, because {@code canStartSprinting} and {@code shouldStopRunSprinting} both
     * pass {@code abilities.flying} (false on foot). Only the swim path passes {@code true}, and
     * that needs the eyes submerged. ViaFabricPlus agrees - its
     * {@code isSprintingPossible1_21_10} override only replaces the method for
     * {@code olderThanOrEqualTo(v1_21_9)} targets and leaves 1.21.10+ on vanilla behaviour.
     *
     * @param allowedInShallowWater vanilla's parameter: {@code true} only on the swim-sprint path
     */
    public static boolean isShallowWaterSprintAllowed(boolean allowedInShallowWater) {
        if (allowedInShallowWater) {
            return true;
        }

        ProtocolVersion version = JelloPortal.getVersion();
        // <=1.12.2 had no shallow-water sprint restriction of any kind.
        return version != null && version.olderThanOrEqualTo(ProtocolVersion.v1_12_2);
    }

    // ===== movement cadence / physics gates =====

    public static boolean shouldUseMovementFlags() {
        return shouldUseMovementFlags(activeUserConnection());
    }

    private static boolean shouldUseMovementFlags(UserConnection connection) {
        // Version + PLAY only. Protocol-name matching was dropping HC flag rewrites on
        // some 1.21.6+ pipelines that still speak modern movement flags.
        return isEnabled()
                && isTargetAtLeast1_21_3Protocol()
                && isPlayState(connection);
    }

    public static boolean shouldUseVanilla1_21MovementCadence() {
        return shouldUseVanilla1_21MovementPhysics();
    }

    public static boolean shouldUseVanilla1_21MovementPhysics() {
        // Client-side prediction must track JelloPortal target version even when the
        // Via pipeline name set is incomplete; PLAY-state is not required for local physics.
        return isEnabled() && isTargetAtLeast1_21_3Protocol();
    }

    public static boolean shouldUseGrimVanillaMovement() {
        return shouldUseVanilla1_21MovementPhysics()
                && Boolean.parseBoolean(System.getProperty(GRIM_VANILLA_COMPAT_PROPERTY, "false"));
    }

    public static boolean shouldUseVanilla1_21_5InputPhysics() {
        return isEnabled() && targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    public static int getPositionPacketInterval(boolean legacy) {
        if (legacy) {
            return 21;
        }

        return shouldUseVanilla1_21MovementCadence() ? 20 : 19;
    }

    /**
     * Modern KeyboardInput.tick: after assembling raw impulses, call
     * {@code moveVector = new Vec2(strafe, forward).normalized()} when length &gt; 1.
     * ViaFabricPlus strips that normalize on &lt;=1.21.4; inverted here for 1.21.5+.
     * Must run before sneak/item slowdown so modifyInput's later 0.98+square path
     * matches the server (sneak-diagonal otherwise overshoots by ~sqrt(2)).
     */
    public static void normalizeRaw1_21_5MovementInput(MovementInput input) {
        if (!shouldUseVanilla1_21_5InputPhysics() || input == null) {
            return;
        }

        float lengthSquared = input.moveStrafe * input.moveStrafe + input.moveForward * input.moveForward;
        if (lengthSquared > 1.0F) {
            float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
            input.moveStrafe *= inverseLength;
            input.moveForward *= inverseLength;
        }
    }

    // ===== attack self-slow =====

    /**
     * Modern 1.21+ client attack self-slow no longer reads item Knockback enchant locally.
     * {@code LivingEntity.getKnockback} only runs {@code EnchantmentHelper.modifyKnockback}
     * on {@code ServerLevel}; the multipath client uses {@code ATTACK_KNOCKBACK/2} plus the
     * sprint-hit +0.5 added in {@code Player.attack} before {@code causeExtraKnockback}.
     * ViaFabricPlus does not rewrite this on the high client. Invert that gate for 1.16 -> 1.21+.
     *
     * <p>Cut at {@code v1_21} to match the enchantment-component era (and Grim's client-version
     * cutoff that forces item knockback level 0 for >= 1.21 clients). Prefer this lower tag over
     * inventing 1.21.11-only semantics.
     */
    private static boolean shouldUseModernAttackSelfSlow() {
        return isEnabled() && targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21);
    }

    /**
     * Client-side portion of modern {@code LivingEntity.getKnockback}: {@code ATTACK_KNOCKBACK / 2}.
     * 1.16 registers {@code generic.attack_knockback} on {@code MobEntity} only, so a player never
     * carries it and the local multipath contribution is always 0 unless a server/plugin injects it.
     */
    private static float clientAttackKnockbackAttribute() {
        return 0.0F;
    }

    /**
     * Whether {@code causeExtraKnockback}-equivalent self slow / local KB impulse should run.
     *
     * @param sprintKnockbackHit sprinting with full attack strength (legacy flag1 / modern +0.5)
     * @param enchantKnockbackLevels {@code EnchantmentHelper.getKnockbackModifier} (legacy only)
     */
    public static boolean shouldApplyAttackSelfSlow(boolean sprintKnockbackHit, int enchantKnockbackLevels) {
        if (!shouldUseModernAttackSelfSlow()) {
            return sprintKnockbackHit || enchantKnockbackLevels > 0;
        }
        // 1.16 players do not register ATTACK_KNOCKBACK; modern client attribute portion is 0
        // unless a server/plugin injects it. Sprint-hit is the practical multipath trigger.
        return sprintKnockbackHit || clientAttackKnockbackAttribute() > 0.0F;
    }

    /**
     * Strength passed into modern {@code causeExtraKnockback} on the client multipath:
     * attribute/2 + (sprintHit ? 0.5 : 0). Enchant knockback is server-only.
     */
    public static float modernAttackKnockbackStrength(boolean sprintKnockbackHit) {
        float strength = clientAttackKnockbackAttribute();
        if (sprintKnockbackHit) {
            strength += 0.5F;
        }
        return strength;
    }

    /**
     * Legacy uses integer knockback levels as {@code level * 0.5F}. Modern uses the float above.
     */
    public static float attackKnockbackStrength(boolean sprintKnockbackHit, int totalLegacyKnockbackLevels) {
        if (shouldUseModernAttackSelfSlow()) {
            return modernAttackKnockbackStrength(sprintKnockbackHit);
        }
        return totalLegacyKnockbackLevels * 0.5F;
    }

    /*
     * There is deliberately no flushSprintAfterAttack() here.
     *
     * Player.attack only calls setSprinting(false) locally; the PLAYER_COMMAND action is emitted by
     * LocalPlayer#sendPosition -> sendIsSprintingIfNeeded at the top of the movement send, so it
     * still reaches the server ahead of that tick's movement packet. ViaFabricPlus keeps that
     * placement for every target version - it only suppresses or relocates the call (MixinLocalPlayer
     * removeSprintingPacket / sendSneakingAfterSprinting) and never adds a sprint packet inside the
     * attack path. ClientPlayerEntity#sendSprintingPacket inside sendMovementPackets is the
     * equivalent seam here.
     *
     * Flushing at attack time instead produced two deviations from a real 1.21+ client: an entity
     * action wedged between the interact and the swing packet, and a STOP_SPRINTING/START_SPRINTING
     * pair inside a single tick whenever that same tick's livingTick re-acquired sprint.
     */

    // ===== serverbound movement flags =====

    public static boolean unpackOnGround(int flags) {
        return shouldUseMovementFlags() ? (flags & FLAG_ON_GROUND) != 0 : flags != 0;
    }

    public static boolean unpackHorizontalCollision(int flags) {
        return shouldUseMovementFlags() && (flags & FLAG_HORIZONTAL_COLLISION) != 0;
    }

    public static boolean horizontalCollision() {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity player = mc.player;
        return player != null && player.collidedHorizontally;
    }

    public static void rememberMovementPacket(boolean horizontalCollision) {
        UserConnection connection = activeUserConnection();
        if (!shouldUseMovementFlags(connection)) {
            clearMovementFlags(connection);
            return;
        }

        movementFlagState(connection).horizontalCollisions.offer(horizontalCollision);
    }

    /**
     * ViaVersion changes the server-side state to CONFIGURATION while a modern
     * proxy transfers the client to another backend. Custom PLAY packets and raw
     * PLAY packet rewrites must stop until that state returns to PLAY.
     */
    public static boolean isPlayState(UserConnection connection) {
        return connection != null
                && connection.isActive()
                && !connection.isPendingDisconnect()
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getClientState() == State.PLAY
                && connection.getProtocolInfo().getServerState() == State.PLAY;
    }

    public static boolean hasProtocol(UserConnection connection, Class<? extends Protocol> protocolClass) {
        return connection != null
                && protocolClass != null
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getPipeline().contains(protocolClass);
    }

    private static UserConnection activeUserConnection() {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayNetHandler playHandler = mc.getConnection();
        if (playHandler == null) {
            return null;
        }

        NetworkManager networkManager = playHandler.getNetworkManager();
        return networkManager == null ? null : networkManager.getViaUserConnection();
    }

    public static ChannelOutboundHandlerAdapter createServerboundMovementFlagHandler(UserConnection connection) {
        return new ServerboundMovementFlagHandler(connection);
    }

    /**
     * ViaVersion changes the server-side state to CONFIGURATION while a modern
     * proxy transfers the client to another backend. Custom PLAY packets and raw
     * PLAY packet rewrites must stop until that state returns to PLAY.
     */
    private static void rewriteServerboundMovementFlags(ByteBuf buf, UserConnection connection) {
        if (!shouldUseMovementFlags(connection) || !buf.isReadable()) {
            clearMovementFlags(connection);
            return;
        }

        int packetStart = buf.readerIndex();
        VarInt packetId = readVarInt(buf, packetStart);
        if (packetId.bytes <= 0) {
            return;
        }

        int payloadStart = packetStart + packetId.bytes;
        int flagsIndex = flagsIndex(buf, packetId.value, payloadStart);
        if (flagsIndex < 0 || flagsIndex >= buf.writerIndex()) {
            return;
        }

        int flags = buf.getUnsignedByte(flagsIndex);
        Boolean queuedHorizontalCollision = movementFlagState(connection).horizontalCollisions.poll();
        boolean horizontalCollision = queuedHorizontalCollision != null
                ? queuedHorizontalCollision
                : horizontalCollision();

        if (horizontalCollision) {
            flags |= FLAG_HORIZONTAL_COLLISION;
        } else {
            flags &= ~FLAG_HORIZONTAL_COLLISION;
        }

        buf.setByte(flagsIndex, flags);
    }

    private static int flagsIndex(ByteBuf buf, int packetId, int payloadStart) {
        MovementPacketIds ids = movementPacketIds();
        if (packetId == ids.position) {
            return readable(buf, payloadStart, DOUBLE_BYTES * 3 + 1)
                    ? payloadStart + DOUBLE_BYTES * 3
                    : -1;
        }

        if (packetId == ids.positionRotation) {
            return readable(buf, payloadStart, DOUBLE_BYTES * 3 + FLOAT_BYTES * 2 + 1)
                    ? payloadStart + DOUBLE_BYTES * 3 + FLOAT_BYTES * 2
                    : -1;
        }

        if (packetId == ids.rotation) {
            return readable(buf, payloadStart, FLOAT_BYTES * 2 + 1)
                    ? payloadStart + FLOAT_BYTES * 2
                    : -1;
        }

        if (packetId == ids.statusOnly) {
            return readable(buf, payloadStart, 1) ? payloadStart : -1;
        }

        return -1;
    }

    private static boolean readable(ByteBuf buf, int start, int length) {
        return start >= buf.readerIndex() && start + length <= buf.writerIndex();
    }

    private static MovementPacketIds movementPacketIds() {
        ProtocolVersion targetVersion = targetVersion();
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            return movementPacketIds(
                    ServerboundPackets1_21_6.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_6.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_6.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_6.MOVE_PLAYER_STATUS_ONLY);
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return movementPacketIds(
                    ServerboundPackets1_21_5.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_5.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_5.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_5.MOVE_PLAYER_STATUS_ONLY);
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return movementPacketIds(
                    ServerboundPackets1_21_4.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_4.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_4.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_4.MOVE_PLAYER_STATUS_ONLY);
        }

        return movementPacketIds(
                ServerboundPackets1_21_2.MOVE_PLAYER_POS,
                ServerboundPackets1_21_2.MOVE_PLAYER_POS_ROT,
                ServerboundPackets1_21_2.MOVE_PLAYER_ROT,
                ServerboundPackets1_21_2.MOVE_PLAYER_STATUS_ONLY);
    }

    private static MovementPacketIds movementPacketIds(ServerboundPacketType position,
                                                       ServerboundPacketType positionRotation,
                                                       ServerboundPacketType rotation,
                                                       ServerboundPacketType statusOnly) {
        return new MovementPacketIds(position.getId(), positionRotation.getId(), rotation.getId(), statusOnly.getId());
    }

    private static VarInt readVarInt(ByteBuf buf, int index) {
        int value = 0;
        int position = 0;

        for (int offset = 0; offset < 5; ++offset) {
            if (index + offset >= buf.writerIndex()) {
                return new VarInt(0, -1);
            }

            byte currentByte = buf.getByte(index + offset);
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                return new VarInt(value, offset + 1);
            }

            position += 7;
        }

        return new VarInt(0, -1);
    }

    private record VarInt(int value, int bytes) {
    }

    private record MovementPacketIds(int position, int positionRotation, int rotation, int statusOnly) {
    }

    private static final class ServerboundMovementFlagHandler extends ChannelOutboundHandlerAdapter {
        private final UserConnection connection;

        private ServerboundMovementFlagHandler(UserConnection connection) {
            this.connection = connection;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                if (dropBackloggedMovement(buf, this.connection)) {
                    promise.setSuccess();
                    return;
                }
                rewriteServerboundMovementFlags(buf, this.connection);
            }

            super.write(ctx, msg, promise);
        }
    }

    /**
     * Drops stale movement packets that were queued during an initial-join /
     * chunk burst instead of flushing them all at once after recovery.
     * Anti-cheat-facing consequence: the burst that triggers Grim Timer /
     * TimerLimit is removed; the connection keeps flowing because keep-alive
     * and interaction packets are not movement packets.
     */
    private static boolean dropBackloggedMovement(ByteBuf buf, UserConnection connection) {
        if (!ViaNetworkDiagnostics.shouldDropStaleMovement() || !shouldUseMovementFlags(connection)
                || !buf.isReadable()) {
            return false;
        }

        int start = buf.readerIndex();
        VarInt packetId = readVarInt(buf, start);
        if (packetId.bytes <= 0) {
            return false;
        }

        MovementPacketIds ids = movementPacketIds();
        boolean isMovement = packetId.value == ids.position
                || packetId.value == ids.positionRotation
                || packetId.value == ids.rotation
                || packetId.value == ids.statusOnly;
        if (!isMovement) {
            return false;
        }

        ViaNetworkDiagnostics.droppedBacklogMovement();
        return true;
    }

    private static MovementFlagState movementFlagState(UserConnection connection) {
        return (MovementFlagState) connection.getStoredObjects().computeIfAbsent(
                MovementFlagState.class, ignored -> new MovementFlagState());
    }

    private static void clearMovementFlags(UserConnection connection) {
        if (connection == null) {
            return;
        }

        MovementFlagState state = connection.get(MovementFlagState.class);
        if (state != null) {
            state.horizontalCollisions.clear();
        }
    }

    private static final class MovementFlagState implements StorableObject {
        private final Queue<Boolean> horizontalCollisions = new ConcurrentLinkedQueue<>();

        @Override
        public void onRemove() {
            this.horizontalCollisions.clear();
        }
    }

    // ===== 1.21.11 bubble-column / inside-block effects =====

    /**
     * Only Minecraft 1.21.11 is enabled. Earlier 1.21.x versions are kept on
     * the original 1.16.4 order until their exact inside-block semantics are
     * verified against their own sources (the workspace only contains the
     * 1.21.11 named source tree).
     */
    public static boolean shouldUseModernBlockEffects() {
        if (!isEnabled()) {
            return false;
        }

        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        ProtocolVersion target = loadingBase == null ? null : loadingBase.getTargetVersion();
        return target != null && target.newerThanOrEqualTo(ProtocolVersion.v1_21_11);
    }

    public static boolean isLocalPlayer(Entity entity) {
        if (entity == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player == entity;
    }

    /**
     * True for the local player on a 1.21.11 target: inside-block effects must
     * not run inside {@code Entity.move} (legacy 1.16.4 phase). They are applied
     * once after {@code travel()} in {@code LivingEntity.livingTick}, mirroring
     * 1.21.11's {@code tickBlockCollision()} stage.
     */
    public static boolean shouldDeferInsideBlockEffects(Entity entity) {
        return shouldUseModernBlockEffects() && isLocalPlayer(entity);
    }

    /**
     * Pure bubble-column velocity transform, identical in 1.16.4 and 1.21.11:
     * {@code airAbove=true} is the "surface" variant
     * ({@code Entity.onEnterBubbleColumnWithAirAbove} /
     * {@code Entity.applyBubbleColumnSurfaceEffects}), {@code airAbove=false}
     * is the submerged variant ({@code Entity.onEnterBubbleColumn} /
     * {@code Entity.applyBubbleColumnEffects}).
     */
    public static double computeBubbleColumnY(double y, boolean drag, boolean airAbove) {
        if (airAbove) {
            return drag ? Math.max(-0.9D, y - 0.03D) : Math.min(1.8D, y + 0.1D);
        }

        return drag ? Math.max(-0.3D, y - 0.03D) : Math.min(0.7D, y + 0.06D);
    }
}
