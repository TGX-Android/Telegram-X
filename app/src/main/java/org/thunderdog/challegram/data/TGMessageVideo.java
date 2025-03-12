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
 * File created on 27/08/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessageViewGroup;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.disposable.DisposableMediaViewController;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.player.RoundVideoController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.io.File;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;

public class TGMessageVideo extends TGMessage implements FileProgressComponent.SimpleListener, TGPlayerController.TrackListener {
  private TdApi.VideoNote videoNote;
  private boolean notViewed;

  public static final int RESIZE_DEFAULT_DURATION = 225;
  public static final Interpolator RESIZE_INTERPOLATOR = AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR;  // AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR
  private static final int FULL_SIZE_ANIMATOR_ID = 10001;

  private final BoolAnimator isFullSizeAnimator = new BoolAnimator(FULL_SIZE_ANIMATOR_ID, this, RESIZE_INTERPOLATOR, RESIZE_DEFAULT_DURATION);
  private long fullSizeAnimatorDuration = RESIZE_DEFAULT_DURATION;

  private FileProgressComponent fileProgress;
  private ImageFile miniThumbnail, previewFile;
  private GifFile mutedVideoFile;

  private int sourceDuration;

  private int duration;
  private String durationStr;
  private float durationWidth;

  public TGMessageVideo (MessagesManager context, TdApi.Message msg, TdApi.VideoNote videoNote, boolean isViewed) {
    super(context, msg);
    setVideoNote(videoNote);
    setNotViewed(!isViewed, false);
  }

  private void setVideoNote (TdApi.VideoNote videoNote) {
    this.videoNote = videoNote;
    this.fileProgress = new FileProgressComponent(context(), tdlib, TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE, true, getChatId(), getId());
    this.fileProgress.setSimpleListener(this);
    this.fileProgress.setViewProvider(overlayViews);
    this.fileProgress.setFile(videoNote.video, getMessage());
    if (TD.isSelfDestructTypeImmediately(getMessage())) {
      this.fileProgress.setIgnorePlayPauseClicks(true);
      this.fileProgress.setDownloadedIconRes(R.drawable.baseline_hot_once_24);
    }

    if (videoNote.minithumbnail != null) {
      this.miniThumbnail = new ImageFileLocal(videoNote.minithumbnail);
    }
    this.previewFile = newPreviewFile(tdlib, videoNote);

    this.mutedVideoFile = new GifFile(tdlib, videoNote.video, GifFile.TYPE_MPEG4);
    this.mutedVideoFile.setIsRoundVideo(msg.chatId, msg.id);
    this.mutedVideoFile.setRequestedSize(Screen.dp(200f));
    if (!Settings.instance().needAutoplayGIFs()) {
      this.mutedVideoFile.setIsStill(true);
    }

    this.sourceDuration = videoNote.duration;

    setDuration(videoNote.duration);
  }

  public static ImageFile newPreviewFile (Tdlib tdlib, TdApi.VideoNote videoNote) {
    if (videoNote != null && videoNote.thumbnail != null) {
      ImageFile imageFile = TD.toImageFile(tdlib, videoNote.thumbnail);
      if (imageFile != null) {
        imageFile.setSize(Screen.dp(200f));
      }
      return imageFile;
    }
    return null;
  }

  @Override
  protected void onMessageAttachStateChange (boolean isAttached) {
    checkTrackListenerAttached();
    if (!isAttached && isFullSizeAnimator.isAnimating()) {
      isFullSizeAnimator.setValue(isFullSizeAnimator.getValue(), false);
    }
  }

  private void checkTrackListenerAttached () {
    setTrackListenerAttached((isUnmuted || isAttachedToView()) && !isDestroyed());
  }

  private boolean isTrackListenerAttached;

  private void setTrackListenerAttached (boolean attach) {
    if (isTrackListenerAttached != attach) {
      isTrackListenerAttached = attach;
      if (attach) {
        tdlib.context().player().addTrackListener(tdlib, getMessage(), this);
      } else {
        tdlib.context().player().removeTrackListener(tdlib, getMessage(), this);
      }
    }
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    setNotViewed(!((TdApi.MessageVideoNote) newContent).isViewed, true);
    if (TD.isSelfDestructTypeImmediately(message)) {
      if (newContent != null && newContent.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR) {
        final var videoNote = ((TdApi.MessageVideoNote) newContent).videoNote;
        if (videoNote.minithumbnail != null) {
          this.miniThumbnail = new ImageFileLocal(videoNote.minithumbnail);
          invalidatePreviewReceiver();
        }
      }
    }

    return false;
  }

  @Override
  protected void onMessageContentOpened (long messageId) {
    setNotViewed(false, true);
  }

  private void setDuration (int duration) {
    if (StringUtils.isEmpty(durationStr) || this.duration != duration) {
      this.duration = duration;
      this.durationStr = Strings.buildDuration(duration);
      this.durationWidth = U.measureText(durationStr, useBubbles() ? mTimeBubble() : mTime(false));
      invalidateOverlay();
    }
  }

  public int getVideoMessageTargetHeight (boolean isExpanded) {
    return height - videoSize + (isExpanded ? + videoFullSize : videoSmallSize);
  }

  // View

  private static final int ANIMATOR_VIEW = 0;
  private FactorAnimator viewAnimator;
  private float viewFactor;

  private void setNotViewed (boolean isViewed, boolean animated) {
    if (this.notViewed != isViewed && isOutgoing()) {
      this.notViewed = isViewed;
      final float toFactor = isViewed ? 1f : 0f;
      if (animated && overlayViews.hasAnyTargetToInvalidate()) {
        if (viewAnimator == null) {
          viewAnimator = new FactorAnimator(ANIMATOR_VIEW, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.viewFactor);
        }
        viewAnimator.animateTo(toFactor);
      } else {
        if (viewAnimator != null) {
          viewAnimator.forceFactor(toFactor);
        }
        setViewFactor(toFactor);
      }
    }
  }

  private void setViewFactor (float factor) {
    if (this.viewFactor != factor) {
      this.viewFactor = factor;
      invalidateOverlay();
    }
  }

  @Override
  protected final void onChildFactorChanged (int id, float factor, float fraction) {
    switch (id) {
      case ANIMATOR_VIEW: {
        setViewFactor(factor);
        break;
      }
      case ANIMATOR_UNMUTE: {
        setUnmuteFactor(factor);
        break;
      }
      case FULL_SIZE_ANIMATOR_ID: {
        videoSize = MathUtils.fromTo(videoSmallSize, videoFullSize, isFullSizeAnimator.getFloatValue());
        if (isLayoutBuilt()) {
          rebuildContent();
          requestLayout();
          invalidateOverlay();
          invalidate();
        }
        break;
      }
    }
  }

  @Override
  public int getForwardLineColor () {
    return ColorUtils.alphaColor(1f - isFullSizeAnimator.getFloatValue(), super.getForwardLineColor());
  }

  @Override
  public int getForwardAuthorNameLeft () {
    return (int) (super.getForwardAuthorNameLeft() - Screen.dp(11f) * isFullSizeAnimator.getFloatValue());
  }

  public void setFullSizeAnimatorDuration (long fullSizeAnimatorDuration) {
    this.fullSizeAnimatorDuration = fullSizeAnimatorDuration;
  }

  @Override
  protected void onChildFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id ==  FULL_SIZE_ANIMATOR_ID) {
      fullSizeAnimatorDuration = RESIZE_DEFAULT_DURATION;
    }
  }

  // File utils

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    fileProgress.downloadAutomatically(type);
  }

  @Override
  public boolean onPlayPauseClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    return onClick(context, view, file, messageId);
  }

  @Override
  public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
    if (Config.ROUND_VIDEOS_PLAYBACK_SUPPORTED) {
      if (view.getParent() instanceof MessageViewGroup) {
        if (TD.isSelfDestructTypeImmediately(getMessage())) {
          DisposableMediaViewController.openMediaOrShowTooltip(view, this, (targetView, outRect) -> fileProgress.toRect(outRect));
        } else {
          tdlib.context().player().playPauseMessage(tdlib, msg, manager);
        }
      }
    } else {
      U.openFile(manager.controller(), "video.mp4", new File(file.local.path), "video/mp4", 0);
      readContent();
    }
    return true;
  }

  private static final int ANIMATOR_UNMUTE = 1;
  private boolean isUnmuted;
  private float unmuteFactor;
  private FactorAnimator unmuteAnimator;

  private boolean hasValidAttachedView () {
    for (View v : currentViews) {
      if (Views.isValid(v)) {
        if (v instanceof MessageView) {
          if (!((MessageView) v).isAttached()) {
            continue;
          }
        }

        return true;
      }
    }
    return false;
  }

  private float scrollCompensationFactor;

  public float getScrollCompensationFactor () {
    return scrollCompensationFactor;
  }

  private void setUnmuted (boolean unmuted) {
    if (this.isUnmuted != unmuted) {
      this.isUnmuted = unmuted;

      scrollCompensationFactor = 0.5f;
      if (!unmuted) {
        MessagesController c = messagesController();
        if (c != null) {
          RecyclerView recyclerView = c.getMessagesView();
          if (recyclerView != null) {
            final int range = recyclerView.computeVerticalScrollRange();
            final int offset = recyclerView.computeVerticalScrollOffset();
            final int extent = recyclerView.computeVerticalScrollExtent();
            if (range != extent) {
              scrollCompensationFactor = MathUtils.clamp((offset) / (float)(range - extent));
            } else {
              scrollCompensationFactor = 0.5f;
            }
          }
        }
      }

      this.isFullSizeAnimator.setDuration(fullSizeAnimatorDuration);
      this.isFullSizeAnimator.setValue(unmuted, UI.inUiThread() && hasValidAttachedView());
      final float toFactor = unmuted ? 1f : 0f;
      boolean animated = currentViews.hasAnyTargetToInvalidate();
      if (animated) {
        if (unmuteAnimator == null) {
          unmuteAnimator = new FactorAnimator(ANIMATOR_UNMUTE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.unmuteFactor);
        }
        unmuteAnimator.animateTo(toFactor);
      } else {
        if (unmuteAnimator != null) {
          unmuteAnimator.forceFactor(toFactor);
        }
        setUnmuteFactor(toFactor);
      }
    }
  }

  private void setUnmuteFactor (float factor) {
    if (this.unmuteFactor != factor) {
      this.unmuteFactor = factor;
      invalidateOverlay();
    }
  }

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    boolean unmuted = state != TGPlayerController.STATE_NONE;
    setUnmuted(unmuted);
    checkTrackListenerAttached();
    if (!unmuted) {
      setDuration(sourceDuration);
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    if (isUnmuted) {
      setDuration(playPosition != -1 && playDuration != -1 ? Math.min(sourceDuration, U.calculateRemainingSeconds(playDuration - playPosition)) : sourceDuration);
    }
  }

  // View utils

  private int videoSmallSize;
  private int videoSize;
  private int videoFullSize;

  @Override
  protected void buildContent (int maxWidth) {
    videoSmallSize = Math.min(Screen.dp(200f), maxWidth);
    videoFullSize = getVideoSize();
    videoSize = MathUtils.fromTo(videoSmallSize, videoFullSize, isFullSizeAnimator.getFloatValue());
  }

  public static int getVideoSize () {
    return Math.min(Screen.smallestSide() - Screen.dp(32), Screen.dp(640));
  }

  public boolean inSizeAnimation () {
    return isFullSizeAnimator.isAnimating();
  }

  public float getPlayerScale () {
    return (float) videoSize / getVideoSize();
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return videoSize / 2;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    receiver.requestFile(miniThumbnail, previewFile);
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    receiver.requestFile(TD.isFileLoaded(videoNote.video) ? mutedVideoFile : null);
  }

  @Override
  protected int getContentWidth () {
    return videoSize;
  }

  @Override
  protected int getContentHeight () {
    return videoSize;
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  protected int getBubbleContentPadding () {
    return Screen.dp(2f);
  }

  @Override
  protected boolean useCircleBubble () {
    return true;
  }

  @Override
  protected boolean separateReplyFromBubble () {
    return true;
  }

  @Override
  protected boolean allowBubbleHorizontalExtend () {
    return false;
  }

  @Override
  protected boolean drawBubbleTimeOverContent () {
    return true;
  }

  @Override
  protected int getAbsolutelyRealRightContentEdge (View view, int timePartWidth) {
    // ImageReceiver receiver = view.getPreviewReceiver();
    int left = getContentX();
    int right = left + videoSize;
    if (isOutgoingBubble()) {
      return right - timePartWidth;
    } else {
      return Math.min(
        (left + right) / 2 + (int) ((float) ((double) (videoSize / 2) * Math.sin(Math.toRadians(45f))) + Screen.dp(6f)),
        width - (width - videoFullSize) / 2 - timePartWidth
      );
    }
  }

  @Override
  public boolean needViewGroup () {
    return true;
  }

  @Override
  protected void onMessageAttachedToOverlayView (@NonNull View view, boolean attached) {
    fileProgress.notifyInvalidateTargetsChanged();
  }

  @Override
  public int getContentX () {
    return (int) MathUtils.fromTo(super.getContentX(), ((width - videoSize) / 2f), isFullSizeAnimator.getFloatValue());
  }

  @Override
  public int getChildrenLeft () {
    return getContentX();
  }

  @Override
  protected void drawBubble (Canvas c, Paint paint, boolean stroke, int padding) {
    final int s = Views.save(c);
    final float cx = getContentX() + videoSize / 2f;
    final float offset = cx - bubblePathRect.centerX();
    c.translate(offset, 0);

    super.drawBubble(c, paint, stroke, padding);

    Views.restore(c, s);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    startX = getContentX();
    preview.setBounds(startX, startY, startX + videoSize, startY + videoSize);
    preview.setRadius(getImageContentRadius(true));
    if (preview.needPlaceholder()) {
      preview.drawPlaceholderRounded(c, videoSize / 2f);
    }

    final boolean drawSpoiler = TD.isSelfDestructTypeImmediately(getMessage());
    final float radius = videoSize / 2f;
    final float cx = preview.centerX(), cy = preview.centerY();

    if (drawSpoiler) {
      Receiver spoilerReceiver;
      if (preview instanceof DoubleImageReceiver) {
        spoilerReceiver = ((DoubleImageReceiver) preview).getPreview();
      } else {
        spoilerReceiver = preview;
      }
      if (spoilerReceiver.isEmpty()) {
        spoilerReceiver = preview;
      }
      spoilerReceiver.draw(c);

      c.drawCircle(cx, cy, radius, Paints.fillingPaint(Theme.getColor(ColorId.spoilerMediaOverlay)));
    } else {
      preview.draw(c);
    }
  }

  @Override
  protected void drawOverlay (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    startX = getContentX();
    fileProgress.setBounds(startX, startY, startX + videoSize, startY + videoSize);
    fileProgress.draw(view, c);

    Receiver receiver = view.getPreviewReceiver();
    final int centerX = receiver.centerX();

    int x = centerX - (int) ((float) ((double) (receiver.getWidth() / 2) * Math.sin(Math.toRadians(45f))));

    int circleX;
    int contentWidth = (int) (durationWidth + Screen.dp(5f) * viewFactor);
    int textY = receiver.getBottom() - getBubbleTimePartHeight() - Screen.dp(8f);
    boolean useBubbles = useBubbles();
    if (useBubbles) {
      textY -= Screen.dp(3.5f);
      RectF rectF = Paints.getRectF();
      int padding = Screen.dp(6f);
      rectF.set(x - contentWidth - padding * 2, textY, x, textY + Screen.dp(21f));
      c.drawRoundRect(rectF, Screen.dp(12f), Screen.dp(12f), Paints.fillingPaint(getBubbleTimeColor()));
      textY -= Screen.dp(1f);
      c.drawText(durationStr, x - contentWidth - padding, textY + Screen.dp(15.5f), Paints.colorPaint(mTimeBubble(), getBubbleTimeTextColor()));
      circleX = x - Screen.dp(7f);
    } else {
      c.drawText(durationStr, x - contentWidth, textY + Screen.dp(15f), mTime(true));
      circleX = x;
    }

    if (viewFactor > 0f) {
      c.drawCircle(circleX, textY + Screen.dp(11.5f), Screen.dp(1.5f), Paints.fillingPaint(ColorUtils.alphaColor(viewFactor, useBubbles ? 0xffffffff : Theme.getColor(ColorId.online))));
    }

    final boolean drawSpoiler = TD.isSelfDestructTypeImmediately(getMessage());
    float alpha = (1f - unmuteFactor) * (1f - fileProgress.getBackgroundAlpha());
    if (alpha > 0f && !drawSpoiler) {
      int radius = Screen.dp(12f);
      int centerY = receiver.getBottom() - radius - Screen.dp(10f);

      final float scale = .6f + (1f - unmuteFactor) * .4f;
      final boolean needScale = scale != 1f;
      int restoreToCount;
      if (needScale) {
        restoreToCount = Views.save(c);
        c.scale(scale, scale, centerX, centerY);
      } else {
        restoreToCount = -1;
      }
      c.drawCircle(centerX, centerY, radius, Paints.fillingPaint(ColorUtils.alphaColor(alpha, 0x40000000)));
      Paint paint = Paints.whitePorterDuffPaint();
      paint.setAlpha((int) (255f * alpha));
      Drawable drawable = view.getSparseDrawable(R.drawable.deproko_baseline_sound_muted_24, 0);
      Drawables.draw(c, drawable, centerX - drawable.getMinimumWidth() / 2f, centerY - drawable.getMinimumHeight() / 2f, paint);
      paint.setAlpha(255);
      if (needScale) {
        Views.restore(c, restoreToCount);
      }
    }
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    return super.onTouchEvent(view, e) || fileProgress.onTouchEvent(view, e);
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    fileProgress.updateMessageId(oldMessageId, newMessageId, success);
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    BaseActivity context = UI.getContext(view.getContext());
    RoundVideoController c = context.getRoundVideoController();
    if (c.comparePlayingObject(tdlib, msg)) {
      ViewController<?> m = ViewController.findRoot(view);
      if (m instanceof MessagesController) {
        ((MessagesController) m).checkRoundVideo();
      } else {
        // Log.w("attach/detach, but MessageController not found");
      }
    }
    fileProgress.notifyInvalidateTargetsChanged();
  }
}
