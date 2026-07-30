#version 110

/*
 * SMAA pass 1/3: luma edge detection with depth predication.
 *
 * Ported from SMAA.hlsl (SMAALumaEdgeDetectionPS + SMAACalculatePredicatedThreshold),
 * Copyright (C) 2013 Jorge Jimenez et al., MIT licensed. Writes horizontal/vertical edge
 * flags to the red/green channels.
 *
 * Predication is what makes this behave like a shader pack's AA rather than plain FXAA:
 * the depth buffer tells us where real geometry edges are, so block outlines get a lowered
 * threshold while flat surfaces keep the high one and their texture detail is left alone.
 */

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 OutSize;

uniform float Threshold;
uniform float LocalContrastAdaptationFactor;
uniform float PredicationThreshold;
uniform float PredicationScale;
uniform float PredicationStrength;
uniform float NearPlane;
uniform float FarPlane;

varying vec2 texCoord;

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

/**
 * Raw depth is heavily non-linear: past a short distance almost everything reads as ~1.0, so
 * comparing raw values would miss every distant edge. This maps it back to view-space depth.
 * Near/far match GameRenderer.getProjectionMatrix (0.05 near, clipDistance far).
 */
float linearDepth(vec2 uv) {
    float d = texture2D(DepthSampler, uv).r * 2.0 - 1.0;
    return (2.0 * NearPlane * FarPlane) / (FarPlane + NearPlane - d * (FarPlane - NearPlane));
}

/**
 * Lowers the luma threshold where the depth buffer shows a real geometry discontinuity.
 * Mirrors SMAACalculatePredicatedThreshold (SMAA.hlsl:615-623), with the depth deltas made
 * relative so the test behaves the same near and far from the camera.
 */
vec2 predicatedThreshold(vec2 rcp) {
    float D     = linearDepth(texCoord);
    float Dleft = linearDepth(texCoord + vec2(-rcp.x, 0.0));
    float Dtop  = linearDepth(texCoord + vec2(0.0, -rcp.y));

    vec2 delta = abs(D - vec2(Dleft, Dtop)) / max(D, 1.0);
    vec2 depthEdges = step(vec2(PredicationThreshold), delta);

    return PredicationScale * Threshold * (1.0 - PredicationStrength * depthEdges);
}

void main() {
    vec2 rcp = 1.0 / OutSize;
    vec2 threshold = predicatedThreshold(rcp);

    float L      = luma(texture2D(DiffuseSampler, texCoord).rgb);
    float Lleft  = luma(texture2D(DiffuseSampler, texCoord + vec2(-rcp.x, 0.0)).rgb);
    float Ltop   = luma(texture2D(DiffuseSampler, texCoord + vec2(0.0, -rcp.y)).rgb);

    vec4 delta;
    delta.xy = abs(L - vec2(Lleft, Ltop));
    vec2 edges = step(threshold, delta.xy);

    // No edge here: leave the pixel blank rather than discard, since the target is reused.
    if (edges.x + edges.y == 0.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float Lright  = luma(texture2D(DiffuseSampler, texCoord + vec2(rcp.x, 0.0)).rgb);
    float Lbottom = luma(texture2D(DiffuseSampler, texCoord + vec2(0.0, rcp.y)).rgb);
    delta.zw = abs(L - vec2(Lright, Lbottom));

    vec2 maxDelta = max(delta.xy, delta.zw);

    float Lleftleft = luma(texture2D(DiffuseSampler, texCoord + vec2(-2.0 * rcp.x, 0.0)).rgb);
    float Ltoptop   = luma(texture2D(DiffuseSampler, texCoord + vec2(0.0, -2.0 * rcp.y)).rgb);
    delta.zw = abs(vec2(Lleft, Ltop) - vec2(Lleftleft, Ltoptop));

    maxDelta = max(maxDelta.xy, delta.zw);
    float finalDelta = max(maxDelta.x, maxDelta.y);

    // Local contrast adaptation: suppress edges that sit next to a much stronger one, which is
    // what keeps SMAA from smearing texture detail the way plain FXAA does.
    edges.xy *= step(vec2(finalDelta), LocalContrastAdaptationFactor * delta.xy);

    gl_FragColor = vec4(edges, 0.0, 1.0);
}
