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
 * File created on 02/10/2023
 */
package org.thunderdog.challegram.util;

import android.text.Spannable;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiSpan;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;

public class NonBubbleEmojiLayout {
  public final ArrayList<Item> items = new ArrayList<>();

  @Nullable
  public static NonBubbleEmojiLayout create (TdApi.FormattedText text) {
    NonBubbleEmojiLayout layout = new NonBubbleEmojiLayout();
    if (isValidEmojiText(text, layout)) {
      return layout;
    } else {
      return null;
    }
  }

  private void addSpan (String emoji, long customEmojiId) {
    items.add(new Item(Item.EMOJI, emoji, customEmojiId));
  }

  private void addRow () {
    items.add(new Item(Item.LINE_BREAK));
  }

  private void addSpace () {
    items.add(new Item(Item.SPACE));
  }

  public static boolean isValidEmojiText (TdApi.FormattedText formattedText) {
    return isValidEmojiText(formattedText, null);
  }

  private static boolean isValidEmojiText (TdApi.FormattedText formattedText, @Nullable NonBubbleEmojiLayout layout) {
    if (StringUtils.isEmpty(formattedText.text)) {
      return false;
    }

    final int textSize = formattedText.text.length();
    int index = 0;
    if (formattedText.entities != null) {
      for (TdApi.TextEntity entity : formattedText.entities) {
        switch (entity.type.getConstructor()) {
          case TdApi.TextEntityTypeBold.CONSTRUCTOR:
          case TdApi.TextEntityTypeItalic.CONSTRUCTOR:
          case TdApi.TextEntityTypeUnderline.CONSTRUCTOR:
          case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR:
            continue;
          case TdApi.TextEntityTypeCustomEmoji.CONSTRUCTOR: {
            int start = entity.offset;
            int end = start + entity.length;
            if (index != start && !isValidTextBlock(formattedText.text.substring(index, start), layout)) {
              return false;
            }
            index = end;
            if (layout != null) {
              layout.addSpan(formattedText.text.substring(start, end), ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId);
            }
            continue;
          }
          default:
            return false;
        }
      }
    }

    return index == textSize || isValidTextBlock(formattedText.text.substring(index, textSize), layout);
  }

  private static boolean isValidTextBlock (String text, @Nullable NonBubbleEmojiLayout layout) {
    CharSequence parsed = Emoji.instance().replaceEmoji(text);
    if (!(parsed instanceof Spannable)) {
      return isValidAllowedSymbols(text, layout);
    }

    Spannable spannable = (Spannable) parsed;
    for (int index = 0; index < spannable.length(); ) {
      EmojiSpan[] spans = spannable.getSpans(index, index, EmojiSpan.class);
      if (spans == null || spans.length == 0) {
        if (!isValidAllowedSymbols(text.substring(index, index + 1), layout)) {
          return false;
        }
        index += 1;
      } else {
        boolean spanIsFound = false;
        for (EmojiSpan span : spans) {
          int start = spannable.getSpanStart(span);
          int end = spannable.getSpanEnd(span);
          if (start < index) {
            continue;
          }
          if (index == end) {
            return false;
          }
          index = end;
          if (spanIsFound) {
            return false;
          }
          spanIsFound = true;
          if (layout != null) {
            layout.addSpan(text.substring(start, end), span.getCustomEmojiId());
          }
        }
        if (!spanIsFound) {
          if (isValidAllowedSymbols(text.substring(index, index + 1), layout)) {
            index++;
          } else {
            return false;
          }
        }
      }
    }

    return true;
  }

  private static boolean isValidAllowedSymbols (String text, @Nullable NonBubbleEmojiLayout layout) {
    for (int a = 0; a < text.length(); a++) {
      char c = text.charAt(a);
      switch (c) {
        case ' ': {
          if (layout != null) {
            layout.addSpace();
          }
          continue;
        }
        case '\n': {
          if (layout != null) {
            layout.addRow();
          }
          continue;
        }
      }
      return false;
    }
    return true;
  }

  private float lastMaxRowSize;
  private float lastSpaceSize;
  private LayoutBuildResult lastLayoutResult;

  public LayoutBuildResult layout (float maxRowSize, float spaceSize) {
    if (maxRowSize == lastMaxRowSize && spaceSize == lastSpaceSize && lastLayoutResult != null) {
      return lastLayoutResult;
    }

    lastMaxRowSize = maxRowSize;
    lastSpaceSize = spaceSize;
    return lastLayoutResult = new LayoutBuildResult(items, maxRowSize, spaceSize);
  }

  public static class LayoutBuildResult {
    public final ArrayList<Representation> representations = new ArrayList<>();
    public final float maxLineSize;
    public final int linesCount;
    public final boolean hasClassicEmoji;

    private LayoutBuildResult (ArrayList<Item> items, float maxRowSize, float spaceSize) {
      float currentX = 0;
      float maxRowSizeReal = 0;
      boolean hasClassicEmoji = false;
      int currentRow = 0;

      for (Item item : items) {
        if (item.type == Item.SPACE) {
          currentX += spaceSize;
        } else if (item.type == Item.LINE_BREAK) {
          currentX = 0;
          currentRow += 1;
        } else if (item.type == Item.EMOJI) {
          hasClassicEmoji |= item.customEmojiId == 0;
          if ((currentX + 1f) > maxRowSize) {
            currentX = 0;
            currentRow += 1;
          }
          representations.add(new Representation(item.emoji, item.customEmojiId, currentX, currentRow));
          currentX += 1f;
          maxRowSizeReal = Math.max(maxRowSizeReal, currentX);
        }
      }
      this.hasClassicEmoji = hasClassicEmoji;
      this.maxLineSize = maxRowSizeReal;
      this.linesCount = currentRow + 1;
    }
  }

  public static class Representation {
    public final String emoji;
    public final long customEmojiId;
    public final float x;
    public final int y;

    private Representation (String emoji, long customEmojiId, float x, int y) {
      this.customEmojiId = customEmojiId;
      this.emoji = emoji;
      this.x = x;
      this.y = y;
    }
  }

  public static class Item {
    public static final int EMOJI = 0;
    public static final int SPACE = 1;
    public static final int LINE_BREAK = 2;

    public final String emoji;
    public final long customEmojiId;
    public final int type;

    private Item (int type) {
      this(type, null, 0);
    }

    private Item (int type, String emoji, long customEmojiId) {
      this.type = type;
      this.emoji = emoji;
      this.customEmojiId = customEmojiId;
    }
  }
}
