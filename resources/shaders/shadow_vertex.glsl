#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal; // because vertex buffer structure
layout(location = 2) in vec3 entityTangent; // because vertex buffer structure
layout(location = 3) in vec3 entityBitangent; // because vertex buffer structure
layout(location = 4) in vec2 entityTextCoords; // because vertex buffer structure

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

layout (location = 0) out vec2 outTextCoord;

void main()
{
    gl_Position = push_constants.modelMatrix * vec4(entityPos, 1.0f);
    outTextCoord = entityTextCoords;
}