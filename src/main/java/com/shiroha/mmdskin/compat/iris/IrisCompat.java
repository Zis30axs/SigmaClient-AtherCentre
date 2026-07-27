package com.shiroha.mmdskin.compat.iris;

/**
 * 文件职责：Iris 光影兼容占位。纯客户端化移植中 Iris 联动已移除，
 * 保留最小布尔门控（恒 false），使渲染核心无需改动调用点即可编译运行。
 */
public final class IrisCompat {

    private IrisCompat() {
    }

    public static boolean isIrisShaderActive() {
        return false;
    }

    public static boolean isRenderingShadows() {
        return false;
    }

    public static void reset() {
    }
}
