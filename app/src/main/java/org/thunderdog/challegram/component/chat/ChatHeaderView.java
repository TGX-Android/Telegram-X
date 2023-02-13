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
 *
 * File created on 10/08/2015 at 19:56
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.ThreadInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;

public class ChatHeaderView extends ComplexHeaderView {
  public interface Callback {
    void onChatHeaderClick ();
  }

  private Callback callback;

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
      setText(messageThread.chatHeaderTitle(), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : messageThread.chatHeaderSubtitle());
      setExpandedSubtitle(null);
      setUseRedHighlight(false);
      attachChatStatus(messageThread.getChatId(), messageThread.getMessageThreadId());
    } else {
      setText(tdlib.chatTitle(chat), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
      setUseRedHighlight(tdlib.isRedTeam(chat.id));
      attachChatStatus(chat.id, 0);
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
}
