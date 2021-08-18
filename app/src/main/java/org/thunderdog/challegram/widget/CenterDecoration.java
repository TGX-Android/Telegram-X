package org.thunderdog.challegram.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;

public abstract class CenterDecoration extends RecyclerView.ItemDecoration {
  public abstract int getItemCount ();

  @Override
  public void getItemOffsets (Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
    int itemCount = getItemCount();
    int adapterPosition = parent.getChildAdapterPosition(view);
    if (itemCount == 0 || adapterPosition == RecyclerView.NO_POSITION) {
      outRect.left = outRect.right = 0;
      return;
    }
    int itemWidth = Screen.dp(72f);
    int totalWidth = itemWidth * itemCount;
    int parentWidth = parent.getMeasuredWidth();
    int emptyWidth = parentWidth - totalWidth;
    if (emptyWidth <= 0) {
      outRect.left = outRect.right = 0;
      return;
    }
    int left = emptyWidth / (itemCount + 2);
    if (adapterPosition == 0) {
      left += left / 2;
    }
    if (Lang.rtl()) {
      outRect.right = left;
      outRect.left = 0;
    } else {
      outRect.left = left;
      outRect.right = 0;
    }
  }
}
