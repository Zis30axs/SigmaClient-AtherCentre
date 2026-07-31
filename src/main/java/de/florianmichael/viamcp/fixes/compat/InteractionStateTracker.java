package de.florianmichael.viamcp.fixes.compat;

import net.minecraft.item.ItemStack;

public final class InteractionStateTracker {
    private InteractionStateTracker() {
    }

    public static void rememberLastUsedItem(ItemStack stack) {
        LocalInteractionState.rememberUsedItem(stack);
    }

    public static ItemStack lastUsedItem() {
        return LocalInteractionState.lastLocallyUsedItem();
    }
}
