package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.network.message.S2CSyncPlayerStatePacket;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerEntityFrameState extends LivingEntityFrameState<PlayerEntity> {

    private final boolean isLocalPlayer;

    private final Object2ByteOpenHashMap<Effect> effectAmplifiers;

    private boolean isFlying;

    private int experienceLevel;

    private float health;

    private float maxHealth;

    private int foodLevel;

    private float strafeInput;

    private float verticalInput;

    private float forwardInput;

    private boolean isShieldBlocking;

    private static float headYawDelta;

    private static float lastYRot;

    public PlayerEntityFrameState(PlayerEntity player, boolean isLocalPlayer) {
        super(player);
        this.isLocalPlayer = isLocalPlayer;
        this.effectAmplifiers = new Object2ByteOpenHashMap<>(8);
    }

    @Override
    public void reset() {
        super.reset();
        this.effectAmplifiers.clear();
        this.isFlying = false;
        this.experienceLevel = 0;
        this.health = 0;
        this.maxHealth = 0;
        this.foodLevel = 0;
        this.strafeInput = 0.0f;
        this.verticalInput = 0.0f;
        this.forwardInput = 0.0f;
        this.isShieldBlocking = false;
    }

    public void applySyncMessage(S2CSyncPlayerStatePacket message) {
        if ((message.flags & 2) != 0) {
            this.isFlying = message.isFlying;
        }
        if ((message.flags & 4) != 0) {
            if (message.isFullSync()) {
                this.effectAmplifiers.clear();
            }
            this.effectAmplifiers.putAll(message.effectAmplifiers);
        }
        if ((message.flags & 8) != 0) {
            this.experienceLevel = message.experienceLevel;
        }
        if ((message.flags & 16) != 0) {
            this.foodLevel = message.foodLevel;
        }
        if ((message.flags & 32) != 0) {
            this.health = message.health;
        }
        if ((message.flags & 64) != 0) {
            this.maxHealth = message.maxHealth;
        }
        if ((message.flags & 128) != 0) {
            this.strafeInput = message.strafeInput / 127.0f;
        }
        if ((message.flags & 256) != 0) {
            this.verticalInput = message.verticalInput / 127.0f;
        }
        if ((message.flags & 512) != 0) {
            this.forwardInput = message.forwardInput / 127.0f;
        }
        if ((message.flags & 1024) != 0) {
            this.isShieldBlocking = message.shieldBlockCooldown > 0;
        }
    }

    public boolean isFlying() {
        if (this.isLocalPlayer) {
            return this.entity.abilities.isFlying;
        }
        return this.isFlying;
    }

    public int getExperienceLevel() {
        return this.experienceLevel;
    }

    @Override
    public float getHealth() {
        return this.health;
    }

    @Override
    public float getMaxHealth() {
        return this.maxHealth;
    }

    public int getFoodLevel() {
        return this.foodLevel;
    }

    public float getStrafeInput() {
        return this.strafeInput;
    }

    public float getVerticalInput() {
        return this.verticalInput;
    }

    public float getForwardInput() {
        return this.forwardInput;
    }

    public boolean isShieldBlocking() {
        return this.isShieldBlocking;
    }

    public byte getEffectAmplifier(Effect effect) {
        if (this.isLocalPlayer) {
            EffectInstance effectInstance = this.entity.getActivePotionEffect(effect);
            if (effectInstance != null) {
                return (byte) (effectInstance.getAmplifier() + 1);
            }
            return (byte) 0;
        }
        return this.effectAmplifiers.getOrDefault(effect, (byte) 0);
    }

    @Override
    public void onTickUpdate(int i, int i2) {
        if (this.isLocalPlayer) {
            updateHeadYaw(this.entity, i, i2);
        }
        super.onTickUpdate(i, i2);
    }

    private static void updateHeadYaw(PlayerEntity player, int i, int i2) {
        float yRot = player.rotationYaw;
        if (i2 > 0) {
            headYawDelta = ((yRot - lastYRot) * 20.0f) / (i - i2);
        }
        lastYRot = yRot;
    }

    public static float getHeadYawDelta() {
        return headYawDelta;
    }
}