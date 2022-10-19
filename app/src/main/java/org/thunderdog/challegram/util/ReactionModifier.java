package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.util.ArrayList;
import java.util.List;

public class ReactionModifier implements DrawModifier {
  private final ImageReceiver[] previewReceivers;
  private final GifReceiver[] gifReceivers;
  private final int offset;
  private final int size;
  private final int totalWidth;
  private final int totalHeight;
  private final int padding;

  public ReactionModifier (ComplexReceiver complexReceiver, TGReaction... reactions) {
    this(complexReceiver, 18, reactions);
  }

  public ReactionModifier (ComplexReceiver complexReceiver, int offsetInDp, TGReaction... reactions) {
    size = Screen.dp(reactions.length == 1 ? 40 : 20);
    totalWidth = Screen.dp(40);
    totalHeight = reactions.length > 2 ? totalWidth : size;
    padding = Screen.dp(reactions.length == 1 ? 0 : -8);
    offset = offsetInDp;

    previewReceivers = new ImageReceiver[reactions.length];
    gifReceivers = new GifReceiver[reactions.length];

    for (int a = 0; a < reactions.length; a++) {
      previewReceivers[a] = complexReceiver.getImageReceiver(a * 2L + 1);
      gifReceivers[a] = complexReceiver.getGifReceiver(a * 2L);
      TGReaction reaction = reactions[a];
      if (reaction != null) {
        ImageFile previewImage = reaction.staticCenterAnimationSicker().getImage();
        GifFile gifFile = reaction.staticCenterAnimationSicker().getPreviewAnimation();
        previewReceivers[a].requestFile(previewImage);
        gifReceivers[a].requestFile(gifFile);
      }
    }
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    int sx = 0;
    int sy = 0;

    final int saveCount = Views.save(c);
    c.translate(view.getMeasuredWidth() - Screen.dp(offset) - totalWidth, view.getMeasuredHeight() / 2f - totalHeight / 2f);
    if (gifReceivers.length > 0) {
      DrawAlgorithms.drawReceiver(c, previewReceivers[0], gifReceivers[0], false, true,
        padding, padding, size - padding, size - padding);
      sx += size;
    }

    if (gifReceivers.length > 1) {
      DrawAlgorithms.drawReceiver(c, previewReceivers[1], gifReceivers[1], false, true,
        sx + padding, sy + padding, size + sx - padding, size + sy- padding);
      sx = gifReceivers.length == 4 ? 0 : size / 2 ; sy += size;
    }

    if (gifReceivers.length > 2) {
      DrawAlgorithms.drawReceiver(c, previewReceivers[2], gifReceivers[2], false, true,
        sx + padding, sy + padding, size + sx - padding, size + sy- padding);
      sx += size;
    }

    if (gifReceivers.length > 3) {
      DrawAlgorithms.drawReceiver(c, previewReceivers[3], gifReceivers[3], false, true,
        sx + padding, sy + padding, size + sx- padding, size + sy - padding);
    }

    Views.restore(c, saveCount);
  }

  @Override
  public int getWidth () {
    return Screen.dp(48);
  }
}
