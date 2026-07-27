package com.shiroha.mmdskin.ui.wheel;

import net.minecraft.client.util.InputMappings;
import com.shiroha.mmdskin.ui.selector.MaterialVisibilityScreen;
import com.shiroha.mmdskin.ui.selector.ModelSelectorScreen;
import com.shiroha.mmdskin.ui.selector.SceneSelectorScreen;
import com.shiroha.mmdskin.util.KeyMappingUtil;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 涓婚厤缃疆鐩樼晫闈€?*/
public class ConfigWheelScreen extends AbstractWheelScreen {
    private static final WheelStyle STYLE = createTranslucentWheelStyle(0.50f, 0.30f);

    private final List<ConfigSlot> configSlots;
    private final KeyBinding monitoredKey;
    private static Supplier<Screen> modSettingsScreenFactory;

    public ConfigWheelScreen(KeyBinding keyMapping) {
        super(new TranslationTextComponent("gui.mmdskin.config_wheel"), STYLE);
        this.monitoredKey = keyMapping;
        this.configSlots = new ArrayList<>();
        initConfigSlots();
    }

    public static void setModSettingsScreenFactory(Supplier<Screen> factory) {
        modSettingsScreenFactory = factory;
    }

    private void initConfigSlots() {
        configSlots.add(new ConfigSlot("model",
                new TranslationTextComponent("gui.mmdskin.config.model_switch").getString(),
                "model", this::openModelSelector));
        configSlots.add(new ConfigSlot("action",
                new TranslationTextComponent("gui.mmdskin.config.action_select").getString(),
                "action", this::openActionWheel));
        configSlots.add(new ConfigSlot("morph",
                new TranslationTextComponent("gui.mmdskin.config.morph_select").getString(),
                "morph", this::openMorphWheel));
        configSlots.add(new ConfigSlot("material",
                new TranslationTextComponent("gui.mmdskin.config.material_control").getString(),
                "mat", this::openMaterialVisibility));
        configSlots.add(new ConfigSlot("scene",
                new TranslationTextComponent("gui.mmdskin.config.scene_mode").getString(),
                "scene", this::openSceneSelector));
        configSlots.add(new ConfigSlot("settings",
                new TranslationTextComponent("gui.mmdskin.config.mod_settings").getString(),
                "cfg", this::openModSettings));
    }

    @Override
    protected int getSlotCount() {
        return configSlots.size();
    }

    @Override
    protected void init() {
        super.init();
        initWheelLayout();
    }

    @Override
    public void render(MatrixStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderWheelBase(guiGraphics, mouseX, mouseY, partialTick, buildEntries());

        String centerText = selectedSlot >= 0 ? configSlots.get(selectedSlot).name : "MMD Skin";
        renderCenterBubble(guiGraphics, centerText, style.lineColor());

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        if (Minecraft.getInstance().currentScreen != this) {
            return;
        }

        if (monitoredKey != null) {
            // 1.16.5 直移植说明：Screen 打开后按键事件不再路由给 KeyBinding，
            // isKeyDown() 状态不可靠——只信 GLFW 实时轮询。
            boolean isDown = false;
            InputMappings.Input key = KeyMappingUtil.getBoundKey(monitoredKey);
            if (key != null
                    && key.getType() == InputMappings.Type.KEYSYM
                    && key.getKeyCode() != -1) {
                long window = Minecraft.getInstance().getMainWindow().getHandle();
                isDown = GLFW.glfwGetKey(window, key.getKeyCode()) == GLFW.GLFW_PRESS;
            }

            if (!isDown) {
                if (selectedSlot >= 0 && selectedSlot < configSlots.size()) {
                    ConfigSlot slot = configSlots.get(selectedSlot);
                    this.closeScreen();
                    slot.action.run();
                } else {
                    this.closeScreen();
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 兜底交互：左键点击也可确认选择并关闭轮盘。
        if (button == 0) {
            if (selectedSlot >= 0 && selectedSlot < configSlots.size()) {
                ConfigSlot slot = configSlots.get(selectedSlot);
                this.closeScreen();
                slot.action.run();
            } else {
                this.closeScreen();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private List<WheelEntry> buildEntries() {
        List<WheelEntry> entries = new ArrayList<>(configSlots.size());
        for (ConfigSlot slot : configSlots) {
            entries.add(new WheelEntry(slot.name, null));
        }
        return entries;
    }

    private void openModelSelector() {
        Minecraft.getInstance().displayGuiScreen(new ModelSelectorScreen());
    }

    private void openActionWheel() {
        Minecraft.getInstance().displayGuiScreen(new ActionWheelScreen());
    }

    private void openMorphWheel() {
        Minecraft.getInstance().displayGuiScreen(new MorphWheelScreen(monitoredKey));
    }

    private void openMaterialVisibility() {
        MaterialVisibilityScreen screen = MaterialVisibilityScreen.createForPlayer();
        if (screen != null) {
            Minecraft.getInstance().displayGuiScreen(screen);
        } else {
            Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(
                    new TranslationTextComponent("message.mmdskin.player.model_not_found"));
        }
    }

    private void openSceneSelector() {
        Minecraft.getInstance().displayGuiScreen(new SceneSelectorScreen());
    }

    private void openModSettings() {
        if (modSettingsScreenFactory != null) {
            Screen settingsScreen = modSettingsScreenFactory.get();
            if (settingsScreen != null) {
                Minecraft.getInstance().displayGuiScreen(settingsScreen);
                return;
            }
        }
        Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(
                new TranslationTextComponent("message.mmdskin.mod_settings.not_initialized"));
    }

    private static class ConfigSlot {
        @SuppressWarnings("unused")
        final String id;
        final String name;
        final String icon;
        final Runnable action;

        ConfigSlot(String id, String name, String icon, Runnable action) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.action = action;
        }
    }
}
