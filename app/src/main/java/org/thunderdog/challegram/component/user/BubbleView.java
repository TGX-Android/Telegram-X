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
 * File created on 08/02/2016 at 08:09
 */
package org.thunderdog.challegram.component.user;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.td.Td;

public class BubbleView {
  private static final int FLAG_HIDING = 0x01;
  private static final int FLAG_SHOWING = 0x02;
  private static final int FLAG_MOVING = 0x04;
  private static final int FLAG_DELETING = 0x08;

  private final Tdlib tdlib;
  private int flags;

  private final TextPaint paint;

  private final TdApi.MessageSender senderId;
  private final TdApi.User user;
  private final TdApi.Chat chat;

  private int width, avatarSize;
  private int x, y;
  private int paddingLeft;
  private int avatarRadius;
  private int textOffset;

  private String name;
  private int nameWidth;

  public BubbleView (Tdlib tdlib, TextPaint paint, TdApi.MessageSender senderId, int maxTextWidth) {
    this.tdlib = tdlib;
    this.paint = paint;
    this.senderId = senderId;
    this.user = tdlib.chatUser(Td.getSenderId(senderId));
    this.chat = tdlib.chat(Td.getSenderId(senderId));

    paddingLeft = Screen.dp(7f);
    int paddingRight = Screen.dp(11f);
    avatarRadius = Screen.dp(16f);
    textOffset = Screen.dp(21f);
    avatarSize = avatarRadius * 2;

    name = user != null ? TD.getUserName(user) : (chat != null ? tdlib.chatTitle(chat) : null);
    buildName(maxTextWidth);

    width = nameWidth + paddingLeft + paddingRight + avatarSize;
  }

  private boolean shortNameAttempt;

  private void buildName (int maxWidth) {
    nameWidth = (int) U.measureText(name, paint);
    if (nameWidth > maxWidth) {
      if (!shortNameAttempt && user != null) {
        String firstName = user.firstName;
        String lastName = user.lastName;
        if (firstName.length() > 0 && lastName.length() > 0) {
          shortNameAttempt = true;
          name = firstName.charAt(0) + ". " + lastName;
          buildName(maxWidth);
        }
      }
      name = (String) TextUtils.ellipsize(name, paint, maxWidth, TextUtils.TruncateAt.END);
      nameWidth = (int) U.measureText(name, paint);
    }
  }

  public void requestFile (ComplexReceiver complexReceiver) {
    if (complexReceiver != null) {
      complexReceiver.getAvatarReceiver(Td.getSenderId(senderId)).requestMessageSender(tdlib, senderId, AvatarReceiver.Options.NONE);
    }
  }

  public int getWidth () {
    return width;
  }

  public int getHeight () {
    return avatarSize;
  }

  public int getX () {
    return (flags & FLAG_MOVING) != 0 ? toY : x;
  }

  public int getY () {
    return (flags & FLAG_MOVING) != 0 ? toY : y;
  }

  public TdApi.MessageSender getSenderId () {
    return senderId;
  }

  public long getChatId () {
    return Td.getSenderId(senderId);
  }

  private int toX, diffX, toY, diffY;

  public void setXY (int x, int y) {
    if ((flags & FLAG_MOVING) != 0) {
      this.toX = x;
      this.diffX = x - this.x;
      this.toY = y;
      this.diffY = y - this.y;
    } else {
      this.x = x;
      this.y = y;
      //layoutReceiver();
    }
  }

  private void layoutReceiver (Receiver receiver, int parentWidth) {
    if (receiver != null) {
      int cx = x + (int) ((float) diffX * factor);
      int cy = y + (int) ((float) diffY * factor);
      if (Lang.rtl()) {
        cx = parentWidth - cx - avatarSize;
      }
      receiver.setBounds(cx, cy, cx + avatarSize, cy + avatarSize);
    }
  }

  public void prepareMove () {
    flags |= FLAG_MOVING;
    factor = 0f;
  }

  public void completeMove () {
    flags &= ~FLAG_MOVING;
    x = toX;
    y = toY;
    diffX = 0;
    diffY = 0;
    factor = 0f;
    //layoutReceiver();
  }

  public void prepareShow () {
    flags |= FLAG_SHOWING;
  }

  public void completeShow () {
    flags &= ~FLAG_SHOWING;
  }

  public void prepareHide () {
    flags |= FLAG_HIDING;
    factor = 0f;
  }

  public boolean isHiding () {
    return (flags & FLAG_HIDING) != 0;
  }

  private float factor;

  public void setFactor (float factor) {
    this.factor = factor;
    /*if (receiver != null && (flags & FLAG_MOVING) != 0) {
      layoutReceiver();
    }*/
  }

  public float getFactor () {
    return flags != 0 ? factor : 0f;
  }

  public void draw (Canvas c, ComplexReceiver complexReceiver, int parentWidth) {
    int cx, cy;
    float scale;



    if ((flags & FLAG_MOVING) != 0) {
      cx = x + (int) ((float) diffX * factor);
      cy = y + (int) ((float) diffY * factor);
      scale = 1f;
    } else {
      if ((flags & FLAG_SHOWING) != 0) {
        scale = factor;
      } else if ((flags & FLAG_HIDING) != 0) {
        scale = 1f - factor;
      } else {
        scale = 1f;
      }
      cx = x;
      cy = y;
    }

    if (Lang.rtl()) {
      cx = parentWidth - cx - width;
    }

    final boolean savedScale = scale != 1f;
    if (savedScale) {
      c.save();
      float vScale = 1f - (1f - scale) * .65f;
      c.scale(vScale, vScale, cx + (float) width * .5f, cy + avatarRadius);
    }

    // int alpha = (int) (255f * scale);
    boolean deleting = (deleteFactor != 0f && (flags & FLAG_DELETING) != 0);

    // paint.setColor(deleting ? changer.getColor(deleteFactor) : TGTheme.headerPlaceholderColor());
    paint.setColor(ColorUtils.alphaColor(scale, ColorUtils.fromToArgb(ColorUtils.compositeColor(Theme.headerColor(), Theme.headerPlaceholderColor()), Theme.getColor(ColorId.headerRemoveBackground), deleting ? deleteFactor : 0f)));
    // paint.setAlpha(alpha);

    RectF rectF = Paints.getRectF();
    rectF.set(cx, cy, cx + width, cy + avatarSize);
    c.drawRoundRect(rectF, avatarRadius, avatarRadius, paint);
    //c.drawRect(cx + avatarRadius, cy, cx + width - avatarRadius, cy + avatarSize, paint);
    //c.drawCircle(cx + width - avatarRadius, cy + avatarRadius, avatarRadius, paint);

    paint.setColor(ColorUtils.color((int) (255f * scale), 0xffffffff));
    if (name != null) {
      c.drawText(name, Lang.rtl() ? cx + width - avatarSize - paddingLeft - nameWidth : cx + avatarSize + paddingLeft, cy + textOffset, paint);
    }

    int circleX = Lang.rtl() ? cx + width - avatarRadius : cx + avatarRadius;

    AvatarReceiver receiver = complexReceiver.getAvatarReceiver(Td.getSenderId(senderId));
    layoutReceiver(receiver, parentWidth);
    if (receiver.needPlaceholder()) {
      paint.setColor(ColorUtils.alphaColor(scale, ColorUtils.fromToArgb(ColorUtils.compositeColor(Theme.headerColor(), Theme.headerPlaceholderColor()), Theme.getColor(ColorId.headerRemoveBackgroundHighlight), deleting ? deleteFactor : 0f)));
      c.drawCircle(receiver.centerX(), receiver.centerY(), avatarRadius, paint);
    } else if (deleting) {
      paint.setColor(ColorUtils.alphaColor(scale, Theme.getColor(ColorId.headerRemoveBackgroundHighlight)));
      c.drawCircle(receiver.centerX(), receiver.centerY(), avatarRadius, paint);
    }
    receiver.setPaintAlpha(deleting ? scale * (1f - deleteFactor) : scale);
    if (deleting) {
      c.save();
      c.rotate(45f * (Lang.rtl() ? 1f : -1f) * deleteFactor, receiver.centerX(), receiver.centerY());
    }
    receiver.draw(c);
    if (deleting) {
      c.restore();
    }
    receiver.restorePaintAlpha();

    if (deleting) {
      final int deleteIconStroke = Screen.dp(1f);
      final int deleteIconWidth = Screen.dp(7f);

      c.save();
      c.rotate(90f + 45f * (Lang.rtl() ? 1f : -1f) * deleteFactor, circleX, cy + avatarRadius);

      paint.setColor(ColorUtils.color((int) (255f * scale * deleteFactor), 0xffffffff));
      c.drawRect(circleX - deleteIconWidth, cy + avatarRadius - deleteIconStroke, circleX + deleteIconWidth, cy + avatarRadius + deleteIconStroke, paint);
      c.drawRect(circleX - deleteIconStroke, cy + avatarRadius - deleteIconWidth, circleX + deleteIconStroke, cy + avatarRadius + deleteIconWidth, paint);

      c.restore();
    }

    if (savedScale) {
      c.restore();
    }
  }

  // Deletion

  private float deleteFactor;

  public void setDeleteFactor (float deleteFactor) {
    if (this.deleteFactor != deleteFactor) {
      this.deleteFactor = deleteFactor;
      if (invalidateCallback != null) {
        invalidateCallback.run();
      }
    }
  }

  public float getDeleteFactor () {
    return deleteFactor;
  }

  public void startDeletion () {
    flags |= FLAG_DELETING;

    final float startFactor = getDeleteFactor();
    final float diffFactor = 1f - startFactor;
    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setDeleteFactor(startFactor + diffFactor * AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.setDuration(120l);
    obj.start();
  }

  public void cancelDeletion () {
    final float startFactor = getDeleteFactor();
    ValueAnimator obj;
    obj = AnimatorUtils.simpleValueAnimator();
    obj.addUpdateListener(animation -> setDeleteFactor(startFactor - startFactor * AnimatorUtils.getFraction(animation)));
    obj.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
    obj.setDuration(120l);
    obj.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd (Animator animation) {
        flags &= ~FLAG_DELETING;
      }
    });
    obj.start();
  }

  private Runnable invalidateCallback;

  public void setInvalidateCallback (Runnable invalidateCallback) {
    this.invalidateCallback = invalidateCallback;
  }
}
