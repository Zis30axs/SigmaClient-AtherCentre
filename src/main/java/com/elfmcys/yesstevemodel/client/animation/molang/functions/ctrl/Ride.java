package com.elfmcys.yesstevemodel.client.animation.molang.functions.ctrl;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.StringUtils;

public class Ride extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String VEHICLE_KEY = "vehicle";

    private static final String PASSENGER_KEY = "passenger";

    public static Ride create() {
        return new Ride();
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        Entity firstPassenger;
        String type = arguments.getAsString(context, 0);
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        if (VEHICLE_KEY.equals(type)) {
            firstPassenger = entity.getRidingEntity();
        } else if (PASSENGER_KEY.equals(type)) {
            firstPassenger = entity.getPassengers().isEmpty() ? null : entity.getPassengers().get(0);
        } else {
            return 0;
        }
        if (firstPassenger == null || !firstPassenger.isAlive()) {
            return 0;
        }
        String strSubstring = id.substring(1);
        EntityType<?> entityType = firstPassenger.getType();
        if (id.startsWith(PREFIX_ITEM_ID)) {
            ResourceLocation key = Registry.ENTITY_TYPE.getKey(entityType);
            if (key == null) {
                return 0;
            }
            return strSubstring.equals(key.toString()) ? 1 : 0;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            ITag<EntityType<?>> tag = EntityTypeTags.getCollection().get(new ResourceLocation(strSubstring));
            return tag != null && tag.contains(entityType) ? 1 : 0;
        }
        return 0;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}
