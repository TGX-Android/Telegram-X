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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;

import androidx.annotation.DrawableRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.widget.FileProgressComponent;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TGMessageCall extends TGMessage {
  private final TdApi.MessageCall callRaw;

  public TGMessageCall (MessagesManager context, TdApi.Message msg, TdApi.MessageCall call) {
    super(context, msg);
    this.callRaw = call;
  }

  // private Drawable phoneIcon, callIcon;
  private @DrawableRes int callIconId;
  private @ThemeColorId
  int callIconColorId;
  // private String title, subtitle;

  private String trimmedTitle, trimmedSubtitle;
  private boolean needFakeTitle;
  private float titleWidth, subtitleWidth;

  @Override
  protected void buildContent (int maxWidth) {
    this.callIconId = CallItem.getSubtitleIcon(callRaw, isOutgoing());
    this.callIconColorId = CallItem.getSubtitleIconColorId(callRaw);
    boolean isFullSubtitle = useBubbles() || callRaw.duration > 0;
    String title = Lang.getString(isFullSubtitle ? TD.getCallName(callRaw, isOutgoing(), true) : isOutgoing() ? R.string.OutgoingCall : R.string.IncomingCall);
    String subtitle = CallItem.getSubtitle(msg, isFullSubtitle, 1);
    if (useBubbles()) {
      StringBuilder b = new StringBuilder();
      b.append(Lang.time(msg.date, TimeUnit.SECONDS));
      if (!StringUtils.isEmpty(subtitle)) {
        b.append(", ");
        b.append(subtitle);
      }
      subtitle = b.toString();
    } else {
      maxWidth -= Screen.dp(40f) + Screen.dp(11f);
    }
    this.needFakeTitle = Text.needFakeBold(title);
    this.trimmedTitle = TextUtils.ellipsize(title, Paints.getMediumTextPaint(15f, needFakeTitle), maxWidth, TextUtils.TruncateAt.END).toString();
    this.trimmedSubtitle = TextUtils.ellipsize(subtitle, Paints.getSubtitlePaint(), maxWidth - Screen.dp(20f), TextUtils.TruncateAt.END).toString();
    this.titleWidth = U.measureText(trimmedTitle, Paints.getMediumTextPaint(13f, needFakeTitle));
    this.subtitleWidth = U.measureText(trimmedSubtitle, Paints.getSubtitlePaint());
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    Drawable phoneIcon = view.getSparseDrawable(callRaw.isVideo ? R.drawable.baseline_videocam_24 : R.drawable.baseline_phone_24, 0);
    Drawable callIcon = view.getSparseDrawable(callIconId, 0);
    if (useBubbles()) {
      int colorId = isOutgoingBubble() ? R.id.theme_color_bubbleOut_file : R.id.theme_color_file;
      Drawables.draw(c, phoneIcon, startX + getContentWidth() - getContentHeight() / 2f - phoneIcon.getMinimumWidth() / 2f, startY + getContentHeight() / 2f - phoneIcon.getMinimumHeight() / 2f, PorterDuffPaint.get(colorId));
    } else {
      int radius = Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS);
      c.drawCircle(startX + radius, startY + radius, radius, Paints.fillingPaint(Theme.getColor(R.id.theme_color_file)));
      Drawables.draw(c, phoneIcon, startX + radius - phoneIcon.getMinimumWidth() / 2f, startY + radius - phoneIcon.getMinimumHeight() / 2f, Paints.getPorterDuffPaint(0xffffffff));
      startX += radius * 2 + Screen.dp(11f);
    }
    if (useBubbles()) {
      startY -= Screen.dp(4f);
    }
    c.drawText(trimmedTitle, startX, startY + Screen.dp(21f), Paints.getMediumTextPaint(15f, getTextColor(), needFakeTitle));
    Drawables.draw(c, callIcon, startX, startY + Screen.dp(callIconId == R.drawable.baseline_call_missed_18 ? 27.5f : callIconId == R.drawable.baseline_call_made_18 ? 26.5f : 27f), Paints.getPorterDuffPaint(Theme.getColor(callIconColorId)));
    c.drawText(trimmedSubtitle, startX + Screen.dp(20f), startY + Screen.dp(41f), Paints.getRegularTextPaint(13f, getDecentColor()));
  }

  private boolean caught;
  private float startX, startY;

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e)) {
      return true;
    }

    float x = e.getX();
    float y = e.getY();

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        caught = x >= getContentX() && x <= getContentX() + getContentWidth() && y >= getContentY() && y <= getContentY() + getContentHeight();
        startX = x;
        startY = y;
        return caught;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (caught) {
          caught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (caught && Math.abs(x - startX) > Screen.getTouchSlop() && Math.abs(y - startY) > Screen.getTouchSlop()) {
          caught = false;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (caught) {
          caught = true;
          long userId;
          if (isOutgoing()) {
            userId = ChatId.toUserId(msg.chatId);
          } else {
            userId = Td.getSenderUserId(msg);
          }
          if (userId == 0) {
            return false;
          }
          performClickSoundFeedback();
          tdlib.context().calls().makeCall(controller(), userId, null);
          return true;
        }
        break;
      }
    }

    return caught;
  }

  @Override
  protected int getContentWidth () {
    return (int) Math.max(Math.max(titleWidth, subtitleWidth + Screen.dp(20f)), useBubbles() ? Screen.dp(182f) : 0) + Screen.dp(40f) + Screen.dp(11f);
  }

  @Override
  protected int getContentHeight () {
    return useBubbles() ? Screen.dp(46f) : Screen.dp(FileProgressComponent.DEFAULT_FILE_RADIUS) * 2;
  }
}
