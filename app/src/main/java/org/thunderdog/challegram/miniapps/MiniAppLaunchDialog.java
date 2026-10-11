/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.AvatarView;
import me.vkryl.core.lambda.RunnableBool;

/** Bot identity, profile link and terms follow Telegram Android's AlertsCreator.createBotLaunchAlert. */
public final class MiniAppLaunchDialog {
  private MiniAppLaunchDialog () { }

  public static AlertDialog show (TdlibDelegate owner, long botId, boolean requestWrite, int action, RunnableBool accepted, Runnable cancelled) {
    final AlertDialog[] dialog = new AlertDialog[1];
    final boolean[] confirmed = {false};
    LinearLayout content = new LinearLayout(owner.context());
    content.setOrientation(LinearLayout.VERTICAL);
    // Telegram's titleless AlertDialog gives custom content a 16dp top inset.
    // Its customViewOffset(6) separates an optional message, not the dialog edge.
    content.setPadding(0, Screen.dp(16), 0, 0);
    FrameLayout identity = new FrameLayout(owner.context());
    content.addView(identity, new LinearLayout.LayoutParams(-1, Screen.dp(48)));
    AvatarView avatar = new AvatarView(owner.context());
    avatar.setUser(owner.tdlib(), botId, false);
    FrameLayout.LayoutParams a = new FrameLayout.LayoutParams(Screen.dp(40), Screen.dp(40), Gravity.START | Gravity.TOP);
    horizontalMargins(a, 22, 22); a.topMargin = Screen.dp(5);
    identity.addView(avatar, a);
    TextView title = text(owner, 20, Theme.textAccentColor());
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    title.setText(owner.tdlib().cache().userName(botId));
    title.setSingleLine(true);
    title.setHorizontalFadingEdgeEnabled(true);
    title.setFadingEdgeLength(Screen.dp(16));
    FrameLayout.LayoutParams t = new FrameLayout.LayoutParams(-1, -2);
    horizontalMargins(t, 76, 21);
    identity.addView(title, t);
    TextView profile = text(owner, 14, Theme.getColor(ColorId.textLink));
    profile.setText(Lang.getString(R.string.MiniAppMoreAboutBot));
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, Screen.dp(24));
    horizontalMargins(p, 76, 21); p.topMargin = Screen.dp(24);
    identity.addView(profile, p);
    profile.setOnClickListener(v -> {
      if (dialog[0] != null) dialog[0].dismiss();
      owner.tdlib().ui().openPrivateProfile(owner, botId, null);
    });
    TextView message = text(owner, 16, Theme.textAccentColor());
    String terms = Lang.getString(R.string.MiniAppTerms);
    SpannableString permission = new SpannableString(Lang.getString(R.string.MiniAppLaunchTerms, terms));
    int start = permission.toString().indexOf(terms);
    if (start >= 0) permission.setSpan(new ClickableSpan() {
      @Override public void onClick (View view) {
        if (dialog[0] != null) dialog[0].dismiss();
        owner.tdlib().ui().openUrl(owner, "https://telegram.org/tos/mini-apps", null);
      }
    }, start, start + terms.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    message.setText(permission);
    message.setLinkTextColor(Theme.getColor(ColorId.textLink));
    message.setMovementMethod(LinkMovementMethod.getInstance());
    message.setPadding(Screen.dp(24), Screen.dp(9), Screen.dp(24), Screen.dp(1));
    content.addView(message, new LinearLayout.LayoutParams(-1, -2));
    CheckBox write = new CheckBox(owner.context());
    if (requestWrite) {
      write.setText(Lang.getString(R.string.MiniAppAllowMessages));
      write.setTextColor(Theme.textAccentColor());
      write.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
      write.setMinHeight(Screen.dp(48));
      write.setPadding(Screen.dp(16), Screen.dp(8), Screen.dp(16), 0);
      // This is a visible opt-in in the native confirmation, never an implicit server grant.
      write.setChecked(true);
      content.addView(write, new LinearLayout.LayoutParams(-1, -2));
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(owner.context()).setView(content)
      .setNegativeButton(Lang.getString(R.string.Cancel), null)
      .setPositiveButton(Lang.getString(action), (d, which) -> {
        confirmed[0] = true;
        accepted.runWithBool(requestWrite && write.isChecked());
      });
    ViewController<?> parent = owner instanceof ViewController ? (ViewController<?>) owner : owner.context().navigation().getCurrentStackItem();
    dialog[0] = parent != null ? parent.showAlert(builder) : builder.show();
    if (dialog[0] != null) dialog[0].setOnDismissListener(d -> {
      avatar.performDestroy();
      if (!confirmed[0] && cancelled != null) cancelled.run();
    });
    else avatar.performDestroy();
    return dialog[0];
  }

  private static void horizontalMargins (FrameLayout.LayoutParams params, int start, int end) {
    params.leftMargin = Screen.dp(Lang.rtl() ? end : start);
    params.rightMargin = Screen.dp(Lang.rtl() ? start : end);
  }

  private static TextView text (TdlibDelegate owner, int size, int color) {
    TextView view = new TextView(owner.context());
    view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    view.setTextColor(color);
    view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    return view;
  }
}
