package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;
import androidx.exifinterface.media.ExifInterface;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.loader.ImageGalleryFile;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.loader.ImageStrictCache;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;

import java.io.File;
import java.util.ArrayList;

public abstract class CameraManager <T extends View> {
  public static final long MINIMUM_VIDEO_DURATION = 1200l;

  protected final Context context;
  protected final CameraDelegate delegate;
  protected final T cameraView;

  public CameraManager (Context context, CameraDelegate delegate) {
    this.context = context;
    this.delegate = delegate;
    this.cameraView = onCreateView();
    checkDisplayRotation();
  }

  protected abstract T onCreateView ();

  public final T getView () {
    return cameraView;
  }

  protected abstract void onDisplayRotationChanged (int rotation);

  @UiThread
  public abstract void switchCamera ();
  @UiThread
  public abstract void switchFlashMode ();

  public abstract boolean isCameraActive ();

  public abstract float getMaxZoom ();
  public abstract float getMinZoom ();
  public abstract float getCurrentZoom ();
  public abstract float getMinZoomStep ();
  protected abstract void onRequestZoom (float zoom);
  @AnyThread
  public final void onZoomChanged (float zoom) {
    delegate.onZoomChanged(zoom);
  }

  protected abstract void onResetPreferences ();

  public final void resetPreferences () {
    setPreferFrontFacingCamera(false);
    onResetPreferences();
  }

  protected abstract void onTakePhoto (int trimWidth, int trimHeight, int outRotation);
  protected abstract boolean onStartVideoCapture (int outRotation);
  protected abstract void onFinishOrCancelVideoCapture ();

  public abstract void openCamera ();
  public abstract void pauseCamera ();
  public abstract void resumeCamera ();
  public abstract void closeCamera ();
  public abstract void destroy ();

  // Properties

  private int maxResolution;

  public final void setMaxResolution (int maxResolution) {
    this.maxResolution = maxResolution;
  }

  public final int getMaxResolution () {
    return maxResolution;
  }

  private boolean preferFrontFacingCamera;

  public final boolean preferFrontFacingCamera () {
    return preferFrontFacingCamera;
  }

  public final void setPreferFrontFacingCamera (boolean frontFace) {
    this.preferFrontFacingCamera = frontFace;
  }

  private boolean noPreviewBlur;

  public void setNoPreviewBlur (boolean noPreviewBlur) {
    this.noPreviewBlur = noPreviewBlur;
  }

  public boolean shouldIgnorePreviewBlur () {
    return noPreviewBlur;
  }

  public final int getDisplayRotation () {
    return displayRotation;
  }

  public final int getSurfaceRotation () {
    switch (getDisplayRotation()) {
      case 0:
        return Surface.ROTATION_0;
      case 90:
        return Surface.ROTATION_90;
      case 180:
        return Surface.ROTATION_180;
      case 270:
        return Surface.ROTATION_270;
    }
    throw new IllegalStateException("displayRotation = " + getDisplayRotation());
  }

  private int displayRotation;

  public final void checkDisplayRotation () {
    BaseActivity activity = UI.getContext(context);
    int rotation = activity.getWindowRotationDegrees();
    if (this.displayRotation != rotation) {
      this.displayRotation = rotation;
      delegate.onDisplayRotationChanged();
      onDisplayRotationChanged(displayRotation);
    }
  }

  private int parentWidth, parentHeight;

  protected void onParentSizeChanged (int width, int height) {
    // Override
  }

  public final int getParentWidth () {
    return parentWidth;
  }

  public final int getParentHeight () {
    return parentHeight;
  }

  public final void setParentSize (int viewWidth, int viewHeight) {
    if (this.parentWidth != viewWidth || this.parentHeight != viewHeight) {
      this.parentWidth = viewWidth;
      this.parentHeight = viewHeight;
      onParentSizeChanged(viewWidth, viewHeight);
    }
  }

  public final boolean isCapturingVideo () {
    return takingVideo;
  }

  public final void setTakingVideo (boolean isTakingVideo, long startTimeMs) {
    if (!UI.inUiThread()) {
      UI.post(() -> setTakingVideo(isTakingVideo, startTimeMs));
      return;
    }
    if (this.takingVideo != isTakingVideo) {
      this.takingVideo = isTakingVideo;
      checkUiBlocked();
      if (isTakingVideo) {
        delegate.onVideoCaptureStarted(startTimeMs);
      } else {
        delegate.onVideoCaptureEnded();
      }
    }
  }

  private boolean takingPhoto, takingVideo, finishingVideo;
  private boolean uiBlocked;

  private void checkUiBlocked () {
    boolean isBlocked = takingPhoto || takingVideo || finishingVideo;
    if (this.uiBlocked != isBlocked) {
      this.uiBlocked = isBlocked;
      delegate.onUiBlocked(isBlocked);
    }
  }

  public final void setTakingPhoto (boolean isTakingPhoto) {
    if (this.takingPhoto != isTakingPhoto) {
      this.takingPhoto = isTakingPhoto;
      checkUiBlocked();
    }
  }

  public final void setFinishingVideo (boolean isVideo) {
    if (this.finishingVideo != isVideo) {
      this.finishingVideo = isVideo;
      checkUiBlocked();
    }
  }

  /**
   * Async request to take photo
   * */
  public final void takePhoto (int trimWidth, int trimHeight, int outRotation) {
    if (!takingPhoto) {
      setTakingPhoto(true);
      onTakePhoto(trimWidth, trimHeight, outRotation);
    }
  }

  public final boolean startVideoCapture (int outRotation) {
    if (takingPhoto || takingVideo)
      return false;
    if (TdlibManager.instance().calls().promptActiveCall())
      return false;
    return onStartVideoCapture(outRotation);
  }

  /**
   * Async request to stop video capturing.
   *
   * if result video is long enough, video capture will be completed soon
   * if result video is shorter than {@link CameraManager#MINIMUM_VIDEO_DURATION}, video capture is cancelled
   * if video capture has not yet started, this method does nothing
   *
   * Calling this method after {@link #startVideoCapture(int)} returned {@code false}
   * is undefined behavior
   */
  public final void finishOrCancelVideoCapture () {
    onFinishOrCancelVideoCapture();
  }

  public final void onTakeMediaError (boolean isVideo) {
    if (!UI.inUiThread()) {
      UI.post(() -> onTakeMediaError(isVideo));
      return;
    }

    if (isVideo) {
      setTakingVideo(false, -1);
      UI.showToast(R.string.TakeVideoError, Toast.LENGTH_SHORT);
    } else {
      setTakingPhoto(false);
      UI.showToast(R.string.TakePhotoError, Toast.LENGTH_SHORT);
    }
  }

  public void onTakeMediaResult (final ImageGalleryFile resultFile, boolean isVideo) {
    if (!UI.inUiThread()) {
      UI.post(() -> onTakeMediaResult(resultFile, isVideo));
      return;
    }

    if (resultFile == null) {
      onTakeMediaError(isVideo);
      return;
    }

    if (!delegate.usePrivateFolder()) {
      U.addToGallery(new File(resultFile.getFilePath()));
    }

    if (isVideo) {
      setFinishingVideo(true);
      setTakingVideo(false, -1);
      UI.post(() -> {
        setFinishingVideo(false);
        delegate.onMediaTaken(resultFile);
      }, 200);
    } else {
      setTakingPhoto(false);
      delegate.onMediaTaken(resultFile);
    }
  }

  public final File getOutputFile (boolean isVideo) {
    final boolean isPrivate = delegate.usePrivateFolder();
    if (isVideo) {
      return U.generateVideoPath(isPrivate);
    } else {
      return U.generatePicturePath(isPrivate);
    }
  }

  /**
   * Called by UI, when picture has been instantly taken via {@link TextureView#getBitmap(Bitmap)}
   * */
  public boolean onInstantPhotoResult (final Bitmap in, int trimWidth, int trimHeight, final int rotation) {
    if (in == null) {
      onTakeMediaError(false);
      return false;
    }

    int dataWidth = in.getWidth();
    int dataHeight = in.getHeight();

    final File outFile = getOutputFile(false);
    if (outFile == null) {
      onTakeMediaError(false);
      return false;
    }

    Bitmap result = in;

    if (trimWidth > dataWidth || trimHeight > dataHeight) {
      float scale = Math.min((float) dataWidth / (float) trimWidth, (float) dataHeight / (float) trimHeight);
      trimWidth *= scale;
      trimHeight *= scale;
    }

    if (trimWidth != dataWidth || trimHeight != dataHeight) {
      int trimLeft = trimWidth != dataWidth ? (dataWidth - trimWidth) / 2 : 0;
      int trimTop = trimHeight != dataHeight ? (dataHeight - trimHeight) / 2 : 0;
      result = Bitmap.createBitmap(in, trimLeft, trimTop, trimWidth, trimHeight, null, false);
    }

    /*float dataRatio = (float) dataWidth / (float) dataHeight;
    float trimRatio = (float) Math.max(trimWidth, trimHeight) / (float) Math.min(trimWidth, trimHeight);
    float trimReverseRatio = (float) Math.min(trimWidth, trimHeight) / (float) Math.max(trimWidth, trimHeight);
    Bitmap result = in;

    int newWidth = dataWidth, newHeight = dataHeight;

    if (dataRatio != trimRatio) {
      if (dataRatio >= trimRatio) { // We need to trim the biggest side
        if (dataWidth >= dataHeight) {
          newHeight = dataHeight;
          newWidth = (int) (dataHeight * trimReverseRatio);
        } else {
          newWidth = dataWidth;
          newHeight = (int) (dataWidth * trimReverseRatio);
        }
      } else { // We need to trim the smallest side
        if (dataWidth <= dataHeight) {
          newHeight = dataHeight;
          newWidth = (int) (dataHeight * trimRatio);
        } else {
          newWidth = dataWidth;
          newHeight = (int) (dataWidth * trimRatio);
        }
      }
    }

    if (newWidth != dataWidth || newHeight != dataHeight) {
      int trimLeft = newWidth != dataWidth ? (dataWidth - newWidth) / 2 : 0;
      int trimTop = newHeight != dataHeight ? (dataHeight - newHeight) / 2 : 0;
      result = Bitmap.createBitmap(in, trimLeft, trimTop, newWidth, newHeight, null, false);
    }*/

    /*int width = in.getWidth();
    int height = in.getHeight();


    if (textureWidth != trimWidth) {
      float scale = (float) trimWidth / (float) textureWidth;
      int newWidth = (int) (width * scale);
      if (newWidth != width) {
        int trimLeft = (width - newWidth) / 2;
        result = Bitmap.createBitmap(in, trimLeft, 0, newWidth, height);
        width = newWidth;
      }
    } else if (textureHeight != trimHeight) {
      float scale = (float) trimHeight / (float) textureHeight;
      int newHeight = (int) (height * scale);
      if (newHeight != height) {
        int trimTop = (height - newHeight) / 2;
        result = Bitmap.createBitmap(in, 0, trimTop, width, newHeight);
        height = newHeight;
      }
    }*/

    final ImageGalleryFile resultFile = new ImageGalleryFile(-1, outFile.getPath(), System.currentTimeMillis(), result.getWidth(), result.getHeight(), -1, false);
    resultFile.setRotation(rotation);
    resultFile.setFromCamera();
    resultFile.setNoCache();
    resultFile.setNoReference();
    ImageStrictCache.instance().put(resultFile, result);

    final Bitmap resultFinal = result;
    ImageReader.instance().post(() -> {
      if (U.compress(resultFinal, 100, outFile.getPath())) {
        if (rotation != 0) {
          int value = orientationToExifRotation(rotation);
          try {
            ExifInterface exif = new ExifInterface(outFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(value));
            exif.saveAttributes();
          } catch (Throwable t) {
            Log.w(Log.TAG_CAMERA, "Unable to set exif orientation: %d", t, value);
          }
        }
        resultFile.setReady();
      }
    });

    onTakeMediaResult(resultFile, false);

    return result == in;
  }

  private static int orientationToExifRotation (int orientation) {
    switch (orientation) {
      case 90:
        return ExifInterface.ORIENTATION_ROTATE_90;
      case 180:
        return ExifInterface.ORIENTATION_ROTATE_180;
      case 270:
        return ExifInterface.ORIENTATION_ROTATE_270;
    }
    return ExifInterface.ORIENTATION_UNDEFINED;
  }// Bitmap

  private final ArrayList<Bitmap> bitmaps = new ArrayList<>(); // List of bitmaps that are not used right now

  @UiThread
  private Bitmap obtainBitmap (int width, int height) {
    Bitmap targetBitmap = null;
    Bitmap previewBitmap = null;
    synchronized (this) {
      if (!bitmaps.isEmpty()) {
        int i = 0;
        for (Bitmap bitmap : bitmaps) {
          if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
            previewBitmap = bitmap;
            bitmaps.remove(i);
            break;
          }
          i++;
        }
      }
    }
    try {
      if (previewBitmap != null && !previewBitmap.isRecycled()) {
        if (previewBitmap.getWidth() == width && previewBitmap.getHeight() == height) {
          targetBitmap = previewBitmap;
        } else {
          previewBitmap.recycle();
        }
      }
      if (targetBitmap == null) {
        targetBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      }
    } catch (Throwable t) {
      Log.e(t);
    }
    return targetBitmap;
  }

  /**
   * Called when new preview bitmap has replaced an old one.
   * */
  public void releaseBitmapOwnership (Bitmap bitmap) {
    if (U.isValidBitmap(bitmap)) {
      synchronized (this) {
        bitmaps.add(bitmap);
      }
    }
  }

  public boolean takeBitmapOwnership (Bitmap bitmap) {
    synchronized (this) {
      return U.isValidBitmap(bitmap) && bitmaps.remove(bitmap);
    }
  }

  protected abstract boolean getBitmap (Bitmap bitmap);

  private static final int PREVIEW_SIZE = 160, OUTPUT_SIZE = 1280;
  private Bitmap previewBitmap, outputBitmap;
  private int textureWidth, textureHeight;

  public void prepareBitmaps (int width, int height) {
    this.textureWidth = width;
    this.textureHeight = height;
    if (width > 0 && height > 0) {
      preparePreviewBitmap(width, height);
      prepareOutputBitmap(width, height);
    }
  }

  private void preparePreviewBitmap (int width, int height) {
    float scale = Math.min((float) PREVIEW_SIZE / (float) width, (float) PREVIEW_SIZE / (float) height);
    width *= scale;
    height *= scale;

    if (previewBitmap != null && !previewBitmap.isRecycled() && previewBitmap.getWidth() == width && previewBitmap.getHeight() == height) {
      return;
    }

    Bitmap newBitmap = obtainBitmap(width, height);
    if (previewBitmap != null && !previewBitmap.isRecycled()) {
      releaseBitmapOwnership(previewBitmap);
    }
    previewBitmap = newBitmap;
  }

  private void prepareOutputBitmap (int width, int height) {
    if (outputInUse) {
      return;
    }
    float scale = Math.min(1f, Math.min((float) OUTPUT_SIZE / (float) width, (float) OUTPUT_SIZE / (float) height));
    width *= scale;
    height *= scale;
    if (outputBitmap != null && outputBitmap.getWidth() == width && outputBitmap.getHeight() == height) {
      return;
    }
    Bitmap oldBitmap = outputBitmap;
    boolean reused = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && oldBitmap != null && !outputInUse) {
      try {
        oldBitmap.reconfigure(width, height, Bitmap.Config.ARGB_8888);
        reused = true;
      } catch (Throwable ignored) { }
    }
    Bitmap newBitmap;
    if (reused) {
      newBitmap = oldBitmap;
      oldBitmap = null;
    } else {
      newBitmap = obtainBitmap(width, height);
    }
    if (oldBitmap != null && !oldBitmap.isRecycled()) {
      releaseBitmapOwnership(oldBitmap);
    }
    outputBitmap = newBitmap;
  }

  @UiThread
  public final Bitmap takeBlurredPreview () {
    if (!U.isValidBitmap(previewBitmap)) { // || !isAvailable()
      return null;
    }
    try {
      previewBitmap.eraseColor(0);
      if (getBitmap(previewBitmap) && previewBitmap.getPixel(0, 0) != 0 && (shouldIgnorePreviewBlur() || U.blurBitmap(previewBitmap, 3, 1))) {
        return previewBitmap;
      }
    } catch (Throwable t) {
      Log.w(Log.TAG_CAMERA, "Cannot take bitmap", t);
    }
    return null;
  }

  public boolean canTakeSnapshot () {
    return U.isValidBitmap(outputBitmap);
  }

  private boolean outputInUse;

  public Bitmap takeSnapshot () {
    prepareOutputBitmap(textureWidth, textureHeight);
    if (outputBitmap == null) {
      return null;
    }
    getBitmap(outputBitmap);
    if (outputBitmap.getPixel(0, 0) == 0) {
      return null;
    }
    outputInUse = true;
    Bitmap result = outputBitmap;
    outputBitmap = null;
    return result;
  }

  // Called when Bitmap control returned back. e.g. when user takes photo and presses back
  public void onSnapshotControlReturned (Bitmap bitmap) {
    if (bitmap == null || bitmap.isRecycled()) {
      onSnapshotControlLost();
      return;
    }
    if (outputInUse) {
      bitmap.eraseColor(0);
      outputInUse = false;
      outputBitmap = bitmap;
      prepareOutputBitmap(textureWidth, textureHeight);
    }
  }

  public void onSnapshotControlLost () {
    if (outputInUse) {
      outputInUse = false;
      prepareOutputBitmap(textureWidth, textureHeight);
    }
  }
}
