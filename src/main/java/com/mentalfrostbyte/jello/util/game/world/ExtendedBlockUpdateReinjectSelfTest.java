package com.mentalfrostbyte.jello.util.game.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.minecraft.util.math.SectionPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Byte-level round trip for the packets {@link ExtendedHeightBlockUpdateHandler}
 * re-injects. Every case is encoded exactly as the handler encodes it, then
 * decoded through the same three steps {@code NettyPacketDecoder} performs -
 * read the packet id varint, look the packet up in {@link ProtocolType#PLAY},
 * run {@code readPacketData} - and the buffer must be fully consumed
 * afterwards.
 *
 * <p>This is what catches a "found N bytes extra" regression without launching
 * the game: writing a {@code BlockPos} as three ints instead of one packed long
 * fails the {@code readableBytes() == 0} assertion here.
 *
 * <p>Off by default. Enable with
 * {@code -Dsigma.viamcp.testBlockUpdateReinject=true}; it then runs once per
 * JVM when the handler is installed into a channel pipeline.
 */
public final class ExtendedBlockUpdateReinjectSelfTest {
    private static final Logger LOGGER = LogManager.getLogger("ExtendedHeightBlockUpdates");
    private static final String PROPERTY = "sigma.viamcp.testBlockUpdateReinject";
    private static final AtomicBoolean RAN = new AtomicBoolean();

    /** Legacy edge (-1), 1.18 floor (-64), legacy ceiling (256) and 1.18 roof (319). */
    private static final int[] TEST_Y = { -1, -64, 0, 255, 256, 319 };

    private ExtendedBlockUpdateReinjectSelfTest() {
    }

    /** @return false only when a round trip actually failed. */
    public static boolean runOnce() {
        if (!Boolean.getBoolean(PROPERTY) || RAN.getAndSet(true)) {
            return true;
        }

        List<String> failures = new ArrayList<>();
        int cases = 0;

        try {
            int smallStateId = findStateId(1, 127);
            int largeStateId = findStateId(128, 1 << 16);

            if (smallStateId < 0 || largeStateId < 0) {
                LOGGER.warn("[ExtendedHeightSelfTest] Skipped: block state registry not populated yet");
                return true;
            }

            for (int y : TEST_Y) {
                for (int stateId : new int[] { smallStateId, largeStateId }) {
                    ++cases;
                    checkSingle(failures, 1234567, y, -7654321, stateId);
                }

                ++cases;
                checkDestruction(failures, 42, 1234567, y, -7654321, 7);
                ++cases;
                checkDestruction(failures, 300, -30000000, y, 29999999, 0);
            }

            for (int sectionY : new int[] { -4, -1, 0, 15, 16, 19 }) {
                for (boolean suppressLight : new boolean[] { false, true }) {
                    ++cases;
                    checkMulti(failures, sectionY, suppressLight, smallStateId, largeStateId);
                }
            }
        } catch (Throwable t) {
            failures.add("self test aborted: " + t);
        }

        if (failures.isEmpty()) {
            LOGGER.info("[ExtendedHeightSelfTest] {} re-injection round trips passed", cases);
            return true;
        }

        LOGGER.error("[ExtendedHeightSelfTest] {}/{} re-injection round trips FAILED", failures.size(), cases);

        for (String failure : failures) {
            LOGGER.error("[ExtendedHeightSelfTest]   {}", failure);
        }

        return false;
    }

    private static void checkSingle(List<String> failures, int x, int y, int z, int stateId) {
        ByteBuf buf = null;

        try {
            buf = ExtendedHeightBlockUpdateHandler.encodeSingle(x, y, z, stateId);
            String hex = ByteBufUtil.hexDump(buf);
            /* id varint (1) + packed pos long (8) + state varint (1 or 2..) */
            IPacket<?> packet = decode(buf, ExtendedHeightBlockUpdateHandler.PacketSlot.CHANGE_BLOCK,
                    SChangeBlockPacket.class);
            SChangeBlockPacket decoded = (SChangeBlockPacket) packet;

            expect(failures, "SINGLE y=" + y + " state=" + stateId + " pos hex=" + hex,
                    new BlockPos(x, y, z), decoded.getPos());
            expect(failures, "SINGLE y=" + y + " state=" + stateId + " state hex=" + hex,
                    stateId, Block.getStateId(decoded.getState()));
            expectDrained(failures, "SINGLE y=" + y + " state=" + stateId + " hex=" + hex, buf);
        } catch (Throwable t) {
            failures.add("SINGLE y=" + y + " state=" + stateId + ": " + t);
        } finally {
            release(buf);
        }
    }

    private static void checkDestruction(List<String> failures, int entityId, int x, int y, int z, int stage) {
        ByteBuf buf = null;

        try {
            buf = ExtendedHeightBlockUpdateHandler.encodeDestruction(entityId, x, y, z, stage);
            String hex = ByteBufUtil.hexDump(buf);
            SAnimateBlockBreakPacket decoded = (SAnimateBlockBreakPacket) decode(buf,
                    ExtendedHeightBlockUpdateHandler.PacketSlot.ANIMATE_BLOCK_BREAK, SAnimateBlockBreakPacket.class);

            expect(failures, "DESTRUCTION y=" + y + " breaker hex=" + hex, entityId, decoded.getBreakerId());
            expect(failures, "DESTRUCTION y=" + y + " pos hex=" + hex, new BlockPos(x, y, z), decoded.getPosition());
            expect(failures, "DESTRUCTION y=" + y + " stage hex=" + hex, stage, decoded.getProgress());
            expectDrained(failures, "DESTRUCTION y=" + y + " hex=" + hex, buf);
        } catch (Throwable t) {
            failures.add("DESTRUCTION y=" + y + ": " + t);
        } finally {
            release(buf);
        }
    }

    private static void checkMulti(List<String> failures, int sectionY, boolean suppressLight, int smallStateId,
            int largeStateId) {
        ByteBuf buf = null;
        String label = "MULTI sectionY=" + sectionY + " suppressLight=" + suppressLight;

        try {
            long sectionPos = SectionPos.asLong(4, sectionY, -9);
            /* short pos = x << 8 | z << 4 | y, all nibbles */
            short[] positions = { 0, (short) 4095, (short) 0x0F0 };
            int[] stateIds = { smallStateId, largeStateId, smallStateId };
            long[] records = new long[positions.length];

            for (int i = 0; i < records.length; ++i) {
                records[i] = ((long) stateIds[i] << 12) | (positions[i] & 0xFFFL);
            }

            buf = ExtendedHeightBlockUpdateHandler.encodeMulti(sectionPos, suppressLight, records);
            String hex = ByteBufUtil.hexDump(buf);
            SMultiBlockChangePacket decoded = (SMultiBlockChangePacket) decode(buf,
                    ExtendedHeightBlockUpdateHandler.PacketSlot.MULTI_BLOCK_CHANGE, SMultiBlockChangePacket.class);

            expect(failures, label + " sectionPos hex=" + hex, sectionPos, decoded.getSectionPos().asLong());
            expect(failures, label + " suppressLight hex=" + hex, suppressLight, decoded.func_244311_b());

            List<BlockPos> seenPositions = new ArrayList<>();
            List<Integer> seenStates = new ArrayList<>();
            decoded.func_244310_a((pos, state) -> {
                seenPositions.add(pos.toImmutable());
                seenStates.add(Block.getStateId(state));
            });

            expect(failures, label + " record count hex=" + hex, records.length, seenPositions.size());

            for (int i = 0; i < records.length && i < seenPositions.size(); ++i) {
                BlockPos expected = new BlockPos(
                        SectionPos.toWorld(4) + SectionPos.func_243641_a(positions[i]),
                        SectionPos.toWorld(sectionY) + SectionPos.func_243642_b(positions[i]),
                        SectionPos.toWorld(-9) + SectionPos.func_243643_c(positions[i]));
                expect(failures, label + " record " + i + " pos hex=" + hex, expected, seenPositions.get(i));
                expect(failures, label + " record " + i + " state hex=" + hex, stateIds[i], seenStates.get(i).intValue());
            }

            expectDrained(failures, label + " hex=" + hex, buf);
        } catch (Throwable t) {
            failures.add(label + ": " + t);
        } finally {
            release(buf);
        }
    }

    /** Mirrors {@code NettyPacketDecoder.decode} minus the trailing-byte throw. */
    private static IPacket<?> decode(ByteBuf buf, ExtendedHeightBlockUpdateHandler.PacketSlot slot,
            Class<? extends IPacket<?>> expectedType) throws Exception {
        PacketBuffer in = new PacketBuffer(buf);
        int wireId = in.readVarInt();
        int expectedId = ExtendedHeightBlockUpdateHandler.packetId(slot);

        if (wireId != expectedId) {
            throw new IllegalStateException("wire packet id " + wireId + " != registry id " + expectedId);
        }

        IPacket<?> packet = ProtocolType.PLAY.getPacket(PacketDirection.CLIENTBOUND, wireId);

        if (packet == null) {
            throw new IllegalStateException("Bad packet id " + wireId);
        }

        if (!expectedType.isInstance(packet)) {
            throw new IllegalStateException(
                    "packet id " + wireId + " resolved to " + packet.getClass().getSimpleName()
                            + " instead of " + expectedType.getSimpleName());
        }

        packet.readPacketData(in);
        return packet;
    }

    private static void expectDrained(List<String> failures, String label, ByteBuf buf) {
        if (buf.readableBytes() != 0) {
            failures.add(label + ": " + buf.readableBytes() + " trailing bytes after readPacketData");
        }
    }

    private static void expect(List<String> failures, String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            failures.add(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expect(List<String> failures, String label, int expected, int actual) {
        if (expected != actual) {
            failures.add(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expect(List<String> failures, String label, long expected, long actual) {
        if (expected != actual) {
            failures.add(label + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expect(List<String> failures, String label, boolean expected, boolean actual) {
        if (expected != actual) {
            failures.add(label + ": expected " + expected + " but got " + actual);
        }
    }

    /** First registered state id in the range, so the varint width is the point being tested. */
    private static int findStateId(int from, int to) {
        for (int id = from; id <= to; ++id) {
            BlockState state = Block.BLOCK_STATE_IDS.getByValue(id);

            if (state != null && state != Blocks.AIR.getDefaultState() && Block.getStateId(state) == id) {
                return id;
            }
        }

        return -1;
    }

    private static void release(ByteBuf buf) {
        if (buf != null && buf.refCnt() > 0) {
            buf.release();
        }
    }
}
