package net.minecraft.util.math.vector;

/**
 * mmdskin 移植辅助：JOML Matrix4f -> Mojang Matrix4f。
 * 必须位于 net.minecraft.util.math.vector 包内，才能写入 Matrix4f 的包级私有字段。
 * 注意命名换轴：JOML 用 m&lt;col&gt;&lt;row&gt;，Mojang 用 m&lt;row&gt;&lt;col&gt;。
 */
public final class JomlMatrix4fBridge {

    private JomlMatrix4fBridge() {
    }

    public static Matrix4f toMojang(org.joml.Matrix4f j) {
        Matrix4f m = new Matrix4f();
        m.m00 = j.m00(); m.m01 = j.m10(); m.m02 = j.m20(); m.m03 = j.m30();
        m.m10 = j.m01(); m.m11 = j.m11(); m.m12 = j.m21(); m.m13 = j.m31();
        m.m20 = j.m02(); m.m21 = j.m12(); m.m22 = j.m22(); m.m23 = j.m32();
        m.m30 = j.m03(); m.m31 = j.m13(); m.m32 = j.m23(); m.m33 = j.m33();
        return m;
    }
}
