package com.elfmcys.yesstevemodel.audio;

/** Upstream {@code audio/AudioCodec} (verbatim). Ordinal order matters: the binary model
 * format stores the codec as {@code ordinal()} (1=VORBIS, 2=OPUS). */
public enum AudioCodec {
    UNDEFINED,
    VORBIS,
    OPUS
}
