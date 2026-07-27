package com.elfmcys.yesstevemodel.util.log;

import net.minecraft.util.text.ITextComponent;

public class ChatLogger implements ILogger {
    public static final ChatLogger INSTANCE = new ChatLogger();

    @Override
    public void logFormatted(String str, Object... objArr) {}

    @Override
    public void logComponent(ITextComponent component) {}
}