#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec2 entityTextCoords;

layout(location = 0) out vec2 textCoords;

// set is the location inside the buffer where we call vkCmdBindDescriptorSets
layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 projectionMatrix;
} projUniform;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

void main()
{
    gl_Position = projUniform.projectionMatrix * push_constants.modelMatrix * vec4(entityPos, 1);
    textCoords = entityTextCoords;
}

