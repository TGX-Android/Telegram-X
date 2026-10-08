/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

/** Inline buttons use the same rich-text interaction path as buttons inside a paragraph. */
public final class PageBlockButtons extends PageBlock {
  private final TextWrapper[] labels;
  private final int[] widths, positions;
  private final TdApi.PageBlockHorizontalAlignment alignment;
  private final TdApi.InlineButton[] buttons;
  private int pressed = -1;
  private int height;

  public PageBlockButtons (ViewController<?> context, TdApi.PageBlockButtonRow block, int quoteLevel) {
    super(context, block, quoteLevel);
    buttons = block.buttons;
    labels = new TextWrapper[block.buttons.length];
    widths = new int[labels.length]; positions = new int[labels.length]; alignment = block.align;
    for (int i = 0; i < labels.length; i++) {
      labels[i] = TextWrapper.parseRichText(context, null, new TdApi.RichTextButton(block.buttons[i]), Paints.robotoStyleProvider(15f), TextColorSets.InstantView.NORMAL, null, null);
      labels[i].setViewProvider(currentViews);
    }
  }

  @Override public int getRelatedViewType () { return ListItem.TYPE_PAGE_BLOCK; }
  @Override protected int computeHeight (View view, int width) {
    int available = Math.max(1, width - Screen.dp(32f));
    int columnWidth = Math.max(1, available / Math.max(1, labels.length)), total = 0;
    height = Screen.dp(36f);
    for (int i = 0; i < labels.length; i++) {
      TextWrapper label = labels[i];
      label.prepare(Math.max(1, columnWidth - Screen.dp(8f)));
      widths[i] = alignment == null ? columnWidth : Math.min(columnWidth, label.getWidth() + Screen.dp(12f)); total += widths[i];
      height = Math.max(height, label.getHeight() + Screen.dp(20f));
    }
    int left = Screen.dp(16f) + (alignment instanceof TdApi.PageBlockHorizontalAlignmentRight ? available - total : alignment instanceof TdApi.PageBlockHorizontalAlignmentCenter ? (available - total) / 2 : 0);
    for (int i = 0; i < labels.length; i++) { positions[i] = left; left += widths[i]; }
    return labels.length == 0 ? 0 : height;
  }
  @Override public void requestIcons (ComplexReceiver receiver) {
    int offset = 0;
    for (TextWrapper label : labels) {
      int count = label.getMaxMediaCount();
      label.requestMedia(receiver, offset, count);
      offset += count;
    }
    receiver.clearReceiversWithHigherKey(offset);
  }
  @Override protected int getContentTop () { return Screen.dp(10f); }
  @Override protected int getContentHeight () { return height; }
  @Override protected boolean handleTouchEvent (View view, MotionEvent event) {
    for (TextWrapper label : labels) if (label.onTouchEvent(view, event, textClickCallback)) return true;
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) pressed = buttonAt(event.getX(), event.getY());
    if (pressed >= 0) {
      if (event.getActionMasked() == MotionEvent.ACTION_UP) {
        int index = pressed; pressed = -1;
        if (buttonAt(event.getX(), event.getY()) == index && textClickCallback != null) textClickCallback.onButtonClick(view, buttons[index], new org.thunderdog.challegram.telegram.TdlibUi.UrlOpenParameters());
      } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL || buttonAt(event.getX(), event.getY()) != pressed) pressed = -1;
      return true;
    }
    return false;
  }
  private int buttonAt (float x, float y) {
    if (y < 0 || y > height) return -1;
    for (int i = 0; i < buttons.length; i++) if (x >= positions[i] && x < positions[i] + widths[i] && !(buttons[i].type instanceof TdApi.InlineKeyboardButtonTypeDisabled)) return i;
    return -1;
  }
  @Override protected <T extends View & DrawableProvider> void drawInternal (T view, Canvas canvas, Receiver preview, Receiver receiver, ComplexReceiver icons) {
    for (int i = 0; i < labels.length; i++) {
      int left = alignment == null ? positions[i] + Math.max(0, (widths[i] - labels[i].getWidth()) / 2) : positions[i];
      labels[i].draw(canvas, left, positions[i] + widths[i] - Screen.dp(8f), 0, Screen.dp(10f), null, 1f, icons);
    }
  }
}
