/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSets;

import java.util.ArrayList;

import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.collection.LongSparseIntArray;
import me.vkryl.td.Td;

public class PageBlockTable extends PageBlock {
  private static final int MARGIN_BOTTOM = 6;
  private static final int MARGIN_HORIZONTAL = 12;
  private static final int PADDING_HORIZONTAL = 8;
  private static final int PADDING_VERTICAL = 8;


  // Drawing



  private final Cell[] cellsList;

  private final int totalColumnsCount;
  private final int totalRowsCount;

  private final TableLayout tableLayoutMaxWidth;
  private final TableLayout tableLayoutMinWidth;


  public PageBlockTable (ViewController<?> context, TdApi.PageBlockTable block, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, block);

    final LongSparseIntArray keys = new LongSparseIntArray(50);
    final ArrayList<Cell> cellsList = new ArrayList<>();

    int currentIndexY = 0;
    for (TdApi.PageBlockTableCell[] rowCells : block.cells) {
      int currentIndexX = 0;
      for (TdApi.PageBlockTableCell cell : rowCells) {
        while (keys.get(currentIndexX, 0) > 0) {
          currentIndexX += 1;
        }
        if (isVisible(cell)) {
          cellsList.add(new Cell(cell, currentIndexX, currentIndexY, FormattedText.parseRichText(context, cell.text, openParameters), currentViews));
        }
        for (int x = 0; x < cell.colspan; x++) {
          long key = currentIndexX + x;
          keys.put(key, Math.max(cell.rowspan, keys.get(key, 0)));
        }
        currentIndexX += cell.colspan;
      }
      currentIndexY += 1;
      for (int a = 0; a < keys.size(); a++) {
        keys.setValueAt(a, Math.max(keys.valueAt(a) - 1, 0));
      }
    }

    int columnsCount = 0;
    int rowsCount = 0;

    for (Cell cell: cellsList) {
      columnsCount = Math.max(columnsCount, cell.cellPositionEndX());
      rowsCount = Math.max(rowsCount, cell.cellPositionEndY());
    }

    this.cellsList = cellsList.toArray(new Cell[0]);
    this.totalColumnsCount = columnsCount;
    this.totalRowsCount = rowsCount;

    this.tableLayoutMaxWidth = TableLayout.valueOf(this.cellsList, totalColumnsCount, totalRowsCount, Cell.METRIC_TYPE_MAX);
    this.tableLayoutMinWidth = TableLayout.valueOf(this.cellsList, totalColumnsCount, totalRowsCount, Cell.METRIC_TYPE_MIN);
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK_TABLE;
  }

  @Override
  public void requestIcons (ComplexReceiver receiver) {
    int iconCount = 0;
    for (Cell cell : cellsList) {
      if (cell.text != null) {
        cell.text.requestMedia(receiver, iconCount, cell.iconCount);
      }
      iconCount += cell.iconCount;
    }
    receiver.clearReceiversWithHigherKey(iconCount);
  }

  private int tableWidth, tableHeight;

  @Override
  protected int computeHeight (View view, final int maxContentWidth) {
    final int horizontalMargin = Screen.dp(MARGIN_HORIZONTAL);
    final int topMargin = getContentTop();
    final int bottomMargin = Screen.dp(MARGIN_BOTTOM);
    final int defaultWidth = (maxContentWidth - horizontalMargin * 2);


    final int[] tableCordsX;
    final int[] tableCordsY;
    if (tableLayoutMaxWidth.tableWidth <= defaultWidth) {
      int diff = defaultWidth - tableLayoutMaxWidth.tableWidth;
      int add = diff / totalColumnsCount;

      final int[] columnsWidth = new int[tableLayoutMaxWidth.columnWidth.length];
      for (int a = 0; a < columnsWidth.length; a++) {
        columnsWidth[a] = tableLayoutMaxWidth.columnWidth[a] + add;
      }

      tableCordsX = TableLayout.computeCordsArray(columnsWidth);
      tableCordsY = tableLayoutMaxWidth.cellsY;
    } else if (tableLayoutMinWidth.tableWidth <= defaultWidth) {
      final int W = defaultWidth - tableLayoutMinWidth.tableWidth;
      final int D = tableLayoutMaxWidth.tableWidth - tableLayoutMinWidth.tableWidth;

      final int[] columnsWidth = new int[tableLayoutMaxWidth.columnWidth.length];
      for (int a = 0; a < columnsWidth.length; a++) {
        final int d = tableLayoutMaxWidth.columnWidth[a] -  tableLayoutMinWidth.columnWidth[a];
        columnsWidth[a] = tableLayoutMinWidth.columnWidth[a] + (int) ((float) d * W / D);
      }

      tableCordsX = TableLayout.computeCordsArray(columnsWidth);
      for (Cell cell : cellsList) {
        cell.build(tableCordsX[cell.cellPositionEndX()] - tableCordsX[cell.cellPositionStartX()] + 2, false);
      }
      tableCordsY = TableLayout.computeCordsArrayY(cellsList, totalRowsCount, Cell.METRIC_TYPE_CURRENT);
    } else {
      for (Cell cell : cellsList) {
        cell.build(defaultWidth, true);
      }

      TableLayout tl = TableLayout.valueOf(cellsList, totalColumnsCount, totalRowsCount, Cell.METRIC_TYPE_CURRENT);
      tableCordsX = tl.cellsX;
      tableCordsY = tl.cellsY;
    }

    for (Cell cell : cellsList) {
      cell.bounds.set(
        tableCordsX[cell.cellPositionStartX()],
        tableCordsY[cell.cellPositionStartY()],
        tableCordsX[cell.cellPositionEndX()],
        tableCordsY[cell.cellPositionEndY()]);
      cell.bounds.offset(horizontalMargin, topMargin);
    }

    this.tableWidth = tableCordsX[tableCordsX.length - 1];
    this.tableHeight = tableCordsY[tableCordsY.length - 1];

    return topMargin + tableHeight + bottomMargin;
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    for (Cell cell : cellsList) {
      if (cell.text != null && cell.text.onTouchEvent(view, e, context instanceof Text.ClickCallback ? (Text.ClickCallback) context : null))
        return true;
    }
    return false;
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(Td.isEmpty(((TdApi.PageBlockTable) block).caption) ? 6f : 2f);
  }

  @Override
  protected int getContentHeight () {
    return this.tableHeight;
  }

  @Override
  protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas c, Receiver preview, Receiver receiver, @Nullable ComplexReceiver iconReceiver) {
    final TdApi.PageBlockTable table = (TdApi.PageBlockTable) this.block;
    for (Cell cell : cellsList) {
      if (cell.cell.isHeader || (table.isStriped && cell.cellPositionStartY() % 2 == 0)) {
        c.drawRect(cell.bounds, Paints.fillingPaint(Theme.backgroundColor()));
      }
      if (table.isBordered) {
        c.drawRect(cell.bounds, Paints.strokeSeparatorPaint(Theme.separatorColor()));
      }
      if (cell.text != null) {
        int restoreCount = Views.save(c);
        c.clipRect(cell.bounds);
        int y;
        switch (cell.cell.valign.getConstructor()) {
          case TdApi.PageBlockVerticalAlignmentTop.CONSTRUCTOR:
            y = cell.bounds.top + Screen.dp(PADDING_VERTICAL);
            break;
          case TdApi.PageBlockVerticalAlignmentMiddle.CONSTRUCTOR:
            y = cell.bounds.centerY() - (cell.text.getHeight() - cell.text.getLineSpacing()) / 2;
            break;
          case TdApi.PageBlockVerticalAlignmentBottom.CONSTRUCTOR:
            y = cell.bounds.bottom - Screen.dp(PADDING_VERTICAL) - cell.text.getHeight();
            break;
          default:
            throw new UnsupportedOperationException(cell.toString());
        }
        cell.text.draw(c, cell.bounds.left + Screen.dp(PADDING_HORIZONTAL), cell.bounds.right - Screen.dp(PADDING_VERTICAL), 0, y, null, 1f, iconReceiver);
        Views.restore(c, restoreCount);
      }
    }
  }

  public static boolean isVisible (TdApi.PageBlockTableCell cell) {
    return cell.text != null;
  }









  private static class TableLayout {
    public final int[] columnWidth;
    public final int[] rowHeight;
    public final int[] cellsX;
    public final int[] cellsY;
    public final int tableWidth;
    public final int tableHeight;

    private TableLayout (int[] columnWidth, int[] rowHeight) {
      this.columnWidth = columnWidth;
      this.rowHeight = rowHeight;
      this.cellsX = computeCordsArray(columnWidth);
      this.cellsY = computeCordsArray(rowHeight);
      this.tableWidth = cellsX[cellsX.length - 1];
      this.tableHeight = cellsY[cellsY.length - 1];
    }

    public static int[] computeCordsArray (int[] size) {
      final int[] cords = new int[size.length + 1];
      for (int a = 0; a < size.length; a++) {
        cords[a + 1] = cords[a] + size[a];
      }
      return cords;
    }

    public static int[] computeCordsArrayY (Cell[] cells, int rowsCount, int metricType) {
      int[] rowHeight = new int[rowsCount];

      for (Cell cell : cells) {
        final int height = cell.height(metricType);
        for (int row = cell.cellPositionStartY(); row < cell.cellPositionEndY(); row++) {
          rowHeight[row] = Math.max(rowHeight[row], height / cell.cell.rowspan);
        }
      }

      return computeCordsArray(rowHeight);
    }

    public static TableLayout valueOf (Cell[] cells, int columnsCount, int rowsCount, int metricType) {
      int[] columnWidth = new int[columnsCount];
      int[] rowHeight = new int[rowsCount];

      for (Cell cell : cells) {
        final int width = cell.width(metricType);
        for (int column = cell.cellPositionStartX(); column < cell.cellPositionEndX(); column++) {
          columnWidth[column] = Math.max(columnWidth[column], width / cell.cell.colspan);
        }
        final int height = cell.height(metricType);
        for (int row = cell.cellPositionStartY(); row < cell.cellPositionEndY(); row++) {
          rowHeight[row] = Math.max(rowHeight[row], height / cell.cell.rowspan);
        }
      }

      return new TableLayout(columnWidth, rowHeight);
    }
  }

  private static class Cell {
    private static final int METRIC_TYPE_MAX = 0;
    private static final int METRIC_TYPE_MIN = 1;
    private static final int METRIC_TYPE_CURRENT = 2;

    private final @NonNull TdApi.PageBlockTableCell cell;
    private final int cellPositionX, cellPositionY;
    private final int iconCount;

    private final int heightForMinimalPossibleWidth;
    private final int heightForMaximalPossibleWidth;
    private final int minimalPossibleWidth;
    private final int maximalPossibleWidth;

    private final @Nullable Text text;

    private final Rect bounds = new Rect();

    public Cell (@NonNull TdApi.PageBlockTableCell cell, int cellPositionX, int cellPositionY, @Nullable FormattedText formattedText, ViewProvider viewProvider) {
      this.cell = cell;
      this.cellPositionX = cellPositionX;
      this.cellPositionY = cellPositionY;
      this.iconCount = formattedText != null ? formattedText.getIconCount() : 0;

      if (formattedText != null) {
        Text.Builder b = new Text.Builder(formattedText.text, Integer.MAX_VALUE, PageBlockRichText.getParagraphProvider(), TextColorSets.InstantView.NORMAL)
          .entities(formattedText.entities, (text, specificMedia) -> {

          })
          .textFlags(Text.FLAG_ARTICLE | Text.FLAG_CUSTOM_LONG_PRESS | Text.FLAG_ALWAYS_BREAK)
          .viewProvider(viewProvider);
        switch (cell.align.getConstructor()) {
          case TdApi.PageBlockHorizontalAlignmentLeft.CONSTRUCTOR:
            break;
          case TdApi.PageBlockHorizontalAlignmentCenter.CONSTRUCTOR:
            b.addFlags(Text.FLAG_ALIGN_CENTER);
            break;
          case TdApi.PageBlockHorizontalAlignmentRight.CONSTRUCTOR:
            b.addFlags(Text.FLAG_ALIGN_RIGHT);
            break;
        }
        text = b.build();

        minimalPossibleWidth = text.getWidth();
        heightForMinimalPossibleWidth = text.getHeight();

        text.setTextFlag(Text.FLAG_ALWAYS_BREAK, false);
        text.changeMaxWidth(Integer.MAX_VALUE - 1);

        maximalPossibleWidth = text.getWidth();
        heightForMaximalPossibleWidth = text.getHeight();
      } else {
        text = null;
        minimalPossibleWidth = 0;
        heightForMinimalPossibleWidth = 0;
        maximalPossibleWidth = 0;
        heightForMaximalPossibleWidth = 0;
      }
    }

    public int width (int metricType) {
      if (metricType == METRIC_TYPE_CURRENT) {
        return width();
      }
      return ((metricType == METRIC_TYPE_MIN ? minimalPossibleWidth : maximalPossibleWidth) + Screen.dp(PADDING_HORIZONTAL) * 2);
    }

    public int height (int metricType) {
      if (metricType == METRIC_TYPE_CURRENT) {
        return height();
      }
      return (metricType == METRIC_TYPE_MIN ? heightForMinimalPossibleWidth : heightForMaximalPossibleWidth) + Screen.dp(PADDING_VERTICAL) * 2;
    }

    public int cellPositionStartX () {
      return cellPositionX;
    }

    public int cellPositionEndX () {
      return cellPositionX + cell.colspan;
    }

    public int cellPositionStartY () {
      return cellPositionY;
    }

    public int cellPositionEndY () {
      return cellPositionY + cell.rowspan;
    }

    public int width () {
      return text != null ? text.getWidth() + Screen.dp(PADDING_HORIZONTAL) * 2 : 0;
    }

    public int height () {
      return text != null ? text.getHeight() + Screen.dp(PADDING_VERTICAL) * 2 : 0;
    }

    public void build (int maxCellWidth, boolean alwaysBreak) {
      if (text != null) {
        text.setTextFlag(Text.FLAG_ALWAYS_BREAK, alwaysBreak);
        text.changeMaxWidth(maxCellWidth - Screen.dp(PADDING_HORIZONTAL) * 2);
      }
    }
  }
}
