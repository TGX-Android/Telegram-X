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
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.AvatarPlaceholder;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.widget.AttachDelegate;
import org.thunderdog.challegram.widget.CheckView;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ProgressComponent;
import org.thunderdog.challegram.widget.TimerView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;

public class MediaLocationPlaceView extends FrameLayoutFix implements AttachDelegate, Destroyable, FactorAnimator.Target, TimerView.ActiveStateChangeListener {
  private static final float IMAGE_RADIUS = 20f;

  private static final int FLAG_RED = 1;
  private static final int FLAG_FADED = 1 << 1;
  private static final int FLAG_LIVE_LOCATION = 1 << 2;
  private static final int FLAG_NEED_WAVES = 1 << 3;

  private MediaLocationData location;

  private TextView titleView;
  private TextView addressView;
  private ImageReceiver receiver;
  private CheckView checkView;
  private TimerView timerView;

  private final Drawable iconSmall, iconBig;

  public MediaLocationPlaceView (Context context) {
    super(context);
    iconSmall = Drawables.get(getResources(), R.drawable.baseline_location_on_18);
    iconBig = Drawables.get(getResources(), R.drawable.baseline_location_on_24);
  }

  private int flags;
  private @Nullable ViewController<?> themeProvider;

  private void setIsRed (boolean isRed) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_RED, isRed);
    if (this.flags != flags) {
      this.flags = flags;
      if (themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(titleView);
      }
      int colorId = isRed ? ColorId.textNegative : ColorId.text;
      titleView.setTextColor(Theme.getColor(colorId));
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(titleView, colorId);
      }
    }
  }

  public void setIsFaded (boolean isFaded) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_FADED, isFaded);
    if (this.flags != flags) {
      this.flags = flags;
      float alpha = isFaded ? .6f : 1f;
      titleView.setAlpha(alpha);
      addressView.setAlpha(alpha);
      receiver.invalidate();
    }
  }

  @Override
  public void onActiveStateChanged (TimerView v, boolean isActive) {
    int rightMargin = Screen.dp(IMAGE_RADIUS) + (timerView.isTimerVisible() ? Screen.dp(26f) : 0);
    Views.setRightMargin(titleView, rightMargin);
    Views.setRightMargin(addressView, rightMargin);
  }

  public void init (@Nullable ViewController<?> themeProvider, boolean big) {
    Context context = getContext();

    this.themeProvider = themeProvider;

    int imageRadius = Screen.dp(IMAGE_RADIUS);
    int imagePaddingTop = Screen.dp(8) + (big ? Screen.dp(4f) : 0);
    int iconPadding = 0; // Screen.dp(2f);

    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.bottomMargin = Screen.dp(10f);
    params.leftMargin = imageRadius * 2 + imageRadius * 2;
    params.rightMargin = imageRadius;

    titleView = new NoScrollTextView(context);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTextColor(Theme.textAccentColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(titleView);
    }
    titleView.setSingleLine();
    titleView.setLayoutParams(params);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    addView(titleView);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
    params.topMargin = Screen.dp(10f);
    params.leftMargin = imageRadius * 2 + imageRadius * 2;
    params.rightMargin = imageRadius;

    addressView = new NoScrollTextView(context);
    addressView.setTypeface(Fonts.getRobotoRegular());
    addressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    addressView.setTextColor(Theme.textDecentColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextDecentColorListener(addressView);
    }
    addressView.setSingleLine();
    addressView.setLayoutParams(params);
    addressView.setEllipsize(TextUtils.TruncateAt.END);
    addView(addressView);

    params = FrameLayoutFix.newParams(Screen.dp(26f), Screen.dp(26f), Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    params.rightMargin = Screen.dp(10f);
    timerView = new TimerView(context);
    timerView.setListener(this);
    timerView.setTextColor(Theme.progressColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(timerView, ColorId.progress);
    }
    timerView.setLayoutParams(params);
    addView(timerView);


    params = FrameLayoutFix.newParams(imageRadius * 2 + Screen.dp(1f) * 2, imageRadius * 2 + Screen.dp(1f) * 2, Gravity.LEFT | Gravity.TOP);
    params.leftMargin = imageRadius - Screen.dp(1f);
    params.topMargin = imagePaddingTop - Screen.dp(1f);
    checkView = new CheckView(context);
    checkView.initWithMode(CheckView.MODE_LOCATION);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(checkView);
    }
    checkView.setLayoutParams(params);
    addView(checkView);

    receiver = new ImageReceiver(this, 0);
    receiver.setBounds(imageRadius + iconPadding, imagePaddingTop + iconPadding, imageRadius + imageRadius * 2 - iconPadding, imagePaddingTop + imageRadius * 2 - iconPadding);

    setWillNotDraw(false);
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(big ? 64 : 56f)));

    Views.setClickable(this);
    RippleSupport.setSimpleWhiteBackground(this, themeProvider);
  }

  // Adapter

  @Override
  public void attach () {
    receiver.attach();
  }

  @Override
  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
  }

  // Data

  private int circleColorId = ColorId.fileAttach;
  private Letters letters;
  private float lettersWidth;

  public void setLocation (String title, String subtitle, @ColorId int circleColorId, Letters letters, boolean isFaded, int livePeriod, long expiresAt) {
    clearLiveLocation();
    setIsFaded(isFaded);
    timerView.setLivePeriod(livePeriod, expiresAt);
    titleView.setText(title);
    addressView.setText(subtitle);
    boolean needInvalidate = false;
    if (this.circleColorId != circleColorId) {
      this.circleColorId = circleColorId;
      needInvalidate = true;
    }
    if (!StringUtils.equalsOrBothEmpty(this.letters != null ? this.letters.text : null, letters != null ? letters.text : null)) {
      this.letters = letters;
      this.lettersWidth = Paints.measureLetters(letters, 17f);
      needInvalidate = true;
    }
    if (needInvalidate) {
      receiver.invalidate();
    }
  }

  public void setLocationImage (ImageFile image) {
    receiver.setRadius(0);
    receiver.requestFile(image);
  }

  public void setRoundedLocationImage (ImageFile image) {
    receiver.setRadius(Screen.dp(IMAGE_RADIUS));
    receiver.requestFile(image);
  }

  private AvatarPlaceholder avatarPlaceholder;

  public void setPlaceholder (AvatarPlaceholder.Metadata metadata) {
    receiver.clear();
    avatarPlaceholder = new AvatarPlaceholder(IMAGE_RADIUS, metadata, null);
  }

  public void setLocation (MediaLocationData location, boolean isChecked) {
    clearLiveLocation();
    if (this.location == null || !this.location.equals(location)) {
      this.location = location;
      receiver.requestFile(location.getIconImage());
      titleView.setText(location.getTitle());
      addressView.setText(location.getAddress());
    }
    checkView.forceSetChecked(isChecked);
  }

  public void setDefaultLiveLocation (boolean needWaves) {
    setLiveLocation(Lang.getString(R.string.ShareLiveLocation), Lang.getString(R.string.SendLiveLocationInfo), false, needWaves, false, 0, 0);
  }

  private Runnable subtitleUpdater;

  public Runnable getSubtitleUpdater () {
    return subtitleUpdater;
  }

  public void scheduleSubtitleUpdater (final Runnable updater, long delay) {
    if (this.subtitleUpdater != null) {
      removeCallbacks(subtitleUpdater);
    }
    this.subtitleUpdater = updater;
    if (updater != null) {
      postDelayed(updater, delay);
    }
  }

  public void updateSubtitle (CharSequence newSubtitle) {
    addressView.setText(newSubtitle);
  }

  private void clearLiveLocation () {
    this.flags &= ~FLAG_LIVE_LOCATION;
    this.flags &= ~FLAG_NEED_WAVES;
    scheduleSubtitleUpdater(null, -1);
    setIsRed(false);
  }

  public void setLiveLocation (String title, String subtitle, boolean isRed, boolean needWaves, boolean isFaded, int livePeriod, long expiresAt) {
    int flags = BitwiseUtils.setFlag(this.flags, FLAG_NEED_WAVES, needWaves);
    flags = BitwiseUtils.setFlag(flags, FLAG_FADED, isFaded);
    flags = BitwiseUtils.setFlag(flags, FLAG_LIVE_LOCATION, true);
    this.flags = flags;
    this.subtitleUpdater = null;
    this.letters = null;
    setIsRed(isRed);
    setIsFaded(isFaded);
    timerView.setLivePeriod(livePeriod, expiresAt);
    titleView.setText(title);
    addressView.setText(subtitle);
    receiver.requestFile(null);
    checkView.forceSetChecked(false);
    receiver.invalidate();
  }

  private BoolAnimator progressAnimator;
  private ProgressComponent progressComponent;
  private static final int ANIMATOR_PROGRESS = 0;

  public void setInProgress (boolean inProgress, boolean animated) {
    boolean nowInProgress = progressAnimator != null && progressAnimator.getValue();
    if (nowInProgress != inProgress) {
      if (progressAnimator == null) {
        progressAnimator = new BoolAnimator(ANIMATOR_PROGRESS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
      }
      if (progressComponent == null) {
        progressComponent = ProgressComponent.simpleInstance(this, 5f, receiver.getLeft(), receiver.getTop(), receiver.getWidth(), receiver.getHeight());
      }
      progressAnimator.setValue(inProgress, animated);
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    progressComponent.setAlpha(factor);
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  public MediaLocationData getLocation () {
    return location;
  }

  // Check

  public void setChecked (boolean isChecked, boolean animated) {
    if (animated) {
      checkView.setChecked(isChecked);
    } else {
      checkView.forceSetChecked(isChecked);
    }
  }

  // Drawing

  private long nextScheduleTime;

  @Override
  protected void onDraw (Canvas c) {
    int cx = receiver.centerX();
    int cy = receiver.centerY();

    float alpha = (flags & FLAG_FADED) != 0 ? .6f : 1f;

    if ((flags & FLAG_LIVE_LOCATION) != 0) {
      float progressFactor = progressAnimator != null ? progressAnimator.getFloatValue() : 0f;
      c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.getColor(ColorId.fileRed))));

      if (progressFactor < 1f) {
        Paint bitmapPaint = Paints.whitePorterDuffPaint();
        bitmapPaint.setAlpha((int) (255f * (1f - progressFactor) * alpha));
        Drawables.draw(c, iconSmall, cx - iconSmall.getMinimumWidth() / 2, cy - iconSmall.getMinimumHeight() / 2, bitmapPaint);
        bitmapPaint.setAlpha(255);

        if ((flags & FLAG_NEED_WAVES) != 0) {
          long delay = DrawAlgorithms.drawWaves(c, cx, cy, ColorUtils.color((int) (255f * (1f - progressFactor) * alpha), 0xffffff), false, nextScheduleTime);
          if (delay != -1) {
            nextScheduleTime = SystemClock.uptimeMillis() + delay;
            postInvalidateDelayed(delay, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom());
          }
        }
      }

      if (progressComponent != null) {
        progressComponent.draw(c);
      }

      return;
    }

    if (avatarPlaceholder != null) {
      avatarPlaceholder.draw(c, cx, cy);
      return;
    }

    c.drawCircle(cx, cy, Screen.dp(IMAGE_RADIUS), Paints.fillingPaint(ColorUtils.alphaColor(alpha, Theme.getColor(circleColorId))));
    if (letters != null) {
      Paints.drawLetters(c, letters, cx - lettersWidth / 2, cy + Screen.dp(6f), 17f, alpha);
    }
    if (letters == null || receiver.getCurrentFile() != null) {
      if (receiver.needPlaceholder()) {
        float iconAlpha = alpha - receiver.getDisplayAlpha();
        Paint paint = Paints.whitePorterDuffPaint();
        paint.setAlpha((int) (255f * iconAlpha));
        Drawables.draw(c, iconBig, cx - iconBig.getMinimumWidth() / 2, cy - iconBig.getMinimumHeight() / 2, paint);
        paint.setAlpha(255);
      }
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }
  }
}
