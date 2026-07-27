package com.elfmcys.yesstevemodel.util;

import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.UseAction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class EquipmentUtil {
    private static final Map<String, EquipmentSlotType> SLOT_BY_NAME = createSlotMap();

    private EquipmentUtil() {
    }

    public static Optional<UseAction> getUseActionByName(String name) {
        if (name == null) {
            return Optional.empty();
        }

        String normalizedName = name.trim().toUpperCase(Locale.US);
        if (normalizedName.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UseAction.valueOf(normalizedName));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Optional<EquipmentSlotType> getEquipmentSlotByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SLOT_BY_NAME.get(name.trim().toLowerCase(Locale.US)));
    }

    private static Map<String, EquipmentSlotType> createSlotMap() {
        Map<String, EquipmentSlotType> slots = new HashMap<>();
        for (EquipmentSlotType slot : EquipmentSlotType.values()) {
            slots.put(slot.getName().toLowerCase(Locale.US), slot);
        }
        return slots;
    }
}
