/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ArticleDocumentTest {
  private static TdApi.RichText text (String value) { return new TdApi.RichTextPlain(value); }

  private static TdApi.RichMessage mixed () {
    TdApi.File file = new TdApi.File();
    file.id = 42;
    TdApi.Photo photo = new TdApi.Photo(false, null, new TdApi.PhotoSize[] {
      new TdApi.PhotoSize("x", file, 800, 600, new int[0])
    });
    return new TdApi.RichMessage(new TdApi.PageBlock[] {
      new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {
        new TdApi.RichTextBold(text("Статья 👩‍💻 ")),
        new TdApi.RichTextUrl(text("источник"), "https://example.org/?a=1&b=2", false)
      })),
      new TdApi.PageBlockPhoto(photo, new TdApi.PageBlockCaption(text("Подпись"), text("Автор")), "", true),
      new TdApi.PageBlockDetails(text("Детали"), new TdApi.PageBlock[] {
        new TdApi.PageBlockTable(text("Таблица"), new TdApi.PageBlockTableCell[][] {
          {new TdApi.PageBlockTableCell(new TdApi.RichTextUrl(text("ссылка"), "https://example.org/table", false), true, 2, 3, new TdApi.PageBlockHorizontalAlignmentCenter(), new TdApi.PageBlockVerticalAlignmentBottom())}
        }, true, true, true)
      }, false),
      new TdApi.PageBlockList(new TdApi.PageBlockListItem[] {
        new TdApi.PageBlockListItem("7.", new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(text("Пункт"))}, true, true, 7, "a")
      })
    }, true, true);
  }

  @Test public void incomingSnapshotRetainsNullableMediaCaptionAndReferences () throws Exception {
    TdApi.RichMessage original = mixed();
    TdApi.RichMessage copy = (TdApi.RichMessage) ArticleCodec.decode(ArticleCodec.encode(original));
    TdApi.PageBlockPhoto photo = (TdApi.PageBlockPhoto) copy.blocks[1];
    assertEquals(42, photo.photo.sizes[0].photo.id);
    assertEquals("Автор", ((TdApi.RichTextPlain) photo.caption.credit).text);
    assertTrue(photo.hasSpoiler);
    assertNotSame(original.blocks[1], photo);
    TdApi.PageBlockTable table = (TdApi.PageBlockTable) ((TdApi.PageBlockDetails) copy.blocks[2]).blocks[0];
    assertEquals(2, table.cells[0][0].colspan);
    assertEquals(3, table.cells[0][0].rowspan);
    assertTrue(table.isCompact);
    assertTrue(table.cells[0][0].valign instanceof TdApi.PageBlockVerticalAlignmentBottom);
  }

  @Test public void editingOneLinkPreservesUnrelatedBlocks () {
    ArticleDocument document = ArticleDocument.received(mixed());
    TdApi.InputRichMessage edit = document.toInput();
    TdApi.InputPageBlock[] blocks = ((TdApi.RichMessageSourceBlocks) edit.source).blocks;
    byte[] media = ArticleCodec.encode(blocks[1]);
    byte[] details = ArticleCodec.encode(blocks[2]);
    byte[] list = ArticleCodec.encode(blocks[3]);
    TdApi.RichTextUrl url = (TdApi.RichTextUrl) ((TdApi.RichTexts) ((TdApi.InputPageBlockParagraph) blocks[0]).text).texts[1];
    url.url = "https://example.org/edited";
    ArticleDocument changed = new ArticleDocument(edit);
    TdApi.InputPageBlock[] result = ((TdApi.RichMessageSourceBlocks) changed.toInput().source).blocks;
    assertArrayEquals(media, ArticleCodec.encode(result[1]));
    assertArrayEquals(details, ArticleCodec.encode(result[2]));
    assertArrayEquals(list, ArticleCodec.encode(result[3]));
    assertNotEquals(document, changed);
    TdApi.InputPageBlock[] untouched = ((TdApi.RichMessageSourceBlocks) document.toInput().source).blocks;
    assertEquals("https://example.org/?a=1&b=2", ((TdApi.RichTextUrl) ((TdApi.RichTexts) ((TdApi.InputPageBlockParagraph) untouched[0]).text).texts[1]).url);
  }

  @Test public void draftRoundTripAndImmutability () throws Exception {
    ArticleDocument document = ArticleDocument.received(mixed());
    assertEquals(document, ArticleDocument.restore(document.save()));
    byte[] bytes = document.save();
    bytes[0] = 0;
    assertEquals(document, ArticleDocument.restore(document.save()));
    TdApi.InputRichMessage detached = document.toInput();
    detached.isRtl = false;
    assertTrue(document.toInput().isRtl);
  }

  @Test public void nullableDateFormattingAndInlineButtonSurvive () throws Exception {
    TdApi.RichTextDateTime date = new TdApi.RichTextDateTime(text("Дата"), 12345, new TdApi.DateTimeFormattingTypeRelative());
    TdApi.RichTextDateTime copy = (TdApi.RichTextDateTime) ArticleCodec.decode(ArticleCodec.encode(date));
    assertTrue(copy.formattingType instanceof TdApi.DateTimeFormattingTypeRelative);
    TdApi.InlineButton button = new TdApi.InlineButton(text("Копировать"), new TdApi.ButtonStylePrimary(), new TdApi.InlineKeyboardButtonTypeCopyText("payload"));
    TdApi.InlineButton restored = ArticleCodec.copy(button, TdApi.InlineButton.class);
    assertEquals("payload", ((TdApi.InlineKeyboardButtonTypeCopyText) restored.type).text);
    assertTrue(restored.style instanceof TdApi.ButtonStylePrimary);
  }

  @Test public void refusesPartialArticleBeforeEdit () {
    TdApi.RichMessage message = mixed(); message.isFull = false;
    assertThrows(IllegalArgumentException.class, () -> ArticleDocument.received(message));
  }

  @Test public void refusesUnknownBlockInsteadOfDeletingIt () {
    TdApi.RichMessage message = mixed(); message.blocks[1] = new TdApi.PageBlockUnsupported();
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleDocument.received(message));
  }

  @Test public void refusesBotOnlyButtonAndThinking () {
    TdApi.RichMessage message = mixed();
    message.blocks[0] = new TdApi.PageBlockButtonRow(new TdApi.InlineButton[] {
      new TdApi.InlineButton(text("Action"), new TdApi.ButtonStyleDefault(), new TdApi.InlineKeyboardButtonTypeCallback(new byte[] {1, 2}))
    }, null);
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleDocument.received(message));
    message.blocks[0] = new TdApi.PageBlockThinking(text("Thinking"));
    assertThrows(ArticleMapper.Uneditable.class, () -> ArticleDocument.received(message));
  }

  @Test public void rejectsTruncatedTrailingWrongRootAndMalformedUnicode () {
    byte[] encoded = ArticleDocument.empty().save();
    assertThrows(IOException.class, () -> ArticleDocument.restore(Arrays.copyOf(encoded, encoded.length - 1)));
    assertThrows(IOException.class, () -> ArticleDocument.restore(Arrays.copyOf(encoded, encoded.length + 1)));
    assertThrows(IOException.class, () -> ArticleDocument.restore(ArticleCodec.encode(new TdApi.RichTextPlain("not a draft"))));
    assertThrows(IllegalArgumentException.class, () -> ArticleCodec.encode(new TdApi.RichTextPlain("\uD800")));
  }

  @Test public void cyclicTreeAndExcessiveDepthFailBoundedly () {
    TdApi.RichTextBold cycle = new TdApi.RichTextBold(); cycle.text = cycle;
    assertThrows(IllegalArgumentException.class, () -> ArticleCodec.encode(cycle));
    assertThrows(IllegalArgumentException.class, () -> ArticleCodec.visit(cycle, (v, depth) -> { }));
  }

  @Test public void undoRedoRestoresFullDocumentAndDropsAbandonedRedo () {
    ArticleDocument original = ArticleDocument.received(mixed());
    ArticleHistory history = new ArticleHistory(original);
    assertFalse(history.push(original));
    assertFalse(history.canUndo());
    TdApi.InputRichMessage changed = original.toInput(); changed.isRtl = false;
    ArticleDocument next = new ArticleDocument(changed);
    history.push(next);
    assertEquals(original, history.undo());
    assertEquals(next, history.redo());
    history.undo();
    history.push(ArticleDocument.empty());
    assertFalse(history.canRedo());
    assertEquals(original, history.undo());
  }
}
