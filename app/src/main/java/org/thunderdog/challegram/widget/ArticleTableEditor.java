/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.data.article.ArticleTableGrid;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Editable table cells share the rich-text input used by paragraphs. */
public final class ArticleTableEditor extends ViewGroup {
  private final TdApi.InputPageBlockTable table;
  private final ArticleTableGrid grid;
  private final Runnable changed, structureChanged;
  private final List<CellView> cells = new ArrayList<>();
  private final List<View> rowTools = new ArrayList<>(), columnTools = new ArrayList<>();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] rowTops, rowHeights;
  private int cellWidth, selectedRow = -1, selectedColumn = -1;
  private static final class CellView {
    final ArticleTextInput input; final int row, column; final TdApi.PageBlockTableCell cell;
    CellView (ArticleTextInput input, int row, int column, TdApi.PageBlockTableCell cell) { this.input = input; this.row = row; this.column = column; this.cell = cell; }
  }
  public ArticleTableEditor (Context context, org.thunderdog.challegram.telegram.Tdlib tdlib, TdApi.InputPageBlockTable table, Consumer<ArticleTextInput> register, Runnable changed, Runnable structureChanged) {
    super(context); this.table = table; this.grid = new ArticleTableGrid(table); this.changed = changed; this.structureChanged = structureChanged; setWillNotDraw(false);
    rowTops = new int[grid.rows() + 1]; rowHeights = new int[grid.rows()];
    for (int r = 0; r < grid.rows(); r++) for (int c = 0; c < grid.columns(); c++) if (grid.isOrigin(r, c)) {
      TdApi.PageBlockTableCell cell = grid.cell(r, c);
      ArticleTextInput input = new ArticleTextInput(context, tdlib, cell.text, value -> { cell.text = value; changed.run(); });
      input.setPadding(Screen.dp(10), Screen.dp(8), Screen.dp(10), Screen.dp(8));
      input.setTypeface(null, cell.isHeader ? Typeface.BOLD : Typeface.NORMAL);
      input.setGravity((cell.align instanceof TdApi.PageBlockHorizontalAlignmentCenter ? Gravity.CENTER_HORIZONTAL : cell.align instanceof TdApi.PageBlockHorizontalAlignmentRight ? Gravity.RIGHT : Gravity.LEFT) |
        (cell.valign instanceof TdApi.PageBlockVerticalAlignmentMiddle ? Gravity.CENTER_VERTICAL : cell.valign instanceof TdApi.PageBlockVerticalAlignmentBottom ? Gravity.BOTTOM : Gravity.TOP));
      register.accept(input); cells.add(new CellView(input, r, c, cell)); addView(input);
    }
    for (int r = 0; r < grid.rows(); r++) {
      final int row = r; View tool = ArticleEditorPopup.icon(context, R.drawable.baseline_more_horiz_24, R.string.ArticleRowOptions, () -> { selectedRow = row; selectedColumn = -1; invalidate(); menu(rowTools.get(row), row, true); });
      tool.setPadding(Screen.dp(2), Screen.dp(2), Screen.dp(2), Screen.dp(2)); tool.setBackground(null); rowTools.add(tool); addView(tool);
    }
    for (int c = 0; c < grid.columns(); c++) {
      final int column = c; View tool = ArticleEditorPopup.icon(context, R.drawable.baseline_more_horiz_24, R.string.ArticleColumnOptions, () -> { selectedColumn = column; selectedRow = -1; invalidate(); menu(columnTools.get(column), column, false); });
      tool.setBackground(null); columnTools.add(tool); addView(tool);
    }
  }
  private List<TdApi.PageBlockTableCell> selected (int index, boolean row) {
    List<TdApi.PageBlockTableCell> out = new ArrayList<>();
    for (int i = 0; i < (row ? grid.columns() : grid.rows()); i++) { TdApi.PageBlockTableCell cell = row ? grid.cell(index, i) : grid.cell(i, index); if (!out.contains(cell)) out.add(cell); }
    return out;
  }
  public boolean showCellMenu (ArticleTextInput input, View anchor) {
    for (CellView view : cells) if (view.input == input) {
      TdApi.PageBlockTableCell cell = view.cell;
      ArticleEditorPopup popup = new ArticleEditorPopup(getContext());
      alignment(popup, java.util.Collections.singletonList(cell));
      popup.checked(R.drawable.article_iv_table_highlight, R.string.ArticleHighlightCell, cell.isHeader, () -> { cell.isHeader = !cell.isHeader; structureChanged.run(); });
      if (cell.colspan > 1 || cell.rowspan > 1)
        popup.item(R.drawable.article_iv_table_unmerge, R.string.ArticleUnmergeCells, () -> { grid.unmerge(view.row, view.column); structureChanged.run(); });
      popup.show(anchor);
      return true;
    }
    return false;
  }
  private void alignment (ArticleEditorPopup popup, List<TdApi.PageBlockTableCell> selection) {
    popup.icons(new int[] {R.drawable.article_iv_align_horiz_left, R.drawable.article_iv_align_horiz_middle, R.drawable.article_iv_align_horiz_right, R.drawable.article_iv_align_vert_top, R.drawable.article_iv_align_vert_middle, R.drawable.article_iv_align_vert_bottom},
      new int[] {R.string.ArticleLeft, R.string.ArticleCenter, R.string.ArticleRight, R.string.ArticleTop, R.string.ArticleMiddle, R.string.ArticleBottom}, which -> {
        for (TdApi.PageBlockTableCell cell : selection) {
          if (which < 3) cell.align = which == 0 ? new TdApi.PageBlockHorizontalAlignmentLeft() : which == 1 ? new TdApi.PageBlockHorizontalAlignmentCenter() : new TdApi.PageBlockHorizontalAlignmentRight();
          else cell.valign = which == 3 ? new TdApi.PageBlockVerticalAlignmentTop() : which == 4 ? new TdApi.PageBlockVerticalAlignmentMiddle() : new TdApi.PageBlockVerticalAlignmentBottom();
        }
        structureChanged.run();
      });
  }
  private void menu (View anchor, int index, boolean row) {
    List<TdApi.PageBlockTableCell> selection = selected(index, row);
    ArticleEditorPopup popup = new ArticleEditorPopup(getContext());
    alignment(popup, selection);
    popup.item(R.drawable.article_iv_table_highlight, row ? R.string.ArticleHighlightRow : R.string.ArticleHighlightColumn, () -> { boolean all = true; for (TdApi.PageBlockTableCell cell : selection) all &= cell.isHeader; for (TdApi.PageBlockTableCell cell : selection) cell.isHeader = !all; structureChanged.run(); });
    if (selection.size() > 1) popup.item(R.drawable.article_iv_table_merge, R.string.ArticleMergeCells, () -> {
      if (!grid.merge(row ? index : 0, row ? 0 : index, row ? index : grid.rows() - 1, row ? grid.columns() - 1 : index)) UI.showToast(R.string.ArticleTableMergeInvalid, Toast.LENGTH_SHORT);
      else structureChanged.run();
    });
    if (selection.size() == 1 && (selection.get(0).colspan > 1 || selection.get(0).rowspan > 1)) popup.item(R.drawable.article_iv_table_unmerge, R.string.ArticleUnmergeCells, () -> {
      for (int i = 0; i < (row ? grid.columns() : grid.rows()); i++) grid.unmerge(row ? index : i, row ? i : index); structureChanged.run();
    });
    popup.item(row ? R.drawable.article_iv_table_insert_top : R.drawable.article_iv_table_insert_left, row ? R.string.ArticleInsertAbove : R.string.ArticleInsertLeft, () -> { if (row) grid.insertRow(index); else grid.insertColumn(index); structureChanged.run(); });
    popup.item(row ? R.drawable.article_iv_table_insert_bottom : R.drawable.article_iv_table_insert_right, row ? R.string.ArticleInsertBelow : R.string.ArticleInsertRight, () -> { if (row) grid.insertRow(index + 1); else grid.insertColumn(index + 1); structureChanged.run(); });
    popup.item(R.drawable.article_iv_table_remove, row ? R.string.ArticleDeleteRow : R.string.ArticleDeleteColumn, () -> { if (row) grid.deleteRow(index); else grid.deleteColumn(index); structureChanged.run(); });
    popup.show(anchor);
  }
  @Override protected void onMeasure (int widthSpec, int heightSpec) {
    int width = Math.max(MeasureSpec.getSize(widthSpec), Screen.dp(24 + 88 * grid.columns()));
    cellWidth = (width - Screen.dp(24)) / grid.columns(); int[] heights = rowHeights; java.util.Arrays.fill(heights, Screen.dp(46));
    for (CellView cell : cells) {
      cell.input.measure(MeasureSpec.makeMeasureSpec(cellWidth * Math.max(1, cell.cell.colspan), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      int last = Math.min(heights.length, cell.row + Math.max(1, cell.cell.rowspan)), allocated = 0;
      for (int r = cell.row; r < last; r++) allocated += heights[r];
      if (allocated < cell.input.getMeasuredHeight()) heights[last - 1] += cell.input.getMeasuredHeight() - allocated;
    }
    for (int r = 0; r < heights.length; r++) rowTops[r + 1] = rowTops[r] + heights[r];
    for (CellView cell : cells) cell.input.measure(MeasureSpec.makeMeasureSpec(cellWidth * Math.max(1, cell.cell.colspan), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowTops[Math.min(grid.rows(), cell.row + Math.max(1, cell.cell.rowspan))] - rowTops[cell.row], MeasureSpec.EXACTLY));
    for (View tool : rowTools) tool.measure(MeasureSpec.makeMeasureSpec(Screen.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(32), MeasureSpec.EXACTLY));
    for (View tool : columnTools) tool.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(28), MeasureSpec.EXACTLY));
    setMeasuredDimension(width, rowTops[grid.rows()] + Screen.dp(28));
  }
  @Override protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    for (CellView cell : cells) { int x = Screen.dp(24) + cell.column * cellWidth, y = rowTops[cell.row]; cell.input.layout(x, y, x + cell.input.getMeasuredWidth(), y + cell.input.getMeasuredHeight()); }
    for (int r = 0; r < rowTools.size(); r++) { int y = (rowTops[r] + rowTops[r + 1] - Screen.dp(32)) / 2; rowTools.get(r).layout(0, y, Screen.dp(24), y + Screen.dp(32)); }
    for (int c = 0; c < columnTools.size(); c++) { int x = Screen.dp(24) + c * cellWidth; columnTools.get(c).layout(x, rowTops[grid.rows()], x + cellWidth, getMeasuredHeight()); }
  }
  @Override protected void onDraw (Canvas canvas) {
    for (CellView cell : cells) {
      View view = cell.input; paint.setStyle(Paint.Style.FILL); paint.setColor(0x20888888);
      if (cell.cell.isHeader || table.isStriped && cell.row % 2 == 1) canvas.drawRect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom(), paint);
      if (table.isBordered) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(1)); paint.setColor(0x55888888); canvas.drawRect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom(), paint); }
    }
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Screen.dp(2)); paint.setColor(Theme.textLinkColor());
    if (selectedRow >= 0) canvas.drawRect(Screen.dp(24), rowTops[selectedRow], getWidth() - 1, rowTops[selectedRow + 1], paint);
    if (selectedColumn >= 0) { int x = Screen.dp(24) + selectedColumn * cellWidth; canvas.drawRect(x, 1, x + cellWidth, rowTops[grid.rows()], paint); }
  }
}
