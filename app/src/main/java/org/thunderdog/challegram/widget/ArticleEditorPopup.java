/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;

/** Anchored menus keep the document, caret and keyboard visible while choosing a tool. */
public final class ArticleEditorPopup {
  private final LinearLayout content;
  private final PopupWindow popup;
  public ArticleEditorPopup (Context context) {
    content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(0, Screen.dp(8), 0, Screen.dp(8));
    android.widget.ScrollView scroll = new android.widget.ScrollView(context); scroll.addView(content); scroll.setFillViewport(false);
    popup = new PopupWindow(scroll, Screen.dp(252), -2, true);
    popup.setBackgroundDrawable(background(Theme.fillingColor(), 16));
    if (android.os.Build.VERSION.SDK_INT >= 21) popup.setElevation(Screen.dp(8)); popup.setOutsideTouchable(true);
    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
  }
  public static GradientDrawable background (int color, int radius) {
    GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(Screen.dp(radius)); return result;
  }
  public static int surfaceColor () { return androidx.core.graphics.ColorUtils.compositeColors(0x18888888, Theme.fillingColor()); }
  public static ImageView icon (Context context, int resource, int description, Runnable action) {
    ImageView view = new ImageView(context); view.setImageResource(resource); view.setColorFilter(Theme.textAccentColor());
    view.setPadding(Screen.dp(10), Screen.dp(10), Screen.dp(10), Screen.dp(10)); view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setContentDescription(Lang.getString(description)); view.setBackground(background(surfaceColor(), 24));
    view.setOnClickListener(v -> action.run()); return view;
  }
  public ArticleEditorPopup item (int icon, int label, Runnable action) {
    LinearLayout row = new LinearLayout(content.getContext()); row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
    if (icon != 0) { ImageView image = icon(content.getContext(), icon, label, action); image.setBackground(null); row.addView(image, new LinearLayout.LayoutParams(Screen.dp(40), Screen.dp(44))); }
    TextView text = new TextView(content.getContext()); text.setText(Lang.getString(label)); text.setTextSize(15); text.setTextColor(Theme.textAccentColor());
    row.addView(text, new LinearLayout.LayoutParams(-1, -2));
    row.setOnClickListener(v -> { popup.dismiss(); action.run(); }); content.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(46))); return this;
  }
  public ArticleEditorPopup icons (int[] icons, int[] labels, java.util.function.IntConsumer action) {
    LinearLayout row = new LinearLayout(content.getContext()); row.setPadding(Screen.dp(6), 0, Screen.dp(6), 0);
    for (int i = 0; i < icons.length; i++) { final int index = i; ImageView image = icon(content.getContext(), icons[i], labels[i], () -> { popup.dismiss(); action.accept(index); }); image.setBackground(null); row.addView(image, new LinearLayout.LayoutParams(0, Screen.dp(42), 1)); }
    content.addView(row); return this;
  }
  public void show (View anchor) {
    content.measure(View.MeasureSpec.makeMeasureSpec(Screen.dp(252), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int[] position = new int[2]; anchor.getLocationOnScreen(position);
    android.graphics.Rect frame = new android.graphics.Rect(); anchor.getWindowVisibleDisplayFrame(frame);
    int height = Math.min(content.getMeasuredHeight(), frame.height() - Screen.dp(16)); popup.setHeight(height);
    int x = Math.max(frame.left + Screen.dp(8), Math.min(position[0], frame.right - Screen.dp(260)));
    int y = position[1] >= height + frame.top ? position[1] - height : Math.min(position[1] + anchor.getHeight(), frame.bottom - height);
    popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, Math.max(frame.top, y));
  }
}
