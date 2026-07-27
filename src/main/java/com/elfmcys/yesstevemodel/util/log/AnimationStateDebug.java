package com.elfmcys.yesstevemodel.util.log;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.IAnimationController;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Opt-in diagnostic for "which animation is actually playing" bugs. Enable with the JVM flag
 * {@code -Dysm.debug.anim=true}; it then logs the local player's controller -> animation mapping
 * once per change (not per frame), so a stuck or unexpected animation is immediately visible.
 *
 * <p>Off by default and cheap when off: a single boolean test per render.
 */
public final class AnimationStateDebug {

    private static final boolean ENABLED = Boolean.getBoolean("ysm.debug.anim");

    private static String lastLine = "";

    private AnimationStateDebug() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void dumpLocalPlayer(AnimatableEntity<?> animatable) {
        if (!ENABLED || animatable == null) {
            return;
        }
        Entity entity = animatable.getEntity();
        if (entity == null || entity != Minecraft.getInstance().player) {
            return;
        }
        List<IAnimationController> controllers = animatable.getAnimationData().getAnimationControllers();
        if (controllers.isEmpty()) {
            log("(no controllers)");
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<String> active = new ArrayList<>();
        for (IAnimationController controller : controllers) {
            String current;
            try {
                current = controller.getCurrentAnimation();
            } catch (Throwable th) {
                current = "<error: " + th.getClass().getSimpleName() + ">";
            }
            if (current != null && !current.isEmpty()) {
                active.add(controller.getName() + "=" + current);
            }
        }
        if (active.isEmpty()) {
            log("(all controllers idle, " + controllers.size() + " registered)");
            return;
        }
        sb.append(String.join(", ", active));
        log(sb.toString());
    }

    private static void log(String line) {
        if (line.equals(lastLine)) {
            return;
        }
        lastLine = line;
        YesSteveModel.LOGGER.info("[YSM/anim] {}", line);
    }
}
