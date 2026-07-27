package com.elfmcys.yesstevemodel.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.settings.KeyBinding;

public class InputUtil {

    /**
     * Stand-in for Forge's {@code KeyModifier}. 1.16.5 {@link KeyBinding} has no modifier concept,
     * so bindings that upstream declares with a modifier (the roulette lock key is
     * {@code KeyModifier.ALT} + {@code L}) carry it alongside the binding instead.
     */
    public enum Modifier {
        NONE,
        CONTROL,
        SHIFT,
        ALT
    }

    /**
     * Forge {@code KeyModifier.getActiveModifier()}: first active in the order CONTROL, SHIFT, ALT,
     * else NONE. Reproducing the precedence matters — it is what makes a {@code NONE} binding
     * refuse to fire while any modifier is held.
     */
    public static Modifier activeModifier() {
        if (Screen.hasControlDown()) {
            return Modifier.CONTROL;
        }
        if (Screen.hasShiftDown()) {
            return Modifier.SHIFT;
        }
        if (Screen.hasAltDown()) {
            return Modifier.ALT;
        }
        return Modifier.NONE;
    }

    /**
     * Upstream {@code isKeyPressed(InputEvent.Key, KeyMapping)}. There is no Forge input event here,
     * so the raw GLFW pair from {@code KeyboardListener#onKeyEvent} is passed straight through;
     * {@code KeyMapping#matches} is 1.16.5's {@code KeyBinding#matchesKey}.
     */
    public static boolean isKeyPressed(int keysym, int scancode, KeyBinding keyBinding) {
        return isKeyPressed(keysym, scancode, keyBinding, Modifier.NONE);
    }

    public static boolean isKeyPressed(int keysym, int scancode, KeyBinding keyBinding, Modifier required) {
        return keyBinding.matchesKey(keysym, scancode) && required == activeModifier();
    }

    public static boolean isPlayerReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.loadingGui != null || minecraft.currentScreen != null || !minecraft.mouseHelper.isMouseGrabbed()) {
            return false;
        }
        return minecraft.isGameFocused();
    }
}
