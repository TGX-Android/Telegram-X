package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.ImageView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.tool.Screen;


public class MemberPickView extends ImageView {
  private DoubleTextWrapper content;
  private ImageReceiver receiver;

  public MemberPickView (Context context) {
    super(context);
  }

  public void setContent (DoubleTextWrapper content) {
    this.content = content;
    if (content == null) {
      receiver = null;
      setImageResource(R.drawable.baseline_person_24);
      invalidate();
      return;
    }
    receiver = new ImageReceiver(this, getWidth() / 2);
    receiver.requestFile(content.getAvatarFile());
    receiver.attach();
    invalidate();
  }
  public void reset(){
    setContent(null);
  }

  private void layoutReceiver () {
    int viewHeight = getMeasuredHeight();
    int radius = Screen.dp(11);
    int left = 0;
    int right = radius * 2;
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      this.receiver.setBounds(viewWidth - right, viewHeight / 2 - radius, viewWidth - left, viewHeight / 2 + radius);
    } else {
      this.receiver.setBounds(left, viewHeight / 2 - radius, right, viewHeight / 2 + radius);
    }
  }

  @Override
  public void draw (Canvas canvas) {
    if (content == null) {
      super.draw(canvas);
      return;
    }

    layoutReceiver();
    float radius = Screen.dp(11);
    final int saveCount = canvas.save();
    try {
      canvas.translate(getMeasuredWidth() / 2f - radius, 0);

      if (content.getAvatarFile() == null) {
        content.getAvatarPlaceholder().draw(canvas, radius, getMeasuredHeight() / 2f, 1, radius);
      } else {
        if (receiver.needPlaceholder()) {
          receiver.drawPlaceholderRounded(canvas, receiver.getRadius());
        }
        receiver.draw(canvas);
      }
    } catch (Exception ignored) {

    } finally {
      canvas.restoreToCount(saveCount);
    }
  }
}