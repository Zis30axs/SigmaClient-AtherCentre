package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.StarModels;
import com.elfmcys.yesstevemodel.client.animation.AnimationTracker;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.client.upload.IResourceLocatable;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shiroha.mmdskin.ui.ScissorCompat;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Port of upstream {@code client/gui/button/ModelButton} (1.20.1): one model cell in the picker
 * grid - live rotating preview, display name, hover border, star marker, shift-for-details
 * tooltip.
 *
 * <p>Deobfuscation note: upstream's badly-named fields {@code modelId}/{@code modelName}/
 * {@code authorName} hold animation names; they are {@link #hoverAnimation}/
 * {@link #fadeoutAnimation}/{@link #focusAnimation} here.
 *
 * <p>Deviations:
 * <ul>
 *   <li>The ctor's {@code locked} flag (upstream {@code isStarred}) is always {@code false} in
 *       practice: it marks server-auth models the player does not own, and this standalone client
 *       has no auth capability - every local model is usable. The rendering branches are kept for
 *       fidelity.</li>
 *   <li>{@link #onPress()} takes upstream's offline branch (capability bind) and additionally
 *       persists through {@link YesSteveModel#selectModel(String, String)}, because the local
 *       render chain re-reads the client config every frame and would otherwise revert the
 *       choice. The connected branch ({@code C2SRequestSwitchModelPacket}) is cut with server
 *       sync.</li>
 *   <li>Star marker reads {@link StarModels} (client config) instead of the server capability.</li>
 *   <li>{@code GuiGraphics.enableScissor} (already GL-converted upstream) -> {@link ScissorCompat}
 *       with plain GUI-corner coordinates.</li>
 * </ul>
 */
public class ModelButton extends Button {

    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(YesSteveModel.MOD_ID, "texture/icon.png");

    /** Upstream {@code isStarred}: auth-locked (greyed, unclickable). Always false locally. */
    public final boolean isLocked;

    private final int backgroundColor;

    public final ModelAssembly renderContext;

    public final PlayerPreviewEntity modelIdHolder;

    private final String hoverAnimation;

    private final String fadeoutAnimation;

    private final String focusAnimation;

    private final double animationDuration;

    private final boolean disablePreviewRotation;

    private final ITextComponent displayName;

    @Nullable
    private final IResourceLocatable backgroundTexture;

    @Nullable
    private final IResourceLocatable foregroundTexture;

    @Nullable
    private String cachedLanguage;

    @Nullable
    private List<ITextComponent> tooltipLines;

    @Nullable
    private List<ITextComponent> detailedTooltipLines;

    private long lastHoverTime;

    public ModelButton(int x, int y, boolean locked, PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        super(x, y, 52, 90, createDisplayName(previewEntity, modelAssembly), button -> {
        });
        this.tooltipLines = null;
        this.detailedTooltipLines = null;
        this.lastHoverTime = -1L;
        this.isLocked = locked;
        this.backgroundColor = locked ? 2130706432 : -12369342;
        this.renderContext = modelAssembly;
        this.modelIdHolder = previewEntity;
        this.disablePreviewRotation = modelAssembly.getModelData().getModelProperties().isDisablePreviewRotation();
        this.displayName = new StringTextComponent(FileTypeUtil.getNameWithoutArchiveExtension(previewEntity.getModelId()));
        this.backgroundTexture = modelAssembly.getTextureRegistry().getGuiBackground() == null ? null
                : UploadManager.getOrCreateLocatableWithSize(modelAssembly.getTextureRegistry().getGuiBackground(),
                true, 200);
        this.foregroundTexture = modelAssembly.getTextureRegistry().getGuiForeground() == null ? null
                : UploadManager.getOrCreateLocatableWithSize(modelAssembly.getTextureRegistry().getGuiForeground(),
                true, 200);
        Object2ReferenceMap<String, Animation> mainAnimations = modelAssembly.getAnimationBundle().getMainAnimations();
        if (mainAnimations.containsKey("hover")) {
            this.hoverAnimation = "hover";
        } else {
            this.hoverAnimation = "empty";
        }
        if (mainAnimations.containsKey("hover_fadeout")) {
            this.fadeoutAnimation = "hover_fadeout";
            this.animationDuration = mainAnimations.get("hover_fadeout").animationLength * 50.0D;
        } else {
            this.fadeoutAnimation = "empty";
            this.animationDuration = 0.0D;
        }
        if (mainAnimations.containsKey("focus")) {
            this.focusAnimation = "focus";
        } else {
            this.focusAnimation = "empty";
        }
    }

    private static ITextComponent createDisplayName(PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        Metadata metadata = modelAssembly.getModelData().getExtraInfo();
        if (metadata == null || StringUtils.isBlank(metadata.getName())) {
            return new StringTextComponent(FileTypeUtil.getNameWithoutArchiveExtension(previewEntity.getModelId()));
        }
        return new StringTextComponent(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name",
                metadata.getName()));
    }

    @Override
    public ITextComponent getMessage() {
        if (GeneralConfig.isShowModelIdFirst()) {
            return this.displayName;
        }
        return super.getMessage();
    }

    @Override
    public void onPress() {
        PlayerEntity player;
        if (this.isLocked || (player = Minecraft.getInstance().player) == null) {
            return;
        }
        String modelId = this.modelIdHolder.getModelId();
        String textureName = this.modelIdHolder.getCurrentTextureName();
        // Persist first: the local chain re-binds the capability from the client config on the
        // next frame, so a capability-only write would be reverted.
        YesSteveModel.selectModel(modelId, textureName);
        player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap ->
                cap.initModelWithTexture(modelId, textureName));
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        AnimationTracker animationTracker = this.modelIdHolder.getAnimationStateMachine();
        if (this.isHovered()) {
            this.lastHoverTime = Util.milliTime();
            animationTracker.setPreviousAnimation(this.hoverAnimation);
        } else if (Util.milliTime() - this.lastHoverTime < this.animationDuration) {
            animationTracker.setPreviousAnimation(this.fadeoutAnimation);
        } else {
            animationTracker.setPreviousAnimation("empty");
        }
        if (this.isFocused()) {
            animationTracker.setQueuedAnimation(this.focusAnimation);
        } else {
            animationTracker.setQueuedAnimation("empty");
        }
        Minecraft minecraft = Minecraft.getInstance();
        FontRenderer font = minecraft.fontRenderer;
        int x = this.x;
        int y = this.y;
        this.fillGradient(matrixStack, x, y, x + this.width, y + this.height, this.backgroundColor, this.backgroundColor);
        if (this.backgroundTexture != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            this.backgroundTexture.getResourceLocation().ifPresent(location -> {
                minecraft.getTextureManager().bindTexture(location);
                blit(matrixStack, x, y, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
            });
            RenderSystem.disableBlend();
        }
        ScissorCompat.enable(x, y, x + this.width, (y + this.height) - 20);
        ModelPreviewRenderer.renderLivingEntityPreview(x + (this.width / 2.0F), y + (this.height / 2.0F) + 20.0F,
                30.0F, minecraft.getRenderPartialTicks(), this.modelIdHolder, RendererManager.getPlayerRenderer(),
                this.disablePreviewRotation, true);
        ScissorCompat.disable();
        if (this.foregroundTexture != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            this.foregroundTexture.getResourceLocation().ifPresent(location -> {
                minecraft.getTextureManager().bindTexture(location);
                blit(matrixStack, x, y, 3500, 0.0F, 0.0F, this.width, this.height, this.height, this.width);
            });
            RenderSystem.disableBlend();
        }
        List<IReorderingProcessor> lines = font.trimStringToWidth(this.getMessage(), 45);
        if (lines.size() > 1) {
            drawCenteredProcessor(matrixStack, font, lines.get(0), x + (this.width / 2), (y + this.height) - 19,
                    15986656);
            drawCenteredProcessor(matrixStack, font, lines.get(1), x + (this.width / 2), (y + this.height) - 10,
                    15986656);
        } else {
            drawCenteredString(matrixStack, font, this.getMessage(), x + (this.width / 2), (y + this.height) - 15,
                    15986656);
        }
        if (!this.isLocked && (this.isHovered() || this.isFocused())) {
            this.fillGradient(matrixStack, x, y + 1, x + 1, (y + this.height) - 1, -790560, -790560);
            this.fillGradient(matrixStack, x, y, x + this.width, y + 1, -790560, -790560);
            this.fillGradient(matrixStack, (x + this.width) - 1, y + 1, x + this.width, (y + this.height) - 1, -790560,
                    -790560);
            this.fillGradient(matrixStack, x, (y + this.height) - 1, x + this.width, y + this.height, -790560, -790560);
        }
        if (this.isLocked) {
            this.fillGradient(matrixStack, x, y, x + this.width, y + this.height, -1625152990, -1625152990);
        }
        if (StarModels.containsModel(this.modelIdHolder.getModelId())) {
            minecraft.getTextureManager().bindTexture(ICON_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            blit(matrixStack, (x + this.width) - 14, y, 16.0F, 0.0F, 16, 16, 256, 256);
            RenderSystem.disableBlend();
        }
    }

    /** Upstream {@code renderTooltip(GuiGraphics, Screen, int, int)}; 1.16.5 tooltips handle z internally. */
    public void renderTooltip(MatrixStack matrixStack, Screen screen, int mouseX, int mouseY) {
        if (!this.isHovered()) {
            return;
        }
        String selected = Minecraft.getInstance().getLanguageManager().getCurrentLanguage().getCode();
        if (!Objects.equals(this.cachedLanguage, selected)) {
            this.cachedLanguage = selected;
            this.detailedTooltipLines = null;
            this.tooltipLines = null;
        }
        long handle = Minecraft.getInstance().getMainWindow().getHandle();
        if (InputMappings.isKeyDown(handle, 340) || InputMappings.isKeyDown(handle, 344)) {
            if (this.detailedTooltipLines == null) {
                this.detailedTooltipLines = ModelMetadataPresenter.buildModelTooltip(this.renderContext, selected,
                        this.modelIdHolder.getModelId(), true);
            }
            screen.func_243308_b(matrixStack, this.detailedTooltipLines, mouseX, mouseY);
        } else {
            if (this.tooltipLines == null) {
                this.tooltipLines = ModelMetadataPresenter.buildModelTooltip(this.renderContext, selected,
                        this.modelIdHolder.getModelId(), false);
            }
            screen.func_243308_b(matrixStack, this.tooltipLines, mouseX, mouseY);
        }
    }

    @Override
    public boolean clicked(double mouseX, double mouseY) {
        return !this.isLocked && super.clicked(mouseX, mouseY);
    }

    /**
     * 1.16.5 has no {@code drawCenteredString} overload for {@link IReorderingProcessor}; same
     * helper as {@code AnimationRouletteScreen#drawCenteredProcessor}, with the shadowed variant
     * (see Batch J review fix).
     */
    private static void drawCenteredProcessor(MatrixStack matrixStack, FontRenderer font, IReorderingProcessor line,
                                              int centerX, int y, int color) {
        font.func_238407_a_(matrixStack, line, (float) (centerX - font.getStringWidth(line) / 2), (float) y, color);
    }
}
