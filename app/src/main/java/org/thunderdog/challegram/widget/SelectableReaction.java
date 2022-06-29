package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.graphics.drawable.DrawableCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.EditReactionsController;

public class SelectableReaction extends View {
  private EmojiInfo reaction;
  private boolean isSelected;
  public EditReactionsController.ReactionItemHolder holder;
  Paint indicatorStrokePaint;

  public SelectableReaction (Context context) {
    super(context);
    indicatorStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  }

  public void setReaction (String reaction) {
    this.reaction = Emoji.instance().getEmojiInfo(EmojiData.instance().colorize(reaction, null, new String[]{}));
  }

  public void setHolder (EditReactionsController.ReactionItemHolder holder) {
    this.holder = holder;
  }

  public void setSelected (boolean isSelected) {
    if (this.isSelected != isSelected) {
      this.isSelected = isSelected;
      invalidate();
    }
  }

  @Override
  public boolean isSelected () {
    return isSelected;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (reaction == null) {
      return;
    }
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();

    int reactionPadding = Screen.dp(8f);
    int reactionSize = Math.min(viewWidth, viewHeight) - reactionPadding - reactionPadding;
    Rect rect = Paints.getRect();
    rect.left = viewWidth / 2 - reactionSize / 2;
    rect.top = viewHeight / 2 - reactionSize / 2;
    rect.right = rect.left + reactionSize;
    rect.bottom = rect.top + reactionSize;
    Emoji.instance().draw(c, reaction, rect);

    if (isSelected) {
      indicatorStrokePaint.setColor(Theme.getColor(R.id.theme_color_background));
      indicatorStrokePaint.setStyle(Paint.Style.FILL_AND_STROKE);
      Drawable indicator = getResources().getDrawable(R.drawable.baseline_check_circle_24);
      indicator.setBounds(viewWidth - Screen.dp(20f), viewHeight - Screen.dp(20f), viewWidth, viewHeight);
      DrawableCompat.setTint(indicator, Theme.getColor(R.id.theme_color_togglerActive));
      c.drawCircle(indicator.getBounds().centerX(), indicator.getBounds().centerY(), Screen.dp(10f), indicatorStrokePaint);
      indicator.draw(c);
    }
  }
}
