package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider;
import com.elfmcys.yesstevemodel.client.StarModels;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;

/**
 * Port of upstream {@code client/gui/button/ModIconButton} (1.20.1): the star toggle on the model
 * picker's toolbar; shows a filled star when the currently selected model is starred.
 *
 * <p>Deviation: upstream reads/writes {@code StarModelsCapability} and mirrors the change to the
 * server with {@code C2SSetStarModelPacket}. This standalone client keeps stars in the client
 * config via {@link StarModels}; the packet is cut with the rest of server sync.
 */
public class ModIconButton extends FlatColorButton {

    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(YesSteveModel.MOD_ID, "texture/icon.png");

    public ModIconButton(int x, int y) {
        super(x, y, 20, 20, StringTextComponent.EMPTY, button -> {
        });
    }

    @Override
    public void renderButton(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        super.renderButton(matrixStack, mouseX, mouseY, partialTicks);
        int offsetX = (this.width - 16) / 2;
        int offsetY = (this.height - 16) / 2;
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            boolean starred = StarModels.containsModel(cap.getModelId());
            Minecraft.getInstance().getTextureManager().bindTexture(ICON_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            blit(matrixStack, this.x + offsetX, this.y + offsetY, 16, 16, starred ? 16.0F : 0.0F, 0.0F,
                    16, 16, 256, 256);
            RenderSystem.disableBlend();
        });
    }

    @Override
    public void onPress() {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.getCapability(PlayerCapabilityProvider.PLAYER_CAP).ifPresent(cap -> {
            String modelId = cap.getModelId();
            if (StarModels.containsModel(modelId)) {
                StarModels.removeModel(modelId);
            } else {
                StarModels.addModel(modelId);
            }
        });
    }
}
