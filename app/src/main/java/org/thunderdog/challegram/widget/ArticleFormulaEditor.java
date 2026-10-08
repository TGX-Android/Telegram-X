/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.article.ArticleMath;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Telegram's equation sheet: inline preview, a rounded LaTeX field and a full-width Done button. */
public final class ArticleFormulaEditor extends LinearLayout {
  private static final ExecutorService RENDERS = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final String initial;
  private final Consumer<String> whenDone;
  private final EditText expression;
  private final HorizontalScrollView previewScroll;
  private final ImageView preview;
  private final TextView error;
  private final TextView done;
  private Runnable close;
  private Future<?> rendering;
  private int generation;
  private boolean valid, ready, delivered, disposed;
  private final Runnable render = this::renderPreview;

  public ArticleFormulaEditor (Context context, String initial, Consumer<String> whenDone) {
    super(context);
    this.initial = initial == null ? "" : initial;
    this.whenDone = whenDone;
    setOrientation(VERTICAL);
    setPadding(0, Screen.dp(8), 0, 0);
    setBackgroundColor(Theme.backgroundColor());

    FrameLayout previewContainer = new FrameLayout(context);
    preview = new ImageView(context);
    preview.setPadding(Screen.dp(4), Screen.dp(4), Screen.dp(4), Screen.dp(4));
    preview.setBackground(ArticleEditorPopup.background(previewBackground(), 8));
    previewContainer.addView(preview, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    error = new TextView(context);
    error.setText(Lang.getString(R.string.ArticleFormulaError));
    error.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
    error.setTypeface(Typeface.create("serif", Typeface.ITALIC));
    error.setTextColor(Theme.textRedColor());
    error.setPadding(Screen.dp(4), Screen.dp(4), Screen.dp(4), Screen.dp(4));
    error.setBackground(ArticleEditorPopup.background(previewBackground(), 8));
    error.setVisibility(GONE);
    previewContainer.addView(error, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    previewScroll = new HorizontalScrollView(context);
    previewScroll.setHorizontalScrollBarEnabled(false);
    previewScroll.setClipToPadding(false);
    previewScroll.setFillViewport(true);
    previewScroll.setVisibility(GONE);
    previewScroll.addView(previewContainer, new FrameLayout.LayoutParams(-2, -2));
    addView(previewScroll, row(-2, 2, 0));

    expression = new EditText(context);
    expression.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
    expression.setTextColor(Theme.textAccentColor());
    expression.setHintTextColor(Theme.textPlaceholderColor());
    expression.setHint(Lang.getString(R.string.ArticleFormulaEquation));
    expression.setContentDescription(Lang.getString(R.string.ArticleFormulaEquation));
    expression.setSingleLine(false);
    expression.setMaxLines(5);
    expression.setGravity(Gravity.TOP | Gravity.LEFT);
    expression.setPadding(Screen.dp(21), Screen.dp(15), Screen.dp(21), Screen.dp(15));
    expression.setBackground(ArticleEditorPopup.background(Theme.fillingColor(), 24));
    expression.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    expression.setText(this.initial);
    expression.setSelection(expression.length());
    addView(expression, row(-2, 8, 0));

    done = new TextView(context);
    done.setGravity(Gravity.CENTER);
    done.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
    done.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    done.setText(Lang.getString(R.string.Done));
    done.setTextColor(new ColorStateList(new int[][] {new int[] {android.R.attr.state_enabled}, new int[0]}, new int[] {Color.WHITE, 0x80ffffff}));
    done.setBackground(ArticleEditorPopup.background(Theme.getColor(ColorId.fillingPositive), 24));
    done.setOnClickListener(v -> submit());
    addView(done, row(48, 12, 12));
    expression.setOnEditorActionListener((v, action, event) -> {
      if (action == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
      return false;
    });
    expression.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { updatePreview(); }
      @Override public void afterTextChanged (Editable text) { }
    });
    updatePreview();
  }

  private static int previewBackground () {
    return androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.textAccentColor(), 13);
  }
  private static LayoutParams row (int height, int top, int bottom) {
    LayoutParams params = new LayoutParams(-1, height < 0 ? height : Screen.dp(height));
    params.setMargins(Screen.dp(12), Screen.dp(top), Screen.dp(12), Screen.dp(bottom));
    return params;
  }

  private void updatePreview () {
    generation++;
    handler.removeCallbacks(render);
    if (rendering != null) rendering.cancel(false);
    done.setEnabled(false);
    ready = false;
    valid = false;
    if (expression.getText().toString().trim().isEmpty()) {
      // Clearing an existing equation is applied on dismissal, just like in the original editor.
      valid = ready = true;
      previewScroll.setVisibility(GONE);
      return;
    }
    handler.postDelayed(render, 100);
  }

  private void renderPreview () {
    if (disposed) return;
    final int request = generation;
    final String source = expression.getText().toString();
    final Context context = getContext().getApplicationContext();
    rendering = RENDERS.submit(() -> {
      Bitmap image = ArticleMath.render(context, source, Screen.dp(26));
      handler.post(() -> {
        if (disposed || generation != request) return;
        ready = true;
        valid = image != null;
        boolean wasError = error.getVisibility() == VISIBLE;
        preview.setVisibility(valid ? VISIBLE : GONE);
        error.setVisibility(valid ? GONE : VISIBLE);
        if (valid) { preview.setImageBitmap(image); preview.setColorFilter(Theme.textAccentColor()); }
        else if (!wasError) {
          android.view.animation.TranslateAnimation shake = new android.view.animation.TranslateAnimation(-Screen.dp(3), Screen.dp(3), 0, 0);
          shake.setDuration(65); shake.setRepeatCount(3); shake.setRepeatMode(android.view.animation.Animation.REVERSE); error.startAnimation(shake);
        }
        previewScroll.setVisibility(VISIBLE);
        done.setEnabled(valid);
      });
    });
  }

  private void submit () {
    if (disposed || delivered || !ready || !valid || !done.isEnabled()) return;
    delivered = true;
    whenDone.accept(expression.getText().toString());
    if (close != null) close.run();
  }

  private void dismissed () {
    expression.clearFocus();
    Keyboard.hide(expression);
    String source = expression.getText().toString();
    if (!delivered && ready && valid && !initial.equals(source)) {
      delivered = true;
      whenDone.accept(source);
    }
    disposed = true;
    generation++;
    handler.removeCallbacksAndMessages(null);
    if (rendering != null) rendering.cancel(false);
  }

  public static Dialog show (Context context, String initial, Consumer<String> whenDone) {
    Dialog dialog = new Dialog(context, Theme.dialogTheme());
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    ArticleFormulaEditor editor = new ArticleFormulaEditor(context, initial, whenDone);
    editor.close = dialog::dismiss;
    dialog.setContentView(editor);
    dialog.setOnDismissListener(ignored -> editor.dismissed());
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.getDecorView().setPadding(0, 0, 0, 0);
      window.setGravity(Gravity.BOTTOM);
      window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      window.setDimAmount(.2f);
      if (Build.VERSION.SDK_INT >= 21) window.setNavigationBarColor(Theme.backgroundColor());
    }
    dialog.show();
    if (window != null) window.setLayout(-1, -2);
    editor.handler.postDelayed(() -> {
      if (!editor.disposed) { editor.expression.requestFocus(); Keyboard.show(editor.expression); }
    }, 200);
    return dialog;
  }
}
