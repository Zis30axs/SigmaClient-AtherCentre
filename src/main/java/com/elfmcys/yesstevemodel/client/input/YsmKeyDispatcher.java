package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.event.AnimationLockEvent;
import net.minecraft.client.settings.KeyBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Replaces Forge's client key event bus. Upstream spreads key handling across four
 * {@code @SubscribeEvent onKeyInput(InputEvent.Key)} methods ({@link AnimationRouletteKey},
 * {@link ExtraAnimationKey}, {@link PlayerModelToggleKey}, {@link ExtraPlayerRenderKey}); with no bus, they are called from a single
 * seam in {@code net.minecraft.client.KeyboardListener#onKeyEvent}.
 *
 * <p>The seam sits at the exact position where Forge fires {@code InputEvent.Key} — the very last
 * statement of {@code onKeyEvent}, after the screen dispatch and the {@code KeyBinding} state
 * updates — and is <em>fire-and-continue</em>: it never consumes the key. That ordering is what
 * makes the port behave like upstream:
 * <ul>
 *   <li>a key shared with a vanilla binding (the lock key defaults to {@code L}, and so does
 *       {@code keyBindAdvancements}) triggers both, because vanilla's {@code KeyBinding.onTick} has
 *       already run before this fires;</li>
 *   <li>while a screen is open, that screen sees the key first; if it consumes it (the roulette
 *       swallows its own hotkey so the opening press cannot bounce it shut), this never runs;</li>
 *   <li>the Controls screen can rebind any of these keys, because a rebind click is consumed by the
 *       screen and never reaches here.</li>
 * </ul>
 *
 * <p>{@link #registerAll()} is called from the {@code GameSettings} constructor so the bindings are
 * in {@code keyBindings} before {@code loadOptions()} reads {@code options.txt} — the same ordering
 * OptiFine's {@code ofKeyBindZoom} relies on.
 */
public final class YsmKeyDispatcher {

    private static boolean registered;

    private YsmKeyDispatcher() {
    }

    /** All YSM bindings, in the order they should appear in the Controls screen. */
    public static KeyBinding[] registerAll() {
        List<KeyBinding> all = new ArrayList<>();
        Collections.addAll(all, AnimationRouletteKey.register());
        Collections.addAll(all, ExtraAnimationKey.register());
        Collections.addAll(all, PlayerModelToggleKey.register());
        Collections.addAll(all, ExtraPlayerRenderKey.register());
        registered = true;
        return all.toArray(new KeyBinding[0]);
    }

    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Forge {@code InputEvent.Key} equivalent. Never consumes — see the class javadoc for why.
     *
     * @param action GLFW action; {@code 1} is press, which is all upstream reacts to.
     */
    public static void fireKeyInput(int keysym, int scancode, int action) {
        if (!registered || !YesSteveModel.isAvailable()) {
            return;
        }
        // AnimationLockEvent's key half: upstream gates it only on isAvailable() and the press
        // action, deliberately without isPlayerReady(), so the lock can be toggled from a GUI.
        if (action == 1 && AnimationRouletteKey.isLockKey(keysym, scancode)) {
            AnimationLockEvent.onKeyInput(keysym, scancode);
        }
        AnimationRouletteKey.onKeyInput(keysym, scancode, action);
        ExtraAnimationKey.onKeyInput(keysym, scancode, action);
        PlayerModelToggleKey.onKeyInput(keysym, scancode, action);
        ExtraPlayerRenderKey.onKeyInput(keysym, scancode, action);
    }
}
