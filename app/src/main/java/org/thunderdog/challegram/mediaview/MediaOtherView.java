package org.thunderdog.challegram.mediaview;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

/**
 * Date: 10/12/2016
 * Author: default
 */

public class MediaOtherView extends FrameLayoutFix implements Destroyable {
  private ImageReceiver receiver;

  public MediaOtherView (Context context) {
    super(context);
    this.receiver = new ImageReceiver(this, 0);
    setWillNotDraw(false);

    int padding = Screen.dp(4f);
    int paddingBig = Screen.dp(16f);
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(Screen.dp(24f) + padding + paddingBig, Screen.dp(24f) + padding + paddingBig, Gravity.RIGHT | Gravity.TOP);

    DeleteView view = new DeleteView(context);
    view.setId(R.id.btn_removePhoto);
    view.setLayoutParams(params);
    view.setPadding(paddingBig, padding, padding, paddingBig);
    addView(view);
  }

  public void setOnDeleteClick (View.OnClickListener onDeleteClick) {
    getChildAt(0).setOnClickListener(onDeleteClick);
  }

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  @Override
  public void performDestroy () {
    receiver.requestFile(null);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(Screen.dp(100f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Screen.dp(100f), MeasureSpec.EXACTLY));
    receiver.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
  }

  private ImageFile imageFile;

  public void setImage (ImageFile image) {
    this.imageFile = image;
    receiver.requestFile(image);
  }

  public ImageFile getImage () {
    return imageFile;
  }

  @Override
  protected void onDraw (Canvas c) {
    if (receiver.needPlaceholder()) {
      c.drawRect(receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), Paints.fillingPaint(0x22ffffff));
    }
    receiver.draw(c);
  }

  private static class DeleteView extends View {
    public DeleteView (Context context) {
      super(context);
    }

    @Override
    protected void onDraw (Canvas c) {
      int cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;
      int cy = getPaddingTop() + (getMeasuredHeight() - getPaddingLeft() - getPaddingRight()) / 2;

      c.drawCircle(cx, cy, Screen.dp(12f), Paints.fillingPaint(0xffffffff));
      c.drawCircle(cx, cy, Screen.dp(10f), Paints.fillingPaint(0xffe45356));

      int width = Screen.dp(5f);
      int height = Screen.dp(3f);
      c.drawRect(cx - width, cy - height / 2, cx + width, cy + height / 2 + height % 2, Paints.fillingPaint(0xffffffff));
    }
  }
}
