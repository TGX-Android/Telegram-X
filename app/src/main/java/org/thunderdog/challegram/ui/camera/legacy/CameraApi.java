/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/09/2017
 */
package org.thunderdog.challegram.ui.camera.legacy;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import androidx.annotation.WorkerThread;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.player.RoundVideoRecorder;
import org.thunderdog.challegram.ui.camera.CameraFeatures;
import org.thunderdog.challegram.ui.camera.CameraManager;

import me.vkryl.core.lambda.RunnableData;

public abstract class CameraApi {

  protected final Context context;
  protected final CameraManagerLegacy manager;
  protected final CameraThread cameraThread;
  protected final BaseThread backgroundThread;

  protected int mPreviewWidth, mPreviewHeight;
  protected int mDisplayOrientation;

  protected int mSurfaceWidth, mSurfaceHeight;

  protected CameraFeatures mFeatures;
  protected int mFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
  protected float mZoom;

  protected boolean isCameraActive;

  private boolean cameraPreviewRequested;
  protected SurfaceTexture mSurface;

  protected int mForcedOutputOrientation = -1;

  protected int mNumberOfCameras = -1;
  private int mRequestedCameraIndex;

  public CameraApi (Context context, CameraManagerLegacy manager) {
    this.context = context;
    this.manager = manager;
    this.cameraThread = new CameraThread(this, false);
    this.backgroundThread = new CameraThread(this, true);
  }

  public final Handler getCameraHandler () {
    return cameraThread.getHandler();
  }

  public final Handler getBackgroundHandler () {
    return backgroundThread.getHandler();
  }

  /**
   * Preview size changed
   *
   * TODO: As an optimization, it's possible to call {@code manager.setAspectRatio(width, height)} right in this method.
   * */
  public final void setPreviewSize (int viewWidth, int viewHeight) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_SET_PREVIEW_SIZE, viewWidth, viewHeight);
      return;
    }

    if (mPreviewWidth != viewWidth || mPreviewHeight != viewHeight) {
      mPreviewWidth = viewWidth;
      mPreviewHeight = viewHeight;
      onPreviewSizeChanged(viewWidth, viewHeight);
      if (isCameraActive && !isCameraBusy()) {
        setCameraActive(false);
        setCameraActive(true);
      }
    }
  }
  protected abstract void onPreviewSizeChanged (int newWidth, int newHeight);

  private boolean isCameraBusy () {
    return isVideoCapturing || (roundRecorder != null && roundRecorder.isCapturing());
  }

  /**
   * Open camera preview
   */
  public final boolean openPreview () {
    setCameraPreviewRequested(true);
    return true;
  }

  /**
   * Close camera preview
   */
  public final boolean closePreview () {
    setCameraPreviewRequested(false);
    return true;
  }

  private void setCameraPreviewRequested (boolean isRequested) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_SET_PREVIEW_REQUESTED, isRequested ? 1 : 0, 0);
      return;
    }

    if (this.cameraPreviewRequested != isRequested) {
      this.cameraPreviewRequested = isRequested;
      checkCameraActive();
    }
  }

  private void checkCameraActive () {
    if (!checkCameraThread()) {
      sendMessage(ACTION_CHECK_CAMERA_ACTIVE);
      return;
    }
    setCameraActive(cameraPreviewRequested && mSurface != null);
  }

  /**
   * Switch to next camera, if possible
   * */
  public final void switchToNextCameraDevice () {
    if (!checkCameraThread()) {
      if (!hasMessages(ACTION_SWITCH_TO_NEXT_CAMERA)) {
        sendMessage(ACTION_SWITCH_TO_NEXT_CAMERA);
      }
      return;
    }
    if (isCameraActive && cameraOpened) {
      if (roundRecorder != null && !roundRecorder.canSwitchToNewCamera()) {
        return;
      }
      onNextCameraSourceRequested();
    }
  }
  protected abstract void onNextCameraSourceRequested ();

  protected final void setCameraDeviceFeatures (CameraFeatures features) {
    synchronized (manager) {
      if (this.mFeatures != features) {
        this.mFeatures = features;
        // TODO ?
      }
    }
  }

  /**
   * Toggle flash mode, if possible.
   *
   * Possible values:
   * {@link CameraFeatures#FEATURE_FLASH_OFF}
   * {@link CameraFeatures#FEATURE_FLASH_ON}
   * {@link CameraFeatures#FEATURE_FLASH_AUTO}
   */
  public final void toggleFlashMode () {
    if (!checkCameraThread()) {
      if (!hasMessages(ACTION_TOGGLE_FLASH_MODE)) {
        sendMessage(ACTION_TOGGLE_FLASH_MODE);
      }
      return;
    }
    if (isCameraActive && mFeatures != null && mFeatures.canFlash(true)) {
      setFlashMode(getNextFlashMode());
    }
  }
  protected abstract void onFlashModeChange (int newFlashMode) throws Throwable;

  protected final void setRequestedCameraIndex (int index) {
    if (index >= mNumberOfCameras) {
      index = 0;
    }
    if (mRequestedCameraIndex != index) {
      mRequestedCameraIndex = index;
    }
  }

  protected final int getRequestedCameraIndex () {
    if (mRequestedCameraIndex >= mNumberOfCameras || mRequestedCameraIndex < 0)
      mRequestedCameraIndex = 0;
    return mRequestedCameraIndex;
  }

  protected final int getNextCameraIndex () {
    int nextCameraIndex = mRequestedCameraIndex + 1;
    if (nextCameraIndex >= mNumberOfCameras)
      nextCameraIndex = 0;
    return nextCameraIndex;
  }

  /**
   * Change zoom, if possible.
   * */
  public final void requestZoom (float zoom) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_REQUEST_ZOOM, Float.floatToIntBits(zoom), 0);
      return;
    }
    if (isCameraActive && mFeatures.canZoom()) {
      setZoom(zoom);
    }
  }
  protected abstract void onZoomChanged (float zoom);

  public void resetRequestedSettings () {
    if (!checkCameraThread()) {
      if (!hasMessages(ACTION_RESET_REQUESTED_SETTINGS)) {
        sendMessage(ACTION_RESET_REQUESTED_SETTINGS);
      }
      return;
    }
    if (!isCameraActive) {
      setFlashMode(CameraFeatures.FEATURE_FLASH_OFF);
      setZoom(0f);
      setRequestedCameraIndex(0);
      onResetRequestedSettings();
    }
  }
  protected abstract void onResetRequestedSettings ();

  public float getMaxZoom () {
    return mFeatures != null ? mFeatures.getMaxZoom() : 0f;
  }

  private int getNextFlashMode () {
    assertCameraThread();
    if (mFeatures == null) {
      return mFlashMode;
    }
    switch (mFlashMode) {
      case CameraFeatures.FEATURE_FLASH_OFF:
        if (mFeatures.has(CameraFeatures.FEATURE_FLASH_ON)) {
          return CameraFeatures.FEATURE_FLASH_ON;
        } else if (mFeatures.has(CameraFeatures.FEATURE_FACING_FRONT)) {
          return CameraFeatures.FEATURE_FLASH_FAKE;
        }
      case CameraFeatures.FEATURE_FLASH_ON:
      case CameraFeatures.FEATURE_FLASH_FAKE:
        if (Config.CAMERA_AUTO_FLASH_ENABLED && mFeatures.has(CameraFeatures.FEATURE_FLASH_AUTO)) {
          return CameraFeatures.FEATURE_FLASH_AUTO;
        }
      case CameraFeatures.FEATURE_FLASH_AUTO:
        if (mFeatures.canFlash(true)) {
          return CameraFeatures.FEATURE_FLASH_OFF;
        }
    }
    return mFlashMode;
  }

  protected final void resetFlashMode () {
    setFlashMode(CameraFeatures.FEATURE_FLASH_OFF);
  }

  private void setFlashMode (int mode) {
    assertCameraThread();
    if (this.mFlashMode != mode) {
      boolean success = false;
      try {
        if (mFeatures == null || mode == CameraFeatures.FEATURE_FLASH_OFF || mode == CameraFeatures.FEATURE_FLASH_FAKE || mFeatures.canFlash(false)) {
          onFlashModeChange(mode);
        }
        success = true;
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot change flash mode", t);
      }
      if (success) {
        this.mFlashMode = mode;
        manager.onFlashModeChanged(mode);
      }
    }
  }

  protected final void resetZoom () {
    setZoom(0f);
  }

  public final float getZoom () {
    return mZoom;
  }

  private void setZoom (float zoom) {
    assertCameraThread();
    if (this.mZoom != zoom) {
      boolean success = false;
      try {
        if (mFeatures == null || mFeatures.canZoom()) {
          onZoomChanged(zoom);
        }
        success = true;
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot zoom", t);
      }
      if (success) {
        this.mZoom = zoom;
        manager.onZoomChanged(zoom);
      }
    }
  }

  /**
   * Async request to take photo.
   * */
  public final void takePhoto (int trimWidth, int trimHeight, int orientation) {
    if (!checkCameraThread()) {
      if (!hasMessages(ACTION_TAKE_PHOTO)) {
        sendMessage(ACTION_TAKE_PHOTO, trimWidth, trimHeight, orientation);
      }
      return;
    }
    if (isCameraActive) {
      manager.onPerformSuccessHint(false);
      try {
        onTakePhoto(trimWidth, trimHeight, orientation);
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot take photo", t);
        manager.onTakeMediaError(false);
      }
    }
  }
  protected abstract void onTakePhoto (int trimWidth, int trimHeight, int orientation);

  /**
   * @param orientation -1 When output orientation is no longer blocked.
   *                    0, 90, 180, 270 otherwise
   * */
  protected final void forceOutputOrientation (int orientation) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_FORCE_OUTPUT_ORIENTATION, orientation, 0);
      return;
    }
    if (mForcedOutputOrientation != orientation) {
      mForcedOutputOrientation = orientation;
      onForcedOrientationChange();
    }
  }
  protected abstract void onForcedOrientationChange ();

  private int outputVideoOrientation;

  /**
   * Async request to start video capture.
   * */
  public final void startVideoCapture (int outputOrientation) {
    if (!manager.isCapturingVideo() && isCameraActive) {
      outputVideoOrientation = outputOrientation;
      setVideoCapturing(true);
    }
  }

  /**
   * Async request to stop video capture.
   */
  public final void finishOrCancelVideoCapture () {
    if (manager.isCapturingVideo()) {
      setVideoCapturing(false);
    }
  }

  protected long mVideoCaptureStartTime;

  private boolean isVideoCapturing;

  private void setVideoCapturing (boolean isCapturing) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_CHANGE_CAPTURE_STATE, isCapturing ? 1 : 0, 0);
      return;
    }
    if (this.isVideoCapturing != isCapturing) {
      if (isCapturing) {
        boolean done = false;
        try {
          mVideoCaptureStartTime = 0;
          forceOutputOrientation(outputVideoOrientation);
          onStartVideoCapture();
          mVideoCaptureStartTime = SystemClock.uptimeMillis();
          done = true;
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Cannot start video capture", t);
        }
        if (done) {
          this.isVideoCapturing = true;
          manager.setTakingVideo(true, mVideoCaptureStartTime);
          return;
        }
      }
      forceOutputOrientation(-1);
      boolean needFile = mVideoCaptureStartTime != 0 && SystemClock.uptimeMillis() - mVideoCaptureStartTime > CameraManager.MINIMUM_VIDEO_DURATION;
      this.isVideoCapturing = false;
      manager.setTakingVideo(false, -1);
      onFinishVideoCapture(needFile, galleryFile -> {
        if (galleryFile != null)
          manager.onTakeMediaResult(galleryFile, true);
      });
    }
  }

  protected abstract void onStartVideoCapture () throws Throwable;
  protected abstract void onFinishVideoCapture (boolean saveFile, RunnableData<ImageGalleryFile> callback);

  /**
   * Tells whether video recording is implemented in current API instance
   */
  public abstract boolean isVideoRecordSupported ();

  /**
   * Informs about current display rotation
   * @param degrees Rotation degrees, always divisible by 90
   */
  public final void setDisplayOrientation (int degrees) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_SET_DISPLAY_ORIENTATION, degrees, 0);
      return;
    }

    synchronized (this) {
      if (this.mDisplayOrientation != degrees) {
        this.mDisplayOrientation = degrees;
        onDisplayOrientationChanged();
      }
    }
  }

  protected abstract void onDisplayOrientationChanged ();

  protected static int compareOutputSizes (int w1, int h1, int w2, int h2, float desiredRatio, int maxResolution) {
    if (maxResolution <= 0) {
      maxResolution = 1080;
    }

    boolean e1 = Math.max(w1, h1) <= maxResolution;
    boolean e2 = Math.max(w2, h2) <= maxResolution;

    if (e1 != e2) {
      return e1 ? -1 : 1;
    }

    final float r1 = (float) Math.max(w1, h1) / (float) Math.min(w1, h1);
    final float r2 = (float) Math.max(w2, h2) / (float) Math.min(w2, h2);

    final float rd1 = Math.abs(r1 - desiredRatio);
    final float rd2 = Math.abs(r2 - desiredRatio);

    if (rd1 != rd2) {
      return rd1 < rd2 ? -1 : 1;
    }

    final long s1 = (long) w1 * h1;
    final long s2 = (long) w2 * h2;

    if (s1 != s2) {
      return s1 > s2 ? -1 : 1;
    }

    if (w1 != w2) {
      return w1 > w2 ? -1 : 1;
    }

    if (h1 != h2) {
      return h1 > h2 ? -1 : 1;
    }

    return 0;
  }
  
  protected static int comparePreviewSizes (int w1, int h1, int w2, int h2, long desiredSquare, float desiredRatio) {
    final long s1 = (long) w1 * h1;
    final long s2 = (long) w2 * h2;

    final long s1Diff = Math.abs(desiredSquare - s1);
    final long s2Diff = Math.abs(desiredSquare - s2);

    if (s1Diff != s2Diff) {
      return s1Diff < s2Diff ? -1 : 1;
    }

    if (s1 != s2) {
      return s1 > s2 ? -1 : 1;
    }

    final float r1 = (float) Math.max(w1, h1) / (float) Math.min(w1, h1);
    final float r2 = (float) Math.max(w2, h2) / (float) Math.min(w2, h2);

    final float r1Diff = Math.abs(desiredRatio - r1);
    final float r2Diff = Math.abs(desiredRatio - r2);

    if (r1Diff != r2Diff) {
      return r1Diff < r2Diff ? -1 : 1;
    }

    return 0;
  }

  // TextureView

  public void onSurfaceTextureAvailable (SurfaceTexture surface, int width, int height) {
    synchronized (this) {
      this.mSurface = surface;
      this.mSurfaceWidth = width;
      this.mSurfaceHeight = height;
    }
    checkCameraActive();
  }

  public void onSurfaceTextureSizeChanged (SurfaceTexture surface, int width, int height) {
    synchronized (this) {
      this.mSurfaceWidth = width;
      this.mSurfaceHeight = height;
    }
  }

  public void onSurfaceTextureDestroyed (SurfaceTexture surface) {
    synchronized (this) {
      this.mSurface = null;
      this.mSurfaceWidth = this.mSurfaceHeight = 0;
    }
    checkCameraActive();
  }

  // Thread

  public final boolean checkCameraThread () {
    return Thread.currentThread() == cameraThread;
  }

  public final void assertCameraThread () {
    if (Thread.currentThread() != cameraThread) {
      throw new RuntimeException();
    }
  }

  public final boolean checkBackgroundThread () {
    return Thread.currentThread() == backgroundThread;
  }

  public final void assertBackgroundThread () {
    if (Thread.currentThread() != backgroundThread) {
      throw new RuntimeException();
    }
  }

  protected void handleMessage (Message msg) { }
  protected void handleBackgroundMessage (Message msg) { }

  protected final void sendMessage (int what) {
    cameraThread.sendMessage(Message.obtain(cameraThread.getHandler(), what), 0);
  }

  protected final void sendMessage (int what, Object obj) {
    cameraThread.sendMessage(Message.obtain(cameraThread.getHandler(), what, obj), 0);
  }

  protected final void sendMessage (int what, int arg1, int arg2) {
    cameraThread.sendMessage(Message.obtain(cameraThread.getHandler(), what, arg1, arg2), 0);
  }

  protected final void sendMessage (int what, int arg1, int arg2, Object obj) {
    cameraThread.sendMessage(Message.obtain(cameraThread.getHandler(), what, arg1, arg2, obj), 0);
  }

  protected final boolean hasMessages (int what) {
    return cameraThread.getHandler().hasMessages(what);
  }

  public final void post (Runnable runnable, int delay) {
    cameraThread.post(runnable, delay);
  }

  private static class CameraThread extends BaseThread {
    private final CameraApi context;
    private final boolean isBackground;

    public CameraThread (CameraApi context, boolean isBackground) {
      super("CameraThread");
      this.context = context;
      this.isBackground = isBackground;
    }

    @Override
    protected void process (Message msg) {
      if (msg.what >= 0) {
        if (isBackground) {
          context.handleBackgroundMessage(msg);
        } else {
          context.handleMessage(msg);
        }
      } else {
        if (isBackground) {
          context.handleInternalBackgroundMessage(msg);
        } else {
          context.handleInternalMessage(msg);
        }
      }
    }
  }

  // COMMON

  private static final int ACTION_SET_PREVIEW_SIZE = -1;
  private static final int ACTION_SET_DISPLAY_ORIENTATION = -2;
  private static final int ACTION_SWITCH_TO_NEXT_CAMERA = -3;
  private static final int ACTION_TOGGLE_FLASH_MODE = -4;
  private static final int ACTION_CHANGE_CAMERA_STATE = -6;
  private static final int ACTION_SET_PREVIEW_REQUESTED = -7;
  private static final int ACTION_CHECK_CAMERA_ACTIVE = -8;
  private static final int ACTION_RESET_REQUESTED_SETTINGS = -9;
  private static final int ACTION_REQUEST_ZOOM = -10;
  private static final int ACTION_TAKE_PHOTO = -11;
  private static final int ACTION_FORCE_OUTPUT_ORIENTATION = -12;
  private static final int ACTION_CHANGE_CAPTURE_STATE = -13;
  private static final int ACTION_START_ROUND_CAPTURE = -14;
  private static final int ACTION_FINISH_ROUND_CAPTURE = -15;
  private static final int ACTION_OPEN_CAMERA_IF_NEEDED = -16;
  private static final int ACTION_DESTROY_ROUND_RENDERER = -17;

  private void handleInternalMessage (Message msg) {
    switch (msg.what) {
      case ACTION_SET_PREVIEW_SIZE:
        setPreviewSize(msg.arg1, msg.arg2);
        break;
      case ACTION_SET_DISPLAY_ORIENTATION:
        setDisplayOrientation(msg.arg1);
        break;
      case ACTION_SWITCH_TO_NEXT_CAMERA:
        switchToNextCameraDevice();
        break;
      case ACTION_TOGGLE_FLASH_MODE:
        toggleFlashMode();
        break;
      case ACTION_CHANGE_CAMERA_STATE:
        setCameraActive(msg.arg1 == 1);
        break;
      case ACTION_SET_PREVIEW_REQUESTED:
        setCameraPreviewRequested(msg.arg1 == 1);
        break;
      case ACTION_CHECK_CAMERA_ACTIVE:
        checkCameraActive();
        break;
      case ACTION_RESET_REQUESTED_SETTINGS:
        resetRequestedSettings();
        break;
      case ACTION_REQUEST_ZOOM:
        requestZoom(Float.intBitsToFloat(msg.arg1));
        break;
      case ACTION_TAKE_PHOTO:
        takePhoto(msg.arg1, msg.arg2, (Integer) msg.obj);
        break;
      case ACTION_FORCE_OUTPUT_ORIENTATION:
        forceOutputOrientation(msg.arg1);
        break;
      case ACTION_CHANGE_CAPTURE_STATE:
        setVideoCapturing(msg.arg1 == 1);
        break;
      case ACTION_START_ROUND_CAPTURE: {
        Object[] data = (Object[]) msg.obj;
        startRoundVideoCapture((String) data[0], (RoundVideoRecorder.Delegate) data[1], (String) data[2]);
        data[0] = null;
        data[1] = null;
        data[2] = null;
        break;
      }
      case ACTION_FINISH_ROUND_CAPTURE:
        finishOrCancelRoundVideoCapture((String) msg.obj, msg.arg1 == 1);
        break;
      case ACTION_OPEN_CAMERA_IF_NEEDED:
        openCameraIfStillNeeded((RoundVideoRecorder) msg.obj);
        break;
      case ACTION_DESTROY_ROUND_RENDERER:
        destroyRoundRenderer();
        break;
    }
  }

  private void handleInternalBackgroundMessage (Message msg) {

  }

  @WorkerThread
  protected abstract boolean prepareCamera () throws Throwable;

  private boolean cameraOpened;

  @WorkerThread
  protected abstract boolean openCamera (SurfaceTexture texture) throws Throwable;

  public final void openCameraIfStillNeeded (RoundVideoRecorder recorder) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_OPEN_CAMERA_IF_NEEDED, recorder);
      return;
    }
    if (this.roundRecorder == recorder && isCameraActive && !cameraOpened) {
      try {
        openCamera(recorder.getCameraTexture());
        cameraOpened = true;
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot open camera lately");
        setCameraActive(false);
      }
    }
  }

  @WorkerThread
  protected abstract int calculateDisplayOrientation ();
  @WorkerThread
  protected abstract int getCameraOutputWidth ();
  @WorkerThread
  protected abstract int getCameraOutputHeight ();
  @WorkerThread
  protected abstract int getSensorOrientation ();

  @WorkerThread
  protected abstract void closeCamera () throws Throwable;

  @WorkerThread
  protected final void setNumberOfCameras (int num) {
    if (mNumberOfCameras != num) {
      mNumberOfCameras = num;
      manager.setAvailableCameraCount(num);
    }
  }

  public final void destroyRoundRenderer () {
    if (!checkCameraThread()) {
      sendMessage(ACTION_DESTROY_ROUND_RENDERER);
      return;
    }
    if (roundRecorder != null) {
      roundRecorder.destroy();
      roundRecorder = null;
    }
  }

  @WorkerThread
  protected final void setCameraActive (boolean isActive) {
    if (this.isCameraActive != isActive) {
      boolean success = false;
      if (isActive) {
        try {
          success = prepareCamera();
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Cannot prepare camera", t);
        }
        if (success) {
          try {
            SurfaceTexture surface;
            synchronized (manager) {
              surface = mSurface;
            }
            if (surface == null) {
              throw new NullPointerException();
            }
            if (manager.useRoundRender()) {
              if (roundRecorder == null) {
                roundRecorder = new RoundVideoRecorder(this, surface, mSurfaceWidth, mSurfaceHeight);
                roundRecorder.setPreviewSizes(getCameraOutputWidth(), getCameraOutputHeight(), calculateDisplayOrientation());
                roundRecorder.openCamera();
              } else {
                roundRecorder.setPreviewSizes(getCameraOutputWidth(), getCameraOutputHeight(), calculateDisplayOrientation());
                roundRecorder.switchToNewCamera();
              }
            } else {
              success = openCamera(surface);
              cameraOpened = success;
            }
          } catch (Throwable t) {
            Log.w(Log.TAG_CAMERA, "Cannot open camera", t);
            success = false;
          }
        }
      }
      if (!isActive || !success) {
        try { setVideoCapturing(false); } catch (Throwable ignored) { }
        try {
          closeCamera();
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Cannot close camera, but we ignore it.", t);
        }
        if (roundRecorder != null) {
          roundRecorder.onCameraClose();
        }
        cameraOpened = false;
        success = !isActive;
      }
      if (success) {
        this.isCameraActive = isActive;
      } else if (isActive) {
        manager.switchToLegacyApi();
      }
    }
  }

  // Round render

  private RoundVideoRecorder roundRecorder;

  public final void startRoundVideoCapture (String key, RoundVideoRecorder.Delegate delegate, String outputPath) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_START_ROUND_CAPTURE, new Object[] {key, delegate, outputPath});
      return;
    }

    roundRecorder.startCapture(delegate, key, outputPath);
  }

  public final void finishOrCancelRoundVideoCapture (String key, boolean saveResult) {
    if (!checkCameraThread()) {
      sendMessage(ACTION_FINISH_ROUND_CAPTURE, saveResult ? 1 : 0, 0, key);
      return;
    }

    roundRecorder.finishCapture(key, saveResult);
  }
}
