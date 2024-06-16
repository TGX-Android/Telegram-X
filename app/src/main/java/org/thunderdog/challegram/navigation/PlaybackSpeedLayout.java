package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
import androidx.annotation.Nullable;
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
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class PlaybackSpeedLayout extends MenuMoreWrapAbstract implements View.OnClickListener {
  private static final int MIN_SPEED = 20;
  private static final int MAX_SPEED = 300;

  private final Slider slider;
  private final SparseArray<Button> buttonSparseArray = new SparseArray<>();
  private final Button[] buttons = new Button[6];

  private @Nullable ThemeListenerList themeListeners;
  private Listener listener;

  public interface Listener {
    void onChange (int speed, boolean needApply, boolean needClose);
  }

  public PlaybackSpeedLayout (Context context) {
    super(context);

    slider = new Slider(context);
  }

  public void init (ThemeListenerList themeListeners, Listener listener, int currentSpeed) {
    this.themeListeners = themeListeners;
    this.currentSpeed = currentSpeed;
    this.listener = listener;

    setMinimumWidth(Screen.dp(196f));
    Drawable drawable;
    drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.getColor(ColorId.filling), PorterDuff.Mode.MULTIPLY));

    ViewUtils.setBackground(this, drawable);

    if (themeListeners != null) {
      themeListeners.addThemeSpecialFilterListener(drawable, ColorId.filling);
      themeListeners.addThemeInvalidateListener(this);
    }

    setOrientation(VERTICAL);
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));

    slider.setText(Lang.getString(R.string.PlaybackSpeed));
    slider.setOnTouchListener((View v, MotionEvent event) -> {
      processTouchEvent(event.getAction(), event.getX(), event.getY(), false);
      return true;
    });
    addTextViewAndSetColors(slider);

    {
      ShadowView shadowView = new ShadowView(getContext());
      if (themeListeners != null) {
        themeListeners.addThemeInvalidateListener(shadowView);
      }
      shadowView.setSimpleBottomTransparentShadow(false);
      shadowView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
      addView(shadowView);
    }
    {
      ShadowView shadowView = new ShadowView(getContext());
      if (themeListeners != null) {
        themeListeners.addThemeInvalidateListener(shadowView);
      }
      shadowView.setSimpleTopShadow(true);
      shadowView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(6f)));
      addView(shadowView);
    }

    buttons[0] = addItem(R.id.btn_playback_speed_0_5, R.string.PlaybackSpeed50, R.drawable.baseline_playback_speed_0_5_24, this, 50);
    buttons[1] = addItem(R.id.btn_playback_speed_0_7, R.string.PlaybackSpeed70, R.drawable.baseline_playback_speed_0_7_24, this, 70);
    buttons[2] = addItem(R.id.btn_playback_speed_1_0, R.string.PlaybackSpeed100, R.drawable.baseline_playback_speed_1_0_24, this, 100);
    buttons[3] = addItem(R.id.btn_playback_speed_1_2, R.string.PlaybackSpeed120, R.drawable.baseline_playback_speed_1_2_24, this, 120);
    buttons[4] = addItem(R.id.btn_playback_speed_1_5, R.string.PlaybackSpeed150, R.drawable.baseline_playback_speed_1_5_24, this, 150);
    buttons[5] = addItem(R.id.btn_playback_speed_2_0, R.string.PlaybackSpeed200, R.drawable.baseline_playback_speed_2_0_24, this, 200);

    setSliderValue((float) currentSpeed / MAX_SPEED, false, false);
    slider.setCounter(currentSpeed, false);
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    canvas.drawRect(Screen.dp(8), Screen.dp(8 + 48), getMeasuredWidth() - Screen.dp(8), Screen.dp(8 + 48 + 12), Paints.fillingPaint(Theme.backgroundColor()));
    super.dispatchDraw(canvas);
  }

  private void addTextViewAndSetColors (NoScrollTextView view) {
    view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(48)));
    view.setTextColor(Theme.textAccentColor());
    if (themeListeners != null) {
      themeListeners.addThemeTextAccentColorListener(view);
    }

    view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    view.setTag(view.getMeasuredWidth());

    addView(view);
  }

  private Button addItem (int id, @StringRes int stringRes, int iconRes, OnClickListener listener, int speed) {
    Button menuItem = new Button(getContext());
    menuItem.setId(id);
    menuItem.setSpeed(speed);

    menuItem.setText(Lang.getString(stringRes));
    menuItem.setOnClickListener(listener);
    menuItem.setDrawable(iconRes);

    addTextViewAndSetColors(menuItem);

    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);

    buttonSparseArray.append(speed, menuItem);
    return menuItem;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();

    if (id == R.id.btn_playback_speed_0_5) {
      listener.onChange(50, true, true);
    } else if (id == R.id.btn_playback_speed_0_7) {
      listener.onChange(70, true, true);
    } else if (id == R.id.btn_playback_speed_1_0) {
      listener.onChange(100, true, true);
    } else if (id == R.id.btn_playback_speed_1_2) {
      listener.onChange(120, true, true);
    } else if (id == R.id.btn_playback_speed_1_5) {
      listener.onChange(150, true, true);
    } else if (id == R.id.btn_playback_speed_2_0) {
      listener.onChange(200, true, true);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return true;
  }

  private static final int MODE_NONE = 0;
  private static final int MODE_HORIZONTAL = 1;
  private static final int MODE_VERTICAL = 2;

  private int mode;
  private float xStart, yStart;
  private float xPrev, yPrev;

  public void processTouchEvent (int event, float x, float y, boolean external) {
    final float dx = x - xPrev;
    final float dy = y - yPrev;
    final float dxTotal = x - xStart;
    final float dyTotal = y - yStart;

    switch (event) {
      case MotionEvent.ACTION_DOWN: {
        mode = MODE_NONE;
        xStart = xPrev = x;
        yStart = yPrev = y;
        lastIndex = -1;
        sliderWasChanged = false;
        break;
      }

      case MotionEvent.ACTION_MOVE: {
        if (mode == MODE_NONE) {
          if (Math.hypot(dx, dy) > Screen.getTouchSlop() * 1.5f) {
            mode = Math.abs(dx) > Math.abs(dy) ? MODE_HORIZONTAL : MODE_VERTICAL;
            xStart = xPrev = x;
            yStart = yPrev = y;
            UI.hapticVibrate(this, false);
          }
          break;
        }
        processMove(dx, dy, dxTotal, dyTotal, external);
        xPrev = x;
        yPrev = y;
        break;
      }

      case MotionEvent.ACTION_UP: {
        if (lastIndex != -1) {
          processUp(external);
          if (external) {
            Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_PLAYBACK_SPEED_SWIPE);
          }
        }
        break;
      }
    }
  }

  private int getTouchIndex (float dyTotal) {
    return dyTotal < Screen.dp(36) ? 0 : (MathUtils.clamp((int) (dyTotal - Screen.dp(36)) / Screen.dp(48), 0, buttons.length - 1) + 1);
  }

  private int lastIndex = -1;
  private int currentSpeed;
  private boolean sliderWasChanged;

  private void processUp (boolean needClose) {
    listener.onChange(currentSpeed, true, needClose);
  }

  private void processMove (float dx, float dy, float dxTotal, float dyTotal, boolean external) {
    final int index = external ? getTouchIndex(dyTotal) : 0;

    if (index == 0 && (dyTotal < 0 || sliderWasChanged || Math.abs(dxTotal) > Math.abs(dyTotal))) {
      sliderWasChanged = true;
      setSliderValue(value + dx / getMeasuredWidth(), true, true);
    }
    if (lastIndex != index) {
      if (lastIndex != -1) {
        UI.hapticVibrate(this, false);
      }
      lastIndex = index;
      for (int a = 0; a < buttons.length; a++) {
        buttons[a].setSelected(a == index - 1, true);
      }

      if (index > 0) {
        setSliderValue(((float) buttons[index - 1].speed) / MAX_SPEED, false, true);
      }
    }
  }


  private float value;
  private long lastUpdateTime;

  private void setSliderValue (float v, boolean fromSliderTouch, boolean animated) {
    final float value = MathUtils.clamp(v, (float) MIN_SPEED / MAX_SPEED, 1f);
    if (this.value != value) {
      this.value = value;
      this.slider.setValue(value, !fromSliderTouch && animated);
      final int speed = Math.round(value * MAX_SPEED / 10) * 10;
      if (this.currentSpeed != speed) {
        this.onSpeedChanged(speed);

        if (fromSliderTouch && animated) {
          if (updateRunnable == null) {
            final long delay = 150L - (System.currentTimeMillis() - lastUpdateTime);
            updateRunnable = new CancellableRunnable() {
              @Override
              public void act () {
                update(true);
                updateRunnable = null;
              }
            };
            UI.post(updateRunnable, delay);
          }
        } else {
          update(animated);
        }
      }
      invalidate();
    }
  }

  private void onSpeedChanged (int speed) {
    if (currentSpeed != speed) {
      setButtonActive(currentSpeed, false, true);
      setButtonActive(speed, true, true);

      listener.onChange(speed, false, false);
      currentSpeed = speed;
    }
  }

  private void setButtonActive (int speed, boolean active, boolean animated) {
    final Button button = buttonSparseArray.get(speed);
    if (button != null) {
      button.setActive(active, animated);
    }
  }

  private void update (boolean animated) {
    this.lastUpdateTime = System.currentTimeMillis();
    this.slider.setCounter(currentSpeed, animated);
  }

  private CancellableRunnable updateRunnable;

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (updateRunnable != null) {
      updateRunnable.cancel();
      updateRunnable = null;
    }
  }

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
}
