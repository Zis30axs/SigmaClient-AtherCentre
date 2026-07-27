package com.elfmcys.yesstevemodel.client.compat.touhoulittlemaid;

import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.model.PlayerModelBundle;
import net.minecraft.entity.Entity;

public class TouhouLittleMaidCompat {
    public static boolean isMaid(Object entity) { return false; }
    public static Object buildControllers(PlayerModelBundle bundle, ModelResourceBundle resourceBundle) { return null; }
    public static boolean isSimplePlanesEntity(Entity entity) { return false; }
    public static boolean isImmersiveAircraftEntity(Entity entity) { return false; }
    public static boolean isMaidSitting(Entity entity) { return false; }
    public static void registerMaidAnimStates(Object binding) { }
    /** Roulette key hijack: upstream opens the maid chat bubble instead of the wheel. Never here. */
    public static boolean isMaidChatAvailable() { return false; }
    public static void openMaidChat() { }
}
