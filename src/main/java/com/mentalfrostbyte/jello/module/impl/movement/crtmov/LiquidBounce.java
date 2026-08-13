package com.mentalfrostbyte.jello.module.impl.movement.crtmov;

import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import team.sdhq.eventBus.annotations.EventTarget;

/**
 * Pass-through corrector, behaviourally the same as {@link Sigma}.
 *
 * <p>The previous version gated on {@code getModWithTypeSetToName() instanceof Sigma}, so it
 * never ran in its own mode and instead fired alongside Sigma in Sigma's mode, doubling every
 * correction there. It also had no {@code EventMotion} handler and leaned on
 * {@code managers.RotationManager} to get the rotation onto the packet; that class is retired,
 * so the packet write lives here now.
 */
public class LiquidBounce extends CorrectorMode {

    public LiquidBounce() {
        super("LiquidBounce", "Corrector");
    }

    @EventTarget
    public void onPre(EventMotion event) {
        if (!event.isPre() || !this.isActiveMode()) {
            return;
        }

        if (this.hasRotation()) {
            event.setYaw(RotationCore.currentYaw);
            event.setPitch(RotationCore.currentPitch);
        }

        RotationCore.lastYaw = event.getYaw();
        RotationCore.lastPitch = event.getPitch();
    }

    @EventTarget
    public void onInput(EventMoveInput event) {
        if (this.canCorrect()) {
            MovementUtil.silentStrafe(event, RotationCore.currentYaw);
        }
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    public void onStrafe(EventMoveFlying event) {
        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    public void onLook(EventLook event) {
        if (this.canCorrect() && this.fixLook()) {
            event.yaw = RotationCore.currentYaw;
            event.pitch = RotationCore.currentPitch;
        }
    }
}
