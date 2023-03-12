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
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.player.RoundVideoRecorder;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraDelegate;
import org.thunderdog.challegram.ui.camera.CameraError;
import org.thunderdog.challegram.ui.camera.CameraManagerTexture;
import org.thunderdog.challegram.ui.camera.CameraQrBridge;
import org.thunderdog.challegram.ui.camera.CameraTextureView;

import me.vkryl.core.MathUtils;

public class CameraManagerLegacy extends CameraManagerTexture {

  private CameraApi api;
  private CameraQrBridge cameraQrBridge;

  private boolean useRoundRender;

  private volatile int availableCameraCount = -1;

  public CameraManagerLegacy (Context context, CameraDelegate delegate) {
    super(context, delegate);
    api = new CameraApiLegacy(context, this);
    api.setDisplayOrientation(getDisplayRotation());

    if (delegate.useQrScanner() && cameraQrBridge == null) {
      cameraQrBridge = new CameraQrBridge(this);
    }
  }

  public void onPreviewFrame (byte[] data, Camera camera) {
    if (cameraQrBridge != null && api.isCameraActive) {
      try {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        cameraQrBridge.processImage(data, previewSize.width, previewSize.height, (CameraApiLegacy) api);
      } catch (Exception ignored) {
      }
    }
  }

  @Override
  protected void onDisplayRotationChanged (int rotation) {
    if (api != null)
      api.setDisplayOrientation(rotation);
  }

  @Override
  @UiThread
  public void switchCamera () {
    switchToNextCameraDevice();
  }

  @Override
  protected boolean getBitmap (Bitmap bitmap) {
    try {
      if (cameraView.isAvailable()) {
        cameraView.getBitmap(bitmap);
        return true;
      }
    } catch (Throwable t) {
      Log.e(Log.TAG_CAMERA, "Unable to take camera preview", t);
    }
    return false;
  }

  @Override
  @UiThread
  public void switchFlashMode () {
    api.toggleFlashMode();
  }

  @Override
  @UiThread
  protected void onRequestZoom (float zoom) {
    api.requestZoom(MathUtils.clamp(zoom, getMinZoom(), getMaxZoom()));
  }

  @Override
  public float getCurrentZoom () {
    return api.getZoom();
  }

  @Override
  public float getMinZoomStep () {
    return 1;
  }

  @Override
  @UiThread
  public void onResetPreferences () {
    api.resetRequestedSettings();
  }

  // API

  public boolean useRoundRender () {
    synchronized (this) {
      return useRoundRender;
    }
  }

  public void setUseRoundRender (boolean useRoundRender) {
    synchronized (this) {
      if (this.useRoundRender != useRoundRender) {
        this.useRoundRender = useRoundRender;
        if (!useRoundRender) {
          api.destroyRoundRenderer();
        }
      }
    }
  }

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int outRotation) {
    api.takePhoto(trimWidth, trimHeight, outRotation);
  }

  @Override
  public boolean onStartVideoCapture (int orientation) {
    Log.i(Log.TAG_CAMERA, "requestVideoCapture, orientation: %d", orientation);
    if (api.isVideoRecordSupported()) {
      api.startVideoCapture(orientation);
      return true;
    }
    return false;
  }

  @Override
  protected void onFinishOrCancelVideoCapture () {
    api.finishOrCancelVideoCapture();
  }

  /**
   * Async request to start round video capturin.
   * */
  public boolean requestRoundVideoCapture (String key, RoundVideoRecorder.Delegate delegate, String outputPath) {
    Log.i(Log.TAG_CAMERA, "requestRoundCapture, key: %s", key);
    if (api.isVideoRecordSupported()) {
      api.startRoundVideoCapture(key, delegate, outputPath);
      return true;
    }
    return false;
  }

  /**
   * Async request to stop round video capture.
   *
   * Calling this method after {@link #requestRoundVideoCapture} returned {@code false}
   * is undefined behavior
   * */
  public void finishOrCancelRoundVideoCapture (String key, boolean saveResult) {
    api.finishOrCancelRoundVideoCapture(key, saveResult);
  }

  @UiThread
  public void switchToNextCameraDevice () {
    if (availableCameraCount != -1) {
      api.switchToNextCameraDevice();
    }
  }

  private boolean isCameraOpen, isCameraPaused;
  private CameraApi destroyedCamera;

  public void openCamera () {
    isCameraOpen = true;
    checkCameraState();
  }

  public void closeCamera () {
    isCameraOpen = false;
    checkCameraState();
  }

  public void pauseCamera () {
    isCameraPaused = true;
    checkCameraState();
  }

  public void resumeCamera () {
    isCameraPaused = false;
    checkCameraState();
  }

  public void destroy () {
    if (cameraQrBridge != null) {
      cameraQrBridge.destroy();
      cameraQrBridge = null;
    }

    destroyedCamera = api;
    checkCameraState();
  }

  // Internal

  private volatile boolean __isCameraActive;

  @Override
  public boolean isCameraActive () {
    return __isCameraActive;
  }

  public void checkCameraState () {
    boolean isCameraActive = isCameraOpen && !isCameraPaused && api != destroyedCamera;
    if (__isCameraActive != isCameraActive) {
      boolean success;
      boolean needSwitchToLegacyApi = false;
      if (isCameraActive) {
        int state = openCameraImpl();
        success = state == STATE_OK;
        needSwitchToLegacyApi = state == STATE_SWITCH_TO_LEGACY_API;
      } else {
        success = closeCameraImpl();
      }
      if (success) {
        __isCameraActive = isCameraActive;
        Log.i(Log.TAG_CAMERA, "isCameraActive -> %b", isCameraActive);
      } else {
        Log.i(Log.TAG_CAMERA, "isCameraActive -> %b failed", isCameraActive);
        if (needSwitchToLegacyApi) {
          switchToLegacyApi();
        }
      }
    }
  }

  private static final int STATE_OK = 0;
  private static final int STATE_FAILED = 1;
  private static final int STATE_SWITCH_TO_LEGACY_API = 2;

  private int openCameraImpl () {
    int state = STATE_FAILED;
    try {
      if (api.openPreview()) {
        state = STATE_OK;
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Cannot open preview", t);
      if (!(api instanceof CameraApiLegacy)) {
        state = STATE_SWITCH_TO_LEGACY_API;
      } else {
        showFatalError(t);
      }
    }
    return state;
  }

  private boolean closeCameraImpl () {
    try {
      return api.closePreview();
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Cannot close preview", t);
    }
    return false;
  }

  // Semi-internal API

  public int getCurrentCameraRotation () {
    return api.calculateDisplayOrientation();
  }

  public int getCurrentCameraSensorOrientation () {
    return api.getSensorOrientation();
  }

  public void showFatalError (String error) {
    delegate.displayFatalErrorMessage(error);
  }

  public void showFatalError (Throwable error) {
    delegate.displayFatalErrorMessage(Log.toString(error));
  }

  public void resolveExpectedError (@CameraError.Code int code) {
    delegate.resolveExpectedError(code);
  }

  public void setAvailableCameraCount (int cameraCount) {
    synchronized (this) {
      if (this.availableCameraCount != cameraCount) {
        this.availableCameraCount = cameraCount;
        delegate.onAvailableCamerasCountChanged(cameraCount);
      }
    }
  }

  public int getAvailableCameraCount () {
    synchronized (this) {
      return availableCameraCount;
    }
  }

  // UI handler

  private void sendMessage (int what) {
    handler.sendMessage(Message.obtain(handler, what));
  }

  private void sendMessage (int what, int arg1, int arg2, Object obj) {
    handler.sendMessage(Message.obtain(handler, what, arg1, arg2, obj));
  }

  private final MainHandler handler = new MainHandler(this);
  private static final int ACTION_SWITCH_TO_LEGACY_API = 0;
  private static final int ACTION_DISPATCH_MEDIA_RESULT = 1;

  private void processMessage (Message msg) {
    switch (msg.what) {
      case ACTION_SWITCH_TO_LEGACY_API:
        switchToLegacyApi();
        break;
      case ACTION_DISPATCH_MEDIA_RESULT:
        if (msg.obj != null) {
          onTakeMediaResult((ImageGalleryFile) msg.obj, msg.arg1 == 1);
        } else {
          onTakeMediaError(msg.arg1 == 1);
        }
        break;
    }
  }

  private static class MainHandler extends Handler {
    private final CameraManagerLegacy manager;

    public MainHandler (CameraManagerLegacy manager) {

      super(Looper.getMainLooper());
      this.manager = manager;
    }

    @Override
    public void handleMessage (Message msg) {
      manager.processMessage(msg);
    }
  }

  // Texture listeners

  @Override
  public void onTextureAvailable (SurfaceTexture surface, int width, int height) {
    api.onSurfaceTextureAvailable(surface, width, height);
  }

  @Override
  public void onTextureSizeChanged (SurfaceTexture surface, int width, int height) {
    api.onSurfaceTextureSizeChanged(surface, width, height);
  }

  @Override
  public void onTextureDestroyed (SurfaceTexture surface) {
    api.onSurfaceTextureDestroyed(surface);
  }

  private boolean renderedFirstFrame;

  @Override
  public void setPreviewSize (int viewWidth, int viewHeight) {
    api.setPreviewSize(viewWidth, viewHeight);
  }

  // Called by Api

  @AnyThread
  void onPerformSuccessHint (boolean isVideo) {
    delegate.onPerformSuccessHint(isVideo);
  }

  @AnyThread
  public void onCameraSourceChange (boolean completed, boolean isForward, boolean toFrontFace) {
    delegate.onCameraSourceChange(completed, isForward, toFrontFace);
  }

  @AnyThread
  public void onFlashModeChanged (int mode) {
    delegate.onFlashModeChanged(mode);
  }

  @Override
  public float getMaxZoom () {
    return api.getMaxZoom();
  }

  @Override
  public float getMinZoom () {
    return 0f;
  }

  /**
   * Called when first camera frame has been rendered.
   *
   * Good place to hide fake overlays.
   * */
  @AnyThread
  public void onRenderedFirstFrame () {
    if (!renderedFirstFrame && __isCameraActive) {
      Log.i(Log.TAG_CAMERA, "onRenderedFirstFrame");
      renderedFirstFrame = true;
      delegate.onRenderedFirstFrame();
    }
  }

  /**
   * Called when {@link CameraTextureView} has been abandoned by {@link CameraApi}
   *
   * Show fake overlays at this point.
   * */
  @AnyThread
  public void resetRenderState (boolean needBlurPreview) {
    if (renderedFirstFrame) {
      Log.i(Log.TAG_CAMERA, "resetRenderState");
      renderedFirstFrame = false;
      delegate.onResetRenderState(needBlurPreview, null);
    }
  }

  /**
   * Called when camera preview size became known.
   * */
  @AnyThread
  public void setAspectRatio (int width, int height) {
    UI.post(() -> cameraView.setAspectRatio(width, height));
  }

  /**
   * Called when Camera2 API has failed due to unknown error.
   *
   * Seamlessly switch to CameraApiLegacy & remember that until SDK_INT is changed,
   * (obviously meaning that Android has been updated and camera2 API might has become available)
   * */
  public void switchToLegacyApi () {
    if (!UI.inUiThread()) {
      sendMessage(ACTION_SWITCH_TO_LEGACY_API);
      return;
    }

    if (api instanceof CameraApiLegacy) {
      Log.e(Log.TAG_CAMERA, "Trying to switch to legacy API, when already using legacy API");
      return;
    }

    Log.w(Log.TAG_CAMERA, "Switching to legacy API.");
    // Settings.instance().setPreferLegacyApi(true);
    destroy();

    destroyedCamera = null;
    api = new CameraApiLegacy(context, this);
    if (getSurfaceTexture() != null) {
      api.onSurfaceTextureAvailable(getSurfaceTexture(), getTextureWidth(), getTextureHeight());
    }

    checkCameraState();
  }
}
