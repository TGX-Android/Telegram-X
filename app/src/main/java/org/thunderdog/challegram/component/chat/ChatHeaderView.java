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
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.ForumHistory;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.core.StringUtils;
import tgx.td.ChatId;

public class ChatHeaderView extends ComplexHeaderView {
  public interface Callback {
    void onChatHeaderClick ();
  }

  private Callback callback;
  private boolean isForumTopic;
  private TdApi.ForumTopicIcon topicIcon;
  private String topicLetter = "#";
  private Text customTopicEmoji;
  private final ComplexReceiver topicEmojiReceiver = new ComplexReceiver(this);
  private final Paint topicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path topicTail = new Path();

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
    this.tdlib = tdlib;

    if (chat == null) {
      setText("Debug controller", "nobody should find this view");
      return;
    }

    getAvatarReceiver().requestChat(tdlib, chat.id, AvatarReceiver.Options.FULL_SIZE);
    setShowVerify(tdlib.chatVerified(chat));
    setShowScam(tdlib.chatScam(chat));
    setShowFake(tdlib.chatFake(chat));
    setShowMute(tdlib.chatNeedsMuteIcon(chat));
    setShowLock(ChatId.isSecret(chat.id));
    if (messageThread != null) {
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

  public void setForumTopic (TdApi.Chat chat, @Nullable TdApi.MessageTopic topicId, @Nullable TdApi.ForumTopic topic, @Nullable String restriction) {
    isForumTopic = ForumHistory.isForum(topicId);
    setNoExpand(isForumTopic);
    TdApi.ForumTopicIcon icon = topic != null ? topic.info.icon : null;
    long customEmojiId = icon != null ? icon.customEmojiId : 0;
    long previousEmojiId = topicIcon != null ? topicIcon.customEmojiId : 0;
    topicIcon = icon;
    if (!isForumTopic || customEmojiId != previousEmojiId) {
      if (customTopicEmoji != null) customTopicEmoji.performDestroy();
      customTopicEmoji = null;
      topicEmojiReceiver.clear();
    }
    if (!isForumTopic) return;
    setShowMute(org.thunderdog.challegram.data.ForumPresentation.isMuted(topic != null ? topic.notificationSettings : null, tdlib.chatNeedsMuteIcon(chat)));
    String title = topic != null ? topic.info.name : Lang.getString(R.string.ForumTopicTitle);
    topicLetter = topic != null && topic.info.isGeneral || title.isEmpty() ? "#" : title.substring(0, title.offsetByCodePoints(0, 1));
    setEmojiStatus(null);
    setShowVerify(false);
    setShowScam(false);
    setShowFake(false);
    setShowLock(false);
    CharSequence subtitle = !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : restriction != null ? restriction :
      topic != null && topic.info.isClosed ? chat.title + " · " + Lang.getString(R.string.ForumTopicClosed) : chat.title;
    setText(title, subtitle);
    setExpandedSubtitle(null);
    attachChatStatus(chat.id, topicId);
    if (customEmojiId != 0 && customTopicEmoji == null) {
      TdApi.FormattedText emoji = new TdApi.FormattedText("*", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(customEmojiId))});
      customTopicEmoji = new Text.Builder(tdlib, emoji, null, Screen.dp(60), Paints.robotoStyleProvider(30), TextColorSets.WHITE, (text, media) -> {
        if (text == customTopicEmoji) { text.requestMedia(topicEmojiReceiver); invalidate(); }
      }).singleLine().build();
      customTopicEmoji.requestMedia(topicEmojiReceiver);
    }
    invalidate();
  }

  @Override
  protected void drawAvatar (Canvas c) {
    if (!isForumTopic) { super.drawAvatar(c); return; }
    AvatarReceiver avatar = getAvatarReceiver();
    float cx = avatar.centerX(), cy = avatar.centerY();
    if (customTopicEmoji != null) {
      customTopicEmoji.draw(c, (int) (cx - customTopicEmoji.getWidth() / 2f), (int) (cy - customTopicEmoji.getHeight() / 2f), null, 1f, topicEmojiReceiver);
      return;
    }
    float radius = Screen.dp(19);
    topicPaint.setColor(0xff000000 | (topicIcon != null ? topicIcon.color : 0x6fb9f0));
    c.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius - Screen.dp(3), Screen.dp(12), Screen.dp(12), topicPaint);
    topicTail.reset();
    topicTail.moveTo(cx - radius + Screen.dp(3), cy + radius - Screen.dp(9));
    topicTail.lineTo(cx - radius + Screen.dp(3), cy + radius + Screen.dp(2));
    topicTail.lineTo(cx - radius + Screen.dp(15), cy + radius - Screen.dp(4));
    topicTail.close();
    c.drawPath(topicTail, topicPaint);
    topicPaint.setColor(0xffffffff);
    topicPaint.setTextSize(Screen.dp(21));
    topicPaint.setTypeface(Fonts.getRobotoMedium());
    topicPaint.setTextAlign(Paint.Align.CENTER);
    c.drawText(topicLetter, cx, cy - Screen.dp(2) - (topicPaint.ascent() + topicPaint.descent()) / 2, topicPaint);
  }

  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); topicEmojiReceiver.attach(); }
  @Override protected void onDetachedFromWindow () { topicEmojiReceiver.detach(); super.onDetachedFromWindow(); }
  @Override public void performDestroy () {
    if (customTopicEmoji != null) customTopicEmoji.performDestroy();
    topicEmojiReceiver.performDestroy();
    super.performDestroy();
  }

  // Updates (new)

  private Tdlib tdlib;

  public void updateUserStatus (TdApi.Chat chat) {
    if (!isForumTopic && StringUtils.isEmpty(forcedSubtitle)) {
      setSubtitle(tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
    }
  }
}
