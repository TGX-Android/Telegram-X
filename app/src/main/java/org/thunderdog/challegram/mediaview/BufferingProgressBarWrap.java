package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.SingleViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class BufferingProgressBarWrap extends View implements Destroyable {
  private final RectF fakeDialogFrame = new RectF();
  private ProgressComponent progressComponent;

  private final BoolAnimator progressVisible = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    progressComponent.setAlpha(factor);
  }, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, 210L);

  public BufferingProgressBarWrap (@NonNull Context context) {
    super(context);
    setWillNotDraw(false);
    progressComponent = new ProgressComponent(UI.getContext(getContext()), Screen.dp(18f));
    progressComponent.forceColor(Color.WHITE);
    progressComponent.setUseStupidInvalidate();
    progressComponent.setUseLargerPaint(Screen.dp(4f));
    progressComponent.setViewProvider(new SingleViewProvider(this));
    progressComponent.setAlpha(0f);
    progressComponent.attachToView(this);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;
    int dSize = Screen.dp(42f);

    progressComponent.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    fakeDialogFrame.set(cx - dSize, cy - dSize, cx + dSize, cy + dSize);
  }

  @Override
  public void performDestroy () {
    progressComponent.detachFromView(this);
    progressComponent.performDestroy();
  }

  public void setProgressVisible (boolean value) {
    progressVisible.setValue(value, true);
  }

  @Override
  protected void onDraw (Canvas canvas) {
    super.onDraw(canvas);

    final int color = 0x4c000000;
    final int bgColor = ColorUtils.color((int) ((float) Color.alpha(color) * progressVisible.getFloatValue()), color);

    canvas.drawRoundRect(fakeDialogFrame, Screen.dp(8f), Screen.dp(8f), Paints.getPorterDuffPaint(bgColor));
    progressComponent.draw(canvas);
  }
}
