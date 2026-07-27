package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.client.compat.immersivemelodies.ImmersiveMelodiesCompat;
import com.elfmcys.yesstevemodel.geckolib3.core.EntityFrameStateTracker;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

public class LivingEntityFrameState<T extends LivingEntity> extends EntityFrameStateTracker<T> {

    private final ImmersiveMelodiesCompat.ImmersiveMelodiesData imData;

    private ItemStack mainHandItem;

    private ItemStack offHandItem;

    public LivingEntityFrameState(T t) {
        super(t);
        this.imData = new ImmersiveMelodiesCompat.ImmersiveMelodiesData();
        this.mainHandItem = ItemStack.EMPTY;
        this.offHandItem = ItemStack.EMPTY;
    }

    @Override
    public void reset() {
        this.mainHandItem = ItemStack.EMPTY;
        this.offHandItem = ItemStack.EMPTY;
        super.reset();
    }

    @Override
    public void onTimeUpdate(float f, float f2, float f3) {
        super.onTimeUpdate(f, f2, f3);
        // 更新沉浸式奏乐数据
        ImmersiveMelodiesCompat.updateMelodyProgress(this.entity, this.imData);
    }

    public ItemStack getHandItemsForAnimation(Hand Hand) {
        if (Hand == Hand.MAIN_HAND) {
            return this.mainHandItem;
        }
        return this.offHandItem;
    }

    public void setHandItemsForAnimation(ItemStack itemStack, Hand Hand) {
        if (Hand == Hand.MAIN_HAND) {
            this.mainHandItem = itemStack;
        } else {
            this.offHandItem = itemStack;
        }
    }

    public ImmersiveMelodiesCompat.ImmersiveMelodiesData getImmersiveMelodiesData() {
        return this.imData;
    }
}