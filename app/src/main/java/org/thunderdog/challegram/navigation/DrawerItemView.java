/**
 * File created on 09/05/15 at 13:01
 * Copyright Vyacheslav Krylov, 2014
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

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
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
  private ImageReceiver receiver;
  private Counter counter;

  public DrawerItemView (Context context, Tdlib tdlib) {
    super(context, tdlib);
    TGLegacyManager.instance().addEmojiListener(this);
  }

  @Override
  public void onEmojiPartLoaded () {
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
  private static final float AVATAR_LETTERS_SIZE = 16f;

  public void addAvatar () {
    receiver = new ImageReceiver(this, Screen.dp(AVATAR_SIZE) / 2);
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

  private AvatarPlaceholder avatarPlaceholder;
  private ImageFile imageFile;

  public void setAvatar (AvatarPlaceholder.Metadata avatarPlaceholderMetadata, @Nullable ImageFile imageFile) {
    if (avatarPlaceholderMetadata != null) {
      avatarPlaceholder = new AvatarPlaceholder(AVATAR_SIZE / 2f, avatarPlaceholderMetadata, null);
    } else {
      avatarPlaceholder = null;
    }
    this.receiver.requestFile(this.imageFile = imageFile);
    invalidate();
  }

  public void setError (boolean error, boolean animated) {
    if (error) {
      setUnreadCount(Tdlib.CHAT_FAILED, counter != null && counter.isMuted(), animated);
    } else {
      setUnreadCount(0, false, animated);
    }
  }

  public void setUnreadCount (int unreadCount, boolean muted, boolean animated) {
    if (unreadCount == 0 && this.counter == null)
      return;
    if (this.counter == null){
      this.counter = new Counter.Builder().callback((counter, sizeChanged) -> {
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
      int radius = Screen.dp(AVATAR_SIZE) / 2;
      if (imageFile != null) {
        if (receiver.needPlaceholder()) {
          receiver.drawPlaceholderRounded(c, radius);
        }
        receiver.draw(c);
      } else if (avatarPlaceholder != null) {
        avatarPlaceholder.draw(c, receiver.centerX(), receiver.centerY());
      }
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
    if (counter != null)
      counter.draw(c, rtl ? Screen.dp(24f) : viewWidth - Screen.dp(24f), getMeasuredHeight() / 2f, Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT, 1f);
  }
}
