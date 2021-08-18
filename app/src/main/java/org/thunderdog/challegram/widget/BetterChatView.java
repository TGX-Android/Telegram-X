package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.CallItem;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.data.TGFoundMessage;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.NotificationSettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Icons;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.unit.BitwiseUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

/**
 * Date: 6/3/17
 * Author: default
 */

public class BetterChatView extends BaseView implements Destroyable, RemoveHelper.RemoveDelegate, ChatListener, TdlibCache.UserDataChangeListener, TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, NotificationSettingsListener, TdlibCache.UserStatusChangeListener, DrawableProvider, TooltipOverlayView.LocationProvider {
  private static final int FLAG_FAKE_TITLE = 1;
  private static final int FLAG_SECRET = 1 << 1;
  private static final int FLAG_ONLINE = 1 << 2;
  private static final int FLAG_SELF_CHAT = 1 << 3;

  private int flags;

  private final ImageReceiver receiver;

  private CharSequence title;
  private CharSequence trimmedTitle;
  private float trimmedTitleWidth;
  private Layout titleLayout;

  private CharSequence subtitle;
  private CharSequence trimmedSubtitle;
  private float trimmedSubtitleWidth;
  private Layout subtitleLayout;

  private String time;
  private float timeWidth;

  private final Counter counter = new Counter.Builder().callback((counter, sizeChanged) -> {
    if (sizeChanged) {
      setTrimmedTitle();
      setTrimmedSubtitle();
    }
    invalidate();
  }).build();

  private ImageFile avatar;
  private AvatarPlaceholder avatarPlaceholder;

  private int subtitleIcon;
  private Drawable subtitleIconDrawable;
  private @ThemeColorId
  int subtitleIconColorId;

  public BetterChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    this.receiver = new ImageReceiver(this, ChatView.getAvatarRadius(Settings.CHAT_MODE_2LINE));
    receiver.setBounds(Screen.dp(11f), Screen.dp(10f), Screen.dp(11f) + Screen.dp(52f), Screen.dp(10f) + Screen.dp(52f));
  }

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    setChatImpl(null);
    setMessageImpl(null);
  }

  @SuppressWarnings("WrongConstant")
  public void setCallItem (CallItem item) {
    int userId = item.getUserId();
    TdApi.User user = tdlib.cache().user(userId);

    setPreviewChatId(null, item.getChatId(), null, new MessageId(item.getChatId(), item.getLatestMessageId()), null);

    setTime(Lang.time(item.getDate(), TimeUnit.SECONDS));
    setTitle(TD.getUserName(userId, user));
    setSubtitleIcon(item.getSubtitleIcon(), item.getSubtitleIconColorId());
    setSubtitle(item.getSubtitle());
    boolean hasAvatar = user != null && user.profilePhoto != null;
    setAvatar(hasAvatar ? user.profilePhoto.small : null, hasAvatar || user == null ? null : tdlib.cache().userPlaceholder(user, true, ChatView.getAvatarSizeDp(Settings.CHAT_MODE_2LINE) / 2f, null));
    invalidate();
  }

  private int lastWidth;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    if (lastWidth != width) {
      lastWidth = width;

      layoutReceiver(width);

      setTrimmedTitle();
      setTrimmedSubtitle();
    }
  }

  private void layoutReceiver (int width) {
    if (Lang.rtl()) {
      int x = Screen.dp(11f);
      int avatarWidth = Screen.dp(52f);
      receiver.setBounds(width - x - avatarWidth, Screen.dp(10f), width - x, Screen.dp(10f) + Screen.dp(52f));
    } else {
      receiver.setBounds(Screen.dp(11f), Screen.dp(10f), Screen.dp(11f) + Screen.dp(52f), Screen.dp(10f) + Screen.dp(52f));
    }
  }

  public void setTime (String time) {
    if (!StringUtils.equalsOrBothEmpty(this.time, time)) {
      this.time = time;
      float newTimeWidth = U.measureText(time, ChatView.getTimePaint());
      if (this.timeWidth != newTimeWidth) {
        this.timeWidth = newTimeWidth;
        setTrimmedTitle();
      }
    }
  }

  public void setUnreadCount (int unreadCount, boolean muted, boolean animated) {
    counter.setCount(unreadCount, muted, animated);
  }

  public void setSubtitleIcon (int icon, @ThemeColorId int color) {
    if (this.subtitleIcon != icon || this.subtitleIconColorId != color) {
      this.subtitleIconColorId = color;
      if (this.subtitleIcon != icon) {
        boolean prevHadIcon = subtitleIcon != 0;
        this.subtitleIcon = icon;
        this.subtitleIconDrawable = getSparseDrawable(icon, 0);
        boolean nowHasIcon = icon != 0;
        if (prevHadIcon != nowHasIcon) {
          setTrimmedSubtitle();
        }
      }
      invalidate();
    }
  }

  public void setAvatar (@Nullable TdApi.File file, @Nullable AvatarPlaceholder avatarPlaceholder) {
    ImageFile avatar;
    if (file != null) {
      if (this.avatar == null || Td.getId(this.avatar.getFile()) != file.id) {
        avatar = new ImageFile(tdlib, file);
        avatar.setSize(ChatView.getDefaultAvatarCacheSize());
      } else {
        avatar = this.avatar;
      }
    } else {
      avatar = null;
    }
    setAvatar(avatar, avatarPlaceholder);
  }

  public void setIsOnline (boolean isOnline) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_ONLINE, isOnline);
    if (this.flags != flags) {
      this.flags = flags;
      invalidate();
    }
  }

  public void setIsSecret (boolean isSecret) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_SECRET, isSecret);
    if (this.flags != flags) {
      this.flags = flags;
      setTrimmedTitle();
      invalidate();
    }
  }

  public void setAvatar (ImageFile avatar, AvatarPlaceholder avatarPlaceholder) {
    this.avatar = avatar;
    this.avatarPlaceholder = avatarPlaceholder;
    receiver.requestFile(avatar);
  }

  public void setAvatar (ImageFile avatar, AvatarPlaceholder.Metadata avatarPlaceholderMetadata) {
    setAvatar(avatar, avatarPlaceholderMetadata != null ? new AvatarPlaceholder(ChatView.getAvatarSizeDp(Settings.CHAT_MODE_2LINE) / 2f, avatarPlaceholderMetadata, null) : null);
  }

  public void setTitle (CharSequence title) {
    if (!StringUtils.equalsOrBothEmpty(this.title, title)) {
      this.title = title;
      this.flags = BitwiseUtils.setFlag(flags, FLAG_FAKE_TITLE, title != null && Text.needFakeBold(title.toString()));
      setTrimmedTitle();
    }
  }

  private void setTrimmedTitle () {
    int width = getMeasuredWidth();
    final CharSequence title = this.title;
    if (width <= 0 || StringUtils.isEmpty(title)) {
      trimmedTitle = null;
      trimmedTitleWidth = 0f;
    } else {
      float avail = width - Screen.dp(72f) - ChatView.getTimePaddingRight();

      if (timeWidth != 0) {
        avail -= timeWidth +  ChatView.getTimePaddingLeft();
      }
      if ((flags & FLAG_SECRET) != 0) {
        avail -= Screen.dp(15f);
      }
      avail -= counter.getScaledWidth(Screen.dp(8f) + Screen.dp(23f));
      boolean fakeTitle = (flags & FLAG_SECRET) != 0;
      trimmedTitle = TextUtils.ellipsize(Emoji.instance().replaceEmoji(title), ChatView.getTitlePaint(fakeTitle), avail, TextUtils.TruncateAt.END);
      if (!(trimmedTitle instanceof String)) {
        int maxWidth = Screen.widestSide();
        titleLayout = U.createLayout(trimmedTitle, maxWidth, ChatView.getTitlePaint(fakeTitle));
        trimmedTitleWidth = titleLayout.getWidth();
      } else {
        titleLayout = null;
        trimmedTitleWidth = U.measureText(trimmedTitle, ChatView.getTitlePaint(fakeTitle));
      }
    }
  }

  public void setSubtitle (CharSequence subtitle) {
    if (!StringUtils.equalsOrBothEmpty(this.subtitle, subtitle)) {
      this.subtitle = subtitle;
      setTrimmedSubtitle();
    }
  }

  public boolean hasComplexText () {
    return subtitleLayout != null; // || titleLayout != null
  }

  private void setTrimmedSubtitle () {
    int width = getMeasuredWidth();
    if (width <= 0 || StringUtils.isEmpty(subtitle)) {
      trimmedSubtitle = null;
      trimmedSubtitleWidth = 0f;
    } else {
      float avail = width - Screen.dp(72f) - ChatView.getTimePaddingRight();
      if (subtitleIcon != 0) {
        avail -= Screen.dp(18f);
      }
      avail -= counter.getScaledWidth(Screen.dp(8f) + Screen.dp(23f));
      trimmedSubtitle = TextUtils.ellipsize(Emoji.instance().replaceEmoji(subtitle), Paints.getTextPaint16(), avail, TextUtils.TruncateAt.END);
      if (!(trimmedSubtitle instanceof String)) {
        int maxWidth = Screen.widestSide();
        subtitleLayout = U.createLayout(trimmedSubtitle, (int) Math.max(maxWidth, avail), Paints.getTextPaint16());
        trimmedSubtitleWidth = subtitleLayout.getWidth();
      } else {
        subtitleLayout = null;
        trimmedSubtitleWidth = U.measureText(trimmedSubtitle, Paints.getTextPaint16());
      }
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (trimmedTitle != null) {
      int titleLeft = Screen.dp(72f);
      int titleTop = Screen.dp(28f) + Screen.dp(1f) - Screen.dp(16f);
      Paint.FontMetricsInt fm = ChatView.getTitlePaint((flags & FLAG_FAKE_TITLE) != 0).getFontMetricsInt();
      int titleBottom = titleTop + U.getLineHeight(fm);
      int titleWidth = (int) (titleLayout != null ? (titleLayout.getLineCount() > 0 ? titleLayout.getLineWidth(0) : 0) : trimmedTitleWidth);
      outRect.set(titleLeft, titleTop, titleLeft + titleWidth, titleBottom);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (removeHelper != null) {
      removeHelper.save(c);
    }

    final int width = getMeasuredWidth();
    final boolean rtl = Lang.rtl();

    layoutReceiver(width);

    if (avatar != null) {
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholderRounded(c, ChatView.getAvatarRadius(Settings.CHAT_MODE_2LINE));
      }
      receiver.draw(c);
    } else if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
    }
    if (trimmedTitle != null) {
      boolean isSecret = (flags & FLAG_SECRET) != 0;
      Paint paint = ChatView.getTitlePaint((flags & FLAG_FAKE_TITLE) != 0);
      int titleLeft = Screen.dp(72f);
      if (isSecret) {
        Drawables.drawRtl(c, Icons.getSecureDrawable(), titleLeft - Screen.dp(6f), Screen.dp(12f), Paints.getGreenPorterDuffPaint(), width, rtl);
        titleLeft += Screen.dp(15f);
        paint.setColor(Theme.getColor(R.id.theme_color_textSecure));
      }
      int titleTop = Screen.dp(28f) + Screen.dp(1f);
      if (titleLayout != null) {
        // titleTop -= Screen.dp(14.5f);
        int originalColor = titleLayout.getPaint().getColor();
        titleLayout.getPaint().setColor(paint.getColor());
        c.save();
        float left;
        if (rtl)
          left = width - titleLeft - (titleLayout.getLineCount() > 0 ? titleLayout.getLineLeft(0) + titleLayout.getLineWidth(0) : 0);
        else
          left = titleLeft - (titleLayout.getLineCount() > 0 ? titleLayout.getLineLeft(0) : 0);
        c.translate(left, titleTop - Screen.dp(16f));
        titleLayout.draw(c);
        c.restore();
        titleLayout.getPaint().setColor(originalColor);
      } else {
        c.drawText((String) trimmedTitle, rtl ? width - titleLeft - trimmedTitleWidth : titleLeft, titleTop, paint);
      }
      if (isSecret || titleLayout != null) {
        paint.setColor(Theme.textAccentColor());
      }
    }
    int subtitleOffset = -Screen.dp(1f);
    if (trimmedSubtitle != null) {
      int subtitleLeft = Screen.dp(72f);
      if (subtitleIcon != 0) {
        subtitleLeft += Screen.dp(20f);
      }
      int subtitleTop = Screen.dp(54f) + subtitleOffset;
      if (subtitleLayout != null) {
        subtitleTop -= Screen.dp(14.5f);
        c.save();
        float left;
        if (rtl)
          left = width - subtitleLeft - (subtitleLayout.getLineCount() > 0 ? subtitleLayout.getLineLeft(0) + subtitleLayout.getLineWidth(0) : 0);
        else
          left = subtitleLeft - (subtitleLayout.getLineCount() > 0 ? subtitleLayout.getLineLeft(0) : 0);
        c.translate(left, subtitleTop);
        Paints.getTextPaint16(Theme.getColor(R.id.theme_color_textLight));
        subtitleLayout.draw(c);
        c.restore();
        Paints.getTextPaint16(Theme.getColor(R.id.theme_color_textLight));
      } else {
        c.drawText((String) trimmedSubtitle, rtl ? width - subtitleLeft - trimmedSubtitleWidth : subtitleLeft, subtitleTop, Paints.getTextPaint16((flags & FLAG_ONLINE) != 0 ? Theme.getColor(R.id.theme_color_textNeutral) : Theme.textDecentColor()));
      }
    }
    if (subtitleIcon != 0) {
      Drawables.drawRtl(c, subtitleIconDrawable, Screen.dp(72f), Screen.dp(subtitleIcon == R.drawable.baseline_call_missed_18 ? 40f : 39f) + subtitleOffset, PorterDuffPaint.get(subtitleIconColorId), width, rtl);
    }
    if (time != null) {
      c.drawText(time, rtl ? ChatView.getTimePaddingRight() : width - ChatView.getTimePaddingRight() - timeWidth, Screen.dp(28f), ChatView.getTimePaint());
    }

    counter.draw(c, rtl ? ChatView.getTimePaddingRight() + Screen.dp(11.5f) : width - ChatView.getTimePaddingRight() - Screen.dp(11.5f), getMeasuredHeight() / 2f, rtl ? Gravity.LEFT : Gravity.RIGHT, 1f);

    if (removeHelper != null) {
      removeHelper.restore(c);
      removeHelper.draw(c);
    }
  }

  // Found chat

  private TGFoundChat lastChat;
  private boolean needStatusUiUpdates;

  private void addListeners (@NonNull TdApi.Chat chat, boolean add, boolean needStatusUiUpdates) {
    if (add) {
      tdlib.listeners().subscribeToChatUpdates(chat.id, this);
      tdlib.listeners().subscribeToSettingsUpdates(chat.id, this);
    } else {
      tdlib.listeners().unsubscribeFromChatUpdates(chat.id, this);
      tdlib.listeners().unsubscribeFromSettingsUpdates(chat.id, this);
    }
    switch (chat.type.getConstructor()) {
      case TdApi.ChatTypePrivate.CONSTRUCTOR:
      case TdApi.ChatTypeSecret.CONSTRUCTOR: {
        if (add) {
          this.needStatusUiUpdates = needStatusUiUpdates;
          tdlib.cache().subscribeToUserUpdates(TD.getUserId(chat.type), this);
        } else {
          tdlib.cache().unsubscribeFromUserUpdates(TD.getUserId(chat.type), this);
        }
        break;
      }
      case TdApi.ChatTypeSupergroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromSupergroupUpdates(ChatId.toSupergroupId(chat.id), this);
        }
        break;
      }
      case TdApi.ChatTypeBasicGroup.CONSTRUCTOR: {
        if (add) {
          tdlib.cache().subscribeToGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        } else {
          tdlib.cache().unsubscribeFromGroupUpdates(ChatId.toBasicGroupId(chat.id), this);
        }
        break;
      }
    }
  }

  @Override
  public void onNotificationSettingsChanged (TdApi.NotificationSettingsScope scope, TdApi.ScopeNotificationSettings settings) {
    // TODO update?
  }

  @Override
  public void onNotificationSettingsChanged (long chatId, TdApi.ChatNotificationSettings settings) {
    updateChat(chatId);
  }

  private void addListeners (int userId, boolean add, boolean needStatusUiUpdates) {
    if (add) {
      this.needStatusUiUpdates = needStatusUiUpdates;
      tdlib.cache().subscribeToUserUpdates(userId, this);
    } else {
      tdlib.cache().unsubscribeFromUserUpdates(userId, this);
    }
  }

  private void setChatImpl (@Nullable TGFoundChat chat) {
    if (lastChat != null) {
      if (lastChat.getChat() != null) {
        addListeners(lastChat.getChat(), false, needStatusUiUpdates);
      } else if (lastChat.getUserId() != 0) {
        addListeners(lastChat.getUserId(), false, needStatusUiUpdates);
      }
    }
    this.lastChat = chat;
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, chat != null && chat.isSelfChat());
    if (chat != null) {
      long chatId = 0;
      setIsSecret(chat.isSecret());
      if (chat.getChat() != null) {
        addListeners(chat.getChat(), true, !chat.isGlobal());
        chatId = chat.getChat().id;
      } else if (chat.getUserId() != 0) {
        addListeners(lastChat.getUserId(), true, !chat.isGlobal());
        chatId = ChatId.fromUserId(lastChat.getUserId());
      }
      setPreviewChatId(chat.getList(), chatId, null);
    } else {
      setPreviewChatId(null, 0, null);
    }
  }

  public void setChat (@Nullable TGFoundChat chat) {
    if (chat == lastChat) {
      receiver.requestFile(avatar);
      return;
    }
    if (lastMessage != null) {
      setMessageImpl(null);
    }
    setChatImpl(chat);
    if (chat != null) {
      updateChat(false);
    }
  }

  private void updateChat (boolean update) {
    if (update) {
      lastChat.updateChat();
    }
    setTitle(lastChat.getTitle());
    updateSubtitle();
    setAvatar(lastChat.getAvatar(), lastChat.getAvatarPlaceholderMetadata());
    setTime(null);
    setUnreadCount(lastChat.getUnreadCount(), !lastChat.notificationsEnabled(), update);
  }

  private void updateSubtitle () {
    if (lastChat != null) {
      if (StringUtils.isEmpty(lastChat.getForcedSubtitle())) {
        final int userId = lastChat.getUserId();
        if (lastChat.isGlobal()) {
          setSubtitle(lastChat.getUsername());
        } else {
          String subtitle = (userId != 0 ? tdlib.status().getPrivateChatSubtitle(userId) : tdlib.status().chatStatus(lastChat.getId())).toString();
          if (lastChat.needForceUsername()) {
            String username = userId != 0 ? tdlib.cache().userUsername(userId) : tdlib.chatUsername(lastChat.getId());
            if (!StringUtils.isEmpty(username)) {
              subtitle = "@" + username + ", " + subtitle;
            }
          }
          setSubtitle(subtitle);
        }
        setIsOnline(!tdlib.isSelfUserId(userId) && tdlib.cache().isOnline(userId));
      } else {
        setSubtitle(lastChat.getForcedSubtitle());
        setIsOnline(false);
      }
      invalidate();
    }
  }

  // Update listeners

  private void updateChat (final long chatId) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getId() == chatId) {
        updateChat(true);
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    updateChat(chatId);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
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

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getUserId() == user.id) {
        updateChat(true);
      }
    });
  }

  @Override
  public void onUserFullUpdated (int userId, TdApi.UserFullInfo userFull) { }

  @Override
  public boolean needUserStatusUiUpdates () {
    return needStatusUiUpdates;
  }

  @Override
  @UiThread
  public void onUserStatusChanged (int userId, TdApi.UserStatus status, boolean uiOnly) {
    if (lastChat != null && lastChat.getUserId() == userId) {
      updateSubtitle();
    }
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    updateSubtitleIfNeeded(ChatId.fromBasicGroupId(basicGroup.id));
  }

  @Override
  public void onBasicGroupFullUpdated (int basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    updateSubtitleIfNeeded(ChatId.fromBasicGroupId(basicGroupId));
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    updateSubtitleIfNeeded(chatId);
  }

  @Override
  public void onSupergroupUpdated (final TdApi.Supergroup supergroup) {
    updateSubtitleIfNeeded(ChatId.fromSupergroupId(supergroup.id));
  }

  @Override
  public void onSupergroupFullUpdated (final int supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    updateSubtitleIfNeeded(ChatId.fromSupergroupId(supergroupId));
  }

  private void updateSubtitleIfNeeded (long chatId) {
    tdlib.uiExecute(() -> {
      if (lastChat != null && lastChat.getChatId() == chatId) {
        updateSubtitle();
      }
    });
  }

  // Remove delegate

  private RemoveHelper removeHelper;

  @Override
  public void setRemoveDx (float dx) {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    removeHelper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    if (removeHelper == null) {
      removeHelper = new RemoveHelper(this, R.drawable.baseline_delete_24);
    }
    removeHelper.onSwipe();
  }

  // Message

  private TGFoundMessage lastMessage;

  private void setMessageImpl (TGFoundMessage message) {
    if (lastMessage != null) {
      tdlib.listeners().unsubscribeFromChatUpdates(lastMessage.getChat().getId(), this);
    }
    lastMessage = message;
    if (message != null) {
      final long chatId = message.getChat().getId();
      setIsSecret(message.getChat().isSecret());
      setPreviewChatId(message.getChat().getList(), chatId, null, new MessageId(chatId, message.getId()), null);
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
    } else {
      clearPreviewChat();
    }
  }

  public void setMessage (@Nullable TGFoundMessage foundMessage) {
    if (lastChat != null) {
      setChatImpl(null);
    } else if (lastMessage == foundMessage) {
      receiver.requestFile(foundMessage != null ? foundMessage.getAvatar() : null);
      return;
    }
    setMessageImpl(foundMessage);
    if (foundMessage != null) {
      TdApi.Message message = foundMessage.getMessage();
      flags = BitwiseUtils.setFlag(flags, FLAG_SELF_CHAT, foundMessage.getChat().isSelfChat());
      setTime(Lang.timeOrDateShort(message.date, TimeUnit.SECONDS));
      setTitle(foundMessage.getChat().getTitle());
      setSubtitle(foundMessage.getText());
      setUnreadCount(0, counter.isMuted(), false);
      setAvatar(foundMessage.getAvatar(), foundMessage.getAvatarPlaceholderMetadata() != null ? new AvatarPlaceholder(ChatView.getAvatarSizeDp(Settings.CHAT_MODE_2LINE) / 2f, foundMessage.getAvatarPlaceholderMetadata(), null) : null);
      invalidate();
    }
  }
}
