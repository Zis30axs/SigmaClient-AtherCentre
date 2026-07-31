package com.mentalfrostbyte.jello.util.game.world;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExtendedBlockStateMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int AIR_ID = Block.getStateId(Blocks.AIR.getDefaultState());
    private static final int STONE_ID = Block.getStateId(Blocks.STONE.getDefaultState());
    private static volatile MappingChain mappingChain;
    private static volatile ProtocolVersion mappingVersion;
    /**
     * rawStateId -> native 1.16 block-state id. -1 means "not computed yet".
     * Grown lazily (never shrinks) and swapped atomically.
     */
    private static volatile int[] nativeIdCache = new int[0];
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean();

    private static final int UNCOMPUTED = -1;
    private static final int WARMUP_BOUND = 1 << 16;

    private ExtendedBlockStateMapper() {
    }

    public static BlockState mapToBlockState(int rawStateId) {
        int mappedId = mapToNativeId(rawStateId);
        BlockState state = Block.BLOCK_STATE_IDS.getByValue(mappedId);
        return state != null ? state : Blocks.STONE.getDefaultState();
    }

    public static int mapToNativeId(int rawStateId) {
        if (rawStateId == AIR_ID) {
            return AIR_ID;
        }

        int[] cache = nativeIdCache;
        if (rawStateId >= 0 && rawStateId < cache.length) {
            int cached = cache[rawStateId];
            if (cached != UNCOMPUTED) {
                return cached;
            }
        }

        return slowMapToNativeId(rawStateId);
    }

    private static int slowMapToNativeId(int rawStateId) {
        MappingChain chain = getMappingChain();
        int mappedId = chain.map(rawStateId);
        if (mappedId < 0) {
            return STONE_ID;
        }

        int nativeId = Block.BLOCK_STATE_IDS.getByValue(mappedId) != null ? mappedId : STONE_ID;
        cacheNativeId(rawStateId, nativeId);
        return nativeId;
    }

    /**
     * Populates the fast mapping cache on a background thread so the first
     * chunk data packet does not pay the per-block reflection cost on the
     * Netty event loop. Idempotent.
     */
    public static void warmupAsync() {
        if (WARMUP_STARTED.getAndSet(true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                ensureCacheCapacity(WARMUP_BOUND);
                int[] cache = nativeIdCache;
                for (int rawStateId = 0; rawStateId < cache.length; ++rawStateId) {
                    if (cache[rawStateId] == UNCOMPUTED) {
                        cache[rawStateId] = slowMapToNativeId(rawStateId);
                    }
                }
                LOGGER.info("[ExtendedHeight] Warmed up {} block-state mappings for {}",
                        cache.length, WorldHeightHelper.getTargetVersionSafe().getName());
            } catch (Throwable t) {
                LOGGER.warn("[ExtendedHeight] Block-state warmup failed, falling back to lazy fill", t);
            }
        });
    }

    private static void cacheNativeId(int rawStateId, int nativeId) {
        if (rawStateId < 0) {
            return;
        }

        int[] cache = nativeIdCache;
        if (rawStateId < cache.length) {
            cache[rawStateId] = nativeId;
            return;
        }

        synchronized (ExtendedBlockStateMapper.class) {
            cache = nativeIdCache;
            if (rawStateId >= cache.length) {
                ensureCacheCapacity(Math.max(rawStateId + 1, cache.length * 2));
            }
            nativeIdCache[rawStateId] = nativeId;
        }
    }

    private static void ensureCacheCapacity(int capacity) {
        int[] current = nativeIdCache;
        if (capacity <= current.length) {
            return;
        }

        int[] grown = new int[capacity];
        java.util.Arrays.fill(grown, UNCOMPUTED);
        System.arraycopy(current, 0, grown, 0, current.length);
        nativeIdCache = grown;
    }

    private static MappingChain getMappingChain() {
        ProtocolVersion target = WorldHeightHelper.getTargetVersionSafe();
        MappingChain current = mappingChain;
        if (current != null && target.equals(mappingVersion)) {
            return current;
        }

        synchronized (ExtendedBlockStateMapper.class) {
            current = mappingChain;
            if (current != null && target.equals(mappingVersion)) {
                return current;
            }

            current = new MappingChain(loadMappings(target));
            mappingChain = current;
            mappingVersion = target;
            LOGGER.info("[ExtendedHeight] Loaded {} block-state mapping steps for {}", current.size(), target.getName());
            return current;
        }
    }

    private static List<Object> loadMappings(ProtocolVersion target) {
        List<Object> mappings = new ArrayList<>();
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_11,
                "com.viaversion.viabackwards.protocol.v1_21_11to1_21_9.Protocol1_21_11To1_21_9");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_9,
                "com.viaversion.viabackwards.protocol.v1_21_9to1_21_7.Protocol1_21_9To1_21_7");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_7,
                "com.viaversion.viabackwards.protocol.v1_21_7to1_21_6.Protocol1_21_7To1_21_6");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_6,
                "com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_5,
                "com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_4,
                "com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_2,
                "com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21,
                "com.viaversion.viabackwards.protocol.v1_21to1_20_5.Protocol1_21To1_20_5");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_5,
                "com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_3,
                "com.viaversion.viabackwards.protocol.v1_20_3to1_20_2.Protocol1_20_3To1_20_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_2,
                "com.viaversion.viabackwards.protocol.v1_20_2to1_20.Protocol1_20_2To1_20");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20,
                "com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_4,
                "com.viaversion.viabackwards.protocol.v1_19_4to1_19_3.Protocol1_19_4To1_19_3");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_3,
                "com.viaversion.viabackwards.protocol.v1_19_3to1_19_1.Protocol1_19_3To1_19_1");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_1,
                "com.viaversion.viabackwards.protocol.v1_19_1to1_19.Protocol1_19_1To1_19");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19,
                "com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_18_2,
                "com.viaversion.viabackwards.protocol.v1_18_2to1_18.Protocol1_18_2To1_18");
        addIfNeeded(mappings, target, ProtocolVersion.v1_18,
                "com.viaversion.viabackwards.protocol.v1_18to1_17_1.Protocol1_18To1_17_1");
        addIfNeeded(mappings, target, ProtocolVersion.v1_17,
                "com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4");
        return mappings;
    }

    private static void addIfNeeded(List<Object> mappings, ProtocolVersion target, ProtocolVersion minimum,
            String protocolClassName) {
        if (target.olderThan(minimum)) {
            return;
        }

        try {
            Class<?> protocolClass = Class.forName(protocolClassName);
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object protocol = Via.getManager().getProtocolManager().getProtocol((Class) protocolClass);
            if (protocol == null) {
                return;
            }

            Object mappingData = protocol.getClass().getMethod("getMappingData").invoke(protocol);
            if (mappingData == null) {
                return;
            }

            Object blockStateMappings = mappingData.getClass().getMethod("getBlockStateMappings").invoke(mappingData);
            if (blockStateMappings != null) {
                mappings.add(blockStateMappings);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[ExtendedHeight] Could not load block-state mapping {}: {}", protocolClassName, e.getMessage());
        }
    }

    private static final class MappingChain {
        private final List<MappingStep> steps;

        private MappingChain(List<Object> rawSteps) {
            this.steps = new ArrayList<>(rawSteps.size());
            for (Object rawStep : rawSteps) {
                try {
                    this.steps.add(new MappingStep(rawStep, rawStep.getClass().getMethod("getNewId", int.class)));
                } catch (Exception e) {
                    LOGGER.warn("[ExtendedHeight] Ignoring unusable block-state mapping: {}", e.getMessage());
                }
            }
        }

        private int size() {
            return this.steps.size();
        }

        private int map(int id) {
            int mapped = id;

            for (MappingStep step : this.steps) {
                mapped = step.map(mapped);
                if (mapped < 0) {
                    return -1;
                }
            }

            return mapped;
        }
    }

    private static final class MappingStep {
        private final Object mappings;
        private final Method getNewId;

        private MappingStep(Object mappings, Method getNewId) {
            this.mappings = mappings;
            this.getNewId = getNewId;
        }

        private int map(int id) {
            try {
                return (int) this.getNewId.invoke(this.mappings, id);
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
