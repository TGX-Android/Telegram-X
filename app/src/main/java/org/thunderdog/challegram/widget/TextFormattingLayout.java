package org.thunderdog.challegram.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.TranslationControllerV2;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.LanguageDetector;
import org.thunderdog.challegram.util.text.TextColorSets;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

@SuppressLint("ViewConstructor")
public class TextFormattingLayout extends FrameLayout {
  private static final int SIZE = 40;

  public static final int FLAG_BOLD = 1;
  public static final int FLAG_ITALIC = 1 << 1;
  private static final int FLAG_MONOSPACE = 1 << 2;
  private static final int FLAG_UNDERLINE = 1 << 3;
  private static final int FLAG_STRIKETHROUGH = 1 << 4;
  private static final int FLAG_LINK = 1 << 5;
  private static final int FLAG_SPOILER = 1 << 6;
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

  private final ViewController<?> parent;
  private final TextView editHeader;
  private final TextView translateHeader;
  private final TextView clearButton;
  private final Button[] buttons;
  private final LangSelector langSelector;
  private final Tdlib tdlib;
  private final InputView inputView;
  private String languageToTranslate;
  private TranslationControllerV2.Wrapper translationPopup;
  private final BoolAnimator clearButtonIsEnabled;

  public TextFormattingLayout (@NonNull Context context, ViewController<?> parent, InputView inputView) {
    super(context);

    this.inputView = inputView;
    this.tdlib = parent.tdlib();
    this.parent = parent;

    setBackgroundColor(Theme.backgroundColor());
    setPadding(Screen.dp(15), 0, Screen.dp(15), 0);

    addView(createHeader(context, Lang.getString(R.string.TextFormatting)));
    addView(editHeader = createHeader(context, Lang.getString(R.string.TextFormattingTools)));
    addView(translateHeader = createHeader(context, Lang.getString(R.string.TextFormattingTranslate)));

    clearButton = createHeader(context, Lang.getString(R.string.Clear));
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
      button.setBackground(a < 7);
      button.setOnClickListener(this::onButtonClick);
      addView(buttons[a] = button);
    }

    langSelector = new LangSelector(context);
    langSelector.setOnClickListener(this::onClickLanguageSelector);
    addView(langSelector);

    setLanguageToTranslate(Settings.instance().getDefaultLanguageForTranslateDraft());
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

  private void setLanguageToTranslate (String language) {
    Settings.instance().setDefaultLanguageForTranslateDraft(languageToTranslate = language);
    langSelector.setText(Lang.getLanguageName(language, Lang.getString(R.string.TranslateLangUnknown)));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = MeasureSpec.getSize(widthMeasureSpec);
    float padding = Math.max(Screen.dp(5), (width - Screen.dp(30 + 7 * SIZE)) / 6f);

    ViewGroup.MarginLayoutParams params;

    params = (ViewGroup.MarginLayoutParams) editHeader.getLayoutParams();
    params.topMargin = Screen.dp(46 + SIZE);

    params = (ViewGroup.MarginLayoutParams) translateHeader.getLayoutParams();
    params.leftMargin = (int) ((padding + Screen.dp(SIZE)) * 3);
    params.topMargin = Screen.dp(46 + SIZE);

    params = (ViewGroup.MarginLayoutParams) langSelector.getLayoutParams();
    params.topMargin = Screen.dp(92 + SIZE);
    params.leftMargin = (int) ((padding + Screen.dp(SIZE)) * 3);
    params.height = Screen.dp(SIZE);
    params.width = (int) (Screen.dp(SIZE * 3) + padding * 2);

    for (int a = 0; a < buttonIds.length; a++) {
      params = (ViewGroup.MarginLayoutParams) buttons[a].getLayoutParams();
      params.width = params.height = Screen.dp(SIZE);
      if (a < 7) {
        params.leftMargin = (int) ((padding + Screen.dp(SIZE)) * a);
        params.topMargin = Screen.dp(46);
      } else if (a < 10) {
        params.leftMargin = (int) ((padding + Screen.dp(SIZE)) * (a - 7));
        params.topMargin = Screen.dp(92 + SIZE);
      } else {
        params.leftMargin = (int) ((padding + Screen.dp(SIZE)) * 6);
        params.topMargin = Screen.dp(92 + SIZE);
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

    if (id == R.id.btn_translate) {
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

    TranslationControllerV2.LanguageSelectorPopup languagePopupLayout = new TranslationControllerV2.LanguageSelectorPopup(v.getContext(), this::setLanguageToTranslate, languageToTranslate, null);

    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) languagePopupLayout.languageRecyclerWrap.getLayoutParams();
    params.gravity = Gravity.TOP | Gravity.LEFT;

    languagePopupLayout.languageRecyclerWrap.setTranslationX(x);
    languagePopupLayout.languageRecyclerWrap.setTranslationY(y);
    languagePopupLayout.show();
    languagePopupLayout.languageRecyclerWrap.setPivotX(Screen.dp(TranslationControllerV2.LanguageSelectorPopup.WIDTH + TranslationControllerV2.LanguageSelectorPopup.PADDING));
    languagePopupLayout.languageRecyclerWrap.setPivotY(Screen.dp(TranslationControllerV2.LanguageSelectorPopup.HEIGHT + TranslationControllerV2.LanguageSelectorPopup.PADDING));
  }

  private void checkLanguage (RunnableData<String> callback) {
    String text = inputView.getOutputText(false).text;
    if (text == null) {
      callback.runWithData(null);
      return;
    }

    String textToDetect = text.substring(inputView.getSelectionStart(), inputView.getSelectionEnd());
    LanguageDetector.detectLanguage(getContext(), textToDetect, callback);
  }

  private void startTranslate () {
    final int start =  inputView.getSelectionStart();
    TdApi.FormattedText text = Td.substring(inputView.getOutputText(false), start, inputView.getSelectionEnd());
    checkLanguage(language -> {
      if (translationPopup != null) return;

      translationPopup = new TranslationControllerV2.Wrapper(getContext(), tdlib, parent);
      translationPopup.setArguments(new TranslationControllerV2.Args(new TranslationsManager.Translatable() {
        @Nullable
        @Override
        public String getOriginalMessageLanguage () {
          return language;
        }

        @Override
        public TdApi.FormattedText getTextToTranslate () {
          return text;
        }
      }));
      translationPopup.setTextColorSet(TextColorSets.Regular.NORMAL);
      translationPopup.setTranslationApplyCallback(r -> {
        if (r == null) return;
        inputView.paste(r, true);
        checkButtonsActive(true);
      });
      translationPopup.setDefaultLanguageToTranslate(languageToTranslate);
      translationPopup.show();
      translationPopup.setDismissListener(popup -> translationPopup = null);
    });
  }

  private boolean isSelectionEmpty () {
    return inputView.getSelectionStart() == inputView.getSelectionEnd();
  }

  private int checkSpans () {
    return isSelectionEmpty() ? 0: checkSpans(inputView.getOutputText(false), inputView.getSelectionStart(), inputView.getSelectionEnd());
  }

  /* * */

  private static int checkSpans (TdApi.FormattedText text, int start, int end) {
    if (text == null || text.entities == null) return 0;
    int flags = 0;

    for (TdApi.TextEntity entity: text.entities) {
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

  private static int getTypeFlagFromEntityType (TdApi.TextEntityType entityType) {
    int constructor = entityType.getConstructor();
    if (constructor == TdApi.TextEntityTypeBold.CONSTRUCTOR) {
      return FLAG_BOLD;
    } else if (constructor == TdApi.TextEntityTypeItalic.CONSTRUCTOR) {
      return FLAG_ITALIC;
    } else if (constructor == TdApi.TextEntityTypeCode.CONSTRUCTOR) {
      return FLAG_MONOSPACE;
    } else if (constructor == TdApi.TextEntityTypeUnderline.CONSTRUCTOR) {
      return  FLAG_UNDERLINE;
    } else if (constructor == TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR) {
      return FLAG_STRIKETHROUGH;
    } else if (constructor == TdApi.TextEntityTypeTextUrl.CONSTRUCTOR) {
      return FLAG_LINK;
    } else if (constructor == TdApi.TextEntityTypeSpoiler.CONSTRUCTOR) {
      return FLAG_SPOILER;
    }
    return -1;
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
      return  new TdApi.TextEntityTypeTextUrl();
    } else if (flag == FLAG_SPOILER) {
      return  new TdApi.TextEntityTypeSpoiler();
    }
    return null;
  }

  private static TextView createHeader (Context context, String text) {
    TextView textView = new NoScrollTextView(context);
    textView.setPadding(Screen.dp(6), Screen.dp(20), Screen.dp(6), Screen.dp(8));
    textView.setSingleLine(true);
    textView.setEllipsize(TextUtils.TruncateAt.END);
    textView.setTextColor(Theme.textAccent2Color());
    textView.setTypeface(Fonts.getRobotoMedium());
    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    textView.setLayoutParams(FrameLayoutFix.newParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    textView.setText(text);
    return textView;
  }

  private static class Button extends FrameLayout implements FactorAnimator.Target {
    private final BoolAnimator isActive;
    private final BoolAnimator isEnabled;
    private Drawable drawable;

    public Button (Context context) {
      super(context);

      isActive = new BoolAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);
      isEnabled = new BoolAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200L);

      setIsEnabled(true, false);
      Views.setClickable(this);
    }

    public void setBackground (boolean isToggle) {
      if (isToggle) {
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(Screen.dp(6), Theme.getColor(ColorId.filling), Theme.getColor(ColorId.fillingPressed)));
      } else {
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(Screen.dp(6), Theme.getColor(ColorId.background), Theme.getColor(ColorId.iv_textReferenceBackgroundPressed)));
      }
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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

    @Override
    protected void dispatchDraw (Canvas canvas) {
      super.dispatchDraw(canvas);
      if (drawable != null) {
        int color = ColorUtils.fromToArgb(Theme.iconColor(), Theme.getColor(ColorId.iconActive), isActive.getFloatValue());
        Drawables.drawCentered(canvas, drawable, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, Paints.getPorterDuffPaint(color));
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
      setBackground(true);
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
