package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;

public class ReactionModifier implements DrawModifier {
  private final ImageReceiver[] imageReceivers;
  private final int size;
  private final int width;
  private final int padding;

  public ReactionModifier (View view, TGStickerObj... reactions) {
    size = Screen.dp(reactions.length == 1 ? 22 : 20);
    width = Screen.dp(reactions.length == 1 ? 22 : 40);
    padding = Screen.dp(reactions.length == 1 ? 0 : 1);


    imageReceivers = new ImageReceiver[reactions.length];
    for (int a = 0; a < reactions.length; a++) {
      ImageFile imageFile = !reactions[a].isEmpty() ? reactions[a].getImage() : null;
      ImageReceiver imageReceiver = new ImageReceiver(view, 0);
      imageReceivers[a] = imageReceiver;
      if (imageFile != null) {
        imageReceiver.requestFile(imageFile);
      }
    }
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    int sx = 0;
    int sy = 0;

    c.save();
    c.translate(view.getMeasuredWidth() - Screen.dp(18f) - width, view.getMeasuredHeight() / 2f - width / 2f);
    if (imageReceivers.length > 0) {
      imageReceivers[0].setBounds(padding, padding, size - padding, size - padding);
      imageReceivers[0].draw(c);
      sx += size;
    }

    if (imageReceivers.length > 1) {
      imageReceivers[1].setBounds(sx + padding, sy + padding, size + sx - padding, size + sy- padding);
      imageReceivers[1].draw(c);
      sx = 0; sy += size;
    }

    if (imageReceivers.length > 2) {
      imageReceivers[2].setBounds(sx + padding, sy + padding, size + sx - padding, size + sy- padding);
      imageReceivers[2].draw(c);
      sx += size;
    }

    if (imageReceivers.length > 3) {
      imageReceivers[3].setBounds(sx + padding, sy + padding, size + sx- padding, size + sy - padding);
      imageReceivers[3].draw(c);
    }

    c.restore();
  }
}
