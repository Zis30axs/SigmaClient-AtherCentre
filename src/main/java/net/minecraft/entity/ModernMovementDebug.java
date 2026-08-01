package net.minecraft.entity;

import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

/**
 * Default-off diagnostics for the 1.21.11 bubble-column movement path.
 * Enabled with {@code -Dsigma.bubbleCompatDebug=true}.
 *
 * <p>Only tracks the local player. Rate limited (one block per 500 ms) and
 * uses a fixed-size ring for per-application velocity deltas. Never stores
 * Entity, World, BlockState or Vector references between ticks; all values
 * are flattened into the log line.
 */
public final class ModernMovementDebug {

    private static final Logger LOGGER = LogManager.getLogger("BubbleCompat");
    private static final String ENABLED_PROPERTY = "sigma.bubbleCompatDebug";
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    private static final long MIN_INTERVAL_MS = 500L;
    private static final int MAX_TRACKED_APPLICATIONS = 8;
    private static final int DETAIL_CAP = 1024;
    private static final double INSIDE_BLOCK_MARGIN = 9.999999747378752E-6D;
    private static long lastLogTimeMs = Long.MIN_VALUE;

    private static final StringBuilder detail = new StringBuilder(DETAIL_CAP);
    private static final boolean[] appDrag = new boolean[MAX_TRACKED_APPLICATIONS];
    private static final boolean[] appAirAbove = new boolean[MAX_TRACKED_APPLICATIONS];
    private static final double[] appYBefore = new double[MAX_TRACKED_APPLICATIONS];
    private static final double[] appYAfter = new double[MAX_TRACKED_APPLICATIONS];
    private static final String[] appPhase = new String[MAX_TRACKED_APPLICATIONS];
    private static int appWriteIndex;
    private static int applicationCount;
    private static String currentPhase = "in-move";

    private ModernMovementDebug() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Called once per tick for the local player before travel. Clears the
     * per-tick trace and the application ring.
     */
    public static void resetTick(Entity entity) {
        if (!ENABLED || !ModernMovementPhysics.isLocalPlayer(entity)) {
            return;
        }

        detail.setLength(0);
        appWriteIndex = 0;
        applicationCount = 0;
        currentPhase = "in-move";
    }

    /** Marks that inside-block effects are now applied after travel. */
    public static void beginPostTravelPhase(Entity entity) {
        if (!ENABLED || !ModernMovementPhysics.isLocalPlayer(entity)) {
            return;
        }

        currentPhase = "post-travel";
    }

    /**
     * Appends one velocity trace point (e.g. requested move, collision-resolved
     * vector, after fluid travel). No-op when disabled.
     */
    public static void captureMotion(Entity entity, String phase, Vector3d motion) {
        if (!ENABLED || !ModernMovementPhysics.isLocalPlayer(entity) || motion == null) {
            return;
        }

        if (detail.length() >= DETAIL_CAP) {
            return;
        }

        detail.append("  ").append(phase).append('=')
                .append(describe(motion)).append('\n');
    }

    /**
     * Records one bubble-column velocity application. Called from
     * {@code Entity.onEnterBubbleColumn*} so the count and per-application
     * before/after Y values are exact.
     */
    public static void trackBubbleEffect(Entity entity, boolean drag, boolean airAbove,
                                         double yBefore, double yAfter) {
        if (!ENABLED || !ModernMovementPhysics.isLocalPlayer(entity)) {
            return;
        }

        int index = appWriteIndex % MAX_TRACKED_APPLICATIONS;
        appDrag[index] = drag;
        appAirAbove[index] = airAbove;
        appYBefore[index] = yBefore;
        appYAfter[index] = yAfter;
        appPhase[index] = currentPhase;
        appWriteIndex++;
        applicationCount++;
    }

    /**
     * Emits one rate-limited diagnostics block. Only logs when the player is in
     * water / touches a bubble column / performed a swim-hop candidate /
     * collided horizontally.
     */
    public static void logTick(LivingEntity entity, String phase, Vector3d motionAtTickStart,
                               boolean swimHop, boolean horizontalCollision, boolean onGround,
                               double startX, double startY, double startZ) {
        if (!ENABLED || entity == null) {
            return;
        }

        if (!entity.isInWater() && !swimHop && !horizontalCollision) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs < MIN_INTERVAL_MS) {
            return;
        }
        lastLogTimeMs = now;

        BubbleInfo info = collectBubbleInfo(entity);
        if (!info.bubble && !swimHop && !horizontalCollision) {
            return;
        }

        Vector3d motion = entity.getMotion();
        StringBuilder sb = new StringBuilder(512);
        sb.append("\n[BubbleCompat]\n");
        sb.append("tick=").append(entity.ticksExisted).append('\n');
        sb.append("target=").append(describeTarget()).append('\n');
        sb.append("phase=").append(phase).append('\n');
        sb.append("bubble=").append(info.bubble).append('\n');
        sb.append("drag=").append(info.bubble ? info.drag : "n/a").append('\n');
        sb.append("source=").append(info.bubble ? info.source : "n/a").append('\n');
        sb.append("horizontalCollision=").append(horizontalCollision).append('\n');
        sb.append("onGround=").append(onGround).append('\n');
        sb.append("inWater=").append(entity.isInWater()).append('\n');
        sb.append("swimming=").append(entity.isSwimming()).append('\n');
        sb.append("jumping=").append(entity.isJumping).append('\n');
        sb.append("sprinting=").append(entity.isSprinting()).append('\n');
        sb.append("motionAtTickStart=").append(describe(motionAtTickStart)).append('\n');
        sb.append("finalMotion=").append(describe(motion)).append('\n');
        sb.append("positionDelta=(").append(format(entity.getPosX() - startX)).append(',')
                .append(format(entity.getPosY() - startY)).append(',')
                .append(format(entity.getPosZ() - startZ)).append(")\n");
        sb.append("applicationCount=").append(applicationCount).append('\n');

        int shown = Math.min(applicationCount, MAX_TRACKED_APPLICATIONS);
        for (int i = 0; i < shown; ++i) {
            int index = (appWriteIndex - shown + i + MAX_TRACKED_APPLICATIONS) % MAX_TRACKED_APPLICATIONS;
            sb.append("application#").append(i + 1)
                    .append(" phase=").append(appPhase[index])
                    .append(" drag=").append(appDrag[index])
                    .append(" airAbove=").append(appAirAbove[index])
                    .append(" motionYBefore=").append(format(appYBefore[index]))
                    .append(" motionYAfter=").append(format(appYAfter[index]))
                    .append('\n');
        }

        sb.append("bubbleBlocks=").append(info.blocks.length() == 0 ? "[]" : info.blocks).append('\n');
        sb.append("motionTrace:\n").append(detail.length() == 0 ? "  (none)\n" : detail);
        sb.append("thread=").append(Thread.currentThread().getName());
        LOGGER.info(sb.toString());
    }

    private static String describeTarget() {
        if (ViaLoadingBase.getInstance() == null
                || ViaLoadingBase.getInstance().getTargetVersion() == null) {
            return "unknown";
        }

        return ViaLoadingBase.getInstance().getTargetVersion().getName();
    }

    private static String describe(Vector3d vector) {
        return vector == null
                ? "(n/a)"
                : "(" + format(vector.x) + "," + format(vector.y) + "," + format(vector.z) + ")";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static BubbleInfo collectBubbleInfo(LivingEntity entity) {
        BubbleInfo info = new BubbleInfo();
        World world = entity.world;

        if (world == null) {
            return info;
        }

        AxisAlignedBB axisalignedbb = entity.getBoundingBox();
        BlockPos from = new BlockPos(axisalignedbb.minX + INSIDE_BLOCK_MARGIN,
                axisalignedbb.minY + INSIDE_BLOCK_MARGIN, axisalignedbb.minZ + INSIDE_BLOCK_MARGIN);
        BlockPos to = new BlockPos(axisalignedbb.maxX - INSIDE_BLOCK_MARGIN,
                axisalignedbb.maxY - INSIDE_BLOCK_MARGIN, axisalignedbb.maxZ - INSIDE_BLOCK_MARGIN);

        if (!world.isAreaLoaded(from, to)) {
            return info;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = from.getX(); x <= to.getX(); ++x) {
            for (int y = from.getY(); y <= to.getY(); ++y) {
                for (int z = from.getZ(); z <= to.getZ(); ++z) {
                    mutable.setPos(x, y, z);
                    BlockState state = world.getBlockState(mutable);

                    if (!state.isIn(Blocks.BUBBLE_COLUMN)) {
                        continue;
                    }

                    boolean drag = state.get(BubbleColumnBlock.DRAG);
                    String source = bubbleSource(world, mutable);

                    if (!info.bubble) {
                        info.bubble = true;
                        info.drag = drag;
                        info.source = source;
                    }

                    info.count++;

                    if (info.count <= 8) {
                        info.blocks.append("(").append(x).append(',').append(y).append(',').append(z)
                                .append("):drag=").append(drag)
                                .append(":source=").append(source)
                                .append(' ');
                    }
                }
            }
        }

        return info;
    }

    private static String bubbleSource(World world, BlockPos pos) {
        BlockPos below = pos.down();

        for (int i = 0; i < 4; ++i) {
            BlockState state = world.getBlockState(below);

            if (state.isIn(Blocks.SOUL_SAND)) {
                return "SOUL_SAND";
            }

            if (state.isIn(Blocks.MAGMA_BLOCK)) {
                return "MAGMA";
            }

            if (!state.isIn(Blocks.BUBBLE_COLUMN)) {
                return "UNKNOWN";
            }

            below = below.down();
        }

        return "UNKNOWN";
    }

    private static final class BubbleInfo {
        private boolean bubble;
        private boolean drag;
        private String source = "UNKNOWN";
        private int count;
        private final StringBuilder blocks = new StringBuilder();
    }
}
