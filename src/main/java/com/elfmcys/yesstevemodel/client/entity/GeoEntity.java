package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.audio.AudioCodec;
import com.elfmcys.yesstevemodel.audio.AudioStreamCache;
import com.elfmcys.yesstevemodel.audio.AudioTrackData;
import com.elfmcys.yesstevemodel.audio.IAudioStreamFactory;
import com.elfmcys.yesstevemodel.audio.IAudioStreamProvider;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangEventDispatcher;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.compat.oculus.OculusCompat;
import com.elfmcys.yesstevemodel.client.animation.molang.PhysicsManager;
import com.elfmcys.yesstevemodel.client.renderer.AnimationDebugOverlay;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.AnimationProcessor;
import com.elfmcys.yesstevemodel.util.log.ChatLogger;
import com.elfmcys.yesstevemodel.util.log.ILogger;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public abstract class GeoEntity<T extends Entity> extends AnimatableEntity<T> {
    private String modelId;
    private ModelAssembly modelAssembly;
    private ModelWrapper renderShape;
    private boolean loaded;
    private int updateTicks;

    @Nullable
    private PhysicsManager bones;

    @Nullable
    private List<IValue> renderLayers;

    @Nullable
    public abstract GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean z);

    public abstract GeoModel getAnimationProcessor();

    public GeoEntity(T t, boolean z) {
        super(t);
        this.modelId = "default";
        if (z) {
            EntityRenderCache.register(this);
        }
    }

    @Override
    public PhysicsManager getPhysicsManager() {
        if (ModelPreviewRenderer.isFirstPerson() || ModelPreviewRenderer.isExtraPlayer()) {
            return this.physicsManager;
        }
        if (this.bones == null) {
            this.bones = new PhysicsManager();
        }
        return this.bones;
    }

    @Nullable
    public List<IValue> getRenderLayers() {
        return this.renderLayers;
    }

    @Override
    public void setupAnim(float seekTime, boolean z) {
        super.setupAnim(seekTime, z);
    }

    public void tickModel() {
        if (this.updateTicks < this.entity.ticksExisted) {
            refreshModel();
            this.updateTicks = this.entity.ticksExisted;
        }
    }

    public final ModelAssembly getModelAssembly() {
        return this.modelAssembly;
    }

    public final void setModelId(String str) {
        this.modelId = str;
        refreshModel();
    }

    private void refreshModel() {
        Optional<ModelAssembly> opt = ClientModelManager.getModelContext(this.modelId);
        if (opt.isPresent()) {
            ModelAssembly assembly = opt.get();
            if (this.renderShape == null || this.renderShape.isDefault || assembly != this.renderShape.context) {
                this.renderShape = buildRenderShape(assembly, false);
            }
        } else {
            ModelAssembly modelAssembly = ClientModelManager.getLocalModelContext();
            if (this.renderShape == null || !this.renderShape.isDefault || modelAssembly != this.renderShape.context) {
                this.renderShape = buildRenderShape(modelAssembly, true);
            }
        }
        if (this.renderShape != null) {
            if ((this.renderShape.context != this.modelAssembly || this.renderShape.isDefault != this.loaded) && this.renderShape.isValid()) {
                this.modelAssembly = this.renderShape.context;
                this.loaded = this.renderShape.isDefault;
                onModelLoaded(this.modelAssembly);
                initAnimationControllers(getAnimationProcessor(), this.modelAssembly.getExpressionCache().getEvents());
                return;
            }
            return;
        }
        if (this.modelAssembly != null) {
            clearModel();
        }
    }

    public final ModelWrapper getRenderShape() {
        return this.renderShape;
    }

    public void onModelLoaded(ModelAssembly modelAssembly) {
        this.renderShape.audioProvider = AudioStreamCache.getOrCreateProvider(modelAssembly);
        this.renderLayers = modelAssembly.getExpressionCache().getEvents().get(MolangEventDispatcher.DEFER);
    }

    public void clearModel() {
        this.modelAssembly = null;
        this.renderLayers = null;
        this.renderShape = null;
        this.loaded = false;
        reset();
    }

    @Override
    public void reset() {
        super.reset();
        this.bones = null;
        this.updateTicks = 0;
    }

    public void resetModel() {
        this.modelId = "default";
        this.modelInitialized = false;
        clearModel();
    }

    public final String getModelId() {
        return this.modelId;
    }

    @Override
    public boolean isModelReady() {
        return this.renderShape != null && !this.renderShape.isDefault && this.renderShape.isValid();
    }

    @Override
    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return event.isFirstPerson() || OculusCompat.isShaderActive();
    }

    @Override
    @Nullable
    public final IValue resolveExpression(String str) {
        return getModelAssembly().getExpressionCache().getFunctions().get(str);
    }

    /**
     * Upstream {@code GeoEntity#getAudioStreamFactory} (W5): resolves a model-bundled audio
     * track by name and hands out a factory that decodes it (via the per-assembly
     * {@link AudioStreamCache} provider) each time a new play starts.
     */
    @Override
    public Optional<IAudioStreamFactory> getAudioStreamFactory(String str) {
        AudioTrackData trackData;
        if (this.renderShape != null && this.renderShape.audioProvider != null
                && (trackData = getModelAssembly().getExpressionCache().getSoundEffects().get(str)) != null
                && trackData.getData() != null && trackData.getCodec() != AudioCodec.UNDEFINED) {
            IAudioStreamProvider streamProvider = this.renderShape.audioProvider;
            return Optional.of(() -> streamProvider.createAudioStream(trackData));
        }
        return Optional.empty();
    }

    @Override
    public ILogger getLogger() {
        if (AnimationDebugOverlay.isDebugActive()) {
            return ChatLogger.INSTANCE;
        }
        return null;
    }

    public boolean supportsAsync() {
        return false;
    }

    public static class ModelWrapper {
        public final ModelAssembly context;
        public final boolean isDefault;
        public IAudioStreamProvider audioProvider;

        public ModelWrapper(ModelAssembly modelAssembly, boolean z) {
            this.context = modelAssembly;
            this.isDefault = z;
        }

        public boolean isValid() {
            return true;
        }
    }

    public void submitAsyncUpdate(float partialTick) {
        // Async disabled in standalone client - no-op
    }

    public AnimationEvent<?> awaitAsyncResult() {
        return null;
    }
}
