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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiSpan;
import org.thunderdog.challegram.helper.editable.EditableHelper;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.TextSelection;

import me.vkryl.core.lambda.Destroyable;
import tgx.td.Td;

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
          if (context != null && context.dismissLastOpenWindow(true, true, false, true)) {
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

  // Android: Copy-paste workaround

  @Override
  public boolean onTextContextMenuItem (@IdRes int id) {
    try {
      TextSelection selection = getTextSelection();
      if (selection == null) {
        return super.onTextContextMenuItem(id);
      }
      Editable editable = getText();
      switch (id) {
        case android.R.id.cut: {
          if (!selection.isEmpty()) {
            CharSequence copyText = editable.subSequence(selection.start, selection.end);
            editable.delete(selection.start, selection.end);
            U.copyText(copyText);
            setSelection(selection.start);
            return true;
          }
          break;
        }
        case android.R.id.copy: {
          if (!selection.isEmpty()) {
            CharSequence copyText = editable.subSequence(selection.start, selection.end);
            U.copyText(copyText);
            setSelection(selection.end);
            return true;
          }
          break;
        }
        case android.R.id.paste: {
          CharSequence pasteText = U.getPasteText(getContext());
          if (pasteText != null) {
            paste(pasteText, false);
            return true;
          }
          break;
        }
      }
    } catch (Throwable t) {
      Log.e("onTextContextMenuItem failed for id %s", t, Lang.getResourceEntryName(id));
    }
    return super.onTextContextMenuItem(id);
  }

  public void paste (TdApi.FormattedText pasteText, boolean needSelectPastedText) {
    paste(TD.toCharSequence(pasteText), needSelectPastedText);
  }

  public void paste (CharSequence pasteText, boolean needSelectPastedText) {
    paste(getTextSelection(), pasteText, needSelectPastedText);
  }

  private void paste (TextSelection selection, CharSequence pasteText, boolean needSelectPastedText) {
    if (selection == null) return;
    int start = selection.start;
    int end = selection.end;

    final Spanned spanned = (pasteText instanceof Spanned) ? (Spanned) pasteText : null;

    Editable editable = getText();
    if (EditableHelper.startsWithQuote(spanned) && editable.length() > 0) {
      editable.insert(end, "\n");
      start++;
      end++;
    }

    if (selection.isEmpty()) {
      editable.insert(start, pasteText);
    } else {
      editable.replace(start, end, pasteText);
    }
    if (pasteText instanceof Spanned) {
      // TODO: should this be a part of EmojiFilter?
      removeCustomEmoji(editable, start, start + pasteText.length());
    }
    if (needSelectPastedText) {
      setSelection(start, start + pasteText.length());
    } else {
      setSelection(start + pasteText.length());
    }
  }

  private static void removeCustomEmoji (Editable editable, int start, int end) {
    URLSpan[] urlSpans = editable.getSpans(start, end, URLSpan.class);
    if (urlSpans != null) {
      for (URLSpan urlSpan : urlSpans) {
        int urlStart = editable.getSpanStart(urlSpan);
        int urlEnd = editable.getSpanEnd(urlSpan);
        EmojiSpan[] emojiSpans = editable.getSpans(urlStart, urlEnd, EmojiSpan.class);
        for (EmojiSpan emojiSpan : emojiSpans) {
          if (emojiSpan.isCustomEmoji()) {
            int emojiStart = editable.getSpanStart(emojiSpan);
            int emojiEnd = editable.getSpanEnd(emojiSpan);
            editable.removeSpan(emojiSpan);
            if (emojiSpan instanceof Destroyable) {
              ((Destroyable) emojiSpan).performDestroy();
            }
            parseEmoji(editable, emojiStart, emojiEnd);
          }
        }
      }
    }
  }

  protected static void parseEmoji (Editable editable, int start, int end) {
    CharSequence cs = Emoji.instance().replaceEmoji(editable, start, end, null);
    if (cs != editable && cs instanceof Spanned) {
      Spanned emojiText = (Spanned) cs;
      EmojiSpan[] parsedEmojis = emojiText.getSpans(0, emojiText.length(), EmojiSpan.class);
      if (parsedEmojis != null) {
        for (EmojiSpan parsedEmoji : parsedEmojis) {
          int emojiStart = emojiText.getSpanStart(parsedEmoji);
          int emojiEnd = emojiText.getSpanEnd(parsedEmoji);
          editable.setSpan(
            parsedEmoji,
            start + emojiStart,
            start + emojiEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          );
        }
      }
    }
  }

  // FormattedText generation

  public final TdApi.FormattedText toOutputText (CharSequence cs, boolean applyMarkdown) {
    SpannableStringBuilder text = new SpannableStringBuilder(cs);
    BaseInputConnection.removeComposingSpans(text);
    TdApi.FormattedText formattedText = new TdApi.FormattedText(text.toString(), TD.toEntities(text, false));
    if (applyMarkdown) {
      Td.parseMarkdown(formattedText);
    }
    return formattedText;
  }

  public final TdApi.FormattedText getOutputText (boolean applyMarkdown) {
    return toOutputText(getText(), applyMarkdown);
  }

  public final boolean hasOnlyPremiumFeatures () {
    return TD.hasCustomEmoji(getOutputText(false));
  }

  public static CharSequence nonModifiableCopy (CharSequence cs) {
    if (cs == null || cs instanceof String) {
      return cs;
    }
    if (cs instanceof StringBuilder) {
      return cs.toString();
    }
    return new SpannableStringBuilder(cs);
  }
}
