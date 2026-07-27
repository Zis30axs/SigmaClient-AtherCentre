package com.shiroha.mmdskin.util;

import net.minecraft.client.util.InputMappings;
import net.minecraft.client.settings.KeyBinding;

/**
 * 1.16.5 直移植说明：KeyBinding.keyCode 字段为 public，
 * 原版本经 Fabric mixin accessor 间接读取的机制不再需要。
 */
public class KeyMappingUtil {

    public static InputMappings.Input getBoundKey(KeyBinding keyMapping) {
        if (keyMapping == null || keyMapping.keyCode == null) {
            return InputMappings.INPUT_INVALID;
        }
        return keyMapping.keyCode;
    }
}
