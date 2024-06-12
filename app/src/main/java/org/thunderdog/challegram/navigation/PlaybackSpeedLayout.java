package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
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
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.ShadowView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.CancellableRunnable;

public class PlaybackSpeedLayout extends MenuMoreWrapAbstract implements View.OnClickListener {

  private final Slider slider;
  private @Nullable ThemeListenerList themeListeners;

  private final SparseArray<Button> buttonSparseArray = new SparseArray<>();
  private Listener listener;
  public interface Listener {
    void onChange (int speed, boolean needApply, boolean needClose);
  }

  public PlaybackSpeedLayout (Context context) {
    super(context);

    slider = new Slider(getContext());
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

    slider.setListener(this::onSpeedChanged);
    slider.setText(Lang.getString(R.string.PlaybackSpeed));
    slider.setValue((float) currentSpeed / Slider.MAX_SPEED, false, false);
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

    addItem(R.id.btn_playback_speed_0_5, R.string.PlaybackSpeed50, R.drawable.baseline_playback_speed_0_5_24, this, 50);
    addItem(R.id.btn_playback_speed_0_7, R.string.PlaybackSpeed70, R.drawable.baseline_playback_speed_0_7_24, this, 70);
    addItem(R.id.btn_playback_speed_1_0, R.string.PlaybackSpeed100, R.drawable.baseline_playback_speed_1_0_24, this, 100);
    addItem(R.id.btn_playback_speed_1_2, R.string.PlaybackSpeed120, R.drawable.baseline_playback_speed_1_2_24, this, 120);
    addItem(R.id.btn_playback_speed_1_5, R.string.PlaybackSpeed150, R.drawable.baseline_playback_speed_1_5_24, this, 150);
    addItem(R.id.btn_playback_speed_2_0, R.string.PlaybackSpeed200, R.drawable.baseline_playback_speed_2_0_24, this, 200);

    setButtonActive(currentSpeed, true, false);
  }

  private int currentSpeed;

  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    canvas.drawRect(Screen.dp(8), Screen.dp(8 + 48), getMeasuredWidth() - Screen.dp(8), Screen.dp(8 + 48 + 12), Paints.fillingPaint(Theme.backgroundColor()));
    super.dispatchDraw(canvas);
  }

  private void onSpeedChanged (int speed, boolean needApply, boolean needClose) {
    if (currentSpeed != speed || needApply) {
      setButtonActive(currentSpeed, false, true);
      setButtonActive(speed, true, true);

      listener.onChange(speed, needApply, needClose);
      currentSpeed = speed;
    }
  }

  private void setButtonActive (int speed, boolean active, boolean animated) {
    final Button button = buttonSparseArray.get(speed);
    if (button != null) {
      button.setActive(active, animated);
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return true;
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

  private void addItem (int id, @StringRes int stringRes, int iconRes, OnClickListener listener, int speed) {
    Button menuItem = new Button(getContext());
    menuItem.setId(id);

    menuItem.setText(Lang.getString(stringRes));
    menuItem.setOnClickListener(listener);
    menuItem.setDrawable(iconRes);

    addTextViewAndSetColors(menuItem);

    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);

    buttonSparseArray.append(speed, menuItem);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();

    if (id == R.id.btn_playback_speed_0_5) {
      onSpeedChanged(50, true, true);
    } else if (id == R.id.btn_playback_speed_0_7) {
      onSpeedChanged(70, true, true);
    } else if (id == R.id.btn_playback_speed_1_0) {
      onSpeedChanged(100, true, true);
    } else if (id == R.id.btn_playback_speed_1_2) {
      onSpeedChanged(120, true, true);
    } else if (id == R.id.btn_playback_speed_1_5) {
      onSpeedChanged(150, true, true);
    } else if (id == R.id.btn_playback_speed_2_0) {
      onSpeedChanged(200, true, true);
    }
  }

  private static class Slider extends NoScrollTextView {
    private static final int MIN_SPEED = 20;
    private static final int MAX_SPEED = 300;

    private final BoolAnimator isRed;
    private final Counter counter;
    private Listener listener;

    private float value;
    private int speed;

    public Slider (Context context) {
      super(context);

      setTypeface(Fonts.getRobotoRegular());
      setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);

      setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);

      setPadding(Screen.dp(17f), 0, Screen.dp(49f), 0);

      isRed = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
      counter = new Counter.Builder()
        .noBackground()
        .allBold(true)
        .drawable(Drawables.get(R.drawable.baseline_playback_speed_x_5), 1, Gravity.LEFT)
        .textSize(13f)
        .textColor(ColorId.text)
        .callback(this)
        .build();
    }

    public void setListener (Listener listener) {
      this.listener = listener;
    }

    private void setValue (float v, boolean fromTouch, boolean animated) {
      final float value = MathUtils.clamp(v, (float) MIN_SPEED / MAX_SPEED, 1f);

      if (this.value != value) {
        this.value = value;
        final int speed = Math.round(value * MAX_SPEED / 10) * 10;
        if (this.speed != speed) {
          this.speed = speed;

          if (fromTouch && animated) {
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

    private long lastUpdateTime;

    private void update (boolean animated) {
      this.lastUpdateTime = System.currentTimeMillis();
      this.isRed.setValue(speed < 50, animated);
      this.counter.setCount(speed, false, getSpeedText(speed), animated);
    }

    @Override
    protected void onDetachedFromWindow () {
      super.onDetachedFromWindow();
      if (updateRunnable != null) {
        updateRunnable.cancel();
        updateRunnable = null;
      }
    }

    private CancellableRunnable updateRunnable;
    private boolean captured;
    private float capturedX;

    @Override
    public boolean onTouchEvent (MotionEvent event) {
      final float x = event.getX();

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN: {
            capturedX = x;
          captured = true;
          return true;
        }
        case MotionEvent.ACTION_MOVE: {
          if (captured) {
            processTouchMove(x - capturedX);
            capturedX = x;
            listener.onChange(speed, false, false);
            return true;
          }
          break;
        }
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_UP: {
          if (captured) {
            captured = false;
            listener.onChange(speed, true, false);
            return true;
          }
          break;
        }
      }

      return false;
    }

    public void processTouchMove (float dx) {
      setValue(value + dx / getMeasuredWidth(), true, true);
      listener.onChange(speed, false, false);
    }

    @Override
    protected void onDraw (Canvas canvas) {
      final int color = ColorUtils.fromToArgb(Theme.getColor(ColorId.iconActive), Theme.getColor(ColorId.themeRed), isRed.getFloatValue());
      canvas.drawRect(0, 0, getMeasuredWidth() * value, getMeasuredHeight(), Paints.fillingPaint(color));
      super.onDraw(canvas);
      counter.draw(canvas, getMeasuredWidth() - Screen.dp(24), getMeasuredHeight() / 2f, Gravity.CENTER, 1f);
    }
  }

  public void processTouchMove (float x) {
    slider.processTouchMove(x);
  }

  private static class Button extends NoScrollTextView {
    private final BoolAnimator isActive;
    private Drawable drawable;

    public Button (Context context) {
      super(context);

      this.isActive = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

      setTypeface(Fonts.getRobotoRegular());
      setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);

      setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
      setSingleLine(true);
      setEllipsize(TextUtils.TruncateAt.END);

      setPadding(Screen.dp(17f), 0, Screen.dp(49f), 0);
    }

    public void setActive (boolean isActive, boolean animated) {
      this.isActive.setValue(isActive, animated);
    }

    public void setDrawable (@DrawableRes int drawableRes) {
      drawable = Drawables.get(getResources(), drawableRes);
    }

    @Override
    protected void onDraw (Canvas canvas) {
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
