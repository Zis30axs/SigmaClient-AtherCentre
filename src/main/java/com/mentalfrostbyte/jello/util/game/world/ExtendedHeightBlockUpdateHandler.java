package com.mentalfrostbyte.jello.util.game.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.play.server.SAnimateBlockBreakPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SMultiBlockChangePacket;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sits between the Via decoder and the vanilla 1.16.4 packet decoder. Every
 * clientbound block change whose Y is outside 0..255 is cancelled by
 * ViaBackwards (1.17 -&gt; 1.16.4); {@link ChunkDataInterceptor} captured the raw
 * packet before the Via pipeline, and this handler re-injects an equivalent
 * 1.16.4 wire packet so the extended-height client world applies server-side
 * break/place updates at Y &lt; 0 and Y &gt; 255.
 *
 * <p>The buffer handed to {@code NettyPacketDecoder} is a bare
 * {@code [packet id VarInt][packet payload]} frame: the length prefix has
 * already been stripped by {@code splitter} and the payload has already been
 * decompressed by {@code decompress}, both of which sit upstream of this
 * handler.
 *
 * <p>Payloads are produced by the vanilla {@code writePacketData} methods
 * wherever the packet class has a usable constructor, so the layout can never
 * drift from the {@code readPacketData} the decoder runs. In particular a
 * 1.16.4 {@code BlockPos} is a single packed long, not three ints - writing
 * three ints left exactly four trailing bytes and tripped the decoder's
 * "larger than I expected" guard.
 *
 * <p>Modern -&gt; legacy payload differences that still have to be handled by
 * hand:
 * <ul>
 *   <li>BLOCK_UPDATE: identical layout (packed pos long + varint state), only
 *       the block-state id needs remapping.</li>
 *   <li>SECTION_BLOCKS_UPDATE: 1.18/1.19 carry a suppress-light boolean that
 *       1.20+ dropped; 1.16.4 always expects it. Record encoding
 *       ({@code state << 12 | packedPos}, var-long) is unchanged.</li>
 *   <li>BLOCK_DESTRUCTION: identical layout (varint entity id + packed pos long
 *       + byte stage).</li>
 * </ul>
 */
public final class ExtendedHeightBlockUpdateHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger("ExtendedHeightBlockUpdates");

    public static final String HANDLER_NAME = "extended-height-block-update";

    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugBlockUpdateReinject";

    /**
     * Per-kind kill switches so a single kind can be bisected in game without a
     * rebuild ({@code -Dsigma.viamcp.reinject.multi=false}). All default to on.
     */
    private static final String SINGLE_PROPERTY = "sigma.viamcp.reinject.single";
    private static final String MULTI_PROPERTY = "sigma.viamcp.reinject.multi";
    private static final String DESTRUCTION_PROPERTY = "sigma.viamcp.reinject.destruction";

    /**
     * 1.16.4 clientbound PLAY packet ids, resolved from the live
     * {@link ProtocolType#PLAY} registry instead of hardcoded ordinals.
     */
    private static volatile int[] packetIds;

    /** Guards against a re-injection triggering another drain of the same queue. */
    private boolean draining;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ExtendedBlockUpdateReinjectSelfTest.runOnce();
        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        drain(ctx);
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        drain(ctx);
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ExtendedBlockUpdateStore.clearAll();
        super.channelInactive(ctx);
    }

    private void drain(ChannelHandlerContext ctx) {
        if (!WorldHeightHelper.isExtendedHeight()) {
            ExtendedBlockUpdateStore.clearAll();
            return;
        }

        if (this.draining) {
            return;
        }

        this.draining = true;

        try {
            ExtendedBlockUpdateStore.CapturedUpdate update;
            while ((update = ExtendedBlockUpdateStore.poll()) != null) {
                ByteBuf synthetic = null;
                try {
                    synthetic = encode(update);
                    if (synthetic != null) {
                        logInjection(ctx, update, synthetic);
                        // Ownership moves to the vanilla decoder; do not release here.
                        ctx.fireChannelRead(synthetic);
                    }
                } catch (Throwable t) {
                    if (synthetic != null && synthetic.refCnt() > 0) {
                        synthetic.release();
                    }
                    if (isDebugEnabled() || ChunkDataInterceptor.isDebugEnabled()) {
                        LOGGER.warn("[ExtendedHeight] Could not re-inject captured block update kind={}: {}",
                                update.kind, t.toString());
                    } else {
                        LOGGER.debug("[ExtendedHeight] Could not re-inject captured block update: {}", t.getMessage());
                    }
                }
            }
        } finally {
            this.draining = false;
        }
    }

    private static ByteBuf encode(ExtendedBlockUpdateStore.CapturedUpdate update) throws Exception {
        switch (update.kind) {
            case SINGLE:
                if (!isEnabled(SINGLE_PROPERTY)) {
                    return null;
                }

                return encodeSingle(update.x, update.y, update.z,
                        ExtendedBlockStateMapper.mapToNativeId(update.stateId));
            case MULTI: {
                if (!isEnabled(MULTI_PROPERTY)) {
                    return null;
                }

                SectionBlocksPayload payload = parseSectionBlocksPayload(update.payload);
                long[] records = new long[payload.records.length];

                for (int i = 0; i < records.length; ++i) {
                    long record = payload.records[i];
                    long packedPos = record & 0xFFFL;
                    int mappedId = ExtendedBlockStateMapper.mapToNativeId((int) (record >>> 12));
                    records[i] = ((long) mappedId << 12) | packedPos;
                }

                return encodeMulti(update.sectionPos, payload.suppressLightUpdates, records);
            }
            case DESTRUCTION:
                if (!isEnabled(DESTRUCTION_PROPERTY)) {
                    return null;
                }

                return encodeDestruction(update.entityId, update.x, update.y, update.z, update.stage);
            default:
                return null;
        }
    }

    /**
     * {@code [id][BlockPos long][VarInt state]} - byte-for-byte what
     * {@link SChangeBlockPacket#readPacketData(PacketBuffer)} consumes.
     */
    static ByteBuf encodeSingle(int x, int y, int z, int nativeStateId) throws Exception {
        SChangeBlockPacket packet = new SChangeBlockPacket(new BlockPos(x, y, z), stateById(nativeStateId));
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.CHANGE_BLOCK));
        packet.writePacketData(out);
        return buf;
    }

    /**
     * {@code [id][VarInt breaker][BlockPos long][byte stage]} - byte-for-byte
     * what {@link SAnimateBlockBreakPacket#readPacketData(PacketBuffer)}
     * consumes.
     */
    static ByteBuf encodeDestruction(int entityId, int x, int y, int z, int stage) throws Exception {
        SAnimateBlockBreakPacket packet = new SAnimateBlockBreakPacket(entityId, new BlockPos(x, y, z), stage);
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.ANIMATE_BLOCK_BREAK));
        packet.writePacketData(out);
        return buf;
    }

    /**
     * {@code [id][section long][boolean][VarInt count][count * VarLong]} -
     * the exact inverse of
     * {@link SMultiBlockChangePacket#readPacketData(PacketBuffer)}. The packet
     * class has no constructor that takes raw records, so this one stage is
     * hand-encoded and covered by
     * {@link ExtendedBlockUpdateReinjectSelfTest}.
     */
    static ByteBuf encodeMulti(long sectionPos, boolean suppressLightUpdates, long[] records) {
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.MULTI_BLOCK_CHANGE));
        out.writeLong(sectionPos);
        out.writeBoolean(suppressLightUpdates);
        out.writeVarInt(records.length);

        for (long record : records) {
            out.writeVarLong(record);
        }

        return buf;
    }

    private static BlockState stateById(int nativeStateId) {
        BlockState state = Block.BLOCK_STATE_IDS.getByValue(nativeStateId);
        return state != null ? state : Blocks.STONE.getDefaultState();
    }

    /** Section-blocks payload from the suppress-light slot onward. */
    private static final class SectionBlocksPayload {
        private final boolean suppressLightUpdates;
        private final long[] records;

        private SectionBlocksPayload(boolean suppressLightUpdates, long[] records) {
            this.suppressLightUpdates = suppressLightUpdates;
            this.records = records;
        }
    }

    /**
     * 1.18/1.19 send {@code [boolean][VarInt count][records]}; 1.20+ dropped the
     * boolean and send {@code [VarInt count][records]}. The target version picks
     * the expected layout and the other one is tried as a fallback, so a wrong
     * version boundary degrades to a retry instead of a malformed packet. A
     * layout is only accepted when it consumes the payload exactly.
     */
    private static SectionBlocksPayload parseSectionBlocksPayload(byte[] payload) {
        boolean expectBoolean = WorldHeightHelper
                .hasSectionBlocksUpdateSuppressLight(WorldHeightHelper.getTargetVersionSafe());

        SectionBlocksPayload parsed = tryParseSectionBlocksPayload(payload, expectBoolean);
        if (parsed == null) {
            parsed = tryParseSectionBlocksPayload(payload, !expectBoolean);

            if (parsed != null && isDebugEnabled()) {
                LOGGER.info("[ExtendedHeightProbe] SECTION_BLOCKS_UPDATE layout fallback: suppressLightField={}",
                        !expectBoolean);
            }
        }

        if (parsed == null) {
            throw new IllegalStateException("Section blocks update payload (" + payload.length
                    + " bytes) matched neither the 1.18 nor the 1.20 layout");
        }

        return parsed;
    }

    private static SectionBlocksPayload tryParseSectionBlocksPayload(byte[] payload, boolean withBoolean) {
        ByteBuf buf = Unpooled.wrappedBuffer(payload);

        try {
            boolean suppressLightUpdates = false;

            if (withBoolean) {
                int raw = buf.readUnsignedByte();
                if (raw > 1) {
                    return null;
                }

                suppressLightUpdates = raw != 0;
            }

            int count = readVarInt(buf);
            if (count < 0 || count > buf.readableBytes()) {
                return null;
            }

            long[] records = new long[count];

            for (int i = 0; i < count; ++i) {
                records[i] = readVarLong(buf);
            }

            return buf.isReadable() ? null : new SectionBlocksPayload(suppressLightUpdates, records);
        } catch (RuntimeException e) {
            return null;
        }
    }

    enum PacketSlot {
        ANIMATE_BLOCK_BREAK,
        CHANGE_BLOCK,
        MULTI_BLOCK_CHANGE
    }

    static int packetId(PacketSlot slot) {
        int[] ids = packetIds;

        if (ids == null) {
            ids = new int[PacketSlot.values().length];
            ids[PacketSlot.ANIMATE_BLOCK_BREAK.ordinal()] = resolvePacketId(new SAnimateBlockBreakPacket());
            ids[PacketSlot.CHANGE_BLOCK.ordinal()] = resolvePacketId(new SChangeBlockPacket());
            ids[PacketSlot.MULTI_BLOCK_CHANGE.ordinal()] = resolvePacketId(new SMultiBlockChangePacket());
            packetIds = ids;
        }

        return ids[slot.ordinal()];
    }

    private static int resolvePacketId(IPacket<?> packet) {
        Integer id = ProtocolType.PLAY.getPacketId(PacketDirection.CLIENTBOUND, packet);

        if (id == null) {
            throw new IllegalStateException(
                    packet.getClass().getSimpleName() + " is not registered in ProtocolType.PLAY CLIENTBOUND");
        }

        return id;
    }

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    private static boolean isEnabled(String property) {
        return !"false".equalsIgnoreCase(System.getProperty(property));
    }

    private void logInjection(ChannelHandlerContext ctx, ExtendedBlockUpdateStore.CapturedUpdate update, ByteBuf buf) {
        if (!isDebugEnabled()) {
            return;
        }

        int wireId = buf.getUnsignedByte(buf.readerIndex());
        LOGGER.info(
                "[ExtendedHeightProbe] REINJECT kind={} wirePacketId={} pos=({},{},{}) packedPos={} sectionPos={} "
                        + "rawStateId={} nativeStateId={} entityId={} stage={} readerIndex={} writerIndex={} "
                        + "readableBytes={} hex={} before={} after={}",
                update.kind, wireId, update.x, update.y, update.z, BlockPos.pack(update.x, update.y, update.z),
                update.sectionPos, update.stateId,
                update.kind == ExtendedBlockUpdateStore.Kind.SINGLE
                        ? ExtendedBlockStateMapper.mapToNativeId(update.stateId)
                        : -1,
                update.entityId, update.stage, buf.readerIndex(), buf.writerIndex(), buf.readableBytes(),
                ByteBufUtil.hexDump(buf), neighbour(ctx, -1), neighbour(ctx, 1));
    }

    private static String neighbour(ChannelHandlerContext ctx, int offset) {
        List<String> names = ctx.pipeline().names();
        int index = names.indexOf(HANDLER_NAME) + offset;
        return index >= 0 && index < names.size() ? names.get(index) : "<none>";
    }

    private static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte currentByte;

        do {
            currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }

    private static long readVarLong(ByteBuf buf) {
        long value = 0L;
        int position = 0;
        byte currentByte;

        do {
            currentByte = buf.readByte();
            value |= (long) (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 64) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }
}
