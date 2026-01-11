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
 * File created on 10/08/2015 at 19:56
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccentColor;
import org.thunderdog.challegram.telegram.TdlibEmojiManager;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;
import tgx.td.Td;

public class ChatHeaderView extends ComplexHeaderView implements TdlibEmojiManager.Watcher {
  public interface Callback {
    void onChatHeaderClick ();
  }

  private Callback callback;

  // Topic icon support
  private long topicCustomEmojiId;
  private TdlibEmojiManager.Entry topicCustomEmoji;
  private ComplexReceiver topicIconReceiver;
  private ImageFile topicImageFile;
  private GifFile topicGifFile;

  public ChatHeaderView (Context context, Tdlib tdlib, @Nullable ViewController<?> parent) {
    super(context, tdlib, parent);
    setPhotoOpenDisabled(true);
    setOnClickListener(v -> {
      if (callback != null) {
        callback.onChatHeaderClick();
      }
    });
    setUseDefaultClickListener(true);
    setBackgroundResource(ThemeDeprecated.headerSelector());
    setInnerMargins(Screen.dp(56f), Screen.dp(49f));
    topicIconReceiver = new ComplexReceiver(this);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    topicIconReceiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    topicIconReceiver.detach();
  }

  @Override
  public void onCustomEmojiLoaded (TdlibEmojiManager context, TdlibEmojiManager.Entry entry) {
    if (entry.customEmojiId == topicCustomEmojiId) {
      topicCustomEmoji = entry;
      loadTopicEmojiFiles();
      invalidate();
    }
  }

  private void loadTopicEmojiFiles () {
    if (topicCustomEmoji == null || topicCustomEmoji.isNotFound()) {
      topicImageFile = null;
      topicGifFile = null;
      topicIconReceiver.clear();
      return;
    }

    TdApi.Sticker sticker = topicCustomEmoji.value;
    if (sticker == null) {
      topicImageFile = null;
      topicGifFile = null;
      topicIconReceiver.clear();
      return;
    }

    int size = Screen.dp(40f); // Avatar size
    // Check sticker format to determine file type
    // WebP stickers use ImageFile, TGS and WEBM use GifFile
    switch (sticker.format.getConstructor()) {
      case TdApi.StickerFormatWebp.CONSTRUCTOR: {
        topicImageFile = new ImageFile(tdlib, sticker.sticker);
        topicImageFile.setSize(size);
        topicImageFile.setScaleType(ImageFile.CENTER_CROP);
        topicGifFile = null;
        topicIconReceiver.getImageReceiver(0).requestFile(topicImageFile);
        break;
      }
      case TdApi.StickerFormatTgs.CONSTRUCTOR:
      case TdApi.StickerFormatWebm.CONSTRUCTOR: {
        topicGifFile = new GifFile(tdlib, sticker);
        topicGifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
        topicGifFile.setRequestedSize(size);
        topicImageFile = null;
        topicIconReceiver.getGifReceiver(0).requestFile(topicGifFile);
        break;
      }
    }
  }

  private void clearTopicEmoji () {
    if (topicCustomEmojiId != 0 && topicCustomEmoji == null && tdlib != null) {
      tdlib.emoji().forgetWatcher(topicCustomEmojiId, this);
    }
    topicCustomEmojiId = 0;
    topicCustomEmoji = null;
    topicImageFile = null;
    topicGifFile = null;
    if (topicIconReceiver != null) {
      topicIconReceiver.clear();
    }
  }

  private CharSequence forcedSubtitle;

  public void setForcedSubtitle (CharSequence subtitle) {
    if (!StringUtils.equalsOrBothEmpty(forcedSubtitle, subtitle)) {
      this.forcedSubtitle = subtitle;
      setNoStatus(!StringUtils.isEmpty(subtitle));
      if (hasSubtitle()) {
        setSubtitle(subtitle);
      }
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, HeaderView.getSize(scaleFactor != 0f, true));
  }

  @Override
  public void setScaleFactor (float scaleFactor, float fromFactor, float toScaleFactor, boolean byScroll) {
    if (this.scaleFactor != scaleFactor) {
      boolean layout = this.scaleFactor == 0f || scaleFactor == 0f;
      super.setScaleFactor(scaleFactor, fromFactor, toScaleFactor, byScroll);
      if (layout) {
        setEnabled(scaleFactor == 0f);
        requestLayout();
      }
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return callback != null && super.onTouchEvent(e);
  }

  public void setChat (Tdlib tdlib, TdApi.Chat chat, @Nullable ThreadInfo messageThread) {
    setChat(tdlib, chat, messageThread, null);
  }

  public void setChat (Tdlib tdlib, TdApi.Chat chat, @Nullable ThreadInfo messageThread, @Nullable TdApi.ForumTopic forumTopic) {
    this.tdlib = tdlib;

    // Clear previous topic emoji
    clearTopicEmoji();

    if (chat == null) {
      setText("Debug controller", "nobody should find this view");
      return;
    }

    // For forum topics, show topic icon instead of chat avatar
    if (forumTopic != null) {
      TdApi.ForumTopicIcon icon = forumTopic.info.icon;
      int topicColor = icon != null ? icon.color : 0x6FB9F0;
      // Ensure color has alpha
      if (topicColor < 0x01000000) {
        topicColor = 0xFF000000 | topicColor;
      }

      // Check if topic has custom emoji icon
      if (icon != null && icon.customEmojiId != 0) {
        // Request custom emoji
        topicCustomEmojiId = icon.customEmojiId;
        topicCustomEmoji = tdlib.emoji().findOrPostponeRequest(topicCustomEmojiId, this);
        if (topicCustomEmoji != null) {
          loadTopicEmojiFiles();
        }
        // Use placeholder while loading or as fallback
        String letter = forumTopic.info.forumTopicId == 1 ? "#" :
          (!StringUtils.isEmpty(forumTopic.info.name) ? forumTopic.info.name.substring(0, 1).toUpperCase() : "?");
        TdlibAccentColor accentColor = new TdlibAccentColor(topicColor);
        AvatarPlaceholder.Metadata metadata = new AvatarPlaceholder.Metadata(accentColor, new Letters(letter));
        getAvatarReceiver().requestPlaceholder(tdlib, metadata, AvatarReceiver.Options.FULL_SIZE);
      } else {
        // No custom emoji - show colored placeholder with letter/hash
        String letter = forumTopic.info.forumTopicId == 1 ? "#" :
          (!StringUtils.isEmpty(forumTopic.info.name) ? forumTopic.info.name.substring(0, 1).toUpperCase() : "?");
        TdlibAccentColor accentColor = new TdlibAccentColor(topicColor);
        AvatarPlaceholder.Metadata metadata = new AvatarPlaceholder.Metadata(accentColor, new Letters(letter));
        getAvatarReceiver().requestPlaceholder(tdlib, metadata, AvatarReceiver.Options.FULL_SIZE);
      }
    } else {
      getAvatarReceiver().requestChat(tdlib, chat.id, AvatarReceiver.Options.FULL_SIZE);
    }

    setShowVerify(tdlib.chatVerified(chat));
    setShowScam(tdlib.chatScam(chat));
    setShowFake(tdlib.chatFake(chat));
    setShowMute(tdlib.chatNeedsMuteIcon(chat));
    setShowLock(ChatId.isSecret(chat.id));
    if (forumTopic != null) {
      // Forum topic: show topic name as title, chat name as subtitle
      setEmojiStatus(null);
      setText(forumTopic.info.name, !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : tdlib.chatTitle(chat));
      setExpandedSubtitle(null);
      setUseRedHighlight(false);
      attachChatStatus(chat.id, new TdApi.MessageTopicForum(forumTopic.info.forumTopicId));
    } else if (messageThread != null) {
      setEmojiStatus(null);
      setText(messageThread.chatHeaderTitle(), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : messageThread.chatHeaderSubtitle());
      setExpandedSubtitle(null);
      setUseRedHighlight(false);
      attachChatStatus(messageThread.getChatId(), messageThread.getMessageTopicId());
    } else {
      setEmojiStatus(tdlib.isSelfChat(chat) ? null : tdlib.chatUser(chat));
      setText(tdlib.chatTitle(chat), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
      setUseRedHighlight(tdlib.isRedTeam(chat.id));
      attachChatStatus(chat.id, null);
    }
  }

  // Updates (new)

  private Tdlib tdlib;

  public void updateUserStatus (TdApi.Chat chat) {
    if (StringUtils.isEmpty(forcedSubtitle)) {
      setSubtitle(tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    super.onDraw(c);

    // Draw topic custom emoji icon on top of avatar if loaded
    if (topicCustomEmojiId != 0 && topicCustomEmoji != null && !topicCustomEmoji.isNotFound()) {
      AvatarReceiver avatarReceiver = getAvatarReceiver();
      int left = avatarReceiver.getLeft();
      int top = avatarReceiver.getTop();
      int right = avatarReceiver.getRight();
      int bottom = avatarReceiver.getBottom();
      float centerX = (left + right) / 2f;
      float centerY = (top + bottom) / 2f;
      float radius = (right - left) / 2f;

      // Cover the placeholder with header background before drawing emoji
      c.drawCircle(centerX, centerY, radius, Paints.fillingPaint(Theme.headerColor()));

      if (topicImageFile != null) {
        topicIconReceiver.getImageReceiver(0).setBounds(left, top, right, bottom);
        topicIconReceiver.getImageReceiver(0).draw(c);
      } else if (topicGifFile != null) {
        topicIconReceiver.getGifReceiver(0).setBounds(left, top, right, bottom);
        topicIconReceiver.getGifReceiver(0).draw(c);
      }
    }
  }
}
