package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class JumpResetAntiKB extends Module {
    public JumpResetAntiKB() {
        super(ModuleCategory.COMBAT,"JumpReset","JumpReset AntiKB mode");
    }
    private boolean velpacket;

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled() && mc.player != null && mc.world != null) {
            if (event.packet instanceof SEntityVelocityPacket && ((SEntityVelocityPacket) event.packet).getEntityID() == mc.player.getEntityId() && (((SEntityVelocityPacket) event.packet).getMotionX() != 0 || ((SEntityVelocityPacket) event.packet).getMotionZ() != 0)) {
                if (!velpacket) {
                    velpacket = true;
                }
            }
        }
    }

    @EventTarget
    public void onMoveFlyingEvent(EventMoveFlying event) {
        if (this.isEnabled() && mc.player != null && mc.world != null) {
            if (velpacket) {
                if (mc.player.isSprinting() && mc.player.onGround) {
                    if (!mc.gameSettings.keyBindJump.isKeyDown()) {
                        mc.player.jump();
                    }
                }
                velpacket = false;
            }
        }
    }

    @EventTarget
    public void onMoveInputEvent(EventMoveInput event) {
        if (this.isEnabled() && mc.player != null && mc.world != null) {
            if (velpacket) {
                if (!mc.player.isSprinting() && mc.player.onGround) {
                    event.forward = 1.0f;
                }
            }
        }
    }
}
