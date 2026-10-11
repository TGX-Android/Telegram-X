/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/** The official sheet's undimmed, top-right options and Downloads submenu. */
final class MiniAppOptions {
  private final MiniAppController host;
  private final LinearLayout content;
  private final ScrollView scroll;
  private final PopupWindow window;
  private final int textColor;
  private final int iconColor;
  private int preferredWidth = Screen.dp(196);
  private Runnable update;

  MiniAppOptions (MiniAppController host) {
    this.host = host;
    boolean light = androidx.core.graphics.ColorUtils.calculateLuminance(host.previewColor()) >= .721;
    textColor = light ? Color.BLACK : Color.WHITE;
    iconColor = (textColor & 0xffffff) | 0xd9000000;
    content = new LinearLayout(host.context()); content.setOrientation(LinearLayout.VERTICAL);
    scroll = new ScrollView(host.context()); scroll.setFillViewport(true); scroll.addView(content);
    window = new PopupWindow(scroll, Screen.dp(264), -2, true);
    GradientDrawable background = new GradientDrawable(); background.setColor(light ? Color.WHITE : 0xff181819); background.setCornerRadius(Screen.dp(12));
    window.setBackgroundDrawable(background); window.setElevation(Screen.dp(8));
    window.setOutsideTouchable(true); window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    window.setOnDismissListener(() -> { if (update != null) { content.removeCallbacks(update); update = null; } });
  }

  void add (int icon, String label, Runnable action) {
    LinearLayout row = row(48);
    ImageView image = image(icon); row.addView(image, new LinearLayout.LayoutParams(Screen.dp(24), -1));
    TextView text = text(16); text.setText(label); text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1); params.setMarginStart(Screen.dp(19)); row.addView(text, params);
    preferredWidth = Math.max(preferredWidth, (int) Math.ceil(text.getPaint().measureText(label)) + Screen.dp(79));
    row.setOnClickListener(view -> { dismiss(); action.run(); });
  }

  void gap () {
    View line = new View(host.context()); line.setBackgroundColor((textColor & 0xffffff) | 0x0d000000);
    content.addView(line, new LinearLayout.LayoutParams(-1, Screen.dp(8)));
  }

  void downloads (MiniAppActions actions, Runnable back) {
    preferredWidth = Screen.dp(264);
    add(R.drawable.baseline_arrow_back_24, Lang.getString(R.string.MiniAppBack), back); gap();
    java.util.ArrayList<Runnable> refresh = new java.util.ArrayList<>();
    for (MiniAppActions.Download file : actions.downloads()) {
      LinearLayout row = row(56);
      LinearLayout labels = new LinearLayout(host.context()); labels.setOrientation(LinearLayout.VERTICAL); labels.setGravity(Gravity.CENTER_VERTICAL);
      TextView name = text(16); name.setText(file.name); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); labels.addView(name);
      TextView detail = text(13); detail.setTextColor(iconColor); labels.addView(detail);
      row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
      ImageView cancel = image(R.drawable.baseline_close_24); cancel.setContentDescription(Lang.getString(R.string.Cancel)); row.addView(cancel, new LinearLayout.LayoutParams(Screen.dp(32), -1));
      final MiniAppActions.Download[] current = {file};
      refresh.add(() -> {
        current[0] = actions.download(file.id, file.name);
        if (current[0] == null) { row.setVisibility(View.GONE); return; }
        detail.setText(current[0].description()); cancel.setVisibility(current[0].running() ? View.VISIBLE : View.GONE);
      });
      Runnable clicked = () -> {
        if (current[0] == null) return;
        if (current[0].running()) { actions.cancelDownload(file.id); row.setVisibility(View.GONE); }
        else { dismiss(); actions.openDownload(file.id); }
      };
      row.setOnClickListener(view -> clicked.run()); cancel.setOnClickListener(view -> clicked.run());
    }
    update = new Runnable() { @Override public void run () {
      for (Runnable task : refresh) task.run();
      if (window.isShowing()) content.postDelayed(this, 500);
    }};
    for (Runnable task : refresh) task.run();
  }

  private LinearLayout row (int height) {
    LinearLayout row = new LinearLayout(host.context()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Screen.dp(18), 0, Screen.dp(18), 0);
    row.setBackground(new RippleDrawable(ColorStateList.valueOf((textColor & 0xffffff) | 0x19000000), null, new android.graphics.drawable.ColorDrawable(Color.WHITE)));
    row.setFocusable(true); content.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(height))); return row;
  }
  private ImageView image (int resource) {
    ImageView view = new ImageView(host.context()); view.setImageResource(resource); view.setScaleType(ImageView.ScaleType.CENTER); view.setColorFilter(iconColor); return view;
  }
  private TextView text (int size) {
    TextView view = new TextView(host.context()); view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size); view.setTextColor(textColor); view.setGravity(Gravity.CENTER_VERTICAL); return view;
  }
  void show (View anchor) {
    int[] position = new int[2]; anchor.getLocationOnScreen(position);
    Rect screen = new Rect(); anchor.getWindowVisibleDisplayFrame(screen);
    int width = Math.min(preferredWidth, screen.width() - Screen.dp(16)); window.setWidth(width);
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int y = Math.max(screen.top + Screen.dp(8), position[1]);
    int height = Math.min(content.getMeasuredHeight(), screen.bottom - y - Screen.dp(8)); window.setHeight(height);
    int x = Math.max(screen.left + Screen.dp(8), Math.min(position[0] + anchor.getWidth() - width, screen.right - width - Screen.dp(8)));
    window.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, y);
    if (update != null) content.post(update);
  }
  void dismiss () { window.dismiss(); }
}
