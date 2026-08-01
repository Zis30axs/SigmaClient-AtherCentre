package com.mentalfrostbyte.jello.util.game.network;

import de.florianmichael.vialoadingbase.ViaLoadingBase;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs Via-translated serverbound packet ids before compression/framing.
 */
public class ServerboundPacketDebugHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugServerbound";
    public static final String HANDLER_NAME = "serverbound-packet-debug";

    // 1.21.6+ serverbound play ids (unchanged through 1.21.7/1.21.9/1.21.11).
    private static final int MOVE_PLAYER_POS = 0x1D;
    private static final int MOVE_PLAYER_POS_ROT = 0x1E;
    private static final int MOVE_PLAYER_ROT = 0x1F;
    private static final int MOVE_PLAYER_STATUS_ONLY = 0x20;
    private static final int USE_ITEM = 0x40;

    private final UseItemRotationDebug.PostViaMovementState lastMovement =
            new UseItemRotationDebug.PostViaMovementState();

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    private static boolean isRotationDebugEnabled() {
        return UseItemRotationDebug.isEnabled();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if ((isDebugEnabled() || isRotationDebugEnabled()) && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                int packetId = readVarInt(buf, buf.readerIndex());
                if (isDebugEnabled()) {
                    LOGGER.info("[ViaServerboundProbe] POST_VIA packetId={} length={} target={}",
                            packetId, buf.readableBytes(), ViaLoadingBase.getInstance().getTargetVersion());
                }
                if (isRotationDebugEnabled()) {
                    inspectModernPacket(buf, packetId);
                }
            } catch (Exception e) {
                LOGGER.warn("[ViaServerboundProbe] Could not inspect post-Via serverbound packet length={} target={}: {}",
                        buf.readableBytes(), ViaLoadingBase.getInstance().getTargetVersion(), e.getMessage());
            }
        }

        super.write(ctx, msg, promise);
    }

    private void inspectModernPacket(ByteBuf buf, int packetId) {
        ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
        if (target == null || target.olderThan(ProtocolVersion.v1_21_6)) {
            return;
        }

        int start = buf.readerIndex() + varIntSize(buf, buf.readerIndex());
        switch (packetId) {
            case USE_ITEM: {
                int hand = readVarInt(buf, start);
                int sequence = readVarInt(buf, start + varIntSize(buf, start));
                int yawOffset = start + varIntSize(buf, start) + varIntSize(buf, start + varIntSize(buf, start));
                float yaw = buf.getFloat(yawOffset);
                float pitch = buf.getFloat(yawOffset + 4);
                UseItemRotationDebug.logPostViaUseItem(hand, sequence, yaw, pitch);
                UseItemRotationDebug.logUseItemConsistency(
                        sequence, yaw, pitch,
                        this.lastMovement.hasRotation(),
                        this.lastMovement.yaw(), this.lastMovement.pitch(),
                        this.lastMovement.hasRotation() ? "last-movement" : "POSITION_ONLY_CURRENT_ROTATION");
                break;
            }
            case MOVE_PLAYER_POS_ROT: {
                int yawOffset = start + 24;
                float yaw = buf.getFloat(yawOffset);
                float pitch = buf.getFloat(yawOffset + 4);
                this.lastMovement.set(yaw, pitch);
                UseItemRotationDebug.logPostViaMovement("pos-rot", yaw, pitch, true);
                break;
            }
            case MOVE_PLAYER_ROT: {
                float yaw = buf.getFloat(start);
                float pitch = buf.getFloat(start + 4);
                this.lastMovement.set(yaw, pitch);
                UseItemRotationDebug.logPostViaMovement("rot", yaw, pitch, true);
                break;
            }
            case MOVE_PLAYER_POS:
                UseItemRotationDebug.logPostViaMovement("pos", 0.0F, 0.0F, false);
                break;
            case MOVE_PLAYER_STATUS_ONLY:
                UseItemRotationDebug.logPostViaMovement("status", 0.0F, 0.0F, false);
                break;
            default:
                break;
        }
    }

    private static int readVarInt(ByteBuf buf, int index) {
        int value = 0;
        int position = 0;
        int offset = 0;

        while (true) {
            if (index + offset >= buf.writerIndex()) {
                throw new IllegalArgumentException("VarInt exceeds readable bytes");
            }

            byte currentByte = buf.getByte(index + offset);
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) {
                return value;
            }

            position += 7;
            ++offset;

            if (position >= 32) {
                throw new IllegalArgumentException("VarInt too big");
            }
        }
    }

    private static int varIntSize(ByteBuf buf, int index) {
        int offset = 0;
        while (index + offset < buf.writerIndex()) {
            byte currentByte = buf.getByte(index + offset);
            ++offset;
            if ((currentByte & 0x80) == 0) {
                return offset;
            }
            if (offset >= 5) {
                break;
            }
        }
        return 0;
    }
}
