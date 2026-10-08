/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.io.IOException;
import java.util.Arrays;

/** Immutable editor snapshot. Returned TDLib values are detached mutable copies. */
public final class ArticleDocument {
  private final byte[] snapshot;

  public ArticleDocument (TdApi.InputRichMessage message) {
    if (message == null || !(message.source instanceof TdApi.RichMessageSourceBlocks) || ((TdApi.RichMessageSourceBlocks) message.source).blocks == null)
      throw new IllegalArgumentException("A user article requires structured blocks");
    snapshot = ArticleCodec.encode(message);
  }

  public static ArticleDocument empty () {
    return new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(
      new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(new TdApi.RichTextPlain(""))}), false, true));
  }

  public static ArticleDocument received (TdApi.RichMessage message) {
    if (message == null || !message.isFull)
      throw new IllegalArgumentException("Load the full article before editing");
    return new ArticleDocument(ArticleMapper.toInput(message));
  }

  public static ArticleDocument restore (byte[] bytes) throws IOException {
    TdApi.Object value = ArticleCodec.decode(bytes);
    if (!(value instanceof TdApi.InputRichMessage)) throw new IOException("Not an article draft");
    try { return new ArticleDocument((TdApi.InputRichMessage) value); }
    catch (IllegalArgumentException e) { throw new IOException("Invalid article draft", e); }
  }

  public TdApi.InputRichMessage toInput () {
    try { return (TdApi.InputRichMessage) ArticleCodec.decode(snapshot); }
    catch (IOException e) { throw new IllegalStateException("Corrupt in-memory article", e); }
  }

  public byte[] save () { return snapshot.clone(); }

  /** Compare content after the server consumes sending options and canonicalizes text. */
  public boolean hasSameContent (ArticleDocument other) {
    return hasSameContent(other, java.util.Collections.emptyMap());
  }

  /** File IDs in TDLib's draft echo can replace the local paths supplied by the editor. */
  public boolean hasSameContent (ArticleDocument other, java.util.Map<String, Integer> localFiles) {
    if (other == null) return false;
    if (equals(other)) return true;
    TdApi.InputRichMessage left = toInput(), right = other.toInput();
    left.detectAutomaticBlocks = right.detectAutomaticBlocks = false;
    normalizeDraft(left, localFiles);
    normalizeDraft(right, localFiles);
    if (left.isRtl == right.isRtl && blankParagraphs(left) && blankParagraphs(right)) return true;
    return Arrays.equals(ArticleCodec.encode(left), ArticleCodec.encode(right));
  }

  private static TdApi.InputFile normalizeFile (TdApi.InputFile file, java.util.Map<String, Integer> localFiles) {
    Integer id = file instanceof TdApi.InputFileLocal ? localFiles.get(((TdApi.InputFileLocal) file).path) :
      ArticleFiles.key(file) != null ? localFiles.get(ArticleFiles.key(file)) : null;
    return id != null ? new TdApi.InputFileId(id) : file;
  }

  private static void normalizeDraft (TdApi.InputRichMessage message, java.util.Map<String, Integer> localFiles) {
    ArticleCodec.visit(message, (value, depth) -> {
      if (value instanceof TdApi.InputPageBlock && ArticleEditorTree.isMedia((TdApi.InputPageBlock) value)) {
        TdApi.PageBlockCaption caption = ArticleEditorTree.editCaption((TdApi.InputPageBlock) value);
        if (caption.text == null || blankText(caption.text)) caption.text = new TdApi.RichTextPlain("");
        if (caption.credit == null || blankText(caption.credit)) caption.credit = new TdApi.RichTextPlain("");
      }
      if (value instanceof TdApi.PageBlockTableCell && ((TdApi.PageBlockTableCell) value).text == null) {
        ((TdApi.PageBlockTableCell) value).text = new TdApi.RichTextPlain("");
      }
      if (value instanceof TdApi.InputDocument) {
        TdApi.InputDocument media = (TdApi.InputDocument) value;
        media.document = normalizeFile(media.document, localFiles);
        media.disableContentTypeDetection = true;
      } else if (value instanceof TdApi.InputPhoto) {
        TdApi.InputPhoto media = (TdApi.InputPhoto) value; media.photo = normalizeFile(media.photo, localFiles);
      } else if (value instanceof TdApi.InputVideo) {
        TdApi.InputVideo media = (TdApi.InputVideo) value; media.video = normalizeFile(media.video, localFiles);
      } else if (value instanceof TdApi.InputAnimation) {
        TdApi.InputAnimation media = (TdApi.InputAnimation) value; media.animation = normalizeFile(media.animation, localFiles);
      } else if (value instanceof TdApi.InputAudio) {
        TdApi.InputAudio media = (TdApi.InputAudio) value; media.audio = normalizeFile(media.audio, localFiles);
      } else if (value instanceof TdApi.InputVoiceNote) {
        TdApi.InputVoiceNote media = (TdApi.InputVoiceNote) value; media.voiceNote = normalizeFile(media.voiceNote, localFiles);
      } else if (value instanceof TdApi.InputThumbnail) {
        TdApi.InputThumbnail thumbnail = (TdApi.InputThumbnail) value; thumbnail.thumbnail = normalizeFile(thumbnail.thumbnail, localFiles);
      }
    });
    ArticleCodec.transformRichTexts(message, ArticleDocument::normalizeText);
  }

  private static TdApi.RichText normalizeText (TdApi.RichText text) {
    if (!(text instanceof TdApi.RichTexts)) return text;
    java.util.List<TdApi.RichText> parts = new java.util.ArrayList<>();
    appendText(parts, text);
    if (parts.isEmpty()) return new TdApi.RichTextPlain("");
    return parts.size() == 1 ? parts.get(0) : new TdApi.RichTexts(parts.toArray(new TdApi.RichText[0]));
  }

  private static void appendText (java.util.List<TdApi.RichText> parts, TdApi.RichText text) {
    if (text instanceof TdApi.RichTexts) {
      for (TdApi.RichText part : ((TdApi.RichTexts) text).texts) appendText(parts, part);
    } else if (text instanceof TdApi.RichTextPlain) {
      String value = ((TdApi.RichTextPlain) text).text;
      if (value.isEmpty()) return;
      if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof TdApi.RichTextPlain) {
        ((TdApi.RichTextPlain) parts.get(parts.size() - 1)).text += value;
      } else parts.add(new TdApi.RichTextPlain(value));
    } else {
      // Even an empty link, anchor, button or formatting wrapper has semantics.
      parts.add(text);
    }
  }

  private static boolean blankParagraphs (TdApi.InputRichMessage message) {
    for (TdApi.InputPageBlock block : ((TdApi.RichMessageSourceBlocks) message.source).blocks) {
      if (!(block instanceof TdApi.InputPageBlockParagraph) || !blankText(((TdApi.InputPageBlockParagraph) block).text)) return false;
    }
    return true;
  }

  private static boolean blankText (TdApi.RichText text) {
    if (text instanceof TdApi.RichTextPlain) return ((TdApi.RichTextPlain) text).text.trim().isEmpty();
    if (text instanceof TdApi.RichTexts) {
      for (TdApi.RichText part : ((TdApi.RichTexts) text).texts) if (!blankText(part)) return false;
      return true;
    }
    return false; // An empty anchor or other semantic object must still be preserved.
  }

  @Override public boolean equals (Object other) {
    return other instanceof ArticleDocument && Arrays.equals(snapshot, ((ArticleDocument) other).snapshot);
  }

  @Override public int hashCode () { return Arrays.hashCode(snapshot); }
}
