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

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
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
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.td.Td;

public class TGMessageText extends TGMessage {
  private TdApi.FormattedText text;
  private TextWrapper wrapper;

  private TGWebPage webPage;
  private TdApi.MessageText currentMessageText, pendingMessageText;

  public TdApi.SponsoredMessage sponsoredMetadata;

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.MessageText text, @Nullable TdApi.MessageText pendingMessageText) {
    super(context, msg);
    this.currentMessageText = text;
    this.pendingMessageText = pendingMessageText;
    if (this.pendingMessageText != null) {
      setText(this.pendingMessageText.text, false);
      setWebPage(this.pendingMessageText.webPage);
    } else {
      setText(text.text, false);
      setWebPage(text.webPage);
    }
  }

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.SponsoredMessage text) {
    super(context, msg);
    this.sponsoredMetadata = text;
    this.currentMessageText = (TdApi.MessageText) text.content;
    setText(currentMessageText.text, false);
  }

  public TGMessageText (MessagesManager context, TdApi.Message msg, TdApi.FormattedText text) {
    this(context, msg, new TdApi.MessageText(text, null), null);
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
      if (messageContent != null && messageContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR && Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI)) {
        messageContent = new TdApi.MessageText(Td.textOrCaption(messageContent), null);
      }
      if (this.pendingMessageText != messageContent) {
        if (messageContent != null && messageContent.getConstructor() != TdApi.MessageText.CONSTRUCTOR)
          return MESSAGE_REPLACE_REQUIRED;
        TdApi.MessageText messageText = (TdApi.MessageText) messageContent;
        this.pendingMessageText = messageText;
        if (messageText != null) {
          setText(messageText.text, false);
          setWebPage(messageText.webPage);
        } else {
          setText(currentMessageText.text, false);
          setWebPage(currentMessageText.webPage);
        }
        rebuildContent();
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
      if (entity.type.getConstructor() == TdApi.TextEntityTypeUrl.CONSTRUCTOR) {
        url = text.text.substring(entity.offset, entity.offset + entity.length);
      } else if (entity.type.getConstructor() == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
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
    return setText(text, parseEntities, false);
  }

  private boolean setText (TdApi.FormattedText text, boolean parseEntities, boolean forceUpdate) {
    if (this.text == null || !Td.equalsTo(this.text, text) || forceUpdate) {
      this.text = text;
      TextColorSet colorSet = isErrorMessage() ? TextColorSets.Regular.NEGATIVE : getTextColorSet();
      TextWrapper.TextMediaListener textMediaListener = (wrapper, updatedText, specificTextMedia) -> {
        if (this.wrapper == wrapper) {
          invalidateTextMediaReceiver(updatedText, specificTextMedia);
        }
      };
      if (translatedText != null) {
        this.wrapper = new TextWrapper(translatedText.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, translatedText, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, translatedText.text))
          .setClickCallback(clickCallback());
      } else if (text.entities != null || !parseEntities) {
        this.wrapper = new TextWrapper(text.text, getTextStyleProvider(), colorSet)
          .setEntities(TextEntity.valueOf(tdlib, text, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, text.text))
          .setClickCallback(clickCallback());
      } else {
        this.wrapper = new TextWrapper(text.text, getTextStyleProvider(), colorSet)
          .setEntities(Text.makeEntities(text.text, Text.ENTITY_FLAGS_ALL, null, tdlib, openParameters()), textMediaListener)
          .setHighlightText(getHighlightedText(Highlight.Pool.KEY_TEXT, text.text))
          .setClickCallback(clickCallback());
      }
      this.wrapper.addTextFlags(Text.FLAG_BIG_EMOJI);
      if (useBubbles()) {
        this.wrapper.addTextFlags(Text.FLAG_ADJUST_TO_CURRENT_WIDTH);
      }
      if (Config.USE_NONSTRICT_TEXT_ALWAYS || !useBubbles()) {
        this.wrapper.addTextFlags(Text.FLAG_BOUNDS_NOT_STRICT);
      }
      this.wrapper.setViewProvider(currentViews);
      if (hasMedia()) {
        invalidateTextMediaReceiver();
      }
      return true;
    }
    return false;
  }

  @Override
  protected void onUpdateHighlightedText () {
    if (this.text != null) {
      setText(this.text, false, true);
      rebuildContent();
    }
  }

  private boolean hasMedia () {
    return wrapper.hasMedia() || (webPage != null && webPage.hasMedia());
  }

  private static final int WEB_PAGE_RECEIVERS_KEY = Integer.MAX_VALUE / 2;

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (wrapper != null) {
      wrapper.requestMedia(textMediaReceiver, 0, WEB_PAGE_RECEIVERS_KEY);
      if (webPage != null) {
        webPage.requestTextMedia(textMediaReceiver, WEB_PAGE_RECEIVERS_KEY);
      } else {
        textMediaReceiver.clearReceiversWithHigherKey(WEB_PAGE_RECEIVERS_KEY);
      }
    } else {
      textMediaReceiver.clear();
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

    wrapper.prepare(maxWidth);
    this.maxWidth = maxWidth;

    int webPageMaxWidth = getSmallestMaxContentWidth();
    if (pendingMessageText != null) {
      if (setWebPage(pendingMessageText.webPage))
        webPage.buildLayout(webPageMaxWidth);
    } else if (msg.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR && setWebPage(((TdApi.MessageText) msg.content).webPage)) {
      webPage.buildLayout(webPageMaxWidth);
    } else if (webPage != null && webPage.getMaxWidth() != webPageMaxWidth) {
      webPage.buildLayout(webPageMaxWidth);
    }
  }

  private boolean setWebPage (TdApi.WebPage page) {
    if (page != null) {
      String url = text != null ? Td.findUrl(text, page.url, false) : page.url;
      this.webPage = new TGWebPage(this, page, url);
      this.webPage.setViewProvider(currentViews);
      return true;
    } else {
      this.webPage = null;
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
    if (Td.isEmpty(text)) {
      return getContentY();
    } else {
      return getContentY() + wrapper.getHeight() + getTextTopOffset() + Screen.dp(6f);
    }
  }

  @Override
  protected boolean isSupportedMessageContent (TdApi.Message message, TdApi.MessageContent messageContent) {
    if (messageContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR)
      return Settings.instance().getNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI);
    return super.isSupportedMessageContent(message, messageContent);
  }

  @Override
  protected boolean onMessageContentChanged (TdApi.Message message, TdApi.MessageContent oldContent, TdApi.MessageContent newContent, boolean isBottomMessage) {
    if (!Td.equalsTo(Td.textOrCaption(oldContent), Td.textOrCaption(newContent)) ||
        !Td.equalsTo(oldContent.getConstructor() == TdApi.MessageText.CONSTRUCTOR ? ((TdApi.MessageText) oldContent).webPage : null,
                     newContent.getConstructor() == TdApi.MessageText.CONSTRUCTOR ? ((TdApi.MessageText) newContent).webPage : null)
    ) {
      updateMessageContent(msg, newContent, isBottomMessage);
      return true;
    }
    return false;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.WebPage oldWebPage = this.msg.content.getConstructor() == TdApi.MessageText.CONSTRUCTOR ? ((TdApi.MessageText) this.msg.content).webPage : null;
    this.msg.content = newContent;
    TdApi.MessageText newText = newContent.getConstructor() == TdApi.MessageAnimatedEmoji.CONSTRUCTOR ? new TdApi.MessageText(Td.textOrCaption(newContent), null) : (TdApi.MessageText) newContent;
    this.currentMessageText = newText;
    if (!isBeingEdited()) {
      setText(newText.text, false);
      setWebPage(newText.webPage);
      rebuildContent();
      if (!Td.equalsTo(oldWebPage, newText.webPage)) {
        invalidateContent(this);
        invalidatePreviewReceiver();
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
      webPage.requestPreview(receiver, getContentX(), getWebY());
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

  private int getStartXRtl (int startX, int maxWidth) {
    return useBubbles() ? (Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT || wrapper.getLineCount() > 1 ? getActualRightContentEdge() - getBubbleContentPadding() : startX) : startX + maxWidth;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    float alpha = getTranslationLoadingAlphaValue();
    wrapper.draw(c, startX, getStartXRtl(startX, maxWidth), Config.MOVE_BUBBLE_TIME_RTL_TO_LEFT ? 0 : getBubbleTimePartWidth(), startY + getTextTopOffset(), null, alpha, view.getTextMediaReceiver());
    if (webPage != null && receiver != null) {
      webPage.draw(view, c, Lang.rtl() ? startX + maxWidth - webPage.getWidth() : startX, getWebY(), preview, receiver, alpha, view.getTextMediaReceiver());
    }
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawContent(view, c, startX, startY, maxWidth, null, null);
  }

  @Override
  protected int getContentHeight () {
    int height = 0;
    if (!Td.isEmpty(text)) {
      height += wrapper.getHeight() + getTextTopOffset();
    }
    if (webPage != null) {
      if (height > 0)
        height += Screen.dp(8f);
      height += webPage.getHeight();
    }
    return height;
  }

  @Override
  protected int getFooterPaddingTop () {
    return Td.isEmpty(text) ? -Screen.dp(3f) : Screen.dp(7f);
  }

  @Override
  protected int getBottomLineContentWidth () {
    if (webPage != null) {
      return webPage.getLastLineWidth();
    }
    if (Lang.rtl() == wrapper.getLastLineIsRtl()) {
      return wrapper.getLastLineWidth();
    } else {
      return BOTTOM_LINE_EXPAND_HEIGHT;
    }
  }

  /*@Override
  protected boolean allowBubbleHorizontalExtend () {
    return messageReactions.getBubblesCount() < 2 || !useReactionBubbles() || replyData != null;
  }*/

  @Override
  protected int getContentWidth () {
    if (webPage != null) {
      return Math.max(wrapper.getWidth(), webPage.getWidth());
    } /* else if (messageReactions.getTotalCount() > 0 && useReactionBubbles()) {
      int textWidth = Math.max(wrapper.getWidth(), computeBubbleTimePartWidth(false));
      int reactionsWidth = messageReactions.getWidth();
      return Math.min(maxWidth, Math.max(textWidth, reactionsWidth));
    } */

    return wrapper.getWidth();
  }

  private boolean forceExpand = false;

  @Override
  protected void buildReactions (boolean animated) {
    if (webPage != null || !useBubble() || wrapper == null || !useReactionBubbles() /*|| replyData != null*/) {
      super.buildReactions(animated);
    } else {
      final float maxWidthMultiply = replyData != null ? 1f : 0.7f;
      final int textWidth = Math.max(wrapper.getWidth(), computeBubbleTimePartWidth(false));
      forceExpand = replyData == null && (textWidth < (int)(maxWidth * maxWidthMultiply)) && messageReactions.getBubblesCount() > 1 && messageReactions.getHeight() <= TGReactions.getReactionBubbleHeight();
      messageReactions.measureReactionBubbles(Math.max(textWidth, (int)(maxWidth * maxWidthMultiply)), computeBubbleTimePartWidth(true, true));
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
    return wrapper.performLongPress(view) || (webPage != null && webPage.performLongPress(view, this)) || res;
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (wrapper != null) {
      wrapper.performDestroy();
    }
    if (webPage != null) {
      webPage.performDestroy();
    }
  }

  // private int touchX, touchY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    return super.onTouchEvent(view, e) || wrapper.onTouchEvent(view, e) || (webPage != null && webPage.onTouchEvent(view, e, getContentX(), getWebY(), clickCallback()));
  }

  // Sponsor-related stuff
  // TODO: better be separated in a different object

  @Override
  public boolean isSponsored () {
    return sponsoredMetadata != null;
  }

  public boolean isBotSponsor () {
    return isSponsored() && sponsoredMetadata.link != null && sponsoredMetadata.link.getConstructor() == TdApi.InternalLinkTypeBotStart.CONSTRUCTOR;
  }

  public int getSponsorButtonName () {
    if (!isSponsored() || sponsoredMetadata.link == null) {
      return R.string.OpenChannel;
    }

    switch (sponsoredMetadata.link.getConstructor()) {
      case TdApi.InternalLinkTypeMessage.CONSTRUCTOR: {
        return R.string.OpenMessage;
      }

      case TdApi.InternalLinkTypeBotStart.CONSTRUCTOR: {
        return R.string.OpenBot;
      }

      default: {
        return R.string.OpenChannel;
      }
    }
  }

  public String getSponsoredButtonUrl () {
    if (!isSponsored() || sponsoredMetadata.link == null) {
      return tdlib.tMeUrl(tdlib.chatUsername(getSponsorChatId()));
    }

    switch (sponsoredMetadata.link.getConstructor()) {
      case TdApi.InternalLinkTypeMessage.CONSTRUCTOR: {
        TdApi.InternalLinkTypeMessage link = (TdApi.InternalLinkTypeMessage) sponsoredMetadata.link;
        return link.url;
      }

      case TdApi.InternalLinkTypeBotStart.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBotStart link = (TdApi.InternalLinkTypeBotStart) sponsoredMetadata.link;
        return tdlib.tMeStartUrl(link.botUsername, link.startParameter, false);
      }

      default: {
        return tdlib.tMeUrl(tdlib.chatUsername(getSponsorChatId()));
      }
    }
  }

  public void callSponsorButton () {
    if (!isSponsored()) {
      return;
    }

    long sponsorId = getSponsorChatId();

    if (sponsoredMetadata.link == null) {
      tdlib.ui().openChat(this, sponsorId, new TdlibUi.ChatOpenParameters().keepStack());
      return;
    }

    switch (sponsoredMetadata.link.getConstructor()) {
      case TdApi.InternalLinkTypeMessage.CONSTRUCTOR: {
        TdApi.InternalLinkTypeMessage link = (TdApi.InternalLinkTypeMessage) sponsoredMetadata.link;
        tdlib.client().send(new TdApi.GetMessageLinkInfo(link.url), messageLinkResult -> {
          if (messageLinkResult.getConstructor() == TdApi.MessageLinkInfo.CONSTRUCTOR) {
            TdApi.MessageLinkInfo messageLinkInfo = (TdApi.MessageLinkInfo) messageLinkResult;
            tdlib.ui().post(() -> {
              tdlib.ui().openMessage(this, messageLinkInfo, null);
            });
          }
        });
        break;
      }

      case TdApi.InternalLinkTypeBotStart.CONSTRUCTOR: {
        TdApi.InternalLinkTypeBotStart link = (TdApi.InternalLinkTypeBotStart) sponsoredMetadata.link;
        tdlib.ui().openChat(this, sponsorId, new TdlibUi.ChatOpenParameters().shareItem(new TGBotStart(sponsorId, link.startParameter, false)).keepStack());
        break;
      }

      default: {
        tdlib.ui().openChat(this, sponsorId, new TdlibUi.ChatOpenParameters().keepStack());
        break;
      }
    }
  }

  public long getSponsorChatId () {
    return isSponsored() ? sponsoredMetadata.sponsorChatId : 0;
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
    setText(this.text, false, true);
    rebuildAndUpdateContent();
    invalidateTextMediaReceiver();
    super.setTranslationResult(text);
  }
}
