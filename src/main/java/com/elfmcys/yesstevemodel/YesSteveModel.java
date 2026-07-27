package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.animation.AnimationRegister;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.IResourceManagerReloadListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class YesSteveModel {
    public static final String MOD_ID = "yes_steve_model";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final OpenYsmModelIndex MODEL_INDEX = new OpenYsmModelIndex();
    private static final OpenYsmResourceReloadListener RELOAD_LISTENER = new OpenYsmResourceReloadListener();
    private static OpenYsmClientConfig clientConfig = new OpenYsmClientConfig();

    private static volatile boolean initialized;
    private static Path configDirectory;

    private YesSteveModel() {
    }

    private static void registerCapabilities() {
        net.minecraft.entity.Entity.registerLazyCapability(
                com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider.PLAYER_CAP,
                entity -> entity instanceof net.minecraft.entity.player.PlayerEntity
                        ? new com.elfmcys.yesstevemodel.capability.PlayerCapability((net.minecraft.entity.player.PlayerEntity) entity)
                        : null);
    }

    public static synchronized void bootstrap(Path gameDirectory) {
        registerCapabilities();
        AnimationRegister.registerAnimationState();
        configDirectory = gameDirectory.resolve("config").resolve(MOD_ID).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configDirectory.resolve("custom"));
            Files.createDirectories(configDirectory.resolve("auth"));
            Files.createDirectories(configDirectory.resolve("cache"));
            loadClientConfig();
            initialized = true;
            LOGGER.info("[YSM] OpenYSM 1.16.4 bridge initialized at {}", configDirectory);
        } catch (IOException exception) {
            initialized = false;
            LOGGER.error("[YSM] Failed to initialize OpenYSM directories", exception);
        }
    }

    public static void reload(IResourceManager resourceManager) {
        if (!initialized || configDirectory == null) {
            LOGGER.warn("[YSM] Reload requested before OpenYSM bridge initialization");
            return;
        }

        MODEL_INDEX.reload(resourceManager, configDirectory);
        ClientModelManager.clearModelState();
        LOGGER.info("[YSM] Indexed {} model entries", MODEL_INDEX.getEntries().size());
    }

    public static boolean isAvailable() {
        return initialized;
    }

    public static boolean isEnabled() {
        return initialized && clientConfig.isEnabled();
    }

    public static Path getConfigDirectory() {
        return configDirectory;
    }

    public static OpenYsmClientConfig getClientConfig() {
        return clientConfig;
    }

    public static OpenYsmModelIndex getModelIndex() {
        return MODEL_INDEX;
    }

    public static Optional<OpenYsmModelEntry> getSelectedModelEntry() {
        return MODEL_INDEX.findById(clientConfig.getSelectedModelId());
    }

    public static void setEnabled(boolean enabled) {
        if (clientConfig.isEnabled() == enabled) {
            return;
        }

        clientConfig.setEnabled(enabled);
        if (!enabled) {
            ClientModelManager.clearModelState();
        }
        saveClientConfig();
    }

    public static void setRenderPlayers(boolean renderPlayers) {
        clientConfig.setRenderPlayers(renderPlayers);
        saveClientConfig();
    }

    public static void setExtraPlayerRender(boolean enabled) {
        clientConfig.setExtraPlayerRender(enabled);
        saveClientConfig();
    }

    public static void setExtraPlayerTransform(int x, int y, float scale, float yawOffset) {
        clientConfig.setExtraPlayerX(x);
        clientConfig.setExtraPlayerY(y);
        clientConfig.setExtraPlayerScale(scale);
        clientConfig.setExtraPlayerYawOffset(yawOffset);
        saveClientConfig();
    }

    public static boolean selectModel(String modelId) {
        return selectModel(modelId, "");
    }

    public static boolean selectModel(String modelId, String textureId) {
        Optional<OpenYsmModelEntry> entry = MODEL_INDEX.findById(modelId);
        if (!entry.isPresent()) {
            return false;
        }

        clientConfig.setSelectedModelId(entry.get().getId());
        clientConfig.setSelectedTextureId(textureId);
        saveClientConfig();
        return true;
    }

    public static IResourceManagerReloadListener getReloadListener() {
        return RELOAD_LISTENER;
    }

    private static void loadClientConfig() throws IOException {
        Path path = clientConfigPath();
        if (!Files.isRegularFile(path)) {
            clientConfig = new OpenYsmClientConfig();
            saveClientConfig();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OpenYsmClientConfig loaded = GSON.fromJson(reader, OpenYsmClientConfig.class);
            clientConfig = loaded != null ? loaded : new OpenYsmClientConfig();
        } catch (JsonParseException exception) {
            LOGGER.warn("[YSM] Invalid client config {}; using defaults", path, exception);
            clientConfig = new OpenYsmClientConfig();
            saveClientConfig();
        }
    }

    public static void saveClientConfig() {
        if (configDirectory == null) {
            return;
        }

        try {
            Files.createDirectories(configDirectory);
            try (Writer writer = Files.newBufferedWriter(clientConfigPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(clientConfig, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("[YSM] Failed to save client config", exception);
        }
    }

    private static Path clientConfigPath() {
        return configDirectory.resolve("client.json").toAbsolutePath().normalize();
    }
}
