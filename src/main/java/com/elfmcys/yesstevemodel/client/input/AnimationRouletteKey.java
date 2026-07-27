package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.gui.AnimationRouletteScreen;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.util.InputUtil;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.impl.gui.jello.YsmActionsGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;

/**
 * Port of upstream {@code client/input/AnimationRouletteKey} (1.20.1).
 *
 * <p>Translation notes: {@code KeyMapping} -> {@code KeyBinding}; Forge's
 * {@code KeyConflictContext.IN_GAME} has no counterpart, and is instead enforced by
 * {@code InputUtil.isPlayerReady()} at dispatch (it already refuses while any screen is open);
 * {@code KeyModifier} rides alongside the binding as {@link InputUtil.Modifier} because 1.16.5
 * {@code KeyBinding} has no modifier field. Default keys are upstream's: roulette = KEYSYM 90
 * ({@code Z}), lock = ALT + KEYSYM 76 ({@code L}).
 *
 * <p>Registration is not an event here — {@link #register()} appends to
 * {@code GameSettings#keyBindings} following OptiFine's {@code ofKeyBindZoom} precedent, so the keys
 * show up in the vanilla Controls screen and are persisted to {@code options.txt}. Dispatch is not
 * an event either — {@link YsmKeyDispatcher#fireKeyInput} is called at the exact spot where Forge
 * fires {@code InputEvent.Key} (the last statement of {@code KeyboardListener#onKeyEvent}), after
 * the screen dispatch and the {@code KeyBinding} state updates, and never consumes the key.
 */
public class AnimationRouletteKey {

    /**
     * Upstream's default is KEYSYM 90 ({@code Z}), but on this client the roulette's hotkey is the key
     * the player bound to Sigma's "YSM Actions" module — see {@link #moduleKeybind()}. Shipping a
     * second, separate default would mean two different keys opening the same wheel, with only one of
     * them able to close it. So this vanilla binding is registered (it keeps upstream's mechanism and
     * gives the wheel an entry in the vanilla Controls screen) but defaults to <em>unbound</em>: set
     * it there only if a vanilla-style binding is wanted alongside the module bind.
     */
    public static final KeyBinding KEY_ROULETTE = new KeyBinding(
            "key.yes_steve_model.animation_roulette.desc", InputMappings.Type.KEYSYM,
            InputMappings.INPUT_INVALID.getKeyCode(), "key.category.yes_steve_model");

    public static final KeyBinding KEY_LOCK = new KeyBinding(
            "key.yes_steve_model.lock_roulette.desc", InputMappings.Type.KEYSYM, 76,
            "key.category.yes_steve_model");

    /**
     * Upstream declares {@link #KEY_LOCK} with {@code KeyModifier.ALT}, but its own handler
     * ({@code AnimationLockEvent#onKeyInput}) tests the binding with plain
     * {@code KeyMapping#matches(key, scanCode)}, which in Forge does <em>not</em> consult the
     * modifier — so upstream's lock actually toggles on a bare {@code L}, and the declared ALT only
     * affects conflict display in the Controls screen. Matched here, both for fidelity and because
     * 1.16.5 {@code KeyBinding} has no modifier to display: the Controls screen would show plain
     * {@code L} while silently requiring ALT.
     */
    public static final InputUtil.Modifier LOCK_MODIFIER = InputUtil.Modifier.NONE;

    private AnimationRouletteKey() {
    }

    public static KeyBinding[] register() {
        return new KeyBinding[]{KEY_ROULETTE, KEY_LOCK};
    }

    /**
     * The GLFW key the player bound to Sigma's "YSM Actions" module, or {@code -1} when unbound.
     *
     * <p>This is the roulette's real hotkey on this client: {@code ModuleKeyPress.press} routes that
     * key to {@code YsmActionsGUI.openWhilePressing} -> {@code OpenYsmScreens.openActionWheel}. Read
     * the same way Sigma's own screens read their bind (see
     * {@code ClickGuiScreen.keyPressed}, which closes on {@code getKeybindFor(ClickGuiHolder.class)}),
     * so rebinding the module in the Keybinds GUI moves the roulette with it and no second source of
     * truth is introduced.
     *
     * <p>Defensively guarded: this is reachable from the key callback, which can fire before
     * {@code Client}/{@code moduleManager} are fully constructed.
     */
    public static int moduleKeybind() {
        try {
            Client client = Client.getInstance();
            if (client == null || client.moduleManager == null) {
                return -1;
            }
            Module module = client.moduleManager.getModuleByClass(YsmActionsGUI.class);
            if (module == null) {
                return -1;
            }
            return client.moduleManager.getKeyManager().getKeybindFor(module);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** True when {@code key} is a hotkey that opens the roulette (module bind or vanilla binding). */
    public static boolean isRouletteHotkey(int keysym, int scancode) {
        int moduleKey = moduleKeybind();
        if (moduleKey != -1 && keysym == moduleKey) {
            return true;
        }
        return KEY_ROULETTE.keyCode.getKeyCode() != InputMappings.INPUT_INVALID.getKeyCode()
                && KEY_ROULETTE.matchesKey(keysym, scancode);
    }

    /** True when a roulette is currently on screen. Null-safe: reachable before the client exists. */
    public static boolean isRouletteOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        Screen current = minecraft.currentScreen;
        return current instanceof AnimationRouletteScreen;
    }

    /** Upstream {@code onKeyInput(InputEvent.Key)}; {@code action == 1} is GLFW_PRESS. */
    public static boolean onKeyInput(int keysym, int scancode, int action) {
        if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady() || action != 1
                || !InputUtil.isKeyPressed(keysym, scancode, KEY_ROULETTE)) {
            return false;
        }
        if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get().booleanValue()) {
            return false;
        }
        if (TouhouLittleMaidCompat.isMaidChatAvailable()) {
            TouhouLittleMaidCompat.openMaidChat();
            return true;
        }
        if (Minecraft.getInstance().player == null) {
            return false;
        }
        Minecraft.getInstance().player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            openRoulette(cap.getModelId(), cap.getModelAssembly(), cap, keysym);
        });
        return true;
    }

    /**
     * Upstream's open body, split out so the module-key entry point
     * ({@code OpenYsmScreens#openActionWheel}) reaches the same code instead of duplicating it.
     *
     * @param hotkey the GLFW key that triggered the open, or {@code -1} if it was not a key press.
     *               The screen keeps it so it can recognise — and swallow — that same key.
     *
     * <p>Deliberately has no {@code currentScreen == null} guard: the key path is already gated by
     * {@code InputUtil.isPlayerReady()} (which refuses while any screen is up), and the GUI path
     * (a button on the model-selection screen) legitimately wants to replace the open screen —
     * refusing there would fall back to the retired wheel, the very store nothing reads.
     *
     * <p>Upstream's second branch here toggles the wheel shut when it is already open. Dropped: on
     * this client the hotkey must not close the wheel (see
     * {@code AnimationRouletteScreen#keyPressed}), and the branch was unreachable anyway —
     * {@code InputUtil.isPlayerReady()} already returns false whenever a screen is up, upstream
     * included.
     */
    public static boolean openRoulette(String modelId, ModelAssembly modelAssembly,
                                       com.elfmcys.yesstevemodel.capability.PlayerCapability cap,
                                       int hotkey) {
        if (modelAssembly == null
                || modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
            return false;
        }
        if (isRouletteOpen()) {
            // Already open: do nothing at all, rather than re-opening or closing.
            return true;
        }
        Minecraft.getInstance().displayGuiScreen(new AnimationRouletteScreen(modelId, modelAssembly, cap, hotkey));
        return true;
    }

    /**
     * Upstream's lock half lives in {@code AnimationLockEvent#onKeyInput}; this is its guard. Bare
     * {@code matchesKey}, no modifier test — see {@link #LOCK_MODIFIER}.
     */
    public static boolean isLockKey(int keysym, int scancode) {
        return KEY_LOCK.matchesKey(keysym, scancode);
    }
}
