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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
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
import org.thunderdog.challegram.tool.Strings;
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
import tgx.td.Td;

public class TextFormattingLayout extends FrameLayout implements TranslationsManager.Translatable {
  private static final int MIN_PADDING_BETWEEN_BUTTONS = 4;
  private static final int MAX_BUTTON_SIZE = 40;
  private static final int PADDING_HORIZONTAL = 15;
  private static final int PADDING_VERTICAL = 20;

  public static final int FLAG_BOLD = 1;
  public static final int FLAG_ITALIC = 1 << 1;
  private static final int FLAG_MONOSPACE = 1 << 2;
  private static final int FLAG_UNDERLINE = 1 << 3;
  private static final int FLAG_STRIKETHROUGH = 1 << 4;
  private static final int FLAG_LINK = 1 << 5;
  private static final int FLAG_SPOILER = 1 << 6;
  private static final int FLAG_BLOCK_QUOTE = 1 << 7;
  private static final int FLAG_CLEAR = 1 << 30;

  private KeyboardFrameLayout keyboardView;
  private ViewController<?> parent;
  private InputView inputView;
  private TranslationsManager translationsManager;

  private final LangSelector langSelector;
  private final LinkInput linkInput;
  private LinkButton linkButton;

  private final SparseArray<Button> buttons = new SparseArray<>();
  private final Params lp = new Params();

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
    default void onWantsOpenTextFormattingKeyboard () {}
  }

  public TextFormattingLayout (@NonNull Context context) {
    super(context);
    setPadding(Screen.dp(PADDING_HORIZONTAL), Screen.dp(PADDING_VERTICAL), Screen.dp(PADDING_HORIZONTAL), 0);

    translationCounterDrawable = new TranslationCounterDrawable(Drawables.get(R.drawable.baseline_translate_24));
    translationCounterDrawable.setColors(ColorId.icon, ColorId.background ,ColorId.iconActive);
    translationCounterDrawable.setInvalidateCallback(() -> buttons.get(R.id.btn_translate).invalidate());

    linkInput = new LinkInput(context);
    linkInput.setId(R.id.input);
    linkInput.setEnabled(false);
    linkInput.setClickable(false);
    addView(linkInput);

    langSelector = new LangSelector(context);
    langSelector.setOnClickListener(this::onClickLanguageSelector);
    addView(langSelector);

    for (int a = 0; a < buttonsData.size(); a++) {
      final ButtonData buttonData = buttonsData.valueAt(a);
      final Button button;
      if (buttonData.id == R.id.btn_link) {
        linkButton = new LinkButton(context);
        button = linkButton;
      } else {
        button = new Button(context);
      }

      button.setId(buttonData.id);
      button.setDrawable(buttonData.icon);
      button.setOnClickListener(this::onButtonClick);
      buttons.append(buttonData.id, button);
      addView(button);

      if (buttonData.id == R.id.btn_translate) {
        button.setDrawable(translationCounterDrawable);
        button.setNeedDrawWithoutRepainting(true);
      }
    }
  }

  void setKeyboardView (KeyboardFrameLayout keyboardView) {
    this.keyboardView = keyboardView;
  }

  public void init (ViewController<?> parent, InputView inputView, Delegate delegate) {
    this.parent = parent;
    this.delegate = delegate;
    this.inputView = inputView;

    linkInput.init(parent);
    langSelector.init(parent);
    ViewSupport.setThemedBackground(this, ColorId.background, parent);

    for (int a = 0; a < buttonsData.size(); a++) {
      final ButtonData buttonData = buttonsData.valueAt(a);
      buttons.get(buttonData.id).setBackground(buttonData.needBackground, parent);
    }

    translationsManager = new TranslationsManager(parent.tdlib(), this, this::setTranslatedStatus, this::setTranslationResult, this::setTranslationError);
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
    checkButtonsActive(true);
  }

  public void onInputViewTouchEvent (MotionEvent event) {
    clickHelper.onTouchEvent(inputView, event);
  }

  public void checkButtonsActive (boolean animated) {
    final boolean isEmpty = isSelectionEmpty();

    final TextSelection selection = inputView.getTextSelection();
    final Editable text = inputView.getText();
    final TdApi.FormattedText outputText = inputView.getOutputText(false);

    final int flags = (selection == null || selection.isEmpty()) ? 0 :
      checkSpans(outputText, selection.start, selection.end);

    final URLSpan[] existingSpans = selection != null ?
      text.getSpans(selection.start, selection.end, URLSpan.class) : null;
    final int linksCount = existingSpans != null ? existingSpans.length : 0;

    for (int a = 0; a < buttonsData.size(); a++) {
      final ButtonData data = buttonsData.valueAt(a);
      final Button button = buttons.get(data.id);
      if (data.id == R.id.btn_plain) {
        button.setIsEnabled(BitwiseUtils.hasFlag(flags, data.flag), animated);
        continue;
      }

      if (data.id == R.id.btn_circleBackspace) {
        button.setIsEnabled(selection != null && (!selection.isEmpty() || selection.start > 0), animated);
        continue;
      }

      if (data.id == R.id.btn_link) {
        button.setIsActive(existingSpans != null && existingSpans.length > 0, animated);
        linkButton.setIsMultiple(existingSpans != null && existingSpans.length > 1, animated);
        linkInput.textView.setText(linksCount == 1 ? existingSpans[0].getURL() : null);
        linkInput.textView.setHint(linksCount > 1 ? R.string.MultipleUrlPlaceholder : R.string.PasteUrlPlaceholder);
        linkInput.textView.setTypeface(linksCount > 1 ? Fonts.getRobotoItalic() : Fonts.getRobotoRegular());
        continue;
      }

      if (data.flag != 0) {
        button.setIsActive(BitwiseUtils.hasFlag(flags, data.flag), animated);
      }

      if (data.id != android.R.id.paste) {
        button.setIsEnabled(!isEmpty, animated);
      }
    }
    linkInput.setIsEnabled(!isEmpty, animated);
    langSelector.setIsEnabled(!isEmpty, animated);
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
    final int height = MeasureSpec.getSize(heightMeasureSpec);
    lp.init(width, height);
    lp.apply(langSelector, 0, 2, 6);
    lp.apply(linkInput, 0, 1, 6);

    for (int a = 0; a < buttonsData.size(); a++) {
      final ButtonData buttonData = buttonsData.valueAt(a);
      final Button button = buttons.get(buttonData.id);
      lp.apply(button, buttonData.left, buttonData.top);
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return true;
  }

  private void onButtonClick (View v) {
    int id = v.getId();

    if (id == R.id.btn_pasteUrl) {
      final CharSequence text = U.getPasteText(v.getContext());
      if (text != null) {
        final boolean isValid = Strings.isValidLink(text.toString());
        if (isValid) {
          inputView.setSpanLink(text.toString());
        } else {
          parent.context().tooltipManager().builder(v).show(parent.tdlib(), R.string.PasteUrlNotValidHint).hideDelayed();
          UI.hapticVibrate(v, false);
        }
      } else {
        parent.context().tooltipManager().builder(v).show(parent.tdlib(), R.string.PasteUrlEmptyHint).hideDelayed();
        UI.hapticVibrate(v, false);
      }
    } else if (id == R.id.btn_circleBackspace) {
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
      int flag = buttonsData.get(id).flag;
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
      case TdApi.TextEntityTypeExpandableBlockQuote.CONSTRUCTOR:
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
        Td.assertTextEntityType_56c1e709();
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

  private static class Button extends FrameLayout implements FactorAnimator.Target {
    private final BoolAnimator isActive;
    private final BoolAnimator isEnabled;
    private boolean needDrawWithoutRepainting;
    protected Drawable drawable;

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
      drawDrawable(canvas, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, 1f);
    }

    protected void drawDrawable (Canvas canvas, float cx, float cy, float alpha) {
      if (drawable != null) {
        int color = ColorUtils.fromToArgb(Theme.iconColor(), Theme.getColor(ColorId.iconActive), isActive.getFloatValue());
        color = ColorUtils.alphaColor(alpha, color);
        Drawables.drawCentered(canvas, drawable, cx, cy, needDrawWithoutRepainting ? null : Paints.getPorterDuffPaint(color));
      }
    }

    @Override
    public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
      setAlpha(MathUtils.fromTo(0.4f, 1f, isEnabled.getFloatValue()));
      invalidate();
    }
  }

  private static class LinkButton extends Button {
    private static final RectF tmpRect = new RectF();
    private final BoolAnimator isMultiple = new BoolAnimator(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
    private final Path path = new Path();

    public LinkButton (Context context) {
      super(context);
    }

    public void setIsMultiple (boolean active, boolean animated) {
      isMultiple.setValue(active, animated);
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
      final float factor = isMultiple.getFloatValue();
      if (factor == 0f) {
        super.dispatchDraw(canvas);
        return;
      }

      final int s = Views.save(canvas);
      final float offset = -Screen.dp(2f) * factor;
      final float cx = getMeasuredWidth() / 2f;
      final float cy = getMeasuredHeight() / 2f;

      canvas.translate(0, offset);
      super.dispatchDraw(canvas);

      path.reset();
      tmpRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
      path.addRect(tmpRect, Path.Direction.CW);
      tmpRect.set(cx - Screen.dp(11), cy - Screen.dp(5), cx + Screen.dp(11), cy + Screen.dp(7));
      path.addRoundRect(tmpRect, Screen.dp(6), Screen.dp(6), Path.Direction.CCW);
      path.close();

      canvas.clipPath(path);

      drawDrawable(canvas, cx, cy + Screen.dp(4), factor);

      Views.restore(canvas, s);
    }
  }

  private static class LangSelector extends Button {
    private final Drawable arrowDrawable;
    private final TextView textView;

    public LangSelector (Context context) {
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

      addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

      Views.setClickable(this);
    }

    public void init (ViewController<?> parent) {
      parent.addThemeTextColorListener(textView, ColorId.text);
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

  private static class LinkInput extends Button {
    public final TextView textView;

    public LinkInput (Context context) {
      super(context);

      textView = new TextView(context);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
      textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
      textView.setPadding(Screen.dp(12), 0, Screen.dp(12), 0);
      textView.setHint(R.string.PasteUrlPlaceholder);
      textView.setTextColor(Theme.getColor(ColorId.text));
      textView.setHintTextColor(Theme.textPlaceholderColor());
      textView.setTypeface(Fonts.getRobotoRegular());
      textView.setSingleLine(true);

      addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 0, 0, 40, 0));
    }

    public void init (ViewController<?> parent) {
      parent.addThemeTextColorListener(textView, ColorId.text);
      parent.addThemeHintTextColorListener(textView, ColorId.textPlaceholder);
      setBackground(true, parent);
    }
  }

  private static class Params {
    private int buttonSizePx = 0;
    private float paddingHorizontal = 0f;
    private float paddingVertical = 0f;

    public void init(int width, int height) {
      final int sizeW = (width - Screen.dp(PADDING_HORIZONTAL * 2 + MIN_PADDING_BETWEEN_BUTTONS * 6)) / 7;
      final int sizeH = (height - Screen.dp(PADDING_VERTICAL * 2 + MIN_PADDING_BETWEEN_BUTTONS * 3)) / 4;

      buttonSizePx = Math.min(Math.min(sizeW, sizeH), Screen.dp(MAX_BUTTON_SIZE));
      paddingHorizontal = Math.max(Screen.dp(MIN_PADDING_BETWEEN_BUTTONS), (width - Screen.dp(PADDING_HORIZONTAL * 2) - 7 * buttonSizePx) / 6f);
      paddingVertical = Screen.dp(12);
    }

    public void apply(View v, int left, int top) {
      apply(v, left, top, 1);
    }

    public void apply(View v, int left, int top, int width) {
      final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
      params.width = getWidth(width);
      params.height = buttonSizePx;
      params.leftMargin = getLeft(left);
      params.topMargin = getTop(top);
    }

    private int getWidth(int count) {
      return (int) (buttonSizePx * count + paddingHorizontal * (count - 1));
    }

    private int getLeft(int index) {
      return (int) ((paddingHorizontal + buttonSizePx) * index);
    }

    private int getRight(int index) {
      return (int) ((paddingHorizontal + buttonSizePx) * index + buttonSizePx);
    }

    private int getTop(int index) {
      return (int) ((paddingVertical + buttonSizePx) * index);
    }
  }

  private static class ButtonData {
    public final int id;
    public final int icon;
    public final boolean needBackground;

    public final int flag;
    public final int top;
    public final int left;

    public ButtonData (int id, int icon, boolean needBackground, int flag, int left, int top) {
      this.id = id;
      this.icon = icon;
      this.needBackground = needBackground;
      this.flag = flag;
      this.left = left;
      this.top = top;
    }
  }



  /* * */

  private static final SparseArray<ButtonData> buttonsData = new SparseArray<>();

  private static void addButton(ButtonData button) {
    buttonsData.append(button.id, button);
  }

  static {
    addButton(new ButtonData(R.id.btn_bold, R.drawable.baseline_format_bold_24, true, FLAG_BOLD, 0, 0));
    addButton(new ButtonData(R.id.btn_italic, R.drawable.baseline_format_italic_24, true, FLAG_ITALIC, 1, 0));
    addButton(new ButtonData(R.id.btn_monospace, R.drawable.baseline_code_24, true, FLAG_MONOSPACE, 2, 0));
    addButton(new ButtonData(R.id.btn_underline, R.drawable.baseline_format_underlined_24, true, FLAG_UNDERLINE, 3, 0));
    addButton(new ButtonData(R.id.btn_strikethrough, R.drawable.baseline_strikethrough_s_24, true, FLAG_STRIKETHROUGH, 4, 0));
    addButton(new ButtonData(R.id.btn_quote, R.drawable.baseline_format_quote_close_18, true, FLAG_BLOCK_QUOTE, 5, 0));
    addButton(new ButtonData(R.id.btn_spoiler, R.drawable.baseline_eye_off_24, true, FLAG_SPOILER, 6,0));

    addButton(new ButtonData(R.id.btn_pasteUrl, R.drawable.baseline_content_paste_24, false, 0, 5, 1));
    addButton(new ButtonData(R.id.btn_link, R.drawable.baseline_link_24, false, FLAG_LINK, 6, 1));
    addButton(new ButtonData(R.id.btn_translate, R.drawable.baseline_translate_24, false, 0, 6, 2));

    addButton(new ButtonData(R.id.btn_plain, R.drawable.dot_baseline_clear_formatting_24, false, FLAG_CLEAR, 0,3));
    addButton(new ButtonData(android.R.id.cut, R.drawable.baseline_content_cut_24, false, 0, 2, 3));
    addButton(new ButtonData(android.R.id.copy, R.drawable.baseline_content_copy_24, false, 0, 3, 3));
    addButton(new ButtonData(android.R.id.paste, R.drawable.baseline_content_paste_24, false, 0, 4, 3));
    addButton(new ButtonData(R.id.btn_circleBackspace, R.drawable.baseline_backspace_24, false, 0, 6, 3));
  }
}
