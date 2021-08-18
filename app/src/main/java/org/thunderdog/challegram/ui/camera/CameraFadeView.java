package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.view.View;

import me.vkryl.core.ColorUtils;

/**
 * Date: 9/22/17
 * Author: default
 */

public class CameraFadeView extends View {
  public CameraFadeView (Context context) {
    super(context);
    setBackgroundColor(ColorUtils.color((int) (255f * .7f), 0));
  }

  private float fadeFactor = 1f;

  public void setFadeFactor (float fadeFactor) {
    if (this.fadeFactor != fadeFactor) {
      this.fadeFactor = fadeFactor;
      setAlpha(fadeFactor);
    }
  }
}
