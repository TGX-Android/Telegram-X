package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGReaction;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ComplexReceiverProvider;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

public class ReactionModifier implements DrawModifier {
  public static final int MODE_GRID = 0;
  public static final int MODE_INLINE = 1;

  private final TGReaction[] reactions;
  private int mode = MODE_GRID;

  private int offset = 18;

  public ReactionModifier (Tdlib tdlib, String[] keys) {
    this(toReactions(tdlib, keys));
  }

  public ReactionModifier (TGReaction... reactions) {
    this.reactions = reactions;
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    ComplexReceiver complexReceiver = view instanceof ComplexReceiverProvider ? ((ComplexReceiverProvider) view).getComplexReceiver() : null;
    if (complexReceiver == null) return;

    if (mode == MODE_GRID) {
      drawGridMode(view, c, complexReceiver);
    } else if (mode == MODE_INLINE) {
      drawInlineMode(view, c, complexReceiver);
    }
  }

  @Override
  public int getWidth () {
    return Screen.dp(mode == MODE_INLINE ? (26 * reactions.length + 14) : 48);
  }

  public ReactionModifier requestFiles (ComplexReceiver complexReceiver) {
    for (int a = 0; a < reactions.length; a++) {
      TGReaction reaction = reactions[a];
      TGStickerObj centerAnimation = reaction.staticCenterAnimationSicker();
      TGStickerObj staticSticker = reaction.staticCenterAnimationSicker();
      ImageFile previewImage = centerAnimation.getImage();
      GifFile gifFile = staticSticker.getPreviewAnimation();
      complexReceiver.getImageReceiver(a * 2L + 1).requestFile(previewImage);
      complexReceiver.getGifReceiver(a * 2L).requestFile(gifFile);
    }

    return this;
  }

  public ReactionModifier setOffset (int offset) {
    this.offset = offset;
    return this;
  }

  public ReactionModifier setMode (int mode) {
    this.mode = mode;
    return this;
  }

  private void drawGridMode (View view, Canvas c, @NonNull ComplexReceiver complexReceiver) {
    int size = Screen.dp(reactions.length == 1 ? 40 : 20);
    int totalWidth = Screen.dp(40);
    int totalHeight = reactions.length > 2 ? totalWidth : size;
    int padding = Screen.dp(reactions.length == 1 ? 0 : -8);
    int sx = 0;
    int sy = 0;

    final int saveCount = Views.save(c);
    c.translate(view.getMeasuredWidth() - Screen.dp(offset) - totalWidth, view.getMeasuredHeight() / 2f - totalHeight / 2f);

    for (int a = 0; a < reactions.length; a++) {
      TGReaction reaction = reactions[a];
      ImageReceiver imageReceiver = complexReceiver.getImageReceiver(a * 2L + 1);
      GifReceiver gifReceiver = complexReceiver.getGifReceiver(a * 2L);
      final float previewScale = reaction.staticCenterAnimationSicker().getDisplayScale();
      final float gifScale = reaction.staticCenterAnimationSicker().getDisplayScale();

      DrawAlgorithms.drawReceiver(c, imageReceiver, gifReceiver, false, true,
        sx + padding, sy + padding, size + sx - padding, size + sy - padding, previewScale, gifScale);

      if (a == 0) {
        sx += size;
      }
      if (a == 1) {
        sx = reactions.length == 4 ? 0 : size / 2;
        sy += size;
      }
      if (a == 2) {
        sx += size;
      }
    }

    Views.restore(c, saveCount);
  }

  private void drawInlineMode (View view, Canvas c, @NonNull ComplexReceiver complexReceiver) {
    final int saveCount = Views.save(c);
    c.translate(view.getMeasuredWidth() - Screen.dp(offset) - getWidth(), view.getMeasuredHeight() / 2f - Screen.dp(20));

    int size = Screen.dp(40);
    int sx = 0;
    int sy = 0;

    for (int a = 0; a < reactions.length; a++) {
      TGReaction reaction = reactions[a];
      ImageReceiver imageReceiver = complexReceiver.getImageReceiver(a * 2L + 1);
      GifReceiver gifReceiver = complexReceiver.getGifReceiver(a * 2L);
      final float previewScale = reaction.staticCenterAnimationSicker().getDisplayScale();
      final float gifScale = reaction.staticCenterAnimationSicker().getDisplayScale();
      DrawAlgorithms.drawReceiver(c, imageReceiver, gifReceiver, false, true,
        sx, sy, size + sx, size + sy, previewScale, gifScale);

      sx += Screen.dp(26);
    }

    Views.restore(c, saveCount);
  }

  private static TGReaction[] toReactions (Tdlib tdlib, String[] keys) {
    TGReaction[] reactions = new TGReaction[keys.length];
    for (int a = 0; a < keys.length; a++) {
      reactions[a] = tdlib.getReaction(TD.toReactionType(keys[a]));
    }
    return reactions;
  }
}
