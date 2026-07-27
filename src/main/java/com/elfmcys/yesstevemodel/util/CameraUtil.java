package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.client.compat.oculus.OculusCompat;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.Entity;

public final class CameraUtil {
    public static int getCameraType(IContext<? extends Entity> context) {
        if (context.entity() == Minecraft.getInstance().player && ModelPreviewRenderer.isFirstPerson()) {
            return context.mc().gameSettings.getPointOfView().ordinal();
        }
        return PointOfView.THIRD_PERSON_FRONT.ordinal();
    }

    public static boolean isFirstPerson() {
        return Minecraft.getInstance().gameSettings.getPointOfView() == PointOfView.FIRST_PERSON;
    }

    public static boolean isFirstPerson(AnimatableEntity<? extends Entity> animatableEntity) {
        return animatableEntity.getEntity() == Minecraft.getInstance().player
                && ModelPreviewRenderer.isFirstPerson()
                && !OculusCompat.isPBRActive()
                && Minecraft.getInstance().gameSettings.getPointOfView() == PointOfView.FIRST_PERSON;
    }

    public static boolean isThirdPerson(IContext<? extends Entity> context) {
        return isThirdPersonModel(context.geoInstance());
    }

    public static boolean isThirdPersonModel(AnimatableEntity<?> model) {
        return (model instanceof IPreviewAnimatable) || ModelPreviewRenderer.isPreview();
    }
}
