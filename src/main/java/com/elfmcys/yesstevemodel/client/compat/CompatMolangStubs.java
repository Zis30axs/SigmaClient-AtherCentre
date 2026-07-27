package com.elfmcys.yesstevemodel.client.compat;

import com.elfmcys.yesstevemodel.client.animation.molang.CtrlBinding;
import com.elfmcys.yesstevemodel.client.animation.molang.TLMBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.molang.runtime.Function;

/**
 * Neutral molang surface for every third-party integration this client-only port cuts.
 *
 * <p><b>Cutting a compat integration does not mean its molang symbols may be omitted.</b> Models
 * reference them unconditionally, and an unregistered symbol resolves to {@code null}, which
 * silently poisons whatever expression uses it. Real case: {@code 昔涟1.0.4.ysm} gates every
 * {@code walk}/{@code run}/{@code sneak}/{@code sneaking} animation blend on
 * {@code ctrl.parcool_state == ''}. With {@code parcool_state} absent that comparison can never be
 * true, so all those blends stayed at weight zero and the body froze in an A-pose while the
 * physics/hair controllers (which use no such gate) kept animating.
 *
 * <p>Every value below is copied from the corresponding upstream {@code XxxCompat}
 * "mod is absent" branch, so behaviour matches upstream running without the mod installed:
 * <ul>
 *   <li>{@code BetterCombatCompat.registerDummyBindings}</li>
 *   <li>{@code CarryOnCompat.registerBindings} (absent branch)</li>
 *   <li>{@code CreateCompat.registerCreateFunctions} (absent branch)</li>
 *   <li>{@code TacCompat.registerControllerFunctions} (absent branch)</li>
 *   <li>{@code ImmersiveMelodiesCompat.registerBindings} (absent branch)</li>
 *   <li>{@code SpellbooksCompat.registerDummyBindings}</li>
 *   <li>{@code ParcoolCompat.registerBindings} (absent branch)</li>
 *   <li>{@code SBackpackCompat.registerControllerFunctions} (absent branch)</li>
 *   <li>{@code SlashBladeCompat.registerSlashBladeFunctions}</li>
 *   <li>{@code SWEMCompat.registerSWEMFunctions}</li>
 *   <li>{@code TouhouLittleMaidCompat.registerMaidAnimStates} (absent branch)</li>
 *   <li>{@code CuriosCompat.registerDummyBindings}</li>
 * </ul>
 */
public final class CompatMolangStubs {

    private CompatMolangStubs() {
    }

    /** The {@code ctrl.*} namespace. */
    public static void registerCtrl(CtrlBinding binding) {
        // bettercombat
        binding.clientPlayerEntityVar("bcombat_attack_animation", ctx -> StringPool.EMPTY);

        // carryon
        binding.livingEntityVar("carryon_type", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("carryon_is_princess", ctx -> false);

        // create
        binding.playerEntityVar("create_hanging_skyhook", ctx -> false);

        // tacz
        binding.livingEntityVar("tac_hold_gun", ctx -> false);
        binding.livingEntityVar("tac_gun_type", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("tac_gun_id", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("tac_is_fire", ctx -> false);
        binding.livingEntityVar("tac_is_aim", ctx -> false);
        binding.livingEntityVar("tac_is_reload", ctx -> false);
        binding.livingEntityVar("tac_is_melee", ctx -> false);
        binding.livingEntityVar("tac_is_draw", ctx -> false);
        binding.livingEntityVar("tac_fire_mode", ctx -> StringPool.EMPTY);

        // immersivemelodies
        binding.livingEntityVar("im_pitch", ctx -> 0.0f);
        binding.livingEntityVar("im_volume", ctx -> 0.0f);
        binding.livingEntityVar("im_current", ctx -> 0.0f);
        binding.livingEntityVar("im_delta", ctx -> 0L);
        binding.livingEntityVar("im_time", ctx -> 0L);

        // ironsspellbooks
        binding.clientPlayerEntityVar("iss_animation", ctx -> StringPool.EMPTY);

        // parcool
        binding.livingEntityVar("parcool_state", ctx -> StringPool.EMPTY);

        // sophisticated backpacks
        binding.livingEntityVar("has_sophisticated_backpack", ctx -> false);

        // slashblade
        binding.livingEntityVar("slashblade_animation", ctx -> StringPool.EMPTY);

        // swem
        binding.livingEntityVar("swem_is_ride", ctx -> false);
        binding.livingEntityVar("swem_state", ctx -> StringPool.EMPTY);
    }

    /** The {@code tlm.*} namespace (TouhouLittleMaid). */
    public static void registerTlm(TLMBinding binding) {
        binding.livingEntityVar("is_begging", ctx -> false);
        binding.livingEntityVar("is_sitting", ctx -> false);
        binding.livingEntityVar("has_backpack", ctx -> false);
        binding.livingEntityVar("favorability_point", ctx -> 0);
        binding.livingEntityVar("favorability_level", ctx -> 0);
        binding.livingEntityVar("task_id", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("schedule", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("activity", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("gomoku_win_count", ctx -> 0);
        binding.livingEntityVar("gomoku_rank", ctx -> 1);
        binding.livingEntityVar("game_statue", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("backpack_type", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("is_entity", ctx -> true);
        binding.livingEntityVar("is_statue", ctx -> false);
        binding.livingEntityVar("is_garage_kit", ctx -> false);
        binding.livingEntityVar("show_item", ctx -> StringPool.EMPTY);
    }

    /** The curios part of the {@code ysm.*} namespace. */
    public static void registerCurios(ContextBinding binding) {
        binding.function("has_any_curios", Function.NOOP);
        binding.function("has_any_curios_with_all_tags", Function.NOOP);
        binding.function("has_any_curios_with_any_tag", Function.NOOP);
        binding.livingEntityVar("dump_curios", context -> {
            context.logWarning("Curios not installed.");
            return null;
        });
    }
}
