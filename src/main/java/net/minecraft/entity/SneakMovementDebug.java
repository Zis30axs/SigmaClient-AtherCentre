package net.minecraft.entity;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Default-off continuous-tick diagnostics for the sneak-jump / air-input
 * compatibility path. Enabled with {@code -Dsigma.debug.sneakMovement=true}.
 *
 * <p>Starts recording on the tick a grounded player presses jump and logs the
 * next {@value #RECORD_TICKS} ticks unconditionally (no rate limiting) so a
 * single jump is fully visible. All values are flattened to primitives at
 * capture time; no Entity, World or packet references are kept across calls.
 */
public final class SneakMovementDebug {

    private static final Logger LOGGER = LogManager.getLogger("SneakMovementDebug");
    private static final String ENABLED_PROPERTY = "sigma.debug.sneakMovement";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final int RECORD_TICKS = 25;

    private static boolean recording;
    private static int ticksLeft;
    private static int slotEntityId = Integer.MIN_VALUE;
    private static double startX;
    private static double startY;
    private static double startZ;

    private static long tick;
    private static String target = "?";
    private static boolean onGround;
    private static double motionY;
    private static float fallDistance;
    private static boolean isCrouching;
    private static boolean isForcedDown;
    private static boolean inputSneaking;
    private static boolean forwardKeyDown;
    private static boolean backKeyDown;
    private static boolean leftKeyDown;
    private static boolean rightKeyDown;
    private static boolean jump;
    private static boolean sprintKey;
    private static boolean isSprinting;
    private static float rawForward;
    private static float rawStrafe;
    private static float afterNormalizeForward;
    private static float afterNormalizeStrafe;
    private static float afterSneakForward;
    private static float afterSneakStrafe;
    private static float afterItemUseForward;
    private static float afterItemUseStrafe;
    private static float beforeLiving098;
    private static float afterLiving098;
    private static float offGroundSpeed;
    private static String motionBeforeTravel = "?";
    private static String motionAfterInput = "?";
    private static String motionAfterDrag = "?";
    private static double requestedMoveX;
    private static double requestedMoveY;
    private static double requestedMoveZ;
    private static double actualDeltaX;
    private static double actualDeltaY;
    private static double actualDeltaZ;
    private static boolean hasRequestedMove;
    private static int playerInputFlags;
    private static boolean playerInputPacketSent;
    private static String movementPacketType = "none";

    private SneakMovementDebug() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    private static boolean isLocalPlayer(Entity entity) {
        if (entity == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player == entity;
    }

    /**
     * Called from {@code ClientPlayerEntity.livingTick} after the crouching pose
     * is derived but before {@code movementInput.tickMovement} samples this
     * tick's keys, so the captured key/sneak state is the pre-tick one (the same
     * source vanilla uses for {@code wasShiftKeyDown} / {@code crouching}).
     */
    public static void beginTick(ClientPlayerEntity player) {
        if (!ENABLED || !isLocalPlayer(player)) {
            return;
        }

        slotEntityId = player.getEntityId();
        tick = player.ticksExisted;
        target = String.valueOf(JelloPortal.getVersion());
        onGround = player.onGround;
        motionY = player.getMotion().y;
        fallDistance = player.fallDistance;
        isCrouching = player.isCrouching();
        isForcedDown = player.isForcedDown();
        inputSneaking = player.movementInput != null && player.movementInput.sneaking;
        forwardKeyDown = player.movementInput != null && player.movementInput.forwardKeyDown;
        backKeyDown = player.movementInput != null && player.movementInput.backKeyDown;
        leftKeyDown = player.movementInput != null && player.movementInput.leftKeyDown;
        rightKeyDown = player.movementInput != null && player.movementInput.rightKeyDown;
        jump = player.movementInput != null && player.movementInput.jump;
        sprintKey = isSprintKeyDown();
        isSprinting = player.isSprinting();
    }

    /** Called from {@code MovementInputFromOptions} right after key assembly. */
    public static void captureRawInput(float forward, float strafe) {
        if (!ENABLED) {
            return;
        }

        rawForward = forward;
        rawStrafe = strafe;
    }

    /** Called from {@code MovementInputFromOptions} after 1.21.5+ normalize. */
    public static void captureNormalizedInput(float forward, float strafe) {
        if (!ENABLED) {
            return;
        }

        afterNormalizeForward = forward;
        afterNormalizeStrafe = strafe;
    }

    /** Called from {@code MovementInputFromOptions} after the sneak slowdown. */
    public static void captureSneakInput(float forward, float strafe) {
        if (!ENABLED) {
            return;
        }

        afterSneakForward = forward;
        afterSneakStrafe = strafe;
    }

    /** Called from {@code ClientPlayerEntity.livingTick} after item slowdown. */
    public static void captureItemUseInput(float forward, float strafe) {
        if (!ENABLED) {
            return;
        }

        afterItemUseForward = forward;
        afterItemUseStrafe = strafe;
    }

    /** Called before {@code super.livingTick()} (0.98/square travel factors). */
    public static void captureBeforeLiving(ClientPlayerEntity player) {
        if (!ENABLED || !isLocalPlayer(player)) {
            return;
        }

        beforeLiving098 = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        motionBeforeTravel = describe(player.getMotion());
    }

    /** Called after {@code super.livingTick()} (post-travel). */
    public static void captureAfterLiving(ClientPlayerEntity player, float offGroundSpeedIn) {
        if (!ENABLED || !isLocalPlayer(player)) {
            return;
        }

        afterLiving098 = player.movementInput == null ? 0.0F : player.movementInput.moveForward;
        offGroundSpeed = offGroundSpeedIn;
        if ("?".equals(motionAfterDrag)) {
            motionAfterDrag = describe(player.getMotion());
        }
    }

    /** Called from {@code LivingEntity.travel} right after the input stage. */
    public static void captureMotionAfterInput(Entity entity, Vector3d motion) {
        if (!ENABLED || !isLocalPlayer(entity)) {
            return;
        }

        motionAfterInput = describe(motion);
    }

    /** Called from {@code LivingEntity.travel} right after the drag stage. */
    public static void captureMotionAfterDrag(Entity entity) {
        if (!ENABLED || !isLocalPlayer(entity)) {
            return;
        }

        motionAfterDrag = describe(entity.getMotion());
    }

    /** Called from {@code Entity.move} with the post-backoff requested vector. */
    public static void captureRequestedMove(Entity entity, Vector3d requested) {
        if (!ENABLED || !isLocalPlayer(entity) || slotEntityId != entity.getEntityId()) {
            return;
        }

        startX = entity.getPosX();
        startY = entity.getPosY();
        startZ = entity.getPosZ();
        requestedMoveX = requested == null ? 0.0D : requested.x;
        requestedMoveY = requested == null ? 0.0D : requested.y;
        requestedMoveZ = requested == null ? 0.0D : requested.z;
        actualDeltaX = 0.0D;
        actualDeltaY = 0.0D;
        actualDeltaZ = 0.0D;
        hasRequestedMove = true;
    }

    /** Called from {@code Entity.move} after the bounding box was moved. */
    public static void captureMoveResult(Entity entity) {
        if (!ENABLED || !isLocalPlayer(entity) || slotEntityId != entity.getEntityId()) {
            return;
        }

        if (hasRequestedMove) {
            actualDeltaX = entity.getPosX() - startX;
            actualDeltaY = entity.getPosY() - startY;
            actualDeltaZ = entity.getPosZ() - startZ;
        }
    }

    /** Called from {@code ClientPlayerEntity.tick} after the input packet send. */
    public static void capturePlayerInput(ClientPlayerEntity player, boolean sent) {
        if (!ENABLED || !isLocalPlayer(player)) {
            return;
        }

        int flags = 0;
        if (player.movementInput != null) {
            flags |= player.movementInput.forwardKeyDown ? 1 : 0;
            flags |= player.movementInput.backKeyDown ? 2 : 0;
            flags |= player.movementInput.leftKeyDown ? 4 : 0;
            flags |= player.movementInput.rightKeyDown ? 8 : 0;
            flags |= player.movementInput.jump ? 16 : 0;
            flags |= player.movementInput.sneaking ? 32 : 0;
        }
        flags |= isSprintKeyDown() ? 64 : 0;
        playerInputFlags = flags;
        playerInputPacketSent = sent;
    }

    /** Called from {@code ClientPlayerEntity.sendMovementPackets}. */
    public static void captureMovementPacketType(String type) {
        if (!ENABLED) {
            return;
        }

        movementPacketType = type;
    }

    /**
     * Called at the end of {@code ClientPlayerEntity.tick}. Starts recording on
     * the tick a grounded player holds jump (the pre-jump tick) and then logs
     * every tick for {@value #RECORD_TICKS} ticks without rate limiting.
     */
    public static void logTick(ClientPlayerEntity player) {
        if (!ENABLED || !isLocalPlayer(player) || slotEntityId != player.getEntityId()) {
            return;
        }

        boolean jumpHeld = player.movementInput != null && player.movementInput.jump;
        if (!recording) {
            if (!(player.onGround && jumpHeld)) {
                return;
            }

            recording = true;
            ticksLeft = RECORD_TICKS;
        }

        StringBuilder sb = new StringBuilder(900);
        sb.append("[SneakMovementDebug]")
                .append(" tick=").append(tick)
                .append(" targetProtocol=").append(target)
                .append(" onGround=").append(onGround)
                .append(" motionY=").append(fmt(motionY))
                .append(" fallDistance=").append(fmt(fallDistance))
                .append(" isCrouching=").append(isCrouching)
                .append(" isForcedDown=").append(isForcedDown)
                .append(" inputSneaking=").append(inputSneaking)
                .append(" forwardKeyDown=").append(forwardKeyDown)
                .append(" backKeyDown=").append(backKeyDown)
                .append(" leftKeyDown=").append(leftKeyDown)
                .append(" rightKeyDown=").append(rightKeyDown)
                .append(" jump=").append(jump)
                .append(" sprintKey=").append(sprintKey)
                .append(" isSprinting=").append(isSprinting)
                .append(" rawForward=").append(fmt(rawForward))
                .append(" rawStrafe=").append(fmt(rawStrafe))
                .append(" afterNormalizeForward=").append(fmt(afterNormalizeForward))
                .append(" afterNormalizeStrafe=").append(fmt(afterNormalizeStrafe))
                .append(" afterSneakForward=").append(fmt(afterSneakForward))
                .append(" afterSneakStrafe=").append(fmt(afterSneakStrafe))
                .append(" afterItemUseForward=").append(fmt(afterItemUseForward))
                .append(" afterItemUseStrafe=").append(fmt(afterItemUseStrafe))
                .append(" beforeLiving098=").append(fmt(beforeLiving098))
                .append(" afterLiving098=").append(fmt(afterLiving098))
                .append(" offGroundSpeed=").append(fmt(offGroundSpeed))
                .append(" motionBeforeTravel=").append(motionBeforeTravel)
                .append(" motionAfterInput=").append(motionAfterInput)
                .append(" motionAfterDrag=").append(motionAfterDrag)
                .append(" requestedMoveX=").append(fmt(requestedMoveX))
                .append(" requestedMoveY=").append(fmt(requestedMoveY))
                .append(" requestedMoveZ=").append(fmt(requestedMoveZ))
                .append(" actualDeltaX=").append(fmt(actualDeltaX))
                .append(" actualDeltaY=").append(fmt(actualDeltaY))
                .append(" actualDeltaZ=").append(fmt(actualDeltaZ))
                .append(" playerInputFlags=").append(playerInputFlags)
                .append(" playerInputPacketSent=").append(playerInputPacketSent)
                .append(" movementPacketType=").append(movementPacketType);
        LOGGER.info(sb.toString());

        if (--ticksLeft <= 0) {
            recording = false;
            slotEntityId = Integer.MIN_VALUE;
        }
    }

    private static boolean isSprintKeyDown() {
        Minecraft minecraft = Minecraft.getInstance();
        KeyBinding keyBinding = minecraft == null || minecraft.gameSettings == null
                ? null : minecraft.gameSettings.keyBindSprint;
        return keyBinding != null && keyBinding.isKeyDown();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String describe(Vector3d vector) {
        return vector == null ? "null" : "(" + fmt(vector.x) + "," + fmt(vector.y) + "," + fmt(vector.z) + ")";
    }
}
