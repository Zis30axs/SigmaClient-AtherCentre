package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.JomlMatrix4fBridge;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Port note: upstream is a plain static utility (the native SIMD path is disabled there too, so only
 * the Java {@code renderModel} is ported). It must NOT implement {@link IGeoRenderer} - the renderers
 * own that interface.
 */
public final class NativeModelRenderer {

    private NativeModelRenderer() {
    }

    public static void renderMesh(IVertexBuilder buffer, MatrixStack.Entry pose, GeoModel model, float[] matrixData, float[] absPivotData, int textureIndex, int renderPartMask, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        renderModel(buffer, pose, model, matrixData, renderPartMask, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public static void renderModel(IVertexBuilder vertexConsumer, MatrixStack.Entry pose, GeoModel mesh, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a) {
        if (mesh.bakedBones == null || mesh.bakedBones.isEmpty()) return;
        int boneCount = mesh.bakedBones.size();
        if (boneParams == null || boneParams.length < boneCount * 12) return;

        Matrix4f rootPoseMat = JomlMatrix4fBridge.fromVanilla(pose.getMatrix());
        Matrix4f identityMat = new Matrix4f();
        Matrix4f globalBoneMat = new Matrix4f();
        Matrix3f globalNormalMat = new Matrix3f();
        Vector4f tempPos = new Vector4f();
        Vector3f tempNorm = new Vector3f();
        Matrix4f[] boneLocalTransforms = new Matrix4f[boneCount];
        boolean[] boneVisible = new boolean[boneCount];

        for (int i = 0; i < boneCount; i++) {
            calculateBoneMatrix(i, mesh.bakedBones, boneParams, boneLocalTransforms, boneVisible, identityMat);
        }

        for (int i = 0; i < boneCount; i++) {
            if (!boneVisible[i]) continue;
            GeoModel.BakedBone bone = mesh.bakedBones.get(i);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) continue;

            Matrix4f localBoneMat = boneLocalTransforms[i];
            globalBoneMat.set(rootPoseMat).mul(localBoneMat);
            globalBoneMat.normal(globalNormalMat);

            int currentPackedLight = bone.glow ? LightTexture.packLight(15, 15) : packedLight;

            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    // PORT-REVIEW: the source does clip-space backface culling here using the projection
                    // matrix (RenderSystem.getProjectionMatrix(), a 1.17+ API). Culling is disabled in this
                    // initial 1.16.5 port so no faces go missing; re-enable by reading the projection matrix
                    // via GL11.glGetFloatv(GL_PROJECTION_MATRIX) after runtime verification.
                    tempNorm.set(quad.normal).mul(globalNormalMat).normalize();
                    for (int v = 0; v < 4; v++) {
                        globalBoneMat.transform(tempPos.set(quad.positions[v].x(), quad.positions[v].y(), quad.positions[v].z(), 1.0f));
                        vertexConsumer.pos(tempPos.x(), tempPos.y(), tempPos.z())
                                .color(r, g, b, a)
                                .tex(quad.uvs[v].x(), quad.uvs[v].y())
                                .overlay(packedOverlay)
                                .lightmap(currentPackedLight)
                                .normal(tempNorm.x(), tempNorm.y(), tempNorm.z())
                                .endVertex();
                    }
                }
            }
        }
    }

    private static Matrix4f calculateBoneMatrix(int idx, List<GeoModel.BakedBone> bones, float[] boneParams, Matrix4f[] cache, boolean[] visibleCache, Matrix4f rootPose) {
        if (cache[idx] != null) return cache[idx];
        GeoModel.BakedBone bone = bones.get(idx);
        Matrix4f parentMatrix = rootPose;
        boolean isVisible = true;
        if (bone.parentIdx != -1) {
            parentMatrix = calculateBoneMatrix(bone.parentIdx, bones, boneParams, cache, visibleCache, rootPose);
            if (!visibleCache[bone.parentIdx]) isVisible = false;
        }
        Matrix4f localMat = new Matrix4f(parentMatrix);
        int pOffset = idx * 12;
        float animRx = boneParams[pOffset];
        float animRy = boneParams[pOffset + 1];
        float animRz = boneParams[pOffset + 2];
        float animTx = boneParams[pOffset + 3];
        float animTy = boneParams[pOffset + 4];
        float animTz = boneParams[pOffset + 5];
        float animSx = boneParams[pOffset + 6];
        float animSy = boneParams[pOffset + 7];
        float animSz = boneParams[pOffset + 8];
        if (animSx == 0.0f && animSy == 0.0f && animSz == 0.0f) isVisible = false;
        localMat.translate((bone.pivotX - animTx) * 0.0625f, (bone.pivotY + animTy) * 0.0625f, (bone.pivotZ + animTz) * 0.0625f);
        localMat.rotateZ(animRz);
        localMat.rotateY(animRy);
        localMat.rotateX(animRx);
        if (animSx != 1.0f || animSy != 1.0f || animSz != 1.0f) localMat.scale(animSx, animSy, animSz);
        localMat.translate(-bone.pivotX / 16f, -bone.pivotY / 16f, -bone.pivotZ / 16f);
        cache[idx] = localMat;
        visibleCache[idx] = isVisible;
        return localMat;
    }
}
