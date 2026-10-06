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

  @Override public boolean equals (Object other) {
    return other instanceof ArticleDocument && Arrays.equals(snapshot, ((ArticleDocument) other).snapshot);
  }

  @Override public int hashCode () { return Arrays.hashCode(snapshot); }
}
