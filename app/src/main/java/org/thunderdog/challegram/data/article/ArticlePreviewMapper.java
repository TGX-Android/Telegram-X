/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.function.Function;

/** Builds a detached local preview without sending or normalizing the editable document. */
public final class ArticlePreviewMapper {
  private final Function<TdApi.InputFile, TdApi.File> files;
  public ArticlePreviewMapper (Function<TdApi.InputFile, TdApi.File> files) { this.files = files; }
  public TdApi.RichMessage preview (ArticleDocument document) {
    TdApi.InputRichMessage input = document.toInput();
    return new TdApi.RichMessage(blocks(((TdApi.RichMessageSourceBlocks) input.source).blocks), input.isRtl, true);
  }
  private TdApi.PageBlock[] blocks (TdApi.InputPageBlock[] source) {
    TdApi.PageBlock[] result = new TdApi.PageBlock[source.length];
    for (int i = 0; i < source.length; i++) result[i] = block(source[i]);
    return result;
  }
  private TdApi.Thumbnail thumb (TdApi.InputThumbnail thumbnail) {
    return thumbnail == null ? null : new TdApi.Thumbnail(new TdApi.ThumbnailFormatJpeg(), thumbnail.width, thumbnail.height, files.apply(thumbnail.thumbnail));
  }
  private String name (TdApi.InputFile file) { return file instanceof TdApi.InputFileLocal ? new java.io.File(((TdApi.InputFileLocal) file).path).getName() : ""; }
  private TdApi.PageBlock block (TdApi.InputPageBlock input) {
    switch (input.getConstructor()) {
      case TdApi.InputPageBlockSectionHeading.CONSTRUCTOR: { TdApi.InputPageBlockSectionHeading b = (TdApi.InputPageBlockSectionHeading) input; return new TdApi.PageBlockSectionHeading(b.text, b.size); }
      case TdApi.InputPageBlockParagraph.CONSTRUCTOR: return new TdApi.PageBlockParagraph(((TdApi.InputPageBlockParagraph) input).text);
      case TdApi.InputPageBlockPreformatted.CONSTRUCTOR: { TdApi.InputPageBlockPreformatted b = (TdApi.InputPageBlockPreformatted) input; return new TdApi.PageBlockPreformatted(b.text, b.language); }
      case TdApi.InputPageBlockFooter.CONSTRUCTOR: return new TdApi.PageBlockFooter(((TdApi.InputPageBlockFooter) input).footer);
      case TdApi.InputPageBlockDivider.CONSTRUCTOR: return new TdApi.PageBlockDivider();
      case TdApi.InputPageBlockMathematicalExpression.CONSTRUCTOR: return new TdApi.PageBlockMathematicalExpression(((TdApi.InputPageBlockMathematicalExpression) input).expression);
      case TdApi.InputPageBlockAnchor.CONSTRUCTOR: return new TdApi.PageBlockAnchor(((TdApi.InputPageBlockAnchor) input).name);
      case TdApi.InputPageBlockList.CONSTRUCTOR: {
        TdApi.InputPageBlockList list = (TdApi.InputPageBlockList) input; TdApi.PageBlockListItem[] items = new TdApi.PageBlockListItem[list.items.length];
        for (int i = 0; i < items.length; i++) {
          TdApi.InputPageBlockListItem item = list.items[i];
          items[i] = new TdApi.PageBlockListItem(ArticleListLabels.label(item.value, item.type), blocks(item.blocks), item.hasCheckbox, item.isChecked, item.value, item.type);
        }
        return new TdApi.PageBlockList(items);
      }
      case TdApi.InputPageBlockBlockQuote.CONSTRUCTOR: { TdApi.InputPageBlockBlockQuote b = (TdApi.InputPageBlockBlockQuote) input; return new TdApi.PageBlockBlockQuote(blocks(b.blocks), b.credit); }
      case TdApi.InputPageBlockExpandableBlockQuote.CONSTRUCTOR: { TdApi.InputPageBlockExpandableBlockQuote b = (TdApi.InputPageBlockExpandableBlockQuote) input; return new TdApi.PageBlockExpandableBlockQuote(b.text, b.credit); }
      case TdApi.InputPageBlockPullQuote.CONSTRUCTOR: { TdApi.InputPageBlockPullQuote b = (TdApi.InputPageBlockPullQuote) input; return new TdApi.PageBlockPullQuote(b.text, b.credit); }
      case TdApi.InputPageBlockPhoto.CONSTRUCTOR: {
        TdApi.InputPageBlockPhoto b = (TdApi.InputPageBlockPhoto) input; TdApi.InputPhoto p = b.photo;
        return new TdApi.PageBlockPhoto(new TdApi.Photo(p.addedStickerFileIds != null && p.addedStickerFileIds.length > 0, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("w", files.apply(p.photo), p.width, p.height, new int[0])}), b.caption, "", b.hasSpoiler);
      }
      case TdApi.InputPageBlockVideo.CONSTRUCTOR: {
        TdApi.InputPageBlockVideo b = (TdApi.InputPageBlockVideo) input; TdApi.InputVideo v = b.video;
        return new TdApi.PageBlockVideo(new TdApi.Video(v.duration, v.width, v.height, name(v.video), "video/mp4", false, v.supportsStreaming, null, thumb(v.thumbnail), files.apply(v.video)), b.caption, false, false, b.hasSpoiler);
      }
      case TdApi.InputPageBlockAnimation.CONSTRUCTOR: {
        TdApi.InputPageBlockAnimation b = (TdApi.InputPageBlockAnimation) input; TdApi.InputAnimation a = b.animation;
        return new TdApi.PageBlockAnimation(new TdApi.Animation(a.duration, a.width, a.height, name(a.animation), name(a.animation).toLowerCase(java.util.Locale.US).endsWith(".gif") ? "image/gif" : "video/mp4", false, null, thumb(a.thumbnail), files.apply(a.animation)), b.caption, true, b.hasSpoiler);
      }
      case TdApi.InputPageBlockAudio.CONSTRUCTOR: {
        TdApi.InputPageBlockAudio b = (TdApi.InputPageBlockAudio) input; TdApi.InputAudio a = b.audio;
        return new TdApi.PageBlockAudio(new TdApi.Audio(a.duration, a.title, a.performer, name(a.audio), "audio/mpeg", null, thumb(a.albumCoverThumbnail), new TdApi.Thumbnail[0], files.apply(a.audio)), b.caption);
      }
      case TdApi.InputPageBlockVoiceNote.CONSTRUCTOR: {
        TdApi.InputPageBlockVoiceNote b = (TdApi.InputPageBlockVoiceNote) input; TdApi.InputVoiceNote v = b.voiceNote;
        return new TdApi.PageBlockVoiceNote(new TdApi.VoiceNote(v.duration, v.waveform, "audio/ogg", null, files.apply(v.voiceNote)), b.caption);
      }
      case TdApi.InputPageBlockDocument.CONSTRUCTOR: {
        TdApi.InputPageBlockDocument b = (TdApi.InputPageBlockDocument) input; TdApi.InputDocument d = b.document;
        return new TdApi.PageBlockDocument(new TdApi.Document(name(d.document), "application/octet-stream", null, thumb(d.thumbnail), files.apply(d.document)), b.caption);
      }
      case TdApi.InputPageBlockCollage.CONSTRUCTOR: { TdApi.InputPageBlockCollage b = (TdApi.InputPageBlockCollage) input; return new TdApi.PageBlockCollage(blocks(b.blocks), b.caption); }
      case TdApi.InputPageBlockSlideshow.CONSTRUCTOR: { TdApi.InputPageBlockSlideshow b = (TdApi.InputPageBlockSlideshow) input; return new TdApi.PageBlockSlideshow(blocks(b.blocks), b.caption); }
      case TdApi.InputPageBlockDetails.CONSTRUCTOR: { TdApi.InputPageBlockDetails b = (TdApi.InputPageBlockDetails) input; return new TdApi.PageBlockDetails(b.header, blocks(b.blocks), b.isOpen); }
      case TdApi.InputPageBlockTable.CONSTRUCTOR: {
        TdApi.InputPageBlockTable b = (TdApi.InputPageBlockTable) input;
        for (TdApi.PageBlockTableCell[] row : b.cells) for (TdApi.PageBlockTableCell cell : row) {
          cell.colspan = Math.max(1, cell.colspan); cell.rowspan = Math.max(1, cell.rowspan);
          if (cell.align == null) cell.align = new TdApi.PageBlockHorizontalAlignmentLeft();
          if (cell.valign == null) cell.valign = new TdApi.PageBlockVerticalAlignmentMiddle();
        }
        return new TdApi.PageBlockTable(b.caption, b.cells, b.isBordered, b.isStriped, b.isCompact);
      }
      case TdApi.InputPageBlockMap.CONSTRUCTOR: { TdApi.InputPageBlockMap b = (TdApi.InputPageBlockMap) input; return new TdApi.PageBlockMap(b.location, b.zoom, b.width, b.height, b.caption); }
      case TdApi.InputPageBlockButtonRow.CONSTRUCTOR: { TdApi.InputPageBlockButtonRow b = (TdApi.InputPageBlockButtonRow) input; return new TdApi.PageBlockButtonRow(b.buttons, b.align); }
      default: throw new IllegalArgumentException("Cannot preview article block " + input.getConstructor());
    }
  }
}
