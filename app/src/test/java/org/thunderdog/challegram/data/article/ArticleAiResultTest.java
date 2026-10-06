/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleAiResultTest {
  private static TdApi.RichText text (String value) { return new TdApi.RichTextPlain(value); }
  private static TdApi.RichText diff (String replacement, String original) { return new TdApi.RichTextDiff(text(replacement), text(original)); }
  private static TdApi.RichMessage message (TdApi.PageBlock... blocks) { return new TdApi.RichMessage(blocks, true, true); }
  private static TdApi.InputPageBlock[] blocks (ArticleDocument document) { return ((TdApi.RichMessageSourceBlocks) document.toInput().source).blocks; }

  @Test public void acceptsReplacementsInsertionsAndDeletions () {
    TdApi.RichMessage response = message(new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {
      diff("This", "Thsi"), text(" is "), diff("", "very very "), diff("correct", ""), text(".")
    })));
    TdApi.InputPageBlockParagraph paragraph = (TdApi.InputPageBlockParagraph) blocks(ArticleAiResult.toDocument(response))[0];
    assertEquals("This is correct.", ArticleRichText.plain(paragraph.text));
    ArticleCodec.visit(paragraph, (value, depth) -> assertFalse(value instanceof TdApi.RichTextDiff));
  }

  @Test public void keepsNestedLinksCaptionsTablesButtonsAndMedia () {
    TdApi.File file = new TdApi.File(); file.id = 42;
    TdApi.RichMessage response = message(new TdApi.PageBlockDetails(diff("Details", "Detalis"), new TdApi.PageBlock[] {
      new TdApi.PageBlockTable(diff("Table", "Tabel"), new TdApi.PageBlockTableCell[][] {{
        new TdApi.PageBlockTableCell(new TdApi.RichTextUrl(new TdApi.RichTextDiff(new TdApi.RichTextBold(text("Link")), text("Lnik")), "https://example.org/table", true), true, 2, 3, new TdApi.PageBlockHorizontalAlignmentCenter(), new TdApi.PageBlockVerticalAlignmentBottom())
      }}, true, false, true),
      new TdApi.PageBlockPhoto(new TdApi.Photo(false, null, new TdApi.PhotoSize[] {new TdApi.PhotoSize("x", file, 800, 600, new int[0])}), new TdApi.PageBlockCaption(diff("Caption", "Capiton"), diff("Author", "Auhtor")), "", true),
      new TdApi.PageBlockButtonRow(new TdApi.InlineButton[] {new TdApi.InlineButton(diff("Copy", "Coppy"), new TdApi.ButtonStylePrimary(), new TdApi.InlineKeyboardButtonTypeCopyText("payload"))}, new TdApi.PageBlockHorizontalAlignmentRight())
    }, true));
    byte[] original = ArticleCodec.encode(response);
    ArticleDocument accepted = ArticleAiResult.toDocument(response);
    assertArrayEquals(original, ArticleCodec.encode(response));
    assertTrue(accepted.toInput().isRtl);
    TdApi.InputPageBlockDetails details = (TdApi.InputPageBlockDetails) blocks(accepted)[0];
    assertEquals("Details", ArticleRichText.plain(details.header));
    assertTrue(details.isOpen);
    TdApi.InputPageBlockTable table = (TdApi.InputPageBlockTable) details.blocks[0];
    assertEquals("Table", ArticleRichText.plain(table.caption));
    assertTrue(table.isCompact);
    assertEquals(2, table.cells[0][0].colspan); assertEquals(3, table.cells[0][0].rowspan);
    TdApi.RichTextUrl url = (TdApi.RichTextUrl) table.cells[0][0].text;
    assertEquals("https://example.org/table", url.url); assertTrue(url.isCached); assertTrue(url.text instanceof TdApi.RichTextBold);
    TdApi.InputPageBlockPhoto photo = (TdApi.InputPageBlockPhoto) details.blocks[1];
    assertEquals(42, ((TdApi.InputFileId) photo.photo.photo).id); assertTrue(photo.hasSpoiler);
    assertEquals("Caption", ArticleRichText.plain(photo.caption.text)); assertEquals("Author", ArticleRichText.plain(photo.caption.credit));
    TdApi.InputPageBlockButtonRow row = (TdApi.InputPageBlockButtonRow) details.blocks[2];
    assertEquals("Copy", ArticleRichText.plain(row.buttons[0].text));
    assertEquals("payload", ((TdApi.InlineKeyboardButtonTypeCopyText) row.buttons[0].type).text);
    assertTrue(row.buttons[0].style instanceof TdApi.ButtonStylePrimary);
    ArticleCodec.visit(accepted.toInput(), (value, depth) -> assertFalse(value instanceof TdApi.RichTextDiff));
  }

  @Test public void acceptsNestedCorrectionsWithoutVisitingDiscardedOldText () {
    TdApi.RichText replacement = new TdApi.RichTextDiff(new TdApi.RichTextDiff(text("New"), text("Intermediate")), new TdApi.RichTextIcon());
    TdApi.RichMessage response = message(new TdApi.PageBlockParagraph(new TdApi.RichTextItalic(replacement)));
    TdApi.RichTextItalic result = (TdApi.RichTextItalic) ((TdApi.InputPageBlockParagraph) blocks(ArticleAiResult.toDocument(response))[0]).text;
    assertEquals("New", ArticleRichText.plain(result.text));
    assertTrue(((TdApi.PageBlockParagraph) response.blocks[0]).text instanceof TdApi.RichTextItalic);
    assertTrue(((TdApi.RichTextItalic) ((TdApi.PageBlockParagraph) response.blocks[0]).text).text instanceof TdApi.RichTextDiff);
  }

  @Test public void rejectsUnsupportedResultInsteadOfDroppingIt () {
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleAiResult.toDocument(message(new TdApi.PageBlockParagraph(new TdApi.RichTextIcon()))));
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleAiResult.toDocument(message(new TdApi.PageBlockUnsupported())));
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleAiResult.toDocument(message(new TdApi.PageBlockButtonRow(new TdApi.InlineButton[] {
      new TdApi.InlineButton(text("Action"), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeCallback(new byte[] {1}))
    }, null))));
  }

  @Test public void refusesIncompleteMalformedAndCyclicResponses () {
    assertThrows(IllegalArgumentException.class, () -> ArticleAiResult.toDocument(null));
    TdApi.RichMessage partial = message(new TdApi.PageBlockParagraph(text("partial"))); partial.isFull = false;
    assertThrows(IllegalArgumentException.class, () -> ArticleAiResult.toDocument(partial));
    assertThrows(IllegalArgumentException.class, () -> ArticleAiResult.toDocument(message(new TdApi.PageBlockParagraph(new TdApi.RichTextDiff(null, text("old"))))));
    TdApi.RichTextDiff cycle = new TdApi.RichTextDiff(); cycle.text = cycle; cycle.oldText = text("old");
    assertThrows(IllegalArgumentException.class, () -> ArticleAiResult.toDocument(message(new TdApi.PageBlockParagraph(cycle))));
  }

  @Test public void correctionIsOneUndoableDocumentChange () throws Exception {
    ArticleDocument original = ArticleDocument.received(message(new TdApi.PageBlockParagraph(text("Thsi is text"))));
    ArticleHistory history = new ArticleHistory(original);
    ArticleDocument corrected = ArticleAiResult.toDocument(message(new TdApi.PageBlockParagraph(diff("This is text", "Thsi is text"))));
    assertTrue(history.push(corrected));
    assertEquals(original, history.undo());
    assertEquals(corrected, history.redo());
    assertEquals(corrected, ArticleDocument.restore(corrected.save()));
  }

  @Test public void ordinaryMessageEditingStillRejectsResponseOnlyDiffs () {
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleDocument.received(message(new TdApi.PageBlockParagraph(diff("New", "Old")))));
  }
}
