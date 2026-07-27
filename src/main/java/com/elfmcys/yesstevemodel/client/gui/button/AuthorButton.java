package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.ConfirmOpenLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Port of upstream {@code client/gui/button/AuthorButton} (1.20.1): one author card on the model
 * info screen - avatar, name, role, comment, and a scrollable contact tooltip whose entries open
 * links or copy to the clipboard.
 *
 * <p>Translation notes: {@code renderScrollingString} -> centre + trim (same stand-in as
 * {@link FlatColorButton}); {@code ConfirmLinkScreen} -> {@link ConfirmOpenLinkScreen};
 * {@code Util.getPlatform().openUri} -> {@code Util.getOSType().openURI};
 * {@code keyboardHandler.setClipboard} -> {@code keyboardListener.setClipboardString}. The
 * selection marker glyphs are mojibake in the decompiled upstream; plain {@code [x]}/{@code [ ]}
 * markers are used instead.
 */
public class AuthorButton extends Button {

    @Nullable
    private final AuthorInfo authorInfo;

    @Nullable
    private final ModelAssembly modelAssembly;

    @Nullable
    private final ResourceLocation resourceLocation;

    private final int authorIndex;

    private final List<ITextComponent> componentList;

    private int selectedContactIndex;

    private final Screen parentScreen;

    public AuthorButton(int x, int y, @Nullable AuthorInfo authorInfo, @Nullable ModelAssembly modelAssembly,
                        @Nullable ResourceLocation resourceLocation, int authorIndex, Screen parentScreen) {
        super(x, y, 70, 130, StringTextComponent.EMPTY, button -> {
        });
        this.selectedContactIndex = -1;
        this.authorInfo = authorInfo;
        this.modelAssembly = modelAssembly;
        this.resourceLocation = resourceLocation;
        this.authorIndex = authorIndex;
        this.componentList = Lists.newArrayList();
        if (this.authorInfo != null) {
            renderTooltipLines(false);
        }
        this.parentScreen = parentScreen;
    }

    public static AuthorButton createAuthorButton(int x, int y, Screen screen) {
        return new AuthorButton(x, y, null, null, null, -1, screen);
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = Minecraft.getInstance().fontRenderer;
        if (this.authorInfo == null || this.modelAssembly == null || this.resourceLocation == null) {
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -1891417534,
                    -1891417534);
            drawCenteredString(matrixStack, font, new StringTextComponent("......"), this.x + (this.width / 2),
                    this.y + (this.height / 2), TextFormatting.GRAY.getColor());
            return;
        }
        if (this.isHovered() || this.isFocused()) {
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -1892652116,
                    -1892652116);
        } else {
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -1891417534,
                    -1891417534);
        }
        Minecraft.getInstance().getTextureManager().bindTexture(this.resourceLocation);
        blit(matrixStack, this.x + 3, this.y + 3, 64, 64, 0.0F, 0.0F, 64, 64, 64, 64);
        String name = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly,
                String.format("metadata.authors.%d.name", this.authorIndex), this.authorInfo.getName());
        String role = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly,
                String.format("metadata.authors.%d.role", this.authorIndex), this.authorInfo.getRole());
        String comment = ModelMetadataPresenter.getLocalizedModelString(this.modelAssembly,
                String.format("metadata.authors.%d.comment", this.authorIndex), this.authorInfo.getComment());
        drawScrollingStandIn(matrixStack, font, new StringTextComponent(name), this.x + 2, this.y + 72,
                (this.x + this.width) - 2, TextFormatting.GOLD.getColor());
        String trimmedRole = font.func_238412_a_(role, this.width);
        font.drawStringWithShadow(matrixStack, trimmedRole,
                (float) (this.x + (this.width - font.getStringWidth(trimmedRole)) / 2), (float) (this.y + 82),
                TextFormatting.GREEN.getColor());
        drawWrappedText(matrixStack, font, new StringTextComponent(comment), this.x + 3, this.y + 95, 64, -1);
    }

    /** Upstream {@code drawWrappedText}: split to width, shadowless, clipped at the button's bottom edge. */
    public void drawWrappedText(MatrixStack matrixStack, FontRenderer font, ITextComponent text, int x, int y,
                                int width, int color) {
        for (IReorderingProcessor line : font.trimStringToWidth(text, width)) {
            font.func_238422_b_(matrixStack, line, (float) x, (float) y, color);
            y += 9;
            if (y > this.y + this.height) {
                return;
            }
        }
    }

    /** Upstream {@code refreshContactComponents}: hover shows the contact tooltip; unhover resets the selection. */
    public void refreshContactComponents(MatrixStack matrixStack, Screen screen, int mouseX, int mouseY) {
        if (this.isHovered() && !this.componentList.isEmpty()) {
            screen.func_243308_b(matrixStack, this.componentList, mouseX, mouseY);
        } else if (this.selectedContactIndex != -1) {
            this.selectedContactIndex = -1;
            renderTooltipLines(false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0.0D) {
            if (this.selectedContactIndex > 0) {
                this.selectedContactIndex--;
                renderTooltipLines(false);
            }
            return true;
        }
        if (delta < 0.0D) {
            if (this.selectedContactIndex < this.componentList.size() - 2) {
                this.selectedContactIndex++;
                renderTooltipLines(false);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void renderTooltipLines(boolean markSelected) {
        if (this.authorInfo == null) {
            return;
        }
        this.componentList.clear();
        for (int i = 0; i < this.authorInfo.getContact().size(); i++) {
            ITextComponent line = new StringTextComponent(
                    this.authorInfo.getContact().getKeyAt(i) + ": " + this.authorInfo.getContact().getValueAt(i));
            if (i == this.selectedContactIndex) {
                line = line.deepCopy().append(
                        new StringTextComponent(markSelected ? " [x]" : " [ ]").mergeStyle(TextFormatting.YELLOW,
                                TextFormatting.BOLD));
            }
            this.componentList.add(line);
        }
        if (!this.componentList.isEmpty()) {
            this.componentList.add(new TranslationTextComponent("gui.yes_steve_model.model.info.contact.click_hint")
                    .mergeStyle(TextFormatting.DARK_GRAY));
        }
    }

    @Override
    public void onPress() {
        if (this.authorInfo == null) {
            return;
        }
        int index = this.selectedContactIndex;
        if (index == -1) {
            index = 0;
        }
        if (index < 0 || index >= this.authorInfo.getContact().size()) {
            return;
        }
        String link = this.authorInfo.getContact().getValueAt(index);
        if (link == null) {
            return;
        }
        if (link.startsWith("http://") || link.startsWith("https://")) {
            Minecraft.getInstance().displayGuiScreen(new ConfirmOpenLinkScreen(confirmed -> {
                if (confirmed) {
                    Util.getOSType().openURI(link);
                }
                Minecraft.getInstance().displayGuiScreen(this.parentScreen);
            }, link, true));
            return;
        }
        Minecraft.getInstance().keyboardListener.setClipboardString(link);
        if (this.selectedContactIndex == -1) {
            this.selectedContactIndex = 0;
        }
        renderTooltipLines(true);
    }

    /** Stand-in for {@code renderScrollingString}: centre, trimmed to the given span. */
    private static void drawScrollingStandIn(MatrixStack matrixStack, FontRenderer font, ITextComponent text,
                                             int left, int y, int right, int color) {
        String trimmed = font.func_238412_a_(text.getString(), right - left);
        font.drawStringWithShadow(matrixStack, trimmed,
                (float) (left + ((right - left) - font.getStringWidth(trimmed)) / 2), (float) y, color);
    }
}
