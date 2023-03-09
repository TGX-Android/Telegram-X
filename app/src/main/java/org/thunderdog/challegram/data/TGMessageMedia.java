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
 * File created on 03/05/2015 at 10:56
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.mediaview.MediaViewController;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.td.Td;

public class TGMessageMedia extends TGMessage {
  // private MediaWrapper mediaWrapper;
  private @Nullable TextWrapper wrapper;

  // Timer stuff

  private String timerText;
  private int timerWidth;
  // private int pTimerRight, pTimerTop;

  protected TGMessageMedia (MessagesManager context, TdApi.Message msg, @NonNull TdApi.MessagePhoto photo, TdApi.FormattedText caption) {
    super(context, msg);
    MediaWrapper mediaWrapper = new MediaWrapper(context(), tdlib, photo, msg.chatId, msg.id, this, true);
    mediaWrapper.setViewProvider(currentViews);
    init(mediaWrapper, caption);
  }

  protected TGMessageMedia (MessagesManager context, TdApi.Message msg, @NonNull TdApi.MessageVideo video, TdApi.FormattedText caption) {
    super(context, msg);
    MediaWrapper mediaWrapper = new MediaWrapper(context(), tdlib, video, msg.chatId, msg.id, this, true);
    mediaWrapper.setViewProvider(currentViews);
    init(mediaWrapper, caption);
  }

  protected TGMessageMedia (MessagesManager context, TdApi.Message msg, @NonNull TdApi.MessageAnimation animation, TdApi.FormattedText caption) {
    super(context, msg);
    MediaWrapper mediaWrapper = new MediaWrapper(context(), tdlib, animation, msg.chatId, msg.id, this, true);
    mediaWrapper.setViewProvider(currentViews);
    init(mediaWrapper, caption);
  }

  protected TGMessageMedia (MessagesManager context, TdApi.Message msg, @NonNull TdApi.Document document, TdApi.FormattedText caption) {
    super(context, msg);
    MediaWrapper mediaWrapper = new MediaWrapper(context(), tdlib, document, msg.chatId, msg.id, this, true);
    mediaWrapper.setViewProvider(currentViews);
    init(mediaWrapper, caption);
  }

  private MediaWrapper createMediaWrapper (TdApi.Message message) {
    return createMediaWrapper(message, message.content);
  }

  private MediaWrapper createMediaWrapper (TdApi.Message message, TdApi.MessageContent content) {
    MediaWrapper mediaWrapper;
    //noinspection SwitchIntDef
    switch (content.getConstructor()) {
      case TdApi.MessagePhoto.CONSTRUCTOR:
        mediaWrapper = new MediaWrapper(context(), tdlib, (TdApi.MessagePhoto) content, message.chatId, message.id, this, true);
        break;
      case TdApi.MessageVideo.CONSTRUCTOR:
        mediaWrapper = new MediaWrapper(context(), tdlib, (TdApi.MessageVideo) content, message.chatId, message.id, this, true);
        break;
      case TdApi.MessageAnimation.CONSTRUCTOR:
        mediaWrapper = new MediaWrapper(context(), tdlib, (TdApi.MessageAnimation) content, message.chatId, message.id, this, true);
        break;
      default:
        throw new UnsupportedOperationException(content.toString());
    }
    mediaWrapper.setViewProvider(currentViews);
    mediaWrapper.setSelectionAnimator(findSelectionAnimator(message.id));
    return mediaWrapper;
  }

  private MosaicWrapper mosaicWrapper;

  private void init (MediaWrapper wrapper, TdApi.FormattedText caption) {
    if (msg.chatId == 0) {
      wrapper.loadStubPhoto(((TdApi.MessagePhoto) msg.content).photo.sizes[0].type);
    }
    mosaicWrapper = new MosaicWrapper(wrapper, this);
    updateRounds();
    setCaption(caption, msg.id);
    checkCommonCaption();
    if (isHotTimerStarted()) {
      onHotTimerStarted(false);
    }
  }

  @Override
  protected boolean isBeingEdited () {
    return this.isBeingEdited;
  }

  @Override
  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    if (checkCommonCaption()) {
      rebuildContent();
      return (getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED);
    }
    return MESSAGE_NOT_CHANGED;
  }

  public TdApi.File getTargetFile () {
    // TODO
    return mosaicWrapper.getSingularItem().getTargetFile();
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    mosaicWrapper.updateMessageId(oldMessageId, newMessageId, success);
    if (captionMessageId == oldMessageId) {
      captionMessageId = newMessageId;
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    mosaicWrapper.notifyInvalidateTargetsChanged();
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    MediaViewThumbLocation location = mosaicWrapper.getMediaThumbLocation(messageId, view, viewTop, viewBottom, top);
    if (location != null) {
      location.setColorId(useBubbles() && isOutgoingBubble() ? R.id.theme_color_bubbleOut_background : R.id.theme_color_filling);
    }
    return location;
  }

  private static final float MIN_RATIO = .5f;
  private static final float MIN_RATIO_GIF = .8f;
  private static final float MAX_RATIO = 1.5f;
  private static final float MAX_RATIO_GIF = 1.08f;

  @Override
  protected boolean preferFullWidth () {
    return UI.isPortrait() && !UI.isTablet() && isChannel() && !isEventLog() && msg.content.getConstructor() != TdApi.MessageAnimation.CONSTRUCTOR && (!mosaicWrapper.isSingular() || mosaicWrapper.getAspectRatio() >= (mosaicWrapper.getSingularItem().isGif() ? MIN_RATIO_GIF : MIN_RATIO));
  }

  @Override
  protected void onMessageContainerDestroyed () {
    mosaicWrapper.destroy();
    cancelScheduledHotOpening(null, false);
    closeHot(null, true, false);
    if (wrapper != null) {
      wrapper.performDestroy();
    }
  }

  private void updateRounds () {
    if (useBubbles()) {
      mosaicWrapper.setNeedDefaultRounds(needTopContentRounding(), needBottomContentRounding());
    }
  }

  private boolean isBeingEdited;

  private boolean checkCommonCaption () {
    return checkCommonCaption(false);
  }

  private boolean checkCommonCaption (boolean force) {
    TdApi.FormattedText caption = null;
    long captionMessageId = 0;
    boolean hasEditedText = false;
    synchronized (this) {
      ArrayList<TdApi.Message> combinedMessages = getCombinedMessagesUnsafely();
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        TdApi.Message captionMessage = TD.getAlbumCaptionMessage(tdlib, combinedMessages);
        if (captionMessage != null) {
          caption = tdlib.getPendingFormattedText(captionMessage.chatId, captionMessage.id);
          if (caption != null) {
            hasEditedText = true;
          } else {
            caption = Td.textOrCaption(captionMessage.content);
          }
          captionMessageId = captionMessage.id;
        }
      } else {
        caption = tdlib.getPendingFormattedText(msg.chatId, msg.id);
        if (caption != null) {
          hasEditedText = true;
        } else {
          caption = Td.textOrCaption(msg.content);
        }
        captionMessageId = msg.id;
      }
    }
    this.isBeingEdited = hasEditedText;
    return setCaption(caption, captionMessageId, force);
  }

  @Override
  protected int getFooterPaddingBottom () {
    return Screen.dp(8f);
  }

  @Override
  protected int getFooterPaddingTop () {
    return Td.isEmpty(caption) ? Screen.dp(8f) : -Screen.dp(2f);
  }

  @Override
  protected void onBubbleHasChanged () {
    updateRounds();
  }

  // Caption

  private TdApi.FormattedText caption;
  private long captionMessageId;

  public long getCaptionMessageId () {
    return captionMessageId;
  }

  private boolean setCaption (TdApi.FormattedText caption, long messageId) {
    return setCaption(caption, messageId, false);
  }

  @Override
  protected void onUpdateHighlightedText () {
    if (mosaicWrapper != null) {
      checkCommonCaption(true);
      rebuildContent();
    }
  }

  private boolean setCaption (TdApi.FormattedText caption, long messageId, boolean force) {
    this.captionMessageId = messageId;
    if (!Td.equalsTo(this.caption, caption) || force) {
      this.caption = caption;
      if (this.wrapper != null) {
        this.wrapper.performDestroy();
      }
      if (!Td.isEmpty(caption)) {
        this.wrapper = new TextWrapper(caption.text, getTextStyleProvider(), getTextColorSet())
          .setEntities(TextEntity.valueOf(tdlib, caption, openParameters()), (wrapper, text, specificMedia) -> {
            if (this.wrapper == wrapper) {
              invalidateTextMediaReceiver(text, specificMedia);
            }
          })
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_MEDIA_CAPTION, caption.text))
          .addTextFlags(Text.FLAG_BIG_EMOJI)
          .setClickCallback(clickCallback());
        this.wrapper.setViewProvider(currentViews);
        if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
          this.wrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
        }
      } else {
        this.wrapper = null;
      }
      updateRounds();
      invalidateTextMediaReceiver();
      return true;
    }
    return false;
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (wrapper != null) {
      wrapper.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  @Override
  protected boolean needBubbleCornerFix () {
    return true;
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    mosaicWrapper.autoDownloadContent(type);
  }

  // Photo

  private static boolean isAcceptedMessageContent (TdApi.MessageContent content) {
    //noinspection SwitchIntDef
    switch (content.getConstructor()) {
      case TdApi.MessageAnimation.CONSTRUCTOR:
      case TdApi.MessageVideo.CONSTRUCTOR:
      case TdApi.MessagePhoto.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    return isAcceptedMessageContent(messageContent) && isAcceptedMessageContent(message.content);
  }

  private static final int FLAG_CHANGED_SIMPLY = 1;
  private static final int FLAG_CHANGED_RECEIVERS = 1 << 1;

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (message.viaBotUserId != 0 && oldContent.getConstructor() == TdApi.MessagePhoto.CONSTRUCTOR) {
      updateMessageContent(message, newContent, isBottomMessage);
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    int changed = 0;

    if (message.content.getConstructor() != newContent.getConstructor()) {
      MediaWrapper wrapper = createMediaWrapper(message, newContent);
      synchronized (this) {
        if (mosaicWrapper.replaceMediaWrapper(wrapper) != MosaicWrapper.MOSAIC_NOT_CHANGED) {
          changed |= FLAG_CHANGED_RECEIVERS;
        }
      }
    } else {
      MediaWrapper wrapper = mosaicWrapper.findMediaWrapperByMessageId(message.id);
      if (wrapper != null) {
        int oldContentWidth = wrapper.getContentWidth();
        int oldContentHeight = wrapper.getContentHeight();
        boolean updated;
        //noinspection SwitchIntDef
        switch (newContent.getConstructor()) {
          case TdApi.MessagePhoto.CONSTRUCTOR: {
            TdApi.MessagePhoto newPhoto = (TdApi.MessagePhoto) newContent;
            updated = wrapper.updatePhoto(message.id, newPhoto);
            break;
          }
          case TdApi.MessageVideo.CONSTRUCTOR: {
            TdApi.MessageVideo newVideo = (TdApi.MessageVideo) newContent;
            updated = wrapper.updateVideo(message.id, newVideo);
            break;
          }
          case TdApi.MessageAnimation.CONSTRUCTOR: {
            TdApi.MessageAnimation newAnimation = (TdApi.MessageAnimation) newContent;
            updated = wrapper.updateAnimation(message.id, newAnimation);
            break;
          }
          default:
            throw new UnsupportedOperationException(newContent.toString());
        }
        if (updated) {
          if (oldContentWidth != wrapper.getContentWidth() || oldContentHeight != wrapper.getContentHeight()) {
            mosaicWrapper.rebuild();
          }
          changed |= FLAG_CHANGED_RECEIVERS;
        }
      }
    }

    message.content = newContent;
    if (checkCommonCaption()) {
      changed |= FLAG_CHANGED_SIMPLY;
    }

    if (changed != 0) {
      rebuildContent();
      if ((changed & FLAG_CHANGED_RECEIVERS) != 0) {
        invalidateContentReceiver();
      }
      return true;
    }

    return false;
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePaddingSmall;
  }

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  protected void buildContent (final int origMaxWidth) {
    int maxWidth, maxHeight;
    boolean needFullWidth = useFullWidth();
    if (needFullWidth) {
      maxWidth = origMaxWidth;
      if (mosaicWrapper.isSingular()) {
        maxHeight = (int) ((float) origMaxWidth * (mosaicWrapper.getSingularItem().isGif() ? MAX_RATIO_GIF : MAX_RATIO));
        MediaWrapper item = mosaicWrapper.getSingularItem();
        float ratio = (float) origMaxWidth / (float) item.getContentWidth();
        maxHeight = Math.min(maxHeight, (int) ((float) item.getContentHeight() * ratio));
      } else {
        maxHeight = (int) ((float) origMaxWidth * .85f);
      }
    } else {
      maxWidth = getSmallestMaxContentWidth();
      maxHeight = getSmallestMaxContentHeight();
    }

    int minWidth = Screen.dp(MosaicWrapper.MIN_LAYOUT_WIDTH);
    int minHeight = Screen.dp(MosaicWrapper.MIN_LAYOUT_HEIGHT);

    if (commentButton.isVisible() && commentButton.isInline() && useBubbles() && !allowBubbleHorizontalExtend()) {
      float minContentWidth = commentButton.getAnimatedWidth(0, 1f) - getBubbleContentPadding() * 2;
      if (minContentWidth > minWidth) {
        minWidth = Math.round(MathUtils.fromTo(minWidth, minContentWidth, commentButton.getVisibility()));
      }
    }

    minWidth = Math.min(minWidth, maxWidth);
    minHeight = Math.min(minHeight, maxHeight);

    mosaicWrapper.build(maxWidth, maxHeight, minWidth, minHeight, needFullWidth ? MosaicWrapper.MODE_FIT_WIDTH : MosaicWrapper.MODE_FIT_AS_IS, false);

    if (isHot()) {
      updateTimerText();
    }

    if (wrapper != null) {
      wrapper.prepare(useBubbles() ? mosaicWrapper.getWidth() - xBubblePadding * 2 : getRealContentMaxWidth());
    }
  }

  @Override
  protected void onAnimatorAttachedToMessage (long messageId, FactorAnimator animator) {
    mosaicWrapper.setSelectionAnimator(messageId, animator);
  }

  @Override
  public long findChildMessageIdUnder (float x, float y) {
    MediaWrapper wrapper = mosaicWrapper.findMediaWrapperUnder(x, y);
    return wrapper != null ? wrapper.getSourceMessageId() : 0;
  }

  @Override
  protected void onMessageSelectionChanged (long messageId, float selectionFactor, boolean needInvalidate) {
    if (needInvalidate) {
      MediaWrapper wrapper = mosaicWrapper.findMediaWrapperByMessageId(messageId);
      if (wrapper != null) {
        int left = wrapper.getCellLeft();
        int top = wrapper.getCellTop();
        invalidateParentOrSelf(left, top, left + wrapper.getCellWidth(), top + wrapper.getCellHeight(), false);
      }
    }
  }

  @Override
  protected void onMessageCombinedWithOtherMessage (TdApi.Message otherMessage, boolean atBottom, boolean local) {
    checkCommonCaption();
    mosaicWrapper.addItem(createMediaWrapper(otherMessage), atBottom);
  }

  @Override
  protected void onMessageCombinationRemoved (TdApi.Message message, int index) {
    boolean useFullWidth = useFullWidth();
    switch (mosaicWrapper.removeItem(message.id, index)) {
      case MosaicWrapper.MOSAIC_CHANGED: {
        rebuildAndUpdateContent();
        break;
      }
      case MosaicWrapper.MOSAIC_INVALIDATED: {
        if (useFullWidth() != useFullWidth) {
          rebuildAndUpdateContent();
        }
        invalidate();
        break;
      }
    }
  }

  private boolean updateTimerText () {
    String ttl = getHotTimerText();
    if (timerText == null || !timerText.equals(ttl)) {
      timerText = ttl;
      timerWidth = (int) U.measureText(ttl, mHotPaint());
      return true;
    }
    return false;
  }

  @Override
  protected int getBottomLineContentWidth () {
    return wrapper != null && Lang.rtl() == wrapper.getLastLineIsRtl() ? wrapper.getLastLineWidth() + (xBubblePaddingSmall + xBubblePadding) * 2 : BOTTOM_LINE_EXPAND_HEIGHT;
  }

  /*@Override
  protected boolean moveBubbleTimePartToLeft () {
    return wrapper != null && wrapper.getLastLineIsRtl();
  }*/

  @Override
  protected boolean drawBubbleTimeOverContent () {
    return wrapper == null && !hasFooter();
  }

  @Override
  protected boolean allowBubbleHorizontalExtend () {
    return false;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, ComplexReceiver complexReceiver) {
    final boolean clipped = useBubbles() && !useForward();
    final int saveCount = clipped ? ViewSupport.clipPath(c, getBubbleClipPath()) : Integer.MIN_VALUE;
    mosaicWrapper.draw(view, c, startX, startY, complexReceiver, useFullWidth());
    if (clipped) {
      ViewSupport.restoreClipPath(c, saveCount);
    }

    if (timerText != null) {
      MediaWrapper mediaWrapper = mosaicWrapper.getSingularItem();
      int pTimerRight, pTimerLeft;

      int offset = Screen.dp(4f);
      if (true || isOutgoing() || useBubbles()) {
        pTimerLeft = mediaWrapper.getCellLeft() + offset;
        if (useBubbles()) {
          pTimerLeft += offset + Screen.dp(2f);
        } else {
          pTimerLeft += Screen.dp(4f);
        }
        pTimerRight = pTimerLeft + timerWidth + offset;
      } else {
        pTimerRight = mediaWrapper.getCellRight() - offset;
        if (useBubbles()) {
          pTimerRight -= offset + Screen.dp(2f);
        }
        pTimerLeft = pTimerRight - timerWidth - Screen.dp(4f);
      }
      int pTimerTop = startY + mediaWrapper.getCellHeight() - Screen.dp(4f) - Screen.dp(20f) - Screen.dp(4f);
      if (useBubbles()) {
        pTimerTop -= offset;
      }

      RectF rectF = Paints.getRectF();
      rectF.set(pTimerLeft - Screen.dp(4f), pTimerTop + Screen.dp(4f), pTimerRight, pTimerTop + Screen.dp(4f) + Screen.dp(20f));

      c.drawRoundRect(rectF, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(0x4c000000));
      c.drawText(timerText, pTimerLeft, pTimerTop + Screen.dp(18f), Paints.colorPaint(mHotPaint(), 0xffffffff));

      if (isHotTimerStarted() && !isOutgoing()) {
        int centerX = mediaWrapper.getCenterX();
        int centerY = mediaWrapper.getCenterY();
        int radius = Screen.dp(10f);

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        c.drawArc(rectF, -90f, -360f * getHotExpiresFactor(), true, Paints.fillingPaint(0xffffffff));
      }
    }

    if (wrapper != null) {
      wrapper.draw(c, getTextX(view, wrapper, false), getTextX(view, wrapper, true), Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT ? 0 : getBubbleTimePartWidth(), startY + mosaicWrapper.getHeight() + Screen.dp(TEXT_MARGIN), null, 1f, view.getTextMediaReceiver());
    }
  }

  private static float TEXT_MARGIN = 10f;

  private int getTextX (View view, @NonNull TextWrapper wrapper, boolean rtl) {
    if (rtl) {
      if (useBubbles())
        return (getActualRightContentEdge() - xBubblePadding - xBubblePaddingSmall);
      if (useFullWidth())
        return view.getMeasuredWidth() - getRealContentX();
      int startX = getContentX();
      return Math.max(startX + mosaicWrapper.getWidth(), startX + wrapper.getWidth());
    } else {
      return useBubbles() ? (useForward() ? getContentX() : getContentX() + xBubblePadding) : getRealContentX();
    }
  }

  // HOT STUFF

  @Override
  protected void onHotTimerStarted (boolean byEvent) {
    if (!isOutgoing() && mosaicWrapper != null && mosaicWrapper.getSingularItem() != null) {
      mosaicWrapper.getSingularItem().getFileProgress().setHideDownloadedIcon(true);
    }
  }

  @Override
  protected boolean needHotTimer () {
    return true;
  }

  @Override
  protected void onHotInvalidate (boolean secondsChanged) {
    MediaWrapper mediaWrapper = mosaicWrapper != null ? mosaicWrapper.getSingularItem() : null;

    if (mediaWrapper == null) {
      return;
    }

    if (secondsChanged) {
      updateTimerText();
    }

    int cellLeft = getContentX();
    int cellTop = getContentY();
    int cellRight = cellLeft + mediaWrapper.getCellWidth();
    int cellBottom = cellTop + mediaWrapper.getCellHeight();

    int centerX = (cellLeft + cellRight) / 2;
    int centerY = (cellTop + cellBottom) / 2;
    int radius = Screen.dp(15f);
    if (secondsChanged) {
      invalidate(cellLeft, cellTop, cellRight, cellBottom);
    } else {
      invalidate(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }
  }

  @Override
  protected void onMessageContentOpened (long messageId) {
    if (isOutgoing() && isHot()) {
      mosaicWrapper.getSingularItem().getFileProgress().setDownloadedIconRes(R.drawable.baseline_check_24);
    }
  }

  // Hot touch

  private boolean scheduledHotOpen, hotOpened;
  private float hotStartX, hotStartY;
  private CancellableRunnable hotOpen;

  private void showHotHint (View view) {
    context().tooltipManager().builder(view, currentViews).controller(controller()).locate((targetView, outRect) -> mosaicWrapper.getSingularItem().getFileProgress().toRect(outRect)).show(tdlib, R.string.HoldMediaTutorial);
  }

  private void cancelScheduledHotOpening (View view, boolean needHint) {
    if (hotOpen != null) {
      hotOpen.cancel();
      hotOpen = null;
      if (needHint) {
        showHotHint(view);
      }
    }
    scheduledHotOpen = false;
  }

  private void scheduleHotOpen (final MessageView view) {
    cancelScheduledHotOpening(view, false);
    hotOpen = new CancellableRunnable() {
      @Override
      public void act () {
        if (scheduledHotOpen && hotOpen == this) {
          hotOpen = null;
          openHot(view);
        }
      }
    };
    hotOpen.removeOnCancel(UI.getAppHandler());
    scheduledHotOpen = true;
    UI.post(hotOpen, 100);
  }

  private ViewParent currentParent;
  private MediaViewController secretController;
  private long hotOpenTime;

  private void openHot (final MessageView view) {
    if (!hotOpened && view != null) {
      currentParent = view.getParent();
      if (currentParent != null) {
        hotOpened = true;
        hotOpenTime = SystemClock.uptimeMillis();
        currentParent.requestDisallowInterceptTouchEvent(true);
        ViewController<?> c = context().navigation().getCurrentStackItem();
        if (c != null) {
          c.hideSoftwareKeyboard();
        }
        secretController = MediaViewController.openSecret(this);
        if (secretController == null) {
          currentParent.requestDisallowInterceptTouchEvent(false);
          hotOpened = false;
        }
      }
    }
  }

  private void closeHot (View view, boolean delayed, boolean needHint) {
    if (hotOpened) {
      hotOpened = false;
      if (needHint && SystemClock.uptimeMillis() - hotOpenTime <= 200l) {
        showHotHint(view);
      }
      if (delayed) {
        if (mosaicWrapper.getSingularItem().getVideo() != null) {
          ViewParent currentParent = this.currentParent;
          MediaViewController secretController = this.secretController;
          UI.post(() -> {
            currentParent.requestDisallowInterceptTouchEvent(false);
            secretController.closeByDelete();
          }, 20);
        } else {
          currentParent.requestDisallowInterceptTouchEvent(false);
          secretController.closeByDelete();
        }
      } else {
        currentParent.requestDisallowInterceptTouchEvent(false);
        secretController.forceClose();
      }
      currentParent = null;
      secretController = null;
    }
  }

  // Content bounds

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return 0;
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    mosaicWrapper.requestFiles(receiver, invalidate);
  }

  @Override
  protected int getContentWidth () {
    int mosaicWidth = mosaicWrapper != null ? mosaicWrapper.getWidth() : 0;
    return wrapper == null ?
      mosaicWidth :
      Math.max(mosaicWidth, wrapper.getWidth());
  }

  @Override
  protected int getContentHeight () {
    int mosaicHeight = mosaicWrapper != null ? mosaicWrapper.getHeight() : 0;
    return wrapper == null ?
      mosaicHeight :
      mosaicHeight + wrapper.getHeight() + Screen.dp(TEXT_MARGIN) + (useBubbles() && !useForward() ? Screen.dp(TEXT_MARGIN) - getBubbleContentPadding() : 0);
  }

  // Touch

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }

    int cellLeft = getContentX();
    int cellTop = getContentY();
    int cellRight = cellLeft + mosaicWrapper.getWidth();
    int cellBottom = cellTop + mosaicWrapper.getHeight();

    if (wrapper != null && wrapper.onTouchEvent(view, e)) {
      return true;
    }

    if (isHot() && mosaicWrapper.getSingularItem().getFileProgress().isLoaded()) {
      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          cancelScheduledHotOpening(view, false);
          hotStartX = e.getX();
          hotStartY = e.getY();
          scheduledHotOpen = hotStartX >= cellLeft && hotStartX <= cellRight && hotStartY >= cellTop && hotStartY <= cellBottom;
          if (scheduledHotOpen) {
            scheduleHotOpen(view);
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          if (scheduledHotOpen && Math.max(Math.abs(hotStartX - e.getX()), Math.abs(hotStartY - e.getY())) > Screen.getTouchSlop()) {
            cancelScheduledHotOpening(view, false);
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (scheduledHotOpen) {
            cancelScheduledHotOpening(view, false);
          }
          if (hotOpened) {
            closeHot(view, false, false);
          }
          break;
        }
        case MotionEvent.ACTION_UP: {
          if (scheduledHotOpen) {
            cancelScheduledHotOpening(view, true);
          }
          if (hotOpened) {
            closeHot(view, false, true);
          }
          break;
        }
      }
      return scheduledHotOpen || hotOpened;
    }

    return mosaicWrapper.onTouchEvent(view, e);
  }

  @Override
  public boolean allowLongPress (float x, float y) {
    if (!super.allowLongPress(x, y)) {
      return false;
    }
    int cellLeft = getContentX();
    int cellTop = getContentY();
    int cellRight = cellLeft + mosaicWrapper.getWidth();
    int cellBottom = cellTop + mosaicWrapper.getHeight();

    return !isHot() || x < cellLeft || x > cellRight || y < cellTop || y > cellBottom;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return mosaicWrapper.performLongPress(view) || (wrapper != null && wrapper.performLongPress(view)) || res;
  }

  public boolean isVideoFirstInMosaic (int mediaId) {
    return mosaicWrapper.isSingular() || (mosaicWrapper.getSingularItem() != null && mosaicWrapper.getSingularItem().isVideo() && mosaicWrapper.getSingularItem().getVideo().video.id == mediaId);
  }
}
