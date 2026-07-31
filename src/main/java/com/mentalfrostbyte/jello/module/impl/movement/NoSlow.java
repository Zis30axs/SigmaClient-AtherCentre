package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class NoSlow extends Module {
    private static final BooleanSetting swordnoslow = new BooleanSetting("SwordNoSlow", "Sword NoSlow", true);
    private static final ModeSetting swordnoslowmode = new ModeSetting(
            "SwordNoSlowMode", "Sword NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting bownoslow = new BooleanSetting("BowNoSlow", "Bow NoSlow", true);
    private static final ModeSetting bownoslowmode = new ModeSetting(
            "BowNoSlowMode", "Bow NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting consumablenoslow = new BooleanSetting("FoodNoSlow", "Consumable NoSlow", true);
    private static final ModeSetting consumablenoslowmode = new ModeSetting(
            "FoodNoSlowMode", "Consumable NoSlow Mode", 0, "Vanilla", "GrimTick");

    private boolean grimticknoslow;

    public NoSlow() {
        super(ModuleCategory.MOVEMENT, "NoSlow", "Stops slowdown when using an item");
        this.registerSetting(
                swordnoslow,
                swordnoslowmode,
                bownoslow,
                bownoslowmode,
                consumablenoslow,
                consumablenoslowmode
        );
    }

    @EventTarget
    public void onSlowDown(EventSlowDown event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        Item item = mc.player.getHeldItem(mc.player.getActiveHand()).getItem();
        if (swordnoslow.getCurrentValue() && item instanceof SwordItem) {
            this.applySlowMode(event, swordnoslowmode);
        }

        if (bownoslow.getCurrentValue() && item instanceof BowItem) {
            this.applySlowMode(event, bownoslowmode);
        }

        if (consumablenoslow.getCurrentValue()
                && item.isFood()) {
            this.applySlowMode(event, consumablenoslowmode);
        }

        this.forceSprint();
    }

    private void forceSprint() {
        mc.player.setSprinting(true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true);
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (event.packet instanceof CPlayerTryUseItemPacket && this.grimticknoslow) {
            this.grimticknoslow = false;
        }
    }

    @Override
    public void onDisable() {
        this.grimticknoslow = false;
    }

    private void applySlowMode(EventSlowDown event, ModeSetting setting) {
        switch (setting.getCurrentValue()) {
            case "Vanilla" -> event.cancelled = true;
            case "GrimTick" -> {
                if (this.grimticknoslow) {
                    event.cancelled = true;
                    this.grimticknoslow = false;
                } else {
                    this.grimticknoslow = true;
                }
            }
        }
    }
}
