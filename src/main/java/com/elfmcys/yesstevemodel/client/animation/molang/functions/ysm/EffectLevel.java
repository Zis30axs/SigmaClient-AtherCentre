package com.elfmcys.yesstevemodel.client.animation.molang.functions.ysm;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.funciton.ContextFunction;
import com.elfmcys.yesstevemodel.molang.runtime.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

public class EffectLevel extends ContextFunction<Entity> {
    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 1;
    }

    @Override
    public java.lang.Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int effects = 0;

        for (int i = 0; i < arguments.size(); i++) {
            ResourceLocation var5 = arguments.getResourceLocation(context, i);
            if (var5 != null) {
                Effect mobEffect = Registry.EFFECTS.getOptional(var5).orElse(null);
                if (mobEffect != null) {
                    if (context.entity().geoInstance() instanceof PlayerCapability cap
                            && !cap.isLocalPlayerModel()) {
                        effects += cap.getPositionTracker().getEffectAmplifier(mobEffect);
                    } else if (context.entity().entity() instanceof LivingEntity) {
                        EffectInstance mobEffectInstance = ((LivingEntity) context.entity().entity())
                                .getActivePotionEffect(mobEffect);
                        if (mobEffectInstance != null) {
                            effects += mobEffectInstance.getAmplifier() + 1;
                        }
                    } else {
                        if (!(context.entity().entity() instanceof ArrowEntity)) {
                            return null;
                        }

                        for (EffectInstance mobEffectInstance : ((ArrowEntity) context.entity().entity())
                                .getCustomPotionEffects()) {
                            if (mobEffectInstance.getPotion() == mobEffect) {
                                effects += mobEffectInstance.getAmplifier() + 1;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return effects;
    }
}
