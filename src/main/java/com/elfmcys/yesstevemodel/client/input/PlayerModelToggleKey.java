package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.DisclaimerScreen;
import com.elfmcys.yesstevemodel.client.gui.PlayerModelScreen;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;

/**
 * Port of upstream {@code client/input/PlayerModelToggleKey} (1.20.1): ALT+Y opens the model
 * picker ({@link PlayerModelScreen}), passing through the one-time {@link DisclaimerScreen} until
 * the user ticks "read".
 *
 * <p>Translation notes:
 * <ul>
 *   <li>Upstream declares the binding with {@code KeyModifier.ALT} and
 *       {@code KeyConflictContext.IN_GAME}. 1.16.5 {@code KeyBinding} has neither, so the modifier
 *       is tested at dispatch ({@link InputUtil#isKeyPressed(int, int, KeyBinding,
 *       InputUtil.Modifier)}) and the conflict context becomes the {@code currentScreen == null}
 *       gate in {@link #onKeyInput}. Closing an already-open picker on the same key is handled by
 *       the screen itself ({@code PlayerModelScreen#handleToggleKey}), matching upstream.</li>
 *   <li>The {@code ServerConfig.CAN_SWITCH_MODEL} branch ({@code ExtraPlayerConfigScreen} when the
 *       server forbids switching) is cut with server sync; offline the picker always opens.</li>
 *   <li>{@code sendUnavailableMessage} has no local counterpart; unavailable state is a silent
 *       no-op, as in {@code AnimationRouletteKey}.</li>
 * </ul>
 */
public class PlayerModelToggleKey {

    /** Upstream default: KEYSYM 89 ({@code Y}) with {@code KeyModifier.ALT}. */
    public static final KeyBinding KEY_MAPPING = new KeyBinding(
            "key.yes_steve_model.player_model.desc", InputMappings.Type.KEYSYM, 89,
            "key.category.yes_steve_model");

    /**
     * Unlike {@code AnimationRouletteKey#LOCK_MODIFIER}, the ALT here is real: upstream's
     * {@code InputUtil.isKeyPressed(event, KEY_MAPPING)} consults the binding's declared modifier,
     * so a bare {@code Y} never opens the picker.
     */
    public static final InputUtil.Modifier TOGGLE_MODIFIER = InputUtil.Modifier.ALT;

    private PlayerModelToggleKey() {
    }

    public static KeyBinding[] register() {
        return new KeyBinding[]{KEY_MAPPING};
    }

    /** True when the press matches the binding <em>and</em> ALT is held (upstream's modifier). */
    public static boolean isToggleKey(int keysym, int scancode) {
        return InputUtil.isKeyPressed(keysym, scancode, KEY_MAPPING, TOGGLE_MODIFIER);
    }

    /**
     * Upstream {@code onKeyInput(InputEvent.Key)}; {@code action == 1} is GLFW_PRESS. The
     * {@code currentScreen} gate reproduces upstream's {@code KeyConflictContext.IN_GAME}.
     */
    public static boolean onKeyInput(int keysym, int scancode, int action) {
        if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady() || action != 1
                || !isToggleKey(keysym, scancode)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.currentScreen != null) {
            return false;
        }
        openModelScreen();
        return true;
    }

    /** Shared by the hotkey and {@code OpenYsmScreens.openModelManager}: disclaimer once, then the picker. */
    public static void openModelScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (GeneralConfig.isDisclaimerShow()) {
            minecraft.displayGuiScreen(new DisclaimerScreen());
        } else {
            minecraft.displayGuiScreen(new PlayerModelScreen());
        }
    }
}
