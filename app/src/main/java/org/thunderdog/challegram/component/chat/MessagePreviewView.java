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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.ContentPreview;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.receiver.RefreshRateLimiter;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibMessageViewer;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BaseView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagePreviewView extends BaseView implements AttachDelegate, Destroyable, ChatListener, MessageListener, TdlibCache.UserDataChangeListener, TGLegacyManager.EmojiLoadListener, TdlibUi.MessageProvider {
  private static class TextEntry extends ListAnimator.MeasurableEntry<Text> implements Destroyable {
    public final Drawable drawable;
    public ComplexReceiver receiver;
    public TdlibAccentColor accentColor;

    public TextEntry (Text text, Drawable drawable, ComplexReceiver receiver, TdlibAccentColor accentColor) {
      super(text);
      this.drawable = drawable;
      this.receiver = receiver;
      this.accentColor = accentColor;
    }

    public @PorterDuffColorId int getNameColorId (@PorterDuffColorId int fallbackColorId) {
      if (accentColor != null) {
        long complexColor = accentColor.getNameComplexColor();
        if (Theme.isColorId(complexColor)) {
          return Theme.extractColorValue(complexColor);
        }
      }
      return fallbackColorId;
    }

    @Override
    public void performDestroy () {
      super.performDestroy();
      if (receiver != null) {
        receiver.performDestroy();
      }
    }
  }

  private static class MediaEntry extends ListAnimator.MeasurableEntry<MediaPreview> implements Destroyable {
    final ComplexReceiver receiver;

    public MediaEntry (MediaPreview preview, ComplexReceiver receiver) {
      super(preview);
      this.receiver = receiver;
    }

    @Override
    public void performDestroy () {
      super.performDestroy();
      receiver.performDestroy();
    }
  }

  private final RefreshRateLimiter emojiUpdateLimiter = new RefreshRateLimiter(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);

  public MessagePreviewView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW)));
    RippleSupport.setTransparentSelector(this);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  private final ViewProvider viewProvider = new SingleViewProvider(this);

  private TdApi.Message message;
  private @Nullable TdApi.FormattedText messageQuote;
  private String forcedTitle;
  private boolean useAvatarFallback;
  private int displayOptions;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {
    Options.NONE,
    Options.IGNORE_ALBUM_REFRESHERS,
    Options.DISABLE_MESSAGE_PREVIEW
  }, flag = true)
  public @interface Options {
    int
      NONE = 0,
      IGNORE_ALBUM_REFRESHERS = 1,
      DISABLE_MESSAGE_PREVIEW = 1 << 1;
  }

  private ContentPreview contentPreview;

  public void setMessage (@Nullable TdApi.Message message, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String forcedTitle, @Options int options) {
    setMessage(message, null, filter, forcedTitle, options);
  }

  public void setMessage (@Nullable TdApi.Message message, @Nullable TdApi.FormattedText quote, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String forcedTitle, @Options int options) {
    this.displayOptions = options;
    if (this.message == message && Td.equalsTo(this.messageQuote, quote)) {
      setForcedTitle(forcedTitle);
      return;
    }
    if (this.message != null) {
      unsubscribeFromUpdates(this.message);
    }
    this.message = message;
    this.messageQuote = quote;
    this.forcedTitle = forcedTitle;
    if (message != null) {
      subscribeToUpdates(message);
      buildPreview();
    } else {
      this.contentPreview = null;
      this.mediaPreview.replace(null, false);
      this.mediaPreview.measure(false);
    }
    if (message != null && BitwiseUtils.hasFlag(options, Options.DISABLE_MESSAGE_PREVIEW)) {
      setPreviewChatId(null, message.chatId, null, new MessageId(message.chatId, message.id), filter);
    } else {
      clearPreviewChat();
    }
    invalidate();
  }

  public void setForcedTitle (@Nullable String forcedTitle) {
    if (!StringUtils.equalsOrBothEmpty(this.forcedTitle, forcedTitle)) {
      this.forcedTitle = forcedTitle;
      updateTitleText();
    }
  }

  public void setUseAvatarFallback (boolean useAvatarFallback) {
    this.useAvatarFallback = useAvatarFallback;
  }

  private final ReplaceAnimator<TextEntry> titleText = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final ReplaceAnimator<TextEntry> contentText = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final ReplaceAnimator<MediaEntry> mediaPreview = new ReplaceAnimator<>(ignored -> {
    buildText(true);
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private static final float LINE_PADDING = 6f;
  private static final float PADDING_SIZE = 8f;
  private static final float TEXT_SIZE = 14f;
  private static final float IMAGE_HEIGHT = 40f;

  private float customLinePadding = -1;

  private float getLinePadding () {
    return customLinePadding != -1 ? customLinePadding : LINE_PADDING;
  }

  public void setLinePadding (float customLinePadding) {
    this.customLinePadding = customLinePadding;
    buildText(true);
    buildMediaPreview(false);
    invalidate();
  }

  private void buildPreview () {
    if (messageQuote != null) {
      this.contentPreview = new ContentPreview(messageQuote, false);
    } else {
      this.contentPreview = ContentPreview.getChatListPreview(tdlib, message.chatId, message, true);
    }
    if (contentPreview.hasRefresher() && !(BitwiseUtils.hasFlag(displayOptions, Options.IGNORE_ALBUM_REFRESHERS) && contentPreview.isMediaGroup())) {
      contentPreview.refreshContent((chatId, messageId, newPreview, oldPreview) -> {
        tdlib.runOnUiThread(() -> {
          if (this.contentPreview == oldPreview) {
            this.contentPreview = newPreview;
            updateContentText();
            buildMediaPreview(true);
          }
        });
      });
    }
    buildText(false);
    buildMediaPreview(false);
    invalidate();
  }

  private int lastTextWidth;

  private int getTextHorizontalOffset () {
    return (int) (mediaPreview.getMetadata().getTotalWidth() + mediaPreview.getMetadata().getTotalVisibility() * Screen.dp(PADDING_SIZE)) + Screen.dp(getLinePadding());
  }

  private int contentInset;

  public void setContentInset (int contentInset) {
    if (this.contentInset != contentInset) {
      this.contentInset = contentInset;
      buildText(true);
    }
  }

  private int calculateTextWidth () {
    return Math.max(0, getMeasuredWidth() - Screen.dp(PADDING_SIZE) * 2 - getTextHorizontalOffset() - Screen.dp(getLinePadding()) - contentInset);
  }

  private ComplexReceiver newComplexReceiver (boolean forTextMedia) {
    ComplexReceiver receiver = new ComplexReceiver(forTextMedia ? null : this);
    if (forTextMedia) {
      receiver.setUpdateListener(emojiUpdateLimiter);
    }
    if (isAttached) {
      receiver.attach();
    } else {
      receiver.detach();
    }
    return receiver;
  }

  private void buildText (boolean isLayout) {
    int textWidth = calculateTextWidth();
    if (this.lastTextWidth != textWidth || !isLayout) {
      this.lastTextWidth = textWidth;
      if (textWidth > 0) {
        if (isLayout && !titleText.isEmpty()) {
          for (ListAnimator.Entry<TextEntry> entry : titleText) {
            entry.item.content.changeMaxWidth(textWidth);
          }
        } else {
          buildTitleText(textWidth, false);
        }
        if (isLayout && !contentText.isEmpty()) {
          for (ListAnimator.Entry<TextEntry> entry : contentText) {
            entry.item.content.changeMaxWidth(textWidth);
            if (entry.item.receiver != null || entry.item.content.hasMedia()) {
              if (entry.item.receiver == null) {
                entry.item.receiver = newComplexReceiver(true);
              }
              entry.item.content.requestMedia(entry.item.receiver);
            }
          }
        } else {
          buildContentText(textWidth, false);
        }
      } else {
        this.titleText.replace(null, false);
        this.contentText.replace(null, false);
      }
      invalidate();
    }
  }

  private void buildMediaPreview (boolean animated) {
    MediaPreview preview;

    if (message != null) {
      preview = MediaPreview.valueOf(tdlib, message, contentPreview, Screen.dp(IMAGE_HEIGHT), Screen.dp(3f));
      if (preview == null && useAvatarFallback) {
        TdApi.Chat chat = tdlib.chat(message.chatId);
        if (chat != null && chat.photo != null) {
          preview = MediaPreview.valueOf(tdlib, chat.photo, Screen.dp(IMAGE_HEIGHT), Screen.dp(3f));
        }
      }
    } else {
      preview = null;
    }

    if (preview == null) {
      this.mediaPreview.replace(null, animated);
    } else {
      ComplexReceiver receiver = newComplexReceiver(false);
      preview.requestFiles(receiver, false);
      this.mediaPreview.replace(new MediaEntry(preview, receiver), animated);
    }
  }

  private void updateTitleText () {
    if (lastTextWidth > 0) {
      buildTitleText(lastTextWidth, true);
      invalidate();
    }
  }

  @Nullable
  private String getTitle () {
    return !StringUtils.isEmpty(forcedTitle) ? forcedTitle : message != null ? tdlib.senderName(message, true, false) : null;
  }

  @Nullable
  private TdlibAccentColor getAccentColor () {
    if (StringUtils.isEmpty(forcedTitle) && message != null) {
      return tdlib.messageAccentColor(message);
    } else {
      return null;
    }
  }

  private boolean needBoldTitle () {
    return !StringUtils.isEmpty(forcedTitle) || (message != null && Td.getMessageAuthorId(message, true) != 0);
  }

  private static final float CORNER_PADDING = 3f;

  private void buildTitleText (int availWidth, boolean animated) {
    String title = getTitle();
    TdlibAccentColor accentColor = getAccentColor();

    Drawable drawable;
    if (messageQuote != null) {
      @ColorId int colorId = ColorId.messageAuthor;
      if (accentColor != null) {
        long complexColor = accentColor.getNameComplexColor();
        if (Theme.isColorId(complexColor)) {
          colorId = Theme.extractColorValue(complexColor);
        }
      }
      drawable = getSparseDrawable(R.drawable.baseline_format_quote_close_16, colorId);
      availWidth -= drawable.getMinimumWidth() + Screen.dp(CORNER_PADDING);
    } else {
      drawable = null;
    }

    Text newText;
    if (!StringUtils.isEmpty(title)) {
      newText = new Text.Builder(tdlib,
        title,
        null,
        availWidth,
        Paints.robotoStyleProvider(TEXT_SIZE),
        accentColor != null ?
          accentColor::getNameColor :
          TextColorSets.Regular.MESSAGE_AUTHOR,
        null
      ).viewProvider(viewProvider)
       .singleLine()
       .allClickable()
       .allBold(needBoldTitle())
       .build();
    } else {
      newText = null;
    }
    this.titleText.replace(newText != null || drawable != null ? new TextEntry(newText, drawable, null, accentColor) : null, animated);
  }

  private void updateContentText () {
    if (lastTextWidth > 0) {
      buildContentText(lastTextWidth, true);
    }
  }

  private TdApi.FormattedText getContentText () {
    return contentPreview != null ? contentPreview.buildFormattedText(true) : null;
  }

  private void buildContentText (int availWidth, boolean animated) {
    TdApi.FormattedText text = getContentText();
    int iconRes = contentPreview != null && contentPreview.emoji != null ? contentPreview.emoji.iconRepresentation : 0;
    Text newText;
    ComplexReceiver receiver;
    if (!Td.isEmpty(text)) {
      newText = new Text.Builder(tdlib,
        text, null, availWidth, Paints.robotoStyleProvider(TEXT_SIZE), TextColorSets.Regular.NORMAL, (text1, specificMedia) -> {
          for (ListAnimator.Entry<TextEntry> entry : contentText) {
            if (entry.item.content == text1) {
              if (!text1.invalidateMediaContent(entry.item.receiver, specificMedia)) {
                text1.requestMedia(entry.item.receiver);
              }
            }
          }
        }
      ).viewProvider(viewProvider)
       .lineMarginProvider(iconRes != 0 ? (lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? Screen.dp(2f) + Screen.dp(18f) : 0 : null)
       .singleLine()
       .ignoreNewLines()
       .ignoreContinuousNewLines()
       .addFlags(Text.FLAG_CUSTOM_LONG_PRESS)
       .build();
      if (newText.hasMedia()) {
        receiver = newComplexReceiver(true);
        newText.requestMedia(receiver);
      } else {
        receiver = null;
      }
    } else {
      newText = null;
      receiver = null;
    }
    this.contentText.replace(newText != null || iconRes != 0 ? new TextEntry(newText, getSparseDrawable(iconRes, ColorId.icon), receiver, null) : null, animated);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildText(true);
  }

  // Drawing

  @Override
  protected void onDraw (Canvas c) {
    if (this.message == null)
      return;
    int textX = Screen.dp(getLinePadding()) + Screen.dp(PADDING_SIZE) + getTextHorizontalOffset();
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      entry.item.content.draw(this, c, entry.item.receiver, textX - entry.item.content.getWidth() - Screen.dp(PADDING_SIZE), (SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW) - Screen.dp(IMAGE_HEIGHT)) / 2f, entry.getVisibility());
    }
    int contextTextY = Screen.dp(7f) + Screen.dp(TEXT_SIZE) + Screen.dp(5f);
    for (ListAnimator.Entry<TextEntry> entry : titleText) {
      int titleY = Screen.dp(7f);
      if (entry.item.content != null) {
        entry.item.content.draw(c, textX, titleY, null, entry.getVisibility());
      }
      if (entry.item.drawable != null) {
        float textCenterY = titleY + (entry.item.content != null ? entry.item.content.getLineHeight(false) : Screen.dp(TEXT_SIZE)) / 2f;
        float iconX = textX + Math.max(
          (entry.item.content != null ? entry.item.content.getWidth() + Screen.dp(CORNER_PADDING) : 0),
          contentText.getMetadata().getTotalWidth() - entry.item.drawable.getMinimumWidth()
        );
        Drawables.draw(c, entry.item.drawable, iconX, textCenterY - entry.item.drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(entry.item.getNameColorId(ColorId.messageAuthor), entry.getVisibility()));
      }
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.drawable != null) {
        Drawables.draw(c, entry.item.drawable, textX, contextTextY + (entry.item.content != null ? entry.item.content.getLineHeight(false) : Screen.dp(TEXT_SIZE)) / 2f - entry.item.drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(ColorId.icon, entry.getVisibility()));
      }
      if (entry.item.content != null) {
        entry.item.content.draw(c, textX, contextTextY, null, entry.getVisibility(), entry.item.receiver);
      }
    }
  }

  // Touch events

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.content != null && entry.item.content.onTouchEvent(this, e)) {
        return true;
      }
    }
    return super.onTouchEvent(e);
  }

  // Listeners

  public void subscribeToUpdates (TdApi.Message message) {
    switch (message.senderId.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        tdlib.listeners().subscribeToChatUpdates(((TdApi.MessageSenderChat) message.senderId).chatId, this);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        tdlib.cache().addUserDataListener(((TdApi.MessageSenderUser) message.senderId).userId, this);
        break;
      }
    }
    tdlib.listeners().subscribeToMessageUpdates(message.chatId, this);
  }

  public void unsubscribeFromUpdates (TdApi.Message message) {
    switch (message.senderId.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        tdlib.listeners().unsubscribeFromChatUpdates(((TdApi.MessageSenderChat) message.senderId).chatId, this);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        tdlib.cache().removeUserDataListener(((TdApi.MessageSenderUser) message.senderId).userId, this);
        break;
      }
    }
    tdlib.listeners().unsubscribeFromMessageUpdates(message.chatId, this);
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    tdlib.runOnUiThread(() -> {
      if (this.message != null && this.message.senderId.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR && ((TdApi.MessageSenderChat) this.message.senderId).chatId == chatId) {
        updateTitleText();
      }
    });
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    tdlib.runOnUiThread(() -> {
      if (this.message != null && this.message.senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR && ((TdApi.MessageSenderUser) this.message.senderId).userId == user.id) {
        updateTitleText();
      }
    });
  }

  @Override
  public void onMessageContentChanged (long chatId, long messageId, TdApi.MessageContent newContent) {
    tdlib.runOnUiThread(() -> {
      if (this.message != null && this.message.chatId == chatId && this.message.id == messageId) {
        this.message.content = newContent;
        buildPreview();
      }
    });
  }

  @Override
  public void onMessagesDeleted (long chatId, long[] messageIds) {
    tdlib.runOnUiThread(() -> {
      if (this.message != null && this.message.chatId == chatId) {
        if (this.contentPreview != null) {
          Tdlib.Album album = this.contentPreview.getAlbum();
          if (album != null) {
            for (TdApi.Message albumMessage : album.messages) {
              if (ArrayUtils.contains(messageIds, albumMessage.id)) {
                buildPreview(); // one of album's message was deleted
                break;
              }
            }
          }
        }
      }
    });
  }

  // Interface

  private boolean isAttached;

  @Override
  public void attach () {
    this.isAttached = true;
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      entry.item.receiver.attach();
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.receiver != null) {
        entry.item.receiver.attach();
      }
    }
  }

  @Override
  public void detach () {
    this.isAttached = false;
    for (ListAnimator.Entry<MediaEntry> entry : mediaPreview) {
      entry.item.receiver.attach();
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.receiver != null) {
        entry.item.receiver.detach();
      }
    }
  }

  public void clear () {
    setMessage(null, null, null, Options.NONE);
  }

  @Override
  public void performDestroy () {
    clear();
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  @Override
  public boolean isMediaGroup () {
    Tdlib.Album album = contentPreview != null ? contentPreview.getAlbum() : null;
    return album != null;
  }

  @Override
  public List<TdApi.Message> getVisibleMediaGroup () {
    Tdlib.Album album = contentPreview != null ? contentPreview.getAlbum() : null;
    return album != null ? album.messages : null;
  }

  @Override
  public TdApi.Message getVisibleMessage () {
    return message;
  }

  @Override
  public int getVisibleMessageFlags () {
    return TdlibMessageViewer.Flags.NO_SENSITIVE_SCREENSHOT_NOTIFICATION;
  }
}
