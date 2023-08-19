package org.thunderdog.challegram.widget.EmojiMediaLayout.Sections;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;

public class EmojiSectionView extends View {
  private int forceWidth = -1;
  private int additionParentPadding = 0;

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

  public void setAdditionParentPadding (int additionParentPadding) {
    this.additionParentPadding = additionParentPadding;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int width = Math.min(MeasureSpec.getSize(widthMeasureSpec) + additionParentPadding, Screen.currentWidth());
    int itemWidth = forceWidth > 0 ? forceWidth: getWidth(width);
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (section != null) {
      section.draw(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    }
  }

  public static int getWidth (int parentWidth) {
    return Screen.dp(48); // parentWidth / 8;
  }
}
