package com.elfmcys.yesstevemodel.config;

import com.elfmcys.yesstevemodel.YesSteveModel;

import java.util.function.Supplier;

/**
 * Client-side subset of upstream {@code config/GeneralConfig} (1.20.1).
 *
 * <p>Upstream backs every entry with a {@code ForgeConfigSpec.BooleanValue}. There is no Forge
 * config system here, so each entry is a {@link Supplier} over a mutable static; the ones that are
 * still hard {@code false} are the render kill-switches nothing toggles yet (they arrive with the
 * config screens in W6 step 5).
 */
public class GeneralConfig {
    public static final Supplier<Boolean> DISABLE_SELF_MODEL = () -> false;
    public static final Supplier<Boolean> DISABLE_OTHER_MODEL = () -> false;
    public static final Supplier<Boolean> DISABLE_PROJECTILE_MODEL = () -> false;
    public static final Supplier<Boolean> DISABLE_VEHICLE_MODEL = () -> false;

    /**
     * Upstream {@code SoundVolume} ({@code defineInRange("SoundVolume", 100, 0, 100)}), read by
     * {@code YSMTickableSoundInstance#tick}. Constant facade at the upstream default: the Forge
     * config screen that edited it is not ported, and the Sigma GUI has no YSM volume slider.
     */
    public static final Supplier<Double> SOUND_VOLUME = () -> 100.0;

    /**
     * Upstream {@code PrintAnimationRouletteMsg}, default {@code false} — when on, picking an
     * animation in the roulette echoes {@code message.yes_steve_model.model.animation_roulette.play}
     * into chat. Read by {@code AnimationRouletteScreen#playAnimation}.
     */
    private static boolean printAnimationRouletteMsg = false;

    public static final Supplier<Boolean> PRINT_ANIMATION_ROULETTE_MSG =
            () -> Boolean.valueOf(printAnimationRouletteMsg);

    public static void setPrintAnimationRouletteMsg(boolean value) {
        printAnimationRouletteMsg = value;
    }

    /**
     * Upstream {@code ShowModelIdFirst}, default {@code false}. Persisted in the client config
     * (there is no Forge config system here); read by {@code ModelButton#getMessage}.
     */
    public static boolean isShowModelIdFirst() {
        return YesSteveModel.getClientConfig().isShowModelIdFirst();
    }

    public static void setShowModelIdFirst(boolean value) {
        YesSteveModel.getClientConfig().setShowModelIdFirst(value);
        YesSteveModel.saveClientConfig();
    }

    /**
     * Upstream {@code DisclaimerShow}, default {@code true}. Persisted in the client config;
     * read by {@code PlayerModelToggleKey}, cleared by {@code DisclaimerScreen}.
     */
    public static boolean isDisclaimerShow() {
        return YesSteveModel.getClientConfig().isDisclaimerShow();
    }

    public static void setDisclaimerShow(boolean value) {
        YesSteveModel.getClientConfig().setDisclaimerShow(value);
        YesSteveModel.saveClientConfig();
    }
}
