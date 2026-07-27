package com.shiroha.mmdskin.render.policy;

import com.shiroha.mmdskin.config.UIConstants;
import com.shiroha.mmdskin.player.sync.PlayerModelSyncService;
import com.shiroha.mmdskin.render.entity.MobReplacementService;
import com.shiroha.mmdskin.render.pipeline.RenderPerformanceProfiler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** 鏂囦欢鑱岃矗锛氬熀浜庡彲瑙佹€т笌璺濈棰勭畻鍐冲畾娓叉煋鏇存柊浼樺厛绾с€?*/
public final class RenderPriorityService {
    private static final RenderPriorityService INSTANCE = new RenderPriorityService();
    private final RenderPerformanceConfig config = ConfigManagerRenderPerformanceConfig.get();

    private final ConcurrentMap<Long, Long> lastAnimationUpdateFrameByModel = new ConcurrentHashMap<>();
    private final Set<UUID> prioritizedVisibleEntities = new HashSet<>();
    private final Set<UUID> prioritizedPhysicsEntities = new HashSet<>();

    private long currentFrameKey = Long.MIN_VALUE;
    private long currentFrameIndex = 0L;
    private int visibleModelsThisFrame = 0;
    private int physicsModelsThisFrame = 0;

    private RenderPriorityService() {
    }

    public static RenderPriorityService get() {
        return INSTANCE;
    }

    public synchronized void beginWorldFrame() {
        long nextFrameKey = computeFrameKey();
        if (nextFrameKey == currentFrameKey) {
            return;
        }

        if (currentFrameKey != Long.MIN_VALUE) {
            RenderPerformanceProfiler.get().completeFrame(visibleModelsThisFrame, physicsModelsThisFrame);
        }

        currentFrameKey = nextFrameKey;
        currentFrameIndex++;
        rebuildPrioritySets();
    }

    public synchronized boolean shouldUsePlayerModel(AbstractClientPlayerEntity player) {
        beginWorldFrame();
        if (player == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getUniqueID().equals(player.getUniqueID())) {
            return true;
        }
        return prioritizedVisibleEntities.contains(player.getUniqueID());
    }

    public synchronized boolean shouldUseMobReplacement(LivingEntity entity) {
        beginWorldFrame();
        return entity != null && prioritizedVisibleEntities.contains(entity.getUniqueID());
    }

    public boolean shouldUpdateAnimation(long modelHandle, double distanceSq, boolean localPlayer) {
        if (localPlayer) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }

        int updateInterval = resolveAnimationUpdateInterval(distanceSq);
        if (updateInterval <= 1) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }

        Long lastFrame = lastAnimationUpdateFrameByModel.get(modelHandle);
        if (lastFrame == null || currentFrameIndex - lastFrame >= updateInterval) {
            lastAnimationUpdateFrameByModel.put(modelHandle, currentFrameIndex);
            return true;
        }

        return false;
    }

    public synchronized boolean shouldEnablePhysics(Entity entity, boolean localPlayer) {
        beginWorldFrame();
        if (!config.isPhysicsEnabled()) {
            return false;
        }
        if (localPlayer) {
            return true;
        }
        return entity != null && prioritizedPhysicsEntities.contains(entity.getUniqueID());
    }

    public double distanceSqToCamera(Entity entity, boolean localPlayer) {
        if (entity == null || localPlayer) {
            return 0.0d;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getRenderViewEntity();
        if (cameraEntity == null) {
            return 0.0d;
        }

        return entity.getDistanceSq(cameraEntity);
    }

    private int resolveAnimationUpdateInterval(double distanceSq) {
        double mediumDistance = config.getAnimationLodMediumDistance();
        double farDistance = config.getAnimationLodFarDistance();
        double mediumSq = mediumDistance * mediumDistance;
        double farSq = farDistance * farDistance;

        if (distanceSq <= mediumSq) {
            return 1;
        }
        if (distanceSq <= farSq) {
            return config.getAnimationLodMediumUpdateInterval();
        }
        return config.getAnimationLodFarUpdateInterval();
    }

    private long computeFrameKey() {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.world != null ? minecraft.world.getGameTime() : 0L;
        long frameTimeBits = Float.floatToRawIntBits(minecraft.getRenderPartialTicks()) & 0xffffffffL;
        return (gameTime << 32) ^ frameTimeBits;
    }

    private void rebuildPrioritySets() {
        prioritizedVisibleEntities.clear();
        prioritizedPhysicsEntities.clear();
        visibleModelsThisFrame = 0;
        physicsModelsThisFrame = 0;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world == null) {
            return;
        }

        List<PrioritizedEntity> candidates = new ArrayList<>();

        for (AbstractClientPlayerEntity player : minecraft.world.getPlayers()) {
            if (shouldConsiderPlayer(player)) {
                boolean localPlayer = minecraft.player != null && minecraft.player.getUniqueID().equals(player.getUniqueID());
                candidates.add(new PrioritizedEntity(player, distanceSqToCamera(player, localPlayer), localPlayer));
            }
        }

        for (Entity entity : minecraft.world.getAllEntities()) {
            if (entity instanceof LivingEntity living && !(entity instanceof AbstractClientPlayerEntity)) {
                String replacementModel = MobReplacementService.getReplacementModelName(living);
                if (replacementModel != null) {
                    candidates.add(new PrioritizedEntity(living, distanceSqToCamera(living, false), false));
                }
            }
        }

        candidates.sort(Comparator
                .comparing(PrioritizedEntity::localPlayer).reversed()
                .thenComparingDouble(PrioritizedEntity::distanceSq));

        int visibleCap = config.getMaxVisibleModelsPerFrame();
        int physicsCap = config.getMaxPhysicsModelsPerFrame();
        double physicsDistance = config.getPhysicsLodMaxDistance();
        double physicsDistanceSq = physicsDistance * physicsDistance;

        for (PrioritizedEntity candidate : candidates) {
            Entity entity = candidate.entity();
            UUID uuid = entity.getUniqueID();
            if (candidate.localPlayer() || visibleCap <= 0 || prioritizedVisibleEntities.size() < visibleCap) {
                prioritizedVisibleEntities.add(uuid);
            }

            if (!config.isPhysicsEnabled()) {
                continue;
            }
            if (candidate.localPlayer()) {
                prioritizedPhysicsEntities.add(uuid);
                continue;
            }
            if (physicsDistance > 0.0d && candidate.distanceSq() > physicsDistanceSq) {
                continue;
            }
            if (physicsCap <= 0 || prioritizedPhysicsEntities.size() < physicsCap) {
                prioritizedPhysicsEntities.add(uuid);
            }
        }

        visibleModelsThisFrame = prioritizedVisibleEntities.size();
        physicsModelsThisFrame = prioritizedPhysicsEntities.size();
    }

    private boolean shouldConsiderPlayer(AbstractClientPlayerEntity player) {
        if (player == null || player.isSpectator()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = minecraft.player != null && minecraft.player.getUniqueID().equals(player.getUniqueID());
        String selectedModel = PlayerModelSyncService.getPlayerModel(player.getUniqueID(), player.getName().getString(), localPlayer);
        return selectedModel != null
                && !selectedModel.isBlank()
                && !UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel);
    }

    private record PrioritizedEntity(Entity entity, double distanceSq, boolean localPlayer) {
    }
}
