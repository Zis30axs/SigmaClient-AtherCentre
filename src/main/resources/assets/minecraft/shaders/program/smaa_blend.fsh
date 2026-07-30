#version 110

/*
 * SMAA pass 3/3: neighborhood blending.
 *
 * Ported from SMAA.hlsl (SMAANeighborhoodBlendingPS), Copyright (C) 2013 Jorge Jimenez et al.,
 * MIT licensed. Mixes each pixel with one neighbour using the weights from pass 2.
 */

uniform sampler2D DiffuseSampler;   // blend weights (from pass 2)
uniform sampler2D ColorSampler;     // original scene colour
uniform vec2 OutSize;

varying vec2 texCoord;

void main() {
    vec2 rcp = 1.0 / OutSize;

    vec4 a;
    a.x = texture2D(DiffuseSampler, texCoord + vec2(rcp.x, 0.0)).a;   // right
    a.y = texture2D(DiffuseSampler, texCoord + vec2(0.0, rcp.y)).g;   // top
    // SMAA.hlsl:1264 is `a.wz = SMAASample(blendTex, texcoord).xz`, i.e. bottom reads r and
    // left reads b. Swapping these makes the horizontal and vertical weights cross over.
    vec4 centre = texture2D(DiffuseSampler, texCoord);
    a.w = centre.r;                                                    // bottom
    a.z = centre.b;                                                    // left

    if (a.x + a.y + a.z + a.w < 1e-5) {
        gl_FragColor = texture2D(ColorSampler, texCoord);
        return;
    }

    // Pick the dominant axis, then blend along it.
    bool h = max(a.x, a.z) > max(a.y, a.w);

    vec4 blendingOffset = vec4(0.0, a.y, 0.0, a.w);
    vec2 blendingWeight = a.yw;

    if (h) {
        blendingOffset = vec4(a.x, 0.0, a.z, 0.0);
        blendingWeight = a.xz;
    }

    blendingWeight /= blendingWeight.x + blendingWeight.y;

    vec4 blendingCoord = vec4(
        texCoord + blendingOffset.xy * vec2(rcp.x, rcp.y),
        texCoord + blendingOffset.zw * vec2(-rcp.x, -rcp.y));

    // Bilinear filtering does the actual mixing between the pixel and its neighbour.
    vec4 color = blendingWeight.x * texture2D(ColorSampler, blendingCoord.xy);
    color += blendingWeight.y * texture2D(ColorSampler, blendingCoord.zw);

    gl_FragColor = color;
}
