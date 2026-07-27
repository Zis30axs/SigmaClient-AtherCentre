package com.elfmcys.yesstevemodel.resource.models;

import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Port of upstream {@code resource/models/ModelPackData} (1.20.1): the parsed content of a model
 * pack's {@code ysm_pack.json} (https://ysm.cfpa.team/wiki/model-pack/).
 *
 * <p>Local note: the on-demand loader does not parse {@code ysm_pack.json}, so no real instances
 * with icons/translations exist yet; {@code PlayerModelScreen} synthesizes path-only instances for
 * its folder hierarchy. Pure data class, no behavior changes.
 */
public class ModelPackData {

    private final String path;

    private final String name;

    private final String description;

    @Nullable
    private final OuterFileTexture texture;

    @Nullable
    private final Map<String, Map<String, String>> translations;

    public ModelPackData(String path, String name, String description, @Nullable OuterFileTexture texture,
                         @Nullable Map<String, Map<String, String>> translations) {
        this.path = path;
        this.name = name;
        this.description = description;
        this.texture = texture;
        this.translations = translations;
    }

    public String getPath() {
        return this.path;
    }

    public String getName() {
        return this.name;
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    @Nullable
    public OuterFileTexture getTexture() {
        return this.texture;
    }

    @Nullable
    public Map<String, Map<String, String>> getTranslations() {
        return this.translations;
    }
}
