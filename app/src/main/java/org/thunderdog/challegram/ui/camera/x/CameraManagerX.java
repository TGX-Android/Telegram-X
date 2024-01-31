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
 */
package org.thunderdog.challegram.ui.camera.x;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Rational;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.camera.CameraDelegate;
import org.thunderdog.challegram.ui.camera.CameraFeatures;
import org.thunderdog.challegram.ui.camera.CameraManager;
import org.thunderdog.challegram.ui.camera.CameraQrBridge;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraManagerX extends CameraManager<PreviewView> {
  public CameraManagerX (Context context, CameraDelegate delegate) {
    super(context, delegate);
  }

  @Override
  protected PreviewView onCreateView () {
    PreviewView view = new PreviewView(context);
    view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
      prepareBitmaps(v.getMeasuredWidth(), v.getMeasuredHeight())
    );
    view.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    return view;
  }

  private boolean isOpen;
  private ProcessCameraProvider cameraProvider;
  private ImageCapture imageCapture;
  private VideoCapture<Recorder> videoCapture;
  private Recording videoRecording;
  private VideoRecordEvent videoRecordStatus;
  private Preview preview;
  private int previewRotation;
  private Camera camera;
  private int flashMode = ImageCapture.FLASH_MODE_OFF;
  private boolean originalFacing;
  private CameraQrBridge cameraQrBridge;

  @Override
  public void openCamera () {
    this.isOpen = true;
    this.originalFacing = preferFrontFacingCamera();
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        if (isOpen) {
          this.cameraProvider = cameraProvider;
          this.bindPreview();
        } else {
          cameraProvider.unbindAll();
        }
      } catch (ExecutionException | InterruptedException e) {
        // No errors need to be handled for this Future.
        // This should never be reached.
      }
    }, ContextCompat.getMainExecutor(context));
  }

  @Override
  protected void onParentSizeChanged (int width, int height) {
    if (Settings.instance().getCameraAspectRatioMode() == Settings.CAMERA_RATIO_FULL_SCREEN) {
      if (width == 0 || height == 0)
        return;
      Rational aspectRatioCustom = null;
      int aspectRatio = AspectRatio.RATIO_16_9;
      float ratio = (float) Math.max(width, height) / (float) Math.min(width, height);
      if (ratio == 16f / 9f) {
        aspectRatio = AspectRatio.RATIO_16_9;
      } else if (ratio == 4f / 3f) {
        aspectRatio = AspectRatio.RATIO_4_3;
      } else if (ratio == 1f) {
        aspectRatioCustom = new Rational(1, 1);
      } else {
        aspectRatioCustom = new Rational(Math.min(width, height), Math.max(width, height));
      }
      bindPreview();
    }
  }

  private static android.util.Size toSize (Rational rational, int surfaceRotation) {
    double maxSize = 1920;
    double minSize = maxSize * rational.doubleValue();
    return new android.util.Size((int) maxSize, (int) minSize - ((int) minSize) % 2);
  }

  @SuppressWarnings("RestrictedApi")
  private void bindPreview () {
    if (!isOpen || isPaused || cameraProvider == null)
      return;

    // Must unbind the use-cases before rebinding them
    cameraProvider.unbindAll();

    AspectRatioStrategy aspectRatioStrategy;
    int aspectRatioMode = Settings.instance().getCameraAspectRatioMode();
    switch (aspectRatioMode) {
      case Settings.CAMERA_RATIO_FULL_SCREEN:
      case Settings.CAMERA_RATIO_16_9:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY;
        break;
      case Settings.CAMERA_RATIO_1_1:
      case Settings.CAMERA_RATIO_4_3:
      default:
        aspectRatioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY;
        break;
    }

    ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
      .setAspectRatioStrategy(aspectRatioStrategy)
      .build();

    final int lensFacing = preferFrontFacingCamera() ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;

    CameraSelector cameraSelector = new CameraSelector.Builder()
      .requireLensFacing(lensFacing)
      .build();
    try {
      if (!cameraProvider.hasCamera(cameraSelector)) {
        Log.w(Log.TAG_CAMERA, "Now camera with facing: %d", lensFacing);
        cameraSelector = new CameraSelector.Builder().build();
      }
    } catch (CameraInfoUnavailableException e) {
      Log.e(Log.TAG_CAMERA, "Unable to camera %d", lensFacing);
    }

    Preview.Builder previewBuilder = new Preview.Builder()
      .setTargetRotation(previewRotation = getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    preview = previewBuilder.build();

    ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
      .setFlashMode(flashMode)
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector);
    imageCapture = imageCaptureBuilder.build();

    QualitySelector qualitySelector = QualitySelector.fromOrderedList(
      Arrays.asList(Quality.FHD, Quality.HD, Quality.SD),
      FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
    );
    Recorder recorder = new Recorder.Builder()
      .setQualitySelector(qualitySelector)
      .build();
    videoCapture = new VideoCapture.Builder<>(recorder)
      .setCaptureType(UseCaseConfigFactory.CaptureType.VIDEO_CAPTURE)
      .setTargetRotation(getSurfaceRotation())
      .setResolutionSelector(resolutionSelector)
      .build();

    ImageAnalysis imageAnalyzer;

    boolean needQrScanner = delegate.useQrScanner();
    if (needQrScanner) {
      imageAnalyzer = new ImageAnalysis.Builder()
        .setTargetRotation(getSurfaceRotation())
        .setResolutionSelector(resolutionSelector)
        .build();
      if (cameraQrBridge == null) {
        cameraQrBridge = new CameraQrBridge(this);
      }
      imageAnalyzer.setAnalyzer(cameraQrBridge.backgroundExecutor, proxy -> cameraQrBridge.processImage(proxy));
    } else {
      imageAnalyzer = null;
    }

    try {
      // A variable number of use-cases can be passed here -
      // camera provides access to CameraControl & CameraInfo
      if (needQrScanner) {
        // We probably don't want to take photos or videos while scanning QR codes. (Also, there are 3 use case limit in CameraX)
        this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageAnalyzer);
      } else {
        this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageCapture, videoCapture);
      }
    } catch (Exception e) {
      Log.e(Log.TAG_CAMERA, "Use case binding failed", e);
      return;
    }

    Preview.SurfaceProvider surfaceProvider = cameraView.getSurfaceProvider();
    preview.setSurfaceProvider(surfaceProvider);
    UI.post(delegate::onRenderedFirstFrame, 800);

    reportFlashMode();
  }

  @Override
  public void closeCamera () {
    if (this.isOpen) {
      if (cameraProvider != null) {
        cameraProvider.unbindAll();
        preview = null;
        videoRecordStatus = null;
        if (videoRecording != null) {
          videoRecording.close();
          videoRecording = null;
        }
        videoCapture = null;
        imageCapture = null;
        cameraProvider = null;
        camera = null;
      }
      this.isOpen = false;
    }
  }

  @Override
  protected void onDisplayRotationChanged (int rotation) {
    if (cameraProvider != null) {
      bindPreview();
    }
  }

  private boolean switchingCamera;

  @Override
  public void switchCamera () {
    if (switchingCamera)
      return;
    if (cameraProvider != null) {
      int facing = !preferFrontFacingCamera() ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
      try {
        long speed = SystemClock.uptimeMillis();
        if (!cameraProvider.hasCamera(new CameraSelector.Builder().requireLensFacing(facing).build())) {
          Log.w(Log.TAG_CAMERA, "Camera is not available, facing: %d", facing);
          return;
        }
        Log.w("checked camera availability in %dms", SystemClock.uptimeMillis() - speed);
      } catch (CameraInfoUnavailableException e) {
        Log.e(Log.TAG_CAMERA, "Camera info unavailable, facing: %d", e, facing);
        return;
      }
      switchingCamera = true;
      setPreferFrontFacingCamera(!preferFrontFacingCamera());
      delegate.onCameraSourceChange(false, originalFacing != preferFrontFacingCamera(), preferFrontFacingCamera());
      delegate.onResetRenderState(true, () -> {
        bindPreview();
        delegate.onCameraSourceChange(true, originalFacing != preferFrontFacingCamera(), preferFrontFacingCamera());
        switchingCamera = false;
      });
    }
  }

  @Override
  public void switchFlashMode () {
    if (camera != null) {
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            flashMode = ImageCapture.FLASH_MODE_ON;
            break;
          case ImageCapture.FLASH_MODE_ON:
            flashMode = ImageCapture.FLASH_MODE_OFF;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            flashMode = isCapturingVideo() ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_AUTO;
            break;
          default:
            return;
        }
        imageCapture.setFlashMode(flashMode);
        reportFlashMode();
      } else if (preferFrontFacingCamera()) {
        this.flashMode = this.flashMode == ImageCapture.FLASH_MODE_OFF ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF;
        reportFlashMode();
      }
    }
  }

  private void reportFlashMode () {
    if (camera != null) {
      int reportFlashMode;
      if (camera.getCameraInfo().hasFlashUnit()) {
        switch (flashMode) {
          case ImageCapture.FLASH_MODE_AUTO:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_AUTO;
            break;
          case ImageCapture.FLASH_MODE_ON:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_ON;
            break;
          case ImageCapture.FLASH_MODE_OFF:
            reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
            break;
          default:
            return;
        }
      } else if (preferFrontFacingCamera()) {
        reportFlashMode = flashMode == ImageCapture.FLASH_MODE_OFF ? CameraFeatures.FEATURE_FLASH_OFF : CameraFeatures.FEATURE_FLASH_FAKE;
      } else {
        reportFlashMode = CameraFeatures.FEATURE_FLASH_OFF;
      }
      delegate.onFlashModeChanged(reportFlashMode);
    }
  }

  @Override
  protected boolean getBitmap (Bitmap bitmap) {
    for (int i = 0; i < cameraView.getChildCount(); i++) {
      View view = cameraView.getChildAt(i);
      if (view instanceof TextureView) {
        if (((TextureView) view).isAvailable()) {
          ((TextureView) view).getBitmap(bitmap);
          return true;
        }
        return false;
      }
    }
    return false;
  }

  @Override
  public boolean isCameraActive () {
    return isOpen && cameraProvider != null;
  }

  @Override
  public float getMaxZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMaxZoomRatio() : 0f;
  }

  @Override
  public float getMinZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getMinZoomRatio() : 0f;
  }

  @Override
  public float getCurrentZoom () {
    ZoomState zoomState = camera != null ? camera.getCameraInfo().getZoomState().getValue() : null;
    return zoomState != null ? zoomState.getZoomRatio() : 0f;
  }

  @Override
  public float getMinZoomStep () {
    return .1f;
  }

  @Override
  protected void onRequestZoom (float zoom) {
    if (camera != null && getCurrentZoom() != zoom) {
      camera.getCameraControl().setZoomRatio(zoom);
      onZoomChanged(zoom);
    }
  }

  @Override
  protected void onResetPreferences () {
    this.flashMode = ImageCapture.FLASH_MODE_OFF;
  }

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int outRotation) {
    if (imageCapture != null) {
      final File outFile = getOutputFile(false);
      if (outFile == null) {
        onTakeMediaError(false);
        return;
      }

      boolean ok;
      try {
        ImageCapture.OutputFileOptions.Builder b = new ImageCapture.OutputFileOptions.Builder(outFile);
        if (preferFrontFacingCamera() && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_NO_FLIP)) {
          ImageCapture.Metadata metadata = new ImageCapture.Metadata();
          metadata.setReversedHorizontal(true);
          b.setMetadata(metadata);
        }
        imageCapture.takePicture(b.build(), ContextCompat.getMainExecutor(context), new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved (@NonNull ImageCapture.OutputFileResults ignored) {
            U.toGalleryFile(outFile, false, file -> {
              if (file != null) {
                onTakeMediaResult(file, false);
              } else {
                Log.e(Log.TAG_CAMERA, "Output file still does not exist!");
                onTakeMediaError(false);
              }
            });
          }

          @Override
          public void onError (@NonNull ImageCaptureException e) {
            Log.e(Log.TAG_CAMERA, "Cannot take photo", e);
            onTakeMediaError(false);
          }
        });
        ok = true;
      } catch (Throwable t) {
        Log.e(Log.TAG_CAMERA, "Unable to take photo", t);
        onTakeMediaError(false);
        ok = false;
      }
      if (ok) {
        delegate.onPerformSuccessHint(false);
      }
    }
  }

  @Override
  protected boolean onStartVideoCapture (int outRotation) {
    if (videoCapture != null) {
      boolean success;
      File outFile = getOutputFile(true);
      boolean forceEnableTorch = flashMode == ImageCapture.FLASH_MODE_ON && camera.getCameraInfo().hasFlashUnit();
      try {
        if (!outFile.createNewFile()) {
          return false;
        }
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Unable to create output file for video", t);
        return false;
      }
      if (forceEnableTorch) {
        forceEnableTorch();
      }
      try {
        AtomicBoolean isFinished = new AtomicBoolean(false);
        videoRecording = videoCapture.getOutput()
          .prepareRecording(context, new FileOutputOptions.Builder(outFile).build())
          .withAudioEnabled()
          .start(ContextCompat.getMainExecutor(context), event -> {
            if (!(event instanceof VideoRecordEvent.Status)) {
              videoRecordStatus = event;
            }
            if (event instanceof VideoRecordEvent.Start) {
              VideoRecordEvent.Start start = (VideoRecordEvent.Start) event;
              setTakingVideo(true, SystemClock.uptimeMillis());
            } else if (event instanceof VideoRecordEvent.Finalize) {
              if (isFinished.getAndSet(true)) {
                return;
              }
              VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;

              if (finalize.hasError()) {
                if (outFile.exists()) {
                  if (!outFile.delete()) {
                    Log.e(Log.TAG_CAMERA, "Unable to delete video output file");
                  }
                }
                setTakingVideo(false, -1);
                Log.e(Log.TAG_CAMERA, "Failed to capture video: %d", finalize.getCause(), finalize.getError());
                onTakeMediaError(true);
              } else {
                U.toGalleryFile(outFile, true, file -> {
                  setTakingVideo(false, -1);
                  onTakeMediaResult(file, true);
                });
              }
            }
          });
        success = true;
      } catch (Throwable t) {
        Log.e("Cannot start recording video", t);
        success = false;
      }
      if (!success) {
        disableTorch();
      }
      return success;
    }
    return false;
  }

  @SuppressWarnings("RestrictedApi")
  @Override
  protected void onFinishOrCancelVideoCapture () {
    if (videoRecording != null) {
      videoRecording.stop();
      onVideoRecordingFinished();
    }
  }

  private boolean torchForceEnabled;

  private void forceEnableTorch () {
    if (!torchForceEnabled) {
      torchForceEnabled = true;
      camera.getCameraControl().enableTorch(true);
    }
  }

  private void disableTorch () {
    if (torchForceEnabled) {
      torchForceEnabled = false;
      camera.getCameraControl().enableTorch(false);
    }
  }

  private void onVideoRecordingFinished () {
    disableTorch();
    delegate.onVideoCaptureEnded();
  }

  @Override
  public void destroy () {
    if (cameraQrBridge != null) {
      cameraQrBridge.destroy();
      cameraQrBridge = null;
    }
  }

  private boolean isPaused;

  @Override
  public void pauseCamera () {
    isPaused = true;
    if (cameraProvider != null) {
      cameraProvider.unbindAll();
    }
  }

  @Override
  public void resumeCamera () {
    isPaused = false;
    if (isOpen) {
      bindPreview();
    }
  }
}
