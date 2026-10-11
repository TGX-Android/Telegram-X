/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

/** Download progress below the sheet header, retained by DownloadManager when the sheet closes. */
final class MiniAppDownloadBulletin extends LinearLayout implements Runnable {
  private final MiniAppActions actions;
  private final long id;
  private final String name;
  private final Runnable hide;
  private final TextView detail;
  private final ImageView end;
  private MiniAppActions.Download file;
  private long finishedAt;
  private boolean stopped;

  MiniAppDownloadBulletin (MiniAppController host, MiniAppActions actions, long id, String name, Runnable hide) {
    super(host.context()); this.actions = actions; this.id = id; this.name = name; this.hide = hide;
    setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL); setPadding(Screen.dp(12), 0, 0, 0);
    GradientDrawable shape = new GradientDrawable(); shape.setColor(0xf2222222); shape.setCornerRadius(Screen.dp(8)); setBackground(shape); setElevation(Screen.dp(4));
    ImageView icon = new ImageView(getContext()); icon.setImageResource(R.drawable.baseline_file_download_24); icon.setScaleType(ImageView.ScaleType.CENTER); icon.setColorFilter(Color.WHITE); addView(icon, new LayoutParams(Screen.dp(36), -1));
    LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(VERTICAL); labels.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = new TextView(getContext()); title.setText(name); title.setTextSize(14); title.setTextColor(Color.WHITE); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.MIDDLE); labels.addView(title);
    detail = new TextView(getContext()); detail.setTextSize(12); detail.setTextColor(0xffdddddd); labels.addView(detail);
    LayoutParams params = new LayoutParams(0, -1, 1); params.leftMargin = Screen.dp(8); addView(labels, params);
    end = new ImageView(getContext()); end.setImageResource(R.drawable.baseline_close_24); end.setScaleType(ImageView.ScaleType.CENTER); end.setColorFilter(Color.WHITE); end.setContentDescription(Lang.getString(R.string.Cancel)); end.setFocusable(true); addView(end, new LayoutParams(Screen.dp(48), -1));
    end.setOnClickListener(view -> { if (file != null && file.running()) actions.cancelDownload(id); hide.run(); });
    setOnClickListener(view -> { if (file != null && !file.running()) { actions.openDownload(id); hide.run(); } else actions.showDownloads(); });
  }
  @Override protected void onAttachedToWindow () { super.onAttachedToWindow(); run(); }
  @Override protected void onDetachedFromWindow () { stop(); super.onDetachedFromWindow(); }
  void stop () { stopped = true; removeCallbacks(this); }
  @Override public void run () {
    if (stopped) return;
    file = actions.download(id, name);
    if (file == null) { hide.run(); return; }
    if (!file.running() && finishedAt == 0) finishedAt = SystemClock.uptimeMillis();
    detail.setText(!file.running() && file.status == android.app.DownloadManager.STATUS_SUCCESSFUL ? Lang.getString(R.string.MiniAppDownloadSaved) + " · " + file.description() : file.description());
    end.setContentDescription(Lang.getString(file.running() ? R.string.Cancel : R.string.MiniAppClose));
    if (finishedAt != 0 && SystemClock.uptimeMillis() - finishedAt > 4500) { hide.run(); return; }
    postDelayed(this, 500);
  }
}
