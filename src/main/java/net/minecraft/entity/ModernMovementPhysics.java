package net.minecraft.entity;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.fixes.PacketFixFor1_21Plus;
import net.minecraft.client.Minecraft;

/**
 * Version gate and shared bubble-column velocity math for the 1.21.11
 * movement compatibility fix.
 *
 * <p>Behavioral reference: Minecraft 1.21.11 named sources
 * (E:\.sigma\namedSrc):
 * <ul>
 *   <li>{@code Entity.move} no longer applies inside-block effects while
 *       moving; it only records {@code QueuedCollisionCheck} segments.</li>
 *   <li>{@code LivingEntity.tickMovement} calls {@code tickBlockCollision()}
 *       immediately after {@code travel()}; that stage walks the recorded
 *       movement path and invokes {@code Block.onEntityCollision}, which for
 *       bubble columns calls {@code onBubbleColumnSurfaceCollision} /
 *       {@code onBubbleColumnCollision} and writes the final velocity.</li>
 *   <li>The bubble formulas are unchanged versus 1.16.4; only the application
 *       stage moved.</li>
 * </ul>
 *
 * <p>This class therefore does NOT re-implement a block scanner. It only
 * decides when the existing 1.16.4 {@code doBlockCollisions} pipeline runs,
 * and hosts the pure formula so {@code Entity.onEnterBubbleColumn*} stay the
 * single source of truth.
 */
public final class ModernMovementPhysics {

    private ModernMovementPhysics() {
    }

    /**
     * Only Minecraft 1.21.11 is enabled. Earlier 1.21.x versions are kept on
     * the original 1.16.4 order until their exact inside-block semantics are
     * verified against their own sources (the workspace only contains the
     * 1.21.11 named source tree).
     */
    public static boolean shouldUseModernBlockEffects() {
        if (!PacketFixFor1_21Plus.isEnabled()) {
            return false;
        }

        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        ProtocolVersion target = loadingBase == null ? null : loadingBase.getTargetVersion();
        return target != null && target.newerThanOrEqualTo(ProtocolVersion.v1_21_11);
    }

    public static boolean isLocalPlayer(Entity entity) {
        if (entity == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player == entity;
    }

    /**
     * True for the local player on a 1.21.11 target: inside-block effects must
     * not run inside {@code Entity.move} (legacy 1.16.4 phase). They are applied
     * once after {@code travel()} in {@code LivingEntity.livingTick}, mirroring
     * 1.21.11's {@code tickBlockCollision()} stage.
     */
    public static boolean shouldDeferInsideBlockEffects(Entity entity) {
        return shouldUseModernBlockEffects() && isLocalPlayer(entity);
    }

    /**
     * Pure bubble-column velocity transform, identical in 1.16.4 and 1.21.11:
     * {@code airAbove=true} is the "surface" variant
     * ({@code Entity.onEnterBubbleColumnWithAirAbove} /
     * {@code Entity.applyBubbleColumnSurfaceEffects}), {@code airAbove=false}
     * is the submerged variant ({@code Entity.onEnterBubbleColumn} /
     * {@code Entity.applyBubbleColumnEffects}).
     */
    public static double computeBubbleColumnY(double y, boolean drag, boolean airAbove) {
        if (airAbove) {
            return drag ? Math.max(-0.9D, y - 0.03D) : Math.min(1.8D, y + 0.1D);
        }

        return drag ? Math.max(-0.3D, y - 0.03D) : Math.min(0.7D, y + 0.06D);
    }
}
