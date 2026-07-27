package com.elfmcys.yesstevemodel.util.log;

import net.minecraft.util.text.ITextComponent;

public interface ILogger {
    void logFormatted(String str, Object... objArr);

    void logComponent(ITextComponent component);
}