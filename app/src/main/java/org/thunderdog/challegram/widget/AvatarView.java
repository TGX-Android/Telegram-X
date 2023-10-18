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
 * File created on 30/05/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.Destroyable;

public class AvatarView extends View implements Destroyable, TdlibCache.UserDataChangeListener, ChatListener, AttachDelegate {
  private static final int FLAG_NO_PLACEHOLDERS = 0x01;
  private static final int FLAG_NEED_FULL = 0x02;
  private static final int FLAG_NO_ROUND = 0x04;
  private static final int FLAG_NEED_OVERLAY = 0x08;
  private static final int FLAG_CUSTOM_WINDOW_MANAGEMENT = 0x10;

  private int flags;

  private final ImageReceiver receiver;
  private ImageReceiver preview;
  private Drawable overlayIcon;

  public AvatarView (Context context) {
    super(context);
    this.receiver = new ImageReceiver(this, 1);
    this.receiver.setRadius(0);
  }

  public void setNeedOverlay () {
    flags |= FLAG_NEED_OVERLAY;
    if (overlayIcon == null) {
      overlayIcon = Drawables.get(getResources(), R.drawable.baseline_camera_alt_24);
    }
  }

  public void setUseCustomWindowManagement () {
    flags |= FLAG_CUSTOM_WINDOW_MANAGEMENT;
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    return !(e.getAction() == MotionEvent.ACTION_DOWN && getAlpha() == 0f) && super.onTouchEvent(e);
  }

  public void setNoPlaceholders (boolean noPlaceholders) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NO_PLACEHOLDERS, noPlaceholders);
  }

  public void setNeedFull (boolean needFull) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NEED_FULL, needFull);
    if (needFull && preview == null) {
      preview = new ImageReceiver(this, 1);
      preview.copyBounds(receiver);
    }
  }

  public void setNoRound (boolean noRound) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_NO_ROUND, noRound);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    if ((flags & FLAG_CUSTOM_WINDOW_MANAGEMENT) == 0) {
      receiver.attach();
      if (preview != null) {
        preview.attach();
      }
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if ((flags & FLAG_CUSTOM_WINDOW_MANAGEMENT) == 0) {
      receiver.detach();
      if (preview != null) {
        preview.detach();
      }
    }
  }

  @Override
  public void attach () {
    receiver.attach();
    if (preview != null) {
      preview.attach();
    }
  }

  @Override
  public void detach () {
    receiver.detach();
    if (preview != null) {
      preview.detach();
    }
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    if (preview != null) {
      preview.destroy();
    }
    if (this.tdlib != null) {
      if (getUserId() != 0) {
        this.tdlib.cache().removeUserDataListener(getUserId(), this);
      }
      if (getChatId() != 0) {
        this.tdlib.listeners().unsubscribeFromChatUpdates(getChatId(), this);
      }
    }
    this.tdlib = null;
    this.chat = null;
    this.user = null;
    this.userId = 0;
    this.chatId = 0;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    final int left = getPaddingLeft();
    final int top = getPaddingTop();
    final int right = getMeasuredWidth() - getPaddingRight();
    final int bottom = getMeasuredHeight() - getPaddingBottom();

    receiver.setBounds(left, top, right, bottom);
    if (needRounds()) {
      receiver.setRadius(Math.min(receiver.getWidth(), receiver.getHeight()) / 2);
    }
    if (needFull()) {
      preview.copyBounds(receiver);
    }
  }

  public long getUserId () {
    return user != null ? user.id : userId;
  }

  public long getChatId () {
    return chat != null ? chat.id : chatId;
  }

  private TdlibAccount account;
  private TdApi.User user;
  private TdApi.Chat chat;
  private long userId;
  private long chatId;
  private Tdlib tdlib;
  private boolean hasPhoto, allowSavedMessages;
  private AvatarPlaceholder.Metadata avatarPlaceholderMetadata;
  private AvatarPlaceholder avatarPlaceholder;

  public void setUser (TdlibAccount account) {
    this.account = account;
    if (account != null) {
      ImageFile file = account.getAvatarFile(false);
      if (hasPhoto = (file != null)) {
        receiver.requestFile(file);
      } else {
        avatarPlaceholderMetadata = account.getAvatarPlaceholderMetadata();
        avatarPlaceholder = null;
        receiver.clear();
      }
    } else {
      receiver.clear();
      hasPhoto = false;
    }
    invalidate();
  }

  public void setUser (Tdlib tdlib, long userId, boolean allowSavedMessages) {
    setUser(tdlib, tdlib.cache().user(userId), allowSavedMessages);
  }

  public void setUser (Tdlib tdlib, @Nullable TdApi.User user, boolean allowSavedMessages) {
    long newUserId = user != null ? user.id : 0;
    setUser(tdlib, user, newUserId, allowSavedMessages, false);
  }

  private void setUser (Tdlib tdlib, @Nullable TdApi.User user, long newUserId, boolean allowSavedMessages, boolean ignoreChecks) {
    long oldUserId = getUserId();
    if (oldUserId != newUserId || ignoreChecks) {
      if (oldUserId != 0) {
        this.tdlib.cache().removeUserDataListener(oldUserId, this);
      }
      this.user = user;
      this.userId = newUserId;
      this.tdlib = tdlib;
      if (newUserId != 0) {
        tdlib.cache().addUserDataListener(newUserId, this);
      }
      if (user != null) {
        setPhoto(tdlib, user, allowSavedMessages);
      } else {
        receiver.clear();
      }
    }
  }

  public void setChat (Tdlib tdlib, @Nullable TdApi.Chat chat) {
    long newChatId = chat != null ? chat.id : 0;
    setChat(tdlib, chat, newChatId, false);
  }

  private void setChat (Tdlib tdlib, @Nullable TdApi.Chat chat, long newChatId, boolean ignoreChecks) {
    long oldChatId = getChatId();
    if (oldChatId != newChatId || ignoreChecks) {
      if (oldChatId != 0) {
        this.tdlib.listeners().unsubscribeFromChatUpdates(oldChatId, this);
      }
      this.chat = chat;
      this.tdlib = tdlib;
      if (newChatId != 0) {
        tdlib.listeners().subscribeToChatUpdates(newChatId, this);
      }
      if (chat != null) {
        setPhoto(tdlib, chat);
      } else {
        receiver.clear();
      }
    }
  }

  public void setMessageSender (Tdlib tdlib, @Nullable TdApi.MessageSender sender) {
    if (sender == null) {
      receiver.clear();
      return;
    };
    if (sender.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
      long userId = ((TdApi.MessageSenderUser) sender).userId;
      setUser(tdlib, tdlib.cache().user(userId), userId, false, true);
      return;
    }
    if (sender.getConstructor() == TdApi.MessageSenderChat.CONSTRUCTOR) {
      long chatId = ((TdApi.MessageSenderChat) sender).chatId;
      setChat(tdlib, tdlib.chat(chatId), chatId, true);
      return;
    }
    receiver.clear();
  }

  private static final int BLURRED_SIZE = 160;

  private boolean needFull () {
    return (flags & FLAG_NEED_FULL) != 0;
  }

  private float alpha;

  public void setMainAlpha (float alpha) {
    if (this.alpha != alpha) {
      if (this.alpha == receiver.getAlpha() && receiver.isLoaded()) {
        receiver.setAlpha(this.alpha = alpha);
      } else {
        this.alpha = alpha;
      }
    }
  }

  private float lettersSizeDp = 17f;

  public void setLettersSizeDp (float dp) {
    this.lettersSizeDp = dp;
  }

  private void setPhoto (Tdlib tdlib, TdApi.Chat chat) {
    if (hasPhoto = (chat.photo != null)) {
      setPhotoImpl(tdlib, chat.photo.small, chat.photo.big);
    } else {
      avatarPlaceholderMetadata = tdlib.chatPlaceholderMetadata(chat, true);
      avatarPlaceholder = null;
      receiver.clear();
    }
    invalidate();
  }

  private void setPhoto (Tdlib tdlib, TdApi.User user, boolean allowSavedMessages) {
    this.allowSavedMessages = allowSavedMessages;
    if (hasPhoto = (user.profilePhoto != null)) {
      setPhotoImpl(tdlib, user.profilePhoto.small, user.profilePhoto.big);
    } else {
      avatarPlaceholderMetadata = tdlib.cache().userPlaceholderMetadata(user, allowSavedMessages);
      avatarPlaceholder = null;
      receiver.clear();
    }
    invalidate();
  }

  private void setPhotoImpl (Tdlib tdlib, TdApi.File photoSmall, TdApi.File photoFull) {
    ImageFile imageFile = new ImageFile(tdlib, photoSmall);
    imageFile.setScaleType(ImageFile.CENTER_CROP);
    if (needFull()) {
      imageFile.setBlur(7);
      imageFile.setNeedFitSize();
      imageFile.setSize(ChatView.getDefaultAvatarCacheSize() == BLURRED_SIZE ? BLURRED_SIZE - 1 : BLURRED_SIZE);
      preview.requestFile(imageFile);
      imageFile = new ImageFile(tdlib, photoFull != null ? photoFull : photoSmall);
      imageFile.setScaleType(ImageFile.CENTER_CROP);
      receiver.requestFile(imageFile);
    } else {
      imageFile.setSize(ChatView.getDefaultAvatarCacheSize());
      receiver.requestFile(imageFile);
    }
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    post(() -> {
      if (tdlib != null && getUserId() == user.id) {
        AvatarView.this.user = user;
        AvatarView.this.userId = user.id;
        setPhoto(tdlib, user, allowSavedMessages);
      }
    });
  }

  @Override
  public void onChatPhotoChanged (final long chatId, @Nullable final TdApi.ChatPhotoInfo photo) {
    post(() -> {
      if (tdlib != null && getChatId() == chatId) {
        setPhoto(tdlib, chat);
      }
    });
  }

  private boolean needPlaceholders () {
    return (flags & FLAG_NO_PLACEHOLDERS) == 0;
  }

  private boolean needRounds () {
    return (flags & FLAG_NO_ROUND) == 0;
  }

  private void drawPlaceholder (Canvas c, @ColorId int colorId) {
    if (needRounds()) {
      c.drawCircle(receiver.centerX(), receiver.centerY(), receiver.getRadius(), Paints.fillingPaint(
        Theme.getColor(colorId)
      ));
    } else {
      c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(Theme.placeholderColor()));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (account != null || getUserId() != 0 || getChatId() != 0) {
      if (hasPhoto) {
        if (receiver.needPlaceholder() && (preview == null || preview.needPlaceholder())) {
          drawPlaceholder(c, ColorId.placeholder);
        }
        if (preview != null && receiver.needPlaceholder()) {
          preview.draw(c);
        }
        receiver.draw(c);
      } else if (needPlaceholders()) {
        if ((flags & FLAG_NEED_OVERLAY) == 0) {
          if (avatarPlaceholderMetadata != null) {
            if (avatarPlaceholder == null)
              avatarPlaceholder = new AvatarPlaceholder(Screen.px(receiver.getWidth() / 2), avatarPlaceholderMetadata, null);
            avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
          }
        } else {
          drawPlaceholder(c, avatarPlaceholderMetadata != null ? avatarPlaceholderMetadata.colorId : ColorId.placeholder);
        }
      }
    }
    if ((flags & FLAG_NEED_OVERLAY) != 0) {
      if (hasPhoto) {
        drawPlaceholder(c, ColorId.statusBar);
      }
      if (overlayIcon != null)
        Drawables.draw(c, overlayIcon, receiver.centerX() - overlayIcon.getMinimumWidth() / 2, receiver.centerY() - overlayIcon.getMinimumHeight() / 2, Paints.whitePorterDuffPaint());
    }
  }
}
