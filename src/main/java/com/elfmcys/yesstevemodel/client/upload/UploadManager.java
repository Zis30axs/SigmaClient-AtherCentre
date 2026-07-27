package com.elfmcys.yesstevemodel.client.upload;

import com.elfmcys.yesstevemodel.ResourceCleanupHelper;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ReferenceIntMutablePair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.time.StopWatch;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.16.5 port of OpenYSM 2.6.5 UploadManager: registers baked model textures with the
 * vanilla TextureManager and expires them after their locatable is garbage collected.
 *
 * <p>MCP mapping notes: {@code AbstractTexture} -> {@link Texture},
 * {@code TextureManager#register} -> {@code loadTexture}, {@code #release} ->
 * {@code deleteTexture}, {@code RenderSystem.assertOnRenderThread} ->
 * {@code assertThread(isOnGameThread)}. Suffix-texture traversal uses
 * {@link OuterFileTexture} directly instead of upstream's ITextureMap interface.
 */
public class UploadManager {

    private static final int UPLOAD_TIME_LIMIT_MS = 20;

    private static long textureCounter = 0;

    private static final IdentityHashMap<Texture, WeakReference<TextureLocatable>> textureCache = new IdentityHashMap<>();

    private static final Queue<Pair<TextureLocatable, Texture>> pendingUploads = Queues.newArrayDeque();

    private static final ConcurrentHashMap<Texture, ReferenceIntMutablePair<ResourceLocation>> expiredTextures = new ConcurrentHashMap<>();

    private static final Queue<ResourceLocation> pendingReleases = Queues.newArrayDeque();

    public static IResourceLocatable getOrCreateLocatable(ResourceLocation location, boolean registerImmediately) {
        return () -> location;
    }

    public static IResourceLocatable getOrCreateLocatable(Texture texture, boolean registerImmediately) {
        return getOrCreateLocatableWithSize(texture, registerImmediately, 200);
    }

    public static IResourceLocatable getOrCreateLocatableWithSize(ResourceLocation location, boolean registerImmediately, int size) {
        return () -> location;
    }

    public static IResourceLocatable getOrCreateLocatableWithSize(Texture texture, boolean registerImmediately, int size) {
        RenderSystem.assertThread(RenderSystem::isOnGameThread);
        WeakReference<TextureLocatable> weakReference = textureCache.get(texture);
        if (weakReference != null) {
            TextureLocatable locatable = weakReference.get();
            if (locatable != null) {
                if (registerImmediately && !locatable.registered) {
                    registerTexture(texture, locatable);
                }
                return locatable;
            }
            textureCache.remove(texture);
        }
        ReferenceIntMutablePair<ResourceLocation> removed = expiredTextures.remove(texture);
        TextureLocatable locatable;
        if (removed != null) {
            locatable = new TextureLocatable(removed.first(), size);
        } else {
            locatable = new TextureLocatable(size);
        }
        if (texture instanceof OuterFileTexture) {
            for (Texture suffixTexture : ((OuterFileTexture) texture).getSuffixTextures().values()) {
                if (locatable.suffixTextures == null) {
                    locatable.suffixTextures = new ArrayList<>(2);
                }
                locatable.suffixTextures.add(getOrCreateLocatableWithSize(suffixTexture, registerImmediately, size));
            }
        }
        textureCache.put(texture, new WeakReference<>(locatable));
        if (registerImmediately) {
            registerTexture(texture, locatable);
        } else {
            pendingUploads.add(Pair.of(locatable, texture));
        }
        return locatable;
    }

    public static void removeTexture(Texture texture) {
        RenderSystem.assertThread(RenderSystem::isOnGameThread);
        textureCache.remove(texture);
    }

    /**
     * Drains deferred texture registrations and expired-texture releases. Called once
     * per client tick from {@code Minecraft.runTick} (upstream: ClientTickEvent).
     */
    public static void processPendingUploads() {
        RenderSystem.assertThread(RenderSystem::isOnGameThread);
        if (!expiredTextures.isEmpty()) {
            Iterator<Map.Entry<Texture, ReferenceIntMutablePair<ResourceLocation>>> iterator = expiredTextures.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Texture, ReferenceIntMutablePair<ResourceLocation>> next = iterator.next();
                int countdown = next.getValue().secondInt();
                if (countdown <= 0) {
                    pendingReleases.add(next.getValue().first());
                    iterator.remove();
                } else {
                    next.getValue().second(countdown - 1);
                }
            }
        }
        StopWatch stopWatch = StopWatch.createStarted();
        do {
            Pair<TextureLocatable, Texture> pair = pendingUploads.poll();
            if (pair != null) {
                registerTexture(pair.right(), pair.left());
            } else {
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                do {
                    ResourceLocation location = pendingReleases.poll();
                    if (location != null) {
                        textureManager.deleteTexture(location);
                    } else {
                        return;
                    }
                } while (stopWatch.getTime() < UPLOAD_TIME_LIMIT_MS);
                return;
            }
        } while (stopWatch.getTime() < UPLOAD_TIME_LIMIT_MS);
    }

    private static void registerTexture(Texture texture, TextureLocatable locatable) {
        if (!locatable.registered) {
            Minecraft.getInstance().getTextureManager().loadTexture(locatable.resourceLocation, texture);
            ResourceCleanupHelper.registerBiCleanup(locatable, locatable.resourceLocation, locatable.resolution,
                    (resourceLocation, num) -> expiredTextures.put(texture, ReferenceIntMutablePair.of(resourceLocation, num)));
            locatable.markRegistered();
        }
    }

    private static class TextureLocatable implements IResourceLocatable {

        private final ResourceLocation resourceLocation;

        private final int resolution;

        private List<IResourceLocatable> suffixTextures;

        private volatile boolean registered;

        public TextureLocatable(ResourceLocation resourceLocation, int resolution) {
            this.resourceLocation = resourceLocation;
            this.resolution = resolution;
        }

        TextureLocatable(int resolution) {
            this.resourceLocation = new ResourceLocation(YesSteveModel.MOD_ID, "textures/" + (++textureCounter));
            this.resolution = resolution;
            this.registered = false;
        }

        @Override
        public ResourceLocation getLocation() {
            return this.resourceLocation;
        }

        @Override
        public Optional<ResourceLocation> getResourceLocation() {
            return this.registered ? Optional.of(this.resourceLocation) : Optional.empty();
        }

        public void markRegistered() {
            this.registered = true;
        }
    }
}
