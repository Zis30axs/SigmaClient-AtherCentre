package com.mentalfrostbyte.jello.util.game.world;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Captures raw 1.18+ serverbound block-change packets that ViaBackwards
 * 1.17 -> 1.16.4 cancels because their Y is outside the legacy 0..255 range.
 * {@link ChunkDataInterceptor} stores them before the Via pipeline, and
 * {@link ExtendedHeightBlockUpdateHandler} re-injects equivalent 1.16.4
 * packets after the Via decoder so the extended-height client world applies
 * server-side break/place updates below Y=0 and above Y=255.
 *
 * <p>The queue only stores primitives and raw payload bytes; no packet,
 * entity, world or channel references are kept.
 */
public final class ExtendedBlockUpdateStore {
    private static final int MAX_STORED = 512;
    private static final ConcurrentLinkedQueue<CapturedUpdate> QUEUE = new ConcurrentLinkedQueue<>();

    private ExtendedBlockUpdateStore() {
    }

    public enum Kind {
        SINGLE,
        MULTI,
        DESTRUCTION
    }

    public static final class CapturedUpdate {
        public final Kind kind;
        public final int x;
        public final int y;
        public final int z;
        /** Raw 1.18+ block state id (SINGLE). */
        public final int stateId;
        /** Packed section position long (MULTI). */
        public final long sectionPos;
        /** Raw payload from the suppress-light boolean onward (MULTI). */
        public final byte[] payload;
        /** Breaking entity id (DESTRUCTION). */
        public final int entityId;
        /** Block break stage (DESTRUCTION). */
        public final int stage;

        private CapturedUpdate(Kind kind, int x, int y, int z, int stateId, long sectionPos, byte[] payload,
                               int entityId, int stage) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stateId = stateId;
            this.sectionPos = sectionPos;
            this.payload = payload;
            this.entityId = entityId;
            this.stage = stage;
        }
    }

    public static void putSingle(int x, int y, int z, int stateId) {
        put(new CapturedUpdate(Kind.SINGLE, x, y, z, stateId, 0L, null, 0, 0));
    }

    public static void putMulti(long sectionPos, byte[] payload) {
        put(new CapturedUpdate(Kind.MULTI, 0, 0, 0, 0, sectionPos, payload, 0, 0));
    }

    public static void putDestruction(int entityId, int x, int y, int z, int stage) {
        put(new CapturedUpdate(Kind.DESTRUCTION, x, y, z, 0, 0L, null, entityId, stage));
    }

    public static CapturedUpdate poll() {
        return QUEUE.poll();
    }

    public static void clearAll() {
        QUEUE.clear();
    }

    private static void put(CapturedUpdate update) {
        while (QUEUE.size() >= MAX_STORED) {
            QUEUE.poll();
        }
        QUEUE.add(update);
    }
}
