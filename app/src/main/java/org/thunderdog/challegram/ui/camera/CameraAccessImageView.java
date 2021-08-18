package org.thunderdog.challegram.ui.camera;

import android.content.Context;
import android.view.MotionEvent;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.chat.InvisibleImageView;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Date: 9/21/17
 * Author: default
 */

public class CameraAccessImageView extends InvisibleImageView {
  private final MessagesController c;
  private boolean hasAnyCamera;

  private ViewController.CameraOpenOptions cameraOpenOptions;

  public CameraAccessImageView (Context context, MessagesController c) {
    super(context);
    this.c = c;
    this.hasAnyCamera = U.deviceHasAnyCamera(context);
  }
    @Override
    public boolean onTouchEvent (MotionEvent event) {
    boolean res = super.onTouchEvent(event);
    if (Settings.instance().getCameraType() != Settings.CAMERA_TYPE_SYSTEM && res && event.getAction() == MotionEvent.ACTION_DOWN) {
      if (!hasAnyCamera) {
        hasAnyCamera = U.deviceHasAnyCamera(getContext());
      }
      if (hasAnyCamera && c.canSendMedia()) {
        if (cameraOpenOptions == null)
          cameraOpenOptions = new ViewController.CameraOpenOptions();
        UI.getContext(getContext()).prepareCameraDragByTouchDown(cameraOpenOptions, true);
      }
    }
    return res;
  }

  public void setCameraOpenOptions (ViewController.CameraOpenOptions options) {
    this.cameraOpenOptions = options;
  }
}
