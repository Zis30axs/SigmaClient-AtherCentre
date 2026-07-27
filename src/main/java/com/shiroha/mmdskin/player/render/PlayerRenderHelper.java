package com.shiroha.mmdskin.player.render;

import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRenderProperties;
import com.shiroha.mmdskin.player.runtime.FirstPersonManager;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;

/** 鏂囦欢鑱岃矗锛氶泦涓绠楃帺瀹舵ā鍨嬫覆鏌撳Э鎬佷笌妯″瀷灞炴€ц鍙栥€?*/
public final class PlayerRenderHelper {

    private PlayerRenderHelper() {}

    public static MutableRenderPose calculateMutableRenderPose(AbstractClientPlayerEntity player, ManagedModel modelData, float tickDelta) {
        MutableRenderPose params = new MutableRenderPose();
        ModelRenderProperties renderProperties = modelData.renderProperties();
        float vrBodyYaw = FirstPersonManager.vrRuntime().getBodyYawDegrees(player, tickDelta);
        params.bodyYaw = Float.isFinite(vrBodyYaw) ? vrBodyYaw : player.renderYawOffset;
        params.bodyPitch = 0.0f;
        params.translation.zero();

        if (player.isElytraFlying()) {
            params.bodyPitch = player.rotationPitch + renderProperties.flyingPitch();
            params.translation.set(renderProperties.flyingTranslation());
        } else if (player.isSleeping()) {
            params.bodyYaw = player.getBedDirection().getHorizontalAngle() + 180.0f;
            params.bodyPitch = renderProperties.sleepingPitch();
            params.translation.set(renderProperties.sleepingTranslation());
        } else if (player.isSwimming()) {
            params.bodyPitch = player.rotationPitch + renderProperties.swimmingPitch();
            params.translation.set(renderProperties.swimmingTranslation());
        } else if (player.isCrouching()) {
            params.bodyPitch = renderProperties.crawlingPitch();
            params.translation.set(renderProperties.crawlingTranslation());
        }

        return params;
    }

    public static float[] getModelSize(ManagedModel modelData) {
        return new float[] {
                modelData.renderProperties().modelScale(),
                modelData.renderProperties().inventoryScale()
        };
    }
}
