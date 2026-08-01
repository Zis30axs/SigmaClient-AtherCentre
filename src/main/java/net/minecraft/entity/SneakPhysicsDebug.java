package net.minecraft.entity;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.viamcp.fixes.PacketFixFor1_21Plus;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Default-off diagnostics for the 1.21.x sneak / sneak-jump / edge-backoff
 * compatibility path. Enabled with {@code -Dsigma.sneakPhysicsDebug=true}.
 *
 * <p>Only tracks the local player, is rate limited (one block per 500 ms) and
 * keeps a fixed-size ring of flattened log lines. Never stores Entity, World,
 * BlockState, Packet or ByteBuf references across calls; all values are
 * flattened into the log line at capture time.
 */
public final class SneakPhysicsDebug {

    private static final Logger LOGGER = LogManager.getLogger("SneakPhysics");
    private static final String ENABLED_PROPERTY = "sigma.sneakPhysicsDebug";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final long MIN_INTERVAL_MS = 500L;
    private static final int RING_CAPACITY = 64;

    private static long lastLogTimeMs = Long.MIN_VALUE;
    private static final String[] ring = new String[RING_CAPACITY];
    private static int ringWriteIndex;
    private static int ringCount;

    private static int slotEntityId = Integer.MIN_VALUE;
    private static long tickId;
    private static String behavior = "LEGACY";
    private static boolean modernGate;
    private static boolean safeWalkEvent;
    private static float stepHeight;
    private static boolean sneaking;
    private static boolean crouching;
    private static boolean steppingCarefully;
    private static boolean onGroundBefore;
    private static boolean jumping;
    private static boolean flying;
    private static float inputForward;
    private static float inputStrafe;
    private static boolean inputSneaking;
    private static boolean inputJumping;
    private static String pose = "?";
    private static String box = "?";
    private static String blockBelow = "?";
    private static String thread = "?";
    private static double startX;
    private static double startY;
    private static double startZ;
    private static Vector3d requested;
    private static Vector3d afterBackoff;
    private static boolean backoffRan;
    private static int iterationsX;
    private static int iterationsZ;
    private static int iterationsXZ;

    private SneakPhysicsDebug() {
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

    /** Called at the start of {@code Entity.move} for the local player. */
    public static void beginMove(Entity entity) {
        if (!ENABLED || !isLocalPlayer(entity)) {
            return;
        }

        slotEntityId = entity.getEntityId();
        tickId = entity.ticksExisted;
        thread = Thread.currentThread().getName();
        startX = entity.getPosX();
        startY = entity.getPosY();
        startZ = entity.getPosZ();
        requested = null;
        afterBackoff = null;
        backoffRan = false;
        iterationsX = 0;
        iterationsZ = 0;
        iterationsXZ = 0;
    }

    /**
     * Called from {@code PlayerEntity.maybeBackOffFromEdge} before the backoff
     * branch, so the state reflects the tick the edge clamp is about to run in.
     */
    public static void beginBackoff(PlayerEntity player, Vector3d move, float step, boolean safeWalk) {
        if (!ENABLED || !isLocalPlayer(player) || slotEntityId != player.getEntityId()) {
            return;
        }

        requested = move;
        stepHeight = step;
        safeWalkEvent = safeWalk;
        sneaking = player.isSneaking();
        crouching = player.isCrouching();
        steppingCarefully = player.isSteppingCarefully();
        onGroundBefore = player.onGround;
        jumping = player.isJumping;
        flying = player.abilities.isFlying;
        pose = String.valueOf(player.getPose());
        AxisAlignedBB bb = player.getBoundingBox();
        box = fmt(bb.minX) + "," + fmt(bb.minY) + "," + fmt(bb.minZ)
                + "->" + fmt(bb.maxX) + "," + fmt(bb.maxY) + "," + fmt(bb.maxZ);
        ProtocolVersion target = JelloPortal.getVersion();
        modernGate = PacketFixFor1_21Plus.shouldUseVanilla1_21MovementPhysics();
        behavior = target == null ? "?" : target.olderThanOrEqualTo(ProtocolVersion.v1_20_3) ? "LEGACY"
                : target.newerThanOrEqualTo(ProtocolVersion.v1_21_5) ? "POST_1_21_5" : "PRE_1_21_5";

        if (player instanceof ClientPlayerEntity) {
            ClientPlayerEntity client = (ClientPlayerEntity) player;

            if (client.movementInput != null) {
                inputForward = client.movementInput.moveForward;
                inputStrafe = client.movementInput.moveStrafe;
                inputSneaking = client.movementInput.sneaking;
                inputJumping = client.movementInput.jump;
            }
        }

        Block block = player.world.getBlockState(player.getPositionUnderneath()).getBlock();
        blockBelow = Registry.BLOCK.getKey(block) == null ? "?" : Registry.BLOCK.getKey(block).toString();
    }

    /** Called from {@code PlayerEntity.maybeBackOffFromEdge} after the branch. */
    public static void finishBackoff(PlayerEntity player, Vector3d result, boolean ran,
                                     int iterX, int iterZ, int iterXZ) {
        if (!ENABLED || !isLocalPlayer(player) || slotEntityId != player.getEntityId()) {
            return;
        }

        afterBackoff = result;
        backoffRan = ran;
        iterationsX = iterX;
        iterationsZ = iterZ;
        iterationsXZ = iterXZ;
    }

    /** Called at the end of {@code Entity.move} for the local player. */
    public static void finishMove(Entity entity, Vector3d collisionResolved, boolean horizontalCollision,
                                  boolean verticalCollision, Vector3d finalMotion) {
        if (!ENABLED || !isLocalPlayer(entity) || slotEntityId != entity.getEntityId()) {
            return;
        }

        slotEntityId = Integer.MIN_VALUE;

        if (!sneaking && !backoffRan && !horizontalCollision) {
            return;
        }

        StringBuilder sb = new StringBuilder(640);
        sb.append("[SneakPhysics]")
                .append(" tick=").append(tickId)
                .append(" target=").append(JelloPortal.getVersion())
                .append(" behavior=").append(behavior)
                .append(" modernGate=").append(modernGate)
                .append(" safeWalkEvent=").append(safeWalkEvent)
                .append(" thread=").append(thread)
                .append(" inputForward=").append(fmt(inputForward))
                .append(" inputStrafe=").append(fmt(inputStrafe))
                .append(" inputSneaking=").append(inputSneaking)
                .append(" inputJumping=").append(inputJumping)
                .append(" entitySneaking=").append(sneaking)
                .append(" entityCrouching=").append(crouching)
                .append(" steppingCarefully=").append(steppingCarefully)
                .append(" onGroundBefore=").append(onGroundBefore)
                .append(" onGroundAfter=").append(entity.onGround)
                .append(" jumping=").append(jumping)
                .append(" flying=").append(flying)
                .append(" pose=").append(pose)
                .append(" box=").append(box)
                .append(" blockBelow=").append(blockBelow)
                .append(" stepHeight=").append(fmt(stepHeight))
                .append(" requestedMove=").append(describe(requested))
                .append(" afterBackoff=").append(describe(afterBackoff))
                .append(" backoffRan=").append(backoffRan)
                .append(" iterationsX=").append(iterationsX)
                .append(" iterationsZ=").append(iterationsZ)
                .append(" iterationsXZ=").append(iterationsXZ)
                .append(" collisionResolved=").append(describe(collisionResolved))
                .append(" finalDelta=").append(describe(new Vector3d(
                        entity.getPosX() - startX, entity.getPosY() - startY, entity.getPosZ() - startZ)))
                .append(" finalMotion=").append(describe(finalMotion))
                .append(" horizontalCollision=").append(horizontalCollision)
                .append(" verticalCollision=").append(verticalCollision);
        String line = sb.toString();

        ring[ringWriteIndex] = line;
        ringWriteIndex = (ringWriteIndex + 1) % RING_CAPACITY;

        if (ringCount < RING_CAPACITY) {
            ringCount++;
        }

        long now = System.currentTimeMillis();

        if (now - lastLogTimeMs >= MIN_INTERVAL_MS) {
            lastLogTimeMs = now;
            LOGGER.info(line);
        }
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
