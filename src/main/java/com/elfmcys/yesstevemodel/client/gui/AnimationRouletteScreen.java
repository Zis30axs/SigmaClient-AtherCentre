package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.event.AnimationLockEvent;
import com.elfmcys.yesstevemodel.client.gui.button.AnimationSlider;
import com.elfmcys.yesstevemodel.client.gui.button.ConfigCheckBox;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.button.FlatIconButton;
import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.ExtraAnimationKey;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.molang.parser.ParseException;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SPlayAnimationPacket;
import com.elfmcys.yesstevemodel.network.message.C2SRequestExecuteMolangPacket;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.util.InputUtil;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.ui.ScissorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of upstream {@code client/gui/AnimationRouletteScreen} (1.20.1) — the real YSM animation wheel.
 *
 * <p>This replaces the port's own {@code gui/OpenYsmActionWheelScreen}, which wrote into
 * {@code capability/OpenYsmPlayerAnimationState}: a store only the retired render chain read, which is
 * why picking an animation on the wheel never made the model move. The live terminus is
 * {@code PlayerCapability#requestModelSwitch(String)}, consumed by {@code PlayerBaseAnimationPredicate}
 * through {@code PlayerAnimationController}'s {@code "cap"} controller.
 *
 * <p>Translation notes (1.20.1 -> 1.16.5):
 * <ul>
 *   <li>{@code GuiGraphics} -> {@code MatrixStack} plus the static {@code AbstractGui} helpers;
 *       {@code guiGraphics.pose()} is the {@code MatrixStack} itself.</li>
 *   <li>1.20 keeps two widget collections — {@code renderables} (drawn) and {@code children}
 *       (events) — with {@code addRenderableWidget} feeding both and {@code addRenderableOnly}
 *       feeding only the first. 1.16.5 has {@code buttons}/{@code children} but {@code buttons} is
 *       only consulted by {@code Screen#render}, which this screen replaces wholesale. So
 *       {@link #renderList} plays {@code renderables} and {@code addListener} plays {@code children};
 *       {@code buttons} is deliberately left untouched.</li>
 *   <li>1.20 renders widget tooltips itself from {@code AbstractWidget#setTooltip}. There is no such
 *       mechanism here, so the lines are held on the widgets and drawn by
 *       {@link #renderWidgetTooltip}, after the scissor is released so they are not clipped.</li>
 *   <li>{@code enableScissor}/{@code disableScissor} -> {@link ScissorCompat} (GUI-coordinate GL
 *       scissor, the mmdskin precedent).</li>
 *   <li>{@code RenderSystem.setShader(GameRenderer::getPositionColorShader)} is 1.17+; the radial
 *       quads instead use the fixed-function bracket copied from {@code AbstractGui#fillGradient}.
 *       {@code BufferBuilder.vertex(Matrix4f,...)} -> {@code IVertexBuilder#pos(Matrix4f,...)},
 *       {@code Tesselator.end()} -> {@code Tessellator#draw()}.</li>
 *   <li>{@code screen.onClose()} -> {@code closeScreen()}; {@code minecraft.setScreen} ->
 *       {@code displayGuiScreen}; {@code sendSystemMessage} ->
 *       {@code sendMessage(ITextComponent, UUID)} with {@code Util.DUMMY_UUID}.</li>
 * </ul>
 *
 * <p>Cut per the project's server-sync exclusion: every {@code NetworkHandler.sendToServer} branch is
 * unreachable ({@code isClientConnected()} is hardcoded {@code false}) but is kept verbatim so the
 * control flow stays comparable with upstream. The reachable path is upstream's own offline branch.
 */
public class AnimationRouletteScreen extends Screen {

    private static final String SUBMENU_PREFIX = "#";

    private static final String RETURN_KEY = "#return";

    private static final String CONFIG_TITLE_FORMAT = "properties.extra_animation_buttons.%s.config_forms.%d.title";

    private static final String CONFIG_DESC_FORMAT = "properties.extra_animation_buttons.%s.config_forms.%d.description";

    private static final String CONFIG_LABEL_FORMAT = "properties.extra_animation_buttons.%s.config_forms.%d.labels.%d";

    private static final int ITEMS_PER_PAGE = 8;

    /** Radial geometry, verbatim from upstream. */
    private static final float FIRST_SEGMENT_ANGLE = 0.3926991F;

    private static final float SEGMENT_STEP = 0.7853982F;

    private static final float TAU = 6.2831855F;

    private static final float SEGMENT_GAP = 0.034906585F;

    private static final LinkedList<Pair<String, Integer>> navigationStack = Lists.newLinkedList();

    private static String lastModelId = StringPool.EMPTY;

    private int centerX;

    private int centerY;

    private int hoveredIndex;

    private int hoveredConfigIndex;

    private ExtraAnimationButtons currentConfigGroup;

    private Pair<String, Integer> currentNavEntry;

    private int configScrollOffset;

    private int maxConfigScroll;

    @Nullable
    private FlatColorButton scrollUpButton;

    @Nullable
    private FlatColorButton scrollDownButton;

    /** Stands in for 1.20's {@code Screen#renderables}. */
    private final List<Widget> renderList = Lists.newArrayList();

    private final OrderedStringMap<String, String> currentProperties;

    private final Map<String, ExtraAnimationButtons> renderGroups;

    private final Map<String, OrderedStringMap<String, String>> textProperties;

    private final ModelProperties timingConfig;

    private final AnimatableEntity<?> animatableModel;

    private final ModelAssembly renderContext;

    /**
     * The GLFW key that opened this wheel, or {@code -1}. Carried across submenu navigation so the
     * hotkey stays swallowed on every page (see {@link #keyPressed}).
     */
    private final int hotkey;

    public AnimationRouletteScreen(Map<String, ExtraAnimationButtons> map,
                                   Map<String, OrderedStringMap<String, String>> map2,
                                   ModelAssembly modelAssembly, AnimatableEntity<?> animatableEntity) {
        this(map, map2, modelAssembly, animatableEntity, -1);
    }

    public AnimationRouletteScreen(Map<String, ExtraAnimationButtons> map,
                                   Map<String, OrderedStringMap<String, String>> map2,
                                   ModelAssembly modelAssembly, AnimatableEntity<?> animatableEntity,
                                   int hotkey) {
        super(new StringTextComponent("Animation Roulette GUI"));
        this.hotkey = hotkey;
        this.hoveredIndex = -1;
        this.hoveredConfigIndex = -1;
        this.currentConfigGroup = null;
        this.configScrollOffset = 0;
        this.maxConfigScroll = 0;
        this.renderContext = modelAssembly;
        this.timingConfig = modelAssembly.getModelData().getModelProperties();
        this.animatableModel = animatableEntity;
        this.textProperties = map2;
        this.renderGroups = map;
        this.currentNavEntry = navigationStack.peekLast();
        if (this.currentNavEntry != null && this.textProperties.containsKey(this.currentNavEntry.getLeft())) {
            this.currentProperties = this.textProperties.get(this.currentNavEntry.getLeft());
            return;
        }
        this.currentProperties = this.timingConfig.getExtraAnimation();
        navigationStack.clear();
        navigationStack.add(MutablePair.of(StringPool.EMPTY,
                Integer.valueOf(this.currentNavEntry == null ? 0 : this.currentNavEntry.getRight().intValue())));
        this.currentNavEntry = navigationStack.peekLast();
    }

    public AnimationRouletteScreen(String modelId, ModelAssembly modelAssembly,
                                   AnimatableEntity<?> animatableEntity) {
        this(modelId, modelAssembly, animatableEntity, -1);
    }

    public AnimationRouletteScreen(String modelId, ModelAssembly modelAssembly,
                                   AnimatableEntity<?> animatableEntity, int hotkey) {
        super(new StringTextComponent("Animation Roulette GUI"));
        this.hotkey = hotkey;
        this.hoveredIndex = -1;
        this.hoveredConfigIndex = -1;
        this.currentConfigGroup = null;
        this.configScrollOffset = 0;
        this.maxConfigScroll = 0;
        this.renderContext = modelAssembly;
        this.timingConfig = modelAssembly.getModelData().getModelProperties();
        this.animatableModel = animatableEntity;
        this.textProperties = this.timingConfig.getExtraAnimationClassify();
        this.renderGroups = this.timingConfig.getExtraAnimationButtons();
        if (!lastModelId.equals(modelId)) {
            navigationStack.clear();
            lastModelId = modelId;
        }
        if (navigationStack.isEmpty()) {
            navigationStack.add(MutablePair.of(StringPool.EMPTY, Integer.valueOf(0)));
        }
        this.currentNavEntry = navigationStack.peekLast();
        if (this.textProperties.containsKey(this.currentNavEntry.getLeft())) {
            this.currentProperties = this.textProperties.get(this.currentNavEntry.getLeft());
            return;
        }
        this.currentProperties = this.timingConfig.getExtraAnimation();
        navigationStack.clear();
        navigationStack.add(MutablePair.of(StringPool.EMPTY, this.currentNavEntry.getRight()));
        this.currentNavEntry = navigationStack.peekLast();
    }

    @Override
    protected void init() {
        clearWidgets();
        this.centerX = (this.width / 2) - 70;
        this.centerY = (this.height / 2) - 8;
        if (this.currentProperties.size() < (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE) + 1) {
            this.currentNavEntry.setValue(Integer.valueOf(0));
        }
        if (this.currentProperties.size() <= this.hoveredIndex) {
            this.hoveredIndex = 0;
        }
        if (this.animatableModel.getEntity() instanceof PlayerEntity) {
            addRenderableWidget(new FlatColorButton(this.centerX - 20, this.centerY - 10, 40, 20,
                    StringTextComponent.EMPTY, button -> AnimationLockEvent.toggleLock()) {
                @Override
                @NotNull
                public ITextComponent getMessage() {
                    if (AnimationLockEvent.isLocked()) {
                        return new TranslationTextComponent("gui.yes_steve_model.roulette.lock_on");
                    }
                    return new TranslationTextComponent("gui.yes_steve_model.roulette.lock_off");
                }
            });
        } else {
            addRenderableWidget(new FlatColorButton(this.centerX - 20, this.centerY - 10, 40, 20,
                    new TranslationTextComponent("gui.yes_steve_model.roulette.stop"), button -> {
                NetworkHandler.sendToServer(
                        C2SPlayAnimationPacket.createWithIndex(this.animatableModel.getEntity().getEntityId()));
                closeScreen();
            }));
        }
        addRenderableWidget(new FlatColorButton(this.centerX + 125, this.centerY - 102, 30, 30,
                new StringTextComponent("<"), button -> previousPage()));
        addRenderableWidget(new FlatColorButton(this.centerX + 240, this.centerY - 102, 30, 30,
                new StringTextComponent(">"), button -> nextPage()));
        addRenderableWidget(new FlatColorButton(this.centerX + 125, this.centerY - 70, 145, 22,
                new TranslationTextComponent("gui.yes_steve_model.model.return"), button -> navigateBack()));
        if (this.currentConfigGroup != null) {
            this.scrollUpButton = new FlatColorButton(this.centerX + 242, this.centerY - 46, 28, 60,
                    new StringTextComponent("↑"), button -> {
                scrollConfigUp(50);
                if (this.configScrollOffset == 0 && this.scrollUpButton != null) {
                    this.scrollUpButton.active = false;
                }
                if (this.scrollDownButton != null) {
                    this.scrollDownButton.active = true;
                }
            });
            this.scrollDownButton = new FlatColorButton(this.centerX + 242, this.centerY + 50, 28, 60,
                    new StringTextComponent("↓"), button -> {
                scrollConfigDown(50);
                if (this.configScrollOffset == this.maxConfigScroll && this.scrollDownButton != null) {
                    this.scrollDownButton.active = false;
                }
                if (this.scrollUpButton != null) {
                    this.scrollUpButton.active = true;
                }
            });
            addRenderableWidget(this.scrollUpButton);
            addRenderableWidget(this.scrollDownButton);
            int[] rowY = {-46};
            int[] formIndex = {0};
            for (AbstractConfig config : this.currentConfigGroup.getConfigForms()) {
                renderConfigFormItem(config, rowY, formIndex);
            }
        }
    }

    /** 1.20 {@code Screen#clearWidgets}. {@code buttons} is never populated (see class javadoc). */
    private void clearWidgets() {
        this.renderList.clear();
        this.children.clear();
    }

    private <T extends Widget> T addRenderableWidget(T widget) {
        this.renderList.add(widget);
        addListener(widget);
        return widget;
    }

    private <T extends Widget> T addRenderableOnly(T widget) {
        this.renderList.add(widget);
        return widget;
    }

    private void renderConfigFormItem(AbstractConfig abstractConfig, int[] rowY, int[] formIndex) {
        if (abstractConfig instanceof CheckboxConfig) {
            CheckboxConfig config = (CheckboxConfig) abstractConfig;
            executeExpression(abstractConfig.getValue(), value -> {
                this.minecraft.execute(() -> {
                    addRenderableWidget(createCheckbox(config, value, rowY, formIndex));
                    rowY[0] = rowY[0] + 14;
                    formIndex[0] = formIndex[0] + 1;
                    this.maxConfigScroll = Math.max(0, rowY[0] - 110);
                });
            });
        }
        if (abstractConfig instanceof RangeConfig) {
            RangeConfig config = (RangeConfig) abstractConfig;
            executeExpression(abstractConfig.getValue(), value -> {
                this.minecraft.execute(() -> {
                    addRenderableWidget(createSlider(config, value, rowY, formIndex));
                    rowY[0] = rowY[0] + 17;
                    formIndex[0] = formIndex[0] + 1;
                    this.maxConfigScroll = Math.max(0, rowY[0] - 110);
                });
            });
        }
        if (abstractConfig instanceof RadioConfig) {
            RadioConfig config = (RadioConfig) abstractConfig;
            executeExpression(abstractConfig.getValue(), value -> {
                this.minecraft.execute(() -> renderRadioGroup(config, value, rowY, formIndex));
            });
        }
    }

    private void renderRadioGroup(RadioConfig radioConfig, String value, int[] rowY, int[] formIndex) {
        int selected = Math.round(parseFloatValue(value));
        OrderedStringMap<String, String> labels = radioConfig.getLabels();
        if (selected < 0 || labels.size() < selected) {
            selected = 0;
        }
        int widest = 0;
        int i = 0;
        Iterator<String> it = labels.getKeys().iterator();
        while (it.hasNext()) {
            widest = Math.max(widest, this.font.getStringWidth(ModelMetadataPresenter.getLocalizedModelString(
                    this.renderContext,
                    String.format(CONFIG_LABEL_FORMAT, this.currentConfigGroup.getId(),
                            Integer.valueOf(formIndex[0]), Integer.valueOf(i)),
                    it.next())) + 16);
            i++;
        }
        if (widest == 0) {
            widest = 115;
        }
        int columns = Math.max(1, 115 / widest);
        String title = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_TITLE_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                radioConfig.getTitle());
        String description = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_DESC_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                radioConfig.getDescription());
        int groupHeight = ((((labels.size() - 1) / columns) + 1) * 14) + 14;
        FlatIconButton iconButton = new FlatIconButton(this.centerX + 125, this.centerY + rowY[0], groupHeight,
                new StringTextComponent(title));
        iconButton.setTooltipLines(tooltipLines(description));
        addRenderableOnly(iconButton);
        int labelY = rowY[0] + 14;
        int index = 0;
        while (index < labels.size()) {
            ITextComponent label = new StringTextComponent(ModelMetadataPresenter.getLocalizedModelString(
                    this.renderContext,
                    String.format(CONFIG_LABEL_FORMAT, this.currentConfigGroup.getId(),
                            Integer.valueOf(formIndex[0]), Integer.valueOf(index)),
                    labels.getKeyAt(index)));
            String expression = labels.getValueAt(index);
            boolean isSelected = selected == index;
            int columnWidth = Math.round(110.0F / columns);
            ConfigCheckBox configCheckBox = new ConfigCheckBox(
                    this.centerX + 127 + (columnWidth * (index % columns)), this.centerY + labelY, columnWidth,
                    label, checked -> {
                executeExpression(expression, null);
                if (!GeckoLibCache.isRoamingVariableAssignment(expression) && NetworkHandler.isClientConnected()
                        && !ServerConfig.LOW_BANDWIDTH_USAGE.get().booleanValue()) {
                    NetworkHandler.sendToServer(new C2SRequestExecuteMolangPacket(expression,
                            this.animatableModel.getEntity().getEntityId()));
                }
                init();
            });
            configCheckBox.setStateTriggered(isSelected);
            addRenderableWidget(configCheckBox);
            if (index % columns == columns - 1) {
                labelY += 14;
            }
            index++;
        }
        rowY[0] = rowY[0] + groupHeight + 3;
        formIndex[0] = formIndex[0] + 1;
        this.maxConfigScroll = Math.max(0, rowY[0] - 110);
    }

    @NotNull
    private AnimationSlider createSlider(RangeConfig rangeConfig, String value, int[] rowY, int[] formIndex) {
        String title = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_TITLE_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                rangeConfig.getTitle());
        String description = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_DESC_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                rangeConfig.getDescription());
        AnimationSlider animationSlider = new AnimationSlider(this.centerX + 125, this.centerY + rowY[0],
                new StringTextComponent(title), parseFloatValue(value), this.animatableModel,
                rangeConfig.getValue(), rangeConfig.getStep(), rangeConfig.getMin(), rangeConfig.getMax());
        animationSlider.setTooltipLines(tooltipLines(description));
        return animationSlider;
    }

    @NotNull
    private ConfigCheckBox createCheckbox(CheckboxConfig checkboxConfig, String value, int[] rowY, int[] formIndex)
            throws NumberFormatException {
        String title = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_TITLE_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                checkboxConfig.getTitle());
        String description = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                String.format(CONFIG_DESC_FORMAT, this.currentConfigGroup.getId(), Integer.valueOf(formIndex[0])),
                checkboxConfig.getDescription());
        float current = parseFloatValue(value);
        ConfigCheckBox configCheckBox = new ConfigCheckBox(this.centerX + 125, this.centerY + rowY[0],
                new StringTextComponent(title), checked -> {
            String expression = checkboxConfig.getValue() + "=" + (checked.booleanValue() ? "1" : "0");
            executeExpression(expression, null);
            if (!GeckoLibCache.isRoamingVariableAssignment(expression) && NetworkHandler.isClientConnected()
                    && !ServerConfig.LOW_BANDWIDTH_USAGE.get().booleanValue()) {
                NetworkHandler.sendToServer(new C2SRequestExecuteMolangPacket(expression,
                        this.animatableModel.getEntity().getEntityId()));
            }
        }) {
            @Override
            public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
                fill(matrixStack, this.x, this.y, this.x + this.getWidth(), this.y + this.getHeightRealms(),
                        -280804798);
                super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
            }
        };
        configCheckBox.setStateTriggered(current > 0.0F);
        configCheckBox.setTooltipLines(tooltipLines(description));
        return configCheckBox;
    }

    private float parseFloatValue(String value) throws NumberFormatException {
        float parsed;
        if ("null".equals(value)) {
            parsed = 0.0F;
        } else if (NumberUtils.isParsable(value)) {
            parsed = Float.parseFloat(value);
        } else if (BooleanUtils.toBooleanObject(value) != null) {
            parsed = BooleanUtils.toBoolean(value) ? 1.0F : 0.0F;
        } else {
            parsed = 0.0F;
        }
        return parsed;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        int scrolledMouseY;
        drawCenteredString(matrixStack, this.font,
                new TranslationTextComponent("gui.yes_steve_model.roulette.path",
                        StringUtils.joinWith(" > ", navigationStack.stream().map(Pair::getLeft).toArray())),
                this.centerX + 195, this.centerY - 100, 16777215);
        renderRadialBackground(matrixStack, mouseX, mouseY);
        renderRadialButtons(matrixStack);
        renderPageInfo(matrixStack);
        for (Widget widget : this.renderList) {
            if (!(widget instanceof ISpecialWidget)) {
                widget.render(matrixStack, mouseX, mouseY, partialTicks);
            }
        }
        ScissorCompat.enable(0, this.centerY - 46, this.width, this.centerY + 110);
        if (mouseY < this.centerY - 46 || this.centerY + 110 < mouseY) {
            scrolledMouseY = -1000;
        } else {
            scrolledMouseY = mouseY + this.configScrollOffset;
        }
        matrixStack.push();
        matrixStack.translate(0.0D, -this.configScrollOffset, 0.0D);
        for (Widget widget : this.renderList) {
            if (widget instanceof ISpecialWidget) {
                widget.render(matrixStack, mouseX, scrolledMouseY, partialTicks);
            }
        }
        matrixStack.pop();
        ScissorCompat.disable();
        renderHoverTooltip(matrixStack, mouseX, scrolledMouseY);
        renderWidgetTooltip(matrixStack, mouseX, mouseY, scrolledMouseY);
    }

    private void renderHoverTooltip(MatrixStack matrixStack, int mouseX, int mouseY) {
        if (-1 < this.hoveredIndex && this.hoveredIndex < this.currentProperties.size()) {
            String description = ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                    String.format("properties.extra_animation.%s.desc",
                            this.currentProperties.getKeyAt(this.hoveredIndex)),
                    StringPool.EMPTY);
            if (StringUtils.isNotBlank(description)) {
                renderTooltip(matrixStack, this.font.trimStringToWidth(new StringTextComponent(description), 240),
                        mouseX, mouseY);
            }
        }
    }

    /**
     * Stands in for 1.20's automatic {@code AbstractWidget} tooltip pass. Hit-testing uses the
     * scroll-adjusted Y for {@link ISpecialWidget}s (they were drawn under a translate), but the
     * tooltip is drawn at the real cursor position and outside the scissor.
     */
    private void renderWidgetTooltip(MatrixStack matrixStack, int mouseX, int mouseY, int scrolledMouseY) {
        for (Widget widget : this.renderList) {
            List<IReorderingProcessor> lines = tooltipLinesOf(widget);
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            int hitY = (widget instanceof ISpecialWidget) ? scrolledMouseY : mouseY;
            if (!widget.isMouseOver(mouseX, hitY)) {
                continue;
            }
            renderTooltip(matrixStack, lines, mouseX, mouseY);
            return;
        }
    }

    /**
     * Upstream attaches these as {@code Tooltip.create(...)}, which 1.20 wraps at 170px when drawn.
     * 1.16.5 tooltip rendering never wraps, so the split happens here instead.
     */
    private List<IReorderingProcessor> tooltipLines(String description) {
        return this.font.trimStringToWidth(new StringTextComponent(description), 170);
    }

    @Nullable
    private static List<IReorderingProcessor> tooltipLinesOf(Widget widget) {
        if (widget instanceof FlatColorButton) {
            return ((FlatColorButton) widget).getTooltipLines();
        }
        if (widget instanceof FlatIconButton) {
            return ((FlatIconButton) widget).getTooltipLines();
        }
        if (widget instanceof ConfigCheckBox) {
            return ((ConfigCheckBox) widget).getTooltipLines();
        }
        if (widget instanceof AnimationSlider) {
            return ((AnimationSlider) widget).getTooltipLines();
        }
        return null;
    }

    private void executeExpression(String expression, @Nullable Consumer<String> consumer) {
        try {
            this.animatableModel.executeExpression(GeckoLibCache.parseSimpleExpression(expression), true, false,
                    consumer);
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error(e);
        }
    }

    private void renderPageInfo(MatrixStack matrixStack) {
        fill(matrixStack, this.centerX + 157, this.centerY - 87, this.centerX + 238, this.centerY - 72, -822083584);
        drawCenteredString(matrixStack, this.font,
                String.format("%d/%d", Integer.valueOf(this.currentNavEntry.getRight().intValue() + 1),
                        Integer.valueOf(((this.currentProperties.size() - 1) / ITEMS_PER_PAGE) + 1)),
                this.centerX + 197, this.centerY - 83, TextFormatting.AQUA.getColor().intValue());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0.0D) {
            if (mouseX < this.centerX + 110) {
                nextPage();
                return true;
            }
            scrollConfigDown(20);
            return true;
        }
        if (delta <= 0.0D) {
            return false;
        }
        if (mouseX < this.centerX + 110) {
            previousPage();
            return true;
        }
        scrollConfigUp(20);
        return true;
    }

    private void previousPage() {
        this.currentNavEntry.setValue(Integer.valueOf(Math.max(0, this.currentNavEntry.getRight().intValue() - 1)));
    }

    private void nextPage() {
        if (this.currentProperties.size() > (this.currentNavEntry.getRight().intValue() + 1) * ITEMS_PER_PAGE) {
            this.currentNavEntry.setValue(Integer.valueOf(this.currentNavEntry.getRight().intValue() + 1));
        }
    }

    private void scrollConfigUp(int amount) {
        this.configScrollOffset = Math.max(0, this.configScrollOffset - amount);
    }

    private void scrollConfigDown(int amount) {
        this.configScrollOffset = Math.min(this.maxConfigScroll, this.configScrollOffset + amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (-1 < this.hoveredIndex && this.hoveredIndex < this.currentProperties.size()) {
            this.minecraft.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            String key = this.currentProperties.getKeyAt(this.hoveredIndex);
            if (RETURN_KEY.equals(key)) {
                navigateBack();
            } else if (key.startsWith(SUBMENU_PREFIX)) {
                navigateToSubmenu(key);
            } else {
                playAnimation(key);
            }
        } else if (-1 < this.hoveredConfigIndex && this.hoveredConfigIndex < this.currentProperties.size()) {
            this.minecraft.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            String value = this.currentProperties.getValueAt(this.hoveredConfigIndex);
            if (value.startsWith(SUBMENU_PREFIX)) {
                String groupId = value.substring(SUBMENU_PREFIX.length());
                if (this.renderGroups.containsKey(groupId)) {
                    showConfigGroup(groupId);
                }
            }
        }
        for (IGuiEventListener listener : this.children) {
            double localMouseY = mouseY;
            if (listener instanceof ISpecialWidget) {
                localMouseY = mouseY + this.configScrollOffset;
            }
            if (listener.mouseClicked(mouseX, localMouseY, button)) {
                setListener(listener);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Upstream closes the wheel here when {@code KEY_ROULETTE} is pressed again. On this client the
     * roulette's hotkey is the key bound to Sigma's "YSM Actions" module, and that key must NOT close
     * the wheel, because the very press that opened it is delivered here as well:
     * {@code KeyboardListener#onKeyEvent} calls {@code ModuleKeyPress.press} (which opens the screen)
     * and then, further down the same invocation, re-reads {@code mc.currentScreen} and dispatches the
     * same {@code keyPressed} to it. Closing on the hotkey would make the wheel flash open and shut.
     *
     * <p>So the hotkey is consumed and ignored — it neither closes nor re-opens the wheel, on this
     * press or any later one. ESC and clicking a slot still close it. This is scoped to this screen
     * only, so other modules bound to the same key are untouched: {@code ModuleKeyPress.press} has
     * already fanned the key out to every {@code Bound} before the screen ever sees it.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isHotkey(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Also swallowed on release, so a hold-then-release cannot leak the hotkey either. */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isHotkey(keyCode, scanCode)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean isHotkey(int keyCode, int scanCode) {
        if (this.hotkey != -1 && keyCode == this.hotkey) {
            return true;
        }
        // Also covers the module bind being changed while the wheel is open, and the optional vanilla
        // KEY_ROULETTE binding. Upstream additionally requires no modifier to be held.
        return AnimationRouletteKey.isRouletteHotkey(keyCode, scanCode)
                && InputUtil.activeModifier() == InputUtil.Modifier.NONE;
    }

    private void showConfigGroup(String groupId) {
        this.currentConfigGroup = this.renderGroups.get(groupId);
        this.configScrollOffset = 0;
        this.maxConfigScroll = 0;
        init();
    }

    private void playAnimation(String animationName) {
        ClientPlayerEntity localPlayer = this.minecraft.player;
        if (NetworkHandler.isClientConnected()) {
            Pair<String, Integer> last = navigationStack.peekLast();
            String submenu = StringPool.EMPTY;
            if (last != null && StringUtils.isNotBlank(last.getLeft())) {
                submenu = last.getLeft();
            }
            Entity entity = this.animatableModel.getEntity();
            if (entity instanceof PlayerEntity) {
                NetworkHandler.sendToServer(new C2SPlayAnimationPacket(this.hoveredIndex, submenu));
            } else {
                NetworkHandler.sendToServer(
                        new C2SPlayAnimationPacket(this.hoveredIndex, submenu, entity.getEntityId()));
            }
        } else if (localPlayer != null) {
            localPlayer.getCapability(PlayerCapabilityProvider.PLAYER_CAP)
                    .ifPresent(cap -> cap.requestModelSwitch(animationName));
        }
        if (localPlayer != null && GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG.get().booleanValue()) {
            localPlayer.sendMessage(new TranslationTextComponent(
                    "message.yes_steve_model.model.animation_roulette.play", animationName), Util.DUMMY_UUID);
        }
        this.minecraft.displayGuiScreen(null);
    }

    private void navigateToSubmenu(String key) {
        if (navigationStack.size() > 5) {
            ClientPlayerEntity localPlayer = this.minecraft.player;
            if (localPlayer != null) {
                localPlayer.sendMessage(new TranslationTextComponent("gui.yes_steve_model.roulette.too_long"),
                        Util.DUMMY_UUID);
            }
            return;
        }
        String groupId = key.substring(SUBMENU_PREFIX.length());
        if (this.textProperties.get(groupId) != null) {
            navigationStack.addLast(MutablePair.of(groupId, Integer.valueOf(0)));
            this.minecraft.displayGuiScreen(new AnimationRouletteScreen(this.renderGroups, this.textProperties,
                    this.renderContext, this.animatableModel, this.hotkey));
        }
    }

    private void navigateBack() {
        if (navigationStack.size() > 1) {
            navigationStack.removeLast();
            this.minecraft.displayGuiScreen(new AnimationRouletteScreen(this.renderGroups, this.textProperties,
                    this.renderContext, this.animatableModel, this.hotkey));
            return;
        }
        this.minecraft.displayGuiScreen(null);
    }

    public static void setInitialSubmenu(String groupId) {
        navigationStack.clear();
        navigationStack.addLast(MutablePair.of(StringPool.EMPTY, Integer.valueOf(0)));
        navigationStack.addLast(MutablePair.of(groupId, Integer.valueOf(0)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderRadialButtons(MatrixStack matrixStack) {
        float angle = FIRST_SEGMENT_ANGLE;
        int remaining = this.currentProperties.size()
                - (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE);
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, remaining); i++) {
            int slot = i + (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE);
            int labelX = (int) (this.centerX + (65 * MathHelper.cos(angle)));
            float labelCenterY = this.centerY + (65 * MathHelper.sin(angle));
            int labelY = (int) (labelCenterY - (9.0F / 2.0F));
            String label = this.currentProperties.getValueAt(slot);
            boolean keyIsSubmenu = this.currentProperties.getKeyAt(slot).startsWith(SUBMENU_PREFIX);
            if (label.startsWith(SUBMENU_PREFIX)) {
                String groupId = label.substring(SUBMENU_PREFIX.length());
                if (this.renderGroups.containsKey(groupId)) {
                    label = this.renderGroups.get(groupId).getName();
                    int gearX = (int) (this.centerX + (35 * MathHelper.cos(angle)));
                    float gearCenterY = this.centerY + (35 * MathHelper.sin(angle));
                    drawCenteredString(matrixStack, this.font,
                            new StringTextComponent("⚙").mergeStyle(TextFormatting.BOLD, TextFormatting.GOLD),
                            gearX, (int) (gearCenterY - (9.0F / 2.0F)), 16777215);
                }
            }
            if (StringUtils.isNoneBlank(label)) {
                renderWrappedLabel(matrixStack, new StringTextComponent(
                        ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                                String.format("properties.extra_animation.%s",
                                        this.currentProperties.getKeyAt(slot)), label)),
                        labelX, labelY, keyIsSubmenu);
            } else {
                drawCenteredString(matrixStack, this.font, new StringTextComponent(
                                ModelMetadataPresenter.getLocalizedModelString(this.renderContext,
                                        String.format("properties.extra_animation.%s",
                                                this.currentProperties.getKeyAt(slot)), String.valueOf(slot))),
                        labelX, labelY - 8, 15986656);
            }
            if (this.currentNavEntry.getRight().intValue() == 0 && navigationStack.size() == 1) {
                renderKeyBindings(matrixStack, slot, labelX, labelY);
            }
            angle += SEGMENT_STEP;
        }
    }

    private void renderKeyBindings(MatrixStack matrixStack, int slot, int x, int y) {
        IFormattableTextComponent line = new StringTextComponent("[ ").mergeStyle(TextFormatting.YELLOW);
        if (slot >= ExtraAnimationKey.KEY_MAPPINGS.size()) {
            return;
        }
        KeyBinding keyBinding = ExtraAnimationKey.KEY_MAPPINGS.get(slot);
        // 1.20 KeyMapping#getKey() -> the public keyCode field.
        if (keyBinding.keyCode.equals(InputMappings.INPUT_INVALID)) {
            line.append(new TranslationTextComponent("key.yes_steve_model.extra_animation.none"));
        } else {
            line.append(keyBinding.func_238171_j_());
        }
        line.appendString(" ]");
        drawCenteredString(matrixStack, this.font, line, x, y + 4, 15986656);
    }

    private void renderWrappedLabel(MatrixStack matrixStack, IFormattableTextComponent label, int x, int y,
                                    boolean isSubmenu) {
        if (isSubmenu) {
            label = label.mergeStyle(TextFormatting.RED);
        }
        List<IReorderingProcessor> lines = this.font.trimStringToWidth(label, 50);
        int lineY = (y - (lines.size() * 9)) + 2;
        if (this.currentNavEntry.getRight().intValue() != 0 || navigationStack.size() > 1) {
            lineY += 9;
        }
        for (IReorderingProcessor line : lines) {
            drawCenteredProcessor(matrixStack, line, x, lineY, 15986656);
            lineY += 9;
        }
    }

    /**
     * 1.16.5 has no {@code drawCenteredString} overload for {@code IReorderingProcessor}.
     * {@code func_238407_a_} is the drop-shadow variant — upstream's
     * {@code GuiGraphics.drawCenteredString(Font, FormattedCharSequence, ...)} delegates to
     * {@code drawString(..., true)}, and every sibling draw on this screen (gear glyph, numeric
     * fallback, key hint) goes through {@code AbstractGui.drawCenteredString}, which is also
     * shadowed; the no-shadow {@code func_238422_b_} would make these labels the odd ones out.
     * Integer division for the centring, matching every vanilla {@code drawCenteredString}.
     */
    private void drawCenteredProcessor(MatrixStack matrixStack, IReorderingProcessor line, int centerX, int y,
                                       int color) {
        this.font.func_238407_a_(matrixStack, line, centerX - (this.font.getStringWidth(line) / 2), y, color);
    }

    private void renderRadialBackground(MatrixStack matrixStack, int mouseX, int mouseY) {
        if (this.currentProperties.isEmpty()) {
            return;
        }
        // 1.16.5 has no shader pipeline; this is AbstractGui#fillGradient's fixed-function bracket,
        // which additionally needs disableTexture (upstream's POSITION_COLOR shader implies it).
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.disableAlphaTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(7, DefaultVertexFormats.POSITION_COLOR);
        Matrix4f pose = matrixStack.getLast().getMatrix();
        float pointerAngle = (float) MathHelper.atan2(mouseY - this.centerY, mouseX - this.centerX);
        if (pointerAngle < 0.0F) {
            pointerAngle = TAU + pointerAngle;
        }
        float pointerRadius = MathHelper.sqrt(MathHelper.squareFloat(mouseY - this.centerY)
                + MathHelper.squareFloat(mouseX - this.centerX));
        boolean anyLabelHovered = false;
        boolean anyConfigHovered = false;
        int visible = Math.min(ITEMS_PER_PAGE,
                this.currentProperties.size() - (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE));
        for (int i = 0; i < visible; i++) {
            float from = ((TAU / ITEMS_PER_PAGE) * i) + SEGMENT_GAP;
            float to = ((TAU / ITEMS_PER_PAGE) * (i + 1)) - SEGMENT_GAP;
            int slot = i + (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE);
            boolean isConfigSlot = this.currentProperties.getValueAt(slot).startsWith(SUBMENU_PREFIX);
            anyLabelHovered = checkRadialHover(from, pointerAngle, to, pointerRadius, anyLabelHovered, isConfigSlot,
                    i, builder, pose);
            boolean gearHovered = from < pointerAngle && pointerAngle < to && 20.0F < pointerRadius
                    && pointerRadius < 50.0F;
            if (isConfigSlot) {
                if (gearHovered) {
                    drawRadialSegment(builder, pose, 15.0F, 50.0F, from, to, -268382465);
                    anyConfigHovered = true;
                    this.hoveredConfigIndex = slot;
                } else {
                    drawRadialSegment(builder, pose, 25.0F, 50.0F, from, to, 1879101183);
                }
            }
        }
        if (!anyLabelHovered) {
            this.hoveredIndex = -1;
        }
        if (!anyConfigHovered) {
            this.hoveredConfigIndex = -1;
        }
        tessellator.draw();
        RenderSystem.shadeModel(7424);
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableTexture();
    }

    private boolean checkRadialHover(float from, float pointerAngle, float to, float pointerRadius,
                                     boolean anyHovered, boolean isConfigSlot, int i, BufferBuilder builder,
                                     Matrix4f pose) {
        boolean hovered = from < pointerAngle && pointerAngle < to && 50.0F < pointerRadius && pointerRadius < 100.0F;
        if (hovered) {
            anyHovered = true;
            this.hoveredIndex = i + (this.currentNavEntry.getRight().intValue() * ITEMS_PER_PAGE);
        }
        if (hovered && i < this.currentProperties.size()) {
            if (isConfigSlot) {
                drawRadialSegment(builder, pose, 50.0F, 115.0F, from, to, -251678464);
                drawRadialSegment(builder, pose, 25.0F, 50.0F, from, to, -1879048192);
            } else {
                drawRadialSegment(builder, pose, 25.0F, 115.0F, from, to, -251678464);
            }
        } else {
            drawRadialSegment(builder, pose, 25.0F, 105.0F, from, to, -1879048192);
        }
        return anyHovered;
    }

    private void drawRadialSegment(BufferBuilder builder, Matrix4f pose, float innerRadius, float outerRadius,
                                   float fromAngle, float toAngle, int argb) {
        float alpha = ((argb >> 24) & 255) / 255.0F;
        float red = ((argb >> 16) & 255) / 255.0F;
        float green = ((argb >> 8) & 255) / 255.0F;
        float blue = (argb & 255) / 255.0F;
        builder.pos(pose, this.centerX + (outerRadius * MathHelper.cos(fromAngle)),
                this.centerY + (outerRadius * MathHelper.sin(fromAngle)), 0.0F)
                .color(red, green, blue, alpha).endVertex();
        builder.pos(pose, this.centerX + (innerRadius * MathHelper.cos(fromAngle)),
                this.centerY + (innerRadius * MathHelper.sin(fromAngle)), 0.0F)
                .color(red, green, blue, alpha).endVertex();
        builder.pos(pose, this.centerX + (innerRadius * MathHelper.cos(toAngle)),
                this.centerY + (innerRadius * MathHelper.sin(toAngle)), 0.0F)
                .color(red, green, blue, alpha).endVertex();
        builder.pos(pose, this.centerX + (outerRadius * MathHelper.cos(toAngle)),
                this.centerY + (outerRadius * MathHelper.sin(toAngle)), 0.0F)
                .color(red, green, blue, alpha).endVertex();
    }
}
