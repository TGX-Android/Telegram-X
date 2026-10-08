/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ArticleInteractionTest {
  private static TdApi.RichText text (String text) { return new TdApi.RichTextPlain(text); }
  private static ArticleDocument document (String text) {
    return new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text(text))}), false, false));
  }

  @Test public void formattingInButtonKeepsOneButtonAndItsPayload () {
    TdApi.RichTextButton button = new TdApi.RichTextButton(new TdApi.InlineButton(text(""), new TdApi.ButtonStyleDanger(), new TdApi.InlineKeyboardButtonTypeCopyText("payload")));
    TdApi.RichTextBold bold = new TdApi.RichTextBold(text(""));
    TdApi.RichTextItalic italic = new TdApi.RichTextItalic(text(""));
    ArticleTextBuilder builder = new ArticleTextBuilder();
    builder.append(Arrays.asList(button, bold), text("bold"));
    builder.append(Collections.singletonList(button), text(" "));
    builder.append(Arrays.asList(button, italic), text("italic"));
    TdApi.RichTextButton result = (TdApi.RichTextButton) builder.build();
    assertEquals("bold italic", ArticleRichText.plain(result));
    assertEquals("payload", ((TdApi.InlineKeyboardButtonTypeCopyText) result.button.type).text);
    assertTrue(result.button.style instanceof TdApi.ButtonStyleDanger);
    assertEquals(3, ((TdApi.RichTexts) result.button.text).texts.length);
    assertEquals("", ArticleRichText.plain(button));
  }

  @Test public void adjacentEqualLinksRemainDistinctAndCrossingStylesArePreserved () {
    TdApi.RichTextUrl first = new TdApi.RichTextUrl(text(""), "https://example.org/1", false);
    TdApi.RichTextUrl second = new TdApi.RichTextUrl(text(""), "https://example.org/1", true);
    ArticleTextBuilder builder = new ArticleTextBuilder();
    builder.append(Collections.singletonList(first), text("a")); builder.append(Collections.singletonList(second), text("b"));
    TdApi.RichTexts result = (TdApi.RichTexts) builder.build();
    assertEquals(2, result.texts.length);
    assertFalse(((TdApi.RichTextUrl) result.texts[0]).isCached);
    assertTrue(((TdApi.RichTextUrl) result.texts[1]).isCached);
    TdApi.RichTextBold bold = new TdApi.RichTextBold(text("")); TdApi.RichTextItalic italic = new TdApi.RichTextItalic(text(""));
    builder = new ArticleTextBuilder();
    builder.append(Collections.singletonList(bold), text("a")); builder.append(Arrays.asList(bold, italic), text("b")); builder.append(Collections.singletonList(italic), text("c"));
    result = (TdApi.RichTexts) builder.build();
    assertEquals("abc", ArticleRichText.plain(result));
    assertTrue(result.texts[0] instanceof TdApi.RichTextBold);
    assertTrue(((TdApi.RichTexts) ((TdApi.RichTextBold) result.texts[0]).text).texts[1] instanceof TdApi.RichTextItalic);
    assertTrue(result.texts[1] instanceof TdApi.RichTextItalic);
  }

  @Test public void checkboxWalkIncludesClosedNestedListsAndPreservesOriginal () {
    TdApi.InputPageBlockListItem first = new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("one"))}, false, false, 0, "");
    TdApi.InputPageBlockListItem target = new TdApi.InputPageBlockListItem(new TdApi.InputPageBlock[] {new TdApi.InputPageBlockParagraph(text("two"))}, true, false, 0, "");
    ArticleDocument original = new ArticleDocument(new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {
      new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {first}),
      new TdApi.InputPageBlockDetails(text("closed"), new TdApi.InputPageBlock[] {new TdApi.InputPageBlockList(new TdApi.InputPageBlockListItem[] {target})}, false)
    }), false, false));
    ArticleDocument toggled = ArticleCheckbox.toggle(original, 1);
    assertFalse(target.isChecked); assertNotEquals(original, toggled);
    assertEquals(original, ArticleCheckbox.toggle(toggled, 1));
    assertThrows(IllegalArgumentException.class, () -> ArticleCheckbox.toggle(original, 0));
    assertThrows(IllegalArgumentException.class, () -> ArticleCheckbox.toggle(original, 2));
  }

  @Test public void anchorOpensOnlyItsEnclosingDetailsIncludingInlineAnchors () {
    TdApi.PageBlockDetails sibling = new TdApi.PageBlockDetails(text("sibling"), new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(text("body"))}, false);
    TdApi.PageBlockDetails inner = new TdApi.PageBlockDetails(text("inner"), new TdApi.PageBlock[] {new TdApi.PageBlockParagraph(new TdApi.RichTexts(new TdApi.RichText[] {new TdApi.RichTextAnchor("target"), text("body")}))}, false);
    TdApi.PageBlockDetails outer = new TdApi.PageBlockDetails(text("outer"), new TdApi.PageBlock[] {inner}, false);
    TdApi.RichMessage article = new TdApi.RichMessage(new TdApi.PageBlock[] {sibling, outer, new TdApi.PageBlockAnchor("end")}, false, true);
    assertFalse(ArticleNavigation.revealAnchor(article, "absent")); assertFalse(outer.isOpen);
    assertTrue(ArticleNavigation.revealAnchor(article, "end")); assertFalse(outer.isOpen);
    assertTrue(ArticleNavigation.revealAnchor(article, "target")); assertTrue(outer.isOpen); assertTrue(inner.isOpen); assertFalse(sibling.isOpen);
  }

  @Test public void historyRestoresSelectionAndDropsRedoWithoutSharingArrays () {
    ArticleHistory history = new ArticleHistory(document("one"));
    history.rememberSelection(2, 1, 3, 50); history.push(document("two")); history.rememberSelection(3, 2, 2, 100);
    history.undo(); assertArrayEquals(new int[] {2, 1, 3, 50}, history.selection());
    history.selection()[0] = 99; assertEquals(2, history.selection()[0]);
    history.redo(); assertArrayEquals(new int[] {3, 2, 2, 100}, history.selection());
    history.undo(); history.push(document("three")); assertFalse(history.canRedo());
  }

  @Test public void emptyTableIsRejectedAndZeroSpansAreNormalizedOnlyInPreview () {
    ArticleValidator.Limits limits = new ArticleValidator.Limits(32768, 500, 16, 50, 20);
    TdApi.InputPageBlockTable table = new TdApi.InputPageBlockTable(text("caption"), new TdApi.PageBlockTableCell[][] {new TdApi.PageBlockTableCell[0]}, false, false, false);
    TdApi.InputRichMessage input = new TdApi.InputRichMessage(new TdApi.RichMessageSourceBlocks(new TdApi.InputPageBlock[] {table}), false, false);
    assertEquals(ArticleValidator.Problem.STRUCTURE, ArticleValidator.validate(input, limits));
    table.cells = new TdApi.PageBlockTableCell[][] {{new TdApi.PageBlockTableCell(text("cell"), false, 0, 0, null, null)}};
    ArticleDocument original = new ArticleDocument(input);
    TdApi.PageBlockTable preview = (TdApi.PageBlockTable) new ArticlePreviewMapper(file -> null).preview(original).blocks[0];
    assertEquals(1, preview.cells[0][0].colspan); assertEquals(1, preview.cells[0][0].rowspan);
    assertEquals(0, ((TdApi.InputPageBlockTable) ((TdApi.RichMessageSourceBlocks) original.toInput().source).blocks[0]).cells[0][0].colspan);
  }

  @Test public void mediaCaptionKeepsLinksStylesEmojiAndCreditOffsets () {
    TdApi.RichText label = new TdApi.RichTextBold(new TdApi.RichTextUrl(text("link"), "https://example.org/target", false));
    TdApi.PageBlockCaption caption = new TdApi.PageBlockCaption(new TdApi.RichTexts(new TdApi.RichText[] {new TdApi.RichTextCustomEmoji(42, "😀"), label}), new TdApi.RichTextMentionName(text("author"), 123));
    TdApi.FormattedText result = ArticleTextExport.caption(caption);
    assertEquals("😀link\nauthor", result.text);
    assertEquals(4, result.entities.length);
    boolean linkFound = false, creditFound = false;
    for (TdApi.TextEntity entity : result.entities) {
      if (entity.type instanceof TdApi.TextEntityTypeTextUrl) { assertEquals(2, entity.offset); assertEquals(4, entity.length); assertEquals("https://example.org/target", ((TdApi.TextEntityTypeTextUrl) entity.type).url); linkFound = true; }
      if (entity.type instanceof TdApi.TextEntityTypeMentionName) { assertEquals(7, entity.offset); assertEquals(6, entity.length); creditFound = true; }
    }
    assertTrue(linkFound); assertTrue(creditFound);
  }

  @Test public void draftKeysSeparateReusedAccountSlotsTopicsAndEditedMessages () {
    String key = ArticleDraftStore.key(0, 42, -100, new TdApi.MessageTopicForum(1), 0);
    assertNotEquals(key, ArticleDraftStore.key(0, 43, -100, new TdApi.MessageTopicForum(1), 0));
    assertNotEquals(key, ArticleDraftStore.key(1, 42, -100, new TdApi.MessageTopicForum(1), 0));
    assertNotEquals(key, ArticleDraftStore.key(0, 42, -100, new TdApi.MessageTopicForum(2), 0));
    assertNotEquals(key, ArticleDraftStore.key(0, 42, -100, new TdApi.MessageTopicForum(1), 10));
    assertNotEquals(key, ArticleDraftStore.key(0, 42, -100, null, 0));
  }
}
