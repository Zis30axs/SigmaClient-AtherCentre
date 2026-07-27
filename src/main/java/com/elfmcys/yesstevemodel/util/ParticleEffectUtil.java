package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import com.elfmcys.yesstevemodel.molang.runtime.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.command.arguments.ParticleArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.IParticleData;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ParticleEffectUtil {
    private static final Cache<String, IParticleData> particleCache = CacheBuilder.newBuilder().expireAfterAccess(60, TimeUnit.SECONDS).build();

    public static boolean handleParticle(ExecutionContext<IContext<Entity>> context, Function.ArgumentCollection arguments, boolean isAbsolute) throws ExecutionException, CommandSyntaxException {
        String particleId = arguments.getAsString(context, 0);
        if (StringUtils.isBlank(particleId)) {
            return false;
        }

        double offsetX = 0.0d;
        double offsetY = 0.0d;
        double offsetZ = 0.0d;
        double deltaX = 0.0d;
        double deltaY = 0.0d;
        double deltaZ = 0.0d;
        double speed = 0.0d;
        int count = 0;
        int lifetime = 20;
        int argCount = arguments.size();

        if (argCount > 1) {
            offsetX = arguments.getAsDouble(context, 1);
        }
        if (argCount > 2) {
            offsetY = arguments.getAsDouble(context, 2);
        }
        if (argCount > 3) {
            offsetZ = arguments.getAsDouble(context, 3);
        }
        if (argCount > 4) {
            deltaX = arguments.getAsDouble(context, 4);
        }
        if (argCount > 5) {
            deltaY = arguments.getAsDouble(context, 5);
        }
        if (argCount > 6) {
            deltaZ = arguments.getAsDouble(context, 6);
        }
        if (argCount > 7) {
            speed = arguments.getAsDouble(context, 7);
        }
        if (argCount > 8) {
            count = Math.max(arguments.getAsInt(context, 8), 0);
        }
        if (argCount > 9) {
            lifetime = Math.max(arguments.getAsInt(context, 9), 1);
        }

        spawnParticles(context.entity().entity(), particleId, offsetX, offsetY, offsetZ, deltaX, deltaY, deltaZ, speed, count, lifetime, isAbsolute, context.entity().random());
        return true;
    }

    private static void spawnParticles(Entity entity, String particleId, double offsetX, double offsetY, double offsetZ, double deltaX, double deltaY, double deltaZ, double speed, int count, int lifetime, boolean isAbsolute, Random random) throws ExecutionException, CommandSyntaxException {
        IParticleData particleData = particleCache.get(particleId, () -> ParticleArgument.parseParticle(new StringReader(particleId)));

        if (particleData == null) {
            return;
        }

        ParticleManager particleManager = Minecraft.getInstance().particles;

        if (count == 0) {
            Vector3d spawnPos = new Vector3d(offsetX, offsetY, offsetZ);
            if (!isAbsolute) {
                float yaw = entity instanceof PlayerEntity ? ((PlayerEntity) entity).renderYawOffset : entity.rotationYaw;
                spawnPos = rotateY(spawnPos, -yaw * 0.017453292f);
            }

            double x = entity.getPosX() + spawnPos.x;
            double y = entity.getPosY() + spawnPos.y;
            double z = entity.getPosZ() + spawnPos.z;
            double velocityX = speed * deltaX;
            double velocityY = speed * deltaY;
            double velocityZ = speed * deltaZ;

            Minecraft.getInstance().execute(() -> {
                Particle particle = particleManager.addParticle(particleData, x, y, z, velocityX, velocityY, velocityZ);
                if (particle != null) {
                    particle.setMaxAge(lifetime);
                }
            });
            return;
        }

        for (int i = 0; i < count; i++) {
            emitParticle(entity, offsetX, offsetY, offsetZ, deltaX, deltaY, deltaZ, speed, lifetime, particleManager, particleData, isAbsolute, random);
        }
    }

    private static void emitParticle(Entity entity, double offsetX, double offsetY, double offsetZ, double deltaX, double deltaY, double deltaZ, double speed, int lifetime, ParticleManager particleManager, IParticleData particleData, boolean isAbsolute, Random random) {
        double spreadX = random.nextGaussian() * deltaX;
        double spreadY = random.nextGaussian() * deltaY;
        double spreadZ = random.nextGaussian() * deltaZ;
        double velocityX = random.nextGaussian() * speed;
        double velocityY = random.nextGaussian() * speed;
        double velocityZ = random.nextGaussian() * speed;

        Vector3d spawnPos = new Vector3d(offsetX + spreadX, offsetY + spreadY, offsetZ + spreadZ);

        if (!isAbsolute) {
            spawnPos = rotateY(spawnPos, -entity.rotationYaw * 0.017453292f);
        }

        double x = entity.getPosX() + spawnPos.x;
        double y = entity.getPosY() + spawnPos.y;
        double z = entity.getPosZ() + spawnPos.z;

        Minecraft.getInstance().execute(() -> {
            Particle particle = particleManager.addParticle(particleData, x, y, z, velocityX, velocityY, velocityZ);
            if (particle != null) {
                particle.setMaxAge(lifetime);
            }
        });
    }

    private static Vector3d rotateY(Vector3d vec, float angle) {
        float sin = MathHelper.sin(angle);
        float cos = MathHelper.cos(angle);
        return new Vector3d(vec.x * (double) cos + vec.z * (double) sin, vec.y, vec.z * (double) cos - vec.x * (double) sin);
    }
}
