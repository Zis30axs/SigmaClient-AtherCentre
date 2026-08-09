package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Random;

public class HeypixelDisabler extends Module {
    private final BooleanSetting ACAAimStep = new BooleanSetting("ACA-AimStep","Dis ACA AimStep",true);
    private final BooleanSetting ACAPerfectRotation = new BooleanSetting("ACA-PerfectRotation","Dis ACA PerfectRotation",true);
    private final BooleanSetting GrimBedPacketA = new BooleanSetting("DisBadPacketA","Dis GrimAC BadPacketF",true);
    private final BooleanSetting GrimBedPacketF = new BooleanSetting("DisBadPacketF","Dis GrimAC BadPacketF",true);
    private final BooleanSetting GrimAimModulo360 = new BooleanSetting("DisAimModulo360","Dis GrimAC AimModulo360",true);
    private final BooleanSetting GrimDuplicateRotPlace = new BooleanSetting("DisDuplicateRotPlace","Dis GrimAC DuplicateRotPlace",true);
    private int lastSlot;
    private boolean lastSprinting;

    public HeypixelDisabler() {
        super(ModuleCategory.EXPLOIT, "Heypixel", "Disables some anticheats");
        this.registerSetting(ACAAimStep,ACAPerfectRotation,GrimBedPacketA,GrimBedPacketF,GrimAimModulo360,GrimDuplicateRotPlace);
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        IPacket<?> packet = event.packet;

        if (packet instanceof CHeldItemChangePacket && GrimBedPacketA.getCurrentValue()) {
            int slot = ((CHeldItemChangePacket)packet).getSlotId();
            if (slot == this.lastSlot && slot != -1) {
                event.cancelled = true;
            }
            this.lastSlot = ((CHeldItemChangePacket)packet).getSlotId();
        }

        if (packet instanceof CEntityActionPacket && GrimBedPacketF.getCurrentValue()) {
            if (((CEntityActionPacket)packet).getAction() == CEntityActionPacket.Action.START_SPRINTING) {
                if (this.lastSprinting) {
                    event.cancelled = true;
                }
                this.lastSprinting = true;
            } else if (((CEntityActionPacket)packet).getAction() == CEntityActionPacket.Action.STOP_SPRINTING) {
                if (!this.lastSprinting) {
                    event.cancelled = true;
                }
                this.lastSprinting = false;
            }
        }
        if (GrimDuplicateRotPlace.getCurrentValue()) {
            if (packet instanceof CPlayerPacket.RotationPacket || packet instanceof CPlayerPacket.PositionRotationPacket) {
                float lastPlayerYaw = playerYaw;
                playerYaw = ((CPlayerPacket) packet).getYaw(0.0F);
                deltaYaw = Math.abs(playerYaw - lastPlayerYaw);
                rotated = true;

                if (deltaYaw > 2) {
                    float xDiff = Math.abs(deltaYaw - lastPlacedDeltaYaw);
                    if (xDiff < 0.0001) {
                        //log("Disabling DuplicateRotPlace!");
                        ((CPlayerPacket) packet).setYaw(((CPlayerPacket) packet).getYaw(0.0F) + 0.0002F);
                    }
                }
            } else if (packet instanceof CPlayerTryUseItemOnBlockPacket) {
                if (rotated) {
                    lastPlacedDeltaYaw = deltaYaw;
                    rotated = false;
                }
            }
        }
        if ((this.ACAAimStep.currentValue || this.ACAPerfectRotation.currentValue) && packet instanceof CPlayerPacket) {
            float currentYaw = ((CPlayerPacket) packet).getYaw(0.0f);
            float currentPitch = ((CPlayerPacket) packet).getPitch(0.0f);
            boolean modified = false;
            if (this.ACAAimStep.currentValue && this.shouldModifyRotation(currentYaw, currentPitch)) {
                float[] modifiedRotation = this.getModifiedRotation(currentYaw, currentPitch);
                currentYaw = modifiedRotation[0];
                currentPitch = modifiedRotation[1];
                modified = true;
            }

            if (this.ACAPerfectRotation.currentValue) {
                float[] antiPerfectRotation = this.getAntiPerfectRotation(currentYaw, currentPitch);
                if (antiPerfectRotation[0] != currentYaw || antiPerfectRotation[1] != currentPitch) {
                    currentYaw = antiPerfectRotation[0];
                    currentPitch = antiPerfectRotation[1];
                    modified = true;
                    //this.log("PerfectRotation: Modified rotation");
                }
            }

            if (modified) {
                ((CPlayerPacket) packet).setYaw(currentYaw);
                ((CPlayerPacket) packet).setPitch(clampPitch_To90(currentPitch));
            }

            this.lastYaw = ((CPlayerPacket) packet).getYaw(0.0f);
            this.lastPitch = ((CPlayerPacket) packet).getPitch(0.0f);
        }
        if (GrimAimModulo360.getCurrentValue()) {
            if (packet instanceof CPlayerPacket.RotationPacket || packet instanceof CPlayerPacket.PositionRotationPacket) {
                ((CPlayerPacket) packet).setYaw(((CPlayerPacket) packet).getYaw(0F) + randomCount /*2*/ * 360);
                randomCount += 5;
                if (randomCount >= 100) randomCount = 0;
            }
        }
    }
    private static int randomCount = 0;
    //Grim
    private float playerYaw;
    private float deltaYaw;
    private float lastPlacedDeltaYaw;
    private boolean rotated = false;
    //ACA
    private final Random random = new Random();
    private static final double[] PERFECT_PATTERNS = new double[]{0.1, 0.25};
    private float lastYaw = 0.0F;
    private float lastPitch = 0.0F;

    public static float clampPitch_To90(float pitch) {
        if (pitch > 90.0F) {
            return 90.0F;
        } else {
            return pitch < -90.0F ? -90.0F : pitch;
        }
    }

    private float normalizeYaw(float yaw) {
        while (yaw > 180.0F) {
            yaw -= 360.0F;
        }

        while (yaw < -180.0F) {
            yaw += 360.0F;
        }

        return yaw;
    }

    private boolean shouldModifyRotation(float currentYaw, float currentPitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
            return false;
        } else {
            double yawDelta = (double)Math.abs(this.normalizeYaw(currentYaw - this.lastYaw));
            double pitchDelta = (double)Math.abs(currentPitch - this.lastPitch);
            boolean isStepYaw = yawDelta < 1.0E-5 && pitchDelta > 1.0;
            boolean isStepPitch = pitchDelta < 1.0E-5 && yawDelta > 1.0;
            return isStepYaw || isStepPitch;
        }
    }

    private float[] getModifiedRotation(float yaw, float pitch) {
        double yawDelta = (double)Math.abs(this.normalizeYaw(yaw - this.lastYaw));
        double pitchDelta = (double)Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            newYaw = this.lastYaw + (float)(this.random.nextGaussian() * 0.001);
        }

        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            newPitch = this.lastPitch + (float)(this.random.nextGaussian() * 0.001);
        }

        return new float[]{newYaw, newPitch};
    }

    private float[] getAntiPerfectRotation(float yaw, float pitch) {
        if (this.lastYaw == 0.0F && this.lastPitch == 0.0F) {
            return new float[]{yaw, pitch};
        } else {
            double yawDelta = (double)Math.abs(this.normalizeYaw(yaw - this.lastYaw));
            double pitchDelta = (double)Math.abs(pitch - this.lastPitch);
            float newYaw = yaw;
            float newPitch = pitch;
            if (!this.isNoRotation(yawDelta) && this.isPerfectPattern(yawDelta)) {
                double jitter = this.random.nextGaussian() * 0.005;
                newYaw = yaw + (float)jitter;
            }

            if (!this.isNoRotation(pitchDelta) && this.isPerfectPattern(pitchDelta)) {
                double jitter = this.random.nextGaussian() * 0.005;
                newPitch = pitch + (float)jitter;
            }

            return new float[]{newYaw, newPitch};
        }
    }

    private boolean isNoRotation(double rotation) {
        return Math.abs(rotation) <= 1.0E-10 || this.isIntegerMultiple(360.0, rotation);
    }

    private boolean isPerfectPattern(double rotation) {
        if (!Double.isInfinite(rotation) && !Double.isNaN(rotation)) {
            for (double pattern : PERFECT_PATTERNS) {
                if (this.isIntegerMultiple(pattern, rotation)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isIntegerMultiple(double reference, double value) {
        if (reference == 0.0) {
            return Math.abs(value) <= 1.0E-10;
        } else {
            double multiple = value / reference;
            return Math.abs(multiple - (double)Math.round(multiple)) <= 1.0E-10;
        }
    }

}
