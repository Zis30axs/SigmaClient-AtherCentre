package de.florianmichael.viamcp.fixes;

import com.mentalfrostbyte.jello.util.game.network.ViaNetworkDiagnostics;
import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2;
import com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4;
import com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientTickFix {
    private static final Logger LOGGER = LogManager.getLogger("ClientTickFix");
    private static final String ENABLED_PROPERTY = "sigma.viamcp.clientTickFix";
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugServerbound";
    private static int debugTickCounter;
    private static int failureCounter;
    private static boolean loggedSkippedClientTickEnd;

    private ClientTickFix() {
    }

    /**
     * Sends the 1.21.3+ zero-payload end-of-tick packet once per client tick.
     */
    public static void tick() {
        if (!isEnabled()) {
            return;
        }

        ProtocolVersion targetVersion = ViaLoadingBase.getInstance().getTargetVersion();
        if (!PacketFixFor1_21Plus.isAtLeast1_21_3Protocol(targetVersion)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        try {
            UserConnection connection = getActiveConnection(mc);
            if (!PacketFixFor1_21Plus.isPlayState(connection)) {
                return;
            }

            if (ViaNetworkDiagnostics.isBacklogged()) {
                // The event loop is still draining a backlog (initial join /
                // chunk burst). Queuing one CLIENT_TICK_END per tick here only
                // makes the recovery flush worse; the next non-backlogged tick
                // sends a fresh end-of-tick marker.
                return;
            }

            sendClientTickEnd(connection, targetVersion);
            ViaNetworkDiagnostics.resentC2S();
            failureCounter = 0;
        } catch (Exception e) {
            logFailure(e);
        }
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    private static UserConnection getActiveConnection(Minecraft mc) {
        ClientPlayNetHandler playHandler = mc.getConnection();
        if (playHandler != null) {
            NetworkManager networkManager = playHandler.getNetworkManager();
            if (networkManager != null) {
                UserConnection connection = networkManager.getViaUserConnection();
                if (connection != null) {
                    return connection;
                }
            }
        }

        return null;
    }

    /**
     * Picks the injection point for the synthetic CLIENT_TICK_END.
     *
     * <p>This client is always the <i>older</i> side (native 1.16.4 -&gt; newer server), so every
     * node on the 1.21.x ladder is a <b>ViaBackwards</b> {@code Protocol<server>To<client>} class:
     * {@code Protocol1_21_2To1_21}, {@code Protocol1_21_4To1_21_2}, {@code Protocol1_21_5To1_21_4},
     * {@code Protocol1_21_6To1_21_5}, then 1.21.7/1.21.9/1.21.11 on top. The identically-shaped
     * ViaVersion classes ({@code Protocol1_21_5To1_21_6} and friends) belong to the opposite
     * direction (newer client -&gt; older server) and are never in this pipeline - selecting one
     * makes {@link PacketFixFor1_21Plus#hasProtocol} fail and silently drops the packet.
     *
     * <p>{@code scheduleSendToServer(P)} applies the protocols <i>after</i> P, so the packet type
     * must be in P's server-side format. 1.21.7 - 1.21.11 introduce no serverbound play id
     * changes ({@code ServerboundPackets1_21_6} implements {@code ServerboundPacket1_21_9} as
     * well), which is why the 1.21.6 rung also covers every target above it.
     */
    private static void sendClientTickEnd(UserConnection connection, ProtocolVersion targetVersion) throws Exception {
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_6.CLIENT_TICK_END,
                    Protocol1_21_6To1_21_5.class, "1_21_6")) {
                return;
            }

            logSkippedClientTickEnd(connection, targetVersion);
            return;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_5.CLIENT_TICK_END,
                    Protocol1_21_5To1_21_4.class, "1_21_5")) {
                return;
            }

            logSkippedClientTickEnd(connection, targetVersion);
            return;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_4.CLIENT_TICK_END,
                    Protocol1_21_4To1_21_2.class, "1_21_4")) {
                return;
            }

            logSkippedClientTickEnd(connection, targetVersion);
            return;
        }

        if (!sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_2.CLIENT_TICK_END,
                Protocol1_21_2To1_21.class, "1_21_2")) {
            logSkippedClientTickEnd(connection, targetVersion);
        }
    }

    private static boolean sendClientTickEnd(UserConnection connection, ProtocolVersion targetVersion,
            ServerboundPacketType packetType, Class<? extends Protocol> protocolClass, String packetFamily)
            throws Exception {
        if (!PacketFixFor1_21Plus.hasProtocol(connection, protocolClass)) {
            return false;
        }

        PacketWrapper packet = PacketWrapper.create(packetType, null, connection);
        logClientTickEnd(targetVersion, packetFamily, protocolClass.getSimpleName());
        packet.scheduleSendToServer(protocolClass);
        return true;
    }

    private static void logClientTickEnd(ProtocolVersion targetVersion, String packetFamily, String protocolName) {
        if (Boolean.getBoolean(DEBUG_PROPERTY) && (++debugTickCounter % 20 == 1)) {
            LOGGER.info("[ClientTickFix] CLIENT_TICK_END target={} packetFamily={} viaProtocol={}",
                    targetVersion, packetFamily, protocolName);
        }
    }

    /**
     * A missing injection protocol disables tick-end framing outright, which every
     * 1.21.2+ anticheat reads as "client never ended its tick" (Grim PacketOrderO /
     * TickTimer flag on every packet that follows a movement). That must never be a
     * debug-only message again, so the first occurrence is a WARN with the live pipeline.
     */
    private static void logSkippedClientTickEnd(UserConnection connection, ProtocolVersion targetVersion) {
        if (!loggedSkippedClientTickEnd) {
            loggedSkippedClientTickEnd = true;
            LOGGER.warn("[ClientTickFix] No matching clientside Via protocol for target={}; CLIENT_TICK_END is NOT being sent. Active pipeline: {}",
                    targetVersion, pipelineNames(connection));
            return;
        }

        if (Boolean.getBoolean(DEBUG_PROPERTY) && (++debugTickCounter % 20 == 1)) {
            LOGGER.info("[ClientTickFix] Skipping CLIENT_TICK_END for target={} because no matching clientside Via protocol is active",
                    targetVersion);
        }
    }

    private static String pipelineNames(UserConnection connection) {
        if (connection == null || connection.getProtocolInfo() == null) {
            return "<none>";
        }

        StringBuilder names = new StringBuilder();
        for (Protocol protocol : connection.getProtocolInfo().getPipeline().pipes()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(protocol.getClass().getSimpleName());
        }
        return names.toString();
    }

    private static void logFailure(Exception e) {
        ++failureCounter;
        if (failureCounter == 1 || failureCounter % 100 == 0 || Boolean.getBoolean(DEBUG_PROPERTY)) {
            LOGGER.warn("[ClientTickFix] Failed to send CLIENT_TICK_END (failureCount={})", failureCounter, e);
        } else if (failureCounter % 20 == 0) {
            LOGGER.warn("[ClientTickFix] Failed to send CLIENT_TICK_END (failureCount={}): {}",
                    failureCounter, e.toString());
        }
    }
}
