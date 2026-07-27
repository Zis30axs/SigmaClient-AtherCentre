package com.elfmcys.yesstevemodel;

import java.util.LinkedHashSet;
import java.util.Set;

public final class OpenYsmClientConfig {
    private boolean enabled = true;
    private boolean renderPlayers = true;
    private boolean extraPlayerRender = false;
    private int extraPlayerX = 10;
    private int extraPlayerY = 10;
    private float extraPlayerScale = 40.0F;
    private float extraPlayerYawOffset = 5.0F;
    private String selectedModelId = "misc/2_steve";
    private String selectedTextureId = "";
    /**
     * Upstream {@code GeneralConfig.SHOW_MODEL_ID_FIRST} (model picker shows the file id instead
     * of the localized display name).
     */
    private boolean showModelIdFirst = false;
    /**
     * Upstream {@code GeneralConfig.DISCLAIMER_SHOW}: the disclaimer is shown once before the
     * first opening of the model picker, until the user ticks "read".
     */
    private boolean disclaimerShow = true;
    /**
     * Local stand-in for upstream's server-synced {@code StarModelsCapability}: the standalone
     * client keeps starred (favourite) models in the client config instead.
     */
    private Set<String> starModels = new LinkedHashSet<>();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRenderPlayers() {
        return this.renderPlayers;
    }

    public void setRenderPlayers(boolean renderPlayers) {
        this.renderPlayers = renderPlayers;
    }

    public boolean isExtraPlayerRender() {
        return this.extraPlayerRender;
    }

    public void setExtraPlayerRender(boolean extraPlayerRender) {
        this.extraPlayerRender = extraPlayerRender;
    }

    public int getExtraPlayerX() {
        return this.extraPlayerX;
    }

    public void setExtraPlayerX(int extraPlayerX) {
        this.extraPlayerX = Math.max(0, extraPlayerX);
    }

    public int getExtraPlayerY() {
        return this.extraPlayerY;
    }

    public void setExtraPlayerY(int extraPlayerY) {
        this.extraPlayerY = Math.max(0, extraPlayerY);
    }

    public float getExtraPlayerScale() {
        return this.extraPlayerScale <= 0.0F ? 40.0F : this.extraPlayerScale;
    }

    public void setExtraPlayerScale(float extraPlayerScale) {
        this.extraPlayerScale = Math.max(8.0F, Math.min(360.0F, extraPlayerScale));
    }

    public float getExtraPlayerYawOffset() {
        return this.extraPlayerYawOffset;
    }

    public void setExtraPlayerYawOffset(float extraPlayerYawOffset) {
        this.extraPlayerYawOffset = extraPlayerYawOffset;
    }

    public String getSelectedModelId() {
        return this.selectedModelId == null || this.selectedModelId.isEmpty() ? "misc/2_steve" : this.selectedModelId;
    }

    public void setSelectedModelId(String selectedModelId) {
        this.selectedModelId = selectedModelId;
    }

    public String getSelectedTextureId() {
        return this.selectedTextureId == null ? "" : this.selectedTextureId;
    }

    public void setSelectedTextureId(String selectedTextureId) {
        this.selectedTextureId = selectedTextureId == null ? "" : selectedTextureId;
    }

    public boolean isShowModelIdFirst() {
        return this.showModelIdFirst;
    }

    public void setShowModelIdFirst(boolean showModelIdFirst) {
        this.showModelIdFirst = showModelIdFirst;
    }

    public boolean isDisclaimerShow() {
        return this.disclaimerShow;
    }

    public void setDisclaimerShow(boolean disclaimerShow) {
        this.disclaimerShow = disclaimerShow;
    }

    public Set<String> getStarModels() {
        if (this.starModels == null) {
            this.starModels = new LinkedHashSet<>();
        }
        return this.starModels;
    }

    public void setStarModels(Set<String> starModels) {
        this.starModels = starModels == null ? new LinkedHashSet<>() : starModels;
    }
}
