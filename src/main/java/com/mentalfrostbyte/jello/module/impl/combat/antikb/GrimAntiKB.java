package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import team.sdhq.eventBus.annotations.EventTarget;

public class GrimAntiKB extends Module {
    private final ModeSetting grimmode = new ModeSetting("GrimMode","Choose Grim AntiKB Modes","GrimC0F","GrimC0F","GrimC07");
    public GrimAntiKB() {
        super(ModuleCategory.COMBAT, "Grim", "Grim AntiKB"/*"Epic prediction bypass 2025 dont let stormingmoon code again"*/);
        this.registerSetting(grimmode);
    }

    @EventTarget
    public void dontletmecodeagain(EventReceivePacket event) {
        if (mc.player != null) {
            if (event.packet instanceof SEntityVelocityPacket packet) {
                if (packet.getEntityID() == mc.player.getEntityId()) {
                    event.cancelled = true;
                    if (grimmode.getCurrentValue().equals("GrimC07")) {
                        BlockPos pos = new BlockPos(mc.player.getPosX(),mc.player.getPosY(),mc.player.getPosZ());
                        mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
                        mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK, pos.up(), Direction.DOWN));
                    }
                }
            }

            if (event.packet instanceof SConfirmTransactionPacket && grimmode.getCurrentValue().equals("GrimC0F")) {
                if (mc.player.ticksExisted > 180) {
                    event.cancelled = true;
                }
            }
        }
    }
}