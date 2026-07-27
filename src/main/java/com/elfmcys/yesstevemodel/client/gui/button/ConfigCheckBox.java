package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.ISpecialWidget;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.ToggleWidget;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port of upstream {@code client/gui/button/ConfigCheckBox} (1.20.1).
 *
 * <p>Translation notes: {@code StateSwitchingButton} -> 1.16.5
 * {@code net.minecraft.client.gui.widget.ToggleWidget} (same {@code initTextureValues} /
 * {@code stateTriggered} / {@code setStateTriggered} surface, and its {@code renderButton} does the
 * same two-axis texture offset). {@code renderWidget} -> {@code renderButton}.
 *
 * <p>{@code ToggleWidget} has no {@code onClick} of its own and {@code Widget#mouseClicked} does not
 * route to one, so the click handling is implemented here directly.
 */
public class ConfigCheckBox extends ToggleWidget implements ISpecialWidget {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(YesSteveModel.MOD_ID, "texture/roulette.png");

    private final Consumer<Boolean> onToggle;

    private final ITextComponent label;

    private List<IReorderingProcessor> tooltip;

    public ConfigCheckBox(int x, int y, int width, ITextComponent label, Consumer<Boolean> onToggle) {
        super(x, y, width, 12, false);
        this.label = label;
        this.onToggle = onToggle;
        initTextureValues(0, 0, 128, 12, TEXTURE);
    }

    public ConfigCheckBox(int x, int y, ITextComponent label, Consumer<Boolean> onToggle) {
        this(x, y, 115, label, onToggle);
    }

    public ConfigCheckBox setTooltipLines(List<IReorderingProcessor> lines) {
        this.tooltip = lines;
        return this;
    }

    public List<IReorderingProcessor> getTooltipLines() {
        return this.tooltip;
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        font.func_243248_b(matrixStack, this.label, this.x + 14, this.y + 2, -1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundHandler());
        this.stateTriggered = !this.stateTriggered;
        this.onToggle.accept(Boolean.valueOf(this.stateTriggered));
        return true;
    }
}
