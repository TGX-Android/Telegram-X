package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.ProgressComponent;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

/**
 * Date: 22/10/2016
 * Author: default
 */

public class MediaLocationPointView extends View implements FactorAnimator.Target {
  private static final int ANIMATOR_GPS = 0;
  private static final int ANIMATOR_CUSTOM = 1;
  private static final int ANIMATOR_PLACE = 2;

  private long initializationTime = SystemClock.uptimeMillis();
  private final BoolAnimator gpsLocated = new BoolAnimator(ANIMATOR_GPS, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator isCustom = new BoolAnimator(ANIMATOR_CUSTOM, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
  private final BoolAnimator isPlace = new BoolAnimator(ANIMATOR_PLACE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  private final ProgressComponent progressComponent = ProgressComponent.simpleInstance(this, 5f, Screen.dp(20f), Screen.dp(8f), Screen.dp(40f), Screen.dp(40));

  private final Drawable locationIcon, sendIcon;

  public MediaLocationPointView (Context context) {
    super(context);
    locationIcon = Drawables.get(getResources(), R.drawable.baseline_location_on_24);
    sendIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_send_24);
    progressComponent.setAlpha(1f);
  }

  private boolean needAnimation () {
    if (initializationTime == 0) {
      return true;
    }
    if (SystemClock.uptimeMillis() - initializationTime >= 100l) {
      initializationTime = 0;
      return true;
    }
    return false;
  }

  public void setShowProgress (boolean show) {
    this.gpsLocated.setValue(!show, needAnimation());
  }

  public void setIsCustom (boolean isCustom) {
    this.isCustom.setValue(isCustom, needAnimation());
  }

  public void setIsPlace (boolean isPlace) {
    this.isPlace.setValue(isPlace, true);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    progressComponent.setAlpha(1f - Math.max(gpsLocated.getFloatValue(), Math.max(isCustom.getFloatValue(), isPlace.getFloatValue())));
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  protected void onDraw (Canvas c) {
    float cx = getPaddingLeft() + (getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) / 2;
    float cy = getPaddingTop() + (getMeasuredHeight() - getPaddingTop() - getPaddingBottom()) / 2;

    float gpsFactor = gpsLocated.getFloatValue();
    float customFactor = isCustom.getFloatValue();
    float placeFactor = isPlace.getFloatValue();
    float activeFactor = Math.max(gpsFactor, customFactor);

    int color = Theme.getColor(R.id.theme_color_file);

    c.drawCircle(cx, cy, Screen.dp(20f), Paints.fillingPaint(color));
    progressComponent.draw(c);
    if (activeFactor > 0f && placeFactor < 1f) {
      Paint paint = Paints.getPorterDuffPaint(0xffffffff);
      paint.setAlpha((int) (255f * activeFactor * (1f - placeFactor)));
      Drawables.draw(c, locationIcon, cx - locationIcon.getMinimumWidth() / 2, cy - locationIcon.getMinimumHeight() / 2, paint);
      paint.setAlpha(255);
    }
    if (placeFactor > 0f) {
      Paint paint = Paints.getPorterDuffPaint(0xffffffff);
      paint.setAlpha((int) (255f * placeFactor));
      c.save();
      c.scale(.7f, .7f, cx, cy);
      Drawables.draw(c, sendIcon, cx + Screen.dp(2f) - sendIcon.getMinimumWidth() / 2, cy - sendIcon.getMinimumHeight() / 2, paint);
      c.restore();
      paint.setAlpha(255);
    }
  }
}
