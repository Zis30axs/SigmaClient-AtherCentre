package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Independent KillAura experiment.
 *
 * Combat helpers from the client are intentionally not used here. Targeting,
 * aim-point selection, rotation smoothing, ray casting and attack timing are
 * implemented locally so the module can be evaluated in isolation.
 */
public class Anthropic extends Module {
    private final List<PlayerEntity> selectedTargets = new ArrayList<>();

    private PlayerEntity primaryTarget;
    private Vector3d attackOrigin;
    private Vector3d aimPoint;

    private float appliedYaw;
    private float appliedPitch;
    private float yawVelocity;
    private float pitchVelocity;

    private int lastTargetId = Integer.MIN_VALUE;
    private long nextAttackTime;

    public Anthropic() {
        super(ModuleCategory.COMBAT, "Anthropic", "Independent KillAura experiment.");

        this.registerSetting(new ModeSetting("Mode", "Attack one target or multiple targets.", 0,
                "Single", "Multi"));
        this.registerSetting(new NumberSetting<>("Range", "Maximum attack range.",
                3.4F, 2.0F, 6.0F, 0.05F));
        this.registerSetting(new NumberSetting<>("Max Targets", "Maximum targets attacked in Multi mode.",
                4.0F, 1.0F, 8.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("CPS", "Attack cycles per second.",
                10.0F, 1.0F, 20.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Rotation Speed", "Maximum adaptive rotation step per tick.",
                90.0F, 5.0F, 180.0F, 5.0F));
        this.registerSetting(new BooleanSetting("Raycast", "Require the server rotation to resolve to a valid target.",
                true));
        this.registerSetting(new BooleanSetting("Through Walls", "Allow targets behind blocks.",
                false));
    }

    @Override
    public void onEnable() {
        this.clearState();
        if (mc.player != null) {
            this.appliedYaw = mc.player.lastReportedYaw;
            this.appliedPitch = mc.player.lastReportedPitch;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearState();
        super.onDisable();
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (!event.isPre()) {
            return;
        }

        if (mc.player == null || mc.world == null || mc.playerController == null) {
            this.clearTargets();
            return;
        }

        this.attackOrigin = new Vector3d(
                event.getX(),
                event.getY() + (double) mc.player.getEyeHeight(),
                event.getZ());

        float baseYaw = mc.player.lastReportedYaw;
        float basePitch = mc.player.lastReportedPitch;

        if (!this.updateTargets(baseYaw, basePitch)) {
            this.yawVelocity *= 0.35F;
            this.pitchVelocity *= 0.35F;
            return;
        }

        this.aimPoint = this.findBestAimPoint(this.primaryTarget, this.attackOrigin, baseYaw, basePitch);
        if (this.aimPoint == null) {
            this.clearTargets();
            return;
        }

        float[] desiredRotation = this.rotationTo(this.attackOrigin, this.aimPoint);
        this.updateAdaptiveRotation(baseYaw, basePitch, desiredRotation[0], desiredRotation[1]);

        event.setYaw(this.appliedYaw);
        event.setPitch(this.appliedPitch);

        float remainingYaw = Math.abs(wrapDegrees(desiredRotation[0] - this.appliedYaw));
        float remainingPitch = Math.abs(desiredRotation[1] - this.appliedPitch);
        boolean rotationReady = remainingYaw <= 6.0F && remainingPitch <= 6.0F;

        if (rotationReady) {
            event.attackPost(this::attackSelectedTargets);
        }
    }

    private boolean updateTargets(float baseYaw, float basePitch) {
        List<PlayerEntity> candidates = new ArrayList<>();
        double engageRange = this.getNumberValueBySettingName("Range") + 1.0D;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (!this.isBasicTargetValid(player)) {
                continue;
            }

            if (this.distanceToBox(this.attackOrigin, player.getBoundingBox()) <= engageRange) {
                candidates.add(player);
            }
        }

        if (candidates.isEmpty()) {
            this.clearTargets();
            return false;
        }

        candidates.sort(Comparator.comparingDouble(player -> this.targetScore(player, baseYaw, basePitch)));

        PlayerEntity best = candidates.get(0);
        if (this.primaryTarget != null && candidates.contains(this.primaryTarget)) {
            double bestScore = this.targetScore(best, baseYaw, basePitch);
            double oldScore = this.targetScore(this.primaryTarget, baseYaw, basePitch);

            // Small hysteresis keeps the aura from changing target every frame when
            // two players have nearly identical scores.
            if (oldScore <= bestScore * 1.15D + 2.0D) {
                best = this.primaryTarget;
            }
        }

        if (best.getEntityId() != this.lastTargetId) {
            this.lastTargetId = best.getEntityId();
            this.yawVelocity *= 0.2F;
            this.pitchVelocity *= 0.2F;
        }

        this.primaryTarget = best;
        this.selectedTargets.clear();
        this.selectedTargets.add(best);

        if ("Multi".equals(this.getStringSettingValueByName("Mode"))) {
            int maxTargets = Math.max(1, (int) this.getNumberValueBySettingName("Max Targets"));
            for (PlayerEntity player : candidates) {
                if (this.selectedTargets.size() >= maxTargets) {
                    break;
                }
                if (player != best) {
                    this.selectedTargets.add(player);
                }
            }
        }

        return true;
    }

    private boolean isBasicTargetValid(PlayerEntity player) {
        return player != null
                && player != mc.player
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative()
                && player.getHealth() > 0.0F;
    }

    private double targetScore(PlayerEntity target, float currentYaw, float currentPitch) {
        Vector3d closest = this.closestPoint(this.attackOrigin, target.getBoundingBox());
        float[] rotation = this.rotationTo(this.attackOrigin, closest);
        double yawCost = Math.abs(wrapDegrees(rotation[0] - currentYaw));
        double pitchCost = Math.abs(rotation[1] - currentPitch);
        double angularCost = Math.sqrt(yawCost * yawCost + pitchCost * pitchCost * 0.55D);
        double distance = this.distanceToBox(this.attackOrigin, target.getBoundingBox());

        // Distance matters, but not enough to constantly pull the target away from
        // whatever the current server rotation can reach cleanly.
        return angularCost * 0.72D + distance * 8.0D;
    }

    private Vector3d findBestAimPoint(PlayerEntity target, Vector3d origin, float currentYaw, float currentPitch) {
        AxisAlignedBB box = target.getBoundingBox();
        Vector3d bestPoint = null;
        double bestCost = Double.MAX_VALUE;

        Vector3d closest = this.closestPoint(origin, box);
        if (this.canUseAimPoint(origin, closest)) {
            bestPoint = closest;
            bestCost = this.aimPointCost(origin, closest, currentYaw, currentPitch);
        }

        // A small 3x3x3 lattice is enough for player hitboxes and avoids the heavy
        // allocations / large point clouds used by many generic raytrace helpers.
        double[] fractions = { 0.15D, 0.50D, 0.85D };
        for (double fx : fractions) {
            double x = lerp(box.minX, box.maxX, fx);
            for (double fy : fractions) {
                double y = lerp(box.minY, box.maxY, fy);
                for (double fz : fractions) {
                    double z = lerp(box.minZ, box.maxZ, fz);
                    Vector3d point = new Vector3d(x, y, z);
                    if (!this.canUseAimPoint(origin, point)) {
                        continue;
                    }

                    double cost = this.aimPointCost(origin, point, currentYaw, currentPitch);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestPoint = point;
                    }
                }
            }
        }

        return bestPoint;
    }

    private double aimPointCost(Vector3d origin, Vector3d point, float currentYaw, float currentPitch) {
        float[] rotation = this.rotationTo(origin, point);
        double yawCost = Math.abs(wrapDegrees(rotation[0] - currentYaw));
        double pitchCost = Math.abs(rotation[1] - currentPitch);
        double distanceCost = Math.sqrt(origin.squareDistanceTo(point)) * 0.15D;
        return yawCost + pitchCost * 0.72D + distanceCost;
    }

    private boolean canUseAimPoint(Vector3d origin, Vector3d point) {
        if (this.getBooleanValueFromSettingName("Through Walls")) {
            return true;
        }

        RayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                origin,
                point,
                RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE,
                mc.player));

        if (blockHit == null || blockHit.getType() == RayTraceResult.Type.MISS) {
            return true;
        }

        double pointDistance = origin.squareDistanceTo(point);
        double blockDistance = origin.squareDistanceTo(blockHit.getHitVec());
        return blockDistance + 1.0E-4D >= pointDistance;
    }

    private void updateAdaptiveRotation(float baseYaw, float basePitch, float targetYaw, float targetPitch) {
        float maxStep = this.getNumberValueBySettingName("Rotation Speed");
        float yawError = wrapDegrees(targetYaw - baseYaw);
        float pitchError = targetPitch - basePitch;

        float yawGain = 0.30F + 0.48F * Math.min(1.0F, Math.abs(yawError) / 90.0F);
        float pitchGain = 0.34F + 0.46F * Math.min(1.0F, Math.abs(pitchError) / 60.0F);

        float desiredYawStep = MathHelper.clamp(yawError * yawGain, -maxStep, maxStep);
        float desiredPitchStep = MathHelper.clamp(pitchError * pitchGain, -maxStep, maxStep);

        this.yawVelocity = this.yawVelocity * 0.28F + desiredYawStep * 0.72F;
        this.pitchVelocity = this.pitchVelocity * 0.24F + desiredPitchStep * 0.76F;

        if (Math.abs(yawError) < 0.35F) {
            this.yawVelocity = yawError;
        }
        if (Math.abs(pitchError) < 0.35F) {
            this.pitchVelocity = pitchError;
        }

        float yaw = baseYaw + this.yawVelocity;
        float pitch = MathHelper.clamp(basePitch + this.pitchVelocity, -90.0F, 90.0F);

        float gcd = this.mouseGcd();
        if (gcd > 0.0F) {
            float yawDelta = yaw - baseYaw;
            float pitchDelta = pitch - basePitch;
            yaw = baseYaw + yawDelta - yawDelta % gcd;
            pitch = basePitch + pitchDelta - pitchDelta % gcd;
        }

        this.appliedYaw = yaw;
        this.appliedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
    }

    private float mouseGcd() {
        float sensitivity = (float) (mc.gameSettings.mouseSensitivity * 0.6F + 0.2F);
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    private void attackSelectedTargets() {
        if (mc.player == null || mc.world == null || mc.playerController == null
                || this.primaryTarget == null || this.attackOrigin == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextAttackTime) {
            return;
        }

        float range = this.getNumberValueBySettingName("Range");
        boolean multi = "Multi".equals(this.getStringSettingValueByName("Mode"));
        boolean raycast = this.getBooleanValueFromSettingName("Raycast");
        boolean attacked = false;

        if (!multi) {
            PlayerEntity target = this.primaryTarget;

            if (raycast) {
                Entity hit = this.raycastEntity(this.appliedYaw, this.appliedPitch, range);
                if (hit instanceof PlayerEntity && this.isAttackableNow((PlayerEntity) hit, range)) {
                    target = (PlayerEntity) hit;
                } else {
                    return;
                }
            }

            if (this.isAttackableNow(target, range)) {
                this.attackEntity(target);
                attacked = true;
            }
        } else {
            // Multi intentionally means "attack all selected valid targets". A single
            // server rotation cannot geometrically point at several separated players,
            // so exact crosshair raycast is a Single-mode condition. Multi still uses
            // per-target block visibility unless Through Walls is enabled.
            for (PlayerEntity target : this.selectedTargets) {
                if (!this.isAttackableNow(target, range)) {
                    continue;
                }
                this.attackEntity(target);
                attacked = true;
            }
        }

        if (attacked) {
            this.scheduleNextAttack(now);
        }
    }

    private boolean isAttackableNow(PlayerEntity target, float range) {
        if (!this.isBasicTargetValid(target)) {
            return false;
        }

        if (this.distanceToBox(this.attackOrigin, target.getBoundingBox()) > range) {
            return false;
        }

        if (!this.getBooleanValueFromSettingName("Through Walls")) {
            return this.findBestAimPoint(target, this.attackOrigin, this.appliedYaw, this.appliedPitch) != null;
        }

        return true;
    }

    private Entity raycastEntity(float yaw, float pitch, double reach) {
        Vector3d origin = this.attackOrigin;
        Vector3d look = mc.player.getLookCustom(1.0F, yaw, pitch);
        Vector3d end = origin.add(look.x * reach, look.y * reach, look.z * reach);

        double maxDistanceSq = reach * reach;
        if (!this.getBooleanValueFromSettingName("Through Walls")) {
            RayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                    origin,
                    end,
                    RayTraceContext.BlockMode.COLLIDER,
                    RayTraceContext.FluidMode.NONE,
                    mc.player));
            if (blockHit != null && blockHit.getType() != RayTraceResult.Type.MISS) {
                maxDistanceSq = origin.squareDistanceTo(blockHit.getHitVec());
            }
        }

        AxisAlignedBB searchBox = mc.player.getBoundingBox()
                .expand(look.scale(reach))
                .grow(1.0D, 1.0D, 1.0D);

        EntityRayTraceResult result = ProjectileHelper.rayTraceEntities(
                mc.player,
                origin,
                end,
                searchBox,
                this::canRaycastEntity,
                maxDistanceSq);

        return result == null ? null : result.getEntity();
    }

    private boolean canRaycastEntity(Entity entity) {
        return entity != mc.player
                && entity instanceof LivingEntity
                && entity.isAlive()
                && !entity.isSpectator()
                && entity.canBeCollidedWith();
    }

    private void attackEntity(PlayerEntity target) {
        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(Hand.MAIN_HAND);
    }

    private void scheduleNextAttack(long now) {
        double cps = Math.max(1.0D, this.getNumberValueBySettingName("CPS"));
        double jitter = ThreadLocalRandom.current().nextDouble(0.92D, 1.08D);
        long delay = Math.max(1L, Math.round(1000.0D / (cps * jitter)));
        this.nextAttackTime = now + delay;
    }

    private float[] rotationTo(Vector3d from, Vector3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[] { yaw, MathHelper.clamp(pitch, -90.0F, 90.0F) };
    }

    private Vector3d closestPoint(Vector3d point, AxisAlignedBB box) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return new Vector3d(x, y, z);
    }

    private double distanceToBox(Vector3d point, AxisAlignedBB box) {
        Vector3d closest = this.closestPoint(point, box);
        return Math.sqrt(point.squareDistanceTo(closest));
    }

    private static double lerp(double min, double max, double fraction) {
        return min + (max - min) * fraction;
    }

    private static float wrapDegrees(float value) {
        return MathHelper.wrapDegrees(value);
    }

    private void clearTargets() {
        this.primaryTarget = null;
        this.aimPoint = null;
        this.attackOrigin = null;
        this.selectedTargets.clear();
        this.lastTargetId = Integer.MIN_VALUE;
    }

    private void clearState() {
        this.clearTargets();
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.appliedYaw = 0.0F;
        this.appliedPitch = 0.0F;
        this.nextAttackTime = 0L;
    }
}
