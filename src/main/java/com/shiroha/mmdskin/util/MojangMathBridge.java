package com.shiroha.mmdskin.util;

import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.JomlMatrix4fBridge;
import org.joml.Quaternionf;

/**
 * JOML 与 1.16.5 Mojang 数学类型的桥接。
 * 1.16.5 MatrixStack.rotate 只接受 Mojang Quaternion；mul 只接受 Mojang Matrix4f。
 */
public final class MojangMathBridge {

    private MojangMathBridge() {
    }

    public static Quaternion toMojang(Quaternionf q) {
        return new Quaternion(q.x, q.y, q.z, q.w);
    }

    public static net.minecraft.util.math.vector.Matrix4f toMojang(org.joml.Matrix4f m) {
        return JomlMatrix4fBridge.toMojang(m);
    }
}
