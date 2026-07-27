package com.shiroha.mmdskin.render.entity;

import com.shiroha.mmdskin.player.render.InventoryRenderHelper;

import com.shiroha.mmdskin.MmdSkin;
import com.shiroha.mmdskin.model.runtime.ManagedModel;
import com.shiroha.mmdskin.model.runtime.ModelRequestKey;
import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.render.scene.RenderScene;
import com.shiroha.mmdskin.render.scene.MutableRenderPose;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MMD 鑷畾涔夊疄浣撴覆鏌撳櫒銆? */
public class MmdSkinRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            new ResourceLocation(MmdSkin.MOD_ID, "textures/entity/placeholder.png");

    protected final String modelName;

    private final MutableRenderPose reusablePose = new MutableRenderPose();
    private final Quaternionf reusableQuat = new Quaternionf();
    private final Vector3f reusableVec = new Vector3f();
    private final float[] reusableSize = new float[2];

    public MmdSkinRenderer(EntityRendererManager renderManager, String entityName) {
        super(renderManager);
        this.modelName = entityName.replace(':', '.');
    }

    @Override
    public void render(T entityIn, float entityYaw, float tickDelta, MatrixStack matrixStackIn,
                       IRenderTypeBuffer bufferIn, int packedLightIn) {
        super.render(entityIn, entityYaw, tickDelta, matrixStackIn, bufferIn, packedLightIn);

        ManagedModel model = ClientRenderRuntime.get().modelRepository()
                .acquire(ModelRequestKey.mob(entityIn, modelName));
        if (model == null) return;

        float[] size = parseModelSize(model, reusableSize);

        reusablePose.reset();
        EntityAnimationResolver.resolve(entityIn, model, entityYaw, tickDelta, reusablePose);

        matrixStackIn.push();

        if (entityIn instanceof LivingEntity living && living.isChild()) {
            matrixStackIn.scale(0.5f, 0.5f, 0.5f);
        }

        if (InventoryRenderHelper.isInventoryScreen()) {
            renderInInventory(entityIn, model, entityYaw, tickDelta, matrixStackIn, packedLightIn, size);
        } else {
            matrixStackIn.scale(size[0], size[0], size[0]);
            model.modelInstance().render(entityIn, reusablePose.bodyYaw, reusablePose.bodyPitch, reusablePose.translation,
                             tickDelta, matrixStackIn, packedLightIn, RenderScene.WORLD);
        }

        matrixStackIn.pop();
    }

    private void renderInInventory(T entityIn, ManagedModel model, float entityYaw,
                                    float tickDelta, MatrixStack matrixStack, int packedLight, float[] size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.currentScreen == null) return;

        // 1.16.5: no global model-view stack; use a local stack for GUI transform.
        MatrixStack modelViewStack = new MatrixStack();

        int posX = (mc.currentScreen.width - 176) / 2;
        int posY = (mc.currentScreen.height - 166) / 2;
        modelViewStack.translate(posX + 51, posY + 60, 50.0);
        modelViewStack.push();
        modelViewStack.scale(20.0f, 20.0f, -20.0f);
        modelViewStack.scale(size[1], size[1], size[1]);

        reusableQuat.identity()
                .rotateZ((float) Math.PI)
                .rotateX(-entityIn.rotationPitch * ((float) Math.PI / 180F))
                .rotateY(-entityIn.rotationYaw * ((float) Math.PI / 180F));
        rotateByJoml(modelViewStack, reusableQuat);

        reusableVec.set(0.0f);
        model.modelInstance().render(entityIn, entityYaw, 0.0f, reusableVec,
                          tickDelta, modelViewStack, packedLight, RenderScene.INVENTORY);
        modelViewStack.pop();
    }

    private static void rotateByJoml(MatrixStack stack, Quaternionf quat) {
        stack.rotate(new net.minecraft.util.math.vector.Quaternion(quat.x, quat.y, quat.z, quat.w));
    }

    private static float[] parseModelSize(ManagedModel model, float[] out) {
        out[0] = model.renderProperties().modelScale();
        out[1] = model.renderProperties().inventoryScale();
        return out;
    }

    @Override
    public ResourceLocation getEntityTexture(T entity) {
        return PLACEHOLDER_TEXTURE;
    }
}
