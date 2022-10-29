package org.thunderdog.challegram.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.support.RectDrawable;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.CustomTextView;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.TextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public class DoubleTapConfirmationView extends FrameLayoutFix
  implements PopupLayout.AnimatedPopupProvider, FactorAnimator.Target, PopupLayout.DismissListener {
  private PopupLayout popupLayout;
  private Runnable onConfirm;

  private final BoolAnimator visibilityAnimator = new BoolAnimator(
    0,
    this,
    AnimatorUtils.LINEAR_INTERPOLATOR,
    180l
  );

  public DoubleTapConfirmationView (@NonNull Context context) {
    super(context);
  }

  public void init (Tdlib tdlib, String text, int textWidth) {
    setLayoutParams(new FrameLayoutFix.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.CENTER)
    );
    setOnClickListener(view -> {
      if (onConfirm != null) {
        onConfirm.run();
      }
      popupLayout.hideWindow(true);
    });
    Drawable background = new RectDrawable(R.id.theme_color_filling, 10f, 0f, false);
    setBackground(background);
    setAlpha(0f);

    LinearLayout wrapper = new LinearLayout(getContext());
    wrapper.setLayoutParams(new FrameLayoutFix.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.CENTER)
    );
    wrapper.setPadding(Screen.dp(20f), Screen.dp(20f), Screen.dp(20f), Screen.dp(20f));
    wrapper.setOrientation(LinearLayout.VERTICAL);
    wrapper.setHorizontalGravity(Gravity.CENTER);
    RippleSupport.setTransparentSelector(wrapper);

    TextView textView = new TextView(getContext());
    textView.setLayoutParams(new LinearLayout.LayoutParams(textWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    textView.setText(text);
    textView.setGravity(Gravity.CENTER_HORIZONTAL);
    textView.setTextColor(Theme.textAccentColor());
    textView.setTextSize(14f);

    ImageView imageView = new ImageView(getContext());
    imageView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(50f), Screen.dp(50f)));
    imageView.setImageResource(R.drawable.baseline_fingerprint_24);
    imageView.setColorFilter(Theme.iconColor());
    imageView.setPadding(Screen.dp(0f), Screen.dp(20f), Screen.dp(0f), Screen.dp(0f));

    wrapper.addView(textView);
    wrapper.addView(imageView);

    addView(wrapper);
  }

  public void setOnConfirm (Runnable onConfirm) {
    this.onConfirm = onConfirm;
  }

  public void show () {
    popupLayout = new PopupLayout(getContext());
    popupLayout.init(false);
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(true);
    popupLayout.setDismissListener(popup -> {
      if (this.popupLayout == popup) {
        this.popupLayout = null;
      }
    });
    popupLayout.showAnimatedPopupView(this, this);
  }

  public void hide () {
    if (this.popupLayout != null) {
      this.popupLayout.hideWindow(true);
      this.popupLayout = null;
    }
  }

  @Override
  public void onPopupDismiss (PopupLayout popup) {
    int a = 0;
  }

  @Override
  public void prepareShowAnimation () {
  }

  @Override
  public void launchShowAnimation (PopupLayout popup) {
    visibilityAnimator.setValue(true, true);
  }

  @Override
  public boolean launchHideAnimation (PopupLayout popup, FactorAnimator originalAnimator) {
    visibilityAnimator.setValue(false, true);
    return true;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setAlpha(factor);
    setScaleX(factor);
    setScaleY(factor);
    popupLayout.onFactorChanged(id, factor, fraction, callee);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      popupLayout.onCustomShowComplete();
    } else {
      popupLayout.onCustomHideAnimationComplete();
    }
  }
}
