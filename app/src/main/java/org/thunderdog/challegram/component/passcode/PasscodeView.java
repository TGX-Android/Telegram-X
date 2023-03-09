/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 07/08/2015 at 20:23
 */
package org.thunderdog.challegram.component.passcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.PasscodeBuilder;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ColorUtils;

public class PasscodeView extends View {
  private static final int TEXT_PASSWORD_TOP = 114;
  private static final int TEXT_PIN_TOP = 82;
  private static final int TEXT_PATTERN_TOP = 138;
  private static final int TEXT_GESTURE_TOP = 74;

  private static final int LINE_PASSWORD_TOP = 170;
  private static final int LINE_PIN_TOP = 148;

  private static final int LINE_PASSWORD_PADDING = 44;
  private static final int LINE_PIN_PADDING = 20;

  private boolean isIntercepted;

  private int orientation;
  private int state;
  private int mode;
  private String text;

  private Callback callback;

  public PasscodeView (Context context) {
    super(context);
    this.orientation = UI.getOrientation();
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      buildLayout();
    }
  }

  private float textLeft, textTop, textWidth;

  private float lineLeft, lineTop, lineRight;
  public void setModeAndState (int mode, int state) {
    if (this.mode != mode) {
      this.state = state;
      setMode(mode);
    } else {
      setState(state);
    }
  }

  public void setMode (int mode) {
    if (this.mode != mode) {
      this.mode = mode;
      updateText();
      layoutLine();
      buildLayout();
      invalidate();
    }
  }

  public void setState (int state) {
    if (this.state != state) {
      this.state = state;
      updateText();
      invalidate();
      if (state == Passcode.STATE_CONFIRM) {
        callback.updateConfirmMode();
      }
    }
  }

  public int getState () {
    return state;
  }

  public String getText () {
    return text;
  }

  public boolean isIntercepted () {
    return isIntercepted;
  }

  private void layoutLine () {
    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        lineLeft = Screen.dpf(LINE_PASSWORD_PADDING);
        lineTop = Screen.dpf(LINE_PASSWORD_TOP);
        break;
      }
      case Passcode.MODE_PINCODE: {
        lineLeft = UI.isLandscape() ? Screen.dpf(LINE_PIN_PADDING) : (Screen.currentWidth() - (Screen.dp(PinInputLayout.BUTTON_WIDTH) * 3)) / 2;
        lineTop = Screen.dpf(LINE_PIN_TOP);
        break;
      }
      default: {
        lineTop = -1;
        break;
      }
    }
    if (lineTop != -1) {
      lineTop += getPaddingTop();
      lineRight = mode == Passcode.MODE_PINCODE && orientation == Configuration.ORIENTATION_LANDSCAPE ? ((float) getMeasuredWidth() * .5f) - lineLeft : getMeasuredWidth() - lineLeft;
    }
  }

  // private Paint iconPaint;
  private Drawable icon;
  private float iconLeft, iconTop;

  public void setDisplayLogo () {
    // iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    icon = Drawables.load(R.drawable.deproko_logo_telegram_passcode_56);
  }

  public void layoutIcon () {
    if (icon != null) {
      iconLeft = textLeft + textWidth * .5f - (float) icon.getMinimumWidth() * .5f;
      iconTop = Math.max(textTop * .5f - (float) icon.getMinimumHeight() * .5f, Screen.dp(40f));
    }
  }

  private void layoutContent (int totalWidth, int totalHeight) {
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        layoutPattern(totalWidth, totalHeight);
        break;
      }
      case Passcode.MODE_PINCODE: {
        layoutPincode();
        break;
      }
    }
  }

  private float getTextTop () {
    float offset = 9f;
    switch (mode) {
      case Passcode.MODE_PASSWORD: {
        return Screen.dpf(TEXT_PASSWORD_TOP + offset) + getPaddingTop();
      }
      case Passcode.MODE_PINCODE: {
        return Screen.dpf(TEXT_PIN_TOP + offset) + getPaddingTop();
      }
      case Passcode.MODE_PATTERN: {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          return Screen.dpf(TEXT_PATTERN_TOP + offset) + getPaddingTop();
        }
        float height = Screen.currentActualHeight() - HeaderView.getSize(false) - getPaddingTop();
        return height * .5f + getPaddingTop();
      }
      case Passcode.MODE_GESTURE:
      case Passcode.MODE_FINGERPRINT: {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
          return Screen.dpf(TEXT_GESTURE_TOP + offset) + getPaddingTop();
        }
        float height = Screen.currentActualHeight() - HeaderView.getSize(false) - getPaddingTop();
        return height * .5f + getPaddingTop();
      }
      default: {
        return 0f;
      }
    }
  }

  public void updateTextAndInvalidate () {
    updateText();
    invalidate();
  }

  private void updateText () {
    String suffix = callback.makeBruteForceSuffix();
    int seconds = Settings.instance().getPasscodeBlockSeconds(suffix);
    if (seconds > 0) {
      text = Lang.getString(R.string.format_PasscodeTooManyAttempts, Lang.plural(R.string.TryAgainSeconds, seconds));
    } else {
      text = Passcode.getActionName(mode, state);
    }
    if (text == null) {
      return;
    }
    if (state == Passcode.STATE_CONFIRM && Passcode.canBeInvisible(mode) && !callback.isPasscodeVisible()) {
      text = Lang.getString(R.string.passcode_confirm_invisible, text);
    }
    textWidth = U.measureText(text, Paints.getRegularTextPaint(13f));
    textTop = getTextTop();
    textLeft = getTextLeft();
  }

  private float getTextLeft () {
    float center = (float) getMeasuredWidth() * .5f;
    return (mode == Passcode.MODE_PINCODE || mode == Passcode.MODE_PATTERN || mode == Passcode.MODE_GESTURE || mode == Passcode.MODE_FINGERPRINT) && orientation == Configuration.ORIENTATION_LANDSCAPE ? center * .5f - textWidth * .5f : center - textWidth * .5f;
  }

  private void buildLayout () {
    int totalWidth = getMeasuredWidth();
    if (totalWidth == 0) return;

    textLeft = getTextLeft();
    layoutLine();
    layoutIcon();

    layoutContent(totalWidth, getMeasuredHeight());
  }

  @Override
  protected void onDraw (Canvas c) {
    // int decentColor = ;
    if (lineTop != -1) {
      c.drawRect(lineLeft, lineTop, lineRight, lineTop + Screen.dp(1f), Paints.fillingPaint(ColorUtils.alphaColor(.3f, Theme.getColor(R.id.theme_color_passcodeText))));
    }
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        drawPattern(c);
        break;
      }
      case Passcode.MODE_PINCODE: {
        drawPincode(c);
        break;
      }
    }
    if (text != null) {
      c.drawText(text, textLeft, textTop, Paints.getRegularTextPaint(13f, Theme.passcodeSubtitleColor()));
    }
    if (icon != null) {
      Drawables.draw(c, icon, iconLeft, iconTop, Paints.getPasscodeIconPaint());
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        return onPatternTouch(event);
      }
      default: {
        return super.onTouchEvent(event);
      }
    }
  }

  // Orientation

  public void setOrientation (int orientation) {
    this.orientation = orientation;
    switch (mode) {
      case Passcode.MODE_PATTERN: {
        textTop = getTextTop();
        clearPattern(false);
        break;
      }
      case Passcode.MODE_GESTURE:
      case Passcode.MODE_FINGERPRINT: {
        textTop = getTextTop();
        break;
      }
      case Passcode.MODE_PINCODE: {
        layoutPincode();
        break;
      }
    }
  }

  // Pattern

  private static final int PATTERN_TOP = 206;
  private static final int PATTERN_PADDING = 72;
  private static final int PATTERN_CELL_SIZE = 102;

  private ArrayList<PatternLine> patternLines;
  private PatternDot[] patternDots;

  private float patternOffset;

  private PatternLine patternLine;
  private @Nullable PasscodeBuilder pattern;

  @SuppressWarnings ("SuspiciousNameCombination")
  private void layoutPattern (int totalWidth, int totalHeight) {
    if (patternDots == null) {
      pattern = new PasscodeBuilder();
      patternLine = new PatternLine(0, 0, 0, 0);
      patternLines = new ArrayList<>();
      patternDots =  new PatternDot[] {
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this),
        new PatternDot(this)
      };
    }

    float cellSize = Screen.dpf(PATTERN_CELL_SIZE);
    float paddingLeft = Screen.dpf(PATTERN_PADDING);
    float paddingTop, paddingBottom;

    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      paddingTop = Screen.dp(12f);
      paddingBottom = paddingTop;
    } else {
      paddingTop = Screen.dpf(PATTERN_TOP);
      paddingBottom = Screen.dpf(88f);
    }

    patternOffset = Screen.dpf(26f);

    float factor = 1f;

    float width = cellSize * 2f;
    float height = width;
    float fullWidth = width + paddingLeft * 2f;
    float fullHeight = height + paddingTop + paddingBottom;

    if (fullWidth != totalWidth) {
      factor = totalWidth / fullWidth;
    }

    if (fullHeight != totalHeight) {
      factor = Math.min(factor, totalHeight / fullHeight);
    }

    if (factor != 1f) {
      if (factor < 1f) {
        cellSize *= factor;
        patternOffset = Math.max(Screen.dpf(PatternDot.RADIUS * 2f), patternOffset * factor);
        width = cellSize * 2f;
        height = width;
        paddingTop *= factor;
        // paddingBottom *= factor;
      }
      if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        paddingLeft = (totalWidth - width) * .5f;
      } else {
        paddingTop = (totalHeight - height) * .5f;
      }
    }

    float patternLeft;
    float patternTop;
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      patternLeft = totalWidth - width - paddingLeft;
      patternTop = paddingTop;
    } else {
      patternLeft = paddingLeft;
      patternTop = paddingTop + getPaddingTop();
    }


    float cx = patternLeft;
    float cy = patternTop;
    int i = 0;

    for (PatternDot dot : patternDots) {
      dot.setXY(cx, cy);
      i++;
      if (i % 3 == 0) {
        cx = patternLeft;
        cy += cellSize;
      } else {
        cx += cellSize;
      }
    }
  }

  private boolean selectPatternDot (float x, float y, boolean noLine) {
    int i = 0;
    for (PatternDot dot : patternDots) {
      if (dot.isMatching(x, y, patternOffset)) {
        dot.setSelected(true);
        appendPatternPoint(i, noLine);
        if (!noLine) {
          patternLine.setFromXY(x, y);
          patternLine.setFromXYAnimated(dot.getX(), dot.getY());
        }
        return true;
      }
      i++;
    }
    return false;
  }

  private void appendPatternPoint (int dot, boolean noLine) {
    if (pattern == null) {
      return;
    }

    if (!noLine && pattern.getSize() > 0) {
      PatternDot prev = patternDots[pattern.getLastDigit()];
      PatternDot cur = patternDots[dot];

      float x = prev.getX() + (cur.getX() - prev.getX()) * .5f;
      float y = prev.getY() + (cur.getY() - prev.getY()) * .5f;
      selectPatternDot(x, y, true);

      PatternLine line = new PatternLine(prev.getX(), prev.getY(), patternLine.getToX(), patternLine.getToY());
      line.setParentView(this);
      line.setToXYAnimated(cur.getX(), cur.getY());
      patternLines.add(line);
    }

    pattern.append(dot);
  }

  private float patternFactor;
  private boolean patternAnimating;

  public void setPatternFactor (float factor) {
    if (this.patternFactor != factor) {
      this.patternFactor = factor;
      PatternLine.setAlpha(1f - factor);
      invalidate();
    }
  }

  public void clearPattern (boolean animated) {
    isIntercepted = false;
    if (!animated) {
      if (pattern != null) {
        pattern.clear();
        patternLines.clear();
        for (PatternDot dot : patternDots) {
          dot.setSelected(false);
        }
      }
    } else {
      this.patternFactor = 0f;
      this.patternAnimating = true;
      ValueAnimator animator = AnimatorUtils.simpleValueAnimator();
      // FIXME do with implements
      animator.addUpdateListener(animation -> setPatternFactor(AnimatorUtils.getFraction(animation)));
      animator.setInterpolator(AnimatorUtils.DECELERATE_INTERPOLATOR);
      animator.setDuration(160l);
      animator.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd (Animator animation) {
          clearPattern(false);
          PatternLine.setAlpha(1f);
          patternAnimating = false;
          invalidate();
        }
      });
      animator.start();
    }
  }

  private boolean onPatternTouch (MotionEvent e) {
    if (patternAnimating) {
      return false;
    }
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        float x = e.getX();
        float y = e.getY();
        patternLine.setToXY(x, y);
        clearPattern(false);
        if (selectPatternDot(x, y, false)) {
          isIntercepted = true;
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isIntercepted) {
          float x = e.getX();
          float y = e.getY();
          patternLine.setToXY(x, y);
          if (!selectPatternDot(x, y, false)) {
            invalidate();
          }
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isIntercepted) {
          if (callback != null) {
            callback.handlePattern(new PasscodeBuilder(pattern));
          }
          clearPattern(true);
          invalidate();
          return true;
        }
        break;
      }
    }

    return false;
  }

  private void drawPattern (Canvas c) {
    for (PatternDot dot : patternDots) {
      dot.draw(c);
    }
    if (callback.isPasscodeVisible()) {
      for (PatternLine line : patternLines) {
        line.draw(c);
      }
      if (isIntercepted || patternAnimating) {
        patternLine.draw(c);
      }
    }
  }

  private PincodeOutputView pincodeView;

  private void layoutPincode () {
    if (pincodeView == null) {
      pincodeView = new PincodeOutputView();
      pincodeView.setParentView(this);
    }
    pincodeView.setRect(lineLeft, lineTop - Screen.dp(52f) - 1, lineRight, lineTop - 1);
  }

  private void drawPincode (Canvas c) {
    pincodeView.draw(c);
  }

  public PincodeOutputView getPincodeOutput () {
    return pincodeView;
  }

  public Callback getCallback () {
    return callback;
  }

  public interface Callback {
    boolean isPasscodeVisible ();
    void updateConfirmMode ();
    void handlePattern (PasscodeBuilder pattern);
    void handlePincode (PasscodeBuilder pincode);
    boolean inSetupMode ();
    String makeBruteForceSuffix ();
  }
}
