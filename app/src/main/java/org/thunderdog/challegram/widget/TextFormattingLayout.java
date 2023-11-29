/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 22/07/2023
 */
package org.thunderdog.challegram.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.charts.LayoutHelper;
import org.thunderdog.challegram.component.chat.InputView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TranslationsManager;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.TranslationControllerV2;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.TextSelection;
import org.thunderdog.challegram.util.TranslationCounterDrawable;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

@SuppressLint("ViewConstructor")
public class TextFormattingLayout extends FrameLayout implements TranslationsManager.Translatable {
  private static final int MIN_PADDING_BETWEEN_BUTTONS = 4;
  private static final int MAX_BUTTON_SIZE = 40;
  private static final int PADDING = 15;

  public static final int FLAG_BOLD = 1;
  public static final int FLAG_ITALIC = 1 << 1;
  private static final int FLAG_MONOSPACE = 1 << 2;
  private static final int FLAG_UNDERLINE = 1 << 3;
  private static final int FLAG_STRIKETHROUGH = 1 << 4;
  private static final int FLAG_LINK = 1 << 5;
  private static final int FLAG_SPOILER = 1 << 6;
  private static final int FLAG_BLOCK_QUOTE = 1 << 7;
  private static final int FLAG_CLEAR = 1 << 30;

  private static final int[] buttonIds = new int[]{
    R.id.btn_bold, R.id.btn_italic, R.id.btn_monospace, R.id.btn_underline,
    R.id.btn_strikethrough, R.id.btn_link, R.id.btn_spoiler,
    android.R.id.cut, android.R.id.copy, android.R.id.paste, R.id.btn_translate
  };

  private static final int[] buttonIcons = new int[]{
    R.drawable.baseline_format_bold_24, R.drawable.baseline_format_italic_24,
    R.drawable.baseline_code_24, R.drawable.baseline_format_underlined_24,
    R.drawable.baseline_strikethrough_s_24, R.drawable.baseline_link_24,
    R.drawable.baseline_eye_off_24, R.drawable.baseline_content_cut_24,
    R.drawable.baseline_content_copy_24, R.drawable.baseline_content_paste_24,
    R.drawable.baseline_translate_24
  };

  private final @NonNull ViewController<?> parent;
  private final TextView editHeader;
  private final TextView translateHeader;
  private final TextView clearButton;
  private final Button[] buttons;
  private final LangSelector langSelector;
  private final InputView inputView;
  private final BoolAnimator clearButtonIsEnabled;

  private final TranslationsManager translationsManager;
  private final TranslationCounterDrawable translationCounterDrawable;
  private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
    @Override
    public boolean needClickAt (View view, float x, float y) {
      return true;
    }

    @Override
    public void onClickAt (View view, float x, float y) {
      if (inputView.isEmpty()) {
        closeTextFormattingKeyboard();
        return;
      }
      closeTextFormattingKeyboardDelay(isSelectionEmpty());
    }

    @Override
    public void onClickTouchDown (View view, float x, float y) {
      closeTextFormattingKeyboardDelay(false);
    }
  });

  private TdApi.FormattedText originalTextToTranslate;
  private boolean ignoreSelectionChangesForTranslatedText;
  private String languageToTranslate;
  private Delegate delegate;

  public interface Delegate {
    void onWantsCloseTextFormattingKeyboard ();
  }

  public TextFormattingLayout (@NonNull Context context, @NonNull ViewController<?> parent, InputView inputView) {
    super(context);
    this.parent = parent;
    this.inputView = inputView;

    ViewSupport.setThemedBackground(this, ColorId.background, parent);
    setPadding(Screen.dp(PADDING), 0, Screen.dp(PADDING), 0);

    addView(createHeader(context, parent, Lang.getString(R.string.TextFormatting)));
    addView(editHeader = createHeader(context, parent, Lang.getString(R.string.TextFormattingTools)));
    addView(translateHeader = createHeader(context, parent, Lang.getString(R.string.TextFormattingTranslate)));

    clearButton = createHeader(context, parent, Lang.getString(R.string.Clear));
    clearButton.setOnClickListener(this::onButtonClick);
    clearButton.setTypeface(Fonts.getRobotoRegular());
    clearButton.setId(R.id.btn_plain);
    addView(clearButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));

    clearButtonIsEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> clearButton.setAlpha(factor),
      AnimatorUtils.DECELERATE_INTERPOLATOR, 200L, true);

    buttons = new Button[buttonIds.length];
    for (int a = 0; a < buttonIds.length; a++) {
      Button button = new Button(context);
      button.setId(buttonIds[a]);
      button.setDrawable(buttonIcons[a]);
      button.setBackground(a < 7, parent);
      button.setOnClickListener(this::onButtonClick);
      addView(buttons[a] = button);
    }

    langSelector = new LangSelector(context, parent);
    langSelector.setOnClickListener(this::onClickLanguageSelector);
    addView(langSelector);

    CircleButton circleButton = new CircleButton(getContext());
    circleButton.setId(R.id.btn_circleBackspace);
    circleButton.init(R.drawable.baseline_backspace_24, -Screen.dp(1.5f), 46f, 4f, ColorId.circleButtonOverlay, ColorId.circleButtonOverlayIcon);
    circleButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(54), Screen.dp(54), Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, Screen.dp(12f)));
    circleButton.setOnClickListener(this::onButtonClick);
    parent.addThemeInvalidateListener(circleButton);
    addView(circleButton);

    translationsManager = new TranslationsManager(parent.tdlib(), this, this::setTranslatedStatus, this::setTranslationResult, this::setTranslationError);
    translationCounterDrawable = new TranslationCounterDrawable(Drawables.get(R.drawable.baseline_translate_24));
    translationCounterDrawable.setColors(ColorId.icon, ColorId.background ,ColorId.iconActive);
    translationCounterDrawable.setInvalidateCallback(() -> buttons[10].invalidate());
    buttons[10].setDrawable(translationCounterDrawable);
    buttons[10].setNeedDrawWithoutRepainting(true);

    setLanguageToTranslate(Settings.instance().getDefaultLanguageForTranslateDraft());
  }

  public void onInputViewSelectionChanged (int start, int end) {
    stopTranslationIfNeeded();
    if (start != end) {
      closeTextFormattingKeyboardDelay(false);
    }
    checkButtonsActive(true);
  }

  public void onInputViewSpansChanged () {
    stopTranslationIfNeeded();
  }

  public void onInputViewTouchEvent (MotionEvent event) {
    clickHelper.onTouchEvent(inputView, event);
  }

  public void setDelegate (Delegate delegate) {
    this.delegate = delegate;
  }

  public void checkButtonsActive (boolean animated) {
    final boolean isEmpty = isSelectionEmpty();
    final int flags = checkSpans();

    for (int a = 0; a < 11; a++) {
      if (a < 7) buttons[a].setIsActive(BitwiseUtils.hasFlag(flags, 1 << a), animated);
      if (a != 9) buttons[a].setIsEnabled(!isEmpty, animated);
    }
    langSelector.setIsEnabled(!isEmpty, animated);
    clearButtonIsEnabled.setValue(BitwiseUtils.hasFlag(flags, FLAG_CLEAR), animated);
  }

  private void stopTranslationIfNeeded () {
    if (!ignoreSelectionChangesForTranslatedText && originalTextToTranslate != null) {
      originalTextToTranslate = null;
      translationsManager.stopTranslation();
    }
  }

  private void setLanguageToTranslate (String language) {
    Settings.instance().setDefaultLanguageForTranslateDraft(languageToTranslate = language);
    langSelector.setText(Lang.getLanguageName(language, Lang.getString(R.string.TranslateLangUnknown)));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    final int width = MeasureSpec.getSize(widthMeasureSpec);

    final int button_size_px = Math.min((width - Screen.dp(PADDING * 2 + MIN_PADDING_BETWEEN_BUTTONS * 6)) / 7, Screen.dp(MAX_BUTTON_SIZE));
    final float padding = Math.max(Screen.dp(MIN_PADDING_BETWEEN_BUTTONS), (width - Screen.dp(PADDING * 2) - 7 * button_size_px) / 6f);

    ViewGroup.MarginLayoutParams params;

    params = (ViewGroup.MarginLayoutParams) editHeader.getLayoutParams();
    params.topMargin = Screen.dp(46) + button_size_px;

    params = (ViewGroup.MarginLayoutParams) translateHeader.getLayoutParams();
    params.leftMargin = (int) ((padding + button_size_px) * 3);
    params.topMargin = Screen.dp(46) + button_size_px;

    params = (ViewGroup.MarginLayoutParams) langSelector.getLayoutParams();
    params.topMargin = Screen.dp(92) + button_size_px;
    params.leftMargin = (int) ((padding + button_size_px) * 3);
    params.height = button_size_px;
    params.width = (int) (button_size_px * 3 + padding * 2);

    for (int a = 0; a < buttonIds.length; a++) {
      params = (ViewGroup.MarginLayoutParams) buttons[a].getLayoutParams();
      params.width = params.height = button_size_px;
      if (a < 7) {
        params.leftMargin = (int) ((padding + button_size_px) * a);
        params.topMargin = Screen.dp(46);
      } else if (a < 10) {
        params.leftMargin = (int) ((padding + button_size_px) * (a - 7));
        params.topMargin = Screen.dp(92) + button_size_px;
      } else {
        params.leftMargin = (int) ((padding + button_size_px) * 6);
        params.topMargin = Screen.dp(92) + button_size_px;
      }
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return true;
  }

  private void onButtonClick (View v) {
    int id = v.getId();

    if (id == R.id.btn_circleBackspace) {
      inputView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
    } else if (id == R.id.btn_translate) {
      startTranslate();
    } else if (id == android.R.id.copy || id == android.R.id.cut || id == android.R.id.paste) {
      inputView.onTextContextMenuItem(id);
    } else {
      setOrRemoveSpan(id);
    }
  }

  private void setOrRemoveSpan (int id) {
    if (id == R.id.btn_plain) {
      inputView.setSpan(id);
    } else {
      int flag = getTypeFlagFromButtonId(id);
      if (flag == -1) return;

      int flags = checkSpans();
      if (BitwiseUtils.hasFlag(flags, flag)) {
        inputView.removeSpan(getEntityTypeFromTypeFlag(flag));
      } else {
        inputView.setSpan(id);
      }
    }

    checkButtonsActive(true);
  }

  private void onClickLanguageSelector (View v) {
    int[] cords = Views.getLocationOnScreen(langSelector);
    int x = langSelector.getMeasuredWidth() + cords[0] - Screen.dp(TranslationControllerV2.LanguageSelectorPopup.WIDTH + TranslationControllerV2.LanguageSelectorPopup.PADDING * 2);
    int y = langSelector.getMeasuredHeight() +  cords[1] - Screen.dp(TranslationControllerV2.LanguageSelectorPopup.HEIGHT + TranslationControllerV2.LanguageSelectorPopup.PADDING * 2);

    TranslationControllerV2.LanguageSelectorPopup languagePopupLayout = new TranslationControllerV2.LanguageSelectorPopup(v.getContext(), parent, this::setLanguageToTranslate, languageToTranslate, null);

    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) languagePopupLayout.languageRecyclerWrap.getLayoutParams();
    params.gravity = Gravity.TOP | Gravity.LEFT;

    languagePopupLayout.languageRecyclerWrap.setTranslationX(x);
    languagePopupLayout.languageRecyclerWrap.setTranslationY(y);
    languagePopupLayout.show();
    languagePopupLayout.languageRecyclerWrap.setPivotX(Screen.dp(TranslationControllerV2.LanguageSelectorPopup.WIDTH + TranslationControllerV2.LanguageSelectorPopup.PADDING));
    languagePopupLayout.languageRecyclerWrap.setPivotY(Screen.dp(TranslationControllerV2.LanguageSelectorPopup.HEIGHT + TranslationControllerV2.LanguageSelectorPopup.PADDING));

    inputView.hideSelectionCursors();
  }

  private void startTranslate () {
    TextSelection selection = inputView.getTextSelection();
    if (selection == null || selection.isEmpty()) return;

    TdApi.FormattedText text = Td.substring(inputView.getOutputText(false), selection.start, selection.end);
    if (!StringUtils.equalsOrBothEmpty(translationsManager.getCurrentTranslatedLanguage(), languageToTranslate)) {
      if (originalTextToTranslate == null) {
        originalTextToTranslate = text;
      }
      translationsManager.requestTranslation(languageToTranslate);
    } else {
      translationsManager.stopTranslation();
    }
  }

  private boolean isSelectionEmpty () {
    TextSelection selection = inputView.getTextSelection();
    return selection == null || selection.isEmpty();
  }

  private int checkSpans () {
    TextSelection selection = inputView.getTextSelection();
    if (selection == null || selection.isEmpty()) {
      return 0;
    }

    return checkSpans(inputView.getOutputText(false), selection.start, selection.end);
  }


  private Runnable closeTextFormattingKeyboardRunnable;

  private void closeTextFormattingKeyboardDelay (boolean needClose) {
    if (closeTextFormattingKeyboardRunnable != null) {
      UI.cancel(closeTextFormattingKeyboardRunnable);
    }
    if (needClose) {
      UI.post(closeTextFormattingKeyboardRunnable = this::closeTextFormattingKeyboard, ViewConfiguration.getDoubleTapTimeout() + 50);
    }
  }

  private void closeTextFormattingKeyboard () {
    closeTextFormattingKeyboardRunnable = null;
    if (delegate != null) {
      delegate.onWantsCloseTextFormattingKeyboard();
    }
  }

  /* * */

  private void setTranslatedStatus (int status, boolean animated) {
    translationCounterDrawable.setStatus(status, animated);
  }

  private void setTranslationResult (TdApi.FormattedText translationResult) {
    ignoreSelectionChangesForTranslatedText = true;
    if (translationResult != null) {
      inputView.paste(translationResult, true);
    } else if (originalTextToTranslate != null) {
      inputView.paste(originalTextToTranslate, true);
    }

    ignoreSelectionChangesForTranslatedText = false;
  }

  private void setTranslationError (String string) {

  }

  @Nullable
  @Override
  public String getOriginalMessageLanguage () {
    return null;
  }

  @Override
  public TdApi.FormattedText getTextToTranslate () {
    return originalTextToTranslate;
  }

  /* * */

  private static int checkSpans (TdApi.FormattedText text, int start, int end) {
    if (text == null || text.entities == null) return 0;
    int flags = 0;

    for (TdApi.TextEntity entity : text.entities) {
      final int entityStart = entity.offset, entityEnd = entity.offset + entity.length;

      if (!(entityStart >= end || start >= entityEnd)) {
        int flag = getTypeFlagFromEntityType(entity.type);
        if (flag == -1) continue;

        flags = BitwiseUtils.setFlag(flags, FLAG_CLEAR, true);
        if (entityStart <= start && entityEnd >= end) {
          flags = BitwiseUtils.setFlag(flags, flag, true);
        }
      }
    }

    return flags;
  }

  private static int getTypeFlagFromButtonId (@IdRes int id) {
    if (id == R.id.btn_bold) {
      return FLAG_BOLD;
    } else if (id == R.id.btn_italic) {
      return FLAG_ITALIC;
    } else if (id == R.id.btn_monospace) {
      return FLAG_MONOSPACE;
    } else if (id == R.id.btn_underline) {
      return  FLAG_UNDERLINE;
    } else if (id == R.id.btn_strikethrough) {
      return FLAG_STRIKETHROUGH;
    } else if (id == R.id.btn_link) {
      return FLAG_LINK;
    } else if (id == R.id.btn_spoiler) {
      return FLAG_SPOILER;
    }
    return -1;
  }

  private static int getTypeFlagFromEntityType (TdApi.TextEntityType type) {
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR:
        return FLAG_BOLD;
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
        return FLAG_ITALIC;
      case TdApi.TextEntityTypeCode.CONSTRUCTOR:
      case TdApi.TextEntityTypePre.CONSTRUCTOR:
      case TdApi.TextEntityTypePreCode.CONSTRUCTOR:
        return FLAG_MONOSPACE;
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
        return FLAG_UNDERLINE;
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
        return FLAG_STRIKETHROUGH;
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR:
        return FLAG_LINK;
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR:
        return FLAG_SPOILER;
      case TdApi.TextEntityTypeBlockQuote.CONSTRUCTOR:
        return FLAG_BLOCK_QUOTE;

      // immutable
      case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR:
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR:

      // auto-detected
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR:
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR:
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR:
      case TdApi.TextEntityTypeMediaTimestamp.CONSTRUCTOR:
      case TdApi.TextEntityTypeMention.CONSTRUCTOR:
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR:
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR:
        return -1;

      // unsupported
      default:
        Td.assertTextEntityType_91234a79();
        throw Td.unsupported(type);
    }
  }

  private static TdApi.TextEntityType getEntityTypeFromTypeFlag (int flag) {
    if (flag == FLAG_BOLD) {
      return new TdApi.TextEntityTypeBold();
    } else if (flag == FLAG_ITALIC) {
      return new TdApi.TextEntityTypeItalic();
    } else if (flag == FLAG_MONOSPACE) {
      return new TdApi.TextEntityTypeCode();
    } else if (flag == FLAG_UNDERLINE) {
      return new TdApi.TextEntityTypeUnderline();
    } else if (flag == FLAG_STRIKETHROUGH) {
      return new TdApi.TextEntityTypeStrikethrough();
    } else if (flag == FLAG_LINK) {
      return new TdApi.TextEntityTypeTextUrl();
    } else if (flag == FLAG_SPOILER) {
      return new TdApi.TextEntityTypeSpoiler();
    } else if (flag == FLAG_BLOCK_QUOTE) {
      return new TdApi.TextEntityTypeBlockQuote();
    }
    return null;
  }

  private static TextView createHeader (Context context, ViewController<?> parent, String text) {
    TextView textView = new NoScrollTextView(context);
    textView.setPadding(Screen.dp(6), Screen.dp(20), Screen.dp(6), Screen.dp(8));
    textView.setSingleLine(true);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setTextColor(Theme.textAccent2Color());
    textView.setTypeface(Fonts.getRobotoMedium());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    textView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    textView.setText(text);
    parent.addThemeTextColorListener(textView, ColorId.background_text);
    return textView;
  }

  private static class Button extends FrameLayout implements FactorAnimator.Target {
    private final BoolAnimator isActive;
    private final BoolAnimator isEnabled;
    private boolean needDrawWithoutRepainting;
    private Drawable drawable;

    public Button (Context context) {
      super(context);

      isActive = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
      isEnabled = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);

      setIsEnabled(true, false);
      Views.setClickable(this);
    }

    public void setBackground (boolean isToggle, ViewController<?> themeProvider) {
      if (isToggle) {
        RippleSupport.setSimpleWhiteBackground(this, ColorId.filling, 6f, themeProvider);
      } else {
        RippleSupport.setTransparentSelector(this, 6f, themeProvider);
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setNeedDrawWithoutRepainting (boolean needDrawWithoutRepainting) {
      this.needDrawWithoutRepainting = needDrawWithoutRepainting;
    }

    public void setIsActive (boolean active, boolean animated) {
      isActive.setValue(active, animated);
    }

    public void setIsEnabled (boolean enabled, boolean animated) {
      isEnabled.setValue(enabled, animated);
      setEnabled(enabled);
    }

    public void setDrawable (int drawableRes) {
      drawable = Drawables.get(getResources(), drawableRes);
    }

    public void setDrawable (Drawable drawable) {
      this.drawable = drawable;
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      if (drawable != null) {
        int color = ColorUtils.fromToArgb(Theme.iconColor(), Theme.getColor(ColorId.iconActive), isActive.getFloatValue());
        Drawables.drawCentered(canvas, drawable, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, needDrawWithoutRepainting ? null : Paints.getPorterDuffPaint(color));
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setAlpha(MathUtils.fromTo(0.4f, 1f, isEnabled.getFloatValue()));
      invalidate();
    }
  }

  @SuppressLint("AppCompatCustomView")
  private static class LangSelector extends Button {
    private final Drawable arrowDrawable;
    private final TextView textView;

    public LangSelector (Context context, ViewController<?> parent) {
      super(context);

      arrowDrawable = Drawables.get(R.drawable.baseline_keyboard_arrow_down_20);

      textView = new TextView(context);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      textView.setPadding(Screen.dp(12), 0, Screen.dp(18), 0);
      textView.setTextColor(Theme.getColor(ColorId.text));
      textView.setEllipsize(TextUtils.TruncateAt.END);
      textView.setTypeface(Fonts.getRobotoRegular());
      textView.setSingleLine(true);
      parent.addThemeTextColorListener(textView, ColorId.text);
      addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

      Views.setClickable(this);
      setBackground(true, parent);
    }

    public void setText (String text) {
      textView.setText(text);
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      Drawables.draw(canvas, arrowDrawable, getMeasuredWidth() - Screen.dp(26), Screen.dp(10), Paints.getPorterDuffPaint(Theme.iconColor()));
    }
  }
}
