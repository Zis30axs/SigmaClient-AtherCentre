/* 职责：以原生 MatrixStack 渲染模型选择界面。 */
package com.shiroha.mmdskin.ui.selector;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import com.shiroha.mmdskin.ui.selector.application.ModelSelectionApplicationService;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** 文件职责：提供玩家模型选择原生界面。 */
public class ModelSelectorScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ModelSelectionApplicationService SERVICE = ModelSelectorServices.modelSelection();

    private static final int WINDOW_MARGIN = 10;
    private static final int MIN_WINDOW_WIDTH = 150;
    private static final int MAX_WINDOW_WIDTH = 190;
    private static final int MIN_WINDOW_HEIGHT = 220;

    private static final int HEADER_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int LIST_PADDING = 5;
    private static final int CARD_HEIGHT = 14;
    private static final int CARD_GAP = 4;

    private final List<ModelSelectionApplicationService.ModelCard> modelCards = new ArrayList<>();

    private String currentModel;
    private boolean pendingClose;
    private String pendingSettingsModel;

    private float targetScroll;
    private float animatedScroll;
    private int hoveredCard = -1;
    private ButtonTarget hoveredButton = ButtonTarget.NONE;
    private Layout layout = Layout.empty();

    private enum ButtonTarget {
        NONE,
        DONE,
        REFRESH,
        SETTINGS
    }

    public ModelSelectorScreen() {
        super(new TranslationTextComponent("gui.mmdskin.model_selector"));
        reloadModelCards();
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    @Override
    public void render(MatrixStack guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            updateLayout();
            updateHoverState(mouseX, mouseY);
            updateScrollAnimation();
            renderFallback(guiGraphics);
            flushPendingActions(minecraft);
        } catch (Throwable throwable) {
            closeAfterFailure(throwable);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!layout.panel.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (layout.doneButton.contains(mouseX, mouseY)) {
            pendingClose = true;
            return true;
        }
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            refreshModels();
            return true;
        }

        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        if (selectedCard != null
                && selectedCard.configurable()
                && layout.settingsButton.contains(mouseX, mouseY)) {
            pendingSettingsModel = selectedCard.displayName();
            return true;
        }

        if (layout.listBox.contains(mouseX, mouseY) && hoveredCard >= 0 && hoveredCard < modelCards.size()) {
            selectModel(modelCards.get(hoveredCard).displayName());
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!layout.listBox.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        float step = 12.0f;
        targetScroll -= (float) delta * step;
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateLayout() {
        int panelWidth = MathHelper.clamp(Math.round(this.width * 0.14f), MIN_WINDOW_WIDTH, MAX_WINDOW_WIDTH);
        int panelHeight = Math.max(MIN_WINDOW_HEIGHT, this.height - WINDOW_MARGIN * 2);
        int panelX = this.width - panelWidth - WINDOW_MARGIN;
        int panelY = WINDOW_MARGIN;

        UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);
        UiRect header = new UiRect(panelX + 8, panelY + 5, panelWidth - 16, HEADER_HEIGHT);

        int buttonY = panel.y + panel.h - BUTTON_HEIGHT - 6;
        int buttonWidth = (header.w - BUTTON_GAP * 2) / 3;
        UiRect doneButton = new UiRect(header.x, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect refreshButton = new UiRect(header.x + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT);
        UiRect settingsButton = new UiRect(header.x + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT);

        int listY = header.y + header.h + 2;
        int listBottom = buttonY - 4;
        int listHeight = Math.max(48, listBottom - listY);
        UiRect listBox = new UiRect(header.x, listY, header.w, listHeight);
        layout = new Layout(panel, header, listBox, doneButton, refreshButton, settingsButton);

        float maxScroll = maxScroll();
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll);
        animatedScroll = MathHelper.clamp(animatedScroll, 0.0f, maxScroll);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        hoveredButton = ButtonTarget.NONE;
        hoveredCard = -1;

        if (layout.doneButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.DONE;
            return;
        }
        if (layout.refreshButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.REFRESH;
            return;
        }
        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        if (selectedCard != null
                && selectedCard.configurable()
                && layout.settingsButton.contains(mouseX, mouseY)) {
            hoveredButton = ButtonTarget.SETTINGS;
            return;
        }

        if (!layout.listBox.contains(mouseX, mouseY) || modelCards.isEmpty()) {
            return;
        }

        float listTop = layout.listBox.y + LIST_PADDING;
        float itemStride = CARD_HEIGHT + CARD_GAP;
        float localY = (float) mouseY - listTop + animatedScroll;
        if (localY < 0.0f) {
            return;
        }
        int index = (int) (localY / itemStride);
        if (index < 0 || index >= modelCards.size()) {
            return;
        }

        float offsetInItem = localY - index * itemStride;
        if (offsetInItem <= CARD_HEIGHT) {
            hoveredCard = index;
        }
    }

    private void updateScrollAnimation() {
        targetScroll = MathHelper.clamp(targetScroll, 0.0f, maxScroll());
        animatedScroll = MathHelper.lerp(0.24f, animatedScroll, targetScroll);
        if (Math.abs(animatedScroll - targetScroll) < 0.25f) {
            animatedScroll = targetScroll;
        }
    }

    private float maxScroll() {
        int count = modelCards.size();
        float contentHeight = count <= 0
                ? 0.0f
                : LIST_PADDING * 2.0f + count * CARD_HEIGHT + Math.max(0, count - 1) * CARD_GAP;
        return Math.max(0.0f, contentHeight - layout.listBox.h);
    }

    private void renderFallback(MatrixStack guiGraphics) {
        TranslucentTrayChrome.drawOverlay(guiGraphics, this.width, this.height);
        TranslucentTrayChrome.drawPanel(guiGraphics, layout.panel.x, layout.panel.y, layout.panel.w, layout.panel.h);

        this.font.drawString(guiGraphics, this.title.getString(), layout.header.x, layout.header.y + 1, TranslucentTrayChrome.TITLE_TEXT);
        String stats = new TranslationTextComponent("gui.mmdskin.model_selector.stats",
                Math.max(0, modelCards.size() - 1),
                shorten(currentModel, 8)).getString();
        this.font.drawString(guiGraphics, stats, layout.header.x, layout.header.y + 10, TranslucentTrayChrome.SUBTITLE_TEXT);
        drawFallbackButton(guiGraphics, layout.doneButton, new TranslationTextComponent("gui.done").getString(), hoveredButton == ButtonTarget.DONE, true);
        drawFallbackButton(guiGraphics, layout.refreshButton, new TranslationTextComponent("gui.mmdskin.refresh").getString(), hoveredButton == ButtonTarget.REFRESH, true);
        ModelSelectionApplicationService.ModelCard selectedCard = getSelectedCard();
        boolean settingsEnabled = selectedCard != null && selectedCard.configurable();
        drawFallbackButton(guiGraphics, layout.settingsButton, new TranslationTextComponent("gui.mmdskin.model_settings.title").getString(), hoveredButton == ButtonTarget.SETTINGS, settingsEnabled);

        UiRect list = layout.listBox;
        TranslucentTrayChrome.fillListArea(guiGraphics, list.x, list.y, list.w, list.h);
        if (modelCards.isEmpty()) {
            drawCenteredString(guiGraphics, this.font, "No models", list.centerX(), list.centerY() - 4, TranslucentTrayChrome.BODY_TEXT);
            return;
        }

        int y = Math.round(list.y + LIST_PADDING - animatedScroll);
        for (int i = 0; i < modelCards.size(); i++) {
            ModelSelectionApplicationService.ModelCard card = modelCards.get(i);
            if (y + CARD_HEIGHT < list.y) {
                y += CARD_HEIGHT + CARD_GAP;
                continue;
            }
            if (y > list.y + list.h) {
                break;
            }
            boolean selected = card.displayName().equals(currentModel);
            boolean hovered = i == hoveredCard;
            int bg = TranslucentTrayChrome.cardBackground(selected, hovered);
            fill(guiGraphics, list.x + 4, y, list.x + list.w - 4, y + CARD_HEIGHT, bg);
            this.font.drawString(guiGraphics, buildCardLabel(card), list.x + 7, y + 3, TranslucentTrayChrome.BODY_TEXT);
            y += CARD_HEIGHT + CARD_GAP;
        }
    }

    private void drawFallbackButton(MatrixStack guiGraphics, UiRect rect, String text, boolean hovered, boolean enabled) {
        TranslucentTrayChrome.drawButton(guiGraphics, this.font, rect.x, rect.y, rect.w, rect.h, text, hovered, enabled);
    }

    private void reloadModelCards() {
        modelCards.clear();
        modelCards.addAll(SERVICE.loadModelCards());
        currentModel = SERVICE.getCurrentModel();
    }

    private void refreshModels() {
        SERVICE.refreshModelCatalog();
        reloadModelCards();
        targetScroll = 0.0f;
        animatedScroll = 0.0f;
    }

    private void selectModel(String modelName) {
        currentModel = modelName;
        SERVICE.selectModel(modelName);
    }

    private ModelSelectionApplicationService.ModelCard getSelectedCard() {
        for (ModelSelectionApplicationService.ModelCard card : modelCards) {
            if (card.displayName().equals(currentModel)) {
                return card;
            }
        }
        return null;
    }

    private void flushPendingActions(Minecraft minecraft) {
        if (pendingSettingsModel != null && minecraft.currentScreen == this) {
            String modelName = pendingSettingsModel;
            pendingSettingsModel = null;
            minecraft.displayGuiScreen(new ModelSettingsScreen(modelName, this));
            return;
        }

        if (pendingClose && minecraft.currentScreen == this) {
            pendingClose = false;
            minecraft.displayGuiScreen(null);
        }
    }

    private void closeAfterFailure(Throwable throwable) {
        LOGGER.error("[ModelSelector] Native selector failed and will close", throwable);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.currentScreen == this) {
            minecraft.displayGuiScreen(null);
        }
    }

    private static String buildCardLabel(ModelSelectionApplicationService.ModelCard card) {
        String name = shorten(card.displayName(), 14);
        return card.configurable() ? name : name + " (Default)";
    }

    private static String shorten(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        if (maxChars <= 3) {
            return value.substring(0, Math.max(0, maxChars));
        }
        return value.substring(0, maxChars - 2) + "..";
    }

    record UiRect(int x, int y, int w, int h) {
        static UiRect empty() {
            return new UiRect(0, 0, 0, 0);
        }

        boolean contains(double px, double py) {
            return px >= x && py >= y && px <= x + w && py <= y + h;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }

    private record Layout(UiRect panel, UiRect header, UiRect listBox,
                          UiRect doneButton, UiRect refreshButton, UiRect settingsButton) {
        static Layout empty() {
            UiRect empty = UiRect.empty();
            return new Layout(empty, empty, empty, empty, empty, empty);
        }
    }
}
