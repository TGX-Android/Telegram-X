/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleComposerTest {
  private static TdApi.InputPageBlock[] blocks (String text, TdApi.TextEntity... entities) {
    return ((TdApi.RichMessageSourceBlocks) ArticleComposer.fromText(new TdApi.FormattedText(text, entities), false).toInput().source).blocks;
  }
  private static TdApi.RichText text (TdApi.InputPageBlock block) { return ((TdApi.InputPageBlockParagraph) block).text; }

  @Test public void expandUsesVisualLinesAndIgnoresWhitespace () {
    assertFalse(ArticleComposer.needsExpand(2, "long input"));
    assertTrue(ArticleComposer.needsExpand(3, "a\nb\nc"));
    assertFalse(ArticleComposer.needsExpand(4, " \n \n \n"));
    assertFalse(ArticleComposer.needsExpand(3, null));
  }
  @Test public void emptyParagraphsAndTrailingCaretArePreserved () {
    TdApi.InputPageBlock[] result = blocks("one\n\ntwo\n");
    assertEquals(4, result.length);
    assertEquals("one", ArticleRichText.plain(text(result[0])));
    assertEquals("", ArticleRichText.plain(text(result[1])));
    assertEquals("two", ArticleRichText.plain(text(result[2])));
    assertEquals("", ArticleRichText.plain(text(result[3])));
  }
  @Test public void linksStylesAndEmojiSurvivePromotionWithoutChangingInput () {
    TdApi.TextEntity[] entities = {
      new TdApi.TextEntity(5, 2, new TdApi.TextEntityTypeCustomEmoji(123)),
      new TdApi.TextEntity(0, 4, new TdApi.TextEntityTypeTextUrl("https://example.org/a#b")),
      new TdApi.TextEntity(0, 7, new TdApi.TextEntityTypeBold())
    };
    TdApi.RichText rich = text(blocks("link 😀", entities)[0]);
    assertEquals("link 😀", ArticleRichText.plain(rich));
    final boolean[] seen = new boolean[3];
    ArticleCodec.visit(rich, (node, depth) -> {
      if (node instanceof TdApi.RichTextUrl) { seen[0] = true; assertEquals("https://example.org/a#b", ((TdApi.RichTextUrl) node).url); }
      if (node instanceof TdApi.RichTextBold) seen[1] = true;
      if (node instanceof TdApi.RichTextCustomEmoji) { seen[2] = true; assertEquals(123, ((TdApi.RichTextCustomEmoji) node).customEmojiId); }
    });
    assertArrayEquals(new boolean[] {true, true, true}, seen);
    assertTrue(entities[0].type instanceof TdApi.TextEntityTypeCustomEmoji);
  }
  @Test public void quoteAndCodeKeepTheirBlockMeaningAndLanguage () {
    TdApi.InputPageBlock[] result = blocks("before\ncode\nafter", new TdApi.TextEntity(7, 4, new TdApi.TextEntityTypeBlockQuote()), new TdApi.TextEntity(7, 4, new TdApi.TextEntityTypePreCode("java")));
    assertEquals(3, result.length);
    TdApi.InputPageBlockBlockQuote quote = (TdApi.InputPageBlockBlockQuote) result[1];
    assertEquals(1, quote.blocks.length);
    TdApi.InputPageBlockPreformatted code = (TdApi.InputPageBlockPreformatted) quote.blocks[0];
    assertEquals("java", code.language); assertEquals("code", ArticleRichText.plain(code.text));
    assertEquals("after", ArticleRichText.plain(text(result[2])));
  }
  @Test public void styleCrossingLineBreakRetainsBothParts () {
    TdApi.InputPageBlock[] result = blocks("ab\ncd", new TdApi.TextEntity(1, 3, new TdApi.TextEntityTypeItalic()));
    for (TdApi.InputPageBlock block : result) {
      TdApi.RichTexts parts = (TdApi.RichTexts) text(block);
      assertTrue(parts.texts[block == result[0] ? 1 : 0] instanceof TdApi.RichTextItalic);
    }
  }
  @Test public void invalidOrUnsupportedEntityFailsWithoutModifyingText () {
    TdApi.FormattedText input = new TdApi.FormattedText("0:05", new TdApi.TextEntity[] {new TdApi.TextEntity(0, 4, new TdApi.TextEntityTypeMediaTimestamp(5))});
    try { ArticleComposer.fromText(input, false); fail("Unsupported entity was silently discarded"); } catch (IllegalArgumentException expected) { }
    assertEquals("0:05", input.text);
    try { blocks("text", new TdApi.TextEntity(3, 3, new TdApi.TextEntityTypeBold())); fail("Invalid bounds accepted"); } catch (IllegalArgumentException expected) { }
  }

  @Test public void detectedLinkRetainsOverlappingTextStyle () {
    String url = "https://example.org";
    TdApi.FormattedText input = new TdApi.FormattedText(url, new TdApi.TextEntity[] {new TdApi.TextEntity(0, url.length(), new TdApi.TextEntityTypeBold())});
    ArticleDocument result = ArticleComposer.fromText(input, new TdApi.TextEntity[] {new TdApi.TextEntity(0, url.length(), new TdApi.TextEntityTypeUrl())}, false);
    final boolean[] seen = new boolean[2];
    ArticleCodec.visit(result.toInput(), (node, depth) -> {
      if (node instanceof TdApi.RichTextUrl) { seen[0] = true; assertEquals(url, ((TdApi.RichTextUrl) node).url); }
      if (node instanceof TdApi.RichTextBold) seen[1] = true;
    });
    assertArrayEquals(new boolean[] {true, true}, seen);
    assertEquals(1, input.entities.length);
  }

  @Test public void detectedLinksDoNotOverrideExplicitLinksOrCode () {
    String url = "https://example.org";
    TdApi.FormattedText input = new TdApi.FormattedText(url + " " + url, new TdApi.TextEntity[] {
      new TdApi.TextEntity(0, url.length(), new TdApi.TextEntityTypeTextUrl("https://example.net")),
      new TdApi.TextEntity(url.length() + 1, url.length(), new TdApi.TextEntityTypeCode())
    });
    ArticleDocument result = ArticleComposer.fromText(input, new TdApi.TextEntity[] {
      new TdApi.TextEntity(0, url.length(), new TdApi.TextEntityTypeUrl()),
      new TdApi.TextEntity(url.length() + 1, url.length(), new TdApi.TextEntityTypeUrl())
    }, false);
    final int[] links = {0};
    ArticleCodec.visit(result.toInput(), (node, depth) -> {
      if (node instanceof TdApi.RichTextUrl) { links[0]++; assertEquals("https://example.net", ((TdApi.RichTextUrl) node).url); }
    });
    assertEquals(1, links[0]);
  }
}
