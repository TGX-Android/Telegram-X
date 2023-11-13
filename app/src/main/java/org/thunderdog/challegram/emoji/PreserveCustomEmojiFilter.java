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
 * File created on 28/08/2023
 */
package org.thunderdog.challegram.emoji;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.inputmethod.BaseInputConnection;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;

import me.vkryl.td.Td;

/**
 * Workaround for bug present in Samsung Keyboard version 5.6.10.40 with "Predictive text" toggle on (com.samsung.android.honeyboard/.service.HoneyBoardService)
 */
public class PreserveCustomEmojiFilter implements InputFilter {
  @Override
  public CharSequence filter (CharSequence sourceRaw, int start, int end, Spanned dest, int dstart, int dend) {
    if (!(sourceRaw instanceof Spanned)) {
      return null;
    }
    final Spanned source = (Spanned) sourceRaw;
    final int length = source.length();
    // Entire non-empty text was replaced with the text of the same length
    if (start == 0 && dstart == 0 && end > start && dend == end && end == length && dest.length() == length) {
      int transitionStart = dest.nextSpanTransition(dstart, dend, CustomEmojiSpanImpl.class);
      // Custom emoji were not present
      if (transitionStart == dend) {
        return null;
      }
      transitionStart = source.nextSpanTransition(start, end, CustomEmojiSpanImpl.class);
      // Custom emoji are still present
      if (transitionStart != end) {
        return null;
      }

      SpannableStringBuilder newText = new SpannableStringBuilder(source, start, end);

      // Text changed
      if (!dest.toString().equals(newText.toString())) {
        return null;
      }

      CustomEmojiSpanImpl[] lostCustomEmoji = dest.getSpans(0, dest.length(), CustomEmojiSpanImpl.class);
      for (CustomEmojiSpanImpl span : lostCustomEmoji) {
        int emojiStart = dest.getSpanStart(span);
        int emojiEnd = dest.getSpanEnd(span);
        if (emojiStart != -1 && emojiEnd != -1) {
          newText.setSpan(span, emojiStart, emojiEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }

      SpannableStringBuilder oldContent = new SpannableStringBuilder(dest, dstart, dend);
      BaseInputConnection.removeComposingSpans(oldContent);
      SpannableStringBuilder newContent = new SpannableStringBuilder(newText, start, end);
      BaseInputConnection.removeComposingSpans(newContent);
      TdApi.TextEntity[] oldEntities = TD.toEntities(oldContent, false);
      TdApi.TextEntity[] newEntities = TD.toEntities(newContent, false);
      // Entities changed
      if (!Td.equalsTo(oldEntities, newEntities, true)) {
        return null;
      }

      return newText;
    }
    return null;
  }
}
