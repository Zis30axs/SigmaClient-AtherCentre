/* 鏂囦欢鑱岃矗锛氭牴鎹墜閮ㄧ煩闃甸┍鍔ㄥ弻鎵嬬墿鍝佺殑娓叉煋濮挎€併€?*/
package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.bridge.runtime.NativeMatrixPort;
import com.shiroha.mmdskin.config.ModelConfigData;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Hand;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 鏂囦欢鑱岃矗锛氭牴鎹墜閮ㄧ煩闃甸┍鍔ㄥ弻鎵嬬墿鍝佺殑娓叉煋濮挎€併€?*/
public class ItemRenderHelper {
    public interface ExternalItemRenderer {
        boolean renderItems(AbstractClientPlayerEntity player, ManagedModel model,
                            MatrixStack matrixStack, IRenderTypeBuffer vertexConsumers,
                            int packedLight, float tickDelta, float modelScale, float heldItemScale);
    }

    private static final ExternalItemRenderer NOOP_EXTERNAL_ITEM_RENDERER = (player, model, matrixStack,
                                                                             vertexConsumers, packedLight,
                                                                             tickDelta, modelScale,
                                                                             heldItemScale) -> false;

    private static final float DEG_TO_RAD = (float) Math.PI / 180F;
    private static final NativeMatrixPort NOOP_MATRIX_PORT = new NativeMatrixPort() {
        @Override
        public long createMatrix() {
            return 0L;
        }

        @Override
        public void deleteMatrix(long matrixHandle) {
        }

        @Override
        public void populateHandMatrix(long modelHandle, long handMatrixHandle, boolean mainHand) {
        }

        @Override
        public boolean copyMatrixToBuffer(long matrixHandle, ByteBuffer targetBuffer) {
            return false;
        }
    };

    private static volatile NativeMatrixPort matrixPort = NOOP_MATRIX_PORT;
    private static volatile ExternalItemRenderer externalItemRenderer = NOOP_EXTERNAL_ITEM_RENDERER;

    public static void configureRuntimeCollaborators(NativeMatrixPort matrixPort) {
        ItemRenderHelper.matrixPort = matrixPort != null ? matrixPort : NOOP_MATRIX_PORT;
    }

    public static void setExternalItemRenderer(ExternalItemRenderer renderer) {
        externalItemRenderer = renderer != null ? renderer : NOOP_EXTERNAL_ITEM_RENDERER;
    }

    public static void renderItems(AbstractClientPlayerEntity player, ManagedModel model,
                                   MatrixStack matrixStack, IRenderTypeBuffer vertexConsumers,
                                   int packedLight, float heldItemScale, float tickDelta, float modelScale) {
        if (externalItemRenderer.renderItems(player, model, matrixStack, vertexConsumers,
                packedLight, tickDelta, modelScale, heldItemScale)) {
            return;
        }

        renderHandItem(
                player,
                model,
                matrixStack,
                vertexConsumers,
                packedLight,
                Hand.MAIN_HAND,
                heldItemScale);
        renderHandItem(
                player,
                model,
                matrixStack,
                vertexConsumers,
                packedLight,
                Hand.OFF_HAND,
                heldItemScale);
    }

    public static boolean applyMmdHandMatrix(ManagedModel model, MatrixStack matrixStack, Hand hand) {
        matrixStack.getLast().getMatrix().mul(com.shiroha.mmdskin.util.MojangMathBridge.toMojang(getMmdHandMatrix(model, hand)));
        return true;
    }

    public static Matrix4f getMmdHandMatrix(ManagedModel model, Hand hand) {
        boolean isMainHand = hand == Hand.MAIN_HAND;
        NativeMatrixPort runtimeBridge = matrixPort;
        long modelHandle = model.modelInstance().getModelHandle();
        long handMat = isMainHand ? model.entityState().rightHandMat : model.entityState().leftHandMat;

        runtimeBridge.populateHandMatrix(modelHandle, handMat, isMainHand);
        return convertToMatrix4f(runtimeBridge, handMat, model.entityState().matBuffer);
    }

    private static void renderHandItem(AbstractClientPlayerEntity player, ManagedModel model,
                                          MatrixStack matrixStack, IRenderTypeBuffer vertexConsumers,
                                          int packedLight, Hand hand, float heldItemScale) {
        ItemStack itemStack = player.getHeldItem(hand);
        if (itemStack.isEmpty()) {
            return;
        }

        boolean isMainHand = (hand == Hand.MAIN_HAND);
        NativeMatrixPort runtimeBridge = matrixPort;
        long modelHandle = model.modelInstance().getModelHandle();
        long handMat = isMainHand ? model.entityState().rightHandMat : model.entityState().leftHandMat;
        float itemScale = resolveItemScale(heldItemScale);

        runtimeBridge.populateHandMatrix(modelHandle, handMat, isMainHand);

        matrixStack.push();
        matrixStack.getLast().getMatrix().mul(com.shiroha.mmdskin.util.MojangMathBridge.toMojang(convertToMatrix4f(runtimeBridge, handMat, model.entityState().matBuffer)));

        matrixStack.rotate(com.shiroha.mmdskin.util.MojangMathBridge.toMojang(new org.joml.Quaternionf().rotateX(90.0f * DEG_TO_RAD)));
        matrixStack.rotate(com.shiroha.mmdskin.util.MojangMathBridge.toMojang(new org.joml.Quaternionf().rotateY(180.0f * DEG_TO_RAD)));

        applyConfiguredRotation(matrixStack, player, model, hand);

        float baseScale = 10.0f * itemScale;
        matrixStack.scale(baseScale, baseScale, baseScale);

        ItemCameraTransforms.TransformType displayCtx = isMainHand
                ? ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND
                : ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND;

        Minecraft.getInstance().getItemRenderer().renderItem(
            player, itemStack, displayCtx, !isMainHand,
            matrixStack, vertexConsumers, player.world, packedLight, OverlayTexture.NO_OVERLAY
        );

        matrixStack.pop();
    }

    private static void applyConfiguredRotation(MatrixStack matrixStack, AbstractClientPlayerEntity player,
                                                ManagedModel model, Hand hand) {
        for (String axis : new String[]{"x", "y", "z"}) {
            float rotation = getItemRotation(player, model, hand, axis);
            if (rotation != 0.0f) {
                Quaternionf q = new Quaternionf();
                switch (axis) {
                    case "x" -> q.rotateX(rotation * DEG_TO_RAD);
                    case "y" -> q.rotateY(rotation * DEG_TO_RAD);
                    case "z" -> q.rotateZ(rotation * DEG_TO_RAD);
                }
                matrixStack.rotate(com.shiroha.mmdskin.util.MojangMathBridge.toMojang(q));
            }
        }
    }

    private static float getItemRotation(AbstractClientPlayerEntity player, ManagedModel model,
                                        Hand hand, String axis) {
        String itemId = getItemId(player, hand);
        String handStr = (hand == Hand.MAIN_HAND) ? "Right" : "Left";
        String handState = getHandState(player, hand);

        String specificKey = itemId + "_" + handStr + "_" + handState + "_" + axis;
        String specificValue = model.properties.getProperty(specificKey);
        if (specificValue != null) {
            return parseFloatSafe(specificValue, 0.0f);
        }

        String defaultKey = "default_" + axis;
        String defaultValue = model.properties.getProperty(defaultKey);
        if (defaultValue != null) {
            return parseFloatSafe(defaultValue, 0.0f);
        }

        return 0.0f;
    }

    private static float parseFloatSafe(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isFinitePositive(float value) {
        return Float.isFinite(value) && value > 0.0f;
    }

    private static float resolveItemScale(float heldItemScale) {
        return isFinitePositive(heldItemScale)
                ? heldItemScale
                : ModelConfigData.DEFAULT_HELD_ITEM_SCALE;
    }

    private static String getHandState(AbstractClientPlayerEntity player, Hand hand) {
        if (hand == player.getActiveHand() && player.isHandActive()) {
            return "using";
        } else if (hand == player.swingingHand && player.isSwingInProgress) {
            return "swinging";
        }
        return "idle";
    }

    private static String getItemId(AbstractClientPlayerEntity player, Hand hand) {
        String descriptionId = player.getHeldItem(hand).getItem().getTranslationKey();
        return descriptionId.substring(descriptionId.indexOf(".") + 1);
    }

    public static Matrix4f convertToMatrix4f(NativeMatrixPort runtimeBridge, long matId, ByteBuffer buf) {
        buf.clear();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        if (!runtimeBridge.copyMatrixToBuffer(matId, buf)) {
            return new Matrix4f();
        }
        buf.position(0);
        Matrix4f result = new Matrix4f(
            buf.getFloat(0),  buf.getFloat(16), buf.getFloat(32), buf.getFloat(48),
            buf.getFloat(4),  buf.getFloat(20), buf.getFloat(36), buf.getFloat(52),
            buf.getFloat(8),  buf.getFloat(24), buf.getFloat(40), buf.getFloat(56),
            buf.getFloat(12), buf.getFloat(28), buf.getFloat(44), buf.getFloat(60)
        );
        result.transpose();
        return result;
    }
}
