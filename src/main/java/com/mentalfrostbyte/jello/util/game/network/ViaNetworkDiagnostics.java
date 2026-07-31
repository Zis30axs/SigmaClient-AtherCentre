package com.mentalfrostbyte.jello.util.game.network;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.State;
import io.netty.channel.Channel;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.play.client.CClientSettingsPacket;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.network.play.client.CKeepAlivePacket;
import net.minecraft.network.play.client.CResourcePackStatusPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Low-overhead, off-by-default diagnostics for the ViaMCP network path.
 *
 * <p>Enable with {@code -Dsigma.via.diagnostics=true}. All instrumentation is a
 * single static boolean check per hook, so the disabled cost is one branch.
 * Counters are {@link LongAdder}s; per-packet-class timings are stored in a
 * bounded-by-nature map keyed by packet simple names (never packet objects or
 * ByteBufs), so nothing leaks across connections.
 *
 * <p>The class also hosts three independent, always-available guards (each with
 * its own system property):
 * <ul>
 *   <li>join backpressure ({@code -Dsigma.viamcp.joinBackpressure=true})</li>
 *   <li>netty backlog sampling used to suppress/drop stale movement
 *       ({@code -Dsigma.viamcp.antiBurst=true})</li>
 *   <li>configuration-phase PLAY packet guard (no property; always on for Via
 *       connections, mirrors ViaBackwards' own remap allowlist)</li>
 * </ul>
 */
public final class ViaNetworkDiagnostics {
    private static final Logger LOGGER = LogManager.getLogger("ViaDiag");

    private static final boolean ENABLED = Boolean.getBoolean("sigma.via.diagnostics");
    private static final boolean ANTI_BURST = Boolean.parseBoolean(
            System.getProperty("sigma.viamcp.antiBurst", "true"));
    private static final boolean JOIN_BACKPRESSURE = Boolean.parseBoolean(
            System.getProperty("sigma.viamcp.joinBackpressure", "true"));

    /** Event-loop pending task thresholds used by the anti-burst guard. */
    private static final int BACKLOG_HIGH = 60;
    private static final int BACKLOG_LOW = 30;

    /** Main-thread task queue thresholds used by join backpressure. */
    private static final int MAIN_QUEUE_HIGH = 6000;
    private static final int MAIN_QUEUE_LOW = 3000;

    private static final long SAMPLE_INTERVAL_NANOS = 250_000_000L;
    private static final long PRINT_INTERVAL_NANOS = 1_000_000_000L;

    // ---- aggregated counters (reset once per second) ----
    private static final LongAdder RAW_S2C = new LongAdder();
    private static final LongAdder TRANSFORMED_S2C = new LongAdder();
    private static final LongAdder SCHEDULED_S2C = new LongAdder();
    private static final LongAdder PROCESSED_S2C = new LongAdder();
    private static final LongAdder RAW_C2S = new LongAdder();
    private static final LongAdder TRANSFORMED_C2S = new LongAdder();
    private static final LongAdder WRITTEN_C2S = new LongAdder();
    private static final LongAdder CANCELLED_C2S = new LongAdder();
    private static final LongAdder RESENT_C2S = new LongAdder();
    private static final LongAdder DROPPED_STATE_MISMATCH = new LongAdder();
    private static final LongAdder DROPPED_BACKLOG_MOVEMENT = new LongAdder();

    private static final ConcurrentHashMap<String, ClassStat> STATS = new ConcurrentHashMap<>();

    // ---- sampled state ----
    private static volatile NetworkManager activeManager;
    private static volatile int mainQueueSize;
    private static volatile int eventLoopPending;
    private static volatile boolean backlogged;
    private static volatile boolean backpressureActive;
    private static volatile int clientTicks;
    private static volatile long lastGcTimeMillis;

    private static final AtomicLong LAST_SAMPLE_NANOS = new AtomicLong();
    private static final AtomicLong LAST_PRINT_NANOS = new AtomicLong();
    private static final AtomicLong LAST_TICK_NANOS = new AtomicLong();
    private static boolean backpressureLogged;

    private ViaNetworkDiagnostics() {
    }

    // ------------------------------------------------------------------
    // Fast guards used by the actual fixes (always active, near-zero cost)
    // ------------------------------------------------------------------

    public static boolean shouldSuppressMovementPackets() {
        return ANTI_BURST && backlogged;
    }

    public static boolean shouldDropStaleMovement() {
        return ANTI_BURST && backlogged;
    }

    public static boolean isBacklogged() {
        return backlogged;
    }

    /**
     * Configuration-phase guard: while Via's server state is CONFIGURATION the
     * wire protocol has no PLAY packet registry. ViaBackwards explicitly remaps
     * only CLIENT_INFORMATION / CUSTOM_PAYLOAD / KEEP_ALIVE / PONG /
     * RESOURCE_PACK, so any other 1.16 PLAY packet would be serialized with a
     * PLAY id into a CONFIGURATION stream and Velocity would kick with
     * "An internal error occurred in your connection."
     */
    public static boolean shouldDropPlayPacketDuringConfiguration(NetworkManager networkManager, IPacket<?> packet) {
        if (ProtocolType.getFromPacket(packet) != ProtocolType.PLAY) {
            return false;
        }

        if (isConfigSafe(packet)) {
            return false;
        }

        UserConnection user = networkManager.getViaUserConnection();
        if (user == null || user.getProtocolInfo() == null
                || user.getProtocolInfo().getServerState() != State.CONFIGURATION) {
            return false;
        }

        DROPPED_STATE_MISMATCH.increment();
        return true;
    }

    private static boolean isConfigSafe(IPacket<?> packet) {
        return packet instanceof CKeepAlivePacket
                || packet instanceof CClientSettingsPacket
                || packet instanceof CCustomPayloadPacket
                || packet instanceof CResourcePackStatusPacket;
    }

    // ------------------------------------------------------------------
    // Instrumentation hooks
    // ------------------------------------------------------------------

    public static long startTiming() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void rawS2C(IPacket<?> packet, long startNanos) {
        if (!ENABLED) {
            return;
        }

        RAW_S2C.increment();
        recordStat("S2C." + packet.getClass().getSimpleName(), System.nanoTime() - startNanos);
    }

    public static void transformedS2C(long startNanos) {
        if (!ENABLED) {
            return;
        }

        TRANSFORMED_S2C.increment();
        recordStat("S2C.Transform", System.nanoTime() - startNanos);
    }

    public static void scheduledS2C(IPacket<?> packet) {
        if (ENABLED) {
            SCHEDULED_S2C.increment();
        }
    }

    public static void processedS2C(IPacket<?> packet, long startNanos) {
        if (!ENABLED) {
            return;
        }

        PROCESSED_S2C.increment();
        recordStat("MT." + packet.getClass().getSimpleName(), System.nanoTime() - startNanos);
    }

    public static void rawC2S() {
        if (ENABLED) {
            RAW_C2S.increment();
        }
    }

    public static void cancelledC2S() {
        if (ENABLED) {
            CANCELLED_C2S.increment();
        }
    }

    public static void transformedC2S(long startNanos) {
        if (!ENABLED) {
            return;
        }

        TRANSFORMED_C2S.increment();
        recordStat("C2S.Transform", System.nanoTime() - startNanos);
    }

    public static void writtenC2S() {
        if (ENABLED) {
            WRITTEN_C2S.increment();
        }
    }

    public static void resentC2S() {
        if (ENABLED) {
            RESENT_C2S.increment();
        }
    }

    private static void recordStat(String name, long nanos) {
        if (nanos < 0L) {
            nanos = 0L;
        }

        ClassStat stat = STATS.computeIfAbsent(name, ignored -> new ClassStat());
        stat.count.increment();
        stat.totalNanos.add(nanos);
        stat.maxNanos.accumulateAndGet(nanos, Math::max);
    }

    // ------------------------------------------------------------------
    // Per-client-tick sampling and printing (main thread only)
    // ------------------------------------------------------------------

    public static void onConnectionTick(NetworkManager networkManager) {
        if (!ENABLED) {
            // Anti-burst / backpressure still need periodic sampling.
            if (!ANTI_BURST && !JOIN_BACKPRESSURE) {
                return;
            }
        } else {
            clientTicks++;
        }

        if (networkManager == null || networkManager.hasNoChannel()) {
            return;
        }

        long now = System.nanoTime();
        if (now - LAST_SAMPLE_NANOS.get() < SAMPLE_INTERVAL_NANOS) {
            return;
        }
        LAST_SAMPLE_NANOS.set(now);

        sampleQueueSizes(networkManager);
        applyJoinBackpressure(networkManager);

        if (ENABLED && now - LAST_PRINT_NANOS.get() >= PRINT_INTERVAL_NANOS) {
            LAST_PRINT_NANOS.set(now);
            printSnapshot(networkManager);
        }
    }

    private static void sampleQueueSizes(NetworkManager networkManager) {
        if (ENABLED || ANTI_BURST || JOIN_BACKPRESSURE) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mainQueueSize = mc.getQueueSize();
            }
        }

        UserConnection user = networkManager.getViaUserConnection();
        Channel channel = user == null ? null : user.getChannel();
        if (channel != null) {
            eventLoopPending = pendingTasks(channel);
            backlogged = eventLoopPending > BACKLOG_HIGH;
            if (eventLoopPending <= BACKLOG_LOW) {
                backlogged = false;
            }
        }
    }

    private static void applyJoinBackpressure(NetworkManager networkManager) {
        if (!JOIN_BACKPRESSURE || networkManager.getViaUserConnection() == null) {
            return;
        }

        if (!backpressureActive && mainQueueSize > MAIN_QUEUE_HIGH) {
            backpressureActive = true;
            backpressureLogged = false;
            networkManager.disableAutoRead();
            if (ENABLED || !backpressureLogged) {
                LOGGER.warn("[ViaDiag] Main thread queue={} above {}, disabling autoRead until it drains",
                        mainQueueSize, MAIN_QUEUE_HIGH);
                backpressureLogged = true;
            }
        } else if (backpressureActive && mainQueueSize < MAIN_QUEUE_LOW) {
            backpressureActive = false;
            // Re-enable reads without touching the protocol attribute.
            networkManager.getViaUserConnection().getChannel().config().setAutoRead(true);
            if (ENABLED) {
                LOGGER.info("[ViaDiag] Main thread queue={} drained, autoRead re-enabled", mainQueueSize);
            }
        }
    }

    private static int pendingTasks(Channel channel) {
        try {
            if (channel.eventLoop() instanceof SingleThreadEventExecutor) {
                return ((SingleThreadEventExecutor) channel.eventLoop()).pendingTasks();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static void printSnapshot(NetworkManager networkManager) {
        UserConnection user = networkManager.getViaUserConnection();
        State clientState = user != null && user.getProtocolInfo() != null
                ? user.getProtocolInfo().getClientState() : null;
        State serverState = user != null && user.getProtocolInfo() != null
                ? user.getProtocolInfo().getServerState() : null;
        String target = user != null && user.getProtocolInfo() != null
                ? user.getProtocolInfo().protocolVersion().getName() : "native";

        long gcDelta = gcDeltaMillis();
        List<ClassStat.Entry> slow = slowest(10);

        StringBuilder sb = new StringBuilder(320);
        sb.append("[ViaDiag]\n");
        sb.append("state=").append(serverState).append(" protocol=").append(target)
                .append(" fps=").append(Minecraft.getFps()).append(" clientTicks=").append(clientTicks)
                .append(" gcMs=").append(gcDelta).append('\n');
        sb.append("S2C raw=").append(RAW_S2C.sumThenReset())
                .append(" transformed=").append(TRANSFORMED_S2C.sumThenReset())
                .append(" scheduled=").append(SCHEDULED_S2C.sumThenReset())
                .append(" processed=").append(PROCESSED_S2C.sumThenReset()).append('\n');
        sb.append("C2S raw=").append(RAW_C2S.sumThenReset())
                .append(" transformed=").append(TRANSFORMED_C2S.sumThenReset())
                .append(" written=").append(WRITTEN_C2S.sumThenReset())
                .append(" cancelled=").append(CANCELLED_C2S.sumThenReset())
                .append(" resent=").append(RESENT_C2S.sumThenReset())
                .append(" stateDrops=").append(DROPPED_STATE_MISMATCH.sumThenReset())
                .append(" backlogDrops=").append(DROPPED_BACKLOG_MOVEMENT.sumThenReset()).append('\n');
        sb.append("mainQueue=").append(mainQueueSize)
                .append(" eventLoopPending=").append(eventLoopPending)
                .append(" backlogged=").append(backlogged)
                .append(" clientState=").append(clientState).append('\n');
        UserConnection pipelineUser = networkManager.getViaUserConnection();
        sb.append("pipeline=").append(pipelineUser != null && pipelineUser.getChannel() != null
                ? pipelineUser.getChannel().pipeline().names() : "<none>").append('\n');

        if (!slow.isEmpty()) {
            sb.append("slowPackets:\n");
            for (ClassStat.Entry entry : slow) {
                sb.append("  ").append(entry.name)
                        .append(" count=").append(entry.count)
                        .append(" total=").append(entry.totalNanos / 1_000_000L).append("ms")
                        .append(" max=").append(entry.maxNanos / 1_000_000L).append("ms\n");
            }
        }

        LOGGER.info(sb.toString().trim());
        clientTicks = 0;
    }

    private static List<ClassStat.Entry> slowest(int limit) {
        List<ClassStat.Entry> entries = new ArrayList<>(STATS.size());
        for (ConcurrentHashMap.Entry<String, ClassStat> entry : STATS.entrySet()) {
            ClassStat stat = entry.getValue();
            entries.add(new ClassStat.Entry(entry.getKey(), stat.count.sum(), stat.totalNanos.sum(),
                    stat.maxNanos.get()));
        }
        entries.sort(Comparator.comparingLong((ClassStat.Entry e) -> e.totalNanos).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private static long gcDeltaMillis() {
        try {
            long total = 0L;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                total += bean.getCollectionTime();
            }
            long delta = total - lastGcTimeMillis;
            lastGcTimeMillis = total;
            return delta;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    public static void detach(NetworkManager networkManager) {
        if (!ENABLED && !ANTI_BURST && !JOIN_BACKPRESSURE) {
            return;
        }

        if (activeManager == networkManager) {
            activeManager = null;
        }

        if (ENABLED) {
            RAW_S2C.reset();
            TRANSFORMED_S2C.reset();
            SCHEDULED_S2C.reset();
            PROCESSED_S2C.reset();
            RAW_C2S.reset();
            TRANSFORMED_C2S.reset();
            WRITTEN_C2S.reset();
            CANCELLED_C2S.reset();
            RESENT_C2S.reset();
            DROPPED_STATE_MISMATCH.reset();
            DROPPED_BACKLOG_MOVEMENT.reset();
            STATS.clear();
        }

        backlogged = false;
        backpressureActive = false;
        mainQueueSize = 0;
        eventLoopPending = 0;
        clientTicks = 0;
        LAST_SAMPLE_NANOS.set(0L);
        LAST_PRINT_NANOS.set(0L);
    }

    public static void droppedBacklogMovement() {
        if (ENABLED) {
            DROPPED_BACKLOG_MOVEMENT.increment();
        }
    }

    private static final class ClassStat {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private static final class Entry {
            private final String name;
            private final long count;
            private final long totalNanos;
            private final long maxNanos;

            private Entry(String name, long count, long totalNanos, long maxNanos) {
                this.name = name;
                this.count = count;
                this.totalNanos = totalNanos;
                this.maxNanos = maxNanos;
            }
        }
    }
}
