/**
 * File created on 10/08/15 at 19:56
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.ThreadInfo;
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

    setShowLock(chat != null && ChatId.isSecret(chat.id));

    if (chat == null) {
      setText("Debug controller", "nobody should find this view");
      return;
    }

    if (messageThread != null) {
      setInnerMargins(Screen.dp(56f), Screen.dp(49f));
      setText(Lang.plural(messageThread.areComments() ? R.string.xComments : R.string.xReplies, messageThread.getSize()), null);
      attachChatStatus(messageThread.getChatId(), messageThread.getMessageThreadId());
    } else {
      setChatPhoto(chat, chat.photo);
      setShowVerify(tdlib.chatVerified(chat));
      setShowMute(TD.needMuteIcon(chat.notificationSettings, tdlib.scopeNotificationSettings(chat.id)));
      setText(tdlib.chatTitle(chat), !StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle : tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
      setUseRedHighlight(tdlib.isRedTeam(chat.id));
      attachChatStatus(chat.id, 0);
    }
  }

  private void setChatPhoto (TdApi.Chat chat, @Nullable TdApi.ChatPhotoInfo photo) {
    boolean empty = tdlib.isSelfChat(chat.id) || photo == null;
    setPhotoOpenDisabled(empty);
    if (empty) {
      setAvatarPlaceholder(tdlib.chatPlaceholder(chat, true, getBaseAvatarRadiusDp(), null));
    } else {
      setAvatar(photo);
    }
  }

  // Updates (new)

  public void updateChatTitle (long chatId, String title) {
    setTitle(title);
    TdApi.Chat chat = tdlib.chat(chatId);
    if (chat != null && chat.photo == null) {
      setChatPhoto(chat, null);
      updateAvatar();
    }
  }

  public void updateChatPhoto (TdApi.Chat chat, @Nullable TdApi.ChatPhotoInfo photo) {
    setChatPhoto(chat, photo);
    updateAvatar();
  }

  public void updateNotificationSettings (long chatId, TdApi.ChatNotificationSettings settings) {
    boolean isMuted = TD.needMuteIcon(settings, tdlib.scopeNotificationSettings(chatId));
    if (getShowMute() != isMuted) {
      setShowMute(isMuted);
    }
  }

  private Tdlib tdlib;

  public void updateUserStatus (TdApi.Chat chat) {
    if (StringUtils.isEmpty(forcedSubtitle)) {
      setSubtitle(tdlib.status().chatStatus(chat));
      setExpandedSubtitle(tdlib.status().chatStatusExpanded(chat));
    }
  }
}
