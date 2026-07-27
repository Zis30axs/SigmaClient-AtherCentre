package com.elfmcys.yesstevemodel.network.message;

/**
 * Inert stub for upstream's "mirror this molang assignment to the server" packet. Server sync is cut
 * from this client-only port; the roulette's checkbox/radio/slider handlers still build one behind
 * the {@code NetworkHandler.isClientConnected()} guard so their control flow stays comparable with
 * upstream.
 */
public class C2SRequestExecuteMolangPacket {

    public final String expression;

    public final int entityId;

    public C2SRequestExecuteMolangPacket(String expression, int entityId) {
        this.expression = expression;
        this.entityId = entityId;
    }
}
