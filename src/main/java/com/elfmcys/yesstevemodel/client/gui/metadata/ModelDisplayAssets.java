package com.elfmcys.yesstevemodel.client.gui.metadata;

import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import net.minecraft.client.renderer.texture.Texture;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ModelDisplayAssets {
    private final String selectedTexture;

    private boolean isAuthModel;

    private final Map<String, OuterFileTexture> authorAvatars;

    private final Map<String, Texture> guiTextures;

    public ModelDisplayAssets(String selectedTexture, boolean isAuth, Map<String, OuterFileTexture> authorAvatars, Map<String, Texture> guiTextures) {
        this.selectedTexture = selectedTexture;
        this.isAuthModel = isAuth;
        this.authorAvatars = authorAvatars;
        this.guiTextures = guiTextures;
    }

    public String getSelectedTexture() {
        return this.selectedTexture;
    }

    public boolean isAuthModel() {
        return this.isAuthModel;
    }

    public void setAuthModel(boolean isModelReady) {
        this.isAuthModel = isModelReady;
    }

    public Map<String, OuterFileTexture> getAuthorAvatars() {
        return this.authorAvatars;
    }

    @Nullable
    public Texture getGuiForeground() {
        return this.guiTextures.get("gui_foreground");
    }

    @Nullable
    public Texture getGuiBackground() {
        return this.guiTextures.get("gui_background");
    }
}