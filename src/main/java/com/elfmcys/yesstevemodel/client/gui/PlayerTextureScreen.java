package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.button.IconButton;
import com.elfmcys.yesstevemodel.client.gui.button.TextureButton;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.ui.ScissorCompat;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of upstream {@code client/gui/PlayerTextureScreen} (1.20.1): the texture picker - central
 * drag/zoom preview, animation list on the left, texture cells on the right.
 *
 * <p>Translation notes: {@code GuiGraphics.enableScissor} (pre-converted upstream) ->
 * {@link ScissorCompat} with GUI-corner coordinates; {@code SimpleSoundInstance.forUI} ->
 * {@code SimpleSound.master}; {@code I18n.exists} -> {@code I18n.hasKey}; {@code Mth.clamp} ->
 * {@link MathHelper#clamp}; {@code setScreen} -> {@code displayGuiScreen}. {@code HIDDEN_PREFIX}
 * is mojibake in the decompiled upstream; the raw bytes are U+2014 U+2014 ("--" as em dashes).
 */
public class PlayerTextureScreen extends Screen {

    private static final String HIDDEN_PREFIX = "\u2014\u2014";

    private static final float MAX_ZOOM = 360.0F;

    private static final float MIN_ZOOM = 18.0F;

    private static final float MAX_PITCH = 90.0F;

    private static final float MIN_PITCH = -90.0F;

    private static final PlayerPreviewEntity[] texturePreviewHolders = new PlayerPreviewEntity[4];

    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int RIGHT_MOUSE_BUTTON = 1;

    private final List<Widget> renderList = Lists.newArrayList();

    public final PlayerPreviewEntity modelHolder;

    public final ModelAssembly renderContext;

    private final PlayerModelScreen parentScreen;

    private final String modelId;

    private final OrderedStringMap<String, ? extends Texture> textureMap;

    private final List<String> animationKeys;

    private String currentAnimation;

    private int textureMaxPage;

    private int textureCurrentPage;

    private int animationMaxPage;

    private int animationCurrentPage;

    public int guiLeft;

    public int guiTop;

    public float offsetX;

    public float offsetY;

    public float zoom;

    public float yaw;

    public float pitch;

    public boolean showGround;

    static {
        for (int i = 0; i < texturePreviewHolders.length; i++) {
            texturePreviewHolders[i] = new PlayerPreviewEntity();
        }
    }

    public PlayerTextureScreen(PlayerModelScreen parentScreen, String modelId, ModelAssembly modelAssembly) {
        super(new StringTextComponent("Player Texture GUI"));
        this.currentAnimation = StringPool.EMPTY;
        this.offsetX = 0.0F;
        this.offsetY = -60.0F;
        this.zoom = 80.0F;
        this.yaw = 165.0F;
        this.pitch = -5.0F;
        this.showGround = true;
        this.modelHolder = new PlayerPreviewEntity();
        for (PlayerPreviewEntity holder : texturePreviewHolders) {
            holder.resetModel();
            holder.getAnimationStateMachine().setCurrentAnimation("idle");
        }
        this.parentScreen = parentScreen;
        this.modelId = modelId;
        this.renderContext = modelAssembly;
        this.textureMap = modelAssembly.getAnimationBundle().getTextures();
        this.animationKeys = new ArrayList<>(modelAssembly.getAnimationBundle().getMainAnimations().keySet());
        this.animationKeys.removeIf(key -> key.startsWith(HIDDEN_PREFIX));
        this.animationKeys.sort(String::compareTo);
    }

    public TextureButton createTextureButton(int x, int y, PlayerPreviewEntity previewEntity, int index) {
        return new TextureButton(x, y, previewEntity, this.renderContext);
    }

    @Override
    protected void init() {
        clearWidgets();
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        this.textureMaxPage = (this.textureMap.size() - 1) / 4;
        this.animationMaxPage = (this.animationKeys.size() - 1) / 11;
        if (this.textureCurrentPage > this.textureMaxPage) {
            this.textureCurrentPage = 0;
        }
        if (this.animationCurrentPage > this.animationMaxPage) {
            this.animationCurrentPage = 0;
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 5, this.guiTop, 80, 18,
                new TranslationTextComponent("gui.yes_steve_model.model.return"), button ->
                this.minecraft.displayGuiScreen(this.parentScreen)));
        addRenderableWidget(new IconButton(this.guiLeft + 281, this.guiTop + 2, 16, 16, 64, 16, button ->
                this.currentAnimation = "idle").setTooltipText("gui.yes_steve_model.model.stop"));
        addRenderableWidget(new IconButton(this.guiLeft + 263, this.guiTop + 2, 16, 16, 48, 16, button -> {
            this.offsetX = 0.0F;
            this.offsetY = -60.0F;
            this.zoom = 80.0F;
            this.yaw = 165.0F;
            this.pitch = -5.0F;
        }).setTooltipText("gui.yes_steve_model.model.reset"));
        addRenderableWidget(new IconButton(this.guiLeft + 245, this.guiTop + 2, 16, 16, 64, 0, button ->
                this.showGround = !this.showGround).setTooltipText("gui.yes_steve_model.model.ground"));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 321, this.guiTop + 213, 18, 18,
                new StringTextComponent("<"), button -> {
            if (this.textureCurrentPage > 0) {
                this.textureCurrentPage--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 383, this.guiTop + 213, 18, 18,
                new StringTextComponent(">"), button -> {
            if (this.textureCurrentPage < this.textureMaxPage) {
                this.textureCurrentPage++;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 11, this.guiTop + 214, 16, 16,
                new StringTextComponent("<"), button -> {
            if (this.animationCurrentPage > 0) {
                this.animationCurrentPage--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 63, this.guiTop + 214, 16, 16,
                new StringTextComponent(">"), button -> {
            if (this.animationCurrentPage < this.animationMaxPage) {
                this.animationCurrentPage++;
                init();
            }
        }));
        for (int row = 0; row < 11; row++) {
            int animationIndex = row + (this.animationCurrentPage * 11);
            if (animationIndex >= this.animationKeys.size()) {
                break;
            }
            String animation = this.animationKeys.get(animationIndex);
            int y = this.guiTop + 27 + (17 * row);
            String labelKey = String.format("gui.yes_steve_model.texture.button.%s", animation.replaceAll("\\:", "."));
            String descKey = String.format("gui.yes_steve_model.texture.button.%s.desc",
                    animation.replaceAll("\\:", "."));
            ITextComponent label;
            if (I18n.hasKey(labelKey)) {
                label = new TranslationTextComponent(labelKey);
            } else {
                label = new StringTextComponent(animation);
            }
            FlatColorButton animationButton = new FlatColorButton(this.guiLeft + 5, y, 80, 16, label, button ->
                    this.currentAnimation = animation);
            if (I18n.hasKey(descKey)) {
                List<ITextComponent> lines = Lists.newArrayList(
                        new TranslationTextComponent(descKey).mergeStyle(TextFormatting.GOLD),
                        new TranslationTextComponent("gui.yes_steve_model.texture.button.animation_name", animation)
                                .mergeStyle(TextFormatting.GRAY));
                List<net.minecraft.util.IReorderingProcessor> wrapped = new ArrayList<>();
                for (ITextComponent line : lines) {
                    wrapped.addAll(this.font.trimStringToWidth(line, 170));
                }
                animationButton.setTooltipLines(wrapped);
            }
            addRenderableWidget(animationButton);
        }
        for (int cell = 0; cell < 4; cell++) {
            int textureIndex = cell + (this.textureCurrentPage * 4);
            if (textureIndex >= this.textureMap.size()) {
                break;
            }
            int x = this.guiLeft + 306 + (56 * (cell % 2));
            int y = this.guiTop + 5 + (104 * (cell / 2));
            PlayerPreviewEntity previewEntity = texturePreviewHolders[cell];
            previewEntity.initModelWithTexture(this.modelId, this.textureMap.getKeyAt(textureIndex));
            addRenderableWidget(createTextureButton(x, y, previewEntity, textureIndex));
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.renderBackground(matrixStack);
        this.fillGradient(matrixStack, this.guiLeft, this.guiTop + 22, this.guiLeft + 90, this.guiTop + 235,
                -14540254, -14540254);
        this.fillGradient(matrixStack, this.guiLeft + 93, this.guiTop, this.guiLeft + 299, this.guiTop + 235,
                -14540254, -14540254);
        this.fillGradient(matrixStack, this.guiLeft + 302, this.guiTop, this.guiLeft + 420, this.guiTop + 235,
                -14540254, -14540254);
        if (!this.modelHolder.getAnimationStateMachine().isCurrentAnimation(this.currentAnimation)) {
            this.modelHolder.getAnimationStateMachine().setCurrentAnimation(this.currentAnimation);
        }
        renderTexturePreview(matrixStack, this.minecraft.getRenderPartialTicks());
        String texturePage = String.format("%d/%d", this.textureCurrentPage + 1, this.textureMaxPage + 1);
        this.font.drawStringWithShadow(matrixStack, texturePage,
                this.guiLeft + 302 + ((118 - this.font.getStringWidth(texturePage)) / 2.0F),
                (float) ((this.guiTop + 223) - (9 / 2)), 15986656);
        String animationPage = String.format("%d/%d", this.animationCurrentPage + 1, this.animationMaxPage + 1);
        this.font.drawStringWithShadow(matrixStack, animationPage,
                this.guiLeft + 5 + ((80 - this.font.getStringWidth(animationPage)) / 2.0F),
                (float) (this.guiTop + 218), 15986656);
        for (Widget widget : this.renderList) {
            widget.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        for (Widget widget : this.renderList) {
            if (widget instanceof FlatColorButton) {
                ((FlatColorButton) widget).renderTooltip(matrixStack, this, mouseX, mouseY);
            }
        }
    }

    /** Upstream {@code renderTexturePreview(GuiGraphics, ...)}; GL coords upstream -> GUI corners here. */
    public void renderTexturePreview(MatrixStack matrixStack, float partialTick) {
        ScissorCompat.enable(this.guiLeft + 93, this.guiTop, this.guiLeft + 299, this.guiTop + 235);
        this.minecraft.player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            this.modelHolder.initModelWithTexture(this.modelId, cap.getCurrentTextureName());
            ModelPreviewRenderer.renderEntityPreview(this.guiLeft + 149.5F + 40.0F + this.offsetX,
                    this.guiTop + 117.5F + 80.0F + this.offsetY, this.zoom, this.pitch, this.yaw, partialTick,
                    this.modelHolder, RendererManager.getPlayerRenderer(), this.showGround);
        });
        ScissorCompat.disable();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.minecraft == null || !isInPreviewArea(mouseX, mouseY)) {
            return false;
        }
        if (button == LEFT_MOUSE_BUTTON) {
            this.yaw = (float) (this.yaw + (1.5D * dragX));
            adjustPitch((float) dragY);
        }
        if (button == RIGHT_MOUSE_BUTTON) {
            this.offsetX = (float) (this.offsetX + dragX);
            this.offsetY = (float) (this.offsetY + dragY);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.minecraft == null) {
            return false;
        }
        if (delta != 0.0D) {
            if (isInPreviewArea(mouseX, mouseY)) {
                adjustZoom(((float) delta) * 0.07F);
                return true;
            }
            if (isInAnimationArea(mouseX, mouseY)) {
                return scrollAnimationPage(delta);
            }
            if (isInTextureArea(mouseX, mouseY)) {
                return scrollTexturePage(delta);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean scrollTexturePage(double delta) {
        if (delta > 0.0D && this.textureCurrentPage > 0) {
            this.textureCurrentPage--;
            playClickSound();
            init();
        }
        if (delta < 0.0D && this.textureCurrentPage < this.textureMaxPage) {
            this.textureCurrentPage++;
            playClickSound();
            init();
            return true;
        }
        return true;
    }

    private boolean scrollAnimationPage(double delta) {
        if (delta > 0.0D && this.animationCurrentPage > 0) {
            this.animationCurrentPage--;
            playClickSound();
            init();
        }
        if (delta < 0.0D && this.animationCurrentPage < this.animationMaxPage) {
            this.animationCurrentPage++;
            playClickSound();
            init();
            return true;
        }
        return true;
    }

    private void playClickSound() {
        this.minecraft.getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isInPreviewArea(double x, double y) {
        return x >= (this.guiLeft + 93) && x < (this.guiLeft + 299) && y >= this.guiTop && y < (this.guiTop + 235);
    }

    private boolean isInAnimationArea(double x, double y) {
        return x >= this.guiLeft && x < (this.guiLeft + 90) && y >= (this.guiTop + 22) && y < (this.guiTop + 235);
    }

    private boolean isInTextureArea(double x, double y) {
        return x >= (this.guiLeft + 302) && x < (this.guiLeft + 420) && y >= this.guiTop && y < (this.guiTop + 235);
    }

    private void adjustPitch(float delta) {
        if (this.pitch - delta > MAX_PITCH) {
            this.pitch = MAX_PITCH;
        } else if (this.pitch - delta < MIN_PITCH) {
            this.pitch = MIN_PITCH;
        } else {
            this.pitch -= delta;
        }
    }

    private void adjustZoom(float factor) {
        this.zoom = MathHelper.clamp(this.zoom + (factor * this.zoom), MIN_ZOOM, MAX_ZOOM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
