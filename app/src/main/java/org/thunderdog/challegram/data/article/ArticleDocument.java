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

  /** Compare draft contents after the server consumes sending options and clears blank text. */
  public boolean hasSameContent (ArticleDocument other) {
    if (other == null) return false;
    if (equals(other)) return true;
    TdApi.InputRichMessage left = toInput(), right = other.toInput();
    left.detectAutomaticBlocks = right.detectAutomaticBlocks = false;
    if (left.isRtl == right.isRtl && blankParagraphs(left) && blankParagraphs(right)) return true;
    return Arrays.equals(ArticleCodec.encode(left), ArticleCodec.encode(right));
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
