package com.elfmcys.yesstevemodel.geckolib3.util;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import com.elfmcys.yesstevemodel.molang.runtime.Function;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;

public class MolangUtils {

    private static final HashMap<String, EquipmentSlotType> SLOT_MAP = new HashMap<>();

    static {
        SLOT_MAP.put("chest", EquipmentSlotType.CHEST);
        SLOT_MAP.put("feet", EquipmentSlotType.FEET);
        SLOT_MAP.put("head", EquipmentSlotType.HEAD);
        SLOT_MAP.put("legs", EquipmentSlotType.LEGS);
        SLOT_MAP.put("mainhand", EquipmentSlotType.MAINHAND);
        SLOT_MAP.put("offhand", EquipmentSlotType.OFFHAND);
    }

    public static float normalizeTime(long timestamp) {
        return ((float) (timestamp + 6000L) / 24000) % 1;
    }

    @Nullable
    public static BlockState getRelativeBlockState(ExecutionContext<IContext<Entity>> context, Function.ArgumentCollection args) {
        return getRelativeBlockStateAt(context, args, 0);
    }

    @Nullable
    public static BlockState getRelativeBlockStateAt(ExecutionContext<IContext<Entity>> context, Function.ArgumentCollection args, int i) {
        double deltaX = args.getAsDouble(context, i);
        double deltaY = args.getAsDouble(context, i + 1);
        double deltaZ = args.getAsDouble(context, i + 2);
        if (Math.abs(deltaX) > 5.0d || Math.abs(deltaY) > 5.0d || Math.abs(deltaZ) > 5.0d) {
            return null;
        }
        Entity entity = context.entity().entity();
        return entity.world.getBlockState(new BlockPos((int) Math.round((entity.getPosX() + deltaX) - 0.5d), (int) Math.round((entity.getPosY() + deltaY) - 0.5d), (int) Math.round((entity.getPosZ() + deltaZ) - 0.5d)));
    }

    @Nullable
    public static EquipmentSlotType parseSlotType(IContext<?> context, String value) {
        if (value == null) {
            return null;
        }
        EquipmentSlotType equipmentSlot = SLOT_MAP.get(value.toLowerCase(Locale.ENGLISH));
        if (equipmentSlot == null) {
            context.logWarning("Illegal slot type: %s.", value);
        }
        return equipmentSlot;
    }
}