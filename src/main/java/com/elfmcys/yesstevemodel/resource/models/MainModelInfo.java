package com.elfmcys.yesstevemodel.resource.models;

/** Geometry statistics of the main model, shown in the model-info screen. */
public record MainModelInfo(int bones, int cubes, int faces) {
    public int getBones() {
        return this.bones;
    }

    public int getCubes() {
        return this.cubes;
    }

    public int getFaces() {
        return this.faces;
    }
}
