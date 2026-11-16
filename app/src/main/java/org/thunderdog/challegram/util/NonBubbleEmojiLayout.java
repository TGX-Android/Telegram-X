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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.emoji.EmojiSpan;

import java.util.ArrayList;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

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

  private void addSpan (String emoji, long customEmojiId, @Nullable EmojiInfo info) {
    items.add(new Item(Item.EMOJI, emoji, customEmojiId, info));
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
              layout.addSpan(formattedText.text.substring(start, end), ((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId, null);
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
    FoundEmoji found = new FoundEmoji(text);
    Emoji.instance().replaceEmoji(found);
    if (!found.hasSpans) {
      return isValidAllowedSymbols(text, layout);
    }

    // Emoji come in the order of their starts
    int next = 0;
    for (int index = 0; index < text.length(); ) {
      while (next < found.spans.size() && found.starts.get(next) < index) {
        next++;
      }
      if (next < found.spans.size() && found.starts.get(next) == index) {
        EmojiSpan span = found.spans.get(next);
        int end = found.ends.get(next);
        next++;
        if (index == end) {
          return false;
        }
        if (layout != null) {
          String emoji = text.substring(index, end);
          layout.addSpan(emoji, span.getCustomEmojiId(), span.getBuiltInEmojiInfo());
        }
        index = end;
      } else {
        if (!isValidAllowedSymbols(text.substring(index, index + 1), layout)) {
          return false;
        }
        index++;
      }
    }

    return true;
  }

  // Spans in lists: a SpannableString searches all of them for each one set or looked for
  private static final class FoundEmoji implements Spannable {
    private final String text;
    private final ArrayList<EmojiSpan> spans = new ArrayList<>();
    private final IntList starts = new IntList(10), ends = new IntList(10);
    private boolean hasSpans;

    FoundEmoji (String text) {
      this.text = text;
    }

    @Override
    public void setSpan (Object what, int start, int end, int flags) {
      hasSpans = true;
      if (what instanceof EmojiSpan) {
        spans.add((EmojiSpan) what);
        starts.append(start);
        ends.append(end);
      }
    }

    @Override
    public void removeSpan (Object what) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] getSpans (int start, int end, Class<T> type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getSpanStart (Object tag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getSpanEnd (Object tag) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getSpanFlags (Object tag) {
      throw new UnsupportedOperationException();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public int nextSpanTransition (int start, int limit, Class type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int length () {
      return text.length();
    }

    @Override
    public char charAt (int index) {
      return text.charAt(index);
    }

    @NonNull
    @Override
    public CharSequence subSequence (int start, int end) {
      return text.subSequence(start, end);
    }

    @NonNull
    @Override
    public String toString () {
      return text;
    }
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
    public final EmojiInfo info;

    private Item (int type) {
      this(type, null, 0, null);
    }

    private Item (int type, String emoji, long customEmojiId, EmojiInfo info) {
      this.type = type;
      this.emoji = emoji;
      this.customEmojiId = customEmojiId;
      this.info = info;
    }
  }
}
