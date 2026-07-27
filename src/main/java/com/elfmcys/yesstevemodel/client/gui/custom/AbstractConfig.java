package com.elfmcys.yesstevemodel.client.gui.custom;

/**
 * One entry of a model's {@code extra_animation_buttons[].config_forms}:
 * <pre>
 * {
 *   "type": "checkbox",
 *   "title": "headdress/头饰",
 *   "description": "Used to hide/show the red bow headdress",
 *   "value": "v.roaming.red_bow_headdress"
 * }
 * </pre>
 * {@code value} is the molang variable the widget writes to.
 */
public abstract class AbstractConfig {

    private final String type;

    private final String title;

    private final String description;

    private final String value;

    public AbstractConfig(String type, String title, String description, String value) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.value = value;
    }

    public String getType() {
        return this.type;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getValue() {
        return this.value;
    }
}
