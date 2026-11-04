#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec3 skyDir;

out vec4 fragColor;

void main() {
    vec3 dir = normalize(skyDir);
    float azimuth = atan(dir.z, dir.x);
    if (azimuth < 0.0) azimuth += 6.2831853;
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    vec4 color = textureLod(Sampler0, vec2(azimuth / 6.2831853, (1.5707963 - elevation) / 3.1415927), 0.0);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
