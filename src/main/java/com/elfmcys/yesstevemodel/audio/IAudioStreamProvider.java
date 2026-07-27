package com.elfmcys.yesstevemodel.audio;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

/** Upstream {@code audio/IAudioStreamProvider} (verbatim). */
public interface IAudioStreamProvider {
    IAudioStreamSupport createAudioStream(AudioTrackData trackData) throws UnsupportedAudioFileException, IOException;
}
