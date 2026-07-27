package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.GuiBlitCompat;
import com.elfmcys.yesstevemodel.client.gui.ISpecialWidget;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.molang.parser.ParseException;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.AbstractSlider;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import java.text.DecimalFormat;
import java.util.List;

/**
 * Port of upstream {@code client/gui/button/AnimationSlider} (1.20.1).
 *
 * <p>Upstream extends Forge's {@code ForgeSlider}, which does not exist here, so its value model is
 * reimplemented on top of vanilla {@code AbstractSlider} (which only knows a normalised 0..1
 * {@code sliderValue}), keeping ForgeSlider's invariants rather than the obvious ones:
 * <ul>
 *   <li>The stored {@code sliderValue} is always <em>snapped</em>: {@link #setValue} runs
 *       {@code snapToNearest} (snap in absolute units on a grid anchored at zero, then clamp to
 *       [min,max], then renormalise) before storing, exactly like {@code ForgeSlider#setValue}.
 *       Because of that the handle blit, the label and {@link #getValue()} can never disagree.</li>
 *   <li>{@code getValue()} is a plain denormalisation — no second snap.</li>
 *   <li>{@code func_230972_a_()} (apply value) is upstream's {@code applyValue()}.</li>
 *   <li>{@code func_230979_b_()} (update message) is {@code ForgeSlider#updateMessage} with
 *       {@code drawString=true}: prefix + value with <em>no</em> separator — YSM config titles carry
 *       their own (e.g. {@code "睁眼幅度: "}), and appending ": " would double it. The prefix is
 *       appended as a component, not flattened through {@code getString()}, so its style survives.</li>
 * </ul>
 *
 * <p>The texture bands live at v=24/44/64/84 of {@code texture/roulette.png} (200x15 each), which is
 * exactly vanilla 1.20's {@code getTextureY()}/{@code getHandleTextureY()} (0/20, 40/60) plus the
 * +24 offset upstream applies. The band predicates match vanilla 1.20 too: the track keys off
 * {@code isFocused()} alone and the handle off hover-or-focus with no {@code active} gate — 1.16.5's
 * {@code Widget#isHovered()} already folds focus in, so the expressions stay equivalent.
 * {@code blitWithBorder} is provided by {@link GuiBlitCompat}.
 *
 * <p>Label placement follows 1.20's {@code renderScrollingString}: vertical centre at
 * {@code (top + bottom - 9) / 2 + 1} (y+4 for a 15px widget) and the active foreground colour
 * 0xFFFFFF, not vanilla Button's grey at y+3.
 *
 * <p>Cut, per the project's server-sync exclusion: upstream also mirrors the assignment to the
 * server via {@code C2SRequestExecuteMolangPacket}. Only the local
 * {@code AnimatableEntity#executeExpression} call is kept.
 */
public class AnimationSlider extends AbstractSlider implements ISpecialWidget {

    private static final ResourceLocation ROULETTE_TEXTURE =
            new ResourceLocation(YesSteveModel.MOD_ID, "texture/roulette.png");

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    private final AnimatableEntity<?> model;

    private final String controllerName;

    private final ITextComponent prefix;

    private final double minValue;

    private final double maxValue;

    private final double stepSize;

    private List<IReorderingProcessor> tooltip;

    public AnimationSlider(int x, int y, ITextComponent prefix, double currentValue, AnimatableEntity<?> model,
                           String controllerName, double stepSize, double minValue, double maxValue) {
        super(x, y, 115, 15, StringTextComponent.EMPTY, 0.0D);
        this.model = model;
        this.controllerName = controllerName;
        this.prefix = prefix;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.stepSize = Math.abs(stepSize);
        setValue(currentValue);
    }

    public AnimationSlider setTooltipLines(List<IReorderingProcessor> lines) {
        this.tooltip = lines;
        return this;
    }

    public List<IReorderingProcessor> getTooltipLines() {
        return this.tooltip;
    }

    /**
     * {@code ForgeSlider#setValue}: snap in absolute units, clamp, renormalise, apply, refresh the
     * label. The stored {@code sliderValue} always corresponds to an on-grid value.
     */
    public void setValue(double value) {
        this.sliderValue = this.maxValue > this.minValue
                ? (snapToNearest(value) - this.minValue) / (this.maxValue - this.minValue)
                : 0.0D;
        func_230972_a_();
        func_230979_b_();
    }

    /** {@code ForgeSlider#snapToNearest}: grid anchored at zero, then clamped to [min,max]. */
    private double snapToNearest(double value) {
        if (this.stepSize <= 0.0D) {
            return MathHelper.clamp(value, this.minValue, this.maxValue);
        }
        value = this.stepSize * Math.round(value / this.stepSize);
        return MathHelper.clamp(value, this.minValue, this.maxValue);
    }

    /** Plain denormalisation — the stored value is already snapped (see {@link #setValue}). */
    public double getValue() {
        return this.minValue + ((this.maxValue - this.minValue) * this.sliderValue);
    }

    public String getValueString() {
        return DECIMAL_FORMAT.format(getValue());
    }

    /** Upstream {@code applyValue()}. */
    @Override
    protected void func_230972_a_() {
        try {
            String expression = this.controllerName + "=" + getValue();
            this.model.executeExpression(GeckoLibCache.parseSimpleExpression(expression), true, false, null);
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error(e);
        }
    }

    /** Upstream {@code ForgeSlider#updateMessage} with {@code drawString=true}: no separator. */
    @Override
    protected void func_230979_b_() {
        setMessage(this.prefix.deepCopy().appendString(getValueString()));
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiBlitCompat.blitWithBorder(matrixStack, ROULETTE_TEXTURE, this.x, this.y, 0, getTextureY() + 24,
                this.width, this.height, 200, 15, 2, 3, 2, 2);
        GuiBlitCompat.blitWithBorder(matrixStack, ROULETTE_TEXTURE,
                this.x + ((int) (this.sliderValue * (this.width - 8))), this.y, 0, getHandleTextureY() + 24,
                8, this.height, 200, 15, 2, 3, 2, 2);
        drawLabel(matrixStack, font);
    }

    /**
     * Stand-in for upstream's {@code renderScrollingString}: centred, trimmed to fit, drawn at the
     * same vertical position ((top + bottom - 9) / 2 + 1) and in the same active foreground colour
     * (0xFFFFFF) that helper uses.
     */
    private void drawLabel(MatrixStack matrixStack, FontRenderer font) {
        String text = getMessage().getString();
        int inner = this.width - 4;
        if (inner <= 0) {
            return;
        }
        if (font.getStringWidth(text) > inner) {
            text = font.func_238412_a_(text, inner);
        }
        int color = 16777215 | (MathHelper.ceil(this.alpha * 255.0F) << 24);
        drawCenteredString(matrixStack, font, text, this.x + (this.width / 2),
                this.y + ((this.height - 9) / 2) + 1, color);
    }

    /** Vanilla 1.20 {@code AbstractSliderButton#getTextureY}: track band keys off focus alone. */
    private int getTextureY() {
        return this.isFocused() ? 20 : 0;
    }

    /** Vanilla 1.20 {@code AbstractSliderButton#getHandleTextureY}: hover-or-focus, no active gate. */
    private int getHandleTextureY() {
        return this.isHovered() ? 60 : 40;
    }
}
