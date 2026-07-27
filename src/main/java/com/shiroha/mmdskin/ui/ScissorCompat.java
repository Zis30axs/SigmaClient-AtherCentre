package com.shiroha.mmdskin.ui;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * 1.16.5 直移植说明：GuiGraphics.enableScissor/disableScissor 为 1.20 API，
 * 此处以 GL 裁剪 + GUI 缩放换算实现同语义（入参为 GUI 坐标系的 x1,y1,x2,y2）。
 */
public final class ScissorCompat {

    private ScissorCompat() {
    }

    public static void enable(int x1, int y1, int x2, int y2) {
        MainWindow window = Minecraft.getInstance().getMainWindow();
        double scale = window.getGuiScaleFactor();
        int fbHeight = window.getFramebufferHeight();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
                (int) Math.round(x1 * scale),
                (int) Math.round(fbHeight - y2 * scale),
                (int) Math.round((x2 - x1) * scale),
                (int) Math.round((y2 - y1) * scale));
    }

    public static void disable() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
