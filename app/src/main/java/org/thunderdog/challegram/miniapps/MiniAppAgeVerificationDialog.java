/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

/** Layout follows Telegram Android ThemeActivity.verifyAge: 80dp icon and a 48dp primary action. */
public final class MiniAppAgeVerificationDialog {
  public interface Start { void run (Runnable retry, Runnable opened); }

  private MiniAppAgeVerificationDialog () { }

  public static PopupLayout show (ViewController<?> owner, MiniAppAgeVerification verification, Start start) {
    PopupLayout popup = new PopupLayout(owner.context());
    popup.init(true);
    popup.setNeedRootInsets();
    final boolean[] opened = {false};
    popup.setDismissListener(window -> { if (!opened[0]) verification.cancel(); });
    LinearLayout content = new LinearLayout(owner.context());
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Screen.dp(12), 0, Screen.dp(12), Screen.dp(14));
    content.setBackgroundColor(Theme.fillingColor());
    FrameLayout circle = new FrameLayout(owner.context());
    GradientDrawable background = new GradientDrawable();
    background.setShape(GradientDrawable.OVAL);
    background.setColor(Theme.getColor(ColorId.fillingPositive));
    circle.setBackground(background);
    ImageView icon = new ImageView(owner.context());
    icon.setImageResource(R.drawable.miniapp_verify_age);
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    circle.addView(icon, new FrameLayout.LayoutParams(Screen.dp(50), Screen.dp(50), Gravity.CENTER));
    LinearLayout.LayoutParams identity = new LinearLayout.LayoutParams(Screen.dp(80), Screen.dp(80));
    identity.gravity = Gravity.CENTER_HORIZONTAL; identity.topMargin = Screen.dp(20); identity.bottomMargin = Screen.dp(8);
    content.addView(circle, identity);
    TextView title = text(owner, 20);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    title.setText(Lang.getString(R.string.MiniAppAgeVerificationTitle));
    title.setPadding(Screen.dp(24), Screen.dp(8), Screen.dp(24), Screen.dp(8));
    content.addView(title, new LinearLayout.LayoutParams(-1, -2));
    TextView message = text(owner, 14);
    message.setText(Lang.getString("GB".equals(verification.country) ? R.string.MiniAppAgeVerificationTextGB : R.string.MiniAppAgeVerificationText, verification.minAge));
    message.setPadding(Screen.dp(24), 0, Screen.dp(24), 0);
    content.addView(message, new LinearLayout.LayoutParams(-1, -2));
    TextView button = text(owner, 14);
    button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    button.setText(Lang.getString(R.string.MiniAppAgeVerificationButton));
    button.setTextColor(Theme.getColor(ColorId.fillingPositiveContent));
    GradientDrawable action = new GradientDrawable(); action.setCornerRadius(Screen.dp(8)); action.setColor(Theme.getColor(ColorId.fillingPositive));
    button.setBackground(action);
    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, Screen.dp(48));
    actionParams.setMargins(Screen.dp(2), Screen.dp(29), Screen.dp(2), 0);
    content.addView(button, actionParams);
    button.setOnClickListener(v -> {
      button.setEnabled(false); button.setAlpha(.6f);
      start.run(() -> { button.setEnabled(true); button.setAlpha(1f); }, () -> { opened[0] = true; popup.hideWindow(true); });
    });
    ScrollView scroll = new ScrollView(owner.context());
    scroll.setFillViewport(false); scroll.addView(content);
    int width = owner.context().getRootView().getWidth();
    int maxHeight = Math.max(Screen.dp(200), owner.context().getRootView().getHeight() - Screen.getStatusBarHeight());
    scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
    popup.showSimplePopupView(scroll, scroll.getMeasuredHeight());
    return popup;
  }

  private static TextView text (ViewController<?> owner, int size) {
    TextView view = new TextView(owner.context());
    view.setTextSize(size); view.setGravity(Gravity.CENTER); view.setTextColor(Theme.textAccentColor());
    return view;
  }
}
