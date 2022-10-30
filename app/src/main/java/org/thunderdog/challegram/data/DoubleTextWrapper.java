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
 * File created on 18/08/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.MessageSourceProvider;
import org.thunderdog.challegram.util.UserProvider;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.android.animator.BounceAnimator;
import me.vkryl.android.util.MultipleViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class DoubleTextWrapper implements MessageSourceProvider, UserProvider, TooltipOverlayView.LocationProvider {
  private final Tdlib tdlib;

  private long chatId;
  private final long userId;
  private long groupId, channelId;

  private boolean isOnline, ignoreOnline;

  private TdApi.ChatMember memberInfo;
  private boolean needAdminSign;
  private @Nullable Text adminSign;
  private @Nullable TdApi.User user;

  private String title;
  private Text trimmedTitle;
  private Text chatMark;

  private CharSequence subtitle;
  private Text trimmedSubtitle;

  private AvatarPlaceholder avatarPlaceholder;

  private ImageFile avatarFile;

  private final MultipleViewProvider currentViews = new MultipleViewProvider();
  private final BounceAnimator isAnonymous = new BounceAnimator(currentViews);
  private final int horizontalPadding;

  public DoubleTextWrapper (Tdlib tdlib, TdApi.Chat chat) {
    this.tdlib = tdlib;
    this.horizontalPadding = Screen.dp(72f) + Screen.dp(11f);

    this.chatId = chat.id;
    this.userId = TD.getUserId(chat);
    this.groupId = ChatId.toBasicGroupId(chat.id);
    this.channelId = ChatId.toSupergroupId(chat.id);

    if (chat.type.getConstructor() == TdApi.ChatTypeSupergroup.CONSTRUCTOR) {
      setChatMark(tdlib.chatScam(chat), tdlib.chatFake(chat));
    } else {
      setChatMark(false, false);
    }

    setTitle(chat.title);
    this.avatarPlaceholder = tdlib.chatPlaceholder(chat, false, AVATAR_PLACEHOLDER_RADIUS, null);
    if (chat.photo != null) {
      setPhoto(chat.photo.small);
    }
    updateSubtitle();
  }

  public DoubleTextWrapper (Tdlib tdlib, long userId, boolean needSubtitle) {
    this.tdlib = tdlib;
    this.horizontalPadding = Screen.dp(72f) + Screen.dp(11f);

    this.userId = userId;
    this.user = tdlib.cache().user(userId);

    setChatMark(this.user != null && this.user.isScam, this.user != null && this.user.isFake);
    setTitle(TD.getUserName(user));
    this.avatarPlaceholder = tdlib.cache().userPlaceholder(user, false, AVATAR_PLACEHOLDER_RADIUS, null);
    if (user != null && user.profilePhoto != null) {
      setPhoto(user.profilePhoto.small);
    }
    if (needSubtitle) {
      updateSubtitle();
    }
  }

  public TdApi.MessageSender getSenderId () {
    return userId != 0 ? new TdApi.MessageSenderUser(userId) : chatId != 0 ? (ChatId.isUserChat(chatId) ? new TdApi.MessageSenderUser(tdlib.chatUserId(chatId)) : new TdApi.MessageSenderChat(chatId)) : null;
  }

  private static final float AVATAR_PLACEHOLDER_RADIUS = 25f;

  private boolean needFullDescription;

  public static DoubleTextWrapper valueOf (Tdlib tdlib, TdApi.ChatMember member, boolean needFullDescription, boolean needAdminSign) {
    DoubleTextWrapper item;
    switch (member.memberId.getConstructor()) {
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        item = new DoubleTextWrapper(tdlib, ((TdApi.MessageSenderUser) member.memberId).userId, !needFullDescription);
        break;
      }
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        item = new DoubleTextWrapper(tdlib, tdlib.chatStrict(((TdApi.MessageSenderChat) member.memberId).chatId));
        break;
      }
      default: {
        throw new IllegalArgumentException(member.memberId.toString());
      }
    }
    item.setMember(member, needFullDescription, needAdminSign);
    return item;
  }

  private void setChatMark (boolean isScam, boolean isFake) {
    if (isScam || isFake) {
      chatMark = new Text.Builder(Lang.getString(isFake ? R.string.FakeMark : R.string.ScamMark), 0, Paints.robotoStyleProvider(10f), TextColorSets.Regular.NEGATIVE)
        .singleLine()
        .allBold()
        .clipTextArea()
        .build();
    } else {
      chatMark = null;
    }
  }

  public void setIgnoreOnline (boolean ignoreOnline) {
    if (this.ignoreOnline != ignoreOnline) {
      this.ignoreOnline = ignoreOnline;
      if (isOnline) {
        setOnline(false);
      }
    }
  }

  public void setMember (TdApi.ChatMember member, boolean needFullDescription, boolean needAdminStar) {
    boolean isUpdate = this.memberInfo != null && Td.equalsTo(this.memberInfo.memberId, member.memberId);
    this.memberInfo = member;
    this.needFullDescription = needFullDescription;
    this.needAdminSign = needAdminStar;
    this.isAnonymous.setValue(Td.isAnonymous(member.status), isUpdate && currentViews.hasAnyTargetToInvalidate());
    updateSubtitle();
    if (isUpdate) {
      buildTitle();
      currentViews.invalidate();
    }
  }

  public MultipleViewProvider getViewProvider () {
    return currentViews;
  }

  private void setPhoto (@Nullable TdApi.File file) {
    final int currentFileId = avatarFile != null ? avatarFile.getId() : 0;
    final int newFileId = file != null ? file.id : 0;
    if (currentFileId != newFileId) {
      if (file != null) {
        this.avatarFile = new ImageFile(tdlib, file);
        this.avatarFile.setSize(ChatView.getDefaultAvatarCacheSize());
      } else {
        this.avatarFile = null;
      }
      if (currentWidth > 0) {
        currentViews.invalidateContent(this);
      }
    }
  }

  public void updateTitleAndPhoto () {
    if (chatId != 0) {
      TdApi.Chat chat = tdlib.chat(chatId);
      if (chat != null) {
        setTitle(chat.title);
        avatarPlaceholder = tdlib.chatPlaceholder(chat, false, AVATAR_PLACEHOLDER_RADIUS, null);
        setPhoto(chat.photo != null ? chat.photo.small : null);
        currentViews.invalidate();
      }
    } else if (userId != 0) {
      TdApi.User user = tdlib.cache().user(userId);
      if (user != null) {
        setTitle(TD.getUserName(user.id, user));
        avatarPlaceholder = tdlib.cache().userPlaceholder(user, false, AVATAR_PLACEHOLDER_RADIUS, null);
        setPhoto(user.profilePhoto != null ? user.profilePhoto.small : null);
        currentViews.invalidate();
      }
    }
  }

  private static Paint.FontMetricsInt __metrics;

  private void setTitle (String newTitle) {
    if (!StringUtils.equalsOrBothEmpty(this.title, newTitle)) {
      Paint.FontMetricsInt metrics = __metrics;
      if (metrics == null) {
        metrics = new Paint.FontMetricsInt();
        __metrics = metrics;
      }
      this.title = newTitle;
      if (currentWidth != 0) {
        buildTitle();
        currentViews.invalidate();
      }
    }
  }

  public void updateSubtitle () {
    CharSequence description = needFullDescription ? TD.getMemberDescription(new TdlibContext(null, tdlib), memberInfo, false) : null;
    if (!StringUtils.isEmpty(description)) {
      setSubtitle(description);
      return;
    }
    if (userId != 0) {
      TdApi.User user = tdlib.cache().user(userId);
      boolean isOnline = TD.isOnline(user);
      String newSubtitle;
      if (isOnline) {
        newSubtitle = Lang.getString(R.string.status_Online);
      } else if (user != null && user.type.getConstructor() == TdApi.UserTypeBot.CONSTRUCTOR) {
        boolean hasAccess = ((TdApi.UserTypeBot) user.type).canReadAllGroupMessages;
        newSubtitle = Lang.getString(hasAccess ? R.string.BotStatusRead : R.string.BotStatusCantRead);
      } else {
        newSubtitle = tdlib.status().getPrivateChatSubtitle(userId, user, false);
      }
      setSubtitle(newSubtitle);
      setOnline(isOnline);
    } else {
      setSubtitle(tdlib.status().chatStatus(chatId));
      setOnline(false);
    }
  }

  private TdApi.ChatMessageSender chatMessageSender;
  private boolean isPremiumLocked;
  private boolean drawAnonymousIcon;
  private boolean drawFakeCheckbox;

  public void setChatMessageSender (TdApi.ChatMessageSender sender) {
    this.chatMessageSender = sender;
    this.isPremiumLocked = !tdlib.hasPremium() && sender.needsPremium;
    this.drawAnonymousIcon = !tdlib.isSelfSender(sender.sender) && !tdlib.isChannel(Td.getSenderId(sender.sender));

    buildTitle();
  }

  public void setDrawFakeCheckbox (boolean drawFakeCheckbox) {
    this.drawFakeCheckbox = drawFakeCheckbox;
  }

  public boolean isPremiumLocked () {
    return this.isPremiumLocked;
  }

  public @Nullable TdApi.ChatMessageSender getChatMessageSender () {
    return chatMessageSender;
  }

  private CharSequence forcedSubtitle;

  public void setForcedSubtitle (CharSequence newSubtitle) {
    this.forcedSubtitle  = newSubtitle;
    setIgnoreOnline(true);
    setSubtitle(!StringUtils.isEmpty(forcedSubtitle) ? forcedSubtitle: subtitle);
  }

  public void setSubtitle (CharSequence newSubtitle) {
    if (!StringUtils.isEmpty(forcedSubtitle)) {
      newSubtitle = forcedSubtitle;
    }

    if (!StringUtils.equalsOrBothEmpty(this.subtitle, newSubtitle)) {
      this.subtitle = newSubtitle;
      if (currentWidth != 0) {
        buildSubtitle();
        currentViews.invalidate();
      }
    }
  }

  private void setOnline (boolean isOnline) {
    if (this.isOnline != isOnline && !(ignoreOnline && isOnline)) {
      this.isOnline = isOnline;
      currentViews.invalidate();
    }
  }

  // DATA

  public long getChatId () {
    return chatId;
  }

  public long getUserId () {
    return userId;
  }

  public @Nullable TdApi.User getUser () {
    return user;
  }

  @Override
  public TdApi.User getTdUser () {
    return user;
  }

  public long getGroupId () {
    return groupId;
  }

  public long getChannelId () {
    return channelId;
  }

  public TdApi.ChatMember getMember () {
    return memberInfo;
  }

  // VIEW DATA

  public ImageFile getAvatarFile () {
    return avatarFile;
  }

  public AvatarPlaceholder getAvatarPlaceholder () {
    return avatarPlaceholder;
  }

  // Layouting

  private int currentWidth;

  public void layout (int width) {
    if (currentWidth == width || width <= 0) {
      return;
    }
    currentWidth = width;
    buildTitle();
    buildSubtitle();
  }

  private void buildTitle () {
    int availWidth = currentWidth - horizontalPadding;
    if (drawAnonymousIcon || isPremiumLocked) {
      availWidth -= Screen.dp(30);
    }

    String adminSign = null;
    if (memberInfo != null) {
      adminSign = Td.getCustomTitle(memberInfo.status);
      if (StringUtils.isEmpty(adminSign) && needAdminSign) {
        switch (memberInfo.status.getConstructor()) {
          case TdApi.ChatMemberStatusCreator.CONSTRUCTOR:
            adminSign = Lang.getString(R.string.message_ownerSign);
            break;
          case TdApi.ChatMemberStatusAdministrator.CONSTRUCTOR:
            adminSign = Lang.getString(R.string.message_adminSignPlain);
            break;
        }
      }
    }
    if (!StringUtils.isEmpty(adminSign)) {
      this.adminSign = new Text.Builder(adminSign, availWidth, Paints.robotoStyleProvider(13), TextColorSets.Regular.LIGHT).singleLine().build();
      availWidth -= this.adminSign.getWidth() + Screen.dp(4f);
    } else {
      this.adminSign = null;
    }

    if (chatMark != null) {
      chatMark.changeMaxWidth(availWidth);
      availWidth -= chatMark.getWidth() + Screen.dp(8f);
    }

    if (availWidth <= 0) {
      trimmedTitle = null;
      return;
    }

    this.trimmedTitle = StringUtils.isEmpty(title) ? null : new Text.Builder(title, availWidth, Paints.robotoStyleProvider(15), TextColorSets.Regular.NORMAL).allBold().singleLine().build();
  }

  private void buildSubtitle () {
    int availWidth = currentWidth - horizontalPadding;
    if (this.adminSign != null) {
      availWidth -= this.adminSign.getWidth() + Screen.dp(4f);
    }
    if (availWidth <= 0) {
      trimmedSubtitle = null;
      return;
    }
    if (!StringUtils.isEmpty(subtitle)) {
      // TODO: custom emoji support
      trimmedSubtitle = new Text.Builder(tdlib, subtitle, null, availWidth, Paints.robotoStyleProvider(15), TextColorSets.Regular.LIGHT, null)
        .singleLine()
        .build();
    } else {
      trimmedSubtitle = null;
    }
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (trimmedTitle != null) {
      trimmedTitle.toRect(outRect);
    }
  }

  public <T extends View & DrawableProvider> void draw (T view, Receiver receiver, Canvas c) {
    int left = Screen.dp(72f);
    boolean rtl = Lang.rtl();
    int viewWidth = view.getMeasuredWidth();

    final float anonymousFactor = isAnonymous.getFloatValue();
    if (anonymousFactor > 0f) {
      double radians = Math.toRadians(45f);
      float x = receiver.centerX() + (float) ((double) (receiver.getWidth() / 2) * Math.sin(radians));
      float y = receiver.centerY() + (float) ((double) (receiver.getHeight() / 2) * Math.cos(radians));
      Drawable incognitoIcon = view.getSparseDrawable(R.drawable.baseline_incognito_circle_18, R.id.theme_color_iconLight);
      c.drawCircle(x, y, incognitoIcon.getMinimumWidth() / 2f * anonymousFactor, Paints.fillingPaint(Theme.fillingColor()));
      if (anonymousFactor != 1f) {
        c.save();
        c.scale(anonymousFactor, anonymousFactor, x, y);
      }
      Drawables.draw(c, incognitoIcon, x - incognitoIcon.getMinimumWidth() / 2f, y - incognitoIcon.getMinimumHeight() / 2f, Paints.getIconLightPorterDuffPaint());
      if (anonymousFactor != 1f) {
        c.restore();
      }
    }

    if (drawFakeCheckbox) {
      double radians = Math.toRadians(45f);
      float cx = receiver.centerX() + (float) ((double) (receiver.getWidth() / 2) * Math.sin(radians));
      float cy = receiver.centerY() + (float) ((double) (receiver.getHeight() / 2) * Math.cos(radians));
      c.drawCircle(cx, cy, Screen.dp(11.5f), Paints.fillingPaint(Theme.fillingColor()));
      c.drawCircle(cx, cy, Screen.dp(10f), Paints.fillingPaint(Theme.radioFillingColor()));
      c.save();
      float lineSize = Screen.dp(2);
      float x1 = cx - Screen.dp(1.5f);
      float y1 = cy + Screen.dp(5.5f);
      float w2 = Screen.dp(10f);
      float h1 = Screen.dp(6f);
      c.rotate(-45f, x1, y1);
      c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(Theme.radioCheckColor()));
      c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(Theme.radioCheckColor()));
      c.restore();
    }
    if (drawAnonymousIcon) {
      Drawable incognitoIcon = view.getSparseDrawable(R.drawable.dot_baseline_acc_anon_24, R.id.theme_color_icon);
      float x = currentWidth - Screen.dp(28);
      float y = receiver.centerY();
      Drawables.draw(c, incognitoIcon, x - incognitoIcon.getMinimumWidth() / 2f, y - incognitoIcon.getMinimumHeight() / 2f, PorterDuffPaint.get(R.id.theme_color_icon));
    }
    if (isPremiumLocked) {
      Drawable incognitoIcon = view.getSparseDrawable(R.drawable.baseline_lock_16, R.id.theme_color_text);
      float x = currentWidth - Screen.dp(18 + 16);
      float y = receiver.centerY();
      Drawables.draw(c, incognitoIcon, x, y - incognitoIcon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_text)));
    }
    if (trimmedTitle != null) {
      trimmedTitle.draw(c, left, Screen.dp(13f));
    }

    if (adminSign != null) {
      adminSign.draw(c, viewWidth - Screen.dp(14f) - adminSign.getWidth(), view.getMeasuredHeight() / 2 - adminSign.getHeight() / 2, memberInfo != null && TD.isCreator(memberInfo.status) ? TextColorSets.Regular.NEUTRAL : null);
    }

    if (trimmedSubtitle != null) {
      trimmedSubtitle.draw(c, left, Screen.dp(33f), isOnline ? TextColorSets.Regular.NEUTRAL : null);
    }

    if (trimmedTitle != null && chatMark != null) {
      int cmLeft = left + trimmedTitle.getWidth() + Screen.dp(6f);
      RectF rct = Paints.getRectF();
      rct.set(cmLeft, Screen.dp(13f), cmLeft + chatMark.getWidth() + Screen.dp(8f), Screen.dp(13f) + trimmedTitle.getLineHeight(false));
      c.drawRoundRect(rct, Screen.dp(2f), Screen.dp(2f), Paints.getProgressPaint(Theme.getColor(R.id.theme_color_textNegative), Screen.dp(1.5f)));
      cmLeft += Screen.dp(4f);
      chatMark.draw(c, cmLeft, cmLeft + chatMark.getWidth(), 0, Screen.dp(13f) + ((trimmedTitle.getLineHeight(false) - chatMark.getLineHeight(false)) / 2));
    }
  }

  // Unused

  @Override
  public int getSourceDate () {
    return 0;
  }

  @Override
  public long getSourceMessageId () {
    return 0;
  }

  @Override
  public TdApi.Message getMessage () {
    return null;
  }
}
