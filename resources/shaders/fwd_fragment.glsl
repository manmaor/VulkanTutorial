#version 450

layout(location = 0) in vec2 textCoords;
layout(location = 0) out vec4 uFragColor;

// set is the location inside the buffer where we call vkCmdBindDescriptorSets
layout(set = 1, binding = 0) uniform sampler2D textSampler;

void main()
{
    uFragColor = texture(textSampler, textCoords);
}

