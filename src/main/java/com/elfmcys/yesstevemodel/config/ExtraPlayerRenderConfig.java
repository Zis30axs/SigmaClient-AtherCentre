package com.elfmcys.yesstevemodel.config;

import com.elfmcys.yesstevemodel.OpenYsmClientConfig;
import com.elfmcys.yesstevemodel.YesSteveModel;

import java.util.function.Supplier;

/**
 * Facade for upstream {@code config/ExtraPlayerRenderConfig} (1.20.1), which backs every entry
 * with a {@code ForgeConfigSpec} value. There is no Forge config system here, so the entries
 * delegate to {@link OpenYsmClientConfig} - the same store the live in-game overlay gate
 * ({@code IngameGui}) and the retired {@code OpenYsmExtraPlayerRenderScreen} already use.
 *
 * <p><b>Deliberate inversion:</b> upstream's {@code DisablePlayerRender} defaults to
 * {@code false} (overlay shown), while the local {@code extraPlayerRender} flag defaults to
 * {@code false} meaning "do not draw the overlay". The local flag is the authoritative behaviour,
 * so {@link #DISABLE_PLAYER_RENDER} is its negation and {@link #setDisablePlayerRender(boolean)}
 * flips it back. This keeps the ported {@code ExtraPlayerRenderScreen} line-diffable against
 * upstream without changing what the in-game gate reads.
 *
 * <p>Range clamping ({@code PlayerPosX/Y >= 0}, {@code PlayerScale} in 8..360 with the
 * upstream-default 40 fallback) lives in {@link OpenYsmClientConfig}'s setters, matching
 * upstream's {@code defineInRange} bounds.
 */
public class ExtraPlayerRenderConfig {

    /** Upstream {@code DisablePlayerRender}: true when the overlay is hidden. See the inversion note. */
    public static final Supplier<Boolean> DISABLE_PLAYER_RENDER =
            () -> !YesSteveModel.getClientConfig().isExtraPlayerRender();

    /** Upstream {@code PlayerPosX} ({@code IntValue}, default 10). */
    public static final Supplier<Double> PLAYER_POS_X =
            () -> (double) YesSteveModel.getClientConfig().getExtraPlayerX();

    /** Upstream {@code PlayerPosY} ({@code IntValue}, default 10). */
    public static final Supplier<Double> PLAYER_POS_Y =
            () -> (double) YesSteveModel.getClientConfig().getExtraPlayerY();

    /** Upstream {@code PlayerScale} ({@code DoubleValue}, default 40, range 8..360). */
    public static final Supplier<Double> PLAYER_SCALE =
            () -> (double) YesSteveModel.getClientConfig().getExtraPlayerScale();

    /** Upstream {@code PlayerYawOffset} ({@code DoubleValue}, default 5). */
    public static final Supplier<Double> PLAYER_YAW_OFFSET =
            () -> (double) YesSteveModel.getClientConfig().getExtraPlayerYawOffset();

    private ExtraPlayerRenderConfig() {
    }

    /**
     * Upstream {@code DISABLE_PLAYER_RENDER.set(...)}; persists immediately because the checkbox
     * writes on press (same call path as the retired screen's toggle).
     */
    public static void setDisablePlayerRender(boolean disable) {
        YesSteveModel.setExtraPlayerRender(!disable);
    }

    public static void setPlayerPosX(int value) {
        YesSteveModel.getClientConfig().setExtraPlayerX(value);
    }

    public static void setPlayerPosY(int value) {
        YesSteveModel.getClientConfig().setExtraPlayerY(value);
    }

    public static void setPlayerScale(double value) {
        YesSteveModel.getClientConfig().setExtraPlayerScale((float) value);
    }

    public static void setPlayerYawOffset(double value) {
        YesSteveModel.getClientConfig().setExtraPlayerYawOffset((float) value);
    }

    /**
     * Single save for {@code ExtraPlayerRenderScreen#onClose()}, replacing upstream's per-field
     * {@code ForgeConfigSpec} writes (which save on {@code .set()}).
     */
    public static void save() {
        YesSteveModel.saveClientConfig();
    }
}
