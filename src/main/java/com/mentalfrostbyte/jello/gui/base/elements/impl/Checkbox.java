package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.lwjgl.opengl.GL11;

public class Checkbox extends Element {
    public boolean field21369;
    public Animation field21370 = new Animation(70, 90);

    public Checkbox(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6) {
        super(var1, var2, var3, var4, var5, var6, false);
    }

    public boolean method13703() {
        return this.field21369;
    }

    public void method13704(boolean var1) {
        this.method13705(var1, true);
    }

    public void method13705(boolean var1, boolean var2) {
        if (var1 != this.method13703()) {
            this.field21369 = var1;
            this.field21370.changeDirection(!this.field21369 ? Animation.Direction.FORWARDS : Animation.Direction.BACKWARDS);
            if (var2) {
                this.callUIHandlers();
            } else {
                // Programmatic set (panel rebuild / external sync): snap to end state.
                // Fresh FORWARDS animations start at 0% which draws the checked look, so
                // unchecked boxes flash "on" for ~70ms after every isHidden rebuild.
                this.snapAnimationToCurrentState();
            }
        } else if (!var2) {
            // Same value as default (false): still need to finish the initial FORWARDS anim.
            this.snapAnimationToCurrentState();
        }
    }

    /** Jump the check animation to its settled frame for the current boolean value. */
    private void snapAnimationToCurrentState() {
        long now = System.currentTimeMillis();
        if (this.field21369) {
            // CHECKED uses BACKWARDS; percent 0 => full check visible.
            this.field21370.direction = Animation.Direction.BACKWARDS;
            this.field21370.reverseStartTime = new java.util.Date(now - this.field21370.reverseDuration - 1L);
        } else {
            // UNCHECKED uses FORWARDS; percent 1 => check fully hidden.
            this.field21370.direction = Animation.Direction.FORWARDS;
            this.field21370.startTime = new java.util.Date(now - this.field21370.duration - 1L);
        }
    }

    @Override
    public void draw(float partialTicks) {
        float var4 = !this.method13212() ? 0.43F : 0.6F;
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) this.widthA,
                (float) this.heightA,
                10.0F,
                RenderUtil2.applyAlpha(-4144960, var4 * this.field21370.calcPercent() * partialTicks)
        );
        float var5 = (1.0F - this.field21370.calcPercent()) * partialTicks;
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) this.widthA,
                (float) this.heightA,
                10.0F,
                RenderUtil2.applyAlpha(RenderUtil2.shiftTowardsOther(-14047489, ClientColors.DEEP_TEAL.getColor(), !this.method13212() ? 1.0F : 0.9F), var5)
        );
        GL11.glPushMatrix();
        GL11.glTranslatef((float) (this.getXA() + this.getWidthA() / 2), (float) (this.getYA() + this.getHeightA() / 2), 0.0F);
        GL11.glScalef(1.5F - 0.5F * var5, 1.5F - 0.5F * var5, 0.0F);
        GL11.glTranslatef((float) (-this.getXA() - this.getWidthA() / 2), (float) (-this.getYA() - this.getHeightA() / 2), 0.0F);
        RenderUtil.drawImage(
                (float) this.xA,
                (float) this.yA,
                (float) this.widthA,
                (float) this.heightA,
                Resources.checkPNG,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var5)
        );
        GL11.glPopMatrix();
        var5 *= var5;
        super.draw(partialTicks);
    }

    @Override
    public void onClick3(int mouseX, int mouseY, int mouseButton) {
        this.method13705(!this.field21369, true);
    }
}