/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleEditingTest {
  private static TdApi.RichText text (String value) { return new TdApi.RichTextPlain(value); }
  private static TdApi.InputRichMessage input (TdApi.InputPageBlock... blocks) { return new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(blocks), false, false); }
  private static TdApi.PageBlockCaption caption () { return new TdApi.PageBlockCaption(text("caption"), new TdApi.RichTextUrl(text("credit"), "https://example.org/credit", false)); }
  private static TdApi.PageBlockTableCell cell (int colspan, int rowspan) { return new TdApi.PageBlockTableCell(text("cell"), true, colspan, rowspan, new TdApi.PageBlockHorizontalAlignmentCenter(), new TdApi.PageBlockVerticalAlignmentBottom()); }
  private static ArticleValidator.Limits limits () { return new ArticleValidator.Limits(32768, 500, 16, 50, 20); }

  @Test public void previewRoundTripCoversEveryUserBlockAndRetainsMetadata () {
    TdApi.InputFile file = new TdApi.InputFileId(42);
    TdApi.InputPageBlock[] blocks = {
      new TdApi.InputPageBlockSectionHeading(text("heading"), 6), new TdApi.InputPageBlockParagraph(text("paragraph")),
      new TdApi.InputPageBlockPreformatted(text("print(1)"), "python"), new TdApi.InputPageBlockFooter(text("footer")),
      new TdApi.InputPageBlockDivider(), new TdApi.InputPageBlockMathematicalExpression("\\sqrt{x}"), new TdApi.InputPageBlockAnchor("anchor"),
      new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("item"))}, true, true, 28, "a")}),
      new TdApi.InputPageBlockBlockQuote(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("quote"))}, text("author")),
      new TdApi.InputPageBlockExpandableBlockQuote(text("expandable"), text("author")), new TdApi.InputPageBlockPullQuote(text("pull quote"), text("author")),
      new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(file, null, null, new int[0], 800, 600), caption(), true),
      new TdApi.InputPageBlockVideo(new TdApi.InputVideo(file, null, null, 0, new int[0], 10, 640, 480, true), caption(), true),
      new TdApi.InputPageBlockAnimation(new TdApi.InputAnimation(file, null, new int[0], 3, 320, 240), caption(), true),
      new TdApi.InputPageBlockAudio(new TdApi.InputAudio(file, null, 20, "song", "artist"), caption()),
      new TdApi.InputPageBlockDocument(new TdApi.InputDocument(file, null, true), caption()),
      new TdApi.InputPageBlockVoiceNote(new TdApi.InputVoiceNote(file, 15, new byte[] {1, 2, 3}), caption()),
      new TdApi.InputPageBlockCollage(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(file, null, null, new int[0], 100, 100), caption(), false)}, caption()),
      new TdApi.InputPageBlockSlideshow(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockPhoto(new TdApi.InputPhoto(file, null, null, new int[0], 100, 100), caption(), false)}, caption()),
      new TdApi.InputPageBlockTable(text("table"), new TdApi.PageBlockTableCell[][] {{cell(2, 1)}}, true, true, true),
      new TdApi.InputPageBlockDetails(text("details"), new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("body"))}, false),
      new TdApi.InputPageBlockMap(new TdApi.Location(55, 83, 1), 15, 640, 360, caption()),
      new TdApi.InputPageBlockButtonRow(new TdApi.InlineButton[] {new TdApi.InlineButton(new TdApi.RichTextBold(text("copy")), new TdApi.ButtonStyleSuccess(), new TdApi.InlineKeyboardButtonTypeCopyText("payload"))}, new TdApi.PageBlockHorizontalAlignmentRight())
    };
    ArticleDocument original = new ArticleDocument(input(blocks));
    TdApi.RichMessage preview = new ArticlePreviewMapper(value -> new TdApi.File(((TdApi.InputFileId) value).id, 100, 100, null, null)).preview(original);
    assertEquals(23, preview.blocks.length);
    assertTrue(preview.isFull);
    assertEquals("ab.", ((TdApi.PageBlockList) preview.blocks[7]).items[0].label);
    assertEquals(original, ArticleDocument.received(preview));
    assertNull(ArticleValidator.validate(original.toInput(), limits()));
  }

  @Test public void editingNestedListGroupPreservesParentAndSibling () {
    TdApi.InputPageBlockParagraph first = new TdApi.InputPageBlockParagraph(text("a"));
    TdApi.InputPageBlockParagraph second = new TdApi.InputPageBlockParagraph(text("b"));
    TdApi.InputPageBlockList list = new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {first, second}, true, true, 4, "I")});
    TdApi.InputRichMessage document = input(list);
    ArticleEditorTree.Entry entry = ArticleEditorTree.entries(document).get(2);
    entry.group.move(entry.index, -1);
    assertSame(second, list.items[0].blocks[0]);
    ArticleEditorTree.fields(first).get(0).set.accept(new TdApi.RichTextUrl(text("a"), "https://example.org", false));
    assertTrue(first.text instanceof TdApi.RichTextUrl);
    assertTrue(list.items[0].hasCheckbox); assertEquals(4, list.items[0].value);
    entry.group.remove(0); assertSame(first, list.items[0].blocks[0]);
  }

  @Test public void unicodeLimitCountsCodePointsAndPreviewNeverSplitsSurrogates () {
    TdApi.InputRichMessage input = input(new TdApi.InputPageBlockParagraph(text("a😀")));
    assertNull(ArticleValidator.validate(input, new ArticleValidator.Limits(2, 10, 16, 10, 10)));
    assertEquals(ArticleValidator.Problem.TEXT_LENGTH, ArticleValidator.validate(input, new ArticleValidator.Limits(1, 10, 16, 10, 10)));
    TdApi.RichMessage article = new TdApi.RichMessage(new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {text("a"), new TdApi.RichTextBold(text("😀b"))})), new TdApi.PageBlockDetails(text("header"), new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(text("nested"))}, false)}, false, true);
    assertEquals("a", ArticleRichText.preview(article, 2));
    assertEquals("a😀b\nheader\nnested", ArticleRichText.preview(article, 100));
  }

  @Test public void mapOnlyArticleIsNotEmptyAndInvalidMapIsRejected () {
    TdApi.InputPageBlockMap map = new TdApi.InputPageBlockMap(new TdApi.Location(0, 0, 0), 15, 640, 360, new TdApi.PageBlockCaption(text(""), text("")));
    assertNull(ArticleValidator.validate(input(map), limits()));
    map.location.latitude = Double.NaN;
    assertEquals(ArticleValidator.Problem.STRUCTURE, ArticleValidator.validate(input(map), limits()));
  }

  @Test public void rowSpansCountAgainstTableWidthAndBounds () {
    TdApi.PageBlockTableCell[][] cells = {{cell(1, 2), cell(1, 1)}, {cell(1, 1)}};
    assertEquals(2, ArticleValidator.tableColumns(cells, 2));
    assertEquals(2, ArticleValidator.tableColumns(cells, 1));
    assertEquals(-1, ArticleValidator.tableColumns(new TdApi.PageBlockTableCell[][] {{cell(1, 2)}}, 20));
    assertEquals(21, ArticleValidator.tableColumns(new TdApi.PageBlockTableCell[][] {{cell(Integer.MAX_VALUE, 1)}}, 20));
  }

  @Test public void limitsIncludeNestedBlocksListItemsRowsAndMedia () {
    TdApi.InputPageBlockList list = new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("text"))}, false, false, 0, "")});
    assertEquals(ArticleValidator.Problem.BLOCK_COUNT, ArticleValidator.validate(input(list), new ArticleValidator.Limits(100, 2, 16, 10, 20)));
    assertEquals(ArticleValidator.Problem.DEPTH, ArticleValidator.validate(input(list), new ArticleValidator.Limits(100, 3, 1, 10, 20)));
    TdApi.InputPageBlockMap map = new TdApi.InputPageBlockMap(new TdApi.Location(0, 0, 0), 15, 640, 360, caption());
    assertEquals(ArticleValidator.Problem.MEDIA_COUNT, ArticleValidator.validate(input(map, map), new ArticleValidator.Limits(100, 10, 16, 1, 20)));
  }

  @Test public void invalidEmptyContainersAndMixedListModesAreRejected () {
    assertEquals(ArticleValidator.Problem.STRUCTURE, ArticleValidator.validate(input(new TdApi.InputPageBlockButtonRow(new TdApi.InlineButton[0], null)), limits()));
    assertEquals(ArticleValidator.Problem.STRUCTURE, ArticleValidator.validate(input(new TdApi.InputPageBlockCollage(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("not media"))}, caption())), limits()));
    TdApi.InputPageBlock[] paragraph = {new TdApi.InputPageBlockParagraph(text("item"))};
    assertEquals(ArticleValidator.Problem.STRUCTURE, ArticleValidator.validate(input(new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {new TdApi.InputPageBlockListItem(paragraph, false, false, 0, ""), new TdApi.InputPageBlockListItem(paragraph, false, false, 1, "1")})), limits()));
  }

  @Test public void formattingPreservesLinkMentionReferenceAndDateMetadata () {
    TdApi.RichText[] wrappers = {new TdApi.RichTextUrl(text("old"), "https://example.org?a=1#anchor", true), new TdApi.RichTextReferenceLink(text("old"), "reference", "https://example.org/ref"), new TdApi.RichTextDateTime(text("old"), 1720000000, new TdApi.DateTimeFormattingTypeRelative()), new TdApi.RichTextMentionName(text("old"), 123456789L)};
    for (TdApi.RichText wrapper : wrappers) {
      byte[] before = ArticleCodec.encode(wrapper);
      TdApi.RichText updated = ArticleRichText.withChild(wrapper, new TdApi.RichTextBold(text("new")));
      assertArrayEquals(before, ArticleCodec.encode(wrapper));
      assertEquals("new", ArticleRichText.plain(updated));
      assertArrayEquals(before, ArticleCodec.encode(ArticleRichText.withChild(updated, text("old"))));
    }
  }

  @Test public void listLabelsHandleAlphabeticRomanAndLargeValuesBoundedly () {
    assertEquals("", ArticleListLabels.label(0, "")); assertEquals("aa.", ArticleListLabels.label(27, "a"));
    assertEquals("AZ.", ArticleListLabels.label(52, "A")); assertEquals("xiv.", ArticleListLabels.label(14, "i"));
    assertEquals("3999.", ArticleListLabels.label(3999, "1")); assertEquals("2147483647.", ArticleListLabels.label(Integer.MAX_VALUE, "I"));
  }

  @Test public void receivedFileWithoutCaptionCanBeEditedWithoutChangingItsReference () {
    TdApi.File file = new TdApi.File(42, 115, 115, null, null);
    TdApi.RichMessage received = new TdApi.RichMessage(new TdApi.PageBlock[] {
      new TdApi.PageBlockParagraph(text("before")),
      new TdApi.PageBlockDocument(new TdApi.Document("notes.txt", "text/plain", null, null, file), null),
      new TdApi.PageBlockParagraph(text("after"))
    }, false, true);
    ArticleDocument original = ArticleDocument.received(received);
    TdApi.InputRichMessage working = original.toInput();
    TdApi.InputPageBlockDocument document = (TdApi.InputPageBlockDocument) ((TdApi.RichMessageSourceBlocks) working.source).blocks[1];
    assertTrue(ArticleEditorTree.isMedia(document));
    java.util.List<ArticleEditorTree.TextField> fields = ArticleEditorTree.fields(document);
    assertEquals(2, fields.size());
    assertEquals("", ArticleRichText.plain(fields.get(0).value));
    assertNull(document.caption);
    assertEquals(original, new ArticleDocument(working));
    fields.get(0).set.accept(new TdApi.RichTextUrl(text("caption"), "https://example.org", false));
    fields.get(1).set.accept(text("credit"));
    TdApi.InputPageBlock[] saved = ((TdApi.RichMessageSourceBlocks) new ArticleDocument(working).toInput().source).blocks;
    TdApi.InputPageBlockDocument result = (TdApi.InputPageBlockDocument) saved[1];
    assertEquals(42, ((TdApi.InputFileId) result.document.document).id);
    assertEquals("https://example.org", ((TdApi.RichTextUrl) result.caption.text).url);
    assertEquals("credit", ArticleRichText.plain(result.caption.credit));
    assertEquals("before", ArticleRichText.plain(((TdApi.InputPageBlockParagraph) saved[0]).text));
    assertEquals("after", ArticleRichText.plain(((TdApi.InputPageBlockParagraph) saved[2]).text));
  }

  @Test public void remoteFilePreviewRetainsResolvedNameWithoutRewritingInput () {
    ArticleDocument original = new ArticleDocument(input(new TdApi.InputPageBlockDocument(new TdApi.InputDocument(new TdApi.InputFileId(42), null, true), null)));
    TdApi.RichMessage preview = new ArticlePreviewMapper(
      file -> new TdApi.File(((TdApi.InputFileId) file).id, 115, 115, null, null),
      file -> ((TdApi.InputFileId) file).id == 42 ? "notes.txt" : null).preview(original);
    TdApi.PageBlockDocument document = (TdApi.PageBlockDocument) preview.blocks[0];
    assertEquals("notes.txt", document.document.fileName);
    assertEquals(42, document.document.document.id);
    assertNull(document.caption);
    assertEquals(original, ArticleDocument.received(preview));
  }

  @Test public void generatedFilePreviewUsesOriginalName () {
    ArticleDocument original = new ArticleDocument(input(new TdApi.InputPageBlockDocument(new TdApi.InputDocument(new TdApi.InputFileGenerated("/tmp/report.txt", "copy", 115), null, true), null)));
    TdApi.RichMessage preview = new ArticlePreviewMapper(file -> new TdApi.File(42, 115, 115, null, null)).preview(original);
    assertEquals("report.txt", ((TdApi.PageBlockDocument) preview.blocks[0]).document.fileName);
  }
}
