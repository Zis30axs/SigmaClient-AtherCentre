package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventStopUseItem;
import com.mentalfrostbyte.jello.event.impl.player.action.EventUseItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import de.florianmichael.viamcp.fixes.compat.InteractionProtocol;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.network.play.server.SSetSlotPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
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
            "FoodNoSlowMode", "Consumable NoSlow Mode", 0, "Vanilla", "GrimTick", "Anthropic");

    private final Object anthropicLock = new Object();
    private volatile AnthropicState anthropicState = AnthropicState.IDLE;
    private ItemStack anthropicMainHand = ItemStack.EMPTY;
    private ItemStack anthropicOffHand = ItemStack.EMPTY;
    private int anthropicHotbarSlot = -1;
    private int anthropicTicks;
    private int anthropicIdleTicks;
    private boolean anthropicHandsSwapped;
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

        if (this.anthropicState == AnthropicState.EATING && this.hasAnthropicMode()) {
            event.cancelled = true;
            this.forceSprint();
            return;
        }

        ItemStack activeStack = mc.player.getActiveItemStack();
        if (activeStack.getItem().isFood() && this.hasAnthropicMode()) {
            if (this.anthropicState == AnthropicState.IDLE && this.canStartAnthropic()) {
                this.beginAnthropic();
            }
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
    public void onUseItem(EventUseItem event) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.isWaitingForAnthropic()) {
            event.useItem = false;
        }
    }

    @EventTarget
    public void onStopUseItem(EventStopUseItem event) {
        if (this.isEnabled() && this.isWaitingForAnthropic()) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (event.packet instanceof CPlayerTryUseItemPacket && this.grimticknoslow) {
            this.grimticknoslow = false;
        }

        if (event.packet instanceof CPlayerDiggingPacket packet
                && packet.getAction() == CPlayerDiggingPacket.Action.RELEASE_USE_ITEM
                && this.anthropicState != AnthropicState.IDLE) {
            this.finishAnthropic(true);
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (!this.isEnabled() || this.anthropicState == AnthropicState.IDLE) {
            return;
        }

        if (event.packet instanceof SConfirmTransactionPacket) {
            synchronized (this.anthropicLock) {
                event.cancelled = true;
                if (this.anthropicState == AnthropicState.WAITING_FOR_TRANSACTION) {
                    this.anthropicState = AnthropicState.WAITING_FOR_SLOT;
                    this.anthropicTicks = 0;
                    this.anthropicHandsSwapped = true;
                    this.sendSwapHandsPacket();
                }
            }
            return;
        }

        if (event.packet instanceof SEntityVelocityPacket packet
                && mc.player != null
                && packet.getEntityID() == mc.player.getEntityId()) {
            event.cancelled = true;
            return;
        }

        if (event.packet instanceof SSetSlotPacket packet) {
            synchronized (this.anthropicLock) {
                if (this.anthropicState == AnthropicState.WAITING_FOR_SLOT
                        && this.isAnthropicHandSlot(packet)) {
                    this.anthropicState = AnthropicState.WAITING_FOR_REFRESH;
                    this.anthropicTicks = 0;
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled() || this.anthropicState == AnthropicState.IDLE) {
            return;
        }

        if (mc.player == null || mc.world == null || !this.hasAnthropicMode()) {
            this.finishAnthropic(mc.player != null && mc.world != null && mc.getConnection() != null);
            return;
        }

        if (!mc.gameSettings.keyBindUseItem.isKeyDown()
                || mc.player.inventory.currentItem != this.anthropicHotbarSlot) {
            this.finishAnthropic(true);
            return;
        }

        synchronized (this.anthropicLock) {
            this.anthropicTicks++;

            switch (this.anthropicState) {
                case WAITING_FOR_TRANSACTION -> {
                    if (this.anthropicTicks > 20) {
                        this.finishAnthropic(false);
                    }
                }
                case WAITING_FOR_SLOT, WAITING_FOR_REFRESH -> {
                    if (this.anthropicState == AnthropicState.WAITING_FOR_REFRESH
                            && this.areAnthropicHandsRefreshed()) {
                        this.anthropicState = AnthropicState.EATING;
                        this.anthropicTicks = 0;
                        this.anthropicIdleTicks = 0;
                    } else if (this.anthropicTicks > 20) {
                        this.finishAnthropic(true);
                    }
                }
                case EATING -> {
                    if (mc.player.isHandActive()) {
                        this.anthropicIdleTicks = 0;
                    } else if (++this.anthropicIdleTicks >= 5) {
                        this.finishAnthropic(true);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        this.finishAnthropic(false);
    }

    @Override
    public void onDisable() {
        this.finishAnthropic(mc.player != null && mc.world != null && mc.getConnection() != null);
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

    private boolean canStartAnthropic() {
        return InteractionProtocol.supportsOffhand()
                && mc.player.isHandActive()
                && mc.player.getActiveHand() == Hand.OFF_HAND
                && mc.player.getActiveItemStack().getItem().isFood()
                && !mc.player.getHeldItemMainhand().isEmpty();
    }

    private void beginAnthropic() {
        synchronized (this.anthropicLock) {
            if (this.anthropicState != AnthropicState.IDLE) {
                return;
            }

            this.anthropicMainHand = mc.player.getHeldItemMainhand().copy();
            this.anthropicOffHand = mc.player.getHeldItemOffhand().copy();
            this.anthropicHotbarSlot = mc.player.inventory.currentItem;
            this.anthropicTicks = 0;
            this.anthropicIdleTicks = 0;
            this.anthropicHandsSwapped = false;
            this.anthropicState = AnthropicState.WAITING_FOR_TRANSACTION;
        }
    }

    private boolean isWaitingForAnthropic() {
        return this.anthropicState == AnthropicState.WAITING_FOR_TRANSACTION
                || this.anthropicState == AnthropicState.WAITING_FOR_SLOT
                || this.anthropicState == AnthropicState.WAITING_FOR_REFRESH;
    }

    private boolean hasAnthropicMode() {
        return consumablenoslow.getCurrentValue()
                && "Anthropic".equals(consumablenoslowmode.getCurrentValue());
    }

    private boolean isAnthropicHandSlot(SSetSlotPacket packet) {
        int slot = packet.getSlot();
        if (packet.getWindowId() == -2) {
            return slot == this.anthropicHotbarSlot || slot == 40;
        }

        return packet.getWindowId() == 0
                && (slot == 36 + this.anthropicHotbarSlot || slot == 45);
    }

    private boolean areAnthropicHandsRefreshed() {
        return ItemStack.areItemStacksEqual(mc.player.getHeldItemMainhand(), this.anthropicOffHand)
                && ItemStack.areItemStacksEqual(mc.player.getHeldItemOffhand(), this.anthropicMainHand);
    }

    private void finishAnthropic(boolean restoreHands) {
        synchronized (this.anthropicLock) {
            if (this.anthropicState == AnthropicState.IDLE) {
                return;
            }

            boolean shouldRestoreHands = restoreHands
                    && this.anthropicHandsSwapped
                    && mc.getConnection() != null;
            this.anthropicState = AnthropicState.IDLE;
            this.anthropicHandsSwapped = false;

            if (shouldRestoreHands) {
                this.sendSwapHandsPacket();
            }

            this.anthropicMainHand = ItemStack.EMPTY;
            this.anthropicOffHand = ItemStack.EMPTY;
            this.anthropicHotbarSlot = -1;
            this.anthropicTicks = 0;
            this.anthropicIdleTicks = 0;
        }
    }

    private void sendSwapHandsPacket() {
        mc.getConnection().sendPacket(new CPlayerDiggingPacket(
                CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        ));
    }

    private enum AnthropicState {
        IDLE,
        WAITING_FOR_TRANSACTION,
        WAITING_FOR_SLOT,
        WAITING_FOR_REFRESH,
        EATING
    }
}
