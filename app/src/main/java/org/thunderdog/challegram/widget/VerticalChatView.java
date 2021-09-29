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
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerEntry;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

/**
 * Date: 7/17/17
 * Author: default
 */

public class VerticalChatView extends BaseView implements Destroyable, ChatListener, FactorAnimator.Target, TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, NotificationSettingsListener, AttachDelegate, SimplestCheckBoxHelper.Listener, TextColorSet, TooltipOverlayView.LocationProvider {
  private final ImageReceiver receiver;
  private final Counter counter;

  private SimplestCheckBoxHelper checkBoxHelper;

  public VerticalChatView (@NonNull Context context, Tdlib tdlib) {
    super(context, tdlib);

    Views.setClickable(this);
    RippleSupport.setTransparentSelector(this);

    receiver = new ImageReceiver(this, Screen.dp(25f));
    counter = new Counter.Builder().callback(this).outlineColor(R.id.theme_color_filling).build();
  }

  public void setIsChecked (boolean isChecked, boolean animated) {
    if (isChecked != (checkBoxHelper != null && checkBoxHelper.isChecked())) {
      if (checkBoxHelper == null) {
        checkBoxHelper = new SimplestCheckBoxHelper(this, receiver);
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
    final int radius = Screen.dp(25f);
    final int centerX = getMeasuredWidth() / 2;
    final int centerY = getMeasuredHeight() / 2 - Screen.dp(11f);
    receiver.setBounds(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    buildTrimmedTitle();
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    setChat(null);
  }

  private ImageFile avatarFile;
  private AvatarPlaceholder avatarPlaceholder;

  private void setAvatar (ImageFile imageFile) {
    this.avatarFile = imageFile;
    receiver.requestFile(imageFile);
    invalidate();
  }

  private void setAvatarPlaceholder (AvatarPlaceholder.Metadata avatarPlaceholderMetadata) {
    if (avatarPlaceholderMetadata != null) {
      this.avatarPlaceholder = new AvatarPlaceholder(25f, avatarPlaceholderMetadata, null);
    } else {
      this.avatarPlaceholder = null;
    }
    invalidate();
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
    final int colorId = checkFactor == 1f ? R.id.theme_color_textLink : chat.isSecret() ? R.id.theme_color_textGreen : R.id.theme_color_textAccent;
    titleView.setTextColor(checkFactor == 0f ? TGTheme.getColor(colorId) : ColorChanger.inlineChange(TGTheme.getColor(colorId), TGTheme.getColor(R.id.theme_color_textLink), checkFactor));
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
      long oldUserId = getUserId();
      if (oldUserId != 0) {
        tdlib.cache().unsubscribeFromUserUpdates(oldUserId, this);
      }
      this.chat = chat;
      setPreviewChatId(chat != null ? chat.getList() : null, newChatId, null);
      if (chat != null) {
        updateTextColor();
        updateChat(false);
        long newUserId = getUserId();
        setIsOnline(!chat.isSelfChat() && newUserId != 0 && tdlib.cache().isOnline(newUserId), false);
        if (!chat.noSubscription()) {
          tdlib.listeners().subscribeToChatUpdates(newChatId, this);
          tdlib.listeners().subscribeToSettingsUpdates(newChatId, this);
        }
        if (newUserId != 0) {
          tdlib.cache().subscribeToUserUpdates(newUserId, this);
        }
      } else {
        setAvatar(null);
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
    setAvatarPlaceholder(chat.getAvatarPlaceholderMetadata());
    setAvatar(chat.getAvatar());
    setTitle(chat.getSingleLineTitle().toString());
    counter.setCount(chat.getUnreadCount(), !chat.notificationsEnabled(), isUpdate && isParentVisible());
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
  public void onChatMarkedAsUnread (long chatId, boolean isMarkedAsUnread) {
    updateChat(chatId);
  }

  // Animator target

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_ONLINE: {
        setOnlineFactor(factor);
        break;
      }
    }
  }

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

  @Override
  @UiThread
  public void onUserStatusChanged (long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (!uiOnly && getUserId() == userId) {
      setIsOnline(TD.isOnline(status), true);
    }
  }

  private static final int ANIMATOR_ONLINE = 1;
  private float onlineFactor;

  private boolean isOnline;

  private FactorAnimator onlineAnimator;

  private void forceOnlineFactor (boolean isOnline) {
    if (onlineAnimator != null) {
      onlineAnimator.forceFactor(isOnline ? 1f : 0f);
    }
    setOnlineFactor(isOnline ? 1f : 0f);
  }

  private void animateOnlineFactor (boolean isOnline) {
    if (onlineAnimator == null) {
      onlineAnimator = new FactorAnimator(ANIMATOR_ONLINE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    onlineAnimator.cancel();
    if (isOnline && onlineFactor == 0f) {
      onlineAnimator.setInterpolator(AnimatorUtils.OVERSHOOT_INTERPOLATOR);
      onlineAnimator.setDuration(210l);
    } else {
      onlineAnimator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      onlineAnimator.setDuration(100l);
    }
    onlineAnimator.animateTo(isOnline ? 1f : 0f);
  }

  private void setIsOnline (boolean isOnline, boolean animated) {
    if (this.isOnline != isOnline) {
      this.isOnline = isOnline;
      if (animated && isParentVisible()) {
        animateOnlineFactor(isOnline);
      } else {
        forceOnlineFactor(isOnline);
      }
    }
  }

  private void setOnlineFactor (float factor) {
    if (this.onlineFactor != factor) {
      this.onlineFactor = factor;
      invalidate();
    }
  }

  // Drawing


  @Override
  public int defaultTextColor () {
    boolean isSecret = chat != null && chat.isSecret();
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    return isSecret ? Theme.getColor(R.id.theme_color_textSecure) : checkFactor == 0f ? Theme.textAccentColor() : ColorUtils.fromToArgb(Theme.textAccentColor(), Theme.getColor(R.id.theme_color_textSearchQueryHighlight), checkFactor);
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    receiver.toRect(outRect);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (avatarFile != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, Screen.dp(25f));
      }
      receiver.draw(c);
    } else if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
    }
    final float checkFactor = checkBoxHelper != null ? checkBoxHelper.getCheckFactor() : 0f;
    double radians = Math.toRadians(Lang.rtl() ? 225f : 135f);
    float x = receiver.centerX() + (float) ((double) (receiver.getWidth() / 2) * Math.sin(radians));
    float y = receiver.centerY() + (float) ((double) (receiver.getHeight() / 2) * Math.cos(radians));
    counter.draw(c, x, y, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, (1f - checkFactor));

    DrawAlgorithms.drawOnline(c, receiver, onlineFactor * (1f - checkFactor));
    if (checkFactor > 0f) {
      DrawAlgorithms.drawSimplestCheckBox(c, receiver, checkFactor);
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
  }

  private static final float SECURE_OFFSET = 12f;

  private static TextPaint getTextPaint () {
    return Paints.getSmallTitlePaint();
  }
}
