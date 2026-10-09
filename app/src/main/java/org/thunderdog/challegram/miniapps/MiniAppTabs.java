/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
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
import org.thunderdog.challegram.widget.RootFrameLayout;

import java.util.ArrayList;
import java.util.List;

/** One bottom tab strip per account/activity. Its height is reserved in the underlying navigation. */
public final class MiniAppTabs extends FrameLayout implements RootFrameLayout.InsetsChangeListener {
  private final BaseActivity activity;
  private final Runnable onInsetsChanged;
  private final int originalPadding;
  private final LinearLayout bar;
  private final View stack;
  private final TextView title;
  private final ImageView close, expand;
  private final List<MiniAppController> sessions = new ArrayList<>();
  private PopupLayout overview;
  private boolean attached;

  public MiniAppTabs (BaseActivity activity, Runnable onInsetsChanged) {
    super(activity);
    this.activity = activity;
    this.onInsetsChanged = onInsetsChanged;
    originalPadding = activity.getContentView().getPaddingBottom();
    setLayoutParams(new FrameLayout.LayoutParams(-1, Screen.dp(60), Gravity.BOTTOM));
    stack = new View(activity);
    FrameLayout.LayoutParams stackParams = new FrameLayout.LayoutParams(-1, Screen.dp(47.5f), Gravity.BOTTOM);
    stackParams.leftMargin = stackParams.rightMargin = Screen.dp(14);
    stackParams.bottomMargin = Screen.dp(12);
    addView(stack, stackParams);
    bar = new LinearLayout(activity);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, Screen.dp(50), Gravity.BOTTOM);
    params.leftMargin = params.rightMargin = Screen.dp(4);
    params.bottomMargin = Screen.dp(4);
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
    int height = Screen.dp(sessions.size() > 1 ? 68 : 60);
    getLayoutParams().height = height;
    requestLayout();
    View content = activity.getContentView();
    content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), originalPadding + (visible && !activity.isKeyboardVisible() ? height : 0));
    if (visible) {
      String name = sessions.get(sessions.size() - 1).botName();
      title.setText(name);
      title.setContentDescription(sessions.size() > 1 ? Lang.getString(R.string.MiniAppTabsTitle, name, sessions.size()) : name);
      stack.setVisibility(sessions.size() > 1 ? VISIBLE : GONE);
      refreshTheme();
      if (!attached) {
        activity.getRootView().addInsetsChangeListener(this);
        activity.addToRoot(this, true);
        attached = true;
      }
    } else if (attached) {
      activity.getRootView().removeInsetsChangeListener(this);
      activity.removeFromRoot(this);
      attached = false;
    }
    if (overview != null) overview.hideWindow(false);
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) {
    onInsetsChanged.run();
  }

  public void raiseAboveSheet () {
    if (attached) {
      activity.removeFromRoot(this);
      // BaseActivity keeps this below passcode/protected overlays.
      activity.addToRoot(this, true);
    }
  }

  private void refreshTheme () {
    setBackgroundColor(Theme.backgroundColor());
    GradientDrawable background = new GradientDrawable();
    background.setColor(Theme.fillingColor());
    background.setCornerRadius(Screen.dp(18));
    bar.setBackground(background);
    GradientDrawable stackedBackground = new GradientDrawable();
    stackedBackground.setColor(Theme.fillingColor());
    stackedBackground.setCornerRadius(Screen.dp(18));
    stack.setBackground(stackedBackground);
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
    popup.init(true);
    popup.setNeedRootInsets();
    popup.setOverlayStatusBar(true);
    FrameLayout overlay = new FrameLayout(activity);
    overlay.setBackgroundColor(Theme.backgroundColor());
    View content = activity.getContentView();
    Bitmap backdrop = null;
    if (content.getWidth() > 0 && content.getHeight() > 0) {
      float scale = 240f / content.getWidth();
      backdrop = Bitmap.createBitmap(240, Math.max(1, Math.round(content.getHeight() * scale)), Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(backdrop);
      canvas.scale(scale, scale);
      content.draw(canvas);
      ImageView background = new ImageView(activity);
      background.setScaleType(ImageView.ScaleType.FIT_XY);
      background.setImageBitmap(backdrop);
      background.setAlpha(.35f);
      if (Build.VERSION.SDK_INT >= 31) background.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(Screen.dp(24), Screen.dp(24), android.graphics.Shader.TileMode.CLAMP));
      overlay.addView(background, new FrameLayout.LayoutParams(-1, -1));
    }
    final Bitmap capturedBackground = backdrop;
    popup.setDismissListener(window -> {
      if (overview == window) overview = null;
      if (capturedBackground != null) capturedBackground.recycle();
    });
    ScrollView scroll = new ScrollView(activity);
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setPadding(Screen.dp(36), Math.round(activity.getRootView().getHeight() * .25f), Screen.dp(36), Screen.dp(24));
    LinearLayout cards = new LinearLayout(activity);
    cards.setOrientation(LinearLayout.VERTICAL);
    cards.setClipChildren(false);
    cards.setClipToPadding(false);
    scroll.addView(cards);
    overlay.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    TextView closeAll = title();
    closeAll.setText(Lang.getString(R.string.MiniAppCloseAllTabs));
    closeAll.setTextSize(14);
    closeAll.setGravity(Gravity.CENTER);
    closeAll.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
    org.thunderdog.challegram.support.RippleSupport.setSimpleWhiteBackground(closeAll, ColorId.filling, 16f, null);
    FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(-2, Screen.dp(32), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    closeParams.topMargin = Screen.getStatusBarHeight() + Screen.dp(32);
    overlay.addView(closeAll, closeParams);
    closeAll.setOnClickListener(v -> {
      popup.hideWindow(false);
      List<MiniAppController> closing = new ArrayList<>(sessions);
      Runnable close = () -> { for (MiniAppController controller : closing) controller.close(); };
      if (closing.stream().anyMatch(MiniAppController::needsCloseConfirmation)) {
        new android.app.AlertDialog.Builder(activity).setTitle(Lang.getString(R.string.MiniAppCloseAllTabs))
          .setMessage(Lang.getString(R.string.MiniAppCloseAllConfirm))
          .setNegativeButton(Lang.getString(R.string.Cancel), null)
          .setPositiveButton(Lang.getString(R.string.MiniAppClose), (d, w) -> close.run()).show();
      } else close.run();
    });
    for (int i = 0; i < sessions.size(); i++) {
      MiniAppController controller = sessions.get(i);
      LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable background = new GradientDrawable();
      background.setColor(controller.previewColor());
      background.setCornerRadius(Screen.dp(16));
      card.setBackground(background);
      if (android.os.Build.VERSION.SDK_INT >= 21) card.setClipToOutline(true);
      int cardHeight = Math.round(activity.getRootView().getHeight() * .76f);
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, cardHeight);
      cardParams.bottomMargin = i + 1 < sessions.size() ? -Math.round(cardHeight * .64f) : 0;
      card.setCameraDistance(Screen.dp(1280));
      card.setPivotY(0);
      card.setRotationX(-5f);
      cards.addView(card, cardParams);
      LinearLayout row = new LinearLayout(activity);
      TextView label = title();
      label.setText(controller.botName());
      int color = controller.previewColor();
      int foreground = (.299 * android.graphics.Color.red(color) + .587 * android.graphics.Color.green(color) + .114 * android.graphics.Color.blue(color)) > 150 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
      label.setTextColor(foreground);
      ImageView close = icon(R.drawable.baseline_close_24, R.string.MiniAppClose, v -> { popup.hideWindow(false); controller.requestClose(); });
      close.setColorFilter(foreground);
      row.addView(close);
      row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
      card.addView(row, new LinearLayout.LayoutParams(-1, Screen.dp(50)));
      ImageView thumbnail = new ImageView(activity) {
        @Override protected void onSizeChanged (int w, int h, int oldw, int oldh) {
          super.onSizeChanged(w, h, oldw, oldh);
          Bitmap preview = controller.preview();
          if (preview != null) {
            Matrix matrix = new Matrix();
            matrix.setScale((float) w / preview.getWidth(), (float) w / preview.getWidth());
            setImageMatrix(matrix);
          }
        }
      };
      thumbnail.setScaleType(ImageView.ScaleType.MATRIX);
      thumbnail.setImageBitmap(controller.preview());
      card.addView(thumbnail, new LinearLayout.LayoutParams(-1, 0, 1));
      OnClickListener restore = v -> { popup.hideWindow(false); controller.restore(); };
      label.setOnClickListener(restore);
      thumbnail.setOnClickListener(restore);
    }
    int height = activity.getRootView().getHeight();
    overlay.setLayoutParams(new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM));
    popup.showSimplePopupView(overlay, height);
  }
}
