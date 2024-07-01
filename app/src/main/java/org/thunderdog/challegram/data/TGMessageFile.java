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
 * File created on 03/05/2015 at 10:59
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.telegram.MessageEditMediaPending;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.Animatable;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;
import me.vkryl.td.TdConstants;

public class TGMessageFile extends TGMessage {
  private int objectCount;

  private class CaptionedFile implements ListAnimator.Measurable, Animatable {
    public final int receiverId;
    public long messageId;
    public FileComponent component;
    public TdApi.FormattedText serverCaption;
    public TdApi.FormattedText pendingCaption;
    public TdApi.FormattedText translatedCaption;

    public FactorAnimator checkAnimator;

    private TdApi.FormattedText effectiveCaption;
    public final ReplaceAnimator<TextWrapper> caption;
    private TextWrapper captionWrapper;
    private long captionMediaKeyOffset;
    private final VariableFloat lastLineWidth = new VariableFloat(0);
    private final VariableFloat needBottomLineExpand = new VariableFloat(1f);

    public float getCheckFactor () {
      return checkAnimator != null ? checkAnimator.getFactor() * MathUtils.clamp(files.getMetadata().getSize() - 1f) : 0f;
    }

    public CaptionedFile (TdApi.Message message, FileComponent component, TdApi.FormattedText serverCaption) {
      this.receiverId = ++objectCount;
      this.messageId = message.id;
      this.component = component;
      this.serverCaption = serverCaption;
      this.pendingCaption = tdlib.getPendingMessageCaption(message.chatId, message.id);
      this.caption = new ReplaceAnimator<>(animator -> {
        files.measure(needAnimateChanges());
        invalidate();
      }, AnimatorUtils.DECELERATE_INTERPOLATOR, TEXT_CROSS_FADE_DURATION_MS);
      updateCaption(false);
    }

    public boolean hasTextMedia () {
      return captionWrapper != null && captionWrapper.hasMedia();
    }

    @Override
    public boolean equals (@Nullable Object obj) {
      return obj == this || (obj instanceof CaptionedFile && ((CaptionedFile) obj).messageId == this.messageId);
    }

    @Override
    public int hashCode () {
      return BitwiseUtils.hashCode(messageId);
    }

    private boolean hasCaption () {
      return !Td.isEmpty(effectiveCaption);
    }

    private boolean updateCaption (boolean animated) {
      return updateCaption(animated, false);
    }

    private boolean updateCaption (boolean animated, boolean force) {
      TdApi.FormattedText caption = translatedCaption != null ? translatedCaption : (this.pendingCaption != null ? this.pendingCaption : this.serverCaption);
      if (!Td.equalsTo(this.effectiveCaption, caption) || force) {
        this.effectiveCaption = Td.isEmpty(caption) ? null : caption;
        if (this.captionWrapper != null) {
          this.captionMediaKeyOffset += this.captionWrapper.getMaxMediaCount();
        }
        TextWrapper wrapper;
        if (!Td.isEmpty(caption)) {
          wrapper = new TextWrapper(caption.text, getTextStyleProvider(), getTextColorSet())
            .setEntities(TextEntity.valueOf(tdlib, caption, openParameters()), (wrapper1, text, specificMedia) -> {
              if (captionWrapper == wrapper1) {
                invalidateTextMediaReceiver(text, specificMedia);
              }
            })
            .setHighlightText(getHighlightedText(Highlight.Pool.KEY_FILE_CAPTION, caption.text))
            .addTextFlags(Text.FLAG_BIG_EMOJI)
            .setClickCallback(clickCallback());
          wrapper.setViewProvider(currentViews);
          wrapper.prepare(getContentMaxWidth());
        } else {
          wrapper = null;
        }
        this.caption.replace(this.captionWrapper = wrapper, animated);
        return true;
      }
      return false;
    }

    @Override
    public int getSpacingStart (boolean isFirst) {
      return isFirst ? 0 : Screen.dp(SPACING);
    }

    @Override
    public int getSpacingEnd (boolean isLast) {
      if (isLast) {
        if (hasCaption() && useBubbles() && !useForward()) {
          return Math.max(0, Screen.dp(TEXT_MARGIN) - getBubbleContentPadding());
        }
        return 0;
      }
      return Screen.dp(SPACING);
    }

    @Override
    public int getWidth () {
      return Math.max(component.getWidth(), hasCaption() ? captionWrapper.getWidth() : 0);
    }

    @Override
    public int getHeight () {
      return component.getHeight() + (hasCaption() ? Screen.dp(TEXT_MARGIN) + captionWrapper.getHeight() : 0);
    }

    // Animation

    private int calculateLastLineWidth () {
      if (captionWrapper != null) {
        if (captionWrapper.getLastLineIsRtl() != Lang.rtl()) {
          return BOTTOM_LINE_EXPAND_HEIGHT;
        }
        return captionWrapper.getLastLineWidth();
      } else {
        return component.getLastLineWidth();
      }
    }

    private boolean needExpandHeight () {
      if (useBubbles()) {
        int maxLineWidth = getRealContentMaxWidth();
        int lastLineWidth = calculateLastLineWidth();
        int bubbleTimePartWidth = computeBubbleTimePartWidth(/* includePadding */ true);
        return needExpandBubble(lastLineWidth, bubbleTimePartWidth, maxLineWidth);
      }
      return false;
    }

    private int calculateVisualLastLineWidth () {
      int lastLineWidth = calculateLastLineWidth();
      if (useBubbles()) {
        int maxLineWidth = getRealContentMaxWidth();
        int bubbleTimePartWidth = computeBubbleTimePartWidth(/* includePadding */ true);
        if (needExpandBubble(lastLineWidth, bubbleTimePartWidth, maxLineWidth)) {
          return getWidth() - bubbleTimePartWidth;
        }
      }
      return lastLineWidth;
    }

    @Override
    public void finishAnimation (boolean applyFutureState) {
      lastLineWidth.finishAnimation(applyFutureState);
      needBottomLineExpand.finishAnimation(applyFutureState);
    }

    @Override
    public boolean applyAnimation (float factor) {
      boolean changed = lastLineWidth.applyAnimation(factor);
      changed = needBottomLineExpand.applyAnimation(factor) || changed;
      return changed;
    }

    @Override
    public boolean hasChanges () {
      return this.lastLineWidth.differs(calculateVisualLastLineWidth()) ||
             this.needBottomLineExpand.differs(needExpandHeight() ? 1f : 0f);
    }

    @Override
    public void prepareChanges () {
      this.lastLineWidth.setTo(calculateVisualLastLineWidth());
      this.needBottomLineExpand.setTo(needExpandHeight() ? 1f : 0f);
    }

    @Override
    public void applyChanges () {
      this.lastLineWidth.set(calculateVisualLastLineWidth());
      this.needBottomLineExpand.set(needExpandHeight() ? 1f : 0f);
    }
  }
  private final List<CaptionedFile> filesList = new ArrayList<>();
  private final ListAnimator<CaptionedFile> files = new ListAnimator<>(animator -> {
    if (rebuildContentDimensions()) {
      requestLayout();
    } else {
      invalidate();
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

  @NonNull
  private CaptionedFile newFile (TGMessage context, TdApi.Message message) {
    return newFile(context, message, message.content);
  }

  private CaptionedFile newFile (TGMessage context, TdApi.Message message, TdApi.MessageContent content) {
    FileComponent component;
    TdApi.FormattedText caption;
    boolean disallowTouch = true;
    //noinspection SwitchIntDef
    switch (content.getConstructor()) {
      case TdApi.MessageDocument.CONSTRUCTOR: {
        TdApi.MessageDocument document = (TdApi.MessageDocument) content;
        component = new FileComponent(context, message, document.document);
        caption = document.caption;
        break;
      }
      case TdApi.MessageAudio.CONSTRUCTOR: {
        TdApi.MessageAudio audio = (TdApi.MessageAudio) content;
        component = new FileComponent(context, message, audio.audio, message, context.manager);
        caption = audio.caption;
        break;
      }
      case TdApi.MessageVoiceNote.CONSTRUCTOR: {
        TdApi.MessageVoiceNote voiceNote = (TdApi.MessageVoiceNote) content;
        component = new FileComponent(context, message, voiceNote.voiceNote, message, context.manager);
        caption = voiceNote.caption;
        disallowTouch = false;
        break;
      }
      default: {
        throw new IllegalArgumentException(content.toString());
      }
    }
    if (disallowTouch) {
      component.setDisallowBoundTouch();
    }
    component.setViewProvider(context.currentViews);
    return new CaptionedFile(message, component, caption);
  }

  protected TGMessageFile (MessagesManager context, TdApi.Message msg) {
    this(context, msg, msg.content);
  }

  protected TGMessageFile (MessagesManager context, TdApi.Message msg, TdApi.MessageContent messageContent) {
    super(context, msg);
    checkHasEditedMedia();
    filesList.add(newFile(this, msg, messageContent));
    files.reset(filesList, false);
  }

  @Override
  protected boolean isBeingEdited () {
    if (hasEditedMedia) {
      return true;
    }

    for (CaptionedFile file : filesList) {
      if (file.pendingCaption != null)
        return true;
    }
    return false;
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    return messageContent.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR
      || messageContent.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR
      || messageContent.getConstructor() == TdApi.MessageDocument.CONSTRUCTOR;
  }

  @Override
  protected boolean isSupportedMessagePendingContent (@NonNull MessageEditMediaPending pending) {
    return pending.isDocument() || pending.isAudio();
  }

  @Override
  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    checkHasEditedMedia();

    final TdApi.Message message = getMessage(messageId);
    return updateMessageContentImpl(chatId, messageId, message != null ? message.content : null) != 0 ? MESSAGE_INVALIDATED : MESSAGE_NOT_CHANGED;
  }

  @Override
  protected void onMessageContentOpened (long messageId) {
    for (CaptionedFile file : filesList) {
      if (file.messageId == messageId) {
        file.component.onContentOpened();
      }
    }
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    for (CaptionedFile file : filesList) {
      if (file.messageId == oldMessageId) {
        file.messageId = newMessageId;
        file.component.getFileProgress().updateMessageId(oldMessageId, newMessageId, success);
      }
    }
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    updateMessageContent(message, newContent, isBottomMessage);
    return true;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    return updateMessageContentImpl(message.chatId, message.id, newContent) != 0;
  }

  @Nullable
  private CaptionedFile findCaptionedFile (long messageId) {
    for (CaptionedFile file : filesList) {
      if (file.messageId == messageId) {
        return file;
      }
    }

    return null;
  }

  @Nullable
  public FileComponent findFileComponent (long messageId) {
    CaptionedFile file = findCaptionedFile(messageId);
    return file != null ? file.component : null;
  }

  private static final int FLAG_CHANGED_LAYOUT = 1;
  private static final int FLAG_CHANGED_TEXT_RECEIVERS = 1 << 1;
  private static final int FLAG_CHANGED_CONTENT_RECEIVERS = 1 << 2;

  private int updateMessageContentImpl (long chatId, long messageId, @Nullable TdApi.MessageContent content) {
    final CaptionedFile file = findCaptionedFile(messageId);
    final TdApi.Message message = getMessage(messageId);
    if (file == null || message == null) {
      return 0;
    }

    FileComponent component = file.component;
    if (component == null) {
      return 0;
    }

    int result = 0;
    FileComponent newComponent = null;


    final TdApi.FormattedText pendingCaption = tdlib.getPendingMessageCaption(chatId, messageId);
    file.pendingCaption = pendingCaption;
    boolean hadMedia = file.hasTextMedia();
    if (file.updateCaption(needAnimateChanges()) && (hadMedia || file.hasTextMedia())) {
      result |= FLAG_CHANGED_TEXT_RECEIVERS | FLAG_CHANGED_LAYOUT;
    }

    final MessageEditMediaPending pending = tdlib.getPendingMessageMedia(chatId, messageId);
    if (pending != null && pending.getFile() != null) {
      if (pending.isDocument()) {
        if (component.isDocument()) {
          component.setDoc(pending.getDocument());
        } else {
          newComponent = new FileComponent(this, message, pending.getDocument());
        }
        result |= FLAG_CHANGED_CONTENT_RECEIVERS | FLAG_CHANGED_LAYOUT;
      } else if (pending.isAudio()) {
        if (component.isAudio()) {
          component.setAudio(pending.getAudio(), getMessage(messageId), manager);
        } else {
          newComponent = new FileComponent(this, message, pending.getAudio(), message, manager);
        }
        result |= FLAG_CHANGED_CONTENT_RECEIVERS | FLAG_CHANGED_LAYOUT;
      }
    } else if (content != null && pendingCaption == null) {
      TdApi.FormattedText serverCaption = null;
      switch (content.getConstructor()) {
        case TdApi.MessageDocument.CONSTRUCTOR: {
          TdApi.MessageDocument document = (TdApi.MessageDocument) content;
          serverCaption = document.caption;
          if (component.isDocument()) {
            component.setDoc(document.document);
          } else {
            newComponent = new FileComponent(this, message, document.document);
          }
          result |= FLAG_CHANGED_CONTENT_RECEIVERS | FLAG_CHANGED_LAYOUT;
          break;
        }
        case TdApi.MessageAudio.CONSTRUCTOR: {
          TdApi.MessageAudio audio = (TdApi.MessageAudio) content;
          serverCaption = audio.caption;
          if (component.isAudio()) {
            component.setAudio(audio.audio, getMessage(messageId), manager);
          } else {
            newComponent = new FileComponent(this, message, audio.audio, message, manager);
          }
          result |= FLAG_CHANGED_CONTENT_RECEIVERS | FLAG_CHANGED_LAYOUT;
          break;
        }
        case TdApi.MessageVoiceNote.CONSTRUCTOR: {
          TdApi.MessageVoiceNote voiceNote = (TdApi.MessageVoiceNote) content;
          serverCaption = voiceNote.caption;
          if (component.isVoice()) {
            component.setVoice(voiceNote.voiceNote, getMessage(messageId), manager);
          } else {
            newComponent = new FileComponent(this, message, voiceNote.voiceNote, message, manager);
          }
          result |= FLAG_CHANGED_CONTENT_RECEIVERS | FLAG_CHANGED_LAYOUT;
          break;
        }
        default:
          break;
      }

      boolean hadTextMedia = file.hasTextMedia();
      file.serverCaption = serverCaption;
      boolean changed = file.updateCaption(needAnimateChanges());
      if (changed && (hadTextMedia || file.hasTextMedia())) {
        result |= FLAG_CHANGED_TEXT_RECEIVERS;
      }
    }

    if (newComponent != null) {
      file.component.performDestroy();
      file.component = newComponent;
      component = newComponent;
      component.buildLayout(getContentMaxWidth());
    }

    if (BitwiseUtils.hasFlag(result, FLAG_CHANGED_TEXT_RECEIVERS)) {
      invalidateTextMediaReceiver();
    }
    if (BitwiseUtils.hasFlag(result, FLAG_CHANGED_CONTENT_RECEIVERS)) {
      invalidateContentReceiver(messageId, file.receiverId);
    }
    if (BitwiseUtils.hasFlag(result, FLAG_CHANGED_LAYOUT)) {
      if (newComponent == null) {
        component.rebuildLayout();
      }
      files.measure(needAnimateChanges());
    }

    return result;
  }

  @Override
  protected void onUpdateHighlightedText () {
    if (filesList == null) return;
    for (CaptionedFile file : filesList) {
      file.updateCaption(needAnimateChanges(), true);
    }
    rebuildContent();
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    for (CaptionedFile file : filesList) {
      file.component.getFileProgress().downloadAutomatically(type);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    for (CaptionedFile file : filesList) {
      file.component.getFileProgress().notifyInvalidateTargetsChanged();
    }
  }

  @Override
  protected int getFooterPaddingTop () {
    return Screen.dp(filesList.get(filesList.size() - 1).hasCaption() ? 3f : 6f);
  }

  @Override
  protected int getFooterPaddingBottom () {
    return Screen.dp(4f);
  }

  @Override
  protected void buildContent (int maxWidth) {
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      entry.item.component.buildLayout(maxWidth);
      for (ListAnimator.Entry<TextWrapper> caption : entry.item.caption) {
        caption.item.prepare(maxWidth);
      }
      entry.item.caption.measure(false);
    }
    files.measure(false);
  }

  @Override
  protected void onMessageContainerDestroyed () {
    for (CaptionedFile file : filesList) {
      file.component.performDestroy();
      file.caption.clear(false);
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    final int maxMediaCountPerMessage = Integer.MAX_VALUE / (TdConstants.MAX_MESSAGE_GROUP_SIZE * 10);
    int startKey = 0;
    for (CaptionedFile file : filesList) {
      if (file.captionWrapper != null) {
        if (maxMediaCountPerMessage <= file.captionMediaKeyOffset)
          throw new IllegalStateException();
        file.captionWrapper.requestMedia(textMediaReceiver, startKey + file.captionMediaKeyOffset, maxMediaCountPerMessage - file.captionMediaKeyOffset);
      }
      startKey += maxMediaCountPerMessage;
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, final int startX, final int startY, int maxWidth, ComplexReceiver receiver) {
    float alpha = getTranslationLoadingAlphaValue();

    final int backgroundColor = getContentBackgroundColor();
    final int contentReplaceColor = getContentReplaceColor();
    final boolean clip = useBubbles();
    final int restoreToCount;
    if (clip) {
      restoreToCount = Views.save(c);
      c.clipRect(getActualLeftContentEdge(), getTopContentEdge(), getActualRightContentEdge(), getBottomContentEdge());
    } else {
      restoreToCount = -1;
    }
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      ImageReceiver imageReceiver = receiver.getImageReceiver(entry.item.receiverId);
      DoubleImageReceiver previewReceiver = receiver.getPreviewReceiver(entry.item.receiverId);
      RectF rectF = entry.getRectF();
      rectF.offset(0, startY);
      int pressColor = ColorUtils.alphaColor(entry.item.getCheckFactor(), Theme.getColor(getPressColorId()));
      if (useBubbles()) {
        boolean first = entry.getIndex() == 0;
        boolean last = entry.getIndex() + 1 == filesList.size();
        int left = getActualLeftContentEdge();
        int right = getActualRightContentEdge();
        int top = getTopContentEdge();
        int bottom = getBottomContentEdge();
        Paint paint = Paints.fillingPaint(pressColor);
        if (first || last) {
          RectF drawRect = Paints.getRectF();
          Path path = Paints.getPath();
          path.reset();
          drawRect.set(left, first ? top : rectF.top, right, last ? bottom : rectF.bottom);
          DrawAlgorithms.buildPath(path, drawRect, first ? getBubbleTopLeftRadius() : 0, first ? getBubbleTopRightRadius() : 0, last ? getBubbleBottomRightRadius() : 0, last ? getBubbleBottomLeftRadius() : 0);
          c.drawPath(path, paint);
        } else {
          c.drawRect(left, rectF.top, right, rectF.bottom, paint);
        }
      }
      int contentStartY = Math.round(rectF.top + entry.getSpacingStart());
      entry.item.component.draw(view, c, startX, contentStartY, previewReceiver, imageReceiver, backgroundColor, useBubbles() ? ColorUtils.compositeColor(contentReplaceColor, pressColor) : contentReplaceColor, entry.getVisibility(), entry.item.getCheckFactor());
      for (ListAnimator.Entry<TextWrapper> caption : entry.item.caption) {
        int right = useBubbles() ? startX + getContentWidth() : startX + Math.max(entry.item.component.getWidth(), caption.item.getWidth());
        caption.item.draw(c, startX, right, 0, contentStartY + entry.item.component.getHeight() + Screen.dp(TEXT_MARGIN), null, entry.getVisibility() * caption.getVisibility() * alpha, view.getTextMediaReceiver());
      }
    }
    if (clip) {
      Views.restore(c, restoreToCount);
    }
  }

  private static final float TEXT_MARGIN = 10f;
  private static final float SPACING = 3.5f;

  @Override
  public boolean needComplexReceiver () {
    return true;
  }

  @Override
  public void requestMediaContent (ComplexReceiver receiver, boolean invalidate, int invalidateArg) {
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      if (invalidate && invalidateArg > 0 && entry.item.receiverId != invalidateArg) {
        continue;
      }
      ImageReceiver imageReceiver = receiver.getImageReceiver(entry.item.receiverId);
      DoubleImageReceiver previewReceiver = receiver.getPreviewReceiver(entry.item.receiverId);
      int radius = entry.item.component.getContentRadius(Config.USE_SCALED_ROUNDINGS ? Screen.dp(Theme.getImageRadius()) : 0);
      previewReceiver.setRadius(radius);
      imageReceiver.setRadius(radius);
      if (!invalidate || invalidateArg == entry.item.receiverId) {
        entry.item.component.requestPreview(previewReceiver);
      }
      entry.item.component.requestContent(imageReceiver);
    }
  }

  @Override
  protected float getIntermediateBubbleExpandFactor () {
    return filesList.get(filesList.size() - 1).needBottomLineExpand.get();
  }

  @Override
  protected int getAnimatedBottomLineWidth (int bubbleTimePartWidth) {
    return Math.round(filesList.get(filesList.size() - 1).lastLineWidth.get());
  }

  @Override
  protected int getBottomLineContentWidth () {
    return BOTTOM_LINE_DEFINE_BY_FACTOR;
  }

  @Override
  protected int getContentWidth () {
    return Math.round(files.getMetadata().getMaximumItemWidth());
  }

  @Override
  protected void buildReactions (boolean animated) {
    if (!useBubble() || !useReactionBubbles()) {
      super.buildReactions(animated);
    } else {
      int contentWidth = Math.round(files.getMetadata().getMaximumItemWidth());
      messageReactions.measureReactionBubbles(Math.max(contentWidth, (int)(getEstimatedContentMaxWidth() * 0.75f)), computeBubbleTimePartWidth(true, true));
      messageReactions.resetReactionsAnimator(animated);
    }
  }

  @Override
  protected int getContentHeight () {
    return Math.round(files.getMetadata().getTotalHeight());
  }

  @Override
  protected void onMessageCombinedWithOtherMessage (TdApi.Message otherMessage, boolean atBottom, boolean local) {
    checkHasEditedMedia();

    CaptionedFile file = newFile(this, otherMessage);
    if (local) {
      int maxWidth = getContentMaxWidth();
      if (maxWidth > 0) {
        file.component.buildLayout(maxWidth);
        if (file.hasCaption()) {
          file.captionWrapper.prepare(maxWidth);
          file.caption.measure(false);
        }
      }
    }
    if (atBottom) {
      filesList.add(file);
    } else {
      filesList.add(0, file);
    }
    files.reset(filesList, needAnimateChanges());
    if (local) {
      invalidateContentReceiver(otherMessage.id, file.receiverId);
    }
  }

  @Override
  protected void onMessageCombinationRemoved (TdApi.Message message, int index) {
    CaptionedFile file = filesList.remove(index);
    file.component.performDestroy();
    files.reset(filesList, needAnimateChanges());
  }

  @Override
  public long findChildMessageIdUnder (float x, float y) {
    if (x >= getContentX() && x < getContentX() + getContentWidth() && y >= getContentY() && y < getContentY() + getContentHeight()) {
      for (ListAnimator.Entry<CaptionedFile> entry : files) {
        RectF rectF = entry.getRectF();
        rectF.offset(0, getContentY());
        if (y >= rectF.top && y < rectF.bottom) {
          return entry.item.messageId;
        }
      }
    }
    return 0;
  }

  @Override
  protected void onAnimatorAttachedToMessage (long messageId, FactorAnimator animator) {
    for (CaptionedFile file : filesList) {
      if (file.messageId == messageId) {
        file.checkAnimator = animator;
        break;
      }
    }
  }

  @Override
  protected void onMessageSelectionChanged (long messageId, float selectionFactor, boolean needInvalidate) {
    if (needInvalidate) {
      invalidate();
    }
  }

  // Touch

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    boolean res = super.onTouchEvent(view, e);
    for (CaptionedFile file : filesList) {
      for (ListAnimator.Entry<TextWrapper> caption : file.caption) {
        if (caption.item.onTouchEvent(view, e)) {
          res = true;
        }
      }
      if (file.component.onTouchEvent(view, e)) {
        res = true;
      }
    }
    return res;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      entry.item.component.clearTouch();
      for (ListAnimator.Entry<TextWrapper> caption : entry.item.caption) {
        if (caption.item.performLongPress(view)) {
          res = true;
        }
      }
    }
    return res;
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      if (entry.item.messageId == messageId) {
        return entry.item.component.getMediaThumbLocation(view, viewTop, viewBottom, top);
      }
    }
    return null;
  }

  // Document actions

  @Override
  protected boolean onLocaleChange () {
    boolean changed = false;
    for (ListAnimator.Entry<CaptionedFile> entry : files) {
      changed = entry.item.component.onLocaleChange() || changed;
    }
    return changed;
  }

  private TdApi.FormattedText getTranslationSafeText (TdApi.FormattedText text) {
    if (translationStyleMode() == Settings.TRANSLATE_MODE_POPUP) return text;
    return new TdApi.FormattedText(text.text.replaceAll("\uD83D\uDCC4", "\uD83D\uDCD1"), text.entities);
  }

  @Nullable
  @Override
  public TdApi.FormattedText getTextToTranslateImpl () {
    if (filesList == null) {
      return null;
    }
    if (filesList.size() == 1) {
      CaptionedFile file = filesList.get(0);
      return file.hasCaption() ? getTranslationSafeText(file.serverCaption) : null;
    }

    TdApi.FormattedText resultText = new TdApi.FormattedText("", new TdApi.TextEntity[0]);
    TdApi.FormattedText sep = new TdApi.FormattedText(translationStyleMode() == Settings.TRANSLATE_MODE_POPUP ? "\n\n": "\n\n\uD83D\uDCC4\n", new TdApi.TextEntity[0]);
    int filesWithCaption = 0;

    for (CaptionedFile file : filesList) {
      if (file.hasCaption()) {
        resultText = Td.concat(resultText, sep, getTranslationSafeText(file.serverCaption));
        filesWithCaption++;
      } else {
        resultText = Td.concat(resultText, sep);
      }
    }

    return filesWithCaption > 0 ? Td.trim(resultText) : null;
  }

  @Override
  protected void setTranslationResult (@Nullable TdApi.FormattedText text) {
    ArrayList<TdApi.FormattedText> translatedParts = null;
    if (text != null) {
      translatedParts = new ArrayList<>(filesList.size());
      String sep = "\uD83D\uDCC4";
      int indexStart = text.text.startsWith(sep) ? sep.length() : 0;
      while (true) {
        int index = text.text.indexOf(sep, indexStart);
        TdApi.FormattedText part = (index == -1) ? Td.substring(text, indexStart) : Td.substring(text, indexStart, index);
        translatedParts.add(Td.trim(part));
        if (index == -1) {
          break;
        };
        indexStart = index + sep.length();
      }
    }

    if (translatedParts != null && translatedParts.size() != filesList.size()) {
      translatedParts = null;
    }

    for (int a = 0; a < filesList.size(); a++) {
      CaptionedFile file = filesList.get(a);
      TdApi.FormattedText caption = translatedParts != null ? translatedParts.get(a) : null;
      file.translatedCaption = !Td.isEmpty(caption) ? caption : null;
      file.updateCaption(needAnimateChanges(), true);
    }
    rebuildAndUpdateContent();
    invalidateTextMediaReceiver();
    super.setTranslationResult(text);
  }



  private boolean hasEditedMedia;

  private void checkHasEditedMedia () {
    boolean hasEditedMedia = false;

    synchronized (this) {
      ArrayList<TdApi.Message> combinedMessages = getCombinedMessagesUnsafely();
      if (combinedMessages != null && !combinedMessages.isEmpty()) {
        for (TdApi.Message message: combinedMessages) {
          final MessageEditMediaPending pending = tdlib.getPendingMessageMedia(message.chatId, message.id);
          hasEditedMedia |= pending != null;
        }
      } else {
        final MessageEditMediaPending pending = tdlib.getPendingMessageMedia(msg.chatId, msg.id);
        hasEditedMedia = pending != null;
      }
    }

    this.hasEditedMedia = hasEditedMedia;
  }
}
