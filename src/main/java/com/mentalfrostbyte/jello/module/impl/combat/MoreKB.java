package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventTick;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;

public class MoreKB extends Module {
    private final ModeSetting mode;
    private final BooleanSetting intelligent;
    private final BooleanSetting onlyGround;
    private LivingEntity target;

    public MoreKB() {
        super(ModuleCategory.COMBAT, "MoreKB", "Increases dealt knockback by resetting sprint with various strategies");
        this.mode = new ModeSetting("Mode", "Knockback strategy", 0,
                "Legit", "LegitFast", "LessPacket", "Packet", "DoublePacket");
        this.intelligent = new BooleanSetting("Intelligent", "Only reset sprint when the target is facing you", false);
        this.onlyGround = new BooleanSetting("Only Ground", "Only reset sprint while on the ground", true);
        this.registerSetting(this.mode, this.intelligent, this.onlyGround);
        this.target = null;
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || event.cancelled || mc.player == null || mc.world == null) {
            return;
        }
        if (event.packet instanceof CUseEntityPacket attackPacket
                && attackPacket.getAction() == CUseEntityPacket.Action.ATTACK) {
            Entity targetEntity = attackPacket.getEntityFromWorld(mc.world);
            if (targetEntity instanceof LivingEntity) {
                this.target = (LivingEntity) targetEntity;
            }
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        if (this.mode.getModeIndex() == 1) {
            if (this.target != null && this.isMoving()) {
                if (!this.onlyGround.getCurrentValue() || mc.player.isOnGround()) {
                    mc.player.sprintingTicksLeft = 0;
                }
                this.target = null;
            }
            return;
        }
        LivingEntity entity = null;
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY
                && mc.objectMouseOver instanceof EntityRayTraceResult entityHit
                && entityHit.getEntity() instanceof LivingEntity) {
            entity = (LivingEntity) entityHit.getEntity();
        }
        if (entity == null) {
            return;
        }
        double x = mc.player.getPosX() - entity.getPosX();
        double z = mc.player.getPosZ() - entity.getPosZ();
        float calcYaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
        float diffY = Math.abs(MathHelper.wrapDegrees(calcYaw - entity.rotationYawHead));
        if (this.intelligent.getCurrentValue() && diffY > 120.0F) {
            return;
        }
        if (entity.hurtTime == 10) {
            switch (this.mode.getModeIndex()) {
                case 0:
                    if (mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                        mc.player.setSprinting(true);
                    }
                    break;
                case 2:
                    if (mc.player.isSprinting()) {
                        mc.player.setSprinting(false);
                    }
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
                    mc.player.setSprinting(true);
                    break;
                case 3:
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
                    mc.player.setSprinting(true);
                    break;
                case 4:
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
                    mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
                    mc.player.setSprinting(true);
                    break;
            }
        }
    }

    private boolean isMoving() {
        return mc.player.movementInput.moveForward != 0.0F || mc.player.movementInput.moveStrafe != 0.0F;
    }

   // @Override
    //public String getFormattedName() {
     //   return this.getName() + " §7" + this.mode.getCurrentValue();
    //Claude为什么总是这样啊
}