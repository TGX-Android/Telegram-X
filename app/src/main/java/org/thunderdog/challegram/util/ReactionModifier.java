package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;

public class ReactionModifier implements DrawModifier {
  private TGStickerObj staticIconSticker;
  private ImageFile staticIconFile;
  private ImageReceiver staticIconReceiver;
  private int size = Screen.dp(22);

  public ReactionModifier (View view, TGStickerObj reaction) {
    staticIconSticker = reaction;
    staticIconFile = !staticIconSticker.isEmpty() ? staticIconSticker.getImage() : null;
    staticIconReceiver = new ImageReceiver(view, 0);
    staticIconReceiver.setAlpha(1f);
    if (staticIconFile != null) {
      staticIconReceiver.requestFile(staticIconFile);
    }
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    c.save();
    c.translate(view.getMeasuredWidth() - Screen.dp(18f) - size, view.getMeasuredHeight() / 2f - size / 2f);
    staticIconReceiver.setBounds(0, 0, size, size);
    staticIconReceiver.draw(c);
    c.restore();
  }
}
