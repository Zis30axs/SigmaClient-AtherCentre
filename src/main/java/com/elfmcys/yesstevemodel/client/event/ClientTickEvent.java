package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.audio.ObjectPool;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import net.minecraft.client.Minecraft;

/**
 * Port of upstream {@code client/event/ClientTickEvent} (a Forge
 * {@code @SubscribeEvent TickEvent.ClientTickEvent} at {@code Phase.START}). There is no event bus
 * here, so {@link #onClientTick()} is called directly from
 * {@code net.minecraft.client.Minecraft#runTick}.
 *
 * <p>Previously this class was a bare field holder whose setters had no call sites, so
 * {@link #getTickCount()} was permanently 0 (preview animatables never advanced) and
 * {@link #getRefreshRate()} was permanently the hardcoded 20 rather than the monitor rate that
 * gates animation evaluation in {@code AnimatableEntity#setCustomAnimations}.
 *
 * <p>Cut relative to upstream, with reasons:
 * <ul>
 *   <li>{@code PlayerCapability.tickAnimations()} — drains the roaming-variable queue and sends
 *       {@code C2SCompleteFeedbackPacket}; server sync is cut from this client-only port.</li>
 * </ul>
 */
public class ClientTickEvent {

    private static int tickCount;

    /** Upstream default; replaced each tick by the real monitor refresh rate. */
    private static int refreshRate = 60;

    public static void onClientTick() {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        tickCount++;
        UploadManager.processPendingUploads();
        ClientModelManager.flushPendingModels();
        // W5: upstream's own call — destroys pooled audio decoders idle for >200 ticks.
        ObjectPool.cleanup();
        // Upstream's own TickEvent.ClientTickEvent at Phase.END: clears a roulette-selected animation
        // once the player starts moving. Without this call site a picked animation never releases.
        AnimationLockEvent.onClientTick();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getMainWindow() != null) {
            int rate = minecraft.getMainWindow().getRefreshRate();
            if (rate > 0) {
                refreshRate = rate;
            }
        }
    }

    public static int getTickCount() {
        return tickCount;
    }

    public static int getRefreshRate() {
        return refreshRate;
    }
}
