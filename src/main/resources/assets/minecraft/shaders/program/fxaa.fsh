#version 110
#extension GL_EXT_gpu_shader4 : enable

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;

uniform float SpanMax;
uniform float ReduceMul;
uniform float EdgeThreshold;
uniform float EdgeThresholdMin;

varying vec2 texCoord;
varying vec4 posPos;

#define FxaaTex(t, p) texture2D(t, p)

#if __VERSION__ >= 130
    #define OffsetVec(a, b) ivec2(a, b)
    #define FxaaTexOff(t, p, o, r) textureOffset(t, p, o)
#elif defined(GL_EXT_gpu_shader4)
    #define OffsetVec(a, b) ivec2(a, b)
    #define FxaaTexOff(t, p, o, r) texture2DLodOffset(t, p, 0.0, o)
#else
    #define OffsetVec(a, b) vec2(a, b)
    #define FxaaTexOff(t, p, o, r) texture2D(t, p + vec2(o) * r)
#endif

vec3 FxaaPixelShader(vec2 pos, sampler2D tex, vec2 rcpFrame)
{
    #define FXAA_REDUCE_MIN (1.0/128.0)

    // Sample the 4 diagonal neighbours around the centre pixel. Unlike the offset-corner
    // variant this keeps the kernel symmetric about pos, so single-pixel highlights on an
    // edge are not pulled off-centre into stray dots.
    vec3 rgbNW = FxaaTexOff(tex, pos, OffsetVec(-1, -1), rcpFrame).xyz;
    vec3 rgbNE = FxaaTexOff(tex, pos, OffsetVec( 1, -1), rcpFrame).xyz;
    vec3 rgbSW = FxaaTexOff(tex, pos, OffsetVec(-1,  1), rcpFrame).xyz;
    vec3 rgbSE = FxaaTexOff(tex, pos, OffsetVec( 1,  1), rcpFrame).xyz;
    vec3 rgbM  = FxaaTex(tex, pos).xyz;

    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));
    float lumaRange = lumaMax - lumaMin;

    // Local-contrast early exit from FXAA 3.11. Without it every pixel gets blended, which
    // smears flat interiors and block textures instead of only touching real edges.
    if (lumaRange < max(EdgeThresholdMin, lumaMax * EdgeThreshold)) {
        return rgbM;
    }

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * ReduceMul), FXAA_REDUCE_MIN);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = clamp(dir * rcpDirMin, vec2(-SpanMax), vec2(SpanMax)) * rcpFrame;

    vec3 rgbA = 0.5 * (
        FxaaTex(tex, pos + dir * (1.0/3.0 - 0.5)).xyz +
        FxaaTex(tex, pos + dir * (2.0/3.0 - 0.5)).xyz);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (
        FxaaTex(tex, pos + dir * -0.5).xyz +
        FxaaTex(tex, pos + dir *  0.5).xyz);

    float lumaB = dot(rgbB, luma);

    // The wider 4-tap average is only trusted when it stays inside the local luma range;
    // otherwise it has reached past the edge and the 2-tap average is used instead.
    return ((lumaB < lumaMin) || (lumaB > lumaMax)) ? rgbA : rgbB;
}

void main() {
    gl_FragColor = vec4(FxaaPixelShader(texCoord, DiffuseSampler, 1.0 / OutSize), 1.0);
}
