/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Lossless promotion of the ordinary composer, including entities and block formatting. */
public final class ArticleComposer {
  private ArticleComposer () { }

  public static boolean needsExpand (int lineCount, CharSequence text) {
    return lineCount > 2 && text != null && !text.toString().trim().isEmpty();
  }

  public static ArticleDocument fromText (TdApi.FormattedText input, boolean rtl) {
    if (input == null || input.text == null) throw new IllegalArgumentException("Missing composer text");
    TdApi.TextEntity[] entities = input.entities == null ? new TdApi.TextEntity[0] : input.entities.clone();
    for (TdApi.TextEntity entity : entities) {
      if (entity == null || entity.type == null || entity.offset < 0 || entity.length <= 0 || entity.offset > input.text.length() - entity.length)
        throw new IllegalArgumentException("Invalid composer entity");
    }
    Arrays.sort(entities, Comparator.comparingInt((TdApi.TextEntity e) -> e.offset).thenComparingInt(e -> -e.length));
    List<TdApi.InputPageBlock> blocks = blocks(input.text, entities, 0, input.text.length(), null, 0);
    if (blocks.isEmpty()) blocks.add(new TdApi.InputPageBlockParagraph(new TdApi.RichTextPlain("")));
    return new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(blocks.toArray(new TdApi.InputPageBlock[0])), rtl, true));
  }

  private static boolean isBlock (TdApi.TextEntityType type) {
    return type instanceof TdApi.TextEntityTypeBlockQuote || type instanceof TdApi.TextEntityTypeExpandableBlockQuote || type instanceof TdApi.TextEntityTypePre || type instanceof TdApi.TextEntityTypePreCode;
  }

  private static List<TdApi.InputPageBlock> blocks (String text, TdApi.TextEntity[] entities, int from, int to, TdApi.TextEntity parent, int depth) {
    if (depth > 32) throw new IllegalArgumentException("Composer nesting is too deep");
    ArrayList<TdApi.InputPageBlock> out = new ArrayList<>();
    int position = from;
    boolean afterParent = parent == null;
    for (TdApi.TextEntity entity : entities) {
      if (!afterParent) { if (entity == parent) afterParent = true; continue; }
      if (!isBlock(entity.type) || entity.offset < position || entity.offset + entity.length > to) continue;
      paragraphs(out, text, entities, position, entity.offset, false);
      int end = entity.offset + entity.length;
      if (entity.type instanceof TdApi.TextEntityTypeBlockQuote) {
        List<TdApi.InputPageBlock> children = blocks(text, entities, entity.offset, end, entity, depth + 1);
        out.add(new TdApi.InputPageBlockBlockQuote(children.toArray(new TdApi.InputPageBlock[0]), new TdApi.RichTextPlain("")));
      } else if (entity.type instanceof TdApi.TextEntityTypeExpandableBlockQuote) {
        out.add(new TdApi.InputPageBlockExpandableBlockQuote(inline(text, entities, entity.offset, end), new TdApi.RichTextPlain("")));
      } else {
        out.add(new TdApi.InputPageBlockPreformatted(inline(text, entities, entity.offset, end), entity.type instanceof TdApi.TextEntityTypePreCode ? ((TdApi.TextEntityTypePreCode) entity.type).language : ""));
      }
      position = end;
      if (position < to && text.charAt(position) == '\n') position++;
    }
    paragraphs(out, text, entities, position, to, out.isEmpty() || position < to || to > from && text.charAt(to - 1) == '\n');
    return out;
  }

  private static void paragraphs (List<TdApi.InputPageBlock> out, String text, TdApi.TextEntity[] entities, int from, int to, boolean includeEmpty) {
    if (from == to && !includeEmpty) return;
    int start = from;
    while (start <= to) {
      int end = text.indexOf('\n', start);
      if (end < 0 || end > to) end = to;
      // A newline immediately before a block is its separator, not an additional blank paragraph.
      if (start != to || includeEmpty) out.add(new TdApi.InputPageBlockParagraph(inline(text, entities, start, end)));
      if (end == to) break;
      start = end + 1;
    }
  }

  private static TdApi.RichText inline (String text, TdApi.TextEntity[] entities, int from, int to) {
    TreeSet<Integer> boundaries = new TreeSet<>(); boundaries.add(from); boundaries.add(to);
    for (TdApi.TextEntity entity : entities) if (!isBlock(entity.type) && entity.offset < to && entity.offset + entity.length > from) {
      boundaries.add(Math.max(from, entity.offset)); boundaries.add(Math.min(to, entity.offset + entity.length));
    }
    Integer[] offsets = boundaries.toArray(new Integer[0]);
    ArticleTextBuilder builder = new ArticleTextBuilder();
    for (int i = 0; i + 1 < offsets.length; i++) {
      int start = offsets[i], end = offsets[i + 1];
      TdApi.RichText value = new TdApi.RichTextPlain(text.substring(start, end));
      ArrayList<TdApi.RichText> wrappers = new ArrayList<>();
      for (TdApi.TextEntity entity : entities) if (!isBlock(entity.type) && entity.offset <= start && entity.offset + entity.length >= end) {
        if (entity.type instanceof TdApi.TextEntityTypeCustomEmoji) {
          if (start != entity.offset || end != entity.offset + entity.length) throw new IllegalArgumentException("Split custom emoji");
          value = new TdApi.RichTextCustomEmoji(((TdApi.TextEntityTypeCustomEmoji) entity.type).customEmojiId, text.substring(start, end));
        } else wrappers.add(wrapper(entity.type, text.substring(entity.offset, entity.offset + entity.length)));
      }
      builder.append(wrappers, value);
    }
    return builder.build();
  }

  private static TdApi.RichText wrapper (TdApi.TextEntityType type, String label) {
    TdApi.RichText text = new TdApi.RichTextPlain("");
    switch (type.getConstructor()) {
      case TdApi.TextEntityTypeBold.CONSTRUCTOR: return new TdApi.RichTextBold(text);
      case TdApi.TextEntityTypeItalic.CONSTRUCTOR: return new TdApi.RichTextItalic(text);
      case TdApi.TextEntityTypeUnderline.CONSTRUCTOR: return new TdApi.RichTextUnderline(text);
      case TdApi.TextEntityTypeStrikethrough.CONSTRUCTOR: return new TdApi.RichTextStrikethrough(text);
      case TdApi.TextEntityTypeSpoiler.CONSTRUCTOR: return new TdApi.RichTextSpoiler(text);
      case TdApi.TextEntityTypeCode.CONSTRUCTOR: return new TdApi.RichTextFixed(text);
      case TdApi.TextEntityTypeTextUrl.CONSTRUCTOR: return new TdApi.RichTextUrl(text, ((TdApi.TextEntityTypeTextUrl) type).url, false);
      case TdApi.TextEntityTypeUrl.CONSTRUCTOR: return new TdApi.RichTextUrl(text, label, false);
      case TdApi.TextEntityTypeMentionName.CONSTRUCTOR: return new TdApi.RichTextMentionName(text, ((TdApi.TextEntityTypeMentionName) type).userId);
      case TdApi.TextEntityTypeMention.CONSTRUCTOR: return new TdApi.RichTextMention(text, label.startsWith("@") ? label.substring(1) : label);
      case TdApi.TextEntityTypeEmailAddress.CONSTRUCTOR: return new TdApi.RichTextEmailAddress(text, label);
      case TdApi.TextEntityTypePhoneNumber.CONSTRUCTOR: return new TdApi.RichTextPhoneNumber(text, label);
      case TdApi.TextEntityTypeHashtag.CONSTRUCTOR: return new TdApi.RichTextHashtag(text, label);
      case TdApi.TextEntityTypeCashtag.CONSTRUCTOR: return new TdApi.RichTextCashtag(text, label);
      case TdApi.TextEntityTypeBankCardNumber.CONSTRUCTOR: return new TdApi.RichTextBankCardNumber(text, label);
      case TdApi.TextEntityTypeBotCommand.CONSTRUCTOR: return new TdApi.RichTextBotCommand(text, label);
      case TdApi.TextEntityTypeDateTime.CONSTRUCTOR: {
        TdApi.TextEntityTypeDateTime date = (TdApi.TextEntityTypeDateTime) type;
        return new TdApi.RichTextDateTime(text, date.unixTime, date.formattingType);
      }
      default: throw new IllegalArgumentException("Composer entity has no article equivalent: " + type.getConstructor());
    }
  }
}
