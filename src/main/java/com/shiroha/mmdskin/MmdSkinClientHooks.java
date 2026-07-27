package com.shiroha.mmdskin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.debug.client.PerformanceHud;
import com.shiroha.mmdskin.player.runtime.MmdSkinRendererPlayerHelper;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.ui.QuickModelSwitcher;
import com.shiroha.mmdskin.ui.wheel.ConfigWheelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * 1.16.5 直移植说明：替代原 Fabric/Forge 平台注册层（FabricClientRuntimeHooks 等），
 * 由反编译源码直接调用：键位注册进 GameSettings、客户端 tick、HUD、断线清理。
 * Maid/舞台/网络同步相关逻辑已随纯客户端化裁剪。
 */
public final class MmdSkinClientHooks {

    public static final KeyBinding KEY_CONFIG_WHEEL =
            new KeyBinding("key.mmdskin.config_wheel", GLFW.GLFW_KEY_LEFT_ALT, "key.categories.mmdskin");

    public static final KeyBinding[] KEY_QUICK_MODELS = new KeyBinding[4];

    static {
        for (int i = 0; i < KEY_QUICK_MODELS.length; i++) {
            KEY_QUICK_MODELS[i] = new KeyBinding("key.mmdskin.quick_model_" + (i + 1),
                    GLFW.GLFW_KEY_UNKNOWN, "key.categories.mmdskin");
        }
    }

    private static boolean configWheelKeyWasDown;

    private MmdSkinClientHooks() {
    }

    /** 追加进 GameSettings.keyBindings 的全部键位。 */
    public static KeyBinding[] keyBindings() {
        KeyBinding[] all = new KeyBinding[1 + KEY_QUICK_MODELS.length];
        all[0] = KEY_CONFIG_WHEEL;
        System.arraycopy(KEY_QUICK_MODELS, 0, all, 1, KEY_QUICK_MODELS.length);
        return all;
    }

    /** 每客户端 tick 调用（Minecraft.runTick 注入点）。 */
    public static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null) {
            configWheelKeyWasDown = false;
            return;
        }

        try {
            ClientRenderRuntime.get().modelRepository().tick();
        } catch (IllegalStateException ignored) {
            // 渲染运行时尚未初始化时静默跳过
        }

        if (minecraft.currentScreen == null || minecraft.currentScreen instanceof ConfigWheelScreen) {
            boolean keyDown = KEY_CONFIG_WHEEL.isKeyDown();
            if (keyDown && !configWheelKeyWasDown && minecraft.currentScreen == null) {
                minecraft.displayGuiScreen(new ConfigWheelScreen(KEY_CONFIG_WHEEL));
            }
            configWheelKeyWasDown = keyDown;
        } else {
            configWheelKeyWasDown = false;
        }

        if (minecraft.currentScreen == null) {
            for (int i = 0; i < KEY_QUICK_MODELS.length; i++) {
                while (KEY_QUICK_MODELS[i].isPressed()) {
                    QuickModelSwitcher.switchToSlot(i);
                }
            }
        }
    }

    /** HUD 渲染（IngameGui.renderIngameGui 注入点）。内部有 debug 开关门控。 */
    public static void renderHud(MatrixStack matrixStack) {
        PerformanceHud.render(matrixStack);
    }

    /** 离开世界/断线清理（Minecraft.unloadWorld 注入点）。 */
    public static void onDisconnect() {
        PlayerModelSyncService.onDisconnect();
        MmdSkinRendererPlayerHelper.onDisconnect();
    }

    private static long lastRenderDebugLog;

    /** 渲染接管诊断（节流 2s）：打出本地玩家的选中模型与本帧决策。 */
    public static void debugRenderDecision(String playerName, Object action) {
        long now = System.currentTimeMillis();
        if (now - lastRenderDebugLog > 2000) {
            lastRenderDebugLog = now;
            String selected = com.shiroha.mmdskin.ui.config.ModelSelectorConfig.getInstance().getPlayerModel(playerName);
            org.apache.logging.log4j.LogManager.getLogger().info(
                    "[mmdskin] render decision: player={} selected={} action={}", playerName, selected, action);
        }
    }
}
