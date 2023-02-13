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
 * File created on 09/05/2015 at 13:01
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class DrawerItemView extends BaseView implements FactorAnimator.Target, AttachDelegate, Destroyable, TGLegacyManager.EmojiLoadListener {
  private int iconLeft, iconTop;
  private @Nullable Drawable icon;

  private String text;
  private Text trimmedText;
  private int textLeft;

  private float checkFactor;
  private BoolAnimator checkAnimator;
  private AvatarReceiver receiver;
  private Counter counter;

  public DrawerItemView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidate();
  }

  private boolean isChecked () {
    return (checkAnimator != null && checkAnimator.getValue());
  }

  public boolean hasAvatar () {
    return receiver != null;
  }

  public void setChecked (boolean isChecked, boolean animated) {
    if (isChecked != isChecked()) {
      if (checkAnimator == null) {
        checkAnimator = new BoolAnimator(ANIMATOR_CHECK, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      checkAnimator.setValue(isChecked, animated);
    }
  }

  private static final int ANIMATOR_CHECK = 0;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_CHECK: {
        if (checkFactor != factor) {
          this.checkFactor = factor;
          invalidate();
        }
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public void setItemHeight (int height) {
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
  }

  public void setIcon (int left, int top, @DrawableRes int rawResource) {
    iconLeft = left;
    iconTop = top;
    icon = rawResource == 0 ? null : Drawables.get(getResources(), rawResource);
  }

  /*public void setIcon (int left, @DrawableRes int rawResource) {
    iconLeft = left;
    icon = rawResource == 0 ? null : BitmapFactory.decodeResource(getResources(), rawResource);
    iconTop = icon == null ? (int) ((float) getLayoutParams().height * .5f) : (int) ((float) getLayoutParams().height * .5f - (float) icon.getHeight() * .5f);
  }*/

  public void setText (String text) {
    if (this.text == null || !StringUtils.equalsOrBothEmpty(this.text, text)) {
      this.textLeft = Screen.dp(72f);
      this.text = text;
      trimText(true);
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    trimText(false);
    layoutReceiver();
  }

  private float lastAvailTextWidth;

  private void trimText (boolean force) {
    if (text == null || StringUtils.isEmpty(text)) {
      trimmedText = null;
      return;
    }
    int availWidth = getMeasuredWidth() - textLeft;
    if (counter != null) {
      availWidth -= counter.getScaledWidth(Screen.dp(24f) + Screen.dp(8f));
    }
    if (availWidth <= 0) {
      trimmedText = null;
      return;
    }
    if (force || lastAvailTextWidth != availWidth) {
      lastAvailTextWidth = availWidth;
      trimmedText = new Text.Builder(text, availWidth, Paints.robotoStyleProvider(15), TextColorSets.Regular.NORMAL).singleLine().allBold().build();
    }
  }

  private static final float AVATAR_SIZE = 40f;

  public void addAvatar () {
    receiver = new AvatarReceiver(this);
    layoutReceiver();
    counter = new Counter.Builder().callback(this).build();
  }

  private void layoutReceiver () {
    if (receiver == null)
      return;
    int left = Screen.dp(12f);
    int top = Screen.dp(6f);
    int size = Screen.dp(AVATAR_SIZE);
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      receiver.setBounds(viewWidth - left - size, top, viewWidth - left, top + size);
    } else {
      receiver.setBounds(left, top, left + size, top + size);
    }
  }

  public void setAvatar (Tdlib tdlib, long chatId) {
    this.receiver.requestChat(tdlib, chatId, AvatarReceiver.Options.NONE);
  }

  public void setAvatar (TdlibAccount account) {
    AvatarPlaceholder.Metadata placeholder = account.getAvatarPlaceholderMetadata();
    ImageFile imageFile = account.getAvatarFile(false);
    if (imageFile != null) {
      receiver.requestSpecific(tdlib, imageFile, AvatarReceiver.Options.NONE);
    } else {
      receiver.requestPlaceholder(tdlib, placeholder, AvatarReceiver.Options.NONE);
    }
  }

  public void setError (boolean error, int errorIcon, boolean animated) {
    if (errorIcon != 0) {
      setErrorIcon(errorIcon, animated);
    } else if (error) {
      setUnreadCount(Tdlib.CHAT_FAILED, counter != null && counter.isMuted(), animated);
    } else if (iconDrawableRes != 0) {
      setErrorIcon(0, animated);
    } else {
      setUnreadCount(0, false, animated);
    }
  }

  private @DrawableRes int iconDrawableRes;

  public void setErrorIcon (@DrawableRes int drawableRes, boolean animated) {
    if (this.iconDrawableRes != drawableRes) {
      this.iconDrawableRes = drawableRes;
      if (drawableRes != 0) {
        // TODO support icon replacement inside Counter without need in re-creating it
        this.counter = new Counter.Builder()
          .drawable(drawableRes, 14f, 0, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)
          .callback((counter, sizeChanged) -> {
          if (sizeChanged) {
            trimText(false);
          }
          invalidate();
        }).build();
        this.counter.setCount(Tdlib.CHAT_FAILED, animated);
      } else if (animated && counter != null) {
        this.counter.hide(true);
      } else {
        this.counter = null;
      }
    }
  }

  public void setUnreadCount (int unreadCount, boolean muted, boolean animated) {
    if (unreadCount == 0 && this.counter == null && iconDrawableRes == 0)
      return;
    if (this.counter == null || iconDrawableRes != 0) {
      this.counter = new Counter.Builder()
        .callback((counter, sizeChanged) -> {
        if (sizeChanged) {
          trimText(false);
        }
        invalidate();
      }).build();
    }
    this.counter.setCount(unreadCount, muted, animated);
  }

  @Override
  public void attach () {
    if (receiver != null) {
      receiver.attach();
    }
  }

  @Override
  public void detach () {
    if (receiver != null) {
      receiver.detach();
    }
  }

  @Override
  public void performDestroy () {
    if (receiver != null) {
      receiver.destroy();
    }
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public boolean toggle (boolean animated) {
    boolean isChecked = !isChecked();
    setChecked(isChecked, animated);
    return isChecked;
  }

  private boolean isDragging;

  public void setIsDragging (boolean isDragging) {
    if (this.isDragging != isDragging) {
      this.isDragging = isDragging;
      invalidate();
    }
  }

  @Override
  public void onDraw (Canvas c) {
    boolean rtl = Lang.rtl();
    int viewWidth = getMeasuredWidth();
    layoutReceiver();

    if (isDragging) {
      c.drawColor(ColorUtils.alphaColor(.85f, Theme.fillingColor()));
    }
    if (icon != null) {
      Drawables.draw(c, icon, rtl ? viewWidth - iconLeft - icon.getMinimumWidth() : iconLeft, iconTop, Paints.getIconGrayPorterDuffPaint());
    }
    if (trimmedText != null) {
      trimmedText.draw(c, textLeft, textLeft + trimmedText.getWidth(), 0, Screen.dp(17f));
    }

    if (receiver != null) {
      layoutReceiver();
      if (receiver.needPlaceholder()) {
        receiver.drawPlaceholder(c);
      }
      receiver.draw(c);
      if (checkFactor > 0f) {
        final double radians = Math.toRadians(rtl ? 315f : 45f);

        final int x = receiver.centerX() + (int) ((float) receiver.getWidth() / 2 * Math.sin(radians));
        final int y = receiver.centerY() + (int) ((float) receiver.getHeight() / 2 * Math.cos(radians));

        SimplestCheckBox.draw(c, x, y, checkFactor, null);

        RectF rectF = Paints.getRectF();
        int r = Screen.dp(11f);
        rectF.set(x - r, y - r, x + r, y + r);

        c.drawArc(rectF, rtl ? 225f + 170f * (1f - checkFactor) : 135f, 170f * checkFactor, false, Paints.getOuterCheckPaint(Theme.fillingColor()));
      }

    }
    if (counter != null) {
      counter.draw(c, rtl ? Screen.dp(24f) : viewWidth - Screen.dp(24f), getMeasuredHeight() / 2f, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, 1f, this, R.id.theme_color_badgeFailedText);
    }
  }
}
