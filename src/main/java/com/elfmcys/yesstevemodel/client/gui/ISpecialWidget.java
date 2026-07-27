package com.elfmcys.yesstevemodel.client.gui;

/**
 * Marker for widgets that live inside the roulette's scrolling config column.
 *
 * <p>{@code AnimationRouletteScreen} renders and hit-tests these separately from ordinary widgets:
 * they are drawn inside a scissor rect under a {@code translate(0, -configScrollOffset, 0)}, and
 * mouse coordinates handed to them get {@code configScrollOffset} added back.
 */
public interface ISpecialWidget {
}
