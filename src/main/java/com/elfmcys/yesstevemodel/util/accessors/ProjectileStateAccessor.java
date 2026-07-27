package com.elfmcys.yesstevemodel.util.accessors;

/**
 * Port note: upstream exposes these through {@code AbstractArrowEntityMixin}. This client has no
 * mixin runtime, so {@link net.minecraft.entity.projectile.AbstractArrowEntity} implements the
 * interface directly in the decompiled source.
 */
public interface ProjectileStateAccessor {
    boolean isInGround();

    int getInGroundTime();

    String getOwnerItemId();
}
