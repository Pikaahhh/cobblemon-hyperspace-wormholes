#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D MaskSampler;
uniform sampler2D SkySampler;
uniform mat4 InverseView;
uniform vec3 MaskOrigin;
uniform vec4 MaskInfo;
uniform float SkyAngle;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float distanceAt(ivec2 cell) {
    ivec2 limit = ivec2(int(MaskInfo.z) - 1);
    vec4 t = texelFetch(MaskSampler, clamp(cell, ivec2(0), limit), 0);
    return (t.r * 255.0 * 256.0 + t.g * 255.0) / 65535.0 * 16.0 - 8.0;
}

vec3 sky(vec3 dir) {
    float c = cos(SkyAngle), s = sin(SkyAngle);
    float lx = c * dir.x - s * dir.z;
    float lz = s * dir.x + c * dir.z;
    float azimuth = atan(lz, lx);
    if (azimuth < 0.0) azimuth += 6.2831853;
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    return textureLod(SkySampler, vec2(azimuth / 6.2831853, (1.5707963 - elevation) / 3.1415927), 0.0).rgb;
}

void main() {
    vec4 colour = texture(DiffuseSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    if (depth >= 1.0) {
        fragColor = vec4(colour.rgb, 1.0);
        return;
    }
    vec3 grey = vec3(dot(colour.rgb, vec3(0.3, 0.59, 0.11)));
    if (MaskInfo.x < 0.5) {
        fragColor = vec4(grey, 1.0);
        return;
    }
    vec4 world = InverseView * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 rel = world.xyz / world.w;
    vec2 cell = (rel.xz - MaskOrigin.xz) * MaskInfo.y;
    if (cell.x < 0.0 || cell.y < 0.0 || cell.x >= MaskInfo.z || cell.y >= MaskInfo.z) {
        fragColor = vec4(grey, 1.0);
        return;
    }
    vec2 p = cell - 0.5;
    ivec2 i = ivec2(floor(p));
    vec2 f = p - floor(p);
    float d = mix(mix(distanceAt(i), distanceAt(i + ivec2(1, 0)), f.x),
                  mix(distanceAt(i + ivec2(0, 1)), distanceAt(i + ivec2(1, 1)), f.x), f.y);
    vec2 off = rel.xz - MaskOrigin.xz - vec2(MaskInfo.z / MaskInfo.y * 0.5);
    float theta = atan(off.y, off.x);
    float phase = 7.0 * theta / 6.2831853 + 0.25 * sin(12.0 * theta) + 0.15 * sin(30.0 * theta + 1.3);
    float shift = 0.5 * (0.5 - 0.5 * cos(6.2831853 * (MaskInfo.w + phase)));
    float t = d + shift;
    float a = t >= 0.0 ? 1.0 : 0.95 * clamp(1.0 + t / 0.25, 0.0, 1.0);
    fragColor = vec4(mix(grey, sky(normalize(rel)), a), 1.0);
}
