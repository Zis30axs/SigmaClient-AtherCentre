package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.client.gui.ISpecialWidget;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Port of upstream {@code client/gui/button/FlatIconButton} (1.20.1) — the section header above a
 * radio group in the roulette's config column. Not clickable; it exists to draw a title bar.
 *
 * <p>Upstream's third ctor argument is the *height of the filled bar* (it names the field
 * {@code iconIndex}, but it is used as {@code getY() + iconIndex} in the fill), while the widget's
 * own height stays 115x15. Kept verbatim.
 *
 * <p>Translation notes: {@code AbstractWidget} -> {@code Widget}; {@code renderWidget} ->
 * {@code renderButton}; {@code updateWidgetNarration} has no 1.16.5 counterpart and is dropped.
 * Hit-testing is left at {@code Widget}'s default (the 115x15 widget rect, <em>not</em> the taller
 * filled bar) because that is what upstream's tooltip triggers on; the widget stays unclickable by
 * virtue of being registered render-only, exactly as upstream's {@code addRenderableOnly} does.
 */
public class FlatIconButton extends Widget implements ISpecialWidget {

    private final int barHeight;

    private List<IReorderingProcessor> tooltip;

    public FlatIconButton(int x, int y, int barHeight, ITextComponent title) {
        super(x, y, 115, 15, title);
        this.barHeight = barHeight;
    }

    public FlatIconButton setTooltipLines(List<IReorderingProcessor> lines) {
        this.tooltip = lines;
        return this;
    }

    public List<IReorderingProcessor> getTooltipLines() {
        return this.tooltip;
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        fill(matrixStack, this.x, this.y, this.x + this.getWidth(), this.y + this.barHeight, -280804798);
        String text = this.getMessage().getString();
        int inner = this.getWidth() - 4;
        if (inner > 0) {
            if (font.getStringWidth(text) > inner) {
                text = font.func_238412_a_(text, inner);
            }
            drawCenteredString(matrixStack, font, text, this.x + (this.getWidth() / 2),
                    this.y + ((this.height - 9) / 2) + 1, 16777215);
        }
    }

}
