package com.elfmcys.yesstevemodel.geckolib3.util;

/**
 * Boundary conversion between vanilla (MCP 1.16.5, row-major m&lt;row&gt;&lt;col&gt;)
 * matrices and the JOML (column-major m&lt;col&gt;&lt;row&gt;) matrices used inside geckolib3.
 */
public final class JomlMatrix4fBridge {
    private JomlMatrix4fBridge() {
    }

    public static org.joml.Matrix4f fromVanilla(net.minecraft.util.math.vector.Matrix4f vanilla) {
        float[] m = new float[16];
        vanilla.write(m);
        return new org.joml.Matrix4f(
                m[0], m[4], m[8], m[12],
                m[1], m[5], m[9], m[13],
                m[2], m[6], m[10], m[14],
                m[3], m[7], m[11], m[15]);
    }
}