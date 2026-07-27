package com.elfmcys.yesstevemodel.network.message;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import net.minecraft.potion.Effect;

public class S2CSyncPlayerStatePacket {
    public int flags;
    public boolean isFlying;
    public Object2ByteOpenHashMap<Effect> effectAmplifiers = new Object2ByteOpenHashMap<>();
    public int experienceLevel;
    public int foodLevel;
    public float health;
    public float maxHealth;
    public byte strafeInput;
    public byte verticalInput;
    public byte forwardInput;
    public int shieldBlockCooldown;

    public boolean isFullSync() { return (flags & 1) != 0; }
}