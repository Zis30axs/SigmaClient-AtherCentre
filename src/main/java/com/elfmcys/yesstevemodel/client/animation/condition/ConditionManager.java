package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.util.Hand;

public class ConditionManager {
    private final ConditionSwing swingMainhand = new ConditionSwing(Hand.MAIN_HAND);
    private final ConditionSwing swingOffhand = new ConditionSwing(Hand.OFF_HAND);
    private final ConditionUse useMainhand = new ConditionUse(Hand.MAIN_HAND);
    private final ConditionUse useOffhand = new ConditionUse(Hand.OFF_HAND);
    private final ConditionHold holdMainhand = new ConditionHold(Hand.MAIN_HAND);
    private final ConditionHold holdOffhand = new ConditionHold(Hand.OFF_HAND);
    private final ConditionArmor armor = new ConditionArmor();
    private final ConditionVehicle vehicle = new ConditionVehicle();
    private final ConditionPassenger passenger = new ConditionPassenger();

    public void addTest(String name) {
        this.swingMainhand.addTest(name);
        this.swingOffhand.addTest(name);
        this.useMainhand.addTest(name);
        this.useOffhand.addTest(name);
        this.holdMainhand.addTest(name);
        this.holdOffhand.addTest(name);
        this.armor.addTest(name);
        this.vehicle.addTest(name);
        this.passenger.addTest(name);
    }

    public ConditionSwing getSwingMainhand() {
        return this.swingMainhand;
    }

    public ConditionSwing getSwingOffhand() {
        return this.swingOffhand;
    }

    public ConditionUse getUseMainhand() {
        return this.useMainhand;
    }

    public ConditionUse getUseOffhand() {
        return this.useOffhand;
    }

    public ConditionHold getHoldMainhand() {
        return this.holdMainhand;
    }

    public ConditionHold getHoldOffhand() {
        return this.holdOffhand;
    }

    public ConditionArmor getArmor() {
        return this.armor;
    }

    public ConditionVehicle getVehicle() {
        return this.vehicle;
    }

    public ConditionPassenger getPassenger() {
        return this.passenger;
    }
}
