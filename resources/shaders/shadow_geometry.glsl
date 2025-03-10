#version 450

// You should change this manually if GraphConstants.SHADOW_MAP_CASCADE_COUNT changes
#define SHADOW_MAP_CASCADE_COUNT 3

// the invocations tells the gpu how many times to instance the geometry for each input primitive (in this case triange)
layout(triangles, invocations = SHADOW_MAP_CASCADE_COUNT) in;
layout(triangle_strip, max_vertices = 3) out; // tells we are generating a triangle strip with max of 3 vertices

layout(location = 0) in vec2 inTextCoords[]; // will contain 3 vertices because we are dealing with a single triangle
layout(location = 0) out vec2 outTextCoords;

layout (set = 0, binding = 0) uniform ProjUniforms {
    mat4 projViewMatrices[SHADOW_MAP_CASCADE_COUNT];
} projUniforms;

void main()
{
    // for each vertex
    for (int i = 0; i < 4; i++) // WTH is 4??
    {
        // get the texture coordinates of the vertex
        outTextCoords = inTextCoords[i];
        // set the layer to the gl_InvocationID controlles by the  layout (..., invocations = ) above
        gl_Layer = gl_InvocationID;
        // get the specific cascade view matrix (same by the invocation id) and multiple that vertex with it
        gl_Position = projUniforms.projViewMatrices[gl_InvocationID] * gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}
