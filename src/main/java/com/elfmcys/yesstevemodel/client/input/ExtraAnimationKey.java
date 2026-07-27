package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.event.AnimationLockEvent;
import com.elfmcys.yesstevemodel.client.gui.AnimationRouletteScreen;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.util.InputUtil;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;

import java.util.List;

/**
 * Port of upstream {@code client/input/ExtraAnimationKey} (1.20.1) — eight unbound hotkeys that fire
 * roulette slot 0..7 directly, without opening the wheel.
 *
 * <p>Unbound by default (upstream keycode {@code -1} -> {@code InputMappings.INPUT_INVALID}), and the
 * roulette draws each slot's current binding via
 * {@code AnimationRouletteScreen#renderKeyBindings}, so an unbound slot reads
 * {@code key.yes_steve_model.extra_animation.none}.
 *
 * <p>Deviation forced by the server-sync cut: upstream's plain-animation branch is
 * {@code NetworkHandler.sendToServer(new C2SPlayAnimationPacket(index, ""))} and the model switch
 * comes back from the server. {@code isClientConnected()} is permanently {@code false} here, so the
 * local terminus is used instead — {@code PlayerCapability#requestModelSwitch(String)}, exactly what
 * upstream's own offline branch in {@code AnimationRouletteScreen#playAnimation} does.
 */
public class ExtraAnimationKey {

    public static final List<KeyBinding> KEY_MAPPINGS = Lists.newArrayList();

    private ExtraAnimationKey() {
    }

    /** Upstream {@code registerKeyMappings(RegisterKeyMappingsEvent)}. */
    public static KeyBinding[] register() {
        if (KEY_MAPPINGS.isEmpty()) {
            for (int i = 0; i <= 7; i++) {
                KEY_MAPPINGS.add(new KeyBinding(
                        String.format("key.yes_steve_model.extra_animation.%d.desc", Integer.valueOf(i)),
                        InputMappings.Type.KEYSYM, InputMappings.INPUT_INVALID.getKeyCode(),
                        "key.category.yes_steve_model"));
            }
        }
        return KEY_MAPPINGS.toArray(new KeyBinding[0]);
    }

    public static boolean onKeyInput(int keysym, int scancode, int action) {
        if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady() || action != 1) {
            return false;
        }
        ClientPlayerEntity localPlayer = Minecraft.getInstance().player;
        for (KeyBinding keyBinding : KEY_MAPPINGS) {
            if (!InputUtil.isKeyPressed(keysym, scancode, keyBinding) || localPlayer == null
                    || AnimationLockEvent.isPlayerMoving(localPlayer)) {
                continue;
            }
            localPlayer.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
                ModelAssembly modelAssembly = cap.getModelAssembly();
                if (modelAssembly == null) {
                    return;
                }
                int index = KEY_MAPPINGS.indexOf(keyBinding);
                ModelProperties modelProperties = modelAssembly.getModelData().getModelProperties();
                OrderedStringMap<String, String> map = modelProperties.getExtraAnimation();
                if (map.size() <= index) {
                    return;
                }
                String rouletteKey = map.getKeyAt(index);
                if ("#return".equals(rouletteKey)) {
                    // Upstream: C2SPlayAnimationPacket.createDefault(), and the reset arrives back
                    // from the server. Offline that reduces to dropping the pending switch, which is
                    // what AnimationLockEvent's tick half does when the player starts moving.
                    cap.clearModelSwitch();
                    return;
                }
                if (rouletteKey.startsWith("#")
                        && modelProperties.getExtraAnimationClassify().containsKey(rouletteKey.substring(1))) {
                    AnimationRouletteScreen.setInitialSubmenu(rouletteKey.substring(1));
                    Minecraft.getInstance().displayGuiScreen(new AnimationRouletteScreen(
                            modelProperties.getExtraAnimationButtons(), modelProperties.getExtraAnimationClassify(),
                            modelAssembly, cap));
                    return;
                }
                // Upstream: NetworkHandler.sendToServer(new C2SPlayAnimationPacket(index, StringPool.EMPTY)).
                // Offline terminus instead — same one upstream's own no-server branch uses.
                cap.requestModelSwitch(rouletteKey);
            });
            return true;
        }
        return false;
    }
}
