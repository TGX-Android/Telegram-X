// The value is calculated as targetHdrPeakBrightnessInNits /
// targetSdrWhitePointInNits. In other effect HDR processing and some parts of
// the wider android ecosystem the assumption is
// targetHdrPeakBrightnessInNits=1000 and targetSdrWhitePointInNits=500
const float HDR_SDR_RATIO = 2.0;

// Matrix values are calculated as inverse of RGB_BT2020_TO_XYZ.
const mat3 XYZ_TO_RGB_BT2020 =
    mat3(1.71665, -0.666684, 0.0176399, -0.355671, 1.61648, -0.0427706,
         -0.253366, 0.0157685, 0.942103);
// Matrix values are calculated as inverse of XYZ_TO_RGB_BT709.
const mat3 RGB_BT709_TO_XYZ =
    mat3(0.412391, 0.212639, 0.0193308, 0.357584, 0.715169, 0.119195, 0.180481,
         0.0721923, 0.950532);

// Reference:
// https://developer.android.com/reference/android/graphics/Gainmap#applying-a-gainmap-manually
// Reference Implementation:
// https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/hwui/effects/GainmapRenderer.cpp;l=117-146;drc=fadc20184ccb27fe15bb862e6e03fa6d05d41eac
highp vec3 applyGainmap(vec4 S, vec4 G, int uGainmapIsAlpha, int uNoGamma,
                        int uSingleChannel, vec4 uLogRatioMin,
                        vec4 uLogRatioMax, vec4 uEpsilonSdr, vec4 uEpsilonHdr,
                        vec4 uGainmapGamma, float uDisplayRatioHdr,
                        float uDisplayRatioSdr) {
  float W = clamp((log(HDR_SDR_RATIO) - log(uDisplayRatioSdr)) /
                      (log(uDisplayRatioHdr) - log(uDisplayRatioSdr)),
                  0.0, 1.0);
  vec3 H;
  if (uGainmapIsAlpha == 1) {
    G = vec4(G.a, G.a, G.a, 1.0);
  }
  if (uSingleChannel == 1) {
    mediump float L;
    if (uNoGamma == 1) {
      L = mix(uLogRatioMin.r, uLogRatioMax.r, G.r);
    } else {
      L = mix(uLogRatioMin.r, uLogRatioMax.r, pow(G.r, uGainmapGamma.r));
    }
    H = (S.rgb + uEpsilonSdr.rgb) * exp(L * W) - uEpsilonHdr.rgb;
  } else {
    mediump vec3 L;
    if (uNoGamma == 1) {
      L = mix(uLogRatioMin.rgb, uLogRatioMax.rgb, G.rgb);
    } else {
      L = mix(uLogRatioMin.rgb, uLogRatioMax.rgb,
              pow(G.rgb, uGainmapGamma.rgb));
    }
    H = (S.rgb + uEpsilonSdr.rgb) * exp(L * W) - uEpsilonHdr.rgb;
  }
  return H;
}

highp vec3 bt709ToBt2020(vec3 bt709Color) {
  return XYZ_TO_RGB_BT2020 * RGB_BT709_TO_XYZ * bt709Color;
}

vec3 scaleHdrLuminance(vec3 linearColor) {
  const float SDR_MAX_LUMINANCE = 500.0;
  const float HDR_MAX_LUMINANCE = 1000.0;
  return linearColor * SDR_MAX_LUMINANCE / HDR_MAX_LUMINANCE;
}

// sRGB EOTF for one channel.
float srgbEotfSingleChannel(float srgb) {
  return srgb <= 0.04045 ? srgb / 12.92 : pow((srgb + 0.055) / 1.055, 2.4);
}

// sRGB EOTF.
vec4 srgbEotf(vec4 srgb) {
  return vec4(srgbEotfSingleChannel(srgb.r), srgbEotfSingleChannel(srgb.g),
              srgbEotfSingleChannel(srgb.b), srgb.a);
}
