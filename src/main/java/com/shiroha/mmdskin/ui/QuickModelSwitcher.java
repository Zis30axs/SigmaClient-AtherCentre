package com.shiroha.mmdskin.ui;

import com.shiroha.mmdskin.ui.selector.ModelSelectorServices;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;

/**
 * 蹇嵎妯″瀷鍒囨崲鍣? * 澶勭悊蹇嵎閿Е鍙戠殑妯″瀷鍒囨崲閫昏緫锛岀敱骞冲彴灞傦紙Fabric/Forge锛夊湪鎸夐敭浜嬩欢涓皟鐢ㄣ€? */
public final class QuickModelSwitcher {
    private static final ModelSelectionApplicationService SERVICE = ModelSelectorServices.modelSelection();

    private QuickModelSwitcher() {}

    public static void switchToSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ModelSelectionApplicationService.QuickSwitchResult result = SERVICE.switchToSlot(slot);
        switch (result.status()) {
            case UNBOUND -> mc.ingameGUI.getChatGUI().printChatMessage(
                    new TranslationTextComponent("message.mmdskin.quick_model.unbound", slot + 1));
            case RESET_TO_DEFAULT -> mc.ingameGUI.getChatGUI().printChatMessage(
                    new TranslationTextComponent("message.mmdskin.quick_model.reset"));
            case SWITCHED -> mc.ingameGUI.getChatGUI().printChatMessage(
                    new TranslationTextComponent("message.mmdskin.quick_model.switched", result.targetModelName()));
        }
    }
}
