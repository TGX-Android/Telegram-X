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
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.lambda.Destroyable;

public class TripleAvatarView extends View implements Destroyable {
  public static final int AVATAR_SIZE = 24;
  public static final int AVATAR_PADDING = 2;
  private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  private final ImageReceiver[] receivers = new ImageReceiver[3];
  private final Rect measureRect = new Rect();

  private final AvatarPlaceholder[] placeholders = new AvatarPlaceholder[3];
  private int ignoranceFlags;

  public TripleAvatarView (Context context) {
    super(context);
    setWillNotDraw(false);
    clearPaint.setColor(0);
    clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    for (int i = 0; i < receivers.length; i++) {
      receivers[i] = createReceiver();
    }
  }

  private ImageReceiver createReceiver () {
    ImageReceiver receiver = new ImageReceiver(this, 1);
    receiver.setRadius(0);
    receiver.attach();
    return receiver;
  }

  public void setUsers (Tdlib tdlib, TdApi.MessageViewers viewers) {
    long[] ids = new long[viewers.viewers.length];
    for (int i = 0; i < viewers.viewers.length; i++) {
      TdApi.MessageViewer viewer = viewers.viewers[i];
      ids[i] = viewer.userId;
    }

    for (int i = 0; i < receivers.length; i++) {
      requestUserFile(ids, i, tdlib, receivers[i]);
    }

    syncReceivers(ids);
  }

  private void syncReceivers (long[] users) {
    if (users.length == 1) {
      placeholders[2] = placeholders[0];
      receivers[2].requestFile(receivers[0].getCurrentFile());
      ignoranceFlags = 1;
    } else if (users.length == 2) {
      placeholders[2] = placeholders[1];
      placeholders[1] = placeholders[0];
      receivers[2].requestFile(receivers[1].getCurrentFile());
      receivers[1].requestFile(receivers[0].getCurrentFile());
      ignoranceFlags = 2;
    } else if (users.length == 0) {
      ignoranceFlags = 3;
    } else {
      ignoranceFlags = 0;
    }

    invalidate();
  }

  private void requestUserFile (long[] users, int index, Tdlib tdlib, ImageReceiver receiver) {
    if (users.length > index) {
      TdApi.User user = tdlib.chatUser(users[index]);

      if (user == null || TD.isPhotoEmpty(user.profilePhoto)) {
        placeholders[index] = new AvatarPlaceholder(AVATAR_SIZE / 2f, new AvatarPlaceholder.Metadata(TD.getAvatarColorId(user, tdlib.myUserId()), TD.getLetters(user)), null);
        receiver.requestFile(null);
      } else {
        placeholders[index] = null;
        ImageFile chatAvatar = new ImageFile(tdlib, user.profilePhoto.small);
        chatAvatar.setSize(ChatView.getDefaultAvatarCacheSize());
        receiver.requestFile(chatAvatar);
      }
    } else {
      placeholders[index] = null;
      receiver.requestFile(null);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    final int left = getPaddingLeft();
    int top = getPaddingTop();
    final int right = getMeasuredWidth() - getPaddingRight();
    int bottom = getMeasuredHeight() - getPaddingBottom();
    final int sizeDp = Screen.dp(AVATAR_SIZE);
    final float halfSizeDp = sizeDp / 2f;
    final float evenHalfSizeDp = sizeDp / 4f;

    measureRect.set(left, top, right, bottom);
    top = (int) (measureRect.centerY() - halfSizeDp);
    bottom = (int) (measureRect.centerY() + halfSizeDp);
    setReceiverBounds(receivers[0], (int) (measureRect.centerX() - (halfSizeDp * 3) + evenHalfSizeDp), (int) (measureRect.centerX() - (halfSizeDp) + evenHalfSizeDp), top, bottom);
    setReceiverBounds(receivers[1], (int) (measureRect.centerX() - (halfSizeDp)), (int) (measureRect.centerX() + (halfSizeDp)), top, bottom);
    setReceiverBounds(receivers[2], (int) (measureRect.centerX() + (halfSizeDp) - evenHalfSizeDp), (int) (measureRect.centerX() + (halfSizeDp * 3) - evenHalfSizeDp), top, bottom);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
    for (int i = (receivers.length - 1); i >= 0; i--) {
      drawReceiver(canvas, i, receivers[i]);
    }
    canvas.restore();
  }

  private void setReceiverBounds (ImageReceiver receiver, int left, int right, int top, int bottom) {
    receiver.setBounds(left, top, right, bottom);
    receiver.setRadius(Math.min(receiver.getWidth(), receiver.getHeight()) / 2);
  }

  private void drawReceiver (Canvas c, int index, ImageReceiver receiver) {
    if ((index == 0 && ignoranceFlags == 2) || (index != 2 && ignoranceFlags == 1) || ignoranceFlags == 3)
      return;
    drawPlaceholder(c, receiver);
    if (placeholders[index] != null) {
      placeholders[index].draw(c, receiver.centerX(), receiver.centerY());
    }
    receiver.draw(c);
  }

  private void drawPlaceholder (Canvas c, ImageReceiver receiver) {
    c.drawCircle(receiver.centerX(), receiver.centerY(), receiver.getRadius() + Screen.dp(AVATAR_PADDING), clearPaint);
    c.drawCircle(receiver.centerX(), receiver.centerY(), receiver.getRadius(), Paints.fillingPaint(Theme.placeholderColor()));
  }

  @Override
  public void performDestroy () {
    for (ImageReceiver receiver : receivers) {
      receiver.detach();
    }
  }
}