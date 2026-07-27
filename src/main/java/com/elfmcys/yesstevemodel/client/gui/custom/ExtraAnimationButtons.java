package com.elfmcys.yesstevemodel.client.gui.custom;

/**
 * A model-defined settings group, shown as one button in the extra-animation UI:
 * <pre>
 * "extra_animation_buttons": [
 *   { "id": "extra_config", "name": "0", "config_forms": [ ... ] }
 * ]
 * </pre>
 */
public class ExtraAnimationButtons {

    private final String id;

    private final String name;

    /** Present in the format but not observed populated by any real model. */
    private final String description;

    private final AbstractConfig[] configForms;

    public ExtraAnimationButtons(String id, String name, String description, AbstractConfig[] configForms) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.configForms = configForms;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public AbstractConfig[] getConfigForms() {
        return this.configForms;
    }
}
