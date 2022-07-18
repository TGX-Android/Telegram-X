package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.widget.ImageReceiverView;

import me.vkryl.td.Td;

public class StickerReceiverView extends ImageReceiverView {
  private TdApi.Sticker sticker;
  private Path outline = new Path();

  public StickerReceiverView (Context context) {
    this(context, null);
  }

  public StickerReceiverView (Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public StickerReceiverView (Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void loadSticker (Tdlib tdlib, TdApi.Sticker sticker, boolean thumbnail) {
    this.sticker = sticker;
    getReceiver().requestFile(thumbnail ? TD.toImageFile(tdlib, sticker.thumbnail) : new TGStickerObj(tdlib, sticker, null, sticker.type).getFullImage());
    updateOutline();
    invalidate();
  }

  private void updateOutline () {
    if (sticker == null)
      return;
    outline.rewind();
    int targetSize = getWidth();
    Td.buildOutline(sticker.outline, Math.min((float) targetSize / (float) sticker.width, (float) targetSize / (float) sticker.height), outline);
  }

  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    updateOutline();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (getReceiver().needPlaceholder()) {
      c.drawPath(outline, Paints.getPlaceholderPaint());
    }
    super.onDraw(c);
  }
}
