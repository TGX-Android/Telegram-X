/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.article.ArticleAiResult;
import org.thunderdog.challegram.data.article.ArticleDocument;
import org.thunderdog.challegram.data.article.ArticleRichText;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.util.function.Predicate;

/** AI operates on a detached selection; only Apply changes the article. */
public final class ArticleAiEditor extends LinearLayout {
  private final Tdlib tdlib;
  private final TdApi.InputRichMessage source;
  private final Predicate<ArticleDocument> apply;
  private final boolean create;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final LinearLayout options, styles;
  private final TextView preview, done;
  private final ScrollView previewScroll;
  private final EditText prompt;
  private final CheckBox emojify;
  private final TextView language;
  private final TextView[] tabs = new TextView[3];
  private String languageCode = Lang.locale().getLanguage(), styleName = "";
  private int tab = 1, generation;
  private boolean disposed, busy;
  private ArticleDocument result;
  private Runnable close;

  private ArticleAiEditor (Context context, Tdlib tdlib, TdApi.InputRichMessage source, boolean create, Predicate<ArticleDocument> apply) {
    super(context); this.tdlib = tdlib; this.source = source; this.create = create; this.apply = apply;
    setOrientation(VERTICAL); setPadding(Screen.dp(12), Screen.dp(12), Screen.dp(12), Screen.dp(12));
    setBackground(ArticleEditorPopup.background(Theme.backgroundColor(), 24));
    LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = label(Lang.getString(create ? R.string.ArticleAiCreate : R.string.ArticleAi), 22); title.setTypeface(null, android.graphics.Typeface.BOLD);
    header.addView(title, new LayoutParams(0, Screen.dp(48), 1));
    ImageView cancel = ArticleEditorPopup.icon(context, R.drawable.baseline_close_24, R.string.Cancel, () -> close.run());
    header.addView(cancel, new LayoutParams(Screen.dp(48), Screen.dp(48))); addView(header);
    if (!create) {
      LinearLayout modes = new LinearLayout(context); modes.setPadding(Screen.dp(4), Screen.dp(4), Screen.dp(4), Screen.dp(4)); modes.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 28));
      int[] labels = {R.string.ArticleAiTranslate, R.string.ArticleAiStyle, R.string.ArticleAiFixTab};
      int[] icons = {R.drawable.article_outline_ai_translate2, R.drawable.article_menu_rewrite, R.drawable.article_menu_proofread};
      for (int i = 0; i < 3; i++) { final int mode = i; tabs[i] = chip(Lang.getString(labels[i]), icons[i], () -> selectTab(mode)); modes.addView(tabs[i], new LayoutParams(0, Screen.dp(52), 1)); }
      addView(modes, row(-2, 8));
    }
    options = new LinearLayout(context); options.setOrientation(VERTICAL); options.setPadding(Screen.dp(8), Screen.dp(8), Screen.dp(8), Screen.dp(8));
    options.setBackground(ArticleEditorPopup.background(ArticleEditorPopup.surfaceColor(), 24)); addView(options, row(-2, 10));
    HorizontalScrollView styleScroll = new HorizontalScrollView(context); styleScroll.setHorizontalScrollBarEnabled(false);
    styles = new LinearLayout(context); styleScroll.addView(styles); options.addView(styleScroll);
    prompt = new EditText(context); prompt.setTextColor(Theme.textAccentColor()); prompt.setHintTextColor(Theme.textDecentColor()); prompt.setTextSize(16); prompt.setHint(Lang.getString(R.string.ArticleAiPrompt)); prompt.setMaxLines(4);
    options.addView(prompt, new LayoutParams(-1, -2));
    language = label("", 16); language.setOnClickListener(v -> languagePicker()); options.addView(language, new LayoutParams(-1, Screen.dp(40)));
    emojify = new CheckBox(context); emojify.setText(Lang.getString(R.string.ArticleAiEmojify)); emojify.setTextColor(Theme.textAccentColor());
    emojify.setOnCheckedChangeListener((v, checked) -> { if (!create) request(); }); options.addView(emojify);
    preview = label("", 16); preview.setPadding(Screen.dp(12), Screen.dp(12), Screen.dp(12), Screen.dp(12));
    previewScroll = new ScrollView(context); previewScroll.addView(preview); addView(previewScroll, row(Screen.dp(130), 8));
    done = label("", 16); done.setGravity(Gravity.CENTER); done.setTextColor(Color.WHITE); done.setTypeface(null, android.graphics.Typeface.BOLD); done.setBackground(ArticleEditorPopup.background(Theme.textLinkColor(), 24));
    addView(done, row(Screen.dp(48), 12));
    done.setOnClickListener(v -> { if (busy) return; if (result == null) request(); else if (apply.test(result)) close.run(); });
    prompt.addTextChangedListener(new TextWatcher() {
      @Override public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
      @Override public void onTextChanged (CharSequence s, int start, int before, int count) { generation++; busy = false; result = null; updateDone(); }
      @Override public void afterTextChanged (Editable s) { }
    });
    rebuildStyles(); updateMode(); updateDone();
  }
  private LayoutParams row (int height, int top) { LayoutParams params = new LayoutParams(-1, height); params.topMargin = Screen.dp(top); return params; }
  private TextView label (String text, int size) { TextView label = new TextView(getContext()); label.setText(text); label.setTextSize(size); label.setTextColor(Theme.textAccentColor()); label.setGravity(Gravity.CENTER_VERTICAL); return label; }
  private TextView chip (String title, int resource, Runnable action) {
    TextView view = label(title, 12); view.setGravity(Gravity.CENTER); view.setPadding(Screen.dp(8), Screen.dp(4), Screen.dp(8), Screen.dp(4));
    if (resource != 0) {
      android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(getContext(), resource).mutate(); androidx.core.graphics.drawable.DrawableCompat.setTint(icon, Theme.textAccentColor()); icon.setBounds(0, 0, Screen.dp(24), Screen.dp(24)); view.setCompoundDrawables(null, icon, null, null);
    }
    view.setOnClickListener(v -> action.run()); return view;
  }
  private void rebuildStyles () {
    styles.removeAllViews();
    addStyle(Lang.getString(R.string.ArticleAiPromptTab), R.drawable.article_iv_prompt, "", () -> { styleName = ""; prompt.setVisibility(VISIBLE); result = null; updateDone(); });
    addStyle(Lang.getString(R.string.ArticleAiCreateStyle), R.drawable.article_tone_create, null, this::createStyle);
    for (TdApi.TextCompositionStyle style : tdlib.textCompositionStyles()) addStyle(style.title, 0, style.name, () -> { styleName = style.name; prompt.setVisibility(GONE); request(); });
  }
  private void addStyle (String title, int icon, String name, Runnable action) {
    TextView item = chip(title, icon, () -> { action.run(); markStyle(); }); item.setTag(name); styles.addView(item, new LayoutParams(-2, Screen.dp(56)));
  }
  private void markStyle () { for (int i = 0; i < styles.getChildCount(); i++) { View view = styles.getChildAt(i); boolean selected = styleName.equals(view.getTag()); view.setSelected(selected); view.setBackground(selected ? ArticleEditorPopup.background(0x203399ff, 20) : null); } }
  private void selectTab (int value) { if (tab == value) return; tab = value; generation++; busy = false; result = null; updateMode(); if (tab != 1 || !styleName.isEmpty()) request(); else updateDone(); }
  private void updateMode () {
    for (int i = 0; i < tabs.length; i++) if (tabs[i] != null) { tabs[i].setSelected(tab == i); tabs[i].setTextColor(tab == i ? Theme.textLinkColor() : Theme.textAccentColor()); tabs[i].setBackground(tab == i ? ArticleEditorPopup.background(0x203399ff, 26) : null); }
    ((View) styles.getParent()).setVisibility(!create && tab == 1 ? VISIBLE : GONE);
    prompt.setVisibility(create || tab == 1 && styleName.isEmpty() ? VISIBLE : GONE);
    language.setVisibility(!create && tab == 0 ? VISIBLE : GONE);
    language.setText(Lang.getString(R.string.ArticleAiLanguageMenu, new java.util.Locale(languageCode).getDisplayLanguage(Lang.locale())));
    emojify.setVisibility(!create && tab != 2 ? VISIBLE : GONE);
    if (source != null) preview.setText(plainPreview(source));
    else previewScroll.setVisibility(GONE);
    markStyle();
  }
  private static String plainPreview (TdApi.InputRichMessage message) {
    StringBuilder text = new StringBuilder();
    for (org.thunderdog.challegram.data.article.ArticleEditorTree.Entry entry : org.thunderdog.challegram.data.article.ArticleEditorTree.entries(message))
      for (org.thunderdog.challegram.data.article.ArticleEditorTree.TextField field : org.thunderdog.challegram.data.article.ArticleEditorTree.fields(entry.block)) {
        String part = ArticleRichText.plain(field.value); if (!part.isEmpty()) { if (text.length() > 0) text.append('\n'); text.append(part); }
      }
    return text.toString();
  }
  private void updateDone () {
    done.setText(Lang.getString(busy ? R.string.ArticleAiWorking : result != null ? create ? R.string.ArticleAiAddToPage : R.string.Apply : R.string.ArticleAiGenerate));
    done.setEnabled(!busy && (result != null || !create && (tab != 1 || !styleName.isEmpty()) || !prompt.getText().toString().trim().isEmpty()));
    done.setAlpha(done.isEnabled() ? 1f : .5f);
  }
  private void request () {
    String instructions = prompt.getText().toString().trim();
    if ((create || tab == 1 && styleName.isEmpty()) && instructions.isEmpty()) { result = null; updateDone(); return; }
    int current = ++generation; busy = true; result = null; updateDone();
    TdApi.Function<TdApi.RichMessage> request = create ? new TdApi.CreateRichMessageWithAi(instructions, Lang.locale().getLanguage(), false) : tab == 2 ? new TdApi.FixRichMessageWithAi(source) : new TdApi.ComposeRichMessageWithAi(source, tab == 0 ? languageCode : "", tab == 1 ? styleName : "", tab == 1 && styleName.isEmpty() ? instructions : "", emojify.isChecked());
    tdlib.send(request, (response, error) -> handler.post(() -> {
      if (disposed || current != generation) return;
      busy = false;
      if (error != null) UI.showError(error);
      else try { result = ArticleAiResult.toDocument(response); preview.setText(plainPreview(result.toInput())); previewScroll.setVisibility(VISIBLE); }
      catch (IllegalArgumentException | IllegalStateException invalid) { UI.showToast(R.string.ArticleAiFailed, android.widget.Toast.LENGTH_LONG); }
      updateDone();
    }));
  }
  private void languagePicker () {
    String[] codes = {"en", "ru", "uk", "de", "fr", "es", "it", "pt", "pl", "nl", "tr", "ar", "he", "fa", "hi", "id", "ja", "ko", "zh"};
    String[] names = new String[codes.length]; int selected = -1;
    for (int i = 0; i < codes.length; i++) { names[i] = new java.util.Locale(codes[i]).getDisplayLanguage(Lang.locale()); if (codes[i].equals(languageCode)) selected = i; }
    new AlertDialog.Builder(getContext(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleLanguage)).setSingleChoiceItems(names, selected, (dialog, which) -> { languageCode = codes[which]; dialog.dismiss(); updateMode(); request(); }).setNegativeButton(R.string.Cancel, null).show();
  }
  private void createStyle () {
    LinearLayout form = new LinearLayout(getContext()); form.setOrientation(VERTICAL); form.setPadding(Screen.dp(20), 0, Screen.dp(20), 0);
    EditText title = new EditText(getContext()), description = new EditText(getContext()); title.setHint(Lang.getString(R.string.ArticleAiStyleTitle)); description.setHint(Lang.getString(R.string.ArticleAiPrompt)); form.addView(title); form.addView(description);
    new AlertDialog.Builder(getContext(), Theme.dialogTheme()).setTitle(Lang.getString(R.string.ArticleAiCreateStyle)).setView(form).setNegativeButton(R.string.Cancel, null).setPositiveButton(R.string.ArticleAiCreateStyle, (dialog, which) -> {
      if (title.getText().toString().trim().isEmpty() || description.getText().toString().trim().isEmpty()) return;
      tdlib.send(new TdApi.CreateTextCompositionStyle(title.getText().toString().trim(), 0, description.getText().toString().trim(), false), (style, error) -> handler.post(() -> {
        if (disposed) return; if (error != null) { UI.showError(error); return; }
        rebuildStyles(); styleName = style.name; addStyle(style.title, 0, style.name, () -> { styleName = style.name; prompt.setVisibility(GONE); request(); }); updateMode(); request();
      }));
    }).show();
  }
  public static Dialog show (Context context, Tdlib tdlib, TdApi.InputRichMessage source, boolean create, Predicate<ArticleDocument> apply) {
    Dialog dialog = new Dialog(context, Theme.dialogTheme()); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    ArticleAiEditor view = new ArticleAiEditor(context, tdlib, source, create, apply); view.close = dialog::dismiss; dialog.setContentView(view);
    dialog.setOnDismissListener(ignored -> { view.disposed = true; view.generation++; view.handler.removeCallbacksAndMessages(null); org.thunderdog.challegram.tool.Keyboard.hide(view.prompt); });
    Window window = dialog.getWindow();
    if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.getDecorView().setPadding(0, 0, 0, 0); window.setGravity(Gravity.BOTTOM); window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); window.setDimAmount(.2f); }
    dialog.show(); if (window != null) window.setLayout(-1, -2); return dialog;
  }
}
