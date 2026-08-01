package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

public class GrimSpeed extends Module {
    private final ModeSetting grimode = new ModeSetting("GrimMode","choose speed mode","EntityCollide","EntityCollide");

    public GrimSpeed() {
        super(ModuleCategory.MOVEMENT,"Grim","Speed for Grim-AntiCheat");
        registerSetting(grimode);
    }

    @EventTarget
    public void onMotion(EventMotion event) {

    }

    @EventTarget
    public void onPacket(EventReceivePacket event) {

    }

}
