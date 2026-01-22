/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.formatInvariant;
import static androidx.media3.common.util.Util.loadAsset;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Gainmap;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.SparseArray;
import android.util.SparseIntArray;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/** Applies zero or more {@link TextureOverlay}s onto each frame. */
/* package */ final class OverlayShaderProgram extends BaseGlShaderProgram {

  /** Types of HDR overlay. */
  private static final int HDR_TYPE_ULTRA_HDR = 1;

  private static final int HDR_TYPE_TEXT = 2;

  // The maximum number of samplers allowed in a single GL program is 16.
  // We use one for every overlay and one for the video.
  private static final int MAX_OVERLAY_SAMPLERS = 15;
  private static final String ULTRA_HDR_INSERT = "shaders/insert_ultra_hdr.glsl";
  private static final String FRAGMENT_SHADER_METHODS_INSERT =
      "shaders/insert_overlay_fragment_shader_methods.glsl";
  private static final String TEXTURE_INDEX_FORMAT_SPECIFIER = "%";

  private final GlProgram glProgram;
  private final SamplerOverlayMatrixProvider samplerOverlayMatrixProvider;
  private final ImmutableList<TextureOverlay> overlays;

  @Nullable private final int[] hdrTypes;
  private final SparseArray<Gainmap> lastGainmaps;
  private final SparseIntArray gainmapTexIds;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709. useHdr is
   *     only supported on API 34+ for {@link BitmapOverlay}s, where the {@link Bitmap} contains a
   *     {@link Gainmap}.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public OverlayShaderProgram(
      Context context, boolean useHdr, ImmutableList<TextureOverlay> overlays)
      throws VideoFrameProcessingException {
    super(/* useHighPrecisionColorComponents= */ useHdr, /* texturePoolCapacity= */ 1);
    if (useHdr) {
      hdrTypes = findHdrTypes(overlays);
    } else {
      hdrTypes = null;
      checkArgument(
          overlays.size() <= MAX_OVERLAY_SAMPLERS,
          "OverlayShaderProgram does not support more than 15 SDR overlays in the same instance.");
    }

    this.overlays = overlays;
    this.samplerOverlayMatrixProvider = new SamplerOverlayMatrixProvider();
    lastGainmaps = new SparseArray<>();
    gainmapTexIds = new SparseIntArray();
    try {
      glProgram =
          new GlProgram(
              createVertexShader(overlays.size()),
              createFragmentShader(context, overlays.size(), hdrTypes));
    } catch (GlUtil.GlException | IOException e) {
      throw new VideoFrameProcessingException(e);
    }

    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    Size videoSize = new Size(inputWidth, inputHeight);
    samplerOverlayMatrixProvider.configure(/* backgroundSize= */ videoSize);
    for (TextureOverlay overlay : overlays) {
      overlay.configure(videoSize);
    }
    return videoSize;
  }

  @Override
  @SuppressLint("NewApi") // Checked API level in constructor
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      glProgram.use();
      for (int texUnitIndex = 1; texUnitIndex <= overlays.size(); texUnitIndex++) {
        TextureOverlay overlay = overlays.get(texUnitIndex - 1);

        if (hdrTypes != null) {
          if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_ULTRA_HDR) {
            checkArgument(overlay instanceof BitmapOverlay);
            Bitmap bitmap = ((BitmapOverlay) overlay).getBitmap(presentationTimeUs);
            checkArgument(bitmap.hasGainmap());
            Gainmap gainmap = checkNotNull(bitmap.getGainmap());
            @Nullable Gainmap lastGainmap = lastGainmaps.get(texUnitIndex);
            if (lastGainmap == null || !GainmapUtil.equals(lastGainmap, gainmap)) {
              lastGainmaps.put(texUnitIndex, gainmap);
              if (gainmapTexIds.get(texUnitIndex, /* valueIfKeyNotFound= */ C.INDEX_UNSET)
                  == C.INDEX_UNSET) {
                gainmapTexIds.put(texUnitIndex, GlUtil.createTexture(gainmap.getGainmapContents()));
              } else {
                GlUtil.setTexture(gainmapTexIds.get(texUnitIndex), gainmap.getGainmapContents());
              }
              glProgram.setSamplerTexIdUniform(
                  "uGainmapTexSampler" + texUnitIndex,
                  gainmapTexIds.get(texUnitIndex),
                  /* texUnitIndex= */ overlays.size() + texUnitIndex);
              GainmapUtil.setGainmapUniforms(
                  glProgram, lastGainmaps.get(texUnitIndex), texUnitIndex);
            }
          } else if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_TEXT) {
            float[] luminanceMatrix = GlUtil.create4x4IdentityMatrix();
            float multiplier =
                overlay.getOverlaySettings(presentationTimeUs).getHdrLuminanceMultiplier();
            Matrix.scaleM(luminanceMatrix, /* mOffset= */ 0, multiplier, multiplier, multiplier);
            glProgram.setFloatsUniform(
                formatInvariant("uLuminanceMatrix%d", texUnitIndex), luminanceMatrix);
          }
        }

        glProgram.setSamplerTexIdUniform(
            formatInvariant("uOverlayTexSampler%d", texUnitIndex),
            overlay.getTextureId(presentationTimeUs),
            texUnitIndex);
        glProgram.setFloatsUniform(
            formatInvariant("uVertexTransformationMatrix%d", texUnitIndex),
            overlay.getVertexTransformation(presentationTimeUs));
        OverlaySettings overlaySettings = overlay.getOverlaySettings(presentationTimeUs);
        Size overlaySize = overlay.getTextureSize(presentationTimeUs);
        glProgram.setFloatsUniform(
            formatInvariant("uTransformationMatrix%d", texUnitIndex),
            samplerOverlayMatrixProvider.getTransformationMatrix(overlaySize, overlaySettings));
        glProgram.setFloatUniform(
            formatInvariant("uOverlayAlphaScale%d", texUnitIndex), overlaySettings.getAlphaScale());
      }

      glProgram.setSamplerTexIdUniform("uVideoTexSampler0", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      glProgram.delete();
      for (int i = 0; i < overlays.size(); i++) {
        overlays.get(i).release();
        if (hdrTypes != null && hdrTypes[i] == HDR_TYPE_ULTRA_HDR) {
          int gainmapTexId = gainmapTexIds.get(i, /* valueIfKeyNotFound= */ C.INDEX_UNSET);
          if (gainmapTexId != C.INDEX_UNSET) {
            GlUtil.deleteTexture(gainmapTexId);
          }
        }
      }
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private static int[] findHdrTypes(ImmutableList<TextureOverlay> overlays) {
    int[] hdrTypes = new int[overlays.size()];
    int overlaySamplersAvailable = MAX_OVERLAY_SAMPLERS;
    for (int i = 0; i < overlays.size(); i++) {
      TextureOverlay overlay = overlays.get(i);
      if (overlay instanceof TextOverlay) {
        // TextOverlay must be checked first since they extend BitmapOverlay.
        hdrTypes[i] = HDR_TYPE_TEXT;
        overlaySamplersAvailable -= 1;
      } else if (overlay instanceof BitmapOverlay) {
        checkState(Util.SDK_INT >= 34);
        hdrTypes[i] = HDR_TYPE_ULTRA_HDR;
        // Each UltraHDR overlay uses an extra texture to apply the gainmap to the base in the
        // shader.
        overlaySamplersAvailable -= 2;
      } else {
        throw new IllegalArgumentException(overlay + " is not supported on HDR content.");
      }
      if (overlaySamplersAvailable < 0) {
        throw new IllegalArgumentException(
            "Too many HDR overlays in the same OverlayShaderProgram instance.");
      }
    }
    return hdrTypes;
  }

  private static String createVertexShader(int numOverlays) {
    StringBuilder shader =
        new StringBuilder()
            .append("#version 100\n")
            .append("attribute vec4 aFramePosition;\n")
            .append("varying vec2 vVideoTexSamplingCoord0;\n");

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(formatInvariant("uniform mat4 uTransformationMatrix%s;\n", texUnitIndex))
          .append(formatInvariant("uniform mat4 uVertexTransformationMatrix%s;\n", texUnitIndex))
          .append(formatInvariant("varying vec2 vOverlayTexSamplingCoord%s;\n", texUnitIndex));
    }

    shader
        .append("vec2 getTexSamplingCoord(vec2 ndcPosition){\n")
        .append("  return vec2(ndcPosition.x * 0.5 + 0.5, ndcPosition.y * 0.5 + 0.5);\n")
        .append("}\n")
        .append("void main() {\n")
        .append("  gl_Position = aFramePosition;\n")
        .append("  vVideoTexSamplingCoord0 = getTexSamplingCoord(aFramePosition.xy);\n");

    String variablesTemplate =
        "      vec4 aOverlayPosition% =\n"
            + "  uVertexTransformationMatrix% * uTransformationMatrix% * aFramePosition;\n"
            + "vOverlayTexSamplingCoord% = getTexSamplingCoord(aOverlayPosition%.xy);";
    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader.append(replaceFormatSpecifierWithIndex(variablesTemplate, texUnitIndex));
    }

    shader.append("}\n");

    return shader.toString();
  }

  private static String createFragmentShader(
      Context context, int numOverlays, @Nullable int[] hdrTypes) throws IOException {
    StringBuilder shader =
        new StringBuilder()
            .append("#version 100\n")
            .append("precision mediump float;\n")
            .append("uniform sampler2D uVideoTexSampler0;\n")
            .append("varying vec2 vVideoTexSamplingCoord0;\n")
            .append("\n");

    shader.append(loadAsset(context, FRAGMENT_SHADER_METHODS_INSERT));

    if (hdrTypes != null) {
      shader.append(loadAsset(context, ULTRA_HDR_INSERT));
    }

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(formatInvariant("uniform sampler2D uOverlayTexSampler%d;\n", texUnitIndex))
          .append(formatInvariant("uniform float uOverlayAlphaScale%d;\n", texUnitIndex))
          .append(formatInvariant("varying vec2 vOverlayTexSamplingCoord%d;\n", texUnitIndex))
          .append("\n");
      if (hdrTypes != null) {
        if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_ULTRA_HDR) {
          shader
              .append("// Uniforms for applying the gainmap to the base.\n")
              .append(formatInvariant("uniform sampler2D uGainmapTexSampler%d;\n", texUnitIndex))
              .append(formatInvariant("uniform int uGainmapIsAlpha%d;\n", texUnitIndex))
              .append(formatInvariant("uniform int uNoGamma%d;\n", texUnitIndex))
              .append(formatInvariant("uniform int uSingleChannel%d;\n", texUnitIndex))
              .append(formatInvariant("uniform vec4 uLogRatioMin%d;\n", texUnitIndex))
              .append(formatInvariant("uniform vec4 uLogRatioMax%d;\n", texUnitIndex))
              .append(formatInvariant("uniform vec4 uEpsilonSdr%d;\n", texUnitIndex))
              .append(formatInvariant("uniform vec4 uEpsilonHdr%d;\n", texUnitIndex))
              .append(formatInvariant("uniform vec4 uGainmapGamma%d;\n", texUnitIndex))
              .append(formatInvariant("uniform float uDisplayRatioHdr%d;\n", texUnitIndex))
              .append(formatInvariant("uniform float uDisplayRatioSdr%d;\n", texUnitIndex))
              .append("\n");
        } else if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_TEXT) {
          shader.append(formatInvariant("uniform mat4 uLuminanceMatrix%d;\n", texUnitIndex));
        }
      }
    }

    shader
        .append("void main() {\n")
        .append(" vec4 videoColor = vec4(texture2D(uVideoTexSampler0, vVideoTexSamplingCoord0));\n")
        .append(" vec4 fragColor = videoColor;\n");

    String eletricalColorTemplate =
        "        vec4 electricalOverlayColor% = getClampToBorderOverlayColor(\n"
            + "      uOverlayTexSampler%, vOverlayTexSamplingCoord%, uOverlayAlphaScale%);\n";
    String gainmapApplicationTemplate =
        "        vec4 gainmap% = texture2D(uGainmapTexSampler%, vOverlayTexSamplingCoord%);\n"
            + "  vec3 opticalBt709Color% = applyGainmap(\n"
            + "      srgbEotf(electricalOverlayColor%), gainmap%, uGainmapIsAlpha%, uNoGamma%,\n"
            + "      uSingleChannel%, uLogRatioMin%, uLogRatioMax%, uEpsilonSdr%, uEpsilonHdr%,\n"
            + "      uGainmapGamma%, uDisplayRatioHdr%, uDisplayRatioSdr%);\n"
            + "  vec4 opticalBt2020OverlayColor% =\n"
            + "      vec4(scaleHdrLuminance(bt709ToBt2020(opticalBt709Color%)),"
            + "           electricalOverlayColor%.a);";
    String luminanceApplicationTemplate =
        "vec4 opticalOverlayColor% = uLuminanceMatrix% * srgbEotf(electricalOverlayColor%);\n";
    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader.append(replaceFormatSpecifierWithIndex(eletricalColorTemplate, texUnitIndex));
      String overlayMixColor = "electricalOverlayColor";
      if (hdrTypes != null) {
        if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_ULTRA_HDR) {
          shader.append(replaceFormatSpecifierWithIndex(gainmapApplicationTemplate, texUnitIndex));
          overlayMixColor = "opticalBt2020OverlayColor";
        } else if (hdrTypes[texUnitIndex - 1] == HDR_TYPE_TEXT) {
          shader.append(
              replaceFormatSpecifierWithIndex(luminanceApplicationTemplate, texUnitIndex));
          overlayMixColor = "opticalOverlayColor";
        }
      }
      shader.append(
          formatInvariant(
              "  fragColor = getMixColor(fragColor, %s%d);\n", overlayMixColor, texUnitIndex));
    }

    shader.append("  gl_FragColor = fragColor;\n").append("}\n");

    return shader.toString();
  }

  private static String replaceFormatSpecifierWithIndex(String s, int index) {
    return s.replace(TEXTURE_INDEX_FORMAT_SPECIFIER, Integer.toString(index));
  }
}
