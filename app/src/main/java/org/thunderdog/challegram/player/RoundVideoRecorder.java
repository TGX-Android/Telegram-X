/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 14/10/2017
 */
package org.thunderdog.challegram.player;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.Surface;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.legacy.CameraApi;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.video.Mp4Output;
import org.thunderdog.challegram.video.old.Mp4Movie;
import org.thunderdog.challegram.video.old.Mp4OutputImpl;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;

import me.vkryl.core.BitwiseUtils;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class RoundVideoRecorder {
  public interface Delegate {
    /**
     * Start progress timer at this point, if still required
     * */
    void onVideoRecordingStarted (String key, long startTimeMs);

    /**
     * Called when part of file has been generated
     * */
    void onVideoRecordProgress (String key, long readyBytesCount);

    /**
     * Called when video recording has been completely finished
     *
     * @param resultFileSize Result file size. Negative when recording has been cancelled or failed
     * */
    void onVideoRecordingFinished (String key, long resultFileSize, long resultDuration, TimeUnit resultDurationUnit);
  }

  private static class RoundHandler extends Handler {
    private final RoundVideoRecorder context;
    private final boolean isBackground;

    public RoundHandler (Looper looper, RoundVideoRecorder context, boolean isBackground) {
      super(looper);
      this.context = context;
      this.isBackground = isBackground;
    }

    @Override
    public void handleMessage (Message msg) {
      if (isBackground) {
        context.handleBackgroundMessage(msg);
      } else {
        context.handleCameraMessage(msg);
      }
    }
  }

  private final CameraApi context;

  private final Handler cameraHandler, backgroundHandler;

  private final SurfaceTexture mSurfaceTexture;
  private final int mSurfaceWidth, mSurfaceHeight;
  private int mPreviewWidth, mPreviewHeight;
  private int mOrientation;

  private Delegate mDelegate;
  private String workingKey;
  private String outputPath;
  private volatile boolean isCapturing; // Whether capture should be in progress
  private volatile boolean finishCapture; // Whether capture result should be saved

  public RoundVideoRecorder (CameraApi context, SurfaceTexture surfaceTexture, int surfaceWidth, int surfaceHeight) {
    this.context = context;
    this.cameraHandler = new RoundHandler(context.getCameraHandler().getLooper(), this, false);
    this.backgroundHandler = new RoundHandler(context.getBackgroundHandler().getLooper(), this, true);
    this.mSurfaceTexture = surfaceTexture;
    this.mSurfaceWidth = surfaceWidth;
    this.mSurfaceHeight = surfaceHeight;
  }

  public void setPreviewSizes (int width, int height, int orientation) {
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_SET_SIZES, width, height, orientation));
      return;
    }
    if (mPreviewWidth != 0 || mPreviewHeight != 0) {
      return;
    }
    this.mPreviewWidth = width;
    this.mPreviewHeight = height;
    this.mOrientation = orientation;

    updateSizes();
  }

  public void openCamera () {
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_INIT));
      return;
    }
    prepareGL();
  }

  private volatile boolean isSwitchingToNewCamera;

  public boolean canSwitchToNewCamera () {
    return !isSwitchingToNewCamera && initied && eglSurface != null;
  }

  public void switchToNewCamera () {
    isSwitchingToNewCamera = true;
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_REINIT));
      return;
    }
    doSwitchToNewCamera();
  }

  public void onCameraClose () {
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_CLOSE));
      return;
    }
    doReinitToNextCamera();
  }

  private boolean isDestroyed;

  public void destroy () {
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_DESTROY));
      return;
    }
    if (!isDestroyed) {
      isDestroyed = true;
      doDestroy();
    }
  }

  private SurfaceTexture mCameraSurface;

  public SurfaceTexture getCameraTexture () {
    return mCameraSurface;
  }

  // Entry point

  public void startCapture (Delegate delegate, String key, String outputPath) {
    Log.i(Log.TAG_ROUND, "startCapture: %s, path: %s, isCapturing: %b", key, outputPath, isCapturing);
    if (!isCapturing) {
      this.isCapturing = true;
      this.mDelegate = delegate;
      this.workingKey = key;
      this.outputPath = outputPath;
      boolean success = false;
      try {
        success = startCaptureImpl();
      } catch (Throwable t) {
        Log.w(Log.TAG_ROUND, "Cannot start round video capture", t);
      }
      if (!success) {
        this.isCapturing = false;
      }
    }
  }

  public void finishCapture (String key, boolean saveResult) {
    Log.i(Log.TAG_ROUND, "finishCapture: %s, saveResult: %b, isCapturing: %b", key, saveResult, isCapturing);
    if (isCapturing) {
      this.finishCapture = saveResult;
      this.isCapturing = false;
      stopCaptureImpl();
    }
  }

  // LAYER

  private static final int CAM_ACTION_CREATE = 0;
  private static final int CAM_ACTION_START = 1;

  private void handleCameraMessage (Message msg) { // Camera thread
    switch (msg.what) {
      case CAM_ACTION_CREATE:
        createCamera((SurfaceTexture) msg.obj);
        break;
      case CAM_ACTION_START:
        doStartRecord();
        break;
    }
  }

  private static final int BG_ACTION_INIT = 0;
  private static final int BG_ACTION_REINIT = 1;
  private static final int BG_ACTION_DESTROY = 2;
  private static final int BG_ACTION_CLOSE = 3;
  private static final int BG_ACTION_RENDER = 4;
  private static final int BG_ACTION_SET_SIZES = 5;

  private void handleBackgroundMessage (Message msg) { // Background thread (GL)
    switch (msg.what) {
      case BG_ACTION_INIT:
        openCamera();
        break;
      case BG_ACTION_REINIT:
        switchToNewCamera();
        break;
      case BG_ACTION_DESTROY:
        destroy();
        break;
      case BG_ACTION_CLOSE:
        onCameraClose();
        break;
      case BG_ACTION_RENDER:
        doRender((Integer) msg.obj);
        break;
      case BG_ACTION_SET_SIZES:
        setPreviewSizes(msg.arg1, msg.arg2, (Integer) msg.obj);
        break;
    }
  }

  private static final int UI_ACTION_DISPATCH_VIDEO_RECORD_STARTED = 0;
  private static final int UI_ACTION_DISPATCH_VIDEO_RECORD_FINISHED = 1;
  private static final int UI_ACTION_DISPATCH_VIDEO_RECORD_PROGRESS = 2;

  private static boolean checkUiThread () {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  private static class UiHandler extends Handler {
    private final RoundVideoRecorder context;

    public UiHandler (RoundVideoRecorder context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case UI_ACTION_DISPATCH_VIDEO_RECORD_STARTED: {
          context.dispatchVideoRecordStarted((String) msg.obj, BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
        case UI_ACTION_DISPATCH_VIDEO_RECORD_FINISHED: {
          Object[] data = (Object[]) msg.obj;
          context.dispatchVideoRecordFinished((String) data[0], (long) data[1], (long) data[2], (TimeUnit) data[3]);
          data[0] = data[1] = data[2] = data[3] = null;
          break;
        }
        case UI_ACTION_DISPATCH_VIDEO_RECORD_PROGRESS: {
          context.dispatchVideoRecordProgress((String) msg.obj, BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
          break;
        }
      }
    }
  }

  private final UiHandler uiHandler = new UiHandler(this);

  private void dispatchVideoRecordStarted (String key, long startTimeMs) {
    if (!checkUiThread()) {
      uiHandler.sendMessage(Message.obtain(uiHandler, UI_ACTION_DISPATCH_VIDEO_RECORD_STARTED, BitwiseUtils.splitLongToFirstInt(startTimeMs), BitwiseUtils.splitLongToSecondInt(startTimeMs), key));
      return;
    }
    if (mDelegate != null) {
      mDelegate.onVideoRecordingStarted(key, startTimeMs);
    }
  }

  private void dispatchVideoRecordFinished (String key, long resultFileSize, long resultDuration, TimeUnit resultDurationUnit) {
    if (!checkUiThread()) {
      uiHandler.sendMessage(Message.obtain(
        uiHandler,
        UI_ACTION_DISPATCH_VIDEO_RECORD_FINISHED,
        new Object[] {
          key, resultFileSize, resultDuration, resultDurationUnit
        }
      ));
      return;
    }
    if (mDelegate != null) {
      mDelegate.onVideoRecordingFinished(key, resultFileSize, resultDuration, resultDurationUnit);
    }
  }

  private void dispatchVideoRecordProgress (String key, long readyBytesCount) {
    if (!checkUiThread()) {
      uiHandler.sendMessage(Message.obtain(uiHandler, UI_ACTION_DISPATCH_VIDEO_RECORD_PROGRESS, BitwiseUtils.splitLongToFirstInt(readyBytesCount), BitwiseUtils.splitLongToSecondInt(readyBytesCount), key));
      return;
    }
    if (mDelegate != null) {
      mDelegate.onVideoRecordProgress(key, readyBytesCount);
    }
  }

  private boolean checkCameraThread () {
    return context.checkCameraThread();
  }

  private boolean checkBackgroundThread () {
    return context.checkBackgroundThread();
  }

  // Impl

  private File cameraFile;

  private boolean startCaptureImpl () throws Throwable {
    cameraFile = new File(outputPath);
    /*if (!cameraFile.exists() && !cameraFile.createNewFile()) {
      return false;
    }*/

    doStartRecord();

    return true;
  }

  private void stopCaptureImpl () {
    destroy();
  }


  // Copypasting

  private void requestRender() {
    if (!checkBackgroundThread()) {
      backgroundHandler.sendMessage(Message.obtain(backgroundHandler, BG_ACTION_RENDER, cameraId));
    } else {
      doRender(cameraId);
    }
  }

  private volatile boolean cameraReady;

  private FloatBuffer vertexBuffer;
  private FloatBuffer textureBuffer;

  private static final String VERTEX_SHADER =
    "uniform mat4 uMVPMatrix;\n" +
      "uniform mat4 uSTMatrix;\n" +
      "attribute vec4 aPosition;\n" +
      "attribute vec4 aTextureCoord;\n" +
      "varying vec2 vTextureCoord;\n" +
      "void main() {\n" +
      "   gl_Position = uMVPMatrix * aPosition;\n" +
      "   vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
      "}\n";

  private static final String FRAGMENT_SHADER =
    "#extension GL_OES_EGL_image_external : require\n" +
      "precision highp float;\n" +
      "varying vec2 vTextureCoord;\n" +
      "uniform float scaleX;\n" +
      "uniform float scaleY;\n" +
      "uniform float alpha;\n" +
      "uniform samplerExternalOES sTexture;\n" +
      "void main() {\n" +
      "   vec2 coord = vec2((vTextureCoord.x - 0.5) * scaleX, (vTextureCoord.y - 0.5) * scaleY);\n" +
      "   float coef = ceil(clamp(0.2601 - dot(coord, coord), 0.0, 1.0));\n" +
      "   vec3 color = texture2D(sTexture, vTextureCoord).rgb * coef + (1.0 - step(0.001, coef));\n" +
      "   gl_FragColor = vec4(color * alpha, alpha);\n" +
      "}\n";

  private static final String FRAGMENT_SCREEN_SHADER =
    "#extension GL_OES_EGL_image_external : require\n" +
      "precision lowp float;\n" +
      "varying vec2 vTextureCoord;\n" +
      "uniform samplerExternalOES sTexture;\n" +
      "void main() {\n" +
      "   gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
      "}\n";

  private int[] position = new int[2];
  private int[] cameraTexture = new int[1];
  private int[] oldCameraTexture = new int[1];
  private float cameraTextureAlpha = 1.0f;

  private float scaleX, scaleY;
  private boolean initied;

  private void prepareGL () {
    updateSizes();
    initied = initGL();
  }

  private void updateSizes () {
    int width, height;

    width = mPreviewWidth;
    height = mPreviewHeight;

    int minSide = Math.min(width, height);
    float scale = mSurfaceWidth / (float) minSide;

    width *= scale;
    height *= scale;
    if (width > height) {
      scaleX = 1.0f;
      scaleY = (float) width / (float) mSurfaceHeight;
    } else {
      scaleX = (float) height / (float) mSurfaceWidth;
      scaleY = 1.0f;
    }
    if (!U.isRotated(mOrientation)) {
      float temp = scaleX;
      scaleX = scaleY;
      scaleY = temp;
    }
    rotationAngle = mOrientation;
    surfaceTexture = mSurfaceTexture;

    android.opengl.Matrix.setIdentityM(mMVPMatrix, 0);
    if (rotationAngle != 0) {
      android.opengl.Matrix.rotateM(mMVPMatrix, 0, rotationAngle, 0, 0, 1);
    }
  }

  private static int loadShader (int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
    if (compileStatus[0] == 0) {
      Log.e(Log.TAG_ROUND, GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }
    return shader;
  }

  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  private static final int EGL_OPENGL_ES2_BIT = 4;
  private SurfaceTexture surfaceTexture;
  private EGL10 egl10;
  private EGLDisplay eglDisplay;
  private EGLConfig eglConfig;
  private EGLContext eglContext;
  private EGLSurface eglSurface;
  private GL gl;

  private SurfaceTexture cameraSurface;

  private int drawProgram;
  private int vertexMatrixHandle;
  private int textureMatrixHandle;
  private int positionHandle;
  private int textureHandle;

  private int rotationAngle;

  private Integer cameraId = 0;

  private VideoRecorder videoEncoder;

  private float[] mMVPMatrix = new float[16];
  private float[] mSTMatrix = new float[16];
  private float[] moldSTMatrix = new float[16];

  private boolean initGL () {
    Log.i(Log.TAG_ROUND, "start init gl");
    egl10 = (EGL10) EGLContext.getEGL();

    eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
      Log.e(Log.TAG_ROUND, "eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] version = new int[2];
    if (!egl10.eglInitialize(eglDisplay, version)) {
      Log.e(Log.TAG_ROUND, "eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] configsCount = new int[1];
    EGLConfig[] configs = new EGLConfig[1];
    int[] configSpec = new int[] {
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_RED_SIZE, 8,
      EGL10.EGL_GREEN_SIZE, 8,
      EGL10.EGL_BLUE_SIZE, 8,
      EGL10.EGL_ALPHA_SIZE, 0,
      EGL10.EGL_DEPTH_SIZE, 0,
      EGL10.EGL_STENCIL_SIZE, 0,
      EGL10.EGL_NONE
    };
    if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
      Log.e(Log.TAG_ROUND, "eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    } else if (configsCount[0] > 0) {
      eglConfig = configs[0];
    } else {
      Log.e(Log.TAG_ROUND, "eglConfig not initialized");
      finish();
      return false;
    }

    int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
    eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    if (eglContext == null) {
      Log.e(Log.TAG_ROUND, "eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    if (surfaceTexture instanceof SurfaceTexture) {
      eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
    } else {
      finish();
      return false;
    }

    if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
      Log.e(Log.TAG_ROUND, "createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      Log.e(Log.TAG_ROUND, "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    gl = eglContext.getGL();

    float tX = 1.0f / scaleX / 2.0f;
    float tY = 1.0f / scaleY / 2.0f;
    float[] verticesData = {
      -1.0f, -1.0f, 0,
      1.0f, -1.0f, 0,
      -1.0f, 1.0f, 0,
      1.0f, 1.0f, 0
    };
    float[] texData = {
      0.5f - tX, 0.5f - tY,
      0.5f + tX, 0.5f - tY,
      0.5f - tX, 0.5f + tY,
      0.5f + tX, 0.5f + tY
    };

    videoEncoder = new VideoRecorder();

    vertexBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    vertexBuffer.put(verticesData).position(0);

    textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    textureBuffer.put(texData).position(0);

    android.opengl.Matrix.setIdentityM(mSTMatrix, 0);

    int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
    int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SCREEN_SHADER);
    if (vertexShader != 0 && fragmentShader != 0) {
      drawProgram = GLES20.glCreateProgram();
      GLES20.glAttachShader(drawProgram, vertexShader);
      GLES20.glAttachShader(drawProgram, fragmentShader);
      GLES20.glLinkProgram(drawProgram);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] == 0) {
        Log.e(Log.TAG_ROUND, "failed link shader");
        GLES20.glDeleteProgram(drawProgram);
        drawProgram = 0;
      } else {
        positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
        textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
        vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
        textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
      }
    } else {
      Log.e(Log.TAG_ROUND, "failed creating shader");
      finish();
      return false;
    }

    GLES20.glGenTextures(1, cameraTexture, 0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0]);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

    android.opengl.Matrix.setIdentityM(mMVPMatrix, 0);

    cameraSurface = new SurfaceTexture(cameraTexture[0]);
    cameraSurface.setOnFrameAvailableListener(surfaceTexture -> requestRender());
    createCamera(cameraSurface);
    Log.i(Log.TAG_ROUND, "gl initied");

    return true;
  }

  public void finish() {
    if (eglSurface != null) {
      egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
      egl10.eglDestroySurface(eglDisplay, eglSurface);
      eglSurface = null;
    }
    if (eglContext != null) {
      egl10.eglDestroyContext(eglDisplay, eglContext);
      eglContext = null;
    }
    if (eglDisplay != null) {
      egl10.eglTerminate(eglDisplay);
      eglDisplay = null;
    }
  }

  private void doSwitchToNewCamera () {
    context.openCameraIfStillNeeded(this);
  }

  private void doRender (Integer cameraId) {
    onDraw(cameraId);
  }

  private void createCamera(final SurfaceTexture surfaceTexture) {
    if (!checkCameraThread()) {
      cameraHandler.sendMessage(Message.obtain(cameraHandler, CAM_ACTION_CREATE, surfaceTexture));
      return;
    }

    surfaceTexture.setDefaultBufferSize(mPreviewWidth, mPreviewHeight);
    mCameraSurface = surfaceTexture;
    context.openCameraIfStillNeeded(this);

   /* AndroidUtilities.runOnUIThread(new Runnable() {
      @Override
      public void run() {
        if (cameraThread == null) {
          return;
        }
        FileLog.i("create camera session");



        cameraSession = new CameraSession(selectedCamera, previewSize, pictureSize, ImageFormat.JPEG);
        cameraThread.setCurrentSession(cameraSession);
        CameraController.getInstance().openRound(cameraSession, surfaceTexture, new Runnable() {
          @Override
          public void run() {
            if (cameraSession != null) {
              FileLog.i("camera initied");
              cameraSession.setInitied();
            }
          }
        }, new Runnable() {
          @Override
          public void run() {
            cameraThread.setCurrentSession(cameraSession);
          }
        });
      }
    });*/
  }

  private void doReinitToNextCamera () {
    if (!initied || eglSurface == null) {
      return;
    }

    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      Log.e(Log.TAG_ROUND, "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
      return;
    }

    if (cameraSurface != null) {
      cameraSurface.getTransformMatrix(moldSTMatrix);
      cameraSurface.setOnFrameAvailableListener(null);
      cameraSurface.release();
      oldCameraTexture[0] = cameraTexture[0];
      cameraTextureAlpha = 0.0f;
      cameraTexture[0] = 0;
    }
    cameraId++;
    cameraReady = false;

    GLES20.glGenTextures(1, cameraTexture, 0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0]);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

    cameraSurface = new SurfaceTexture(cameraTexture[0]);
    cameraSurface.setOnFrameAvailableListener(surfaceTexture -> requestRender());
    createCamera(cameraSurface);
  }

  private void doDestroy () {
    finish();
    if (recording) {
      videoEncoder.stopRecording(finishCapture ? 1 : 0);
    }
  }

  private boolean doCapture () {
    return isCapturing || finishCapture || recording;
  }

  private void onDraw(Integer cameraId) {
    if (!initied || eglSurface == null) {
      return;
    }

    if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
      if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        Log.e(Log.TAG_ROUND, "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
        return;
      }
    }
    cameraSurface.updateTexImage();

    if (doCapture()) {
      if (!recording) {
        int resolution;
        int bitrate;
        if (Settings.instance().needHqRoundVideos()) {
          resolution = 320;
          bitrate = 600000;
        } else {
          resolution = 240;
          bitrate = 400000;
        }
        videoEncoder.startRecording(cameraFile, resolution, bitrate, EGL14.eglGetCurrentContext());
        recordStartTime = SystemClock.uptimeMillis();
        recording = true;
        int orientation = rotationAngle;
        if (orientation == 90 || orientation == 270) {
          float temp = scaleX;
          scaleX = scaleY;
          scaleY = temp;
        }
        dispatchVideoRecordStarted(workingKey, recordStartTime);
      }

      videoEncoder.frameAvailable(cameraSurface, cameraId);
    }

    cameraSurface.getTransformMatrix(mSTMatrix);

    GLES20.glUseProgram(drawProgram);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0]);

    GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
    GLES20.glEnableVertexAttribArray(positionHandle);

    GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
    GLES20.glEnableVertexAttribArray(textureHandle);

    GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, mSTMatrix, 0);
    GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, mMVPMatrix, 0);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GLES20.glDisableVertexAttribArray(positionHandle);
    GLES20.glDisableVertexAttribArray(textureHandle);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    GLES20.glUseProgram(0);

    egl10.eglSwapBuffers(eglDisplay, eglSurface);
  }

  private long recordStartTime;
  private boolean recording;

  private void doStartRecord () {
    if (!checkCameraThread()) {
      cameraHandler.sendMessage(Message.obtain(cameraHandler, CAM_ACTION_START));
      return;
    }
    // Nothing?
  }

  private static final int MSG_START_RECORDING = 0;
  private static final int MSG_STOP_RECORDING = 1;
  private static final int MSG_VIDEOFRAME_AVAILABLE = 2;
  private static final int MSG_AUDIOFRAME_AVAILABLE = 3;

  private static class EncoderHandler extends Handler {
    private WeakReference<VideoRecorder> mWeakEncoder;

    public EncoderHandler(VideoRecorder encoder) {
      mWeakEncoder = new WeakReference<>(encoder);
    }

    @Override
    public void handleMessage(Message inputMessage) {
      int what = inputMessage.what;
      Object obj = inputMessage.obj;

      VideoRecorder encoder = mWeakEncoder.get();
      if (encoder == null) {
        return;
      }

      switch (what) {
        case MSG_START_RECORDING: {
          try {
            Log.i(Log.TAG_ROUND, "start encoder");
            encoder.prepareEncoder();
          } catch (Exception e) {
            Log.e(Log.TAG_ROUND, "Error", e);
            encoder.handleStopRecording(0);
            Looper.myLooper().quit();
          }
          break;
        }
        case MSG_STOP_RECORDING: {
          Log.i(Log.TAG_ROUND, "stop encoder");
          encoder.handleStopRecording(inputMessage.arg1);
          break;
        }
        case MSG_VIDEOFRAME_AVAILABLE: {
          long timestamp = (((long) inputMessage.arg1) << 32) | (((long) inputMessage.arg2) & 0xffffffffL);
          Integer cameraId = (Integer) inputMessage.obj;
          encoder.handleVideoFrameAvailable(timestamp, cameraId);
          break;
        }
        case MSG_AUDIOFRAME_AVAILABLE: {
          encoder.handleAudioFrameAvailable((AudioBufferInfo) inputMessage.obj);
          break;
        }
      }
    }

    public void exit() {
      Looper.myLooper().quit();
    }
  }

  private static class AudioBufferInfo {
    byte[] buffer = new byte[2048 * 10];
    long[] offset = new long[10];
    int[] read = new int[10];
    int results;
    int lastWroteBuffer;
    boolean last;
  }

  private class VideoRecorder implements Runnable {

    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 1;

    private File videoFile;
    private int videoWidth;
    private int videoHeight;
    private int videoBitrate;
    private boolean videoConvertFirstWrite = true;
    private boolean blendEnabled;

    private Surface surface;
    private android.opengl.EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLContext sharedEglContext;
    private android.opengl.EGLConfig eglConfig;
    private android.opengl.EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;

    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;
    private Mp4Output mediaMuxer;
    private ArrayList<AudioBufferInfo> buffersToWrite = new ArrayList<>();
    private int videoTrackIndex = -5;
    private int audioTrackIndex = -5;

    private long lastCommitedFrameTime;
    private long audioStartTime = -1;

    private long currentTimestamp = 0;
    private long lastTimestamp = -1;

    private volatile EncoderHandler handler;

    private final Object sync = new Object();
    private boolean ready;
    private volatile boolean running;
    private volatile int sendWhenDone;
    private long skippedTime;
    private boolean skippedFirst;

    private int drawProgram;
    private int vertexMatrixHandle;
    private int textureMatrixHandle;
    private int positionHandle;
    private int textureHandle;
    private int scaleXHandle;
    private int scaleYHandle;
    private int alphaHandle;

    private Integer lastCameraId = 0;

    private AudioRecord audioRecorder;

    private ArrayBlockingQueue<AudioBufferInfo> buffers = new ArrayBlockingQueue<>(10);

    private Runnable recorderRunnable = new Runnable() {

      @Override
      public void run() {
        long audioPresentationTimeNs;
        int readResult;
        boolean done = false;
        while (!done) {
          if (!running && audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
            try {
              audioRecorder.stop();
            } catch (Exception e) {
              done = true;
            }
            if (sendWhenDone == 0) {
              break;
            }
          }
          AudioBufferInfo buffer;
          if (buffers.isEmpty()) {
            buffer = new AudioBufferInfo();
          } else {
            buffer = buffers.poll();
          }
          buffer.lastWroteBuffer = 0;
          buffer.results = 10;
          for (int a = 0; a < 10; a++) {
            audioPresentationTimeNs = System.nanoTime();
            readResult = audioRecorder.read(buffer.buffer, a * 2048, 2048);
            if (readResult <= 0) {
              buffer.results = a;
              if (!running) {
                buffer.last = true;
              }
              break;
            }
            buffer.offset[a] = audioPresentationTimeNs;
            buffer.read[a] = readResult;
          }
          if (buffer.results >= 0 || buffer.last) {
            if (!running && buffer.results < 10) {
              done = true;
            }
            handler.sendMessage(handler.obtainMessage(MSG_AUDIOFRAME_AVAILABLE, buffer));
          } else {
            if (!running) {
              done = true;
            } else {
              try {
                buffers.put(buffer);
              } catch (Exception ignore) {

              }
            }
          }
        }
        try {
          audioRecorder.release();
        } catch (Exception e) {
          Log.e(Log.TAG_ROUND, "Error", e);
        }
        handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING, sendWhenDone, 0));
      }
    };

    public void startRecording(File outputFile, int size, int bitRate, android.opengl.EGLContext sharedContext) {
      videoFile = outputFile;
      videoWidth = size;
      videoHeight = size;
      videoBitrate = bitRate;
      sharedEglContext = sharedContext;

      synchronized (sync) {
        if (running) {
          return;
        }
        running = true;
        new Thread(this, "TextureMovieEncoder").start();
        while (!ready) {
          try {
            sync.wait();
          } catch (InterruptedException ie) {
            // ignore
          }
        }
      }
      handler.sendMessage(handler.obtainMessage(MSG_START_RECORDING));
    }

    public void stopRecording(int send) {
      handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING, send, 0));
    }

    public void frameAvailable(SurfaceTexture st, Integer cameraId) {
      synchronized (sync) {
        if (!ready) {
          return;
        }
      }

      long timestamp = st.getTimestamp();
      if (timestamp == 0) {
        return;
      }

      handler.sendMessage(handler.obtainMessage(MSG_VIDEOFRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, cameraId));
    }

    @Override
    public void run() {
      Looper.prepare();
      synchronized (sync) {
        handler = new EncoderHandler(this);
        ready = true;
        sync.notify();
      }
      Looper.loop();

      synchronized (sync) {
        ready = false;
      }
    }

    private void handleAudioFrameAvailable(AudioBufferInfo input) {
      if (audioStartTime == -1) {
        audioStartTime = input.offset[0];
      }
      buffersToWrite.add(input);
      if (buffersToWrite.size() > 1) {
        input = buffersToWrite.get(0);
      }
      try {
        drainEncoder(false);
      } catch (Exception e) {
        Log.e(Log.TAG_ROUND, "Error", e);
      }
      try {
        boolean isLast = false;
        while (input != null) {
          int inputBufferIndex = audioEncoder.dequeueInputBuffer(0);
          if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= 21) {
              inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
            } else {
              ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
              inputBuffer = inputBuffers[inputBufferIndex];
              inputBuffer.clear();
            }
            long startWriteTime = input.offset[input.lastWroteBuffer];
            for (int a = input.lastWroteBuffer; a <= input.results; a++) {
              if (a < input.results) {
                if (inputBuffer.remaining() < input.read[a]) {
                  input.lastWroteBuffer = a;
                  input = null;
                  break;
                }
                inputBuffer.put(input.buffer, a * 2048, input.read[a]);
              }
              if (a >= input.results - 1) {
                buffersToWrite.remove(input);
                if (running) {
                  buffers.put(input);
                }
                if (!buffersToWrite.isEmpty()) {
                  input = buffersToWrite.get(0);
                } else {
                  isLast = input.last;
                  input = null;
                  break;
                }
              }
            }
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, inputBuffer.position(), startWriteTime == 0 ? 0 : (startWriteTime - audioStartTime) / 1000, isLast ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
          }
        }
      } catch (Throwable e) {
        Log.e(Log.TAG_ROUND, "Error", e);
      }
    }

    private void handleVideoFrameAvailable(long timestampNanos, Integer cameraId) {
      try {
        drainEncoder(false);
      } catch (Exception e) {
        Log.e(Log.TAG_ROUND, "Error", e);
      }
      long dt, alphaDt;
      if (!lastCameraId.equals(cameraId)) {
        lastTimestamp = -1;
        lastCameraId = cameraId;
      }
      if (lastTimestamp == -1) {
        lastTimestamp = timestampNanos;
        if (currentTimestamp != 0) {
          dt = (SystemClock.uptimeMillis() - lastCommitedFrameTime) * 1000000;
          alphaDt = 0;
        } else {
          alphaDt = dt = 0;
        }
      } else {
        alphaDt = dt = (timestampNanos - lastTimestamp);
        lastTimestamp = timestampNanos;
      }
      lastCommitedFrameTime = SystemClock.uptimeMillis();
      if (!skippedFirst) {
        skippedTime += dt;
        if (skippedTime < 200000000) {
          return;
        }
        skippedFirst = true;
      }
      currentTimestamp += dt;

      GLES20.glUseProgram(drawProgram);
      GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
      GLES20.glEnableVertexAttribArray(positionHandle);
      GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
      GLES20.glEnableVertexAttribArray(textureHandle);
      GLES20.glUniform1f(scaleXHandle, scaleX);
      GLES20.glUniform1f(scaleYHandle, scaleY);
      GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, mMVPMatrix, 0);

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      if (oldCameraTexture[0] != 0) {
        if (!blendEnabled) {
          GLES20.glEnable(GLES20.GL_BLEND);
          blendEnabled = true;
        }
        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, moldSTMatrix, 0);
        GLES20.glUniform1f(alphaHandle, 1.0f);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oldCameraTexture[0]);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      }
      GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, mSTMatrix, 0);
      GLES20.glUniform1f(alphaHandle, cameraTextureAlpha);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0]);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

      GLES20.glDisableVertexAttribArray(positionHandle);
      GLES20.glDisableVertexAttribArray(textureHandle);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
      GLES20.glUseProgram(0);

      // Log.i(Log.TAG_ROUND, "frame time = " + currentTimestamp);
      EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, currentTimestamp);
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);

      if (oldCameraTexture[0] != 0 && cameraTextureAlpha < 1.0f) {
        cameraTextureAlpha += alphaDt / 200000000.0f;
        if (cameraTextureAlpha > 1) {
          GLES20.glDisable(GLES20.GL_BLEND);
          blendEnabled = false;
          cameraTextureAlpha = 1;
          isSwitchingToNewCamera = false;
          GLES20.glDeleteTextures(1, oldCameraTexture, 0);
          oldCameraTexture[0] = 0;
          if (!cameraReady) {
            cameraReady = true;
          }
        }
      } else if (!cameraReady) {
        cameraReady = true;
      }
    }

    private void handleStopRecording(final int send) {
      if (running) {
        sendWhenDone = send;
        running = false;
        return;
      }
      try {
        drainEncoder(true);
      } catch (Exception e) {
        Log.e(Log.TAG_ROUND, "Error", e);
      }
      if (videoEncoder != null) {
        try {
          videoEncoder.stop();
          videoEncoder.release();
          videoEncoder = null;
        } catch (Exception e) {
          Log.e(Log.TAG_ROUND, "Error", e);
        }
      }
      if (audioEncoder != null) {
        try {
          audioEncoder.stop();
          audioEncoder.release();
          audioEncoder = null;
        } catch (Exception e) {
          Log.e(Log.TAG_ROUND, "Error", e);
        }
      }
      if (mediaMuxer != null) {
        try {
          mediaMuxer.finishMovie();
        } catch (Exception e) {
          Log.e(Log.TAG_ROUND, "Error", e);
        }
      }
      if (send != 0) {
        UI.post(() -> didWriteData(videoFile, true));
      } else {
        // FileLoader.getInstance().cancelUploadFile(videoFile.getAbsolutePath(), false);
        videoFile.delete();
        dispatchVideoRecordFinished(workingKey, -1, -1, null);
      }
      EGL14.eglDestroySurface(eglDisplay, eglSurface);
      eglSurface = EGL14.EGL_NO_SURFACE;
      if (surface != null) {
        surface.release();
        surface = null;
      }
      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(eglDisplay);
      }
      eglDisplay = EGL14.EGL_NO_DISPLAY;
      eglContext = EGL14.EGL_NO_CONTEXT;
      eglConfig = null;
      handler.exit();
    }

    private void prepareEncoder() {
      try {
        for (int a = 0; a < 3; a++) {
          buffers.add(new AudioBufferInfo());
        }
        AudioRecord audioRecorderFinal = null;
        int sampleRate = SAMPLE_RATES[0];
        for (int newSampleRate : SAMPLE_RATES) {
          sampleRate = newSampleRate;
          int recordBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
          if (recordBufferSize <= 0) {
            recordBufferSize = 3584;
          }
          int bufferSize = 2048 * 24;
          if (bufferSize < recordBufferSize) {
            bufferSize = ((recordBufferSize / 2048) + 1) * 2048 * 2;
          }
          try {
            audioRecorderFinal = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecorderFinal.startRecording();
            audioRecorder = audioRecorderFinal;
            break;
          } catch (Throwable t) {
            if (audioRecorderFinal != null) {
              try { audioRecorderFinal.stop(); } catch (Throwable ignored) { }
              try { audioRecorderFinal.release(); } catch (Throwable ignored) { }
            }
            if (sampleRate == SAMPLE_RATES[SAMPLE_RATES.length - 1]) {
              throw t;
            }
          }
        }
        if (audioRecorder == null) {
          throw new NullPointerException();
        }
        new Thread(recorderRunnable).start();

        audioBufferInfo = new MediaCodec.BufferInfo();
        videoBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2048 * 10);

        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();

        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);

        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);

        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = videoEncoder.createInputSurface();
        videoEncoder.start();

        Mp4Movie movie = new Mp4Movie();
        movie.setCacheFile(videoFile);
        movie.setRotation(0);
        movie.setSize(videoWidth, videoHeight);
        mediaMuxer = new Mp4OutputImpl().createMovie(movie);

        doStartRecord();
      } catch (Exception ioe) {
        throw new RuntimeException(ioe);
      }

      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        throw new RuntimeException("EGL already set up");
      }

      eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
      if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
        throw new RuntimeException("unable to get EGL14 display");
      }
      int[] version = new int[2];
      if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
        eglDisplay = null;
        throw new RuntimeException("unable to initialize EGL14");
      }

      if (eglContext == EGL14.EGL_NO_CONTEXT) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;

        int[] attribList = {
          EGL14.EGL_RED_SIZE, 8,
          EGL14.EGL_GREEN_SIZE, 8,
          EGL14.EGL_BLUE_SIZE, 8,
          EGL14.EGL_ALPHA_SIZE, 8,
          EGL14.EGL_RENDERABLE_TYPE, renderableType,
          0x3142, 1,
          EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
          throw new RuntimeException("Unable to find a suitable EGLConfig");
        }

        int[] attrib2_list = {
          EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
          EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], sharedEglContext, attrib2_list, 0);
        eglConfig = configs[0];
      }

      int[] values = new int[1];
      EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);

      if (eglSurface != EGL14.EGL_NO_SURFACE) {
        throw new IllegalStateException("surface already created");
      }

      int[] surfaceAttribs = {
        EGL14.EGL_NONE
      };
      eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0);
      if (eglSurface == null) {
        throw new RuntimeException("surface was null");
      }

      if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        Log.e(Log.TAG_ROUND, "eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        throw new RuntimeException("eglMakeCurrent failed");
      }
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

      int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
      int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
      if (vertexShader != 0 && fragmentShader != 0) {
        drawProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(drawProgram, vertexShader);
        GLES20.glAttachShader(drawProgram, fragmentShader);
        GLES20.glLinkProgram(drawProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
          GLES20.glDeleteProgram(drawProgram);
          drawProgram = 0;
        } else {
          positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
          textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
          scaleXHandle = GLES20.glGetUniformLocation(drawProgram, "scaleX");
          scaleYHandle = GLES20.glGetUniformLocation(drawProgram, "scaleY");
          alphaHandle = GLES20.glGetUniformLocation(drawProgram, "alpha");
          vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
          textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
        }
      }
    }

    public Surface getInputSurface() {
      return surface;
    }

    private void didWriteData(File file, boolean last) {
      if (videoConvertFirstWrite) {
        videoConvertFirstWrite = false;
      } else if (last) {
        dispatchVideoRecordFinished(workingKey, file.length(), SystemClock.uptimeMillis() - recordStartTime, TimeUnit.MILLISECONDS);
      } else {
        dispatchVideoRecordProgress(workingKey, file.length());
      }
    }

    private boolean videoFinished;
    private long videoFinishedPresentationTimeUs;

    public void drainEncoder(boolean endOfStream) throws Exception {
      if (endOfStream) {
        videoEncoder.signalEndOfInputStream();
      }

      ByteBuffer[] encoderOutputBuffers = videoEncoder.getOutputBuffers();
      while (true) {
        int encoderStatus = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 10000);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (!endOfStream) {
            break;
          }
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          if (Build.VERSION.SDK_INT < 21) {
            encoderOutputBuffers = videoEncoder.getOutputBuffers();
          }
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat newFormat = videoEncoder.getOutputFormat();
          if (videoTrackIndex == -5) {
            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
          }
        } else if (encoderStatus >= 0) {
          ByteBuffer encodedData;
          if (Build.VERSION.SDK_INT < 21) {
            encodedData = encoderOutputBuffers[encoderStatus];
          } else {
            encodedData = videoEncoder.getOutputBuffer(encoderStatus);
          }
          if (encodedData == null) {
            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
          }
          if (videoBufferInfo.size > 1) {
            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
              if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo, true)) {
                didWriteData(videoFile, false);
              }
            } else if (videoTrackIndex == -5) {
              byte[] csd = new byte[videoBufferInfo.size];
              encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size);
              encodedData.position(videoBufferInfo.offset);
              encodedData.get(csd);
              ByteBuffer sps = null;
              ByteBuffer pps = null;
              for (int a = videoBufferInfo.size - 1; a >= 0; a--) {
                if (a > 3) {
                  if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                    sps = ByteBuffer.allocate(a - 3);
                    pps = ByteBuffer.allocate(videoBufferInfo.size - (a - 3));
                    sps.put(csd, 0, a - 3).position(0);
                    pps.put(csd, a - 3, videoBufferInfo.size - (a - 3)).position(0);
                    break;
                  }
                } else {
                  break;
                }
              }

              MediaFormat newFormat = MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight);
              if (sps != null && pps != null) {
                newFormat.setByteBuffer("csd-0", sps);
                newFormat.setByteBuffer("csd-1", pps);
              }
              videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
            }
          }
          videoEncoder.releaseOutputBuffer(encoderStatus, false);
          if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            videoFinished = true;
            videoFinishedPresentationTimeUs = videoBufferInfo.presentationTimeUs;
            break;
          }
        }
      }

      encoderOutputBuffers = audioEncoder.getOutputBuffers();
      boolean encoderOutputAvailable = true;
      while (true) {
        int encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (!endOfStream || !running && sendWhenDone == 0) {
            break;
          }
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
          if (Build.VERSION.SDK_INT < 21) {
            encoderOutputBuffers = audioEncoder.getOutputBuffers();
          }
        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat newFormat = audioEncoder.getOutputFormat();
          if (audioTrackIndex == -5) {
            audioTrackIndex = mediaMuxer.addTrack(newFormat, true);
          }
        } else if (encoderStatus >= 0) {
          ByteBuffer encodedData;
          if (Build.VERSION.SDK_INT < 21) {
            encodedData = encoderOutputBuffers[encoderStatus];
          } else {
            encodedData = audioEncoder.getOutputBuffer(encoderStatus);
          }
          if (encodedData == null) {
            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
          }
          if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            audioBufferInfo.size = 0;
          }
          if (audioBufferInfo.size != 0 && isCapturing) {
            if (mediaMuxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo, false)) {
              didWriteData(videoFile, false);
            }
          }
          audioEncoder.releaseOutputBuffer(encoderStatus, false);
          if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            break;
          }
        }
      }
    }

    @Override
    protected void finalize() throws Throwable {
      try {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
          EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
          EGL14.eglDestroyContext(eglDisplay, eglContext);
          EGL14.eglReleaseThread();
          EGL14.eglTerminate(eglDisplay);
          eglDisplay = EGL14.EGL_NO_DISPLAY;
          eglContext = EGL14.EGL_NO_CONTEXT;
          eglConfig = null;
        }
      } finally {
        super.finalize();
      }
    }
  }

  private static final int[] SAMPLE_RATES = {44100, 22050, 11025, 8000};
}
