package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.CheckboxButton;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

/**
 * Port of upstream {@code client/gui/DisclaimerScreen} (1.20.1): one-time disclaimer shown before
 * the first opening of the model picker; ticking the box opens the picker and never shows it
 * again.
 *
 * <p>Translation notes: {@code Checkbox} -> {@link CheckboxButton}; {@code Button.Builder} ->
 * the plain {@link Button} constructor; {@code guiGraphics.drawWordWrap} ->
 * {@code FontRenderer#func_238418_a_} (split string draw). Widget bookkeeping follows the
 * {@code AnimationRouletteScreen} idiom ({@code renderList} + {@code addListener}).
 */
public class DisclaimerScreen extends Screen {

    private final List<Widget> renderList = Lists.newArrayList();

    private CheckboxButton checkbox;

    private int textX;

    private int textY;

    public DisclaimerScreen() {
        super(new StringTextComponent("Disclaimer GUI"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int lineCount = this.font.trimStringToWidth(new TranslationTextComponent("gui.yes_steve_model.disclaimer.text"),
                400).size();
        int contentHeight = (lineCount * 9) + 20 + 20 + 10 + 20;
        this.textX = (this.width - 400) / 2;
        this.textY = (this.height - contentHeight) / 2;
        ITextComponent readLabel = new TranslationTextComponent("gui.yes_steve_model.disclaimer.read");
        int labelWidth = this.font.getStringPropertyWidth(readLabel);
        this.checkbox = new CheckboxButton((this.width - labelWidth) / 2, (this.textY + contentHeight) - 50,
                labelWidth, 20, readLabel, !GeneralConfig.isDisclaimerShow());
        addRenderableWidget(this.checkbox);
        addRenderableWidget(new Button((this.width - 300) / 2, (this.textY + contentHeight) - 20, 300, 20,
                new TranslationTextComponent("gui.yes_steve_model.disclaimer.close"), button -> {
            if (this.checkbox.isChecked()) {
                GeneralConfig.setDisclaimerShow(false);
                Minecraft.getInstance().displayGuiScreen(new PlayerModelScreen());
            } else {
                Minecraft.getInstance().displayGuiScreen(null);
            }
        }));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.font.func_238418_a_(new TranslationTextComponent("gui.yes_steve_model.disclaimer.text"), this.textX,
                this.textY, 400, -1);
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
