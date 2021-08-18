package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

/**
 * Date: 2019-06-30
 * Author: default
 */
public class SnackBar extends RelativeLayout {
  public interface Callback {
    void onSnackBarTransition (SnackBar v, float factor);
    default void onDestroySnackBar (SnackBar v) { }
  }

  private TextView textView;
  private TextView actionView;

  private final BoolAnimator isShowing;

  public SnackBar (Context context) {
    super(context);

    RelativeLayout.LayoutParams rp;

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.addRule(RelativeLayout.LEFT_OF, R.id.text_title);
    rp.topMargin = rp.bottomMargin = Screen.dp(2f);

    textView = new TextView(context);
    textView.setTextColor(Theme.getColor(R.id.theme_color_snackbarUpdateText));
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    textView.setPadding(Screen.dp(12f), Screen.dp(12f), 0, Screen.dp(12f));
    textView.setLayoutParams(rp);
    addView(textView);

    rp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    rp.leftMargin = rp.rightMargin = rp.topMargin = rp.bottomMargin = Screen.dp(2f);
    rp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    actionView = new TextView(context);
    actionView.setPadding(Screen.dp(12f), Screen.dp(12f), Screen.dp(12f), Screen.dp(12f));
    actionView.setTextColor(Theme.getColor(R.id.theme_color_snackbarUpdateAction));
    actionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    actionView.setLayoutParams(rp);
    Views.setClickable(actionView);
    addView(actionView);

    ViewSupport.setThemedBackground(this, R.id.theme_color_snackbarUpdate);

    isShowing = new BoolAnimator(0, new FactorAnimator.Target() {
      @Override
      public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        updateTranslation();
      }

      @Override
      public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
        updateTranslation();
        if (finalFactor == 0f && !isShowing.getValue() && callback != null) {
          callback.onDestroySnackBar(SnackBar.this);
        }
      }
    }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    setOnTouchListener((v, e) -> true);
  }

  private Callback callback;

  public SnackBar setCallback (Callback callback) {
    this.callback = callback;
    return this;
  }

  public SnackBar setText (String text) {
    textView.setText(text);
    return this;
  }

  public SnackBar setAction (String action, Runnable callback) {
    Views.setMediumText(actionView, action.toUpperCase());
    actionView.setOnClickListener(v -> {
      callback.run();
      dismissSnackBar(true);
    });
    return this;
  }

  public SnackBar showSnackBar (boolean animated) {
    isShowing.setValue(true, animated);
    return this;
  }

  public SnackBar dismissSnackBar (boolean animated) {
    isShowing.setValue(false, animated);
    return this;
  }

  public SnackBar addThemeListeners (@Nullable ViewController themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(actionView, R.id.theme_color_snackbarUpdateAction);
      themeProvider.addThemeTextColorListener(textView, R.id.theme_color_snackbarUpdateText);
      themeProvider.addThemeInvalidateListener(this);
    }
    return this;
  }

  public SnackBar removeThemeListeners (@Nullable ViewController themeProvider) {
    if (themeProvider != null) {
      themeProvider.removeThemeListenerByTarget(textView);
      themeProvider.removeThemeListenerByTarget(actionView);
      themeProvider.removeThemeListenerByTarget(this);
    }
    return this;
  }

  private void updateTranslation () {
    float y = getMeasuredHeight() * (1f - isShowing.getFloatValue());
    if (getTranslationY() != y || y == 0) {
      if (callback != null) {
        callback.onSnackBarTransition(this, isShowing.getFloatValue());
      }
      setTranslationY(y);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    updateTranslation();
  }
}
