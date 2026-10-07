/* This file is a part of Telegram X. SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ArticleDocumentTest {
  private static TdApi.RichText text (String value) { return new TdApi.RichTextPlain(value); }

  @Test public void draftFileAliasesRequireIdenticalContents () throws Exception {
    java.nio.file.Path local = java.nio.file.Files.createTempFile("article-import", ".txt");
    java.nio.file.Path cached = java.nio.file.Files.createTempFile("article-tdlib", ".txt");
    try {
      java.nio.file.Files.write(local, new byte[] {1, 2, 3}); java.nio.file.Files.write(cached, new byte[] {1, 2, 3});
      ArticleDocument document = new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
        new TdApi.InputPageBlockDocument(new TdApi.InputDocument(new TdApi.InputFileLocal(local.toString()), null, true), null)}), false, true));
      java.util.Map<String, Integer> files = java.util.Collections.singletonMap(cached.toString(), 42);
      assertEquals(Integer.valueOf(42), ArticleDraftFiles.aliases(document, files).get(local.toString()));
      java.nio.file.Files.write(cached, new byte[] {3, 2, 1});
      assertFalse(ArticleDraftFiles.aliases(document, files).containsKey(local.toString()));
      java.nio.file.Files.delete(cached);
      assertFalse(ArticleDraftFiles.aliases(document, files).containsKey(local.toString()));
    } finally { java.nio.file.Files.deleteIfExists(local); java.nio.file.Files.deleteIfExists(cached); }
  }

  @Test public void localMediaDraftMatchesTdlibEchoWithoutHidingRealChanges () {
    TdApi.InputPageBlockDocument file = new TdApi.InputPageBlockDocument(new TdApi.InputDocument(new TdApi.InputFileLocal("/draft/qa.txt"), null, false), null);
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {file}), false, true);
    ArticleDocument local = new ArticleDocument(input);
    file.document.document = new TdApi.InputFileId(42);
    file.document.disableContentTypeDetection = true;
    file.caption = new TdApi.PageBlockCaption(text(""), new TdApi.RichTexts(new TdApi.RichText[0]));
    input.detectAutomaticBlocks = false;
    ArticleDocument cloud = new ArticleDocument(input);
    java.util.Map<String, Integer> paths = java.util.Collections.singletonMap("/draft/qa.txt", 42);
    assertFalse(local.hasSameContent(cloud));
    assertTrue(local.hasSameContent(cloud, paths));
    assertTrue(((TdApi.InputPageBlockDocument) ((TdApi.RichMessageSourceBlocks) local.toInput().source).blocks[0]).document.document instanceof TdApi.InputFileLocal);
    file.caption.text = text("Changed on another client");
    assertFalse(local.hasSameContent(new ArticleDocument(input), paths));
    file.caption.text = text(""); file.document.document = new TdApi.InputFileId(43);
    assertFalse(local.hasSameContent(new ArticleDocument(input), paths));
  }

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

  @Test public void cloudDraftComparisonIgnoresOnlyTheConsumedDetectionOption () {
    ArticleDocument cloud = ArticleDocument.received(mixed());
    TdApi.InputRichMessage input = cloud.toInput();
    input.detectAutomaticBlocks = true;
    ArticleDocument local = new ArticleDocument(input);
    assertNotEquals(local, cloud);
    assertTrue(local.hasSameContent(cloud));
    assertTrue(local.toInput().detectAutomaticBlocks);
    input.isRtl = !input.isRtl;
    assertFalse(new ArticleDocument(input).hasSameContent(cloud));
    input.isRtl = !input.isRtl;
    TdApi.InputPageBlockParagraph first = (TdApi.InputPageBlockParagraph) ((TdApi.RichMessageSourceBlocks) input.source).blocks[0];
    ((TdApi.RichTextUrl) ((TdApi.RichTexts) first.text).texts[1]).url = "https://example.org/changed";
    assertFalse(new ArticleDocument(input).hasSameContent(cloud));
  }

  @Test public void clearedDraftDoesNotConflictWithAnEmptyCloudDraft () {
    ArticleDocument empty = ArticleDocument.empty();
    TdApi.InputRichMessage input = empty.toInput();
    input.detectAutomaticBlocks = false;
    input.source = new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
      new TdApi.InputPageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[0])),
      new TdApi.InputPageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {text("  "), text("\n")}))
    });
    assertTrue(empty.hasSameContent(new ArticleDocument(input)));
    assertTrue(new ArticleDocument(input).hasSameContent(empty));
    input.isRtl = true;
    assertFalse(empty.hasSameContent(new ArticleDocument(input)));
    input.isRtl = false;
    input.source = new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
      new TdApi.InputPageBlockParagraph(new TdApi.RichTextReference("anchor", text("")))
    });
    assertFalse(empty.hasSameContent(new ArticleDocument(input)));
    input.source = new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("kept"))});
    assertFalse(empty.hasSameContent(new ArticleDocument(input)));
    input.source = new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[0]);
    assertTrue(empty.hasSameContent(new ArticleDocument(input)));
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
