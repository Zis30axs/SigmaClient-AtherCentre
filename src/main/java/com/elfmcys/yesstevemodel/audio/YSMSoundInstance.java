package com.elfmcys.yesstevemodel.audio;

import net.minecraft.client.audio.IAudioStream;
import net.minecraft.client.audio.SoundEngine;
import net.minecraft.util.SoundEvent;
import net.minecraft.entity.Entity;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/**
 * Port of upstream {@code audio/YSMSoundInstance}: a {@link YSMTickableSoundInstance} that
 * supplies its own {@link IAudioStream} for model-bundled audio instead of a resource-pack
 * file. Upstream overrides 1.20's {@code SoundInstance#getStream(SoundBufferLibrary, Sound,
 * boolean)}; 1.16.5 has no such overridable method, so the stream is injected through the
 * {@link SoundEngine.IStreamedSound} seam (the same decompiled-source edit the existing
 * {@code YsmOggSound} path uses): when the resolved {@code Sound} has {@code stream: true}
 * (guaranteed by {@code assets/yes_steve_model/sounds.json}), the engine calls
 * {@link #openStream()} on the sound executor instead of opening a file.
 *
 * <p>The {@code looping} decision mirrors upstream's {@code z} parameter: it is read from
 * {@code this.repeat}, which the {@code setLooping} callback sets before the manager schedules
 * the play, so by the time the executor calls {@code openStream()} the flag is final.
 */
public class YSMSoundInstance extends YSMTickableSoundInstance implements SoundEngine.IStreamedSound {

    private final IAudioStreamFactory streamFactory;

    private volatile IAudioStreamSupport audioStream;

    public YSMSoundInstance(SoundEvent soundEvent, IAudioStreamFactory streamFactory2, Entity entity) {
        super(soundEvent, entity);
        this.streamFactory = streamFactory2;
    }

    @Override
    public IAudioStream openStream() throws IOException, UnsupportedAudioFileException {
        IAudioStreamSupport audioStreamSupport = this.repeat
                ? new AudioStreamWrapper(this.streamFactory)
                : this.streamFactory.openStream();
        this.audioStream = audioStreamSupport;
        return audioStreamSupport;
    }

    @Override
    public boolean isStopped() {
        if (this.audioStream == null) {
            return isDonePlaying();
        }
        if (this.audioStream.isClosed()) {
            if (!isDonePlaying()) {
                release();
            }
            return true;
        }
        return isDonePlaying();
    }
}
