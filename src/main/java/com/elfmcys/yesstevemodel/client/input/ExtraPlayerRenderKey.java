package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.ExtraPlayerRenderScreen;
import com.elfmcys.yesstevemodel.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;

/**
 * Port of upstream {@code client/input/ExtraPlayerRenderKey} (1.20.1): ALT+P opens the
 * paper-doll overlay config ({@link ExtraPlayerRenderScreen}).
 *
 * <p>Translation notes (same shape as {@link PlayerModelToggleKey}):
 * <ul>
 *   <li>Upstream declares the binding with {@code KeyModifier.ALT} and
 *       {@code KeyConflictContext.IN_GAME}. 1.16.5 {@code KeyBinding} has neither, so the modifier
 *       is tested at dispatch ({@link InputUtil#isKeyPressed(int, int, KeyBinding,
 *       InputUtil.Modifier)}) and the conflict context becomes the {@code currentScreen == null}
 *       gate in {@link #onKeyInput}.</li>
 *   <li>Upstream's {@code InputUtil.isKeyPressed(event, KEY_MAPPING)} consults the binding's
 *       declared modifier, so a bare {@code P} never opens the screen - matched here.</li>
 *   <li>Upstream has no close-on-key for this screen; ESC closes it, as in vanilla.</li>
 * </ul>
 */
public class ExtraPlayerRenderKey {

    /** Upstream default: KEYSYM 80 ({@code P}) with {@code KeyModifier.ALT}. */
    public static final KeyBinding KEY_MAPPING = new KeyBinding(
            "key.yes_steve_model.open_extra_player_render.desc", InputMappings.Type.KEYSYM, 80,
            "key.category.yes_steve_model");

    public static final InputUtil.Modifier TOGGLE_MODIFIER = InputUtil.Modifier.ALT;

    private ExtraPlayerRenderKey() {
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
        minecraft.displayGuiScreen(new ExtraPlayerRenderScreen());
        return true;
    }
}
