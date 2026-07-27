package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Port of upstream {@code client/gui/button/PackIconButton} (1.20.1): one folder cell in the
 * picker grid - pack icon, name, hover description tooltip.
 *
 * <p>Translation notes:
 * <ul>
 *   <li>Upstream's missing-icon branch blits {@code texture/default_pack_icon.png} via a private
 *       static, but both branches actually blit the same looked-up location (upstream bug). Here
 *       the fallback works as intended: 1.16.5 {@code TextureManager#getTexture} returns
 *       {@code null} for unregistered locations (no pack-icon loader exists locally), and the
 *       default icon is bound instead.</li>
 *   <li>The private shadowless centered-draw helpers map to {@code func_243248_b} (component) and
 *       {@code func_238422_b_} (reordering processor).</li>
 * </ul>
 */
public class PackIconButton extends Button {

    private static final ResourceLocation DEFAULT_ICON = new ResourceLocation(YesSteveModel.MOD_ID,
            "texture/default_pack_icon.png");

    private final ModelPackData packData;

    public PackIconButton(int x, int y, int width, int height, ModelPackData packData, Button.IPressable onPress) {
        super(x, y, width, height,
                new StringTextComponent(ModelMetadataPresenter.getLocalizedString(packData, "name", packData.getName())),
                onPress);
        this.packData = packData;
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        FontRenderer font = minecraft.fontRenderer;
        this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + this.height, -6598176, -6598176);
        ResourceLocation iconLocation = FileTypeUtil.getPackIconLocation(this.packData.getPath());
        Texture texture = minecraft.getTextureManager().getTexture(iconLocation);
        minecraft.getTextureManager().bindTexture(texture == null ? DEFAULT_ICON : iconLocation);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(matrixStack, this.x, this.y, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        RenderSystem.disableBlend();
        List<IReorderingProcessor> lines = font.trimStringToWidth(this.getMessage(), 45);
        if (lines.size() > 1) {
            drawCenteredProcessor(matrixStack, font, lines.get(0), this.x + (this.width / 2), (this.y + this.height) - 19,
                    5592405);
            drawCenteredProcessor(matrixStack, font, lines.get(1), this.x + (this.width / 2), (this.y + this.height) - 10,
                    5592405);
        } else {
            drawCenteredComponent(matrixStack, font, this.getMessage(), this.x + (this.width / 2),
                    (this.y + this.height) - 15, 5592405);
        }
        if (this.isHovered() || this.isFocused()) {
            this.fillGradient(matrixStack, this.x, this.y + 1, this.x + 1, (this.y + this.height) - 1, -1982745,
                    -1982745);
            this.fillGradient(matrixStack, this.x, this.y, this.x + this.width, this.y + 1, -1982745, -1982745);
            this.fillGradient(matrixStack, (this.x + this.width) - 1, this.y + 1, this.x + this.width,
                    (this.y + this.height) - 1, -1982745, -1982745);
            this.fillGradient(matrixStack, this.x, (this.y + this.height) - 1, this.x + this.width, this.y + this.height,
                    -1982745, -1982745);
        }
    }

    /** Upstream {@code renderDescription(GuiGraphics, Screen, int, int)}: hover tooltip with the pack description. */
    public void renderDescription(MatrixStack matrixStack, Screen screen, int mouseX, int mouseY) {
        String description = ModelMetadataPresenter.getLocalizedString(this.packData, "description",
                this.packData.getDescription());
        if (StringUtils.isBlank(description) || !this.isHovered()) {
            return;
        }
        List<ITextComponent> lines = Collections.singletonList(new StringTextComponent(description));
        screen.func_243308_b(matrixStack, lines, mouseX, mouseY);
    }

    private static void drawCenteredComponent(MatrixStack matrixStack, FontRenderer font, ITextComponent component,
                                              int centerX, int y, int color) {
        font.func_243248_b(matrixStack, component, (float) (centerX - font.getStringPropertyWidth(component) / 2),
                (float) y, color);
    }

    private static void drawCenteredProcessor(MatrixStack matrixStack, FontRenderer font, IReorderingProcessor line,
                                              int centerX, int y, int color) {
        font.func_238422_b_(matrixStack, line, (float) (centerX - font.getStringWidth(line) / 2), (float) y, color);
    }
}
