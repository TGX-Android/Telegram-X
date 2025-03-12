package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Screen;

public class ReactionsCounterDrawable extends Drawable {
  private final ReactionsListAnimator topReactions;

  public ReactionsCounterDrawable (ReactionsListAnimator topReactions) {
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

  private float getVisibility (ReactionsListAnimator.Entry item) {
    float position = item.getPosition();
    float visibility = item.getVisibility();
    if (position > 3f) {
      return 0f;
    } else if (position > 2f) {
      return Math.min(visibility, 3f - position);
    }

    return visibility;
  }

  private float getTargetVisibility (ReactionsListAnimator.Entry item) {
    float position = item.getPosition();
    float visibility = item.isAffectingList() ? 1f: 0f;
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
      ReactionsListAnimator.Entry item = topReactions.getEntry(a);
      width += Screen.dp(15) * getVisibility(item);
    }
    return Math.max((int) width - Screen.dp(3), 0);
  }

  public int getTargetWidth () {
    float width = 0f;
    for (int a = 0; a < topReactions.size(); a++) {
      ReactionsListAnimator.Entry item = topReactions.getEntry(a);
      width += Screen.dp(15) * getTargetVisibility(item);
    }
    return Math.max((int) width - Screen.dp(3), 0);
  }

  @Override
  public void draw (@NonNull Canvas c) {  // Never use
    draw(c, 0, 0);
  }

  public void draw (@NonNull Canvas c, int x, int y) {
    //c.drawRect(x, y - Screen.dp(6), x + getMinimumWidth(), y + Screen.dp(6), Paints.strokeSmallPaint(Color.RED));

    for (int a = 0; a < topReactions.size(); a++) {
      ReactionsListAnimator.Entry item = topReactions.getEntry(a);
      item.item.drawReactionNonBubble(c, x + item.getPosition() * Screen.dp(15) + Screen.dp(6), y, 12f, getVisibility(item));
    }
  }
}
