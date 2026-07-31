package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;

/**
 * Per-{@link UserConnection} storage for the modern (1.19+) block-interaction
 * sequence counter.
 *
 * <p>One counter is shared by USE_ITEM, USE_ITEM_ON and the digging
 * START/STOP actions, exactly like the vanilla 1.19+ client. Non-digging
 * PLAYER_ACTION packets must carry sequence 0 and must never consume the
 * counter. A fresh {@link UserConnection} starts at 0, so the first
 * incrementing packet after connect / world switch is sequence 1.
 *
 * <p>All mutation happens on the connection's Netty event loop, except the
 * world-switch reset which is issued from the Minecraft main thread
 * ({@code Minecraft.loadWorld}). The methods are therefore synchronized; the
 * frequency is a few calls per second per connection, so the lock is
 * uncontended and effectively free.
 */
public final class InteractionSequenceStorage implements StorableObject {
    private int sequence;

    public synchronized int next() {
        if (sequence == Integer.MAX_VALUE) {
            sequence = 0;
        }
        return ++sequence;
    }

    public synchronized int current() {
        return sequence;
    }

    public synchronized void set(int value) {
        sequence = Math.max(0, value);
    }

    public synchronized void reset() {
        sequence = 0;
    }

    public static InteractionSequenceStorage of(UserConnection connection) {
        InteractionSequenceStorage storage = connection.get(InteractionSequenceStorage.class);
        if (storage != null) {
            return storage;
        }

        synchronized (connection) {
            storage = connection.get(InteractionSequenceStorage.class);
            if (storage == null) {
                storage = new InteractionSequenceStorage();
                connection.put(storage);
            }
            return storage;
        }
    }
}
