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
 * File created on 31/08/2022, 21:08.
 */

package org.thunderdog.challegram.emoji;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.widget.EditText;

import org.thunderdog.challegram.telegram.TGLegacyManager;

import me.vkryl.core.lambda.Destroyable;

public class EmojiLoadSilentFilter implements InputFilter, Destroyable, TGLegacyManager.EmojiLoadListener {
  private final EditText targetView;
  private boolean isRegistered;

  public EmojiLoadSilentFilter (EditText targetView) {
    // FIXME: InputFilter is not intended to be targeted by a single view
    this.targetView = targetView;
    checkSpans(targetView.getText());
  }

  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    boolean needListener =
      hasEmoji(dest, 0, dstart) ||
      hasEmoji(dest, dend, dest.length()) ||
      (source instanceof Spanned && hasEmoji((Spanned) source, start, end));
    setRegistered(needListener);
    return null;
  }

  private boolean hasEmoji (Spanned spannable, int start, int end) {
    return spannable != null && spannable.length() > 0 && end > start && spannable.nextSpanTransition(start, end, EmojiSpan.class) < end;
  }

  private void checkSpans (Editable editable) {
    final boolean needListener = hasEmoji(editable, 0, editable.length());
    setRegistered(needListener);
  }

  private void setRegistered (boolean isRegistered) {
    if (this.isRegistered != isRegistered) {
      this.isRegistered = isRegistered;
      if (isRegistered) {
        TGLegacyManager.instance().addEmojiListener(this);
      } else {
        TGLegacyManager.instance().removeEmojiListener(this);
      }
    }
  }

  public static void invalidateEmojiSpans (EditText targetView, boolean force) {
    Editable editable = targetView.getText();
    EmojiSpan[] emojiSpans = editable.getSpans(0, editable.length(), EmojiSpan.class);
    if (!force) {
      boolean needRefresh = false;
      for (EmojiSpan span : emojiSpans) {
        if (span.needRefresh()) {
          needRefresh = true;
          break;
        }
      }
      if (!needRefresh) {
        return;
      }
    }
    for (EmojiSpan span : emojiSpans) {
      int spanStart = editable.getSpanStart(span);
      int spanEnd = editable.getSpanEnd(span);
      editable.removeSpan(span);
      editable.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  @Override
  public void onEmojiPartLoaded () {
    invalidateEmojiSpans(targetView, false);
  }

  @Override
  public void onEmojiPackChanged () {
    invalidateEmojiSpans(targetView, true);
  }

  @Override
  public void performDestroy () {
    setRegistered(false);
  }
}
