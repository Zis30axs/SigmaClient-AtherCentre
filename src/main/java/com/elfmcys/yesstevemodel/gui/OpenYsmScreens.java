package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import net.minecraft.client.Minecraft;

public final class OpenYsmScreens {
    private OpenYsmScreens() {
    }

    /**
     * The "YSM" module's model-manager entry (Sigma GUI button and {@code /ysm} command). It now
     * opens the ported upstream {@code PlayerModelScreen} (with the one-time disclaimer gate) via
     * {@link PlayerModelToggleKey#openModelScreen()}, replacing the self-authored
     * {@code OpenYsmModelSelectionScreen} - same retarget as {@link #openActionWheel(int)} did for
     * the roulette.
     */
    public static void openModelManager() {
        PlayerModelToggleKey.openModelScreen();
    }

    public static void openActionWheel() {
        openActionWheel(-1);
    }

    /**
     * The "YSM Actions" module keybind lands here, via
     * {@code ModuleKeyPress.press -> YsmActionsGUI.openWhilePressing}. It now opens the ported upstream
     * {@code AnimationRouletteScreen} instead of {@code OpenYsmActionWheelScreen}: the old wheel wrote
     * into {@code capability/OpenYsmPlayerAnimationState}, a store only the retired render chain read,
     * which is exactly why picking an animation on it never made the model move.
     *
     * <p>{@code heldKey} is the key that triggered the open. The new roulette keeps it in order to
     * <em>swallow</em> it — the same press is re-dispatched to {@code mc.currentScreen} later in
     * {@code KeyboardListener#onKeyEvent}, so a wheel that closed on its own hotkey would flash open
     * and shut.
     */
    public static void openActionWheel(int heldKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        // Already showing a roulette: do nothing. The hotkey must not close or re-open it.
        if (AnimationRouletteKey.isRouletteOpen()) {
            return;
        }
        if (minecraft.player != null) {
            PlayerCapability cap =
                    minecraft.player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).orElse(null);
            if (cap != null) {
                AnimationRouletteKey.openRoulette(cap.getModelId(), cap.getModelAssembly(),
                        cap, heldKey);
            }
        }
    }

    /**
     * The paper-doll overlay config entry ({@code .ysm paperdoll}). It now opens the ported
     * upstream {@code client/gui/ExtraPlayerRenderScreen} - same retarget as
     * {@link #openModelManager()} did for the picker. Both screens edit the same
     * {@code OpenYsmClientConfig} extra-player fields (the new one through the
     * {@code ExtraPlayerRenderConfig} facade), so behaviour is unchanged.
     */
    public static void openExtraPlayerRender() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.displayGuiScreen(
                    new com.elfmcys.yesstevemodel.client.gui.ExtraPlayerRenderScreen());
        }
    }
}
