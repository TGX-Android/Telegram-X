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

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.widget.AttachDelegate;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public class BubbleView implements AttachDelegate, Destroyable {
  private static final int FLAG_HIDING = 0x01;
  private static final int FLAG_SHOWING = 0x02;
  private static final int FLAG_MOVING = 0x04;

  private int flags;

  private final BubbleWrapView context;
  private final Entry entry;
  private final int width, avatarSize, paddingLeft, avatarRadius;

  private final BoolAnimator isDeleting;

  private final AvatarReceiver receiver;
  private int x, y;
  private Text displayName;

  public static class Entry {
    public final Tdlib tdlib;
    public final String id;
    public final @Nullable TdApi.MessageSender senderId;
    public final String name;
    public final @Nullable String shortName;
    private final RunnableData<AvatarReceiver> avatarLoader;

    public Entry (Tdlib tdlib, String id, @Nullable TdApi.MessageSender senderId, String name, @Nullable String shortName, RunnableData<AvatarReceiver> avatarLoader) {
      this.tdlib = tdlib;
      this.id = id;
      this.senderId = senderId;
      this.name = name;
      this.shortName = shortName;
      this.avatarLoader = avatarLoader;
    }

    public static Entry valueOf (Tdlib tdlib, TGUser user) {
      TdApi.MessageSender sender = user.getSenderId();
      return new Entry(
        tdlib, "sender_" + user.getChatId(),
        sender,
        user.getName(),
        user.getShorterName(),
        receiver ->
          receiver.requestMessageSender(tdlib, sender, AvatarReceiver.Options.FORCE_IGNORE_FORUM)
      );
    }

    public static Entry valueOf (Tdlib tdlib, TdApi.MessageSender senderId) {
      final String name = tdlib.senderName(senderId);
      final String shortName;
      TdApi.User user = tdlib.senderUser(senderId);
      if (user != null) {
        shortName = TD.getShorterUserNameOrNull(user.firstName, user.lastName);
      } else {
        shortName = null;
      }
      RunnableData<AvatarReceiver> loader = receiver ->
        receiver.requestMessageSender(tdlib, senderId, AvatarReceiver.Options.FORCE_IGNORE_FORUM);
      return new Entry(
        tdlib, "sender_" + Td.getSenderId(senderId), senderId,
        name, shortName, loader
      );
    }
  }

  public static final float RADIUS = 16f;

  public BubbleView (BubbleWrapView context, Entry entry, int maxTextWidth) {
    this.context = context;
    this.entry = entry;
    this.isDeleting = new BoolAnimator(context, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);

    paddingLeft = Screen.dp(7f);
    int paddingRight = Screen.dp(11f);
    avatarRadius = Screen.dp(RADIUS);
    avatarSize = avatarRadius * 2;

    receiver = new AvatarReceiver(context);

    setName(entry.name, entry.shortName, maxTextWidth);

    width = getNameWidth() + paddingLeft + paddingRight + avatarSize;
  }

  public Entry getEntry () {
    return entry;
  }

  private int getNameWidth () {
    return displayName != null ? displayName.getWidth() : 0;
  }

  private void setName (String name, @Nullable String shorterName, int maxWidth) {
    TextStyleProvider textStyleProvider = Paints.robotoStyleProvider(14f);
    TextColorSet textColorSet = () ->
      Theme.getColor(ColorId.headerText);
    displayName = new Text.Builder(name, maxWidth, textStyleProvider, textColorSet)
      .singleLine()
      .build();

    if (displayName.isEllipsized() && !StringUtils.isEmpty(shorterName)) {
      displayName = new Text.Builder(shorterName, maxWidth, textStyleProvider, textColorSet)
        .singleLine()
        .build();
    }
  }

  public void requestFile () {
    if (entry.avatarLoader != null) {
      entry.avatarLoader.runWithData(receiver);
    } else {
      receiver.clear();
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
      layoutReceiver();
    }
  }

  private void layoutReceiver () {
    if (receiver != null) {
      int cx = x + (int) ((float) diffX * factor);
      int cy = y + (int) ((float) diffY * factor);
      if (Lang.rtl()) {
        cx = context.getMeasuredWidth() - cx - avatarSize;
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
    layoutReceiver();
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
    if (receiver != null && (flags & FLAG_MOVING) != 0) {
      layoutReceiver();
    }
  }

  public float getFactor () {
    return flags != 0 ? factor : 0f;
  }

  public void draw (Canvas c, View parentView) {
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
      cx = parentView.getMeasuredWidth() - cx - width;
    }

    final boolean savedScale = scale != 1f;
    final int scaleSaveCount;
    if (savedScale) {
      scaleSaveCount = Views.save(c);
      float vScale = 1f - (1f - scale) * .65f;
      c.scale(vScale, vScale, cx + (float) width * .5f, cy + avatarRadius);
    } else {
      scaleSaveCount = -1;
    }

    final int headerColor = Theme.headerColor();
    final int headerPlaceholderColor = Theme.headerPlaceholderColor();
    final int headerDeleteColor = Theme.getColor(ColorId.headerRemoveBackground);
    final int headerOverlayColor = ColorUtils.compositeColor(headerColor, headerPlaceholderColor);
    final int headerDeleteHighlightColor = Theme.getColor(ColorId.headerRemoveBackgroundHighlight);
    final float deleteFactor = this.isDeleting.getFloatValue();
    final boolean isDeleting = deleteFactor != 0f;
    final int avatarBackgroundColor = ColorUtils.alphaColor(scale,
      ColorUtils.fromToArgb(
        headerOverlayColor,
        headerDeleteHighlightColor,
        deleteFactor
      )
    );
    final int bubbleBackgroundColor = ColorUtils.alphaColor(
      scale, ColorUtils.fromToArgb(
        headerOverlayColor,
        headerDeleteColor, deleteFactor
      )
    );
    context.paint.setColor(bubbleBackgroundColor);

    RectF rectF = Paints.getRectF();
    rectF.set(cx, cy, cx + width, cy + avatarSize);
    c.drawRoundRect(rectF, avatarRadius, avatarRadius, context.paint);

    if (displayName != null) {
      int startX = Lang.rtl() ? cx + width - avatarSize - paddingLeft - displayName.getWidth() : cx + avatarSize + paddingLeft;
      int startY = cy + avatarSize / 2 - displayName.getLineHeight(false) / 2;
      displayName.draw(c, startX, startY, null, scale);
    }

    int circleX = Lang.rtl() ? cx + width - avatarRadius : cx + avatarRadius;
    layoutReceiver();
    if (receiver.needPlaceholder() || isDeleting) {
      receiver.drawPlaceholderRounded(c, avatarRadius, avatarBackgroundColor);
    }
    int saveCount = isDeleting ? Views.save(c) : -1;
    if (isDeleting) {
      c.rotate(45f * (Lang.rtl() ? 1f : -1f) * deleteFactor, receiver.centerX(), receiver.centerY());
    }
    receiver.setPaintAlpha(isDeleting ? scale * (1f - deleteFactor) : scale);
    receiver.draw(c);
    if (isDeleting) {
      Views.restore(c, saveCount);
    }
    receiver.restorePaintAlpha();

    if (isDeleting) {
      saveCount = Views.save(c);
      c.rotate(90f + 45f * (Lang.rtl() ? 1f : -1f) * deleteFactor, circleX, cy + avatarRadius);

      context.paint.setColor(ColorUtils.color((int) (255f * scale * deleteFactor), 0xffffffff));
      c.drawRect(circleX - context.deleteIconWidth, cy + avatarRadius - context.deleteIconStroke, circleX + context.deleteIconWidth, cy + avatarRadius + context.deleteIconStroke, context.paint);
      c.drawRect(circleX - context.deleteIconStroke, cy + avatarRadius - context.deleteIconWidth, circleX + context.deleteIconStroke, cy + avatarRadius + context.deleteIconWidth, context.paint);

      Views.restore(c, saveCount);
    }

    if (savedScale) {
      Views.restore(c, scaleSaveCount);
    }
  }

  public void setIsDeleting (boolean isDeleting, boolean animated) {
    this.isDeleting.setValue(isDeleting, animated);
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
  }

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }
}