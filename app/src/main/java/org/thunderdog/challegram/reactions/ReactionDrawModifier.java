package org.thunderdog.challegram.reactions;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.DrawModifier;

import java.util.ArrayList;
import java.util.List;

public class ReactionDrawModifier implements DrawModifier {
  private ArrayList<Path> outlines = new ArrayList<>();
  private int count;
  private ComplexReceiver receiver;

  public ReactionDrawModifier (List<String> reactions, Tdlib tdlib, View parent) {
    receiver = new ComplexReceiver(parent);
    int i = 0;
    for (String reaction : reactions) {
      TdApi.Sticker _sticker = tdlib.getReaction(reaction).staticIcon;
      TGStickerObj sticker = new TGStickerObj(tdlib, _sticker, null, _sticker.type);
      receiver.getImageReceiver(i).requestFile(sticker.getFullImage());
      Path outline = sticker.getContour(Screen.dp(18));
      outlines.add(outline);
      i++;
    }
    count = reactions.size();
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    int cy = view.getMeasuredHeight() / 2;
    switch (count) {
      case 0:
        return;
      case 1:
        drawReceiver(c, 0, view.getMeasuredWidth() - Screen.dp(30), cy);
        break;
      case 2:
        drawReceiver(c, 0, view.getMeasuredWidth() - Screen.dp(47), cy);
        drawReceiver(c, 1, view.getMeasuredWidth() - Screen.dp(27), cy);
        break;
      case 3:
        drawReceiver(c, 0, view.getMeasuredWidth() - Screen.dp(47), cy - Screen.dp(10));
        drawReceiver(c, 1, view.getMeasuredWidth() - Screen.dp(27), cy - Screen.dp(10));
        drawReceiver(c, 2, view.getMeasuredWidth() - Screen.dp(37), cy + Screen.dp(10));
        break;
      case 4:
      default:
        drawReceiver(c, 0, view.getMeasuredWidth() - Screen.dp(47), cy - Screen.dp(10));
        drawReceiver(c, 1, view.getMeasuredWidth() - Screen.dp(27), cy - Screen.dp(10));
        drawReceiver(c, 2, view.getMeasuredWidth() - Screen.dp(47), cy + Screen.dp(10));
        drawReceiver(c, 3, view.getMeasuredWidth() - Screen.dp(27), cy + Screen.dp(10));
        break;
    }
  }

  private void drawReceiver (Canvas c, int index, int cx, int cy) {
    ImageReceiver receiver = this.receiver.getImageReceiver(index);
    Path outline = outlines.get(index);
    int size = Screen.dp(18);
    int x = cx - size / 2;
    int y = cy - size / 2;
    if (receiver.needPlaceholder() && outline != null) {
      c.save();
      c.translate(x, y);
      c.drawPath(outline, Paints.getPlaceholderPaint());
      c.restore();
    }
    receiver.setBounds(x, y, x + size, y + size);
    receiver.draw(c);
  }
}
