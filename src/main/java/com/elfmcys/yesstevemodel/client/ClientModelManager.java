package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.OpenYsmArchiveUtil;
import com.elfmcys.yesstevemodel.OpenYsmClientConfig;
import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.model.ModelAssemblyFactory;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMModelMapper;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import rip.ysm.security.YsmCrypt;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reduced 1.16.5 port of OpenYSM 2.6.5 {@code ClientModelManager}.
 *
 * <p>The upstream class is dominated by server-side model sync (network packets,
 * {@code ServerModelManager}, sync progress GUI). That whole surface is cut for the
 * standalone client. What remains is the client-local loading chain:
 * <pre>
 *   builtin folder -> YSMFolderDeserializer -> RawYsmModel
 *                  -> YSMModelMapper.buildParsedBundle -> ClientModelInfo
 *                  -> ModelAssemblyFactory.buildAssembly -> ModelAssembly (cached)
 * </pre>
 * {@link #getModelContext(String)} / {@link #getLocalModelContext()} are the seams
 * consumed by {@code GeoEntity.refreshModel()}.
 */
public class ClientModelManager {

    private static volatile ModelAssembly localModelContext;

    private static volatile Runnable pendingModelCallback;

    private static volatile Map<String, ModelAssembly> modelAssemblyMap = new Object2ReferenceOpenHashMap<>();

    private static final ConcurrentLinkedQueue<Pair<ModelAssembly, String>> pendingModelQueue = new ConcurrentLinkedQueue<>();

    /**
     * Models that failed to load in this resource-reload cycle. Prevents a broken
     * selection from re-attempting (and re-logging) every frame. Cleared on reload.
     */
    private static final Set<String> failedModelIds = ConcurrentHashMap.newKeySet();

    public static ResourceLocation getDefaultTexture() {
        // PORT-REVIEW: upstream returns the primary model's first texture via
        // UploadManager.getOrCreateLocatable (client/upload, cut surface). The static
        // fallback keeps callers working until texture registration is wired.
        return new ResourceLocation(YesSteveModel.MOD_ID, "textures/default.png");
    }

    public static Optional<ModelAssembly> getModelContext(String modelId) {
        settlePendingModels();
        return Optional.ofNullable(modelAssemblyMap.get(modelId));
    }

    /**
     * Upstream {@code getModelAssemblyMap()}: the live assembly registry. Here it only contains
     * what has been loaded on demand; {@link #ensureAllModelsLoaded()} fills it for the model
     * picker GUI.
     */
    public static Map<String, ModelAssembly> getModelAssemblyMap() {
        settlePendingModels();
        return modelAssemblyMap;
    }

    /**
     * Upstream {@code getModelPackMap()}. Deviation: always empty - the on-demand loader does not
     * parse {@code ysm_pack.json}, so no real pack metadata exists. {@code PlayerModelScreen}
     * synthesizes its folder hierarchy from model paths instead (its own
     * {@code ensurePackHierarchy}, same as upstream does for pathless models).
     */
    public static Map<String, com.elfmcys.yesstevemodel.resource.models.ModelPackData> getModelPackMap() {
        return Collections.emptyMap();
    }

    /**
     * Loads every model in the index into the assembly registry. Called by the model picker on
     * open; loads are synchronous, deduplicated and failure-sticky ({@link #failedModelIds}), so
     * repeat opens are cheap.
     */
    public static void ensureAllModelsLoaded() {
        for (OpenYsmModelEntry entry : YesSteveModel.getModelIndex().getEntries()) {
            ensureModelLoaded(entry.getId());
        }
    }

    /**
     * Binds the locally selected model (client config) to a player capability and
     * kicks on-demand loading. Called once per render before the ready check;
     * {@code initModelWithTexture} is only re-applied when the configured
     * model/texture selection actually changed.
     */
    public static void syncSelectedModel(PlayerCapability capability) {
        OpenYsmClientConfig config = YesSteveModel.getClientConfig();
        String modelId = config.getSelectedModelId();
        String textureId = config.getSelectedTextureId();
        ensureModelLoaded(modelId);
        boolean modelChanged = !modelId.equals(capability.getModelId());
        boolean textureChanged = !textureId.equals(capability.currentTextureName);
        if (modelChanged || textureChanged) {
            capability.initModelWithTexture(modelId, textureId);
        }
    }

    /**
     * Loads the given model into the assembly registry on demand. The primary
     * ("default") assembly is always built first because
     * {@link ModelAssemblyFactory}'s inheritance logic expects it to exist.
     * Loads are synchronous and deduplicated: a model already in the registry or
     * in {@link #failedModelIds} is never re-attempted.
     */
    public static void ensureModelLoaded(String modelId) {
        if (modelId == null || modelId.isEmpty() || failedModelIds.contains(modelId)) {
            return;
        }
        settlePendingModels();
        if (modelAssemblyMap.containsKey(modelId)) {
            return;
        }
        boolean isDefault = "default".equals(modelId);
        if (!isDefault) {
            ensureModelLoaded("default");
            if (modelAssemblyMap.containsKey(modelId) || failedModelIds.contains(modelId)) {
                return;
            }
        }
        loadModelEntry(modelId, isDefault);
        settlePendingModels();
        if (!modelAssemblyMap.containsKey(modelId)) {
            failedModelIds.add(modelId);
        }
    }

    /**
     * Clears all loaded model state. Called from the resource reload listener so
     * builtin/custom model changes are picked up; capabilities re-bind on their
     * next tick via {@link #syncSelectedModel(PlayerCapability)}.
     */
    public static void clearModelState() {
        pendingModelCallback = null;
        pendingModelQueue.clear();
        modelAssemblyMap = new Object2ReferenceOpenHashMap<>();
        localModelContext = null;
        failedModelIds.clear();
    }

    /**
     * Settles deferred primary-model callbacks and the pending queue before a
     * registry lookup. A failing primary build is isolated here so the render
     * thread never sees the exception; "default" is marked failed to stop
     * per-frame retries.
     */
    private static void settlePendingModels() {
        try {
            runPendingModelCallback();
            flushPendingModels();
        } catch (RuntimeException exception) {
            failedModelIds.add("default");
            YesSteveModel.LOGGER.error("[YSM] Failed to assemble primary model", exception);
        }
    }

    private static void loadModelEntry(String modelId, boolean isPrimary) {
        Optional<OpenYsmModelEntry> entryOptional = YesSteveModel.getModelIndex().findById(modelId);
        if (!entryOptional.isPresent()) {
            // Index miss (selection stale or reload not run yet): fall back to the
            // builtin classpath probe, which also covers "default".
            if (YesSteveModel.class.getResource("/assets/" + YesSteveModel.MOD_ID + "/builtin/" + modelId + "/ysm.json") != null) {
                loadBuiltinModel(modelId, isPrimary);
            } else {
                failedModelIds.add(modelId);
                YesSteveModel.LOGGER.warn("[YSM] Model {} not found in index; skipping load", modelId);
            }
            return;
        }
        OpenYsmModelEntry entry = entryOptional.get();
        if (entry.getSourceType() == OpenYsmModelEntry.SourceType.BUILTIN) {
            loadBuiltinModel(modelId, isPrimary);
            return;
        }
        loadExternalModel(entry, isPrimary);
    }

    private static void loadExternalModel(OpenYsmModelEntry entry, boolean isPrimary) {
        Path path = entry.getPath();
        if (path == null) {
            failedModelIds.add(entry.getId());
            return;
        }
        boolean isAuth = entry.getSourceType() == OpenYsmModelEntry.SourceType.AUTH;
        try {
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.equals("ysm.json")) {
                loadFolderModel(path.getParent(), entry.getId(), isPrimary, isAuth);
            } else if (fileName.endsWith(".zip")) {
                loadZipModel(path, entry.getId(), isPrimary, isAuth);
            } else if (fileName.endsWith(".ysm")) {
                loadBinaryModel(path, entry.getId(), isPrimary, isAuth);
            } else {
                throw new IOException("Unsupported model source: " + path);
            }
        } catch (Exception exception) {
            failedModelIds.add(entry.getId());
            YesSteveModel.LOGGER.error("[YSM] Failed to load model {} from {}", entry.getId(), path, exception);
        }
    }

    private static void loadFolderModel(Path folder, String modelId, boolean isPrimary, boolean isAuth) throws IOException {
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(folder)) {
            RawYsmModel rawModel = deserializer.deserialize();
            onModelDataReceived(YSMModelMapper.buildParsedBundle(rawModel, modelId), modelId, isPrimary, isAuth);
        }
    }

    /**
     * The index accepts zips with a nested {@code ysm.json}, so the zip filesystem
     * is mounted at the actual model root instead of "/" before deserializing.
     */
    private static void loadZipModel(Path archive, String modelId, boolean isPrimary, boolean isAuth) throws IOException {
        String rootPrefix;
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipEntry ysmJson = OpenYsmArchiveUtil.findYsmJson(zipFile);
            if (ysmJson == null) {
                throw new IOException("Missing ysm.json in " + archive);
            }
            rootPrefix = OpenYsmArchiveUtil.rootPrefix(ysmJson);
        }
        URI uri = URI.create("jar:" + archive.toUri());
        FileSystem fileSystem;
        boolean ownsFileSystem;
        try {
            fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
            ownsFileSystem = true;
        } catch (FileSystemAlreadyExistsException exception) {
            fileSystem = FileSystems.getFileSystem(uri);
            ownsFileSystem = false;
        }
        try {
            Path modelRoot = rootPrefix.isEmpty() ? fileSystem.getPath("/") : fileSystem.getPath(rootPrefix);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(modelRoot)) {
                RawYsmModel rawModel = deserializer.deserialize();
                onModelDataReceived(YSMModelMapper.buildParsedBundle(rawModel, modelId), modelId, isPrimary, isAuth);
            }
        } finally {
            if (ownsFileSystem) {
                fileSystem.close();
            }
        }
    }

    private static void loadBinaryModel(Path file, String modelId, boolean isPrimary, boolean isAuth) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file);
        if (fileBytes.length > 50 * 1024 * 1024) {
            throw new IOException("File too large: " + file);
        }
        byte[] decompressed = YsmCrypt.decryptYsmFile(fileBytes);
        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decompressed)) {
            RawYsmModel rawModel = deserializer.deserialize();
            onModelDataReceived(YSMModelMapper.buildParsedBundle(rawModel, modelId), modelId, isPrimary, isAuth);
        }
    }

    public static ModelAssembly getLocalModelContext() {
        runPendingModelCallback();
        flushPendingModels();

        ModelAssembly model = localModelContext;
        if (model != null) return model;

        loadDefaultModel();
        runPendingModelCallback();
        flushPendingModels();

        model = localModelContext;
        if (model != null) return model;

        Map<String, ModelAssembly> reg = modelAssemblyMap;
        if (reg != null && !reg.isEmpty()) {
            model = reg.get("default");
            if (model == null) {
                for (ModelAssembly v : reg.values()) {
                    if (v != null) {
                        model = v;
                        break;
                    }
                }
            }
            if (model != null) {
                localModelContext = model;
                return model;
            }
        }
        return null;
    }

    public static void loadDefaultModel() {
        loadBuiltinModel("default", true);
    }

    public static void loadBuiltinModel(String id, boolean isPrimary) {
        YesSteveModel.LOGGER.info("[YSM] Loading builtin model: {}", id);
        String resourcePath = "/assets/" + YesSteveModel.MOD_ID + "/builtin/" + id;
        try {
            URL resourceUrl = YesSteveModel.class.getResource(resourcePath);
            if (resourceUrl == null) {
                YesSteveModel.LOGGER.error("[YSM] Builtin model not found in classpath: {}", resourcePath);
                return;
            }
            URI uri = resourceUrl.toURI();
            Path folderPath;
            if ("jar".equals(uri.getScheme())) {
                FileSystem jarFs;
                try {
                    jarFs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                folderPath = jarFs.getPath(resourcePath);
            } else {
                folderPath = Paths.get(uri);
            }

            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(folderPath)) {
                RawYsmModel rawModel = deserializer.deserialize();
                ClientModelInfo parsedBundle = YSMModelMapper.buildParsedBundle(rawModel, id);
                onModelDataReceived(parsedBundle, id, isPrimary, false);
                YesSteveModel.LOGGER.info("[YSM] Successfully queued builtin model: {}", id);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[YSM] Failed to load builtin model: {}", id, e);
        }
    }

    private static void onModelDataReceived(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        if (isPrimary) {
            pendingModelCallback = () -> processModelData(parsedBundle, modelId, true, false);
        } else {
            runPendingModelCallback();
            processModelData(parsedBundle, modelId, false, isAuth);
        }
    }

    public static void runPendingModelCallback() {
        Runnable runnable = pendingModelCallback;
        if (runnable != null) {
            synchronized (runnable) {
                Runnable runnable2 = pendingModelCallback;
                if (runnable2 != null) {
                    runnable2.run();
                    pendingModelCallback = null;
                }
            }
        }
    }

    public static void processModelData(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        if (parsedBundle == null) return;
        try {
            ModelAssembly runtimeModel = ModelAssemblyFactory.buildAssembly(parsedBundle, isPrimary, isAuth);
            pendingModelQueue.add(Pair.of(runtimeModel, modelId));
            if (isPrimary) {
                localModelContext = runtimeModel;
                // PORT-REVIEW: upstream registers defaultTexture here via
                // UploadManager.getOrCreateLocatable(runtimeModel...getTextures().getValueAt(0)).
            }
        } catch (Exception e) {
            if (isPrimary) {
                if (e instanceof RuntimeException runtimeException) throw runtimeException;
                throw new RuntimeException(e);
            }
            YesSteveModel.LOGGER.error("[YSM] Failed to process {}", modelId, e);
        }
    }

    public static void flushPendingModels() {
        if (pendingModelQueue.isEmpty()) return;
        Object2ReferenceOpenHashMap<String, ModelAssembly> next = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        while (true) {
            Pair<ModelAssembly, String> pair = pendingModelQueue.poll();
            if (pair != null) {
                next.put(pair.getRight(), pair.getLeft());
            } else {
                modelAssemblyMap = next;
                return;
            }
        }
    }
}
