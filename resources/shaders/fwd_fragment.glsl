#version 450

layout(location = 0) in vec2 textCoords;
layout(location = 0) out vec4 uFragColor;

// set is the location inside the buffer where we call vkCmdBindDescriptorSets
layout(set = 2, binding = 0) uniform sampler2D textSampler;
layout(set = 3, binding = 0) uniform MaterialUniform {
    vec4 diffuseColor;
} material;

void main()
{
    uFragColor = material.diffuseColor + texture(textSampler, textCoords);
}

