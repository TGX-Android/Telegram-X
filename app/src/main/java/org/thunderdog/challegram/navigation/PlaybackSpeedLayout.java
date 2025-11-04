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
 * File created on 12/06/2024
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.CounterPlaybackSpeedDrawableSet;
import org.thunderdog.challegram.util.RateLimiter;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class PlaybackSpeedLayout extends MenuMoreWrapAbstract implements View.OnClickListener, FactorAnimator.Target {
  private static final int MIN_SPEED = 20;
  private static final int MAX_SPEED = 300;
  private static final int FADE_ANIMATOR_ID = 0;
  private static final Rect tmpRect = new Rect();

  public static int normalizeSpeed (int speed) {
    return MathUtils.clamp(speed, MIN_SPEED, MAX_SPEED);
  }

  private static final int BUTTONS_COUNT = 6;
  private final Button[] buttons = new Button[BUTTONS_COUNT];
  private final BoolAnimator fade = new BoolAnimator(FADE_ANIMATOR_ID, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 230L);
  private final RateLimiter setSeekRunnable = new RateLimiter(this::applySpeedAndUpdateViews, 150L, null);
  private final Drawable backgroundDrawable;
  private final Slider slider;
  private final ShadowView shadowView1;
  private final ShadowView shadowView2;
  private final SparseArray<Button> buttonSparseArray = new SparseArray<>();


  private Listener listener;
  private int currentSpeed;
  private float value;

  public interface Listener {
    void onChange (int speed, boolean isFinal);
  }

  public PlaybackSpeedLayout (Context context) {
    super(context);

    setMinimumWidth(Screen.dp(196f));

    setOrientation(VERTICAL);
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));

    backgroundDrawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.getColor(ColorId.filling), PorterDuff.Mode.MULTIPLY));
    backgroundDrawable.getPadding(tmpRect);
    setPadding(tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom);

    slider = new Slider(context);
    slider.setText(Lang.getString(R.string.PlaybackSpeed));
    slider.setOnTouchListener((v, event) -> {
      processTouchEvent(event.getAction(), event.getX(), event.getY(), 0f, false);
      return true;
    });
    addItem(slider);

    shadowView1 = new ShadowView(getContext());
    shadowView1.setSimpleBottomTransparentShadow(false);
    shadowView1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
    addView(shadowView1);

    shadowView2 = new ShadowView(getContext());
    shadowView2.setSimpleTopShadow(true);
    shadowView2.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
    addView(shadowView2);

    buttons[0] = addButton(R.id.btn_playback_speed_0_5, R.string.PlaybackSpeed50, R.drawable.baseline_playback_speed_0_5_24, 50);
    buttons[1] = addButton(R.id.btn_playback_speed_0_7, R.string.PlaybackSpeed70, R.drawable.baseline_playback_speed_0_7_24, 70);
    buttons[2] = addButton(R.id.btn_playback_speed_1_0, R.string.PlaybackSpeed100, R.drawable.baseline_playback_speed_1_0_24, 100);
    buttons[3] = addButton(R.id.btn_playback_speed_1_2, R.string.PlaybackSpeed120, R.drawable.baseline_playback_speed_1_2_24, 120);
    buttons[4] = addButton(R.id.btn_playback_speed_1_5, R.string.PlaybackSpeed150, R.drawable.baseline_playback_speed_1_5_24, 150);
    buttons[5] = addButton(R.id.btn_playback_speed_2_0, R.string.PlaybackSpeed200, R.drawable.baseline_playback_speed_2_0_24, 200);
  }

  public void init (ThemeListenerList themeListeners, Listener listener, int currentSpeed) {
    this.currentSpeed = currentSpeed;
    this.value = valueFromSpeed(currentSpeed);
    this.listener = listener;

    if (themeListeners != null) {
      themeListeners.addThemeSpecialFilterListener(backgroundDrawable, ColorId.filling);
      themeListeners.addThemeInvalidateListener(this);
      themeListeners.addThemeTextAccentColorListener(slider);
      themeListeners.addThemeInvalidateListener(shadowView1);
      themeListeners.addThemeInvalidateListener(shadowView2);
      for (Button button : buttons) {
        themeListeners.addThemeTextAccentColorListener(button);
      }
    }

    updateViews(false);
    updateSliderBackground(false);
  }




  private static final int MODE_FLAG_ALLOW_HORIZONTAL = 1;
  private static final int MODE_FLAG_ALLOW_VERTICAL = 1 << 1;

  private static final int MODE_NONE = 0;
  private static final int MODE_HORIZONTAL = MODE_FLAG_ALLOW_HORIZONTAL;
  private static final int MODE_VERTICAL = MODE_FLAG_ALLOW_VERTICAL;
  private static final int MODE_ALL_DIRECTIONS = MODE_FLAG_ALLOW_HORIZONTAL | MODE_FLAG_ALLOW_VERTICAL;

  private int modeFlags;

  private int defaultSpeed;
  private float xStart, yStart, xPrev, yPrev, yDown;
  private boolean hasChanges = false;
  private boolean canLeaveHorizontalMode = false;

  public void processTouchEvent (int event, float x, float y, float offset, boolean external) {
    final float dx = x - xPrev;
    switch (event) {
      case MotionEvent.ACTION_DOWN: {
        modeFlags = external ? MODE_NONE : MODE_HORIZONTAL;
        defaultSpeed = currentSpeed;
        xPrev = xStart = x;
        yPrev = yStart = yDown = y;
        hasChanges = false;
        break;
      }

      case MotionEvent.ACTION_MOVE: {
        final int index = external ? getTouchIndex(y - yDown + offset) : 0;
        if (modeFlags == MODE_NONE) {
          final float dxTotal = x - xStart;
          float dyTotal = y - yStart;
          if (dyTotal < 0 && hasChanges && index == 0) {
            dyTotal = 0f;
          }

          if (Math.hypot(dxTotal, dyTotal) > Screen.getTouchSlop() * 1.5f) {
            final boolean horizontalMode = (dyTotal < 0f && index == 0) || Math.abs(dxTotal) > Math.abs(dyTotal);
            final boolean verticalMode = !horizontalMode && index > 0;
            if (horizontalMode || verticalMode) {
              modeFlags = verticalMode ? MODE_VERTICAL : MODE_HORIZONTAL;
              UI.hapticVibrate(this, verticalMode);
              xPrev = xStart = x;
              yPrev = yStart = y;
              canLeaveHorizontalMode = false;
            }
          }
          break;
        }

        if (modeFlags == MODE_VERTICAL && index == 0) {
          modeFlags = MODE_NONE;
          xPrev = xStart = x;
          yPrev = yStart = y;
          setSliderValue(valueFromSpeed(defaultSpeed), false);
          setButtonSelected(-1, true);
          UI.hapticVibrate(this, true);
          break;
        }

        final boolean speedChanged = processMove(index, dx);
        boolean needVibrate = speedChanged && defaultSpeed == currentSpeed;
        boolean forceVibrate = needVibrate && !BitwiseUtils.hasFlag(modeFlags, MODE_FLAG_ALLOW_VERTICAL) || index == 0;
        hasChanges |= speedChanged | index > 0;
        canLeaveHorizontalMode |= speedChanged; // | index > 0;

        // if (modeFlags == MODE_ALL_DIRECTIONS && defaultSpeed != currentSpeed /*&& speedChanged*/ && index == 0) {
        //   modeFlags = MODE_HORIZONTAL;
        //   needVibrate = true;
        // }

        /* if (modeFlags == MODE_ALL_DIRECTIONS && index > 0) {
          modeFlags = MODE_VERTICAL;
          needVibrate = true;
        }*/

        if (modeFlags == MODE_HORIZONTAL && canLeaveHorizontalMode && external && defaultSpeed == currentSpeed) {
          modeFlags = MODE_NONE;
          setSliderValue(valueFromSpeed(defaultSpeed), false);
          xPrev = xStart = x;
          yPrev = yStart = y;
          needVibrate = true;
        }

        needVibrate |= BitwiseUtils.hasFlag(modeFlags, MODE_FLAG_ALLOW_VERTICAL) && index > 0 && speedChanged;
        /*needVibrate |=*/ setButtonsVisibility(/*defaultSpeed == currentSpeed ||*/ !external || modeFlags == MODE_NONE || BitwiseUtils.hasFlag(modeFlags, MODE_FLAG_ALLOW_VERTICAL));

        /*
        final boolean needVibrateBySlide = needVibrateBySlide();
        needVibrate |= needVibrateBySlide && (index == 0 || !BitwiseUtils.hasFlag(modeFlags, MODE_FLAG_ALLOW_VERTICAL));
        */

        if (needVibrate) {
          UI.hapticVibrate(this, forceVibrate);
        }

        xPrev = x;
        yPrev = y;
        break;
      }

      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP: {
        if (hasChanges) {
          applySpeedAndUpdateViews(currentSpeed, external);
          if (external) {
            Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_PLAYBACK_SPEED_SWIPE);
          }
        }
        break;
      }
    }
  }


  private boolean processMove (int index, float dx) {
    final boolean allowVertical = BitwiseUtils.hasFlag(modeFlags, MODE_FLAG_ALLOW_VERTICAL);
    boolean changed = false;

    if (index == 0 || !allowVertical) {
      changed = setSliderValue(value + dx / getMeasuredWidth(), true);
    }

    setButtonSelected(allowVertical ? index - 1 : -1, true);
    if (index > 0 && allowVertical) {
      changed = setSliderValue( buttons[index - 1].speed);
    }

    return changed;
  }






  /* * */

  private boolean setSliderValue (int speed) {
    if (currentSpeed != speed) {
      return setSliderValue(valueFromSpeed(speed), false);
    }
    return false;
  }

  private boolean setSliderValue (float v, boolean fromSliderTouch) {
    final float value = MathUtils.clamp(v, (float) MIN_SPEED / MAX_SPEED, 1f);
    if (this.value != value) {
      this.value = value;
      updateSliderBackground(!fromSliderTouch);
      final int speed = speedFromValue(value);
      if (currentSpeed != speed) {
        currentSpeed = speed;
        setSeekRunnable.run();
        return true;
      }
    }

    return false;
  }



  private CancellableRunnable scheduledHideButtons;
  private long lastButtonsShowMillis;


  private boolean setButtonsVisibility (boolean visible) {
    final boolean hidden = !visible;
    if (fade.getValue() != hidden) {

      if (visible) {
        lastButtonsShowMillis = SystemClock.uptimeMillis();
        if (scheduledHideButtons != null) {
          scheduledHideButtons.cancel();
          scheduledHideButtons = null;
        }
      } else {
        if (scheduledHideButtons != null) {
          return false;
        }
        final long delay = SystemClock.uptimeMillis() - lastButtonsShowMillis;
        if (delay < 300L) {
          scheduledHideButtons = new CancellableRunnable() {
            @Override
            public void act () {
              fade.setValue(true, true);
              scheduledHideButtons = null;
            }
          };
          UI.post(scheduledHideButtons, 300L - delay);
          return false;
        }
      }

      fade.setValue(hidden, true);
      return true;
    }
    return false;
  }


  /* * */

  private void applySpeedAndClose (int speed) {
    applySpeedAndUpdateViews(speed, true);
  }

  private void applySpeedAndUpdateViews () {
    applySpeedAndUpdateViews(currentSpeed, false);
  }

  private void applySpeedAndUpdateViews (int speed, boolean isFinal) {
    if (speed <= 0)
      throw new IllegalArgumentException(Integer.toString(speed));
    currentSpeed = speed;
    listener.onChange(currentSpeed, isFinal);
    if (!isFinal) {
      updateViews(true);
    }
  }

  private void updateViews (boolean animated) {
    setButtonActive(currentSpeed, animated);
    slider.setCounter(currentSpeed, animated);
  }

  private void updateSliderBackground (boolean animated) {
    slider.setValue(value, animated);
  }


  /* * */

  private int lastButtonActive;

  private void setButtonActive (int speed, boolean animated) {
    if (lastButtonActive != speed) {
      setButtonActive(lastButtonActive, false, animated);
      setButtonActive(speed, true, animated);
      lastButtonActive = speed;
    }
  }

  private void setButtonActive (int speed, boolean active, boolean animated) {
    final Button button = buttonSparseArray.get(speed);
    if (button != null) {
      button.setActive(active, animated);
    }
  }

  private int lastButtonSelected = -1;

  private void setButtonSelected (int index, boolean animated) {
    if (lastButtonSelected != index) {
      setButtonSelected(lastButtonSelected, false, animated);
      setButtonSelected(index, true, animated);
      lastButtonSelected = index;
    }
  }

  private void setButtonSelected (int index, boolean selected, boolean animated) {
    final Button button = index >= 0 && index < buttons.length ? buttons[index] : null;
    if (button != null) {
      button.setSelected(selected, animated);
    }
  }

  private int lastVibrateIndex = -1;

  private boolean needVibrateBySlide () {
    final int index = currentSpeed / 50;
    final int oldIndex = lastVibrateIndex;
    if (lastVibrateIndex != index) {
      lastVibrateIndex = index;
      return oldIndex != -1;
    }
    return false;
  }

  /* * */

  private static class Slider extends NoScrollTextView {
    private static final RectF tmpRect = new RectF();
    private static final float[] tmpRadii = new float[8];

    private final FactorAnimator value;
    private final BoolAnimator isRed;
    private final BoolAnimator isRound;
    private final Counter counter;
    private final Path path = new Path();

    public Slider (Context context) {
      super(context);

      setTypeface(Fonts.getRobotoRegular());
      setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);

      setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);

      setPadding(Screen.dp(17f), 0, Screen.dp(49f), 0);

      isRed = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      isRound = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      value = new FactorAnimator(0, (a, b, c, d) -> this.invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      counter = new Counter.Builder()
        .noBackground()
        .allBold(true)
        .drawable(Drawables.get(R.drawable.baseline_playback_speed_x_5), 0, Gravity.LEFT)
        .setCustomTextPartBuilder(new CounterPlaybackSpeedDrawableSet())
        .textSize(13f)
        .colorSet(this::getTextColor)
        .callback(this)
        .build();
    }

    public void setValue (float v, boolean animated) {
      if (animated) {
        value.animateTo(v);
      } else {
        value.forceFactor(v);
      }
      invalidate();
    }

    public void setCounter (int speed, boolean animated) {
      isRed.setValue(speed < 50, animated);
      isRound.setValue(speed < MAX_SPEED, animated);
      counter.setCount(speed, false, getSpeedText(speed), animated);
    }

    private boolean inFillingMode;
    private int getTextColor () {
      return Theme.getColor(inFillingMode ? ColorId.fillingPositiveContent : ColorId.text);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      final int color = ColorUtils.fromToArgb(Theme.getColor(ColorId.fillingPositive), Theme.getColor(ColorId.fillingNegative), isRed.getFloatValue());
      final float position = getMeasuredWidth() * value.getFactor();
      final Layout layout = getLayout();
      if (layout == null) {
        return;
      }

      final float radius = Screen.dp(2) * isRound.getFloatValue();

      tmpRect.set(0, 0, position, getMeasuredHeight());
      tmpRadii[0] = tmpRadii[1] = 0;
      tmpRadii[2] = tmpRadii[3] = radius;
      tmpRadii[4] = tmpRadii[5] = radius;
      tmpRadii[6] = tmpRadii[7] = 0;

      path.reset();
      path.addRoundRect(tmpRect, tmpRadii, Path.Direction.CCW);
      path.close();

      canvas.drawPath(path, Paints.fillingPaint(color));

      int s = Views.save(canvas);
      canvas.clipRect(tmpRect);
      inFillingMode = true;
      canvas.drawText(getText().toString(), getPaddingLeft(), (getMeasuredHeight() - layout.getHeight()) / 2f + layout.getLineBaseline(0), Paints.getTextPaint16(getTextColor()));
      counter.draw(canvas, getMeasuredWidth() - Screen.dp(24), getMeasuredHeight() / 2f, Gravity.CENTER, 1f);
      Views.restore(canvas, s);

      tmpRect.set(position, 0, getMeasuredWidth(), getMeasuredHeight());
      s = Views.save(canvas);
      canvas.clipRect(tmpRect);
      inFillingMode = false;
      canvas.drawText(getText().toString(), getPaddingLeft(), (getMeasuredHeight() - layout.getHeight()) / 2f + layout.getLineBaseline(0), Paints.getTextPaint16(getTextColor()));
      counter.draw(canvas, getMeasuredWidth() - Screen.dp(24), getMeasuredHeight() / 2f, Gravity.CENTER, 1f);
      Views.restore(canvas, s);
    }
  }

  private static class Button extends NoScrollTextView {
    private final BoolAnimator isActive;
    private final BoolAnimator isSelected;
    private Drawable drawable;
    private int speed;

    public Button (Context context) {
      super(context);

      this.isActive = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      this.isSelected = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

      setTypeface(Fonts.getRobotoRegular());
      setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);

      setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);

      setPadding(Screen.dp(17f), 0, Screen.dp(49f), 0);
    }

    public void setSpeed (int speed) {
      this.speed = speed;
    }

    public void setSelected (boolean isActive, boolean animated) {
      this.isSelected.setValue(isActive, animated);
    }

    public void setActive (boolean isActive, boolean animated) {
      this.isActive.setValue(isActive, animated);
    }

    public void setDrawable (@DrawableRes int drawableRes) {
      drawable = Drawables.get(getResources(), drawableRes);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      final float selected = isSelected.getFloatValue();
      if (selected > 0) {
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(ColorUtils.alphaColor(selected, Theme.RIPPLE_COLOR)));
      }
      super.onDraw(canvas);
      if (isActive.isAnimating()) {
        final int color = ColorUtils.fromToArgb(Theme.getColor(ColorId.icon), Theme.getColor(ColorId.iconActive), isActive.getFloatValue());
        Drawables.draw(canvas, drawable, getMeasuredWidth() - Screen.dp(36), Screen.dp(12), Paints.getPorterDuffPaint(color));
      } else {
        Drawables.draw(canvas, drawable, getMeasuredWidth() - Screen.dp(36), Screen.dp(12), PorterDuffPaint.get(isActive.getValue() ? ColorId.iconActive : ColorId.icon));
      }
    }
  }

  public static String getSpeedText (int speed) {
    final int whole = speed / 100;
    final int fract = speed % 100;

    StringBuilder b = new StringBuilder(5);
    b.append(whole);

    if (fract == 0) {
      return b.toString();
    }

    b.append('.');
    b.append(fract % 10 == 0 ? fract / 10 : fract);

    return b.toString();
  }

  private static int speedFromValue (float value) {
    return Math.round(value * MAX_SPEED / 10) * 10;
  }

  private static float valueFromSpeed (int speed) {
    return (float) speed / MAX_SPEED;
  }

  private static int getTouchIndex (float y) {
    return MathUtils.clamp((int) ((y - Screen.dp(12)) / Screen.dp(48)), 0, BUTTONS_COUNT);
  }

  /* * */

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    final float fadeFactor = fade.getFloatValue();
    final boolean needClip = fadeFactor != 0f;

    tmpRect.set(0, 0, getMeasuredWidth(), MathUtils.fromTo(getMeasuredHeight(), Screen.dp(48 + 8 * 2), fadeFactor));
    backgroundDrawable.setBounds(tmpRect);
    backgroundDrawable.draw(canvas);

    int s = -1;
    if (needClip) {
      s = Views.save(canvas);
      canvas.clipRect(tmpRect);
    }

    canvas.drawRect(Screen.dp(8), Screen.dp(8 + 48), getMeasuredWidth() - Screen.dp(8), Screen.dp(8 + 48 + 12),
      Paints.fillingPaint(ColorUtils.alphaColor(1f - fadeFactor, Theme.backgroundColor())));

    super.dispatchDraw(canvas);

    if (needClip) {
      Views.restore(canvas, s);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();

    if (id == R.id.btn_playback_speed_0_5) {
      applySpeedAndClose(50);
    } else if (id == R.id.btn_playback_speed_0_7) {
      applySpeedAndClose(70);
    } else if (id == R.id.btn_playback_speed_1_0) {
      applySpeedAndClose(100);
    } else if (id == R.id.btn_playback_speed_1_2) {
      applySpeedAndClose(120);
    } else if (id == R.id.btn_playback_speed_1_5) {
      applySpeedAndClose(150);
    } else if (id == R.id.btn_playback_speed_2_0) {
      applySpeedAndClose(200);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return true;
  }

  @Override
  public int getItemsWidth () {
    int childCount = getChildCount();
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    return Math.max(getMinimumWidth(), maxWidth);
  }

  @Override
  public int getItemsHeight () {
    return Screen.dp(48 * 7 + 12);
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    setSeekRunnable.cancelIfScheduled();
    if (scheduledHideButtons != null) {
      scheduledHideButtons.cancel();
      scheduledHideButtons = null;
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == FADE_ANIMATOR_ID) {
      final float alpha = 1f - factor;

      shadowView1.setAlpha(alpha);
      shadowView2.setAlpha(alpha);
      for (Button button : buttons) {
        button.setAlpha(alpha);
      }

      invalidate();
    }
  }

  /* * */

  private void addItem (NoScrollTextView view) {
    view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48)));
    view.setTextColor(Theme.textAccentColor());
    view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    view.setTag(view.getMeasuredWidth());

    addView(view);
  }

  private Button addButton (int id, @StringRes int stringRes, int iconRes, int speed) {
    Button menuItem = new Button(getContext());
    menuItem.setId(id);
    menuItem.setSpeed(speed);

    menuItem.setText(Lang.getString(stringRes));
    menuItem.setOnClickListener(this);
    menuItem.setDrawable(iconRes);

    addItem(menuItem);

    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);

    buttonSparseArray.append(speed, menuItem);
    return menuItem;
  }
}
