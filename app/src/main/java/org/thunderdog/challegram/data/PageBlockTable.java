/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
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

import me.vkryl.td.Td;

public class PageBlockTable extends PageBlock {
  private final boolean isRtl;
  private final TdlibUi.UrlOpenParameters openParameters;

  // Drawing

  private class Cell {
    private final TdApi.PageBlockTableCell cell;
    private final int rowPosition, columnPosition;
    private final FormattedText formattedText;
    private final int iconCount;

    private final Rect bounds = new Rect();
    private Text text;

    public Cell (TdApi.PageBlockTableCell cell, int rowPosition, int columnPosition, FormattedText formattedText) {
      this.cell = cell;
      this.rowPosition = rowPosition;
      this.columnPosition = columnPosition;
      this.formattedText = formattedText;
      this.iconCount = formattedText != null ? formattedText.getIconCount() : 0;
    }

    public void build (int maxWidth) {
      if (formattedText != null) {
        maxWidth -= padding() * 2;
        Text.Builder b = new Text.Builder(context.tdlib(), formattedText.text, openParameters, maxWidth, PageBlockRichText.getParagraphProvider(), TextColorSets.InstantView.NORMAL)
          .entities(formattedText.entities)
          .textFlags(Text.FLAG_ARTICLE | Text.FLAG_CUSTOM_LONG_PRESS)
          .viewProvider(currentViews);
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
        this.text = b.build();
      }
    }

    public int rowStart () {
      return rowPosition;
    }

    public int rowEnd () {
      return rowPosition + cell.rowspan;
    }

    public boolean belongsToRow (int row) {
      return row >= rowPosition && row < rowPosition + cell.rowspan;
    }

    public int columnStart () {
      return columnPosition;
    }

    public int columnEnd () {
      return columnPosition + cell.colspan;
    }

    public boolean belongsToColumn (int column) {
      return column >= columnPosition && column < columnPosition + cell.colspan;
    }

    public int padding () {
      return Screen.dp(8f);
    }

    public int width () {
      return text != null ? text.getWidth() + padding() * 2 : 0;
    }

    public int height () {
      return text != null ? text.getHeight() + padding() * 2 : 0;
    }
  }

  private final Cell[][] cells;
  // private final int totalRowCount, totalColumnCount;

  public PageBlockTable (ViewController<?> context, TdApi.PageBlockTable block, boolean isRtl, @Nullable TdlibUi.UrlOpenParameters openParameters) {
    super(context, block);
    this.isRtl = isRtl;
    this.openParameters = openParameters;
    this.cells = new Cell[block.cells.length][];

    if (true)
      throw new UnsupportedOperationException();
    /*int rowIndex = 0;
    int rowPosition = 0;
    int maxColumnCount = 0;

    List<Cell> openCells = new ArrayList<>();
    for (TdApi.PageBlockTableCell[] rowCells : block.cells) {
      cells[rowIndex] = new Cell[rowCells.length];
      int columnIndex = 0;
      int minRowCount = 0;
      int columnPosition = 0;
      for (TdApi.PageBlockTableCell cell : rowCells) {
        for (Cell openCell : openCells) {
          if (columnPosition + cell.colspan <= openCell.columnStart())
            break;
          if (openCell.belongsToColumn(columnPosition))
            columnPosition = openCell.columnEnd();
        }

        cells[rowIndex][columnIndex] = new Cell(cell, rowPosition, columnPosition, FormattedText.parseRichText(context, cell.text, openParameters));

        minRowCount = minRowCount == 0 ? cell.rowspan : Math.min(minRowCount, cell.rowspan);
        columnIndex++;
        columnPosition += cell.colspan;
      }
      maxColumnCount = Math.max(columnPosition, maxColumnCount);
      rowPosition += minRowCount;
      rowIndex++;

      for (int i = openCells.size() - 1; i >= 0; i--) {
        if (openCells.get(i).rowEnd() <= rowPosition) {
          openCells.remove(i);
        }
      }
      for (Cell addedCell : cells[rowIndex]) {
        if (addedCell.columnEnd() )
      }
    }*/

    /*for (TdApi.PageBlockTableCell[] row : block.cells) {
      int columnIndex = 0;
      int columnPosition = 0;
      int minRowCount = 0;
      cells[rowIndex] = new Cell[row.length];
      for (TdApi.PageBlockTableCell cell : row) {
        Cell parsedCell = new PageBlockTable.Cell(cell, rowPosition, columnPosition, FormattedText.parseRichText(context, cell.text, openParameters));
        cells[rowIndex][columnIndex] = parsedCell;
        columnIndex++;
        columnPosition += cell.colspan;
        minRowCount = minRowCount == 0 ? cell.rowspan : Math.min(minRowCount, cell.rowspan);
      }
      rowIndex++;
      rowPosition += minRowCount;
      maxColumnCount = Math.max(maxColumnCount, columnPosition);
    }
    this.totalRowCount = rowPosition;
    this.totalColumnCount = maxColumnCount;*/
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK_TABLE;
  }

  @Override
  public void requestIcons (ComplexReceiver receiver) {
    int iconCount = 0;
    for (Cell[] row : cells) {
      for (Cell cell : row) {
        if (cell.text != null) {
          cell.text.requestMedia(receiver, iconCount, cell.iconCount);
        } else if (cell.formattedText != null) {
          FormattedText.requestMedia(cell.formattedText.entities, receiver, iconCount, cell.iconCount);
        }
        iconCount += cell.iconCount;
      }
    }
    receiver.clearReceiversWithHigherKey(iconCount);
  }

  private int tableWidth, tableHeight;

  @Override
  protected int computeHeight (View view, final int maxContentWidth) {
    int horizontalMargin = Screen.dp(12f);
    int topMargin = getContentTop();
    int bottomMargin = Screen.dp(6f);
    int defaultWidth = (maxContentWidth - horizontalMargin * 2);
    return 0;

    // Step #1: build content
    /*for (Cell[] row : cells) {
      for (Cell cell : row) {
        cell.build(defaultWidth);
      }
    }

    final List<Cell> openCells = new ArrayList<>();
    final int[] columnWidth = new int[totalColumnCount];
    final int[] rowHeight = new int[totalRowCount];

    for (Cell[] rowCells : cells) {
      for (Cell cell : rowCells) {
        final int width = cell.width();
        for (int column = cell.columnStart(); column < cell.columnEnd(); column++) {
          columnWidth[column] = Math.max(columnWidth[column], width / cell.cell.colspan);
        }
        final int height = cell.height();
        for (int row = cell.rowStart(); row < cell.rowEnd(); row++) {
          rowHeight[row] = Math.max(rowHeight[row], height / cell.cell.rowspan);
        }
      }
    }






    // Step #2: calculate row heights
    int currentY = topMargin;
    int maxCellCount = 0;
    for (Cell[] row : cells) {
      maxCellCount = Math.max(maxCellCount, row.length);
      int maxCellHeight = 0;
      int minRowEnd = 0;
      int maxRowEnd = 0;
      for (Cell cell : row) {
        final int width = cell.width();
        for (int column = cell.columnStart(); column < cell.columnEnd(); column++) {
          columnWidth[column] = Math.max(columnWidth[column], width / cell.cell.colspan);
        }

        final int height = cell.height();
        maxCellHeight = Math.max(maxCellHeight, height / cell.cell.rowspan);
        int rowEnd = cell.rowEnd();
        minRowEnd = minRowEnd == 0 ? rowEnd : Math.min(minRowEnd, rowEnd);
        maxRowEnd = Math.max(maxRowEnd, rowEnd);
        cell.bounds.top = cell.bounds.bottom = currentY;
      }
      for (int i = openCells.size() - 1; i >= 0; i--) {
        Cell openCell = openCells.get(i);
        if (openCell.belongsToRow(minRowEnd)) {
          maxCellHeight = Math.max(maxCellHeight, openCell.height() / openCell.cell.rowspan);
        } else {
          openCells.remove(i);
        }
      }
      for (Cell openCell : openCells) {
        openCell.bounds.bottom += maxCellHeight;
      }
      for (Cell cell : row) {
        cell.bounds.bottom = cell.bounds.top + maxCellHeight;
        if (cell.rowEnd() > minRowEnd) {
          openCells.add(cell);
        }
      }
      currentY += maxCellHeight;
    }
    for (Cell openCell : openCells) {
      openCell.bounds.bottom = Math.max(currentY, openCell.bounds.top + openCell.height());
    }
    openCells.clear();

    int totalWidth = 0;
    for (int width : columnWidth) {
      totalWidth += width;
    }
    if (totalWidth < defaultWidth) {
      float ratio = (float) defaultWidth / (float) totalWidth;
      for (int column = 0; column < columnWidth.length; column++) {
        columnWidth[column] *= ratio;
      }
      totalWidth = defaultWidth;
    }
    this.tableWidth = totalWidth;
    this.tableHeight = currentY - topMargin;
    for (Cell[] row : cells) {
      int currentX = isRtl ? tableWidth + horizontalMargin : horizontalMargin;
      for (Cell cell : row) {
        int cellWidth = 0;
        for (int column = cell.columnStart(); column < cell.columnEnd(); column++) {
          cellWidth += columnWidth[column];
        }
        if (isRtl) {
          cell.bounds.right = currentX;
          currentX -= cellWidth;
          cell.bounds.left = currentX;
        } else {
          cell.bounds.left = currentX;
          currentX += cellWidth;
          cell.bounds.right = currentX;
        }
      }
    }

    return currentY + bottomMargin;*/
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    for (Cell[] row : cells) {
      for (Cell cell : row) {
        if (cell.text != null && cell.text.onTouchEvent(view, e, context instanceof Text.ClickCallback ? (Text.ClickCallback) context : null))
          return true;
      }
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
    TdApi.PageBlockTable table = (TdApi.PageBlockTable) this.block;
    for (Cell[] row : cells) {
      for (Cell cell : row) {
        if (cell.cell.isHeader || (table.isStriped && cell.rowStart() % 2 == 0)) {
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
              y = cell.bounds.top + cell.padding();
              break;
            case TdApi.PageBlockVerticalAlignmentMiddle.CONSTRUCTOR:
              y = cell.bounds.centerY() - (cell.text.getHeight() - cell.text.getLineSpacing()) / 2;
              break;
            case TdApi.PageBlockVerticalAlignmentBottom.CONSTRUCTOR:
              y = cell.bounds.bottom - cell.padding() - cell.text.getHeight();
              break;
            default:
              throw new UnsupportedOperationException(cell.toString());
          }
          cell.text.draw(c, cell.bounds.left + cell.padding(), cell.bounds.right - cell.padding(), 0, y, null, 1f, iconReceiver);
          Views.restore(c, restoreCount);
        }
      }
    }
  }
}
