package com.elfmcys.yesstevemodel.config;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/**
 * Local stand-in for upstream {@code config/ModSoundEvents}. Upstream registers
 * {@code yes_steve_model:custom} through a Forge {@code DeferredRegister}; here no registry
 * write is needed because the whole play path resolves the sound through the
 * {@code sounds.json} accessor for the same id ({@code assets/yes_steve_model/sounds.json},
 * entry {@code custom} -> {@code yes_steve_model:empty.ogg}, {@code stream: true}) - the
 * {@link SoundEvent} object is only a key holder for {@code LocatableSound}. The resource
 * file itself is never decoded: {@code YSMSoundInstance} injects the model-bundled stream at
 * the {@code SoundEngine.IStreamedSound} seam.
 */
public class ModSoundEvents {

    public static final SoundEvent CUSTOM_SOUND =
            new SoundEvent(new ResourceLocation(YesSteveModel.MOD_ID, "custom"));

    private ModSoundEvents() {
    }
}
