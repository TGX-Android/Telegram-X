package org.thunderdog.challegram.texture;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Message;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.core.BaseThread;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;

/**
 * Date: 20/12/2016
 * Author: default
 */

@SuppressWarnings("NewApi")
public class TextureViewQueue extends BaseThread implements android.view.TextureView.SurfaceTextureListener {
  private static final int REQUEST_RENDER = 1;
  private static final int TEXTURE_AVAILABLE = 2;
  private static final int TEXTURE_RESIZED = 3;
  private static final int TEXTURE_DESTROYED = 4;
  private static final int ACTIVITY_PAUSED = 5;
  private static final int ACTIVITY_RESUMED = 6;
  private static final int ACTIVITY_DESTROYED = 7;

  public TextureViewQueue (Listener listener) {
    super("TextureViewQueue");
    this.listener = listener;
  }

  public void requestRender () {
    sendMessage(Message.obtain(getHandler(), REQUEST_RENDER), 0);
  }

  @Override
  public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
    sendMessage(Message.obtain(getHandler(), TEXTURE_AVAILABLE, width, height, surface), 0);
  }

  @Override
  public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
    sendMessage(Message.obtain(getHandler(), TEXTURE_RESIZED, width, height), 0);
  }

  @Override
  public boolean onSurfaceTextureDestroyed (SurfaceTexture surface) {
    sendMessage(Message.obtain(getHandler(), TEXTURE_DESTROYED), 0);
    return true;
  }

  public void onPause () {
    sendMessage(Message.obtain(getHandler(), ACTIVITY_PAUSED), 0);
  }

  public void onResume () {
    sendMessage(Message.obtain(getHandler(), ACTIVITY_RESUMED), 0);
  }

  public void onDestroy () {
    sendMessage(Message.obtain(getHandler(), ACTIVITY_DESTROYED), 0);
  }

  @Override
  public void onSurfaceTextureUpdated (SurfaceTexture surface) { }

  @Override
  protected void process (Message msg) {
    switch (msg.what) {
      case REQUEST_RENDER: {
        requestRenderInternal();
        break;
      }
      case TEXTURE_AVAILABLE: {
        initSurfaceInternal((SurfaceTexture) msg.obj, msg.arg1, msg.arg2);
        break;
      }
      case TEXTURE_RESIZED: {
        resizeTextureInternal(msg.arg1, msg.arg2);
        break;
      }
      case TEXTURE_DESTROYED: {
        destroyTextureInternal();
        break;
      }
    }
  }

  // Impl

  interface Listener {
    void onSurfaceCreated (int width, int height);
    void onSurfaceSizeChanged (int width, int height);
    void onSurfaceDraw ();
  }

  private final Listener listener;

  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  private static final int EGL_OPENGL_ES2_BIT = 4;

  private boolean inited;
  private EGL10 egl10;
  private EGLDisplay eglDisplay;
  private EGLConfig eglConfig;
  private EGLContext eglContext;
  private EGLSurface eglSurface;
  private SurfaceTexture surfaceTexture;
  private int surfaceWidth, surfaceHeight;
  private GL gl;

  private void finish () {
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
    inited = false;
  }

  private boolean initInternal () {
    egl10 = (EGL10) EGLContext.getEGL();
    eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
      Log.e("eglGetDisplay failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] version = new int[2];
    if (!egl10.eglInitialize(eglDisplay, version)) {
      Log.e("eglInitialize failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    int[] configsCount = new int[1];
    EGLConfig[] configs = new EGLConfig[1];
    int[] configSpec = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_DEPTH_SIZE, 16,
// Requires that setEGLContextClientVersion(2) is called on the view.
      EGL10.EGL_RENDERABLE_TYPE, 4 /* EGL_OPENGL_ES2_BIT */,
      EGL10.EGL_SAMPLE_BUFFERS, 1 /* true */,
      EGL10.EGL_SAMPLES, 2,
      EGL10.EGL_STENCIL_SIZE, 1,
      EGL10.EGL_NONE
    };
    if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
      Log.e("eglChooseConfig failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    } else if (configsCount[0] > 0) {
      eglConfig = configs[0];
    } else {
      Log.e("eglConfig not initialized");
      finish();
      return false;
    }

    int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
    eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    if (eglContext == null) {
      Log.e("eglCreateContext failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }

    if (surfaceTexture != null) {
      eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
    } else {
      finish();
      return false;
    }

    if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
      Log.e("createWindowSurface failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
      Log.e("eglMakeCurrent failed: %s", GLUtils.getEGLErrorString(egl10.eglGetError()));
      finish();
      return false;
    }
    gl = eglContext.getGL();

    return true;
  }

  private void requestRenderInternal () {
    if (inited) {
      GLES20.glClearColor(1f, 0f, 0f, 1f);
      listener.onSurfaceDraw();
    }
  }

  private void initSurfaceInternal (SurfaceTexture texture, int width, int height) {
    this.surfaceTexture = texture;
    this.surfaceWidth = width;
    this.surfaceHeight = height;
    this.inited = initInternal();
    if (inited) {
      listener.onSurfaceCreated(width, height);
    }
  }

  private void resizeTextureInternal (int width, int height) {
    if (inited) {
      listener.onSurfaceSizeChanged(width, height);
    }
  }

  private void destroyTextureInternal () {
    if (inited) {
      finish();
    }
  }
}
