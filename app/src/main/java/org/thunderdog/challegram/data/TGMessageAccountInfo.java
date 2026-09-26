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
 * File created on 26/09/2026
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

import tgx.td.Td;

public class TGMessageAccountInfo extends TGMessage {
  private final TdApi.AccountInfo info;

  private final TextColorSet primaryColorSet = () ->
    useBubbles() ? getBubbleTransparentTextColor(manager) : Theme.textAccentColor();
  private final TextColorSet secondaryColorSet = () ->
    useBubbles() ?
      ColorUtils.alphaColor(.7f, getBubbleTransparentTextColor(manager)) :
      Theme.textDecentColor();
  private final TextColorSet noticeColorSet = () ->
    useBubbles() ? getBubbleDateTextColor() : Theme.textAccentColor();

  private final Drawable footerIcon;
  private Text title, subtitle, footer;
  private boolean footerHasIcon;
  private final List<Text> rowKeys = new ArrayList<>();
  private final List<Text> rowValues = new ArrayList<>();
  private final List<Text> notices = new ArrayList<>();
  private int cardWidth, cardHeight, keyColumnWidth, rowsWidth;

  public TGMessageAccountInfo (MessagesManager context, long chatId, TdApi.AccountInfo info) {
    super(context, TD.newFakeMessage(
      chatId, context.controller().tdlib().sender(chatId),
      new TdApi.MessageText(new TdApi.FormattedText("", null), null, null)
    ));
    this.info = info;
    this.footerIcon = Drawables.get(context.controller().context().getResources(), R.drawable.baseline_error_18);
  }

  public static boolean isEmpty (@Nullable TdApi.AccountInfo info) {
    return info == null || (StringUtils.isEmpty(info.phoneNumberCountryCode) && registrationText(info) == null);
  }

  public void onUserFullUpdated () {
    rebuildAndUpdateContent();
  }

  @Nullable
  private static String registrationText (TdApi.AccountInfo info) {
    boolean hasMonth = info.registrationMonth >= 1 && info.registrationMonth <= 12;
    if (hasMonth && info.registrationYear > 0) {
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(info.registrationYear, info.registrationMonth - 1, 1);
      return Lang.monthYearFull(c.getTimeInMillis(), TimeUnit.MILLISECONDS);
    }
    if (info.registrationYear > 0) {
      return String.valueOf(info.registrationYear);
    }
    if (hasMonth) {
      return Lang.getMonths(Lang.locale())[info.registrationMonth - 1];
    }
    return null;
  }

  private String countryText (String countryCode) {
    if ("FT".equalsIgnoreCase(countryCode)) {
      return Lang.getString(R.string.AccountInfoPhoneAnonymous);
    }
    String flag = Emoji.getEmojiFlagFromCountry(countryCode);
    String name = tdlib.getCountryName(countryCode);
    return StringUtils.isEmpty(flag) ? name : flag + " " + name;
  }

  private Text newText (String text, int maxWidth, boolean bold, boolean secondary) {
    Text.Builder b = new Text.Builder(
      text, maxWidth,
      getServiceTextStyleProvider(useBubbles()),
      secondary ? secondaryColorSet : primaryColorSet
    ).viewProvider(currentViews).singleLine();
    if (bold) {
      b.allBold();
    }
    return b.build();
  }

  private static final float PADDING = 16f;
  private static final float ROW_GAP = 8f;
  private static final float ROW_SPACING = 6f;
  private static final float NOTICE_MARGIN = 14f;
  private static final float FOOTER_ICON_GAP = 6f;

  @Override
  protected void buildContent (int maxWidth) {
    int maxCardWidth = Math.max(0, width - Screen.dp(12f) * 2);
    int maxTextWidth = Math.max(0, maxCardWidth - Screen.dp(PADDING) * 2);
    long userId = tdlib.chatUserId(getChatId());
    TdApi.User user = tdlib.cache().user(userId);
    TdApi.UserFullInfo userFull = tdlib.cache().userFull(userId, false);
    int groupInCommonCount = userFull != null ? userFull.groupInCommonCount : 0;

    title = newText(tdlib.cache().userName(userId), maxTextWidth, true, false);
    subtitle = newText(Lang.getString(user != null && user.isContact ?
      R.string.AccountInfoContact :
      R.string.AccountInfoNotContact
    ), maxTextWidth, false, true);

    List<String> keys = new ArrayList<>(3);
    List<String> values = new ArrayList<>(3);
    if (!StringUtils.isEmpty(info.phoneNumberCountryCode)) {
      keys.add(Lang.getString(R.string.AccountInfoPhone));
      values.add(countryText(info.phoneNumberCountryCode));
    }
    String registration = registrationText(info);
    if (registration != null) {
      keys.add(Lang.getString(R.string.AccountInfoRegistration));
      values.add(registration);
    }
    if (groupInCommonCount > 0) {
      keys.add(Lang.getString(R.string.AccountInfoCommonGroups));
      values.add(Lang.plural(R.string.xGroups, groupInCommonCount));
    }

    rowKeys.clear();
    rowValues.clear();
    keyColumnWidth = 0;
    for (String key : keys) {
      Text text = newText(key, maxTextWidth / 2, false, true);
      keyColumnWidth = Math.max(keyColumnWidth, text.getWidth());
      rowKeys.add(text);
    }
    int valueMaxWidth = Math.max(0, maxTextWidth - keyColumnWidth - Screen.dp(ROW_GAP));
    int valueColumnWidth = 0;
    for (String value : values) {
      Text text = newText(value, valueMaxWidth, true, false);
      valueColumnWidth = Math.max(valueColumnWidth, text.getWidth());
      rowValues.add(text);
    }
    rowsWidth = rowKeys.isEmpty() ? 0 : keyColumnWidth + Screen.dp(ROW_GAP) + valueColumnWidth;

    boolean isVerified = user != null && user.verificationStatus != null && user.verificationStatus.isVerified;
    long botVerificationIconId = user != null && user.verificationStatus != null ? user.verificationStatus.botVerificationIconCustomEmojiId : 0;
    int footerIconWidth = footerIcon.getMinimumWidth() + Screen.dp(FOOTER_ICON_GAP);
    footer = null;
    footerHasIcon = false;
    if (user != null && !isVerified) {
      if (botVerificationIconId != 0) {
        TdApi.BotVerification verification = userFull != null ? userFull.botVerification : null;
        if (verification != null && !Td.isEmpty(verification.customDescription)) {
          FormattedText text = FormattedText.concat(" ",
            FormattedText.customEmoji(tdlib, EmojiStatusHelper.EMOJI, verification.iconCustomEmojiId),
            FormattedText.valueOf(this, verification.customDescription, null)
          );
          footer = new Text.Builder(text, maxTextWidth, getServiceTextStyleProvider(useBubbles()), secondaryColorSet, (text1, specificMedia) -> {
            if (footer == text1) {
              invalidateTextMediaReceiver(text1, specificMedia);
            }
          }).viewProvider(currentViews).maxLineCount(5).textFlags(Text.FLAG_ALIGN_CENTER).build();
        }
      } else {
        footer = newText(Lang.getString(R.string.AccountInfoNotOfficial), Math.max(0, maxTextWidth - footerIconWidth), false, true);
        footerHasIcon = true;
      }
    }
    invalidateTextMediaReceiver();

    int contentWidth = Math.max(Math.max(title.getWidth(), subtitle.getWidth()), rowsWidth);
    if (footer != null) {
      contentWidth = Math.max(contentWidth, (footerHasIcon ? footerIconWidth : 0) + footer.getWidth());
    }
    cardWidth = Math.min(maxCardWidth, contentWidth + Screen.dp(PADDING) * 2);

    cardHeight = Screen.dp(14f) + title.getHeight() + Screen.dp(3f) + subtitle.getHeight();
    if (!rowKeys.isEmpty()) {
      cardHeight += Screen.dp(11f);
      for (int i = 0; i < rowKeys.size(); i++) {
        cardHeight += getRowHeight(i) + (i > 0 ? Screen.dp(ROW_SPACING) : 0);
      }
    }
    if (footer != null) {
      cardHeight += Screen.dp(12f) + getCardFooterHeight();
    }
    cardHeight += Screen.dp(14f);

    notices.clear();
    boolean nameFirst = info.lastPhotoChangeDate == 0 || (info.lastNameChangeDate != 0 && info.lastNameChangeDate <= info.lastPhotoChangeDate);
    if (nameFirst) {
      addNotice(info.lastNameChangeDate, R.string.AccountInfoNameChanged, maxCardWidth);
      addNotice(info.lastPhotoChangeDate, R.string.AccountInfoPhotoChanged, maxCardWidth);
    } else {
      addNotice(info.lastPhotoChangeDate, R.string.AccountInfoPhotoChanged, maxCardWidth);
      addNotice(info.lastNameChangeDate, R.string.AccountInfoNameChanged, maxCardWidth);
    }
  }

  // TODO: schedule refresh using Lang.getNextRelativeDateUpdateMs
  private void addNotice (int date, @StringRes int res, int maxWidth) {
    if (date <= 0) {
      return;
    }
    String text = Lang.getRelativeDate(
      date, TimeUnit.SECONDS,
      tdlib.currentTimeMillis(), TimeUnit.MILLISECONDS,
      true, 60, res, true
    );
    notices.add(new Text.Builder(text, maxWidth, getServiceTextStyleProvider(useBubbles()), noticeColorSet)
      .viewProvider(currentViews)
      .textFlags(Text.FLAG_ALIGN_CENTER)
      .allBold()
      .build()
    );
  }

  private int getCardFooterHeight () {
    return footerHasIcon ? Math.max(footer.getHeight(), footerIcon.getMinimumHeight()) : footer.getHeight();
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (footer != null && !footerHasIcon) {
      footer.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private int getRowHeight (int index) {
    return Math.max(rowKeys.get(index).getHeight(), rowValues.get(index).getHeight());
  }

  @Override
  protected int getContentHeight () {
    int height = cardHeight;
    for (Text notice : notices) {
      height += Screen.dp(NOTICE_MARGIN) + notice.getHeight();
    }
    return notices.isEmpty() ? height : height + Screen.dp(4f);
  }

  @Override
  protected int getContentWidth () {
    return cardWidth;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    if (title == null) {
      return;
    }
    boolean useBubbles = useBubbles();
    int centerX = width / 2;
    int top = getContentY();

    if (useBubbles) {
      RectF rectF = Paints.getRectF();
      rectF.set(centerX - cardWidth / 2f, top, centerX + cardWidth / 2f, top + cardHeight);
      int radius = Screen.dp(12f);
      c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(getBubbleTransparentColor(manager)));
    }

    int y = top + Screen.dp(14f);
    title.draw(c, centerX - title.getWidth() / 2, y);
    y += title.getHeight() + Screen.dp(3f);
    subtitle.draw(c, centerX - subtitle.getWidth() / 2, y);
    y += subtitle.getHeight();

    if (!rowKeys.isEmpty()) {
      y += Screen.dp(11f);
      int keysRight = centerX - rowsWidth / 2 + keyColumnWidth;
      for (int i = 0; i < rowKeys.size(); i++) {
        if (i > 0) {
          y += Screen.dp(ROW_SPACING);
        }
        Text key = rowKeys.get(i);
        key.draw(c, keysRight - key.getWidth(), y);
        rowValues.get(i).draw(c, keysRight + Screen.dp(ROW_GAP), y);
        y += getRowHeight(i);
      }
    }

    if (footer != null) {
      y += Screen.dp(12f);
      if (footerHasIcon) {
        int iconSize = footerIcon.getMinimumWidth();
        int footerHeight = getCardFooterHeight();
        int left = centerX - (iconSize + Screen.dp(FOOTER_ICON_GAP) + footer.getWidth()) / 2;
        Drawables.draw(c, footerIcon,
          left, y + (footerHeight - footerIcon.getMinimumHeight()) / 2f,
          Paints.getPorterDuffPaint(secondaryColorSet.defaultTextColor())
        );
        footer.draw(c, left + iconSize + Screen.dp(FOOTER_ICON_GAP), y + (footerHeight - footer.getHeight()) / 2);
      } else {
        int left = centerX - footer.getWidth() / 2;
        footer.draw(c, left, left + footer.getWidth(), 0, y, null, 1f, view.getTextMediaReceiver());
      }
    }

    y = top + cardHeight;
    int dateRadius = Screen.dp(Theme.getBubbleDateRadius());
    for (Text notice : notices) {
      y += Screen.dp(NOTICE_MARGIN);
      int left = centerX - notice.getWidth() / 2;
      if (useBubbles) {
        RectF rectF = Paints.getRectF();
        rectF.set(
          left - Screen.dp(8f), y - Screen.dp(5f),
          left + notice.getWidth() + Screen.dp(8f), y + notice.getHeight() + Screen.dp(4f)
        );
        c.drawRoundRect(rectF, dateRadius, dateRadius, Paints.fillingPaint(getBubbleDateBackgroundColor()));
      }
      notice.draw(c, left, left + notice.getWidth(), 0, y);
      y += notice.getHeight();
    }
  }

  @Override
  protected boolean headerDisabled () {
    return true;
  }

  @Override
  protected boolean disableBubble () {
    return true;
  }

  @Override
  public boolean canSwipe () {
    return false;
  }

  @Override
  public boolean canBeSelected () {
    return false;
  }

  @Override
  public boolean canMarkAsViewed () {
    return false;
  }

  @Override
  public boolean needRefreshViewCount () {
    return false;
  }

  @Override
  public boolean isFakeMessage () {
    return true;
  }
}
