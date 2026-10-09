/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;
import java.util.List;

/** One bottom tab strip per account/activity. Its height is reserved in the underlying navigation. */
public final class MiniAppTabs extends FrameLayout {
  private final BaseActivity activity;
  private final int originalPadding;
  private final LinearLayout bar;
  private final TextView title;
  private final ImageView close, expand;
  private final List<MiniAppController> sessions = new ArrayList<>();
  private PopupLayout overview;
  private boolean attached;

  public MiniAppTabs (BaseActivity activity) {
    super(activity);
    this.activity = activity;
    originalPadding = activity.getContentView().getPaddingBottom();
    setLayoutParams(new FrameLayout.LayoutParams(-1, Screen.dp(60), Gravity.BOTTOM));
    bar = new LinearLayout(activity);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, Screen.dp(50), Gravity.CENTER);
    params.leftMargin = params.rightMargin = Screen.dp(4);
    addView(bar, params);
    close = icon(R.drawable.baseline_close_24, R.string.MiniAppClose, v -> {
      if (!sessions.isEmpty()) sessions.get(sessions.size() - 1).requestClose();
    });
    bar.addView(close);
    title = title();
    bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
    title.setOnClickListener(v -> open());
    expand = icon(R.drawable.baseline_keyboard_arrow_up_24, R.string.MiniAppOpen, v -> open());
    bar.addView(expand);
  }

  public void update (List<MiniAppController> all) {
    sessions.clear();
    for (MiniAppController controller : all) {
      if (controller.context() == activity && controller.isMinimized()) sessions.add(controller);
    }
    boolean visible = !sessions.isEmpty();
    View content = activity.getContentView();
    content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), originalPadding + (visible ? Screen.dp(60) : 0));
    if (visible) {
      String name = sessions.get(sessions.size() - 1).botName();
      title.setText(sessions.size() > 1 ? Lang.getString(R.string.MiniAppTabsTitle, name, sessions.size()) : name);
      refreshTheme();
      if (!attached) { activity.addToRoot(this, false); attached = true; }
    } else if (attached) {
      activity.removeFromRoot(this);
      attached = false;
    }
    if (overview != null) overview.hideWindow(false);
  }

  private void refreshTheme () {
    setBackgroundColor(Theme.backgroundColor());
    GradientDrawable background = new GradientDrawable();
    background.setColor(Theme.fillingColor());
    background.setCornerRadius(Screen.dp(20));
    bar.setBackground(background);
    title.setTextColor(Theme.textAccentColor());
    close.setColorFilter(Theme.textAccentColor());
    expand.setColorFilter(Theme.textAccentColor());
  }

  private TextView title () {
    TextView text = new TextView(activity);
    text.setTextSize(17);
    text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    text.setSingleLine();
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setGravity(Gravity.CENTER_VERTICAL);
    text.setPadding(Screen.dp(10), 0, Screen.dp(6), 0);
    text.setTextColor(Theme.textAccentColor());
    return text;
  }

  private ImageView icon (int drawable, int description, OnClickListener listener) {
    ImageView image = new ImageView(activity);
    image.setImageResource(drawable);
    image.setScaleType(ImageView.ScaleType.CENTER);
    image.setColorFilter(Theme.textAccentColor());
    image.setContentDescription(Lang.getString(description));
    image.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(50), -1));
    image.setOnClickListener(listener);
    return image;
  }

  private void open () {
    if (sessions.size() == 1) { sessions.get(0).restore(); return; }
    if (sessions.isEmpty() || overview != null) return;
    PopupLayout popup = new PopupLayout(activity);
    overview = popup;
    popup.init(false);
    popup.setNeedRootInsets();
    popup.setDismissListener(window -> { if (overview == window) overview = null; });
    ScrollView scroll = new ScrollView(activity);
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    scroll.setPadding(Screen.dp(12), Screen.dp(16), Screen.dp(12), Screen.dp(12));
    LinearLayout cards = new LinearLayout(activity);
    cards.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(cards);
    for (int i = sessions.size() - 1; i >= 0; i--) {
      MiniAppController controller = sessions.get(i);
      LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable background = new GradientDrawable();
      background.setColor(Theme.fillingColor());
      background.setCornerRadius(Screen.dp(16));
      card.setBackground(background);
      if (android.os.Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, Screen.dp(240));
      cardParams.bottomMargin = Screen.dp(16);
      cards.addView(card, cardParams);
      LinearLayout row = new LinearLayout(activity);
      TextView label = title();
      label.setText(controller.botName());
      row.addView(icon(R.drawable.baseline_close_24, R.string.MiniAppClose, v -> { popup.hideWindow(false); controller.requestClose(); }));
      row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
      card.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(50)));
      ImageView thumbnail = new ImageView(activity);
      thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumbnail.setImageBitmap(controller.preview());
      card.addView(thumbnail, new LinearLayout.LayoutParams(-1, 0, 1));
      OnClickListener restore = v -> { popup.hideWindow(false); controller.restore(); };
      label.setOnClickListener(restore);
      thumbnail.setOnClickListener(restore);
    }
    int height = activity.getVisibleContentHeight() - Screen.getStatusBarHeight() - Screen.dp(24);
    scroll.setLayoutParams(new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM));
    popup.showSimplePopupView(scroll, height);
  }
}
