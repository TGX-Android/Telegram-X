package org.thunderdog.challegram.ui.camera.x;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.util.Rational;
import android.util.Size;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
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
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.util.concurrent.ExecutionException;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraManagerX extends CameraManager<PreviewView> {
  private static final boolean REUSE_PREVIEW_DISABLED = true;
  private static final boolean REUSE_CAPTURE_DISABLED = true;

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
  private VideoCapture videoCapture;
  private Preview preview;
  private int previewRotation;
  private Camera camera;
  private int flashMode = ImageCapture.FLASH_MODE_OFF;
  private boolean originalFacing;
  private int lastAspectRatio;
  private Rational lastAspectRatioCustom;

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
      if (lastAspectRatio != aspectRatio || (aspectRatioCustom == null) != (lastAspectRatioCustom == null) || (aspectRatioCustom != null && !aspectRatioCustom.equals(lastAspectRatioCustom))) {
        bindPreview();
      }
    }
  }

  private static Size toSize (Rational rational, int surfaceRotation) {
    double maxSize = 1920;
    double minSize = maxSize * rational.doubleValue();
    return new Size((int) maxSize, (int) minSize - ((int) minSize) % 2);
  }

  @SuppressWarnings("RestrictedApi")
  private void bindPreview () {
    if (!isOpen || isPaused || cameraProvider == null)
      return;

    int aspectRatioMode = Settings.instance().getCameraAspectRatioMode();
    Rational aspectRatioCustom = null;
    int aspectRatio = AspectRatio.RATIO_16_9;
    switch (aspectRatioMode) {
      case Settings.CAMERA_RATIO_1_1:
        aspectRatioCustom = new Rational(1, 1);
        break;
      case Settings.CAMERA_RATIO_4_3:
        aspectRatio = AspectRatio.RATIO_4_3;
        break;
      case Settings.CAMERA_RATIO_FULL_SCREEN: {
        int width = getParentWidth();
        int height = getParentHeight();
        if (width == 0 || height == 0)
          return;
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
        break;
      }
      case Settings.CAMERA_RATIO_16_9:
      default:
        aspectRatio = AspectRatio.RATIO_16_9;
        break;
    }


    // Must unbind the use-cases before rebinding them
    cameraProvider.unbindAll();

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

    if (REUSE_PREVIEW_DISABLED || preview == null || previewRotation != getSurfaceRotation()) {
      Preview.Builder previewBuilder = new Preview.Builder()
              .setTargetRotation(previewRotation = getSurfaceRotation());
      if (aspectRatioCustom != null) {
        previewBuilder.setTargetResolution(toSize(aspectRatioCustom, getSurfaceRotation()));
      } else {
        previewBuilder.setTargetAspectRatio(aspectRatio);
      }
      preview = previewBuilder.build();
    }

    if (REUSE_CAPTURE_DISABLED || imageCapture == null) {
       ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .setFlashMode(flashMode)
              .setTargetRotation(getSurfaceRotation());
      if (aspectRatioCustom != null) {
        imageCaptureBuilder.setTargetResolution(toSize(aspectRatioCustom, getSurfaceRotation()));
      } else {
        imageCaptureBuilder.setTargetAspectRatio(aspectRatio);
      }
      imageCapture = imageCaptureBuilder.build();
    } else {
      imageCapture.setFlashMode(flashMode);
      imageCapture.setTargetRotation(getSurfaceRotation());
    }
    if (REUSE_CAPTURE_DISABLED || videoCapture == null) {
      VideoCapture.Builder b = new VideoCapture.Builder()
              .setTargetRotation(getSurfaceRotation());
      if (aspectRatioCustom != null) {
        b.setTargetResolution(toSize(aspectRatioCustom, getSurfaceRotation()));
      } else {
        b.setTargetAspectRatio(aspectRatio);
      }
      videoCapture = b.build();
    } else {
      videoCapture.setTargetRotation(getSurfaceRotation());
    }
    /*ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setTargetRotation(getSurfaceRotation())
            .setTargetAspectRatio(aspectRatio)
            .build();
    AtomicBoolean reportedFirstFrame = new AtomicBoolean();
    imageAnalysis.setAnalyzer(backgroundExecutor, proxy -> {
      proxy.close();
      if (!reportedFirstFrame.getAndSet(true)) {
        UI.post(() -> {
          if (this.contextId == contextId && isOpen && !isPaused) {
            delegate.onRenderedFirstFrame();
          }
        });
      }
    });*/

    lastAspectRatio = aspectRatio;
    lastAspectRatioCustom = aspectRatioCustom;

    try {
      // A variable number of use-cases can be passed here -
      // camera provides access to CameraControl & CameraInfo
      this.camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview, imageCapture, videoCapture);
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
        if (REUSE_PREVIEW_DISABLED) {
          preview = null;
        }
        if (REUSE_CAPTURE_DISABLED) {
          videoCapture = null;
          imageCapture = null;
        }
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

  @SuppressWarnings("RestrictedApi")
  @Override
  protected boolean onStartVideoCapture (int outRotation) {
    if (videoCapture != null) {
      boolean success;
      try {
        File outFile = getOutputFile(true);
        videoCapture.startRecording(new VideoCapture.OutputFileOptions.Builder(outFile).build(), ContextCompat.getMainExecutor(context), new VideoCapture.OnVideoSavedCallback() {
          @Override
          public void onVideoSaved (@NonNull VideoCapture.OutputFileResults ignored) {
            U.toGalleryFile(outFile, true, file -> {
              setTakingVideo(false, -1);
              onTakeMediaResult(file, true);
            });
          }

          @Override
          public void onError (int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
            setTakingVideo(false, -1);
            Log.e(Log.TAG_CAMERA, "Failed to capture video: %d, message: %s", cause, videoCaptureError, message);
            onTakeMediaError(true);
          }
        });
        success = true;
      } catch (Throwable t) {
        Log.e("Cannot start recording video", t);
        success = false;
      }
      if (success) {
        if (flashMode == ImageCapture.FLASH_MODE_ON && camera.getCameraInfo().hasFlashUnit()) {
          camera.getCameraControl().enableTorch(true);
        }
        UI.post(() -> setTakingVideo(true, SystemClock.uptimeMillis()));
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("RestrictedApi")
  @Override
  protected void onFinishOrCancelVideoCapture () {
    if (videoCapture != null) {
      videoCapture.stopRecording();
      if (flashMode == ImageCapture.FLASH_MODE_ON && camera.getCameraInfo().hasFlashUnit()) {
        camera.getCameraControl().enableTorch(false);
      }
      delegate.onVideoCaptureEnded();
    }
  }

  @Override
  public void destroy () {
    if (cameraProvider != null) {
      cameraProvider.unbindAll();
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
