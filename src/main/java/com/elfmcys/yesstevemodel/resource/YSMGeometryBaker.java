package com.elfmcys.yesstevemodel.resource;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.resource.models.GeometryDescription;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Pure-Java geometry baker ported from OpenYSM 2.6.5 {@code YSMClientMapper}.
 * Turns parsed {@link RawYsmModel.RawGeometry} into a geckolib3 {@link GeoModel}
 * with populated {@code bakedBones}, which {@code NativeModelRenderer.renderModel}
 * consumes. The JNI native-cache path is intentionally omitted (native lib cut).
 */
public final class YSMGeometryBaker {

    private YSMGeometryBaker() {
    }

    public static GeoModel buildMesh(RawYsmModel.RawGeometry rawGeo, GeometryDescription context, int textureCount) {
        if (rawGeo == null || rawGeo.bones.isEmpty()) {
            return buildMesh(new GeoBone[0], new HashMap<>(), context, textureCount);
        }

        List<GeoBone> geoBones = new ArrayList<>();
        List<GeoModel.BakedBone> bakedBones = new ArrayList<>();
        Map<String, String> parentMap = new HashMap<>();

        for (RawYsmModel.RawBone rb : rawGeo.bones) {
            parentMap.put(rb.name, rb.parentName);
            geoBones.add(new GeoBone(rb.name, false, false, false, rb.pivot[0], rb.pivot[1], rb.pivot[2], rb.rotation[0], rb.rotation[1], rb.rotation[2]));

            GeoModel.BakedBone bb = new GeoModel.BakedBone();
            bb.name = rb.name;
            if (rb.name.startsWith("ysmGlow")) bb.glow = true;
            bb.pivotX = rb.pivot[0];
            bb.pivotY = rb.pivot[1];
            bb.pivotZ = rb.pivot[2];
            bb.rotX = rb.rotation[0];
            bb.rotY = rb.rotation[1];
            bb.rotZ = rb.rotation[2];
            bb.parentIdx = -1;

            for (RawYsmModel.RawCube rc : rb.cubes) {
                GeoModel.BakedCube bc = new GeoModel.BakedCube();
                boolean isNegativeVolume = false;

                for (RawYsmModel.RawFace rf : rc.faces) {
                    Vector3f v0 = new Vector3f(rf.positions[0][0], rf.positions[0][1], rf.positions[0][2]);
                    Vector3f v1 = new Vector3f(rf.positions[1][0], rf.positions[1][1], rf.positions[1][2]);
                    Vector3f v2 = new Vector3f(rf.positions[2][0], rf.positions[2][1], rf.positions[2][2]);
                    Vector3f normal = new Vector3f(rf.normal[0], rf.normal[1], rf.normal[2]);
                    Vector3f e1 = new Vector3f(v1).sub(v0);
                    Vector3f e2 = new Vector3f(v2).sub(v1);
                    Vector3f cross = new Vector3f(e1).cross(e2);
                    if (cross.dot(normal) < -1e-5f) {
                        isNegativeVolume = true;
                        break;
                    }
                }

                if (!isNegativeVolume) {
                    for (int i = 0; i < rc.faces.size(); i++) {
                        RawYsmModel.RawFace faceA = rc.faces.get(i);
                        Vector3f normA = new Vector3f(faceA.normal[0], faceA.normal[1], faceA.normal[2]);

                        for (int j = i + 1; j < rc.faces.size(); j++) {
                            RawYsmModel.RawFace faceB = rc.faces.get(j);
                            Vector3f normB = new Vector3f(faceB.normal[0], faceB.normal[1], faceB.normal[2]);
                            if (normA.dot(normB) < -0.99f) {
                                Vector3f centerA = getFaceCenter(faceA);
                                Vector3f centerB = getFaceCenter(faceB);
                                Vector3f diff = new Vector3f(centerA).sub(centerB);
                                if (diff.dot(normA) < -1e-5f) {
                                    isNegativeVolume = true;
                                    break;
                                }
                            }
                        }
                        if (isNegativeVolume) break;
                    }
                }

                bc.cullable = !(bb.glow && !isNegativeVolume);

                for (RawYsmModel.RawFace rf : rc.faces) {
                    GeoModel.BakedQuad bq = new GeoModel.BakedQuad();
                    bq.normal = new Vector3f(rf.normal[0], rf.normal[1], rf.normal[2]);
                    bq.positions = new Vector3f[4];
                    bq.uvs = new Vector2f[4];
                    for (int i = 0; i < 4; i++) {
                        bq.positions[i] = new Vector3f(rf.positions[i][0], rf.positions[i][1], rf.positions[i][2]);
                        bq.uvs[i] = new Vector2f(rf.u[i], rf.v[i]);
                    }
                    bc.quads.add(bq);
                }
                bb.cubes.add(bc);
            }
            bakedBones.add(bb);
        }

        for (GeoModel.BakedBone b : bakedBones) {
            String parentName = parentMap.get(b.name);
            if (parentName != null && !parentName.isEmpty()) {
                for (int i = 0; i < bakedBones.size(); i++) {
                    if (bakedBones.get(i).name.equals(parentName)) {
                        b.parentIdx = i;
                        break;
                    }
                }
            }
            if (b.name.equals("LeftArm")) b.partMask = 1;
            else if (b.name.equals("RightArm")) b.partMask = 2;
            else if (b.name.equals("Background")) b.partMask = 3;
            else if (b.parentIdx != -1) b.partMask = bakedBones.get(b.parentIdx).partMask;
            else b.partMask = 0;
        }

        GeoModel mesh = buildMesh(geoBones.toArray(new GeoBone[0]), parentMap, context, textureCount);
        mesh.bakedBones = bakedBones;
        return mesh;
    }

    public static GeoModel buildMesh(GeoBone[] bones, Map<String, String> parentMap, GeometryDescription context, int textureCount) {
        String[][] boneNameArrays = buildBoneNameArrays(parentMap);
        boolean[] flags = new boolean[]{parentMap.containsKey("LeftArm"), parentMap.containsKey("RightArm"), parentMap.containsKey("Background")};
        boolean[] translucencyArray = new boolean[Math.max(1, textureCount)];
        return new GeoModel(bones, boneNameArrays, flags, context, translucencyArray);
    }

    public static GeometryDescription buildContext(RawYsmModel.RawGeometry model) {
        float[] offset = model.visibleBoundsOffset;
        double[] visibleBoundsOffset = offset == null ? new double[0]
                : IntStream.range(0, offset.length).mapToDouble(i -> offset[i]).toArray();
        return new GeometryDescription(
                model.identifier,
                model.textureWidth,
                model.textureHeight,
                model.visibleBoundsWidth,
                model.visibleBoundsHeight,
                visibleBoundsOffset
        );
    }

    private static String[][] buildBoneNameArrays(Map<String, String> parentMap) {
        String[][] arrays = new String[35][];
        String[] targetLocators = new String[]{
                "LeftHandLocator",
                "RightHandLocator",
                "ElytraLocator",
                "PistolLocator",
                "RifleLocator",
                "LeftWaistLocator",
                "RightWaistLocator",
                "LeftShoulderLocator",
                "RightShoulderLocator",
                "BladeLocator",
                "SheathLocator",
                "Head",
                "BackpackLocator",
                "LeftHandLocator2",
                "LeftHandLocator3",
                "LeftHandLocator4",
                "LeftHandLocator5",
                "LeftHandLocator6",
                "LeftHandLocator7",
                "LeftHandLocator8",
                "RightHandLocator2",
                "RightHandLocator3",
                "RightHandLocator4",
                "RightHandLocator5",
                "RightHandLocator6",
                "RightHandLocator7",
                "RightHandLocator8",
                "PassengerLocator",
                "PassengerLocator2",
                "PassengerLocator3",
                "PassengerLocator4",
                "PassengerLocator5",
                "PassengerLocator6",
                "PassengerLocator7",
                "PassengerLocator8"
        };

        for (int i = 0; i < arrays.length; i++) {
            if (targetLocators[i] != null && !targetLocators[i].isEmpty()) {
                arrays[i] = buildPath(targetLocators[i], parentMap);
            } else {
                arrays[i] = new String[0];
            }
        }
        return arrays;
    }

    private static String[] buildPath(String targetBone, Map<String, String> parentMap) {
        if (!parentMap.containsKey(targetBone)) {
            return new String[0];
        }
        List<String> path = new ArrayList<>();
        String current = targetBone;
        while (current != null && !current.isEmpty()) {
            path.add(current);
            current = parentMap.get(current);
        }
        Collections.reverse(path);
        return path.toArray(new String[0]);
    }

    private static Vector3f getFaceCenter(RawYsmModel.RawFace face) {
        Vector3f center = new Vector3f(0, 0, 0);
        for (int i = 0; i < 4; i++) {
            center.add(face.positions[i][0], face.positions[i][1], face.positions[i][2]);
        }
        return center.div(4.0f);
    }
}
