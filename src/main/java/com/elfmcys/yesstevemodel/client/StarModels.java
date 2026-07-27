package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;

import java.util.Set;

/**
 * Local stand-in for upstream's {@code StarModelsCapability} (1.20.1).
 *
 * <p>Upstream syncs starred (favourite) models to the server via
 * {@code C2SSetStarModelPacket}; this standalone client keeps them in the client config instead
 * ({@code OpenYsmClientConfig#starModels}), persisted on every change. The capability's
 * {@code LazyOptional.ifPresent} call sites in the ported GUI collapse to plain static calls
 * because the store is always present locally.
 */
public final class StarModels {

    private StarModels() {
    }

    public static boolean containsModel(String modelId) {
        return modelId != null && store().contains(modelId);
    }

    public static void addModel(String modelId) {
        if (modelId != null && store().add(modelId)) {
            YesSteveModel.saveClientConfig();
        }
    }

    public static void removeModel(String modelId) {
        if (modelId != null && store().remove(modelId)) {
            YesSteveModel.saveClientConfig();
        }
    }

    private static Set<String> store() {
        return YesSteveModel.getClientConfig().getStarModels();
    }
}
