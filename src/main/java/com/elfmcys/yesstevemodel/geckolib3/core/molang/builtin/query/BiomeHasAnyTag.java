package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.query;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.registry.WorldGenRegistries;

public class BiomeHasAnyTag extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        Entity entity = context.entity().entity();
        Biome biome = entity.world.getBiome(entity.getPosition());
        ResourceLocation biomeId = WorldGenRegistries.BIOME.getKey(biome);
        if (biomeId == null) return false;
        for (int i = 0; i < arguments.size(); i++) {
            ResourceLocation id = arguments.getResourceLocation(context, i);
            if (id == null) continue;
            if (biomeId.equals(id)) return true;
        }
        return false;
    }
    @Override
    public boolean validateArgumentSize(int size) { return size >= 1; }
}