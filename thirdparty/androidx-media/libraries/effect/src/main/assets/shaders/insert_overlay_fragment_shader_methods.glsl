// Manually implementing the CLAMP_TO_BORDER texture wrapping option
// (https://open.gl/textures) since it's not implemented until OpenGL ES 3.2.
vec4 getClampToBorderOverlayColor(sampler2D texSampler, vec2 texSamplingCoord,
                                  float alphaScale) {
  if (texSamplingCoord.x > 1.0 || texSamplingCoord.x < 0.0 ||
      texSamplingCoord.y > 1.0 || texSamplingCoord.y < 0.0) {
    return vec4(0.0, 0.0, 0.0, 0.0);
  } else {
    vec4 overlayColor = vec4(texture2D(texSampler, texSamplingCoord));
    overlayColor.a = alphaScale * overlayColor.a;
    return overlayColor;
  }
}

vec4 getMixColor(vec4 videoColor, vec4 overlayColor) {
  vec4 outputColor;
  outputColor.rgb = overlayColor.rgb * overlayColor.a +
                    videoColor.rgb * (1.0 - overlayColor.a);
  outputColor.a = overlayColor.a + videoColor.a * (1.0 - overlayColor.a);
  return outputColor;
}
