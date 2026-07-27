package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.CheckboxButton;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * Port of upstream {@code client/gui/ExtraPlayerRenderScreen} (1.20.1): the drag-to-place config
 * GUI for the in-game paper-doll overlay. Opened by {@code ExtraPlayerRenderKey} (ALT+P) and by
 * {@code OpenYsmScreens.openExtraPlayerRender()}.
 *
 * <p>Translation notes:
 * <ul>
 *   <li>{@code GuiGraphics} splits into the vanilla {@link MatrixStack}: {@code vLine/hLine/
 *       fillGradient} are protected {@code AbstractGui} methods here, shadow text is
 *       {@code FontRenderer#func_238407_a_}, and text wrapping is
 *       {@code FontRenderer#trimStringToWidth} (which, like upstream's {@code Font#split}, breaks
 *       on the {@code \n} inside the tips lang entry).</li>
 *   <li>1.20's {@code Button.builder(...).bounds(...)} becomes a plain {@code new Button(...)}, and
 *       the Forge-flavoured {@code Checkbox} becomes {@link CheckboxButton} (its {@code onPress}
 *       toggles first, so the override reads {@link CheckboxButton#isChecked()} after
 *       {@code super.onPress()} - same ordering as upstream's {@code selected()}).</li>
 *   <li>Upstream gates its reset button and handle offsets on {@code PauseScreenButtonBuilder
 *       .isServerConnected()}, which upstream implements as {@code YesSteveModel.isOnAndroid()} -
 *       always false on desktop. The pause-screen builder is cut (it is desktop-dead upstream and
 *       its three buttons duplicate the Y / ALT+Y / ALT+P keys), so {@link #SERVER_CONNECTED} is a
 *       documented constant; the branches are kept to stay line-diffable against upstream.</li>
 *   <li>Config goes through the {@link ExtraPlayerRenderConfig} facade over
 *       {@code OpenYsmClientConfig} instead of ForgeConfigSpec; persistence happens once in
 *       {@link #onClose()} instead of per {@code .set()}.</li>
 *   <li>{@code Minecraft#getFrameTime} is {@code getRenderPartialTicks()} here, matching the rest
 *       of the ported GUI tree.</li>
 * </ul>
 */
public class ExtraPlayerRenderScreen extends Screen {

    private static final char RESET_KEY = 'r';

    /** See the class javadoc: upstream's server-connected extras are desktop-dead. */
    private static final boolean SERVER_CONNECTED = false;

    private int mouseStartX;

    private int mouseStartY;

    private float rotationX;

    private float rotationY;

    private boolean isDragging;

    private boolean isRightDragging;

    private int offsetX;

    private int offsetY;

    public ExtraPlayerRenderScreen() {
        super(new StringTextComponent("YSM Extra Player Render Config GUI"));
        this.isDragging = false;
        this.isRightDragging = false;
        this.offsetX = 5;
        this.offsetY = 1;
        this.mouseStartX = ExtraPlayerRenderConfig.PLAYER_POS_X.get().intValue();
        this.mouseStartY = ExtraPlayerRenderConfig.PLAYER_POS_Y.get().intValue();
        this.rotationX = ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue();
        this.rotationY = ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue();
        if (SERVER_CONNECTED) {
            this.offsetX = 16;
            this.offsetY = 0;
        }
    }

    @Override
    protected void init() {
        int i = -30;
        if (SERVER_CONNECTED) {
            this.addButton(new Button((this.width / 2) - 50, this.height - 35, 100, 30,
                    new TranslationTextComponent("controls.reset"), button -> resetTransform()));
            i = -60;
        }
        TranslationTextComponent hideOrShow = new TranslationTextComponent("gui.yes_steve_model.hide_or_show");
        int checkboxWidth = this.font.getStringPropertyWidth(hideOrShow) + 24;
        this.addButton(new CheckboxButton((this.width - checkboxWidth) / 2, this.height + i,
                checkboxWidth, 20, hideOrShow,
                ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get(), true) {
            @Override
            public void onPress() {
                super.onPress();
                ExtraPlayerRenderConfig.setDisablePlayerRender(this.isChecked());
            }
        });
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTick) {
        int boxLeft = this.mouseStartX;
        int boxTop = this.mouseStartY;
        int boxRight = (int) (boxLeft + this.rotationX);
        int boxBottom = (int) (boxTop + (this.rotationX * 2.0f));
        matrixStack.push();
        matrixStack.translate(0.0f, 0.0f, (-500.0f) - ((50.0f * this.rotationX) / 40.0f));
        this.vLine(matrixStack, (this.width / 2) - 1, -2, this.height + 2, -1610612737);
        this.hLine(matrixStack, -2, this.width + 2, (this.height / 2) - 1, -1610612737);
        this.vLine(matrixStack, 10, -2, this.height + 2, -1610612737);
        this.vLine(matrixStack, this.width - 10, -2, this.height + 2, -1610612737);
        this.hLine(matrixStack, -2, this.width + 2, 10, -1610612737);
        this.hLine(matrixStack, -2, this.width + 2, this.height - 10, -1610612737);
        this.vLine(matrixStack, boxLeft, boxTop, boxBottom, -65536);
        this.vLine(matrixStack, boxRight, boxTop, boxBottom, -65536);
        this.hLine(matrixStack, boxLeft, boxRight, boxTop, -65536);
        this.hLine(matrixStack, boxLeft, boxRight, boxBottom, -65536);
        this.fillGradient(matrixStack, boxLeft, boxTop, boxRight, boxBottom, 1342177279, 1342177279);
        this.fillGradient(matrixStack, boxLeft - this.offsetX, boxTop - this.offsetX,
                boxLeft + this.offsetX, boxTop + this.offsetX, -16711777, -16711777);
        this.fillGradient(matrixStack, boxRight - this.offsetX, boxBottom - this.offsetX,
                boxRight + this.offsetX, boxBottom + this.offsetX, -16777057, -16777057);
        int tipY = 15;
        for (IReorderingProcessor line : this.font.trimStringToWidth(
                new TranslationTextComponent("gui.yes_steve_model.extra_player_render.tips"), 500)) {
            this.font.func_238407_a_(matrixStack, line,
                    (this.width - 15) - this.font.getStringWidth(line), tipY, 16777215);
            tipY += 10;
        }
        matrixStack.pop();
        if (this.minecraft != null && this.minecraft.player != null
                && !ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get()) {
            ModelPreviewRenderer.renderPlayerOverlay(matrixStack, this.minecraft.player,
                    this.mouseStartX, this.mouseStartY, this.rotationX, this.rotationY, -500,
                    this.minecraft.getRenderPartialTicks());
        }
        super.render(matrixStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inLeftHandleX = (this.mouseStartX - this.offsetX) < mouseX
                && mouseX < (this.mouseStartX + this.offsetX);
        boolean inLeftHandleY = (this.mouseStartY - this.offsetX) < mouseY
                && mouseY < (this.mouseStartY + this.offsetX);
        if (button == 0 && inLeftHandleX && inLeftHandleY) {
            this.isDragging = true;
        }
        int rightHandleX = (int) (this.mouseStartX + this.rotationX);
        int rightHandleY = (int) (this.mouseStartY + (this.rotationX * 2.0f));
        boolean inRightHandleX = (rightHandleX - this.offsetX) < mouseX
                && mouseX < (rightHandleX + this.offsetX);
        boolean inRightHandleY = (rightHandleY - this.offsetX) < mouseY
                && mouseY < (rightHandleY + this.offsetX);
        if (button == 0 && inRightHandleX && inRightHandleY) {
            this.isRightDragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        this.isRightDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isRightDragging) {
            this.rotationX = (float) Math.min(mouseX - this.mouseStartX,
                    (mouseY - this.mouseStartY) / 2.0d);
            return true;
        }
        if (this.isDragging) {
            this.mouseStartX = (int) mouseX;
            this.mouseStartY = (int) mouseY;
            return true;
        }
        if (button == this.offsetY) {
            this.rotationY += (float) (dragX * 2.0d);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (Character.toLowerCase(codePoint) == RESET_KEY && hasAltDown()) {
            resetTransform();
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void resetTransform() {
        this.mouseStartX = 10;
        this.mouseStartY = 10;
        this.rotationX = 40.0f;
        this.rotationY = 5.0f;
    }

    @Override
    public void onClose() {
        ExtraPlayerRenderConfig.setPlayerPosX(this.mouseStartX);
        ExtraPlayerRenderConfig.setPlayerPosY(this.mouseStartY);
        ExtraPlayerRenderConfig.setPlayerScale(this.rotationX);
        ExtraPlayerRenderConfig.setPlayerYawOffset(this.rotationY);
        ExtraPlayerRenderConfig.save();
        super.onClose();
    }
}
