#version 100
// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ES 2 fragment shader that:
// 1. Samples from an external texture with uTexSampler copying from this
//    texture to the current output.
// 2. Transforms the electrical colors to "working" colors which is the input
//    colorspace with the colors transferred to either linear or SMPTE 170M as
//    requested by uSdrWorkingColorSpace.
// 3. Applies a 4x4 RGB color matrix to change the pixel colors.
// 4. Outputs as requested by uOutputColorTransfer.

#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTexSampler;
uniform mat4 uRgbMatrix;
varying vec2 vTexSamplingCoord;
// C.java#ColorTransfer value.
// Only COLOR_TRANSFER_LINEAR and COLOR_TRANSFER_SDR_VIDEO are allowed.
uniform int uOutputColorTransfer;
uniform int uSdrWorkingColorSpace;

const float inverseGamma = 0.4500;
const float gamma = 1.0 / inverseGamma;
const int GL_FALSE = 0;
const int GL_TRUE = 1;
// LINT.IfChange(working_color_space)
const int WORKING_COLOR_SPACE_DEFAULT = 0;
const int WORKING_COLOR_SPACE_ORIGINAL = 1;
const int WORKING_COLOR_SPACE_LINEAR = 2;
// LINT.ThenChange(../../../../../effect/src/main/java/androidx/media3/effect/DefaultVideoFrameProcessor.java:working_color_space)

// Output colors for an obviously visible error.
const vec3 ERROR_COLOR_BLUE = vec3(0.0, 0.0, 1.0);
const vec3 ERROR_COLOR_RED = vec3(1.0, 0.0, 0.0);

// Transforms a single channel from electrical to optical SDR using the SMPTE
// 170M OETF.
float smpte170mEotfSingleChannel(float electricalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return electricalChannel < 0.0812
             ? electricalChannel / 4.500
             : pow((electricalChannel + 0.099) / 1.099, gamma);
}

// Transforms electrical to optical SDR using the SMPTE 170M EOTF.
vec3 smpte170mEotf(vec3 electricalColor) {
  return vec3(smpte170mEotfSingleChannel(electricalColor.r),
              smpte170mEotfSingleChannel(electricalColor.g),
              smpte170mEotfSingleChannel(electricalColor.b));
}

// Transforms a single channel from optical to electrical SDR.
float smpte170mOetfSingleChannel(float opticalChannel) {
  // Specification:
  // https://www.itu.int/rec/R-REC-BT.1700-0-200502-I/en
  return opticalChannel < 0.018
             ? opticalChannel * 4.500
             : 1.099 * pow(opticalChannel, inverseGamma) - 0.099;
}

// Transforms optical SDR colors to electrical SDR using the SMPTE 170M OETF.
vec3 smpte170mOetf(vec3 opticalColor) {
  return vec3(smpte170mOetfSingleChannel(opticalColor.r),
              smpte170mOetfSingleChannel(opticalColor.g),
              smpte170mOetfSingleChannel(opticalColor.b));
}

// Optionally applies the appropriate EOTF to convert nonlinear electrical
// signals to linear optical signals. Input and output are both normalized to
// [0, 1].
vec3 convertToWorkingColors(vec3 inputColor) {
  if (uSdrWorkingColorSpace == WORKING_COLOR_SPACE_DEFAULT ||
      uSdrWorkingColorSpace == WORKING_COLOR_SPACE_ORIGINAL) {
    return inputColor;
  } else if (uSdrWorkingColorSpace == WORKING_COLOR_SPACE_LINEAR) {
    return smpte170mEotf(inputColor);
  } else {
    return ERROR_COLOR_BLUE;
  }
}

// Optionally applies the appropriate OETF to convert linear optical signals to
// nonlinear electrical signals. Input and output are both normalized to [0, 1].
highp vec3 convertToOutputColors(highp vec3 workingColors) {
  // LINT.IfChange(color_transfer)
  const int COLOR_TRANSFER_LINEAR = 1;
  const int COLOR_TRANSFER_SDR_VIDEO = 3;
  // LINT.ThenChange(../../../../../common/src/main/java/androidx/media3/common/C.java:color_transfer)
  if (uSdrWorkingColorSpace == WORKING_COLOR_SPACE_DEFAULT) {
    if (uOutputColorTransfer == COLOR_TRANSFER_LINEAR) {
      return smpte170mEotf(workingColors);
    } else if (uOutputColorTransfer == COLOR_TRANSFER_SDR_VIDEO) {
      return workingColors;
    } else {
      return ERROR_COLOR_RED;
    }
  } else if (uSdrWorkingColorSpace == WORKING_COLOR_SPACE_ORIGINAL) {
    return workingColors;
  } else if (uSdrWorkingColorSpace == WORKING_COLOR_SPACE_LINEAR) {
    if (uOutputColorTransfer == COLOR_TRANSFER_LINEAR) {
      return workingColors;
    } else if (uOutputColorTransfer == COLOR_TRANSFER_SDR_VIDEO) {
      return smpte170mOetf(workingColors);
    } else {
      return ERROR_COLOR_RED;
    }
  } else {
    return ERROR_COLOR_RED;
  }
}

void main() {
  vec4 inputColor = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 workingColors = convertToWorkingColors(inputColor.rgb);
  vec4 transformedColors = uRgbMatrix * vec4(workingColors, 1);
  gl_FragColor =
      vec4(convertToOutputColors(transformedColors.rgb), inputColor.a);
}
