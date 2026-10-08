/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleTableGridTest {
  private static TdApi.InputPageBlockTable table () {
    TdApi.PageBlockTableCell a = ArticleTableGrid.empty(); a.text = new TdApi.RichTextUrl(new TdApi.RichTextBold(new TdApi.RichTextPlain("a")), "https://example.org/a", false); a.rowspan = 2;
    TdApi.PageBlockTableCell b = ArticleTableGrid.empty(); b.text = new TdApi.RichTextPlain("b");
    TdApi.PageBlockTableCell c = ArticleTableGrid.empty(); c.text = new TdApi.RichTextPlain("c");
    return new TdApi.InputPageBlockTable(new TdApi.RichTextPlain("title"), new TdApi.PageBlockTableCell[][] {{a, b}, {c}}, true, false, false);
  }
  private static void valid (TdApi.InputPageBlockTable table, int width) { assertEquals(width, ArticleValidator.tableColumns(table.cells, 32)); }

  @Test public void insertingInsideSpanExtendsOnlyThatCell () {
    TdApi.InputPageBlockTable table = table(); TdApi.PageBlockTableCell anchor = table.cells[0][0]; ArticleTableGrid grid = new ArticleTableGrid(table);
    grid.insertRow(1); assertEquals(3, anchor.rowspan); assertSame(anchor, grid.cell(2, 0)); assertEquals("c", ArticleRichText.plain(grid.cell(2, 1).text));
    assertEquals("", ArticleRichText.plain(grid.cell(1, 1).text)); valid(table, 2);
    grid.insertColumn(1); assertNotSame(anchor, grid.cell(0, 1)); assertEquals(1, anchor.colspan); valid(table, 3);
  }
  @Test public void deletingOriginRetainsMergedContentAndMetadata () {
    TdApi.InputPageBlockTable table = table(); TdApi.PageBlockTableCell anchor = table.cells[0][0]; ArticleTableGrid grid = new ArticleTableGrid(table);
    grid.deleteRow(0); assertSame(anchor, table.cells[0][0]); assertEquals(1, anchor.rowspan); assertTrue(anchor.text instanceof TdApi.RichTextUrl); valid(table, 2);
    grid.deleteColumn(1); assertEquals("a", ArticleRichText.plain(table.cells[0][0].text)); valid(table, 1);
    grid.deleteRow(0); grid.deleteColumn(0); assertEquals(1, grid.rows()); assertEquals(1, grid.columns()); valid(table, 1);
  }
  @Test public void mergePreservesFormattingAndUnmergeKeepsTextOnce () {
    TdApi.InputPageBlockTable table = table(); ArticleTableGrid grid = new ArticleTableGrid(table);
    assertTrue(grid.merge(0, 0, 1, 1)); assertEquals("a\nb\nc", ArticleRichText.plain(table.cells[0][0].text));
    assertTrue(((TdApi.RichTexts) table.cells[0][0].text).texts[0] instanceof TdApi.RichTextUrl);
    assertEquals(2, table.cells[0][0].colspan); assertEquals(2, table.cells[0][0].rowspan); valid(table, 2);
    grid.unmerge(1, 1); assertEquals("a\nb\nc", ArticleRichText.plain(table.cells[0][0].text)); assertEquals("", ArticleRichText.plain(table.cells[1][1].text)); valid(table, 2);
  }
  @Test public void partialMergeCannotLoseContentOutsideSelection () {
    TdApi.InputPageBlockTable table = table(); byte[] before = ArticleCodec.encode(table); ArticleTableGrid grid = new ArticleTableGrid(table);
    assertFalse(grid.merge(1, 0, 1, 1)); assertArrayEquals(before, ArticleCodec.encode(table));
  }
  @Test public void gridSurvivesSerializationAfterMixedStructuralEdits () throws Exception {
    TdApi.InputPageBlockTable table = table(); ArticleTableGrid grid = new ArticleTableGrid(table);
    grid.insertColumn(0); grid.insertRow(2); grid.merge(0, 0, 2, 1); grid.insertColumn(1); grid.deleteRow(1); grid.deleteColumn(0); valid(table, 3);
    TdApi.InputPageBlockTable restored = (TdApi.InputPageBlockTable) ArticleCodec.decode(ArticleCodec.encode(table)); ArticleTableGrid roundTrip = new ArticleTableGrid(restored); roundTrip.commit();
    assertArrayEquals(ArticleCodec.encode(table), ArticleCodec.encode(restored)); assertEquals(2, roundTrip.rows()); assertEquals(3, roundTrip.columns());
  }
}
