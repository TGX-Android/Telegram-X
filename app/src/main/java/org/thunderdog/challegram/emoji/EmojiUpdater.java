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
 * File created on 31/08/2022, 21:08.
 */

package org.thunderdog.challegram.emoji;

import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.widget.TextView;

import org.thunderdog.challegram.telegram.TGLegacyManager;

import me.vkryl.core.lambda.Destroyable;

public class EmojiUpdater implements InputFilter, TGLegacyManager.EmojiLoadListener, Destroyable {
  private final TextView targetView;
  private boolean isRegistered;

  public EmojiUpdater (TextView targetView) {
    this.targetView = targetView;
  }

  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    setRegistered(hasEmoji(source, start, end) || hasEmoji(dest, 0, dstart) || hasEmoji(dest, dend, dest.length()));
    return null;
  }

  private boolean hasEmoji (CharSequence cs, int start, int end) {
    if (cs instanceof Spanned && cs.length() > 0 && end > start) {
      Spanned spanned = (Spanned) cs;
      if (spanned.nextSpanTransition(start, end, EmojiSpan.class) < end) {
        return true;
      }
      EmojiSpan[] emojiSpans = spanned.getSpans(start, end, EmojiSpan.class);
      return emojiSpans != null && emojiSpans.length > 0;
    }
    return false;
  }

  public void checkSpans () {
    CharSequence cs = targetView.getText();
    final boolean needListener = hasEmoji(cs, 0, cs.length());
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

  public static void invalidateEmojiSpan (TextView targetView, EmojiSpan emojiSpan) {
    final CharSequence text = targetView.getText();
    if (text instanceof Spannable) {
      Spannable spannable = (Spannable) text;
      int start = spannable.getSpanStart(emojiSpan);
      int end = spannable.getSpanEnd(emojiSpan);
      if (start != -1 && end != -1) {
        spannable.removeSpan(emojiSpan);
        spannable.setSpan(emojiSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    }
  }

  public static void invalidateEmojiSpans (TextView targetView, boolean force, boolean custom) {
    final CharSequence text = targetView.getText();
    if (!(text instanceof Spanned))
      return;
    Spannable spannable = text instanceof Spannable ? (Spannable) text : new SpannableStringBuilder(text);
    EmojiSpan[] emojiSpans = spannable.getSpans(0, spannable.length(), EmojiSpan.class);
    if (!force) {
      boolean needRefresh = false;
      for (EmojiSpan span : emojiSpans) {
        if (span.needRefresh() && span.isCustomEmoji() == custom) {
          needRefresh = true;
          break;
        }
      }
      if (!needRefresh) {
        return;
      }
    }
    boolean updated = false;
    for (EmojiSpan span : emojiSpans) {
      if (span.isCustomEmoji() != custom)
        continue;
      int spanStart = spannable.getSpanStart(span);
      int spanEnd = spannable.getSpanEnd(span);
      spannable.removeSpan(span);
      spannable.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      updated = true;
    }
    if (updated) {
      if (text != spannable) {
        targetView.setText(spannable);
      }
      targetView.invalidate();
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    invalidateEmojiSpans(targetView, isPackSwitch, false);
  }

  @Override
  public void performDestroy () {
    setRegistered(false);
  }
}
