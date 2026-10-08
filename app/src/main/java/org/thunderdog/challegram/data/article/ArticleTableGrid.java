/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** A rectangular editing grid. Repeated identities are covered positions of a merged cell. */
public final class ArticleTableGrid {
  private final TdApi.InputPageBlockTable table;
  private TdApi.PageBlockTableCell[][] grid;

  public ArticleTableGrid (TdApi.InputPageBlockTable table) {
    this.table = table;
    int rows = Math.max(1, table.cells.length), columns = 1;
    List<int[]> positions = new ArrayList<>();
    List<TdApi.PageBlockTableCell> cells = new ArrayList<>();
    Set<Long> occupied = new java.util.HashSet<>();
    for (int r = 0; r < table.cells.length; r++) {
      int c = 0;
      for (TdApi.PageBlockTableCell cell : table.cells[r]) {
        while (occupied.contains(key(r, c))) c++;
        int width = Math.max(1, cell.colspan), height = Math.max(1, cell.rowspan);
        if (c + width > 256 || r + height > 4096) throw new IllegalArgumentException("Table dimensions exceed editing bounds");
        positions.add(new int[] {r, c, height, width}); cells.add(cell);
        for (int y = r; y < r + height; y++) for (int x = c; x < c + width; x++) {
          if (!occupied.add(key(y, x))) throw new IllegalArgumentException("Overlapping table cells");
        }
        rows = Math.max(rows, r + height); columns = Math.max(columns, c + width); c += width;
      }
    }
    grid = new TdApi.PageBlockTableCell[rows][columns];
    for (int i = 0; i < cells.size(); i++) {
      int[] p = positions.get(i);
      for (int r = p[0]; r < p[0] + p[2]; r++) for (int c = p[1]; c < p[1] + p[3]; c++) grid[r][c] = cells.get(i);
    }
    for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++) if (grid[r][c] == null) grid[r][c] = empty();
  }
  private static long key (int r, int c) { return ((long) r << 32) | c; }
  public static TdApi.PageBlockTableCell empty () {
    return new TdApi.PageBlockTableCell(new TdApi.RichTextPlain(""), false, 1, 1, new TdApi.PageBlockHorizontalAlignmentLeft(), new TdApi.PageBlockVerticalAlignmentTop());
  }
  public int rows () { return grid.length; }
  public int columns () { return grid[0].length; }
  public TdApi.PageBlockTableCell cell (int row, int column) { return grid[row][column]; }
  public boolean isOrigin (int row, int column) { return (row == 0 || grid[row - 1][column] != grid[row][column]) && (column == 0 || grid[row][column - 1] != grid[row][column]); }

  public void insertRow (int at) {
    if (at < 0 || at > rows()) throw new IndexOutOfBoundsException();
    TdApi.PageBlockTableCell[][] result = new TdApi.PageBlockTableCell[rows() + 1][columns()];
    for (int r = 0; r < result.length; r++) for (int c = 0; c < columns(); c++)
      result[r][c] = r == at ? at > 0 && at < rows() && grid[at - 1][c] == grid[at][c] ? grid[at][c] : empty() : grid[r < at ? r : r - 1][c];
    grid = result; commit();
  }
  public void insertColumn (int at) {
    if (at < 0 || at > columns()) throw new IndexOutOfBoundsException();
    TdApi.PageBlockTableCell[][] result = new TdApi.PageBlockTableCell[rows()][columns() + 1];
    for (int r = 0; r < rows(); r++) for (int c = 0; c < result[r].length; c++)
      result[r][c] = c == at ? at > 0 && at < columns() && grid[r][at - 1] == grid[r][at] ? grid[r][at] : empty() : grid[r][c < at ? c : c - 1];
    grid = result; commit();
  }
  public void deleteRow (int at) {
    if (rows() == 1) return;
    TdApi.PageBlockTableCell[][] result = new TdApi.PageBlockTableCell[rows() - 1][columns()];
    for (int r = 0; r < result.length; r++) System.arraycopy(grid[r < at ? r : r + 1], 0, result[r], 0, columns());
    grid = result; commit();
  }
  public void deleteColumn (int at) {
    if (columns() == 1) return;
    TdApi.PageBlockTableCell[][] result = new TdApi.PageBlockTableCell[rows()][columns() - 1];
    for (int r = 0; r < rows(); r++) for (int c = 0; c < result[r].length; c++) result[r][c] = grid[r][c < at ? c : c + 1];
    grid = result; commit();
  }

  /** Refuses a selection cutting through another merged cell; never drops its hidden content. */
  public boolean merge (int top, int left, int bottom, int right) {
    Set<TdApi.PageBlockTableCell> selected = Collections.newSetFromMap(new IdentityHashMap<>());
    List<TdApi.RichText> text = new ArrayList<>();
    for (int r = top; r <= bottom; r++) for (int c = left; c <= right; c++) if (selected.add(grid[r][c]) && !ArticleRichText.plain(grid[r][c].text).isEmpty()) {
      if (!text.isEmpty()) text.add(new TdApi.RichTextPlain("\n"));
      text.add(grid[r][c].text);
    }
    for (int r = 0; r < rows(); r++) for (int c = 0; c < columns(); c++) if ((r < top || r > bottom || c < left || c > right) && selected.contains(grid[r][c])) return false;
    TdApi.PageBlockTableCell first = grid[top][left];
    first.text = text.isEmpty() ? new TdApi.RichTextPlain("") : new TdApi.RichTexts(text.toArray(new TdApi.RichText[0]));
    for (int r = top; r <= bottom; r++) for (int c = left; c <= right; c++) grid[r][c] = first;
    commit(); return true;
  }
  public void unmerge (int row, int column) {
    TdApi.PageBlockTableCell target = grid[row][column]; boolean first = true;
    for (int r = 0; r < rows(); r++) for (int c = 0; c < columns(); c++) if (grid[r][c] == target) {
      if (first) first = false;
      else { TdApi.PageBlockTableCell cell = empty(); cell.isHeader = target.isHeader; grid[r][c] = cell; }
    }
    commit();
  }
  public void commit () {
    List<TdApi.PageBlockTableCell[]> rows = new ArrayList<>();
    for (int r = 0; r < rows(); r++) {
      List<TdApi.PageBlockTableCell> cells = new ArrayList<>();
      for (int c = 0; c < columns(); c++) if (isOrigin(r, c)) {
        TdApi.PageBlockTableCell cell = grid[r][c]; int w = 1, h = 1;
        while (c + w < columns() && grid[r][c + w] == cell) w++;
        while (r + h < rows() && grid[r + h][c] == cell) h++;
        cell.colspan = w; cell.rowspan = h; cells.add(cell);
      }
      rows.add(cells.toArray(new TdApi.PageBlockTableCell[0]));
    }
    table.cells = rows.toArray(new TdApi.PageBlockTableCell[0][]);
  }
}
