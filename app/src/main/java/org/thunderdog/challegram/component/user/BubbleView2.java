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
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibSender;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.td.Td;

public class BubbleView2 {
  private final Tdlib tdlib;
  private final SelectionRippleDrawable rippleDrawable;

  private final TextPaint paint;

  private final TdlibSender tdlibSender;
  private final TdApi.MessageSender senderId;
  private final TdApi.User user;

  private final int width;
  private final int avatarSize;
  private int x, y;
  private final int paddingLeft;
  private final int avatarRadius;
  private final int textOffset;

  private String name;
  private int nameWidth;

  public BubbleView2 (ViewProvider viewProvider, Tdlib tdlib, TextPaint paint, TdApi.MessageSender senderId, int maxTextWidth) {
    this.rippleDrawable = new SelectionRippleDrawable(viewProvider);
    this.tdlib = tdlib;
    this.paint = paint;
    this.senderId = senderId;
    this.tdlibSender = new TdlibSender(tdlib, 0, senderId);
    this.user = tdlib.chatUser(Td.getSenderId(senderId));
    TdApi.Chat chat = tdlib.chat(Td.getSenderId(senderId));

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
    return x;
  }

  public int getY () {
    return y;
  }

  public TdApi.MessageSender getSenderId () {
    return senderId;
  }

  public long getChatId () {
    return Td.getSenderId(senderId);
  }

  public void setXY (int x, int y) {
    this.x = x;
    this.y = y;
  }

  private void layoutReceiver (Receiver receiver, int parentWidth, int xOffset, int yOffset) {
    if (receiver != null) {
      int cx = x + xOffset;
      int cy = y + yOffset;
      if (Lang.rtl()) {
        cx = parentWidth - cx - avatarSize;
      }
      receiver.setBounds(cx, cy, cx + avatarSize, cy + avatarSize);
    }
  }

  public void draw (Canvas c, ComplexReceiver complexReceiver, int parentWidth, int xOffset, int yOffset) {
    int cx = x + xOffset;
    int cy = y + yOffset;

    if (Lang.rtl()) {
      cx = parentWidth - cx - width;
    }

    final int color = tdlibSender.getAccentColor().getNameColor();

    paint.setColor(ColorUtils.alphaColor(0.1f, color));

    RectF rectF = Paints.getRectF();
    rectF.set(cx, cy, cx + width, cy + avatarSize);
    c.drawRoundRect(rectF, avatarRadius, avatarRadius, paint);

    rippleDrawable.draw(c, rectF, avatarRadius, color);

    paint.setColor(color);
    if (name != null) {
      c.drawText(name, Lang.rtl() ? cx + width - avatarSize - paddingLeft - nameWidth : cx + avatarSize + paddingLeft, cy + textOffset, paint);
    }

    AvatarReceiver receiver = complexReceiver.getAvatarReceiver(Td.getSenderId(senderId));
    layoutReceiver(receiver, parentWidth, xOffset, yOffset);
    if (receiver.needPlaceholder()) {
      receiver.drawPlaceholder(c);
    }
    receiver.setPaintAlpha(1f);
    receiver.draw(c);
    receiver.restorePaintAlpha();
  }




  public boolean performLongPress (View view) {
    return rippleDrawable.performLongPress(view);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return rippleDrawable.onTouchEvent(view, e);
  }

  public void setOnClickListener (View.OnClickListener onClickListener) {
    rippleDrawable.setOnClickListener(onClickListener);
  }



  public static class SelectionRippleDrawable implements FactorAnimator.Target {
    private final ViewProvider viewProvider;
    private final RectF lastDrawBounds;
    private final Path path;
    private View.OnClickListener onClickListener;

    public SelectionRippleDrawable (ViewProvider viewProvider) {
      this.viewProvider = viewProvider;
      this.lastDrawBounds = new RectF();
      this.path = new Path();
    }

    public void setOnClickListener (View.OnClickListener onClickListener) {
      this.onClickListener = onClickListener;
    }

    private void draw (Canvas c, RectF bounds, int radius, int selectionRippleColor) {
      if (!lastDrawBounds.equals(bounds)) {
        lastDrawBounds.set(bounds);
        path.reset();
        path.addRoundRect(bounds, radius, radius, Path.Direction.CCW);
        path.close();
      }

      final int selectionColor = ColorUtils.alphaColor(0.15f * selectionFactor * (1f - fadeFactor), selectionRippleColor);
      if (selectionFactor != 0f) {
        if (selectionFactor == 1f) {
          c.drawRoundRect(bounds, radius, radius, Paints.fillingPaint(selectionColor));
        } else {
          float selectionRadius = (float) Math.sqrt(bounds.width() * bounds.width() + bounds.height() * bounds.height()) * .5f * selectionFactor;
          float anchorX = MathUtils.clamp(this.anchorX, bounds.left, bounds.right);
          float anchorY = MathUtils.clamp(this.anchorY, bounds.top, bounds.bottom);
          float selectionX = MathUtils.fromTo(anchorX, bounds.centerX(), selectionFactor);
          float selectionY = MathUtils.fromTo(anchorY, bounds.centerY(), selectionFactor);

          final int saveCount;
          if ((saveCount = ViewSupport.clipPath(c, path)) != Integer.MIN_VALUE) {
            c.drawCircle(selectionX, selectionY, selectionRadius, Paints.fillingPaint(selectionColor));
          } else {
            c.drawRoundRect(bounds, radius, radius, Paints.fillingPaint(selectionColor));
          }
          ViewSupport.restoreClipPath(c, saveCount);
        }
      }
    }



    /* * */

    private static final int FLAG_ACTIVE = 0x01;
    private static final int FLAG_CAUGHT = 0x02;
    private static final int FLAG_BLOCKED = 0x04;

    private int flags;
    private int anchorX, anchorY;

    private static final long ANIMATION_DURATION = 180L;

    private static final int SELECTION_ANIMATOR = 0;
    private static final int FADE_ANIMATOR = 2;

    private float selectionFactor;
    private FactorAnimator selectionAnimator;

    private void animateSelectionFactor (float toFactor) {
      if (selectionAnimator == null) {
        selectionAnimator = new FactorAnimator(SELECTION_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        selectionAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      selectionAnimator.animateTo(toFactor);
    }

    private float fadeFactor;
    private FactorAnimator fadeAnimator;

    private void animateFadeFactor (float toFactor) {
      if (toFactor == 1f) {
        flags &= ~FLAG_ACTIVE;
      }
      if (fadeAnimator == null) {
        fadeAnimator = new FactorAnimator(FADE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, ANIMATION_DURATION);
        fadeAnimator.setIsBlocked(viewProvider == null || !viewProvider.hasAnyTargetToInvalidate());
      }
      flags |= FLAG_BLOCKED;
      fadeAnimator.animateTo(toFactor);
    }

    private void forceResetSelection () {
      if (fadeAnimator != null) {
        fadeAnimator.forceFactor(this.fadeFactor = 0f);
        flags &= ~FLAG_BLOCKED;
      }
      if (selectionAnimator != null) {
        selectionAnimator.forceFactor(this.selectionFactor = 0f);
      }
      /*if (activeAnimator != null) {
        activeAnimator.forceFactor(this.activeFactor = 0f);
        flags &= ~FLAG_ACTIVE;
      }*/
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      switch (id) {
        case SELECTION_ANIMATOR: {
          this.selectionFactor = factor;
          break;
        }
        case FADE_ANIMATOR: {
          this.fadeFactor = factor;
          break;
        }
      }
      invalidate();
    }

    @Override
    public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
      if (id == FADE_ANIMATOR) {
        if (finalFactor == 1f) {
          forceResetSelection();
        }
      }
    }

    private void invalidate () {
      if (viewProvider != null) {
        viewProvider.invalidate();
      }
    }

    private boolean isActive () {
      return (flags & FLAG_ACTIVE) != 0;
    }

    private boolean isCaught () {
      return (flags & FLAG_CAUGHT) != 0;
    }

    private boolean isBlocked () {
      return (flags & FLAG_BLOCKED) != 0;
    }

    private void cancelSelection () {
      animateFadeFactor(1f);
    }

    public boolean onTouchEvent (View view, MotionEvent e) {
      final int x = (int) e.getX();
      final int y = (int) e.getY();

      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          flags |= FLAG_CAUGHT;
          anchorX = x; anchorY = y;
          if (!isActive() && !isBlocked()) {
            animateSelectionFactor(1f);
          }
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          anchorX = x; anchorY = y;
          return true;
        }
        case MotionEvent.ACTION_CANCEL: {
          if (isCaught()) {
            flags &= ~FLAG_CAUGHT;
            if (!isActive() && !isBlocked()) {
              cancelSelection();
            }
          }
          return true;
        }
        case MotionEvent.ACTION_UP: {
          anchorX = x; anchorY = y;
          if (isCaught()) {
            flags &= ~FLAG_CAUGHT;
            ViewUtils.onClick(view);
            performClick(view);
            return true;
          }
          return false;
        }
      }

      return true;
    }

    public void performClick (View view) {
      if (!isBlocked()) {
        performAction(view, true);
        cancelSelection();
      }
    }

    private void performAction (View view, boolean needVerify) {
      if (isActive()) {
        return;
      }

      if (onClickListener != null) {
        onClickListener.onClick(view);
      }
    }

    public boolean performLongPress (View view) {
      if ((flags & FLAG_CAUGHT) != 0) {
        flags &= ~FLAG_CAUGHT;
        if (!isActive()) {
          cancelSelection();
        }
      }
      return false;
    }
  }
}
