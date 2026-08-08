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
 * File created on 13/08/2015 at 09:17
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.emoji.CustomEmojiId;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.CustomEmojiTextView;
import org.thunderdog.challegram.widget.TextView;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

public class CommandKeyboardLayout extends ViewGroup implements ViewTreeObserver.OnPreDrawListener, View.OnClickListener, Destroyable {
  private boolean oneTime;
  private int rowsCount;
  private int[] columnCount;

  private int spacingBig;
  private int spacing;
  private int minSize;

  private boolean blockLayout;

  private Callback callback;

  private final @Nullable Tdlib tdlib;

  public CommandKeyboardLayout (Context context, @Nullable Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    spacingBig = Screen.dp(15f);
    spacing = Screen.dp(10f);
    minSize = Screen.dp(42f);
    size = Keyboard.getSize();
    setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, size));
  }

  public void setThemeProvider (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public int getSize () {
    return size;
  }

  public void setKeyboard (TdApi.ReplyMarkupShowKeyboard keyboard) {
    oneTime = keyboard.oneTime;
    fillLayout(keyboard.rows);
    fitPending = true;
    resizeKeyboard(keyboard.resizeKeyboard);
    layoutChildren(Screen.currentWidth(), false, 0);
    requestLayout();
    if (getParent() != null) {
      ((ScrollView) getParent()).scrollTo(0, 0);
    }
  }

  private void fillLayout (TdApi.KeyboardButton[][] rows) {
    rowsCount = rows.length;
    columnCount = new int[rowsCount];
    blockLayout = true;

    int count = getChildCount();
    int column = 0, j = 0;

    for (TdApi.KeyboardButton[] columns : rows) {
      columnCount[column++] = columns.length;
      for (TdApi.KeyboardButton c : columns) {
        TextView text;
        if (j >= count) {
          text = genButton();
          addView(text);
        } else {
          text = (TextView) getChildAt(j);
          text.setVisibility(View.VISIBLE);
        }
        text.setTag(c);
        text.setText(buildButtonText(c));
        applyButtonStyle(text, c.style);

        j++;
      }
    }
    if (count > j) {
      for (int i = count - 1; i >= j; i--) {
        View view = getChildAt(i);
        if (view == null) {
          continue;
        }
        if (i > 10) {
          if (themeProvider != null) {
            themeProvider.removeThemeListenerByTarget(view);
          }
          if (view instanceof Destroyable) {
            ((Destroyable) view).performDestroy();
          }
          removeViewAt(i);
        } else {
          view.setVisibility(View.GONE);
        }
      }
    }
    blockLayout = false;
  }

  private void resizeKeyboard (boolean customSize) {
    int size = minSize * rowsCount + spacing * (rowsCount - 1) + spacingBig * 2;
    setSize(size, customSize ? size : Keyboard.getSize());
  }

  private @Nullable ViewController<?> themeProvider;

  private static final float MAX_BUTTON_TEXT_SIZE_DP = 16f;
  private static final float MIN_BUTTON_TEXT_SIZE_DP = 11f;

  private boolean fitPending;

  // Auto-size can leave mid-word breaks: a two-line layout with a split word still
  // "fits" its constraints. Instead shrink the font until the widest word (with the
  // emoji icon span measured through the paint) fits the button, so greedy breaking
  // never has to split inside a word.
  private void fitButtonText (TextView text, int availWidth) {
    CharSequence label = text.getText();
    if (label == null || label.length() == 0 || availWidth <= 0) {
      return;
    }
    TextPaint paint = new TextPaint(text.getPaint());
    float fitSize = MIN_BUTTON_TEXT_SIZE_DP;
    for (float size = MAX_BUTTON_TEXT_SIZE_DP; size >= MIN_BUTTON_TEXT_SIZE_DP; size -= .5f) {
      paint.setTextSize(Screen.dpf(size));
      if (maxWordWidth(label, paint) <= availWidth && lineCount(label, paint, availWidth) <= 2) {
        fitSize = size;
        break;
      }
    }
    if (text.getTextSize() != Screen.dpf(fitSize)) {
      text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fitSize);
    }
  }

  @SuppressWarnings("deprecation")
  private static int lineCount (CharSequence label, TextPaint paint, int availWidth) {
    return new StaticLayout(label, paint, Math.max(availWidth, 1), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false).getLineCount();
  }

  private static float maxWordWidth (CharSequence label, TextPaint paint) {
    float max = 0f;
    int length = label.length();
    int start = 0;
    for (int i = 0; i <= length; i++) {
      if (i == length || label.charAt(i) == ' ') {
        if (i > start) {
          max = Math.max(max, Layout.getDesiredWidth(label, start, i, paint));
        }
        start = i + 1;
      }
    }
    return max;
  }

  private TextView genButton () {
    TextView text = new CustomEmojiTextView(getContext(), tdlib);
    text.setScrollDisabled(true);
    ViewUtils.setBackground(text, Theme.rectSelector(4f, 0f, ColorId.chatKeyboardButton));
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(text);
    }
    text.setGravity(Gravity.CENTER);
    text.setMaxLines(2);
    text.setEllipsize(TextUtils.TruncateAt.END);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      text.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
      text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
    }
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, MAX_BUTTON_TEXT_SIZE_DP);
    text.setOnClickListener(this);
    //noinspection ResourceType
    text.setLayoutParams(new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    Views.setClickable(text);
    return text;
  }

  private CharSequence buildButtonText (TdApi.KeyboardButton button) {
    String text = button.text != null ? button.text : "";
    if (button.iconCustomEmojiId != 0 && tdlib != null) {
      SpannableStringBuilder b = new SpannableStringBuilder();
      b.append(EmojiStatusHelper.EMOJI);
      b.setSpan(new CustomEmojiId(button.iconCustomEmojiId, false), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      if (!text.isEmpty()) {
        b.append(' ').append(text);
      }
      return b;
    }
    return text;
  }

  private void applyButtonStyle (TextView text, @Nullable TdApi.ButtonStyle style) {
    final @ColorId int colorId = resolveStyleColorId(style);
    text.setTextColor(Theme.getColor(colorId));
    if (themeProvider != null) {
      themeProvider.addOrUpdateThemeTextColorListener(text, colorId);
    }
  }

  private static @ColorId int resolveStyleColorId (@Nullable TdApi.ButtonStyle style) {
    if (style == null) {
      return ColorId.text;
    }
    switch (style.getConstructor()) {
      case TdApi.ButtonStyleDefault.CONSTRUCTOR:
        return ColorId.text;
      case TdApi.ButtonStylePrimary.CONSTRUCTOR:
        return ColorId.textNeutral;
      case TdApi.ButtonStyleSuccess.CONSTRUCTOR:
        return ColorId.iconPositive;
      case TdApi.ButtonStyleDanger.CONSTRUCTOR:
        return ColorId.textNegative;
      default: {
        Td.assertButtonStyle_da99259d();
        throw Td.unsupported(style);
      }
    }
  }

  @Override
  public void performDestroy () {
    for (int i = getChildCount() - 1; i >= 0; i--) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      }
    }
  }

  @Override
  public void onClick (View v) {
    if (callback == null) {
     return;
    }
    TdApi.KeyboardButton button = (TdApi.KeyboardButton) v.getTag();

    switch (button.type.getConstructor()) {
      case TdApi.KeyboardButtonTypeText.CONSTRUCTOR: {
        callback.onCommandPressed(button.text);
        if (oneTime) {
          callback.onDestroyCommandKeyboard();
        }
        break;
      }
      case TdApi.KeyboardButtonTypeRequestLocation.CONSTRUCTOR: {
        callback.onRequestLocation(oneTime);
        break;
      }
      case TdApi.KeyboardButtonTypeRequestPoll.CONSTRUCTOR: {
        TdApi.KeyboardButtonTypeRequestPoll type = (TdApi.KeyboardButtonTypeRequestPoll) button.type;
        callback.onRequestPoll(oneTime, type.forceQuiz, type.forceRegular);
        break;
      }
      case TdApi.KeyboardButtonTypeRequestPhoneNumber.CONSTRUCTOR: {
        callback.onRequestContact(oneTime);
        break;
      }
    }
  }

  private int size;

  public int getParentSize () {
    ViewParent parent = getParent();
    if (parent != null) {
      ViewGroup viewGroup = (ViewGroup) parent;
      return viewGroup.getLayoutParams().height - viewGroup.getPaddingBottom();
    }
    return 0;
  }

  public void setSize (int size, int parentSize) {
    boolean hasParent = getParent() != null;
    if (hasParent) {
      ((View) getParent()).getLayoutParams().height = Math.min(parentSize, minSize * 7);
    }
    if (this.size != size) {
      this.size = size;
      getLayoutParams().height = size;
      requestLayout();
    } else if (hasParent) {
      getParent().requestLayout();
    }
    if (hasParent && callback != null) {
      callback.onResizeCommandKeyboard(getParentSize());
    }
  }

  // Layout

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(getParentSize(), size) + getPaddingBottom() + getPaddingTop(), MeasureSpec.EXACTLY));
    // children measuring is inside onLayout
  }

  int wasChanged;

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    int width = r - l;

    if (blockLayout) {
      if (changed) {
        wasChanged = width;
      }
      return;
    }

    if (wasChanged != 0) {
      width = wasChanged;
      changed = true;
      wasChanged = 0;
    }

    if (changed || fitPending) {
      fitPending = false;
      layoutChildren(width, true, t);
    } else {
      for (int i = 0; i < getChildCount(); i++) {
        View v = getChildAt(i);
        if (v.getVisibility() == View.GONE) continue;
        MarginLayoutParams mp = (MarginLayoutParams) v.getLayoutParams();
        v.measure(MeasureSpec.makeMeasureSpec(mp.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(mp.height, MeasureSpec.EXACTLY));
        v.layout(mp.leftMargin, t + mp.topMargin, mp.leftMargin + mp.width, t + mp.topMargin + mp.height);
      }
    }
  }

  private void layoutChildren (int currentWidth, boolean layout, int top) {
    int count = getChildCount();

    float frameWidth = currentWidth - spacingBig * 2 - spacing * (columnCount[0] - 1);
    float frameHeight = Math.max(getParentSize(), size) - spacingBig * 2 - spacing * (rowsCount - 1);

    int cx = spacingBig;
    int cy = spacingBig;
    int cw = (int) (frameWidth / (float) columnCount[0]);
    int ch = (int) (frameHeight / (float) rowsCount);

    int row = 0;
    int i = 0, c = 0;
    while (i < count) {
      if (columnCount[row] == 0) {
        row++;
        continue;
      }

      View v = getChildAt(i);
      layoutChild(v, cx, cy, cw, ch);
      if (layout) {
        if (v instanceof TextView) {
          fitButtonText((TextView) v, cw - Screen.dp(8f));
        }
        v.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        v.layout(cx, top + cy, cw + cx, top + cy + ch);
      }

      i++;
      c++;

      if (c == columnCount[row]) {
        if (++row == rowsCount) {
          break;
        }
        cx = spacingBig;
        cy = cy + spacing + ch;
        frameWidth = currentWidth - spacingBig * 2 - spacing * (columnCount[row] - 1);
        cw = (int) (frameWidth / (float) columnCount[row]);
        c = 0;
      } else {
        cx += cw + spacing;
      }
    }
  }

  private void layoutChild (View child, int x, int y, int w, int h) {
    MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();

    if (params.leftMargin != x || params.topMargin != y || params.width != w || params.height != h) {
      params.leftMargin = x;
      params.topMargin = y;
      params.width = w;
      params.height = h;
      child.requestLayout();
    }
  }

  public interface Callback {
    void onCommandPressed (String command);
    void onRequestLocation (boolean oneTime);
    void onRequestContact (boolean oneTime);
    void onRequestPoll (boolean oneTime, boolean forceQuiz, boolean forceRegular);
    void onDestroyCommandKeyboard ();
    void onResizeCommandKeyboard (int size);
  }

  // TODO: 13/08/15 Merge this code with EmojiLayout
  // Keyboard utils

  int keyboardState;

  public void showKeyboard (EditText input) {
    keyboardState = 1;
    Keyboard.show(input);
  }

  public void hideKeyboard (EditText input) {
    keyboardState = 2;
    Keyboard.hide(input);
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (keyboardState == 1 && visible) {
      framesDropped = 35;
    } else if (keyboardState == 2 && !visible) {
      keyboardState = 0;
    }
  }

  private int framesDropped;

  @Override
  public boolean onPreDraw () {
    if (keyboardState == 1) {
      if (++framesDropped >= 40) {
        framesDropped = 0;
        keyboardState = 0;
        return true;
      }
      return false;
    }

    if (keyboardState == 2) {
      if (++framesDropped >= 40) {
        framesDropped = 0;
        keyboardState = 0;
        return true;
      }
      return false;
    }

    return true;
  }
}
