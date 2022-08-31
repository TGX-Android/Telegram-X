/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.v.EditText;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.ViewUtils;

public class CommandKeyboardLayout extends ViewGroup implements ViewTreeObserver.OnPreDrawListener, View.OnClickListener {
  private boolean oneTime;
  private int rowsCount;
  private int[] columnCount;

  private int spacingBig;
  private int spacing;
  private int minSize;

  private boolean blockLayout;

  private Callback callback;

  public CommandKeyboardLayout (Context context) {
    super(context);
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
        text.setText(c.text != null ? Emoji.instance().replaceEmoji(c.text) : "");

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

  private TextView genButton () {
    TextView text = new NoScrollTextView(getContext());
    ViewUtils.setBackground(text, Theme.rectSelector(4f, 0f, R.id.theme_color_chatKeyboardButton));
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(text);
    }
    text.setGravity(Gravity.CENTER);
    text.setTextColor(Theme.textAccentColor());
    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(text);
    }
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    text.setOnClickListener(this);
    //noinspection ResourceType
    text.setLayoutParams(new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    Views.setClickable(text);
    return text;
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
    return parent != null ? ((View) parent).getLayoutParams().height : 0;
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
    setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(getParentSize(), size), MeasureSpec.EXACTLY));
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

    if (changed) {
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
