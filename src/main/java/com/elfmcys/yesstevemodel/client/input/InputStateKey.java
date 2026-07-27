package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.util.InputUtil;

public class InputStateKey {

    public static volatile boolean[] keyStates = new boolean[349];

    public static volatile boolean[] mouseStates = new boolean[8];

    // Called from KeyboardListener.onKeyEvent (vanilla hook).
    public static void onKeyInput(int key, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 32 <= key && key <= 348) {
            if (action == 1) {
                keyStates[key] = true;
            } else if (action == 0) {
                keyStates[key] = false;
            }
        }
    }

    // Called from MouseHelper.mouseButtonCallback (vanilla hook).
    public static void onMouseInput(int button, int action) {
        if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && 0 <= button && button <= 7) {
            if (action == 1) {
                mouseStates[button] = true;
            } else if (action == 0) {
                mouseStates[button] = false;
            }
        }
    }
}
