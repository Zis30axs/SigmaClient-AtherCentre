#version 110

/*
 * Straight copy with blending disabled.
 *
 * blit.fsh cannot be used to copy minecraft:main, because blit.json enables srcalpha blending
 * and the main framebuffer is cleared to alpha 0 - the copy would come out multiplied by an
 * alpha the world render never guarantees. This one writes the colour through untouched.
 */

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;

void main() {
    gl_FragColor = vec4(texture2D(DiffuseSampler, texCoord).rgb, 1.0);
}
