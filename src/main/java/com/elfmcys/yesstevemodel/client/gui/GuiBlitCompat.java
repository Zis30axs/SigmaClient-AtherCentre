package com.elfmcys.yesstevemodel.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.ResourceLocation;

/**
 * 1.16.5 stand-ins for GUI blit helpers that only exist in later versions.
 *
 * <p>{@link #blitWithBorder} reproduces the Forge 1.20.1
 * {@code GuiGraphics#blitWithBorder} that upstream's roulette slider draws itself with. The
 * algorithm is Forge's own {@code GuiUtils.drawContinuousTexturedBox}: the four corners are drawn
 * at their natural size, and the edges and centre are <em>tiled</em> (not stretched) to fill the
 * remaining area, with a partial tile at the end of each run.
 */
public final class GuiBlitCompat {

    private static final int TEXTURE_SIZE = 256;

    private GuiBlitCompat() {
    }

    public static void blitWithBorder(MatrixStack matrixStack, ResourceLocation texture, int x, int y,
                                      int u, int v, int width, int height, int regionWidth, int regionHeight,
                                      int borderTop, int borderBottom, int borderLeft, int borderRight) {
        Minecraft.getInstance().getTextureManager().bindTexture(texture);

        int fillerWidth = regionWidth - borderLeft - borderRight;
        int fillerHeight = regionHeight - borderTop - borderBottom;
        int canvasWidth = width - borderLeft - borderRight;
        int canvasHeight = height - borderTop - borderBottom;
        if (fillerWidth <= 0 || fillerHeight <= 0) {
            blit(matrixStack, x, y, u, v, width, height);
            return;
        }
        int xPasses = canvasWidth / fillerWidth;
        int remainderWidth = canvasWidth % fillerWidth;
        int yPasses = canvasHeight / fillerHeight;
        int remainderHeight = canvasHeight % fillerHeight;

        // Corners.
        blit(matrixStack, x, y, u, v, borderLeft, borderTop);
        blit(matrixStack, x + borderLeft + canvasWidth, y, u + borderLeft + fillerWidth, v, borderRight, borderTop);
        blit(matrixStack, x, y + borderTop + canvasHeight, u, v + borderTop + fillerHeight, borderLeft, borderBottom);
        blit(matrixStack, x + borderLeft + canvasWidth, y + borderTop + canvasHeight,
                u + borderLeft + fillerWidth, v + borderTop + fillerHeight, borderRight, borderBottom);

        for (int i = 0; i < xPasses + (remainderWidth > 0 ? 1 : 0); i++) {
            int tileWidth = (i == xPasses) ? remainderWidth : fillerWidth;
            int tileX = x + borderLeft + (i * fillerWidth);
            // Top and bottom edges.
            blit(matrixStack, tileX, y, u + borderLeft, v, tileWidth, borderTop);
            blit(matrixStack, tileX, y + borderTop + canvasHeight, u + borderLeft, v + borderTop + fillerHeight,
                    tileWidth, borderBottom);

            for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
                int tileHeight = (j == yPasses) ? remainderHeight : fillerHeight;
                int tileY = y + borderTop + (j * fillerHeight);
                // Left and right edges (once per row).
                if (i == 0) {
                    blit(matrixStack, x, tileY, u, v + borderTop, borderLeft, tileHeight);
                    blit(matrixStack, x + borderLeft + canvasWidth, tileY, u + borderLeft + fillerWidth,
                            v + borderTop, borderRight, tileHeight);
                }
                // Centre.
                blit(matrixStack, tileX, tileY, u + borderLeft, v + borderTop, tileWidth, tileHeight);
            }
        }
    }

    private static void blit(MatrixStack matrixStack, int x, int y, int u, int v, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        AbstractGui.blit(matrixStack, x, y, (float) u, (float) v, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
