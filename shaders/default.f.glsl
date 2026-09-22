
uniform sampler2D textures[2];
uniform mediump vec2 texsize;
uniform mediump float width, height;

varying mediump vec2 texcoord;

void main()
{
	mediump vec2 modifiedCoord0 = vec2(texcoord.x, (floor(texcoord.y * texsize.y + 0.25) + 0.5)/texsize.y);
	mediump vec2 modifiedCoord1 = vec2(texcoord.x, (floor(texcoord.y * texsize.y - 0.25) + 0.5)/texsize.y);
	// Only part of the texture contains the current, cropped game frame.
	// Clamp each field sample to pixel centers inside that active area;
	// GL_CLAMP_TO_EDGE only clamps to the full backing texture instead.
	mediump vec2 firstPixel = vec2(0.5) / texsize;
	mediump vec2 lastPixel = (vec2(width, height) - vec2(0.5)) / texsize;
	modifiedCoord0 = clamp(modifiedCoord0, firstPixel, lastPixel);
	modifiedCoord1 = clamp(modifiedCoord1, firstPixel, lastPixel);
	gl_FragColor = mix(
		texture2D(textures[1], modifiedCoord1),
		texture2D(textures[0], modifiedCoord0),
		(sin(texcoord.y * texsize.y * 6.283185307) + 1.0) * 0.5
	);
}
