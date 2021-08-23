package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.util.MessageSourceProvider;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ColorUtils;

/**
 * Date: 30/11/2016
 * Author: default
 */

public abstract class InlineResult <T> implements MessageSourceProvider, MultipleViewProvider.InvalidateContentProvider {
  public static final int TYPE_ARTICLE = 0;
  public static final int TYPE_VIDEO = 1;
  public static final int TYPE_CONTACT = 2;
  public static final int TYPE_LOCATION = 3;
  public static final int TYPE_VENUE = 4;
  public static final int TYPE_GAME = 5;
  public static final int TYPE_GIF = 6;
  public static final int TYPE_AUDIO = 7;
  public static final int TYPE_VOICE = 8;
  public static final int TYPE_DOCUMENT = 9;
  public static final int TYPE_PHOTO = 10;
  public static final int TYPE_STICKER = 11;
  public static final int TYPE_MENTION = 12;
  public static final int TYPE_HASHTAG = 13;
  public static final int TYPE_COMMAND = 14;
  public static final int TYPE_BUTTON = 15;
  public static final int TYPE_EMOJI_SUGGESTION = 16;

  protected final BaseActivity context;
  protected final Tdlib tdlib;
  private final int type;
  protected final T data;
  private final String id;
  private long queryId;
  private int date;

  protected boolean forceDarkMode;

  protected int targetStart = -1, targetEnd = -1;

  protected final MultipleViewProvider currentViews;

  protected InlineResult (BaseActivity context, Tdlib tdlib, int type, String id, T data) {
    this.context = context;
    this.tdlib = tdlib;
    this.type = type;
    this.id = id;
    this.data = data;
    this.currentViews = new MultipleViewProvider();
    this.currentViews.setContentProvider(this);
  }

  public final T data () {
    return data;
  }

  public Tdlib tdlib () {
    return tdlib;
  }

  public void setForceDarkMode (boolean forceDarkMode) {
    this.forceDarkMode = forceDarkMode;
  }

  public int getTargetStart () {
    return targetStart;
  }

  public int getTargetEnd () {
    return targetEnd;
  }

  public boolean hasTarget () {
    return targetStart != -1 && targetEnd != -1;
  }

  public CharSequence replaceInTarget (CharSequence targetText, CharSequence within) {
    if (targetText instanceof String && within instanceof String) {
      StringBuilder b = new StringBuilder(within.length() + targetText.length());
      if (targetStart > 0) {
        b.append(targetText, 0, targetStart);
      }
      b.append(within);
      if (targetEnd < targetText.length()) {
        b.append(targetText, targetEnd, targetText.length());
      }
      return b.toString();
    } else {
      SpannableStringBuilder b = new SpannableStringBuilder();
      if (targetStart > 0) {
        b.append(targetText, 0, targetStart);
      }
      b.append(within);
      if (targetEnd < targetText.length()) {
        b.append(targetText, targetEnd, targetText.length());
      }
      return b;
    }
  }

  public InlineResult<T> setTarget (int start, int end) {
    this.targetStart = start;
    this.targetEnd = end;
    return this;
  }

  private TdApi.Message message;

  public InlineResult<T> setMessage (TdApi.Message message) {
    this.message = message;
    return this;
  }

  @Override
  public TdApi.Message getMessage () {
    return message;
  }

  public final int getType () {
    return type;
  }

  public final @Nullable String getId () {
    return id;
  }

  public final long getQueryId () {
    return queryId;
  }

  public final void setQueryId (long id) {
    this.queryId = id;
  }

  public final void setDate (int date) {
    this.date = date;
  }

  // Message shit

  @Override
  public int getSourceDate () {
    return date;
  }

  @Override
  public final long getSourceMessageId () {
    return queryId;
  }


  // List stuff

  private @Nullable ArrayList<InlineResult<?>> boundList;

  public final void setBoundList (@Nullable ArrayList<InlineResult<?>> list) {
    this.boundList = list;
  }

  private boolean forceSeparator;

  public void setForceSeparator (boolean forceSeparator) {
    this.forceSeparator = forceSeparator;
  }

  private boolean needSeparator () {
    if (isSeparatedType(type)) {
      if (boundList != null && !boundList.isEmpty()) {
        InlineResult<?> result = boundList.get(0);
        if (result.equals(this)) {
          return false;
        }
        if (result.getType() == TYPE_BUTTON) {
          return boundList.size() > 1 && !boundList.get(1).equals(this);
        }
        return true;
      }
      return forceSeparator;
    }
    return false;
  }

  private static boolean isSeparatedType (int type) {
    switch (type) {
      case TYPE_VIDEO:
      case TYPE_VENUE:
      case TYPE_LOCATION:
      case TYPE_CONTACT:
      case TYPE_AUDIO:
      case TYPE_VOICE:
      case TYPE_DOCUMENT:

      case TYPE_ARTICLE:
      case TYPE_GAME: {
        return true;
      }
    }
    return false;
  }

  public static boolean isGridType (int type) {
    return type == TYPE_STICKER;
  }

  public static boolean isFlowType (int type) {
    return type == TYPE_PHOTO || type == TYPE_GIF;
  }

  // View and drawing stuff

  public int getCellWidth () {
    return 100;
  }

  public int getCellHeight () {
    return 100;
  }

  public final void attachToView (@Nullable View view) {
    if (currentViews.attachToView(view) && view != null) {
      if (view.getMeasuredHeight() != getHeight()) {
        view.requestLayout();
      }
      onResultAttachedToView(view, true);
    }
  }

  public final void detachFromView (@Nullable View view) {
    if (currentViews.detachFromView(view) && view != null) {
      onResultAttachedToView(view, false);
    }
  }

  protected void onResultAttachedToView (@NonNull View view, boolean isAttached) { }

  public final int getHeight () {
    return getStartY() + getContentHeight();
  }

  protected final int getStartY () {
    return needSeparator() ? Math.max(1, Screen.dp(.5f)) : 0;
  }

  protected int getContentHeight () {
    return 0;
  }

  public final void invalidate () {
    currentViews.invalidate();
  }

  public final boolean hasAnyTargetToInvalidate () {
    return currentViews.hasAnyTargetToInvalidate();
  }

  public final void prepare (int width) {
    if (lastLayoutWidth == 0) {
      lastLayoutWidth = width;
      layoutInternal(width);
    }
  }

  private int lastLayoutWidth;

  public final void layout (int width, ComplexReceiver receiver) {
    if (width > 0) {
      if (width != lastLayoutWidth) {
        lastLayoutWidth = width;
        layoutInternal(width);
      }
    }
  }

  protected void layoutInternal (int contentWidth) { }

  public final void requestFiles (ComplexReceiver receiver) {
    requestContent(receiver, false);
  }

  private MediaPreview mediaPreview;

  protected final void setMediaPreview (MediaPreview mediaPreview) {
    this.mediaPreview = mediaPreview;
  }

  public MediaPreview getMediaPreview () {
    return mediaPreview;
  }

  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    receiver.clear();
  }

  @Override
  public void invalidateContent () {
    List<Reference<View>> views = currentViews.getViewsList();
    if (views != null) {
      for (Reference<View> reference : views) {
        View view = reference.get();
        if (view != null) {
          if (view instanceof CustomResultView) {
            ((CustomResultView) view).invalidateContent(this);
          } else if (view instanceof MultipleViewProvider.InvalidateContentProvider) {
            ((MultipleViewProvider.InvalidateContentProvider) view).invalidateContent();
          }
        }
      }
    }
  }

  private FactorAnimator highlightAnimator;

  public void highlight () {
    if (highlightAnimator == null) {
      highlightAnimator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
          invalidate();
        }

        @Override
        public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, 400, 1f);
      highlightAnimator.setStartDelay(2000l);
    } else {
      highlightAnimator.forceFactor(1f);
    }
    invalidate();
  }

  public final void draw (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, float anchorTouchX, float anchorTouchY, float selectFactor, int selectionIndex, @Nullable SimplestCheckBox checkBox) {
    int startY = 0;
    if (needSeparator()) {
      int width = Screen.dp(72f);
      int height = Math.max(Screen.dp(.5f), 1);
      c.drawRect(0, 0, width, height, Paints.fillingPaint(ColorUtils.fromToArgb(Theme.fillingColor(), ColorUtils.compositeColor(Theme.fillingColor(), Theme.chatSelectionColor()), selectFactor)));
      c.drawRect(width, 0, viewWidth, height, Paints.fillingPaint(Theme.separatorColor()));
      startY += height;
    }
    if (highlightAnimator != null) {
      float highlightFactor = highlightAnimator.getFactor();
      if (highlightFactor > 0f) {
        if (highlightFactor == 1f && !highlightAnimator.isAnimating()) {
          highlightAnimator.animateTo(0f);
        }
        c.drawRect(0, startY, viewWidth, viewHeight, Paints.fillingPaint(ColorUtils.alphaColor(highlightFactor, 0x28a0a0a0)));
      }
    }
    if (selectFactor != 0f) {
      c.drawRect(0, startY, viewWidth, viewHeight, Paints.fillingPaint(ColorUtils.alphaColor(selectFactor, Theme.chatSelectionColor())));
    }
    drawInternal(view, c, receiver, viewWidth, viewHeight, startY);
    if (selectFactor != 0f) {
      String counter = selectionIndex != -1 ? Integer.toString(selectionIndex + 1) : null;
      onDrawSelectionOver(c, receiver, viewWidth, viewHeight, anchorTouchX, anchorTouchY, selectFactor, counter, checkBox);
    }
  }

  public void onDrawSelectionOver (Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, float anchorTouchX, float anchorTouchY, float selectFactor, String counter, @Nullable SimplestCheckBox checkBox) {
    // override
  }

  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) { }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return false;
  }

  // Static stuff

  public static InlineResult<?> valueOf (BaseActivity context, Tdlib tdlib, TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, message, (TdApi.MessageAudio) message.content, null).setMessage(message);
      }
      case TdApi.MessageDocument.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, message, ((TdApi.MessageDocument) message.content).document).setMessage(message);
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, message, ((TdApi.MessageVoiceNote) message.content).voiceNote).setMessage(message);
      }
    }
    return null;
  }

  public static InlineResult<?> valueOf (BaseActivity context, Tdlib tdlib, TdApi.PageBlock pageBlock, TGPlayerController.PlayListBuilder builder) {
    switch (pageBlock.getConstructor()) {
      case TdApi.PageBlockAudio.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.PageBlockAudio) pageBlock, builder);
      }
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.PageBlockVoiceNote) pageBlock, Lang.getString(R.string.Audio));
      }
    }
    return null;
  }

  public static InlineResult<?> valueOf (BaseActivity context, Tdlib tdlib, String query, TdApi.InlineQueryResult result, TGPlayerController.PlayListBuilder builder) {
    switch (result.getConstructor()) {
      case TdApi.InlineQueryResultPhoto.CONSTRUCTOR: {
        return new InlineResultPhoto(context, tdlib, (TdApi.InlineQueryResultPhoto) result);
      }
      case TdApi.InlineQueryResultSticker.CONSTRUCTOR: {
        return new InlineResultSticker(context, tdlib, Emoji.instance().isSingleEmoji(query) ? query : null, (TdApi.InlineQueryResultSticker) result);
      }
      case TdApi.InlineQueryResultAnimation.CONSTRUCTOR: {
        return new InlineResultGif(context, tdlib, (TdApi.InlineQueryResultAnimation) result);
      }

      case TdApi.InlineQueryResultVideo.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultVideo) result);
      }
      case TdApi.InlineQueryResultVenue.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultVenue) result);
      }
      case TdApi.InlineQueryResultLocation.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultLocation) result);
      }
      case TdApi.InlineQueryResultContact.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultContact) result);
      }
      case TdApi.InlineQueryResultAudio.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultAudio) result, builder);
      }
      case TdApi.InlineQueryResultVoiceNote.CONSTRUCTOR: {
        return new InlineResultCommon(context, tdlib, (TdApi.InlineQueryResultVoiceNote) result);
      }
      case TdApi.InlineQueryResultDocument.CONSTRUCTOR: {
        TdApi.InlineQueryResultDocument doc = (TdApi.InlineQueryResultDocument) result;
        if (TGMimeType.isAudioMimeType(doc.document.mimeType)) {
          TdApi.InlineQueryResultAudio audio = new TdApi.InlineQueryResultAudio(doc.id, new TdApi.Audio(0, doc.title, doc.description, doc.document.fileName, doc.document.mimeType, doc.document.minithumbnail, doc.document.thumbnail, doc.document.document));
          return new InlineResultCommon(context, tdlib, audio, builder);
        } else {
          return new InlineResultCommon(context, tdlib, doc);
        }
      }

      case TdApi.InlineQueryResultArticle.CONSTRUCTOR: {
        return new InlineResultMultiline(context, tdlib, (TdApi.InlineQueryResultArticle) result);
      }
      case TdApi.InlineQueryResultGame.CONSTRUCTOR: {
        return new InlineResultMultiline(context, tdlib, (TdApi.InlineQueryResultGame) result);
      }
    }
    return null;
  }
}
