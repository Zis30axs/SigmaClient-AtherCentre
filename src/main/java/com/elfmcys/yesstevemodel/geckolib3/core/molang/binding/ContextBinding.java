package com.elfmcys.yesstevemodel.geckolib3.core.molang.binding;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.block.BlockBehaviorVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.block.BlockStateVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.block.BlockVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity.*;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.item.ItemStackVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.item.ItemVariable;
import com.elfmcys.yesstevemodel.molang.parser.ast.StringExpression;
import com.elfmcys.yesstevemodel.molang.runtime.Function;
import com.elfmcys.yesstevemodel.molang.runtime.binding.ObjectBinding;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import java.util.Set;

public class ContextBinding implements ObjectBinding {

    public final Object2ReferenceOpenHashMap<String, Object> bindings = new Object2ReferenceOpenHashMap<>();

    @Override
    public Object getProperty(String str) {
        return this.bindings.get(str);
    }

    public Set<String> getKeys() {
        return this.bindings.keySet();
    }

    public void function(String name, Function function) {
        bindings.put(name, function);
    }

    public void constValue(String name, Object obj) {
        if (obj instanceof String) {
            this.bindings.put(name, new StringExpression((String) obj));
            return;
        }
        if (obj instanceof Number) {
            this.bindings.put(name, ((Number) obj).floatValue());
        } else if (obj instanceof Boolean) {
            this.bindings.put(name, (Boolean) obj ? 1.0f : 0.0f);
        } else {
            this.bindings.put(name, obj);
        }
    }

    public void var(String name, IValueEvaluator<?, IContext<Object>> evaluator) {
        this.bindings.put(name, new LambdaVariable<>(evaluator));
    }

    public void entityVar(String name, IValueEvaluator<?, IContext<Entity>> evaluator) {
        this.bindings.put(name, new EntityVariable(evaluator));
    }

    public void livingEntityVar(String name, IValueEvaluator<?, IContext<LivingEntity>> evaluator) {
        this.bindings.put(name, new LivingEntityVariable(evaluator));
    }

    public void mobEntityVar(String name, IValueEvaluator<?, IContext<MobEntity>> evaluator) {
        this.bindings.put(name, new MobEntityVariable(evaluator));
    }

    public void tamableEntityVar(String name, IValueEvaluator<?, IContext<TameableEntity>> evaluator) {
        this.bindings.put(name, new TamableEntityVariable(evaluator));
    }

    public void playerEntityVar(String name, IValueEvaluator<?, IContext<PlayerEntity>> evaluator) {
        this.bindings.put(name, new PlayerEntityVariable(evaluator));
    }

    public void clientPlayerEntityVar(String name, IValueEvaluator<?, IContext<AbstractClientPlayerEntity>> evaluator) {
        this.bindings.put(name, new ClientPlayerEntityVariable(evaluator));
    }

    public void localPlayerEntityVar(String name, IValueEvaluator<?, IContext<ClientPlayerEntity>> evaluator) {
        this.bindings.put(name, new LocalPlayerEntityVariable(evaluator));
    }

    public void projectileEntityVar(String name, IValueEvaluator<?, IContext<ProjectileEntity>> evaluator) {
        this.bindings.put(name, new ProjectileEntityVariable(evaluator));
    }

    public void throwableProjectileEntityVar(String name, IValueEvaluator<?, IContext<ProjectileItemEntity>> evaluator) {
        this.bindings.put(name, new ThrowableProjectileEntityVariable(evaluator));
    }

    public void fishHookEntityVar(String name, IValueEvaluator<?, IContext<FishingBobberEntity>> evaluator) {
        this.bindings.put(name, new FishingHookEntityVariable(evaluator));
    }

    public void abstractArrowEntityVar(String name, IValueEvaluator<?, IContext<AbstractArrowEntity>> evaluator) {
        this.bindings.put(name, new AbstractArrowEntityVariable(evaluator));
    }

    public void arrowEntityVar(String name, IValueEvaluator<?, IContext<ArrowEntity>> evaluator) {
        this.bindings.put(name, new ArrowEntityVariable(evaluator));
    }

    public void itemVar(String name, IValueEvaluator<?, IContext<Item>> evaluator) {
        this.bindings.put(name, new ItemVariable(evaluator));
    }

    public void itemStackVar(String name, IValueEvaluator<?, IContext<ItemStack>> evaluator) {
        this.bindings.put(name, new ItemStackVariable(evaluator));
    }

    public void blockStateVar(String name, IValueEvaluator<?, IContext<BlockState>> evaluator) {
        this.bindings.put(name, new BlockStateVariable(evaluator));
    }

    public void blockVar(String name, IValueEvaluator<?, IContext<Block>> evaluator) {
        this.bindings.put(name, new BlockVariable(evaluator));
    }

    public void blockBehaviourVar(String name, IValueEvaluator<?, IContext<Block>> evaluator) {
        this.bindings.put(name, new BlockBehaviorVariable(evaluator));
    }
}