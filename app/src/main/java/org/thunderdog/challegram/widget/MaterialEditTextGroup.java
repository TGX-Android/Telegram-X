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
 * File created on 18/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.HeightChangeListener;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.StringUtils;

@SuppressWarnings("NullableProblems")
public class MaterialEditTextGroup extends FrameLayoutFix implements View.OnFocusChangeListener, FactorAnimator.Target, TextWatcher, TextView.OnEditorActionListener {
  public interface EmptyListener {
    void onTextEmptyStateChanged (MaterialEditTextGroup v, boolean isEmpty);
  }

  public interface DoneListener {
    boolean onTextDonePressed (MaterialEditTextGroup v);
  }

  public interface TextChangeListener {
    void onTextChanged (MaterialEditTextGroup v, CharSequence text);
  }

  public interface FocusListener {
    void onFocusChanged (MaterialEditTextGroup v, boolean isFocused);
  }

  private @Nullable TextView hintView;
  private @NonNull MaterialEditText editText;
  private @Nullable TextView lengthCounter;

  private boolean isNotEmpty;
  private @Nullable EmptyListener emptyListener;
  private @Nullable TextChangeListener textListener;
  private @Nullable DoneListener doneListener;
  private @Nullable FocusListener focusListener;
  private @Nullable HeightChangeListener heightChangeListener;

  public MaterialEditTextGroup (Context context) {
    super(context);
    init(context, true);
  }

  public MaterialEditTextGroup (Context context, boolean needHint) {
    super(context);
    init(context, needHint);
  }

  private void setIsNotEmpty (boolean isNotEmpty) {
    if (this.isNotEmpty != isNotEmpty) {
      this.isNotEmpty = isNotEmpty;
      if (emptyListener != null) {
        emptyListener.onTextEmptyStateChanged(this, !isNotEmpty);
      }
    }
  }

  public void setTextListener (@Nullable TextChangeListener textListener) {
    this.textListener = textListener;
  }

  public void setHeightChangeListener (@Nullable HeightChangeListener heightChangeListener) {
    this.heightChangeListener = heightChangeListener;
  }

  @ThemeColorId
  private int textColorId = R.id.theme_color_text;

  public void setTextColorId (@ThemeColorId int colorId) {
    if (this.textColorId != colorId) {
      this.textColorId = colorId;
      editText.setTextColor(Theme.getColor(colorId));
      if (themeProvider != null) {
        themeProvider.addOrUpdateThemeTextColorListener(this, colorId);
      }
    }
  }

  public void setInputEnabled (boolean enabled) {
    editText.setEnabled(enabled);
    setTextColorId(enabled ? R.id.theme_color_text : R.id.theme_color_textLight);
  }

  public interface NextCallback {
    boolean needNextButton (MaterialEditTextGroup v);
  }

  private NextCallback nextCallback;

  public void setNeedNextButton (NextCallback forceNextButton) {
    this.nextCallback = forceNextButton;
  }

  private void init (Context context, boolean needHint) {
    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(needHint ? 20f : 8f);

    editText = new MaterialEditText(context) {
      @Override
      public InputConnection createInputConnection (EditorInfo outAttrs) {
        InputConnection conn = super.createInputConnection(outAttrs);
        if (nextCallback != null && nextCallback.needNextButton(MaterialEditTextGroup.this)) {
          outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        }
        return conn;
      }
    };
    editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    editText.setTypeface(Fonts.getRobotoRegular());
    editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    editText.setBackgroundResource(R.drawable.transparent);
    editText.setTextColor(Theme.getColor(textColorId));
    editText.setHintTextColor(Theme.textPlaceholderColor());
    editText.setLayoutParams(params);
    editText.addTextChangedListener(this);
    editText.setPadding(Screen.dp(1.5f), Screen.dp(8f), Screen.dp(1.5f), Screen.dp(9f));
    // editText.setMinimumHeight(Screen.dp(40f));
    addView(editText);

    if (needHint) {
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f));
      params.topMargin = Screen.dp(20f);

      hintView = new NoScrollTextView(context) {
        @Override
        protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
          super.onMeasure(widthMeasureSpec, heightMeasureSpec);
          if (isRtl) {
            setPivotX(getMeasuredWidth());
          } else {
            setPivotX(0f);
          }
        }
      };
      hintView.setPivotY(0f);
      hintView.setTypeface(Fonts.getRobotoRegular());
      hintView.setSingleLine(true);
      hintView.setEllipsize(TextUtils.TruncateAt.END);
      hintView.setTextColor(Theme.textPlaceholderColor());
      hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
      hintView.setPadding(Screen.dp(1.5f), 0, Screen.dp(1.5f), 0);
      hintView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
      hintView.setLayoutParams(params);
      addView(hintView);
    }

    editText.setOnFocusChangeListener(this);
  }

  private boolean isRtl;

  public void applyRtl (boolean rtl) {
    if (this.isRtl != rtl) {
      this.isRtl = rtl;
      editText.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
      if (hintView != null)
        hintView.setGravity(Gravity.CENTER_VERTICAL | (rtl ? Gravity.RIGHT : Gravity.LEFT));
    }
  }

  private int maxLength = -1;
  private boolean calculateMaxLengthInCodePoints;

  private int getTextLength () {
    if (calculateMaxLengthInCodePoints) {
      CharSequence text = editText.getText();
      return Character.codePointCount(text, 0, text.length());
    } else {
      return editText.getText().length();
    }
  }

  private void updateRemainingCharCount () {
    if (lengthCounter != null) {
      if (maxLength == -1) {
        lengthCounter.setText("");
      } else {
        final int remaining = maxLength - getTextLength();
        if (remaining > 50) {
          lengthCounter.setText("");
        } else {
          lengthCounter.setText(Strings.buildCounter(remaining));
          lengthCounter.setTextColor(Theme.getColor(remaining <= 0 ? R.id.theme_color_textNegative : R.id.theme_color_textLight));
        }
      }
    }
  }

  public void setMaxLength (int maxLength) {
    setMaxLength(maxLength, true);
  }

  public void setMaxLength (int maxLength, boolean calculateMaxLengthInCodePoints) {
    if (this.maxLength != maxLength || this.calculateMaxLengthInCodePoints != calculateMaxLengthInCodePoints) {
      this.maxLength = maxLength;
      this.calculateMaxLengthInCodePoints = calculateMaxLengthInCodePoints;
      addLengthCounter(false);
      updateRemainingCharCount();
    }
  }

  public void onInputActiveFactorChange (float factor) {
    if (lengthCounter != null) {
      lengthCounter.setAlpha(factor);
    }
  }

  public void addLengthCounter (@Deprecated boolean reduceOffset) {
    if (lengthCounter == null) {
      editText.setParent(this);

      FrameLayoutFix.LayoutParams params;
      params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      params.topMargin = reduceOffset ? Screen.dp(19f) : Screen.dp(20f) + Screen.dp(11f);
      params.leftMargin = params.rightMargin = Screen.dp(6f);
      params.gravity = isRtl ? Gravity.LEFT : Gravity.RIGHT;

      lengthCounter = new NoScrollTextView(getContext());
      lengthCounter.setTextColor(Theme.textDecentColor());
      lengthCounter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
      lengthCounter.setTypeface(Fonts.getRobotoRegular());
      lengthCounter.setAlpha(0f);
      lengthCounter.setLayoutParams(params);

      params = hintView != null ? (FrameLayoutFix.LayoutParams) hintView.getLayoutParams() : null;
      if (isRtl) {
        if (params != null)
          params.leftMargin = Screen.dp(40f);
        editText.setPadding(editText.getPaddingLeft() + Screen.dp(32f), editText.getPaddingTop(), editText.getPaddingRight(), editText.getPaddingBottom());
      } else {
        if (params != null)
          params.rightMargin = Screen.dp(40f);
        editText.setPadding(editText.getPaddingLeft(), editText.getPaddingTop(), editText.getPaddingRight() + Screen.dp(32f), editText.getPaddingBottom());
      }

      addView(lengthCounter);
    }
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(editText, textColorId);
      if (hintView != null)
        themeProvider.addThemeTextColorListener(hintView, R.id.theme_color_textPlaceholder);
      themeProvider.addThemeInvalidateListener(editText);
      themeProvider.addThemeHighlightColorListener(editText, R.id.theme_color_textSelectionHighlight);
      themeProvider.addThemeHintTextColorListener(editText, R.id.theme_color_textPlaceholder);
      if (lengthCounter != null) {
        themeProvider.addThemeTextColorListener(lengthCounter, R.id.theme_color_textLight);
      }
      if (radioView != null) {
        themeProvider.addThemeInvalidateListener(radioView);
      }
    }
  }

  private static final int ANIMATOR_RADIO_VISIBILITY = 6;
  private RadioView radioView;
  private BoolAnimator radioVisible;

  public void setRadioVisible (boolean visible, boolean animated) {
    if (radioView != null || visible) {
      addRadio();
      radioVisible.setValue(visible, animated);
    }
  }

  private TooltipOverlayView.TooltipInfo radioTooltip;

  public void showRadioHint (ViewController<?> controller, Tdlib tdlib, int stringRes) {
    if (radioView == null) {
      addRadio();
    }
    if (radioTooltip != null)
      radioTooltip.hide(true);
    radioTooltip = UI.getContext(getContext()).tooltipManager().builder(radioView).controller(controller).offset(outRect -> outRect.offset(-Screen.dp(40f) * (1f - radioVisible.getFloatValue()), 0)).show(tdlib, stringRes).hideDelayed();
  }

  public void setRadioActive (boolean isActive, boolean animated) {
    if (radioView != null || isActive) {
      addRadio();
      radioView.setChecked(isActive, animated);
    }
  }

  public interface RadioClickListener {
    void onRadioClick (MaterialEditTextGroup editText, RadioView radioView);
  }

  private RadioClickListener radioClickListener;

  public void setOnRadioClickListener(RadioClickListener radioClickListener) {
    this.radioClickListener = radioClickListener;
  }

  public void addRadio () {
    if (radioView == null) {
      radioView = RadioView.simpleRadioView(getContext(), true);
      Views.translateMarginsToPadding(radioView);
      radioView.setOnClickListener(v -> {
        if (radioClickListener != null) {
          radioClickListener.onRadioClick(this, radioView);
        }
      });
      // Views.setTopMargin(radioView, Screen.dp(4f));
      // radioView.setPadding(radioView.getPaddingLeft(), editText.getPaddingTop(), radioView.getPaddingRight(), editText.getPaddingBottom());
      radioView.setAlpha(0f);
      ((ViewGroup) getParent()).addView(radioView);

      radioVisible = new BoolAnimator(ANIMATOR_RADIO_VISIBILITY, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
  }

  private void setRadioVisibility (float factor) {
    radioView.setAlpha(factor);
    radioView.setTranslationX(-Screen.dp(40f) * (1f - factor));
    editText.setTranslationX(Screen.dp(40f) * factor);
    if (radioTooltip != null) {
      radioTooltip.reposition();
    }
  }

  public void setFocusListener (@Nullable FocusListener focusListener) {
    this.focusListener = focusListener;
  }

  public void setEmptyListener (@Nullable EmptyListener emptyListener) {
    this.emptyListener = emptyListener;
  }

  public void setDoneListener (@Nullable DoneListener listener) {
    boolean hadListener = this.doneListener != null;
    this.doneListener = listener;
    if (!hadListener && listener != null) {
      editText.setOnEditorActionListener(this);
    } else if (hadListener && listener == null) {
      editText.setOnEditorActionListener(null);
    }
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    return (keyCode == KeyEvent.KEYCODE_ENTER && doneListener != null && doneListener.onTextDonePressed(this)) || super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
    return U.isImeDone(actionId, event) && doneListener != null && doneListener.onTextDonePressed(this);
  }

  public CharSequence getText () {
    return editText.getText();
  }

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) { }

  private @Nullable CharSequence blockedText;

  public void setBlockedText (CharSequence blockedText) {
    setBlockedText(blockedText, false);
  }

  public void setBlockedText (CharSequence blockedText, boolean animated) {
    this.blockedText = blockedText;
    if (blockedText != null && !getText().toString().equals(blockedText)) {
      this.blockedText = null;
      setText(blockedText, animated);
      this.blockedText = blockedText;
    }
  }

  private String lastInput;
  private boolean ignoreChanges;

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) {
    if (ignoreChanges) {
      return;
    }
    if (blockedText != null && !blockedText.equals(s)) {
      ignoreChanges = true;
      setText(blockedText);
      ignoreChanges = false;
      return;
    }

    if (!StringUtils.isEmpty(s)) {
      updateIsActive(true);
    }

    updateRemainingCharCount();
    String str = s.toString();
    if (lastInput == null || !lastInput.equals(str)) {
      this.lastInput = str;
      if (useTextChangeAnimations && hasFocus) {
        forceAlphaFactor(str.trim().length() > 0 ? 1f : 0f);
      }
      setIsNotEmpty(!str.isEmpty());
      if (textListener != null) {
        textListener.onTextChanged(this, str);
      }
    }
  }

  @Override
  public void afterTextChanged (Editable s) { }

  public @NonNull MaterialEditText getEditText () {
    return editText;
  }

  public void setHint (@StringRes int hintResId) {
    if (hintView != null)
      hintView.setText(Lang.getString(hintResId));
  }

  public void setHint (CharSequence hint) {
    if (hintView != null)
      hintView.setText(hint);
  }

  public void setEmptyHint (@StringRes int hintResId) {
    editText.setHint(hintResId != 0 ? Lang.getString(hintResId) : null);
  }

  public boolean isEmpty () {
    return editText.getText().length() == 0;
  }

  private boolean useTextChangeAnimations;

  public void setUseTextChangeAnimations () {
    useTextChangeAnimations = true;
    editText.setTextColor(calculateTextColor(0f));
  }

  public void setText (CharSequence text) {
    setText(text, false);
  }

  private void setTextImpl (CharSequence text) {
    editText.setText(text);
    editText.setSelection(text != null ? text.length() : 0);
  }

  public void setText (CharSequence text, boolean animated) {
    boolean isActive = (text != null && text.length() > 0) || hasFocus || !StringUtils.isEmpty(editText.getHint());
    if (animated && useTextChangeAnimations) {
      if (isActive) {
        setTextImpl(text);
      }
      setIsActive(isActive);
    } else {
      setTextImpl(text);
      this.isActive = isActive;
      forceFactor(isActive ? 1f : 0f);
    }
  }

  private static int calculateTextColor (float alpha) {
    return ColorUtils.color((int) (255f * alpha), Theme.textAccentColor());
  }

  private boolean alwaysActive;

  public void setAlwaysActive (boolean alwaysActive) {
    if (this.alwaysActive != alwaysActive) {
      this.alwaysActive = alwaysActive;
      updateIsActive(false);
    }
  }

  private boolean hasFocus;

  private void updateIsActive (boolean animated) {
    setIsActive(alwaysActive || hasFocus || !isEmpty() || !StringUtils.isEmpty(editText.getHint()), animated);
  }

  @Override
  public void onFocusChange (View v, boolean hasFocus) {
    if (this.hasFocus != hasFocus) {
      this.hasFocus = hasFocus;
      updateIsActive(true);
      if (focusListener != null) {
        focusListener.onFocusChanged(this, hasFocus);
      }
    }
  }

  public boolean hasFocusInternal () {
    return hasFocus;
  }

  private boolean isActive;

  public void setIsActive (boolean isActive) {
    setIsActive(isActive, true);
  }

  public void setIsActive (boolean isActive, boolean animated) {
    if (this.isActive != isActive) {
      this.isActive = isActive;
      editText.setIsActive(hasFocus, false);
      if (useTextChangeAnimations && !isActive && editText.getText().toString().trim().length() > 0) {
        animateAlphaFactor(0f);
      } else if (animated) {
        animateFactor(isActive ? 1f : 0f);
      } else {
        forceFactor(isActive ? 1f : 0f);
      }
    } else {
      editText.setIsActive(hasFocus, animated);
    }
  }

  private FactorAnimator animator;

  private void forceFactor (float factor) {
    if (animator != null) {
      animator.forceFactor(factor);
    }
    setFactor(factor);
    if (useTextChangeAnimations) {
      forceAlphaFactor(factor);
    }
  }

  private static final int ANIMATOR_TEXT_ALPHA = 5;
  private FactorAnimator textAlphaAnimator;
  private float textAlpha;

  private void forceAlphaFactor (float factor) {
    if (textAlphaAnimator != null) {
      textAlphaAnimator.forceFactor(factor);
    }
    setTextAlphaFactor(factor);
  }

  private void animateAlphaFactor (float factor) {
    if (textAlphaAnimator == null) {
      textAlphaAnimator = new FactorAnimator(ANIMATOR_TEXT_ALPHA, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION - 20l, textAlpha);
    }
    textAlphaAnimator.animateTo(factor);
  }

  private void setTextAlphaFactor (float factor) {
    if (this.textAlpha != factor) {
      this.textAlpha = factor;
      editText.setTextColor(calculateTextColor(factor));
    }
  }

  private float factor;

  private void setFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      updateScale();
      editText.applyActiveFactor(factor);
    }
  }

  private void updateScale () {
    if (hintView != null) {
      final float scaleFactor = factor * (1f - reverseScaleFactor);
      float scale = 1f - .23076923f * scaleFactor;
      hintView.setScaleX(scale);
      hintView.setScaleY(scale);
      hintView.setTranslationY((float) -Screen.dp(20f) * scaleFactor);
    }
  }

  private static final int ANIMATOR_FACTOR = 0;
  private static final long DURATION = 150l;

  private void animateFactor (float toFactor) {
    if (animator == null) {
      animator = new FactorAnimator(ANIMATOR_FACTOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, DURATION, factor);
    }
    animator.animateTo(toFactor);
  }

  private static final int ANIMATOR_REVERSE_SCALE = 2;

  private float reverseScaleFactor;
  private FactorAnimator reverseScaleAnimator;

  private void animateReverseScaleFactor () {
    if (reverseScaleAnimator == null) {
      reverseScaleAnimator = new FactorAnimator(ANIMATOR_REVERSE_SCALE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 140l);
    } else {
      reverseScaleAnimator.forceFactor(0f);
    }
    reverseScaleAnimator.animateTo(1f);
  }

  private void setReverseScaleFactor (float factor) {
    if (this.reverseScaleFactor != factor) {
      this.reverseScaleFactor = factor;
      updateScale();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FACTOR: {
        setFactor(factor);
        break;
      }
      case ANIMATOR_FADE: {
        setFadeFactor(factor);
        break;
      }
      case ANIMATOR_REVERSE_SCALE: {
        setReverseScaleFactor(1f - factor);
        break;
      }
      case ANIMATOR_ERROR: {
        editText.setErrorFactor(factor);
        break;
      }
      case ANIMATOR_GOOD: {
        editText.setGoodFactor(factor);
        break;
      }
      case ANIMATOR_TEXT_ALPHA: {
        setTextAlphaFactor(factor);
        break;
      }
      case ANIMATOR_RADIO_VISIBILITY: {
        setRadioVisibility(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    switch (id) {
      case ANIMATOR_FACTOR: {
        if (finalFactor == 1f && useTextChangeAnimations && editText.getText().toString().trim().length() > 0) {
          animateAlphaFactor(1f);
        }
        break;
      }
      case ANIMATOR_TEXT_ALPHA: {
        if (finalFactor == 0f && useTextChangeAnimations && !isActive) {
          setTextImpl("");
          animateFactor(isActive ? 1f : 0f);
        }
        break;
      }
    }
  }

  private static final int ANIMATOR_FADE = 1;
  private FactorAnimator fadeAnimator;
  private float fadeFactor;

  private void setFadeFactor (float factor) {
    if (this.fadeFactor != factor) {
      this.fadeFactor = factor;

      final float easeFactor = AnimatorUtils.DECELERATE_INTERPOLATOR.getInterpolation(factor);
      final float alphaFactor = easeFactor <= .5f ? easeFactor / .5f : 1f - ((easeFactor - .5f) / .5f);
      if (easeFactor > .5f && !pendingApplied) {
        applyPending();
      }

      editText.setTextColor(calculateTextColor(1f - alphaFactor));
      if (hintView != null)
        hintView.setAlpha(1f - alphaFactor);
    }
  }

  private CharSequence pendingHint, pendingText;
  private boolean pendingTextIsPassword;
  private @Nullable Runnable pendingBetween;

  public void resetWithHint (@StringRes int pendingHint, boolean isPassword, Runnable between) {
    resetWithHint(Lang.getString(pendingHint), null, isPassword, between);
  }

  public void resetWithHint (@StringRes int pendingHint, @Nullable String text, boolean isPassword, Runnable between) {
    resetWithHint(Lang.getString(pendingHint), text, isPassword, between);
  }

  private boolean pendingApplied;

  private void applyPending () {
    if (!pendingApplied) {
      pendingApplied = true;
      editText.setIsPassword(pendingTextIsPassword);
      if (!StringUtils.isEmpty(pendingText)) {
        editText.setText(pendingText);
        editText.setSelection(pendingText.length());
      } else {
        editText.setText("");
      }
      if (hintView != null)
        hintView.setText(pendingHint);
      if (pendingBetween != null) {
        pendingBetween.run();
      }
    }
  }

  public void resetWithHint (final CharSequence pendingHint, final @Nullable CharSequence text, boolean isPassword, Runnable between) {
    if (fadeAnimator == null) {
      fadeAnimator = new FactorAnimator(ANIMATOR_FADE, this, AnimatorUtils.LINEAR_INTERPOLATOR, 360l, fadeFactor);
    } else {
      fadeAnimator.forceFactor(0f);
      fadeFactor = 0f;
    }

    this.pendingApplied = false;
    this.pendingHint = pendingHint;
    this.pendingText = text;
    this.pendingTextIsPassword = isPassword;
    this.pendingBetween = between;
    fadeAnimator.animateTo(1f);
  }

  private FactorAnimator errorAnimator;
  private static final int ANIMATOR_ERROR = 3;

  private boolean inErrorState;

  public void setInErrorState (boolean inErrorState) {
    if (this.inErrorState != inErrorState) {
      this.inErrorState = inErrorState;
      animateErrorState(inErrorState ? 1f : 0f);
    }
  }

  public boolean inErrorState () {
    return inErrorState;
  }

  private void animateErrorState (float toFactor) {
    if (errorAnimator == null) {
      errorAnimator = new FactorAnimator(ANIMATOR_ERROR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    errorAnimator.animateTo(toFactor);
  }

  private FactorAnimator goodAnimator;
  private static final int ANIMATOR_GOOD = 4;

  private boolean inGoodState;

  public void setInGoodState (boolean inGoodState) {
    if (this.inGoodState != inGoodState) {
      this.inGoodState = inGoodState;
      animateGoodState(inGoodState ? 1f : 0f);
    }
  }

  private void animateGoodState (float toFactor) {
    if (goodAnimator == null) {
      goodAnimator = new FactorAnimator(ANIMATOR_GOOD, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    }
    goodAnimator.animateTo(toFactor);
  }
}
