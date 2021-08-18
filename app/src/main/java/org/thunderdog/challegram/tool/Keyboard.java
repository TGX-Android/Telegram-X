/**
 * File created on 23/04/15 at 19:19
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.tool;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.InputMethodManager;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.unsorted.Settings;

public class Keyboard {
  public static void show (View view) {
    if (view != null) {
      try {
        view.requestFocus();

        InputMethodManager manager;

        manager = (InputMethodManager) UI.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.showSoftInput(view, 0);
      } catch (Throwable throwable) {
        Log.e("Cannot show keyboard", throwable);
      }
    }
  }

  public static void showSuggestions (View view, CompletionInfo[] suggestions) {
    if (view != null) {
      try {
        InputMethodManager manager;

        manager = (InputMethodManager) UI.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        manager.displayCompletions(view, suggestions);
      } catch (Throwable throwable) {
        Log.e("Cannot show suggestions", throwable);
      }
    }
  }

  public static void hide (View view) {
    if (view != null) {
      InputMethodManager manager;

      manager = (InputMethodManager) UI.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  /*public static void hideForAll (ViewGroup group) {
    final int size = group.getChildCount();
    for (int i = 0; i < size; i++) {
      View view = group.getChildAt(i);
      if (view instanceof ViewGroup) {
        hideForAll((ViewGroup) view);
      } else if (view instanceof EditText) {
        Keyboard.hide(view);
      }
    }
  }*/

  public static void hideList (View... views) {
    for (View view : views) {
      hide(view);
    }
  }

  /*public static void clearSizes () {
    SharedPreferences.Editor editor;
    editor = Prefs.instance().getStorage(Prefs.KEYBOARD_STORAGE_KEY).edit();
    editor.remove("size" + Configuration.ORIENTATION_LANDSCAPE);
    editor.remove("size" + Configuration.ORIENTATION_PORTRAIT);
    editor.remove("size" + Configuration.ORIENTATION_UNDEFINED);
    editor.apply();
  }*/

  public static void processSize (int size) {
    if (size > 0) {
      //int prevSize = getSize(0);
      //if (force || size > prevSize || prevSize - size > Screen.dp(56f)) {
      Settings.instance().setKeyboardSize(UI.getOrientation(), size);
      //}
    }
  }

  public static int getSize () {
    return getSize(Math.round((float) Screen.currentActualHeight() * .45f));
  }

  public static int getSize (int defValue) {
    int size = Settings.instance().getKeyboardSize(UI.getOrientation(), defValue);
    return Math.max(size, Screen.dp(75f));
  }

  public interface OnStateChangeListener {
    void onKeyboardStateChanged (boolean isVisible);
    void closeAdditionalKeyboards ();
  }
}
