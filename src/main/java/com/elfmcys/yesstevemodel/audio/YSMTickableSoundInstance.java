package com.elfmcys.yesstevemodel.audio;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.TickableSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

/**
 * Port of upstream {@code audio/YSMTickableSoundInstance}. Upstream extends 1.20's
 * {@code AbstractTickableSoundInstance}; here it extends 1.16.5's {@link TickableSound}, whose
 * contract maps as: {@code stop()} -> {@code finishPlaying()}, {@code isStopped()} ->
 * {@code isDonePlaying()}, {@code looping} -> {@code repeat}, {@code relative} ->
 * {@code global}, {@code SoundSource.PLAYERS} -> {@code SoundCategory.PLAYERS}, entity removal
 * check -> {@code !entity.isAlive()} (same check the existing {@code SoundEngine.YsmOggSound}
 * seam uses). The 1.16.5 ctor takes no {@code Random} (that arrived in 1.19).
 *
 * <p>Instances for plain {@code "namespace:path"} sound names play through the vanilla resource
 * path; model-bundled audio uses the {@link YSMSoundInstance} subclass, which injects its own
 * stream at the {@code SoundEngine.IStreamedSound} seam.
 */
public class YSMTickableSoundInstance extends TickableSound implements IAudioPlayer {

    public final Entity entity;

    public float targetVolume;

    public YSMTickableSoundInstance(SoundEvent soundEvent, Entity entity) {
        super(soundEvent, SoundCategory.PLAYERS);
        this.targetVolume = 1.0f;
        this.entity = entity;
        this.x = this.entity.getPosX();
        this.y = this.entity.getPosY();
        this.z = this.entity.getPosZ();
    }

    public void tick() {
        this.volume = (this.targetVolume * GeneralConfig.SOUND_VOLUME.get().floatValue()) / 100.0f;
        if (!this.entity.isAlive()) {
            finishPlaying();
            return;
        }
        this.x = this.entity.getPosX();
        this.y = this.entity.getPosY();
        this.z = this.entity.getPosZ();
    }

    public void setVolume(float f) {
        this.targetVolume = f;
    }

    public void setPitch(float f) {
        this.pitch = f;
    }

    public void stopSound() {
        this.attenuationType = AttenuationType.NONE;
        this.global = true;
    }

    @Override
    public void release() {
        finishPlaying();
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().getSoundHandler().stop(this);
        });
    }

    public void setLooping(boolean z) {
        this.repeat = z;
    }

    @Override
    public boolean isStopped() {
        return isDonePlaying();
    }
}
