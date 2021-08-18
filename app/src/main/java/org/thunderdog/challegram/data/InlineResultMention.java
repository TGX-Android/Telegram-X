package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.text.TextUtils;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.inline.CustomResultView;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeId;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;

/**
 * Date: 03/12/2016
 * Author: default
 */

public class InlineResultMention extends InlineResult<UserContext> {
  private static final float TEXT_SIZE_DP = 14f;

  private final UserContext userContext;
  private String description;
  private final boolean isInlineBot;

  public InlineResultMention (BaseActivity context, Tdlib tdlib, TdApi.User user, boolean isInlineBot) {
    super(context, tdlib, TYPE_MENTION, null, new UserContext(tdlib, user));
    this.userContext = data;
    this.isInlineBot = isInlineBot;
    this.description = userContext.getUser() != null && !userContext.getUser().username.isEmpty() ? "@" + userContext.getUser().username : null;
  }

  private static String getMention (TdApi.User user, boolean forceUsernameless) {
    return user != null ? forceUsernameless || user.username.isEmpty() ? (!StringUtils.isEmpty(user.firstName) ? user.firstName : TD.getUserName(user.firstName, user.lastName)) : "@" + user.username : null;
  }

  public boolean isInlineBot () {
    return isInlineBot;
  }

  public TdApi.User getUser () {
    return userContext.getUser();
  }

  public int getUserId () {
    return userContext.getId();
  }

  public boolean isUsernameless () {
    return userContext.getUser() != null && userContext.getUser().username.isEmpty();
  }

  public String getMention (boolean forceUsernameless) {
    return getMention(userContext.getUser(), forceUsernameless);
  }

  public boolean matchesPrefix (String prefix, boolean allowUsernameless) {
    return userContext.getUser() != null && matchesPrefix(userContext.getUser(), prefix, allowUsernameless);
  }

  public static boolean matchesPrefix (TdApi.User user, String prefix, boolean allowUsernameless) {
    String check = prefix.toLowerCase();
    String lowerFirst = user.firstName.toLowerCase();
    String lowerLast = user.lastName.toLowerCase();
    String check2 = TD.getUserName(lowerFirst, lowerLast);
    if (check2.startsWith(check) || lowerLast.startsWith(check)) {
      return allowUsernameless || !StringUtils.isEmpty(user.username);
    }
    return (allowUsernameless || !user.username.isEmpty()) && user.username.toLowerCase().startsWith(check);
  }

  private String trimmedDescription;

  @Override
  protected void layoutInternal (int contentWidth) {
    this.userContext.measureTexts(12f, Paints.getMediumTextPaint(TEXT_SIZE_DP, false));
    int nameWidth = contentWidth - Screen.dp(14f) * 2 /* avatar */ - Screen.dp(14f) * 2 /* horizontal view padding */ - Screen.dp(12f) /* image right padding */;

    if (description != null) {
      trimmedDescription = TextUtils.ellipsize(description, Paints.getCommandPaint(), nameWidth, TextUtils.TruncateAt.END).toString();
      nameWidth -= U.measureText(trimmedDescription, Paints.getMediumTextPaint(TEXT_SIZE_DP, false));
    }

    userContext.trimName(Paints.getMediumTextPaint(TEXT_SIZE_DP, false), nameWidth);
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(4f) * 2 + Screen.dp(14f) * 2;
  }

  @Override
  public void requestContent (ComplexReceiver receiver, boolean isInvalidate) {
    receiver.clearReceivers((receiverType, receiver1, key) -> receiverType == ComplexReceiver.RECEIVER_TYPE_IMAGE && key == 0);
    receiver.getImageReceiver(0).requestFile(userContext.getImageFile());
  }

  @Override
  protected void drawInternal (CustomResultView view, Canvas c, ComplexReceiver receiver, int viewWidth, int viewHeight, int startY) {
    if (userContext.hasPhoto()) {
      ImageReceiver imageReceiver = receiver.getImageReceiver(0);
      imageReceiver.setRadius(Screen.dp(14f));
      imageReceiver.setBounds(Screen.dp(14f), startY + Screen.dp(4f), Screen.dp(14f) + Screen.dp(14f) * 2, startY + Screen.dp(4f) + Screen.dp(14f) * 2);
      if (imageReceiver.needPlaceholder()) {
        imageReceiver.drawPlaceholder(c);
      }
      imageReceiver.draw(c);
    } else {
      userContext.drawPlaceholder(c, Screen.dp(14f), Screen.dp(14f), startY + Screen.dp(4f), 12f);
    }

    int startX = Screen.dp(14f) * 3 + Screen.dp(12f);
    if (userContext.getTrimmedName() != null) {
      final int textColor = forceDarkMode ? Theme.getColor(R.id.theme_color_text, ThemeId.NIGHT_BLACK) : Theme.textAccentColor();
      c.drawText(userContext.getTrimmedName(), startX, startY + Screen.dp(4f) + Screen.dp(14f) + Screen.dp(5f), Paints.getMediumTextPaint(TEXT_SIZE_DP, textColor, false));
      startX += userContext.getTrimmedNameWidth();
      startX += Screen.dp(8f);
    }

    if (trimmedDescription != null) {
      final int textColor = forceDarkMode ? Theme.getColor(R.id.theme_color_textLight, ThemeId.NIGHT_BLACK) : Theme.textDecentColor();
      c.drawText(trimmedDescription, startX, startY + Screen.dp(4f) + Screen.dp(14f) + Screen.dp(5f), Paints.getCommandPaint(textColor));
    }
  }
}
