package com.elfmcys.yesstevemodel.network.message;

/**
 * Inert stub for upstream's C2S animation packet. Server sync is cut from this client-only port
 * ({@code NetworkHandler.isClientConnected()} is hardcoded {@code false}), so every send site is
 * unreachable — the shapes exist only so the ported call sites stay byte-for-byte comparable with
 * upstream instead of being restructured around the cut.
 */
public class C2SPlayAnimationPacket {

    public final int index;

    public final String submenu;

    public final int entityId;

    public C2SPlayAnimationPacket() {
        this(-1, "", -1);
    }

    public C2SPlayAnimationPacket(int index, String submenu) {
        this(index, submenu, -1);
    }

    public C2SPlayAnimationPacket(int index, String submenu, int entityId) {
        this.index = index;
        this.submenu = submenu;
        this.entityId = entityId;
    }

    public static C2SPlayAnimationPacket createDefault() {
        return new C2SPlayAnimationPacket();
    }

    /** Upstream's "stop the animation on this entity" form. */
    public static C2SPlayAnimationPacket createWithIndex(int entityId) {
        return new C2SPlayAnimationPacket(-1, "", entityId);
    }
}
