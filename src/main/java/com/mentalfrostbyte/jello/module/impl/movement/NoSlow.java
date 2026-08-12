package com.mentalfrostbyte.jello.module.impl.movement;

/*
 * Port notes (1.8.9 "cn.unfair" NoSlow -> this client, 1.16.5): only the C0F consumable mode was
 * missing here, so it is merged into the existing FoodNoSlowMode as a third option.
 *   C0FPacketConfirmTransaction               -> CConfirmTransactionPacket
 *   C0DPacketCloseWindow                      -> CCloseWindowPacket
 *   CPacketSwapItemWithOffHand                -> CPlayerDiggingPacket(SWAP_ITEM_WITH_OFFHAND, ZERO, DOWN)
 *   C07PacketPlayerDigging RELEASE_USE_ITEM   -> CPlayerDiggingPacket.Action.RELEASE_USE_ITEM
 *   C08PacketPlayerBlockPlacement dir != 255  -> CPlayerTryUseItemOnBlockPacket
 *   C08PacketPlayerBlockPlacement dir == 255  -> CPlayerTryUseItemPacket (the eat itself, never held back)
 *   S2FPacketSetSlot                          -> SSetSlotPacket
 *   S08PacketPlayerPosLook                    -> SPlayerPositionLookPacket
 *   S12PacketEntityVelocity                   -> SEntityVelocityPacket
 *   PacketEvent(SEND / RECEIVE)               -> EventSendPacket / EventReceivePacket
 *   PlayerUpdateEvent(Priority.LOW)           -> EventUpdate (@LowerPriority)
 *   KeyBindUtil.setKeyBindState(use, false)   -> EventUseItem.useItem = false (Minecraft#processKeyBinds seam)
 *   PacketUtil.sendPacketNoEvent(p)           -> NetworkManager#sendNoEventPacket
 *   PacketUtil.receivePacketNoEvent(p)        -> NetworkManager#processPacket on the client thread
 *   ItemUtil.isEating()                       -> Item#isFood, the gate the food branch here already uses
 *   ModernOffhandInteraction pre-swap         -> native offhand, so only swap when the eat runs offhand
 * Not ported: the motion/sprint percent properties (this client drops the slowdown through
 * EventSlowDown, which C0F cancels the same way Vanilla mode does), the FloatManager modes, and
 * upstream's fakeEating flag - there the 1.8 hooks fake the animation, here the client really is
 * using the item.
 * Deliberate deviations: the swap back only fires when a swap was actually sent (upstream swaps back
 * unconditionally, so aborting during CANCEL_C0F swapped the hands for real), and both the wait for a
 * transaction and the wait for the eat are watchdogged - on a target that sends no window
 * transactions (1.17+ answers a ping there instead, and swallowing that reply gets you timed out)
 * C0F would otherwise abort every eat forever.
 */

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventUseItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CCloseWindowPacket;
import net.minecraft.network.play.client.CConfirmTransactionPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.network.play.server.SSetSlotPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class NoSlow extends Module {
    /** Ticks the client may stay in EATING without an active hand before C0F gives the packets back. */
    private static final int C0F_IDLE_RELEASE_TICKS = 10;
    /** Ticks C0F waits for the transaction / set slot answer before deciding the target has none. */
    private static final int C0F_STUCK_RELEASE_TICKS = 10;
    /** Ticks C0F stays out of the way after such a timeout, so it cannot loop on aborted eats. */
    private static final int C0F_RETRY_COOLDOWN_TICKS = 40;
    /** Ticks between two offhand -> mainhand pre swaps, while the server slot answer is in flight. */
    private static final int C0F_SWAP_COOLDOWN_TICKS = 10;

    private static final BooleanSetting swordnoslow = new BooleanSetting("SwordNoSlow", "Sword NoSlow", true);
    private static final ModeSetting swordnoslowmode = new ModeSetting(
            "SwordNoSlowMode", "Sword NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting bownoslow = new BooleanSetting("BowNoSlow", "Bow NoSlow", true);
    private static final ModeSetting bownoslowmode = new ModeSetting(
            "BowNoSlowMode", "Bow NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting consumablenoslow = new BooleanSetting("FoodNoSlow", "Consumable NoSlow", true);
    private static final ModeSetting consumablenoslowmode = new ModeSetting(
            "FoodNoSlowMode", "Consumable NoSlow Mode", 0, "Vanilla", "GrimTick", "C0F");
    private static final BooleanSetting c0fdelayknockback = new BooleanSetting(
            "C0FDelayKnockback", "Hold your own knockback back until the C0F eat is over", true);
    private static final BooleanSetting c0fdelayinteract = new BooleanSetting(
            "C0FDelayInteract", "Hold block interactions back until the C0F eat is over", true);

    private boolean grimticknoslow;

    private enum C0FStep {
        NONE,
        CANCEL_C0F,
        SWAP_HANDS,
        EATING
    }

    private volatile C0FStep c0fStep = C0FStep.NONE;
    private volatile boolean c0fSwapped;
    private int c0fNoUsingItemTicks;
    private int c0fStuckTicks;
    private int c0fCooldownTicks;
    private int c0fSwapCooldownTicks;
    private boolean c0fWaitForIdle;
    private final Queue<IPacket<?>> c0fPackets = new LinkedBlockingQueue<>();
    private final Queue<IPacket<?>> c0fDelayedVelocity = new LinkedBlockingQueue<>();
    private final Queue<IPacket<?>> c0fDelayedInteraction = new LinkedBlockingQueue<>();

    public NoSlow() {
        super(ModuleCategory.MOVEMENT, "NoSlow", "Stops slowdown when using an item");
        c0fdelayknockback.setHidden(() -> !isFoodC0F());
        c0fdelayinteract.setHidden(() -> !isFoodC0F());
        this.registerSetting(
                swordnoslow,
                swordnoslowmode,
                bownoslow,
                bownoslowmode,
                consumablenoslow,
                consumablenoslowmode,
                c0fdelayknockback,
                c0fdelayinteract
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
    @LowestPriority
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (event.packet instanceof CPlayerTryUseItemPacket && this.grimticknoslow) {
            this.grimticknoslow = false;
        }

        if (!isC0FUsable() || event.cancelled) {
            // Something ahead of us already dropped this packet - queueing it would send it later.
            return;
        }

        if (this.c0fStep != C0FStep.NONE
                && c0fdelayinteract.getCurrentValue()
                && event.packet instanceof CPlayerTryUseItemOnBlockPacket) {
            // A real block right click carries a face, the eat itself is a CPlayerTryUseItemPacket,
            // so holding this one back never touches the eat.
            event.cancelled = true;
            this.c0fDelayedInteraction.add(event.packet);
            return;
        }

        if (this.c0fStep != C0FStep.NONE && event.packet instanceof CConfirmTransactionPacket) {
            event.cancelled = true;
            this.c0fPackets.add(event.packet);

            if (this.c0fStep == C0FStep.CANCEL_C0F) {
                this.c0fStep = C0FStep.SWAP_HANDS;
                this.c0fStuckTicks = 0;
                this.c0fSwapped = true;
                this.sendNoEvent(swapHandsPacket());
            }

            return;
        }

        if (this.c0fStep == C0FStep.EATING
                && event.packet instanceof CPlayerDiggingPacket digging
                && digging.getAction() == CPlayerDiggingPacket.Action.RELEASE_USE_ITEM) {
            this.releaseC0F();
        }
    }

    @EventTarget
    @LowestPriority
    public void onReceivePacket(EventReceivePacket event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null || !isC0FUsable() || event.cancelled) {
            return;
        }

        if (this.c0fStep == C0FStep.NONE) {
            return;
        }

        if (c0fdelayknockback.getCurrentValue()
                && event.packet instanceof SEntityVelocityPacket velocity
                && velocity.getEntityID() == mc.player.getEntityId()) {
            event.cancelled = true;
            this.c0fDelayedVelocity.add(event.packet);
            return;
        }

        if (this.c0fStep == C0FStep.SWAP_HANDS && event.packet instanceof SSetSlotPacket) {
            // The server acked the swap. Stop vetoing the use key and vanilla starts the real eat again.
            this.c0fStep = C0FStep.EATING;
            this.c0fStuckTicks = 0;
            this.c0fNoUsingItemTicks = 0;
            return;
        }

        if (event.packet instanceof SPlayerPositionLookPacket) {
            this.releaseC0F();
        }
    }

    /**
     * Keeps the use key down state away from vanilla while the trick is being set up. Upstream forced
     * the keybind itself up and pressed it again on the set slot answer; here the veto simply stops
     * once the state machine reaches EATING, so a physically held key resumes the eat on its own and
     * the keybind can never stay stuck.
     */
    @EventTarget
    public void onUseItem(EventUseItem event) {
        if (this.isEnabled() && isC0FUsable() && this.c0fStep != C0FStep.NONE && this.c0fStep != C0FStep.EATING) {
            event.useItem = false;
        }
    }

    @EventTarget
    @LowerPriority
    public void onUpdate(EventUpdate event) {
        this.handleC0FTick();
    }

    @EventTarget
    public void onLoadWorld(EventLoadWorld event) {
        // Queued packets belong to the world we just left, replaying them into the new one is wrong.
        this.c0fPackets.clear();
        this.c0fDelayedVelocity.clear();
        this.c0fDelayedInteraction.clear();
        this.c0fStep = C0FStep.NONE;
        this.c0fSwapped = false;
        this.resetC0FTimers();
    }

    @Override
    public void onDisable() {
        this.grimticknoslow = false;

        if (this.c0fStep != C0FStep.NONE) {
            this.releaseC0F();
        } else {
            this.flushDelayedInteraction();
            this.flushDelayedVelocity();
        }

        this.resetC0FTimers();
    }

    private void applySlowMode(EventSlowDown event, ModeSetting setting) {
        switch (setting.getCurrentValue()) {
            case "Vanilla", "C0F" -> event.cancelled = true;
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

    private void handleC0FTick() {
        if (!this.isEnabled() || !isC0FUsable() || mc.player == null || mc.getConnection() == null) {
            if (this.c0fStep != C0FStep.NONE) {
                this.releaseC0F();
            }

            this.resetC0FTimers();
            return;
        }

        if (this.c0fSwapCooldownTicks > 0) {
            this.c0fSwapCooldownTicks--;
        }

        if (this.c0fCooldownTicks > 0) {
            this.c0fCooldownTicks--;
            return;
        }

        if (this.c0fStep == C0FStep.NONE) {
            if (this.c0fWaitForIdle) {
                // A timeout already cost the player one eat on this target. Stay out until the hand is
                // free again instead of chopping up every following eat as well.
                if (mc.player.isHandActive()) {
                    return;
                }

                this.c0fWaitForIdle = false;
            }

            // The trick eats out of the mainhand, so an offhand eat has to be swapped over first.
            if (this.trySwapOffhandFoodToMainHand()) {
                return;
            }

            this.c0fNoUsingItemTicks = 0;

            if (this.isConsuming()) {
                this.c0fStep = C0FStep.CANCEL_C0F;
                this.c0fSwapped = false;
                this.c0fStuckTicks = 0;
                this.c0fPackets.clear();

                if (mc.player.openContainer != mc.player.container) {
                    this.sendNoEvent(new CCloseWindowPacket(mc.player.openContainer.windowId));
                }
            }

            return;
        }

        if (this.c0fStep == C0FStep.EATING) {
            this.c0fStuckTicks = 0;

            if (mc.player.isHandActive()) {
                this.c0fNoUsingItemTicks = 0;
            } else if (++this.c0fNoUsingItemTicks >= C0F_IDLE_RELEASE_TICKS) {
                this.releaseC0F();
            }

            return;
        }

        // CANCEL_C0F / SWAP_HANDS both wait on the server. If the answer never comes the target has no
        // window transactions, so hand everything back and stay out for a while - otherwise the use
        // key veto would abort every single eat.
        this.c0fNoUsingItemTicks = 0;

        if (++this.c0fStuckTicks >= C0F_STUCK_RELEASE_TICKS) {
            this.releaseC0F();
            this.c0fCooldownTicks = C0F_RETRY_COOLDOWN_TICKS;
            this.c0fWaitForIdle = true;
        }
    }

    private synchronized void releaseC0F() {
        IPacket<?> packet;
        while ((packet = this.c0fPackets.poll()) != null) {
            this.sendNoEvent(packet);
        }

        if (this.c0fSwapped) {
            this.sendNoEvent(swapHandsPacket());
            this.c0fSwapped = false;
        }

        this.c0fStep = C0FStep.NONE;
        this.c0fNoUsingItemTicks = 0;
        this.c0fStuckTicks = 0;

        // The eat is over, either finished or let go of. Interactions first, now that the real hand is
        // back, then the knockback. The eat is never forced to complete: a half eaten food just flushes.
        this.flushDelayedInteraction();
        this.flushDelayedVelocity();
    }

    private void flushDelayedInteraction() {
        IPacket<?> packet;
        while ((packet = this.c0fDelayedInteraction.poll()) != null) {
            this.sendNoEvent(packet);
        }
    }

    private void flushDelayedVelocity() {
        if (this.c0fDelayedVelocity.isEmpty()) {
            return;
        }

        List<IPacket<?>> pending = new ArrayList<>();
        IPacket<?> packet;
        while ((packet = this.c0fDelayedVelocity.poll()) != null) {
            pending.add(packet);
        }

        // Receives are cancelled on the netty thread, so the replay has to go back to the client thread.
        mc.execute(() -> {
            ClientPlayNetHandler connection = mc.getConnection();
            if (connection == null) {
                return;
            }

            for (IPacket<?> delayed : pending) {
                NetworkManager.processPacket(delayed, connection.getNetworkManager().packetListener);
            }
        });
    }

    private boolean trySwapOffhandFoodToMainHand() {
        if (this.c0fSwapCooldownTicks > 0 || !mc.player.isHandActive() || mc.player.getActiveHand() != Hand.OFF_HAND) {
            return false;
        }

        if (!isConsumable(mc.player.getHeldItemOffhand()) || isConsumable(mc.player.getHeldItemMainhand())) {
            return false;
        }

        this.sendNoEvent(swapHandsPacket());
        this.c0fSwapCooldownTicks = C0F_SWAP_COOLDOWN_TICKS;
        return true;
    }

    private boolean isConsuming() {
        return mc.player.isHandActive() && isConsumable(mc.player.getActiveItemStack());
    }

    private void resetC0FTimers() {
        this.c0fNoUsingItemTicks = 0;
        this.c0fStuckTicks = 0;
        this.c0fCooldownTicks = 0;
        this.c0fSwapCooldownTicks = 0;
        this.c0fWaitForIdle = false;
    }

    private void sendNoEvent(IPacket<?> packet) {
        ClientPlayNetHandler connection = mc.getConnection();
        if (connection != null) {
            connection.getNetworkManager().sendNoEventPacket(packet);
        }
    }

    private static CPlayerDiggingPacket swapHandsPacket() {
        return new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN);
    }

    private static boolean isConsumable(ItemStack stack) {
        // Upstream took UseAction EAT / DRINK minus splash potions. This client's food branch keys off
        // Item#isFood, and the state machine has to match the branch that cancels the slowdown, or C0F
        // would run its packet trick on items whose slowdown is left in place.
        return stack != null && !stack.isEmpty() && stack.getItem().isFood();
    }

    private static boolean isFoodC0F() {
        return consumablenoslow.getCurrentValue() && "C0F".equals(consumablenoslowmode.getCurrentValue());
    }

    private static boolean isC0FUsable() {
        // 1.17+ dropped window transactions, the client answers a ping on that packet instead and
        // swallowing the answer times you out.
        return isFoodC0F() && !JelloPortal.getVersion().newerThanOrEqualTo(ProtocolVersion.v1_17);
    }
}
