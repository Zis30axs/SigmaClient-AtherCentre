package com.shiroha.mmdskin.player.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.shiroha.mmdskin.model.runtime.ModelInstance;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.util.MojangMathBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class InventoryRenderHelper {

    public static boolean isInventoryScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.currentScreen == null) return false;
        String className = mc.currentScreen.getClass().getName();
        return className.contains("InventoryScreen") || className.contains("class_490");
    }

    public static void renderInInventory(AbstractClientPlayerEntity player, ModelInstance model, float entityYaw,
                                         float tickDelta, MatrixStack matrixStack, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.currentScreen == null) return;

        MatrixStack localStack = new MatrixStack();

        int posX = (mc.currentScreen.width - 176) / 2;
        int posY = (mc.currentScreen.height - 166) / 2;
        localStack.translate(posX + 51, posY + 75, 50.0);

        float inventorySize = size[1];
        localStack.scale(inventorySize, inventorySize, inventorySize);
        localStack.scale(20.0f, 20.0f, -20.0f);

        Quaternionf rotation = calculateRotation(player);
        localStack.rotate(MojangMathBridge.toMojang(rotation));

        model.render(player, entityYaw, 0.0f, new Vector3f(0.0f), tickDelta, localStack, packedLight, RenderScene.INVENTORY);

        Quaternionf bodyRotation = new Quaternionf().rotateY(-player.renderYawOffset * ((float) Math.PI / 180F));
        matrixStack.rotate(MojangMathBridge.toMojang(bodyRotation));
        matrixStack.scale(inventorySize, inventorySize, inventorySize);
        matrixStack.scale(0.09f, 0.09f, 0.09f);
    }

    private static Quaternionf calculateRotation(AbstractClientPlayerEntity player) {
        Quaternionf quaternion = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitch = new Quaternionf().rotateX(-player.rotationPitch * ((float) Math.PI / 180F));
        Quaternionf yaw = new Quaternionf().rotateY(-player.renderYawOffset * ((float) Math.PI / 180F));
        quaternion.mul(pitch);
        quaternion.mul(yaw);
        return quaternion;
    }
}
