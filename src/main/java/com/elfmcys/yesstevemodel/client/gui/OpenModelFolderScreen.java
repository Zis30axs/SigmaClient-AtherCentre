package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

/**
 * Port of upstream {@code client/gui/OpenModelFolderScreen} (1.20.1): explains the custom-model
 * folder and offers a button that opens it in the OS file manager.
 *
 * <p>Translation notes: upstream opens {@code ServerModelManager.CUSTOM}; the local custom-model
 * folder is {@code <gamedir>/config/yes_steve_model/custom} (see {@code OpenYsmModelIndex}).
 * {@code Util.getPlatform().openFile} -> {@code Util.getOSType().openFile}.
 */
public class OpenModelFolderScreen extends Screen {

    private final List<Widget> renderList = Lists.newArrayList();

    private final PlayerModelScreen parentScreen;

    public OpenModelFolderScreen(PlayerModelScreen parentScreen) {
        super(new StringTextComponent("Open Model Folder"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = (this.width - 310) / 2;
        int y = (this.height / 2) + 60;
        addRenderableWidget(new Button(x, y, 150, 20,
                new TranslationTextComponent("gui.yes_steve_model.open_model_folder.open"), button ->
                Util.getOSType().openFile(YesSteveModel.getConfigDirectory().resolve("custom").toFile())));
        addRenderableWidget(new Button(x + 160, y, 150, 20,
                new TranslationTextComponent("gui.yes_steve_model.model.return"), button ->
                this.minecraft.displayGuiScreen(this.parentScreen)));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.font.func_238418_a_(new TranslationTextComponent("gui.yes_steve_model.open_model_folder.tips"),
                (this.width - 400) / 2, (this.height / 2) - 80, 400, 16777215);
        for (Widget widget : this.renderList) {
            widget.render(matrixStack, mouseX, mouseY, partialTicks);
        }
    }

    private void clearWidgets() {
        this.renderList.clear();
        this.children.clear();
    }

    private <T extends Widget> T addRenderableWidget(T widget) {
        this.renderList.add(widget);
        addListener(widget);
        return widget;
    }
}
