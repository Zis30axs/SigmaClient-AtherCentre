package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.TrueTypeFont;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only block of text that hard-wraps to the element width and clips to its
 * height. {@link Text} draws a single line; this exists for long unbroken values
 * such as session tokens, so it wraps per character instead of per word and can
 * render the value masked.
 * <p>
 * Wrapping happens once per content change, not per frame.
 */
public class TextBlock extends AnimatedIconPanel {
    private static final char MASK_CHAR = '·';

    private final int lineHeight;
    private final List<String> lines = new ArrayList<>();
    private String content = "";
    private boolean masked;
    private boolean truncated;

    public TextBlock(
            CustomGuiScreen screen, String id, int x, int y, int width, int height,
            ColorHelper colorHelper, TrueTypeFont font, int lineHeight) {
        super(screen, id, x, y, width, height, colorHelper, "", font, false);
        this.lineHeight = Math.max(1, lineHeight);
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
        this.rewrap();
    }

    public boolean isMasked() {
        return this.masked;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
        this.rewrap();
    }

    /** True when the content did not fit the element height and was cut short. */
    public boolean isTruncated() {
        return this.truncated;
    }

    /**
     * Splits the (optionally masked) content into lines that fit the element width,
     * keeping at most as many as fit the element height. Overflow is signalled by an
     * ellipsis on the last line so a cut-off value never reads as a complete one.
     */
    private void rewrap() {
        this.lines.clear();
        this.truncated = false;
        if (this.content.isEmpty()) {
            return;
        }

        String value = this.masked ? mask(this.content) : this.content;
        TrueTypeFont font = this.getFont();
        int maxWidth = this.getWidthA();
        int maxLines = Math.max(1, this.getHeightA() / this.lineHeight);
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int charWidth = font.getWidth(String.valueOf(c));
            if (lineWidth + charWidth > maxWidth && line.length() > 0) {
                this.lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
                if (this.lines.size() >= maxLines) {
                    this.truncated = true;
                    this.markTruncation();
                    return;
                }
            }

            line.append(c);
            lineWidth += charWidth;
        }

        if (line.length() > 0) {
            this.lines.add(line.toString());
        }
    }

    /** Replaces the tail of the last kept line with an ellipsis. */
    private void markTruncation() {
        int last = this.lines.size() - 1;
        String text = this.lines.get(last);
        this.lines.set(last, text.length() > 1 ? text.substring(0, text.length() - 1) + "…" : "…");
    }

    private static String mask(String value) {
        char[] masked = new char[value.length()];
        java.util.Arrays.fill(masked, MASK_CHAR);
        return new String(masked);
    }

    @Override
    public void draw(float partialTicks) {
        int color = RenderUtil2.applyAlpha(
                this.textColor.getTextColor(),
                partialTicks * RenderUtil2.getAlpha(this.textColor.getTextColor()));

        for (int i = 0; i < this.lines.size(); i++) {
            RenderUtil.drawString(
                    this.getFont(),
                    (float) this.getXA(),
                    (float) (this.getYA() + i * this.lineHeight),
                    this.lines.get(i),
                    color);
        }
    }
}
