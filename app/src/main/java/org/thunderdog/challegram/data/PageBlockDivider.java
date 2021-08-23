package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;

/**
 * Date: 24/02/2017
 * Author: default
 */

public class PageBlockDivider extends PageBlock {
  public PageBlockDivider (ViewController<?> context, @NonNull TdApi.PageBlock block) {
    super(context, block);
  }

  @Override
  public int getRelatedViewType () {
    return ListItem.TYPE_PAGE_BLOCK;
  }

  @Override
  protected int computeHeight (View view, int width) {
    return Screen.dp(20f);
  }

  @Override
  public boolean handleTouchEvent (View view, MotionEvent e) {
    return false;
  }

  @Override
  protected int getContentTop () {
    return Screen.dp(9f);
  }

  @Override
  protected int getContentHeight () {
    return Screen.dp(1f);
  }

  @Override
  public void drawInternal (View view, Canvas c, Receiver preview, Receiver receiver, ComplexReceiver iconReceiver) {
    int top = Screen.dp(9f);
    int viewWidth = (view.getMeasuredWidth() - getTotalContentPadding()) / 2;
    int x = viewWidth - viewWidth / 2 + getMinimumContentPadding(true);
    c.drawRect(x, top, x + viewWidth, top + Screen.dp(1f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_iv_separator)));
  }
}
