package com.elfmcys.yesstevemodel.client.texture;

import com.elfmcys.yesstevemodel.client.compat.oculus.ShadersTextureType;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.resources.IResourceManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class OuterFileTexture extends Texture {
    @Nullable
    private final byte[] data;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    public OuterFileTexture(@Nullable byte[] data) {
        this.data = data;
    }

    @Nullable
    public byte[] getData() {
        return this.data;
    }

    @Override
    public void loadTexture(@NotNull IResourceManager resourceManager) {
        if (this.data == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(this::doLoad);
        } else {
            doLoad();
        }
    }

    public void doLoad() {
        if (this.data == null) {
            return;
        }
        try {
            NativeImage imageIn = NativeImage.read(new ByteArrayInputStream(this.data));
            int width = imageIn.getWidth();
            int height = imageIn.getHeight();
            TextureUtil.prepareImage(this.getGlTextureId(), 0, width, height);
            imageIn.uploadTextureSub(0, 0, 0, 0, 0, width, height, false, false, false, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSuffixTextures(Map<ShadersTextureType, OuterFileTexture> map) {
        this.suffixTextures = Reference2ReferenceMaps.unmodifiable(new Reference2ReferenceOpenHashMap<>(map));
    }

    public Map<ShadersTextureType, ? extends Texture> getSuffixTextures() {
        return this.suffixTextures;
    }
}