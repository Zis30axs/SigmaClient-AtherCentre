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
 *   S2FPacketSetSlot as the swap ack          -> the food leaving the offhand locally (see isSwapLanded)
 * Not ported: the motion/sprint percent properties (this client drops the slowdown through
 * EventSlowDown, which C0F cancels the same way Vanilla mode does), the FloatManager modes, and
 * upstream's fakeEating flag - there the 1.8 hooks fake the animation, here the client really is
 * using the item.
 * The setup: any item in the mainhand, food in the offhand. Vanilla falls through to the offhand food,
 * so the hand the server is told about is the offhand, and the swap then moves that food out from under
 * it - that is what drops the server side slowdown. C0F only arms on exactly that, an offhand food eat.
 * Version independence: the swap is the mechanism, swallowing the transaction reply only stalls the
 * anticheat clock on top of it. <= 1.16 the reply is the window transaction, 1.17+ the very same packet
 * class carries the pong for the ping that replaced it (see ClientPlayNetHandler#handleConfirmTransaction),
 * and targets that send neither - 1.17+ vanilla, plain servers - get the swap on its own once SwapDelay is
 * up instead of nothing at all.
 * Deliberate deviations: the swap back only fires when a swap was actually sent (upstream swaps back
 * unconditionally, so aborting before the swap swapped the hands for real), and the waits are
 * watchdogged, so a target that never acks falls back to a plain eat instead of hanging.
 * Added on top of upstream: StartSlow, which leaves the vanilla slowdown and the sprint state alone until
 * the swap landed and then for SlowTicks eating ticks on top of that. So the order is: the eat starts and
 * runs plain while the swap is set up, the hands are swapped under it, the eat picks the food back up in the
 * mainhand and stays slow for SlowTicks, and only then does the no slow start. With StartSlow the eat is
 * never cut either, it keeps running through the setup - upstream's use key release is what StartSlow off
 * falls back to.
 * Slowing that opening window rather than cutting it is on purpose: the use the target has open at that
 * point names the offhand food, so what it wants to see is the movement eating that food produces, and
 * cutting the eat does not give it that - it only trades a slow window for a torn one. The window itself
 * cannot be removed, only kept honest and short: that offhand use is the very thing the swap pulls the food
 * out from under, so it has to reach the target. SwapDelay is how long it stays open.
 * Worth knowing: unlike the 1.8 client upstream ran on, this one applies the swap locally, so the food
 * moves into the mainhand and the local eat restarts from there (LivingEntity#updateActiveHand drops an
 * eat whose hand no longer holds that item).
 */

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventUseItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
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
    /** Ticks C0F waits for the swap to land before giving up on the target. */
    private static final int C0F_STUCK_RELEASE_TICKS = 10;
    /** Ticks C0F stays out of the way after such a timeout, so it cannot loop on the same eat. */
    private static final int C0F_RETRY_COOLDOWN_TICKS = 40;

    private static final BooleanSetting swordnoslow = new BooleanSetting("SwordNoSlow", "Sword NoSlow", true);
    private static final ModeSetting swordnoslowmode = new ModeSetting(
            "SwordNoSlowMode", "Sword NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting bownoslow = new BooleanSetting("BowNoSlow", "Bow NoSlow", true);
    private static final ModeSetting bownoslowmode = new ModeSetting(
            "BowNoSlowMode", "Bow NoSlow Mode", 0, "Vanilla", "GrimTick");
    private static final BooleanSetting consumablenoslow = new BooleanSetting("FoodNoSlow", "Consumable NoSlow", true);
    private static final ModeSetting consumablenoslowmode = new ModeSetting(
            "FoodNoSlowMode", "Consumable NoSlow Mode", 0, "Vanilla", "GrimTick", "C0F");
    private static final BooleanSetting c0fstartslow = new BooleanSetting(
            "StartSlow", "Leave the eat normal until the C0F hand swap went through", true);
    private static final NumberSetting<Integer> c0fslowticks = new NumberSetting<>(
            "SlowTicks", "Ticks the eat after the C0F hand swap keeps its normal slowdown", 8, 0, 32, 1);
    private static final NumberSetting<Integer> c0fswapdelay = new NumberSetting<>(
            "SwapDelay", "Ticks the offhand eat runs before C0F swaps the hands under it", 2, 0, 5, 1);
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
    private int c0fEatingTicks;
    private int c0fCooldownTicks;
    private boolean c0fWaitForIdle;
    private final Queue<IPacket<?>> c0fPackets = new LinkedBlockingQueue<>();
    private final Queue<IPacket<?>> c0fDelayedVelocity = new LinkedBlockingQueue<>();
    private final Queue<IPacket<?>> c0fDelayedInteraction = new LinkedBlockingQueue<>();

    public NoSlow() {
        super(ModuleCategory.MOVEMENT, "NoSlow", "Stops slowdown when using an item");
        c0fstartslow.setHidden(() -> !isFoodC0F());
        c0fslowticks.setHidden(() -> !isFoodC0F() || !c0fstartslow.getCurrentValue());
        c0fswapdelay.setHidden(() -> !isFoodC0F());
        c0fdelayknockback.setHidden(() -> !isFoodC0F());
        c0fdelayinteract.setHidden(() -> !isFoodC0F());
        this.registerSetting(
                swordnoslow,
                swordnoslowmode,
                bownoslow,
                bownoslowmode,
                consumablenoslow,
                consumablenoslowmode,
                c0fstartslow,
                c0fslowticks,
                c0fswapdelay,
                c0fdelayknockback,
                c0fdelayinteract
        );
    }

    @EventTarget
    public void onSlowDown(EventSlowDown event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        // When C0F spots an offhand food eat (the same isOffhandFoodEat() condition the tick handler
        // arms on), hand the slowdown back to vanilla. NoSlow simply does not apply to that eat, so
        // leaving the event uncancelled gives back the normal slow state.
        if (isFoodC0F() && this.isOffhandFoodEat()) {
            event.cancelled = false;
            return;
        }

        Item item = mc.player.getHeldItem(mc.player.getActiveHand()).getItem();
        if (swordnoslow.getCurrentValue() && item instanceof SwordItem) {
            this.applySlowMode(event, swordnoslowmode);
        }

        if (bownoslow.getCurrentValue() && item instanceof BowItem) {
            this.applySlowMode(event, bownoslowmode);
        }

        boolean startSlowHold = false;
        if (consumablenoslow.getCurrentValue()
                && item.isFood()) {
            startSlowHold = this.isC0FStartSlowHold();
            this.applySlowMode(event, consumablenoslowmode);
        }

        if (!startSlowHold) {
            // While StartSlow holds the eat back it has to look untouched, forced sprint included.
            this.forceSprint();
        }
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

        if (!isFoodC0F() || event.cancelled || this.c0fStep == C0FStep.NONE) {
            // Not armed, or something ahead of us already dropped this packet - queueing it would just
            // send it later.
            return;
        }

        if (c0fdelayinteract.getCurrentValue()
                && event.packet instanceof CPlayerTryUseItemOnBlockPacket) {
            // A real block right click carries a face, the eat itself is a CPlayerTryUseItemPacket,
            // so holding this one back never touches the eat.
            event.cancelled = true;
            this.c0fDelayedInteraction.add(event.packet);
            return;
        }

        if (event.packet instanceof CConfirmTransactionPacket) {
            // <= 1.16 this is the window transaction reply, 1.17+ the same class carries the pong for
            // the ping that replaced it, so the trick needs no version branch. Targets that send neither
            // still get the swap, the tick handler sends it once the grace is up.
            event.cancelled = true;
            this.c0fPackets.add(event.packet);
            this.beginSwap();
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
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null || !isFoodC0F() || event.cancelled) {
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

        if (event.packet instanceof SPlayerPositionLookPacket) {
            this.releaseC0F();
        }
    }

    /**
     * Keeps the use key down state away from vanilla while the trick is being set up, which is what
     * upstream did by forcing the keybind up and pressing it again once the swap was through. Here the
     * veto simply stops when the state machine reaches EATING, so a physically held key resumes the eat
     * on its own (Minecraft#processKeyBinds) and the keybind can never stay stuck.
     * StartSlow turns this off, and that is the better way round: the use the server has open at that point
     * names the offhand food, so what it wants to see is the movement eating that food produces. Cutting
     * the eat does not give it that, it only trades a slow window for a torn one. With StartSlow the eat
     * runs through the setup untouched and only loses its slowdown once the swap landed.
     */
    @EventTarget
    public void onUseItem(EventUseItem event) {
        if (this.isEnabled()
                && isFoodC0F()
                && !c0fstartslow.getCurrentValue()
                && this.c0fStep != C0FStep.NONE
                && this.c0fStep != C0FStep.EATING) {
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
            case "Vanilla" -> event.cancelled = true;
            case "C0F" -> {
                // StartSlow leaves the eat slowed until the hand swap actually went through, so the
                // server gets a normal looking start and only the rest of the eat is fast.
                if (!this.isC0FStartSlowHold()) {
                    event.cancelled = true;
                }
            }
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
        if (!this.isEnabled() || !isFoodC0F() || mc.player == null || mc.getConnection() == null) {
            if (this.c0fStep != C0FStep.NONE) {
                this.releaseC0F();
            }

            this.resetC0FTimers();
            return;
        }

        if (this.c0fCooldownTicks > 0) {
            this.c0fCooldownTicks--;
            return;
        }

        if (this.c0fStep == C0FStep.NONE) {
            if (this.c0fWaitForIdle) {
                // A timeout on this target already gave one eat back to vanilla. Stay out until the hand
                // is free again instead of retrying inside the same eat.
                if (mc.player.isHandActive()) {
                    return;
                }

                this.c0fWaitForIdle = false;
            }

            this.c0fNoUsingItemTicks = 0;

            if (this.isOffhandFoodEat()) {
                this.c0fStep = C0FStep.CANCEL_C0F;
                this.c0fSwapped = false;
                this.c0fStuckTicks = 0;
                this.c0fEatingTicks = 0;
                this.c0fPackets.clear();

                if (mc.player.openContainer != mc.player.container) {
                    this.sendNoEvent(new CCloseWindowPacket(mc.player.openContainer.windowId));
                }

                if (this.swapDelayTicks() <= 0) {
                    // SwapDelay 0: leave the target no window at all, the swap goes out on the very tick the
                    // eat is picked up. Nothing can be swallowed that early, so this trades the transaction
                    // for the shortest possible offhand use.
                    this.beginSwap();
                }
            }

            return;
        }

        if (this.c0fStep == C0FStep.EATING) {
            this.c0fStuckTicks = 0;

            if (mc.player.isHandActive()) {
                this.c0fNoUsingItemTicks = 0;
                // Only real eating ticks count: the swap resets the local eat, so this starts ticking on
                // the tick vanilla picks the food back up, which is what StartSlow measures.
                this.c0fEatingTicks++;
            } else if (++this.c0fNoUsingItemTicks >= C0F_IDLE_RELEASE_TICKS) {
                this.releaseC0F();
            }

            return;
        }

        this.c0fNoUsingItemTicks = 0;

        if (this.c0fStep == C0FStep.CANCEL_C0F) {
            // Nothing has been sent yet, so only the player can have ended this eat - and ending it is the
            // one thing that makes the swap pointless. With StartSlow the eat was left running, so an idle
            // hand says so outright; with the veto the hand is idle either way and only the use key still
            // knows. Drop back out instead of swapping hands out of nowhere.
            boolean eatOver = !mc.player.isHandActive()
                    && (c0fstartslow.getCurrentValue() || !mc.gameSettings.keyBindUseItem.isKeyDown());
            if (eatOver) {
                this.releaseC0F();
                return;
            }

            // Hanging the swap on a swallowed transaction / pong is upstream's ordering, so give the target
            // SwapDelay ticks to send one. Nothing came? Send the swap anyway - it is the part the mode is
            // built on, and 1.17+ vanilla or a plain server sends no such reply at all.
            if (++this.c0fStuckTicks >= this.swapDelayTicks()) {
                this.beginSwap();
            }

            return;
        }

        // SWAP_HANDS: the swap is out, so wait for the client to apply it. The local eat drops the moment it
        // does (LivingEntity#updateActiveHand), so an idle hand here is the swap working rather than the
        // player letting go - only the watchdog gets to bail out. If the server never acks, hand everything
        // back and stay out for a while, otherwise the use key veto would keep cutting eats.
        if (this.isSwapLanded()) {
            this.c0fStep = C0FStep.EATING;
            this.c0fStuckTicks = 0;
            this.c0fEatingTicks = 0;
            return;
        }

        if (++this.c0fStuckTicks >= C0F_STUCK_RELEASE_TICKS) {
            this.releaseC0F();
            this.c0fCooldownTicks = C0F_RETRY_COOLDOWN_TICKS;
            this.c0fWaitForIdle = true;
        }
    }

    private synchronized void beginSwap() {
        if (this.c0fStep != C0FStep.CANCEL_C0F) {
            return;
        }

        this.c0fStep = C0FStep.SWAP_HANDS;
        this.c0fStuckTicks = 0;
        this.c0fSwapped = true;
        this.sendNoEvent(swapHandsPacket());
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

    private boolean isOffhandFoodEat() {
        // The setup C0F is built on: any item in the mainhand, food in the offhand. Vanilla falls through
        // to the offhand food, so the use the server is told about names the offhand - and the swap then
        // pulls that food out from under it.
        return mc.player.isHandActive()
                && mc.player.getActiveHand() == Hand.OFF_HAND
                && isConsumable(mc.player.getHeldItemOffhand());
    }

    /**
     * The swap is applied by the client itself once the server's slot sync arrives, and the food leaving
     * the offhand is exactly that. Reading it off the inventory rather than off a set slot packet keeps
     * this working no matter which sync packet a target uses.
     */
    private boolean isSwapLanded() {
        return !isConsumable(mc.player.getHeldItemOffhand());
    }

    /**
     * True while StartSlow is deliberately keeping the eat normal: until the hand swap landed, and then for
     * SlowTicks eating ticks on top. The order is swap first, then the eat that follows it takes its plain
     * vanilla slowdown for a while, and only after that does the no slow start. The sprint state is left
     * alone for exactly as long.
     */
    private boolean isC0FStartSlowHold() {
        if (!isFoodC0F() || !c0fstartslow.getCurrentValue()) {
            return false;
        }

        return this.c0fStep != C0FStep.EATING
                || this.c0fEatingTicks <= Math.round(c0fslowticks.getCurrentValue());
    }

    /**
     * SwapDelay in ticks: how long the offhand eat is allowed to run before the swap goes out from under it.
     * That window is the price of the trick, not a bug in it - the use the target has open has to name the
     * offhand food, or there is nothing for the swap to pull the food out from under. It can only be kept
     * honest (StartSlow) and short (this), never removed.
     */
    private int swapDelayTicks() {
        return Math.round(c0fswapdelay.getCurrentValue());
    }

    private void resetC0FTimers() {
        this.c0fNoUsingItemTicks = 0;
        this.c0fStuckTicks = 0;
        this.c0fEatingTicks = 0;
        this.c0fCooldownTicks = 0;
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
}
