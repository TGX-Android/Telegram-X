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
 * File created on 03/05/2015 at 10:53
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.net.Uri;
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
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.animator.VariableFloat;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class TGMessageText extends TGMessage {
  private TdApi.FormattedText text;
  private final VariableFloat lastLineWidth = new VariableFloat(0f);
  private final ReplaceAnimator<TextWrapper> visibleText = new ReplaceAnimator<>(new ReplaceAnimator.Callback() {
    @Override
    public void onItemChanged (ReplaceAnimator<?> animator) {
      if (rebuildContentDimensions()) {
        requestLayout();
      } else {
        invalidate();
      }
    }

    @Override
    public boolean hasChanges (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      return lastLineWidth.differs(width);
    }

    @Override
    public void onForceApplyChanges (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      lastLineWidth.set(width);
    }

    @Override
    public void onPrepareMetadataAnimation (ReplaceAnimator<?> animator) {
      int width = effectiveWrapper != null ? effectiveWrapper.getLastLineWidth() : 0;
      lastLineWidth.setTo(width);
    }

    @Override
    public void onFinishMetadataAnimation (ReplaceAnimator<?> animator, boolean applyFuture) {
      lastLineWidth.finishAnimation(applyFuture);
    }

    @Override
    public boolean onApplyMetadataAnimation (ReplaceAnimator<?> animator, float factor) {
      return lastLineWidth.applyAnimation(factor);
    }
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, TEXT_CROSS_FADE_DURATION_MS);

  private TGWebPage linkPreview;
  private TdApi.MessageText currentMessageText, pendingMessageText;
  private final BoolAnimator linkPreviewAboveText = new BoolAnimator(0,
    (id, factor, fraction, callee) -> invalidate(),
    AnimatorUtils.DECELERATE_INTERPOLATOR, 180L
  );

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.MessageText text, @Nullable TdApi.MessageText pendingMessageText) {
    super(context, msg);
    this.currentMessageText = text;
    this.pendingMessageText = pendingMessageText;
    if (this.pendingMessageText != null) {
      setText(this.pendingMessageText.text, false);
      setLinkPreview(this.pendingMessageText.linkPreview, this.pendingMessageText.linkPreviewOptions);
    } else {
      setText(text.text, false);
      setLinkPreview(text.linkPreview, text.linkPreviewOptions);
    }
  }

  public TGMessageText (MessagesManager context, TdApi.SponsoredMessage sponsoredMessage, long inChatId, boolean isBelowAllMessages) {
    super(context, sponsoredMessage, inChatId, isBelowAllMessages);
    this.currentMessageText = (TdApi.MessageText) sponsoredMessage.content;
    setText(currentMessageText.text, false);
    // TODO button
  }

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.FormattedText text) {
    this(context, msg, new TdApi.MessageText(text, null, null), null);
  }

  public TdApi.File getTargetFile () {
    return linkPreview != null ? linkPreview.getTargetFile() : null;
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    if (linkPreview == null || linkPreview.getMediaWrapper() == null) {
      return null;
    }
    MediaViewThumbLocation location = linkPreview.getMediaWrapper().getMediaThumbLocation(view, viewTop, viewBottom, top);
    if (location != null) {
      location.setColorId(useBubbles() && isOutgoing() ? ColorId.bubbleOut_background : ColorId.filling);
    }
    return location;
  }

  public TdApi.FormattedText getText () {
    return text;
  }

  @Nullable
  public String findUriFragment (TdApi.LinkPreview linkPreview) {
    if (text.entities == null || text.entities.length == 0)
      return null;
    Uri lookupUri = Strings.wrapHttps(linkPreview.url);
    if (lookupUri == null)
      return null;
    int count = 0;
    Uri uri = null;
    for (TdApi.TextEntity entity : text.entities) {
      String url;
      //noinspection SwitchIntDef
      switch (entity.type.getConstructor()) {
        case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
          url = Td.substring(text.text, entity);
          break;
        case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
          url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
          break;
        default:
          continue;
      }
      count++;

      uri = Strings.wrapHttps(url);
      if (uri != null && uri.buildUpon().fragment(null).build().equals(lookupUri)) {
        return uri.getEncodedFragment();
      }
    }
    return count == 1 && uri != null ? uri.getEncodedFragment() : null;
  }

  @Override
  protected int onMessagePendingContentChanged (long chatId, long messageId, int oldHeight) {
    if (currentMessageText != null) {
      TdApi.MessageContent messageContent = tdlib.getPendingMessageText(chatId, messageId);
      final @EmojiMessageContentType int contentType = getEmojiMessageContentType(messageContent);
      boolean allowEmoji = !Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI);
      if (contentType != EmojiMessageContentType.NOT_EMOJI) {
        return MESSAGE_REPLACE_REQUIRED;
      }
      if (messageContent != null && Td.isAnimatedEmoji(messageContent) && !allowEmoji) {
        messageContent = new TdApi.MessageText(Td.textOrCaption(messageContent), null, null);
      }
      if (this.pendingMessageText != messageContent) {
        if (messageContent != null && !Td.isText(messageContent))
          return MESSAGE_REPLACE_REQUIRED;
        TdApi.MessageText messageText = (TdApi.MessageText) messageContent;
        this.pendingMessageText = messageText;
        final boolean textChanged, linkPreviewChanged;
        if (messageText != null) {
          textChanged = setText(messageText.text, false);
          linkPreviewChanged = setLinkPreview(messageText.linkPreview, messageText.linkPreviewOptions);
        } else {
          textChanged = setText(currentMessageText.text, false);
          linkPreviewChanged = setLinkPreview(currentMessageText.linkPreview, currentMessageText.linkPreviewOptions);
        }
        if (!textChanged && !linkPreviewChanged) {
          return MESSAGE_NOT_CHANGED;
        }
        if (linkPreviewChanged) {
          rebuildContent();
        }
        return (getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED);
      }
    }
    return MESSAGE_NOT_CHANGED;
  }

  @Nullable
  @Override
  protected TdApi.LinkPreview findLinkPreview (String link) {
    return linkPreview != null && linkPreview.isPreviewOf(link) ? linkPreview.getLinkPreview() : null;
  }

  @Override
  protected boolean hasInstantView (String link) {
    if (linkPreview == null || !linkPreview.needInstantView())
      return false;
    if (link.equals(linkPreview.getLinkPreview().url))
      return true;
    boolean found = false;
    for (TdApi.TextEntity entity : text.entities) {
      String url;
      if (Td.isUrl(entity.type)) {
        url = text.text.substring(entity.offset, entity.offset + entity.length);
      } else if (Td.isTextUrl(entity.type)) {
        url = ((TdApi.TextEntityTypeTextUrl) entity.type).url;
      } else {
        continue;
      }
      if (link.equals(url)) {
        found = true;
      } else {
        found = false;
        break;
      }
    }
    return found;
  }

  @Override
  protected boolean isBeingEdited () {
    return pendingMessageText != null;
  }

  private boolean setText (TdApi.FormattedText text, boolean parseEntities) {
    return setText(text, parseEntities, false, true);
  }

  private boolean setText (TdApi.FormattedText text, boolean parseEntities, boolean forceUpdate, boolean animated) {
    if (this.text == null || !Td.equalsTo(this.text, text) || forceUpdate) {
      animated = animated && Config.ENABLE_TEXT_ANIMATIONS && needAnimateChanges();

      this.text = text;
      TextColorSet colorSet = isErrorMessage() ? TextColorSets.Regular.NEGATIVE : getTextColorSet();
      TextWrapper.TextMediaListener textMediaListener = (wrapper, updatedText, specificTextMedia) -> {
        if (effectiveWrapper == wrapper) {
          invalidateTextMediaReceiver(updatedText, specificTextMedia);
        }
      };
      TextWrapper wrapper;
      if (translatedText != null) {
        wrapper = new TextWrapper(translatedText.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, translatedText, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, translatedText.text))
          .setClickCallback(clickCallback());
      } else if (text.entities != null || !parseEntities) {
        wrapper = new TextWrapper(text.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, text, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, text.text))
          .setClickCallback(clickCallback());
      } else {
        wrapper = new TextWrapper(text.text, getTextStyleProvider(), colorSet)
          .setEntities(Text.makeEntities(text.text, Text.ENTITY_FLAGS_ALL, null, tdlib, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, text.text))
          .setClickCallback(clickCallback());
      }
      wrapper.addTextFlags(Text.FLAG_BIG_EMOJI);
      if (useBubbles()) {
        wrapper.addTextFlags(Text.FLAG_ADJUST_TO_CURRENT_WIDTH);
      }
      if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
        wrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
      }
      wrapper.setViewProvider(currentViews);
      TextWrapper lastWrapper = effectiveWrapper;
      boolean hadMedia = lastWrapper != null && lastWrapper.hasMedia();
      if (hadMedia) {
        textMediaKeyOffset += lastWrapper.getMaxMediaCount();
      }
      wrapper.prepare(getContentMaxWidth());
      this.effectiveWrapper = wrapper;
      this.visibleText.replace(wrapper, animated);
      this.visibleText.measure(animated);

      if (hadMedia || wrapper.hasMedia()) {
        invalidateTextMediaReceiver();
      }
      return true;
    }
    return false;
  }

  @Override
  protected void onUpdateHighlightedText () {
    if (this.text != null) {
      setText(this.text, false, true, false);
      rebuildContent();
    }
  }

  private TextWrapper effectiveWrapper;

  private static final int MAX_WEB_PAGE_MEDIA_COUNT = Integer.MAX_VALUE / 4;
  private long textMediaKeyOffset;

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (effectiveWrapper == null && linkPreview == null) {
      textMediaReceiver.clear();
      return;
    }
    if (linkPreview != null) {
      linkPreview.requestTextMedia(textMediaReceiver, 0);
    } else {
      textMediaReceiver.clearReceiversRange(0, MAX_WEB_PAGE_MEDIA_COUNT);
    }
    if (effectiveWrapper != null) {
      long startKey = MAX_WEB_PAGE_MEDIA_COUNT + textMediaKeyOffset;
      effectiveWrapper.requestMedia(textMediaReceiver, startKey, Long.MAX_VALUE - startKey);
    } else {
      textMediaReceiver.clearReceiversWithHigherKey(MAX_WEB_PAGE_MEDIA_COUNT);
    }
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  private int maxWidth;

  @Override
  protected void buildContent (int maxWidth) {
    maxWidth = Math.max(maxWidth, computeBubbleTimePartWidth(false));

    this.maxWidth = maxWidth;

    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      entry.item.prepare(maxWidth);
    }
    visibleText.measure(false);

    int linkPreviewMaxWidth = getSmallestMaxContentWidth();
    if (pendingMessageText != null) {
      if (setLinkPreview(pendingMessageText.linkPreview, pendingMessageText.linkPreviewOptions))
        linkPreview.buildLayout(linkPreviewMaxWidth);
    } else if (Td.isText(msg.content) && setLinkPreview(((TdApi.MessageText) msg.content).linkPreview, ((TdApi.MessageText) msg.content).linkPreviewOptions)) {
      linkPreview.buildLayout(linkPreviewMaxWidth);
    } else if (linkPreview != null && linkPreview.getMaxWidth() != linkPreviewMaxWidth) {
      linkPreview.buildLayout(linkPreviewMaxWidth);
    }
  }

  private boolean setLinkPreview (TdApi.LinkPreview linkPreview, @Nullable TdApi.LinkPreviewOptions linkPreviewOptions) {
    if (linkPreview != null) {
      String url = null;
      if (text != null) {
        url = Td.findUrl(text, linkPreview.url, false);
      }
      if (StringUtils.isEmpty(url)) {
        url = linkPreview.url;
      }
      this.linkPreview = new TGWebPage(this, linkPreview, url, linkPreviewOptions);
      this.linkPreview.setViewProvider(currentViews);
      this.linkPreviewAboveText.setValue(linkPreviewOptions != null && linkPreviewOptions.showAboveText, needAnimateChanges());
      return true;
    } else {
      this.linkPreview = null;
      this.linkPreviewAboveText.setValue(false, false);
    }
    return false;
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (linkPreview != null) {
      linkPreview.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (linkPreview != null) {
      linkPreview.notifyInvalidateTargetsChanged();
    }
  }

  private int getWebY () {
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    return getContentY() + getTextTopOffset() + (int) ((visibleText.getMetadata().getTotalHeight() + Screen.dp(6f) * visibleText.getMetadata().getTotalVisibility()) * (1f - linkPreviewAboveText));
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    final @EmojiMessageContentType int contentType = getEmojiMessageContentType(messageContent);
    if (messageContent.getConstructor() == TdApi.MessageText.CONSTRUCTOR || messageContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR) {
      return contentType == EmojiMessageContentType.NOT_EMOJI;
    }
    return super.isSupportedMessageContent(message, messageContent);
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.MessageText oldMessageText = Td.isText(oldContent) ? (TdApi.MessageText) oldContent : null;
    TdApi.MessageText newMessageText = Td.isText(newContent) ? (TdApi.MessageText) newContent : null;
    if (!Td.equalsTo(Td.textOrCaption(oldContent), Td.textOrCaption(newContent)) ||
        !Td.equalsTo(oldMessageText != null ? oldMessageText.linkPreview : null,
                     newMessageText != null ? newMessageText.linkPreview : null) ||
        !Td.equalsTo(oldMessageText != null ? oldMessageText.linkPreviewOptions : null,
                     newMessageText != null ? newMessageText.linkPreviewOptions : null)
    ) {
      updateMessageContent(msg, newContent, isBottomMessage);
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    this.msg.content = newContent;
    TdApi.MessageText newText = Td.isText(newContent) ? (TdApi.MessageText) newContent : new TdApi.MessageText(Td.textOrCaption(newContent), null, null);
    this.currentMessageText = newText;
    if (!isBeingEdited()) {
      boolean textChanged = setText(newText.text, false);
      boolean linkPreviewChanged = setLinkPreview(newText.linkPreview, newText.linkPreviewOptions);
      if (linkPreviewChanged) {
        rebuildContent();
        invalidateContent(this);
        invalidatePreviewReceiver();
      }
      if (linkPreviewChanged || textChanged) {
        invalidate();
      }
    }
    return true;
  }

  @Override
  public boolean needImageReceiver () {
    return linkPreview != null;
  }

  @Override
  public boolean needGifReceiver () {
    return linkPreview != null && linkPreview.needGif();
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return linkPreview != null ? linkPreview.getImageContentRadius(isPreview) : 0;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestContent(receiver, getContentX(), getWebY());
    } else {
      receiver.requestFile(null);
    }
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (linkPreview != null) {
      linkPreview.autodownloadContent(type);
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    if (linkPreview != null) {
      linkPreview.requestGif(receiver, getContentX(), getWebY());
    } else {
      receiver.requestFile(null);
    }
  }

  // Text without any trash

  private int getStartXRtl (TextWrapper wrapper, int startX, int maxWidth) {
    return useBubbles() ? (Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT || wrapper.getLineCount() > 1 ? getActualRightContentEdge() - getBubbleContentPadding() : startX) : startX + maxWidth;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    float alpha = getTranslationLoadingAlphaValue();
    final int endXPadding = Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT ? 0 : getBubbleTimePartWidth();
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    ComplexReceiver textMediaReceiver = view.getTextMediaReceiver();
    int linkPreviewY = getWebY();
    final int topTextY = startY + getTextTopOffset();
    final int bottomTextY = linkPreview == null ? topTextY : linkPreviewY + linkPreview.getHeight() + Screen.dp(6f) + Screen.dp(2f);
    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      final int startXRtl = getStartXRtl(entry.item, startX, maxWidth);
      boolean needClip = entry.getVisibility() != 1f && useBubbles();
      int saveToCount = -1;
      if (needClip) {
        saveToCount = Views.save(c);
        c.clipRect(bubblePathRect);
      }
      float textAlpha = alpha * entry.getVisibility();
      if (linkPreviewAboveText == 0f || linkPreview == null || receiver == null) {
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY, null, textAlpha, textMediaReceiver);
      } else if (linkPreviewAboveText == 1f) {
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha, textMediaReceiver);
      } else {
        entry.item.beginDrawBatch(textMediaReceiver, 1);

        // top text
        int topTextHeight = (int) ((float) (entry.item.getHeight() + Screen.dp(6f)) * MathUtils.clamp(linkPreviewAboveText));
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY - topTextHeight, null, textAlpha * MathUtils.clamp(1f - linkPreviewAboveText), textMediaReceiver);

        // bottom text
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha * MathUtils.clamp(linkPreviewAboveText), textMediaReceiver);

        entry.item.finishDrawBatch(textMediaReceiver, 1);
      }
      if (needClip) {
        Views.restore(c, saveToCount);
      }
    }
    if (linkPreview != null && receiver != null) {
      int linkPreviewX = Lang.rtl() ? startX + maxWidth - linkPreview.getWidth() : startX;
      linkPreview.draw(view, c, linkPreviewX, linkPreviewY, preview, receiver, alpha, textMediaReceiver);
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawContent(view, c, startX, startY, maxWidth, null, null);
  }

  @Override
  protected int getContentHeight () {
    int height = Math.round(visibleText.getMetadata().getTotalHeight() + getTextTopOffset() * visibleText.getMetadata().getTotalVisibility());
    if (linkPreview != null) {
      if (height > 0)
        height += Screen.dp(6f);
      height += linkPreview.getHeight() + Screen.dp(2f);
    }
    return height;
  }

  @Override
  protected int getFooterPaddingTop () {
    return Td.isEmpty(text) ? -Screen.dp(3f) : Screen.dp(7f);
  }

  private int calculateTextLastLineWidth () {
    if (effectiveWrapper != null && Lang.rtl() == effectiveWrapper.getLastLineIsRtl()) {
      // TODO: support for rtl <-> non-rtl transition
      return Math.round(lastLineWidth.get());
    }
    return BOTTOM_LINE_EXPAND_HEIGHT;
  }

  @Override
  protected int getBottomLineContentWidth () {
    int textLastLineWidth = calculateTextLastLineWidth();
    float linkPreviewAboveText = this.linkPreviewAboveText.getFloatValue();
    if (linkPreviewAboveText == 0f || linkPreview == null) {
      if (linkPreview != null) {
        return linkPreview.getLastLineWidth();
      } else {
        return textLastLineWidth;
      }
    } else if (linkPreviewAboveText == 1f) {
      return textLastLineWidth;
    } else {
      // Animated
      return BOTTOM_LINE_DEFINE_BY_FACTOR;
    }
  }

  @Override
  protected float getIntermediateBubbleExpandFactor () {
    int textLastLineWidth = calculateTextLastLineWidth();
    int linkPreviewLastLineWidth = linkPreview != null ? linkPreview.getLastLineWidth() : textLastLineWidth;
    float fromExpandFactor = linkPreviewLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    float toExpandFactor = textLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    return MathUtils.fromTo(fromExpandFactor, toExpandFactor, linkPreviewAboveText.getFloatValue());
  }

  @Override
  protected int getAnimatedBottomLineWidth (int bubbleTimePartWidth) {
    int textLastLineWidth = calculateTextLastLineWidth();
    int linkPreviewLastLineWidth = linkPreview != null ? linkPreview.getLastLineWidth() : textLastLineWidth;
    float factor = linkPreviewAboveText.getFloatValue();
    if (factor == 1f || linkPreview == null) {
      return textLastLineWidth;
    } else if (factor == 0f) {
      return linkPreviewLastLineWidth;
    }
    int fromLastLineWidth = linkPreviewLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? linkPreview.getWidth() - bubbleTimePartWidth : linkPreviewLastLineWidth;
    int toLastLineWidth = /*textLastLineWidth == BOTTOM_LINE_KEEP_WIDTH ? wrapper.getWidth() - bubbleTimePartWidth : */textLastLineWidth;
    return MathUtils.fromTo(fromLastLineWidth, toLastLineWidth, factor);
  }

  @Override
  protected int getContentWidth () {
    int textWidth = Math.round(visibleText.getMetadata().getTotalWidth());
    if (linkPreview != null) {
      return Math.max(textWidth, linkPreview.getWidth());
    }
    return textWidth;
  }

  private boolean forceExpand = false;

  @Override
  protected void buildReactions (boolean animated) {
    if (linkPreview != null || !useBubble() || visibleText.isEmpty() || !useReactionBubbles() /*|| replyData != null*/) {
      super.buildReactions(animated);
    } else {
      final float maxWidthMultiply = replyData != null ? 1f : 0.7f;
      final float textWidth = Math.max(visibleText.getMetadata().getTotalWidth(), computeBubbleTimePartWidth(false));
      forceExpand = replyData == null && (textWidth < (int)(maxWidth * maxWidthMultiply)) && messageReactions.getBubblesCount() > 1 && messageReactions.getHeight() <= TGReactions.getReactionBubbleHeight();
      messageReactions.measureReactionBubbles(Math.max(Math.round(textWidth), (int) (maxWidth * maxWidthMultiply)), computeBubbleTimePartWidth(true, true));
      messageReactions.resetReactionsAnimator(animated);
    }
  }

  @Override
  public boolean getForceTimeExpandHeightByReactions () {
    return forceExpand;
  }

  public TdApi.LinkPreview getLinkPreview () {
    return linkPreview != null ? linkPreview.getLinkPreview() : null;
  }

  public TGWebPage getParsedLinkPreview () {
    return linkPreview;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    TextWrapper wrapper = effectiveWrapper;
    return (wrapper != null && wrapper.performLongPress(view)) || (linkPreview != null && linkPreview.performLongPress(view, this)) || res;
  }

  @Override
  protected void onMessageContainerDestroyed () {
    visibleText.clear(false);
    if (linkPreview != null) {
      linkPreview.performDestroy();
    }
  }

  // private int touchX, touchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }
    TextWrapper wrapper = effectiveWrapper;
    if (wrapper != null && wrapper.onTouchEvent(view, e)) {
      return true;
    }
    return (linkPreview != null && linkPreview.onTouchEvent(view, e, getContentX(), getWebY(), clickCallback()));
  }

  private TdApi.FormattedText translatedText;

  @Nullable
  @Override
  public TdApi.FormattedText getTextToTranslateImpl () {
    return text;
  }

  @Override
  protected void setTranslationResult (@Nullable TdApi.FormattedText text) {
    translatedText = text;
    setText(this.text, false, true, true);
    rebuildAndUpdateContent();
    invalidateTextMediaReceiver();
    super.setTranslationResult(text);
  }
}
