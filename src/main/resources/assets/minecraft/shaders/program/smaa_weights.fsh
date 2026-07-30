#version 110

/*
 * SMAA pass 2/3: blending weight calculation.
 *
 * Ported from SMAA.hlsl (SMAABlendingWeightCalculationPS and its helpers), Copyright (C) 2013
 * Jorge Jimenez et al., MIT licensed. Diagonal detection is omitted: it needs texture fetches
 * with dynamic integer offsets that GLSL 110 cannot express, and the orthogonal path alone
 * already covers the block-edge aliasing this client suffers from.
 *
 * Reads the edge flags produced by smaa_edges and looks up coverage areas in AreaTex/SearchTex.
 */

uniform sampler2D DiffuseSampler;   // edges texture (from pass 1)
uniform sampler2D AreaSampler;      // AreaTex   160x560 RG
uniform sampler2D SearchSampler;    // SearchTex 64x16   R
uniform vec2 OutSize;

uniform float MaxSearchSteps;
uniform float CornerRounding;

varying vec2 texCoord;

#define AREATEX_MAX_DISTANCE 16.0
#define AREATEX_PIXEL_SIZE (1.0 / vec2(160.0, 560.0))
#define AREATEX_SUBTEX_SIZE (1.0 / 7.0)
#define SEARCHTEX_SIZE vec2(66.0, 33.0)
#define SEARCHTEX_PACKED_SIZE vec2(64.0, 16.0)

/**
 * How much length to add in the last search step, from the bilinearly interpolated edge pair.
 * The search texture is flipped vertically, with left/right cases split horizontally.
 */
float searchLength(vec2 e, float offset) {
    vec2 scale = SEARCHTEX_SIZE * vec2(0.5, -1.0);
    vec2 bias = SEARCHTEX_SIZE * vec2(offset, 1.0);

    scale += vec2(-1.0, 1.0);
    bias += vec2(0.5, -0.5);

    scale *= 1.0 / SEARCHTEX_PACKED_SIZE;
    bias *= 1.0 / SEARCHTEX_PACKED_SIZE;

    // Our PNG is stored flipped relative to the reference DDS, so undo that on lookup.
    vec2 uv = scale * e + bias;
    return texture2D(SearchSampler, vec2(uv.x, 1.0 - uv.y)).r;
}

float searchXLeft(vec2 tc, float end, vec2 rcp) {
    vec2 e = vec2(0.0, 1.0);

    for (int i = 0; i < 32; ++i) {
        if (!(tc.x > end && e.g > 0.8281 && e.r == 0.0)) {
            break;
        }

        e = texture2D(DiffuseSampler, tc).rg;
        tc += vec2(-2.0 * rcp.x, 0.0);
    }

    float offset = -(255.0 / 127.0) * searchLength(e, 0.0) + 3.25;
    return rcp.x * offset + tc.x;
}

float searchXRight(vec2 tc, float end, vec2 rcp) {
    vec2 e = vec2(0.0, 1.0);

    for (int i = 0; i < 32; ++i) {
        if (!(tc.x < end && e.g > 0.8281 && e.r == 0.0)) {
            break;
        }

        e = texture2D(DiffuseSampler, tc).rg;
        tc += vec2(2.0 * rcp.x, 0.0);
    }

    float offset = -(255.0 / 127.0) * searchLength(e, 0.5) + 3.25;
    return -rcp.x * offset + tc.x;
}

float searchYUp(vec2 tc, float end, vec2 rcp) {
    vec2 e = vec2(1.0, 0.0);

    for (int i = 0; i < 32; ++i) {
        if (!(tc.y > end && e.r > 0.8281 && e.g == 0.0)) {
            break;
        }

        e = texture2D(DiffuseSampler, tc).rg;
        tc += vec2(0.0, -2.0 * rcp.y);
    }

    float offset = -(255.0 / 127.0) * searchLength(e.gr, 0.0) + 3.25;
    return rcp.y * offset + tc.y;
}

float searchYDown(vec2 tc, float end, vec2 rcp) {
    vec2 e = vec2(1.0, 0.0);

    for (int i = 0; i < 32; ++i) {
        if (!(tc.y < end && e.r > 0.8281 && e.g == 0.0)) {
            break;
        }

        e = texture2D(DiffuseSampler, tc).rg;
        tc += vec2(0.0, 2.0 * rcp.y);
    }

    float offset = -(255.0 / 127.0) * searchLength(e.gr, 0.5) + 3.25;
    return -rcp.y * offset + tc.y;
}

/** Coverage area for a given distance pair and the two crossing edges. */
vec2 area(vec2 dist, float e1, float e2) {
    // Rounding prevents precision errors from bilinear filtering.
    vec2 uv = AREATEX_MAX_DISTANCE * floor(4.0 * vec2(e1, e2) + 0.5) + dist;
    uv = AREATEX_PIXEL_SIZE * uv + 0.5 * AREATEX_PIXEL_SIZE;

    // SMAA 1x uses subsample index 0, so no vertical offset is applied.
    return texture2D(AreaSampler, vec2(uv.x, 1.0 - uv.y)).rg;
}

void main() {
    vec2 rcp = 1.0 / OutSize;
    vec2 pixcoord = texCoord * OutSize;
    vec2 e = texture2D(DiffuseSampler, texCoord).rg;
    vec4 weights = vec4(0.0);

    if (e.g > 0.0) {   // Edge at north -> horizontal pattern
        vec2 d;
        vec3 coords;

        vec2 offLeft = texCoord + vec2(-0.25 * rcp.x, -0.125 * rcp.y);
        vec2 offRight = texCoord + vec2(1.25 * rcp.x, -0.125 * rcp.y);
        // The search limits are relative to the offset start points, not to texCoord
        // (SMAA.hlsl:664-666 adds offset[2] on top of offset[0].xz).
        float endLeft = texCoord.x - 0.25 * rcp.x - 2.0 * rcp.x * MaxSearchSteps;
        float endRight = texCoord.x + 1.25 * rcp.x + 2.0 * rcp.x * MaxSearchSteps;

        coords.x = searchXLeft(offLeft, endLeft, rcp);
        coords.y = texCoord.y - 0.25 * rcp.y;
        d.x = coords.x;

        float e1 = texture2D(DiffuseSampler, vec2(coords.x, coords.y)).r;

        coords.z = searchXRight(offRight, endRight, rcp);
        d.y = coords.z;

        d = abs(floor(OutSize.x * d - pixcoord.x + 0.5));
        vec2 sqrtD = sqrt(d);

        float e2 = texture2D(DiffuseSampler, vec2(coords.z + rcp.x, coords.y)).r;

        weights.rg = area(sqrtD, e1, e2);

        // Corner rounding: reduce blending where the edge turns, so corners stay defined.
        vec2 leftRight = step(d.xy, d.yx);
        vec2 rounding = (1.0 - CornerRounding) * leftRight;
        rounding /= leftRight.x + leftRight.y;
        vec2 factor = vec2(1.0);
        factor.x -= rounding.x * texture2D(DiffuseSampler, vec2(coords.x, texCoord.y + rcp.y)).r;
        factor.x -= rounding.y * texture2D(DiffuseSampler, vec2(coords.z + rcp.x, texCoord.y + rcp.y)).r;
        factor.y -= rounding.x * texture2D(DiffuseSampler, vec2(coords.x, texCoord.y - 2.0 * rcp.y)).r;
        factor.y -= rounding.y * texture2D(DiffuseSampler, vec2(coords.z + rcp.x, texCoord.y - 2.0 * rcp.y)).r;
        weights.rg *= clamp(factor, 0.0, 1.0);
    }

    if (e.r > 0.0) {   // Edge at west -> vertical pattern
        vec2 d;
        vec3 coords;

        vec2 offTop = texCoord + vec2(-0.125 * rcp.x, -0.25 * rcp.y);
        vec2 offBottom = texCoord + vec2(-0.125 * rcp.x, 1.25 * rcp.y);
        float endTop = texCoord.y - 0.25 * rcp.y - 2.0 * rcp.y * MaxSearchSteps;
        float endBottom = texCoord.y + 1.25 * rcp.y + 2.0 * rcp.y * MaxSearchSteps;

        coords.y = searchYUp(offTop, endTop, rcp);
        coords.x = texCoord.x - 0.25 * rcp.x;
        d.x = coords.y;

        float e1 = texture2D(DiffuseSampler, vec2(coords.x, coords.y)).g;

        coords.z = searchYDown(offBottom, endBottom, rcp);
        d.y = coords.z;

        d = abs(floor(OutSize.y * d - pixcoord.y + 0.5));
        vec2 sqrtD = sqrt(d);

        float e2 = texture2D(DiffuseSampler, vec2(coords.x, coords.z + rcp.y)).g;

        weights.ba = area(sqrtD, e1, e2);

        vec2 leftRight = step(d.xy, d.yx);
        vec2 rounding = (1.0 - CornerRounding) * leftRight;
        rounding /= leftRight.x + leftRight.y;
        vec2 factor = vec2(1.0);
        factor.x -= rounding.x * texture2D(DiffuseSampler, vec2(texCoord.x + rcp.x, coords.y)).g;
        factor.x -= rounding.y * texture2D(DiffuseSampler, vec2(texCoord.x + rcp.x, coords.z + rcp.y)).g;
        factor.y -= rounding.x * texture2D(DiffuseSampler, vec2(texCoord.x - 2.0 * rcp.x, coords.y)).g;
        factor.y -= rounding.y * texture2D(DiffuseSampler, vec2(texCoord.x - 2.0 * rcp.x, coords.z + rcp.y)).g;
        weights.ba *= clamp(factor, 0.0, 1.0);
    }

    gl_FragColor = weights;
}
