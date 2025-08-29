package org.thunderdog.challegram.widget.decoration;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;

public class BottomInsetFillingDecoration extends RecyclerView.ItemDecoration {
  private final @ColorId int colorId;

  public BottomInsetFillingDecoration (int colorId) {
    this.colorId = colorId;
  }

  @Override
  public void onDraw (@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    super.onDraw(c, parent, state);

    LinearLayoutManager manager = (LinearLayoutManager) parent.getLayoutManager();

    int maxBottom = -1;
    boolean hasBottom = false;
    for (int i = 0; i < manager.getChildCount(); i++) {
      View view = manager.getChildAt(i);
      if (view != null) {
        int bottom = manager.getDecoratedBottom(view);
        if (hasBottom) {
          maxBottom = Math.max(bottom, maxBottom);
        } else {
          maxBottom = bottom;
        }
        hasBottom = true;
      }
    }

    if (hasBottom) {
      int height = parent.getMeasuredHeight();
      if (height > maxBottom) {
        c.drawRect(0, maxBottom, parent.getMeasuredWidth(), height, Paints.fillingPaint(Theme.getColor(colorId)));
      }
    }
  }
}
