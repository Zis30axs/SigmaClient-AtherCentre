package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.ProtocolPipeline;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ClientboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Assigns the modern block-interaction sequence to every serverbound
 * USE_ITEM / USE_ITEM_ON / PLAYER_ACTION packet exactly once, after the
 * ViaBackwards 1.18.2 -> 1.19 layer has created the sequence field (it always
 * writes 0, which makes Grim flag {@code BadPacketsH expected=1, id=0}).
 *
 * <p>The protocol is inserted into the connection's protocol pipeline
 * immediately after {@link Protocol1_19To1_18_2}, i.e. it sees the fixed
 * 1.19 wire format for every target >= 1.19. All later rungs (1.19.1 ... 1.21.11)
 * preserve the value.
 *
 * <p>It is also the single place that decides the per-action rules:
 * <ul>
 *   <li>START_DESTROY_BLOCK (0) and STOP_DESTROY_BLOCK (2): next(), keep pos/face</li>
 *   <li>ABORT_DESTROY_BLOCK (1): 0, keep pos/face (Grim requires CANCELLED sequence 0)</li>
 *   <li>RELEASE_USE_ITEM / DROP_ITEM / DROP_ALL_ITEMS / SWAP_ITEM_WITH_OFFHAND:
 *       0 and pos=0,0,0 face=DOWN (BadPacketsL)</li>
 * </ul>
 */
public final class ServerboundInteractionSequenceProtocol
        extends AbstractProtocol<ClientboundPackets1_19, ClientboundPackets1_19,
                ServerboundPackets1_19, ServerboundPackets1_19> {

    /** PlayerAction ids, identical from 1.16 through 1.21.11 (PacketEvents DiggingAction). */
    private static final int ACTION_START_DESTROY_BLOCK = 0;
    private static final int ACTION_ABORT_DESTROY_BLOCK = 1;
    private static final int ACTION_STOP_DESTROY_BLOCK = 2;
    private static final int ACTION_DROP_ALL_ITEMS = 3;
    private static final int ACTION_DROP_ITEM = 4;
    private static final int ACTION_RELEASE_USE_ITEM = 5;
    private static final int ACTION_SWAP_ITEM_WITH_OFFHAND = 6;

    private static final Logger LOGGER = LogManager.getLogger("ViaSequence");
    private static final String DEBUG_PROPERTY = "sigma.via.sequenceDebug";
    private static final long DEBUG_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final BlockPosition ZERO = new BlockPosition(0, 0, 0);

    private static volatile boolean debugChecked;
    private static volatile boolean debugEnabled;
    private static volatile long lastDebugNanos;

    public ServerboundInteractionSequenceProtocol() {
        super(ClientboundPackets1_19.class, ClientboundPackets1_19.class,
                ServerboundPackets1_19.class, ServerboundPackets1_19.class);
    }

    @Override
    public void init(UserConnection connection) {
        if (!connection.has(InteractionSequenceStorage.class)) {
            connection.put(new InteractionSequenceStorage());
        }
    }

    @Override
    protected void registerPackets() {
        registerServerbound(ServerboundPackets1_19.USE_ITEM, this::handleUseItem);
        registerServerbound(ServerboundPackets1_19.USE_ITEM_ON, this::handleUseItemOn);
        registerServerbound(ServerboundPackets1_19.PLAYER_ACTION, this::handlePlayerAction);
    }

    private void handleUseItem(PacketWrapper wrapper) throws InformativeException {
        InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
        int before = storage.current();
        int hand = wrapper.read(Types.VAR_INT);
        wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)
        int sequence = storage.next();
        wrapper.write(Types.VAR_INT, hand);
        wrapper.write(Types.VAR_INT, sequence);
        debug(wrapper, "USE_ITEM", -1, null, (short) -1, sequence, before, "translated");
    }

    private void handleUseItemOn(PacketWrapper wrapper) throws InformativeException {
        InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
        int before = storage.current();
        int hand = wrapper.read(Types.VAR_INT);
        BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
        int face = wrapper.read(Types.VAR_INT);
        float cursorX = wrapper.read(Types.FLOAT);
        float cursorY = wrapper.read(Types.FLOAT);
        float cursorZ = wrapper.read(Types.FLOAT);
        boolean inside = wrapper.read(Types.BOOLEAN);
        wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)
        int sequence = storage.next();
        wrapper.write(Types.VAR_INT, hand);
        wrapper.write(Types.BLOCK_POSITION1_14, pos);
        wrapper.write(Types.VAR_INT, face);
        wrapper.write(Types.FLOAT, cursorX);
        wrapper.write(Types.FLOAT, cursorY);
        wrapper.write(Types.FLOAT, cursorZ);
        wrapper.write(Types.BOOLEAN, inside);
        wrapper.write(Types.VAR_INT, sequence);
        debug(wrapper, "USE_ITEM_ON", -1, pos, (short) face, sequence, before, "translated");
    }

    private void handlePlayerAction(PacketWrapper wrapper) throws InformativeException {
        InteractionSequenceStorage storage = InteractionSequenceStorage.of(wrapper.user());
        int before = storage.current();
        int action = wrapper.read(Types.VAR_INT);
        BlockPosition pos = wrapper.read(Types.BLOCK_POSITION1_14);
        short face = wrapper.read(Types.UNSIGNED_BYTE);
        wrapper.read(Types.VAR_INT); // ViaBackwards placeholder (always 0)

        int sequence;
        BlockPosition outPos;
        short outFace;

        switch (action) {
            case ACTION_START_DESTROY_BLOCK, ACTION_STOP_DESTROY_BLOCK -> {
                sequence = storage.next();
                outPos = pos;
                outFace = face;
            }
            case ACTION_ABORT_DESTROY_BLOCK -> {
                // CANCELLED_DIGGING keeps the real position/face but MUST be sequence 0.
                sequence = 0;
                outPos = pos;
                outFace = face;
            }
            default -> {
                // RELEASE_USE_ITEM / DROP_ITEM / DROP_ALL_ITEMS / SWAP_ITEM_WITH_OFFHAND
                // (and any future non-digging action): BadPacketsL requires
                // pos=0,0,0, face=DOWN, sequence=0. Never touches the counter.
                sequence = 0;
                outPos = ZERO;
                outFace = 0;
            }
        }

        wrapper.write(Types.VAR_INT, action);
        wrapper.write(Types.BLOCK_POSITION1_14, outPos);
        wrapper.write(Types.UNSIGNED_BYTE, outFace);
        wrapper.write(Types.VAR_INT, sequence);
        debug(wrapper, "PLAYER_ACTION", action, outPos, outFace, sequence, before, "translated");
    }

    private static void debug(PacketWrapper wrapper, String packet, int action,
                              BlockPosition pos, short face, int sequence, int before, String origin) {
        if (!isDebugEnabled() || !rateLimited()) {
            return;
        }
        LOGGER.info("[ViaSequence] connection={} packet={} action={} pos={} face={} "
                        + "sequence={} counterBefore={} counterAfter={} stage={} thread={} origin={}",
                wrapper.user().getId(), packet,
                action < 0 ? "-" : action,
                pos == null ? "-" : pos.x() + "," + pos.y() + "," + pos.z(),
                face < 0 ? "-" : face,
                sequence, before, sequence,
                ServerboundInteractionSequenceProtocol.class.getSimpleName(),
                Thread.currentThread().getName(), origin);
    }

    private static boolean isDebugEnabled() {
        if (!debugChecked) {
            debugEnabled = Boolean.parseBoolean(System.getProperty(DEBUG_PROPERTY, "false"));
            debugChecked = true;
        }
        return debugEnabled;
    }

    private static synchronized boolean rateLimited() {
        long now = System.nanoTime();
        if (now - lastDebugNanos < DEBUG_INTERVAL_NANOS) {
            return false;
        }
        lastDebugNanos = now;
        return true;
    }

    /**
     * Installs this protocol into the connection pipeline right after the
     * ViaBackwards 1.19 rung. Must be called on the connection's event loop.
     */
    public static void ensureInstalled(UserConnection connection) {
        if (connection == null || connection.getProtocolInfo() == null) {
            return;
        }
        ProtocolPipeline pipeline = connection.getProtocolInfo().getPipeline();
        if (pipeline == null) {
            return;
        }
        if (pipeline.contains(ServerboundInteractionSequenceProtocol.class)) {
            return;
        }
        // Targets below 1.19 have no sequence field; nothing to fix.
        if (!pipeline.contains(Protocol1_19To1_18_2.class)) {
            return;
        }

        try {
            ServerboundInteractionSequenceProtocol protocol = new ServerboundInteractionSequenceProtocol();
            protocol.setClientVersion(ProtocolVersion.v1_19);
            protocol.setServerVersion(ProtocolVersion.v1_19);
            protocol.initialize();
            pipeline.add(protocol);
            moveAfter19Rung(pipeline, protocol);
        } catch (Exception e) {
            LOGGER.warn("Failed to install interaction sequence fix", e);
        }
    }

    /**
     * {@link ProtocolPipeline#add} always appends non-base protocols at the end
     * of the serverbound list; the sequence handler needs to run right after
     * the 1.19 rung so it sees the fixed 1.19 wire format. The list is only
     * touched once, on the event loop, before any further packet transform.
     */
    private static void moveAfter19Rung(ProtocolPipeline pipeline, Protocol protocol) {
        if (!(pipeline instanceof ProtocolPipelineImpl impl)) {
            return;
        }
        try {
            Field field = ProtocolPipelineImpl.class.getDeclaredField("protocolList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Protocol> list = (List<Protocol>) field.get(impl);
            int anchor = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getClass() == Protocol1_19To1_18_2.class) {
                    anchor = i;
                    break;
                }
            }
            if (anchor < 0) {
                return;
            }
            list.remove(protocol);
            list.add(anchor + 1, protocol);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to reposition interaction sequence protocol", e);
        }
    }
}
