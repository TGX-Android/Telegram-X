/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/09/2015 at 03:14
 */
package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.CancellationSignal;
import androidx.core.view.GestureDetectorCompat;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifActor;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.crop.CropState;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.mediaview.gl.EGLEditorView;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.FileProgressComponent;
import org.thunderdog.challegram.widget.ForceTouchView;
import org.thunderdog.challegram.widget.SparseDrawableView;

import java.io.File;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class MediaCellView extends ViewGroup implements
  MediaCellViewDetector.Callback,
  Runnable,
  ImageFile.RotationListener,
  FileProgressComponent.SimpleListener,
  VideoPlayerView.Callback,
  Destroyable,
  ForceTouchView.StateListener,
  FactorAnimator.Target {
  private static final float SCALE_FACTOR = .25f;
  private static final int ZOOM_DURATION = 300;

  private @Nullable MediaItem media;
  private float factor; // 0f - normal, 1f - out of screen, -1f - fade out

  private final ImageReceiver imageReceiver;
  private final GifReceiver gifReceiver;
  private final AvatarReceiver avatarReceiver;
  private final ImageReceiver imagePreviewReceiver;
  private final ImageReceiver miniThumbnail;
  private Receiver preview;
  private Receiver receiver;

  private MediaCellViewDetector detector;

  private ForegroundView overlayView;
  private CellImageView imageView;
  private @Nullable VideoPlayerView playerView;
  private CellButtonView buttonView;
  private CellVideoView videoParentView;
  private BufferingProgressBarWrap bufferingProgressView;

  private final ClippingSubsamplingImageView subsamplingImageView;

  private class ForegroundView extends View {
    public ForegroundView (Context context) {
      super(context);
    }

    private float backgroundAlpha;

    public void setBackgroundAlpha (float alpha) {
      if (this.backgroundAlpha != alpha) {
        this.backgroundAlpha = alpha;
        invalidate();
      }
    }

    @Override
    protected void onDraw (Canvas c) {
      if (receiver != null && imageAlpha != 0f && revealFactor != 0f) {
        c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(ColorUtils.color((int) (255f * backgroundAlpha), forceTouchMode ? 0xffffffff : 0)));
      }
    }
  }

  private Runnable resetZoomRunnable;

  public MediaCellView (Context context) {
    super(context);

    this.detector = new MediaCellViewDetector(context, this, this);

    this.imageView = new CellImageView(context);
    this.imageView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    this.subsamplingImageView = new ClippingSubsamplingImageView(context) {
      @SuppressWarnings("ClickableViewAccessibility")
      @Override
      public boolean onTouchEvent (@NonNull MotionEvent event) {
        if (!subsamplingModeEnabled || !isReady()) {
          return false;
        }
        MediaView mediaView = (MediaView) MediaCellView.this.getParent();
        mediaView.setIgnoreDisallowInterceptTouchEvent(true);
        boolean res = super.onTouchEvent(event);
        mediaView.setIgnoreDisallowInterceptTouchEvent(false);
        return res;
      }
    };
    this.subsamplingImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
    this.subsamplingImageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
    this.subsamplingImageView.setMaxScale(Float.MAX_VALUE);
    this.subsamplingImageView.setDoubleTapZoomScale(1f);
    this.subsamplingImageView.setDoubleTapZoomDuration(ZOOM_DURATION);
    final GestureDetectorCompat gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent e) {
        if (subsamplingModeEnabled && subsamplingImageView.isReady() && canTouch(false)) {
          subsamplingImageView.performClick();
          ((MediaView) getParent()).onMediaClick(e.getX(), e.getY());
          return true;
        }
        return false;
      }
    });
    //noinspection ClickableViewAccessibility
    this.subsamplingImageView.setOnTouchListener((view, event) -> gestureDetector.onTouchEvent(event));
    this.subsamplingImageView.setOnStateChangedListener(new SubsamplingScaleImageView.DefaultOnStateChangedListener() {
      @Override
      public void onScaleChanged (float newScale, int origin) {
        if (origin == SubsamplingScaleImageView.ORIGIN_DOUBLE_TAP_ZOOM || origin == SubsamplingScaleImageView.ORIGIN_ANIM) {
          if (resetZoomRunnable != null) {
            UI.removePendingRunnable(resetZoomRunnable);
          } else {
            resetZoomRunnable = () -> {
              if (subsamplingImageView.isReady() && subsamplingImageView.getScale() != subsamplingImageView.getMinScale()) {
                float difference = subsamplingImageView.getScale() - subsamplingImageView.getMinScale();
                int widthDiff = (int) (subsamplingImageView.getSWidth() * difference);
                int heightDiff = (int) (subsamplingImageView.getSHeight() * difference);
                if (widthDiff == 0 && heightDiff == 0) {
                  subsamplingImageView.resetScaleAndCenter();
                }
              }
            };
          }
          UI.post(resetZoomRunnable, 280l);
        } else {
          if (resetZoomRunnable != null) {
            UI.removePendingRunnable(resetZoomRunnable);
          }
        }
      }
    });
    this.subsamplingImageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    this.subsamplingImageView.setAlpha(0f);
    this.subsamplingImageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
      @Override
      public void onReady () {
        subsamplingImageView.setMaxScale(Math.max(subsamplingImageView.getMinScale() * 2f, Math.max(1f, Screen.density()) * 3f));
        subsamplingImageView.setDoubleTapZoomScale(Math.max(subsamplingImageView.getMinScale() * 1.5f, 1f));
        if (subsamplingModeEnabled) {
          setSubsamplingImageLoaded(true);
        }
      }
    });

    this.imageReceiver = new ImageReceiver(imageView, 0);
    this.imageReceiver.prepareToBeCropped();
    this.gifReceiver = new GifReceiver(imageView);
    this.avatarReceiver = new AvatarReceiver(imageView);
    this.avatarReceiver.setDisplayFullSizeOnlyInFullScreen(true);
    this.avatarReceiver.setScaleMode(AvatarReceiver.ScaleMode.FIT_CENTER);
    this.avatarReceiver.setFullScreen(true, false);
    this.imagePreviewReceiver = new ImageReceiver(imageView, 0);
    this.imagePreviewReceiver.prepareToBeCropped();
    this.miniThumbnail = new ImageReceiver(imageView, 0);
    this.miniThumbnail.prepareToBeCropped();

    FrameLayoutFix imageViewParent = new FrameLayoutFix(context);
    imageViewParent.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    imageViewParent.addView(imageView);
    imageViewParent.addView(subsamplingImageView);

    this.receiver = imageReceiver;
    this.preview = imagePreviewReceiver;

    addView(imageViewParent);

    this.overlayView = new ForegroundView(context);
    this.overlayView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(overlayView);

    this.buttonView = new CellButtonView(context);
    this.buttonView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(buttonView);

    this.bufferingProgressView = new BufferingProgressBarWrap(context);
    this.bufferingProgressView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(bufferingProgressView);
  }

  private boolean subsamplingModeEnabled, subsamplingImageLoaded;

  private static void recycle (SubsamplingScaleImageView imageView) {
    imageView.recycle();
    imageView.setMaxScale(Float.MAX_VALUE);
    imageView.setDoubleTapZoomScale(1f);
  }

  private void setSubsamplingModeEnabled (boolean isEnabled) {
    if (this.subsamplingModeEnabled != isEnabled) {
      this.subsamplingModeEnabled = isEnabled;
      if (!isEnabled) {
        setSubsamplingImageLoaded(false);
        recycle(subsamplingImageView);
        if (subsamplingLoadSignal != null) {
          subsamplingLoadSignal.cancel();
          subsamplingLoadSignal = null;
        }
      }
    }
  }

  private void setSubsamplingImageLoaded (boolean isLoaded) {
    if (this.subsamplingImageLoaded != isLoaded) {
      this.subsamplingImageLoaded = isLoaded;
      subsamplingImageView.setAlpha(isLoaded || forceTouchMode ? 1f : 0f);
      if (isLoaded) {
        imageView.invalidate();
      }
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    final int childCount = getChildCount();

    final int parentWidth = getMeasuredWidth();
    final int parentHeight = getMeasuredHeight();

    final int availWidth = fullWidth - offsetHorizontal * 2;
    final int availHeight = fullHeight - offsetBottom;
    int imageWidth, imageHeight;
    int imageWidthCropped, imageHeightCropped;
    if (media != null) {
      if (media.isVideo() && media.isRotated()) {
        imageWidth = media.getHeight();
        imageHeight = media.getWidth();
      } else {
        imageWidth = media.getWidth();
        imageHeight = media.getHeight();
      }
    } else {
      imageWidth = imageHeight = 0;
    }
    if (imageWidth == 0 || imageHeight == 0) {
      imageWidth = availWidth;
      imageHeight = availHeight;
      imageWidthCropped = availWidth;
      imageHeightCropped = availHeight;
    } else {
      float ratio;

      CropState cropState = media.getCropState();
      if (cropState != null && !cropState.isRegionEmpty()) {
        double width = cropState.getRight() - cropState.getLeft();
        double height = cropState.getBottom() - cropState.getTop();
        imageWidthCropped = (int) ((double) imageWidth * width);
        imageHeightCropped = (int) ((double) imageHeight * height);
      } else {
        imageWidthCropped = imageWidth;
        imageHeightCropped = imageHeight;
      }

      ratio = Math.min((float) availWidth / (float) imageWidth, (float) availHeight / (float) imageHeight);
      imageWidth *= ratio;
      imageHeight *= ratio;

      ratio = Math.min((float) availWidth / (float) imageWidthCropped, (float) availHeight / (float) imageHeightCropped);
      imageWidthCropped *= ratio;
      imageHeightCropped *= ratio;
    }


    int centerX = offsetHorizontal + availWidth / 2;
    int centerY = availHeight / 2;

    int exactLeft = centerX - imageWidth / 2;
    int exactRight = centerX + imageWidth / 2;
    int exactTop = centerY - imageHeight / 2;
    int exactBottom = centerY + imageHeight / 2;

    int exactLeftCropped = centerX - imageWidthCropped / 2;
    int exactRightCropped = centerX + imageWidthCropped / 2;
    int exactTopCropped = centerY - imageHeightCropped / 2;
    int exactBottomCropped = centerY + imageHeightCropped / 2;

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      ViewGroup.LayoutParams params = view.getLayoutParams();
      if (view instanceof EGLEditorView) {
        ((EGLEditorView) view).setCenter(centerX, centerY);
        view.layout(0, 0, parentWidth, parentHeight);
      } else if (params != null && params.width == LayoutParams.WRAP_CONTENT && params.height == LayoutParams.WRAP_CONTENT && !forceTouchMode) {
        view.layout(exactLeft, exactTop, exactRight, exactBottom);
      } else {
        view.layout(0, 0, parentWidth, parentHeight);
      }
    }

    zoomComponents();
    checkPostRotation(false);
  }

  private boolean forceTouchMode;
  private ForceTouchView.ForceTouchContext forceTouchContext;

  public void setBoundForceTouchContext (ForceTouchView.ForceTouchContext context) {
    this.forceTouchMode = true;
    this.forceTouchContext = context;
    this.subsamplingImageView.setAlpha(1f);
  }

  private boolean enableEarlyLoad;

  public void setEnableEarlyLoad () {
    enableEarlyLoad = true;
  }

  @Override
  public void onPrepareToEnterForceTouch (ForceTouchView.ForceTouchContext context) {
    if (enableEarlyLoad) {
      loadMedia(false, 1f);
    }
  }

  @Override
  public void onPrepareToExitForceTouch (ForceTouchView.ForceTouchContext context) {
    pauseIfPlaying();
  }

  @Override
  public void onCompletelyShownForceTouch (ForceTouchView.ForceTouchContext context) {
    if (!enableEarlyLoad) {
      loadMedia(false, 1f);
    }
    autoplayIfNeeded(false);
  }

  @Override
  public void onDestroyForceTouch (ForceTouchView.ForceTouchContext context) { }

  /*@Override
  public int getOffsetHorizontal () {
    return offsetHorizontal;
  }

  @Override
  public int getOffsetTop () {
    return offsetTop;
  }

  @Override
  public int getOffsetBottom () {
    return offsetBottom;
  }*/

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (forceTouchMode && media != null) {
      final int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
      final int maxHeight = MeasureSpec.getSize(heightMeasureSpec) - (forceTouchContext.hasFooter() ? Screen.dp(56f) : 0)/* - (forceTouchContext.hasHeader() ? Screen.dp(56f) : 0)*/;
      final int minWidth = forceTouchContext.getMinimumWidth();

      int photoWidth = media.getWidth();
      int photoHeight = media.getHeight();

      float ratio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);
      photoWidth *= ratio;
      photoHeight *= ratio;

      widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(minWidth, photoWidth), MeasureSpec.EXACTLY);
      heightMeasureSpec = MeasureSpec.makeMeasureSpec(photoHeight, MeasureSpec.EXACTLY);

      setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
      layoutReceivers();
      measureChildren(widthMeasureSpec, heightMeasureSpec);
      checkRotationAndScale();
      return;
    }

    setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

    final int childCount = getChildCount();

    final int availWidth = fullWidth - offsetHorizontal * 2;
    final int availHeight = fullHeight - offsetBottom;
    int imageWidth, imageHeight;
    int imageWidthCropped, imageHeightCropped;
    if (media != null) {
      if (media.isVideo() && media.isRotated()) {
        imageWidth = media.getHeight();
        imageHeight = media.getWidth();
      } else {
        imageWidth = media.getWidth();
        imageHeight = media.getHeight();
      }
    } else {
      imageWidth = imageHeight = 0;
    }
    if (imageWidth == 0 || imageHeight == 0) {
      imageWidth = availWidth;
      imageHeight = availHeight;
      imageWidthCropped = availWidth;
      imageHeightCropped = availHeight;
    } else {
      float ratio;

      CropState cropState = media.getCropState();
      if (cropState != null && !cropState.isRegionEmpty()) {
        double width = cropState.getRight() - cropState.getLeft();
        double height = cropState.getBottom() - cropState.getTop();
        imageWidthCropped = (int) ((double) imageWidth * width);
        imageHeightCropped = (int) ((double) imageHeight * height);
      } else {
        imageWidthCropped = imageWidth;
        imageHeightCropped = imageHeight;
      }

      ratio = Math.min((float) availWidth / (float) imageWidth, (float) availHeight / (float) imageHeight);
      imageWidth *= ratio;
      imageHeight *= ratio;

      ratio = Math.min((float) availWidth / (float) imageWidthCropped, (float) availHeight / (float) imageHeightCropped);
      imageWidthCropped *= ratio;
      imageHeightCropped *= ratio;
    }

    for (int i = 0; i < childCount; i++) {
      View view = getChildAt(i);
      if (view instanceof EGLEditorView) {
        ((EGLEditorView) view).setViewSizes(imageWidth, imageHeight, imageWidthCropped, imageHeightCropped);
        measureChild(view, widthMeasureSpec, heightMeasureSpec);
      } else {
        LayoutParams params = view.getLayoutParams();
        if (params != null && params.width == LayoutParams.WRAP_CONTENT && params.height == LayoutParams.WRAP_CONTENT) {
          int w, h;
          w = imageWidth;
          h = imageHeight;
          measureChild(view, MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        } else {
          measureChild(view, widthMeasureSpec, heightMeasureSpec);
        }
      }
    }

    zoomComponents();
    checkPostRotation(false);
  }

  private class CellVideoView extends ViewGroup {
    public CellVideoView (Context context) {
      super(context);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);

      final int childCount = getChildCount();

      final int availWidth = fullWidth - offsetHorizontal * 2;
      final int availHeight = fullHeight - offsetBottom;
      int imageWidth, imageHeight;
      if (media != null) {
        if (media.isVideo() && media.isRotated()) {
          imageWidth = media.getHeight();
          imageHeight = media.getWidth();
        } else {
          imageWidth = media.getWidth();
          imageHeight = media.getHeight();
        }
      } else {
        imageWidth = imageHeight = 0;
      }
      if (imageWidth == 0 || imageHeight == 0) {
        imageWidth = availWidth;
        imageHeight = availHeight;
      } else {
        float ratio;

        ratio = Math.min((float) availWidth / (float) imageWidth, (float) availHeight / (float) imageHeight);
        imageWidth *= ratio;
        imageHeight *= ratio;
      }

      for (int i = 0; i < childCount; i++) {
        View view = getChildAt(i);
        LayoutParams params = view.getLayoutParams();
        if (params != null && params.width == LayoutParams.WRAP_CONTENT && params.height == LayoutParams.WRAP_CONTENT) {
          int w, h;
          w = imageWidth;
          h = imageHeight;
          measureChild(view, MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        } else {
          measureChild(view, widthMeasureSpec, heightMeasureSpec);
        }
      }
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      final int childCount = getChildCount();

      final int parentWidth = getMeasuredWidth();
      final int parentHeight = getMeasuredHeight();

      final int availWidth = fullWidth - offsetHorizontal * 2;
      final int availHeight = fullHeight - offsetBottom;
      int imageWidth, imageHeight;
      if (media != null) {
        if (media.isVideo() && media.isRotated()) {
          imageWidth = media.getHeight();
          imageHeight = media.getWidth();
        } else {
          imageWidth = media.getWidth();
          imageHeight = media.getHeight();
        }
      } else {
        imageWidth = imageHeight = 0;
      }
      if (imageWidth == 0 || imageHeight == 0) {
        imageWidth = availWidth;
        imageHeight = availHeight;
      } else {
        final float ratio = Math.min((float) availWidth / (float) imageWidth, (float) availHeight / (float) imageHeight);
        imageWidth *= ratio;
        imageHeight *= ratio;
      }
      int centerX = offsetHorizontal + availWidth / 2;
      int centerY = availHeight / 2;
      int exactLeft = centerX - imageWidth / 2;
      int exactRight = centerX + imageWidth / 2;
      int exactTop = centerY - imageHeight / 2;
      int exactBottom = centerY + imageHeight / 2;

      for (int i = 0; i < childCount; i++) {
        View view = getChildAt(i);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.width == LayoutParams.WRAP_CONTENT && params.height == LayoutParams.WRAP_CONTENT && !forceTouchMode) {
          view.layout(exactLeft, exactTop, exactRight, exactBottom);
        } else {
          view.layout(0, 0, parentWidth, parentHeight);
        }
      }

      if (scheduledAnimation) {
        scheduledAnimation = false;
        rotationAnimator.animateTo(0f);
      }
    }
  }

  private boolean destroyed;

  public void destroy () {
    setMedia(null);
    setSubsamplingModeEnabled(false);
    recycle(subsamplingImageView);
    clearImage();
    bufferingProgressView.performDestroy();
    imageReceiver.destroy();
    gifReceiver.destroy();
    imagePreviewReceiver.destroy();
    miniThumbnail.destroy();
    avatarReceiver.destroy();
    if (playerView != null) {
      playerView.destroy();
    }
    destroyed = true;
  }

  public void checkTrim (boolean invalidateFrame) {
    if (playerView != null && playerView.checkTrim()) {
      timeNow = timeTotal = -1;
    }
    if (invalidateFrame && media != null && media.isVideo() && !media.isGifType()) {
      final MediaItem mediaItem = media;
      ImageFile file = mediaItem.getTargetImageFile(true);
      ImageLoader.instance().loadFile(file, (success, result) -> UI.post(() -> {
        if (media == mediaItem) {
          imagePreviewReceiver.requestFile(media.getPreviewImageFile());
          requestImage(media, file);
        }
      }));
    }
  }

  public void onCellActivityResume () {
    if (playerView != null) {
      playerView.attach();
    }
  }

  public void onCellActivityPause () {
    if (playerView != null) {
      playerView.detach();
    }
  }

  // Thumb animation stuff

  private MediaViewThumbLocation thumb;

  public void setTargetLocation (MediaViewThumbLocation thumb) {
    this.thumb = thumb;
  }

  private FactorAnimator targetAnimator;

  public void setTargetAnimator (FactorAnimator animator) {
    if (!receiver.needPlaceholder()) {
      targetAnimator = null;
      animator.animateTo(1f);
    } else {
      targetAnimator = animator;
      UI.post(this, 134l);
    }
  }

  @Override
  public void run () {
    if (targetAnimator != null) {
      targetAnimator.animateTo(1f);
      targetAnimator = null;
    }
  }

  private float revealFactor = 1f;

  public void setRevealFactor (float revealFactor) {
    if (this.revealFactor != revealFactor) {
      this.revealFactor = revealFactor;
      if (media != null) {
        float alpha = MathUtils.clamp(revealFactor);
        if ((media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && media.isVideo() && media.isRemoteVideo()) {
          if (disappearing) {
            float diff = 1f - fromComponentsAlpha;
            media.setComponentsAlpha(fromComponentsAlpha + diff * (1f - alpha));
          } else {
            media.setComponentsAlpha(1f - alpha);
          }
        } else {
          media.setComponentsAlpha(alpha);
        }
      }
      layoutReceivers();
      if (disappearing && media != null && MediaItem.isGalleryType(media.getType())) {
        receiver.setAlpha(Math.max(0f, Math.min(1f, revealFactor)));
      }
      invalidateImage();
    }
  }

  @Override
  public void performDestroy () {
    destroy();
  }

  public void setDisableAnimations (boolean disable) {
    receiver.setAnimationDisabled(disable);
    preview.setAnimationDisabled(disable);
    miniThumbnail.setAnimationDisabled(disable);
  }

  private boolean disappearing;
  private float fromComponentsAlpha;

  public boolean isPlaying () {
    return isPlaying;
  }

  public void setDisappearing (boolean disappearing) {
    this.disappearing = disappearing;
    if (disappearing) {
      if (isPlaying && media != null) {
        media.setComponentsAlpha(0f);
      }
      fromComponentsAlpha = media != null ? media.getComponentsAlpha() : 0f;
      setHideStaticView(false, true);
    }
  }

  // Animator utils

  private float imageAlpha = 1f;

  public void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;

      float alpha = factor < 0f ? 1f + factor : 1f;
      if (this.imageAlpha != alpha) {
        this.imageAlpha = alpha;
        this.overlayView.setBackgroundAlpha(alpha == 0f ? 0f : 1f - alpha);
        invalidateImageComponents();
      }

      if (factor < 0f) {
        float scale = 1f + (SCALE_FACTOR * factor);
        setScaleX(scale);
        setScaleY(scale);
        setTranslationX(0f);
      } else if (factor > 0f) {
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX((int) ((float) fullWidth * factor * (Lang.rtl() ? 1f : -1f)));
      } else {
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
      }

      checkVisibility();
    }
  }

  private void checkVisibility () {
    int visibility = Math.abs(factor) == 1f ? View.INVISIBLE : View.VISIBLE;
    if (getVisibility() != visibility) {
      setVisibility(visibility);
      /*if (playerView != null && media.isLoaded() && media.isVideo()) {
        // playerView.setVideo(visibility == View.VISIBLE ? media : null);
      }*/
    }
  }

  public float getFactor () {
    return factor;
  }

  // Other

  private int offsetTop, offsetBottom, offsetHorizontal;
  private int fullWidth, fullHeight;

  public void layoutCell (int offsetLeft, int offsetTop, int offsetBottom, int width, int height) {
    this.fullWidth = width;
    this.fullHeight = height;

    this.offsetHorizontal = offsetLeft;
    this.offsetTop = offsetTop;
    this.offsetBottom = offsetBottom;

    layoutReceivers();
  }

  public void setOffsets (int offsetLeft, int offsetTop, int offsetBottom) {
    if (this.offsetHorizontal != offsetLeft || this.offsetTop != offsetTop || this.offsetBottom != offsetBottom) {
      this.offsetHorizontal = offsetLeft;
      this.offsetTop = offsetTop;
      this.offsetBottom = offsetBottom;

      layoutReceivers();
      invalidateImage();

      if (media != null && media.isVideo() && hideStaticView && playerView != null) {
        playerView.requestLayout();
      }
    }
  }

  private int clipVertical, clipHorizontal;

  private void layoutReceivers () {
    layoutReceivers(false);
  }

  private void setImageRadius (int radius, float revealFactor) {
    if (receiver instanceof ImageReceiver) {
      ((ImageReceiver) receiver).setRadius(radius);
    }
    if (preview instanceof ImageReceiver) {
      ((ImageReceiver) preview).setRadius(radius);
    }
    miniThumbnail.setRadius(radius);
    if (radius == 0) {
      avatarReceiver.forceFullScreen(true, 1f);
    } else {
      avatarReceiver.forceFullScreen(revealFactor != 0f, MathUtils.clamp(revealFactor));
    }
  }

  private void layoutReceivers (boolean forceLayout) {
    if (forceTouchMode && media != null) {
      int photoWidth = media.getWidth();
      int photoHeight = media.getHeight();

      final int maxWidth = getMeasuredWidth();
      final int maxHeight = getMeasuredHeight();

      final float ratio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);

      photoWidth *= ratio;
      photoHeight *= ratio;

      int left = maxWidth / 2 - photoWidth / 2;
      int right = left + photoWidth;
      int top = maxHeight / 2 - photoHeight / 2;
      int bottom = top + photoHeight;

      if (!preview.setBounds(left, top, right, bottom) && forceLayout) {
        preview.forceBoundsLayout();
      }
      if (!miniThumbnail.setBounds(left, top, right, bottom) && forceLayout) {
        miniThumbnail.forceBoundsLayout();
      }
      if (!receiver.setBounds(left, top, right, bottom) && forceLayout) {
        receiver.forceBoundsLayout();
      }

      setPivotX((left + right) / 2f);
      setPivotY((top + bottom) / 2f);

      return;
    }

    if (thumb == null || revealFactor == 1f || media == null) {
      if (!receiver.setBounds(offsetHorizontal, offsetTop, fullWidth - offsetHorizontal, fullHeight - offsetBottom) && forceLayout) {
        receiver.forceBoundsLayout();
      }
      if (!preview.setBounds(offsetHorizontal, offsetTop, fullWidth - offsetHorizontal, fullHeight - offsetBottom) && forceLayout) {
        preview.forceBoundsLayout();
      }
      if (!miniThumbnail.setBounds(offsetHorizontal, offsetTop, fullWidth - offsetHorizontal, fullHeight - offsetBottom) && forceLayout) {
        miniThumbnail.forceBoundsLayout();
      }

      setPivotX(receiver.centerX());
      setPivotY(receiver.centerY());

      setImageRadius(0, 1.0f);

      subsamplingImageView.setScaleX(1f);
      subsamplingImageView.setScaleY(1f);
      subsamplingImageView.setTranslationX(0f);
      subsamplingImageView.setTranslationY(0f);
      subsamplingImageView.resetClipping();
    } else {
      int fromLeft = thumb.left; // + 1;
      int fromTop = thumb.top; // + 1;
      int fromRight = thumb.right;
      int fromBottom = thumb.bottom;

      int imageWidth, imageHeight;

      if ((media.isVideo() && media.isFinallyRotated()) /*|| (!media.isVideo() && Utils.isRotated(media.getCropRotateBy()))*/) {
        imageWidth = media.getHeight();
        imageHeight = media.getWidth();
      } else {
        imageWidth = media.getWidth();
        imageHeight = media.getHeight();
      }

      clipHorizontal = clipVertical = 0;

      int fromWidth = fromRight - fromLeft;
      int fromHeight = fromBottom - fromTop;

      float ratio = Math.max((float) fromWidth / (float) imageWidth, (float) fromHeight / (float) imageHeight);
      if (ratio != 1f) {
        int actualFromWidth = (int) ((float) imageWidth * ratio);
        int actualFromHeight = (int) ((float) imageHeight * ratio);

        int diffWidth = (actualFromWidth - fromWidth) / 2;
        int diffHeight = (actualFromHeight - fromHeight) / 2;

        clipHorizontal = (int) ((float) diffWidth * MathUtils.clamp(1f - revealFactor));
        clipVertical = (int) ((float) diffHeight * MathUtils.clamp(1f - revealFactor));
      }

      int left, top, right, bottom;

      if (revealFactor >= 0f) {
        left = fromLeft + (int) ((float) (offsetHorizontal - fromLeft) * revealFactor) - clipHorizontal;
        top = fromTop + (int) ((float) (offsetTop - fromTop) * revealFactor) - clipVertical;
        right = fromRight + (int) ((float) (fullWidth - offsetHorizontal - fromRight) * revealFactor) + clipHorizontal;
        bottom = fromBottom + (int) ((float) (fullHeight - offsetBottom - fromBottom) * revealFactor) + clipVertical;
      }/* else if (true) {
        left = fromLeft;
        top = fromTop;
        right = fromRight;
        bottom = fromBottom;
      }*/ else {
        // TODO better

        int centerX = thumb.centerX();
        int centerY = thumb.centerY();

        int width = (fromRight - fromLeft) + (int) ((float) (fromRight - fromLeft) * revealFactor);
        int height = (fromBottom - fromTop) + (int) ((float) (fromBottom - fromTop) * revealFactor);

        left = centerX - width / 2 - clipHorizontal;
        top = centerY - height / 2 - clipVertical;
        right = centerX + width / 2 + clipHorizontal;
        bottom = centerY + height / 2 + clipVertical;
      }

      int radius = imageWidth != imageHeight ? 0 : (int) ((float) thumb.getRadius() * (1f - MathUtils.clamp(revealFactor)));
      setImageRadius(radius, revealFactor);

      if (!receiver.setBounds(left, top, right, bottom) && forceLayout) {
        receiver.forceBoundsLayout();
      }
      if (!preview.setBounds(left, top, right, bottom) && forceLayout) {
        preview.forceBoundsLayout();
      }
      if (!miniThumbnail.setBounds(left, top, right, bottom) && forceLayout) {
        miniThumbnail.forceBoundsLayout();
      }

      float width = right - left;
      float height = bottom - top;
      float centerX = (left + right) / 2f;
      float centerY = (top + bottom) / 2f;

      setPivotX(centerX);
      setPivotY(centerY);

      int targetLeft = offsetHorizontal;
      int targetTop = offsetTop;
      int targetRight = fullWidth - offsetHorizontal;
      int targetBottom = fullHeight - offsetBottom;

      int targetWidth = targetRight - targetLeft;
      int targetHeight = targetBottom - targetTop;

      float targetCenterX = (targetLeft + targetRight) / 2f;
      float targetCenterY = (targetTop + targetBottom) / 2f;

      float scaleX = targetWidth != 0 ? width / targetWidth : 1f;
      float scaleY = targetHeight != 0 ? height / targetHeight : 1f;
      float scale = Math.max(scaleX, scaleY);

      subsamplingImageView.setScaleX(scale);
      subsamplingImageView.setScaleY(scale);

      subsamplingImageView.setTranslationX(centerX - targetCenterX);
      subsamplingImageView.setTranslationY(centerY - targetCenterY);

      float clipHorizontal = Math.max(targetWidth * scale - width, 0) / 2f / scale;
      float clipVertical = Math.max(targetHeight * scale - height, 0) / 2f / scale;

      final float clipFactor = MathUtils.clamp(1f - revealFactor);
      float clipTop = (float) (thumb.clipTop + this.clipVertical) * clipFactor;
      float clipBottom = (float) (thumb.clipBottom + this.clipVertical) * clipFactor;
      float clipLeft = (float) (thumb.clipLeft + this.clipHorizontal) * clipFactor;
      float clipRight = (float) (thumb.clipRight + this.clipHorizontal) * clipFactor;

      subsamplingImageView.setClipping(
        targetWidth, targetHeight,
        clipHorizontal + clipLeft / scale,
        clipVertical + clipTop / scale,
        clipHorizontal + clipRight / scale,
        clipVertical + clipBottom / scale,
        Math.max(radius, thumb.topLeftRadius * clipFactor) / scale,
        Math.max(radius, thumb.topRightRadius * clipFactor) / scale,
        Math.max(radius, thumb.bottomRightRadius * clipFactor) / scale,
        Math.max(radius, thumb.bottomLeftRadius * clipFactor) / scale
      );
    }
  }

  public Receiver getReceiver () {
    return receiver;
  }

  public ImageReceiver getImageReceiver () {
    return receiver == imageReceiver ? imageReceiver : null;
  }

  private static final long DELAY_MIN = 60;
  private static final long DELAY_MAX = 30;
  private static final long DELAY_START = 0;

  private void downloadMedia () {
    if (media == null) {
      return;
    }
    FileProgressComponent fileProgress = media.getFileProgress();
    if (fileProgress != null) {
      fileProgress.setIgnoreAnimations(true);
    }
    media.attachToView(buttonView);
    media.download(!media.isVideo() || forceTouchMode);
    if (fileProgress != null) {
      fileProgress.setIgnoreAnimations(false);
    }
  }

  private CancellationSignal subsamplingLoadSignal;

  private ImageFile requestedImage;

  private void clearImage () {
    requestImage(null, null);
  }

  private void requestImage (MediaItem item, ImageFile imageFile) {
    if (this.requestedImage == imageFile) {
      return;
    }
    this.requestedImage = imageFile;
    if (this.subsamplingLoadSignal != null) {
      this.subsamplingLoadSignal.cancel();
      this.subsamplingLoadSignal = null;
    }
    if (imageFile == null || (item != null && item.isVideo())) {
      imageReceiver.requestFile(imageFile);
      recycle(subsamplingImageView);
      return;
    }
    boolean clearReceiver = true;
    if (imageFile instanceof ImageFileLocal) {
      setSubsamplingModeEnabled(true);
      subsamplingImageView.setImage(ImageSource.uri(Uri.fromFile(new File(imageFile.getFilePath()))));
    } else if (imageFile instanceof ImageFileRemote || imageFile.isRemote()) {
      CancellationSignal signal = new CancellationSignal();
      this.subsamplingLoadSignal = signal;
      setSubsamplingModeEnabled(true);
      Client.ResultHandler fileHandler = remoteFileObject -> {
        if (remoteFileObject.getConstructor() == TdApi.File.CONSTRUCTOR) {
          TdApi.File tdlibFile = (TdApi.File) remoteFileObject;
          imageFile.tdlib().client().send(new TdApi.DownloadFile(tdlibFile.id, 32, 0, 0, true), result -> {
            if (result.getConstructor() == TdApi.File.CONSTRUCTOR) {
              TdApi.File downloadedFile = (TdApi.File) result;
              Td.copyTo(downloadedFile, tdlibFile);
              if (TD.isFileLoaded(downloadedFile)) {
                imageFile.tdlib().runOnUiThread(() -> {
                  if (!signal.isCanceled()) {
                    subsamplingImageView.setImage(ImageSource.uri(Uri.fromFile(new File(downloadedFile.local.path))));
                  }
                });
              }
            }
          });
        }
      };
      if (imageFile instanceof ImageFileRemote) {
        ((ImageFileRemote) imageFile).extractFile(fileHandler);
      } else {
        fileHandler.onResult(imageFile.getFile());
      }
    } else {
      recycle(subsamplingImageView);
      imageReceiver.requestFile(imageFile);
      clearReceiver = false;
    }
    if (clearReceiver) {
      imageReceiver.clear();
    }
  }

  public void loadMedia (boolean delayed, float strength) {
    boolean isAutoplay = media != null && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && media.isAutoplay();
    if (isAutoplay)
      strength = 0f;
    if (delayed && strength < 1.0f) {
      if (preview != gifReceiver) {
        gifReceiver.requestFile(null);
      }
      clearImage();
      avatarReceiver.clear();

      delayedLoad = new CancellableRunnable() {
        @Override
        public void act () {
          if (delayedLoad == this && media != null) {
            loadMedia(false, 1f);
            downloadMedia();
            animatePreviewOverlay();
          }
        }
      };
      long delay = media != null && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) ? media.isAutoplay() ? 150 : DELAY_MIN : DELAY_MAX;
      postDelayed(delayedLoad, DELAY_START + (long) ((int) (delay - DELAY_START) * (1f - strength)));

      return;
    } else if (delayed) {
      downloadMedia();
      forcePreviewOverlay(0f);
    }
    if (media != null && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE)) {
      if (media.isGif()) {
        gifReceiver.requestFile(media.getTargetGifFile());
        clearImage();
        avatarReceiver.clear();
      } else if (media.isVideo() && media.isGifType() && revealFactor == 1f && !disappearing && getParent() instanceof MediaView && ((MediaView) getParent()).isOpen()) {
        clearImage();
        avatarReceiver.clear();
        setHideStaticView(true, false);
      } else if (media.isAvatar()) {
        clearImage();
        gifReceiver.clear();
        media.requestAvatar(avatarReceiver, true);
      } else {
        avatarReceiver.clear();
        if (preview != gifReceiver) {
          gifReceiver.clear();
        }
        ImageFile imageFile = media.getTargetImageFile(true);
        requestImage(media, imageFile);
      }
      invalidateImage();
    } else {
      if (preview != gifReceiver) {
        gifReceiver.requestFile(null);
      }
      clearImage();
      avatarReceiver.clear();
    }
  }

  public void setMedia (@Nullable MediaItem media) {
    setMedia(media, false, 0, 1f);
  }

  private static final int ANIMATOR_OVERLAY = 1;
  private FactorAnimator overlayAnimator;
  private float previewOverlayFactor;
  private CancellableRunnable delayedLoad;

  private void animatePreviewOverlay () {
    if (overlayAnimator == null) {
      overlayAnimator = new FactorAnimator(ANIMATOR_OVERLAY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, this.previewOverlayFactor);
    }
    if (media == null || (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE)) {
      forcePreviewOverlay(0f);
    } else {
      overlayAnimator.animateTo(0f);
    }
  }

  private void setPreviewOverlayFactor (float factor) {
    if (this.previewOverlayFactor != factor) {
      this.previewOverlayFactor = factor;
      buttonView.setAlpha(1f - factor);
      buttonView.invalidate();
    }
  }

  private void cancelDelayedLoad () {
    forcePreviewOverlay(0f);
    if (delayedLoad != null) {
      delayedLoad.cancel();
      delayedLoad = null;
    }
  }

  private boolean needLock () {
    return media != null && media.isVideo() && media.isGifType();
  }

  private boolean mLocked;
  private void checkLock () {
    boolean needLock = needLock();
    if (this.mLocked != needLock) {
      this.mLocked = needLock;
      if (needLock && media != null && media.getTargetGifFile() != null && !(getParent() instanceof MediaView)) {
        GifActor.restartGif(media.getTargetGifFile());
      }
      GifActor.addFreezeReason(needLock ? 1 : -1);
    }
  }

  private Runnable videoLocker = this::checkLock;

  private void forcePreviewOverlay (float factor) {
    if (overlayAnimator != null) {
      overlayAnimator.forceFactor(factor);
    }
    setPreviewOverlayFactor(factor);
  }

  @Nullable
  public MediaItem getMedia () {
    return media;
  }

  public void setMedia (@Nullable MediaItem media, boolean delayed, int thumbSize, float strength) {
    if (this.media == media) {
      return;
    }

    if (this.media != null) {
      cancelDelayedLoad();
      this.media.detachFromView(buttonView);
      this.media.detachFromView(imageView);
      if ((forceTouchMode && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE && this.media.isVideo()) || (delayed && !this.media.isLoaded() && !this.media.isVideo())) {
        this.media.pauseAbandonedDownload();
      }
    }

    this.media = media;
    this.bufferingProgressView.setProgressVisibleInstant(false);

    boolean needLock = needLock();
    if (needLock) {
      UI.cancel(videoLocker);
      checkLock();
    } else {
      UI.post(videoLocker, 350);
    }

    this.detector.reset();
    resetVideoState();
    zoomComponents();
    checkPostRotation(false);

    setSubsamplingModeEnabled(false);
    if (media == null) {
      imagePreviewReceiver.requestFile(null);
      miniThumbnail.requestFile(null);
      gifReceiver.requestFile(null);
      clearImage();
      avatarReceiver.clear();
    } else {
      miniThumbnail.requestFile(media.getMiniThumbnail());
      if (media.isVideo() && media.isGifType() && media.isLoaded() && !delayed) {
        preview = gifReceiver;
        gifReceiver.requestFile(media.getTargetGifFile());
      } else {
        preview = imagePreviewReceiver;
        if (delayed) {
          ImageFile imageFile = (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && !media.isVideo() ? media.getThumbImageFile(thumbSize, true) : null;
          if (imageFile != null) {
            forcePreviewOverlay(1f);
            imagePreviewReceiver.requestFile(imageFile);
          } else {
            imagePreviewReceiver.requestFile(media.getPreviewImageFile());
          }
        } else if (!enableEarlyLoad) {
          imagePreviewReceiver.requestFile(revealFactor == 0f && media.isGif() ? null : media.getPreviewImageFile());
        }
      }
      receiver = media.isAvatar() ? avatarReceiver : media.isGif() ? gifReceiver : imageReceiver;
      if (!forceTouchMode || media.getPreviewImageFile() == null || media.isAvatar()) {
        loadMedia(delayed, strength);
      }
      layoutReceivers();
      media.attachToView(imageView, this, this);
      if (!delayed) {
        downloadMedia();
      }
    }
  }

  @Override
  public void onRotationChanged (final ImageFile imageFile, int newDegrees, boolean byUserRequest) {
    UI.post(() -> {
      if (media != null && media.getSourceGalleryFile() == imageFile && playerView != null) {
        layoutVideo(true);
      }
    });
  }

  @Override
  public void onClick (float x, float y) {
    Receiver tReceiver = receiver;

    if (tReceiver instanceof ImageReceiver && !((ImageReceiver) tReceiver).isLoaded()) {
      tReceiver = imagePreviewReceiver;
    }

    if (media != null && media.isVideo() && media.isGifType() && media.isLoaded() && tReceiver instanceof ImageReceiver && gifReceiver != null) {
      tReceiver = gifReceiver;
    }

    if (tReceiver.isInsideContent(x, y, media != null ? media.getWidth() : 0, media != null ? media.getHeight() : 0)) {
      if (canTouch(false)) {
        ((MediaView) getParent()).onMediaClick(x, y);
      }
    }
  }

  public void invalidateContent (MediaItem item) {
    if (this.media == item && item != null) {
      if (item.isGif()) {
        gifReceiver.requestFile(item.getTargetGifFile());
      } else if (item.isAvatar()) {
        media.requestAvatar(avatarReceiver, true);
      } else {
        requestImage(item, item.getTargetImageFile(true));
      }
    }
  }

  public boolean hasVisibleContent () {
    return !receiver.needPlaceholder() || !preview.needPlaceholder() || !gifReceiver.needPlaceholder() || !miniThumbnail.needPlaceholder() || hideStaticView || (subsamplingModeEnabled && subsamplingImageView.isReady() && subsamplingImageView.getAlpha() > 0f);
  }

  // Post transformation

  private float postRotation;
  private FactorAnimator rotationAnimator;

  private float rotateFactor;
  private boolean scheduledAnimation;

  private void setPostRotation (float rotation, boolean animated) {
    if (this.postRotation != rotation || !animated) {
      float oldRotation = this.postRotation;
      this.postRotation = rotation;
      boolean changed = oldRotation != rotation;
      if (animated && MathUtils.modulo(rotation + 90f, 360f) == oldRotation) {
        if (rotationAnimator == null) {
          rotationAnimator = new FactorAnimator(ANIMATOR_ROTATE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 1f);
        } else {
          rotationAnimator.forceFactor(1f);
        }
        this.rotateFactor = 1f;
        checkRotationAndScale();
        scheduledAnimation = false;
        if (changed) {
          layoutReceivers(true);
          imageView.invalidate();
          if (playerView != null && media != null && (float) media.getWidth() / (float) media.getHeight() != 1f) {
            scheduledAnimation = true;
            playerView.requestLayout();
          }
        }
        if (!scheduledAnimation) {
          rotationAnimator.animateTo(0f);
        }
      } else if (changed) {
        if (rotationAnimator != null) {
          rotationAnimator.forceFactor(1f);
        }
        scheduledAnimation = false;
        rotateFactor = 0f;
        checkRotationAndScale();
        layoutReceivers(true);
        imageView.invalidate();
      } else {
        checkRotationAndScale();
      }
    }
  }

  private void checkRotationAndScale () {
    if (media == null)
      return;

    final int imageWidth, imageHeight;

    if ((media.isVideo() ? media.isFinallyRotated() : media.isPostRotated())) {
      imageWidth = media.getHeight();
      imageHeight = media.getWidth();
    } else {
      imageWidth = media.getWidth();
      imageHeight = media.getHeight();
    }

    if (imageWidth == 0 || imageHeight == 0)
      return;

    int viewWidth = getMeasuredWidth();
    int viewHeight = getMeasuredHeight();
    float scaleWidth, scaleHeight;
    if (media.isVideo() && media.isRotated()) {
      if (viewWidth < viewHeight) {
        scaleWidth = imageHeight;
        scaleHeight = imageWidth;
      } else {
        scaleWidth = imageWidth;
        scaleHeight = imageHeight;
      }
    } else {
      if (viewWidth > viewHeight) {
        scaleWidth = imageHeight;
        scaleHeight = imageWidth;
      } else {
        scaleWidth = imageWidth;
        scaleHeight = imageHeight;
      }
    }
    float rotation = 90f * rotateFactor;
    float scale = 1f + ((scaleWidth / scaleHeight) - 1f) * rotateFactor;

    imageView.setRotation(rotation);
    /*imageView.setPivotX(receiver.centerX());
    imageView.setPivotY(receiver.centerY());*/
    imageView.setScaleX(scale);
    imageView.setScaleY(scale);

    if (viewWidth > viewHeight) {
      scaleWidth = imageHeight;
      scaleHeight = imageWidth;
    } else {
      scaleWidth = imageWidth;
      scaleHeight = imageHeight;
    }

    View playerView = this.playerView != null ? this.playerView.getTargetView() : null;
    if (playerView != null) {
      rotation = postRotation + rotation;
      playerView.setRotation(rotation);
      float finalScale;
      if (media.isPostRotated()) {
        finalScale = scaleHeight / scaleWidth;
        scale = 1f + (finalScale - 1f) * (1f - rotateFactor);
      } else {
        finalScale = scaleWidth / scaleHeight;
        scale = 1f + (finalScale - 1f) * rotateFactor;
      }
      playerView.setScaleX(scale);
      playerView.setScaleY(scale);
    }
  }

  public void checkPostRotation (boolean animated) {
    if (media != null) {
      setPostRotation(media.isVideo() ? media.getPostRotation() : 0, animated);
    }
  }

  private void setRotateFactor (float factor) {
    if (this.rotateFactor != factor) {
      this.rotateFactor = factor;
      checkRotationAndScale();
    }
  }

  private static final int ANIMATOR_ROTATE = 0;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ROTATE:
        setRotateFactor(factor);
        break;
      case ANIMATOR_OVERLAY:
        setPreviewOverlayFactor(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ROTATE:
        if (playerView != null) {
          playerView.requestLayout();
        }
        setPostRotation(postRotation, false);
        break;
    }
  }

  // Zoom stuff

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    boolean res = canTouch(e.getAction() == MotionEvent.ACTION_DOWN) && detector.onTouchEvent(e);
    switch (e.getAction()) {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        interceptAnyEvents = false;
        break;
    }
    return res;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    boolean isDown = ev.getAction() == MotionEvent.ACTION_DOWN;
    if (isDown) {
      interceptAnyEvents = false;
    }
    if (canTouch(isDown)) {
      detector.onTouchEvent(ev);
    }
    return interceptAnyEvents || super.onInterceptTouchEvent(ev);
  }

  public boolean canZoom () {
    return canTouch(false) && getVisibility() == View.VISIBLE && media != null && getAlpha() == 1f && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && revealFactor == 1f && factor == 0f && ((MediaView) getParent()).canZoom(this) && !subsamplingModeEnabled;
  }

  public boolean canTouch (boolean isTouchDown) {
    return getVisibility() == View.VISIBLE && getParent() instanceof MediaView && ((MediaView) getParent()).isTouchEnabled(isTouchDown) && media != null && revealFactor == 1f;
  }

  @Override
  public boolean canMoveZoomedView () {
    return getParent() != null && ((MediaView) getParent()).canMoveZoomedView(this);
  }

  @Override
  public boolean canDoubleTap (float x, float y) {
    if (!((MediaView) getParent()).canDoubleTapZoom()) {
      return false;
    }
    if (media != null && canZoom()) {
      if (media.isVideo()) {
        int bound = Screen.dp(FileProgressComponent.DEFAULT_RADIUS);
        int centerX = getMeasuredWidth() / 2;
        int centerY = getMeasuredHeight() / 2;
        return x < centerX - bound || x > centerX + bound || y < centerY - bound || y > centerY + bound;
      }
      return true;
    }
    return false;
  }

  private boolean interceptAnyEvents;

  @Override
  public boolean onZoomStart () {
    if (!canZoom()) {
      return false;
    }

    getParent().requestDisallowInterceptTouchEvent(true);
    UI.getContext(getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_ZOOMING, true);
    ((MediaView) getParent()).dispatchOnMediaZoom();
    interceptAnyEvents = true;

    return true;
  }

  public MediaCellViewDetector getDetector () {
    return detector;
  }

  private void zoomComponents () {
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View child = getChildAt(i);
      if (child != null && child != buttonView) {
        detector.zoomView(this, child);
      }
    }
  }

  private void invalidateImage () {
    overlayView.invalidate();
    buttonView.invalidate();
    imageView.invalidate();
  }

  @Override
  public void onZoom () {
    zoomComponents();
  }

  @Override
  public void onZoomEnd () {
    getParent().requestDisallowInterceptTouchEvent(false);
    UI.getContext(getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_ZOOMING, false);
  }

  /*@Override
  public void getZoomScreenRect (Rect rect) {
    rect.left = 0;
    rect.top = 0;
    rect.right = getMeasuredWidth();
    rect.bottom = getMeasuredHeight();
  }*/

  @Override
  public void getZoomDisplayRect (Rect rect) {
    rect.left = 0; // receiver.getLeft();
    rect.top = 0; // receiver.getTop();
    rect.right = getMeasuredWidth(); // receiver.getRight();
    rect.bottom = getMeasuredHeight(); // receiver.getBottom();
  }

  public boolean isZoomed () {
    if (subsamplingModeEnabled && subsamplingImageView.isReady()) {
      float scale = subsamplingImageView.getScale();
      float minScale = subsamplingImageView.getMinScale();
      // Log.i("scale = %f min = %f diff = %f zoomed = %b", scale, minScale, scale - minScale, scale != minScale);
      return scale != 0f && scale > minScale;
    } else {
      float factor = getZoomFactor();
      // Log.i("factor zoom = %f zoomed = %b", factor, factor != 1f);
      return factor != 1f;
    }
  }

  public void normalizeZoom () {
    if (subsamplingModeEnabled && subsamplingImageView.isReady()) {
      float minScale = subsamplingImageView.getMinScale();
      SubsamplingScaleImageView.AnimationBuilder b = subsamplingImageView
        .animateScaleAndCenter(minScale, new PointF(
          subsamplingImageView.getSWidth() / 2f,
          subsamplingImageView.getHeight() / 2f
        ));
      if (b != null) {
        b.withInterruptible(false)
         .withDuration(ZOOM_DURATION)
         .withOnAnimationEventListener(new SubsamplingScaleImageView.DefaultOnAnimationEventListener() {
           @Override
           public void onComplete () {
             if (subsamplingImageView.getScale() != subsamplingImageView.getMinScale()) {
               subsamplingImageView.resetScaleAndCenter();
             }
           }
         })
         .start();
      }
    } else {
      getDetector().normalizeZoom(true);
    }
  }

  public float getZoomFactor () {
    return detector.getZoom();
  }

  @Override
  public void getZoomContentRect (Rect rect) {
    final int availWidth = receiver.getWidth();
    final int availHeight = receiver.getHeight();

    int mediaWidth, mediaHeight;
    if (media != null) {
      if (media.isFinallyRotated() && media.isVideo()) {
        mediaWidth = media.getHeight();
        mediaHeight = media.getWidth();
      } else {
        mediaWidth = media.getWidth();
        mediaHeight = media.getHeight();
      }
    } else {
      mediaWidth = mediaHeight = 0;
    }

    if (mediaWidth != 0 && mediaHeight != 0) {
      float ratio = Math.min((float) availWidth / (float) mediaWidth, (float) availHeight / (float) mediaHeight);
      mediaWidth *= ratio;
      mediaHeight *= ratio;
    }

    rect.left = receiver.centerX() - mediaWidth / 2;
    rect.top = receiver.centerY() - mediaHeight / 2;
    rect.right = receiver.centerX() + mediaWidth / 2;
    rect.bottom = receiver.centerY() + mediaHeight / 2;
  }

  // Drawing

  public class CellButtonView extends SparseDrawableView {
    public CellButtonView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      if (media != null && !isPlaying && !hideStaticView && (!media.isAutoplay() || (!media.isLoaded() && !Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE))) {
        media.drawComponents(this, c, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
      }
    }
  }

  private final Path clipPath = new Path();

  public class CellImageView extends View {
    public CellImageView (Context context) {
      super(context);
    }

    public void onDraw (Canvas c) {
      if (media == null || imageAlpha == 0f) {
        return;
      }

      if (revealFactor == 0f) {
        if (targetAnimator != null && (!(!hideStaticView || !videoReady) || !receiver.needPlaceholder())) {
          targetAnimator.animateTo(1f);
          targetAnimator = null;
        }
        return;
      }

      final boolean savedFactor = factor != 0f;
      if (savedFactor) {
        c.save();
      }

      final float clipFactor = Math.max(0f, Math.min(1f, revealFactor));

      final boolean savedClip = thumb != null && clipFactor < 1f;
      int savedRoundedClip = Integer.MIN_VALUE;
      if (savedClip) {
        int clipTop = (int) ((float) (thumb.clipTop + clipVertical) * (1f - clipFactor));
        int clipBottom = (int) ((float) (thumb.clipBottom + clipVertical) * (1f - clipFactor));
        int clipLeft = (int) ((float) (thumb.clipLeft + clipHorizontal) * (1f - clipFactor));
        int clipRight = (int) ((float) (thumb.clipRight + clipHorizontal) * (1f - clipFactor));

        c.save();
        c.clipRect(receiver.getLeft() + clipLeft, receiver.getTop() + clipTop, receiver.getRight() - clipRight, receiver.getBottom() - clipBottom);

        float topLeftRadius = Math.max(thumb.getRadius(), thumb.topLeftRadius) * (1f - clipFactor);
        float topRightRadius = Math.max(thumb.getRadius(), thumb.topRightRadius) * (1f - clipFactor);
        float bottomRightRadius = Math.max(thumb.getRadius(), thumb.bottomRightRadius) * (1f - clipFactor);
        float bottomLeftRadius = Math.max(thumb.getRadius(), thumb.bottomLeftRadius) * (1f - clipFactor);

        if (topLeftRadius != 0 || topRightRadius != 0 || bottomLeftRadius != 0 || bottomRightRadius != 0) {
          clipPath.reset();
          RectF rectF = Paints.getRectF();

          float clipWidth = Math.min(receiver.getTargetWidth(), receiver.getWidth());
          float clipHeight = Math.min(receiver.getTargetHeight(), receiver.getHeight());

          rectF.set(receiver.centerX() - clipWidth / 2f, receiver.centerY() - clipHeight / 2f, receiver.centerX() + clipWidth / 2f, receiver.centerY() + clipHeight / 2f);
          DrawAlgorithms.buildPath(clipPath, rectF, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius);
          savedRoundedClip = ViewSupport.clipPath(c, clipPath);
        }
      }

      if (!hideStaticView || !videoReady) {
        if (receiver.needPlaceholder() || (subsamplingModeEnabled && !subsamplingImageView.isReady())) {
          if (preview.needPlaceholder()) {
            miniThumbnail.draw(c);
          }
          preview.draw(c);
        } else {
          if (targetAnimator != null) {
            targetAnimator.animateTo(1f);
            targetAnimator = null;
          }
        }
        receiver.draw(c);
      } else {
        if (targetAnimator != null) {
          targetAnimator.animateTo(1f);
          targetAnimator = null;
        }
      }

      if (savedClip) {
        if (savedRoundedClip != Integer.MIN_VALUE) {
          c.restoreToCount(savedRoundedClip);
        }
        c.restore();
      }

      if (savedFactor/* || zoomFactor != 1f || translateX != 0 || translateY != 0*/) {
        c.restore();
      }
    }
  }

  public void attach () {
    gifReceiver.attach();
    imageReceiver.attach();
    imagePreviewReceiver.attach();
    miniThumbnail.attach();
    avatarReceiver.attach();
  }

  public void detach () {
    gifReceiver.detach();
    imageReceiver.detach();
    imagePreviewReceiver.detach();
    miniThumbnail.detach();
    avatarReceiver.detach();
  }

  // Video

  public interface Callback {
    void onCanSeekChanged (MediaItem item, boolean canSeek);
    void onSeekProgress (MediaItem item, long now, long duration, float progress);
    void onPlayPause (MediaItem item, boolean isPlaying);
    void onPlayStarted (MediaItem item, boolean isPlaying);
    void onSeekSecondaryProgress (MediaItem item, float offset, float progress);
  }

  private Callback callback;

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    if (media != null && media.isVideo() && (Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE || media.isLoaded())) {
      if (Config.VIDEO_PLAYER_AVAILABLE) {
        if ((!isPlaying && !hideStaticView && !media.isAutoplay()) || view != getParent()) {
          setHideStaticView(true, true);
          if (playerView != null) {
            playerView.playPause();
          }
          return true;
        }
        return false;
      } else if (media.isLoaded()) {
        U.openFile(UI.getContext(getContext()).navigation().getCurrentStackItem(), media.getSourceVideo());
      }
      return true;
    }
    return false;
  }

  private boolean videoReady;

  private void setVideoReady (boolean isReady) {
    if (this.videoReady != isReady) {
      this.videoReady = isReady;
      invalidateImageComponents();
    }
  }

  private void setHideStaticView (boolean hideStaticView, boolean delayed) {
    if (this.hideStaticView != hideStaticView) {
      this.hideStaticView = hideStaticView;
      if (hideStaticView) {
        prepareVideo();
        if (playerView != null) {
          playerView.setVideo(media);
        }
        if (callback != null) {
          callback.onPlayStarted(media, isPlaying);
        }
      } else {
        invalidateImageComponents();
        bufferingProgressView.setProgressVisible(false, false);
        if (delayed) {
          UI.post(() -> {
            if (playerView != null) {
              playerView.setVideo(null);
            }
          });
        } else if (playerView != null) {
          playerView.setVideo(null);
        }
      }
    }
  }

  private boolean hideStaticView;

  private void resetVideoState () {
    setHideStaticView(false, false);
    isPlaying = false;
    videoReady = false;
    timeNow = -1;
    timeTotal = -1;
    // TODO other
  }

  private void invalidateImageComponents () {
    imageView.invalidate();
    buttonView.invalidate();
  }

  private boolean isPlaying;

  @Override
  public void onPlayPause (boolean isPlaying) {
    if (this.isPlaying != isPlaying) {
      this.isPlaying = isPlaying;
      invalidateImageComponents();
      if (callback != null) {
        callback.onPlayPause(media, isPlaying);
      }
    }
  }

  @Override
  public void onPlayReady () {
    setVideoReady(true);
  }

  private long timeNow = -1, timeTotal = -1;

  public long getTimeNow () {
    return timeNow;
  }

  public long getTimeTotal () {
    return timeTotal;
  }

  @Override
  public void onPlayProgress (long totalDuration, long now) {
    this.timeNow = now;
    this.timeTotal = totalDuration;
    if (callback != null) {
      callback.onSeekProgress(media, now, totalDuration, (float) ((double) now / (double) totalDuration));
    }
  }

  private void prepareVideo () {
    if (playerView == null) {
      videoParentView = new CellVideoView(getContext());
      videoParentView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      playerView = new VideoPlayerView(getContext(), videoParentView, 0);
      playerView.setInForceTouch();
      playerView.forceLooping(forceTouchMode);
      playerView.setBoundCell(this);
      playerView.setCallback(this);
      hideStaticView = true;
      addView(videoParentView, 0);
      checkPostRotation(false);
    } else {
      layoutVideo(true);
    }
  }

  private void layoutVideo (boolean requestLayout) {
    if (playerView != null) {
      playerView.requestLayout();
      checkPostRotation(false);
    }
  }

  public void autoplayIfNeeded (boolean isSwitch) {
    if (media != null && media.isVideo() && (!isSwitch || media.isGifType()) && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && media.isRemoteVideo() && !destroyed) {
      if (!media.isGifType()) {
        TdlibManager.instance().player().pauseWithReason(TGPlayerController.PAUSE_REASON_OPEN_VIDEO);
      }
      if (Config.VIDEO_PLAYER_AVAILABLE) {
        setHideStaticView(true, true);
        if (playerView != null && !playerView.isPlaying()) {
          playerView.playPause();
        }
        media.setComponentsAlpha(1f);
      } else {
        U.openFile(UI.getContext(getContext()).navigation().getCurrentStackItem(), media.getSourceVideo());
      }
    }
  }

  public void updateMute () {
    if (playerView != null && media != null && media.isVideo() && (media.isLoaded() || Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) && media.getType() == MediaItem.TYPE_GALLERY_VIDEO) {
      playerView.setMuted(media.needMute());
    }
  }

  public void pauseIfPlaying () {
    setHideStaticView(true, true);
    if (playerView != null) {
      playerView.pauseIfPlaying();
    }
  }

  public void resumeIfNeeded (float progress) {
    if (playerView != null) {
      playerView.resumeIfNeeded(progress);
    }
  }

  public void setSeekProgress (float progress) {
    if (playerView != null) {
      playerView.setSeekProgress(progress);
    }
  }

  private void setCanSeek (boolean canSeek) {
    if (callback != null) {
      callback.onCanSeekChanged(media, canSeek);
    }
  }

  @Override
  public void onStateChanged (TdApi.File file, @TdlibFilesManager.FileDownloadState int state) {
    if (media != null && media.isVideo() && Config.VIDEO_CLOUD_PLAYBACK_AVAILABLE) {
      setCanSeek(true);
    } else {
      setCanSeek(media != null && media.isVideo() && state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED);
    }

    if (state == TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED && (forceTouchMode || (media != null && media.isAutoplay()))) {
      autoplayIfNeeded(false);
    }
  }

  @Override
  public void onBufferingStateChanged (boolean isBuffering) {
    bufferingProgressView.setProgressVisible(isBuffering, media != null && media.getFileProgress().isDownloaded());
  }

  @Override
  public void onPlayError () {
    bufferingProgressView.setProgressVisible(false, false);
  }

  @Override
  public void onProgress (TdApi.File file, float progress) {
    if (callback != null) {
      callback.onSeekSecondaryProgress(media, TD.getFileOffsetProgress(file), TD.getFilePrefixProgress(file));
    }
  }
}
