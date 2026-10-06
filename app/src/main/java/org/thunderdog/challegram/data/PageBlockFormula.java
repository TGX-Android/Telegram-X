/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.article.ArticleMath;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;

public final class PageBlockFormula extends PageBlockRichText {
  private final Bitmap formula;
  private int width;

  public PageBlockFormula (ViewController<?> context, TdApi.PageBlockMathematicalExpression block, int quoteLevel) {
    super(context, block, new TdApi.RichTextFixed(new TdApi.RichTextPlain(block.expression)), quoteLevel, 16f, false, null);
    formula = ArticleMath.render(context.context(), block.expression, Screen.dp(18f));
  }

  @Override public int getRelatedViewType () { return ListItem.TYPE_PAGE_BLOCK_TABLE; }
  @Override public int getCustomWidth () { return width; }
  @Override public boolean allowScrolling () { return true; }
  @Override protected int computeHeight (View view, int width) {
    this.width = formula != null ? Math.max(width, formula.getWidth() + Screen.dp(32f)) : width;
    return formula != null ? formula.getHeight() + Screen.dp(24f) : super.computeHeight(view, width);
  }
  @Override public void drawInternal (View view, Canvas canvas, Receiver preview, Receiver receiver, ComplexReceiver icons) {
    if (formula == null) {
      super.drawInternal(view, canvas, preview, receiver, icons);
    } else {
      canvas.drawBitmap(formula, Math.max(Screen.dp(16f), (width - formula.getWidth()) / 2f), Screen.dp(12f), Paints.fillingPaint(Theme.getColor(ColorId.text)));
    }
  }
}
