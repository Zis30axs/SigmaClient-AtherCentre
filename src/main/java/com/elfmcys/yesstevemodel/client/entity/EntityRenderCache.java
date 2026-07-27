package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;

import java.lang.ref.WeakReference;

public class EntityRenderCache {

    private static final ReferenceArrayList<WeakReference<GeoEntity<?>>> weakRefs = new ReferenceArrayList<>(64);

    private static final ReferenceArrayList<GeoEntity<?>> strongRefs = new ReferenceArrayList<>(16);

    public static void register(GeoEntity<?> entity) {
        weakRefs.add(new WeakReference<>(entity));
    }

    public static void tick(float partialTick) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        ObjectListIterator<WeakReference<GeoEntity<?>>> it = weakRefs.iterator();
        while (it.hasNext()) {
            GeoEntity geoEntity = (GeoEntity) ((WeakReference<?>) it.next()).get();
            if (geoEntity == null) {
                it.remove();
            } else if (!geoEntity.isDebugMode()) {
                it.remove();
            } else {
                geoEntity.tickModel();
                if (geoEntity.supportsAsync() && geoEntity.isModelInitialized() && geoEntity.isModelReady()) {
                    Entity entity = geoEntity.getEntity();
                    if (entity instanceof AbstractClientPlayerEntity) {
                        if (entity instanceof ClientPlayerEntity) {
                            if (!GeneralConfig.DISABLE_SELF_MODEL.get()) {
                                geoEntity.submitAsyncUpdate(partialTick);
                                strongRefs.add(geoEntity);
                            }
                        } else if (!GeneralConfig.DISABLE_OTHER_MODEL.get()) {
                            geoEntity.submitAsyncUpdate(partialTick);
                            strongRefs.add(geoEntity);
                        }
                    } else if (entity instanceof ProjectileEntity) {
                        if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                            geoEntity.submitAsyncUpdate(partialTick);
                            strongRefs.add(geoEntity);
                        }
                    } else if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()) {
                        geoEntity.submitAsyncUpdate(partialTick);
                        strongRefs.add(geoEntity);
                    }
                }
            }
        }
    }

    public static void clear() {
        ObjectListIterator<GeoEntity<?>> it = strongRefs.iterator();
        while (it.hasNext()) {
            try {
                it.next().awaitAsyncResult();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        strongRefs.clear();
    }
}