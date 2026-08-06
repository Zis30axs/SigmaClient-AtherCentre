package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.EventLivingUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.entity.Entity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;

public class AttackReduceAntiKB extends Module {
    private final BooleanSetting usec0b = new BooleanSetting("UseC0B","UserC0BPacket Sprint", false);
    private final NumberSetting<Integer> keepattacktick = new NumberSetting<>("KeepAttackTick", "KeepAttackReduceTick", 5, 1, 10, 1);
    private final NumberSetting<Integer> oncecount = new NumberSetting<>("Once Count", "Attack once AttackCount", 1, 1, 5, 1);
    private final BooleanSetting alink = new BooleanSetting("Alink","Alink in Attack",false);
    private final BooleanSetting alinkinair = new BooleanSetting("Alink If Air","Alink If hurt in air,can use OnGround JumpReset together", false);
    private final BooleanSetting onlysprint = new BooleanSetting("Only Sprint","Only Sprint", true);
    private final BooleanSetting ongroundjump = new BooleanSetting("Ground JumpReset","In Ground JumpReset",false);
    private final BooleanSetting raytrace = new BooleanSetting("RayTrace","Need RayTrace Attack", true);
    private final BooleanSetting debug = new BooleanSetting("Debug", "Debug", false);

    //private static final BooleanSetting attackingkillurastopattack = new BooleanSetting("Attacking Killaura Stop Attack","Attacking Killaura Stop Attack", false);
    private static final BooleanSetting disonuse = new BooleanSetting("Disable on use","Disable on use",true);
    public AttackReduceAntiKB() {
        super(ModuleCategory.COMBAT, "AttackReduce", "AttackReduce");
        this.registerSetting(usec0b,keepattacktick,oncecount,onlysprint,raytrace,disonuse,debug);
    }

    public int attackTick = 0;
    private boolean canattack = false;

    private void debugLog(String message) {
        if (debug.getCurrentValue()) {
            MinecraftUtil.addChatMessage("§7[AttackReduce] §f" + message);
        }
    }

    @Override
    public void onDisable() {
        debugLog("Disabled");
        attackTick = 0;
        canattack = false;
    }

    @EventTarget
    public void onUpdate(EventLivingUpdate event) {
        if ((canattack || attackTick > 0) && mc.world != null && mc.player != null) {
            debugLog("Update: canattack=" + canattack + ", attackTick=" + attackTick);
            Entity entity = null;
            if (KillAura.targetEntity != null && !raytrace.getCurrentValue()) {
                entity = KillAura.targetEntity;
            } else if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
                entity = ((EntityRayTraceResult)mc.objectMouseOver).getEntity();
            } else if (!BlockUtil.rayTraceEntities(RotationCore.lastYaw,RotationCore.lastPitch,3.0f,false).isEmpty()) {
                entity = BlockUtil.rayTraceEntities(RotationCore.lastYaw,RotationCore.lastPitch,3.0f,false).get(0);
            }
            if (entity == null) {
                debugLog("No target entity found, cancelled");
                canattack = false;
                attackTick = 0;
                return;
            }
            if (disonuse.getCurrentValue() && mc.player.isHandActive()) {
                debugLog("Cancelled: using item (DisableOnUse)");
                canattack = false;
                attackTick = 0;
                return;
            }
            boolean state = mc.player.isSprinting();
            if (!state && usec0b.getCurrentValue()) {
                debugLog("Not sprinting, sending C0B START_SPRINTING");
                mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
            }
            if (onlysprint.getCurrentValue() && state || !onlysprint.getCurrentValue() || usec0b.getCurrentValue()) {
                debugLog("Attacking " + entity.getName().getUnformattedComponentText() + " x" + oncecount.getCurrentValue() + ", remaining tick=" + (attackTick - 1));
                for (int i = 0; i < oncecount.getCurrentValue(); i++) {
                    if (ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                        mc.getConnection().sendPacket(new CUseEntityPacket(entity, mc.player.isSneaking()));
                    } else {
                        mc.getConnection().sendPacket(new CUseEntityPacket(entity, mc.player.isSneaking()));
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                    }

                    mc.player.getMotion().x *= 0.6;
                    mc.player.getMotion().z *= 0.6;
                }
                attackTick--;
                debugLog("Executed: " + oncecount.getCurrentValue() + " attack(s) sent, knockback reduced (motionX=" + String.format("%.3f", mc.player.getMotion().x) + ", motionZ=" + String.format("%.3f", mc.player.getMotion().z) + "), remaining tick=" + attackTick + " - effective");
            } else {
                debugLog("Cancelled: not sprinting (OnlySprint)");
                attackTick = 0;
            }

            if (!state && usec0b.getCurrentValue()) {
                mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
            } else {
                mc.player.setSprinting(false);
            }
            canattack = false;
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        IPacket<?> packet = event.packet;
        if (mc.world != null && mc.player != null) {
            if (packet instanceof SEntityVelocityPacket && ((SEntityVelocityPacket) packet).getEntityID() == mc.player.getEntityId() && (((SEntityVelocityPacket) packet).motionX != 0 || ((SEntityVelocityPacket) packet).motionZ != 0)) {
                canattack = true;
                attackTick = (int) keepattacktick.getCurrentValue().intValue();
                debugLog("Velocity packet received (motionX=" + ((SEntityVelocityPacket) packet).motionX + ", motionZ=" + ((SEntityVelocityPacket) packet).motionZ + "), armed attackTick=" + attackTick);
            }
        }
    }
}
