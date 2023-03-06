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
 * File created on 31/08/2022, 19:40.
 */

package org.thunderdog.challegram.emoji;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.annotation.Nullable;

public class EmojiFilter implements InputFilter {
  private final CustomEmojiSurfaceProvider customEmojiSurfaceProvider;

  private int emojiCount;
  private final Emoji.CountLimiter countLimiter;

  public EmojiFilter () {
    this(null);
  }

  public EmojiFilter (@Nullable CustomEmojiSurfaceProvider customEmojiSurfaceProvider) {
    this(customEmojiSurfaceProvider, 0);
  }

  private EmojiFilter (CustomEmojiSurfaceProvider customEmojiSurfaceProvider, int maxCount) {
    this.customEmojiSurfaceProvider = customEmojiSurfaceProvider;
    if (maxCount > 0) {
      this.countLimiter = new Emoji.CountLimiter() {
        @Override
        public int getEmojiCount () {
          return emojiCount;
        }

        @Override
        public boolean incrementEmojiCount () {
          if (emojiCount >= maxCount)
            return false;
          emojiCount++;
          return true;
        }
      };
    } else {
      this.countLimiter = null;
    }
  }

  private CharSequence replaceEmojiOrNull (CharSequence source, int start, int end) {
    if (end < start) {
      int temp = start;
      start = end;
      end = temp;
    }
    if (end - start == 0) {
      return null;
    }
    if (source instanceof Spanned) {
      Spanned spanned = (Spanned) source;
      EmojiSpan[] alreadyFoundEmojis = spanned.getSpans(start, end, EmojiSpan.class);
      if (alreadyFoundEmojis != null && alreadyFoundEmojis.length > 0) {
        if (countLimiter != null) {
          emojiCount += alreadyFoundEmojis.length;
        }

        // replaceEmoji only for what's between existing EmojiSpan
        SpannableStringBuilder b = null;

        int cend = start;

        for (EmojiSpan span : alreadyFoundEmojis) {
          final int emojiStart = spanned.getSpanStart(span);
          final int emojiEnd = spanned.getSpanEnd(span);

          if (emojiStart == -1 || emojiEnd == -1 || emojiEnd - emojiStart <= 0)
            continue;

          if (emojiStart > cend) {
            // replaceEmoji for cend .. emojiStart
            CharSequence foundEmoji = Emoji.instance().replaceEmoji(source, cend, emojiStart, countLimiter);
            if (foundEmoji != source) {
              if (b == null) {
                b = new SpannableStringBuilder(source, start, cend);
              }
              b.append(foundEmoji);
            } else if (b != null) {
              b.append(source, cend, emojiStart);
            }
            cend = emojiStart;
          }

          if (emojiEnd < cend)
            throw new IllegalStateException();

          if (emojiEnd > cend && b != null) {
            b.append(source, cend, emojiEnd);
          }
          cend = emojiEnd;

          if (span.isCustomEmoji()) {
            if (customEmojiSurfaceProvider != null && span.belongsToSurface(customEmojiSurfaceProvider)) {
              continue;
            }

            CharSequence emojiCode = source.subSequence(emojiStart, emojiEnd);
            EmojiInfo info = Emoji.instance().getEmojiInfo(emojiCode, true);
            EmojiSpan newSpan = null;

            if (customEmojiSurfaceProvider != null) {
              newSpan = customEmojiSurfaceProvider.onCreateNewSpan(emojiCode, info, span.getCustomEmojiId());
            }
            if (newSpan == null) {
              // Drop custom emoji information and
              newSpan = Emoji.instance().newSpan(emojiCode, info);
            }
            if (b == null) {
              b = new SpannableStringBuilder(source, start, cend);
            }
            b.removeSpan(span);
            if (newSpan != null) {
              b.setSpan(newSpan, emojiStart, emojiEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
          }
        }
        if (cend < end) {
          // replaceEmoji for cend .. end
          CharSequence foundEmoji = Emoji.instance().replaceEmoji(source, cend, end, countLimiter);
          if (foundEmoji != source) {
            if (b == null) {
              b = new SpannableStringBuilder(source, start, cend);
            }
            b.append(foundEmoji);
          } else if (b != null) {
            b.append(source, cend, end);
          }
          cend = end;
        }

        return b;
      }
    }

    // replaceEmoji for entire text
    CharSequence foundEmoji = Emoji.instance().replaceEmoji(source, start, end, countLimiter);
    return foundEmoji != source ? foundEmoji : null;
  }

  @Override
  public CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    if (countLimiter != null) {
      this.emojiCount = 0;
      if (dstart > 0) {
        EmojiSpan[] spans = dest.getSpans(0, dstart, EmojiSpan.class);
        if (spans != null) {
          this.emojiCount += spans.length;
        }
      }
      if (dend < dest.length()) {
        EmojiSpan[] spans = dest.getSpans(dend, dest.length(), EmojiSpan.class);
        if (spans != null) {
          this.emojiCount += spans.length;
        }
      }
    }
    return replaceEmojiOrNull(source, start, end);
  }
}
