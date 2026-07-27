package com.shiroha.mmdskin;

import com.shiroha.mmdskin.render.bootstrap.ClientRenderRuntime;
import com.shiroha.mmdskin.util.VectorParseUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

public class MmdSkinClient {
    public static final Logger logger = LogManager.getLogger();

    private static volatile boolean initialized = false;

    /** mmdskin 是否成功初始化（原生库加载失败等场景返回 false，功能整体停用）。 */
    public static boolean isInitialized() {
        return initialized;
    }

    public static void initClient() {
        try {
            MmdClientResourceBootstrap.initialize();
            ClientRenderRuntime.initialize();
            com.shiroha.mmdskin.util.MmdSkinLangInjector.installStaticFallback();
            logger.info("[mmdskin] native engine version: {}", NativeFunc.GetInst().GetVersion());
            initialized = true;
        } catch (Throwable t) {
            // 二次分发保险：原生库不可用（非 Windows-x64 等）时停用 mmdskin 而非崩溃。
            initialized = false;
            logger.error("[mmdskin] init failed; mmdskin disabled for this session", t);
        }
    }

    public static String calledFrom(int i){
        StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
        if (steArray.length <= i) {
            return "";
        }
        return steArray[i].getClassName();
    }

    public static Vector3f str2Vec3f(String arg){
        return VectorParseUtil.parseVec3f(arg);
    }

}
