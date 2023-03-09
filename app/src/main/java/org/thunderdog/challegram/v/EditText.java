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
 * File created on 06/12/2016
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.TextSelection;

public class EditText extends android.widget.EditText {
  private boolean ignoreCustomStuff;
  private TextSelection selection;

  public EditText (Context context) {
    super(context);
  }

  public EditText (Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public EditText (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public final void initDefault () {
    setTextColor(Theme.textAccentColor());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    setHintTextColor(Theme.textPlaceholderColor());
    setTypeface(Fonts.getRobotoRegular());
    setBackgroundResource(R.drawable.bg_edittext);
  }

  public void setIgnoreCustomStuff (boolean ignoreCustomStuff) {
    this.ignoreCustomStuff = ignoreCustomStuff;
  }

  public interface BackspaceListener {
    boolean onBackspacePressed (EditText v, Editable text, int selectionStart, int selectionEnd);
  }

  private BackspaceListener backspaceListener;

  public void setBackspaceListener (BackspaceListener listener) {
    this.backspaceListener = listener;
  }

  @Nullable
  public final TextSelection getTextSelection () {
    if (!UI.inUiThread()) {
      throw new IllegalStateException();
    }
    if (selection == null) {
      selection = new TextSelection();
    }
    if (Views.getSelection(this, selection)) {
      return selection;
    } else {
      return null;
    }
  }

  @Override
  public void setSelection (int start, int stop) {
    try {
      super.setSelection(start, stop);
    } catch (Throwable t) {
      Log.e("Cannot set selection for range %d..%d for length %d", t, start, stop, getText().length());
    }
  }

  @Override
  public void setSelection (int index) {
    try {
      super.setSelection(index);
    } catch (Throwable t) {
      Log.e("Cannot set selection for index %d for length %d", t, index, getText().length());
    }
  }

  protected boolean onKeyboardBackPress () {
    return false;
  }

  protected boolean onKeyboardBackspacePress () {
    if (backspaceListener != null) {
      TextSelection selection = getTextSelection();
      if (selection != null) {
        return backspaceListener.onBackspacePressed(this, getText(), selection.start, selection.end);
      }
    }
    return false;
  }

  @Override
  public InputConnection onCreateInputConnection (EditorInfo outAttrs) {
    if (backspaceListener == null) {
      return super.onCreateInputConnection(outAttrs);
    } else {
      return new BackspaceConnectionWrapper(this, super.onCreateInputConnection(outAttrs), true);
    }
  }

  @Override
  public boolean onKeyDown (int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && onKeyboardBackspacePress()) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  private static class BackspaceConnectionWrapper extends InputConnectionWrapper {
    private final EditText editText;

    public BackspaceConnectionWrapper (EditText editText, InputConnection target, boolean mutable) {
      super(target, mutable);
      this.editText = editText;
    }

    @Override
    public boolean sendKeyEvent (KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL && editText.onKeyboardBackspacePress()) {
        return false;
      }
      return super.sendKeyEvent(event);
    }


    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
      // magic: in latest Android, deleteSurroundingText(1, 0) will be called for backspace
      if (beforeLength == 0 && afterLength == 0) {
        // backspace
        return sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
          && sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      }

      return super.deleteSurroundingText(beforeLength, afterLength);
    }
  }

  public void setUseIncognitoKeyboard (int imeOptions) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
    } else {
      imeOptions |= 0x1000000;
    }
    setImeOptions(imeOptions);
  }

  @Override
  public boolean onKeyPreIme (int keyCode, KeyEvent e) {
    if (!ignoreCustomStuff && keyCode == KeyEvent.KEYCODE_BACK) {
      switch (e.getAction()) {
        case KeyEvent.ACTION_DOWN: {
          BaseActivity context = UI.getContext(getContext());
          if (context != null && context.dismissLastOpenWindow(true, true, false)) {
            return true;
          }
          if (onKeyboardBackPress()) {
            return true;
          }
          break;
        }
      }
    }
    return super.onKeyPreIme(keyCode, e);
  }
}
