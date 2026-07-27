package com.elfmcys.yesstevemodel.audio;

import com.elfmcys.yesstevemodel.config.ModSoundEvents;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Upstream {@code audio/AudioPlayerManager} (verbatim modulo mappings). A sound name containing
 * {@code ":"} plays a plain resource sound through {@link YSMTickableSoundInstance}; any other
 * name resolves against the model's bundled audio tracks and plays through
 * {@link YSMSoundInstance} (custom injected stream). MCP map: {@code getSoundManager()} ->
 * {@code getSoundHandler()}, {@code SoundEvent.createVariableRangeEvent} -> {@code new
 * SoundEvent} (1.16.5 has no range variants).
 */
public class AudioPlayerManager {

    private final Int2ReferenceOpenHashMap<IAudioPlayer> activePlayers = new Int2ReferenceOpenHashMap<>(0);

    private final ReferenceArrayList<IAudioPlayer> playerList = new ReferenceArrayList<>(0);

    public boolean playSound(AnimatableEntity<?> entity, int soundId, String soundName, boolean forceReplace, @Nullable Consumer<YSMTickableSoundInstance> callback) {
        YSMTickableSoundInstance soundInstance;
        if (soundName.contains(":")) {
            ResourceLocation resourceLocationTryParse = ResourceLocation.tryCreate(soundName);
            if (resourceLocationTryParse != null) {
                soundInstance = new YSMTickableSoundInstance(new SoundEvent(resourceLocationTryParse), entity.getEntity());
            } else {
                soundInstance = null;
            }
        } else {
            soundInstance = entity.getAudioStreamFactory(soundName).map(audioStreamFactory -> new YSMSoundInstance(ModSoundEvents.CUSTOM_SOUND, audioStreamFactory, entity.getEntity())).orElse(null);
        }
        if (callback != null) {
            callback.accept(soundInstance);
        }
        if (soundInstance == null) {
            return false;
        }
        if (soundId != 0) {
            if (forceReplace) {
                IAudioPlayer previousPlayer = this.activePlayers.put(soundId, soundInstance);
                if (previousPlayer != null && !previousPlayer.isStopped()) {
                    previousPlayer.release();
                }
            } else {
                if (this.activePlayers.compute(soundId, (num, existingPlayer) -> {
                    if (existingPlayer == null || existingPlayer.isStopped()) {
                        return soundInstance;
                    }
                    return existingPlayer;
                }) != soundInstance) {
                    return false;
                }
            }
        } else {
            this.playerList.add(soundInstance);
        }
        YSMTickableSoundInstance soundInstanceToPlay = soundInstance;
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().getSoundHandler().play(soundInstanceToPlay);
        });
        return true;
    }

    public boolean stopSound(int soundId) {
        IAudioPlayer player;
        if (soundId != 0 && (player = this.activePlayers.remove(soundId)) != null) {
            player.release();
            return true;
        }
        return false;
    }

    public void stopAll() {
        this.activePlayers.values().forEach(IAudioPlayer::release);
        for (IAudioPlayer iAudioPlayer : this.playerList) {
            iAudioPlayer.release();
        }
        this.activePlayers.clear();
        this.playerList.clear();
    }

    public void tick() {
        ObjectIterator<Int2ReferenceMap.Entry<IAudioPlayer>> objectIteratorFastIterator = this.activePlayers.int2ReferenceEntrySet().fastIterator();
        while (objectIteratorFastIterator.hasNext()) {
            if (objectIteratorFastIterator.next().getValue().isStopped()) {
                objectIteratorFastIterator.remove();
            }
        }
        this.playerList.removeIf(IAudioPlayer::isStopped);
    }
}
