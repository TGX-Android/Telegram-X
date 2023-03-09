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
 *
 * File created on 04/12/2016
 */
package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.SparseIntArray;

import androidx.recyclerview.widget.GridLayoutManager;

import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

public class NewFlowLayoutManager extends GridLayoutManager {
  private SparseIntArray itemSpans = new SparseIntArray();
  private ArrayList<ArrayList<Integer>> rows;
  private SparseIntArray itemsToRow = new SparseIntArray();
  private int calculatedWidth;
  private int headerItemCount;

  public NewFlowLayoutManager(Context context, int spanCount) {
    super(context, spanCount);
  }

  public static class Size {
    public float width;
    public float height;

    public Size() { }

    public Size(float width, float height) {
      this.width = width;
      this.height = height;
    }
  }

  @Override
  public boolean supportsPredictiveItemAnimations() {
    return false;
  }

  private void prepareLayout(float viewPortAvailableSize) {
    itemSpans.clear();
    itemsToRow.clear();
    int preferredRowSize = Screen.dp(100f);

    float totalItemSize = 0;
    int flowItemCount = getFlowItemCount();
    int[] weights = new int[flowItemCount];
    for (int a = 0; a < flowItemCount; a++) {
      Size size = sizeForItem(a + headerItemCount);
      totalItemSize += (size.width / size.height) * preferredRowSize;
      weights[a] = Math.round(size.width / size.height * 100);
    }

    int numberOfRows = Math.max(Math.round(totalItemSize / viewPortAvailableSize), 1);

    rows = getLinearPartitionForSequence(weights, numberOfRows);

    int i = 0, a;
    for (a = 0; a < rows.size(); a++) {
      ArrayList<Integer> row = rows.get(a);

      float summedRatios = 0;
      for (int j = i, n = i + row.size(); j < n; j++) {
        Size preferredSize = sizeForItem(j + headerItemCount);
        summedRatios += preferredSize.width / preferredSize.height;
      }

      float rowSize = viewPortAvailableSize;

      if (rows.size() == 1 && a == rows.size() - 1) {
        if (row.size() < 2) {
          rowSize = (float) Math.floor(viewPortAvailableSize / 3.0f);
        } else if (row.size() < 3) {
          rowSize = (float) Math.floor(viewPortAvailableSize * 2.0f / 3.0f);
        }
      }

      int spanLeft = getSpanCount();
      for (int j = i, n = i + row.size(); j < n; j++) {
        Size preferredSize = sizeForItem(j + headerItemCount);
        int width = Math.round(rowSize / summedRatios * (preferredSize.width / preferredSize.height));
        int itemSpan;
        if (flowItemCount < 3 || j != n - 1) {
          itemSpan = (int) (width / viewPortAvailableSize * getSpanCount());
          spanLeft -= itemSpan;
        } else {
          itemsToRow.put(j, a);
          itemSpan = spanLeft;
        }
        itemSpans.put(j, itemSpan);
      }
      i += row.size();
    }
  }

  private int[] getLinearPartitionTable(int[] sequence, int numPartitions) {
    int n = sequence.length;
    int i, j, x;

    int[] tmpTable = new int[n * numPartitions];
    int[] solution = new int[(n - 1) * (numPartitions - 1)];

    for (i = 0; i < n; i++) {
      tmpTable[i * numPartitions] = sequence[i] + (i != 0 ? tmpTable[(i - 1) * numPartitions] : 0);
    }

    for (j = 0; j < numPartitions; j++) {
      tmpTable[j] = sequence[0];
    }

    for (i = 1; i < n; i++) {
      for (j = 1; j < numPartitions; j++) {
        int currentMin = 0;
        int minX = Integer.MAX_VALUE;

        for (x = 0; x < i; x++) {
          int cost = Math.max(tmpTable[x * numPartitions + (j - 1)], tmpTable[i * numPartitions] - tmpTable[x * numPartitions]);
          if (x == 0 || cost < currentMin) {
            currentMin = cost;
            minX = x;
          }
        }
        tmpTable[i * numPartitions + j] = currentMin;
        solution[(i - 1) * (numPartitions - 1) + (j - 1)] = minX;
      }
    }

    return solution;
  }

  private ArrayList<ArrayList<Integer>> getLinearPartitionForSequence(int[] sequence, int numberOfPartitions) {
    int n = sequence.length;
    int k = numberOfPartitions;

    if (k <= 0) {
      return new ArrayList<>();
    }

    if (k >= n || n == 1) {
      ArrayList<ArrayList<Integer>> partition = new ArrayList<>(sequence.length);
      for (int i = 0; i < sequence.length; i++) {
        ArrayList<Integer> arrayList = new ArrayList<>(1);
        arrayList.add(sequence[i]);
        partition.add(arrayList);
      }
      return partition;
    }

    int[] solution = getLinearPartitionTable(sequence, numberOfPartitions);
    int solutionRowSize = numberOfPartitions - 1;

    k = k - 2;
    n = n - 1;
    ArrayList<ArrayList<Integer>> answer = new ArrayList<>();

    while (k >= 0) {
      if (n < 1) {
        answer.add(0, new ArrayList<Integer>());
      } else {
        ArrayList<Integer> currentAnswer = new ArrayList<>();
        for (int i = solution[(n - 1) * solutionRowSize + k] + 1, range = n + 1; i < range; i++) {
          currentAnswer.add(sequence[i]);
        }
        answer.add(0, currentAnswer);
        n = solution[(n - 1) * solutionRowSize + k];
      }
      k = k - 1;
    }

    ArrayList<Integer> currentAnswer = new ArrayList<>();
    for (int i = 0, range = n + 1; i < range; i++) {
      currentAnswer.add(sequence[i]);
    }
    answer.add(0, currentAnswer);
    return answer;
  }

  private Size sizeForItem(int i) {
    Size size = getSizeForItem(i);
    if (size.width == 0) {
      size.width = 100;
    }
    if (size.height == 0) {
      size.height = 100;
    }
    float aspect = size.width / size.height;
    if (aspect > 4.0f || aspect < 0.2f) {
      size.height = size.width = Math.max(size.width, size.height);
    }
    return size;
  }

  protected Size getSizeForItem(int i) {
    return new Size(100, 100);
  }

  private void checkLayout() {
    if (itemSpans.size() != getFlowItemCount() || calculatedWidth != getWidth()) {
      calculatedWidth = getWidth();
      prepareLayout(getWidth());
    }
  }

  public int getSpanSizeForItem(int i) {
    checkLayout();
    return i < headerItemCount ? getSpanCount() : itemSpans.get(i - headerItemCount);
  }

  public int getRowsCount(int width) {
    if (rows == null) {
      prepareLayout(width);
    }
    return headerItemCount + (rows != null ? rows.size() : 0);
  }

  public boolean isLastInRow(int i) {
    checkLayout();
    return i < headerItemCount || (itemsToRow.get(i - headerItemCount, -1) != -1);
  }

  public boolean isFirstRow (int i) {
    checkLayout();
    return i >= headerItemCount && (rows != null && !rows.isEmpty() && i - headerItemCount < rows.get(0).size());
  }

  public void setHeaderItemCount (int itemCount) {
    this.headerItemCount = itemCount;
  }

  protected int getFlowItemCount() {
    return getItemCount() - headerItemCount; //  - ignoreItemCount;
  }
}
