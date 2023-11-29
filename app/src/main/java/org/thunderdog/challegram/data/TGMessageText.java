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
import me.vkryl.td.Td;

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

  private TGWebPage webPage;
  private TdApi.MessageText currentMessageText, pendingMessageText;
  private final BoolAnimator webPageOnTop = new BoolAnimator(0,
    (id, factor, fraction, callee) -> invalidate(),
    AnimatorUtils.DECELERATE_INTERPOLATOR, 180L
  );

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.MessageText text, @Nullable TdApi.MessageText pendingMessageText) {
    super(context, msg);
    this.currentMessageText = text;
    this.pendingMessageText = pendingMessageText;
    if (this.pendingMessageText != null) {
      setText(this.pendingMessageText.text, false);
      setWebPage(this.pendingMessageText.webPage, this.pendingMessageText.linkPreviewOptions);
    } else {
      setText(text.text, false);
      setWebPage(text.webPage, text.linkPreviewOptions);
    }
  }

  public TGMessageText (MessagesManager context, long inChatId, TdApi.SponsoredMessage sponsoredMessage) {
    super(context, inChatId, sponsoredMessage);
    this.currentMessageText = (TdApi.MessageText) sponsoredMessage.content;
    setText(currentMessageText.text, false);
  }

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.FormattedText text) {
    this(context, msg, new TdApi.MessageText(text, null, null), null);
  }

  public TdApi.File getTargetFile () {
    return webPage != null ? webPage.getTargetFile() : null;
  }

  @Override
  public MediaViewThumbLocation getMediaThumbLocation (long messageId, View view, int viewTop, int viewBottom, int top) {
    if (webPage == null || webPage.getMediaWrapper() == null) {
      return null;
    }
    MediaViewThumbLocation location = webPage.getMediaWrapper().getMediaThumbLocation(view, viewTop, viewBottom, top);
    if (location != null) {
      location.setColorId(useBubbles() && isOutgoing() ? ColorId.bubbleOut_background : ColorId.filling);
    }
    return location;
  }

  public TdApi.FormattedText getText () {
    return text;
  }

  @Nullable
  public String findUriFragment (TdApi.WebPage webPage) {
    if (text.entities == null || text.entities.length == 0)
      return null;
    Uri lookupUri = Strings.wrapHttps(webPage.url);
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
        final boolean textChanged, webPageChanged;
        if (messageText != null) {
          textChanged = setText(messageText.text, false);
          webPageChanged = setWebPage(messageText.webPage, messageText.linkPreviewOptions);
        } else {
          textChanged = setText(currentMessageText.text, false);
          webPageChanged = setWebPage(currentMessageText.webPage, currentMessageText.linkPreviewOptions);
        }
        if (!textChanged && !webPageChanged) {
          return MESSAGE_NOT_CHANGED;
        }
        if (webPageChanged) {
          rebuildContent();
        }
        return (getHeight() == oldHeight ? MESSAGE_INVALIDATED : MESSAGE_CHANGED);
      }
    }
    return MESSAGE_NOT_CHANGED;
  }

  @Nullable
  @Override
  protected TdApi.WebPage findLinkPreview (String link) {
    return webPage != null && webPage.isPreviewOf(link) ? webPage.getWebPage() : null;
  }

  @Override
  protected boolean hasInstantView (String link) {
    if (webPage == null || !webPage.needInstantView())
      return false;
    if (link.equals(webPage.getWebPage().url))
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
    if (effectiveWrapper == null && webPage == null) {
      textMediaReceiver.clear();
      return;
    }
    if (webPage != null) {
      webPage.requestTextMedia(textMediaReceiver, 0);
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

    int webPageMaxWidth = getSmallestMaxContentWidth();
    if (pendingMessageText != null) {
      if (setWebPage(pendingMessageText.webPage, pendingMessageText.linkPreviewOptions))
        webPage.buildLayout(webPageMaxWidth);
    } else if (Td.isText(msg.content) && setWebPage(((TdApi.MessageText) msg.content).webPage, ((TdApi.MessageText) msg.content).linkPreviewOptions)) {
      webPage.buildLayout(webPageMaxWidth);
    } else if (webPage != null && webPage.getMaxWidth() != webPageMaxWidth) {
      webPage.buildLayout(webPageMaxWidth);
    }
  }

  private boolean setWebPage (TdApi.WebPage page, @Nullable TdApi.LinkPreviewOptions linkPreviewOptions) {
    if (page != null) {
      String url = text != null ? Td.findUrl(text, page.url, false) : page.url;
      this.webPage = new TGWebPage(this, page, url, linkPreviewOptions);
      this.webPage.setViewProvider(currentViews);
      this.webPageOnTop.setValue(linkPreviewOptions != null && linkPreviewOptions.showAboveText, needAnimateChanges());
      return true;
    } else {
      this.webPage = null;
      this.webPageOnTop.setValue(false, false);
    }
    return false;
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (webPage != null) {
      webPage.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (webPage != null) {
      webPage.notifyInvalidateTargetsChanged();
    }
  }

  private int getWebY () {
    float webPageOnTop = this.webPageOnTop.getFloatValue();
    return getContentY() + getTextTopOffset() + (int) ((visibleText.getMetadata().getTotalHeight() + Screen.dp(6f) * visibleText.getMetadata().getTotalVisibility()) * (1f - webPageOnTop));
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
        !Td.equalsTo(oldMessageText != null ? oldMessageText.webPage : null,
                     newMessageText != null ? newMessageText.webPage : null) ||
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
      boolean webPageChanged = setWebPage(newText.webPage, newText.linkPreviewOptions);
      if (webPageChanged) {
        rebuildContent();
        invalidateContent(this);
        invalidatePreviewReceiver();
      }
      if (webPageChanged || textChanged) {
        invalidate();
      }
    }
    return true;
  }

  @Override
  public boolean needImageReceiver () {
    return webPage != null;
  }

  @Override
  public boolean needGifReceiver () {
    return webPage != null && webPage.needGif();
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return webPage != null ? webPage.getImageContentRadius(isPreview) : 0;
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    if (webPage != null) {
      webPage.requestContent(receiver, getContentX(), getWebY());
    } else {
      receiver.requestFile(null);
    }
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (webPage != null) {
      webPage.autodownloadContent(type);
    }
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (webPage != null) {
      webPage.requestPreview(receiver);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    if (webPage != null) {
      webPage.requestGif(receiver, getContentX(), getWebY());
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
    float webPageOnTop = this.webPageOnTop.getFloatValue();
    ComplexReceiver textMediaReceiver = view.getTextMediaReceiver();
    int webPageY = getWebY();
    final int topTextY = startY + getTextTopOffset();
    final int bottomTextY = webPage == null ? topTextY : webPageY + webPage.getHeight() + Screen.dp(6f) + Screen.dp(2f);
    for (ListAnimator.Entry<TextWrapper> entry : visibleText) {
      final int startXRtl = getStartXRtl(entry.item, startX, maxWidth);
      boolean needClip = entry.getVisibility() != 1f && useBubbles();
      int saveToCount = -1;
      if (needClip) {
        saveToCount = Views.save(c);
        c.clipRect(bubblePathRect);
      }
      float textAlpha = alpha * entry.getVisibility();
      if (webPageOnTop == 0f || webPage == null || receiver == null) {
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY, null, textAlpha, textMediaReceiver);
      } else if (webPageOnTop == 1f) {
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha, textMediaReceiver);
      } else {
        entry.item.beginDrawBatch(textMediaReceiver, 1);

        // top text
        int topTextHeight = (int) ((float) (entry.item.getHeight() + Screen.dp(6f)) * MathUtils.clamp(webPageOnTop));
        entry.item.draw(c, startX, startXRtl, endXPadding, topTextY - topTextHeight, null, textAlpha * MathUtils.clamp(1f - webPageOnTop), textMediaReceiver);

        // bottom text
        entry.item.draw(c, startX, startXRtl, endXPadding, bottomTextY, null, textAlpha * MathUtils.clamp(webPageOnTop), textMediaReceiver);

        entry.item.finishDrawBatch(textMediaReceiver, 1);
      }
      if (needClip) {
        Views.restore(c, saveToCount);
      }
    }
    if (webPage != null && receiver != null) {
      int webPageX = Lang.rtl() ? startX + maxWidth - webPage.getWidth() : startX;
      webPage.draw(view, c, webPageX, webPageY, preview, receiver, alpha, textMediaReceiver);
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawContent(view, c, startX, startY, maxWidth, null, null);
  }

  @Override
  protected int getContentHeight () {
    int height = Math.round(visibleText.getMetadata().getTotalHeight() + getTextTopOffset() * visibleText.getMetadata().getTotalVisibility());
    if (webPage != null) {
      if (height > 0)
        height += Screen.dp(6f);
      height += webPage.getHeight() + Screen.dp(2f);
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
    float webPageOnTop = this.webPageOnTop.getFloatValue();
    if (webPageOnTop == 0f || webPage == null) {
      if (webPage != null) {
        return webPage.getLastLineWidth();
      } else {
        return textLastLineWidth;
      }
    } else if (webPageOnTop == 1f) {
      return textLastLineWidth;
    } else {
      // Animated
      return BOTTOM_LINE_DEFINE_BY_FACTOR;
    }
  }

  @Override
  protected float getIntermediateBubbleExpandFactor () {
    int textLastLineWidth = calculateTextLastLineWidth();
    int webPageLastLineWidth = webPage != null ? webPage.getLastLineWidth() : textLastLineWidth;
    float fromExpandFactor = webPageLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    float toExpandFactor = textLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? 1f : 0f;
    return MathUtils.fromTo(fromExpandFactor, toExpandFactor, webPageOnTop.getFloatValue());
  }

  @Override
  protected int getAnimatedBottomLineWidth (int bubbleTimePartWidth) {
    int textLastLineWidth = calculateTextLastLineWidth();
    int webPageLastLineWidth = webPage != null ? webPage.getLastLineWidth() : textLastLineWidth;
    float factor = webPageOnTop.getFloatValue();
    if (factor == 1f || webPage == null) {
      return textLastLineWidth;
    } else if (factor == 0f) {
      return webPageLastLineWidth;
    }
    int fromLastLineWidth = webPageLastLineWidth == BOTTOM_LINE_EXPAND_HEIGHT ? webPage.getWidth() - bubbleTimePartWidth : webPageLastLineWidth;
    int toLastLineWidth = /*textLastLineWidth == BOTTOM_LINE_KEEP_WIDTH ? wrapper.getWidth() - bubbleTimePartWidth : */textLastLineWidth;
    return MathUtils.fromTo(fromLastLineWidth, toLastLineWidth, factor);
  }

  @Override
  protected int getContentWidth () {
    int textWidth = Math.round(visibleText.getMetadata().getTotalWidth());
    if (webPage != null) {
      return Math.max(textWidth, webPage.getWidth());
    }
    return textWidth;
  }

  private boolean forceExpand = false;

  @Override
  protected void buildReactions (boolean animated) {
    if (webPage != null || !useBubble() || visibleText.isEmpty() || !useReactionBubbles() /*|| replyData != null*/) {
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

  public TdApi.WebPage getWebPage () {
    return webPage != null ? webPage.getWebPage() : null;
  }

  public TGWebPage getParsedWebPage () {
    return webPage;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    TextWrapper wrapper = effectiveWrapper;
    return (wrapper != null && wrapper.performLongPress(view)) || (webPage != null && webPage.performLongPress(view, this)) || res;
  }

  @Override
  protected void onMessageContainerDestroyed () {
    visibleText.clear(false);
    if (webPage != null) {
      webPage.performDestroy();
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
    return (webPage != null && webPage.onTouchEvent(view, e, getContentX(), getWebY(), clickCallback()));
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
