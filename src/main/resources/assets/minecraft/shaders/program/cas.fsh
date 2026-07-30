#version 110

/*
 * AMD FidelityFX Contrast Adaptive Sharpening (CAS), single-pass form.
 *
 * Sharpens less where local contrast is already high, so edges recover definition without the
 * halos a plain unsharp mask produces. Intended to run after a blurring antialiasing pass (TAA or
 * FXAA) to win back the softness those introduce.
 */

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;

uniform float Sharpness;

varying vec2 texCoord;

void main() {
    vec2 rcp = 1.0 / OutSize;

    // 3x3 neighbourhood: b = north, d = west, e = centre, f = east, h = south, plus the corners.
    vec3 a = texture2D(DiffuseSampler, texCoord + vec2(-rcp.x, -rcp.y)).rgb;
    vec3 b = texture2D(DiffuseSampler, texCoord + vec2(   0.0, -rcp.y)).rgb;
    vec3 c = texture2D(DiffuseSampler, texCoord + vec2( rcp.x, -rcp.y)).rgb;
    vec3 d = texture2D(DiffuseSampler, texCoord + vec2(-rcp.x,    0.0)).rgb;
    vec3 e = texture2D(DiffuseSampler, texCoord).rgb;
    vec3 f = texture2D(DiffuseSampler, texCoord + vec2( rcp.x,    0.0)).rgb;
    vec3 g = texture2D(DiffuseSampler, texCoord + vec2(-rcp.x,  rcp.y)).rgb;
    vec3 h = texture2D(DiffuseSampler, texCoord + vec2(   0.0,  rcp.y)).rgb;
    vec3 i = texture2D(DiffuseSampler, texCoord + vec2( rcp.x,  rcp.y)).rgb;

    // Min/max over the cross, then extended with the corners. CAS weights the sharpening by how
    // much headroom the pixel has left before clipping, which is what makes it contrast adaptive.
    vec3 mnRGB = min(min(min(d, e), min(f, b)), h);
    vec3 mnRGB2 = min(mnRGB, min(min(a, c), min(g, i)));
    mnRGB += mnRGB2;

    vec3 mxRGB = max(max(max(d, e), max(f, b)), h);
    vec3 mxRGB2 = max(mxRGB, max(max(a, c), max(g, i)));
    mxRGB += mxRGB2;

    vec3 rcpMRGB = 1.0 / max(mxRGB, vec3(1.0 / 8192.0));
    vec3 ampRGB = clamp(min(mnRGB, 2.0 - mxRGB) * rcpMRGB, 0.0, 1.0);
    ampRGB = sqrt(ampRGB);

    // Sharpness 0 -> -1/8 (gentle), 1 -> -1/5 (strong); the reference range from the CAS paper.
    float peak = -1.0 / mix(8.0, 5.0, clamp(Sharpness, 0.0, 1.0));
    vec3 wRGB = ampRGB * peak;
    vec3 rcpWeightRGB = 1.0 / (1.0 + 4.0 * wRGB);

    vec3 outColor = clamp(
        (b * wRGB + d * wRGB + f * wRGB + h * wRGB + e) * rcpWeightRGB,
        0.0, 1.0);

    gl_FragColor = vec4(outColor, 1.0);
}
