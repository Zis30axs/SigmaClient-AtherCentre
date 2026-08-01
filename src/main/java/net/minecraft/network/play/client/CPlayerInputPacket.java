package net.minecraft.network.play.client;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.util.game.network.ViaNetworkDiagnostics;
import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2;
import com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4;
import com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.IServerPlayNetHandler;
import net.minecraft.util.MovementInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * 1.21.2+ {@code ServerboundPlayerInputPacket} expressed with 1.16.4 client
 * semantics. The modern wire format is a single flag byte of raw key states:
 * <pre>
 * forward=1, backward=2, left=4, right=8, jump=16, shift=32, sprint=64
 * </pre>
 * matching the vanilla {@code net.minecraft.world.entity.player.Input} record.
 *
 * <p>Unlike the legacy {@link CInputPacket} (float strafe/forward used while
 * riding), this packet is never decoded by the 1.16.4 integrated server; it is
 * only emitted through the Via pipeline into a 1.21.2+ target. The send state
 * follows vanilla {@code LocalPlayer.tick}: send once per tick when the flag
 * byte changes, starting from a fresh state on join/disconnect.
 *
 * <p>This class intentionally mirrors vanilla key semantics. In particular the
 * shift bit is the <b>raw shift key</b> ({@code movementInput.sneaking}) and is
 * reported while airborne too. Vanilla sends {@code keyPresses.shift()}
 * unconditionally in {@code LocalPlayer.tick} after {@code super.tick()};
 * gating it on {@code onGround} desynchronises the server prediction from the
 * client's 0.3 sneak slowdown and produces Grim Simulation on every direction
 * key pressed in the air.
 */
public class CPlayerInputPacket implements IPacket<IServerPlayNetHandler> {
    private static final Logger LOGGER = LogManager.getLogger("CPlayerInputPacket");

    private static final int FLAG_FORWARD = 1;
    private static final int FLAG_BACKWARD = 2;
    private static final int FLAG_LEFT = 4;
    private static final int FLAG_RIGHT = 8;
    private static final int FLAG_JUMP = 16;
    private static final int FLAG_SHIFT = 32;
    private static final int FLAG_SPRINT = 64;

    /**
     * Modern LocalPlayer only resends PLAYER_INPUT when {@code input.keyPresses}
     * changes. {@link Byte#MIN_VALUE} is not a valid mask so the first real
     * input always flushes.
     */
    private static byte lastSentPlayerInputFlags = Byte.MIN_VALUE;
    private static boolean hasSentPlayerInputFlags = false;

    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean shift;
    private boolean sprint;

    public CPlayerInputPacket() {
    }

    public CPlayerInputPacket(boolean forwardIn, boolean backwardIn, boolean leftIn, boolean rightIn,
                              boolean jumpIn, boolean shiftIn, boolean sprintIn) {
        this.forward = forwardIn;
        this.backward = backwardIn;
        this.left = leftIn;
        this.right = rightIn;
        this.jump = jumpIn;
        this.shift = shiftIn;
        this.sprint = sprintIn;
    }

    /**
     * Reads the raw packet data from the data stream (used only by the local
     * 1.16.4 integrated server, which never receives this packet).
     */
    public void readPacketData(PacketBuffer buf) throws IOException {
        int flags = buf.readByte();
        this.forward = (flags & FLAG_FORWARD) != 0;
        this.backward = (flags & FLAG_BACKWARD) != 0;
        this.left = (flags & FLAG_LEFT) != 0;
        this.right = (flags & FLAG_RIGHT) != 0;
        this.jump = (flags & FLAG_JUMP) != 0;
        this.shift = (flags & FLAG_SHIFT) != 0;
        this.sprint = (flags & FLAG_SPRINT) != 0;
    }

    /**
     * Writes the raw packet data to the data stream with the modern bit layout.
     */
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeByte(toFlags(this.forward, this.backward, this.left, this.right,
                this.jump, this.shift, this.sprint));
    }

    /**
     * Passes this Packet on to the NetHandler for processing (no-op on 1.16.4;
     * the modern packet is consumed by the target server through Via).
     */
    public void processPacket(IServerPlayNetHandler handler) {
    }

    /**
     * Reset on world/connection change so the next join flushes a fresh input
     * packet, mirroring vanilla {@code LocalPlayer.lastSentInput}.
     */
    public static void resetPlayerInputState() {
        lastSentPlayerInputFlags = Byte.MIN_VALUE;
        hasSentPlayerInputFlags = false;
    }

    /**
     * 1.21.2+ input-sync seam for {@code ClientPlayerEntity.tick}. Computes the
     * flag byte from the raw movement-input keys and schedules a
     * {@code ServerboundPlayerInputPacket} when the byte changed this tick.
     *
     * @return {@code true} when the modern packet was sent or is unnecessary
     *         (unchanged flags / backlog), {@code false} when the target is
     *         below 1.21.2 or the Via pipeline cannot carry it (callers then
     *         fall back to the legacy riding {@link CInputPacket}).
     */
    public static boolean sendPlayerInput(ClientPlayerEntity player) {
        if (player == null || player.connection == null) {
            return false;
        }

        ProtocolVersion targetVersion = JelloPortal.getVersion();
        if (targetVersion == null || targetVersion.olderThan(ProtocolVersion.v1_21_2)) {
            return false;
        }

        UserConnection connection = player.connection.getNetworkManager().getViaUserConnection();
        if (!isPlayState(connection)) {
            return false;
        }

        if (ViaNetworkDiagnostics.isBacklogged()) {
            // Skip while the event loop is draining an initial-join backlog;
            // a fresh PLAYER_INPUT is sent once the queue recovers.
            return true;
        }

        try {
            byte flags = playerInputFlags(player);
            if (hasSentPlayerInputFlags && flags == lastSentPlayerInputFlags) {
                return true;
            }

            Class<? extends Protocol> protocolClass = playerInputProtocol(targetVersion);
            if (protocolClass == null || !hasProtocol(connection, protocolClass)) {
                return false;
            }

            if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_6.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            } else if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            } else if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_4.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            } else {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_2.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            }

            lastSentPlayerInputFlags = flags;
            hasSentPlayerInputFlags = true;
            ViaNetworkDiagnostics.resentC2S();
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to send 1.21+ player input packet", e);
            return false;
        }
    }

    private static byte playerInputFlags(ClientPlayerEntity player) {
        MovementInput input = player.movementInput;
        int flags = 0;

        if (input != null) {
            // Raw key states, matching vanilla KeyboardInput.tick -> Input record.
            // movementInput.sneaking is the raw shift key (1.16.4 semantics) and
            // must be reported while airborne as well as on the ground.
            flags |= input.forwardKeyDown ? FLAG_FORWARD : 0;
            flags |= input.backKeyDown ? FLAG_BACKWARD : 0;
            flags |= input.leftKeyDown ? FLAG_LEFT : 0;
            flags |= input.rightKeyDown ? FLAG_RIGHT : 0;
            flags |= input.jump ? FLAG_JUMP : 0;
            flags |= input.sneaking ? FLAG_SHIFT : 0;
        }

        // Vanilla Input.sprint is the sprint KEY, not the sprint state.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameSettings != null && mc.gameSettings.keyBindSprint != null
                && mc.gameSettings.keyBindSprint.isKeyDown()) {
            flags |= FLAG_SPRINT;
        }

        return (byte) flags;
    }

    /**
     * This client is always the older side (1.16.4 native -> newer server), so
     * the pipeline holds the ViaBackwards {@code Protocol<server>To<client>}
     * nodes. The mirrored ViaVersion classes are the opposite direction and are
     * never present here.
     */
    private static Class<? extends Protocol> playerInputProtocol(ProtocolVersion targetVersion) {
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            // 1.21.7 - 1.21.11 add no serverbound play id changes, so this rung
            // covers them too.
            return Protocol1_21_6To1_21_5.class;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return Protocol1_21_5To1_21_4.class;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return Protocol1_21_4To1_21_2.class;
        }

        return Protocol1_21_2To1_21.class;
    }

    private static boolean isPlayState(UserConnection connection) {
        return connection != null
                && connection.isActive()
                && !connection.isPendingDisconnect()
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getClientState() == State.PLAY
                && connection.getProtocolInfo().getServerState() == State.PLAY;
    }

    private static boolean hasProtocol(UserConnection connection, Class<? extends Protocol> protocolClass) {
        return connection != null
                && protocolClass != null
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getPipeline().contains(protocolClass);
    }

    private static byte toFlags(boolean forwardIn, boolean backwardIn, boolean leftIn, boolean rightIn,
                                boolean jumpIn, boolean shiftIn, boolean sprintIn) {
        int flags = 0;
        flags |= forwardIn ? FLAG_FORWARD : 0;
        flags |= backwardIn ? FLAG_BACKWARD : 0;
        flags |= leftIn ? FLAG_LEFT : 0;
        flags |= rightIn ? FLAG_RIGHT : 0;
        flags |= jumpIn ? FLAG_JUMP : 0;
        flags |= shiftIn ? FLAG_SHIFT : 0;
        flags |= sprintIn ? FLAG_SPRINT : 0;
        return (byte) flags;
    }
}
