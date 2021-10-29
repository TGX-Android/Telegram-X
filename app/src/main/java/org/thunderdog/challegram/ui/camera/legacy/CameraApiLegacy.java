package org.thunderdog.challegram.ui.camera.legacy;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;

import androidx.exifinterface.media.ExifInterface;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Device;
import org.thunderdog.challegram.filegen.TdlibFileGenerationManager;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.ui.camera.CameraFeatures;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.unit.ByteUnit;
import okio.BufferedSink;
import okio.Okio;

/**
 * Date: 9/19/17
 * Author: default
 */

@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CameraApiLegacy extends CameraApi implements Camera.PreviewCallback, Camera.AutoFocusMoveCallback, Camera.AutoFocusCallback, Camera.ShutterCallback, Camera.PictureCallback, MediaRecorder.OnInfoListener {
  // === UI ===

  public CameraApiLegacy (Context context, CameraManagerLegacy manager) {
    super(context, manager);
  }

  // === BACKGROUND ===

  private void resetContextualSettings () {
    resetFlashMode();
    resetZoom();
  }

  @Override
  protected void onNextCameraSourceRequested () {
    if (isCameraActive && mNumberOfCameras > 1) {
      resetContextualSettings();
      manager.resetRenderState(true);
      int nextCameraIndex = getNextCameraIndex();
      getCameraInfo(cameraIds[nextCameraIndex], nextCameraInfo);
      boolean forward = nextCameraIndex >= getRequestedCameraIndex();
      boolean toFrontFace = nextCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
      manager.onCameraSourceChange(false, forward, toFrontFace);
      setCameraActive(false);
      setRequestedCameraIndex(nextCameraIndex);
      setCameraActive(true);
      manager.onCameraSourceChange(true, forward, toFrontFace);
    }
  }

  @Override
  protected void onResetRequestedSettings () {
    frontCameraId = -1;
    rearCameraId = -1;
    mCurrentZoom = 0;
  }

  private Camera camera;
  private int cameraId = -1;
  private final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
  private final Camera.CameraInfo nextCameraInfo = new Camera.CameraInfo();
  private Camera.Size previewSize, outputPictureSize, outputVideoSize;

  private static String getFlashMode (int flashMode) {
    switch (flashMode) {
      case CameraFeatures.FEATURE_FLASH_AUTO:
        return Camera.Parameters.FLASH_MODE_AUTO;
      case CameraFeatures.FEATURE_FLASH_ON:
        return Camera.Parameters.FLASH_MODE_ON;
      case CameraFeatures.FEATURE_FLASH_OFF:
      default:
        return Camera.Parameters.FLASH_MODE_OFF;
    }
  }

  @Override
  protected void onFlashModeChange (int newFlashMode) throws Throwable {
    if (isCameraActive) {
      Camera.Parameters params = camera.getParameters();
      params.setFlashMode(getFlashMode(newFlashMode));
      camera.setParameters(params);
    }
  }

  private int mCurrentZoom;

  @Override
  protected void onZoomChanged (float zoom) {
    int zoomValue = Math.round(zoom);
    if (mCurrentZoom != zoomValue) {
      boolean success = mFeatures == null;
      if (!success && mFeatures.has(CameraFeatures.FEATURE_ZOOM_SMOOTH)) {
        try {
          camera.stopSmoothZoom();
          camera.startSmoothZoom(zoomValue);
          success = true;
        } catch (Throwable t) {
          Log.w(Log.TAG_CAMERA, "Cannot change zoom smoothly", t);
        }
      }
      if (!success && mFeatures.has(CameraFeatures.FEATURE_ZOOM)) {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setZoom(zoomValue);
        camera.setParameters(parameters);
      }
      mCurrentZoom = zoomValue;
    }
  }

  private void setupCamera () throws Throwable {
    CameraFeatures features = new CameraFeatures(false);

    Camera.Parameters params = camera.getParameters();

    // First, detect all supported features by camera

    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      features.add(CameraFeatures.FEATURE_FACING_FRONT);
    }

    if (features.has(CameraFeatures.FEATURE_FACING_FRONT)) {
      List<String> sceneModes = params.getSupportedSceneModes();
      if (sceneModes != null && !sceneModes.isEmpty()) {
        for (String sceneMode : sceneModes) {
          if (StringUtils.equalsOrBothEmpty(sceneMode, Camera.Parameters.SCENE_MODE_PORTRAIT)) {
            params.setSceneMode(Camera.Parameters.SCENE_MODE_PORTRAIT);
            break;
          }
        }
      }

      List<String> antibandingModes = params.getSupportedAntibanding();
      if (antibandingModes != null && !antibandingModes.isEmpty()) {
        for (String antibandingMode : antibandingModes) {
          if (StringUtils.equalsOrBothEmpty(antibandingMode, Camera.Parameters.ANTIBANDING_AUTO)) {
            params.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
            break;
          }
        }
      }

      List<String> whiteBalances = params.getSupportedWhiteBalance();
      if (whiteBalances != null && !whiteBalances.isEmpty()) {
        for (String whiteBalance : whiteBalances) {
          if (StringUtils.equalsOrBothEmpty(whiteBalance, Camera.Parameters.WHITE_BALANCE_AUTO)) {
            params.setWhiteBalance(whiteBalance);
            break;
          }
        }
      }

      if (params.isVideoStabilizationSupported()) {
        params.setVideoStabilization(true);
      }
    }

    List<String> focusModes = params.getSupportedFocusModes();
    if (focusModes != null) {
      for (String focusMode : focusModes) {
        if (StringUtils.isEmpty(focusMode)) {
          continue;
        }
        int feature = 0;
        switch (focusMode) {
          case Camera.Parameters.FOCUS_MODE_AUTO:
            feature = CameraFeatures.FEATURE_FOCUS_AUTO;
            break;
          case Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE:
            feature = CameraFeatures.FEATURE_FOCUS_CONTINUOUS_PICTURE;
            break;
          case Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO:
            feature = CameraFeatures.FEATURE_FOCUS_CONTINUOUS_VIDEO;
            break;
        }
        if (feature != 0) {
          features.add(feature);
        }
      }
    }

    List<String> flashModes = params.getSupportedFlashModes();
    if (flashModes != null) {
      for (String flashMode : flashModes) {
        if (StringUtils.isEmpty(flashMode)) {
          continue;
        }
        switch (flashMode) {
          case Camera.Parameters.FLASH_MODE_OFF:
            features.add(CameraFeatures.FEATURE_FLASH_OFF);
            break;
          case Camera.Parameters.FLASH_MODE_ON:
            features.add(CameraFeatures.FEATURE_FLASH_ON);
            break;
          case Camera.Parameters.FLASH_MODE_AUTO:
            features.add(CameraFeatures.FEATURE_FLASH_AUTO);
            break;
          case Camera.Parameters.FLASH_MODE_TORCH:
            features.add(CameraFeatures.FEATURE_FLASH_TORCH);
            break;
        }
      }
    }

    boolean zoomSupported = false;
    if (params.isZoomSupported()) {
      features.add(CameraFeatures.FEATURE_ZOOM);
      zoomSupported = true;
    }
    if (params.isSmoothZoomSupported()) {
      features.add(CameraFeatures.FEATURE_ZOOM_SMOOTH);
      zoomSupported = true;
    }
    if (zoomSupported) {
      features.setMaxZoom(params.getMaxZoom());
    }

    int numFocusAreas = params.getMaxNumFocusAreas();
    if (numFocusAreas > 0) {
      features.add(CameraFeatures.FEATURE_AREA_FOCUS);
    }
    int numMeteringAreas = params.getMaxNumMeteringAreas();
    if (numMeteringAreas > 0) {
      features.add(CameraFeatures.FEATURE_AREA_METERING);
    }

    // Then, set all resulting parameters

    params.setRecordingHint(true);
    params.setPictureFormat(ImageFormat.JPEG);
    params.setRotation(outputRotation = calculateOutputRotation());

    if (features.has(CameraFeatures.FEATURE_FOCUS_ANY)) {
      if (features.has(CameraFeatures.FEATURE_FOCUS_CONTINUOUS_PICTURE)) {
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (features.has(CameraFeatures.FEATURE_FOCUS_CONTINUOUS_VIDEO)) {
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      } else if (features.has(CameraFeatures.FEATURE_FOCUS_AUTO)) {
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      }
    }

    if (features.canFlash(false)) {
      params.setFlashMode(getFlashMode(mFlashMode));
    } else if (features.has(CameraFeatures.FEATURE_FLASH_OFF)) {
      params.setFlashMode(getFlashMode(CameraFeatures.FEATURE_FLASH_OFF));
    }
    if (features.canZoom()) {
      params.setZoom(mCurrentZoom);
    }
    setCameraDeviceFeatures(features);
    // resetFlashMode();

    // Then, set output size
    setupCameraSizes(params);

    camera.setParameters(params);
  }

  private static void sortOutputVideoSizes (List<Camera.Size> sizes, final float desiredRatio, final int maxResolution) {
    Collections.sort(sizes, (o1, o2) -> compareOutputSizes(o1.width, o1.height, o2.width, o2.height, desiredRatio, maxResolution));
  }

  private static void sortPreviewSizes (List<Camera.Size> sizes, final long desiredWidth, final long desiredHeight, final float desiredRatio) {
    final long desiredSquare = desiredWidth * desiredHeight;
    Collections.sort(sizes, (o1, o2) -> comparePreviewSizes(o1.width, o1.height, o2.width, o2.height, desiredSquare, desiredRatio));
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void setupCameraSizes (Camera.Parameters params) {
    if (mPreviewWidth == 0 || mPreviewHeight == 0) {
      return;
    }

    int previewWidth = mPreviewWidth, previewHeight = mPreviewHeight;
    int displayOrientation = calculateDisplayOrientation();
    boolean isRotated = U.isRotated(displayOrientation);

    if (isRotated) {
      int temp = previewWidth;
      previewWidth = previewHeight;
      previewHeight = temp;
    }

    Log.i(Log.TAG_CAMERA, "calculating output sizes; width: %d, height: %d, orientation: %d", previewWidth, previewHeight, displayOrientation);

    final ArrayList<Camera.Size> sizes = new ArrayList<>();

    final List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
    if (previewSizes != null && !previewSizes.isEmpty()) {
      final float previewRatio = (float) previewWidth / (float) previewHeight;

      sizes.ensureCapacity(previewSizes.size());
      sizes.addAll(previewSizes);

      int maxResolution = manager.getMaxResolution();
      if (maxResolution != 0) {
        sortPreviewSizes(sizes, maxResolution, maxResolution, 16f / 9f);
      } else {
        sortPreviewSizes(sizes, previewWidth, previewHeight, previewRatio);
      }
      previewSize = sizes.get(0);
      params.setPreviewSize(previewSize.width, previewSize.height);

      sizes.clear();
    }

    final List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
    if (pictureSizes != null && !pictureSizes.isEmpty()) {
      sizes.ensureCapacity(pictureSizes.size());
      sizes.addAll(pictureSizes);

      float ratio = (float) Math.min(previewWidth, previewHeight) / (float) Math.max(previewWidth, previewHeight);
      float desiredRatio = MathUtils.aspectRatio(previewWidth, previewHeight);

      sortPreviewSizes(sizes, 1280, (int) (1280f * ratio), desiredRatio);
      outputPictureSize = sizes.get(0);
      params.setPictureSize(outputPictureSize.width, outputPictureSize.height);

      sizes.clear();
    }

    final List<Camera.Size> videoSizes = params.getSupportedVideoSizes();
    if (videoSizes != null && !videoSizes.isEmpty()) {
      sizes.ensureCapacity(videoSizes.size());
      sizes.addAll(videoSizes);

      final float outputRatio = MathUtils.aspectRatio(previewWidth, previewHeight);

      sortOutputVideoSizes(sizes, outputRatio, manager.getMaxResolution());
      outputVideoSize = sizes.get(0);

      Log.i(Log.TAG_CAMERA, "output video size: %dx%d", outputVideoSize.width, outputVideoSize.height);
      sizes.clear();
    } else {
      Log.i(Log.TAG_CAMERA, "output video size: unknown");
      outputVideoSize = null;
    }

    Log.i(Log.TAG_CAMERA, "preview size: %dx%d", previewSize.width, previewSize.height);

    int width, height;
    if (isRotated) {
      width = previewSize.height; height = previewSize.width;
    } else {
      width = previewSize.width;
      height = previewSize.height;
    }

    manager.setAspectRatio(width, height);
  }

  private Integer[] cameraIds;
  private int rearCameraId = -1, frontCameraId = -1;

  private final Comparator<Integer> cameraIdComparator = (o1, o2) -> {
    final int left = o1;
    final int right = o2;
    if (manager.preferFrontFacingCamera()) {
      if (frontCameraId != -1) {
        if (left == frontCameraId)
          return -1;
        else if (right == frontCameraId)
          return 1;
      }
      if (rearCameraId != -1) {
        if (left == rearCameraId)
          return -1;
        else if (right == rearCameraId)
          return 1;
      }
    } else {
      if (rearCameraId != -1) {
        if (left == rearCameraId)
          return -1;
        else if (right == rearCameraId)
          return 1;
      }
      if (frontCameraId != -1) {
        if (left == frontCameraId)
          return -1;
        else if (right == frontCameraId)
          return 1;
      }
    }
    if (left < right) {
      return -1;
    } else if (left == right) {
      return 0;
    } else {
      return 1;
    }
  };

  private static void getCameraInfo (int id, Camera.CameraInfo info) {
    info.facing = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      info.canDisableShutterSound = false;
    }
    info.orientation = 0;
    Camera.getCameraInfo(id, info);
  }

  @Override
  protected boolean prepareCamera () throws Throwable {
    setNumberOfCameras(Camera.getNumberOfCameras());

    int cameraId = -1;

    if (mNumberOfCameras > 0) {
      if (cameraIds == null || cameraIds.length != mNumberOfCameras) {
        cameraIds = new Integer[mNumberOfCameras];
      }

      rearCameraId = -1;
      frontCameraId = -1;
      int lastInfoCameraId = -1;

      for (int i = 0; i < mNumberOfCameras; i++) {
        getCameraInfo(i, cameraInfo);
        lastInfoCameraId = i;
        boolean isFrontFace = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
        if (isFrontFace) {
          if (frontCameraId == -1) {
            frontCameraId = i;
          }
        } else {
          if (rearCameraId == -1) {
            rearCameraId = i;
          }
        }
        cameraIds[i] = i;
      }
      Arrays.sort(cameraIds, cameraIdComparator);
      cameraId = cameraIds[getRequestedCameraIndex()];
      if (lastInfoCameraId != cameraId) {
        getCameraInfo(cameraId, cameraInfo);
      }
    }

    if (cameraId == -1) {
      manager.showFatalError("Camera hardware failed");
      return false;
    }

    this.camera = Camera.open(cameraId);
    this.cameraId = cameraId;
    try {
      setupCamera();
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Cannot start camera preview", t);
      closeCamera();
    }

    return camera != null;
  }

  @Override
  protected int getCameraOutputWidth () {
    return previewSize.width;
  }

  @Override
  protected int getCameraOutputHeight () {
    return previewSize.height;
  }

  @Override
  protected boolean openCamera (SurfaceTexture texture) throws Throwable {
    camera.setPreviewTexture(texture);

    camera.setAutoFocusMoveCallback(this);
    camera.setDisplayOrientation(calculateDisplayOrientation());
    camera.setOneShotPreviewCallback(this);
    camera.startPreview();

    return true;
  }

  private int outputRotation;

  private void setOutputRotation (int rotation) {
    if (this.outputRotation != rotation) {
      this.outputRotation = rotation;
      try {
        Camera.Parameters params = camera.getParameters();
        params.setRotation(rotation);
        camera.setParameters(params);
      } catch (Throwable t) {
        Log.e(Log.TAG_CAMERA,"Cannot set output rotation", t);
      }
    }
  }

  @Override
  protected void onDisplayOrientationChanged () {
    if (isCameraActive) {
      try {
        camera.setDisplayOrientation(calculateDisplayOrientation());
        setOutputRotation(calculateOutputRotation());
      } catch (Throwable t) {
        Log.critical(t);
      }
    }
  }

  @Override
  protected void onForcedOrientationChange () {
    if (isCameraActive) {
      setOutputRotation(calculateOutputRotation());
    }
  }

  @Override
  protected int calculateDisplayOrientation () {
    int degrees = this.mDisplayOrientation;
    int result;
    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (cameraInfo.orientation + degrees) % 360;
      result = (360 - result) % 360;  // compensate the mirror
    } else {  // back-facing
      result = (cameraInfo.orientation - degrees + 360) % 360;
    }
    return result;
  }

  @Override
  protected int getSensorOrientation() {
    return cameraInfo.orientation;
  }

  private int calculateOutputRotation () {
    int result;
    int orientation = this.mForcedOutputOrientation != -1 ? this.mForcedOutputOrientation : this.mDisplayOrientation;
    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (cameraInfo.orientation + orientation) % 360;
      //result = (360 - result) % 360;
    } else {
      result = (cameraInfo.orientation - orientation + 360) % 360;
    }
    return result;
  }

  @Override
  protected void closeCamera () throws Throwable {
    if (camera != null) {
      try {
        if (mFeatures.canFocusByTap()) {
          camera.cancelAutoFocus();
        }
      } catch (Throwable t) {
        Log.i(Log.TAG_CAMERA, "Cannot cancel auto-focus", t);
      }
      try {
        manager.resetRenderState(false);
        camera.stopPreview();
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot stop camera preview", t);
      }
      try {
        camera.release();
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot release camera", t);
      }
      this.camera = null;
      this.cameraId = -1;
      setCameraDeviceFeatures(null);
    }
  }

  // Take photo

  private int pictureWidth, pictureHeight, pictureRotation;

  @Override
  protected void onTakePhoto (int trimWidth, int trimHeight, int orientation) {
    if (isCameraActive) {
      this.pictureWidth = trimWidth;
      this.pictureHeight = trimHeight;
      this.pictureRotation = orientation;
      camera.takePicture(this, null, this);
    } else {
      manager.onTakeMediaError(false);
    }
  }

  @Override
  public void onPictureTaken (byte[] data, Camera camera) {
    final int[] exifOrientationOffset = new int[3];
    int inputOrientation = U.getExifOrientation(data, exifOrientationOffset);
    final boolean isFacingFront = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
    final int outputOrientation;
    if (isFacingFront && !Settings.instance().getNewSetting(Settings.SETTING_FLAG_CAMERA_NO_FLIP)) {
      outputOrientation = U.flipExifHorizontally(U.rotateExifOrientation(inputOrientation, 360 - pictureRotation));
    } else if (isFacingFront) {
      outputOrientation = U.rotateExifOrientation(inputOrientation, 360 - pictureRotation);
    } else {
      outputOrientation = U.rotateExifOrientation(inputOrientation, pictureRotation);
    }
    if (outputOrientation != inputOrientation && U.setExifOrientation(data, exifOrientationOffset, outputOrientation)) {
      inputOrientation = outputOrientation;
    }
    final File file = manager.getOutputFile(false);
    boolean success = false;
    switch (outputOrientation) {
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
      case ExifInterface.ORIENTATION_TRANSPOSE:
      case ExifInterface.ORIENTATION_TRANSVERSE: {
        try {
          Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
          if (bitmap != null) {
            bitmap = TdlibFileGenerationManager.orientBitmap(bitmap, outputOrientation);
            try (FileOutputStream out = new FileOutputStream(file)) {
              success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            }
          }
        } catch (Throwable t) {
          Log.e(Log.TAG_CAMERA, "Unable to save taken picture", t);
        }
        break;
      }
    }

    if (!success) {
      try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
        sink.write(data);
      } catch (IOException e) {
        Log.e("Unable to save picture", e);
        manager.onTakeMediaError(false);
        return;
      }
      if (outputOrientation != inputOrientation) {
        U.putExifOrientation(file, outputOrientation);
      }
    }

    U.toGalleryFile(file, false, galleryFile -> manager.onTakeMediaResult(galleryFile, false));
  }

  @Override
  public void onShutter () {
    manager.onPerformSuccessHint(false);
  }

  private void restartPreview () {
    try { camera.stopPreview(); } catch (Throwable ignored) { }
    try { camera.startPreview(); } catch (Throwable ignored) { }
  }

  // Video capture

  @Override
  public boolean isVideoRecordSupported () {
    return true;
  }

  private MediaRecorder videoRecorder;
  private File videoFile;

  @Override
  protected void onStartVideoCapture () throws Throwable {
    if (!isCameraActive) {
      throw new IllegalStateException("!isCameraActive");
    }

    camera.unlock();

    videoRecorder = new MediaRecorder();
    videoRecorder.setCamera(camera);
    videoRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
    videoRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
    videoFile = configureRecorder(videoRecorder);
    if (!videoFile.createNewFile()) {
      throw new IllegalStateException("Could not create output file");
    }
    videoRecorder.setOutputFile(videoFile.getAbsolutePath());
    videoRecorder.setMaxFileSize(ByteUnit.GIB.toBytes(1));
    videoRecorder.setVideoFrameRate(30);
    videoRecorder.setMaxDuration(0);
    videoRecorder.setVideoEncodingBitRate(900000 * 2);
    //videoRecorder.setAudioEncodingBitRate(32000);
    //videoRecorder.setAudioChannels(1);
    videoRecorder.setVideoSize(outputVideoSize.width, outputVideoSize.height);
    videoRecorder.setOnInfoListener(this);
    videoRecorder.prepare();
    videoRecorder.start();
  }

  private File configureRecorder (MediaRecorder recorder) {
    int orientation = mForcedOutputOrientation;
    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      orientation = (cameraInfo.orientation + orientation) % 360;
      orientation = (360 - orientation) % 360;

      if (orientation == 90) {
        orientation = 270;
      }
      if ("Huawei".equals(Build.MANUFACTURER) && "angler".equals(Build.PRODUCT) && orientation == 270) {
        orientation = 90;
      }
    } else {
      orientation = (cameraInfo.orientation - orientation + 360) % 360;
    }
    recorder.setOrientationHint(orientation);
    Log.i(Log.TAG_CAMERA, "output video orientation: %d", orientation);

    int highProfile = getHighCamcorderProfile();
    if (CamcorderProfile.hasProfile(cameraId, highProfile)) {
      recorder.setProfile(CamcorderProfile.get(cameraId, highProfile));
    } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_LOW)) {
      recorder.setProfile(CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW));
    } else {
      throw new IllegalStateException("Could not set camcorder profile");
    }

    return manager.getOutputFile(true);
  }

  private static int getHighCamcorderProfile () {
    switch (Device.PRODUCT) {
      case Device.LGE_G3:
        return CamcorderProfile.QUALITY_480P;
    }
    return CamcorderProfile.QUALITY_HIGH;
  }

  @Override
  protected void onFinishVideoCapture (boolean saveFile, RunnableData<ImageGalleryFile> callback) {
    try {
      camera.reconnect();
      camera.startPreview();
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Cannot reconnect camera", t);
    }

    if (videoRecorder != null) {
      try {
        videoRecorder.stop();
      } catch (Throwable t) {
        Log.w(Log.TAG_CAMERA, "Cannot stop videoRecorder", t);
      }
    }

    if (videoFile != null) {
      if (saveFile && videoFile.exists()) {
        U.toGalleryFile(videoFile, true, callback);
      }

      if (!saveFile && videoFile.exists() && !videoFile.delete()) {
        Log.w(Log.TAG_CAMERA, "Cannot delete video file: %s", videoFile.getPath());
      }
      videoFile = null;
    }

    if (videoRecorder != null) {
      try { videoRecorder.release(); } catch (Throwable t) { Log.w(Log.TAG_CAMERA, "Cannot release MediaRecorder", t); }
      videoRecorder = null;
    }
  }

  @Override
  public void onInfo (MediaRecorder mr, int what, int extra) {
    switch (what) {
      case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
      case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
      case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
        finishOrCancelVideoCapture();
        break;
      default:
        Log.i(Log.TAG_CAMERA, "unknown MediaRecorder what: %d, extra: %d", what, extra);
        break;
    }
  }

  // Preview size

  @Override
  protected void onPreviewSizeChanged (int newWidth, int newHeight) {
    if (isCameraActive) {
      setupCameraSizes(camera.getParameters());
    }
  }

  // Camera listeners

  @Override
  public void onPreviewFrame (byte[] data, Camera camera) {
    if (isCameraActive) {
      manager.onRenderedFirstFrame();
      try {
        camera.setOneShotPreviewCallback(this::onPreviewFrameInternal);
      } catch (RuntimeException ignored) { }
    }
  }

  public void onPreviewFrameInternal (byte[] data, Camera camera) {
    if (isCameraActive) {
      manager.onPreviewFrame(data, camera);
    }
  }

  public void notifyCanReadNextFrame () {
    if (isCameraActive) {
      try {
        camera.setOneShotPreviewCallback(this::onPreviewFrameInternal);
      } catch (RuntimeException ignored) {}
    }
  }

  @Override
  public void onAutoFocus (boolean success, Camera camera) {
    Log.i(Log.TAG_CAMERA, "onAutoFocus %b", success);
  }

  @Override
  public void onAutoFocusMoving (boolean start, Camera camera) {
    Log.i(Log.TAG_CAMERA, "onAutoFocusMoving %b", start);
  }
}
