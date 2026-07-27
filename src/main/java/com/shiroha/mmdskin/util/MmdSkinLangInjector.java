package com.shiroha.mmdskin.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.Language;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.LanguageMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.16.5 直移植说明：翻译不走资源包/classpath JSON（在本客户端不可靠），
 * 改为编译期嵌入（见 {@link MmdSkinLangData}，由 lang JSON 生成）。
 * 两条通道：
 * 1) inject(...)：ClientLanguageMap 构建时（语言重载）合入；
 * 2) installStaticFallback()：initClient 时安装静态 LanguageMap 委托包装，
 *    覆盖重载完成前与任何未走 ClientLanguageMap 的解析路径。
 */
public final class MmdSkinLangInjector {
    private static final Logger LOGGER = LogManager.getLogger();

    private MmdSkinLangInjector() {
    }

    public static void inject(List<Language> languages, Map<String, String> target) {
        for (Language language : languages) {
            loadLocale(language.getCode(), target);
        }
    }

    /** 在 initClient 安装静态兜底：en_us 打底，再叠加当前语言。 */
    public static void installStaticFallback() {
        LOGGER.info("[mmdskin] installing static language fallback...");
        Map<String, String> overlay = new HashMap<>();
        MmdSkinLangData.en_us(overlay);
        try {
            String code = net.minecraft.client.Minecraft.getInstance().gameSettings.language;
            if (code != null) {
                loadLocale(code.toLowerCase(java.util.Locale.ROOT), overlay);
            }
        } catch (Throwable ignored) {
        }

        LanguageMap previous = LanguageMap.getInstance();
        LanguageMap.func_240594_a_(new LanguageMap() {
            @Override
            public String func_230503_a_(String key) {
                String value = overlay.get(key);
                return value != null ? value : previous.func_230503_a_(key);
            }

            @Override
            public boolean func_230506_b_(String key) {
                return overlay.containsKey(key) || previous.func_230506_b_(key);
            }

            @Override
            public boolean func_230505_b_() {
                return previous.func_230505_b_();
            }

            @Override
            public IReorderingProcessor func_241870_a(ITextProperties properties) {
                return previous.func_241870_a(properties);
            }
        });
        LOGGER.info("[mmdskin] static language fallback installed ({} keys)", overlay.size());
    }

    private static void loadLocale(String code, Map<String, String> target) {
        switch (code) {
            case "zh_cn":
                MmdSkinLangData.zh_cn(target);
                break;
            case "ja_jp":
                MmdSkinLangData.ja_jp(target);
                break;
            case "en_us":
                MmdSkinLangData.en_us(target);
                break;
            default:
                // 其他语言：不覆盖（保持 en_us 打底）
                break;
        }
    }
}
