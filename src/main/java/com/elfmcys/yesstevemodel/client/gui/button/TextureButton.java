package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.ui.ScissorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import java.util.List;

/**
 * Port of upstream {@code client/gui/button/TextureButton} (1.20.1): one texture cell on the
 * texture picker - live preview wearing that texture, localized texture name below.
 *
 * <p>Deviation: upstream {@code onPress} writes the capability and mirrors with
 * {@code C2SRequestSwitchModelPacket}. The local chain re-reads the client config every frame, so
 * the choice must persist through {@link YesSteveModel#selectModel(String, String)} (the packet is
 * cut with server sync).
 */
public class TextureButton extends Button {

    public final PlayerPreviewEntity previewEntity;

    public final ModelAssembly modelAssembly;

    public TextureButton(int x, int y, PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        super(x, y, 54, 102, StringTextComponent.EMPTY, button -> {
        });
        this.previewEntity = previewEntity;
        this.modelAssembly = modelAssembly;
    }

    @Override
    public void onPress() {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        String textureName = this.previewEntity.getCurrentTextureName();
        YesSteveModel.selectModel(this.previewEntity.getModelId(), textureName);
        player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap ->
                cap.setCurrentTexture(textureName));
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        FontRenderer font = minecraft.fontRenderer;
        this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -12369342, -12369342);
        renderPlayerPreview(matrixStack, minecraft.getRenderPartialTicks());
        String textureName = this.previewEntity.getCurrentTextureName();
        ITextComponent label = new StringTextComponent(ModelMetadataPresenter.getLocalizedModelString(
                this.modelAssembly, String.format("files.player.texture.%s", textureName), textureName));
        List<IReorderingProcessor> lines = font.trimStringToWidth(label, 50);
        if (lines.size() > 1) {
            drawCenteredProcessor(matrixStack, font, lines.get(0), this.x + (this.width / 2),
                    (this.y + this.height) - 19, 15986656);
            drawCenteredProcessor(matrixStack, font, lines.get(1), this.x + (this.width / 2),
                    (this.y + this.height) - 10, 15986656);
        } else {
            drawCenteredString(matrixStack, font, label, this.x + (this.width / 2), (this.y + this.height) - 15,
                    15986656);
        }
        if (this.isHovered() || this.isFocused()) {
            this.fillGradient(matrixStack, this.x, this.y + 1, this.x + 1, (this.y + this.height) - 1, -790560,
                    -790560);
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + 1, -790560, -790560);
            this.fillGradient(matrixStack, (this.x + this.width) - 1, this.y + 1, this.x + this.width,
                    (this.y + this.height) - 1, -790560, -790560);
            this.fillGradient(matrixStack, this.x, (this.y + this.height) - 1, this.x + this.width,
                    this.y + this.height, -790560, -790560);
        }
    }

    /** Upstream {@code renderPlayerPreview(GuiGraphics, float)}; scissor via {@link ScissorCompat}. */
    public void renderPlayerPreview(MatrixStack matrixStack, float partialTick) {
        ScissorCompat.enable(this.x, this.y, this.x + this.width, (this.y + this.height) - 20);
        ModelPreviewRenderer.renderLivingEntityPreview(this.x + (this.width / 2.0F),
                this.y + (this.height / 2.0F) + 24.0F, 35.0F, partialTick, this.previewEntity,
                RendererManager.getPlayerRenderer(), false, true);
        ScissorCompat.disable();
    }

    /** Shadowed centered draw for a single line (upstream's {@code drawCenteredString}). */
    private static void drawCenteredProcessor(MatrixStack matrixStack, FontRenderer font, IReorderingProcessor line,
                                              int centerX, int y, int color) {
        font.func_238407_a_(matrixStack, line, (float) (centerX - font.getStringWidth(line) / 2), (float) y, color);
    }
}
