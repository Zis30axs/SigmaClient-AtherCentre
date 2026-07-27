package com.elfmcys.yesstevemodel.config;

import java.util.function.Supplier;

public class LoadingStateConfig {
    public static final Supplier<Boolean> DISABLE_LOADING_STATE_SCREEN = () -> false;
    public static final Supplier<Integer> LOADING_STATE_POSITION = () -> 0;
}