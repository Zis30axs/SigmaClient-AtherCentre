package com.elfmcys.yesstevemodel.audio;

import net.minecraft.client.audio.IAudioStream;

public interface IAudioStreamSupport extends IAudioStream {
    boolean isClosed();
}