package com.elfmcys.yesstevemodel.resource.models;

public class GeometryDescription {
    private final String identifier;
    private final double textureWidth;
    private final double textureHeight;
    private final double visibleBoundsWidth;
    private final double visibleBoundsHeight;
    private final double[] visibleBoundsOffset;

    public GeometryDescription(String identifier, double textureWidth, double textureHeight, double visibleBoundsWidth, double visibleBoundsHeight, double[] visibleBoundsOffset) {
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.visibleBoundsWidth = visibleBoundsWidth;
        this.visibleBoundsHeight = visibleBoundsHeight;
        this.visibleBoundsOffset = visibleBoundsOffset;
    }
    public String getIdentifier() { return identifier; }
    public double getTextureWidth() { return textureWidth; }
    public double getTextureHeight() { return textureHeight; }
    public double getVisibleBoundsWidth() { return visibleBoundsWidth; }
    public double getVisibleBoundsHeight() { return visibleBoundsHeight; }
    public double[] getVisibleBoundsOffset() { return visibleBoundsOffset; }
}