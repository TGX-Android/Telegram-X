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
 * File created on 29/08/2017
 */
package org.thunderdog.challegram.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageOverlayView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.preview.FlingDetector;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGMessageVideo;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.MessagesRecyclerView;
import org.thunderdog.challegram.widget.CircleFrameLayout;
import org.thunderdog.challegram.widget.ImageReceiverView;
import org.thunderdog.challegram.widget.RectFrameLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class RoundVideoController extends BasePlaybackController implements
  BaseActivity.ActivityListener,
  TdlibFilesManager.FileListener,
  FactorAnimator.Target
{
  private final BaseActivity context;
  private final VideoHandler handler;

  public RoundVideoController (BaseActivity context) {
    this.context = context;
    this.handler = new VideoHandler(this);
    TdlibManager.instance().player().addTrackChangeListener(this);
  }

  /**
   * Entry section. At this point we receive triggers to process some video, or destroy it.
   * */

  @Override
  protected boolean isSupported (TdApi.Message message) {
    return message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR;
  }

  @Override
  protected void finishPlayback (Tdlib tdlib, TdApi.Message message, boolean byUserRequest) {
    closePlayer(MODE_CLOSE);
  }

  @Override
  protected void startPlayback (Tdlib tdlib, TdApi.Message message, boolean byUserRequest, boolean hadObject, Tdlib previousTdlib, int previousFileId) {
    float currentProgress = 0f;

    if (texturePrepared) {
      fakeFrame.eraseColor(0);
      invalidatePlaceholders();
      currentProgress = visualProgress;
    }

    // Before we start, detach main texture
    setTargetView(null, false, false);

    // First, prepare ExoPlayer & off-screen texture
    preparePlayerIfNeeded();

    // Set playback state that will be used in setSource
    this.isPlaying = true;

    // Reset displayingFrame state in case previous video was paused
    checkPlayingThroughPip();

    // Set video resource, or await download
    setFile(tdlib, TD.getFile(message));
    setPreview(((TdApi.MessageVideoNote) message.content).videoNote);

    // After all manipulations done, find or await new target
    findOrAwaitTargetView(byUserRequest, hadObject, currentProgress);

    // Set playing state
    checkPlayState();
    setPipHidden(false);
  }

  /**
   * This is file-level management.
   * */

  private Tdlib fileContext;
  private TdApi.File file;
  private MediaSource source;

  private void setFile (Tdlib tdlib, TdApi.File file) {
    if (this.file == null && file == null) { // Nothing to do.
      return;
    }

    boolean sameAccount = this.fileContext == tdlib;
    int oldFileId = Td.getId(this.file);
    int newFileId = Td.getId(file);

    if (sameAccount && oldFileId == newFileId) { // Files not changed
      if (TD.isFileLoaded(file)) { // Restart video, if it is downloaded
        exoPlayer.seekTo(0);
        exoPlayer.setPlayWhenReady(isPlaying);
        setPlayProgress(0, playDuration, playDuration);
        checkProgressTimer();
      } else { // Otherwise there's nothing to do just yet. Playback will start when file will be downloaded.
        tdlib.files().downloadFile(file);
      }
      return;
    }

    if (oldFileId != 0) {
      this.fileContext.files().unsubscribe(oldFileId, this);
    }
    this.fileContext = tdlib;
    this.file = file;
    if (newFileId != 0) {
      tdlib.files().subscribe(file, this);
    }

    if (file == null) { // Simply release current video source
      setSource(null, -1);
      setProgressVisible(false);
    } else if (TD.isFileLoaded(file)) { // Start playback
      setSource(tdlib, file.id);
      setProgressVisible(false);
    } else { // Request download & wait until it will be downloaded
      setSource(null, -1);
      setProgressVisible(true);
      tdlib.files().downloadFile(file);
    }
  }

  private void setProgressVisible (boolean isVisible) {
    if (pipProgressView != null) {
      pipProgressView.setProgressVisible(isVisible);
    }
  }

  private ImageFile previewFile;

  private void setPreview (@Nullable TdApi.VideoNote videoNote) {
    int oldFileId = previewFile != null ? previewFile.getId() : 0;
    int newFileId = videoNote != null && videoNote.thumbnail != null ? videoNote.thumbnail.file.id : 0;
    if (oldFileId != newFileId) {
      this.previewFile = TGMessageVideo.newPreviewFile(tdlib, videoNote);
      if (texturePrepared) {
        pipPreviewView.getReceiver().requestFile(previewFile);
      }
    }
  }

  private void setSource (Tdlib tdlib, int fileId) {
    MediaSource newSource;
    if (fileId < 0) {
      newSource = null;
    } else {
      newSource = U.newMediaSource(tdlib.id(), fileId);
    }
    this.source = newSource;
    this.hasRenderedAnyFrame = false;
    setRendered(false, false);
    if (source != null) {
      exoPlayer.setMediaSource(source);
      exoPlayer.prepare();
    }
    setPlayProgress(0f, -1, -1);
  }

  @Override
  public void onFileLoadProgress (TdApi.File file) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_FILE_PROGRESS, file));
      return;
    }
    if (file != null && this.file != null && this.file.id == file.id) {
      Td.copyTo(file, this.file);
      pipProgressView.setLoadProgress(TD.getFileProgress(file));
    }
  }

  @Override
  public void onFileLoadStateChanged (Tdlib tdlib, int fileId, @TdlibFilesManager.FileDownloadState int state, @Nullable TdApi.File downloadedFile) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_DISPATCH_FILE_STATE, fileId, state, new Object[] {tdlib, downloadedFile}));
      return;
    }
    if (this.file != null && this.file.id == fileId && this.tdlib == tdlib) {
      switch (state) {
        case TdlibFilesManager.STATE_PAUSED:
          // User requested to cancel download,
          // we assume he doesn't want to play this message any more & close playback.
          TdlibManager.instance().player().stopPlayback(false);
          break;
        case TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED:
          // File is ready to be played. So we play it.
          if (downloadedFile != null) {
            Td.copyTo(downloadedFile, file);
            setSource(tdlib, downloadedFile.id);
            setProgressVisible(false);
            TdlibManager.instance().player().playPauseMessageEventually(tdlib, object, false);
          }
          break;

        // Nothing to do in other cases
        case TdlibFilesManager.STATE_FAILED:
          break;
        case TdlibFilesManager.STATE_IN_PROGRESS:
          break;
      }
    }
  }

  /*@Override
  public void onFileGenerationProgress (int fileId, int ready, int size) { }*/

  @Override
  public void onFileGenerationFinished (@NonNull TdApi.File file) {

  }

  /**
   * Texture management
   * */

  private boolean texturePrepared;
  private RectFrameLayout rootView;
  private CircleFrameLayout mainPlayerView;
  private View mainTextureView;
  private RoundProgressView mainProgressView;
  private ImageReceiverView mainPreviewView;

  private ExoPlayer exoPlayer;

  private InterceptPipLayout pipParentView;
  private CircleFrameLayout pipPlayerView;
  private TextureView pipTextureView;
  private RoundProgressView pipProgressView;
  private ImageReceiverView pipPreviewView;

  private boolean isRendered;

  private static final boolean USE_SURFACE = false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

  private void preparePlayerIfNeeded () { // Prepare off-screen texture
    if (texturePrepared) {
      return;
    }

    if (fakeFrame == null) {
      fakeFrame = Bitmap.createBitmap(Screen.dp(200f, 2f), Screen.dp(200f, 2f), Bitmap.Config.ARGB_8888);
      previewFrame = Bitmap.createBitmap(Screen.dp(111f, 2f), Screen.dp(111f, 2f), Bitmap.Config.ARGB_8888);
    }

    if (rootView == null) {
      rootView = new RectFrameLayout(context);
      FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      params.topMargin = HeaderView.getSize(true);
      rootView.setLayoutParams(params);
      context.addToNavigation(rootView);
    }

    if (mainPlayerView == null) {
      int textureSize = TGMessageVideo.getVideoSize();
      mainPlayerView = new CircleFrameLayout(context);
      mainPlayerView.setAlpha(mainVisibilityFactor);
      mainPlayerView.setLayoutParams(FrameLayoutFix.newParams(textureSize, textureSize));

      if (USE_SURFACE) {
        mainTextureView = new SurfaceView(context);
      } else {
        mainTextureView = new TextureView(context);
      }
      mainTextureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      mainPlayerView.addView(mainTextureView);

      mainPreviewView = new ImageReceiverView(context);
      mainPreviewView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      mainPlayerView.addView(mainPreviewView);

      mainProgressView = new RoundProgressView(context);
      mainProgressView.setController(this);
      mainProgressView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      mainPlayerView.addView(mainProgressView);

      rootView.addView(mainPlayerView);
    }

    if (pipParentView == null) {
      pipParentView = new InterceptPipLayout(context);
      pipParentView.setContext(this);
      pipParentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    if (pipPlayerView == null) {
      pipPlayerView = new CircleFrameLayout(context);
      pipPlayerView.setLayoutParams(newPlayerParams());
      updatePipStyles();
      pipPlayerView.setOnClickListener(v -> {
        if (object != null) {
          TdlibManager.instance().player().playPauseMessage(tdlib, object, null);
        }
      });
      pipPlayerView.setBackgroundColor(Theme.fillingColor());
      pipPlayerView.setTransparentOutline(false);
      setCircleShadow(pipPlayerView);

      pipTextureView = new TextureView(context);
      pipTextureView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      pipPlayerView.addView(pipTextureView);

      pipPreviewView = new ImageReceiverView(context);
      pipPreviewView.setOverlayBitmap(fakeFrame);
      pipPreviewView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      pipPlayerView.addView(pipPreviewView);

      pipProgressView = new RoundProgressView(context);
      pipProgressView.setController(this);
      pipProgressView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      pipPlayerView.addView(pipProgressView);

      pipParentView.addView(pipPlayerView);
    }

    if (pipParentView.getParent() == null) {
      context.addToRoot(pipParentView, false);
    }

    if (exoPlayer == null) {
      this.exoPlayer = U.newExoPlayer(context, true);
      setExoPlayerParameters();
      this.exoPlayer.addListener(this);
      this.exoPlayer.setVolume(volume);
      if (mainTextureView instanceof SurfaceView) {
        this.exoPlayer.setVideoSurfaceView((SurfaceView) mainTextureView);
      } else {
        this.exoPlayer.setVideoTextureView((TextureView) mainTextureView);
      }
    }

    texturePrepared = true;
  }

  public FrameLayoutFix getPipParentView () {
    return object != null ? pipParentView : null;
  }

  private static void setCircleShadow (View view) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      view.setElevation(Screen.dp(1f));
      view.setTranslationZ(Screen.dp(1f));
    }
  }

  private static final float SWITCH_SCALE = .1f;
  private static final float CLOSE_SCALE = .68f;
  private static final float HIDE_SCALE = .89f;

  private static final int MODE_NONE = 0;
  private static final int MODE_CLOSE = 1;
  private static final int MODE_SWITCH = 2;
  private static final int MODE_DISMISS = 3;
  private static final int MODE_HIDE = 4;

  private FakeView fakeView;

  private void animatePipClose (final int mode, final float withProgress) {
    final Bitmap bitmap = getFrameFromTexture(isPlayingThroughPip, previewFrame);
    if (bitmap == null) {
      return;
    }

    final FakeView view;
    if (fakeView == null) {
      fakeView = new FakeView(context);
      view = fakeView;
    } else if (fakeView.getAlpha() == 0f) {
      view = fakeView;
    } else {
      view = new FakeView(context);
    }

    final float fromAlpha = pipPlayerView.getAlpha();
    view.setAlpha(fromAlpha);
    view.setScaleX(pipPlayerView.getScaleX());
    view.setScaleY(pipPlayerView.getScaleY());
    view.setLayoutParams(FrameLayoutFix.newParams(getPlayerPipSize(), getPlayerPipSize()));
    setCircleShadow(view);
    final float fromTranslateX = pipPlayerView.getLeft() + pipPlayerView.getTranslationX();
    final float fromTranslateY = pipPlayerView.getTop() + pipPlayerView.getTranslationY();
    int gravity = pipParentView.calculateCurrentGravity();
    final float toTranslateX;
    if (isRightGravity(gravity)) {
      toTranslateX = pipParentView.getMeasuredWidth() + getPlayerPipMargin();
    } else {
      toTranslateX = -getPlayerPipSize() - getPlayerPipMargin();
    }
    view.setTranslationX(fromTranslateX);
    view.setTranslationY(fromTranslateY);
    view.setBitmap(bitmap);
    view.setProgress(withProgress);
    FactorAnimator animator = new FactorAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        view.setAlpha(factor * fromAlpha);
        switch (mode) {
          case MODE_CLOSE: {
            float scale = CLOSE_SCALE + (1f - CLOSE_SCALE) * factor;
            view.setScaleX(scale);
            view.setScaleY(scale);
            break;
          }
          case MODE_SWITCH: {
            float scale = SWITCH_SCALE + (1f - SWITCH_SCALE) * factor;
            view.setScaleX(scale);
            view.setScaleY(scale);
            break;
          }
          case MODE_DISMISS:
          case MODE_HIDE: {
            final float scale = HIDE_SCALE + (1f - HIDE_SCALE) * factor;
            view.setScaleY(scale);
            view.setScaleY(scale);
            view.setTranslationX(fromTranslateX + (toTranslateX - fromTranslateX) * (1f - factor));
            break;
          }
        }
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        if (view != fakeView) {
          pipParentView.removeView(view);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, mode == MODE_DISMISS ? 120l : 180l, 1f);

    if (view.getParent() == null) {
      view.startAnimatorOnLayout(animator, 0f);
      pipParentView.addView(view, 0);
    } else {
      animator.animateTo(0f);
    }
  }

  private int forceCloseMode = MODE_NONE;

  private void dismissPlayer () {
    forceCloseMode = MODE_DISMISS;
    TdlibManager.instance().player().playPauseMessage(null, null, null);
  }

  private void closePlayer (int mode) { // Stop playback, hide PiP, release resources
    if (!texturePrepared) {
      return;
    }

    if (mode != MODE_NONE && forceCloseMode != MODE_NONE) {
      mode = forceCloseMode;
      forceCloseMode = MODE_NONE;
    }

    setOwnedOverlay(null);

    Log.i(Log.TAG_VIDEO, "closePlayer, mode: %d", mode);

    if (inPipMode) {
      if (mode != MODE_NONE) {
        animatePipClose(mode, visualProgress);
        pipParentView.forceApplyPosition();
      }
      setInPipMode(false, false);
    }

    texturePrepared = false;

    context.removeFromNavigation(rootView);
    rootView = null;
    mainPlayerView = null; mainTextureView = null; mainProgressView = null;

    // context.removeFromRoot(pipParentView);
    pipParentView.removeView(pipPlayerView);
    pipPreviewView.performDestroy();
    setPreviewVisible(true);
    pipPreviewView.getReceiver().requestFile(null);
    pipPlayerView = null;

    setFile(null, null);

    if (exoPlayer != null) {
      exoPlayer.release();
      exoPlayer = null;
    }
    checkProgressTimer();

    displayingFrame = false;
  }

  private void setRendered (boolean isRendered, boolean animated) {
    if (this.isRendered != isRendered) {
      this.isRendered = isRendered;
      if (texturePrepared) {
        checkMainVisible(animated);
        setPreviewVisible(!isRendered);
      }
    }
  }

  private boolean previewVisible = true;
  private float pipPreviewAlpha = 1f;

  private FactorAnimator previewAnimator;

  private void setPreviewVisible (boolean isVisible) {
    if (this.previewVisible != isVisible) {
      this.previewVisible = isVisible;
      checkPreviewVisible();
    }
  }

  private boolean pipPreviewVisible = true;

  private void checkPreviewVisible () {
    boolean isVisible = this.previewVisible || this.displayingFrame;
    if (this.pipPreviewVisible != isVisible) {
      this.pipPreviewVisible = isVisible;
      final float toFactor = isVisible ? 1f : 0f;
      if (isVisible || pipVisibilityFactor == 0f) {
        if (previewAnimator != null) {
          previewAnimator.forceFactor(toFactor);
        }
        setPreviewAlpha(toFactor);
      } else {
        if (previewAnimator == null) {
          previewAnimator = new FactorAnimator(ANIMATOR_PIP_PREVIEW, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.pipPreviewAlpha);
        }
        previewAnimator.animateTo(toFactor);
      }
    }
  }

  private void setPreviewAlpha (float factor) {
    if (this.pipPreviewAlpha != factor) {
      this.pipPreviewAlpha = factor;
      pipPreviewView.setAlpha(factor);
    }
  }

  // Picture-in-Picture & View handling

  private boolean inPipMode;

  private void setInPipMode (boolean inPipMode, boolean animated) {
    if (this.inPipMode != inPipMode) {
      this.inPipMode = inPipMode;

      Log.i(Log.TAG_VIDEO, "inPipMode -> %b, animated: %b", inPipMode, animated);

      checkPlayingThroughPip();
      checkMainVisible(animated);

      if (texturePrepared) {
        checkPipVisible(animated);
      }
    }
  }

  private FactorAnimator pipAnimator;
  private boolean isPipVisible;
  private float pipVisibilityFactor;

  private boolean pipHidden;

  private void setPipHidden (boolean isHidden) {
    if (this.pipHidden != isHidden) {
      this.pipHidden = isHidden;
      checkPipVisible(!isHidden);
    }
  }

  private void checkPipVisible (boolean animated) {
    setPipVisible(inPipMode && !pipHidden, animated);
  }

  private void setPipVisible (boolean isVisible, boolean animated) {
    if (this.isPipVisible != isVisible || !animated) {
      this.isPipVisible = isVisible;
      final float toFactor = isVisible ? 1f : 0f;
      if (animated) {
        if (pipAnimator == null) {
          if (pipVisibilityFactor == toFactor) {
            return;
          }
          pipAnimator = new FactorAnimator(ANIMATOR_PIP_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, pipVisibilityFactor);
        }
        pipAnimator.animateTo(toFactor);
      } else {
        if (pipAnimator != null) {
          pipAnimator.forceFactor(toFactor);
        }
        setPipVisibilityFactor(toFactor);
      }
    }
  }

  private void setPipVisibilityFactor (float factor) {
    if (this.pipVisibilityFactor != factor) {
      this.pipVisibilityFactor = factor;
      updatePipStyles();
    }
  }

  private static int getPlayerPipMargin () {
    return Screen.dp(11f);
  }

  private static int getPlayerPipSize () {
    return Screen.dp(111f);
  }

  private float pipTranslateX, pipTranslateY;

  private void setPipTranslate (float x, float y) {
    if (pipPlayerView == null || (pipTranslateX == x && pipTranslateY == y)) {
      return;
    }

    pipTranslateX = x;
    pipTranslateY = y;

    updatePipStyles();
  }

  private FrameLayoutFix.LayoutParams newPlayerParams () {
    int pipSize = getPlayerPipSize();

    int gravity = Settings.instance().getPipGravity();

    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(pipSize, pipSize, gravity);
    params.topMargin = HeaderView.getSize(true) + HeaderView.getPlayerSize() + getPlayerPipMargin();
    params.rightMargin = params.bottomMargin = params.leftMargin = getPlayerPipMargin();

    return params;
  }

  private void applyPipGravity (int gravity) {
    if (pipPlayerView != null) {
      pipTranslateX = 0f;
      pipTranslateY = 0f;

      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) pipPlayerView.getLayoutParams();
      if (isRightGravity(params.gravity) != isRightGravity(gravity) || isBottomGravity(params.gravity) != isBottomGravity(gravity)) {
        params.gravity = gravity;
        pipPlayerView.setLayoutParams(params);
        Settings.instance().setPipGravity(gravity);
      }

      updatePipStyles();
    }
  }

  private static boolean isRightGravity (int gravity) {
    return (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
  }

  private static boolean isBottomGravity (int gravity) {
    return (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM;
  }

  private float getPipDismissAlpha () {
    float actualX = pipPlayerView.getLeft() + pipTranslateX;
    int viewWidth = pipPlayerView.getMeasuredWidth();
    int parentWidth = pipParentView.getMeasuredWidth();

    float checkX = viewWidth * .8f;

    final float alpha;
    if (actualX < 0) {
      alpha = 1f - Math.min(1f, -actualX / checkX);
    } else if (actualX + viewWidth > parentWidth) {
      alpha = 1f - Math.min(1f, ((actualX + viewWidth) - parentWidth) / checkX);
    } else {
      alpha = 1f;
    }
    return alpha;
  }

  private void updatePipStyles () {
    if (pipPlayerView == null) {
      return;
    }
    final float alpha = getPipDismissAlpha();

    float translateX;
    float translateY;
    if (pipVisibilityFactor != 1f) {
      float offsetX = getPlayerPipMargin() + getPlayerPipSize();
      int gravity = ((FrameLayoutFix.LayoutParams) pipPlayerView.getLayoutParams()).gravity;
      if (!isRightGravity(gravity)) {
        offsetX *= -1f;
      }
      translateX = offsetX * (1f - pipVisibilityFactor);
      translateY = 0f;
    } else {
      translateX = pipTranslateX;
      translateY = pipTranslateY;
    }

    pipPlayerView.setTranslationX(translateX);
    pipPlayerView.setTranslationY(translateY);

    setVolume(alpha);

    pipPlayerView.setAlpha(pipVisibilityFactor * alpha);
  }

  private boolean isMainVisible;
  private float mainVisibilityFactor;
  private FactorAnimator visibilityAnimator;

  private void checkMainVisible (boolean animated) {
    if (texturePrepared) {
      boolean isVisible = currentViewVisible && !inPipMode && isRendered;
      if (this.isMainVisible != isVisible) {
        this.isMainVisible = isVisible;
        Log.i(Log.TAG_VIDEO, "isMainVisible -> %b", isVisible);
        final float toFactor = isVisible ? 1f : 0f;
        if (animated && targetView != null) {
          if (visibilityAnimator == null) {
            if (mainVisibilityFactor == toFactor) {
              return;
            }
            visibilityAnimator = new FactorAnimator(ANIMATOR_MAIN_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 138l, this.mainVisibilityFactor);
          }
          visibilityAnimator.animateTo(toFactor);
        } else {
          if (visibilityAnimator != null) {
            visibilityAnimator.forceFactor(toFactor);
          }
          setMainVisibilityFactor(toFactor);
        }
      }
    }
  }

  private void setMainVisibilityFactor (float factor) {
    if (this.mainVisibilityFactor != factor) {
      this.mainVisibilityFactor = factor;
      if (texturePrepared) {
        mainPlayerView.setAlpha(factor);
      }
    }
  }

  private boolean isPlayingThroughPip;
  private boolean displayingFrame;
  private Bitmap fakeFrame, previewFrame;
  private boolean hasRenderedAnyFrame;

  private Bitmap getFrameFromTexture (boolean needPip, Bitmap output) {
    try {
      View target = needPip ? pipTextureView : mainTextureView;
      output.eraseColor(0);
      if (target instanceof SurfaceView) {
        // TODO
      } else {
        Bitmap result = output;
        if (hasRenderedAnyFrame || output == previewFrame) {
          result = ((TextureView) target).getBitmap(output);
        }
        invalidatePlaceholders();
        return result;
      }
    } catch (OutOfMemoryError t) {
      Log.w(Log.TAG_VIDEO, "Not enough memory to allocate placeholder");
    }
    return null;
  }

  private void checkPlayingThroughPip () {
    if (isPlaying) { // If we're playing, just switch video texture
      setDisplayingFrame(false);
      setPlayingThroughPip(inPipMode);
    } else if (isPlayingThroughPip != inPipMode && texturePrepared) { // Otherwise, we need to draw bitmap from other texture
      if (!displayingFrame) {
        Bitmap frame = getFrameFromTexture(isPlayingThroughPip, fakeFrame);
        setDisplayingFrame(frame != null);
        invalidatePlaceholders();
        if (!displayingFrame) {
          Log.i(Log.TAG_VIDEO, "Warning: forcely switching output view");
          setPlayingThroughPip(inPipMode);
        }
      }
    } else {
      setDisplayingFrame(false);
    }
  }

  private void setPlayingThroughPip (boolean isPlayingThroughPip) {
    if (this.isPlayingThroughPip != isPlayingThroughPip) {
      this.isPlayingThroughPip = isPlayingThroughPip;
      if (texturePrepared) {
        if (isPlaying) {
          getFrameFromTexture(!isPlayingThroughPip, fakeFrame);
        }

        setRendered(false, true);

        View targetTextureView = isPlayingThroughPip ? pipTextureView : mainTextureView;
        if (targetTextureView instanceof SurfaceView) {
          exoPlayer.setVideoSurfaceView((SurfaceView) targetTextureView);
        } else {
          exoPlayer.setVideoTextureView((TextureView) targetTextureView);
        }
      }
    }
  }

  private void setDisplayingFrame (boolean displayingFrame) {
    if (this.displayingFrame != displayingFrame) {
      this.displayingFrame = displayingFrame;
      invalidatePlaceholders();
      checkPreviewVisible();
    }
  }

  private void invalidatePlaceholders () {
    Bitmap bitmap = displayingFrame && fakeFrame != null && !fakeFrame.isRecycled() ? fakeFrame : null;
    mainPreviewView.setOverlayBitmap(inPipMode ? null : bitmap);
  }

  private void checkPlayState () {
    if (texturePrepared) {
      exoPlayer.setPlayWhenReady(isPlaying && (targetView != null || !awaitingCurrentView));
      checkProgressTimer();
    }
  }

  // Video Playback listeners

  @Override
  public void onPlaybackStateChanged (@Player.State int playbackState) {
    switch (playbackState) {
      case Player.STATE_ENDED: {
        if (isPlaying && source != null) { // Catch playback end only when we actually play the message
          TdlibManager.instance().player().playNextMessageInQueue();
        }
        break;
      }
    }
  }

  @Override
  public void onPlaybackSpeedChanged (int newSpeed) {
    if (exoPlayer != null) {
      exoPlayer.setPlaybackParameters(TGPlayerController.newPlaybackParameters(false, newSpeed));
    }
  }

  @Override
  protected void displayPlaybackError (PlaybackException e) {
    UI.showToast(U.isUnsupportedFormat(e) ? R.string.VideoPlaybackUnsupported : R.string.RoundVideoPlaybackError, Toast.LENGTH_SHORT);
  }

  @Override
  public void onPlayerError (@NonNull PlaybackException e) {
    super.onPlayerError(e);
    stopPlayback();
  }

  @Override
  public void onRenderedFirstFrame () {
    Log.i(Log.TAG_VIDEO, "onRenderedFirstFrame");
    this.hasRenderedAnyFrame = true;
    setRendered(true, true);
  }

  // Playback Control

  private boolean isPlaying;
  private float volume = 1f;

  @Override
  protected void playPause (boolean isPlaying) {
    if (this.isPlaying != isPlaying) {
      this.isPlaying = isPlaying;
      if (this.exoPlayer != null) {
        this.exoPlayer.setPlayWhenReady(isPlaying);
      }
      checkPlayingThroughPip();
      checkProgressTimer();
    }
  }

  private void setVolume (float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      if (this.exoPlayer != null) {
        this.exoPlayer.setVolume(volume);
      }
    }
  }

  private boolean progressTimerStarted;

  private void checkProgressTimer () {
    boolean progressTimer = exoPlayer != null && isPlaying;
    if (this.progressTimerStarted != progressTimer) {
      this.progressTimerStarted = progressTimer;
      Log.i(Log.TAG_VIDEO, "progressTimerStarted -> %b", progressTimer);
      handler.removeMessages(ACTION_PROGRESS_TICK);
      onProgressTick();
    }
  }

  private float progress;
  private long playPosition = -1;
  private long playDuration = -1;

  private void setPlayProgress (float progress, long playPosition, long playDuration) {
    if (this.progress != progress || this.playPosition != playPosition || this.playDuration != playDuration) {
      // boolean reset = this.remainingSeconds != remainingSeconds || this.totalSeconds != totalSeconds;

      this.progress = progress;
      this.playDuration = playDuration;
      if (this.playPosition != playPosition) {
        this.playPosition = playPosition;
        if (object != null) {
          TdlibManager.instance().player().setPlayProgress(tdlib, object.chatId, object.id, TD.getFileId(object), progress, playPosition, playDuration, false);
        }
      }

      setVisualProgress(MathUtils.clamp(progress));
    }
  }

  private float visualProgress;

  public float getVisualProgress () {
    return visualProgress;
  }

  private void setVisualProgress (float progress) {
    if (this.visualProgress != progress) {
      this.visualProgress = progress;
      if (texturePrepared) {
        pipProgressView.invalidate();
        mainProgressView.invalidate();
      }
    }
  }

  private void onProgressTick () {
    if (exoPlayer != null) {
      long duration = exoPlayer.getDuration();
      long position = exoPlayer.getCurrentPosition();
      if (duration != C.TIME_UNSET && position != C.TIME_UNSET) {
        float progress = duration != 0 ? MathUtils.clamp((float) position / (float) duration) : 0f;
        setPlayProgress(progress, position, duration);
      }
    }
    if (progressTimerStarted) {
      long delay = U.calculateDelayForDiameter(inPipMode ? getPlayerPipSize() : TGMessageVideo.getVideoSize(), playDuration);
      handler.sendMessageDelayed(Message.obtain(handler, ACTION_PROGRESS_TICK), delay);
    }
  }

  // Animation

  private static final int ANIMATOR_PROGRESS = 0;
  private static final int ANIMATOR_PIP_PREVIEW = 1;
  private static final int ANIMATOR_PIP_VISIBILITY = 2;
  private static final int ANIMATOR_MAIN_VISIBILITY = 3;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_PROGRESS:
        setVisualProgress(factor);
        break;
      case ANIMATOR_PIP_PREVIEW:
        setPreviewAlpha(factor);
        break;
      case ANIMATOR_PIP_VISIBILITY:
        setPipVisibilityFactor(factor);
        break;
      case ANIMATOR_MAIN_VISIBILITY:
        setMainVisibilityFactor(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }


  // View tracker

  private boolean awaitingCurrentView;

  private void findOrAwaitTargetView (boolean byUserRequest, boolean hadObject, float currentProgress) {
    MessageViewGroup newTarget = null;
    ViewController<?> c = context.navigation().getCurrentStackItem();
    if (object != null && c != null && c instanceof MessagesController) {
      View foundTarget = ((MessagesController) c).getManager().findMessageView(object.chatId, object.id);
      if (foundTarget == null || foundTarget instanceof MessageViewGroup) {
        newTarget = (MessageViewGroup) foundTarget;
        if (!inPipMode || newTarget != null) {
          if (((MessagesController) c).centerMessage(object.chatId, object.id, !byUserRequest, hadObject || !byUserRequest)) {
            awaitingCurrentView = c.isFocused();
          }
        }
      }
    }
    if (awaitingCurrentView) {
      if (pipTranslateY == 0 && pipTranslateX == 0) {
        animatePipClose(MODE_HIDE, currentProgress);
      } else {
        animatePipClose(MODE_SWITCH, currentProgress);
        pipParentView.forceApplyPosition();
      }
      setInPipMode(false, false);
    } else if (inPipMode) {
      animatePipClose(MODE_SWITCH, currentProgress);
      pipParentView.forceApplyPosition();
      setPipHidden(true);
      setPipHidden(false);
    } else if (!hadObject) {
      setInPipMode(true, true);
    }
    if (currentViewVisible) {
      currentViewVisible = false;
      checkMainVisible(false);
    }
    setTargetView(newTarget, true, false);
  }

  private MessageViewGroup targetView;
  private boolean currentViewVisible;
  private MessagesController targetController;

  public void setAttachedToView (MessageViewGroup viewGroup, MessagesController targetController) {
    this.targetController = targetController;
    setTargetView(viewGroup, true, false);
  }

  private void setTargetView (@Nullable MessageViewGroup view, boolean callListeners, boolean firstTime) {
    if (object != null) {
      if (targetView != view) {
        Log.i(Log.TAG_VIDEO, "targetView %s -> %s", targetView != null ? targetView.getClass().getName() : "null", view != null ? view.getClass().getName() : "null");
        this.targetView = view;
      }
      checkCurrentViewVisible(callListeners, firstTime);
    }
  }

  private MessageViewGroup currentOwnedOverlay;

  private void setOwnedOverlay (@Nullable MessageViewGroup group) {
    if (currentOwnedOverlay != group) {
      Log.i(Log.TAG_VIDEO, "ownedOverlay %s -> %s", currentOwnedOverlay != null ? currentOwnedOverlay.getClass().getSimpleName() : "null", group != null ? group.getClass().getSimpleName() : "null");
      if (currentOwnedOverlay != null) {
        detachOverlay(currentOwnedOverlay);
      }
      currentOwnedOverlay = group;
      if (group != null) {
        attachOverlay(group);
      }
    }
  }

  private void detachOverlay (@NonNull MessageViewGroup viewGroup) {
    MessageOverlayView overlayView = viewGroup.getOverlayView();
    ((ViewGroup) overlayView.getParent()).removeView(overlayView);
    overlayView.setTranslationY(0);
    overlayView.setTranslationX(0);
    viewGroup.addView(overlayView);
  }

  private void attachOverlay (@NonNull MessageViewGroup viewGroup) {
    MessageOverlayView overlayView = viewGroup.getOverlayView();
    viewGroup.removeView(overlayView);
    rootView.addView(overlayView);
    overlayView.setTranslationX(overlayTranslationX);
    overlayView.setTranslationY(overlayTranslationY);
  }

  public void onMessagesScroll () {
    if (object != null && targetView != null) {
      checkCurrentViewVisible(true, false);
    }
  }

  public void onMessageTranslate (long chatId, long messageId) {
    if (object != null && object.chatId == chatId && object.id == messageId && targetView != null) {
      layoutPlayer();
    }
  }

  public void checkLayout () {
    if (object != null && targetView != null) {
      checkCurrentViewVisible(true, false);
    }
  }

  private void checkCurrentViewVisible (boolean callListeners, boolean firstTime) {
    ViewController<?> c = targetController != null ? targetController : context.navigation() != null ? context.navigation().getCurrentStackItem() : null;
    boolean isVisible = targetView != null && (c != null && c instanceof MessagesController && c.getChatId() == object.chatId) && layoutPlayer();
    if (this.currentViewVisible != isVisible || firstTime) {
      this.currentViewVisible = isVisible;

      setOwnedOverlay(isVisible && object != null && rootView != null ? targetView : null);

      Log.i(Log.TAG_VIDEO, "currentViewVisible -> %b, callListeners: %b", isVisible, callListeners);
      checkMainVisible(true);
      if (callListeners) {
        if (isVisible) {
          onTargetFound();
        } else {
          onTargetLost();
        }
      }
    }
  }

  private void onTargetFound () {
    Log.v(Log.TAG_VIDEO, "onTargetFound, awaitingCurrentView: %b", awaitingCurrentView);
    if (awaitingCurrentView) {
      checkPlayState();
      awaitingCurrentView = false;
    } else {
      setInPipMode(false, true);
    }
  }

  private void onTargetLost () {
    Log.v(Log.TAG_VIDEO, "onTargetLost");
    setInPipMode(true, true);
  }

  // Crazy layouting shit, but works best way in terms of performance

  private float overlayTranslationX;
  private float overlayTranslationY;

  private boolean layoutPlayer () {
    TGMessage msg = targetView.getMessageView().getMessage();

    final float translationX = targetView.getVideoPlayerView().getTranslationX();
    float totalX = targetView.getLeft() + translationX;
    float totalY = targetView.getTop();

    ViewParent parent = targetView.getParent();
    if (parent != null) {
      totalY += ((ViewGroup) parent).getTranslationY();
    }

    NavigationController navigation = context.navigation();
    final boolean isAnimatingBackward = navigation.isAnimatingBackward();
    ViewController<?> current = navigation.getCurrentStackItem();
    ViewController<?> previous = navigation.getPreviousStackItem();
    final MessagesController m;
    if (targetController != null) {
      m = targetController;
    } else if (current instanceof MessagesController && current.getChatId() == object.chatId) {
      m = (MessagesController) current;
    } else if (previous instanceof MessagesController && previous.getChatId() == object.chatId) {
      m = (MessagesController) previous;
    } else {
      m = null;
    }
    boolean abort = false;
    if (m != null) {
      int bottomOffset = m.getInputOffset(true);
      if (!m.isFocused()) {
        totalX += m.get().getTranslationX();
        abort = m.get().getAlpha() == 0f;
      }
      if (m.needTabs()) {
        totalX -= m.getPagerScrollOffsetInPixels();
        abort = m.getPagerScrollOffset() >= 1f;
      }
      MessagesRecyclerView recyclerView = m.getMessagesView();
      int translationY = (int) recyclerView.getTranslationY() - Views.getBottomMargin(recyclerView);
      int topOffset = m.getTopOffset();
      topOffset += UI.getContext(context).navigation().getHeaderView().getFilling().getPlayerOffset();
      setMargins(topOffset + Math.max(0, translationY), bottomOffset + Math.max(-translationY, 0));
      abort = abort || !m.isFocused() && isAnimatingBackward;
    } else {
      // setBottomMargin(0);
      abort = true;
    }

    overlayTranslationX = totalX;
    overlayTranslationY = totalY;

    if (currentOwnedOverlay != null) {
      MessageOverlayView overlay = currentOwnedOverlay.getOverlayView();
      overlay.setTranslationX(totalX - translationX);
      overlay.setTranslationY(totalY);
    }

    totalX += msg.getContentX();
    totalY += msg.getContentY();

    mainPlayerView.setTranslationX(totalX);
    mainPlayerView.setTranslationY(totalY);

    if (abort) {
      return false;
    }

    int playerSize = TGMessageVideo.getVideoSize();

    int navigationWidth = navigation.get().getMeasuredWidth();
    int navigationHeight = navigation.get().getMeasuredHeight();

    return totalX > -playerSize && totalX < navigationWidth && totalY >= -playerSize + HeaderView.getPlayerSize() && totalY < navigationHeight;
  }

  private void setMargins (int top, int bottom) {
    rootView.setClip(top, bottom);
  }

  // Acitivty state

  private boolean pausedByRequest;

  @Override
  public void onActivityPause () {
    if (isPlaying) {
      pausedByRequest = true;
      playPause(false);
    }
  }

  @Override
  public void onActivityResume () {
    if (pausedByRequest) {
      pausedByRequest = false;
      playPause(true);
    }
  }

  @Override
  public void onActivityDestroy () {
    destroy();
  }

  @Override
  public void onActivityPermissionResult (int code, boolean granted) { }

  // Handler Messages

  private static final int ACTION_DISPATCH_FILE_STATE = 0;
  private static final int ACTION_PROGRESS_TICK = 1;
  private static final int ACTION_FILE_PROGRESS = 2;

  private void processMessage (Message msg) {
    switch (msg.what) {
      case ACTION_DISPATCH_FILE_STATE: {
        Object[] data = (Object[]) msg.obj;
        onFileLoadStateChanged((Tdlib) data[0], msg.arg1, msg.arg2, (TdApi.File) data[1]);
        break;
      }
      case ACTION_PROGRESS_TICK: {
        onProgressTick();
        break;
      }
      case ACTION_FILE_PROGRESS: {
        onFileLoadProgress((TdApi.File) msg.obj);
        break;
      }
    }
  }

  // Handler

  private static class VideoHandler extends Handler {
    private final RoundVideoController controller;

    public VideoHandler (RoundVideoController controller) {
      super(Looper.getMainLooper());
      this.controller = controller;
    }

    @Override
    public void handleMessage (Message msg) {
      controller.processMessage(msg);
    }
  }

  // Fake view

  private static class FakeView extends CircleFrameLayout {
    public FakeView (Context context) {
      super(context);
      setWillNotDraw(false);
    }

    private FactorAnimator scheduledAnimator;
    private float animateToFactor;

    public void startAnimatorOnLayout (FactorAnimator animator, float toFactor) {
      this.scheduledAnimator = animator;
      this.animateToFactor = toFactor;
    }

    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
      super.onLayout(changed, left, top, right, bottom);
      if (scheduledAnimator != null) {
        scheduledAnimator.animateTo(animateToFactor);
        scheduledAnimator = null;
      }
    }

    private Bitmap bitmap;

    public void setBitmap (Bitmap bitmap) {
      this.bitmap = bitmap;
      invalidate();
    }

    private float progress;

    public void setProgress (float progress) {
      if (this.progress != progress) {
        this.progress = progress;
        invalidate();
      }
    }

    private float removeDegrees;

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);

      final double removeDistance = Paints.videoStrokePaint().getStrokeWidth();
      final double totalDistance = (int) (2.0 * Math.PI * (double) (getMeasuredWidth() / 2));
      removeDegrees = ((float) removeDistance / (float) totalDistance) * 360f;
    }

    @Override
    protected void onDraw (Canvas c) {
      DrawAlgorithms.drawScaledBitmap(this, c, bitmap);
      if (progress != 0f) {
        RectF rectF = Paints.getRectF();
        int padding = Screen.dp(1.5f);

        rectF.set(padding, padding, getMeasuredWidth() - padding, getMeasuredHeight() - padding);
        c.drawArc(rectF, -90, (360f - removeDegrees) * progress, false, Paints.videoStrokePaint());
      }
    }
  }

  // PiP parent

  private static class InterceptPipLayout extends FrameLayoutFix implements FactorAnimator.Target, FlingDetector.Callback {
    private FlingDetector detector;
    public InterceptPipLayout (@NonNull Context context) {
      super(context);
      detector = new FlingDetector(context, this);
    }

    @Override
    public boolean onFling (float velocityX, float velocityY) {
      float maxVelocity = Math.max(Math.abs(velocityX), Math.abs(velocityY));

      if (hasTarget && isDragging && context.pipPlayerView != null && context.pipPlayerView.getParent() != null && maxVelocity > Screen.dp(10f)) {
        CircleFrameLayout playerView = context.pipPlayerView;
        ViewGroup parent = (ViewGroup) playerView.getParent();
        int sourceGravity = calculateCurrentGravity();

        FrameLayoutFix.LayoutParams sourceParams = (LayoutParams) parent.getLayoutParams();

        float x = playerView.getLeft() + playerView.getTranslationX();// + playerView.getMeasuredWidth() / 2;
        // float y = playerView.getTop() + playerView.getTranslationY();// + playerView.getMeasuredHeight() / 2;

        float rectWidth = parent.getMeasuredWidth();
        // float rectHeight = parent.getMeasuredHeight();

        final float checkFactor = 1.1f;
        float checkLeft = sourceParams.leftMargin * checkFactor;
        float checkRight = rectWidth - (sourceParams.rightMargin + playerView.getMeasuredWidth()) * checkFactor;

        double degrees = velocityX != 0 || velocityY != 0 ? Math.toDegrees(Math.atan2(velocityY, velocityX)) : 0;

        int gravity = 0;
        boolean dismiss = false;

        // 0 degrees is right
        // 180 is left
        // -180..0 are top
        // 0..180 are bottom
        if (degrees <= -30 && degrees >= -180 + 30) {
          gravity |= Gravity.TOP;
        } else if (degrees >= 30 && degrees <= 180 - 30) {
          gravity |= Gravity.BOTTOM;
        } else {
          gravity |= sourceGravity & Gravity.VERTICAL_GRAVITY_MASK;
        }

        if (Math.abs(degrees) >= 180 - 75) {
          gravity |= Gravity.LEFT;
          dismiss = x < checkLeft && maxVelocity > Screen.dp(20f);
        } else if (Math.abs(degrees) <= 75) {
          gravity |= Gravity.RIGHT;
          dismiss = x > checkRight && maxVelocity > Screen.dp(20f);
        } else {
          gravity |= sourceGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        }

        dropIntercept(false, false);
        if (dismiss) {
          context.dismissPlayer();
        } else {
          animateGravity(gravity, maxVelocity);
        }

        return true;
      }

      return false;
    }

    private FactorAnimator positionAnimator;

    public int calculateCurrentGravity () {
      CircleFrameLayout playerView = context.pipPlayerView;
      if (playerView == null) {
        return Gravity.RIGHT | Gravity.TOP;
      }

      LayoutParams params = (LayoutParams) playerView.getLayoutParams();
      if (playerView.getParent() == null) {
        return params.gravity;
      }

      float actualCenterX = playerView.getLeft() + playerView.getTranslationX() + playerView.getMeasuredWidth() / 2;
      float actualCenterY = playerView.getTop() + playerView.getTranslationY() + playerView.getMeasuredHeight() / 2;

      ViewGroup parent = (ViewGroup) playerView.getParent();

      int gravity;

      if (actualCenterX < parent.getMeasuredWidth() / 2) {
        gravity = Gravity.LEFT;
      } else {
        gravity = Gravity.RIGHT;
      }

      int topOffset = params.topMargin - getPlayerPipMargin();
      if (actualCenterY < topOffset + (parent.getMeasuredHeight() - topOffset) / 2) {
        gravity |= Gravity.TOP;
      } else {
        gravity |= Gravity.BOTTOM;
      }

      return gravity;
    }

    public void dropIntercept (boolean animated, boolean applyPosition) { // If false, do nothing
      if (animated && hasTarget && context.pipPlayerView != null) {
        LayoutParams params = (LayoutParams) context.pipPlayerView.getLayoutParams();
        int gravity = params.gravity;
        boolean dismissed = false;
        if (applyPosition) {
          gravity = calculateCurrentGravity();
          if (context.getPipDismissAlpha() <= .6f) {
            context.dismissPlayer();
            dismissed = true;
          }
        }
        if (!dismissed) {
          animateGravity(gravity, -1);
        }
      }

      startX = -1; startY = -1;
      hasTarget = false;
    }

    private float fromX, fromY, toX, toY;
    private int toGravity;

    private void animateGravity (int gravity, float velocity) {
      CircleFrameLayout playerView = context.pipPlayerView;

      if (playerView == null) {
        return;
      }

      this.toGravity = gravity;
      if (positionAnimator == null) {
        positionAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, 0f);
      } else {
        positionAnimator.forceFactor(0f);
      }

      this.fromX = playerView.getTranslationX();
      this.fromY = playerView.getTranslationY();

      ViewGroup parent = (ViewGroup) playerView.getParent();
      int parentWidth = parent.getMeasuredWidth();
      int parentHeight = parent.getMeasuredHeight();

      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) playerView.getLayoutParams();

      float targetLeft;
      if (isRightGravity(gravity)) {
        targetLeft = parentWidth - getPlayerPipSize() - params.rightMargin;
      } else {
        targetLeft = params.leftMargin;
      }
      float targetTop;
      if (isBottomGravity(gravity)) {
        targetTop = parentHeight - getPlayerPipSize() - params.bottomMargin;
      } else {
        targetTop = params.topMargin;
      }

      this.toX = targetLeft - playerView.getLeft();
      this.toY = targetTop - playerView.getTop();

      positionAnimator.animateTo(1f);
    }

    public void forceApplyPosition () {
      if (context.pipPlayerView != null) {
        setDragging(false, false, false);
        if (positionAnimator != null && positionAnimator.isAnimating()) {
          positionAnimator.cancel();
          context.applyPipGravity(toGravity);
        } else {
          context.applyPipGravity(calculateCurrentGravity());
        }
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case 0: {
          float x = fromX + (toX - fromX) * factor;
          float y = fromY + (toY - fromY) * factor;

          setPlayerTranslate(x, y);

          break;
        }
      }
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      switch (id) {
        case 0: {
          context.applyPipGravity(toGravity);
          break;
        }
      }
    }

    private CircleFrameLayout findPlayerUnder (float x, float y) {
      final int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        View view = getChildAt(i);
        if (view instanceof CircleFrameLayout && view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f) {
          float viewLeft = view.getLeft() + view.getTranslationX();
          float viewTop = view.getTop() + view.getTranslationY();
          if (x >= viewLeft && x <= viewLeft + view.getMeasuredWidth() && y >= viewTop && y <= viewTop + view.getMeasuredHeight()) {
            if (positionAnimator != null && positionAnimator.isAnimating()) {
              positionAnimator.cancel();
            }
            return (CircleFrameLayout) view;
          }
        }
      }
      return null;
    }

    private float startX = -1, startY = -1;
    private boolean isDragging;

    private void setDragging (boolean isDragging, boolean animated, boolean applyPosition) {
      if (this.isDragging != isDragging) {
        this.isDragging = isDragging;
        if (!isDragging) {
          dropIntercept(animated, applyPosition);
        }
      }
    }

    private RoundVideoController context;

    public void setContext (RoundVideoController context) {
      this.context = context;
    }

    private void setPlayerTranslate (float x, float y) {
      context.setPipTranslate(x, y);
    }

    private float dragStartX, dragStartY;
    private float dragTranslateX, dragTranslateY;
    private boolean hasTarget;

    @Override
    public boolean onInterceptTouchEvent (MotionEvent ev) {
      // Log.i("onInterceptTouchEvent %s", ev);
      switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          startX = ev.getX();
          startY = ev.getY();
          hasTarget = findPlayerUnder(startX, startY) != null;
          detector.onTouchEvent(ev);
          return false;
        }
        case MotionEvent.ACTION_MOVE: {
          float x = ev.getX();
          float y = ev.getY();
          if (hasTarget && context.pipPlayerView != null && !isDragging && Math.max(Math.abs(x - startX), Math.abs(y - startY)) > Screen.getTouchSlop()) {
            dragStartX = x;
            dragStartY = y;
            dragTranslateX = context.pipPlayerView.getTranslationX();
            dragTranslateY = context.pipPlayerView.getTranslationY();
            setDragging(true, false, false);
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          setDragging(false, true, false);
          detector.onTouchEvent(ev);
          return false;
        }
        case MotionEvent.ACTION_UP: {
          setDragging(false, true, true);
          detector.onTouchEvent(ev);
          return false;
        }
      }
      return isDragging;
    }

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      // Log.i("onTouchEvent %s", event);
      switch (event.getAction()) {
        case MotionEvent.ACTION_MOVE: {
          if (isDragging && hasTarget) {
            detector.onTouchEvent(event);

            float x = event.getX();
            float y = event.getY();
            float diffX = x - dragStartX;
            float diffY = y - dragStartY;
            setPlayerTranslate(dragTranslateX + diffX, dragTranslateY + diffY);
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          detector.onTouchEvent(event);
          setDragging(false, true, false);
          break;
        }
        case MotionEvent.ACTION_UP:
          detector.onTouchEvent(event);
          setDragging(false, true, true);
          break;
      }

      return false;
    }
  }

  private void setExoPlayerParameters () {
    if (exoPlayer != null) {
      TdlibManager.instance().player().proximityManager().modifyExoPlayer(exoPlayer, C.CONTENT_TYPE_MOVIE);
    }
  }

  @Override
  public void onPlaybackParametersChanged (Tdlib tdlib, @NonNull TdApi.Message track) {
    if (comparePlayingObject(tdlib, track)) {
      setExoPlayerParameters();
    }
  }
}
