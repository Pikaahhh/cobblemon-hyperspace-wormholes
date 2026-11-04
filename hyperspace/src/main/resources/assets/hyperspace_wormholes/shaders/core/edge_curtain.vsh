#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CornerOffset;
uniform float SkyAngle;

out vec3 skyDir;

void main() {
    vec3 rel = Position + CornerOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(rel, 1.0);
    float c = cos(SkyAngle), s = sin(SkyAngle);
    skyDir = vec3(c * rel.x - s * rel.z, rel.y, s * rel.x + c * rel.z);
}
