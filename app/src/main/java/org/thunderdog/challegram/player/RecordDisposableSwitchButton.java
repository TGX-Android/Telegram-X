package org.thunderdog.challegram.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;

import me.vkryl.core.ColorUtils;

public class RecordDisposableSwitchButton extends RecordControllerButton {
  private final Drawable drawable;

  public RecordDisposableSwitchButton(Context context) {
    super(context);
    drawable = Drawables.get(context.getResources(), R.drawable.baseline_hot_once_24);
  }

  @Override
  protected void dispatchDraw (@NonNull Canvas canvas) {
    super.dispatchDraw(canvas);

    final float cx = getMeasuredWidth() / 2f;
    final float cy = getMeasuredHeight() / 2f;
    final float active = getActiveFactor();

    if (active == 0f) {
      Drawables.drawCentered(canvas, drawable, cx, cy, PorterDuffPaint.get(ColorId.icon));
    } else if (active == 1f) {
      Drawables.drawCentered(canvas, drawable, cx, cy, PorterDuffPaint.get(ColorId.fillingPositiveContent));
    } else {
      Drawables.drawCentered(canvas, drawable, cx, cy, Paints.getPorterDuffPaint(
        ColorUtils.fromToArgb(Theme.getColor(ColorId.icon), Theme.getColor(ColorId.fillingPositiveContent), active)
      ));
    }
  }
}
