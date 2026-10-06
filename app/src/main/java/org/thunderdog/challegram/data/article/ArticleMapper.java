/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;

/** Explicit incoming-to-outgoing conversion. Unsupported data is never silently discarded. */
public final class ArticleMapper {
  private ArticleMapper () { }

  public static final class Uneditable extends IllegalArgumentException {
    public final int constructor;
    public Uneditable (int constructor) {
      super("Article contains a read-only element (" + constructor + ")");
      this.constructor = constructor;
    }
  }

  public static TdApi.InputRichMessage toInput (TdApi.RichMessage message) {
    if (message == null || !message.isFull) throw new IllegalArgumentException("Incomplete article");
    TdApi.RichMessage copy = ArticleCodec.copy(message, TdApi.RichMessage.class);
    ArticleCodec.visit(copy, (value, depth) -> {
      if (value instanceof TdApi.RichTextIcon || value instanceof TdApi.RichTextDiff)
        throw new Uneditable(value.getConstructor());
      if (value instanceof TdApi.InlineButton) {
        TdApi.InlineKeyboardButtonType type = ((TdApi.InlineButton) value).type;
        if (!(type instanceof TdApi.InlineKeyboardButtonTypeUrl) &&
            !(type instanceof TdApi.InlineKeyboardButtonTypeUser) &&
            !(type instanceof TdApi.InlineKeyboardButtonTypeCopyText))
          throw new Uneditable(value.getConstructor());
      }
    });
    return new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(blocks(copy.blocks)), copy.isRtl, false);
  }

  private static TdApi.InputPageBlock[] blocks (TdApi.PageBlock[] source) {
    if (source == null) throw new IllegalArgumentException("Missing article blocks");
    TdApi.InputPageBlock[] result = new TdApi.InputPageBlock[source.length];
    for (int i = 0; i < source.length; i++) result[i] = block(source[i]);
    return result;
  }

  private static TdApi.InputFile file (TdApi.File value) {
    if (value == null || value.id <= 0) throw new IllegalArgumentException("Missing article media");
    return new TdApi.InputFileId(value.id);
  }

  private static TdApi.InputThumbnail thumbnail (TdApi.Thumbnail value) {
    return value == null ? null : new TdApi.InputThumbnail(file(value.file), value.width, value.height);
  }

  private static TdApi.InputPageBlock block (TdApi.PageBlock raw) {
    if (raw == null) throw new IllegalArgumentException("Missing article block");
    switch (raw.getConstructor()) {
      case TdApi.PageBlockSectionHeading.CONSTRUCTOR: {
        TdApi.PageBlockSectionHeading b = (TdApi.PageBlockSectionHeading) raw;
        return new TdApi.InputPageBlockSectionHeading(b.text, b.size);
      }
      case TdApi.PageBlockParagraph.CONSTRUCTOR:
        return new TdApi.InputPageBlockParagraph(((TdApi.PageBlockParagraph) raw).text);
      case TdApi.PageBlockPreformatted.CONSTRUCTOR: {
        TdApi.PageBlockPreformatted b = (TdApi.PageBlockPreformatted) raw;
        return new TdApi.InputPageBlockPreformatted(b.text, b.language);
      }
      case TdApi.PageBlockFooter.CONSTRUCTOR:
        return new TdApi.InputPageBlockFooter(((TdApi.PageBlockFooter) raw).footer);
      case TdApi.PageBlockDivider.CONSTRUCTOR: return new TdApi.InputPageBlockDivider();
      case TdApi.PageBlockMathematicalExpression.CONSTRUCTOR:
        return new TdApi.InputPageBlockMathematicalExpression(((TdApi.PageBlockMathematicalExpression) raw).expression);
      case TdApi.PageBlockAnchor.CONSTRUCTOR:
        return new TdApi.InputPageBlockAnchor(((TdApi.PageBlockAnchor) raw).name);
      case TdApi.PageBlockList.CONSTRUCTOR: {
        TdApi.PageBlockList b = (TdApi.PageBlockList) raw;
        TdApi.InputPageBlockListItem[] items = new TdApi.InputPageBlockListItem[b.items.length];
        for (int i = 0; i < items.length; i++) {
          TdApi.PageBlockListItem item = b.items[i];
          items[i] = new TdApi.InputPageBlockListItem(blocks(item.blocks), item.hasCheckbox, item.isChecked, item.value, item.type);
        }
        return new TdApi.InputPageBlockList(items);
      }
      case TdApi.PageBlockBlockQuote.CONSTRUCTOR: {
        TdApi.PageBlockBlockQuote b = (TdApi.PageBlockBlockQuote) raw;
        return new TdApi.InputPageBlockBlockQuote(blocks(b.blocks), b.credit);
      }
      case TdApi.PageBlockExpandableBlockQuote.CONSTRUCTOR: {
        TdApi.PageBlockExpandableBlockQuote b = (TdApi.PageBlockExpandableBlockQuote) raw;
        return new TdApi.InputPageBlockExpandableBlockQuote(b.text, b.credit);
      }
      case TdApi.PageBlockPullQuote.CONSTRUCTOR: {
        TdApi.PageBlockPullQuote b = (TdApi.PageBlockPullQuote) raw;
        return new TdApi.InputPageBlockPullQuote(b.text, b.credit);
      }
      case TdApi.PageBlockAnimation.CONSTRUCTOR: {
        TdApi.PageBlockAnimation b = (TdApi.PageBlockAnimation) raw;
        TdApi.Animation a = b.animation;
        if (a == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockAnimation(new TdApi.InputAnimation(file(a.animation), thumbnail(a.thumbnail), new int[0], a.duration, a.width, a.height), b.caption, b.hasSpoiler);
      }
      case TdApi.PageBlockAudio.CONSTRUCTOR: {
        TdApi.PageBlockAudio b = (TdApi.PageBlockAudio) raw;
        TdApi.Audio a = b.audio;
        if (a == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockAudio(new TdApi.InputAudio(file(a.audio), thumbnail(a.albumCoverThumbnail), a.duration, a.title, a.performer), b.caption);
      }
      case TdApi.PageBlockDocument.CONSTRUCTOR: {
        TdApi.PageBlockDocument b = (TdApi.PageBlockDocument) raw;
        if (b.document == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockDocument(new TdApi.InputDocument(file(b.document.document), thumbnail(b.document.thumbnail), true), b.caption);
      }
      case TdApi.PageBlockPhoto.CONSTRUCTOR: {
        TdApi.PageBlockPhoto b = (TdApi.PageBlockPhoto) raw;
        if (b.url != null && !b.url.isEmpty()) throw new Uneditable(raw.getConstructor());
        TdApi.PhotoSize best = null;
        if (b.photo != null && b.photo.sizes != null) {
          for (TdApi.PhotoSize size : b.photo.sizes) {
            if (size != null && size.photo != null && (best == null || (long) size.width * size.height > (long) best.width * best.height)) best = size;
          }
        }
        if (best == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(file(best.photo), null, null, new int[0], best.width, best.height), b.caption, b.hasSpoiler);
      }
      case TdApi.PageBlockVideo.CONSTRUCTOR: {
        TdApi.PageBlockVideo b = (TdApi.PageBlockVideo) raw;
        TdApi.Video v = b.video;
        if (v == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockVideo(new TdApi.InputVideo(file(v.video), thumbnail(v.thumbnail), null, 0, new int[0], v.duration, v.width, v.height, v.supportsStreaming), b.caption, b.hasSpoiler);
      }
      case TdApi.PageBlockVoiceNote.CONSTRUCTOR: {
        TdApi.PageBlockVoiceNote b = (TdApi.PageBlockVoiceNote) raw;
        TdApi.VoiceNote v = b.voiceNote;
        if (v == null) throw new Uneditable(raw.getConstructor());
        return new TdApi.InputPageBlockVoiceNote(new TdApi.InputVoiceNote(file(v.voice), v.duration, v.waveform), b.caption);
      }
      case TdApi.PageBlockCollage.CONSTRUCTOR: {
        TdApi.PageBlockCollage b = (TdApi.PageBlockCollage) raw;
        return new TdApi.InputPageBlockCollage(blocks(b.blocks), b.caption);
      }
      case TdApi.PageBlockSlideshow.CONSTRUCTOR: {
        TdApi.PageBlockSlideshow b = (TdApi.PageBlockSlideshow) raw;
        return new TdApi.InputPageBlockSlideshow(blocks(b.blocks), b.caption);
      }
      case TdApi.PageBlockTable.CONSTRUCTOR: {
        TdApi.PageBlockTable b = (TdApi.PageBlockTable) raw;
        return new TdApi.InputPageBlockTable(b.caption, b.cells, b.isBordered, b.isStriped, b.isCompact);
      }
      case TdApi.PageBlockDetails.CONSTRUCTOR: {
        TdApi.PageBlockDetails b = (TdApi.PageBlockDetails) raw;
        return new TdApi.InputPageBlockDetails(b.header, blocks(b.blocks), b.isOpen);
      }
      case TdApi.PageBlockMap.CONSTRUCTOR: {
        TdApi.PageBlockMap b = (TdApi.PageBlockMap) raw;
        return new TdApi.InputPageBlockMap(b.location, b.zoom, b.width, b.height, b.caption);
      }
      case TdApi.PageBlockButtonRow.CONSTRUCTOR: {
        TdApi.PageBlockButtonRow b = (TdApi.PageBlockButtonRow) raw;
        return new TdApi.InputPageBlockButtonRow(b.buttons, b.align);
      }
      // Thinking is bot-only; legacy/Instant View blocks and unsupported blocks
      // cannot be re-sent as a user article without changing their meaning.
      default: throw new Uneditable(raw.getConstructor());
    }
  }
}
