package com.elfmcys.yesstevemodel.capability;

public final class Capability<T> {
    private final String id;

    public Capability(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
