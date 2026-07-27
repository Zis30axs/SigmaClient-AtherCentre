package com.elfmcys.yesstevemodel.config;

import java.util.function.Supplier;

/**
 * Inert client-side stand-in for upstream {@code config/ServerConfig}. The server half of YSM is cut
 * from this port, so these values are only ever read behind a
 * {@code NetworkHandler.isClientConnected()} guard that is permanently {@code false}. They exist so
 * the ported call sites keep upstream's shape rather than being rewritten around the cut.
 *
 * <p>Defaults match upstream's: switching models is allowed, low-bandwidth mode is off.
 */
public class ServerConfig {

    public static final Supplier<Boolean> CAN_SWITCH_MODEL = () -> Boolean.TRUE;

    public static final Supplier<Boolean> LOW_BANDWIDTH_USAGE = () -> Boolean.FALSE;
}
