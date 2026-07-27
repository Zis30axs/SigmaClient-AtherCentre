package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.button.AuthorButton;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.client.upload.IResourceLocatable;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConfirmOpenLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of upstream {@code client/gui/ModelInfoScreen} (1.20.1): author cards, model tips, and up
 * to two outbound links for the currently selected model.
 *
 * <p>Translation notes: {@code TextureManager#register} -> {@code loadTexture};
 * {@code ConfirmLinkScreen} -> {@link ConfirmOpenLinkScreen}; {@code Util.getPlatform().openUri}
 * -> {@code Util.getOSType().openURI}. Widget bookkeeping follows the
 * {@code AnimationRouletteScreen} idiom. Only reachable when the model has extra info (the opener
 * guards it); a defensive fallback for a missing {@link Metadata} is kept anyway.
 */
public class ModelInfoScreen extends Screen {

    private static final ResourceLocation DEFAULT_AVATAR = new ResourceLocation(YesSteveModel.MOD_ID,
            "texture/default_avatar.png");

    private static final Map<String, ITextComponent> URL_LABELS = ImmutableMap.of(
            "home", new TranslationTextComponent("gui.yes_steve_model.url.home"),
            "donate", new TranslationTextComponent("gui.yes_steve_model.url.donate"));

    private final List<Widget> renderList = Lists.newArrayList();

    private final List<IResourceLocatable> textureList;

    private final PlayerModelScreen parentScreen;

    private final ModelAssembly renderContext;

    private final ServerModelInfo modelData;

    private int selectedTextureIndex;

    private int guiLeft;

    private int guiTop;

    public ModelInfoScreen(PlayerModelScreen parentScreen, ModelAssembly modelAssembly) {
        super(new StringTextComponent("Model Info GUI"));
        this.textureList = new ArrayList<>();
        this.selectedTextureIndex = 0;
        this.parentScreen = parentScreen;
        this.renderContext = modelAssembly;
        this.modelData = modelAssembly.getModelData();
        initWidgets();
    }

    private void initWidgets() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        this.textureList.clear();
        Metadata metadata = this.modelData.getExtraInfo();
        if (metadata == null) {
            return;
        }
        List<AuthorInfo> authorInfos = metadata.getAuthors();
        Map<String, OuterFileTexture> avatars = this.renderContext.getTextureRegistry().getAuthorAvatars();
        for (int i = 0; i < authorInfos.size(); i++) {
            OuterFileTexture avatar = avatars.get(authorInfos.get(i).getName());
            if (avatar != null) {
                textureManager.loadTexture(new ResourceLocation(YesSteveModel.MOD_ID, "avatars/" + i), avatar);
                this.textureList.add(UploadManager.getOrCreateLocatable(avatar, true));
            } else {
                this.textureList.add(null);
            }
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        this.guiLeft = (this.width - 420) / 2;
        this.guiTop = (this.height - 235) / 2;
        Metadata metadata = this.modelData.getExtraInfo();
        List<AuthorInfo> authorInfos = metadata == null ? new ArrayList<>() : metadata.getAuthors();
        if (authorInfos.size() <= this.selectedTextureIndex) {
            this.selectedTextureIndex = 0;
        }
        int i = 0;
        while (i < 5) {
            int authorIndex = this.selectedTextureIndex + i;
            if (authorIndex >= authorInfos.size()) {
                while (i < 5) {
                    addRenderableWidget(AuthorButton.createAuthorButton(this.guiLeft + 25 + (75 * i), this.guiTop + 15,
                            this));
                    i++;
                }
            } else {
                AuthorInfo authorInfo = authorInfos.get(authorIndex);
                IResourceLocatable locatable = this.textureList.get(authorIndex);
                addRenderableWidget(new AuthorButton(this.guiLeft + 25 + (75 * i), this.guiTop + 15, authorInfo,
                        this.renderContext,
                        locatable != null ? locatable.getResourceLocation().orElse(DEFAULT_AVATAR) : DEFAULT_AVATAR,
                        authorIndex, this));
            }
            i++;
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 2, this.guiTop + 25, 18, 100,
                new StringTextComponent("<"), button -> {
            this.selectedTextureIndex = Math.max(0, this.selectedTextureIndex - 5);
            init();
        }).setTooltipText("gui.yes_steve_model.pre_page"));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 25 + 375, this.guiTop + 25, 18, 100,
                new StringTextComponent(">"), button -> {
            this.selectedTextureIndex += 5;
            init();
        }).setTooltipText("gui.yes_steve_model.next_page"));
        int linkY = this.guiTop + 150;
        if (metadata != null) {
            for (int linkIndex = 0; linkIndex < Math.min(metadata.getLink().size(), 2); linkIndex++) {
                String key = metadata.getLink().getKeyAt(linkIndex);
                String url = metadata.getLink().getValueAt(linkIndex);
                ITextComponent label = URL_LABELS.get(key);
                if (label == null) {
                    label = new StringTextComponent(key);
                }
                addRenderableWidget(new FlatColorButton(this.guiLeft + 310, linkY, 85, 20, label, button ->
                        openUrl(url)));
                linkY += 25;
            }
        }
        addRenderableWidget(new FlatColorButton(this.guiLeft + 310, linkY, 85, 20,
                new TranslationTextComponent("gui.yes_steve_model.model.return"), button ->
                this.minecraft.displayGuiScreen(this.parentScreen)));
    }

    private void openUrl(@Nullable String url) {
        if (url != null && StringUtils.isNoneBlank(url)) {
            this.minecraft.displayGuiScreen(new ConfirmOpenLinkScreen(confirmed -> {
                if (confirmed) {
                    Util.getOSType().openURI(url);
                }
                this.minecraft.displayGuiScreen(this);
            }, url, true));
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        this.fillGradient(matrixStack, this.guiLeft + 25, this.guiTop + 150, this.guiLeft + 305, this.guiTop + 220,
                -1889838245, -1889838245);
        Metadata metadata = this.modelData.getExtraInfo();
        if (metadata != null) {
            int offsetY = 0;
            for (IReorderingProcessor line : this.font.trimStringToWidth(new StringTextComponent(
                    ModelMetadataPresenter.getLocalizedModelString(this.renderContext, "metadata.tips",
                            metadata.getTips())), 270)) {
                this.font.func_238407_a_(matrixStack, line, (float) (this.guiLeft + 30),
                        (float) (this.guiTop + 154 + offsetY), -1);
                offsetY += 9;
                if (offsetY > 9 * 7) {
                    break;
                }
            }
        }
        for (Widget widget : this.renderList) {
            widget.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        for (Widget widget : this.renderList) {
            if (widget instanceof AuthorButton) {
                ((AuthorButton) widget).refreshContactComponents(matrixStack, this, mouseX, mouseY);
            }
        }
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
