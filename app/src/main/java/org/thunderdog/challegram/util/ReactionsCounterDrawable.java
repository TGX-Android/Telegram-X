package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.data.TGReactions;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.animator.ListAnimator;

public class ReactionsCounterDrawable extends Drawable {
  private final ListAnimator<TGReactions.MessageReactionEntry> topReactions;

  public ReactionsCounterDrawable (ListAnimator<TGReactions.MessageReactionEntry> topReactions) {
    this.topReactions = topReactions;
  }

  @Override
  public void setAlpha (int i) {

  }

  @Override
  public void setColorFilter (@Nullable ColorFilter colorFilter) {

  }

  @Override
  public int getOpacity () {
    return PixelFormat.UNKNOWN;
  }

  private float getVisibility (ListAnimator.Entry<TGReactions.MessageReactionEntry> item) {
    float position = item.getPosition();
    float visibility = item.getVisibility();
    if (position > 3f) {
      return 0f;
    } else if (position > 2f) {
      return Math.min(visibility, 3f - position);
    }

    return visibility;
  }

  @Override
  public int getMinimumWidth () {
    float width = 0f;
    for (int a = 0; a < topReactions.size(); a++) {
      ListAnimator.Entry<TGReactions.MessageReactionEntry> item = topReactions.getEntry(a);
      width += Screen.dp(14) * getVisibility(item);
    }
    return (int) width;
  }

  @Override
  public void draw (@NonNull Canvas c) {
    for (int a = 0; a < topReactions.size(); a++) {
      ListAnimator.Entry<TGReactions.MessageReactionEntry> item = topReactions.getEntry(a);
      item.item.drawReaction(c, item.getPosition() * Screen.dp(14), 0, 6f, getVisibility(item));
    }
  }

  public void draw (@NonNull Canvas c, int x, int y) {
    lastDrawX = x;
    lastDrawY = y;
    for (int a = 0; a < topReactions.size(); a++) {
      ListAnimator.Entry<TGReactions.MessageReactionEntry> item = topReactions.getEntry(a);
      item.item.drawReaction(c, x + item.getPosition() * Screen.dp(14), y, 6f, getVisibility(item));
    }
  }

  private float lastDrawX;
  private float lastDrawY;

  public float getLastDrawX () {
    return lastDrawX;
  }

  public float getLastDrawY () {
    return lastDrawY;
  }
}
