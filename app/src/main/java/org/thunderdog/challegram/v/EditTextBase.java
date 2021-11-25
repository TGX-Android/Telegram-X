package org.thunderdog.challegram.v;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.tool.UI;

/**
 * Date: 06/12/2016
 * Author: default
 */

public class EditTextBase extends android.widget.EditText {
  private boolean ignoreCustomStuff;

  public EditTextBase (Context context) {
    super(context);
  }

  public EditTextBase (Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public EditTextBase (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setIgnoreCustomStuff (boolean ignoreCustomStuff) {
    this.ignoreCustomStuff = ignoreCustomStuff;
  }

  public interface BackspaceListener {
    boolean onBackspacePressed (EditTextBase v, Editable text, int selectionStart, int selectionEnd);
  }

  private BackspaceListener backspaceListener;

  public void setBackspaceListener (BackspaceListener listener) {
    this.backspaceListener = listener;
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
    return backspaceListener != null && backspaceListener.onBackspacePressed(this, getText(), getSelectionStart(), getSelectionEnd());
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
    private final EditTextBase editTextBase;

    public BackspaceConnectionWrapper (EditTextBase editTextBase, InputConnection target, boolean mutable) {
      super(target, mutable);
      this.editTextBase = editTextBase;
    }

    @Override
    public boolean sendKeyEvent (KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL && editTextBase.onKeyboardBackspacePress()) {
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
