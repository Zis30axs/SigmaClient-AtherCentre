package com.elfmcys.yesstevemodel.client.gui.button;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.List;

/**
 * Port of upstream {@code client/gui/button/FlatColorButton} (1.20.1).
 *
 * <p>Translation notes:
 * <ul>
 *   <li>{@code renderWidget(GuiGraphics,...)} -> {@code renderButton(MatrixStack,...)}.</li>
 *   <li>{@code getX()/getY()} -> the public {@code x}/{@code y} fields.</li>
 *   <li>{@code guiGraphics.fillGradient} -> the inherited {@code AbstractGui#fillGradient}.</li>
 *   <li>{@code renderScrollingString} does not exist in 1.16.5; the label is drawn centred and
 *       trimmed to the button width instead (same visual intent, no horizontal scroll).</li>
 *   <li>{@code isHoveredOrFocused()} -> {@code isHovered() || isFocused()}.</li>
 * </ul>
 */
public class FlatColorButton extends Button {

    private boolean selected;

    private List<IReorderingProcessor> tooltip;

    public FlatColorButton(int x, int y, int width, int height, ITextComponent title, Button.IPressable pressedAction) {
        super(x, y, width, height, title, pressedAction);
        this.selected = false;
    }

    public FlatColorButton setTooltipText(String key) {
        // Upstream's Tooltip wraps at 170px when drawn; wrap now, since 1.16.5 tooltips never wrap.
        this.tooltip = Minecraft.getInstance().fontRenderer
                .trimStringToWidth(new TranslationTextComponent(key), 170);
        return this;
    }

    public FlatColorButton setTooltipLines(List<IReorderingProcessor> lines) {
        this.tooltip = lines;
        return this;
    }

    public List<IReorderingProcessor> getTooltipLines() {
        return this.tooltip;
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        if (this.selected) {
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -14774017, -14774017);
        } else {
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -12369342, -12369342);
        }
        if (this.isHovered() || this.isFocused()) {
            this.fillGradient(matrixStack, this.x, this.y + 1, this.x + 1, (this.y + this.height) - 1, -790560, -790560);
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + 1, -790560, -790560);
            this.fillGradient(matrixStack, (this.x + this.width) - 1, this.y + 1, this.x + this.width, (this.y + this.height) - 1, -790560, -790560);
            this.fillGradient(matrixStack, this.x, (this.y + this.height) - 1, this.x + this.width, this.y + this.height, -790560, -790560);
        }
        drawLabel(matrixStack, font);
    }

    /** Stand-in for upstream's {@code renderScrollingString}: centre, trimmed to fit. */
    private void drawLabel(MatrixStack matrixStack, FontRenderer font) {
        ITextComponent message = this.getMessage();
        int inner = this.width - 4;
        if (inner <= 0) {
            return;
        }
        String text = message.getString();
        if (font.getStringWidth(text) > inner) {
            text = font.func_238412_a_(text, inner);
        }
        drawCenteredString(matrixStack, font, text, this.x + (this.width / 2),
                this.y + ((this.height - 8) / 2), 15986656);
    }

    /**
     * Upstream's {@code renderTooltip(GuiGraphics, Screen, int, int)}: draws the wrapped tooltip
     * when hovered. 1.16.5's {@code Screen#renderTooltip} handles the z-offset internally, so the
     * manual {@code translate(0, 0, 4000)} upstream performs is unnecessary.
     */
    public void renderTooltip(MatrixStack matrixStack, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered() && this.tooltip != null && !this.tooltip.isEmpty()) {
            screen.renderTooltip(matrixStack, this.tooltip, mouseX, mouseY);
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return this.selected;
    }
}
