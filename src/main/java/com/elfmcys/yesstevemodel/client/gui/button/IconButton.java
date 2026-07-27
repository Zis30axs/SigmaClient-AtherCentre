package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;

/**
 * Port of upstream {@code client/gui/button/IconButton} (1.20.1): a {@link FlatColorButton} with a
 * 16x16 icon from {@code texture/icon.png} centred on it.
 *
 * <p>Translation notes: {@code GuiGraphics#blit(texture, ...)} binds the texture itself; in 1.16.5
 * the caller binds it and then calls the static {@code AbstractGui.blit} with the same argument
 * order. Blend bracketing follows vanilla {@code Widget#renderButton}.
 */
public class IconButton extends FlatColorButton {

    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(YesSteveModel.MOD_ID, "texture/icon.png");

    private final int iconU;

    private final int iconV;

    public IconButton(int x, int y, int width, int height, int iconU, int iconV, Button.IPressable onPress) {
        super(x, y, width, height, StringTextComponent.EMPTY, onPress);
        this.iconU = iconU;
        this.iconV = iconV;
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
        Minecraft.getInstance().getTextureManager().bindTexture(ICON_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(matrixStack, this.x + ((this.width - 16) / 2), this.y + ((this.height - 16) / 2), 16, 16,
                (float) this.iconU, (float) this.iconV, 16, 16, 256, 256);
        RenderSystem.disableBlend();
    }
}
