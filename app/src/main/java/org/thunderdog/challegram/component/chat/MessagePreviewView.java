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
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.MessageListener;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
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

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class MessagePreviewView extends BaseView implements AttachDelegate, Destroyable, ChatListener, MessageListener, TdlibCache.UserDataChangeListener, TGLegacyManager.EmojiLoadListener {
  private static class MeasurableEntry<T extends ListAnimator.Measurable> implements ListAnimator.Measurable {
    protected T content;

    protected MeasurableEntry (T content) {
      this.content = content;
    }

    @Override
    public final int getSpacingStart (boolean isFirst) {
      return content.getSpacingStart(isFirst);
    }

    @Override
    public final int getSpacingEnd (boolean isLast) {
      return content.getSpacingEnd(isLast);
    }

    @Override
    public final int getWidth () {
      return content.getWidth();
    }

    @Override
    public final int getHeight () {
      return content.getHeight();
    }
  }

  private static class TextEntry extends MeasurableEntry<Text> implements Destroyable {
    public Drawable drawable;
    public ComplexReceiver receiver;

    public TextEntry (Text text, Drawable drawable, ComplexReceiver receiver) {
      super(text);
      this.drawable = drawable;
      this.receiver = receiver;
    }

    @Override
    public void performDestroy () {
      this.content.performDestroy();
      if (receiver != null) {
        receiver.performDestroy();
      }
    }
  }

  private static class MediaEntry extends MeasurableEntry<MediaPreview> implements Destroyable {
    final ComplexReceiver receiver;

    public MediaEntry (MediaPreview preview, ComplexReceiver receiver) {
      super(preview);
      this.receiver = receiver;
    }

    @Override
    public void performDestroy () {
      receiver.performDestroy();
    }
  }

  public MessagePreviewView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SettingHolder.measureHeightForType(ListItem.TYPE_MESSAGE_PREVIEW)));
    RippleSupport.setTransparentSelector(this);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  private final ViewProvider viewProvider = new SingleViewProvider(this);

  private TdApi.Message message;
  private String forcedTitle;
  private boolean ignoreAlbumRefreshers, useAvatarFallback;

  private TD.ContentPreview contentPreview;

  public void setMessage (@Nullable TdApi.Message message, @Nullable TdApi.SearchMessagesFilter filter, @Nullable String forcedTitle, boolean ignoreAlbumRefreshers) {
    this.ignoreAlbumRefreshers = ignoreAlbumRefreshers;
    if (this.message == message) {
      setForcedTitle(forcedTitle);
      return;
    }
    if (this.message != null) {
      unsubscribeFromUpdates(this.message);
    }
    this.message = message;
    this.forcedTitle = forcedTitle;
    if (message != null) {
      subscribeToUpdates(message);
      buildPreview();
      setPreviewChatId(null, message.chatId, null, new MessageId(message.chatId, message.id), filter);
    } else {
      clearPreviewChat();
      this.contentPreview = null;
      this.mediaPreview.replace(null, false);
      this.mediaPreview.measure(false);
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

  private final ReplaceAnimator<Text> titleText = new ReplaceAnimator<>(ignored -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
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
    this.contentPreview = TD.getChatListPreview(tdlib, message.chatId, message);
    if (contentPreview.hasRefresher() && !(ignoreAlbumRefreshers && contentPreview.isMediaGroup())) {
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
    return (int) (mediaPreview.getMetadata().getTotalWidth() + mediaPreview.getMetadata().getVisibility() * Screen.dp(PADDING_SIZE)) + Screen.dp(getLinePadding());
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

  private ComplexReceiver newComplexReceiver () {
    ComplexReceiver receiver = new ComplexReceiver(this);
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
          for (ListAnimator.Entry<Text> entry : titleText) {
            entry.item.changeMaxWidth(textWidth);
          }
        } else {
          buildTitleText(textWidth, false);
        }
        if (isLayout && !contentText.isEmpty()) {
          for (ListAnimator.Entry<TextEntry> entry : contentText) {
            entry.item.content.changeMaxWidth(textWidth);
            if (entry.item.receiver != null || entry.item.content.hasMedia()) {
              if (entry.item.receiver == null) {
                entry.item.receiver = newComplexReceiver();
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
    } else if (animated || this.mediaPreview.isEmpty()) {
      ComplexReceiver receiver = newComplexReceiver();
      preview.requestFiles(receiver, false);
      this.mediaPreview.replace(new MediaEntry(preview, receiver), animated);
    } else {
      MediaEntry entry = this.mediaPreview.singleton().item;
      entry.receiver.clear();
      entry.content = preview;
      preview.requestFiles(entry.receiver, false);
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

  private boolean needBoldTitle () {
    return !StringUtils.isEmpty(forcedTitle) || (message != null && Td.getMessageAuthorId(message, true) != 0);
  }

  private void buildTitleText (int availWidth, boolean animated) {
    String title = getTitle();
    Text newText;
    if (!StringUtils.isEmpty(title)) {
      newText = new Text.Builder(tdlib,
        title,
        null,
        availWidth,
        Paints.robotoStyleProvider(TEXT_SIZE),
        TextColorSets.Regular.MESSAGE_AUTHOR
      ).viewProvider(viewProvider)
       .singleLine()
       .allClickable()
       .allBold(needBoldTitle())
       .build();
    } else {
      newText = null;
    }
    this.titleText.replace(newText, animated);
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
        text, null, availWidth, Paints.robotoStyleProvider(TEXT_SIZE), TextColorSets.Regular.NORMAL
      ).viewProvider(viewProvider)
       .lineMarginProvider(iconRes != 0 ? (Text.LineMarginProvider) (lineIndex, y, defaultMaxWidth, lineHeight) -> lineIndex == 0 ? Screen.dp(2f) + Screen.dp(18f) : 0 : null)
       .singleLine()
       .ignoreNewLines()
       .ignoreContinuousNewLines()
       .addFlags(Text.FLAG_CUSTOM_LONG_PRESS)
       .build();
      if (newText.hasMedia()) {
        receiver = newComplexReceiver();
        newText.requestMedia(receiver);
      } else {
        receiver = null;
      }
    } else {
      newText = null;
      receiver = null;
    }
    this.contentText.replace(newText != null || iconRes != 0 ? new TextEntry(newText, getSparseDrawable(iconRes, R.id.theme_color_icon), receiver) : null, animated);
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
    for (ListAnimator.Entry<Text> entry : titleText) {
      entry.item.draw(c, textX, Screen.dp(7f), null, entry.getVisibility());
    }
    for (ListAnimator.Entry<TextEntry> entry : contentText) {
      if (entry.item.drawable != null) {
        Drawables.draw(c, entry.item.drawable, textX, contextTextY + (entry.item.content != null ? entry.item.content.getLineHeight(false) : Screen.dp(TEXT_SIZE)) / 2f - entry.item.drawable.getMinimumHeight() / 2f, PorterDuffPaint.get(R.id.theme_color_icon, entry.getVisibility()));
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
        updateContentText();
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

  @Override
  public void performDestroy () {
    setMessage(null, null, null, false);
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  @Override
  public void onEmojiPartLoaded () {
    invalidate();
  }
}
