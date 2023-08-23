package org.thunderdog.challegram.widget.EmojiMediaLayout.Sections;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;

public class EmojiSectionView extends View {
  private int forceWidth = -1;

  public EmojiSectionView (Context context) {
    super(context);
  }

  private EmojiSection section;

  public void setSection (EmojiSection section) {
    if (this.section != null) {
      this.section.setCurrentView(null);
    }
    this.section = section;
    if (section != null) {
      section.setCurrentView(this);
    }
  }

  public EmojiSection getSection () {
    return section;
  }

  public void setForceWidth (int width) {
    forceWidth = width;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int itemWidth = forceWidth > 0 ? forceWidth: Screen.dp(44);
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (section != null) {
      section.draw(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    }
  }
}
