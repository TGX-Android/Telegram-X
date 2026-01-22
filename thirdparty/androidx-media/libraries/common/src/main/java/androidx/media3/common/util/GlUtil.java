/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.common.util;

import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.GLU.gluErrorString;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.microedition.khronos.egl.EGL10;

/** OpenGL ES utilities. */
@SuppressWarnings("InlinedApi") // GLES constants are used safely based on the API version.
@UnstableApi
public final class GlUtil {

  /** Thrown when an OpenGL error occurs. */
  public static final class GlException extends Exception {
    /** Creates an instance with the specified error message. */
    public GlException(String message) {
      super(message);
    }
  }

  /** Number of elements in a 3d homogeneous coordinate vector describing a vertex. */
  public static final int HOMOGENEOUS_COORDINATE_VECTOR_SIZE = 4;

  /** Length of the normalized device coordinate (NDC) space, which spans from -1 to 1. */
  public static final float LENGTH_NDC = 2f;

  public static final int[] EGL_CONFIG_ATTRIBUTES_RGBA_8888 =
      new int[] {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, /* redSize= */ 8,
        EGL14.EGL_GREEN_SIZE, /* greenSize= */ 8,
        EGL14.EGL_BLUE_SIZE, /* blueSize= */ 8,
        EGL14.EGL_ALPHA_SIZE, /* alphaSize= */ 8,
        EGL14.EGL_DEPTH_SIZE, /* depthSize= */ 0,
        EGL14.EGL_STENCIL_SIZE, /* stencilSize= */ 0,
        EGL14.EGL_NONE
      };
  public static final int[] EGL_CONFIG_ATTRIBUTES_RGBA_1010102 =
      new int[] {
        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_RED_SIZE, /* redSize= */ 10,
        EGL14.EGL_GREEN_SIZE, /* greenSize= */ 10,
        EGL14.EGL_BLUE_SIZE, /* blueSize= */ 10,
        EGL14.EGL_ALPHA_SIZE, /* alphaSize= */ 2,
        EGL14.EGL_DEPTH_SIZE, /* depthSize= */ 0,
        EGL14.EGL_STENCIL_SIZE, /* stencilSize= */ 0,
        EGL14.EGL_NONE
      };

  // https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glFenceSync.xhtml
  private static final long GL_FENCE_SYNC_FAILED = 0;
  // https://www.khronos.org/registry/EGL/extensions/EXT/EGL_EXT_protected_content.txt
  private static final String EXTENSION_PROTECTED_CONTENT = "EGL_EXT_protected_content";
  // https://www.khronos.org/registry/EGL/extensions/KHR/EGL_KHR_surfaceless_context.txt
  private static final String EXTENSION_SURFACELESS_CONTEXT = "EGL_KHR_surfaceless_context";
  // https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_YUV_target.txt
  private static final String EXTENSION_YUV_TARGET = "GL_EXT_YUV_target";
  // https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_gl_colorspace_bt2020_linear.txt
  private static final String EXTENSION_COLORSPACE_BT2020_PQ = "EGL_EXT_gl_colorspace_bt2020_pq";
  private static final String EXTENSION_COLORSPACE_BT2020_HLG = "EGL_EXT_gl_colorspace_bt2020_hlg";
  // https://registry.khronos.org/EGL/extensions/KHR/EGL_KHR_gl_colorspace.txt
  private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
  // https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_gl_colorspace_bt2020_linear.txt
  private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;
  private static final int[] EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_PQ =
      new int[] {
        EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT, EGL14.EGL_NONE, EGL14.EGL_NONE
      };
  private static final int EGL_GL_COLORSPACE_BT2020_HLG_EXT = 0x3540;
  private static final int[] EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_HLG =
      new int[] {
        EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_HLG_EXT, EGL14.EGL_NONE, EGL14.EGL_NONE
      };
  private static final int[] EGL_WINDOW_SURFACE_ATTRIBUTES_NONE = new int[] {EGL14.EGL_NONE};

  /** Class only contains static methods. */
  private GlUtil() {}

  /** Bounds of normalized device coordinates, commonly used for defining viewport boundaries. */
  public static float[] getNormalizedCoordinateBounds() {
    return new float[] {
      -1, -1, 0, 1,
      1, -1, 0, 1,
      -1, 1, 0, 1,
      1, 1, 0, 1
    };
  }

  /** Typical bounds used for sampling from textures. */
  public static float[] getTextureCoordinateBounds() {
    return new float[] {
      0, 0, 0, 1,
      1, 0, 0, 1,
      0, 1, 0, 1,
      1, 1, 0, 1
    };
  }

  /** Creates a 4x4 identity matrix. */
  public static float[] create4x4IdentityMatrix() {
    float[] matrix = new float[16];
    setToIdentity(matrix);
    return matrix;
  }

  /** Sets the input {@code matrix} to an identity matrix. */
  public static void setToIdentity(float[] matrix) {
    Matrix.setIdentityM(matrix, /* smOffset= */ 0);
  }

  /** Flattens the list of 4 element NDC coordinate vectors into a buffer. */
  public static float[] createVertexBuffer(List<float[]> vertexList) {
    float[] vertexBuffer = new float[HOMOGENEOUS_COORDINATE_VECTOR_SIZE * vertexList.size()];
    for (int i = 0; i < vertexList.size(); i++) {
      System.arraycopy(
          /* src= */ vertexList.get(i),
          /* srcPos= */ 0,
          /* dest= */ vertexBuffer,
          /* destPos= */ HOMOGENEOUS_COORDINATE_VECTOR_SIZE * i,
          /* length= */ HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    }
    return vertexBuffer;
  }

  /**
   * Returns whether creating a GL context with {@link #EXTENSION_PROTECTED_CONTENT} is possible.
   *
   * <p>If {@code true}, the device supports a protected output path for DRM content when using GL.
   */
  public static boolean isProtectedContentExtensionSupported(Context context) {
    if (Util.SDK_INT < 24) {
      return false;
    }
    if (Util.SDK_INT < 26
        && ("samsung".equals(Build.MANUFACTURER) || "XT1650".equals(Build.MODEL))) {
      // Samsung devices running Nougat are known to be broken. See
      // https://github.com/google/ExoPlayer/issues/3373 and [Internal: b/37197802].
      // Moto Z XT1650 is also affected. See
      // https://github.com/google/ExoPlayer/issues/3215.
      return false;
    }
    if (Util.SDK_INT < 26
        && !context
            .getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
      // Pre API level 26 devices were not well tested unless they supported VR mode.
      return false;
    }

    return isExtensionSupported(EXTENSION_PROTECTED_CONTENT);
  }

  /**
   * Returns whether the {@link #EXTENSION_SURFACELESS_CONTEXT} extension is supported.
   *
   * <p>This extension allows passing {@link EGL14#EGL_NO_SURFACE} for both the write and read
   * surfaces in a call to {@link EGL14#eglMakeCurrent(EGLDisplay, EGLSurface, EGLSurface,
   * EGLContext)}.
   */
  public static boolean isSurfacelessContextExtensionSupported() {
    return isExtensionSupported(EXTENSION_SURFACELESS_CONTEXT);
  }

  /**
   * Returns whether the {@link #EXTENSION_YUV_TARGET} extension is supported.
   *
   * <p>This extension allows sampling raw YUV values from an external texture, which is required
   * for HDR input.
   */
  public static boolean isYuvTargetExtensionSupported() {
    @Nullable String glExtensions;
    if (Objects.equals(EGL14.eglGetCurrentContext(), EGL14.EGL_NO_CONTEXT)) {
      // Create a placeholder context and make it current to allow calling GLES20.glGetString().
      try {
        EGLDisplay eglDisplay = getDefaultEglDisplay();
        EGLContext eglContext = createEglContext(eglDisplay);
        createFocusedPlaceholderEglSurface(eglContext, eglDisplay);
        glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        destroyEglContext(eglDisplay, eglContext);
      } catch (GlException e) {
        return false;
      }
    } else {
      glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
    }

    return glExtensions != null && glExtensions.contains(EXTENSION_YUV_TARGET);
  }

  /** Returns whether {@link #EXTENSION_COLORSPACE_BT2020_PQ} is supported. */
  public static boolean isBt2020PqExtensionSupported() {
    // On API<33, the system cannot display PQ content correctly regardless of whether BT2020 PQ
    // GL extension is supported. Context: http://b/252537203#comment5.
    return Util.SDK_INT >= 33 && isExtensionSupported(EXTENSION_COLORSPACE_BT2020_PQ);
  }

  /** Returns whether {@link #EXTENSION_COLORSPACE_BT2020_HLG} is supported. */
  public static boolean isBt2020HlgExtensionSupported() {
    return isExtensionSupported(EXTENSION_COLORSPACE_BT2020_HLG);
  }

  /** Returns an initialized default {@link EGLDisplay}. */
  public static EGLDisplay getDefaultEglDisplay() throws GlException {
    EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    checkGlException(!eglDisplay.equals(EGL14.EGL_NO_DISPLAY), "No EGL display.");
    checkGlException(
        EGL14.eglInitialize(
            eglDisplay,
            /* unusedMajor */ new int[1],
            /* majorOffset= */ 0,
            /* unusedMinor */ new int[1],
            /* minorOffset= */ 0),
        "Error in eglInitialize.");
    checkGlError();
    return eglDisplay;
  }

  /**
   * Creates a new {@link EGLContext} for the specified {@link EGLDisplay}.
   *
   * <p>Configures the {@link EGLContext} with {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888} and OpenGL
   * ES 2.0.
   *
   * @param eglDisplay The {@link EGLDisplay} to create an {@link EGLContext} for.
   */
  public static EGLContext createEglContext(EGLDisplay eglDisplay) throws GlException {
    return createEglContext(
        EGL14.EGL_NO_CONTEXT, eglDisplay, /* openGlVersion= */ 2, EGL_CONFIG_ATTRIBUTES_RGBA_8888);
  }

  /**
   * Creates a new {@link EGLContext} for the specified {@link EGLDisplay}.
   *
   * @param sharedContext The {@link EGLContext} with which to share data.
   * @param eglDisplay The {@link EGLDisplay} to create an {@link EGLContext} for.
   * @param openGlVersion The version of OpenGL ES to configure. Accepts either {@code 2}, for
   *     OpenGL ES 2.0, or {@code 3}, for OpenGL ES 3.0.
   * @param configAttributes The attributes to configure EGL with. Accepts either {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_1010102}, or {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
   */
  public static EGLContext createEglContext(
      EGLContext sharedContext,
      EGLDisplay eglDisplay,
      @IntRange(from = 2, to = 3) int openGlVersion,
      int[] configAttributes)
      throws GlException {
    checkArgument(
        Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_8888)
            || Arrays.equals(configAttributes, EGL_CONFIG_ATTRIBUTES_RGBA_1010102));
    checkArgument(openGlVersion == 2 || openGlVersion == 3);
    int[] contextAttributes = {EGL_CONTEXT_CLIENT_VERSION, openGlVersion, EGL14.EGL_NONE};
    EGLContext eglContext =
        EGL14.eglCreateContext(
            eglDisplay,
            getEglConfig(eglDisplay, configAttributes),
            sharedContext,
            contextAttributes,
            /* offset= */ 0);
    if (eglContext == null || eglContext.equals(EGL14.EGL_NO_CONTEXT)) {
      EGL14.eglTerminate(eglDisplay);
      throw new GlException(
          "eglCreateContext() failed to create a valid context. The device may not support EGL"
              + " version "
              + openGlVersion);
    }
    checkGlError();
    return eglContext;
  }

  /**
   * Creates a new {@link EGLSurface} wrapping the specified {@code surface}.
   *
   * <p>The {@link EGLSurface} will configure with OpenGL ES 2.0.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param surface The surface to wrap; must be a surface, surface texture or surface holder.
   * @param colorTransfer The {@linkplain C.ColorTransfer color transfer characteristics} to which
   *     the {@code surface} is configured. The only accepted values are {@link
   *     C#COLOR_TRANSFER_SDR}, {@link C#COLOR_TRANSFER_HLG}, and {@link C#COLOR_TRANSFER_ST2084}.
   * @param isEncoderInputSurface Whether the {@code surface} is the input surface of an encoder.
   */
  public static EGLSurface createEglSurface(
      EGLDisplay eglDisplay,
      Object surface,
      @C.ColorTransfer int colorTransfer,
      boolean isEncoderInputSurface)
      throws GlException {
    int[] configAttributes;
    int[] windowAttributes;
    if (colorTransfer == C.COLOR_TRANSFER_SDR || colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2) {
      configAttributes = EGL_CONFIG_ATTRIBUTES_RGBA_8888;
      windowAttributes = EGL_WINDOW_SURFACE_ATTRIBUTES_NONE;
    } else if (colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084) {
      configAttributes = EGL_CONFIG_ATTRIBUTES_RGBA_1010102;
      // !isEncoderInputSurface means outputting to a display surface. HDR display surfaces
      // require EGL_GL_COLORSPACE_BT2020_PQ_EXT or EGL_GL_COLORSPACE_BT2020_HLG_EXT.
      if (isEncoderInputSurface) {
        // Outputting BT2020 PQ or HLG with EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_PQ to an encoder
        // causes the encoder to incorrectly switch to full range color, even if the encoder is
        // configured with limited range color, because EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_PQ sets
        // full range color output, and GL windowAttributes overrides encoder settings.
        windowAttributes = EGL_WINDOW_SURFACE_ATTRIBUTES_NONE;
      } else if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
        if (!isBt2020PqExtensionSupported()) {
          throw new GlException("BT.2020 PQ OpenGL output isn't supported.");
        }
        // TODO(b/262259999): HDR10 PQ content looks dark on the screen, on API 33.
        windowAttributes = EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_PQ;
      } else {
        if (!isBt2020HlgExtensionSupported()) {
          throw new GlException("BT.2020 HLG OpenGL output isn't supported.");
        }
        windowAttributes = EGL_WINDOW_SURFACE_ATTRIBUTES_BT2020_HLG;
      }
    } else {
      throw new IllegalArgumentException("Unsupported color transfer: " + colorTransfer);
    }
    EGLSurface eglSurface =
        EGL14.eglCreateWindowSurface(
            eglDisplay,
            getEglConfig(eglDisplay, configAttributes),
            surface,
            windowAttributes,
            /* offset= */ 0);
    checkEglException("Error creating a new EGL surface");
    return eglSurface;
  }

  /**
   * Creates a new {@link EGLSurface} wrapping a pixel buffer.
   *
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @param width The width of the pixel buffer.
   * @param height The height of the pixel buffer.
   * @param configAttributes EGL configuration attributes. Valid arguments include {@link
   *     #EGL_CONFIG_ATTRIBUTES_RGBA_8888} and {@link #EGL_CONFIG_ATTRIBUTES_RGBA_1010102}.
   */
  private static EGLSurface createPbufferSurface(
      EGLDisplay eglDisplay, int width, int height, int[] configAttributes) throws GlException {
    int[] pbufferAttributes =
        new int[] {
          EGL14.EGL_WIDTH, width,
          EGL14.EGL_HEIGHT, height,
          EGL14.EGL_NONE
        };
    EGLSurface eglSurface =
        EGL14.eglCreatePbufferSurface(
            eglDisplay,
            getEglConfig(eglDisplay, configAttributes),
            pbufferAttributes,
            /* offset= */ 0);
    checkEglException("Error creating a new EGL Pbuffer surface");
    return eglSurface;
  }

  /**
   * Creates and focuses a placeholder {@link EGLSurface}.
   *
   * <p>This makes a {@link EGLContext} current when reading and writing to a surface is not
   * required, configured with {@link #EGL_CONFIG_ATTRIBUTES_RGBA_8888}.
   *
   * @param eglContext The {@link EGLContext} to make current.
   * @param eglDisplay The {@link EGLDisplay} to attach the surface to.
   * @return {@link EGL14#EGL_NO_SURFACE} if supported and a 1x1 pixel buffer surface otherwise.
   */
  public static EGLSurface createFocusedPlaceholderEglSurface(
      EGLContext eglContext, EGLDisplay eglDisplay) throws GlException {
    // EGL_CONFIG_ATTRIBUTES_RGBA_1010102 could be used for HDR input, but EGL14.EGL_NO_SURFACE
    // support was added before EGL 2, so HDR-capable devices should have support for EGL_NO_SURFACE
    // and therefore configAttributes shouldn't matter for HDR.
    int[] configAttributes = EGL_CONFIG_ATTRIBUTES_RGBA_8888;
    EGLSurface eglSurface =
        isSurfacelessContextExtensionSupported()
            ? EGL14.EGL_NO_SURFACE
            : createPbufferSurface(eglDisplay, /* width= */ 1, /* height= */ 1, configAttributes);

    focusEglSurface(eglDisplay, eglContext, eglSurface, /* width= */ 1, /* height= */ 1);
    return eglSurface;
  }

  /**
   * Returns the {@link EGL14#EGL_CONTEXT_CLIENT_VERSION} of the current context.
   *
   * <p>Returns {@code 0} if no {@link EGLContext} {@linkplain #createFocusedPlaceholderEglSurface
   * is focused}.
   */
  public static long getContextMajorVersion() throws GlException {
    int[] currentEglContextVersion = new int[1];
    EGL14.eglQueryContext(
        EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
        EGL14.eglGetCurrentContext(),
        EGL_CONTEXT_CLIENT_VERSION,
        currentEglContextVersion,
        /* offset= */ 0);
    checkGlError();
    return currentEglContextVersion[0];
  }

  /**
   * Returns a newly created sync object and inserts it into the GL command stream.
   *
   * <p>Returns {@code 0} if the operation failed, no {@link EGLContext} {@linkplain
   * #createFocusedPlaceholderEglSurface is focused}, or the focused {@link EGLContext} version is
   * less than 3.0.
   */
  public static long createGlSyncFence() throws GlException {
    if (getContextMajorVersion() >= 3) {
      long syncObject = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, /* flags= */ 0);
      checkGlError();
      // Due to specifics of OpenGL, it might happen that the fence creation command is not yet
      // sent into the GPU command queue, which can cause other threads to wait infinitely if
      // the glSyncWait/glClientSyncWait command went into the GPU earlier. Hence, we have to
      // call glFlush to ensure that glFenceSync is inside of the GPU command queue.
      GLES20.glFlush();
      checkGlError();
      return syncObject;
    } else {
      return 0;
    }
  }

  /**
   * Deletes the underlying native object.
   *
   * <p>The {@code syncObject} must not be used after deletion.
   */
  public static void deleteSyncObject(long syncObject) throws GlException {
    deleteSyncObjectQuietly(syncObject);
    checkGlError();
  }

  /** Releases the GL sync object if set, suppressing any error. */
  public static void deleteSyncObjectQuietly(long syncObject) {
    GLES30.glDeleteSync(syncObject);
  }

  /**
   * Ensures that following commands on the current OpenGL context will not be executed until the
   * sync point has been reached. If {@code syncObject} equals {@code 0}, this does not block the
   * CPU, and only affects the current OpenGL context. Otherwise, this will block the CPU.
   */
  public static void awaitSyncObject(long syncObject) throws GlException {
    if (syncObject == GL_FENCE_SYNC_FAILED) {
      // Fallback to using glFinish for synchronization when fence creation failed.
      GLES20.glFinish();
    } else {
      GLES30.glWaitSync(syncObject, /* flags= */ 0, GLES30.GL_TIMEOUT_IGNORED);
      checkGlError();
    }
  }

  /** Gets the current {@link EGLContext context}. */
  public static EGLContext getCurrentContext() {
    return EGL14.eglGetCurrentContext();
  }

  /**
   * Collects all OpenGL errors that occurred since this method was last called and throws a {@link
   * GlException} with the combined error message.
   */
  public static void checkGlError() throws GlException {
    StringBuilder errorMessageBuilder = new StringBuilder();
    boolean foundError = false;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      if (foundError) {
        errorMessageBuilder.append('\n');
      }
      @Nullable String errorString = gluErrorString(error);
      if (errorString == null) {
        errorString = "error code: 0x" + Integer.toHexString(error);
      }
      errorMessageBuilder.append("glError: ").append(errorString);
      foundError = true;
    }
    if (foundError) {
      throw new GlException(errorMessageBuilder.toString());
    }
  }

  /**
   * Asserts the texture size is valid.
   *
   * @param width The width for a texture.
   * @param height The height for a texture.
   * @throws GlException If the texture width or height is invalid.
   */
  private static void assertValidTextureSize(int width, int height) throws GlException {
    // TODO(b/201293185): Consider handling adjustments for sizes > GL_MAX_TEXTURE_SIZE
    //  (ex. downscaling appropriately) in a shader program instead of asserting incorrect
    //  values.
    // For valid GL sizes, see:
    // https://www.khronos.org/registry/OpenGL-Refpages/es2.0/xhtml/glTexImage2D.xml
    int[] maxTextureSizeBuffer = new int[1];
    GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSizeBuffer, 0);
    int maxTextureSize = maxTextureSizeBuffer[0];
    checkState(
        maxTextureSize > 0,
        "Create a OpenGL context first or run the GL methods on an OpenGL thread.");

    if (width < 0 || height < 0) {
      throw new GlException("width or height is less than 0");
    }
    if (width > maxTextureSize || height > maxTextureSize) {
      throw new GlException(
          "width or height is greater than GL_MAX_TEXTURE_SIZE " + maxTextureSize);
    }
  }

  /**
   * Fills the pixels in the current output render target buffers with (r=0, g=0, b=0, a=0).
   *
   * <p>Buffers can be focused using {@link #focusEglSurface} and {@link
   * #focusFramebufferUsingCurrentContext}, {@link #focusFramebuffer}, and {@link
   * #createFocusedPlaceholderEglSurface}.
   */
  public static void clearFocusedBuffers() throws GlException {
    GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
    GLES20.glClearDepthf(1.0f);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    GlUtil.checkGlError();
  }

  /**
   * Makes the specified {@code eglSurface} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   */
  public static void focusEglSurface(
      EGLDisplay eglDisplay, EGLContext eglContext, EGLSurface eglSurface, int width, int height)
      throws GlException {
    focusRenderTarget(eglDisplay, eglContext, eglSurface, /* framebuffer= */ 0, width, height);
  }

  /**
   * Makes the specified {@code framebuffer} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   */
  public static void focusFramebuffer(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int framebuffer,
      int width,
      int height)
      throws GlException {
    focusRenderTarget(eglDisplay, eglContext, eglSurface, framebuffer, width, height);
  }

  /**
   * Makes the specified {@code framebuffer} the render target, using a viewport of {@code width} by
   * {@code height} pixels.
   *
   * <p>The caller must ensure that there is a current OpenGL context before calling this method.
   *
   * @param framebuffer The identifier of the framebuffer object to bind as the output render
   *     target.
   * @param width The viewport width, in pixels.
   * @param height The viewport height, in pixels.
   */
  public static void focusFramebufferUsingCurrentContext(int framebuffer, int width, int height)
      throws GlException {
    int[] boundFramebuffer = new int[1];
    GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFramebuffer, /* offset= */ 0);
    if (boundFramebuffer[0] != framebuffer) {
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
    }
    checkGlError();
    GLES20.glViewport(/* x= */ 0, /* y= */ 0, width, height);
    checkGlError();
  }

  /**
   * Allocates a FloatBuffer with the given data.
   *
   * @param data Used to initialize the new buffer.
   */
  public static FloatBuffer createBuffer(float[] data) {
    return (FloatBuffer) createBuffer(data.length).put(data).flip();
  }

  /**
   * Allocates a FloatBuffer.
   *
   * @param capacity The new buffer's capacity, in floats.
   */
  private static FloatBuffer createBuffer(int capacity) {
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(capacity * C.BYTES_PER_FLOAT);
    return byteBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer();
  }

  /**
   * Creates a GL_TEXTURE_EXTERNAL_OES with default configuration of GL_LINEAR filtering and
   * GL_CLAMP_TO_EDGE wrapping.
   */
  public static int createExternalTexture() throws GlException {
    int texId = generateTexture();
    bindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId, GLES20.GL_LINEAR);
    return texId;
  }

  /**
   * Allocates a new texture, initialized with the {@link Bitmap bitmap} data and size.
   *
   * @param bitmap The {@link Bitmap} for which the texture is created.
   * @return The texture identifier for the newly-allocated texture.
   * @throws GlException If the texture allocation fails.
   */
  public static int createTexture(Bitmap bitmap) throws GlException {
    int texId = generateTexture();
    setTexture(texId, bitmap);
    return texId;
  }

  /**
   * Allocates a new RGBA texture with the specified dimensions and color component precision.
   *
   * <p>The created texture is not zero-initialized. To clear the texture, {@linkplain
   * #focusFramebuffer(EGLDisplay, EGLContext, EGLSurface, int, int, int) focus} on the texture and
   * {@linkplain #clearFocusedBuffers() clear} its content.
   *
   * @param width The width of the new texture in pixels.
   * @param height The height of the new texture in pixels.
   * @param useHighPrecisionColorComponents If {@code false}, uses colors with 8-bit unsigned bytes.
   *     If {@code true}, use 16-bit (half-precision) floating-point.
   * @return The texture identifier for the newly-allocated texture.
   * @throws GlException If the texture allocation fails.
   */
  public static int createTexture(int width, int height, boolean useHighPrecisionColorComponents)
      throws GlException {
    // TODO(b/227624622): Implement a pixel test that confirms 16f has less posterization.
    // TODO - b/309459038: Consider renaming the method, as the created textures are uninitialized.
    if (useHighPrecisionColorComponents) {
      return createTextureUninitialized(width, height, GLES30.GL_RGBA16F, GLES30.GL_HALF_FLOAT);
    }
    return createTextureUninitialized(width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE);
  }

  /**
   * Allocates a new {@linkplain GLES20#GL_RGBA normalized integer} {@link GLES30#GL_RGB10_A2}
   * texture with the specified dimensions.
   *
   * <p>Normalized integers in textures are automatically converted for floating point numbers
   * https://www.khronos.org/opengl/wiki/Normalized_Integer
   *
   * <p>The only supported pixel data type for the {@link GLES30#GL_RGB10_A2} sized internal format
   * is {@link GLES30#GL_UNSIGNED_INT_2_10_10_10_REV}. See
   * https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml
   *
   * <p>The created texture is not zero-initialized. To clear the texture, {@linkplain
   * #focusFramebuffer(EGLDisplay, EGLContext, EGLSurface, int, int, int) focus} on the texture and
   * {@linkplain #clearFocusedBuffers() clear} its content.
   *
   * @param width The width of the new texture in pixels.
   * @param height The height of the new texture in pixels.
   * @return The texture identifier for the newly-allocated texture.
   * @throws GlException If the texture allocation fails.
   */
  public static int createRgb10A2Texture(int width, int height) throws GlException {
    return createTextureUninitialized(
        width,
        height,
        /* internalFormat= */ GLES30.GL_RGB10_A2,
        /* type= */ GLES30.GL_UNSIGNED_INT_2_10_10_10_REV);
  }

  /**
   * Allocates a new RGBA texture with the specified dimensions and color component precision.
   *
   * @param width The width of the new texture in pixels.
   * @param height The height of the new texture in pixels.
   * @param internalFormat The number of color components in the texture, as well as their format.
   * @param type The data type of the pixel data.
   * @throws GlException If the texture allocation fails.
   * @return The texture identifier for the newly-allocated texture.
   */
  private static int createTextureUninitialized(int width, int height, int internalFormat, int type)
      throws GlException {
    assertValidTextureSize(width, height);
    int texId = generateTexture();
    bindTexture(GLES20.GL_TEXTURE_2D, texId, GLES20.GL_LINEAR);
    GLES20.glTexImage2D(
        GLES20.GL_TEXTURE_2D,
        /* level= */ 0,
        internalFormat,
        width,
        height,
        /* border= */ 0,
        GLES20.GL_RGBA,
        type,
        /* buffer= */ null);
    checkGlError();
    return texId;
  }

  /** Returns a new, unbound GL texture identifier. */
  public static int generateTexture() throws GlException {
    int[] texId = new int[1];
    GLES20.glGenTextures(/* n= */ 1, texId, /* offset= */ 0);
    checkGlError();
    return texId[0];
  }

  /** Sets the {@code texId} to contain the {@link Bitmap bitmap} data and size. */
  public static void setTexture(int texId, Bitmap bitmap) throws GlException {
    assertValidTextureSize(bitmap.getWidth(), bitmap.getHeight());
    bindTexture(GLES20.GL_TEXTURE_2D, texId, GLES20.GL_LINEAR);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, /* level= */ 0, bitmap, /* border= */ 0);
    checkGlError();
  }

  /**
   * Binds the texture of the given type with the specified MIN and MAG sampling filter and
   * GL_CLAMP_TO_EDGE wrapping.
   *
   * @param textureTarget The target to which the texture is bound, e.g. {@link
   *     GLES20#GL_TEXTURE_2D} for a two-dimensional texture or {@link
   *     GLES11Ext#GL_TEXTURE_EXTERNAL_OES} for an external texture.
   * @param texId The texture identifier.
   * @param sampleFilter The texture sample filter for both {@link GLES20#GL_TEXTURE_MAG_FILTER} and
   *     {@link GLES20#GL_TEXTURE_MIN_FILTER}.
   */
  public static void bindTexture(int textureTarget, int texId, int sampleFilter)
      throws GlException {
    GLES20.glBindTexture(textureTarget, texId);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, sampleFilter);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, sampleFilter);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    checkGlError();
  }

  /**
   * Returns a new framebuffer for the texture.
   *
   * @param texId The identifier of the texture to attach to the framebuffer.
   */
  public static int createFboForTexture(int texId) throws GlException {
    int[] fboId = new int[1];
    GLES20.glGenFramebuffers(/* n= */ 1, fboId, /* offset= */ 0);
    checkGlError();
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0]);
    checkGlError();
    GLES20.glFramebufferTexture2D(
        GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texId, 0);
    checkGlError();
    return fboId[0];
  }

  /**
   * Deletes a GL texture.
   *
   * @param textureId The ID of the texture to delete.
   */
  public static void deleteTexture(int textureId) throws GlException {
    GLES20.glDeleteTextures(/* n= */ 1, new int[] {textureId}, /* offset= */ 0);
    checkGlError();
  }

  /**
   * Destroys the {@link EGLContext} identified by the provided {@link EGLDisplay} and {@link
   * EGLContext}.
   *
   * <p>This is a no-op if called on already-destroyed {@link EGLDisplay} and {@link EGLContext}
   * instances.
   */
  public static void destroyEglContext(
      @Nullable EGLDisplay eglDisplay, @Nullable EGLContext eglContext) throws GlException {
    if (eglDisplay == null || eglDisplay.equals(EGL14.EGL_NO_DISPLAY)) {
      return;
    }
    EGL14.eglMakeCurrent(
        eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    checkEglException("Error releasing context");
    if (eglContext != null && !eglContext.equals(EGL14.EGL_NO_CONTEXT)) {
      EGL14.eglDestroyContext(eglDisplay, eglContext);
      checkEglException("Error destroying context");
    }
    EGL14.eglReleaseThread();
    checkEglException("Error releasing thread");
    EGL14.eglTerminate(eglDisplay);
    checkEglException("Error terminating display");
  }

  /**
   * Destroys the {@link EGLSurface} identified by the provided {@link EGLDisplay} and {@link
   * EGLSurface}.
   */
  public static void destroyEglSurface(
      @Nullable EGLDisplay eglDisplay, @Nullable EGLSurface eglSurface) throws GlException {
    if (eglDisplay == null || eglDisplay.equals(EGL14.EGL_NO_DISPLAY)) {
      return;
    }
    if (eglSurface == null || eglSurface.equals(EGL14.EGL_NO_SURFACE)) {
      return;
    }

    EGL14.eglDestroySurface(eglDisplay, eglSurface);
    checkEglException("Error destroying surface");
  }

  /** Deletes a framebuffer, or silently ignores the method call if {@code fboId} is unused. */
  public static void deleteFbo(int fboId) throws GlException {
    GLES20.glDeleteFramebuffers(/* n= */ 1, new int[] {fboId}, /* offset= */ 0);
    checkGlError();
  }

  /** Deletes a renderbuffer, or silently ignores the method call if {@code rboId} is unused. */
  public static void deleteRbo(int rboId) throws GlException {
    GLES20.glDeleteRenderbuffers(
        /* n= */ 1, /* renderbuffers= */ new int[] {rboId}, /* offset= */ 0);
    checkGlError();
  }

  /**
   * Copies the pixels from {@code readFboId} into {@code drawFboId}. Requires OpenGL ES 3.0.
   *
   * <p>When the input pixel region (given by {@code readRect}) doesn't have the same size as the
   * output region (given by {@code drawRect}), this method uses {@link GLES20#GL_LINEAR} filtering
   * to scale the image contents.
   *
   * @param readFboId The framebuffer object to read from.
   * @param readRect The rectangular region of {@code readFboId} to read from.
   * @param drawFboId The framebuffer object to draw into.
   * @param drawRect The rectangular region of {@code drawFboId} to draw into.
   */
  public static void blitFrameBuffer(int readFboId, GlRect readRect, int drawFboId, GlRect drawRect)
      throws GlException {
    int[] boundFramebuffer = new int[1];
    GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFramebuffer, /* offset= */ 0);
    checkGlError();
    GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, readFboId);
    checkGlError();
    GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, drawFboId);
    checkGlError();
    GLES30.glBlitFramebuffer(
        readRect.left,
        readRect.bottom,
        readRect.right,
        readRect.top,
        drawRect.left,
        drawRect.bottom,
        drawRect.right,
        drawRect.top,
        GLES30.GL_COLOR_BUFFER_BIT,
        GLES30.GL_LINEAR);
    checkGlError();
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, /* framebuffer= */ boundFramebuffer[0]);
    checkGlError();
  }

  /**
   * Creates a pixel buffer object with a data store of the given size and usage {@link
   * GLES30#GL_DYNAMIC_READ}.
   *
   * <p>The buffer is suitable for repeated modification by OpenGL and reads by the application.
   *
   * @param size The size of the buffer object's data store.
   * @return The pixel buffer object.
   */
  public static int createPixelBufferObject(int size) throws GlException {
    int[] ids = new int[1];
    GLES30.glGenBuffers(/* n= */ 1, ids, /* offset= */ 0);
    GlUtil.checkGlError();

    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, ids[0]);
    GlUtil.checkGlError();

    GLES30.glBufferData(
        GLES30.GL_PIXEL_PACK_BUFFER, /* size= */ size, /* data= */ null, GLES30.GL_DYNAMIC_READ);
    GlUtil.checkGlError();

    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, /* buffer= */ 0);
    GlUtil.checkGlError();
    return ids[0];
  }

  /**
   * Reads pixel data from the {@link GLES30#GL_COLOR_ATTACHMENT0} attachment of a framebuffer into
   * the data store of a pixel buffer object.
   *
   * <p>The texture backing the color attachment of {@code readFboId} and the buffer store of {@code
   * bufferId} must hold an image of the given {@code width} and {@code height} with format {@link
   * GLES30#GL_RGBA} and type {@link GLES30#GL_UNSIGNED_BYTE}.
   *
   * <p>This a non-blocking call which reads the data asynchronously.
   *
   * <p>Requires API 24: This method must call the version of {@link GLES30#glReadPixels(int, int,
   * int, int, int, int, int)} which accepts an integer offset as the last parameter. This version
   * of glReadPixels is not available in the Java {@link GLES30} wrapper until API 24.
   *
   * <p>HDR support is not yet implemented.
   *
   * @param readFboId The framebuffer that holds pixel data.
   * @param width The image width.
   * @param height The image height.
   * @param bufferId The pixel buffer object to read into.
   */
  @RequiresApi(24)
  public static void schedulePixelBufferRead(int readFboId, int width, int height, int bufferId)
      throws GlException {
    focusFramebufferUsingCurrentContext(readFboId, width, height);
    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferId);
    GlUtil.checkGlError();

    GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0);
    GLES30.glReadPixels(
        /* x= */ 0,
        /* y= */ 0,
        width,
        height,
        GLES30.GL_RGBA,
        GLES30.GL_UNSIGNED_BYTE,
        /* offset= */ 0);
    GlUtil.checkGlError();

    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, /* buffer= */ 0);
    GlUtil.checkGlError();
  }

  /**
   * Maps the pixel buffer object's data store of a given size and returns a {@link ByteBuffer} of
   * OpenGL managed memory.
   *
   * <p>The application must not write into the returned {@link ByteBuffer}.
   *
   * <p>The pixel buffer object should have a {@linkplain #schedulePixelBufferRead previously
   * scheduled pixel buffer read}.
   *
   * <p>When the application no longer needs to access the returned buffer, call {@link
   * #unmapPixelBufferObject}.
   *
   * <p>This call blocks until the pixel buffer data from the last {@link #schedulePixelBufferRead}
   * call is available.
   *
   * <p>Requires API 24: see {@link #schedulePixelBufferRead}.
   *
   * @param bufferId The pixel buffer object.
   * @param size The size of the pixel buffer object's data store to be mapped.
   * @return The {@link ByteBuffer} that holds pixel data.
   */
  @RequiresApi(24)
  public static ByteBuffer mapPixelBufferObject(int bufferId, int size) throws GlException {
    GLES20.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferId);
    checkGlError();
    ByteBuffer mappedPixelBuffer =
        (ByteBuffer)
            GLES30.glMapBufferRange(
                GLES30.GL_PIXEL_PACK_BUFFER,
                /* offset= */ 0,
                /* length= */ size,
                GLES30.GL_MAP_READ_BIT);
    GlUtil.checkGlError();
    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, /* buffer= */ 0);
    GlUtil.checkGlError();
    return mappedPixelBuffer;
  }

  /**
   * Unmaps the pixel buffer object {@code bufferId}'s data store.
   *
   * <p>The pixel buffer object should be previously {@linkplain #mapPixelBufferObject mapped}.
   *
   * <p>After this method returns, accessing data inside a previously {@linkplain
   * #mapPixelBufferObject mapped} {@link ByteBuffer} results in undefined behaviour.
   *
   * <p>When this method returns, the pixel buffer object {@code bufferId} can be reused by {@link
   * #schedulePixelBufferRead}.
   *
   * <p>Requires API 24: see {@link #schedulePixelBufferRead}.
   *
   * @param bufferId The pixel buffer object.
   */
  @RequiresApi(24)
  public static void unmapPixelBufferObject(int bufferId) throws GlException {
    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, bufferId);
    GlUtil.checkGlError();
    GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER);
    GlUtil.checkGlError();
    GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, /* buffer= */ 0);
    GlUtil.checkGlError();
  }

  /** Deletes a buffer object, or silently ignores the method call if {@code bufferId} is unused. */
  public static void deleteBuffer(int bufferId) throws GlException {
    GLES20.glDeleteBuffers(/* n= */ 1, new int[] {bufferId}, /* offset= */ 0);
    checkGlError();
  }

  /**
   * Throws a {@link GlException} with the given message if {@code expression} evaluates to {@code
   * false}.
   */
  public static void checkGlException(boolean expression, String errorMessage) throws GlException {
    if (!expression) {
      throw new GlException(errorMessage);
    }
  }

  private static EGLConfig getEglConfig(EGLDisplay eglDisplay, int[] attributes)
      throws GlException {
    EGLConfig[] eglConfigs = new EGLConfig[1];
    if (!EGL14.eglChooseConfig(
        eglDisplay,
        attributes,
        /* attrib_listOffset= */ 0,
        eglConfigs,
        /* configsOffset= */ 0,
        /* config_size= */ 1,
        /* unusedNumConfig */ new int[1],
        /* num_configOffset= */ 0)) {
      throw new GlException("eglChooseConfig failed.");
    }
    return eglConfigs[0];
  }

  private static boolean isExtensionSupported(String extensionName) {
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    @Nullable String eglExtensions = EGL14.eglQueryString(display, EGL10.EGL_EXTENSIONS);
    return eglExtensions != null && eglExtensions.contains(extensionName);
  }

  private static void focusRenderTarget(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int framebuffer,
      int width,
      int height)
      throws GlException {
    EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
    checkEglException("Error making context current");
    focusFramebufferUsingCurrentContext(framebuffer, width, height);
  }

  private static void checkEglException(String errorMessage) throws GlException {
    int error = EGL14.eglGetError();
    if (error != EGL14.EGL_SUCCESS) {
      throw new GlException(errorMessage + ", error code: 0x" + Integer.toHexString(error));
    }
  }
}
