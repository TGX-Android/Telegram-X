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
 * File created on 17/07/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerEntry;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class VerticalChatView extends BaseView implements Destroyable, ChatListener, TdlibCache.UserDataChangeListener, NotificationSettingsListener, AttachDelegate, SimplestCheckBoxHelper.Listener, TextColorSet, TooltipOverlayView.LocationProvider {
  private static final int SENDER_RADIUS = 10;

  private final AvatarReceiver avatarReceiver;
  private final Counter counter;

  private SimplestCheckBoxHelper checkBoxHelper;

  public VerticalChatView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);

    Views.setClickable(this);
    RippleSupport.setTransparentSelector(this);

    avatarReceiver = new AvatarReceiver(this);
    counter = new Counter.Builder().callback(this).outlineColor(ColorId.filling).build();
    identityAvatarReceiver = new AvatarReceiver(this);
  }

  public void setIsChecked (boolean isChecked, boolean animated) {
    if (isChecked != (checkBoxHelper != null && checkBoxHelper.isChecked())) {
      if (checkBoxHelper == null) {
        checkBoxHelper = new SimplestCheckBoxHelper(this, avatarReceiver);
      }
      checkBoxHelper.setIsChecked(isChecked, animated);
    }
  }

  @Override
  public void onCheckFactorChanged (float factor) {
    updateTextColor();
  }

  private @Nullable ViewController<?> themeProvider;

  public void setThemeProvider (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    buildTrimmedTitle();
  }

  @Override
  public void attach () {
    avatarReceiver.attach();
    identityAvatarReceiver.attach();
  }

  @Override
  public void detach () {
    avatarReceiver.detach();
    identityAvatarReceiver.detach();
  }

  @Override
  public void performDestroy () {
    avatarReceiver.destroy();
    identityAvatarReceiver.destroy();
    setChat(null);
  }

  // Data

  private TGFoundChat chat;

  public long getChatId () {
    return chat != null ? chat.getId() : 0;
  }

  public long getUserId () {
    return chat != null && !chat.isSelfChat() ? chat.getUserId() : 0;
  }

  private ThemeListenerEntry themeEntry;

  private void updateTextColor () {
    /*final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    final int colorId = checkFactor == 1f ? ColorId.textLink : chat.isSecret() ? ColorId.textGreen : ColorId.textAccent;
    titleView.setTextColor(checkFactor == 0f ? TGTheme.getColor(colorId) : ColorChanger.inlineChange(TGTheme.getColor(colorId), TGTheme.getColor(ColorId.textLink), checkFactor));
    if (themeProvider != null) {
      if (themeEntry != null) {
        themeEntry.setTargetColorId(colorId);
      } else {
        themeEntry = themeProvider.addThemeTextColorListener(titleView, colorId);
      }
    }*/
  }

  public void setChat (TGFoundChat chat) {
    long oldChatId = (this.chat != null ? this.chat.getChatOrUserId() : 0);
    long newChatId = (chat != null ? chat.getChatOrUserId() : 0);

    if (oldChatId != newChatId) {
      if (oldChatId != 0 && !this.chat.noSubscription()) {
        tdlib.listeners().unsubscribeFromChatUpdates(oldChatId, this);
        tdlib.listeners().unsubscribeFromSettingsUpdates(oldChatId, this);
      }
      this.chat = chat;
      setPreviewChatId(chat != null ? chat.getList() : null, newChatId, null);
      if (chat != null) {
        updateTextColor();
        updateChat(false);
        if (!chat.noSubscription()) {
          tdlib.listeners().subscribeToChatUpdates(newChatId, this);
          tdlib.listeners().subscribeToSettingsUpdates(newChatId, this);
        }
      } else {
        avatarReceiver.clear();
        setTitle("");
      }
    }
  }

  private String title;
  private Text trimmedTitle;

  private void setTitle (String title) {
    //titleView.setText(title);
    if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
      this.title = title;
      buildTrimmedTitle();
    }
  }

  private void buildTrimmedTitle () {
    int availWidth = getMeasuredWidth() - Screen.dp(6f);
    boolean isSecret = chat != null && chat.isSecret();
    if (isSecret) {
      availWidth -= Screen.dp(SECURE_OFFSET);
    }
    if (availWidth > 0 && !StringUtils.isEmpty(title)) {
      trimmedTitle = new Text.Builder(title, availWidth, Paints.robotoStyleProvider(13), this).singleLine().build();
    } else {
      trimmedTitle = null;
    }
  }

  private void updateChat (boolean isUpdate) {
    chat.updateChat();
    if (chat.getChatId() != 0) {
      avatarReceiver.requestChat(tdlib, chat.getChatId(), AvatarReceiver.Options.SHOW_ONLINE);
    } else {
      TdApi.MessageSender senderId = chat.getSenderId();
      if (senderId != null) {
        avatarReceiver.requestMessageSender(tdlib, senderId, AvatarReceiver.Options.SHOW_ONLINE);
      } else {
        avatarReceiver.clear();
      }
    }
    setTitle(chat.getSingleLineTitle().toString());
    counter.setCount(chat.getUnreadCount(), !chat.notificationsEnabled(), isUpdate && isParentVisible());
    setMessageSender(chat.getMessageSenderId(), chat.isAnonymousAdmin());
  }

  // Unread badge

  private void updateChat (final long chatId) {
    if (getChatId() == chatId) {
      tdlib.ui().post(() -> {
        if (getChatId() == chatId && chat != null) {
          updateChat(true);
        }
      });
    }
  }

  @Override
  public void onChatTitleChanged (final long chatId, String title) {
    updateChat(chatId);
  }

  @Override
  public void onChatPhotoChanged (final long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    updateChat(chatId);
  }

  @Override
  public void onChatReadInbox(long chatId, long lastReadInboxMessageId, int unreadCount, boolean availabilityChanged) {
    updateChat(chatId);
  }

  @Override
  public void onChatDefaultMessageSenderIdChanged (long chatId, TdApi.MessageSender senderId) {
    updateChat(chatId);
  }

  @Override
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    updateChat(chatId);
  }

  // Animator target

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    if (Td.matchesScope(tdlib.chatType(getChatId()), scope)) {
      tdlib.ui().post(() -> {
        if (chat != null && Td.matchesScope(tdlib.chatType(getChatId()), scope)) {
          chat.updateMuted();
          counter.setMuted(!chat.notificationsEnabled(), true);
        }
      });
    }
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    tdlib.ui().post(() -> {
      if (chatId == getChatId() && chat != null) {
        chat.updateMuted();
        counter.setMuted(!chat.notificationsEnabled(), true);
      }
    });
  }

  // Online

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (getUserId() == user.id && chat != null) {
        updateChat(true);
      }
    });
  }

  // Drawing


  @Override
  public int defaultTextColor () {
    boolean isSecret = chat != null && chat.isSecret();
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    return isSecret ? Theme.getColor(ColorId.textSecure) : checkFactor == 0f ? Theme.textAccentColor() : ColorUtils.fromToArgb(Theme.textAccentColor(), Theme.getColor(ColorId.textSearchQueryHighlight), checkFactor);
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    avatarReceiver.toRect(outRect);
  }

  @Override
  protected void onDraw (Canvas c) {
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    final int radius = Screen.dp(25f);
    final int centerX = getMeasuredWidth() / 2;
    final int centerY = getMeasuredHeight() / 2 - Screen.dp(11f);
    avatarReceiver.forceAllowOnline(checkBoxHelper == null || !checkBoxHelper.isChecked(), 1f - checkFactor);

    avatarReceiver.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);

    float displayRadius = avatarReceiver.getDisplayRadius();


    double topRightRadians = Math.toRadians(Lang.rtl() ? 225f : 135f);
    float checkCenterX, checkCenterY;
    if (Lang.rtl()) {
      checkCenterX = avatarReceiver.getLeft() + displayRadius;
    } else {
      checkCenterX = avatarReceiver.getRight() - displayRadius;
    }
    checkCenterY = avatarReceiver.getTop() + displayRadius;
    float x = checkCenterX + (float) ((double) displayRadius * Math.sin(topRightRadians));
    float y = checkCenterY + (float) ((double) displayRadius * Math.cos(topRightRadians));
    counter.draw(c, x, y, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, (1f - checkFactor));

    if (checkFactor > 0f) {
      DrawAlgorithms.drawSimplestCheckBox(c, avatarReceiver, checkFactor);
    }

    if (trimmedTitle != null) {
      TextPaint paint = getTextPaint();
      final int sourceColor = paint.getColor();
      boolean isSecret = chat != null && chat.isSecret();
      int startX;
      int startY = getMeasuredHeight() / 2 + Screen.dp(22f);
      if (isSecret) {
        Drawable d = Icons.getSecureSmallDrawable();
        startX = getMeasuredWidth() / 2 - (trimmedTitle.getWidth() + d.getMinimumWidth()) / 2;
        Drawables.draw(c, d, startX, startY + trimmedTitle.getHeight() / 2 - d.getMinimumHeight() / 2, Paints.getGreenPorterDuffPaint());
        startX += d.getMinimumWidth();
      } else {
        startX = getMeasuredWidth() / 2 - trimmedTitle.getWidth() / 2;
      }
      trimmedTitle.draw(c, startX, startY);
      paint.setColor(sourceColor);
    }

    if (hasMessageSender || drawAnonymousSender) {
      final double topLeftRadians = Math.toRadians(Lang.rtl() ? 135f : 225f);
      float identityCenterX, identityCenterY;
      if (Lang.rtl()) {
        identityCenterX = avatarReceiver.getRight() - displayRadius;
      } else {
        identityCenterX = avatarReceiver.getLeft() + displayRadius;
      }
      identityCenterY = avatarReceiver.getTop() + displayRadius;

      float senderCenterX = identityCenterX + (float) ((double) displayRadius * Math.sin(topLeftRadians));
      float senderCenterY = identityCenterY + (float) ((double) displayRadius * Math.cos(topLeftRadians));

      final int senderRadius = Screen.dp(SENDER_RADIUS);
      identityAvatarReceiver.setBounds((int) senderCenterX - senderRadius, (int) senderCenterY - senderRadius, (int)senderCenterX + senderRadius, (int) senderCenterY + senderRadius);
      identityAvatarReceiver.drawPlaceholderRounded(c, identityAvatarReceiver.getDisplayRadius(), Theme.fillingColor(), Screen.dp(1.5f));

      if (drawAnonymousSender) {
        identityAvatarReceiver.drawPlaceholderRounded(c, identityAvatarReceiver.getDisplayRadius(), Theme.getColor(ColorId.iconLight));
        Drawables.draw(c, Drawables.get(R.drawable.infanf_baseline_incognito_14), identityAvatarReceiver.centerX() - Screen.dp(7), identityAvatarReceiver.centerY() - Screen.dp(7), PorterDuffPaint.get(ColorId.badgeMutedText));
      } else {
        identityAvatarReceiver.draw(c);
      }
    }
  }

  private static final float SECURE_OFFSET = 12f;

  private static TextPaint getTextPaint () {
    return Paints.getSmallTitlePaint();
  }

  private final AvatarReceiver identityAvatarReceiver;
  private boolean hasMessageSender = false;
  private boolean drawAnonymousSender = false;

  private void setMessageSender (TdApi.MessageSender sender, boolean forceAnonymousSender) {
    identityAvatarReceiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.NONE);
    hasMessageSender = sender != null && (!tdlib.isSelfSender(sender) || Td.isAnonymous(tdlib.chatStatus(chat.getChatId())));
    drawAnonymousSender = (sender == null && forceAnonymousSender) || (sender != null && !tdlib.isChannel(sender) && Td.isAnonymous(tdlib.chatStatus(chat.getChatId())));
    invalidate();
  }
}
