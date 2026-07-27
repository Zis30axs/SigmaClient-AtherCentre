package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SPlayAnimationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.MovementInput;

/**
 * Port of upstream {@code client/event/AnimationLockEvent} (1.20.1).
 *
 * <p>This is what makes a roulette pick <em>stop</em>: a picked extra animation stays on until the
 * player moves, at which point the tick half clears the pending model switch. Without it a chosen
 * animation would either never end or never begin to look interruptible.
 *
 * <p>No event bus here, so upstream's two {@code @SubscribeEvent} halves become plain entry points:
 * <ul>
 *   <li>{@link #onKeyInput(int, int)} — called from {@code KeyboardListener#onKeyEvent} via
 *       {@code YsmKeyDispatcher}.</li>
 *   <li>{@link #onClientTick()} — called from {@code ClientTickEvent#onClientTick()}, which the
 *       {@code Minecraft#runTick} seam invokes at what upstream would call {@code Phase.START}
 *       (upstream subscribes at {@code Phase.END}). {@code movementInput} is refreshed later in the
 *       tick by {@code ClientPlayerEntity#livingTick}, so this observes the previous tick's input:
 *       the release lands one tick (50 ms) later than upstream, on the same data. Accepted rather
 *       than adding a second tick seam.</li>
 * </ul>
 *
 * <p>Translation notes: {@code LocalPlayer#input} ({@code net.minecraft.client.player.Input}) ->
 * {@code ClientPlayerEntity#movementInput} ({@code net.minecraft.util.MovementInput}), with
 * {@code leftImpulse/forwardImpulse/jumping/shiftKeyDown} -> {@code moveStrafe/moveForward/jump/sneaking}.
 */
public class AnimationLockEvent {

    private static boolean animationLocked = false;

    private AnimationLockEvent() {
    }

    /** Upstream's key half: ALT+L toggles the lock. Modifier check lives in the dispatcher. */
    public static void onKeyInput(int keysym, int scancode) {
        animationLocked = !animationLocked;
    }

    public static void onClientTick() {
        if (!YesSteveModel.isAvailable() || animationLocked) {
            return;
        }
        ClientPlayerEntity localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !isPlayerMoving(localPlayer)) {
            return;
        }
        localPlayer.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            if (cap.isModelSwitching()) {
                cap.clearModelSwitch();
                if (NetworkHandler.isClientConnected()) {
                    NetworkHandler.sendToServer(C2SPlayAnimationPacket.createDefault());
                }
            }
        });
    }

    public static boolean isPlayerMoving(ClientPlayerEntity localPlayer) {
        MovementInput input = localPlayer.movementInput;
        return input != null && (isSignificantImpulse(input.moveStrafe) || isSignificantImpulse(input.moveForward)
                || input.jump || input.sneaking);
    }

    private static boolean isSignificantImpulse(float impulse) {
        return Math.abs(impulse) > 1.0E-5F;
    }

    public static void toggleLock() {
        animationLocked = !animationLocked;
    }

    public static boolean isLocked() {
        return animationLocked;
    }
}
