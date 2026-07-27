package com.elfmcys.yesstevemodel.client.animation;

import org.apache.commons.lang3.StringUtils;

public class AnimationTracker {
    private String queuedAnimation = "";
    private String currentAnimation = "";
    private String previousAnimation = "";

    public void setQueuedAnimation(String animation) { this.queuedAnimation = animation; }
    public void setCurrentAnimation(String animation) { this.currentAnimation = animation; }
    public void setPreviousAnimation(String animation) { this.previousAnimation = animation; }
    public String getQueuedAnimation() { return this.queuedAnimation; }
    public String getCurrentAnimation() { return this.currentAnimation; }
    public String getPreviousAnimation() { return this.previousAnimation; }

    public boolean hasAnimation() {
        return StringUtils.isNoneBlank(this.currentAnimation);
    }

    public boolean isCurrentAnimation(String animationName) {
        return hasAnimation() && animationName.equals(this.currentAnimation);
    }
}
