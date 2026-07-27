package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.resource.models.AuthorInfo;
import com.elfmcys.yesstevemodel.resource.models.MainModelInfo;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Port of upstream {@code client/gui/ModelMetadataPresenter} (1.20.1).
 *
 * <p>Model packs ship their own translation tables ({@code ModelExtraResourcesFile#getTranslations},
 * surfaced as {@code ModelAssembly#getExpressionCache().getMetadata()}), keyed by locale then by a
 * dotted key such as {@code properties.extra_animation.<name>}. Every roulette label, tooltip,
 * config title and radio label goes through here.
 *
 * <p>Previously this class was a stub that returned the fallback unchanged, so a pack's own
 * localisation never appeared - the wheel showed raw animation ids instead of display names. Nothing
 * about that failed to compile.
 *
 * <p>Translation notes: {@code Minecraft#getLanguageManager().getSelected()} ->
 * {@code getLanguageManager().getCurrentLanguage().getCode()}; {@code Component.literal/translatable}
 * -> {@code StringTextComponent}/{@code TranslationTextComponent}; {@code ChatFormatting} ->
 * {@code TextFormatting} ({@code withStyle} -> {@code mergeStyle}); {@code CommonComponents.space()}
 * -> a literal " ".
 *
 * <p>The {@code getLocalizedString(ModelPackData, ...)} overload arrived with W6 step 4: the model
 * picker's pack search/description needs it. (It was previously cut when its only known callers
 * were the unported upload screens.)
 */
public class ModelMetadataPresenter {

    public static final String DEFAULT_LOCALE = "en_us";

    /**
     * Upstream {@code getLocalizedString(ModelPackData, key, defaultValue)}: locale -> en_us ->
     * fallback over a pack's own translation table.
     */
    public static String getLocalizedString(ModelPackData modelPackData, String key, @Nullable String defaultValue) {
        if (defaultValue == null) {
            defaultValue = StringPool.EMPTY;
        }
        Map<String, Map<String, String>> translations = modelPackData.getTranslations();
        if (translations == null || translations.isEmpty()) {
            return defaultValue;
        }
        String selectedLocale = currentLocale();
        if (translations.containsKey(selectedLocale)) {
            return translations.get(selectedLocale).getOrDefault(key, defaultValue);
        }
        if (translations.containsKey(DEFAULT_LOCALE)) {
            return translations.get(DEFAULT_LOCALE).getOrDefault(key, defaultValue);
        }
        return defaultValue;
    }

    public static String getLocalizedModelString(ModelAssembly modelAssembly, String key, String defaultValue) {
        return getLocalizedModelStringForLocale(modelAssembly, currentLocale(), key, defaultValue);
    }

    public static String getLocalizedModelStringForLocale(ModelAssembly modelAssembly, String locale, String key,
                                                          String defaultValue) {
        if (defaultValue == null) {
            defaultValue = StringPool.EMPTY;
        }
        if (modelAssembly == null || modelAssembly.getExpressionCache() == null) {
            return defaultValue;
        }
        Map<String, Map<String, String>> metadataMap = modelAssembly.getExpressionCache().getMetadata();
        if (metadataMap == null || metadataMap.isEmpty()) {
            return defaultValue;
        }
        if (metadataMap.containsKey(locale)) {
            return metadataMap.get(locale).getOrDefault(key, defaultValue);
        }
        if (metadataMap.containsKey(DEFAULT_LOCALE)) {
            return metadataMap.get(DEFAULT_LOCALE).getOrDefault(key, defaultValue);
        }
        return defaultValue;
    }

    public static String currentLocale() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getLanguageManager() == null || minecraft.getLanguageManager().getCurrentLanguage() == null) {
            return DEFAULT_LOCALE;
        }
        return minecraft.getLanguageManager().getCurrentLanguage().getCode();
    }

    public static List<ITextComponent> buildModelTooltip(ModelAssembly modelAssembly, String locale, String fileName,
                                                         boolean showAdvancedInfo) {
        List<ITextComponent> tooltipLines = Lists.newArrayList();
        Metadata extraInfo = modelAssembly.getModelData().getExtraInfo();

        if (extraInfo != null) {
            String localizedName = getLocalizedModelStringForLocale(modelAssembly, locale, "metadata.name", extraInfo.getName());
            if (StringUtils.isNoneBlank(localizedName)) {
                tooltipLines.add(new StringTextComponent(localizedName).mergeStyle(TextFormatting.GOLD));
            }

            String localizedTips = getLocalizedModelStringForLocale(modelAssembly, locale, "metadata.tips", extraInfo.getTips());
            if (StringUtils.isNoneBlank(localizedTips)) {
                Arrays.stream(localizedTips.replace("\r", StringPool.EMPTY).split("\n")).forEach(tipLine -> {
                    tooltipLines.add(new StringTextComponent(tipLine).mergeStyle(TextFormatting.GRAY));
                });
            }

            boolean hasLicense = StringUtils.isNoneBlank(extraInfo.getLicense().getFirst());
            if (!extraInfo.getAuthors().isEmpty() || hasLicense) {
                tooltipLines.add(new StringTextComponent(" "));
            }

            if (!extraInfo.getAuthors().isEmpty()) {
                String authorsString = StringUtils.join(extraInfo.getAuthors().stream()
                        .map(createAuthorNameMapper(modelAssembly, locale, new int[]{-1}))
                        .toArray(String[]::new), "丨");
                tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.authors",
                        new StringTextComponent(authorsString).mergeStyle(TextFormatting.DARK_GRAY)));
            }

            if (hasLicense) {
                tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.license",
                        new StringTextComponent(extraInfo.getLicense().getFirst()).mergeStyle(TextFormatting.DARK_GRAY)));
            }
        }

        if (showAdvancedInfo) {
            tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.file",
                    new StringTextComponent(fileName).mergeStyle(TextFormatting.DARK_GRAY)));
            tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.hash",
                    new StringTextComponent(modelAssembly.getModelData().getModelHash()).mergeStyle(TextFormatting.DARK_GRAY)));

            if (StringUtils.isNoneBlank(modelAssembly.getModelData().getExtra())) {
                tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.extra",
                        new StringTextComponent(modelAssembly.getModelData().getExtra()).mergeStyle(TextFormatting.DARK_GRAY)));
            }

            if (modelAssembly.getModelData().getTimestamp() != 0L) {
                String formattedDate = LocalDateTime
                        .ofInstant(Instant.ofEpochMilli(modelAssembly.getModelData().getTimestamp() * 1000L), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.timestamp",
                        new StringTextComponent(formattedDate).mergeStyle(TextFormatting.DARK_GRAY)));
            }

            if (StringUtils.isNoneBlank(modelAssembly.getModelData().getRand())) {
                tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.rand",
                        new StringTextComponent(modelAssembly.getModelData().getRand()).mergeStyle(TextFormatting.DARK_GRAY)));
            }
        }

        MainModelInfo info = modelAssembly.getModelData().getMainModelInfo();
        if (info != null) {
            tooltipLines.add(new StringTextComponent(" "));
            tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.main_model_info",
                    info.getBones(), info.getCubes(), info.getFaces()).mergeStyle(TextFormatting.GRAY));
            tooltipLines.add(new TranslationTextComponent("gui.yes_steve_model.model.texture_info",
                    modelAssembly.getAnimationBundle().getTextures().size()).mergeStyle(TextFormatting.GRAY));
        }

        return tooltipLines;
    }

    @NotNull
    private static Function<AuthorInfo, String> createAuthorNameMapper(ModelAssembly modelAssembly,
                                                                      String locale, int[] index) {
        return authorInfo -> {
            index[0] = index[0] + 1;
            String localizedAuthorName = getLocalizedModelStringForLocale(modelAssembly, locale,
                    String.format("metadata.authors.%d.name", Integer.valueOf(index[0])), authorInfo.getName());

            if (authorInfo.getRole().isEmpty()) {
                return localizedAuthorName;
            }
            String localizedRole = getLocalizedModelStringForLocale(modelAssembly, locale,
                    String.format("metadata.authors.%d.role", Integer.valueOf(index[0])), authorInfo.getRole());
            return localizedRole + ": " + localizedAuthorName;
        };
    }

    /** Convenience for callers that only have a nullable assembly. */
    public static String getLocalizedModelStringOrEmpty(@Nullable ModelAssembly modelAssembly, String key) {
        return modelAssembly == null ? StringPool.EMPTY
                : getLocalizedModelString(modelAssembly, key, StringPool.EMPTY);
    }
}
